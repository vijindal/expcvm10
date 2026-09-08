package test;

import calc.equil.EquilibriumSolver;
import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;
import system.ports.EquilibriumResult;
import system.ports.EquilibriumResult.PhaseResult;

import java.util.Arrays;
import java.util.List;

/**
 * Baseline 2: single-phase V2ZR equilibrium using the existing/original
 * EquilibriumSolver API.
 *
 * Purpose:
 *   Establish a regression reference for the old solver before replacing
 *   it with the new Sundman implementation.
 *
 * System:
 *   V-Zr
 *   Candidate phases: V2ZR only
 *   T = 1500 K
 *   P = 101325 Pa
 *   Overall composition: V=0.70, Zr=0.30
 *
 * Because V2ZR is the only candidate phase, this test isolates the
 * single-phase equilibrium treatment of the CEF model. It is NOT yet
 * a multiphase equilibrium test.
 */
public class V2ZrOldSolverBaselineTest {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    private static final double T = 1500.0;
    private static final double P = 101325.0;

    // Ordered exactly as [V, ZR].
    private static final double[] X_OVERALL = {0.70, 0.30};

    private static final double COMP_TOL = 1.0e-10;

    public static void main(String[] args) throws Exception {

        printHeader();

        // ------------------------------------------------------------
        // 1. Load real database and build the V2ZR CEF model.
        // ------------------------------------------------------------
        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phases = Arrays.asList(PHASE_NAME);

        @SuppressWarnings("unchecked")
        List<PhaseModel> phaseModels =
                (List<PhaseModel>) parser.buildPhaseModels(
                        elements, phases);

        if (phaseModels.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one " + PHASE_NAME
                    + " model, obtained " + phaseModels.size());
        }

        PhaseModel phase = phaseModels.get(0);

        if (phase.gibbs == null) {
            throw new IllegalStateException(
                    PHASE_NAME + " was not constructed as a CEF model.");
        }

        GibbsEnergyModel candidate =
                phase.toGibbsModel(elements);

        if (candidate == null) {
            throw new IllegalStateException(
                    "Could not create GibbsEnergyModel for " + PHASE_NAME);
        }

        // ------------------------------------------------------------
        // 2. Check input composition.
        // ------------------------------------------------------------
        double sum = 0.0;
        for (double x : X_OVERALL) {
            if (!Double.isFinite(x) || x < 0.0) {
                throw new IllegalArgumentException(
                        "Invalid overall composition: "
                        + Arrays.toString(X_OVERALL));
            }
            sum += x;
        }

        if (Math.abs(sum - 1.0) > COMP_TOL) {
            throw new IllegalArgumentException(
                    "Overall composition must sum to 1.0: sum=" + sum);
        }

        System.out.printf(
                "Overall composition [V,ZR] = %s%n",
                Arrays.toString(X_OVERALL));

        // ------------------------------------------------------------
        // 3. Run the EXISTING calc.equil.EquilibriumSolver.
        // ------------------------------------------------------------
        EquilibriumSolver solver = new EquilibriumSolver();

        EquilibriumResult result =
                solver.solve(
                        T,
                        P,
                        X_OVERALL.clone(),
                        Arrays.asList(candidate));

        // ------------------------------------------------------------
        // 4. Print regression information.
        // ------------------------------------------------------------
        System.out.println();
        System.out.println("Solver result");
        System.out.println("-------------");
        System.out.println("Converged   : " + result.isConverged());
        System.out.println("Iterations  : " + result.getIterations());
        System.out.println("mu [J/mol]  : " + Arrays.toString(result.getMu()));
        System.out.printf(
                "Total G     : %.12f J (for normalized system)%n",
                result.totalG());

        System.out.println();
        System.out.println("Stable phases");
        System.out.println("-------------");

        for (PhaseResult pr : result.getStablePhases()) {
            System.out.printf(
                    "%-10s amount=% .12e  G=% .12f  driving=% .12e%n",
                    pr.phaseName,
                    pr.amount,
                    pr.G,
                    pr.drivingForce);

            System.out.println(
                    "           x = " + Arrays.toString(pr.x));
            System.out.println(
                    "           y = " + Arrays.toString(pr.y));
        }

        System.out.println();
        System.out.println("Metastable phases");
        System.out.println("-----------------");

        for (PhaseResult pr : result.getMetastablePhases()) {
            System.out.printf(
                    "%-10s amount=% .12e  G=% .12f  driving=% .12e%n",
                    pr.phaseName,
                    pr.amount,
                    pr.G,
                    pr.drivingForce);
        }

        // ------------------------------------------------------------
        // 5. Basic sanity checks.
        // ------------------------------------------------------------
        if (result.getStablePhases().size() != 1) {
            throw new AssertionError(
                    "Expected exactly one stable phase, got "
                    + result.getStablePhases().size());
        }

        PhaseResult stable = result.getStablePhases().get(0);

        if (!PHASE_NAME.equalsIgnoreCase(stable.phaseName)) {
            throw new AssertionError(
                    "Expected stable phase " + PHASE_NAME
                    + ", got " + stable.phaseName);
        }

        if (Math.abs(stable.amount - 1.0) > 1.0e-8) {
            throw new AssertionError(
                    "Single-phase amount is not 1.0: "
                    + stable.amount);
        }

        if (stable.x.length != 2) {
            throw new AssertionError(
                    "Expected binary composition, got x length "
                    + stable.x.length);
        }

        // This is deliberately a diagnostic rather than the primary
        // acceptance criterion. The old solver may define/return x using
        // its own adapter convention, so we record it but do not silently
        // reinterpret it here.
        double xsum = 0.0;
        for (double x : stable.x) {
            if (!Double.isFinite(x)) {
                throw new AssertionError(
                        "Non-finite stable-phase composition: "
                        + Arrays.toString(stable.x));
            }
            xsum += x;
        }

        System.out.printf(
                "Stable-phase x sum = %.12f%n", xsum);

        if (!Double.isFinite(stable.G)) {
            throw new AssertionError(
                    "Non-finite stable-phase Gibbs energy.");
        }

        System.out.println();
        System.out.println(
                "PASS: Old EquilibriumSolver returned a finite "
                + "single-phase V2ZR result.");
        System.out.println();
        System.out.println(
                "IMPORTANT: Preserve this complete output as the "
                + "regression reference before replacing EquilibriumSolver.");
    }

    private static void printHeader() {
        System.out.println(
                "============================================================");
        System.out.println(
                "V2ZR single-phase old-solver baseline");
        System.out.println(
                "Reference model: data/VZR-re2.TDB");
        System.out.println(
                "Candidate phase: V2ZR only");
        System.out.println(
                "============================================================");
        System.out.printf(
                "T = %.2f K, P = %.0f Pa%n", T, P);
    }
}
