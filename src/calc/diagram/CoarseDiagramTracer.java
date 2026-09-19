package calc.diagram;
import ui.request.AxisConfig;

import system.model.GibbsEnergyModel;
import ui.result.CoarseDiagramResult;
import ui.result.CoarseDiagramResult.GridPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Coarse binary/ternary phase diagram: samples a 2D grid of conditions,
 * calling {@link EquilibriumSolveHelper#stablePhaseNamesViaGridMinimizer}
 * independently at every point, and records the resulting
 * stable-phase-set per point for scatter/dot rendering.
 *
 * <p><b>Deliberately {@link calc.equil.GridMinimizer#initialize}, not
 * {@link calc.equil.EquilibriumSolverV2}</b>: a coarse diagram only ever
 * needs "which phase(s) sit on the lower hull at this T,x" (the stable
 * phase NAMES), never Newton-refined amounts/compositions/driving
 * forces -- exactly what {@code GridMinimizer.initialize()} already
 * computes as a standalone step (pycalphad's own {@code calculate()}/
 * {@code starting_point()}): sample every candidate phase's site
 * fractions, evaluate raw {@code G(T,P,y)} at each sampled point (no
 * derivative/gradient of G, no chemical potential), and find the
 * lower-hull simplex via a single Dantzig-style pivot on that (X, G)
 * point cloud -- with NO Newton iteration anywhere in the call chain.
 * Calling the full multiphase solver at every one of a dense grid's
 * points would pay for iterative refinement the diagram never uses.
 * This mirrors the phase-classification idea used by PhaseHull
 * (Dullemond, github.com/dullemond/PhaseHull): a global lower-convex-
 * hull search over sampled (composition, G) points directly gives the
 * stable phase set at each point, without solving a multiphase
 * equilibrium there.
 *
 * <p>Unlike {@link StepTracer}, this is not a line-following abstraction
 * -- every grid point is computed and recorded on its own, with no
 * bisection or run-grouping. A single bad point (solver exception, or
 * {@code GridMinimizer} finding no feasible sample points) yields an
 * EMPTY stable-phase-name set and is marked {@code converged=false}
 * rather than aborting the whole grid, which is the primary robustness
 * requirement for a "coarse" diagram: most points render correctly even
 * if a few fail.
 *
 * <p><b>Known, deferred gap</b>: {@code GridMinimizer}'s {@code
 * Hyperplane} pivot search has no tie-breaking for exactly
 * symmetric/degenerate compositions, which is a known source of
 * per-point solver failures. This tracer does not attempt to fix that
 * -- it relies on per-point failure isolation instead, so a ternary
 * grid that happens to sample a symmetric or highly degenerate point
 * simply marks that one dot as unconverged.
 */
public final class CoarseDiagramTracer {

    public CoarseDiagramTracer() {
    }

    /**
     * Samples a 2D grid over two independent axes (composition x
     * temperature is the standard binary case, but any two axes are
     * accepted). Exactly one axis may be COMPOSITION for a true binary
     * (2-element) system; for systems with more elements, the swept
     * component's axis renormalizes the others exactly like
     * {@link StepTracer}'s own single composition axis.
     */
    public CoarseDiagramResult traceBinary(
            AxisConfig axisX,
            AxisConfig axisY,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {
        return traceBinary(axisX, axisY, fixedT, fixedP, compOverall, candidates, null);
    }

    /**
     * As {@link #traceBinary(AxisConfig, AxisConfig, double, double, double[], List)},
     * additionally reporting progress after each completed row (fixed
     * {@code axisY} value, all {@code axisX} values swept) via {@code
     * onProgress}, if non-null. Called from whatever thread runs this
     * method -- callers on a background thread (e.g. a Swing
     * {@code SwingWorker}) are responsible for marshalling the message
     * back to their UI thread themselves.
     */
    public CoarseDiagramResult traceBinary(
            AxisConfig axisX,
            AxisConfig axisY,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            Consumer<String> onProgress) {

        CoarseDiagramResult result =
                new CoarseDiagramResult(
                        new String[] { axisX.name, axisY.name },
                        new double[] { axisX.min, axisY.min },
                        new double[] { axisX.max, axisY.max });

        boolean anyNonConverged =
                false;

        int totalRows =
                (int) Math.floor((axisY.max - axisY.min) / axisY.step + 1.0e-9) + 1;

        int rowIndex =
                0;

        for (double y = axisY.min; y <= axisY.max; y += axisY.step) {

            for (double x = axisX.min; x <= axisX.max; x += axisX.step) {

                double t = fixedT;
                double p = fixedP;
                double[] comp = compOverall.clone();

                switch (axisY.type) {
                    case TEMPERATURE: t = y; break;
                    case PRESSURE: p = y; break;
                    case COMPOSITION: comp = StepTracer.applyCompositionAxis(axisY, y, comp); break;
                    default: throw new IllegalStateException("Unhandled axis type: " + axisY.type);
                }

                switch (axisX.type) {
                    case TEMPERATURE: t = x; break;
                    case PRESSURE: p = x; break;
                    case COMPOSITION: comp = StepTracer.applyCompositionAxis(axisX, x, comp); break;
                    default: throw new IllegalStateException("Unhandled axis type: " + axisX.type);
                }

                Set<String> names =
                        EquilibriumSolveHelper.stablePhaseNamesViaGridMinimizer(t, p, comp, candidates);

                boolean pointConverged =
                        !names.isEmpty();

                if (!pointConverged) {
                    anyNonConverged = true;
                }

                result.addPoint(
                        new GridPoint(x, y, new ArrayList<>(names), pointConverged));
            }

            rowIndex++;

            if (onProgress != null) {
                int percent = (int) Math.round(100.0 * rowIndex / totalRows);
                onProgress.accept(String.format(
                        "Row %d/%d (%s=%.4g) -- %d%% -- %d points sampled",
                        rowIndex, totalRows, axisY.name, y, percent, result.getPoints().size()));
            }
        }

        if (anyNonConverged) {
            result.setComplete(false);
            result.setMessage("One or more grid points did not converge.");
        }

        return result;
    }

    /**
     * Samples a 2D grid over two composition axes at fixed T/P -- the
     * standard isothermal ternary diagram convention. The third (and,
     * for &gt;3-element systems, every further) component's fraction is
     * implied by the remaining mass fraction, distributed among the
     * non-swept components proportional to their existing ratios in
     * {@code compOverall} (the same convention {@link StepTracer} uses
     * for its single composition axis, generalized to N-2 other
     * components). Grid points where the implied remainder would be
     * negative (outside the composition simplex) are skipped entirely --
     * not sampled, not marked failed, simply absent from the result.
     */
    public CoarseDiagramResult traceTernary(
            AxisConfig axisCompI,
            AxisConfig axisCompJ,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {
        return traceTernary(axisCompI, axisCompJ, fixedT, fixedP, compOverall, candidates, null);
    }

    /**
     * As {@link #traceTernary(AxisConfig, AxisConfig, double, double, double[], List)},
     * additionally reporting progress after each completed row (fixed
     * {@code axisCompJ} value, all {@code axisCompI} values swept) via
     * {@code onProgress}, if non-null.
     */
    public CoarseDiagramResult traceTernary(
            AxisConfig axisCompI,
            AxisConfig axisCompJ,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            Consumer<String> onProgress) {

        CoarseDiagramResult result =
                new CoarseDiagramResult(
                        new String[] { axisCompI.name, axisCompJ.name },
                        new double[] { axisCompI.min, axisCompJ.min },
                        new double[] { axisCompI.max, axisCompJ.max });

        boolean anyNonConverged =
                false;

        double simplexEpsilon =
                -1.0e-9;

        int totalRows =
                (int) Math.floor((axisCompJ.max - axisCompJ.min) / axisCompJ.step + 1.0e-9) + 1;

        int rowIndex =
                0;

        for (double xj = axisCompJ.min; xj <= axisCompJ.max; xj += axisCompJ.step) {

            for (double xi = axisCompI.min; xi <= axisCompI.max; xi += axisCompI.step) {

                double remainder =
                        1.0 - xi - xj;

                if (remainder < simplexEpsilon) {
                    continue;
                }

                remainder =
                        Math.max(0.0, remainder);

                double[] comp =
                        distributeTernaryRemainder(
                                axisCompI.componentIndex, xi,
                                axisCompJ.componentIndex, xj,
                                remainder, compOverall);

                Set<String> names =
                        EquilibriumSolveHelper.stablePhaseNamesViaGridMinimizer(fixedT, fixedP, comp, candidates);

                boolean pointConverged =
                        !names.isEmpty();

                if (!pointConverged) {
                    anyNonConverged = true;
                }

                result.addPoint(
                        new GridPoint(xi, xj, new ArrayList<>(names), pointConverged));
            }

            rowIndex++;

            if (onProgress != null) {
                int percent = (int) Math.round(100.0 * rowIndex / totalRows);
                onProgress.accept(String.format(
                        "Row %d/%d (%s=%.4g) -- %d%% -- %d points sampled",
                        rowIndex, totalRows, axisCompJ.name, xj, percent, result.getPoints().size()));
            }
        }

        if (anyNonConverged) {
            result.setComplete(false);
            result.setMessage("One or more grid points did not converge.");
        }

        return result;
    }

    /**
     * Sets components {@code indexI} and {@code indexJ} to {@code xi}/
     * {@code xj}, then distributes {@code remainder} among every OTHER
     * component proportional to its share of {@code compOverall}'s
     * existing "other components" total -- for a strict 3-element
     * ternary this is trivially "the one remaining component gets
     * {@code remainder} directly".
     */
    private double[] distributeTernaryRemainder(
            int indexI,
            double xi,
            int indexJ,
            double xj,
            double remainder,
            double[] compOverall) {

        double[] comp =
                compOverall.clone();

        double otherSum =
                0.0;

        for (int a = 0; a < comp.length; a++) {
            if (a != indexI && a != indexJ) {
                otherSum += comp[a];
            }
        }

        int otherCount =
                comp.length - 2;

        for (int a = 0; a < comp.length; a++) {

            if (a == indexI) {
                comp[a] = xi;
            } else if (a == indexJ) {
                comp[a] = xj;
            } else if (otherSum > 0.0) {
                comp[a] = comp[a] / otherSum * remainder;
            } else if (otherCount > 0) {
                comp[a] = remainder / otherCount;
            }
        }

        return comp;
    }
}
