package system.model.cef;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Compound Energy Formalism (CEF) Gibbs energy evaluator for general ns sublattices.
 *
 * <h2>Gibbs energy formula</h2>
 * <pre>
 *   G = G₀ + G_id + G_Em
 *
 *   G₀   = Σ over all end members em: (∏ₛ y[offset[s]+idx_em[s]]) · G_em(T)
 *
 *   G_id = R·T · Σₛ a[s] · Σᵢ y[offset[s]+i] · ln(y[offset[s]+i])
 *
 *   G_Em = Σ_interactions y[pSL][pA]·y[pSL][pB]·y[oSL][k]·L(T)
 * </pre>
 *
 * <p><b>Known limitation</b>: {@code CefInteractionParam} uses
 * {@code oSL = 1 - pSL}, which assumes each interaction parameter
 * pairs exactly two sublattices. This covers all standard TDB databases.</p>
 *
 * <h2>Internal parameter layout</h2>
 * The flat index for sublattice s, constituent i is:
 * <pre>
 *   m = offset[s] + i
 *   where offset[0] = 0,  offset[s] = offset[s-1] + nc[s-1]
 *   nip = offset[ns-1] + nc[ns-1]  = Σₛ nc[s]
 * </pre>
 *
 * <h2>End-member radix ordering</h2>
 * End member em is decoded by:
 * <pre>
 *   stride[0] = 1
 *   stride[s] = stride[s-1] * nc[s-1]
 *   idx[s] = (em / stride[s]) % nc[s]
 * </pre>
 * so sublattice 0 is the least-significant digit.
 */
public class CefGibbs {

    // ------------------------------------------------------------------
    // Class-level constants
    // ------------------------------------------------------------------

    /** Maximum number of sublattices supported. */
    public static final int MAX_SUBLATTICES = 9;

    /** Universal gas constant J/(mol·K). */
    public static final double R = 8.3144598;

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    /** Number of sublattices. */
    private final int ns;

    /** Stoichiometric coefficients, length ns. */
    private final double[] a;

    /** Constituents per sublattice, length ns. */
    private final int[] nc;

    /** Flat-index offset for each sublattice: offset[s] = sum of nc[0..s-1], length ns. */
    private final int[] offset;

    /** Total site fractions = sum of nc[s]. */
    private final int nip;

    /** End members; length = product of nc[s]; indexed in mixed-radix order (SL0 least significant). */
    private final CefEndMember[] g0;

    /** Interaction parameters (unmodifiable). */
    private final List<CefInteractionParam> interactions;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Constructs a general ns-sublattice CEF Gibbs evaluator.
     *
     * @param sublatticeCoeffs         stoichiometric coefficients a[s], length ns
     * @param constituentsPerSublattice nc[s], length ns
     * @param g0                        end-member array, length = ∏ nc[s], in mixed-radix order
     * @param interactions              CEF interaction parameters (may be empty)
     */
    public CefGibbs(double[] sublatticeCoeffs,
                    int[] constituentsPerSublattice,
                    CefEndMember[] g0,
                    List<CefInteractionParam> interactions) {
        if (sublatticeCoeffs.length != constituentsPerSublattice.length)
            throw new IllegalArgumentException(
                "sublatticeCoeffs.length=" + sublatticeCoeffs.length
                + " != constituentsPerSublattice.length=" + constituentsPerSublattice.length);
        if (sublatticeCoeffs.length > MAX_SUBLATTICES)
            throw new IllegalArgumentException(
                "Number of sublattices " + sublatticeCoeffs.length
                + " exceeds MAX_SUBLATTICES=" + MAX_SUBLATTICES);
        int prod = 1;
        for (int n : constituentsPerSublattice) prod *= n;
        if (g0.length != prod)
            throw new IllegalArgumentException(
                "g0.length=" + g0.length + " but product of nc[s]=" + prod);

        this.ns     = sublatticeCoeffs.length;
        this.a      = sublatticeCoeffs.clone();
        this.nc     = constituentsPerSublattice.clone();
        this.offset = new int[ns];
        offset[0]   = 0;
        for (int s = 1; s < ns; s++)
            offset[s] = offset[s - 1] + nc[s - 1];
        this.nip          = offset[ns - 1] + nc[ns - 1];
        this.g0           = g0.clone();
        this.interactions = Collections.unmodifiableList(new ArrayList<>(interactions));
    }

