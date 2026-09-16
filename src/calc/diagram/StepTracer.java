package calc.diagram;

import calc.diagram.PhaseDiagramResult.LineSegment;
import calc.diagram.PhaseDiagramResult.NodePoint;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// walkOneSegment/SegmentResult additions below support StepDiagramTracer's
// Node/NodeRegistry-backed C1 drain loop (docs/roadmap_phase_diagrams.md's
// STEP branch) without changing trace()'s existing behavior or callers.

/**
 * Sundman Algorithm B's STEP branch (2021 Calphad 75, Section 3.2): walk a
 * single axis condition in fixed increments, calling Algorithm A
 * ({@link EquilibriumSolverV2#solve}) at every point, and record where the
 * stable phase set changes.
 *
 * <p>Unlike full ZPF mapping (Algorithm C1+C2+D, not implemented here),
 * a step calculation never fixes a phase at zero amount and never branches
 * into multiple exits -- each phase-set change simply continues the walk
 * in the same direction with the new stable set. Locating the crossing
 * point is done by black-box bisection on {@link EquilibriumSolverV2#solve}
 * (calling it repeatedly at bracketing axis values) rather than Sundman's
 * own exact method (fixing the changed phase's amount at zero as a Lagrange
 * condition), since {@code EquilibriumSolverV2} has no such condition today
 * and adding one is a separate, larger piece of work. This is adequate for
 * step's own purpose -- Sundman's own step diagrams (e.g. Fig. 14) are
 * about how a property varies, not pinpointing a transition to high
 * precision.
 *
 * <p>Every call to {@link EquilibriumSolverV2#solve} starts from scratch
 * (no warm start) -- accepted as a performance cost for this first version.
 */
public final class StepTracer {

    private static final int MAX_BISECTION_ITERATIONS = 20;
    private static final double BISECTION_RELATIVE_TOLERANCE = 1.0e-4;

    public StepTracer() {
    }

    /** Why a call to {@link #walkOneSegment} stopped -- mirrors {@link MapTracer.SegmentEnd},
     *  minus the ZPF-specific cases ({@code INVARIANT}, {@code UNRESOLVED_MULTI_PHASE_CHANGE})
     *  that only apply when a composition axis is released; STEP never releases one. */
    public enum SegmentEnd {
        /** Reached {@code axis.max} or {@code axis.min} without a stable-set change. */
        AXIS_LIMIT,
        /** A non-convergent point was hit; the segment stops there (matches {@link #trace}'s handling). */
        NON_CONVERGENT,
        /** The stable phase set changed; the crossing was located by black-box bisection. */
        CROSSING,
        /**
         * The mid-line {@link PhaseDiagramEngine#isGloballyStable} check
         * (§2.3.3's "regular intervals along a line") failed at an
         * ordinary, non-crossing point -- the segment stops there, one
         * point short of where it would otherwise have continued.
         */
        GLOBALLY_UNSTABLE
    }

    /** Matches OC's {@code globalcheckinterval} default ({@code smp2A.F90}): the mid-line
     *  global stability check runs every 10th walked point, not every point. */
    static final int DEFAULT_GLOBAL_CHECK_INTERVAL = 10;

    /**
     * Result of one {@link #walkOneSegment} call -- the STEP analogue of
     * {@link MapTracer.SegmentResult}: every point solved along the
     * segment (in order), why it ended, and, for a {@code CROSSING} end,
     * the bisected crossing equilibrium and the new stable phase set.
     */
    public static final class SegmentResult {
        public final List<EquilibriumResult> points;
        public final List<Double> axisValues;
        public final SegmentEnd end;
        public final double endAxisValue;
        /** New stable phase set after the crossing; {@code null} unless {@code end == CROSSING}. */
        public final Set<String> newStableNames;
        /** The bisected crossing equilibrium; {@code null} unless {@code end == CROSSING}. */
        public final EquilibriumResult crossingResult;
        /**
         * The last raw (un-bisected) grid-point equilibrium with the NEW
         * stable set -- what a caller resuming the next segment should
         * treat as that segment's own starting result, matching {@link
         * MapTracer.SegmentResult#lastResult}'s same role.
         */
        public final EquilibriumResult lastResult;

