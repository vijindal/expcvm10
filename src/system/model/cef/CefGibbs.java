package system.model.cef;

import java.util.Arrays;
import java.util.List;

/**
 * General Compound Energy Formalism (CEF) Gibbs-energy model.
 *
 * <p>The Gibbs energy is written as</p>
 *
 * <pre>
 * G = G_ref + G_id + G_ex
 * </pre>
 *
 * <p>where</p>
 *
 * <pre>
 * G_ref = sum_I P_I G_I
 *
 * G_id  = R T sum_s a_s sum_i y_si ln(y_si)
 * </pre>
 *
 * <p>The reference term is the CEF zeroth-order constituent-array
 * contribution. Interaction terms are represented by CefInteractionParam
 * objects. The Redlich-Kister order stored in an interaction is kept
 * separate from the CEF constituent-array structure.</p>
 */
public class CefGibbs {

    /**
     * Second-order automatic differentiation value.
     *
     * Stores value, first derivatives, and second derivatives
     * with respect to all CEF site-fraction variables.
     */
    private static final class AD2 {

        final double value;
        final double[] grad;
        final double[][] hess;

        private AD2(double value, double[] grad, double[][] hess) {
            this.value = value;
            this.grad = grad;
            this.hess = hess;
        }

        static AD2 constant(double value, int n) {
            return new AD2(value, new double[n], new double[n][n]);
        }

        static AD2 variable(double value, int index, int n) {
            double[] grad = new double[n];
            double[][] hess = new double[n][n];
            grad[index] = 1.0;
            return new AD2(value, grad, hess);
        }

        AD2 add(AD2 other) {
            int n = grad.length;
            double[] g = new double[n];
            double[][] h = new double[n][n];

            for (int i = 0; i < n; i++) {
                g[i] = grad[i] + other.grad[i];
                for (int j = 0; j < n; j++) {
                    h[i][j] = hess[i][j] + other.hess[i][j];
                }
            }

            return new AD2(value + other.value, g, h);
        }

        AD2 subtract(AD2 other) {
            int n = grad.length;
            double[] g = new double[n];
            double[][] h = new double[n][n];

            for (int i = 0; i < n; i++) {
                g[i] = grad[i] - other.grad[i];
                for (int j = 0; j < n; j++) {
                    h[i][j] = hess[i][j] - other.hess[i][j];
                }
            }

            return new AD2(value - other.value, g, h);
        }

        AD2 multiply(AD2 other) {
            int n = grad.length;
            double[] g = new double[n];
            double[][] h = new double[n][n];

            for (int i = 0; i < n; i++) {
                g[i] = grad[i] * other.value + value * other.grad[i];

                for (int j = 0; j < n; j++) {
                    h[i][j] = hess[i][j] * other.value
                            + grad[i] * other.grad[j]
                            + grad[j] * other.grad[i]
                            + value * other.hess[i][j];
                }
            }

            return new AD2(value * other.value, g, h);
        }

        AD2 scale(double factor) {
            int n = grad.length;
            double[] g = new double[n];
            double[][] h = new double[n][n];

            for (int i = 0; i < n; i++) {
                g[i] = factor * grad[i];
                for (int j = 0; j < n; j++) {
                    h[i][j] = factor * hess[i][j];
                }
            }

            return new AD2(factor * value, g, h);
        }

        AD2 pow(int exponent) {
            if (exponent < 0) {
                throw new IllegalArgumentException(
                        "AD2 power must be non-negative"
                );
            }

            int n = grad.length;

            if (exponent == 0) {
                return constant(1.0, n);
            }

            if (exponent == 1) {
                return this;
            }

            double x = value;

            double valuePow = Math.pow(x, exponent);
            double firstFactor = exponent * Math.pow(x, exponent - 1);

            double secondFactor =
                    exponent * (exponent - 1)
                    * Math.pow(x, exponent - 2);

            double[] g = new double[n];
            double[][] h = new double[n][n];

            for (int i = 0; i < n; i++) {

                g[i] =
                        firstFactor * grad[i];

                for (int j = 0; j < n; j++) {

                    h[i][j] =
                            firstFactor * hess[i][j]
                            + secondFactor
                            * grad[i]
                            * grad[j];
                }
            }

            return new AD2(
                    valuePow,
                    g,
                    h
            );
        }
    }

    private static final double R = 8.3144598;

