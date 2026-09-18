package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-checks {@link PhaseDiagramEngine#traceAlgorithmB} (fresh Algorithm B,
 * pre-C1) against real OpenCalphad {@code step}/{@code map} runs, at 4
 * conditions across 2 systems.
 *
 * <p>OC references: {@code docs/oc_reference_tests/agcu_step_xcu05_full_walk.txt},
 * {@code agcu_full_map_clean_xcu05_output.txt}, {@code almgzn_isopleth_step_walk.txt},
 * {@code agcu_eutectic_lr_t1055.9.txt}.
 */
public class PhaseDiagramEngineAlgorithmBOcComparisonTest {

    private static final double MU_RELATIVE_TOLERANCE = 1.0e-3;
    private static final double COMPOSITION_ABSOLUTE_TOLERANCE = 1.0e-3;
    private static final double T_ABSOLUTE_TOLERANCE = 0.01;

    private static List<GibbsEnergyModel> agCu() throws IOException {
        return ThermodynamicSystem.build("data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"))
                .phaseModels();
    }

    private static List<GibbsEnergyModel> alMgZn() throws IOException {
        return ThermodynamicSystem.build(
                "data/cost507R.TDB", List.of("AL", "MG", "ZN"), List.of("FCC_A1", "MGZN2")).phaseModels();
    }

    /** Prints a {@link PhaseDiagramEngine.DiagramResult}'s full contents for manual inspection. */
    private static void printDiagram(String label, PhaseDiagramEngine.DiagramResult diagram) {
        System.out.println("==== " + label + " ====");
        System.out.println("nodes=" + diagram.nodes.size()
                + " lines=" + diagram.lines.size()
                + " equilibriaBuffer=" + diagram.equilibriaBuffer.size());
        for (int i = 0; i < diagram.nodes.size(); i++) {
            PhaseDiagramEngine.DiagramNode node = diagram.nodes.get(i);
            printEquilibrium("  node[" + i + "].equilibrium", node.equilibrium);
            for (int j = 0; j < node.exits.size(); j++) {
                PhaseDiagramEngine.DiagramExit exit = node.exits.get(j);
                System.out.println("  node[" + i + "].exits[" + j + "]: done=" + exit.done
                        + " fixedPhase=" + exit.fixedPhase
                        + " forbiddenPhase=" + exit.forbiddenPhase
                        + " initialAxis=" + exit.initialAxis
                        + " direction=" + exit.direction);
            }
        }
        System.out.println();
    }

    private static void printEquilibrium(String label, PhaseDiagramEngine.DiagramEquilibrium eq) {
        System.out.println(label + ": T=" + eq.T + " P=" + eq.P
                + " converged=" + eq.converged + " globallyStable=" + eq.globallyStable
                + " stablePhases=" + eq.stablePhases
                + " chemicalPotentials=" + java.util.Arrays.toString(eq.chemicalPotentials));
        for (String phase : eq.stablePhases) {
            System.out.println("    " + phase
                    + ": amount=" + eq.phaseAmounts.get(phase)
                    + " totalMoles=" + eq.phaseTotalMoles.get(phase)
                    + " x=" + java.util.Arrays.toString(eq.phaseMoleFractions.get(phase))
                    + " y=" + java.util.Arrays.toString(eq.phaseConstitutions.get(phase)));
        }
    }

    // ------------------------------------------------------------------
    // 1) Ag-Cu STEP: T=[1150,1230,5], x(Cu)=0.05 fixed.
    // OC (agcu_step_xcu05_full_walk.txt): T=1150K single-phase FCC_A1,
    // x(AG)=0.95, x(CU)=0.05, GS=-6.7882826E+04 J/mol (the STEP file's own
    // "c e" result right before "set ax 1 t 1150 1230 5" / "step" --
    // deliberately NOT agcu_full_map_clean_xcu05_output.txt's own "l r 2"
    // dump at the same nominal T=1150/x(Cu)=.05, which reports a different
    // G/N=-6.8450E4: that file's "c e" logs "Composition set(s) created: 1"
    // before its dump, unlike this STEP file's plain "c e", so the two
    // are not the same converged state despite identical conditions).
    // ------------------------------------------------------------------
    @Test
    void agCuStepNode0MatchesOcsInitialEquilibrium() throws IOException {
        List<Condition> conditions = List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 1150.0, 1230.0, 5.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 100000.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.fixedComposition(1, "x(Cu)", 0.05));
        ConditionSet conds = new ConditionSet(2, conditions);

        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1150.0 }, agCu());
        printDiagram("1) agCuStepNode0MatchesOcsInitialEquilibrium", diagram);

        assertEquals(1, diagram.nodes.size());
        PhaseDiagramEngine.DiagramNode node0 = diagram.nodes.get(0);

        assertEquals(1150.0, node0.equilibrium.T, T_ABSOLUTE_TOLERANCE);
        assertEquals(Set.of("FCC_A1"), node0.equilibrium.stablePhases);
        assertTrue(node0.equilibrium.converged);

        double[] x = node0.equilibrium.phaseMoleFractions.get("FCC_A1");
        assertEquals(0.95, x[0], COMPOSITION_ABSOLUTE_TOLERANCE, "x(AG) vs OC");
        assertEquals(0.05, x[1], COMPOSITION_ABSOLUTE_TOLERANCE, "x(CU) vs OC");

        // OC's own GS at T=1150K (step trace, not the map file's dump --
        // see note above); N=1 so this is directly G/N.
        double ocGPerMol = -6.7882826e4;
        double gPerMol = node0.equilibrium.chemicalPotentials[0] * x[0]
                + node0.equilibrium.chemicalPotentials[1] * x[1];
        assertEquals(ocGPerMol, gPerMol, Math.abs(ocGPerMol) * MU_RELATIVE_TOLERANCE, "G/N vs OC");

        // Sundman 2021 3.2: step's node0 gets 2 exits, +1/-1, no fixed phase.
        assertEquals(2, node0.exits.size());
        for (PhaseDiagramEngine.DiagramExit exit : node0.exits) {
            assertNull(exit.fixedPhase);
            assertNull(exit.forbiddenPhase);
            assertEquals(0, exit.initialAxis);
            assertTrue(!exit.done);
        }
        assertEquals(Set.of(1, -1), Set.of(node0.exits.get(0).direction, node0.exits.get(1).direction));
    }

    // ------------------------------------------------------------------
    // 2) Ag-Cu MAP: axis0=T[800,1500,10] (walked), axis1=x(Cu)[.,.,.] (released),
    // start T=1150K, x(Cu)=0.05. OC (agcu_full_map_clean_xcu05_output.txt):
    // "New line: 1 T= 1176.13 with: LIQUID + FCC_A1#1" -- the crossing search
    // finds LIQUID appearing at T=1176.13K.
    // ------------------------------------------------------------------
    @Test
    void agCuMapNode0MatchesOcsOwnFirstCrossing() throws IOException {
        // OC's own single-point calculation ("set cond t=1150 ... c e")
        // precedes "set ax 1 x(cu) ... / set ax 2 t ... / map" -- T=1150,
        // x(Cu)=0.05 is the ALREADY-CONVERGED starting equilibrium B is
        // handed, supplied explicitly below via startAxisValues, not read
        // off either axis's own min/max plot bound (see the axis's own
        // 800..1500 range: min is deliberately NOT 1150 here, so a test
        // that silently fell back to axis.min would fail this assertion).
        List<Condition> conditions = List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 800.0, 1500.0, 1.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 100000.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(1, "x(Cu)", 0.0, 1.0, 0.025));
        ConditionSet conds = new ConditionSet(2, conditions);

        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1150.0, 0.05 }, agCu());
        printDiagram("2) agCuMapNode0MatchesOcsOwnFirstCrossing", diagram);

        assertEquals(1, diagram.nodes.size());
        PhaseDiagramEngine.DiagramNode node0 = diagram.nodes.get(0);

        // OC's own map trace: "New line: 1 T= 1176.13 with: LIQUID + FCC_A1#1".
        // This codebase's own 1K search step overshoots to the first point
        // PAST the crossing (1177K), same bracket relationship documented
        // in StepDiagramTracerOcFormatterComparisonTest.
        assertEquals(1176.13, node0.equilibrium.T, 1.0,
                "crossing T should match OC's own map trace within one search step");
        assertEquals(Set.of("LIQUID", "FCC_A1"), node0.equilibrium.stablePhases);

        // Sundman 2021 3.3: MAP's node0 gets 2 exits along the OTHER axis
        // (x(Cu), index 1), both fixing the phase that appeared (LIQUID).
        assertEquals(2, node0.exits.size());
        for (PhaseDiagramEngine.DiagramExit exit : node0.exits) {
            assertEquals("LIQUID", exit.fixedPhase);
            assertEquals(1, exit.initialAxis);
            assertTrue(!exit.done);
        }
        assertEquals(Set.of(1, -1), Set.of(node0.exits.get(0).direction, node0.exits.get(1).direction));
    }

    // ------------------------------------------------------------------
    // 3) Al-Mg-Zn STEP (isopleth): T=[630,760,2], x(Mg)=0.05, x(Zn)=0.05 fixed.
    // OC (almgzn_isopleth_step_walk.txt): "Line 1 from 630.0 with: FCC_A1#1
    // MGZN2" -- equilibrium0 is already 2-phase at T=630K.
    // ------------------------------------------------------------------
    @Test
    void alMgZnStepNode0MatchesOcsInitialTwoPhaseEquilibrium() throws IOException {
        List<Condition> conditions = List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 630.0, 760.0, 2.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 100000.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.fixedComposition(1, "x(Mg)", 0.05),
                Condition.fixedComposition(2, "x(Zn)", 0.05));
        ConditionSet conds = new ConditionSet(3, conditions);

        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 630.0 }, alMgZn());
        printDiagram("3) alMgZnStepNode0MatchesOcsInitialTwoPhaseEquilibrium", diagram);

        assertEquals(1, diagram.nodes.size());
        PhaseDiagramEngine.DiagramNode node0 = diagram.nodes.get(0);

        assertEquals(630.0, node0.equilibrium.T, T_ABSOLUTE_TOLERANCE);
        assertEquals(Set.of("FCC_A1", "MGZN2"), node0.equilibrium.stablePhases);
        assertTrue(node0.equilibrium.converged);

        assertEquals(2, node0.exits.size());
        for (PhaseDiagramEngine.DiagramExit exit : node0.exits) {
            assertNull(exit.fixedPhase);
            assertEquals(0, exit.initialAxis);
        }
    }

    // ------------------------------------------------------------------
    // 4) Ag-Cu point check via callAlgorithmA directly (not a full B trace) --
    // Algorithm A's own conversion correctness at a distinct 2-phase
    // condition. OC (agcu_step_xcu05_full_walk.txt lines 241-271): T=1207K,
    // LIQUID#1 (0.958 f.u., x(AG)=0.948876) + FCC_A1#1 (0.04201 f.u.,
    // x(AG)=0.975632).
    // ------------------------------------------------------------------
    @Test
    void agCuTwoPhasePointMatchesOc() throws IOException {
        List<Condition> conditions = List.of(
                Condition.fixed(Condition.Variable.TEMPERATURE, "T", 1207.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 100000.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.fixedComposition(1, "x(Cu)", 0.05));
        ConditionSet conds = new ConditionSet(2, conditions);

        PhaseDiagramEngine.DiagramEquilibrium eq = PhaseDiagramEngine.callAlgorithmA(
                conds, new double[0], agCu());
        printEquilibrium("4) agCuTwoPhasePointMatchesOc", eq);

        assertTrue(eq.converged);
        assertEquals(Set.of("LIQUID", "FCC_A1"), eq.stablePhases);

        double liquidAmount = eq.phaseAmounts.get("LIQUID");
        double[] liquidX = eq.phaseMoleFractions.get("LIQUID");
        assertEquals(0.958, liquidAmount, 2.0e-3, "LIQUID amount vs OC");
        assertEquals(0.948876, liquidX[0], COMPOSITION_ABSOLUTE_TOLERANCE, "LIQUID x(AG) vs OC");

        double fccAmount = eq.phaseAmounts.get("FCC_A1");
        double[] fccX = eq.phaseMoleFractions.get("FCC_A1");
        assertEquals(0.04201, fccAmount, 2.0e-3, "FCC_A1 amount vs OC");
        assertEquals(0.975632, fccX[0], COMPOSITION_ABSOLUTE_TOLERANCE, "FCC_A1 x(AG) vs OC");
    }

    // ------------------------------------------------------------------
    // 5) Regression: B's node0 must start from the CALLER'S initial
    // equilibrium condition, not from the axis's own min/max plot bound.
    // Same axis range as test 1 (T=[1150,1230]) but the initial condition
    // is T=1207K (LIQUID+FCC_A1, per test 4/OC), deliberately NOT the axis
    // minimum -- a startAxisValues=axis.min regression would instead solve
    // at T=1150K (single-phase FCC_A1) and fail every assertion below.
    // ------------------------------------------------------------------
    @Test
    void stepNode0StartsFromCallersInitialConditionNotAxisMin() throws IOException {
        List<Condition> conditions = List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 1150.0, 1230.0, 5.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 100000.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.fixedComposition(1, "x(Cu)", 0.05));
        ConditionSet conds = new ConditionSet(2, conditions);

        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1207.0 }, agCu());
        printDiagram("5) stepNode0StartsFromCallersInitialConditionNotAxisMin", diagram);

        assertEquals(1, diagram.nodes.size());
        PhaseDiagramEngine.DiagramNode node0 = diagram.nodes.get(0);

        assertEquals(1207.0, node0.equilibrium.T, T_ABSOLUTE_TOLERANCE,
                "node0 should sit at the caller's initial T=1207K, not axis.min=1150K");
        assertEquals(Set.of("LIQUID", "FCC_A1"), node0.equilibrium.stablePhases,
                "T=1207K is two-phase (test 4); axis.min=1150K would wrongly report single-phase FCC_A1");
    }

    // ------------------------------------------------------------------
    // 6) Regression: the walked axis is chosen by CONDITION ROLE (the
    // potential, T or P), not by array position -- both orderings of the
    // same 2 axis conditions must walk T and release x(Cu), landing on
    // the SAME crossing OC reports (test 2's own bracket).
    // ------------------------------------------------------------------
    @Test
    void mapWalksThePotentialAxisRegardlessOfConditionOrder() throws IOException {
        Condition tAxis = Condition.axis(Condition.Variable.TEMPERATURE, "T", 800.0, 1500.0, 1.0);
        Condition xCuAxis = Condition.axisComposition(1, "x(Cu)", 0.0, 1.0, 0.025);
        Condition p = Condition.fixed(Condition.Variable.PRESSURE, "P", 100000.0);
        Condition n = Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0);

        ConditionSet tFirst = new ConditionSet(2, List.of(tAxis, p, n, xCuAxis));
        ConditionSet xCuFirst = new ConditionSet(2, List.of(xCuAxis, p, n, tAxis));

        // startAxisValues is in each ConditionSet's OWN axisConditions()
        // order -- (T, x(Cu)) for tFirst, (x(Cu), T) for xCuFirst.
        PhaseDiagramEngine.DiagramResult diagramTFirst = PhaseDiagramEngine.traceAlgorithmB(
                tFirst, new double[] { 1150.0, 0.05 }, agCu());
        PhaseDiagramEngine.DiagramResult diagramXCuFirst = PhaseDiagramEngine.traceAlgorithmB(
                xCuFirst, new double[] { 0.05, 1150.0 }, agCu());
        printDiagram("6a) mapWalksThePotentialAxis (T first)", diagramTFirst);
        printDiagram("6b) mapWalksThePotentialAxis (x(Cu) first)", diagramXCuFirst);

        PhaseDiagramEngine.DiagramNode nodeTFirst = diagramTFirst.nodes.get(0);
        PhaseDiagramEngine.DiagramNode nodeXCuFirst = diagramXCuFirst.nodes.get(0);

        assertEquals(nodeTFirst.equilibrium.T, nodeXCuFirst.equilibrium.T, T_ABSOLUTE_TOLERANCE,
                "both condition orderings should walk T and find the same crossing");
        assertEquals(Set.of("LIQUID", "FCC_A1"), nodeTFirst.equilibrium.stablePhases);
        assertEquals(Set.of("LIQUID", "FCC_A1"), nodeXCuFirst.equilibrium.stablePhases);

        // Both orderings' exits vary x(Cu) (the released/other axis), not T --
        // the exit's initialAxis is an index into ITS OWN ConditionSet's
        // axisConditions(), so this differs (1 vs 0) precisely because the
        // conditions were listed in opposite order.
        for (PhaseDiagramEngine.DiagramExit exit : nodeTFirst.exits) {
            assertEquals(1, exit.initialAxis, "tFirst: exits vary x(Cu), the axis AFTER T in this ordering");
        }
        for (PhaseDiagramEngine.DiagramExit exit : nodeXCuFirst.exits) {
            assertEquals(0, exit.initialAxis, "xCuFirst: exits vary x(Cu), the axis BEFORE T in this ordering");
        }
    }
}
