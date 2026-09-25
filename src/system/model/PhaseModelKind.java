package system.model;

/**
 * Which Gibbs-energy model implementation to use when building a phase.
 */
public enum PhaseModelKind {

    /** Selects CEF if available, else CVM; CEF wins if both are available. */
    AUTO,

    /** Force the Compound Energy Formalism model ({@code CefGibbs}). */
    CEF,

    /** Force the Cluster Variation Method model ({@code CvmGibbsModel}). */
    CVM
}