    private final int ns;
    private final double[] a;
    private final int[] nc;

    /**
     * Offset of each sublattice in the flattened composition vector.
     */
    private final int[] offset;

    /**
     * Stride used for mixed-radix end-member indexing.
     */
    private final int[] stride;

    /**
     * Number of end members.
     */
    private final int totalEM;

    /**
     * Zeroth-order CEF constituent arrays.
     */
    private final CefEndMember[] endMembers;

    /**
     * Higher-order CEF interaction parameters.
     */
    private final List<CefInteractionParam> interactions;


    /**
     * Constructs a general CEF Gibbs-energy model.
     *
     * @param siteRatios number of sites on each sublattice
     * @param constituents number of constituents on each sublattice
     * @param endMembers complete zeroth-order constituent-array data
     * @param interactions CEF interaction parameters
     */
    public CefGibbs(double[] siteRatios,
                    int[] constituents,
                    CefEndMember[] endMembers,
                    List<CefInteractionParam> interactions) {

        if (siteRatios == null || constituents == null)
            throw new IllegalArgumentException(
                    "Site ratios and constituent counts must not be null.");

        if (siteRatios.length == 0)
            throw new IllegalArgumentException(
                    "CEF model must contain at least one sublattice.");

        if (siteRatios.length != constituents.length)
            throw new IllegalArgumentException(
                    "Site-ratio and constituent-count arrays must have equal length.");

        this.ns = siteRatios.length;

        this.a = siteRatios.clone();
        this.nc = constituents.clone();

        for (int s = 0; s < ns; s++) {
            if (!Double.isFinite(a[s]) || a[s] <= 0.0)
                throw new IllegalArgumentException(
                        "Invalid site ratio at sublattice " + s + ": " + a[s]);

            if (nc[s] <= 0)
                throw new IllegalArgumentException(
                        "Invalid constituent count at sublattice " + s + ": " + nc[s]);
        }

        this.offset = new int[ns];
        this.stride = new int[ns];

        offset[0] = 0;
        stride[0] = 1;

        long nEM = nc[0];

        for (int s = 1; s < ns; s++) {
            offset[s] = offset[s - 1] + nc[s - 1];

            long nextStride = (long) stride[s - 1] * nc[s - 1];

            if (nextStride > Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                        "Too many CEF end members for integer indexing.");

            stride[s] = (int) nextStride;

            nEM *= nc[s];

            if (nEM > Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                        "Too many CEF end members.");
        }

        this.totalEM = (int) nEM;

        if (endMembers == null || endMembers.length != totalEM)
            throw new IllegalArgumentException(
                    "Expected " + totalEM +
                    " end members but received " +
                    (endMembers == null ? 0 : endMembers.length));

        this.endMembers = endMembers.clone();

        for (int em = 0; em < totalEM; em++) {
            if (this.endMembers[em] == null)
                throw new IllegalArgumentException(
                        "Missing CEF end member at index " + em);
        }

        this.interactions =
                interactions == null
                ? List.of()
                : List.copyOf(interactions);

        validateInteractions();
    }


    /**
     * Validates interaction indices against this CEF model.
     */
    private void validateInteractions() {

        for (CefInteractionParam p : interactions) {

            if (p == null)
                throw new IllegalArgumentException(
                        "Interaction list contains null.");

            for (int k = 0; k < p.size(); k++) {

                int s = p.sublattice(k);
                int i = p.constituent(k);

                if (s < 0 || s >= ns)
                    throw new IllegalArgumentException(
                            "Interaction contains invalid sublattice: " + s);

                if (i < 0 || i >= nc[s])
                    throw new IllegalArgumentException(
                            "Interaction contains invalid constituent: "
                            + i + " on sublattice " + s);
            }
        }
    }


    /* ------------------------------------------------------------------
     * Basic model information
     * ------------------------------------------------------------------ */

    public int numberOfSublattices() {
        return ns;
    }

    public int numberOfConstituents(int s) {
        checkSublattice(s);
        return nc[s];
    }

    public int totalConstituents() {
        int n = 0;
        for (int x : nc) n += x;
        return n;
    }

    public int numberOfEndMembers() {
        return totalEM;
    }

    public double siteRatio(int s) {
        checkSublattice(s);
        return a[s];
    }


