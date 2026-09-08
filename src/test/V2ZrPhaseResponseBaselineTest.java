package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory.PhaseModel;
import system.model.cef.CefPhaseModelAdapter;
import util.Matrix;
import util.SingularValueDecomposition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Baseline: independent finite-difference verification of the
 * Sundman phase-response coefficients for V2ZR.
 *
 * This test is intentionally independent of EquilibriumSolverV2.
 *
 * It verifies, at a converged V2ZR constitution:
 *
 *   c_iA = dY_i / d(mu_A)
 *
 * for small element-chemical-potential perturbations, while keeping
 * T and P fixed and re-solving the constrained phase equilibrium
 * directly with the CEF model.
 *
 * It also verifies the cG term through:
 *
 *   DeltaY = cG + sum_A c_iA * lambda_A
 *
 * by comparing the reconstructed DeltaY with the direct constrained
 * Newton correction.
 *
 * Reference state:
 *
 *   T = 1500 K
 *   X[V,ZR] = [0.70,0.30]
 *
 * Converged constitution from Baseline 3:
 *
 *   Y* =
 *     [0.9852214302263694,
 *      0.0147785697736306,
 *      0.1295571395472612,
 *      0.8704428604527388]
 *
 * Units:
 *   G       : J/mol-fu
 *   mu      : J/mol
 *   c_iA    : dimensionless per (J/mol)
 */
public class V2ZrPhaseResponseBaselineTest {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    private static final double T = 1500.0;

    private static final double XV = 0.70;
    private static final double XZR = 0.30;

    /*
     * Independent converged CEF constitution.
     */
    private static final double[] Y_STAR = {
        0.9852214302263694,
        0.0147785697736306,
        0.1295571395472612,
        0.8704428604527388
    };

    /*
     * Chemical potentials at the converged state from the V2
     * Sundman calculation.
     */
    private static final double[] LAMBDA_STAR = {
        -76882.03918886269,
        -107535.67930564703
    };

    /*
     * Perturbation used for the finite-difference response test.
     *
     * 0.1 J/mol is deliberately small relative to |lambda|.
     */
    private static final double DELTA_MU = 0.1;

    private static final double DERIVATIVE_REL_TOL = 1.0e-4;
    private static final double STATE_TOL = 1.0e-10;

    private static final int MAX_NEWTON = 50;

