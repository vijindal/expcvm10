package calc.diagram;

import calc.equil.GridMinimizer;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

/**
 * Single top-to-bottom entry point matching {@code
 * docs/phase_diagram_engine_flowchart.md} -- one method per flowchart
 * stage, in the same order the flowchart lists them. Every target
 * diagram type in {@code docs/roadmap_phase_diagrams.md} (binary,
 * ternary isothermal, ternary isopleth, pseudo-isothermal, property/
 * step) runs through this SAME sequence; what differs per diagram type
 * is only the axis/condition setup passed into {@link #defineSystem}
 * and the choice passed to {@link #classifyPlot} at the end -- see
 * {@code docs/roadmap_phase_diagrams.md}'s "One engine, not one tracer
 * per diagram type" section.
 *
 * <p><b>Purpose of this class.</b> Before this, each flowchart stage
 * existed only as a paragraph in the flowchart doc plus, for the stages
 * already built, scattered methods on {@link MapTracer} / {@link
 * MapDiagramTracer} / {@link NodeRegistry} with no single place showing
 * the whole sequence or which stages remain unimplemented. This class
 * is that single place: implemented stages delegate to the classes
 * above; unimplemented stages throw {@link UnsupportedOperationException}
 * naming the exact gap and pointing at the roadmap doc, rather than
 * silently doing nothing or being absent entirely. {@link
 * #generateStartingPoints}/{@link #drainC1Loop}/{@link #drainStepLoop}
 * are thin delegations to already-tested code, not reimplementations --
 * {@link #drainC1Loop(ConditionSet, int, int, double, double[], List)}
 * reaches every diagram type (binary, ternary isothermal, isopleth) this
 * engine's {@link ConditionSet} can express, the same generalization
 * {@link MapDiagramTracer} itself went through (Step 6d).
 *
 * <p><b>Not yet wired into any UI.</b> {@code
 * CalculationSession#calculatePhaseDiagram} still throws its own
 * "not yet implemented" -- this class is the engine {@code
 * calculatePhaseDiagram} will eventually call once enough of the
 * pipeline below is real, not a replacement for it yet.
 */
public final class PhaseDiagramEngine {

    private PhaseDiagramEngine() {
    }

    // ------------------------------------------------------------------
    // DATABASE -> DEFINE SYSTEM + CONDITIONS + AXES + LIMITS
    // ------------------------------------------------------------------

    /**
     * {@code DATABASE} + {@code DEFINE SYSTEM + CONDITIONS + AXES +
     * LIMITS} (flowchart top). Parses the TDB and builds a {@link
     * GibbsEnergyModel} for each of {@code candidatePhases} -- the
     * SET OF CANDIDATE PHASES the flowchart's initialization box
     * discusses (stable vs. "metastable" diagrams are just different
     * candidate-phase selections at this stage, per that box's note;
     * this method does not itself have a stable/metastable mode).
     *
     * <p><b>Implemented</b>: delegates directly to {@link
     * ThermodynamicSystem#build}, already used by every other
     * calculation path in this codebase ({@code CalculationSession},
     * the CLI/GUI/API).
     */
    public static ThermodynamicSystem defineSystem(
            String tdbFilePath,
            List<String> elements,
            List<String> candidatePhases) throws IOException {

        return ThermodynamicSystem.build(tdbFilePath, elements, candidatePhases);
    }

    // ------------------------------------------------------------------
    // VALIDATE n + 2 EQUILIBRIUM CONDITIONS
    // ------------------------------------------------------------------

