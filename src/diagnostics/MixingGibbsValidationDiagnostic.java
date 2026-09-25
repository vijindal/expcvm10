package diagnostics;

import system.database.tdb;
import system.model.GibbsEnergyModel;
import system.model.cvm.CvmGibbsModel;
import system.model.cvm.TdbCvmModelBuilder;

import java.io.IOException;
import java.util.Arrays;

/**
 * Reference-state-independent validation: compare mixing Gibbs energies
 * and equilibrium correlation functions.
 *
 * Tests three compositions:
 * 1. x(V)=0.6, x(Zr)=0.4
 * 2. x(V)=1.0, x(Zr)=0.0
 * 3. x(V)=0.0, x(Zr)=1.0
 *
 * Computes Gmix independent of unary reference state.
 */
public class MixingGibbsValidationDiagnostic {

    public static void main(String[] args) throws IOException {
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("TDB-Driven CVM: Mixing Gibbs Energy Validation");
        System.out.println("════════════════════════════════════════════════════════════════\n");

        double T = 1000.0;
        double P = 101325.0;

        tdb database = new tdb("data/VZR-re2-CVM-eName-model.TDB");
        GibbsEnergyModel model = TdbCvmModelBuilder.buildTdbCvmModel(
                database, Arrays.asList("V", "ZR"), "BCC_A2");

        if (!(model instanceof CvmGibbsModel)) {
            System.out.println("ERROR: Not a CvmGibbsModel!");
            return;
        }

        CvmGibbsModel cvmModel = (CvmGibbsModel) model;

        System.out.println("System: V-Zr BCC_A2 T CVM");
        System.out.println("T = " + T + " K, P = " + P + " Pa");
        System.out.println();

        // Three test compositions
        double[][] compositions = {
                {0.6, 0.4},  // Binary
                {1.0, 0.0},  // Pure V
                {0.0, 1.0}   // Pure Zr
        };

        String[] names = {"Binary (0.6, 0.4)", "Pure V", "Pure Zr"};

        double[] G_values = new double[3];
        double[][] CF_values = new double[3][4];  // 4 correlation functions

        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("Three-Point Calculation");
        System.out.println("─────────────────────────────────────────────────────────────");

        for (int i = 0; i < compositions.length; i++) {
            double[] x = compositions[i];
            String name = names[i];

            System.out.println("\nComputing " + name + ": x(V)=" + x[0] + ", x(Zr)=" + x[1]);

            double[] y = cvmModel.getInitialInternalVars(x);
            double G = cvmModel.G(T, P, y);

            G_values[i] = G;

            // Extract correlation functions (first 4 y-values)
            for (int j = 0; j < 4; j++) {
                CF_values[i][j] = y[j];
            }

            System.out.println("  G = " + G + " J/mol");
            System.out.println("  CF values:");
            System.out.println("    v4AB  = " + CF_values[i][0]);
            System.out.println("    v3AB  = " + CF_values[i][1]);
            System.out.println("    v2AB2 = " + CF_values[i][2]);
            System.out.println("    v2AB1 = " + CF_values[i][3]);

            // Verify composition round-trip
            double[] xOut = cvmModel.compositionFromInternal(y);
            boolean match = Math.abs(xOut[0] - x[0]) < 1e-12 && Math.abs(xOut[1] - x[1]) < 1e-12;
            System.out.println("  Composition round-trip: " + (match ? "✓" : "✗"));
        }

        // Calculate mixing properties
        System.out.println("\n" + "─".repeat(63));
        System.out.println("Mixing Properties (Reference-State-Independent)");
        System.out.println("─".repeat(63));

        double G_binary = G_values[0];
        double G_V = G_values[1];
        double G_Zr = G_values[2];

        double Gmix = G_binary - 0.6*G_V - 0.4*G_Zr;

        System.out.println("\nG(0.6,0.4) = " + G_binary + " J/mol");
        System.out.println("G(1.0,0.0) = " + G_V + " J/mol");
        System.out.println("G(0.0,1.0) = " + G_Zr + " J/mol");
        System.out.println();
        System.out.println("Gmix = G(0.6,0.4) - 0.6*G(1.0,0.0) - 0.4*G(0.0,1.0)");
        System.out.println("Gmix = " + G_binary + " - 0.6*" + G_V + " - 0.4*" + G_Zr);
        System.out.println("Gmix = " + Gmix + " J/mol");

        // Correlation functions at binary state
        System.out.println("\n" + "─".repeat(63));
        System.out.println("Correlation Functions at x(V)=0.6, x(Zr)=0.4");
        System.out.println("─".repeat(63));

        System.out.println("v4AB  = " + CF_values[0][0]);
        System.out.println("v3AB  = " + CF_values[0][1]);
        System.out.println("v2AB2 = " + CF_values[0][2]);
        System.out.println("v2AB1 = " + CF_values[0][3]);

        // Comparison with CEWorkbench (from previous run)
        System.out.println("\n" + "═".repeat(63));
        System.out.println("Comparison with CEWorkbench");
        System.out.println("═".repeat(63));

        double Gmix_CEW = -6115.713483763183;

        System.out.println("\nMixing Gibbs Energy:");
        System.out.println("  Gmix (CEWorkbench) = " + Gmix_CEW + " J/mol");
        System.out.println("  Gmix (TDB-Java)    = " + Gmix + " J/mol");
        System.out.println("  Difference         = " + (Gmix - Gmix_CEW) + " J/mol");
        System.out.println("  Relative diff      = " + (Math.abs(Gmix - Gmix_CEW) / Math.abs(Gmix_CEW) * 100.0) + "%");

        // CEWorkbench CF values from previous run
        double[] cf_CEW = {
                0.057444853986456434,
                -0.04925556389200098,
                0.242970062283239,
                0.2414510277422925
        };
        String[] cfNames = {"v4AB", "v3AB", "v2AB2", "v2AB1"};

        System.out.println("\nCorrelation Functions at x(V)=0.6, x(Zr)=0.4:");
        System.out.println();
        System.out.println(String.format("%-7s %-25s %-25s %-20s",
                "CF", "CEWorkbench", "TDB-Java", "Abs Difference"));
        System.out.println("─".repeat(77));

        for (int i = 0; i < 4; i++) {
            double diff = Math.abs(CF_values[0][i] - cf_CEW[i]);
            System.out.println(String.format("%-7s %-25.18e %-25.18e %-20.18e",
                    cfNames[i], cf_CEW[i], CF_values[0][i], diff));
        }

        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════");
    }
}
