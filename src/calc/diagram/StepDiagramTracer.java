package calc.diagram;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.List;
import java.util.Set;

/**
 * Drains a STEP calculation (single axis, no ZPF line). Creates the start
 * node with 2 exits, then walks every pending line to completion.
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
     * Drains a STEP calculation from {@code axis.min}.
     *
     * @param axis        the single axis to walk
     * @param fixedT      temperature when {@code axis.type != TEMPERATURE}
     * @param fixedP      pressure when {@code axis.type != PRESSURE}
     * @param compOverall overall composition when {@code axis.type != COMPOSITION}
     * @param candidates  candidate phase models
     * @return the populated registry: start node plus every node created
     *         by a stable-set change, and every line walked
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
     * As {@link #drain(AxisConfig, double, double, double[], List)}, with
     * a pre-solved starting equilibrium.
     *
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     */
    public NodeRegistry drain(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        LineFollower.Setup setup = setUp(axis, fixedT, fixedP, compOverall, candidates, startResult);
        LineFollower.drain(setup, candidates);
        return setup.registry();
    }

    /**
     * Fig. 4's STEP branch up to "+node, 2 exits": creates the start node
     * and its 2 pending exits, without yet handing off to C1.
     *
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     * @return the registry (with its start node) and the {@link
     *         LineFollower.SegmentWalker} to drain it with
     * @throws IllegalStateException if the starting point does not converge
     */
    LineFollower.Setup setUp(
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

        LineFollower.SegmentWalker walker = (line, walkCandidates) ->
                walkAndResolve(line, axis, fixedT, fixedP, compOverall, walkCandidates, tracer, registry);
        return new LineFollower.Setup(registry, walker);
    }

    /** {@link LineFollower.SegmentWalker} body for STEP: walks one segment then resolves the crossing, if any. */
    private void walkAndResolve(
            Line line,
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            StepTracer tracer,
            NodeRegistry registry) {

        Node fromNode = line.startNode;
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
