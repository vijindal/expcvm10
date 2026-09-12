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
 * <p><b>Invariant nodes (eutectics/peritectics) release T instead.</b>
 * A genuine invariant is a single POINT where the stable set jumps by
 * MORE than one phase at once (confirmed by direct testing against
 * V-Zr's documented 1586K peritectic: no adjacent sub-interval exists
 * where only one phase differs, so a composition-release C2 call cannot
 * locate it). Sundman/pycalphad/OpenCalphad all locate it the same way
 * as an ordinary boundary: fix the newly-appearing phase at zero amount
 * and release the WALK axis instead, solving for T and composition
 * together ({@link EquilibriumSolverV2#solveBoundaryReleasingT}, enabled
 * by {@code dG_dT}/{@code dM_dT} on {@link
 * system.model.PhaseEquilData}). This only applies when {@code
 * walkAxis.type == TEMPERATURE} -- releasing P at an invariant, or a
 * fully general any-axis-releasable map, is a distinct, smaller
 * follow-up (the same {@code dG_dP}/pressure-response machinery already
 * exists per-phase, per {@code PhaseMatrixAssembler}, but is not yet
 * threaded through the global matrix the way T now is).
 *
 * <p><b>Known gap: {@code solveBoundaryReleasingT} converges slowly when
 * seeding a genuinely new phase</b> (see that method's own javadoc) --
 * confirmed against V-Zr's own 1586K peritectic to converge too slowly
 * to finish within its default iteration budget. Until that is fixed,
 * a multi-phase-change walk step commonly falls through to the "could
 * not be located" fallback below (an ordinary approximate {@code
 * CROSSING} at the raw walk point, {@code isComplete()==false}) rather
 * than actually landing on {@code INVARIANT} -- this is a real,
 * documented limitation, not a silent one.
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

                // Phase set changed between (previousWalkValue, v).
                // A genuine invariant (eutectic/peritectic) is a single
                // POINT where the stable set jumps by MORE than one
                // phase at once (e.g. V2ZR disappears and LIQUID appears
                // at the exact same T) -- confirmed by direct testing
                // this session against V-Zr's own documented 1586K
                // peritectic. Sundman/pycalphad/OpenCalphad all locate
                // this the SAME way as an ordinary boundary (Algorithm
                // C2): fix the newly-appearing phase's amount at zero
                // and release the WALK axis (not the composition axis)
                // instead, so T and composition are solved together.
                String appearingOrDisappearing =
                        findChangedPhase(runNames, currentNames);

                EquilibriumSolverV2.BoundarySolveResult invariantBoundary = null;
                String invariantFixedPhase = null;

                if (appearingOrDisappearing == null
                        && walkAxis.type == AxisConfig.Type.TEMPERATURE) {

                    invariantFixedPhase = findAppearingPhase(runNames, currentNames);

                    if (invariantFixedPhase != null) {
                        invariantBoundary = EquilibriumSolveHelper.solveBoundaryReleasingTOrNull(
                                t, p, comp, candidates, previousResult,
                                invariantFixedPhase, 0.0);
                    }
                }

                double crossingWalkValue = v;
                double crossingReleaseValue = comp[releaseAxis.componentIndex];
                Set<String> nodeNames = new LinkedHashSet<>(runNames);
                nodeNames.addAll(currentNames);
                NodePoint.Type nodeType = NodePoint.Type.CROSSING;

                if (invariantBoundary != null) {

                    // The T-release solve converged with the appearing
                    // phase fixed at zero -- the original two phases
                    // plus the new one are all simultaneously stable at
                    // this exact (T, comp): this IS the invariant node.
                    crossingWalkValue = invariantBoundary.releasedComponentValue;
                    nodeNames = EquilibriumSolveHelper.stablePhaseNames(
                            invariantBoundary.equilibrium);
                    nodeNames.add(invariantFixedPhase);
                    nodeType = NodePoint.Type.INVARIANT;
                    appearingOrDisappearing = null;

                } else if (appearingOrDisappearing != null) {

                    // Ordinary single-phase crossing -- solve the exact
                    // boundary via Algorithm C2 (composition release).
                    EquilibriumSolverV2.BoundarySolveResult boundary =
                            EquilibriumSolveHelper.solveBoundaryOrNull(
                                    t, p, comp, candidates, previousResult,
                                    appearingOrDisappearing, 0.0,
                                    releaseAxis.componentIndex);

                    if (boundary != null) {
                        crossingReleaseValue = boundary.releasedComponentValue;
                        comp[releaseAxis.componentIndex] = crossingReleaseValue;
                    }

                    if (nodeNames.size() >= 3) {

                        List<InvariantExitFinder.ExitCandidate> exits =
                                checkInvariant(nodeNames, candidates, t, p, comp,
                                        appearingOrDisappearing);

                        if (!exits.isEmpty()) {
                            nodeType = NodePoint.Type.INVARIANT;
                        }
                    }

                } else {

                    // More than one phase changed and either the walk
                    // axis isn't TEMPERATURE (T-release not applicable)
                    // or the T-release solve failed to converge --
                    // record the jump as an ordinary (approximate)
                    // CROSSING at the walk point v, matching
                    // StepTracer's own non-resolvable-crossing fallback,
                    // and flag the result as incomplete.
                    result.setComplete(false);
                    result.setMessage("More than one phase changed at "
                            + walkAxis.name + "=" + v
                            + " and the invariant point could not be located.");
                }

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
                runNames = currentNames;
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

    /**
     * Returns a phase present in {@code after} but not {@code before}
     * (a genuinely NEW phase appearing), preferring this over a
     * disappearing one since {@link EquilibriumSolverV2#solveBoundaryReleasingT}
     * needs the newly-appearing phase (fixed at zero amount, about to
     * become stable) to seed the invariant search from the {@code
     * before} side's converged 2-phase equilibrium. Returns {@code null}
     * if no phase was added (i.e. only phases were removed, or the sets
     * are otherwise not resolvable this way).
     */
    private String findAppearingPhase(Set<String> before, Set<String> after) {

        for (String name : after) {
            if (!before.contains(name)) {
                return name;
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
