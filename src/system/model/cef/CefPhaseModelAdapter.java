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

    /**
     * elementIndexOnSublattice[s][i] = index into elementNames_value of the
     * constituent at (sublattice s, position i), or -1 if that constituent
     * is not one of the modeled elements (e.g. "VA").
     * Built from the TDB constituent lists so that composition<->site-fraction
     * mapping is correct even when a sublattice's constituent order/identity
     * differs from the element list (vacancies, interstitials, etc.).
     */
    private final int[][] elementIndexOnSublattice;

    /** True once y has been set to a valid non-zero state. */
    private boolean yInitialized = false;

    public CefPhaseModelAdapter(CefGibbs gibbs,
                                MagneticContribution magnetic,
                                String phaseName,
                                ArrayList<String> elements) {
        this(gibbs, magnetic, phaseName, elements, null);
    }

    public CefPhaseModelAdapter(CefGibbs gibbs,
                                MagneticContribution magnetic,
                                String phaseName,
                                ArrayList<String> elements,
                                ArrayList<ArrayList<String>> constituentNames) {
        this.gibbs = gibbs;
        this.magnetic = magnetic;
        this.phaseName_value = phaseName;
        this.elementNames_value = new ArrayList<>(elements);
        this.elementIndexOnSublattice = buildElementIndexMap(gibbs, elements, constituentNames);

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

    /**
     * Builds the per-sublattice constituent-to-element index map.
     * If {@code constituentNames} is unavailable (e.g. legacy 2-arg
     * constructor), falls back to the previous 1:1 dominant-sublattice
     * assumption (index i on any sublattice maps to element i).
     */
    private static int[][] buildElementIndexMap(CefGibbs gibbs,
                                                 ArrayList<String> elements,
                                                 ArrayList<ArrayList<String>> constituentNames) {
        int ns = gibbs.ns();
        int[] ncSL = gibbs.constituentsPerSublattice();
        int[][] map = new int[ns][];
        for (int s = 0; s < ns; s++) {
            map[s] = new int[ncSL[s]];
            if (constituentNames != null && s < constituentNames.size()) {
                ArrayList<String> names = constituentNames.get(s);
                for (int i = 0; i < ncSL[s]; i++) {
                    map[s][i] = -1;
                    if (i < names.size()) {
                        String name = names.get(i).toUpperCase();
                        for (int k = 0; k < elements.size(); k++) {
                            if (elements.get(k).equalsIgnoreCase(name)) {
                                map[s][i] = k;
                                break;
                            }
                        }
                    }
                }
            } else {
                // Fallback: dominant-sublattice 1:1 assumption
                for (int i = 0; i < ncSL[s]; i++) {
                    map[s][i] = (i < elements.size()) ? i : -1;
                }
            }
        }
        return map;
    }

    // ══════════════════════════════════════════════════════════════════
    // Stateless accessors for the fresh Sundman Algorithm A solver (M3)
    // ══════════════════════════════════════════════════════════════════
    //
    // The abstract GibbsEnergyModel contract only exposes G/gradient/hessian
    // through a stateful, mole-fraction-facing surface (evaluateG(x,T) etc.
    // silently reinterpret their first argument via the yInitialized flag).
    // The new calc.equil.sundman package needs direct, unambiguous, stateless
    // access to the underlying CefGibbs site-fraction evaluator and the
    // constituent-to-element map, without going through PhaseEquilData/
    // compute() (explicitly discarded plumbing per the M3 design report).
    // These three accessors are read-only passthroughs; they add no new
    // behavior or state to this class.

    /** The underlying site-fraction-level CEF Gibbs energy evaluator. */
    public CefGibbs getGibbs() { return gibbs; }

    /**
     * elementIndexOnSublattice[s][i] = index into elementNames() of the
     * constituent at (sublattice s, position i), or -1 if it is not one of
     * the modeled elements (e.g. "VA"). See the field Javadoc above.
     */
    public int[][] getElementIndexOnSublattice() {
        int[][] copy = new int[elementIndexOnSublattice.length][];
        for (int s = 0; s < elementIndexOnSublattice.length; s++) {
            copy[s] = elementIndexOnSublattice[s].clone();
        }
        return copy;
    }

    /**
     * M_A(y) — Sundman Eq. (2): moles of component A per formula unit,
     * Σ_s a[s]*y[s,A], summed only over element-mapped constituents
     * (vacancies excluded). Public passthrough to {@link #unnormalizedM}.
     */
    public double[] computeM(double[] y) { return unnormalizedM(y); }

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
        double G = gibbs.evaluate(T, y);
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
        double G = gibbs.evaluate(T, yLocal);
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
        double[] gxSite = gibbs.gradient(T, yLocal);
        return projectToMoleFractions(gxSite);
    }

    @Override
    public double[][] hessian(double[] x, double T) {
        double[] yLocal = (yInitialized && y != null && y.length == gibbs.nip())
                        ? y : getInitialInternalVars(x);
        return gibbs.hessian(T, yLocal);
    }

    @Override
    public double evaluateGT() {
        double[] yLocal = yInitialized && y != null ? y : getInitialInternalVars(
            x != null ? x : new double[elementNames_value.size()]);
        return gibbs.temperatureDerivative(T, yLocal);
    }

    @Override
    public double evaluateGP() {
        return 0.0;  // CEF has no P-dependence
    }

    @Override
    public double[] evaluateGx() {
        double[] yLocal = yInitialized && y != null ? y : getInitialInternalVars(
            x != null ? x : new double[elementNames_value.size()]);
        double[] gxSite = gibbs.gradient(T, yLocal);
        return projectToMoleFractions(gxSite);
    }

    @Override
    public double[] evaluateGTx() {
        double[] yLocal = yInitialized && y != null ? y : getInitialInternalVars(
            x != null ? x : new double[elementNames_value.size()]);
        double[] gxtSite = gibbs.gradientDT(T, yLocal);
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
        return gibbs.hessian(T, yLocal);
    }

    // ══════════════════════════════════════════════════════════════════
    // Internal Variable Management
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double[] getInitialInternalVars(double[] x) {
        /*
         * Construct a strictly positive CEF constitution y from the requested
         * overall component composition x.
         *
         * Sundman:
         *
         *   M_A = sum_s a_s sum_i b_iA y_is
         *   x_A = M_A / sum_B M_B
         *
         * with
         *
         *   sum_i y_is = 1
         *
         * on every sublattice.
         *
         * The previous implementation normalized x independently on each
         * sublattice.  That is not a valid general CEF composition mapping and
         * gives y_VA = 0 for FCC_A1 when carbon is present.
         *
         * Here we determine y by minimizing the composition residual subject
         * to the sublattice normalization constraints.  The initial point is
         * strictly positive, so CefGibbs.gradient() is always evaluated away
         * from the logarithmic singularity.
         */

        final double EPS = 1.0e-10;
        final int ns  = gibbs.ns();
        final int nip = gibbs.nip();

        if (x == null || x.length != elementNames_value.size()) {
            throw new IllegalArgumentException(
                "Composition length does not match number of system elements");
        }

        /*
         * Normalize the requested overall composition.
         */
        double xSum = 0.0;

        for (double xi : x) {
            if (!Double.isFinite(xi) || xi < 0.0) {
                throw new IllegalArgumentException(
                    "Invalid overall composition: " + xi);
            }
            xSum += xi;
        }

        if (!(xSum > 0.0) || !Double.isFinite(xSum)) {
            throw new IllegalArgumentException(
                "Overall composition must have a positive finite sum");
        }

        final double[] target = new double[x.length];

        for (int k = 0; k < x.length; k++) {
            target[k] = x[k] / xSum;
        }

        /*
         * Start from the center of every sublattice simplex.
         *
         * This guarantees:
         *
         *       y_is > 0
         *       sum_i y_is = 1
         *
         * before composition correction begins.
         */
        double[] y = new double[nip];
        int[] nc = gibbs.constituentsPerSublattice();
        int[] off = gibbs.offsets();

        for (int s = 0; s < ns; s++) {
            double value = 1.0 / nc[s];

            for (int i = 0; i < nc[s]; i++) {
                y[off[s] + i] = value;
            }
        }

        /*
         * The composition mapping is invariant to the overall scale of M.
         * We therefore optimize the normalized component composition directly.
         *
         * A damped Gauss-Newton iteration is used.  The Jacobian is obtained
         * analytically from:
         *
         *       M_A = sum_s a_s sum_i b_iA y_is
         *
         * and the CEF sublattice constraints are enforced by eliminating the
         * last constituent of every sublattice.
         */
        final int nIndependent = nip - ns;

        if (nIndependent <= 0) {
            /*
             * Fixed-composition phase.  Its composition cannot be changed by
             * internal variables, so the central positive constitution is the
             * only valid initialization.
             */
            return y;
        }

        /*
         * Independent-variable representation:
         *
         * for each sublattice, the first nc[s]-1 fractions are independent and
         * the final fraction is:
         *
         *       y_last = 1 - sum(y_independent)
         *
         * To guarantee strict positivity during the iteration we use a
         * softmax representation internally.
         */
        double[] z = new double[nIndependent];

        int p = 0;

        for (int s = 0; s < ns; s++) {
            /*
             * Uniform site fractions correspond to zero logits.
             */
            for (int i = 0; i < nc[s] - 1; i++) {
                z[p++] = 0.0;
            }
        }

        /*
         * Convert logits to site fractions.
         */
        logitsToSiteFractions(z, y, nc, off);

        /*
         * Minimize:
         *
         *       1/2 sum_A (x_A(y)-x_A,target)^2
         *
         * with a small damping term.
         *
         * The iteration is intentionally modest because this is an
         * initialization routine, not the equilibrium minimizer itself.
         */
        final int maxIter = 100;
        final double tol = 1.0e-10;
        final double lambda = 1.0e-8;

        double[] residual = new double[x.length];

        for (int iter = 0; iter < maxIter; iter++) {

            /*
             * Current composition.
             */
            double[] current = compositionFromInternal(y);

            double norm2 = 0.0;

            for (int k = 0; k < target.length; k++) {
                residual[k] = current[k] - target[k];
                norm2 += residual[k] * residual[k];
            }

            if (Math.sqrt(norm2) < tol) {
                break;
            }

            /*
             * Numerical Jacobian with respect to the unconstrained logits.
             *
             * This deliberately uses compositionFromInternal(), so the
             * initializer and the equilibrium layer use exactly the same
             * composition definition.
             */
            double[][] J = new double[target.length][nIndependent];

            final double dz = 1.0e-6;

            for (int j = 0; j < nIndependent; j++) {

                double old = z[j];

                z[j] = old + dz;
                double[] yp = new double[nip];
                logitsToSiteFractions(z, yp, nc, off);
                double[] xp = compositionFromInternal(yp);

                z[j] = old - dz;
                double[] ym = new double[nip];
                logitsToSiteFractions(z, ym, nc, off);
                double[] xm = compositionFromInternal(ym);

                z[j] = old;

                for (int k = 0; k < target.length; k++) {
                    J[k][j] = (xp[k] - xm[k]) / (2.0 * dz);
                }
            }

            /*
             * Solve the damped normal equations:
             *
             *       (J^T J + lambda I) dz = -J^T r
             *
             * This is a small dense system whose dimension is the number of
             * CEF internal variables.
             */
            double[][] A = new double[nIndependent][nIndependent];
            double[] b = new double[nIndependent];

            for (int i = 0; i < nIndependent; i++) {
                for (int j = 0; j < nIndependent; j++) {

                    double sum = 0.0;

                    for (int k = 0; k < target.length; k++) {
                        sum += J[k][i] * J[k][j];
                    }

                    A[i][j] = sum;

                    if (i == j) {
                        A[i][j] += lambda;
                    }
                }

                double sum = 0.0;

                for (int k = 0; k < target.length; k++) {
                    sum += J[k][i] * residual[k];
                }

                b[i] = -sum;
            }

            double[] step = solveLinearSystem(A, b);

            /*
             * Backtracking prevents a very large composition correction from
             * moving the initialization into an undesirable region.
             */
            double oldNorm = Math.sqrt(norm2);
            double alpha = 1.0;

            double[] trialZ = new double[nIndependent];
            double[] trialY = new double[nip];

            boolean accepted = false;

            for (int ls = 0; ls < 20; ls++) {

                for (int j = 0; j < nIndependent; j++) {
                    trialZ[j] = z[j] + alpha * step[j];
                }

                logitsToSiteFractions(
                    trialZ, trialY, nc, off);

                double[] trialX = compositionFromInternal(trialY);

                double trialNorm2 = 0.0;

                for (int k = 0; k < target.length; k++) {
                    double r = trialX[k] - target[k];
                    trialNorm2 += r * r;
                }

                if (Math.sqrt(trialNorm2) < oldNorm) {
                    accepted = true;
                    break;
                }

                alpha *= 0.5;
            }

            if (!accepted) {
                break;
            }

            System.arraycopy(trialZ, 0, z, 0, nIndependent);
            System.arraycopy(trialY, 0, y, 0, nip);
        }

        /*
         * Final positivity/normalization check.
         */
        for (int s = 0; s < ns; s++) {

            double sum = 0.0;

            for (int i = 0; i < nc[s]; i++) {
                double yi = y[off[s] + i];

                if (!Double.isFinite(yi) || yi <= EPS) {
                    return null;
                }

                sum += yi;
            }

            if (Math.abs(sum - 1.0) > 1.0e-12) {
                throw new IllegalStateException(
                    "CEF initial site fractions do not normalize on " +
                    "sublattice " + s + ": sum=" + sum);
            }
        }

        return y;
    }

    @Override
    public double[] compositionFromInternal(double[] y) {
        // Compute mole fractions from site fractions using the
        // constituent-to-element map (correctly excludes VA and other
        // non-element constituents from the composition sum).
        int nc = elementNames_value.size();
        double[] x = unnormalizedM(y);
        double total = 0.0;
        for (int k = 0; k < nc; k++) total += x[k];
        if (total > 0) {
            for (int k = 0; k < nc; k++) x[k] /= total;
        }
        return x;
    }

    /**
     * Unnormalized M_A^phase (Sundman Eq.2): moles of component A per
     * formula unit = Σ_s a[s]*y[s,A], summed only over sublattice
     * constituents that map to element A (vacancies and other non-element
     * constituents contribute to nfu but not to any M_A). Unlike
     * {@link #compositionFromInternal}, this is NOT renormalized to sum
     * to 1 — Σ_A M_A equals nfu only when every sublattice is occupied
     * entirely by real elements (e.g. V2ZR); for a phase with a vacancy
     * sublattice (e.g. BCC_A2, HCP_A3) Σ_A M_A < nfu.
     */
    private double[] unnormalizedM(double[] y) {
        int nc = elementNames_value.size();
        double[] m = new double[nc];
        double[] a = gibbs.stoichiometry();
        int[] offs = gibbs.offsets();
        int[] ncSL = gibbs.constituentsPerSublattice();
        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < ncSL[s]; i++) {
                int el = elementIndexOnSublattice[s][i];
                if (el < 0) continue;
                m[el] += a[s] * y[offs[s] + i];
            }
        }
        return m;
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
        double G = gibbs.evaluate(T, y);
        double[] Gx = gibbs.gradient(T, y);
        double[][] Gxx = gibbs.hessian(T, y);
        double[] GxT = gibbs.gradientDT(T, y);
        double[] GxP = new double[nip];  // no P-dependence

        // Step 2: assemble phase matrix M (nip+ns)×(nip+ns), with one
        // Lagrange-multiplier row/column per sublattice s, enforcing
        // Sigma_i y[s,i] = 1 independently for each sublattice (a single
        // global border enforcing only Sigma_m y[m] = const, as before,
        // under-constrains any phase with more than one sublattice that
        // has more than one constituent -- see M2 Step 4 diagnostic).
        int ns = gibbs.ns();
        int[] offs = gibbs.offsets();
        int[] ncSL = gibbs.constituentsPerSublattice();
        int matDim = nip + ns;
        double[][] M = new double[matDim][matDim];
        for (int i = 0; i < nip; i++) {
            for (int j = 0; j < nip; j++) {
                M[i][j] = Gxx[i][j];
            }
        }
        for (int s = 0; s < ns; s++) {
            int borderRow = nip + s;
            for (int i = offs[s]; i < offs[s] + ncSL[s]; i++) {
                M[i][borderRow] = 1.0;
                M[borderRow][i] = 1.0;
            }
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

        double[] x = compositionFromInternal(y);

        /*
         * ΔM_A = Σ_s a[s] Δy[s,A].
         */
        double[] delnN = new double[nc];
        double[] a = gibbs.stoichiometry();

        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < ncSL[s]; i++) {

                int el = elementIndexOnSublattice[s][i];

                if (el < 0)
                    continue;

                delnN[el] +=
                    a[s]
                    * delyN[offs[s] + i];
            }
        }

        // mA = M_A^phase (Sundman Eq.2): moles of component A per formula
        // unit = sum_s a[s]*y[s,A], summed only over element-mapped
        // constituents (vacancies contribute to nfu but not to any M_A).
        // This is NOT normalized to sum to 1 -- it equals the mole
        // fraction x only when the phase has no non-element sublattice
        // (e.g. LIQUID, V2ZR's own total happens to equal nfu=3, but the
        // *value* of M_A itself is still the unnormalized count, e.g.
        // M_V=1.8 for V2ZR at x_V=0.6, not 0.6).
        double[] mA = unnormalizedM(y);

        // Compute eMatNC[A][B] = dM_A[A]/dmu_B, the phase-dependent
        // composition response used directly by EquilibriumSolver's
        // multiphase Jacobian (see assembleEquilibriumMatrix). Derived by
        // chaining the already-computed eMat (dy/d(mapped mu), from Step 5)
        // through the composition sensitivity dM_A/dy[m] = a[s(m)] (linear,
        // since M_A is now the unnormalized Sundman quantity, not a ratio).
        // See computeEMatNC's Javadoc for the full derivation.
        double[][] eMatNC = computeEMatNC(eMat);

        return new PhaseEquilData(G, delyN, delnN, x, mA, eMat, eMatNC, cG, cT, cP, null);
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

    /**
     * Map chemical potentials (length nc) to site-fraction space (length nip).
     * Only maps constituents that correspond to actual elements (not VA).
     * VA sites get mu=0 (chemical potential of vacancy is zero by convention).
     *
     * <p>Uses {@link #elementIndexOnSublattice} (the same constituent-to-
     * element map used by {@link #getInitialInternalVars} and
     * {@link #compositionFromInternal}) rather than a positional index
     * comparison, so this is correct even when a sublattice's constituent
     * order/identity differs from the element list (e.g. a vacancy
     * sublattice that is not the last sublattice, or whose local index
     * happens to be less than nc).
     */
    /**
     * Maps component chemical-potential multipliers to the
     * site-fraction stationarity equations.
     *
     * For constituent A on sublattice s:
     *
     *     M_A = sum_s a[s] y[s,A]
     *
     * hence
     *
     *     dM_A/dy[s,A] = a[s]
     *
     * and the corresponding site-space multiplier is
     *
     *     muY[s,A] = a[s] mu_A.
     *
     * Non-element constituents such as VA have zero multiplier.
     */
    private double[] mapMuToSiteFractions(double[] mu, double[] y) {
        int nip  = gibbs.nip();
        int[]    offs = gibbs.offsets();
        int[]    ncSL = gibbs.constituentsPerSublattice();
        double[] a    = gibbs.stoichiometry();
        double[] muY  = new double[nip];

        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < ncSL[s]; i++) {
                int flatIdx = offs[s] + i;
                int el = elementIndexOnSublattice[s][i];
                /*
                 * M_el = sum_s a[s] * y[s,el]
                 *
                 * Therefore
                 *
                 * dM_el / dy[s,el] = a[s].
                 *
                 * Sundman's chemical-potential contribution in the
                 * site-fraction stationarity equations is consequently
                 *
                 *     a[s] * mu_el.
                 */
                if (el >= 0 && el < mu.length) {
                    muY[flatIdx] = a[s] * mu[el];
                }
                // else: muY[flatIdx] stays 0.0 (VA convention)
            }
        }
        return muY;
    }

    /**
     * Composition sensitivity ny[A][m] = ∂M_A/∂y[m], the derivative of
     * {@link #unnormalizedM} (Sundman's M_A, the mA now stored in
     * PhaseEquilData) with respect to the flat site fraction y[m].
     *
     * <p>unnormalizedM computes, for mapped constituents only,
     * <pre>
     *   M_A(y) = Σ_m c[A][m]*y[m],  c[A][m] = a[s(m)] if elementIndexOnSublattice[s(m)][i(m)] == A, else 0
     * </pre>
     * which is linear in y (no normalization/division), so
     * <pre>
     *   ∂M_A/∂y[m] = c[A][m] = a[s(m)]  if constituent m maps to element A, else 0
     * </pre>
     * This is independent of the current y value (unlike the mole-fraction
     * x = compositionFromInternal(y), whose derivative involves a quotient
     * rule with a mA-dependent correction term).
     *
     * @return ny[A][m], size nc × nip
     */
    private double[][] compositionSensitivity() {
        int nc  = elementNames_value.size();
        int nip = gibbs.nip();
        double[] a    = gibbs.stoichiometry();
        int[]    offs = gibbs.offsets();
        int[]    ncSL = gibbs.constituentsPerSublattice();

        double[][] ny = new double[nc][nip];
        for (int s = 0; s < gibbs.ns(); s++) {
            for (int i = 0; i < ncSL[s]; i++) {
                int el = elementIndexOnSublattice[s][i];
                if (el < 0) continue; // vacancy/non-element constituent: derivative is 0 for every A
                int m = offs[s] + i;
                ny[el][m] = a[s];
            }
        }
        return ny;
    }

    /**
     * eMatNC[A][B] = ∂mA[A]/∂μ_B, the phase-dependent composition response
     * required by EquilibriumSolver.assembleEquilibriumMatrix (used there
     * as ΔM_A = Σ_B eMatNC[A][B]·Δμ_B in both the Gibbs-Duhem and
     * mass-balance rows of the multiphase Newton Jacobian).
     *
     * <p><b>Derivation.</b> Step 5 of {@link #compute} already establishes,
     * for this same eMat, that
     * <pre>
     *   ∂y[m]/∂muMapped[j] = eMat[m][j]
     * </pre>
     * (from dely[m] = ... + Σ_j eMat[m][j]*muMapped[j]), and
     * {@link #mapMuToSiteFractions} establishes
     * <pre>
     *   muMapped[j] = mu[elementIndexOnSublattice(j)]   if constituent j maps to an element
     *               = 0                                  otherwise (e.g. VA)
     * </pre>
     * so muMapped[j] depends on mu_B only through the indicator
     * "does constituent j map to element B", giving
     * <pre>
     *   ∂y[m]/∂mu_B = Σ_{j : elementIndexOnSublattice(j) == B} eMat[m][j]
     * </pre>
     * Chaining through ny[A][m] = ∂M_A/∂y[m] = a[s(m)] (see
     * {@link #compositionSensitivity}) by the chain rule:
     * <pre>
     *   eMatNC[A][B] = ∂M_A/∂mu_B = Σ_m ny[A][m] · ∂y[m]/∂mu_B
     * </pre>
     *
     * <p>This differs from the a priori formula in {@link PhaseEquilData}'s
     * class Javadoc (eMatNC[A][B] = Σ_m Σ_j ny[A][m]*eMat[m][j]*ny[B][j],
     * i.e. treating ∂y[j]/∂mu_B as ny[B][j]). That alternative assumes mu_B
     * conjugates to the composition variable M_A[B] directly in the phase's
     * internal stationarity condition. The formula implemented here instead
     * follows the chemical-potential-conjugate-to-site-fraction convention
     * that {@link #mapMuToSiteFractions} and Step 5 of {@link #compute}
     * already use elsewhere in this class (weight 1 per matching
     * constituent, not weighted by a[s] or normalization) — using the
     * PhaseEquilData formula here would be inconsistent with how dely
     * (and hence the Newton y-update) is actually computed by this adapter.
     *
     * <p>Units: eMat has units of (J/mol)^-1 (inverse Hessian); ny is
     * a[s], dimensionless site-count weight; so eMatNC has units of
     * (J/mol)^-1, matching Δmu in J/mol to ΔM_A in mol(A)/mol(FU) —
     * consistent with mA's current scale (mA = unnormalizedM(y)).
     *
     * <p>Note: unlike the mole-fraction-based derivation this replaces,
     * ny[A][m] = ∂M_A/∂y[m] does not depend on the current y (M_A is
     * linear in y), so this method no longer needs y or mA as input.
     *
     * @param eMat response matrix from Step 3 of {@link #compute}, nip×nip
     * @return     eMatNC, nc×nc
     */
    private double[][] computeEMatNC(double[][] eMat) {
        int nc  = elementNames_value.size();
        int nip = gibbs.nip();

        double[][] ny  = compositionSensitivity();
        double[] a = gibbs.stoichiometry();

        /*
         * dyDmu[m][B] = sum over j on sublattice s mapped to element B
         * of a[s] * eMat[m][j], accounting for the a[s] factor from
         * mapMuToSiteFractions.
         */
        double[][] dyDmu = new double[nip][nc];
        int[] ncSL = gibbs.constituentsPerSublattice();
        int[] offs = gibbs.offsets();
        for (int s = 0; s < gibbs.ns(); s++) {
            for (int j = 0; j < ncSL[s]; j++) {
                int elJ = elementIndexOnSublattice[s][j];
                if (elJ < 0) continue;
                int flatJ = offs[s] + j;
                for (int m = 0; m < nip; m++) {
                    dyDmu[m][elJ] +=
                        a[s] * eMat[m][flatJ];
                }
            }
        }

        double[][] eMatNC = new double[nc][nc];
        for (int A = 0; A < nc; A++) {
            for (int B = 0; B < nc; B++) {
                double sum = 0.0;
                for (int m = 0; m < nip; m++) {
                    sum += ny[A][m] * dyDmu[m][B];
                }
                eMatNC[A][B] = sum;
            }
        }
        return eMatNC;
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
            for (int i = 0; i < ncSL[s]; i++) {
                int elementIdx = elementIndexOnSublattice[s][i];
                if (elementIdx >= 0) {
                    gxMole[elementIdx] += a[s] * gxSite[offs[s] + i];
                }
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

    /**
     * Convert unconstrained logits into strictly positive site fractions.
     *
     * For a sublattice with n constituents:
     *
     *       y_i = exp(z_i) / (1 + sum exp(z_j))
     *       y_last = 1 / (1 + sum exp(z_j))
     *
     * Thus every y_i is strictly positive and the sublattice sum is exactly 1.
     */
    private static void logitsToSiteFractions(
            double[] z,
            double[] y,
            int[] nc,
            int[] off) {

        int p = 0;

        for (int s = 0; s < nc.length; s++) {

            int n = nc[s];

            /*
             * z values are referenced relative to the last constituent.
             * Subtracting the maximum avoids overflow in exp().
             */
            double maxZ = 0.0;

            for (int i = 0; i < n - 1; i++) {
                maxZ = Math.max(maxZ, z[p + i]);
            }

            double denominator = Math.exp(-maxZ);

            for (int i = 0; i < n - 1; i++) {
                denominator += Math.exp(z[p + i] - maxZ);
            }

            double inv = 1.0 / denominator;

            /*
             * Last constituent has logit zero.
             */
            y[off[s] + n - 1] = Math.exp(-maxZ) * inv;

            for (int i = 0; i < n - 1; i++) {
                y[off[s] + i] =
                    Math.exp(z[p + i] - maxZ) * inv;
            }

            p += n - 1;
        }
    }

    /**
     * Dense Gaussian elimination with partial pivoting.
     *
     * Used only for the small normal-equation system in the initialization
     * routine, so no external linear-algebra dependency is required.
     */
    private static double[] solveLinearSystem(
            double[][] A,
            double[] b) {

        int n = b.length;

        double[][] a = new double[n][n + 1];

        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, a[i], 0, n);
            a[i][n] = b[i];
        }

        for (int k = 0; k < n; k++) {

            int pivot = k;
            double max = Math.abs(a[k][k]);

            for (int i = k + 1; i < n; i++) {
                double v = Math.abs(a[i][k]);

                if (v > max) {
                    max = v;
                    pivot = i;
                }
            }

            if (!(max > 1.0e-20) || !Double.isFinite(max)) {
                /*
                 * Singular/ill-conditioned Jacobian.  Returning zero is
                 * preferable for an initializer: the positive current
                 * constitution remains valid and the Sundman solver can
                 * subsequently refine it.
                 */
                return new double[n];
            }

            if (pivot != k) {
                double[] tmp = a[k];
                a[k] = a[pivot];
                a[pivot] = tmp;
            }

            double diag = a[k][k];

            for (int j = k; j <= n; j++) {
                a[k][j] /= diag;
            }

            for (int i = 0; i < n; i++) {

                if (i == k) {
                    continue;
                }

                double factor = a[i][k];

                if (factor == 0.0) {
                    continue;
                }

                for (int j = k; j <= n; j++) {
                    a[i][j] -= factor * a[k][j];
                }
            }
        }

        double[] x = new double[n];

        for (int i = 0; i < n; i++) {
            x[i] = a[i][n];

            if (!Double.isFinite(x[i])) {
                return new double[n];
            }
        }

        return x;
    }
}
