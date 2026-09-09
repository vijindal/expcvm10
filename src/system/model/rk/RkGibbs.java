package system.model.rk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import system.model.unary.ElementGibbs;
import util.Matrix;

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
 * <p>G0 evaluation uses {@link ElementGibbs} instances provided at construction,
 * evaluated at the phase specified in the constructor.</p>
 *
 * <h2>Layer placement (expCVM 10 architecture)</h2>
 * This class belongs in {@code domain/} — it is a pure thermodynamic model
 * with no knowledge of file I/O, UI, or external frameworks.
 */
public class RkGibbs extends GibbsEnergyModel {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(RkGibbs.class.getName());

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
     * Reference Gibbs energy evaluators for each component.
     * Indexed [0..nc-1]. These are ElementGibbs instances evaluated at the phase stored in phaseName.
     */
    private final ElementGibbs[] g0;

    /** Phase name for G0 evaluation (e.g., "BCC_A2", "LIQUID"). */
    private final String phaseName;

    /** Binary interaction parameters (all pairs i<j). */
    private final List<BinaryParam> binaries;

    /** Ternary interaction parameters (all triplets i<j<k). */
    private final List<TernaryParam> ternaries;

    /** Quaternary interaction parameters (all quartets i<j<k<l). */
    private final List<QuaternaryParam> quaternaries;

    /** Element symbols, ordered to match composition/site-fraction indices. */
    private final ArrayList<String> elementNames_value;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Creates an RkGibbs evaluator.
     *
     * @param nc           number of components
     * @param g0           reference Gibbs energy evaluators (ElementGibbs instances), length nc
     * @param phaseName    phase name for G0 evaluation (e.g., "BCC_A2", "LIQUID")
     * @param binaries     binary RK parameters (all i<j pairs needed)
     * @param ternaries    ternary RK parameters (may be empty)
     * @param quaternaries quaternary RK parameters (may be empty)
     * @param elementNames ordered element symbols, length nc
     */
    public RkGibbs(int nc, ElementGibbs[] g0, String phaseName,
                   List<BinaryParam>     binaries,
                   List<TernaryParam>    ternaries,
                   List<QuaternaryParam> quaternaries,
                   List<String>          elementNames) {
        if (g0 == null || g0.length != nc)
            throw new IllegalArgumentException("g0 must be non-null with length equal to nc");
        if (phaseName == null || phaseName.isEmpty())
            throw new IllegalArgumentException("phaseName must be non-null and non-empty");
        this.nc          = nc;
        this.g0          = g0.clone();
        this.phaseName   = phaseName;
        this.binaries    = Collections.unmodifiableList(new ArrayList<>(binaries));
        this.ternaries   = Collections.unmodifiableList(new ArrayList<>(ternaries));
        this.quaternaries = Collections.unmodifiableList(new ArrayList<>(quaternaries));
        this.elementNames_value = elementNames != null
                ? new ArrayList<>(elementNames) : new ArrayList<>();

        // Initialize GibbsEnergyModel state arrays
        this.x = new double[nc];
        this.y = new double[nc];
        this.g0List = new double[nc];
        this.g0TList = new double[nc];
        this.g0PList = new double[nc];
        this.cachedGx = new double[nc];
        this.cachedGTx = new double[nc];
        this.cachedGPx = new double[nc];
        this.cachedGxx = new double[nc][nc];

        // Populate G0 lists at reference temperature
        double refT = 298.15;
        populateG0Lists(g0Elements(), getPhaseName(), refT);
    }

    // ══════════════════════════════════════════════════════════════════
    // Phase Identity (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    @Override public String phaseName()        { return phaseName; }
    @Override public String modelType()        { return "RK"; }
    @Override public ArrayList<String> elementNames() { return new ArrayList<>(elementNames_value); }
    @Override public String[] componentList()  { return elementNames_value.toArray(new String[0]); }
    @Override public int numComponents()       { return nc; }
    @Override public int numInternalParams()   { return nc; }
    @Override public int numTotalParams()      { return nc; }
    @Override public double nfu()              { return 1.0; }

    // ══════════════════════════════════════════════════════════════════
    // Legacy Composition-Space G Evaluation (GibbsEnergyModel contract)
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double evaluateG() {
        cachedG = evaluate(y, T);
        return cachedG;
    }

    @Override
    public double evaluateG(double[] x, double T) {
        return evaluate(x, T);
    }

    @Override
    public double evaluateGT() {
        cachedGT = temperatureDerivative(y, T);
        return cachedGT;
    }

