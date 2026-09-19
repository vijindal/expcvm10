package application;

import ui.request.AxisConfig;
import calc.diagram.CoarseDiagramTracer;
import calc.diagram.PhaseDiagramEngine;
import calc.diagram.StepTracer;
import calc.equil.EquilibriumSolverV2;
import calc.equil.GridMinimizer;
import system.ThermodynamicSystem;
import system.database.TdbParser;
import system.model.PhaseModelKind;
import system.ports.DatabasePort;
import system.ports.EquilibriumResult;
import ui.result.CoarseDiagramResult;
import calc.diagram.PhaseDiagramResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * UI-agnostic coordinator sitting above the Thermodynamic System Layer and
 * the Calculation Layer (see README.md "Structure"). Single point of
 * contact for a caller (GUI, CLI, API) in both directions -- never exposes
 * {@link ThermodynamicSystem} construction or a solver directly.
 *
 * <p>Not thread-safe; one instance is intended for one sequential caller.
 *
 * <p>UI code must not call these methods directly -- go through {@link
 * application.calctype.CalculationInterface} instead.
 */
public final class ApplicationLayer {

    /** Identifies "what system is currently loaded." */
    public record ModelKey(String tdbFilePath, List<String> elements, List<String> phases,
                           PhaseModelKind modelKind) {}

    private ModelKey currentKey;
    private ThermodynamicSystem currentSystem;
    private EquilibriumResult currentEquilibriumResult;
    private EquilibriumResult currentInitialState;
    private PhaseDiagramResult currentStepResult;
    private CoarseDiagramResult currentCoarseDiagramResult;
    private PhaseDiagramResult currentPhaseDiagram;

    /**
     * Owned separately from {@link #currentSystem}: browsing a database's
     * elements/phases does not require building a {@link ThermodynamicSystem}
     * (no {@code GibbsEnergyModel[]} construction), so this is a lighter,
     * independent path. Reused across browse calls the same way
     * {@link TdbParser#load} itself caches by file path -- calling
     * {@link #availableElements}/{@link #availablePhasesFor} repeatedly
     * with the same {@code tdbFilePath} does not re-parse the file.
     */
    private final DatabasePort browseDatabase = new TdbParser();

    /**
     * Ensures a {@link ThermodynamicSystem} exists for the given model
     * details. Rebuilds only if the details differ from the currently held
     * system; otherwise this is a no-op and the existing system is reused.
     *
     * <p>Validates each input against the corresponding browse method
     * before attempting any real build, so every caller (GUI, CLI, API)
     * gets the same clear, actionable error regardless of how it calls
     * this method -- not a deep parser exception or a silent
     * empty-model failure:
     * <ul>
     *   <li>{@code tdbFilePath} must be one of {@link #availableDatabases()}
     *       (or otherwise exist on disk) -- naming the available databases
     *       if not.</li>
     *   <li>Every entry in {@code elements} must be in
     *       {@link #availableElements(String)} for {@code tdbFilePath} --
     *       naming the invalid elements and the database's actual elements
     *       if not (i.e. "choose a valid database first").</li>
     *   <li>Every entry in {@code phases} must be in
     *       {@link #availablePhasesFor(String, List)} for those elements
     *       -- naming the invalid phases and the actual phases available
     *       for that element set if not (i.e. "choose valid elements
     *       first").</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the database, elements, or
     *         phases fail validation, per the checks above
     * @throws IOException if the TDB file cannot be loaded
     */
    public void setModel(String tdbFilePath, List<String> elements, List<String> phases)
            throws IOException {
        setModel(tdbFilePath, elements, phases, PhaseModelKind.AUTO);
    }

