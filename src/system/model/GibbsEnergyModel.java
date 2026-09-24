package system.model;

import java.util.ArrayList;
import system.model.InternalConstraintSet;

/**
 * Abstract contract for Gibbs energy models, in Sundman's internal-variable
 * (y-facing) formulation only.
 *
 * <p>This is a model-agnostic contract, not a CEF-specific one: {@code y} is
 * whatever internal-variable vector a given model minimizes over (site
 * fractions for the Compound Energy Formalism, cluster/point probabilities
 * for a future Cluster Variation Method model, etc.). Any model implementing
 * this class -- {@link system.model.cef.CefGibbs} (CEF) today, a CVM model
 * later -- plugs into the same Sundman Algorithm-A solver
 * ({@code EquilibriumSolverV2}) as long as it honors this contract: the
 * phase's Gibbs energy and its derivatives evaluated directly in
 * internal-variable space, {@code G(T,P,y)}, {@code dG_dy(T,P,y)},
 * {@code d2G_dy2(T,P,y)}, etc. -- Sundman's {@code G_M(T,P,Y)} (2015 Comput.
 * Mater. Sci. 101; 2021 Calphad 75). The earlier composition-facing
 * (x-facing) surface
 * ({@code evaluateG(x,T)}/{@code gradient(x,T)}/{@code hessian(x,T)} and
 * related G0/derivative-cache machinery, used by the retired
 * mole-fraction {@code EquilibriumSolver}) has been removed;
 * {@code EquilibriumSolverV2} calls only this surface, on whichever
 * concrete model was constructed for the phase.
 *
 * <h2>Structure</h2>
 * <ol>
 *   <li><b>Phase Identity</b> (abstract) — phase name, model type, elements
 *   <li><b>State Variables</b> (concrete) — T, P, y storage &amp; access
 *   <li><b>Site-Fraction Thermodynamics</b> (abstract) — G, dG/dy, d2G/dy2,
 *       dG/dT, d2G/dydT, dG/dP, d2G/dydP, moles, dMoles/dy, and the
 *       sublattice structure accessors
 *   <li><b>Internal Variables</b> (abstract) — composition &lt;-&gt; site
 *       fraction mapping
 * </ol>
 *
 * <p>Assembling and inverting the Newton phase matrix from these values is
 * the calculation layer's job, not the model's -- matching pycalphad, whose
 * model-layer {@code Model}/{@code PhaseRecord} only ever compute and
 * return raw value/gradient/Hessian; all Newton-iteration state
 * ({@code SystemState}, the assembled/inverted matrix) lives in
 * {@code pycalphad.core}. This class therefore has no {@code compute()}
 * method of its own -- {@code calc.equil.PhaseMatrixAssembler} borders,
 * inverts, and projects the phase matrix for any model purely through this
 * abstract surface, so a new model gets that machinery for free instead of
 * reimplementing it.
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

    /** Number of total parameters (internal + constraints). */
    public abstract int numTotalParams();

    /**
     * Nominal formula-unit size: the raw sum of site ratios, CONSTITUTION-
     * INDEPENDENT (fixed for a given phase model, computed without any
     * y). For a phase with a vacancy sublattice (e.g. BCC_A2's
     * (V,Zr)1(Va)3) this counts the vacancy sites too (nfu()=4), even
     * though they hold no atoms.
     *
     * <p><b>Never use this for per-atom G normalization</b> -- use
     * {@link #totalMoles(double[])} instead, which is the constitution-
     * dependent REAL atom count (Sundman's M^alpha, Eq. 6) and excludes
     * vacancies. This distinction has caused real bugs when the two were
     * confused; see {@link #totalMoles(double[])}'s javadoc.
     */
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

    /**
     * Linear equality constraints on internal variables: C·y = b.
     *
     * @return constraint set (e.g., per-sublattice sum constraints for CEF,
     *         composition-normalization for CVM)
     */
    public abstract InternalConstraintSet getConstraintSet();

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
     * Temperature derivative dG/dT at fixed P, y.
     *
     * @param T temperature in Kelvin
     * @param P pressure in Pa
     * @param y site-fraction vector
     * @return dG/dT in J/(mol·K)
     */
    public abstract double dG_dT(double T, double P, double[] y);

    /**
     * Mixed second derivative d2G/dydT at fixed P, y.
     *
     * @param T temperature in Kelvin
     * @param P pressure in Pa
     * @param y site-fraction vector
     * @return d2G/dydT, length {@link #numSiteVars()}
     */
    public abstract double[] d2G_dydT(double T, double P, double[] y);

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
     * Total moles of REAL elements per formula unit at constitution y --
     * Sundman's M^alpha, {@code M = sum_A M_A} (2015 Comput. Mater. Sci.
     * 101, Eq. (3)/(6)): "Provided we have only the elements as
     * constituents we can use M^alpha = sum_s a_s^alpha (1 - y_Va,s^alpha)"
     * -- i.e. vacancy fractions are excluded because Va has no
     * stoichiometric coefficient for any real element A in Eq. (3), so
     * summing {@link #moles(double[])} over every component already
     * excludes them automatically. This is algebraically identical to
     * pycalphad's own {@code _site_ratio_normalization} (model.py), used
     * there for exactly the same purpose (per-atom energy normalization).
     *
     * <p><b>This is NOT {@link #nfu()}.</b> {@code nfu()} is a
     * constitution-INDEPENDENT nominal formula-unit size (the raw sum of
     * site ratios, including any all-vacancy sublattice); this method is
     * the constitution-DEPENDENT real atom count at a SPECIFIC y, which
     * can be smaller than {@code nfu()} whenever any sublattice is
     * partially or fully vacant (e.g. BCC_A2's (V,Zr)1(Va)3 has
     * {@code nfu()=4} always, but {@code totalMoles(y)=1} at every
     * physically valid y, since the 3-site Va sublattice holds no atoms).
     * Using {@code nfu()} where this method is needed silently divides a
     * per-formula-unit G by the wrong constant for any phase with a
     * vacancy sublattice -- this has caused real, hard-to-diagnose bugs
     * (see {@code GridMinimizer}'s hull-search G/atom normalization) and
     * every per-atom normalization in this codebase must use this method,
     * never {@code nfu()}.
     *
     * @param y site-fraction vector
     * @return total real moles of elements per formula unit at y (never
     *         larger than {@link #nfu()}, and strictly less whenever any
     *         sublattice is partially vacant at y)
     */
    public double totalMoles(double[] y) {
        double total = 0.0;
        for (double m : moles(y)) {
            total += m;
        }
        return total;
    }

    /**
     * Jacobian dM_A/dY_i. M_A is linear in Y, so this is constant for a
     * given phase model.
     *
     * @return dM_A/dY, sized [numComponents][{@link #numSiteVars()}]
     */
    public abstract double[][] dMoles_dy();

    // The three accessors below expose how y is partitioned into blocks --
    // CEF's sublattices, each a contiguous run of constituents that must
    // sum to 1 -- so a solver can border the phase-matrix Hessian with one
    // Lagrange row/column per block (Sundman 2015 Eq. 40) without knowing
    // the model's internals. Named after CEF's own vocabulary today since
    // it is the only implementation; a future non-sublattice model (e.g.
    // CVM, whose internal variables are cluster/point probabilities)
    // should map its own block structure onto these same methods rather
    // than adding a parallel set -- revisit the naming once that mapping
    // exists to show what generalization actually fits both models.
    //
    // (Per-sublattice site ratios a[s] are NOT part of this contract: the
    // only quantity a solver needs from them is their sum, already exposed
    // as nfu(), and the per-block weighting a solver needs for M_A is
    // already carried by dMoles_dy() above. A model that needs a[s]
    // individually for its own G(T,P,y) evaluation is free to keep it as
    // a concrete, non-abstract accessor, as CefGibbs does.)

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
    // Output / Debugging (Abstract - Each Model Implements)
    // ══════════════════════════════════════════════════════════════════

    public abstract void printPhaseInfo();
}
