package application;

import ui.request.AxisConfig;
import calc.diagram.PhaseDiagramResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApplicationLayer#calculatePhaseDiagram} -- the UI-facing entry
 * point wiring {@link calc.diagram.PhaseDiagramEngine#callAlgorithmB}
 * into {@link ApplicationLayer}.
 *
 * <p>OC reference: the Ag-Cu liquidus crossing at x(Cu)=0.05, T=1176.13K
 * (data/agcu.TDB).
 *
 * <p><strong>Classified as SLOW (full UI layer integration with phase diagram computation).</strong>
 */
@Tag("slow")
public class ApplicationLayerPhaseDiagramTest {

    @Test
    void calculatePhaseDiagramTwoAxesReturnsTheOcConfirmedLiquidus() throws IOException {
        ApplicationLayer session = new ApplicationLayer();
        session.setModel("data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        // startAxes must match compOverall's own x(Cu)=0.05 (per
        // calculatePhaseDiagram's own javadoc: "startAxes supplies each
        // axis's own starting value, same length/order") -- the OLD
        // implementation silently ignored startAxes[1] for the
        // composition axis and always used compOverall instead; the
        // current callAlgorithmB-based implementation honors it, so
        // this call must actually agree with compOverall now.
        session.calculatePhaseDiagram(
                new AxisConfig[] { walkAxis, releaseAxis },
                new double[] { 1150.0, 0.05 },
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
                "ApplicationLayer.calculatePhaseDiagram should reach OC's own confirmed "
                + "liquidus crossing near T=1176.13K");
    }

    @Test
    void calculatePhaseDiagramOneAxisRunsTheStepBranch() throws IOException {
        ApplicationLayer session = new ApplicationLayer();
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
                "ApplicationLayer.calculatePhaseDiagram's 1-axis (STEP) branch should reach "
                + "OC's own confirmed liquidus crossing at T=1176.13K");
    }

    @Test
    void calculatePhaseDiagramRejectsANonCompositionReleaseAxis() throws IOException {
        ApplicationLayer session = new ApplicationLayer();
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
        ApplicationLayer session = new ApplicationLayer();
        session.setModel("data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        assertThrows(IllegalArgumentException.class, () -> session.calculatePhaseDiagram(
                new AxisConfig[0], new double[0], 1150.0, 101325.0, new double[] { 0.95, 0.05 }));
    }

    @Test
    void calculatePhaseDiagramRequiresSetModelFirst() {
        ApplicationLayer session = new ApplicationLayer();
        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        assertThrows(IllegalStateException.class, () -> session.calculatePhaseDiagram(
                new AxisConfig[] { axis }, new double[] { 1150.0 },
                0.0, 101325.0, new double[] { 0.95, 0.05 }));
    }
}
