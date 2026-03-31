package system.model.rk;

import system.database.tdb;
import system.model.unary.ElementGibbs;
import system.model.unary.SgteElementGibbs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Factory that builds a {@link RkPhaseModelAdapter} for a named phase
 * directly from a parsed {@link tdb} database object.
 *
 * <p>Bridges the legacy TDB parameter format into the clean
 * {@link RkGibbs} + {@link RkPhaseModelAdapter} model hierarchy used by
 * {@link calc.equil.EquilibriumSolver}.
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li>For each element, creates a {@link SgteElementGibbs} instance that evaluates
 *       G°(T) from the TDB SGTE polynomials.</li>
 *   <li>Scans TDB {@code PARAMETER L(...)} entries for the phase and extracts
 *       {@link BinaryParam} objects (a[s] + b[s]·T form).</li>
 *   <li>Constructs {@link RkGibbs} with ElementGibbs[] and phase name,
 *       then wraps it in {@link RkPhaseModelAdapter}.</li>
 * </ol>
 */
public class RkPhaseModelFactory {

    private static final Logger LOG = Logger.getLogger(RkPhaseModelFactory.class.getName());

    /**
     * Build an {@link RkPhaseModelAdapter} for {@code phaseName} using the
     * provided element list and TDB database.
     *
     * @param phaseName  TDB phase name, e.g. "LIQUID", "BCC_A2"
     * @param elements   ordered list of element symbols (defines component indices)
     * @param database   fully-parsed tdb object (subFuncExpInParam already run)
     * @return ready-to-use {@link RkPhaseModelAdapter}
     */
    public static RkPhaseModelAdapter build(String phaseName,
                                            List<String> elements,
                                            tdb database) {
        int nc = elements.size();

        // ── Step 1: Build ElementGibbs for each component (moved to model layer) ──
        ElementGibbs[] g0Elements = new ElementGibbs[nc];
        for (int i = 0; i < nc; i++) {
            String elem = elements.get(i);
            try {
                // Build SgteElementGibbs for this element (evaluates SGTE polynomials)
                g0Elements[i] = buildElementGibbs(elem, phaseName, database);
            } catch (Exception ex) {
                LOG.warning("G0 not found for element '" + elem
                        + "' in phase '" + phaseName + "': " + ex.getMessage()
                        + " — phase will have no G0 data.");
                throw new IllegalStateException(
                        "Cannot build model: missing G0 data for element '" + elem
                        + "' in phase '" + phaseName + "'", ex);
            }
        }

        // ── Step 2: Extract binary L parameters from TDB ─────────────────
        ArrayList<tdb.Parameter> params =
                database.getPhaseParam(new ArrayList<>(elements), phaseName);

        List<BinaryParam>     binaries     = extractBinaryParams(params, elements);
        List<TernaryParam>    ternaries    = extractTernaryParams(params, elements);
        List<QuaternaryParam> quaternaries = Collections.emptyList();

        // ── Step 3: Assemble model ────────────────────────────────────────
        RkGibbs gibbs = new RkGibbs(nc, g0Elements, phaseName, binaries, ternaries, quaternaries);
        // RkPhaseModelAdapter constructor will automatically populate g0List,
        // g0TList, g0PList via the inherited populateG0Lists() method
        return new RkPhaseModelAdapter(gibbs, phaseName, new ArrayList<>(elements));
    }

    // ------------------------------------------------------------------
    // Private: extract BinaryParam list from TDB PARAMETER L entries
    // ------------------------------------------------------------------

    /**
     * Scans TDB parameters for type "L" (RK interaction terms) and converts
     * them to {@link BinaryParam} objects.
     *
     * <p>TDB convention: each order-s parameter has constituents identifying the
     * component pair, and a coefficient list where {@code subCoeffList[0]} is the
     * temperature-independent part {@code a_s} and {@code subCoeffList[1]} is the
     * T-linear coefficient {@code b_s}, i.e. {@code L_s = a_s + b_s·T}.
     */
    private static List<BinaryParam> extractBinaryParams(
            ArrayList<tdb.Parameter> params, List<String> elements) {

        if (params == null || params.isEmpty()) return Collections.emptyList();

        // key = "i:j" (i<j) → sorted map order→[a_s, b_s]
        Map<String, TreeMap<Integer, double[]>> pairCoeffs = new HashMap<>();
        Map<String, int[]>                      pairIndices = new HashMap<>();

        for (tdb.Parameter param : params) {
            if (!"L".equalsIgnoreCase(param.getType())) continue;

            ArrayList<ArrayList<String>> constituents = param.getConstituentList();
            if (constituents == null || constituents.size() < 2) continue;

            List<String> sub0 = constituents.get(0);
            List<String> sub1 = constituents.get(1);
            if (sub0 == null || sub0.isEmpty() || sub1 == null || sub1.isEmpty()) continue;

            int i = elements.indexOf(sub0.get(0));
            int j = elements.indexOf(sub1.get(0));
            if (i < 0 || j < 0 || i == j) continue;
            if (i > j) { int tmp = i; i = j; j = tmp; }

            String key = i + ":" + j;
            pairIndices.put(key, new int[]{i, j});
            pairCoeffs.computeIfAbsent(key, k -> new TreeMap<>());

            ArrayList<tdb.Exp> expList = param.getExpList();
            if (expList == null || expList.isEmpty()) continue;
            ArrayList<Double> coeffs = expList.get(0).getSubCoeffList();
            if (coeffs == null || coeffs.isEmpty()) continue;

            double a = coeffs.size() >= 1 ? coeffs.get(0) : 0.0;
            double b = coeffs.size() >= 2 ? coeffs.get(1) : 0.0;
            pairCoeffs.get(key).put(param.getOrder(), new double[]{a, b});
        }

        List<BinaryParam> result = new ArrayList<>();
        for (Map.Entry<String, int[]> e : pairIndices.entrySet()) {
            TreeMap<Integer, double[]> orderMap = pairCoeffs.get(e.getKey());
            if (orderMap == null || orderMap.isEmpty()) continue;

            int maxOrder = orderMap.lastKey();
            double[] aArr = new double[maxOrder + 1];
            double[] bArr = new double[maxOrder + 1];
            for (Map.Entry<Integer, double[]> oe : orderMap.entrySet()) {
                aArr[oe.getKey()] = oe.getValue()[0];
                bArr[oe.getKey()] = oe.getValue()[1];
            }
            int[] ij = e.getValue();
            result.add(new BinaryParam(ij[0], ij[1], aArr, bArr));
        }
        return result;
    }

