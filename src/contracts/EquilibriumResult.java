package contracts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable value object representing the result of a multi-phase
 * equilibrium calculation (Algorithm A).
 *
 * <p>Contains the converged temperature, pressure, chemical potentials,
 * and the set of stable/metastable phases with their amounts and compositions.
 */
public final class EquilibriumResult {

    private final double T;
    private final double P;
    private final double[] mu;
    private final List<PhaseResult> stablePhases;
    private final List<PhaseResult> metastablePhases;
    private final boolean converged;
    private final int iterations;

    public EquilibriumResult(double T, double P, double[] mu,
                             List<PhaseResult> stablePhases,
                             List<PhaseResult> metastablePhases,
                             boolean converged, int iterations) {
        this.T = T;
        this.P = P;
        this.mu = mu.clone();
        this.stablePhases = Collections.unmodifiableList(
                new ArrayList<>(stablePhases));
        this.metastablePhases = Collections.unmodifiableList(
                new ArrayList<>(metastablePhases));
        this.converged = converged;
        this.iterations = iterations;
    }

    public double getT()  { return T; }
    public double getP()  { return P; }
    public double[] getMu() { return mu.clone(); }
    public List<PhaseResult> getStablePhases()     { return stablePhases; }
    public List<PhaseResult> getMetastablePhases()  { return metastablePhases; }
    public boolean isConverged() { return converged; }
    public int getIterations()   { return iterations; }

    /** Total system Gibbs energy: G_sys = Σ ℵ^α · G^α. */
    public double totalG() {
        double g = 0;
        for (PhaseResult ph : stablePhases) {
            g += ph.amount * ph.G;
        }
        return g;
    }

    // ------------------------------------------------------------------
    // Per-phase result
    // ------------------------------------------------------------------

    /**
     * Data for a single phase in the equilibrium result.
     */
    public static final class PhaseResult {
        public final String   phaseName;
        public final String   modelType;
        public final double   amount;        // ℵ (formula units)
        public final double[] x;             // mole fractions
        public final double[] y;             // internal parameters
        public final double   G;             // Gibbs energy per formula unit
        public final double   drivingForce;  // γ = G + Σ μ_A · x_A

        public PhaseResult(String phaseName, String modelType,
                           double amount, double[] x, double[] y,
                           double G, double drivingForce) {
            this.phaseName    = phaseName;
            this.modelType    = modelType;
            this.amount       = amount;
            this.x            = x.clone();
            this.y            = y.clone();
            this.G            = G;
            this.drivingForce = drivingForce;
        }
    }
}
