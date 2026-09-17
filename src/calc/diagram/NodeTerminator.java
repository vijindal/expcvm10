package calc.diagram;

import calc.equil.EquilibriumSolverV2;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.List;
import java.util.Set;

/**
 * Algorithm C2 (Fig. 6): handles a change of the set of stable phases at
 * the end of a line and generates a node with exits for further lines.
 * One method per flowchart box, called in the flowchart's own order.
 */
final class NodeTerminator {

    private NodeTerminator() {
    }

    /**
     * Builds the crossing-resolution callback for {@link
     * LineFollower#walkLineAlgorithmC1}, bound to one diagram's conditions.
     *
     * @param axis        the axis being walked
     * @param fixedT      temperature (used unless {@code axis} is TEMPERATURE)
     * @param fixedP      pressure (used unless {@code axis} is PRESSURE)
     * @param compOverall overall composition (used unless {@code axis} is COMPOSITION)
     * @param conds       full condition set, for node classification
     * @param candidates  candidate phase models
     * @param registry    the node registry to match/register against
     * @return a callback that resolves a detected crossing into a terminated line and node
     */
    static java.util.function.BiConsumer<Line, EquilibriumResult> crossingHandlerFor(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            ConditionSet conds,
            List<GibbsEnergyModel> candidates,
            NodeRegistry registry) {

        return (line, crossingHint) -> runAlgorithmC2(
                line, crossingHint, axis, fixedT, fixedP, compOverall, conds, candidates, registry);
    }

    /**
     * Runs Algorithm C2 (Fig. 6) for one detected crossing.
     *
     * @param line         the line that crossed a phase-set change
     * @param crossingHint the equilibrium at/after the crossing
     * @param axis         the axis {@code line} is walking
     * @param fixedT       temperature (used unless {@code axis} is TEMPERATURE)
     * @param fixedP       pressure (used unless {@code axis} is PRESSURE)
     * @param compOverall  overall composition (used unless {@code axis} is COMPOSITION)
     * @param conds        full condition set, for node classification
     * @param candidates   candidate phase models
     * @param registry     the node registry to match/register against
     */
    static void runAlgorithmC2(
            Line line,
            EquilibriumResult crossingHint,
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            ConditionSet conds,
            List<GibbsEnergyModel> candidates,
            NodeRegistry registry) {

        // box: set alpha fix with 0 amount, release axis condition
        String fixedPhase = setAlphaFixAtZero(line, crossingHint);
        ReleasedCondition released = releaseAxisCondition(axis, fixedT, fixedP, compOverall, crossingHint);

        // box: A
        EquilibriumSolverV2.BoundarySolveResult boundary =
                callAlgorithmA(axis, released, fixedPhase, crossingHint, candidates);

        // box: error? -- yes --> (to C1)
        if (error(boundary)) {
            line.terminateAtAxisLimit();
            toC1(line);
            return;
        }

        // box: global test
        boolean globallyStable = globalTest(boundary, candidates);

        // box: global? -- no --> delete line
        if (!globallyStable) {
            deleteLine(line, boundary, axis, registry);
            toC1(line);
            return;
        }

        // box: search -- already calculated --> exit marked done
        Node endNode = search(boundary, axis, registry);
        if (alreadyCalculated(endNode)) {
            exitMarkedDone(line, endNode);
            toC1(line);
            return;
        }

        // box: line terminated, +node
        lineTerminatedPlusNode(line, endNode);

        // box: step? -- yes --> +1 exit
        if (isStep(line)) {
            plusOneExit(endNode, line, fixedPhase);
            toC1(line);
            return;
        }

        PhaseDiagramEngine.NodeClass nodeClass =
                PhaseDiagramEngine.classifyNode(conds, endNode.stablePhaseNames.size());

        // box: tie-lines? -- yes --> +2 exits
        if (isTieLines(nodeClass)) {
            plusTwoExits(endNode, line, fixedPhase);
            toC1(line);
            return;
        }

        // box: invariant? -- yes --> D
        if (isInvariant(nodeClass)) {
            algorithmD(endNode, line, fixedPhase);
            toC1(line);
            return;
        }

        // box: +3 exits
        plusThreeExits(endNode, line, fixedPhase);
        toC1(line);
    }

