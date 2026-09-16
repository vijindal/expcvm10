package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;
import ui.result.EquilibriumReport;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StepDiagramTracer}'s C1 drain loop for STEP -- closes {@code
 * docs/roadmap_phase_diagrams.md}'s "StepTracer.trace exists, not wired
 * into Node / NodeRegistry network" gap.
 *
 * <p>OC reference (real {@code oc7C} run via the pty driver, session
 * capture {@code docs/oc_reference_tests/agcu_step_xcu05_full_walk.txt}):
 * Ag-Cu, x(Cu)=0.05 fixed, T stepped 1150-1230K in 5K increments. OC's own
 * {@code step} console trace reports two nodes -- T=1176.13K where LIQUID
 * appears, T=1207.60K where FCC_A1#1 disappears -- bracketed here by
 * direct point solves at T=1176/1177K and T=1207/1208K.
 */
public class StepDiagramTracerTest {

    private static Set<String> stableNames(EquilibriumResult r) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : r.getStablePhases()) names.add(pr.phaseName);
        return names;
    }

    @Test
    void drainProducesStartNodeWithTwoExits() throws IOException {
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        NodeRegistry registry = new StepDiagramTracer().drain(
                axis, 0.0, 101325.0, new double[] { 0.95, 0.05 }, candidates);

        Node start = registry.getNodes().get(0);
        assertEquals(Set.of("FCC_A1"), start.stablePhaseNames);
        assertEquals(2, start.getLines().size(), "the START node should have exactly 2 exits (one per direction)");
    }

    @Test
    void drainFindsBothOcConfirmedCrossings() throws IOException {
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        NodeRegistry registry = new StepDiagramTracer().drain(
                axis, 0.0, 101325.0, new double[] { 0.95, 0.05 }, candidates);

        boolean sawLiquidusCrossing = false;
        boolean sawSolidusCrossing = false;

        for (Node node : registry.getNodes()) {
            double t = node.axisValues[0];
            if (t > 1176.0 && t < 1177.0 && node.stablePhaseNames.equals(Set.of("FCC_A1", "LIQUID"))) {
                sawLiquidusCrossing = true;
                // A mid-walk crossing node should have exactly 1 exit
                // (STEP_CONTINUATION), not 2 -- distinguishes it from the START node.
                assertEquals(1, node.getLines().size(),
                        "a mid-walk STEP crossing node should have exactly 1 continuation exit");
            }
            if (t > 1207.0 && t < 1208.0 && node.stablePhaseNames.equals(Set.of("LIQUID"))) {
                sawSolidusCrossing = true;
            }
        }

        assertTrue(sawLiquidusCrossing,
                "should find the OC-confirmed FCC_A1->FCC_A1+LIQUID crossing at T=[1176,1177]");
        assertTrue(sawSolidusCrossing,
                "should find the OC-confirmed FCC_A1+LIQUID->LIQUID crossing at T=[1207,1208] "
                + "(OC's own step trace: \"Creating a node at 1207.60 where FCC_A1#1 disappear\")");
    }

    @Test
    void nodeEquilibriumFeedsEquilibriumReportDirectly() throws IOException {
        // Confirms the actual point of this wiring: a STEP-produced Node's
        // stored EquilibriumResult works with EquilibriumReport exactly
        // like a MAP-produced node's does -- no STEP-specific formatter code.
        ThermodynamicSystem system = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));
        List<GibbsEnergyModel> candidates = system.phaseModels();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        NodeRegistry registry = new StepDiagramTracer().drain(
                axis, 0.0, 101325.0, new double[] { 0.95, 0.05 }, candidates);

        Node liquidusNode = null;
        for (Node node : registry.getNodes()) {
            if (node.stablePhaseNames.equals(Set.of("FCC_A1", "LIQUID"))) {
                liquidusNode = node;
                break;
            }
        }
        assertTrue(liquidusNode != null, "should have found the FCC_A1+LIQUID crossing node");

        String report = EquilibriumReport.format(liquidusNode.equilibrium, system.elements());

        assertTrue(report.contains("FCC_A1") && report.contains("LIQUID"));
        assertTrue(report.contains("AG") && report.contains("CU"));
        assertTrue(report.contains("amount="));
    }

    @Test
    void wholeLinesCarryFullEquilibriumHistoryNotJustEndpoints() throws IOException {
        // The point of routing StepTracer through Node/Line rather than
        // collapsing to PhaseDiagramResult inline: every WALKED point
        // (not just crossings) keeps its full EquilibriumResult.
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        NodeRegistry registry = new StepDiagramTracer().drain(
                axis, 0.0, 101325.0, new double[] { 0.95, 0.05 }, candidates);

        Node start = registry.getNodes().get(0);
        Line forwardLine = null;
        for (Line l : start.getLines()) {
            if (l.direction == +1) forwardLine = l;
        }
        assertTrue(forwardLine != null);
        List<EquilibriumResult> points = forwardLine.getPoints();
        assertTrue(points.size() >= 3,
                "the forward line from the start node should have walked several interior points");

        // Every point converges; every INTERIOR point (all but the final
        // one, which is the line's own crossing/termination point with
        // the NEW stable set -- same convention as MapTracer.SegmentResult)
        // is still single-phase FCC_A1.
        for (int i = 0; i < points.size(); i++) {
            EquilibriumResult pt = points.get(i);
            assertTrue(pt.isConverged());
            if (i < points.size() - 1) {
                assertEquals(Set.of("FCC_A1"), stableNames(pt), "interior point " + i);
            }
        }
    }
}
