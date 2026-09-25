package diagnostics;

import system.database.tdb;
import system.model.GibbsEnergyModel;
import system.model.cvm.CvmGibbsModel;
import system.model.cvm.TdbCvmModelBuilder;

import java.io.IOException;
import java.util.Arrays;

/**
 * Detailed cross-check: investigates the source of the G difference.
 *
 * Checks:
 * 1. CEC values used by the TDB model
 * 2. Unary Gibbs energies (element reference states)
 * 3. Pressure handling
 * 4. Composition convention (mole fractions vs site fractions)
 */
public class DetailedCeWorkbenchCrossCheckDiagnostic {

    public static void main(String[] args) throws IOException {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("Detailed CEWorkbench Cross-Check");
        System.out.println("════════════════════════════════════════════════════════════════\n");

        double T = 1000.0;
        double P = 101325.0;
        double[] x = {0.6, 0.4}; // V, Zr (mole fractions)

        // Load TDB and build model
        System.out.println("Loading TDB: data/VZR-re2-CVM-eName-model.TDB");
        tdb database = new tdb("data/VZR-re2-CVM-eName-model.TDB");

        System.out.println("Building TDB-driven CVM model for V-Zr BCC_A2...\n");
        GibbsEnergyModel tdbModel = TdbCvmModelBuilder.buildTdbCvmModel(
                database, Arrays.asList("V", "ZR"), "BCC_A2");

        // Extract the underlying CvmGibbsModel to access CEC values
        if (!(tdbModel instanceof CvmGibbsModel)) {
            System.out.println("ERROR: Model is not CvmGibbsModel!");
            return;
        }

        CvmGibbsModel cvmModel = (CvmGibbsModel) tdbModel;

        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("CEC terms used by TDB model:");
        System.out.println("─────────────────────────────────────────────────────────────");

        // Try to access CEC terms
        try {
            // Note: cecEvaluator is accessible via public method
            double[] cecValues = cvmModel.cecEvaluator().evaluate(T);
            System.out.println("CEC values at T=" + T + "K:");
            System.out.println("  cecValues[0] = " + cecValues[0] + " (v4AB)");
            System.out.println("  cecValues[1] = " + cecValues[1] + " (v3AB)");
            System.out.println("  cecValues[2] = " + cecValues[2] + " (v2AB2)");
            System.out.println("  cecValues[3] = " + cecValues[3] + " (v2AB1)");
        } catch (Exception e) {
            System.out.println("Could not access CEC values: " + e.getMessage());
        }

        System.out.println("\n─────────────────────────────────────────────────────────────");
        System.out.println("Unary reference Gibbs energies:");
        System.out.println("─────────────────────────────────────────────────────────────");

        // Evaluate unary Gibbs energies to see what's used
        double[] y = cvmModel.getInitialInternalVars(x);

        System.out.println("  T = " + T + " K");
        System.out.println("  P = " + P + " Pa");
        System.out.println("  x(V) = " + x[0]);
        System.out.println("  x(Zr) = " + x[1]);

        double G_total = cvmModel.G(T, P, y);
        System.out.println("\n  Total G (from model): " + G_total + " J/mol");

        System.out.println("\n─────────────────────────────────────────────────────────────");
        System.out.println("y-vector structure (4 CFC + 2 composition):");
        System.out.println("─────────────────────────────────────────────────────────────");
        for (int i = 0; i < y.length; i++) {
            if (i < 4) {
                String cfName = "";
                switch (i) {
                    case 0: cfName = "v4AB"; break;
                    case 1: cfName = "v3AB"; break;
                    case 2: cfName = "v2AB2"; break;
                    case 3: cfName = "v2AB1"; break;
                }
                System.out.println("  y[" + i + "] = " + y[i] + " (" + cfName + ")");
            } else {
                int elemIdx = i - 4;
                String elemName = (elemIdx == 0) ? "V" : "Zr";
                System.out.println("  y[" + i + "] = " + y[i] + " (x_" + elemName + ")");
            }
        }

        System.out.println("\n─────────────────────────────────────────────────────────────");
        System.out.println("Comparison with CEWorkbench:");
        System.out.println("─────────────────────────────────────────────────────────────");

        double G_CEW = -6115.713483763183;
        double absDiff = Math.abs(G_total - G_CEW);
        double relDiff = absDiff / Math.abs(G_CEW);

        System.out.println("  G (TDB):        " + G_total + " J/mol");
        System.out.println("  G (CEWorkbench): " + G_CEW + " J/mol");
        System.out.println("  Absolute diff:   " + absDiff + " J/mol");
        System.out.println("  Relative diff:   " + (relDiff * 100.0) + "%");

        System.out.println("\n════════════════════════════════════════════════════════════════");
    }
}
