package system.database;

import system.model.unary.ElementGibbs;
import system.model.unary.SgteElementGibbs;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts common unary Gibbs-energy references from a selected phase's zeroth-order
 * parameters ({@code PARAMETER G(phase, element:..., ;0)}), for use by both CEF and CVM models.
 *
 * <p><b>Design principle:</b> Both CEF and CVM must use the SAME selected-phase unary
 * reference functions for thermodynamic consistency:
 *
 * <pre>
 *   G_CEF  = G_ref(selected_phase, T, x) + G_mix_CEF(...)
 *   G_CVM  = G_ref(selected_phase, T, x) + G_mix_CVM(...)
 * </pre>
 *
 * where {@code G_ref} is the common unary Gibbs contribution
 * {@code Σ_i x_i * G_i(selected_phase, T)}.
 *
 * <p>This class extracts the selected phase's unary {@code G(...)  ;0)} definitions
 * directly, eliminating redundant GHSER reference detection and ensuring both models
 * use identical unary functions.
 *
 * <p>Earlier versions:
 * <ul>
 *   <li>CEF: obtained unary G from {@code PARAMETER G(selected_phase, ...;0)}</li>
 *   <li>CVM: independently selected a GHSER reference phase (often different)</li>
 * </ul>
 *
 * This class unifies the path: both now use the selected phase's own unary definitions.
 */
public final class PhaseUnaryGibbsExtractor {

    private PhaseUnaryGibbsExtractor() {}

    /**
     * Builds unary Gibbs-energy evaluators from a selected phase's zeroth-order G parameters.
     *
     * <p>For each requested element, locates {@code PARAMETER G(selectedPhase, element:..., ;0)}
     * and converts it to an {@link ElementGibbs} evaluator that can retrieve G values
     * for the selected phase.
     *
     * <p>Both CEF and CVM construction should use this method to obtain unary references.
     *
     * @param database       loaded TDB database
     * @param elements       ordered list of element symbols (case-insensitive, e.g., "V", "ZR")
     * @param selectedPhase  the phase name whose unary parameters to extract (e.g., "BCC_A2")
     * @return               ElementGibbs[] for the selected phase, indexed by element order,
     *                       where each ElementGibbs encapsulates G(selectedPhase, element, T)
     * @throws IllegalArgumentException if any element lacks a zeroth-order G parameter
     *                                  in the selected phase
     */
    public static ElementGibbs[] buildPhaseUnaryGibbs(
            tdb database,
            List<String> elements,
            String selectedPhase) {

        if (database == null)
            throw new NullPointerException("database must not be null");
        if (elements == null)
            throw new NullPointerException("elements must not be null");
        if (selectedPhase == null || selectedPhase.isBlank())
            throw new IllegalArgumentException("selectedPhase must not be null/blank");

        ElementGibbs[] result = new ElementGibbs[elements.size()];

        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            // Reuse UnaryGibbsBuilder to load all phases for the element,
            // but specify selectedPhase as the GHSER reference instead of auto-detecting
            result[i] = buildElementPhaseGibbs(database, element, selectedPhase);
        }

        return result;
    }

    /**
     * Builds a single element's unary Gibbs evaluator from the selected phase's G parameter.
     *
     * <p>Uses UnaryGibbsBuilder to extract all available phases, then selects the requested
     * phase as the reference (instead of GHSER auto-detection).
     *
     * @param database       loaded TDB database
     * @param elementSymbol  element name (case-insensitive)
     * @param selectedPhase  phase name (e.g., "BCC_A2")
     * @return               ElementGibbs for this element in the selected phase
     * @throws IllegalArgumentException if G parameter not found for the element in the phase
     */
    private static ElementGibbs buildElementPhaseGibbs(
            tdb database,
            String elementSymbol,
            String selectedPhase) {

        // Build using UnaryGibbsBuilder to load all phases (reuse existing parsing logic)
        SgteElementGibbs unaryForElement = UnaryGibbsBuilder.build(elementSymbol, database);

        // Verify selected phase is available
        String phaseUpper = selectedPhase.toUpperCase();
        if (!unaryForElement.availablePhases().contains(phaseUpper)) {
            throw new IllegalArgumentException(
                    "Selected phase '" + selectedPhase + "' has no G data for element '"
                    + elementSymbol + "'. Available: " + unaryForElement.availablePhases());
        }

        // Return a wrapper that always returns G for the selected phase
        // (not the GHSER reference that UnaryGibbsBuilder chose)
        return new PhaseSpecificGibbs(unaryForElement, selectedPhase);
    }

    /**
     * Wraps a full-featured ElementGibbs to return only G for a specific phase,
     * while maintaining the ElementGibbs interface.
     */
    private static final class PhaseSpecificGibbs implements ElementGibbs {

        private final SgteElementGibbs delegate;
        private final String fixedPhaseName;

        PhaseSpecificGibbs(SgteElementGibbs delegate, String fixedPhaseName) {
            this.delegate = delegate;
            this.fixedPhaseName = fixedPhaseName.toUpperCase();
        }

        @Override
        public String elementSymbol() {
            return delegate.elementSymbol();
        }

        @Override
        public double gibbs(String phaseName, double T) {
            // Always return G for the fixed phase, ignore phaseName argument
            return delegate.gibbs(fixedPhaseName, T);
        }

        @Override
        public double ghser(double T) {
            // Return G for the fixed phase (not the GHSER reference)
            return delegate.gibbs(fixedPhaseName, T);
        }

        @Override
        public java.util.Set<String> availablePhases() {
            return java.util.Collections.singleton(fixedPhaseName);
        }
    }
}
