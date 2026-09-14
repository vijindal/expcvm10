package system.ports;

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

    /**
     * Total system Gibbs energy per mole of real atoms -- {@link
     * #totalG()} divided by the total {@link PhaseResult#atoms()} across
     * every stable phase, not {@link #totalG()} divided by total {@code
     * amount} (formula units). This is the quantity comparable across
     * different candidate phase sets with different formula-unit sizes
     * (e.g. this result's own phases vs. {@code GridMinimizer}'s, which
     * reports G per mole of real atoms internally -- see that class's
     * own javadoc) -- {@link #totalG()} alone is NOT comparable across
     * phase sets whose formula units represent different atom counts.
     */
    public double totalGPerAtom() {
        double totalAtoms = 0;
        for (PhaseResult ph : stablePhases) {
            totalAtoms += ph.atoms();
        }
        if (totalAtoms <= 0.0) {
            throw new IllegalStateException(
                    "Cannot compute totalGPerAtom(): no real atoms in the stable phase set "
                    + "(totalAtoms=" + totalAtoms + ").");
        }
        return totalG() / totalAtoms;
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
        public final double   totalMoles;    // real atoms per formula unit at y (see GibbsEnergyModel#totalMoles)

        public PhaseResult(String phaseName, String modelType,
                           double amount, double[] x, double[] y,
                           double G, double drivingForce, double totalMoles) {
            this.phaseName    = phaseName;
            this.modelType    = modelType;
            this.amount       = amount;
            this.x            = x.clone();
            this.y            = y.clone();
            this.G            = G;
            this.drivingForce = drivingForce;
            this.totalMoles   = totalMoles;
        }

        /**
         * Real moles of atoms this phase contributes to the system --
         * {@code amount} (formula units) times {@code totalMoles} (atoms
         * per formula unit at this constitution) -- NOT {@code amount}
         * alone. The lever rule balances on this quantity, not on raw
         * {@code amount}: two phases with different formula-unit sizes
         * (e.g. CEMENTITE's Fe3C, 4 atoms/f.u., vs BCC_A2's Fe, 1 atom/f.u.)
         * cannot be compared or summed as formula-unit amounts directly.
         */
        public double atoms() {
            return amount * totalMoles;
        }
    }
}
