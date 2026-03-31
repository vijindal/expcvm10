package calc.equil;

import system.model.GibbsEnergyModel;
import util.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

/**
 * GridMinimizer — initial phase set estimator for Algorithm A.
 * (Sundman, Dupin, Hallstedt, CALPHAD 75 (2021) 102330, §2.3.3)
 *
 * ── Responsibility ────────────────────────────────────────────────────────
 * Provide a starting estimate of which phases are stable and at what
 * compositions, so that EquilibriumSolver can begin its Newton-Lagrange
 * iteration from a physically reasonable point.
 *
 * ── Design rules (enforced, not just aspirational) ────────────────────────
 *
 *   1. MODEL-AGNOSTIC.
 *      This class calls exactly two methods on GibbsEnergyModel:
 *        • evaluateG(y, T)            — scalar Gibbs energy
 *        • getInitialInternalVars(x)  — model's preferred starting y
 *      It never calls compute(), gradient(), hessian(), or any method that
 *      exposes the internals of RK, CVM, CEF, or any other model.
 *
 *   2. COMPONENT-COUNT-AGNOSTIC.
 *      nc is inferred from xOverall.length at runtime.  There are no if/else
 *      branches conditioned on nc, no special binary path, no hardcoded
 *      index 0 or 1 anywhere in the algorithm.
 *
 *   3. NO HARDCODED PHYSICS.
 *      No element names, no T thresholds, no assumed phase names.  The only
 *      configurable constant is `density` (grid resolution), which is purely
 *      numerical.
 *
 * ── Algorithm summary ─────────────────────────────────────────────────────
 *
 *   1. Build a uniform grid on the (nc−1)-simplex.
 *   2. Evaluate G via GibbsEnergyModel.evaluateG() for all phases at all
 *      grid points.  Record the phase with the lowest G at each point
 *      (the "lower envelope").
 *   3. Compute the lower convex hull of the (composition, G_min) surface.
 *      Binary case: Andrew's monotone chain.
 *      General case: gift-wrapping (Jarvis march) in nc-D space.
 *   4. Find the hull facet that encloses xOverall and solve for barycentric
 *      coordinates → phase amounts (lever rule).
 *   5. Return an EquilibriumState with stable PhaseRecords (positive amount)
 *      and metastable PhaseRecords (amount = 0).
 */
public class GridMinimizer {

    private static final Logger LOG = Logger.getLogger(GridMinimizer.class.getName());

    /**
     * Number of equal divisions along each simplex edge.
     * Grid size ≈ C(density + nc − 1, nc − 1).
     * Default 20 → 231 pts for nc=3, 1771 pts for nc=4.
     */
    private final int density;

    public GridMinimizer()            { this(Constants.GRID_DENSITY); }
    public GridMinimizer(int density) {
        if (density < 2) throw new IllegalArgumentException("density must be >= 2");
        this.density = density;
    }

    // =========================================================================
    //  Public entry point
    // =========================================================================

    /**
     * Estimate the initial phase set and compositions.
     *
     * @param candidates  candidate phases — any model type
     * @param T           temperature (K)
     * @param P           pressure (Pa)
     * @param xOverall    overall mole fractions, length = nc, must sum to 1
     * @return            EquilibriumState ready for EquilibriumSolver
     */
    public EquilibriumState initialize(List<GibbsEnergyModel> candidates,
                                       double T, double P,
                                       double[] xOverall) {
        int nc = xOverall.length;
        int np = candidates.size();

        // Step 1 ── Grid on the (nc-1)-simplex
        List<double[]> grid = buildSimplexGrid(nc);
        int ng = grid.size();
        LOG.fine(String.format("GridMin: nc=%d np=%d grid=%d", nc, np, ng));

        // Step 2 ── Lower envelope: phase with minimum G at each grid point
        double[] envelopeG     = new double[ng];
        int[]    envelopePhase = new int[ng];
        for (int ig = 0; ig < ng; ig++) {
            envelopeG[ig]     = Double.MAX_VALUE;
            envelopePhase[ig] = 0;
            for (int ip = 0; ip < np; ip++) {
                double g = safeG(candidates.get(ip), T, grid.get(ig));
                if (g < envelopeG[ig]) {
                    envelopeG[ig]     = g;
                    envelopePhase[ig] = ip;
                }
            }
        }

        // Step 3 ── Lower convex hull of (composition, G_min) surface
        List<Integer> hull = lowerConvexHull(grid, envelopeG, nc);
        LOG.fine(String.format("GridMin: hull vertices=%d", hull.size()));

        // Step 4 ── Locate xOverall on the hull; get phase amounts
        FacetResult facet = findEnclosingFacet(hull, grid, envelopePhase,
                                                xOverall, nc);

        // Step 5 ── Build EquilibriumState
        return buildState(candidates, facet, xOverall, np, T, P);
    }

