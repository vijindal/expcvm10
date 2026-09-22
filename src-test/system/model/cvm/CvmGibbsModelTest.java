package system.model.cvm;

import org.junit.jupiter.api.Test;
import system.model.unary.ElementGibbs;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CvmGibbsModel}, the first real {@link
 * system.model.GibbsEnergyModel} implementation for a disordered binary
 * BCC_A2 CVM phase.
 *
 * <p>All fixtures use {@link CvmBinaryBccFixture} (CEWorkbench's own,
 * validated binary BCC_A2 tetrahedron-approximation cluster algebra) so
 * every assertion is checked against a real, self-consistent cluster
 * algebra rather than a hand-derived approximation.
 */
class CvmGibbsModelTest {

    private static CvmGibbsModel newModel(List<CecTerm> cecTerms) {
        CvmPhaseData data = CvmBinaryBccFixture.build();
        ElementGibbs ghserA = new FakeElementGibbs("A", 1000.0, -5.0);
        ElementGibbs ghserB = new FakeElementGibbs("B", -2000.0, 3.0);
        return new CvmGibbsModel(data, cecTerms,
                new ElementGibbs[]{ghserA, ghserB}, List.of("A", "B"));
    }

    /** The CEC set used by the CEWorkbench cross-check dump. */
    private static List<CecTerm> crossCheckCecTerms() {
        return List.of(
                new CecTerm("v4AB", 100.0, 0.0),
                new CecTerm("v3AB", -50.0, 0.01),
                new CecTerm("v22AB", 200.0, -0.02),
                new CecTerm("v21AB", -300.0, 0.05));
    }

    // ══════════════════════════════════════════════════════════════════
    // Test A -- dimensions and composition
    // ══════════════════════════════════════════════════════════════════