    /**
     * {@code VALIDATE n + 2 EQUILIBRIUM CONDITIONS} (flowchart, §2.3.2):
     * an n-component system needs exactly n+2 conditions for a single
     * Algorithm-A equilibrium call (T, P, composition by default, or an
     * allowed substitute -- chemical potential/activity, S, H, V, a
     * phase held stable, x in a phase, a constituent fraction, a
     * state-variable expression). Distinct from the Gibbs phase rule
     * (Eq. 8) used later at node classification -- see {@link
     * #classifyNode}.
     *
     * <p><b>Implemented</b> as OC's own check (confirmed directly this
     * session, not just the paper's prose): OC's Fortran source
     * ({@code src/models/gtp3X.F90:5401}, function {@code
     * extract_massbal}) computes {@code idf = noofel+2-nc} and raises
     * error 4144 ("Degrees of freedom not zero") whenever {@code idf !=
     * 0} -- i.e. this is symmetric: TOO FEW conditions and TOO MANY
     * conditions both fail the same way, not just underdetermined
     * systems. Reproduced directly against the real {@code oc7C}
     * binary via the pty driver ({@code
     * docs/oc_reference_tests/run_pty.py}): a 2-component Ag-Cu system
     * with only T,P set (2 conditions, needs 4) and, separately, with
     * T,P,N,x(Cu),x(Ag) set (5 conditions) BOTH produced OC's error
     * 4144 with that exact message; the correctly-determined 4-condition
     * case (T,P,N,x(Cu)) is what every other equilibrium call in this
     * codebase already relies on succeeding.
     *
     * @param numComponents        n, the number of independent components
     * @param numConditionsSupplied how many conditions the caller intends
     *                               to set for one Algorithm-A call
     * @throws IllegalArgumentException if {@code numConditionsSupplied !=
     *         numComponents + 2}, whether too few or too many -- mirroring
     *         OC's own symmetric idf != 0 check, not just an underdetermined-
     *         only guard
     */
    public static void validateConditionCount(int numComponents, int numConditionsSupplied) {
        int requiredConditions = numComponents + 2;
        int idf = requiredConditions - numConditionsSupplied;
        if (idf != 0) {
            throw new IllegalArgumentException(
                    "Degrees of freedom not zero: " + numComponents + " components require exactly "
                    + requiredConditions + " conditions (n+2), but " + numConditionsSupplied
                    + " were supplied (idf=" + idf + "). Matches OpenCalphad's own error 4144, "
                    + "\"Degrees of freedom not zero\" (src/models/gtp3X.F90's idf=noofel+2-nc check).");
        }
    }

    // ------------------------------------------------------------------
    // GENERATE STARTING POINT(S)
    // ------------------------------------------------------------------

    /**
     * {@code GENERATE STARTING POINT(S)} (flowchart): usually one, but
     * a full diagram can have components disconnected from the first
     * (the paper's own Fe-Mo γ-loop example, §3).
     *
     * <p><b>Single-point only -- and this is NOT a gap relative to
     * either source, unlike this method's earlier javadoc claimed.</b>
     * Confirmed directly this session (paper re-read + a full search of
     * OC's actual Fortran source, {@code
     * D:\codes\opencalphad\src\stepmapplot\smp2A.F90} and {@code
     * pmon6.F90}): NEITHER source has a working multi-start-point
     * algorithm to port.
     * <ul>
     *   <li>The paper (§3, page 5) names the problem (Fe-Mo's γ-loop,
     *       disconnected from the rest of that diagram) and says only
     *       "More than one starting point has then to be input. This
     *       can simply be done by the user or it is possible to
     *       automatically predefine many starting points, in particular
     *       for binary or ternary diagrams" -- then explicitly declines
     *       to formalize it: <em>"Such issues will not be considered in
     *       the algorithms presented here, where instead the focus is
     *       on the way to process connected lines."</em> No algorithm,
     *       pseudocode, or figure covers this anywhere in the paper.</li>
     *   <li>OC's own source has exactly one attempt, {@code
     *       auto_startpoints} ({@code smp2A.F90:9342-9498}): a hardcoded
     *       5-point scheme for EXACTLY 2 axes (4 composition-simplex
     *       corners + 1 center point, 12 total directions) -- but it is
     *       gated behind a status bit literally named {@code GSNOAUTOSP}
     *       ("no auto start point"), its own header comment admits "the
     *       rest here works but not converting the startpoint to lines"
     *       (i.e. incomplete), and EVERY call site to it is commented
     *       out ({@code smp2A.F90:81}, {@code pmon6.F90:6810}, the
     *       latter inside a debug-only case block itself preceded by
     *       "debug map_startpoints commented away"). It is unreachable
     *       dead code in the shipped program. A separate author comment
     *       ({@code smp2A.F90:112-113}) states plainly: "I have not
     *       really implemented several startpoint."</li>
     * </ul>
     * So this method's single-point behavior is not an unfinished
     * corner of this codebase -- it is exactly where both the paper and
     * OC's real, running behavior stand today (multiple starting points
     * are a manual, user-driven affair: run separate {@code map}/{@code
     * step} calls from different starting conditions). A future
     * multi-start-point search here would be genuinely NEW
     * implementation work with no validated algorithm or OC reference
     * output to test against -- OC's own disabled {@code
     * auto_startpoints} scheme (corners + center of the 2-axis range)
     * is a plausible reference DESIGN to draw on if that work is ever
     * undertaken, but it must be labeled as an unvalidated, dead-code
     * prototype, not a port of working, tested logic.
     */
    public static List<double[]> generateStartingPoints(double singleStartWalkValue) {
        // Single starting point: matches both the paper's own explicit
        // scope limitation and OC's actual (non-)behavior -- see the
        // javadoc above for the full finding.
        return List.of(new double[] { singleStartWalkValue });
    }

