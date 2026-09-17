package calc.diagram;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.List;

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

        // Algorithm C1 (Fig. 5) + C2 (Fig. 6): LineFollower walks the
        // line and detects the crossing, NodeTerminator resolves it
        // exactly (fix the changed phase at zero, release the axis,
        // solve) rather than StepTracer's black-box bisection -- STEP's
        // crossings are never invariant/isopleth-shaped (fixedPhases is
        // always empty for a step line, so NodeTerminator.isStep routes
        // every crossing to the "+1 exit" STEP_CONTINUATION case before
        // classifyNode/conds is ever consulted), so `conds` is null here.
        java.util.function.BiConsumer<Line, EquilibriumResult> onCrossing =
                NodeTerminator.crossingHandlerFor(axis, fixedT, fixedP, compOverall, null, candidates, registry);

        LineFollower.EquilibriumStepper stepper = stepperFor(axis, fixedT, fixedP, compOverall);
        LineFollower.SegmentWalker walker = (line, walkCandidates) ->
                LineFollower.walkLineAlgorithmC1(
                        line, axis, stepper, onCrossing, walkCandidates,
                        LineFollower.DEFAULT_GLOBAL_CHECK_INTERVAL);
        return new LineFollower.Setup(registry, walker);
    }

    /** Builds a {@link LineFollower.EquilibriumStepper} for {@code axis}, closing over the fixed conditions. */
    private static LineFollower.EquilibriumStepper stepperFor(
            AxisConfig axis, double fixedT, double fixedP, double[] compOverall) {
        return (axisValue, candidates) -> {
            double t = fixedT, p = fixedP;
            double[] comp = compOverall.clone();
            switch (axis.type) {
                case TEMPERATURE: t = axisValue; break;
                case PRESSURE: p = axisValue; break;
                case COMPOSITION: comp = StepTracer.applyCompositionAxis(axis, axisValue, comp); break;
                default: throw new IllegalStateException("Unhandled axis type: " + axis.type);
            }
            return EquilibriumSolveHelper.solveOrSentinel(t, p, comp, candidates);
        };
    }
}
