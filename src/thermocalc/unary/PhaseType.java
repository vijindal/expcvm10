package thermocalc.unary;

/**
 * Canonical phase name constants used throughout the thermocalc layer.
 *
 * <p>Phase names are plain strings — identical to the names declared in SGTE .tdb
 * files after the {@code PHASE} keyword.  This class provides well-known constants
 * for the phases that appear in the Mathematica {@code calG0[]} dispatcher and in
 * {@code delxG} model routing, while leaving the system fully open to any phase name
 * that appears in a loaded .tdb file (e.g. OMEGA, HCP_ZN, LAVES_C14, …).
 *
 * <h2>Usage</h2>
 * <pre>
 *   // Preferred: use a constant
 *   double g = unaryGibbs.gibbs("NB", PhaseType.BCC_A2, 1273.0);
 *
 *   // Also valid: pass any string from the tdb file directly
 *   double g = unaryGibbs.gibbs("ZR", "OMEGA", 1000.0);
 * </pre>
 *
 * <h2>Why not an enum?</h2>
 * An enum would lock the set of phases at compile time.  The SGTE v4.4 database
 * contains 30+ phase names; future databases may add more.  Using plain String
 * constants keeps the API open while still providing IDE auto-complete and
 * compile-time checking for the most common phases.
 */
public final class PhaseType {

    // ------------------------------------------------------------------
    // Well-known phase name constants
    // ------------------------------------------------------------------

    /** Body-centred cubic (A2 Strukturbericht). Stable phase for Nb, Mo, V, W, Ta, Cr, … */
    public static final String BCC_A2  = "BCC_A2";

    /** Face-centred cubic (A1 Strukturbericht). Stable phase for Al, Cu, Ni, Ag, Au, … */
    public static final String FCC_A1  = "FCC_A1";

    /** Hexagonal close-packed (A3 Strukturbericht). Stable phase for Ti, Zr, Re, Co, … */
    public static final String HCP_A3  = "HCP_A3";

    /** Liquid phase. */
    public static final String LIQUID  = "LIQUID";

    // Additional phases present in SGTE v4.4 and referenced in the clusGen source
    /** Omega phase — metastable in Zr, Ti. */
    public static final String OMEGA   = "OMEGA";

    /** HCP_ZN — alternative HCP setting used in some databases for Zn, Mg, … */
    public static final String HCP_ZN  = "HCP_ZN";

    /** C14 Laves phase. */
    public static final String LAVES_C14 = "LAVES_C14";

    /** C15 Laves phase (e.g. V₂Zr). */
    public static final String LAVES_C15 = "LAVES_C15";

    // ------------------------------------------------------------------
    // Utility: normalise a phase name for map look-up
    // ------------------------------------------------------------------

    /**
     * Normalises a phase name string to upper-case for consistent map key usage.
     *
     * <pre>
     *   PhaseType.normalise("bcc_a2")  →  "BCC_A2"
     *   PhaseType.normalise("Liquid")  →  "LIQUID"
     * </pre>
     *
     * @param phaseName  raw phase name, any case
     * @return           upper-case form
     */
    public static String normalise(String phaseName) {
        if (phaseName == null) throw new IllegalArgumentException("phaseName must not be null");
        return phaseName.toUpperCase();
    }

    private PhaseType() {}   // no instances — constants only
}
