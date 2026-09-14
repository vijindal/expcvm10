package calc.diagram;

import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;

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
 * silently doing nothing or being absent entirely. Nothing in the
 * implemented stages below was rewritten to make this skeleton --
 * {@link #generateStartingPoints}/{@link #drainC1Loop} are thin
 * delegations to already-tested code (Steps 1-4).
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
     * The flowchart's "FOR EACH STARTING POINT" box (initial estimate
     * via GridMinimizer, Algorithm A, the initial-equilibrium grid test,
     * the ONE-AXIS-vs-TWO-AXES branch, and locating the true START
     * {@link Node}) followed immediately by the {@code C1 : DRAIN LOOP}
     * box -- together, "drain the diagram from one starting point."
     *
     * <p><b>Implemented for the TWO-AXES (map) branch only, with
     * Algorithm D wired in (Step 5d).</b> Delegates to {@link
     * MapDiagramTracer#drain}, which internally:
     * <ul>
     *   <li>solves the starting condition (Algorithm A), which always
     *       calls {@link calc.equil.GridMinimizer#initialize} first,
     *       unconditionally, as part of {@code EquilibriumSolverV2}'s
     *       own iteration setup -- covering Fig. 1's "optional initial
     *       estimate" step for every call this codebase makes. The
     *       paper's §2.3.3 ALTERNATE path -- skip the grid minimizer up
     *       front only when a condition set doesn't allow it (e.g. T is
     *       not itself a condition), then re-run it as a POST-HOC test
     *       against the computed result and recalculate if any
     *       gridpoint sits below -- genuinely does not exist anywhere
     *       in {@code EquilibriumSolverV2} (confirmed directly: no
     *       post-hoc grid retest of any kind exists in that class).
     *       This is not a gap today because every caller in this
     *       codebase always supplies T as a condition, so the
     *       alternate path's precondition never arises -- but it would
     *       need building if a future caller ever solves with T NOT
     *       fixed/axis (e.g. releasing T at an invariant, still
     *       unimplemented per the roadmap's "P-release is not
     *       implemented" / T-release gaps);</li>
     *   <li>runs the TWO-AXES initial search ({@link
     *       MapTracer#findInitialBoundary}, Step 3a/3b) to locate the
     *       true START node;</li>
     *   <li>drains the C1 loop (Step 2), classifying each resolved
     *       crossing via {@link #classifyNode} (Eq. 8) and attaching
     *       its exits via {@link NodeGeometry} -- 2 exits for the
     *       ordinary {@code TIE_LINE_IN_PLANE} case, and (Step 5d,
     *       CORRECTING this javadoc's earlier claim that invariant
     *       nodes get no exits) Algorithm D exits via {@link
     *       InvariantExitFinder} for a genuine {@code INVARIANT}.</li>
     * </ul>
     *
     * <p><b>NOT implemented within this delegation:</b>
     * <ul>
     *   <li>the ONE-AXIS (step) branch's own node/exit bookkeeping --
     *       {@link StepTracer} exists and is used elsewhere in this
     *       codebase, but is not wired into a {@link Node}/{@link
     *       NodeRegistry}-based drain loop the way the map branch is;</li>
     *   <li>{@code UNRESOLVED_MULTI_PHASE_CHANGE} crossings (more than
     *       one phase changed and neither an ordinary nor an invariant
     *       resolution succeeded) still get no exit lines -- a
     *       deliberate, documented limitation distinct from the
     *       invariant case above (see {@link MapDiagramTracer}'s own
     *       javadoc);</li>
     *   <li>the "fastest-varying axis" reselection during a walk;</li>
     *   <li>multi-start-point stitching -- NOT a gap relative to either
     *       source, see {@link #generateStartingPoints}'s javadoc for
     *       why (neither the paper nor OC has a working algorithm to
     *       port here either).</li>
     * </ul>
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

    // ------------------------------------------------------------------
    // (Inside the C1 loop) GLOBAL STABILITY CHECK
    // ------------------------------------------------------------------

    /**
     * The C1 walk loop's {@code GLOBAL STABILITY CHECK} (flowchart,
     * §2.3.3, general form): at each successfully solved walk point, is
     * there another phase set representing a MORE STABLE equilibrium?
     * If so, the whole line is abandoned and suppressed (not merely
     * terminated) -- distinct from the narrower, gridpoint-specific
     * initial-equilibrium grid test in {@link #drainC1Loop}'s javadoc.
     *
     * <p><b>Not yet implemented.</b> Neither {@link MapTracer#trace}
     * nor {@link MapTracer#walkOneSegment} performs this check today --
     * a walked point is accepted as soon as {@code
     * EquilibriumSolverV2} reports convergence, with no check against
     * other candidate phase sets that might be cheaper. See {@code
     * docs/phase_diagram_engine_flowchart.md}'s walk-loop box.
     */
    public static boolean isGloballyStable(system.ports.EquilibriumResult candidateEquilibrium) {
        throw new UnsupportedOperationException(
                "Global stability check (is a cheaper phase set available?) not yet "
                + "implemented -- walkOneSegment currently accepts any converged point. "
                + "See docs/phase_diagram_engine_flowchart.md's walk-loop box.");
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
        INVARIANT
    }

    /**
     * Node classification at a stable-set change (flowchart): the GIBBS
     * PHASE RULE (Eq. 8, {@code f = n+2-p-c}) distinguishes invariant
     * ({@code f=0}) from ordinary ({@code f>0}).
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
     * number of fixed conditions in general (a fixed composition or N
     * does not count). This codebase's binary map (T and one
     * composition as AXIS, P FIXED) has {@code c=1}; a step calculation
     * (only T as AXIS, P FIXED) also has {@code c=1}; a hypothetical
     * fully-potential-driven setup with both T and P fixed would have
     * {@code c=2}.
     *
     * <p><b>Implemented (Step 5d) for the two cases this codebase's
     * tracers actually produce: {@code TIE_LINE_IN_PLANE} and {@code
     * INVARIANT}.</b> {@code ISOPLETH_CROSSING} (Step 5e's isopleth
     * work) is NOT distinguished here yet -- per the flowchart's
     * explicit correction, exit count for the ordinary ({@code f>0})
     * case comes from NODE GEOMETRY, not from {@code f} itself, and
     * this codebase has no isopleth-tracing code yet to produce that
     * geometry from. Every {@code f>0} node classifies as {@code
     * TIE_LINE_IN_PLANE} until Step 5e adds a way to distinguish the
     * two ordinary cases.
     *
     * @param numComponents          n
     * @param numStablePhases        p, the number of phases stable at this node
     * @param numFixedPotentialConditions c, FIXED (non-axis) potential-type
     *                               conditions (T, P, or a chemical
     *                               potential) -- see the javadoc above
     *                               for why this is 1, not 0, for this
     *                               codebase's binary map/step cases
     *                               (P is fixed) and would be 2 for a
     *                               ternary isothermal section (T and P
     *                               both fixed)
     */
    public static NodeClass classifyNode(int numComponents, int numStablePhases, int numFixedPotentialConditions) {
        int f = numComponents + 2 - numStablePhases - numFixedPotentialConditions;
        return f == 0 ? NodeClass.INVARIANT : NodeClass.TIE_LINE_IN_PLANE;
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
     * Step 4's investigation found this inline dedup does not catch
     * every case: independent walks reaching the "same" physical
     * boundary from different directions can converge to meaningfully
     * different equilibria (chemical potentials differing well beyond
     * solver tolerance), which {@link Node#matches} then correctly
     * treats as distinct nodes even though they represent one physical
     * point -- see {@code docs/roadmap_phase_diagrams.md}'s "STILL
     * OPEN" entry. A true post-hoc merge pass, and global-stability-
     * check-based line suppression, are NOT implemented.
     */
    public static void mergeDedupNetwork(NodeRegistry registry) {
        throw new UnsupportedOperationException(
                "Post-hoc node merge / line suppression not yet implemented -- "
                + "NodeRegistry only dedups inline at creation time, which Step 4 found "
                + "is not sufficient for nodes reached from different walk directions. "
                + "See docs/roadmap_phase_diagrams.md's \"STILL OPEN\" node-duplication entry.");
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
