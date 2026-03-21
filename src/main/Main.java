
/*
 * Composition root: wires all layers and selects entry point (GUI or CLI).
 */
package main;

import application.service.CalculationService;
import application.service.OptimizationService;
import domain.port.PhaseFactory;
import infrastructure.factory.PhaseFactoryImpl;
import infrastructure.logging.ConsoleLogger;
import infrastructure.logging.LoggingConfig;
import infrastructure.output.OptimizationOutputAdapter;
import infrastructure.parser.TdbParser;
import presentation.cli.CliApp;
import presentation.gui.GuiApp;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;

public class Main {

    public static void main(String[] args) throws IOException {
        // --- Initialize logging subsystem ---
        String logDir = System.getProperty("user.dir") + "/data/";
        LoggingConfig.init(Level.INFO, logDir + "expcvm.log", Level.ALL);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LoggingConfig.shutdown();
            }
        });

        // --- Wire infrastructure adapters ---
        PhaseFactory phaseFactory = new PhaseFactoryImpl();
        TdbParser tdbParser = new TdbParser();
        ConsoleLogger logger = new ConsoleLogger();
        OptimizationOutputAdapter outputAdapter = new OptimizationOutputAdapter();

        // --- Wire application services ---
        CalculationService calculationService = new CalculationService(tdbParser);
        OptimizationService optimizationService = new OptimizationService(logger, outputAdapter);

        // --- Select entry point ---
        if (args.length > 0 && "--gui".equals(args[0])) {
            // Strip --gui from args before passing to GUI
            String[] guiArgs = Arrays.copyOfRange(args, 1, args.length);
            GuiApp gui = new GuiApp(calculationService, optimizationService);
            gui.launch(guiArgs);
        } else {
            CliApp cli = new CliApp(calculationService, optimizationService);
            cli.run(args);
        }
    }
}

