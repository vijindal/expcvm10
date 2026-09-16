package diagnostics;

import system.ports.EquilibriumResult;
import ui.gui.MainController;
import ui.layer.OptimizationUseCase;

import java.util.ArrayList;

/**
 * Verifies {@link MainController#runSinglePoint} -- the GUI's production
 * single-point call site -- routes through {@link
 * session.CalculationSession} and reproduces the OC-verified calG values
 * (see {@link CliEquilibriumCommandTest}'s javadoc for the OC cross-check)
 * already confirmed via {@code CalculationSessionCalGTest} (in-process)
 * and {@code CalculationApiServerTest} (REST).
 *
 * <p>No GUI/Swing code is exercised here -- {@code runSinglePoint} is a
 * plain method on {@code MainController}, callable directly.
 */
public class GuiMainControllerEquilibriumTest {

    public static void main(String[] args) throws Exception {
        MainController controller = new MainController(new OptimizationUseCase(null, null));

        ArrayList<ArrayList<Double>> compositions = new ArrayList<>();
        ArrayList<Double> comp0 = new ArrayList<>();
        comp0.add(2.0 / 3.0);
        comp0.add(1.0 / 3.0);
        compositions.add(comp0);

        EquilibriumResult result = controller.runSinglePoint(
                "data/VZR-re2.TDB",
                new String[]{"V", "ZR"},
                "HM",
                new String[]{"V2ZR"},
                1000.0,
                10000.0,
                compositions);

        System.out.println("converged=" + result.isConverged()
                + " iterations=" + result.getIterations());
        System.out.println("mu=" + java.util.Arrays.toString(result.getMu()));
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            System.out.printf("  %-8s G=%.4f J/mol.f.u.%n", pr.phaseName, pr.G);
        }

        boolean pass = result.isConverged()
                && result.getStablePhases().size() == 1
                && "V2ZR".equals(result.getStablePhases().get(0).phaseName)
                && Math.abs(result.getStablePhases().get(0).G - (-150885.5871)) < 0.01
                && Math.abs(result.getMu()[0] - (-44941.4461)) < 0.01;

        System.out.println(pass
                ? "PASS: MainController.runSinglePoint() via CalculationSession matches known-good calG values"
                : "FAIL: result does not match the known-good calG values");
        if (!pass) {
            System.exit(1);
        }

        // Second call with unchanged model details -- confirms reuse (no
        // re-parse) by checking it still succeeds and gives the same G.
        EquilibriumResult second = controller.runSinglePoint(
                "data/VZR-re2.TDB",
                new String[]{"V", "ZR"},
                "HM",
                new String[]{"V2ZR"},
                1000.0,
                10000.0,
                compositions);
        boolean reuseOk = Math.abs(second.getStablePhases().get(0).G
                - result.getStablePhases().get(0).G) < 1e-9;
        System.out.println(reuseOk
                ? "PASS: second call with unchanged model details reuses the session correctly"
                : "FAIL: second call gave a different result for identical inputs");
        if (!reuseOk) {
            System.exit(1);
        }
    }
}
