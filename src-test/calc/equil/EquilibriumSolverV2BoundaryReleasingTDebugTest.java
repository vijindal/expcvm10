package calc.equil;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a {@link GlobalEquilibriumMatrixAssembler
 * #convertToFixedPhaseAmountSystemReleasingT} Jacobian bug: only the
 * fixed phase's own phase-equilibrium row got a {@code -dG_dT} entry in
 * the freed column, leaving every OTHER stable phase's row assuming
 * {@code dG/dT=0} for that phase, even though temperature is a global
 * condition every stable phase responds to. That produced linear (ratio
 * ~0.9024/iteration), not quadratic, convergence, so
 * {@link EquilibriumSolverV2#solveBoundaryReleasingT} never reached
 * {@code tolerance=1e-10} within the 100-iteration cap even while
 * visibly homing in on the correct crossing -- this in turn caused
 * {@link calc.diagram.MapTracer} to silently fall back to the
 * unrefined grid point at every ZPF crossing found while walking a
 * TEMPERATURE axis, doubling up nodes around each true crossing.
 *
 * <p>Reproduces the Ag-Cu x(Cu)=0.05 LIQUID-appearance crossing
 * (OC reference: T=1176.13K) that surfaced the bug.
 */
public class EquilibriumSolverV2BoundaryReleasingTDebugTest {

    @Test
    void solveBoundaryReleasingTConvergesOnAgCuLiquidusCrossing() throws IOException {
        var system = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        double P = 101325.0;
        double xCu = 0.05;
        double[] comp = { 1.0 - xCu, xCu };

        // Seed: converged single-phase (FCC_A1) equilibrium just below the crossing.
        EquilibriumResult seed = new EquilibriumSolverV2().solve(
                1175.0, P, comp, system.phaseModels());
        assertTrue(seed.isConverged());
        assertEquals(1, seed.getStablePhases().size());

        // Fix LIQUID at amount 0 (its appearance boundary) and release T.
        EquilibriumSolverV2.BoundarySolveResult result =
                new EquilibriumSolverV2().solveBoundaryReleasingT(
                        1180.0, P, comp, system.phaseModels(), seed, "LIQUID", 0.0);

        assertEquals(1176.13, result.releasedComponentValue, 0.02);
    }
}