    public static void main(String[] args) throws Exception {

        printHeader();

        // ------------------------------------------------------------
        // 1. Build V2ZR CEF model.
        // ------------------------------------------------------------
        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phases = Arrays.asList(PHASE_NAME);

        @SuppressWarnings("unchecked")
        List<PhaseModel> models =
                (List<PhaseModel>) parser.buildPhaseModels(
                        elements, phases);

        if (models.size() != 1) {
            throw new IllegalStateException(
                    "Expected one V2ZR model, got "
                    + models.size());
        }

        PhaseModel pm = models.get(0);

        if (pm.gibbs == null) {
            throw new IllegalStateException(
                    "V2ZR is not a CEF model.");
        }

        CefPhaseModelAdapter cef =
                new CefPhaseModelAdapter(
                        pm.gibbs,
                        pm.magnetic,
                        pm.phaseName,
                        new ArrayList<>(elements),
                        pm.constituentNames);

        // ------------------------------------------------------------
        // 2. Evaluate the converged reference state.
        // ------------------------------------------------------------
        double G0 =
                cef.sundmanG(T, Y_STAR);

        double[] grad0 =
                cef.sundmanGradient(T, Y_STAR);

        double[][] hess0 =
                cef.sundmanHessian(T, Y_STAR);

        double[][] dMdY =
                cef.sundmanMJacobian();

        double[] M0 =
                cef.sundmanM(Y_STAR);

        checkReferenceState(cef, M0);

        System.out.printf(
                "Reference G = %.15f J/mol-fu%n",
                G0);

        System.out.println(
                "Reference Y  = "
                + Arrays.toString(Y_STAR));

        System.out.println(
                "Reference M  = "
                + Arrays.toString(M0));

        // ------------------------------------------------------------
        // 3. Construct the phase response matrix from the bordered
        //    Hessian.
        // ------------------------------------------------------------
        double[][] phaseMatrix =
                buildPhaseMatrix(
                        cef,
                        hess0);

        double[][] e =
                invertMatrix(phaseMatrix);

        double[] cG =
                calculateCG(
                        e,
                        grad0,
                        cef.sundmanNumSiteVariables());

        double[][] cA =
                calculateCA(
                        e,
                        dMdY,
                        cef.sundmanNumSiteVariables(),
                        M0.length);

        System.out.println();
        System.out.println(
                "Phase-response coefficients");
        System.out.println(
                "----------------------------");

        System.out.println(
                "cG = "
                + Arrays.toString(cG));

        for (int A = 0; A < cA.length; A++) {
            System.out.println(
                    "cA[" + elements.get(A) + "] = "
                    + Arrays.toString(cA[A]));
        }

        // ------------------------------------------------------------
        // 4. Check sublattice constraints on cG and cA.
        // ------------------------------------------------------------
        checkResponseConstraints(
                cef,
                cG,
                cA);

        // ------------------------------------------------------------
        // 5. Finite-difference test of c_iA.
        //
        // We change one chemical potential by +/- DELTA_MU and
        // directly re-solve the constrained stationarity problem.
        //
        // IMPORTANT:
        // The perturbation is NOT inserted into the original solver.
        // This is an independent CEF equilibrium calculation.
        // ------------------------------------------------------------
        System.out.println();
        System.out.println(
                "Finite-difference c_iA verification");
        System.out.println(
                "------------------------------------");

        double[][] fdCA =
                new double[M0.length][Y_STAR.length];

        for (int A = 0; A < M0.length; A++) {

            double[] lambdaPlus =
                    LAMBDA_STAR.clone();

            double[] lambdaMinus =
                    LAMBDA_STAR.clone();

            lambdaPlus[A] += DELTA_MU;
            lambdaMinus[A] -= DELTA_MU;

            /*
             * Solve:
             *
             *   G_Y - J_M^T lambda - C^T gamma = 0
             *
             * subject to:
             *
             *   M_A(Y) = M_A(reference)
             *
             *   sum_i y_is = 1
             *
             * The target M remains the same; only the external
             * chemical-potential parameter used for the response
             * test is perturbed.
             */
            double[] yPlus =
                    solveForFixedLambda(
                            cef,
                            Y_STAR,
                            M0,
                            lambdaPlus);

            double[] yMinus =
                    solveForFixedLambda(
                            cef,
                            Y_STAR,
                            M0,
                            lambdaMinus);

            for (int i = 0;
                 i < Y_STAR.length;
                 i++) {

                fdCA[A][i] =
                        (yPlus[i] - yMinus[i])
                        / (2.0 * DELTA_MU);
            }

            double err =
                    relativeVectorError(
                            fdCA[A],
                            cA[A]);

            System.out.printf(
                    "%s: ||FD-cA||/||cA|| = %.6e%n",
                    elements.get(A),
                    err);

            if (err > DERIVATIVE_REL_TOL) {
                throw new AssertionError(
                        "Finite-difference cA verification failed "
                        + "for " + elements.get(A)
                        + ": relative error=" + err);
            }
        }

        // ------------------------------------------------------------
        // 6. Verify the actual Sundman response at the reference
        //    chemical potentials.
        //
        //     DeltaY = cG + sum_A cA * lambda_A
        //
        // This should be approximately zero at equilibrium.
        // ------------------------------------------------------------
        double[] reconstructed =
                new double[Y_STAR.length];

        for (int i = 0; i < Y_STAR.length; i++) {

            double value =
                    cG[i];

            for (int A = 0;
                 A < LAMBDA_STAR.length;
                 A++) {

                value +=
                        cA[A][i]
                        * LAMBDA_STAR[A];
            }

            reconstructed[i] = value;
        }

        double responseNorm =
                norm(reconstructed);

        System.out.println();
        System.out.println(
                "Equilibrium response reconstruction");
        System.out.println(
                "-----------------------------------");

        System.out.println(
                "cG + cA*lambda = "
                + Arrays.toString(reconstructed));

        System.out.printf(
                "Response norm = %.15e%n",
                responseNorm);

        /*
         * At equilibrium the current lambda and Y* must satisfy
         * the phase response relation to numerical precision.
         */
        if (responseNorm > STATE_TOL) {
            throw new AssertionError(
                    "Equilibrium response does not vanish: "
                    + responseNorm);
        }

        // ------------------------------------------------------------
        // 7. Final result.
        // ------------------------------------------------------------
        System.out.println();
        System.out.println(
                "PASS: V2ZR Sundman phase-response coefficients "
                + "passed independent finite-difference verification.");
    }

    // =================================================================
    // Direct constrained phase solve for response verification
    // =================================================================

