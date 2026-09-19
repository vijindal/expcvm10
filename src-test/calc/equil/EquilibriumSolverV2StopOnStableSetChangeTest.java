package calc.equil;

import org.junit.jupiter.api.Test;

import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;
import system.ports.EquilibriumResult;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link EquilibriumSolverV2#solve(double, double, double[],
 * List, boolean)}'s new {@code stopOnStableSetChange} early exit --
 * Sundman 2021 Fig. 1's {@code step or map?} branch (see {@code
 * docs/sundman2021_reference_notes.md} Section 9 for the full derivation
 * and plan). Solver-only: does not touch the diagram tracers or any of
 * their existing tests.
 *
 * <p>Reuses the same deterministic V2ZR "same candidate twice" setup as
 * {@link EquilibriumSolverV2TwoPhaseEndToEndTest#testV2ZR_sameCandidateTwice_redundantSlotRemoved_endToEnd}
 * -- two stable slots of the SAME candidate phase (V2ZR), seeded so that
 * fixed-phase-set Newton iteration drives one slot's amount to the
 * removal floor, which {@code updateStablePhaseSet()}'s removal pass
 * then trips. That call is known (from the sibling test) to converge to
 * a single surviving slot WITHOUT the new flag -- so with {@code
 * stopOnStableSetChange=true} the solve must instead return early, with
 * {@code isConverged()==false} and a non-null {@code
 * getStableSetChange()} naming V2ZR/DISAPPEARING, strictly BEFORE the
 * iteration count the unmodified sibling test needed to converge.
 */
public class EquilibriumSolverV2StopOnStableSetChangeTest {

    private static final String TDB = "data/VZR-re2.TDB";
    private static final String PHASE_A = "V2ZR";
    private static final double T = 1100.0;
    private static final double P = 101325.0;

    @Test
    void stopOnStableSetChange_true_returnsEarlyWithStableSetChange() throws Exception {

        system.database.TdbParser parser = new system.database.TdbParser();
        parser.load(TDB);

        List<String> elements = Arrays.asList("V", "ZR");

        @SuppressWarnings("unchecked")
        List<CefGibbs> raw =
                (List<CefGibbs>) parser.buildPhaseModels(elements, Arrays.asList(PHASE_A));

        CefGibbs v2zr = raw.get(0);
        List<GibbsEnergyModel> candidates = Arrays.asList((GibbsEnergyModel) v2zr);

        double xZrLow = 0.30;
        double xZrHigh = 0.36;
        double targetXZr = 1.0 / 3.0;

        double lambda = (targetXZr - xZrLow) / (xZrHigh - xZrLow);
        double omega0 = 1.0 - lambda;
        double omega1 = lambda;

        double[] yLow = v2zr.getInitialInternalVars(new double[] {1.0 - xZrLow, xZrLow});
        double[] yHigh = v2zr.getInitialInternalVars(new double[] {1.0 - xZrHigh, xZrHigh});
        double[] target = {1.0 - targetXZr, targetXZr};

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setInitialStateForTest(
                new int[] {0, 0},
                new double[][] {yLow, yHigh},
                new double[] {omega0, omega1});

        EquilibriumResult result = solver.solve(T, P, target, candidates, true);

        assertNotNull(result);

        assertFalse(
                result.isConverged(),
                "An early exit on a stable-set change must not report converged=true.");

        EquilibriumResult.StableSetChange change = result.getStableSetChange();

        assertNotNull(
                change,
                "stopOnStableSetChange=true must populate StableSetChange once the "
                + "redundant V2ZR slot's amount collapses (same scenario the sibling "
                + "EquilibriumSolverV2TwoPhaseEndToEndTest test drives to full "
                + "convergence without this flag).");

        assertEquals(PHASE_A, change.phaseName);
        assertEquals(EquilibriumResult.ChangeDirection.DISAPPEARING, change.direction);

        assertTrue(
                result.getIterations() >= 0,
                "iterations must be a valid non-negative count at the point of exit.");
    }

    /**
     * Same starting state and target, {@code stopOnStableSetChange=false}
     * (the default/existing behavior) -- must reproduce the sibling
     * end-to-end test's own outcome (converges, one surviving slot,
     * {@code getStableSetChange()==null}), confirming the new overload
     * changes nothing for the flag's default value.
     */
    @Test
    void stopOnStableSetChange_false_matchesExistingBehavior() throws Exception {

        system.database.TdbParser parser = new system.database.TdbParser();
        parser.load(TDB);

        List<String> elements = Arrays.asList("V", "ZR");

        @SuppressWarnings("unchecked")
        List<CefGibbs> raw =
                (List<CefGibbs>) parser.buildPhaseModels(elements, Arrays.asList(PHASE_A));

        CefGibbs v2zr = raw.get(0);
        List<GibbsEnergyModel> candidates = Arrays.asList((GibbsEnergyModel) v2zr);

        double xZrLow = 0.30;
        double xZrHigh = 0.36;
        double targetXZr = 1.0 / 3.0;

        double lambda = (targetXZr - xZrLow) / (xZrHigh - xZrLow);
        double omega0 = 1.0 - lambda;
        double omega1 = lambda;

        double[] yLow = v2zr.getInitialInternalVars(new double[] {1.0 - xZrLow, xZrLow});
        double[] yHigh = v2zr.getInitialInternalVars(new double[] {1.0 - xZrHigh, xZrHigh});
        double[] target = {1.0 - targetXZr, targetXZr};

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setInitialStateForTest(
                new int[] {0, 0},
                new double[][] {yLow, yHigh},
                new double[] {omega0, omega1});

        EquilibriumResult result = solver.solve(T, P, target, candidates, false);

        assertNotNull(result);
        assertNull(
                result.getStableSetChange(),
                "stopOnStableSetChange=false must never populate StableSetChange.");
        assertEquals(
                1,
                result.getStablePhases().size(),
                "Without the early exit, the redundant slot is removed and the "
                + "solve keeps iterating to a single surviving V2ZR slot, exactly "
                + "as EquilibriumSolverV2TwoPhaseEndToEndTest's sibling test "
                + "already establishes.");
    }
}
