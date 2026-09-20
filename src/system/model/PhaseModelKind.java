package system.model;

/**
 * Which Gibbs-energy model implementation to use when building a phase.
 *
 * <p>CVM has no TDB grammar yet, so this enum only governs the TDB-driven
 * factory path; a CVM model is instead built directly via
 * {@link system.model.cvm.CvmPhaseSpec#toModel()}.
 */
public enum PhaseModelKind {

    /** Let the factory choose. Today this always means CEF. */
    AUTO,

    /** Force the Compound Energy Formalism model ({@code CefGibbs}). */
    CEF,

    /**
     * Force CVM via the TDB-driven factory path. NOT IMPLEMENTED there --
     * throws {@link UnsupportedOperationException}.
     */
    CVM
}
