package thermocalc.rk;

import util.Matrix;   // expCVM 10 JAMA wrapper

/**
 * Redlich-Kister phase model — translation of {@code delGsol[TN, PN, xN, yN, varList]}
 * in clusGen_25.
 *
 * <h2>What this computes</h2>
 * Given current compositions x[], temperature T, and pressure P, this class:
 * <ol>
 *   <li>Evaluates G, Gx, Gxx, GxT, GxP via {@link RkGibbs}.</li>
 *   <li>Assembles the {@code (nc+1) × (nc+1)} phase matrix M where
 *       {@code M[0..nc-1][0..nc-1] = Gxx}, last row/column are all 1 (constraint row).</li>
 *   <li>Inverts M and drops the last row/column → {@code eMat} (nc × nc).</li>
 *   <li>Computes the composition response coefficients:
 *       <pre>
 *         cG[i]  = -Σₖ eMat[i][k] · Gx[k]
 *         cT[i]  = -Σₖ eMat[i][k] · GxT[k]
 *         cP[i]  = -Σₖ eMat[i][k] · GxP[k]   (= 0, no P-dependence in RK)
 *         cN[i][j] = eMat[i][j]
 *       </pre></li>
 *   <li>Returns the linearised composition change vector:
 *       <pre>
 *         dely[i] = cG[i] + cT[i]·ΔT + cP[i]·ΔP + Σⱼ cN[i][j]·μ[j]
 *       </pre>
 *       For the RK model, deln = dely (internal and external params coincide).</li>
 * </ol>
 *
 * <h2>Return value</h2>
 * {@link Result} packages the outputs matching the Mathematica return tuple:
 * {@code {GN, delnN, delyN, eListN, phaseName}}.
 *
 * <h2>Phase matrix structure</h2>
 * <pre>
 *   M = [ Gxx  | 1 ]    size (nc+1) × (nc+1)
 *       [  1   | 0 ]
 *
 *   eM = inv(M)[0..nc-1][0..nc-1]   (drop last row and column)
 * </pre>
 *
 * <h2>Layer placement (expCVM 10 architecture)</h2>
 * This class belongs in {@code infra/} — it performs matrix operations via JAMA
 * and orchestrates the RkGibbs domain object, making it an infrastructure adapter
 * rather than a pure domain model.
 */
public class RkPhaseModel {

    private final RkGibbs gibbs;
    private final String  phaseName;

    /**
     * Constructs an RK phase model.
     *
     * @param gibbs      configured {@link RkGibbs} for this phase and component set
     * @param phaseName  identifier string (e.g. "BCC_A2", "LIQUID") — appears in Result
     */
    public RkPhaseModel(RkGibbs gibbs, String phaseName) {
        this.gibbs     = gibbs;
        this.phaseName = phaseName;
    }

    // ------------------------------------------------------------------
    // Result record — mirrors Mathematica return tuple
    // ------------------------------------------------------------------

    /**
     * Output of {@link #compute}, matching the Mathematica return:
     * {@code {GN, delnN, delyN, eListN, phaseName}}.
     *
     * <p>For the RK model {@code deln == dely} because internal and external
     * composition parameters are identical (unlike the CVM model).
     */
    public static final class Result {
        /** Molar Gibbs energy G in J/mol. */
        public final double   GN;

        /** Composition change vector delnN (= delyN for RK). Length nc. */
        public final double[] delnN;

        /** Composition change vector delyN. Length nc. */
        public final double[] delyN;

        /**
         * Energy parameter list eListN — reference energies and interaction params
         * at the current (T, x). Useful for diagnostics.
         * Order: [G0₀, G0₁, ..., G0ₙ₋₁, L_01, L_02, ..., L_tern, ...]
         */
        public final double[] eListN;

        /** Phase name identifier. */
        public final String phaseName;

        Result(double GN, double[] delnN, double[] delyN,
               double[] eListN, String phaseName) {
            this.GN        = GN;
            this.delnN     = delnN;
            this.delyN     = delyN;
            this.eListN    = eListN;
            this.phaseName = phaseName;
        }
    }

    // ------------------------------------------------------------------
    // Primary API — mirrors delGsol[TN, PN, xN, yN, varList]
    // ------------------------------------------------------------------

