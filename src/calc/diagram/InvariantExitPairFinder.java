package calc.diagram;

import util.Matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Algorithm D (Sundman 2021 CALPHAD 75, Fig. 7): finds every pair of
 * phases that can be excluded together from an invariant node's stable
 * set while leaving every other phase at a strictly positive amount.
 */
final class InvariantExitPairFinder {

    private InvariantExitPairFinder() {
    }

    /**
     * One candidate exit pair: the two phases that go to zero amount
     * together at the node, with all other stable phases at strictly
     * positive amount.
     */
    static final class ExitPair {

        final String beta1;
        final String beta2;

        ExitPair(String beta1, String beta2) {
            this.beta1 = beta1;
            this.beta2 = beta2;
        }

        @Override
        public String toString() {
            return "ExitPair[" + beta1 + ", " + beta2 + "]";
        }
    }

    /**
     * Finds every valid exit pair from an invariant node.
     *
     * @param stablePhaseNames    names of every phase present at the node
     * @param stableCompositions  each phase's mole fractions,
     *                            {@code [phase][component]}, same
     *                            order/length as {@code stablePhaseNames}
     * @param targetComposition   the node's overall mole fractions
     * @param arrivalPair         the pair of phases already known to have
     *                            zero amount together on the line the
     *                            algorithm arrived by, excluded from the
     *                            result since that exit already exists;
     *                            {@code null} if not applicable
     * @return every other valid exit pair
     */
    static List<ExitPair> findExitPairs(
            List<String> stablePhaseNames,
            double[][] stableCompositions,
            double[] targetComposition,
            ExitPair arrivalPair) {

        int p = stablePhaseNames.size();
        List<ExitPair> pairs = new ArrayList<>();

        for (int i = 0; i < p - 1; i++) {
            for (int j = i + 1; j < p; j++) {

                double[] remainingAmounts = solveRemainingAmounts(
                        i, j, stableCompositions, targetComposition);

                if (remainingAmounts == null) {
                    continue;
                }

                boolean allPositive = true;
                for (double amount : remainingAmounts) {
                    if (!(amount > 0.0)) {
                        allPositive = false;
                        break;
                    }
                }
                if (!allPositive) {
                    continue;
                }

                String beta1 = stablePhaseNames.get(i);
                String beta2 = stablePhaseNames.get(j);

                if (arrivalPair != null && isSamePair(arrivalPair, beta1, beta2)) {
                    continue;
                }

                pairs.add(new ExitPair(beta1, beta2));
            }
        }

        return pairs;
    }

    private static boolean isSamePair(ExitPair pair, String beta1, String beta2) {
        return (pair.beta1.equals(beta1) && pair.beta2.equals(beta2))
                || (pair.beta1.equals(beta2) && pair.beta2.equals(beta1));
    }

    /**
     * Solves for the amounts of every stable phase excluding the pair at
     * indices {@code excludeI}/{@code excludeJ}, via mass-balance plus
     * sum-to-one.
     *
     * @return the solved amounts, same order as {@code stableCompositions}
     *         with indices {@code excludeI}/{@code excludeJ} removed, or
     *         {@code null} if there is no solution
     */
    private static double[] solveRemainingAmounts(
            int excludeI,
            int excludeJ,
            double[][] stableCompositions,
            double[] targetComposition) {

        int p = stableCompositions.length;
        int ncomp = targetComposition.length;
        int remainingCount = p - 2;

        if (remainingCount != ncomp - 1) {
            return null;
        }

        int[] remaining = new int[remainingCount];
        int k = 0;
        for (int idx = 0; idx < p; idx++) {
            if (idx == excludeI || idx == excludeJ) continue;
            remaining[k++] = idx;
        }

        double[][] A = new double[ncomp][remainingCount];
        double[] b = new double[ncomp];

        for (int i = 0; i < ncomp - 1; i++) {
            for (int j = 0; j < remainingCount; j++) {
                A[i][j] = stableCompositions[remaining[j]][i];
            }
            b[i] = targetComposition[i];
        }

        // Sum-to-one row.
        for (int j = 0; j < remainingCount; j++) {
            A[ncomp - 1][j] = 1.0;
        }
        b[ncomp - 1] = 1.0;

        try {

            Matrix matA = new Matrix(A);
            Matrix matB = new Matrix(b, ncomp);
            return matA.solve(matB).getColumnPackedCopy();

        } catch (RuntimeException e) {

            return null;
        }
    }
}
