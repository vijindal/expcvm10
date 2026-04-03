package system.model.cef;

import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import util.Matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * CEF (Compound Energy Formalism) implementation of {@link GibbsEnergyModel}.
 *
 * <p>Delegates CEF-specific calculations to {@link CefGibbs}.
 * All G0 storage and computation inherited from GibbsEnergyModel abstract class.
 * Internal parameters y[] are site fractions; composition x[] are mole fractions.
 */
public class CefPhaseModelAdapter extends GibbsEnergyModel {

    private static final Logger LOG = Logger.getLogger(CefPhaseModelAdapter.class.getName());

    private final CefGibbs gibbs;
    private final MagneticContribution magnetic; // null if none
    private final String phaseName_value;
    private final ArrayList<String> elementNames_value;

    /** True once y has been set to a valid non-zero state. */
    private boolean yInitialized = false;

    public CefPhaseModelAdapter(CefGibbs gibbs,
                                MagneticContribution magnetic,
                                String phaseName,
                                ArrayList<String> elements) {
        this.gibbs = gibbs;
        this.magnetic = magnetic;
        this.phaseName_value = phaseName;
        this.elementNames_value = new ArrayList<>(elements);

        // Initialize state arrays from CefGibbs
        int nip = gibbs.nip();
        int nc = elements.size();
        this.x = new double[nc];
        this.g0List = new double[nc];
        this.g0TList = new double[nc];
        this.g0PList = new double[nc];
        this.cachedGx = new double[nip];
        this.cachedGTx = new double[nip];
        this.cachedGPx = new double[nip];
        this.cachedGxx = new double[nip][nip];

        // For CEF, G0 is computed from end-member energies
        // Set G0 lists to zero as placeholder — CEF end-members handle this
        for (int i = 0; i < nc; i++) {
            this.g0List[i] = 0.0;
            this.g0TList[i] = 0.0;
            this.g0PList[i] = 0.0;
        }
    }

    @Override
    public void setInternalVars(double[] y) {
        super.setInternalVars(y);
        // Mark as initialized only if y contains non-zero values
        if (y != null) {
            for (double v : y) {
                if (v > 1e-15) { yInitialized = true; break; }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Phase Identity (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    @Override public String phaseName()        { return phaseName_value; }
    @Override public String modelType()        { return "CEF"; }
    @Override public ArrayList<String> elementNames() { return new ArrayList<>(elementNames_value); }
    @Override public String[] componentList()  { return elementNames_value.toArray(new String[0]); }
    @Override public int numComponents()       { return elementNames_value.size(); }
    @Override public int numInternalParams()   { return gibbs.nip(); }
    @Override public int numTotalParams()      { return gibbs.nip(); }

    @Override
    public double nfu() {
        double sum = 0.0;
        for (double a : gibbs.stoichiometry()) sum += a;
        return sum;
    }

    // ══════════════════════════════════════════════════════════════════
    // CEF G Evaluation (Delegates to CefGibbs)
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double evaluateG() {
        double G = gibbs.evaluate(y, T);
        if (magnetic != null) {
            double Tc = computeTc();
            double beta = computeBeta();
            G += magnetic.G(T, Tc, beta);
        }
        return G;
    }

    @Override
    public double evaluateG(double[] x, double T) {
        double[] yLocal = (yInitialized && y != null && y.length == gibbs.nip())
                        ? y : getInitialInternalVars(x);
        double G = gibbs.evaluate(yLocal, T);
        if (magnetic != null) {
            double Tc = computeTc();
            double beta = computeBeta();
            G += magnetic.G(T, Tc, beta);
        }
        return G;
    }

    @Override
    public double[] gradient(double[] x, double T) {
        double[] yLocal = (yInitialized && y != null && y.length == gibbs.nip())
                        ? y : getInitialInternalVars(x);
        double[] gxSite = gibbs.gradient(yLocal, T);
        return projectToMoleFractions(gxSite);
    }

    @Override
    public double[][] hessian(double[] x, double T) {
        double[] yLocal = (yInitialized && y != null && y.length == gibbs.nip())
                        ? y : getInitialInternalVars(x);
        return gibbs.hessian(yLocal, T);
    }

    @Override
    public double evaluateGT() {
        double[] yLocal = yInitialized && y != null ? y : getInitialInternalVars(
            x != null ? x : new double[elementNames_value.size()]);
        double[] gxtSite = gibbs.gradientDT(yLocal, T);
        // dG/dT at fixed composition = weighted sum over sublattices
        double dGdT = 0.0;
        double[] a    = gibbs.stoichiometry();
        int[]    offs = gibbs.offsets();
        int[]    ncSL = gibbs.constituentsPerSublattice();
        double   nfu  = nfu();
        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < ncSL[s]; i++) {
                double yi = yLocal[offs[s] + i];
                dGdT += a[s] * yi * gxtSite[offs[s] + i];
            }
        }
        return nfu > 0 ? dGdT / nfu : dGdT;
    }

    @Override
    public double evaluateGP() {
        return 0.0;  // CEF has no P-dependence
    }

    @Override
    public double[] evaluateGx() {
        double[] yLocal = yInitialized && y != null ? y : getInitialInternalVars(
            x != null ? x : new double[elementNames_value.size()]);
        double[] gxSite = gibbs.gradient(yLocal, T);
        return projectToMoleFractions(gxSite);
    }

    @Override
    public double[] evaluateGTx() {
        double[] yLocal = yInitialized && y != null ? y : getInitialInternalVars(
            x != null ? x : new double[elementNames_value.size()]);
        double[] gxtSite = gibbs.gradientDT(yLocal, T);
        return projectToMoleFractions(gxtSite);
    }

    @Override
    public double[] evaluateGPx() {
        return new double[gibbs.nip()];
    }

    @Override
    public double[][] evaluateGxx() {
        double[] yLocal = yInitialized && y != null ? y : getInitialInternalVars(
            x != null ? x : new double[elementNames_value.size()]);
        return gibbs.hessian(yLocal, T);
    }

    // ══════════════════════════════════════════════════════════════════
    // Internal Variable Management
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double[] getInitialInternalVars(double[] x) {
        // Equal distribution across constituents per sublattice
        double[] yInit = new double[gibbs.nip()];
        int[] nc = gibbs.constituentsPerSublattice();
        int[] offset = gibbs.offsets();
        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < nc[s]; i++) {
                yInit[offset[s] + i] = 1.0 / nc[s];
            }
        }
        return yInit;
    }

