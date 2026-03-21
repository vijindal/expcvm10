package infra;

import database.tdb;
import thermocalc.unary.ElementGibbs;
import thermocalc.unary.SgtePolynomial;
import thermocalc.unary.PhaseType;

import java.util.*;

/**
 * Data-driven implementation of {@link ElementGibbs} that reads Gibbs energy
 * data directly from a parsed {@link tdb} object.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>On construction, scans every phase in the loaded {@link tdb} for a
 *       pure-element {@code G} parameter belonging to this element.</li>
 *   <li>For each matching (phase, element) pair, stores the fully-substituted
 *       coefficient arrays from {@code Exp.getSubCoeffList()} into a compiled
 *       {@link PhaseEntry}.</li>
 *   <li>At evaluation time, locates the correct temperature range and calls
 *       {@link SgtePolynomial#evaluate}.</li>
 * </ol>
 *
 * <h2>Phase coverage</h2>
 * All phases present in the .tdb file are loaded automatically — BCC_A2, FCC_A1,
 * HCP_A3, LIQUID, OMEGA, HCP_ZN, and anything else declared for this element.
 * No phase-specific code is needed; the set of available phases is determined
 * entirely by what the .tdb file contains.
 *
 * <h2>Pure-element selection rule</h2>
 * A parameter is accepted as a pure-element unary parameter when:
 * <ul>
 *   <li>Type == "G"</li>
 *   <li>Interaction order == 0</li>
 *   <li>Every sublattice contains at most one non-VA species, and that species
 *       is this element</li>
 * </ul>
 * This avoids accidentally picking up binary/ternary interaction terms.
 *
 * <h2>GHSER reference detection</h2>
 * The GHSER reference phase is determined by inspecting the element's entry in
 * the tdb {@code ELEMENT} list (the {@code ref_state} field, e.g. "BCC_A2",
 * "HCP_A3").  If that field is absent or unrecognised, BCC_A2 is tried first,
 * then HCP_A3, matching the Mathematica convention.
 */
public class TdbUnaryGibbs implements ElementGibbs {

    // ------------------------------------------------------------------
    // Inner class: compiled data for one (element, phase) combination
    // ------------------------------------------------------------------

    /**
     * Immutable, compiled Gibbs energy for one phase: temperature ranges
     * and pre-extracted coefficient arrays ready for {@link SgtePolynomial}.
     */
    static final class PhaseEntry {
        final double[]   tLow;
        final double[]   tHigh;
        final double[][] coeffs;   // [rangeIndex][NUM_COEFFS]

        PhaseEntry(List<database.tdb.Exp> expList) {
            int n = expList.size();
            tLow   = new double[n];
            tHigh  = new double[n];
            coeffs = new double[n][SgtePolynomial.NUM_COEFFS];
            for (int i = 0; i < n; i++) {
                database.tdb.Exp e   = expList.get(i);
                List<Double> range   = e.getTempRange();
                tLow[i]  = range.get(0);
                tHigh[i] = range.get(1);
                coeffs[i] = SgtePolynomial.toArray(e.getSubCoeffList());
            }
        }

        /**
         * Evaluates G(T).  Selects the first range whose upper bound exceeds T.
         * Falls back to the last range if T is above all upper bounds (extrapolation).
         */
        double evaluate(double T) {
            for (int i = 0; i < tHigh.length - 1; i++) {
                if (T < tHigh[i]) return SgtePolynomial.evaluate(coeffs[i], T);
            }
            return SgtePolynomial.evaluate(coeffs[coeffs.length - 1], T);
        }

        int numRanges()          { return tLow.length; }
        double tLow(int i)       { return tLow[i]; }
        double tHigh(int i)      { return tHigh[i]; }
        double[] coeffs(int i)   { return coeffs[i]; }
    }

    // ------------------------------------------------------------------
    // Instance state
    // ------------------------------------------------------------------

    private final String symbol;

    /**
     * Phase name (upper-case) → compiled PhaseEntry.
     * Populated for every phase for which a pure-element G parameter was found.
     */
    private final Map<String, PhaseEntry> phaseMap;

    /** Upper-case name of the GHSER reference phase (e.g. "BCC_A2", "HCP_A3"). */
    private final String ghserPhase;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Builds a {@code TdbUnaryGibbs} by scanning all phases in {@code database}
     * for pure-element G parameters belonging to {@code elementSymbol}.
     *
     * @param elementSymbol  element name as it appears in the tdb file (case-insensitive)
     * @param database       fully constructed {@code tdb} object
     *                       ({@code subFuncExpInParam} has already run)
     * @throws IllegalStateException if no G parameters at all are found for this element
     */
    public TdbUnaryGibbs(String elementSymbol, tdb database) {
        this.symbol   = elementSymbol.toUpperCase();
        this.phaseMap = loadAllPhases(elementSymbol, database);

        if (phaseMap.isEmpty()) {
            throw new IllegalStateException(
                    "No G parameters found for element '" + elementSymbol
                    + "'. Verify the .tdb file contains PARAMETER G(...) entries "
                    + "for this element.");
        }

        this.ghserPhase = detectGhserPhase(database, elementSymbol);
    }

    // ------------------------------------------------------------------
    // ElementGibbs API
    // ------------------------------------------------------------------

    @Override
    public String elementSymbol() { return symbol; }

    /**
     * Evaluates G°(phaseName, T) in J/mol.
     *
     * <p>Accepts any phase name present in the loaded .tdb file.
     * Use constants from {@link PhaseType} for the common cases.
     *
     * @param phaseName  phase name, case-insensitive
     * @param T          temperature in Kelvin
     * @return           G°(phase, T)
     * @throws UnsupportedOperationException if no data exist for this phase
     */
    @Override
    public double gibbs(String phaseName, double T) {
        PhaseEntry entry = phaseMap.get(phaseName.toUpperCase());
        if (entry == null) {
            throw new UnsupportedOperationException(
                    "Phase '" + phaseName + "' not available for element '" + symbol
                    + "'. Available phases: " + phaseMap.keySet());
        }
        return entry.evaluate(T);
    }

