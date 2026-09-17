package calc.diagram;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.List;

/**
 * Drains the C1 mapping loop (Algorithm C1/C2/D via {@link LineFollower}/
 * {@link NodeTerminator}): given a starting equilibrium, repeatedly walks
 * a pending {@link Line} and on a crossing either reuses an existing
 * {@link Node} or creates one with its exit lines, until no pending lines
 * remain. Supports binary T-x, ternary isothermal, and isopleth diagrams
 * via {@link ConditionSet}; single-walk-axis only.
 */
public final class MapDiagramTracer {

    /** T/P node-matching tolerance passed to {@link NodeRegistry#findOrCreate}. */
    private final double tpMatchTolerance;

    /** Chemical-potential node-matching tolerance passed to {@link NodeRegistry#findOrCreate}. */
    private final double muMatchTolerance;

    /** Uses OC's own node-matching tolerances (see {@link Node#matches(Node)}). */
    public MapDiagramTracer() {
        this(Node.DEFAULT_TP_RELATIVE_TOLERANCE, Node.DEFAULT_MU_RELATIVE_TOLERANCE);
    }

    public MapDiagramTracer(double tpMatchTolerance, double muMatchTolerance) {
        this.tpMatchTolerance = tpMatchTolerance;
        this.muMatchTolerance = muMatchTolerance;
    }

    /**
     * Drains a binary MAP calculation. The true start {@link Node} is
     * located by searching {@code walkAxis} from {@code startWalkValue}
     * (holding {@code releaseAxis} fixed), not necessarily the starting
     * condition itself.
     *
     * @param walkAxis       axis searched initially, then walked by every line
     * @param releaseAxis    composition axis solved for exactly at each boundary
     * @param fixedT         temperature when {@code walkAxis.type != TEMPERATURE}
     * @param fixedP         pressure when {@code walkAxis.type != PRESSURE}
     * @param startWalkValue the caller's starting value for {@code walkAxis}
     * @param compOverall    starting overall composition
     * @param candidates     candidate phase models
     * @return the populated registry: start node plus any nodes created
     *         by resolved crossings, and every line walked
     * @throws IllegalStateException if the initial search finds no
     *         stable-set change anywhere in {@code walkAxis}'s range
     */
    public NodeRegistry drain(
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        return drain(walkAxis, releaseAxis, fixedT, fixedP, startWalkValue, compOverall, candidates, null);
    }

    /**
     * As {@link #drain(AxisConfig, AxisConfig, double, double, double,
     * double[], List)}, with a pre-solved starting equilibrium.
     *
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     */
    public NodeRegistry drain(
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        LineFollower.Setup setup = setUp(
                walkAxis, releaseAxis, fixedT, fixedP, startWalkValue, compOverall, candidates, startResult);
        LineFollower.drain(setup, candidates);
        return setup.registry();
    }

    /**
     * As {@link #setUp(ConditionSet, int, int, double, double[], List,
     * EquilibriumResult)}, translating {@code walkAxis}/{@code
     * releaseAxis} into a binary {@link ConditionSet} first.
     *
     * @throws IllegalArgumentException if {@code releaseAxis.type != COMPOSITION}
     */
    LineFollower.Setup setUp(
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        if (releaseAxis.type != AxisConfig.Type.COMPOSITION) {
            throw new IllegalArgumentException(
                    "MapDiagramTracer's release axis must be COMPOSITION; got " + releaseAxis.type);
        }

        ConditionSet conds = ConditionSet.fromBinaryAxes(
                compOverall.length, walkAxis, releaseAxis, fixedT, fixedP, compOverall);

        // fromBinaryAxes puts exactly 2 axis conditions in, in the order
        // added: the walk axis first, the release (composition) axis
        // second -- see its own source. Index 0/1 here is not a
        // coincidence to re-derive per call; it is that method's own
        // fixed construction order.
        return setUp(conds, 0, 1, startWalkValue, compOverall, candidates, startResult);
    }

    /**
     * Drains a MAP calculation for any diagram type expressible as a
     * {@link ConditionSet}: binary T-x, ternary isothermal, or isopleth.
     *
     * @param conds            the full condition set (n+2 conditions)
     * @param searchAxisIndex  index of the axis searched initially, then
     *                         walked by every line in the diagram
     * @param releaseAxisIndex index of the composition axis released at every crossing
     * @param startSearchValue the search axis's value at the caller's starting condition
     * @param compOverall      starting overall composition (length {@code conds.numComponents()})
     * @param candidates       candidate phase models
     * @return the populated registry: start node plus any nodes created
     *         by resolved crossings, and every line walked
     * @throws IllegalStateException if the initial search finds no
     *         stable-set change anywhere in the search axis's range
     */
    public NodeRegistry drain(
            ConditionSet conds,
            int searchAxisIndex,
            int releaseAxisIndex,
            double startSearchValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        return drain(conds, searchAxisIndex, releaseAxisIndex, startSearchValue, compOverall, candidates, null);
    }

    /**
     * As {@link #drain(ConditionSet, int, int, double, double[], List)},
     * with a pre-solved starting equilibrium.
     *
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     */
    public NodeRegistry drain(
            ConditionSet conds,
            int searchAxisIndex,
            int releaseAxisIndex,
            double startSearchValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        LineFollower.Setup setup = setUp(
                conds, searchAxisIndex, releaseAxisIndex, startSearchValue, compOverall, candidates, startResult);
        LineFollower.drain(setup, candidates);
        return setup.registry();
    }

