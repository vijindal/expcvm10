package session;

import calc.diagram.AxisConfig;
import calc.diagram.DiagramTracer;
import calc.diagram.PhaseDiagram;
import calc.equil.EquilibriumSolver;
import system.ThermodynamicSystem;
import system.database.TdbParser;
import system.ports.DatabasePort;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * UI-agnostic coordinator sitting above the Thermodynamic System Layer and
 * the Calculation Layer, per {@code docs/plan-3layer-core-dataflow.md}.
 *
 * <p>A caller (GUI, CLI, API -- anything) sends model details via
 * {@link #setModel} and calculation details via one of four calculation
 * methods -- {@link #calculateEquilibrium} (single point),
 * {@link #calculateStep}, {@link #calculateMap} (property sampled over one
 * or two axes), or {@link #calculatePhaseDiagram} (phase-boundary tracing --
 * a substantially more complex calculation than the other three, kept as
 * its own type rather than unified with step/map) -- then reads results
 * back via the matching {@code currentXxx()} accessor rather than getting
 * them returned directly. This class is the single point of contact for a
 * caller in both directions; it never exposes {@link ThermodynamicSystem}
 * construction or a solver to be called directly.
 *
 * <p>{@code calculateStep}/{@code calculateMap} are declared with their
 * intended parameter shapes but not yet implemented -- no plain
 * property-sampling engine exists in this codebase yet (see their Javadoc).
 *
 * <p>{@link #setModel} rebuilds the held {@link ThermodynamicSystem} only
 * when the model details (database path, elements, phases) actually change,
 * so multiple calculations against the same system -- e.g. a single-point
 * calculation followed by a phase diagram -- do not re-parse the database.
 *
 * <p>This class is also the single point of contact for browsing a
 * database, not just calculating against one: {@link #availableElements}
 * and {@link #availablePhasesFor} answer "what elements/phases exist in
 * this file" without requiring a full model build, for a UI's
 * pick-a-database / pick-elements steps that happen before a phase
 * selection (and therefore a {@link #setModel} call) is possible. A UI
 * must never reach a database/parser class directly for this -- doing so
 * defeats the caching this class and {@code TdbParser} provide and
 * reintroduces the redundant-parsing problem this design fixes (see
 * {@code docs/plan-gui-calculationsession-wiring.md}).
 *
 * <p>Not thread-safe; one session is intended for one sequential caller.
 */
public final class CalculationSession {

    /** Identifies "what system is currently loaded." */
    public record ModelKey(String tdbFilePath, List<String> elements, List<String> phases) {}

    private ModelKey currentKey;
    private ThermodynamicSystem currentSystem;
    private EquilibriumResult currentEquilibriumResult;
    private PhaseDiagram currentPhaseDiagram;

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
     * @throws IOException if the TDB file cannot be loaded
     */
    public void setModel(String tdbFilePath, List<String> elements, List<String> phases)
            throws IOException {
        ModelKey requested = new ModelKey(tdbFilePath, elements, phases);
        if (requested.equals(currentKey)) {
            return;
        }
        this.currentSystem = ThermodynamicSystem.build(tdbFilePath, elements, phases);
        this.currentKey = requested;
        this.currentEquilibriumResult = null;
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
     * <p>Internally, {@link EquilibriumSolver#solve} calls back into the
     * held {@code GibbsEnergyModel} instances once per Newton iteration
     * before this method returns once.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     */
    public void calculateEquilibrium(double T, double P, double[] compOverAll) {
        this.currentEquilibriumResult =
                new EquilibriumSolver().solve(T, P, compOverAll, currentSystem().phaseModels());
    }

    /**
     * Runs a phase-diagram calculation against the currently held system and
     * stores the result; read it back via {@link #currentPhaseDiagram()}.
     *
     * <p>Distinct from {@link #calculateStep}/{@link #calculateMap}: a phase
     * diagram traces phase boundaries (Sundman's Algorithms B/C1/C2 -- see
     * {@link DiagramTracer}), not just a property sampled over an axis grid.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     */
    public void calculatePhaseDiagram(AxisConfig[] axes, double[] startAxes,
                                       double fixedT, double fixedP, double[] comp) {
        this.currentPhaseDiagram = new DiagramTracer().calculate(
                currentSystem().phaseModels(), axes, startAxes, fixedT, fixedP, comp);
    }

    /**
     * Runs a single-axis property scan (sweep one variable, sample the
     * equilibrium at each point) against the currently held system.
     *
     * <p>Not yet implemented: no engine for plain property sampling (as
     * opposed to phase-boundary tracing) exists in this codebase yet.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     * @throws UnsupportedOperationException always, until a real step engine exists
     */
    public void calculateStep(AxisConfig axis, double fixedT, double fixedP, double[] comp) {
        currentSystem();   // still enforce the usual precondition
        throw new UnsupportedOperationException("Step calculation not yet implemented");
    }

    /**
     * Runs a two-axis property scan (sweep two variables on a grid, sample
     * the equilibrium at each point) against the currently held system.
     *
     * <p>Not yet implemented: no engine for plain property sampling (as
     * opposed to phase-boundary tracing) exists in this codebase yet.
     *
     * @throws IllegalStateException if {@link #setModel} hasn't been called yet
     * @throws UnsupportedOperationException always, until a real map engine exists
     */
    public void calculateMap(AxisConfig axis0, AxisConfig axis1,
                              double fixedT, double fixedP, double[] comp) {
        currentSystem();   // still enforce the usual precondition
        throw new UnsupportedOperationException("Map calculation not yet implemented");
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
     * The most recent phase-diagram result, or {@code null} (same rules as
     * {@link #currentEquilibriumResult()}).
     */
    public PhaseDiagram currentPhaseDiagram() {
        return currentPhaseDiagram;
    }
}
