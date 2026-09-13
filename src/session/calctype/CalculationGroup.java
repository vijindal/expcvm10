package session.calctype;

/**
 * The top-level choice every caller (CLI/GUI/API) makes before picking a
 * {@link CalculationKind}: whether it is working from an already-parsed
 * database ({@link #CALCULATE}) or creating one ({@link #ASSESS}).
 *
 * <p>Informally these are "cal" and "opt" respectively; that vocabulary is
 * kept only as a display/input alias in UI code, never as the enum's own
 * identifiers, so it doesn't collide with the separate legacy
 * {@code legacy.calbince} {@code opt}/{@code cal} commands.
 */
public enum CalculationGroup {
    /** Database-consuming calculations (equilibrium, phase diagrams, ...). Requires a Gibbs model via {@code CalculationSession.setModel}. Default. */
    CALCULATE,
    /** Database-creating calculations (thermodynamic assessment / optimization). Never parses a TDB. */
    ASSESS
}
