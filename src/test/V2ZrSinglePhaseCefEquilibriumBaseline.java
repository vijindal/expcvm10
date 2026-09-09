package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory.PhaseModel;
import system.model.cef.CefPhaseModelAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Baseline 3:
 *
 * Independent single-phase V2ZR CEF equilibrium calculation.
 *
 * This test DOES NOT use EquilibriumSolver.
 *
 * It minimizes the validated CEF Gibbs-energy surface subject to:
 *
 *   yV(1)  + yZR(1) = 1
 *   yV(2)  + yZR(2) = 1
 *
 * and the imposed overall Zr composition:
 *
 *   2*yZR(1) + yZR(2) = 3*xZR
 *
 * For xZR = 0.30:
 *
 *   2*yZR(1) + yZR(2) = 0.90
 *
 * The two composition constraints allow the entire problem to be
 * reduced to one independent variable:
 *
 *   q = yZR(1)
 *   yZR(2) = 0.90 - 2*q
 *
 * Therefore:
 *
 *   Y = [1-q, q, 1-(0.90-2q), 0.90-2q]
 *     = [1-q, q, 0.10+2q, 0.90-2q]
 *
 * The scalar stationarity condition is
 *
 *   dG/dq = 0
 *
 * with
 *
 *   dY/dq = [-1, +1, +2, -2].
 *
 * Hence
 *
 *   dG/dq = grad(G) . dY/dq.
 *
 * The second derivative is
 *
 *   d2G/dq2 = dY/dq^T * H * dY/dq.
 *
 * Newton iterations are performed directly on this constrained
 * one-dimensional thermodynamic problem.
 *
 * This gives us an independent thermodynamic reference before
 * implementing the same calculation inside EquilibriumSolver.
 */
public class V2ZrSinglePhaseCefEquilibriumBaseline {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    private static final double T = 1500.0;

    // Overall composition [V,ZR].
    private static final double XV = 0.70;
    private static final double XZR = 0.30;

    // V2ZR contains 3 sublattice sites per formula unit:
    // site ratio = [2,1].
    private static final double TOTAL_SITES = 3.0;

    private static final double TARGET_ZR = TOTAL_SITES * XZR;

    private static final double TOL_F = 1.0e-8;
    private static final double TOL_STEP = 1.0e-12;

    private static final int MAX_ITER = 50;

    /*
     * Start at the old-solver constitution.
     *
     * q = yZR(1) = 0.26355668629388834
     *
     * This is deliberately only an initial guess.
     * The result must be determined from the CEF surface itself.
     */
    private static final double INITIAL_Q =
            0.26355668629388834;

