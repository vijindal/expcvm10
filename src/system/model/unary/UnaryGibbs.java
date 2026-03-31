package system.model.unary;

import system.database.tdb;
import system.database.UnaryGibbsBuilder;
import system.model.unary.PhaseType;

import java.io.IOException;
import java.util.*;

/**
 * Central dispatcher for pure-element SGTE Gibbs energies.
 *
 * <p>This class is the direct Java translation of the Mathematica
 * {@code calG0[element, phase, T, TN]} function. It maintains a registry of
 * {@link ElementGibbs} instances (one per element) populated by parsing a
 * standard SGTE .tdb file, and routes {@link #gibbs} calls to the correct element.
 *
 * <h2>Usage</h2>
 * <pre>
 *   // Load from SGTE unary database file — all elements
 *   UnaryGibbs ug = UnaryGibbs.fromTdb("sgte_unary_v44.tdb");
 *
 *   // Load only a subset of elements
 *   UnaryGibbs ug = UnaryGibbs.fromTdb("sgte_unary_v44.tdb", "NB", "TI", "ZR");
 *
 *   // Reuse an already-parsed tdb (e.g. shared with the RK / CEF model reader)
 *   tdb database = new tdb("my_alloy_system.tdb");
 *   UnaryGibbs ug = UnaryGibbs.fromTdb(database);
 *
 *   // Evaluate — any phase name present in the .tdb file
 *   double g = ug.gibbs("NB",  PhaseType.BCC_A2,  1273.0);   // J/mol
 *   double g = ug.gibbs("ZR",  "OMEGA",            900.0);    // any tdb phase
 *   double g = ug.ghser("TI",                      1000.0);   // GHSER reference
 * </pre>
 *
 * <h2>Mathematica → Java mapping</h2>
 * <pre>
 *   calG0["Nb", "BCC_A2", T, TN]  →  ug.gibbs("Nb", PhaseType.BCC_A2, T)
 *   calG0["Zr", "HCP_A3", T, TN]  →  ug.gibbs("Zr", PhaseType.HCP_A3, T)
 *   calG0["Ti", "LIQUID",  T, TN]  →  ug.gibbs("Ti", PhaseType.LIQUID,  T)
 * </pre>
 * The {@code TN} argument (used only for range selection in Mathematica) is not needed
 * in Java — temperature range selection is handled inside each {@link SgteElementGibbs}.
 *
 * <h2>Adding elements</h2>
 * No Java code changes are needed. Any element with {@code PARAMETER G(...)} entries
 * in a loaded .tdb file is automatically available after calling a factory method.
 *
 * <h2>Thread safety</h2>
 * Instances are effectively immutable after construction and safe for concurrent use.
 */
public class UnaryGibbs {

    // ------------------------------------------------------------------
    // Registry  (key = upper-case element symbol)
    // ------------------------------------------------------------------

    private final Map<String, ElementGibbs> registry = new LinkedHashMap<>();

    // ------------------------------------------------------------------
    // Factory methods
    // ------------------------------------------------------------------

    /**
     * Creates a {@code UnaryGibbs} by parsing a SGTE .tdb file.
     *
     * <p>Every element in the file that has at least one {@code PARAMETER G(...)}
     * entry is automatically registered. The {@link tdb} parser handles FUNCTION
     * substitution, so evaluating the returned instance requires no further parsing.
     *
     * @param tdbFilePath   path to the SGTE .tdb file (e.g. {@code "sgte_unary_v44.tdb"})
     * @param elementFilter optional upper-case element symbols to load;
     *                      if omitted, all elements in the file are registered
     * @return fully populated {@code UnaryGibbs}
     * @throws IOException if the file cannot be read
     */
    public static UnaryGibbs fromTdb(String tdbFilePath, String... elementFilter)
            throws IOException {
        tdb database = new tdb(tdbFilePath);
        UnaryGibbs ug = new UnaryGibbs();
        ug.registerFromTdb(database, elementFilter);
        return ug;
    }

    /**
     * Creates a {@code UnaryGibbs} from an already-parsed {@link tdb} object.
     *
     * <p>Use this when the caller already holds a {@code tdb} instance loaded
     * for another purpose (e.g. to also read binary RK interaction parameters).
     * The {@code tdb} object's constructors must have already completed
     * (including {@code subFuncExpInParam()}) before calling this method.
     *
     * @param database      fully constructed {@code tdb} object
     * @param elementFilter optional element symbols to register; all if omitted
     * @return fully populated {@code UnaryGibbs}
     */
    public static UnaryGibbs fromTdb(tdb database, String... elementFilter) {
        UnaryGibbs ug = new UnaryGibbs();
        ug.registerFromTdb(database, elementFilter);
        return ug;
    }

