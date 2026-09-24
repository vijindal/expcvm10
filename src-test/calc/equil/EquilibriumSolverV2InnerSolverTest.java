package calc.equil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.cvm.CecTerm;
import system.model.cvm.CvmGibbsModel;
import system.model.cvm.CvmPhaseData;
import system.model.cvm.CvmPhaseSpec;
import system.model.unary.ElementGibbs;
import system.ports.EquilibriumResult;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the production {@link EquilibriumSolverV2#setInitialState} API for
 * inner solver use: single-phase fixed-composition equilibration without
 * GridMinimizer invocation.
 *
 * <p>This demonstrates the intended workflow for GridMinimizer's inner
 * solver instances: prescribe a composition, initialize internal variables,
 * equilibrate, and return the converged state. GridMinimizer will use many
 * such inner solvers to build a lower-hull point cloud.
 */
@Disabled("Incomplete refactoring: requires new model.getStateSampler() API not yet implemented")
class EquilibriumSolverV2InnerSolverTest {

    private static final double T = 1000.0;
    private static final double P = 101325.0;

    /**
     * CVM inner solver test: single-phase fixed-composition equilibration
     * using the production setInitialState() API.
     *
     * Demonstrates:
     * - One phase, prescribed composition xSample
     * - Initial Y from model.getInitialInternalVars(xSample)
     * - setInitialState() call (production API, public)
     * - GridMinimizer is NOT invoked
     * - Solver converges via existing Newton/Sundman loop
     * - Returns equilibrated (Y, G, x) in EquilibriumResult
     */
    @Test
    void cvmInnerSolverConverges() {

        // Build a simple CVM model (binary BCC_A2)
        CvmGibbsModel cvm = buildCvmModel();
        List<GibbsEnergyModel> candidates = List.of(cvm);

        // Sample composition
        double[] xSample = {0.5, 0.5};

        // Initialize internal variables for this composition
        double[] y0 = cvm.getInitialInternalVars(xSample);
        assertTrue(cvm.isValid(y0), "Initial Y must be valid");

        // Create inner solver
        EquilibriumSolverV2 innerSolver = new EquilibriumSolverV2();
        innerSolver.setTolerance(1.0e-8);

        // Prescribe initial state (test API)
        innerSolver.setInitialStateForTest(
                new int[]{0},                  // phase candidate index
                new double[][]{y0},            // initial Y for that phase
                new double[]{1.0}              // phase amount (whole system)
        );

        // Solve at the prescribed composition
        EquilibriumResult result = innerSolver.solve(T, P, xSample, candidates);

        // Verify convergence
        assertNotNull(result, "Solver must return a result");
        assertTrue(result.isConverged(),
                "Inner solver must converge for single-phase equilibration");

        // Verify single stable phase
        assertEquals(1, result.getStablePhases().size(),
                "Must have exactly one stable phase");

        EquilibriumResult.PhaseResult stable = result.getStablePhases().get(0);

        // Verify phase identity
        assertEquals("BCC_A2", stable.phaseName, "Phase name");
        assertEquals("CVM", stable.modelType, "Model type");

        // Verify equilibrated composition matches sampled composition
        assertEquals(xSample[0], stable.x[0], 1.0e-6,
                "Equilibrated x[0] must match sampled x");
        assertEquals(xSample[1], stable.x[1], 1.0e-6,
                "Equilibrated x[1] must match sampled x");

        // Verify equilibrated Y is valid
        double[] yEq = stable.y;
        assertNotNull(yEq, "Equilibrated Y must not be null");
        assertTrue(cvm.isValid(yEq), "Equilibrated Y must be valid");

        // Verify Gibbs energy is finite and sensible
        assertTrue(Double.isFinite(stable.G),
                "Equilibrated G must be finite: " + stable.G);

        // Verify chemical potentials are finite
        double[] mu = result.getMu();
        for (double m : mu) {
            assertTrue(Double.isFinite(m),
                    "Chemical potentials must be finite: " + Arrays.toString(mu));
        }

        // Verify phase amount
        assertTrue(stable.amount > 0.0 && Double.isFinite(stable.amount),
                "Phase amount must be positive and finite: " + stable.amount);

        // Cross-check: directly evaluate converged G
        double gDirect = cvm.G(T, P, yEq);
        assertEquals(gDirect, stable.G, 1.0e-6 * Math.max(1.0, Math.abs(gDirect)),
                "Directly evaluated G must match solver result");
    }

    /**
     * Multi-composition workflow: three samples of the same phase,
     * each equilibrated via inner solver, demonstrating the intended
     * GridMinimizer usage pattern.
     *
     * For each sampled composition:
     * 1. Generate initial internal vars
     * 2. Create inner solver with setInitialState()
     * 3. Equilibrate
     * 4. Extract (x_eq, G_eq)
     *
     * This mimics what GridMinimizer will do when refactored.
     */
    @Test
    void cvmMultiCompositionInnerSolvers() {

        CvmGibbsModel cvm = buildCvmModel();
        List<GibbsEnergyModel> candidates = List.of(cvm);

        double[][] xSamples = {
                {0.3, 0.7},
                {0.5, 0.5},
                {0.7, 0.3}
        };

        for (double[] xSample : xSamples) {

            double[] y0 = cvm.getInitialInternalVars(xSample);
            assertTrue(cvm.isValid(y0));

            EquilibriumSolverV2 innerSolver = new EquilibriumSolverV2();
            innerSolver.setTolerance(1.0e-8);

            innerSolver.setInitialStateForTest(
                    new int[]{0},
                    new double[][]{y0},
                    new double[]{1.0}
            );

            EquilibriumResult result =
                    innerSolver.solve(T, P, xSample, candidates);

            assertTrue(result.isConverged(),
                    "Composition " + Arrays.toString(xSample) + " should converge");

            EquilibriumResult.PhaseResult stable =
                    result.getStablePhases().get(0);

            // Equilibrated point
            double[] yEq = stable.y;
            double G_eq = stable.G;
            double[] x_eq = stable.x;

            // Verify consistency
            assertEquals(xSample[0], x_eq[0], 1.0e-6);
            assertEquals(xSample[1], x_eq[1], 1.0e-6);
            assertTrue(Double.isFinite(G_eq));
            assertTrue(cvm.isValid(yEq));

            System.out.println(String.format(
                    "Inner solver @ x=(%.1f,%.1f): G=%.2f, converged",
                    xSample[0], xSample[1], G_eq));
        }
    }

    /**
     * Test that GridMinimizer is NOT invoked when setInitialState() is used.
     *
     * Verify this by checking that no GridMinimizer errors would occur
     * even if its implementation had bugs -- the solver never creates
     * a GridMinimizer instance when a prescribed state is supplied.
     *
     * (If GridMinimizer were invoked, CVM would raise
     * UnsupportedOperationException from getStateSampler().sample())
     */
    @Test
    void gridMinimizerNotInvokedWithPrescribedState() {

        CvmGibbsModel cvm = buildCvmModel();
        List<GibbsEnergyModel> candidates = List.of(cvm);

        double[] xSample = {0.6, 0.4};
        double[] y0 = cvm.getInitialInternalVars(xSample);

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setTolerance(1.0e-8);

        // Prescribe initial state
        solver.setInitialStateForTest(
                new int[]{0},
                new double[][]{y0},
                new double[]{1.0}
        );

        // Solve WITHOUT exceptions
        // (If GridMinimizer were invoked, UnsupportedOperationException
        // would be thrown from CVM's getStateSampler().sample())
        EquilibriumResult result = solver.solve(T, P, xSample, candidates);

        assertTrue(result.isConverged(),
                "Should converge without GridMinimizer");
    }

    // ════════════════════════════════════════════════════════════════════
    // Fixture: CVM binary BCC_A2 model
    // ════════════════════════════════════════════════════════════════════

    private static CvmGibbsModel buildCvmModel() {
        CvmPhaseData data = buildCvmPhaseData();

        List<CecTerm> cecTerms = List.of(
                new CecTerm("v4AB", 100.0, 0.0),
                new CecTerm("v3AB", -50.0, 0.01),
                new CecTerm("v22AB", 200.0, -0.02),
                new CecTerm("v21AB", -300.0, 0.05));

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

        String[] u2Names = {"v4AB", "v3AB", "v22AB", "v21AB", "xA", "xB"};
        String[] eNames = {"v4AB", "v3AB", "v22AB", "v21AB"};

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
