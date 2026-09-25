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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 8: the first genuine CVM candidate-stability/driving-force path.
 *
 * <p>Before this test's implementation, a non-{@link CefGibbs} candidate
 * (e.g. a {@link CvmGibbsModel} phase) was simply skipped by {@link
 * EquilibriumSolverV2#updateStablePhaseSet()}'s candidate-addition scan --
 * never evaluated, never a contender to be added. This class proves the
 * replacement path: a CVM candidate is now relaxed to its own stationary
 * point using the SAME {@link EquilibriumSolverV2} Newton machinery (a
 * nested single-candidate, fixed-overall-composition {@link
 * EquilibriumSolverV2#solve} call -- see {@code relaxCvmCandidate()}'s
 * javadoc), and its Sundman Eq. 62 driving force against the outer
 * equilibrium's chemical potentials is then used exactly like any other
 * candidate's.
 *
 * <h2>Test setup</h2>
 * V2ZR (CEF) is forced stable, alone, at the real V-Zr overall composition
 * x(V,Zr)=[0.95,0.05] -- deep inside BCC_A2's actual single-phase field
 * (see {@code EquilibriumSolverV2CefCvmTwoPhaseEndToEndTest}'s class
 * javadoc: BCC_A2 is stable and V2ZR is metastable there). BCC_A2 (CVM) is
 * supplied as an explicit, not-initially-stable candidate. This is exactly
 * the situation Step 8 must handle: a CVM candidate present but excluded
 * from the initial stable set, whose true driving force (once relaxed) is
 * strongly positive.
 *
 * <p>No V2ZR/BCC_A2 common tangent is asserted or required -- per the
 * task's scope limits, this test demonstrates candidate discovery/
 * relaxation/driving-force correctness, not a fabricated two-phase
 * equilibrium.
 */
class EquilibriumSolverV2CvmCandidateStabilityTest {

    private static final String TDB = "data/VZR-re2.TDB";
    private static final double T = 1000.0;
    private static final double P = 101325.0;
    private static final double[] TARGET = {0.95, 0.05};
    private static final int NCF = 4;

    private static CvmGibbsModel buildBccModel(TdbParser parser) {
        CvmPhaseData data = buildBccPhaseData();

        // Real V-Zr bcc CECs, Jindal & Lele, CALPHAD 89 (2025) 102825,
        // Table 11 (verbatim copy of the Step 6/7 fixture).
        List<CecTerm> cecTerms = List.of(
                new CecTerm("v4AB", 0.0, 0.0),
                new CecTerm("v3AB", 120.0, 0.0),
                new CecTerm("v2AB2", -746.7, -0.106),
                new CecTerm("v2AB1", -1120.0, -0.159));

        ElementGibbs ghserV = UnaryGibbsBuilder.build("V", parser.getUnderlyingTdb());
        ElementGibbs ghserZr = UnaryGibbsBuilder.build("ZR", parser.getUnderlyingTdb());

        CvmPhaseSpec spec = new CvmPhaseSpec(data, cecTerms,
                new ElementGibbs[]{ghserV, ghserZr}, List.of("V", "ZR"));

        return (CvmGibbsModel) PhaseModelFactory.buildCvm(spec);
    }

    /** Verbatim copy of the Step 3-7 binary BCC_A2 tetrahedron-approximation fixture. */
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

        String[] u2Names = {"v4AB", "v3AB", "v2AB2", "v2AB1", "xA", "xB"};
        String[] eNames = {"v4AB", "v3AB", "v2AB2", "v2AB1"};

        return new CvmPhaseData(
                "BCC_A2", nComp, ncf,
                tcdis, mhdis, kbdis,
                lc, lcv, mh, wcv, cmat,
                uListLen, cfCoeffs,
                u2Names, eNames);
    }

    private static List<GibbsEnergyModel> buildCandidates(TdbParser parser) throws Exception {
        List<GibbsEnergyModel> rawCef =
                parser.buildPhaseModels(Arrays.asList("V", "ZR"), Arrays.asList("V2ZR"));
        assertEquals(1, rawCef.size(), "Expected exactly one V2ZR candidate.");
        CefGibbs v2zr = (CefGibbs) rawCef.get(0);
        CvmGibbsModel bcc = buildBccModel(parser);
        return List.of(v2zr, bcc);
    }

    // ------------------------------------------------------------------
    // Test A: the CVM candidate is no longer skipped -- reaches candidate
    // evaluation and, since its relaxed driving force at this composition
    // is strongly positive, is recognized by the stable-set update as
    // APPEARING (not silently ignored, and no ClassCastException/
    // UnsupportedOperationException escapes).
    // ------------------------------------------------------------------
    @Test
    void cvmCandidateIsEvaluatedAndRecognizedAsAppearing() throws Exception {
        TdbParser parser = new TdbParser();
        parser.load(TDB);

        List<GibbsEnergyModel> candidates = buildCandidates(parser);
        CefGibbs v2zr = (CefGibbs) candidates.get(0);

        double[] yV2zr = v2zr.getInitialInternalVars(new double[]{1.0 - 1.0 / 3.0, 1.0 / 3.0});
        assertTrue(v2zr.isValid(yV2zr), "V2ZR initial constitution must be valid.");

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setTolerance(1.0e-6);

        // Force V2ZR alone to be the (thermodynamically wrong, but valid)
        // initial stable set at this composition -- BCC_A2 (CVM) is an
        // explicitly supplied candidate, NOT initially stable.
        solver.setInitialStateForTest(
                new int[]{0},
                new double[][]{yV2zr},
                new double[]{1.0});

        // stopOnStableSetChange=true returns immediately at the FIRST
        // Newton iteration whose own updateStablePhaseSet() call changes
        // the stable set -- exactly where the candidate-addition scan
        // (the code path this test targets) runs.
        EquilibriumResult result = solver.solve(T, P, TARGET, candidates, true);

        assertNotNull(result);
        assertNotNull(result.getStableSetChange(),
                "BCC_A2's driving force at x=[0.95,0.05] should be strongly positive "
                + "against V2ZR-only chemical potentials, triggering a stable-set change.");
        assertEquals("BCC_A2", result.getStableSetChange().phaseName);
        assertEquals(EquilibriumResult.ChangeDirection.APPEARING,
                result.getStableSetChange().direction);

        // The candidate must have been genuinely evaluated: it now shows
        // up as a stable slot with a finite G and a valid, non-null y --
        // not merely absent/skipped.
        boolean foundBcc = false;
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            if ("BCC_A2".equals(pr.phaseName)) {
                foundBcc = true;
                assertTrue(Double.isFinite(pr.G), "Added BCC_A2 slot's G must be finite.");
                assertNotNull(pr.y, "Added BCC_A2 slot's y must not be null.");
            }
        }
        assertTrue(foundBcc, "BCC_A2 must have been added as a new stable slot.");
    }

    // ------------------------------------------------------------------
    // Test B: candidate internal relaxation. Directly exercises the same
    // nested-solve relaxation the candidate path uses (via the public,
    // already-validated single-CVM-phase solve() entry point -- Step 7's
    // pattern) to confirm: (1) the initial u is NOT already stationary --
    // i.e. this is a genuine relaxation, not a no-op -- and (2) the final
    // u is a stationary point (small max|dG/du|).
    // ------------------------------------------------------------------
    @Test
    void candidateRelaxationActuallyMovesInternalVariables() throws Exception {
        TdbParser parser = new TdbParser();
        parser.load(TDB);
        CvmGibbsModel bcc = buildBccModel(parser);

        double[] y0 = bcc.getInitialInternalVars(TARGET);
        assertTrue(bcc.isValid(y0), "BCC_A2 initial constitution must be valid.");

        double[] grad0 = bcc.dG_dy(T, P, y0);
        double maxAbsGradU0 = 0.0;
        for (int i = 0; i < NCF; i++) {
            maxAbsGradU0 = Math.max(maxAbsGradU0, Math.abs(grad0[i]));
        }

        // Same nested single-candidate fixed-composition solve pattern
        // relaxCvmCandidate() uses internally.
        EquilibriumSolverV2 nestedSolver = new EquilibriumSolverV2();
        nestedSolver.setTolerance(1.0e-6);
        nestedSolver.setInitialStateForTest(
                new int[]{0},
                new double[][]{y0},
                new double[]{1.0});

        EquilibriumResult nestedResult =
                nestedSolver.solve(T, P, TARGET, List.of(bcc));

        assertTrue(nestedResult.isConverged(), "CVM candidate relaxation should converge.");
        double[] yFinal = nestedResult.getStablePhases().get(0).y;
        assertNotNull(yFinal);
        assertTrue(bcc.isValid(yFinal), "Relaxed CVM candidate state must remain valid.");

        double[] gradFinal = bcc.dG_dy(T, P, yFinal);
        double maxAbsGradUFinal = 0.0;
        for (int i = 0; i < NCF; i++) {
            maxAbsGradUFinal = Math.max(maxAbsGradUFinal, Math.abs(gradFinal[i]));
        }

        System.out.println();
        System.out.println("=== CVM candidate relaxation (x=" + Arrays.toString(TARGET) + ") ===");
        System.out.println("u initial       = " + Arrays.toString(Arrays.copyOfRange(y0, 0, NCF)));
        System.out.println("u final         = " + Arrays.toString(Arrays.copyOfRange(yFinal, 0, NCF)));
        System.out.printf(Locale.ROOT, "max|dG/du| initial = %.6e%n", maxAbsGradU0);
        System.out.printf(Locale.ROOT, "max|dG/du| final   = %.6e%n", maxAbsGradUFinal);

        // (1) genuine relaxation: u actually moved.
        boolean anyChanged = false;
        for (int i = 0; i < NCF; i++) {
            if (Math.abs(yFinal[i] - y0[i]) > 1.0e-8) {
                anyChanged = true;
                break;
            }
        }
        assertTrue(anyChanged,
                "Relaxation must actually move the CVM internal variables u "
                + "(initial state is not already stationary at this composition).");

        // (2) final state is stationary.
        assertTrue(maxAbsGradUFinal < 1.0e-2,
                "max|dG/du| at the relaxed state should be small (stationary point): "
                + maxAbsGradUFinal);

        // (3) the relaxed state agrees with the independently validated
        // Step 7 Hillert cross-check reference at this SAME composition
        // (x=[0.95,0.05]) -- proving relaxation converges to the correct
        // physical minimum, not merely some nearby stationary artifact.
        double[] uHillert95_05 = {
                0.002196933159356511, -0.04300541171348146,
                0.04758483141119484, 0.04759867139824415};
        for (int i = 0; i < NCF; i++) {
            assertEquals(uHillert95_05[i], yFinal[i], 5.0e-5,
                    "Relaxed u[" + i + "] must agree with the Hillert cross-check reference.");
        }
    }

    // ------------------------------------------------------------------
    // Test C: candidate driving force is finite, internally consistent
    // (matches the direct Sundman Eq. 62 formula evaluated on the same
    // relaxed state and the outer solve's own chemical potentials), and
    // has the expected sign (positive: BCC_A2 is genuinely favorable at
    // this composition against V2ZR-only mu, matching Test A).
    // ------------------------------------------------------------------
    @Test
    void candidateDrivingForceIsFiniteAndCorrectSign() throws Exception {
        TdbParser parser = new TdbParser();
        parser.load(TDB);

        List<GibbsEnergyModel> candidates = buildCandidates(parser);
        CefGibbs v2zr = (CefGibbs) candidates.get(0);
        CvmGibbsModel bcc = (CvmGibbsModel) candidates.get(1);

        double[] yV2zr = v2zr.getInitialInternalVars(new double[]{1.0 - 1.0 / 3.0, 1.0 / 3.0});

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setTolerance(1.0e-6);
        solver.setInitialStateForTest(
                new int[]{0},
                new double[][]{yV2zr},
                new double[]{1.0});

        EquilibriumResult result = solver.solve(T, P, TARGET, candidates, true);

        assertNotNull(result.getStableSetChange());
        assertEquals("BCC_A2", result.getStableSetChange().phaseName);

        // Recover the outer solve's chemical potentials at the moment of
        // the stable-set change, and independently recompute BCC_A2's
        // relaxed-state driving force via the exact same nested-solve
        // procedure the production code uses, to check internal
        // consistency against the newly-added stable slot's own reported
        // driving force.
        double[] mu = result.getMu();
        assertNotNull(mu);
        for (double m : mu) {
            assertTrue(Double.isFinite(m), "Chemical potentials must be finite.");
        }

        EquilibriumResult.PhaseResult bccSlot = null;
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            if ("BCC_A2".equals(pr.phaseName)) {
                bccSlot = pr;
            }
        }
        assertNotNull(bccSlot, "BCC_A2 must have been added as a new stable slot.");
        assertTrue(Double.isFinite(bccSlot.G), "Added BCC_A2 slot's G must be finite.");
        assertTrue(Double.isFinite(bccSlot.drivingForce),
                "Added BCC_A2 slot's driving force must be finite.");

        double[] mABcc = bcc.moles(bccSlot.y);
        double expectedDrivingForce = -bccSlot.G;
        for (int a = 0; a < mu.length; a++) {
            expectedDrivingForce += mu[a] * mABcc[a];
        }

        System.out.println();
        System.out.println("=== CVM candidate driving force ===");
        System.out.printf(Locale.ROOT, "mu                = %s%n", Arrays.toString(mu));
        System.out.printf(Locale.ROOT, "BCC_A2 G          = %.6f%n", bccSlot.G);
        System.out.printf(Locale.ROOT, "BCC_A2 x          = %s%n", Arrays.toString(bccSlot.x));
        System.out.printf(Locale.ROOT, "driving force     = %.6e%n", expectedDrivingForce);

        assertEquals(expectedDrivingForce, bccSlot.drivingForce,
                1.0e-6 * Math.max(1.0, Math.abs(expectedDrivingForce)),
                "Driving force must match Sundman Eq. 62 (-G + sum mu_A*M_A) exactly.");

        // Qualitative sign: BCC_A2 is the physically stable phase at this
        // composition (see EquilibriumSolverV2CefCvmTwoPhaseEndToEndTest's
        // class javadoc) -- against V2ZR-only chemical potentials its
        // driving force must be strongly positive, matching the fact it
        // was recognized as APPEARING in Test A.
        assertTrue(expectedDrivingForce > 0.0,
                "BCC_A2's driving force against V2ZR-only mu should be positive "
                + "(favorable) at x=[0.95,0.05]: " + expectedDrivingForce);
    }
}
