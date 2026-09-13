package session.calctype.types;

import calc.diagram.AxisConfig;
import calc.diagram.PhaseDiagram;
import session.CalculationSession;
import session.calctype.CalculatingType;
import session.calctype.CalculationKind;
import session.calctype.ModelSelection;
import session.calctype.ParameterDescriptor;
import session.calctype.ParameterDescriptor.ValueKind;
import session.calctype.ParameterSpec;

import java.io.IOException;
import java.util.List;

/**
 * Wraps {@link CalculationSession#calculatePhaseDiagram}, which still always
 * throws {@link UnsupportedOperationException} today (pending a y-facing
 * tracer) -- unchanged behavior, just reached through this common interface.
 */
public final class PhaseDiagramCalculationType
        implements CalculatingType<PhaseDiagramCalculationType.Params, PhaseDiagram> {

    public record Params(AxisConfig[] axes, double[] startAxes, double fixedT, double fixedP,
                          double[] composition) {
    }

    @Override
    public CalculationKind kind() {
        return CalculationKind.PHASE_DIAGRAM;
    }

    @Override
    public ParameterSpec parameterSpec() {
        return new ParameterSpec(List.of(
                new ParameterDescriptor("axes", ValueKind.AXIS_CONFIG, "Axes to trace", "", true),
                new ParameterDescriptor("startAxes", ValueKind.DOUBLE_ARRAY, "Starting axis values", "", true),
                new ParameterDescriptor("fixedT", ValueKind.DOUBLE, "Temperature (K)", "1000.0", true),
                new ParameterDescriptor("fixedP", ValueKind.DOUBLE, "Pressure (Pa)", "101325.0", true),
                new ParameterDescriptor("composition", ValueKind.DOUBLE_ARRAY,
                        "Overall composition (mole fractions)", "", true)
        ));
    }

    @Override
    public PhaseDiagram run(CalculationSession session, ModelSelection model, Params p)
            throws IOException {
        session.setModel(model.tdbFilePath(), model.elements(), model.phases(), model.modelKind());
        session.calculatePhaseDiagram(p.axes(), p.startAxes(), p.fixedT(), p.fixedP(), p.composition());
        return session.currentPhaseDiagram();
    }
}
