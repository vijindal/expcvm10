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

    /**
     * Converts an already-assembled (matrix, rhs) pair from {@link
     * #buildMatrix}/{@link #buildRhs} into the ZPF boundary-fixing system
     * Sundman's Algorithm C2 needs: phase {@code fixedSlotIndex}'s amount
     * is removed as a Newton unknown (its column is repurposed) while
     * component {@code releasedComponentIndex}'s target amount -- normally
     * a fixed input -- becomes the new unknown occupying that column, so
     * the amount-of-that-component correction needed to sit exactly on the
     * boundary falls out of the solve.
     *
     * <p>This is variable ELIMINATION, matching OpenCalphad's own
     * mechanism (verified directly against {@code matsmin.F90}): fixing a
     * phase's amount does NOT add a new "amount = fixedAmount" equation
     * row, and does NOT remove that phase's own phase-equilibrium row --
     * both the row count and the unknown count stay exactly {@code nc+np},
     * only the classification of one column changes. Concretely (see
     * {@code setup_equilmatrix}'s "incl fixed" phase-equilibrium loop and
     * the "if(pmi%phasestatus.ne.PHFIXED) notf=notf+1" column-index
     * pattern repeated at every condition-row builder): a fixed phase
     * keeps contributing to the matrix as a KNOWN constant amount (its
     * {@code phaseAmounts[fixedSlotIndex]} entry, already driven to {@code
     * fixedAmount} by the caller before this method is invoked -- see
     * {@link EquilibriumSolverV2#solveBoundary}), it simply loses its own
     * unknown column.
     *
     * <p>The freed column ({@code nc + fixedSlotIndex}, previously the
     * {@code DeltaOmega_fixedSlotIndex} coefficients: zero in every
     * phase-equilibrium row, {@code phaseData[k].mA[Aidx]} in every
     * mass-balance row per {@link #buildMatrix}) is replaced with
     * coefficient {@code -1} in mass-balance row {@code np +
     * releasedComponentIndex}, 0 elsewhere. Solving the SAME-SIZE system
     * then yields, in that column, {@code Delta targetAmounts[
     * releasedComponentIndex]} -- an INCREMENT, exactly like every other
     * {@code DeltaOmega_k} this system already solves for (not an
     * absolute value like {@code lambda}) -- so the caller applies it the
     * same way {@code updateState()} already applies {@code
     * deltaPhaseAmounts}: {@code targetAmounts[releasedComponentIndex] +=
     * solution[nc + fixedSlotIndex]}.
     *
     * <p>Sign derivation: row {@code np+releasedComponentIndex}'s
     * unmodified equation is
     * {@code LHS_others + mA_fixed^A * DeltaOmega_fixed = target_A - represented - q}.
     * Substituting {@code target_A = target_A_current + Delta_target_A}
     * and moving the now-solved-for {@code Delta_target_A} to the LHS
     * gives {@code LHS_others + (-1)*Delta_target_A = target_A_current -
     * represented - q} -- i.e. the RHS is UNCHANGED from {@link
     * #buildRhs}'s existing {@code massResidual - q} (which already uses
     * {@code target_A_current}), and only the column coefficient (-1)
     * differs from an ordinary {@code DeltaOmega} column.
     *
     * <p>{@code buildMatrix}/{@code buildRhs}'s own mass-balance formulas
     * are otherwise unchanged and still correct here -- they already fold
     * {@code phaseAmounts[fixedSlotIndex]} in as a plain constant for
     * every row (there is nothing that treats slot {@code
     * fixedSlotIndex} specially versus any other stable slot in either
     * method); only the LHS column swap below is new.
     *
     * @param matrix                  the assembled matrix from {@link #buildMatrix},
     *                                built with {@code phaseAmounts[fixedSlotIndex]}
     *                                already equal to the phase's fixed amount
     * @param rhs                     the assembled RHS from {@link #buildRhs},
     *                                built with the same {@code phaseAmounts}
     * @param nc                      number of components
     * @param np                      number of stable phases
     * @param fixedSlotIndex          index into {@code phaseData}/{@code phaseAmounts} of
     *                                the phase whose amount is fixed
     * @param releasedComponentIndex  index of the component whose target amount is
     *                                released and solved for instead
     * @return a NEW (matrix, rhs) pair -- the inputs are not mutated
     */
    public static Result convertToFixedPhaseAmountSystem(
            double[][] matrix,
            double[] rhs,
            int nc,
            int np,
            int fixedSlotIndex,
            int releasedComponentIndex) {

        int n = nc + np;

        double[][] A = new double[n][];
        for (int row = 0; row < n; row++) {
            A[row] = matrix[row].clone();
        }
        double[] b = rhs.clone();

        int fixedColumn = nc + fixedSlotIndex;

        for (int row = 0; row < n; row++) {
            A[row][fixedColumn] = 0.0;
        }
        A[np + releasedComponentIndex][fixedColumn] = -1.0;

        return new Result(A, b, null, null);
    }

    /**
     * As {@link #convertToFixedPhaseAmountSystem}, but releases
     * TEMPERATURE instead of a composition component -- needed to
     * locate an invariant node (Sundman Algorithm C2 applied along the
     * temperature axis, e.g. a eutectic/peritectic) rather than an
     * ordinary two-phase boundary.
     *
     * <p>Unlike the composition-release case (a single {@code -1} entry
     * in ONE mass-balance row), releasing T touches potentially every
     * row, since temperature is a GLOBAL condition every stable phase
     * responds to, not a per-component target:
     * <ul>
     *   <li>Phase-equilibrium row {@code k}: {@code M_A^k*lambda_A -
     *       dG_dT^k*DeltaT = G^k} -- only phase {@code k}'s own row
     *       gets a nonzero entry, {@code -phaseData[k].dG_dT}, in the
     *       freed column.</li>
     *   <li>Mass-balance row {@code A}: the T-dependent part of
     *       {@code q_A = sum_k omega_k*deln_k[A]} is {@code
     *       sum_k omega_k*phaseData[k].dM_dT[A]*DeltaT} (every stable
     *       phase's own composition response to T, weighted by its
     *       amount) -- moving this to the LHS gives coefficient {@code
     *       +sum_k omega_k*phaseData[k].dM_dT[A]} in the freed column.</li>
     * </ul>
     * Solving the SAME-SIZE system yields, in the freed column, {@code
     * DeltaT} directly (an increment, exactly like {@code DeltaOmega} --
     * NOT an absolute value like {@code lambda}); the caller applies it
     * as {@code T += solution[nc + fixedSlotIndex]}.
     *
     * <p>The RHS is unchanged from {@link #buildRhs} for the same reason
     * as the composition-release case: {@code deln}/{@code G} are still
     * evaluated at {@code deltaT=0} by {@link
     * calc.equil.PhaseMatrixAssembler#compute}, so the existing RHS
     * already reflects the current (pre-step) state; only the LHS
     * column's meaning changes.
     *
     * @param phaseData      same array used to build {@code matrix}/{@code rhs}
     *                       (needed here for {@code dG_dT}/{@code dM_dT},
     *                       which {@code buildMatrix}/{@code buildRhs} don't
     *                       themselves consume)
     * @param phaseAmounts   same array used to build {@code matrix}/{@code rhs}
     * @param fixedSlotIndex index of the phase whose amount is fixed
     * @return a NEW (matrix, rhs) pair -- the inputs are not mutated
     */
    public static Result convertToFixedPhaseAmountSystemReleasingT(
            double[][] matrix,
            double[] rhs,
            PhaseEquilData[] phaseData,
            double[] phaseAmounts,
            int nc,
            int np,
            int fixedSlotIndex) {

        int n = nc + np;

        double[][] A = new double[n][];
        for (int row = 0; row < n; row++) {
            A[row] = matrix[row].clone();
        }
        double[] b = rhs.clone();

        int fixedColumn = nc + fixedSlotIndex;

        for (int row = 0; row < n; row++) {
            A[row][fixedColumn] = 0.0;
        }

        A[fixedSlotIndex][fixedColumn] = -phaseData[fixedSlotIndex].dG_dT;

        for (int Aidx = 0; Aidx < nc; Aidx++) {
            double coeff = 0.0;
            for (int k = 0; k < np; k++) {
                coeff += phaseAmounts[k] * phaseData[k].dM_dT[Aidx];
            }
            A[np + Aidx][fixedColumn] = coeff;
        }

        return new Result(A, b, null, null);
    }
}
