package ui.gui;

import ui.request.CalculationRequest;
import ui.result.CalculationResult;
import ui.result.ModelInfo;
import ui.layer.SinglePointUseCase;
import ui.request.PropertyScanRequest;
import ui.result.PropertyScanResult;
import ui.layer.OptimizationUseCase;
import ui.request.PhaseDiagramRequest;
import ui.result.PhaseDiagramResult;
import ui.layer.PhaseDiagramUseCase;
import ui.layer.ModelInspectionService;
import calc.diagram.AxisConfig;
import util.AppLevel;
import util.Trace;
import contracts.LoggingPort;
import contracts.OptimizationOutputPort;

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
    private final SinglePointUseCase singlePointUseCase;
    private final OptimizationUseCase optimizationUseCase;
    private final PhaseDiagramUseCase phaseDiagramUseCase;
    private final ModelInspectionService modelInspectionService;

    public MainController(SinglePointUseCase singlePointUseCase,
                          OptimizationUseCase optimizationUseCase,
                          PhaseDiagramUseCase phaseDiagramUseCase,
                          ModelInspectionService modelInspectionService) {
        this.singlePointUseCase = singlePointUseCase;
        this.optimizationUseCase = optimizationUseCase;
        this.phaseDiagramUseCase = phaseDiagramUseCase;
        this.modelInspectionService = modelInspectionService;
    }

    /**
     * Run a single-point calculation with the given parameters.
     */
    public contracts.EquilibriumResult runSinglePoint(String tdbPath, String[] elements,
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
            contracts.EquilibriumResult r = singlePointUseCase.execute(request);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runSinglePoint");
            return r;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Single-point calculation failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runSinglePoint");
            throw new RuntimeException("Single-point calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run a phase diagram calculation with the given request.
     */
    public PhaseDiagramResult runPhaseDiagram(PhaseDiagramRequest request) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runPhaseDiagram");
        try {
            PhaseDiagramResult r = phaseDiagramUseCase.execute(request);
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
            optimizationUseCase.runOptimization(exptDataFile, phaseDataFile, filePrefix, maxIterations);
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
     * NOTE: Requires ModelInspectionService to be injected separately.
     */
    public CalculationResult runCalModel(String exptDataFile, String phaseDataFile) {
        // TODO: Wire to ModelInspectionService in constructor
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runCalModel");
        CalculationResult error = new CalculationResult();
        error.setSuccess(false);
        error.setMessage("CalModel support requires ModelInspectionService injection");
        Trace.exit(LOG, AppLevel.FLOW, "MainController", "runCalModel");
        return error;
    }

    /**
     * Inspect TDB and return model metadata.
     */
    public ModelInfo inspectModel(String tdbPath, String[] elements) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "inspectModel");
        try {
            ModelInfo info = modelInspectionService.inspectModel(tdbPath, elements);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "inspectModel");
            return info;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Model inspection failed", e);
            ModelInfo error = new ModelInfo();
            error.setError("Model inspection failed: " + e.getMessage());
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "inspectModel");
            return error;
        }
    }

    public List<String> getPhasesForElements(String tdbPath, List<String> elements) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "getPhasesForElements");
        try {
            List<String> phases = modelInspectionService.getPhasesForElements(tdbPath, elements);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "getPhasesForElements");
            return phases;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Phase retrieval failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "getPhasesForElements");
            return new ArrayList<>();
        }
    }

    public List<?> getPhaseParameters(String tdbPath, List<String> elements, String phaseName) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "getPhaseParameters");
        try {
            List<?> parameters = modelInspectionService.getPhaseParameters(tdbPath, elements, phaseName);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "getPhaseParameters");
            return parameters;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Parameter retrieval failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "getPhaseParameters");
            return new ArrayList<>();
        }
    }

    public PropertyScanResult runPropertyScan(PropertyScanRequest request) {
        // TODO: Wire to PropertyScanUseCase in constructor
        PropertyScanResult err = new PropertyScanResult();
        err.setSuccess(false);
        err.setMessage("Property scan requires PropertyScanUseCase injection");
        return err;
    }
}
