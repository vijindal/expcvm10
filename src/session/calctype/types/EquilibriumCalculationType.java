package session.calctype.types;

import session.CalculationSession;
import session.calctype.CalculatingType;
import session.calctype.CalculationKind;
import session.calctype.ModelSelection;
import session.calctype.ParameterDescriptor;
import session.calctype.ParameterDescriptor.ValueKind;
import session.calctype.ParameterSpec;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

/** Wraps {@link CalculationSession#calculateEquilibrium}. */
public final class EquilibriumCalculationType
        implements CalculatingType<EquilibriumCalculationType.Params, EquilibriumResult> {

    public record Params(double T, double P, double[] composition) {
    }

    @Override
    public CalculationKind kind() {
        return CalculationKind.EQUILIBRIUM;
    }

    @Override
    public ParameterSpec parameterSpec() {
        return new ParameterSpec(List.of(
                new ParameterDescriptor("T", ValueKind.DOUBLE, "Temperature (K)", "1000.0", true),
                new ParameterDescriptor("P", ValueKind.DOUBLE, "Pressure (Pa)", "101325.0", true),
                new ParameterDescriptor("composition", ValueKind.DOUBLE_ARRAY,
                        "Overall composition (mole fractions)", "", true)
        ));
    }

    @Override
    public EquilibriumResult run(CalculationSession session, ModelSelection model, Params p)
            throws IOException {
        session.setModel(model.tdbFilePath(), model.elements(), model.phases(), model.modelKind());
        session.calculateEquilibrium(p.T(), p.P(), p.composition());
        return session.currentEquilibriumResult();
    }
}
