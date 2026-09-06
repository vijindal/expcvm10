package system.model.rk;

import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import util.Matrix;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * RK (Redlich-Kister) implementation of {@link GibbsEnergyModel}.
 *
 * <p>Delegates RK-specific calculations to {@link RkGibbs}.
 * All G0 storage and computation inherited from GibbsEnergyModel abstract class.
 * For RK, internal parameters y[] and mole fractions x[] are identical.
 */
public class RkPhaseModelAdapter extends GibbsEnergyModel {

    private static final Logger LOG = Logger.getLogger(RkPhaseModelAdapter.class.getName());

    private final RkGibbs gibbs;
    private final String phaseName_value;
    private final ArrayList<String> elementNames_value;

    public RkPhaseModelAdapter(RkGibbs gibbs, String phaseName, ArrayList<String> elementNames) {
        this.gibbs = gibbs;
        this.phaseName_value = phaseName;
        this.elementNames_value = elementNames != null ? elementNames : new ArrayList<>();

        // Initialize state arrays from RkGibbs
        int nc = gibbs.nc();
        this.x = new double[nc];
        this.y = new double[nc];
        this.g0List = new double[nc];
        this.g0TList = new double[nc];
        this.g0PList = new double[nc];
        this.cachedGx = new double[nc];
        this.cachedGTx = new double[nc];
        this.cachedGPx = new double[nc];
        this.cachedGxx = new double[nc][nc];

        // Populate G0 lists from RkGibbs at reference temperature
        double refT = 298.15;  // Standard reference temperature in K
        populateG0Lists(gibbs.g0Elements(), gibbs.getPhaseName(), refT);
    }

    // ══════════════════════════════════════════════════════════════════
    // Phase Identity (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    @Override public String phaseName()        { return phaseName_value; }
    @Override public String modelType()        { return "RK"; }
    @Override public ArrayList<String> elementNames() { return new ArrayList<>(elementNames_value); }
    @Override public String[] componentList()  { return elementNames_value.toArray(new String[0]); }
    @Override public int numComponents()       { return gibbs.nc(); }
    @Override public int numInternalParams()   { return gibbs.nc(); }
    @Override public int numTotalParams()      { return gibbs.nc(); }
    @Override public double nfu()              { return 1.0; }

    // ══════════════════════════════════════════════════════════════════
    // RK-Specific G Evaluation (Delegates to RkGibbs)
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double evaluateG() {
        cachedG = gibbs.evaluate(y, T);
        return cachedG;
    }

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

    @Override
    public double evaluateGT() {
        cachedGT = gibbs.temperatureDerivative(y, T);
        return cachedGT;
    }

    @Override
    public double evaluateGP() {
        cachedGP = 0.0;  // RK has no P-dependence
        return cachedGP;
    }

    @Override
    public double[] evaluateGx() {
        cachedGx = gibbs.gradient(y, T);
        return cachedGx.clone();
    }

    @Override
    public double[] evaluateGTx() {
        cachedGTx = gibbs.gradientDT(y, T);
        return cachedGTx.clone();
    }

    @Override
    public double[] evaluateGPx() {
        int nc = gibbs.nc();
        cachedGPx = new double[nc];  // RK has no P-dependence
        return cachedGPx;
    }

    @Override
    public double[][] evaluateGxx() {
        cachedGxx = gibbs.hessian(y, T);
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
    // Full Per-Phase Computation
    // ══════════════════════════════════════════════════════════════════

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
        LOG.fine("Phase: " + phaseName_value + " (RK model)");
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
    // Private Helpers
    // ══════════════════════════════════════════════════════════════════

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
