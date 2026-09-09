package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory.PhaseModel;
import system.model.cef.CefPhaseModelAdapter;
import util.Matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Corrected Sundman phase-response verification for V2ZR.
 *
 * This test verifies c_iA independently of the global equilibrium solver.
 *
 * At fixed T and fixed element chemical potentials lambda_A, the phase
 * stationarity equations are
 *
 *     G_Y - J_M^T lambda - C^T gamma = 0
 *
 * together with only the sublattice normalization constraints
 *
 *     C(Y) = 0.
 *
 * IMPORTANT:
 * The element amounts M_A are NOT fixed in this response test. That was
 * the error in the previous finite-difference test.
 *
 * Therefore a perturbation
 *
 *     lambda_A -> lambda_A +/- DeltaMu
 *
 * is allowed to change the equilibrium constitution Y.
 *
 * The finite-difference response is
 *
 *     c_iA(FD) =
 *       [Y_i(lambda_A + DeltaMu) -
 *        Y_i(lambda_A - DeltaMu)] / (2 DeltaMu)
 *
 * which is compared with the analytic Sundman coefficient
 *
 *     c_iA = sum_j e_ij * dM_A/dY_j.
 *
 * The test also verifies, at the converged equilibrium state,
 *
 *     cG + sum_A cA * lambda_A = 0.
 */
public class V2ZrPhaseResponseBaselineTestCorrected {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    private static final double T = 1500.0;

    /*
     * Independently validated equilibrium constitution from Baseline 3.
     */
    private static final double[] Y_STAR = {
        0.9852214302263694,
        0.0147785697736306,
        0.1295571395472612,
        0.8704428604527388
    };

    /*
     * Accepted chemical potentials from the converged V2 Sundman solve.
     */
    private static final double[] LAMBDA_STAR = {
        -76882.03918886269,
        -107535.67930564703
    };

    /*
     * Perturbation in lambda.
     *
     * This is small enough for a central finite difference but large
     * enough to avoid round-off cancellation in the Y response.
     */
    private static final double DELTA_MU = 0.1;

    private static final int MAX_NEWTON = 100;

    private static final double NEWTON_TOL = 1.0e-12;

    private static final double RESPONSE_REL_TOL = 1.0e-5;

    private static final double EQUIL_RESPONSE_TOL = 1.0e-10;

    public static void main(String[] args) throws Exception {

        printHeader();

        // ------------------------------------------------------------
        // 1. Build the V2ZR CEF phase.
        // ------------------------------------------------------------
        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);

        List<String> elements =
                Arrays.asList("V", "ZR");

        List<String> phases =
                Arrays.asList(PHASE_NAME);

        @SuppressWarnings("unchecked")
        List<PhaseModel> models =
                (List<PhaseModel>) parser.buildPhaseModels(
                        elements,
                        phases);

