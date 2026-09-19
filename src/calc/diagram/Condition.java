package calc.diagram;
import ui.request.AxisConfig;

/**
 * One thermodynamic condition in a {@link ConditionSet}, per Sundman
 * 2021 §2.3.2's n+2 condition rule -- e.g. a binary T-x map is n=2
 * components, 4 conditions (T=AXIS, P=FIXED, N=FIXED, x(B)=AXIS).
 */
public final class Condition {

    /** Which thermodynamic variable this condition constrains. */
    public enum Variable {
        TEMPERATURE,
        PRESSURE,
        /** Total moles of the system (Sundman's N). */
        TOTAL_MOLES,
        /** Mole fraction of one component; see {@link Condition#componentIndex}. */
        COMPOSITION
    }

    /**
     * Whether this condition is held constant or varies during tracing.
     *
     * <p><b>Design decision: no separate WALKED/RELEASED role.</b> In
     * today's binary map, T is independently stepped ("walked") while
     * x(Cu) is solved for dependently at each crossing via Algorithm C2
     * ("released"); in a ternary isothermal section, both composition
     * axes are simultaneously free with no "released" variable in the
     * same sense. Rather than encode WALKED vs. RELEASED as a durable
     * per-condition property, both are modeled as {@link #AXIS} here --
     * whether a given AXIS condition is actively incremented or solved-
     * for at any particular walk step is a per-line, per-moment TRACER
     * decision (the flowchart's own "pick the fastest-varying axis"
     * language already treats this as dynamic, not fixed), not
     * something that belongs on the condition itself.
     */
    public enum Role {
        /** A single, unchanging value for the whole calculation. */
        FIXED,
        /** Varies -- one of the diagram's free axes. */
        AXIS
    }

    public final Variable variable;
    public final Role role;

    /** 0-based component index; meaningful only when {@code variable == COMPOSITION}. */
    public final int componentIndex;

    /** Human-readable label, e.g. "T / K" or "x(Cr)". */
    public final String name;

    /** The constant value; meaningful only when {@code role == FIXED}. */
    public final double fixedValue;

    /** Axis bounds and step; meaningful only when {@code role == AXIS}. */
    public final double min;
    public final double max;
    public final double step;

    private Condition(Variable variable, Role role, int componentIndex, String name,
                       double fixedValue, double min, double max, double step) {
        this.variable = variable;
        this.role = role;
        this.componentIndex = componentIndex;
        this.name = name;
        this.fixedValue = fixedValue;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public static Condition fixed(Variable variable, String name, double value) {
        if (variable == Variable.COMPOSITION) {
            throw new IllegalArgumentException("Use fixedComposition(...) for a COMPOSITION condition.");
        }
        return new Condition(variable, Role.FIXED, -1, name, value, Double.NaN, Double.NaN, Double.NaN);
    }

    public static Condition fixedComposition(int componentIndex, String name, double value) {
        return new Condition(Variable.COMPOSITION, Role.FIXED, componentIndex, name,
                value, Double.NaN, Double.NaN, Double.NaN);
    }

    public static Condition axis(Variable variable, String name, double min, double max, double step) {
        if (variable == Variable.COMPOSITION) {
            throw new IllegalArgumentException("Use axisComposition(...) for a COMPOSITION condition.");
        }
        return new Condition(variable, Role.AXIS, -1, name, Double.NaN, min, max, step);
    }

    public static Condition axisComposition(int componentIndex, String name,
                                             double min, double max, double step) {
        return new Condition(Variable.COMPOSITION, Role.AXIS, componentIndex, name,
                Double.NaN, min, max, step);
    }

    public boolean isAxis() {
        return role == Role.AXIS;
    }

    public boolean isFixed() {
        return role == Role.FIXED;
    }

    /** This condition's {@link #min}/{@link #max}/{@link #step} as an {@link AxisConfig}. Requires {@link #isAxis()}. */
    public AxisConfig toAxisConfig() {
        if (!isAxis()) {
            throw new IllegalStateException("Cannot convert a FIXED condition to an AxisConfig: " + this);
        }
        if (variable == Variable.COMPOSITION) {
            return new AxisConfig(name, componentIndex, min, max, step);
        }
        AxisConfig.Type type = variable == Variable.TEMPERATURE
                ? AxisConfig.Type.TEMPERATURE : AxisConfig.Type.PRESSURE;
        if (variable == Variable.TOTAL_MOLES) {
            throw new IllegalStateException("TOTAL_MOLES has no AxisConfig.Type equivalent today.");
        }
        return new AxisConfig(name, type, min, max, step);
    }

    @Override
    public String toString() {
        return role == Role.FIXED
                ? name + "=" + fixedValue
                : name + "[" + min + ".." + max + " step=" + step + "]";
    }
}
