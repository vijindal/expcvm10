package ui.cli;

import service.CalculationRequest;
import service.CalculationResult;
import service.FitParametersUseCase;
import service.ModelInfo;
import service.ValidateModelUseCase;
import service.SinglePointUseCase;
import service.StepCalculationUseCase;
import service.CalculationService;
import service.OptimizationService;
import service.PhaseDiagramRequest;
import service.PhaseDiagramResult;
import service.PhaseDiagramUseCase;
import thermocalc.diagram.AxisConfig;
import thermocalc.diagram.AxisConfig.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * CLI entry point for the application.
 *
 * Supported commands:
 *   (no args)           Default single-point calculation demo
 *   opt                 Run parameter optimization
 *   cal                 Run CalModel calculation
 *   diagram [options]   Calculate a phase diagram (text summary)
 *   inspect [options]   Inspect a TDB database file
 */
public class CliApp {

    private static final Logger LOG = Logger.getLogger(CliApp.class.getName());

    private final CalculationService  calculationService;
    private final OptimizationService optimizationService;

    public CliApp(CalculationService calculationService, OptimizationService optimizationService) {
        this.calculationService  = calculationService;
        this.optimizationService = optimizationService;
    }

    public void run(String[] args) throws IOException {
        LOG.info("CliApp.run() invoked with args: " + Arrays.toString(args));
        String cwd       = System.getProperty("user.dir");
        String expIn     = cwd + "/data/ExptData.txt";
        String phaseIn   = cwd + "/data/PhaseData.txt";
        String filePrefix= cwd + "/data/log";

        printBanner();

        long beg = System.currentTimeMillis();

        if (args.length == 0) {
            System.out.println("No arguments given. Running default single-point demo.");
            runDefaultCalculation(cwd);
        } else {
            switch (args[0]) {
                case "opt":
                    runOptimization(expIn, phaseIn, filePrefix);
                    break;
                case "cal":
                    runCalModel(expIn, phaseIn, filePrefix);
                    break;
                case "diagram":
                    runPhaseDiagram(args, cwd);
                    break;
                case "inspect":
                    runModelInspect(args, cwd);
                    break;
                default:
                    System.out.println("Unknown command: " + args[0]);
                    printUsage();
                    break;
            }
        }

        long end = System.currentTimeMillis();
        System.out.printf("%nCalculation took %.3f sec%n", (end - beg) / 1000.0);
    }

    // ──────────────────────────────────────────────────────────────────
    // Single-point (default demo)
    // ──────────────────────────────────────────────────────────────────

    private void runDefaultCalculation(String cwd) throws IOException {
        String tdbPath = cwd + "/data/tizr_kum_cvm.tdb";
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

        ArrayList<Double> x = new ArrayList<>();
        double temp = 1.0 / 3;
        x.add(temp); x.add(temp); x.add(temp);
        ArrayList<ArrayList<Double>> condX = new ArrayList<>();
        condX.add(x);
        request.setCompositions(condX);

        SinglePointUseCase useCase = new SinglePointUseCase(calculationService);
        CalculationResult result = useCase.execute(request);
        System.out.println("Result: " + result.getMessage());
    }

    // ──────────────────────────────────────────────────────────────────
    // Phase diagram
    // ──────────────────────────────────────────────────────────────────

    /**
     * Calculate a phase diagram from CLI flags.
     *
     * Usage:
     *   diagram [--tdb FILE] [--elements A,B] [--phases P1,P2,P3]
     *           [--type MAP|STEP]
     *           [--axis0 TYPE,min,max,step]  e.g. COMPOSITION,0,1,0.05
     *           [--axis1 TYPE,min,max,step]  e.g. TEMPERATURE,500,2000,50
     */
    private void runPhaseDiagram(String[] args, String cwd) {
        String  tdbPath  = cwd + "/data/tizr_kum.tdb";
        String  elements = "Ti,Zr";
        String  phases   = "HCP_A3,BCC_A2,LIQUID";
        String  type     = "MAP";
        String  axis0Str = "COMPOSITION,0,1,0.05";
        String  axis1Str = "TEMPERATURE,500,2000,50";

        // Parse flags
        for (int i = 1; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--tdb":      tdbPath  = resolvePath(args[++i], cwd); break;
                case "--elements": elements = args[++i]; break;
                case "--phases":   phases   = args[++i]; break;
                case "--type":     type     = args[++i].toUpperCase(); break;
                case "--axis0":    axis0Str = args[++i]; break;
                case "--axis1":    axis1Str = args[++i]; break;
                default: i++; break;  // skip unknown flag + its value
            }
        }

        System.out.println("─── Phase Diagram Calculation ───────────────────────");
        System.out.println("TDB:      " + tdbPath);
        System.out.println("Elements: " + elements);
        System.out.println("Phases:   " + phases);
        System.out.println("Type:     " + type);
        System.out.println("Axis 0:   " + axis0Str);
        System.out.println("Axis 1:   " + axis1Str);
        System.out.println("─────────────────────────────────────────────────────");

        PhaseDiagramRequest request = new PhaseDiagramRequest();
        request.setTdbFilePath(tdbPath);
        request.setElements(splitCsv(elements));
        request.setPhases(splitCsv(phases));
        request.setDiagramType("MAP".equalsIgnoreCase(type)
                ? PhaseDiagramRequest.DiagramType.MAP
                : PhaseDiagramRequest.DiagramType.STEP);
        request.setFixedP(101325.0);

