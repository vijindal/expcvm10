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
 * <p><b>Scope constraint (v1): composition-release only.</b>
 * {@code EquilibriumSolverV2}'s matrix has no {@code dG/dT}/{@code dG/dP}
 * terms today (T and P are not Newton unknowns anywhere in the current
 * matrix), so {@code releaseAxis} MUST be {@link AxisConfig.Type#COMPOSITION}
 * -- {@code walkAxis} is stepped in ordinary fixed increments exactly
 * like {@link StepTracer} (typically TEMPERATURE), while the boundary
 * composition along {@code releaseAxis} is solved for exactly at each
 * crossing. This is fully sufficient for a binary T-x map (this
 * project's own validated systems, e.g. Ag-Cu's eutectic). Releasing T
 * or P as the map's axis is a distinct, larger follow-up requiring new
 * derivative plumbing in {@code PhaseMatrixAssembler}/{@code PhaseEquilData}.
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

                // Phase set changed between (previousWalkValue, v) --
                // solve the exact boundary via Algorithm C2.
                String appearingOrDisappearing =
                        findChangedPhase(runNames, currentNames);

                EquilibriumSolverV2.BoundarySolveResult boundary =
                        appearingOrDisappearing != null
                                ? EquilibriumSolveHelper.solveBoundaryOrNull(
                                        t, p, comp, candidates, previousResult,
                                        appearingOrDisappearing, 0.0,
                                        releaseAxis.componentIndex)
                                : null;

                double boundaryWalkValue = v;
                double boundaryReleaseValue = comp[releaseAxis.componentIndex];
                Set<String> nodeNames = new LinkedHashSet<>(runNames);
                nodeNames.addAll(currentNames);

                if (boundary != null) {
                    boundaryReleaseValue = boundary.releasedComponentValue;
                    comp[releaseAxis.componentIndex] = boundaryReleaseValue;
                }

                runCoords.add(new double[] { boundaryWalkValue, boundaryReleaseValue });

                result.addLine(buildSegment(runCoords, runNames, runFixedPhase));

                NodePoint.Type nodeType = NodePoint.Type.CROSSING;

                if (nodeNames.size() >= 3) {

                    List<InvariantExitFinder.ExitCandidate> exits =
                            checkInvariant(nodeNames, candidates, t, p, comp);

                    if (!exits.isEmpty()) {
                        nodeType = NodePoint.Type.INVARIANT;
                    }
                }

                result.addNode(
                        new NodePoint(
                                new double[] { boundaryWalkValue, boundaryReleaseValue },
                                new ArrayList<>(nodeNames),
                                nodeType));

                runCoords = new ArrayList<>();
                runCoords.add(new double[] { boundaryWalkValue, boundaryReleaseValue });
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
     * Algorithm D check: with 3 phases at a candidate node, ask {@link
     * InvariantExitFinder} whether any valid exit exists distinct from
     * the arrival pair -- if so, this node is a genuine invariant
     * (multiple regions meet here), not just an ordinary crossing that
     * happens to touch a third phase transiently.
     */
    private List<InvariantExitFinder.ExitCandidate> checkInvariant(
            Set<String> nodeNames,
            List<GibbsEnergyModel> candidates,
            double t, double p, double[] comp) {

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
                names, compositions, comp, names.get(0), names.get(1));
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
