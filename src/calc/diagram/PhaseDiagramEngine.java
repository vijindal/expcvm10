package calc.diagram;

import calc.equil.EquilibriumSolverV2;
import calc.equil.GridMinimizer;
import calc.diagram.PhaseDiagramResult.LineSegment;
import calc.diagram.PhaseDiagramResult.NodePoint;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

/**
 * Top-level orchestration for phase-diagram tracing, implementing Sundman 2021
 * Algorithms A/B/C1/C2/D. Provides one method per flowchart stage (see
 * {@code docs/phase_diagram_engine_flowchart.md}). Supported diagram types:
 * binary T-x, ternary isothermal, ternary isopleth, and property/step diagrams.
 */
public final class PhaseDiagramEngine {

    private PhaseDiagramEngine() {
    }

    /**
     * Complete output of Algorithm A for one point.
     */
    public static final class DiagramEquilibrium {

        public final double T;
        public final double P;
        public final java.util.Map<String, Double> phaseAmounts;
        public final java.util.Map<String, double[]> phaseConstitutions;
        public final java.util.Map<String, double[]> phaseMoleFractions;
        public final java.util.Map<String, Double> phaseTotalMoles;
        public final double[] chemicalPotentials;
        public final ConditionSet conditions;
        public final java.util.Set<String> stablePhases;
        public final boolean converged;
        public final boolean globallyStable;

        public DiagramEquilibrium(
                double T,
                double P,
                java.util.Map<String, Double> phaseAmounts,
                java.util.Map<String, double[]> phaseConstitutions,
                java.util.Map<String, double[]> phaseMoleFractions,
                java.util.Map<String, Double> phaseTotalMoles,
                double[] chemicalPotentials,
                ConditionSet conditions,
                java.util.Set<String> stablePhases,
                boolean converged,
                boolean globallyStable) {
            this.T = T;
            this.P = P;
            this.phaseAmounts = java.util.Map.copyOf(phaseAmounts);
            this.phaseConstitutions = java.util.Map.copyOf(phaseConstitutions);
            this.phaseMoleFractions = java.util.Map.copyOf(phaseMoleFractions);
            this.phaseTotalMoles = java.util.Map.copyOf(phaseTotalMoles);
            this.chemicalPotentials = chemicalPotentials.clone();
            this.conditions = conditions;
            this.stablePhases = java.util.Set.copyOf(stablePhases);
            this.converged = converged;
            this.globallyStable = globallyStable;
        }

        /** A copy of this equilibrium with {@code newPhaseAmounts} in place of {@link #phaseAmounts}. */
        public DiagramEquilibrium withPhaseAmounts(java.util.Map<String, Double> newPhaseAmounts) {
            return new DiagramEquilibrium(T, P, newPhaseAmounts, phaseConstitutions,
                    phaseMoleFractions, phaseTotalMoles, chemicalPotentials, conditions,
                    stablePhases, converged, globallyStable);
        }
    }

    /**
     * The point where a line starts or ends.
     */
    public static final class DiagramNode {

        public final DiagramEquilibrium equilibrium;
        public final List<DiagramExit> exits;

        public DiagramNode(DiagramEquilibrium equilibrium, List<DiagramExit> exits) {
            this.equilibrium = equilibrium;
            this.exits = new java.util.ArrayList<>(exits);
        }
    }

    /**
     * One pending or resolved direction to walk away from a node.
     */
    public static final class DiagramExit {

        public final DiagramNode node;
        public final DiagramEquilibrium equilibrium;
        public boolean done;
        public final String fixedPhase;
        public final int initialAxis;
        public int direction;
        public final String forbiddenPhase;

        public DiagramExit(
                DiagramNode node,
                DiagramEquilibrium equilibrium,
                boolean done,
                String fixedPhase,
                int initialAxis,
                int direction,
                String forbiddenPhase) {
            this.node = node;
            this.equilibrium = equilibrium;
            this.done = done;
            this.fixedPhase = fixedPhase;
            this.initialAxis = initialAxis;
            this.direction = direction;
            this.forbiddenPhase = forbiddenPhase;
        }
    }

    /**
     * The lines share a buffer for sequential storage of all calculated
     * equilibria along the lines.
     */
    public static final class DiagramLineResult {

        public final DiagramNode startNode;
        public DiagramNode endNode;
        public final List<DiagramEquilibrium> equilibria;
        public String terminatedReason;

        public DiagramLineResult(DiagramNode startNode) {
            this.startNode = startNode;
            this.endNode = null;
            this.equilibria = new java.util.ArrayList<>();
            this.terminatedReason = null;
        }
    }

    /**
     * Output of Algorithm B: the complete diagram.
     */
    public static final class DiagramResult {

        public final List<DiagramNode> nodes = new java.util.ArrayList<>();
        public final List<DiagramLineResult> lines = new java.util.ArrayList<>();
        public final List<DiagramEquilibrium> equilibriaBuffer = new java.util.ArrayList<>();
    }

    /**
     * Algorithm A: solves one equilibrium at {@code axisValues} (one value
     * per {@code conds.axisConditions()}, in order), holding every other
     * condition at {@code conds}'s own fixed value.
     *
     * @param conds      full condition set for the diagram
     * @param axisValues current value of each axis condition, same order
     *                   as {@code conds.axisConditions()}
     * @param candidates candidate phase models
     * @return the resulting equilibrium, converged or not
     */
    public static DiagramEquilibrium callAlgorithmA(
            ConditionSet conds,
            double[] axisValues,
            List<GibbsEnergyModel> candidates) {

        List<Condition> axes = conds.axisConditions();
        double t = conds.fixedTemperature();
        double p = conds.fixedPressure();
        double[] comp = new double[conds.numComponents()];
        boolean[] specified = new boolean[comp.length];
        double specifiedSum = 0.0;

        for (Condition c : conds.all()) {
            if (c.variable != Condition.Variable.COMPOSITION || !c.isFixed()) continue;
            comp[c.componentIndex] = c.fixedValue;
            specified[c.componentIndex] = true;
            specifiedSum += c.fixedValue;
        }

        for (int i = 0; i < axes.size(); i++) {
            Condition axis = axes.get(i);
            switch (axis.variable) {
                case TEMPERATURE: t = axisValues[i]; break;
                case PRESSURE: p = axisValues[i]; break;
                case COMPOSITION:
                    comp[axis.componentIndex] = axisValues[i];
                    specified[axis.componentIndex] = true;
                    specifiedSum += axisValues[i];
                    break;
                default: throw new IllegalStateException("Unhandled axis variable: " + axis.variable);
            }
        }

        int unspecifiedCount = 0;
        for (boolean s : specified) if (!s) unspecifiedCount++;
        if (unspecifiedCount > 0) {
            double remainder = Math.max(0.0, 1.0 - specifiedSum) / unspecifiedCount;
            for (int i = 0; i < comp.length; i++) {
                if (!specified[i]) comp[i] = remainder;
            }
        }

        EquilibriumResult result = EquilibriumSolveHelper.solveOrSentinel(t, p, comp, candidates);
        return toDiagramEquilibrium(result, conds, candidates);
    }

