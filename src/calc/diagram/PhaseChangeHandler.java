package calc.diagram;

import system.ports.EquilibriumResult;
import system.model.GibbsEnergyModel;
import calc.equil.EquilibriumSolver;
import util.Constants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Algorithm C2 — Phase change handler.
 *
 * <p>When {@link LineStepper} detects a positive driving force (a phase wants
 * to enter), this class:
 * <ol>
 *   <li>Bisects the bracketing interval to locate the exact ZPF crossing.</li>
 *   <li>Creates a {@link DiagramNode} at that point.</li>
 *   <li>Determines the number of degrees of freedom (f) at the node via the
 *       Gibbs phase rule.</li>
 *   <li>Generates the appropriate {@link DiagramExit}s:
 *       <ul>
 *         <li>f &gt; 0 normal crossing — two exits (one per side of the boundary).</li>
 *         <li>f = 0 invariant point — delegates to {@link InvariantHandler}.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>Bisection principle</h2>
 * Let A = last good point (driving force ≤ 0) and B = first bad point
 * (driving force &gt; 0).  The midpoint M is solved with the ZPF phase
 * excluded.  If DF(M) ≤ 0 then A ← M, else B ← M.  Repeat until the axis
 * interval |B−A| &lt; {@link #BISECT_TOL}.
 */
public class PhaseChangeHandler {

    private static final Logger LOG = Logger.getLogger(PhaseChangeHandler.class.getName());

    private final EquilibriumSolver  solver;
    private final InvariantHandler   invariantHandler;

    public PhaseChangeHandler(EquilibriumSolver solver) {
        this.solver           = solver;
        this.invariantHandler = new InvariantHandler();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Handle the phase change at the end of {@code line} and attach the
     * resulting node (with exits) to {@code diagram}.
     *
     * @param line        the ZPF line that ended with {@code phaseChangeDetected=true};
     *                    must contain at least 2 points
     * @param candidates  all candidate phase models
     * @param axes        axis configurations
     * @param fixedT      fixed temperature (K) if T is not an axis
     * @param fixedP      fixed pressure (Pa)
     * @param diagram     the diagram being built (used for node de-duplication)
     * @return the new (or merged) {@link DiagramNode} at the phase change point
     */
    public DiagramNode handle(DiagramLine line,
                               List<GibbsEnergyModel> candidates,
                               AxisConfig[] axes,
                               double fixedT, double fixedP,
                               PhaseDiagram diagram) {

        List<double[]> coords = line.getAxisCoords();
        List<EquilibriumResult> points = line.getPoints();
        int n = points.size();
        if (n < 2) {
            throw new IllegalArgumentException("DiagramLine must have at least 2 points");
        }

        // Bracket: A = second-to-last point (DF ≤ 0), B = last point (DF > 0)
        double[] axesA = coords.get(n - 2);
        double[] axesB = coords.get(n - 1);
        EquilibriumResult eqA = points.get(n - 2);

        // Identify the entering phase from the last point's metastable driving forces
        String enteringPhase = findEnteringPhase(points.get(n - 1));
        if (enteringPhase == null) {
            // Fallback: use the fixedPhase of the line (it was excluded, now re-enters)
            enteringPhase = line.fixedPhase;
        }
        LOG.fine("PhaseChangeHandler: entering phase = " + enteringPhase);

        // Build active set (same as LineStepper: exclude fixedPhase + forbiddenPhase)
        // For bisection we exclude the entering phase
        List<GibbsEnergyModel> active = exclude(candidates, enteringPhase, null);

        // ── Bisect to find the exact crossing ────────────────────────────
        double[] nodeAxes = bisect(axesA, axesB, active, axes, fixedT, fixedP);

        // ── Solve the full equilibrium at the node (include the entering phase) ──
        double nodeT     = resolveT(nodeAxes, axes, fixedT);
        double nodeP     = resolveP(nodeAxes, axes, fixedP);
        double[] nodeComp = massAverageComp(eqA);  // use last-good composition
        EquilibriumResult nodeEq = solver.solve(nodeT, nodeP, nodeComp, candidates);

        // ── De-duplicate: check if a nearby node already exists ──────────
        DiagramNode existing = findNearbyNode(diagram, nodeAxes, Constants.NODE_MERGE_TOL);
        if (existing != null) {
            LOG.fine("PhaseChangeHandler: merging with existing node " + existing.id);
            line.endNode = existing;
            return existing;
        }

        // ── Create the new node ───────────────────────────────────────────
        DiagramNode node = diagram.addNode(nodeEq, nodeAxes);
        line.endNode = node;

        // ── Determine exits via Gibbs phase rule ──────────────────────────
        int numStable = nodeEq.getStablePhases().size();
        int c         = candidates.get(0).numComponents();
        int n_axes    = axes.length;
        // f = n_axes - p + c  (axes fix n_axes conditions; standard CALPHAD convention)
        int f = n_axes - numStable + c;

        LOG.fine("PhaseChangeHandler: node " + node.id
                + " stable=" + numStable + " c=" + c + " f=" + f);

        if (f <= 0) {
            // Invariant point: delegate to InvariantHandler
            invariantHandler.findExits(node, candidates, axes);
        } else {
            // Normal crossing: two exits, one per side of the boundary
            buildNormalExits(node, line, enteringPhase, axes);
        }

        return node;
    }

    // ------------------------------------------------------------------
    // Private: bisection
    // ------------------------------------------------------------------

    /**
     * Bisect the axis interval [axesA, axesB] to find where the entering
     * phase's driving force crosses zero.
     *
     * <p>Returns the axis values at the last A point (DF ≤ 0) — the exact
     * ZPF crossing location.
     */
    private double[] bisect(double[] axesA, double[] axesB,
                             List<GibbsEnergyModel> active,
                             AxisConfig[] axes,
                             double fixedT, double fixedP) {

        double[] a = axesA.clone();
        double[] b = axesB.clone();

        for (int i = 0; i < Constants.MAX_BISECT; i++) {
            // Check convergence: max axis distance
            double dist = 0;
            for (int k = 0; k < a.length; k++) {
                double range = axes[k].max - axes[k].min;
                if (range > 0) dist = Math.max(dist, Math.abs(b[k] - a[k]) / range);
            }
            if (dist < Constants.BISECT_TOL) break;

            // Midpoint
            double[] mid = new double[a.length];
            for (int k = 0; k < a.length; k++) mid[k] = (a[k] + b[k]) / 2.0;

            double midT = resolveT(mid, axes, fixedT);
            double midP = resolveP(mid, axes, fixedP);

            // Use composition from last A equilibrium (placeholder)
            // The mass-average is updated through a rough estimate here
            EquilibriumResult midEq;
            try {
                double[] midComp = new double[active.get(0).numComponents()];
                // Equal composition as a neutral starting point
                for (int ci = 0; ci < midComp.length; ci++) {
                    midComp[ci] = 1.0 / midComp.length;
                }
                midEq = solver.solve(midT, midP, midComp, active);
            } catch (Exception ex) {
                // Bisect toward A on failure
                b = mid;
                continue;
            }

            double maxDF = maxDrivingForce(midEq);
            if (maxDF <= Constants.DRIVING_FORCE_TOL) {
                a = mid;  // still before the change
            } else {
                b = mid;  // after the change
            }
        }
        return a;
    }

    // ------------------------------------------------------------------
    // Private: exit generation
    // ------------------------------------------------------------------

    /**
     * Generate two exits for a normal (non-invariant) phase change:
     * <ul>
     *   <li>Exit 1: continue along the current ZPF line direction
     *       (fixedPhase = old ZPF phase, forbiddenPhase = enteringPhase).</li>
     *   <li>Exit 2: start a new ZPF line for the entering phase
     *       (fixedPhase = enteringPhase, forbiddenPhase = old ZPF phase).</li>
     * </ul>
     */
    private void buildNormalExits(DiagramNode node,
                                   DiagramLine line,
                                   String enteringPhase,
                                   AxisConfig[] axes) {
        // Determine which axis to step along and in which direction from the line
        int axisIdx = inferPrimaryAxis(line);
        int dir     = inferDirection(line, axisIdx);

        // Exit 1: continue old ZPF line (fixedPhase = old ZPF, forbidden = entering)
        node.addExit(new DiagramExit(node,
                line.fixedPhase,      // ZPF phase stays fixed
                enteringPhase,        // newly entering phase is initially forbidden
                axisIdx, dir));

        // Exit 2: new ZPF line for the entering phase (other direction along boundary)
        node.addExit(new DiagramExit(node,
                enteringPhase,        // entering phase is now the ZPF constraint
                line.fixedPhase,      // old ZPF phase now forbidden
                axisIdx, -dir));
    }

    // ------------------------------------------------------------------
    // Private: helpers
    // ------------------------------------------------------------------>

    /** Find the first excluded phase whose driving force exceeds the tolerance. */
    private String findEnteringPhase(EquilibriumResult eq) {
        for (EquilibriumResult.PhaseResult pr : eq.getMetastablePhases()) {
            if (pr.drivingForce > Constants.DRIVING_FORCE_TOL) return pr.phaseName;
        }
        return null;
    }

    /** Maximum driving force among metastable phases. */
    private double maxDrivingForce(EquilibriumResult eq) {
        double max = Double.NEGATIVE_INFINITY;
        for (EquilibriumResult.PhaseResult pr : eq.getMetastablePhases()) {
            if (pr.drivingForce > max) max = pr.drivingForce;
        }
        return max == Double.NEGATIVE_INFINITY ? 0.0 : max;
    }

    /** Build candidate list excluding named phases. */
    private List<GibbsEnergyModel> exclude(List<GibbsEnergyModel> all,
                                          String ph1, String ph2) {
        List<GibbsEnergyModel> result = new ArrayList<>();
        for (GibbsEnergyModel m : all) {
            if (m.phaseName().equals(ph1)) continue;
            if (ph2 != null && m.phaseName().equals(ph2)) continue;
            result.add(m);
        }
        return result;
    }

    /** Overall composition = mass-average of stable phases. */
    private double[] massAverageComp(EquilibriumResult eq) {
        int nc = eq.getMu().length;
        double[] comp = new double[nc];
        double total = 0;
        for (EquilibriumResult.PhaseResult pr : eq.getStablePhases()) {
            for (int i = 0; i < nc; i++) comp[i] += pr.amount * pr.x[i];
            total += pr.amount;
        }
        if (total > 0) for (int i = 0; i < nc; i++) comp[i] /= total;
        return comp;
    }

    /** Resolve T from axes. */
    private double resolveT(double[] av, AxisConfig[] axes, double fixedT) {
        for (int i = 0; i < axes.length; i++)
            if (axes[i].type == AxisConfig.Type.TEMPERATURE) return av[i];
        return fixedT;
    }

    /** Resolve P from axes. */
    private double resolveP(double[] av, AxisConfig[] axes, double fixedP) {
        for (int i = 0; i < axes.length; i++)
            if (axes[i].type == AxisConfig.Type.PRESSURE) return av[i];
        return fixedP;
    }

    /**
     * Find an existing node within {@code tol} (normalised axis distance).
     * Returns {@code null} if no nearby node found.
     */
    private DiagramNode findNearbyNode(PhaseDiagram diagram,
                                        double[] nodeAxes, double tol) {
        for (DiagramNode n : diagram.getNodes()) {
            double dist = 0;
            for (int k = 0; k < nodeAxes.length; k++) {
                dist = Math.max(dist, Math.abs(n.axisValues[k] - nodeAxes[k]));
            }
            if (dist < tol) return n;
        }
        return null;
    }

    /**
     * Infer the primary stepping axis from the line by finding the axis
     * with the largest total displacement (normalised).
     */
    private int inferPrimaryAxis(DiagramLine line) {
        List<double[]> coords = line.getAxisCoords();
        if (coords.size() < 2) return 0;
        double[] first = coords.get(0);
        double[] last  = coords.get(coords.size() - 1);
        int best = 0;
        double bestDelta = 0;
        for (int k = 0; k < first.length; k++) {
            double d = Math.abs(last[k] - first[k]);
            if (d > bestDelta) { bestDelta = d; best = k; }
        }
        return best;
    }

    /** Infer the direction of travel from the line. */
    private int inferDirection(DiagramLine line, int axisIdx) {
        List<double[]> coords = line.getAxisCoords();
        if (coords.size() < 2) return +1;
        double delta = coords.get(coords.size() - 1)[axisIdx]
                     - coords.get(0)[axisIdx];
        return delta >= 0 ? +1 : -1;
    }
}
