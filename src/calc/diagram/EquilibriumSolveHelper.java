package calc.diagram;

import calc.equil.EquilibriumSolverV2;
import calc.equil.EquilibriumState;
import calc.equil.GridMinimizer;
import calc.equil.PhaseRecord;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared per-point solve-and-catch logic used by every tracer in this
 * package that calls a solver once per independent sample point
 * ({@link StepTracer}'s axis walk via {@link EquilibriumSolverV2},
 * {@link CoarseDiagramTracer}'s grid walk via {@link GridMinimizer}): a
 * single bad point must not abort the whole walk, so a {@link
 * RuntimeException} is turned into a non-converged sentinel result
 * instead of propagating.
 */
final class EquilibriumSolveHelper {

    private EquilibriumSolveHelper() {
    }

    /**
     * Calls {@link EquilibriumSolverV2#solve}, returning a non-converged
     * sentinel {@link EquilibriumResult} instead of throwing if the
     * solver fails -- most often when two stable slots of the same
     * candidate converge to near-identical compositions (a known
     * GridMinimizer/updateStablePhaseSet() gap, see
     * EquilibriumSolverV2BaselineTest's own documented skips and
     * CalculationSessionStepTracerTest Section E).
     *
     * <p>Used only by {@link StepTracer}, which needs the full
     * Newton-refined equilibrium (precise crossing bisection, driving
     * forces) -- {@link CoarseDiagramTracer} uses {@link
     * #solveWithGridMinimizerOrSentinel} instead; see that method's
     * Javadoc for why a coarse scatter/dot diagram should not pay for a
     * full multiphase solve at every grid point.
     */
    static EquilibriumResult solveOrSentinel(
            double t,
            double p,
            double[] comp,
            List<GibbsEnergyModel> candidates) {

        try {

            return new EquilibriumSolverV2().solve(t, p, comp, candidates);

        } catch (RuntimeException e) {

            return new EquilibriumResult(
                    t, p, new double[comp.length],
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    false, 0);
        }
    }

    /**
     * Calls {@link GridMinimizer#initialize} directly -- NOT {@link
     * GridMinimizer#solve}, which wraps the same result through {@link
     * EquilibriumResult}/{@code EquilibriumResult.PhaseResult}, types
     * built for {@link EquilibriumSolverV2}'s Newton-iteration
     * bookkeeping (driving force, converged/iteration count) that a
     * coarse diagram has no use for -- and returns just the names of the
     * stable-set {@link PhaseRecord}s, or an empty set if the call fails
     * (e.g. no feasible sample points at this condition, or a degenerate
     * hull search).
     *
     * <p><b>Why {@link CoarseDiagramTracer} uses this instead of {@link
     * EquilibriumSolverV2}</b>: a coarse/scatter diagram's purpose is a
     * quick, dense overview of which phase(s) are stable across a T-x
     * grid -- it does not need Newton-refined phase amounts/compositions
     * at each point, only "which candidate phase(s) sit on the lower
     * hull here." {@link GridMinimizer#initialize} is exactly pycalphad's
     * own {@code calculate()}/{@code starting_point()} step -- sample
     * every candidate phase's site-fraction space, evaluate {@code
     * G(T,P,y)} at each sampled point (no derivative or gradient of G,
     * no chemical potential), and find the lower-hull simplex enclosing
     * the target composition via a single Dantzig-style pivot on that
     * raw (composition, G) point cloud -- with NO Newton iteration
     * anywhere in the call chain (confirmed: {@code GridMinimizer.java}
     * never references {@code EquilibriumSolverV2} except in its own
     * doc comments). This is the same phase-classification idea used by
     * PhaseHull (Dullemond, github.com/dullemond/PhaseHull): a global
     * lower convex hull over sampled (composition, G) points directly
     * gives the stable phase set, without solving a multiphase
     * equilibrium at every point. Using {@code EquilibriumSolverV2} here
     * instead would make a dense grid pay for a full Newton solve
     * (multiple iterations, a global linear system per iteration) at
     * every single dot, for no benefit the coarse diagram's own stated
     * purpose needs -- it only ever reads back the stable phase names.
     */
    static Set<String> stablePhaseNamesViaGridMinimizer(
            double t,
            double p,
            double[] comp,
            List<GibbsEnergyModel> candidates) {

        try {

            EquilibriumState state =
                    new GridMinimizer().initialize(candidates, t, p, comp);

            Set<String> names =
                    new LinkedHashSet<>();

            for (PhaseRecord pr : state.stablePhases()) {
                names.add(pr.phaseName());
            }

            return names;

        } catch (RuntimeException e) {

            return java.util.Collections.emptySet();
        }
    }

    /**
     * A LinkedHashSet collapses two stable slots sharing one phaseName
     * (a miscibility gap) into one entry -- acceptable for step/coarse
     * diagrams; distinguishing them is not required by any current
     * use case.
     */
    static Set<String> stablePhaseNames(
            EquilibriumResult result) {

        Set<String> names =
                new LinkedHashSet<>();

        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            names.add(pr.phaseName);
        }

        return names;
    }

}
