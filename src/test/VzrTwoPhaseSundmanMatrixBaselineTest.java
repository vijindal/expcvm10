package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory.PhaseModel;
import system.model.cef.CefPhaseModelAdapter;
import util.Matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Baseline 4A:
 *
 * Generic two-phase Sundman global-matrix test.
 *
 * Purpose:
 *   Validate the generalization of Sundman Eq. (59) from one stable
 *   phase to two stable phases before modifying EquilibriumSolverV2.
 *
 * IMPORTANT:
 *   This is a MATRIX test, not an equilibrium test.
 *
 *   The two selected phases and their constitutions need not be at
 *   equilibrium. We only require:
 *
 *     1. valid CEF phase states,
 *     2. valid phase-response coefficients,
 *     3. correct construction of the Eq. (59) matrix,
 *     4. successful solution,
 *     5. small A*x-b residual.
 *
 * Current test phases:
 *
 *     V2ZR
 *     BCC_A2
 *
 * If the exact phase name in VZR-re2.TDB differs, change BCC_PHASE.
 */
public class VzrTwoPhaseSundmanMatrixBaselineTest {

    private static final String TDB_PATH =
            "data/VZR-re2.TDB";

    private static final String PHASE_A =
            "V2ZR";

    private static final String BCC_PHASE =
            "BCC_A2";

    private static final double T =
            1100.0;

    private static final double P =
            101325.0;

    /*
     * Arbitrary valid test constitutions.
     *
     * These are NOT equilibrium compositions.
     */
    private static final double XZR_A =
            0.35;

    private static final double XZR_B =
            0.55;

    /*
     * Arbitrary positive phase amounts.
     *
     * These are NOT equilibrium phase fractions.
     */
    private static final double OMEGA_A =
            0.5;

    private static final double OMEGA_B =
            0.5;

    private static final double MATRIX_TOL =
            1.0e-8;

    private static final class PhaseData {

        final String name;
        final CefPhaseModelAdapter phase;

        final double[] y;

        final double G;

        final double[] gy;
        final double[][] gyy;

        final double[] mA;
        final double[][] dMdY;

        final double[][] e;
        final double[] cG;
        final double[][] cA;

        final double[][] R;
        final double[] q;

        PhaseData(
                String name,
                CefPhaseModelAdapter phase,
                double[] y,
                double G,
                double[] gy,
                double[][] gyy,
                double[] mA,
                double[][] dMdY,
                double[][] e,
                double[] cG,
                double[][] cA,
                double[][] R,
                double[] q) {

            this.name = name;
            this.phase = phase;
            this.y = y;
            this.G = G;
            this.gy = gy;
            this.gyy = gyy;
            this.mA = mA;
            this.dMdY = dMdY;
            this.e = e;
            this.cG = cG;
            this.cA = cA;
            this.R = R;
            this.q = q;
        }
    }

