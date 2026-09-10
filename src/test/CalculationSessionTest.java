package test;

import session.CalculationSession;
import system.ThermodynamicSystem;

import java.util.List;

/**
 * Verification for {@link CalculationSession} per the checklist in
 * {@code docs/plan-3layer-core-dataflow.md} ("Additional verification for
 * CalculationSession"): reuse-vs-rebuild on {@code setModel}, the
 * IllegalStateException guard before a model is set, and an end-to-end
 * single-point + phase-diagram run against one held system.
 */
public class CalculationSessionTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testRebuildOnlyWhenKeyChanges();
        testCalculateBeforeSetModelThrows();
        testEndToEndReuseAcrossCalculationKinds();
        testStepAndMapAreUnimplementedStubs();

        if (failures == 0) {
            System.out.println("ALL CalculationSession CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static void check(boolean condition, String description) {
        if (condition) {
            System.out.println("  PASS: " + description);
        } else {
            System.out.println("  FAIL: " + description);
            failures++;
        }
    }

    private static void testRebuildOnlyWhenKeyChanges() throws Exception {
        System.out.println("=== setModel: rebuild only when key changes ===");
        CalculationSession session = new CalculationSession();

        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("LIQUID", "BCC_A2"));
        ThermodynamicSystem first = session.currentSystem();

        // Identical arguments -- must NOT rebuild (same reference back).
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("LIQUID", "BCC_A2"));
        check(session.currentSystem() == first,
                "setModel() with identical args does not rebuild (same System reference)");

        // Different phase list -- MUST rebuild (different reference).
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"),
                List.of("LIQUID", "BCC_A2", "HCP_A3"));
        check(session.currentSystem() != first,
                "setModel() with different phases DOES rebuild (different System reference)");
    }

    private static void testCalculateBeforeSetModelThrows() {
        System.out.println("=== calculate before setModel throws ===");
        CalculationSession session = new CalculationSession();
        boolean threw = false;
        try {
            session.calculateEquilibrium(2000.0, 101325.0, new double[]{0.5, 0.5});
        } catch (IllegalStateException expected) {
            threw = true;
        }
        check(threw, "calculateEquilibrium() before setModel() throws IllegalStateException");
    }

    private static void testEndToEndReuseAcrossCalculationKinds() throws Exception {
        System.out.println("=== end-to-end: single-point then phase-diagram, one held system ===");
        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("LIQUID", "BCC_A2"));
        ThermodynamicSystem systemBefore = session.currentSystem();

        session.calculateEquilibrium(2000.0, 101325.0, new double[]{0.5, 0.5});
        check(session.currentEquilibriumResult() != null,
                "calculateEquilibrium() stores a non-null EquilibriumResult");
        check(session.currentSystem() == systemBefore,
                "system unchanged after calculateEquilibrium()");

        calc.diagram.AxisConfig axis =
                new calc.diagram.AxisConfig("T / K", calc.diagram.AxisConfig.Type.TEMPERATURE,
                        1800, 2200, 50);
        session.calculatePhaseDiagram(new calc.diagram.AxisConfig[]{axis},
                new double[]{2000.0}, 2000.0, 101325.0, new double[]{0.5, 0.5});
        check(session.currentPhaseDiagram() != null,
                "calculatePhaseDiagram() stores a non-null PhaseDiagram");
        check(session.currentSystem() == systemBefore,
                "system still unchanged after calculatePhaseDiagram() -- no re-parse of the TDB");
        check(session.currentEquilibriumResult() != null,
                "earlier equilibrium result is still available after running a different calculation kind");
    }

    private static void testStepAndMapAreUnimplementedStubs() throws Exception {
        System.out.println("=== calculateStep/calculateMap: unimplemented stubs ===");
        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("LIQUID", "BCC_A2"));

        calc.diagram.AxisConfig axis0 =
                new calc.diagram.AxisConfig("T / K", calc.diagram.AxisConfig.Type.TEMPERATURE,
                        1800, 2200, 50);
        calc.diagram.AxisConfig axis1 =
                new calc.diagram.AxisConfig("x(ZR)", 1, 0.0, 1.0, 0.05);

        boolean stepThrew = false;
        try {
            session.calculateStep(axis0, 2000.0, 101325.0, new double[]{0.5, 0.5});
        } catch (UnsupportedOperationException expected) {
            stepThrew = true;
        }
        check(stepThrew, "calculateStep() throws UnsupportedOperationException (not yet implemented)");

        boolean mapThrew = false;
        try {
            session.calculateMap(axis0, axis1, 2000.0, 101325.0, new double[]{0.5, 0.5});
        } catch (UnsupportedOperationException expected) {
            mapThrew = true;
        }
        check(mapThrew, "calculateMap() throws UnsupportedOperationException (not yet implemented)");

        // Both must still enforce the setModel() precondition even though unimplemented.
        CalculationSession freshSession = new CalculationSession();
        boolean stepStateThrew = false;
        try {
            freshSession.calculateStep(axis0, 2000.0, 101325.0, new double[]{0.5, 0.5});
        } catch (IllegalStateException expected) {
            stepStateThrew = true;
        }
        check(stepStateThrew, "calculateStep() before setModel() throws IllegalStateException, not UnsupportedOperationException");
    }
}
