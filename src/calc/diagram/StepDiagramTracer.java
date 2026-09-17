package calc.diagram;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.List;
import java.util.Set;

/**
 * The C1 drain loop for STEP (one axis, Sundman 2021 Calphad 75 §3.2) --
 * the STEP-side counterpart of {@link MapDiagramTracer}, closing {@code
 * docs/roadmap_phase_diagrams.md}'s "StepTracer.trace exists, not wired
 * into Node / NodeRegistry network" gap.
 *
 * <p><b>Why this is a separate class from {@link MapDiagramTracer}, not a
 * shared one, despite both draining the same {@link Node}/{@link
 * NodeRegistry}/{@link NodeGeometry} machinery (per this codebase's "one
 * engine" design -- see {@link Condition}'s javadoc):</b> STEP has no ZPF
 * line at all -- no phase is ever fixed at zero amount and no composition
 * axis is ever released (the flowchart doc's explicit callout: "step has
 * no ZPF-fixed axis at all"). {@link MapTracer#walkOneSegment} always
 * calls {@link calc.equil.EquilibriumSolverV2#solveBoundary} to locate a
 * crossing exactly; STEP instead locates it by black-box bisection ({@link
 * StepTracer#walkOneSegment}) since there is nothing to release. What
 * generalizes across both is the OUTER bookkeeping -- {@link Node}, {@link
 * Line}, {@link NodeRegistry}, {@link NodeGeometry}'s exit-attachment,
 * {@link PhaseDiagramEngine#isGloballyStable} -- not the per-segment walk
 * mechanism itself, which the paper and OC both keep genuinely distinct
 * for STEP vs. MAP (Sundman 2021 §3.2 vs. §3.3; OC's {@code map_step} vs.
 * {@code map_calcnode}'s ZPF-releasing branch).
 *
 * <p><b>Exit geometry, per §3.2's own explicit distinction</b> (already
 * documented in the flowchart doc, now implemented here): the START node
 * gets 2 exits, one per direction (created directly by {@link #drain},
 * not via {@link NodeGeometry} -- there is no "arriving line" at the very
 * first node). Every node created afterward by a stable-set change gets
 * exactly 1 exit, continuing in the SAME direction the arriving line was
 * already walking ({@link PhaseDiagramEngine.NodeClass#STEP_CONTINUATION},
 * {@link NodeGeometry}'s direction-taking {@code attachExits} overload).
 *
 * <p><b>Global stability check</b> reuses {@link
 * PhaseDiagramEngine#isGloballyStable} at every newly created node,
 * matching {@link MapDiagramTracer}'s same check -- a failing node gets
 * no exit and its arriving {@link Line} is {@link Line#markExcluded
 * marked excluded}.
 */
public final class StepDiagramTracer {

    /** T/P node-matching tolerance passed to {@link NodeRegistry#findOrCreate}. */
    private final double tpMatchTolerance;

    /** Chemical-potential node-matching tolerance passed to {@link NodeRegistry#findOrCreate}. */
    private final double muMatchTolerance;

    /** Uses OC's own node-matching tolerances (see {@link Node#matches(Node)}). */
    public StepDiagramTracer() {
        this(Node.DEFAULT_TP_RELATIVE_TOLERANCE, Node.DEFAULT_MU_RELATIVE_TOLERANCE);
    }

    public StepDiagramTracer(double tpMatchTolerance, double muMatchTolerance) {
        this.tpMatchTolerance = tpMatchTolerance;
        this.muMatchTolerance = muMatchTolerance;
    }

    /**
     * Drains the C1 loop for a STEP calculation: solves the starting
     * point directly (no initial search needed, unlike MAP -- §3.2:
     * "a step command generates a node at the initial equilibrium"),
     * creates the START {@link Node} there, attaches 2 pending exits
     * (one per direction), and walks every pending {@link Line} to
     * completion.
     *
     * @param axis        the single axis to walk
     * @param fixedT      temperature when {@code axis.type != TEMPERATURE}
     * @param fixedP      pressure when {@code axis.type != PRESSURE}
     * @param compOverall overall composition when {@code axis.type != COMPOSITION}
     * @param candidates  candidate phase models
     * @return the populated registry: 1 start node plus every node
     *         created by a stable-set change, and every line walked
     * @throws IllegalStateException if the starting point does not converge
     */
    public NodeRegistry drain(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        return drain(axis, fixedT, fixedP, compOverall, candidates, null);
    }

