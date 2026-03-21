package thermocalc.cef;

import util.Matrix;

/**
 * CEF (Compound Energy Formalism) phase model — translation of
 * {@code delxGC15[elementList, numComp, TN, PN, xN, yN, varList]} in clusGen_25.
 *
 * <h2>What this computes</h2>
 * Given site fractions y[], temperature T, and pressure P:
 * <ol>
 *   <li>Evaluates G, Gx (nip), Gxx (nip×nip), GxT (nip), GxP (nip=0) via {@link CefGibbs}.</li>
 *   <li>Builds the {@code (nip+ns) × (nip+ns)} phase matrix M.</li>
 *   <li>Inverts M; drops the last {@code ns} rows/columns → {@code eMat} (nip×nip).</li>
 *   <li>Computes composition response coefficients cG, cT, cP, cN.</li>
 *   <li>Builds {@code delyN} — site fraction response vector.</li>
 *   <li>Converts to {@code delnN} via the {@code ny} matrix:
 *       {@code deln[k] = Σᵢ ny[k][i]·dely[i]} where {@code ny[k][i] = dn[k]/dy[i]}.</li>
 * </ol>
 *
 * <h2>Phase matrix structure (C15, ns=2 sublattices)</h2>
 * <pre>
 *   The active Mathematica code uses {@code Solve[phaseEq, phaseVar]}.
 *   The equivalent explicit matrix (commented out in Mathematica) is:
 *
 *   M = [ Gxx     | b₁ | b₂ ]    size (nip+2) × (nip+2)
 *       [ b₁ᵀ    |  0 |  0 ]
 *       [ b₂ᵀ    |  0 |  0 ]
 *
 *   where b₁ has 1s in positions 0..nc-1 (SL1 constraint: Σᵢ y[0][i] = 1)
 *         b₂ has 1s in positions nc..2nc-1 (SL2 constraint: Σⱼ y[1][j] = 1)
 * </pre>
 *
 * <h2>ny matrix</h2>
 * The composition-to-site-fraction conversion matrix:
 * <pre>
 *   n[k] = 2·y[0][k] + 1·y[1][k]   for k = 0..nc-1
 *   ny[k][i] = ∂n[k]/∂y[i]
 *     = a₁ = 2  if i = k         (SL1 index: flat idx = k)
 *     = a₂ = 1  if i = nc+k      (SL2 index)
 *     = 0       otherwise
 * </pre>
 *
 * <h2>Return value</h2>
 * {@link Result} matches the Mathematica tuple:
 * {@code {GN, delnN, delyN, eListN, phaseName, SN, HN, GmixN, SmixN, HmixN}}.
 *
 * <h2>Architecture placement (expCVM 10)</h2>
 * {@code infra/} — uses JAMA {@code Matrix} from {@code util/}.
 */
public class CefPhaseModel {

    private static final int NS = 2;           // number of sublattices (C15)
    private static final double[] A = {2.0, 1.0};  // stoichiometric coefficients

    private final CefGibbs gibbs;
    private final String   phaseName;

    /**
     * Constructs a CEF phase model.
     *
     * @param gibbs      configured {@link CefGibbs} evaluator
     * @param phaseName  identifier string (e.g. "LAVES_C15_CEF")
     */
    public CefPhaseModel(CefGibbs gibbs, String phaseName) {
        this.gibbs     = gibbs;
        this.phaseName = phaseName;
    }

    // ------------------------------------------------------------------
    // Result record
    // ------------------------------------------------------------------

    /**
     * Output of {@link #compute}, matching the Mathematica return tuple:
     * {@code {GN, delnN, delyN, eListN, phaseName, SN, HN, GmixN, SmixN, HmixN}}.
     */
    public static final class Result {
        /** Molar Gibbs energy G in J/mol. */
        public final double   GN;
        /** Composition (mole number) change vector, length nc. */
        public final double[] delnN;
        /** Site-fraction change vector, length nip = 2·nc. */
        public final double[] delyN;
        /** Energy parameter list [G[0][0], G[0][1], ..., L values evaluated at T]. */
        public final double[] eListN;
        /** Phase identifier string. */
        public final String   phaseName;
        /** Entropy S = -dG/dT in J/(mol·K). */
        public final double   SN;
        /** Enthalpy H = G + T·S in J/mol. */
        public final double   HN;