        ArrayList<AxisConfig> axes = new ArrayList<>();
        AxisConfig a0 = parseAxisConfig(axis0Str, "Axis0");
        AxisConfig a1 = parseAxisConfig(axis1Str, "Axis1");
        if (a0 != null) axes.add(a0);
        if (a1 != null) axes.add(a1);
        request.setAxes(axes);

        try {
            PhaseDiagramResult result = new PhaseDiagramUseCase().execute(request);
            if (result.isComplete()) {
                System.out.println("✓ Calculation complete");
                System.out.printf("  Lines:  %d%n", result.getLines().size());
                System.out.printf("  Nodes:  %d%n", result.getNodes().size());
                printPhaseRegions(result);
            } else {
                System.out.println("✗ Failed: " + result.getMessage());
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private AxisConfig parseAxisConfig(String spec, String name) {
        String[] parts = spec.split(",");
        if (parts.length < 4) return null;
        try {
            String typeStr = parts[0].trim().toUpperCase();
            double min  = Double.parseDouble(parts[1].trim());
            double max  = Double.parseDouble(parts[2].trim());
            double step = Double.parseDouble(parts[3].trim());
            switch (typeStr) {
                case "TEMPERATURE": return new AxisConfig(name + " (K)", Type.TEMPERATURE, min, max, step);
                case "PRESSURE":    return new AxisConfig(name + " (Pa)", Type.PRESSURE, min, max, step);
                case "COMPOSITION": return new AxisConfig(name + " (X)", 1, min, max, step);
                default:            return null;
            }
        } catch (NumberFormatException e) { return null; }
    }

    private void printPhaseRegions(PhaseDiagramResult result) {
        java.util.Set<String> regions = new java.util.TreeSet<>();
        for (PhaseDiagramResult.LineSegment seg : result.getLines()) {
            regions.add(String.join(" + ", seg.stablePhases));
        }
        if (!regions.isEmpty()) {
            System.out.println("  Phase regions found:");
            for (String r : regions) System.out.println("    " + r);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Model inspect
    // ──────────────────────────────────────────────────────────────────

    /**
     * Inspect a TDB database file.
     *
     * Usage:
     *   inspect [--tdb FILE]
     */
    private void runModelInspect(String[] args, String cwd) {
        String tdbPath = cwd + "/data/tizr_kum_cvm.tdb";

        for (int i = 1; i < args.length - 1; i++) {
            if ("--tdb".equals(args[i])) { tdbPath = resolvePath(args[++i], cwd); }
        }

        System.out.println("─── TDB Inspection ──────────────────────────────────");
        System.out.println("File: " + tdbPath);

        ModelInfo info = calculationService.inspectModel(tdbPath, new String[]{});

        System.out.println("Exists:   " + info.isFileExists());
        if (info.getAvailableElements() != null) {
            System.out.println("Elements: " + String.join(", ", info.getAvailableElements()));
        }
        if (info.getAvailablePhases() != null) {
            System.out.println("Phases (" + info.getAvailablePhases().size() + "):");
            for (String p : info.getAvailablePhases()) System.out.println("  " + p);
        }
        if (info.getError() != null && !info.getError().isEmpty()) {
            System.out.println("Error: " + info.getError());
        }
        System.out.println("─────────────────────────────────────────────────────");
    }

    // ──────────────────────────────────────────────────────────────────
    // Legacy commands
    // ──────────────────────────────────────────────────────────────────

    private void runOptimization(String expIn, String phaseIn, String filePrefix) throws IOException {
        FitParametersUseCase useCase = new FitParametersUseCase(optimizationService);
        useCase.execute(expIn, phaseIn, filePrefix, 50);
    }

    private void runCalModel(String expIn, String phaseIn, String filePrefix) throws IOException {
        ValidateModelUseCase useCase = new ValidateModelUseCase(calculationService);
        useCase.execute(expIn, phaseIn);
    }

    // ──────────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────────

    private ArrayList<String> splitCsv(String in) {
        ArrayList<String> list = new ArrayList<>();
        if (in == null || in.trim().isEmpty()) return list;
        for (String s : in.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    private String resolvePath(String path, String cwd) {
        java.io.File f = new java.io.File(path);
        if (f.isAbsolute()) return path;
        return cwd + "/" + path;
    }

    private void printBanner() {
        System.out.println("─────────────────────────────────────────────");
        System.out.println("  expCVM 10 — Thermodynamic Workbench");
        System.out.println("  Started: " + new java.util.Date());
        System.out.println("─────────────────────────────────────────────");
    }

    private void printUsage() {
        System.out.println("Usage: java -cp build/classes ui.Main [command] [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  (no args)             Default single-point calculation demo");
        System.out.println("  opt                   Run parameter optimization");
        System.out.println("  cal                   Run CalModel calculation");
        System.out.println("  diagram [options]     Calculate a phase diagram");
        System.out.println("  inspect [--tdb FILE]  Inspect a TDB database");
        System.out.println("  --gui                 Launch the graphical interface");
        System.out.println();
        System.out.println("Diagram options:");
        System.out.println("  --tdb FILE                  TDB database path");
        System.out.println("  --elements Ti,Zr            Comma-separated elements");
        System.out.println("  --phases HCP_A3,BCC_A2,LIQ  Comma-separated phases");
        System.out.println("  --type MAP|STEP              Diagram type");
        System.out.println("  --axis0 COMPOSITION,0,1,0.05");
        System.out.println("  --axis1 TEMPERATURE,500,2000,50");
    }
}
