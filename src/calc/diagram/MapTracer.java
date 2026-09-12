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
 */
public final class MapTracer {

    public MapTracer() {
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

        double previousWalkValue = Double.NaN;
        EquilibriumResult previousResult = null;

        for (double v = walkAxis.min; v <= walkAxis.max; v += walkAxis.step) {

            double t = fixedT;
            double p = fixedP;

            switch (walkAxis.type) {
                case TEMPERATURE: t = v; break;
                case PRESSURE: p = v; break;
                case COMPOSITION: comp = StepTracer.applyCompositionAxis(walkAxis, v, comp); break;
                default: throw new IllegalStateException("Unhandled axis type: " + walkAxis.type);
            }

            EquilibriumResult current =
                    EquilibriumSolveHelper.solveOrSentinel(t, p, comp, candidates);

            if (!current.isConverged()) {

                result.setComplete(false);
                result.setMessage("Non-convergent point at " + walkAxis.name + "=" + v);

                if (runNames == null) {
                    runNames = stablePhaseNames(current);
                }
                runCoords.add(new double[] { v, comp[releaseAxis.componentIndex] });
                previousWalkValue = v;
                previousResult = current;
                continue;
            }

            Set<String> currentNames = stablePhaseNames(current);

            if (runNames == null) {

                runNames = currentNames;
                runCoords.add(new double[] { v, comp[releaseAxis.componentIndex] });

                result.addNode(
                        new NodePoint(
                                new double[] { v, comp[releaseAxis.componentIndex] },
                                new ArrayList<>(currentNames),
                                NodePoint.Type.BOUNDARY));

            } else if (currentNames.equals(runNames)) {

                runCoords.add(new double[] { v, comp[releaseAxis.componentIndex] });

            } else {

                // Phase set changed between (previousWalkValue, v). A
                // genuine invariant (eutectic/peritectic) is a single
                // POINT where the stable set jumps by MORE than one
                // phase at once (e.g. V2ZR disappears and LIQUID appears
                // at the exact same T) -- confirmed by direct testing
                // this session against V-Zr's own documented 1586K
                // peritectic.
                //
                // OpenCalphad's own map_calcnode/map_halfstep (traced
                // directly this session, C:\Users\admin\codes\opencalphad
                // \src\stepmapplot\smp2A.F90) handles this the SAME way
                // as an ordinary crossing -- Algorithm C2 always fixes
                // exactly ONE phase and releases exactly ONE condition,
                // never two. When a second phase's driving force also
                // crosses zero during that solve (meq_sameset returning
                // irem/iadd nonzero), map_calcnode treats it as node
                // failure (bmperr=4222/4223) and map_halfstep retries
                // with a SMALLER walk-axis step from the last converged
                // point (a 10% sub-step, up to 3 attempts, giving up with
                // "two phases competing to appear/disappear" if the
                // second phase still triggers -- see map_halfstep's own
                // comment at that exact error code). There is no
                // OpenCalphad code path that fixes two phases and
                // releases two conditions simultaneously; an earlier
                // version of this method did that (Eq. 9 mis-applied to
                // node-FINDING rather than the post-node exit-amount
                // bookkeeping it actually describes) and has been
                // reverted in favor of this OpenCalphad-faithful retry.
                String appearingOrDisappearing =
                        findChangedPhase(runNames, currentNames);

                // The walk point/temperature/composition actually used
                // to solve the single-phase-fix boundary below -- either
                // the raw grid point v, or (if a retry narrowed the
                // multi-phase jump down to a resolvable single-phase
                // sub-interval) the finer point found by the retry.
                double effectiveWalkValue = v;
                double effectiveT = t;
                double[] effectiveComp = comp;
                Set<String> effectiveCurrentNames = currentNames;

                if (appearingOrDisappearing == null
                        && walkAxis.type == AxisConfig.Type.TEMPERATURE
                        && !Double.isNaN(previousWalkValue)) {

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

                double crossingWalkValue = effectiveWalkValue;
                double crossingReleaseValue = effectiveComp[releaseAxis.componentIndex];
                Set<String> nodeNames = new LinkedHashSet<>(runNames);
                nodeNames.addAll(effectiveCurrentNames);
                NodePoint.Type nodeType = NodePoint.Type.CROSSING;

                if (appearingOrDisappearing != null) {

                    // Ordinary single-phase crossing -- solve the exact
                    // boundary via Algorithm C2 (composition release).
                    EquilibriumSolverV2.BoundarySolveResult boundary =
                            EquilibriumSolveHelper.solveBoundaryOrNull(
                                    effectiveT, p, effectiveComp, candidates, previousResult,
                                    appearingOrDisappearing, 0.0,
                                    releaseAxis.componentIndex);

                    if (boundary != null) {
                        crossingReleaseValue = boundary.releasedComponentValue;
                        effectiveComp[releaseAxis.componentIndex] = crossingReleaseValue;
                    }

                    if (nodeNames.size() >= 3) {

                        List<InvariantExitFinder.ExitCandidate> exits =
                                checkInvariant(nodeNames, candidates, effectiveT, p, effectiveComp,
                                        appearingOrDisappearing);

                        if (!exits.isEmpty()) {
                            nodeType = NodePoint.Type.INVARIANT;
                        }
                    }

                } else {

                    // More than one phase changed and either the walk
                    // axis isn't TEMPERATURE (T-release not applicable)
                    // or the OpenCalphad-style halved-step retry could
                    // not resolve it to a single-phase sub-interval
                    // within 3 attempts (matching map_halfstep's own
                    // "two phases competing to appear/disappear" give-up
                    // condition) -- record the jump as an ordinary
                    // (approximate) CROSSING at the walk point v,
                    // matching StepTracer's own non-resolvable-crossing
                    // fallback, and flag the result as incomplete.
                    result.setComplete(false);
                    result.setMessage("More than one phase changed at "
                            + walkAxis.name + "=" + v
                            + " and the invariant point could not be located.");
                }

                comp = effectiveComp;
                runCoords.add(new double[] { crossingWalkValue, crossingReleaseValue });

                result.addLine(buildSegment(runCoords, runNames, runFixedPhase));

                result.addNode(
                        new NodePoint(
                                new double[] { crossingWalkValue, crossingReleaseValue },
                                new ArrayList<>(nodeNames),
                                nodeType));

                runCoords = new ArrayList<>();
                runCoords.add(new double[] { crossingWalkValue, crossingReleaseValue });
                runCoords.add(new double[] { v, comp[releaseAxis.componentIndex] });
                runNames = effectiveCurrentNames;
                runFixedPhase = appearingOrDisappearing;
            }

            previousWalkValue = v;
            previousResult = current;
        }

        if (runNames != null && !runCoords.isEmpty()) {

            result.addLine(buildSegment(runCoords, runNames, runFixedPhase));

            result.addNode(
                    new NodePoint(
                            new double[] { previousWalkValue, comp[releaseAxis.componentIndex] },
                            new ArrayList<>(runNames),
                            NodePoint.Type.BOUNDARY));
        }

        return result;
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
