package system.ports;

/**
 * Port for legacy fitting/minimization functionality.
 *
 * Isolates the legacy `calbince` and `phase` packages behind a clean interface.
 * This allows the rest of the application to remain dependency-free from legacy code.
 *
 * Implementation: {@link legacy.LegacyFitAdapter}
 */
public interface LegacyFitPort {

    /**
     * Fit/minimize internal variables for a C15 phase at fixed composition.
     *
     * @param phase          C15Phase object with T, P, reference Gibbs energies, interactions set
     * @param composition    target composition (mole fractions), length = nComp
     * @param nComp          number of components
     * @param speciesToComp  mapping from species (sublattice sites) to components
     * @return               equilibrium internal variables (site occupancies) y
     */
    double[] fitC15Parameters(Object phase, double[] composition, int nComp, int[][] speciesToComp);

    /**
     * Fit/minimize internal variables using the improved algorithm (variant).
     *
     * @param phase          C15Phase object
     * @param composition    target composition
     * @param nComp          number of components
     * @param speciesToComp  species-to-component mapping
     * @return               equilibrium internal variables y
     */
    double[] fitC15ParametersImproved(Object phase, double[] composition, int nComp, int[][] speciesToComp);
}