    @Override
    public double evaluateGP() {
        cachedGP = 0.0;  // RK has no P-dependence
        return cachedGP;
    }

    @Override
    public double[] evaluateGx() {
        cachedGx = gradient(y, T);
        return cachedGx.clone();
    }

    @Override
    public double[] evaluateGTx() {
        cachedGTx = gradientDT(y, T);
        return cachedGTx.clone();
    }

    @Override
    public double[] evaluateGPx() {
        cachedGPx = new double[nc];  // RK has no P-dependence
        return cachedGPx;
    }

    @Override
    public double[][] evaluateGxx() {
        cachedGxx = hessian(y, T);
        return cloneMatrix(cachedGxx);
    }

    // ══════════════════════════════════════════════════════════════════
    // Internal Variable Management (RK: y = x)
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double[] getInitialInternalVars(double[] x) {
        return x.clone();  // RK: y = x
    }

    @Override
    public double[] compositionFromInternal(double[] y) {
        return y.clone();  // RK: x = y
    }

    @Override
    public boolean isValid(double[] y) {
        double sum = 0;
        for (int i = 0; i < y.length; i++) {
            if (y[i] < -1e-12) return false;
            sum += y[i];
        }
        return Math.abs(sum - 1.0) < 1e-6;
    }

    // ══════════════════════════════════════════════════════════════════
    // Site-Fraction Thermodynamics (RK: one sublattice, Y = X, no
    // vacancies -- the ns=1 specialization of the general CEF contract)
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double siteEnergy(double T, double[] y) {
        return evaluate(y, T);
    }

    @Override
    public double[] siteGradient(double T, double[] y) {
        return gradient(y, T);
    }

    @Override
    public double[][] siteHessian(double T, double[] y) {
        return hessian(y, T);
    }

    /** RK: M_A(Y) = Y_A directly (one constituent per element, nfu=1). */
    @Override
    public double[] elementAmounts(double[] y) {
        return y.clone();
    }

    /** RK: dM_A/dY_i is the identity matrix (M_A = Y_A). */
    @Override
    public double[][] elementAmountsJacobian() {
        double[][] identity = new double[nc][nc];
        for (int i = 0; i < nc; i++) {
            identity[i][i] = 1.0;
        }
        return identity;
    }

    /** RK: a single sublattice with site ratio 1.0. */
    @Override
    public double[] siteRatios() {
        return new double[] { 1.0 };
    }

    @Override
    public int numSublattices() {
        return 1;
    }

    @Override
    public int numSiteVariables() {
        return nc;
    }

    @Override
    public int[] sublatticeOffsets() {
        return new int[] { 0 };
    }

    @Override
    public int[] constituentsPerSublattice() {
        return new int[] { nc };
    }

    // ══════════════════════════════════════════════════════════════════
    // Full Per-Phase Computation
    // ══════════════════════════════════════════════════════════════════

    @Override
    public PhaseEquilData compute(double T, double P, double[] y,
                                  double deltaT, double deltaP, double[] mu) {
        // Step 1: evaluate G and all derivatives
        double     GN    = evaluate(y, T);
        double[]   GxN   = gradient(y, T);
        double[][] GxxN  = hessian(y, T);
        double[]   GxTN  = gradientDT(y, T);
        double[]   GxPN  = new double[nc];  // no P-dependence in RK

        // Step 2: assemble phase matrix M (nc+1)×(nc+1)
        double[][] M = new double[nc + 1][nc + 1];
        for (int i = 0; i < nc; i++) {
            for (int j = 0; j < nc; j++) {
                M[i][j] = GxxN[i][j];
            }
            M[i][nc] = 1.0;
            M[nc][i] = 1.0;
        }

        // Step 3: invert M, extract top-left nc×nc block → eMat
        Matrix matM  = new Matrix(M);
        Matrix matMI = matM.inverse();
        double[][] eMat = new double[nc][nc];
        for (int i = 0; i < nc; i++)
            for (int j = 0; j < nc; j++)
                eMat[i][j] = matMI.get(i, j);

        // Step 4: composition response coefficients
        double[] cG = new double[nc];
        double[] cT = new double[nc];
        double[] cP = new double[nc];
        for (int i = 0; i < nc; i++) {
            for (int k = 0; k < nc; k++) {
                cG[i] -= eMat[i][k] * GxN[k];
                cT[i] -= eMat[i][k] * GxTN[k];
                cP[i] -= eMat[i][k] * GxPN[k];
            }
        }

        // Step 5: linearised composition change
        double[] delyN = new double[nc];
        for (int i = 0; i < nc; i++) {
            delyN[i] = cG[i] + cT[i] * deltaT + cP[i] * deltaP;
            for (int j = 0; j < nc; j++) {
                delyN[i] += eMat[i][j] * mu[j];
            }
        }

        // For RK, deln == dely and x == y
        double[] delnN = delyN;
        double[] x = y.clone();

        // For RK: M^α_A = x^α_A (since nfu=1)
        double[] mA = x.clone();

        // For RK: eMatNC = eMat (already in composition space)
        double[][] eMatNC = eMat;

        // Energy parameter list
        double[] eList = buildEList(y, T, GN);

        return new PhaseEquilData(GN, delyN, delnN, x, mA, eMat, eMatNC, cG, cT, cP, eList);
    }

