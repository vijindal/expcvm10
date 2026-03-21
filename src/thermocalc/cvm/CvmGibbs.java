package thermocalc.cvm;

/**
 * CVM (Cluster Variation Method) Gibbs energy evaluator.
 *
 * <h2>Mathematica correspondence</h2>
 * This class implements the numerical evaluation equivalent of:
 * <pre>
 *   calGmcecvm[phaseData]  → entropy S and enthalpy H expressions
 *   calGcecvm[elementList, phaseName, phaseData, TN] → G, Gx, Gxx, GxT
 * </pre>
 * All symbolic algebra done in Mathematica is replaced by direct numerical
 * evaluation using the pre-computed data in {@link CvmPhaseData}.
 *
 * <h2>Gibbs energy formula</h2>
 * <pre>
 *   G(v, x, T) = G₀(x, T) + Gm(v, T)
 *
 *   G₀(x, T) = Σᵢ xᵢ · GHSER_i(T)     (reference energies, linear in x)
 *
 *   Gm(v, T)  = H(v) - T·S(v)
 *
 *   H(v) = Σᵢ₌₁ⁿᶜᶠ  e[i] · v[i]        (linear in CECs e[] and CVs v[])
 *
 *   S(v) = -R · Σ_{itc=1}^{tcdis}
 *              kbdis[itc] · msdis[itc] ·
 *              Σ_{inc=1}^{lc[itc]}
 *                mh[itc][inc] ·
 *                Σ_{icv=1}^{lcv[itc][inc]}
 *                  wcv[itc][inc][icv] · CV[itc][inc][icv] · ln(CV[itc][inc][icv])
 * </pre>
 * where each CV is:
 * <pre>
 *   CV[itc][inc][icv] = Σⱼ cmat[itc][inc][icv][j] · u[j]
 *   u[j] = Σₖ cfCoeffs[j][k] · u2List[k]
 *   u2List = {v[0], ..., v[ncf-1], x[0], ..., x[nComp-1]}
 * </pre>
 *
 * <h2>Derivatives</h2>
 * All derivatives are analytical:
 * <pre>
 *   ∂G/∂v[m]  (m=0..ncf-1):  ∂H/∂v[m] - T·∂S/∂v[m]
 *   ∂G/∂x[m]  (m=0..nComp-1): ∂G₀/∂x[m] - T·∂S/∂x[m]
 *
 *   ∂H/∂v[m] = e[m]   (H is linear in v[])
 *
 *   ∂S/∂u2[m] = -R · Σ_{itc,inc,icv} kbdis·msdis·mh·wcv
 *                     · (∂CV/∂u2[m]) · (ln(CV) + 1)
 *   ∂CV[itc][inc][icv]/∂u2[m] = Σⱼ cmat[...][j] · cfCoeffs[j][m]
 *
 *   ∂²G/∂u2[m]∂u2[n] = -T · ∂²S/∂u2[m]∂u2[n]   (H is linear → no second deriv)
 *   ∂²S/∂u2[m]∂u2[n] = -R · Σ_{itc,inc,icv} kbdis·msdis·mh·wcv
 *                         · (∂CV/∂u2[m]) · (∂CV/∂u2[n]) / CV
 *
 *   ∂²G/∂u2[m]∂T = ∂(∂G/∂u2[m])/∂T = -∂S/∂u2[m]
 * </pre>
 *
 * <h2>Internal parameter convention</h2>
 * The flat parameter vector passed to all methods is:
 * <pre>
 *   u2vals[0..ncf-1]         = CV variables {v4AB, v3AB, ...}
 *   u2vals[ncf..nip-1]       = compositions {xA, xB, ...}
 * </pre>
 * This matches Mathematica's u2List and iplN=yN in delxGCVM.
 */
public class CvmGibbs {

    private static final double R = 8.3144598;

    private final CvmPhaseData data;

    /** Reference Gibbs energy functions per component (GHSER). */
    private final thermocalc.rk.RkGibbs.G0Function[] ghser;

    /**
     * Constructs a CVM Gibbs evaluator.
     *
     * @param data   parsed phase data from .nb file
     * @param ghser  GHSER functions, one per component (0-based)
     */
    public CvmGibbs(CvmPhaseData data,
                    thermocalc.rk.RkGibbs.G0Function[] ghser) {
        if (ghser.length != data.nComp)
            throw new IllegalArgumentException("ghser.length must equal nComp");
        this.data  = data;
        this.ghser = ghser;
    }

    // ------------------------------------------------------------------
    // Primary API
    // ------------------------------------------------------------------

    /**
     * Evaluates G(u2vals, T) = G₀(x, T) + H(v) - T·S(v).
     *
     * @param u2vals  {v[0..ncf-1], x[0..nComp-1]}, length = nip
     * @param T       temperature in Kelvin
     * @param cec     cluster expansion coefficients e[0..ncf-1] at this T
     * @return        G in J/mol
     */
    public double evaluate(double[] u2vals, double T, double[] cec) {
        checkU2(u2vals);
        return g0(u2vals, T) + H(u2vals, cec) - T * S(u2vals);
    }

