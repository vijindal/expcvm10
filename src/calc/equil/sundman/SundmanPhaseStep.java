package calc.equil.sundman;

import util.Matrix;

/**
 * Per-phase "solve eq. (6)" reduction (Sundman, Dupin &amp; Hallstedt,
 * CALPHAD 75 (2021) 102330, Fig. 1's "solve eq. (6)" box), rederived
 * directly from the Lagrangian rather than ported from the old
 * eMatNC/PhaseEquilData plumbing.
 *
 * <h2>Derivation</h2>
 * The Lagrangian (Eq. 6) is
 * <pre>
 *   L = Σ_α ℵ^α G_M^α + Σ_A μ_A (Σ_α ℵ^α M_A^α − Ñ_A)
 *     + Σ_α Σ_s η_s^α (1 − Σ_i y_is^α) + Σ_φ γ^φ ℵ^φ
 * </pre>
 * For a single phase α, holding ℵ^α fixed and nonzero, the internal
 * (site-fraction) stationarity condition ∂L/∂y_m = 0 is
 * <pre>
 *   Gx[m] + Σ_A μ_A·(∂M_A/∂y_m) = η_{s(m)}
 * </pre>
 * where {@code s(m)} is the sublattice containing flat index m, and
 * (Sundman Eq. 2, linear in y) ∂M_A/∂y_m = a[s(m)] if constituent m maps
 * to element A, else 0. Summing over A, the term Σ_A μ_A·∂M_A/∂y_m
 * collapses to μ_mapped(m) = μ_{element(m)} if m maps to a modeled
 * element, else 0 (a vacancy contributes to no M_A, so it has no
 * associated chemical potential in this condition) — this is the
 * mu-to-site-fraction map, now derived from the Lagrangian rather than
 * assumed. So the condition is
 * <pre>
 *   Gx[m] + muMapped[m] = eta_{s(m)}                                  (*)
 * </pre>
 * together with the ns sublattice constraints Σ_i y_is = 1 (Eq. 1).
 *
 * <p>Linearizing (*) and the constraints in [Δy, Δη] for a change Δμ
 * (with Gxx = ∂Gx/∂y treated as fixed within one Newton step — the
 * standard "frozen Hessian" linearization) gives the bordered system
 * <pre>
 *   [ Gxx   Bᵀ ] [ Δy ]   [ -Δ(muMapped) ]
 *   [ B     0  ] [ Δη ] = [      0        ]
 * </pre>
 * where B has one row per sublattice s, with 1s at the flat indices
 * belonging to s and 0 elsewhere (this enforces Σ_i Δy_is = 0 for every
 * sublattice independently — the fix validated in M2 Step 5, rederived
 * here directly from Eq. 1 rather than carried over as code). Inverting
 * this (nip+ns)×(nip+ns) matrix and taking the top-left nip×nip block
 * gives eMat, the response matrix:
 * <pre>
 *   Δy = eMat · Δ(muMapped)
 * </pre>
 * (the diagonal/off-diagonal split from the old code's separate cG/cT/cP
 * terms is not needed here because T, P are FIXED conditions for Algorithm
 * A as specified in this task — no deltaT/deltaP terms are carried).
 *
 * <p>Chaining Δ(muMapped)[m] = Σ_B [m maps to B]·Δμ_B through eMat gives
 * <pre>
 *   ∂y_m/∂μ_B = Σ_{m maps to B} eMat[m][j]      (dyDmu[m][B])
 * </pre>
 * and then, since M_A(y) = Σ_m c[A][m]·y[m] is linear (c[A][m]=a[s(m)] if
 * m maps to A else 0):
 * <pre>
 *   ∂M_A/∂μ_B = Σ_m c[A][m]·(∂y_m/∂μ_B) = Σ_m c[A][m]·dyDmu[m][B]     (dMdMu)
 *   ∂G_M/∂μ_B = Σ_m Gx[m]·(∂y_m/∂μ_B) = Σ_m Gx[m]·dyDmu[m][B]         (dGdMu)
 * </pre>
 * dMdMu is the corrected replacement for the old eMatNC; dGdMu is the
 * term M2 Step 8 found missing from the old Jacobian — both are included
 * here from the start.
 */
public final class SundmanPhaseStep {

    /** Response matrix eMat = top-left nip×nip block of the inverse bordered matrix. */
    public final double[][] eMat;

    /** ∂M_A/∂μ_B, nc×nc. */
    public final double[][] dMdMu;

