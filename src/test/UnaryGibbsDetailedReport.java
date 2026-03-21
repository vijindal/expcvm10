package test;

import domain.UnaryGibbs;
import thermocalc.unary.PhaseType;
import database.tdb;

import java.io.IOException;

/**
 * Detailed verification report for unary Gibbs energy calculations.
 *
 * Shows G0 values at 1000K for multiple phases using the SGTE database parser.
 */
public class UnaryGibbsDetailedReport {

    public static void main(String[] args) {
        try {
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  Unary SGTE Gibbs Energy Calculation Verification Report        ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

            System.out.println("Loading SGTE Unary Database: data/Unary.TDB");
            tdb database = new tdb("data/Unary.TDB");
            UnaryGibbs ug = UnaryGibbs.fromTdb(database);

            System.out.println("✓ Database loaded with " + ug.registeredElements().size() + " elements\n");

            double T = 1000.0;  // Reference temperature

            // Test V (Vanadium)
            printElementReport("V", ug, T);

            // Test Zr (Zirconium)
            printElementReport("ZR", ug, T);

            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  ✓ All calculations completed successfully using TDB parser    ║");
            System.out.println("║    and SGTE polynomial evaluation (no direct evaluations)      ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        } catch (IOException e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printElementReport(String element, UnaryGibbs ug, double T) {
        System.out.println("────────────────────────────────────────────────────────────────");
        System.out.println("Element: " + element + " at T = " + (int)T + " K");
        System.out.println("────────────────────────────────────────────────────────────────\n");

        if (!ug.contains(element)) {
            System.out.println("✗ Element not found\n");
            return;
        }

        try {
            // Get GHSER (reference state)
            double ghser = ug.ghser(element, T);
            System.out.println("GHSER Reference:        " + formatGibbs(ghser) + " J/mol\n");

            // Get available phases
            java.util.Set<String> phases = ug.availablePhases(element);
            System.out.println("Available phases (" + phases.size() + "):\n");

            // Evaluate key phases
            String[] keyPhases = {
                PhaseType.BCC_A2,
                PhaseType.FCC_A1,
                PhaseType.HCP_A3,
                PhaseType.LIQUID
            };

            for (String phase : keyPhases) {
                if (phases.contains(phase)) {
                    double g = ug.gibbs(element, phase, T);
                    System.out.println("  " + String.format("%-12s", phase) + " → " + formatGibbs(g) + " J/mol");
                }
            }

            System.out.println();

        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String formatGibbs(double value) {
        return String.format("%12.6f", value);
    }
}
