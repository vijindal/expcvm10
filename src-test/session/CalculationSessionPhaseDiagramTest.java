package session;

import calc.diagram.AxisConfig;
import calc.diagram.PhaseDiagramResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CalculationSession#calculatePhaseDiagram} -- the UI-facing entry
 * point wiring {@link calc.diagram.PhaseDiagramEngine}'s full flowchart
 * sequence (defineSystem is skipped here since {@link
 * CalculationSession#setModel} already built the system; {@code
 * validateConditionCount -> generateStartingPoints -> drainC1Loop/
 * drainStepLoop -> classifyPlot}) into {@link CalculationSession}, closing
 * the "not yet wired into any UI" gap {@code PhaseDiagramEngine}'s own
 * class javadoc used to describe.
 *
 * <p>OC reference: same Ag-Cu liquidus crossing (data/agcu.TDB,
 * x(Cu)=0.05, T=1176.13K) already used throughout {@code
 * PhaseDiagramEngineEndToEndTest} and {@code
 * StepDiagramTracerOcFormatterComparisonTest} -- this test's job is only
 * to confirm the SAME already-OC-verified engine result reaches a caller
 * through {@link CalculationSession}, not to re-derive it.
 */
public class CalculationSessionPhaseDiagramTest {

    @Test
    void calculatePhaseDiagramTwoAxesReturnsTheOcConfirmedLiquidus() throws IOException {
        CalculationSession session = new CalculationSession();
        session.setModel("data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        session.calculatePhaseDiagram(
                new AxisConfig[] { walkAxis, releaseAxis },
                new double[] { 1150.0, 0.01 },
                1150.0, 101325.0, new double[] { 0.95, 0.05 });

        PhaseDiagramResult result = session.currentPhaseDiagram();

        boolean foundOcConfirmedLiquidus = false;
        for (PhaseDiagramResult.NodePoint node : result.getNodes()) {
            double t = node.axisValues[0];
            if (t >= 1176.0 && t <= 1180.0 && Set.copyOf(node.stablePhases).equals(Set.of("FCC_A1", "LIQUID"))) {
                foundOcConfirmedLiquidus = true;
            }
        }
        assertTrue(foundOcConfirmedLiquidus,
                "CalculationSession.calculatePhaseDiagram should reach OC's own confirmed "
                + "liquidus crossing near T=1176.13K");
    }

    @Test
    void calculatePhaseDiagramOneAxisRunsTheStepBranch() throws IOException {
        CalculationSession session = new CalculationSession();
        session.setModel("data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        session.calculatePhaseDiagram(
                new AxisConfig[] { axis }, new double[] { 1150.0 },
                0.0, 101325.0, new double[] { 0.95, 0.05 });

        PhaseDiagramResult result = session.currentPhaseDiagram();
        assertEquals(1, result.numAxes());

        boolean foundOcConfirmedLiquidus = false;
        for (PhaseDiagramResult.NodePoint node : result.getNodes()) {
            double t = node.axisValues[0];
            if (t > 1176.0 && t < 1177.0 && Set.copyOf(node.stablePhases).equals(Set.of("FCC_A1", "LIQUID"))) {
                foundOcConfirmedLiquidus = true;
            }
        }
        assertTrue(foundOcConfirmedLiquidus,
                "CalculationSession.calculatePhaseDiagram's 1-axis (STEP) branch should reach "
                + "OC's own confirmed liquidus crossing at T=1176.13K");
    }

    @Test
    void calculatePhaseDiagramRejectsANonCompositionReleaseAxis() throws IOException {
        CalculationSession session = new CalculationSession();
        session.setModel("data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig badReleaseAxis = new AxisConfig("P / Pa", AxisConfig.Type.PRESSURE, 1e5, 2e5, 1e4);

        assertThrows(IllegalArgumentException.class, () -> session.calculatePhaseDiagram(
                new AxisConfig[] { walkAxis, badReleaseAxis },
                new double[] { 1150.0, 1e5 },
                1150.0, 101325.0, new double[] { 0.95, 0.05 }));
    }

    @Test
    void calculatePhaseDiagramRejectsAnUnsupportedAxisCount() throws IOException {
        CalculationSession session = new CalculationSession();
        session.setModel("data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        assertThrows(IllegalArgumentException.class, () -> session.calculatePhaseDiagram(
                new AxisConfig[0], new double[0], 1150.0, 101325.0, new double[] { 0.95, 0.05 }));
    }

    @Test
    void calculatePhaseDiagramRequiresSetModelFirst() {
        CalculationSession session = new CalculationSession();
        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        assertThrows(IllegalStateException.class, () -> session.calculatePhaseDiagram(
                new AxisConfig[] { axis }, new double[] { 1150.0 },
                0.0, 101325.0, new double[] { 0.95, 0.05 }));
    }
}