    /**
     * Returns the flattened composition-vector offset of a sublattice.
     */
    public int offset(int s) {
        checkSublattice(s);
        return offset[s];
    }


    /**
     * Returns the end-member index corresponding to a complete
     * constituent-index array.
     */
    public int endMemberIndex(int... constituentIdx) {

        if (constituentIdx == null || constituentIdx.length != ns)
            throw new IllegalArgumentException(
                    "Expected " + ns + " constituent indices.");

        int em = 0;

        for (int s = 0; s < ns; s++) {
            int i = constituentIdx[s];

            if (i < 0 || i >= nc[s])
                throw new IllegalArgumentException(
                        "Invalid constituent index " + i +
                        " on sublattice " + s);

            em += i * stride[s];
        }

        return em;
    }


    /**
     * Returns the end-member Gibbs-energy object.
     */
    public CefEndMember endMember(int... constituentIdx) {
        return endMembers[endMemberIndex(constituentIdx)];
    }


    /**
     * Backward-compatible two-sublattice accessor.
     */
    public CefEndMember endMember(int i, int j) {
        if (ns != 2)
            throw new IllegalStateException(
                    "Two-index endMember() is valid only for a two-sublattice model.");

        return endMembers[endMemberIndex(i, j)];
    }


    public int ns() {
        return ns;
    }

    public int nip() {
        return totalConstituents();
    }

    public double[] stoichiometry() {
        return a.clone();
    }

    public int[] constituentsPerSublattice() {
        return nc.clone();
    }

    public int[] offsets() {
        return offset.clone();
    }


    /* ------------------------------------------------------------------
     * Composition validation
     * ------------------------------------------------------------------ */

    /**
     * Checks the composition vector and allows zero fractions.
     *
     * <p>This is appropriate for Gibbs-energy evaluation because the
     * mathematical limit y ln y -> 0 is used at y = 0.</p>
     */
    private void checkY(double[] y) {

        if (y == null || y.length != totalConstituents())
            throw new IllegalArgumentException(
                    "Expected composition vector of length "
                    + totalConstituents());

        for (double v : y) {
            if (!Double.isFinite(v) || v < 0.0)
                throw new IllegalArgumentException(
                        "Site fractions must be finite and non-negative.");
        }

        for (int s = 0; s < ns; s++) {

            double sum = 0.0;

            for (int i = 0; i < nc[s]; i++)
                sum += y[offset[s] + i];

            if (!Double.isFinite(sum) ||
                Math.abs(sum - 1.0) > 1.0e-10) {

                throw new IllegalArgumentException(
                        "Sublattice " + s +
                        " fractions must sum to one; sum = " + sum);
            }
        }
    }


    /**
     * Derivative APIs require strictly positive site fractions because the
     * ideal configurational entropy derivatives are singular at y = 0.
     */
    private void checkPositiveY(double[] y) {

        checkY(y);

        for (double v : y) {
            if (v <= 0.0)
                throw new IllegalArgumentException(
                        "Analytical derivatives require strictly positive site fractions.");
        }
    }


    /* ------------------------------------------------------------------
     * Gibbs energy
     * ------------------------------------------------------------------ */

    /**
     * Evaluates the molar Gibbs energy.
     *
     * @param T temperature in K
     * @param y flattened site-fraction vector
     * @return Gibbs energy in J/mol
     */
    public double evaluate(double T, double[] y) {

        if (!Double.isFinite(T) || T <= 0.0)
            throw new IllegalArgumentException(
                    "Temperature must be finite and positive.");

        checkY(y);

        double gRef = referenceEnergy(T, y);
        double gId  = idealEnergy(T, y);
        double gEx  = excessEnergy(T, y);

        return gRef + gId + gEx;
    }


    /**
     * Zeroth-order CEF reference contribution.
     *
     * <pre>
     * G_ref = sum_I P_I G_I
     * </pre>
     *
     * where
     *
     * <pre>
     * P_I = product_s y[s][I_s].
     * </pre>
     */
    private double referenceEnergy(double T, double[] y) {

        double result = 0.0;

        for (int em = 0; em < totalEM; em++) {

            double probability = 1.0;
            int index = em;

            for (int s = 0; s < ns; s++) {

                int i = (index / stride[s]) % nc[s];

                probability *= y[offset[s] + i];

                if (probability == 0.0)
                    break;
            }

            if (probability != 0.0)
                result += probability * endMembers[em].G(T);
        }

        return result;
    }


