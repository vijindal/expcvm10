package system.model;

/**
 * Generates sample points in a Gibbs energy model's internal-variable space.
 *
 * <p>Each model owns its own internal-variable representation and knows how to
 * sample over its valid/useful domain. This abstraction allows the global
 * initialization algorithm (GridMinimizer) to remain model-agnostic: it
 * requests samples from each model and evaluates them, without knowledge of
 * CEF sublattices, CVM correlation functions, or any model-specific structure.
 *
 * <p>CEF models sample site-fraction space via endmembers, edges, and Halton
 * interior points. CVM models may sample differently (or not at all for now).
 * A future model provides its own sampling strategy.
 */
public interface InternalStateSampler {

    /**
     * Generate sample points across this model's internal-variable space.
     *
     * <p>The meaning of {@code density} and {@code edgePoints} is model-specific:
     * <ul>
     *   <li>CEF: {@code density} is the Halton point count per degree of freedom
     *       (PDENS in pycalphad); {@code edgePoints} is the linear grid count
     *       per endmember-pair edge.</li>
     *   <li>CVM (if supported): model-specific interpretation.</li>
     * </ul>
     *
     * @param density     model-specific sample density (e.g., pycalphad's PDENS)
     * @param edgePoints  model-specific edge sampling (e.g., pycalphad's fixed-grid count)
     * @return array of internal-variable vectors, each valid for the owning model's
     *         {@code isValid(y)} check
     * @throws UnsupportedOperationException if sampling is not yet implemented
     *         for this model type (e.g., CVM)
     */
    double[][] sample(int density, int edgePoints);
}