    /**
     * As {@link #setModel(String, List, List)}, but selecting which
     * Gibbs-energy model to build per phase (see {@link PhaseModelKind}).
     * The model kind is part of the model identity, so changing it forces
     * a rebuild.
     */
    public void setModel(String tdbFilePath, List<String> elements, List<String> phases,
                         PhaseModelKind modelKind) throws IOException {
        ModelKey requested = new ModelKey(tdbFilePath, elements, phases, modelKind);
        if (requested.equals(currentKey)) {
            return;
        }

        if (!new java.io.File(tdbFilePath).exists()) {
            throw new IllegalArgumentException(
                    "Database not found: " + tdbFilePath
                    + ". Available databases: " + availableDatabases());
        }

        List<String> validElements = availableElements(tdbFilePath);
        List<String> badElements = new ArrayList<>();
        for (String el : elements) {
            if (!validElements.contains(el)) {
                badElements.add(el);
            }
        }
        if (!badElements.isEmpty()) {
            throw new IllegalArgumentException(
                    "Element(s) not found in " + tdbFilePath + ": " + badElements
                    + ". Choose a database first, then elements from: " + validElements);
        }

        List<String> validPhases = availablePhasesFor(tdbFilePath, elements);
        List<String> badPhases = new ArrayList<>();
        for (String ph : phases) {
            if (!validPhases.contains(ph)) {
                badPhases.add(ph);
            }
        }
        if (!badPhases.isEmpty()) {
            throw new IllegalArgumentException(
                    "Phase(s) not valid for elements " + elements + " in " + tdbFilePath
                    + ": " + badPhases + ". Choose a database and elements first, then "
                    + "phases from: " + validPhases);
        }

        this.currentSystem =
                ThermodynamicSystem.build(tdbFilePath, elements, phases, modelKind);
        this.currentKey = requested;
        this.currentEquilibriumResult = null;
        this.currentInitialState = null;
        this.currentStepResult = null;
        this.currentCoarseDiagramResult = null;
        this.currentPhaseDiagram = null;
    }

    /** True once {@link #setModel} has succeeded at least once. */
    public boolean hasModel() {
        return currentSystem != null;
    }

    /**
     * The currently held system.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     */
    public ThermodynamicSystem currentSystem() {
        if (currentSystem == null) {
            throw new IllegalStateException("No model set -- call setModel() first");
        }
        return currentSystem;
    }

    /** Directory scanned by {@link #availableDatabases()} for {@code .tdb} files. */
    private static final String DATABASE_DIRECTORY = "data";

    /**
     * Lists the {@code .tdb} database files available to choose from, in
     * {@value #DATABASE_DIRECTORY} relative to the working directory --
     * the same fixed location every hardcoded default path in this
     * codebase already assumes (no new configuration surface).
     *
     * <p>This is the browsing counterpart to {@link #setModel} one step
     * earlier than {@link #availableElements}: "which files exist to pick
     * from" rather than "what's inside one already-chosen file." A UI's
     * database dropdown/list calls this (not a filesystem/{@code File}
     * API directly) so that GUI, CLI, and API all discover the same set
     * of databases through one shared, testable path.
     *
     * <p>Returns paths relative to {@value #DATABASE_DIRECTORY} (e.g.
     * {@code "data/VZR-re2.TDB"}), matching the relative-path convention
     * every other {@code ApplicationLayer} method already accepts.
     * Returns an empty list if the directory doesn't exist; never throws
     * for a missing directory, since "no databases found" is a normal,
     * displayable UI state, not an error.
     */
    public List<String> availableDatabases() {
        java.io.File dir = new java.io.File(DATABASE_DIRECTORY);
        java.io.File[] files = dir.listFiles(
                (d, name) -> name.toLowerCase().endsWith(".tdb"));
        if (files == null) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (java.io.File f : files) {
            paths.add(DATABASE_DIRECTORY + "/" + f.getName());
        }
        return paths;
    }

    /**
     * Lists every element symbol available in {@code tdbFilePath}, without
     * requiring any elements or phases to be chosen yet and without
     * building a {@link ThermodynamicSystem}.
     *
     * <p>This is the browsing counterpart to {@link #setModel}: a UI's
     * "pick a database" step calls this (not {@link #setModel}, and not
     * any database/parser class directly) to populate a database's
     * element list before the user has chosen which ones to use.
     *
     * @throws IOException if the TDB file cannot be loaded
     */
    public List<String> availableElements(String tdbFilePath) throws IOException {
        browseDatabase.load(tdbFilePath);
        return new ArrayList<>(((TdbParser) browseDatabase).getElementNames());
    }