    /**
     * Scans TDB parameters for ternary "L" entries (3 constituents) and converts
     * them to {@link TernaryParam} objects.
     *
     * <p>TDB convention: each order-m parameter (m=0,1,2) corresponds to the v
     * coefficient for component idxI, idxJ, idxK respectively:
     * {@code L(i,j,k;m) = aₘ + bₘ·T} where m identifies which component's v-term.
     */
    private static List<TernaryParam> extractTernaryParams(
            ArrayList<tdb.Parameter> params, List<String> elements) {

        if (params == null || params.isEmpty()) return Collections.emptyList();

        // key = "i:j:k" (i<j<k) → map of order(0/1/2) → [a, b]
        Map<String, TreeMap<Integer, double[]>> tripletCoeffs  = new HashMap<>();
        Map<String, int[]>                      tripletIndices = new HashMap<>();

        for (tdb.Parameter param : params) {
            if (!"L".equalsIgnoreCase(param.getType())) continue;

            ArrayList<ArrayList<String>> constituents = param.getConstituentList();
            if (constituents == null || constituents.size() != 3) continue;

            List<String> sub0 = constituents.get(0);
            List<String> sub1 = constituents.get(1);
            List<String> sub2 = constituents.get(2);
            if (sub0 == null || sub0.isEmpty()
             || sub1 == null || sub1.isEmpty()
             || sub2 == null || sub2.isEmpty()) continue;

            int i = elements.indexOf(sub0.get(0));
            int j = elements.indexOf(sub1.get(0));
            int k = elements.indexOf(sub2.get(0));
            if (i < 0 || j < 0 || k < 0) continue;

            // Sort indices so i < j < k
            int[] sorted = {i, j, k};
            java.util.Arrays.sort(sorted);
            i = sorted[0]; j = sorted[1]; k = sorted[2];
            if (i == j || j == k) continue;

            String key = i + ":" + j + ":" + k;
            tripletIndices.put(key, new int[]{i, j, k});
            tripletCoeffs.computeIfAbsent(key, x -> new TreeMap<>());

            ArrayList<tdb.Exp> expList = param.getExpList();
            if (expList == null || expList.isEmpty()) continue;
            ArrayList<Double> coeffs = expList.get(0).getSubCoeffList();
            if (coeffs == null || coeffs.isEmpty()) continue;

            double a = coeffs.size() >= 1 ? coeffs.get(0) : 0.0;
            double b = coeffs.size() >= 2 ? coeffs.get(1) : 0.0;
            // order 0/1/2 maps to v-coefficient for i/j/k respectively
            tripletCoeffs.get(key).put(param.getOrder(), new double[]{a, b});
        }

        List<TernaryParam> result = new ArrayList<>();
        for (Map.Entry<String, int[]> e : tripletIndices.entrySet()) {
            TreeMap<Integer, double[]> orderMap = tripletCoeffs.get(e.getKey());
            if (orderMap == null || orderMap.isEmpty()) continue;

            double[] aArr = new double[3];
            double[] bArr = new double[3];
            for (Map.Entry<Integer, double[]> oe : orderMap.entrySet()) {
                int ord = oe.getKey();
                if (ord < 0 || ord > 2) continue;
                aArr[ord] = oe.getValue()[0];
                bArr[ord] = oe.getValue()[1];
            }
            int[] ijk = e.getValue();
            try {
                result.add(new TernaryParam(ijk[0], ijk[1], ijk[2], aArr, bArr));
            } catch (IllegalArgumentException ex) {
                LOG.warning("Skipping invalid ternary param " + e.getKey() + ": " + ex.getMessage());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Private: build SgteElementGibbs from TDB (SGTE polynomial evaluator)
    // ------------------------------------------------------------------

    /**
     * Creates a {@link SgteElementGibbs} for the given element from TDB data.
     * This extracts SGTE polynomial coefficients and compiles them into
     * a phase-indexed structure for efficient G0(T) evaluation.
     *
     * <p>Note: G0 calculation is a <b>model layer concern</b>, not database layer.
     * This helper creates the model-layer object from database-layer data.
     *
     * @param element       element symbol (e.g. "NB", "TI")
     * @param phaseName     phase name (e.g. "BCC_A2", "LIQUID")
     * @param database      parsed TDB object
     * @return              SgteElementGibbs evaluator
     * @throws IllegalStateException if element has no G parameters in the phase
     */
    private static SgteElementGibbs buildElementGibbs(String element, String phaseName,
                                                      tdb database) {
        // Use UnaryGibbsBuilder to extract all phase data from TDB
        return system.database.UnaryGibbsBuilder.build(element, database);
    }
}
