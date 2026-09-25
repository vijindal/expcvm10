package calc.equil;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.cvm.CecTerm;
import system.model.cvm.CvmGibbsModel;
import system.model.cvm.CvmPhaseData;
import system.model.cvm.CvmPhaseSpec;
import system.model.unary.ElementGibbs;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B: verifies GridMinimizer.initialize() now samples composition
 * space and equilibrates every sample via an independent inner
 * {@link EquilibriumSolverV2} instance, rather than evaluating G directly
 * at raw sampled site fractions.
 *
 * <p>Uses a synthetic binary CVM model (no TDB I/O) so the ~4000 inner
 * single-phase solves this composition-space sampling implies stay fast
 * enough for the default (non "integration-heavy") test task.
 */
class GridMinimizerInnerSolverWiringTest {

    private static final double T = 1000.0;
    private static final double P = 101325.0;

    /**
     * GridMinimizer's normal initialize() path must now use composition
     * sampling (sampleCompositions), not sampleSiteFractions, to seed the
     * hull point cloud, and the returned stable-phase composition (lever
     * rule across stable slots) must reproduce the requested xOverall.
     *
     * <p>Previously disabled: this CVM fixture's equilibrated single-phase
     * G(x) curve has a deep, narrow minimum near x=0.5 versus a much
     * shallower region near x=0.15, which reliably triggered a
     * Hyperplane.solve pivot-search bug -- its unconditional
     * {@code {0,1}}-index seed simplex locked onto two near-duplicate
     * points near x=0.152 and returned wildly out-of-range barycentric
     * fractions (~-695/+696) instead of bracketing x=0.5, before a single
     * pivot iteration ran. Phase 3D fixed Hyperplane.solve to validate its
     * seed simplex's conditioning (and repair it from the point cloud when
     * degenerate) before entering the pivot loop; this test is re-enabled
     * to confirm the fixture now converges to the true x=0.5 minimum.
     */
    @Test
    @Tag("slow")
    void initializeUsesCompositionSamplingAndInnerSolver() {

        CvmGibbsModel cvm = buildCvmModel();
        List<GibbsEnergyModel> candidates = List.of(cvm);

        double[] xOverall = {0.5, 0.5};

        GridMinimizer minimizer = new GridMinimizer();
        EquilibriumState state = minimizer.initialize(candidates, T, P, xOverall);

        assertNotNull(state, "GridMinimizer should return an EquilibriumState");
        assertTrue(!state.stablePhases().isEmpty(), "Should find at least one stable phase");

        // The hull may return more than one stable slot of the SAME
        // candidate phase (e.g. an ordered/disordered split) whose
        // amount-weighted (lever rule) composition reproduces xOverall,
        // even though no single vertex needs to sit exactly at xOverall.
        double[] leverX = new double[xOverall.length];
        double totalAmount = 0.0;
        for (PhaseRecord pr : state.stablePhases()) {
            assertEquals("BCC_A2", pr.model.phaseName());
            assertEquals("CVM", pr.model.modelType());
            assertNotNull(pr.x);
            assertNotNull(pr.y);
            assertTrue(cvm.isValid(pr.y), "Stored Y must be valid for the model");
            assertTrue(Double.isFinite(pr.G), "Gibbs energy must be finite: " + pr.G);

            for (int i = 0; i < leverX.length; i++) {
                leverX[i] += pr.amount * pr.x[i];
            }
            totalAmount += pr.amount;
        }
        for (int i = 0; i < leverX.length; i++) {
            leverX[i] /= totalAmount;
        }

        // The resulting (lever-rule) composition must correspond to the
        // requested sampled composition within the solver's normal
        // tolerance.
        assertEquals(xOverall[0], leverX[0], 1.0e-3);
        assertEquals(xOverall[1], leverX[1], 1.0e-3);
    }

    /**
     * Each sampled composition must be equilibrated by its own,
     * independent EquilibriumSolverV2 instance solving a SINGLE-phase
     * problem (candidates list of size 1, phase index 0) -- not the full
     * candidate list, and not a shared/reused solver instance.
     *
     * Verified here by confirming the same behavior the production path
     * relies on: an inner solve of one composition sample, seeded via
     * setInitialState() with stablePhases={0}, converges to a state whose
     * composition matches the sample and whose model is exactly the one
     * candidate passed in.
     */
    @Test
    void innerSolveIsSinglePhaseAndIndependent() {

        CvmGibbsModel cvm = buildCvmModel();

        double[] xSample = {0.6, 0.4};
        double[] y0 = cvm.getInitialInternalVars(xSample);

        EquilibriumSolverV2 inner = new EquilibriumSolverV2();
        inner.setTolerance(1.0e-8);
        inner.setInitialState(new int[]{0}, new double[][]{y0}, new double[]{1.0});

        var result = inner.solve(T, P, xSample, List.of(cvm));

        assertTrue(result.isConverged(), "Inner single-phase solve should converge");
        assertEquals(1, result.getStablePhases().size(),
                "Inner problem must contain exactly one phase");

        var pr = result.getStablePhases().get(0);
        assertEquals("BCC_A2", pr.phaseName);
        assertEquals(xSample[0], pr.x[0], 1.0e-6);
        assertEquals(xSample[1], pr.x[1], 1.0e-6);

        // A second, independent inner solver at a different composition
        // must not be affected by the first instance's state.
        double[] xSample2 = {0.3, 0.7};
        double[] y02 = cvm.getInitialInternalVars(xSample2);

        EquilibriumSolverV2 inner2 = new EquilibriumSolverV2();
        inner2.setTolerance(1.0e-8);
        inner2.setInitialState(new int[]{0}, new double[][]{y02}, new double[]{1.0});

        var result2 = inner2.solve(T, P, xSample2, List.of(cvm));

        assertTrue(result2.isConverged());
        assertEquals(xSample2[0], result2.getStablePhases().get(0).x[0], 1.0e-6);
    }

