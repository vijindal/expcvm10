package calc.diagram;
import ui.request.AxisConfig;

import calc.diagram.PhaseDiagramResult.LineSegment;
import calc.diagram.PhaseDiagramResult.NodePoint;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


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

        List<Double> runPropertyValues =
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
                // increment (Sundman's own C1 does this). No converged
                // equilibrium exists at this point, so its molar Gibbs
                // energy is undefined -- record NaN rather than 0 (see
                // buildSegment/#molarGibbsEnergyPerAtom).
                if (runNames == null) {
                    runNames = stablePhaseNames(current);
                }

                runAxisValues.add(v);
                runPropertyValues.add(Double.NaN);
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
                runPropertyValues.add(molarGibbsEnergyPerAtom(current));

                result.addNode(
                        new NodePoint(
                                new double[] { v },
                                new ArrayList<>(currentNames),
                                NodePoint.Type.BOUNDARY));

            } else if (currentNames.equals(runNames)) {

                runAxisValues.add(v);
                runPropertyValues.add(molarGibbsEnergyPerAtom(current));

            } else {

                // Phase set changed between (previousAxisValue, v) --
                // refine the crossing, close out the current run, and
                // start a new one at the crossing point.
                double crossing =
                        bisectCrossing(
                                axis, previousAxisValue, v,
                                runNames, fixedT, fixedP, compOverall,
                                candidates);

                EquilibriumResult crossingResult =
                        solveAt(axis, crossing, fixedT, fixedP, compOverall, candidates);

                runAxisValues.add(crossing);
                runPropertyValues.add(molarGibbsEnergyPerAtom(crossingResult));

                result.addLine(
                        buildSegment(runAxisValues, runNames, runPropertyValues));

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
                runPropertyValues = new ArrayList<>();
                runPropertyValues.add(molarGibbsEnergyPerAtom(crossingResult));
                runPropertyValues.add(molarGibbsEnergyPerAtom(current));
                runNames = currentNames;
            }

            previousAxisValue = v;
        }

        if (runNames != null
                && !runAxisValues.isEmpty()) {

            result.addLine(
                    buildSegment(runAxisValues, runNames, runPropertyValues));

            result.addNode(
                    new NodePoint(
                            new double[] { previousAxisValue },
                            new ArrayList<>(runNames),
                            NodePoint.Type.BOUNDARY));
        }

        return result;
    }

    /**
     * The one scalar equilibrium property {@link StepTracer} reports per
     * point: system Gibbs energy per mole of real atoms ({@link
     * EquilibriumResult#totalGPerAtom()}, "Gm" -- the same quantity {@code
     * EquilibriumReport} already displays for a single-point calculation),
     * the only system-level scalar property this codebase's calculation
     * machinery actually computes today (no separate system-level
     * enthalpy/"HM" aggregation exists -- see {@code CvmGibbs.H(v)}, which
     * is an internal per-phase mixing term, not exposed at this level).
     *
     * @return {@code NaN} if the stable phase set is empty (no real atoms
     *         to divide by -- {@link EquilibriumResult#totalGPerAtom()}
     *         itself throws in that case), rather than propagating the
     *         exception and aborting the whole scan for one bad point
     */
    private double molarGibbsEnergyPerAtom(EquilibriumResult result) {
        if (result.getStablePhases().isEmpty()) {
            return Double.NaN;
        }
        return result.totalGPerAtom();
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
            Set<String> names,
            List<Double> propertyValues) {

        List<double[]> coords =
                new ArrayList<>(axisValues.size());

        for (double v : axisValues) {
            coords.add(new double[] { v });
        }

        double[] properties = new double[propertyValues.size()];
        for (int i = 0; i < properties.length; i++) {
            properties[i] = propertyValues.get(i);
        }

        return new LineSegment(
                coords,
                null,
                new ArrayList<>(names),
                properties);
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
