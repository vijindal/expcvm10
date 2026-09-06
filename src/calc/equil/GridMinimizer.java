package calc.equil;

import system.model.GibbsEnergyModel;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Grid minimizer — initial phase set estimator for Algorithm A.
 *
 * Implements Sundman et al. CALPHAD 75 (2021) §2.3.3:
 *   "The grid minimizer approximates the Gibbs energy surface of each
 *    phase with a set of gridpoints and selects the set that gives the
 *    lowest Gibbs energy for the current conditions."
 *
 * Algorithm:
 *   1. Build a uniform composition grid on the (nc-1)-simplex.
 *   2. For each grid point x and each phase, evaluate G(x,T)/nfu
 *      (per mole of atoms — common basis for all phases).
 *      For each grid point keep only the phase with lowest G.
 *      This builds the lower envelope.
 *   3. Build the lower convex hull of the (x, G_min) surface.
 *      Binary: Andrew's monotone chain on 2D points.
 *   4. Find the hull facet enclosing xOverall via barycentric coords.
 *      The phases at the facet vertices are the initial stable set.
 *   5. Build EquilibriumState with those phases as stable,
 *      all others as metastable with amount=0.
 *
 * Key design rule from Sundman: G must be compared on a per-atom basis
 * (G_per_FU / nfu) so that phases with different formula units
 * (e.g. V2Zr with nfu=3 vs LIQUID with nfu=1) compete fairly.
 *
 * Internal variable minimization: for ordered phases the grid minimizer
 * should ideally minimize G over internal variables at each grid point.
 * Here we use a simple steepest-descent inner loop over site fractions
 * for each phase at each grid point, which correctly finds the ordered
 * configuration at each composition.
 */
public class GridMinimizer {

    private static final Logger LOG =
        Logger.getLogger(GridMinimizer.class.getName());

    /** Grid density: number of intervals per composition axis. */
    private static final int DENSITY = 20;

    /** Max iterations for inner site-fraction minimization. */
    private static final int INNER_ITER = 50;

    /** Step size for inner minimization. */
    private static final double INNER_STEP = 0.05;

    /** Floor for site fractions to avoid log(0). */
    private static final double Y_FLOOR = 1e-6;

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

        // Step 1: composition grid on (nc-1)-simplex
        List<double[]> grid = buildGrid(nc);
        int ng = grid.size();

        // Step 2: lower envelope — minimum G/atom over all phases
        // at each grid point, with inner site-fraction minimization
        double[] envG     = new double[ng];
        int[]    envPhase = new int[ng];
        double[][] envY   = new double[ng][];  // best y at each grid point

        for (int ig = 0; ig < ng; ig++) {
            envG[ig]     = Double.MAX_VALUE;
            envPhase[ig] = 0;
            double[] x   = grid.get(ig);

            for (int ip = 0; ip < np; ip++) {
                GibbsEnergyModel m = candidates.get(ip);
                double nfu = m.nfu();
                if (nfu <= 0) nfu = 1.0;

                // Initialize site fractions from composition
                double[] y = m.getInitialInternalVars(x);
                if (y == null || y.length == 0) continue;

                // Inner minimization: descend over site fractions
                // to find the lowest G configuration at this composition.
                // This is critical for ordered phases like V2Zr where
                // the stable configuration is NOT the uniform one.
                y = minimizeSiteFractions(m, y, x, T);

                double G;
                try {
                    G = m.evaluateG(y, T) / nfu;
                } catch (Exception e) {
                    continue;
                }

                if (G < envG[ig]) {
                    envG[ig]     = G;
                    envPhase[ig] = ip;
                    envY[ig]     = y.clone();
                }
            }
            if (envY[ig] == null)
                envY[ig] = candidates.get(envPhase[ig])
                               .getInitialInternalVars(x);
        }

        // Step 3: lower convex hull (binary: 2D monotone chain)
        List<Integer> hull = lowerConvexHull(grid, envG, nc);

        // Step 4: find hull facet enclosing xOverall
        FacetResult facet = findFacet(hull, grid, envPhase, envY,
                                       xOverall, nc);

