package ui.cli;

import ui.layer.FitParametersUseCase;
import ui.layer.ValidateModelUseCase;
import ui.layer.OptimizationUseCase;
import ui.layer.ModelInspectionService;
import ui.layer.ModelBrowseService;
import system.database.TdbParser;
import system.ports.EquilibriumResult;
import session.CalculationSession;
import session.calctype.CalculationCatalog;
import session.calctype.CalculationGroup;
import session.calctype.CalculationKind;
import session.calctype.CalculationOutcome;
import session.calctype.ModelSelection;
import session.calctype.types.CoarseBinaryCalculationType;
import session.calctype.types.CoarseTernaryCalculationType;
import session.calctype.types.EquilibriumCalculationType;
import session.calctype.types.InitialStateCalculationType;
import session.calctype.types.MapCalculationType;
import session.calctype.types.PhaseDiagramCalculationType;
import session.calctype.types.StepCalculationType;
import calc.diagram.AxisConfig;
import calc.diagram.AxisConfig.Type;
import calc.diagram.PhaseDiagram;
import calc.diagram.DiagramLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * CLI entry point for the application.
 *
 * <p>Per the target data flow (README "Structure" / {@code
 * docs/dataflow_target.png}), the CLI's contact with the System and
 * Calculation layers is {@link session.calctype.CalculationCatalog}, never
 * {@link CalculationSession} directly: every calculation command below
 * builds a {@link ModelSelection} and a calculation type's own typed
 * params, then calls {@link CalculationCatalog#runCalculating} -- no
 * command calls {@code CalculationSession.setModel}/{@code calculate*}
 * itself, and none reaches into {@code calc.equil}/{@code calc.diagram}'s
 * solver classes directly either. This mirrors the GUI and REST API, which
 * go through the same catalog the same way -- the CLI is a thin
 * argument-parsing/printing shell over the Calculation layer's public
 * surface, exposing every {@code CalculationKind} as its own subcommand.
 * {@code opt}/{@code cal} (the menu's top-level group choice) are distinct
 * from the separate legacy {@code opt}/{@code cal} subcommands below, which
 * remain their own out-of-scope pathway.
 *
 * Supported commands:
 *   (no args)              Default single-point equilibrium demo
 *   opt                    Run parameter optimization (legacy pathway)
 *   cal                    Run CalModel calculation (legacy pathway)
 *   diagram [options]      Phase-diagram tracing (currently unimplemented
 *                          in CalculationSession -- see calculatePhaseDiagram)
 *   inspect [options]      Browse a TDB database file
 *   equilibrium [opts]     Single-point equilibrium (Newton-refined)
 *   initial-state [opts]   Grid-minimizer starting point only, no Newton step
 *   step [options]         Single-axis property scan (Algorithm B step branch)
 *   coarse-binary [opts]   2D grid of independent equilibrium samples
 *   coarse-ternary [opts]  2D grid over two composition axes
 *   map [options]          Two-axis ZPF phase-diagram map (Algorithms C1/C2/D)
 */
public class CliApp {

    private static final Logger LOG = Logger.getLogger(CliApp.class.getName());

    private final OptimizationUseCase optimizationUseCase;
    private final ModelInspectionService modelInspectionService;
    private final CalculationSession session = new CalculationSession();
    private final ModelBrowseService modelBrowseService = new ModelBrowseService(session);

    /**
     * Lazily created, reused for the whole process: {@link Prompter} wraps
     * {@code System.in} in a {@link java.io.BufferedReader}, which reads
     * ahead of what it hands back -- a second reader on the same stream
     * (e.g. one per interactive command) would find the first one's
     * read-ahead already consumed and hit EOF immediately. One instance
     * for the process's whole interactive lifetime (menu choice, then the
     * chosen command's own prompts) avoids that.
     */
    private Prompter prompter;

    public CliApp(OptimizationUseCase optimizationUseCase) {
        this.optimizationUseCase = optimizationUseCase;
        // Retained only for the legacy `cal` command (ValidateModelUseCase).
        this.modelInspectionService = new ModelInspectionService(new TdbParser());
    }

    private Prompter prompter(String cwd) {
        if (prompter == null) {
            prompter = new Prompter(cwd, modelBrowseService);
        }
        return prompter;
    }

    /**
     * @return process exit code: 0 on success, 2 on usage error (unknown
     *     command, missing/invalid option), 1 on a runtime failure.
     */
    public int run(String[] args) throws IOException {
        LOG.info("CliApp.run() invoked with args: " + Arrays.toString(args));

        if (args.length > 0 && isHelpFlag(args[0])) {
            printUsage();
            return 0;
        }

        String cwd       = System.getProperty("user.dir");
        String expIn     = cwd + "/data/ExptData.txt";
        String phaseIn   = cwd + "/data/PhaseData.txt";
        String filePrefix= cwd + "/data/log";

        printBanner();

        long beg = System.currentTimeMillis();
        int exitCode = 0;

        if (args.length == 0) {
            runMenu(cwd);
        } else if (args.length > 1 && isHelpFlag(args[1])) {
            printCommandUsage(args[0]);
        } else {
            exitCode = dispatch(args[0], args, cwd, expIn, phaseIn, filePrefix);
        }

        long end = System.currentTimeMillis();
        System.out.printf("%nCalculation took %.3f sec%n", (end - beg) / 1000.0);
        return exitCode;
    }

    /** Runs one named command; shared by {@link #run} and the no-args {@link #runMenu}. */
    private int dispatch(String command, String[] args, String cwd,
                          String expIn, String phaseIn, String filePrefix) throws IOException {
        switch (command) {
            case "opt":
                runOptimization(expIn, phaseIn, filePrefix);
                return 0;
            case "cal":
                runCalModel(expIn, phaseIn, filePrefix);
                return 0;
            case "diagram":
                runPhaseDiagram(args, cwd);
                return 0;
            case "inspect":
                runModelInspect(args, cwd);
                return 0;
            case "equilibrium":
                runEquilibriumViaSession(args, cwd);
                return 0;
            case "initial-state":
                runInitialState(args, cwd);
                return 0;
            case "step":
                runStep(args, cwd);
                return 0;
            case "coarse-binary":
                runCoarseBinary(args, cwd);
                return 0;
            case "coarse-ternary":
                runCoarseTernary(args, cwd);
                return 0;
            case "map":
                runMap(args, cwd);
                return 0;
            default:
                System.out.println("Unknown command: " + command);
                printUsage();
                return 2;
        }
    }

    private boolean isHelpFlag(String arg) {
        return "-h".equals(arg) || "--help".equals(arg) || "help".equals(arg);
    }

    /**
     * True if a calculation command should prompt for its inputs rather
     * than read {@code --flag value} pairs: either {@code -i}/
     * {@code --interactive} was passed explicitly, or the command was
     * given with no flags at all ({@code args} is just the command name),
     * in which case prompting is the default -- mirroring a GUI's "open
     * with nothing configured yet" behavior instead of silently running
     * on a hardcoded demo system.
     *
     * <p>Deliberately does not gate on {@link System#console()}: through
     * {@code ./gradlew run}, Gradle's {@code JavaExec} never attaches a
     * real console to the child JVM, so {@code System.console()} returns
     * {@code null} even in an actual interactive terminal session --
     * confirmed by running {@code ./gradlew run} directly. Gating on it
     * would silently break interactive/menu mode for the common case
     * (a real user, in a real terminal, via Gradle) to guard a rarer one
     * (truly empty piped stdin, where {@link Prompter} simply falls back
     * to each prompt's default when {@code readLine()} returns
     * {@code null} or blank -- the same graceful behavior as a blank
     * answer typed by hand).
     */
    private boolean isInteractive(String[] args) {
        return Arrays.asList(args).contains("-i")
                || Arrays.asList(args).contains("--interactive")
                || args.length == 1;
    }

    /**
     * Argument-less entry point: describes the program, lists every
     * calculation/browse command, and guides the chosen one's own
     * interactive ({@code -i}) prompts -- the CLI analogue of launching
     * the GUI with no setup required.
     */
    private void runMenu(String cwd) throws IOException {
        System.out.println("A command-line workbench for CALPHAD-style thermodynamic");
        System.out.println("equilibrium and phase-diagram calculations, built around");
        System.out.println("session.CalculationSession -- the same session the GUI and");
        System.out.println("REST API use.");
        System.out.println();
        System.out.println("Choose a calculation group:");
        System.out.println("  cal   Calculations from an already-parsed database (default)");
        System.out.println("  opt   Thermodynamic assessment / database creation (not implemented yet)");
        System.out.println();

        String groupChoice = prompter(cwd).str("Group", "cal");
        CalculationGroup group = "opt".equalsIgnoreCase(groupChoice.trim())
                ? CalculationGroup.ASSESS : CalculationGroup.CALCULATE;

        if (group == CalculationGroup.ASSESS) {
            CalculationOutcome.NotImplemented<Void> outcome =
                    CalculationCatalog.runAssessing(CalculationKind.ASSESSMENT, null);
            System.out.println();
            System.out.println(outcome.message());
            return;
        }

        System.out.println();
        System.out.println("Choose a calculation:");
        System.out.println("  1. equilibrium      Single-point equilibrium (Newton-refined)");
        System.out.println("  2. initial-state    Grid-minimizer starting point only");
        System.out.println("  3. step             Single-axis property scan");
        System.out.println("  4. coarse-binary    2D grid of independent equilibrium samples");
        System.out.println("  5. coarse-ternary   2D grid over two composition axes");
        System.out.println("  6. map              Two-axis ZPF phase-diagram map");
        System.out.println("  7. inspect          Browse a TDB database (elements / phases)");
        System.out.println("  0. quit");
        System.out.println();

        String choice = prompter(cwd).str("Choice", "1");

        String command;
        switch (choice.trim()) {
            case "1": command = "equilibrium";     break;
            case "2": command = "initial-state";   break;
            case "3": command = "step";            break;
            case "4": command = "coarse-binary";   break;
            case "5": command = "coarse-ternary";  break;
            case "6": command = "map";             break;
            case "7": command = "inspect";         break;
            case "0": return;
            default:
                System.out.println("Unrecognized choice: " + choice);
                return;
        }

        System.out.println();
        dispatch(command, new String[]{command, "-i"}, cwd, null, null, null);
    }

    private void printCommandUsage(String command) {
        switch (command) {
            case "diagram":
                System.out.println("Usage: diagram [options]");
                System.out.println("  --tdb FILE                  TDB database path");
                System.out.println("  --elements Ti,Zr            Comma-separated elements");
                System.out.println("  --phases HCP_A3,BCC_A2,LIQ  Comma-separated phases");
                System.out.println("  --axis0 COMPOSITION,0,1,0.05");
                System.out.println("  --axis1 TEMPERATURE,500,2000,50");
                break;
            case "inspect":
                System.out.println("Usage: inspect [options]");
                System.out.println("  --tdb FILE                  TDB database path");
                System.out.println("  --elements Ti,Zr            List phases for these elements");
                break;
            case "equilibrium":
                System.out.println("Usage: equilibrium [options]");
                System.out.println("  --tdb FILE                    TDB database path");
                System.out.println("  --elements V,ZR               Comma-separated elements");
                System.out.println("  --phases V2ZR                 Comma-separated phases (candidates)");
                System.out.println("  --T value                     Temperature (K)");
                System.out.println("  --P value                     Pressure (Pa)");
                System.out.println("  --composition x1,x2,...       Overall mole fractions");
                System.out.println("  -i, --interactive             Prompt for each value instead of flags");
                break;
            case "initial-state":
                System.out.println("Usage: initial-state [options]");
                System.out.println("  Grid-minimizer starting point only, no Newton refinement.");
                System.out.println("  --tdb FILE                    TDB database path");
                System.out.println("  --elements V,ZR               Comma-separated elements");
                System.out.println("  --phases V2ZR                 Comma-separated phases (candidates)");
                System.out.println("  --T value                     Temperature (K)");
                System.out.println("  --P value                     Pressure (Pa)");
                System.out.println("  --composition x1,x2,...       Overall mole fractions");
                break;
            case "step":
                System.out.println("Usage: step [options]");
                System.out.println("  Single-axis property scan (Algorithm B step branch).");
                System.out.println("  --tdb FILE                     TDB database path");
                System.out.println("  --elements AG,CU               Comma-separated elements");
                System.out.println("  --phases LIQUID,FCC_A1         Comma-separated phases (candidates)");
                System.out.println("  --axis TYPE,min,max,step       e.g. TEMPERATURE,1000,1200,5");
                System.out.println("                                 or COMPOSITION:i,min,max,step (i=element index)");
                System.out.println("  --T value                      Fixed T (K), used when axis type != TEMPERATURE");
                System.out.println("  --P value                      Fixed P (Pa), used when axis type != PRESSURE");
                System.out.println("  --composition x1,x2,...        Overall mole fractions (used when axis type != COMPOSITION)");
                break;
            case "coarse-binary":
                System.out.println("Usage: coarse-binary [options]");
                System.out.println("  2D grid of independent equilibrium samples (composition x temperature typical).");
                System.out.println("  --tdb FILE                     TDB database path");
                System.out.println("  --elements V,ZR                Comma-separated elements");
                System.out.println("  --phases V2ZR,BCC_A2           Comma-separated phases (candidates)");
                System.out.println("  --axisX TYPE,min,max,step      e.g. COMPOSITION:1,0.02,0.20,0.01");
                System.out.println("  --axisY TYPE,min,max,step      e.g. TEMPERATURE,1200,1600,50");
                System.out.println("  --T value                      Fixed T (K), used when axisY type != TEMPERATURE");
                System.out.println("  --P value                      Fixed P (Pa), used when neither axis is PRESSURE");
                System.out.println("  --composition x1,x2,...        Overall mole fractions (renormalized for swept axes)");
                break;
            case "coarse-ternary":
                System.out.println("Usage: coarse-ternary [options]");
                System.out.println("  2D grid over two composition axes; remaining component(s) renormalized.");
                System.out.println("  --tdb FILE                     TDB database path");
                System.out.println("  --elements CR,FE,MO            Comma-separated elements");
                System.out.println("  --phases LIQUID,A2             Comma-separated phases (candidates)");
                System.out.println("  --axisI COMPOSITION:i,min,max,step  e.g. COMPOSITION:1,0.0,0.6,0.1");
                System.out.println("  --axisJ COMPOSITION:j,min,max,step  e.g. COMPOSITION:2,0.0,0.6,0.1");
                System.out.println("  --T value                      Fixed T (K)");
                System.out.println("  --P value                      Fixed P (Pa)");
                System.out.println("  --composition x1,x2,...        Overall mole fractions (renormalized for swept axes)");
                break;
            case "map":
                System.out.println("Usage: map [options]");
                System.out.println("  Two-axis ZPF phase-diagram map (Algorithm C1 walk + exact C2 boundary solve).");
                System.out.println("  --tdb FILE                     TDB database path");
                System.out.println("  --elements AG,CU                Comma-separated elements");
                System.out.println("  --phases LIQUID,FCC_A1          Comma-separated phases (candidates)");
                System.out.println("  --axis0 TYPE,min,max,step       walked axis, e.g. TEMPERATURE,1000,1200,5");
                System.out.println("  --axis1 COMPOSITION:i,min,max,step  released axis; must be COMPOSITION");
                System.out.println("  --T value                       Fixed T (K), used when axis0 type != TEMPERATURE");
                System.out.println("  --P value                       Fixed P (Pa), used when axis0 type != PRESSURE");
                System.out.println("  --composition x1,x2,...         Overall mole fractions");
                break;
            case "opt":
                System.out.println("Usage: opt");
                System.out.println("  Run parameter optimization (legacy pathway); no options.");
                break;
            case "cal":
                System.out.println("Usage: cal");
                System.out.println("  Run CalModel calculation (legacy pathway); no options.");
                break;
            default:
                printUsage();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Phase diagram (via CalculationSession)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Calculate a phase diagram from CLI flags, routed through
     * {@link CalculationSession} ({@code setModel(...)} then
     * {@code calculatePhaseDiagram(...)}, result read via
     * {@code currentPhaseDiagram()}).
     *
     * Usage:
     *   diagram [--tdb FILE] [--elements A,B] [--phases P1,P2,P3]
     *           [--axis0 TYPE,min,max,step]  e.g. COMPOSITION,0,1,0.05
     *           [--axis1 TYPE,min,max,step]  e.g. TEMPERATURE,500,2000,50
     */
    private void runPhaseDiagram(String[] args, String cwd) throws IOException {
        String  tdbPath  = cwd + "/data/tizr_kum.tdb";
        String  elements = "Ti,Zr";
        String  phases   = "HCP_A3,BCC_A2,LIQUID";
        String  axis0Str = "COMPOSITION,0,1,0.05";
        String  axis1Str = "TEMPERATURE,500,2000,50";

        // Parse flags
        for (int i = 1; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--tdb":      tdbPath  = resolvePath(args[++i], cwd); break;
                case "--elements": elements = args[++i]; break;
                case "--phases":   phases   = args[++i]; break;
                case "--axis0":    axis0Str = args[++i]; break;
                case "--axis1":    axis1Str = args[++i]; break;
                default: i++; break;  // skip unknown flag + its value
            }
        }

        List<String> elementList = splitCsv(elements);
        List<String> phaseList   = splitCsv(phases);

        System.out.println("--- Phase Diagram Calculation (via CalculationSession) ---");
        System.out.println("TDB:      " + tdbPath);
        System.out.println("Elements: " + elementList);
        System.out.println("Phases:   " + phaseList);
        System.out.println("Axis 0:   " + axis0Str);
        System.out.println("Axis 1:   " + axis1Str);
        System.out.println("-------------------------------------------------------");

        AxisConfig a0 = parseAxisConfig(axis0Str, "Axis0");
        AxisConfig a1 = parseAxisConfig(axis1Str, "Axis1");
        List<AxisConfig> axes = new ArrayList<>();
        if (a0 != null) axes.add(a0);
        if (a1 != null) axes.add(a1);
        if (axes.isEmpty()) {
            System.out.println("Error: no valid axes parsed");
            return;
        }

        double[] startAxes = new double[axes.size()];
        for (int i = 0; i < axes.size(); i++) startAxes[i] = axes.get(i).min;
        int nc = elementList.size();
        double[] comp = new double[nc];
        Arrays.fill(comp, 1.0 / nc);

        ModelSelection model = new ModelSelection(tdbPath, elementList, phaseList);
        PhaseDiagramCalculationType.Params params = new PhaseDiagramCalculationType.Params(
                axes.toArray(new AxisConfig[0]), startAxes,
                /* fixedT */ 1000.0, /* fixedP */ 101325.0, comp);

        PhaseDiagram diagram;
        try {
            diagram = CalculationCatalog.runCalculating(
                    session, CalculationKind.PHASE_DIAGRAM, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        System.out.println("Calculation complete");
        System.out.printf("  Lines:  %d%n", diagram.getLines().size());
        System.out.printf("  Nodes:  %d%n", diagram.getNodes().size());
        printPhaseRegions(diagram);
    }

    // ──────────────────────────────────────────────────────────────────
    // Single-point equilibrium via CalculationSession
    // ──────────────────────────────────────────────────────────────────

    /**
     * Single-point equilibrium calculation routed through
     * {@link CalculationSession} -- the same session/model/calculation
     * lifecycle the REST API and the GUI use.
     *
     * <p>Usage:
     * <pre>
     *   equilibrium [--tdb FILE] [--elements A,B] [--phases P1,P2]
     *               [--T value] [--P value] [--composition x1,x2,...]
     * </pre>
     */
    private void runEquilibriumViaSession(String[] args, String cwd) throws IOException {
        boolean interactive = isInteractive(args);

        EquilibriumParams p = interactive
                ? EquilibriumParams.fromPrompts(prompter(cwd))
                : EquilibriumParams.fromArgs(args, cwd, this::resolvePath);

        System.out.println("--- Single-Point Equilibrium (via CalculationSession) ---");
        System.out.println("TDB:         " + p.tdbPath);
        System.out.println("Elements:    " + p.elements);
        System.out.println("Phases:      " + p.phases);
        System.out.println("T:           " + p.T + " K");
        System.out.println("P:           " + p.P + " Pa");
        System.out.println("Composition: " + Arrays.toString(p.composition));
        System.out.println("--------------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        EquilibriumCalculationType.Params params =
                new EquilibriumCalculationType.Params(p.T, p.P, p.composition);

        EquilibriumResult result;
        try {
            result = CalculationCatalog.runCalculating(
                    session, CalculationKind.EQUILIBRIUM, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printEquilibriumResult(result);
    }

    private static void printEquilibriumResult(EquilibriumResult result) {
        System.out.println("Converged:   " + result.isConverged()
                + "  (iterations=" + result.getIterations() + ")");
        System.out.println("mu:          " + Arrays.toString(result.getMu()));
        System.out.println("Stable phases:");
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            System.out.printf("  %-10s amount=%-12.6f G=%.4f J/mol.f.u.%n",
                    pr.phaseName, pr.amount, pr.G);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Grid-minimizer initial state (via CalculationSession)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Grid-minimizer starting point only (no Newton refinement), routed
     * through {@link CalculationSession#calculateInitialState}. Shares
     * {@link EquilibriumParams} with {@code equilibrium} since both take
     * the same (tdb, elements, phases, T, P, composition) inputs.
     */
    private void runInitialState(String[] args, String cwd) throws IOException {
        boolean interactive = isInteractive(args);

        EquilibriumParams p = interactive
                ? EquilibriumParams.fromPrompts(prompter(cwd))
                : EquilibriumParams.fromArgs(args, cwd, this::resolvePath);

        System.out.println("--- Grid-Minimizer Initial State (via CalculationSession) ---");
        System.out.println("TDB:         " + p.tdbPath);
        System.out.println("Elements:    " + p.elements);
        System.out.println("Phases:      " + p.phases);
        System.out.println("T:           " + p.T + " K");
        System.out.println("P:           " + p.P + " Pa");
        System.out.println("Composition: " + Arrays.toString(p.composition));
        System.out.println("---------------------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        InitialStateCalculationType.Params params =
                new InitialStateCalculationType.Params(p.T, p.P, p.composition);

        EquilibriumResult result;
        try {
            result = CalculationCatalog.runCalculating(
                    session, CalculationKind.INITIAL_STATE, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printEquilibriumResult(result);
    }

    // ──────────────────────────────────────────────────────────────────
    // Step (single-axis scan, via CalculationSession)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Single-axis property scan (Sundman 2021 Calphad 75, Algorithm B's
     * step branch), routed through {@link CalculationSession#calculateStep}.
     */
    private void runStep(String[] args, String cwd) throws IOException {
        boolean interactive = isInteractive(args);

        StepParams p = interactive
                ? StepParams.fromPrompts(prompter(cwd))
                : StepParams.fromArgs(args, cwd, this::resolvePath);

        System.out.println("--- Step Calculation (via CalculationSession) ---");
        System.out.println("TDB:         " + p.tdbPath);
        System.out.println("Elements:    " + p.elements);
        System.out.println("Phases:      " + p.phases);
        System.out.println("Axis:        " + p.axis.name);
        System.out.println("T:           " + p.T + " K");
        System.out.println("P:           " + p.P + " Pa");
        System.out.println("Composition: " + Arrays.toString(p.composition));
        System.out.println("---------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        StepCalculationType.Params params =
                new StepCalculationType.Params(p.axis, p.T, p.P, p.composition);

        ui.result.PhaseDiagramResult result;
        try {
            result = CalculationCatalog.runCalculating(
                    session, CalculationKind.STEP, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printPhaseDiagramResult(result);
    }

    /** Inputs for {@code step}, from flags or prompts -- see {@link EquilibriumParams}. */
    private static final class StepParams {
        final String tdbPath;
        final List<String> elements;
        final List<String> phases;
        final AxisConfig axis;
        final double T;
        final double P;
        final double[] composition;

        private StepParams(String tdbPath, List<String> elements, List<String> phases,
                            AxisConfig axis, double T, double P, double[] composition) {
            this.tdbPath = tdbPath;
            this.elements = elements;
            this.phases = phases;
            this.axis = axis;
            this.T = T;
            this.P = P;
            this.composition = composition;
        }

        static StepParams fromArgs(String[] args, String cwd,
                                    java.util.function.BiFunction<String, String, String> resolvePath) {
            String tdbPath        = cwd + "/data/agcu.TDB";
            String elementsStr    = "AG,CU";
            String phasesStr      = "LIQUID,FCC_A1";
            String axisStr        = "TEMPERATURE,1000,1200,5";
            double T              = 1000.0;
            double P              = 101325.0;
            String compositionStr = "0.5,0.5";

            for (int i = 1; i < args.length - 1; i++) {
                switch (args[i]) {
                    case "--tdb":         tdbPath        = resolvePath.apply(args[++i], cwd); break;
                    case "--elements":    elementsStr    = args[++i]; break;
                    case "--phases":      phasesStr      = args[++i]; break;
                    case "--axis":        axisStr        = args[++i]; break;
                    case "--T":           T              = Double.parseDouble(args[++i]); break;
                    case "--P":           P              = Double.parseDouble(args[++i]); break;
                    case "--composition": compositionStr = args[++i]; break;
                    default: i++; break;
                }
            }
            AxisConfig axis = parseAxisConfig(axisStr, "Axis");
            if (axis == null) {
                throw new IllegalArgumentException("could not parse --axis '" + axisStr + "'");
            }
            return new StepParams(tdbPath, splitCsv(elementsStr), splitCsv(phasesStr),
                    axis, T, P, parseDoubleCsv(compositionStr));
        }

        static StepParams fromPrompts(Prompter p) throws IOException {
            String tdbPath = p.tdbPath("agcu.TDB");
            List<String> elements = p.pickElements(tdbPath);
            List<String> phases = p.pickPhases(tdbPath, elements);
            AxisConfig axis = p.axis("Axis (TYPE,min,max,step)", "TEMPERATURE,1000,1200,5");
            double T = p.num("Fixed T (K, used if axis type != TEMPERATURE)", 1000.0);
            double P = p.num("Fixed P (Pa, used if axis type != PRESSURE)", 101325.0);
            String compositionStr = p.str(
                    "Overall composition (mole fractions, used if axis type != COMPOSITION)", "0.5,0.5");
            return new StepParams(tdbPath, elements, phases,
                    axis, T, P, parseDoubleCsv(compositionStr));
        }
    }

    /**
     * Inputs shared by {@code coarse-binary}, {@code coarse-ternary}, and
     * {@code map} -- all three take (tdb, elements, phases, two axes, fixed
     * T/P, overall composition); only which {@code CalculationSession}
     * method consumes {@code axis1}/{@code axis2} differs. See
     * {@link EquilibriumParams} for the flags-vs-prompts pattern.
     */
    private static final class CoarseGridParams {
        final String tdbPath;
        final List<String> elements;
        final List<String> phases;
        final AxisConfig axis1;
        final AxisConfig axis2;
        final double T;
        final double P;
        final double[] composition;

        private CoarseGridParams(String tdbPath, List<String> elements, List<String> phases,
                                  AxisConfig axis1, AxisConfig axis2,
                                  double T, double P, double[] composition) {
            this.tdbPath = tdbPath;
            this.elements = elements;
            this.phases = phases;
            this.axis1 = axis1;
            this.axis2 = axis2;
            this.T = T;
            this.P = P;
            this.composition = composition;
        }

        void printSummary(String axis1Label, String axis2Label) {
            System.out.println("TDB:         " + tdbPath);
            System.out.println("Elements:    " + elements);
            System.out.println("Phases:      " + phases);
            System.out.println(axis1Label + ":      " + axis1.name);
            System.out.println(axis2Label + ":      " + axis2.name);
            System.out.println("T:           " + T + " K");
            System.out.println("P:           " + P + " Pa");
            System.out.println("Composition: " + Arrays.toString(composition));
        }

        static CoarseGridParams fromArgs(String[] args, String cwd,
                                          java.util.function.BiFunction<String, String, String> resolvePath,
                                          String defaultTdbRelPath, String defaultElements, String defaultPhases,
                                          String axis1Flag, String defaultAxis1Spec,
                                          String axis2Flag, String defaultAxis2Spec,
                                          double defaultT, String defaultComposition) {
            String tdbPath        = cwd + "/" + defaultTdbRelPath;
            String elementsStr    = defaultElements;
            String phasesStr      = defaultPhases;
            String axis1Spec      = defaultAxis1Spec;
            String axis2Spec      = defaultAxis2Spec;
            double T              = defaultT;
            double P              = 101325.0;
            String compositionStr = defaultComposition;

            for (int i = 1; i < args.length - 1; i++) {
                String flag = args[i];
                if ("--tdb".equals(flag))              tdbPath        = resolvePath.apply(args[++i], cwd);
                else if ("--elements".equals(flag))    elementsStr    = args[++i];
                else if ("--phases".equals(flag))      phasesStr      = args[++i];
                else if (flag.equals(axis1Flag))       axis1Spec      = args[++i];
                else if (flag.equals(axis2Flag))       axis2Spec      = args[++i];
                else if ("--T".equals(flag))           T              = Double.parseDouble(args[++i]);
                else if ("--P".equals(flag))           P              = Double.parseDouble(args[++i]);
                else if ("--composition".equals(flag)) compositionStr = args[++i];
                else i++;
            }
            AxisConfig axis1 = parseAxisConfig(axis1Spec, axis1Flag);
            AxisConfig axis2 = parseAxisConfig(axis2Spec, axis2Flag);
            if (axis1 == null || axis2 == null) {
                throw new IllegalArgumentException(
                        "could not parse " + axis1Flag + "/" + axis2Flag);
            }
            return new CoarseGridParams(tdbPath, splitCsv(elementsStr), splitCsv(phasesStr),
                    axis1, axis2, T, P, parseDoubleCsv(compositionStr));
        }

        static CoarseGridParams fromPrompts(Prompter p, String defaultTdbRelPath,
                                             String defaultElements, String defaultPhases,
                                             String axis1Label, String defaultAxis1Spec,
                                             String axis2Label, String defaultAxis2Spec,
                                             double defaultT, String defaultComposition) throws IOException {
            String tdbPath = p.tdbPath(defaultTdbRelPath.substring(defaultTdbRelPath.lastIndexOf('/') + 1));
            List<String> elements = p.pickElements(tdbPath);
            List<String> phases = p.pickPhases(tdbPath, elements);
            AxisConfig axis1 = p.axis(axis1Label + " (TYPE,min,max,step)", defaultAxis1Spec);
            AxisConfig axis2 = p.axis(axis2Label + " (TYPE,min,max,step)", defaultAxis2Spec);
            double T = p.num("Fixed T (K)", defaultT);
            double P = p.num("Fixed P (Pa)", 101325.0);
            String compositionStr = p.str("Overall composition (mole fractions)", defaultComposition);
            return new CoarseGridParams(tdbPath, elements, phases,
                    axis1, axis2, T, P, parseDoubleCsv(compositionStr));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Coarse binary / ternary diagrams (via CalculationSession)
    // ──────────────────────────────────────────────────────────────────

    /**
     * 2D grid of independent equilibrium samples over two axes, routed
     * through {@link CalculationSession#calculateCoarseBinaryDiagram}.
     */
    private void runCoarseBinary(String[] args, String cwd) throws IOException {
        boolean interactive = isInteractive(args);

        CoarseGridParams p = interactive
                ? CoarseGridParams.fromPrompts(prompter(cwd), "data/VZR-re2.TDB",
                        "V,ZR", "V2ZR,BCC_A2", "AxisX", "COMPOSITION:1,0.02,0.20,0.01",
                        "AxisY", "TEMPERATURE,1200,1600,50", 1500.0, "1.0,0.0")
                : CoarseGridParams.fromArgs(args, cwd, this::resolvePath, "data/VZR-re2.TDB",
                        "V,ZR", "V2ZR,BCC_A2", "--axisX", "COMPOSITION:1,0.02,0.20,0.01",
                        "--axisY", "TEMPERATURE,1200,1600,50", 1500.0, "1.0,0.0");

        System.out.println("--- Coarse Binary Diagram (via CalculationSession) ---");
        p.printSummary("Axis X", "Axis Y");
        System.out.println("-------------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        CoarseBinaryCalculationType.Params params =
                new CoarseBinaryCalculationType.Params(p.axis1, p.axis2, p.T, p.P, p.composition);

        ui.result.CoarseDiagramResult result;
        try {
            result = CalculationCatalog.runCalculating(
                    session, CalculationKind.COARSE_BINARY, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printCoarseDiagramResult(result);
    }

    /**
     * 2D grid over two composition axes (third+ component(s) renormalized),
     * routed through {@link CalculationSession#calculateCoarseTernaryDiagram}.
     */
    private void runCoarseTernary(String[] args, String cwd) throws IOException {
        boolean interactive = isInteractive(args);

        CoarseGridParams p = interactive
                ? CoarseGridParams.fromPrompts(prompter(cwd), "data/Cr-Fe-Mo.TDB",
                        "CR,FE,MO", "LIQUID,A2", "AxisI", "COMPOSITION:1,0.0,0.6,0.1",
                        "AxisJ", "COMPOSITION:2,0.0,0.6,0.1", 1800.0, "1.0,0.0,0.0")
                : CoarseGridParams.fromArgs(args, cwd, this::resolvePath, "data/Cr-Fe-Mo.TDB",
                        "CR,FE,MO", "LIQUID,A2", "--axisI", "COMPOSITION:1,0.0,0.6,0.1",
                        "--axisJ", "COMPOSITION:2,0.0,0.6,0.1", 1800.0, "1.0,0.0,0.0");

        System.out.println("--- Coarse Ternary Diagram (via CalculationSession) ---");
        p.printSummary("Axis I", "Axis J");
        System.out.println("--------------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        CoarseTernaryCalculationType.Params params =
                new CoarseTernaryCalculationType.Params(p.axis1, p.axis2, p.T, p.P, p.composition);

        ui.result.CoarseDiagramResult result;
        try {
            result = CalculationCatalog.runCalculating(
                    session, CalculationKind.COARSE_TERNARY, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printCoarseDiagramResult(result);
    }

    private static void printCoarseDiagramResult(ui.result.CoarseDiagramResult result) {
        System.out.println("Calculation complete: " + result.isComplete());
        if (!result.getMessage().isEmpty()) {
            System.out.println("Message: " + result.getMessage());
        }
        System.out.printf("  Points: %d%n", result.getPoints().size());
        java.util.Set<String> regions = new java.util.TreeSet<>();
        int unconverged = 0;
        for (ui.result.CoarseDiagramResult.GridPoint pt : result.getPoints()) {
            if (!pt.converged) { unconverged++; continue; }
            regions.add(String.join(" + ", pt.stablePhases));
        }
        System.out.printf("  Unconverged points: %d%n", unconverged);
        if (!regions.isEmpty()) {
            System.out.println("  Phase regions found:");
            for (String r : regions) System.out.println("    " + r);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Map (two-axis ZPF phase diagram, via CalculationSession)
    // ──────────────────────────────────────────────────────────────────

    /**
     * True two-axis ZPF phase-diagram map (Sundman 2021 Calphad 75,
     * Algorithms C1/C2/D), routed through
     * {@link CalculationSession#calculateMap}. {@code axis1} (released,
     * solved for exactly at each boundary) must be COMPOSITION.
     */
    private void runMap(String[] args, String cwd) throws IOException {
        boolean interactive = isInteractive(args);

        CoarseGridParams p = interactive
                ? CoarseGridParams.fromPrompts(prompter(cwd), "data/agcu.TDB",
                        "AG,CU", "LIQUID,FCC_A1", "Axis0", "TEMPERATURE,1000,1200,5",
                        "Axis1", "COMPOSITION:1,0.0,1.0,0.01", 1000.0, "0.5,0.5")
                : CoarseGridParams.fromArgs(args, cwd, this::resolvePath, "data/agcu.TDB",
                        "AG,CU", "LIQUID,FCC_A1", "--axis0", "TEMPERATURE,1000,1200,5",
                        "--axis1", "COMPOSITION:1,0.0,1.0,0.01", 1000.0, "0.5,0.5");

        System.out.println("--- Map Calculation (via CalculationSession) ---");
        p.printSummary("Axis 0", "Axis 1");
        System.out.println("-------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        MapCalculationType.Params params =
                new MapCalculationType.Params(p.axis1, p.axis2, p.T, p.P, p.composition);

        ui.result.PhaseDiagramResult result;
        try {
            result = CalculationCatalog.runCalculating(
                    session, CalculationKind.MAP, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printPhaseDiagramResult(result);
    }

    private static void printPhaseDiagramResult(ui.result.PhaseDiagramResult result) {
        System.out.println("Calculation complete: " + result.isComplete());
        if (!result.getMessage().isEmpty()) {
            System.out.println("Message: " + result.getMessage());
        }
        System.out.printf("  Lines: %d%n", result.getLines().size());
        System.out.printf("  Nodes: %d%n", result.getNodes().size());
        java.util.Set<String> regions = new java.util.TreeSet<>();
        for (ui.result.PhaseDiagramResult.LineSegment line : result.getLines()) {
            regions.add(line.label());
        }
        if (!regions.isEmpty()) {
            System.out.println("  Lines found:");
            for (String r : regions) System.out.println("    " + r);
        }
        for (ui.result.PhaseDiagramResult.NodePoint node : result.getNodes()) {
            if (node.type == ui.result.PhaseDiagramResult.NodePoint.Type.INVARIANT) {
                System.out.println("  Invariant node: " + String.join("+", node.stablePhases)
                        + " at " + Arrays.toString(node.axisValues));
            }
        }
    }

    private static double[] parseDoubleCsv(String in) {
        String[] parts = in.split(",");
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Double.parseDouble(parts[i].trim());
        }
        return values;
    }

    /**
     * Reads stdin answers for interactive ({@code -i}/{@code --interactive})
     * command modes: one method per value shape, each showing the current
     * default in brackets and keeping it on blank input.
     */
    private static final class Prompter {
        private final java.io.BufferedReader in;
        private final String cwd;
        private final ModelBrowseService browse;

        Prompter(String cwd, ModelBrowseService browse) {
            this.in = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            this.cwd = cwd;
            this.browse = browse;
        }

        String str(String label, String current) throws IOException {
            System.out.print(label + " [" + current + "]: ");
            System.out.flush();
            String line = in.readLine();
            return (line == null || line.trim().isEmpty()) ? current : line.trim();
        }

        double num(String label, double current) throws IOException {
            return Double.parseDouble(str(label, String.valueOf(current)));
        }

        /**
         * Prompts for a TDB filename only (e.g. {@code agcu.TDB}) -- the
         * containing directory is always this project's {@code data/}
         * folder, fixed relative to {@code cwd}, never shown or asked
         * for. {@code defaultFileName} is a bare filename, not a path.
         * Re-prompts if the resulting file doesn't exist.
         */
        String tdbPath(String defaultFileName) throws IOException {
            while (true) {
                String fileName = str("TDB file (in data/)", defaultFileName);
                String fullPath = cwd + "/data/" + fileName;
                if (new java.io.File(fullPath).isFile()) {
                    return fullPath;
                }
                System.out.println("  No such file: data/" + fileName + " -- try again.");
            }
        }

        /**
         * Shows the elements actually selectable from {@code tdbPath}
         * (via {@link ModelBrowseService#selectableElements}, the same
         * bridge the GUI and REST API use, pseudo-elements already
         * filtered out) and lets the user pick a comma-separated subset;
         * blank input (just pressing Enter) selects all of them --
         * mirroring the GUI's default.
         */
        List<String> pickElements(String tdbPath) throws IOException {
            List<String> available;
            try {
                available = browse.selectableElements(tdbPath);
            } catch (IOException e) {
                System.out.println("  Could not read elements from " + tdbPath + ": " + e.getMessage());
                return List.of();
            }
            String allCsv = String.join(",", available);
            System.out.println("  Available elements: " + available);
            while (true) {
                String chosenCsv = str("Elements (comma-separated, Enter for all)", allCsv);
                List<String> matched = matchAgainst(splitCsv(chosenCsv), available);
                if (matched != null) {
                    return matched;
                }
            }
        }

        /**
         * As {@link #pickElements}, but for the phases {@code elements}
         * make available in {@code tdbPath} (via {@link
         * ModelBrowseService#selectablePhases}) -- the GUI's next step
         * once elements are chosen. Blank input selects all of them.
         */
        List<String> pickPhases(String tdbPath, List<String> elements) throws IOException {
            List<String> available;
            try {
                available = browse.selectablePhases(tdbPath, elements);
            } catch (IOException e) {
                System.out.println("  Could not read phases from " + tdbPath + ": " + e.getMessage());
                return List.of();
            }
            String allCsv = String.join(",", available);
            System.out.println("  Available phases for " + elements + ": " + available);
            while (true) {
                String chosenCsv = str("Phases (comma-separated, Enter for all)", allCsv);
                List<String> matched = matchAgainst(splitCsv(chosenCsv), available);
                if (matched != null) {
                    return matched;
                }
            }
        }

        /**
         * Case-insensitively matches each of {@code chosen} against
         * {@code available}, returning the canonically-cased names from
         * {@code available} (not whatever case the user typed) if every
         * entry matches, or {@code null} (after printing which entries
         * didn't match) so the caller re-prompts.
         */
        private List<String> matchAgainst(List<String> chosen, List<String> available) {
            List<String> matched = new ArrayList<>(chosen.size());
            List<String> unknown = new ArrayList<>();
            for (String entry : chosen) {
                String canonical = null;
                for (String candidate : available) {
                    if (candidate.equalsIgnoreCase(entry)) {
                        canonical = candidate;
                        break;
                    }
                }
                if (canonical != null) {
                    matched.add(canonical);
                } else {
                    unknown.add(entry);
                }
            }
            if (!unknown.isEmpty()) {
                System.out.println("  Not available: " + unknown + " -- try again.");
                return null;
            }
            return matched;
        }

        AxisConfig axis(String label, String current) throws IOException {
            while (true) {
                String spec = str(label, current);
                AxisConfig parsed = parseAxisConfig(spec, label);
                if (parsed != null) return parsed;
                System.out.println("  Could not parse '" + spec + "' -- expected TYPE,min,max,step "
                        + "or COMPOSITION:i,min,max,step");
            }
        }
    }

    /**
     * Resolved inputs for a single-point equilibrium calculation, built
     * either from {@code --flag value} pairs or by prompting on stdin --
     * the two sources are interchangeable and {@link #runEquilibriumViaSession}
     * doesn't care which one produced them.
     */
    private static final class EquilibriumParams {
        final String tdbPath;
        final List<String> elements;
        final List<String> phases;
        final double T;
        final double P;
        final double[] composition;

        private EquilibriumParams(String tdbPath, List<String> elements, List<String> phases,
                                   double T, double P, double[] composition) {
            this.tdbPath = tdbPath;
            this.elements = elements;
            this.phases = phases;
            this.T = T;
            this.P = P;
            this.composition = composition;
        }

        static EquilibriumParams fromArgs(String[] args, String cwd,
                                           java.util.function.BiFunction<String, String, String> resolvePath) {
            String tdbPath        = cwd + "/data/VZR-re2.TDB";
            String elementsStr    = "V,ZR";
            String phasesStr      = "V2ZR";
            double T              = 1000.0;
            double P              = 10000.0;
            String compositionStr = "0.6666666666666666,0.3333333333333333";

            for (int i = 1; i < args.length - 1; i++) {
                switch (args[i]) {
                    case "--tdb":         tdbPath        = resolvePath.apply(args[++i], cwd); break;
                    case "--elements":    elementsStr    = args[++i]; break;
                    case "--phases":      phasesStr      = args[++i]; break;
                    case "--T":           T              = Double.parseDouble(args[++i]); break;
                    case "--P":           P              = Double.parseDouble(args[++i]); break;
                    case "--composition": compositionStr = args[++i]; break;
                    default: i++; break;
                }
            }
            return build(tdbPath, elementsStr, phasesStr, T, P, compositionStr);
        }

        static EquilibriumParams fromPrompts(Prompter p) throws IOException {
            String tdbPath = p.tdbPath("VZR-re2.TDB");
            List<String> elements = p.pickElements(tdbPath);
            List<String> phases = p.pickPhases(tdbPath, elements);
            double T = p.num("Temperature (K)", 1000.0);
            double P = p.num("Pressure (Pa)", 10000.0);
            String compositionStr = p.str("Overall composition (mole fractions, comma-separated)",
                    "0.6666666666666666,0.3333333333333333");
            return new EquilibriumParams(tdbPath, elements, phases, T, P, parseDoubleCsv(compositionStr));
        }

        private static EquilibriumParams build(String tdbPath, String elementsStr, String phasesStr,
                                                 double T, double P, String compositionStr) {
            return new EquilibriumParams(tdbPath, splitCsv(elementsStr), splitCsv(phasesStr),
                    T, P, parseDoubleCsv(compositionStr));
        }
    }

    /**
     * Parses an axis spec of the form {@code TYPE,min,max,step} (TEMPERATURE
     * or PRESSURE) or {@code COMPOSITION:i,min,max,step} (i = component
     * index, default 0 if omitted as bare {@code COMPOSITION}).
     */
    private static AxisConfig parseAxisConfig(String spec, String name) {
        String[] parts = spec.split(",");
        if (parts.length < 4) return null;
        try {
            String typeToken = parts[0].trim().toUpperCase();
            double min  = Double.parseDouble(parts[1].trim());
            double max  = Double.parseDouble(parts[2].trim());
            double step = Double.parseDouble(parts[3].trim());
            if (typeToken.startsWith("COMPOSITION")) {
                int colon = typeToken.indexOf(':');
                int componentIndex = colon < 0 ? 0 : Integer.parseInt(typeToken.substring(colon + 1));
                return new AxisConfig(name + " (X)", componentIndex, min, max, step);
            }
            switch (typeToken) {
                case "TEMPERATURE": return new AxisConfig(name + " (K)", Type.TEMPERATURE, min, max, step);
                case "PRESSURE":    return new AxisConfig(name + " (Pa)", Type.PRESSURE, min, max, step);
                default:            return null;
            }
        } catch (NumberFormatException e) { return null; }
    }

    private void printPhaseRegions(PhaseDiagram diagram) {
        java.util.Set<String> regions = new java.util.TreeSet<>();
        for (DiagramLine line : diagram.getLines()) {
            regions.add(String.join(" + ", line.stablePhaseSet));
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
     * Browse a TDB database file: list its elements, and (given
     * elements) the phases available for them. Routed through
     * {@link CalculationSession}'s browse methods -- the pre-calculation
     * path in the target data flow, no model build.
     *
     * Usage:
     *   inspect [--tdb FILE] [--elements A,B]
     */
    private void runModelInspect(String[] args, String cwd) throws IOException {
        boolean interactive = isInteractive(args);

        if (interactive) {
            Prompter p = prompter(cwd);
            String tdbPath = p.tdbPath("tizr_kum_cvm.tdb");
            System.out.println("--- TDB Inspection (browse via CalculationSession) ---");
            System.out.println("File: " + tdbPath);
            try {
                List<String> elements = p.pickElements(tdbPath);
                if (!elements.isEmpty()) {
                    List<String> phases = p.pickPhases(tdbPath, elements);
                    System.out.println("Selected phases (" + phases.size() + "): " + phases);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            System.out.println("-------------------------------------------------------");
            return;
        }

        String tdbPath  = cwd + "/data/tizr_kum_cvm.tdb";
        String elements = "";
        for (int i = 1; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--tdb":      tdbPath  = resolvePath(args[++i], cwd); break;
                case "--elements": elements = args[++i]; break;
                default: i++; break;
            }
        }

        System.out.println("--- TDB Inspection (browse via CalculationSession) ---");
        System.out.println("File: " + tdbPath);

        try {
            List<String> allElements = modelBrowseService.selectableElements(tdbPath);
            System.out.println("Elements: " + String.join(", ", allElements));

            List<String> elementFilter = splitCsv(elements);
            if (!elementFilter.isEmpty()) {
                List<String> phases = modelBrowseService.selectablePhases(tdbPath, elementFilter);
                System.out.println("Phases for " + elementFilter
                        + " (" + phases.size() + "):");
                for (String p : phases) System.out.println("  " + p);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println("-------------------------------------------------------");
    }

    // ──────────────────────────────────────────────────────────────────
    // Legacy commands
    // ──────────────────────────────────────────────────────────────────

    private void runOptimization(String expIn, String phaseIn, String filePrefix) throws IOException {
        FitParametersUseCase useCase = new FitParametersUseCase(optimizationUseCase);
        useCase.execute(expIn, phaseIn, filePrefix, 50);
    }

    private void runCalModel(String expIn, String phaseIn, String filePrefix) throws IOException {
        ValidateModelUseCase useCase = new ValidateModelUseCase(modelInspectionService);
        useCase.execute(expIn, phaseIn);
    }

    // ──────────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────────

    private static ArrayList<String> splitCsv(String in) {
        ArrayList<String> list = new ArrayList<>();
        if (in == null || in.trim().isEmpty()) return list;
        for (String s : in.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    private String resolvePath(String path, String cwd) {
        return resolvePathStatic(path, cwd);
    }

    private static String resolvePathStatic(String path, String cwd) {
        java.io.File f = new java.io.File(path);
        if (f.isAbsolute()) return path;
        return cwd + "/" + path;
    }

    private void printBanner() {
        System.out.println("===============================================");
        System.out.println("  expCVM 10 - Thermodynamic Workbench");
        System.out.println("  Started: " + new java.util.Date());
        System.out.println("===============================================");
    }

    private void printUsage() {
        System.out.println("Usage: ./gradlew run --args=\"[command] [options]\"");
        System.out.println("       (or) java -cp build/classes ui.Main [command] [options]");
        System.out.println();
        System.out.println("Every calculation/browse command below is a thin wrapper around one");
        System.out.println("session.CalculationSession method -- the same session the GUI and REST");
        System.out.println("API use. Run '<command> --help' for that command's options.");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  (no args)              Default single-point equilibrium demo");
        System.out.println("  equilibrium [options]  Single-point equilibrium (Newton-refined)");
        System.out.println("  initial-state [opts]   Grid-minimizer starting point only, no Newton step");
        System.out.println("  step [options]         Single-axis property scan (Algorithm B step branch)");
        System.out.println("  coarse-binary [opts]   2D grid of independent equilibrium samples");
        System.out.println("  coarse-ternary [opts]  2D grid over two composition axes");
        System.out.println("  map [options]          Two-axis ZPF phase-diagram map (Algorithms C1/C2/D)");
        System.out.println("  diagram [options]      Phase-diagram tracing (not yet implemented --");
        System.out.println("                         see CalculationSession#calculatePhaseDiagram)");
        System.out.println("  inspect [options]      Browse a TDB database (elements / phases)");
        System.out.println("  opt                    Run parameter optimization (legacy pathway)");
        System.out.println("  cal                    Run CalModel calculation (legacy pathway)");
        System.out.println("  --gui                  Launch the graphical interface");
        System.out.println("  -h, --help             Show this help and exit");
    }
}
