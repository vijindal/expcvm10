package thermocalc.cef;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Compound Energy Formalism (CEF) Gibbs energy evaluator for C15 Laves phases.
 *
 * <h2>Mathematica correspondence</h2>
 * Direct translation of {@code calGC15[paramList]} in clusGen_25, extended with
 * full analytical first and second derivatives needed by {@link CefPhaseModel}.
 *
 * <h2>Crystal structure: C15 Laves (A,B,...)₂(A,B,...)₁</h2>
 * <pre>
 *   ns = 2 sublattices,  a = {2, 1} stoichiometric coefficients
 *   nc = number of components (2, 3, or 4)
 *   nip = 2·nc internal parameters: y = {y[0][0]..y[0][nc-1], y[1][0]..y[1][nc-1]}
 * </pre>
 * Component mole numbers:  {@code n[k] = 2·y[0][k] + 1·y[1][k]}   (from the n-vector)
 *
 * <h2>Gibbs energy formula</h2>
 * <pre>
 *   G = G₀ + G_id + G_Em
 *
 *   G₀   = Σᵢ Σⱼ y[0][i]·y[1][j]·G[i][j](T)                        (end-member reference)
 *
 *   G_id = R·T·(2·Σᵢ y[0][i]·ln(y[0][i])  +  1·Σⱼ y[1][j]·ln(y[1][j]))
 *                ↑ sublattice-1 weighted by a₁=2      ↑ sublattice-2 weighted by a₂=1
 *
 *   G_Em = Σ over all L params: y[pairSL][pA]·y[pairSL][pB]·y[otherSL][k]·L(T)
 * </pre>
 *
 * <h2>Internal parameter layout</h2>
 * {@code y[s][i]} is stored in a flat array of length {@code 2·nc}:
 * <pre>
 *   flat index: s*nc + i   (s=0: sublattice 1, s=1: sublattice 2)
 * </pre>
 * This matches the Mathematica flattening {@code ipl = {y11,y12,...,y21,y22,...}}.
 *
 * <h2>Analytical derivatives — derivation</h2>
 *
 * Let {@code m = s·nc + i} be the flat index for sublattice {@code s}, component {@code i}.
 *
 * <b>∂G₀/∂y[s][i]</b>:
 * <pre>
 *   if s=0: ∂/∂y[0][i] Σₐ Σ_b y[0][a]·y[1][b]·G[a][b] = Σ_b y[1][b]·G[i][b]
 *   if s=1: ∂/∂y[1][j] Σₐ Σ_b y[0][a]·y[1][b]·G[a][b] = Σₐ y[0][a]·G[a][j]
 * </pre>
 *
 * <b>∂G_id/∂y[s][i]</b>:
 * <pre>
 *   = R·T·a[s]·(ln(y[s][i]) + 1)
 * </pre>
 *
 * <b>∂G_Em/∂y[s][i]</b>: for each interaction param p:
 * <pre>
 *   Type 1 (pair on SL 0): contribution y[0][pA]·y[0][pB]·y[1][k]·L
 *     ∂/∂y[0][pA] = y[0][pB]·y[1][k]·L
 *     ∂/∂y[0][pB] = y[0][pA]·y[1][k]·L
 *     ∂/∂y[1][k]  = y[0][pA]·y[0][pB]·L
 *   Type 2 (pair on SL 1): contribution y[1][pA]·y[1][pB]·y[0][k]·L
 *     ∂/∂y[1][pA] = y[1][pB]·y[0][k]·L
 *     ∂/∂y[1][pB] = y[1][pA]·y[0][k]·L
 *     ∂/∂y[0][k]  = y[1][pA]·y[1][pB]·L
 * </pre>
 *
 * <b>∂²G/∂y[s][i]·∂y[t][j]</b>:
 * <pre>
 *   G₀ cross terms:
 *     s=0,t=1: ∂²G₀/∂y[0][i]∂y[1][j] = G[i][j]
 *     s=1,t=0: G[i][j]  (symmetric)
 *     same-sublattice: 0
 *   G_id diagonal only:
 *     s=t, i=j: R·T·a[s]/y[s][i]
 *     otherwise: 0
 *   G_Em: differentiate Gx_Em[m] w.r.t. y[t][j] — three-variable product, second deriv is simpler.
 * </pre>
 *
 * <b>∂²G/∂y[s][i]∂T (GxT)</b>:
 * <pre>
 *   G₀:   Σ_b y[1][b]·dG[i][b]/dT  (s=0) or Σₐ y[0][a]·dG[a][j]/dT (s=1)
 *   G_id: R·a[s]·(ln(y[s][i]) + 1)
 *   G_Em: same structure as ∂G_Em/∂y[s][i] but with dL/dT instead of L
 * </pre>
 */
