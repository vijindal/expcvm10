package calc.diagram;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ConditionSet} correctly generalizes across diagram
 * types -- Step 5a of {@code docs/phase_diagram_engine_flowchart.md}'s
 * "one engine, not one tracer per diagram type" claim, worked out
 * concretely rather than just asserted. See {@link Condition}'s class
 * javadoc for the binary-vs-ternary-isothermal shape comparison this
 * test exercises directly.
 */
public class ConditionSetTest {

    @Test
    void fromBinaryAxesReproducesTodaysAgCuMapConditionShape() {
        // Matches MapDiagramTracerAgCuTest's exact setup (Steps 3b/4):
        // T walked, x(Cu) released, P/N fixed -- 4 conditions for n=2.
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        ConditionSet set = ConditionSet.fromBinaryAxes(2, walkAxis, releaseAxis, 1150.0, 101325.0,
                new double[] { 0.95, 0.05 });

        assertEquals(4, set.all().size(), "a 2-component system needs exactly n+2=4 conditions");
        assertEquals(2, set.numAxes(), "T (walked) and x(Cu) (released) are BOTH AXIS conditions -- "
                + "they vary across the diagram. Whether a given AXIS condition is independently "
                + "stepped ('walked') or solved-for dependently at a crossing ('released') is a "
                + "per-line, per-moment TRACER decision (which axis the drain loop happens to be "
                + "incrementing right now vs. which one Algorithm C2 solves for), not a durable "
                + "property of the condition itself -- see Condition's class javadoc.");
    }

    @Test
    void fromBinaryAxesFixesPressureAndTotalMoles() {
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        ConditionSet set = ConditionSet.fromBinaryAxes(2, walkAxis, releaseAxis, 1150.0, 101325.0,
                new double[] { 0.95, 0.05 });

        assertEquals(101325.0, set.fixedPressure());
        assertTrue(Double.isNaN(set.fixedTemperature()),
                "T is an AXIS condition here, not FIXED, so fixedTemperature() should report NaN");
    }

    @Test
    void initialCompositionFillsTheImplicitSecondComponent() {
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        ConditionSet set = ConditionSet.fromBinaryAxes(2, walkAxis, releaseAxis, 1150.0, 101325.0,
                new double[] { 0.95, 0.05 });

        double[] comp = set.initialComposition();
        assertEquals(2, comp.length);
        assertEquals(0.01, comp[1], 1e-12, "x(Cu) (component 1) takes the release axis's min value");
        assertEquals(0.99, comp[0], 1e-12, "x(Ag) (component 0, implicit) fills the remainder to sum to 1");
    }

    @Test
    void rejectsACompositionWalkAxisPairedWithACompositionReleaseAxisForABinary() {
        AxisConfig walkAxis = new AxisConfig("x(Ag)", 0, 0.5, 0.99, 0.01);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        assertThrows(IllegalArgumentException.class, () ->
                ConditionSet.fromBinaryAxes(2, walkAxis, releaseAxis, 1150.0, 101325.0,
                        new double[] { 0.95, 0.05 }));
    }

    @Test
    void ternaryIsothermalShapeHasTwoCompositionAxesAndNoTemperatureAxis() {
        // The Step 5 target shape (Condition's class javadoc): n=3, T
        // FIXED, P FIXED, N FIXED, x(Cr) AXIS, x(Mo) AXIS -- 5 conditions,
        // 2 of them AXIS, BOTH composition, no T walk at all. Built
        // directly (not via fromBinaryAxes, which is binary-only) to
        // confirm the general Condition/ConditionSet model itself
        // supports this shape without any binary-specific assumption.
        List<Condition> conditions = List.of(
                Condition.fixed(Condition.Variable.TEMPERATURE, "T", 1400.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 101325.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(0, "x(Cr)", 0.0, 1.0, 0.02),
                Condition.axisComposition(2, "x(Mo)", 0.0, 1.0, 0.02));

        ConditionSet set = new ConditionSet(3, conditions);

        assertEquals(5, set.all().size(), "a 3-component system needs exactly n+2=5 conditions");
        assertEquals(2, set.numAxes(), "a ternary isothermal section has 2 AXIS conditions");
        assertEquals(1400.0, set.fixedTemperature());
        for (Condition axis : set.axisConditions()) {
            assertEquals(Condition.Variable.COMPOSITION, axis.variable,
                    "both AXIS conditions in a ternary isothermal section are COMPOSITION, "
                            + "unlike the binary map case where one AXIS condition is TEMPERATURE");
        }
    }

    @Test
    void ternaryIsothermalInitialCompositionFillsTheImplicitThirdComponent() {
        List<Condition> conditions = List.of(
                Condition.fixed(Condition.Variable.TEMPERATURE, "T", 1400.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 101325.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(0, "x(Cr)", 0.3, 0.3, 0.02),
                Condition.axisComposition(2, "x(Mo)", 0.05, 0.05, 0.02));

        ConditionSet set = new ConditionSet(3, conditions);
        double[] comp = set.initialComposition();

        assertEquals(3, comp.length);
        assertEquals(0.3, comp[0], 1e-12, "x(Cr), explicitly given");
        assertEquals(0.65, comp[1], 1e-12, "x(Fe) (implicit component 1) fills the remainder to sum to 1");
        assertEquals(0.05, comp[2], 1e-12, "x(Mo), explicitly given");
    }

    @Test
    void rejectsAWrongNumberOfConditions() {
        // Reuses PhaseDiagramEngine.validateConditionCount (OC-cross-
        // checked, see PhaseDiagramEngineTest) -- ConditionSet must not
        // bypass that check.
        List<Condition> tooFew = List.of(
                Condition.fixed(Condition.Variable.TEMPERATURE, "T", 1400.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 101325.0));

        assertThrows(IllegalArgumentException.class, () -> new ConditionSet(3, tooFew));
    }
}