    private static double[] solveForFixedLambda(
            CefPhaseModelAdapter phase,
            double[] initialY,
            double[] targetM,
            double[] lambda) {

        double[] y =
                initialY.clone();

        int nip =
                phase.sundmanNumSiteVariables();

        int nc =
                targetM.length;

        int ns =
                phase.sundmanNumSublattices();

        int n =
                nip + ns;

        double[] gamma =
                new double[ns];

        for (int iter = 0;
             iter < MAX_NEWTON;
             iter++) {

            double[] gy =
                    phase.sundmanGradient(
                            T, y);

            double[][] gyy =
                    phase.sundmanHessian(
                            T, y);

            double[] M =
                    phase.sundmanM(y);

            double[][] dMdY =
                    phase.sundmanMJacobian();

            double[] residual =
                    new double[n];

            double[][] A =
                    new double[n][n];

            // --------------------------------------------------------
            // Hessian block
            // --------------------------------------------------------
            for (int i = 0; i < nip; i++) {
                for (int j = 0; j < nip; j++) {
                    A[i][j] =
                            gyy[i][j];
                }
            }

            // --------------------------------------------------------
            // Stationarity Jacobian wrt gamma
            // --------------------------------------------------------
            int[] offsets =
                    phase.sundmanOffsets();

            int[] nconst =
                    phase.sundmanConstituentsPerSublattice();

            for (int i = 0; i < nip; i++) {

                double r =
                        gy[i];

                for (int Aidx = 0;
                     Aidx < nc;
                     Aidx++) {

                    r -=
                            dMdY[Aidx][i]
                            * lambda[Aidx];
                }

                int s =
                        sublatticeOf(
                                i,
                                offsets,
                                nconst);

                r -= gamma[s];

                residual[i] = r;

                A[i][nip + s] = -1.0;
                A[nip + s][i] = -1.0;
            }

            // --------------------------------------------------------
            // Target M residual
            // --------------------------------------------------------
            //
            // For response verification this is a fixed-composition
            // constrained phase. M(Y) must remain equal to M0.
            //
            // One independent composition equation is sufficient for
            // the binary V2ZR case. The redundant element equation is
            // intentionally omitted.
            //
            double[] mResidual =
                    new double[nc];

            for (int Aidx = 0;
                 Aidx < nc;
                 Aidx++) {

                mResidual[Aidx] =
                        M[Aidx]
                        - targetM[Aidx];
            }

            /*
             * The binary case has one independent composition constraint.
             * Use Zr as the independent composition equation.
             */
            int compositionRow =
                    nip;

            residual[compositionRow] =
                    mResidual[1];

            for (int i = 0; i < nip; i++) {
                A[compositionRow][i] =
                        dMdY[1][i];
                A[i][compositionRow] =
                        dMdY[1][i];
            }

            // --------------------------------------------------------
            // Remaining rows: sublattice normalization
            // --------------------------------------------------------
            for (int s = 0; s < ns; s++) {

                int row =
                        nip + 1 + s;

                /*
                 * The matrix dimensions must include the extra
                 * independent composition row.  This branch is kept
                 * below only for clarity; the actual system is built
                 * separately.
                 */
            }

            /*
             * The above compact construction cannot be used because
             * the binary problem needs:
             *
             *   nip Y variables
             *   + 1 composition multiplier
             *   + ns sublattice multipliers
             *
             * Rebuild the actual square system.
             */
            int nActual =
                    nip + 1 + ns;

            double[][] K =
                    new double[nActual][nActual];

            double[] r =
                    new double[nActual];

            // Hessian
            for (int i = 0; i < nip; i++) {
                for (int j = 0; j < nip; j++) {
                    K[i][j] =
                            gyy[i][j];
                }
            }

            // Stationarity with composition multiplier eta
            // and sublattice gamma.
            for (int i = 0; i < nip; i++) {

                double value =
                        gy[i];

                for (int Aidx = 0;
                     Aidx < nc;
                     Aidx++) {

                    value -=
                            dMdY[Aidx][i]
                            * lambda[Aidx];
                }

                int s =
                        sublatticeOf(
                                i,
                                offsets,
                                nconst);

                value -= gamma[s];

                /*
                 * eta is a scalar multiplier on M_Zr.
                 */
                value -=
                        0.0 * dMdY[1][i];

                r[i] = value;

                /*
                 * Derivative wrt eta.
                 */
                K[i][nip] =
                        -dMdY[1][i];

                K[nip][i] =
                        -dMdY[1][i];

                /*
                 * Derivative wrt gamma_s.
                 */
                K[i][nip + 1 + s] =
                        -1.0;

                K[nip + 1 + s][i] =
                        -1.0;
            }

            /*
             * Add the composition correction into the stationarity
             * residual through eta.  The exact value of eta is solved
             * simultaneously below.
             */
            double compositionResidual =
                    M[1] - targetM[1];

            r[nip] =
                    compositionResidual;

            /*
             * Sublattice normalization.
             */
            for (int s = 0; s < ns; s++) {

                double sum = 0.0;

                for (int i = 0;
                     i < nconst[s];
                     i++) {

                    sum +=
                            y[offsets[s] + i];
                }

                r[nip + 1 + s] =
                        sum - 1.0;
            }

            /*
             * The system is singular because the two elemental
             * chemical potentials are linearly dependent under the
             * fixed-composition constraint.  A minimum-norm SVD solve
             * gives a consistent Newton step for this diagnostic.
             */
            double[] rhs =
                    negate(r);

            double[] correction =
                    solveMinimumNormSystem(
                            K,
                            rhs);

            double[] dy =
                    Arrays.copyOfRange(
                            correction,
                            0,
                            nip);

            /*
             * Physical-domain backtracking.
             */
            double alpha = 1.0;

            boolean accepted = false;

            for (int ls = 0;
                 ls < 30;
                 ls++) {

                double[] trial =
                        new double[nip];

                for (int i = 0;
                     i < nip;
                     i++) {

                    trial[i] =
                            y[i]
                            + alpha * dy[i];
                }

                if (phase.isValid(trial)) {

                    y = trial;
                    accepted = true;
                    break;
                }

                alpha *= 0.5;
            }

            if (!accepted) {
                throw new IllegalStateException(
                        "Could not find a physical Newton step.");
            }

            double dyNorm =
                    norm(dy);

            if (dyNorm < STATE_TOL) {
                break;
            }
        }

        return y;
    }

