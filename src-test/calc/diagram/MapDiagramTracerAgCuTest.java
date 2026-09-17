package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the C1 drain loop ({@link MapDiagramTracer}) --
 * see {@code docs/phase_diagram_engine_flowchart.md}.
 *
 * <p><b>Step 3b revision.</b> An earlier version of this test (Step 2)
 * started {@link MapDiagramTracer#drain} from an already TWO-phase
 * equilibrium (T=1070K, x(Cu)=0.2, LIQUID+FCC_A1) and treated it as the
 * start node directly. That matched {@code drain()}'s behavior at the
 * time, but not the flowchart's actual map-branch initialization
 * (Sundman 2021 Section 3.3): the true start node is whatever the
 * initial single-axis SEARCH finds, which may differ from the caller's
 * starting condition entirely if that condition is single-phase.
 * {@code drain()} now performs that search ({@link
 * MapTracer#findInitialBoundary}, Step 3a) before creating any node, so
 * this test is rewritten to start from a genuinely SINGLE-phase
 * condition -- the case Step 2's test could not exercise.
 *
 * <p><b>Reference values.</b> {@code data/agcu.TDB} (identical to OC's
 * bundled {@code examples/macros/agcu.TDB}, confirmed in earlier
 * sessions). Direct {@code EquilibriumSolverV2} scanning (no tracer)
 * at x(Cu)=0.05 found a clean single-phase FCC_A1 field from at least
 * T=1150K up to T=1175K, then FCC_A1+LIQUID from T=1180K -- narrowed to
 * that 5K bracket this session. OC's own console (driven via the pty
 * pattern, {@code docs/oc_reference_tests/run_pty.py} against the real
 * {@code oc7C} binary, capture in {@code
 * docs/oc_reference_tests/agcu_mu_1150.txt}) confirms FCC_A1 alone
 * stable at T=1150K, x(Cu)=0.05, with Chem.pot/RT = -7.1104 (Ag) /
 * -6.8921 (Cu) and RT=9561.7 J/mol at this T -- i.e. mu(Ag)=-67987.5,
 * mu(Cu)=-65900.2 J/mol, SER reference. (An earlier session's citation
 * here of mu(Ag)=-71761.7 from a since-unreproducible compiled TQ
 * example capture was a stale/incorrect transcription -- corrected
 * this session against a fresh, reproducible OC console capture, which
 * matches this project's own solver closely: mu(Ag)=-67987.17,
 * mu(Cu)=-65900.04, see {@code MultiDiagramTypeSuiteTest}'s Type-5
 * binary activity/mu-representation case.)
 */
public class MapDiagramTracerAgCuTest {

    private static final String TDB = "data/agcu.TDB";
    private static final List<String> ELEMENTS = List.of("AG", "CU");
    private static final List<String> PHASES = List.of("LIQUID", "FCC_A1");
    private static final double FIXED_P = 101325.0;

    private static List<GibbsEnergyModel> candidates() throws IOException {
        return ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();
    }

    @Test
    void searchFromASinglePhaseStartLocatesTheFccToTwoPhaseCrossing() throws IOException {
        // Start deep in single-phase FCC_A1 (OC-confirmed at T=1150K,
        // x(Cu)=0.05) and search T upward with a 5K step -- the crossing
        // this session's direct solver scan narrowed to 1175K (FCC_A1)
        // -> 1180K (FCC_A1+LIQUID).
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        NodeRegistry registry = new MapDiagramTracer().drain(
                walkAxis, releaseAxis, 1150.0, FIXED_P, 1150.0,
                new double[] { 0.95, 0.05 }, candidates());

        Node start = registry.getNodes().get(0);
        assertEquals(2, start.stablePhaseNames.size(),
                "the located start node should be the two-phase FCC_A1+LIQUID field, "
                        + "not the caller's single-phase starting condition");
        assertTrue(start.stablePhaseNames.contains("LIQUID"));
        assertTrue(start.stablePhaseNames.contains("FCC_A1"));
        assertTrue(start.axisValues[0] > 1173.0 && start.axisValues[0] < 1182.0,
                "start node T should be within the confirmed 1175-1180K bracket, got "
                        + start.axisValues[0]);
    }

    @Test
    void startNodeGetsExactlyTwoWalkAxisExitLines() throws IOException {
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        NodeRegistry registry = new MapDiagramTracer().drain(
                walkAxis, releaseAxis, 1150.0, FIXED_P, 1150.0,
                new double[] { 0.95, 0.05 }, candidates());

        Node start = registry.getNodes().get(0);
        List<Line> lines = start.getLines();
        assertEquals(2, lines.size());
        for (Line line : lines) {
            assertEquals(Line.State.TERMINATED, line.getState());
        }
    }

    @Test
    void throwsWhenNoCrossingExistsAnywhereInTheSearchRange() throws IOException {
        // A window entirely inside the single-phase FCC_A1 field (per the
        // same scan: FCC_A1 stable at least through 1175K) should find no
        // crossing at all -- drain() should fail fast rather than
        // silently treat the caller's starting point as a node.
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1170.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        MapDiagramTracer tracer = new MapDiagramTracer();
        assertThrows(IllegalStateException.class, () -> tracer.drain(
                walkAxis, releaseAxis, 1150.0, FIXED_P, 1150.0,
                new double[] { 0.95, 0.05 }, candidates()));
    }

    @Test
    void highTExitLineReachesTheFccPlusLiquidToLiquidCrossing() throws IOException {
        // Once at the correctly-located start node (T~1175-1180K), the
        // high-T exit line should walk toward the FCC_A1+LIQUID -> LIQUID
        // crossing (OC reference: T=1207.60K, docs/oc_reference_tests/
        // agcu_step_xcu05_full_walk.txt). A ZPF node's own equilibrium
        // (Sundman 2021 Algorithm C2) is the exact boundary solve --
        // BOTH phases present, FCC_A1 at its fixed ~0 amount -- not a
        // single-phase LIQUID-only point (that was this test's original,
        // now-corrected expectation, from before MapTracer was fixed to
        // pass the boundary solve's own equilibrium through as the
        // node's equilibrium instead of the coarser grid/retry point
        // used only to locate the crossing).
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        NodeRegistry registry = new MapDiagramTracer().drain(
                walkAxis, releaseAxis, 1150.0, FIXED_P, 1150.0,
                new double[] { 0.95, 0.05 }, candidates());

        boolean sawFccDisappearanceNode = false;
        for (Node node : registry.getNodes()) {
            if (node.stablePhaseNames.size() == 2
                    && node.stablePhaseNames.contains("LIQUID")
                    && node.stablePhaseNames.contains("FCC_A1")
                    && Math.abs(node.axisValues[0] - 1207.60) < 0.5) {
                sawFccDisappearanceNode = true;
            }
        }
        assertTrue(sawFccDisappearanceNode,
                "the high-T exit line should reach the FCC_A1+LIQUID -> LIQUID crossing at T=1207.60K "
                        + "(OC reference); got nodes: " + registry.getNodes());
    }
}
