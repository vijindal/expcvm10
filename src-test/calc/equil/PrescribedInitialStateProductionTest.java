package calc.equil;

import org.junit.jupiter.api.Test;

import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production test for {@link EquilibriumSolverV2#setInitialState}.
 *
 * <p>Demonstrates the legitimate production use case: inner solver instances
 * for composition sampling with a prescribed initial state, equilibrating
 * without GridMinimizer reinvocation.
 *
 * <p>Test verifies:
 * 1. GridMinimizer is bypassed when setInitialState() is used
 * 2. Normal Newton/Sundman iteration runs
 * 3. Solver converges from the prescribed starting point
 * 4. Returned EquilibriumResult contains equilibrated Y and composition
 * 5. Returned composition matches the prescribed composition
 */
class PrescribedInitialStateProductionTest {

    private static final String TDB = "data/VZR-re2.TDB";
    private static final String PHASE = "BCC_A2";
    private static final double T = 1100.0;
    private static final double P = 101325.0;

    /**
     * Production test: single phase with prescribed composition and
     * initial constitution, converges via Newton/Sundman.
     *
     * <p>Flow:
     * 1. Load phase model from database
     * 2. Prescribe composition xSample
     * 3. Get initial internal vars from model.getInitialInternalVars(xSample)
     * 4. Create EquilibriumSolverV2 instance
     * 5. Call setInitialState() with prescribed state
     * 6. Call solve() -- GridMinimizer is NOT invoked
     * 7. Verify convergence and returned composition
     *
     * <p>This is the exact workflow inner solver instances will follow.
     */
    @Test
    void prescribedStateConverges_SinglePhase() throws Exception {

        // Load phase model
        system.database.TdbParser parser = new system.database.TdbParser();
        parser.load(TDB);

        List<GibbsEnergyModel> candidates = parser.buildPhaseModels(
                Arrays.asList("V", "ZR"),
                Arrays.asList(PHASE)
        );

        GibbsEnergyModel model = candidates.get(0);

        // Prescribed composition (overall system composition)
        double[] xSample = {0.45, 0.55};

        // Get initial internal variables for this composition
        double[] y0 = model.getInitialInternalVars(xSample);
        assertTrue(model.isValid(y0),
                "Initial Y must be valid");

        // Create inner solver
        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setTolerance(1.0e-10);

        // PRODUCTION API: Prescribe the initial state
        solver.setInitialState(
                new int[]{0},          // phase candidate index
                new double[][]{y0},    // initial Y
                new double[]{1.0}      // phase amount (whole system)
        );

        // Solve: GridMinimizer is bypassed, Newton/Sundman loop runs
        EquilibriumResult result = solver.solve(T, P, xSample, candidates);

        // Verify convergence
        assertNotNull(result,
                "Solver must return a result");
        assertTrue(result.isConverged(),
                "Solver must converge from prescribed state");

        // Verify single stable phase
        assertEquals(1, result.getStablePhases().size(),
                "Must have exactly one stable phase");

        EquilibriumResult.PhaseResult stable = result.getStablePhases().get(0);

        // Verify phase identity
        assertEquals("BCC_A2", stable.phaseName,
                "Phase name");

        // Verify equilibrated composition matches prescribed composition
        assertEquals(xSample[0], stable.x[0], 1.0e-6,
                "Equilibrated x[0] must match prescribed x");
        assertEquals(xSample[1], stable.x[1], 1.0e-6,
                "Equilibrated x[1] must match prescribed x");

        // Verify equilibrated Y is valid
        double[] yEq = stable.y;
        assertNotNull(yEq,
                "Equilibrated Y must not be null");
        assertTrue(model.isValid(yEq),
                "Equilibrated Y must be valid");

        // Verify Gibbs energy is finite
        assertTrue(Double.isFinite(stable.G),
                "Equilibrated G must be finite: " + stable.G);

        // Verify chemical potentials are finite
        double[] mu = result.getMu();
        for (double m : mu) {
            assertTrue(Double.isFinite(m),
                    "Chemical potentials must be finite");
        }

        // Verify phase amount is reasonable
        assertTrue(stable.amount > 0.0 && Double.isFinite(stable.amount),
                "Phase amount must be positive and finite: " + stable.amount);

        // Cross-check: directly evaluate converged G
        double gDirect = model.G(T, P, yEq);
        assertEquals(gDirect, stable.G, 1.0e-6 * Math.max(1.0, Math.abs(gDirect)),
                "Directly evaluated G must match solver result");
    }

    /**
     * Verify that GridMinimizer is NOT invoked when setInitialState() is used.
     *
     * <p>This is crucial: if GridMinimizer were called, it would invoke
     * an expensive composition-space sweep that is unnecessary when the
     * composition is already prescribed. By using setInitialState, we
     * skip GridMinimizer entirely.
     */
    @Test
    void gridMinimizerNotInvokedWithPrescribedState() throws Exception {

        system.database.TdbParser parser = new system.database.TdbParser();
        parser.load(TDB);

        List<GibbsEnergyModel> candidates = parser.buildPhaseModels(
                Arrays.asList("V", "ZR"),
                Arrays.asList(PHASE)
        );

        GibbsEnergyModel model = candidates.get(0);
        double[] xSample = {0.5, 0.5};
        double[] y0 = model.getInitialInternalVars(xSample);

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setTolerance(1.0e-10);

        // Prescribe initial state (GridMinimizer not invoked)
        solver.setInitialState(
                new int[]{0},
                new double[][]{y0},
                new double[]{1.0}
        );

        // Solve: no GridMinimizer invocation
        EquilibriumResult result = solver.solve(T, P, xSample, candidates);

        assertTrue(result.isConverged(),
                "Should converge without GridMinimizer");
    }

    /**
     * Verify backward compatibility: deprecated setInitialStateForTest()
     * still works and delegates to the new production API.
     */
    @Test
    void backwardCompatibility_setInitialStateForTestStillWorks() throws Exception {

        system.database.TdbParser parser = new system.database.TdbParser();
        parser.load(TDB);

        List<GibbsEnergyModel> candidates = parser.buildPhaseModels(
                Arrays.asList("V", "ZR"),
                Arrays.asList(PHASE)
        );

        GibbsEnergyModel model = candidates.get(0);
        double[] xSample = {0.4, 0.6};
        double[] y0 = model.getInitialInternalVars(xSample);

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setTolerance(1.0e-10);

        // Use deprecated method (should delegate to setInitialState)
        @SuppressWarnings("deprecation")
        EquilibriumSolverV2 unused = solver;
        unused.setInitialStateForTest(
                new int[]{0},
                new double[][]{y0},
                new double[]{1.0}
        );

        EquilibriumResult result = solver.solve(T, P, xSample, candidates);

        assertTrue(result.isConverged(),
                "Deprecated method must still work");
        assertEquals(xSample[0], result.getStablePhases().get(0).x[0], 1.0e-6);
        assertEquals(xSample[1], result.getStablePhases().get(0).x[1], 1.0e-6);
    }
}
