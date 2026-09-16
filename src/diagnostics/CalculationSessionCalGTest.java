package diagnostics;

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
 * <p><b>Resolved (originally found 2026-09-10, confirmed fixed this
 * session via OC cross-check).</b> This test originally documented a
 * solver gap: {@code EquilibriumSolver} converged to a DISORDERED
 * constitution (G &asymp; -137.35 kJ/mol) instead of the true ordered V:ZR
 * end member (G &asymp; -150.69 kJ/mol, confirmed via {@link
 * CefLiteratureBaselineTest}'s direct CEF evaluation). The solver now
 * converges to the ordered minimum -- G=-150885.5871 J/mol.f.u.,
 * confirmed deterministic across repeated runs and independently matching
 * a real {@code oc7C} run at this exact condition (OC: G/N=-5.0295E+04
 * J/mol = -150885.6 J/mol.f.u. over 3 atoms/f.u.; see {@link
 * CliEquilibriumCommandTest}'s javadoc). Asserted directly below, not just
 * "a single V2ZR phase was found."
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
                && "V2ZR".equals(result.getStablePhases().get(0).phaseName)
                && Math.abs(result.getStablePhases().get(0).G - (-150885.5871)) < 0.01;
        System.out.println(pass
                ? "PASS: single-phase (V2ZR) equilibrium ran end-to-end through CalculationSession, "
                    + "converging to the OC-verified ordered minimum"
                : "FAIL: expected exactly one stable V2ZR phase at the OC-verified G value");
        if (!pass) {
            System.exit(1);
        }
    }
}
