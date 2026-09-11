package test;

import calc.equil.Halton;

/**
 * Verifies the {@link Halton} port against reference values generated
 * directly from pycalphad 0.11.1's {@code pycalphad.core.halton.halton}
 * and {@code pycalphad.core.utils.point_sample}.
 *
 * <p>Reference 1 -- scrambled Halton sequence, dim=4, nbpts=5:
 *
 * <pre>
 * from pycalphad.core.halton import halton
 * halton(4, 5, scramble=True)
 * </pre>
 *
 * <pre>
 * [[0.5           0.6666666667  0.4           0.7142857143]
 *  [0.25          0.3333333333  0.8           0.4285714286]
 *  [0.75          0.2222222222  0.2           0.1428571429]
 *  [0.125         0.8888888889  0.6           0.8571428571]
 *  [0.625         0.5555555556  0.08          0.5714285714]]
 * </pre>
 *
 * <p>Reference 2 -- point_sample for two 2-constituent sublattices
 * (matching V2ZR's (V,Zr)(V,Zr) structure), pdof=3:
 *
 * <pre>
 * from pycalphad.core.utils import point_sample
 * point_sample([2,2], pdof=3)
 * </pre>
 *
 * <pre>
 * [[0.6309297536 0.3690702464 0.7314158823 0.2685841177]
 *  [0.5578858913 0.4421141087 0.2084593784 0.7915406216]
 *  [0.1605584217 0.8394415783 0.4526808303 0.5473191697]
 *  [0.9463946304 0.0536053696 0.768186206  0.231813794 ]
 *  [0.4443259045 0.5556740955 0.8186212916 0.1813787084]
 *  [0.3086257519 0.6913742481 0.3694353954 0.6305646046]]
 * </pre>
 *
 * <p>This is a standalone {@code main}-based check (this project's other
 * "tests" follow the same convention -- see e.g. CalculationSessionCalGTest)
 * rather than a JUnit test, since the project has no test-runner
 * dependency wired in yet.
 */
public class HaltonPortTest {

    private static final double[][] EXPECTED_HALTON = {
        {0.5,    0.6666666667, 0.4,   0.7142857143},
        {0.25,   0.3333333333, 0.8,   0.4285714286},
        {0.75,   0.2222222222, 0.2,   0.1428571429},
        {0.125,  0.8888888889, 0.6,   0.8571428571},
        {0.625,  0.5555555556, 0.08,  0.5714285714},
    };

    private static final double[][] EXPECTED_POINT_SAMPLE = {
        {0.6309297536, 0.3690702464, 0.7314158823, 0.2685841177},
        {0.5578858913, 0.4421141087, 0.2084593784, 0.7915406216},
        {0.1605584217, 0.8394415783, 0.4526808303, 0.5473191697},
        {0.9463946304, 0.0536053696, 0.768186206,  0.231813794},
        {0.4443259045, 0.5556740955, 0.8186212916, 0.1813787084},
        {0.3086257519, 0.6913742481, 0.3694353954, 0.6305646046},
    };

    private static final double TOL = 1.0e-9;

    public static void main(String[] args) {

        checkMatrix("halton(4,5)", Halton.generate(4, 5), EXPECTED_HALTON);

        checkMatrix("point_sample([2,2], pdof=3)",
                Halton.pointSample(new int[]{2, 2}, 3),
                EXPECTED_POINT_SAMPLE);

        System.out.println("HaltonPortTest PASSED.");
    }

    private static void checkMatrix(String label,
                                     double[][] actual,
                                     double[][] expected) {

        if (actual.length != expected.length) {
            throw new AssertionError(label + ": row count mismatch: "
                    + "expected " + expected.length
                    + " got " + actual.length);
        }

        double maxAbsDiff = 0.0;

        for (int r = 0; r < expected.length; r++) {

            if (actual[r].length != expected[r].length) {
                throw new AssertionError(label
                        + ": column count mismatch at row " + r);
            }

            for (int c = 0; c < expected[r].length; c++) {

                double diff = Math.abs(actual[r][c] - expected[r][c]);
                maxAbsDiff = Math.max(maxAbsDiff, diff);

                if (diff > TOL) {
                    throw new AssertionError(String.format(
                            "%s mismatch at [%d][%d]: expected %.10f"
                            + " got %.10f (diff %.3e)",
                            label, r, c, expected[r][c], actual[r][c], diff));
                }
            }
        }

        System.out.println(label + ": max abs diff vs pycalphad reference = "
                + maxAbsDiff);
    }
}
