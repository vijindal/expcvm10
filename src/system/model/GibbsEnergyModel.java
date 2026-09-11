package system.model;

import java.util.ArrayList;

/**
 * Abstract contract for Gibbs energy models, in Sundman's site-fraction
 * (y-facing) formulation only.
 *
 * <p>This codebase standardizes on Sundman's CEF/Algorithm-A notation
 * (2015 Comput. Mater. Sci. 101; 2021 Calphad 75): the phase's Gibbs
 * energy and its derivatives are evaluated directly in site-fraction
 * space, {@code G(T,P,y)}, {@code dG_dy(T,P,y)}, {@code d2G_dy2(T,P,y)},
 * etc. -- Sundman's {@code G_M(T,P,Y)}. The earlier composition-facing
 * (x-facing) surface
 * ({@code evaluateG(x,T)}/{@code gradient(x,T)}/{@code hessian(x,T)} and
 * related G0/derivative-cache machinery, used by the retired
 * mole-fraction {@code EquilibriumSolver}) has been removed; the sole
 * production solver, {@link system.model.cef.CefGibbs}'s consumer
 * {@code EquilibriumSolverV2}, calls only this surface.
 *
 * <h2>Structure</h2>
 * <ol>
 *   <li><b>Phase Identity</b> (abstract) — phase name, model type, elements
 *   <li><b>State Variables</b> (concrete) — T, P, y storage &amp; access
 *   <li><b>Site-Fraction Thermodynamics</b> (abstract) — G, dG/dy, d2G/dy2,
 *       dG/dT, moles, and the sublattice structure accessors
 *   <li><b>Internal Variables</b> (abstract) — composition &lt;-&gt; site
 *       fraction mapping
 *   <li><b>Equilibrium Matrix</b> (concrete) — solver support
 * </ol>
 */
public abstract class GibbsEnergyModel {

    // ══════════════════════════════════════════════════════════════════
    // State Variables (Concrete Storage)
    // ══════════════════════════════════════════════════════════════════
    //
    // T, P and y are also passed explicitly to every method in the
    // Site-Fraction Thermodynamics block below (Sundman's G_M(T,P,Y)) --
    // these fields are scratch storage for callers that want to hold a
    // "current state" (e.g. printPhaseInfo()), never read implicitly by
    // G/dG_dy/d2G_dy2/etc. themselves.

    protected double T;
    protected double P;
    protected double[] y;

    // ══════════════════════════════════════════════════════════════════
    // Phase Identity (Abstract - Each Model Provides)
    // ══════════════════════════════════════════════════════════════════

    /** Phase name, e.g. "BCC_A2", "LIQUID", "C15". */
    public abstract String phaseName();

    /** Model type: "CEF" or "CVM". */
    public abstract String modelType();

    /** Element symbols in this phase (e.g. ["NB", "TI"]). */
    public abstract ArrayList<String> elementNames();

    /** Component symbols/labels. */
    public abstract String[] componentList();

    /** Number of independent components. */
    public abstract int numComponents();

    /** Number of internal parameters (site fractions for CEF, cluster vars for CVM). */
    public abstract int numInternalParams();

    /** Number of total parameters (internal + constraints). */
    public abstract int numTotalParams();

    /** Number of formula units per mole of atoms. CEF: e.g. 3 for A₂B. */
    public abstract double nfu();

    // ══════════════════════════════════════════════════════════════════
    // State Variables (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    public void setTemperature(double T)    { this.T = T; }
    public double getTemperature()          { return T; }

    public void setPressure(double P)       { this.P = P; }
    public double getPressure()             { return P; }

    public void setInternalVars(double[] y) { this.y = y.clone(); }
    public double[] getInternalVars()       { return y.clone(); }

    // ══════════════════════════════════════════════════════════════════
    // Internal Variable Management (Abstract - Each Model Implements)
    // ══════════════════════════════════════════════════════════════════

    public abstract double[] getInitialInternalVars(double[] x);
    public abstract double[] compositionFromInternal(double[] y);
    public abstract boolean isValid(double[] y);

    // ══════════════════════════════════════════════════════════════════
    // Site-Fraction Thermodynamics (Abstract - Each Model Implements)
    //
    // The direct, stateless, site-fraction-space contract used by the
    // Sundman-algorithm equilibrium solver (EquilibriumSolverV2) -- see
    // Sundman 2015 §2.2-2.3 / 2021 §2.2, Eq. (4): G_M(T,P,y). T, P and y
    // are always explicit arguments here, never read from the scratch
    // fields above -- this mirrors pycalphad's explicit v.T/v.P symbolic
    // dependence and avoids the "silently reinterpreted via instance
    // state" hazard the retired x-facing evaluateG(x,T) had.
    // ══════════════════════════════════════════════════════════════════

