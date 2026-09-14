package calc.diagram;

import calc.equil.EquilibriumSolverV2;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;
import ui.result.PhaseDiagramResult;
import ui.result.PhaseDiagramResult.LineSegment;
import ui.result.PhaseDiagramResult.NodePoint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sundman Algorithm B's MAP branch (2021 Calphad 75, Section 3.2): walk
 * one axis ({@code walkAxis}) in fixed increments like {@link
 * StepTracer}, but locate each phase-boundary crossing EXACTLY via
 * Algorithm C2 ({@link EquilibriumSolverV2#solveBoundary}, a genuine
 * zero-phase-amount Lagrange-style condition -- see that method's
 * javadoc for the full mechanism, ported and verified directly against
 * OpenCalphad's own implementation, C:\Users\admin\codes\opencalphad,
 * this session) rather than {@link StepTracer}'s black-box bisection.
 * When three phases coexist at a node, Algorithm D ({@link
 * InvariantExitFinder}) confirms whether it is a genuine invariant.
 *
 * <p><b>Scope constraint (v1): {@code releaseAxis} is composition-only.</b>
 * The ordinary two-phase boundary crossings this tracer finds always
 * release a composition component ({@code releaseAxis} MUST be {@link
 * AxisConfig.Type#COMPOSITION}) -- {@code walkAxis} is stepped in
 * ordinary fixed increments exactly like {@link StepTracer} (typically
 * TEMPERATURE). This is fully sufficient for a binary T-x map (this
 * project's own validated systems, e.g. Ag-Cu's eutectic).
 *
 * <p><b>Invariant nodes (eutectics/peritectics)</b> are a single POINT
 * where the stable set jumps by MORE than one phase at once (confirmed
 * by direct testing against V-Zr's documented 1586K peritectic: no
 * adjacent sub-interval exists where only one phase differs). Per
 * Sundman Eq. 8, an isobaric binary invariant has exactly {@code
 * p=n+1=3} stable phases, so a single-phase-fix/single-condition
 * Algorithm C2 solve (used for an ordinary crossing above) is
 * mathematically singular there by construction: with 3 stable phases
 * but only 2 chemical potentials, the 3 phase-equilibrium rows ({@link
 * GlobalEquilibriumMatrixAssembler#buildMatrix} never puts a
 * DeltaOmega coefficient in a phase-equilibrium row) are confined to 2
 * nonzero columns and are linearly dependent regardless of seeding
 * (confirmed directly this session -- even an optimally seeded new
 * phase still produced an exactly-singular matrix on iteration 0).
 *
 * <p>The fix is NOT to fix two phases and release two conditions at
 * once -- that was tried and found to work numerically but is not
 * grounded in the paper or in OpenCalphad's implementation, both of
 * which always keep Algorithm C2 to exactly one fixed phase and one
 * released condition. OpenCalphad's own {@code map_calcnode}/{@code
 * map_halfstep} (traced directly this session, {@code
 * C:\Users\admin\codes\opencalphad\src\stepmapplot\smp2A.F90}) instead
 * treats a second phase's driving force crossing zero mid-solve as a
 * node-solve FAILURE and retries from the last converged point with a
 * much smaller walk-axis sub-step (10% of the normal increment, up to
 * 3 attempts, giving up with "two phases competing to appear/disappear"
 * if the jump still cannot be narrowed to one phase). {@link
 * #retryWithHalvedSteps} implements exactly this retry; once it narrows
 * the jump to a single resolvable phase change, the ordinary Algorithm
 * C2 path above (composition release) locates the exact crossing, and
 * {@link InvariantExitFinder} (Algorithm D) confirms whether the
 * resulting >=3-phase node is a genuine invariant. Only applies when
 * {@code walkAxis.type == TEMPERATURE} -- releasing P at an invariant,
 * or a fully general any-axis-releasable map, is a distinct, smaller
 * follow-up.
 *
 * <p>Unlike {@link StepTracer}, every ordinary (non-crossing) walk point
 * is solved with the CURRENT tracked boundary composition (updated after
 * every successful boundary solve), not a single fixed target -- a map
 * line follows a moving phase boundary, not one fixed overall
 * composition.
 *
 * <p><b>{@link #trace} vs. {@link #walkOneSegment}:</b> {@link #trace}
 * walks the ENTIRE axis range in one call, restarting its own
 * line/node bookkeeping every time the stable set changes, and is the
 * tracer every existing caller/test uses. {@link #walkOneSegment} is
 * the same per-increment walk logic pulled out as a resumable
 * primitive -- walk from one starting point in one direction until
 * either an axis limit or a stable-set change, then return control
 * without deciding what happens next. It exists for {@code
 * docs/phase_diagram_engine_flowchart.md}'s C1 drain loop (see the
 * forthcoming {@code NodeRegistry}/drain-loop orchestrator in this
 * package), which needs to walk one {@link Line} at a time and decide
 * node creation/exit generation itself rather than have {@link #trace}
 * decide it internally. {@link #trace} is now implemented by calling
 * {@link #walkOneSegment} in a loop -- a pure refactor, verified by
 * {@code CalculationSessionMapTracerTest} (a standalone diagnostic,
 * not JUnit) continuing to pass unmodified.
 */
public final class MapTracer {

    public MapTracer() {
    }

    /** Why a call to {@link #walkOneSegment} stopped. */
    public enum SegmentEnd {
        /** Reached {@code walkAxis.max} or {@code walkAxis.min} without a stable-set change. */
        AXIS_LIMIT,
        /** A non-convergent point was hit; the segment stops there (matches {@link #trace}'s handling). */
        NON_CONVERGENT,
        /** Exactly one phase appeared or disappeared; the exact boundary was located via Algorithm C2. */
        CROSSING,
        /** Like {@code CROSSING}, but Algorithm D confirmed a genuine invariant at that point. */
        INVARIANT,
        /**
         * More than one phase changed and the crossing could not be resolved
         * to a single-phase boundary (matches {@link #trace}'s incomplete-crossing fallback).
         */
        UNRESOLVED_MULTI_PHASE_CHANGE
    }

    /**
     * Result of one {@link #walkOneSegment} call: the points walked (in
     * order, starting just after the segment's starting point) plus why
     * the segment ended and, for a {@code CROSSING}/{@code INVARIANT}
     * end, the new stable phase set and the exact crossing coordinates.
     */
    public static final class SegmentResult {
        public final List<double[]> coords;
        public final List<EquilibriumResult> points;
        public final SegmentEnd end;
        public final double endWalkValue;
        /** Overall composition at the end of the segment (post-crossing release value applied, if any). */
        public final double[] endComposition;
        /** New stable phase set at the crossing; {@code null} unless {@code end} is CROSSING or INVARIANT. */
        public final Set<String> newStableNames;
        /** The phase that appeared/disappeared to cause a CROSSING/INVARIANT; {@code null} otherwise. */
        public final String changedPhase;
        /**
         * For AXIS_LIMIT/NON_CONVERGENT: the last successfully solved
         * equilibrium in this segment. For CROSSING/INVARIANT/
         * UNRESOLVED_MULTI_PHASE_CHANGE: the equilibrium AT {@code
         * endWalkValue} with the NEW stable set (matching {@code
         * trace()}'s original {@code previousResult = current} at the
         * raw grid point where the change was first observed) -- this is
         * what a caller resuming the next segment should pass as that
         * segment's own starting result, not a re-solve of the refined
         * crossing composition.
         */
        public final EquilibriumResult lastResult;

        SegmentResult(List<double[]> coords, List<EquilibriumResult> points, SegmentEnd end,
                      double endWalkValue, double[] endComposition, Set<String> newStableNames,
                      String changedPhase, EquilibriumResult lastResult) {
            this.coords = coords;
            this.points = points;
            this.end = end;
            this.endWalkValue = endWalkValue;
            this.endComposition = endComposition;
            this.newStableNames = newStableNames;
            this.changedPhase = changedPhase;
            this.lastResult = lastResult;
        }
    }

    /**
     * Walks {@code walkAxis} from {@code walkAxis.min} to {@code
     * walkAxis.max} in increments of {@code walkAxis.step}, tracking the
     * two-phase boundary's {@code releaseAxis} composition as it moves,
     * recording each maximal run of constant stable-phase-set as one
     * {@link LineSegment} (with {@code fixedPhase} populated -- unlike
     * {@link StepTracer}, which always leaves it {@code null}) and each
     * exact boundary crossing as a {@link NodePoint} ({@code CROSSING}
     * for an ordinary two-phase-region boundary, {@code INVARIANT} when
     * Algorithm D confirms three phases genuinely coexist at that point).
     *
     * @param walkAxis    the axis walked in fixed increments (any type)
     * @param releaseAxis the composition axis solved for exactly at each
     *                    boundary; must have {@code type == COMPOSITION}
     * @param fixedT      temperature to use when {@code walkAxis.type != TEMPERATURE}
     * @param fixedP      pressure to use when {@code walkAxis.type != PRESSURE}
     * @param compOverall starting overall composition (the {@code
     *                    releaseAxis} component's entry is overwritten as
     *                    the walk proceeds)
     * @param candidates  candidate phase models
     * @return the traced map result
     * @throws IllegalArgumentException if {@code releaseAxis.type != COMPOSITION}
     */
    public PhaseDiagramResult trace(
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double[] compOverall,
            List<GibbsEnergyModel> candidates) {

        if (releaseAxis.type != AxisConfig.Type.COMPOSITION) {
            throw new IllegalArgumentException(
                    "MapTracer's release axis must be COMPOSITION (T/P release "
                    + "requires derivative plumbing not yet implemented); got "
                    + releaseAxis.type);
        }

        PhaseDiagramResult result =
                new PhaseDiagramResult(
                        new String[] { walkAxis.name, releaseAxis.name },
                        new double[] { walkAxis.min, releaseAxis.min },
                        new double[] { walkAxis.max, releaseAxis.max });

        double[] comp = compOverall.clone();

        List<double[]> runCoords = new ArrayList<>();
        Set<String> runNames = null;
        String runFixedPhase = null;

        // Solve the starting point itself, exactly as the original
        // for-loop's very first iteration (v == walkAxis.min) did, before
        // any segment walk begins.
        double t0 = fixedT, p0 = fixedP;
        switch (walkAxis.type) {
            case TEMPERATURE: t0 = walkAxis.min; break;
            case PRESSURE: p0 = walkAxis.min; break;
            case COMPOSITION: comp = StepTracer.applyCompositionAxis(walkAxis, walkAxis.min, comp); break;
            default: throw new IllegalStateException("Unhandled axis type: " + walkAxis.type);
        }
        EquilibriumResult startResult = EquilibriumSolveHelper.solveOrSentinel(t0, p0, comp, candidates);
        runNames = stablePhaseNames(startResult);
        runCoords.add(new double[] { walkAxis.min, comp[releaseAxis.componentIndex] });
        result.addNode(
                new NodePoint(
                        new double[] { walkAxis.min, comp[releaseAxis.componentIndex] },
                        new ArrayList<>(runNames),
                        NodePoint.Type.BOUNDARY));

        double segmentStartValue = walkAxis.min;
        double lastWalkValue = walkAxis.min;
        Set<String> segmentStartNames = runNames;
        EquilibriumResult segmentStartResult = startResult;

        while (true) {

            SegmentResult seg = walkOneSegment(
                    segmentStartValue, segmentStartNames, walkAxis, releaseAxis,
                    fixedT, fixedP, comp, candidates, segmentStartResult);

            runCoords.addAll(seg.coords);
            comp = seg.endComposition;
            lastWalkValue = seg.endWalkValue;

            if (seg.end == SegmentEnd.AXIS_LIMIT || seg.end == SegmentEnd.NON_CONVERGENT) {
                if (seg.end == SegmentEnd.NON_CONVERGENT) {
                    result.setComplete(false);
                    result.setMessage("Non-convergent point at " + walkAxis.name + "=" + seg.endWalkValue);
                }
                break;
            }

            // CROSSING, INVARIANT, or UNRESOLVED_MULTI_PHASE_CHANGE: close
            // out the current line/node exactly as the original inline loop did.
            NodePoint.Type nodeType;
            Set<String> nodeNames;

            if (seg.end == SegmentEnd.UNRESOLVED_MULTI_PHASE_CHANGE) {
                result.setComplete(false);
                result.setMessage("More than one phase changed at "
                        + walkAxis.name + "=" + seg.endWalkValue
                        + " and the invariant point could not be located.");
                nodeType = NodePoint.Type.CROSSING;
                nodeNames = new LinkedHashSet<>(runNames);
                if (seg.newStableNames != null) nodeNames.addAll(seg.newStableNames);
            } else {
                nodeType = (seg.end == SegmentEnd.INVARIANT)
                        ? NodePoint.Type.INVARIANT : NodePoint.Type.CROSSING;
                nodeNames = new LinkedHashSet<>(runNames);
                nodeNames.addAll(seg.newStableNames);
            }

            result.addLine(buildSegment(runCoords, runNames, runFixedPhase));
            result.addNode(
                    new NodePoint(
                            new double[] { seg.endWalkValue, comp[releaseAxis.componentIndex] },
                            new ArrayList<>(nodeNames),
                            nodeType));

            // Matches the original: keep walking forward from the raw grid
            // point with the new stable set -- the loop does not stop on
            // an unresolved multi-phase jump either, same as before.
            runCoords = new ArrayList<>();
            runCoords.add(new double[] { seg.endWalkValue, comp[releaseAxis.componentIndex] });
            runNames = seg.newStableNames;
            runFixedPhase = seg.changedPhase;

            // Resume the next segment one increment past where this one
            // ended, exactly as the original for-loop's next v would be,
            // using the SAME (raw grid point, pre-refinement) result the
            // original loop carried forward as previousResult/previousWalkValue.
            segmentStartNames = seg.newStableNames;
            segmentStartResult = seg.lastResult;
            segmentStartValue = seg.endWalkValue + walkAxis.step;
            if ((walkAxis.step > 0 && segmentStartValue > walkAxis.max)
                    || (walkAxis.step < 0 && segmentStartValue < walkAxis.min)) {
                break;
            }
        }

        if (runNames != null && !runCoords.isEmpty()) {
            result.addLine(buildSegment(runCoords, runNames, runFixedPhase));
            result.addNode(
                    new NodePoint(
                            new double[] { lastWalkValue, comp[releaseAxis.componentIndex] },
                            new ArrayList<>(runNames),
                            NodePoint.Type.BOUNDARY));
        }

        return result;
    }

    /**
     * Walks {@code walkAxis} starting just after {@code startWalkValue}
     * (i.e. the first point solved is {@code startWalkValue +
     * walkAxis.step}), in the direction of {@code walkAxis.step}'s sign,
     * until either an axis limit is reached or the stable phase set
     * changes -- the resumable primitive behind {@link #trace}, and the
     * one {@code docs/phase_diagram_engine_flowchart.md}'s C1 drain loop
     * calls once per pending {@link Line}.
     *
     * <p>This does not itself decide what a crossing means for node/line
     * bookkeeping (that is the caller's job, per the flowchart: {@link
     * #trace} closes out its own inline bookkeeping, while the drain
     * loop instead consults a node registry) -- it only walks and
     * reports where it stopped and why.
     *
     * @param startWalkValue the axis value of the ALREADY-KNOWN starting
     *                       equilibrium (not re-solved here); the first
     *                       new point solved is one step beyond it
     * @param compAtStart    overall composition at {@code startWalkValue};
     *                       not mutated
     * @return where and why the segment stopped, and the points walked
     */
    public SegmentResult walkOneSegment(
            double startWalkValue,
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double[] compAtStart,
            List<GibbsEnergyModel> candidates) {

        double[] comp = compAtStart.clone();
        double t0 = fixedT, p0 = fixedP;
        switch (walkAxis.type) {
            case TEMPERATURE: t0 = startWalkValue; break;
            case PRESSURE: p0 = startWalkValue; break;
            case COMPOSITION: comp = StepTracer.applyCompositionAxis(walkAxis, startWalkValue, comp); break;
            default: throw new IllegalStateException("Unhandled axis type: " + walkAxis.type);
        }
        EquilibriumResult startResult = EquilibriumSolveHelper.solveOrSentinel(t0, p0, comp, candidates);

        return walkOneSegment(startWalkValue, stablePhaseNames(startResult), walkAxis, releaseAxis,
                fixedT, fixedP, compAtStart, candidates, startResult);
    }

    /**
     * Same as {@link #walkOneSegment(double, AxisConfig, AxisConfig,
     * double, double, double[], List)}, but for a caller (the C1 drain
     * loop) that already knows the starting equilibrium's stable phase
     * set and result -- e.g. from a {@link Node} -- and so does not need
     * this method to redundantly re-solve the starting point.
     *
     * @param startStableNames stable phase names at {@code startWalkValue}
     * @param startResult      the already-known starting equilibrium, used
     *                         as {@code previousResult} for the first
     *                         Algorithm C2 call if the first walked point
     *                         already crosses a boundary
     */
    public SegmentResult walkOneSegment(
            double startWalkValue,
            Set<String> startStableNames,
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double[] compAtStart,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult startResult) {

        if (releaseAxis.type != AxisConfig.Type.COMPOSITION) {
            throw new IllegalArgumentException(
                    "MapTracer's release axis must be COMPOSITION (T/P release "
                    + "requires derivative plumbing not yet implemented); got "
                    + releaseAxis.type);
        }

        double[] comp = compAtStart.clone();
        Set<String> runNames = startStableNames;

        List<double[]> coords = new ArrayList<>();
        List<EquilibriumResult> points = new ArrayList<>();

        double previousWalkValue = startWalkValue;
        EquilibriumResult previousResult = startResult;

        for (double v = startWalkValue + walkAxis.step;
             walkAxis.step > 0 ? v <= walkAxis.max : v >= walkAxis.min;
             v += walkAxis.step) {

            double t = fixedT;
            double p = fixedP;

            switch (walkAxis.type) {
                case TEMPERATURE: t = v; break;
                case PRESSURE: p = v; break;
                case COMPOSITION: comp = StepTracer.applyCompositionAxis(walkAxis, v, comp); break;
                default: throw new IllegalStateException("Unhandled axis type: " + walkAxis.type);
            }

            EquilibriumResult current = EquilibriumSolveHelper.solveOrSentinel(t, p, comp, candidates);

            if (!current.isConverged()) {
                coords.add(new double[] { v, comp[releaseAxis.componentIndex] });
                points.add(current);
                return new SegmentResult(coords, points, SegmentEnd.NON_CONVERGENT,
                        v, comp.clone(), null, null, previousResult);
            }

            Set<String> currentNames = stablePhaseNames(current);

            if (currentNames.equals(runNames)) {
                coords.add(new double[] { v, comp[releaseAxis.componentIndex] });
                points.add(current);
                previousWalkValue = v;
                previousResult = current;
                continue;
            }

            // Stable set changed -- resolve exactly as trace()'s inline
            // handling did.
            String appearingOrDisappearing = findChangedPhase(runNames, currentNames);

            double effectiveWalkValue = v;
            double effectiveT = t;
            double[] effectiveComp = comp;
            Set<String> effectiveCurrentNames = currentNames;

            if (appearingOrDisappearing == null
                    && walkAxis.type == AxisConfig.Type.TEMPERATURE) {

                RetriedCrossing retried = retryWithHalvedSteps(
                        previousWalkValue, v, walkAxis,
                        fixedP, comp, candidates, runNames);

                if (retried != null) {
                    appearingOrDisappearing = retried.appearingOrDisappearing;
                    effectiveCurrentNames = retried.currentNames;
                    effectiveWalkValue = retried.walkValue;
                    effectiveT = retried.walkValue;
                    effectiveComp = retried.comp;
                }
            }

            if (appearingOrDisappearing == null) {
                coords.add(new double[] { v, comp[releaseAxis.componentIndex] });
                return new SegmentResult(coords, points, SegmentEnd.UNRESOLVED_MULTI_PHASE_CHANGE,
                        v, comp.clone(), effectiveCurrentNames, null, current);
            }

            EquilibriumSolverV2.BoundarySolveResult boundary =
                    EquilibriumSolveHelper.solveBoundaryOrNull(
                            effectiveT, p, effectiveComp, candidates, previousResult,
                            appearingOrDisappearing, 0.0,
                            releaseAxis.componentIndex);

            double crossingReleaseValue = effectiveComp[releaseAxis.componentIndex];
            if (boundary != null) {
                crossingReleaseValue = boundary.releasedComponentValue;
                effectiveComp[releaseAxis.componentIndex] = crossingReleaseValue;
            }

            Set<String> nodeNames = new LinkedHashSet<>(runNames);
            nodeNames.addAll(effectiveCurrentNames);

            SegmentEnd end = SegmentEnd.CROSSING;
            if (nodeNames.size() >= 3) {
                List<InvariantExitFinder.ExitCandidate> exits =
                        checkInvariant(nodeNames, candidates, effectiveT, p, effectiveComp,
                                appearingOrDisappearing);
                if (!exits.isEmpty()) {
                    end = SegmentEnd.INVARIANT;
                }
            }

            coords.add(new double[] { effectiveWalkValue, crossingReleaseValue });

            return new SegmentResult(coords, points, end,
                    effectiveWalkValue, effectiveComp.clone(), effectiveCurrentNames,
                    appearingOrDisappearing, current);
        }

        return new SegmentResult(coords, points, SegmentEnd.AXIS_LIMIT,
                previousWalkValue, comp.clone(), runNames, null, previousResult);
    }

    /** Result of a successful {@link #retryWithHalvedSteps} attempt. */
    private static final class RetriedCrossing {
        final double walkValue;
        final double[] comp;
        final Set<String> currentNames;
        final String appearingOrDisappearing;

        RetriedCrossing(double walkValue, double[] comp,
                Set<String> currentNames, String appearingOrDisappearing) {
            this.walkValue = walkValue;
            this.comp = comp;
            this.currentNames = currentNames;
            this.appearingOrDisappearing = appearingOrDisappearing;
        }
    }

    /**
     * OpenCalphad's {@code map_halfstep} (traced directly this session,
     * {@code smp2A.F90}): when the ordinary single-phase-fix Algorithm
     * C2 solve cannot be applied because MORE than one phase's stable
     * set differs across the last walk increment (a second phase's
     * driving force also crossed zero inside that increment -- exactly
     * the signature of a genuine invariant), back up to the last
     * successfully converged point and re-walk with a much smaller
     * sub-step (OpenCalphad uses {@code 1.0D-1 * axfact * axinc}, a 10%
     * sub-step of the normal increment), re-checking after each sub-step
     * whether the phase-set difference has narrowed to exactly one
     * phase. Up to 3 sub-step attempts are made (matching {@code
     * map_halfstep}'s {@code halfstep.ge.3} give-up condition, whose own
     * comment names this exact situation "two phases competing to
     * appear/disappear"); if still unresolved, returns {@code null} so
     * the caller falls back to recording an approximate, incomplete
     * crossing.
     *
     * <p>This is deliberately NOT a bigger simultaneous solve (fixing
     * two phases and releasing two conditions at once) -- neither the
     * Sundman 2021 paper nor OpenCalphad's implementation do that
     * anywhere; both always keep Algorithm C2 to a single fixed phase
     * and a single released condition, resolving a multi-phase jump by
     * finding a finer walk point where only one phase's set differs,
     * not by enlarging the linear system.
     *
     * @return the finer point's result, or {@code null} if 3 sub-step
     *         attempts still could not narrow the jump to a single
     *         resolvable phase change
     */
    private RetriedCrossing retryWithHalvedSteps(
            double lastGoodWalkValue,
            double overshotWalkValue,
            AxisConfig walkAxis,
            double fixedP,
            double[] compAtOvershoot,
            List<GibbsEnergyModel> candidates,
            Set<String> lastGoodNames) {

        if (walkAxis.type != AxisConfig.Type.TEMPERATURE) {
            throw new IllegalArgumentException(
                    "retryWithHalvedSteps only supports a TEMPERATURE walk axis; got "
                    + walkAxis.type);
        }

        double subStep = 0.1 * (overshotWalkValue - lastGoodWalkValue);
        double[] comp = compAtOvershoot.clone();

        for (int attempt = 1; attempt <= 3; attempt++) {

            double candidateWalkValue = lastGoodWalkValue + attempt * subStep;

            EquilibriumResult candidateResult =
                    EquilibriumSolveHelper.solveOrSentinel(
                            candidateWalkValue, fixedP, comp, candidates);

            if (!candidateResult.isConverged()) {
                continue;
            }

            Set<String> candidateNames = stablePhaseNames(candidateResult);
            String changed = findChangedPhase(lastGoodNames, candidateNames);

            if (changed != null) {
                return new RetriedCrossing(
                        candidateWalkValue, comp, candidateNames, changed);
            }
        }

        return null;
    }

    /**
     * Returns the single phase name present in exactly one of the two
     * sets (the phase that appeared or disappeared), or {@code null} if
     * the sets differ by more than one phase (an ambiguous crossing this
     * tracer's single-phase-fix C2 call cannot resolve in one step).
     */
    private String findChangedPhase(Set<String> before, Set<String> after) {

        Set<String> symmetricDifference = new LinkedHashSet<>(before);
        for (String name : after) {
            if (before.contains(name)) {
                symmetricDifference.remove(name);
            } else {
                symmetricDifference.add(name);
            }
        }

        if (symmetricDifference.size() != 1) {
            return null;
        }

        return symmetricDifference.iterator().next();
    }

    /**
     * Algorithm D check: with {@code ncomp+1} phases at a candidate
     * node, ask {@link InvariantExitFinder} whether any valid exit
     * exists distinct from the arrival exit -- if so, this node is a
     * genuine invariant (multiple regions meet here), not just an
     * ordinary crossing that happens to touch a third phase
     * transiently.
     *
     * @param arrivedViaExcludedPhase the phase that was just fixed at
     *                                zero amount to solve the boundary
     *                                the algorithm arrived at this node
     *                                by (the TRUE arrival exit, not a
     *                                guess) -- may be {@code null} if
     *                                the crossing changed more than one
     *                                phase at once, in which case no
     *                                exit is excluded as "already known"
     */
    private List<InvariantExitFinder.ExitCandidate> checkInvariant(
            Set<String> nodeNames,
            List<GibbsEnergyModel> candidates,
            double t, double p, double[] comp,
            String arrivedViaExcludedPhase) {

        List<String> names = new ArrayList<>(nodeNames);
        double[][] compositions = new double[names.size()][];

        EquilibriumResult probe =
                EquilibriumSolveHelper.solveOrSentinel(t, p, comp, candidates);

        if (!probe.isConverged()) {
            return new ArrayList<>();
        }

        for (int i = 0; i < names.size(); i++) {
            for (EquilibriumResult.PhaseResult pr : probe.getStablePhases()) {
                if (pr.phaseName.equals(names.get(i))) {
                    compositions[i] = pr.x;
                    break;
                }
            }
            if (compositions[i] == null) {
                // Not all node-candidate phases are simultaneously stable
                // in this single-point probe -- cannot evaluate exits.
                return new ArrayList<>();
            }
        }

        if (names.size() < 2) {
            return new ArrayList<>();
        }

        return InvariantExitFinder.findExits(
                names, compositions, comp, arrivedViaExcludedPhase);
    }

    private LineSegment buildSegment(
            List<double[]> coords,
            Set<String> names,
            String fixedPhase) {

        return new LineSegment(coords, fixedPhase, new ArrayList<>(names));
    }

    private Set<String> stablePhaseNames(EquilibriumResult result) {
        return EquilibriumSolveHelper.stablePhaseNames(result);
    }
}
