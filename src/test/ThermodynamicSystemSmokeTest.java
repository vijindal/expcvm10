package test;

import ui.layer.EquilibriumUseCase;
import ui.request.CalculationRequest;
import system.ports.EquilibriumResult;

import java.util.ArrayList;

/**
 * One-shot smoke test for the Step 1 refactor (introduction of
 * {@code system.ThermodynamicSystem}): confirms
 * {@link EquilibriumUseCase#execute} still runs end-to-end through the new
 * shared construction path without throwing.
 *
 * <p>Not a literature baseline, and this particular (T, composition, phase
 * set) is known not to converge with the legacy solver's initial guess --
 * verified bit-for-bit identical (same iteration count, same final mu[])
 * against the pre-refactor code path. The point of this test is only to
 * confirm the refactor didn't change that behavior: same inputs must still
 * produce the same non-convergence, not a crash or a different trajectory.
 */
public class ThermodynamicSystemSmokeTest {

    public static void main(String[] args) throws Exception {
        CalculationRequest request = new CalculationRequest();
        request.setTdbFilePath("data/VZR-re2.TDB");
        ArrayList<String> elements = new ArrayList<>();
        elements.add("V");
        elements.add("ZR");
        request.setElements(elements);

        ArrayList<String> phases = new ArrayList<>();
        phases.add("LIQUID");
        phases.add("BCC_A2");
        phases.add("HCP_A3");
        request.setPhases(phases);

        request.setT(2000.0);
        request.setP(101325.0);

        ArrayList<ArrayList<Double>> comps = new ArrayList<>();
        ArrayList<Double> comp0 = new ArrayList<>();
        comp0.add(0.5);
        comp0.add(0.5);
        comps.add(comp0);
        request.setCompositions(comps);

        EquilibriumUseCase useCase = new EquilibriumUseCase();
        EquilibriumResult result = useCase.execute(request);

        System.out.println("converged=" + result.isConverged()
                + " iterations=" + result.getIterations());
        System.out.println("stable phases: " + result.getStablePhases().size());
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            System.out.println("  " + pr.phaseName + "  amount=" + pr.amount);
        }
        System.out.println("mu = " + java.util.Arrays.toString(result.getMu()));

        // Golden values captured from the pre-refactor code path (verified
        // bit-for-bit identical before/after introducing ThermodynamicSystem).
        boolean pass = result.getIterations() == 21
                && result.getStablePhases().size() == 1
                && Math.abs(result.getMu()[0] - 1.8149475367494476E10) < 1.0
                && Math.abs(result.getMu()[1] - (-3.77132929808263E7)) < 1.0;
        System.out.println(pass
                ? "PASS: EquilibriumUseCase -> ThermodynamicSystem wiring reproduces pre-refactor behavior exactly"
                : "FAIL: refactor changed solver behavior for identical inputs");
        if (!pass) System.exit(1);
    }
}
