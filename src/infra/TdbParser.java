// ...existing code...
package infra;

import database.tdb;
import domain.DatabasePort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Infrastructure adapter wrapping the legacy tdb parser.
 * Implements DatabasePort so application/domain code can depend on the port interface.
 */
public class TdbParser implements DatabasePort {

    /**
     * Returns all element names in the loaded TDB.
     */
    public ArrayList<String> getElementNames() {
        if (database == null) return new ArrayList<>();
        return database.getElementNames();
    }

    private static final Logger LOG = Logger.getLogger(TdbParser.class.getName());
    private tdb database;

    @Override
    public void load(String filePath) throws IOException {
        LOG.fine("Loading TDB file: " + filePath);
        this.database = new tdb(filePath);
        LOG.fine("TDB file loaded successfully.");
    }

    @Override
    public DatabasePort extractSystem(String[] elements) throws IOException {
        LOG.fine("Extracting system for elements: " + java.util.Arrays.toString(elements));
        TdbParser sub = new TdbParser();
        sub.database = this.database.gettdb(elements);
        return sub;
    }

    @Override
    public ArrayList<String> getPhaseNames() {
        if (database == null) {
            return new ArrayList<>();
        }
        return database.getPhaseNames();
    }

    /**
     * Access the underlying legacy tdb object for backward compatibility.
     * Application code should migrate away from this over time.
     */
    public tdb getUnderlyingTdb() {
        return database;
    }
}
