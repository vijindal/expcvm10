package calc.diagram;

import calc.equil.GridMinimizer;
import calc.diagram.PhaseDiagramResult.LineSegment;
import calc.diagram.PhaseDiagramResult.NodePoint;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

/**
 * Top-level orchestration for phase-diagram tracing, implementing Sundman 2021
 * Algorithms A/B/C1/C2/D. Provides one method per flowchart stage (see
 * {@code docs/phase_diagram_engine_flowchart.md}). Supported diagram types:
 * binary T-x, ternary isothermal, ternary isopleth, and property/step diagrams.
 */
public final class PhaseDiagramEngine {

    private PhaseDiagramEngine() {
    }

    /**
     * Parses the TDB and builds Gibbs-energy models for each candidate phase.
     *
     * @param tdbFilePath path to the TDB file
     * @param elements list of element symbols
     * @param candidatePhases list of phase names
     * @return the built thermodynamic system
     * @throws IOException if the TDB file cannot be read
     */
    public static ThermodynamicSystem defineSystem(
            String tdbFilePath,
            List<String> elements,
            List<String> candidatePhases) throws IOException {

        return ThermodynamicSystem.build(tdbFilePath, elements, candidatePhases);
    }

    /**
     * Validates that the number of conditions supplied equals n+2 for an
     * n-component system. An n-component equilibrium requires exactly n+2
     * thermodynamic conditions (T, P, and n mole fractions by default, or
     * allowed substitutes like chemical potential, entropy, enthalpy, etc.).
     *
     * @param numComponents the number of independent components (n)
     * @param numConditionsSupplied the number of conditions being set
     * @throws IllegalArgumentException if the count does not match n+2
     */
    public static void validateConditionCount(int numComponents, int numConditionsSupplied) {
        int requiredConditions = numComponents + 2;
        int idf = requiredConditions - numConditionsSupplied;
        if (idf != 0) {
            throw new IllegalArgumentException(
                    "Degrees of freedom not zero: " + numComponents + " components require exactly "
                    + requiredConditions + " conditions (n+2), but " + numConditionsSupplied
                    + " were supplied (idf=" + idf + ").");
        }
    }

    /**
     * Generates starting point(s) for diagram tracing. Currently returns
     * a single starting point; multiple starting points are a manual,
     * user-driven operation (issue #TODO: linked disconnected components).
     *
     * @param singleStartWalkValue the starting value along the walk axis
     * @return a list containing the single starting point
     */
    public static List<double[]> generateStartingPoints(double singleStartWalkValue) {
        return List.of(new double[] { singleStartWalkValue });
    }

    /**
     * Algorithm B orchestration: full diagram tracing from conditions
     * through classification. Solves the initial equilibrium once, then
     * branches on axis count to either STEP (single-axis) or MAP (two-axis)
     * draining, and returns the complete classified result.
     *
     * @param axes 1 or 2 axes; if 2 axes, axes[1] must be COMPOSITION
     * @param startValues starting value per axis
     * @param fixedT temperature (used if not an axis)
     * @param fixedP pressure (used if not an axis)
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @return the classified phase diagram result
     * @throws IllegalArgumentException if axes.length not 1 or 2, or
     *         if 2-axis and axes[1].type != COMPOSITION
     * @throws IllegalStateException if initial equilibrium fails to converge
     */
    public static PhaseDiagramResult calculatePhaseDiagram(
            AxisConfig[] axes,
            double[] startValues,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        if (axes.length != startValues.length) {
            throw new IllegalArgumentException(
                    "axes.length (" + axes.length + ") must equal startValues.length ("
                    + startValues.length + ")");
        }
        if (axes.length != 1 && axes.length != 2) {
            throw new IllegalArgumentException(
                    "calculatePhaseDiagram supports 1 axis (STEP) or 2 axes (binary MAP) only; got "
                    + axes.length);
        }
        if (axes.length == 2 && axes[1].type != AxisConfig.Type.COMPOSITION) {
            throw new IllegalArgumentException(
                    "For 2-axis MAP, axes[1] must be COMPOSITION; got " + axes[1].type);
        }
        // --- Algorithm A: solve ONCE at the starting conditions ---
        // Use the FIRST axis's starting value for the initial solve
        EquilibriumResult initialEquilibrium = solveInitialEquilibrium(
                axes[0], fixedT, fixedP, startValues[0], compOverall, candidates);
                
        // --- Branch on axis count: STEP vs. MAP, each up to "+node, 2 exits" ---
        LineFollower.Setup setup;
        PhaseDiagramEngine.PlotType plotType;
        String[] axisNames;
        double[] axisMin, axisMax;
        int compositionAxisIndex, compositionComponentIndex;

        if (axes.length == 1) {
            setup = new StepDiagramTracer().setUp(
                    axes[0], fixedT, fixedP, compOverall, candidates, initialEquilibrium);

            plotType = PlotType.PROPERTY_OR_STEP_DIAGRAM;
            axisNames = new String[] { axes[0].name };
            axisMin = new double[] { axes[0].min };
            axisMax = new double[] { axes[0].max };
            compositionAxisIndex = -1;
            compositionComponentIndex = -1;
        } else {
            setup = new MapDiagramTracer().setUp(
                    axes[0], axes[1], fixedT, fixedP, startValues[0], compOverall, candidates,
                    initialEquilibrium);

            plotType = PlotType.BINARY_T_X;
            axisNames = new String[] { axes[0].name, axes[1].name };
            axisMin = new double[] { axes[0].min, axes[1].min };
            axisMax = new double[] { axes[0].max, axes[1].max };
            compositionAxisIndex = 1;
            compositionComponentIndex = axes[1].componentIndex;
        }

        // --- C1 (Fig. 5): both branches hand off here ---
        LineFollower.drain(setup, candidates);

        return classifyPlot(setup.registry(), plotType, axisNames, axisMin, axisMax,
                compositionAxisIndex, compositionComponentIndex);
    }

