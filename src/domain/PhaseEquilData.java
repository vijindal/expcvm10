package domain;

/**
 * Immutable value object returned by {@link PhaseModelPort#compute}.
 *
 * <p>Mirrors the Mathematica return tuple
 * {@code {GN, delnN, delyN, eListN, phaseName}} plus the intermediate
 * response coefficients (eMat, cG, cT, cP) needed by the equilibrium
 * matrix assembly in {@code EquilibriumSolver}.
 *
 * <p>For RK phases: deln == dely (internal = external parameters).
 * For CEF/CVM: dely has length nip (site fractions / cluster vars),
 * deln has length nc (mole fraction changes).
 */
public final class PhaseEquilData {

    /** Molar Gibbs energy G in J/mol. */
    public final double G;

    /** Internal parameter change vector, length nip. */
    public final double[] dely;

    /** Composition (mole fraction) change vector, length nc. */
    public final double[] deln;

    /** Current composition (mole fractions), length nc. */
    public final double[] x;

    /**
     * Inverted phase matrix (top-left block), nip × nip.
     * {@code eMat[i][j]} = cAB response coefficient.
     * Used by the equilibrium solver to build the Jacobian.
     */
    public final double[][] eMat;

    /** Composition response to G: cG[i] = -Σ eMat[i][k]·Gx[k]. Length nip. */
    public final double[] cG;

    /** Composition response to T: cT[i] = -Σ eMat[i][k]·GxT[k]. Length nip. */
    public final double[] cT;

    /** Composition response to P: cP[i] = -Σ eMat[i][k]·GxP[k]. Length nip. */
    public final double[] cP;

    /** Energy parameter list for diagnostics. */
    public final double[] eList;

    public PhaseEquilData(double G,
                          double[] dely, double[] deln, double[] x,
                          double[][] eMat,
                          double[] cG, double[] cT, double[] cP,
                          double[] eList) {
        this.G     = G;
        this.dely  = dely;
        this.deln  = deln;
        this.x     = x;
        this.eMat  = eMat;
        this.cG    = cG;
        this.cT    = cT;
        this.cP    = cP;
        this.eList = eList;
    }
}
