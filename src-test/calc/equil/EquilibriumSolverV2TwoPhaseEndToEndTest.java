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
}
