package test;

import session.CalculationSession;
import system.ports.EquilibriumResult;

import java.util.List;

/**
 * Step calculation (G vs T) for the V2ZR phase, run point-by-point through
 * {@link CalculationSession#calculateEquilibrium}, compared against the
 * existing Fig. 9 literature baseline
 * ({@link CefLiteratureBaselineTest}, digitized from Cui et al. 2016).
 *
 * <p>{@link CalculationSession#calculateStep} is intentionally an
 * unimplemented stub (no plain property-sampling engine exists yet -- see
 * {@code docs/plan-3layer-core-dataflow.md}, Step 3's "Four calculation
 * types" note). This test instead builds the system ONCE via
 * {@link CalculationSession#setModel} and calls
 * {@link CalculationSession#calculateEquilibrium} once per temperature
 * point -- the same "sweep one axis, sample the equilibrium at each point"
 * shape a real {@code calculateStep} would eventually automate, done
 * manually by the caller for now.
 *
 * <p><b>Expected outcome, given the calG finding (see
 * {@link CalculationSessionCalGTest}):</b> {@code EquilibriumSolver}
 * converges to a disordered constitution at this stoichiometric
 * composition rather than the true ordered V:ZR end member, so every point
 * in this scan is expected to disagree with the Fig. 9 literature curve by
 * roughly the same ~13 kJ/mol gap seen at T=1000 K -- this is the SAME
 * solver limitation, not a new one, now visible across a full temperature
 * range instead of one point. This test reports the comparison and does
 * NOT assert pass/fail against Fig. 9 (that assertion already exists and
 * passes via direct CEF evaluation in
 * {@link CefLiteratureBaselineTest}); it only confirms
 * {@code CalculationSession} runs a full step-shaped sweep end-to-end
 * without error, and documents by how much the solver's answer disagrees
 * with literature at each point.
 */
public class CalculationSessionStepCalGTest {

    private static final double[][] FIG9_SOLID_CURVE = {
        // { T [K], G [kJ/mol formula unit] } -- same digitized points as
        // CefLiteratureBaselineTest
        {  300, -41.69 }, {  350, -46.61 }, {  400, -52.90 }, {  450, -58.80 },
        {  500, -65.68 }, {  550, -72.57 }, {  600, -79.84 }, {  650, -87.32 },
        {  700, -96.17 }, {  750, -104.03 }, {  800, -113.08 }, {  850, -121.73 },
        {  900, -131.17 }, {  950, -139.23 }, { 1000, -150.05 }, { 1050, -160.28 },
        { 1100, -170.70 }, { 1150, -181.51 }, { 1200, -192.33 }, { 1250, -203.74 },
        { 1300, -215.73 }, { 1350, -225.37 }, { 1400, -237.17 }, { 1450, -248.57 },
        { 1500, -260.77 }, { 1550, -272.37 }, { 1600, -285.35 }, { 1650, -297.54 },
        { 1700, -310.32 }, { 1750, -322.71 }, { 1800, -335.89 }, { 1850, -348.87 },
        { 1900, -362.83 }, { 1950, -376.40 },
    };

    public static void main(String[] args) throws Exception {
        System.out.println("=== Step: V2ZR G vs T through CalculationSession, vs. Fig. 9 ===");

        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("V2ZR"));

        double xZr = 1.0 / 3.0;
        double[] compOverAll = {1.0 - xZr, xZr};
        double P = 101325.0;

        System.out.printf("%-10s %-18s %-16s %-12s%n",
                "T [K]", "Solver G [kJ/mol]", "Fig.9 [kJ/mol]", "Abs error");
        System.out.println("--------------------------------------------------------------");

        double maxAbsError = 0.0;
        int pointsRun = 0;

        for (double[] point : FIG9_SOLID_CURVE) {
            double T = point[0];
            double gFig9 = point[1];

            session.calculateEquilibrium(T, P, compOverAll);
            EquilibriumResult result = session.currentEquilibriumResult();
            if (result == null || result.getStablePhases().isEmpty()) {
                System.out.printf("%-10.2f %-18s %-16.4f %-12s%n", T, "(no result)", gFig9, "--");
                continue;
            }

            double gSolverKJ = result.getStablePhases().get(0).G / 1000.0;
            double error = Math.abs(gSolverKJ - gFig9);
            maxAbsError = Math.max(maxAbsError, error);
            pointsRun++;

            System.out.printf("%-10.2f %-18.4f %-16.4f %-12.4f%n", T, gSolverKJ, gFig9, error);
        }

        System.out.println();
        System.out.printf("Ran %d/%d points; max abs error vs Fig. 9 = %.4f kJ/mol%n",
                pointsRun, FIG9_SOLID_CURVE.length, maxAbsError);
        System.out.println(
                "(Large error expected -- see class Javadoc: EquilibriumSolver converges to a "
                + "disordered constitution at this stoichiometric composition, not the true "
                + "ordered end member. This is a known solver limitation, not asserted against "
                + "here.)");

        boolean ranAllPoints = pointsRun == FIG9_SOLID_CURVE.length;
        System.out.println(ranAllPoints
                ? "PASS: step-shaped sweep ran end-to-end through CalculationSession for all points"
                : "FAIL: not all points produced a result");
        if (!ranAllPoints) {
            System.exit(1);
        }
    }
}
