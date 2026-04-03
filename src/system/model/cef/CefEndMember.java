package system.model.cef;

/**
 * A single end-member in the CEF surface of reference.
 * Stores either a full multi-range SGTE polynomial or a simple a+b*T form.
 */
public final class CefEndMember {

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
