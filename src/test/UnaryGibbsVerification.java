package test;

import domain.UnaryGibbs;
import thermocalc.unary.PhaseType;
import database.tdb;

import java.io.IOException;

/**
 * Verification test for unary SGTE Gibbs energy calculations.
 *
 * <p>Loads V and Zr element data from the SGTE Unary database and evaluates
 * G°(BCC_A2, 1000K) using the built-in parser and thermodynamic functions.
 *
 * <h2>Test Elements and Phases</h2>
 * - V (Vanadium), BCC_A2 phase at 1000 K
 * - Zr (Zirconium), BCC_A2 phase at 1000 K
 *
 * <h2>Method</h2>
 * Uses the full TDB parsing pipeline:
 * <ol>
 *   <li>Load TDB file with {@code tdb} parser</li>
 *   <li>Extract pure-element G parameters for BCC_A2 phase</li>
 *   <li>Evaluate SGTE polynomial at T=1000K</li>
 * </ol>
 */
public class UnaryGibbsVerification {

    public static void main(String[] args) {
        try {
            System.out.println("========================================");
            System.out.println("  Unary Gibbs Energy Verification");
            System.out.println("========================================\n");

            System.out.println("Loading SGTE Unary Database (data/Unary.TDB)...");
            tdb database = new tdb("data/Unary.TDB");
            System.out.println("✓ TDB file parsed successfully\n");

            // Load all elements from the database
            UnaryGibbs ug = UnaryGibbs.fromTdb(database);
            System.out.println("✓ Registry created with " + ug.registeredElements().size() + " elements\n");

            // Temperature for evaluation
            double T = 1000.0;  // Kelvin
            String phase = PhaseType.BCC_A2;

            System.out.println("========================================");
            System.out.println("Evaluating G°(" + phase + ", " + T + "K)");
            System.out.println("========================================\n");

            // Verify V (Vanadium)
            if (ug.contains("V")) {
                evaluateElement(ug, "V", phase, T);
            } else {
                System.out.println("✗ Element V not found");
            }

            // Verify Zr (Zirconium)
            if (ug.contains("ZR")) {
                evaluateElement(ug, "ZR", phase, T);
            } else {
                System.out.println("✗ Element ZR not found");
            }

            System.out.println("========================================");
            System.out.println("✓ Verification complete!");
            System.out.println("========================================");

        } catch (IOException e) {
            System.err.println("✗ Error loading TDB file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("✗ Error during verification: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Evaluate G°(phase, T) for a specific element.
     */
    private static void evaluateElement(UnaryGibbs ug, String element, String phase, double T) {
        try {
            double g = ug.gibbs(element, phase, T);
            System.out.println(element + ":");
            System.out.println("  G°(" + phase + ", " + (int)T + "K) = " + String.format("%.6f", g) + " J/mol");
            System.out.println("✓ " + element + " " + phase + " evaluated\n");
        } catch (UnsupportedOperationException e) {
            System.out.println(element + ":");
            System.out.println("  ✗ " + phase + " not available for " + element);
            System.out.println("  Available phases: " + ug.availablePhases(element) + "\n");
        } catch (Exception e) {
            System.out.println(element + ":");
            System.out.println("  ✗ Error: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}
