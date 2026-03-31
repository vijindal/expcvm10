package system.database;

import system.database.tdb;
import system.model.unary.SgteElementGibbs;
import system.model.unary.SgtePolynomial;
import system.model.unary.PhaseType;

import java.util.*;

/**
 * Builder that extracts pure-element Gibbs energy data from a parsed TDB database
 * and constructs {@link SgteElementGibbs} instances.
 *
 * <p><b>DATABASE LAYER ONLY</b> — This class reads TDB data and performs
 * coefficient extraction. It does NOT evaluate Gibbs energies (that's SgteElementGibbs's job).
 *
 * <h2>Separation of concerns</h2>
 * <ul>
 *   <li><b>UnaryGibbsBuilder (database layer):</b> Parse TDB → extract coefficients → build data structure</li>
 *   <li><b>SgteElementGibbs (model layer):</b> Given coefficients → evaluate G(T) at any phase</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   tdb database = new tdb("sgte_unary_v44.tdb");
 *   SgteElementGibbs sgteNb = UnaryGibbsBuilder.build("NB", database);
 *   double gBcc = sgteNb.gibbs("BCC_A2", 1273.0);  // J/mol
 * </pre>
 *
 * <h2>Pure-element selection rule</h2>
 * A TDB parameter is accepted as pure-element when:
 * <ul>
 *   <li>Type == "G"</li>
 *   <li>Interaction order == 0</li>
 *   <li>Every sublattice contains at most one non-VA species, and that species matches the element</li>
 * </ul>
 * This avoids picking up binary/ternary interaction terms.
 *
 * <h2>GHSER reference detection</h2>
 * The reference phase is determined by:
 * <ol>
 *   <li>Priority order: BCC_A2 → HCP_A3 → FCC_A1 → LIQUID (SGTE convention)</li>
 *   <li>Fall back to first loaded phase if none of the above are present</li>
 * </ol>
 */
public class UnaryGibbsBuilder {

    /**
     * Builds a {@link SgteElementGibbs} for the given element by extracting
     * all pure-element G parameters from the TDB database.
     *
     * @param elementSymbol  element name (case-insensitive), e.g., "NB", "TI", "ZR"
     * @param database       fully parsed TDB object (subFuncExpInParam already run)
     * @return               SgteElementGibbs ready for G(T) evaluation
     * @throws IllegalStateException if no G parameters found for this element
     */
    public static SgteElementGibbs build(String elementSymbol, tdb database) {
        String symbol = elementSymbol.toUpperCase();

        // Extract all phases and their coefficient data
        Map<String, SgteElementGibbs.PhaseEntry> phases = loadAllPhases(elementSymbol, database);

        if (phases.isEmpty()) {
            throw new IllegalStateException(
                    "No G parameters found for element '" + elementSymbol
                    + "'. Verify the .tdb file contains PARAMETER G(...) entries "
                    + "for this element.");
        }

        // Detect GHSER reference phase
        String refPhase = detectGhserPhase(database, elementSymbol, phases);

        // Construct and return the model-layer object
        return new SgteElementGibbs(symbol, phases, refPhase);
    }

    // ------------------------------------------------------------------
    // Private: TDB parsing and coefficient extraction
    // ------------------------------------------------------------------

