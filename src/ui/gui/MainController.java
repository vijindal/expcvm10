package ui.gui;

import ui.result.CalculationResult;
import ui.result.ModelInfo;
import ui.layer.OptimizationUseCase;
import ui.layer.ModelBrowseService;
import ui.request.PropertyScanRequest;
import ui.result.PropertyScanResult;
import ui.request.PhaseDiagramRequest;
import calc.diagram.PhaseDiagramResult;
import application.ApplicationLayer;
import application.calctype.CalculationInterface;
import application.calctype.CalculationKind;
import application.calctype.CalculationOutcome;
import application.calctype.ModelSelection;
import util.AppLevel;
import util.Trace;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bridges GUI views to {@link ApplicationLayer}.
 *
 * <p>Per the target data flow (README "Structure" / {@code
 * docs/dataflow_target.png}), the GUI's only point of contact with the
 * System and Calculation layers is {@link application.calctype.CalculationInterface}:
 * browsing (pre-calculation) still goes directly through the held {@link
 * ApplicationLayer} (via {@link ModelBrowseService}, unchanged), but every
 * calculation builds a {@link application.calctype.ModelSelection} and a
 * calculation type's own typed params, then calls {@link
 * CalculationInterface#runCalculating}/{@link CalculationInterface#runAssessing}
 * rather than {@code calculationSession.calculate*}/{@code setModel}
 * directly. Paths that still reached around it -- phase diagram via
 * {@code PhaseDiagramUseCase}, property scan, the parameter-dump inspector
 * via {@code ModelInspectionService} -- have been reduced to explicit
 * "not yet wired through ApplicationLayer" stubs rather than left
 * bypassing the coordinator. See the TODO markers below.
 */