        if (models.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one V2ZR phase, got "
                            + models.size());
        }

        PhaseModel pm = models.get(0);

        if (pm.gibbs == null) {
            throw new IllegalStateException(
                    "V2ZR is not backed by CefGibbs.");
        }

        CefPhaseModelAdapter phase =
                new CefPhaseModelAdapter(
                        pm.gibbs,
                        pm.magnetic,
                        pm.phaseName,
                        new ArrayList<>(elements),
                        pm.constituentNames);

        // ------------------------------------------------------------
        // 2. Evaluate reference state.
        // ------------------------------------------------------------
        double G =
                phase.siteEnergy(T, Y_STAR);

        double[] gy =
                phase.siteGradient(T, Y_STAR);

        double[][] gyy =
                phase.siteHessian(T, Y_STAR);

        double[][] dMdY =
                phase.elementAmountsJacobian();

        double[] M =
                phase.elementAmounts(Y_STAR);

        checkReferenceComposition(M);

        // ------------------------------------------------------------
        // 3. Build analytic Sundman response coefficients.
        // ------------------------------------------------------------
        double[][] E =
                buildPhaseMatrix(
                        phase,
                        gyy);

        double[][] Einv =
                invert(E);

        double[] cG =
                calculateCG(
                        Einv,
                        gy);

        double[][] cA =
                calculateCA(
                        Einv,
                        dMdY);

        System.out.println();
        System.out.println("Reference state");
        System.out.println("----------------");
        System.out.println(
                "Y_STAR = "
                + Arrays.toString(Y_STAR));
        System.out.println(
                "lambda = "
                + Arrays.toString(LAMBDA_STAR));
        System.out.printf(
                "G = %.15f J/mol-fu%n",
                G);

        System.out.println();
        System.out.println("Analytic response coefficients");
        System.out.println("--------------------------------");

        System.out.println(
                "cG = "
                + Arrays.toString(cG));

        for (int A = 0; A < cA.length; A++) {
            System.out.println(
                    "cA[" + elements.get(A) + "] = "
                    + Arrays.toString(cA[A]));
        }

        // ------------------------------------------------------------
        // 4. Check sublattice constraints.
        // ------------------------------------------------------------
        checkResponseConstraints(
                phase,
                cG,
                cA);

        // ------------------------------------------------------------
        // 5. Check equilibrium response:
        //
        //     cG + sum_A cA*lambda_A = 0
        // ------------------------------------------------------------
        double[] equilibriumResponse =
                new double[Y_STAR.length];

        for (int i = 0;
             i < Y_STAR.length;
             i++) {

            double value =
                    cG[i];

            for (int A = 0;
                 A < LAMBDA_STAR.length;
                 A++) {

                value +=
                        cA[A][i]
                        * LAMBDA_STAR[A];
            }

            equilibriumResponse[i] =
                    value;
        }

        double equilibriumResponseNorm =
                norm(equilibriumResponse);

        System.out.println();
        System.out.println(
                "Equilibrium response check");
        System.out.println(
                "--------------------------");

        System.out.println(
                "cG + cA*lambda = "
                + Arrays.toString(
                        equilibriumResponse));

        System.out.printf(
                "response norm = %.15e%n",
                equilibriumResponseNorm);

        if (equilibriumResponseNorm >
                EQUIL_RESPONSE_TOL) {

            throw new AssertionError(
                    "Equilibrium response is not zero: "
                            + equilibriumResponseNorm);
        }

        // ------------------------------------------------------------
        // 6. Finite-difference verification of c_iA.
        //
        // IMPORTANT:
        // Only sublattice normalization is imposed.
        // M_A is allowed to change.
        // ------------------------------------------------------------
        System.out.println();
        System.out.println(
                "Finite-difference response verification");
        System.out.println(
                "-----------------------------------------");

        double[][] fdCA =
                new double[LAMBDA_STAR.length]
                        [Y_STAR.length];

        for (int A = 0;
             A < LAMBDA_STAR.length;
             A++) {

            double[] lambdaPlus =
                    LAMBDA_STAR.clone();

            double[] lambdaMinus =
                    LAMBDA_STAR.clone();

            lambdaPlus[A] += DELTA_MU;
            lambdaMinus[A] -= DELTA_MU;

            double[] yPlus =
                    solvePhaseStationarity(
                            phase,
                            Y_STAR,
                            lambdaPlus);

            double[] yMinus =
                    solvePhaseStationarity(
                            phase,
                            Y_STAR,
                            lambdaMinus);

            for (int i = 0;
                 i < Y_STAR.length;
                 i++) {

                fdCA[A][i] =
                        (yPlus[i] - yMinus[i])
                        / (2.0 * DELTA_MU);
            }

            double relativeError =
                    relativeVectorError(
                            fdCA[A],
                            cA[A]);

            System.out.println();
            System.out.println(
                    elements.get(A) + " response:");

            System.out.println(
                    "Y+ = "
                    + Arrays.toString(yPlus));

            System.out.println(
                    "Y- = "
                    + Arrays.toString(yMinus));

            System.out.println(
                    "FD cA = "
                    + Arrays.toString(fdCA[A]));

            System.out.println(
                    "analytic cA = "
                    + Arrays.toString(cA[A]));

            System.out.printf(
                    "relative error = %.6e%n",
                    relativeError);

            if (relativeError >
                    RESPONSE_REL_TOL) {

                throw new AssertionError(
                        "Finite-difference response verification "
                        + "failed for "
                        + elements.get(A)
                        + ": relative error="
                        + relativeError);
            }

            /*
             * The amount vector should change when lambda_A is
             * perturbed. This is a useful guard against accidentally
             * reintroducing the old fixed-M constraint.
             */
            double[] Mplus =
                    phase.elementAmounts(yPlus);

            double[] Mminus =
                    phase.elementAmounts(yMinus);

            double dM =
                    normDifference(
                            Mplus,
                            Mminus);

            System.out.printf(
                    "||M+ - M-|| = %.6e%n",
                    dM);

            if (dM < 1.0e-12) {

                throw new AssertionError(
                        "Response test did not allow M_A to change "
                        + "for perturbed lambda["
                        + elements.get(A)
                        + "].");
            }
        }

        System.out.println();
        System.out.println(
                "PASS: Analytic c_iA coefficients agree with "
                + "independent finite-difference phase response.");
    }

    // =================================================================
    // Direct phase stationarity solve
    // =================================================================

    /**
     * Solve
     *
     *     G_Y - J_M^T lambda - C^T gamma = 0
     *
     * subject ONLY to
     *
     *     sum_i y_is = 1.
     *
     * Element amounts M_A are not constrained.
     */
    private static double[] solvePhaseStationarity(
            CefPhaseModelAdapter phase,
            double[] initialY,
            double[] lambda) {

        /*
         * V2ZR:
         *
         *     Y = [1-q1, q1, 1-q2, q2]
         *
         * with q1 = yZR(1), q2 = yZR(2).
         *
         * At fixed lambda, solve the two independent stationarity
         * equations:
         *
         *   F1 = dG/dq1 - 2*(lambdaZR-lambdaV) = 0
         *   F2 = dG/dq2 -   (lambdaZR-lambdaV) = 0
         *
         * This imposes only sublattice normalization. Element amounts
         * are deliberately free to change.
         */

        double q1 =
                initialY[1];

        double q2 =
                initialY[3];

        for (int iter = 0;
             iter < MAX_NEWTON;
             iter++) {

            double[] y = {
                1.0 - q1,
                q1,
                1.0 - q2,
                q2
            };

            if (!phase.isValid(y)) {
                throw new IllegalStateException(
                        "Invalid V2ZR trial constitution: "
                        + Arrays.toString(y));
            }

            double[] gy =
                    phase.siteGradient(T, y);

            double[][] H =
                    phase.siteHessian(T, y);

            double dLambda =
                    lambda[1] - lambda[0];

            /*
             * First independent stationarity equation.
             *
             * dq1 direction = [-1,+1,0,0].
             */
            double F1 =
                    (gy[1] - gy[0])
                    - 2.0 * dLambda;

            /*
             * Second independent stationarity equation.
             *
             * dq2 direction = [0,0,-1,+1].
             */
            double F2 =
                    (gy[3] - gy[2])
                    - dLambda;

            double residual =
                    Math.sqrt(
                            F1 * F1
                            + F2 * F2);

            if (residual < NEWTON_TOL) {
                return y;
            }

            /*
             * Jacobian with respect to q1,q2:
             *
             * J11 = d2G/dq1^2
             * J12 = d2G/dq1dq2
             * J21 = d2G/dq2dq1
             * J22 = d2G/dq2^2
             */
            double J11 =
                    H[1][1]
                    - H[1][0]
                    - H[0][1]
                    + H[0][0];

            double J12 =
                    H[1][3]
                    - H[1][2]
                    - H[0][3]
                    + H[0][2];

            double J21 =
                    H[3][1]
                    - H[3][0]
                    - H[2][1]
                    + H[2][0];

            double J22 =
                    H[3][3]
                    - H[3][2]
                    - H[2][3]
                    + H[2][2];

            double det =
                    J11 * J22
                    - J12 * J21;

            if (Math.abs(det) < 1.0e-20) {
                throw new IllegalStateException(
                        "Singular reduced V2ZR response Jacobian.");
            }

            /*
             * Newton step:
             *
             *     J * [dq1,dq2] = -[F1,F2]
             */
            double dq1 =
                    (-F1 * J22
                     + J12 * F2)
                    / det;

            double dq2 =
                    (J21 * F1
                     - J11 * F2)
                    / det;

            /*
             * Backtracking.
             */
            double alpha = 1.0;

            boolean accepted = false;

            for (int ls = 0;
                 ls < 40;
                 ls++) {

                double trialQ1 =
                        q1 + alpha * dq1;

                double trialQ2 =
                        q2 + alpha * dq2;

                double[] trialY = {
                    1.0 - trialQ1,
                    trialQ1,
                    1.0 - trialQ2,
                    trialQ2
                };

                if (!phase.isValid(trialY)) {
                    alpha *= 0.5;
                    continue;
                }

                double[] trialGy =
                        phase.siteGradient(
                                T,
                                trialY);

                double trialF1 =
                        (trialGy[1] - trialGy[0])
                        - 2.0 * dLambda;

                double trialF2 =
                        (trialGy[3] - trialGy[2])
                        - dLambda;

                double trialResidual =
                        Math.sqrt(
                                trialF1 * trialF1
                                + trialF2 * trialF2);

                if (trialResidual < residual) {

                    q1 = trialQ1;
                    q2 = trialQ2;

                    accepted = true;
                    break;
                }

                alpha *= 0.5;
            }

            if (!accepted) {
                throw new IllegalStateException(
                        "Could not find a physical, "
                        + "residual-decreasing V2ZR phase step.");
            }
        }

        throw new IllegalStateException(
                "V2ZR phase stationarity solve did not converge.");
    }

    private static double stationarityResidualNorm(
            CefPhaseModelAdapter phase,
            double[] y,
            double[] lambda,
            double[] gamma) {

        int nip =
                phase.numSiteVariables();

        int ns =
                phase.numSublattices();

        int[] offsets =
                phase.sublatticeOffsets();

        int[] nconst =
                phase.constituentsPerSublattice();

        double[] gy =
                phase.siteGradient(
                        T,
                        y);

        double[][] dMdY =
                phase.elementAmountsJacobian();

        double sum2 = 0.0;

        for (int i = 0;
             i < nip;
             i++) {

            double r =
                    gy[i];

            for (int A = 0;
                 A < lambda.length;
                 A++) {

                r -=
                        dMdY[A][i]
                        * lambda[A];
            }

            int s =
                    sublatticeOf(
                            i,
                            offsets,
                            nconst);

            r -= gamma[s];

            sum2 += r * r;
        }

        for (int s = 0;
             s < ns;
             s++) {

            double sum = 0.0;

            for (int i = 0;
                 i < nconst[s];
                 i++) {

                sum +=
                        y[
                            offsets[s] + i];
            }

            double r =
                    sum - 1.0;

            sum2 += r * r;
        }

        return Math.sqrt(sum2);
    }

    // =================================================================
    // Analytic response construction
    // =================================================================

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

                E[k][row] = 1.0;
                E[row][k] = 1.0;
            }
        }

        return E;
    }

    private static double[] calculateCG(
            double[][] inverse,
            double[] gy) {

        int nip =
                gy.length;

        double[] cG =
                new double[nip];

        for (int i = 0;
             i < nip;
             i++) {

            double sum = 0.0;

            for (int j = 0;
                 j < nip;
                 j++) {

                sum +=
                        inverse[i][j]
                        * gy[j];
            }

            /*
             * Matches EquilibriumSolverV2.calculatePhaseResponse(),
             * which established this negative-sign convention as the
             * one consistent with an actually-converging Sundman
             * updateState()/checkConvergence() implementation.
             */
            cG[i] = -sum;
        }

        return cG;
    }

    private static double[][] calculateCA(
            double[][] inverse,
            double[][] dMdY) {

        int nc =
                dMdY.length;

        int nip =
                dMdY[0].length;

        double[][] cA =
                new double[nc][nip];

        for (int A = 0;
             A < nc;
             A++) {

            for (int i = 0;
                 i < nip;
                 i++) {

                for (int j = 0;
                     j < nip;
                     j++) {

                    cA[A][i] +=
                            inverse[i][j]
                            * dMdY[A][j];
                }
            }
        }

        return cA;
    }

    // =================================================================
    // Linear algebra
    // =================================================================

    private static double[][] invert(
            double[][] A) {

        Matrix m =
                new Matrix(A);

        return m.inverse().getArray();
    }

    private static double[] solveLinearSystem(
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

    // =================================================================
    // Validation / utilities
    // =================================================================

    private static void checkReferenceComposition(
            double[] M) {

        if (M.length != 2) {
            throw new AssertionError(
                    "Expected binary V2ZR M vector.");
        }

        double total =
                M[0] + M[1];

        double xZr =
                M[1] / total;

        if (Math.abs(xZr - 0.30)
                > 1.0e-10) {

            throw new AssertionError(
                    "Reference Y does not correspond to xZr=0.30: "
                    + xZr);
        }
    }

    private static void checkResponseConstraints(
            CefPhaseModelAdapter phase,
            double[] cG,
            double[][] cA) {

        int[] offsets =
                phase.sublatticeOffsets();

        int[] nconst =
                phase.constituentsPerSublattice();

        final double tol =
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

            if (Math.abs(sumCG) >
                    tol) {

                throw new AssertionError(
                        "cG violates sublattice "
                        + s + ": "
                        + sumCG);
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

                if (Math.abs(sumCA) >
                        tol) {

                    throw new AssertionError(
                            "cA[" + A
                            + "] violates sublattice "
                            + s + ": "
                            + sumCA);
                }
            }
        }
    }

    private static double relativeVectorError(
            double[] a,
            double[] b) {

        double numerator = 0.0;
        double denominator = 0.0;

        for (int i = 0;
             i < a.length;
             i++) {

            double d =
                    a[i] - b[i];

            numerator += d * d;
            denominator += b[i] * b[i];
        }

        return Math.sqrt(numerator)
                / Math.max(
                        Math.sqrt(denominator),
                        1.0e-30);
    }

    private static double normDifference(
            double[] a,
            double[] b) {

        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector-length mismatch.");
        }

        double sum2 = 0.0;

        for (int i = 0;
             i < a.length;
             i++) {

            double d =
                    a[i] - b[i];

            sum2 += d * d;
        }

        return Math.sqrt(sum2);
    }

    private static double norm(
            double[] v) {

        double sum2 = 0.0;

        for (double x : v) {
            sum2 += x * x;
        }

        return Math.sqrt(sum2);
    }

    private static double[] negate(
            double[] v) {

        double[] r =
                new double[v.length];

        for (int i = 0;
             i < v.length;
             i++) {

            r[i] = -v[i];
        }

        return r;
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
                "Unknown sublattice for Y index "
                        + index);
    }

    private static void printHeader() {

        System.out.println(
                "============================================================");

        System.out.println(
                "V2ZR corrected Sundman phase-response baseline");

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
                "DeltaMu  : %.6f J/mol%n",
                DELTA_MU);

        System.out.println();
    }
}
