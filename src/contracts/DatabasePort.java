package contracts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Port interface for loading and querying a thermodynamic database.
 * Infrastructure layer provides the concrete implementation (e.g. TDB parser).
 */
public interface DatabasePort {

    /**
     * Load the database from the given file path.
     */
    void load(String filePath) throws IOException;

    /**
     * Extract a sub-database for the given element set.
     *
     * @param elements element symbols (e.g. "TI", "ZR")
     * @return a new DatabasePort scoped to those elements
     */
    DatabasePort extractSystem(String[] elements) throws IOException;

    /**
     * Get the list of phase names available in this database.
     */
    ArrayList<String> getPhaseNames();

    /**
     * Build phase models for the requested phases and element set.
     * Convenience method combining extractSystem + phase model construction.
     *
     * @param phases element names (e.g. ["TI", "ZR"])
     * @param phaseNames phase names to build models for (e.g. ["LIQUID", "BCC_A2"])
     * @return list of GibbsEnergyModel objects ready for equilibrium solving
     * @throws IOException if database load fails
     */
    List<?> buildPhaseModels(List<String> phases, List<String> phaseNames) throws IOException;
}
