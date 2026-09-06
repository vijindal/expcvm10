package calc.equil;

import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;

/**
 * Mutable per-phase state during equilibrium iteration.
 *
 * Notation follows Sundman et al. CALPHAD 75 (2021):
 *   ℵ^α  = amount (moles of formula units)
 *   y    = site fractions (internal variables)
 *   x    = mole fractions
 *   mA   = M^α_A = Σ_s a[s]*y[s][A] (moles of A per formula unit), the
 *          true unnormalized per-formula-unit count (M2 Step 3).
 *   G    = G^α_M (Gibbs energy per formula unit)
 *   γ    = driving force = G^α_M + Σ_A μ_A M^α_A  (Sundman Eq.7 convention,
 *          the same convention EquilibriumSolver.assembleEquilibriumMatrix
 *          uses for its Gibbs-Duhem rows)
 *          = 0 at equilibrium for stable phases
 *          &lt; 0 for metastable phases
 */
public class PhaseRecord {

    /** The thermodynamic model for this phase. */
    public final GibbsEnergyModel model;

    /** Phase amount ℵ^α (moles of formula units). */
    public double amount;

    /** Site fractions y^α_{si} (internal variables), length nip. */
    public double[] y;

    /** Mole fractions x^α_A, length nc. */
    public double[] x;

    /**
     * M^α_A = Σ_s a[s]*y[s][A], moles of component A per formula unit.
     * Used in Gibbs-Duhem (Eq.7) and mass balance.
     * For RK phases (nfu=1): mA == x.
     */
    public double[] mA;

    /** G^α_M, Gibbs energy per mole formula unit. */
    public double G;

    /**
     * Driving force γ^φ = G^α_M + Σ_A μ_A M^α_A.
     * From Eq.6: positive γ means phase wants to become stable.
     * At equilibrium γ=0 for stable phases.
     */
    public double drivingForce;

    /** True if this phase is in the current stable set. */
    public boolean stable;

    /** Cached result from last compute() call. */
    public PhaseEquilData lastCompute;

    public PhaseRecord(GibbsEnergyModel model,
                       double[] x,
                       double amount,
                       boolean stable) {
        this.model  = model;
        this.x      = x != null ? x.clone() : new double[model.numComponents()];
        this.mA     = this.x.clone();  // initialise mA = x (correct for RK)
        this.amount = amount;
        this.stable = stable;
        int nip = model.numInternalParams();
        this.y  = model.getInitialInternalVars(this.x);
        this.G  = 0.0;
        this.drivingForce = 0.0;
    }

    /**
     * Delegates to model.compute() and caches result.
     * Updates G, x, mA, y from the returned PhaseEquilData.
     */
    public void updateFromModel(double T, double P,
                                double deltaT, double deltaP,
                                double[] mu) {
        lastCompute = model.compute(T, P, y, deltaT, deltaP, mu);
        this.G  = lastCompute.G;
        this.x  = lastCompute.x;
        this.mA = lastCompute.mA;
        // Update y with Newton step (damped externally by solver)
        // dely is stored in lastCompute for the solver to apply
    }

    /**
     * Update mole fractions from current internal parameters.
     * Must be called after modifying y[].
     */
    public void updateComposition() {
        this.x = model.compositionFromInternal(y);
    }

    /**
     * γ^α = G^α_M + Σ_A μ_A M^α_A  (Sundman Eq.7 convention). At
     * equilibrium γ=0 for stable phases; a positive γ indicates the phase
     * wants to enter the stable set.
     *
     * <p>Uses the current per-formula-unit G and the unnormalized
     * mA=M^α_A (M2 Step 3), matching exactly the convention
     * EquilibriumSolver.assembleEquilibriumMatrix uses for its
     * Gibbs-Duhem rows (γ = G + Σμ·M, RHS = -γ). Prior to this fix this
     * method divided G by nfu and subtracted Σμ·mA, a formula that was
     * only correct when mA was the normalized mole fraction x (pre-M2
     * Step 3); left unfixed, it produced a residual on a different scale
     * and sign than the one the Newton solver actually targets, causing
     * spurious large "driving force" values even when a phase was at (or
     * near) its true Gibbs-Duhem equilibrium (see M2 Step 8 diagnostic).</p>
     */
    public void computeDrivingForce(double[] mu) {
        double sum = G;
        for (int ic = 0; ic < mu.length && ic < mA.length; ic++)
            sum += mu[ic] * mA[ic];
        this.drivingForce = sum;
    }

    public String phaseName() { return model.phaseName(); }
    public String modelType() { return model.modelType(); }
}
