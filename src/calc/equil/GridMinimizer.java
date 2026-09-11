package calc.equil;

import system.model.GibbsEnergyModel;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Grid minimizer -- initial phase set estimator, ported to sample and
 * search exactly the way pycalphad's {@code calculate()} /
 * {@code lower_convex_hull()} do.
 *
 * <p>pycalphad's global-minimization starting point (see
 * {@code pycalphad.core.calculate._sample_phase_constitution} and
 * {@code pycalphad.core.starting_point.starting_point}) works directly in
 * the internal degrees of freedom (site fractions), not in composition
 * space:
 *
 * <ol>
 *   <li>Sample the site-fraction space of every candidate phase: exact
 *       endmembers, a fixed grid of points along every endmember-pair edge,
 *       and {@code pdof * dof} interior points from a scrambled Halton
 *       sequence transformed onto each sublattice's simplex
 *       ({@code point_sample}).</li>
 *   <li>Evaluate G (and hence the per-mole-atom energy) and the overall
 *       composition X at every sampled point of every phase.</li>
 *   <li>Take the lower convex hull of the combined (X, G/atom) point cloud
 *       across all candidate phases.</li>
 *   <li>The hull facet enclosing the requested overall composition gives
 *       the initial stable phase set, their site fractions, and (via the
 *       lever rule / barycentric coordinates) their initial amounts.</li>
 * </ol>
 *
 * <p>This class ports steps 1-4 directly:
 * {@link #sampleSiteFractions} mirrors
 * {@code _sample_phase_constitution} ({@link Halton#pointSample} is a
 * direct port of {@code pycalphad.core.utils.point_sample}, itself built on
 * {@link Halton#generate}, a direct port of
 * {@code pycalphad.core.halton.halton}); {@link #initialize} mirrors
 * {@code starting_point()}'s call into the convex hull.
 *
 * <p>The hull/facet search itself ({@link #lowerConvexHull},
 * {@link #findFacet}) is exact only for binary (2-component) systems
 * (Andrew's monotone chain, as pycalphad's own {@code hyperplane} routine
 * is exact for any dimension via QHull); for higher-order systems this
 * falls back to a nearest-sampled-point search, which is an approximation
 * of the true facet-finding pycalphad performs via a full N-dimensional
 * convex hull.
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
        List<Double>   envG     = new ArrayList<>();  // G per mole atom (nfu-normalized)
        List<Integer>  envPhase = new ArrayList<>();  // candidate index
        List<double[]> envY     = new ArrayList<>();  // site fractions y at each point

        for (int ip = 0; ip < np; ip++) {
            GibbsEnergyModel m = candidates.get(ip);
            double nfu = m.nfu();
            if (nfu <= 0) nfu = 1.0;

            double[][] points = sampleSiteFractions(m);

            for (double[] y : points) {

                double G;
                double[] x;
                try {
                    G = m.G(T, P, y) / nfu;
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

        // Step 3: lower convex hull of the combined (X, G) point cloud.
        List<Integer> hull = lowerConvexHull(X, G, nc);

        // Step 4: find hull facet enclosing xOverall.
        FacetResult facet = findFacet(hull, X, phase, Y, xOverall, nc);

        // Step 5: build EquilibriumState.
        return buildState(candidates, facet, xOverall, np, T, P);
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
    private double[][] sampleSiteFractions(GibbsEnergyModel m) {

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
    // Step 3: Lower convex hull
    // ─────────────────────────────────────────────────────────────────

    private List<Integer> lowerConvexHull(double[][] X, double[] G, int nc) {
        if (nc == 2) return monotoneChain(X, G);
        return allHullPoints(X); // fallback for nc>2
    }

    /** Andrew's monotone chain -- lower hull in 2D (binary system). */
    private List<Integer> monotoneChain(double[][] X, double[] G) {
        int n = X.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx,
            (a, b) -> Double.compare(X[a][0], X[b][0]));

        int[] hull = new int[n];
        int   k    = 0;
        for (int ii = 0; ii < n; ii++) {
            int i = idx[ii];
            while (k >= 2) {
                int a = hull[k-2], b = hull[k-1];
                double cross =
                    (X[b][0] - X[a][0]) * (G[i] - G[a]) -
                    (X[i][0] - X[a][0]) * (G[b] - G[a]);
                if (cross <= 0) k--;
                else break;
            }
            hull[k++] = i;
        }
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < k; i++) result.add(hull[i]);
        return result;
    }

    /** Fallback: return all sampled points (safe but slow for nc>2). */
    private List<Integer> allHullPoints(double[][] X) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < X.length; i++) result.add(i);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // Step 4: Find enclosing facet
    // ─────────────────────────────────────────────────────────────────

    private FacetResult findFacet(List<Integer> hull,
                                   double[][] X,
                                   int[] envPhase,
                                   double[][] envY,
                                   double[] xOverall,
                                   int nc) {
        if (nc == 2) return findFacetBinary(hull, X, envPhase, envY, xOverall);
        return closestVertex(hull, X, envPhase, envY, xOverall);
    }

    /**
     * Binary case: find the hull segment [left, right] that encloses
     * xOverall[0], compute lever-rule amounts.
     */
    private FacetResult findFacetBinary(List<Integer> hull,
                                         double[][] X,
                                         int[] envPhase,
                                         double[][] envY,
                                         double[] xOverall) {
        double xTarget = xOverall[0];
        int    bestL   = hull.get(0);
        int    bestR   = hull.get(hull.size() - 1);

        for (int h = 0; h < hull.size() - 1; h++) {
            int    li  = hull.get(h);
            int    ri  = hull.get(h + 1);
            double xl  = X[li][0];
            double xr  = X[ri][0];
            if (xl <= xTarget + 1e-10 && xTarget <= xr + 1e-10) {
                bestL = li;
                bestR = ri;
                break;
            }
        }

        double xl  = X[bestL][0];
        double xr  = X[bestR][0];
        double dx  = xr - xl;
        double lam = (dx > 1e-12) ? (xTarget - xl) / dx : 0.5;
        lam        = Math.max(0.0, Math.min(1.0, lam));

        FacetResult fr  = new FacetResult();
        fr.phaseIdx     = new int[]    { envPhase[bestL], envPhase[bestR] };
        fr.yAtVertex    = new double[][]{ envY[bestL],     envY[bestR]    };
        fr.xAtVertex    = new double[][]{ X[bestL],        X[bestR]       };
        fr.amount       = new double[]  { 1.0 - lam,       lam            };
        return fr;
    }

    /** Fallback: return the single closest hull vertex. */
    private FacetResult closestVertex(List<Integer> hull,
                                       double[][] X,
                                       int[] envPhase,
                                       double[][] envY,
                                       double[] xOverall) {
        int    best = hull.get(0);
        double bd   = dist(X[best], xOverall);
        for (int ig : hull) {
            double d = dist(X[ig], xOverall);
            if (d < bd) { bd = d; best = ig; }
        }
        FacetResult fr = new FacetResult();
        fr.phaseIdx    = new int[]    { envPhase[best] };
        fr.yAtVertex   = new double[][]{ envY[best]    };
        fr.xAtVertex   = new double[][]{ X[best]       };
        fr.amount      = new double[]  { 1.0           };
        return fr;
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

        // Deduplicate: if facet vertices point to the SAME phase (e.g. a
        // single-candidate system, or a miscibility gap resolved by two
        // sample points of one phase straddling xOverall), merge them into
        // one stable PhaseRecord at the lower-G vertex.
        //
        // Both vertices' y came from the site-fraction sampling/hull
        // search (sampleSiteFractions + lowerConvexHull), NOT re-derived
        // via getInitialInternalVars(xOverall) -- that naive
        // composition-only guess is exactly the disordered starting point
        // this class exists to avoid (see class javadoc).
        boolean allSame = true;
        for (int k = 1; k < facet.phaseIdx.length; k++)
            if (facet.phaseIdx[k] != facet.phaseIdx[0])
                { allSame = false; break; }

        if (allSame) {
            int ip = facet.phaseIdx[0];
            GibbsEnergyModel m = candidates.get(ip);

            int bestK = 0;
            double bestG = Double.POSITIVE_INFINITY;
            for (int k = 0; k < facet.yAtVertex.length; k++) {
                double g = m.G(T, P, facet.yAtVertex[k]);
                if (g < bestG) { bestG = g; bestK = k; }
            }

            double[] y = facet.yAtVertex[bestK].clone();
            double[] x = m.compositionFromInternal(y);
            PhaseRecord pr = new PhaseRecord(m, x, 1.0, true);
            pr.y = y;
            pr.G = bestG;
            allPhases.add(pr);
            used[ip] = true;
        } else {
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
                allPhases.add(pr);
                used[ip] = true;

                LOG.fine(String.format(
                    "GridMin stable [%s] amt=%.4f x=[%.4f,%.4f]",
                    m.phaseName(), amt,
                    x.length>0?x[0]:0, x.length>1?x[1]:0));
            }
        }

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

    // ─────────────────────────────────────────────────────────────────
    // Inner data class
    // ─────────────────────────────────────────────────────────────────

    private static class FacetResult {
        int[]      phaseIdx;
        double[][] yAtVertex;
        double[][] xAtVertex;
        double[]   amount;
    }

    // ─────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────

    private double dist(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) { double d=a[i]-b[i]; s+=d*d; }
        return Math.sqrt(s);
    }
}
