package session.calctype;

/**
 * Generic, UI-agnostic description of one parameter a {@link CalculationType}
 * needs -- a CLI can prompt off this, a GUI can build a form row, an API can
 * validate a request body, all without hardcoding each kind's shape.
 *
 * <p>Purely for UI-driving; the real, type-safe parameter values a
 * {@code CalculationType} actually runs with are its own typed {@code P}
 * (e.g. {@code EquilibriumCalculationType.Params}), not this descriptor.
 *
 * @param name         parameter name, e.g. {@code "T"}, {@code "composition"}
 * @param valueKind    shape of the value expected
 * @param label        human-readable label, e.g. {@code "Temperature (K)"}
 * @param defaultValue string form of a default, parsed per {@code valueKind}
 *                     by the caller; empty string if there is no sensible default
 * @param required     whether the caller must supply this parameter
 */
public record ParameterDescriptor(String name, ValueKind valueKind, String label,
                                   String defaultValue, boolean required) {

    /** Shape of a parameter's value, for a generic UI to render/parse it. */
    public enum ValueKind {
        DOUBLE, DOUBLE_ARRAY, AXIS_CONFIG, STRING
    }
}