    // ------------------------------------------------------------------
    // FOR EACH STARTING POINT: initial estimate, Algorithm A, initial
    // search, START node -- then C1 DRAIN LOOP
    // ------------------------------------------------------------------

    /**
     * The flowchart's "FOR EACH STARTING POINT" box through the {@code
     * C1 : DRAIN LOOP} box, MAPPING branch (2 axes): drains the whole
     * diagram from one starting point. Delegates to {@link
     * MapDiagramTracer#drain(AxisConfig, AxisConfig, double, double,
     * double, double[], List)} -- see that class's javadoc for what it
     * does internally (initial search, node classification via {@link
     * #classifyNode}, exit generation via {@link NodeGeometry}/{@link
     * InvariantExitFinder}) and {@code docs/roadmap_phase_diagrams.md}
     * for what's still out of scope (the "fastest-varying axis"
     * reselection, {@code UNRESOLVED_MULTI_PHASE_CHANGE} exits,
     * multi-start-point stitching). This overload only expresses a
     * binary T-x map (its two {@link AxisConfig} arguments cannot
     * describe a FIXED third composition or two free composition axes)
     * -- see {@link #drainC1Loop(ConditionSet, int, int, double,
     * double[], List)} for a ternary isothermal section or an isopleth.
     *
     * @throws IllegalStateException if the initial search finds no
     *         crossing anywhere in {@code walkAxis}'s range (propagated
     *         from {@link MapDiagramTracer#drain})
     */
    public static NodeRegistry drainC1Loop(
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double startWalkValue,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        return new MapDiagramTracer().drain(
                walkAxis, releaseAxis, fixedT, fixedP, startWalkValue, compOverall, candidates);
    }

