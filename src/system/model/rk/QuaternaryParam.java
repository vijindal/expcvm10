package system.model.rk;

/**
 * Redlich-Kister quaternary interaction parameter L(i,j,k,l) — a scalar constant.
 *
 * <h2>Form</h2>
 * <pre>
 *   G_Em_quat contribution = xᵢ·xⱼ·xₖ·xₗ·L_ijkl
 * </pre>
 * where L_ijkl is a T-linear scalar: {@code L = a + b·T}.
 *
 * <h2>Component order</h2>
 * Indices satisfy {@code idxI < idxJ < idxK < idxL} (0-based).
 */
public final class QuaternaryParam {

    /** 0-based component indices (idxI &lt; idxJ &lt; idxK &lt; idxL). */
    public final int idxI, idxJ, idxK, idxL;

    /** Temperature-independent part: L = a + b·T. */
    public final double a;

    /** Temperature-linear part: L = a + b·T. */
    public final double b;

    /**
     * Constructs a quaternary interaction parameter.
     *
     * @param idxI  smallest 0-based component index
     * @param idxJ  second component index
     * @param idxK  third component index
     * @param idxL  largest component index
     * @param a     T-independent part of L
     * @param b     T-linear part of L
     */
    public QuaternaryParam(int idxI, int idxJ, int idxK, int idxL, double a, double b) {
        if (!(idxI < idxJ && idxJ < idxK && idxK < idxL))
            throw new IllegalArgumentException(
                    "QuaternaryParam requires idxI<idxJ<idxK<idxL");
        this.idxI = idxI; this.idxJ = idxJ;
        this.idxK = idxK; this.idxL = idxL;
        this.a = a; this.b = b;
    }

    /** Returns L at temperature T. */
    public double L(double T) { return a + b * T; }

    /** Returns dL/dT. */
    public double dLdT() { return b; }

    /** Convenience: constant L (b = 0). */
    public static QuaternaryParam constant(int i, int j, int k, int l, double a) {
        return new QuaternaryParam(i, j, k, l, a, 0.0);
    }
}
