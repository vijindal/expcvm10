package calc.diagram;

import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a {@link PhaseDiagramEngine.NodeClass} and the {@link Line} that
 * just arrived at a node, returns the exit {@link Line}s to attach --
 * Step 5d of {@code docs/roadmap_phase_diagrams.md}, closing the "invariant
 * nodes get zero exit lines" gap {@link MapDiagramTracer} previously
 * documented as out of scope.
 *
 * <p>Per the flowchart's node-geometry branch: exit count for the
 * ordinary ({@code f>0}) case comes from NODE GEOMETRY, not from Eq. 8's
 * {@code f} itself -- {@link PhaseDiagramEngine.NodeClass#TIE_LINE_IN_PLANE}
 * always gets 2 exits (this codebase's only ordinary case so far;
 * {@code ISOPLETH_CROSSING}'s 3-exit case is Step 5e's work, not
 * reachable from {@link PhaseDiagramEngine#classifyNode} yet). {@code
 * INVARIANT} exits are found via Algorithm D ({@link InvariantExitFinder},
 * Eq. 9): one {@link Line} per {@link InvariantExitFinder.ExitCandidate},
 * verified combinatorics only for binary systems so far (see that
 * class's own javadoc).
 */
final class NodeGeometry {

    private NodeGeometry() {
    }

    /**
     * Builds and attaches the exit lines for {@code node}, given the
     * classification of the crossing that created it.
     *
     * @param node          the newly-created node (must have no lines yet)
     * @param nodeClass     from {@link PhaseDiagramEngine#classifyNode}
     * @param arrivedViaPhase the phase that appeared/disappeared to create
     *                      this node (the arrival line's fixed phase) --
     *                      excluded from {@code INVARIANT}'s exit search
     *                      since that exit already exists (the arrival line)
     * @param walkAxisIndex the axis index every exit {@link Line} varies
     *                      (this codebase's single-walk-axis scope, per
     *                      {@link MapDiagramTracer}'s class javadoc)
     */
    static void attachExits(
            Node node,
            PhaseDiagramEngine.NodeClass nodeClass,
            String arrivedViaPhase,
            int walkAxisIndex) {

        switch (nodeClass) {
            case TIE_LINE_IN_PLANE:
                attachTieLineInPlaneExits(node, arrivedViaPhase, walkAxisIndex);
                break;
            case INVARIANT:
                attachInvariantExits(node, arrivedViaPhase, walkAxisIndex);
                break;
            case STEP_CONTINUATION:
                throw new IllegalArgumentException(
                        "STEP_CONTINUATION needs the arriving line's walk direction -- call "
                        + "attachExits(Node, NodeClass, String, int, int) instead, which "
                        + "StepDiagramTracer uses directly (it always knows its own direction).");
            case ISOPLETH_CROSSING:
                throw new UnsupportedOperationException(
                        "ISOPLETH_CROSSING exit geometry (3 exits) is Step 5e's work -- "
                        + "not reachable from PhaseDiagramEngine#classifyNode yet, so this "
                        + "should be unreachable today.");
            default:
                throw new IllegalStateException("Unhandled NodeClass: " + nodeClass);
        }
    }

    /**
     * {@link #attachExits(Node, PhaseDiagramEngine.NodeClass, String, int)}
     * overload for {@link PhaseDiagramEngine.NodeClass#STEP_CONTINUATION}
     * (Sundman 2021 §3.2, see that enum constant's javadoc): exactly 1
     * exit, continuing the SAME {@code direction} the arriving {@link
     * Line} was already walking -- distinguishes it from the step's own
     * START node, which {@link StepDiagramTracer} attaches 2 exits to
     * directly (one per direction), not through this method.
     *
     * @param direction the direction the arriving line was walking
     *                  (+1 or -1); the single exit continues in this
     *                  same direction
     */
    static void attachExits(
            Node node,
            PhaseDiagramEngine.NodeClass nodeClass,
            String arrivedViaPhase,
            int walkAxisIndex,
            int direction) {

        if (nodeClass != PhaseDiagramEngine.NodeClass.STEP_CONTINUATION) {
            throw new IllegalArgumentException(
                    "The direction-taking overload is for STEP_CONTINUATION only; got " + nodeClass
                    + " -- use attachExits(Node, NodeClass, String, int) instead.");
        }
        node.addLine(new Line(node, List.of(arrivedViaPhase), walkAxisIndex, direction));
    }

    /**
     * The ordinary tie-line-in-plane case (this codebase's only {@code
     * f>0} geometry so far): 2 exits, one continuing in each direction
     * of {@code walkAxisIndex} -- exactly {@link MapDiagramTracer}'s
     * pre-Step-5d inline construction, now centralized here.
     */
    private static void attachTieLineInPlaneExits(Node node, String arrivedViaPhase, int walkAxisIndex) {
        node.addLine(new Line(node, List.of(arrivedViaPhase), walkAxisIndex, +1));
        node.addLine(new Line(node, List.of(arrivedViaPhase), walkAxisIndex, -1));
    }

    /**
     * Algorithm D (Eq. 9): enumerate every valid exit from the invariant
     * node (excluding the arrival exit) and attach one PENDING {@link
     * Line} per {@link InvariantExitFinder.ExitCandidate}, each fixing
     * that candidate's {@code excludedPhase} at zero amount.
     *
     * <p>If {@link InvariantExitFinder#findExits} returns no candidates
     * (e.g. this node's stable-phase compositions don't admit a valid
     * positive-amount exit within {@code walkAxisIndex}'s plane, or the
     * node's phase count doesn't match {@code ncomp+1} exactly -- e.g. a
     * higher-order invariant this codebase has not validated
     * {@link InvariantExitFinder} against, per that class's own
     * binary-only verification note), the node is left with NO exit
     * lines, matching {@link MapDiagramTracer}'s prior documented
     * behavior for this case -- an explicit "no further lines from
     * here" rather than a thrown error, since a node with no valid
     * plane-exits is a legitimate outcome (the invariant may simply not
     * continue any line within this diagram's 2D plane).
     */
    private static void attachInvariantExits(Node node, String arrivedViaPhase, int walkAxisIndex) {
        List<String> phaseNames = new ArrayList<>(node.stablePhaseNames);
        double[][] compositions = new double[phaseNames.size()][];

        for (int i = 0; i < phaseNames.size(); i++) {
            for (EquilibriumResult.PhaseResult pr : node.equilibrium.getStablePhases()) {
                if (pr.phaseName.equals(phaseNames.get(i))) {
                    compositions[i] = pr.x;
                    break;
                }
            }
            if (compositions[i] == null) {
                // A stable phase name with no matching PhaseResult should
                // not happen (stablePhaseNames is built FROM
                // equilibrium.getStablePhases() in Node's constructor),
                // but fail loudly rather than silently skip if it ever does.
                throw new IllegalStateException(
                        "Node's stablePhaseNames contains \"" + phaseNames.get(i)
                        + "\" with no matching PhaseResult in its equilibrium -- should be impossible.");
            }
        }

        if (node.overallComposition == null) {
            throw new IllegalStateException(
                    "Cannot find invariant exits without the node's overall composition "
                    + "(Node#overallComposition is null) -- see MapDiagramTracer, which is "
                    + "expected to always supply it via NodeRegistry#findOrCreate.");
        }

        List<InvariantExitFinder.ExitCandidate> exits = InvariantExitFinder.findExits(
                phaseNames, compositions, node.overallComposition, arrivedViaPhase);

        // Each ExitCandidate names WHICH phase excludes/which phases stay
        // stable along that exit line, but not which walkAxisIndex
        // DIRECTION traces it -- an exit's direction is not determinable
        // from the candidate alone (it depends on which way the walk axis
        // must move to stay on that phase assemblage, the same ambiguity
        // Sundman 2021 Fig. 8(c) resolves by trying a direction and
        // flipping it if the result is "forbidden"). Attach BOTH
        // directions per candidate, matching the tie-line-in-plane
        // pattern; the wrong-direction line is expected to terminate
        // quickly (non-convergence or an immediate re-crossing) when
        // walked by the drain loop rather than being pre-filtered here.
        for (InvariantExitFinder.ExitCandidate exit : exits) {
            node.addLine(new Line(node, List.of(exit.excludedPhase), walkAxisIndex, +1));
            node.addLine(new Line(node, List.of(exit.excludedPhase), walkAxisIndex, -1));
        }
    }
}
