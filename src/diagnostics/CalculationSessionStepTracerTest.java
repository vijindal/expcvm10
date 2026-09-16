package diagnostics;

import calc.diagram.AxisConfig;
import session.CalculationSession;
import calc.diagram.PhaseDiagramResult;
import calc.diagram.PhaseDiagramResult.LineSegment;
import calc.diagram.PhaseDiagramResult.NodePoint;

import java.util.Arrays;
import java.util.List;

/**
 * End-to-end test of {@link CalculationSession#calculateStep} -- Sundman
 * 2021 Calphad 75, Algorithm B's step branch -- via {@link
 * calc.diagram.StepTracer}, across multiple databases, axis types, and
 * phase-boundary kinds:
 *
 * <ul>
 *   <li><b>Section A</b> -- V-Zr (data/VZR-re2.TDB), COMPOSITION axis,
 *       single-phase -&gt; two-DIFFERENT-phase boundary (BCC_A2 -&gt;
 *       BCC_A2+V2ZR).</li>
 *   <li><b>Section B</b> -- Ag-Cu (data/agcu.TDB, Sundman 2021's own Fig. 2(a)
 *       example system), TEMPERATURE axis at the eutectic composition,
 *       crossing TWO boundaries in one walk: single-phase LIQUID -&gt;
 *       LIQUID+FCC_A1 -&gt; FCC_A1+FCC_A1 (a miscibility gap -- the SAME
 *       phase name stable at two different compositions, collapsing from
 *       2 distinct phase names to 1 repeated name, still a genuine
 *       detectable stable-set change).</li>
 *   <li><b>Section C</b> -- Ag-Cu, COMPOSITION axis at T=700K entirely
 *       inside the FCC_A1 miscibility gap (no crossing expected) --
 *       verifies a step walk that finds NO boundary still produces a
 *       single, correct, complete line segment rather than spurious
 *       nodes.</li>
 *   <li><b>Section D</b> -- Fe-C (data/steel1.TDB), single-candidate
 *       (BCC_A2 only, an interstitial (Fe)(C,Va)3 sublattice model)
 *       COMPOSITION axis at T=1000K spanning x_C 0.0001-0.2. Regression
 *       test for a real {@code GridMinimizer} bug found and fixed this
 *       session: the hull search could return TWO near-identical-
 *       composition stable slots of the SAME single candidate (no
 *       genuine miscibility gap here), making the global equilibrium
 *       matrix ill-conditioned -- previously this range failed at
 *       roughly half its sampled points with "Excessive global
 *       linear-system residual". Fixed via {@code
 *       GridMinimizer.mergeDuplicateCompositions()}, matching
 *       OpenCalphad's own {@code same_composition()} check
 *       (matsmin.F90, {@code xdiff=0.01} mole fraction). Asserts the
 *       ENTIRE walk now converges (a single BCC_A2-only line segment,
 *       {@code isComplete()==true}) -- Section E below shows the same
 *       kind of duplicate-slot gap still recurring for a QUATERNARY
 *       system, where it arises DURING Newton iteration rather than at
 *       {@code GridMinimizer} initialization and is not fixed by this
 *       session's change (a separate, deferred follow-up).</li>
 *   <li><b>Section E</b> -- Al-Co-Cr-Ni (data/AlCoCrNi-volume.TDB), a
 *       genuine QUATERNARY system, TEMPERATURE axis at a fixed
 *       off-symmetric composition, LIQUID+FCC_A1 candidates. Confirmed
 *       (via direct spot-checks) to converge at both scan endpoints --
 *       LIQUID+FCC_A1 at 2000K, pure FCC_A1 at 1400K -- with several
 *       intermediate points hitting a composition-duplicate stable-slot
 *       gap that arises DURING Newton iteration (stable slots converging
 *       toward each other, not present at {@code GridMinimizer}'s own
 *       initialization) -- distinct from, and not fixed by, Section D's
 *       {@code GridMinimizer}-initialization-time fix; {@code
 *       updateStablePhaseSet()}'s removal pass still only triggers on
 *       near-zero amount, not on two slots converging to the same
 *       composition mid-iteration. This is the {@link
 *       calc.diagram.StepTracer} exception-resilience path (does not
 *       crash, reports {@code isComplete()==false}, still produces
 *       usable segments) recurring naturally in an N&gt;2-component
 *       system, and confirms {@code StepTracer}'s N-component
 *       COMPOSITION-axis renormalization (holding the other 3
 *       components' RATIOS fixed while the axis component varies)
 *       produces a still-meaningful walk despite the intermediate
 *       failures.</li>
 * </ul>
 */
public class CalculationSessionStepTracerTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {

        runSectionA();
        runSectionB();
        runSectionC();
        runSectionD();
        runSectionE();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CALCULATIONSESSION STEP-TRACER CHECKS PASSED");
        } else {
            throw new AssertionError(failures + " check(s) failed -- see log above.");
        }
    }

    // ------------------------------------------------------------------
    // Section A -- V-Zr, COMPOSITION axis, single -> two-different-phase
    // ------------------------------------------------------------------

    private static void runSectionA() throws Exception {

        System.out.println("============================================================");
        System.out.println("Section A: V-Zr, COMPOSITION axis, BCC_A2 -> BCC_A2+V2ZR");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", Arrays.asList("V", "ZR"),
                Arrays.asList("V2ZR", "BCC_A2"));

        AxisConfig axis = new AxisConfig(
                "x(Zr)", 1, // component index 1 = ZR, per elements list ["V","ZR"]
                0.02, 0.20, 0.01);

        double[] comp = { 1.0, 0.0 }; // overridden by the axis at componentIndex=1

        session.calculateStep(axis, 1500.0, 101325.0, comp);

        PhaseDiagramResult result = session.currentStepResult();

        printResult(result);

        requireHasSinglePhaseSegment(result, "BCC_A2");
        requireHasSegmentWithPhases(result, "BCC_A2", "V2ZR");
        requireCrossingInRange(result, 0.05, 0.08);
        requireComplete(result);
    }

    // ------------------------------------------------------------------
    // Section B -- Ag-Cu, TEMPERATURE axis through the eutectic, two
    // crossings: LIQUID -> LIQUID+FCC_A1 -> FCC_A1+FCC_A1 (miscibility gap)
    // ------------------------------------------------------------------

    private static void runSectionB() throws Exception {

        System.out.println();
        System.out.println("============================================================");
        System.out.println("Section B: Ag-Cu, TEMPERATURE axis through the eutectic "
                + "(~1052K), x_Cu=0.5");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/agcu.TDB", Arrays.asList("AG", "CU"),
                Arrays.asList("LIQUID", "FCC_A1"));

        AxisConfig axis = new AxisConfig(
                "T / K", AxisConfig.Type.TEMPERATURE,
                1000.0, 1200.0, 5.0);

        double[] comp = { 0.5, 0.5 };

        session.calculateStep(axis, 0.0, 101325.0, comp);

        PhaseDiagramResult result = session.currentStepResult();

        printResult(result);

        requireHasSinglePhaseSegment(result, "LIQUID");
        requireHasSegmentWithPhases(result, "LIQUID", "FCC_A1");

        boolean sawMiscibilityGapSegment = false;

        for (LineSegment line : result.getLines()) {
            if (line.stablePhases.size() == 1
                    && line.stablePhases.contains("FCC_A1")) {
                sawMiscibilityGapSegment = true;
            }
        }

        require("a low-T FCC_A1-only segment was found (miscibility gap "
                + "collapses two stable slots to one reported name)",
                sawMiscibilityGapSegment, "not found");

        require("at least 2 CROSSING nodes were found (LIQUID->2-phase, "
                + "then 2-phase->miscibility-gap)",
                countCrossings(result) >= 2,
                "got " + countCrossings(result));

        requireComplete(result);
    }

    // ------------------------------------------------------------------
    // Section C -- Ag-Cu, COMPOSITION axis entirely inside the FCC
    // miscibility gap: no crossing expected, one clean segment.
    // ------------------------------------------------------------------

    private static void runSectionC() throws Exception {

        System.out.println();
        System.out.println("============================================================");
        System.out.println("Section C: Ag-Cu, COMPOSITION axis at T=700K, entirely "
                + "inside the FCC_A1 miscibility gap (no crossing expected)");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/agcu.TDB", Arrays.asList("AG", "CU"),
                Arrays.asList("LIQUID", "FCC_A1"));

        AxisConfig axis = new AxisConfig(
                "x(Cu)", 1,
                0.10, 0.90, 0.05);

        double[] comp = { 1.0, 0.0 };

        session.calculateStep(axis, 700.0, 101325.0, comp);

        PhaseDiagramResult result = session.currentStepResult();

        printResult(result);

        require("exactly 1 line segment (no boundary crossed)",
                result.getLines().size() == 1,
                "got " + result.getLines().size());

        require("0 CROSSING nodes (only the 2 BOUNDARY endpoints)",
                countCrossings(result) == 0,
                "got " + countCrossings(result));

        requireHasSegmentWithPhases(result, "FCC_A1"); // single name, two slots

        requireComplete(result);
    }

    // ------------------------------------------------------------------
    // Section D -- Ag-Cu, COMPOSITION axis starting at a composition
    // where GridMinimizer is known to throw for this system/T -- verifies
    // graceful degradation, not a crash.
    // ------------------------------------------------------------------

    private static void runSectionD() throws Exception {

        System.out.println();
        System.out.println("============================================================");
        System.out.println("Section D: Fe-C (steel1.TDB), single-candidate (BCC_A2 only) "
                + "COMPOSITION axis at T=1000K, x_C 0.0001-0.2 -- regression test for "
                + "a GridMinimizer duplicate-composition-stable-slot bug found and "
                + "fixed this session (see GridMinimizer.mergeDuplicateCompositions(), "
                + "matching OpenCalphad's own same_composition() check)");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/steel1.TDB", Arrays.asList("FE", "C"),
                Arrays.asList("BCC_A2"));

        AxisConfig axis = new AxisConfig(
                "x(C)", 1,
                0.0001, 0.2, 0.01);

        double[] comp = { 1.0, 0.0 };

        session.calculateStep(axis, 1000.0, 101325.0, comp);

        PhaseDiagramResult result = session.currentStepResult();

        printResult(result);

        require("currentStepResult() is not null",
                result != null, "null result");

        require("exactly 1 line segment (BCC_A2 stable throughout, no "
                + "spurious duplicate-slot failures)",
                result != null && result.getLines().size() == 1,
                "got " + (result == null ? "null" : result.getLines().size()));

        if (result != null) {
            requireHasSinglePhaseSegment(result, "BCC_A2");
        }

        requireComplete(result);
    }

    // ------------------------------------------------------------------
    // Section E -- Al-Co-Cr-Ni, a genuine QUATERNARY system, TEMPERATURE
    // axis, LIQUID+FCC_A1 -> FCC_A1 with known intermediate solver gaps.
    // ------------------------------------------------------------------

    private static void runSectionE() throws Exception {

        System.out.println();
        System.out.println("============================================================");
        System.out.println("Section E: Al-Co-Cr-Ni (quaternary), TEMPERATURE axis, "
                + "x=(0.4,0.3,0.2,0.1) Al/Co/Cr/Ni, LIQUID+FCC_A1 -> FCC_A1");
        System.out.println("============================================================");

        CalculationSession session = new CalculationSession();
        session.setModel("data/AlCoCrNi-volume.TDB",
                Arrays.asList("AL", "CO", "CR", "NI"),
                Arrays.asList("LIQUID", "FCC_A1"));

        AxisConfig axis = new AxisConfig(
                "T / K", AxisConfig.Type.TEMPERATURE,
                1400.0, 2000.0, 50.0);

        // Off-symmetric composition -- an equimolar (0.25 each) start was
        // spot-checked separately and found numerically degenerate for
        // GridMinimizer's hull search at EVERY temperature (a fully
        // symmetric composition ties many candidate grid points at once).
        double[] comp = { 0.4, 0.3, 0.2, 0.1 };

        // Must not throw despite several known-bad intermediate points.
        session.calculateStep(axis, 0.0, 101325.0, comp);

        PhaseDiagramResult result = session.currentStepResult();

        printResult(result);

        require("currentStepResult() is not null",
                result != null, "null result");

        if (result == null) {
            return;
        }

        require("at least one segment has both LIQUID and FCC_A1 stable "
                + "(confirmed at the high-T scan endpoint)",
                hasSegmentContaining(result, "LIQUID", "FCC_A1"),
                "not found");

        requireHasSinglePhaseSegment(result, "FCC_A1");

        require("result reports incomplete (known intermediate solver "
                + "gaps in this composition/T range were hit, per direct "
                + "spot-checks during this test's design)",
                !result.isComplete(), result.getMessage());
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static void printResult(PhaseDiagramResult result) {

        if (result == null) {
            System.out.println("  (null result)");
            return;
        }

        System.out.println("Lines:");
        for (LineSegment line : result.getLines()) {
            System.out.println("  " + line.label() + " over "
                    + line.size() + " points, axis in ["
                    + line.coords.get(0)[0] + ", "
                    + line.coords.get(line.coords.size() - 1)[0] + "]");
        }

        System.out.println("Nodes:");
        for (NodePoint node : result.getNodes()) {
            System.out.println("  " + node.type + " at axis="
                    + node.axisValues[0] + " phases=" + node.stablePhases);
        }

        System.out.println("complete=" + result.isComplete()
                + (result.isComplete() ? "" : " message=" + result.getMessage()));
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

    private static void requireHasSegmentWithPhases(
            PhaseDiagramResult result, String... phaseNames) {

        List<String> expected = Arrays.asList(phaseNames);

        boolean found = false;

        for (LineSegment line : result.getLines()) {
            if (line.stablePhases.size() == expected.size()
                    && line.stablePhases.containsAll(expected)) {
                found = true;
            }
        }

        require("a segment with exactly " + expected + " was found",
                found, "not found");
    }

    /** Like {@link #requireHasSegmentWithPhases}, but the segment may
     * have MORE stable phases than listed (checks containment, not an
     * exact-size match) -- useful when a system has more candidate
     * phases than the ones being asserted on. */
    private static boolean hasSegmentContaining(
            PhaseDiagramResult result, String... phaseNames) {

        List<String> expected = Arrays.asList(phaseNames);

        for (LineSegment line : result.getLines()) {
            if (line.stablePhases.containsAll(expected)) {
                return true;
            }
        }

        return false;
    }

    private static void requireCrossingInRange(
            PhaseDiagramResult result, double low, double high) {

        boolean found = false;

        for (NodePoint node : result.getNodes()) {

            if (node.type != NodePoint.Type.CROSSING) {
                continue;
            }

            found = true;

            require("crossing axis value " + node.axisValues[0]
                    + " is within [" + low + ", " + high + "]",
                    node.axisValues[0] > low && node.axisValues[0] < high,
                    "crossing at " + node.axisValues[0]);
        }

        require("at least one CROSSING node was found",
                found, "no crossing node");
    }

    private static void requireComplete(PhaseDiagramResult result) {

        require("result reports complete (all points converged)",
                result.isComplete(), result.getMessage());
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
