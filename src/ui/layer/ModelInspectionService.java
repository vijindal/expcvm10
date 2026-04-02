package ui.layer;

import legacy.calbince.*;
import system.database.tdb;
import system.ports.DatabasePort;
import util.AppLevel;
import util.Trace;
import system.database.TdbParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service: TDB inspection and model metadata.
 * Extracted from CalculationService to provide model loading and query functionality.
 */
public class ModelInspectionService {

    private static final Logger LOG = Logger.getLogger(ModelInspectionService.class.getName());
    private final DatabasePort databasePort;

    /**
     * Constructor with dependency injection.
     * @param databasePort port for loading TDB databases
     */
    public ModelInspectionService(DatabasePort databasePort) {
        this.databasePort = databasePort;
    }

    /**
     * Inspect TDB and return model metadata for GUI/CLI.
     */
    public ui.result.ModelInfo inspectModel(String tdbPath, String[] elements) {
        // DEBUG: Log all phase names from TDB after loading
        if (databasePort instanceof TdbParser) {
            java.util.List<String> debugPhases = ((TdbParser) databasePort).getPhaseNames();
            LOG.log(AppLevel.RESULT, "DEBUG: All phases in TDB: {0}", debugPhases);
        }
        Trace.enter(LOG, AppLevel.FLOW, "ModelInspectionService", "inspectModel");
        ui.result.ModelInfo info = new ui.result.ModelInfo();
        info.setFilePath(tdbPath);
        File file = new File(tdbPath);
        info.setFileExists(file.exists());
        info.setLastModifiedEpochMillis(info.isFileExists() ? file.lastModified() : 0L);
        info.setDetectedElements(java.util.Arrays.asList(elements));

        if (!info.isFileExists()) {
            info.setError("TDB file not found.");
            info.setAvailablePhases(Collections.emptyList());
            return info;
        }

        try {
            databasePort.load(tdbPath);
            // Get all available elements from the loaded TDB (not just the input system)
            java.util.List<String> allElements = null;
            if (databasePort instanceof TdbParser) {
                allElements = ((TdbParser) databasePort).getElementNames();
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
                if (databasePort instanceof TdbParser) {
                    info.setAvailablePhases(((TdbParser) databasePort).getPhaseNames());
                } else {
                    info.setAvailablePhases(Collections.emptyList());
                }
            }
        } catch (Exception ex) {
            info.setAvailablePhases(Collections.emptyList());
            info.setAvailableElements(Collections.emptyList());
            info.setError(ex.getMessage());
            LOG.log(AppLevel.WARN, "inspectModel error", ex);
        }
        Trace.exit(LOG, AppLevel.FLOW, "ModelInspectionService", "inspectModel");
        return info;
    }

    /**
     * Returns phase names whose constituents are a subset of the given elements.
     */
    public List<String> getPhasesForElements(String tdbPath, List<String> elements) {
        if (elements == null || elements.isEmpty()) return Collections.emptyList();
        try {
            databasePort.load(tdbPath);
            DatabasePort sub = databasePort.extractSystem(elements.toArray(new String[0]));
            return sub.getPhaseNames();
        } catch (Exception ex) {
            LOG.log(AppLevel.WARN, "getPhasesForElements error", ex);
            return Collections.emptyList();
        }
    }

    /**
     * Returns the Parameter list for a given phase + element set from the loaded TDB.
     */
    public List<tdb.Parameter> getPhaseParameters(String tdbPath, List<String> elements, String phaseName) {
        if (elements == null || elements.isEmpty() || phaseName == null) return Collections.emptyList();
        try {
            databasePort.load(tdbPath);
            if (!(databasePort instanceof TdbParser)) return Collections.emptyList();
            tdb db = ((TdbParser) databasePort).getUnderlyingTdb();
            if (db == null) return Collections.emptyList();
            ArrayList<String> elemList = new ArrayList<>(elements);
            return db.getPhaseParam(elemList, phaseName);
        } catch (Exception ex) {
            LOG.log(AppLevel.WARN, "getPhaseParameters error", ex);
            return Collections.emptyList();
        }
    }

    /**
     * Run the legacy CalModel-based calculation from data files.
     */
    public ui.result.CalculationResult runCalModel(String exptDataFile, String phaseDataFile) throws IOException {
        Trace.enter(LOG, AppLevel.FLOW, "ModelInspectionService", "runCalModel");
        ui.result.CalculationResult result = new ui.result.CalculationResult();

        PhaseData phasedata = new PhaseData(phaseDataFile);
        phasedata.readPhaseDataInputFile();

        ExptData exptdata = new ExptData(exptDataFile);
        exptdata.readExptDataFile();

        ExptData recordData = new ExptData();
        CalModel calmodel = new CalModel(exptdata, phasedata, recordData);
        calmodel.Run();

        result.setMessage("CalModel calculation completed.");
        Trace.exit(LOG, AppLevel.FLOW, "ModelInspectionService", "runCalModel");
        return result;
    }
}