    public static void main(String[] args) throws Exception {

        printHeader();

        if (Math.abs(XV + XZR - 1.0) > 1.0e-12) {
            throw new IllegalStateException(
                    "Overall composition is not normalized.");
        }

        // ------------------------------------------------------------
        // 1. Load V-Zr database and build V2ZR CEF model.
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
                    "Expected exactly one V2ZR model, got "
                            + models.size());
        }

        PhaseModel pm = models.get(0);

        if (pm.gibbs == null) {
            throw new IllegalStateException(
                    "V2ZR is not backed by CefGibbs.");
        }

        CefPhaseModelAdapter adapter =
                new CefPhaseModelAdapter(
                        pm.gibbs,
                        pm.magnetic,
                        pm.phaseName,
                        new ArrayList<>(elements),
                        pm.constituentNames);

        // ------------------------------------------------------------
        // 2. Feasible interval for q = yZR(1).
        //
        // yZR(1) = q              must satisfy 0 <= q <= 1
        // yZR(2) = TARGET_ZR-2q   must satisfy 0 <= ... <= 1
        //
        // Therefore:
        //
        // max(0, (TARGET_ZR-1)/2) <= q
        // <= min(1, TARGET_ZR/2)
        // ------------------------------------------------------------
        double qMin = Math.max(
                0.0,
                (TARGET_ZR - 1.0) / 2.0);

        double qMax = Math.min(
                1.0,
                TARGET_ZR / 2.0);

        if (!(qMin < qMax)) {
            throw new IllegalStateException(
                    "No feasible V2ZR constitution for XZR="
                            + XZR);
        }

        double q = INITIAL_Q;

        if (q < qMin || q > qMax) {
            q = 0.5 * (qMin + qMax);
        }

        System.out.println();
        System.out.printf(
                "Overall composition: X[V,ZR] = [%.12f, %.12f]%n",
                XV, XZR);
        System.out.printf(
                "Target Zr amount     = %.12f mol Zr/fu%n",
                TARGET_ZR);
        System.out.printf(
                "Feasible q interval  = [%.12f, %.12f]%n",
                qMin, qMax);
        System.out.printf(
                "Initial q             = %.15f%n",
                q);

        // ------------------------------------------------------------
        // 3. Newton solve.
        // ------------------------------------------------------------
        boolean converged = false;

        System.out.println();
        System.out.println(
                "Newton iterations");
        System.out.println(
                "-----------------");
        System.out.println(
                "iter        q              dG/dq             d2G/dq2");

        for (int iter = 0; iter < MAX_ITER; iter++) {

            double[] y = constitution(q);

            double G = adapter.siteEnergy(T, y);
            double[] grad = adapter.siteGradient(T, y);
            double[][] hess = adapter.siteHessian(T, y);

            double[] dqVector = {
                    -1.0,
                    +1.0,
                    +2.0,
                    -2.0
            };

            double dGdq = dot(grad, dqVector);
            double d2Gdq2 = quadraticForm(
                    dqVector, hess);

            System.out.printf(
                    "%3d  % .15f  % .12e  % .12e%n",
                    iter, q, dGdq, d2Gdq2);

            if (!Double.isFinite(G)
                    || !Double.isFinite(dGdq)
                    || !Double.isFinite(d2Gdq2)) {

                throw new IllegalStateException(
                        "Non-finite CEF thermodynamic quantity.");
            }

            if (Math.abs(dGdq) < TOL_F) {
                converged = true;
                break;
            }

            if (Math.abs(d2Gdq2) < 1.0e-14) {
                throw new IllegalStateException(
                        "Near-zero constrained curvature during "
                                + "Newton iteration.");
            }

            double dq = -dGdq / d2Gdq2;

            /*
             * Keep Newton inside the physically admissible interval.
             * Backtracking is used only when necessary.
             */
            double trialQ = q + dq;

            if (trialQ < qMin || trialQ > qMax) {

                double scale = 1.0;

                while (scale > 1.0e-12) {

                    scale *= 0.5;

                    trialQ = q + scale * dq;

                    if (trialQ >= qMin
                            && trialQ <= qMax) {
                        break;
                    }
                }
            }

            if (trialQ < qMin || trialQ > qMax) {

                throw new IllegalStateException(
                        "Newton step could not be kept inside "
                                + "the physical constitution domain.");
            }

            if (Math.abs(trialQ - q) < TOL_STEP) {
                q = trialQ;
                converged = true;
                break;
            }

            q = trialQ;
        }

        // ------------------------------------------------------------
        // 4. Final state.
        // ------------------------------------------------------------
        double[] yFinal = constitution(q);

        double GFinal =
                adapter.siteEnergy(T, yFinal);

        double[] gradFinal =
                adapter.siteGradient(T, yFinal);

        double[][] hessFinal =
                adapter.siteHessian(T, yFinal);

        double[] direction = {
                -1.0,
                +1.0,
                +2.0,
                -2.0
        };

        double stationarityResidual =
                dot(gradFinal, direction);

        double curvature =
                quadraticForm(direction, hessFinal);

        double[] M =
                adapter.elementAmounts(yFinal);

        double xV = M[0] / TOTAL_SITES;
        double xZR = M[1] / TOTAL_SITES;

        double compositionResidual =
                xZR - XZR;

        // ------------------------------------------------------------
        // 5. Print final result.
        // ------------------------------------------------------------
        System.out.println();
        System.out.println(
                "Single-phase CEF equilibrium");
        System.out.println(
                "-----------------------------");

        System.out.println(
                "Converged              : " + converged);

        System.out.printf(
                "q = yZR(1)             : %.15f%n",
                q);

        System.out.printf(
                "yZR(2)                 : %.15f%n",
                yFinal[3]);

        System.out.println(
                "Y                      : "
                        + Arrays.toString(yFinal));

        System.out.printf(
                "G                      : %.15f J/mol-fu%n",
                GFinal);

        System.out.printf(
                "M[V]                   : %.15f%n",
                M[0]);

        System.out.printf(
                "M[ZR]                  : %.15f%n",
                M[1]);

        System.out.printf(
                "x[V]                   : %.15f%n",
                xV);

        System.out.printf(
                "x[ZR]                  : %.15f%n",
                xZR);

        System.out.printf(
                "composition residual   : %.6e%n",
                compositionResidual);

        System.out.printf(
                "stationarity residual  : %.6e J/mol-fu%n",
                stationarityResidual);

        System.out.printf(
                "constrained curvature : %.6e%n",
                curvature);

        // ------------------------------------------------------------
        // 6. Acceptance tests.
        // ------------------------------------------------------------
        if (!converged) {
            throw new AssertionError(
                    "Single-phase constrained CEF Newton solve "
                            + "did not converge.");
        }

        if (Math.abs(compositionResidual) > 1.0e-12) {
            throw new AssertionError(
                    "Overall composition constraint failed: "
                            + compositionResidual);
        }

        if (Math.abs(stationarityResidual) > TOL_F) {
            throw new AssertionError(
                    "CEF stationarity condition failed: "
                            + stationarityResidual);
        }

        if (!(curvature > 0.0)) {
            throw new AssertionError(
                    "Stationary point is not locally stable: "
                            + "constrained curvature = "
                            + curvature);
        }

        for (double value : yFinal) {
            if (!Double.isFinite(value)
                    || value < -1.0e-12
                    || value > 1.0 + 1.0e-12) {

                throw new AssertionError(
                        "Unphysical final site fraction: "
                                + value);
            }
        }

        System.out.println();
        System.out.println(
                "PASS: Independent single-phase V2ZR CEF "
                        + "equilibrium was obtained.");
        System.out.println();
        System.out.println(
                "This result is the thermodynamic reference "
                        + "for the first Sundman EquilibriumSolver "
                        + "implementation.");
    }

    /**
     * Construct the full CEF site-fraction vector from
     * q = yZR(1).
     */
    private static double[] constitution(double q) {

        double yZR1 = q;
        double yZR2 = TARGET_ZR - 2.0 * q;

        return new double[] {
                1.0 - yZR1,
                yZR1,
                1.0 - yZR2,
                yZR2
        };
    }

    private static double dot(
            double[] a,
            double[] b) {

        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector-length mismatch.");
        }

        double sum = 0.0;

        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }

    private static double quadraticForm(
            double[] v,
            double[][] A) {

        if (A.length != v.length) {
            throw new IllegalArgumentException(
                    "Matrix/vector dimension mismatch.");
        }

        double sum = 0.0;

        for (int i = 0; i < v.length; i++) {
            double rowSum = 0.0;

            if (A[i].length != v.length) {
                throw new IllegalArgumentException(
                        "Non-square Hessian.");
            }

            for (int j = 0; j < v.length; j++) {
                rowSum += A[i][j] * v[j];
            }

            sum += v[i] * rowSum;
        }

        return sum;
    }

    private static void printHeader() {

        System.out.println(
                "============================================================");
        System.out.println(
                "V2ZR independent single-phase CEF equilibrium baseline");
        System.out.println(
                "============================================================");
        System.out.println(
                "Database : " + TDB_PATH);
        System.out.println(
                "Phase    : " + PHASE_NAME);
        System.out.printf(
                "T        : %.2f K%n", T);
        System.out.printf(
                "X[V,ZR]  : [%.6f, %.6f]%n", XV, XZR);
        System.out.println(
                "Method   : constrained 1-D Newton solve on CEF G(Y)");
    }
}
