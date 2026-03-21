package domain;

import java.io.IOException;
import java.util.ArrayList;

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
}