    // ------------------------------------------------------------------
    // Primary API
    // ------------------------------------------------------------------

    /**
     * Evaluates G(y, T) = G₀ + G_id + G_Em.
     *
     * @param y  flat site-fraction vector, length nip
     * @param T  temperature in Kelvin
     * @return   molar Gibbs energy in J/mol
     */
    public double evaluate(double[] y, double T) {
        checkY(y);
        return g0Ref(y, T) + gId(y, T) + gEm(y, T);
    }

    /**
     * Composition gradient ∂G/∂y[m] for all flat indices m = offset[s] + i.
     *
     * @param y  flat site-fraction vector
     * @param T  temperature in Kelvin
     * @return   gradient vector, length nip
     */
    public double[] gradient(double[] y, double T) {
        checkY(y);
        double[] gx = new double[nip];

        // ── G₀ contribution ──────────────────────────────────────────────
        // For each end member em, ∂G₀/∂y[m] = (∏_{t≠s} y[offset[t]+idx[t]]) · G_em(T)
        // where m = offset[s] + idx[s]
        int totalEM = totalEndMembers();
        int[] stride = computeStrides();

        for (int em = 0; em < totalEM; em++) {
            int[] idx = decodeEM(em, stride);
            double yProd = 1.0;
            for (int s = 0; s < ns; s++)
                yProd *= y[offset[s] + idx[s]];
            double gVal = g0[em].G(T);
            for (int s = 0; s < ns; s++) {
                int m = offset[s] + idx[s];
                gx[m] += (yProd / y[m]) * gVal;
            }
        }

        // ── G_id contribution ─────────────────────────────────────────────
        // ∂G_id/∂y[m] = R·T·a[s]·(ln(y[m]) + 1)
        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < nc[s]; i++) {
                int m = offset[s] + i;
                gx[m] += R * T * a[s] * (Math.log(y[m]) + 1.0);
            }
        }

        // ── G_Em contribution ─────────────────────────────────────────────
        addGEmGradient(y, T, gx, false);

        return gx;
    }

    /**
     * Hessian ∂²G/∂y[m]∂y[n], returned as (nip × nip) matrix.
     *
     * @param y  flat site-fraction vector
     * @param T  temperature in Kelvin
     * @return   symmetric nip×nip Hessian
     * <p>Note: cross-sublattice terms are skipped when any site fraction
     * y[m] &lt; 1e-300 to avoid Inf/NaN at boundary compositions.</p>
     */
    public double[][] hessian(double[] y, double T) {
        checkY(y);
        double[][] gxx = new double[nip][nip];

        // ── G₀: cross-sublattice terms ───────────────────────────────────
        int totalEM = totalEndMembers();
        int[] stride = computeStrides();

        for (int em = 0; em < totalEM; em++) {
            int[] idx = decodeEM(em, stride);
            double yProd = 1.0;
            for (int s = 0; s < ns; s++)
                yProd *= y[offset[s] + idx[s]];
            double gVal = g0[em].G(T);
            for (int s1 = 0; s1 < ns; s1++) {
                for (int s2 = s1 + 1; s2 < ns; s2++) {
                    int m1 = offset[s1] + idx[s1];
                    int m2 = offset[s2] + idx[s2];
                    if (y[m1] < 1e-300 || y[m2] < 1e-300) continue;
                    double partialProduct = yProd / (y[m1] * y[m2]);
                    gxx[m1][m2] += partialProduct * gVal;
                    gxx[m2][m1] += partialProduct * gVal;
                }
            }
        }

        // ── G_id: diagonal only ───────────────────────────────────────────
        // ∂²G_id/∂y[m]² = R·T·a[s]/y[m]
        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < nc[s]; i++) {
                int m = offset[s] + i;
                gxx[m][m] += R * T * a[s] / y[m];
            }
        }

        // ── G_Em ──────────────────────────────────────────────────────────
        addGEmHessian(y, T, gxx, false);

        return gxx;
    }

    /**
     * Mixed derivative ∂²G/∂y[m]∂T (GxT vector).
     *
     * @param y  flat site-fraction vector
     * @param T  temperature in Kelvin
     * @return   GxT vector, length nip
     */
    public double[] gradientDT(double[] y, double T) {
        checkY(y);
        double[] gxt = new double[nip];

        // ── G₀: use dGdT instead of G ─────────────────────────────────────
        int totalEM = totalEndMembers();
        int[] stride = computeStrides();

        for (int em = 0; em < totalEM; em++) {
            int[] idx = decodeEM(em, stride);
            double yProd = 1.0;
            for (int s = 0; s < ns; s++)
                yProd *= y[offset[s] + idx[s]];
            double dGdT = g0[em].dGdT(T);
            for (int s = 0; s < ns; s++) {
                int m = offset[s] + idx[s];
                gxt[m] += (yProd / y[m]) * dGdT;
            }
        }

        // ── G_id: ∂/∂T of R·T·a[s]·(ln(y)+1) = R·a[s]·(ln(y)+1) ─────────
        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < nc[s]; i++) {
                int m = offset[s] + i;
                gxt[m] += R * a[s] * (Math.log(y[m]) + 1.0);
            }
        }

        // ── G_Em: same structure but dL/dT instead of L ───────────────────
        addGEmGradient(y, T, gxt, true);

        return gxt;
    }

    // ------------------------------------------------------------------
    // Individual energy components (for diagnostics)
    // ------------------------------------------------------------------

    /**
     * Reference energy G₀ using iterative mixed-radix enumeration over all end members.
     */
    public double g0Ref(double[] y, double T) {
        int totalEM = totalEndMembers();
        int[] stride = computeStrides();
        double g = 0.0;
        for (int em = 0; em < totalEM; em++) {
            int[] idx = decodeEM(em, stride);
            double yProd = 1.0;
            for (int s = 0; s < ns; s++)
                yProd *= y[offset[s] + idx[s]];
            g += yProd * g0[em].G(T);
        }
        return g;
    }

    /**
     * Ideal mixing entropy contribution G_id = R·T · Σₛ a[s] · Σᵢ y[m]·ln(y[m]).
     */
    public double gId(double[] y, double T) {
        double g = 0.0;
        for (int s = 0; s < ns; s++) {
            double sub = 0.0;
            for (int i = 0; i < nc[s]; i++) {
                double yi = y[offset[s] + i];
                if (yi > 1e-300) sub += yi * Math.log(yi);
            }
            g += a[s] * sub;
        }
        return R * T * g;
    }

    /**
     * Excess mixing energy G_Em = Σ_interactions y[pSL][pA]·y[pSL][pB]·y[oSL][k]·L(T).
     */
    public double gEm(double[] y, double T) {
        double g = 0.0;
        for (CefInteractionParam p : interactions) {
            int pSL = p.pairSublattice;
            int oSL = 1 - pSL;
            g += y[offset[pSL] + p.pairA]
               * y[offset[pSL] + p.pairB]
               * y[offset[oSL] + p.singleIdx]
               * p.L(T);
        }
        return g;
    }

    /** Direct temperature derivative at fixed normalized site fractions. */
    public double temperatureDerivative(double[] y, double T) {
        double derivative = 0.0;
        int totalEM = totalEndMembers();
        int[] stride = computeStrides();
        for (int em = 0; em < totalEM; em++) {
            int[] idx = decodeEM(em, stride);
            double yProd = 1.0;
            for (int s = 0; s < ns; s++) yProd *= y[offset[s] + idx[s]];
            derivative += yProd * g0[em].dGdT(T);
        }
        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < nc[s]; i++) {
                double yi = y[offset[s] + i];
                if (yi > 1e-300) derivative += R * a[s] * yi * Math.log(yi);
            }
        }
        for (CefInteractionParam p : interactions) {
            int pSL = p.pairSublattice;
            double pair = y[offset[pSL] + p.pairA] * y[offset[pSL] + p.pairB];
            double delta = y[offset[pSL] + p.pairA] - y[offset[pSL] + p.pairB];
            double term = pair;
            if (p.singleIdx >= 0) term *= y[offset[1 - pSL] + p.singleIdx];
            derivative += term * p.dLdT();
        }
        return derivative;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /** Returns number of sublattices. */
    public int ns() { return ns; }

    /** Returns number of constituents on sublattice 0 (backward-compatible single-nc accessor). */
    public int nc() { return nc[0]; }

    /** Returns total number of internal parameters (Σ nc[s]). */
    public int nip() { return nip; }

    /** Returns a copy of stoichiometric coefficients. */
    public double[] stoichiometry() { return a.clone(); }

    /** Returns a copy of constituents-per-sublattice array. */
    public int[] constituentsPerSublattice() { return nc.clone(); }

    /** Returns a copy of the sublattice offsets array. */
    public int[] offsets() { return offset.clone(); }

    /** Returns the interaction parameter list (unmodifiable). */
    public List<CefInteractionParam> interactions() { return interactions; }

    /**
     * Returns end member at (sublattice-0 species i, sublattice-1 species j).
     * Uses mixed-radix index: em = i + j * nc[0].
     */
    public CefEndMember endMember(int i, int j) { return g0[i + j * nc[0]]; }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Total number of end members = ∏ nc[s]. */
    private int totalEndMembers() {
        int total = 1;
        for (int s = 0; s < ns; s++) total *= nc[s];
        return total;
    }

    /**
     * Computes mixed-radix strides: stride[0]=1, stride[s]=stride[s-1]*nc[s-1].
     */
    private int[] computeStrides() {
        int[] stride = new int[ns];
        stride[0] = 1;
        for (int s = 1; s < ns; s++)
            stride[s] = stride[s - 1] * nc[s - 1];
        return stride;
    }

    /**
     * Decodes end-member index em into constituent indices idx[0..ns-1].
     * idx[s] = (em / stride[s]) % nc[s]
     */
    private int[] decodeEM(int em, int[] stride) {
        int[] idx = new int[ns];
        for (int s = 0; s < ns; s++)
            idx[s] = (em / stride[s]) % nc[s];
        return idx;
    }

    /**
     * Accumulates ∂G_Em/∂y[m] into gx[] for all flat indices.
     * If {@code useDerivL = true}, uses dL/dT instead of L (for GxT computation).
     *
     * For each interaction param p:
     *   Type pairSL=0: term = y[0][pA]·y[0][pB]·y[1][k]·L
     *     ∂/∂y[0][pA] += y[0][pB]·y[1][k]·L   at flat index offset[0]+pA
     *     ∂/∂y[0][pB] += y[0][pA]·y[1][k]·L   at flat index offset[0]+pB
     *     ∂/∂y[1][k]  += y[0][pA]·y[0][pB]·L  at flat index offset[1]+k
     *   Type pairSL=1: term = y[1][pA]·y[1][pB]·y[0][k]·L
     *     ∂/∂y[1][pA] += y[1][pB]·y[0][k]·L   at flat index offset[1]+pA
     *     ∂/∂y[1][pB] += y[1][pA]·y[0][k]·L   at flat index offset[1]+pB
     *     ∂/∂y[0][k]  += y[1][pA]·y[1][pB]·L  at flat index offset[0]+k
     */
    private void addGEmGradient(double[] y, double T, double[] gx, boolean useDerivL) {
        for (CefInteractionParam p : interactions) {
            int pSL = p.pairSublattice;
            double L = useDerivL ? p.dLdT() : p.L(T);
            if (L == 0.0) continue;

            int mpA = offset[pSL] + p.pairA;
            int mpB = offset[pSL] + p.pairB;
            int oSL = 1 - pSL;
            int mk  = offset[oSL] + p.singleIdx;

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
            double L = useDerivL ? p.dLdT() : p.L(T);
            if (L == 0.0) continue;

            int mpA = offset[pSL] + p.pairA;
            int mpB = offset[pSL] + p.pairB;
            int oSL = 1 - pSL;
            int mk  = offset[oSL] + p.singleIdx;

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