public class CefGibbs {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    /** Universal gas constant J/(mol·K). */
    public static final double R = 8.3144598;

    /** Stoichiometric coefficients: a[0]=2 (sublattice 1), a[1]=1 (sublattice 2). */
    private static final double[] A = {2.0, 1.0};

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private final int nc;   // number of components
    private final int nip;  // 2·nc internal parameters

    /**
     * End-member energies G[i][j], stored as flat array indexed by i*nc + j.
     * i = sublattice-1 species (0..nc-1), j = sublattice-2 species (0..nc-1).
     */
    private final CefEndMember[] g0;

    private final List<CefInteractionParam> interactions;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Constructs a CEF Gibbs evaluator.
     *
     * @param nc           number of components (2, 3, or 4)
     * @param g0           end-member energies, flat array length nc² indexed [i*nc+j]
     * @param interactions CEF interaction parameters (may be empty if all zero)
     */
    public CefGibbs(int nc, CefEndMember[] g0, List<CefInteractionParam> interactions) {
        if (g0.length != nc * nc)
            throw new IllegalArgumentException("g0 must have length nc²=" + nc * nc);
        this.nc           = nc;
        this.nip          = 2 * nc;
        this.g0           = g0.clone();
        this.interactions = Collections.unmodifiableList(new ArrayList<>(interactions));
    }

    // ------------------------------------------------------------------
    // Primary API
    // ------------------------------------------------------------------

    /**
     * Evaluates G(y, T) = G₀ + G_id + G_Em.
     * Translation of {@code calGC15[paramList][[1]]} (returns full G, not Gmix).
     *
     * @param y  internal parameter vector, length 2·nc:
     *           {y[0][0]..y[0][nc-1], y[1][0]..y[1][nc-1]}
     * @param T  temperature in Kelvin
     * @return   molar Gibbs energy in J/mol
     */
    public double evaluate(double[] y, double T) {
        checkY(y);
        return g0Ref(y, T) + gId(y, T) + gEm(y, T);
    }

    /**
     * Composition gradient ∂G/∂y[s][i] for all flat indices m = s·nc + i.
     * Corresponds to {@code Gx = Table[D[G, ipl[[i]]], {i, nip}]} in Mathematica.
     *
     * @param y  internal parameter vector
     * @param T  temperature
     * @return   gradient vector, length nip = 2·nc
     */
    public double[] gradient(double[] y, double T) {
        checkY(y);
        double[] gx = new double[nip];

        // ── G₀ contribution ──────────────────────────────────────────
        // ∂G₀/∂y[0][i] = Σ_j y[1][j]·G[i][j]
        for (int i = 0; i < nc; i++) {
            double sum = 0.0;
            for (int j = 0; j < nc; j++) {
                sum += y[nc + j] * g0[i * nc + j].G(T);
            }
            gx[i] += sum;    // flat index for SL1: 0*nc + i = i
        }
        // ∂G₀/∂y[1][j] = Σ_i y[0][i]·G[i][j]
        for (int j = 0; j < nc; j++) {
            double sum = 0.0;
            for (int i = 0; i < nc; i++) {
                sum += y[i] * g0[i * nc + j].G(T);
            }
            gx[nc + j] += sum;  // flat index for SL2: 1*nc + j = nc+j
        }

        // ── G_id contribution ─────────────────────────────────────────
        // ∂G_id/∂y[s][i] = R·T·a[s]·(ln(y[s][i]) + 1)
        for (int s = 0; s < 2; s++) {
            double aS = A[s];
            for (int i = 0; i < nc; i++) {
                int m = s * nc + i;
                gx[m] += R * T * aS * (Math.log(y[m]) + 1.0);
            }
        }

        // ── G_Em contribution ─────────────────────────────────────────
        addGEmGradient(y, T, gx, false);

        return gx;
    }

