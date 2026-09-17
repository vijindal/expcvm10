package ui.gui;

import ui.result.CalculationResult;
import ui.result.ModelInfo;
import ui.layer.OptimizationUseCase;
import ui.layer.ModelBrowseService;
import ui.request.PropertyScanRequest;
import ui.result.PropertyScanResult;
import ui.request.PhaseDiagramRequest;
import calc.diagram.PhaseDiagramResult;
import session.CalculationSession;
import session.calctype.CalculationInterface;
import session.calctype.CalculationKind;
import session.calctype.CalculationOutcome;
import session.calctype.ModelSelection;
import util.AppLevel;
import util.Trace;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bridges GUI views to {@link CalculationSession}.
 *
 * <p>Per the target data flow (README "Structure" / {@code
 * docs/dataflow_target.png}), the GUI's only point of contact with the
 * System and Calculation layers is {@link session.calctype.CalculationInterface}:
 * browsing (pre-calculation) still goes directly through the held {@link
 * CalculationSession} (via {@link ModelBrowseService}, unchanged), but every
 * calculation builds a {@link session.calctype.ModelSelection} and a
 * calculation type's own typed params, then calls {@link
 * CalculationInterface#runCalculating}/{@link CalculationInterface#runAssessing}
 * rather than {@code calculationSession.calculate*}/{@code setModel}
 * directly. Paths that still reached around it -- phase diagram via
 * {@code PhaseDiagramUseCase}, property scan, the parameter-dump inspector
 * via {@code ModelInspectionService} -- have been reduced to explicit
 * "not yet wired through CalculationSession" stubs rather than left
 * bypassing the coordinator. See the TODO markers below.
 */
