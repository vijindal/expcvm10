package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ports.EquilibriumResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Line#markExcluded}/{@link Line#isExcluded} -- the {@code
 * EXCLUDEDLINE} equivalent wired into {@link MapDiagramTracer#drain}
 * (Step 6b, {@code docs/roadmap_phase_diagrams.md}) when a newly-created
 * node fails {@link PhaseDiagramEngine#isGloballyStable}.
 */
public class LineTest {

    private static EquilibriumResult dummyEquilibrium() {
        EquilibriumResult.PhaseResult liquid = new EquilibriumResult.PhaseResult(
                "LIQUID", "CEF", 1.0, new double[] { 0.5, 0.5 }, new double[] { 0.5, 0.5 },
                -1000.0, 0.0, 1.0);
        return new EquilibriumResult(1000.0, 101325.0, new double[] { -1000.0, -1000.0 },
                List.of(liquid), List.of(), true, 5);
    }

    @Test
    void markExcludedRequiresTerminatedState() {
        Node node = new Node(0, dummyEquilibrium(), new double[] { 1000.0, 0.5 }, new double[] { 0.5, 0.5 });
        Line line = new Line(node, List.of(), 0, +1);

        assertThrows(IllegalStateException.class, line::markExcluded);
        assertFalse(line.isExcluded());
    }

    @Test
    void markExcludedSetsIsExcludedOnceTerminated() {
        Node startNode = new Node(0, dummyEquilibrium(), new double[] { 1000.0, 0.5 }, new double[] { 0.5, 0.5 });
        Node endNode = new Node(1, dummyEquilibrium(), new double[] { 1050.0, 0.5 }, new double[] { 0.5, 0.5 });
        Line line = new Line(startNode, List.of(), 0, +1);

        line.startWalking();
        line.terminateAtNode(endNode);
        assertFalse(line.isExcluded());

        line.markExcluded();
        assertTrue(line.isExcluded());
        assertEquals(Line.State.TERMINATED, line.getState());
    }
}