    /**
     * Evaluates the RK phase model at the given conditions.
     *
     * <p>The {@code varList} encodes the unknowns in the outer equilibrium loop:
     * <ul>
     *   <li>{@code varList[0]} = ΔT (temperature increment symbol)</li>
     *   <li>{@code varList[1]} = ΔP (pressure increment symbol)</li>
     *   <li>{@code varList[2][j]} = μ[j] (chemical potential for component j, 0-based)</li>
     * </ul>
     * In Java these are passed as numeric values (not symbolic), so the caller
     * provides numeric ΔT, ΔP, and μ[] for the current outer-loop iteration.
     *
     * @param T       temperature in Kelvin
     * @param P       pressure in Pa (currently no P-dependence in RK model)
     * @param x       composition array (mole fractions), length nc
     * @param deltaT  ΔT value for composition response (from outer loop)
     * @param deltaP  ΔP value for composition response (from outer loop)
     * @param mu      chemical potentials μ[0..nc-1] (from outer loop)
     * @return        {@link Result} containing G, deln, dely, eList, phaseName
     */
    public Result compute(double T, double P, double[] x,
                          double deltaT, double deltaP, double[] mu) {

        int nc = gibbs.nc();

        // ── Step 1: evaluate G and all derivatives ──────────────────────
        double   GN    = gibbs.evaluate(x, T);
        double[] GxN   = gibbs.gradient(x, T);
        double[][] GxxN = gibbs.hessian(x, T);
        double[] GxTN  = gibbs.gradientDT(x, T);
        // GxP = 0 for RK model (no pressure terms)
        double[] GxPN  = new double[nc];

        // ── Step 2: assemble phase matrix M ─────────────────────────────
        // M is (nc+1) × (nc+1):  [Gxx | 1 ; 1 | 0]
        double[][] M = new double[nc + 1][nc + 1];
        for (int i = 0; i < nc; i++) {
            for (int j = 0; j < nc; j++) {
                M[i][j] = GxxN[i][j];
            }
            M[i][nc] = 1.0;
            M[nc][i] = 1.0;
        }
        // M[nc][nc] = 0 (already initialised to 0)

        // ── Step 3: invert M, drop last row/col → eMat (nc × nc) ────────
        Matrix matM  = new Matrix(M);
        Matrix matMI = matM.inverse();
        double[][] eMat = new double[nc][nc];
        for (int i = 0; i < nc; i++)
            for (int j = 0; j < nc; j++)
                eMat[i][j] = matMI.get(i, j);

        // ── Step 4: composition response coefficients ────────────────────
        // cG[i]  = -Σₖ eMat[i][k] · Gx[k]
        // cT[i]  = -Σₖ eMat[i][k] · GxT[k]
        // cP[i]  = -Σₖ eMat[i][k] · GxP[k]
        // cN[i][j] = eMat[i][j]
        double[] cG = new double[nc];
        double[] cT = new double[nc];
        double[] cP = new double[nc];
        for (int i = 0; i < nc; i++) {
            for (int k = 0; k < nc; k++) {
                cG[i] -= eMat[i][k] * GxN[k];
                cT[i] -= eMat[i][k] * GxTN[k];
                cP[i] -= eMat[i][k] * GxPN[k];  // always 0 for RK
            }
        }

        // ── Step 5: linearised composition change ────────────────────────
        // dely[i] = cG[i] + cT[i]·ΔT + cP[i]·ΔP + Σⱼ eMat[i][j]·μ[j]
        double[] delyN = new double[nc];
        for (int i = 0; i < nc; i++) {
            delyN[i] = cG[i] + cT[i] * deltaT + cP[i] * deltaP;
            for (int j = 0; j < nc; j++) {
                delyN[i] += eMat[i][j] * mu[j];
            }
        }
        double[] delnN = delyN;  // for RK, deln == dely

        // ── Step 6: energy parameter list ────────────────────────────────
        double[] eListN = buildEList(x, T, GN);

        return new Result(GN, delnN, delyN, eListN, phaseName);
    }

    /**
     * Convenience overload without external ΔT, ΔP, μ — returns G and derivatives only.
     * Useful for single-point evaluation (e.g. plotting G surfaces).
     */
    public double evaluate(double[] x, double T) {
        return gibbs.evaluate(x, T);
    }

    /** Returns the underlying {@link RkGibbs} evaluator. */
    public RkGibbs gibbs() { return gibbs; }

    /** Returns the phase name. */
    public String phaseName() { return phaseName; }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Builds the energy parameter list eListN for diagnostics.
     * Lists: G0ᵢ(T) for each component, then all L values evaluated at (x, T).
     * Order matches the Mathematica eListN convention.
     */
    private double[] buildEList(double[] x, double T, double GN) {
        int nc = gibbs.nc();
        // Count: nc G0 values + binaries + ternaries + quaternaries
        int nBin  = gibbs.binaries().size();
        int nTern = gibbs.ternaries().size();
        int nQuat = gibbs.quaternaries().size();
        double[] eList = new double[nc + nBin + nTern + nQuat];

        int idx = 0;
        // G0 values at current T
        for (int i = 0; i < nc; i++) {
            eList[idx++] = gibbs.g0Component(i, T);
        }
        // Binary L values
        for (BinaryParam p : gibbs.binaries()) {
            eList[idx++] = p.L(x, T);
        }
        // Ternary L values
        for (TernaryParam p : gibbs.ternaries()) {
            eList[idx++] = p.L(x, T);
        }
        // Quaternary L values
        for (QuaternaryParam p : gibbs.quaternaries()) {
            eList[idx++] = p.L(T);
        }
        return eList;
    }
}