    /**
     * Fig. 4's MAP branch up to "+node, 2 exits": searches for the first
     * stable-set change, creates the start node and its 2 pending exits,
     * without yet handing off to C1.
     *
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     * @return the registry (with its start node) and the {@link
     *         LineFollower.SegmentWalker} to drain it with
     * @throws IllegalStateException if the initial search finds no
     *         stable-set change anywhere in the search axis's range
     */
    LineFollower.Setup setUp(
            ConditionSet conds,
            int searchAxisIndex,
            int releaseAxisIndex,
            double startSearchValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        List<Condition> axes = conds.axisConditions();
        Condition searchCondition = axes.get(searchAxisIndex);
        Condition releaseCondition = axes.get(releaseAxisIndex);

        if (releaseCondition.variable != Condition.Variable.COMPOSITION) {
            throw new IllegalArgumentException(
                    "The release axis must be a COMPOSITION condition; got "
                    + releaseCondition.variable + " (" + releaseCondition + ")");
        }

        AxisConfig walkAxis = searchCondition.toAxisConfig();
        AxisConfig releaseAxis = releaseCondition.toAxisConfig();

        MapTracer tracer = new MapTracer();
        NodeRegistry registry = new NodeRegistry();

        double[] startComp = compOverall.clone();
        if (walkAxis.type == AxisConfig.Type.COMPOSITION) {
            startComp = StepTracer.applyCompositionAxis(walkAxis, startSearchValue, startComp);
        }

        MapTracer.InitialBoundaryResult initial = startResult == null
                ? tracer.findInitialBoundary(
                        conds, searchAxisIndex, startSearchValue, releaseAxisIndex, startComp, candidates)
                : tracer.findInitialBoundary(
                        conds, searchAxisIndex, startSearchValue, releaseAxisIndex, startComp, candidates,
                        startResult);

        if (!initial.found) {
            throw new IllegalStateException(
                    "No stable-set change found searching " + walkAxis.name + " in ["
                    + walkAxis.min + ", " + walkAxis.max + "] from the starting condition -- "
                    + "nothing for the drain loop to start from.");
        }

        double[] startNodeComp = initial.segment.endComposition;
        Node startNode = registry.findOrCreate(
                initial.equilibrium,
                new double[] { initial.crossingSearchValue, startNodeComp[releaseAxis.componentIndex] },
                startNodeComp, tpMatchTolerance, muMatchTolerance);

        // Sundman 2021 §3.3: "A first node point will be created with the
        // appearing/disappearing phase as fix with zero amount and with
        // two exits" -- matches OC's own map_startpoint (linefixph copied
        // onto both exits for tieline_inplane>0, smp2A.F90 ~1533-1541).
        // The START node still has no ARRIVING line, so no already-fixed
        // phase of its own to form an isopleth crossing with (that
        // distinction stays NodeGeometry's, for nodes found mid-walk).
        List<String> startNodeFixedPhase = List.of(initial.segment.changedPhase);
        startNode.addLine(new Line(startNode, startNodeFixedPhase, 0, +1));
        startNode.addLine(new Line(startNode, startNodeFixedPhase, 0, -1));

        // Algorithm C1 (Fig. 5) + C2 (Fig. 6) + D (Fig. 7, via
        // NodeGeometry.attachExits inside NodeTerminator): the same
        // LineFollower/NodeTerminator chain StepDiagramTracer now uses,
        // generalized to any ConditionSet-expressible diagram type (binary
        // T-x, ternary isothermal, isopleth) via the walkAxisIndex=0
        // translation -- every Line created by this setUp/NodeGeometry
        // walks conds.axisConditions().get(0), matching MapTracer's own
        // ConditionSet overloads' fixed index-0 convention.
        java.util.function.BiConsumer<Line, EquilibriumResult> onCrossing =
                NodeTerminator.crossingHandlerFor(conds, 0, compOverall, candidates, registry);
        LineFollower.EquilibriumStepper stepper = stepperFor(conds, 0);

        LineFollower.SegmentWalker walker = (line, walkCandidates) ->
                LineFollower.walkLineAlgorithmC1(
                        line, walkAxis, stepper, onCrossing, walkCandidates,
                        LineFollower.DEFAULT_GLOBAL_CHECK_INTERVAL);
        return new LineFollower.Setup(registry, walker);
    }

    /**
     * Builds a {@link LineFollower.EquilibriumStepper} that solves at a given
     * value of {@code conds}'s axis condition {@code walkAxisIndex}, holding
     * every other condition at {@code conds}'s own fixed/initial value --
     * generalizes {@link StepDiagramTracer}'s single-{@link AxisConfig}
     * stepper to a full {@link ConditionSet} (ternary isothermal/isopleth:
     * the non-walked axis condition may itself be COMPOSITION, not just T/P).
     */
    private static LineFollower.EquilibriumStepper stepperFor(ConditionSet conds, int walkAxisIndex) {
        List<Condition> axes = conds.axisConditions();
        Condition walkCondition = axes.get(walkAxisIndex);
        AxisConfig walkAxis = walkCondition.toAxisConfig();
        double fixedT = conds.fixedTemperature();
        double fixedP = conds.fixedPressure();
        double[] baseComp = conds.initialComposition();

        return (axisValue, candidates) -> {
            double t = fixedT, p = fixedP;
            double[] comp = baseComp.clone();
            switch (walkAxis.type) {
                case TEMPERATURE: t = axisValue; break;
                case PRESSURE: p = axisValue; break;
                case COMPOSITION: comp = StepTracer.applyCompositionAxis(walkAxis, axisValue, comp); break;
                default: throw new IllegalStateException("Unhandled axis type: " + walkAxis.type);
            }
            return EquilibriumSolveHelper.solveOrSentinel(t, p, comp, candidates);
        };
    }
}
