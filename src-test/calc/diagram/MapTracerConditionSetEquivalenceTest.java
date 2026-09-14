package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Step 5b of {@code docs/roadmap_phase_diagrams.md}: {@link
 * MapTracer#walkOneSegment(ConditionSet, int, int, double, Set,
 * double[], List, EquilibriumResult)} (the new {@link ConditionSet}
 * -driven entry point) is a pure translation layer over the same shared
 * walk body ({@code walkOneSegmentInternal}) as the existing {@link
 * AxisConfig}-based overload -- this test asserts they produce
 * IDENTICAL {@link MapTracer.SegmentResult}s given an equivalent {@link
 * ConditionSet} built via {@link ConditionSet#fromBinaryAxes}, on the
 * same Ag-Cu case {@code MapDiagramTracerAgCuTest} already uses. Pure
 * refactor guard -- no new OC reference data needed, since this only
 * has to prove "these two entry points agree with each other," not
 * "the underlying physics is right" (already covered by Steps 1-4's
 * OC-referenced tests, which this refactor must not disturb).
 */
public class MapTracerConditionSetEquivalenceTest {

    private static final String TDB = "data/agcu.TDB";
    private static final List<String> ELEMENTS = List.of("AG", "CU");
    private static final List<String> PHASES = List.of("LIQUID", "FCC_A1");
    private static final double FIXED_P = 101325.0;

    private static List<GibbsEnergyModel> candidates() throws IOException {
        return ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();
    }

    @Test
    void conditionSetAndAxisConfigOverloadsAgreeOnAnOrdinaryWalk() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();

        // Single-phase FCC_A1 start, matching MapTracerInitialBoundaryTest's
        // style: T=1150K, x(Cu)=0.05.
        double startT = 1150.0;
        double[] startComp = { 0.95, 0.05 };
        EquilibriumResult startResult = EquilibriumSolveHelper.solveOrSentinel(
                startT, FIXED_P, startComp, candidates);
        Set<String> startNames = EquilibriumSolveHelper.stablePhaseNames(startResult);

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        MapTracer tracer = new MapTracer();

        MapTracer.SegmentResult viaAxisConfig = tracer.walkOneSegment(
                startT, startNames, walkAxis, releaseAxis, startT, FIXED_P,
                startComp, candidates, startResult);

        ConditionSet conds = ConditionSet.fromBinaryAxes(
                2, walkAxis, releaseAxis, startT, FIXED_P, startComp);
        List<Condition> axes = conds.axisConditions();
        int walkIndex = indexOfTemperature(axes);
        int releaseIndex = 1 - walkIndex;

        MapTracer.SegmentResult viaConditionSet = tracer.walkOneSegment(
                conds, walkIndex, releaseIndex, startT, startNames, startComp,
                candidates, startResult);

        assertEquals(viaAxisConfig.end, viaConditionSet.end);
        assertEquals(viaAxisConfig.endWalkValue, viaConditionSet.endWalkValue, 1e-12);
        assertEquals(viaAxisConfig.newStableNames, viaConditionSet.newStableNames);
        assertEquals(viaAxisConfig.points.size(), viaConditionSet.points.size());

        for (int i = 0; i < viaAxisConfig.endComposition.length; i++) {
            assertEquals(viaAxisConfig.endComposition[i], viaConditionSet.endComposition[i], 1e-12,
                    "endComposition[" + i + "] should match exactly -- both overloads share the "
                            + "same walkOneSegmentInternal body, not two independent implementations");
        }
    }

    @Test
    void conditionSetOverloadRejectsANonCompositionReleaseAxis() throws IOException {
        // Build a ConditionSet where BOTH axes happen to be TEMPERATURE-like
        // is not representable via fromBinaryAxes (composition is always
        // present), so instead construct one directly with T as both --
        // an invalid release axis choice the method must reject.
        List<GibbsEnergyModel> candidates = candidates();
        double[] startComp = { 0.95, 0.05 };

        ConditionSet conds = new ConditionSet(2, List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 1150.0, 1230.0, 5.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", FIXED_P),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(1, "x(Cu)", 0.01, 0.6, 0.01)));

        EquilibriumResult startResult = EquilibriumSolveHelper.solveOrSentinel(
                1150.0, FIXED_P, startComp, candidates);
        Set<String> startNames = EquilibriumSolveHelper.stablePhaseNames(startResult);

        MapTracer tracer = new MapTracer();
        // walkAxisIndex=0 (T), releaseAxisIndex=0 (also T) -- invalid, the
        // release axis must be COMPOSITION.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                tracer.walkOneSegment(conds, 0, 0, 1150.0, startNames, startComp, candidates, startResult));
    }

    private static int indexOfTemperature(List<Condition> axes) {
        for (int i = 0; i < axes.size(); i++) {
            if (axes.get(i).variable == Condition.Variable.TEMPERATURE) return i;
        }
        throw new IllegalStateException("Expected a TEMPERATURE axis condition");
    }
}
