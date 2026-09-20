package calc.equil;

import org.junit.jupiter.api.Test;

import system.database.TdbParser;
import system.database.UnaryGibbsBuilder;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.cef.CefGibbs;
import system.model.cvm.CecTerm;
import system.model.cvm.CvmGibbsModel;
import system.model.cvm.CvmPhaseData;
import system.model.cvm.CvmPhaseSpec;
import system.model.unary.ElementGibbs;
import system.ports.EquilibriumResult;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 6: a {@link CefGibbs} phase (V2ZR) and a {@link CvmGibbsModel}
 * phase (BCC_A2, real V-Zr CECs from Jindal &amp; Lele, CALPHAD 89 (2025)
 * 102825 Table 11) supplied together as explicit candidates to one
 * {@link EquilibriumSolverV2#solve} call, T=1000K, x(V,Zr)=(0.95,0.05).
 * BCC_A2's real two-phase partner here is Laves_C15, not this TDB's
 * V2ZR (see {@code BccCefVsCvmGibbsCompareTest}: no common tangent), so
 * this asserts the physically correct single-phase outcome -- BCC_A2
 * stable, V2ZR present and correctly metastable -- rather than forcing
 * an artificial two-phase split.
 */
class EquilibriumSolverV2CefCvmTwoPhaseEndToEndTest {

    private static final String TDB = "data/VZR-re2.TDB";

    private static final double T = 1000.0;
    private static final double P = 101325.0;

    /** Overall composition x(V,Zr), inside BCC_A2's single-phase region. */
    private static final double[] TARGET = {0.95, 0.05};

    // ------------------------------------------------------------------
    // BCC_A2 (CVM) construction: Step 3-5's generic binary BCC_A2
    // tetrahedron-approximation geometry (verbatim, component-agnostic),
    // with real V-Zr CECs and real GHSERVV/GHSERZR references from
    // data/VZR-re2.TDB.
    // ------------------------------------------------------------------

    private static CvmGibbsModel buildBccModel(TdbParser parser) {
        CvmPhaseData data = buildBccPhaseData();

        // Real V-Zr bcc CECs, Jindal & Lele, CALPHAD 89 (2025) 102825,
        // Table 11: E21VZr=-1120-0.159T (I-neighbor), E22VZr=-746.7-0.106T
        // (II-neighbor), E3VZr=120 (triangle); v4VZr not reported (0).
        List<CecTerm> cecTerms = List.of(
                new CecTerm("v4AB", 0.0, 0.0),
                new CecTerm("v3AB", 120.0, 0.0),
                new CecTerm("v22AB", -746.7, -0.106),
                new CecTerm("v21AB", -1120.0, -0.159));

        ElementGibbs ghserV = UnaryGibbsBuilder.build("V", parser.getUnderlyingTdb());
        ElementGibbs ghserZr = UnaryGibbsBuilder.build("ZR", parser.getUnderlyingTdb());

        CvmPhaseSpec spec = new CvmPhaseSpec(data, cecTerms,
                new ElementGibbs[]{ghserV, ghserZr}, List.of("V", "ZR"));

        return (CvmGibbsModel) PhaseModelFactory.buildCvm(spec);
    }

    /**
     * Verbatim copy of the Step 3-5 binary BCC_A2 tetrahedron-approximation
     * fixture ({@code system.model.cvm.CvmBinaryBccFixture#build()},
     * package-private, reproduced here for the same reason {@link
     * EquilibriumSolverV2CvmSinglePhaseEndToEndTest} reproduces it). This
     * cluster geometry is component-agnostic; only the CEC values and
     * GHSER references above are V-Zr-specific.
     */
    private static CvmPhaseData buildBccPhaseData() {
        int nComp = 2;
        int ncf = 4;
        int tcdis = 5;

        double[] mhdis = {6.0, 12.0, 4.0, 3.0, 1.0};
        double[] kbdis = {1.0, -1.0, 1.0, 1.0, -1.0};

        int[] lc = {1, 1, 1, 1, 1};

        int[][] lcv = {
                {6}, // tetrahedron
                {6}, // triangle
                {3}, // pair 2NN
                {3}, // pair 1NN
                {2}, // point
        };

        double[][] mh = {
                {1.0}, {1.0}, {1.0}, {1.0}, {1.0}
        };

        double[][][] wcv = {
                {{1, 4, 4, 2, 4, 1}},
                {{1, 2, 1, 1, 2, 1}},
                {{1, 2, 1}},
                {{1, 2, 1}},
                {{1, 1}},
        };

        double[][][][] cmat = {
                // t=0 tetrahedron
                {{
                        {1.0, 1.0, 0.0, -2.0, 1.0, 0.0},
                        {-1.0, -0.5, -0.5, 1.0, 0.0, 0.0},
                        {1.0, 0.0, 1.0, -1.0, 0.0, 0.0},
                        {1.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                        {-1.0, 0.5, -0.5, 1.0, 0.0, 0.0},
                        {1.0, -1.0, 0.0, -2.0, 0.0, 1.0},
                }},
                // t=1 triangle
                {{
                        {0.0, 0.5, -0.5, -1.0, 1.0, 0.0},
                        {0.0, -0.5, 0.5, 0.0, 0.0, 0.0},
                        {0.0, 0.5, -0.5, 1.0, 0.0, 0.0},
                        {0.0, -0.5, -0.5, 1.0, 0.0, 0.0},
                        {0.0, 0.5, 0.5, 0.0, 0.0, 0.0},
                        {0.0, -0.5, -0.5, -1.0, 0.0, 1.0},
                }},
                // t=2 pair 2NN
                {{
                        {0.0, 0.0, 0.0, -1.0, 1.0, 0.0},
                        {0.0, 0.0, 0.0, 1.0, 0.0, 0.0},
                        {0.0, 0.0, 0.0, -1.0, 0.0, 1.0},
                }},
                // t=3 pair 1NN
                {{
                        {0.0, 0.0, -1.0, 0.0, 1.0, 0.0},
                        {0.0, 0.0, 1.0, 0.0, 0.0, 0.0},
                        {0.0, 0.0, -1.0, 0.0, 0.0, 1.0},
                }},
                // t=4 point
                {{
                        {0.0, 0.0, 0.0, 0.0, 1.0, 0.0},
                        {0.0, 0.0, 0.0, 0.0, 0.0, 1.0},
                }},
        };

        int uListLen = 6;

        double[][] cfCoeffs = new double[uListLen][uListLen];
        for (int i = 0; i < uListLen; i++) cfCoeffs[i][i] = 1.0;

        String[] u2Names = {"v4AB", "v3AB", "v22AB", "v21AB", "xA", "xB"};
        String[] eNames = {"v4AB", "v3AB", "v22AB", "v21AB"};

        return new CvmPhaseData(
                "BCC_A2", nComp, ncf,
                tcdis, mhdis, kbdis,
                lc, lcv, mh, wcv, cmat,
                uListLen, cfCoeffs,
                u2Names, eNames);
    }

    @Test
    void bccCvmStableWithV2zrCefCandidateInSameSolve() throws Exception {

        TdbParser parser = new TdbParser();
        parser.load(TDB);

        List<GibbsEnergyModel> rawCef =
                parser.buildPhaseModels(Arrays.asList("V", "ZR"), Arrays.asList("V2ZR"));
        assertEquals(1, rawCef.size(), "Expected exactly one V2ZR candidate.");
        CefGibbs v2zr = (CefGibbs) rawCef.get(0);

        CvmGibbsModel bcc = buildBccModel(parser);

        List<GibbsEnergyModel> candidates = List.of(v2zr, bcc);

        // ------------------------------------------------------------
        // Item 5: independent, model-appropriate initialization paths.
        // V2ZR uses its own existing CEF initializer; BCC_A2 (CVM) uses
        // getInitialInternalVars()/isValid() directly, bypassing
        // GridMinimizer.sampleSiteFractions() entirely for both.
        // ------------------------------------------------------------
        double[] yV2zr = v2zr.getInitialInternalVars(new double[]{1.0 - 1.0 / 3.0, 1.0 / 3.0});
        assertTrue(v2zr.isValid(yV2zr), "V2ZR initial constitution must be valid.");

        double[] yBcc = bcc.getInitialInternalVars(TARGET);
        assertTrue(bcc.isValid(yBcc), "BCC_A2 (CVM) initial constitution must be valid.");

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();

        // Relaxed tolerance, same rationale as the single-CVM-phase test
        // (CVM's Hessian conditioning is far worse-scaled than CEF's);
        // loosened further here since a mixed candidate list's
        // metastable-phase driving-force evaluation adds its own
        // residual noise on top of that per-model conditioning floor.
        solver.setTolerance(1.0e-6);

        // Single stable slot: BCC_A2 (CVM) alone, at TARGET's own
        // composition (a genuine single-phase point per the real V-Zr
        // phase diagram -- see the class javadoc). V2ZR is supplied only
        // as a metastable candidate, exactly as candidates work for any
        // other EquilibriumSolverV2 test.
        solver.setInitialStateForTest(
                new int[]{1},
                new double[][]{yBcc},
                new double[]{1.0});

        EquilibriumResult result = solver.solve(T, P, TARGET, candidates);

        // ------------------------------------------------------------
        // Both model types accepted; solver converged.
        // ------------------------------------------------------------
        assertNotNull(result);
        assertTrue(result.isConverged(),
                "BCC_A2(CVM)-stable solve with a CEF candidate present should converge.");

        List<EquilibriumResult.PhaseResult> stableResults = result.getStablePhases();
        assertEquals(1, stableResults.size(),
                "BCC_A2 alone is the physically correct stable set at this composition.");

        EquilibriumResult.PhaseResult bccResult = stableResults.get(0);
        assertEquals("BCC_A2", bccResult.phaseName);
        assertEquals("CVM", bccResult.modelType);

        // ------------------------------------------------------------
        // Mass balance: the single stable phase's composition must
        // equal the overall target.
        // ------------------------------------------------------------
        assertEquals(TARGET[0], bccResult.x[0], 1.0e-6);
        assertEquals(TARGET[1], bccResult.x[1], 1.0e-6);
        for (int a = 0; a < TARGET.length; a++) {
            assertEquals(TARGET[a], bccResult.amount * bccResult.x[a], 1.0e-6);
        }

        // ------------------------------------------------------------
        // Chemical potentials finite.
        // ------------------------------------------------------------
        double[] mu = result.getMu();
        assertNotNull(mu);
        assertEquals(TARGET.length, mu.length);
        for (double m : mu) {
            assertTrue(Double.isFinite(m),
                    "Chemical potentials must be finite: " + Arrays.toString(mu));
        }

        assertTrue(Double.isFinite(bccResult.G), "BCC_A2 converged G must be finite.");

        // ------------------------------------------------------------
        // Item 8: direct model cross-check at the converged BCC_A2 state.
        // ------------------------------------------------------------
        double[] yCvmConverged = bccResult.y;
        assertNotNull(yCvmConverged);
        assertTrue(bcc.isValid(yCvmConverged), "Converged BCC_A2 constitution must remain valid.");

        double gCvmDirect = bcc.G(T, P, yCvmConverged);
        assertEquals(gCvmDirect, bccResult.G,
                1.0e-6 * Math.max(1.0, Math.abs(gCvmDirect)),
                "Directly evaluated BCC_A2 G must agree with the solver's stored G.");

        double[] xCvmDirect = bcc.compositionFromInternal(yCvmConverged);
        assertEquals(xCvmDirect[0], bccResult.x[0], 1.0e-9);
        assertEquals(xCvmDirect[1], bccResult.x[1], 1.0e-9);

        // CvmGibbsModel must still agree with the underlying CvmGibbs
        // evaluator directly (not just via the model-layer G()).
        double gCvmUnderlying = bcc.underlyingGibbs()
                .evaluate(yCvmConverged, T, bcc.cecEvaluator().evaluate(T));
        assertEquals(gCvmUnderlying, gCvmDirect, 1.0e-9 * Math.max(1.0, Math.abs(gCvmDirect)),
                "CvmGibbsModel.G() must agree with the underlying CvmGibbs evaluator.");

        // ------------------------------------------------------------
        // V2ZR (the CEF candidate) must have been genuinely evaluated
        // as metastable, with a non-positive driving force at this mu
        // (i.e. it is correctly rejected, not merely absent) -- this is
        // what proves the CEF candidate was actually exercised alongside
        // the CVM one, not just present in the list unused.
        // ------------------------------------------------------------
        List<EquilibriumResult.PhaseResult> metastable = result.getMetastablePhases();
        EquilibriumResult.PhaseResult v2zrMeta = null;
        for (EquilibriumResult.PhaseResult pr : metastable) {
            if ("V2ZR".equals(pr.phaseName)) {
                v2zrMeta = pr;
            }
        }
        assertNotNull(v2zrMeta, "V2ZR must appear among the metastable phases.");
        assertTrue(v2zrMeta.drivingForce <= 1.0e-6,
                "V2ZR's driving force must be non-positive (correctly metastable): "
                + v2zrMeta.drivingForce);
        assertTrue(Double.isFinite(v2zrMeta.G), "V2ZR's evaluated G must be finite.");
    }
}