    /** box: set alpha fix with 0 amount -- identifies the phase to hold at zero. */
    private static String setAlphaFixAtZero(Line line, EquilibriumResult crossingHint) {
        Set<String> before = line.getRunningStableNames();
        Set<String> after = EquilibriumSolveHelper.stablePhaseNames(crossingHint);
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

    /** One released axis condition (T, P, or a composition component). */
    private record ReleasedCondition(double t, double p, double[] comp) {
    }

    /** box: release axis condition -- drops the walked axis as a fixed condition. */
    private static ReleasedCondition releaseAxisCondition(
            AxisConfig axis, double fixedT, double fixedP, double[] compOverall, EquilibriumResult crossingHint) {
        double t0 = fixedT, p0 = fixedP;
        double[] comp = compOverall.clone();
        switch (axis.type) {
            case TEMPERATURE: t0 = crossingHint.getT(); break;
            case PRESSURE: p0 = crossingHint.getP(); break;
            case COMPOSITION: break; // released below, seeded from compOverall
            default: throw new IllegalStateException("Unhandled axis type: " + axis.type);
        }
        return new ReleasedCondition(t0, p0, comp);
    }

    /** box: A -- solves the boundary equilibrium with the fixed phase and released axis. */
    private static EquilibriumSolverV2.BoundarySolveResult callAlgorithmA(
            AxisConfig axis,
            ReleasedCondition released,
            String fixedPhase,
            EquilibriumResult seed,
            List<GibbsEnergyModel> candidates) {

        return switch (axis.type) {
            case COMPOSITION -> EquilibriumSolveHelper.solveBoundaryOrNull(
                    released.t(), released.p(), released.comp(), candidates, seed, fixedPhase, 0, axis.componentIndex);
            case TEMPERATURE -> EquilibriumSolveHelper.solveBoundaryReleasingTOrNull(
                    released.t(), released.p(), released.comp(), candidates, seed, fixedPhase, 0);
            case PRESSURE -> EquilibriumSolveHelper.solveBoundaryReleasingPOrNull(
                    released.t(), released.p(), released.comp(), candidates, seed, fixedPhase, 0);
        };
    }

    /** box: error? -- true if Algorithm A failed to converge on the boundary equilibrium. */
    private static boolean error(EquilibriumSolverV2.BoundarySolveResult boundary) {
        return boundary == null;
    }

    /** box: global test -- runs the global stability check on the boundary equilibrium. */
    private static boolean globalTest(EquilibriumSolverV2.BoundarySolveResult boundary, List<GibbsEnergyModel> candidates) {
        return PhaseDiagramEngine.isGloballyStable(boundary.equilibrium, candidates);
    }

    /** box: delete line -- abandons a line whose boundary equilibrium failed the global test. */
    private static void deleteLine(
            Line line, EquilibriumSolverV2.BoundarySolveResult boundary, AxisConfig axis, NodeRegistry registry) {
        Node node = matchOrCreateNode(registry, axis, boundary);
        line.terminateAtNode(node);
        line.markExcluded();
    }

    /** box: search -- finds or registers the node for the boundary equilibrium. */
    private static Node search(EquilibriumSolverV2.BoundarySolveResult boundary, AxisConfig axis, NodeRegistry registry) {
        return matchOrCreateNode(registry, axis, boundary);
    }

    private static Node matchOrCreateNode(
            NodeRegistry registry, AxisConfig axis, EquilibriumSolverV2.BoundarySolveResult boundary) {
        double releasedAxisValue = axis.type == AxisConfig.Type.COMPOSITION
                ? boundary.releasedComponentValue
                : (axis.type == AxisConfig.Type.TEMPERATURE ? boundary.equilibrium.getT() : boundary.equilibrium.getP());
        return registry.findOrCreate(
                boundary.equilibrium, new double[] { releasedAxisValue }, overallComposition(boundary.equilibrium));
    }

    /** box: search's "already calculated" branch -- true if {@code endNode} already has attached lines. */
    private static boolean alreadyCalculated(Node endNode) {
        return !endNode.getLines().isEmpty();
    }

    /** box: exit marked done -- the arriving exit is satisfied by the already-existing node. */
    private static void exitMarkedDone(Line line, Node endNode) {
        line.terminateAtNode(endNode);
    }

    /** box: line terminated, +node -- ends {@code line} at the newly-registered node. */
    private static void lineTerminatedPlusNode(Line line, Node endNode) {
        line.terminateAtNode(endNode);
    }

    /** box: step? -- true for a STEP line: no phase is fixed at zero along it (unlike every ZPF/MAP line). */
    private static boolean isStep(Line line) {
        return line.fixedPhases.isEmpty();
    }

    /** box: +1 exit -- STEP_CONTINUATION exit count. */
    private static void plusOneExit(Node endNode, Line line, String fixedPhase) {
        NodeGeometry.attachExits(
                endNode, PhaseDiagramEngine.NodeClass.STEP_CONTINUATION, fixedPhase, line.initialAxisIndex, line.direction);
    }

    /** box: tie-lines? -- true when the crossing is an ordinary tie-line-in-plane node. */
    private static boolean isTieLines(PhaseDiagramEngine.NodeClass nodeClass) {
        return nodeClass == PhaseDiagramEngine.NodeClass.TIE_LINE_IN_PLANE;
    }

    /** box: +2 exits -- TIE_LINE_IN_PLANE exit count. */
    private static void plusTwoExits(Node endNode, Line line, String fixedPhase) {
        String arrivingLineFixedPhase = line.fixedPhases.isEmpty() ? null : line.fixedPhases.get(0);
        NodeGeometry.attachExits(endNode, PhaseDiagramEngine.NodeClass.TIE_LINE_IN_PLANE,
                fixedPhase, arrivingLineFixedPhase, line.initialAxisIndex);
    }

    /** box: invariant? -- true when the crossing is an invariant (f=0) node. */
    private static boolean isInvariant(PhaseDiagramEngine.NodeClass nodeClass) {
        return nodeClass == PhaseDiagramEngine.NodeClass.INVARIANT;
    }

    /** box: D -- Algorithm D, finds and attaches the invariant's exits (Eq. 9). */
    private static void algorithmD(Node endNode, Line line, String fixedPhase) {
        String arrivingLineFixedPhase = line.fixedPhases.isEmpty() ? null : line.fixedPhases.get(0);
        NodeGeometry.attachExits(endNode, PhaseDiagramEngine.NodeClass.INVARIANT,
                fixedPhase, arrivingLineFixedPhase, line.initialAxisIndex);
    }

    /** box: +3 exits -- ISOPLETH_CROSSING exit count (the else branch: not step, tie-lines, or invariant). */
    private static void plusThreeExits(Node endNode, Line line, String fixedPhase) {
        String arrivingLineFixedPhase = line.fixedPhases.isEmpty() ? null : line.fixedPhases.get(0);
        NodeGeometry.attachExits(endNode, PhaseDiagramEngine.NodeClass.ISOPLETH_CROSSING,
                fixedPhase, arrivingLineFixedPhase, line.initialAxisIndex);
    }

    /**
     * box: C1 -- every terminal branch of Fig. 6 flows back into Fig. 5's
     * own {@code search} box, not merely "C1" in general: {@link
     * LineFollower#drain}'s {@code while (registry.hasPendingWork())}
     * loop with {@code registry.nextPendingLine()} inside it IS {@code
     * search} (scan the node list for a pending exit; no exit left ->
     * Finished). Reaching it needs no call from here -- any exit lines
     * just attached via {@link NodeGeometry#attachExits} are already
     * {@link Line.State#PENDING} on their node, so returning from this
     * method (back up through {@code walkAndResolve} to {@code drain}'s
     * loop body) puts control at {@code search}'s next scan directly.
     *
     * @throws IllegalStateException if {@code line} did not actually reach
     *         {@link Line.State#TERMINATED} -- every branch of {@link
     *         #runAlgorithmC2} must terminate the line before reaching C1
     */
    private static void toC1(Line line) {
        if (line.getState() != Line.State.TERMINATED) {
            throw new IllegalStateException(
                    "Algorithm C2 must terminate its line before returning to C1, but " + line
                    + " is still " + line.getState());
        }
    }

    private static double[] overallComposition(EquilibriumResult result) {
        int nc = result.getMu().length;
        double[] atomsPerComponent = new double[nc];
        double totalAtoms = 0.0;
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            double phaseAtoms = pr.atoms();
            totalAtoms += phaseAtoms;
            for (int i = 0; i < nc; i++) {
                atomsPerComponent[i] += phaseAtoms * pr.x[i];
            }
        }
        double[] comp = new double[nc];
        for (int i = 0; i < nc; i++) {
            comp[i] = atomsPerComponent[i] / totalAtoms;
        }
        return comp;
    }
}