    /** ∂G_M/∂μ_B, length nc. */
    public final double[] dGdMu;

    /**
     * Internal-stationarity residual r[m] = Gx[m] + muMapped[m], length nip.
     * (The Lagrange multiplier η_s is eliminated by the bordered solve, so
     * it is not subtracted explicitly here.) At internal equilibrium for
     * the current μ this is constant within each sublattice, and the
     * bordered solve maps it to zero Δy.
     */
    public final double[] internalResidual;

    /**
     * Δy contribution from the CURRENT internal-stationarity residual at
     * fixed μ: dyResidual = −eMat·(Gx + muMapped), length nip.
     *
     * <p>This is the term whose absence made phase constitutions freeze at
     * their initial-estimate values: without it, Δy → 0 whenever Δμ → 0,
     * regardless of whether ∂L/∂y = 0 had actually been reached.
     */
    public final double[] dyResidual;

    /** ΔM_A produced by dyResidual: Σ_m c[A][m]·dyResidual[m], length nc. */
    public final double[] dMresidual;

    /** ΔG_M produced by dyResidual: Σ_m Gx[m]·dyResidual[m]. */
    public final double dGresidual;

    /** True if the bordered matrix was singular (no valid step for this phase this iteration). */
    public final boolean singular;

    private SundmanPhaseStep(double[][] eMat, double[][] dMdMu, double[] dGdMu,
                             double[] internalResidual, double[] dyResidual,
                             double[] dMresidual, double dGresidual, boolean singular) {
        this.eMat = eMat;
        this.dMdMu = dMdMu;
        this.dGdMu = dGdMu;
        this.internalResidual = internalResidual;
        this.dyResidual = dyResidual;
        this.dMresidual = dMresidual;
        this.dGresidual = dGresidual;
        this.singular = singular;
    }

