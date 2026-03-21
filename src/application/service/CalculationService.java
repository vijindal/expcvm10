package application.service;

import application.dto.CalculationRequest;
import application.dto.CalculationResult;
import application.dto.ModelInfo;
import calbince.*;
import database.tdb;
import domain.model.ThermoCondition;
import domain.port.DatabasePort;
import infrastructure.logging.AppLevel;
import infrastructure.logging.Trace;
import infrastructure.parser.TdbParser;
import phase.calphad.RK;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application service facade for thermodynamic calculation workflows.
 * Wraps the existing calculate, CalModel, and Methods logic.
 */
public class CalculationService {

    private static final Logger LOG = Logger.getLogger(CalculationService.class.getName());
    private final DatabasePort databasePort;

    /**
     * Constructor with dependency injection.
     * @param databasePort port for loading TDB databases
     */
    public CalculationService(DatabasePort databasePort) {
        this.databasePort = databasePort;
    }

    /**
     * Execute a single-point or method-based calculation from a CalculationRequest.
     */
    public CalculationResult runCalculation(CalculationRequest request) throws IOException {
        Trace.enter(LOG, AppLevel.FLOW, "CalculationService", "runCalculation");
        LOG.log(AppLevel.RESULT, "runCalculation: method={0}, T={1}, P={2}",
                new Object[]{request.getMethod(), request.getT(), request.getP()});
        CalculationResult result = new CalculationResult();
        result.setMethod(request.getMethod());

        // Load database
        String currentDirectory = System.getProperty("user.dir");
        String tdbPath = request.getTdbFilePath();
        if (tdbPath == null || tdbPath.isEmpty()) {
            LOG.log(AppLevel.WARN, "No TDB file path specified in request.");
            result.setSuccess(false);
            result.setMessage("No TDB file path specified.");
            return result;
        }
        LOG.log(AppLevel.FLOW, "Loading TDB: {0}", tdbPath);
        databasePort.load(tdbPath);
        String[] elementArray = request.getElements().toArray(new String[0]);
        DatabasePort systemPort = databasePort.extractSystem(elementArray);
        // Temporary bridge: legacy code still expects tdb objects
        tdb systdb = ((TdbParser) systemPort).getUnderlyingTdb();

        // Build CalVars
        CalVars calvars = new CalVars(systdb);
        CalcSet calcSet = new CalcSet();
        calcSet.setElementNames(request.getElements());

        CalcType calcType = new CalcType();
        calcType.setMethod(request.getMethod());
        calcType.setPhases(request.getPhases());

        Condition condition = new Condition(request.getT(), request.getP(), request.getCompositions());
        calcType.addConditions(condition);
        calcSet.addCalcType(calcType);
        calvars.addcalcSet(calcSet);

        // Run calculation
        calculate cal = new calculate(calvars);
        Trace.enter(LOG, AppLevel.FLOW, "CalculationService", "calculate.cal");
        cal.cal();
        Trace.exit(LOG, AppLevel.FLOW, "CalculationService", "calculate.cal");

        // Map a numeric result into DTO for GUI/CLI display.
        // Current implementation supports LIQUID + RK model path used in this migration.
        double computedValue = Double.NaN;
        if (request.getPhases() != null && !request.getPhases().isEmpty()) {
            String primaryPhase = request.getPhases().get(0);
            ArrayList<tdb.Parameter> params = systdb.getPhaseParam(request.getElements(), primaryPhase);
            if (params != null && !params.isEmpty()) {
                ThermoCondition thermoCondition = new ThermoCondition(
                    request.getT(),
                    request.getP(),
                    new ArrayList<List<Double>>(request.getCompositions())
                );
                RK rk = new RK(params, thermoCondition);
                if ("Gm".equalsIgnoreCase(request.getMethod())) {
                    computedValue = rk.calGm();
                } else if ("G".equalsIgnoreCase(request.getMethod()) || "HM".equalsIgnoreCase(request.getMethod())) {
                    // HM currently follows legacy single-point path; expose total Gibbs value until dedicated HM path is restored.
                    computedValue = rk.calG();
                } else {
                    computedValue = rk.calG();
                }
            }
        }
        LOG.log(AppLevel.RESULT, "Computed value: {0}", computedValue);

        result.setTemperature(request.getT());
        result.setPressure(request.getP());
        result.setValue(computedValue);
        if (request.getCompositions() != null && !request.getCompositions().isEmpty()) {
            ArrayList<Double> x = request.getCompositions().get(0);
            double[] xOut = new double[x.size()];
            for (int i = 0; i < x.size(); i++) {
                xOut[i] = x.get(i);
            }
            result.setCompositionResult(xOut);
        }
        result.setMessage(Double.isNaN(computedValue)
                ? "Calculation completed (numeric value unavailable for selected phase/method)."
                : "Calculation completed.");
        Trace.exit(LOG, AppLevel.FLOW, "CalculationService", "runCalculation");
        return result;
    }

