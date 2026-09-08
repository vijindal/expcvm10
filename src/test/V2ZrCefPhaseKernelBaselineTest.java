package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory.PhaseModel;
import system.model.cef.CefPhaseModelAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Phase-kernel diagnostic for the CEF V2ZR model.
 *
 * Purpose:
 *   Validate the thermodynamic quantities needed by the Sundman
 *   single-phase solver before replacing/finishing EquilibriumSolver.
 *
 * Case:
 *   Database : data/VZR-re2.TDB
 *   Phase    : V2ZR = (V,ZR)2(V,ZR)
 *   T        : 1500 K
 *   Y        : chosen from the old-solver Baseline 2 result
 *
 * The Y vector is:
 *   [yV(1), yZR(1), yV(2), yZR(2)]
 *
 * For Baseline 2:
 *   y = [0.7364433137061116,
 *        0.26355668629388834,
 *        0.6271133725877588,
 *        0.3728866274122412]
 */
public class V2ZrCefPhaseKernelBaselineTest {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    private static final double T = 1500.0;

    private static final double[] Y = {
            0.7364433137061116,
            0.26355668629388834,
            0.6271133725877588,
            0.3728866274122412
    };

    private static final double NORMALIZATION_TOL = 1.0e-10;

    public static void main(String[] args) throws Exception {

        printHeader();

        // ------------------------------------------------------------
        // 1. Read the real V-Zr database.
        // ------------------------------------------------------------
        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phases = Arrays.asList(PHASE_NAME);

        @SuppressWarnings("unchecked")
        List<PhaseModel> models =
                (List<PhaseModel>) parser.buildPhaseModels(
                        elements, phases);

        if (models.size() != 1) {
            throw new IllegalStateException(
                    "Expected one V2ZR model, got " + models.size());
        }

        PhaseModel pm = models.get(0);

        if (pm.gibbs == null) {
            throw new IllegalStateException(
                    "V2ZR is not backed by CefGibbs.");
        }

        CefPhaseModelAdapter adapter =
                new CefPhaseModelAdapter(
                        pm.gibbs,
                        pm.magnetic,
                        pm.phaseName,
                        new ArrayList<>(elements),
                        pm.constituentNames);

        // ------------------------------------------------------------
        // 2. Evaluate the phase kernel.
        // ------------------------------------------------------------
        double G = adapter.sundmanG(T, Y);
        double[] gy = adapter.sundmanGradient(T, Y);
        double[][] gyy = adapter.sundmanHessian(T, Y);
        double[] M = adapter.sundmanM(Y);
        double[][] dMdY = adapter.sundmanMJacobian();

        // ------------------------------------------------------------
        // 3. Print results.
        // ------------------------------------------------------------
        System.out.println();
        System.out.println("Internal variables Y");
        System.out.println("--------------------");
        for (int i = 0; i < Y.length; i++) {
            System.out.printf("Y[%d] = %.15f%n", i, Y[i]);
        }

        System.out.println();
        System.out.println("Gibbs energy");
        System.out.println("------------");
        System.out.printf("G = %.15f J/mol-fu%n", G);

        System.out.println();
        System.out.println("Gradient dG/dY");
        System.out.println("-------------");
        for (int i = 0; i < gy.length; i++) {
            System.out.printf("dG/dY[%d] = %.15f%n", i, gy[i]);
        }

        System.out.println();
        System.out.println("Hessian d2G/dYdY");
        System.out.println("----------------");
        for (int i = 0; i < gyy.length; i++) {
            for (int j = 0; j < gyy[i].length; j++) {
                System.out.printf(
                        "% .12e%s",
                        gyy[i][j],
                        (j + 1 == gyy[i].length ? "%n" : " "));
            }
        }

        System.out.println();
        System.out.println("Element amounts M_A per formula unit");
        System.out.println("-------------------------------------");
        for (int a = 0; a < M.length; a++) {
            System.out.printf(
                    "M[%s] = %.15f%n",
                    elements.get(a), M[a]);
        }

        System.out.println();
        System.out.println("Jacobian dM_A/dY");
        System.out.println("----------------");
        for (int a = 0; a < dMdY.length; a++) {
            System.out.printf("%s : ", elements.get(a));
            for (int k = 0; k < dMdY[a].length; k++) {
                System.out.printf(
                        "% .6f%s",
                        dMdY[a][k],
                        (k + 1 == dMdY[a].length ? "%n" : " "));
            }
        }

        // ------------------------------------------------------------
        // 4. Sublattice normalization.
        // ------------------------------------------------------------
        int[] offsets = adapter.sundmanOffsets();
        int[] nconst = adapter.sundmanConstituentsPerSublattice();

        double maxNormError = 0.0;

        System.out.println();
        System.out.println("Sublattice normalization");
        System.out.println("-------------------------");

        for (int s = 0; s < offsets.length; s++) {

            double sum = 0.0;

            for (int i = 0; i < nconst[s]; i++) {
                sum += Y[offsets[s] + i];
            }

            double err = Math.abs(sum - 1.0);
            maxNormError = Math.max(maxNormError, err);

            System.out.printf(
                    "sublattice %d: sum = %.15f, error = %.3e%n",
                    s + 1, sum, err);
        }

        // ------------------------------------------------------------
        // 5. Basic numerical checks.
        // ------------------------------------------------------------
        checkFinite("G", G);

        for (int i = 0; i < gy.length; i++) {
            checkFinite("gradient[" + i + "]", gy[i]);
        }

        for (int i = 0; i < gyy.length; i++) {
            for (int j = 0; j < gyy[i].length; j++) {
                checkFinite(
                        "hessian[" + i + "][" + j + "]",
                        gyy[i][j]);
            }
        }

        for (int i = 0; i < M.length; i++) {
            checkFinite("M[" + i + "]", M[i]);
        }

        if (maxNormError > NORMALIZATION_TOL) {
            throw new AssertionError(
                    "Sublattice normalization error = "
                            + maxNormError);
        }

        // The V2ZR model has site ratios [2,1], therefore for the
        // composition represented by Y:
        //
        // M_V  = 2*yV(1) + yV(2)
        // M_Zr = 2*yZr(1) + yZr(2)
        //
        // and M_V + M_Zr = 3 formula-unit sites.
        double totalM = 0.0;
        for (double value : M) {
            totalM += value;
        }

        if (Math.abs(totalM - 3.0) > NORMALIZATION_TOL) {
            throw new AssertionError(
                    "Unexpected total element amount: "
                            + totalM + " (expected 3.0)");
        }

        System.out.println();
        System.out.printf(
                "Maximum normalization error = %.3e%n",
                maxNormError);
        System.out.printf(
                "Total M_A = %.15f (expected 3.0)%n",
                totalM);

        System.out.println();
        System.out.println(
                "PASS: CEF phase-kernel quantities evaluated "
                + "successfully for V2ZR at 1500 K.");
        System.out.println();
        System.out.println(
                "Next step: use these quantities in the "
                + "single-phase Sundman Newton solve.");
    }

    private static void checkFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new AssertionError(
                    "Non-finite " + name + ": " + value);
        }
    }

    private static void printHeader() {
        System.out.println(
                "============================================================");
        System.out.println(
                "V2ZR CEF phase-kernel baseline");
        System.out.println(
                "============================================================");
        System.out.println(
                "Database : " + TDB_PATH);
        System.out.println(
                "Phase    : " + PHASE_NAME);
        System.out.printf("T        : %.2f K%n", T);
        System.out.println(
                "Y        : " + Arrays.toString(Y));
    }
}
