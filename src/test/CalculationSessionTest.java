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
        testBrowsingDoesNotRequireSetModel();
        testAvailableDatabasesListsKnownFixtures();
        testSetModelValidatesDatabaseElementsPhases();

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
        System.out.println("=== end-to-end: single-point equilibrium, held system ===");
        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("LIQUID", "BCC_A2"));
        ThermodynamicSystem systemBefore = session.currentSystem();

        session.calculateEquilibrium(2000.0, 101325.0, new double[]{0.5, 0.5});
        check(session.currentEquilibriumResult() != null,
                "calculateEquilibrium() stores a non-null EquilibriumResult");
        check(session.currentSystem() == systemBefore,
                "system unchanged after calculateEquilibrium()");
    }

    private static void testStepAndMapAreUnimplementedStubs() throws Exception {
        System.out.println("=== calculateStep/calculateMap implemented; calculatePhaseDiagram: unimplemented stub ===");
        CalculationSession session = new CalculationSession();
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("LIQUID", "BCC_A2"));

        calc.diagram.AxisConfig axis0 =
                new calc.diagram.AxisConfig("T / K", calc.diagram.AxisConfig.Type.TEMPERATURE,
                        1800, 2200, 50);
        calc.diagram.AxisConfig axis1 =
                new calc.diagram.AxisConfig("x(ZR)", 1, 0.0, 1.0, 0.05);

        // calculateStep() is implemented (StepTracer) -- see
        // CalculationSessionStepTracerTest for its own dedicated coverage.
        // Here just confirm it no longer throws and stores a result.
        session.calculateStep(axis0, 2000.0, 101325.0, new double[]{0.5, 0.5});
        check(session.currentStepResult() != null,
                "calculateStep() is implemented and stores a currentStepResult()");

        // calculateMap() is implemented (MapTracer) -- see
        // CalculationSessionMapTracerTest for its own dedicated coverage.
        // Here just confirm it no longer throws and stores a result.
        session.calculateMap(axis0, axis1, 2000.0, 101325.0, new double[]{0.5, 0.5});
        check(session.currentMapResult() != null,
                "calculateMap() is implemented and stores a currentMapResult()");

        boolean diagramThrew = false;
        try {
            session.calculatePhaseDiagram(new calc.diagram.AxisConfig[]{axis0},
                    new double[]{2000.0}, 2000.0, 101325.0, new double[]{0.5, 0.5});
        } catch (UnsupportedOperationException expected) {
            diagramThrew = true;
        }
        check(diagramThrew, "calculatePhaseDiagram() throws UnsupportedOperationException "
                + "(x-facing tracer removed; no y-facing tracer yet)");

        // All three must still enforce the setModel() precondition.
        CalculationSession freshSession = new CalculationSession();
        boolean stepStateThrew = false;
        try {
            freshSession.calculateStep(axis0, 2000.0, 101325.0, new double[]{0.5, 0.5});
        } catch (IllegalStateException expected) {
            stepStateThrew = true;
        }
        check(stepStateThrew, "calculateStep() before setModel() throws IllegalStateException");
    }

    private static void testBrowsingDoesNotRequireSetModel() throws Exception {
        System.out.println("=== browsing (availableElements/availablePhasesFor) works before setModel ===");
        CalculationSession session = new CalculationSession();

        // No setModel() call yet -- browsing must still work.
        java.util.List<String> elements = session.availableElements("data/VZR-re2.TDB");
        check(elements.contains("V") && elements.contains("ZR"),
                "availableElements() lists V and ZR from VZR-re2.TDB with no setModel() call");

        java.util.List<String> phases =
                session.availablePhasesFor("data/VZR-re2.TDB", java.util.List.of("V", "ZR"));
        check(phases.contains("V2ZR"),
                "availablePhasesFor() lists V2ZR for elements [V, ZR] with no setModel() call");

        check(!session.hasModel(),
                "browsing does not itself build a ThermodynamicSystem (hasModel() still false)");

        // Now actually set the model using the same TDB/elements/phases just
        // browsed, confirming the two paths (browse, then calculate) compose.
        session.setModel("data/VZR-re2.TDB", java.util.List.of("V", "ZR"), java.util.List.of("V2ZR"));
        check(session.hasModel(), "setModel() after browsing builds the system as normal");
    }

    private static void testAvailableDatabasesListsKnownFixtures() {
        System.out.println("=== availableDatabases lists known fixture files ===");
        CalculationSession session = new CalculationSession();
        java.util.List<String> databases = session.availableDatabases();
        check(databases.contains("data/VZR-re2.TDB"),
                "availableDatabases() lists data/VZR-re2.TDB");
    }

    private static void testSetModelValidatesDatabaseElementsPhases() {
        System.out.println("=== setModel validates database/elements/phases with helpful errors ===");

        // 1. Unknown database -- error names available databases.
        CalculationSession session1 = new CalculationSession();
        try {
            session1.setModel("data/does-not-exist.tdb", java.util.List.of("V"), java.util.List.of("V2ZR"));
            check(false, "setModel() with an unknown database throws IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains("data/does-not-exist.tdb")
                    && e.getMessage().toLowerCase().contains("available"),
                    "setModel() with an unknown database throws IllegalArgumentException naming available databases");
        } catch (Exception e) {
            check(false, "setModel() with an unknown database throws IllegalArgumentException, not " + e.getClass());
        }

        // 2. Valid database, invalid element -- error tells the user to
        // choose a valid database first and names the actual elements.
        CalculationSession session2 = new CalculationSession();
        try {
            session2.setModel("data/VZR-re2.TDB", java.util.List.of("XX"), java.util.List.of("V2ZR"));
            check(false, "setModel() with an invalid element throws IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains("XX") && e.getMessage().contains("V")
                    && e.getMessage().contains("ZR"),
                    "setModel() with an invalid element names the bad element and the database's real elements");
        } catch (Exception e) {
            check(false, "setModel() with an invalid element throws IllegalArgumentException, not " + e.getClass());
        }

        // 3. Valid database+elements, invalid phase -- error tells the user
        // to choose valid elements first and names the actual phases.
        CalculationSession session3 = new CalculationSession();
        try {
            session3.setModel("data/VZR-re2.TDB", java.util.List.of("V", "ZR"),
                    java.util.List.of("NOT_A_REAL_PHASE"));
            check(false, "setModel() with an invalid phase throws IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains("NOT_A_REAL_PHASE") && e.getMessage().contains("V2ZR"),
                    "setModel() with an invalid phase names the bad phase and the valid phases for those elements");
        } catch (Exception e) {
            check(false, "setModel() with an invalid phase throws IllegalArgumentException, not " + e.getClass());
        }

        // 4. Fully valid input still succeeds (validation doesn't false-positive).
        CalculationSession session4 = new CalculationSession();
        try {
            session4.setModel("data/VZR-re2.TDB", java.util.List.of("V", "ZR"), java.util.List.of("V2ZR"));
            check(session4.hasModel(), "setModel() with fully valid input still succeeds");
        } catch (Exception e) {
            check(false, "setModel() with fully valid input should not throw, but got: " + e);
        }
    }
}
