package test;

import domain.ThermoResult;

/**
 * Unit tests for ThermoResult value object.
 * Run with: java -cp build/classes test.ThermoResultTest
 */
public class ThermoResultTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testBasicConstruction();
        testImmutabilityGx();
        testImmutabilityGxx();
        testNullArrays();

        System.out.println("\n=== ThermoResultTest Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testBasicConstruction() {
        double[] Gx = {-5000.0, -3000.0};
        double[] GTx = {-12.0, -8.5};
        double[] GPx = {0.001, 0.002};
        double[][] Gxx = {{100.0, -50.0}, {-50.0, 200.0}};

        ThermoResult r = new ThermoResult(-45000.0, -10.5, 0.0001, Gx, GTx, GPx, Gxx);

        assertEquals("G", -45000.0, r.getG());
        assertEquals("GT", -10.5, r.getGT());
        assertEquals("GP", 0.0001, r.getGP());
        assertEquals("Gx[0]", -5000.0, r.getGx()[0]);
        assertEquals("Gx[1]", -3000.0, r.getGx()[1]);
        assertEquals("GTx[0]", -12.0, r.getGTx()[0]);
        assertEquals("GPx[1]", 0.002, r.getGPx()[1]);
        assertEquals("Gxx[0][0]", 100.0, r.getGxx()[0][0]);
        assertEquals("Gxx[0][1]", -50.0, r.getGxx()[0][1]);
        assertEquals("Gxx[1][1]", 200.0, r.getGxx()[1][1]);
    }

    private static void testImmutabilityGx() {
        double[] Gx = {1.0, 2.0};
        ThermoResult r = new ThermoResult(0, 0, 0, Gx, null, null, null);

        // Mutate original — should not affect result
        Gx[0] = 999.0;
        assertEquals("immutability: original Gx mutation", 1.0, r.getGx()[0]);

        // Mutate returned array — should not affect internal state
        double[] returned = r.getGx();
        returned[0] = 888.0;
        assertEquals("immutability: returned Gx mutation", 1.0, r.getGx()[0]);
    }

    private static void testImmutabilityGxx() {
        double[][] Gxx = {{10.0, 20.0}, {30.0, 40.0}};
        ThermoResult r = new ThermoResult(0, 0, 0, null, null, null, Gxx);

        // Mutate original
        Gxx[0][0] = 999.0;
        assertEquals("immutability: original Gxx mutation", 10.0, r.getGxx()[0][0]);

        // Mutate returned
        double[][] returned = r.getGxx();
        returned[1][1] = 888.0;
        assertEquals("immutability: returned Gxx mutation", 40.0, r.getGxx()[1][1]);
    }

    private static void testNullArrays() {
        ThermoResult r = new ThermoResult(-100.0, -1.0, 0.001, null, null, null, null);
        assertTrue("null Gx", r.getGx() == null);
        assertTrue("null GTx", r.getGTx() == null);
        assertTrue("null GPx", r.getGPx() == null);
        assertTrue("null Gxx", r.getGxx() == null);
        assertEquals("G with null arrays", -100.0, r.getG());
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

    private static void assertTrue(String name, boolean value) {
        if (value) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + name + " — expected true");
        }
    }
}