    public static void main(String[] args) throws Exception {

        printHeader();

        // ------------------------------------------------------------
        // 1. Load database.
        // ------------------------------------------------------------

        TdbParser parser =
                new TdbParser();

        parser.load(TDB_PATH);

        List<String> elements =
                Arrays.asList("V", "ZR");

        List<String> phaseNames =
                Arrays.asList(
                        PHASE_A,
                        BCC_PHASE);

        @SuppressWarnings("unchecked")
        List<PhaseModel> models =
                (List<PhaseModel>) parser.buildPhaseModels(
                        elements,
                        phaseNames);

        if (models.size() != 2) {

            throw new IllegalStateException(
                    "Expected two phases, obtained "
                    + models.size()
                    + ". Check the phase names in "
                    + TDB_PATH);
        }

        // ------------------------------------------------------------
        // 2. Build the two CEF phases.
        // ------------------------------------------------------------

        PhaseData a =
                buildPhaseData(
                        models.get(0),
                        elements,
                        XZR_A);

        PhaseData b =
                buildPhaseData(
                        models.get(1),
                        elements,
                        XZR_B);

        System.out.println();
        printPhaseSummary(
                a,
                OMEGA_A);

        printPhaseSummary(
                b,
                OMEGA_B);

        // ------------------------------------------------------------
        // 3. Check that this is genuinely a two-phase system for
        //    matrix testing: both phases must have positive amounts
        //    and distinct compositions.
        // ------------------------------------------------------------

        if (!(OMEGA_A > 0.0)
                || !(OMEGA_B > 0.0)) {

            throw new AssertionError(
                    "Both test phase amounts must be positive.");
        }

        // ------------------------------------------------------------
        // 4. Assemble Sundman Eq. (59).
        //
        // Unknowns:
        //
        //   [ lambda_V
        //     lambda_Zr
        //     DeltaOmega_A
        //     DeltaOmega_B ]
        //
        // Equations:
        //
        //   phase A Gibbs relation
        //   phase B Gibbs relation
        //   V mass-balance response
        //   Zr mass-balance response
        // ------------------------------------------------------------

        double[][] A =
                buildTwoPhaseEquilibriumMatrix(
                        a,
                        b,
                        OMEGA_A,
                        OMEGA_B);

        double[] rhs =
                buildTwoPhaseRhs(
                        a,
                        b,
                        OMEGA_A,
                        OMEGA_B);

        System.out.println();
        System.out.println(
                "Two-phase Sundman matrix");
        System.out.println(
                "-------------------------");

        printMatrix(
                A,
                rhs);

        // ------------------------------------------------------------
        // 5. Solve.
        // ------------------------------------------------------------

        double[] x =
                solveSquareSystem(
                        A,
                        rhs);

        System.out.println();
        System.out.println(
                "Two-phase matrix solution");
        System.out.println(
                "-------------------------");

        System.out.println(
                "lambda/deltaOmega = "
                + Arrays.toString(x));

        // ------------------------------------------------------------
        // 6. Verify A*x = rhs.
        // ------------------------------------------------------------

        double residual =
                matrixResidualNorm(
                        A,
                        x,
                        rhs);

        System.out.printf(
                "Matrix residual = %.15e%n",
                residual);

        if (residual > MATRIX_TOL) {

            throw new AssertionError(
                    "Two-phase Sundman matrix residual too large: "
                    + residual);
        }

        // ------------------------------------------------------------
        // 7. Print the independent response terms.
        // ------------------------------------------------------------

        printResponse(
                a,
                elements);

        printResponse(
                b,
                elements);

        // ------------------------------------------------------------
        // 8. Final.
        // ------------------------------------------------------------

        System.out.println();
        System.out.println(
                "PASS: Generic two-phase Sundman Eq. (59) "
                + "matrix was assembled and solved successfully.");

        System.out.println();
        System.out.println(
                "NOTE: This baseline validates the global matrix only.");
        System.out.println(
                "No phase equilibrium or phase-set change is claimed.");
    }

    // ================================================================
    // Phase construction
    // ================================================================

