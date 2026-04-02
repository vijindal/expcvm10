package system.model.cef;

/**
 * A single end-member in the CEF surface of reference.
 * Stores the polynomial G(T) = a + b*T evaluated at runtime.
 *
 * The constituent index array identifies which constituent occupies
 * each sublattice for this end member, in the same mixed-radix order
 * used by CefGibbs (sublattice 0 = least significant).
 */
public final class CefEndMember {

    /** Constituent index per sublattice, length = ns. */
    public final int[] constituentIdx;

    /** Constant term in G(T) = a + b*T  (J/mol) */
    private final double a;

    /** Linear T term in G(T) = a + b*T  (J/(mol·K)) */
    private final double b;

    /**
     * @param constituentIdx  constituent index per sublattice, length ns
     * @param a               constant term
     * @param b               linear temperature coefficient
     */
    public CefEndMember(int[] constituentIdx, double a, double b) {
        this.constituentIdx = constituentIdx.clone();
        this.a = a;
        this.b = b;
    }

    /** G(T) = a + b*T */
    public double G(double T) { return a + b * T; }

    /** dG/dT = b */
    public double dGdT(double T) { return b; }

    /** Constant term. */
    public double a() { return a; }

    /** Linear coefficient. */
    public double b() { return b; }
}
