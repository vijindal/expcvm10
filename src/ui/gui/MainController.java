package ui.gui;

import service.CalculationRequest;
import service.CalculationResult;
import service.ModelInfo;
import service.CalculationService;
import service.PropertyScanRequest;
import service.PropertyScanResult;
import service.OptimizationService;
import service.PhaseDiagramRequest;
import service.PhaseDiagramResult;
import service.PhaseDiagramUseCase;
import thermocalc.diagram.AxisConfig;
import infra.AppLevel;
import infra.Trace;

import database.tdb;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main controller bridging GUI views to application use-cases.
 */
public class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());
    private final CalculationService calculationService;
    private final OptimizationService optimizationService;

    public MainController(CalculationService calculationService,
                          OptimizationService optimizationService) {
        this.calculationService = calculationService;
        this.optimizationService = optimizationService;
    }

    /**
     * Run a single-point calculation with the given parameters.
     */
    public CalculationResult runSinglePoint(String tdbPath, String[] elements,
                                            String method, String[] phases,
                                            double T, double P,
                                            ArrayList<ArrayList<Double>> compositions) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runSinglePoint");
        CalculationRequest request = new CalculationRequest();
        request.setTdbFilePath(tdbPath);
        request.setElements(new ArrayList<>(Arrays.asList(elements)));
        request.setMethod(method);
        request.setPhases(new ArrayList<>(Arrays.asList(phases)));
        request.setT(T);
        request.setP(P);
        request.setCompositions(compositions);

        try {
            CalculationResult r = calculationService.runCalculation(request);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runSinglePoint");
            return r;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Single-point calculation failed", e);
            CalculationResult error = new CalculationResult();
            error.setSuccess(false);
            error.setMessage("Error: " + e.getMessage());
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runSinglePoint");
            return error;
        }
    }

    /**
     * Run a phase diagram calculation with the given request.
     */
    public PhaseDiagramResult runPhaseDiagram(PhaseDiagramRequest request) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runPhaseDiagram");
        try {
            PhaseDiagramResult r = new PhaseDiagramUseCase().execute(request);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runPhaseDiagram");
            return r;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Phase diagram calculation failed", e);
            PhaseDiagramResult error = new PhaseDiagramResult(
                    request.getAxes().size() == 0 ? new String[]{"Axis"} : new String[]{request.getAxes().get(0).name},
                    new double[]{0}, new double[]{1});
            error.setComplete(false);
            error.setMessage("Error: " + e.getMessage());
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runPhaseDiagram");
            return error;
        }
    }

    /**
     * Run optimization from the GUI.
     */
    public String runOptimization(String exptDataFile, String phaseDataFile,
                                  String filePrefix, int maxIterations) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runOptimization");
        try {
            optimizationService.runOptimization(exptDataFile, phaseDataFile, filePrefix, maxIterations);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runOptimization");
            return "Optimization completed successfully.";
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Optimization failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runOptimization");
            return "Optimization failed: " + e.getMessage();
        }
    }

    /**
     * Run CalModel-based validation from the GUI.
     */
    public CalculationResult runCalModel(String exptDataFile, String phaseDataFile) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runCalModel");
        try {
            CalculationResult r = calculationService.runCalModel(exptDataFile, phaseDataFile);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runCalModel");
            return r;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CalModel run failed", e);
            CalculationResult error = new CalculationResult();
            error.setSuccess(false);
            error.setMessage("Error: " + e.getMessage());
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runCalModel");
            return error;
        }
    }

    /**
     * Inspect TDB and return model metadata.
     */
    public ModelInfo inspectModel(String tdbPath, String[] elements) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "inspectModel");
        ModelInfo info = calculationService.inspectModel(tdbPath, elements);
        Trace.exit(LOG, AppLevel.FLOW, "MainController", "inspectModel");
        return info;
    }

    public List<String> getPhasesForElements(String tdbPath, List<String> elements) {
        return calculationService.getPhasesForElements(tdbPath, elements);
    }

    public List<tdb.Parameter> getPhaseParameters(String tdbPath, List<String> elements, String phaseName) {
        return calculationService.getPhaseParameters(tdbPath, elements, phaseName);
    }

    public PropertyScanResult runPropertyScan(PropertyScanRequest request) {
        try {
            return calculationService.runPropertyScan(request);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Property scan failed", e);
            PropertyScanResult err = new PropertyScanResult();
            err.setSuccess(false);
            err.setMessage("Error: " + e.getMessage());
            return err;
        }
    }
}