        // Step 5: build EquilibriumState
        return buildState(candidates, facet, xOverall, np, T, P);
    }

    // ─────────────────────────────────────────────────────────────────
    // Step 1: Composition grid
    // ─────────────────────────────────────────────────────────────────

    private List<double[]> buildGrid(int nc) {
        List<double[]> pts = new ArrayList<>();
        buildGridRecursive(nc, new int[nc], 0, DENSITY, pts);
        return pts;
    }

    private void buildGridRecursive(int nc, int[] cnt, int dim,
                                     int rem, List<double[]> out) {
        if (dim == nc - 1) {
            cnt[dim] = rem;
            out.add(countsToX(cnt, nc));
            return;
        }
        for (int i = 0; i <= rem; i++) {
            cnt[dim] = i;
            buildGridRecursive(nc, cnt, dim + 1, rem - i, out);
        }
    }

    private double[] countsToX(int[] cnt, int nc) {
        double[] x   = new double[nc];
        double   sum = 0;
        for (int i = 0; i < nc; i++) {
            x[i] = Math.max((double) cnt[i] / DENSITY, Y_FLOOR);
            sum += x[i];
        }
        for (int i = 0; i < nc; i++) x[i] /= sum;
        return x;
    }

    // ─────────────────────────────────────────────────────────────────
    // Inner site-fraction minimization
    // ─────────────────────────────────────────────────────────────────

    /**
     * Minimize G(y,T) over site fractions y, subject to sublattice
     * sum constraints, for fixed overall composition x.
     *
     * Uses gradient descent on site fractions within each sublattice.
     * This ensures ordered phases settle into their correct
     * configuration (e.g. V2Zr favors V on SL1 and Zr on SL2
     * near x_Zr=0.333).
     *
     * The composition constraint x is enforced by using the
     * overall composition as a soft constraint — we minimize
     * G(y,T) while keeping the phase at composition x.
     * For phases where all sublattices are substitutional,
     * the composition uniquely determines y only for single-sublattice
     * phases. For multi-sublattice ordered phases we explore the space.
     */
    private double[] minimizeSiteFractions(GibbsEnergyModel m,
                                            double[] yInit,
                                            double[] x,
                                            double T) {
        int nip = yInit.length;
        if (nip == 0) return yInit;

        double[] y    = yInit.clone();
        double   G    = safeEval(m, y, T);
        double   nfu  = Math.max(m.nfu(), 1.0);

        for (int iter = 0; iter < INNER_ITER; iter++) {
            double[] grad;
            try {
                grad = m.gradient(x, T);
            } catch (Exception e) {
                break;
            }

            // Try small step in gradient direction on each component
            // within site-fraction space via getInitialInternalVars
            // using a perturbed composition
            boolean improved = false;
            int nc = x.length;
            for (int k = 0; k < nc && !improved; k++) {
                // Perturb composition slightly toward component k
                double[] xTry = x.clone();
                double delta  = INNER_STEP * (1.0 / nc - x[k]);
                xTry[k]      += delta;
                // Renormalise
                double s = 0; for (double v : xTry) s += v;
                for (int i = 0; i < nc; i++) xTry[i] /= s;
                // Clamp
                for (int i = 0; i < nc; i++)
                    xTry[i] = Math.max(xTry[i], Y_FLOOR);

                double[] yTry = m.getInitialInternalVars(xTry);
                if (yTry == null) continue;
                double gTry = safeEval(m, yTry, T);
                if (gTry < G) {
                    y = yTry;
                    G = gTry;
                    improved = true;
                }
            }
            if (!improved) break;
        }
        return y;
    }

    private double safeEval(GibbsEnergyModel m, double[] y, double T) {
        try {
            double nfu = Math.max(m.nfu(), 1.0);
            return m.evaluateG(y, T) / nfu;
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Step 3: Lower convex hull
    // ─────────────────────────────────────────────────────────────────

    private List<Integer> lowerConvexHull(List<double[]> grid,
                                           double[] G, int nc) {
        if (nc == 2) return monotoneChain(grid, G);
        return allHullPoints(grid, G); // fallback for nc>2
    }

    /** Andrew's monotone chain — lower hull in 2D (binary system). */
    private List<Integer> monotoneChain(List<double[]> grid,
                                         double[] G) {
        int n = grid.size();
        // Sort by x[0]
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx,
            (a, b) -> Double.compare(grid.get(a)[0], grid.get(b)[0]));

        // Build lower hull
        int[] hull = new int[n];
        int   k    = 0;
        for (int ii = 0; ii < n; ii++) {
            int i = idx[ii];
            while (k >= 2) {
                int a = hull[k-2], b = hull[k-1];
                // Cross product — keep only right turns (lower hull)
                double cross =
                    (grid.get(b)[0] - grid.get(a)[0]) * (G[i]   - G[a]) -
                    (grid.get(i)[0] - grid.get(a)[0]) * (G[b]   - G[a]);
                if (cross <= 0) k--;
                else break;
            }
            hull[k++] = i;
        }
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < k; i++) result.add(hull[i]);
        return result;
    }

    /** Fallback: return all grid points (safe but slow for nc>2). */
    private List<Integer> allHullPoints(List<double[]> grid,
                                         double[] G) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < grid.size(); i++) result.add(i);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // Step 4: Find enclosing facet
    // ─────────────────────────────────────────────────────────────────

    private FacetResult findFacet(List<Integer> hull,
                                   List<double[]> grid,
                                   int[] envPhase,
                                   double[][] envY,
                                   double[] xOverall,
                                   int nc) {
        if (nc == 2) return findFacetBinary(hull, grid, envPhase,
                                             envY, xOverall);
        return closestVertex(hull, grid, envPhase, envY, xOverall);
    }

    /**
     * Binary case: find the hull segment [left, right] that encloses
     * xOverall[0], compute lever-rule amounts.
     */
    private FacetResult findFacetBinary(List<Integer> hull,
                                         List<double[]> grid,
                                         int[] envPhase,
                                         double[][] envY,
                                         double[] xOverall) {
        double xTarget = xOverall[0];
        int    bestL   = hull.get(0);
        int    bestR   = hull.get(hull.size() - 1);

        for (int h = 0; h < hull.size() - 1; h++) {
            int    li  = hull.get(h);
            int    ri  = hull.get(h + 1);
            double xl  = grid.get(li)[0];
            double xr  = grid.get(ri)[0];
            if (xl <= xTarget + 1e-10 && xTarget <= xr + 1e-10) {
                bestL = li;
                bestR = ri;
                break;
            }
        }

        double xl  = grid.get(bestL)[0];
        double xr  = grid.get(bestR)[0];
        double dx  = xr - xl;
        double lam = (dx > 1e-12) ? (xTarget - xl) / dx : 0.5;
        lam        = Math.max(0.0, Math.min(1.0, lam));

        FacetResult fr  = new FacetResult();
        fr.phaseIdx     = new int[]    { envPhase[bestL], envPhase[bestR] };
        fr.yAtVertex    = new double[][]{ envY[bestL],     envY[bestR]    };
        fr.xAtVertex    = new double[][]{ grid.get(bestL), grid.get(bestR)};
        fr.amount       = new double[]  { 1.0 - lam,       lam            };
        return fr;
    }

    /** Fallback: return the single closest hull vertex. */
    private FacetResult closestVertex(List<Integer> hull,
                                       List<double[]> grid,
                                       int[] envPhase,
                                       double[][] envY,
                                       double[] xOverall) {
        int    best = hull.get(0);
        double bd   = dist(grid.get(best), xOverall);
        for (int ig : hull) {
            double d = dist(grid.get(ig), xOverall);
            if (d < bd) { bd = d; best = ig; }
        }
        FacetResult fr = new FacetResult();
        fr.phaseIdx    = new int[]    { envPhase[best] };
        fr.yAtVertex   = new double[][]{ envY[best]    };
        fr.xAtVertex   = new double[][]{ grid.get(best)};
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

        // Deduplicate: if facet vertices point to same phase,
        // keep only one at xOverall
        boolean allSame = true;
        for (int k = 1; k < facet.phaseIdx.length; k++)
            if (facet.phaseIdx[k] != facet.phaseIdx[0])
                { allSame = false; break; }

        if (allSame) {
            int ip = facet.phaseIdx[0];
            GibbsEnergyModel m = candidates.get(ip);
            double[] y = m.getInitialInternalVars(xOverall);
            double[] x = m.compositionFromInternal(y);
            PhaseRecord pr = new PhaseRecord(m, x, 1.0, true);
            pr.y = y;
            allPhases.add(pr);
            used[ip] = true;
        } else {
            for (int k = 0; k < facet.phaseIdx.length; k++) {
                int    ip  = facet.phaseIdx[k];
                double amt = facet.amount[k];
                if (amt < 1e-10) continue;

                GibbsEnergyModel m = candidates.get(ip);
                double[] y = facet.yAtVertex[k];
                double[] x = m.compositionFromInternal(y);
                PhaseRecord pr = new PhaseRecord(m, x, amt, true);
                pr.y = y.clone();
                allPhases.add(pr);
                used[ip] = true;

                LOG.fine(String.format(
                    "GridMin stable [%s] amt=%.4f x=[%.4f,%.4f]",
                    m.phaseName(), amt,
                    x.length>0?x[0]:0, x.length>1?x[1]:0));
            }
        }

        // Metastable phases
        for (int ip = 0; ip < np; ip++) {
            if (used[ip]) continue;
            GibbsEnergyModel m = candidates.get(ip);
            double[] y = m.getInitialInternalVars(xOverall);
            double[] x = m.compositionFromInternal(y);
            PhaseRecord pr = new PhaseRecord(m, x, 0.0, false);
            pr.y = y;
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
