package test;

import calc.diagram.AxisConfig;
import session.CalculationSession;
import ui.result.CoarseDiagramResult;
import ui.result.CoarseDiagramResult.GridPoint;

import java.util.Arrays;

/**
 * End-to-end test of {@link CalculationSession#calculateCoarseBinaryDiagram}
 * / {@link CalculationSession#calculateCoarseTernaryDiagram} -- the new
 * coarse/scatter phase-diagram feature -- via {@link
 * calc.diagram.CoarseDiagramTracer}:
 *
 * <ul>
 *   <li><b>Section A</b> -- V-Zr (data/VZR-re2.TDB) binary coarse diagram:
 *       x(Zr) x Temperature grid, spanning the same BCC_A2/BCC_A2+V2ZR
 *       boundary validated by {@code CalculationSessionStepTracerTest}
 *       Section A. Confirms the known single-phase region samples show
 *       only BCC_A2 and the known two-phase region samples show both
 *       BCC_A2 and V2ZR, and that the whole grid call completes without
 *       throwing.</li>
 *   <li><b>Section B</b> -- Cr-Fe-Mo (data/Cr-Fe-Mo.TDB), a genuine
 *       ternary system this session's TDB-parser fixes unblocked: a
 *       coarse ternary diagram over x(Fe)/x(Mo) at fixed T=1800K, where
 *       the Cr-rich corner (low x(Fe)+x(Mo)) is single-phase A2 and the
 *       Fe-rich corner (high x(Fe)) is LIQUID -- confirmed by running
 *       the full grid, not by a handful of spot-checks, since a sparse
 *       spot-check of this system missed the LIQUID region entirely
 *       during this test's design -- and a second grid at T=2000K, where
 *       spot-checks found a genuine LIQUID/A2 split plus some solver
 *       exceptions, confirming per-point failure isolation. Also
 *       confirms points outside the composition simplex (x(Fe)+x(Mo)
 *       &gt; 1) are absent from the result.</li>
 *   <li><b>Section C</b> -- regression/failure-isolation, reusing the
 *       known-flaky Al-Co-Cr-Ni quaternary system from {@code
 *       CalculationSessionStepTracerTest} Section E: confirms {@code
 *       EquilibriumSolveHelper.solveOrSentinel} isolates per-point
 *       failures in the coarse tracer exactly as {@code StepTracer}
 *       already does (proving the shared-helper extraction did not
 *       change behavior), by re-running the same known-flaky
 *       temperature range through {@code calculateCoarseBinaryDiagram}
 *       (a 1-wide composition "axis" x the temperature axis) rather
 *       than {@code calculateStep}.</li>
 * </ul>
 */
public class CalculationSessionCoarseDiagramTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {

