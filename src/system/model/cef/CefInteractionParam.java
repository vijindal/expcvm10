package system.model.cef;

/**
 * CEF interaction parameter L for one interacting pair/species combination.
 *
 * <h2>Two types in C15</h2>
 * The C15 Laves phase has interactions of two kinds, derived from the
 * systematic enumeration in {@code calGC15}:
 *
 * <ul>
 *   <li><b>Type-1 (pair on sublattice 1, single on sublattice 2)</b>:
 *       L[pI,pJ × k] — species pI and pJ mix on sublattice 1; species k on sublattice 2.
 *       Notation: {@code L{pI}{pJ}x{k}x} e.g. {@code L12x1x}
 *       G_Em contribution: y₁[pI]·y₁[pJ]·y₂[k]·L</li>
 *   <li><b>Type-2 (single on sublattice 1, pair on sublattice 2)</b>:
 *       L[i × pJ,pK] — species i on sublattice 1; species pJ and pK mix on sublattice 2.
 *       Notation: {@code L{i}x{pJ}{pK}x} e.g. {@code L1x12x}
 *       G_Em contribution: y₁[i]·y₂[pJ]·y₂[pK]·L</li>
 * </ul>
 *
 * <h2>L value</h2>
 * Each L is T-linear: {@code L = a + b·T}.
 *
 * <h2>Internal parameter indexing</h2>
 * The internal parameter vector is flattened as:
 * <pre>
 *   ipl = {y[1][0], y[1][1], ..., y[1][nc-1],   ← sublattice 1, 0-based
 *          y[2][0], y[2][1], ..., y[2][nc-1]}    ← sublattice 2, 0-based
 * </pre>
 * So {@code y[s][i] = ipl[s*nc + i]} (s=0 or 1 in Java, 1 or 2 in Mathematica).
 */
public final class CefInteractionParam {

    /**
     * Sublattice index of the interacting PAIR (0-based: 0 = sublattice 1, 1 = sublattice 2).
     * The single species occupies the OTHER sublattice.
     */
    public final int pairSublattice;

    /** First species of the pair (0-based component index). */
    public final int pairA;

    /** Second species of the pair (0-based component index, pairA &lt; pairB). */
    public final int pairB;

    /** Species on the single-species sublattice (0-based component index). */
    public final int singleIdx;

    /** Temperature-independent coefficient (J/mol). */
    public final double a;

    /** Temperature-linear coefficient (J/(mol·K)). */
    public final double b;

    /**
     * Constructs a CEF interaction parameter.
     *
     * @param pairSublattice  0-based sublattice index where the pair mixes (0 or 1)
     * @param pairA           0-based index of first species in the pair (smaller)
     * @param pairB           0-based index of second species in the pair (larger)
     * @param singleIdx       0-based index of the species on the single sublattice
     * @param a               T-independent part of L (J/mol)
     * @param b               T-linear part of L (J/(mol·K))
     */
    public CefInteractionParam(int pairSublattice, int pairA, int pairB,
                               int singleIdx, double a, double b) {
        if (pairA >= pairB) throw new IllegalArgumentException(
                "pairA must be < pairB, got " + pairA + " >= " + pairB);
        this.pairSublattice = pairSublattice;
        this.pairA     = pairA;
        this.pairB     = pairB;
        this.singleIdx = singleIdx;
        this.a = a;
        this.b = b;
    }

    /** Evaluates L(T) = a + b·T. */
    public double L(double T) { return a + b * T; }

    /** Returns dL/dT = b. */
    public double dLdT() { return b; }

    /**
     * Convenience factory: T-independent L (b = 0).
     */
    public static CefInteractionParam constant(int pairSublattice,
                                               int pairA, int pairB,
                                               int singleIdx, double a) {
        return new CefInteractionParam(pairSublattice, pairA, pairB, singleIdx, a, 0.0);
    }

    /**
     * Convenience factory: zero interaction (L = 0).
     */
    public static CefInteractionParam zero(int pairSublattice,
                                           int pairA, int pairB, int singleIdx) {
        return new CefInteractionParam(pairSublattice, pairA, pairB, singleIdx, 0.0, 0.0);
    }

    @Override
    public String toString() {
        String sub = pairSublattice == 0 ? "SL1" : "SL2";
        return String.format("L[%s pair(%d,%d) × single(%d)] = %.4f + %.6f·T",
                sub, pairA, pairB, singleIdx, a, b);
    }
}