        SegmentResult(List<EquilibriumResult> points, List<Double> axisValues, SegmentEnd end,
                      double endAxisValue, Set<String> newStableNames,
                      EquilibriumResult crossingResult, EquilibriumResult lastResult) {
            this.points = points;
            this.axisValues = axisValues;
            this.end = end;
            this.endAxisValue = endAxisValue;
            this.newStableNames = newStableNames;
            this.crossingResult = crossingResult;
            this.lastResult = lastResult;
        }
    }

    /**
     * Walks {@code axis} starting just after {@code startAxisValue} (the
     * first point solved is {@code startAxisValue + axis.step}) until
     * either an axis limit is reached or the stable phase set changes --
     * the resumable primitive behind {@link #trace}, and the one {@link
     * StepDiagramTracer}'s C1 drain loop calls once per pending {@link
     * Line}. Unlike {@link MapTracer#walkOneSegment}, every walked point
     * (including interior, non-crossing points) is kept in {@link
     * SegmentResult#points} -- STEP's {@link Line} needs the full
     * per-point {@link EquilibriumResult} history, not just axis coordinates.
     *
     * @param startAxisValue   the axis value of the ALREADY-KNOWN starting
     *                         equilibrium (not re-solved here)
     * @param startStableNames stable phase names at {@code startAxisValue}
     * @return where and why the segment stopped, and every point walked
     */
    public SegmentResult walkOneSegment(
            double startAxisValue,
            Set<String> startStableNames,
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {
        return walkOneSegment(startAxisValue, startStableNames, axis, fixedT, fixedP,
                compOverall, candidates, 0);
    }

    /**
     * As {@link #walkOneSegment(double, Set, AxisConfig, double, double,
     * double[], List)}, additionally running the mid-line {@code GLOBAL
     * STABILITY CHECK} (§2.3.3) every {@code globalCheckInterval} ordinary
     * (non-crossing) points -- {@code 0} disables it, matching the
     * 7-arg overload's behavior. See {@link
     * #DEFAULT_GLOBAL_CHECK_INTERVAL} for OC's own default.
     */
    public SegmentResult walkOneSegment(
            double startAxisValue,
            Set<String> startStableNames,
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            int globalCheckInterval) {

        List<EquilibriumResult> points = new ArrayList<>();
        List<Double> axisValues = new ArrayList<>();

        Set<String> runNames = startStableNames;
        double previousAxisValue = startAxisValue;

        for (double v = startAxisValue + axis.step;
             axis.step > 0 ? v <= axis.max : v >= axis.min;
             v += axis.step) {

            EquilibriumResult current = solveAt(axis, v, fixedT, fixedP, compOverall, candidates);

            if (!current.isConverged()) {
                points.add(current);
                axisValues.add(v);
                return new SegmentResult(points, axisValues, SegmentEnd.NON_CONVERGENT,
                        v, null, null, current);
            }

            Set<String> currentNames = stablePhaseNames(current);

            if (currentNames.equals(runNames)) {
                points.add(current);
                axisValues.add(v);
                previousAxisValue = v;

                if (globalCheckInterval > 0 && points.size() % globalCheckInterval == 0
                        && !PhaseDiagramEngine.isGloballyStable(current, candidates)) {
                    return new SegmentResult(points, axisValues, SegmentEnd.GLOBALLY_UNSTABLE,
                            v, null, null, current);
                }
                continue;
            }

            double crossing = bisectCrossing(
                    axis, previousAxisValue, v, runNames, fixedT, fixedP, compOverall, candidates);
            EquilibriumResult crossingResult =
                    solveAt(axis, crossing, fixedT, fixedP, compOverall, candidates);

            points.add(crossingResult);
            axisValues.add(crossing);

            return new SegmentResult(points, axisValues, SegmentEnd.CROSSING,
                    crossing, currentNames, crossingResult, current);
        }

        return new SegmentResult(points, axisValues, SegmentEnd.AXIS_LIMIT,
                previousAxisValue, null, null,
                points.isEmpty() ? null : points.get(points.size() - 1));
    }

    /**
     * Walks {@code axis} from {@code axis.min} to {@code axis.max} in
     * increments of {@code axis.step}, at the given fixed condition,
     * recording each maximal run of constant stable-phase-set as one
     * {@link LineSegment} and each detected phase-set change as one
     * {@link NodePoint}.
     *
     * @param axis        the axis to walk (its {@code type} selects which
     *                    of {@code fixedT}/{@code fixedP}/{@code compOverall}
     *                    is overridden by the swept value)
     * @param fixedT      temperature to use when {@code axis.type != TEMPERATURE}
     * @param fixedP      pressure to use when {@code axis.type != PRESSURE}
     * @param compOverall overall composition to use when
     *                    {@code axis.type != COMPOSITION}
     * @param candidates  candidate phase models
     * @return the sampled step result
     */
    public PhaseDiagramResult trace(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        PhaseDiagramResult result =
                new PhaseDiagramResult(
                        new String[] { axis.name },
                        new double[] { axis.min },
                        new double[] { axis.max });

        List<Double> runAxisValues =
                new ArrayList<>();

        Set<String> runNames =
                null;

        double previousAxisValue =
                Double.NaN;

        for (double v = axis.min;
             v <= axis.max;
             v += axis.step) {

            EquilibriumResult current =
                    solveAt(axis, v, fixedT, fixedP, compOverall, candidates);

            if (!current.isConverged()) {

                result.setComplete(false);
                result.setMessage(
                        "Non-convergent point at " + axis.name + "=" + v);

                // Treat as a continuation of the current run rather than a
                // phase-set change -- out of scope to retry with a smaller
                // increment (Sundman's own C1 does this).
                if (runNames == null) {
                    runNames = stablePhaseNames(current);
                }

                runAxisValues.add(v);
                previousAxisValue = v;
                continue;
            }

            Set<String> currentNames =
                    stablePhaseNames(current);

            if (runNames == null) {

                // First point: start the first run and record the low
                // boundary node.
                runNames = currentNames;
                runAxisValues.add(v);

                result.addNode(
                        new NodePoint(
                                new double[] { v },
                                new ArrayList<>(currentNames),
                                NodePoint.Type.BOUNDARY));

            } else if (currentNames.equals(runNames)) {

                runAxisValues.add(v);

            } else {

                // Phase set changed between (previousAxisValue, v) --
                // refine the crossing, close out the current run, and
                // start a new one at the crossing point.
                double crossing =
                        bisectCrossing(
                                axis, previousAxisValue, v,
                                runNames, fixedT, fixedP, compOverall,
                                candidates);

                runAxisValues.add(crossing);

                result.addLine(
                        buildSegment(runAxisValues, runNames));

                Set<String> unionAtCrossing =
                        new LinkedHashSet<>(runNames);

                unionAtCrossing.addAll(currentNames);

                result.addNode(
                        new NodePoint(
                                new double[] { crossing },
                                new ArrayList<>(unionAtCrossing),
                                NodePoint.Type.CROSSING));

                runAxisValues = new ArrayList<>();
                runAxisValues.add(crossing);
                runAxisValues.add(v);
                runNames = currentNames;
            }

            previousAxisValue = v;
        }

        if (runNames != null
                && !runAxisValues.isEmpty()) {

            result.addLine(
                    buildSegment(runAxisValues, runNames));

            result.addNode(
                    new NodePoint(
                            new double[] { previousAxisValue },
                            new ArrayList<>(runNames),
                            NodePoint.Type.BOUNDARY));
        }

        return result;
    }

    /**
     * Bisects between {@code lowValue} (known to have stable set
     * {@code lowNames}) and {@code highValue} (known to have a different
     * stable set) to refine the crossing point.
     */
    private double bisectCrossing(
            AxisConfig axis,
            double lowValue,
            double highValue,
            Set<String> lowNames,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        double a =
                lowValue;

        double b =
                highValue;

        double tolerance =
                Math.abs(axis.step) * BISECTION_RELATIVE_TOLERANCE;

        for (int iter = 0;
             iter < MAX_BISECTION_ITERATIONS
                     && Math.abs(b - a) > tolerance;
             iter++) {

            double mid =
                    (a + b) / 2.0;

            EquilibriumResult midResult =
                    solveAt(axis, mid, fixedT, fixedP, compOverall, candidates);

            if (!midResult.isConverged()) {
                // Cannot refine further at a non-convergent midpoint --
                // stop bisecting and report the current bracket midpoint.
                break;
            }

            Set<String> midNames =
                    stablePhaseNames(midResult);

            if (midNames.equals(lowNames)) {
                a = mid;
            } else {
                b = mid;
            }
        }

        return (a + b) / 2.0;
    }

    private LineSegment buildSegment(
            List<Double> axisValues,
            Set<String> names) {

        List<double[]> coords =
                new ArrayList<>(axisValues.size());

        for (double v : axisValues) {
            coords.add(new double[] { v });
        }

        return new LineSegment(
                coords,
                null,
                new ArrayList<>(names));
    }

    private Set<String> stablePhaseNames(
            EquilibriumResult result) {

        return EquilibriumSolveHelper.stablePhaseNames(result);
    }

    private EquilibriumResult solveAt(
            AxisConfig axis,
            double axisValue,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        double t =
                fixedT;

        double p =
                fixedP;

        double[] comp =
                compOverall.clone();

        switch (axis.type) {

            case TEMPERATURE:
                t = axisValue;
                break;

            case PRESSURE:
                p = axisValue;
                break;

            case COMPOSITION:
                comp = applyCompositionAxis(axis, axisValue, compOverall);
                break;

            default:
                throw new IllegalStateException(
                        "Unhandled axis type: " + axis.type);
        }

        return EquilibriumSolveHelper.solveOrSentinel(t, p, comp, candidates);
    }

    /**
     * Sets {@code axis.componentIndex} to {@code axisValue}, then
     * rescales every OTHER component so the vector still sums to 1 --
     * fixing the swept component alone (leaving the rest at
     * {@code compOverall}'s original values) would silently pass an
     * invalid, non-normalized composition to the solver (e.g.
     * {1.0, 0.05} instead of {0.95, 0.05}), which can cause spurious
     * solver instability at some points and not others depending on how
     * far off-normalization the result lands. Shared with
     * {@link CoarseDiagramTracer}'s binary-grid case, which applies this
     * same single-composition-axis logic to one of its two axes.
     */
    static double[] applyCompositionAxis(
            AxisConfig axis,
            double axisValue,
            double[] compOverall) {

        double[] comp =
                compOverall.clone();

        double remainder =
                1.0 - axisValue;

        double otherSum =
                0.0;

        for (int i = 0; i < comp.length; i++) {
            if (i != axis.componentIndex) {
                otherSum += comp[i];
            }
        }

        for (int i = 0; i < comp.length; i++) {

            if (i == axis.componentIndex) {
                comp[i] = axisValue;
            } else if (otherSum > 0.0) {
                comp[i] = comp[i] / otherSum * remainder;
            } else {
                // All other components were zero -- distribute
                // the remainder evenly among them.
                comp[i] = remainder / (comp.length - 1);
            }
        }

        return comp;
    }
}
