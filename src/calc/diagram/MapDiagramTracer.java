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
 * <p><b>Scope, matching {@code docs/roadmap_phase_diagrams.md}'s
 * step-by-step build order:</b> a resolved {@link
 * MapTracer.SegmentEnd#CROSSING} is classified via {@link
 * PhaseDiagramEngine#classifyNode} (Eq. 8) and given exits via {@link
 * NodeGeometry} -- {@code TIE_LINE_IN_PLANE} (2 exits) for the
 * ordinary case, or a genuine {@link MapTracer.SegmentEnd#INVARIANT}
 * routed through Algorithm D ({@link InvariantExitFinder}), Step 5d.
 * {@link MapTracer.SegmentEnd#UNRESOLVED_MULTI_PHASE_CHANGE} is still
 * out of scope -- that crossing could not even be resolved to a
 * specific node (more than one phase changed and neither an ordinary
 * nor an invariant resolution succeeded), so it is registered with no
 * exit lines, a deliberate, documented limitation, not a silent gap.
 * No known end-to-end case in this codebase yet exercises a genuine
 * resolved {@code INVARIANT} through a real walk (V-Zr's own
 * documented peritectic is confirmed {@code
 * UNRESOLVED_MULTI_PHASE_CHANGE}, not {@code INVARIANT}) -- {@link
 * NodeGeometry}'s invariant-exit wiring is unit-tested directly
 * ({@code NodeGeometryTest}) against a synthetic node built from
 * literature data, not through this drain loop.
 *
 * <p>Also single-walk-axis only: every {@link Line} varies the SAME
 * walk axis as the start node (direction +1 or -1); the flowchart's
 * "select the fastest-varying axis" reselection during a walk is not
 * implemented here.
 *
 * <p><b>On the flowchart's "attach exits along the OTHER axis"
 * language (Step 3b analysis):</b> for a binary T-x map -- this
 * version's only supported case -- there are exactly 2 axes total, one
 * WALKED and one RELEASED (never both walked at once); the initial
 * search (see {@link #drain}) consumes {@code walkAxis}, and {@code
 * releaseAxis} is never itself walked in this scope, only solved for
 * exactly at each crossing. So the start node's 2 exit lines correctly
 * continue walking {@code walkAxis} in each direction (exactly like
 * every other node's exits, Step 2's original design) -- the
 * flowchart's "other axis" case only becomes literally applicable once
 * a diagram type with a genuine second WALKABLE axis exists (e.g. a
 * ternary isothermal section's two composition axes, neither one
 * released) -- deferred to that future work, not implemented here.
 */
public final class MapDiagramTracer {

    /** Node-matching tolerance passed to {@link NodeRegistry#findOrCreate}. */
    private final double nodeMatchTolerance;

    /**
     * Overall composition tracked per node id -- {@link Node} cannot
     * always recover this from its {@link EquilibriumResult} alone for a
     * multi-phase node (see {@link Node#overallComposition}'s javadoc),
     * so this loop threads it explicitly.
     */
    private final Map<Integer, double[]> compositionByNodeId = new HashMap<>();

    public MapDiagramTracer() {
        this(1e-4);
    }

    public MapDiagramTracer(double nodeMatchTolerance) {
        this.nodeMatchTolerance = nodeMatchTolerance;
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

        MapTracer tracer = new MapTracer();
        NodeRegistry registry = new NodeRegistry();

        double[] startComp = compOverall.clone();
        if (walkAxis.type == AxisConfig.Type.COMPOSITION) {
            startComp = StepTracer.applyCompositionAxis(walkAxis, startWalkValue, startComp);
        }

        MapTracer.InitialBoundaryResult initial = tracer.findInitialBoundary(
                walkAxis, startWalkValue, releaseAxis, fixedT, fixedP, startComp, candidates);

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
                startNodeComp, nodeMatchTolerance);
        compositionByNodeId.putIfAbsent(startNode.id, startNodeComp);

        // Per the flowchart's map-branch initialization: attach 2 pending
        // lines, one in each direction of the walk axis, from the start
        // node -- see the class javadoc for why this stays walkAxis (not
        // releaseAxis) in this version's binary-only scope.
        startNode.addLine(new Line(startNode, List.of(), 0, +1));
        startNode.addLine(new Line(startNode, List.of(), 0, -1));

        while (registry.hasPendingWork()) {

            Line line = registry.nextPendingLine();
            Node fromNode = line.startNode;
            line.startWalking();

            AxisConfig directedWalkAxis = directed(walkAxis, line.direction);
            double[] compAtStart = compositionByNodeId.get(fromNode.id);

            MapTracer.SegmentResult seg = tracer.walkOneSegment(
                    fromNode.axisValues[0], fromNode.stablePhaseNames,
                    directedWalkAxis, releaseAxis, fixedT, fixedP,
                    compAtStart, candidates, fromNode.equilibrium);

            for (int i = 0; i < seg.points.size(); i++) {
                line.addPoint(seg.points.get(i), seg.coords.get(i));
            }

            switch (seg.end) {
                case AXIS_LIMIT:
                case NON_CONVERGENT:
                    line.terminateAtAxisLimit();
                    break;

                case CROSSING: {
                    Node endNode = registry.findOrCreate(
                            seg.lastResult,
                            new double[] { seg.endWalkValue, seg.endComposition[releaseAxis.componentIndex] },
                            seg.endComposition, nodeMatchTolerance);
                    compositionByNodeId.putIfAbsent(endNode.id, seg.endComposition);
                    line.terminateAtNode(endNode);
                    if (endNode.getLines().isEmpty()) {
                        // Newly created: classify via Eq. 8 and attach
                        // exits accordingly (Step 5d) -- see NodeGeometry.
                        // c=1: this codebase's binary map fixes P as a
                        // non-axis potential condition (see
                        // PhaseDiagramEngine#classifyNode's javadoc for
                        // the full derivation against the paper's own
                        // binary-isobaric worked example).
                        PhaseDiagramEngine.NodeClass nodeClass = PhaseDiagramEngine.classifyNode(
                                compOverall.length, endNode.stablePhaseNames.size(), 1);
                        NodeGeometry.attachExits(endNode, nodeClass, seg.changedPhase, 0);
                    }
                    break;
                }

                case INVARIANT: {
                    // A genuine invariant (Algorithm D, Step 5d) -- register
                    // the node and attach whatever valid exits
                    // InvariantExitFinder finds (possibly none, a
                    // legitimate outcome; see NodeGeometry's javadoc).
                    Node endNode = registry.findOrCreate(
                            seg.lastResult,
                            new double[] { seg.endWalkValue, seg.endComposition[releaseAxis.componentIndex] },
                            seg.endComposition, nodeMatchTolerance);
                    compositionByNodeId.putIfAbsent(endNode.id, seg.endComposition);
                    line.terminateAtNode(endNode);
                    if (endNode.getLines().isEmpty()) {
                        NodeGeometry.attachExits(endNode, PhaseDiagramEngine.NodeClass.INVARIANT,
                                seg.changedPhase, 0);
                    }
                    break;
                }

                case UNRESOLVED_MULTI_PHASE_CHANGE: {
                    // Still out of scope: the crossing could not even be
                    // resolved to a specific node (more than one phase
                    // changed and neither an ordinary nor an invariant
                    // resolution succeeded) -- nothing for NodeGeometry to
                    // classify. Register the node (so it is visible/
                    // inspectable) but do not attach exit lines.
                    Node endNode = registry.findOrCreate(
                            seg.lastResult,
                            new double[] { seg.endWalkValue, seg.endComposition[releaseAxis.componentIndex] },
                            seg.endComposition, nodeMatchTolerance);
                    compositionByNodeId.putIfAbsent(endNode.id, seg.endComposition);
                    line.terminateAtNode(endNode);
                    break;
                }
            }
        }

        return registry;
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