    /** Reads each axis condition's current value back off a solved {@link DiagramEquilibrium}. */
    private static double[] extractAxisValues(DiagramEquilibrium equilibrium, ConditionSet conds) {
        List<Condition> axes = conds.axisConditions();
        double[] values = new double[axes.size()];
        for (int i = 0; i < axes.size(); i++) {
            Condition axis = axes.get(i);
            switch (axis.variable) {
                case TEMPERATURE: values[i] = equilibrium.T; break;
                case PRESSURE: values[i] = equilibrium.P; break;
                case COMPOSITION: values[i] = overallComposition(equilibrium)[axis.componentIndex]; break;
                default: throw new IllegalStateException("Unhandled axis variable: " + axis.variable);
            }
        }
        return values;
    }

    /**
     * Overall system composition (mole fractions), lever-ruled across a
     * {@link DiagramEquilibrium}'s stable phases by real atom count
     * ({@code amount * totalMoles}, not raw formula-unit {@code amount}
     * -- see {@link EquilibriumResult.PhaseResult#atoms()}).
     */
    private static double[] overallComposition(DiagramEquilibrium equilibrium) {
        int nc = equilibrium.chemicalPotentials.length;
        double[] atomsPerComponent = new double[nc];
        double totalAtoms = 0.0;
        for (String phaseName : equilibrium.stablePhases) {
            double phaseAtoms = equilibrium.phaseAmounts.get(phaseName)
                    * equilibrium.phaseTotalMoles.get(phaseName);
            double[] x = equilibrium.phaseMoleFractions.get(phaseName);
            totalAtoms += phaseAtoms;
            for (int i = 0; i < nc; i++) {
                atomsPerComponent[i] += phaseAtoms * x[i];
            }
        }
        double[] comp = new double[nc];
        for (int i = 0; i < nc; i++) {
            comp[i] = atomsPerComponent[i] / totalAtoms;
        }
        return comp;
    }

    /** Converts a solver-level {@link EquilibriumResult} into a {@link DiagramEquilibrium}. */
    private static DiagramEquilibrium toDiagramEquilibrium(
            EquilibriumResult result, ConditionSet conds, List<GibbsEnergyModel> candidates) {

        java.util.Map<String, Double> phaseAmounts = new java.util.LinkedHashMap<>();
        java.util.Map<String, double[]> phaseConstitutions = new java.util.LinkedHashMap<>();
        java.util.Map<String, double[]> phaseMoleFractions = new java.util.LinkedHashMap<>();
        java.util.Map<String, Double> phaseTotalMoles = new java.util.LinkedHashMap<>();
        java.util.Set<String> stablePhases = new java.util.LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            phaseAmounts.put(pr.phaseName, pr.amount);
            phaseConstitutions.put(pr.phaseName, pr.y);
            phaseMoleFractions.put(pr.phaseName, pr.x);
            phaseTotalMoles.put(pr.phaseName, pr.totalMoles);
            stablePhases.add(pr.phaseName);
        }

        boolean globallyStable = result.isConverged() && isGloballyStable(result, candidates);