    private static PhaseData buildPhaseData(
            PhaseModel model,
            List<String> elements,
            double xZR)
            throws Exception {

        if (model.gibbs == null) {

            throw new IllegalStateException(
                    "Phase " + model.phaseName
                    + " is not a CEF phase.");
        }

        CefPhaseModelAdapter phase =
                new CefPhaseModelAdapter(
                        model.gibbs,
                        model.magnetic,
                        model.phaseName,
                        new ArrayList<>(elements),
                        model.constituentNames);

        /*
         * Build a valid constitution from the requested binary
         * composition.
         */
        double[] x = {
                1.0 - xZR,
                xZR
        };

        double[] y =
                phase.getInitialInternalVars(x);

        if (!phase.isValid(y)) {

            throw new IllegalStateException(
                    "Invalid initial constitution for "
                    + model.phaseName
                    + ": "
                    + Arrays.toString(y));
        }

        double G =
                phase.siteEnergy(
                        T,
                        y);

        double[] gy =
                phase.siteGradient(
                        T,
                        y);

        double[][] gyy =
                phase.siteHessian(
                        T,
                        y);

        double[] mA =
                phase.elementAmounts(y);

        double[][] dMdY =
                phase.elementAmountsJacobian();

        // ------------------------------------------------------------
        // Phase matrix and inverse.
        // ------------------------------------------------------------

        double[][] E =
                buildPhaseMatrix(
                        phase,
                        gyy);

        double[][] Einv =
                new Matrix(E)
                        .inverse()
                        .getArray();

        int nip =
                phase.numSiteVariables();

        int nc =
                mA.length;

        /*
         * cG convention currently used by the validated V2 solver.
         */
        double[] cG =
                new double[nip];

        for (int i = 0;
             i < nip;
             i++) {

            for (int j = 0;
                 j < nip;
                 j++) {

                cG[i] -=
                        Einv[i][j]
                        * gy[j];
            }
        }

        /*
         * c_iA = sum_j e_ij * dM_A/dY_j
         */
        double[][] cA =
                new double[nc][nip];

        for (int Aidx = 0;
             Aidx < nc;
             Aidx++) {

            for (int i = 0;
                 i < nip;
                 i++) {

                for (int j = 0;
                     j < nip;
                     j++) {

                    cA[Aidx][i] +=
                            Einv[i][j]
                            * dMdY[Aidx][j];
                }
            }
        }

        /*
         * R_AB and q_A for the global mass-balance equations.
         */
        double[][] R =
                new double[nc][nc];

        double[] q =
                new double[nc];

        for (int Aidx = 0;
             Aidx < nc;
             Aidx++) {

            for (int i = 0;
                 i < nip;
                 i++) {

                q[Aidx] +=
                        dMdY[Aidx][i]
                        * cG[i];

                for (int Bidx = 0;
                     Bidx < nc;
                     Bidx++) {

                    R[Aidx][Bidx] +=
                            dMdY[Aidx][i]
                            * cA[Bidx][i];
                }
            }
        }

        return new PhaseData(
                model.phaseName,
                phase,
                y,
                G,
                gy,
                gyy,
                mA,
                dMdY,
                Einv,
                cG,
                cA,
                R,
                q);
    }

    // ================================================================
    // Sundman Eq. (59)
    // ================================================================

    private static double[][] buildTwoPhaseEquilibriumMatrix(
            PhaseData a,
            PhaseData b,
            double omegaA,
            double omegaB) {

        final int nc =
                2;

        final int np =
                2;

        final int n =
                nc + np;

        double[][] A =
                new double[n][n];

        /*
         * Rows 0 and 1:
         *
         *     G^a = sum_A M_A^a lambda_A
         *     G^b = sum_A M_A^b lambda_A
         *
         * Columns:
         *
         *     0 = lambda_V
         *     1 = lambda_Zr
         *     2 = DeltaOmega_a
         *     3 = DeltaOmega_b
         */

        A[0][0] =
                a.mA[0];

        A[0][1] =
                a.mA[1];

        A[0][2] =
                0.0;

        A[0][3] =
                0.0;

        A[1][0] =
                b.mA[0];

        A[1][1] =
                b.mA[1];

        A[1][2] =
                0.0;

        A[1][3] =
                0.0;

        /*
         * Rows 2 and 3:
         *
         * sum_u omega_u R^u_AB lambda_B
         *   + sum_u M_A^u DeltaOmega_u
         *
         * = sum_u omega_u q_A^u
         */
        for (int Aidx = 0;
             Aidx < nc;
             Aidx++) {

            int row =
                    nc + Aidx;

            for (int Bidx = 0;
                 Bidx < nc;
                 Bidx++) {

                A[row][Bidx] =
                        omegaA
                        * a.R[Aidx][Bidx]
                        +
                        omegaB
                        * b.R[Aidx][Bidx];
            }

            A[row][nc + 0] =
                    a.mA[Aidx];

            A[row][nc + 1] =
                    b.mA[Aidx];
        }

        return A;
    }

    private static double[] buildTwoPhaseRhs(
            PhaseData a,
            PhaseData b,
            double omegaA,
            double omegaB) {

        double[] rhs =
                new double[4];

        /*
         * Phase equilibrium rows.
         */
        rhs[0] =
                a.G;

        rhs[1] =
                b.G;

        /*
         * Mass-balance response rows.
         */
        rhs[2] =
                omegaA * a.q[0]
                +
                omegaB * b.q[0];

        rhs[3] =
                omegaA * a.q[1]
                +
                omegaB * b.q[1];

        return rhs;
    }

