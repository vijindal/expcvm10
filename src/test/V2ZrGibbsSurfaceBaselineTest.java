package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;

import java.util.Arrays;
import java.util.List;

/**
 * Baseline 1: full CEF Gibbs-energy surface of V2Zr.
 *
 * Reference:
 * J. Cui et al., "Thermodynamic modeling of the V-Zr system supported
 * by key experiments", CALPHAD 53 (2016) 122-129.
 *
 * V2Zr is modeled as:
 *
 *        (V,Zr)2(V,Zr)
 *
 * At fixed T, this test varies the two independent site fractions:
 *
 *        yZr,1 in [0,1]
 *        yZr,2 in [0,1]
 *
 * with
 *
 *        yV,1 = 1-yZr,1
 *        yV,2 = 1-yZr,2
 *
 * and evaluates the complete CEF Gibbs energy:
 *
 *        G = G_ref + G_id + G_ex
 *
 * This test is deliberately NOT an equilibrium calculation.
 * It is a direct test of the CEF Gibbs-energy surface generated from
 * the real VZR-re2.TDB database.
 *
 * Units: J/mol formula unit.
 */
public class V2ZrGibbsSurfaceBaselineTest {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    private static final double T = 1500.0;

    /*
     * A modest grid is used for the first baseline. Increase later
     * if a graphical surface/map is required.
     */
    private static final int GRID_POINTS = 21;

    /*
     * Site fractions at the boundaries generate logarithmic derivative
     * singularities, but Gibbs-energy evaluation itself is finite because
     *
     *        lim(y ln y) = 0.
     *
     * Therefore this test includes the boundaries deliberately.
     */
    private static final double FINITE_TOL = 0.0;

