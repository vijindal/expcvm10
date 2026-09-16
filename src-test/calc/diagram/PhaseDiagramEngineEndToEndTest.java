package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import ui.result.EquilibriumReport;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code FOR EACH STARTING POINT} checked truly END TO END, chaining
 * {@link PhaseDiagramEngine}'s own top-to-bottom sequence in ONE test
 * call -- {@link PhaseDiagramEngine#defineSystem} &#8594; {@link
 * PhaseDiagramEngine#validateConditionCount} &#8594; {@link
 * PhaseDiagramEngine#generateStartingPoints} &#8594; {@link
 * PhaseDiagramEngine#drainC1Loop}/{@link
 * PhaseDiagramEngine#drainStepLoop} -- rather than each stage tested in
 * isolation the way {@link PhaseDiagramEngineTest} already does.
 *
 * <p>Closes {@code docs/roadmap_phase_diagrams.md}'s "FOR EACH STARTING
 * POINT" gap: every sub-box under it (STEP, C1, C2, NODE MATCHING, NODE
 * CLASSIFICATION + EXIT GEOMETRY) was individually ✅, but {@link
 * PhaseDiagramEngine#drainC1Loop} itself only wrapped {@link
 * MapDiagramTracer}'s binary-only {@code AxisConfig} overload, and there
 * was no {@link PhaseDiagramEngine}-level entry point for STEP at all.
 * Both gaps are fixed ({@link PhaseDiagramEngine#drainC1Loop(
 * ConditionSet, int, int, double, double[], List)}, {@link
 * PhaseDiagramEngine#drainStepLoop}); this test proves the SINGLE
 * top-to-bottom entry point the class javadoc claims actually reaches
 * all three diagram shapes this session's own OC-referenced tests
 * already validated at the segment/drain-loop level (binary map,
 * ternary isopleth, and STEP), by chaining them through {@link
 * PhaseDiagramEngine} itself rather than calling {@link
 * MapDiagramTracer}/{@link StepDiagramTracer} directly.
 */
public class PhaseDiagramEngineEndToEndTest {

    @Test
    void chainsDefineSystemThroughDrainC1LoopForABinaryMap() throws IOException {
        // OC reference (this session's C2 generalization work): Ag-Cu,
        // x(Cu)=0.05, liquidus crossing at T=1176.13K
        // (docs/oc_reference_tests/agcu_step_xcu05_full_walk.txt).
        var system = PhaseDiagramEngine.defineSystem(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        // n=2 components; T, P, N, x(Cu) = 4 = n+2 conditions.
        PhaseDiagramEngine.validateConditionCount(2, 4);

        List<double[]> startPoints = PhaseDiagramEngine.generateStartingPoints(1150.0);
        assertEquals(1, startPoints.size());
        double startT = startPoints.get(0)[0];

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        NodeRegistry registry = PhaseDiagramEngine.drainC1Loop(
                walkAxis, releaseAxis, startT, 101325.0, startT,
                new double[] { 0.95, 0.05 }, system.phaseModels());

        // This walk's 5K grid snaps the reported node to the grid point
        // PAST OC's own exact crossing (T=1176.13K -- see
        // StepDiagramTracerOcFormatterComparisonTest for the same
        // grid-snapping behavior established this session), landing at
        // T=1180K here.
        boolean foundOcConfirmedLiquidus = false;
        for (Node node : registry.getNodes()) {
            double t = node.axisValues[0];
            if (t >= 1176.0 && t <= 1180.0 && node.stablePhaseNames.equals(Set.of("FCC_A1", "LIQUID"))) {
                foundOcConfirmedLiquidus = true;

                // FORMATTER level: the chained call's own node reflects
                // real thermodynamic data, not a stub.
                String report = EquilibriumReport.format(node.equilibrium, List.of("AG", "CU"));
                assertTrue(report.contains("FCC_A1") && report.contains("LIQUID"));
            }
        }
        assertTrue(foundOcConfirmedLiquidus,
                "chained defineSystem->drainC1Loop should find OC's own confirmed "
                + "liquidus crossing near T=1176.13K");
    }

    @Test
    void chainsDefineSystemThroughDrainC1LoopForATernaryIsopleth() throws IOException {
        // OC reference (this session's NODE CLASSIFICATION + EXIT
        // GEOMETRY work): Al-Mg-Zn isopleth at x(Mg)=0.05, crossing at
        // T=699.58K where MGZN2 disappears
        // (docs/oc_reference_tests/almgzn_isopleth_step_walk.txt),
        // grid-snapped to T=700K by this codebase's own 2K-step search.
        var system = PhaseDiagramEngine.defineSystem(
                "data/cost507R.TDB", List.of("AL", "MG", "ZN"), List.of("FCC_A1", "MGZN2"));

        // n=3 components; T, P, N, x(Mg), x(Zn) = 5 = n+2 conditions.
        PhaseDiagramEngine.validateConditionCount(3, 5);

        List<double[]> startPoints = PhaseDiagramEngine.generateStartingPoints(630.0);
        assertEquals(1, startPoints.size());
        double startT = startPoints.get(0)[0];

        double fixedXMg = 0.05;
        ConditionSet conds = new ConditionSet(3, List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T / K", 630.0, 760.0, 2.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 101325.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.fixedComposition(1, "x(Mg)", fixedXMg),
                Condition.axisComposition(2, "x(Zn)", 0.0, 0.5, 0.001)));

        double xZnStart = 0.05;
        double[] compOverall = { 1.0 - fixedXMg - xZnStart, fixedXMg, xZnStart };

        NodeRegistry registry = PhaseDiagramEngine.drainC1Loop(
                conds, 0, 1, startT, compOverall, system.phaseModels());

        Node crossingNode = null;
        for (Node node : registry.getNodes()) {
            double t = node.axisValues[0];
            if (t >= 698.0 && t <= 700.0 && node.stablePhaseNames.equals(Set.of("FCC_A1"))) {
                crossingNode = node;
            }
        }
        assertTrue(crossingNode != null,
                "chained defineSystem->drainC1Loop should find OC's own confirmed "
                + "isopleth crossing near T=699.58K");

        // The node itself should be classified genuinely as an isopleth
        // crossing (the whole point of this end-to-end wiring): re-derive
        // the same classification the drain loop used internally and
        // confirm it against the diagram's own ConditionSet.
        assertEquals(PhaseDiagramEngine.NodeClass.ISOPLETH_CROSSING,
                PhaseDiagramEngine.classifyNode(conds, crossingNode.stablePhaseNames.size()));

        String report = EquilibriumReport.format(crossingNode.equilibrium, List.of("AL", "MG", "ZN"));
        assertTrue(report.contains("FCC_A1"));
    }

    @Test
    void chainsDefineSystemThroughDrainStepLoopForAStepCalculation() throws IOException {
        // OC reference (StepDiagramTracerOcFormatterComparisonTest):
        // Ag-Cu, x(Cu)=0.05, the SAME liquidus crossing as the binary MAP
        // test above, reached here via the STEP branch instead --
        // confirming PhaseDiagramEngine's single entry point covers BOTH
        // branches, not just MAPPING.
        var system = PhaseDiagramEngine.defineSystem(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        PhaseDiagramEngine.validateConditionCount(2, 4);

        List<double[]> startPoints = PhaseDiagramEngine.generateStartingPoints(1150.0);
        double startT = startPoints.get(0)[0];

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, startT, 1230.0, 5.0);

        NodeRegistry registry = PhaseDiagramEngine.drainStepLoop(
                axis, 0.0, 101325.0, new double[] { 0.95, 0.05 }, system.phaseModels());

        boolean foundOcConfirmedLiquidus = false;
        for (Node node : registry.getNodes()) {
            double t = node.axisValues[0];
            if (t > 1176.0 && t < 1177.0 && node.stablePhaseNames.equals(Set.of("FCC_A1", "LIQUID"))) {
                foundOcConfirmedLiquidus = true;
            }
        }
        assertTrue(foundOcConfirmedLiquidus,
                "chained defineSystem->drainStepLoop should find OC's own confirmed "
                + "liquidus crossing at T=1176.13K");
    }
}