    /**
     * Ideal configurational contribution.
     */
    private double idealEnergy(double T, double[] y) {

        double result = 0.0;

        for (int s = 0; s < ns; s++) {

            for (int i = 0; i < nc[s]; i++) {

                double yi = y[offset[s] + i];

                if (yi > 0.0)
                    result += a[s] * yi * Math.log(yi);
            }
        }

        return R * T * result;
    }


    /**
     * Evaluates the excess contribution from all CEF interactions.
     *
     * <p>The interaction representation stores the constituent factors.
     * The RK composition factor is handled separately.</p>
     */
    private double excessEnergy(double T, double[] y) {

        double result = 0.0;

        for (CefInteractionParam p : interactions) {

            double basis = interactionBasis(p, y);

            result += basis * p.L(T);
        }

        return result;
    }


    /**
     * Evaluates the composition basis of an interaction.
     *
     * <p>For the zero-order RK case the basis is simply the product of the
     * explicitly specified constituent fractions.</p>
     *
     * <p>For RK orders greater than zero, the standard binary RK factor is
     * generated when the interaction contains a pair of constituents on the
     * same sublattice. This is intentionally evaluated without division,
     * so the energy remains well behaved at composition boundaries.</p>
     */
    private double interactionBasis(CefInteractionParam p, double[] y) {

        double basis = 1.0;

        /*
         * First multiply the explicit constituent-array factors.
         */
        for (int k = 0; k < p.size(); k++) {

            int s = p.sublattice(k);
            int i = p.constituent(k);

            basis *= y[offset[s] + i];

            if (basis == 0.0)
                return 0.0;
        }

        /*
         * RK order zero requires no additional composition factor.
         */
        if (p.rkOrder() == 0)
            return basis;

        /*
         * Locate two distinct constituent factors on the same sublattice.
         *
         * This is the standard binary RK construction. Higher-order
         * composition dependence is represented by repeated powers of
         * (y_A - y_B).
         */
        int pairSL = -1;
        int pairA = -1;
        int pairB = -1;

        outer:
        for (int k = 0; k < p.size(); k++) {
            for (int j = k + 1; j < p.size(); j++) {

                if (p.sublattice(k) == p.sublattice(j) &&
                    p.constituent(k) != p.constituent(j)) {

                    pairSL = p.sublattice(k);
                    pairA = p.constituent(k);
                    pairB = p.constituent(j);
                    break outer;
                }
            }
        }

        if (pairSL < 0)
            throw new IllegalArgumentException(
                    "RK order " + p.rkOrder() +
                    " requires a distinct constituent pair on a sublattice.");

        double yA = y[offset[pairSL] + pairA];
        double yB = y[offset[pairSL] + pairB];

        double delta = yA - yB;

        for (int r = 0; r < p.rkOrder(); r++)
            basis *= delta;

        return basis;
    }


    /**
     * Builds the composition-dependent interaction basis and its
     * first and second derivatives via automatic differentiation.
     *
     * <p>The composition factors are constructed in exactly the same
     * way as interactionBasis(...): product of all explicitly specified
     * constituent fractions, followed (when rkOrder > 0) by the RK
     * composition factor.</p>
     */
    private AD2 interactionBasisAD(CefInteractionParam interaction, double[] y) {

        final int n = y.length;

        AD2 basis = AD2.constant(1.0, n);

        for (int k = 0; k < interaction.size(); k++) {

            int sl = interaction.sublattice(k);
            int ci = interaction.constituent(k);

            int idx = offset[sl] + ci;

            basis = basis.multiply(
                    AD2.variable(y[idx], idx, n)
            );
        }

        int order = interaction.rkOrder();

        if (order > 0) {

            int pairA = -1;
            int pairB = -1;
            int pairSL = -1;

            for (int k = 0; k < interaction.size(); k++) {

                for (int l = k + 1; l < interaction.size(); l++) {

                    if (interaction.sublattice(k) == interaction.sublattice(l)) {

                        pairSL = interaction.sublattice(k);

                        pairA = offset[pairSL] + interaction.constituent(k);

                        pairB = offset[pairSL] + interaction.constituent(l);

                        break;
                    }
                }

                if (pairSL >= 0)
                    break;
            }

            if (pairSL >= 0) {

                AD2 ya = AD2.variable(y[pairA], pairA, n);
                AD2 yb = AD2.variable(y[pairB], pairB, n);

                AD2 delta = ya.subtract(yb);

                basis = basis.multiply(delta.pow(order));
            }
        }

        return basis;
    }


