package infra;

import database.tdb;
import thermocalc.rk.BinaryParam;
import thermocalc.rk.QuaternaryParam;
import thermocalc.rk.RkGibbs;
import thermocalc.rk.TernaryParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * {@link thermocalc.equil.EquilibriumSolver}.
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li>For each element, creates a {@link RkGibbs.G0Function} backed by
 *       {@link TdbUnaryGibbs} — so G°(T) is evaluated from the TDB SGTE
 *       polynomials.</li>
 *   <li>Scans TDB {@code PARAMETER L(...)} entries for the phase and extracts
 *       {@link BinaryParam} objects (a[s] + b[s]·T form).</li>
 *   <li>Constructs {@link RkGibbs} and wraps it in {@link RkPhaseModelAdapter}.</li>
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

        // ── Step 1: G0 functions from TdbUnaryGibbs ──────────────────────
        RkGibbs.G0Function[] g0 = new RkGibbs.G0Function[nc];
        for (int i = 0; i < nc; i++) {
            String elem = elements.get(i);
            try {
                TdbUnaryGibbs unary = new TdbUnaryGibbs(elem, database);
                final String phaseKey = phaseName;
                g0[i] = T -> unary.gibbs(phaseKey, T);
            } catch (Exception ex) {
                LOG.warning("G0 not found for element '" + elem
                        + "' in phase '" + phaseName + "': " + ex.getMessage()
                        + " — using zero reference energy.");
                g0[i] = T -> 0.0;
            }
        }

        // ── Step 2: Extract binary L parameters from TDB ─────────────────
        ArrayList<tdb.Parameter> params =
                database.getPhaseParam(new ArrayList<>(elements), phaseName);

        List<BinaryParam>     binaries     = extractBinaryParams(params, elements);
        List<TernaryParam>    ternaries    = Collections.emptyList();
        List<QuaternaryParam> quaternaries = Collections.emptyList();

        // ── Step 3: Assemble model ────────────────────────────────────────
        RkGibbs gibbs = new RkGibbs(nc, g0, binaries, ternaries, quaternaries);
        return new RkPhaseModelAdapter(gibbs, phaseName);
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
}
