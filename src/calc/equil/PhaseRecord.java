package calc.equil;

import system.model.PhaseEquilData;
import system.model.GibbsEnergyModel;

/**
 * Mutable per-phase state during equilibrium iteration.
 *
 * <p>Mirrors a single entry of the Mathematica {@code phaseNameList}:
 * {@code {phaseName, modelType, nfu, composition}}.
 * Tracks the phase's current internal variables, composition, amount,
 * and cached computation results across Newton iterations.
 */
public class PhaseRecord {

    /** The phase model adapter. */
    public final GibbsEnergyModel model;

    /** Phase amount ℵ (formula units). Maps to NListN[ip] in Mathematica. */
    public double amount;

    /** Internal variables y[] (cluster vars for CVM, site fracs for CEF, = x for RK). */
    public double[] y;

    /** Mole fractions x[] derived from y via compositionFromInternal. */
    public double[] x;

    /** Gibbs energy per formula unit (from last compute). */
    public double G;

    /**
     * Driving force: γ = G + Σ μ_A · x_A.
     * Negative means metastable; zero at equilibrium.
     */
    public double drivingForce;

    /** Whether this phase is in the stable set (amount > 0). */
    public boolean stable;

    /** Cached result from last GibbsEnergyModel.compute() call. */
    public PhaseEquilData lastCompute;

    /**
     * Constructs a new phase record.
     *
     * @param model   the phase model adapter
     * @param x       initial composition (mole fractions)
     * @param amount  initial phase amount (formula units)
     * @param stable  whether to include in initial stable set
     */
    public PhaseRecord(GibbsEnergyModel model, double[] x,
                       double amount, boolean stable) {
        this.model  = model;
        this.x      = x.clone();
        this.y      = model.getInitialInternalVars(x);
        this.amount = amount;
        this.stable = stable;
    }

    /**
     * Update mole fractions from current internal parameters.
     * Must be called after modifying y[].
     */
    public void updateComposition() {
        this.x = model.compositionFromInternal(y);
    }

    /**
     * Run full per-phase compute and cache the result.
     *
     * @param T      temperature
     * @param P      pressure
     * @param deltaT ΔT from outer loop
     * @param deltaP ΔP from outer loop
     * @param mu     chemical potentials
     */
    public void updateFromModel(double T, double P,
                                double deltaT, double deltaP,
                                double[] mu) {
        lastCompute = model.compute(T, P, y, deltaT, deltaP, mu);
        this.G = lastCompute.G;
        this.x = lastCompute.x;
    }

    /**
     * Compute driving force γ = G + Σ μ_A · x_A.
     * For a stable phase at equilibrium, γ = 0.
     * For a metastable phase, γ < 0. If γ > 0, the phase should be added to the stable set.
     *
     * @param mu chemical potentials
     */
    public void computeDrivingForce(double[] mu) {
        drivingForce = G;
        for (int i = 0; i < x.length; i++) {
            drivingForce += mu[i] * x[i];
        }
    }

    /** Convenience: phase name from the underlying model. */
    public String phaseName() { return model.phaseName(); }

    /** Convenience: model type from the underlying model. */
    public String modelType() { return model.modelType(); }
}
