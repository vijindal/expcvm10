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
 * Verifies {@link PhaseDiagramEngine}'s skeleton matches {@code
 * docs/phase_diagram_engine_flowchart.md}: implemented stages produce
 * real results (delegating to already-tested Steps 1-4 code), and
 * unimplemented stages throw {@link UnsupportedOperationException}
 * rather than silently doing nothing.
 *
 * <p>This test exists to keep the skeleton honest over time -- if a
 * stage listed here as "not yet implemented" is later implemented, this
 * test's corresponding {@code assertThrows} will fail, forcing an
 * intentional update here (and, ideally, of the flowchart/roadmap docs)
 * rather than letting the skeleton's javadoc silently drift out of date.
 */
public class PhaseDiagramEngineTest {

    private static final String TDB = "data/agcu.TDB";
    private static final List<String> ELEMENTS = List.of("AG", "CU");
    private static final List<String> PHASES = List.of("LIQUID", "FCC_A1");

    @Test
    void defineSystemIsImplemented() throws IOException {
        ThermodynamicSystem system = PhaseDiagramEngine.defineSystem(TDB, ELEMENTS, PHASES);
        assertEquals(2, system.elements().size());
        assertEquals(2, system.phaseModels().size());
    }

    @Test
    void generateStartingPointsReturnsTheSingleSuppliedPoint() {
        List<double[]> points = PhaseDiagramEngine.generateStartingPoints(1150.0);
        assertEquals(1, points.size());
        assertEquals(1150.0, points.get(0)[0]);
    }

    @Test
    void drainC1LoopIsImplementedForTheOrdinaryMapCase() throws IOException {
        List<GibbsEnergyModel> candidates =
                ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        NodeRegistry registry = PhaseDiagramEngine.drainC1Loop(
                walkAxis, releaseAxis, 1150.0, 101325.0, 1150.0,
                new double[] { 0.95, 0.05 }, candidates);

        assertTrue(registry.size() >= 1, "the drain loop should have produced at least a start node");
    }

    @Test
    void validateConditionCountIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.validateConditionCount(2, 4));
    }

    @Test
    void isGloballyStableIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.isGloballyStable(null));
    }

    @Test
    void classifyNodeIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.classifyNode(2, 2, 0));
    }

    @Test
    void mergeDedupNetworkIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.mergeDedupNetwork(new NodeRegistry()));
    }

    @Test
    void identifyPhaseRegionsIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.identifyPhaseRegions(new NodeRegistry()));
    }

    @Test
    void classifyPlotIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.classifyPlot(new NodeRegistry(), PhaseDiagramEngine.PlotType.BINARY_T_X));
    }
}