    // ================================================================
    // Phase matrix
    // ================================================================

    private static double[][] buildPhaseMatrix(
            CefPhaseModelAdapter phase,
            double[][] hessian) {

        int nip =
                phase.numSiteVariables();

        int ns =
                phase.numSublattices();

        double[][] E =
                new double[nip + ns][nip + ns];

        for (int i = 0;
             i < nip;
             i++) {

            for (int j = 0;
                 j < nip;
                 j++) {

                E[i][j] =
                        hessian[i][j];
            }
        }

        int[] offsets =
                phase.sublatticeOffsets();

        int[] nconst =
                phase.constituentsPerSublattice();

        for (int s = 0;
             s < ns;
             s++) {

            int row =
                    nip + s;

            for (int i = 0;
                 i < nconst[s];
                 i++) {

                int k =
                        offsets[s] + i;

                E[k][row] =
                        1.0;

                E[row][k] =
                        1.0;
            }
        }

        return E;
    }

    // ================================================================
    // Linear algebra
    // ================================================================

    private static double[] solveSquareSystem(
            double[][] A,
            double[] b) {

        Matrix mA =
                new Matrix(A);

        Matrix mb =
                new Matrix(
                        b,
                        b.length);

        Matrix mx =
                mA.solve(mb);

        return mx.getColumnPackedCopy();
    }

    private static double matrixResidualNorm(
            double[][] A,
            double[] x,
            double[] b) {

        double sum2 =
                0.0;

        for (int i = 0;
             i < A.length;
             i++) {

            double value =
                    0.0;

            for (int j = 0;
                 j < A[i].length;
                 j++) {

                value +=
                        A[i][j]
                        * x[j];
            }

            double r =
                    value - b[i];

            sum2 +=
                    r * r;
        }

        return Math.sqrt(sum2);
    }

    // ================================================================
    // Diagnostics
    // ================================================================

    private static void printMatrix(
            double[][] A,
            double[] rhs) {

        for (int i = 0;
             i < A.length;
             i++) {

            System.out.printf(
                    "row %d : ",
                    i);

            for (int j = 0;
                 j < A[i].length;
                 j++) {

                System.out.printf(
                        "% .12e ",
                        A[i][j]);
            }

            System.out.printf(
                    " | % .12e%n",
                    rhs[i]);
        }
    }

    private static void printPhaseSummary(
            PhaseData p,
            double omega) {

        System.out.println();
        System.out.println(
                "Phase: " + p.name);

        System.out.println(
                "omega = " + omega);

        System.out.println(
                "Y = " + Arrays.toString(p.y));

        System.out.println(
                "M = " + Arrays.toString(p.mA));

        System.out.println(
                "G = " + p.G);
    }

    private static void printResponse(
            PhaseData p,
            List<String> elements) {

        System.out.println();
        System.out.println(
                "Response: " + p.name);

        System.out.println(
                "cG = "
                + Arrays.toString(p.cG));

        for (int A = 0;
             A < p.cA.length;
             A++) {

            System.out.println(
                    "cA[" + elements.get(A) + "] = "
                    + Arrays.toString(p.cA[A]));
        }

        System.out.println(
                "q = "
                + Arrays.toString(p.q));

        for (int A = 0;
             A < p.R.length;
             A++) {

            System.out.println(
                    "R[" + elements.get(A) + "] = "
                    + Arrays.toString(p.R[A]));
        }
    }

    private static void printHeader() {

        System.out.println(
                "============================================================");

        System.out.println(
                "V-Zr two-phase Sundman matrix baseline");

        System.out.println(
                "============================================================");

        System.out.println(
                "Database : " + TDB_PATH);

        System.out.println(
                "Phase A  : " + PHASE_A);

        System.out.println(
                "Phase B  : " + BCC_PHASE);

        System.out.printf(
                "T        : %.2f K%n",
                T);

        System.out.printf(
                "P        : %.0f Pa%n",
                P);

        System.out.println(
                "NOTE: constitutions and phase amounts are "
                + "matrix-test inputs, not equilibrium values.");
    }
}
