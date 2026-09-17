package calc.diagram;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drains the C1 mapping loop: given a starting equilibrium, repeatedly
 * walks a pending {@link Line} via {@link MapTracer#walkOneSegment} and
 * on a crossing either reuses an existing {@link Node} or creates one
 * with its exit lines, until no pending lines remain. Supports binary
 * T-x, ternary isothermal, and isopleth diagrams via {@link
 * ConditionSet}; single-walk-axis only.
 */
public final class MapDiagramTracer {

    /** T/P node-matching tolerance passed to {@link NodeRegistry#findOrCreate}. */
    private final double tpMatchTolerance;

    /** Chemical-potential node-matching tolerance passed to {@link NodeRegistry#findOrCreate}. */
    private final double muMatchTolerance;

    /**
     * Overall composition tracked per node id -- {@link Node} cannot
     * always recover this from its {@link EquilibriumResult} alone for a
     * multi-phase node (see {@link Node#overallComposition}'s javadoc),
     * so this loop threads it explicitly.
     */
    private final Map<Integer, double[]> compositionByNodeId = new HashMap<>();

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

    /** Returns an {@link AxisConfig} with the same range but a step whose sign matches {@code direction}. */
    private static AxisConfig directed(AxisConfig axis, int direction) {
        double step = Math.abs(axis.step) * Math.signum(direction);
        if (axis.type == AxisConfig.Type.COMPOSITION) {
            return new AxisConfig(axis.name, axis.componentIndex, axis.min, axis.max, step);
        }
        return new AxisConfig(axis.name, axis.type, axis.min, axis.max, step);
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
        double fixedT = conds.fixedTemperature();
        double fixedP = conds.fixedPressure();

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
        compositionByNodeId.putIfAbsent(startNode.id, startNodeComp);

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

        LineFollower.SegmentWalker walker = (line, walkCandidates) ->
                walkAndResolve(line, conds, walkAxis, releaseAxis, fixedT, fixedP,
                        walkCandidates, tracer, registry);
        return new LineFollower.Setup(registry, walker);
    }

    /** {@link LineFollower.SegmentWalker} body for MAP: walks one segment then resolves the crossing, if any. */
    private void walkAndResolve(
            Line line,
            ConditionSet conds,
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            List<GibbsEnergyModel> candidates,
            MapTracer tracer,
            NodeRegistry registry) {

        Node fromNode = line.startNode;
        String arrivingLineFixedPhase = line.fixedPhases.isEmpty() ? null : line.fixedPhases.get(0);

        AxisConfig directedWalkAxis = directed(walkAxis, line.direction);
        double[] compAtStart = compositionByNodeId.get(fromNode.id);

        MapTracer.SegmentResult seg = tracer.walkOneSegment(
                fromNode.axisValues[0], fromNode.stablePhaseNames,
                directedWalkAxis, releaseAxis, fixedT, fixedP,
                compAtStart, candidates, fromNode.equilibrium,
                StepTracer.DEFAULT_GLOBAL_CHECK_INTERVAL);

        for (int i = 0; i < seg.points.size(); i++) {
            line.addPoint(seg.points.get(i), seg.coords.get(i));
        }

        switch (seg.end) {
            case AXIS_LIMIT:
            case NON_CONVERGENT:
                line.terminateAtAxisLimit();
                break;

            case GLOBALLY_UNSTABLE:
                line.terminateAtAxisLimit();
                line.markExcluded();
                break;

            case CROSSING: {
                Node endNode = registry.findOrCreate(
                        seg.lastResult,
                        new double[] { seg.endWalkValue, seg.endComposition[releaseAxis.componentIndex] },
                        seg.endComposition, tpMatchTolerance, muMatchTolerance);
                compositionByNodeId.putIfAbsent(endNode.id, seg.endComposition);
                line.terminateAtNode(endNode);
                if (endNode.getLines().isEmpty()) {
                    if (!PhaseDiagramEngine.isGloballyStable(seg.lastResult, candidates)) {
                        line.markExcluded();
                        break;
                    }
                    PhaseDiagramEngine.NodeClass nodeClass = PhaseDiagramEngine.classifyNode(
                            conds, endNode.stablePhaseNames.size());
                    if (nodeClass == PhaseDiagramEngine.NodeClass.ISOPLETH_CROSSING) {
                        NodeGeometry.attachExits(endNode, nodeClass,
                                seg.changedPhase, arrivingLineFixedPhase, 0);
                    } else {
                        NodeGeometry.attachExits(endNode, nodeClass, seg.changedPhase, 0);
                    }
                }
                break;
            }

            case INVARIANT: {
                Node endNode = registry.findOrCreate(
                        seg.lastResult,
                        new double[] { seg.endWalkValue, seg.endComposition[releaseAxis.componentIndex] },
                        seg.endComposition, tpMatchTolerance, muMatchTolerance);
                compositionByNodeId.putIfAbsent(endNode.id, seg.endComposition);
                line.terminateAtNode(endNode);
                if (endNode.getLines().isEmpty()) {
                    if (!PhaseDiagramEngine.isGloballyStable(seg.lastResult, candidates)) {
                        line.markExcluded();
                        break;
                    }
                    NodeGeometry.attachExits(endNode, PhaseDiagramEngine.NodeClass.INVARIANT,
                            seg.changedPhase, 0);
                }
                break;
            }

            case UNRESOLVED_MULTI_PHASE_CHANGE: {
                Node endNode = registry.findOrCreate(
                        seg.lastResult,
                        new double[] { seg.endWalkValue, seg.endComposition[releaseAxis.componentIndex] },
                        seg.endComposition, tpMatchTolerance, muMatchTolerance);
                compositionByNodeId.putIfAbsent(endNode.id, seg.endComposition);
                line.terminateAtNode(endNode);
                break;
            }
        }
    }
}
