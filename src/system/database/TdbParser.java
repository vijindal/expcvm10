// ...existing code...
package system.database;

import system.database.tdb;
import system.model.GibbsEnergyModel;
import system.model.rk.RkPhaseModelFactory;
import contracts.DatabasePort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public List<?> buildPhaseModels(List<String> elements, List<String> phaseNames) throws IOException {
        List<GibbsEnergyModel> models = new ArrayList<>();
        DatabasePort sub = extractSystem(elements.toArray(new String[0]));
        tdb systdb = ((TdbParser) sub).getUnderlyingTdb();

        for (String phaseName : phaseNames) {
            try {
                models.add(RkPhaseModelFactory.build(phaseName, elements, systdb));
                LOG.fine("Built model: " + phaseName);
            } catch (Exception ex) {
                LOG.warning("Skipping phase " + phaseName + ": " + ex.getMessage());
            }
        }
        return models;
    }
}
