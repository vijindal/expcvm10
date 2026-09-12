package calc.diagram;

import util.Matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Sundman 2021 CALPHAD 75's Algorithm D: at an invariant node (a point
 * where {@code ncomp+1} phases coexist -- one more than an adjacent
 * two-phase-region-boundary line's stable-phase count for a binary
 * system, matching the classical Gibbs-phase-rule invariant, e.g. a
 * binary eutectic/peritectic with exactly 3 phases), enumerate every
 * subset of {@code ncomp} phases (excluding exactly ONE of the {@code
 * ncomp+1} node phases) and check whether the mass-balance conditions
 * can be satisfied with strictly positive amounts of just that subset
 * -- a valid subset identifies a distinct exit line leaving the node,
 * with the ONE excluded phase being the phase that goes to zero amount
 * along that exit.
 *
 * <p><b>Combinatorics verified against two independent references</b>
 * (both cross-checked this session after an earlier, incorrect
 * {@code exclude-2-phases} version of this class was found to always
 * return an empty list for the exact binary case {@link MapTracer}
 * exercises):
 * <ul>
 *   <li>pycalphad's {@code isopleth_strategy.py}
 *       ({@code _invariant_exits}, C:\Users\admin\codes\pycalphad):
 *       at a node with {@code p} phases, "a ZPF surface coming out of a
 *       node has at most {@code p-1} phases" -- i.e. exactly ONE phase
 *       is excluded per exit, not two.</li>
 *   <li>pycalphad's {@code binary_strategy.py}/{@code ternary_strategy.py}
 *       {@code _find_exits_from_node}: a binary/ternary invariant always
 *       has exactly 3 stable phases, and exits are {@code
 *       itertools.combinations(node.stable_composition_sets, 2)} --
 *       {@code C(3,2)=3} candidate exits, each KEEPING 2 phases
 *       (equivalently excluding the 1 remaining phase).</li>
 * </ul>
 * Both agree: for a binary ({@code ncomp=2}) invariant with 3 phases,
 * there are exactly 3 candidate exits, each a 2-phase pair excluding
 * exactly 1 phase -- matching the classical eutectic/peritectic
 * picture (three two-phase regions meeting at one point) and this
 * class's corrected formula below.
 *
 * <p>A subset's amounts are found by solving a linear mass-balance +
 * sum-to-one system ({@link Matrix#solve}, the same linear-solve
 * machinery {@code GlobalEquilibriumMatrixAssembler} already uses,
 * mirroring OpenCalphad's own {@code dgetrf}/{@code dgetrs} positivity
 * test in {@code find_inv_exits}, smp2A.F90); a subset is a valid exit
 * iff every solved amount is strictly positive.
 *
 * <p>Cost is combinatorial in the number of phases present at the node
 * ({@code C(ncomp+1, ncomp) = ncomp+1} linear solves for the base
 * invariant case). This class is written generally (any component
 * count) but is verified in this codebase only against binary systems
 * ({@code ncomp=2}, 3 node phases, 3 candidate exits), matching {@link
 * MapTracer}'s own scope.
 */
public final class InvariantExitFinder {

    private InvariantExitFinder() {
    }

    /** One candidate exit: the single phase that goes to zero amount along that boundary line. */
    public static final class ExitCandidate {

        public final String excludedPhase;

        /** The phases that remain stable along this exit's boundary line. */
        public final List<String> stablePhases;

        public ExitCandidate(String excludedPhase, List<String> stablePhases) {
            this.excludedPhase = excludedPhase;
            this.stablePhases = stablePhases;
        }

        @Override
        public String toString() {
            return "ExitCandidate[excluded=" + excludedPhase + ", stable=" + stablePhases + "]";
        }
    }

    /**
     * Finds every valid exit from an invariant node.
     *
     * @param candidatePhaseNames   names of every phase present at the node,
     *                              length {@code ncomp+1}
     * @param candidateCompositions each phase's mole fractions,
     *                              {@code [phase][component]}, same order/length
     *                              as {@code candidatePhaseNames}
     * @param targetComposition     the node's overall mole fractions, length {@code ncomp}
     * @param arrivedViaExcludedPhase the single phase that was excluded (zero amount)
     *                              on the line the algorithm arrived by; excluded
     *                              from the result since that exit already exists
     * @return every OTHER valid exit (excludes the arrival exit)
     */
    public static List<ExitCandidate> findExits(
            List<String> candidatePhaseNames,
            double[][] candidateCompositions,
            double[] targetComposition,
            String arrivedViaExcludedPhase) {

        int nPhasesAtNode = candidatePhaseNames.size();
        int ncomp = targetComposition.length;
        int subsetSize = ncomp;

        List<ExitCandidate> exits = new ArrayList<>();

        int[] indices = new int[subsetSize];
        combinations(nPhasesAtNode, subsetSize, indices, 0, 0, subset -> {

            double[] amounts = solveSubsetAmounts(
                    subset, candidateCompositions, targetComposition);

            if (amounts == null) {
                return;
            }

            for (double amount : amounts) {
                if (!(amount > 0.0)) {
                    return;
                }
            }

            List<Integer> excluded = new ArrayList<>();
            for (int i = 0; i < nPhasesAtNode; i++) {
                if (!contains(subset, i)) {
                    excluded.add(i);
                }
            }

            if (excluded.size() != 1) {
                // Should not happen: nPhasesAtNode - subsetSize == 1
                // whenever nPhasesAtNode == ncomp+1, subsetSize == ncomp.
                return;
            }

            String excludedPhase = candidatePhaseNames.get(excluded.get(0));

            if (excludedPhase.equals(arrivedViaExcludedPhase)) {
                return;
            }

            List<String> stablePhases = new ArrayList<>();
            for (int idx : subset) {
                stablePhases.add(candidatePhaseNames.get(idx));
            }

            exits.add(new ExitCandidate(excludedPhase, stablePhases));
        });

        return exits;
    }

    /**
     * Solves {@code amounts} such that
     * {@code sum_j amounts[j] * candidateCompositions[subset[j]][i] == targetComposition[i]}
     * for every component {@code i}, plus the sum-to-one row
     * {@code sum_j amounts[j] == 1}.
     *
     * @return the solved amounts, or {@code null} if the subset's
     *         composition matrix is singular (no solution -- not a
     *         numerical failure, just not a valid exit)
     */
    private static double[] solveSubsetAmounts(
            int[] subset,
            double[][] candidateCompositions,
            double[] targetComposition) {

        int ncomp = targetComposition.length;
        int subsetSize = subset.length;

        double[][] A = new double[subsetSize][subsetSize];
        double[] b = new double[subsetSize];

        // ncomp-1 composition rows (component 0..ncomp-2 -- the last
        // component's fraction is implied by summing to 1, matching
        // this codebase's own convention elsewhere, e.g.
        // Hyperplane.solve's targetMoleFractions contract).
        for (int i = 0; i < ncomp - 1; i++) {
            for (int j = 0; j < subsetSize; j++) {
                A[i][j] = candidateCompositions[subset[j]][i];
            }
            b[i] = targetComposition[i];
        }

        // Sum-to-one row.
        for (int j = 0; j < subsetSize; j++) {
            A[ncomp - 1][j] = 1.0;
        }
        b[ncomp - 1] = 1.0;

        try {

            Matrix matA = new Matrix(A);
            Matrix matB = new Matrix(b, subsetSize);
            return matA.solve(matB).getColumnPackedCopy();

        } catch (RuntimeException e) {

            return null;
        }
    }

    private static boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface SubsetConsumer {
        void accept(int[] subset);
    }

    /** Enumerates every subset of size {@code k} out of {@code 0..n-1}, in increasing index order. */
    private static void combinations(
            int n, int k, int[] buffer, int start, int filled, SubsetConsumer consumer) {

        if (filled == k) {
            consumer.accept(buffer.clone());
            return;
        }

        for (int i = start; i < n; i++) {
            buffer[filled] = i;
            combinations(n, k, buffer, i + 1, filled + 1, consumer);
        }
    }
}
