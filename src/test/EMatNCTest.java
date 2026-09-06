package test;

import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;

import java.util.Arrays;
import java.util.List;

/**
 * Regression test for M2 Step 3: verifies
 *   (a) mA is the true Sundman M_A (unnormalized moles of A per formula
 *       unit), not the normalized mole fraction x, for BCC_A2 and V2ZR;
 *   (b) x = compositionFromInternal(y) is still the normalized mole
 *       fraction, unaffected by the mA change;
 *   (c) the real (non-identity) eMatNC[A][B] = dM_A[A]/dmu_B computed by
 *       CefPhaseModelAdapter.compute() matches a central-difference
 *       approximation, for BCC_A2 and V2ZR.
 *
 * This checks only that mA/eMatNC are internally consistent with the
 * verified Sundman M_A definition -- it does not assert overall
 * multiphase correctness.
 */
public class EMatNCTest {

    static int failures = 0;

    public static void main(String[] args) throws Exception {
        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");

        List<String> elements = Arrays.asList("V", "ZR");

        // BCC_A2 (V,Zr)1(Va)3: y=[yV_sl0, yZr_sl0, yVA_sl1]
        checkPhase(parser, elements, "BCC_A2", 1200.0,
                new double[]{0.8, 0.2, 1.0},
                new double[]{0.8, 0.2},   // expected M = [0.8, 0.2]
                new double[]{0.8, 0.2});  // expected x = [0.8, 0.2]

        // V2ZR (V,Zr)2(V,Zr)1: y=[yV_sl0, yZr_sl0, yV_sl1, yZr_sl1]
        checkPhase(parser, elements, "V2ZR", 1200.0,
                new double[]{0.6, 0.4, 0.6, 0.4},
                new double[]{1.8, 1.2},   // expected M = [1.8, 1.2]
                new double[]{0.6, 0.4});  // expected x = [0.6, 0.4]

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL M2 STEP 3 REGRESSION CHECKS PASSED");
        } else {
            System.out.println(failures + " M2 STEP 3 REGRESSION CHECK(S) FAILED");
        }
    }

    private static void checkPhase(TdbParser parser, List<String> elements,
                                    String phaseName, double T, double[] y,
                                    double[] expectedM, double[] expectedX) throws Exception {
        System.out.println("=== M_A/eMatNC check: " + phaseName + " y=" + Arrays.toString(y) + " ===");

        @SuppressWarnings("unchecked")
        List<PhaseModel> models = (List<PhaseModel>) parser.buildPhaseModels(elements, Arrays.asList(phaseName));
        PhaseModel pm = models.get(0);
        GibbsEnergyModel gm = PhaseModelFactory.toGibbsModel(pm, elements);

        double[] mu0 = {100.0, -50.0}; // arbitrary non-zero base point, away from mu=0 degeneracy
        PhaseEquilData base = gm.compute(T, 101325.0, y, 0, 0, mu0);

        System.out.println("mA (Sundman M_A) = " + Arrays.toString(base.mA));
        System.out.println("x  (mole fraction) = " + Arrays.toString(base.x));
        System.out.println("eMatNC = " + Arrays.deepToString(base.eMatNC));

        // Property (a): mA equals the expected unnormalized M_A.
        assertArrayClose(phaseName + ": mA == expected M_A", base.mA, expectedM, 1e-9);

        // Property (b): x is still the normalized mole fraction (unchanged
        // by the mA/eMatNC rework).
        assertArrayClose(phaseName + ": x == expected mole fraction", base.x, expectedX, 1e-9);

        // Property (c): eMatNC is not the identity placeholder.
        boolean isIdentity = true;
        double[][] eMatNC = base.eMatNC;
        for (int i = 0; i < eMatNC.length && isIdentity; i++) {
            for (int j = 0; j < eMatNC[i].length; j++) {
                double expected = (i == j) ? 1.0 : 0.0;
                if (Math.abs(eMatNC[i][j] - expected) > 1e-9) { isIdentity = false; break; }
            }
        }
        assertTrue(phaseName + ": eMatNC is not the identity placeholder", !isIdentity);

        // Property (d): central-difference check isolating exactly the
        // mu-dependent term of dely, i.e. deltaY(mu)[m] = sum_j eMat[m][j]*muMapped(mu)[j].
        // eMatNC[A][B] should equal d(mA(y+deltaY(mu)))/dmu_B, where mA is
        // now compute()'s unnormalized M_A (obtained here via a second
        // compute() call at the perturbed y, reading back .mA -- mA does
        // not depend on the mu argument itself, only on y).
        double h = 1e-2; // small mu perturbation (J/mol)
        int nc = mu0.length;
        double maxErr = 0.0;
        for (int B = 0; B < nc; B++) {
            double[] muPlus = new double[nc];
            double[] muMinus = new double[nc];
            muPlus[B] = h;
            muMinus[B] = -h;

            double[] deltaYPlus = muOnlyDeltaY(gm, y, muPlus, T);
            double[] deltaYMinus = muOnlyDeltaY(gm, y, muMinus, T);

            double[] yPlus = y.clone();
            double[] yMinus = y.clone();
            for (int m = 0; m < y.length; m++) {
                yPlus[m] += deltaYPlus[m];
                yMinus[m] += deltaYMinus[m];
            }
            double[] mAPlus = gm.compute(T, 101325.0, yPlus, 0, 0, new double[nc]).mA;
            double[] mAMinus = gm.compute(T, 101325.0, yMinus, 0, 0, new double[nc]).mA;

            for (int A = 0; A < nc; A++) {
                double fdDeriv = (mAPlus[A] - mAMinus[A]) / (2 * h);
                double err = Math.abs(fdDeriv - eMatNC[A][B]);
                maxErr = Math.max(maxErr, err);
            }
        }
        System.out.println("max |finite-difference dM_A/dmu - eMatNC| = " + maxErr);
        assertTrue(phaseName + ": eMatNC matches finite-difference dM_A/dmu response (tol 1e-6)",
                maxErr < 1e-6);
    }

    /**
     * Isolates the mu-dependent part of dely: dely(mu) - dely(0). Since
     * dely[m] = cG[m] + cT[m]*dT + cP[m]*dP + sum_j eMat[m][j]*muMapped(mu)[j]
     * and cG/cT/cP/eMat do not depend on mu, subtracting dely(0) exactly
     * cancels the mu-independent terms, leaving sum_j eMat[m][j]*muMapped(mu)[j].
     */
    private static double[] muOnlyDeltaY(GibbsEnergyModel gm, double[] y, double[] mu, double T) {
        PhaseEquilData withMu = gm.compute(T, 101325.0, y, 0, 0, mu);
        PhaseEquilData zeroMu = gm.compute(T, 101325.0, y, 0, 0, new double[mu.length]);
        double[] result = new double[y.length];
        for (int m = 0; m < y.length; m++) {
            result[m] = withMu.dely[m] - zeroMu.dely[m];
        }
        return result;
    }

    private static void assertTrue(String label, boolean cond) {
        if (cond) {
            System.out.println("  PASS: " + label);
        } else {
            System.out.println("  FAIL: " + label);
            failures++;
        }
    }

    private static void assertArrayClose(String label, double[] actual, double[] expected, double tol) {
        boolean ok = actual.length == expected.length;
        if (ok) {
            for (int i = 0; i < actual.length; i++) {
                if (Math.abs(actual[i] - expected[i]) > tol) { ok = false; break; }
            }
        }
        if (ok) {
            System.out.println("  PASS: " + label + " (" + Arrays.toString(actual) + ")");
        } else {
            System.out.println("  FAIL: " + label + " expected=" + Arrays.toString(expected)
                    + " actual=" + Arrays.toString(actual));
            failures++;
        }
    }
}