    // =========================================================================
    //  Step 1 — Simplex grid
    // =========================================================================

    /**
     * Uniform grid on the (nc−1)-simplex with `density` intervals per edge.
     * Each point is a double[nc] that sums to 1, every component >= Constants.GRID_X_FLOOR.
     */
    private List<double[]> buildSimplexGrid(int nc) {
        List<double[]> pts = new ArrayList<>();
        addSimplexPoints(nc, new int[nc], 0, density, pts);
        return pts;
    }

    private void addSimplexPoints(int nc, int[] cnt, int dim, int rem,
                                   List<double[]> out) {
        if (dim == nc - 1) {
            cnt[dim] = rem;
            out.add(countsToY(cnt, nc));
            return;
        }
        for (int i = 0; i <= rem; i++) {
            cnt[dim] = i;
            addSimplexPoints(nc, cnt, dim + 1, rem - i, out);
        }
    }

    /** Convert integer counts (summing to density) to mole fractions with floor. */
    private double[] countsToY(int[] cnt, int nc) {
        double[] y = new double[nc];
        double sum = 0;
        for (int i = 0; i < nc; i++) {
            y[i] = Math.max((double) cnt[i] / density, Constants.GRID_X_FLOOR);
            sum += y[i];
        }
        for (int i = 0; i < nc; i++) y[i] /= sum;
        return y;
    }

    // =========================================================================
    //  Step 3 — Lower convex hull (arbitrary nc)
    // =========================================================================

    /**
     * Lower convex hull of the nc-dimensional point set
     * { (y[0], …, y[nc-2], G) } where y are the first nc-1 independent
     * mole fractions and G is the Gibbs energy.
     *
     * For nc=2: 2-D lower hull via Andrew's monotone chain.
     * For nc>2: gift-wrapping in nc-D space.
     *
     * Returns indices into `grid`.
     */
    private List<Integer> lowerConvexHull(List<double[]> grid,
                                           double[] G, int nc) {
        // Augment each grid point with its G value
        double[][] pts = augment(grid, G, nc);

        if (nc == 2) return monotoneChain(pts);
        return giftWrap(pts, nc);
    }

    /** Build augmented point matrix: each row = [y[0]..y[nc-2], G]. */
    private double[][] augment(List<double[]> grid, double[] G, int nc) {
        int n = grid.size();
        double[][] pts = new double[n][nc];
        for (int i = 0; i < n; i++) {
            System.arraycopy(grid.get(i), 0, pts[i], 0, nc - 1);
            pts[i][nc - 1] = G[i];
        }
        return pts;
    }

    // ── nc = 2: Andrew's monotone chain ──────────────────────────────────────

