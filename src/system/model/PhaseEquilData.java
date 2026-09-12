package system.model;

/**
 * Immutable data returned by PhaseMatrixAssembler.compute() for one phase.
 *
 * Notation follows Sundman et al. CALPHAD 75 (2021) 102330:
 *   G^α_M   — Gibbs energy per mole formula unit
 *   M^α_A   — moles of component A per formula unit = Σ_s a[s]*y[s][A]
 *   y       — site fractions (internal variables), length nip
 *   eMat    — nip×nip response matrix = top-left block of inverse(PhaseMatrix)
 *   eMatNC  — nc×nc projected response = Σ_m Σ_j ny[A][m]*eMat[m][j]*ny[B][j]
 *             a phase's contribution to a Jacobian assembled by a solver
 *   cG      — ∂y/∂G   = -eMat * Gx,  length nip
 *   cT      — ∂y/∂T   = -eMat * GxT, length nip
 *   dely    — Δy = cG + cT*ΔT + Σ_A cN[A]*μ_A
 *   deln    — ΔM^α_A = Σ_s a[s]*Δy[offset[s]+A], length nc
 *   mA      — current M^α_A values, length nc
 *   x       — mole fractions, length nc
 */
public final class PhaseEquilData {

    /** Gibbs energy per mole formula unit (J/mol FU). */
    public final double G;

    /** Site fraction changes Δy, length nip. */
    public final double[] dely;

    /** Changes in moles per FU: ΔM^α_A = Σ_s a[s]*Δy[s][A], length nc. */
    public final double[] deln;

    /** Mole fractions x^α_A, length nc. */
    public final double[] x;

    /** M^α_A = Σ_s a[s]*y[s][A], moles of A per formula unit, length nc. */
    public final double[] mA;

    /** Response matrix eMat[m][j], nip×nip, top-left block of inv(PhaseMatrix). */
    public final double[][] eMat;

    /**
     * Projected response matrix eMatNC[A][B] = Σ_m Σ_j ny[A][m]*eMat[m][j]*ny[B][j]
     * = ∂M^α_A/∂μ_B, size nc×nc. A phase's contribution to a solver's
     * multiphase Jacobian assembly.
     */
    public final double[][] eMatNC;

    /** cG[m] = -Σ_j eMat[m][j]*Gx[j], length nip. */
    public final double[] cG;

    /** cT[m] = -Σ_j eMat[m][j]*GxT[j], length nip. */
    public final double[] cT;

    /** cP[m] = -Σ_j eMat[m][j]*GxP[j], length nip (zero if no P dependence). */
    public final double[] cP;

    /**
     * dM_dT[A] = Σ_m dM_A/dy[m] * cT[m], length nc -- the phase's pure
     * temperature sensitivity of M_A (moles of component A per formula
     * unit), independent of {@code deltaT}/chemical potentials. This is
     * {@code deln}'s T-only analogue: {@code deln} uses the FULL {@code
     * dely} (which folds in {@code deltaT} and {@code mu}), whereas
     * {@code dM_dT} isolates just the {@code cT} contribution, needed to
     * release T as a Newton unknown in the global equilibrium matrix
     * (Sundman Algorithm C2's invariant-node search, {@link
     * calc.equil.EquilibriumSolverV2#solveBoundary}) -- see {@code
     * GlobalEquilibriumMatrixAssembler.convertToFixedPhaseAmountSystemReleasingT}.
     */
    public final double[] dM_dT;

    /**
     * dG/dT at fixed y -- the phase's own Gibbs-energy T-sensitivity
     * (from {@link system.model.GibbsEnergyModel#dG_dT}), needed as the
     * ΔT coefficient in this phase's OWN phase-equilibrium row when T is
     * released as a Newton unknown: {@code M_A*lambda_A - dG_dT*deltaT = G}.
     */
    public final double dG_dT;

    /** Diagnostic energy list. */
    public final double[] eList;

    public PhaseEquilData(double G,
                          double[] dely,
                          double[] deln,
                          double[] x,
                          double[] mA,
                          double[][] eMat,
                          double[][] eMatNC,
                          double[] cG,
                          double[] cT,
                          double[] cP,
                          double[] dM_dT,
                          double dG_dT,
                          double[] eList) {
        this.G      = G;
        this.dely   = dely;
        this.deln   = deln;
        this.x      = x;
        this.mA     = mA;
        this.eMat   = eMat;
        this.eMatNC = eMatNC;
        this.cG     = cG;
        this.cT     = cT;
        this.cP     = cP;
        this.dM_dT  = dM_dT;
        this.dG_dT  = dG_dT;
        this.eList  = eList;
    }
}