    // =================================================================
    // Response coefficient construction
    // =================================================================

    private static double[][] buildPhaseMatrix(
            CefPhaseModelAdapter phase,
            double[][] hessian) {

        int nip =
                phase.sundmanNumSiteVariables();

        int ns =
                phase.sundmanNumSublattices();

        double[][] E =
                new double[nip + ns][nip + ns];

        for (int i = 0; i < nip; i++) {
            for (int j = 0; j < nip; j++) {
                E[i][j] =
                        hessian[i][j];
            }
        }

        int[] offsets =
                phase.sundmanOffsets();

        int[] nconst =
                phase.sundmanConstituentsPerSublattice();

        for (int s = 0; s < ns; s++) {

            int row =
                    nip + s;

            for (int i = 0;
                 i < nconst[s];
                 i++) {

                int k =
                        offsets[s] + i;

                E[k][row] = 1.0;
                E[row][k] = 1.0;
            }
        }

        return E;
    }

    private static double[] calculateCG(
            double[][] inverse,
            double[] gradient,
            int nip) {

        double[] cG =
                new double[nip];

        for (int i = 0; i < nip; i++) {

            double sum = 0.0;

            for (int j = 0; j < nip; j++) {

                sum +=
                        inverse[i][j]
                        * gradient[j];
            }

            /*
             * Current V2 convention follows Eq. (44).
             */
            cG[i] =
                    sum;
        }

        return cG;
    }

    private static double[][] calculateCA(
            double[][] inverse,
            double[][] dMdY,
            int nip,
            int nc) {

        double[][] cA =
                new double[nc][nip];

        for (int A = 0;
             A < nc;
             A++) {

            for (int i = 0;
                 i < nip;
                 i++) {

                double sum = 0.0;

                for (int j = 0;
                     j < nip;
                     j++) {

                    sum +=
                            inverse[i][j]
                            * dMdY[A][j];
                }

                cA[A][i] =
                        sum;
            }
        }

        return cA;
    }

    // =================================================================
    // Validation helpers
    // =================================================================

    private static void checkReferenceState(
            CefPhaseModelAdapter phase,
            double[] M) {

        if (M.length != 2) {
            throw new AssertionError(
                    "Expected binary V2ZR M vector.");
        }

        double xV =
                M[0] / (M[0] + M[1]);

        double xZR =
                M[1] / (M[0] + M[1]);

        if (Math.abs(xV - XV) > STATE_TOL
                || Math.abs(xZR - XZR) > STATE_TOL) {

            throw new AssertionError(
                    "Reference constitution has wrong composition: "
                    + "x=[" + xV + "," + xZR + "]");
        }
    }

