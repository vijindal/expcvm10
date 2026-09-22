package diagnostics;

import system.database.tdb;
import system.model.GibbsEnergyModel;
import system.model.cvm.TdbCvmModelBuilder;

import java.io.IOException;
import java.util.Arrays;

/**
 * Cross-check TDB-driven CVM model against CEWorkbench API calculation.
 *
 * Exact test case:
 *   System: V-Zr
 *   Phase: BCC_A2
 *   Model: T_CVCF / T_CVM
 *   T = 1000 K
 *   x(V) = 0.6, x(Zr) = 0.4
 *   P = 101325 Pa
 *
 * CEWorkbench result:
 *   G = -6115.713483763183 J/mol
 *   H = -522.5546686831556 J/mol
 *   S = 5.593158815080027 J/(mol·K)
 */
public class CeWorkbenchCrossCheckDiagnostic {

    public static void main(String[] args) throws IOException {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("CEWorkbench Cross-Check: TDB-Driven CVM Model");
        System.out.println("════════════════════════════════════════════════════════════════\n");

        double T = 1000.0;
        double P = 101325.0;
        double[] x = {0.6, 0.4}; // V, Zr

        // Load TDB and build model
        System.out.println("Loading TDB: data/VZR-re2-CVM-eName-model.TDB");
        tdb database = new tdb("data/VZR-re2-CVM-eName-model.TDB");

        System.out.println("Building TDB-driven CVM model for V-Zr BCC_A2...");
        GibbsEnergyModel tdbModel = TdbCvmModelBuilder.buildTdbCvmModel(
                database, Arrays.asList("V", "ZR"), "BCC_A2");

        System.out.println("\n─────────────────────────────────────────────────────────────");
        System.out.println("Model structure:");
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("  Phase name:      " + tdbModel.phaseName());
        System.out.println("  Model type:      " + tdbModel.modelType());
        System.out.println("  Components:      " + tdbModel.numComponents());
        System.out.println("  Site variables:  " + tdbModel.numSiteVars());
        System.out.println("  Sublattices:     " + tdbModel.numSublattices());

        // Get initial state
        System.out.println("\n─────────────────────────────────────────────────────────────");
        System.out.println("Thermodynamic state:");
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("  T:               " + T + " K");
        System.out.println("  P:               " + P + " Pa");
        System.out.println("  x(V):            " + x[0]);
        System.out.println("  x(Zr):           " + x[1]);

        double[] y = tdbModel.getInitialInternalVars(x);
        System.out.println("\n  Initial y-vector (6 components):");
        for (int i = 0; i < y.length; i++) {
            System.out.println("    y[" + i + "] = " + y[i]);
        }

        // Verify composition round-trip
        double[] xRoundTrip = tdbModel.compositionFromInternal(y);
        System.out.println("\n  Composition round-trip:");
        System.out.println("    Input:  x = [" + x[0] + ", " + x[1] + "]");
        System.out.println("    Output: x = [" + xRoundTrip[0] + ", " + xRoundTrip[1] + "]");
        System.out.println("    Match:  " + Arrays.equals(x, xRoundTrip));

        // Evaluate Gibbs energy
        double G = tdbModel.G(T, P, y);

        System.out.println("\n─────────────────────────────────────────────────────────────");
        System.out.println("Gibbs energy result:");
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("  G (TDB-driven):     " + G + " J/mol");

        // CEWorkbench reference
        double G_CEW = -6115.713483763183;
        double H_CEW = -522.5546686831556;
        double S_CEW = 5.593158815080027;

        System.out.println("\n─────────────────────────────────────────────────────────────");
        System.out.println("CEWorkbench reference (API calculation):");
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("  G (CEWorkbench):    " + G_CEW + " J/mol");
        System.out.println("  H (CEWorkbench):    " + H_CEW + " J/mol");
        System.out.println("  S (CEWorkbench):    " + S_CEW + " J/(mol·K)");

        System.out.println("\n─────────────────────────────────────────────────────────────");
        System.out.println("Comparison:");
        System.out.println("─────────────────────────────────────────────────────────────");
        double absDiff = Math.abs(G - G_CEW);
        double relDiff = absDiff / Math.abs(G_CEW);

        System.out.println("  Absolute difference: " + absDiff + " J/mol");
        System.out.println("  Relative difference: " + (relDiff * 100.0) + "%");

        if (Double.isFinite(G)) {
            System.out.println("\n✓ TDB-driven model produced finite G value");
        } else {
            System.out.println("\n✗ TDB-driven model produced non-finite G value!");
        }

        System.out.println("\n════════════════════════════════════════════════════════════════");
    }
}
