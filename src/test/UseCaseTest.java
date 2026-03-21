package test;

import service.ExportDatabaseUseCase;
import service.CalculationRequest;
import service.CalculationResult;
import domain.ResultPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Use-case level tests using mock port implementations.
 * No external dependencies — tests pure application logic and wiring.
 *
 * Run with: java -cp build/classes test.UseCaseTest
 */
public class UseCaseTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testExportDatabaseUseCase();
        testCalculationRequestDto();
        testCalculationResultDto();

        System.out.println("\n=== UseCaseTest Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * Test ExportDatabaseUseCase with a mock ResultPort.
     */
    private static void testExportDatabaseUseCase() {
        MockResultPort mockPort = new MockResultPort();
        ExportDatabaseUseCase useCase = new ExportDatabaseUseCase(mockPort);

        try {
            useCase.execute("ELEMENT TI BCC_A2 47.88 0 0 !");
            assertTrue("MockPort received line", mockPort.lines.size() == 1);
            assertEquals("MockPort line content",
                    "ELEMENT TI BCC_A2 47.88 0 0 !", mockPort.lines.get(0));
            assertTrue("MockPort was closed", mockPort.closed);
        } catch (IOException e) {
            fail("ExportDatabaseUseCase threw: " + e.getMessage());
        }
    }

    /**
     * Test CalculationRequest DTO round-trip.
     */
    private static void testCalculationRequestDto() {
        CalculationRequest req = new CalculationRequest();
        req.setMethod("HM");
        req.setT(1200.0);
        req.setP(101325.0);
        req.setTdbFilePath("/path/to/test.tdb");
        req.setElements(new ArrayList<>(Arrays.asList("TI", "ZR")));
        req.setPhases(new ArrayList<>(Arrays.asList("LIQUID", "BCC_A2")));

        ArrayList<ArrayList<Double>> comps = new ArrayList<>();
        ArrayList<Double> row = new ArrayList<>(Arrays.asList(0.3, 0.3, 0.4));
        comps.add(row);
        req.setCompositions(comps);

        assertEquals("method", "HM", req.getMethod());
        assertEquals("T", 1200.0, req.getT());
        assertEquals("P", 101325.0, req.getP());
        assertEquals("tdbFilePath", "/path/to/test.tdb", req.getTdbFilePath());
        assertEquals("elements size", 2, req.getElements().size());
        assertEquals("phases[1]", "BCC_A2", req.getPhases().get(1));
        assertEquals("compositions[0][2]", 0.4, req.getCompositions().get(0).get(2));
    }

    /**
     * Test CalculationResult DTO defaults and setters.
     */
    private static void testCalculationResultDto() {
        CalculationResult res = new CalculationResult();

        // Default success should be true
        assertTrue("default success", res.isSuccess());

        res.setSuccess(false);
        res.setMessage("Test error");
        res.setMethod("Gm");
        res.setValue(-45000.0);
        res.setTemperature(500.0);
        res.setPressure(10000.0);

        assertTrue("success set to false", !res.isSuccess());
        assertEquals("message", "Test error", res.getMessage());
        assertEquals("method", "Gm", res.getMethod());
        assertEquals("value", -45000.0, res.getValue());
        assertEquals("temperature", 500.0, res.getTemperature());
        assertEquals("pressure", 10000.0, res.getPressure());
    }

    // --- Mock ResultPort ---

    private static class MockResultPort implements ResultPort {
        final ArrayList<String> lines = new ArrayList<>();
        boolean closed = false;

        @Override
        public void writeLine(String line) throws IOException {
            lines.add(line);
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }

    // --- Assertion helpers ---

    private static void assertEquals(String name, String expected, String actual) {
        if (expected.equals(actual)) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + name + " — expected \"" + expected + "\" but got \"" + actual + "\"");
        }
    }

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

    private static void fail(String message) {
        failed++;
        System.err.println("FAIL: " + message);
    }
}