    // ══════════════════════════════════════════════════════════════════
    // Output / Debugging
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void printPhaseInfo() {
        LOG.fine("Phase: " + phaseName + " (RK model)");
        LOG.fine("Components: " + elementNames_value);
        LOG.fine("T = " + T + " K, P = " + P + " Pa");
        LOG.fine("Composition x = " + java.util.Arrays.toString(x));
    }

    @Override
    public void printDerivatives() {
        LOG.fine("G = " + cachedG);
        LOG.fine("dG/dT = " + cachedGT + ", dG/dP = " + cachedGP);
        LOG.fine("dG/dx = " + java.util.Arrays.toString(cachedGx));
        LOG.finer("d2G/dTdx = " + java.util.Arrays.toString(cachedGTx));
        LOG.finer("d2G/dPdx = " + java.util.Arrays.toString(cachedGPx));
        LOG.finer("d2G/dxdx = " + java.util.Arrays.deepToString(cachedGxx));
    }

    // ══════════════════════════════════════════════════════════════════
    // Private Helpers (per-phase computation)
    // ══════════════════════════════════════════════════════════════════

    private double[] buildEList(double[] x, double T, double GN) {
        int nBin  = binaries.size();
        int nTern = ternaries.size();
        int nQuat = quaternaries.size();
        double[] eList = new double[nc + nBin + nTern + nQuat];

        int idx = 0;
        for (int i = 0; i < nc; i++) {
            eList[idx++] = g0Component(i, T);
        }
        for (BinaryParam p : binaries) {
            eList[idx++] = p.L(x, T);
        }
        for (TernaryParam p : ternaries) {
            eList[idx++] = p.L(x, T);
        }
        for (QuaternaryParam p : quaternaries) {
            eList[idx++] = p.L(T);
        }
        return eList;
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
            gx[m] += g0[m].gibbs(phaseName, T);
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

        // ∂(∂G₀/∂xₘ)/∂T = dG0ₘ/dT (numerical derivative)
        double h = 0.01;
        for (int m = 0; m < nc; m++) {
            double dG0dT = (g0[m].gibbs(phaseName, T + h)
                          - g0[m].gibbs(phaseName, T - h)) / (2.0 * h);
            gxt[m] += dG0dT;
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
        for (int i = 0; i < nc; i++) g += x[i] * g0[i].gibbs(phaseName, T);
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

    /** Direct temperature derivative at fixed composition. */
    public double temperatureDerivative(double[] x, double T) {
        double derivative = 0.0;
        double h = 0.01;
        for (int i = 0; i < nc; i++) {
            derivative += x[i] * (g0[i].gibbs(phaseName, T + h)
                    - g0[i].gibbs(phaseName, T - h)) / (2.0 * h);
            if (x[i] > 1e-300) derivative += R * x[i] * Math.log(x[i]);
        }
        for (BinaryParam p : binaries)
            derivative += x[p.idxI] * x[p.idxJ] * p.dLdT(x);
        for (TernaryParam p : ternaries)
            derivative += x[p.idxI] * x[p.idxJ] * x[p.idxK] * p.dLdT(x);
        for (QuaternaryParam p : quaternaries)
            derivative += x[p.idxI] * x[p.idxJ] * x[p.idxK] * x[p.idxL] * p.dLdT();
        return derivative;
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
        return g0[i].gibbs(phaseName, T);
    }

    /**
     * Returns the ElementGibbs array for all components.
     * Used by {@link system.model.GibbsEnergyModel#populateG0Lists}.
     */
    public ElementGibbs[] g0Elements() {
        return g0.clone();
    }

    /**
     * Returns the phase name used for G0 evaluation.
     */
    public String getPhaseName() {
        return phaseName;
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
