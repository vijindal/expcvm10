package system.model.unary;

import java.util.List;

/**
 * Evaluates the standard SGTE 11-term polynomial for a single temperature range.
 *
 * <h2>Polynomial form</h2>
 * <pre>
 *   G(T) = c[0]
 *        + c[1]  · T
 *        + c[2]  · T · ln(T)
 *        + c[3]  · T²
 *        + c[4]  · T³
 *        + c[5]  · T⁴
 *        + c[6]  · T⁷
 *        + c[7]  · T⁻¹
 *        + c[8]  · T⁻²
 *        + c[9]  · T⁻³
 *        + c[10] · T⁻⁹
 * </pre>
 *
 * <h2>Coefficient index mapping</h2>
 * This matches exactly the {@code subCoeffList} produced by {@code tdb.readTerms()}:
 * <pre>
 *   index  term             tdb source string
 *   -----  ---------------  -----------------
 *     0    constant         (remaining after all terms stripped)
 *     1    *T               *T
 *     2    *T*LOG(T)        *T*LOG(T)
 *     3    *T**2            *T**2
 *     4    *T**3            *T**3
 *     5    *T**4            *T**4
 *     6    *T**7            *T**7
 *     7    *T**(-1)         *T**(-1)
 *     8    *T**(-2)         *T**(-2)
 *     9    *T**(-3)         *T**(-3)
 *    10    *T**(-9)         *T**(-9)
 * </pre>
 *
 * <p>The {@code tdb} parser stores a 12th term (*T**(-11)) at index 11 in some versions,
 * but it is zero for all SGTE unary data in v4.4 and is not evaluated here.
 */
public final class SgtePolynomial {

    /** Number of coefficients this evaluator handles (indices 0–10). */
    public static final int NUM_COEFFS = 11;

    private SgtePolynomial() {}   // utility class, no instances

    // ------------------------------------------------------------------
    // Primary evaluation entry point
    // ------------------------------------------------------------------

    /**
     * Evaluates the SGTE polynomial for a given temperature using
     * a {@code List<Double>} coefficient list as returned by {@code Exp.getSubCoeffList()}.
     *
     * @param coeffs  list of at least {@value #NUM_COEFFS} coefficients (indices 0–10)
     * @param T       temperature in Kelvin (must be > 0)
     * @return        G(T) in J/mol
     * @throws IllegalArgumentException if {@code coeffs} has fewer than 11 elements
     *                                  or {@code T} is non-positive
     */
    public static double evaluate(List<Double> coeffs, double T) {
        if (coeffs == null || coeffs.size() < NUM_COEFFS) {
            throw new IllegalArgumentException(
                    "subCoeffList must have at least " + NUM_COEFFS + " elements, got "
                    + (coeffs == null ? "null" : coeffs.size()));
        }
        if (T <= 0) {
            throw new IllegalArgumentException("Temperature must be positive, got T=" + T);
        }
        return evalCore(
                coeffs.get(0), coeffs.get(1), coeffs.get(2),
                coeffs.get(3), coeffs.get(4), coeffs.get(5),
                coeffs.get(6), coeffs.get(7), coeffs.get(8),
                coeffs.get(9), coeffs.get(10),
                T);
    }

    /**
     * Evaluates using a raw {@code double[]} array (avoids autoboxing overhead
     * for performance-critical inner loops).
     *
     * @param coeffs  array of at least {@value #NUM_COEFFS} coefficients
     * @param T       temperature in Kelvin
     * @return        G(T) in J/mol
     */
    public static double evaluate(double[] coeffs, double T) {
        if (coeffs == null || coeffs.length < NUM_COEFFS) {
            throw new IllegalArgumentException(
                    "coefficient array must have at least " + NUM_COEFFS + " elements");
        }
        if (T <= 0) {
            throw new IllegalArgumentException("Temperature must be positive, got T=" + T);
        }
        return evalCore(coeffs[0], coeffs[1], coeffs[2],
                        coeffs[3], coeffs[4], coeffs[5],
                        coeffs[6], coeffs[7], coeffs[8],
                        coeffs[9], coeffs[10], T);
    }

    // ------------------------------------------------------------------
    // Core computation (all paths converge here)
    // ------------------------------------------------------------------

    private static double evalCore(
            double c0, double c1, double c2,
            double c3, double c4, double c5,
            double c6, double c7, double c8,
            double c9, double c10,
            double T) {

        double T2 = T  * T;
        double T3 = T2 * T;

        double result = c0;
        if (c1  != 0) result += c1  * T;
        if (c2  != 0) result += c2  * T * Math.log(T);
        if (c3  != 0) result += c3  * T2;
        if (c4  != 0) result += c4  * T3;
        if (c5  != 0) result += c5  * T3 * T;                // T⁴
        if (c6  != 0) result += c6  * T3 * T3 * T;           // T⁷
        if (c7  != 0) result += c7  / T;                      // T⁻¹
        if (c8  != 0) result += c8  / T2;                     // T⁻²
        if (c9  != 0) result += c9  / T3;                     // T⁻³
        if (c10 != 0) result += c10 / (T3 * T3 * T3);        // T⁻⁹

        return result;
    }

    // ------------------------------------------------------------------
    // Helper: extract subCoeffList as a double[] for hot paths
    // ------------------------------------------------------------------

    /**
     * Converts a {@code List<Double>} subCoeffList to a {@code double[]} for
     * repeated evaluation without repeated unboxing.
     *
     * @param coeffs  list from {@code Exp.getSubCoeffList()}
     * @return        primitive double array of length {@link #NUM_COEFFS}
     */
    public static double[] toArray(List<Double> coeffs) {
        double[] arr = new double[NUM_COEFFS];
        for (int i = 0; i < NUM_COEFFS; i++) {
            arr[i] = (i < coeffs.size()) ? coeffs.get(i) : 0.0;
        }
        return arr;
    }
}
