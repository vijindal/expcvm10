package session.calctype;

import session.CalculationSession;

import java.io.IOException;

/**
 * A {@link CalculationGroup#CALCULATE} calculation type: has access to a
 * {@link CalculationSession} and a {@link ModelSelection}, and is expected to
 * call {@code session.setModel(...)} before dispatching into the matching
 * {@code CalculationSession.calculate*} method (see {@link
 * CalculationCatalog#runCalculating} for the contract every implementation
 * must follow).
 *
 * @param <P> this type's own parameter shape
 * @param <R> this type's result shape
 */
public non-sealed interface CalculatingType<P, R> extends CalculationType<P, R> {

    R run(CalculationSession session, ModelSelection model, P params) throws IOException;
}
