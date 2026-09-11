package calc.equil;

import system.model.PhaseEquilData;
import util.Matrix;

/**
 * Assembles and solves the global multiphase Sundman equilibrium matrix
 * (2015 Eq. 58; solver flowchart STEP 3-4, see
 * {@code docs/solver_flowchart_target.png}) from each stable phase's
 * per-phase response data.
 *
 * <p>This is calc-layer work, not model-layer work, in exactly the sense
 * {@link PhaseMatrixAssembler}'s own javadoc describes:
 * {@link PhaseMatrixAssembler#compute} produces one phase's local
 * Newton-response coefficients from any
 * {@link system.model.GibbsEnergyModel}; this class combines several
 * phases' already-computed {@link PhaseEquilData} into the GLOBAL system a
 * multiphase Newton step solves for chemical potentials and phase-amount
 * corrections. Neither class has model-specific knowledge -- every input
 * here comes from {@link PhaseEquilData}'s public fields.
 *
 * <p>Extracted from {@code EquilibriumSolverV2.buildEquilibriumMatrix()} /
 * {@code solveEquilibriumMatrix()} so this stage is independently callable
 * and testable, the same way {@link PhaseMatrixAssembler} was pulled out
 * of {@code CefGibbs.compute()} for STEP 1-2 (see
 * {@code PhaseMatrixAssemblerContractTest}).
 *
 * <h2>System assembled</h2>
 * Unknowns, in order: {@code nc} chemical potentials {@code lambda_A},
 * then {@code np} phase-amount corrections {@code DeltaOmega_k} (one per
 * stable phase, in the same order as the {@code phaseData}/
 * {@code phaseAmounts} arrays).
 *
 * <p>Phase-equilibrium rows (one per stable phase {@code k}):
 * <pre>
 *     sum_A M_A^k * lambda_A = G^k
 * </pre>
 *
 * <p>Element mass-balance rows (one per component {@code A}):
 * <pre>
 *     sum_k omega_k * sum_B R_AB^k * lambda_B
 *   + sum_k M_A^k * DeltaOmega_k
 *   = (N_A(target) - sum_k omega_k * M_A^k) - sum_k omega_k * q_A^k
 * </pre>
 * where {@code R_AB^k = PhaseEquilData.eMatNC[A][B]} and
 * {@code q_A^k = PhaseEquilData.deln[A]}, PROVIDED {@code phaseData[k]}
 * was computed with {@code mu=0}, {@code deltaT=0}, {@code deltaP=0}: at
 * that evaluation point {@link PhaseMatrixAssembler#compute}'s
 * {@code delyN} reduces to exactly {@code cG}, so {@code deln} reduces to
 * exactly {@code sum_i dM_A/dy_i * cG_i} -- Sundman's {@code q_A}. Passing
 * a {@code PhaseEquilData} computed at nonzero mu/deltaT/deltaP produces a
 * wrong system; this class does not itself enforce that precondition
 * (it has no way to see how {@code phaseData[k]} was computed), so
 * callers must compute every {@code phaseData} entry that way.
 */
public final class GlobalEquilibriumMatrixAssembler {

    private GlobalEquilibriumMatrixAssembler() { }

    /** The assembled (A, b) system and its solution. */
    public static final class Result {
        /** The assembled (nc+np) x (nc+np) matrix. */
        public final double[][] matrix;
        /** The assembled right-hand side, length nc+np. */
        public final double[] rhs;
        /** New chemical potentials lambda_A, length nc. */
        public final double[] lambda;
        /** Phase-amount corrections DeltaOmega_k, length np, in phase order. */
        public final double[] deltaOmega;

        Result(double[][] matrix, double[] rhs,
               double[] lambda, double[] deltaOmega) {
            this.matrix = matrix;
            this.rhs = rhs;
            this.lambda = lambda;
            this.deltaOmega = deltaOmega;
        }
    }

