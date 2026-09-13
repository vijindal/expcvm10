package session.calctype;

/**
 * One calculation a caller can run via {@link CalculationCatalog}, keyed by
 * {@link CalculationKind}. Sealed into {@link CalculatingType} (has access to
 * a {@code CalculationSession}) and {@link AssessingType} (does not) so the
 * TDB-parsing gate in {@link CalculationGroup#ASSESS} is structural, not
 * conventional -- an {@code AssessingType} implementation has no parameter
 * through which it could reach a {@code CalculationSession}/{@code setModel}
 * even by mistake.
 *
 * @param <P> this type's own parameter shape
 * @param <R> this type's result shape
 */
public sealed interface CalculationType<P, R> permits CalculatingType, AssessingType {

    CalculationKind kind();

    /**
     * Generic, UI-agnostic description of the parameters this type needs --
     * CLI prompts, GUI builds a form, API validates a request body, all off
     * this same list rather than each hardcoding this type's shape.
     */
    ParameterSpec parameterSpec();
}
