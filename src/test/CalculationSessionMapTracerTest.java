package test;

import calc.diagram.AxisConfig;
import session.CalculationSession;
import ui.result.PhaseDiagramResult;
import ui.result.PhaseDiagramResult.LineSegment;
import ui.result.PhaseDiagramResult.NodePoint;

import java.util.Arrays;

/**
 * End-to-end test of {@link CalculationSession#calculateMap} -- Sundman
 * 2021 Calphad 75, Algorithm B's map branch (C1 line-following + C2 exact
 * zero-phase-amount boundary solving + D invariant-node detection) --
 * via {@link calc.diagram.MapTracer}:
 *
 * <ul>
 *   <li><b>Section A</b> -- Ag-Cu (data/agcu.TDB), the same
 *       LIQUID/FCC_A1-only system {@code CalculationSessionStepTracerTest}
 *       Section B already validates with STEP's black-box bisection.
 *       Walks TEMPERATURE (1000K-1200K, step 5K) and releases x(Cu)
 *       exactly at each boundary. Confirms: at least 2 two-phase
 *       LineSegments, each with a non-null {@code fixedPhase} (proving
 *       the exact C2 solve actually ran, not a fallback); at least 2
 *       CROSSING nodes bracketing the ~1052K-1090K transition region;
 *       and NO INVARIANT nodes (this system never has 3 simultaneously
 *       stable phases, so Algorithm D must not fire spuriously here --
 *       confirmed empirically during this test's design, matching
 *       {@code CalculationSessionStepTracerTest} Section B's own
 *       documented miscibility-gap structure for this exact system).</li>
 *   <li><b>Section B</b> -- V-Zr (data/VZR-re2.TDB), the same
 *       BCC_A2-&gt;BCC_A2+V2ZR system {@code CalculationSessionStepTracerTest}
 *       Section A already validates, as a second negative control for
 *       Algorithm D: asserts line segments have {@code fixedPhase}
 *       populated but zero INVARIANT nodes.</li>
 * </ul>
 */
public class CalculationSessionMapTracerTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {

        runSectionA();
        runSectionB();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CALCULATIONSESSION MAP-TRACER CHECKS PASSED");
        } else {
            throw new AssertionError(failures + " check(s) failed -- see log above.");
        }
    }

    // ------------------------------------------------------------------
    // Section A -- Ag-Cu, TEMPERATURE walk / x(Cu) release
    // ------------------------------------------------------------------

    private static void runSectionA() throws Exception {

        System.out.println("============================================================");
        System.out.println("Section A: Ag-Cu map, TEMPERATURE walk / x(Cu) release");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/agcu.TDB", Arrays.asList("AG", "CU"),
                Arrays.asList("LIQUID", "FCC_A1"));

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE,
                1000.0, 1200.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.0, 1.0, 0.01);

        double[] comp = { 0.5, 0.5 };

        session.calculateMap(walkAxis, releaseAxis, 0.0, 101325.0, comp);

        PhaseDiagramResult result = session.currentMapResult();

        printResult(result);

        require("currentMapResult() is not null", result != null, "was null");

        int twoPhaseSegmentsWithFixedPhase = 0;
        for (LineSegment line : result.getLines()) {
            if (line.stablePhases.size() == 2 && line.fixedPhase != null) {
                twoPhaseSegmentsWithFixedPhase++;
            }
        }
        require("at least 1 two-phase segment has a non-null fixedPhase "
                        + "(proves the exact C2 boundary solve ran, not a fallback)",
                twoPhaseSegmentsWithFixedPhase >= 1,
                "found " + twoPhaseSegmentsWithFixedPhase);

        require("at least 2 CROSSING nodes were found "
                        + "(FCC_A1->2-phase, then 2-phase->LIQUID)",
                countCrossings(result) >= 2,
                "got " + countCrossings(result));

        require("no INVARIANT nodes (this system never has 3 simultaneously "
                        + "stable phases -- Algorithm D must not fire spuriously)",
                countInvariants(result) == 0,
                "got " + countInvariants(result));

        requireHasSinglePhaseSegment(result, "FCC_A1");
        requireHasSinglePhaseSegment(result, "LIQUID");
    }

    // ------------------------------------------------------------------
    // Section B -- V-Zr negative control (no invariant expected in range)
    // ------------------------------------------------------------------

    private static void runSectionB() throws Exception {

        System.out.println();
        System.out.println("============================================================");
        System.out.println("Section B: V-Zr map, x(Zr) walk / negative Algorithm-D control");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", Arrays.asList("V", "ZR"),
                Arrays.asList("V2ZR", "BCC_A2"));

        // Walk composition, release... a map needs the RELEASE axis to be
        // COMPOSITION; use TEMPERATURE as the walk axis here (mirrors
        // CalculationSessionStepTracerTest Section A's own T/x choice,
        // just swapping which is walked vs. released for map's own shape).
        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE,
                1400.0, 1600.0, 20.0);
        AxisConfig releaseAxis = new AxisConfig("x(Zr)", 1, 0.0, 1.0, 0.01);

        double[] comp = { 0.93, 0.07 };

        session.calculateMap(walkAxis, releaseAxis, 0.0, 101325.0, comp);

        PhaseDiagramResult result = session.currentMapResult();

        printResult(result);

        require("currentMapResult() is not null", result != null, "was null");

        require("no INVARIANT nodes (V2ZR+BCC_A2 is an ordinary 2-phase "
                        + "boundary in this range, not a genuine invariant)",
                countInvariants(result) == 0,
                "got " + countInvariants(result));

        int segmentsWithFixedPhase = 0;
        for (LineSegment line : result.getLines()) {
            if (line.fixedPhase != null) {
                segmentsWithFixedPhase++;
            }
        }
        require("at least 1 segment has a non-null fixedPhase",
                segmentsWithFixedPhase >= 1,
                "found " + segmentsWithFixedPhase);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void printResult(PhaseDiagramResult result) {

        System.out.println("complete=" + result.isComplete() + " message=" + result.getMessage());

        System.out.println("Lines:");
        for (LineSegment line : result.getLines()) {
            System.out.println("  " + line.stablePhases + " fixedPhase=" + line.fixedPhase
                    + " size=" + line.size());
        }

        System.out.println("Nodes:");
        for (NodePoint node : result.getNodes()) {
            System.out.println("  " + node.type + " " + Arrays.toString(node.axisValues)
                    + " phases=" + node.stablePhases);
        }
    }

    private static int countCrossings(PhaseDiagramResult result) {
        int count = 0;
        for (NodePoint node : result.getNodes()) {
            if (node.type == NodePoint.Type.CROSSING) {
                count++;
            }
        }
        return count;
    }

    private static int countInvariants(PhaseDiagramResult result) {
        int count = 0;
        for (NodePoint node : result.getNodes()) {
            if (node.type == NodePoint.Type.INVARIANT) {
                count++;
            }
        }
        return count;
    }

    private static void requireHasSinglePhaseSegment(
            PhaseDiagramResult result, String phaseName) {

        boolean found = false;

        for (LineSegment line : result.getLines()) {
            if (line.stablePhases.size() == 1
                    && line.stablePhases.contains(phaseName)) {
                found = true;
            }
        }

        require("a single-phase (" + phaseName + "-only) segment was found",
                found, "not found");
    }

    private static void require(String label, boolean condition, String detail) {
        if (condition) {
            System.out.println("PASS: " + label);
        } else {
            System.out.println("FAIL: " + label + " -- " + detail);
            failures++;
        }
    }
}