    /* ------------------------------------------------------------------
     * Gradient
     * ------------------------------------------------------------------ */

    /**
     * Analytical gradient of G with respect to the flattened site fractions.
     */
    public double[] gradient(double T, double[] y) {

        if (!Double.isFinite(T) || T <= 0.0)
            throw new IllegalArgumentException(
                    "Temperature must be finite and positive.");

        checkPositiveY(y);

        double[] g = new double[y.length];

        referenceGradient(T, y, g);
        idealGradient(T, y, g);
        excessGradient(T, y, g);

        return g;
    }


    private void referenceGradient(double T,
                                   double[] y,
                                   double[] g) {

        for (int em = 0; em < totalEM; em++) {

            int[] idx = endMemberIndices(em);

            double G = endMembers[em].G(T);

            for (int s = 0; s < ns; s++) {

                double product = G;

                for (int q = 0; q < ns; q++) {

                    if (q == s)
                        continue;

                    product *= y[offset[q] + idx[q]];
                }

                g[offset[s] + idx[s]] += product;
            }
        }
    }


    private void idealGradient(double T,
                                double[] y,
                                double[] g) {

        for (int s = 0; s < ns; s++) {

            for (int i = 0; i < nc[s]; i++) {

                int k = offset[s] + i;
                g[k] += R * T * a[s] * (Math.log(y[k]) + 1.0);
            }
        }
    }


    private void excessGradient(double T,
                                double[] y,
                                double[] g) {

        for (CefInteractionParam p : interactions) {

            addInteractionGradient(p, T, y, g);
        }
    }


    /**
     * Gradient of an interaction basis.
     *
     * <p>The derivative is obtained by differentiating the product directly,
     * rather than dividing by a site fraction. This avoids 0/0 expressions
     * in the underlying polynomial calculation.</p>
     */
    private void addInteractionGradient(CefInteractionParam p,
                                        double T,
                                        double[] y,
                                        double[] g) {

        double L = p.L(T);

        /*
         * Product of explicit constituent factors.
         */
        int n = p.size();

        double[] factors = new double[n];

        for (int k = 0; k < n; k++) {
            factors[k] =
                    y[offset[p.sublattice(k)] + p.constituent(k)];
        }

        /*
         * Derivative of the explicit product.
         */
        for (int k = 0; k < n; k++) {

            double d = 1.0;

            for (int j = 0; j < n; j++) {

                if (j != k)
                    d *= factors[j];
            }

            int variable =
                    offset[p.sublattice(k)] + p.constituent(k);

            g[variable] += L * d;
        }

        /*
         * RK composition factor.
         */
        if (p.rkOrder() == 0)
            return;

        int[] pair = findRKPair(p);

        int pairSL = pair[0];
        int pairA  = pair[1];
        int pairB  = pair[2];

        double delta =
                y[offset[pairSL] + pairA]
                - y[offset[pairSL] + pairB];

        double rk = 1.0;

        for (int r = 0; r < p.rkOrder(); r++)
            rk *= delta;

        /*
         * Re-evaluate basis without RK factor.
         */
        double explicitProduct = 1.0;

        for (double f : factors)
            explicitProduct *= f;

        int varA = offset[pairSL] + pairA;
        int varB = offset[pairSL] + pairB;

        /*
         * Product derivative and RK derivative.
         *
         * d(P * delta^r)
         * = dP * delta^r + P * r delta^(r-1) d(delta)
         */
        for (int k = 0; k < n; k++) {

            double dP = 1.0;

            for (int j = 0; j < n; j++) {
                if (j != k)
                    dP *= factors[j];
            }

            int variable =
                    offset[p.sublattice(k)] + p.constituent(k);

            g[variable] +=
                    L * dP * rk;
        }

        if (p.rkOrder() > 0) {

            double deltaPower = 1.0;

            for (int r = 1; r < p.rkOrder(); r++)
                deltaPower *= delta;

            double rkDerivative =
                    p.rkOrder() * deltaPower * explicitProduct * L;

            g[varA] += rkDerivative;
            g[varB] -= rkDerivative;
        }
    }


