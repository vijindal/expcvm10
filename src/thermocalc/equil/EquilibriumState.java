package thermocalc.equil;

import java.util.ArrayList;
import java.util.List;

/**
 * Global system state during equilibrium iteration.
 *
 * <p>Holds T, P, chemical potentials μ[], total composition,
 * and the list of all candidate phases (stable + metastable).
 */
public class EquilibriumState {

    /** Temperature in Kelvin. */
    public double T;

    /** Pressure in Pa. */
    public double P;

    /** Chemical potentials μ[0..nc-1]. */
    public double[] mu;

    /** Overall composition (total moles per component). */
    public double[] compOverAll;

    /** All candidate phase records (stable and metastable). */
    public final List<PhaseRecord> phases;

    public EquilibriumState(double T, double P, double[] compOverAll,
                            List<PhaseRecord> phases) {
        this.T = T;
        this.P = P;
        this.compOverAll = compOverAll.clone();
        this.phases = new ArrayList<>(phases);
        this.mu = new double[compOverAll.length];
    }

    /** Returns only the phases in the stable set (amount > 0). */
    public List<PhaseRecord> stablePhases() {
        List<PhaseRecord> result = new ArrayList<>();
        for (PhaseRecord pr : phases) {
            if (pr.stable) result.add(pr);
        }
        return result;
    }

    /** Returns only the metastable phases (not in stable set). */
    public List<PhaseRecord> metastablePhases() {
        List<PhaseRecord> result = new ArrayList<>();
        for (PhaseRecord pr : phases) {
            if (!pr.stable) result.add(pr);
        }
        return result;
    }

    /** Number of stable phases. */
    public int numStable() {
        int count = 0;
        for (PhaseRecord pr : phases) {
            if (pr.stable) count++;
        }
        return count;
    }

    /** Number of components. */
    public int numComponents() {
        return compOverAll.length;
    }

    /**
     * Mass balance residual: Σ_α ℵ^α · x^α_A - compOverAll_A for each component.
     *
     * @return residual array, length numComponents
     */
    public double[] massBalanceResidual() {
        int nc = numComponents();
        double[] residual = new double[nc];
        for (PhaseRecord pr : phases) {
            if (pr.stable) {
                for (int i = 0; i < nc; i++) {
                    residual[i] += pr.amount * pr.x[i];
                }
            }
        }
        for (int i = 0; i < nc; i++) {
            residual[i] -= compOverAll[i];
        }
        return residual;
    }
}
