package system.model.unary;

/**
 * Contract that every element's SGTE Gibbs energy implementation must satisfy.
 *
 * <h2>Design: open phase API</h2>
 * Earlier versions had explicit methods ({@code gbcc}, {@code gfcc}, etc.), which
 * locked the interface to exactly four phases.  The SGTE v4.4 database contains 30+
 * phase names; elements like Zr have OMEGA, CBCC_A12, ORTHORHOMBIC_A20, and more.
 *
 * <p>This version exposes a single {@link #gibbs(String, double)} method that accepts
 * any phase name string matching what is stored in the .tdb file.  The four common
 * phase names are available as constants in {@link PhaseType}:
 * <pre>
 *   double g = element.gibbs(PhaseType.BCC_A2, 1273.0);
 *   double g = element.gibbs("OMEGA",           900.0);   // any tdb phase name
 * </pre>
 *
 * <h2>Relationship to Mathematica calG0</h2>
 * <pre>
 *   calG0["Nb", "BCC_A2", T, TN]  →  element.gibbs(PhaseType.BCC_A2, T)
 *   calG0["Zr", "BCC_A2", T, TN]  →  element.gibbs(PhaseType.BCC_A2, T)
 * </pre>
 * The {@code TN} argument (used only for range selection in Mathematica) is not
 * needed here — {@code T} is always numeric and range selection is handled internally.
 *
 * <h2>GHSER reference</h2>
 * {@link #ghser(double)} returns the element's SGTE reference state (the stable phase
 * at 298.15 K and 1 bar).  For BCC-stable metals (Nb, Mo, V, W, Ta) this equals
 * G(BCC_A2).  For HCP-stable metals (Ti, Zr, Re) this equals G(HCP_A3).
 */
public interface ElementGibbs {

    /**
     * Element symbol as stored in the tdb file, e.g. {@code "NB"}, {@code "TI"},
     * {@code "ZR"}.  Used as the map key in {@link UnaryGibbs}.
     */
    String elementSymbol();

    /**
     * Molar Gibbs energy G°(phase, T) in J/mol.
     *
     * @param phaseName  phase name string matching the tdb PHASE declaration,
     *                   e.g. {@code "BCC_A2"}, {@code "LIQUID"}, {@code "OMEGA"}.
     *                   Use constants from {@link PhaseType} for the common cases.
     * @param T          temperature in Kelvin (must be &gt; 0)
     * @return           G°(phase, T) in J/mol
     * @throws UnsupportedOperationException if this element has no data for {@code phaseName}
     */
    double gibbs(String phaseName, double T);

    /**
     * Molar Gibbs energy of the SGTE reference state (GHSER) in J/mol.
     *
     * <p>For BCC-stable elements this equals {@code gibbs(PhaseType.BCC_A2, T)}.
     * For HCP-stable elements this equals {@code gibbs(PhaseType.HCP_A3, T)}.
     *
     * @param T  temperature in Kelvin
     * @return   G°(reference, T) in J/mol
     */
    double ghser(double T);

    /**
     * Returns a set of all phase names for which this element has data.
     *
     * @return unmodifiable set of phase name strings (upper-case)
     */
    java.util.Set<String> availablePhases();
}
