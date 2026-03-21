package gui;

import service.CalculationRequest;
import service.CalculationResult;
import service.FitParametersUseCase;
import service.ValidateModelUseCase;
import service.SinglePointUseCase;
import service.StepCalculationUseCase;
import service.CalculationService;
import service.OptimizationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CLI entry point for the application.
 * Receives configured use-case objects from the composition root (Main).
 * Parses command-line arguments and dispatches to appropriate workflows.
 */
public class CliApp {

    private static final Logger LOG = Logger.getLogger(CliApp.class.getName());
    private final CalculationService calculationService;
    private final OptimizationService optimizationService;

    public CliApp(CalculationService calculationService, OptimizationService optimizationService) {
        this.calculationService = calculationService;
        this.optimizationService = optimizationService;
    }

    /**
     * Run the CLI application with the given arguments.
     *
     * @param args command-line arguments:
     *   (none)  → default calculation demo
     *   "opt"   → optimization module
     *   "cal"   → calculation module (legacy CalModel)
     */
    public void run(String[] args) throws IOException {
        LOG.info("CliApp.run() invoked with args: " + Arrays.toString(args));
        String currentDirectory = System.getProperty("user.dir");
        String expIn = currentDirectory + "/data/ExptData.txt";
        String phaseIn = currentDirectory + "/data/PhaseData.txt";
        String filePrefix = currentDirectory + "/data/log";

        System.out.println("-------------Job/System Properties--------------------");
        System.out.println("  started on: " + new java.util.Date());
        System.out.println("-----------------------------------------------------");
        System.out.println("                expCVM-version: 10.00");
        System.out.println("-----------------------------------------------------");

        long beg = System.currentTimeMillis();

        if (args.length == 0) {
            System.out.println("No arguments were given. Running default calculation demo.");
            runDefaultCalculation(currentDirectory);
        } else {
            switch (args[0]) {
                case "opt":
                    runOptimization(expIn, phaseIn, filePrefix);
                    break;
                case "cal":
                    runCalModel(expIn, phaseIn, filePrefix);
                    break;
                default:
                    System.out.println("Unknown command: " + args[0]);
                    printUsage();
                    break;
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("#Calculations took " + (double) (end - beg) / 1000 + " sec");
    }

    private void runDefaultCalculation(String currentDirectory) throws IOException {
        String tdbPath = currentDirectory + "/data/tizr_kum_cvm.tdb";
        String[] elements = {"TI", "ZR"};

        CalculationRequest request = new CalculationRequest();
        request.setTdbFilePath(tdbPath);
        request.setElements(new ArrayList<>(Arrays.asList(elements)));
        request.setMethod("HM");
        ArrayList<String> phases = new ArrayList<>();
        phases.add("LIQUID");
        request.setPhases(phases);
        request.setT(500.0);
        request.setP(10000.0);
        ArrayList<ArrayList<Double>> condX = new ArrayList<>();
        ArrayList<Double> x = new ArrayList<>();
        double temp = 1.0 / 3;
        x.add(temp);
        x.add(temp);
        x.add(temp);
        condX.add(x);
        request.setCompositions(condX);

        SinglePointUseCase useCase = new SinglePointUseCase(calculationService);
        CalculationResult result = useCase.execute(request);
        System.out.println("Result: " + result.getMessage());
    }

    private void runOptimization(String expIn, String phaseIn, String filePrefix) throws IOException {
        FitParametersUseCase useCase = new FitParametersUseCase(optimizationService);
        useCase.execute(expIn, phaseIn, filePrefix, 50);
    }

    private void runCalModel(String expIn, String phaseIn, String filePrefix) throws IOException {
        ValidateModelUseCase useCase = new ValidateModelUseCase(calculationService);
        useCase.execute(expIn, phaseIn);
    }

    private void printUsage() {
        System.out.println("Usage: expcvm10 [command]");
        System.out.println("  (no args)  Default calculation demo");
        System.out.println("  opt        Run parameter optimization");
        System.out.println("  cal        Run CalModel calculation");
        System.out.println("  --gui      Launch GUI (pass to Main)");
    }
}
