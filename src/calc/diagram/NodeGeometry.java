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
 * always gets 2 exits; {@link PhaseDiagramEngine.NodeClass#ISOPLETH_CROSSING}
 * always gets 3 (Sundman 2021 §3.3's own worked description, see {@link
 * #attachIsoplethCrossingExits}). {@code INVARIANT} exits are found via
 * Algorithm D ({@link InvariantExitFinder}, Eq. 9): one {@link Line} per
 * {@link InvariantExitFinder.ExitCandidate}, verified combinatorics only
 * for binary systems so far (see that class's own javadoc).
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
     * @throws IllegalArgumentException if {@code nodeClass} is {@code
     *         ISOPLETH_CROSSING} -- use {@link #attachExits(Node,
     *         PhaseDiagramEngine.NodeClass, String, String, int)} instead,
     *         which needs the arriving line's own already-fixed phase too
     */
    static void attachExits(
            Node node,
            PhaseDiagramEngine.NodeClass nodeClass,
            String arrivedViaPhase,
            int walkAxisIndex) {
        attachExits(node, nodeClass, arrivedViaPhase, null, walkAxisIndex);
    }

    /**
     * As {@link #attachExits(Node, PhaseDiagramEngine.NodeClass, String,
     * int)}, additionally taking the arriving line's own fixed phase (OC's
     * {@code LFIX}/{@code jphr}) -- required for {@code ISOPLETH_CROSSING},
     * and also used to set each exit's {@link Line#forbiddenPhase} for
     * {@code TIE_LINE_IN_PLANE} and {@code INVARIANT}. {@code null} if the
     * arriving line had no fixed phase (e.g. a diagram's own START node).
     *
     * @throws IllegalArgumentException if {@code nodeClass} is {@code
     *         STEP_CONTINUATION} -- use {@link #attachExits(Node,
     *         PhaseDiagramEngine.NodeClass, String, int, int)} instead
     */
    static void attachExits(
            Node node,
            PhaseDiagramEngine.NodeClass nodeClass,
            String arrivedViaPhase,
            String arrivingLineFixedPhase,
            int walkAxisIndex) {

        switch (nodeClass) {
            case TIE_LINE_IN_PLANE:
                attachTieLineInPlaneExits(node, arrivedViaPhase, arrivingLineFixedPhase, walkAxisIndex);
                break;
            case INVARIANT:
                attachInvariantExits(node, arrivedViaPhase, arrivingLineFixedPhase, walkAxisIndex);
                break;
            case ISOPLETH_CROSSING:
                attachIsoplethCrossingExits(node, arrivedViaPhase, arrivingLineFixedPhase, walkAxisIndex);
                break;
            case STEP_CONTINUATION:
                throw new IllegalArgumentException(
                        "STEP_CONTINUATION needs the arriving line's walk direction -- call "
                        + "attachExits(Node, NodeClass, String, int, int) instead, which "
                        + "StepDiagramTracer uses directly (it always knows its own direction).");
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
     * The ordinary tie-line-in-plane case: 2 exits, one continuing in each
     * direction of {@code walkAxisIndex}, both fixing {@code
     * arrivedViaPhase} and both forbidding {@code arrivingLineFixedPhase}
     * from reappearing at the first step (the phase the arriving line came
     * from -- if it does, the direction is wrong; OC's {@code nodfixph}).
     *
     * @param arrivingLineFixedPhase may be {@code null} (e.g. a diagram's
     *                      own START node has no arriving line)
     */
    private static void attachTieLineInPlaneExits(
            Node node, String arrivedViaPhase, String arrivingLineFixedPhase, int walkAxisIndex) {
        node.addLine(new Line(node, List.of(arrivedViaPhase), walkAxisIndex, +1, arrivingLineFixedPhase));
        node.addLine(new Line(node, List.of(arrivedViaPhase), walkAxisIndex, -1, arrivingLineFixedPhase));
    }

    /**
     * The isopleth-crossing case: "two crossing lines" meet at this node
     * (Sundman 2021 §3.3's own worked description, quoted in full in
     * {@link PhaseDiagramEngine#classifyNode(ConditionSet, int)}'s
     * javadoc) -- confirmed directly against OpenCalphad's own
     * implementation of this exact case ({@code
     * src/stepmapplot/smp2A.F90}, {@code case(3)}, comment "Normal node
     * in a phase diagram without tie-lines in plane... Two crossing
     * lines, one in and 3 exits"). OC names the two phases involved
     * {@code PHFIX} (the phase that just appeared/disappeared to create
     * THIS crossing -- {@code arrivedViaPhase} here) and {@code LFIX}/
     * {@code jphr} (the phase that was ALREADY fixed at zero along the
     * arriving line -- {@code arrivingLineFixedPhase} here, found by
     * OC's own comment as "the phase that was stable along the line"
     * relative to the line's own fix phase, i.e. the arriving {@link
     * Line#fixedPhases}' single entry, not a driving-force search or any
     * other computed quantity).
     *
     * <p>OC's own exit table (comment at {@code case(3)}, {@code
     * PHFIX>0}, a phase appearing -- the disappearing case mirrors it):
     * <pre>
     *          FIX     STABLE (relative to the node's own p phases)
     * exit 1   LFIX    p+1, including PHFIX  -- LFIX's own line continues
     * exit 2   PHFIX   p+1, including LFIX   -- PHFIX's line, LFIX side
     * exit 3   PHFIX   p,   LFIX in, PHFIX out -- PHFIX's line, other side
     * </pre>
     * Exit 1 is the SINGLE continuation of the phase already fixed along
     * the arriving line (not both directions -- the arriving line
     * already covers the direction we came from; only the far side is a
     * genuinely new exit, per OC's "4 lines meet, 3 exits" accounting).
     * Exits 2 and 3 are the NEW phase's ({@code arrivedViaPhase}) own ZPF
     * line, in the two directions that differ by which OTHER phase
     * (LFIX in vs. out) is stable alongside it -- both attached here
     * (their actual walk direction is not determinable from local node
     * data alone, the same ambiguity {@link #attachInvariantExits}
     * documents for Algorithm D; the wrong one is expected to terminate
     * quickly when walked).
     *
     * <p>If {@code arrivingLineFixedPhase} is {@code null} (the arriving
     * line had no fixed phase of its own -- it came from a {@code
     * TIE_LINE_IN_PLANE} node or the diagram's START node, so there is
     * no "already fixed" phase to form a second crossing line with),
     * this degrades to the ordinary 2-exit case: {@code
     * arrivedViaPhase}'s own line continues in both directions, exactly
     * {@link #attachTieLineInPlaneExits}. This matches the paper's own
     * "MOST node points" (not ALL) phrasing -- not every {@code f>0}
     * node in an isopleth-shaped diagram is a genuine 4-region crossing.
     */
    private static void attachIsoplethCrossingExits(
            Node node, String arrivedViaPhase, String arrivingLineFixedPhase, int walkAxisIndex) {

        if (arrivingLineFixedPhase == null) {
            attachTieLineInPlaneExits(node, arrivedViaPhase, null, walkAxisIndex);
            return;
        }

        // Exit 1: LFIX's own line continues (single direction -- the
        // other direction is where the arriving line came from);
        // forbids PHFIX from reappearing.
        node.addLine(new Line(node, List.of(arrivingLineFixedPhase), walkAxisIndex, +1, arrivedViaPhase));

        // Exits 2 and 3: PHFIX's own line, both directions; forbid LFIX.
        node.addLine(new Line(node, List.of(arrivedViaPhase), walkAxisIndex, +1, arrivingLineFixedPhase));
        node.addLine(new Line(node, List.of(arrivedViaPhase), walkAxisIndex, -1, arrivingLineFixedPhase));
    }

    /**
     * Algorithm D (Fig. 7/Eq. 9): enumerate every valid exit PAIR from
     * the invariant node (excluding the arrival pair) and attach 2
     * PENDING {@link Line}s per {@link
     * InvariantExitPairFinder.ExitPair} -- Fig. 7's own {@code "+2
     * exits"} box: one line fixes {@code beta1} at zero with {@code
     * beta2} forbidden, the other fixes {@code beta2} at zero with
     * {@code beta1} forbidden.
     *
     * <p>The arrival pair is {@code arrivedViaPhase} (the phase that
     * just went to/from zero amount to create this node) together with
     * {@code arrivingLineFixedPhase} (the phase already held at zero
     * along the arriving line) -- together the two phases with zero
     * amount on the line the algorithm arrived by, per Eq. (9)'s own
     * "Both of these have zero amount at the exit" description.
     *
     * <p>If {@link InvariantExitPairFinder#findExitPairs} returns no
     * pairs (e.g. this node's stable-phase compositions don't admit a
     * valid positive-amount exit within {@code walkAxisIndex}'s plane,
     * or the node's phase count doesn't match {@code ncomp+1} exactly --
     * e.g. a higher-order invariant this codebase has not validated
     * {@link InvariantExitPairFinder} against, per that class's own
     * scope note), the node is left with NO exit lines, matching {@link
     * MapDiagramTracer}'s prior documented behavior for this case -- an
     * explicit "no further lines from here" rather than a thrown error,
     * since a node with no valid plane-exits is a legitimate outcome
     * (the invariant may simply not continue any line within this
     * diagram's 2D plane).
     */
    private static void attachInvariantExits(
            Node node, String arrivedViaPhase, String arrivingLineFixedPhase, int walkAxisIndex) {
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

        InvariantExitPairFinder.ExitPair arrivalPair = arrivingLineFixedPhase == null
                ? null
                : new InvariantExitPairFinder.ExitPair(arrivedViaPhase, arrivingLineFixedPhase);

        List<InvariantExitPairFinder.ExitPair> exitPairs = InvariantExitPairFinder.findExitPairs(
                phaseNames, compositions, node.overallComposition, arrivalPair);

        // Fig. 7's "+2 exits" box: each valid pair gets both role
        // assignments -- beta1 fixed/beta2 forbidden, and vice versa.
        // Neither exit's walk DIRECTION along walkAxisIndex is
        // determinable from the pair alone (it depends on which way the
        // axis must move to stay on that phase assemblage, the same
        // ambiguity Sundman 2021 Fig. 8(c) resolves by trying a
        // direction and flipping it if the result is "forbidden"), so
        // both directions are attached per role; the wrong-direction
        // line is expected to terminate quickly (non-convergence or an
        // immediate re-crossing) when walked by the drain loop rather
        // than being pre-filtered here.
        for (InvariantExitPairFinder.ExitPair pair : exitPairs) {
            node.addLine(new Line(node, List.of(pair.beta2), walkAxisIndex, +1, pair.beta1));
            node.addLine(new Line(node, List.of(pair.beta2), walkAxisIndex, -1, pair.beta1));
            node.addLine(new Line(node, List.of(pair.beta1), walkAxisIndex, +1, pair.beta2));
            node.addLine(new Line(node, List.of(pair.beta1), walkAxisIndex, -1, pair.beta2));
        }
    }
}
