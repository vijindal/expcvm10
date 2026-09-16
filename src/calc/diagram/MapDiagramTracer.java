package calc.diagram;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The C1 drain loop from {@code docs/phase_diagram_engine_flowchart.md}:
 * given a starting equilibrium, repeatedly picks a pending {@link Line}
 * from the {@link NodeRegistry}, walks it via {@link
 * MapTracer#walkOneSegment}, and on a crossing either reuses an existing
 * {@link Node} (Algorithm C2's "already found?" check) or creates one and
 * attaches its exit lines -- until no pending lines remain.
 *
 * <p><b>Every diagram type this engine's {@link ConditionSet} can
 * express</b> -- binary T-x, ternary isothermal (two free composition
 * axes), or a ternary+ isopleth (T + one free composition, one or more
 * OTHER compositions FIXED) -- runs through the SAME {@link
 * #drain(ConditionSet, int, int, double, double[], List)} loop below.
 * {@link #drain(AxisConfig, AxisConfig, double, double, double,
 * double[], List)} is a thin translation layer over it (via {@link
 * ConditionSet#fromBinaryAxes}) kept for existing callers that only ever
 * describe a binary T-x map with {@code AxisConfig} pairs (e.g. {@link
 * PhaseDiagramEngine#drainC1Loop}) -- both overloads share one
 * implementation, not two copies.
 *
 * <p><b>Scope, matching {@code docs/roadmap_phase_diagrams.md}'s
 * step-by-step build order:</b> a resolved {@link
 * MapTracer.SegmentEnd#CROSSING} is classified via {@link
 * PhaseDiagramEngine#classifyNode(ConditionSet, int)} (Eq. 8 plus the
 * paper's own §3.3 tie-line-in-plane/isopleth distinction) and given
 * exits via {@link NodeGeometry} -- {@code TIE_LINE_IN_PLANE} (2 exits)
 * for the ordinary case, {@code ISOPLETH_CROSSING} (2 or 3 exits,
 * depending on whether the arriving line already had a fixed phase of
 * its own) for an isopleth-shaped diagram, or a genuine {@link
 * MapTracer.SegmentEnd#INVARIANT} routed through Algorithm D ({@link
 * InvariantExitFinder}). {@link
 * MapTracer.SegmentEnd#UNRESOLVED_MULTI_PHASE_CHANGE} is still out of
 * scope -- that crossing could not even be resolved to a specific node
 * (more than one phase changed and neither an ordinary nor an invariant
 * resolution succeeded), so it is registered with no exit lines, a
 * deliberate, documented limitation, not a silent gap. No known
 * end-to-end case in this codebase yet exercises a genuine resolved
 * {@code INVARIANT} through a real walk (V-Zr's own documented
 * peritectic is confirmed {@code UNRESOLVED_MULTI_PHASE_CHANGE}, not
 * {@code INVARIANT}) -- {@link NodeGeometry}'s invariant-exit wiring is
 * unit-tested directly ({@code NodeGeometryTest}) against a synthetic
 * node built from literature data, not through this drain loop.
 *
 * <p>Also single-walk-axis only: every {@link Line} varies the SAME
 * axis the caller names as {@code searchAxisIndex}/{@code walkAxis}
 * (direction +1 or -1); the flowchart's "select the fastest-varying
 * axis" reselection during a walk is not implemented here -- a ternary
 * isothermal or isopleth caller picks ONE of its axes to walk for the
 * whole diagram, same as the binary case always has.
 *
 * <p><b>Global stability check (Step 6b, §2.3.3).</b> Every newly
 * created {@code CROSSING}/{@code INVARIANT} node is checked via
 * {@link PhaseDiagramEngine#isGloballyStable}, matching OC's {@code
 * global_equil_check1} at node creation; a failing node gets no exits
 * and its arriving {@link Line} is {@link Line#markExcluded marked
 * excluded} rather than terminated normally. NOT checked: the START
 * node (no arriving line to exclude if it fails -- a separate, smaller
 * follow-up) and every ordinary walked point mid-line (OC's own
 * cheaper, off-by-default {@code check_all_phases} interval check,
 * confirmed unrelated to line abandonment -- see the roadmap doc).
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
     * Drains the C1 loop starting from one initial equilibrium, which may
     * be SINGLE-phase (Step 3b): the true start {@link Node} is located
     * by {@link MapTracer#findInitialBoundary}'s search along {@code
     * walkAxis} (holding {@code releaseAxis} fixed, per the flowchart's
     * map-branch initialization, Section 3.3) from the caller's starting
     * condition -- not the starting condition itself, unless it already
     * happens to sit exactly on a boundary.
     *
     * @param walkAxis    the axis searched initially, then walked by
     *                    every line in the diagram (e.g. TEMPERATURE);
     *                    see the class javadoc for why every line,
     *                    including the start node's own exits, walks
     *                    this SAME axis in this version's binary-only scope
     * @param releaseAxis the composition axis solved for exactly at each
     *                    boundary; must have {@code type == COMPOSITION}
     *                    (same constraint as {@link MapTracer})
     * @param fixedT      temperature when {@code walkAxis.type != TEMPERATURE}
     * @param fixedP      pressure when {@code walkAxis.type != PRESSURE}
     * @param startWalkValue the axis value of the CALLER's starting
     *                       condition (usually {@code walkAxis.min}) --
     *                       not necessarily where the start node ends up
     * @param compOverall starting overall composition
     * @param candidates  candidate phase models
     * @return the populated registry: 1 start node (located by the
     *         initial search), plus any nodes created by resolved
     *         crossings, and every line walked
     * @throws IllegalStateException if the initial search finds no
     *         stable-set change anywhere in {@code walkAxis}'s range --
     *         there is nothing for the drain loop to start from
     */
    public NodeRegistry drain(
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

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
        return drain(conds, 0, 1, startWalkValue, compOverall, candidates);
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
     * The actual C1 drain loop -- see the class javadoc. Drains the loop
     * for ANY diagram type expressible as a {@link ConditionSet}: binary
     * T-x, ternary isothermal (two free composition axes), or a
     * ternary+ isopleth (T + one free composition, one or more OTHER
     * compositions FIXED). {@link
     * PhaseDiagramEngine#classifyNode(ConditionSet, int)} genuinely
     * distinguishes {@code TIE_LINE_IN_PLANE} from {@code
     * ISOPLETH_CROSSING} here, and the arriving {@link Line}'s own
     * {@link Line#fixedPhases} is threaded into {@link NodeGeometry} so
     * a genuine isopleth crossing gets its paper-derived 3 exits (see
     * {@link NodeGeometry#attachExits(Node, PhaseDiagramEngine.NodeClass,
     * String, String, int)}'s javadoc for the exact mechanism, ported
     * directly from OpenCalphad's own {@code map_newnode case(3)}).
     *
     * @param conds            the full condition set (n+2 conditions)
     * @param searchAxisIndex  index into {@code conds.axisConditions()}
     *                         of the axis searched initially, then walked
     *                         by every line in the diagram -- this
     *                         version's single-walk-axis scope (see the
     *                         class javadoc) still applies: every line
     *                         walks this SAME axis, no per-line
     *                         reselection
     * @param releaseAxisIndex index into {@code conds.axisConditions()}
     *                         of the composition axis Algorithm C2
     *                         releases at every crossing
     * @param startSearchValue the search axis's value at the caller's
     *                         starting condition
     * @param compOverall      starting overall composition (length
     *                         {@code conds.numComponents()}); any
     *                         composition condition's FIXED value must
     *                         already be baked in here (this method does
     *                         not re-derive it from {@code conds} beyond
     *                         what {@link ConditionSet#initialComposition()}
     *                         would give a fresh caller)
     * @param candidates       candidate phase models
     * @return the populated registry: 1 start node (located by the
     *         initial search), plus any nodes created by resolved
     *         crossings, and every line walked
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

        MapTracer.InitialBoundaryResult initial = tracer.findInitialBoundary(
                conds, searchAxisIndex, startSearchValue, releaseAxisIndex, startComp, candidates);

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

        // The START node has no arriving line, so no fixed phase of its
        // own to form an isopleth crossing with -- its 2 exits are always
        // the ordinary case, exactly the AxisConfig overload's own
        // start-node construction.
        startNode.addLine(new Line(startNode, List.of(), 0, +1));
        startNode.addLine(new Line(startNode, List.of(), 0, -1));

        while (registry.hasPendingWork()) {

            Line line = registry.nextPendingLine();
            Node fromNode = line.startNode;
            String arrivingLineFixedPhase = line.fixedPhases.isEmpty() ? null : line.fixedPhases.get(0);
            line.startWalking();

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

        return registry;
    }
}
