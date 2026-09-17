package calc.diagram;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Algorithm C1 (Sundman 2021 Calphad 75, Fig. 5): repeatedly searches
 * {@code registry} for a pending {@link Line}, walks it via {@code walker},
 * and repeats until no pending line remains. The per-segment walk and the
 * handling of a resolved crossing (node creation, exit attachment) are
 * supplied by {@code walker} -- STEP and MAP differ enough there (ZPF
 * release, exit count) that this loop stays the only code shared between them.
 */
final class LineFollower {

    private LineFollower() {
    }

    /** One pending {@link Line}'s worth of work: walk it and resolve however it ends. */
    interface SegmentWalker {
        void walkAndResolve(Line line, List<GibbsEnergyModel> candidates);
    }

    /**
     * A registry with its start node and pending exits already attached
     * (Fig. 4's "+node, 2 exits" box, either branch), plus the {@link
     * SegmentWalker} that knows how to walk this diagram's lines -- the
     * handoff point Fig. 4 shows both the STEP and MAP branches merging
     * into C1 from.
     */
    record Setup(NodeRegistry registry, SegmentWalker walker) {
    }

    static void drain(Setup setup, List<GibbsEnergyModel> candidates) {
        NodeRegistry registry = setup.registry();
        SegmentWalker walker = setup.walker();
        while (registry.hasPendingWork()) {
            Line line = registry.nextPendingLine();
            line.startWalking();
            walker.walkAndResolve(line, candidates);
        }
    }

    /** One equilibrium solve at a given axis value, scoped to a diagram's own condition mapping. */
    interface EquilibriumStepper {
        EquilibriumResult solveAt(double axisValue, List<GibbsEnergyModel> candidates);
    }

    /** True if {@code result}'s stable phase set differs from {@code line}'s running set. */
    private static boolean phaseChanged(Line line, EquilibriumResult result) {
        if (!result.isConverged()) {
            return false;
        }
        return !EquilibriumSolveHelper.stablePhaseNames(result).equals(line.getRunningStableNames());
    }

    /**
     * Returns the single phase name present in exactly one of the two sets (the
     * phase that appeared or disappeared) -- what {@code Algorithm C2}'s "fix
     * alpha at zero amount" box needs to identify -- or {@code null} if the sets
     * differ by more than one phase (an ambiguous jump {@link
     * #retryWithHalvedSteps} must narrow before C2 can be called).
     */
    private static String singleChangedPhase(Set<String> before, Set<String> after) {
        Set<String> symmetricDifference = new LinkedHashSet<>(before);
        for (String name : after) {
            if (before.contains(name)) {
                symmetricDifference.remove(name);
            } else {
                symmetricDifference.add(name);
            }
        }
        return symmetricDifference.size() == 1 ? symmetricDifference.iterator().next() : null;
    }

    /**
     * OpenCalphad's {@code map_halfstep} (see {@link MapTracer#retryWithHalvedSteps}
     * for the full citation and rationale, ported here verbatim so {@code
     * walkLineAlgorithmC1} handles the same "two phases competing to appear/
     * disappear" case production's {@link MapTracer} already does): when more
     * than one phase's stable set differs across the last walk increment, back
     * up to the last converged point and re-walk with a 10%-of-full-step
     * sub-step, up to 3 attempts, until the jump narrows to exactly one changed
     * phase.
     *
     * @return the finer point's result and the single changed phase name, or
     *         {@code null} if 3 sub-step attempts could not narrow the jump
     */
    private static RetriedCrossing retryWithHalvedSteps(
            double lastGoodAxisValue,
            double overshotAxisValue,
            EquilibriumStepper stepper,
            Set<String> lastGoodNames,
            List<GibbsEnergyModel> candidates) {

        double subStep = 0.1 * (overshotAxisValue - lastGoodAxisValue);

        for (int attempt = 1; attempt <= 3; attempt++) {
            double candidateAxisValue = lastGoodAxisValue + attempt * subStep;
            EquilibriumResult candidateResult = stepper.solveAt(candidateAxisValue, candidates);

            if (!candidateResult.isConverged()) {
                continue;
            }

            Set<String> candidateNames = EquilibriumSolveHelper.stablePhaseNames(candidateResult);
            String changed = singleChangedPhase(lastGoodNames, candidateNames);
            if (changed != null) {
                return new RetriedCrossing(candidateAxisValue, candidateResult, changed);
            }
        }
        return null;
    }