    @Test
    void dimensionsAndCompositionAreConsistent() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());

        assertEquals(2, model.numComponents());
        assertEquals(4 + 2, model.numSiteVars());
        assertEquals(6, model.numSiteVars());
        assertEquals(1, model.numSublattices());
        assertArrayEquals(new int[]{4}, model.offsets());
        assertArrayEquals(new int[]{2}, model.constituentsPerSublattice());

        double[] y = {0.01, -0.02, 0.03, -0.04, 0.6, 0.4};
        double[] x = model.compositionFromInternal(y);
        assertArrayEquals(new double[]{0.6, 0.4}, x, 1e-12);

        double[] moles = model.moles(y);
        assertArrayEquals(x, moles, 1e-12);

        double[][] dM = model.dMoles_dy();
        assertEquals(2, dM.length);
        assertEquals(6, dM[0].length);
        double[][] expected = {
                {0, 0, 0, 0, 1, 0},
                {0, 0, 0, 0, 0, 1},
        };
        for (int i = 0; i < 2; i++) {
            assertArrayEquals(expected[i], dM[i], 1e-12);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Test B -- random/disordered state validity
    // ══════════════════════════════════════════════════════════════════

    @Test
    void randomDisorderedStateIsValid() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());

        double[] x = {0.5, 0.5};
        double[] y = model.getInitialInternalVars(x);

        assertEquals(6, y.length);
        assertTrue(model.isValid(y), "random-state y must be valid: " + Arrays.toString(y));

        double xSum = y[4] + y[5];
        assertEquals(1.0, xSum, 1e-12);

        // At xA=xB=0.5 every non-point CVCF should vanish except v4AB/v2's
        // random-state values (matches evalRandApprox / CEWorkbench dump).
        assertEquals(0.0625, y[0], 1e-12); // v4AB = xA^2*xB^2
        assertEquals(0.0, y[1], 1e-12);    // v3AB
        assertEquals(0.25, y[2], 1e-12);   // v22AB = xA*xB
        assertEquals(0.25, y[3], 1e-12);   // v21AB = xA*xB

        // Cluster probabilities derived from cmat.u must be physically valid.
        double[] u = model.phaseData().computeU(y);
        CvmPhaseData data = model.phaseData();
        for (int itc = 0; itc < data.tcdis; itc++) {
            for (int inc = 0; inc < data.lc[itc]; inc++) {
                double sum = 0.0;
                for (int icv = 0; icv < data.lcv[itc][inc]; icv++) {
                    double cv = dot(data.cmat[itc][inc][icv], u);
                    assertTrue(cv >= -1e-9 && cv <= 1.0 + 1e-9,
                            "cv out of range at t=" + itc + " v=" + icv + ": " + cv);
                    sum += data.wcv[itc][inc][icv] * cv;
                }
                assertEquals(1.0, sum, 1e-9, "wcv-weighted sum must equal 1 at t=" + itc);
            }
        }
    }

    @Test
    void skewedCompositionAlsoProducesValidRandomState() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());
        double[] x = {0.7, 0.3};
        double[] y = model.getInitialInternalVars(x);
        assertTrue(model.isValid(y));
        assertEquals(0.7, y[4], 1e-12);
        assertEquals(0.3, y[5], 1e-12);
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    // ══════════════════════════════════════════════════════════════════
    // Test C -- free-energy cross-check against the underlying CvmGibbs
    // ══════════════════════════════════════════════════════════════════

    @Test
    void adapterMatchesUnderlyingCvmGibbsExactly() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());
        CvmGibbs gibbs = model.underlyingGibbs();

        double T = 1000.0;
        double P = 101325.0;
        double[] y = {0.6 * 0.0576, 0.8 * -0.048, 0.8 * 0.24, 0.8 * 0.24, 0.6, 0.4};
        // (arbitrary valid-looking perturbed state; validity checked below)
        y = new double[]{0.0576 * 0.8, -0.048 * 0.8, 0.24 * 0.8, 0.24 * 0.8, 0.6, 0.4};

        assertTrue(model.isValid(y));

        double[] cec = model.cecEvaluator().evaluate(T);

        double expectedG = gibbs.evaluate(y, T, cec);
        double actualG = model.G(T, P, y);
        assertEquals(expectedG, actualG, 1e-9);

        double[] expectedGrad = gibbs.gradient(y, T, cec);
        double[] actualGrad = model.dG_dy(T, P, y);
        assertArrayEquals(expectedGrad, actualGrad, 1e-6);

        double[][] expectedHess = gibbs.hessian(y, T);
        double[][] actualHess = model.d2G_dy2(T, P, y);
        for (int i = 0; i < expectedHess.length; i++) {
            assertArrayEquals(expectedHess[i], actualHess[i], 1e-6);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Test D -- CEWorkbench cross-check
    // ══════════════════════════════════════════════════════════════════

    /**
     * Cross-checks H, S and Gm against CEWorkbench's {@code CVMGibbsModel}
     * at the identical (T, x, u) state, using the exact same cluster
     * algebra ({@link CvmBinaryBccFixture}, taken verbatim from
     * CEWorkbench's own {@code CvmGeometry.build}) and the same CEC set.
     * The ordering/mapping ambiguity flagged in the task (which of
     * {@code v2AB1}/{@code v2AB2} is 1NN vs 2NN) is resolved here by using
     * CEWorkbench's own explicit shell-named columns
     * ({@code v21AB}=1NN, {@code v22AB}=2NN) directly, rather than guessing.
     *
     * <p>Reference values captured from a one-off run of CEWorkbench's
     * {@code CVMGibbsModel.State} at T=1000K, x=(0.6,0.4), u = 0.8 *
     * randomStateU(x), with the CEC set in {@link #crossCheckCecTerms()}:
     * <pre>
     *   hm = -7.2960000000
     *   sm =  4.4737509394   (J/mol/K)
     *   gm = -4481.0469393723
     *   gmu = [117803.6732225158, -20392.513373516176,
     *          52632.485447964944, -116886.94110016826]
     * </pre>
     */
    @Test
    void crossChecksAgainstCEWorkbenchMixingEnergy() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());
        CvmGibbs gibbs = model.underlyingGibbs();

        double T = 1000.0;
        double[] x = {0.6, 0.4};
        double[] uRandom = model.phaseData().evalRandApprox(x); // [v4,v3,v22,v21,xA,xB]
        double[] y = new double[6];
        for (int i = 0; i < 4; i++) y[i] = uRandom[i] * 0.8;
        y[4] = x[0];
        y[5] = x[1];

        double[] cec = model.cecEvaluator().evaluate(T);

        double Hm = gibbs.H(y, cec);
        double Sm = gibbs.S(y);
        double Gm = Hm - T * Sm;

        assertEquals(-7.2960000000, Hm, 1e-6);
        assertEquals(4.4737509394, Sm, 1e-6);
        assertEquals(-4481.0469393723, Gm, 1e-4);

        double[] gradS = gibbs.gradientS(y);
        double[] gmu = new double[4];
        for (int i = 0; i < 4; i++) gmu[i] = cec[i] - T * gradS[i];
        double[] expectedGmu = {117803.6732225158, -20392.513373516176,
                52632.485447964944, -116886.94110016826};
        assertArrayEquals(expectedGmu, gmu, 1e-3);
    }

    // ══════════════════════════════════════════════════════════════════
    // Test E -- derivatives vs finite differences
    // ══════════════════════════════════════════════════════════════════

    @Test
    void dG_dyMatchesFiniteDifference() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());
        double T = 900.0, P = 101325.0;
        double[] y = interiorState();

        double[] analytical = model.dG_dy(T, P, y);
        double h = 1e-6;
        for (int m = 0; m < y.length; m++) {
            double[] yp = y.clone(); yp[m] += h;
            double[] ym = y.clone(); ym[m] -= h;
            double fd = (model.G(T, P, yp) - model.G(T, P, ym)) / (2 * h);
            assertEquals(fd, analytical[m], 1e-3 * Math.max(1.0, Math.abs(fd)),
                    "dG_dy mismatch at index " + m);
        }
    }

    @Test
    void d2G_dy2MatchesFiniteDifference() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());
        double T = 900.0, P = 101325.0;
        double[] y = interiorState();

        double[][] analytical = model.d2G_dy2(T, P, y);
        double h = 1e-5;
        int n = y.length;
        for (int m = 0; m < n; m++) {
            double[] yp = y.clone(); yp[m] += h;
            double[] ym = y.clone(); ym[m] -= h;
            double[] gp = model.dG_dy(T, P, yp);
            double[] gm = model.dG_dy(T, P, ym);
            for (int k = 0; k < n; k++) {
                double fd = (gp[k] - gm[k]) / (2 * h);
                assertEquals(fd, analytical[m][k], 1e-2 * Math.max(1.0, Math.abs(fd)),
                        "d2G_dy2 mismatch at (" + m + "," + k + ")");
            }
        }
    }

    @Test
    void dG_dTMatchesFiniteDifference() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());
        double T = 900.0, P = 101325.0;
        double[] y = interiorState();

        double analytical = model.dG_dT(T, P, y);
        double h = 1e-3;
        double fd = (model.G(T + h, P, y) - model.G(T - h, P, y)) / (2 * h);
        assertEquals(fd, analytical, 1e-4 * Math.max(1.0, Math.abs(fd)));
    }

    @Test
    void d2G_dydTMatchesFiniteDifference() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());
        double T = 900.0, P = 101325.0;
        double[] y = interiorState();

        double[] analytical = model.d2G_dydT(T, P, y);
        double h = 1e-3;
        double[] gp = model.dG_dy(T + h, P, y);
        double[] gm = model.dG_dy(T - h, P, y);
        for (int k = 0; k < y.length; k++) {
            double fd = (gp[k] - gm[k]) / (2 * h);
            assertEquals(fd, analytical[k], 1e-3 * Math.max(1.0, Math.abs(fd)),
                    "d2G_dydT mismatch at index " + k);
        }
    }

    /** An interior (non-boundary), valid y for finite-difference checks. */
    private static double[] interiorState() {
        CvmPhaseData data = CvmBinaryBccFixture.build();
        double[] x = {0.55, 0.45};
        double[] uRandom = data.evalRandApprox(x);
        double[] y = new double[6];
        for (int i = 0; i < 4; i++) y[i] = uRandom[i] * 0.9;
        y[4] = x[0];
        y[5] = x[1];
        return y;
    }

    // ══════════════════════════════════════════════════════════════════
    // Test F -- pressure contract
    // ══════════════════════════════════════════════════════════════════

    @Test
    void pressureContributionIsIdenticallyZero() {
        CvmGibbsModel model = newModel(crossCheckCecTerms());
        double T = 900.0, P = 101325.0;
        double[] y = interiorState();

        assertEquals(0.0, model.dG_dP(T, P, y));

        double[] d2GdydP = model.d2G_dydP(T, P, y);
        assertEquals(6, d2GdydP.length);
        for (double v : d2GdydP) {
            assertEquals(0.0, v);
        }

        // Also independent of P value itself.
        assertEquals(model.G(T, P, y), model.G(T, 2 * P, y), 1e-9);
    }

    // ══════════════════════════════════════════════════════════════════
    // CEC coverage / evaluator error handling
    // ══════════════════════════════════════════════════════════════════

    @Test
    void missingCecTermIsRejected() {
        CvmPhaseData data = CvmBinaryBccFixture.build();
        List<CecTerm> incomplete = List.of(
                new CecTerm("v4AB", 1.0, 0.0),
                new CecTerm("v3AB", 1.0, 0.0),
                new CecTerm("v22AB", 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CecEvaluator(data, incomplete));
    }

    @Test
    void unmatchedCecTermIsRejected() {
        CvmPhaseData data = CvmBinaryBccFixture.build();
        List<CecTerm> extra = List.of(
                new CecTerm("v4AB", 1.0, 0.0),
                new CecTerm("v3AB", 1.0, 0.0),
                new CecTerm("v22AB", 1.0, 0.0),
                new CecTerm("v21AB", 1.0, 0.0),
                new CecTerm("bogus", 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new CecEvaluator(data, extra));
    }
}
