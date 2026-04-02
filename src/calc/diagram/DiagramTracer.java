package calc.diagram;

import system.ports.EquilibriumResult;
import system.model.GibbsEnergyModel;
import calc.equil.EquilibriumSolver;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Algorithm B — Phase diagram orchestrator.
 *
 * <p>Drives the full phase diagram calculation by:
 * <ol>
 *   <li><b>STEP (1 axis)</b>: scans the single axis, records nodes wherever
 *       the stable phase set changes, accumulates points into {@link DiagramLine}s.</li>
 *   <li><b>MAP (2 axes)</b>: finds the first phase boundary by scanning the
 *       primary axis; creates the initial {@link DiagramNode}; then processes
 *       all unvisited {@link DiagramExit}s through {@link LineStepper} and
 *       {@link PhaseChangeHandler} until the diagram is complete.</li>
 * </ol>
 *
 * <h2>Main MAP loop (Algorithm B)</h2>
 * <pre>
 *   findInitialNode()          → first ZPF crossing, creates node with exits
 *   while unvisited exits exist:
 *     exit = nextUnvisitedExit()
 *     line = LineStepper.followLine(exit, ...)
 *     if line.phaseChangeDetected:
 *         newNode = PhaseChangeHandler.handle(line, ...)
 *     addLine(line)
 * </pre>
 */
public class DiagramTracer {

    private static final Logger LOG = Logger.getLogger(DiagramTracer.class.getName());

    /** Safety cap on exit-following iterations (prevents infinite loops). */
    private static final int MAX_EXIT_ITERATIONS = 500;

    private final EquilibriumSolver    solver;
    private final LineStepper          lineStepper;
    private final PhaseChangeHandler   phaseChangeHandler;
    private final InvariantHandler     invariantHandler;

