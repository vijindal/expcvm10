package test;

import phase.C15Phase;

/**
 * V-Zr C15 Laves Phase (V2ZR) Gibbs Energy Calculation
 * Database: VZR-re2.TDB
 *
 * V2ZR structure:
 * - Sublattice 0: V,ZR with multiplicity a_0 = 2
 * - Sublattice 1: V,ZR with multiplicity a_1 = 1
 *
 * Pure component Gibbs energies (per mole of C15, i.e., V2ZR formula):
 * G(V:V) = 3*G°(V)_ref + 15000 J/mol
 * G(ZR:V) = 2*G°(ZR)_ref + G°(V)_ref + 42672.959 - 3.8143614*T
 * G(V:ZR) = 2*G°(V)_ref + G°(ZR)_ref - 12672.959 + 3.8143614*T
 * G(ZR:ZR) = 3*G°(ZR)_ref + 15000 J/mol
 *
 * Interaction parameters:
 * L(V:V,ZR) = 11001.758 + 9.5308266*T J/mol
 * L(V,ZR:ZR) = 18017.282 + 30.767371*T J/mol
 */
public class C15VZrCalculation {

    // Gibbs energy expressions from database (per atom, valid at specific T ranges)
    static class GibbsEnergy {
        /**
         * GHSERVV - Gibbs energy of V reference state
         * Valid range: 298.15 - 4000 K
         */
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