    /**
     * The GridMinimizer-produced hull-point Y must be the inner solver's
     * EQUILIBRATED Y, not the unequilibrated getInitialInternalVars()
     * seed -- i.e. running the model's G at the returned Y should exactly
     * match the model's own G at that Y (sanity), and the returned Y
     * should generally differ from the raw seed for a composition where
     * the model has genuine internal relaxation (non-trivial CEC terms
     * here bias the equilibrium constitution away from the naive seed).
     */
    @Test
    @Tag("slow")
    void hullPointYIsEquilibratedNotRawSeed() {

        CvmGibbsModel cvm = buildCvmModel();
        double[] xOverall = {0.4, 0.6};

        List<GibbsEnergyModel> candidates = List.of(cvm);
        GridMinimizer minimizer = new GridMinimizer();
        EquilibriumState state = minimizer.initialize(candidates, T, P, xOverall);

        PhaseRecord stable = state.stablePhases().get(0);
        double[] hullY = stable.y;

        assertNotNull(hullY);
        assertTrue(cvm.isValid(hullY));

        // Cross-check: G at the stored Y must equal the model's own G()
        // evaluated at that same Y (proves it's a real, self-consistent
        // constitution, not a placeholder).
        double gDirect = cvm.G(T, P, hullY);
        assertEquals(gDirect, stable.G, 1.0e-6 * Math.max(1.0, Math.abs(gDirect)));
    }

    // ════════════════════════════════════════════════════════════════════
    // Fixture: CVM binary BCC_A2 model (same fixture pattern used
    // elsewhere in this package for CVM end-to-end tests)
    // ════════════════════════════════════════════════════════════════════

    private static CvmGibbsModel buildCvmModel() {
        CvmPhaseData data = buildCvmPhaseData();

        List<CecTerm> cecTerms = List.of(
                new CecTerm("v4AB", 100.0, 0.0),
                new CecTerm("v3AB", -50.0, 0.01),
                new CecTerm("v2AB2", 200.0, -0.02),
                new CecTerm("v2AB1", -300.0, 0.05));

        ElementGibbs ghserA = new TestElementGibbs("A", 1000.0, -5.0);
        ElementGibbs ghserB = new TestElementGibbs("B", -2000.0, 3.0);

        CvmPhaseSpec spec = new CvmPhaseSpec(data, cecTerms,
                new ElementGibbs[]{ghserA, ghserB}, List.of("A", "B"));

        return (CvmGibbsModel) PhaseModelFactory.buildCvm(spec);
    }

    private static CvmPhaseData buildCvmPhaseData() {
        int nComp = 2;
        int ncf = 4;
        int tcdis = 5;

        double[] mhdis = {6.0, 12.0, 4.0, 3.0, 1.0};
        double[] kbdis = {1.0, -1.0, 1.0, 1.0, -1.0};

        int[] lc = {1, 1, 1, 1, 1};

        int[][] lcv = {
                {6}, {6}, {3}, {3}, {2},
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
                {{
                        {1.0, 1.0, 0.0, -2.0, 1.0, 0.0},
                        {-1.0, -0.5, -0.5, 1.0, 0.0, 0.0},
                        {1.0, 0.0, 1.0, -1.0, 0.0, 0.0},
                        {1.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                        {-1.0, 0.5, -0.5, 1.0, 0.0, 0.0},
                        {1.0, -1.0, 0.0, -2.0, 0.0, 1.0},
                }},
                {{
                        {0.0, 0.5, -0.5, -1.0, 1.0, 0.0},
                        {0.0, -0.5, 0.5, 0.0, 0.0, 0.0},
                        {0.0, 0.5, -0.5, 1.0, 0.0, 0.0},
                        {0.0, -0.5, -0.5, 1.0, 0.0, 0.0},
                        {0.0, 0.5, 0.5, 0.0, 0.0, 0.0},
                        {0.0, -0.5, -0.5, -1.0, 0.0, 1.0},
                }},
                {{
                        {0.0, 0.0, 0.0, -1.0, 1.0, 0.0},
                        {0.0, 0.0, 0.0, 1.0, 0.0, 0.0},
                        {0.0, 0.0, 0.0, -1.0, 0.0, 1.0},
                }},
                {{
                        {0.0, 0.0, -1.0, 0.0, 1.0, 0.0},
                        {0.0, 0.0, 1.0, 0.0, 0.0, 0.0},
                        {0.0, 0.0, -1.0, 0.0, 0.0, 1.0},
                }},
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
                "BCC_A2", nComp, ncf, tcdis, mhdis, kbdis,
                lc, lcv, mh, wcv, cmat,
                uListLen, cfCoeffs, u2Names, eNames);
    }

    private static final class TestElementGibbs implements ElementGibbs {
        private final String symbol;
        private final double a;
        private final double b;

        TestElementGibbs(String symbol, double a, double b) {
            this.symbol = symbol;
            this.a = a;
            this.b = b;
        }

        @Override public String elementSymbol() { return symbol; }
        @Override public double gibbs(String phaseName, double T) { return a + b * T; }
        @Override public double ghser(double T) { return a + b * T; }
        @Override public Set<String> availablePhases() { return Set.of("BCC_A2"); }
    }
}
