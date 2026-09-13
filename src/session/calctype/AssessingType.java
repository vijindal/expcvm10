package session.calctype;

/**
 * A {@link CalculationGroup#ASSESS} calculation type. Deliberately has no
 * {@code CalculationSession} parameter in {@link #run} -- an implementation
 * structurally cannot call {@code setModel}/parse a TDB even by mistake,
 * which is what makes "TDB parsing only for cal" a compiler-checked
 * guarantee rather than a convention.
 *
 * @param <P> this type's own parameter shape
 * @param <R> this type's result shape
 */
public non-sealed interface AssessingType<P, R> extends CalculationType<P, R> {

    R run(P params);
}