    /**
     * Gradient ∂G/∂u2vals[m] for all m = 0..nip-1.
     * Corresponds to Gx in delxGCVM.
     *
     * @param u2vals  internal parameter vector
     * @param T       temperature
     * @param cec     cluster expansion coefficients
     * @return        gradient vector, length nip
     */
    public double[] gradient(double[] u2vals, double T, double[] cec) {
        checkU2(u2vals);
        int nip  = data.nip;
        int ncf  = data.ncf;
        double[] gx = new double[nip];

        // ∂G₀/∂x[m] = GHSER_m(T)  (only for composition indices)
        for (int m = 0; m < data.nComp; m++) {
            gx[ncf + m] += ghser[m].G0(T);
        }

        // ∂H/∂v[m] = e[m]  (only for CV indices 0..ncf-1)
        for (int m = 0; m < ncf; m++) {
            gx[m] += cec[m];
        }

        // -T · ∂S/∂u2[m]  for all m
        double[] dSdu2 = gradientS(u2vals);
        for (int m = 0; m < nip; m++) {
            gx[m] -= T * dSdu2[m];
        }

        return gx;
    }

    /**
     * Hessian ∂²G/∂u2[m]∂u2[n] (nip × nip).
     * Corresponds to Gxx in delxGCVM.
     *
     * <p>H is linear in u2vals, so Hessian comes entirely from -T·S.
     * G₀ is linear in x, so no second derivatives there either.
     *
     * @return symmetric nip×nip matrix
     */
    public double[][] hessian(double[] u2vals, double T) {
        checkU2(u2vals);
        double[][] d2Sdu2 = hessianS(u2vals);
        int nip = data.nip;
        double[][] gxx = new double[nip][nip];
        for (int m = 0; m < nip; m++)
            for (int n = 0; n < nip; n++)
                gxx[m][n] = -T * d2Sdu2[m][n];
        return gxx;
    }

    /**
     * Mixed derivative ∂²G/∂u2[m]∂T = -∂S/∂u2[m].
     * Corresponds to GxT in delxGCVM.
     */
    public double[] gradientDT(double[] u2vals, double T, double[] cec) {
        checkU2(u2vals);
        int nip = data.nip;
        int ncf = data.ncf;
        double[] gxt = new double[nip];

        // ∂(∂G₀/∂x[m])/∂T = dGHSER_m/dT
        for (int m = 0; m < data.nComp; m++) {
            gxt[ncf + m] += ghser[m].dG0dT(T);
        }

        // ∂(∂H/∂v[m])/∂T = 0  (e[m] are T-linear but the pure v-derivative of e[m]*v[m]
        // w.r.t. T is de[m]/dT, handled separately in delnN computation)
        // Note: in the Mathematica code, eRules contain T-dependent expressions.
        // Here we separate: the CEC values are passed as cec[] and their T-derivative
        // must be provided by the caller if needed for the full GxT.
        // For the phase matrix inversion, only the numerical GxT at (T, u2) is needed.

        // -∂S/∂u2[m]  (the dominant term)
        double[] dSdu2 = gradientS(u2vals);
        for (int m = 0; m < nip; m++) {
            gxt[m] -= dSdu2[m];
        }

        return gxt;
    }

    // ------------------------------------------------------------------
    // Energy components
    // ------------------------------------------------------------------

    /** G₀(x, T) = Σᵢ x[i]·GHSER_i(T). */
    public double g0(double[] u2vals, double T) {
        double g = 0.0;
        for (int i = 0; i < data.nComp; i++) {
            g += u2vals[data.ncf + i] * ghser[i].G0(T);
        }
        return g;
    }

    /** H(v) = Σᵢ e[i]·v[i] (enthalpy of mixing, linear in CECs). */
    public double H(double[] u2vals, double[] cec) {
        double h = 0.0;
        for (int i = 0; i < data.ncf; i++) {
            h += cec[i] * u2vals[i];
        }
        return h;
    }

    /**
     * Entropy of mixing S(v) in J/(mol·K).
     * <pre>
     *   S = -R · Σ_{itc} kbdis[itc]·msdis[itc] ·
     *            Σ_{inc} mh[itc][inc] ·
     *            Σ_{icv} wcv[itc][inc][icv] · CV · ln(CV)
     * </pre>
     */
    public double S(double[] u2vals) {
        double[] u = data.computeU(u2vals);
        double s = 0.0;
        for (int itc = 0; itc < data.tcdis; itc++) {
            double kb  = data.kbdis[itc];
            double ms  = data.mhdis[itc];
            int    lci = data.lc[itc];
            for (int inc = 0; inc < lci; inc++) {
                double mhi  = data.mh[itc][inc];
                int    lcvi = data.lcv[itc][inc];
                for (int icv = 0; icv < lcvi; icv++) {
                    double cv = dotProduct(data.cmat[itc][inc][icv], u);
                    if (cv <= 0.0) continue;  // CV must be positive; skip degenerate
                    s += kb * ms * mhi * data.wcv[itc][inc][icv] * cv * Math.log(cv);
                }
            }
        }
        return -R * s;
    }

