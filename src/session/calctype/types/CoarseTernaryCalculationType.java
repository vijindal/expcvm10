package session.calctype.types;

import calc.diagram.AxisConfig;
import session.CalculationSession;
import session.calctype.CalculatingType;
import session.calctype.CalculationKind;
import session.calctype.ModelSelection;
import session.calctype.ParameterDescriptor;
import session.calctype.ParameterDescriptor.ValueKind;
import session.calctype.ParameterSpec;
import ui.result.CoarseDiagramResult;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/** Wraps {@link CalculationSession#calculateCoarseTernaryDiagram}. */
public final class CoarseTernaryCalculationType
        implements CalculatingType<CoarseTernaryCalculationType.Params, CoarseDiagramResult> {

    public record Params(AxisConfig axisCompI, AxisConfig axisCompJ, double fixedT, double fixedP,
                          double[] composition, Consumer<String> onProgress) {

        public Params(AxisConfig axisCompI, AxisConfig axisCompJ, double fixedT, double fixedP,
                       double[] composition) {
            this(axisCompI, axisCompJ, fixedT, fixedP, composition, null);
        }
    }

    @Override
    public CalculationKind kind() {
        return CalculationKind.COARSE_TERNARY;
    }

    @Override
    public ParameterSpec parameterSpec() {
        return new ParameterSpec(List.of(
                new ParameterDescriptor("axisCompI", ValueKind.AXIS_CONFIG, "Composition axis I", "", true),
                new ParameterDescriptor("axisCompJ", ValueKind.AXIS_CONFIG, "Composition axis J", "", true),
                new ParameterDescriptor("fixedT", ValueKind.DOUBLE, "Temperature (K)", "1000.0", true),
                new ParameterDescriptor("fixedP", ValueKind.DOUBLE, "Pressure (Pa)", "101325.0", true),
                new ParameterDescriptor("composition", ValueKind.DOUBLE_ARRAY,
                        "Overall composition (mole fractions)", "", true)
        ));
    }

    @Override
    public CoarseDiagramResult run(CalculationSession session, ModelSelection model, Params p)
            throws IOException {
        session.setModel(model.tdbFilePath(), model.elements(), model.phases(), model.modelKind());
        session.calculateCoarseTernaryDiagram(p.axisCompI(), p.axisCompJ(), p.fixedT(), p.fixedP(),
                p.composition(), p.onProgress());
        return session.currentCoarseDiagramResult();
    }
}
