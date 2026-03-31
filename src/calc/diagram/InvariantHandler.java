package calc.diagram;

import contracts.EquilibriumResult;
import system.model.GibbsEnergyModel;
import util.Matrix;
import util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Algorithm D — Invariant equilibrium handler.
 *
 * <p>Called by {@link PhaseChangeHandler} when the Gibbs phase rule gives
 * f = 0 at a node (e.g. a eutectic or peritectic point in a binary T-x
 * diagram where three phases coexist).
 *
 * <h2>Exit topology at an invariant node</h2>
 * At invariant equilibrium, p = c + n_axes phases are stable.  Leaving the
 * node along a ZPF line requires fixing one phase at zero amount.  For each
 * candidate ZPF phase i, the remaining (p-1) phases must satisfy the mass
 * balance equation (Eq. 9 of Sundman et al. 2021):
 *
 * <pre>
 *   Σ_{α ≠ i,j}  ℵ^α · x^α_A  =  x̃_A     for all components A
 * </pre>
 *
 * where j is a second excluded phase used to make the system square.
 * If the solution has all amounts &gt; 0, exit (fixedPhase=i, forbidden=j)
 * and exit (fixedPhase=j, forbidden=i) are both valid.
 *
 * <p>In practice for binary T-x (p=3, c=2):  we test every ordered pair
 * (i, j) of the three stable phases.  For each pair, solving the 2×2 mass
 * balance for the one remaining phase gives a single scalar; if it is
 * positive we add two exits.  This results in exactly 4 exits for a
 * eutectic/peritectic.
 */
public class InvariantHandler {

