package calc.equil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for a Hyperplane pivot-search failure surfaced by
 * Phase 3C's GridMinimizer &rarr; EquilibriumSolverV2 wiring: a sharply
 * peaked binary CVM point cloud produced a degenerate facet built from two
 * near-duplicate points near x &asymp; 0.152, with barycentric fractions of
 * roughly -695/+696, even though a well-converged, clearly lower-G point at
 * x = 0.5 was present in the same cloud.
 *
 * <p><b>Root cause.</b> {@link Hyperplane#solve} always seeds its pivot
 * search from the arbitrary input-order pair {@code {0, 1, ..., simplexSize-1}}
 * (see the {@code bestGuessSimplex} initialization). When that seed pair
 * happens to be two nearly-coincident compositions, the seed's tieline /
 * barycentric-coordinate matrix is nearly singular (determinant on the
 * order of the points' composition gap, e.g. ~1e-6 for a 1e-5 gap) but is
 * never exactly {@code 0.0}. {@link util.LUDecomposition#isNonsingular()}
 * only checks {@code LU[j][j] == 0.0} with no tolerance, so
 * {@link util.Matrix#solve} happily returns a numerically-exploded but
 * "successful" solution instead of tripping Hyperplane's own
 * {@code SINGULAR_SENTINEL} path. The resulting barycentric fractions are
 * huge (~1e5 magnitude), which satisfies the very first iteration's
 * early-exit check ({@code smallestFractions[savedTrial] < -simplexSize}) --
 * a heuristic meant to recognize genuine degeneracy -- so the pivot loop
 * exits at iteration 0, before ever computing chemical potentials, driving
 * forces, or pivoting toward the true lower-G point. (Confirmed directly:
 * moving the same near-duplicate pair to any other pair of indices lets the
 * search pivot past them and converge on the correct x = 0.5 facet in two
 * iterations.)
 *
 * <p>These tests reproduce the failure with a minimal synthetic point
 * cloud (isolated from any thermodynamic model) and pin down the intended
 * behavior: Hyperplane must not report the target composition as lying
 * on a facet between two near-duplicate points while ignoring a clearly
 * lower-G alternative.
 *
 * <p><b>Phase 3D fix.</b> {@link Hyperplane#solve} now validates its seed
 * simplex's conditioning ({@code selectWellConditionedSeed} /
 * {@code isWellConditionedSimplex}) before entering the pivot loop, and
 * repairs it from the point cloud when degenerate, so the near-duplicate
 * seed described above is no longer accepted uncritically.
 */
class HyperplaneNearDuplicateRegressionTest {

    /**
     * Minimal reproduction of the reported failure: a 5-point binary cloud
     * whose first two points (the arbitrary pivot seed) are nearly
     * coincident in composition, with a well-separated, clearly lower-G
     * point at the target composition x = 0.5.
     *
     * <p>Current (buggy) behavior returns simplex {@code [0, 1]} with
     * fractions of magnitude ~1e5 and energy far above the true minimum;
     * this test asserts the physically correct answer.
     */
    @Test
    void doesNotReportPathologicalFacetWhenSeedPairIsNearDuplicate() {
        double[][] compositions = {
            {0.152000, 0.848000}, // near-duplicate pair (the pivot seed)
            {0.152001, 0.847999}, // near-duplicate pair (the pivot seed)
            {0.500000, 0.500000}, // clearly lower G, at the target composition
            {0.050000, 0.950000},
            {0.950000, 0.050000},
        };
        double[] energies = {
            -1234.5678,
            -1234.5679,
            -2000.0000,
            -500.0,
            -500.0,
        };

        Hyperplane.Result result = Hyperplane.solve(
                compositions, energies, new double[]{0.5});

        assertReasonableFractions(result.fractions);
        assertTrue(containsIndex(result.simplex, 2),
                "expected the clearly lower-G point at x=0.5 (index 2) to "
                + "participate in the selected facet, but simplex was "
                + java.util.Arrays.toString(result.simplex));
        assertEquals(-2000.0, result.energy, 1e-6,
                "expected the reported energy to match the dominant "
                + "lower-G point, not a pathological near-duplicate facet");
    }

    /**
     * Same physical scenario as above, but with the near-duplicate pair
     * placed away from indices {0,1} (the search's arbitrary pivot seed).
     * This must already pass under the existing implementation and pins
     * down that the failure is specific to the seed position, not to the
     * mere presence of near-duplicate points in the cloud.
     */
    @Test
    void nearDuplicatePairAwayFromPivotSeedIsHandledCorrectly() {
        double[][] compositions = {
            {0.500000, 0.500000}, // clearly lower G, at the target composition
            {0.050000, 0.950000},
            {0.152000, 0.848000}, // near-duplicate pair, NOT the pivot seed
            {0.152001, 0.847999}, // near-duplicate pair, NOT the pivot seed
            {0.950000, 0.050000},
        };
        double[] energies = {
            -2000.0000,
            -500.0,
            -1234.5678,
            -1234.5679,
            -500.0,
        };

        Hyperplane.Result result = Hyperplane.solve(
                compositions, energies, new double[]{0.5});

        assertReasonableFractions(result.fractions);
        assertEquals(-2000.0, result.energy, 1e-6);
    }

    /**
     * Barycentric fractions of a valid facet containing the target point
     * must lie in (or very near) [0, 1] and sum to 1 -- large-magnitude,
     * opposite-sign fractions (as produced by the bug) are geometrically
     * meaningless for a point actually inside the simplex.
     */
    private static void assertReasonableFractions(double[] fractions) {
        double sum = 0.0;
        for (double f : fractions) {
            assertTrue(f > -1e-6 && f < 1.0 + 1e-6,
                    "barycentric fraction out of [0,1] range: "
                    + java.util.Arrays.toString(fractions));
            sum += f;
        }
        assertEquals(1.0, sum, 1e-6,
                "barycentric fractions must sum to 1: "
                + java.util.Arrays.toString(fractions));
    }

    private static boolean containsIndex(int[] simplex, int target) {
        for (int idx : simplex) {
            if (idx == target) {
                return true;
            }
        }
        return false;
    }
}