    /**
     * Solves a single equilibrium at the starting conditions. This is the
     * shared Algorithm A box that Algorithm B executes before branching
     * to STEP or MAP, ensuring both branches operate from the same
     * converged starting point.
     *
     * @param axis the first/walked axis
     * @param fixedT temperature (modified by axis if axis is TEMPERATURE)
     * @param fixedP pressure (modified by axis if axis is PRESSURE)
     * @param startWalkValue the axis value where to solve
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @return converged equilibrium result
     * @throws IllegalStateException if convergence fails
     */
    public static EquilibriumResult solveInitialEquilibrium(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        double t0 = fixedT, p0 = fixedP;
        double[] comp = compOverall.clone();
        switch (axis.type) {
            case TEMPERATURE: t0 = startWalkValue; break;
            case PRESSURE: p0 = startWalkValue; break;
            case COMPOSITION: comp = StepTracer.applyCompositionAxis(axis, startWalkValue, comp); break;
            default: throw new IllegalStateException("Unhandled axis type: " + axis.type);
        }

        EquilibriumResult result = EquilibriumSolveHelper.solveOrSentinel(t0, p0, comp, candidates);
        if (!result.isConverged()) {
            throw new IllegalStateException(
                    "Initial equilibrium did not converge at " + axis.name + "=" + startWalkValue);
        }
        return result;
    }

    /**
     * Drains the map diagram (2-axis ZPF tracing) from one starting point --
     * the 2-axis counterpart of {@link #drainStepLoop}. Walks the first
     * axis while keeping a phase at zero amount.
     *
     * @param walkAxis axis to walk
     * @param releaseAxis axis to release (must be COMPOSITION for binary MAP)
     * @param fixedT temperature
     * @param fixedP pressure
     * @param startWalkValue starting walk value
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     * @return registry of traced nodes and lines
     */
    public static NodeRegistry drainMapLoop(
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        return new MapDiagramTracer().drain(
                walkAxis, releaseAxis, fixedT, fixedP, startWalkValue, compOverall, candidates, startResult);
    }

    /**
     * Drains the map diagram using a ConditionSet for generalized diagram
     * types (binary, ternary isothermal, isopleth, etc.).
     *
     * @param conds full condition set for the diagram
     * @param searchAxisIndex index of the axis to search/walk
     * @param releaseAxisIndex index of the axis to release
     * @param startSearchValue starting value for the search axis
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @return registry of traced nodes and lines
     */
    public static NodeRegistry drainMapLoop(
            ConditionSet conds,
            int searchAxisIndex,
            int releaseAxisIndex,
            double startSearchValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        return new MapDiagramTracer().drain(
                conds, searchAxisIndex, releaseAxisIndex, startSearchValue, compOverall, candidates);
    }

    /**
     * Drains the step diagram (single-axis property scan) from one starting point.
     *
     * @param axis the axis to walk
     * @param fixedT temperature
     * @param fixedP pressure
     * @param compOverall overall composition
     * @param candidates candidate phase models
     * @param startResult pre-solved initial equilibrium, or null to solve it here
     * @return registry of traced nodes and lines
     */
    public static NodeRegistry drainStepLoop(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        return new StepDiagramTracer().drain(axis, fixedT, fixedP, compOverall, candidates, startResult);
    }

