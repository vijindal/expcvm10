package calc.diagram;
import ui.request.AxisConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete set of thermodynamic conditions for one diagram calculation
 * -- exactly {@code n+2} of them, per Sundman 2021 §2.3.2 ({@link
 * PhaseDiagramEngine#validateConditionCount}). See {@link Condition}'s
 * class javadoc for why this is the generalization behind "one engine,
 * not one tracer per diagram type."
 */
public final class ConditionSet {

    private final List<Condition> conditions;
    private final int numComponents;

    public ConditionSet(int numComponents, List<Condition> conditions) {
        this.numComponents = numComponents;
        this.conditions = List.copyOf(conditions);
        PhaseDiagramEngine.validateConditionCount(numComponents, this.conditions.size());
    }

    public int numComponents() {
        return numComponents;
    }

    public List<Condition> all() {
        return conditions;
    }

    /** Conditions with {@link Condition.Role#AXIS} -- the diagram's free axes, in supplied order. */
    public List<Condition> axisConditions() {
        List<Condition> axes = new ArrayList<>();
        for (Condition c : conditions) {
            if (c.isAxis()) axes.add(c);
        }
        return axes;
    }

    public int numAxes() {
        return axisConditions().size();
    }

    /** The single FIXED temperature, or {@code Double.NaN} if temperature is an AXIS condition instead. */
    public double fixedTemperature() {
        for (Condition c : conditions) {
            if (c.variable == Condition.Variable.TEMPERATURE && c.isFixed()) return c.fixedValue;
        }
        return Double.NaN;
    }

    /** The single FIXED pressure, or {@code Double.NaN} if pressure is an AXIS condition instead. */
    public double fixedPressure() {
        for (Condition c : conditions) {
            if (c.variable == Condition.Variable.PRESSURE && c.isFixed()) return c.fixedValue;
        }
        return Double.NaN;
    }

    /**
     * Overall composition array (length {@link #numComponents()}) built
     * from every FIXED composition condition, with any AXIS composition
     * condition's entry set to its {@code min} as a starting value, and
     * every component with no explicit condition (the "implicit"
     * component in a ternary+ system, e.g. Fe when x(Cr) and x(Mo) are
     * given) filled by the remainder needed to sum to 1.
     */
    public double[] initialComposition() {
        double[] comp = new double[numComponents];
        boolean[] specified = new boolean[numComponents];
        double specifiedSum = 0.0;

        for (Condition c : conditions) {
            if (c.variable != Condition.Variable.COMPOSITION) continue;
            double value = c.isFixed() ? c.fixedValue : c.min;
            comp[c.componentIndex] = value;
            specified[c.componentIndex] = true;
            specifiedSum += value;
        }

        int unspecifiedCount = 0;
        for (boolean s : specified) if (!s) unspecifiedCount++;

        if (unspecifiedCount > 0) {
            double remainder = Math.max(0.0, 1.0 - specifiedSum) / unspecifiedCount;
            for (int i = 0; i < numComponents; i++) {
                if (!specified[i]) comp[i] = remainder;
            }
        }

        return comp;
    }

    /**
     * Constructs a {@link ConditionSet} for a binary MAP: T and/or P
     * and/or one composition axis walked, one composition axis
     * released, N fixed at 1 -- the bridge from an {@link AxisConfig}
     * pair to this engine-internal model.
     *
     * @param numComponents the number of components (2 for a binary)
     * @param walkAxis      today's walk axis (T, P, or a composition)
     * @param releaseAxis   today's release axis (always COMPOSITION)
     * @param fixedT        used when {@code walkAxis.type != TEMPERATURE}
     * @param fixedP        used when {@code walkAxis.type != PRESSURE}
     * @param compOverall   starting overall composition (length {@code numComponents})
     */
    public static ConditionSet fromBinaryAxes(
            int numComponents,
            AxisConfig walkAxis,
            AxisConfig releaseAxis,
            double fixedT,
            double fixedP,
            double[] compOverall) {

        if (numComponents == 2 && walkAxis.type == AxisConfig.Type.COMPOSITION) {
            // A binary has exactly ONE independent composition degree of
            // freedom -- walkAxis and releaseAxis would both be
            // COMPOSITION conditions, one too many alongside T/P/N (5
            // total, not n+2=4). Callers use a TEMPERATURE (or PRESSURE)
            // walkAxis with a COMPOSITION releaseAxis; this combination
            // is unsupported here rather than silently mis-modeled.
            throw new IllegalArgumentException(
                    "fromBinaryAxes does not support a COMPOSITION walkAxis together with a "
                    + "COMPOSITION releaseAxis for a 2-component system -- that is 2 composition "
                    + "conditions for only 1 independent composition degree of freedom in a binary.");
        }

        List<Condition> conditions = new ArrayList<>();

        if (walkAxis.type == AxisConfig.Type.TEMPERATURE) {
            conditions.add(Condition.axis(Condition.Variable.TEMPERATURE, walkAxis.name,
                    walkAxis.min, walkAxis.max, walkAxis.step));
        } else {
            conditions.add(Condition.fixed(Condition.Variable.TEMPERATURE, "T", fixedT));
        }

        if (walkAxis.type == AxisConfig.Type.PRESSURE) {
            conditions.add(Condition.axis(Condition.Variable.PRESSURE, walkAxis.name,
                    walkAxis.min, walkAxis.max, walkAxis.step));
        } else {
            conditions.add(Condition.fixed(Condition.Variable.PRESSURE, "P", fixedP));
        }

        conditions.add(Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0));

        conditions.add(Condition.axisComposition(releaseAxis.componentIndex, releaseAxis.name,
                releaseAxis.min, releaseAxis.max, releaseAxis.step));

        // For a binary (numComponents == 2), this one composition AXIS
        // condition already covers both components: the released one
        // explicitly, and the other implied by summing to 1 --
        // initialComposition()'s "implicit remainder" logic handles that
        // second component automatically.

        return new ConditionSet(numComponents, conditions);
    }

    /**
     * Constructs a {@link ConditionSet} equivalent to today's single-axis
     * STEP call: one axis (T, P, or a composition) walked, every other
     * composition held FIXED at {@code compOverall}'s own value (the
     * last unspecified component filled in by {@link #initialComposition()}'s
     * implicit-remainder rule), N fixed at 1 -- the STEP-mode counterpart
     * of {@link #fromBinaryAxes} (which is MAP-only: it always supplies
     * exactly one released COMPOSITION axis, which STEP has none of).
     *
     * @param numComponents the number of components
     * @param walkAxis      the axis walked (T, P, or a composition)
     * @param fixedT        used when {@code walkAxis.type != TEMPERATURE}
     * @param fixedP        used when {@code walkAxis.type != PRESSURE}
     * @param compOverall   starting overall composition (length {@code numComponents});
     *                      every component except {@code walkAxis}'s own (if it is a
     *                      composition axis) and the last is supplied as a FIXED condition,
     *                      the last left implicit (per {@link #initialComposition()})
     */
    public static ConditionSet fromStepAxis(
            int numComponents,
            AxisConfig walkAxis,
            double fixedT,
            double fixedP,
            double[] compOverall) {

        List<Condition> conditions = new ArrayList<>();

        if (walkAxis.type == AxisConfig.Type.TEMPERATURE) {
            conditions.add(Condition.axis(Condition.Variable.TEMPERATURE, walkAxis.name,
                    walkAxis.min, walkAxis.max, walkAxis.step));
        } else {
            conditions.add(Condition.fixed(Condition.Variable.TEMPERATURE, "T", fixedT));
        }

        if (walkAxis.type == AxisConfig.Type.PRESSURE) {
            conditions.add(Condition.axis(Condition.Variable.PRESSURE, walkAxis.name,
                    walkAxis.min, walkAxis.max, walkAxis.step));
        } else {
            conditions.add(Condition.fixed(Condition.Variable.PRESSURE, "P", fixedP));
        }

        conditions.add(Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0));

        // Every component gets an explicit condition -- the walked axis
        // itself if it is a composition axis, otherwise FIXED at
        // compOverall's own value -- except exactly one, left implicit
        // (filled in by initialComposition()'s remainder rule) to match
        // n+2's own count: T, P, N, plus (numComponents - 1) composition
        // conditions = numComponents + 2. The implicit component is the
        // LAST index not equal to the walked composition axis's own
        // index (if any), so a composition walkAxis at the last index
        // still gets its own AXIS condition rather than being silently
        // dropped as "the implicit one."
        int walkedComponentIndex = walkAxis.type == AxisConfig.Type.COMPOSITION
                ? walkAxis.componentIndex : -1;
        int implicitComponentIndex = numComponents - 1;
        if (implicitComponentIndex == walkedComponentIndex) {
            implicitComponentIndex = numComponents - 2;
        }
        for (int i = 0; i < numComponents; i++) {
            if (i == implicitComponentIndex) {
                continue;
            }
            if (i == walkedComponentIndex) {
                conditions.add(Condition.axisComposition(i, walkAxis.name,
                        walkAxis.min, walkAxis.max, walkAxis.step));
            } else {
                conditions.add(Condition.fixedComposition(i, "x(" + i + ")", compOverall[i]));
            }
        }

        return new ConditionSet(numComponents, conditions);
    }

    @Override
    public String toString() {
        return "ConditionSet[n=" + numComponents + ", " + conditions + "]";
    }
}
