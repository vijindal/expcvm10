package system.model.cvm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a named set of {@link CecTerm}s into the flat {@code cec[]}
 * array that {@link CvmGibbs} expects, in exactly the order of
 * {@link CvmPhaseData#eListNames}.
 *
 * <p>Adapted from CEWorkbench's {@code CECEvaluator} concept: match each
 * term's name against the phase data's correlation-function names, evaluate
 * {@code J(T) = a + b*T} at the requested temperature, and place it at the
 * matching index. Unmatched names on either side are treated as an error
 * rather than silently defaulting to zero or falling back to positional
 * order -- a silent fallback here would put a coefficient on the wrong
 * correlation function with no error signal (see the project's
 * "V-Zr candidate completeness" lesson: truncated/misaligned inputs give a
 * self-consistent but wrong answer).
 */
public final class CecEvaluator {

    private final CvmPhaseData data;
    private final Map<String, CecTerm> byName;

    /**
     * @param data  phase data supplying {@link CvmPhaseData#eListNames}
     * @param terms CEC terms; every name in {@code data.eListNames} must be
     *              present exactly once, and every term name must match one
     *              of {@code data.eListNames} (no extras, no duplicates)
     */
    public CecEvaluator(CvmPhaseData data, List<CecTerm> terms) {
        this.data = data;
        this.byName = new LinkedHashMap<>();
        for (CecTerm t : terms) {
            if (byName.put(t.name, t) != null) {
                throw new IllegalArgumentException("Duplicate CEC term name: " + t.name);
            }
        }
        checkCoverage();
    }

    private void checkCoverage() {
        for (String expected : data.eListNames) {
            if (!byName.containsKey(expected)) {
                throw new IllegalArgumentException(
                        "Missing CEC term for correlation function '" + expected
                        + "'; expected names " + java.util.Arrays.toString(data.eListNames));
            }
        }
        for (String provided : byName.keySet()) {
            boolean known = false;
            for (String expected : data.eListNames) {
                if (expected.equals(provided)) { known = true; break; }
            }
            if (!known) {
                throw new IllegalArgumentException(
                        "CEC term '" + provided + "' does not match any correlation function name; "
                        + "expected one of " + java.util.Arrays.toString(data.eListNames));
            }
        }
    }

    /**
     * Evaluates cec[i] = J_i(T) for i = 0..ncf-1, in the exact order of
     * {@link CvmPhaseData#eListNames} (the order {@link CvmGibbs} requires).
     */
    public double[] evaluate(double T) {
        double[] cec = new double[data.ncf];
        for (int i = 0; i < data.ncf; i++) {
            cec[i] = byName.get(data.eListNames[i]).value(T);
        }
        return cec;
    }

    /**
     * dcec[i]/dT for i = 0..ncf-1, same order as {@link #evaluate(double)}.
     * Exact since each term is affine in T.
     */
    public double[] evaluateDT() {
        double[] dcec = new double[data.ncf];
        for (int i = 0; i < data.ncf; i++) {
            dcec[i] = byName.get(data.eListNames[i]).dValueDT();
        }
        return dcec;
    }

    public CvmPhaseData data() { return data; }
}