        Result(double GN, double[] delnN, double[] delyN,
               double[] eListN, String phaseName, double SN, double HN) {
            this.GN        = GN;
            this.delnN     = delnN;
            this.delyN     = delyN;
            this.eListN    = eListN;
            this.phaseName = phaseName;
            this.SN        = SN;
            this.HN        = HN;
        }
    }

    // ------------------------------------------------------------------
    // Primary API
    // ------------------------------------------------------------------

    /**
     * Evaluates the CEF phase model.
     *
     * @param T       temperature in Kelvin
     * @param P       pressure in Pa (no P-dependence in current model)
     * @param y       site fractions, flat array length nip = 2·nc
     * @param deltaT  ΔT increment for composition response
     * @param deltaP  ΔP increment (= 0, no pressure term)
     * @param mu      chemical potentials μ[0..nc-1]
     * @return        {@link Result}
     */
    public Result compute(double T, double P, double[] y,
                          double deltaT, double deltaP, double[] mu) {

        int nc  = gibbs.nc();
        int nip = gibbs.nip();

        // ── Step 1: G and all derivatives ────────────────────────────
        double     GN    = gibbs.evaluate(y, T);
        double[]   GxN   = gibbs.gradient(y, T);
        double[][] GxxN  = gibbs.hessian(y, T);
        double[]   GxTN  = gibbs.gradientDT(y, T);
        double[]   GxPN  = new double[nip];   // no P-dependence

        // S = -dG/dT,  H = G + T*S
        double GTN = gT(y, T);
        double SN  = -GTN;
        double HN  = GN + T * SN;

        // ── Step 2: assemble phase matrix M (nip+ns) × (nip+ns) ─────
        // Upper-left block: Gxx
        // Constraint columns/rows: SL1 constraint covers rows 0..nc-1,
        //                          SL2 constraint covers rows nc..2nc-1
        int mSize = nip + NS;
        double[][] M = new double[mSize][mSize];
        for (int i = 0; i < nip; i++)
            for (int j = 0; j < nip; j++)
                M[i][j] = GxxN[i][j];

        // SL1 constraint (Lagrange col/row nip):  y[0][0]+...+y[0][nc-1] = 1
        for (int i = 0; i < nc; i++) {
            M[i][nip]   = 1.0;
            M[nip][i]   = 1.0;
        }
        // SL2 constraint (Lagrange col/row nip+1): y[1][0]+...+y[1][nc-1] = 1
        for (int i = nc; i < nip; i++) {
            M[i][nip + 1]   = 1.0;
            M[nip + 1][i]   = 1.0;
        }
        // Corner 2×2 block remains zero

        // ── Step 3: invert M, drop last NS rows/cols → eMat (nip×nip) ─
        Matrix matM  = new Matrix(M);
        Matrix matMI = matM.inverse();
        double[][] eMat = new double[nip][nip];
        for (int i = 0; i < nip; i++)
            for (int j = 0; j < nip; j++)
                eMat[i][j] = matMI.get(i, j);

        // ── Step 4: response coefficients ─────────────────────────────
        double[] cG = new double[nip];
        double[] cT = new double[nip];
        double[] cP = new double[nip];
        for (int i = 0; i < nip; i++) {
            for (int k = 0; k < nip; k++) {
                cG[i] -= eMat[i][k] * GxN[k];
                cT[i] -= eMat[i][k] * GxTN[k];
                cP[i] -= eMat[i][k] * GxPN[k];  // zero
            }
        }

        // cN[i][k] = Σⱼ eMat[i][j]·ny[k][j]   (ny encodes dn/dy)
        // ny[k][j]: dn[k]/dy[j] = a₁ if j=k (SL1), a₂ if j=nc+k (SL2), else 0
        double[][] cN = new double[nip][nc];
        for (int i = 0; i < nip; i++) {
            for (int k = 0; k < nc; k++) {
                // SL1 contribution: ny[k][k]=a₁=2
                cN[i][k] += eMat[i][k]      * A[0];
                // SL2 contribution: ny[k][nc+k]=a₂=1
                cN[i][k] += eMat[i][nc + k] * A[1];
            }
        }

        // ── Step 5: site-fraction response ────────────────────────────
        // dely[i] = cG[i] + cT[i]·ΔT + cP[i]·ΔP + Σₖ cN[i][k]·μ[k]
        double[] delyN = new double[nip];
        for (int i = 0; i < nip; i++) {
            delyN[i] = cG[i] + cT[i] * deltaT + cP[i] * deltaP;
            for (int k = 0; k < nc; k++) {
                delyN[i] += cN[i][k] * mu[k];
            }
        }

        // ── Step 6: composition change deln ───────────────────────────
        // deln[k] = a₁·dely[k] + a₂·dely[nc+k]
        double[] delnN = new double[nc];
        for (int k = 0; k < nc; k++) {
            delnN[k] = A[0] * delyN[k] + A[1] * delyN[nc + k];
        }

        // ── Step 7: energy parameter list ─────────────────────────────
        double[] eListN = buildEList(y, T, nc, nip);

        return new Result(GN, delnN, delyN, eListN, phaseName, SN, HN);
    }

