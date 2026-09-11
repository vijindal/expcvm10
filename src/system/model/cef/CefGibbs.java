package system.model.cef;

import system.database.tdb;
import system.database.tdb.Phase;
import system.database.tdb.Parameter;
import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import system.model.PhaseModelKind;
import util.Matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * General Compound Energy Formalism (CEF) Gibbs-energy model.
 *
 * <p>This is the CEF implementation of {@link GibbsEnergyModel}. It builds
 * itself directly from a parsed TDB database, in the style of pycalphad's
 * {@code Model(dbe, comps, phase_name)}:</p>
 *
 * <pre>
 * new CefGibbs(database, elements, "FCC_A1")
 * </pre>
 *
 * <p>The constructor extracts the sublattice structure, site ratios,
 * zeroth-order end-member parameters, and Redlich-Kister interaction
 * parameters from the database. All CEF math (site-fraction-space
 * {@code G}, {@code dG_dy}, {@code d2G_dy2}, {@code dG_dT},
 * {@code d2G_dydT}) and the rest of the {@link GibbsEnergyModel} contract
 * ({@code compute(...)}, {@code getInitialInternalVars(...)}) live here.</p>
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
public class CefGibbs extends GibbsEnergyModel {

    private static final Logger LOG = Logger.getLogger(CefGibbs.class.getName());

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

        /**
         * Composes a smooth scalar function {@code phi} with this AD2 value.
         * Given phi(u0), phi'(u0), phi''(u0) evaluated at the current value,
         * returns the AD2 for {@code phi(this)} by the chain rule:
         *
         * <pre>
         *   value   = phi(u0)
         *   grad_i  = phi'(u0) * u.grad_i
         *   hess_ij = phi''(u0) * u.grad_i * u.grad_j + phi'(u0) * u.hess_ij
         * </pre>
         */
        AD2 compose(double phi, double dphi, double d2phi) {
            int n = grad.length;
            double[] g = new double[n];
            double[][] h = new double[n][n];
            for (int i = 0; i < n; i++) {
                g[i] = dphi * grad[i];
                for (int j = 0; j < n; j++) {
                    h[i][j] = d2phi * grad[i] * grad[j] + dphi * hess[i][j];
                }
            }
            return new AD2(phi, g, h);
        }

        /** {@code this ^ k} for any real exponent k (value must be &gt; 0). */
        AD2 powReal(double k) {
            double u = value;
            return compose(Math.pow(u, k),
                           k * Math.pow(u, k - 1.0),
                           k * (k - 1.0) * Math.pow(u, k - 2.0));
        }

