package calc.equil;

/**
 * Multi-dimensional scrambled Halton sequence generator.
 *
 * <p>Direct port of pycalphad's {@code pycalphad.core.halton.halton}, which
 * is itself based on:
 *
 * <p>Chi, H., Mascagni, M., &amp; Warnock, T. (2005). On the optimal Halton
 * sequence. Mathematics and Computers in Simulation, 70(1), 9-21.
 * doi:10.1016/j.matcom.2005.03.004
 *
 * <p>The scrambling multipliers below are copied verbatim from pycalphad so
 * that, given the same {@code dim} and {@code nbpts}, this generator
 * produces the same low-discrepancy sequence pycalphad's grid sampler uses
 * (before pycalphad's exponential-then-normalize transform onto the
 * simplex -- see {@link GridMinimizer#pointSample}).
 */
public final class Halton {

    private Halton() { }

    /**
     * (prime, scrambling multiplier) pairs, in the same order as
     * pycalphad's {@code _scrambling_multipliers}. Only the primes needed
     * to reach the requested dimension are used.
     */
    private static final int[][] SCRAMBLING_MULTIPLIERS = {
        {2, 1}, {3, 2}, {5, 2}, {7, 5}, {11, 3}, {13, 7}, {17, 3},
        {19, 10}, {23, 18}, {29, 11}, {31, 17}, {37, 5}, {41, 17},
        {43, 26}, {47, 40}, {53, 14}, {59, 40}, {61, 44}, {67, 12},
        {71, 31}, {73, 45}, {79, 70}, {83, 8}, {89, 38}, {97, 82},
        {101, 8}, {103, 12}, {107, 38}, {109, 47}, {113, 70},
        {127, 29}, {131, 57}, {137, 97}, {139, 110}, {149, 32},
        {151, 48}, {157, 84}, {163, 124}, {167, 155}, {173, 26},
        {179, 69}, {181, 83}, {191, 157}, {193, 171}, {197, 8},
        {199, 32}, {211, 112}, {223, 205}, {227, 15}, {229, 31},
        {233, 61}, {239, 112}, {241, 127}, {251, 212}, {257, 7},
        {263, 57}, {269, 108}, {271, 120}, {277, 178}, {281, 210},
        {283, 234}, {293, 34}, {307, 161}, {311, 199}, {313, 219},
        {317, 255}, {331, 63}, {337, 120}, {347, 218}, {349, 237},
        {353, 278}, {359, 341}, {367, 58}, {373, 118}, {379, 176},
        {383, 218}, {389, 282}, {397, 369}, {401, 12}, {409, 93},
    };

    /**
     * Generates a scrambled Halton sequence.
     *
     * @param dim   number of dimensions
     * @param nbpts number of points along each dimension
     * @return array of shape {@code [nbpts][dim]}
     */
    public static double[][] generate(int dim, int nbpts) {

        if (dim > SCRAMBLING_MULTIPLIERS.length) {
            throw new IllegalArgumentException(
                    "Halton sequence of " + dim
                    + " dimensions requested but only "
                    + SCRAMBLING_MULTIPLIERS.length
                    + " primes/multipliers are available.");
        }

        double[][] result = new double[nbpts][dim];

        for (int d = 0; d < dim; d++) {

            int prime = SCRAMBLING_MULTIPLIERS[d][0];
            int scrambler = SCRAMBLING_MULTIPLIERS[d][1];

            int numPowers =
                    (int) Math.ceil(
                            Math.log(nbpts + 1) / Math.log(prime));

            // powers[k] = prime^-(k+1), radix[k] = prime^-k
            double[] powers = new double[numPowers];
            double[] radix = new double[numPowers];

            for (int k = 0; k < numPowers; k++) {
                powers[k] = Math.pow(prime, -(k + 1));
                radix[k] = Math.pow(prime, -k);
            }

            for (int row = 0; row < nbpts; row++) {

                int n = row + 1;
                double value = 0.0;

                for (int k = 0; k < numPowers; k++) {

                    double sum = n * radix[k];
                    double modded =
                            Math.floor(sum + 1e-15) % prime;

                    // Java's % can return a negative remainder for
                    // negative operands; floor(sum) here is always
                    // >= 0 for n,radix > 0, so this guard is defensive.
                    if (modded < 0) {
                        modded += prime;
                    }

                    double scrambled =
                            (scrambler * modded) % prime;

                    if (scrambled < 0) {
                        scrambled += prime;
                    }

                    value += scrambled * powers[k];
                }

                result[row][d] = value;
            }
        }

        return result;
    }

    /**
     * Direct port of pycalphad's {@code pycalphad.core.utils.point_sample}.
     *
     * <p>Samples {@code pdof * (sum(compCount) - compCount.length)} points
     * in composition space for the sublattice configuration specified by
     * {@code compCount}, quasi-randomly from a scrambled Halton sequence.
     * The sequence is transformed via {@code -log(u)} then renormalized
     * within each sublattice's block, which distributes the points
     * uniformly over each sublattice's simplex.
     *
     * @param compCount number of constituents in each sublattice
     * @param pdof      number of points to sample per degree of freedom
     * @return array of shape {@code [numPoints][sum(compCount)]}; if the
     *         requested degrees of freedom are zero (a single-constituent
     *         system), returns one row of all 1.0 (matching pycalphad's
     *         fallback for that case)
     */
    public static double[][] pointSample(int[] compCount, int pdof) {

        int dim = 0;
        int dof = 0;

        for (int c : compCount) {
            dim += c;
            dof += (c - 1);
        }

        int nbpts = pdof * dof;

        if (nbpts <= 0) {
            double[][] fallback = new double[1][compCount.length];
            for (int i = 0; i < compCount.length; i++) {
                fallback[0][i] = 1.0;
            }
            return fallback;
        }

        double[][] pts = generate(dim, nbpts);

        // pts = -log(pts)
        for (double[] row : pts) {
            for (int i = 0; i < row.length; i++) {
                row[i] = -Math.log(row[i]);
            }
        }

        // Renormalize each sublattice's block to sum to 1 (uniform over
        // that sublattice's simplex).
        int curIdx = 0;
        for (int c : compCount) {
            int endIdx = curIdx + c;
            for (double[] row : pts) {
                double sum = 0.0;
                for (int i = curIdx; i < endIdx; i++) {
                    sum += row[i];
                }
                for (int i = curIdx; i < endIdx; i++) {
                    row[i] /= sum;
                }
            }
            curIdx = endIdx;
        }

        return pts;
    }
}