        return new DiagramEquilibrium(
                result.getT(), result.getP(), phaseAmounts, phaseConstitutions,
                phaseMoleFractions, phaseTotalMoles, result.getMu(), conds, stablePhases,
                result.isConverged(), globallyStable);
    }

    /**
     * Algorithm B (Fig. 4): traces one complete diagram from {@code conds}.
     *
     * @param conds           full condition set for the diagram (n+2
     *                        conditions, 1 or 2 of them {@link
     *                        Condition.Role#AXIS})
     * @param startAxisValues the diagram's already-established initial
     *                        equilibrium condition for each axis (one
     *                        value per {@code conds.axisConditions()}, in
     *                        order) -- the point the caller's own single-
     *                        point calculation already converged at, NOT
     *                        necessarily either axis's {@code min}/{@code
     *                        max} plot bound
     * @param candidates      candidate phase models
     * @return the complete traced diagram: nodes, lines, and equilibria
     * @throws IllegalArgumentException if {@code conds} has other than 1 or 2 axis conditions
     */
    public static DiagramResult traceAlgorithmB(
            ConditionSet conds, double[] startAxisValues, List<GibbsEnergyModel> candidates) {

        List<Condition> axes = conds.axisConditions();
        int numAxes = axes.size();
        if (numAxes != 1 && numAxes != 2) {
            throw new IllegalArgumentException(
                    "Algorithm B supports 1 or 2 axis conditions; got " + numAxes);
        }

        DiagramResult diagram = new DiagramResult();

        DiagramEquilibrium equilibrium0 = callAlgorithmA(conds, startAxisValues, candidates);

        if (numAxes == 1) {
            DiagramNode node0 = new DiagramNode(equilibrium0, new java.util.ArrayList<>());
            node0.exits.add(new DiagramExit(node0, equilibrium0, false, null, 0, +1, null));
            node0.exits.add(new DiagramExit(node0, equilibrium0, false, null, 0, -1, null));
            diagram.nodes.add(node0);
            callAlgorithmC1(diagram, conds, candidates);

        } else {
            int axis = walkedAxisIndex(axes);
            int otherAxis = 1 - axis;

            double[] currentAxisValues = extractAxisValues(equilibrium0, conds);
            DiagramEquilibrium current = equilibrium0;
            while (true) {
                currentAxisValues[axis] += axes.get(axis).step;
                current = callAlgorithmA(conds, currentAxisValues, candidates);
                if (current.stablePhases.equals(equilibrium0.stablePhases)) {
                    continue; // no phase change
                } else {
                    break; // phases changed
                }
            }

            String alpha = changedPhase(equilibrium0.stablePhases, current.stablePhases);

            DiagramNode node0 = new DiagramNode(current, new java.util.ArrayList<>());
            node0.exits.add(new DiagramExit(node0, current, false, alpha, otherAxis, +1, null));
            node0.exits.add(new DiagramExit(node0, current, false, alpha, otherAxis, -1, null));
            diagram.nodes.add(node0);
            callAlgorithmC1(diagram, conds, candidates);
        }

        return diagram;
    }

    /**
     * Picks which of 2 axis conditions Algorithm B walks first ("normally
     * a potential" -- Sundman 2021 3.3): the first TEMPERATURE or
     * PRESSURE axis condition, by position in {@code axes}, so the rule
     * does not depend on which index the caller happened to list a
     * potential axis at. Falls back to index 0 when both axes are
     * COMPOSITION (e.g. a ternary isothermal section).
     */
    private static int walkedAxisIndex(List<Condition> axes) {
        for (int i = 0; i < axes.size(); i++) {
            Condition.Variable v = axes.get(i).variable;
            if (v == Condition.Variable.TEMPERATURE || v == Condition.Variable.PRESSURE) {
                return i;
            }
        }
        return 0;
    }

    /** The single phase name present in exactly one of {@code before}/{@code after}. */
    private static String changedPhase(java.util.Set<String> before, java.util.Set<String> after) {
        java.util.Set<String> symmetricDifference = new java.util.LinkedHashSet<>(before);
        for (String name : after) {
            if (before.contains(name)) {
                symmetricDifference.remove(name);
            } else {
                symmetricDifference.add(name);
            }
        }
        if (symmetricDifference.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one changed phase between " + before + " and " + after
                    + ", found " + symmetricDifference);
        }
        return symmetricDifference.iterator().next();
    }

    private static final int MAX_CONVERGENCE_RETRIES = 3;
    private static final int GLOBAL_CHECK_INTERVAL = 10;

    /**
     * Algorithm C1 (Sundman 2021 Fig. 5): "will begin searching the list
     * of nodes to find exits to generate lines. If there are none the
     * mapping is finished" (S3.3). Searches {@code diagram.nodes} --
     * not just the node that started the diagram -- so exits later
     * pushed onto that list by C2/D are found too, then walks the found
     * exit one small axis increment at a time until the walked axis runs
     * out of bounds, convergence repeatedly fails, or the stable phase
     * set changes. A plain (STEP) exit walks via {@link #callAlgorithmA};
     * a ZPF exit ({@code exit.fixedPhase != null}, Sundman 2021 S3.3)
     * walks via {@link EquilibriumSolverV2#solveZpf}, releasing the
     * diagram's other axis to hold the fixed phase at zero amount along
     * the line.
     *
     * @param diagram    diagram being built; its {@code nodes} list is
     *                   searched for exits and its {@code lines} are
     *                   appended to here
     * @param conds      full condition set for the diagram
     * @param candidates candidate phase models
     */
    static void callAlgorithmC1(
            DiagramResult diagram,
            ConditionSet conds,
            List<GibbsEnergyModel> candidates) {

        List<Condition> axes = conds.axisConditions();

        while (true) {
            DiagramExit exit = searchPendingExit(diagram.nodes);
            if (exit == null) {
                return;
            }

            java.util.Set<String> runningStableSet = exit.equilibrium.stablePhases;

            DiagramLineResult line = new DiagramLineResult(exit.node);
            diagram.lines.add(line);

            int axis = exit.initialAxis;
            Condition axisCondition = axes.get(axis);
            double[] anchorAxisValues = extractAxisValues(exit.equilibrium, conds);
            double stepSize = axisCondition.step;
            DiagramEquilibrium seed = exit.equilibrium;

            double[] currentAxisValues = anchorAxisValues.clone();
            currentAxisValues[axis] += stepSize * exit.direction;
            DiagramEquilibrium result = callAlgorithmAOrZpf(
                    conds, currentAxisValues, axis, exit.fixedPhase, seed, candidates);

            if (exit.forbiddenPhase != null && result.stablePhases.contains(exit.forbiddenPhase)) {
                exit.direction = -exit.direction;
                currentAxisValues = anchorAxisValues.clone();
                currentAxisValues[axis] += stepSize * exit.direction;
                result = callAlgorithmAOrZpf(
                        conds, currentAxisValues, axis, exit.fixedPhase, seed, candidates);
            }

            int attempts = 0;
            while (true) {
                if (!result.converged) {
                    if (attempts >= MAX_CONVERGENCE_RETRIES) {
                        line.terminatedReason = "convergence failure";
                        exit.done = true;
                        break;
                    }
                    stepSize *= 0.5;
                    currentAxisValues = anchorAxisValues.clone();
                    currentAxisValues[axis] += stepSize * exit.direction;
                    result = callAlgorithmAOrZpf(
                            conds, currentAxisValues, axis, exit.fixedPhase, seed, candidates);
                    attempts++;
                    continue;
                }

                if (!axisCondition.toAxisConfig().inBounds(currentAxisValues[axis])) {
                    line.terminatedReason = "axis limit";
                    exit.done = true;
                    break;
                }

                if (!result.stablePhases.equals(runningStableSet)) {
                    line.terminatedReason = "phase change";
                    exit.done = true;
                    callAlgorithmC2(exit, result, axis, runningStableSet, conds, diagram, line, candidates);
                    break;
                }

                diagram.equilibriaBuffer.add(result);
                line.equilibria.add(result);

                if (GLOBAL_CHECK_INTERVAL > 0 && line.equilibria.size() % GLOBAL_CHECK_INTERVAL == 0
                        && !result.globallyStable) {
                    line.terminatedReason = "excluded";
                    exit.done = true;
                    break;
                }

                // A ZPF step (exit.fixedPhase != null) only sets the walked
                // axis directly; the diagram's other axis is released and
                // solved for (Sundman 2021 S3.3), so `result`'s actual
                // converged state -- not the `currentAxisValues` fed into
                // callAlgorithmAOrZpf -- is the true position of both axes.
                double[] solvedAxisValues = extractAxisValues(result, conds);

                int newAxis = selectAxisWithLargestVariation(axes, anchorAxisValues, solvedAxisValues);
                if (newAxis != axis) {
                    // exit.direction is a sign defined relative to the axis it
                    // was walking; reusing it unchanged after switching axes
                    // is meaningless (it could just as well continue OR
                    // reverse the new axis). Re-derive it from the new axis's
                    // own observed trend over the step just accepted, per OC's
                    // map_step2 (smp2A.F90 ~3780-3797: "mapline%axandir=-nyax"
                    // / "=nyax" chosen from the sign of dax1(nyax)/dax2(nyax),
                    // never carried over from the old axis).
                    double newAxisDelta = solvedAxisValues[newAxis] - anchorAxisValues[newAxis];
                    exit.direction = newAxisDelta < 0 ? -1 : 1;
                }
                axis = newAxis;
                axisCondition = axes.get(axis);

                attempts = 0;
                stepSize = axisCondition.step;
                seed = result;
                anchorAxisValues = solvedAxisValues;
                currentAxisValues = anchorAxisValues.clone();
                currentAxisValues[axis] += stepSize * exit.direction;
                result = callAlgorithmAOrZpf(
                        conds, currentAxisValues, axis, exit.fixedPhase, seed, candidates);
            }
        }
    }

    /** OC's own T/P node-matching relative tolerance ({@code vz}, {@code map_newnode}, smp2A.F90). */
    private static final double NODE_TP_RELATIVE_TOLERANCE = 1.0e-8;

    /** OC's own chemical-potential node-matching relative tolerance ({@code 20*vz}, {@code map_newnode}). */
    private static final double NODE_MU_RELATIVE_TOLERANCE = 2.0e-7;

    /**
     * Algorithm C2 (Sundman 2021 Fig. 6): resolves a stable-phase-set
     * change detected by C1 into a boundary equilibrium and, from it, a
     * matched-or-new {@link DiagramNode} with its own exits, so C1's own
     * {@code searchPendingExit} can pick them up on a later loop
     * iteration. Every branch below terminates {@code line} and leaves
     * {@code exit.done = true} (already set by the caller) before
     * returning -- there is no explicit "back to C1" call, since
     * returning here puts control back at {@code callAlgorithmC1}'s own
     * {@code searchPendingExit} loop directly.
     *
     * @param exit              the exit whose line just detected a crossing
     * @param crossingResult    the (not-yet-saved) equilibrium at/after the crossing
     * @param axis              the axis {@code exit}'s line was walking at
     *                          the moment of crossing (may differ from
     *                          {@code exit.initialAxis} after an axis switch)
     * @param runningStableSet  the line's own stable-phase set before the crossing
     * @param conds             full condition set for the diagram
     * @param diagram           diagram being built; a matched-or-new node
     *                          may be appended to its {@code nodes} list
     * @param line              the line that detected the crossing
     * @param candidates        candidate phase models
     */
    private static void callAlgorithmC2(
            DiagramExit exit,
            DiagramEquilibrium crossingResult,
            int axis,
            java.util.Set<String> runningStableSet,
            ConditionSet conds,
            DiagramResult diagram,
            DiagramLineResult line,
            List<GibbsEnergyModel> candidates) {

        String alpha = changedPhase(runningStableSet, crossingResult.stablePhases);

        List<Condition> axes = conds.axisConditions();
        Condition axisCondition = axes.get(axis);

        double t = conds.fixedTemperature();
        double p = conds.fixedPressure();
        double[] comp = overallComposition(crossingResult);
        EquilibriumSolverV2.ReleasedVariable released;
        int releasedComponentIndex = -1;
        switch (axisCondition.variable) {
            case TEMPERATURE:
                released = EquilibriumSolverV2.ReleasedVariable.TEMPERATURE;
                break;
            case PRESSURE:
                released = EquilibriumSolverV2.ReleasedVariable.PRESSURE;
                break;
            case COMPOSITION:
                released = EquilibriumSolverV2.ReleasedVariable.COMPOSITION;
                releasedComponentIndex = axisCondition.componentIndex;
                break;
            default:
                throw new IllegalStateException("Unhandled axis variable: " + axisCondition.variable);
        }
        // Every non-walked, non-released condition stays at its own fixed
        // value; T/P above already default to conds' fixed values and are
        // only overwritten below when the walked axis is TEMPERATURE/PRESSURE
        // (a released T/P is instead seeded from the crossing result and
        // solved for by solveZpf itself).
        if (axisCondition.variable == Condition.Variable.TEMPERATURE) {
            t = crossingResult.T;
        } else if (axisCondition.variable == Condition.Variable.PRESSURE) {
            p = crossingResult.P;
        }

        EquilibriumResult seedResult = toEquilibriumResultForSeeding(crossingResult);

        EquilibriumSolverV2.BoundarySolveResult boundary;
        try {
            boundary = new EquilibriumSolverV2().solveZpf(
                    t, p, comp, candidates, seedResult, alpha, 0.0, released, releasedComponentIndex);
        } catch (RuntimeException e) {
            boundary = null;
        }

        if (boundary == null || !boundary.equilibrium.isConverged()) {
            line.terminatedReason = "boundary solve failed";
            return;
        }

        DiagramEquilibrium boundaryEquilibrium = toDiagramEquilibrium(boundary.equilibrium, conds, candidates);

        if (!isGloballyStable(boundary.equilibrium, candidates)) {
            line.terminatedReason = "excluded";
            return;
        }

        DiagramNode matched = findMatchingNode(diagram.nodes, boundaryEquilibrium);
        if (matched != null) {
            exit.done = true;
            line.endNode = matched;
            return;
        }

        DiagramNode node = new DiagramNode(boundaryEquilibrium, new java.util.ArrayList<>());
        diagram.nodes.add(node);
        line.endNode = node;

        NodeClass nodeClass = classifyNode(conds, boundaryEquilibrium.stablePhases.size());

        if (exit.fixedPhase == null) {
            // STEP line (Sundman 2021 S3.2): single continuation exit, same
            // axis/direction, no fixed phase -- a 1-axis STEP diagram has no
            // released axis to hold anything at zero along the next line.
            node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, null, axis, exit.direction, null));
            return;
        }

        if (nodeClass == NodeClass.TIE_LINE_IN_PLANE) {
            node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, alpha, axis, +1, exit.fixedPhase));
            node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, alpha, axis, -1, exit.fixedPhase));
            return;
        }

        if (nodeClass == NodeClass.INVARIANT) {
            attachInvariantExits(node, boundaryEquilibrium, axis, alpha, exit.fixedPhase, conds);
            return;
        }

        // ISOPLETH_CROSSING: exit.fixedPhase's own line continues one more
        // step in the same direction (forbidding alpha), plus alpha's own
        // ZPF line in both directions (forbidding exit.fixedPhase).
        node.exits.add(new DiagramExit(
                node, boundaryEquilibrium, false, exit.fixedPhase, axis, exit.direction, alpha));
        node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, alpha, axis, +1, exit.fixedPhase));
        node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, alpha, axis, -1, exit.fixedPhase));
    }

    /**
     * Algorithm D (Fig. 7, Eq. 9): finds every valid exit-phase pair at an
     * invariant node (all other stable phases at strictly positive
     * amount) and attaches 2 exits per pair, excluding the pair already
     * on the arriving line -- see {@link InvariantExitPairFinder}.
     */
    private static void attachInvariantExits(
            DiagramNode node,
            DiagramEquilibrium boundaryEquilibrium,
            int axis,
            String alpha,
            String arrivingLineFixedPhase,
            ConditionSet conds) {

        List<String> phaseNames = new java.util.ArrayList<>(boundaryEquilibrium.stablePhases);
        double[][] compositions = new double[phaseNames.size()][];
        for (int i = 0; i < phaseNames.size(); i++) {
            compositions[i] = boundaryEquilibrium.phaseMoleFractions.get(phaseNames.get(i));
        }
        double[] targetComposition = overallComposition(boundaryEquilibrium);

        InvariantExitPairFinder.ExitPair arrivalPair =
                new InvariantExitPairFinder.ExitPair(alpha, arrivingLineFixedPhase);
        List<InvariantExitPairFinder.ExitPair> exitPairs = InvariantExitPairFinder.findExitPairs(
                phaseNames, compositions, targetComposition, arrivalPair);

        for (InvariantExitPairFinder.ExitPair pair : exitPairs) {
            node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, pair.beta2, axis, +1, pair.beta1));
            node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, pair.beta2, axis, -1, pair.beta1));
            node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, pair.beta1, axis, +1, pair.beta2));
            node.exits.add(new DiagramExit(node, boundaryEquilibrium, false, pair.beta1, axis, -1, pair.beta2));
        }
    }

    /**
     * Finds an already-registered node matching {@code candidate} under
     * OC's own {@code map_newnode} rule: identical stable phase set, T
     * and P within {@link #NODE_TP_RELATIVE_TOLERANCE}, and every
     * chemical potential within {@link #NODE_MU_RELATIVE_TOLERANCE} --
     * both tolerances scaled relative to the EXISTING registered node's
     * own value, floored at 1.0. Returns {@code null} if none match.
     */
    private static DiagramNode findMatchingNode(List<DiagramNode> nodes, DiagramEquilibrium candidate) {
        for (DiagramNode existing : nodes) {
            DiagramEquilibrium reference = existing.equilibrium;
            if (!reference.stablePhases.equals(candidate.stablePhases)) continue;
            if (!withinRelativeTolerance(candidate.T, reference.T, NODE_TP_RELATIVE_TOLERANCE)) continue;
            if (!withinRelativeTolerance(candidate.P, reference.P, NODE_TP_RELATIVE_TOLERANCE)) continue;
            if (reference.chemicalPotentials.length != candidate.chemicalPotentials.length) continue;
            boolean muMatch = true;
            for (int i = 0; i < reference.chemicalPotentials.length; i++) {
                if (!withinRelativeTolerance(candidate.chemicalPotentials[i],
                        reference.chemicalPotentials[i], NODE_MU_RELATIVE_TOLERANCE)) {
                    muMatch = false;
                    break;
                }
            }
            if (!muMatch) continue;
            return existing;
        }
        return null;
    }

    private static boolean withinRelativeTolerance(double candidate, double reference, double relativeTolerance) {
        double scale = Math.max(1.0, Math.abs(reference));
        return Math.abs(candidate - reference) <= scale * relativeTolerance;
    }

    /**
     * Algorithm C1's "Select axis with largest variation" box (Sundman
     * 2021 Fig. 5, S3.3): "algorithm C1 will check which axis varies
     * most rapidly and possibly change the axis to use for incrementing
     * the next iteration." Compares, for each axis, how far the last
     * accepted step moved it relative to that axis's own increment
     * ({@code |Δaxis_i| / step_i}) and returns the index of the largest;
     * a no-op for a 1-axis (STEP) diagram, where this is the only axis.
     *
     * <p>This is the paper's literal box only: unlike OpenCalphad's
     * {@code map_step2} (smp2A.F90 ~3660-3720), it has no hysteresis
     * margin, no cooldown period after a switch, and no interaction with
     * fix-phase selection -- those are OC engineering refinements beyond
     * what Fig. 5 specifies, deliberately left out here.
     */
    private static int selectAxisWithLargestVariation(
            List<Condition> axes, double[] previousAxisValues, double[] newAxisValues) {
        int best = 0;
        double bestVariation = -1.0;
        for (int i = 0; i < axes.size(); i++) {
            double variation = Math.abs(newAxisValues[i] - previousAxisValues[i]) / axes.get(i).step;
            if (variation > bestVariation) {
                bestVariation = variation;
                best = i;
            }
        }
        return best;
    }

    /**
     * One C1 line-walking step: an ordinary {@link #callAlgorithmA} call
     * when {@code fixedPhase} is {@code null} (STEP), or a ZPF boundary
     * solve via {@link EquilibriumSolverV2#solveZpf} when not (MAP) --
     * {@code walkedAxis}'s own condition is set to {@code
     * axisValues[walkedAxis]} directly, while the diagram's OTHER axis
     * condition is released and solved for so that {@code fixedPhase}
     * stays at exactly zero amount, per Sundman 2021 S3.3.
     */
    private static DiagramEquilibrium callAlgorithmAOrZpf(
            ConditionSet conds,
            double[] axisValues,
            int walkedAxis,
            String fixedPhase,
            DiagramEquilibrium seed,
            List<GibbsEnergyModel> candidates) {

        if (fixedPhase == null) {
            return callAlgorithmA(conds, axisValues, candidates);
        }

        List<Condition> axes = conds.axisConditions();
        if (axes.size() != 2) {
            throw new UnsupportedOperationException(
                    "Algorithm C1: a ZPF exit (fixedPhase != null) requires exactly 2 axis "
                    + "conditions (the walked axis and the one released to hold the fixed "
                    + "phase at zero amount); got " + axes.size() + ".");
        }
        int releasedAxis = 1 - walkedAxis;
        Condition releasedCondition = axes.get(releasedAxis);

        double t = conds.fixedTemperature();
        double p = conds.fixedPressure();
        double[] comp = new double[conds.numComponents()];
        boolean[] specified = new boolean[comp.length];
        double specifiedSum = 0.0;

        for (Condition c : conds.all()) {
            if (c.variable != Condition.Variable.COMPOSITION || !c.isFixed()) continue;
            comp[c.componentIndex] = c.fixedValue;
            specified[c.componentIndex] = true;
            specifiedSum += c.fixedValue;
        }

        Condition walkedCondition = axes.get(walkedAxis);
        switch (walkedCondition.variable) {
            case TEMPERATURE: t = axisValues[walkedAxis]; break;
            case PRESSURE: p = axisValues[walkedAxis]; break;
            case COMPOSITION:
                comp[walkedCondition.componentIndex] = axisValues[walkedAxis];
                specified[walkedCondition.componentIndex] = true;
                specifiedSum += axisValues[walkedAxis];
                break;
            default: throw new IllegalStateException("Unhandled axis variable: " + walkedCondition.variable);
        }

        // The released axis's own last-known value seeds comp/T/P where
        // solveZpf does not overwrite it outright (T and P, released via
        // solveBoundaryReleasingT/P, are seeded then solved for in place;
        // a released COMPOSITION entry is overwritten by solveBoundary's
        // own releasedComponentIndex mechanism, so its seed value here is
        // only a starting point for that Newton iteration).
        EquilibriumSolverV2.ReleasedVariable released;
        int releasedComponentIndex = -1;
        switch (releasedCondition.variable) {
            case TEMPERATURE:
                released = EquilibriumSolverV2.ReleasedVariable.TEMPERATURE;
                t = seed.T;
                break;
            case PRESSURE:
                released = EquilibriumSolverV2.ReleasedVariable.PRESSURE;
                p = seed.P;
                break;
            case COMPOSITION:
                released = EquilibriumSolverV2.ReleasedVariable.COMPOSITION;
                releasedComponentIndex = releasedCondition.componentIndex;
                comp[releasedComponentIndex] = overallComposition(seed)[releasedComponentIndex];
                specified[releasedCondition.componentIndex] = true;
                specifiedSum += comp[releasedComponentIndex];
                break;
            default: throw new IllegalStateException("Unhandled axis variable: " + releasedCondition.variable);
        }

        int unspecifiedCount = 0;
        for (boolean s : specified) if (!s) unspecifiedCount++;
        if (unspecifiedCount > 0) {
            double remainder = Math.max(0.0, 1.0 - specifiedSum) / unspecifiedCount;
            for (int i = 0; i < comp.length; i++) {
                if (!specified[i]) comp[i] = remainder;
            }
        }

        EquilibriumResult seedResult = toEquilibriumResultForSeeding(seed);

        EquilibriumSolverV2.BoundarySolveResult boundary;
        try {
            boundary = new EquilibriumSolverV2().solveZpf(
                    t, p, comp, candidates, seedResult, fixedPhase, 0.0,
                    released, releasedComponentIndex);
        } catch (RuntimeException e) {
            return toDiagramEquilibrium(
                    new EquilibriumResult(t, p, new double[comp.length],
                            java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                            false, 0),
                    conds, candidates);
        }

        return toDiagramEquilibrium(boundary.equilibrium, conds, candidates);
    }

    /**
     * Rebuilds a solver-level {@link EquilibriumResult} from a {@link
     * DiagramEquilibrium}, carrying exactly the fields Sundman 2021 S3.1
     * requires an exit's stored equilibrium to retain ("T, P, the amount
     * and constitution of all phases and the chemical potentials") --
     * sufficient to seed {@link EquilibriumSolverV2#solveZpf}, which only
     * reads {@code phaseName}/{@code amount}/{@code y} off each seed
     * {@code PhaseResult} and recomputes everything else itself.
     */
    private static EquilibriumResult toEquilibriumResultForSeeding(DiagramEquilibrium eq) {
        List<EquilibriumResult.PhaseResult> stable = new java.util.ArrayList<>();
        for (String phaseName : eq.stablePhases) {
            stable.add(new EquilibriumResult.PhaseResult(
                    phaseName, "", eq.phaseAmounts.get(phaseName),
                    eq.phaseMoleFractions.get(phaseName), eq.phaseConstitutions.get(phaseName),
                    0.0, 0.0, eq.phaseTotalMoles.get(phaseName)));
        }
        return new EquilibriumResult(eq.T, eq.P, eq.chemicalPotentials,
                stable, java.util.Collections.emptyList(), eq.converged, 0);
    }

    /**
     * Scans every node's exits, in {@code nodes} order, for the first
     * not-yet-{@code done} exit, or {@code null} if none remain across
     * the whole list (Sundman 2021 S3.3: "Algorithm C1 will begin
     * searching the list of nodes to find exits ... If there are none
     * the mapping is finished").
     */
    private static DiagramExit searchPendingExit(List<DiagramNode> nodes) {
        for (DiagramNode node : nodes) {
            for (DiagramExit exit : node.exits) {
                if (!exit.done) {
                    return exit;
                }
            }
        }
        return null;
    }

    /**
     * Parses the TDB and builds Gibbs-energy models for each candidate phase.
     *
     * @param tdbFilePath path to the TDB file
     * @param elements list of element symbols
     * @param candidatePhases list of phase names
     * @return the built thermodynamic system
     * @throws IOException if the TDB file cannot be read
     */
    public static ThermodynamicSystem defineSystem(
            String tdbFilePath,
            List<String> elements,
            List<String> candidatePhases) throws IOException {

        return ThermodynamicSystem.build(tdbFilePath, elements, candidatePhases);
    }

    /**
     * Validates that the number of conditions supplied equals n+2 for an
     * n-component system. An n-component equilibrium requires exactly n+2
     * thermodynamic conditions (T, P, and n mole fractions by default, or
     * allowed substitutes like chemical potential, entropy, enthalpy, etc.).
     *
     * @param numComponents the number of independent components (n)
     * @param numConditionsSupplied the number of conditions being set
     * @throws IllegalArgumentException if the count does not match n+2
     */
    public static void validateConditionCount(int numComponents, int numConditionsSupplied) {
        int requiredConditions = numComponents + 2;
        int idf = requiredConditions - numConditionsSupplied;
        if (idf != 0) {
            throw new IllegalArgumentException(
                    "Degrees of freedom not zero: " + numComponents + " components require exactly "
                    + requiredConditions + " conditions (n+2), but " + numConditionsSupplied
                    + " were supplied (idf=" + idf + ").");
        }
    }

    /**
     * Generates starting point(s) for diagram tracing. Currently returns
     * a single starting point; multiple starting points are a manual,
     * user-driven operation (issue #TODO: linked disconnected components).
     *
     * @param singleStartWalkValue the starting value along the walk axis
     * @return a list containing the single starting point
     */
    public static List<double[]> generateStartingPoints(double singleStartWalkValue) {
        return List.of(new double[] { singleStartWalkValue });
    }

    /**
     * Algorithm B orchestration: the single top-to-bottom entry point for
     * automated phase-diagram tracing, from condition validation through
     * plot classification. Validates the n+2 condition count, generates
     * the starting point(s), solves the initial equilibrium once, then
     * branches on axis count to either STEP (single-axis) or MAP (two-axis)
     * draining, and returns the complete classified result.
     *
     * @param numComponents number of independent components (n), for the
     *                      n+2 condition-count check
     * @param axes 1 or 2 axes; if 2 axes, axes[1] must be COMPOSITION
     * @param startValues starting value per axis
     * @param fixedT temperature (used if not an axis)
     * @param fixedP pressure (used if not an axis)
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @return the classified phase diagram result
     * @throws IllegalArgumentException if axes.length not 1 or 2, or
     *         if 2-axis and axes[1].type != COMPOSITION
     * @throws IllegalStateException if initial equilibrium fails to converge
     */
    public static PhaseDiagramResult calculatePhaseDiagram(
            int numComponents,
            AxisConfig[] axes,
            double[] startValues,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        Traced traced = traceForClassification(
                numComponents, axes, startValues, fixedT, fixedP, compOverall, candidates);
        return classifyPlot(traced.registry, traced.plotType, traced.axisNames,
                traced.axisMin, traced.axisMax, traced.compositionAxisIndex,
                traced.compositionComponentIndex);
    }

    /** Bundles a traced {@link NodeRegistry} with the metadata {@link #classifyPlot} needs. */
    private static final class Traced {
        final NodeRegistry registry;
        final PlotType plotType;
        final String[] axisNames;
        final double[] axisMin;
        final double[] axisMax;
        final int compositionAxisIndex;
        final int compositionComponentIndex;

        Traced(NodeRegistry registry, PlotType plotType, String[] axisNames, double[] axisMin,
                double[] axisMax, int compositionAxisIndex, int compositionComponentIndex) {
            this.registry = registry;
            this.plotType = plotType;
            this.axisNames = axisNames;
            this.axisMin = axisMin;
            this.axisMax = axisMax;
            this.compositionAxisIndex = compositionAxisIndex;
            this.compositionComponentIndex = compositionComponentIndex;
        }
    }

    private static Traced traceForClassification(
            int numComponents,
            AxisConfig[] axes,
            double[] startValues,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        if (axes.length != startValues.length) {
            throw new IllegalArgumentException(
                    "axes.length (" + axes.length + ") must equal startValues.length ("
                    + startValues.length + ")");
        }
        if (axes.length != 1 && axes.length != 2) {
            throw new IllegalArgumentException(
                    "calculatePhaseDiagram supports 1 axis (STEP) or 2 axes (binary MAP) only; got "
                    + axes.length);
        }
        if (axes.length == 2 && axes[1].type != AxisConfig.Type.COMPOSITION) {
            throw new IllegalArgumentException(
                    "For 2-axis MAP, axes[1] must be COMPOSITION; got " + axes[1].type);
        }

        validateConditionCount(numComponents, numComponents + 2);
        generateStartingPoints(startValues[0]);

        EquilibriumResult initialEquilibrium = solveInitialEquilibrium(
                axes[0], fixedT, fixedP, startValues[0], compOverall, candidates);

        LineFollower.Setup setup;
        PlotType plotType;
        String[] axisNames;
        double[] axisMin, axisMax;
        int compositionAxisIndex, compositionComponentIndex;

        if (axes.length == 1) {
            setup = new StepDiagramTracer().setUp(
                    axes[0], fixedT, fixedP, compOverall, candidates, initialEquilibrium);

            plotType = PlotType.PROPERTY_OR_STEP_DIAGRAM;
            axisNames = new String[] { axes[0].name };
            axisMin = new double[] { axes[0].min };
            axisMax = new double[] { axes[0].max };
            compositionAxisIndex = -1;
            compositionComponentIndex = -1;
        } else {
            setup = new MapDiagramTracer().setUp(
                    axes[0], axes[1], fixedT, fixedP, startValues[0], compOverall, candidates,
                    initialEquilibrium);

            plotType = PlotType.BINARY_T_X;
            axisNames = new String[] { axes[0].name, axes[1].name };
            axisMin = new double[] { axes[0].min, axes[1].min };
            axisMax = new double[] { axes[0].max, axes[1].max };
            compositionAxisIndex = 1;
            compositionComponentIndex = axes[1].componentIndex;
        }

        LineFollower.drain(setup, candidates);

        return new Traced(setup.registry(), plotType, axisNames, axisMin, axisMax,
                compositionAxisIndex, compositionComponentIndex);
    }

    /**
     * Solves a single equilibrium at the starting conditions. This is the
     * shared Algorithm A box that Algorithm B executes before branching
     * to STEP or MAP, ensuring both branches operate from the same
     * converged starting point.
     *
     * @param axis the first/walked axis
     * @param fixedT temperature (modified by axis if axis is TEMPERATURE)
     * @param fixedP pressure (modified by axis if axis is PRESSURE)
     * @param startWalkValue the axis value where to solve
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @return converged equilibrium result
     * @throws IllegalStateException if convergence fails
     */
    public static EquilibriumResult solveInitialEquilibrium(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        double t0 = fixedT, p0 = fixedP;
        double[] comp = compOverall.clone();
        switch (axis.type) {
            case TEMPERATURE: t0 = startWalkValue; break;
            case PRESSURE: p0 = startWalkValue; break;
            case COMPOSITION: comp = StepTracer.applyCompositionAxis(axis, startWalkValue, comp); break;
            default: throw new IllegalStateException("Unhandled axis type: " + axis.type);
        }

        EquilibriumResult result = EquilibriumSolveHelper.solveOrSentinel(t0, p0, comp, candidates);
        if (!result.isConverged()) {
            throw new IllegalStateException(
                    "Initial equilibrium did not converge at " + axis.name + "=" + startWalkValue);
        }
        return result;
    }

    /**
     * Drains the map diagram (2-axis ZPF tracing) from one starting point --
     * the 2-axis counterpart of {@link #drainStepLoop}. Walks the first
     * axis while keeping a phase at zero amount.
     *
     * @param walkAxis axis to walk
     * @param releaseAxis axis to release (must be COMPOSITION for binary MAP)
     * @param fixedT temperature
     * @param fixedP pressure
     * @param startWalkValue starting walk value
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     * @return registry of traced nodes and lines
     */
    public static NodeRegistry drainMapLoop(
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        return new MapDiagramTracer().drain(
                walkAxis, releaseAxis, fixedT, fixedP, startWalkValue, compOverall, candidates, startResult);
    }

    /**
     * Drains the map diagram using a ConditionSet for generalized diagram
     * types (binary, ternary isothermal, isopleth, etc.).
     *
     * @param conds full condition set for the diagram
     * @param searchAxisIndex index of the axis to search/walk
     * @param releaseAxisIndex index of the axis to release
     * @param startSearchValue starting value for the search axis
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @return registry of traced nodes and lines
     */
    public static NodeRegistry drainMapLoop(
            ConditionSet conds,
            int searchAxisIndex,
            int releaseAxisIndex,
            double startSearchValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        return new MapDiagramTracer().drain(
                conds, searchAxisIndex, releaseAxisIndex, startSearchValue, compOverall, candidates);
    }

    /**
     * Drains the step diagram (single-axis property scan) from one starting point.
     *
     * @param axis the axis to walk
     * @param fixedT temperature
     * @param fixedP pressure
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     * @return registry of traced nodes and lines
     */
    public static NodeRegistry drainStepLoop(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        return new StepDiagramTracer().drain(axis, fixedT, fixedP, compOverall, candidates, startResult);
    }

    private static final double GLOBAL_STABILITY_RELATIVE_TOLERANCE = 1.0e-4;

    /**
     * Checks whether the candidate equilibrium is globally stable via a
     * grid-based global minimization. Returns true if the candidate's Gibbs
     * energy is within tolerance of the true global minimum.
     *
     * @param candidateEquilibrium the equilibrium to check
     * @param candidates the candidate phase models
     * @return true if the candidate is globally stable
     */
    public static boolean isGloballyStable(
            EquilibriumResult candidateEquilibrium,
            List<GibbsEnergyModel> candidates) {

        double[] overallComposition = overallComposition(candidateEquilibrium);

        EquilibriumResult gridResult = new GridMinimizer().solve(
                candidates, candidateEquilibrium.getT(), candidateEquilibrium.getP(), overallComposition);

        double candidateGPerAtom = candidateEquilibrium.totalGPerAtom();
        double gridGPerAtom = gridResult.totalGPerAtom();

        double relativeDifference = (candidateGPerAtom - gridGPerAtom) / Math.abs(candidateGPerAtom);
        return relativeDifference <= GLOBAL_STABILITY_RELATIVE_TOLERANCE;
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

    // ------------------------------------------------------------------
    // (Inside the C1 loop, at a stable-set change) GIBBS PHASE RULE /
    // node classification, Algorithm D
    // ------------------------------------------------------------------

    /** Classification of a node in a phase diagram. */
    public enum NodeClass {
        /** Ordinary crossing with tie-line geometry (2 exits). */
        TIE_LINE_IN_PLANE,
        /** Ordinary crossing with isopleth geometry (3 exits). */
        ISOPLETH_CROSSING,
        /** Invariant point (f = 0, multiple exits via Algorithm D). */
        INVARIANT,
        /** Step-diagram continuation (1 exit). */
        STEP_CONTINUATION
    }

    /**
     * Classifies a node at a stable-phase-set change using the Gibbs phase
     * rule (f = n+2-p-c). Distinguishes between invariant (f=0) and
     * ordinary (f>0) nodes, and for ordinary nodes, distinguishes between
     * tie-line-in-plane and isopleth-crossing geometries based on whether
     * any composition is fixed outside the diagram axes.
     *
     * @param conds the full condition set for the diagram
     * @param numStablePhases number of stable phases at this node
     * @return classification of the node
     */
    public static NodeClass classifyNode(ConditionSet conds, int numStablePhases) {
        int numFixedPotentialConditions = 0;
        boolean hasFixedComposition = false;

        for (Condition c : conds.all()) {
            if (!c.isFixed()) continue;
            switch (c.variable) {
                case TEMPERATURE:
                case PRESSURE:
                    numFixedPotentialConditions++;
                    break;
                case COMPOSITION:
                    hasFixedComposition = true;
                    break;
                default:
                    // TOTAL_MOLES: neither a potential condition nor a
                    // composition -- does not affect either count.
            }
        }

        NodeClass ordinary = hasFixedComposition
                ? NodeClass.ISOPLETH_CROSSING
                : NodeClass.TIE_LINE_IN_PLANE;

        int f = conds.numComponents() + 2 - numStablePhases - numFixedPotentialConditions;
        return f == 0 ? NodeClass.INVARIANT : ordinary;
    }

    /**
     * Filters the traced line network, removing excluded lines and returning
     * the lines ready for plotting. Nodes are deduplicated inline during
     * tracing via {@link NodeRegistry#findOrCreate}.
     *
     * @param registry the traced node/line registry
     * @return all non-excluded lines in the registry
     */
    public static List<Line> mergeDedupNetwork(NodeRegistry registry) {
        List<Line> kept = new java.util.ArrayList<>();
        for (Node node : registry.getNodes()) {
            for (Line line : node.getLines()) {
                if (!line.isExcluded()) {
                    kept.add(line);
                }
            }
        }
        return kept;
    }

    /**
     * Identifies and labels phase regions from the traced line network.
     * Assigns stable-phase labels to each line and node, and computes
     * tie-triangles for 3-phase regions.
     *
     * @param registry the traced node/line registry
     * @return phase regions with labels and tie-triangles
     */
    public static PhaseRegions identifyPhaseRegions(NodeRegistry registry) {
        java.util.Map<Line, java.util.Set<String>> lineLabels = new java.util.LinkedHashMap<>();
        java.util.Map<Node, java.util.Set<String>> nodeLabels = new java.util.LinkedHashMap<>();
        List<PhaseRegions.TieTriangle> tieTriangles = new java.util.ArrayList<>();

        for (Node node : registry.getNodes()) {
            nodeLabels.put(node, node.stablePhaseNames);

            if (node.stablePhaseNames.size() == 3) {
                List<String> phaseNames = new java.util.ArrayList<>();
                List<double[]> vertices = new java.util.ArrayList<>();
                for (EquilibriumResult.PhaseResult pr : node.equilibrium.getStablePhases()) {
                    phaseNames.add(pr.phaseName);
                    vertices.add(pr.x);
                }
                tieTriangles.add(new PhaseRegions.TieTriangle(node, phaseNames, vertices));
            }

            for (Line line : node.getLines()) {
                lineLabels.put(line, lineStablePhaseNames(line));
            }
        }

        return new PhaseRegions(lineLabels, nodeLabels, tieTriangles);
    }

    /**
     * The stable-phase set bounding {@code line} -- every point sampled
     * along it shares the same stable-phase set by construction (a
     * {@link Line} terminates exactly when that set changes), so the
     * first sampled point's set suffices; empty if no points were
     * sampled (e.g. a line terminated immediately at an axis limit).
     */
    private static java.util.Set<String> lineStablePhaseNames(Line line) {
        List<EquilibriumResult> points = line.getPoints();
        if (points.isEmpty()) {
            return java.util.Set.of();
        }
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : points.get(0).getStablePhases()) {
            names.add(pr.phaseName);
        }
        return names;
    }

    // ------------------------------------------------------------------
    // CLASSIFY REQUESTED PLOT / VALIDATE PLOT
    // ------------------------------------------------------------------

    /** The plot types the flowchart's CLASSIFY REQUESTED PLOT box lists. */
    public enum PlotType {
        BINARY_T_X,
        ACTIVITY_OR_CHEMICAL_POTENTIAL,
        H_X_S_X_G_X,
        TERNARY_ISOTHERMAL,
        TERNARY_ISOPLETH,
        MULTICOMPONENT_ISOPLETH_OR_PSEUDO_ISOTHERMAL,
        PROPERTY_OR_STEP_DIAGRAM
    }

    /**
     * Converts the traced node/line network into a renderable phase diagram
     * result for the specified plot type. Validates axis count and converts
     * stored equilibria into line segments and node points. For diagrams with
     * a released composition axis, splits multi-phase lines into one segment
     * per stable phase using each phase's own composition.
     *
     * @param registry the traced network
     * @param requestedType the desired plot type
     * @param axisNames diagram axis names
     * @param axisMin minimum value per axis
     * @param axisMax maximum value per axis
     * @param compositionAxisIndex index of released composition axis (-1 if none)
     * @param compositionComponentIndex component index of composition axis
     * @return the complete classified phase diagram
     * @throws IllegalArgumentException if axis count does not match requestedType
     * @throws UnsupportedOperationException if requestedType is not yet implemented
     */
    public static PhaseDiagramResult classifyPlot(
            NodeRegistry registry,
            PlotType requestedType,
            String[] axisNames,
            double[] axisMin,
            double[] axisMax,
            int compositionAxisIndex,
            int compositionComponentIndex) {

        int expectedAxes = expectedAxisCount(requestedType);
        if (axisNames.length != expectedAxes || axisMin.length != expectedAxes || axisMax.length != expectedAxes) {
            throw new IllegalArgumentException(
                    requestedType + " requires exactly " + expectedAxes + " axis/axes, got "
                    + axisNames.length + " names / " + axisMin.length + " mins / "
                    + axisMax.length + " maxes -- not a well-posed diagram (VALIDATE PLOT).");
        }

        switch (requestedType) {
            case BINARY_T_X:
            case PROPERTY_OR_STEP_DIAGRAM:
            case TERNARY_ISOTHERMAL:
            case TERNARY_ISOPLETH:
                return buildResult(registry, axisNames, axisMin, axisMax,
                        compositionAxisIndex, compositionComponentIndex);

            case ACTIVITY_OR_CHEMICAL_POTENTIAL:
            case H_X_S_X_G_X:
            case MULTICOMPONENT_ISOPLETH_OR_PSEUDO_ISOTHERMAL:
            default:
                throw new UnsupportedOperationException(
                        requestedType + " not yet implemented -- see "
                        + "PhaseDiagramEngine#classifyPlot's own javadoc for what's missing "
                        + "and docs/phase_diagram_engine_flowchart.md's CLASSIFY REQUESTED PLOT box.");
        }
    }

    /** VALIDATE PLOT's own axis-count expectation per {@link PlotType} -- see {@link #classifyPlot}'s javadoc. */
    private static int expectedAxisCount(PlotType type) {
        return type == PlotType.PROPERTY_OR_STEP_DIAGRAM ? 1 : 2;
    }

    /**
     * The actual {@link Node}/{@link Line} -&gt; {@link PhaseDiagramResult}
     * conversion shared by every implemented {@link PlotType} in {@link
     * #classifyPlot} -- see that method's javadoc for what this does and
     * does not compute (in particular the &sect;4.1-driven per-phase
     * composition split this method performs).
     */
    private static PhaseDiagramResult buildResult(
            NodeRegistry registry, String[] axisNames, double[] axisMin, double[] axisMax,
            int compositionAxisIndex, int compositionComponentIndex) {

        PhaseDiagramResult result = new PhaseDiagramResult(axisNames, axisMin, axisMax);
        int numAxes = axisNames.length;

        for (Line line : mergeDedupNetwork(registry)) {
            List<EquilibriumResult> points = line.getPoints();
            if (points.isEmpty()) {
                continue;
            }
            String fixedPhase = line.fixedPhases.isEmpty() ? null : line.fixedPhases.get(0);

            if (compositionAxisIndex < 0) {
                // No released composition (e.g. STEP) -- the stored walk
                // coordinates are already correct as-is.
                result.addLine(new LineSegment(line.getAxisCoords(), fixedPhase,
                        new java.util.ArrayList<>(lineStablePhaseNames(line))));
                continue;
            }

            // Sundman 2021 §4.1: a proper T-x diagram plots "the mole
            // fraction of Cu in ALL STABLE PHASES" -- one curve per
            // stable phase, not the overall composition Line.getAxisCoords()
            // stores at compositionAxisIndex. Split this one walked run
            // into one LineSegment per phase name, each phase's own
            // PhaseResult.x substituted in at compositionAxisIndex.
            List<double[]> walkCoords = line.getAxisCoords();
            for (String phaseName : lineStablePhaseNames(line)) {
                List<double[]> perPhaseCoords = new java.util.ArrayList<>(points.size());
                boolean phasePresentThroughout = true;
                for (int i = 0; i < points.size(); i++) {
                    double[] x = phaseCompositionOrNull(points.get(i), phaseName);
                    if (x == null) {
                        phasePresentThroughout = false;
                        break;
                    }
                    double[] coord = walkCoords.get(i).clone();
                    coord[compositionAxisIndex] = x[compositionComponentIndex];
                    perPhaseCoords.add(coord);
                }
                if (phasePresentThroughout && !perPhaseCoords.isEmpty()) {
                    result.addLine(new LineSegment(perPhaseCoords, fixedPhase, List.of(phaseName)));
                }
            }
        }

        for (Node node : registry.getNodes()) {
            // Every Node in a NodeRegistry is a real phase-set-change
            // point -- an axis-limit termination (Line#terminateAtAxisLimit)
            // never creates a Node at all, only a dangling Line with no
            // end node (see Line.getEndNode()'s javadoc), so
            // NodePoint.Type.BOUNDARY does not arise from this graph.
            NodePoint.Type type = node.stablePhaseNames.size() > numAxes + 1
                    ? NodePoint.Type.INVARIANT
                    : NodePoint.Type.CROSSING;
            result.addNode(new NodePoint(
                    node.axisValues, new java.util.ArrayList<>(node.stablePhaseNames), type));
        }

        return result;
    }

    /** {@code eq}'s stable-phase mole-fraction composition for {@code phaseName}, or {@code null} if not stable there. */
    private static double[] phaseCompositionOrNull(EquilibriumResult eq, String phaseName) {
        for (EquilibriumResult.PhaseResult pr : eq.getStablePhases()) {
            if (pr.phaseName.equals(phaseName)) {
                return pr.x;
            }
        }
        return null;
    }
}
