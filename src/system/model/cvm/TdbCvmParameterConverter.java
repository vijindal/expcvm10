package system.model.cvm;

import system.database.tdb;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts parsed TDB G_CVM parameters into {@link CecTerm} objects.
 *
 * <p>Bridge layer between TDB parsing and CVM model construction:
 * <ul>
 *   <li>Accepts a list of {@code tdb.Parameter} objects with type "G_CVM"</li>
 *   <li>Extracts linear-in-T coefficients (a, b) via {@code tdb.extractCvmLinearCoefficients}</li>
 *   <li>Constructs {@code CecTerm(parameterId, a, b)} in parameter order</li>
 * </ul>
 *
 * <p>Rejects malformed or missing parameters; does not silently skip errors.
 */
public final class TdbCvmParameterConverter {

    private TdbCvmParameterConverter() {}

    /**
     * Converts a list of G_CVM parameters to CecTerms.
     *
     * @param tdbRef          the loaded TDB database (for coefficient extraction)
     * @param cvmParameters   G_CVM parameters from {@code tdbRef.getCvmParams(...)},
     *                        in TDB order
     * @return                CecTerms with names from {@code parameterId} and
     *                        coefficients from linear-T expressions, in same order
     * @throws IllegalArgumentException if any parameter lacks a parameterId or
     *                        has a malformed/non-linear expression
     * @throws NullPointerException if any argument is null
     */
    public static List<CecTerm> toCecTerms(tdb tdbRef, List<tdb.Parameter> cvmParameters) {
        if (tdbRef == null)
            throw new NullPointerException("tdbRef must not be null");
        if (cvmParameters == null)
            throw new NullPointerException("cvmParameters must not be null");

        List<CecTerm> cecTerms = new ArrayList<>();

        for (int i = 0; i < cvmParameters.size(); i++) {
            tdb.Parameter param = cvmParameters.get(i);

            // Require symbolic parameterId
            String id = param.getParameterId();
            if (id == null || id.isBlank())
                throw new IllegalArgumentException(
                        "G_CVM parameter " + i + " has missing or blank parameterId");

            // Extract linear coefficients (a, b)
            double[] coeffs;
            try {
                coeffs = tdbRef.extractCvmLinearCoefficients(param);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "G_CVM parameter '" + id + "' at index " + i + ": " + e.getMessage(), e);
            }

            if (coeffs == null || coeffs.length != 2)
                throw new IllegalArgumentException(
                        "G_CVM parameter '" + id + "' at index " + i
                        + " failed to extract [a, b] coefficients");

            double a = coeffs[0];
            double b = coeffs[1];

            cecTerms.add(new CecTerm(id, a, b));
        }

        return cecTerms;
    }
}