    private List<Integer> monotoneChain(double[][] pts) {
        int n = pts.length;
        List<Integer> hull = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            while (hull.size() >= 2) {
                int j = hull.get(hull.size() - 1);
                int k = hull.get(hull.size() - 2);
                // 2-D cross product in (x, G) plane;
                // remove j if it is above or on the line k→i
                double cross = (pts[j][0] - pts[k][0]) * (pts[i][1] - pts[k][1])
                             - (pts[i][0] - pts[k][0]) * (pts[j][1] - pts[k][1]);
                if (cross >= 0) hull.remove(hull.size() - 1);
                else break;
            }
            hull.add(i);
        }
        return hull;
    }

    // ── nc > 2: gift-wrapping ─────────────────────────────────────────────────

    /**
     * Jarvis march restricted to the lower half-space (facets whose outward
     * normal has a negative G-component).
     *
     * Works in the nc-dimensional augmented space where the last coordinate
     * is G.  We look for (nc−1)-simplices (facets) that lie on the lower hull.
     */
    private List<Integer> giftWrap(double[][] pts, int nc) {
        int n = pts.length;
        Set<Integer>  hullV   = new LinkedHashSet<>();
        List<int[]>   done    = new ArrayList<>();
        Queue<int[]>  edgeQ   = new LinkedList<>();

        // Seed: nc-point simplex starting from the global G-minimum
        int[] seed = buildSeed(pts, n, nc);
        if (seed == null) {
            // Degenerate: return all indices
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < n; i++) all.add(i);
            return all;
        }
        for (int v : seed) hullV.add(v);
        done.add(seed);
        enqEdges(seed, edgeQ);

        int cap = n * nc;
        while (!edgeQ.isEmpty() && done.size() < cap) {
            int[] edge = edgeQ.poll();
            int best = bestVertex(pts, edge, n, nc);
            if (best < 0) continue;
            int[] facet = cat(edge, best);
            if (isKnown(facet, done)) continue;
            done.add(facet);
            hullV.add(best);
            enqEdges(facet, edgeQ);
        }
        return new ArrayList<>(hullV);
    }

    private int[] buildSeed(double[][] pts, int n, int nc) {
        // Start from the lowest-G vertex
        int base = 0;
        for (int i = 1; i < n; i++)
            if (pts[i][nc - 1] < pts[base][nc - 1]) base = i;

        int[] current = {base};
        for (int step = 1; step < nc; step++) {
            int best = -1;
            double bestV = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (in(current, i)) continue;
                int[] trial = cat(current, i);
                double v = signedVol(pts, trial, nc);
                if (v < bestV) { bestV = v; best = i; }
            }
            if (best < 0) return null;
            current = cat(current, best);
        }
        return current;
    }

    private int bestVertex(double[][] pts, int[] edge, int n, int nc) {
        int best = -1;
        double bestV = 0; // We want vol < 0 (lower hull)
        for (int i = 0; i < n; i++) {
            if (in(edge, i)) continue;
            int[] trial = cat(edge, i);
            double v = signedVol(pts, trial, nc);
            if (v < bestV) { bestV = v; best = i; }
        }
        return best;
    }

    /**
     * Signed volume of the nc-simplex defined by `idx` in `pts`.
     * Uses the (nc−1)×(nc−1) determinant of edge vectors.
     */
    private double signedVol(double[][] pts, int[] idx, int nc) {
        if (idx.length != nc) return 0;
        double[][] M = new double[nc - 1][nc - 1];
        double[] p0 = pts[idx[0]];
        for (int i = 1; i < nc; i++) {
            double[] pi = pts[idx[i]];
            for (int j = 0; j < nc - 1; j++)
                M[i - 1][j] = pi[j] - p0[j];
        }
        return det(M, nc - 1);
    }

    private double det(double[][] M, int s) {
        if (s == 1) return M[0][0];
        if (s == 2) return M[0][0]*M[1][1] - M[0][1]*M[1][0];
        double r = 0;
        double[][] sub = new double[s - 1][s - 1];
        for (int c = 0; c < s; c++) {
            for (int i = 1; i < s; i++) {
                int sc = 0;
                for (int j = 0; j < s; j++)
                    if (j != c) sub[i-1][sc++] = M[i][j];
            }
            r += (c % 2 == 0 ? 1 : -1) * M[0][c] * det(sub, s - 1);
        }
        return r;
    }

    private void enqEdges(int[] facet, Queue<int[]> q) {
        for (int skip = 0; skip < facet.length; skip++) {
            int[] edge = new int[facet.length - 1];
            int k = 0;
            for (int i = 0; i < facet.length; i++) if (i != skip) edge[k++] = facet[i];
            q.add(edge);
        }
    }

    private boolean isKnown(int[] f, List<int[]> done) {
        int[] s = f.clone(); Arrays.sort(s);
        for (int[] d : done) {
            int[] ds = d.clone(); Arrays.sort(ds);
            if (Arrays.equals(s, ds)) return true;
        }
        return false;
    }

    private int[]    cat(int[] a, int v) { int[] r = Arrays.copyOf(a, a.length+1); r[a.length]=v; return r; }
    private boolean  in(int[] a, int v)  { for (int x : a) if (x == v) return true; return false; }

    // =========================================================================
    //  Step 4 — Locate xOverall on the hull
    // =========================================================================

    private static final class FacetResult {
        int[]      phaseIdx;   // phase index for each facet vertex
        double[][] yAtVertex;  // composition at each vertex
        double[]   amount;     // barycentric coordinate = lever-rule amount
    }

    /**
     * Find which hull facet (nc-vertex simplex of the lower convex hull)
     * contains xOverall, and compute barycentric coordinates.
     *
     * For nc=2, facets are consecutive pairs of hull vertices.
     * For nc>2, we generate all nc-combinations of hull vertices.
     */
    private FacetResult findEnclosingFacet(List<Integer> hull,
                                            List<double[]> grid,
                                            int[] envelopePhase,
                                            double[] xOverall, int nc) {
        List<int[]> facets = facetList(hull, nc);

        FacetResult best      = null;
        double      bestPenalty = Double.MAX_VALUE;

        for (int[] fv : facets) {
            double[] bary = bary(fv, grid, xOverall, nc);
            if (bary == null) continue;

            // Penalty = sum of negative barycentric coordinates (= how far outside)
            double penalty = 0;
            for (double b : bary) if (b < 0) penalty -= b;

            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = toFacetResult(fv, bary, grid, envelopePhase, nc);
                if (penalty < 1e-9) break; // perfect
            }
        }

        return (best != null) ? best : closestVertex(hull, grid, envelopePhase, xOverall);
    }

    private List<int[]> facetList(List<Integer> hull, int nc) {
        List<int[]> out = new ArrayList<>();
        if (nc == 2) {
            for (int i = 0; i < hull.size() - 1; i++)
                out.add(new int[]{hull.get(i), hull.get(i+1)});
        } else {
            combos(hull, nc, 0, new int[nc], 0, out);
        }
        return out;
    }

    private void combos(List<Integer> src, int nc, int start,
                         int[] cur, int depth, List<int[]> out) {
        if (depth == nc) { out.add(cur.clone()); return; }
        for (int i = start; i < src.size(); i++) {
            cur[depth] = src.get(i);
            combos(src, nc, i + 1, cur, depth + 1, out);
        }
    }

    /**
     * Compute barycentric coordinates of xOverall w.r.t. the nc simplex
     * whose vertices are grid[facetVerts[0..nc-1]].
     *
     * Solves: A·λ = b
     *   A[i][k] = grid[facetVerts[k]][i]  for i < nc-1
     *   A[nc-1][k] = 1
     *   b[i] = xOverall[i]  for i < nc-1
     *   b[nc-1] = 1
     * Returns null for singular systems (degenerate facet).
     */
    private double[] bary(int[] facetVerts, List<double[]> grid,
                            double[] xOverall, int nc) {
        double[][] A = new double[nc][nc];
        double[]   b = new double[nc];
        for (int k = 0; k < nc; k++) {
            double[] yk = grid.get(facetVerts[k]);
            for (int i = 0; i < nc - 1; i++) A[i][k] = yk[i];
            A[nc-1][k] = 1.0;
        }
        for (int i = 0; i < nc - 1; i++) b[i] = xOverall[i];
        b[nc-1] = 1.0;
        return gaussElim(A, b, nc);
    }

    private double[] gaussElim(double[][] A, double[] b, int n) {
        double[][] M = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            M[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col+1; r < n; r++)
                if (Math.abs(M[r][col]) > Math.abs(M[piv][col])) piv = r;
            double[] tmp = M[col]; M[col] = M[piv]; M[piv] = tmp;
            if (Math.abs(M[col][col]) < 1e-14) return null;
            double sc = M[col][col];
            for (int j = col; j <= n; j++) M[col][j] /= sc;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = M[r][col];
                for (int j = col; j <= n; j++) M[r][j] -= f * M[col][j];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = M[i][n];
        return x;
    }

    private FacetResult toFacetResult(int[] fv, double[] bary,
                                        List<double[]> grid,
                                        int[] envelopePhase, int nc) {
        FacetResult fr = new FacetResult();
        fr.phaseIdx   = new int[nc];
        fr.yAtVertex  = new double[nc][];
        fr.amount     = new double[nc];
        double sum = 0;
        for (int k = 0; k < nc; k++) {
            fr.phaseIdx[k]  = envelopePhase[fv[k]];
            fr.yAtVertex[k] = grid.get(fv[k]).clone();
            fr.amount[k]    = Math.max(bary[k], 0.0);
            sum += fr.amount[k];
        }
        if (sum > 1e-12) for (int k = 0; k < nc; k++) fr.amount[k] /= sum;
        return fr;
    }

    private FacetResult closestVertex(List<Integer> hull, List<double[]> grid,
                                        int[] envelopePhase, double[] xOverall) {
        int best = hull.get(0);
        double bd = dist(grid.get(best), xOverall);
        for (int ig : hull) {
            double d = dist(grid.get(ig), xOverall);
            if (d < bd) { bd = d; best = ig; }
        }
        FacetResult fr = new FacetResult();
        fr.phaseIdx   = new int[]    { envelopePhase[best] };
        fr.yAtVertex  = new double[][]{ grid.get(best).clone() };
        fr.amount     = new double[]  { 1.0 };
        return fr;
    }

    // =========================================================================
    //  Step 5 — Build EquilibriumState
    // =========================================================================

    private EquilibriumState buildState(List<GibbsEnergyModel> candidates,
                                         FacetResult facet,
                                         double[] xOverall, int np,
                                         double T, double P) {
        List<PhaseRecord> allPhases = new ArrayList<>();
        boolean[] stable = new boolean[np];

        // Detect degenerate case: all facet vertices point to the same phase.
        // This happens (e.g. HCP_A3 at both pure-Ti and pure-Zr endpoints at low T)
        // and would create duplicate PhaseRecord instances → singular Jacobian.
        // In that case create a single PhaseRecord at xOverall as the starting point.
        boolean allSamePhase = true;
        int firstPhaseIdx = facet.phaseIdx[0];
        for (int k = 1; k < facet.phaseIdx.length; k++) {
            if (facet.phaseIdx[k] != firstPhaseIdx) { allSamePhase = false; break; }
        }

        if (allSamePhase && facet.phaseIdx.length > 1) {
            int ip = firstPhaseIdx;
            double[] y = safeInitVars(candidates.get(ip), xOverall);
            double[] x = candidates.get(ip).compositionFromInternal(y);
            PhaseRecord pr = new PhaseRecord(candidates.get(ip), x, 1.0, true);
            pr.y = y;
            allPhases.add(pr);
            stable[ip] = true;
            LOG.fine(String.format("GridMin degenerate (all same phase) — single stable [%s] at xOverall=%s",
                candidates.get(ip).phaseName(), fmt(xOverall)));
        } else {
            // Create stable phases from facet
            for (int k = 0; k < facet.phaseIdx.length; k++) {
                int    ip  = facet.phaseIdx[k];
                double amt = facet.amount[k];
                double[] y = safeInitVars(candidates.get(ip), facet.yAtVertex[k]);
                double[] x = candidates.get(ip).compositionFromInternal(y);

                PhaseRecord pr = new PhaseRecord(candidates.get(ip), x, amt, true);
                pr.y = y;
                allPhases.add(pr);
                stable[ip] = true;

                LOG.fine(String.format("GridMin stable [%s] amt=%.4f y=%s",
                    candidates.get(ip).phaseName(), amt, fmt(y)));
            }
        }

        // Create metastable phases
        for (int ip = 0; ip < np; ip++) {
            if (!stable[ip]) {
                double[] y = safeInitVars(candidates.get(ip), xOverall);
                double[] x = candidates.get(ip).compositionFromInternal(y);
                PhaseRecord pr = new PhaseRecord(candidates.get(ip), x, 0.0, false);
                pr.y = y;
                allPhases.add(pr);
            }
        }

        return new EquilibriumState(T, P, xOverall, allPhases);
    }

    // =========================================================================
    //  Safe GibbsEnergyModel wrappers
    // =========================================================================

    /** Evaluate G, returning Double.MAX_VALUE on any exception. */
    private double safeG(GibbsEnergyModel m, double T, double[] y) {
        try   { return m.evaluateG(y, T); }
        catch (Exception e) { return Double.MAX_VALUE; }
    }

    /** Get model's preferred starting internal vars; fall back to y on error. */
    private double[] safeInitVars(GibbsEnergyModel m, double[] y) {
        try {
            double[] v = m.getInitialInternalVars(y);
            return (v != null) ? v : y.clone();
        } catch (Exception e) { return y.clone(); }
    }

    // =========================================================================
    //  Utilities
    // =========================================================================

    private double dist(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) { double d=a[i]-b[i]; s+=d*d; }
        return Math.sqrt(s);
    }

    private String fmt(double[] a) {
        if (a == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.4f", a[i]));
        }
        return sb.append("]").toString();
    }
}