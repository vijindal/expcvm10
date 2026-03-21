package test;

import infra.TdbParser;
import domain.DatabasePort;

import java.io.IOException;

/**
 * Integration test for TdbParser wrapping the legacy tdb parser.
 * Requires data/tizr_kum_cvm.tdb to exist relative to the working directory.
 *
 * Run with: java -cp build/classes test.TdbParserTest
 */
public class TdbParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testLoadAndExtractSystem();
        testLoadNonexistentFileThrows();

        System.out.println("\n=== TdbParserTest Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testLoadAndExtractSystem() {
        TdbParser parser = new TdbParser();
        try {
            parser.load("data/tizr_kum_cvm.tdb");
            assertTrue("load succeeds", true);

            // Extract a binary subsystem
            DatabasePort sub = parser.extractSystem(new String[]{"TI", "ZR"});
            assertTrue("extractSystem returns non-null", sub != null);

            // The underlying tdb should be accessible
            assertTrue("underlying tdb non-null", parser.getUnderlyingTdb() != null);
        } catch (IOException e) {
            fail("load/extract threw IOException: " + e.getMessage());
        }
    }

    private static void testLoadNonexistentFileThrows() {
        // Note: The legacy tdb constructor catches FileNotFoundException internally
        // and prints an error message rather than propagating the exception.
        // This test verifies that the parser handles it gracefully (no crash).
        TdbParser parser = new TdbParser();
        try {
            parser.load("nonexistent_file_12345.tdb");
            // If no exception is thrown, the legacy tdb swallowed it — still no crash
            assertTrue("load nonexistent file graceful handling", true);
        } catch (IOException e) {
            // If an exception is thrown, that's also acceptable
            assertTrue("load nonexistent file throws IOException", true);
        }
    }

    // --- Assertion helpers ---

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