        /** Natural log of this value (value must be &gt; 0). */
        AD2 ln() {
            double u = value;
            return compose(Math.log(u), 1.0 / u, -1.0 / (u * u));
        }
    }

    private static final double R = 8.3144598;

    private final int ns;
    private final double[] a;
    private final int[] ncSub;

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


    // ══════════════════════════════════════════════════════════════════
    // Composition-facing (GibbsEnergyModel) state
    // ══════════════════════════════════════════════════════════════════

    /**
     * Inden-Hillert-Jarl magnetic model (holds the AFM factor and the
     * structure factor {@code p}); null if the phase carries no
     * {@code TYPE_DEFINITION ... MAGNETIC} hint.
     */
    private final MagneticContribution magnetic;

    /**
     * {@code TC} (Curie/Neel temperature) as a CEF constituent-array
     * quantity: end-member values plus interaction parameters, summed
     * exactly like the reference + excess Gibbs energy. Empty for a
     * non-magnetic phase.
     */
    private final CefEndMember[] tcEndMembers;
    private final List<CefInteractionParam> tcInteractions;

    /**
     * {@code BMAGN} (mean magnetic moment / Bohr-magneton number) as a CEF
     * constituent-array quantity, same structure as {@link #tcEndMembers}.
     */
    private final CefEndMember[] bmagEndMembers;
    private final List<CefInteractionParam> bmagInteractions;

    /**
     * {@code V0} (molar volume at the reference pressure) as a CEF
     * constituent-array quantity, same structure as {@link #tcEndMembers}.
     * Empty for a phase with no {@code V0} parameters (no volume/pressure
     * contribution).
     */
    private final CefEndMember[] v0EndMembers;
    private final List<CefInteractionParam> v0Interactions;

    /**
     * {@code VA} (thermal-expansion polynomial exponent, dimensionless)
     * as a CEF constituent-array quantity. {@code G_vol = V0(y)*exp(VA(T,y))
     * * (P - P0)}, following pycalphad's {@code Model.volume_energy}.
     */
    private final CefEndMember[] vaEndMembers;
    private final List<CefInteractionParam> vaInteractions;

    /** Phase name, e.g. "FCC_A1". */
    private final String phaseName_value;

    /** Ordered system element symbols this model was built for. */
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


    // ══════════════════════════════════════════════════════════════════
    // Constructors
    // ══════════════════════════════════════════════════════════════════

    /**
     * Builds a CEF model straight from a parsed TDB database, in the style
     * of pycalphad's {@code Model(dbe, comps, phase_name)}.
     *
     * @param database  loaded TDB database (element filtering is applied
     *                  internally via {@code getPhaseParam})
     * @param elements  ordered system element symbols
     * @param phaseName phase to build
     */
    public CefGibbs(tdb database, List<String> elements, String phaseName) {
        this(database, elements, phaseName, null, null, PhaseModelKind.AUTO);
    }

    /**
     * As {@link #CefGibbs(tdb, List, String)}, additionally accepting the
     * magnetic {@code A}/{@code p} hint maps parsed from
     * {@code TYPE_DEFINITION ... MAGNETIC} records and a
     * {@link PhaseModelKind} selector.
     */
    public CefGibbs(tdb database, List<String> elements, String phaseName,
                    Map<String, Double> affMap, Map<String, Double> pMap,
                    PhaseModelKind kind) {
        this(extract(database, elements, phaseName, affMap, pMap, kind));
    }

    /**
     * Private array-based constructor. Takes the fully extracted CEF
     * structure ({@link Parts}) and assembles both the site-fraction math
     * state and the composition-facing model state.
     */
    private CefGibbs(Parts p) {
        this(p.siteRatios, p.constituents, p.endMembers, p.interactions,
             p.magnetic, p.tcEndMembers, p.tcInteractions,
             p.bmagEndMembers, p.bmagInteractions,
             p.v0EndMembers, p.v0Interactions,
             p.vaEndMembers, p.vaInteractions,
             p.phaseName, new ArrayList<>(p.elements),
             p.constituentNames);
    }

    /**
     * Core constructor. Package-private so that unit tests exercising only
     * the pure CEF math (no TDB) can build a model from hand-assembled
     * end-member / interaction data; production code uses the
     * database constructors above.
     *
     * @param siteRatios       number of sites on each sublattice
     * @param constituents     number of constituents on each sublattice
     * @param endMembers       complete zeroth-order constituent-array data
     * @param interactions     CEF interaction parameters
     * @param magnetic         magnetic contribution, or null
     * @param phaseName        phase name (may be "" for pure-math tests)
     * @param elements         ordered system elements (may be empty for
     *                         pure-math tests; then the composition-facing
     *                         surface is unusable but the y-facing math works)
     * @param constituentNames per-sublattice constituent names, or null to
     *                         fall back to the dominant-sublattice 1:1 map
     */
    CefGibbs(double[] siteRatios,
             int[] constituents,
             CefEndMember[] endMembers,
             List<CefInteractionParam> interactions,
             MagneticContribution magnetic,
             CefEndMember[] tcEndMembers,
             List<CefInteractionParam> tcInteractions,
             CefEndMember[] bmagEndMembers,
             List<CefInteractionParam> bmagInteractions,
             CefEndMember[] v0EndMembers,
             List<CefInteractionParam> v0Interactions,
             CefEndMember[] vaEndMembers,
             List<CefInteractionParam> vaInteractions,
             String phaseName,
             ArrayList<String> elements,
             ArrayList<ArrayList<String>> constituentNames) {

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
        this.ncSub = constituents.clone();

        for (int s = 0; s < ns; s++) {
            if (!Double.isFinite(a[s]) || a[s] <= 0.0)
                throw new IllegalArgumentException(
                        "Invalid site ratio at sublattice " + s + ": " + a[s]);

            if (ncSub[s] <= 0)
                throw new IllegalArgumentException(
                        "Invalid constituent count at sublattice " + s + ": " + ncSub[s]);
        }

        this.offset = new int[ns];
        this.stride = new int[ns];

        offset[0] = 0;
        stride[0] = 1;

        long nEM = ncSub[0];

        for (int s = 1; s < ns; s++) {
            offset[s] = offset[s - 1] + ncSub[s - 1];

            long nextStride = (long) stride[s - 1] * ncSub[s - 1];

            if (nextStride > Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                        "Too many CEF end members for integer indexing.");

            stride[s] = (int) nextStride;

            nEM *= ncSub[s];

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

        /*
         * Magnetic (Inden-Hillert-Jarl) state. TC / BMAGN are CEF
         * constituent-array quantities, stored and summed exactly like the
         * reference + excess Gibbs energy. Empty lists / zero-length
         * arrays for a non-magnetic phase (magnetic == null).
         */
        this.magnetic = magnetic;
        this.tcEndMembers =
                tcEndMembers == null ? new CefEndMember[0] : tcEndMembers.clone();
        this.tcInteractions =
                tcInteractions == null ? List.of() : List.copyOf(tcInteractions);
        this.bmagEndMembers =
                bmagEndMembers == null ? new CefEndMember[0] : bmagEndMembers.clone();
        this.bmagInteractions =
                bmagInteractions == null ? List.of() : List.copyOf(bmagInteractions);

        /*
         * Volume / pressure (V0, VA) state. Same CEF constituent-array
         * structure as TC / BMAGN. Empty for a phase with no V0 parameters
         * (no volume/pressure contribution -- dG_dP == 0).
         */
        this.v0EndMembers =
                v0EndMembers == null ? new CefEndMember[0] : v0EndMembers.clone();
        this.v0Interactions =
                v0Interactions == null ? List.of() : List.copyOf(v0Interactions);
        this.vaEndMembers =
                vaEndMembers == null ? new CefEndMember[0] : vaEndMembers.clone();
        this.vaInteractions =
                vaInteractions == null ? List.of() : List.copyOf(vaInteractions);

        /*
         * Composition-facing model state.
         */
        this.phaseName_value = phaseName == null ? "" : phaseName;
        this.elementNames_value =
                elements == null ? new ArrayList<>() : new ArrayList<>(elements);
        this.elementIndexOnSublattice =
                buildElementIndexMap(ncSub, this.elementNames_value, constituentNames);

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

                if (i < 0 || i >= ncSub[s])
                    throw new IllegalArgumentException(
                            "Interaction contains invalid constituent: "
                            + i + " on sublattice " + s);
            }
        }
    }


    /* ------------------------------------------------------------------
     * Basic model information
     * ------------------------------------------------------------------ */

    /** Number of sublattices. */
    public int numSublattices() {
        return ns;
    }

    /** Number of constituents on sublattice {@code s}. */
    public int numConstituents(int s) {
        checkSublattice(s);
        return ncSub[s];
    }

    /** Total site-fraction variables (length of the flat {@code y} vector). */
    public int numSiteVars() {
        int n = 0;
        for (int x : ncSub) n += x;
        return n;
    }

    /** Number of end members. */
    public int numEndMembers() {
        return totalEM;
    }

    /** Site ratio a[s] for sublattice {@code s} (sites per formula unit). */
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

            if (i < 0 || i >= ncSub[s])
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


    /** Site ratios a[s] for every sublattice (sites per formula unit). */
    public double[] siteRatios() {
        return a.clone();
    }

    /** Number of constituents on each sublattice. */
    public int[] constituentsPerSublattice() {
        return ncSub.clone();
    }

    /** Flattened-vector offset of each sublattice's constituent block. */
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

        if (y == null || y.length != numSiteVars())
            throw new IllegalArgumentException(
                    "Expected composition vector of length "
                    + numSiteVars());

        for (double v : y) {
            if (!Double.isFinite(v) || v < 0.0)
                throw new IllegalArgumentException(
                        "Site fractions must be finite and non-negative.");
        }

        for (int s = 0; s < ns; s++) {

            double sum = 0.0;

            for (int i = 0; i < ncSub[s]; i++)
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
     * Molar Gibbs energy G(T, P, y), in J per mole of formula unit
     * ({@code G = Gref + Gid + Gex + Gmagn + Gvol}).
     */
    @Override
    public double G(double T, double P, double[] y) {

        if (!Double.isFinite(T) || T <= 0.0)
            throw new IllegalArgumentException(
                    "Temperature must be finite and positive.");

        checkY(y);

        return referenceEnergy(T, y) + idealEnergy(T, y) + excessEnergy(T, y)
                + magneticEnergy(T, y) + volumeEnergy(T, P, y);
    }

    /**
     * The reference (zeroth-order end-member) contribution alone,
     * {@code Gref = sum_I P_I G_I}, in J per mole of formula unit.
     */
    public double Gref(double T, double[] y) {
        checkY(y);
        return referenceEnergy(T, y);
    }

    /**
     * The ideal configurational contribution alone,
     * {@code Gid = R T sum_s a_s sum_i y_si ln y_si}, in J per mole of
     * formula unit.
     */
    public double Gid(double T, double[] y) {
        checkY(y);
        return idealEnergy(T, y);
    }

    /**
     * The excess (Redlich-Kister interaction) contribution alone, in J
     * per mole of formula unit.
     */
    public double Gex(double T, double[] y) {
        checkY(y);
        return excessEnergy(T, y);
    }

    /**
     * The volume/pressure contribution alone, {@code Gvol = V0(y)*
     * exp(VA(T,y))*(P-101325)}, in J per mole of formula unit; 0 if this
     * phase has no {@code V0} parameters.
     */
    public double Gvol(double T, double P, double[] y) {
        checkY(y);
        return volumeEnergy(T, P, y);
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

                int i = (index / stride[s]) % ncSub[s];

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

            for (int i = 0; i < ncSub[s]; i++) {

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
    @Override
    public double[] dG_dy(double T, double P, double[] y) {

        if (!Double.isFinite(T) || T <= 0.0)
            throw new IllegalArgumentException(
                    "Temperature must be finite and positive.");

        checkPositiveY(y);

        double[] g = new double[y.length];

        referenceGradient(T, y, g);
        idealGradient(T, y, g);
        excessGradient(T, y, g);

        AD2 magn = magneticEnergyAD(T, y);
        if (magn != null)
            for (int k = 0; k < g.length; k++)
                g[k] += magn.grad[k];

        AD2 vol = volumeEnergyAD(T, P, y);
        if (vol != null)
            for (int k = 0; k < g.length; k++)
                g[k] += vol.grad[k];

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

            for (int i = 0; i < ncSub[s]; i++) {

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
    @Override
    public double[][] d2G_dy2(double T, double P, double[] y) {

        if (!Double.isFinite(T) || T <= 0.0)
            throw new IllegalArgumentException(
                    "Temperature must be finite and positive.");

        checkPositiveY(y);

        int n = y.length;
        double[][] H = new double[n][n];

        referenceHessian(T, y, H);
        idealHessian(T, y, H);
        excessHessian(T, y, H);

        AD2 magn = magneticEnergyAD(T, y);
        if (magn != null)
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    H[i][j] += magn.hess[i][j];

        AD2 vol = volumeEnergyAD(T, P, y);
        if (vol != null)
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    H[i][j] += vol.hess[i][j];

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

            for (int i = 0; i < ncSub[s]; i++) {

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
     * Temperature derivative of G at fixed site fractions, at the
     * reference pressure P0 = 101325 Pa (i.e. excludes the volume
     * contribution's T-derivative; see {@link #dG_dT(double, double, double[])}
     * for the pressure-aware form).
     */
    public double dG_dT(double T, double[] y) {
        return dG_dT(T, P_REF, y);
    }

    /**
     * Temperature derivative of G at fixed pressure and site fractions,
     * including the volume contribution's T-derivative.
     */
    @Override
    public double dG_dT(double T, double P, double[] y) {

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

                int i = (index / stride[s]) % ncSub[s];

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
            for (int i = 0; i < ncSub[s]; i++) {

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

        /*
         * Magnetic contribution.
         */
        dGdT += magneticDGDT(T, y);

        /*
         * Volume contribution.
         */
        dGdT += volumeDGDT(T, P, y);

        return dGdT;
    }


    /**
     * Temperature derivative of the composition gradient, at the
     * reference pressure P0 = 101325 Pa (i.e. excludes the volume
     * contribution's mixed derivative; see
     * {@link #d2G_dydT(double, double, double[])} for the pressure-aware
     * form).
     */
    public double[] d2G_dydT(double T, double[] y) {
        return d2G_dydT(T, P_REF, y);
    }

    /**
     * Temperature derivative of the composition gradient at fixed
     * pressure, including the volume contribution's mixed derivative.
     */
    @Override
    public double[] d2G_dydT(double T, double P, double[] y) {

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

            for (int i = 0; i < ncSub[s]; i++) {

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

        /*
         * Magnetic contribution.
         */
        double[] magnDT = magneticGradientDT(T, y);
        for (int k = 0; k < n; k++)
            result[k] += magnDT[k];

        /*
         * Volume contribution.
         */
        double[] volDT = volumeGradientDT(T, P, y);
        for (int k = 0; k < n; k++)
            result[k] += volDT[k];

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
            idx[s] = (em / stride[s]) % ncSub[s];

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
                ", constituents=" + Arrays.toString(ncSub) +
                ", endMembers=" + totalEM +
                ", interactions=" + interactions.size() +
                '}';
    }


    /**
     * Builds the per-sublattice constituent-to-element index map.
     * If {@code constituentNames} is unavailable (e.g. legacy 2-arg
     * constructor), falls back to the previous 1:1 dominant-sublattice
     * assumption (index i on any sublattice maps to element i).
     */
    private static int[][] buildElementIndexMap(int[] ncSub,
                                               ArrayList<String> elements,
                                               ArrayList<ArrayList<String>> constituentNames) {
        int ns = ncSub.length;
        int[] ncSL = ncSub;
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
    // Stateless accessors
    // ══════════════════════════════════════════════════════════════════
    //
    // G/dG_dy/d2G_dy2 (GibbsEnergyModel's y-facing contract) are already
    // stateless, direct site-fraction-space methods on this class -- no
    // separate evaluator object exists. getGibbs() is kept as a
    // self-identity accessor for callers migrated from the earlier
    // CefGibbs+CefPhaseModelAdapter split.

    /** Self-identity accessor; kept for callers written against the
     *  earlier CefGibbs+CefPhaseModelAdapter split. */
    public CefGibbs getGibbs() { return this; }

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
     * dG/dP = V0(y)*exp(VA(T,y)), the molar volume at (T,y); 0 if this
     * phase has no {@code V0} parameters.
     */
    @Override
    public double dG_dP(double T, double P, double[] y) {
        checkY(y);
        return volumeDGDP(T, y);
    }

    /**
     * d2G/dydP = dV0/dy (at fixed T); zero vector if this phase has no
     * {@code V0} parameters.
     */
    @Override
    public double[] d2G_dydP(double T, double P, double[] y) {
        checkPositiveY(y);
        return volumeGradientDP(T, y);
    }

    /**
     * Element content:
     *
     *   M_A = sum_s a_s sum_i b_Ai y_si
     *
     * Returned values are unnormalized moles of element A per formula unit.
     */
    @Override
    public double[] moles(double[] y) {
        return unnormalizedM(y);
    }

    /**
     * Jacobian dMoles/dy_m.
     *
     * M_A is linear in y, so this Jacobian is constant for a given
     * phase model.
     */
    @Override
    public double[][] dMoles_dy() {

        int nc = elementNames_value.size();
        int nip = numSiteVars();

        double[][] dM = new double[nc][nip];

        double[] a = siteRatios();
        int[] offs = offsets();
        int[] ncSL = constituentsPerSublattice();

        for (int s = 0; s < numSublattices(); s++) {

            for (int i = 0; i < ncSL[s]; i++) {

                int element = elementIndexOnSublattice[s][i];

                if (element < 0)
                    continue;

                int k = offs[s] + i;

                dM[element][k] = a[s];
            }
        }

        return dM;
    }

    // ══════════════════════════════════════════════════════════════════
    // Phase Identity (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    @Override public String phaseName()        { return phaseName_value; }
    @Override public String modelType()        { return "CEF"; }

    /** True if this phase carries a magnetic contribution. */
    public boolean hasMagnetic()               { return magnetic != null; }
    @Override public ArrayList<String> elementNames() { return new ArrayList<>(elementNames_value); }
    @Override public String[] componentList()  { return elementNames_value.toArray(new String[0]); }
    @Override public int numComponents()       { return elementNames_value.size(); }
    @Override public int numTotalParams()      { return numSiteVars(); }

    @Override
    public double nfu() {
        double sum = 0.0;
        for (double a : siteRatios()) sum += a;
        return sum;
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
         * strictly positive, so CefGibbs.dG_dy() is always evaluated away
         * from the logarithmic singularity.
         */

        final double EPS = 1.0e-10;
        final int ns  = numSublattices();
        final int nip = numSiteVars();

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
        int[] nc = constituentsPerSublattice();
        int[] off = offsets();

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
        double[] a = siteRatios();
        int[] offs = offsets();
        int[] ncSL = constituentsPerSublattice();
        for (int s = 0; s < numSublattices(); s++) {
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
        if (y == null || y.length != numSiteVars()) return false;
        int[] ncSL = constituentsPerSublattice();
        int[] off = offsets();
        for (int s = 0; s < numSublattices(); s++) {
            double sum = 0.0;
            for (int i = 0; i < ncSL[s]; i++) {
                if (y[off[s] + i] < -1e-12) return false;
                sum += y[off[s] + i];
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
        int nip = numSiteVars();
        int nc = elementNames_value.size();

        // Step 1: evaluate G and all derivatives
        double G = this.G(T, P, y);
        double[] Gx = this.dG_dy(T, P, y);
        double[][] Gxx = this.d2G_dy2(T, P, y);
        double[] GxT = this.d2G_dydT(T, P, y);
        double[] GxP = this.d2G_dydP(T, P, y);

        // Step 2: assemble phase matrix M (nip+ns)×(nip+ns), with one
        // Lagrange-multiplier row/column per sublattice s, enforcing
        // Sigma_i y[s,i] = 1 independently for each sublattice (a single
        // global border enforcing only Sigma_m y[m] = const, as before,
        // under-constrains any phase with more than one sublattice that
        // has more than one constituent -- see M2 Step 4 diagnostic).
        int ns = numSublattices();
        int[] offs = offsets();
        int[] ncSL = constituentsPerSublattice();
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
        double[] a = siteRatios();

        for (int s = 0; s < numSublattices(); s++) {
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
        LOG.fine("  ns=" + numSublattices() + " nip=" + numSiteVars());
        LOG.fine("  elements=" + elementNames_value);
        LOG.fine("  T=" + T + " K, P=" + P + " Pa");
        LOG.fine("  y=" + java.util.Arrays.toString(y));
    }

    @Override
    public void printDerivatives() {
        if (y == null) {
            LOG.fine("(no site fractions set)");
            return;
        }
        LOG.fine("G=" + G(T, P, y));
        LOG.fine("dG/dT=" + dG_dT(T, y));
        LOG.fine("dG/dP=" + dG_dP(T, P, y));
        LOG.fine("dG/dy=" + java.util.Arrays.toString(dG_dy(T, P, y)));
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
        int nip  = numSiteVars();
        int[]    offs = offsets();
        int[]    ncSL = constituentsPerSublattice();
        double[] a    = siteRatios();
        double[] muY  = new double[nip];

        for (int s = 0; s < numSublattices(); s++) {
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
        int nip = numSiteVars();
        double[] a    = siteRatios();
        int[]    offs = offsets();
        int[]    ncSL = constituentsPerSublattice();

        double[][] ny = new double[nc][nip];
        for (int s = 0; s < numSublattices(); s++) {
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
        int nip = numSiteVars();

        double[][] ny  = compositionSensitivity();
        double[] a = siteRatios();

        /*
         * dyDmu[m][B] = sum over j on sublattice s mapped to element B
         * of a[s] * eMat[m][j], accounting for the a[s] factor from
         * mapMuToSiteFractions.
         */
        double[][] dyDmu = new double[nip][nc];
        int[] ncSL = constituentsPerSublattice();
        int[] offs = offsets();
        for (int s = 0; s < numSublattices(); s++) {
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

    /* ------------------------------------------------------------------
     * Magnetic contribution (Inden-Hillert-Jarl)
     *
     * Follows pycalphad's Model.magnetic_energy. TC and BMAGN are CEF
     * constituent-array quantities (magneticFieldAD sums them exactly like
     * G_ref + G_ex). The AFM ("Weiss") factor folds the Neel branch into
     * the Curie branch:
     *
     *   Tc   = curieRaw / afm   if curieRaw <= 0,  else curieRaw
     *   beta = mmm      / afm   if mmm      <= 0,  else mmm
     *   tau  = T / ( (curieRaw > 0 ? curieRaw : curieRaw/afm) + 1e-9 )
     *   G_magn = R T ln(beta+1) f(tau)
     *
     * with f(tau) the Inden polynomial (see the nested MagneticContribution).
     *
     * pycalphad divides by _site_ratio_normalization to get J/mol-atom;
     * this class works in J/mol-formula-unit throughout, so no division is
     * applied here (consistent with G_ref / G_id / G_ex).
     *
     * TC / BMAGN polynomials are treated as temperature-independent (they
     * are plain numbers in every real database), so f depends on T only
     * through tau = T/Tc; this makes dG_dT and d2G_dydT closed-form.
     * ------------------------------------------------------------------ */

    /** Small offset used (as in pycalphad) to keep tau finite when Tc -> 0. */
    private static final double TAU_EPS = 1.0e-9;

    /**
     * AD2 (value + d/dy + d2/dy2) of a CEF constituent-array field
     * {@code Sigma_I P_I f_I + Sigma_p basis_p f_p(T)} over a TC or BMAGN
     * end-member array + interaction list.
     */
    private AD2 magneticFieldAD(CefEndMember[] fieldEM,
                                List<CefInteractionParam> fieldIA,
                                double T, double[] y) {

        int n = y.length;
        AD2 sum = AD2.constant(0.0, n);

        for (int em = 0; em < fieldEM.length; em++) {
            if (fieldEM[em] == null)
                continue;
            int[] idx = endMemberIndices(em);
            AD2 prob = AD2.constant(1.0, n);
            for (int s = 0; s < ns; s++) {
                int k = offset[s] + idx[s];
                prob = prob.multiply(AD2.variable(y[k], k, n));
            }
            sum = sum.add(prob.scale(fieldEM[em].G(T)));
        }

        for (CefInteractionParam p : fieldIA) {
            AD2 basis = interactionBasisAD(p, y);
            sum = sum.add(basis.scale(p.L(T)));
        }

        return sum;
    }

    /** Same field as {@link #magneticFieldAD} but value-only (no derivatives). */
    private double magneticFieldValue(CefEndMember[] fieldEM,
                                      List<CefInteractionParam> fieldIA,
                                      double T, double[] y) {
        double sum = 0.0;
        for (int em = 0; em < fieldEM.length; em++) {
            if (fieldEM[em] == null)
                continue;
            int[] idx = endMemberIndices(em);
            double prob = 1.0;
            for (int s = 0; s < ns; s++)
                prob *= y[offset[s] + idx[s]];
            if (prob != 0.0)
                sum += prob * fieldEM[em].G(T);
        }
        for (CefInteractionParam p : fieldIA)
            sum += interactionBasis(p, y) * p.L(T);
        return sum;
    }

    /**
     * Inden f(tau) as an AD2 in y, given tau as an AD2 in y and the raw
     * (possibly negative) Curie temperature value (used only to choose the
     * tau &lt; 1 / tau &gt;= 1 branch, matching pycalphad's Piecewise).
     */
    private static AD2 indenF(AD2 tau, double p) {
        double A = 518.0 / 1125.0 + (11692.0 / 15975.0) * (1.0 / p - 1.0);
        int n = tau.grad.length;
        if (tau.value < 1.0) {
            AD2 term = tau.powReal(-1.0).scale(79.0 / (140.0 * p));
            term = term.add(
                    tau.powReal(3.0).scale(1.0 / 6.0)
                       .add(tau.powReal(9.0).scale(1.0 / 135.0))
                       .add(tau.powReal(15.0).scale(1.0 / 600.0))
                       .scale((474.0 / 497.0) * (1.0 / p - 1.0)));
            return AD2.constant(1.0, n).subtract(term.scale(1.0 / A));
        } else {
            AD2 term = tau.powReal(-5.0).scale(1.0 / 10.0)
                    .add(tau.powReal(-15.0).scale(1.0 / 315.0))
                    .add(tau.powReal(-25.0).scale(1.0 / 1500.0));
            return term.scale(-1.0 / A);
        }
    }

    /** Scalar Inden f(tau). */
    private static double indenFScalar(double tau, double p) {
        double A = 518.0 / 1125.0 + (11692.0 / 15975.0) * (1.0 / p - 1.0);
        if (tau < 1.0) {
            double s = (79.0 / (140.0 * p)) * (1.0 / tau)
                    + (474.0 / 497.0) * (1.0 / p - 1.0)
                      * (Math.pow(tau, 3) / 6.0 + Math.pow(tau, 9) / 135.0
                         + Math.pow(tau, 15) / 600.0);
            return 1.0 - s / A;
        }
        double s = Math.pow(tau, -5) / 10.0 + Math.pow(tau, -15) / 315.0
                + Math.pow(tau, -25) / 1500.0;
        return -s / A;
    }

    /** Scalar Inden df/dtau. */
    private static double indenDfScalar(double tau, double p) {
        double A = 518.0 / 1125.0 + (11692.0 / 15975.0) * (1.0 / p - 1.0);
        if (tau < 1.0) {
            double ds = -(79.0 / (140.0 * p)) * (1.0 / (tau * tau))
                    + (474.0 / 497.0) * (1.0 / p - 1.0)
                      * (Math.pow(tau, 2) / 2.0 + Math.pow(tau, 8) * (9.0 / 135.0)
                         + Math.pow(tau, 14) * (15.0 / 600.0));
            return -ds / A;
        }
        double ds = -5.0 * Math.pow(tau, -6) / 10.0
                - 15.0 * Math.pow(tau, -16) / 315.0
                - 25.0 * Math.pow(tau, -26) / 1500.0;
        return -ds / A;
    }

    /**
     * Builds the full AD2 (in y) of the magnetic Gibbs energy
     * {@code R T ln(beta+1) f(tau)} at fixed T, or null if this phase is
     * non-magnetic. Returns a zero AD2 when the state has no ordering
     * (pycalphad's Piecewise falls through to 0): {@code beta <= -1},
     * {@code curieRaw == 0}, or {@code T} exactly equal to the raw Curie
     * temperature.
     */
    private AD2 magneticEnergyAD(double T, double[] y) {
        if (magnetic == null)
            return null;

        int n = y.length;
        double afm = magnetic.aff();
        double p   = magnetic.p();

        AD2 curieRaw = magneticFieldAD(tcEndMembers, tcInteractions, T, y);
        AD2 mmm      = magneticFieldAD(bmagEndMembers, bmagInteractions, T, y);

        // beta = mmm / afm  if mmm <= 0, else mmm     (pycalphad Piecewise)
        AD2 beta = (mmm.value <= 0.0) ? mmm.scale(1.0 / afm) : mmm;

        double curie = curieRaw.value;
        if (magneticZero(curie, T, afm, beta.value))
            return AD2.constant(0.0, n);

        // denom = (curie > 0 ? curie : curie/afm) + TAU_EPS  (pycalphad guard)
        AD2 denom = (curie > 0.0) ? curieRaw : curieRaw.scale(1.0 / afm);
        denom = denom.add(AD2.constant(TAU_EPS, n));

        AD2 tau  = denom.powReal(-1.0).scale(T);          // tau = T / denom
        AD2 fTau = indenF(tau, p);
        AD2 lnB  = beta.add(AD2.constant(1.0, n)).ln();   // ln(beta + 1)

        return fTau.multiply(lnB).scale(R * T);
    }

    /**
     * True when pycalphad's magnetic Piecewise falls through to 0 for this
     * (raw Curie temperature, T, afm, beta): beta &lt;= -1, curie == 0, or
     * T exactly on the branch boundary (curie == T for Tc &gt; 0, or
     * curie/afm == T for Tc &lt; 0).
     */
    private static boolean magneticZero(double curie, double T, double afm, double beta) {
        if (beta <= -1.0 || curie == 0.0)
            return true;
        if (curie > 0.0)
            return curie == T;          // neither curie>T nor curie<T
        return (curie / afm) == T;      // Tc < 0: boundary at curie/afm == T
    }

    /** Value-only magnetic Gibbs energy (J/mol formula unit); 0 if none. */
    private double magneticEnergy(double T, double[] y) {
        if (magnetic == null)
            return 0.0;
        double afm = magnetic.aff();
        double p   = magnetic.p();
        double curieRaw = magneticFieldValue(tcEndMembers, tcInteractions, T, y);
        double mmm      = magneticFieldValue(bmagEndMembers, bmagInteractions, T, y);
        double beta = (mmm <= 0.0) ? mmm / afm : mmm;
        if (magneticZero(curieRaw, T, afm, beta))
            return 0.0;
        double tcEff = (curieRaw > 0.0) ? curieRaw : curieRaw / afm;
        double tau = T / (tcEff + TAU_EPS);
        return R * T * Math.log(beta + 1.0) * indenFScalar(tau, p);
    }

    /**
     * d/dT of the magnetic Gibbs energy at fixed y. With T-independent
     * TC / BMAGN, ln(beta+1) is constant in T and f depends on T only via
     * tau = T/Tc, so
     *
     *   d/dT [ R T ln(b+1) f(tau) ] = R ln(b+1) ( f(tau) + tau f'(tau) ).
     */
    private double magneticDGDT(double T, double[] y) {
        if (magnetic == null)
            return 0.0;
        double afm = magnetic.aff();
        double p   = magnetic.p();
        double curieRaw = magneticFieldValue(tcEndMembers, tcInteractions, T, y);
        double mmm      = magneticFieldValue(bmagEndMembers, bmagInteractions, T, y);
        double beta = (mmm <= 0.0) ? mmm / afm : mmm;
        if (magneticZero(curieRaw, T, afm, beta))
            return 0.0;
        double tcEff = (curieRaw > 0.0) ? curieRaw : curieRaw / afm;
        double tau = T / (tcEff + TAU_EPS);
        return R * Math.log(beta + 1.0)
                * (indenFScalar(tau, p) + tau * indenDfScalar(tau, p));
    }

    /**
     * d/dT of the magnetic site-fraction gradient, {@code d2Gmagn/dy dT},
     * at fixed y. Same T-independence assumption as {@link #magneticDGDT};
     * obtained as the y-gradient of the AD2 for
     * {@code R ln(beta(y)+1) ( f(tau(y)) + tau(y) f'(tau(y)) )}.
     */
    private double[] magneticGradientDT(double T, double[] y) {
        int n = y.length;
        if (magnetic == null)
            return new double[n];
        double afm = magnetic.aff();
        double p   = magnetic.p();

        AD2 curieRaw = magneticFieldAD(tcEndMembers, tcInteractions, T, y);
        AD2 mmm      = magneticFieldAD(bmagEndMembers, bmagInteractions, T, y);
        AD2 beta = (mmm.value <= 0.0) ? mmm.scale(1.0 / afm) : mmm;

        double curie = curieRaw.value;
        if (magneticZero(curie, T, afm, beta.value))
            return new double[n];

        AD2 denom = (curie > 0.0) ? curieRaw : curieRaw.scale(1.0 / afm);
        denom = denom.add(AD2.constant(TAU_EPS, n));

        AD2 tau  = denom.powReal(-1.0).scale(T);
        AD2 fTau = indenF(tau, p);
        // AD2 of f'(tau(y)) by composing the scalar f' through tau. Only the
        // first derivative (.grad = f''(tau0)*tau.grad) is consumed below,
        // via expr.grad, so the .hess coefficient is left at 0.
        AD2 dFtau = tau.compose(indenDfScalar(tau.value, p),
                                indenD2fScalar(tau.value, p),
                                0.0);

        AD2 lnB = beta.add(AD2.constant(1.0, n)).ln();
        AD2 bracket = fTau.add(tau.multiply(dFtau));       // f + tau f'
        AD2 expr = bracket.multiply(lnB).scale(R);         // R ln(b+1)(f+tau f')
        return expr.grad.clone();
    }

    /** Scalar Inden d2f/dtau2 (needed for the AD2 chain in magneticGradientDT). */
    private static double indenD2fScalar(double tau, double p) {
        double A = 518.0 / 1125.0 + (11692.0 / 15975.0) * (1.0 / p - 1.0);
        if (tau < 1.0) {
            double dds = (2.0 * 79.0 / (140.0 * p)) * (1.0 / (tau * tau * tau))
                    + (474.0 / 497.0) * (1.0 / p - 1.0)
                      * (tau
                         + Math.pow(tau, 7) * (8.0 * 9.0 / 135.0)
                         + Math.pow(tau, 13) * (14.0 * 15.0 / 600.0));
            return -dds / A;
        }
        double dds = 30.0 * Math.pow(tau, -7) / 10.0
                + 240.0 * Math.pow(tau, -17) / 315.0
                + 650.0 * Math.pow(tau, -27) / 1500.0;
        return -dds / A;
    }

    /* ------------------------------------------------------------------
     * Volume / pressure contribution
     *
     * Follows pycalphad's Model.volume_energy. V0 and VA are CEF
     * constituent-array quantities (magneticFieldAD/magneticFieldValue
     * sum them exactly like TC/BMAGN):
     *
     *   V_p0(T,y) = V0(y) * exp(VA(T,y))
     *   G_vol     = V_p0(T,y) * (P - P0),   P0 = 101325 Pa
     *
     * V_p0 is linear in (P - P0), so unlike the magnetic term every
     * derivative here is closed-form with no branch/AD2 composition
     * needed beyond what magneticFieldAD already provides:
     *
     *   dG_vol/dy   = dV_p0/dy * (P - P0)
     *   d2G_vol/dy2 = d2V_p0/dy2 * (P - P0)
     *   dG_vol/dP   = V_p0(T,y)
     *   d2G_vol/dydP = dV_p0/dy
     *   dG_vol/dT   = dV_p0/dT * (P - P0)
     *   d2G_vol/dydT = d2V_p0/dydT * (P - P0)
     *
     * VK (isothermal compressibility) is rejected in extract() -- as in
     * pycalphad, its pressure dependence is not implemented.
     *
     * pycalphad divides by _site_ratio_normalization to get J/mol-atom;
     * this class works in J/mol-formula-unit throughout, so no division
     * is applied here (consistent with G_ref/G_id/G_ex and the magnetic
     * term above).
     * ------------------------------------------------------------------ */

    /** Reference pressure P0 in the TDB V0/(P-P0) convention. */
    private static final double P_REF = 101325.0;

    /** True if this phase has a volume/pressure contribution (V0 present). */
    private boolean hasVolume() {
        return v0EndMembers.length > 0
                && (anyNonNull(v0EndMembers) || !v0Interactions.isEmpty());
    }

    private static boolean anyNonNull(CefEndMember[] arr) {
        for (CefEndMember em : arr) if (em != null) return true;
        return false;
    }

    /**
     * V_p0(T,y) = V0(y) * exp(VA(T,y)), as an AD2 in y.
     */
    private AD2 molarVolumeAD(double T, double[] y) {
        AD2 v0 = magneticFieldAD(v0EndMembers, v0Interactions, T, y);
        if (vaEndMembers.length == 0 && vaInteractions.isEmpty())
            return v0;
        AD2 va = magneticFieldAD(vaEndMembers, vaInteractions, T, y);
        AD2 expVa = va.compose(Math.exp(va.value), Math.exp(va.value), Math.exp(va.value));
        return v0.multiply(expVa);
    }

    /** Value-only V_p0(T,y). */
    private double molarVolumeValue(double T, double[] y) {
        double v0 = magneticFieldValue(v0EndMembers, v0Interactions, T, y);
        if (vaEndMembers.length == 0 && vaInteractions.isEmpty())
            return v0;
        double va = magneticFieldValue(vaEndMembers, vaInteractions, T, y);
        return v0 * Math.exp(va);
    }

    /** Value-only volume Gibbs energy contribution G_vol(T,P,y); 0 if none. */
    private double volumeEnergy(double T, double P, double[] y) {
        if (!hasVolume())
            return 0.0;
        return molarVolumeValue(T, y) * (P - P_REF);
    }

    /** AD2 (in y) of the volume Gibbs energy contribution; null if none. */
    private AD2 volumeEnergyAD(double T, double P, double[] y) {
        if (!hasVolume())
            return null;
        return molarVolumeAD(T, y).scale(P - P_REF);
    }

    /** dG_vol/dP = V_p0(T,y); 0 if no volume contribution. */
    private double volumeDGDP(double T, double[] y) {
        if (!hasVolume())
            return 0.0;
        return molarVolumeValue(T, y);
    }

    /** d2G_vol/dydP = dV_p0/dy; zero vector if no volume contribution. */
    private double[] volumeGradientDP(double T, double[] y) {
        int n = y.length;
        if (!hasVolume())
            return new double[n];
        return molarVolumeAD(T, y).grad.clone();
    }

    /**
     * dG_vol/dT = dV_p0/dT * (P - P0). V0/VA polynomials are generally
     * T-dependent (VA especially), so unlike the magnetic block this uses
     * a central finite difference in T on {@link #molarVolumeValue}
     * rather than a closed-form dV0/dT -- V0/VA are rarely used with
     * strongly nonlinear T-dependence, and this keeps the AD2 engine
     * (which differentiates in y, not T) out of the loop.
     */
    private double volumeDGDT(double T, double P, double[] y) {
        if (!hasVolume())
            return 0.0;
        double h = Math.max(1.0e-4 * T, 1.0e-6);
        double vPlus  = molarVolumeValue(T + h, y);
        double vMinus = molarVolumeValue(T - h, y);
        double dVdT = (vPlus - vMinus) / (2.0 * h);
        return dVdT * (P - P_REF);
    }

    /** d2G_vol/dydT = d2V_p0/dydT * (P - P0), via the same finite difference in T. */
    private double[] volumeGradientDT(double T, double P, double[] y) {
        int n = y.length;
        if (!hasVolume())
            return new double[n];
        double h = Math.max(1.0e-4 * T, 1.0e-6);
        double[] gPlus  = molarVolumeAD(T + h, y).grad;
        double[] gMinus = molarVolumeAD(T - h, y).grad;
        double[] result = new double[n];
        for (int k = 0; k < n; k++)
            result[k] = (gPlus[k] - gMinus[k]) / (2.0 * h) * (P - P_REF);
        return result;
    }

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


    // ══════════════════════════════════════════════════════════════════
    // TDB extraction  (pycalphad-style: build the model from a database)
    // ══════════════════════════════════════════════════════════════════

    /** Fully-extracted CEF structure, produced by {@link #extract}. */
    private static final class Parts {
        double[] siteRatios;
        int[] constituents;
        CefEndMember[] endMembers;
        List<CefInteractionParam> interactions;
        MagneticContribution magnetic;
        CefEndMember[] tcEndMembers;
        List<CefInteractionParam> tcInteractions;
        CefEndMember[] bmagEndMembers;
        List<CefInteractionParam> bmagInteractions;
        CefEndMember[] v0EndMembers;
        List<CefInteractionParam> v0Interactions;
        CefEndMember[] vaEndMembers;
        List<CefInteractionParam> vaInteractions;
        String phaseName;
        List<String> elements;
        ArrayList<ArrayList<String>> constituentNames;
    }

    /**
     * Reads the sublattice structure, site ratios, zeroth-order
     * end-member parameters and Redlich-Kister interaction parameters for
     * {@code phaseName} out of {@code database}. This is the logic that
     * previously lived in {@code PhaseModelFactory.build}.
     */
    private static Parts extract(tdb database,
                                 List<String> elements,
                                 String phaseName,
                                 Map<String, Double> affMap,
                                 Map<String, Double> pMap,
                                 PhaseModelKind kind) {

        if (database == null)
            throw new IllegalArgumentException("Database must not be null.");

        if (phaseName == null || phaseName.isBlank())
            throw new IllegalArgumentException("Phase name must not be blank.");

        if (elements == null)
            throw new IllegalArgumentException("Element list must not be null.");

        if (kind == PhaseModelKind.CVM)
            throw new UnsupportedOperationException(
                    "CVM model not yet implemented (phase " + phaseName + ")");


        /*
         * ---------------------------------------------------------------
         * 1. Locate phase
         * ---------------------------------------------------------------
         */

        Phase phase = database.getPhase(phaseName);

        if (phase == null)
            throw new IllegalArgumentException(
                    "Phase not found: " + phaseName);


        /*
         * ---------------------------------------------------------------
         * 2. Read CEF structure
         * ---------------------------------------------------------------
         *
         * The CEF path below is fully sublattice-count-generic. A
         * one-sublattice phase is the substitutional case: end members =
         * pure elements, interactions = L(A,B;n) on the single sublattice.
         */

        int ns = phase.getNumSubLat();

        double[] numSites = phase.getNumSites();

        ArrayList<ArrayList<String>> constituentList =
                phase.getConstituentList();

        if (numSites == null || numSites.length != ns)
            throw new IllegalArgumentException(
                    "Invalid site-ratio data for phase " + phaseName);

        if (constituentList == null ||
            constituentList.size() != ns) {

            throw new IllegalArgumentException(
                    "Invalid constituent-list data for phase "
                    + phaseName);
        }


        double[] a = new double[ns];
        int[] nc = new int[ns];

        for (int s = 0; s < ns; s++) {

            if (!Double.isFinite(numSites[s]) ||
                numSites[s] <= 0.0) {

                throw new IllegalArgumentException(
                        "Invalid site ratio for phase "
                        + phaseName +
                        ", sublattice " + s +
                        ": " + numSites[s]);
            }

            if (constituentList.get(s) == null ||
                constituentList.get(s).isEmpty()) {

                throw new IllegalArgumentException(
                        "Empty constituent list for phase "
                        + phaseName +
                        ", sublattice " + s);
            }

            a[s] = numSites[s];
            nc[s] = constituentList.get(s).size();
        }


        /*
         * ---------------------------------------------------------------
         * 4. Construct mixed-radix indexing
         * ---------------------------------------------------------------
         */

        int[] stride = new int[ns];

        stride[0] = 1;

        long totalEMLong = nc[0];

        for (int s = 1; s < ns; s++) {

            long nextStride =
                    (long) stride[s - 1] * nc[s - 1];

            if (nextStride > Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                        "CEF indexing exceeds integer range for phase "
                        + phaseName);

            stride[s] = (int) nextStride;

            totalEMLong *= nc[s];

            if (totalEMLong > Integer.MAX_VALUE)
                throw new IllegalArgumentException(
                        "Too many CEF end members for phase "
                        + phaseName);
        }

        int totalEM = (int) totalEMLong;


        /*
         * ---------------------------------------------------------------
         * 5. Constituent name -> index maps
         * ---------------------------------------------------------------
         */

        List<Map<String, Integer>> constituentIdx =
                new ArrayList<>();

        for (int s = 0; s < ns; s++) {

            Map<String, Integer> map =
                    new java.util.LinkedHashMap<>();

            ArrayList<String> names =
                    constituentList.get(s);

            for (int i = 0; i < names.size(); i++) {

                String name = names.get(i);

                if (name == null || name.isBlank())
                    throw new IllegalArgumentException(
                            "Blank constituent name in phase "
                            + phaseName +
                            ", sublattice " + s);

                String key = name.trim().toUpperCase();

                if (map.put(key, i) != null)
                    throw new IllegalArgumentException(
                            "Duplicate constituent " + key +
                            " on sublattice " + s +
                            " of phase " + phaseName);
            }

            constituentIdx.add(map);
        }


        /*
         * ---------------------------------------------------------------
         * 6. Obtain phase parameters
         * ---------------------------------------------------------------
         */

        ArrayList<String> elementArray =
                new ArrayList<>(elements);

        ArrayList<Parameter> params =
                database.getPhaseParam(
                        elementArray,
                        phaseName);


        /*
         * ---------------------------------------------------------------
         * 7. Allocate end-member array
         * ---------------------------------------------------------------
         *
         * Do NOT initialize missing end members to zero.
         *
         * A missing zeroth-order CEF parameter represents an unassigned
         * end member and must not silently become G = 0.
         */
        CefEndMember[] endMembers =
                new CefEndMember[totalEM];


        /*
         * TC (Curie/Neel temperature) and BMAGN (mean magnetic moment) are
         * CEF constituent-array quantities, exactly like G: an end-member
         * value on every zeroth-order array plus higher-order interaction
         * parameters. They are collected here into their own end-member
         * arrays + interaction lists and summed the same way as the
         * reference / excess Gibbs energy (see magneticEnergy).
         *
         * A missing TC/BMAGN end member simply means "0 on that array" --
         * unlike G, it is not an error (many end members are non-magnetic).
         */
        CefEndMember[] tcEndMembers   = new CefEndMember[totalEM];
        CefEndMember[] bmagEndMembers = new CefEndMember[totalEM];
        List<CefInteractionParam> tcInteractions   = new ArrayList<>();
        List<CefInteractionParam> bmagInteractions = new ArrayList<>();

        /*
         * V0 (molar volume at the reference pressure) and VA (thermal
         * expansion polynomial exponent) are CEF constituent-array
         * quantities too, feeding the volume/pressure contribution (see
         * volumeEnergy): G_vol = V0(y)*exp(VA(T,y)) * (P - P0). VK
         * (isothermal compressibility) is detected and rejected below --
         * pycalphad's Model.volume_energy also leaves it unimplemented.
         */
        CefEndMember[] v0EndMembers = new CefEndMember[totalEM];
        CefEndMember[] vaEndMembers = new CefEndMember[totalEM];
        List<CefInteractionParam> v0Interactions = new ArrayList<>();
        List<CefInteractionParam> vaInteractions = new ArrayList<>();
        boolean hasVK = false;


        /*
         * ---------------------------------------------------------------
         * 8. Construct CEF interaction parameters
         * ---------------------------------------------------------------
         */

        List<CefInteractionParam> interactions =
                new ArrayList<>();


        /*
         * ---------------------------------------------------------------
         * 9. Process all G / TC / BMAGN parameters
         * ---------------------------------------------------------------
         */

        for (Parameter param : params) {

            if (param == null)
                continue;

            String type =
                    param.getType() == null
                    ? ""
                    : param.getType().trim().toUpperCase();

            if (type.equals("VK")) {
                hasVK = true;
                continue;
            }

            if (!type.equals("G") &&
                !type.equals("TC") &&
                !type.equals("BMAGN") &&
                !type.equals("V0") &&
                !type.equals("VA")) {

                continue;
            }


            ArrayList<ArrayList<String>> clist =
                    param.getConstituentList();

            if (clist == null || clist.size() != ns)
                throw new IllegalArgumentException(
                        "Parameter constituent array does not match "
                        + "number of sublattices for phase "
                        + phaseName);


            /*
             * Build the complete temperature polynomial.
             */
            SgtePolynomial poly =
                    SgtePolynomial.fromExpList(
                            param.getExpList());

            if (poly == null)
                throw new IllegalArgumentException(
                        "Unable to construct temperature polynomial "
                        + "for parameter in phase "
                        + phaseName);


            /*
             * Parameter order is the TDB RK order.
             *
             * It is NOT the CEF constituent-array order.
             */
            int rkOrder = param.getOrder();


            /*
             * Determine whether this is a zeroth-order end member.
             *
             * A zeroth-order constituent array contains exactly one
             * constituent on every sublattice.
             */
            boolean isEndMember = true;

            for (int s = 0; s < ns; s++) {

                if (clist.get(s) == null ||
                    clist.get(s).size() != 1) {

                    isEndMember = false;
                    break;
                }
            }


            /*
             * -----------------------------------------------------------
             * 9a. Zeroth-order G / TC / BMAGN
             * -----------------------------------------------------------
             */

            if (isEndMember) {

                int em = endMemberIndex(
                        clist,
                        constituentIdx,
                        stride,
                        nc,
                        ns);

                if (em < 0)
                    continue;


                int[] emIdx = endMemberIndices(em, stride, nc, ns);

                if (type.equals("G")) {
                    endMembers[em] = new CefEndMember(emIdx, poly);
                } else if (type.equals("TC")) {
                    tcEndMembers[em] = new CefEndMember(emIdx, poly);
                } else if (type.equals("BMAGN")) {
                    bmagEndMembers[em] = new CefEndMember(emIdx, poly);
                } else if (type.equals("V0")) {
                    v0EndMembers[em] = new CefEndMember(emIdx, poly);
                } else if (type.equals("VA")) {
                    vaEndMembers[em] = new CefEndMember(emIdx, poly);
                }

                continue;
            }


            /*
             * -----------------------------------------------------------
             * 9b. Higher-order G parameter
             * -----------------------------------------------------------
             *
             * The complete TDB constituent array is preserved.
             *
             * Examples:
             *
             *   L(FE,V:C,VA;0)
             *
             * becomes factors
             *
             *   (SL0,FE) (SL0,V) (SL1,C) (SL1,VA)
             *
             * and
             *
             *   L(C,CR,FE;0)
             *
             * becomes
             *
             *   (SL0,C) (SL0,CR) (SL0,FE)
             *
             * The TDB order is retained independently as rkOrder.
             *
             * G interactions feed the excess Gibbs energy; TC / BMAGN
             * interactions feed the magnetic Curie-temperature / moment
             * constituent-array sums; V0 / VA interactions feed the molar
             * volume / thermal-expansion constituent-array sums.
             */
            if (type.equals("G") || type.equals("TC") || type.equals("BMAGN")
                    || type.equals("V0") || type.equals("VA")) {

                int factorCount = 0;

                for (int s = 0; s < ns; s++)
                    factorCount += clist.get(s).size();


                if (factorCount == 0)
                    throw new IllegalArgumentException(
                            "Empty interaction constituent array "
                            + "in phase " + phaseName);


                int[] factorSL =
                        new int[factorCount];

                int[] factorIdx =
                        new int[factorCount];


                int k = 0;

                for (int s = 0; s < ns; s++) {

                    ArrayList<String> names =
                            clist.get(s);

                    for (String name : names) {

                        if (name == null || name.isBlank())
                            throw new IllegalArgumentException(
                                    "Blank constituent in interaction "
                                    + "of phase " + phaseName);

                        String key =
                                name.trim().toUpperCase();

                        Integer idx =
                                constituentIdx.get(s).get(key);

                        if (idx == null)
                            throw new IllegalArgumentException(
                                    "Constituent " + key +
                                    " in parameter is not present "
                                    + "on sublattice " + s +
                                    " of phase " + phaseName);

                        factorSL[k] = s;
                        factorIdx[k] = idx;
                        k++;
                    }
                }


                CefInteractionParam ip =
                        new CefInteractionParam(
                                factorSL,
                                factorIdx,
                                rkOrder,
                                poly);

                if (type.equals("G")) {
                    interactions.add(ip);
                } else if (type.equals("TC")) {
                    tcInteractions.add(ip);
                } else if (type.equals("BMAGN")) {
                    bmagInteractions.add(ip);
                } else if (type.equals("V0")) {
                    v0Interactions.add(ip);
                } else {
                    vaInteractions.add(ip);
                }
            }
        }

        if (hasVK) {
            throw new UnsupportedOperationException(
                    "Phase " + phaseName + " has a VK (isothermal "
                    + "compressibility) parameter; the pressure "
                    + "dependence of molar volume from VK is not "
                    + "implemented (pycalphad's Model.volume_energy "
                    + "leaves this unimplemented too).");
        }


        /*
         * ---------------------------------------------------------------
         * 10. Verify all zeroth-order G parameters
         * ---------------------------------------------------------------
         *
         * Missing end members are not silently assigned G = 0.
         */
        List<String> missingEndMembers =
                new ArrayList<>();

        for (int em = 0; em < totalEM; em++) {

            if (endMembers[em] == null) {

                int[] idx =
                        endMemberIndices(
                                em,
                                stride,
                                nc,
                                ns);

                missingEndMembers.add(
                        formatEndMember(
                                idx,
                                constituentList));
            }
        }

        if (!missingEndMembers.isEmpty()) {

            throw new IllegalArgumentException(
                    "Missing CEF G end-member parameter(s) for phase "
                    + phaseName + ": "
                    + missingEndMembers);
        }

        /*
         * ---------------------------------------------------------------
         * 11. Magnetic model
         * ---------------------------------------------------------------
         *
         * The Inden-Hillert-Jarl model applies only if this phase has a
         * TYPE_DEFINITION ... MAGNETIC hint (AFM factor + structure factor
         * p), parsed by TdbParser into affMap / pMap. Without the hint the
         * TC / BMAGN parameters (if any) are simply ignored, matching
         * pycalphad (which returns S.Zero when the model hints are absent).
         */
        MagneticContribution magnetic = null;
        if (affMap != null && pMap != null
                && affMap.containsKey(phaseName) && pMap.containsKey(phaseName)) {
            double aff = affMap.get(phaseName);
            double p   = pMap.get(phaseName);
            if (Double.isFinite(aff) && Double.isFinite(p) && p > 0.0
                    && (aff == -1.0 || aff == -3.0)) {
                magnetic = new MagneticContribution(aff, p);
            } else {
                LOG.warning("Phase " + phaseName + " has a MAGNETIC hint with "
                        + "unsupported (aff=" + aff + ", p=" + p + "); "
                        + "magnetic contribution disabled for this phase.");
            }
        }

        /*
         * A volume/pressure contribution exists only if this phase has at
         * least one V0 parameter -- matching pycalphad, which checks for
         * the parameter's presence rather than a phase-level hint (unlike
         * MAGNETIC, there is no TYPE_DEFINITION gate for volume).
         */
        boolean hasV0 = false;
        for (CefEndMember em : v0EndMembers) {
            if (em != null) { hasV0 = true; break; }
        }
        if (!hasV0 && !v0Interactions.isEmpty()) hasV0 = true;

        /*
         * ---------------------------------------------------------------
         * 12. Assemble the extracted parts
         * ---------------------------------------------------------------
         */
        Parts parts = new Parts();
        parts.siteRatios = a;
        parts.constituents = nc;
        parts.endMembers = endMembers;
        parts.interactions = interactions;
        parts.magnetic = magnetic;
        parts.tcEndMembers   = magnetic == null ? null : tcEndMembers;
        parts.tcInteractions = magnetic == null ? null : tcInteractions;
        parts.bmagEndMembers   = magnetic == null ? null : bmagEndMembers;
        parts.bmagInteractions = magnetic == null ? null : bmagInteractions;
        parts.v0EndMembers   = hasV0 ? v0EndMembers : null;
        parts.v0Interactions = hasV0 ? v0Interactions : null;
        parts.vaEndMembers   = hasV0 ? vaEndMembers : null;
        parts.vaInteractions = hasV0 ? vaInteractions : null;
        parts.phaseName = phaseName;
        parts.elements = new ArrayList<>(elements);
        parts.constituentNames = deepCopyConstituentList(constituentList);
        return parts;
    }

    /*
     * =====================================================================
     * Helper methods
     * =====================================================================
     */


    /**
     * Converts a mixed-radix end-member number to constituent indices.
     */
    private static int[] endMemberIndices(
            int em,
            int[] stride,
            int[] nc,
            int ns) {

        int[] idx = new int[ns];

        for (int s = 0; s < ns; s++)
            idx[s] =
                    (em / stride[s]) % nc[s];

        return idx;
    }


    /**
     * Converts a complete TDB zeroth-order constituent array to its
     * mixed-radix end-member index.
     */
    private static int endMemberIndex(
            ArrayList<ArrayList<String>> clist,
            List<Map<String, Integer>> constituentIdx,
            int[] stride,
            int[] nc,
            int ns) {

        int em = 0;

        for (int s = 0; s < ns; s++) {

            if (clist.get(s).size() != 1)
                return -1;

            String name =
                    clist.get(s).get(0);

            if (name == null)
                return -1;

            Integer idx =
                    constituentIdx.get(s)
                            .get(name.trim().toUpperCase());

            if (idx == null)
                return -1;

            if (idx < 0 || idx >= nc[s])
                return -1;

            em += idx * stride[s];
        }

        return em;
    }


    /**
     * Makes an independent copy of the phase constituent structure.
     */
    private static ArrayList<ArrayList<String>>
    deepCopyConstituentList(
            ArrayList<ArrayList<String>> source) {

        ArrayList<ArrayList<String>> copy =
                new ArrayList<>();

        for (ArrayList<String> sl : source)
            copy.add(new ArrayList<>(sl));

        return copy;
    }


    /**
     * Formats an end-member constituent array for diagnostics.
     */
    private static String formatEndMember(
            int[] idx,
            ArrayList<ArrayList<String>> constituentList) {

        StringBuilder sb = new StringBuilder("(");

        for (int s = 0; s < idx.length; s++) {

            if (s > 0)
                sb.append(":");

            sb.append(
                    constituentList
                            .get(s)
                            .get(idx[s]));
        }

        sb.append(")");

        return sb.toString();
    }


    /**
     * Returns the first two effective coefficients of a polynomial.
     *
     * <p>This is used only for the presently unused magnetic arrays.
     * CEF G parameters retain their complete SgtePolynomial.</p>
     */
    private static double[] effectiveLinearCoefficients(
            SgtePolynomial poly) {

        double T1 = 298.15;
        double T2 = 299.15;

        double G1 = poly.G(T1);
        double G2 = poly.G(T2);

        double b = G2 - G1;
        double a = G1 - b * T1;

        return new double[] {a, b};
    }


    // ══════════════════════════════════════════════════════════════
    // Nested value type: one CEF excess (mixing) parameter
    // ══════════════════════════════════════════════════════════════

    /**
     * A general CEF interaction parameter.
     *
     * <p>The constituent array is represented explicitly as a set of constituent
     * factors. Each factor identifies one constituent (sublattice, constituent)
     * whose site fraction multiplies the interaction parameter.</p>
     *
     * <p>This deliberately separates the CEF constituent-array structure from
     * the Redlich-Kister order stored in a TDB parameter. The latter is represented
     * by {@code rkOrder}.</p>
     *
     * <p>For example, for
     *
     * <pre>
     * L(FE,V:C,VA;0)
     * </pre>
     *
     * the constituent factors are
     *
     * <pre>
     * (SL0,FE) (SL0,V) (SL1,C)
     * </pre>
     *
     * and the contribution is
     *
     * <pre>
     * y(FE) y(V) y(C) L(T).
     * </pre>
     *
     * <p>For a ternary parameter such as
     *
     * <pre>
     * L(C,CR,FE;0)
     * </pre>
     *
     * the factors are simply
     *
     * <pre>
     * (SL0,C) (SL0,CR) (SL0,FE).
     * </pre>
     *
     * <p>The RK order is kept separately so that a future implementation can
     * evaluate the corresponding Redlich-Kister composition factor without
     * confusing it with the CEF constituent-array order.</p>
     */
    public static final class CefInteractionParam {

        /**
         * Sublattice index for each constituent factor.
         */
        private final int[] sublattice;

        /**
         * Constituent index for each constituent factor.
         */
        private final int[] constituent;

        /**
         * Redlich-Kister order from the TDB parameter, i.e. the integer after ';'.
         *
         * <p>This is NOT the CEF constituent-array order.</p>
         */
        private final int rkOrder;

        /** Temperature-dependent interaction polynomial L(T). */
        private final SgtePolynomial polynomial;


        /**
         * Constructs a general CEF interaction parameter.
         *
         * @param sublattice    sublattice index of each constituent factor
         * @param constituent   constituent index corresponding to each factor
         * @param rkOrder       Redlich-Kister order from the TDB parameter
         * @param polynomial    temperature-dependent interaction polynomial
         */
        public CefInteractionParam(int[] sublattice,
                                   int[] constituent,
                                   int rkOrder,
                                   SgtePolynomial polynomial) {

            if (sublattice == null || constituent == null)
                throw new IllegalArgumentException(
                        "Sublattice and constituent arrays must not be null.");

            if (sublattice.length != constituent.length)
                throw new IllegalArgumentException(
                        "Sublattice and constituent arrays must have equal length.");

            if (sublattice.length == 0)
                throw new IllegalArgumentException(
                        "CEF interaction must contain at least one constituent factor.");

            if (rkOrder < 0)
                throw new IllegalArgumentException(
                        "RK order must be non-negative: " + rkOrder);

            if (polynomial == null)
                throw new IllegalArgumentException(
                        "Polynomial must not be null.");

            for (int i = 0; i < sublattice.length; i++) {
                if (sublattice[i] < 0)
                    throw new IllegalArgumentException(
                            "Invalid sublattice index: " + sublattice[i]);

                if (constituent[i] < 0)
                    throw new IllegalArgumentException(
                            "Invalid constituent index: " + constituent[i]);
            }

            this.sublattice  = sublattice.clone();
            this.constituent = constituent.clone();
            this.rkOrder     = rkOrder;
            this.polynomial  = polynomial;
        }


        /**
         * Constructs a T-independent interaction.
         */
        public static CefInteractionParam constant(int[] sublattice,
                                                   int[] constituent,
                                                   int rkOrder,
                                                   double a) {
            double[] tLow = {298.15};
            double[] tHigh = {6000.0};
            double[][] coeffs = {{a, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}};
            SgtePolynomial poly = new SgtePolynomial(tLow, tHigh, coeffs);
            return new CefInteractionParam(
                    sublattice, constituent, rkOrder, poly);
        }


        /**
         * Temperature-dependent interaction via polynomial evaluation.
         */
        public double L(double T) {
            return polynomial.G(T);
        }


        /**
         * Temperature derivative of L.
         */
        public double dLdT(double T) {
            return polynomial.dGdT(T);
        }


        /**
         * Number of constituent factors in the CEF constituent array.
         */
        public int size() {
            return sublattice.length;
        }


        /**
         * Returns the sublattice index of factor {@code k}.
         */
        public int sublattice(int k) {
            return sublattice[k];
        }


        /**
         * Returns the constituent index of factor {@code k}.
         */
        public int constituent(int k) {
            return constituent[k];
        }


        /**
         * Returns a copy of the sublattice-factor array.
         */
        public int[] sublattices() {
            return sublattice.clone();
        }


        /**
         * Returns a copy of the constituent-factor array.
         */
        public int[] constituents() {
            return constituent.clone();
        }


        /**
         * Redlich-Kister order from the TDB parameter.
         */
        public int rkOrder() {
            return rkOrder;
        }


        /**
         * Returns the polynomial.
         */
        public SgtePolynomial polynomial() {
            return polynomial;
        }


        /**
         * Returns the CEF constituent-array order.
         *
         * <p>For the standard CEF representation this is one less than the
         * number of explicit constituent factors beyond the implicit
         * one-constituent-per-sublattice reference structure. This accessor is
         * mainly diagnostic; it must not be confused with {@link #rkOrder()}.</p>
         */
        public int cefOrder(int numberOfSublattices) {
            return sublattice.length - numberOfSublattices;
        }


        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("L(");

            for (int i = 0; i < sublattice.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("s")
                  .append(sublattice[i])
                  .append(":")
                  .append(constituent[i]);
            }

            sb.append("; rkOrder=")
              .append(rkOrder)
              .append(") = ")
              .append(polynomial);

            return sb.toString();
        }
    }


    // ══════════════════════════════════════════════════════════════
    // Nested value type: one CEF end-member (reference-surface corner)
    // ══════════════════════════════════════════════════════════════

    /**
     * A single end-member in the CEF surface of reference.
     * Stores either a full multi-range SGTE polynomial or a simple a+b*T form.
     */
    public static final class CefEndMember {

        /** Constituent index per sublattice, length = ns. */
        public final int[] constituentIdx;

        /** Full SGTE polynomial (preferred). Null if using simple form. */
        private final SgtePolynomial poly;

        /** Fallback constant term when poly is null. */
        private final double a;

        /** Fallback linear coefficient when poly is null. */
        private final double b;

        /**
         * Construct with full SGTE polynomial (preferred constructor).
         */
        public CefEndMember(int[] constituentIdx, SgtePolynomial poly) {
            this.constituentIdx = constituentIdx.clone();
            this.poly = poly;
            this.a    = 0.0;
            this.b    = 0.0;
        }

        /**
         * Construct with simple a+b*T form (fallback for interactions/missing data).
         */
        public CefEndMember(int[] constituentIdx, double a, double b) {
            this.constituentIdx = constituentIdx.clone();
            this.poly = null;
            this.a    = a;
            this.b    = b;
        }

        /** G(T) — uses full polynomial if available, else a+b*T. */
        public double G(double T) {
            return poly != null ? poly.G(T) : a + b * T;
        }

        /** dG/dT — uses full polynomial if available, else b. */
        public double dGdT(double T) {
            return poly != null ? poly.dGdT(T) : b;
        }

        public double a() { return poly != null ? 0.0 : a; }
        public double b() { return poly != null ? 0.0 : b; }

        /** True if this end-member has a full SGTE polynomial. */
        public boolean hasPoly() { return poly != null; }
    }


    // ══════════════════════════════════════════════════════════════
    // Nested value type: Inden-Hillert-Jarl magnetic contribution
    // ══════════════════════════════════════════════════════════════

    /**
     * The two Inden-Hillert-Jarl model hints for a magnetic phase:
     * the antiferromagnetic ("Weiss") factor and the structure factor
     * {@code p}, parsed from a {@code TYPE_DEFINITION ... MAGNETIC aff p}
     * record (via {@code TdbParser} -&gt; {@code affMap} / {@code pMap}).
     *
     * <p>The IHJ energy itself ({@code G_magn = R T ln(beta+1) f(tau)},
     * with {@code Tc(y)} / {@code beta(y)} built from the phase's
     * {@code TC} / {@code BMAGN} constituent-array parameters) lives in
     * {@link CefGibbs#magneticEnergyAD} / {@link CefGibbs#indenF}, so this
     * type is now just the {@code (aff, p)} pair.</p>
     */
    public static final class MagneticContribution {

        /** Antiferromagnetic ("Weiss") factor: -1.0 (BCC) or -3.0 (FCC/HCP). */
        private final double aff;

        /** Inden structure factor p: 0.4 (BCC), 0.28 (FCC/HCP). */
        private final double p;

        /**
         * @param aff antiferromagnetic factor; must be -1.0 or -3.0
         * @param p   structure factor; must be &gt; 0
         */
        public MagneticContribution(double aff, double p) {
            if (aff != -1.0 && aff != -3.0)
                throw new IllegalArgumentException(
                    "aff must be -1.0 (BCC) or -3.0 (FCC/HCP), got " + aff);
            if (!(p > 0.0))
                throw new IllegalArgumentException("p must be > 0, got " + p);
            this.aff = aff;
            this.p   = p;
        }

        /** Antiferromagnetic factor. */
        public double aff() { return aff; }

        /** Inden structure factor p. */
        public double p() { return p; }
    }
}
