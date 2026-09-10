package test;

import session.CalculationSession;
import system.ports.EquilibriumResult;

import java.util.List;

/**
 * First real single-point calculation run through {@link CalculationSession}
 * end-to-end: calG (Gibbs energy) for the V2ZR phase at T=1000 K,
 * P=10000 Pa, x_Zr=1/3, in the V-Zr system.
 *
 * <p>Per the user's direction, "calG" is not a separate code path -- it is
 * {@link CalculationSession#calculateEquilibrium} with the
 * {@code ThermodynamicSystem} restricted to a single candidate phase
 * (V2ZR), so the equilibrium solver's one stable phase is V2ZR by
 * construction. This exercises the same {@code EquilibriumSolver} used for
 * multi-phase equilibria -- it "should handle all types of equilibrium
 * calculations: single phase or multiphase" (unchanged from that
 * direction), rather than adding a bespoke single-phase evaluation method.
 *
 * <p>V2ZR is (V,Zr)2(V,Zr): at the stoichiometric V:ZR end member, the
 * overall composition is 2 mol V + 1 mol Zr per formula unit, i.e.
 * x_Zr = 1/3, matching the composition requested here.
 *
 * <p><b>Known limitation, found by this test (2026-09-10), not fixed
 * here:</b> {@code EquilibriumSolver} converges in 1 iteration to a
 * DISORDERED constitution (partial V/Zr mixing on both sublattices) at
 * this composition, giving G &asymp; -137.35 kJ/mol -- but the true ordered
 * V:ZR end member (y=[1,0,0,1], confirmed via
 * {@link V2ZrGibbsLiteratureBaselineTest}'s direct CEF evaluation) gives
 * G &asymp; -150.69 kJ/mol, a deeper (more stable) minimum the solver misses.
 * At exactly one composition constraint with 2 internal degrees of freedom
 * on this phase, there is a one-parameter family of constitutions giving
 * the same overall x_Zr=1/3; the grid minimizer's initial guess apparently
 * doesn't land near the true (ordered, stoichiometric) minimum for this
 * case, and Newton iteration from there doesn't escape to it. This is a
 * genuine {@code EquilibriumSolver}/{@code GridMinimizer} gap, independent
 * of {@code CalculationSession} wiring -- flagged here, not fixed, per
 * explicit direction to continue with step/GUI wiring first. This test
 * therefore checks only that the solver runs and reports a single stable
 * V2ZR phase, NOT that its G matches the literature value.
 */
public class CalculationSessionCalGTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== calG: V2ZR Gibbs energy at T=1000K, P=10000Pa, x_Zr=1/3 ===");

        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("V2ZR"));

        double T = 1000.0;
        double P = 10000.0;
        double xZr = 1.0 / 3.0;
        double[] compOverAll = {1.0 - xZr, xZr};

        session.calculateEquilibrium(T, P, compOverAll);

        EquilibriumResult result = session.currentEquilibriumResult();
        if (result == null) {
            throw new IllegalStateException("calculateEquilibrium() produced no result");
        }

        System.out.println("converged = " + result.isConverged()
                + ", iterations = " + result.getIterations());
        System.out.println("stable phases: " + result.getStablePhases().size());
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            System.out.printf("  %-8s amount=%-10.6f G=%.4f J/mol.f.u.%n",
                    pr.phaseName, pr.amount, pr.G);
            System.out.println("    y = " + java.util.Arrays.toString(pr.y));
            System.out.println("    x = " + java.util.Arrays.toString(pr.x));
        }
        System.out.println("mu = " + java.util.Arrays.toString(result.getMu()));

        boolean pass = result.getStablePhases().size() == 1
                && "V2ZR".equals(result.getStablePhases().get(0).phaseName);
        System.out.println(pass
                ? "PASS: single-phase (V2ZR) equilibrium ran end-to-end through CalculationSession"
                : "FAIL: expected exactly one stable phase, V2ZR");
        if (!pass) {
            System.exit(1);
        }
    }
}
