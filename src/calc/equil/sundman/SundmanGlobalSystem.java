package calc.equil.sundman;

import util.Matrix;

import java.util.List;

/**
 * Assembles and solves the global linearized Eq. (6) system for the
 * currently stable phases: one Gibbs-Duhem/phase-equilibrium row per
 * stable phase (Eq. 7) and one mass-balance row per component.
 *
 * <h2>Equations</h2>
 * <pre>
 *   F_α     = G_M^α + Σ_A μ_A·M_A^α = 0                for each stable phase α   (Eq. 7)
 *   F_mass,A = Σ_α ℵ^α·M_A^α − Ñ_A = 0                  for each component A     (mass balance, Eq. 6's 2nd term)
 * </pre>
 * Unknowns: Δμ_B (B=1..nc), Δℵ_α (α over stable phases).
 *
 * <h2>Jacobian (derived in SundmanPhaseStep's Javadoc; repeated here for
 * the assembly context)</h2>
 * <pre>
 *   ∂F_α/∂ℵ_β      = 0                                          (a phase's own G, M don't depend on any amount)
 *   ∂F_α/∂μ_B      = M_B^α + dGdMu^α[B] + Σ_A μ_A·dMdMu^α[A][B]
 *   ∂F_mass,A/∂ℵ_α = M_A^α
 *   ∂F_mass,A/∂μ_B = Σ_α ℵ^α·dMdMu^α[A][B]
 * </pre>
 * Newton update solves J·Δx = −F for Δx = [Δμ; Δℵ].
 *
 * <p>This class only assembles and solves the linear system; it does not
 * decide phase-set membership, apply damping, or update any state — that
 * is {@link SundmanEquilibriumSolver}'s responsibility.
 */
public final class SundmanGlobalSystem {

    /**
     * Condition-number ceiling above which a solve is rejected as
     * numerically unreliable, even though {@code Matrix.solve()} did not
     * throw. This is a minimal robustness layer, not a general linear-
     * algebra library: it uses {@code Matrix.cond()} (already implemented
     * via SVD in the existing numerical infrastructure) as a cheap,
     * off-the-shelf ill-conditioning check, plus finite-value and
     * solve-residual checks below. 1e12 is a conservative, standard rule
     * of thumb for double-precision linear solves (roughly the point
     * where half of double's ~16 significant digits are already lost to
     * conditioning) — an implementation choice, not a value from Sundman.
     */
    private static final double MAX_CONDITION_NUMBER = 1e12;

    /**
     * Relative solve-residual ceiling: ||J·Δx − rhs|| / (||rhs|| + floor)
     * must be below this for a solve to be accepted. Catches the case
     * where {@code Matrix.solve()} returns finite but numerically
     * meaningless values for a near-singular system that {@code cond()}
     * alone might not flag as cleanly (e.g. under a different norm).
     */
    private static final double MAX_RELATIVE_RESIDUAL = 1e-6;

    /** Jacobian, (nc+np)×(nc+np): rows 0..np-1 are Gibbs-Duhem, rows np..np+nc-1 are mass balance. */
    public final double[][] jac;

    /** Residual vector F (NOT negated), same row order as jac. */
    public final double[] F;

    /**
     * True if the assembled matrix was singular, ill-conditioned, or
     * produced a non-finite/inconsistent solution — no valid Newton
     * correction is available this iteration. Includes both the case
     * where {@code Matrix.solve()} threw AND the case where it returned
     * finite numbers that fail the condition-number/residual/finiteness
     * checks below.
     */
    public final boolean singular;

    /** Human-readable reason for {@code singular}, or null if not singular. Diagnostic only. */
    public final String singularReason;

    /** Solution Δx = [Δμ_1..Δμ_nc, Δℵ_1..Δℵ_np], or null if singular. */
    public final double[] delta;

    private SundmanGlobalSystem(double[][] jac, double[] F, boolean singular, String singularReason, double[] delta) {
        this.jac = jac;
        this.F = F;
        this.singular = singular;
        this.singularReason = singularReason;
        this.delta = delta;
    }

