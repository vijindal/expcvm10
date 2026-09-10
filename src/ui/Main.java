/*
 * Composition root: wires all layers and selects entry point (GUI or CLI).
 */
package ui;

import ui.layer.OptimizationUseCase;
import util.ConsoleLogger;
import util.LoggingConfig;
import util.OptimizationOutputAdapter;
import ui.cli.CliApp;
import ui.gui.GuiApp;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;

public class Main {

    public static void main(String[] args) throws IOException {
        // --- Initialize logging subsystem ---
        // NOTE: File logging temporarily disabled to reduce log file size
        LoggingConfig.init(Level.INFO, null, Level.WARNING);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LoggingConfig.shutdown();
            }
        });

        // --- Wire infrastructure adapters ---
        ConsoleLogger logger = new ConsoleLogger();
        OptimizationOutputAdapter outputAdapter = new OptimizationOutputAdapter();

        // --- Wire application use-cases ---
        OptimizationUseCase optimizationUseCase = new OptimizationUseCase(logger, outputAdapter);

        // --- Select entry point ---
        if (args.length > 0 && "--gui".equals(args[0])) {
            // Strip --gui from args before passing to GUI
            String[] guiArgs = Arrays.copyOfRange(args, 1, args.length);
            GuiApp gui = new GuiApp(optimizationUseCase);
            gui.launch(guiArgs);
        } else {
            CliApp cli = new CliApp(optimizationUseCase);
            cli.run(args);
        }
    }
}
