package session.calctype.types;

import calc.diagram.AxisConfig;
import session.CalculationSession;
import session.calctype.CalculatingType;
import session.calctype.CalculationKind;
import session.calctype.ModelSelection;
import session.calctype.ParameterDescriptor;
import session.calctype.ParameterDescriptor.ValueKind;
import session.calctype.ParameterSpec;
import ui.result.PhaseDiagramResult;

import java.io.IOException;
import java.util.List;

/** Wraps {@link CalculationSession#calculateStep}. */
public final class StepCalculationType
        implements CalculatingType<StepCalculationType.Params, PhaseDiagramResult> {

    public record Params(AxisConfig axis, double fixedT, double fixedP, double[] composition) {
    }

    @Override
    public CalculationKind kind() {
        return CalculationKind.STEP;
    }

    @Override
    public ParameterSpec parameterSpec() {
        return new ParameterSpec(List.of(
                new ParameterDescriptor("axis", ValueKind.AXIS_CONFIG, "Axis to walk", "", true),
                new ParameterDescriptor("fixedT", ValueKind.DOUBLE, "Temperature (K)", "1000.0", true),
                new ParameterDescriptor("fixedP", ValueKind.DOUBLE, "Pressure (Pa)", "101325.0", true),
                new ParameterDescriptor("composition", ValueKind.DOUBLE_ARRAY,
                        "Overall composition (mole fractions)", "", true)
        ));
    }

    @Override
    public PhaseDiagramResult run(CalculationSession session, ModelSelection model, Params p)
            throws IOException {
        session.setModel(model.tdbFilePath(), model.elements(), model.phases(), model.modelKind());
        session.calculateStep(p.axis(), p.fixedT(), p.fixedP(), p.composition());
        return session.currentStepResult();
    }
}