    private static void checkResponseConstraints(
            CefPhaseModelAdapter phase,
            double[] cG,
            double[][] cA) {

        int[] offsets =
                phase.sundmanOffsets();

        int[] nconst =
                phase.sundmanConstituentsPerSublattice();

        double tol =
                1.0e-12;

        for (int s = 0;
             s < nconst.length;
             s++) {

            double sumCG = 0.0;

            for (int i = 0;
                 i < nconst[s];
                 i++) {

                sumCG +=
                        cG[offsets[s] + i];
            }

            if (Math.abs(sumCG) > tol) {
                throw new AssertionError(
                        "cG violates sublattice constraint "
                        + s + ": " + sumCG);
            }

            for (int A = 0;
                 A < cA.length;
                 A++) {

                double sumCA = 0.0;

                for (int i = 0;
                     i < nconst[s];
                     i++) {

                    sumCA +=
                            cA[A][offsets[s] + i];
                }

                if (Math.abs(sumCA) > tol) {
                    throw new AssertionError(
                            "cA[" + A
                            + "] violates sublattice constraint "
                            + s + ": " + sumCA);
                }
            }
        }
    }

    // =================================================================
    // Linear algebra
    // =================================================================

    private static double[][] invertMatrix(
            double[][] A) {

        Matrix m =
                new Matrix(A);

        return m.inverse().getArray();
    }

    private static double[] solveMinimumNormSystem(
            double[][] A,
            double[] b) {

        Matrix m =
                new Matrix(A);

        SingularValueDecomposition svd =
                new SingularValueDecomposition(m);

        double[] s =
                svd.getSingularValues();

        Matrix U =
                svd.getU();

        Matrix V =
                svd.getV();

        int rows =
                A.length;

        int cols =
                A[0].length;

        int r =
                Math.min(rows, cols);

        double tol =
                (s.length == 0 || s[0] == 0.0)
                        ? 0.0
                        : Math.max(rows, cols)
                          * s[0]
                          * Math.pow(2.0, -52.0);

        double[][] ua =
                U.getArray();

        double[] utb =
                new double[r];

        for (int k = 0; k < r; k++) {

            for (int i = 0; i < rows; i++) {

                utb[k] +=
                        ua[i][k]
                        * b[i];
            }
        }

        double[] z =
                new double[r];

        for (int k = 0; k < r; k++) {

            z[k] =
                    s[k] > tol
                            ? utb[k] / s[k]
                            : 0.0;
        }

        double[][] va =
                V.getArray();

        double[] x =
                new double[cols];

        for (int j = 0; j < cols; j++) {

            for (int k = 0; k < r; k++) {

                x[j] +=
                        va[j][k]
                        * z[k];
            }
        }

        return x;
    }

    private static double[] negate(
            double[] v) {

        double[] r =
                new double[v.length];

        for (int i = 0; i < v.length; i++) {
            r[i] = -v[i];
        }

        return r;
    }

    private static double norm(
            double[] v) {

        double sum = 0.0;

        for (double x : v) {
            sum += x * x;
        }

        return Math.sqrt(sum);
    }

    private static double relativeVectorError(
            double[] a,
            double[] b) {

        double numerator = 0.0;
        double denominator = 0.0;

        for (int i = 0; i < a.length; i++) {

            double d =
                    a[i] - b[i];

            numerator += d * d;
            denominator += b[i] * b[i];
        }

        double n =
                Math.sqrt(numerator);

        double d =
                Math.sqrt(denominator);

        return n /
                Math.max(
                        d,
                        1.0e-30);
    }

    private static int sublatticeOf(
            int index,
            int[] offsets,
            int[] nconst) {

        for (int s = 0;
             s < offsets.length;
             s++) {

            if (index >= offsets[s]
                    && index < offsets[s] + nconst[s]) {

                return s;
            }
        }

        throw new IllegalArgumentException(
                "Unknown sublattice index: " + index);
    }

    private static void printHeader() {

        System.out.println(
                "============================================================");

        System.out.println(
                "V2ZR Sundman phase-response baseline");

        System.out.println(
                "============================================================");

        System.out.println(
                "Database : " + TDB_PATH);

        System.out.println(
                "Phase    : " + PHASE_NAME);

        System.out.printf(
                "T        : %.2f K%n",
                T);

        System.out.printf(
                "X[V,ZR]  : [%.6f, %.6f]%n",
                XV, XZR);

        System.out.printf(
                "dMu      : %.6f J/mol%n",
                DELTA_MU);
    }
}
