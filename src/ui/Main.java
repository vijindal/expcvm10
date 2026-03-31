/*
 * Composition root: wires all layers and selects entry point (GUI or CLI).
 */
package ui;

import ui.layer.*;
import util.ConsoleLogger;
import util.LoggingConfig;
import util.OptimizationOutputAdapter;
import system.database.TdbParser;
import ui.cli.CliApp;
import ui.gui.GuiApp;
import contracts.DatabasePort;

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
        DatabasePort databasePort = new TdbParser();
        ConsoleLogger logger = new ConsoleLogger();
        OptimizationOutputAdapter outputAdapter = new OptimizationOutputAdapter();

        // --- Wire application use-cases ---
        EquilibriumUseCase equilibriumUseCase = new EquilibriumUseCase();
        PhaseDiagramUseCase phaseDiagramUseCase = new PhaseDiagramUseCase();
        SinglePointUseCase singlePointUseCase = new SinglePointUseCase();
        OptimizationUseCase optimizationUseCase = new OptimizationUseCase(logger, outputAdapter);
        ui.layer.ModelInspectionService modelInspectionService = new ui.layer.ModelInspectionService(databasePort);

        // --- Select entry point ---
        if (args.length > 0 && "--gui".equals(args[0])) {
            // Strip --gui from args before passing to GUI
            String[] guiArgs = Arrays.copyOfRange(args, 1, args.length);
            GuiApp gui = new GuiApp(singlePointUseCase, optimizationUseCase, phaseDiagramUseCase, modelInspectionService);
            gui.launch(guiArgs);
        } else {
            CliApp cli = new CliApp(singlePointUseCase, optimizationUseCase, phaseDiagramUseCase);
            cli.run(args);
        }
    }
}
