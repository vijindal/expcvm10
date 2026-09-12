package calc.equil;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Grid minimizer -- initial phase set estimator, ported to sample and
 * search exactly the way pycalphad's {@code calculate()} /
 * {@code starting_point()} / {@code lower_convex_hull()} do.
 *
 * <p>pycalphad's global-minimization starting point (see
 * {@code pycalphad.core.calculate._sample_phase_constitution},
 * {@code pycalphad.core.starting_point.starting_point}, and
 * {@code pycalphad.core.hyperplane.hyperplane}) works directly in the
 * internal degrees of freedom (site fractions), not in composition space:
 *
 * <ol>
 *   <li>Sample the site-fraction space of every candidate phase: exact
 *       endmembers, a fixed grid of points along every endmember-pair edge,
 *       and {@code pdof * dof} interior points from a scrambled Halton
 *       sequence transformed onto each sublattice's simplex
 *       ({@code point_sample}).</li>
 *   <li>Evaluate G (and hence the per-mole-atom energy) and the overall
 *       composition X at every sampled point of every phase.</li>
 *   <li>Find the lower-hull simplex (an N-component tangent hyperplane)
 *       enclosing the requested overall composition, via a Dantzig-style
 *       simplex pivot directly on the combined (X, G/atom) point cloud
 *       across all candidate phases -- not a low-dimension-specific
 *       geometric convex hull, so this generalizes to any number of
 *       components.</li>
 *   <li>The simplex's vertices give the initial stable phase set, their
 *       site fractions, and (via the returned barycentric fractions) their
 *       initial amounts.</li>
 * </ol>
 *
 * <p>This class ports every stage directly: {@link #sampleSiteFractions}
 * mirrors {@code _sample_phase_constitution} ({@link Halton#pointSample} is
 * a direct port of {@code pycalphad.core.utils.point_sample}, itself built
 * on {@link Halton#generate}, a direct port of
 * {@code pycalphad.core.halton.halton}); {@link #initialize} mirrors
 * {@code starting_point()}'s call into the hull, dispatching to
 * {@link Hyperplane#solve}, a direct port of
 * {@code pycalphad.core.hyperplane.hyperplane} verified against pycalphad
 * to ~1e-11 J (see {@code HyperplanePortTest}).
 */
public class GridMinimizer {

    private static final Logger LOG =
        Logger.getLogger(GridMinimizer.class.getName());

    /**
     * Number of Halton-sampled interior points per degree of freedom,
     * matching pycalphad's {@code calculate(..., pdens=...)} default.
     */
    private static final int PDENS = 2000;

    /** Floor for site fractions to avoid log(0)/division singularities. */
    private static final double MIN_SITE_FRACTION = 1.0e-14;

    // ─────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────

    /**
     * Estimate the initial stable phase set and compositions.
     *
     * @param candidates  all candidate phase models
     * @param T           temperature (K)
     * @param P           pressure (Pa)
     * @param xOverall    overall mole fractions, length nc, must sum to 1
     * @return            EquilibriumState ready for EquilibriumSolver
     */
    public EquilibriumState initialize(List<GibbsEnergyModel> candidates,
                                       double T, double P,
                                       double[] xOverall) {
        int nc = xOverall.length;
        int np = candidates.size();

        // Steps 1-2: sample every candidate phase's internal degrees of
        // freedom directly (site fractions), evaluate G/atom and X at
        // every sampled point, and build the combined lower envelope
        // (minimum G/atom per point, tagged with which phase achieved it).
        List<double[]> envX     = new ArrayList<>();  // overall composition x at each point
        List<Double>   envG     = new ArrayList<>();  // G per mole real atom (Sundman's M^alpha)
        List<Integer>  envPhase = new ArrayList<>();  // candidate index
        List<double[]> envY     = new ArrayList<>();  // site fractions y at each point

        for (int ip = 0; ip < np; ip++) {
            GibbsEnergyModel m = candidates.get(ip);

            double[][] points = sampleSiteFractions(m);

            for (double[] y : points) {

                double G;
                double[] x;
                try {
                    /*
                     * Normalize by totalMoles(y) (Sundman's M^alpha, Eq.
                     * 6), the REAL atom count at this constitution -- NOT
                     * m.nfu() (the constitution-independent nominal sum
                     * of site ratios). For a phase with a vacancy
                     * sublattice (e.g. BCC_A2's (V,Zr)1(Va)3), nfu()
                     * counts the vacancy sites too (nfu=4) even though
                     * they hold no atoms (real count=1); dividing by
                     * nfu() here previously produced a badly wrong G/atom
                     * for every BCC_A2 sample point, silently biasing the
                     * hull search away from BCC_A2 entirely. This matches
                     * pycalphad's own per-mole-atom GM convention exactly
                     * (GM = G / total real atoms, never / raw site-ratio
                     * sum) -- see GibbsEnergyModel.totalMoles()'s javadoc.
                     */
                    double totalAtoms = m.totalMoles(y);
                    if (!(totalAtoms > 0.0)) continue;

                    G = m.G(T, P, y) / totalAtoms;
                    x = m.compositionFromInternal(y);
                } catch (Exception e) {
                    continue;
                }

                if (!Double.isFinite(G) || x == null) continue;

                envX.add(x);
                envG.add(G);
                envPhase.add(ip);
                envY.add(y);
            }
        }

        int ng = envG.size();
        if (ng == 0) {
            throw new IllegalStateException(
                    "GridMinimizer produced no feasible sample points for "
                    + "any candidate phase.");
        }

        double[]   G     = new double[ng];
        int[]      phase = new int[ng];
        double[][] X     = new double[ng][];
        double[][] Y     = new double[ng][];
        for (int i = 0; i < ng; i++) {
            G[i]     = envG.get(i);
            phase[i] = envPhase.get(i);
            X[i]     = envX.get(i);
            Y[i]     = envY.get(i);
        }

        // Steps 3-4: find the lower-hull simplex enclosing xOverall via
        // Hyperplane's direct port of pycalphad's simplex-pivot search.
        // Independent targets are components 0..nc-2 (the last component's
        // fraction is implied by summing to 1), matching Hyperplane.solve's
        // contract.
        double[] targetMoleFractions = new double[nc - 1];
        System.arraycopy(xOverall, 0, targetMoleFractions, 0, nc - 1);

        Hyperplane.Result hyperplaneResult =
                Hyperplane.solve(X, G, targetMoleFractions);

        FacetResult facet = toFacetResult(hyperplaneResult, phase, Y, X);

        // Step 5: build EquilibriumState.
        return buildState(candidates, facet, xOverall, np, T, P);
    }

    /**
     * Adapts a {@link Hyperplane.Result} (indices into the flattened
     * sample arrays) into the {@link FacetResult} shape
     * {@link #buildState} consumes, dropping any simplex vertex whose
     * barycentric fraction is (numerically) zero or negative -- the
     * "Gibbs phase rule" trim pycalphad itself applies to its own
     * {@code result_fractions}/{@code result_simplex} outputs.
     */
    private FacetResult toFacetResult(Hyperplane.Result hr,
                                       int[] phase, double[][] Y, double[][] X) {

        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < hr.fractions.length; i++) {
            if (hr.fractions[i] > 1e-10) {
                keep.add(i);
            }
        }
        if (keep.isEmpty()) {
            // Degenerate: fall back to the single largest-fraction vertex.
            int best = 0;
            for (int i = 1; i < hr.fractions.length; i++) {
                if (hr.fractions[i] > hr.fractions[best]) best = i;
            }
            keep.add(best);
        }

        FacetResult fr = new FacetResult();
        fr.phaseIdx = new int[keep.size()];
        fr.yAtVertex = new double[keep.size()][];
        fr.xAtVertex = new double[keep.size()][];
        fr.amount = new double[keep.size()];

        for (int k = 0; k < keep.size(); k++) {
            int i = keep.get(k);
            int pointIdx = hr.simplex[i];
            fr.phaseIdx[k] = phase[pointIdx];
            fr.yAtVertex[k] = Y[pointIdx];
            fr.xAtVertex[k] = X[pointIdx];
            fr.amount[k] = hr.fractions[i];
        }

        return fr;
    }

    /**
     * Runs the initializer as a standalone calculation and returns its
     * result in the same {@link EquilibriumResult} shape
     * {@link EquilibriumSolverV2#solve} produces, so a caller (e.g.
     * {@code CalculationSession}) can dispatch to either one identically.
     *
     * <p>Mirrors pycalphad's own separation between {@code calculate()} /
     * {@code starting_point()} (grid sampling and convex-hull starting
     * point -- independently callable and testable on their own) and
     * {@code equilibrium()} (which additionally runs the Newton solve):
     * the initializer is a genuinely separate calculation there, not
     * merely an internal step of the full solve, and this method gives it
     * the same standing here.
     *
     * <p>The returned result has {@code converged=false} and
     * {@code iterations=0} -- it is a starting point, not a converged
     * equilibrium.
     *
     * @param candidates  all candidate phase models
     * @param T           temperature (K)
     * @param P           pressure (Pa)
     * @param xOverall    overall mole fractions, length nc, must sum to 1
     * @return            the initial phase set/constitutions as an
     *                     unconverged {@link EquilibriumResult}
     */
    public EquilibriumResult solve(List<GibbsEnergyModel> candidates,
                                    double T, double P,
                                    double[] xOverall) {

        EquilibriumState state = initialize(candidates, T, P, xOverall);

        List<EquilibriumResult.PhaseResult> stableResults = new ArrayList<>();
        for (PhaseRecord pr : state.stablePhases()) {
            stableResults.add(new EquilibriumResult.PhaseResult(
                    pr.phaseName(), pr.modelType(), pr.amount, pr.x, pr.y,
                    pr.G, pr.drivingForce));
        }

        List<EquilibriumResult.PhaseResult> metastableResults = new ArrayList<>();
        for (PhaseRecord pr : state.metastablePhases()) {
            metastableResults.add(new EquilibriumResult.PhaseResult(
                    pr.phaseName(), pr.modelType(), pr.amount, pr.x, pr.y,
                    pr.G, pr.drivingForce));
        }

        return new EquilibriumResult(
                T, P, state.mu, stableResults, metastableResults,
                false, 0);
    }

    // ─────────────────────────────────────────────────────────────────
    // Steps 1-2: Direct internal-degrees-of-freedom sampling
    //
    // Ports pycalphad.core.calculate._sample_phase_constitution
    // (endmembers + fixed-grid edges + Halton interior sampling), minus
    // the ionic-liquid charge-balance branch, which this project's models
    // do not use.
    // ─────────────────────────────────────────────────────────────────

    /**
     * Number of linear edge points sampled between each pair of
     * endmembers, matching pycalphad's {@code fixed_grid} pass in
     * {@code calculate()} (which uses {@code pdens} there too).
     */
    private static final int EDGE_POINTS = PDENS;

    /**
     * Samples the site-fraction space of one candidate phase: exact
     * endmembers, a fixed linear grid along every endmember-pair edge, and
     * Halton-sampled interior points -- the non-charge-constrained,
     * non-linearly-constrained path of pycalphad's
     * {@code _sample_phase_constitution}.
     */
    double[][] sampleSiteFractions(GibbsEnergyModel m) {

        int ns = m.numSublattices();
        int[] ncSub = m.constituentsPerSublattice();
        int[] offsets = m.offsets();
        int nip = m.numSiteVars();

        List<double[]> points = new ArrayList<>();

        // --- Endmembers: full Cartesian product of one-hot vectors per
        // sublattice (pycalphad's endmember_matrix). ---
        List<double[]> endmembers = buildEndmemberMatrix(ns, ncSub, offsets, nip);
        points.addAll(endmembers);

        // --- Fixed grid: linear interpolation along every endmember pair
        // edge (pycalphad samples EDGE_POINTS points per edge). ---
        int numEndmembers = endmembers.size();
        if (numEndmembers >= 2) {
            for (int a = 0; a < numEndmembers; a++) {
                for (int b = a + 1; b < numEndmembers; b++) {
                    double[] emA = endmembers.get(a);
                    double[] emB = endmembers.get(b);
                    for (int k = 0; k < EDGE_POINTS; k++) {
                        double lam = (EDGE_POINTS == 1)
                                ? 0.5
                                : (double) k / (EDGE_POINTS - 1);
                        double[] p = new double[nip];
                        for (int i = 0; i < nip; i++) {
                            p[i] = emA[i] * lam + emB[i] * (1.0 - lam);
                        }
                        points.add(p);
                    }
                }
            }
        }

        // --- Interior Halton sampling (pycalphad's point_sample), only
        // when the phase actually has internal degrees of freedom. ---
        if (nip > ns) {
            double[][] interior = Halton.pointSample(ncSub, PDENS);
            for (double[] p : interior) {
                points.add(p);
            }
        }

        return points.toArray(new double[0][]);
    }

    /**
     * Full Cartesian product of one-hot constituent vectors per
     * sublattice, with zero entries floored to {@link #MIN_SITE_FRACTION}
     * and each sublattice block renormalized to sum to 1 -- a direct port
     * of pycalphad's {@code endmember_matrix} (without the vacancy-index
     * exclusion, which this project's phase models do not require here).
     */
    private List<double[]> buildEndmemberMatrix(int ns, int[] ncSub,
                                                 int[] offsets, int nip) {

        List<int[]> combos = new ArrayList<>();
        cartesianProduct(ncSub, 0, new int[ns], combos);

        List<double[]> result = new ArrayList<>(combos.size());

        for (int[] combo : combos) {
            double[] y = new double[nip];
            for (int i = 0; i < nip; i++) {
                y[i] = MIN_SITE_FRACTION;
            }
            for (int s = 0; s < ns; s++) {
                y[offsets[s] + combo[s]] = 1.0;
            }
            // Renormalize each sublattice block to sum to 1.
            for (int s = 0; s < ns; s++) {
                double sum = 0.0;
                for (int i = 0; i < ncSub[s]; i++) {
                    sum += y[offsets[s] + i];
                }
                for (int i = 0; i < ncSub[s]; i++) {
                    y[offsets[s] + i] /= sum;
                }
            }
            result.add(y);
        }

        return result;
    }

    private void cartesianProduct(int[] ncSub, int s, int[] combo,
                                   List<int[]> out) {
        if (s == ncSub.length) {
            out.add(combo.clone());
            return;
        }
        for (int i = 0; i < ncSub[s]; i++) {
            combo[s] = i;
            cartesianProduct(ncSub, s + 1, combo, out);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Step 5: Build EquilibriumState
    // ─────────────────────────────────────────────────────────────────

    private EquilibriumState buildState(List<GibbsEnergyModel> candidates,
                                         FacetResult facet,
                                         double[] xOverall,
                                         int np, double T, double P) {
        boolean[] used = new boolean[np];
        List<PhaseRecord> allPhases = new ArrayList<>();

        // Each hyperplane simplex vertex becomes its own stable
        // PhaseRecord, even when two or more vertices are the SAME phase
        // model -- e.g. a single-candidate system where the tangent
        // hyperplane at xOverall is approximated by several distinct
        // site-fraction states of that one phase, or a genuine
        // miscibility gap. This matches pycalphad's own
        // lower_convex_hull(), which likewise copies out Phase/X/Y/NP
        // per simplex vertex without merging same-named phases (see
        // lower_convex_hull.py's per-vertex "take(points, ...)" copy).
        // Collapsing same-phase vertices down to a single lowest-G point
        // (the previous behavior here) silently discards the
        // composition-matching mixture and returns a phase at the WRONG
        // composition entirely for cases like a ternary phase's own
        // internal ordering (see GridMinimizerChiA12PycalphadTest).
        List<PhaseRecord> stableRecords = new ArrayList<>();

        for (int k = 0; k < facet.phaseIdx.length; k++) {
            int    ip  = facet.phaseIdx[k];
            double amt = facet.amount[k];
            if (amt < 1e-10) continue;

            GibbsEnergyModel m = candidates.get(ip);
            double[] y = facet.yAtVertex[k].clone();
            double[] x = m.compositionFromInternal(y);
            PhaseRecord pr = new PhaseRecord(m, x, amt, true);
            pr.y = y;
            pr.G = m.G(T, P, y);
            stableRecords.add(pr);
            used[ip] = true;

            LOG.fine(String.format(
                "GridMin stable [%s] amt=%.4f x=%s",
                m.phaseName(), amt, java.util.Arrays.toString(x)));
        }

        // Merge stable slots of the SAME candidate model whose overall
        // compositions are within OpenCalphad's own same_composition()
        // tolerance (matsmin.F90, xdiff=0.01 mole fraction per
        // component) -- the hull search can legitimately return two
        // simplex vertices for one candidate at nearly the same
        // composition (not a genuine miscibility gap, which OC's own
        // check correctly leaves alone since its two compositions differ
        // by more than this tolerance), producing two near-duplicate
        // rows in the downstream global equilibrium matrix and a
        // singular/ill-conditioned solve. Combine such slots into one,
        // amount-weighting the composition/constitution the same way the
        // lever rule already implies, rather than keeping both.
        allPhases.addAll(mergeDuplicateCompositions(stableRecords));

        // Metastable phases: no sampled point of these phases is on the
        // hull facet enclosing xOverall, so there is no energy-minimizing
        // constitution to reuse here -- fall back to the naive
        // composition-matching seed, same as before.
        for (int ip = 0; ip < np; ip++) {
            if (used[ip]) continue;
            GibbsEnergyModel m = candidates.get(ip);
            double[] y = m.getInitialInternalVars(xOverall);
            double[] x = m.compositionFromInternal(y);
            PhaseRecord pr = new PhaseRecord(m, x, 0.0, false);
            pr.y = y;
            pr.G = m.G(T, P, y);
            allPhases.add(pr);
        }

        return new EquilibriumState(T, P, xOverall, allPhases);
    }

    /**
     * Composition-difference tolerance (mole fraction, per component)
     * below which two stable slots of the SAME candidate model are
     * considered duplicates rather than a genuine miscibility gap --
     * OpenCalphad's own {@code same_composition()} (matsmin.F90,
     * {@code xdiff=0.01D0}). OC's own comment there notes the tradeoff
     * directly: "a large value... may mean you miss a miscibility gap; a
     * small value may create bad convergence" -- 0.01 is their own
     * settled value after tuning against Au-Cu, adopted verbatim here
     * rather than re-deriving a project-specific number.
     */
    private static final double SAME_COMPOSITION_TOLERANCE = 0.01;

    /**
     * Merges stable slots of the same candidate model whose overall
     * compositions ({@code x}, not site fractions {@code y}) are within
     * {@link #SAME_COMPOSITION_TOLERANCE} on every component -- see the
     * call site's own comment for why this is needed and why it is safe
     * (a genuine miscibility gap's two vertices differ by more than this
     * tolerance in at least one component, so it is never merged).
     * Merged slots combine amounts (lever rule) and keep the
     * lower-index slot's constitution.
     */
    private List<PhaseRecord> mergeDuplicateCompositions(
            List<PhaseRecord> stableRecords) {

        boolean[] merged = new boolean[stableRecords.size()];
        List<PhaseRecord> result = new ArrayList<>();

        for (int i = 0; i < stableRecords.size(); i++) {

            if (merged[i]) {
                continue;
            }

            PhaseRecord keep = stableRecords.get(i);

            for (int j = i + 1; j < stableRecords.size(); j++) {

                if (merged[j]) {
                    continue;
                }

                PhaseRecord candidate = stableRecords.get(j);

                if (keep.model != candidate.model) {
                    continue;
                }

                if (!withinTolerance(keep.x, candidate.x)) {
                    continue;
                }

                LOG.fine(String.format(
                        "GridMin merging duplicate-composition stable "
                        + "slot [%s] x=%s into x=%s (amounts %.4f + %.4f)",
                        keep.model.phaseName(),
                        java.util.Arrays.toString(candidate.x),
                        java.util.Arrays.toString(keep.x),
                        keep.amount, candidate.amount));

                keep.amount += candidate.amount;
                merged[j] = true;
            }

            result.add(keep);
        }

        return result;
    }

    /**
     * True if every component of {@code x1}/{@code x2} differs by no
     * more than {@link #SAME_COMPOSITION_TOLERANCE}.
     */
    private boolean withinTolerance(double[] x1, double[] x2) {

        int n = Math.min(x1.length, x2.length);

        for (int i = 0; i < n; i++) {

            if (Math.abs(x1[i] - x2[i]) > SAME_COMPOSITION_TOLERANCE) {
                return false;
            }
        }

        return true;
    }

    // ─────────────────────────────────────────────────────────────────
    // Inner data class
    // ─────────────────────────────────────────────────────────────────

    private static class FacetResult {
        int[]      phaseIdx;
        double[][] yAtVertex;
        double[][] xAtVertex;
        double[]   amount;
    }
}
