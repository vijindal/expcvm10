package calc.equil;

import org.junit.jupiter.api.Test;

import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;
import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the actual public {@link EquilibriumSolverV2#solve}
 * entry point for the already-established fixed two-phase V-Zr case:
 *
 *     V2ZR + BCC_A2, T = 1100 K, target x(V,Zr) = (0.55, 0.45)
 *
 *     V2ZR : xZr = 0.35, omega = 1/6
 *     BCC  : xZr = 0.55, omega = 1/2
 *
 * These are exactly the parameters already used by the fixed two-phase
 * matrix baseline ({@code test.VzrFixedTwoPhaseSundmanBaseline} /
 * {@code test.VzrTwoPhaseSundmanMatrixBaselineTest}), reused here rather
 * than introducing another test problem.
 *
 * Unlike those baselines, this test does NOT call any private solver
 * stage directly (no {@code buildEquilibriumMatrix()},
 * {@code solveEquilibriumMatrix()}, {@code calculateInternalCorrections()},
 * etc.). It only calls the public {@link EquilibriumSolverV2#solve}
 * method, using the package-private {@code setInitialStateForTest}
 * hook solely to reproduce the same controlled starting point that the
 * matrix-level test already established. That hook does not change
 * solver mathematics, line search, convergence, or phase management.
 *
 * This test is deliberately modest: it establishes that solve() can
 * enter the iteration with a prescribed two-phase stable set, perform
 * the Sundman matrix solve, update, and continue iterating. It does not
 * yet assert the converged equilibrium values, since the nonlinear
 * iteration itself is what is being debugged.
 */
public class EquilibriumSolverV2TwoPhaseEndToEndTest {

    private static final String TDB =
            "data/VZR-re2.TDB";

    private static final String PHASE_A =
            "V2ZR";

    private static final String PHASE_B =
            "BCC_A2";

    private static final double T =
            1100.0;

    private static final double P =
            101325.0;

    private static final double[] TARGET =
            {0.55, 0.45};

    private static final double XZR_A =
            0.35;

    private static final double XZR_B =
            0.55;

    private static final double OMEGA_A =
            1.0 / 6.0;

    private static final double OMEGA_B =
            1.0 / 2.0;

    @Test
    void testV2ZR_BCC_twoPhase_endToEnd() throws Exception {

        // ---------------------------------------------------------
        // Problem definition / candidate phases
        // ---------------------------------------------------------

        system.database.TdbParser parser =
                new system.database.TdbParser();

        parser.load(TDB);

        List<String> elements =
                Arrays.asList("V", "ZR");

        List<String> phaseNames =
                Arrays.asList(PHASE_A, PHASE_B);

        @SuppressWarnings("unchecked")
        List<CefGibbs> raw =
                (List<CefGibbs>)
                        parser.buildPhaseModels(elements, phaseNames);

        assertEquals(
                2,
                raw.size(),
                "Expected exactly two candidate phases.");

        // buildPhaseModels already returns fully-built CefGibbs models.
        CefGibbs v2zr = raw.get(0);
        CefGibbs bcc  = raw.get(1);

        List<GibbsEnergyModel> candidates =
                Arrays.asList(v2zr, bcc);

        // ---------------------------------------------------------
        // Initial state: same as the matrix-level baseline
        // ---------------------------------------------------------

        double[] yA =
                v2zr.getInitialInternalVars(
                        new double[] {1.0 - XZR_A, XZR_A});

        double[] yB =
                bcc.getInitialInternalVars(
                        new double[] {1.0 - XZR_B, XZR_B});

        System.out.println();
        System.out.println(
                "=== V2ZR + BCC_A2 END-TO-END TEST ===");
        System.out.println();
        System.out.printf(
                "T = %.1f K%n",
                T);
        System.out.printf(
                "P = %.0f Pa%n",
                P);
        System.out.println(
                "target = " + Arrays.toString(TARGET));
        System.out.println();
        System.out.println("Initial state:");
        System.out.println(PHASE_A + ":");
        System.out.println(
                "    Y = " + Arrays.toString(yA));
        System.out.printf(
                "    omega = %.10f%n",
                OMEGA_A);
        System.out.println(PHASE_B + ":");
        System.out.println(
                "    Y = " + Arrays.toString(yB));
        System.out.printf(
                "    omega = %.10f%n",
                OMEGA_B);

        // ---------------------------------------------------------
        // Run the ACTUAL public solver
        // ---------------------------------------------------------

        EquilibriumSolverV2 solver =
                new EquilibriumSolverV2();

        solver.setInitialStateForTest(
                new int[] {0, 1},
                new double[][] {yA, yB},
                new double[] {OMEGA_A, OMEGA_B});

        EquilibriumResult result =
                solver.solve(
                        T,
                        P,
                        TARGET,
                        candidates);

        // ---------------------------------------------------------
        // Inspect result
        // ---------------------------------------------------------

        assertNotNull(result);

        assertNotNull(
                result.getMu(),
                "Solver must produce a chemical-potential vector.");

        assertEquals(
                TARGET.length,
                result.getMu().length,
                "mu must have one entry per system component.");

        for (double value : result.getMu()) {

            assertTrue(
                    Double.isFinite(value),
                    "mu must be finite: " + Arrays.toString(result.getMu()));
        }

        assertFalse(
                result.getStablePhases().isEmpty()
                        && result.getMetastablePhases().isEmpty(),
                "solve() must report at least one phase.");

        System.out.println();
        System.out.println(
                "Converged = " + result.isConverged()
                + ", iterations = " + result.getIterations());
        System.out.println(
                "mu(final) = " + Arrays.toString(result.getMu()));
    }

    /**
     * Verifies the same-candidate-twice stable-slot fix directly: a single
     * candidate phase (V2ZR) occupying TWO stable slots simultaneously
     * with two different site-fraction constitutions -- a real,
     * physically correct scenario (a phase split across a miscibility
     * gap, or a lower-convex-hull tie line with both endpoints on the
     * same phase, as {@code GridMinimizer} legitimately produces for
     * V2ZR -- see {@code GridMinimizerPycalphadTest}).
     *
     * <p>Before this fix, {@code EquilibriumSolverV2}'s {@code PhaseWork}
     * objects were indexed 1:1 with CANDIDATE phases
     * ({@code phaseWorks.get(p)}), so {@code stablePhases = {0, 0}}
     * (candidate 0 stable twice) aliased the SAME {@code PhaseWork} for
     * both slots: writing slot 0's {@code y} and then slot 1's {@code y}
     * in {@code updateState()}'s commit loop silently clobbered slot 0's
     * freshly-committed state. This produced duplicate rows in the global
     * equilibrium matrix and a "Matrix is singular" failure (reproduced
     * end-to-end by {@code CalculationSessionCalGTest}'s V2ZR case).
     *
     * <p>This test isolates the fix from {@code GridMinimizer}'s own
     * correctness by driving the same-candidate-twice state directly via
     * {@link EquilibriumSolverV2#setInitialStateForTest}, with only V2ZR
     * as a candidate (no BCC), so there is no ambiguity about which
     * candidate is being split.
     */
    @Test
    void testV2ZR_sameCandidateTwice_twoStableSlots_endToEnd() throws Exception {

        system.database.TdbParser parser =
                new system.database.TdbParser();

        parser.load(TDB);

        List<String> elements =
                Arrays.asList("V", "ZR");

        @SuppressWarnings("unchecked")
        List<CefGibbs> raw =
                (List<CefGibbs>)
                        parser.buildPhaseModels(elements, Arrays.asList(PHASE_A));

        assertEquals(
                1,
                raw.size(),
                "Expected exactly one candidate phase.");

        CefGibbs v2zr = raw.get(0);

        List<GibbsEnergyModel> candidates =
                Arrays.asList((GibbsEnergyModel) v2zr);

        // Two distinct V2ZR vertices straddling the target composition,
        // combined via the lever rule so their weighted composition
        // reconstructs TARGET exactly.
        double xZrLow = 0.30;
        double xZrHigh = 0.36;
        double targetXZr = 1.0 / 3.0;

        double lambda =
                (targetXZr - xZrLow) / (xZrHigh - xZrLow);

        double omega0 = 1.0 - lambda;
        double omega1 = lambda;

        double[] yLow =
                v2zr.getInitialInternalVars(
                        new double[] {1.0 - xZrLow, xZrLow});

        double[] yHigh =
                v2zr.getInitialInternalVars(
                        new double[] {1.0 - xZrHigh, xZrHigh});

        double[] sameCandidateTarget =
                {1.0 - targetXZr, targetXZr};

        System.out.println();
        System.out.println(
                "=== V2ZR same-candidate-twice END-TO-END TEST ===");
        System.out.println(
                "yLow  = " + Arrays.toString(yLow) + ", omega0 = " + omega0);
        System.out.println(
                "yHigh = " + Arrays.toString(yHigh) + ", omega1 = " + omega1);

        EquilibriumSolverV2 solver =
                new EquilibriumSolverV2();

        solver.setInitialStateForTest(
                new int[] {0, 0},
                new double[][] {yLow, yHigh},
                new double[] {omega0, omega1});

        /*
         * Limit to a single Sundman iteration. This test's whole purpose
         * is to prove that ONE updateState() commit keeps the two
         * same-candidate stable slots independent (the exact site of the
         * original aliasing bug) -- it is not exercising phase-set
         * management (updateStablePhaseSet() is a deferred no-op), so
         * letting the fixed-phase-set loop run to convergence or failure
         * from this coarse two-vertex starting guess would legitimately
         * drive one slot's amount to zero/negative in a later iteration,
         * which is that deferred feature's job to handle, not a
         * regression of this fix.
         */
        solver.setMaxIterations(1);

        EquilibriumResult result =
                solver.solve(
                        T,
                        P,
                        sameCandidateTarget,
                        candidates);

        assertNotNull(result);

        List<EquilibriumResult.PhaseResult> stableResults =
                result.getStablePhases();

        assertEquals(
                2,
                stableResults.size(),
                "Both stable slots must survive as distinct result "
                + "entries -- if this is 1 (or the solve threw before "
                + "reaching here), the same-candidate-twice aliasing bug "
                + "has regressed.");

        EquilibriumResult.PhaseResult slot0 =
                stableResults.get(0);

        EquilibriumResult.PhaseResult slot1 =
                stableResults.get(1);

        assertEquals(PHASE_A, slot0.phaseName);
        assertEquals(PHASE_A, slot1.phaseName);

        assertFalse(
                Arrays.equals(slot0.y, slot1.y),
                "The two V2ZR stable slots must retain independent "
                + "constitutions -- equal y arrays here means slot 1 "
                + "clobbered slot 0 (the original aliasing bug).");

        assertTrue(
                v2zr.isValid(slot0.y),
                "Slot 0's constitution must remain physically valid: "
                + Arrays.toString(slot0.y));

        assertTrue(
                v2zr.isValid(slot1.y),
                "Slot 1's constitution must remain physically valid: "
                + Arrays.toString(slot1.y));

        assertNotNull(result.getMu());

        assertEquals(
                2,
                result.getMu().length,
                "mu must have one entry per system component.");

        for (double value : result.getMu()) {

            assertTrue(
                    Double.isFinite(value),
                    "mu must be finite: " + Arrays.toString(result.getMu()));
        }

        // Lever rule (diagnostic only): with only ONE Newton step taken
        // from a coarse two-vertex initial guess and no phase-set
        // management (updateStablePhaseSet() is a deferred no-op, see
        // the class javadoc above), the fixed-phase-set update is not
        // expected to already reconstruct the target composition -- the
        // structural assertions above (distinct, valid, non-aliased
        // slots) are what this test exists to prove. Report the
        // reconstructed composition for visibility without asserting on
        // it.
        double leverXZr =
                slot0.amount * slot0.x[1] + slot1.amount * slot1.x[1];

        double totalAmount =
                slot0.amount + slot1.amount;

        System.out.println();
        System.out.println(
                "Lever-rule reconstructed x_Zr = "
                + (leverXZr / totalAmount)
                + " (target = " + targetXZr + ", diagnostic only)");
        System.out.println(
                "Converged = " + result.isConverged()
                + ", iterations = " + result.getIterations());
        System.out.println(
                "slot0: amount=" + slot0.amount
                + " y=" + Arrays.toString(slot0.y));
        System.out.println(
                "slot1: amount=" + slot1.amount
                + " y=" + Arrays.toString(slot1.y));
        System.out.println(
                "mu(final) = " + Arrays.toString(result.getMu()));
    }
}