    /**
     * Same as {@link #drain(AxisConfig, double, double, double[], List)},
     * but for a caller (Algorithm B's own dispatcher --
     * {@link PhaseDiagramEngine}/{@code CalculationSession}) that already
     * holds the ONE shared initial equilibrium Algorithm B solves before
     * branching STEP vs. MAP (Sundman 2021 Section 3: "the simplest way
     * to start is to set the appropriate conditions for a single
     * equilibrium calculation and THEN select one or more conditions as
     * axis variable"). Passing {@code null} re-solves the starting point
     * internally, matching the other overload's behavior exactly (kept
     * for standalone callers, e.g. tests, that have no such shared result
     * to offer).
     *
     * @param startResult the already-solved equilibrium at {@code
     *                    axis.min}, or {@code null} to solve it here
     */
    public NodeRegistry drain(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        StepTracer tracer = new StepTracer();
        NodeRegistry registry = new NodeRegistry();

        double t0 = fixedT, p0 = fixedP;
        double[] comp = compOverall.clone();
        switch (axis.type) {
            case TEMPERATURE: t0 = axis.min; break;
            case PRESSURE: p0 = axis.min; break;
            case COMPOSITION: comp = StepTracer.applyCompositionAxis(axis, axis.min, comp); break;
            default: throw new IllegalStateException("Unhandled axis type: " + axis.type);
        }

        if (startResult == null) {
            startResult = EquilibriumSolveHelper.solveOrSentinel(t0, p0, comp, candidates);
        }
        if (!startResult.isConverged()) {
            throw new IllegalStateException(
                    "Starting point did not converge at " + axis.name + "=" + axis.min
                    + " -- nothing for the drain loop to start from.");
        }

        Node startNode = registry.findOrCreate(
                startResult, new double[] { axis.min }, comp, tpMatchTolerance, muMatchTolerance);

        // §3.2: "a step command generates a node at the initial
        // equilibrium with two exits, one in each direction of the
        // axis" -- attached directly, not via NodeGeometry (no arriving
        // line to derive a "same direction" from at the very first node).
        startNode.addLine(new Line(startNode, List.of(), 0, +1));
        startNode.addLine(new Line(startNode, List.of(), 0, -1));

        while (registry.hasPendingWork()) {

            Line line = registry.nextPendingLine();
            Node fromNode = line.startNode;
            line.startWalking();

            AxisConfig directedAxis = directed(axis, line.direction);
            double[] compAtStart = compAtNode(fromNode, axis, compOverall);

            StepTracer.SegmentResult seg = tracer.walkOneSegment(
                    fromNode.axisValues[0], fromNode.stablePhaseNames,
                    directedAxis, fixedT, fixedP, compAtStart, candidates,
                    StepTracer.DEFAULT_GLOBAL_CHECK_INTERVAL);

            for (int i = 0; i < seg.points.size(); i++) {
                line.addPoint(seg.points.get(i), new double[] { seg.axisValues.get(i) });
            }

            switch (seg.end) {
                case AXIS_LIMIT:
                case NON_CONVERGENT:
                    line.terminateAtAxisLimit();
                    break;

                case GLOBALLY_UNSTABLE:
                    // §2.3.3's mid-line check: "abandon this line and
                    // suppress it" -- no node created, matching the
                    // node-level failure's treatment below.
                    line.terminateAtAxisLimit();
                    line.markExcluded();
                    break;

                case CROSSING: {
                    double[] crossingComp = compAtAxisValue(directedAxis, seg.endAxisValue, compAtStart);
                    Node endNode = registry.findOrCreate(
                            seg.crossingResult, new double[] { seg.endAxisValue },
                            crossingComp, tpMatchTolerance, muMatchTolerance);
                    line.terminateAtNode(endNode);

                    if (endNode.getLines().isEmpty()) {
                        if (!PhaseDiagramEngine.isGloballyStable(seg.crossingResult, candidates)) {
                            // Global stability check (§2.3.3), same pattern
                            // as MapDiagramTracer: abandon and suppress
                            // this line, no exits attached.
                            line.markExcluded();
                            break;
                        }
                        String changedPhase = findChangedPhase(fromNode.stablePhaseNames, seg.newStableNames);
                        NodeGeometry.attachExits(
                                endNode, PhaseDiagramEngine.NodeClass.STEP_CONTINUATION,
                                changedPhase, 0, line.direction);
                    }
                    break;
                }
            }
        }

        return registry;
    }

    /** The overall composition to resume walking from at {@code node} -- {@code
     *  node.overallComposition} when tracked, else the caller's original starting
     *  composition (matches {@link MapDiagramTracer}'s same per-node tracking need). */
    private static double[] compAtNode(Node node, AxisConfig axis, double[] fallback) {
        return node.overallComposition != null ? node.overallComposition : fallback.clone();
    }

    /** Applies {@code axis}'s COMPOSITION renormalization at {@code axisValue}, or
     *  returns {@code comp} unchanged for a TEMPERATURE/PRESSURE axis (whose own
     *  value is not part of the composition vector). */
    private static double[] compAtAxisValue(AxisConfig axis, double axisValue, double[] comp) {
        return axis.type == AxisConfig.Type.COMPOSITION
                ? StepTracer.applyCompositionAxis(axis, axisValue, comp)
                : comp.clone();
    }

    /** The single phase name present in exactly one of the two sets (appeared or
     *  disappeared) -- STEP's crossings are always single-phase-change by construction
     *  (bisection only stops once exactly one phase differs), so exactly one such name exists. */
    private static String findChangedPhase(Set<String> before, Set<String> after) {
        for (String name : after) {
            if (!before.contains(name)) return name;
        }
        for (String name : before) {
            if (!after.contains(name)) return name;
        }
        throw new IllegalStateException(
                "No differing phase name between " + before + " and " + after
                + " -- should be impossible at a detected crossing.");
    }

    /** Returns an {@link AxisConfig} with the same range but a step whose sign matches {@code direction}. */
    private static AxisConfig directed(AxisConfig axis, int direction) {
        double step = Math.abs(axis.step) * Math.signum(direction);
        if (axis.type == AxisConfig.Type.COMPOSITION) {
            return new AxisConfig(axis.name, axis.componentIndex, axis.min, axis.max, step);
        }
        return new AxisConfig(axis.name, axis.type, axis.min, axis.max, step);
    }
}
