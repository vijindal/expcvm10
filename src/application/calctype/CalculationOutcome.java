package application.calctype;

/**
 * Result of a {@link CalculationInterface#runAssessing} call. A sealed type
 * rather than a thrown exception so every caller (CLI/GUI/API) renders "not
 * implemented yet" the same way, without each having to catch a different
 * exception type -- the way {@code ApplicationLayer.calculatePhaseDiagram}'s
 * {@code UnsupportedOperationException} is handled ad hoc by each UI today.
 */
public sealed interface CalculationOutcome<R> permits CalculationOutcome.Success, CalculationOutcome.NotImplemented {

    record Success<R>(R result) implements CalculationOutcome<R> {
    }

    record NotImplemented<R>(String message) implements CalculationOutcome<R> {
    }
}
