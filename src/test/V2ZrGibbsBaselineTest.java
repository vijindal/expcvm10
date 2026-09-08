package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;

import java.util.Arrays;
import java.util.List;

/**
 * Real-data baseline for the V2Zr Gibbs energy in Fig. 9 of:
 *
 * J. Cui, C. Guo, L. Zou, C. Li and Z. Du,
 * "Thermodynamic modeling of the V-Zr system supported by key experiments",
 * CALPHAD 53 (2016) 122-129.
 *
 * V2Zr is modeled as:
 *
 *       (V,Zr)2(V,Zr)
 *
 * At the stoichiometric V:ZR end member:
 *
 *       y = [1, 0, 0, 1]
 *
 * all configurational and higher-order interaction contributions vanish.
 * Therefore the CEF Gibbs energy must equal the V:ZR end-member parameter:
 *
 *       G(V2ZR,V:ZR;0)
 *
 * The supplied VZR-re2.TDB contains:
 *
 *       G(V:ZR)
 *         = 2*GHSERVV + GHSERZR
 *           - 12672.959 + 3.8143614*T
 *
 * The expected value is reconstructed independently from the explicit
 * GHSERVV/GHSERZR function definitions in VZR-re2.TDB.
 *
 * This test deliberately does NOT use UnaryGibbs to calculate the expected
 * value, because that would reuse the TDB function-substitution machinery
 * being independently tested.
 *
 * Units: J/mol formula unit.
 */
public class V2ZrGibbsBaselineTest {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    /*
     * The CEF value and the independent analytical expression are both
     * deterministic double calculations, so a tight tolerance is appropriate.
     */
    private static final double ABS_TOL = 1.0e-7;
    private static final double REL_TOL = 1.0e-12;

    /*
     * These temperatures correspond to the range shown in Fig. 9 and stay
     * within the first/second SGTE ranges of the supplied database.
     */
    private static final double[] TEST_TEMPERATURES = {
        298.15,
        500.0,
        1000.0,
        1500.0,
        2000.0
    };

    public static void main(String[] args) throws Exception {

        printHeader();

        // ============================================================
        // 1. Load the actual V-Zr database
        // ============================================================

        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phases = Arrays.asList(PHASE_NAME);

        @SuppressWarnings("unchecked")
        List<PhaseModel> models =
                (List<PhaseModel>) parser.buildPhaseModels(
                        elements,
                        phases);

        if (models.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one " + PHASE_NAME
                    + " model, but obtained " + models.size());
        }

        PhaseModel phase = models.get(0);

        if (phase.gibbs == null) {
            throw new IllegalStateException(
                    PHASE_NAME + " was not constructed as a CEF model.");
        }

        // ============================================================
        // 2. Verify the expected CEF structure
        // ============================================================

        double[] siteRatios = phase.gibbs.stoichiometry();
        int[] constituents =
                phase.gibbs.constituentsPerSublattice();

        if (siteRatios.length != 2
                || constituents.length != 2
                || Math.abs(siteRatios[0] - 2.0) > ABS_TOL
                || Math.abs(siteRatios[1] - 1.0) > ABS_TOL
                || constituents[0] != 2
                || constituents[1] != 2) {

            throw new AssertionError(
                    "Unexpected V2ZR CEF structure: "
                    + "siteRatios="
                    + Arrays.toString(siteRatios)
                    + ", constituents="
                    + Arrays.toString(constituents));
        }

        // ============================================================
        // 3. Stoichiometric V2Zr end member
        // ============================================================

        /*
         * Sublattice 1: V = 1, ZR = 0
         * Sublattice 2: V = 0, ZR = 1
         *
         * Therefore:
         *
         *       y = [1,0,0,1]
         */
        final double[] y = {
            1.0, 0.0,
            0.0, 1.0
        };

        System.out.println("Phase        : " + phase.phaseName);
        System.out.println("Model        : (V,ZR)2(V,ZR)");
        System.out.println("Site ratios  : "
                + Arrays.toString(siteRatios));
        System.out.println("Constituents : "
                + Arrays.toString(constituents));
        System.out.println("y            : "
                + Arrays.toString(y));
        System.out.println("x expected   : [2/3, 1/3]");
        System.out.println("nfu          : "
                + sum(siteRatios));
        System.out.println();

        // ============================================================
        // 4. Independent baseline calculation
        // ============================================================

        System.out.printf(
                "%-10s %-20s %-20s %-16s %-8s%n",
                "T [K]",
                "CEF G [J/mol-fu]",
                "Expected [J/mol-fu]",
                "Abs error",
                "Result");

        System.out.println(
                "--------------------------------------------------------------------------");

