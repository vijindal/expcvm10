package calc.equil;

import org.junit.jupiter.api.Test;

import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;
import system.ports.EquilibriumResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feasibility demo: phase fractions from a T-scan (STEP) at fixed
 * composition, V-Zr binary. Uses {@link EquilibriumSolverV2#solve}'s
 * {@link EquilibriumResult.PhaseResult#amount}/{@code atoms()} at each
 * converged point.
 */
public class PhaseFractionTemperatureStepDemoTest {

    private static final String TDB = "data/VZR-re2.TDB";
    // Full candidate list (must include every phase in the TDB, not a
    // hand-picked subset) -- see VZr900KFullCandidatesCheckTest: leaving
    // out HCP_A3 previously produced a self-consistent but wrong
    // equilibrium (driving force converged to 0 within the truncated
    // candidate set, but didn't match the true OpenCalphad answer).
    private static final List<String> PHASE_NAMES =
            Arrays.asList("LIQUID", "BCC_A2", "HCP_A3", "V2ZR");
    private static final double P = 101325.0;

    // Overall composition held fixed across the T scan: (x_V, x_Zr).
    private static final double[] COMP_OVERALL = {0.55, 0.45};

    private static final double T_MIN = 900.0;
    private static final double T_MAX = 1500.0;
    private static final double T_STEP = 50.0;

    @Test
    void temperatureStep_phaseFractions_VZr() throws Exception {

        system.database.TdbParser parser = new system.database.TdbParser();
        parser.load(TDB);

        List<String> elements = Arrays.asList("V", "ZR");

        @SuppressWarnings("unchecked")
        List<CefGibbs> raw =
                (List<CefGibbs>) parser.buildPhaseModels(elements, PHASE_NAMES);

        List<GibbsEnergyModel> candidates = new java.util.ArrayList<>(raw);

        System.out.println();
        System.out.println("=== STEP scan: phase fractions vs T, V-Zr binary ===");
        System.out.println("Overall composition x = " + Arrays.toString(COMP_OVERALL)
                + "  (x_V, x_Zr), P = " + P + " Pa");
        System.out.println();
        System.out.printf(Locale.ROOT, "%8s  %-10s  %10s  %10s  %10s  %10s%n",
                "T(K)", "phase", "amount", "atoms", "f_fu", "f_atom");
        System.out.println("-".repeat(70));

        for (double t = T_MIN; t <= T_MAX + 1e-9; t += T_STEP) {

            EquilibriumSolverV2 solver = new EquilibriumSolverV2();
            EquilibriumResult result;
            try {
                result = solver.solve(t, P, COMP_OVERALL, candidates);
            } catch (RuntimeException e) {
                System.out.printf(Locale.ROOT, "%8.1f  <solve failed: %s>%n", t, e.getMessage());
                continue;
            }

            if (!result.isConverged()) {
                System.out.printf(Locale.ROOT, "%8.1f  <non-converged>%n", t);
                continue;
            }

            List<EquilibriumResult.PhaseResult> stable = result.getStablePhases();

            double totalAmount = 0.0;
            double totalAtoms = 0.0;
            for (EquilibriumResult.PhaseResult pr : stable) {
                totalAmount += pr.amount;
                totalAtoms += pr.atoms();
            }

            double sumFAtom = 0.0;
            boolean first = true;
            for (EquilibriumResult.PhaseResult pr : stable) {

                double fFu = pr.amount / totalAmount;
                double fAtom = pr.atoms() / totalAtoms;
                sumFAtom += fAtom;

                assertTrue(Double.isFinite(fAtom) && fAtom >= -1e-9,
                        "Phase fraction must be finite and non-negative: " + pr.phaseName
                                + " f_atom=" + fAtom + " at T=" + t);

                System.out.printf(Locale.ROOT, "%8.1f  %-10s  %10.4f  %10.4f  %10.4f  %10.4f%n",
                        first ? t : Double.NaN, pr.phaseName, pr.amount, pr.atoms(), fFu, fAtom);
                first = false;
            }

            assertTrue(Math.abs(sumFAtom - 1.0) < 1e-6,
                    "Atom-basis phase fractions must sum to 1 at T=" + t + " (got " + sumFAtom + ")");
        }

        System.out.println();
        System.out.println("Done. f_fu = formula-unit-basis fraction (amount_i / sum(amount)); "
                + "f_atom = real-atom-basis phase fraction (atoms_i / sum(atoms)) -- "
                + "the lever-rule-comparable quantity.");
    }
}