    /* ------------------------------------------------------------------
     * Hessian
     * ------------------------------------------------------------------ */

    /**
     * Analytical Hessian of G.
     *
     * <p>As for the gradient, this method requires strictly positive site
     * fractions because the ideal entropy Hessian contains 1/y.</p>
     */
    public double[][] hessian(double T, double[] y) {

        if (!Double.isFinite(T) || T <= 0.0)
            throw new IllegalArgumentException(
                    "Temperature must be finite and positive.");

        checkPositiveY(y);

        int n = y.length;
        double[][] H = new double[n][n];

        referenceHessian(T, y, H);
        idealHessian(T, y, H);
        excessHessian(T, y, H);

        return H;
    }


    /**
     * Adds the reference-state Hessian contribution using second-order
     * automatic differentiation of the complete endmember probability.
     *
     * <p>For an endmember I:</p>
     * <pre>
     * P_I = product_s y[s][i_s]
     * </pre>
     *
     * <p>where i_s is the constituent occupying sublattice s.</p>
     *
     * <p>The AD2 construction automatically retains all cross-sublattice
     * second derivatives, avoiding hand-written formula errors.</p>
     */
    private void referenceHessian(double T,
                                  double[] y,
                                  double[][] H) {

        final int n = y.length;

        for (int em = 0; em < totalEM; em++) {

            int[] idx = endMemberIndices(em);

            AD2 probability =
                    AD2.constant(1.0, n);

            for (int s = 0; s < ns; s++) {

                int constituent = idx[s];

                int varIdx =
                        offset[s] + constituent;

                probability =
                        probability.multiply(
                                AD2.variable(
                                        y[varIdx],
                                        varIdx,
                                        n
                                )
                        );
            }

            double G =
                    endMembers[em].G(T);

            for (int i = 0; i < n; i++) {

                for (int j = 0; j < n; j++) {

                    H[i][j] +=
                            G * probability.hess[i][j];
                }
            }
        }
    }


    private void idealHessian(double T,
                              double[] y,
                              double[][] H) {

        for (int s = 0; s < ns; s++) {

            for (int i = 0; i < nc[s]; i++) {

                int k = offset[s] + i;

                H[k][k] +=
                        R * T * a[s] / y[k];
            }
        }
    }


    /**
     * Excess Hessian.
     *
     * <p>The interaction polynomial is evaluated through a small automatic
     * differentiation calculation. This is preferable to formulas involving
     * division by constituent fractions and remains valid for polynomial
     * terms whose composition factors vanish.</p>
     */
    private void excessHessian(double T,
                               double[] y,
                               double[][] H) {

        for (CefInteractionParam p : interactions)
            addInteractionHessian(p, T, y, H);
    }


