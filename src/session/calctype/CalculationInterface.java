package session.calctype;

import calc.diagram.AxisConfig;
import session.CalculationSession;
import system.ports.EquilibriumResult;
import ui.result.CoarseDiagramResult;
import calc.diagram.PhaseDiagramResult;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * The single point of contact between UI code (CLI/GUI/API) and {@link
 * CalculationSession} for running a calculation -- no UI calls {@code
 * CalculationSession.setModel}/{@code calculate*} directly.
 *
 * <p>{@link #runCalculating} handles every {@link CalculationGroup#CALCULATE}
 * ("cal") kind: it calls {@code session.setModel(...)} with the given {@link
 * ModelSelection}, dispatches into the matching {@code CalculationSession
 * .calculate*} method, and returns the result directly to the caller (read
 * back from the matching {@code current*()} accessor) -- callers get their
 * result as an ordinary return value, the same way as before this class
 * existed.
 *
 * <p>{@link #runAssessing} handles every {@link CalculationGroup#ASSESS}
 * ("opt") kind -- today just {@link CalculationKind#ASSESSMENT}, always
 * "not implemented yet." Unlike the earlier two-interface design (a sealed
 * {@code CalculatingType}/{@code AssessingType} pair where an ASSESS-group
 * implementation had no {@code CalculationSession} parameter in scope at
 * all, so it could not reach {@code setModel} even by a coding mistake),
 * this single class only keeps that guarantee by inspection: {@link
 * #runAssessing} simply never has a {@code CalculationSession} parameter to
 * pass along, so nothing here calls {@code setModel}, but that is no longer
 * compiler-enforced. Acceptable for now -- assessment itself is revisited
 * later.
 */
public final class CalculationInterface {

    private CalculationInterface() {
    }

    // ────────────────────────────────────────────────────────────────
    // Params -- one record per CalculationKind, carrying its typed inputs
    // ────────────────────────────────────────────────────────────────

    public record EquilibriumParams(double T, double P, double[] composition) {
    }

    public record InitialStateParams(double T, double P, double[] composition) {
    }

    public record StepParams(AxisConfig axis, double fixedT, double fixedP, double[] composition) {
    }

    public record CoarseBinaryParams(AxisConfig axisX, AxisConfig axisY, double fixedT, double fixedP,
                                      double[] composition, Consumer<String> onProgress) {

        public CoarseBinaryParams(AxisConfig axisX, AxisConfig axisY, double fixedT, double fixedP,
                                   double[] composition) {
            this(axisX, axisY, fixedT, fixedP, composition, null);
        }
    }

    public record CoarseTernaryParams(AxisConfig axisCompI, AxisConfig axisCompJ, double fixedT,
                                       double fixedP, double[] composition, Consumer<String> onProgress) {

        public CoarseTernaryParams(AxisConfig axisCompI, AxisConfig axisCompJ, double fixedT,
                                    double fixedP, double[] composition) {
            this(axisCompI, axisCompJ, fixedT, fixedP, composition, null);
        }
    }

    public record PhaseDiagramParams(AxisConfig[] axes, double[] startAxes, double fixedT,
                                      double fixedP, double[] composition) {
    }

    // ────────────────────────────────────────────────────────────────
    // CALCULATE group ("cal")
    // ────────────────────────────────────────────────────────────────

    /**
     * Runs a {@link CalculationGroup#CALCULATE} calculation: calls {@code
     * session.setModel(...)} with {@code model}, then dispatches {@code
     * params} into the {@code CalculationSession.calculate*} method matching
     * {@code kind}, returning the result read back from the matching {@code
     * current*()} accessor.
     *
     * @throws IllegalArgumentException if {@code kind} is not a CALCULATE-group
     *         kind, or {@code model} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <P, R> R runCalculating(CalculationSession session, CalculationKind kind,
                                           ModelSelection model, P params) throws IOException {
        if (kind.group() != CalculationGroup.CALCULATE) {
            throw new IllegalArgumentException(kind + " is not a CALCULATE-group calculation type");
        }
        if (model == null) {
            throw new IllegalArgumentException(kind + " requires a ModelSelection (setModel inputs)");
        }

        session.setModel(model.tdbFilePath(), model.elements(), model.phases(), model.modelKind());

        switch (kind) {
            case EQUILIBRIUM: {
                EquilibriumParams p = (EquilibriumParams) params;
                session.calculateEquilibrium(p.T(), p.P(), p.composition());
                return (R) session.currentEquilibriumResult();
            }
            case INITIAL_STATE: {
                InitialStateParams p = (InitialStateParams) params;
                session.calculateInitialState(p.T(), p.P(), p.composition());
                return (R) session.currentInitialState();
            }
            case STEP: {
                StepParams p = (StepParams) params;
                session.calculateStep(p.axis(), p.fixedT(), p.fixedP(), p.composition());
                return (R) session.currentStepResult();
            }
            case COARSE_BINARY: {
                CoarseBinaryParams p = (CoarseBinaryParams) params;
                session.calculateCoarseBinaryDiagram(p.axisX(), p.axisY(), p.fixedT(), p.fixedP(),
                        p.composition(), p.onProgress());
                return (R) session.currentCoarseDiagramResult();
            }
            case COARSE_TERNARY: {
                CoarseTernaryParams p = (CoarseTernaryParams) params;
                session.calculateCoarseTernaryDiagram(p.axisCompI(), p.axisCompJ(), p.fixedT(),
                        p.fixedP(), p.composition(), p.onProgress());
                return (R) session.currentCoarseDiagramResult();
            }
            case PHASE_DIAGRAM: {
                PhaseDiagramParams p = (PhaseDiagramParams) params;
                session.calculatePhaseDiagram(p.axes(), p.startAxes(), p.fixedT(), p.fixedP(),
                        p.composition());
                return (R) session.currentPhaseDiagram();
            }
            default:
                throw new IllegalArgumentException("Unhandled CALCULATE-group kind: " + kind);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // ASSESS group ("opt")
    // ────────────────────────────────────────────────────────────────

    /**
     * Runs a {@link CalculationGroup#ASSESS} calculation. Today only {@link
     * CalculationKind#ASSESSMENT} exists, and it is not implemented yet --
     * always returns {@link CalculationOutcome.NotImplemented} so every
     * caller (CLI/GUI/API) renders the same message.
     *
     * @throws IllegalArgumentException if {@code kind} is not an ASSESS-group kind
     */
    @SuppressWarnings("unchecked")
    public static <P, R> R runAssessing(CalculationKind kind, P params) {
        if (kind.group() != CalculationGroup.ASSESS) {
            throw new IllegalArgumentException(kind + " is not an ASSESS-group calculation type");
        }

        switch (kind) {
            case ASSESSMENT:
                return (R) new CalculationOutcome.NotImplemented<>(
                        "Thermodynamic assessment (opt) is not implemented yet in the "
                        + "CalculationSession architecture. Use the legacy opt command for now.");
            default:
                throw new IllegalArgumentException("Unhandled ASSESS-group kind: " + kind);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Parameter discovery -- generic UI-driving metadata per kind
    // ────────────────────────────────────────────────────────────────

    /**
     * Generic, UI-agnostic description of the parameters {@code kind} needs
     * -- a CLI can prompt off this, a GUI can build a form, an API can
     * validate a request body, all without hardcoding each kind's shape.
     */
    public static ParameterSpec parameterSpec(CalculationKind kind) {
        switch (kind) {
            case EQUILIBRIUM:
            case INITIAL_STATE:
                return new ParameterSpec(List.of(
                        new ParameterDescriptor("T", ParameterDescriptor.ValueKind.DOUBLE,
                                "Temperature (K)", "1000.0", true),
                        new ParameterDescriptor("P", ParameterDescriptor.ValueKind.DOUBLE,
                                "Pressure (Pa)", "101325.0", true),
                        new ParameterDescriptor("composition", ParameterDescriptor.ValueKind.DOUBLE_ARRAY,
                                "Overall composition (mole fractions)", "", true)
                ));
            case STEP:
                return new ParameterSpec(List.of(
                        new ParameterDescriptor("axis", ParameterDescriptor.ValueKind.AXIS_CONFIG,
                                "Axis to walk", "", true),
                        new ParameterDescriptor("fixedT", ParameterDescriptor.ValueKind.DOUBLE,
                                "Temperature (K)", "1000.0", true),
                        new ParameterDescriptor("fixedP", ParameterDescriptor.ValueKind.DOUBLE,
                                "Pressure (Pa)", "101325.0", true),
                        new ParameterDescriptor("composition", ParameterDescriptor.ValueKind.DOUBLE_ARRAY,
                                "Overall composition (mole fractions)", "", true)
                ));
            case COARSE_BINARY:
                return new ParameterSpec(List.of(
                        new ParameterDescriptor("axisX", ParameterDescriptor.ValueKind.AXIS_CONFIG, "X axis", "", true),
                        new ParameterDescriptor("axisY", ParameterDescriptor.ValueKind.AXIS_CONFIG, "Y axis", "", true),
                        new ParameterDescriptor("fixedT", ParameterDescriptor.ValueKind.DOUBLE,
                                "Temperature (K)", "1000.0", true),
                        new ParameterDescriptor("fixedP", ParameterDescriptor.ValueKind.DOUBLE,
                                "Pressure (Pa)", "101325.0", true),
                        new ParameterDescriptor("composition", ParameterDescriptor.ValueKind.DOUBLE_ARRAY,
                                "Overall composition (mole fractions)", "", true)
                ));
            case COARSE_TERNARY:
                return new ParameterSpec(List.of(
                        new ParameterDescriptor("axisCompI", ParameterDescriptor.ValueKind.AXIS_CONFIG,
                                "Composition axis I", "", true),
                        new ParameterDescriptor("axisCompJ", ParameterDescriptor.ValueKind.AXIS_CONFIG,
                                "Composition axis J", "", true),
                        new ParameterDescriptor("fixedT", ParameterDescriptor.ValueKind.DOUBLE,
                                "Temperature (K)", "1000.0", true),
                        new ParameterDescriptor("fixedP", ParameterDescriptor.ValueKind.DOUBLE,
                                "Pressure (Pa)", "101325.0", true),
                        new ParameterDescriptor("composition", ParameterDescriptor.ValueKind.DOUBLE_ARRAY,
                                "Overall composition (mole fractions)", "", true)
                ));
            case PHASE_DIAGRAM:
                return new ParameterSpec(List.of(
                        new ParameterDescriptor("axes", ParameterDescriptor.ValueKind.AXIS_CONFIG,
                                "Axes to trace", "", true),
                        new ParameterDescriptor("startAxes", ParameterDescriptor.ValueKind.DOUBLE_ARRAY,
                                "Starting axis values", "", true),
                        new ParameterDescriptor("fixedT", ParameterDescriptor.ValueKind.DOUBLE,
                                "Temperature (K)", "1000.0", true),
                        new ParameterDescriptor("fixedP", ParameterDescriptor.ValueKind.DOUBLE,
                                "Pressure (Pa)", "101325.0", true),
                        new ParameterDescriptor("composition", ParameterDescriptor.ValueKind.DOUBLE_ARRAY,
                                "Overall composition (mole fractions)", "", true)
                ));
            case ASSESSMENT:
                return new ParameterSpec(List.of());
            default:
                throw new IllegalArgumentException("Unhandled kind: " + kind);
        }
    }
}