        /**
         * GHSERZR - Gibbs energy of Zr reference state
         * Valid range: 298.15 - 6000 K
         */
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
        System.out.println("║  V-Zr C15 Laves Phase (V2ZR) - Gibbs Energy Calculation        ║");
        System.out.println("║  Database: VZR-re2.TDB                                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // Temperature and pressure
        double T = 1000.0;  // Kelvin
        double P = 1.0;     // Bar (standard pressure)
        double R = 8.314;   // Gas constant J/(mol·K)

        System.out.printf("Temperature: %.1f K\n", T);
        System.out.printf("Pressure: %.1f bar\n\n", P);

        // Pure component Gibbs energies (J/mol)
        double G_V_ref = GibbsEnergy.GHSERVV(T);
        double G_Zr_ref = GibbsEnergy.GHSERZR(T);

        System.out.println("=== Pure Component Reference Gibbs Energies ===");
        System.out.printf("G°(V)_ref = %.2f J/mol\n", G_V_ref);
        System.out.printf("G°(Zr)_ref = %.2f J/mol\n\n", G_Zr_ref);

        // V2ZR Laves phase (2:1 stoichiometry)
        // Per formula unit V2Zr, sublattice 0 has 2 atoms, sublattice 1 has 1 atom
        // Internal variables: y_0V, y_0Zr, y_1V, y_1Zr
        // Constraints: y_0V + y_0Zr = 1.0,  y_1V + y_1Zr = 1.0

        C15Phase c15 = new C15Phase(2, 2);
        c15.setT(T);
        c15.setP(P);
        c15.setR(R);
        c15.setNumComp(2);

        // Component mapping: 0=V, 1=Zr on both sublattices
        int[][] speciesToComp = {{0, 1}, {0, 1}};
        c15.setSpeciesToComp(speciesToComp);

        // Pure component Gibbs energies on the two sublattices
        // G0[i][j] = G(species_i on subL0, species_j on subL1)
        //
        // Mapping from database:
        // G(V:V) = 3*G°(V)_ref + 15000              → [0][0] = 3*G_V_ref + 15000
        // G(ZR:V) = 2*G°(Zr)_ref + G°(V)_ref + offset → [1][0] = 2*G_Zr_ref + G_V_ref + offset
        // G(V:ZR) = 2*G°(V)_ref + G°(Zr)_ref + offset → [0][1] = 2*G_V_ref + G_Zr_ref + offset
        // G(ZR:ZR) = 3*G°(Zr)_ref + 15000            → [1][1] = 3*G_Zr_ref + 15000

        double offset_VZr = -12672.959 + 3.8143614 * T;  // Temperature-dependent
        double offset_ZrV = 42672.959 - 3.8143614 * T;

        double[][] G0 = {
            {3 * G_V_ref + 15000.0, 2 * G_V_ref + G_Zr_ref + offset_VZr},
            {2 * G_Zr_ref + G_V_ref + offset_ZrV, 3 * G_Zr_ref + 15000.0}
        };

        System.out.println("=== V2ZR Lattice Energies (J/mol) ===");
        System.out.printf("G(V:V)   = 3*G°(V) + 15000 = %.2f J/mol\n", G0[0][0]);
        System.out.printf("G(V:Zr)  = 2*G°(V) + G°(Zr) + offset = %.2f J/mol\n", G0[0][1]);
        System.out.printf("G(Zr:V)  = 2*G°(Zr) + G°(V) + offset = %.2f J/mol\n", G0[1][0]);
        System.out.printf("G(Zr:Zr) = 3*G°(Zr) + 15000 = %.2f J/mol\n\n", G0[1][1]);

        c15.setG0(G0);

        // Interaction parameters
        double L_VV_Zr = 11001.758 + 9.5308266 * T;     // V on subL0, Zr on subL1
        double L_V_ZrZr = 18017.282 + 30.767371 * T;    // V on subL0, Zr on subL1

        System.out.println("=== Interaction Parameters ===");
        System.out.printf("L(V:V,Zr) = 11001.758 + 9.5308266*T = %.2f J/mol\n", L_VV_Zr);
        System.out.printf("L(V,Zr:Zr) = 18017.282 + 30.767371*T = %.2f J/mol\n\n", L_V_ZrZr);

        // Add interactions
        c15.addInteraction(new C15Phase.Interaction(
            new int[]{0, 1},  // V on subL0, Zr on subL1
            new int[]{0, 1},
            11001.758,
            9.5308266
        ));

        c15.addInteraction(new C15Phase.Interaction(
            new int[]{0, 1},  // V on subL0, Zr on subL1
            new int[]{1, 1},
            18017.282,
            30.767371
        ));

        // For 50-50 V-Zr composition:
        // x_V = 0.5, x_Zr = 0.5
        // With a_0=2, a_1=1:
        //   M_V = 2*y_0V + 1*y_1V = 0.5
        //   M_Zr = 2*y_0Zr + 1*y_1Zr = 0.5
        //   y_0V + y_0Zr = 1.0
        //   y_1V + y_1Zr = 1.0
        //
        // Solution for equimolar: y_0V = 1/6, y_0Zr = 5/6, y_1V = 1/3, y_1Zr = 2/3
        // Verification:
        //   M_V = 2*(1/6) + 1*(1/3) = 1/3 + 1/3 = 2/3... NO
        //   Let me recalculate: M_V = 2*y0V + 1*y1V = a0*y0V + a1*y1V
        //   For 50-50: total = a0*(y0V + y0Zr) + a1*(y1V + y1Zr) = 2*1 + 1*1 = 3
        //   M_V = 0.5 * 3 = 1.5
        //   So: 2*y0V + y1V = 1.5
        //   And: y0V + y0Zr = 1.0, y1V + y1Zr = 1.0
        //
        // Try: y0V = 0.5, y0Zr = 0.5, y1V = 0.5, y1Zr = 0.5
        //   M_V = 2*0.5 + 1*0.5 = 1.5 ✓
        //   x_V = 1.5/3 = 0.5 ✓

        double[] y_equimolar = {0.5, 0.5, 0.5, 0.5};  // y_0V, y_0Zr, y_1V, y_1Zr
        c15.setInternalVars(y_equimolar);

        // Verify composition
        double[] x = c15.computeComposition(2, speciesToComp);
        System.out.println("=== Composition Verification ===");
        System.out.printf("Target composition: x_V = 0.5000, x_Zr = 0.5000\n");
        System.out.printf("Internal variables: y = [%.4f, %.4f, %.4f, %.4f]\n",
            y_equimolar[0], y_equimolar[1], y_equimolar[2], y_equimolar[3]);
        System.out.printf("Computed composition: x_V = %.4f, x_Zr = %.4f\n\n", x[0], x[1]);

        // Calculate Gibbs energy
        double G = c15.calG();
        double Gm = c15.calGm();

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    GIBBS ENERGY RESULTS                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.printf("G (Gibbs energy, molar) = %.2f J/mol\n", G);
        System.out.printf("Gm (Gibbs energy of mixing) = %.2f J/mol\n\n", Gm);

        // First derivatives
        System.out.println("=== Thermodynamic Derivatives ===");
        double GT = c15.calDGT();
        System.out.printf("∂G/∂T = %.4f J/(mol·K)  →  S = -∂G/∂T = %.4f J/(mol·K)\n", GT, -GT);

        double[] mu = c15.calDGx();
        System.out.printf("\nChemical Potentials:\n");
        System.out.printf("  μ_V = %.2f J/mol\n", mu[0]);
        System.out.printf("  μ_Zr = %.2f J/mol\n", mu[1]);

        // Activity coefficients
        System.out.printf("\nActivity Coefficients (ln γ):\n");
        double RT = R * T;
        double lnGamma_V = (mu[0] - (-RT * Math.log(0.5))) / RT;
        double lnGamma_Zr = (mu[1] - (-RT * Math.log(0.5))) / RT;
        System.out.printf("  ln γ_V = %.4f  →  γ_V = %.4f\n", lnGamma_V, Math.exp(lnGamma_V));
        System.out.printf("  ln γ_Zr = %.4f  →  γ_Zr = %.4f\n", lnGamma_Zr, Math.exp(lnGamma_Zr));

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    CALCULATION COMPLETE                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
    }
}