    /**
     * {@link ConditionSet}-driven form of {@link #drainC1Loop(AxisConfig,
     * AxisConfig, double, double, double, double[], List)}: the SAME
     * MAPPING branch, generalized to any diagram type this engine's
     * {@link ConditionSet} can express -- binary T-x, ternary isothermal
     * (two free composition axes), or a ternary+ isopleth (one or more
     * compositions FIXED, per {@link #classifyNode(ConditionSet, int)}'s
     * own distinction). Delegates to {@link
     * MapDiagramTracer#drain(ConditionSet, int, int, double, double[],
     * List)}.
     *
     * @throws IllegalStateException if the initial search finds no
     *         crossing anywhere in the search axis's range (propagated
     *         from {@link MapDiagramTracer#drain})
     */
    public static NodeRegistry drainC1Loop(
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
     * The flowchart's "FOR EACH STARTING POINT" box through the {@code
     * C1 : DRAIN LOOP} box, STEP branch (1 axis, §3.2): drains the whole
     * property/step diagram from one starting point. Delegates to
     * {@link StepDiagramTracer#drain} -- STEP has no ZPF-fixed axis at
     * all (no phase is ever released), so unlike the MAPPING branch it
     * needs no {@link ConditionSet} generalization: one {@link
     * AxisConfig} already fully describes it.
     *
     * @throws IllegalStateException if the starting point does not converge
     *         (propagated from {@link StepDiagramTracer#drain})
     */
    public static NodeRegistry drainStepLoop(
            AxisConfig axis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        return new StepDiagramTracer().drain(axis, fixedT, fixedP, compOverall, candidates);
    }

    // ------------------------------------------------------------------
    // (Inside the C1 loop) GLOBAL STABILITY CHECK
    // ------------------------------------------------------------------

    /**
     * Relative G/atom tolerance -- above {@code GridMinimizer}'s own
     * ~1e-5 sampling noise on a correct point, well below a genuine
     * miscibility-gap-scale violation (~1e-1); see {@code
     * PhaseDiagramEngineTest} for the calibration case.
     */
    private static final double GLOBAL_STABILITY_RELATIVE_TOLERANCE = 1.0e-4;

    /**
     * The {@code GLOBAL STABILITY CHECK} (§2.3.3): does another candidate
     * phase set give a lower G at this equilibrium's (T, P, overall
     * composition)? Re-runs {@link GridMinimizer}'s independent global
     * search and compares G per mole of real atoms ({@link
     * EquilibriumResult#totalGPerAtom()}, not {@link
     * EquilibriumResult#totalG()} -- not comparable across phase sets
     * with different formula-unit sizes).
     *
     * <p>Called at every node ({@link MapDiagramTracer}, {@link
     * StepDiagramTracer}) and, at a configurable interval, mid-line
     * ({@link MapTracer#walkOneSegment}, {@link
     * StepTracer#walkOneSegment}) -- the paper's two check sites, §2.3.3:
     * "at node points and at regular intervals along a line."
     *
     * @param candidates the SAME candidate list {@code candidateEquilibrium} was solved with
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

    /** Overall composition (mole fractions) implied by a converged result's stable phases. */
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

    /** Result of {@link #classifyNode}. */
    public enum NodeClass {
        /** f &gt; 0, tie-line-in-plane geometry: 2 exits. */
        TIE_LINE_IN_PLANE,
        /** f &gt; 0, isopleth-style crossing geometry: 3 exits. */
        ISOPLETH_CROSSING,
        /** f = 0: a genuine invariant, exits found via Algorithm D. */
        INVARIANT,
        /**
         * A STEP-calculation node created by a stable-set change mid-walk
         * (NOT the step's own START node, which instead gets 2 exits --
         * see {@link StepDiagramTracer}): exactly 1 exit, continuing in
         * the SAME direction the line was already going. Not reached via
         * {@link #classifyNode}'s Eq. 8 formula (STEP has no ZPF-fixed
         * axis at all, so {@code f} is not evaluated for it) -- Sundman
         * 2021 Calphad 75 Section 3.2 states this exit count directly in
         * prose: "a new node will be created with one exit to continue
         * calculating along the axis in the same direction with the new
         * set of stable phases."
         */
        STEP_CONTINUATION
    }

    /**
     * Node classification at a stable-set change (flowchart): the GIBBS
     * PHASE RULE (Eq. 8, {@code f = n+2-p-c}) distinguishes invariant
     * ({@code f=0}) from ordinary ({@code f>0}); {@code numComponents}
     * and {@code c} (FIXED, non-axis potential-type conditions) are
     * derived directly from {@code conds} rather than passed separately,
     * so this always reflects the diagram actually being traced.
     *
     * <p><b>{@code c}'s exact meaning, worked out against the paper's
     * own example (important -- easy to get backwards).</b> §3.3: "A
     * binary isobaric phase diagram has {@code f=3-p}... and an
     * invariant has thus 3 stable phases." "Isobaric" means P is FIXED,
     * not an axis -- so for {@code n=2} this requires {@code c=1}
     * (P counted as one fixed, non-axis potential condition), giving
     * {@code f=n+2-p-c=2+2-p-1=3-p}, matching the paper exactly and
     * giving {@code f=0} at {@code p=3} as stated. So {@code c} counts
     * FIXED potential-type conditions (T, P, or a chemical potential)
     * NOT used as axes -- it is NOT the number of axes, and NOT the
     * number of fixed conditions in general ({@link
     * Condition.Variable#TOTAL_MOLES} and a FIXED composition condition
     * do not count). This codebase's binary map (T and one composition
     * as AXIS, P FIXED) has {@code c=1}; a step calculation (only T as
     * AXIS, P FIXED) also has {@code c=1}; a hypothetical
     * fully-potential-driven setup with both T and P fixed would have
     * {@code c=2}.
     *
     * <p><b>Distinguishing the two ordinary ({@code f>0}) cases --
     * {@code TIE_LINE_IN_PLANE} vs. {@code ISOPLETH_CROSSING}.</b> Per
     * the flowchart's "Exit count at a normal node" analysis: exit count
     * for {@code f>0} comes from NODE GEOMETRY, not {@code f} itself, so
     * this is not an Eq. 8 computation -- it is the paper's own §3.3
     * geometric distinction, "tie-lines in the plane" (binary T-x,
     * ternary isothermal: every stable phase's composition is fully
     * described by the diagram's own axes) vs. not (an isopleth: some
     * composition is FIXED outside the two axes, so a phase's tie-line
     * generally leaves the plane -- §3.3's own words, "In iso-pleths...
     * most node points correspond to two crossing lines... requires the
     * creation of 3 exits"). Concretely: a {@link ConditionSet} with ANY
     * FIXED {@link Condition.Variable#COMPOSITION} condition is an
     * isopleth-shaped diagram (2021 Fig. 3(c)'s own worked example: T and
     * x(Zn) axes, x(Mg) FIXED); a {@link ConditionSet} with none is
     * tie-line-in-plane (binary T-x: only T is an axis besides the one
     * released composition, no OTHER composition to fix; ternary
     * isothermal: both composition axes are free, none fixed).
     *
     * @param conds           the full condition set (n+2 conditions) for
     *                        the diagram this node belongs to
     * @param numStablePhases p, the number of phases stable at this node
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

    // ------------------------------------------------------------------
    // MERGE / DEDUP NETWORK
    // ------------------------------------------------------------------

    /**
     * {@code MERGE / DEDUP NETWORK} (flowchart): collapse nodes reached
     * from two directions via node matching, remove/suppress lines
     * rejected by the global stability check, and remove duplicate
     * representations (implementation cleanup, not paper-sourced).
     *
     * <p><b>Partially implemented.</b> {@link NodeRegistry#findOrCreate}
     * (Step 1/2) performs node-matching dedup INLINE, at creation time,
     * for the ordinary case -- there is no separate post-hoc merge pass.
     * {@link Node#matches} now ports OC's own {@code map_newnode}
     * comparison (T/P gate, then chemical potentials, both at OC's own
     * tight tolerances -- closing the roadmap's earlier "nodes reached
     * from different walk directions" gap), so the REMAINING work here
     * is exclusively global-stability-check-based line suppression
     * across the whole registry (a POST-HOC pass over already-created
     * nodes/lines, distinct from the per-node check {@link
     * PhaseDiagramEngine#isGloballyStable} already runs inline at node
     * creation) -- NOT implemented.
     */
    public static void mergeDedupNetwork(NodeRegistry registry) {
        throw new UnsupportedOperationException(
                "Post-hoc line suppression (global-stability-check-based) not yet "
                + "implemented -- node-matching dedup itself is done (NodeRegistry.findOrCreate, "
                + "Node.matches), see docs/roadmap_phase_diagrams.md's MERGE / DEDUP NETWORK box.");
    }

    // ------------------------------------------------------------------
    // IDENTIFY / LABEL PHASE REGIONS
    // ------------------------------------------------------------------

    /**
     * {@code IDENTIFY / LABEL PHASE REGIONS} (flowchart, §2.4): ZPF
     * lines separate regions where a phase is present from regions
     * where it is not. The paper states this defining property but
     * gives no computational-geometry algorithm, and neither does OC's
     * source (confirmed: {@code smp2A.F90}/{@code smp2B.F90} mention
     * "region" only in comments, never as a data structure or
     * algorithm) -- this is genuinely new implementation work, not a
     * port from either source.
     *
     * <p><b>Not implemented.</b> No region/polygon construction exists
     * anywhere in this codebase yet.
     */
    public static void identifyPhaseRegions(NodeRegistry registry) {
        throw new UnsupportedOperationException(
                "Phase-region identification/labeling not yet implemented -- this is new "
                + "implementation work with no algorithm to port from the paper or OC. "
                + "See docs/phase_diagram_engine_flowchart.md's IDENTIFY/LABEL PHASE REGIONS box.");
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
     * {@code CLASSIFY REQUESTED PLOT} + {@code VALIDATE PLOT}
     * (flowchart): render the same stored {@link Node}/{@link Line}
     * data as whichever plot type was requested; may overlay results
     * from separate {@link #defineSystem} runs with different candidate
     * -phase sets (Fig. 2c's stable-vs-"metastable" overlay) -- each
     * run is a full, independent pass through this whole engine, the
     * overlay itself is a plotting-stage step only.
     *
     * <p><b>Not implemented.</b> No rendering/plot-classification layer
     * exists for this engine yet (the GUI's existing {@code
     * PhaseDiagramPanel}/{@code CoarseDiagramPanel} render {@link
     * StepTracer}/{@link CoarseDiagramTracer} output directly, not this
     * class's {@link Node}/{@link Line} graph).
     */
    public static void classifyPlot(NodeRegistry registry, PlotType requestedType) {
        throw new UnsupportedOperationException(
                "Plot classification/rendering not yet implemented for the Node/Line engine -- "
                + "see docs/phase_diagram_engine_flowchart.md's CLASSIFY REQUESTED PLOT box.");
    }
}