    /**
     * Molar Gibbs energy G(T,P,Y), evaluated directly in site-fraction
     * space.
     *
     * @param T temperature in Kelvin
     * @param P pressure in Pa
     * @param y site-fraction / internal-variable vector, length
     *          {@link #numSiteVars()}
     * @return G in J/mol
     */
    public abstract double G(double T, double P, double[] y);

    /**
     * Gradient dG/dy at fixed T, P, in site-fraction space.
     *
     * @param T temperature in Kelvin
     * @param P pressure in Pa
     * @param y site-fraction vector
     * @return dG/dy, length {@link #numSiteVars()}
     */
    public abstract double[] dG_dy(double T, double P, double[] y);

    /**
     * Hessian d2G/dy2 at fixed T, P, in site-fraction space.
     *
     * @param T temperature in Kelvin
     * @param P pressure in Pa
     * @param y site-fraction vector
     * @return d2G/dy2, {@link #numSiteVars()} x {@link #numSiteVars()}
     */
    public abstract double[][] d2G_dy2(double T, double P, double[] y);

    /**
     * Pressure derivative dG/dP at fixed T, y (Sundman's molar volume
     * contribution, e.g. TDB {@code V0}/{@code VA} parameters).
     *
     * @param T temperature in Kelvin
     * @param P pressure in Pa
     * @param y site-fraction vector
     * @return dG/dP in J/(mol·Pa)
     */
    public abstract double dG_dP(double T, double P, double[] y);

    /**
     * Mixed second derivative d2G/dydP at fixed T, y.
     *
     * @param T temperature in Kelvin
     * @param P pressure in Pa
     * @param y site-fraction vector
     * @return d2G/dydP, length {@link #numSiteVars()}
     */
    public abstract double[] d2G_dydP(double T, double P, double[] y);

    /**
     * Element content M_A(Y): moles of each system component per formula
     * unit, as a function of site fractions.
     *
     * @param y site-fraction vector
     * @return M_A per component, length equal to the number of system
     *         components
     */
    public abstract double[] moles(double[] y);

    /**
     * Jacobian dM_A/dY_i. M_A is linear in Y, so this is constant for a
     * given phase model.
     *
     * @return dM_A/dY, sized [numComponents][{@link #numSiteVars()}]
     */
    public abstract double[][] dMoles_dy();

    /**
     * Site ratios a[s] for each sublattice (moles of sites per formula
     * unit, per sublattice).
     */
    public abstract double[] siteRatios();

    /** Number of sublattices ns. */
    public abstract int numSublattices();

    /** Number of site-fraction variables (length of y). */
    public abstract int numSiteVars();

    /** Offset of each sublattice's constituent block within the flattened
     *  site-fraction vector y. */
    public abstract int[] offsets();

    /** Number of constituents on each sublattice. */
    public abstract int[] constituentsPerSublattice();

    // ══════════════════════════════════════════════════════════════════
    // Equilibrium Matrix (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    public void setEquilibriumMatrix(double[][] emat) { this.eMat = cloneMatrix(emat); }
    public double[][] getEquilibriumMatrix()          { return cloneMatrix(eMat); }

    public void setConstraintGradients(double[] cg)    { this.cG = cg != null ? cg.clone() : null; }
    public double[] getConstraintGradients()           { return cG != null ? cG.clone() : null; }

    public void setConstraintTempDeriv(double[] ct)    { this.cT = ct != null ? ct.clone() : null; }
    public double[] getConstraintTempDeriv()           { return cT != null ? cT.clone() : null; }

    public void setConstraintPressDeriv(double[] cp)   { this.cP = cp != null ? cp.clone() : null; }
    public double[] getConstraintPressDeriv()          { return cP != null ? cP.clone() : null; }

    public void setABMatrix(double[][] cab)            { this.cAB = cloneMatrix(cab); }
    public double[][] getABMatrix()                    { return cloneMatrix(cAB); }

    protected double[][] eMat;
    protected double[] cG;
    protected double[] cT;
    protected double[] cP;
    protected double[][] cAB;

    // ══════════════════════════════════════════════════════════════════
    // Full Per-Phase Computation (Abstract - Each Model Implements)
    // ══════════════════════════════════════════════════════════════════

    public abstract PhaseEquilData compute(double T, double P, double[] y,
                                          double deltaT, double deltaP, double[] mu);

    // ══════════════════════════════════════════════════════════════════
    // Output / Debugging (Abstract - Each Model Implements)
    // ══════════════════════════════════════════════════════════════════

    public abstract void printPhaseInfo();
    public abstract void printDerivatives();

    // ══════════════════════════════════════════════════════════════════
    // Private Helpers
    // ══════════════════════════════════════════════════════════════════

    protected static double[][] cloneMatrix(double[][] mat) {
        if (mat == null) return null;
        double[][] clone = new double[mat.length][];
        for (int i = 0; i < mat.length; i++)
            clone[i] = mat[i].clone();
        return clone;
    }
}
