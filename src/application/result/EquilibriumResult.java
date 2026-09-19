package application.result;

/**
 * Result of an equilibrium calculation.
 *
 * <p>Holds the equilibrium state (temperature, pressure, compositions, phase amounts)
 * and convergence information. Lives in the session layer so both UI and System/Calculation
 * layers can access it without direct dependencies.
 */
public final class EquilibriumResult {

    private final EquilibriumState state;
    private final boolean converged;
    private final int iterations;
    private final double residual;

    public EquilibriumResult(EquilibriumState state, boolean converged, int iterations, double residual) {
        this.state = state;
        this.converged = converged;
        this.iterations = iterations;
        this.residual = residual;
    }

    public EquilibriumState getState() { return state; }
    public boolean isConverged() { return converged; }
    public int getIterations() { return iterations; }
    public double getResidual() { return residual; }

    /**
     * Equilibrium state: temperature, pressure, element compositions, phase amounts,
     * phase names, and chemical potentials.
     */
    public static final class EquilibriumState {
        private final double temperature;
        private final double pressure;
        private final double[] elementCompositions;
        private final double[] phaseAmounts;
        private final String[] stablePhases;
        private final double[] chemicalPotentials;

        public EquilibriumState(double temperature, double pressure,
                              double[] elementCompositions, double[] phaseAmounts,
                              String[] stablePhases, double[] chemicalPotentials) {
            this.temperature = temperature;
            this.pressure = pressure;
            this.elementCompositions = elementCompositions.clone();
            this.phaseAmounts = phaseAmounts.clone();
            this.stablePhases = stablePhases.clone();
            this.chemicalPotentials = chemicalPotentials.clone();
        }

        public double getTemperature() { return temperature; }
        public double getPressure() { return pressure; }
        public double[] getElementCompositions() { return elementCompositions.clone(); }
        public double[] getPhaseAmounts() { return phaseAmounts.clone(); }
        public String[] getStablePhases() { return stablePhases.clone(); }
        public double[] getChemicalPotentials() { return chemicalPotentials.clone(); }
    }
}
