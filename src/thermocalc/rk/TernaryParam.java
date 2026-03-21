package thermocalc.rk;

/**
 * Redlich-Kister ternary interaction parameter L(i,j,k) for a triplet of components.
 *
 * <h2>Form in the Mathematica source</h2>
 * <pre>
 *   L(i,j,k) = vᵢ·xᵢ + vⱼ·xⱼ + vₖ·xₖ
 * </pre>
 * where each vₘ is T-linear:
 * <pre>
 *   vₘ = aₘ + bₘ·T
 * </pre>
 *
 * <h2>Example from clusGen_25 (Ti-V-Zr system)</h2>
 * <pre>
 *   LABC = xA*(−253783.69 + 10.9738*T) + xB*(7589.27) + xC*(80496.47)
 * </pre>
 * maps to {@code idxI=0(Ti), idxJ=1(V), idxK=2(Zr)},
 * {@code a={-253783.69, 7589.27, 80496.47}}, {@code b={10.9738, 0.0, 0.0}}.
 *
 * <h2>Component order</h2>
 * Indices satisfy {@code idxI < idxJ < idxK} (0-based). The v-coefficients are
 * ordered to match: {@code v[0] ↔ idxI}, {@code v[1] ↔ idxJ}, {@code v[2] ↔ idxK}.
 */
public final class TernaryParam {

    /** 0-based component indices (idxI &lt; idxJ &lt; idxK). */
    public final int idxI, idxJ, idxK;

    /**
     * Temperature-independent parts of per-component v coefficients.
     * {@code a[0] ↔ idxI, a[1] ↔ idxJ, a[2] ↔ idxK}.
     */
    public final double[] a;

    /**
     * Temperature-linear parts of per-component v coefficients.
     * {@code b[0] ↔ idxI, b[1] ↔ idxJ, b[2] ↔ idxK}.
     */
    public final double[] b;

    /**
     * Constructs a ternary parameter.
     *
     * @param idxI  0-based first component index (smallest)
     * @param idxJ  0-based second component index
     * @param idxK  0-based third component index (largest)
     * @param a     {aᵢ, aⱼ, aₖ} — T-independent v coefficients
     * @param b     {bᵢ, bⱼ, bₖ} — T-linear v coefficients
     */
    public TernaryParam(int idxI, int idxJ, int idxK, double[] a, double[] b) {
        if (!(idxI < idxJ && idxJ < idxK))
            throw new IllegalArgumentException(
                    "TernaryParam requires idxI < idxJ < idxK, got "
                    + idxI + "," + idxJ + "," + idxK);
        if (a.length != 3 || b.length != 3)
            throw new IllegalArgumentException("a and b must each have length 3");
        this.idxI = idxI;
        this.idxJ = idxJ;
        this.idxK = idxK;
        this.a = a.clone();
        this.b = b.clone();
    }

    /**
     * Returns the v coefficient for component {@code idx} at temperature T.
     * {@code idx} must be one of {idxI, idxJ, idxK}.
     */
    public double v(int idx, double T) {
        if (idx == idxI) return a[0] + b[0] * T;
        if (idx == idxJ) return a[1] + b[1] * T;
        if (idx == idxK) return a[2] + b[2] * T;
        throw new IllegalArgumentException("Index " + idx + " not in triplet");
    }

    /**
     * Evaluates L(i,j,k) = vᵢ·xᵢ + vⱼ·xⱼ + vₖ·xₖ at compositions x and temperature T.
     */
    public double L(double[] x, double T) {
        return (a[0] + b[0]*T)*x[idxI]
             + (a[1] + b[1]*T)*x[idxJ]
             + (a[2] + b[2]*T)*x[idxK];
    }

    /**
     * Returns dL/dT (T-derivative of the interaction parameter).
     */
    public double dLdT(double[] x) {
        return b[0]*x[idxI] + b[1]*x[idxJ] + b[2]*x[idxK];
    }

    /**
     * Returns dL/d(xₘ) = vₘ(T) if m ∈ {idxI, idxJ, idxK}, else 0.
     */
    public double dLdx(double[] x, double T, int m) {
        if (m == idxI) return a[0] + b[0]*T;
        if (m == idxJ) return a[1] + b[1]*T;
        if (m == idxK) return a[2] + b[2]*T;
        return 0.0;
    }

    /**
     * Returns d(dL/dT)/d(xₘ) = bₘ if m ∈ {idxI, idxJ, idxK}, else 0.
     * (The mixed T-x derivative of L needed for GxT.)
     */
    public double d2LdTdx(int m) {
        if (m == idxI) return b[0];
        if (m == idxJ) return b[1];
        if (m == idxK) return b[2];
        return 0.0;
    }

    @Override
    public String toString() {
        return String.format("L(%d,%d,%d) = (%.4f+%.4f·T)·x%d + (%.4f+%.4f·T)·x%d + (%.4f+%.4f·T)·x%d",
                idxI, idxJ, idxK,
                a[0], b[0], idxI,
                a[1], b[1], idxJ,
                a[2], b[2], idxK);
    }
}
