package ui.cli;

import ui.layer.FitParametersUseCase;
import ui.layer.ValidateModelUseCase;
import ui.layer.OptimizationUseCase;
import ui.layer.ModelInspectionService;
import ui.layer.ModelBrowseService;
import ui.layer.CompositionUnits;
import system.database.TdbParser;
import system.ports.EquilibriumResult;
import session.CalculationSession;
import session.calctype.CalculationInterface;
import session.calctype.CalculationGroup;
import session.calctype.CalculationKind;
import session.calctype.CalculationOutcome;
import session.calctype.ModelSelection;
import calc.diagram.AxisConfig;
import calc.diagram.AxisConfig.Type;
import ui.result.EquilibriumReport;

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
 * Calculation layers is {@link session.calctype.CalculationInterface}, never
 * {@link CalculationSession} directly: every calculation command below
 * builds a {@link ModelSelection} and a calculation type's own typed
 * params, then calls {@link CalculationInterface#runCalculating} -- no
 * command calls {@code CalculationSession.setModel}/{@code calculate*}
 * itself, and none reaches into {@code calc.equil}/{@code calc.diagram}'s
 * solver classes directly either. This mirrors the GUI and REST API, which
 * go through the same interface the same way -- the CLI is a thin
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
                    CalculationInterface.runAssessing(CalculationKind.ASSESSMENT, null);
            System.out.println();
            System.out.println(outcome.message());
            return;
        }

        // Browse (tdb -> elements -> phases) once here, before the
        // calculation-type choice -- every calculation type needs the same
        // three answers, so ask once and reuse them, rather than each
        // chosen type's own fromPrompts() re-browsing from scratch.
        System.out.println();
        System.out.println("Choose a database, elements, and phases:");
        Prompter prompter = prompter(cwd);
        String tdbPath = prompter.tdbPath("VZR-re2.TDB");
        List<String> elements = prompter.pickElements(tdbPath);
        List<String> phases = prompter.pickPhases(tdbPath, elements);
        ModelSelection model = new ModelSelection(tdbPath, elements, phases);

        System.out.println();
        System.out.println("Choose a calculation:");
        System.out.println("  1. equilibrium      Single-point equilibrium (Newton-refined)");
        System.out.println("  2. initial-state    Grid-minimizer starting point only");
        System.out.println("  3. step             Single-axis property scan");
        System.out.println("  4. coarse-binary    2D grid of independent equilibrium samples");
        System.out.println("  5. coarse-ternary   2D grid over two composition axes");
        System.out.println("  6. map              Two-axis ZPF phase-diagram map");
        System.out.println("  0. quit");
        System.out.println();

        String choice = prompter.str("Choice", "1");

        System.out.println();
        switch (choice.trim()) {
            case "1": runEquilibriumViaSession(new String[]{"equilibrium", "-i"}, cwd, model); break;
            case "2": runInitialState(new String[]{"initial-state", "-i"}, cwd, model);         break;
            case "3": runStep(new String[]{"step", "-i"}, cwd, model);                          break;
            case "4": runCoarseBinary(new String[]{"coarse-binary", "-i"}, cwd, model);         break;
            case "5": runCoarseTernary(new String[]{"coarse-ternary", "-i"}, cwd, model);       break;
            case "6": runMap(new String[]{"map", "-i"}, cwd, model);                            break;
            case "0": return;
            default:
                System.out.println("Unrecognized choice: " + choice);
        }
    }

    private void printCommandUsage(String command) {
        switch (command) {
            case "diagram":
                System.out.println("Usage: diagram [options]");
                System.out.println("  Automated binary phase-diagram tracing (Algorithms B/C1/C2/D --");
                System.out.println("  stitches the WHOLE connected diagram from one starting point,");
                System.out.println("  unlike map's single ZPF line).");
                System.out.println("  --tdb FILE                     TDB database path");
                System.out.println("  --elements AG,CU                Comma-separated elements");
                System.out.println("  --phases LIQUID,FCC_A1          Comma-separated phases (candidates)");
                System.out.println("  --axis TYPE,min,max,step        repeat twice: 1st = walked axis, e.g. TEMPERATURE,1000,1200,5;");
                System.out.println("                                 2nd = released axis (must be COMPOSITION), e.g. COMPOSITION:1,0.0,1.0,0.01");
                System.out.println("  --T value                       Fixed T (K), used when 1st axis type != TEMPERATURE");
                System.out.println("  --P value                       Fixed P (Pa), used when 1st axis type != PRESSURE");
                System.out.println("  --composition x1,x2,...         Overall mole fractions");
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
                System.out.println("  --axis TYPE,min,max,step       repeat twice: e.g. COMPOSITION:1,0.02,0.20,0.01");
                System.out.println("                                 then TEMPERATURE,1200,1600,50");
                System.out.println("  --T value                      Fixed T (K), used when neither axis is TEMPERATURE");
                System.out.println("  --P value                      Fixed P (Pa), used when neither axis is PRESSURE");
                System.out.println("  --composition x1,x2,...        Overall mole fractions (renormalized for swept axes)");
                break;
            case "coarse-ternary":
                System.out.println("Usage: coarse-ternary [options]");
                System.out.println("  2D grid over two composition axes; remaining component(s) renormalized.");
                System.out.println("  --tdb FILE                     TDB database path");
                System.out.println("  --elements CR,FE,MO            Comma-separated elements");
                System.out.println("  --phases LIQUID,A2             Comma-separated phases (candidates)");
                System.out.println("  --axis COMPOSITION:i,min,max,step  repeat twice: e.g. COMPOSITION:1,0.0,0.6,0.1");
                System.out.println("                                 then COMPOSITION:2,0.0,0.6,0.1");
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
                System.out.println("  --axis TYPE,min,max,step        repeat twice: 1st = walked axis, e.g. TEMPERATURE,1000,1200,5;");
                System.out.println("                                 2nd = released axis (must be COMPOSITION), e.g. COMPOSITION:1,0.0,1.0,0.01");
                System.out.println("  --T value                       Fixed T (K), used when 1st axis type != TEMPERATURE");
                System.out.println("  --P value                       Fixed P (Pa), used when 1st axis type != PRESSURE");
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
     * Automated binary phase-diagram tracing (Sundman Algorithms
     * A/B/C1/C2/D) via {@link CalculationSession#calculatePhaseDiagram}.
     * axis0 is walked in fixed increments (typically TEMPERATURE), axis1
     * is released and solved exactly at each boundary (must be
     * COMPOSITION).
     */
    private void runPhaseDiagram(String[] args, String cwd) throws IOException {
        boolean interactive = isInteractive(args);

        CoarseGridParams p = interactive
                ? CoarseGridParams.fromPrompts(prompter(cwd), "data/agcu.TDB",
                        "AG,CU", "LIQUID,FCC_A1", "Axis0", "TEMPERATURE,1000,1200,5",
                        "Axis1", "COMPOSITION:1,0.0,1.0,0.01", 1000.0, "0.5,0.5")
                : CoarseGridParams.fromArgs(args, cwd, this::resolvePath, "data/agcu.TDB",
                        "AG,CU", "LIQUID,FCC_A1", "TEMPERATURE,1000,1200,5",
                        "COMPOSITION:1,0.0,1.0,0.01", 1000.0, "0.5,0.5");

        System.out.println("--- Phase Diagram Calculation (via CalculationSession) ---");
        p.printSummary("Axis 0 (walked)", "Axis 1 (released, must be COMPOSITION)");
        System.out.println("-------------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        CalculationInterface.PhaseDiagramParams params = new CalculationInterface.PhaseDiagramParams(
                new AxisConfig[] { p.axis1, p.axis2 },
                new double[] { p.axis1.min, p.axis2.min },
                p.T, p.P, p.composition);

        calc.diagram.PhaseDiagramResult result;
        try {
            result = CalculationInterface.runCalculating(
                    session, CalculationKind.PHASE_DIAGRAM, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printPhaseDiagramResult(result);
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
        runEquilibriumViaSession(args, cwd, null);
    }

    /**
     * As {@link #runEquilibriumViaSession(String[], String)}, but if
     * {@code preSelected} is non-null (the interactive menu already browsed
     * a model before this calculation type was chosen), skips the
     * tdb/elements/phases prompts and only asks for T/P/composition.
     */
    private void runEquilibriumViaSession(String[] args, String cwd, ModelSelection preSelected)
            throws IOException {
        boolean interactive = isInteractive(args);

        EquilibriumParams p = preSelected != null
                ? EquilibriumParams.fromPrompts(prompter(cwd), preSelected)
                : interactive
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
        CalculationInterface.EquilibriumParams params =
                new CalculationInterface.EquilibriumParams(p.T, p.P, p.composition);

        EquilibriumResult result;
        try {
            result = CalculationInterface.runCalculating(
                    session, CalculationKind.EQUILIBRIUM, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printEquilibriumResult(result, p.elements);
    }

    /**
     * Prints each stable phase's amount (formula units and real atoms --
     * see {@link EquilibriumResult.PhaseResult#atoms()}), Gibbs energy,
     * internal composition (mole fraction {@code x}), and its RELATIVE
     * share of the whole system -- both atomic% (real atoms of this
     * phase / total real atoms across all stable phases) and mass%
     * (real mass of this phase / total real mass), the lever-rule split
     * of the system between phases. Mass is computed via {@link
     * CompositionUnits#averageAtomicMass} on each phase's own {@code x};
     * relative mass% is omitted (for every phase) if any phase's
     * composition includes an element with no known standard atomic
     * mass, since a partial mass balance would be misleading.
     */
    private void printEquilibriumResult(EquilibriumResult result, List<String> elements) {
        System.out.println("Converged:   " + result.isConverged()
                + "  (iterations=" + result.getIterations() + ")");
        System.out.println("mu:          " + Arrays.toString(result.getMu()));
        System.out.println("Stable phases:");

        List<EquilibriumResult.PhaseResult> stable = result.getStablePhases();
        double totalAtoms = 0.0;
        double totalMass = 0.0;
        double[] phaseMass = new double[stable.size()];
        boolean massKnown = true;
        for (int i = 0; i < stable.size(); i++) {
            EquilibriumResult.PhaseResult pr = stable.get(i);
            totalAtoms += pr.atoms();
            double avgMass = CompositionUnits.averageAtomicMass(pr.x, elements);
            if (Double.isNaN(avgMass)) {
                massKnown = false;
            } else {
                phaseMass[i] = pr.atoms() * avgMass;
                totalMass += phaseMass[i];
            }
        }

        for (int i = 0; i < stable.size(); i++) {
            EquilibriumResult.PhaseResult pr = stable.get(i);
            System.out.printf("  %-10s %.6f f.u. (%.6f atoms)  G=%.4f J/mol.f.u.  x=%s",
                    pr.phaseName, pr.amount, pr.atoms(), pr.G, Arrays.toString(pr.x));
            double atomicPct = totalAtoms > 0.0 ? 100.0 * pr.atoms() / totalAtoms : Double.NaN;
            System.out.printf("  atomic%%=%.4f", atomicPct);
            if (massKnown && totalMass > 0.0) {
                System.out.printf("  mass%%=%.4f", 100.0 * phaseMass[i] / totalMass);
            }
            System.out.println();
        }

        System.out.println();
        System.out.println(EquilibriumReport.format(result, elements));
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
        runInitialState(args, cwd, null);
    }

    /** As {@link #runInitialState(String[], String)}, but skips re-browsing if {@code preSelected} is non-null -- see {@link #runEquilibriumViaSession(String[], String, ModelSelection)}. */
    private void runInitialState(String[] args, String cwd, ModelSelection preSelected) throws IOException {
        boolean interactive = isInteractive(args);

        EquilibriumParams p = preSelected != null
                ? EquilibriumParams.fromPrompts(prompter(cwd), preSelected)
                : interactive
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
        CalculationInterface.InitialStateParams params =
                new CalculationInterface.InitialStateParams(p.T, p.P, p.composition);

        EquilibriumResult result;
        try {
            result = CalculationInterface.runCalculating(
                    session, CalculationKind.INITIAL_STATE, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printEquilibriumResult(result, p.elements);
    }

    // ──────────────────────────────────────────────────────────────────
    // Step (single-axis scan, via CalculationSession)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Single-axis property scan (Sundman 2021 Calphad 75, Algorithm B's
     * step branch), routed through {@link CalculationSession#calculateStep}.
     */
    private void runStep(String[] args, String cwd) throws IOException {
        runStep(args, cwd, null);
    }

    /** As {@link #runStep(String[], String)}, but skips re-browsing if {@code preSelected} is non-null -- see {@link #runEquilibriumViaSession(String[], String, ModelSelection)}. */
    private void runStep(String[] args, String cwd, ModelSelection preSelected) throws IOException {
        boolean interactive = isInteractive(args);

        StepParams p = preSelected != null
                ? StepParams.fromPrompts(prompter(cwd), preSelected)
                : interactive
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
        CalculationInterface.StepParams params =
                new CalculationInterface.StepParams(p.axis, p.T, p.P, p.composition);

        calc.diagram.PhaseDiagramResult result;
        try {
            result = CalculationInterface.runCalculating(
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
            return fromPromptsRemaining(p, tdbPath, elements, phases);
        }

        /** As {@link #fromPrompts(Prompter)}, but skips the tdb/elements/phases prompts -- see {@link EquilibriumParams#fromPrompts(Prompter, ModelSelection)}. */
        static StepParams fromPrompts(Prompter p, ModelSelection preSelected) throws IOException {
            return fromPromptsRemaining(p, preSelected.tdbFilePath(), preSelected.elements(),
                    preSelected.phases());
        }

        private static StepParams fromPromptsRemaining(Prompter p, String tdbPath,
                List<String> elements, List<String> phases) throws IOException {
            AxisConfig axis = p.axis("Axis (TYPE,min,max,step)", "TEMPERATURE,1000,1200,5");
            double T = p.num("Fixed T (K, used if axis type != TEMPERATURE)", 1000.0);
            double P = p.num("Fixed P (Pa, used if axis type != PRESSURE)", 101325.0);
            double[] composition = p.compositionMoleFractions(
                    "Overall composition (used if axis type != COMPOSITION)", "0.5,0.5",
                    elements);
            return new StepParams(tdbPath, elements, phases,
                    axis, T, P, composition);
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

        /**
         * Parses a 2-axis command's flags. Both axes share ONE flag name,
         * {@code --axis} -- repeated twice, in order (first {@code --axis}
         * is axis1/walked/X/I, second is axis2/released/Y/J depending on
         * the command) -- rather than a different per-command suffix
         * ({@code --axis0}/{@code --axis1}, {@code --axisX}/{@code
         * --axisY}, {@code --axisI}/{@code --axisJ}): the same conceptual
         * input (an {@link AxisConfig} spec string) deserves the same flag
         * name everywhere, matching {@code --T}/{@code --P}/{@code
         * --composition}'s already-consistent naming across every command.
         */
        static CoarseGridParams fromArgs(String[] args, String cwd,
                                          java.util.function.BiFunction<String, String, String> resolvePath,
                                          String defaultTdbRelPath, String defaultElements, String defaultPhases,
                                          String defaultAxis1Spec, String defaultAxis2Spec,
                                          double defaultT, String defaultComposition) {
            String tdbPath        = cwd + "/" + defaultTdbRelPath;
            String elementsStr    = defaultElements;
            String phasesStr      = defaultPhases;
            List<String> axisSpecs = new ArrayList<>();
            double T              = defaultT;
            double P              = 101325.0;
            String compositionStr = defaultComposition;

            for (int i = 1; i < args.length - 1; i++) {
                String flag = args[i];
                if ("--tdb".equals(flag))              tdbPath        = resolvePath.apply(args[++i], cwd);
                else if ("--elements".equals(flag))    elementsStr    = args[++i];
                else if ("--phases".equals(flag))      phasesStr      = args[++i];
                else if ("--axis".equals(flag))        axisSpecs.add(args[++i]);
                else if ("--T".equals(flag))           T              = Double.parseDouble(args[++i]);
                else if ("--P".equals(flag))           P              = Double.parseDouble(args[++i]);
                else if ("--composition".equals(flag)) compositionStr = args[++i];
                else i++;
            }
            String axis1Spec = axisSpecs.size() > 0 ? axisSpecs.get(0) : defaultAxis1Spec;
            String axis2Spec = axisSpecs.size() > 1 ? axisSpecs.get(1) : defaultAxis2Spec;

            AxisConfig axis1 = parseAxisConfig(axis1Spec, "Axis1");
            AxisConfig axis2 = parseAxisConfig(axis2Spec, "Axis2");
            if (axis1 == null || axis2 == null) {
                throw new IllegalArgumentException("could not parse --axis (need exactly 2)");
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
            return fromPromptsRemaining(p, tdbPath, elements, phases,
                    axis1Label, defaultAxis1Spec, axis2Label, defaultAxis2Spec, defaultT, defaultComposition);
        }

        /** As the other {@code fromPrompts}, but skips the tdb/elements/phases prompts -- see {@link EquilibriumParams#fromPrompts(Prompter, ModelSelection)}. */
        static CoarseGridParams fromPrompts(Prompter p, ModelSelection preSelected,
                                             String axis1Label, String defaultAxis1Spec,
                                             String axis2Label, String defaultAxis2Spec,
                                             double defaultT, String defaultComposition) throws IOException {
            return fromPromptsRemaining(p, preSelected.tdbFilePath(), preSelected.elements(),
                    preSelected.phases(), axis1Label, defaultAxis1Spec, axis2Label, defaultAxis2Spec,
                    defaultT, defaultComposition);
        }

        private static CoarseGridParams fromPromptsRemaining(Prompter p, String tdbPath,
                List<String> elements, List<String> phases,
                String axis1Label, String defaultAxis1Spec,
                String axis2Label, String defaultAxis2Spec,
                double defaultT, String defaultComposition) throws IOException {
            AxisConfig axis1 = p.axis(axis1Label + " (TYPE,min,max,step)", defaultAxis1Spec);
            AxisConfig axis2 = p.axis(axis2Label + " (TYPE,min,max,step)", defaultAxis2Spec);
            double T = p.num("Fixed T (K)", defaultT);
            double P = p.num("Fixed P (Pa)", 101325.0);
            double[] composition = p.compositionMoleFractions(
                    "Overall composition", defaultComposition, elements);
            return new CoarseGridParams(tdbPath, elements, phases,
                    axis1, axis2, T, P, composition);
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
        runCoarseBinary(args, cwd, null);
    }

    /** As {@link #runCoarseBinary(String[], String)}, but skips re-browsing if {@code preSelected} is non-null -- see {@link #runEquilibriumViaSession(String[], String, ModelSelection)}. */
    private void runCoarseBinary(String[] args, String cwd, ModelSelection preSelected) throws IOException {
        boolean interactive = isInteractive(args);

        CoarseGridParams p = preSelected != null
                ? CoarseGridParams.fromPrompts(prompter(cwd), preSelected,
                        "AxisX", "COMPOSITION:1,0.02,0.20,0.01", "AxisY", "TEMPERATURE,1200,1600,50",
                        1500.0, "1.0,0.0")
                : interactive
                        ? CoarseGridParams.fromPrompts(prompter(cwd), "data/VZR-re2.TDB",
                                "V,ZR", "V2ZR,BCC_A2", "AxisX", "COMPOSITION:1,0.02,0.20,0.01",
                                "AxisY", "TEMPERATURE,1200,1600,50", 1500.0, "1.0,0.0")
                        : CoarseGridParams.fromArgs(args, cwd, this::resolvePath, "data/VZR-re2.TDB",
                                "V,ZR", "V2ZR,BCC_A2", "COMPOSITION:1,0.02,0.20,0.01",
                                "TEMPERATURE,1200,1600,50", 1500.0, "1.0,0.0");

        System.out.println("--- Coarse Binary Diagram (via CalculationSession) ---");
        p.printSummary("Axis X", "Axis Y");
        System.out.println("-------------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        CalculationInterface.CoarseBinaryParams params =
                new CalculationInterface.CoarseBinaryParams(p.axis1, p.axis2, p.T, p.P, p.composition);

        ui.result.CoarseDiagramResult result;
        try {
            result = CalculationInterface.runCalculating(
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
        runCoarseTernary(args, cwd, null);
    }

    /** As {@link #runCoarseTernary(String[], String)}, but skips re-browsing if {@code preSelected} is non-null -- see {@link #runEquilibriumViaSession(String[], String, ModelSelection)}. */
    private void runCoarseTernary(String[] args, String cwd, ModelSelection preSelected) throws IOException {
        boolean interactive = isInteractive(args);

        CoarseGridParams p = preSelected != null
                ? CoarseGridParams.fromPrompts(prompter(cwd), preSelected,
                        "AxisI", "COMPOSITION:1,0.0,0.6,0.1", "AxisJ", "COMPOSITION:2,0.0,0.6,0.1",
                        1800.0, "1.0,0.0,0.0")
                : interactive
                        ? CoarseGridParams.fromPrompts(prompter(cwd), "data/Cr-Fe-Mo.TDB",
                                "CR,FE,MO", "LIQUID,A2", "AxisI", "COMPOSITION:1,0.0,0.6,0.1",
                                "AxisJ", "COMPOSITION:2,0.0,0.6,0.1", 1800.0, "1.0,0.0,0.0")
                        : CoarseGridParams.fromArgs(args, cwd, this::resolvePath, "data/Cr-Fe-Mo.TDB",
                                "CR,FE,MO", "LIQUID,A2", "COMPOSITION:1,0.0,0.6,0.1",
                                "COMPOSITION:2,0.0,0.6,0.1", 1800.0, "1.0,0.0,0.0");

        System.out.println("--- Coarse Ternary Diagram (via CalculationSession) ---");
        p.printSummary("Axis I", "Axis J");
        System.out.println("--------------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        CalculationInterface.CoarseTernaryParams params =
                new CalculationInterface.CoarseTernaryParams(p.axis1, p.axis2, p.T, p.P, p.composition);

        ui.result.CoarseDiagramResult result;
        try {
            result = CalculationInterface.runCalculating(
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
     * Algorithms A/B/C1/C2/D), routed through {@link
     * CalculationSession#calculatePhaseDiagram}. {@code axis1} (released,
     * solved for exactly at each boundary) must be COMPOSITION.
     */
    private void runMap(String[] args, String cwd) throws IOException {
        runMap(args, cwd, null);
    }

    /** As {@link #runMap(String[], String)}, but skips re-browsing if {@code preSelected} is non-null -- see {@link #runEquilibriumViaSession(String[], String, ModelSelection)}. */
    private void runMap(String[] args, String cwd, ModelSelection preSelected) throws IOException {
        boolean interactive = isInteractive(args);

        CoarseGridParams p = preSelected != null
                ? CoarseGridParams.fromPrompts(prompter(cwd), preSelected,
                        "Axis0", "TEMPERATURE,1000,1200,5", "Axis1", "COMPOSITION:1,0.0,1.0,0.01",
                        1000.0, "0.5,0.5")
                : interactive
                        ? CoarseGridParams.fromPrompts(prompter(cwd), "data/agcu.TDB",
                                "AG,CU", "LIQUID,FCC_A1", "Axis0", "TEMPERATURE,1000,1200,5",
                                "Axis1", "COMPOSITION:1,0.0,1.0,0.01", 1000.0, "0.5,0.5")
                        : CoarseGridParams.fromArgs(args, cwd, this::resolvePath, "data/agcu.TDB",
                                "AG,CU", "LIQUID,FCC_A1", "TEMPERATURE,1000,1200,5",
                                "COMPOSITION:1,0.0,1.0,0.01", 1000.0, "0.5,0.5");

        System.out.println("--- Map Calculation (via CalculationSession) ---");
        p.printSummary("Axis 0", "Axis 1");
        System.out.println("-------------------------------------------------");

        ModelSelection model = new ModelSelection(p.tdbPath, p.elements, p.phases);
        AxisConfig[] axes = { p.axis1, p.axis2 };
        double[] startAxes = { p.axis1.min, p.axis2.min };
        CalculationInterface.PhaseDiagramParams params =
                new CalculationInterface.PhaseDiagramParams(axes, startAxes, p.T, p.P, p.composition);

        calc.diagram.PhaseDiagramResult result;
        try {
            result = CalculationInterface.runCalculating(
                    session, CalculationKind.PHASE_DIAGRAM, model, params);
        } catch (IllegalStateException | UnsupportedOperationException | IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        printPhaseDiagramResult(result);
    }

    private static void printPhaseDiagramResult(calc.diagram.PhaseDiagramResult result) {
        System.out.println("Calculation complete: " + result.isComplete());
        if (!result.getMessage().isEmpty()) {
            System.out.println("Message: " + result.getMessage());
        }
        System.out.printf("  Lines: %d%n", result.getLines().size());
        System.out.printf("  Nodes: %d%n", result.getNodes().size());
        java.util.Set<String> regions = new java.util.TreeSet<>();
        for (calc.diagram.PhaseDiagramResult.LineSegment line : result.getLines()) {
            regions.add(line.label());
        }
        if (!regions.isEmpty()) {
            System.out.println("  Lines found:");
            for (String r : regions) System.out.println("    " + r);
        }
        for (calc.diagram.PhaseDiagramResult.NodePoint node : result.getNodes()) {
            if (node.type == calc.diagram.PhaseDiagramResult.NodePoint.Type.INVARIANT) {
                System.out.println("  Invariant node: " + String.join("+", node.stablePhases)
                        + " at " + Arrays.toString(node.axisValues));
            }
        }

        String[] axisNames = result.getAxisNames();

        System.out.println();
        System.out.println("=== Full line detail (" + result.getLines().size() + " lines) ===");
        int lineIndex = 0;
        for (calc.diagram.PhaseDiagramResult.LineSegment line : result.getLines()) {
            lineIndex++;
            System.out.println("Line " + lineIndex + ": " + line.label()
                    + " (" + line.size() + " points, fixedPhase=" + line.fixedPhase + ")");
            int pointIndex = 0;
            for (double[] coord : line.coords) {
                pointIndex++;
                System.out.println("    [" + pointIndex + "] " + formatCoords(axisNames, coord));
            }
        }

        System.out.println();
        System.out.println("=== Full node detail (" + result.getNodes().size() + " nodes) ===");
        int nodeIndex = 0;
        for (calc.diagram.PhaseDiagramResult.NodePoint node : result.getNodes()) {
            nodeIndex++;
            System.out.println("Node " + nodeIndex + ": " + node.type + " "
                    + String.join("+", node.stablePhases) + " at " + formatCoords(axisNames, node.axisValues));
        }
    }

    /** Renders axis coordinates as {@code "name=value, name=value, ..."} using each axis's own display name. */
    private static String formatCoords(String[] axisNames, double[] coord) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < coord.length; i++) {
            if (i > 0) sb.append(", ");
            String name = i < axisNames.length ? axisNames[i] : ("axis" + i);
            sb.append(name).append('=').append(coord[i]);
        }
        return sb.toString();
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
         *
         * <p>Returned in {@code available}'s order (which {@link
         * ModelBrowseService#selectableElements} already sorts
         * alphabetically), not whatever order the user typed -- e.g.
         * typing {@code "FE,C"} still returns {@code [C, FE]}. Element/
         * phase order defines the composition-vector index order for the
         * whole calculation (see {@code ThermodynamicSystem.build}), so
         * this must be a fixed, predictable order regardless of typed
         * order -- otherwise a composition the user types immediately
         * afterward could silently mean something different from what
         * they intended (see {@link ModelBrowseService#selectableElements}'s
         * Javadoc for the failure this caused).
         */
        private List<String> matchAgainst(List<String> chosen, List<String> available) {
            List<String> unknown = new ArrayList<>();
            for (String entry : chosen) {
                boolean found = false;
                for (String candidate : available) {
                    if (candidate.equalsIgnoreCase(entry)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    unknown.add(entry);
                }
            }
            if (!unknown.isEmpty()) {
                System.out.println("  Not available: " + unknown + " -- try again.");
                return null;
            }
            List<String> matched = new ArrayList<>(chosen.size());
            for (String candidate : available) {
                for (String entry : chosen) {
                    if (candidate.equalsIgnoreCase(entry)) {
                        matched.add(candidate);
                        break;
                    }
                }
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

        /**
         * Prompts for the overall composition, in the caller's choice of
         * mole fraction (default) or weight percent -- always returns mole
         * fractions, in {@code elements} order, since that is the only
         * unit {@code CalculationSession}/{@code CalculationInterface}
         * understand. wt.% input is converted here, immediately, using
         * {@link CompositionUnits}'s standard atomic-weight table; the
         * converted mole-fraction vector is echoed back so the conversion
         * is visible, not a silent black box.
         *
         * @param label              prompt label, e.g. "Overall composition"
         * @param defaultMoleCsv     default comma-separated MOLE fractions,
         *                           shown as the default when mole units are chosen
         * @param elements           element order the returned array must match
         */
        double[] compositionMoleFractions(String label, String defaultMoleCsv,
                                           List<String> elements) throws IOException {
            String units = str("Units (mole / wt%)", "mole");
            if (!"wt%".equalsIgnoreCase(units.trim()) && !"wt".equalsIgnoreCase(units.trim())) {
                String compositionStr = str(label + " (mole fractions, comma-separated)", defaultMoleCsv);
                return parseDoubleCsv(compositionStr);
            }

            String defaultWtPctCsv = CompositionUnits.moleFractionsToWeightPercentCsv(
                    parseDoubleCsv(defaultMoleCsv), elements);
            if (defaultWtPctCsv == null) {
                defaultWtPctCsv = defaultMoleCsv;
            }
            String compositionStr = str(label + " (weight percent, comma-separated)", defaultWtPctCsv);
            double[] weightFractions = parseDoubleCsv(compositionStr);
            for (int i = 0; i < weightFractions.length; i++) {
                weightFractions[i] /= 100.0;
            }
            double[] moleFractions = CompositionUnits.weightToMoleFractions(weightFractions, elements);
            System.out.println("  -> mole fractions: " + Arrays.toString(moleFractions));
            return moleFractions;
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
            return fromPromptsRemaining(p, tdbPath, elements, phases);
        }

        /**
         * As {@link #fromPrompts(Prompter)}, but skips the tdb/elements/phases
         * prompts -- used when {@code runMenu} already browsed a {@link
         * ModelSelection} before the calculation-type choice, so this type's
         * own prompts only ask for its remaining (T, P, composition) fields.
         */
        static EquilibriumParams fromPrompts(Prompter p, ModelSelection preSelected) throws IOException {
            return fromPromptsRemaining(p, preSelected.tdbFilePath(), preSelected.elements(),
                    preSelected.phases());
        }

        private static EquilibriumParams fromPromptsRemaining(Prompter p, String tdbPath,
                List<String> elements, List<String> phases) throws IOException {
            double T = p.num("Temperature (K)", 1000.0);
            double P = p.num("Pressure (Pa)", 10000.0);
            double[] composition = p.compositionMoleFractions("Overall composition",
                    "0.6666666666666666,0.3333333333333333", elements);
            return new EquilibriumParams(tdbPath, elements, phases, T, P, composition);
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
        System.out.println("  diagram [options]      Automated binary phase-diagram tracing");
        System.out.println("                         (Algorithms B/C1/C2/D, whole connected diagram)");
        System.out.println("  inspect [options]      Browse a TDB database (elements / phases)");
        System.out.println("  opt                    Run parameter optimization (legacy pathway)");
        System.out.println("  cal                    Run CalModel calculation (legacy pathway)");
        System.out.println("  --gui                  Launch the graphical interface");
        System.out.println("  -h, --help             Show this help and exit");
    }
}