        runSectionA();
        runSectionB();
        runSectionC();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CALCULATIONSESSION COARSE-DIAGRAM CHECKS PASSED");
        } else {
            throw new AssertionError(failures + " check(s) failed -- see log above.");
        }
    }

    // ------------------------------------------------------------------
    // Section A -- V-Zr binary coarse diagram
    // ------------------------------------------------------------------

    private static void runSectionA() throws Exception {

        System.out.println("============================================================");
        System.out.println("Section A: V-Zr binary coarse diagram, x(Zr) x T");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", Arrays.asList("V", "ZR"),
                Arrays.asList("V2ZR", "BCC_A2"));

        AxisConfig axisX = new AxisConfig("x(Zr)", 1, 0.02, 0.20, 0.01);
        AxisConfig axisY = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE,
                1200.0, 1600.0, 50.0);

        double[] comp = { 1.0, 0.0 };

        session.calculateCoarseBinaryDiagram(axisX, axisY, 1500.0, 101325.0, comp);

        CoarseDiagramResult result = session.currentCoarseDiagramResult();
        printSummary(result);

        require("currentCoarseDiagramResult() is not null", result != null, "was null");

        boolean sawSinglePhaseBccOnly = false;
        boolean sawTwoPhase = false;

        for (GridPoint gp : result.getPoints()) {
            if (!gp.converged) {
                continue;
            }
            if (gp.axisXValue < 0.05 && gp.stablePhases.size() == 1
                    && gp.stablePhases.contains("BCC_A2")) {
                sawSinglePhaseBccOnly = true;
            }
            if (gp.axisXValue > 0.09 && gp.stablePhases.contains("BCC_A2")
                    && gp.stablePhases.contains("V2ZR")) {
                sawTwoPhase = true;
            }
        }

        require("a converged single-phase BCC_A2-only sample was found (x(Zr) < 0.05)",
                sawSinglePhaseBccOnly, "no such sample found");
        require("a converged two-phase BCC_A2+V2ZR sample was found (x(Zr) > 0.09)",
                sawTwoPhase, "no such sample found");
    }

    // ------------------------------------------------------------------
    // Section B -- Cr-Fe-Mo ternary coarse diagram
    // ------------------------------------------------------------------

    private static void runSectionB() throws Exception {

        System.out.println("============================================================");
        System.out.println("Section B: Cr-Fe-Mo ternary coarse diagram, x(Fe) x x(Mo)");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/Cr-Fe-Mo.TDB", Arrays.asList("CR", "FE", "MO"),
                Arrays.asList("LIQUID", "A2"));

        // Elements ["CR","FE","MO"] -> index 0=CR, 1=FE, 2=MO.
        AxisConfig axisFe = new AxisConfig("x(Fe)", 1, 0.0, 0.6, 0.1);
        AxisConfig axisMo = new AxisConfig("x(Mo)", 2, 0.0, 0.6, 0.1);

        double[] comp = { 1.0, 0.0, 0.0 };

        // T=1800K: the Cr-rich corner (low x(Fe)+x(Mo)) is single-phase
        // A2, the Fe-rich corner (high x(Fe)) is LIQUID -- both regions
        // are only visible by running the whole grid (a sparse
        // spot-check of a few Cr-rich points during this test's design
        // missed the LIQUID corner entirely).
        session.calculateCoarseTernaryDiagram(axisFe, axisMo, 1800.0, 101325.0, comp);
        CoarseDiagramResult t1800Result = session.currentCoarseDiagramResult();
        printSummary(t1800Result);

        require("currentCoarseDiagramResult() is not null (T=1800K)",
                t1800Result != null, "was null");

        boolean anyOutsideSimplex = false;
        boolean sawCrRichA2Only = false;
        boolean sawFeRichLiquid = false;

        for (GridPoint gp : t1800Result.getPoints()) {

            if (gp.axisXValue + gp.axisYValue > 1.0 + 1.0e-6) {
                anyOutsideSimplex = true;
            }

            if (!gp.converged) {
                continue;
            }

            if (gp.axisXValue <= 0.4 && gp.stablePhases.size() == 1
                    && gp.stablePhases.contains("A2")) {
                sawCrRichA2Only = true;
            }

            if (gp.axisXValue >= 0.5 && gp.stablePhases.size() == 1
                    && gp.stablePhases.contains("LIQUID")) {
                sawFeRichLiquid = true;
            }
        }

        require("no sampled point lies outside the composition simplex (x(Fe)+x(Mo) <= 1)",
                !anyOutsideSimplex, "found a point with x(Fe)+x(Mo) > 1");
        require("a converged single-phase A2 sample was found in the Cr-rich corner (T=1800K)",
                sawCrRichA2Only, "no such sample found");
        require("a converged single-phase LIQUID sample was found in the Fe-rich corner (T=1800K)",
                sawFeRichLiquid, "no such sample found");

        // T=2000K: spot-checks found a genuine LIQUID/A2 split plus some
        // solver exceptions -- exercises both "known two regions" and
        // per-point failure isolation in one grid.
        session.calculateCoarseTernaryDiagram(axisFe, axisMo, 2000.0, 101325.0, comp);
        CoarseDiagramResult mixedResult = session.currentCoarseDiagramResult();
        printSummary(mixedResult);

        require("currentCoarseDiagramResult() is not null (T=2000K)",
                mixedResult != null, "was null");

        boolean sawLiquid = false;
        boolean sawA2 = false;
        boolean sawNonConverged = false;

        for (GridPoint gp : mixedResult.getPoints()) {
            if (!gp.converged) {
                sawNonConverged = true;
                continue;
            }
            if (gp.stablePhases.contains("LIQUID")) {
                sawLiquid = true;
            }
            if (gp.stablePhases.contains("A2")) {
                sawA2 = true;
            }
        }

        require("a converged LIQUID sample was found at T=2000K",
                sawLiquid, "no such sample found");
        require("a converged A2 sample was found at T=2000K",
                sawA2, "no such sample found");
        require("at least one non-converged sample was marked (not thrown) at T=2000K",
                sawNonConverged, "no non-converged sample found -- spot-check assumption may be stale");
    }

    // ------------------------------------------------------------------
    // Section C -- AlCoCrNi regression / failure-isolation
    // ------------------------------------------------------------------

    private static void runSectionC() throws Exception {

        System.out.println("============================================================");
        System.out.println("Section C: Al-Co-Cr-Ni quaternary, coarse binary diagram "
                + "(1-wide composition axis x Temperature) -- failure-isolation regression");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/AlCoCrNi-volume.TDB",
                Arrays.asList("AL", "CO", "CR", "NI"),
                Arrays.asList("LIQUID", "FCC_A1"));

        // A degenerate, 1-wide COMPOSITION axis (min==max==axisValue used by
        // the fixed composition) just re-runs the same T-sweep
        // CalculationSessionStepTracerTest Section E already exercises,
        // through the grid tracer instead of the line tracer, to confirm
        // the EquilibriumSolveHelper extraction preserved per-point
        // failure isolation.
        AxisConfig axisComp = new AxisConfig("x(Al)", 0, 0.4, 0.4, 1.0);
        AxisConfig axisT = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE,
                1400.0, 2000.0, 50.0);

        double[] comp = { 0.4, 0.3, 0.2, 0.1 };

        session.calculateCoarseBinaryDiagram(axisComp, axisT, 2000.0, 101325.0, comp);

        CoarseDiagramResult result = session.currentCoarseDiagramResult();
        printSummary(result);

        require("currentCoarseDiagramResult() is not null", result != null, "was null");

        boolean sawNonConverged = false;
        boolean sawConverged = false;

        for (GridPoint gp : result.getPoints()) {
            if (gp.converged) {
                sawConverged = true;
            } else {
                sawNonConverged = true;
            }
        }

        require("at least one grid point converged (scan endpoints are known-good)",
                sawConverged, "no converged points at all");
        require("at least one grid point was marked non-converged rather than throwing "
                        + "(known mid-iteration duplicate-slot gap, still present for this system)",
                sawNonConverged, "no non-converged points found -- gap may now be fully closed, or grid too coarse");
        require("the whole grid call completed without throwing", true, "");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void printSummary(CoarseDiagramResult result) {

        int converged = 0;
        int nonConverged = 0;

        for (GridPoint gp : result.getPoints()) {
            if (gp.converged) {
                converged++;
            } else {
                nonConverged++;
            }
        }

        System.out.println("Points: " + result.getPoints().size()
                + " (converged=" + converged + ", non-converged=" + nonConverged + ")");
        System.out.println("complete=" + result.isComplete() + " message=" + result.getMessage());
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
