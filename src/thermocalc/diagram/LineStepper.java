package thermocalc.diagram;

import domain.EquilibriumResult;
import domain.PhaseModelPort;
import thermocalc.equil.EquilibriumSolver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Algorithm C1 — ZPF line following.
 *
 * <p>Starting from a {@link DiagramExit}, steps along the Zero Phase Fraction
 * (ZPF) line by incrementing one diagram axis at a time and solving
 * equilibrium for the active (non-ZPF) phase set at each point.
 *
 * <h2>ZPF principle</h2>
 * The ZPF phase ({@link DiagramExit#fixedPhase}) is excluded from the
 * stable set throughout.  It is treated as a candidate for the driving-force
 * check only: if its driving force becomes positive at some step, the ZPF
 * line has ended and a phase change must be handled (Algorithm C2, Phase 7).
 *
 * <h2>Step logic</h2>
 * <ol>
 *   <li>Increment the current axis by {@code direction × step}.</li>
 *   <li>Update T/P/comp from the new axis values (the overall composition at
 *       each step is the mass-average composition of the active stable phases
 *       from the previous equilibrium — this keeps the ZPF phase at zero
 *       amount).</li>
 *   <li>Call {@link EquilibriumSolver#solve} for the active candidates.</li>
 *   <li>On failure: halve the step size and retry up to
 *       {@link #MAX_RETRIES} times.</li>
 *   <li>On success: check driving forces of all excluded phases.  If any
 *       exceeds {@link #DRIVING_FORCE_TOL}, record the pre-change point as the
 *       line's last point and return (phase change detected).</li>
 *   <li>Check axis bounds; return when the axis limit is reached.</li>
 *   <li>Optionally switch the stepping axis when the other axis changes
 *       more rapidly than the primary one (avoids following near-vertical
 *       lines in the wrong direction).</li>
 * </ol>
 */
public class LineStepper {

    private static final Logger LOG = Logger.getLogger(LineStepper.class.getName());

    private static final int    MAX_STEPS         = 1000;
    private static final int    MAX_RETRIES       = 4;
    private static final double DRIVING_FORCE_TOL = 1e-4;
    private static final double MIN_STEP_FRACTION = 1.0 / 16.0; // 4 halvings

    private final EquilibriumSolver solver;

    public LineStepper(EquilibriumSolver solver) {
        this.solver = solver;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Follow the ZPF line corresponding to {@code exit}.
     *
     * @param exit         starting exit (carries fixedPhase, axis index, direction)
     * @param candidates   all candidate phase models
     * @param axes         axis configurations (length = number of diagram axes)
     * @param fixedT       temperature to use when T is not a diagram axis (K)
     * @param fixedP       pressure (Pa)
     * @return a {@link DiagramLine} with all sampled equilibrium points;
     *         {@code endNode} is {@code null} if terminated at an axis boundary,
     *         or the pre-change point if a phase change was detected (Phase 7
     *         will convert that into a proper node)
     */
    public DiagramLine followLine(DiagramExit exit,
                                  List<PhaseModelPort> candidates,
                                  AxisConfig[] axes,
                                  double fixedT,
                                  double fixedP) {

        // ── Setup ────────────────────────────────────────────────────────
        EquilibriumResult startEq = exit.parentNode.equilibrium;
        double[] axisValues = exit.parentNode.axisValues.clone();

        // Build active candidate list: exclude fixedPhase (and initially forbiddenPhase)
        List<PhaseModelPort> active = buildActive(candidates,
                exit.fixedPhase, exit.forbiddenPhase);

        // Build initial overall composition from stable phases at start node
        double[] compOverAll = massAverageComp(startEq);

        // Stable phase set at start (names only, for change detection)
        Set<String> stableSet = stableNames(startEq);

        // Initial step sizes (may be halved on convergence failure)
        double[] stepSize = new double[axes.length];
        for (int i = 0; i < axes.length; i++) stepSize[i] = axes[i].step;

        int axisIdx = exit.initialAxisIndex;
        int dir     = exit.direction;

        // Start the line
        DiagramLine line = new DiagramLine(exit.parentNode, exit.fixedPhase, stableSet);
        line.addPoint(startEq, axisValues.clone());

        EquilibriumResult prevEq = startEq;

        // ── Main stepping loop ───────────────────────────────────────────
        for (int step = 0; step < MAX_STEPS; step++) {

            EquilibriumResult accepted = null;
            double[] acceptedAxes     = null;
            double[] acceptedComp     = null;

            // Step with retry on failure
            double currentStep = stepSize[axisIdx];
            for (int retry = 0; retry <= MAX_RETRIES; retry++) {

                // Trial axis increment
                double[] trialAxes = axisValues.clone();
                trialAxes[axisIdx] += dir * currentStep;

                // Check axis bounds → terminate at boundary
                if (!axes[axisIdx].inBounds(trialAxes[axisIdx])) {
                    LOG.fine("LineStepper: axis " + axisIdx + " out of bounds at step " + step);
                    return line; // endNode = null (axis boundary)
                }

                // Derive T, P, comp from trial axis values
                double trialT = resolveT(trialAxes, axes, fixedT);
                double trialP = resolveP(trialAxes, axes, fixedP);
                double[] trialComp = resolveComp(trialAxes, axes, compOverAll);

                // Solve
                EquilibriumResult eq;
                try {
                    eq = solver.solve(trialT, trialP, trialComp, active);
                } catch (Exception ex) {
                    LOG.fine("LineStepper: solver threw at step " + step
                            + " retry " + retry + ": " + ex.getMessage());
                    currentStep /= 2.0;
                    continue;
                }

                if (!eq.isConverged()) {
                    currentStep /= 2.0;
                    if (currentStep < stepSize[axisIdx] * MIN_STEP_FRACTION) break;
                    continue;
                }

                // Accept
                accepted     = eq;
                acceptedAxes = trialAxes;
                acceptedComp = massAverageComp(eq);
                break;
            }

            if (accepted == null) {
                // Could not step → terminate
                LOG.fine("LineStepper: all retries exhausted at step " + step);
                return line;
            }

            // ── Driving-force check for excluded phases ───────────────────
            double maxDF = maxExcludedDrivingForce(accepted, candidates,
                    exit.fixedPhase, exit.forbiddenPhase);

            if (maxDF > DRIVING_FORCE_TOL) {
                // A new phase wants to enter — record last accepted point
                // and signal a phase change.  Phase 7 (PhaseChangeHandler) will
                // bisect precisely and create the new node.
                line.addPoint(accepted, acceptedAxes);
                line.phaseChangeDetected = true;
                LOG.fine("LineStepper: driving force " + maxDF
                        + " > tol at step " + step + " — phase change detected.");
                return line;
            }

            // ── Update state ─────────────────────────────────────────────
            axisValues   = acceptedAxes;
            compOverAll  = acceptedComp;
            prevEq       = accepted;

            // Restore step size toward original on successive successes
            stepSize[axisIdx] = Math.min(currentStep * 1.5, axes[axisIdx].step);

            line.addPoint(accepted, axisValues.clone());

            // ── Axis switching heuristic ──────────────────────────────────
            // If the other axis is changing faster than the primary one
            // (line is more "horizontal" than "vertical"), switch axes.
            if (axes.length == 2 && line.size() >= 3) {
                int other = 1 - axisIdx;
                double dPrimary = Math.abs(axisValues[axisIdx]
                        - line.getAxisCoords().get(line.size() - 3)[axisIdx]);
                double dOther   = Math.abs(axisValues[other]
                        - line.getAxisCoords().get(line.size() - 3)[other]);
                // Normalise by axis range so T (hundreds of K) vs x (0–1) are comparable
                double rangeP = axes[axisIdx].max - axes[axisIdx].min;
                double rangeO = axes[other].max  - axes[other].min;
                if (rangeP > 0 && rangeO > 0) {
                    double normP = dPrimary / rangeP;
                    double normO = dOther   / rangeO;
                    if (normO > 2.0 * normP && normO > 1e-10) {
                        // Switch
                        axisIdx = other;
                        // Determine new direction from the recent change
                        double delta = axisValues[axisIdx]
                                - line.getAxisCoords().get(line.size() - 2)[axisIdx];
                        dir = delta >= 0 ? +1 : -1;
                        LOG.fine("LineStepper: switched to axis " + axisIdx
                                + " dir=" + dir + " at step " + step);
                    }
                }
            }
        }

        LOG.fine("LineStepper: reached MAX_STEPS=" + MAX_STEPS);
        return line;
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Build the list of active candidates: all phases except fixedPhase
     * (and forbiddenPhase on the first iteration — same exclusion for simplicity).
     */
    private List<PhaseModelPort> buildActive(List<PhaseModelPort> all,
                                              String fixedPhase,
                                              String forbiddenPhase) {
        List<PhaseModelPort> result = new ArrayList<>();
        for (PhaseModelPort m : all) {
            if (m.phaseName().equals(fixedPhase))    continue;
            if (m.phaseName().equals(forbiddenPhase)) continue;
            result.add(m);
        }
        return result;
    }

    /**
     * Compute the mass-average overall composition from the stable phases
     * of the last equilibrium.  This is what keeps the ZPF phase at zero
     * amount: we only account for phases that ARE stable.
     */
    private double[] massAverageComp(EquilibriumResult eq) {
        int nc = eq.getMu().length;
        double[] comp = new double[nc];
        double totalN = 0;
        for (EquilibriumResult.PhaseResult pr : eq.getStablePhases()) {
            for (int i = 0; i < nc; i++) comp[i] += pr.amount * pr.x[i];
            totalN += pr.amount;
        }
        if (totalN > 0) {
            for (int i = 0; i < nc; i++) comp[i] /= totalN;
        }
        return comp;
    }

    /** Collect stable phase names from an equilibrium result. */
    private Set<String> stableNames(EquilibriumResult eq) {
        Set<String> names = new LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : eq.getStablePhases()) {
            names.add(pr.phaseName);
        }
        return names;
    }

    /** Resolve T from axis values: use the TEMPERATURE axis if present, else fixedT. */
    private double resolveT(double[] axisValues, AxisConfig[] axes, double fixedT) {
        for (int i = 0; i < axes.length; i++) {
            if (axes[i].type == AxisConfig.Type.TEMPERATURE) return axisValues[i];
        }
        return fixedT;
    }

    /** Resolve P from axis values: use the PRESSURE axis if present, else fixedP. */
    private double resolveP(double[] axisValues, AxisConfig[] axes, double fixedP) {
        for (int i = 0; i < axes.length; i++) {
            if (axes[i].type == AxisConfig.Type.PRESSURE) return axisValues[i];
        }
        return fixedP;
    }

    /**
     * Resolve overall composition.  COMPOSITION axes override the corresponding
     * component of {@code baseComp}; all other components are taken from baseComp.
     */
    private double[] resolveComp(double[] axisValues, AxisConfig[] axes,
                                  double[] baseComp) {
        double[] comp = baseComp.clone();
        for (int i = 0; i < axes.length; i++) {
            if (axes[i].type == AxisConfig.Type.COMPOSITION) {
                comp[axes[i].componentIndex] = axisValues[i];
            }
        }
        // Re-normalise if a composition axis was overridden
        // (simple: assign last component as 1 - sum of others)
        double sum = 0;
        int lastComp = comp.length - 1;
        boolean hasCompAxis = false;
        for (AxisConfig ax : axes) {
            if (ax.type == AxisConfig.Type.COMPOSITION) { hasCompAxis = true; break; }
        }
        if (hasCompAxis) {
            for (int i = 0; i < lastComp; i++) sum += comp[i];
            comp[lastComp] = Math.max(0, 1.0 - sum);
        }
        return comp;
    }

    /**
     * Compute the maximum driving force of all excluded phases
     * (fixedPhase + forbiddenPhase) at the current equilibrium.
     *
     * <p>Driving force γ = G + Σ μ_A · x_A; positive means the phase
     * wants to enter the stable set.
     */
    private double maxExcludedDrivingForce(EquilibriumResult eq,
                                            List<PhaseModelPort> allCandidates,
                                            String fixedPhase,
                                            String forbiddenPhase) {
        double maxDF = Double.NEGATIVE_INFINITY;

        // Check among metastable phases reported by the equilibrium result
        for (EquilibriumResult.PhaseResult pr : eq.getMetastablePhases()) {
            if (!pr.phaseName.equals(fixedPhase) && !pr.phaseName.equals(forbiddenPhase)) continue;
            double df = pr.drivingForce;  // already computed by solver
            if (df > maxDF) maxDF = df;
        }
        return maxDF == Double.NEGATIVE_INFINITY ? 0.0 : maxDF;
    }
}
