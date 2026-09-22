package session;

import calc.diagram.AxisConfig;
import calc.diagram.PhaseDiagramResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CalculationSession#calculatePhaseDiagram} -- the UI-facing entry
 * point wiring {@link calc.diagram.PhaseDiagramEngine}'s full flowchart
 * sequence (defineSystem is skipped here since {@link
 * CalculationSession#setModel} already built the system; {@code
 * validateConditionCount -> generateStartingPoints -> drainMapLoop/
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
}