    public static void main(String[] args) throws Exception {

        printHeader();

        // ============================================================
        // 1. Load the real database and construct V2ZR
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
                    "Expected exactly one " + PHASE_NAME +
                    " model, obtained " + models.size());
        }

        PhaseModel phase = models.get(0);

        if (phase.gibbs == null) {
            throw new IllegalStateException(
                    PHASE_NAME + " was not constructed as a CEF model.");
        }

        // ============================================================
        // 2. Verify model structure
        // ============================================================

        double[] a = phase.gibbs.stoichiometry();
        int[] nc = phase.gibbs.constituentsPerSublattice();
        int[] offset = phase.gibbs.offsets();

        if (a.length != 2
                || nc.length != 2
                || offset.length != 2
                || Math.abs(a[0] - 2.0) > 1.0e-12
                || Math.abs(a[1] - 1.0) > 1.0e-12
                || nc[0] != 2
                || nc[1] != 2) {

            throw new AssertionError(
                    "Unexpected V2ZR CEF structure: "
                    + "a=" + Arrays.toString(a)
                    + ", nc=" + Arrays.toString(nc)
                    + ", offset=" + Arrays.toString(offset));
        }

        System.out.println("Phase        : " + phase.phaseName);
        System.out.println("Model        : (V,ZR)2(V,ZR)");
        System.out.println("Temperature  : " + T + " K");
        System.out.println("Site ratios  : " + Arrays.toString(a));
        System.out.println("Constituents : " + Arrays.toString(nc));
        System.out.println();

        // ============================================================
        // 3. Evaluate the complete CEF surface
        // ============================================================

        double minG = Double.POSITIVE_INFINITY;
        double maxG = Double.NEGATIVE_INFINITY;

        double minY1 = Double.NaN;
        double minY2 = Double.NaN;

        double maxY1 = Double.NaN;
        double maxY2 = Double.NaN;

        int nonFiniteCount = 0;

        double stoichG = Double.NaN;
        double stoichErrorCheck = Double.NaN;

        System.out.printf(
                "%-10s %-10s %-20s %-12s %-12s%n",
                "yZr(1)",
                "yZr(2)",
                "G [J/mol-fu]",
                "G [kJ/mol-fu]",
                "Status");

        System.out.println(
                "----------------------------------------------------------------");

        for (int i = 0; i < GRID_POINTS; i++) {

            double yZr1 =
                    (double) i / (GRID_POINTS - 1);

            double yV1 =
                    1.0 - yZr1;

            for (int j = 0; j < GRID_POINTS; j++) {

                double yZr2 =
                        (double) j / (GRID_POINTS - 1);

                double yV2 =
                        1.0 - yZr2;

                /*
                 * CEF ordering is:
                 *
                 *   sublattice 1: [V, ZR]
                 *   sublattice 2: [V, ZR]
                 */
                double[] y = {
                    yV1, yZr1,
                    yV2, yZr2
                };

                double g =
                        phase.gibbs.evaluate(T, y);

                boolean finite =
                        Double.isFinite(g);

                if (!finite) {
                    nonFiniteCount++;
                }

                if (finite) {

                    if (g < minG) {
                        minG = g;
                        minY1 = yZr1;
                        minY2 = yZr2;
                    }

                    if (g > maxG) {
                        maxG = g;
                        maxY1 = yZr1;
                        maxY2 = yZr2;
                    }
                }

                /*
                 * Print the diagonal and stoichiometric point so the
                 * first run remains compact but informative.
                 */
                if (i == j || (i == 0 && j == GRID_POINTS - 1)
                        || (i == GRID_POINTS - 1 && j == 0)) {

                    System.out.printf(
                            "%-10.4f %-10.4f %-20.10f %-12.8f %-12s%n",
                            yZr1,
                            yZr2,
                            g,
                            g / 1000.0,
                            finite ? "PASS" : "FAIL");
                }

                /*
                 * Stoichiometric V2Zr is V:ZR:
                 *
                 *     y = [1,0,0,1]
                 *
                 * corresponding to yZr1=0, yZr2=1.
                 */
                if (Math.abs(yZr1) < 1.0e-15
                        && Math.abs(yZr2 - 1.0) < 1.0e-15) {

                    stoichG = g;
                }
            }
        }

        // ============================================================
        // 4. Basic surface checks
        // ============================================================

        System.out.println();
        System.out.println("Surface summary");
        System.out.println("----------------");

        System.out.printf(
                "Minimum G = %.10f J/mol-fu at "
                + "yZr(1)=%.6f, yZr(2)=%.6f%n",
                minG, minY1, minY2);

        System.out.printf(
                "Maximum G = %.10f J/mol-fu at "
                + "yZr(1)=%.6f, yZr(2)=%.6f%n",
                maxG, maxY1, maxY2);

        System.out.println(
                "Non-finite Gibbs energies = " + nonFiniteCount);

        if (nonFiniteCount > 0) {
            throw new AssertionError(
                    "CEF Gibbs surface contains non-finite values.");
        }

        // ============================================================
        // 5. Stoichiometric point
        // ============================================================

        /*
         * Evaluate directly so that the exact baseline point is
         * independent of the grid indexing.
         */
        double[] yStoich = {
            1.0, 0.0,
            0.0, 1.0
        };

        double directStoichG =
                phase.gibbs.evaluate(T, yStoich);

        /*
         * Baseline 0 has already established the exact end-member
         * temperature dependence. Here we only check consistency
         * between the grid and direct evaluation.
         */
        if (!Double.isFinite(directStoichG)) {
            throw new AssertionError(
                    "Non-finite stoichiometric V2Zr Gibbs energy.");
        }

        if (Math.abs(directStoichG - stoichG) > 1.0e-10) {
            throw new AssertionError(
                    "Stoichiometric point differs between direct and "
                    + "grid evaluation: direct=" + directStoichG
                    + ", grid=" + stoichG);
        }

        System.out.printf(
                "Stoichiometric V:ZR G = %.10f J/mol-fu%n",
                directStoichG);

        // ============================================================
        // 6. Sublattice-normalization checks
        // ============================================================

        /*
         * Since the grid is generated from yV=1-yZr, every point must
         * satisfy the CEF normalization constraints exactly.
         */
        double maxNormalizationError = 0.0;

        for (int i = 0; i < GRID_POINTS; i++) {

            double yZr1 =
                    (double) i / (GRID_POINTS - 1);

            double y1Sum =
                    (1.0 - yZr1) + yZr1;

            double err1 =
                    Math.abs(y1Sum - 1.0);

            maxNormalizationError =
                    Math.max(maxNormalizationError, err1);

            for (int j = 0; j < GRID_POINTS; j++) {

                double yZr2 =
                        (double) j / (GRID_POINTS - 1);

                double y2Sum =
                        (1.0 - yZr2) + yZr2;

                double err2 =
                        Math.abs(y2Sum - 1.0);

                maxNormalizationError =
                        Math.max(maxNormalizationError, err2);
            }
        }

        System.out.printf(
                "Maximum sublattice normalization error = %.3e%n",
                maxNormalizationError);

        if (maxNormalizationError > 1.0e-14) {
            throw new AssertionError(
                    "CEF site-fraction normalization failed.");
        }

        // ============================================================
        // 7. Final result
        // ============================================================

        System.out.println();

        if (minG == Double.POSITIVE_INFINITY
                || maxG == Double.NEGATIVE_INFINITY) {

            throw new AssertionError(
                    "No finite values were obtained from the CEF surface.");
        }

        System.out.println(
                "PASS: Complete V2Zr CEF Gibbs-energy surface was "
                + "evaluated successfully at 1500 K.");

        System.out.println();
        System.out.println(
                "NOTE:");
        System.out.println(
                "  This test verifies the CEF energy surface only.");
        System.out.println(
                "  It does NOT determine the equilibrium constitution.");
        System.out.println(
                "  Equilibrium will be tested later by EquilibriumSolver.");
    }

    private static void printHeader() {

        System.out.println(
                "============================================================");

        System.out.println(
                "V2ZR CEF Gibbs-energy surface baseline");

        System.out.println(
                "Reference: Cui et al., CALPHAD 53 (2016), Fig. 9");

        System.out.println(
                "Database : " + TDB_PATH);

        System.out.println(
                "============================================================");
    }
}
