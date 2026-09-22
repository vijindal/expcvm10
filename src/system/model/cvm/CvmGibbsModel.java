package system.model.cvm;

import system.model.GibbsEnergyModel;
import system.model.unary.ElementGibbs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * First real {@link GibbsEnergyModel} implementation for a disordered
 * binary BCC_A2 Cluster Variation Method (CVM) phase.
 *
 * <p>This is the adapter/model-layer class that plugs the existing,
 * validated {@link CvmGibbs} mathematics (entropy, enthalpy, gradient,
 * Hessian) into the model-agnostic {@link GibbsEnergyModel} contract,
 * exactly the way {@link system.model.cef.CefGibbs} plugs CEF math into
 * the same contract. It does NOT reimplement or alter any of the
 * {@link CvmGibbs}/{@link CvmPhaseData} thermodynamics -- see those
 * classes' javadoc for the actual formulas.
 *
 * <pre>
 *   CvmGibbsModel
 *         |
 *         +-- CvmPhaseData        (geometry: cmat/cfCoeffs/eListNames/...)
 *         +-- CecEvaluator        (named, T-dependent CECs -> cec[](T))
 *         +-- CvmGibbs            (existing S/H/gradient/Hessian math)
 *         +-- ElementGibbs[]      (existing SGTE unary reference energies)
 * </pre>
 *
 * <h2>Internal-variable representation (y = uFull = [u ; x])</h2>
 * For this first integration, the model's {@code y} vector is exactly
 * {@link CvmPhaseData}'s {@code u2List}:
 * <pre>
 *   y[0 .. ncf-1]     = CVM non-point correlation functions (v4AB, v3AB, ...)
 *   y[ncf .. ncf+K-1] = component mole fractions (xA, xB, ...)
 * </pre>
 * i.e. {@code numSiteVars() == data.nip == ncf + nComp}. This choice (over
 * {@code y = u} alone) is what lets {@link #moles(double[])} /
 * {@link #dMoles_dy()} below satisfy the existing
 * {@code GibbsEnergyModel} contract with a trivial, constant Jacobian --
 * see the task's design-decision note for the full rationale.
 *
 * <h2>Composition-normalization constraint (integration bridge only)</h2>
 * {@link #numSublattices()}/{@link #offsets()}/
 * {@link #constituentsPerSublattice()} report a single block covering the
 * trailing {@code x} slice of {@code y}, purely so that the existing
 * {@link calc.equil.PhaseMatrixAssembler} bordered-matrix machinery
 * creates the one constraint this model actually has,
 * {@code sum_A x_A = 1}, without modification. This is NOT a claim that
 * CVM has a CEF-style sublattice -- the leading {@code u}-block is not a
 * sublattice at all, and is deliberately excluded from this "sublattice."
 *
 * <h2>Not yet supported</h2>
 * <ul>
 *   <li>Pressure/volume: {@link #dG_dP} and {@link #d2G_dydP} are
 *       identically zero (neither CEWorkbench nor the existing CVM
 *       evaluator has a volume model; see their javadoc for why).</li>
 *   <li>Ordered CVM phases, ternary/quaternary CECs, and solver/factory
 *       integration are explicitly out of scope for this class.</li>
 * </ul>
 */
public class CvmGibbsModel extends GibbsEnergyModel {

    private final CvmPhaseData data;
    private final CvmGibbs gibbs;
    private final CecEvaluator cec;
    private final ElementGibbs[] ghser;
    private final ArrayList<String> elements;

    /** Finite-difference step for dGHSER/dT (K), matching the convention
     *  already used by {@link CvmGibbs#gradientDT}: {@link ElementGibbs}
     *  exposes only {@code gibbs(phase,T)}/{@code ghser(T)}, no analytical
     *  temperature derivative, so this is the only consistent option
     *  available without extending that shared interface (out of scope
     *  for this model-layer task). */
    private static final double GHSER_DT_STEP = 0.01;

    /**
     * @param data      parsed CVM phase geometry (u2ListNames/eListNames
     *                  order defines the y-vector and CEC-array order)
     * @param cecTerms  named, temperature-dependent CEC terms; must cover
     *                  exactly {@code data.eListNames} (see
     *                  {@link CecEvaluator})
     * @param ghser     SGTE reference-energy evaluators, one per component,
     *                  in the same order as the trailing x-block of y
     * @param elements  element symbols for the trailing x-block of y, same
     *                  order as {@code ghser}
     */
    public CvmGibbsModel(CvmPhaseData data, List<CecTerm> cecTerms,
                          ElementGibbs[] ghser, List<String> elements) {
        if (ghser.length != data.nComp)
            throw new IllegalArgumentException("ghser.length must equal nComp=" + data.nComp);
        if (elements.size() != data.nComp)
            throw new IllegalArgumentException("elements.size() must equal nComp=" + data.nComp);
        this.data = data;
        this.gibbs = new CvmGibbs(data, ghser);
        this.cec = new CecEvaluator(data, cecTerms);
        this.ghser = ghser.clone();
        this.elements = new ArrayList<>(elements);
    }

    // ══════════════════════════════════════════════════════════════════
    // Phase Identity
    // ══════════════════════════════════════════════════════════════════

    @Override public String phaseName() { return data.phaseName; }
    @Override public String modelType() { return "CVM"; }
    @Override public ArrayList<String> elementNames() { return new ArrayList<>(elements); }
    @Override public String[] componentList() { return elements.toArray(new String[0]); }
    @Override public int numComponents() { return data.nComp; }
    @Override public int numTotalParams() { return numSiteVars(); }

    /**
     * Nominal formula-unit size. The existing CVM data/evaluator has no
     * notion of a vacancy sublattice or site ratio distinct from the atom
     * count itself -- every u2List component is either a correlation
     * function (dimensionless, not atom-counting) or a mole fraction that
     * already sums to 1 atom per formula unit. There is therefore no
     * separate "raw site ratio sum" for CVM the way CEF's nfu() sums
     * per-sublattice site ratios (which can exceed 1, e.g. a vacancy
     * sublattice): nfu() = 1 for this disordered single-site-type phase.
     */
    @Override public double nfu() { return 1.0; }

    // ══════════════════════════════════════════════════════════════════
    // Internal Variable Management
    // ══════════════════════════════════════════════════════════════════

    /**
     * Builds a valid random/disordered initial state {@code [u_initial; x]}
     * from the requested overall composition, reusing
     * {@link CvmPhaseData#evalRandApprox(double[])} -- the existing
     * disordered-state construction -- rather than inventing a CVM
     * minimizer ({@code CvmNewtonSolver} is explicitly not ported here).
     */
    @Override
    public double[] getInitialInternalVars(double[] x) {
        if (x == null || x.length != data.nComp)
            throw new IllegalArgumentException(
                    "Composition length must equal nComp=" + data.nComp);
        double sum = 0.0;
        for (double xi : x) {
            if (!Double.isFinite(xi) || xi < 0.0)
                throw new IllegalArgumentException("Invalid composition value: " + xi);
            sum += xi;
        }
        if (!(sum > 0.0))
            throw new IllegalArgumentException("Composition must have a positive sum");

        double[] xNorm = new double[x.length];
        for (int i = 0; i < x.length; i++) xNorm[i] = x[i] / sum;

        return data.evalRandApprox(xNorm);
    }

    /** compositionFromInternal(y) = trailing K entries of y (the x-block). */
    @Override
    public double[] compositionFromInternal(double[] y) {
        checkY(y);
        return Arrays.copyOfRange(y, data.ncf, data.nip);
    }

    /**
     * Validity of {@code y = [u; x]} for the disordered binary CVM state:
     * <ol>
     *   <li>correct length ({@code nip});</li>
     *   <li>no NaN/Infinity;</li>
     *   <li>composition block (trailing K entries) is physically valid:
     *       each in [0,1] and summing to 1;</li>
     *   <li>every derived cluster probability
     *       {@code CV = cmat . computeU(y)} is a physically valid
     *       probability, i.e. in [0,1] (using the CVM/CEWorkbench cluster-
     *       probability domain, not CEF's site-fraction validity check).</li>
     * </ol>
     */
    @Override
    public boolean isValid(double[] y) {
        if (y == null || y.length != data.nip) return false;

        for (double v : y) {
            if (!Double.isFinite(v)) return false;
        }

        double xSum = 0.0;
        for (int i = 0; i < data.nComp; i++) {
            double xi = y[data.ncf + i];
            if (xi < -1.0e-9 || xi > 1.0 + 1.0e-9) return false;
            xSum += xi;
        }
        if (Math.abs(xSum - 1.0) > 1.0e-8) return false;

        double[] u = data.computeU(y);
        final double EPS = 1.0e-9;
        for (int itc = 0; itc < data.tcdis; itc++) {
            int lci = data.lc[itc];
            for (int inc = 0; inc < lci; inc++) {
                int lcvi = data.lcv[itc][inc];
                for (int icv = 0; icv < lcvi; icv++) {
                    double cv = dot(data.cmat[itc][inc][icv], u);
                    if (!Double.isFinite(cv) || cv < -EPS || cv > 1.0 + EPS) return false;
                }
            }
        }
        return true;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) s += a[i] * b[i];
        return s;
    }

    // ══════════════════════════════════════════════════════════════════
    // Site-Fraction ("y") Thermodynamics
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double G(double T, double P, double[] y) {
        checkY(y);
        return gibbs.evaluate(y, T, cec.evaluate(T));
    }

    @Override
    public double[] dG_dy(double T, double P, double[] y) {
        checkY(y);
        return gibbs.gradient(y, T, cec.evaluate(T));
    }

    @Override
    public double[][] d2G_dy2(double T, double P, double[] y) {
        checkY(y);
        return gibbs.hessian(y, T);
    }

    /**
     * dG/dT at fixed y, accounting for the T-dependence of both G0(T,x)
     * (GHSER) and the CECs J_i(T) = a_i + b_i*T:
     *
     * <pre>
     *   G = G0(T,x) + sum_i J_i(T)*u2[i] - T*S(u2)
     *
     *   dG/dT = dG0/dT + sum_i (dJ_i/dT)*u2[i] - S(u2)
     * </pre>
     *
     * (the {@code -T*S} term differentiates to {@code -S} since S(u2) does
     * not depend on T). dG0/dT is obtained by the same central finite
     * difference {@link CvmGibbs#gradientDT} already uses for GHSER, since
     * {@link ElementGibbs} has no analytical derivative; the CEC and
     * entropy terms are exact/analytical.
     */
    @Override
    public double dG_dT(double T, double P, double[] y) {
        checkY(y);

        double dG0dT = 0.0;
        for (int i = 0; i < data.nComp; i++) {
            double x_i = y[data.ncf + i];
            double dGhserDT = (ghser[i].ghser(T + GHSER_DT_STEP) - ghser[i].ghser(T - GHSER_DT_STEP))
                             / (2.0 * GHSER_DT_STEP);
            dG0dT += x_i * dGhserDT;
        }

        double[] dcecDT = cec.evaluateDT();
        double dHdT = 0.0;
        for (int i = 0; i < data.ncf; i++) {
            dHdT += dcecDT[i] * y[i];
        }

        double S = gibbs.S(y);

        return dG0dT + dHdT - S;
    }

    /**
     * d2G/dy_m dT, the y-derivative of {@link #dG_dT}:
     *
     * <pre>
     *   d/dy_m [ dG0/dT ]              = dGHSER_m/dT   if m is a composition index, else 0
     *   d/dy_m [ sum_i (dJ_i/dT)*u2_i ] = dJ_m/dT       if m &lt; ncf,               else 0
     *   d/dy_m [ -S(u2) ]              = -dS/du2[m]     (all m)
     * </pre>
     *
     * All three pieces use the same primitives as {@link #dG_dT}: the
     * finite-difference GHSER derivative, the exact CEC derivative, and
     * {@link CvmGibbs#gradientS(double[])}.
     */
    @Override
    public double[] d2G_dydT(double T, double P, double[] y) {
        checkY(y);
        int nip = data.nip;
        int ncf = data.ncf;
        double[] result = new double[nip];

        for (int i = 0; i < data.nComp; i++) {
            double dGhserDT = (ghser[i].ghser(T + GHSER_DT_STEP) - ghser[i].ghser(T - GHSER_DT_STEP))
                             / (2.0 * GHSER_DT_STEP);
            result[ncf + i] += dGhserDT;
        }

        double[] dcecDT = cec.evaluateDT();
        for (int i = 0; i < ncf; i++) {
            result[i] += dcecDT[i];
        }

        double[] dSdu2 = gibbs.gradientS(y);
        for (int m = 0; m < nip; m++) {
            result[m] -= dSdu2[m];
        }

        return result;
    }

    /**
     * No volume/pressure contribution: neither CEWorkbench nor the
     * existing {@link CvmGibbs} evaluator models a molar volume, so this
     * is identically zero. A future volume model must not be improvised
     * here.
     */
    @Override
    public double dG_dP(double T, double P, double[] y) {
        checkY(y);
        return 0.0;
    }

    /** See {@link #dG_dP}: zero vector, same no-volume-model reason. */
    @Override
    public double[] d2G_dydP(double T, double P, double[] y) {
        checkY(y);
        return new double[data.nip];
    }

    /**
     * moles(y) = x, the trailing K-entry composition block of y -- the
     * first-implementation convention for this disordered CVM phase (no
     * distinct "constituent -> element" mapping is needed since y already
     * carries mole fractions directly, unlike CEF's site-fraction
     * constituents).
     */
    @Override
    public double[] moles(double[] y) {
        checkY(y);
        return Arrays.copyOfRange(y, data.ncf, data.nip);
    }

    /**
     * dMoles/dy = [0_(K x ncf) | I_K]: M_A(y) = x_A is a direct trailing
     * slice of y, so its Jacobian is the constant zero/identity block
     * described in the task's design decision.
     */
    @Override
    public double[][] dMoles_dy() {
        int K = data.nComp;
        int nip = data.nip;
        double[][] dM = new double[K][nip];
        for (int a = 0; a < K; a++) {
            dM[a][data.ncf + a] = 1.0;
        }
        return dM;
    }

    // ══════════════════════════════════════════════════════════════════
    // Sublattice bridge (integration representation only -- see class
    // javadoc "Composition-normalization constraint" section)
    // ══════════════════════════════════════════════════════════════════

    @Override public int numSublattices() { return 1; }
    @Override public int numSiteVars() { return data.nip; }
    @Override public int[] offsets() { return new int[] { data.ncf }; }
    @Override public int[] constituentsPerSublattice() { return new int[] { data.nComp }; }

    // ══════════════════════════════════════════════════════════════════
    // Output / Debugging
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void printPhaseInfo() {
        System.out.println("CvmGibbsModel{phase=" + data.phaseName
                + ", nComp=" + data.nComp + ", ncf=" + data.ncf + ", nip=" + data.nip
                + ", u2ListNames=" + Arrays.toString(data.u2ListNames)
                + ", eListNames=" + Arrays.toString(data.eListNames) + "}");
    }

    // ══════════════════════════════════════════════════════════════════
    // Accessors (for tests / cross-checks against the underlying evaluator)
    // ══════════════════════════════════════════════════════════════════

    public CvmPhaseData phaseData() { return data; }
    public CvmGibbs underlyingGibbs() { return gibbs; }
    public CecEvaluator cecEvaluator() { return cec; }

    // ══════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════

    private void checkY(double[] y) {
        if (y == null || y.length != data.nip) {
            throw new IllegalArgumentException(
                    "y.length must equal nip=" + data.nip
                    + " but was " + (y == null ? "null" : y.length));
        }
    }
}
