package system.model;

/**
 * Which Gibbs-energy model implementation to use when building a phase.
 *
 * <p>The Compound Energy Formalism ({@code system.model.cef.CefGibbs})
 * is the general model: a one-sublattice substitutional (Redlich-Kister)
 * phase is just its 1-sublattice special case, so CEF covers everything a
 * dedicated RK model did. The Cluster Variation Method
 * ({@code system.model.cvm.CvmGibbs}) is a separate model for phases with
 * short-range order; a standalone {@code CvmGibbs} evaluator exists but
 * is not yet wired into {@code PhaseModelFactory}.
 */
public enum PhaseModelKind {

    /**
     * Let the factory choose. Today this always means CEF. When CVM is
     * implemented, AUTO will consult a TDB model hint (e.g. a
     * {@code TYPE_DEFINITION} / {@code MODEL} record) to pick CVM for the
     * phases that declare it, and CEF for the rest.
     */
    AUTO,

    /** Force the Compound Energy Formalism model ({@code CefGibbs}). */
    CEF,

    /**
     * Force the Cluster Variation Method model. NOT IMPLEMENTED --
     * requesting this currently throws
     * {@link UnsupportedOperationException}.
     */
    CVM
}
