package system.model.cef;

import system.database.tdb;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a multi-temperature-range SGTE polynomial G(T) and its
 * derivative dG/dT from the subCoeffList data stored in tdb.Exp objects.
 *
 * Each temperature range has 11 coefficients c[0..10] mapping to:
 *   G(T) = c[0]
 *         + c[1]*T
 *         + c[2]*T*ln(T)
 *         + c[3]*T^2
 *         + c[4]*T^3
 *         + c[5]*T^4
 *         + c[6]*T^7
 *         + c[7]*T^(-1)
 *         + c[8]*T^(-2)
 *         + c[9]*T^(-3)
 *         + c[10]*T^(-9)
 *
 * dG/dT = c[1]
 *         + c[2]*(ln(T) + 1)
 *         + 2*c[3]*T
 *         + 3*c[4]*T^2
 *         + 4*c[5]*T^3
 *         + 7*c[6]*T^6
 *         - c[7]*T^(-2)
 *         - 2*c[8]*T^(-3)
 *         - 3*c[9]*T^(-4)
 *         - 9*c[10]*T^(-10)
 */
public class SgtePolynomial {

    /** Number of SGTE coefficient terms per temperature range. */
    public static final int NUM_COEFFS = 11;

    /** Temperature range lower bounds, length = numRanges. */
    private final double[] tLow;

    /** Temperature range upper bounds, length = numRanges. */
    private final double[] tHigh;

    /** SGTE coefficients per range: coeffs[range][0..10]. */
    private final double[][] coeffs;

    /** Number of temperature ranges. */
    private final int numRanges;

    /**
     * Construct from parallel arrays of bounds and coefficients.
     *
     * @param tLow   lower temperature bounds, length n
     * @param tHigh  upper temperature bounds, length n
     * @param coeffs coefficient arrays, coeffs[i] length >= 11, one per range
     */
    public SgtePolynomial(double[] tLow, double[] tHigh, double[][] coeffs) {
        if (tLow.length != tHigh.length || tLow.length != coeffs.length)
            throw new IllegalArgumentException("Mismatched array lengths");
        this.numRanges = tLow.length;
        this.tLow   = tLow.clone();
        this.tHigh  = tHigh.clone();
        this.coeffs = new double[numRanges][NUM_COEFFS];
        for (int i = 0; i < numRanges; i++) {
            double[] src = coeffs[i];
            for (int j = 0; j < NUM_COEFFS; j++)
                this.coeffs[i][j] = (src != null && j < src.length) ? src[j] : 0.0;
        }
    }

    /**
     * Evaluate G(T) using the correct temperature range.
     * If T is outside all ranges, uses the nearest range boundary.
     */
    public double G(double T) {
        double[] c = selectCoeffs(T);
        return evalG(c, T);
    }

    /**
     * Evaluate dG/dT using the correct temperature range.
     */
    public double dGdT(double T) {
        double[] c = selectCoeffs(T);
        return evalDGDT(c, T);
    }

    /**
     * Builds an SgtePolynomial from a list of tdb.Exp objects.
     * Each Exp contributes one temperature range.
     * Uses subCoeffList preferentially, falls back to coeffList.
     * Skips Exp objects with null/empty coefficient lists.
     *
     * @param expList list of Exp objects from tdb.Parameter.getExpList()
     * @return SgtePolynomial, or null if no valid ranges found
     */
    public static SgtePolynomial fromExpList(List<tdb.Exp> expList) {
        if (expList == null || expList.isEmpty()) return null;

        List<double[]> tLowList  = new ArrayList<>();
        List<double[]> tHighList = new ArrayList<>();
        List<double[]> coeffList = new ArrayList<>();

        for (tdb.Exp exp : expList) {
            ArrayList<Double> tempRange = exp.getTempRange();
            if (tempRange == null || tempRange.size() < 2) continue;

            // Prefer subCoeffList (post-substitution), fall back to coeffList
            ArrayList<Double> rawCoeffs = exp.getSubCoeffList();
            if (rawCoeffs == null || rawCoeffs.isEmpty())
                rawCoeffs = exp.getCoeffList();
            if (rawCoeffs == null || rawCoeffs.isEmpty()) continue;

            double[] c = new double[NUM_COEFFS];
            for (int i = 0; i < NUM_COEFFS && i < rawCoeffs.size(); i++)
                c[i] = rawCoeffs.get(i) != null ? rawCoeffs.get(i) : 0.0;

            tLowList.add(new double[]{tempRange.get(0)});
            tHighList.add(new double[]{tempRange.get(1)});
            coeffList.add(c);
        }

        if (tLowList.isEmpty()) return null;

        int n = tLowList.size();
        double[] lo = new double[n];
        double[] hi = new double[n];
        double[][] cs = new double[n][];
        for (int i = 0; i < n; i++) {
            lo[i] = tLowList.get(i)[0];
            hi[i] = tHighList.get(i)[0];
            cs[i] = coeffList.get(i);
        }
        return new SgtePolynomial(lo, hi, cs);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Select coefficients for temperature T. */
    private double[] selectCoeffs(double T) {
        // Find matching range
        for (int i = 0; i < numRanges; i++) {
            if (T >= tLow[i] && T <= tHigh[i])
                return coeffs[i];
        }
        // T below all ranges: use first range
        if (T < tLow[0]) return coeffs[0];
        // T above all ranges: use last range
        return coeffs[numRanges - 1];
    }

    /** Evaluate G(T) from coefficient array. */
    private static double evalG(double[] c, double T) {
        double lnT = Math.log(T);
        return c[0]
             + c[1]  * T
             + c[2]  * T * lnT
             + c[3]  * T * T
             + c[4]  * T * T * T
             + c[5]  * T * T * T * T
             + c[6]  * Math.pow(T, 7)
             + c[7]  / T
             + c[8]  / (T * T)
             + c[9]  / (T * T * T)
             + c[10] / Math.pow(T, 9);
    }

    /** Evaluate dG/dT from coefficient array. */
    private static double evalDGDT(double[] c, double T) {
        double lnT = Math.log(T);
        return c[1]
             + c[2]  * (lnT + 1.0)
             + 2.0 * c[3]  * T
             + 3.0 * c[4]  * T * T
             + 4.0 * c[5]  * T * T * T
             + 7.0 * c[6]  * Math.pow(T, 6)
             - c[7]  / (T * T)
             - 2.0 * c[8]  / (T * T * T)
             - 3.0 * c[9]  / (T * T * T * T)
             - 9.0 * c[10] / Math.pow(T, 10);
    }

    /** Number of temperature ranges. */
    public int numRanges() { return numRanges; }

    /** Lower bound of range i. */
    public double tLow(int i)  { return tLow[i]; }

    /** Upper bound of range i. */
    public double tHigh(int i) { return tHigh[i]; }
}
