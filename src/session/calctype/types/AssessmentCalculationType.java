package session.calctype.types;

import session.calctype.AssessingType;
import session.calctype.CalculationKind;
import session.calctype.CalculationOutcome;
import session.calctype.ParameterSpec;

import java.util.List;

/**
 * The lone {@link session.calctype.CalculationGroup#ASSESS} calculation
 * type ("opt" -- thermodynamic assessment / database creation). Not
 * implemented yet; always reports {@link CalculationOutcome.NotImplemented}
 * so every caller (CLI/GUI/API) renders the same message.
 *
 * <p>Structurally has no access to a {@code CalculationSession} (see {@link
 * AssessingType}), so it cannot parse a TDB even by mistake.
 */
public final class AssessmentCalculationType
        implements AssessingType<Void, CalculationOutcome.NotImplemented<Void>> {

    @Override
    public CalculationKind kind() {
        return CalculationKind.ASSESSMENT;
    }

    @Override
    public ParameterSpec parameterSpec() {
        return new ParameterSpec(List.of());
    }

    @Override
    public CalculationOutcome.NotImplemented<Void> run(Void params) {
        return new CalculationOutcome.NotImplemented<>(
                "Thermodynamic assessment (opt) is not implemented yet in the "
                + "CalculationSession architecture. Use the legacy opt command for now.");
    }
}
