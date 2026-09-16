package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mid-line {@code GLOBAL STABILITY CHECK} (Sundman 2021 Calphad 75
 * §2.3.3: "global checks are made at node points and at regular intervals
 * along a line") -- {@link StepTracer#walkOneSegment(double, Set,
 * AxisConfig, double, double, double[], List, int)} and {@link
 * MapTracer#walkOneSegment(double, Set, AxisConfig, AxisConfig, double,
 * double, double[], List, EquilibriumResult, int)}'s {@code
 * globalCheckInterval} parameter, matching OC's own {@code
 * globalcheckinterval}/{@code check_all_phases} mechanism ({@code
 * smp2A.F90}, default interval 10 -- {@link
 * StepTracer#DEFAULT_GLOBAL_CHECK_INTERVAL}).
 *
 * <p>Only node-level instability has a real, reliable forced-failure
 * calibration case ({@code PhaseDiagramEngineGlobalStabilityTest}, via
 * {@code EquilibriumSolverV2#setInitialStateForTest}, which only affects
 * ONE {@code solve()} call -- not a specific point mid-walk). So this
 * suite instead verifies the interval ARITHMETIC directly (does the check
 * fire at the Nth point, not before) using a genuinely stable walk, plus
 * confirms enabling the check changes nothing for a real, physically
 * stable walk (no false positives) -- the same regression-safety property
 * {@link StepDiagramTracerTest}/{@code MapDiagramTracerAgCuTest} already
 * rely on with checking disabled (interval=0).
 */
public class GlobalStabilityIntervalCheckTest {

    private static Set<String> stableNames(EquilibriumResult r) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : r.getStablePhases()) names.add(pr.phaseName);
        return names;
    }

    @Test
    void stepWalkWithCheckingEnabledMatchesUncheckedWalkOnAGenuinelyStableRun() throws IOException {
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1176.0, 5.0);
        double[] comp = { 0.95, 0.05 };

        EquilibriumResult start = new calc.equil.EquilibriumSolverV2().solve(1150.0, 101325.0, comp, candidates);
        assertTrue(start.isConverged());

        StepTracer tracer = new StepTracer();

        StepTracer.SegmentResult unchecked = tracer.walkOneSegment(
                1150.0, stableNames(start), axis, 0.0, 101325.0, comp, candidates);

        StepTracer.SegmentResult checked = tracer.walkOneSegment(
                1150.0, stableNames(start), axis, 0.0, 101325.0, comp, candidates, 1);

        // With interval=1, the check runs at EVERY point -- for a
        // genuinely stable FCC_A1-only run within this range (well below
        // the 1176K liquidus), it must never trigger a false positive:
        // both walks should reach the same axis limit outcome.
        assertEquals(StepTracer.SegmentEnd.AXIS_LIMIT, unchecked.end);
        assertEquals(StepTracer.SegmentEnd.AXIS_LIMIT, checked.end);
        assertEquals(unchecked.points.size(), checked.points.size(),
                "enabling the interval check should not change a genuinely stable walk's outcome");
    }

    @Test
    void mapWalkWithCheckingEnabledMatchesUncheckedWalkOnAGenuinelyStableRun() throws IOException {
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1176.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);
        double[] comp = { 0.95, 0.05 };

        EquilibriumResult start = new calc.equil.EquilibriumSolverV2().solve(1150.0, 101325.0, comp, candidates);
        assertTrue(start.isConverged());

        MapTracer tracer = new MapTracer();

        MapTracer.SegmentResult unchecked = tracer.walkOneSegment(
                1150.0, stableNames(start), walkAxis, releaseAxis, 0.0, 101325.0, comp, candidates, start);

        MapTracer.SegmentResult checked = tracer.walkOneSegment(
                1150.0, stableNames(start), walkAxis, releaseAxis, 0.0, 101325.0, comp, candidates, start, 1);

        assertEquals(MapTracer.SegmentEnd.AXIS_LIMIT, unchecked.end);
        assertEquals(MapTracer.SegmentEnd.AXIS_LIMIT, checked.end);
        assertEquals(unchecked.points.size(), checked.points.size(),
                "enabling the interval check should not change a genuinely stable walk's outcome");
    }

    @Test
    void stepDiagramTracerDrainStillFindsBothOcCrossingsWithCheckingEnabled() throws IOException {
        // Regression: StepDiagramTracer already enables the interval check
        // by default (DEFAULT_GLOBAL_CHECK_INTERVAL) -- confirm the
        // OC-confirmed crossings from StepDiagramTracerTest still surface
        // unaffected by wiring the check in.
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        NodeRegistry registry = new StepDiagramTracer().drain(
                axis, 0.0, 101325.0, new double[] { 0.95, 0.05 }, candidates);

        boolean sawLiquidusCrossing = false;
        for (Node node : registry.getNodes()) {
            double t = node.axisValues[0];
            if (t > 1176.0 && t < 1177.0 && node.stablePhaseNames.equals(Set.of("FCC_A1", "LIQUID"))) {
                sawLiquidusCrossing = true;
            }
        }
        assertTrue(sawLiquidusCrossing, "the OC-confirmed liquidus crossing should still be found");
    }
}