    /**
     * Hessian ∂²G/∂y[s][i]∂y[t][j], returned as (nip × nip) matrix.
     * Corresponds to {@code Gxx = Table[D[G, ipl[[j]], ipl[[i]]], {j,nip},{i,nip}]}.
     *
     * @param y  internal parameter vector
     * @param T  temperature
     * @return   symmetric nip×nip Hessian
     */
    public double[][] hessian(double[] y, double T) {
        checkY(y);
        double[][] gxx = new double[nip][nip];

        // ── G₀: cross-sublattice terms only ──────────────────────────
        // ∂²G₀/∂y[0][i]∂y[1][j] = G[i][j]
        for (int i = 0; i < nc; i++) {
            for (int j = 0; j < nc; j++) {
                double gij = g0[i * nc + j].G(T);
                int mi = i;          // SL1 flat index
                int mj = nc + j;     // SL2 flat index
                gxx[mi][mj] += gij;
                gxx[mj][mi] += gij;  // symmetric
            }
        }

        // ── G_id: diagonal only ───────────────────────────────────────
        // ∂²G_id/∂y[s][i]² = R·T·a[s]/y[s][i]
        for (int s = 0; s < 2; s++) {
            for (int i = 0; i < nc; i++) {
                int m = s * nc + i;
                gxx[m][m] += R * T * A[s] / y[m];
            }
        }

        // ── G_Em: differentiate gradient w.r.t. each y ────────────────
        addGEmHessian(y, T, gxx, false);

        return gxx;
    }

    /**
     * Mixed derivative ∂²G/∂y[s][i]∂T = ∂(Gx)/∂T (GxT vector).
     * Corresponds to {@code GxT = D[Gx, T]} in Mathematica.
     *
     * @param y  internal parameter vector
     * @param T  temperature
     * @return   GxT vector, length nip
     */
    public double[] gradientDT(double[] y, double T) {
        checkY(y);
        double[] gxt = new double[nip];

        // ── G₀: ∂/∂T of (∂G₀/∂y[0][i]) = Σ_j y[1][j]·dG[i][j]/dT ──
        for (int i = 0; i < nc; i++) {
            double sum = 0.0;
            for (int j = 0; j < nc; j++) {
                sum += y[nc + j] * g0[i * nc + j].dGdT(T);
            }
            gxt[i] += sum;
        }
        for (int j = 0; j < nc; j++) {
            double sum = 0.0;
            for (int i = 0; i < nc; i++) {
                sum += y[i] * g0[i * nc + j].dGdT(T);
            }
            gxt[nc + j] += sum;
        }

        // ── G_id: ∂/∂T of R·T·a[s]·(ln(y)+1) = R·a[s]·(ln(y)+1) ───
        for (int s = 0; s < 2; s++) {
            double aS = A[s];
            for (int i = 0; i < nc; i++) {
                int m = s * nc + i;
                gxt[m] += R * aS * (Math.log(y[m]) + 1.0);
            }
        }

        // ── G_Em: same structure but dL/dT instead of L ──────────────
        addGEmGradient(y, T, gxt, true);

        return gxt;
    }

    // ------------------------------------------------------------------
    // Individual energy components (for diagnostics)
    // ------------------------------------------------------------------

    /**
     * Reference energy G₀ = Σᵢ Σⱼ y[0][i]·y[1][j]·G[i][j](T).
     */
    public double g0Ref(double[] y, double T) {
        double g = 0.0;
        for (int i = 0; i < nc; i++)
            for (int j = 0; j < nc; j++)
                g += y[i] * y[nc + j] * g0[i * nc + j].G(T);
        return g;
    }

    /**
     * Ideal mixing entropy:
     * G_id = R·T·(2·Σᵢ y[0][i]·ln(y[0][i]) + 1·Σⱼ y[1][j]·ln(y[1][j])).
     */
    public double gId(double[] y, double T) {
        double g = 0.0;
        for (int s = 0; s < 2; s++) {
            double sub = 0.0;
            for (int i = 0; i < nc; i++) {
                double yi = y[s * nc + i];
                sub += yi * Math.log(yi);
            }
            g += A[s] * sub;
        }
        return R * T * g;
    }

    /**
     * Excess mixing energy G_Em = Σ_interactions y[pSL][pA]·y[pSL][pB]·y[oSL][k]·L(T).
     */
    public double gEm(double[] y, double T) {
        double g = 0.0;
        for (CefInteractionParam p : interactions) {
            int pSL  = p.pairSublattice;
            int oSL  = 1 - pSL;
            g += y[pSL * nc + p.pairA]
               * y[pSL * nc + p.pairB]
               * y[oSL * nc + p.singleIdx]
               * p.L(T);
        }
        return g;
    }

    /** Returns number of components. */
    public int nc() { return nc; }

    /** Returns number of internal parameters (2·nc). */
    public int nip() { return nip; }

