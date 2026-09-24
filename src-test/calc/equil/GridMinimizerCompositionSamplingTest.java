package calc.equil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for GridMinimizer's composition-space sampling
 * (sampleCompositions) -- Phase 3A.
 *
 * <p>Verifies that sampled compositions satisfy simplex constraints,
 * include pure-component and boundary-edge points, and deterministically
 * reproduce the same point set, without depending on the exact internal
 * Halton implementation beyond the observable output.
 */
class GridMinimizerCompositionSamplingTest {

    private static final double TOL = 1.0e-9;

    private final GridMinimizer minimizer = new GridMinimizer();

    // ─────────────────────────────────────────────────────────────────
    // Helper: access the private sampleCompositions via reflection
    // ─────────────────────────────────────────────────────────────────

    private double[][] sampleCompositions(int nc) throws Exception {
        var method = GridMinimizer.class.getDeclaredMethod(
                "sampleCompositions", int.class);
        method.setAccessible(true);
        return (double[][]) method.invoke(minimizer, nc);
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers: validation predicates
    // ─────────────────────────────────────────────────────────────────

    private boolean isValidSimplex(double[] x) {
        for (double xi : x) {
            if (xi < -TOL) return false;
        }
        double sum = 0.0;
        for (double xi : x) {
            sum += xi;
        }
        return Math.abs(sum - 1.0) < TOL;
    }

    private boolean isPureComponent(double[] x, int i) {
        if (Math.abs(x[i] - 1.0) > TOL) return false;
        for (int j = 0; j < x.length; j++) {
            if (i != j && Math.abs(x[j]) > TOL) {
                return false;
            }
        }
        return true;
    }

    private int countPureComponents(double[][] points, int nc) {
        Set<Integer> found = new HashSet<>();
        for (double[] x : points) {
            for (int i = 0; i < nc; i++) {
                if (isPureComponent(x, i)) {
                    found.add(i);
                }
            }
        }
        return found.size();
    }

    /** Counts distinct component pairs represented by exactly-two-nonzero points. */
    private int countBinaryEdges(double[][] points, int nc) {
        Set<String> found = new HashSet<>();
        for (double[] x : points) {
            List<Integer> nonzero = new ArrayList<>();
            for (int i = 0; i < nc; i++) {
                if (Math.abs(x[i]) > TOL) {
                    nonzero.add(i);
                }
            }
            if (nonzero.size() == 2) {
                int a = nonzero.get(0);
                int b = nonzero.get(1);
                found.add(a + "," + b);
            }
        }
        return found.size();
    }

    private int countInteriorPoints(double[][] points, int nc) {
        int count = 0;
        for (double[] x : points) {
            boolean allPositive = true;
            boolean hasUnit = false;
            for (double xi : x) {
                if (xi < TOL) {
                    allPositive = false;
                }
                if (Math.abs(xi - 1.0) < TOL) {
                    hasUnit = true;
                }
            }
            if (allPositive && !hasUnit) {
                count++;
            }
        }
        return count;
    }

    // ─────────────────────────────────────────────────────────────────
    // Binary (nc=2)
    // ─────────────────────────────────────────────────────────────────

    @Test
    void binaryValidSimplex() throws Exception {
        double[][] points = sampleCompositions(2);
        for (double[] x : points) {
            assertTrue(isValidSimplex(x), "Point not on simplex: " + Arrays.toString(x));
        }
    }

    @Test
    void binaryAllComponentsInRange() throws Exception {
        double[][] points = sampleCompositions(2);
        for (double[] x : points) {
            for (double xi : x) {
                assertTrue(xi >= -TOL && xi <= 1.0 + TOL,
                        "Component out of [0,1]: " + xi);
            }
        }
    }

    @Test
    void binaryPureEndpointsPresent() throws Exception {
        double[][] points = sampleCompositions(2);
        assertEquals(2, countPureComponents(points, 2),
                "Missing pure-component endpoints");
    }

    @Test
    void binaryEdgeCoveragePresent() throws Exception {
        double[][] points = sampleCompositions(2);
        assertEquals(1, countBinaryEdges(points, 2),
                "Should have the single binary edge (0,1)");
    }

    @Test
    void binaryDeterministic() throws Exception {
        double[][] run1 = sampleCompositions(2);
        double[][] run2 = sampleCompositions(2);
        assertEquals(run1.length, run2.length, "Point count differs");
        for (int i = 0; i < run1.length; i++) {
            assertArrayEquals(run1[i], run2[i], TOL, "Point " + i + " differs");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Ternary (nc=3)
    // ─────────────────────────────────────────────────────────────────

    @Test
    void ternaryAllPointsOnSimplex() throws Exception {
        double[][] points = sampleCompositions(3);
        for (double[] x : points) {
            assertTrue(isValidSimplex(x), "Point not on simplex: " + Arrays.toString(x));
        }
    }

    @Test
    void ternaryAllThreePureComponentsPresent() throws Exception {
        double[][] points = sampleCompositions(3);
        assertEquals(3, countPureComponents(points, 3));
    }

    @Test
    void ternaryAllThreeBinaryEdgesRepresented() throws Exception {
        double[][] points = sampleCompositions(3);
        assertEquals(3, countBinaryEdges(points, 3));
    }

    @Test
    void ternaryInteriorPointsExist() throws Exception {
        double[][] points = sampleCompositions(3);
        assertTrue(countInteriorPoints(points, 3) > 0);
    }

    @Test
    void ternaryDeterministic() throws Exception {
        double[][] run1 = sampleCompositions(3);
        double[][] run2 = sampleCompositions(3);
        assertEquals(run1.length, run2.length);
        for (int i = 0; i < run1.length; i++) {
            assertArrayEquals(run1[i], run2[i], TOL);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Quaternary (nc=4)
    // ─────────────────────────────────────────────────────────────────

    @Test
    void quaternaryAllPointsOnSimplex() throws Exception {
        double[][] points = sampleCompositions(4);
        for (double[] x : points) {
            assertTrue(isValidSimplex(x), "Point not on simplex: " + Arrays.toString(x));
        }
    }

    @Test
    void quaternaryAllFourPureComponentsPresent() throws Exception {
        double[][] points = sampleCompositions(4);
        assertEquals(4, countPureComponents(points, 4));
    }

    @Test
    void quaternaryAllSixBinaryEdgesRepresented() throws Exception {
        double[][] points = sampleCompositions(4);
        assertEquals(6, countBinaryEdges(points, 4));
    }

    @Test
    void quaternaryInteriorPointsExist() throws Exception {
        double[][] points = sampleCompositions(4);
        assertTrue(countInteriorPoints(points, 4) > 0);
    }

    @Test
    void quaternaryDeterministic() throws Exception {
        double[][] run1 = sampleCompositions(4);
        double[][] run2 = sampleCompositions(4);
        assertEquals(run1.length, run2.length);
        for (int i = 0; i < run1.length; i++) {
            assertArrayEquals(run1[i], run2[i], TOL);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Reporting: point counts for nc=2,3,4
    // ─────────────────────────────────────────────────────────────────

    @Test
    void reportPointCounts() throws Exception {
        for (int nc = 2; nc <= 4; nc++) {
            double[][] points = sampleCompositions(nc);
            int interior = countInteriorPoints(points, nc);
            System.out.println(String.format(
                    "nc=%d: total=%d (pure=%d, interior=%d, rest=edges)",
                    nc, points.length, nc, interior));
        }
    }
}