    /**
     * Inspect TDB and return model metadata for GUI/CLI.
     */
    public ModelInfo inspectModel(String tdbPath, String[] elements) {
                // DEBUG: Log all phase names from TDB after loading
                if (databasePort instanceof infrastructure.parser.TdbParser) {
                    java.util.List<String> debugPhases = ((infrastructure.parser.TdbParser) databasePort).getPhaseNames();
                    LOG.log(AppLevel.RESULT, "DEBUG: All phases in TDB: {0}", debugPhases);
                }
        Trace.enter(LOG, AppLevel.FLOW, "CalculationService", "inspectModel");
        ModelInfo info = new ModelInfo();
        info.setFilePath(tdbPath);
        File file = new File(tdbPath);
        info.setFileExists(file.exists());
        info.setLastModifiedEpochMillis(info.isFileExists() ? file.lastModified() : 0L);
        info.setDetectedElements(java.util.Arrays.asList(elements));

        if (!info.isFileExists()) {
            info.setError("TDB file not found.");
            info.setAvailablePhases(Collections.<String>emptyList());
            return info;
        }

        try {
            databasePort.load(tdbPath);
            // Get all available elements from the loaded TDB (not just the input system)
            java.util.List<String> allElements = null;
            if (databasePort instanceof infrastructure.parser.TdbParser) {
                allElements = ((infrastructure.parser.TdbParser) databasePort).getElementNames();
            } else {
                allElements = java.util.Collections.emptyList();
            }
            info.setAvailableElements(allElements);
            if (elements != null && elements.length > 0) {
                DatabasePort systemPort = databasePort.extractSystem(elements);
                info.setAvailablePhases(systemPort.getPhaseNames());
                LOG.log(AppLevel.RESULT, "inspectModel found phases: {0}", info.getAvailablePhases());
            } else {
                // Show all phases in the TDB if no elements specified
                if (databasePort instanceof infrastructure.parser.TdbParser) {
                    info.setAvailablePhases(((infrastructure.parser.TdbParser) databasePort).getPhaseNames());
                } else {
                    info.setAvailablePhases(Collections.<String>emptyList());
                }
            }
        } catch (Exception ex) {
            info.setAvailablePhases(Collections.<String>emptyList());
            info.setAvailableElements(Collections.<String>emptyList());
            info.setError(ex.getMessage());
            LOG.log(AppLevel.WARN, "inspectModel error", ex);
        }
        Trace.exit(LOG, AppLevel.FLOW, "CalculationService", "inspectModel");
        return info;
    }

    /**
     * Run the legacy CalModel-based calculation from data files.
     */
    public CalculationResult runCalModel(String exptDataFile, String phaseDataFile) throws IOException {
        Trace.enter(LOG, AppLevel.FLOW, "CalculationService", "runCalModel");
        CalculationResult result = new CalculationResult();

        PhaseData phasedata = new PhaseData(phaseDataFile);
        phasedata.readPhaseDataInputFile();

        ExptData exptdata = new ExptData(exptDataFile);
        exptdata.readExptDataFile();

        ExptData recordData = new ExptData();
        CalModel calmodel = new CalModel(exptdata, phasedata, recordData);
        calmodel.Run();

        result.setMessage("CalModel calculation completed.");
        Trace.exit(LOG, AppLevel.FLOW, "CalculationService", "runCalModel");
        return result;
    }
}
