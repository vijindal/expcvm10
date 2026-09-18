package calc.equil;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link EquilibriumSolverV2#solveBoundary}/{@link
 * EquilibriumSolverV2#solveZpf} report the fixed phase's amount as
 * exactly {@code 0.0} -- Sundman 2021's ZPF condition (S3.2: "the phase
 * which appears or disappears is set fix with zero amount") -- rather
 * than a {@code MIN_PHASE_AMOUNT} floor.
 */
public class EquilibriumSolverV2ZpfExactZeroTest {

    private static final String TDB = "data/agcu.TDB";
    private static final List<String> ELEMENTS = List.of("AG", "CU");
    private static final List<String> PHASES = List.of("LIQUID", "FCC_A1");

    private static List<GibbsEnergyModel> candidates() throws IOException {
        return ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();
    }

    @Test
    void solveBoundaryReportsTheFixedPhaseAtExactlyZeroAmount() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();

        double[] comp = { 0.95, 0.05 };
        EquilibriumResult seed = new EquilibriumSolverV2().solve(1150.0, 101325.0, comp, candidates);
        assertTrue(seed.isConverged());

        EquilibriumSolverV2.BoundarySolveResult result = new EquilibriumSolverV2().solveBoundary(
                1150.0, 101325.0, comp.clone(), candidates, seed, "LIQUID", 0.0, 1);

        double liquidAmount = phaseAmount(result.equilibrium, "LIQUID");
        assertEquals(0.0, liquidAmount, 0.0, "fixed phase amount should be exactly 0.0, not a MIN_PHASE_AMOUNT floor");
    }

    @Test
    void solveZpfDispatchesToSolveBoundaryForCompositionAndAlsoReportsExactZero() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();

        double[] comp = { 0.95, 0.05 };
        EquilibriumResult seed = new EquilibriumSolverV2().solve(1150.0, 101325.0, comp, candidates);
        assertTrue(seed.isConverged());

        EquilibriumSolverV2.BoundarySolveResult viaSolveBoundary = new EquilibriumSolverV2().solveBoundary(
                1150.0, 101325.0, comp.clone(), candidates, seed, "LIQUID", 0.0, 1);

        EquilibriumSolverV2.BoundarySolveResult viaSolveZpf = new EquilibriumSolverV2().solveZpf(
                1150.0, 101325.0, comp.clone(), candidates, seed, "LIQUID", 0.0,
                EquilibriumSolverV2.ReleasedVariable.COMPOSITION, 1);

        assertEquals(0.0, phaseAmount(viaSolveZpf.equilibrium, "LIQUID"), 0.0);
        assertEquals(viaSolveBoundary.releasedComponentValue, viaSolveZpf.releasedComponentValue, 1.0e-6,
                "solveZpf(COMPOSITION) should match solveBoundary's own result");
    }

    private static double phaseAmount(EquilibriumResult eq, String phaseName) {
        for (EquilibriumResult.PhaseResult pr : eq.getStablePhases()) {
            if (pr.phaseName.equals(phaseName)) {
                return pr.amount;
            }
        }
        throw new AssertionError(phaseName + " not found among stable phases: " + eq.getStablePhases());
    }
}