    public DiagramTracer() {
        this.solver             = new EquilibriumSolver();
        this.lineStepper        = new LineStepper(solver);
        this.phaseChangeHandler = new PhaseChangeHandler(solver);
        this.invariantHandler   = new InvariantHandler();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Calculate a phase diagram.
     *
     * @param candidates       all candidate phase models
     * @param axes             1 axis = STEP calculation; 2 axes = MAP calculation
     * @param startAxisValues  starting axis values (length must equal axes.length)
     * @param fixedT           temperature (K) when T is not a diagram axis
     * @param fixedP           pressure (Pa) when P is not a diagram axis
     * @param compOverAll      overall composition (mole fractions, length = numComponents)
     * @return the computed {@link PhaseDiagram}
     */
    public PhaseDiagram calculate(List<GibbsEnergyModel> candidates,
                                   AxisConfig[] axes,
                                   double[] startAxisValues,
                                   double fixedT, double fixedP,
                                   double[] compOverAll) {

        String[] names = new String[axes.length];
        double[] mins  = new double[axes.length];
        double[] maxs  = new double[axes.length];
        for (int i = 0; i < axes.length; i++) {
            names[i] = axes[i].name;
            mins[i]  = axes[i].min;
            maxs[i]  = axes[i].max;
        }
        PhaseDiagram diagram = new PhaseDiagram(names, mins, maxs);

        if (axes.length == 1) {
            calculateStep(candidates, axes, startAxisValues, fixedT, fixedP,
                          compOverAll, diagram);
        } else {
            calculateMap(candidates, axes, startAxisValues, fixedT, fixedP,
                         compOverAll, diagram);
        }

        return diagram;
    }

    // ------------------------------------------------------------------
    // STEP calculation (1 axis)
    // ------------------------------------------------------------------

    /**
     * Scan the single axis from min to max.  Each phase-set change creates a
     * node.  Points between changes are collected into {@link DiagramLine}s.
     */
    private void calculateStep(List<GibbsEnergyModel> candidates,
                                AxisConfig[] axes,
                                double[] startAxisValues,
                                double fixedT, double fixedP,
                                double[] compOverAll,
                                PhaseDiagram diagram) {

        AxisConfig ax = axes[0];
        double[] current = startAxisValues.clone();

        EquilibriumResult prevEq     = null;
        Set<String>       prevStable = null;
        double[]          prevAxes   = null;
        DiagramNode       prevNode   = null;
        DiagramLine       currentLine = null;

        for (double v = ax.min; v <= ax.max + ax.step * 0.5; v += ax.step) {
            current[0] = Math.min(v, ax.max);
            double     T    = resolveT(current, axes, fixedT);
            double     P    = resolveP(current, axes, fixedP);
            double[]   comp = resolveComp(current, axes, compOverAll);

            EquilibriumResult eq;
            try {
                eq = solver.solve(T, P, comp, candidates);
            } catch (Exception ex) {
                LOG.fine("STEP: solver failed at v=" + v + ": " + ex.getMessage());
                continue;
            }
            if (!eq.isConverged()) continue;

            Set<String> stable = stableNames(eq);

            // ── Phase-set change → new node ───────────────────────────────
            if (prevStable != null && !stable.equals(prevStable)) {

                DiagramNode node = diagram.addNode(prevEq, prevAxes.clone());

                if (currentLine != null) {
                    currentLine.endNode = node;
                    diagram.addLine(currentLine);
                }
                prevNode = node;

                // Start new line from this node
                currentLine = new DiagramLine(node, null,
                        new LinkedHashSet<>(stable));
            }

            // ── First point: create seed node and first line ──────────────
            if (currentLine == null) {
                DiagramNode seed = diagram.addNode(eq, current.clone());
                currentLine = new DiagramLine(seed, null,
                        new LinkedHashSet<>(stable));
            }

            currentLine.addPoint(eq, current.clone());
            prevEq     = eq;
            prevStable = stable;
            prevAxes   = current.clone();
        }

        if (currentLine != null) {
            diagram.addLine(currentLine);
        }

        LOG.info("STEP: " + diagram.getNodes().size() + " nodes, "
                + diagram.getLines().size() + " lines.");
    }

    // ------------------------------------------------------------------
    // MAP calculation (2 axes)
    // ------------------------------------------------------------------

    /**
     * Follow all ZPF lines to build a 2-axis phase diagram.
     *
     * <p>Step 1: find the first node by scanning the primary axis.
     * Step 2: process all unvisited exits until none remain.
     */
    private void calculateMap(List<GibbsEnergyModel> candidates,
                               AxisConfig[] axes,
                               double[] startAxisValues,
                               double fixedT, double fixedP,
                               double[] compOverAll,
                               PhaseDiagram diagram) {

        // ── Step 1: find initial node ─────────────────────────────────────
        DiagramNode firstNode = findInitialNode(candidates, axes, startAxisValues,
                fixedT, fixedP, compOverAll, diagram);

        if (firstNode == null) {
            LOG.warning("DiagramTracer: no phase change found while scanning — "
                    + "diagram may be entirely single-phase.");
            return;
        }

        LOG.info("DiagramTracer: first node at " + Arrays.toString(firstNode.axisValues));

        // ── Step 2: process all unvisited exits ───────────────────────────
        int iterations = 0;
        DiagramExit exit;

        while ((exit = diagram.nextUnvisitedExit()) != null
                && iterations < MAX_EXIT_ITERATIONS) {

            iterations++;
            exit.visited = true;

            LOG.fine("DiagramTracer [" + iterations + "]: following " + exit);

            DiagramLine line = lineStepper.followLine(
                    exit, candidates, axes, fixedT, fixedP);
            diagram.addLine(line);

            if (line.phaseChangeDetected) {
                try {
                    DiagramNode newNode = phaseChangeHandler.handle(
                            line, candidates, axes, fixedT, fixedP, diagram);
                    line.endNode = newNode;
                    LOG.fine("DiagramTracer: new node " + newNode.id
                            + " with " + newNode.getExits().size() + " exits.");

                    // ── Connect InvariantHandler for invariant nodes ──
                    // An invariant point has f = c + n_axes - p = 0, i.e., p = c + n_axes phases.
                    // For efficiency, InvariantHandler only handles p ≥ 3.
                    if (newNode.equilibrium.getStablePhases().size() >= 3) {
                        invariantHandler.findExits(newNode, candidates, axes);
                        LOG.fine("DiagramTracer: InvariantHandler computed " + newNode.getExits().size()
                                + " exits for node " + newNode.id);
                    }
                } catch (Exception ex) {
                    LOG.warning("DiagramTracer: PhaseChangeHandler failed at exit "
                            + exit + ": " + ex.getMessage());
                }
            }
        }

        if (iterations >= MAX_EXIT_ITERATIONS) {
            LOG.warning("DiagramTracer: reached MAX_EXIT_ITERATIONS="
                    + MAX_EXIT_ITERATIONS + " — diagram may be incomplete.");
        }

        LOG.info("DiagramTracer MAP done: " + diagram.getNodes().size() + " nodes, "
                + diagram.getLines().size() + " lines, "
                + iterations + " exits followed.");
    }

    // ------------------------------------------------------------------
    // Initial node discovery (MAP)
    // ------------------------------------------------------------------

    /**
     * Find the first phase boundary by doing a 2D grid scan over both axes.
     *
     * <p>For each value of axis 1 (outer loop, coarse step × 4 for speed),
     * scans axis 0 (inner loop, normal step) looking for a phase-set change.
     * Returns the first {@link DiagramNode} found, populated with initial exits.
     * Also tries a single-axis scan along axis 1 at the axis-0 midpoint in case
     * the boundary is along the secondary axis.
     */
    private DiagramNode findInitialNode(List<GibbsEnergyModel> candidates,
                                         AxisConfig[] axes,
                                         double[] startAxisValues,
                                         double fixedT, double fixedP,
                                         double[] compOverAll,
                                         PhaseDiagram diagram) {

        AxisConfig ax0 = axes[0];
        AxisConfig ax1 = axes.length > 1 ? axes[1] : null;

        // ── Outer loop: step through axis 1 (coarser, 4× step) ───────────
        double outerStep = ax1 != null ? Math.max(ax1.step, (ax1.max - ax1.min) / 20.0) : 0;
        double outerMin  = ax1 != null ? ax1.min : 0;
        double outerMax  = ax1 != null ? ax1.max : 0;

        int outerCount = ax1 != null ? (int) Math.round((outerMax - outerMin) / outerStep) + 1 : 1;

        for (int oi = 0; oi < outerCount; oi++) {
            double[] current = startAxisValues.clone();
            if (ax1 != null) current[1] = Math.min(outerMin + oi * outerStep, outerMax);

            EquilibriumResult prevEq     = null;
            Set<String>       prevStable = null;
            double[]          prevAxes   = null;

            for (double v = ax0.min; v <= ax0.max + ax0.step * 0.5; v += ax0.step) {
                current[0] = Math.min(v, ax0.max);
                double   T    = resolveT(current, axes, fixedT);
                double   P    = resolveP(current, axes, fixedP);
                double[] comp = resolveComp(current, axes, compOverAll);

                EquilibriumResult eq;
                try {
                    eq = solver.solve(T, P, comp, candidates);
                } catch (Exception ex) {
                    prevEq = null; prevStable = null; prevAxes = null;
                    continue;
                }
                if (!eq.isConverged()) { prevEq = null; prevStable = null; continue; }

                Set<String> stable = stableNames(eq);

                if (prevStable != null && !stable.equals(prevStable)) {
                    DiagramNode node = diagram.addNode(prevEq, prevAxes.clone());
                    addInitialExits(node, prevEq, axes);
                    LOG.info("DiagramTracer: initial node found at ax1="
                            + (ax1 != null ? current[1] : "n/a")
                            + " ax0=" + prevAxes[0]);
                    return node;
                }

                prevEq     = eq;
                prevStable = stable;
                prevAxes   = current.clone();
            }
        }

        return null;
    }

    /**
     * Attach initial exits to the first node.
     *
     * <p>For each stable phase at the node, adds two exits (±direction along
     * axis 0), making each stable phase in turn the ZPF constraint.  This
     * allows LineStepper to follow every phase boundary leaving the node.
     */
    private void addInitialExits(DiagramNode node,
                                  EquilibriumResult eq,
                                  AxisConfig[] axes) {
        // Add exits in ALL axis directions so that both horizontal (x) and
        // vertical (T) phase boundaries can be traced from the initial node.
        for (int axisIdx = 0; axisIdx < axes.length; axisIdx++) {
            for (EquilibriumResult.PhaseResult pr : eq.getStablePhases()) {
                node.addExit(new DiagramExit(node, pr.phaseName, null, axisIdx, +1));
                node.addExit(new DiagramExit(node, pr.phaseName, null, axisIdx, -1));
            }
        }
        LOG.fine("addInitialExits: node " + node.id
                + " → " + node.getExits().size() + " exits.");
    }

    // ------------------------------------------------------------------
    // Axis resolution helpers (mirrored from LineStepper)
    // ------------------------------------------------------------------

    private double resolveT(double[] av, AxisConfig[] axes, double fixedT) {
        for (int i = 0; i < axes.length; i++)
            if (axes[i].type == AxisConfig.Type.TEMPERATURE) return av[i];
        return fixedT;
    }

    private double resolveP(double[] av, AxisConfig[] axes, double fixedP) {
        for (int i = 0; i < axes.length; i++)
            if (axes[i].type == AxisConfig.Type.PRESSURE) return av[i];
        return fixedP;
    }

    private double[] resolveComp(double[] av, AxisConfig[] axes, double[] base) {
        double[] comp = base.clone();
        boolean hasComp = false;
        for (int i = 0; i < axes.length; i++) {
            if (axes[i].type == AxisConfig.Type.COMPOSITION) {
                comp[axes[i].componentIndex] = av[i];
                hasComp = true;
            }
        }
        if (hasComp) {
            // Re-normalise: assign last component = 1 - sum of others
            double sum = 0;
            for (int i = 0; i < comp.length - 1; i++) sum += comp[i];
            comp[comp.length - 1] = Math.max(0, 1.0 - sum);
        }
        return comp;
    }

    private Set<String> stableNames(EquilibriumResult eq) {
        Set<String> names = new LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : eq.getStablePhases())
            names.add(pr.phaseName);
        return names;
    }
}
