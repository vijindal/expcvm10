package system.model;

/**
 * Immutable data returned by GibbsEnergyModel.compute() for one phase.
 *
 * Notation follows Sundman et al. CALPHAD 75 (2021) 102330:
 *   G^α_M   — Gibbs energy per mole formula unit
 *   M^α_A   — moles of component A per formula unit = Σ_s a[s]*y[s][A]
 *   y       — site fractions (internal variables), length nip
 *   eMat    — nip×nip response matrix = top-left block of inverse(PhaseMatrix)
 *   eMatNC  — nc×nc projected response = Σ_m Σ_j ny[A][m]*eMat[m][j]*ny[B][j]
 *             used by EquilibriumSolver for Jacobian assembly
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
     * = ∂M^α_A/∂μ_B, size nc×nc.
     * Used directly by EquilibriumSolver Jacobian assembly.
     */
    public final double[][] eMatNC;

    /** cG[m] = -Σ_j eMat[m][j]*Gx[j], length nip. */
    public final double[] cG;

    /** cT[m] = -Σ_j eMat[m][j]*GxT[j], length nip. */
    public final double[] cT;

    /** cP[m] = -Σ_j eMat[m][j]*GxP[j], length nip (zero if no P dependence). */
    public final double[] cP;

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
        this.eList  = eList;
    }
}