    /** Returns stoichiometric coefficients {a₁, a₂}. */
    public double[] stoichiometry() { return A.clone(); }

    /** Returns the interaction parameter list (unmodifiable). */
    public List<CefInteractionParam> interactions() { return interactions; }

    /**
     * Returns G[i][j] end-member at (i, j) — sublattice 1 species i, sublattice 2 species j.
     * Both indices 0-based.
     */
    public CefEndMember endMember(int i, int j) { return g0[i * nc + j]; }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Accumulates ∂G_Em/∂y[s][i] into gx[] for all flat indices.
     * If {@code useDerivL = true}, uses dL/dT instead of L (for GxT computation).
     *
     * For each interaction param p:
     *   Type pairSL=0: term = y[0][pA]·y[0][pB]·y[1][k]·L
     *     ∂/∂y[0][pA] += y[0][pB]·y[1][k]·L   at flat index pA
     *     ∂/∂y[0][pB] += y[0][pA]·y[1][k]·L   at flat index pB
     *     ∂/∂y[1][k]  += y[0][pA]·y[0][pB]·L  at flat index nc+k
     *   Type pairSL=1: term = y[1][pA]·y[1][pB]·y[0][k]·L
     *     ∂/∂y[1][pA] += y[1][pB]·y[0][k]·L   at flat index nc+pA
     *     ∂/∂y[1][pB] += y[1][pA]·y[0][k]·L   at flat index nc+pB
     *     ∂/∂y[0][k]  += y[1][pA]·y[1][pB]·L  at flat index k
     */
    private void addGEmGradient(double[] y, double T, double[] gx, boolean useDerivL) {
        for (CefInteractionParam p : interactions) {
            int pSL = p.pairSublattice;
            int oSL = 1 - pSL;
            double L = useDerivL ? p.dLdT() : p.L(T);
            if (L == 0.0) continue;

            int mpA = pSL * nc + p.pairA;
            int mpB = pSL * nc + p.pairB;
            int mk  = oSL * nc + p.singleIdx;

            double ypA = y[mpA], ypB = y[mpB], yk = y[mk];

            gx[mpA] += ypB * yk * L;
            gx[mpB] += ypA * yk * L;
            gx[mk]  += ypA * ypB * L;
        }
    }

    /**
     * Accumulates ∂²G_Em/∂y[m]∂y[n] into gxx[][].
     * Differentiates the gradient from {@link #addGEmGradient} once more.
     *
     * For each interaction param p with term y[pA]·y[pB]·y[k]·L:
     *   Gx[pA] = y[pB]·y[k]·L   → ∂/∂y[pB] = y[k]·L,  ∂/∂y[k] = y[pB]·L
     *   Gx[pB] = y[pA]·y[k]·L   → ∂/∂y[pA] = y[k]·L,  ∂/∂y[k] = y[pA]·L
     *   Gx[k]  = y[pA]·y[pB]·L  → ∂/∂y[pA] = y[pB]·L, ∂/∂y[pB] = y[pA]·L
     * All diagonal second derivs of G_Em are zero (no squared term in any single y).
     */
    private void addGEmHessian(double[] y, double T, double[][] gxx, boolean useDerivL) {
        for (CefInteractionParam p : interactions) {
            int pSL = p.pairSublattice;
            int oSL = 1 - pSL;
            double L = useDerivL ? p.dLdT() : p.L(T);
            if (L == 0.0) continue;

            int mpA = pSL * nc + p.pairA;
            int mpB = pSL * nc + p.pairB;
            int mk  = oSL * nc + p.singleIdx;

            double ypA = y[mpA], ypB = y[mpB], yk = y[mk];

            // ∂Gx[pA]/∂y[pB] = yk·L  and by symmetry ∂Gx[pB]/∂y[pA] = yk·L
            gxx[mpA][mpB] += yk  * L;
            gxx[mpB][mpA] += yk  * L;

            // ∂Gx[pA]/∂y[k]  = ypB·L  and ∂Gx[k]/∂y[pA] = ypB·L
            gxx[mpA][mk]  += ypB * L;
            gxx[mk][mpA]  += ypB * L;

            // ∂Gx[pB]/∂y[k]  = ypA·L  and ∂Gx[k]/∂y[pB] = ypA·L
            gxx[mpB][mk]  += ypA * L;
            gxx[mk][mpB]  += ypA * L;
        }
    }

    private void checkY(double[] y) {
        if (y.length != nip)
            throw new IllegalArgumentException(
                    "y.length=" + y.length + " but nip=" + nip);
    }
}
