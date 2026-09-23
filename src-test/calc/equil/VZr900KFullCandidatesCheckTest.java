package calc.equil;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;
import system.ports.EquilibriumResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Re-check T=900K, x(V,Zr)=(0.55,0.45) with the FULL candidate list
 * (LIQUID, BCC_A2, HCP_A3, V2ZR) -- the earlier check only offered
 * {V2ZR, BCC_A2}, omitting HCP_A3, which OpenCalphad finds is actually
 * stable here (see docs/oc_reference_tests and
 * examples/macros/vzr_check_900K_output.txt in the OC checkout).
 *
 * <p><strong>Classified as INTEGRATION-HEAVY (multi-phase diagnostic check).</strong>
 */
@Tag("integration-heavy")
public class VZr900KFullCandidatesCheckTest {

    @Test
    void check900K_fullCandidates() throws Exception {
        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phaseNames = Arrays.asList("LIQUID", "BCC_A2", "HCP_A3", "V2ZR");

        List<GibbsEnergyModel> raw = parser.buildPhaseModels(elements, phaseNames);
        List<GibbsEnergyModel> candidates = new java.util.ArrayList<>(raw);

        System.out.println("Candidates: " + phaseNames);

        double T = 900.0, P = 101325.0;
        double[] comp = {0.55, 0.45};

        // GridMinimizer stable set with full candidates
        GridMinimizer gm = new GridMinimizer();
        EquilibriumState gstate = gm.initialize(candidates, T, P, comp);
        System.out.println("=== GridMinimizer stable set (full candidates) ===");
        for (PhaseRecord pr : gstate.stablePhases()) {
            System.out.println("  " + pr.phaseName() + "  y=" + Arrays.toString(pr.y)
                    + "  amount=" + pr.amount);
        }

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        EquilibriumResult result = solver.solve(T, P, comp, candidates);

        System.out.println();
        System.out.println("=== EquilibriumSolverV2 result (full candidates) ===");
        System.out.println("converged=" + result.isConverged() + " iterations=" + result.getIterations());
        System.out.println("mu=" + Arrays.toString(result.getMu()));

        System.out.println("-- stable phases --");
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            System.out.printf(Locale.ROOT,
                    "  %-10s amount=%10.6f atoms=%10.6f x=%s G=%12.4f df=%10.6e%n",
                    pr.phaseName, pr.amount, pr.atoms(), Arrays.toString(pr.x), pr.G, pr.drivingForce);
        }
        System.out.println("-- metastable phases --");
        for (EquilibriumResult.PhaseResult pr : result.getMetastablePhases()) {
            System.out.printf(Locale.ROOT,
                    "  %-10s amount=%10.6f x=%s df=%10.6e%n",
                    pr.phaseName, pr.amount, Arrays.toString(pr.x), pr.drivingForce);
        }

        System.out.println();
        System.out.println("totalGPerAtom = " + result.totalGPerAtom()
                + "  (OC reference: G/N = -44345.48 J/mol)");
    }
}
