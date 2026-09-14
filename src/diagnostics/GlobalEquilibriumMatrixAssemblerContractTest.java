package diagnostics;

import calc.equil.GlobalEquilibriumMatrixAssembler;
import calc.equil.PhaseMatrixAssembler;
import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import system.model.PhaseModelFactory;
import system.model.cef.CefGibbs;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies {@code calc.equil.GlobalEquilibriumMatrixAssembler} -- the
 * solver flowchart's STEP 3-4 ("Build the global equilibrium matrix" /
 * "Solve the global Newton system", see
 * {@code docs/solver_flowchart_target.png}) -- against a pycalphad-derived
 * two-phase equilibrium, extending the same per-stage contract-test
 * approach {@link PhaseMatrixAssemblerContractTest} established for STEP
 * 1-2.
 *
 * <h2>Reference generation</h2>
 * pycalphad 0.11.1's {@code equilibrium()} gives a converged two-phase
 * V2ZR + BCC_A2 equilibrium for the V-Zr system (elements ordered
 * {@code [V, ZR]}, {@code data/VZR-re2.TDB}) at two independent
 * (T, x_Zr) points:
 *
 * <pre>
 * from pycalphad import Database, equilibrium, variables as v
 * db = Database('data/VZR-re2.TDB')
 * eq = equilibrium(db, ['V','ZR','VA'], ['V2ZR','BCC_A2'],
 *                   {v.T: T, v.P: 101325.0, v.X('ZR'): xZr, v.N: 1})
 * # eq.Y, eq.NP (phase amount, moles of ATOMS basis), eq.MU
 * </pre>
 *
 * Case 1 (T=1100K, x_Zr=0.45): Y_V2ZR=[0.97158104, 0.02841896, 0.00626942,
 * 0.99373058], Y_BCC=[0.07316463, 0.92683537, 1.0], NP=[0.82691197,
 * 0.17308803], MU=[-54920.99767447, -61560.14023244].
 *
 * <p>Case 2 (T=1300K, x_Zr=0.5): Y_V2ZR=[0.96617474, 0.03382526,
 * 0.01646135, 0.98353865], Y_BCC=[0.1148621, 0.8851379, 1.0],
 * NP=[0.72023192, 0.27976808], MU=[-67848.0317267, -78888.93416708].
 *
 * <h2>Converting pycalphad's NP to this project's omega (moles of formula
 * units)</h2>
 * pycalphad's {@code NP} is each phase's fraction of the system BY MOLES
 * OF ATOMS (system total = 1 mole atoms, since N=1 was the condition).
 * This project's Sundman-convention {@code omega_k} (phase amount used in
 * mass balance, {@code sum_k omega_k * M_A^k = N_A(target)}) is moles of
 * FORMULA UNITS: {@code omega_k = NP_k / nfu_real_k}, where
 * {@code nfu_real_k} is the number of real-atom sites in one formula unit
 * of phase k -- NOT simply the sum of all site ratios when a sublattice is
 * entirely vacancies. V2ZR has no vacancy sublattice, so
 * {@code nfu_real = nfu = 3} (site ratios 2+1); BCC_A2's second sublattice
 * is pure {@code VA} (site ratio 3, zero real atoms), so
 * {@code nfu_real = 1} (only the first sublattice's ratio), NOT
 * {@code nfu = 4}. This was independently confirmed two ways: (a)
 * {@code CefGibbs.G()} for BCC_A2 matches pycalphad's raw
 * {@code Model.GM} (which is per mole of ATOMS) with a scale factor of
 * exactly 1, not 4 (see {@code PhaseMatrixAssemblerContractTest}'s BCC_A2
 * cases, all consistent with {@code nfu_real=1}); (b) the Gibbs-Duhem
 * relation {@code G^k == sum_A mu_A * M_A^k} and the mass-balance
 * equation {@code sum_k omega_k * M_A^k == N_A(target)} both hold to
 * ~1e-8 with {@code nfu_real=1} for BCC_A2 and fail by orders of
 * magnitude with {@code nfu=4} -- this was an actual mistake made once
 * while deriving this test's reference values, caught by exactly this
 * check, not asserted from first principles.
 *
 * <h2>What is checked</h2>
 * Per Sundman's equilibrium condition, at pycalphad's own converged state
 * the assembled global system should itself certify convergence: solving
 * it should reproduce pycalphad's chemical potentials
 * ({@code lambda == MU}) and give phase-amount corrections of
 * essentially zero ({@code DeltaOmega == 0}) -- if the current state is
 * already the solution, a genuine multiphase Newton step should propose
 * no further correction. This is a stronger, more end-to-end check than
 * comparing the assembled matrix entries directly (which would require
 * separately re-deriving {@code R_AB}/{@code q_A} by an independent
 * method the way {@code PhaseMatrixAssemblerContractTest} does for
 * {@code eMat}/{@code cG}/{@code cA}); it verifies the full assemble-and-
 * solve pipeline reproduces the pycalphad-certified equilibrium.
 */
public class GlobalEquilibriumMatrixAssemblerContractTest {

    private static int failures = 0;

    private static final double LAMBDA_TOL = 1.0e-1;   // J/mol; pycalphad MU has ~5 sig figs
    private static final double DELTA_OMEGA_TOL = 1.0e-5;
    private static final double MASS_BALANCE_TOL = 1.0e-6;
    private static final double GIBBS_DUHEM_TOL = 1.0;  // J/mol f.u.; matches CefContractTest's ~1e-4 rel floor at these magnitudes

    public static void main(String[] args) throws Exception {

        for (Case c : CASES) {
            checkCase(c);
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL GLOBAL-EQUILIBRIUM-MATRIX-ASSEMBLER CONTRACT CHECKS PASSED");
        } else {
            System.out.println(failures + " GLOBAL-EQUILIBRIUM-MATRIX-ASSEMBLER CONTRACT CHECK(S) FAILED");
            throw new AssertionError(failures + " check(s) failed -- see log above.");
        }
    }

    private static void checkCase(Case c) throws Exception {

        System.out.println("=== " + c.label + " ===");

        TdbParser parser = new TdbParser();
        parser.load(c.tdb);
        List<String> elements = Arrays.asList(c.elements);

        GibbsEnergyModel[] models = new GibbsEnergyModel[c.phaseNames.length];
        for (int i = 0; i < c.phaseNames.length; i++) {
            @SuppressWarnings("unchecked")
            List<CefGibbs> built = (List<CefGibbs>)
                    parser.buildPhaseModels(elements, Arrays.asList(c.phaseNames[i]));
            models[i] = PhaseModelFactory.toGibbsModel(built.get(0), elements);
        }

        int nc = c.elements.length;
        double[] mu0 = new double[nc];

        PhaseEquilData[] phaseData = new PhaseEquilData[models.length];
        for (int i = 0; i < models.length; i++) {
            phaseData[i] = PhaseMatrixAssembler.compute(
                    models[i], c.T, c.P, c.y[i], 0.0, 0.0, mu0);
        }

        // ------------------------------------------------------------
        // Sanity check 0: Gibbs-Duhem, G^k == sum_A mu_A * M_A^k, using
        // pycalphad's own converged mu. This is what caught the
        // nfu_real vs. nfu mistake while deriving this test -- keep it
        // gated so a future reference-data mistake is caught the same
        // way, not just this test's own solve-based checks.
        // ------------------------------------------------------------
        for (int i = 0; i < phaseData.length; i++) {
            double predicted = 0.0;
            for (int A = 0; A < nc; A++) {
                predicted += c.pycalphadMu[A] * phaseData[i].mA[A];
            }
            double diff = Math.abs(predicted - phaseData[i].G);
            require(c.label + ": Gibbs-Duhem check for " + c.phaseNames[i],
                    diff < GIBBS_DUHEM_TOL,
                    "predicted sum(mu*M_A)=" + predicted + " vs G=" + phaseData[i].G
                    + " diff=" + diff);
        }

        // ------------------------------------------------------------
        // Sanity check 1: mass balance holds at pycalphad's own
        // omega/mA (independent of GlobalEquilibriumMatrixAssembler --
        // this only depends on correctly converting NP to omega).
        // ------------------------------------------------------------
        for (int A = 0; A < nc; A++) {
            double represented = 0.0;
            for (int k = 0; k < phaseData.length; k++) {
                represented += c.omega[k] * phaseData[k].mA[A];
            }
            double diff = Math.abs(represented - c.targetAmounts[A]);
            require(c.label + ": mass balance for component " + A,
                    diff < MASS_BALANCE_TOL,
                    "represented=" + represented + " target=" + c.targetAmounts[A]
                    + " diff=" + diff);
        }

        // ------------------------------------------------------------
        // The actual STEP 3-4 check: assemble and solve, and verify
        // the result certifies convergence at pycalphad's own
        // equilibrium (lambda == MU, DeltaOmega == 0).
        // ------------------------------------------------------------
        GlobalEquilibriumMatrixAssembler.Result result =
                GlobalEquilibriumMatrixAssembler.assembleAndSolve(
                        phaseData, c.omega, c.targetAmounts);

        System.out.println("  lambda = " + Arrays.toString(result.lambda));
        System.out.println("  DeltaOmega = " + Arrays.toString(result.deltaOmega));
        System.out.println("  pycalphad MU = " + Arrays.toString(c.pycalphadMu));

        for (int A = 0; A < nc; A++) {
            double diff = Math.abs(result.lambda[A] - c.pycalphadMu[A]);
            require(c.label + ": lambda[" + A + "] matches pycalphad MU",
                    diff < LAMBDA_TOL,
                    "lambda=" + result.lambda[A] + " MU=" + c.pycalphadMu[A]
                    + " diff=" + diff);
        }

        for (int k = 0; k < result.deltaOmega.length; k++) {
            require(c.label + ": DeltaOmega[" + k + "] is ~0 (already converged)",
                    Math.abs(result.deltaOmega[k]) < DELTA_OMEGA_TOL,
                    "DeltaOmega=" + result.deltaOmega[k]);
        }
    }

    private static void require(String label, boolean condition, String detail) {
        if (condition) {
            System.out.println("  PASS: " + label);
        } else {
            System.out.println("  FAIL: " + label + " -- " + detail);
            failures++;
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Reference cases
    // ────────────────────────────────────────────────────────────────

    private static final class Case {
        final String label;
        final String tdb;
        final String[] elements;
        final String[] phaseNames;
        final double T, P;
        final double[][] y;
        final double[] omega;
        final double[] targetAmounts;
        final double[] pycalphadMu;

        Case(String label, String tdb, String[] elements, String[] phaseNames,
             double T, double P, double[][] y,
             double[] npByMolesOfAtoms, double[] nfuReal,
             double[] targetAmounts, double[] pycalphadMu) {
            this.label = label;
            this.tdb = tdb;
            this.elements = elements;
            this.phaseNames = phaseNames;
            this.T = T; this.P = P;
            this.y = y;
            this.omega = new double[npByMolesOfAtoms.length];
            for (int i = 0; i < omega.length; i++) {
                this.omega[i] = npByMolesOfAtoms[i] / nfuReal[i];
            }
            this.targetAmounts = targetAmounts;
            this.pycalphadMu = pycalphadMu;
        }
    }

    private static final Case[] CASES = {

        new Case("V2ZR+BCC_A2 T=1100K x_Zr=0.45",
            "data/VZR-re2.TDB", new String[]{"V", "ZR"},
            new String[]{"V2ZR", "BCC_A2"},
            1100.0, 101325.0,
            new double[][]{
                {0.97158104, 0.02841896, 0.00626942, 0.99373058},
                {0.07316463, 0.92683537, 1.0},
            },
            new double[]{0.82691197, 0.17308803},   // pycalphad NP (moles of atoms basis)
            new double[]{3.0, 1.0},                  // nfu_real: V2ZR=2+1, BCC_A2's VA sublattice excluded
            new double[]{0.55, 0.45},                // targetAmounts = xV, xZr (N=1)
            new double[]{-54920.99767447, -61560.14023244}),

        new Case("V2ZR+BCC_A2 T=1300K x_Zr=0.50",
            "data/VZR-re2.TDB", new String[]{"V", "ZR"},
            new String[]{"V2ZR", "BCC_A2"},
            1300.0, 101325.0,
            new double[][]{
                {0.96617474, 0.03382526, 0.01646135, 0.98353865},
                {0.1148621, 0.8851379, 1.0},
            },
            new double[]{0.72023192, 0.27976808},
            new double[]{3.0, 1.0},
            new double[]{0.5, 0.5},
            new double[]{-67848.0317267, -78888.93416708}),
    };
}
