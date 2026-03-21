package thermocalc.rk;

/**
 * Redlich-Kister binary interaction parameter L(i,j) for a pair of components.
 *
 * <h2>RK polynomial form</h2>
 * <pre>
 *   L(i,j) = Σₛ Lₛ · (xᵢ − xⱼ)ˢ      s = 0, 1, 2, ...
 * </pre>
 * where each Lₛ is itself T-linear:
 * <pre>
 *   Lₛ = aₛ + bₛ·T
 * </pre>
 *
 * <h2>Storage</h2>
 * Coefficients are stored as parallel arrays {@code a[s]} and {@code b[s]}
 * (length = number of RK terms, typically 1–3).
 *
 * <h2>Mathematica correspondence</h2>
 * In {@code delGsol}: {@code LAB = (a0 + b0*T) + (a1 + b1*T)*(xA-xB) + (a2 + b2*T)*(xA-xB)^2}
 * maps to {@code a={a0,a1,a2}, b={b0,b1,b2}}.
 *
 * <h2>Component indices</h2>
 * {@code idxI} and {@code idxJ} are 0-based component indices with {@code idxI < idxJ}.
 */
public final class BinaryParam {

    /** 0-based index of first component (i < j). */
    public final int idxI;

    /** 0-based index of second component (i < j). */
    public final int idxJ;

    /**
     * Temperature-independent part of each RK coefficient.
     * {@code a[s]} contributes {@code a[s] * (xi - xj)^s}.
     */
    public final double[] a;

    /**
     * Temperature-linear part of each RK coefficient.
     * {@code b[s]} contributes {@code b[s] * T * (xi - xj)^s}.
     */
    public final double[] b;

    /**
     * Constructs a binary RK parameter with T-linear coefficients.
     *
     * @param idxI  0-based index of first component (must be &lt; idxJ)
     * @param idxJ  0-based index of second component
     * @param a     temperature-independent coefficients [L0_a, L1_a, L2_a, ...]
     * @param b     temperature-linear coefficients      [L0_b, L1_b, L2_b, ...]
     */
    public BinaryParam(int idxI, int idxJ, double[] a, double[] b) {
        if (idxI >= idxJ) throw new IllegalArgumentException(
                "BinaryParam requires idxI < idxJ, got " + idxI + " >= " + idxJ);
        if (a.length != b.length) throw new IllegalArgumentException(
                "a and b must have the same length");
        this.idxI = idxI;
        this.idxJ = idxJ;
        this.a = a.clone();
        this.b = b.clone();
    }

    /** Number of RK expansion terms (order + 1). */
    public int order() { return a.length; }

    /**
     * Evaluates the full L(i,j) value at temperature T and compositions x[].
     *
     * @param x  composition array (0-based)
     * @param T  temperature in Kelvin
     * @return   L_ij evaluated at (x, T)
     */
    public double L(double[] x, double T) {
        double d  = x[idxI] - x[idxJ];
        double dp = 1.0;   // (xi-xj)^s
        double L  = 0.0;
        for (int s = 0; s < a.length; s++) {
            L  += (a[s] + b[s] * T) * dp;
            dp *= d;
        }
        return L;
    }

    /**
     * Evaluates dL/dT (temperature derivative of the interaction parameter).
     *
     * @param x  composition array (0-based)
     * @return   dL_ij/dT at compositions x (independent of T)
     */
    public double dLdT(double[] x) {
        double d  = x[idxI] - x[idxJ];
        double dp = 1.0;
        double dL = 0.0;
        for (int s = 0; s < b.length; s++) {
            dL += b[s] * dp;
            dp *= d;
        }
        return dL;
    }

    /**
     * Evaluates dL/d(xₘ) — derivative of L w.r.t. component m.
     *
     * <p>Only xᵢ and xⱼ appear in L, so this is non-zero only for m = idxI or m = idxJ.
     *
     * @param x  composition array
     * @param T  temperature
     * @param m  component index (0-based)
     * @return   ∂L/∂xₘ
     */
    public double dLdx(double[] x, double T, int m) {
        if (m != idxI && m != idxJ) return 0.0;
        double sign = (m == idxI) ? +1.0 : -1.0;  // ∂(xi-xj)/∂xm
        double d  = x[idxI] - x[idxJ];
        double dp = 1.0;   // (xi-xj)^(s-1) * s, accumulated below
        double dL = 0.0;
        for (int s = 1; s < a.length; s++) {
            dL += (a[s] + b[s] * T) * s * dp;
            dp *= d;
        }
        return sign * dL;
    }

    /**
     * Convenience factory: pure temperature-independent binary parameter (b = 0).
     * Used for constant interaction energies.
     */
    public static BinaryParam constant(int idxI, int idxJ, double... a) {
        double[] zeros = new double[a.length];
        return new BinaryParam(idxI, idxJ, a, zeros);
    }

    /**
     * Convenience factory: single-term RK (L0 = a0 + b0*T, no composition dependence).
     */
    public static BinaryParam zerothOrder(int idxI, int idxJ, double a0, double b0) {
        return new BinaryParam(idxI, idxJ, new double[]{a0}, new double[]{b0});
    }

    /**
     * Returns ∂(dL/dT)/∂xₘ = ∂²L/∂T∂xₘ.
     *
     * <p>Since L = Σs (aₛ + bₛ·T)·dˢ, we have dL/dT = Σs bₛ·dˢ.
     * Differentiating w.r.t. xₘ: same structure as {@link #dLdx} but using only bₛ.
     */
    public double dLdx_dT(double[] x, int m) {
        if (m != idxI && m != idxJ) return 0.0;
        double sign = (m == idxI) ? +1.0 : -1.0;
        double d  = x[idxI] - x[idxJ];
        double dp = 1.0;
        double dL = 0.0;
        for (int s = 1; s < b.length; s++) {
            dL += b[s] * s * dp;
            dp *= d;
        }
        return sign * dL;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("L(").append(idxI).append(',').append(idxJ).append(") =");
        for (int s = 0; s < a.length; s++) {
            if (s > 0) sb.append(" +");
            sb.append(" (").append(a[s]).append(" + ").append(b[s]).append("·T)");
            if (s > 0) sb.append("·(xi-xj)^").append(s);
        }
        return sb.toString();
    }
}