    @Override
    public double[] compositionFromInternal(double[] y) {
        // Compute mole fractions from site fractions
        // Simplified: assume 1:1 mapping of constituents to elements
        int nc = elementNames_value.size();
        double[] x = new double[nc];
        double[] a = gibbs.stoichiometry();
        int[] offs = gibbs.offsets();
        int[] ncSL = gibbs.constituentsPerSublattice();
        double total = 0.0;
        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < Math.min(ncSL[s], nc); i++) {
                if (i < nc) {
                    x[i] += a[s] * y[offs[s] + i];
                    total += a[s] * y[offs[s] + i];
                }
            }
        }
        if (total > 0) {
            for (int k = 0; k < nc; k++) x[k] /= total;
        }
        return x;
    }

    @Override
    public boolean isValid(double[] y) {
        if (y == null || y.length != gibbs.nip()) return false;
        int[] nc = gibbs.constituentsPerSublattice();
        int[] offset = gibbs.offsets();
        for (int s = 0; s < gibbs.ns(); s++) {
            double sum = 0.0;
            for (int i = 0; i < nc[s]; i++) {
                if (y[offset[s] + i] < -1e-12) return false;
                sum += y[offset[s] + i];
            }
            if (Math.abs(sum - 1.0) > 1e-6) return false;
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════
    // Full Per-Phase Computation
    // ══════════════════════════════════════════════════════════════════

    @Override
    public PhaseEquilData compute(double T, double P, double[] y,
                                  double deltaT, double deltaP,
                                  double[] mu) {
        int nip = gibbs.nip();
        int nc = elementNames_value.size();

        // Step 1: evaluate G and all derivatives
        double G = gibbs.evaluate(y, T);
        double[] Gx = gibbs.gradient(y, T);
        double[][] Gxx = gibbs.hessian(y, T);
        double[] GxT = gibbs.gradientDT(y, T);
        double[] GxP = new double[nip];  // no P-dependence

        // Step 2: assemble phase matrix M (nip+1)×(nip+1)
        double[][] M = new double[nip + 1][nip + 1];
        for (int i = 0; i < nip; i++) {
            for (int j = 0; j < nip; j++) {
                M[i][j] = Gxx[i][j];
            }
            M[i][nip] = 1.0;
            M[nip][i] = 1.0;
        }

        // Step 3: invert M, extract top-left nip×nip block → eMat
        double[][] eMat = new double[nip][nip];
        try {
            Matrix matM = new Matrix(M);
            Matrix matMI = matM.inverse();
            for (int i = 0; i < nip; i++) {
                for (int j = 0; j < nip; j++) {
                    eMat[i][j] = matMI.get(i, j);
                }
            }
        } catch (Exception e) {
            LOG.warning("Phase matrix singular for " + phaseName_value
                    + " — using zero eMat");
            // eMat remains zero matrix
        }

        // Step 4: composition response coefficients
        double[] cG = new double[nip];
        double[] cT = new double[nip];
        double[] cP = new double[nip];
        for (int i = 0; i < nip; i++) {
            for (int k = 0; k < nip; k++) {
                cG[i] -= eMat[i][k] * Gx[k];
                cT[i] -= eMat[i][k] * GxT[k];
                cP[i] -= eMat[i][k] * GxP[k];
            }
        }

        // Step 5: linearised composition change
        // Map chemical potentials (length nc) to site-fraction space (length nip)
        double[] muMapped = mapMuToSiteFractions(mu, y);
        double[] delyN = new double[nip];
        for (int i = 0; i < nip; i++) {
            delyN[i] = cG[i] + cT[i] * deltaT + cP[i] * deltaP;
            for (int j = 0; j < nip; j++) {
                delyN[i] += eMat[i][j] * muMapped[j];
            }
        }

        // For CEF: deln is the mole fraction change (convert from dely)
        double[] delnN = compositionFromInternal(delyN);
        double[] x = compositionFromInternal(y);

        // Energy parameter list
        double[] eList = buildEList(y, T, G);

        return new PhaseEquilData(G, delyN, delnN, x, eMat, cG, cT, cP, eList);
    }

    // ══════════════════════════════════════════════════════════════════
    // Output / Debugging
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void printPhaseInfo() {
        LOG.fine("Phase: " + phaseName_value + " (CEF model)");
        LOG.fine("  ns=" + gibbs.ns() + " nip=" + gibbs.nip());
        LOG.fine("  elements=" + elementNames_value);
        LOG.fine("  T=" + T + " K, P=" + P + " Pa");
        LOG.fine("  y=" + java.util.Arrays.toString(y));
    }

    @Override
    public void printDerivatives() {
        LOG.fine("G=" + cachedG);
        LOG.fine("dG/dT=" + cachedGT + ", dG/dP=" + cachedGP);
        LOG.fine("dG/dy=" + java.util.Arrays.toString(cachedGx));
    }

    // ══════════════════════════════════════════════════════════════════
    // Private Helpers
    // ══════════════════════════════════════════════════════════════════

    private double[] buildEList(double[] y, double T, double G) {
        int nip = gibbs.nip();
        double[] eList = new double[nip];
        // For now, store site fractions as energy parameters
        // Expand later to include end-member energies
        for (int i = 0; i < nip; i++) {
            eList[i] = y[i];
        }
        return eList;
    }

    /**
     * Map chemical potentials (length nc) to site-fraction space (length nip).
     * Only maps constituents that correspond to actual elements (not VA).
     * VA sites get mu=0 (chemical potential of vacancy is zero by convention).
     */
    private double[] mapMuToSiteFractions(double[] mu, double[] y) {
        int nip  = gibbs.nip();
        int nc   = elementNames_value.size();
        int[]    offs = gibbs.offsets();
        int[]    ncSL = gibbs.constituentsPerSublattice();
        double[] muY  = new double[nip];

        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < ncSL[s]; i++) {
                int flatIdx = offs[s] + i;
                // Only map if this constituent index is within the element list
                // VA constituents (index >= nc) get mu = 0
                if (i < nc) {
                    muY[flatIdx] = mu[i];
                }
                // else: muY[flatIdx] stays 0.0 (VA convention)
            }
        }
        return muY;
    }

    /**
     * Projects site-fraction gradient (length nip) to
     * mole-fraction gradient (length nc) using the chain rule:
     *   dG/dx_k = Σ_s a[s] * dG/dy_{s,k}  for k < nc[s]
     * normalized by nfu = Σ a[s].
     *
     * This is the dominant-sublattice approximation:
     * element k on sublattice s contributes a[s] * gxSite[offset[s]+k].
     * VA sites (constituent index >= nc) do not contribute.
     */
    private double[] projectToMoleFractions(double[] gxSite) {
        int nc   = elementNames_value.size();
        double[] gxMole = new double[nc];
        double[] a    = gibbs.stoichiometry();
        int[]    offs = gibbs.offsets();
        int[]    ncSL = gibbs.constituentsPerSublattice();
        double   nfu  = nfu();

        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < ncSL[s] && i < nc; i++) {
                gxMole[i] += a[s] * gxSite[offs[s] + i];
            }
        }
        // Normalize by nfu so gradient is per mole of atoms
        if (nfu > 0)
            for (int k = 0; k < nc; k++)
                gxMole[k] /= nfu;

        return gxMole;
    }

    /** Compute Tc from end-member TC parameters. Placeholder. */
    private double computeTc() { return 0.0; }

    /** Compute beta from end-member BMAGN parameters. Placeholder. */
    private double computeBeta() { return 0.0; }
}