    /** Evaluates G at (y, T) — convenience single-point method. */
    public double evaluate(double[] y, double T) {
        return gibbs.evaluate(y, T);
    }

    /** Returns the underlying {@link CefGibbs} evaluator. */
    public CefGibbs gibbs() { return gibbs; }

    /** Returns the phase name. */
    public String phaseName() { return phaseName; }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * ∂G/∂T = dG₀/dT + dG_id/dT + dG_Em/dT.
     * Used to compute S = -dG/dT and H = G + T·S.
     *
     * dG₀/dT   = Σᵢ Σⱼ y[0][i]·y[1][j]·dG[i][j]/dT
     * dG_id/dT = R·(2·Σᵢ y[0][i]·ln(y[0][i]) + 1·Σⱼ y[1][j]·ln(y[1][j]))
     * dG_Em/dT = Σ_p y[pA]·y[pB]·y[k]·dL/dT
     */
    private double gT(double[] y, double T) {
        int nc = gibbs.nc();
        double gt = 0.0;

        // dG₀/dT
        for (int i = 0; i < nc; i++)
            for (int j = 0; j < nc; j++)
                gt += y[i] * y[nc + j] * gibbs.endMember(i, j).dGdT(T);

        // dG_id/dT = G_id / T  (since G_id = R·T·(entropy terms))
        gt += gibbs.gId(y, T) / T;

        // dG_Em/dT
        for (CefInteractionParam p : gibbs.interactions()) {
            int pSL = p.pairSublattice;
            int oSL = 1 - pSL;
            gt += y[pSL * nc + p.pairA]
                * y[pSL * nc + p.pairB]
                * y[oSL * nc + p.singleIdx]
                * p.dLdT();
        }

        return gt;
    }

    /**
     * Builds the energy parameter list eListN for diagnostics.
     * Order: G[0][0], G[0][1], ..., G[nc-1][nc-1], then all L values at T.
     */
    private double[] buildEList(double[] y, double T, int nc, int nip) {
        int nG = nc * nc;
        int nL = gibbs.interactions().size();
        double[] eList = new double[nG + nL];
        int idx = 0;
        for (int i = 0; i < nc; i++)
            for (int j = 0; j < nc; j++)
                eList[idx++] = gibbs.endMember(i, j).G(T);
        for (CefInteractionParam p : gibbs.interactions())
            eList[idx++] = p.L(T);
        return eList;
    }
}