    /**
     * Builds the per-phase bordered-Hessian reduction at the phase's
     * current y (phase.Gxx must already be populated via
     * {@link SundmanPhase#evaluate}).
     *
     * @param phase                    the phase (already evaluated at current y, T)
     * @param elementIndexOnSublattice constituent-to-element map, [s][i] -> element index or -1
     * @param mu                       current chemical potentials, length nc (needed for the
     *                                 internal-stationarity residual Gx + muMapped)
     */
    public static SundmanPhaseStep build(SundmanPhase phase, int[][] elementIndexOnSublattice, double[] mu) {
        int nip = phase.nip;
        int ns = phase.ns;
        int nc = phase.nc;
        int[] offs = phase.offsets();
        int[] ncSL = phase.constituentsPerSublattice();

        // Bordered matrix: (nip+ns) x (nip+ns)
        int dim = nip + ns;
        double[][] M = new double[dim][dim];
        for (int i = 0; i < nip; i++) {
            System.arraycopy(phase.Gxx[i], 0, M[i], 0, nip);
        }
        for (int s = 0; s < ns; s++) {
            int row = nip + s;
            for (int i = offs[s]; i < offs[s] + ncSL[s]; i++) {
                M[i][row] = 1.0;
                M[row][i] = 1.0;
            }
        }

        double[][] eMat = new double[nip][nip];
        boolean singular = false;
        try {
            Matrix matM = new Matrix(M);
            Matrix inv = matM.inverse();
            for (int i = 0; i < nip; i++) {
                for (int j = 0; j < nip; j++) {
                    eMat[i][j] = inv.get(i, j);
                }
            }
        } catch (Exception e) {
            singular = true;
            // eMat left as zero; caller must treat this as a failed step for this phase.
        }

        // dyDmu[m][B] = ∂y_m/∂μ_B = −Σ_{j maps to B} eMat[m][j].
        //
        // Sign: the bordered internal Newton system is
        //   Gxx·Δy + Bᵀ·Δη = −(Gx + muMapped)
        // so Δy = −eMat·(Gx + muMapped) and hence ∂y/∂(muMapped) = −eMat.
        // The minus is essential and must match deltaY() below: raising
        // μ_B makes constituent B less favourable in L = ... + Σ μ_A M_A,
        // pushing y away from B.
        double[][] dyDmu = new double[nip][nc];
        for (int s = 0; s < ns; s++) {
            for (int j = 0; j < ncSL[s]; j++) {
                if (j >= elementIndexOnSublattice[s].length) continue;
                int elJ = elementIndexOnSublattice[s][j];
                if (elJ < 0 || elJ >= nc) continue;
                int flatJ = offs[s] + j;
                for (int m = 0; m < nip; m++) {
                    dyDmu[m][elJ] -= eMat[m][flatJ];
                }
            }
        }

        // dMdMu[A][B] = sum_m c[A][m] * dyDmu[m][B], c[A][m] = a[s(m)] if m maps to A else 0
        double[] a = phase.stoichiometry();
        double[][] dMdMu = new double[nc][nc];
        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < ncSL[s]; i++) {
                if (i >= elementIndexOnSublattice[s].length) continue;
                int elA = elementIndexOnSublattice[s][i];
                if (elA < 0 || elA >= nc) continue;
                int m = offs[s] + i;
                for (int B = 0; B < nc; B++) {
                    dMdMu[elA][B] += a[s] * dyDmu[m][B];
                }
            }
        }

        // dGdMu[B] = sum_m Gx[m] * dyDmu[m][B]
        double[] dGdMu = new double[nc];
        for (int m = 0; m < nip; m++) {
            for (int B = 0; B < nc; B++) {
                dGdMu[B] += phase.Gx[m] * dyDmu[m][B];
            }
        }

        // ---- Internal-stationarity residual at the CURRENT mu ----
        // r[m] = Gx[m] + muMapped[m]  (dL/dy_m up to the eliminated eta_s)
        double[] muMapped = new double[nip];
        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < ncSL[s]; i++) {
                if (i >= elementIndexOnSublattice[s].length) continue;
                int el = elementIndexOnSublattice[s][i];
                if (el < 0 || el >= mu.length) continue;
                muMapped[offs[s] + i] = mu[el];
            }
        }
        double[] internalResidual = new double[nip];
        for (int m = 0; m < nip; m++) {
            internalResidual[m] = phase.Gx[m] + muMapped[m];
        }

        // dyResidual = -eMat * internalResidual  (the Newton step that
        // drives dL/dy to zero at fixed mu; the bordered solve
        // automatically projects out the per-sublattice eta components)
        double[] dyResidual = new double[nip];
        for (int m = 0; m < nip; m++) {
            double sum = 0.0;
            for (int j = 0; j < nip; j++) sum += eMat[m][j] * internalResidual[j];
            dyResidual[m] = -sum;
        }

        // Effect of dyResidual on M_A and G_M (needed for the reduced
        // system's RHS, since G and M move as y relaxes).
        double[] dMresidual = new double[nc];
        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < ncSL[s]; i++) {
                if (i >= elementIndexOnSublattice[s].length) continue;
                int elA = elementIndexOnSublattice[s][i];
                if (elA < 0 || elA >= nc) continue;
                dMresidual[elA] += a[s] * dyResidual[offs[s] + i];
            }
        }
        double dGresidual = 0.0;
        for (int m = 0; m < nip; m++) dGresidual += phase.Gx[m] * dyResidual[m];

        return new SundmanPhaseStep(eMat, dMdMu, dGdMu,
                internalResidual, dyResidual, dMresidual, dGresidual, singular);
    }

    /**
     * Total Newton step for the internal variables:
     * <pre>
     *   Δy = −eMat·(Gx + muMapped)  −  eMat·Δ(muMapped)
     *        \____ dyResidual ____/    \_ response to Δμ _/
     * </pre>
     * The first term drives the internal stationarity condition
     * ∂L/∂y = 0 to zero at the current μ; the second is the response to
     * the Newton update Δμ. Both are required: with only the second term
     * (the previous implementation) Δy vanishes whenever Δμ vanishes, so
     * the constitution would freeze at its initial-estimate value and the
     * converged phase compositions would depend on the initial estimate
     * (and hence on the overall composition) rather than on μ alone.
     */
    public double[] deltaY(double[] deltaMu, int[][] elementIndexOnSublattice,
                            int[] offsets, int[] constituentsPerSublattice) {
        int nip = eMat.length;
        int ns = offsets.length;
        double[] deltaMuMapped = new double[nip];
        for (int s = 0; s < ns; s++) {
            for (int i = 0; i < constituentsPerSublattice[s]; i++) {
                int el = elementIndexOnSublattice[s][i];
                if (el < 0 || el >= deltaMu.length) continue;
                deltaMuMapped[offsets[s] + i] = deltaMu[el];
            }
        }
        double[] dy = new double[nip];
        for (int m = 0; m < nip; m++) {
            double sum = 0.0;
            for (int j = 0; j < nip; j++) sum += eMat[m][j] * deltaMuMapped[j];
            dy[m] = dyResidual[m] - sum;
        }
        return dy;
    }
}