    /**
     * Assembles and solves the global system for the given stable phases
     * (each already evaluated via {@link SundmanPhase#evaluate} and paired
     * with its {@link SundmanPhaseStep}) at the current μ.
     */
    public static SundmanGlobalSystem solve(List<SundmanPhase> stable,
                                             List<SundmanPhaseStep> steps,
                                             double[] mu,
                                             double[] Ntotal) {
        int np = stable.size();
        int nc = mu.length;
        int dim = nc + np;

        double[][] jac = new double[dim][dim];
        double[] F = new double[dim];

        // Row block 1: Gibbs-Duhem, rows 0..np-1
        for (int a = 0; a < np; a++) {
            SundmanPhase phase = stable.get(a);
            SundmanPhaseStep step = steps.get(a);

            // F_a = G_M^a + Sum_A mu_A M_A^a  (Eq. 7), PLUS the first-order
            // change produced by the internal-stationarity relaxation
            // dyResidual (which moves both G_M^a and M_A^a). Omitting these
            // terms makes the reduced system blind to any internal
            // disequilibrium, freezing each phase's constitution at its
            // initial estimate.
            double residual = phase.G + step.dGresidual;
            for (int A = 0; A < nc; A++) {
                residual += mu[A] * (phase.mA[A] + step.dMresidual[A]);
            }
            F[a] = residual;

            for (int B = 0; B < nc; B++) {
                double jval = phase.mA[B] + step.dGdMu[B];
                for (int A = 0; A < nc; A++) jval += mu[A] * step.dMdMu[A][B];
                jac[a][B] = jval;
            }
            // columns nc..nc+np-1 (d/dAleph) remain zero
        }

        // Row block 2: mass balance, rows np..np+nc-1
        for (int A = 0; A < nc; A++) {
            int row = np + A;
            // Sum_a Aleph^a M_A^a - N_A, plus the first-order change in
            // M_A^a produced by the internal relaxation dyResidual.
            double sum = 0.0;
            for (int a = 0; a < np; a++) {
                sum += stable.get(a).amount * (stable.get(a).mA[A] + steps.get(a).dMresidual[A]);
            }
            F[row] = sum - Ntotal[A];

            for (int B = 0; B < nc; B++) {
                double jval = 0.0;
                for (int a = 0; a < np; a++) jval += stable.get(a).amount * steps.get(a).dMdMu[A][B];
                jac[row][B] = jval;
            }
            for (int a = 0; a < np; a++) {
                jac[row][nc + a] = stable.get(a).mA[A];
            }
        }

        // Solve J*delta = -F
        double[] rhs = new double[dim];
        for (int i = 0; i < dim; i++) rhs[i] = -F[i];

        Matrix matJ = new Matrix(jac);

        // ---- Numerical safety layer (minimal, using existing infrastructure) ----
        // 1. Condition-number check via the existing SVD-based Matrix.cond(),
        //    which does not throw on a singular/near-singular matrix and so
        //    catches cases Matrix.solve()'s exception alone would miss.
        double condNumber;
        try {
            condNumber = matJ.cond();
        } catch (Exception e) {
            return new SundmanGlobalSystem(jac, F, true, "condition number could not be computed: " + e.getMessage(), null);
        }
        if (Double.isNaN(condNumber) || Double.isInfinite(condNumber) || condNumber > MAX_CONDITION_NUMBER) {
            return new SundmanGlobalSystem(jac, F, true,
                    "ill-conditioned Jacobian (cond=" + condNumber + " > " + MAX_CONDITION_NUMBER + ")", null);
        }

        // 2. Attempt the solve itself (LU-based; still may throw on an
        //    exactly singular matrix that slipped past the cond() check
        //    due to floating-point edge cases).
        double[] delta;
        try {
            Matrix matRhs = new Matrix(rhs, dim);
            Matrix sol = matJ.solve(matRhs);
            delta = new double[dim];
            for (int i = 0; i < dim; i++) delta[i] = sol.get(i, 0);
        } catch (Exception e) {
            return new SundmanGlobalSystem(jac, F, true, "solve() threw: " + e.getMessage(), null);
        }

        // 3. Finiteness check: reject any NaN/Infinity in the raw solution
        //    outright, regardless of how it arose.
        for (double v : delta) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return new SundmanGlobalSystem(jac, F, true, "non-finite correction returned by solve()", null);
            }
        }

        // 4. Solve-residual check: ||J*delta - rhs|| / (||rhs|| + floor)
        //    must be small. Catches a finite-but-numerically-meaningless
        //    solution that both solve() and cond() failed to flag.
        double residualNorm = 0.0;
        double rhsNorm = 0.0;
        for (int i = 0; i < dim; i++) {
            double predicted = 0.0;
            for (int j = 0; j < dim; j++) predicted += jac[i][j] * delta[j];
            double diff = predicted - rhs[i];
            residualNorm += diff * diff;
            rhsNorm += rhs[i] * rhs[i];
        }
        residualNorm = Math.sqrt(residualNorm);
        rhsNorm = Math.sqrt(rhsNorm);
        double relativeResidual = residualNorm / (rhsNorm + 1e-12);
        if (Double.isNaN(relativeResidual) || relativeResidual > MAX_RELATIVE_RESIDUAL) {
            return new SundmanGlobalSystem(jac, F, true,
                    "solve residual too large (relative residual=" + relativeResidual + ")", null);
        }

        return new SundmanGlobalSystem(jac, F, false, null, delta);
    }
}
