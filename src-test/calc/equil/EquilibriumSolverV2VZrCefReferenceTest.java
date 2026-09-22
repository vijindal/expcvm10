package calc.equil;

import org.junit.jupiter.api.Test;

import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * All-CEF reference solve for V2ZR + BCC_A2 (both CEF, from {@code
 * data/VZR-re2.TDB}) at T=1000K, x(V,Zr)=(0.75,0.25). Establishes the
 * real two-phase bracket that {@link
 * EquilibriumSolverV2CefCvmTwoPhaseEndToEndTest} compares against once
 * BCC_A2 is swapped for its CVM model.
 */
class EquilibriumSolverV2VZrCefReferenceTest {

    @Test
    void v2zrAndCefBccReference() throws Exception {

        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");

        List<GibbsEnergyModel> raw = parser.buildPhaseModels(
                Arrays.asList("V", "ZR"), Arrays.asList("V2ZR", "BCC_A2"));

        List<GibbsEnergyModel> candidates = List.of(raw.get(0), raw.get(1));

        double T = 1000.0, P = 101325.0;
        double[] target = {0.75, 0.25};

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        EquilibriumResult result = solver.solve(T, P, target, candidates);

        assertTrue(result.isConverged());

        List<EquilibriumResult.PhaseResult> stable = result.getStablePhases();
        assertEquals(2, stable.size(),
                "All-CEF V2ZR+BCC_A2 must be a genuine two-phase equilibrium here.");
        for (EquilibriumResult.PhaseResult pr : stable) {
            assertTrue(pr.amount > 0.0 && Double.isFinite(pr.amount));
        }

        System.out.println("converged=" + result.isConverged()
                + " iterations=" + result.getIterations());
        for (EquilibriumResult.PhaseResult pr : stable) {
            System.out.printf(Locale.ROOT,
                    "  %-10s amount=%10.6f x=%s G=%12.4f%n",
                    pr.phaseName, pr.amount, Arrays.toString(pr.x), pr.G);
        }
    }
}
