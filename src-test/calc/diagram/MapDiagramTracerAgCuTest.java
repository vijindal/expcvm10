package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First end-to-end test of Step 2's C1 drain loop ({@link
 * MapDiagramTracer}, wrapping {@link MapTracer#walkOneSegment} and
 * {@link NodeRegistry}) -- see {@code
 * docs/phase_diagram_engine_flowchart.md}.
 *
 * <p><b>OpenCalphad reference and a discovered gap.</b> OC's own {@code
 * map} command (driven through a pseudo-terminal, see {@code
 * docs/oc_reference_tests/run_pty.py} -- OC's console uses raw-mode
 * terminal I/O that hangs/garbles input under plain piped stdin), on
 * {@code data/agcu.TDB} (identical to OC's own bundled {@code
 * examples/macros/agcu.TDB}) with:
 *
 * <pre>
 * set cond t=1070 p=1e5 n=1 x(cu)=.2
 * set ax 1 x(cu) 0.05 0.6 0.05
 * set ax 2 t 1000 1150 20
 * map
 * </pre>
 *
 * traced two lines from the two-phase LIQUID+FCC_A1 start, both running
 * cleanly to their T axis limits with NO crossings -- because OC's
 * mapper re-solves the exact phase-boundary composition at EVERY walk
 * step (Algorithm C2-style), continuously tracking the moving boundary.
 *
 * <p>Investigating why this codebase's {@link MapTracer} disagreed
 * (it found crossings in that same range) surfaced a real, pre-existing
 * gap, not a Step 2 regression: {@link MapTracer#walkOneSegment} (and
 * the {@link MapTracer#trace} it is refactored from) only calls the
 * exact Algorithm C2 boundary solve WHEN a stable-set change is
 * detected -- between detected changes, the release-axis composition is
 * held at whatever it last was, rather than continuously re-solved as
 * the true moving boundary. Confirmed directly: scanning {@link
 * calc.equil.EquilibriumSolverV2#solve} at fixed x(Cu)=0.2 across
 * T=[1000,1160] (this codebase's own solver, no tracer involved) shows
 * the phase set actually does change near T~1055K (a miscibility-gap
 * FCC_A1+FCC_A1 -> FCC_A1+LIQUID transition) and again near T~1135K
 * (FCC_A1+LIQUID -> LIQUID) -- i.e. a FIXED x(Cu)=0.2 genuinely is not
 * the moving phase boundary throughout [1000,1150], so finding
 * crossings there is this codebase's correct answer to a different
 * question than what OC's continuously-corrected boundary walk answers.
 * This gap (continuous boundary tracking, not just crossing-time
 * correction) belongs in a follow-up to {@code
 * docs/roadmap_phase_diagrams.md}, not fixed inside Step 2.
 *
 * <p>Given that, this test does not assert "no crossings" against the
 * OC run above (that would test a capability {@link MapTracer} does not
 * have). Instead it uses this codebase's OWN solver (scanned directly
 * above, not through any tracer) to identify a genuinely crossing-free
 * window at fixed x(Cu)=0.2 -- T in [1060,1130], stable FCC_A1+LIQUID
 * throughout -- for the axis-limit-termination case, and the wider
 * [1000,1150] range (where a real crossing exists, confirmed above) for
 * the node-creation/exit-line case. The topology OC actually reported
 * (a two-phase LIQUID+FCC_A1 start point, both directions of the T
 * axis) is still what seeds every scenario below.
 */
public class MapDiagramTracerAgCuTest {

    private static final String TDB = "data/agcu.TDB";
    private static final List<String> ELEMENTS = List.of("AG", "CU");
    private static final List<String> PHASES = List.of("LIQUID", "FCC_A1");

    private static final double START_T = 1070.0;
    private static final double START_X_CU = 0.2;
    private static final double FIXED_P = 101325.0;

    private static List<GibbsEnergyModel> candidates() throws IOException {
        return ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();
    }

    @Test
    void startPointIsTwoPhaseLiquidPlusFcc() throws IOException {
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1060.0, 1130.0, 10.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.05, 0.6, 0.05);

        NodeRegistry registry = new MapDiagramTracer().drain(
                walkAxis, releaseAxis, START_T, FIXED_P, START_T,
                new double[] { 1.0 - START_X_CU, START_X_CU }, candidates());

        Node start = registry.getNodes().get(0);
        assertEquals(2, start.stablePhaseNames.size());
        assertTrue(start.stablePhaseNames.contains("LIQUID"));
        assertTrue(start.stablePhaseNames.contains("FCC_A1"));
    }

    @Test
    void crossingFreeWindowProducesExactlyOneNodeAndTwoAxisLimitLines() throws IOException {
        // T in [1060, 1130] at fixed x(Cu)=0.2 is genuinely FCC_A1+LIQUID
        // throughout (confirmed by direct solver scan, see class javadoc)
        // -- both lines should run to their axis limits with no crossing.
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1060.0, 1130.0, 10.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.05, 0.6, 0.05);

        NodeRegistry registry = new MapDiagramTracer().drain(
                walkAxis, releaseAxis, START_T, FIXED_P, START_T,
                new double[] { 1.0 - START_X_CU, START_X_CU }, candidates());

        assertEquals(1, registry.size(), "no crossings expected in this window");

        Node start = registry.getNodes().get(0);
        List<Line> lines = start.getLines();
        assertEquals(2, lines.size());

        for (Line line : lines) {
            assertEquals(Line.State.TERMINATED, line.getState());
            assertNull(line.getEndNode(), "should terminate at an axis limit, not a node");
            assertTrue(line.size() > 0, "each line should have walked at least one point");
        }
    }

    @Test
    void crossingAtHighTCreatesANewNodeWithTwoExitLines() throws IOException {
        // T in [1000, 1150] at fixed x(Cu)=0.2 crosses FCC_A1+LIQUID ->
        // LIQUID near T~1135K (confirmed by direct solver scan) -- the
        // high-T direction line should terminate at a NEW node, which
        // should then be given exactly 2 pending/attached exit lines
        // (the ordinary tie-line-in-plane case, per the flowchart).
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1000.0, 1150.0, 10.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.05, 0.6, 0.05);

        NodeRegistry registry = new MapDiagramTracer().drain(
                walkAxis, releaseAxis, START_T, FIXED_P, START_T,
                new double[] { 1.0 - START_X_CU, START_X_CU }, candidates());

        assertTrue(registry.size() >= 2,
                "at least the start node plus one crossing-created node should exist");

        boolean foundLiquidOnlyNode = false;
        for (Node node : registry.getNodes()) {
            if (node.stablePhaseNames.size() == 1 && node.stablePhaseNames.contains("LIQUID")) {
                foundLiquidOnlyNode = true;
                assertEquals(2, node.getLines().size(),
                        "an ordinary (non-invariant) crossing node should get exactly 2 exit lines");
            }
        }
        assertTrue(foundLiquidOnlyNode, "expected a single-phase LIQUID node from the high-T crossing");
    }

    @Test
    void drainingTwiceFromTheSameStartProducesMatchingStartNodes() throws IOException {
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1060.0, 1130.0, 10.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.05, 0.6, 0.05);

        NodeRegistry first = new MapDiagramTracer().drain(
                walkAxis, releaseAxis, START_T, FIXED_P, START_T,
                new double[] { 1.0 - START_X_CU, START_X_CU }, candidates());
        NodeRegistry second = new MapDiagramTracer().drain(
                walkAxis, releaseAxis, START_T, FIXED_P, START_T,
                new double[] { 1.0 - START_X_CU, START_X_CU }, candidates());

        Node startA = first.getNodes().get(0);
        Node startB = second.getNodes().get(0);

        assertTrue(startA.matches(startB, 1e-4),
                "two independent drains from the identical start point should agree as the same node "
                        + "(regression check on Step 1's Node#matches, now exercised through the drain loop)");
    }
}
