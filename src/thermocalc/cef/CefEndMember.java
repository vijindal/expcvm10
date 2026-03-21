package thermocalc.cef;

/**
 * End-member energy for one (sublattice-1 species i, sublattice-2 species j) pair
 * in a CEF (Compound Energy Formalism) phase.
 *
 * <h2>Form</h2>
 * For the C15 Laves phase with stoichiometry (A,B)₂(A,B)₁:
 * <pre>
 *   G[i][j](T) = a₁·GHSER_i(T) + a₂·GHSER_j(T) + ΔE_ij(T)
 * </pre>
 * where {@code a₁=2, a₂=1} for C15, and ΔE_ij = {@code deltaA + deltaB·T}.
 *
 * <h2>Mathematica correspondence</h2>
 * <pre>
 *   G11N = 2*GHSERNB + GHSERNB + 15000  → g0[0][0], ghser_i=NB, ghser_j=NB, deltaA=15000, deltaB=0
 *   G12N = 2*GHSERNB + GHSERV  + 45000  → g0[0][1], ghser_i=NB, ghser_j=V,  deltaA=45000, deltaB=0
 *   G23N = 2*GHSERV  + GHSERZR - 10796.71 + 3.8144*T → deltaA=-10796.71, deltaB=3.8144
 * </pre>
 *
 * <h2>Storage</h2>
 * Both GHSER references are stored as {@link thermocalc.rk.RkGibbs.G0Function} lambdas
 * so they evaluate from the unary layer at runtime.
 */
public final class CefEndMember {

    /** 0-based index of the species occupying sublattice 1 (the majority sublattice). */
    public final int idxI;

    /** 0-based index of the species occupying sublattice 2. */
    public final int idxJ;

    /** Stoichiometric coefficient of sublattice 1 (a₁ = 2 for C15). */
    private final double a1;

    /** Stoichiometric coefficient of sublattice 2 (a₂ = 1 for C15). */
    private final double a2;

    /** GHSER reference energy function for species i (sublattice 1). */
    private final thermocalc.rk.RkGibbs.G0Function ghserI;

    /** GHSER reference energy function for species j (sublattice 2). */
    private final thermocalc.rk.RkGibbs.G0Function ghserJ;

    /** Temperature-independent part of the excess end-member energy ΔE_ij. */
    private final double deltaA;

    /** Temperature-linear part of the excess end-member energy (coefficient of T). */
    private final double deltaB;

    /**
     * Constructs a C15 end-member energy entry.
     *
     * @param idxI    0-based species index for sublattice 1
     * @param idxJ    0-based species index for sublattice 2
     * @param a1      stoichiometric coefficient for sublattice 1 (2 for C15)
     * @param a2      stoichiometric coefficient for sublattice 2 (1 for C15)
     * @param ghserI  GHSER function for species i
     * @param ghserJ  GHSER function for species j
     * @param deltaA  T-independent part of ΔE_ij  (J/mol)
     * @param deltaB  T-linear part of ΔE_ij (J/(mol·K))
     */
    public CefEndMember(int idxI, int idxJ, double a1, double a2,
                        thermocalc.rk.RkGibbs.G0Function ghserI,
                        thermocalc.rk.RkGibbs.G0Function ghserJ,
                        double deltaA, double deltaB) {
        this.idxI   = idxI;
        this.idxJ   = idxJ;
        this.a1     = a1;
        this.a2     = a2;
        this.ghserI = ghserI;
        this.ghserJ = ghserJ;
        this.deltaA = deltaA;
        this.deltaB = deltaB;
    }

    /**
     * Evaluates G[i][j](T) = a₁·GHSER_i(T) + a₂·GHSER_j(T) + deltaA + deltaB·T.
     *
     * @param T  temperature in Kelvin
     * @return   end-member Gibbs energy in J/mol
     */
    public double G(double T) {
        return a1 * ghserI.G0(T) + a2 * ghserJ.G0(T) + deltaA + deltaB * T;
    }

    /**
     * Evaluates dG[i][j]/dT = a₁·dGHSER_i/dT + a₂·dGHSER_j/dT + deltaB.
     *
     * @param T  temperature in Kelvin
     * @return   temperature derivative in J/(mol·K)
     */
    public double dGdT(double T) {
        return a1 * ghserI.dG0dT(T) + a2 * ghserJ.dG0dT(T) + deltaB;
    }

    /** Returns the stoichiometric coefficient for sublattice 1. */
    public double a1() { return a1; }

    /** Returns the stoichiometric coefficient for sublattice 2. */
    public double a2() { return a2; }
}
