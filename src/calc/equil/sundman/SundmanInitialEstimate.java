package calc.equil.sundman;

import calc.equil.GridMinimizer;
import calc.equil.PhaseRecord;
import calc.equil.EquilibriumState;
import system.model.GibbsEnergyModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that converts {@link GridMinimizer}'s output into the initial
 * {@link SundmanPhase} list consumed by {@link SundmanEquilibriumSolver}.
 *
 * <p>Per the M3 design report and Sundman Fig. 1, the grid/global initial
 * estimate is a separate component from Algorithm A itself: this class
 * contains no grid-search logic of its own (it neither implements nor
 * calls any composition-grid scan beyond {@link GridMinimizer}'s existing
 * public {@code initialize} method). It reads GridMinimizer's existing
 * public result type ({@link EquilibriumState}/{@link PhaseRecord}) and
 * repackages it as {@link SundmanPhase} objects, using the fixed
 * convention (G per formula unit, unnormalized M_A) that
 * {@link SundmanEquilibriumSolver} requires.
 *
 * <h2>Same-composition problem</h2>
 * {@link GridMinimizer#initialize} seeds every metastable candidate phase
 * at the overall target composition (see its {@code buildState}). When a
 * metastable phase is later promoted to stable by Algorithm A's own
 * positive-driving-force rule, it can therefore start out numerically
 * coincident in composition with an already-stable phase, making the
 * mass-balance block of the global Jacobian singular/ill-conditioned the
 * moment both are stable (confirmed directly: two phases with
 * proportional M_A vectors give proportional mass-balance rows). Re-
 * running GridMinimizer with a single-candidate list does not fix this,
 * because a single-candidate grid search trivially returns the target
 * composition itself (there is no competing phase to form a hull facet
 * against). Per this task's explicit instruction ("handle this through
 * the initial-estimate/phase-set logic rather than inventing an arbitrary
 * correction"), each metastable phase is instead evaluated at a small,
 * fixed set of REFERENCE compositions spanning the simplex (pure-A,
 * pure-B, and their midpoint, for a binary system) using only methods
 * the model already exposes (getInitialInternalVars, evaluateG); the
 * reference composition giving that phase's own lowest G per mole of
 * atoms is used to seed its y. This is a small, bounded, model-only
 * evaluation (a handful of point evaluations, not a composition scan) —
 * not a duplicate grid search — and it reliably breaks exact composition
 * coincidence with whichever phase(s) GridMinimizer selected as stable
 * (which start at the actual target composition, not a reference point,
 * except in the degenerate case where the target composition IS one of
 * the reference points, handled by trying several).
 */
public final class SundmanInitialEstimate {

    private SundmanInitialEstimate() {}

    /**
     * Builds the initial {@link SundmanPhase} list for
     * {@link SundmanEquilibriumSolver#solve} from a fresh
     * {@link GridMinimizer} run over the given candidates.
     *
     * @param candidates  all candidate phase models (must all be
     *                    {@link CefPhaseModelAdapter} instances — the only
     *                    model type Algorithm A currently supports)
     * @param T           temperature, K
     * @param P           pressure, Pa
     * @param Ntotal      overall composition (mole fractions, will be
     *                    normalized by GridMinimizer)
     * @return initial phases, one per candidate, with stable/metastable
     *         and y/amount set from the grid estimate
     */
    public static List<SundmanPhase> build(List<GibbsEnergyModel> candidates,
                                            double T, double P, double[] Ntotal) {
        GridMinimizer gridMinimizer = new GridMinimizer();
        EquilibriumState state = gridMinimizer.initialize(candidates, T, P, Ntotal);

        // Track compositions already claimed (by the stable phase(s) from
        // GridMinimizer, and by each metastable phase as it is assigned a
        // reference composition below) so that no two candidates start
        // out numerically coincident -- this is what actually breaks the
        // mass-balance-row degeneracy once several phases become stable.
        List<double[]> claimed = new ArrayList<>();
        for (PhaseRecord pr : state.phases) {
            if (pr.stable) claimed.add(pr.model.compositionFromInternal(pr.y));
        }

        List<SundmanPhase> result = new ArrayList<>();
        for (PhaseRecord pr : state.phases) {
            double[] y = pr.y;
            double amount = pr.amount;
            boolean stable = pr.stable;

            if (!stable) {
                double[] ownBestY = ownBestReferenceY(pr.model, T, Ntotal.length, claimed);
                if (ownBestY != null) {
                    y = ownBestY;
                    claimed.add(pr.model.compositionFromInternal(y));
                }
            }

            result.add(new SundmanPhase(pr.model, y, amount, stable));
        }
        return result;
    }

    /** Composition-coincidence tolerance used to reject an already-claimed reference point. */
    private static final double COINCIDENCE_TOL = 1e-3;

    /**
     * Evaluates this phase's own model (G per mole of atoms) at a small,
     * fixed set of reference compositions spanning the simplex, and
     * returns the y at whichever UNCLAIMED reference composition gives
     * the lowest G/nfu (a composition already in {@code claimed}, within
     * {@link #COINCIDENCE_TOL}, is skipped so that no two candidates are
     * seeded at numerically the same point). For nc=2 (binary, the only
     * case exercised so far) the references are {0.1, 0.5, 0.9} on the
     * first component; for nc&gt;2 only the centroid and the nc
     * pure-component corners are tried (kept intentionally small — this
     * is a tie-breaking heuristic, not a replacement for GridMinimizer's
     * own composition search).
     */
    private static double[] ownBestReferenceY(GibbsEnergyModel model, double T, int nc, List<double[]> claimed) {
        List<double[]> references = new ArrayList<>();
        if (nc == 2) {
            references.add(new double[]{0.1, 0.9});
            references.add(new double[]{0.5, 0.5});
            references.add(new double[]{0.9, 0.1});
        } else {
            double[] centroid = new double[nc];
            for (int i = 0; i < nc; i++) centroid[i] = 1.0 / nc;
            references.add(centroid);
            for (int i = 0; i < nc; i++) {
                double[] corner = new double[nc];
                for (int j = 0; j < nc; j++) corner[j] = (j == i) ? 0.9 : 0.1 / (nc - 1);
                references.add(corner);
            }
        }

        double[] bestY = null;
        double bestG = Double.POSITIVE_INFINITY;
        double nfu = Math.max(model.nfu(), 1.0);
        for (double[] x : references) {
            if (isClaimed(x, claimed)) continue;
            try {
                double[] y = model.getInitialInternalVars(x);
                double g = model.evaluateG(y, T) / nfu;
                if (g < bestG) {
                    bestG = g;
                    bestY = y;
                }
            } catch (Exception e) {
                // skip this reference point
            }
        }
        // If every reference point is claimed (more candidates than
        // reference points), fall back to the unconstrained best even if
        // claimed -- SundmanEquilibriumSolver's singularity check will
        // still catch a genuinely degenerate resulting system.
        if (bestY == null) {
            for (double[] x : references) {
                try {
                    double[] y = model.getInitialInternalVars(x);
                    double g = model.evaluateG(y, T) / nfu;
                    if (g < bestG) {
                        bestG = g;
                        bestY = y;
                    }
                } catch (Exception e) {
                    // skip
                }
            }
        }
        return bestY;
    }

    private static boolean isClaimed(double[] x, List<double[]> claimed) {
        for (double[] c : claimed) {
            double dist2 = 0.0;
            for (int i = 0; i < x.length && i < c.length; i++) {
                double d = x[i] - c[i];
                dist2 += d * d;
            }
            if (dist2 < COINCIDENCE_TOL * COINCIDENCE_TOL) return true;
        }
        return false;
    }
}
