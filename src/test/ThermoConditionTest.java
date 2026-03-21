package test;

import domain.ThermoCondition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for ThermoCondition value object.
 * Run with: java -cp build/classes test.ThermoConditionTest
 */
public class ThermoConditionTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testBasicConstruction();
        testDof();
        testImmutabilityOfXList();
        testEmptyCompositions();
        testMultiPhase();

        System.out.println("\n=== ThermoConditionTest Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testBasicConstruction() {
        ArrayList<List<Double>> x = new ArrayList<>();
        x.add(Arrays.asList(0.3, 0.7));

        ThermoCondition tc = new ThermoCondition(500.0, 101325.0, x);

        assertEquals("T", 500.0, tc.getT());
        assertEquals("P", 101325.0, tc.getP());
        assertEquals("phaseCount", 1, tc.getPhaseCount());
        assertEquals("componentCount", 2, tc.getComponentCount());
        assertEquals("x[0][0]", 0.3, tc.getX().get(0).get(0));
        assertEquals("x[0][1]", 0.7, tc.getX().get(0).get(1));
    }

    private static void testDof() {
        // 1 phase, 2 components => F = 2 + 2 - 1 = 3
        ArrayList<List<Double>> x1 = new ArrayList<>();
        x1.add(Arrays.asList(0.5, 0.5));
        ThermoCondition tc1 = new ThermoCondition(1000.0, 100000.0, x1);
        assertEquals("dof (1 phase, 2 comp)", 3, tc1.dof());

        // 2 phases, 2 components => F = 2 + 2 - 2 = 2
        ArrayList<List<Double>> x2 = new ArrayList<>();
        x2.add(Arrays.asList(0.3, 0.7));
        x2.add(Arrays.asList(0.6, 0.4));
        ThermoCondition tc2 = new ThermoCondition(1000.0, 100000.0, x2);
        assertEquals("dof (2 phases, 2 comp)", 2, tc2.dof());

        // 3 phases, 3 components => F = 3 + 2 - 3 = 2
        ArrayList<List<Double>> x3 = new ArrayList<>();
        x3.add(Arrays.asList(0.3, 0.3, 0.4));
        x3.add(Arrays.asList(0.1, 0.5, 0.4));
        x3.add(Arrays.asList(0.2, 0.2, 0.6));
        ThermoCondition tc3 = new ThermoCondition(1000.0, 100000.0, x3);
        assertEquals("dof (3 phases, 3 comp)", 2, tc3.dof());
    }

    private static void testImmutabilityOfXList() {
        ArrayList<List<Double>> x = new ArrayList<>();
        ArrayList<Double> row = new ArrayList<>(Arrays.asList(0.4, 0.6));
        x.add(row);

        ThermoCondition tc = new ThermoCondition(300.0, 50000.0, x);

        // Mutating the original list should not affect the ThermoCondition
        row.set(0, 0.99);
        assertEquals("immutability: original list mutation", 0.4, tc.getX().get(0).get(0));

        // Mutating the original outer list should not affect the ThermoCondition
        x.add(Arrays.asList(0.1, 0.9));
        assertEquals("immutability: outer list mutation", 1, tc.getPhaseCount());

        // Attempting to mutate the returned list should fail
        boolean threw = false;
        try {
            tc.getX().add(Arrays.asList(0.5, 0.5));
        } catch (UnsupportedOperationException e) {
            threw = true;
        }
        assertTrue("immutability: outer list unmodifiable", threw);

        threw = false;
        try {
            tc.getX().get(0).set(0, 0.99);
        } catch (UnsupportedOperationException e) {
            threw = true;
        }
        assertTrue("immutability: inner list unmodifiable", threw);
    }

    private static void testEmptyCompositions() {
        ArrayList<List<Double>> x = new ArrayList<>();
        ThermoCondition tc = new ThermoCondition(200.0, 101325.0, x);
        assertEquals("empty: phaseCount", 0, tc.getPhaseCount());
        assertEquals("empty: componentCount", 0, tc.getComponentCount());
    }

    private static void testMultiPhase() {
        ArrayList<List<Double>> x = new ArrayList<>();
        x.add(Arrays.asList(0.25, 0.75));
        x.add(Arrays.asList(0.80, 0.20));

        ThermoCondition tc = new ThermoCondition(800.0, 101325.0, x);
        assertEquals("multiPhase: phaseCount", 2, tc.getPhaseCount());
        assertEquals("multiPhase: x[1][0]", 0.80, tc.getX().get(1).get(0));
    }

    // --- Assertion helpers ---

    private static void assertEquals(String name, double expected, double actual) {
        if (Math.abs(expected - actual) < 1e-12) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + name + " — expected " + expected + " but got " + actual);
        }
    }

    private static void assertEquals(String name, int expected, int actual) {
        if (expected == actual) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + name + " — expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(String name, boolean value) {
        if (value) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + name + " — expected true");
        }
    }
}