public class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());
    private final OptimizationUseCase optimizationUseCase;
    private final CalculationSession calculationSession = new CalculationSession();
    private final ModelBrowseService modelBrowseService = new ModelBrowseService(calculationSession);

    public MainController(OptimizationUseCase optimizationUseCase) {
        this.optimizationUseCase = optimizationUseCase;
    }

    /**
     * Run a single-point calculation with the given parameters.
     *
     * <p>Routed through {@link CalculationSession} -- the same coordinator
     * the REST API and the CLI's {@code equilibrium} command use. One
     * {@code CalculationSession} is held for the lifetime of this
     * controller, so repeated single-point runs against the same
     * TDB/elements/phases reuse the built {@code ThermodynamicSystem}
     * instead of re-parsing the database every time.
     */
    public system.ports.EquilibriumResult runSinglePoint(String tdbPath, String[] elements,
                                            String method, String[] phases,
                                            double T, double P,
                                            ArrayList<ArrayList<Double>> compositions) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runSinglePoint");
        try {
            List<String> elementList = Arrays.asList(elements);
            List<String> phaseList = Arrays.asList(phases);
            double[] compOverAll = extractComposition(compositions, elementList.size());

            ModelSelection model = new ModelSelection(tdbPath, elementList, phaseList);
            CalculationInterface.EquilibriumParams params =
                    new CalculationInterface.EquilibriumParams(T, P, compOverAll);
            system.ports.EquilibriumResult r = CalculationInterface.runCalculating(
                    calculationSession, CalculationKind.EQUILIBRIUM, model, params);

            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runSinglePoint");
            return r;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Single-point calculation failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runSinglePoint");
            throw new RuntimeException("Single-point calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the overall composition array from the GUI's composition
     * input, matching {@code EquilibriumUseCase}'s existing convention:
     * use the first composition vector if present, otherwise fall back to
     * a uniform composition.
     */
    private double[] extractComposition(ArrayList<ArrayList<Double>> compositions, int nc) {
        double[] comp = new double[nc];
        if (compositions != null && !compositions.isEmpty()) {
            List<Double> first = compositions.get(0);
            for (int i = 0; i < Math.min(nc, first.size()); i++) {
                comp[i] = first.get(i);
            }
        } else {
            Arrays.fill(comp, 1.0 / nc);
        }
        return comp;
    }

    /**
     * Run an automated phase-diagram calculation with the given request
     * -- {@link CalculationSession#calculatePhaseDiagram} (Sundman
     * Algorithms B/C1/C2/D, whole connected diagram from one starting
     * point), not the old {@code PhaseDiagramUseCase} path (which did
     * {@code ThermodynamicSystem.build(...)} on its own, bypassing the
     * session so the browsing/single-point system was never reused).
     *
     * <p>{@link PhaseDiagramRequest#axisArray()}/{@link
     * PhaseDiagramRequest#startAxisValues()} already have exactly the
     * shape {@link CalculationSession#calculatePhaseDiagram} needs --
     * this method is a thin adapter, the same shape as {@link
     * #runCoarseDiagram}. {@link PhaseDiagramRequest.DiagramType#COARSE}
     * is not handled here -- callers wanting a coarse grid should call
     * {@link #runCoarseDiagram} directly.
     */
    public PhaseDiagramResult runPhaseDiagram(PhaseDiagramRequest request) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runPhaseDiagram");
        try {
            ModelSelection model = new ModelSelection(request.getTdbFilePath(),
                    request.getElements(), request.getPhases());

            CalculationInterface.PhaseDiagramParams params = new CalculationInterface.PhaseDiagramParams(
                    request.axisArray(), request.startAxisValues(),
                    request.getFixedT(), request.getFixedP(), request.getStartComposition());

            PhaseDiagramResult r = CalculationInterface.runCalculating(
                    calculationSession, CalculationKind.PHASE_DIAGRAM, model, params);

            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runPhaseDiagram");
            return r;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Phase diagram calculation failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runPhaseDiagram");
            throw new RuntimeException("Phase diagram calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run a coarse binary/ternary phase diagram: samples a 2D grid of
     * conditions and calls the full equilibrium solver independently at
     * each point, coloring the result by stable-phase-set for scatter/
     * dot rendering. Unlike {@link #runPhaseDiagram}, this is fully
     * wired through {@link CalculationSession} -- see {@code
     * CalculationSession#calculateCoarseBinaryDiagram}/
     * {@code calculateCoarseTernaryDiagram}.
     */
    public ui.result.CoarseDiagramResult runCoarseDiagram(PhaseDiagramRequest request) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runCoarseDiagram");
        try {
            ModelSelection model = new ModelSelection(request.getTdbFilePath(),
                    request.getElements(), request.getPhases());

            List<calc.diagram.AxisConfig> axes = request.getAxes();
            calc.diagram.AxisConfig axisX = axes.get(0);
            calc.diagram.AxisConfig axisY = axes.get(1);
            double[] comp = request.getStartComposition();

            ui.result.CoarseDiagramResult r;
            if (request.isTernary()) {
                CalculationInterface.CoarseTernaryParams params = new CalculationInterface.CoarseTernaryParams(
                        axisX, axisY, request.getFixedT(), request.getFixedP(), comp,
                        request.getProgressCallback());
                r = CalculationInterface.runCalculating(
                        calculationSession, CalculationKind.COARSE_TERNARY, model, params);
            } else {
                CalculationInterface.CoarseBinaryParams params = new CalculationInterface.CoarseBinaryParams(
                        axisX, axisY, request.getFixedT(), request.getFixedP(), comp,
                        request.getProgressCallback());
                r = CalculationInterface.runCalculating(
                        calculationSession, CalculationKind.COARSE_BINARY, model, params);
            }

            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runCoarseDiagram");
            return r;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Coarse diagram calculation failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runCoarseDiagram");
            throw new RuntimeException("Coarse diagram calculation failed: " + e.getMessage(), e);
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
     * The {@link session.calctype.CalculationGroup#ASSESS} ("opt") landing
     * choice: thermodynamic assessment / database creation. Not implemented
     * yet -- routed through {@link CalculationInterface#runAssessing} so the
     * GUI surfaces the same message the CLI and API do, rather than its own
     * ad hoc stub text.
     */
    public CalculationResult runAssessment() {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runAssessment");
        CalculationOutcome.NotImplemented<Void> outcome =
                CalculationInterface.runAssessing(CalculationKind.ASSESSMENT, null);
        CalculationResult result = new CalculationResult();
        result.setSuccess(false);
        result.setMessage(outcome.message());
        Trace.exit(LOG, AppLevel.FLOW, "MainController", "runAssessment");
        return result;
    }

    /**
     * Lists the {@code .tdb} database files available to choose from.
     * Routed through {@link CalculationSession#availableDatabases}, per
     * {@code docs/plan-gui-calculationsession-wiring.md} Fix 3 -- the GUI
     * must not scan the filesystem itself, so this capability is shared
     * with the CLI and API via the same session method.
     */
    public List<String> availableDatabases() {
        return calculationSession.availableDatabases();
    }

    /**
     * Inspect TDB and return model metadata.
     *
     * <p>Routed through {@link CalculationSession#availableElements}
     * (browsing, not calculating -- see
     * {@code docs/plan-gui-calculationsession-wiring.md}) rather than
     * {@link ModelInspectionService} directly, so the GUI's
     * database-selection step shares the same cached
     * {@code TdbParser} state as everything else that goes through
     * {@code calculationSession}, instead of re-parsing the file on its
     * own separate path.
     */
    public ModelInfo inspectModel(String tdbPath, String[] elements) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "inspectModel");
        ModelInfo info = new ModelInfo();
        info.setFilePath(tdbPath);
        info.setDetectedElements(Arrays.asList(elements));

        java.io.File file = new java.io.File(tdbPath);
        info.setFileExists(file.exists());
        info.setLastModifiedEpochMillis(info.isFileExists() ? file.lastModified() : 0L);
        if (!info.isFileExists()) {
            info.setError("TDB file not found.");
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "inspectModel");
            return info;
        }

        try {
            info.setAvailableElements(modelBrowseService.selectableElements(tdbPath));
            if (elements != null && elements.length > 0) {
                info.setAvailablePhases(
                        modelBrowseService.selectablePhases(tdbPath, Arrays.asList(elements)));
            }
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "inspectModel");
            return info;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Model inspection failed", e);
            info.setError("Model inspection failed: " + e.getMessage());
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "inspectModel");
            return info;
        }
    }

    /**
     * Routed through {@link ModelBrowseService#selectablePhases} -- see
     * {@link #inspectModel} for why.
     */
    public List<String> getPhasesForElements(String tdbPath, List<String> elements) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "getPhasesForElements");
        try {
            List<String> phases = modelBrowseService.selectablePhases(tdbPath, elements);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "getPhasesForElements");
            return phases;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Phase retrieval failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "getPhasesForElements");
            return new ArrayList<>();
        }
    }

    /**
     * Detailed per-phase parameter dump for the model inspector panel.
     *
     * <p>TODO: not yet wired through {@link CalculationSession}. The old
     * path called {@code ModelInspectionService.getPhaseParameters(...)}
     * directly (raw {@code TdbParser}), reaching around the session. The
     * compliant shape is a browse-style query method on
     * {@code CalculationSession} (e.g. {@code phaseParameters(tdbPath,
     * elements, phaseName)}) backed by the session's own browse
     * {@code DatabasePort}. Returns an empty list until then.
     */
    public List<?> getPhaseParameters(String tdbPath, List<String> elements, String phaseName) {
        LOG.warning("getPhaseParameters: not yet wired through CalculationSession");
        return new ArrayList<>();
    }

    /**
     * Property scan (STEP / MAP).
     *
     * <p>TODO: not yet wired through {@link CalculationSession}. The
     * compliant shape is {@code calculationSession.setModel(...)} then
     * {@code calculationSession.calculateStep(...)} /
     * {@code calculateMap(...)} -- both of which are themselves
     * unimplemented stubs today (no plain property-sampling engine
     * exists; see {@code CalculationSession}). Returns a "not
     * implemented" result until an engine and the wiring both exist.
     */
    public PropertyScanResult runPropertyScan(PropertyScanRequest request) {
        LOG.warning("runPropertyScan: not yet wired through CalculationSession");
        PropertyScanResult err = new PropertyScanResult();
        err.setSuccess(false);
        err.setMessage("Property scan is not yet wired through CalculationSession "
                + "(see MainController.runPropertyScan TODO)");
        return err;
    }
}