    /**
     * Lists the phase names available in {@code tdbFilePath} whose
     * constituents are a subset of {@code elements} -- the browsing
     * counterpart to {@link #setModel} for a UI's "pick phases" step,
     * called once elements are chosen but before a phase selection is
     * confirmed (i.e. before there is a full model to build).
     *
     * @throws IOException if the TDB file cannot be loaded
     */
    public List<String> availablePhasesFor(String tdbFilePath, List<String> elements)
            throws IOException {
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }
        browseDatabase.load(tdbFilePath);
        DatabasePort scoped = browseDatabase.extractSystem(elements.toArray(new String[0]));
        return new ArrayList<>(scoped.getPhaseNames());
    }

    /**
     * Runs a single-point equilibrium calculation against the currently held
     * system and stores the result; does not return it directly -- read it
     * back via {@link #currentEquilibriumResult()}.
     *
     * <p>Internally, {@link EquilibriumSolverV2#solve} calls back into the
     * held {@code GibbsEnergyModel} instances once per Newton iteration
     * before this method returns once. This is Sundman's Algorithm A
     * (site-fraction / y-facing formulation); the legacy mole-fraction
     * (x-facing) {@code EquilibriumSolver} is no longer used here.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     */
    public void calculateEquilibrium(double T, double P, double[] compOverAll) {
        this.currentEquilibriumResult =
                new EquilibriumSolverV2().solve(T, P, compOverAll, currentSystem().phaseModels());
    }

    /**
     * Runs only the equilibrium initializer -- {@link GridMinimizer}'s
     * site-fraction grid sampling and lower-convex-hull search -- against
     * the currently held system, without the Newton iteration
     * {@link #calculateEquilibrium} additionally performs. Stores the
     * result; read it back via {@link #currentInitialState()}.
     *
     * <p>This is a distinct calculation type dispatched to the Calculation
     * layer, the same way {@link #calculateEquilibrium} and
     * {@link #calculatePhaseDiagram} are -- {@link GridMinimizer} lives in
     * {@code calc/equil} alongside {@link EquilibriumSolverV2} for exactly
     * this reason, and this method delegates to it in one line, the same
     * shape as {@link #calculateEquilibrium}. It mirrors pycalphad's own
     * separation between {@code calculate()}/{@code starting_point()}
     * (grid sampling and convex-hull starting point, independently
     * callable and testable) and {@code equilibrium()} (which additionally
     * runs the Newton solve): the initializer is a genuinely separate
     * calculation there, not merely an internal step of the full solve.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     */
    public void calculateInitialState(double T, double P, double[] compOverAll) {
        this.currentInitialState =
                new GridMinimizer().solve(currentSystem().phaseModels(), T, P, compOverAll);
    }

    /**
     * Runs a full, automated phase-diagram calculation against the
     * currently held system -- multi-line/node ZPF stitching, not a
     * single line like {@link #calculateStep}. Stores the result; read it
     * back via {@link #currentPhaseDiagram()}.
     *
     * @param axes       1 axis (STEP) or 2 axes (binary MAP, axes[1] must
     *                   be COMPOSITION); {@code startAxes} supplies each
     *                   axis's own starting value, same length/order
     * @param startAxes  starting value per axis, same length as {@code axes}
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     * @throws IllegalArgumentException if {@code axes.length} is not 1 or 2,
     *         {@code startAxes.length != axes.length}, or (2-axis case)
     *         {@code axes[1].type != COMPOSITION}
     */
    public void calculatePhaseDiagram(AxisConfig[] axes, double[] startAxes,
                                       double fixedT, double fixedP, double[] comp) {
        ThermodynamicSystem system = currentSystem();
        int numComponents = currentKey.elements().size();

        this.currentPhaseDiagram = PhaseDiagramEngine.calculatePhaseDiagram(
                numComponents, axes, startAxes, fixedT, fixedP, comp, system.phaseModels());
    }

    /**
     * Runs a single-axis property scan (sweep one variable, sample the
     * equilibrium at each point) against the currently held system --
     * Sundman 2021 Calphad 75, Algorithm B's step branch. Stores the
     * result; read it back via {@link #currentStepResult()}.
     *
     * <p>Delegates to {@link StepTracer}, which walks {@code axis} from
     * its {@code min} to {@code max} in {@code step} increments, calling
     * {@link EquilibriumSolverV2#solve} fresh at every point (no warm
     * start) and locating each stable-phase-set change by black-box
     * bisection rather than Sundman's own exact zero-amount-phase
     * condition (a separate, larger piece of work -- see
     * {@code StepTracer}'s own Javadoc). Unlike {@link #calculatePhaseDiagram},
     * a step never fixes a phase at zero amount and never branches into
     * multiple exits.
     *
     * @param axis    the axis to walk; its {@link AxisConfig.Type} selects
     *                which of {@code fixedT}/{@code fixedP}/{@code comp} is
     *                overridden by the swept value
     * @param fixedT  temperature used when {@code axis.type != TEMPERATURE}
     * @param fixedP  pressure used when {@code axis.type != PRESSURE}
     * @param comp    overall composition used when
     *                {@code axis.type != COMPOSITION}
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     */
    public void calculateStep(AxisConfig axis, double fixedT, double fixedP, double[] comp) {
        this.currentStepResult =
                new StepTracer().trace(axis, fixedT, fixedP, comp, currentSystem().phaseModels());
    }

    /**
     * Runs a coarse binary phase diagram: samples a 2D grid over
     * {@code axisX}/{@code axisY} (composition x temperature is the
     * standard case), calling the full equilibrium solver independently
     * at each point; read the result back via
     * {@link #currentCoarseDiagramResult()}. See
     * {@link calc.diagram.CoarseDiagramTracer} for the sampling and
     * per-point failure-isolation details.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     */
    public void calculateCoarseBinaryDiagram(AxisConfig axisX, AxisConfig axisY,
                                              double fixedT, double fixedP, double[] comp) {
        calculateCoarseBinaryDiagram(axisX, axisY, fixedT, fixedP, comp, null);
    }

    /**
     * As {@link #calculateCoarseBinaryDiagram(AxisConfig, AxisConfig, double, double, double[])},
     * additionally reporting progress after each completed row via
     * {@code onProgress}, if non-null (see {@code CoarseDiagramTracer}).
     */
    public void calculateCoarseBinaryDiagram(AxisConfig axisX, AxisConfig axisY,
                                              double fixedT, double fixedP, double[] comp,
                                              java.util.function.Consumer<String> onProgress) {
        this.currentCoarseDiagramResult =
                new CoarseDiagramTracer().traceBinary(axisX, axisY, fixedT, fixedP, comp,
                        currentSystem().phaseModels(), onProgress);
    }

    /**
     * Runs a coarse ternary phase diagram: samples a 2D grid over two
     * composition axes at fixed T/P, with the remaining component(s)'
     * fraction implied by the simplex constraint; read the result back
     * via {@link #currentCoarseDiagramResult()}. See
     * {@link calc.diagram.CoarseDiagramTracer} for the sampling and
     * per-point failure-isolation details.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     */
    public void calculateCoarseTernaryDiagram(AxisConfig axisCompI, AxisConfig axisCompJ,
                                               double fixedT, double fixedP, double[] comp) {
        calculateCoarseTernaryDiagram(axisCompI, axisCompJ, fixedT, fixedP, comp, null);
    }

    /**
     * As {@link #calculateCoarseTernaryDiagram(AxisConfig, AxisConfig, double, double, double[])},
     * additionally reporting progress after each completed row via
     * {@code onProgress}, if non-null (see {@code CoarseDiagramTracer}).
     */
    public void calculateCoarseTernaryDiagram(AxisConfig axisCompI, AxisConfig axisCompJ,
                                               double fixedT, double fixedP, double[] comp,
                                               java.util.function.Consumer<String> onProgress) {
        this.currentCoarseDiagramResult =
                new CoarseDiagramTracer().traceTernary(axisCompI, axisCompJ, fixedT, fixedP, comp,
                        currentSystem().phaseModels(), onProgress);
    }

    /**
     * The most recent equilibrium result, or {@code null} if none has
     * completed yet, or it was invalidated by a subsequent {@link #setModel}
     * call onto a different system.
     */
    public EquilibriumResult currentEquilibriumResult() {
        return currentEquilibriumResult;
    }

    /**
     * The most recent initializer-only result from
     * {@link #calculateInitialState}, or {@code null} if none has
     * completed yet, or it was invalidated by a subsequent {@link #setModel}
     * call onto a different system.
     */
    public EquilibriumResult currentInitialState() {
        return currentInitialState;
    }

    /**
     * The most recent step-calculation result, or {@code null} (same
     * rules as {@link #currentEquilibriumResult()}).
     */
    public PhaseDiagramResult currentStepResult() {
        return currentStepResult;
    }

    /**
     * The most recent coarse binary/ternary phase diagram result, or
     * {@code null} (same rules as {@link #currentEquilibriumResult()}).
     */
    public CoarseDiagramResult currentCoarseDiagramResult() {
        return currentCoarseDiagramResult;
    }

    /**
     * The most recent {@link #calculatePhaseDiagram} result, or {@code
     * null} (same rules as {@link #currentEquilibriumResult()}).
     */
    public PhaseDiagramResult currentPhaseDiagram() {
        return currentPhaseDiagram;
    }
}
