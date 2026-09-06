package system.model.cef;

import java.util.Arrays;

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
public final class CefInteractionParam {

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