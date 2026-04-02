// ...existing code...
package system.database;

import system.database.tdb;
import system.model.GibbsEnergyModel;
import system.model.rk.RkPhaseModelFactory;
import system.ports.DatabasePort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public List<?> buildPhaseModels(List<String> elements,
                                    List<String> phaseNames) throws IOException {

        List<system.model.PhaseModelFactory.PhaseModel> models = new ArrayList<>();

        // Step 1: extract affMap and pMap from TYPE_DEFINITION records
        // These contain MAGNETIC parameters: aff (value1) and p (value2)
        Map<String, Double> affMap = new java.util.HashMap<>();
        Map<String, Double> pMap   = new java.util.HashMap<>();

        tdb systdb = this.getUnderlyingTdb();
        if (systdb != null) {
            for (tdb.TypeDefinition td : systdb.getTypeDefinitions()) {
                if ("MAGNETIC".equalsIgnoreCase(td.property)
                        && td.phasename != null
                        && !td.phasename.isEmpty()) {
                    affMap.put(td.phasename, td.value1);
                    pMap.put(td.phasename, td.value2);
                    LOG.fine("Magnetic phase: " + td.phasename
                           + " aff=" + td.value1 + " p=" + td.value2);
                }
            }
        }

        // Step 2: extract system tdb filtered for selected elements
        DatabasePort filteredDb = extractSystem(elements.toArray(new String[0]));
        tdb filteredTdb = ((TdbParser) filteredDb).getUnderlyingTdb();

        // Step 3: build a PhaseModel for each requested phase
        for (String phaseName : phaseNames) {
            try {
                system.model.PhaseModelFactory.PhaseModel model =
                    system.model.PhaseModelFactory.build(
                        phaseName,
                        filteredTdb,
                        elements,
                        affMap,
                        pMap
                    );
                models.add(model);
                LOG.fine("Built CEF model: " + phaseName
                       + (model.hasMagnetic() ? " [MAGNETIC]" : ""));
            } catch (Exception ex) {
                LOG.warning("Skipping phase " + phaseName
                          + ": " + ex.getMessage());
            }
        }
        return models;
    }
}
