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
 * Phase 4G, part C: boundary/ZPF INTEGRATION test that exercises the real
 * production path --
 *
 * <pre>
 *     solveBoundary()
 *         -&gt; setUpBoundarySolve()
 *             -&gt; addNewStableSlot()
 *                 -&gt; bestSeedConstitution()   (CVM branch, Phase 4G)
 * </pre>
 *
 * -- for a newly-appearing CVM phase, WITHOUT bypassing seed generation via
 * {@code setInitialStateForTest} the way every earlier CVM test in this
 * package deliberately does (see e.g. {@code
 * EquilibriumSolverV2CvmSinglePhaseEndToEndTest}'s own class javadoc,
 * which explicitly states CVM candidate discovery/grid sampling was out of
 * scope for that step).
 *
 * <h2>Fixture: the existing real V-Zr candidate-stability fixture</h2>
 * Reuses EXACTLY the same real, already-validated setup {@code
 * EquilibriumSolverV2CvmCandidateStabilityTest} uses: {@code
 * data/VZR-re2.TDB}, V2ZR built as CEF, BCC_A2 built as a real CVM model
 * with the published Jindal &amp; Lele (CALPHAD 89 (2025) 102825, Table 11)
 * CECs, at T=1000K, overall composition x(V,Zr)=[0.95,0.05] -- a
 * composition deep inside BCC_A2's actual single-phase field, where that
 * test independently proves (Test C, {@code
 * candidateDrivingForceIsFiniteAndCorrectSign}) BCC_A2's driving force
 * against V2ZR-forced-alone chemical potentials is genuinely, strongly
 * positive. No synthetic/invented CEC parameters are introduced here --
 * this is a verbatim copy of that class's already-validated fixture.
 *
 * <p>{@code EquilibriumSolverV2CvmCandidateStabilityTest} exercises this
 * crossing through the MAIN candidate-discovery path ({@code
 * updateStablePhaseSet}/{@code relaxCvmCandidate}). This test exercises
 * the SAME physical crossing through the boundary/ZPF path instead ({@code
 * solveBoundary} -&gt; {@code addNewStableSlot} -&gt; {@code
 * bestSeedConstitution}'s new CVM branch), which needs a genuinely
 * different seed-search formulation (Phase 4F: the boundary phase's
 * composition is not fixed to the overall target the way the main-loop
 * candidate's is).
 */
class EquilibriumSolverV2CvmBoundaryZpfIntegrationTest {

    private static final String TDB = "data/VZR-re2.TDB";
    private static final double T = 1000.0;
    private static final double P = 101325.0;
    private static final double[] TARGET = {0.95, 0.05};

    /** Verbatim copy of EquilibriumSolverV2CvmCandidateStabilityTest's BCC_A2 CVM fixture. */
    private static CvmGibbsModel buildBccModel(TdbParser parser) {
        CvmPhaseData data = buildBccPhaseData();

        // Real V-Zr bcc CECs, Jindal & Lele, CALPHAD 89 (2025) 102825,
        // Table 11 (verbatim copy of the Step 6/7/8 fixture).
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

    /** Verbatim copy of the Step 3-8 binary BCC_A2 tetrahedron-approximation fixture. */
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

    private static List<GibbsEnergyModel> buildCandidates(TdbParser parser) throws Exception {
        List<GibbsEnergyModel> rawCef =
                parser.buildPhaseModels(Arrays.asList("V", "ZR"), Arrays.asList("V2ZR"));
        assertEquals(1, rawCef.size(), "Expected exactly one V2ZR candidate.");
        CefGibbs v2zr = (CefGibbs) rawCef.get(0);
        CvmGibbsModel bcc = buildBccModel(parser);
        return List.of(v2zr, bcc);
    }

    @Test
    void boundarySolveInitializesNewlyAppearingCvmBccPhaseViaSeedSearch() throws Exception {

        TdbParser parser = new TdbParser();
        parser.load(TDB);

        List<GibbsEnergyModel> candidates = buildCandidates(parser);
        CefGibbs v2zr = (CefGibbs) candidates.get(0);

        // Same seed V2ZR uses in EquilibriumSolverV2CvmCandidateStabilityTest:
        // V2ZR forced stable ALONE at its own stoichiometric composition
        // (x_Zr=1/3), with the OUTER target composition set to TARGET
        // (deep in BCC_A2's real single-phase field) -- reproducing that
        // test's seed exactly, but via the public solve() entry point (no
        // stopOnStableSetChange short-circuit) so `seed` is a genuine
        // converged EquilibriumResult usable by solveBoundary().
        double[] yV2zr = v2zr.getInitialInternalVars(new double[]{1.0 - 1.0 / 3.0, 1.0 / 3.0});
        assertTrue(v2zr.isValid(yV2zr), "V2ZR initial constitution must be valid.");

        EquilibriumSolverV2 seedSolver = new EquilibriumSolverV2();
        seedSolver.setTolerance(1.0e-6);
        seedSolver.setInitialStateForTest(
                new int[]{0},
                new double[][]{yV2zr},
                new double[]{1.0});
        EquilibriumResult seed = seedSolver.solve(T, P, TARGET, List.of(v2zr));

        assertTrue(seed.isConverged(), "Single-phase V2ZR seed must converge");
        assertEquals(1, seed.getStablePhases().size());
        assertEquals("V2ZR", seed.getStablePhases().get(0).phaseName);

        // Fix BCC_A2 (absent from `seed`, and a real CVM model) at zero
        // amount and release component 0 (V)'s target amount -- exactly
        // the "newly-appearing phase at a boundary" scenario
        // addNewStableSlot() exists for (Phase 4E/4F/4G), forcing
        // bestSeedConstitution()'s CVM branch to search a composition and
        // relax BCC_A2's internal variables there before the boundary
        // Newton loop can even take its first step.
        EquilibriumSolverV2 boundarySolver = new EquilibriumSolverV2();
        boundarySolver.setTolerance(1.0e-6);

        EquilibriumSolverV2.BoundarySolveResult result;
        try {
            result = boundarySolver.solveBoundary(
                    T, P, TARGET, candidates, seed, "BCC_A2", 0.0, 0);
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "Boundary solve introducing BCC_A2 via the new CVM "
                    + "seed-search mechanism should not throw: " + e.getMessage(), e);
        }

        assertNotNull(result);
        assertTrue(result.equilibrium.isConverged(),
                "Boundary solve (fixing BCC_A2 at zero, releasing x(V)) "
                + "must converge using the seeded CVM starting constitution");

        // Both phases must be present in the boundary result: the
        // originally-stable V2ZR, and BCC_A2 at (approximately) zero
        // amount -- proving addNewStableSlot()/bestSeedConstitution()
        // actually produced a usable seed for BCC_A2 rather than the
        // solve failing to even start (the documented pre-Phase-4G
        // singular-matrix failure mode for a composition-only guess).
        List<EquilibriumResult.PhaseResult> stable = result.equilibrium.getStablePhases();
        boolean hasV2zr = false;
        boolean hasBcc = false;
        for (EquilibriumResult.PhaseResult pr : stable) {
            if (pr.phaseName.equals("V2ZR")) hasV2zr = true;
            if (pr.phaseName.equals("BCC_A2")) {
                hasBcc = true;
                assertTrue(Double.isFinite(pr.G), "BCC_A2's G must be finite");
                CvmGibbsModel bcc = (CvmGibbsModel) candidates.get(1);
                assertTrue(bcc.isValid(pr.y), "BCC_A2's constitution must be valid");
                assertEquals("CVM", pr.modelType);
            }
        }
        assertTrue(hasV2zr, "V2ZR must remain stable in the boundary result");
        assertTrue(hasBcc, "BCC_A2 (the newly-seeded CVM phase) must appear in the boundary result");

        assertTrue(Double.isFinite(result.releasedComponentValue),
                "Released composition component must be finite");

        System.out.println("Boundary result: releasedX(V)=" + result.releasedComponentValue);
        for (EquilibriumResult.PhaseResult pr : stable) {
            System.out.println("  " + pr.phaseName + " (" + pr.modelType + "): amount="
                    + pr.amount + " x=" + Arrays.toString(pr.x));
        }
    }
}
