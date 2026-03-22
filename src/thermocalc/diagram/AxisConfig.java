package thermocalc.diagram;

/**
 * Configuration for one axis of a phase diagram calculation.
 *
 * <p>An axis represents a variable condition that is scanned during diagram
 * tracing.  Common examples:
 * <ul>
 *   <li>Temperature T (K) — axis type {@link Type#TEMPERATURE}</li>
 *   <li>Mole fraction x(B) — axis type {@link Type#COMPOSITION}</li>
 *   <li>Pressure P (Pa) — axis type {@link Type#PRESSURE}</li>
 * </ul>
 *
 * <p>For a binary T-x diagram: axis 0 = TEMPERATURE, axis 1 = COMPOSITION.
 * The DiagramTracer and LineStepper use {@link #type} to map axis values onto
 * the conditions passed to the equilibrium solver.
 */
public final class AxisConfig {

    /** Kind of thermodynamic variable represented by this axis. */
    public enum Type {
        /** Temperature in Kelvin. */
        TEMPERATURE,
        /** Mole fraction of a component (maps to overall composition[componentIndex]). */
        COMPOSITION,
        /** Pressure in Pa. */
        PRESSURE
    }

    /** Human-readable label, e.g. "T / K" or "x(B)". */
    public final String name;

    /** Axis variable type. */
    public final Type type;

    /**
     * Component index this axis refers to.
     * Relevant only for {@link Type#COMPOSITION}; ignored otherwise.
     */
    public final int componentIndex;

    /** Lower bound (inclusive). */
    public final double min;

    /** Upper bound (inclusive). */
    public final double max;

    /** Step size for ZPF line following. */
    public final double step;

    /**
     * Constructs an axis configuration for a TEMPERATURE or PRESSURE axis.
     *
     * @param name   display label
     * @param type   must be TEMPERATURE or PRESSURE
     * @param min    lower bound
     * @param max    upper bound
     * @param step   step size
     */
    public AxisConfig(String name, Type type, double min, double max, double step) {
        if (type == Type.COMPOSITION) {
            throw new IllegalArgumentException(
                    "Use AxisConfig(name, COMPOSITION, componentIndex, min, max, step) "
                    + "for composition axes.");
        }
        this.name           = name;
        this.type           = type;
        this.componentIndex = -1;
        this.min            = min;
        this.max            = max;
        this.step           = step;
    }

    /**
     * Constructs an axis configuration for a COMPOSITION axis.
     *
     * @param name            display label, e.g. "x(B)"
     * @param componentIndex  0-based index in the elements list
     * @param min             lower bound (mole fraction)
     * @param max             upper bound (mole fraction)
     * @param step            step size
     */
    public AxisConfig(String name, int componentIndex,
                      double min, double max, double step) {
        this.name           = name;
        this.type           = Type.COMPOSITION;
        this.componentIndex = componentIndex;
        this.min            = min;
        this.max            = max;
        this.step           = step;
    }

    /** Returns {@code true} if {@code value} lies within [min, max]. */
    public boolean inBounds(double value) {
        return value >= min && value <= max;
    }

    @Override
    public String toString() {
        return name + "[" + type + " " + min + ".." + max + " step=" + step + "]";
    }
}