    private void addInteractionHessian(CefInteractionParam p,
                                       double T,
                                       double[] y,
                                       double[][] H) {

        AD2 basis = interactionBasisAD(p, y);

        double L = p.L(T);

        int n = y.length;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                H[i][j] += L * basis.hess[i][j];
            }
        }
    }


    private int[] findRKPair(CefInteractionParam p) {

        for (int k = 0; k < p.size(); k++) {

            for (int j = k + 1; j < p.size(); j++) {

                if (p.sublattice(k) == p.sublattice(j) &&
                    p.constituent(k) != p.constituent(j)) {

                    return new int[] {
                        p.sublattice(k),
                        p.constituent(k),
                        p.constituent(j)
                    };
                }
            }
        }

        throw new IllegalArgumentException(
                "RK interaction requires two distinct constituents " +
                "on the same sublattice.");
    }


    /* ------------------------------------------------------------------
     * Temperature derivatives
     * ------------------------------------------------------------------ */

    /**
     * Temperature derivative of G at fixed site fractions.
     */
    public double temperatureDerivative(double T, double[] y) {

        if (!Double.isFinite(T) || T <= 0.0)
            throw new IllegalArgumentException(
                    "Temperature must be finite and positive.");

        checkY(y);

        double dGdT = 0.0;

        /*
         * Reference contribution.
         */
        for (int em = 0; em < totalEM; em++) {

            double probability = 1.0;
            int index = em;

            for (int s = 0; s < ns; s++) {

                int i = (index / stride[s]) % nc[s];

                probability *= y[offset[s] + i];

                if (probability == 0.0)
                    break;
            }

            if (probability != 0.0)
                dGdT +=
                        probability * endMembers[em].dGdT(T);
        }

        /*
         * Ideal contribution.
         */
        double entropyBasis = 0.0;

        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < nc[s]; i++) {

                double yi = y[offset[s] + i];

                if (yi > 0.0)
                    entropyBasis += a[s] * yi * Math.log(yi);
            }
        }

        dGdT += R * entropyBasis;

        /*
         * Excess contribution.
         */
        for (CefInteractionParam p : interactions)
            dGdT += interactionBasis(p, y) * p.dLdT(T);

        return dGdT;
    }


    /**
     * Temperature derivative of the composition gradient.
     */
    public double[] gradientDT(double T, double[] y) {

        if (!Double.isFinite(T) || T <= 0.0)
            throw new IllegalArgumentException(
                    "Temperature must be finite and positive.");

        checkPositiveY(y);

        int n = y.length;
        double[] result = new double[n];

        /*
         * Reference contribution.
         */
        for (int em = 0; em < totalEM; em++) {

            int[] idx = endMemberIndices(em);

            double dGdT = endMembers[em].dGdT(T);

            for (int s = 0; s < ns; s++) {

                double product = dGdT;

                for (int q = 0; q < ns; q++) {
                    if (q != s)
                        product *= y[offset[q] + idx[q]];
                }

                result[offset[s] + idx[s]] += product;
            }
        }

        /*
         * Ideal contribution.
         *
         * d/dT [R T a (ln y + 1)]
         * = R a (ln y + 1)
         */
        for (int s = 0; s < ns; s++) {

            for (int i = 0; i < nc[s]; i++) {

                int k = offset[s] + i;

                result[k] +=
                        R * a[s] * (Math.log(y[k]) + 1.0);
            }
        }

        /*
         * Excess contribution.
         */
        for (CefInteractionParam p : interactions)
            addInteractionGradientDT(p, T, y, result);

        return result;
    }


    private void addInteractionGradientDT(CefInteractionParam p,
                                          double T,
                                          double[] y,
                                          double[] result) {

        double dL = p.dLdT(T);

        int n = p.size();

        double[] factors = new double[n];

        for (int k = 0; k < n; k++) {

            factors[k] =
                    y[offset[p.sublattice(k)] + p.constituent(k)];
        }

        int rk = p.rkOrder();

        double rkFactor = 1.0;

        if (rk > 0) {

            int[] pair = findRKPair(p);

            double delta =
                    y[offset[pair[0]] + pair[1]]
                    - y[offset[pair[0]] + pair[2]];

            for (int r = 0; r < rk; r++)
                rkFactor *= delta;
        }

        for (int k = 0; k < n; k++) {

            double dP = 1.0;

            for (int j = 0; j < n; j++) {
                if (j != k)
                    dP *= factors[j];
            }

            int variable =
                    offset[p.sublattice(k)] + p.constituent(k);

            result[variable] +=
                    dL * dP * rkFactor;
        }

        if (rk > 0) {

            int[] pair = findRKPair(p);

            int varA =
                    offset[pair[0]] + pair[1];

            int varB =
                    offset[pair[0]] + pair[2];

            double delta = y[varA] - y[varB];

            double deltaPower = 1.0;

            for (int r = 1; r < rk; r++)
                deltaPower *= delta;

            double P = 1.0;

            for (double f : factors)
                P *= f;

            double contribution =
                    dL * rk * deltaPower * P;

            result[varA] += contribution;
            result[varB] -= contribution;
        }
    }


    /* ------------------------------------------------------------------
     * Utilities
     * ------------------------------------------------------------------ */

    private int[] endMemberIndices(int em) {

        int[] idx = new int[ns];

        for (int s = 0; s < ns; s++)
            idx[s] = (em / stride[s]) % nc[s];

        return idx;
    }


    private void checkSublattice(int s) {

        if (s < 0 || s >= ns)
            throw new IllegalArgumentException(
                    "Invalid sublattice index: " + s);
    }


    @Override
    public String toString() {

        return "CefGibbs{" +
                "ns=" + ns +
                ", siteRatios=" + Arrays.toString(a) +
                ", constituents=" + Arrays.toString(nc) +
                ", endMembers=" + totalEM +
                ", interactions=" + interactions.size() +
                '}';
    }
}