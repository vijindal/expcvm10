package calc.diagram;

import util.Matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Sundman 2021 CALPHAD 75's Algorithm D: at an invariant node (a point
 * where {@code ncomp+2} phases coexist, one more than an adjacent
 * two-phase-region-boundary line's stable-phase count for a binary
 * system), enumerate every subset of {@code ncomp+1} phases and check
 * whether the mass-balance conditions can be satisfied with strictly
 * positive amounts of just that subset -- a valid subset identifies a
 * distinct exit line leaving the node, with the two EXCLUDED phases
 * being that exit's zero-amount pair.
 *
 * <p>Ported from OpenCalphad's {@code find_inv_exits}
 * (C:\Users\admin\codes\opencalphad\src\stepmapplot\smp2A.F90,
 * lines 5952-6248), verified directly against that source this session.
 * Its own design comment states the approach directly:
 * <pre>
 * 1. extract the composition of all stable phases at invariant (N+1)
 * 2. set up a system of linear equations M_j x_ij = c_i
 *    where x_ij is composition of component i in phase j, c_i is the
 *    condition for component i, (N-1 conditions), M_j amount of phase j
 * 3. The cases when this system has a solution represent exits
 * </pre>
 * A subset's amounts are found by solving {@code test*N = rhs} (LAPACK
 * {@code dgetrf}/{@code dgetrs} in OpenCalphad; {@link Matrix#solve} here
 * -- the same linear-solve machinery {@code GlobalEquilibriumMatrixAssembler}
 * already uses); a subset is a valid exit iff every solved amount is
 * strictly positive (OpenCalphad: {@code if(rhs(kk).le.zero) goto 100}).
 *
 * <p>Cost is combinatorial in the number of phases present at the node
 * ({@code C(phasesAtNode, ncomp+1)} linear solves) -- OpenCalphad's own
 * comment notes "limit to 8" phases at a node in practice; this class is
 * written generally (any component count) but is verified in this
 * codebase only against binary systems ({@code ncomp=2}, where an
 * invariant has exactly 3 coexisting phases and {@code C(3,3)=1}
 * subset to check), matching {@link MapTracer}'s own scope.
 */
public final class InvariantExitFinder {

    private InvariantExitFinder() {
    }

    /** One candidate exit: the two phases with zero amount along that boundary line. */
    public static final class ExitCandidate {

        public final String phaseA;
        public final String phaseB;

        public ExitCandidate(String phaseA, String phaseB) {
            this.phaseA = phaseA;
            this.phaseB = phaseB;
        }

        @Override
        public String toString() {
            return "ExitCandidate[" + phaseA + ", " + phaseB + "]";
        }
    }

    /**
     * Finds every valid exit from an invariant node.
     *
     * @param candidatePhaseNames   names of every phase present at the node,
     *                              length {@code ncomp+2}
     * @param candidateCompositions each phase's mole fractions,
     *                              {@code [phase][component]}, same order/length
     *                              as {@code candidatePhaseNames}
     * @param targetComposition     the node's overall mole fractions, length {@code ncomp}
     * @param arrivedViaPhaseA      one of the two phases with zero amount on the line
     *                              the algorithm arrived by (excluded from the result)
     * @param arrivedViaPhaseB      the other phase with zero amount on the arrival line
     * @return every OTHER valid exit (excludes the arrival pair)
     */
    public static List<ExitCandidate> findExits(
            List<String> candidatePhaseNames,
            double[][] candidateCompositions,
            double[] targetComposition,
            String arrivedViaPhaseA,
            String arrivedViaPhaseB) {

        int nPhasesAtNode = candidatePhaseNames.size();
        int ncomp = targetComposition.length;
        int subsetSize = ncomp + 1;

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

            if (excluded.size() != 2) {
                // Should not happen: nPhasesAtNode - subsetSize == 2
                // whenever nPhasesAtNode == ncomp+2, subsetSize == ncomp+1.
                return;
            }

            String phaseA = candidatePhaseNames.get(excluded.get(0));
            String phaseB = candidatePhaseNames.get(excluded.get(1));

            boolean isArrivalPair =
                    (phaseA.equals(arrivedViaPhaseA) && phaseB.equals(arrivedViaPhaseB))
                    || (phaseA.equals(arrivedViaPhaseB) && phaseB.equals(arrivedViaPhaseA));

            if (!isArrivalPair) {
                exits.add(new ExitCandidate(phaseA, phaseB));
            }
        });

        return exits;
    }

    /**
     * Solves {@code amounts} such that
     * {@code sum_j amounts[j] * candidateCompositions[subset[j]][i] == targetComposition[i]}
     * for every component {@code i}, plus the sum-to-one row
     * {@code sum_j amounts[j] == 1}, mirroring OpenCalphad's own
     * {@code find_inv_exits} system (N-1 composition conditions + the
     * implicit total-amount-normalization row, for N unknowns).
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