    /**
     * Iterates over every phase in the TDB and extracts pure-element G parameters.
     * Returns a map of phase name → pre-compiled coefficient data.
     *
     * @param elementSymbol  element name (case-insensitive)
     * @param database       parsed TDB
     * @return               map of upper-case phase name → PhaseEntry
     */
    private static Map<String, SgteElementGibbs.PhaseEntry> loadAllPhases(
            String elementSymbol, tdb database) {

        Map<String, SgteElementGibbs.PhaseEntry> map = new LinkedHashMap<>();

        ArrayList<String> elemList = new ArrayList<>();
        elemList.add(elementSymbol.toUpperCase());
        elemList.add("VA");

        // Iterate over all phase names in the TDB
        for (String phaseName : database.getPhaseNames()) {
            ArrayList<system.database.tdb.Parameter> params =
                    database.getPhaseParam(elemList, phaseName);

            if (params == null || params.isEmpty()) continue;

            for (system.database.tdb.Parameter p : params) {
                // Validate that this is a pure-element parameter
                if (!"G".equalsIgnoreCase(p.getType()))     continue;
                if (p.getOrder() != 0)                       continue;
                if (!isPureElement(p.getConstituentList(), elementSymbol)) continue;

                List<system.database.tdb.Exp> expList = p.getExpList();
                if (expList == null || expList.isEmpty())    continue;
                if (!isCoeffListReady(expList))              continue;

                // Extract coefficient arrays for all temperature ranges
                int n = expList.size();
                double[] tLows = new double[n];
                double[] tHighs = new double[n];
                double[][] coeffs = new double[n][SgtePolynomial.NUM_COEFFS];

                for (int i = 0; i < n; i++) {
                    system.database.tdb.Exp e = expList.get(i);
                    List<Double> range = e.getTempRange();
                    tLows[i] = range.get(0);
                    tHighs[i] = range.get(1);
                    coeffs[i] = SgtePolynomial.toArray(e.getSubCoeffList());
                }

                // Store compiled coefficients
                map.put(phaseName.toUpperCase(), new SgteElementGibbs.PhaseEntry(tLows, tHighs, coeffs));
                break; // One parameter per phase per element — use first match
            }
        }
        return map;
    }

    /**
     * Determines the GHSER reference phase for the element.
     *
     * <p>Strategy (in order):
     * <ol>
     *   <li>Try SGTE priority order: BCC_A2 → HCP_A3 → FCC_A1 → LIQUID</li>
     *   <li>Fall back to first loaded phase if none of the above exist</li>
     * </ol>
     *
     * @param database  parsed TDB
     * @param elementSymbol  element name
     * @param phases    map of available phases for this element
     * @return          upper-case phase name to use as GHSER reference
     */
    private static String detectGhserPhase(tdb database, String elementSymbol,
                                          Map<String, SgteElementGibbs.PhaseEntry> phases) {
        // SGTE convention: most elements use BCC_A2, some use HCP_A3, etc.
        String[] preferred = {
            PhaseType.BCC_A2, PhaseType.HCP_A3, PhaseType.FCC_A1, PhaseType.LIQUID
        };
        for (String p : preferred) {
            if (phases.containsKey(p)) return p;
        }
        // Last resort: first phase in insertion order
        return phases.keySet().iterator().next();
    }

    // ------------------------------------------------------------------
    // Private: validation helpers
    // ------------------------------------------------------------------

    /**
     * Checks if a constituent list represents a pure-element parameter
     * for the given element (no binary/ternary mixing).
     *
     * @param constituentList  sublattice composition from TDB parameter
     * @param elementSymbol    element name
     * @return                 true if this is a valid pure-element parameter
     */
    private static boolean isPureElement(
            ArrayList<ArrayList<String>> constituentList, String elementSymbol) {

        if (constituentList == null || constituentList.isEmpty()) return false;
        String elemUpper = elementSymbol.toUpperCase();

        for (ArrayList<String> sublattice : constituentList) {
            List<String> nonVa = sublattice.stream()
                    .filter(s -> !"VA".equalsIgnoreCase(s))
                    .toList();
            if (nonVa.isEmpty())  continue;           // VA-only sublattice — OK
            if (nonVa.size() > 1) return false;       // Multiple species — binary/ternary
            if (!nonVa.get(0).equalsIgnoreCase(elemUpper)) return false;  // Wrong element
        }
        return true;
    }

    /**
     * Checks if the TDB expression list has been fully parsed
     * (coefficients have been extracted and substituted).
     *
     * @param expList  list of expressions from TDB parameter
     * @return         true if all expressions have complete coefficient lists
     */
    private static boolean isCoeffListReady(List<system.database.tdb.Exp> expList) {
        for (system.database.tdb.Exp e : expList) {
            List<Double> sc = e.getSubCoeffList();
            if (sc == null || sc.size() < SgtePolynomial.NUM_COEFFS) return false;
        }
        return true;
    }
}
