package calc.equil.sundman;

import java.util.ArrayList;
import java.util.List;

/**
 * Global state for Algorithm A (Sundman et al., Fig. 1): the box labeled
 * "ℵ^α, y_is^α, μ_A, γ^φ" in the flowchart, plus the fixed conditions
 * (T, P, Ñ_A) for this single-equilibrium calculation.
 *
 * <p>This is a passive data container. All phase-set decisions and
 * iteration logic live in {@link SundmanEquilibriumSolver}.
 */
public final class SundmanEquilibriumState {

    /** Temperature, K. */
    public final double T;

    /** Pressure, Pa. */
    public final double P;

    /** Prescribed total moles of each component, Ñ_A, length nc. */
    public final double[] Ntotal;

    /** Chemical potentials μ_A, length nc. */
    public double[] mu;

    /** All candidate phases (both currently stable and metastable). */
    public final List<SundmanPhase> phases;

    public SundmanEquilibriumState(double T, double P, double[] Ntotal, List<SundmanPhase> phases) {
        this.T = T;
        this.P = P;
        this.Ntotal = Ntotal.clone();
        this.phases = new ArrayList<>(phases);
        this.mu = new double[Ntotal.length];
    }

    public int numComponents() { return Ntotal.length; }

    public List<SundmanPhase> stablePhases() {
        List<SundmanPhase> result = new ArrayList<>();
        for (SundmanPhase p : phases) if (p.stable) result.add(p);
        return result;
    }

    public List<SundmanPhase> metastablePhases() {
        List<SundmanPhase> result = new ArrayList<>();
        for (SundmanPhase p : phases) if (!p.stable) result.add(p);
        return result;
    }

    public int numStable() {
        int n = 0;
        for (SundmanPhase p : phases) if (p.stable) n++;
        return n;
    }
}