    // ------------------------------------------------------------------
    // Analytical derivatives of S
    // ------------------------------------------------------------------

    /**
     * Gradient of S w.r.t. u2vals: ∂S/∂u2[m] for m=0..nip-1.
     *
     * <pre>
     *   ∂S/∂u2[m] = -R · Σ_{itc,inc,icv} kbdis·ms·mh·wcv · (∂CV/∂u2[m]) · (ln(CV) + 1)
     *
     *   ∂CV[itc][inc][icv]/∂u2[m] = Σⱼ cmat[itc][inc][icv][j] · cfCoeffs[j][m]
     * </pre>
     */
    public double[] gradientS(double[] u2vals) {
        double[] u    = data.computeU(u2vals);
        int nip       = data.nip;
        double[] dSdu = new double[nip];

        for (int itc = 0; itc < data.tcdis; itc++) {
            double kb  = data.kbdis[itc];
            double ms  = data.mhdis[itc];
            int    lci = data.lc[itc];
            for (int inc = 0; inc < lci; inc++) {
                double mhi  = data.mh[itc][inc];
                int    lcvi = data.lcv[itc][inc];
                for (int icv = 0; icv < lcvi; icv++) {
                    double cv = dotProduct(data.cmat[itc][inc][icv], u);
                    if (cv <= 0.0) continue;
                    double weight = kb * ms * mhi * data.wcv[itc][inc][icv];
                    double logFac = Math.log(cv) + 1.0;

                    // ∂CV/∂u2[m] = Σⱼ cmat[j] * cfCoeffs[j][m]
                    for (int m = 0; m < nip; m++) {
                        double dCVdu2m = 0.0;
                        for (int j = 0; j < data.uListLen; j++) {
                            dCVdu2m += data.cmat[itc][inc][icv][j] * data.cfCoeffs[j][m];
                        }
                        dSdu[m] += weight * dCVdu2m * logFac;
                    }
                }
            }
        }

        // Apply -R factor
        for (int m = 0; m < nip; m++) dSdu[m] *= -R;
        return dSdu;
    }

    /**
     * Hessian of S w.r.t. u2vals: ∂²S/∂u2[m]∂u2[n].
     *
     * <pre>
     *   ∂²S/∂u2[m]∂u2[n] = -R · Σ_{itc,inc,icv} kbdis·ms·mh·wcv
     *                           · (∂CV/∂u2[m]) · (∂CV/∂u2[n]) / CV
     * </pre>
     * (The ln(CV)+1 term differentiates to 1/CV · ∂CV/∂u2[n].)
     */
    public double[][] hessianS(double[] u2vals) {
        double[] u    = data.computeU(u2vals);
        int nip       = data.nip;
        double[][] d2 = new double[nip][nip];

        for (int itc = 0; itc < data.tcdis; itc++) {
            double kb  = data.kbdis[itc];
            double ms  = data.mhdis[itc];
            int    lci = data.lc[itc];
            for (int inc = 0; inc < lci; inc++) {
                double mhi  = data.mh[itc][inc];
                int    lcvi = data.lcv[itc][inc];
                for (int icv = 0; icv < lcvi; icv++) {
                    double cv = dotProduct(data.cmat[itc][inc][icv], u);
                    if (cv <= 0.0) continue;
                    double weight = kb * ms * mhi * data.wcv[itc][inc][icv] / cv;

                    // Pre-compute ∂CV/∂u2[m] for all m
                    double[] dCVdu2 = new double[nip];
                    for (int m = 0; m < nip; m++) {
                        for (int j = 0; j < data.uListLen; j++) {
                            dCVdu2[m] += data.cmat[itc][inc][icv][j] * data.cfCoeffs[j][m];
                        }
                    }

                    for (int m = 0; m < nip; m++) {
                        for (int n = 0; n < nip; n++) {
                            d2[m][n] += weight * dCVdu2[m] * dCVdu2[n];
                        }
                    }
                }
            }
        }

        for (int m = 0; m < nip; m++)
            for (int n = 0; n < nip; n++)
                d2[m][n] *= -R;
        return d2;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public CvmPhaseData data() { return data; }
    public int nip()           { return data.nip; }
    public int ncf()           { return data.ncf; }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static double dotProduct(double[] a, double[] b) {
        double sum = 0.0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) sum += a[i] * b[i];
        return sum;
    }

    private void checkU2(double[] u2vals) {
        if (u2vals.length != data.nip)
            throw new IllegalArgumentException(
                    "u2vals.length=" + u2vals.length + " but nip=" + data.nip);
    }
}