    private static final Logger LOG = Logger.getLogger(InvariantHandler.class.getName());

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Compute and attach all valid ZPF exits to {@code node}.
     *
     * <p>Iterates over all ordered pairs (i, j) of stable phases at the node.
     * For each pair, solves the reduced mass balance (excluding phases i and j)
     * and adds two exits if the remaining amounts are all positive.
     *
     * @param node        the invariant node (must have ≥ 3 stable phases)
     * @param candidates  all candidate phase models (for axis-direction inference)
     * @param axes        diagram axis configurations
     */
    public void findExits(DiagramNode node,
                          List<GibbsEnergyModel> candidates,
                          AxisConfig[] axes) {

        List<EquilibriumResult.PhaseResult> stable =
                node.equilibrium.getStablePhases();
        int p = stable.size();

        if (p < 3) {
            LOG.warning("InvariantHandler called with only " + p
                    + " stable phases — expected ≥ 3 for invariant point.");
            return;
        }

        LOG.fine("InvariantHandler: node " + node.id + " has " + p + " phases.");

        int exitsAdded = 0;

        // Iterate over all ordered pairs (i, j)
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                if (i == j) continue;

                String phaseI = stable.get(i).phaseName;
                String phaseJ = stable.get(j).phaseName;

                if (feasibleExcluding(stable, i, j,
                        node.equilibrium.getMu().length)) {
                    // Both exits for this (i,j) pair are valid
                    int axisIdx = inferPrimaryAxis(axes, node);

                    node.addExit(new DiagramExit(node,
                            phaseI,   // ZPF phase = i
                            phaseJ,   // forbidden = j
                            axisIdx, +1));

                    node.addExit(new DiagramExit(node,
                            phaseI,   // ZPF phase = i
                            phaseJ,   // forbidden = j
                            axisIdx, -1));

                    exitsAdded += 2;
                    LOG.fine("InvariantHandler: added exits fixed=" + phaseI
                            + " forbidden=" + phaseJ);
                }
            }
        }

        LOG.fine("InvariantHandler: total exits added = " + exitsAdded);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Solve the reduced mass balance excluding phases {@code iExcl} and
     * {@code jExcl}, and return {@code true} if all remaining phase amounts
     * are positive (Eq. 9 of Sundman et al.).
     *
     * <p>The system is:
     * <pre>
     *   Σ_{α ∉ {i,j}}  ℵ^α · x^α_A  =  x̃_A   for each component A
     * </pre>
     * where x̃_A is the overall composition (recovered from total amounts).
     *
     * @param stable   stable phase results at the node
     * @param iExcl    index of first excluded phase
     * @param jExcl    index of second excluded phase
     * @param nc       number of components
     * @return {@code true} if the linear system has a solution with all amounts &gt; 0
     */
    private boolean feasibleExcluding(List<EquilibriumResult.PhaseResult> stable,
                                       int iExcl, int jExcl, int nc) {

        // Build list of remaining phases (not i, not j)
        List<EquilibriumResult.PhaseResult> remaining = new ArrayList<>();
        for (int k = 0; k < stable.size(); k++) {
            if (k != iExcl && k != jExcl) remaining.add(stable.get(k));
        }
        int nr = remaining.size();
        if (nr == 0) return false;

        // Overall composition = mass-average of ALL stable phases
        double totalN = 0;
        double[] xOverAll = new double[nc];
        for (EquilibriumResult.PhaseResult pr : stable) {
            for (int a = 0; a < nc; a++) xOverAll[a] += pr.amount * pr.x[a];
            totalN += pr.amount;
        }
        if (totalN > 0) for (int a = 0; a < nc; a++) xOverAll[a] /= totalN;

        if (nr == 1) {
            // Only one remaining phase: its amount must satisfy x_remaining ≈ xOverAll
            // Check that the single phase composition is compatible with overall
            EquilibriumResult.PhaseResult ph = remaining.get(0);
            // For mass balance, ℵ · x_A = xOverAll_A  →  ℵ = xOverAll_A / x_A
            // (take component with largest x to avoid division by near-zero)
            double amount = estimateSingleAmount(ph, xOverAll, nc);
            return amount > Constants.MIN_INVARIANT_AMOUNT;
        }

        // General case: solve Σ ℵ^α · x^α_A = xOverAll_A (nr unknowns, nc equations)
        // Use first min(nc, nr) equations + sum-to-1 constraint
        int nEq = Math.min(nc, nr);
        double[][] A = new double[nr][nr];
        double[] b = new double[nr];

        for (int eq = 0; eq < nEq; eq++) {
            for (int k = 0; k < nr; k++) {
                A[eq][k] = remaining.get(k).x[eq];
            }
            b[eq] = xOverAll[eq];
        }
        // Fill remaining equations with sum = 1 constraint (if needed)
        for (int eq = nEq; eq < nr; eq++) {
            for (int k = 0; k < nr; k++) A[eq][k] = 1.0;
            b[eq] = 1.0;
        }

        try {
            Matrix matA = new Matrix(A);
            Matrix matB = new Matrix(b, nr);
            Matrix sol  = matA.solve(matB);
            for (int k = 0; k < nr; k++) {
                if (sol.get(k, 0) < Constants.MIN_INVARIANT_AMOUNT) return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Estimate the amount of a single remaining phase from the overall
     * composition.  Uses the component with the largest mole fraction to
     * avoid division by near-zero.
     */
    private double estimateSingleAmount(EquilibriumResult.PhaseResult ph,
                                         double[] xOverAll, int nc) {
        int best = 0;
        for (int a = 1; a < nc; a++) {
            if (xOverAll[a] > xOverAll[best]) best = a;
        }
        if (ph.x[best] < 1e-12) return 0.0;
        return xOverAll[best] / ph.x[best];
    }

    /**
     * Infer a sensible primary axis index for exits from this node.
     * Prefers a TEMPERATURE axis; falls back to axis 0.
     */
    private int inferPrimaryAxis(AxisConfig[] axes, DiagramNode node) {
        for (int i = 0; i < axes.length; i++) {
            if (axes[i].type == AxisConfig.Type.TEMPERATURE) return i;
        }
        return 0;
    }
}