public class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());
    private final OptimizationUseCase optimizationUseCase;
    private final ApplicationLayer calculationSession = new ApplicationLayer();
    private final ModelBrowseService modelBrowseService = new ModelBrowseService(calculationSession);
    private final ui.layer.ModelInspectionService inspectionService = new ui.layer.ModelInspectionService(calculationSession);

    public MainController(OptimizationUseCase optimizationUseCase) {
        this.optimizationUseCase = optimizationUseCase;
    }

    /**
     * Run a single-point calculation with the given parameters.
     *
     * <p>Routed through {@link ApplicationLayer} -- the same coordinator
     * the REST API and the CLI's {@code equilibrium} command use. One
     * {@code ApplicationLayer} is held for the lifetime of this
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
     * -- {@link ApplicationLayer#calculatePhaseDiagram} (Sundman
     * Algorithms B/C1/C2/D, whole connected diagram from one starting
     * point), not the old {@code PhaseDiagramUseCase} path (which did
     * {@code ThermodynamicSystem.build(...)} on its own, bypassing the
     * session so the browsing/single-point system was never reused).
     *
     * <p>{@link PhaseDiagramRequest#axisArray()}/{@link
     * PhaseDiagramRequest#startAxisValues()} already have exactly the
     * shape {@link ApplicationLayer#calculatePhaseDiagram} needs --
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
     * wired through {@link ApplicationLayer} -- see {@code
     * ApplicationLayer#calculateCoarseBinaryDiagram}/
     * {@code calculateCoarseTernaryDiagram}.
     */
    public ui.result.CoarseDiagramResult runCoarseDiagram(PhaseDiagramRequest request) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runCoarseDiagram");
        try {
            ModelSelection model = new ModelSelection(request.getTdbFilePath(),
                    request.getElements(), request.getPhases());

            List<ui.request.AxisConfig> axes = request.getAxes();
            ui.request.AxisConfig axisX = axes.get(0);
            ui.request.AxisConfig axisY = axes.get(1);
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
     * Run a two-axis ZPF MAP calculation from the GUI's {@link
     * PropertyScanRequest} (built by {@link PropertyCalcConfigPanel} in
     * MAP mode) -- the same {@link ApplicationLayer#calculatePhaseDiagram}
     * 2-axis pathway (Sundman Algorithms A/B/C1/C2/D) the CLI's {@code map}
     * command already uses (see {@code CliApp#runMap}), via {@link
     * CalculationKind#PHASE_DIAGRAM}, not a separate MAP engine. Returns
     * the real {@link PhaseDiagramResult} (ZPF boundary lines/nodes)
     * directly -- unlike {@link #runPropertyScan}, this is not squeezed
     * into {@link PropertyScanResult}, whose dense-grid shape cannot
     * represent this result (see that method's Javadoc). Callers should
     * render the result with {@code PhaseDiagramPanel}, the same renderer
     * {@link #runPhaseDiagram} already feeds.
     *
     * <p>{@link ApplicationLayer#calculatePhaseDiagram}'s 2-axis case
     * requires {@code axes[1]} to be the COMPOSITION axis (the one solved
     * for/released at each boundary) -- see that method's Javadoc, and
     * {@code CliApp#runMap}'s own comment noting the same constraint.
     * {@link PropertyCalcConfigPanel} lets the user set either GUI slot
     * (axis 0 or axis 1) to COMPOSITION or TEMPERATURE, so the two request
     * axes are reordered here by actual type, not by GUI slot index.
     *
     * @throws IllegalArgumentException if neither or both request axes are
     *         COMPOSITION -- {@link ApplicationLayer#calculatePhaseDiagram}
     *         needs exactly one
     */
    public PhaseDiagramResult runMap(PropertyScanRequest request) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runMap");
        try {
            ModelSelection model = new ModelSelection(
                    request.getTdbFilePath(), request.getElements(), request.getPhases());

            ui.request.AxisConfig axis0 = toMapAxisConfig(
                    request.getAxis0Type(), request.getAxis0Min(), request.getAxis0Max(),
                    request.getAxis0Step(), 0);
            ui.request.AxisConfig axis1 = toMapAxisConfig(
                    request.getAxis1Type(), request.getAxis1Min(), request.getAxis1Max(),
                    request.getAxis1Step(), 1);

            boolean axis0IsComp = axis0.type == ui.request.AxisConfig.Type.COMPOSITION;
            boolean axis1IsComp = axis1.type == ui.request.AxisConfig.Type.COMPOSITION;
            if (axis0IsComp == axis1IsComp) {
                throw new IllegalArgumentException(
                        "MAP requires exactly one axis to be COMPOSITION (got "
                        + request.getAxis0Type() + "/" + request.getAxis1Type() + ")");
            }
            ui.request.AxisConfig[] axes = axis1IsComp
                    ? new ui.request.AxisConfig[] { axis0, axis1 }
                    : new ui.request.AxisConfig[] { axis1, axis0 };
            double[] startAxes = { axes[0].min, axes[1].min };

            int nc = request.getElements().size();
            double[] comp = parseMapStartingComposition(request.getFixedX(), nc);

            CalculationInterface.PhaseDiagramParams params = new CalculationInterface.PhaseDiagramParams(
                    axes, startAxes, request.getFixedT(), request.getFixedP(), comp);
            PhaseDiagramResult r = CalculationInterface.runCalculating(
                    calculationSession, CalculationKind.PHASE_DIAGRAM, model, params);

            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runMap");
            return r;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "MAP calculation failed", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runMap");
            throw new RuntimeException("MAP calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parses MAP starting composition from user input (CSV string in
     * {@link PropertyCalcConfigPanel}'s "Starting composition" field) or
     * falls back to uniform. Input must be a comma-separated list of
     * component fractions summing to 1.0 (with tolerance), or null to use
     * uniform fallback.
     */
    private double[] parseMapStartingComposition(double userInputValue, int numComponents) {
        double[] comp = new double[numComponents];

        // userInputValue comes from fixedXField, which stores a single number
        // for STEP but for MAP stores the first component fraction directly.
        // For a proper CSV input in MAP mode, the UI would pass a string,
        // but PropertyScanRequest.fixedX is typed as double, not String.
        // As a practical workaround, treat userInputValue as the first component,
        // rest uniform-fill, ensuring normalization.

        if (numComponents <= 0) {
            return comp;
        }

        if (numComponents == 1) {
            comp[0] = 1.0;
            return comp;
        }

        // For simplicity with the existing fixedX field (double, not String),
        // use userInputValue as the first component and distribute the remainder
        // uniformly among the others, then normalize to sum to 1.
        double x1 = Math.max(0.0, Math.min(1.0, userInputValue)); // clamp to [0, 1]
        double remainder = 1.0 - x1;
        int otherCount = numComponents - 1;

        comp[0] = x1;
        for (int i = 1; i < numComponents; i++) {
            comp[i] = remainder / otherCount;
        }

        // Verify normalization
        double sum = 0.0;
        for (double c : comp) sum += c;
        if (Math.abs(sum - 1.0) > 1e-9) {
            // Fallback to uniform if something went wrong
            Arrays.fill(comp, 1.0 / numComponents);
        }

        return comp;
    }

    /** Builds one {@link ui.request.AxisConfig} for {@link #runMap}; {@code slot} is the composition component index used when {@code type == COMPOSITION}. */
    private ui.request.AxisConfig toMapAxisConfig(PropertyScanRequest.AxisType type,
                                                    double min, double max, double step, int slot) {
        if (type == PropertyScanRequest.AxisType.TEMPERATURE) {
            return new ui.request.AxisConfig("T", ui.request.AxisConfig.Type.TEMPERATURE, min, max, step);
        }
        return new ui.request.AxisConfig("x(" + slot + ")", slot, min, max, step);
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
     * The {@link application.calctype.CalculationGroup#ASSESS} ("opt") landing
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
     * Routed through {@link ApplicationLayer#availableDatabases}, per
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
     * <p>Routed through {@link ApplicationLayer#availableElements}
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
     * <p>Routed through {@link ModelInspectionService} which wires through
     * {@link ApplicationLayer} and reuses the cached TDB parse across
     * multiple inspector lookups.
     */
    public List<?> getPhaseParameters(String tdbPath, List<String> elements, String phaseName) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "getPhaseParameters");
        try {
            List<?> params = inspectionService.getPhaseParameters(tdbPath, elements, phaseName);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "getPhaseParameters");
            return params;
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "getPhaseParameters error", e);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "getPhaseParameters");
            return new ArrayList<>();
        }
    }

    /**
     * Property scan -- STEP only. MAP is not representable through this
     * method: unlike CLI's {@code map} command (see {@link #runMap}), a
     * GUI request arriving here as {@code PropertyScanRequest} with
     * {@code scanType == MAP} would need to become a {@link
     * PropertyScanResult}, but the actual MAP calculation engine ({@link
     * ApplicationLayer#calculatePhaseDiagram}'s 2-axis case, same as
     * {@link #runPhaseDiagram}) returns ZPF boundary lines/nodes, not the
     * dense {@code double[][]} grid {@link PropertyScanResult#getMapValues()}
     * requires -- so this cannot honestly build one. Callers wanting MAP
     * must call {@link #runMap} instead, which returns the real
     * {@link PhaseDiagramResult}.
     *
     * <p>Routed through {@link CalculationInterface#runCalculating} with
     * {@link CalculationKind#STEP}, the same {@link CalculationInterface}
     * call the CLI's {@code step} command uses -- reuses {@code StepTracer}
     * rather than calling it directly. {@code StepTracer} walks one axis
     * and records phase-stability-boundary lines ({@code
     * PhaseDiagramResult}), not a scalar thermodynamic property curve, so
     * the mapping into {@link PropertyScanResult} below plots stable-phase
     * count per sampled point rather than inventing a property value the
     * engine does not compute.
     */
    public PropertyScanResult runPropertyScan(PropertyScanRequest request) {
        Trace.enter(LOG, AppLevel.FLOW, "MainController", "runPropertyScan");
        if (request.getScanType() != PropertyScanRequest.ScanType.STEP) {
            PropertyScanResult err = new PropertyScanResult();
            err.setSuccess(false);
            err.setMessage("MAP scan result cannot be represented as PropertyScanResult "
                    + "-- call runMap() instead");
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runPropertyScan");
            return err;
        }
        try {
            ModelSelection model = new ModelSelection(
                    request.getTdbFilePath(), request.getElements(), request.getPhases());

            ui.request.AxisConfig axis = toStepAxisConfig(request);
            double fixedT = request.getAxis0Type() == PropertyScanRequest.AxisType.TEMPERATURE
                    ? request.getAxis0Min() : request.getFixedT();
            double fixedP = request.getFixedP();
            double[] comp = toOverallComposition(request);

            CalculationInterface.StepParams params =
                    new CalculationInterface.StepParams(axis, fixedT, fixedP, comp);
            PhaseDiagramResult result = CalculationInterface.runCalculating(
                    calculationSession, CalculationKind.STEP, model, params);

            PropertyScanResult scanResult = toPropertyScanResult(request, result);
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runPropertyScan");
            return scanResult;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "STEP calculation failed", e);
            PropertyScanResult err = new PropertyScanResult();
            err.setSuccess(false);
            err.setMessage("STEP calculation failed: " + e.getMessage());
            Trace.exit(LOG, AppLevel.FLOW, "MainController", "runPropertyScan");
            return err;
        }
    }

    /**
     * Builds the {@link ui.request.AxisConfig} {@link CalculationInterface.StepParams}
     * needs from the GUI's {@link PropertyScanRequest}. Only {@code
     * TEMPERATURE}/{@code COMPOSITION} axis 0 are offered by {@link
     * PropertyCalcConfigPanel}; composition scans use overall component 0
     * as the swept index, matching that panel's single-composition-axis UI.
     */
    private ui.request.AxisConfig toStepAxisConfig(PropertyScanRequest request) {
        if (request.getAxis0Type() == PropertyScanRequest.AxisType.TEMPERATURE) {
            return new ui.request.AxisConfig("T", ui.request.AxisConfig.Type.TEMPERATURE,
                    request.getAxis0Min(), request.getAxis0Max(), request.getAxis0Step());
        }
        return new ui.request.AxisConfig("x(1)", 0,
                request.getAxis0Min(), request.getAxis0Max(), request.getAxis0Step());
    }

    /**
     * Overall composition for the STEP call. When the swept axis is itself
     * COMPOSITION, {@link ApplicationLayer#calculateStep} overrides
     * component 0 at each sampled point, so the starting composition here
     * only needs to fill the remaining components; a uniform split matches
     * {@link #extractComposition}'s fallback for single-point calculations.
     */
    private double[] toOverallComposition(PropertyScanRequest request) {
        int nc = request.getElements().size();
        double[] comp = new double[nc];
        if (request.getAxis0Type() == PropertyScanRequest.AxisType.TEMPERATURE && nc > 1) {
            comp[1 % nc] = request.getFixedX();
            comp[0] = 1.0 - comp[1 % nc];
        } else {
            Arrays.fill(comp, nc == 0 ? 0.0 : 1.0 / nc);
        }
        return comp;
    }

    /**
     * Maps a {@code StepTracer} run's {@link PhaseDiagramResult} into the
     * GUI's existing {@link PropertyScanResult}/{@link StepResultPanel}
     * shape: one point per sampled axis value across all returned line
     * segments (concatenated in order), y = {@link
     * PhaseDiagramResult.LineSegment#propertyValues} -- system Gibbs
     * energy per mole of real atoms ("Gm"), the real per-point quantity
     * {@code StepTracer} now computes via {@link
     * system.ports.EquilibriumResult#totalGPerAtom()} (see {@code
     * StepTracer#molarGibbsEnergyPerAtom}), not a placeholder.
     *
     * <p>{@code request.getMethod()} selects "HM"/"Gm"/"G" the same
     * dropdown {@link SinglePointSidebarPanel} offers, but only "Gm" is
     * backed by real machinery here: no system-level enthalpy ("HM")
     * calculation exists anywhere in this codebase (single-point
     * calculations drop {@code method} unused today -- see {@link
     * #runSinglePoint}), and "G" (total, per formula unit rather than per
     * atom) is not comparable across a scan whose stable phase set can
     * change. Requesting "HM" or "G" therefore returns Gm anyway, labeled
     * honestly as Gm with a message explaining the substitution, rather
     * than silently mislabeling the curve or fabricating an unimplemented
     * property.
     */
    private PropertyScanResult toPropertyScanResult(PropertyScanRequest request,
                                                      PhaseDiagramResult result) {
        List<Double> axisValues = new ArrayList<>();
        List<Double> propertyValues = new ArrayList<>();
        for (PhaseDiagramResult.LineSegment line : result.getLines()) {
            for (int i = 0; i < line.coords.size(); i++) {
                axisValues.add(line.coords.get(i)[0]);
                propertyValues.add(line.propertyValues != null
                        ? line.propertyValues[i] : Double.NaN);
            }
        }

        PropertyScanResult scanResult = new PropertyScanResult();
        double[] xs = axisValues.stream().mapToDouble(Double::doubleValue).toArray();
        double[] ys = propertyValues.stream().mapToDouble(Double::doubleValue).toArray();
        scanResult.setAxis0Values(xs);
        scanResult.setStepValues(ys);
        scanResult.setAxis0Label(request.getAxis0Type() == PropertyScanRequest.AxisType.TEMPERATURE
                ? "T / K" : "x(1)");
        scanResult.setPropertyLabel("Gm / J·mol⁻¹");
        scanResult.setMethod("Gm");

        boolean substituted = !"Gm".equals(request.getMethod());
        boolean success = result.isComplete() && xs.length > 0;
        scanResult.setSuccess(success);
        String outcome = result.isComplete()
                ? (xs.length > 0 ? "OK" : "No points computed")
                : result.getMessage();
        scanResult.setMessage(substituted
                ? outcome + " (requested \"" + request.getMethod()
                        + "\" not implemented for STEP -- showing Gm)"
                : outcome);
        return scanResult;
    }
}