    /** Result of a successful {@link #retryWithHalvedSteps} attempt. */
    private static final class RetriedCrossing {
        final double axisValue;
        final EquilibriumResult result;
        final String changedPhase;

        RetriedCrossing(double axisValue, EquilibriumResult result, String changedPhase) {
            this.axisValue = axisValue;
            this.result = result;
            this.changedPhase = changedPhase;
        }
    }

    /**
     * True if the phase forbidden from this line's first step became stable there,
     * meaning the walk direction must flip before continuing.
     *
     * @param line the line being started
     * @param firstStepResult the result of the line's first solved step
     * @return whether the forbidden phase appeared
     */
    private static boolean forbiddenPhaseAppeared(Line line, EquilibriumResult firstStepResult) {
        if (line.forbiddenPhase == null || !firstStepResult.isConverged()) {
            return false;
        }
        return EquilibriumSolveHelper.stablePhaseNames(firstStepResult).contains(line.forbiddenPhase);
    }

    /**
     * Retries a non-convergent point with a shrinking axis step, up to 3 attempts.
     *
     * @param line the line being walked
     * @param lastGoodAxisValue axis value of the last converged point
     * @param failedAxisValue axis value that failed to converge
     * @param stepper the equilibrium solver
     * @param candidates candidate phase models
     * @return a converged result, or {@code null} if all 3 attempts failed
     */
    private static EquilibriumResult retryOnNonConvergence(
            Line line,
            double lastGoodAxisValue,
            double failedAxisValue,
            EquilibriumStepper stepper,
            List<GibbsEnergyModel> candidates) {

        double fullStep = failedAxisValue - lastGoodAxisValue;
        for (int attempt = 1; attempt <= 3; attempt++) {
            double shrunkStep = fullStep / Math.pow(2, attempt);
            double candidateAxisValue = lastGoodAxisValue + shrunkStep;
            EquilibriumResult candidate = stepper.solveAt(candidateAxisValue, candidates);
            if (candidate.isConverged()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Selects which axis to increment on the next iteration. Always returns
     * {@code currentAxisIndex} unchanged; multi-axis switching is not yet implemented.
     *
     * @return the axis index to increment next
     */
    private static int selectAxisWithLargestVariation(
            Line line, int currentAxisIndex, EquilibriumResult previous, EquilibriumResult current) {
        return currentAxisIndex;
    }

    /** Matches {@link StepTracer#DEFAULT_GLOBAL_CHECK_INTERVAL}: the mid-line
     *  global stability check (Sundman 2021 §2.3.3) runs every 10th walked
     *  point, not every point. {@code 0} disables it. */
    static final int DEFAULT_GLOBAL_CHECK_INTERVAL = StepTracer.DEFAULT_GLOBAL_CHECK_INTERVAL;

    /**
     * Walks one pending {@link Line} to completion: axis limit, non-convergence
     * after retry, or a phase-set change handed off to {@code onCrossing}.
     *
     * @param line the pending line, already {@link Line#startWalking() started}
     * @param axis the axis this line walks
     * @param stepper the equilibrium solver
     * @param onCrossing called once the stable phase set changes, to resolve the boundary and
     *        node; build with {@link NodeTerminator#crossingHandlerFor}
     * @param candidates candidate phase models
     */
    static void walkLineAlgorithmC1(
            Line line,
            AxisConfig axis,
            EquilibriumStepper stepper,
            java.util.function.BiConsumer<Line, EquilibriumResult> onCrossing,
            List<GibbsEnergyModel> candidates) {
        walkLineAlgorithmC1(line, axis, stepper, onCrossing, candidates, 0);
    }

    /**
     * As {@link #walkLineAlgorithmC1(Line, AxisConfig, EquilibriumStepper,
     * java.util.function.BiConsumer, List)}, additionally running the mid-line
     * {@code GLOBAL STABILITY CHECK} (Sundman 2021 §2.3.3: "During the line
     * calculations... global checks are made at node points and at regular
     * intervals along a line. If such a check finds that there is another set
     * of phases which represent a more stable equilibrium, the automatic
     * procedure is to abandon this line and suppress it in a subsequent
     * plot.") every {@code globalCheckInterval} ordinary (non-crossing) saved
     * points -- {@code 0} disables it. A failed check terminates and excludes
     * the line (matching {@link StepTracer}/{@link MapTracer}'s {@code
     * GLOBALLY_UNSTABLE} handling), without calling {@code onCrossing}: no
     * node is created for an abandoned line.
     */
    static void walkLineAlgorithmC1(
            Line line,
            AxisConfig axis,
            EquilibriumStepper stepper,
            java.util.function.BiConsumer<Line, EquilibriumResult> onCrossing,
            List<GibbsEnergyModel> candidates,
            int globalCheckInterval) {

        // --- box: "small axis increment" ---
        double axisValue = line.startNode.axisValues[line.initialAxisIndex]
                + Math.abs(axis.step) * Math.signum(line.direction);

        // --- box: "A" (first solve) ---
        EquilibriumResult result = stepper.solveAt(axisValue, candidates);

        // --- box: "forbidden? -- yes --> sign of increment changed --> A" ---
        if (forbiddenPhaseAppeared(line, result)) {
            line.direction = -line.direction;
            axisValue = line.startNode.axisValues[line.initialAxisIndex]
                    + Math.abs(axis.step) * Math.signum(line.direction);
            result = stepper.solveAt(axisValue, candidates);
        }

        double lastGoodAxisValue = line.startNode.axisValues[line.initialAxisIndex];

        while (true) {

            // --- box: "error?" -- Algorithm A non-convergence retry (3x max) ---
            if (!result.isConverged()) {
                EquilibriumResult retried =
                        retryOnNonConvergence(line, lastGoodAxisValue, axisValue, stepper, candidates);
                if (retried == null) {
                    line.terminateAtAxisLimit(); // ">3 times -> line terminated"
                    return;
                }
                result = retried;
            }

            // --- box: "axis limit?" ---
            if (!axis.inBounds(axisValue)) {
                line.terminateAtAxisLimit();
                return;
            }

            // --- box: "phase change? -- yes --> C2" ---
            if (phaseChanged(line, result)) {
                Set<String> currentNames = EquilibriumSolveHelper.stablePhaseNames(result);
                String changedPhase = singleChangedPhase(line.getRunningStableNames(), currentNames);

                double crossingAxisValue = axisValue;
                EquilibriumResult crossingResult = result;

                if (changedPhase == null) {
                    // More than one phase differs -- OC's map_halfstep case
                    // (§3.3's own algorithm never fixes two phases at once);
                    // narrow with a shrinking sub-step before calling C2.
                    RetriedCrossing retried = retryWithHalvedSteps(
                            lastGoodAxisValue, axisValue, stepper, line.getRunningStableNames(), candidates);
                    if (retried == null) {
                        // Matches MapTracer's UNRESOLVED_MULTI_PHASE_CHANGE:
                        // terminate at this point with no exits attached,
                        // rather than letting C2 fail on an ambiguous phase.
                        line.terminateAtAxisLimit();
                        return;
                    }
                    crossingAxisValue = retried.axisValue;
                    crossingResult = retried.result;
                }

                onCrossing.accept(line, crossingResult); // Algorithm C2 + node matching + exit attachment
                return;
            }

            // --- box: "save results" ---
            line.addPoint(result, new double[] { axisValue });

            // --- §2.3.3's mid-line GLOBAL STABILITY CHECK ---
            if (globalCheckInterval > 0 && line.size() % globalCheckInterval == 0
                    && !PhaseDiagramEngine.isGloballyStable(result, candidates)) {
                line.terminateAtAxisLimit();
                line.markExcluded();
                return;
            }

            // --- box: "Select axis with largest variation" (MAP only; no-op for STEP) ---
            int nextAxisIndex = selectAxisWithLargestVariation(line, line.initialAxisIndex, result, result);

            // --- box: "increment axis" -- loop back to "A" ---
            lastGoodAxisValue = axisValue;
            axisValue = axisValue + Math.abs(axis.step) * Math.signum(line.direction);
            result = stepper.solveAt(axisValue, candidates);
        }
    }
}
