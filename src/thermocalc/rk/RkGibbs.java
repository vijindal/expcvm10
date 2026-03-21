package thermocalc.rk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redlich-Kister (RK) molar Gibbs energy evaluator for an {@code nc}-component solution phase.
 *
 * <h2>Mathematica correspondence</h2>
 * This class is the direct Java translation of {@code calGsol[paramList]} in clusGen_25.
 * It evaluates the four components of the molar Gibbs energy and their exact analytical
 * derivatives with respect to composition and temperature.
 *
 * <h2>Gibbs energy formula</h2>
 * <pre>
 *   G(x, T) = G₀ + G_id + G_Em
 *
 *   G₀    = Σᵢ  xᵢ · G0ᵢ(T)                                   (reference energies)
 *   G_id  = R·T · Σᵢ  xᵢ · ln(xᵢ)                             (ideal mixing)
 *   G_Em  = Σᵢ<ⱼ  xᵢ·xⱼ·L_ij                                  (binary excess)
 *         + Σᵢ<ⱼ<ₖ xᵢ·xⱼ·xₖ·L_ijk                             (ternary excess)
 *         + Σᵢ<ⱼ<ₖ<ₗ xᵢ·xⱼ·xₖ·xₗ·L_ijkl                       (quaternary excess)
 * </pre>
 *
 * <h2>Analytical derivatives — derivation</h2>
 *
 * <b>First derivative ∂G/∂xₘ (Gx vector):</b>
 * <pre>
 *   ∂G₀/∂xₘ   = G0ₘ
 *   ∂G_id/∂xₘ = R·T·(ln(xₘ) + 1)
 *   ∂G_Em_bin/∂xₘ: for each pair (i,j),
 *     contribution if m=i: xⱼ·L + xᵢ·xⱼ·∂L/∂xᵢ
 *     contribution if m=j: xᵢ·L + xᵢ·xⱼ·∂L/∂xⱼ
 *     (zero otherwise)
 *   ∂G_Em_tern/∂xₘ: for each triplet (i,j,k),
 *     contribution if m=i: xⱼ·xₖ·L + xᵢ·xⱼ·xₖ·vᵢ
 *     contribution if m=j: xᵢ·xₖ·L + xᵢ·xⱼ·xₖ·vⱼ
 *     contribution if m=k: xᵢ·xⱼ·L + xᵢ·xⱼ·xₖ·vₖ
 *   ∂G_Em_quat/∂xₘ: for each quartet (i,j,k,l),
 *     contribution: (product of other 3 x's) · L
 * </pre>
 *
 * <b>Second derivative ∂²G/∂xₘ∂xₙ (Gxx matrix):</b>
 * <pre>
 *   ∂²G₀/∂xₘ∂xₙ   = 0
 *   ∂²G_id/∂xₘ∂xₙ = R·T/xₘ  if m=n,  0 otherwise
 *   ∂²G_Em_bin/∂xₘ∂xₙ: differentiate the Gx_bin expression once more.
 *     For pair (i,j), dGx[i]/dxₙ and dGx[j]/dxₙ are evaluated explicitly.
 *   ∂²G_Em_tern/∂xₘ∂xₙ: differentiate the Gx_tern expression once more.
 *   ∂²G_Em_quat/∂xₘ∂xₙ: differentiate the Gx_quat expression once more.
 * </pre>
 *
 * <b>Mixed derivative ∂²G/∂xₘ∂T (GxT vector):</b>
 * <pre>
 *   ∂(∂G₀/∂xₘ)/∂T   = dG0ₘ/dT
 *   ∂(∂G_id/∂xₘ)/∂T = R·(ln(xₘ) + 1)
 *   ∂(∂G_Em/∂xₘ)/∂T = same structure as ∂G_Em/∂xₘ but with dL/dT instead of L
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>
 *   // Build from parameters (e.g. read from tdb)
 *   RkGibbs rk = new RkGibbs(nc, g0Func, binParams, ternParams, quatParams);
 *
 *   double   G   = rk.evaluate(x, T);
 *   double[] Gx  = rk.gradient(x, T);
 *   double[][] Gxx = rk.hessian(x, T);
 *   double[] GxT = rk.gradientDT(x, T);
 * </pre>
 *
 * <h2>Layer placement (expCVM 10 architecture)</h2>
 * This class belongs in {@code domain/} — it is a pure thermodynamic model
 * with no knowledge of file I/O, UI, or external frameworks.
 */
public class RkGibbs {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    /** Universal gas constant J/(mol·K). */
    public static final double R = 8.3144598;

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Number of components. */
    private final int nc;

    /**
     * Reference Gibbs energies G0ᵢ(T) for each component.
     * Indexed [0..nc-1]. These are functions of T (evaluated via the unary layer).
     */
    private final G0Function[] g0;

    /** Binary interaction parameters (all pairs i<j). */
    private final List<BinaryParam> binaries;

    /** Ternary interaction parameters (all triplets i<j<k). */
    private final List<TernaryParam> ternaries;

    /** Quaternary interaction parameters (all quartets i<j<k<l). */
    private final List<QuaternaryParam> quaternaries;

    // ------------------------------------------------------------------
    // Functional interface for reference energies
    // ------------------------------------------------------------------

    /**
     * Provides G0(T) and dG0/dT for one component.
     * Typically backed by {@code UnaryGibbs.gibbs(element, phase, T)}.
     */
    @FunctionalInterface
    public interface G0Function {
        /** Molar Gibbs energy G0(T) in J/mol. */
        double G0(double T);

        /**
         * Temperature derivative dG0/dT in J/(mol·K).
         * Default: numerical central difference with step 0.01 K.
         * Override with an analytical implementation when available.
         */
        default double dG0dT(double T) {
            double h = 0.01;
            return (G0(T + h) - G0(T - h)) / (2.0 * h);
        }
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Creates an RkGibbs evaluator.
     *
     * @param nc           number of components
     * @param g0           reference Gibbs energy functions, length nc
     * @param binaries     binary RK parameters (all i<j pairs needed)
     * @param ternaries    ternary RK parameters (may be empty)
     * @param quaternaries quaternary RK parameters (may be empty)
     */
    public RkGibbs(int nc, G0Function[] g0,
                   List<BinaryParam>     binaries,
                   List<TernaryParam>    ternaries,
                   List<QuaternaryParam> quaternaries) {
        if (g0.length != nc)
            throw new IllegalArgumentException("g0.length must equal nc");
        this.nc          = nc;
        this.g0          = g0.clone();
        this.binaries    = Collections.unmodifiableList(new ArrayList<>(binaries));
        this.ternaries   = Collections.unmodifiableList(new ArrayList<>(ternaries));
        this.quaternaries = Collections.unmodifiableList(new ArrayList<>(quaternaries));
    }

    // ------------------------------------------------------------------
    // Primary API — mirrors calGsol and the derivative calls in delGsol
    // ------------------------------------------------------------------

    /**
     * Evaluates G(x, T) = G₀ + G_id + G_Em in J/mol.
     * Direct translation of {@code calGsol[paramList]} in Mathematica.
     *
     * @param x  mole fractions, length nc, must sum to 1
     * @param T  temperature in Kelvin
     * @return   molar Gibbs energy in J/mol
     */
    public double evaluate(double[] x, double T) {
        checkX(x);
        return g0(x, T) + gId(x, T) + gEmBin(x, T) + gEmTern(x, T) + gEmQuat(x, T);
    }

    /**
     * Computes the composition gradient ∂G/∂xₘ for m = 0..nc-1.
     * Corresponds to {@code Gx = Table[D[G, ipl[[i]]], {i, nip}]} in Mathematica.
     *
     * @param x  mole fractions
     * @param T  temperature
     * @return   gradient vector, length nc
     */
    public double[] gradient(double[] x, double T) {
        checkX(x);
        double[] gx = new double[nc];

        // G₀ contribution: ∂G₀/∂xₘ = G0ₘ(T)
        for (int m = 0; m < nc; m++) {
            gx[m] += g0[m].G0(T);
        }

        // G_id contribution: ∂G_id/∂xₘ = R·T·(ln(xₘ) + 1)
        for (int m = 0; m < nc; m++) {
            gx[m] += R * T * (Math.log(x[m]) + 1.0);
        }

        // G_Em_bin: for each pair (i,j)
        for (BinaryParam p : binaries) {
            int i = p.idxI, j = p.idxJ;
            double xi = x[i], xj = x[j];
            double L    = p.L(x, T);
            double dLdi = p.dLdx(x, T, i);  // ∂L/∂xᵢ
            double dLdj = p.dLdx(x, T, j);  // ∂L/∂xⱼ

            // ∂(xᵢ·xⱼ·L)/∂xᵢ = xⱼ·L + xᵢ·xⱼ·∂L/∂xᵢ
            gx[i] += xj * L + xi * xj * dLdi;
            // ∂(xᵢ·xⱼ·L)/∂xⱼ = xᵢ·L + xᵢ·xⱼ·∂L/∂xⱼ
            gx[j] += xi * L + xi * xj * dLdj;
        }

        // G_Em_tern: for each triplet (i,j,k)
        for (TernaryParam p : ternaries) {
            int i = p.idxI, j = p.idxJ, k = p.idxK;
            double xi = x[i], xj = x[j], xk = x[k];
            double L  = p.L(x, T);
            double vi = p.v(i, T), vj = p.v(j, T), vk = p.v(k, T);
            double xijk = xi * xj * xk;

            // ∂(xᵢ·xⱼ·xₖ·L)/∂xₘ = (∂xᵢxⱼxₖ/∂xₘ)·L + xᵢxⱼxₖ·vₘ
            gx[i] += xj * xk * L + xijk * vi;
            gx[j] += xi * xk * L + xijk * vj;
            gx[k] += xi * xj * L + xijk * vk;
        }

        // G_Em_quat: for each quartet (i,j,k,l)
        for (QuaternaryParam p : quaternaries) {
            int i = p.idxI, j = p.idxJ, k = p.idxK, l = p.idxL;
            double xi = x[i], xj = x[j], xk = x[k], xl = x[l];
            double L = p.L(T);

            // ∂(xᵢxⱼxₖxₗ·L)/∂xₘ: the product of the other 3 × L
            gx[i] += xj * xk * xl * L;
            gx[j] += xi * xk * xl * L;
            gx[k] += xi * xj * xl * L;
            gx[l] += xi * xj * xk * L;
        }

        return gx;
    }

    /**
     * Computes the composition Hessian ∂²G/∂xₘ∂xₙ (nc × nc matrix).
     * Corresponds to {@code Gxx = Table[D[G, ipl[[j]], ipl[[i]]], {j,nip},{i,nip}]} in Mathematica.
     *
     * <p>Note: Mathematica's {@code D[G, ipl[[j]], ipl[[i]]]} is ∂²G/∂x_j∂x_i,
     * so {@code Gxx[j][i]} in the Mathematica convention.  This method returns
     * {@code gxx[m][n] = ∂²G/∂xₘ∂xₙ} (standard math convention, symmetric).
     *
     * @param x  mole fractions
     * @param T  temperature
     * @return   symmetric nc×nc Hessian matrix
     */
    public double[][] hessian(double[] x, double T) {
        checkX(x);
        double[][] gxx = new double[nc][nc];

        // G_id: ∂²G_id/∂xₘ² = R·T/xₘ  (diagonal only)
        for (int m = 0; m < nc; m++) {
            gxx[m][m] += R * T / x[m];
        }

        // G_Em_bin: differentiate Gx_bin[m] w.r.t. xₙ
        for (BinaryParam p : binaries) {
            int i = p.idxI, j = p.idxJ;
            double xi = x[i], xj = x[j];
            double L    = p.L(x, T);
            double dLdi = p.dLdx(x, T, i);
            double dLdj = p.dLdx(x, T, j);
            double d    = xi - xj;

            // Coefficients for second RK term: ∂(xi-xj)/∂xi=+1, ∂/∂xj=-1
            // ∂L/∂xi = Σs≥1 s·Ls·(xi-xj)^(s-1), ∂²L/∂xi² = Σs≥2 s(s-1)Ls(xi-xj)^(s-2)
            // ∂²L/∂xi∂xj = -∂²L/∂xi² (sign flip from ∂(xi-xj)/∂xj = -1)
            double d2Ldii = d2Ldxx(p, x, T, +1, +1);
            double d2Ldij = d2Ldxx(p, x, T, +1, -1);
            double d2Ldjj = d2Ldxx(p, x, T, -1, -1);

            // Gx[i] = xj·L + xi·xj·∂L/∂xi
            // ∂Gx[i]/∂xi = ∂xj/∂xi·L + xj·∂L/∂xi + xj·∂L/∂xi + xi·xj·∂²L/∂xi²
            //             = 0 + 2·xj·dLdi + xi·xj·d2Ldii
            gxx[i][i] += 2.0 * xj * dLdi + xi * xj * d2Ldii;

            // ∂Gx[i]/∂xj = L + xj·∂L/∂xj·... wait, careful product rule
            // Gx[i] = xj·L(xi,xj) + xi·xj·dL/dxi
            // ∂/∂xj: L + xj·dLdj + xi·L + xi·xj·d2Ldij ... No — re-expand:
            // = L + xj·(dLdi·∂xi/∂xj + dLdj) + xi·(dLdi + xj·d2Ldij)  [∂xi/∂xj = 0]
            // = L + xj·dLdj + xi·dLdi + xi·xj·d2Ldij
            gxx[i][j] += L + xj * dLdj + xi * dLdi + xi * xj * d2Ldij;
            gxx[j][i]  = gxx[i][j];   // symmetry

            // Gx[j] = xi·L + xi·xj·dLdj
            // ∂Gx[j]/∂xj = 2·xi·dLdj + xi·xj·d2Ldjj
            gxx[j][j] += 2.0 * xi * dLdj + xi * xj * d2Ldjj;
        }

        // G_Em_tern: differentiate Gx_tern[m] w.r.t. xₙ
        for (TernaryParam p : ternaries) {
            int i = p.idxI, j = p.idxJ, k = p.idxK;
            double xi = x[i], xj = x[j], xk = x[k];
            double L  = p.L(x, T);
            double vi = p.v(i, T), vj = p.v(j, T), vk = p.v(k, T);

            // F = xi·xj·xk·(vi·xi + vj·xj + vk·xk)
            //
            // ∂F/∂xi = xj·xk·L + xi·xj·xk·vi
            //
            // ∂²F/∂xi∂xi: differentiate ∂F/∂xi w.r.t. xi
            //   = xj·xk·vi + xj·xk·vi = 2·xj·xk·vi
            gxx[i][i] += 2.0 * xj * xk * vi;
            gxx[j][j] += 2.0 * xi * xk * vj;
            gxx[k][k] += 2.0 * xi * xj * vk;

            // ∂²F/∂xi∂xj: differentiate ∂F/∂xi w.r.t. xj
            //   = xk·L + xj·xk·vj + xi·xk·vi + xi·xj·xk·0   [∂vi/∂xj = 0]
            //   = xk·(L + xj·vj + xi·vi)
            gxx[i][j] += xk * (L + xi * vi + xj * vj);
            gxx[j][i]  = gxx[i][j];

            // ∂²F/∂xi∂xk: differentiate ∂F/∂xi w.r.t. xk
            //   = xj·L + xj·xk·vk + xi·xj·vi
            //   = xj·(L + xk·vk + xi·vi)
            gxx[i][k] += xj * (L + xi * vi + xk * vk);
            gxx[k][i]  = gxx[i][k];

            // ∂²F/∂xj∂xk: differentiate ∂F/∂xj w.r.t. xk
            //   ∂F/∂xj = xi·xk·L + xi·xj·xk·vj
            //   ∂/∂xk  = xi·L + xi·xk·vk + xi·xj·vj
            //           = xi·(L + xk·vk + xj·vj)
            gxx[j][k] += xi * (L + xj * vj + xk * vk);
            gxx[k][j]  = gxx[j][k];
        }

        // G_Em_quat: differentiate Gx_quat[m] w.r.t. xₙ
        for (QuaternaryParam p : quaternaries) {
            int i = p.idxI, j = p.idxJ, k = p.idxK, l = p.idxL;
            double xi = x[i], xj = x[j], xk = x[k], xl = x[l];
            double L = p.L(T);

            // Gx[i] = xj·xk·xl·L  → ∂/∂xn: only non-zero for n ∈ {j,k,l}
            gxx[i][j] += xk * xl * L;  gxx[j][i] = gxx[i][j];
            gxx[i][k] += xj * xl * L;  gxx[k][i] = gxx[i][k];
            gxx[i][l] += xj * xk * L;  gxx[l][i] = gxx[i][l];
            // Gx[j] = xi·xk·xl·L  → ∂/∂xn: n ∈ {k,l} (i already set by symmetry)
            gxx[j][k] += xi * xl * L;  gxx[k][j] = gxx[j][k];
            gxx[j][l] += xi * xk * L;  gxx[l][j] = gxx[j][l];
            // Gx[k] = xi·xj·xl·L  → ∂/∂xl
            gxx[k][l] += xi * xj * L;  gxx[l][k] = gxx[k][l];
            // Diagonal terms (e.g. ∂Gx[i]/∂xi = 0 for pure quaternary product)
            // all zero for quartet cross-derivatives involving the same index twice
        }

        return gxx;
    }

    /**
     * Computes the mixed derivative ∂²G/∂xₘ∂T = ∂(Gx)/∂T (GxT vector).
     * Corresponds to {@code GxT = D[Gx, T]} in Mathematica.
     *
     * <p>Key insight: for the excess terms, GxT has exactly the same structure
     * as Gx but with each L coefficient replaced by its T-derivative (dL/dT),
     * i.e. {@code b[s]} instead of {@code a[s] + b[s]·T}.
     *
     * @param x  mole fractions
     * @param T  temperature
     * @return   GxT vector, length nc
     */
    public double[] gradientDT(double[] x, double T) {
        checkX(x);
        double[] gxt = new double[nc];

        // ∂(∂G₀/∂xₘ)/∂T = dG0ₘ/dT
        for (int m = 0; m < nc; m++) {
            gxt[m] += g0[m].dG0dT(T);
        }

        // ∂(∂G_id/∂xₘ)/∂T = R·(ln(xₘ) + 1)   [from R·T·(...), d/dT gives R·(...)]
        for (int m = 0; m < nc; m++) {
            gxt[m] += R * (Math.log(x[m]) + 1.0);
        }

        // G_Em_bin: same structure as gradient but use dL/dT instead of L, dLdx/dT instead of dLdx
        // Since ∂L/∂T = dLdT (independent of T in these coefficients),
        // and ∂(dLdx)/∂T = dLdx evaluated with b[] instead of a[]+b[]*T:
        for (BinaryParam p : binaries) {
            int i = p.idxI, j = p.idxJ;
            double xi = x[i], xj = x[j];
            double dLdT  = p.dLdT(x);        // T-derivative of L itself
            double dLdxi_dT = p.dLdx_dT(x, i); // T-derivative of ∂L/∂xi
            double dLdxj_dT = p.dLdx_dT(x, j);

            // ∂(xj·L + xi·xj·∂L/∂xi)/∂T = xj·dLdT + xi·xj·(∂L/∂xi)/∂T
            gxt[i] += xj * dLdT + xi * xj * dLdxi_dT;
            gxt[j] += xi * dLdT + xi * xj * dLdxj_dT;
        }

        // G_Em_tern: same structure, L→dL/dT, vi→dvi/dT = bi
        for (TernaryParam p : ternaries) {
            int i = p.idxI, j = p.idxJ, k = p.idxK;
            double xi = x[i], xj = x[j], xk = x[k];
            double dLdT = p.dLdT(x);           // T-derivative of L_ijk at these compositions
            double dvi_dT = p.b[0], dvj_dT = p.b[1], dvk_dT = p.b[2];
            double xijk = xi * xj * xk;

            // ∂(xj·xk·L + xijk·vi)/∂T = xj·xk·dLdT + xijk·dvi/dT
            gxt[i] += xj * xk * dLdT + xijk * dvi_dT;
            gxt[j] += xi * xk * dLdT + xijk * dvj_dT;
            gxt[k] += xi * xj * dLdT + xijk * dvk_dT;
        }

        // G_Em_quat: L = a + b·T → dL/dT = b
        for (QuaternaryParam p : quaternaries) {
            int i = p.idxI, j = p.idxJ, k = p.idxK, l = p.idxL;
            double xi = x[i], xj = x[j], xk = x[k], xl = x[l];
            double dLdT = p.dLdT();

            gxt[i] += xj * xk * xl * dLdT;
            gxt[j] += xi * xk * xl * dLdT;
            gxt[k] += xi * xj * xl * dLdT;
            gxt[l] += xi * xj * xk * dLdT;
        }

        return gxt;
    }

    // ------------------------------------------------------------------
    // Individual energy contributions (useful for debugging)
    // ------------------------------------------------------------------

    /** G₀ = Σᵢ xᵢ·G0ᵢ(T). */
    public double g0(double[] x, double T) {
        double g = 0.0;
        for (int i = 0; i < nc; i++) g += x[i] * g0[i].G0(T);
        return g;
    }

    /** G_id = R·T·Σᵢ xᵢ·ln(xᵢ). */
    public double gId(double[] x, double T) {
        double g = 0.0;
        for (int i = 0; i < nc; i++) g += x[i] * Math.log(x[i]);
        return R * T * g;
    }

    /** Binary excess Gibbs energy. */
    public double gEmBin(double[] x, double T) {
        double g = 0.0;
        for (BinaryParam p : binaries)
            g += x[p.idxI] * x[p.idxJ] * p.L(x, T);
        return g;
    }

    /** Ternary excess Gibbs energy. */
    public double gEmTern(double[] x, double T) {
        double g = 0.0;
        for (TernaryParam p : ternaries)
            g += x[p.idxI] * x[p.idxJ] * x[p.idxK] * p.L(x, T);
        return g;
    }

    /** Quaternary excess Gibbs energy. */
    public double gEmQuat(double[] x, double T) {
        double g = 0.0;
        for (QuaternaryParam p : quaternaries)
            g += x[p.idxI] * x[p.idxJ] * x[p.idxK] * x[p.idxL] * p.L(T);
        return g;
    }

    /** Returns number of components. */
    public int nc() { return nc; }

    /** Returns the binary interaction parameters (unmodifiable). */
    public List<BinaryParam> binaries() { return binaries; }

    /** Returns the ternary interaction parameters (unmodifiable). */
    public List<TernaryParam> ternaries() { return ternaries; }

    /** Returns the quaternary interaction parameters (unmodifiable). */
    public List<QuaternaryParam> quaternaries() { return quaternaries; }

    /**
     * Returns G0ᵢ(T) for component i.
     * Exposed for diagnostic use (e.g. building eListN).
     */
    public double g0Component(int i, double T) {
        return g0[i].G0(T);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Validates composition array length. */
    private void checkX(double[] x) {
        if (x.length != nc)
            throw new IllegalArgumentException(
                    "Composition array length " + x.length + " != nc=" + nc);
    }

    /**
     * ∂²L/∂xₘ∂xₙ for a binary parameter, where signM = ∂d/∂xₘ and signN = ∂d/∂xₙ.
     * d = xi - xj, so signM = +1 for m=i, -1 for m=j.
     * Only non-zero when both m and n are in {i,j}.
     */
    private double d2Ldxx(BinaryParam p, double[] x, double T, double signM, double signN) {
        // ∂²L/∂xₘ∂xₙ = signM·signN · Σs≥2 s(s-1)·Ls·(xi-xj)^(s-2)
        double d  = x[p.idxI] - x[p.idxJ];
        double dp = 1.0;
        double d2L = 0.0;
        for (int s = 2; s < p.a.length; s++) {
            d2L += (p.a[s] + p.b[s] * T) * s * (s - 1) * dp;
            dp  *= d;
        }
        return signM * signN * d2L;
    }
}
