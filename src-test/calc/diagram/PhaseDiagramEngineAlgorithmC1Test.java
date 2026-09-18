package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dedicated tests for {@link PhaseDiagramEngine}'s Algorithm C1 (the STEP
 * exit-walking loop), cross-checked against OC's own line-by-line log in
 * {@code docs/oc_reference_tests/agcu_step_xcu05_full_walk.txt}.
 */
public class PhaseDiagramEngineAlgorithmC1Test {

    private static List<GibbsEnergyModel> agCu() throws IOException {
        return ThermodynamicSystem.build("data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"))
                .phaseModels();
    }

    private static ConditionSet agCuStepConditions(double axisMin, double axisMax, double step) {
        List<Condition> conditions = List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", axisMin, axisMax, step),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 100000.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.fixedComposition(1, "x(Cu)", 0.05));
        return new ConditionSet(2, conditions);
    }

    /** Prints a {@link PhaseDiagramEngine.DiagramResult}'s full contents for manual inspection. */
    private static void printDiagram(String label, PhaseDiagramEngine.DiagramResult diagram) {
        System.out.println("==== " + label + " ====");
        System.out.println("nodes=" + diagram.nodes.size()
                + " lines=" + diagram.lines.size()
                + " equilibriaBuffer=" + diagram.equilibriaBuffer.size());
        for (int i = 0; i < diagram.nodes.size(); i++) {
            PhaseDiagramEngine.DiagramNode node = diagram.nodes.get(i);
            System.out.println("  node[" + i + "]: T=" + node.equilibrium.T
                    + " stablePhases=" + node.equilibrium.stablePhases);
            for (int j = 0; j < node.exits.size(); j++) {
                PhaseDiagramEngine.DiagramExit exit = node.exits.get(j);
                System.out.println("  node[" + i + "].exits[" + j + "]: done=" + exit.done
                        + " fixedPhase=" + exit.fixedPhase
                        + " forbiddenPhase=" + exit.forbiddenPhase
                        + " initialAxis=" + exit.initialAxis
                        + " direction=" + exit.direction);
            }
        }
        for (int i = 0; i < diagram.lines.size(); i++) {
            PhaseDiagramEngine.DiagramLineResult line = diagram.lines.get(i);
            System.out.println("  line[" + i + "]: terminatedReason=" + line.terminatedReason
                    + " equilibria=" + line.equilibria.size());
            for (PhaseDiagramEngine.DiagramEquilibrium eq : line.equilibria) {
                System.out.println("    T=" + eq.T + " stablePhases=" + eq.stablePhases
                        + " converged=" + eq.converged + " globallyStable=" + eq.globallyStable);
            }
        }
        System.out.println();
    }

    // ------------------------------------------------------------------
    // OC (agcu_step_xcu05_full_walk.txt), T=[1150,1230,5], x(Cu)=0.05:
    // "Line 1 from 1150.0 with: FCC_A1#1 / Creating a node at 1176.13
    // where LIQUID appears / Finishing line with 12 equilibria"
    // "Line 2 from 1150.0 with: FCC_A1#1 / Terminating line with 2
    // equilibria at axis limit 1150.0"
    // ------------------------------------------------------------------
    @Test
    void directionPlusOneStopsAtPhaseChangeDirectionMinusOneStopsAtAxisLimit() throws IOException {
        ConditionSet conds = agCuStepConditions(1150.0, 1230.0, 5.0);

        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1150.0 }, agCu());
        printDiagram("directionPlusOneStopsAtPhaseChangeDirectionMinusOneStopsAtAxisLimit", diagram);

        assertEquals(1, diagram.nodes.size());
        PhaseDiagramEngine.DiagramNode node0 = diagram.nodes.get(0);
        assertEquals(2, node0.exits.size());
        assertEquals(2, diagram.lines.size());

        PhaseDiagramEngine.DiagramExit plusExit = node0.exits.get(0).direction == 1
                ? node0.exits.get(0) : node0.exits.get(1);
        PhaseDiagramEngine.DiagramExit minusExit = plusExit == node0.exits.get(0)
                ? node0.exits.get(1) : node0.exits.get(0);

        // Both exits are resolved by C1 -- one via the (deferred) C2 handoff
        // at a phase change, the other by running off the axis limit.
        assertTrue(plusExit.done);
        assertTrue(minusExit.done);

        PhaseDiagramEngine.DiagramLineResult plusLine = lineStartingFrom(diagram, node0, +1);
        PhaseDiagramEngine.DiagramLineResult minusLine = lineStartingFrom(diagram, node0, -1);

        assertEquals("phase change", plusLine.terminatedReason);
        assertFalse(plusLine.equilibria.isEmpty());
        for (PhaseDiagramEngine.DiagramEquilibrium eq : plusLine.equilibria) {
            assertEquals(Set.of("FCC_A1"), eq.stablePhases,
                    "every saved point on the +1 line stays FCC_A1-only, up to the crossing");
        }
        double lastSavedT = plusLine.equilibria.get(plusLine.equilibria.size() - 1).T;
        assertTrue(lastSavedT < 1176.13, "last saved point should be just below OC's own crossing at T=1176.13");

        assertEquals("axis limit", minusLine.terminatedReason);
        for (PhaseDiagramEngine.DiagramEquilibrium eq : minusLine.equilibria) {
            assertEquals(Set.of("FCC_A1"), eq.stablePhases);
            assertTrue(eq.T >= 1150.0 && eq.T < 1150.0 + 1.0e-6 + 5.0,
                    "T=[1150,1230] leaves only one 5K step below the start before hitting axis min");
        }
    }

    // ------------------------------------------------------------------
    // A narrow axis window with no room to walk in either direction: both
    // exits should terminate at "axis limit" immediately, saving zero
    // equilibria, and C1's search() must still return (not loop forever).
    // ------------------------------------------------------------------
    @Test
    void bothExitsTerminateImmediatelyWhenAxisWindowIsAlreadyExhausted() throws IOException {
        ConditionSet conds = agCuStepConditions(1148.0, 1152.0, 5.0);

        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1150.0 }, agCu());

        assertEquals(1, diagram.nodes.size());
        PhaseDiagramEngine.DiagramNode node0 = diagram.nodes.get(0);
        assertEquals(2, diagram.lines.size());

        for (PhaseDiagramEngine.DiagramExit exit : node0.exits) {
            assertTrue(exit.done);
        }
        for (PhaseDiagramEngine.DiagramLineResult line : diagram.lines) {
            assertEquals("axis limit", line.terminatedReason);
            assertTrue(line.equilibria.isEmpty(),
                    "a single 5K step in either direction already clears [1148,1152]");
        }
    }

    // ------------------------------------------------------------------
    // Sanity check: C1 must actually run inside traceAlgorithmB (not be a
    // dead call) -- diagram.equilibriaBuffer collects every point C1 saved
    // across both lines, and it must be non-empty when there is room to walk.
    // ------------------------------------------------------------------
    @Test
    void equilibriaBufferAccumulatesPointsFromBothLines() throws IOException {
        ConditionSet conds = agCuStepConditions(1150.0, 1230.0, 5.0);

        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1150.0 }, agCu());

        assertFalse(diagram.equilibriaBuffer.isEmpty());

        int summedLineEquilibria = 0;
        for (PhaseDiagramEngine.DiagramLineResult line : diagram.lines) {
            summedLineEquilibria += line.equilibria.size();
        }
        assertEquals(summedLineEquilibria, diagram.equilibriaBuffer.size(),
                "every saved point must be recorded in both its own line and the shared buffer");
    }

    /** Finds the one line in {@code diagram.lines} that started at {@code node0} walking {@code direction}. */
    private static PhaseDiagramEngine.DiagramLineResult lineStartingFrom(
            PhaseDiagramEngine.DiagramResult diagram, PhaseDiagramEngine.DiagramNode node0, int direction) {
        for (PhaseDiagramEngine.DiagramLineResult line : diagram.lines) {
            if (line.startNode != node0) continue;
            if (line.equilibria.isEmpty()) continue;
            double firstT = line.equilibria.get(0).T;
            boolean matchesDirection = direction > 0
                    ? firstT > node0.equilibrium.T
                    : firstT < node0.equilibrium.T;
            if (matchesDirection) return line;
        }
        // No saved equilibria (e.g. immediate axis-limit termination): fall
        // back to matching by exit order (exits[] and lines[] are created
        // in the same +1/-1 order by traceAlgorithmB and callAlgorithmC1).
        int index = direction > 0 ? 0 : 1;
        return diagram.lines.get(index);
    }

    @Test
    void forbiddenPhaseIsNeverUsedOnAStepExit() throws IOException {
        ConditionSet conds = agCuStepConditions(1150.0, 1230.0, 5.0);
        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1150.0 }, agCu());

        PhaseDiagramEngine.DiagramNode node0 = diagram.nodes.get(0);
        for (PhaseDiagramEngine.DiagramExit exit : node0.exits) {
            assertNull(exit.forbiddenPhase);
        }
    }

    // ------------------------------------------------------------------
    // Point-by-point cross-check of the +1 line's saved equilibria against
    // OC's own single-point re-solves at the same T along the walk (same
    // file, "set cond t=1160/1176/1177 ... c e" blocks): OC's G/N values
    // are GS/N since N=1, directly comparable to mu . x here.
    // ------------------------------------------------------------------
    @Test
    void plusOneLineMatchesOcsPerPointGValuesAlongTheWalk() throws IOException {
        ConditionSet conds = agCuStepConditions(1150.0, 1230.0, 5.0);
        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1150.0 }, agCu());

        PhaseDiagramEngine.DiagramLineResult plusLine = lineStartingFrom(diagram, diagram.nodes.get(0), +1);

        // C1 walks every 5K step: 1155, 1160, 1165, 1170, 1175, then detects
        // the crossing at 1180 (not saved) -- matches OC's own "Line 1 ...
        // Creating a node at 1176.13" bracket (1175 single-phase, 1180 two-phase).
        assertEquals(5, plusLine.equilibria.size());
        assertEquals(1155.0, plusLine.equilibria.get(0).T, 1.0e-9);
        assertEquals(1175.0, plusLine.equilibria.get(4).T, 1.0e-9);

        // OC: T=1160, x(Cu)=.05, single-phase FCC_A1, GS=-6.8692808E+04 J/mol.
        PhaseDiagramEngine.DiagramEquilibrium at1160 = plusLine.equilibria.get(1);
        assertEquals(1160.0, at1160.T, 1.0e-9);
        assertEquals(Set.of("FCC_A1"), at1160.stablePhases);
        double[] x1160 = at1160.phaseMoleFractions.get("FCC_A1");
        double gPerMol1160 = at1160.chemicalPotentials[0] * x1160[0] + at1160.chemicalPotentials[1] * x1160[1];
        assertEquals(-6.8692808e4, gPerMol1160, Math.abs(-6.8692808e4) * 1.0e-3, "G/N at T=1160 vs OC");
    }

    // ------------------------------------------------------------------
    // Sundman 2021 3.3: a ZPF line exit ("one phase will be fix with zero
    // amount along the line") has exit.fixedPhase != null; C1 walks it via
    // EquilibriumSolverV2#solveZpf, releasing the diagram's OTHER axis (T)
    // to hold LIQUID at exactly zero amount while x(Cu) is walked directly.
    // OC (agcu_full_map_clean_xcu05_output.txt): node0 at T=1176.13,
    // x(Cu)=0.05, LIQUID+FCC_A1#1 -- "New line: 1 T=1176.13 ... Creating a
    // node at 1056.12 where FCC_A1_AUTO#2 appears" confirms the line
    // survives cleanly at least that far walking x(Cu) upward from 0.05.
    // ------------------------------------------------------------------
    @Test
    void zpfLineExitWalksXCuReleasingTWithLiquidFixedAtExactlyZero() throws IOException {
        List<Condition> conditions = List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 800.0, 1500.0, 1.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 100000.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(1, "x(Cu)", 0.0, 1.0, 0.025));
        ConditionSet conds = new ConditionSet(2, conditions);

        PhaseDiagramEngine.DiagramResult diagram = PhaseDiagramEngine.traceAlgorithmB(
                conds, new double[] { 1150.0, 0.05 }, agCu());
        printDiagram("zpfLineExitWalksXCuReleasingTWithLiquidFixedAtExactlyZero", diagram);

        PhaseDiagramEngine.DiagramNode node0 = diagram.nodes.get(0);
        assertEquals("LIQUID", node0.exits.get(0).fixedPhase);
        assertEquals(1, node0.exits.get(0).initialAxis, "node0's exits walk x(Cu) (axis 1), releasing T");

        PhaseDiagramEngine.DiagramExit plusExit = node0.exits.get(0).direction == 1
                ? node0.exits.get(0) : node0.exits.get(1);

        PhaseDiagramEngine.DiagramLineResult plusLine = null;
        for (PhaseDiagramEngine.DiagramLineResult line : diagram.lines) {
            if (line.startNode == node0 && !line.equilibria.isEmpty()
                    && overallXCu(line.equilibria.get(0)) > 0.05) {
                plusLine = line;
                break;
            }
        }
        assertNotNull(plusLine, "the +1 exit (increasing x(Cu)) should have saved at least one point");
        assertTrue(plusExit.done);

        for (PhaseDiagramEngine.DiagramEquilibrium eq : plusLine.equilibria) {
            assertEquals(Set.of("LIQUID", "FCC_A1"), eq.stablePhases);
            assertEquals(0.0, eq.phaseAmounts.get("LIQUID"), 0.0,
                    "LIQUID must be held at exactly zero amount along a ZPF line");
            assertTrue(eq.T < 1176.13, "T should be released downward as x(Cu) increases from OC's node0");
        }
    }

    /** Overall x(Cu) (component index 1) lever-ruled across {@code eq}'s stable phases. */
    private static double overallXCu(PhaseDiagramEngine.DiagramEquilibrium eq) {
        double atomsAg = 0.0, atomsCu = 0.0;
        for (String phase : eq.stablePhases) {
            double atoms = eq.phaseAmounts.get(phase) * eq.phaseTotalMoles.get(phase);
            double[] x = eq.phaseMoleFractions.get(phase);
            atomsAg += atoms * x[0];
            atomsCu += atoms * x[1];
        }
        return atomsCu / (atomsAg + atomsCu);
    }
}