    private static final double GLOBAL_STABILITY_RELATIVE_TOLERANCE = 1.0e-4;

    /**
     * Checks whether the candidate equilibrium is globally stable via a
     * grid-based global minimization. Returns true if the candidate's Gibbs
     * energy is within tolerance of the true global minimum.
     *
     * @param candidateEquilibrium the equilibrium to check
     * @param candidates the candidate phase models
     * @return true if the candidate is globally stable
     */
    public static boolean isGloballyStable(
            EquilibriumResult candidateEquilibrium,
            List<GibbsEnergyModel> candidates) {

        double[] overallComposition = overallComposition(candidateEquilibrium);

        EquilibriumResult gridResult = new GridMinimizer().solve(
                candidates, candidateEquilibrium.getT(), candidateEquilibrium.getP(), overallComposition);

        double candidateGPerAtom = candidateEquilibrium.totalGPerAtom();
        double gridGPerAtom = gridResult.totalGPerAtom();

        double relativeDifference = (candidateGPerAtom - gridGPerAtom) / Math.abs(candidateGPerAtom);
        return relativeDifference <= GLOBAL_STABILITY_RELATIVE_TOLERANCE;
    }

    private static double[] overallComposition(EquilibriumResult result) {
        int nc = result.getMu().length;
        double[] atomsPerComponent = new double[nc];
        double totalAtoms = 0.0;
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            double phaseAtoms = pr.atoms();
            totalAtoms += phaseAtoms;
            for (int i = 0; i < nc; i++) {
                atomsPerComponent[i] += phaseAtoms * pr.x[i];
            }
        }
        double[] comp = new double[nc];
        for (int i = 0; i < nc; i++) {
            comp[i] = atomsPerComponent[i] / totalAtoms;
        }
        return comp;
    }

    // ------------------------------------------------------------------
    // (Inside the C1 loop, at a stable-set change) GIBBS PHASE RULE /
    // node classification, Algorithm D
    // ------------------------------------------------------------------

    /** Classification of a node in a phase diagram. */
    public enum NodeClass {
        /** Ordinary crossing with tie-line geometry (2 exits). */
        TIE_LINE_IN_PLANE,
        /** Ordinary crossing with isopleth geometry (3 exits). */
        ISOPLETH_CROSSING,
        /** Invariant point (f = 0, multiple exits via Algorithm D). */
        INVARIANT,
        /** Step-diagram continuation (1 exit). */
        STEP_CONTINUATION
    }

    /**
     * Classifies a node at a stable-phase-set change using the Gibbs phase
     * rule (f = n+2-p-c). Distinguishes between invariant (f=0) and
     * ordinary (f>0) nodes, and for ordinary nodes, distinguishes between
     * tie-line-in-plane and isopleth-crossing geometries based on whether
     * any composition is fixed outside the diagram axes.
     *
     * @param conds the full condition set for the diagram
     * @param numStablePhases number of stable phases at this node
     * @return classification of the node
     */
    public static NodeClass classifyNode(ConditionSet conds, int numStablePhases) {
        int numFixedPotentialConditions = 0;
        boolean hasFixedComposition = false;

        for (Condition c : conds.all()) {
            if (!c.isFixed()) continue;
            switch (c.variable) {
                case TEMPERATURE:
                case PRESSURE:
                    numFixedPotentialConditions++;
                    break;
                case COMPOSITION:
                    hasFixedComposition = true;
                    break;
                default:
                    // TOTAL_MOLES: neither a potential condition nor a
                    // composition -- does not affect either count.
            }
        }

        NodeClass ordinary = hasFixedComposition
                ? NodeClass.ISOPLETH_CROSSING
                : NodeClass.TIE_LINE_IN_PLANE;

        int f = conds.numComponents() + 2 - numStablePhases - numFixedPotentialConditions;
        return f == 0 ? NodeClass.INVARIANT : ordinary;
    }

    /**
     * Filters the traced line network, removing excluded lines and returning
     * the lines ready for plotting. Nodes are deduplicated inline during
     * tracing via {@link NodeRegistry#findOrCreate}.
     *
     * @param registry the traced node/line registry
     * @return all non-excluded lines in the registry
     */
    public static List<Line> mergeDedupNetwork(NodeRegistry registry) {
        List<Line> kept = new java.util.ArrayList<>();
        for (Node node : registry.getNodes()) {
            for (Line line : node.getLines()) {
                if (!line.isExcluded()) {
                    kept.add(line);
                }
            }
        }
        return kept;
    }

    /**
     * Identifies and labels phase regions from the traced line network.
     * Assigns stable-phase labels to each line and node, and computes
     * tie-triangles for 3-phase regions.
     *
     * @param registry the traced node/line registry
     * @return phase regions with labels and tie-triangles
     */
    public static PhaseRegions identifyPhaseRegions(NodeRegistry registry) {
        java.util.Map<Line, java.util.Set<String>> lineLabels = new java.util.LinkedHashMap<>();
        java.util.Map<Node, java.util.Set<String>> nodeLabels = new java.util.LinkedHashMap<>();
        List<PhaseRegions.TieTriangle> tieTriangles = new java.util.ArrayList<>();

        for (Node node : registry.getNodes()) {
            nodeLabels.put(node, node.stablePhaseNames);

            if (node.stablePhaseNames.size() == 3) {
                List<String> phaseNames = new java.util.ArrayList<>();
                List<double[]> vertices = new java.util.ArrayList<>();
                for (EquilibriumResult.PhaseResult pr : node.equilibrium.getStablePhases()) {
                    phaseNames.add(pr.phaseName);
                    vertices.add(pr.x);
                }
                tieTriangles.add(new PhaseRegions.TieTriangle(node, phaseNames, vertices));
            }

            for (Line line : node.getLines()) {
                lineLabels.put(line, lineStablePhaseNames(line));
            }
        }

        return new PhaseRegions(lineLabels, nodeLabels, tieTriangles);
    }

    /**
     * The stable-phase set bounding {@code line} -- every point sampled
     * along it shares the same stable-phase set by construction (a
     * {@link Line} terminates exactly when that set changes), so the
     * first sampled point's set suffices; empty if no points were
     * sampled (e.g. a line terminated immediately at an axis limit).
     */
    private static java.util.Set<String> lineStablePhaseNames(Line line) {
        List<EquilibriumResult> points = line.getPoints();
        if (points.isEmpty()) {
            return java.util.Set.of();
        }
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : points.get(0).getStablePhases()) {
            names.add(pr.phaseName);
        }
        return names;
    }

    // ------------------------------------------------------------------
    // CLASSIFY REQUESTED PLOT / VALIDATE PLOT
    // ------------------------------------------------------------------

    /** The plot types the flowchart's CLASSIFY REQUESTED PLOT box lists. */
    public enum PlotType {
        BINARY_T_X,
        ACTIVITY_OR_CHEMICAL_POTENTIAL,
        H_X_S_X_G_X,
        TERNARY_ISOTHERMAL,
        TERNARY_ISOPLETH,
        MULTICOMPONENT_ISOPLETH_OR_PSEUDO_ISOTHERMAL,
        PROPERTY_OR_STEP_DIAGRAM
    }

    /**
     * Converts the traced node/line network into a renderable phase diagram
     * result for the specified plot type. Validates axis count and converts
     * stored equilibria into line segments and node points. For diagrams with
     * a released composition axis, splits multi-phase lines into one segment
     * per stable phase using each phase's own composition.
     *
     * @param registry the traced network
     * @param requestedType the desired plot type
     * @param axisNames diagram axis names
     * @param axisMin minimum value per axis
     * @param axisMax maximum value per axis
     * @param compositionAxisIndex index of released composition axis (-1 if none)
     * @param compositionComponentIndex component index of composition axis
     * @return the complete classified phase diagram
     * @throws IllegalArgumentException if axis count does not match requestedType
     * @throws UnsupportedOperationException if requestedType is not yet implemented
     */
    public static PhaseDiagramResult classifyPlot(
            NodeRegistry registry,
            PlotType requestedType,
            String[] axisNames,
            double[] axisMin,
            double[] axisMax,
            int compositionAxisIndex,
            int compositionComponentIndex) {

        int expectedAxes = expectedAxisCount(requestedType);
        if (axisNames.length != expectedAxes || axisMin.length != expectedAxes || axisMax.length != expectedAxes) {
            throw new IllegalArgumentException(
                    requestedType + " requires exactly " + expectedAxes + " axis/axes, got "
                    + axisNames.length + " names / " + axisMin.length + " mins / "
                    + axisMax.length + " maxes -- not a well-posed diagram (VALIDATE PLOT).");
        }

        switch (requestedType) {
            case BINARY_T_X:
            case PROPERTY_OR_STEP_DIAGRAM:
            case TERNARY_ISOTHERMAL:
            case TERNARY_ISOPLETH:
                return buildResult(registry, axisNames, axisMin, axisMax,
                        compositionAxisIndex, compositionComponentIndex);

            case ACTIVITY_OR_CHEMICAL_POTENTIAL:
            case H_X_S_X_G_X:
            case MULTICOMPONENT_ISOPLETH_OR_PSEUDO_ISOTHERMAL:
            default:
                throw new UnsupportedOperationException(
                        requestedType + " not yet implemented -- see "
                        + "PhaseDiagramEngine#classifyPlot's own javadoc for what's missing "
                        + "and docs/phase_diagram_engine_flowchart.md's CLASSIFY REQUESTED PLOT box.");
        }
    }

    /** VALIDATE PLOT's own axis-count expectation per {@link PlotType} -- see {@link #classifyPlot}'s javadoc. */
    private static int expectedAxisCount(PlotType type) {
        return type == PlotType.PROPERTY_OR_STEP_DIAGRAM ? 1 : 2;
    }

    /**
     * The actual {@link Node}/{@link Line} -&gt; {@link PhaseDiagramResult}
     * conversion shared by every implemented {@link PlotType} in {@link
     * #classifyPlot} -- see that method's javadoc for what this does and
     * does not compute (in particular the &sect;4.1-driven per-phase
     * composition split this method performs).
     */
    private static PhaseDiagramResult buildResult(
            NodeRegistry registry, String[] axisNames, double[] axisMin, double[] axisMax,
            int compositionAxisIndex, int compositionComponentIndex) {

        PhaseDiagramResult result = new PhaseDiagramResult(axisNames, axisMin, axisMax);
        int numAxes = axisNames.length;

        for (Line line : mergeDedupNetwork(registry)) {
            List<EquilibriumResult> points = line.getPoints();
            if (points.isEmpty()) {
                continue;
            }
            String fixedPhase = line.fixedPhases.isEmpty() ? null : line.fixedPhases.get(0);

            if (compositionAxisIndex < 0) {
                // No released composition (e.g. STEP) -- the stored walk
                // coordinates are already correct as-is.
                result.addLine(new LineSegment(line.getAxisCoords(), fixedPhase,
                        new java.util.ArrayList<>(lineStablePhaseNames(line))));
                continue;
            }

            // Sundman 2021 §4.1: a proper T-x diagram plots "the mole
            // fraction of Cu in ALL STABLE PHASES" -- one curve per
            // stable phase, not the overall composition Line.getAxisCoords()
            // stores at compositionAxisIndex. Split this one walked run
            // into one LineSegment per phase name, each phase's own
            // PhaseResult.x substituted in at compositionAxisIndex.
            List<double[]> walkCoords = line.getAxisCoords();
            for (String phaseName : lineStablePhaseNames(line)) {
                List<double[]> perPhaseCoords = new java.util.ArrayList<>(points.size());
                boolean phasePresentThroughout = true;
                for (int i = 0; i < points.size(); i++) {
                    double[] x = phaseCompositionOrNull(points.get(i), phaseName);
                    if (x == null) {
                        phasePresentThroughout = false;
                        break;
                    }
                    double[] coord = walkCoords.get(i).clone();
                    coord[compositionAxisIndex] = x[compositionComponentIndex];
                    perPhaseCoords.add(coord);
                }
                if (phasePresentThroughout && !perPhaseCoords.isEmpty()) {
                    result.addLine(new LineSegment(perPhaseCoords, fixedPhase, List.of(phaseName)));
                }
            }
        }

        for (Node node : registry.getNodes()) {
            // Every Node in a NodeRegistry is a real phase-set-change
            // point -- an axis-limit termination (Line#terminateAtAxisLimit)
            // never creates a Node at all, only a dangling Line with no
            // end node (see Line.getEndNode()'s javadoc), so
            // NodePoint.Type.BOUNDARY does not arise from this graph.
            NodePoint.Type type = node.stablePhaseNames.size() > numAxes + 1
                    ? NodePoint.Type.INVARIANT
                    : NodePoint.Type.CROSSING;
            result.addNode(new NodePoint(
                    node.axisValues, new java.util.ArrayList<>(node.stablePhaseNames), type));
        }

        return result;
    }

    /** {@code eq}'s stable-phase mole-fraction composition for {@code phaseName}, or {@code null} if not stable there. */
    private static double[] phaseCompositionOrNull(EquilibriumResult eq, String phaseName) {
        for (EquilibriumResult.PhaseResult pr : eq.getStablePhases()) {
            if (pr.phaseName.equals(phaseName)) {
                return pr.x;
            }
        }
        return null;
    }
}