        boolean allPass = true;
        double maxAbsError = 0.0;

        for (double T : TEST_TEMPERATURES) {

            /*
             * Actual CEF calculation.
             *
             * This deliberately bypasses the equilibrium solver.
             */
            double gCef =
                    phase.gibbs.evaluate(T, y);

            /*
             * Independent analytical reconstruction of:
             *
             * G(V2ZR,V:ZR;0)
             */
            double gExpected =
                    expectedV2ZrV_Zr(T);

            double error =
                    Math.abs(gCef - gExpected);

            maxAbsError =
                    Math.max(maxAbsError, error);

            boolean pass =
                    error <= ABS_TOL
                    + REL_TOL * Math.max(
                            1.0,
                            Math.abs(gExpected));

            allPass &= pass;

            System.out.printf(
                    "%-10.2f %-20.10f %-20.10f %-16.6e %-8s%n",
                    T,
                    gCef,
                    gExpected,
                    error,
                    pass ? "PASS" : "FAIL");
        }

        System.out.println();
        System.out.printf(
                "Maximum absolute error = %.12e J/mol-fu%n",
                maxAbsError);

        // ============================================================
        // 5. Explain the end-member limit
        // ============================================================

        System.out.println();
        System.out.println("End-member limit:");
        System.out.println(
                "  (V:ZR) means y=[1,0,0,1].");
        System.out.println(
                "  G_id = 0 because y ln(y) -> 0 at y -> 0.");
        System.out.println(
                "  All interaction products containing a zero fraction vanish.");
        System.out.println(
                "  Therefore G_CEF must equal G(V2ZR,V:ZR;0).");

        // ============================================================
        // 6. Final result
        // ============================================================

        if (!allPass) {
            throw new AssertionError(
                    "V2ZR Gibbs-energy baseline FAILED; "
                    + "maximum absolute error = "
                    + maxAbsError
                    + " J/mol-fu");
        }

        System.out.println();
        System.out.println(
                "PASS: V2ZR end-member Gibbs energy reproduces");
        System.out.println(
                "      the independent V:ZR TDB expression.");
    }

    // =================================================================
    // Independent expected value
    // =================================================================

    /**
     * Reconstruct:
     *
     *   G(V2ZR,V:ZR;0)
     *
     * from the explicit GHSERVV/GHSERZR functions in VZR-re2.TDB.
     *
     * No application TDB-function evaluator is used here.
     */
    private static double expectedV2ZrV_Zr(double T) {

        return 2.0 * ghserV(T)
                + ghserZR(T)
                - 12672.959
                + 3.8143614 * T;
    }

    /**
     * GHSERVV from VZR-re2.TDB.
     *
     * Range 1: 298.14 <= T < 790 K
     * Range 2: 790 <= T < 2183 K
     * Range 3: 2183 <= T <= 4000 K
     */
    private static double ghserV(double T) {

        if (T < 790.0) {

            return -7930.43
                    + 133.346053 * T
                    - 24.134 * T * Math.log(T)
                    - 0.003098 * T * T
                    + 1.2175e-7 * T * T * T
                    + 69460.0 / T;
        }

        if (T < 2183.0) {

            return -7967.842
                    + 143.291093 * T
                    - 25.9 * T * Math.log(T)
                    + 6.25e-5 * T * T
                    - 6.8e-7 * T * T * T;
        }

        return -41689.864
                + 321.140783 * T
                - 47.43 * T * Math.log(T)
                + 6.44389e31 * Math.pow(T, -9);
    }

    /**
     * GHSERZR from VZR-re2.TDB.
     *
     * Range 1: 298.14 <= T < 2128 K
     * Range 2: 2128 <= T <= 6000 K
     */
    private static double ghserZR(double T) {

        if (T < 2128.0) {

            return -7827.595
                    + 125.64905 * T
                    - 24.1618 * T * Math.log(T)
                    - 0.00437791 * T * T
                    + 34971.0 / T;
        }

        return -26085.921
                + 262.724183 * T
                - 42.144 * T * Math.log(T)
                - 1.342896e31 * Math.pow(T, -9);
    }

    private static double sum(double[] values) {

        double result = 0.0;

        for (double value : values) {
            result += value;
        }

        return result;
    }

    private static void printHeader() {

        System.out.println(
                "============================================================");

        System.out.println(
                "V2ZR Gibbs-energy baseline test");

        System.out.println(
                "Reference: Cui et al., CALPHAD 53 (2016), Fig. 9");

        System.out.println(
                "Database : " + TDB_PATH);

        System.out.println(
                "Purpose  : independent real-database CEF baseline");

        System.out.println(
                "============================================================");
    }
}
