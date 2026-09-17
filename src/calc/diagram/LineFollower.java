package calc.diagram;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.List;

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
                onCrossing.accept(line, result); // Algorithm C2 + node matching + exit attachment
                return;
            }

            // --- box: "save results" ---
            line.addPoint(result, new double[] { axisValue });

            // --- box: "Select axis with largest variation" (MAP only; no-op for STEP) ---
            int nextAxisIndex = selectAxisWithLargestVariation(line, line.initialAxisIndex, result, result);

            // --- box: "increment axis" -- loop back to "A" ---
            lastGoodAxisValue = axisValue;
            axisValue = axisValue + Math.abs(axis.step) * Math.signum(line.direction);
            result = stepper.solveAt(axisValue, candidates);
        }
    }
}
