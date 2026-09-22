package application.calctype;

/**
 * Every calculation a caller can run through {@link CalculationInterface},
 * each tied to exactly one {@link CalculationGroup} so the two-level choice
 * (group, then kind) can never desync.
 */
public enum CalculationKind {
    EQUILIBRIUM(CalculationGroup.CALCULATE),
    INITIAL_STATE(CalculationGroup.CALCULATE),
    STEP(CalculationGroup.CALCULATE),
    COARSE_BINARY(CalculationGroup.CALCULATE),
    COARSE_TERNARY(CalculationGroup.CALCULATE),
    PHASE_DIAGRAM(CalculationGroup.CALCULATE),
    /** The lone {@link CalculationGroup#ASSESS} kind; not implemented yet. */
    ASSESSMENT(CalculationGroup.ASSESS);

    private final CalculationGroup group;

    CalculationKind(CalculationGroup group) {
        this.group = group;
    }

    public CalculationGroup group() {
        return group;
    }
}
