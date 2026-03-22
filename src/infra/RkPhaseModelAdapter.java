package infra;

import domain.PhaseEquilData;
import domain.PhaseModelPort;
import thermocalc.rk.BinaryParam;
import thermocalc.rk.QuaternaryParam;
import thermocalc.rk.RkGibbs;
import thermocalc.rk.TernaryParam;
import util.Matrix;

/**
 * Adapter implementing {@link PhaseModelPort} for the Redlich-Kister model.
 *
 * <p>Delegates to {@link RkGibbs} for all thermodynamic evaluations.
 * For RK, internal parameters y[] and mole fractions x[] are identical,
 * so {@code compositionFromInternal} is the identity mapping.
 */
public class RkPhaseModelAdapter implements PhaseModelPort {

    private final RkGibbs gibbs;
    private final String  phaseName;

    public RkPhaseModelAdapter(RkGibbs gibbs, String phaseName) {
        this.gibbs     = gibbs;
        this.phaseName = phaseName;
    }

    @Override public String phaseName()        { return phaseName; }
    @Override public String modelType()        { return "RK"; }
    @Override public int    numComponents()    { return gibbs.nc(); }
    @Override public int    numInternalParams() { return gibbs.nc(); }
    @Override public double nfu()              { return 1.0; }

    // ------------------------------------------------------------------
    // G evaluation
    // ------------------------------------------------------------------

    @Override
    public double evaluateG(double[] x, double T) {
        return gibbs.evaluate(x, T);
    }

    @Override
    public double[] gradient(double[] x, double T) {
        return gibbs.gradient(x, T);
    }

    @Override
    public double[][] hessian(double[] x, double T) {
        return gibbs.hessian(x, T);
    }

    // ------------------------------------------------------------------
    // Internal variable management
    // ------------------------------------------------------------------

    @Override
    public double[] compositionFromInternal(double[] y) {
        // RK: internal params = mole fractions
        return y.clone();
    }

    @Override
    public double[] getInitialInternalVars(double[] x) {
        // RK: internal params = mole fractions
        return x.clone();
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

    // ------------------------------------------------------------------
    // Full per-phase computation
    // ------------------------------------------------------------------

    @Override
    public PhaseEquilData compute(double T, double P, double[] y,
                                  double deltaT, double deltaP, double[] mu) {
        int nc = gibbs.nc();

        // Step 1: evaluate G and all derivatives
        double     GN    = gibbs.evaluate(y, T);
        double[]   GxN   = gibbs.gradient(y, T);
        double[][] GxxN  = gibbs.hessian(y, T);
        double[]   GxTN  = gibbs.gradientDT(y, T);
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

        // Energy parameter list
        double[] eList = buildEList(y, T, GN);

        return new PhaseEquilData(GN, delyN, delnN, x, eMat, cG, cT, cP, eList);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private double[] buildEList(double[] x, double T, double GN) {
        int nc = gibbs.nc();
        int nBin  = gibbs.binaries().size();
        int nTern = gibbs.ternaries().size();
        int nQuat = gibbs.quaternaries().size();
        double[] eList = new double[nc + nBin + nTern + nQuat];

        int idx = 0;
        for (int i = 0; i < nc; i++) {
            eList[idx++] = gibbs.g0Component(i, T);
        }
        for (BinaryParam p : gibbs.binaries()) {
            eList[idx++] = p.L(x, T);
        }
        for (TernaryParam p : gibbs.ternaries()) {
            eList[idx++] = p.L(x, T);
        }
        for (QuaternaryParam p : gibbs.quaternaries()) {
            eList[idx++] = p.L(T);
        }
        return eList;
    }
}
