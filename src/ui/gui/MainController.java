package ui.gui;

import ui.result.CalculationResult;
import ui.result.ModelInfo;
import ui.layer.OptimizationUseCase;
import ui.request.PropertyScanRequest;
import ui.result.PropertyScanResult;
import ui.request.PhaseDiagramRequest;
import ui.result.PhaseDiagramResult;
import session.CalculationSession;
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
 * System and Calculation layers is {@link CalculationSession}: browsing
 * (pre-calculation) and calculating both go through the one session this
 * controller holds. Paths that still reached around it -- phase diagram
 * via {@code PhaseDiagramUseCase}, property scan, the parameter-dump
 * inspector via {@code ModelInspectionService} -- have been reduced to
 * explicit "not yet wired through CalculationSession" stubs rather than
 * left bypassing the coordinator. See the TODO markers below.
 */
public class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());
    private final OptimizationUseCase optimizationUseCase;
    private final CalculationSession calculationSession = new CalculationSession();

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

            calculationSession.setModel(tdbPath, elementList, phaseList);
            calculationSession.calculateEquilibrium(T, P, compOverAll);

            system.ports.EquilibriumResult r = calculationSession.currentEquilibriumResult();
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
     * Run a phase diagram calculation with the given request.
     *
     * <p>TODO: not yet wired through {@link CalculationSession}. The old
     * path called {@code PhaseDiagramUseCase.execute(...)}, which does
     * {@code ThermodynamicSystem.build(...)} on its own -- bypassing the
     * session, so the system built for browsing / single-point is not
     * reused and the result does not land on the session. The compliant
     * shape is {@code calculationSession.setModel(...)} then
     * {@code calculationSession.calculatePhaseDiagram(axes, startAxes,
     * fixedT, fixedP, comp)}, read back via
     * {@code calculationSession.currentPhaseDiagram()} -- pending a
     * {@code PhaseDiagramRequest -> AxisConfig[]} adapter in this class.
     */
    public PhaseDiagramResult runPhaseDiagram(PhaseDiagramRequest request) {
        LOG.warning("runPhaseDiagram: not yet wired through CalculationSession");
        PhaseDiagramResult stub = new PhaseDiagramResult(
                request.getAxes().isEmpty() ? new String[]{"Axis"}
                        : new String[]{request.getAxes().get(0).name},
                new double[]{0}, new double[]{1});
        stub.setComplete(false);
        stub.setMessage("Phase diagram is not yet wired through CalculationSession "
                + "(see MainController.runPhaseDiagram TODO)");
        return stub;
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
            info.setAvailableElements(calculationSession.availableElements(tdbPath));
            if (elements != null && elements.length > 0) {
                info.setAvailablePhases(
                        calculationSession.availablePhasesFor(tdbPath, Arrays.asList(elements)));
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
     * Routed through {@link CalculationSession#availablePhasesFor} -- see
     * {@link #inspectModel} for why.
     */
    public List<String> getPhasesForElements(String tdbPath, List<String> elements) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "getPhasesForElements");
        try {
            List<String> phases = calculationSession.availablePhasesFor(tdbPath, elements);
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
