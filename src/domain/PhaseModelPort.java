package domain;

/**
 * Port interface abstracting over RK, CEF, and CVM phase models.
 *
 * <p>Mirrors the Mathematica {@code delxG} dispatcher and helper functions
 * ({@code getInitValues}, {@code updateComp}, {@code isValidParams}).
 *
 * <p>Each phase model has internal parameters {@code y[]} whose length and meaning
 * depend on the model type:
 * <ul>
 *   <li><b>RK</b>: y = x (mole fractions), length nc</li>
 *   <li><b>CEF</b>: y = site fractions, length nip = ns*nc (e.g. 2*nc for C15)</li>
 *   <li><b>CVM</b>: y = {cluster variables, compositions}, length nip</li>
 * </ul>
 */
public interface PhaseModelPort {

    /** Phase name, e.g. "BCC_A2", "LIQUID", "LAVES_C15". */
    String phaseName();

    /** Model type: "RK", "CEF", or "CVM". */
    String modelType();

    /** Number of independent components. */
    int numComponents();

    /**
     * Number of internal parameters (length of y[]).
     * RK: nc, CEF: ns*nc, CVM: nip.
     */
    int numInternalParams();

    /**
     * Number of formula units per mole of atoms.
     * From Mathematica {@code phaseNameList[[ip]][[3]]}.
     * RK: 1.0, CEF: e.g. 3 for A₂B, CVM: depends on cluster.
     */
    double nfu();

    // ------------------------------------------------------------------
    // G evaluation (for grid minimization / driving force)
    // ------------------------------------------------------------------

    /**
     * Evaluate molar Gibbs energy at composition x and temperature T.
     *
     * @param x composition (mole fractions), length numComponents
     * @param T temperature in Kelvin
     * @return G in J/mol
     */
    double evaluateG(double[] x, double T);

    /**
     * Gradient dG/dx at composition x and temperature T.
     *
     * @param x composition, length numComponents
     * @param T temperature in Kelvin
     * @return dG/dx array, length numComponents
     */
    double[] gradient(double[] x, double T);

    /**
     * Hessian d²G/dxdx at composition x and temperature T.
     *
     * @param x composition, length numComponents
     * @param T temperature in Kelvin
     * @return d²G/dxdx matrix, numComponents × numComponents
     */
    double[][] hessian(double[] x, double T);

    // ------------------------------------------------------------------
    // Internal variable management (mirrors Mathematica helpers)
    // ------------------------------------------------------------------

    /**
     * Map internal parameters y[] to mole fractions x[].
     * Mirrors Mathematica {@code updateComp}.
     * <ul>
     *   <li>RK: returns y directly (x = y)</li>
     *   <li>CEF/C15: x[i] = (a₁·y[i] + a₂·y[nc+i]) / (a₁+a₂)</li>
     *   <li>CVM: x[i] = y[ncf + i] (last numComponents entries)</li>
     * </ul>
     *
     * @param y internal parameters, length numInternalParams
     * @return mole fractions, length numComponents
     */
    double[] compositionFromInternal(double[] y);

    /**
     * Generate initial guess for internal parameters from composition.
     * Mirrors Mathematica {@code getInitValues}.
     *
     * @param x composition (mole fractions), length numComponents
     * @return initial y[], length numInternalParams
     */
    double[] getInitialInternalVars(double[] x);

    /**
     * Check whether internal parameters are physically valid.
     * Mirrors Mathematica {@code isValidParams}.
     * <ul>
     *   <li>RK: all y[i] &ge; 0</li>
     *   <li>CEF: each sublattice sums to 1, all fractions in [0,1]</li>
     *   <li>CVM: all cluster variables positive</li>
     * </ul>
     *
     * @param y internal parameters, length numInternalParams
     * @return true if y represents a valid physical state
     */
    boolean isValid(double[] y);

    // ------------------------------------------------------------------
    // Full per-phase computation (mirrors delGsol / delxGC15 / delxGCVM)
    // ------------------------------------------------------------------

    /**
     * Full per-phase equilibrium computation.
     * Returns G, composition response coefficients, and change vectors.
     *
     * <p>This is the core method called by the equilibrium solver at each
     * Newton iteration. It mirrors:
     * <ul>
     *   <li>{@code delGsol[TN, PN, xN, yN, varList]} for RK</li>
     *   <li>{@code delxGC15[...]} for CEF</li>
     *   <li>{@code delxGCVM[...]} for CVM</li>
     * </ul>
     *
     * @param T       temperature in Kelvin
     * @param P       pressure in Pa
     * @param y       internal parameters, length numInternalParams
     * @param deltaT  temperature increment from outer loop
     * @param deltaP  pressure increment from outer loop
     * @param mu      chemical potentials, length numComponents
     * @return equilibrium data for this phase
     */
    PhaseEquilData compute(double T, double P, double[] y,
                           double deltaT, double deltaP, double[] mu);
}
