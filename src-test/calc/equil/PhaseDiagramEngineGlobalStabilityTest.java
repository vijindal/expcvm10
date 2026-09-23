package calc.equil;

import calc.diagram.PhaseDiagramEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calibration case for {@link PhaseDiagramEngine#isGloballyStable}: Ag-Cu
 * at T=700K, x(Cu)=0.5, forced via {@link
 * EquilibriumSolverV2#setInitialStateForTest} (package-private, hence
 * this test living in {@code calc.equil} rather than {@code
 * calc.diagram}) to converge as a SINGLE FCC_A1 phase instead of
 * splitting across its real miscibility gap -- a genuinely converged
 * but globally-unstable point, confirmed directly this session:
 * G/atom=-28521 forced vs. G/atom=-31585 from {@link
 * GridMinimizer}'s independent search, a ~10.7% relative difference,
 * far past {@code isGloballyStable}'s 1e-4 tolerance.
 */
@Tag("slow")
class PhaseDiagramEngineGlobalStabilityTest {

    @Test
    void rejectsAForcedSinglePhasePointInsideAMiscibilityGap() throws Exception {
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("FCC_A1")).phaseModels();
        double[] comp = { 0.5, 0.5 };

        GibbsEnergyModel fcc = candidates.get(0);
        double[] seedY = fcc.getInitialInternalVars(comp);

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        solver.setInitialStateForTest(new int[] { 0 }, new double[][] { seedY }, new double[] { 1.0 });
        EquilibriumResult forced = solver.solve(700.0, 101325.0, comp, candidates);

        assertTrue(forced.isConverged());
        assertEquals(1, forced.getStablePhases().size());
        assertFalse(PhaseDiagramEngine.isGloballyStable(forced, candidates));
    }

    @Test
    void acceptsTheGenuineMiscibilityGapSplitFoundByNormalSolve() throws Exception {
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("FCC_A1")).phaseModels();
        double[] comp = { 0.5, 0.5 };

        EquilibriumResult result = new EquilibriumSolverV2().solve(700.0, 101325.0, comp, candidates);

        assertTrue(result.isConverged());
        assertEquals(2, result.getStablePhases().size());
        assertTrue(PhaseDiagramEngine.isGloballyStable(result, candidates));
    }
}