    /**
     * Builds and solves the global equilibrium matrix for the given stable
     * phases.
     *
     * @param phaseData     each stable phase's {@link PhaseEquilData},
     *                      evaluated at mu=0, deltaT=0, deltaP=0 (see class
     *                      javadoc for why that evaluation point is
     *                      required for {@code deln} to equal {@code q_A})
     * @param phaseAmounts  each stable phase's current amount omega_k,
     *                      same order as {@code phaseData}
     * @param targetAmounts target element amounts N_A, length nc
     * @return the assembled system and its solution
     * @throws IllegalStateException if the assembled matrix is singular
     */
    public static Result assembleAndSolve(PhaseEquilData[] phaseData,
                                           double[] phaseAmounts,
                                           double[] targetAmounts) {

        double[][] A = buildMatrix(phaseData, phaseAmounts, targetAmounts);
        double[] b = buildRhs(phaseData, phaseAmounts, targetAmounts);

        int nc = targetAmounts.length;
        int np = phaseData.length;
        int n = nc + np;

        Matrix matA = new Matrix(A);
        Matrix matB = new Matrix(b, n);

        double[] solution;
        try {
            solution = matA.solve(matB).getColumnPackedCopy();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to solve global Sundman equilibrium system.", e);
        }

        double[] lambda = new double[nc];
        System.arraycopy(solution, 0, lambda, 0, nc);

        double[] deltaOmega = new double[np];
        System.arraycopy(solution, nc, deltaOmega, 0, np);

        return new Result(A, b, lambda, deltaOmega);
    }

    /**
     * Builds the global matrix only (no solve) -- exposed separately so a
     * test can check the assembled matrix/RHS against a reference without
     * depending on the linear solve.
     */
    public static double[][] buildMatrix(PhaseEquilData[] phaseData,
                                          double[] phaseAmounts,
                                          double[] targetAmounts) {

        int nc = targetAmounts.length;
        int np = phaseData.length;
        int n = nc + np;

        double[][] A = new double[n][n];

        // Phase-equilibrium rows: sum_A M_A^k * lambda_A = G^k.
        // DeltaOmega columns are zero (left at their default 0.0).
        for (int k = 0; k < np; k++) {
            PhaseEquilData pd = phaseData[k];
            for (int Aidx = 0; Aidx < nc; Aidx++) {
                A[k][Aidx] = pd.mA[Aidx];
            }
        }

        // Element mass-balance rows.
        for (int Aidx = 0; Aidx < nc; Aidx++) {
            int row = np + Aidx;

            for (int Bidx = 0; Bidx < nc; Bidx++) {
                double value = 0.0;
                for (int k = 0; k < np; k++) {
                    value += phaseAmounts[k] * phaseData[k].eMatNC[Aidx][Bidx];
                }
                A[row][Bidx] = value;
            }

            for (int k = 0; k < np; k++) {
                A[row][nc + k] = phaseData[k].mA[Aidx];
            }
        }

        return A;
    }

    /**
     * Builds the global RHS only (no solve) -- see {@link #buildMatrix}.
     */
    public static double[] buildRhs(PhaseEquilData[] phaseData,
                                     double[] phaseAmounts,
                                     double[] targetAmounts) {

        int nc = targetAmounts.length;
        int np = phaseData.length;
        int n = nc + np;

        double[] b = new double[n];

        // Phase-equilibrium rows: RHS = G^k.
        for (int k = 0; k < np; k++) {
            b[k] = phaseData[k].G;
        }

        // Element mass-balance rows:
        //   b_A = (N_A(target) - sum_k omega_k*M_A^k) - sum_k omega_k*q_A^k
        for (int Aidx = 0; Aidx < nc; Aidx++) {
            int row = np + Aidx;

            double represented = 0.0;
            double q = 0.0;
            for (int k = 0; k < np; k++) {
                represented += phaseAmounts[k] * phaseData[k].mA[Aidx];
                q += phaseAmounts[k] * phaseData[k].deln[Aidx];
            }

            double massResidual = targetAmounts[Aidx] - represented;
            b[row] = massResidual - q;
        }

        return b;
    }
}
