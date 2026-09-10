package test;

import ui.cli.CliApp;
import ui.layer.OptimizationUseCase;
import ui.layer.PhaseDiagramUseCase;
import ui.layer.SinglePointUseCase;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verifies the CLI's new {@code equilibrium} command (wired through
 * {@link session.CalculationSession}, per
 * {@code docs/plan-3layer-core-dataflow.md}) reproduces the same known-good
 * calG values as {@link CalculationSessionCalGTest} and
 * {@link CalculationApiServerTest}, driven this time from
 * {@link CliApp#run(String[])} exactly as a real CLI invocation would.
 */
public class CliEquilibriumCommandTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        Logger.getLogger("").setLevel(Level.SEVERE);   // quiet the solver's INFO logging for this check

        String output = runCli(new String[]{"equilibrium"});

        check(output.contains("Converged:   true"), "CLI reports convergence");
        check(output.contains("G=-137349.4480"), "CLI reports the known-good G value (-137349.4480)");
        check(output.contains("116674.3539155658"), "CLI reports the known-good mu[0] value");
        check(output.contains("V2ZR"), "CLI reports V2ZR as the stable phase");

        String customOutput = runCli(new String[]{"equilibrium", "--T", "1500", "--composition", "0.5,0.5"});
        check(customOutput.contains("T:           1500.0 K"), "custom --T flag is honored");
        check(customOutput.contains("Composition: [0.5, 0.5]"), "custom --composition flag is honored");

        if (failures == 0) {
            System.out.println("ALL CliEquilibriumCommand CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static void check(boolean condition, String description) {
        if (condition) {
            System.out.println("  PASS: " + description);
        } else {
            System.out.println("  FAIL: " + description);
            failures++;
        }
    }

    private static String runCli(String[] args) throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            CliApp cli = new CliApp(new SinglePointUseCase(),
                    new OptimizationUseCase(null, null), new PhaseDiagramUseCase());
            cli.run(args);
        } finally {
            System.setOut(original);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
