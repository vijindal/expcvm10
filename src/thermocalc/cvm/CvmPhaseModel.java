package thermocalc.cvm;

import util.Matrix;

/**
 * CVM phase model — translation of {@code delxGCVM[elementList, phaseName, nc, TN, PN, xN, yN, varList]}.
 *
 * <h2>Phase matrix structure</h2>
 * Unlike RK (nip+1 × nip+1) and CEF (nip+2 × nip+2), the CVM phase matrix is
 * {@code (nip+1) × (nip+1)} with one Lagrange multiplier for the composition constraint.
 *
 * The equations from {@code delxGCVM} are:
 * <pre>
 *   For i = 0..ncf-1 (CV equations):
 *     Gx[i] + GxT[i]·ΔT + GxP[i]·ΔP + Σⱼ Gxx[i][j]·Δy[j] = 0
 *
 *   For i = ncf..nip-1 (composition equations):
 *     Gx[i] + GxT[i]·ΔT + GxP[i]·ΔP + Σⱼ Gxx[i][j]·Δy[j] - μ[i-ncf] - λ = 0
 *
 *   Constraint: Σᵢ₌ₙᶜᶠ^{nip-1} Δy[i] = 0
 * </pre>
 *
 * <h2>Return structure</h2>
 * Matches Mathematica: {@code {GN, delnN, delyN, eListN, SN, HN, GmixN, SmixN, HmixN}}.
 * {@code delnN = sol[ncf..nip-1]} = the composition response (last nComp entries of sol).
 */
public class CvmPhaseModel {

    private final CvmGibbs gibbs;
    private final String   phaseName;

    public CvmPhaseModel(CvmGibbs gibbs, String phaseName) {
        this.gibbs     = gibbs;
        this.phaseName = phaseName;
    }

    // ------------------------------------------------------------------
    // Result
    // ------------------------------------------------------------------

    public static final class Result {
        public final double   GN;
        public final double[] delnN;    // composition responses, length nComp
        public final double[] delyN;    // full internal parameter responses, length nip
        public final double[] eListN;   // CEC values at T
        public final String   phaseName;
        public final double   SN;       // entropy
        public final double   HN;       // enthalpy
        public final double   GmixN;    // mixing Gibbs

        Result(double GN, double[] delnN, double[] delyN, double[] eListN,
               String phaseName, double SN, double HN, double GmixN) {
            this.GN = GN; this.delnN = delnN; this.delyN = delyN;
            this.eListN = eListN; this.phaseName = phaseName;
            this.SN = SN; this.HN = HN; this.GmixN = GmixN;
        }
    }

    // ------------------------------------------------------------------
    // Primary API
    // ------------------------------------------------------------------

    /**
     * Evaluates the CVM phase model.
     *
     * @param T       temperature in Kelvin
     * @param P       pressure (no P-dependence in current CVM model)
     * @param u2vals  internal parameters {v[0..ncf-1], x[0..nComp-1]}, length nip
     * @param cec     cluster expansion coefficients e[0..ncf-1] (T-evaluated)
     * @param deltaT  ΔT for composition response
     * @param deltaP  ΔP (= 0)
     * @param mu      chemical potentials μ[0..nComp-1]
     * @return        {@link Result}
     */
    public Result compute(double T, double P, double[] u2vals, double[] cec,
                          double deltaT, double deltaP, double[] mu) {

        CvmPhaseData d  = gibbs.data();
        int nip  = d.nip;
        int ncf  = d.ncf;
        int nComp = d.nComp;

        // ── Step 1: evaluate G and derivatives ──────────────────────
        double   GN    = gibbs.evaluate(u2vals, T, cec);
        double[] GxN   = gibbs.gradient(u2vals, T, cec);
        double[][] GxxN = gibbs.hessian(u2vals, T);
        double[] GxTN  = gibbs.gradientDT(u2vals, T, cec);
        double[] GxPN  = new double[nip];  // no P-dependence

        double SN    = gibbs.S(u2vals);
        double HN    = gibbs.H(u2vals, cec);
        double GmixN = HN - T * SN;  // mixing Gibbs = Gm (without G₀)

        // ── Step 2: assemble phase matrix M (nip+1) × (nip+1) ───────
        // CV equations (rows 0..ncf-1): no mu or lambda
        // Composition equations (rows ncf..nip-1): subtract mu[k] and lambda
        // Constraint row (row nip): sum of composition deltas = 0
        int mSize = nip + 1;
        double[][] M = new double[mSize][mSize];

        // Upper-left: Gxx block
        for (int i = 0; i < nip; i++)
            for (int j = 0; j < nip; j++)
                M[i][j] = GxxN[i][j];

        // Lagrange multiplier column/row for composition constraint
        // Only the composition rows (ncf..nip-1) have the λ term
        for (int i = ncf; i < nip; i++) {
            M[i][nip]  = 1.0;   // λ column
            M[nip][i]  = 1.0;   // constraint row
        }
        // M[nip][nip] = 0

        // ── Step 3: build RHS vector b ───────────────────────────────
        // For rows 0..ncf-1: b[i] = -(Gx[i] + GxT[i]*dT + GxP[i]*dP)
        // For rows ncf..nip-1: b[i] = -(Gx[i] + GxT[i]*dT + GxP[i]*dP - mu[i-ncf])
        // For row nip (constraint): b[nip] = 0
        double[] b = new double[mSize];
        for (int i = 0; i < ncf; i++) {
            b[i] = -(GxN[i] + GxTN[i] * deltaT + GxPN[i] * deltaP);
        }
        for (int i = ncf; i < nip; i++) {
            b[i] = -(GxN[i] + GxTN[i] * deltaT + GxPN[i] * deltaP - mu[i - ncf]);
        }
        // b[nip] = 0 (constraint: sum of composition changes = 0)

        // ── Step 4: solve M·sol = b ──────────────────────────────────
        Matrix matM = new Matrix(M);
        Matrix matB = new Matrix(b, mSize);  // column vector
        Matrix matSol;
        try {
            matSol = matM.solve(matB);
        } catch (RuntimeException e) {
            // Fallback to inverse if solve fails (singular-ish matrix)
            matSol = matM.inverse().times(matB);
        }

        double[] delyN = new double[nip];
        for (int i = 0; i < nip; i++) {
            delyN[i] = matSol.get(i, 0);
        }

        // delnN = sol[ncf..nip-1] (the composition response entries)
        double[] delnN = new double[nComp];
        for (int k = 0; k < nComp; k++) {
            delnN[k] = delyN[ncf + k];
        }

        return new Result(GN, delnN, delyN, cec.clone(), phaseName, SN, HN, GmixN);
    }

    /** Single-point G evaluation. */
    public double evaluate(double[] u2vals, double T, double[] cec) {
        return gibbs.evaluate(u2vals, T, cec);
    }

    public CvmGibbs gibbs()     { return gibbs; }
    public String   phaseName() { return phaseName; }
}
