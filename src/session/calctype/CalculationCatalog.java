package session.calctype;

import session.CalculationSession;
import session.calctype.types.AssessmentCalculationType;
import session.calctype.types.CoarseBinaryCalculationType;
import session.calctype.types.CoarseTernaryCalculationType;
import session.calctype.types.EquilibriumCalculationType;
import session.calctype.types.InitialStateCalculationType;
import session.calctype.types.MapCalculationType;
import session.calctype.types.PhaseDiagramCalculationType;
import session.calctype.types.StepCalculationType;

import java.io.IOException;
import java.util.Map;

/**
 * The single funnel point a caller (CLI/GUI/API) uses to run any {@link
 * CalculationType} -- never {@link CalculationSession} directly. Looks up
 * the {@link CalculationType} registered for a {@link CalculationKind} and
 * enforces, at the call-site signature level, the two structural
 * guarantees this package exists for:
 * <ul>
 *   <li>{@link #runCalculating} requires a non-null {@link ModelSelection}
 *       and only ever dispatches to a {@link CalculatingType} -- a {@code
 *       CalculationGroup#ASSESS} kind cannot be run this way.</li>
 *   <li>{@link #runAssessing} never receives (and so can never pass along)
 *       a {@link CalculationSession} -- a {@code CalculationGroup#CALCULATE}
 *       kind cannot be run this way, and TDB parsing is unreachable from
 *       this path entirely.</li>
 * </ul>
 */
public final class CalculationCatalog {

    private CalculationCatalog() {
    }

    private static final Map<CalculationKind, CalculationType<?, ?>> TYPES = Map.ofEntries(
            Map.entry(CalculationKind.EQUILIBRIUM, new EquilibriumCalculationType()),
            Map.entry(CalculationKind.INITIAL_STATE, new InitialStateCalculationType()),
            Map.entry(CalculationKind.STEP, new StepCalculationType()),
            Map.entry(CalculationKind.COARSE_BINARY, new CoarseBinaryCalculationType()),
            Map.entry(CalculationKind.COARSE_TERNARY, new CoarseTernaryCalculationType()),
            Map.entry(CalculationKind.MAP, new MapCalculationType()),
            Map.entry(CalculationKind.PHASE_DIAGRAM, new PhaseDiagramCalculationType()),
            Map.entry(CalculationKind.ASSESSMENT, new AssessmentCalculationType())
    );

    /** The {@link CalculationType} registered for {@code kind}, or {@code null} if none. */
    public static CalculationType<?, ?> get(CalculationKind kind) {
        return TYPES.get(kind);
    }

    /**
     * Runs a {@link CalculationGroup#CALCULATE} calculation: {@code kind}
     * must resolve to a {@link CalculatingType}, and {@code model} must be
     * non-null (the "Gibbs energy model parameters selected, then passed to
     * CalculationSession" step happens inside the matching {@code
     * CalculatingType.run}, before its own parameters are used).
     *
     * @throws IllegalArgumentException if {@code kind} isn't a {@link
     *         CalculatingType}, or {@code model} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <P, R> R runCalculating(CalculationSession session, CalculationKind kind,
                                           ModelSelection model, P params) throws IOException {
        CalculationType<?, ?> type = get(kind);
        if (!(type instanceof CalculatingType)) {
            throw new IllegalArgumentException(kind + " is not a CALCULATE-group calculation type");
        }
        if (model == null) {
            throw new IllegalArgumentException(kind + " requires a ModelSelection (setModel inputs)");
        }
        CalculatingType<P, R> calculating = (CalculatingType<P, R>) type;
        return calculating.run(session, model, params);
    }

    /**
     * Runs a {@link CalculationGroup#ASSESS} calculation: {@code kind} must
     * resolve to an {@link AssessingType}. Never touches a {@link
     * CalculationSession} -- there is no parameter through which one could
     * be passed.
     *
     * @throws IllegalArgumentException if {@code kind} isn't an {@link AssessingType}
     */
    @SuppressWarnings("unchecked")
    public static <P, R> R runAssessing(CalculationKind kind, P params) {
        CalculationType<?, ?> type = get(kind);
        if (!(type instanceof AssessingType)) {
            throw new IllegalArgumentException(kind + " is not an ASSESS-group calculation type");
        }
        AssessingType<P, R> assessing = (AssessingType<P, R>) type;
        return assessing.run(params);
    }
}