    // ------------------------------------------------------------------
    // Primary API  — mirrors calG0[element, phase, T, TN]
    // ------------------------------------------------------------------

    /**
     * Returns G°(phase, T) for the given element in J/mol.
     *
     * <p>Directly replaces {@code calG0[element, phase, T, TN]} in Mathematica.
     *
     * @param element    element symbol (case-insensitive), e.g. {@code "Nb"}, {@code "TI"}
     * @param phaseName  phase name (case-insensitive), e.g. {@code "BCC_A2"}, {@code "OMEGA"}.
     *                   Use constants from {@link PhaseType} for the common cases.
     * @param T          temperature in Kelvin
     * @return           G°(phase, T) in J/mol
     * @throws IllegalArgumentException      if the element is not registered
     * @throws UnsupportedOperationException if the element has no data for the requested phase
     */
    public double gibbs(String element, String phaseName, double T) {
        return lookup(element).gibbs(phaseName, T);
    }

    /**
     * Returns the GHSER (reference state) value for an element at temperature T.
     *
     * <p>The reference phase is determined by the element's {@code ref_state}
     * in the tdb ELEMENT declaration: BCC for most refractory metals,
     * HCP for Ti, Zr, Re, etc.
     *
     * @param element  element symbol (case-insensitive)
     * @param T        temperature in Kelvin
     * @return         G°(reference, T) in J/mol
     */
    public double ghser(String element, double T) {
        return lookup(element).ghser(T);
    }

    /**
     * Returns the set of phase names for which the given element has data.
     *
     * <p>Useful for discovering what phases are available without triggering
     * {@link UnsupportedOperationException}.
     *
     * @param element  element symbol (case-insensitive)
     * @return         unmodifiable set of phase name strings (upper-case)
     */
    public Set<String> availablePhases(String element) {
        return lookup(element).availablePhases();
    }

    // ------------------------------------------------------------------
    // Registry inspection
    // ------------------------------------------------------------------

    /** Returns {@code true} if the element is registered (case-insensitive). */
    public boolean contains(String element) {
        return registry.containsKey(element.toUpperCase());
    }

    /** Returns an unmodifiable view of all registered upper-case element symbols. */
    public Set<String> registeredElements() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    /**
     * Populates the registry from a parsed {@link tdb} object.
     *
     * <p>For each element name returned by {@link tdb#getElementNames()}, uses
     * {@link UnaryGibbsBuilder} to extract G parameters and build {@link SgteElementGibbs}
     * instances. Elements with no G parameters (e.g. VA, or elements whose data are
     * defined as FUNCTION references that were not resolved) are silently skipped.
     *
     * @param database      fully parsed tdb (all subFuncExpInParam calls done)
     * @param elementFilter optional symbols to register; registers all if empty
     */
    public void registerFromTdb(tdb database, String... elementFilter) {
        Set<String> filterSet = new HashSet<>();
        for (String e : elementFilter) filterSet.add(e.toUpperCase());

        // Get element names from database
        java.util.List<String> elementNames = new java.util.ArrayList<>(database.getElementNames());

        // Register only elements in the database that have G parameters
        for (String elemName : elementNames) {
            String key = elemName.toUpperCase();
            if ("VA".equals(key)) continue;
            if (!filterSet.isEmpty() && !filterSet.contains(key)) continue;
            try {
                // Use UnaryGibbsBuilder to extract G parameters and build SgteElementGibbs
                ElementGibbs elementGibbs = UnaryGibbsBuilder.build(elemName, database);
                registry.put(key, elementGibbs);
            } catch (IllegalStateException | NullPointerException ignored) {
                // No G parameters found for this element — skip silently
            }
        }
    }

    /**
     * Manually registers (or replaces) a single {@link ElementGibbs} entry.
     * Useful for overriding a specific element with custom-loaded data.
     *
     * @param impl  element implementation to register
     */
    public void register(ElementGibbs impl) {
        registry.put(impl.elementSymbol().toUpperCase(), impl);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private UnaryGibbs() {}  // use factory methods

    private ElementGibbs lookup(String element) {
        ElementGibbs eg = registry.get(element.toUpperCase());
        if (eg == null) {
            throw new IllegalArgumentException(
                    "No SGTE data registered for element '" + element
                    + "'. Registered: " + registry.keySet());
        }
        return eg;
    }
}
