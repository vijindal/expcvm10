package calc.equil;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a bug found while stress-testing the diagram
 * tracer's drain loop (see {@code docs/roadmap_phase_diagrams.md}, "Two
 * new drain-loop bugs..."): {@link EquilibriumSolverV2#solveBoundary}'s
 * Newton iteration applied
 * NO physical bound to the released composition variable, unlike every
 * other iterated quantity (phase amounts floored at {@code
 * MIN_PHASE_AMOUNT}, site fractions clamped to {@code [1e-14, 1.0]}).
 * A poorly-seeded boundary solve could walk the released value
 * arbitrarily far outside {@code [0, 1]} while every other variable
 * stayed bounded, letting the iteration falsely "converge" (small
 * residuals in an already physically inconsistent system) instead of
 * throwing.
 *
 * <p><b>Concrete reproduction (found via the diagram tracer's drain
 * loop, confirmed directly against {@code solveBoundary} in isolation
 * this session):</b> Ag-Cu (SGTE-style {@code data/agcu.TDB}), fixing
 * FCC_A1's amount at zero and releasing x(Cu) (component index 1) at
 * T=1205K, seeded from a converged LIQUID-only equilibrium at a
 * DIFFERENT point (T=1210K, x(Cu)=0.0453) -- a genuinely poor starting
 * seed for this boundary, since the two points are not adjacent on the
 * same walk. Before the fix, this returned {@code
 * releasedComponentValue = 3.153} (physically impossible; mole
 * fractions must be in {@code [0, 1]}) with no exception. After the
 * fix, the same call now correctly throws ({@code
 * IllegalStateException}, "Boundary solve did not converge") rather
 * than returning a nonsensical answer disguised as a converged one.
 */
public class EquilibriumSolverV2SolveBoundaryClampTest {

    private static final String TDB = "data/agcu.TDB";
    private static final List<String> ELEMENTS = List.of("AG", "CU");
    private static final List<String> PHASES = List.of("LIQUID", "FCC_A1");

    private static List<GibbsEnergyModel> candidates() throws IOException {
        return ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();
    }

    @Test
    void poorlySeededBoundarySolveFailsRatherThanReturningAnImpossibleComposition() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();

        double[] seedComp = { 0.95, 0.04531499310579948 };
        EquilibriumResult seed = new EquilibriumSolverV2().solve(1210.0, 101325.0, seedComp, candidates);
        assertTrue(seed.isConverged());

        // Ask solveBoundary to fix FCC_A1 at zero and release x(Cu) at a
        // DIFFERENT temperature (1205K) using the T=1210K seed -- exactly
        // the mismatched-seed scenario that previously produced x(Cu)=3.15.
        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        double[] compAt1205 = seedComp.clone();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                solver.solveBoundary(1205.0, 101325.0, compAt1205, candidates, seed,
                        "FCC_A1", 0.0, 1));

        assertTrue(thrown.getMessage().contains("did not converge"),
                "should fail with a convergence error, not silently return an out-of-range composition");
    }

    @Test
    void everySuccessfulBoundarySolveReleasesAPhysicallyValidMoleFraction() throws IOException {
        // A well-seeded boundary solve (seed at the SAME T as the target,
        // adjacent composition) should still succeed and, whether it
        // succeeds or fails, never report a released value outside [0, 1].
        List<GibbsEnergyModel> candidates = candidates();

        double[] comp = { 0.95, 0.05 };
        EquilibriumResult seed = new EquilibriumSolverV2().solve(1150.0, 101325.0, comp, candidates);
        assertTrue(seed.isConverged());

        EquilibriumSolverV2.BoundarySolveResult result = new EquilibriumSolverV2().solveBoundary(
                1150.0, 101325.0, comp.clone(), candidates, seed, "LIQUID", 0.0, 1);

        assertTrue(result.releasedComponentValue >= 0.0 && result.releasedComponentValue <= 1.0,
                "a released mole fraction must lie in [0, 1], got " + result.releasedComponentValue);
    }
}