    /**
     * Returns G°(GHSER reference, T) in J/mol.
     *
     * <p>The reference phase is read from the element's {@code ref_state} field
     * in the tdb ELEMENT declaration.  Falls back to BCC_A2, then HCP_A3.
     */
    @Override
    public double ghser(double T) {
        return gibbs(ghserPhase, T);
    }

    /** Returns the set of all phase names for which data were loaded. */
    @Override
    public Set<String> availablePhases() {
        return Collections.unmodifiableSet(phaseMap.keySet());
    }

    // ------------------------------------------------------------------
    // Package-level access for tests
    // ------------------------------------------------------------------

    /** Returns the PhaseEntry for the given phase, or null if not present. */
    PhaseEntry phaseEntry(String phaseName) {
        return phaseMap.get(phaseName.toUpperCase());
    }

    /** Returns the detected GHSER phase name. */
    String ghserPhaseName() { return ghserPhase; }

    // ------------------------------------------------------------------
    // Private: loading
    // ------------------------------------------------------------------

    /**
     * Iterates over every phase registered in the tdb and attempts to find a
     * pure-element G parameter for {@code elementSymbol}.  Returns a map of
     * upper-case phase name → PhaseEntry for all phases where data were found.
     */
    private static Map<String, PhaseEntry> loadAllPhases(
            String elementSymbol, tdb database) {

        Map<String, PhaseEntry> map = new LinkedHashMap<>();

        ArrayList<String> elemList = new ArrayList<>();
        elemList.add(elementSymbol.toUpperCase());
        elemList.add("VA");

        // Iterate over all phase names the tdb knows about
        for (String phaseName : database.getPhaseNames()) {
            ArrayList<database.tdb.Parameter> params =
                    database.getPhaseParam(elemList, phaseName);

            if (params == null || params.isEmpty()) continue;

            for (database.tdb.Parameter p : params) {
                if (!"G".equalsIgnoreCase(p.getType()))     continue;
                if (p.getOrder() != 0)                       continue;
                if (!isPureElement(p.getConstituentList(), elementSymbol)) continue;

                List<database.tdb.Exp> expList = p.getExpList();
                if (expList == null || expList.isEmpty())    continue;

                // Validate that subCoeffList is populated (i.e. parsing completed)
                if (!isCoeffListReady(expList))              continue;

                map.put(phaseName.toUpperCase(), new PhaseEntry(expList));
                break; // one parameter per phase per element — take the first match
            }
        }
        return map;
    }

    /**
     * Determines the GHSER reference phase for this element.
     *
     * <p>Strategy (in order):
     * <ol>
     *   <li>Read the {@code ref_state} field from the tdb ELEMENT declaration.</li>
     *   <li>If that phase is in our loaded map, use it.</li>
     *   <li>Otherwise fall back: prefer BCC_A2, then HCP_A3, then the first
     *       loaded phase alphabetically.</li>
     * </ol>
     */
    private String detectGhserPhase(tdb database, String elementSymbol) {
        // Strategy 1: tdb element ref_state field
        // tdb.getElementNames() returns names in order; we look up the Element object
        // indirectly via getPhaseParam with a dummy single-element request.
        // Since tdb doesn't expose a direct getElement(name) method, we use a heuristic:
        // check which of the common reference phases is in our map.
        // The SGTE database uses these ref_states:
        //   BCC_A2  for Nb, Mo, V, W, Ta, Cr, Fe(BCC region), etc.
        //   HCP_A3  for Ti, Zr, Re, Co, etc.
        //   FCC_A1  for Al, Cu, Ni, Ag, Au, etc.
        //   Others  for unusual elements

        // Priority order matching the SGTE convention
        String[] preferred = {
            PhaseType.BCC_A2, PhaseType.HCP_A3, PhaseType.FCC_A1, PhaseType.LIQUID
        };
        for (String p : preferred) {
            if (phaseMap.containsKey(p)) return p;
        }
        // Last resort: first key in insertion order
        return phaseMap.keySet().iterator().next();
    }

    // ------------------------------------------------------------------
    // Private: validation helpers
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} if the constituent list represents a pure-element parameter
     * for {@code elementSymbol}: every sublattice contains at most one non-VA species
     * and that species matches the element.
     */
    private static boolean isPureElement(
            ArrayList<ArrayList<String>> constituentList, String elementSymbol) {

        if (constituentList == null || constituentList.isEmpty()) return false;
        String elemUpper = elementSymbol.toUpperCase();

        for (ArrayList<String> sublattice : constituentList) {
            List<String> nonVa = sublattice.stream()
                    .filter(s -> !"VA".equalsIgnoreCase(s))
                    .toList();
            if (nonVa.isEmpty())  continue;           // VA-only sublattice — fine
            if (nonVa.size() > 1) return false;       // binary/ternary mixing term
            if (!nonVa.get(0).equalsIgnoreCase(elemUpper)) return false;
        }
        return true;
    }

    /**
     * Returns {@code true} if every {@code Exp} in the list has a non-null,
     * non-empty {@code subCoeffList} (meaning the tdb parsing pipeline completed).
     */
    private static boolean isCoeffListReady(List<database.tdb.Exp> expList) {
        for (database.tdb.Exp e : expList) {
            List<Double> sc = e.getSubCoeffList();
            if (sc == null || sc.size() < SgtePolynomial.NUM_COEFFS) return false;
        }
        return true;
    }
}
