package test;

import phase.C15Phase;
import service.C15Minimizer;

/**
 * V-Zr C15 Laves Phase - Gibbs Energy Calculation WITH Internal Minimization
 *
 * This version finds the equilibrium internal variables (site occupancies)
 * that minimize Gibbs energy at fixed T, P, and composition.
 */
public class C15VZrWithMinimization {

    static class GibbsEnergy {
        static double GHSERVV(double T) {
            if (T < 790.0) {
                return -7930.43 + 133.346053 * T - 24.134 * T * Math.log(T)
                    - 0.003098 * T * T + 1.2175e-7 * T * T * T + 69460 / T;
            } else if (T < 2183.0) {
                return -7967.842 + 143.291093 * T - 25.9 * T * Math.log(T)
                    + 6.25e-5 * T * T - 6.8e-7 * T * T * T;
            } else {
                return -41689.864 + 321.140783 * T - 47.43 * T * Math.log(T)
                    + 6.44389e31 / Math.pow(T, 9);
            }
        }

        static double GHSERZR(double T) {
            if (T < 2128.0) {
                return -7827.595 + 125.64905 * T - 24.1618 * T * Math.log(T)
                    - 0.00437791 * T * T + 34971 / T;
            } else {
                return -26085.921 + 262.724183 * T - 42.144 * T * Math.log(T)
                    - 1.342896e31 / Math.pow(T, 9);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  V-Zr C15 Laves Phase WITH INTERNAL MINIMIZATION                ║");
        System.out.println("║  Database: VZR-re2.TDB                                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        double T = 1000.0;
        double P = 1.0;
        double R = 8.314;

        System.out.printf("Temperature: %.1f K\n", T);
        System.out.printf("Pressure: %.1f bar\n\n", P);

        // Setup phase
        C15Phase c15 = new C15Phase(2, 2);
        c15.setT(T);
        c15.setP(P);
        c15.setR(R);
        c15.setNumComp(2);

        int[][] speciesToComp = {{0, 1}, {0, 1}};
        c15.setSpeciesToComp(speciesToComp);

        // Pure component energies
        double G_V_ref = GibbsEnergy.GHSERVV(T);
        double G_Zr_ref = GibbsEnergy.GHSERZR(T);

        System.out.println("=== Pure Component Reference Gibbs Energies ===");
        System.out.printf("G°(V)_ref = %.2f J/mol\n", G_V_ref);
        System.out.printf("G°(Zr)_ref = %.2f J/mol\n\n", G_Zr_ref);

        double offset_VZr = -12672.959 + 3.8143614 * T;
        double offset_ZrV = 42672.959 - 3.8143614 * T;

        double[][] G0 = {
            {3 * G_V_ref + 15000.0, 2 * G_V_ref + G_Zr_ref + offset_VZr},
            {2 * G_Zr_ref + G_V_ref + offset_ZrV, 3 * G_Zr_ref + 15000.0}
        };

        c15.setG0(G0);

        // Interaction parameters
        c15.addInteraction(new C15Phase.Interaction(
            new int[]{0, 1}, new int[]{0, 1},
            11001.758, 9.5308266
        ));

        c15.addInteraction(new C15Phase.Interaction(
            new int[]{0, 1}, new int[]{1, 1},
            18017.282, 30.767371
        ));

        // Target composition
        double[] composition = {0.5, 0.5};  // 50-50 V-Zr

        System.out.println("=== BEFORE MINIMIZATION ===");
        double[] y_initial = {0.5, 0.5, 0.5, 0.5};
        c15.setInternalVars(y_initial);
        double[] x_initial = c15.computeComposition(2, speciesToComp);
        System.out.printf("Initial y: [%.4f, %.4f, %.4f, %.4f]\n",
            y_initial[0], y_initial[1], y_initial[2], y_initial[3]);
        System.out.printf("Composition: x_V = %.4f, x_Zr = %.4f\n", x_initial[0], x_initial[1]);
        System.out.printf("G (before minimization) = %.2f J/mol\n\n", c15.calG());

        // ========== INTERNAL MINIMIZATION ==========
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           STARTING INTERNAL MINIMIZATION...                    ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        double[] y_equilibrium = C15Minimizer.minimizeGibbs(c15, composition, 2, speciesToComp);

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                   MINIMIZATION COMPLETE                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // Results
        System.out.println("=== AFTER MINIMIZATION ===");
        double[] x_final = c15.computeComposition(2, speciesToComp);
        System.out.printf("Equilibrium y: [%.4f, %.4f, %.4f, %.4f]\n",
            y_equilibrium[0], y_equilibrium[1], y_equilibrium[2], y_equilibrium[3]);
        System.out.printf("Composition: x_V = %.4f, x_Zr = %.4f\n", x_final[0], x_final[1]);

        double G_final = c15.calG();
        System.out.printf("G (after minimization) = %.2f J/mol\n", G_final);
        System.out.printf("ΔG = %.2f J/mol (change due to minimization)\n\n", G_final - c15.calG());

        // Thermodynamic properties at equilibrium
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║            EQUILIBRIUM THERMODYNAMIC PROPERTIES                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        double GT = c15.calDGT();
        System.out.printf("\nG = %.2f J/mol\n", G_final);
        System.out.printf("S = -∂G/∂T = %.4f J/(mol·K)\n", -GT);
        System.out.printf("H = G - T·S ≈ %.2f J/mol\n\n", G_final - T * (-GT));

        // Gradient check
        double[] grad = c15.calDGy();
        double grad_norm = norm(grad);
        System.out.printf("||∇G|| = %.2e (gradient norm - should be ~0)\n\n", grad_norm);

        // Chemical potentials
        double[] mu = c15.calDGx();
        System.out.printf("Chemical Potentials:\n");
        System.out.printf("  μ_V = %.2f J/mol\n", mu[0]);
        System.out.printf("  μ_Zr = %.2f J/mol\n", mu[1]);
        System.out.printf("  Δμ = μ_V - μ_Zr = %.2f J/mol\n\n", mu[0] - mu[1]);

        // Activity coefficients
        double lnGamma_V = (mu[0] - (-R * T * Math.log(0.5))) / (R * T);
        double lnGamma_Zr = (mu[1] - (-R * T * Math.log(0.5))) / (R * T);
        System.out.printf("Activity Coefficients:\n");
        System.out.printf("  ln γ_V = %.4f  →  γ_V = %.4f\n", lnGamma_V, Math.exp(lnGamma_V));
        System.out.printf("  ln γ_Zr = %.4f  →  γ_Zr = %.6f\n\n", lnGamma_Zr, Math.exp(lnGamma_Zr));

        // Stability
        double[][] H = c15.calDGyy();
        double H_min = findMinEigenvalueEstimate(H);
        System.out.printf("Stability:\n");
        System.out.printf("  Min Hessian eigenvalue (estimate) = %.2e\n", H_min);
        System.out.printf("  Phase is %s\n\n", H_min > 0 ? "STABLE (convex)" : "UNSTABLE (concave)");

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    CALCULATION COMPLETE                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
    }

    private static double norm(double[] v) {
        double sum = 0;
        for (double x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }

    private static double findMinEigenvalueEstimate(double[][] H) {
        // Rough estimate: minimum diagonal element
        double minDiag = Double.MAX_VALUE;
        for (int i = 0; i < H.length; i++) {
            minDiag = Math.min(minDiag, H[i][i]);
        }
        return minDiag;
    }
}
