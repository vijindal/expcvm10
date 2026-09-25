// ...existing code...
package system.database;

import system.database.tdb;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelAvailability;
import system.model.PhaseModelKind;
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
    private String loadedFilePath;

    /**
     * Loads {@code filePath}, unless it is already the currently loaded
     * file -- in which case this is a no-op. Callers that construct a
     * fresh {@code TdbParser} per load are unaffected (each instance
     * loads at most once); callers that reuse one instance across
     * repeated {@code load()} calls with the same path (e.g. GUI panels
     * re-inspecting the same default database) no longer re-parse it
     * from disk every time.
     */
    @Override
    public void load(String filePath) throws IOException {
        if (filePath.equals(loadedFilePath)) {
            return;
        }
        LOG.fine("Loading TDB file: " + filePath);
        this.database = new tdb(filePath);
        this.loadedFilePath = filePath;
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
    public List<GibbsEnergyModel> buildPhaseModels(List<String> elements,
                                    List<String> phaseNames) throws IOException {
        return buildPhaseModels(elements, phaseNames, PhaseModelKind.AUTO);
    }

    @Override
    public List<GibbsEnergyModel> buildPhaseModels(List<String> elements,
                                    List<String> phaseNames,
                                    PhaseModelKind kind) throws IOException {

        List<GibbsEnergyModel> models = new ArrayList<>();

        // Step 1: extract affMap and pMap from TYPE_DEFINITION records
        // These contain MAGNETIC parameters: aff (value1) and p (value2)
        Map<String, Double> affMap = new java.util.HashMap<>();
        Map<String, Double> pMap   = new java.util.HashMap<>();

        tdb systdb = this.getUnderlyingTdb();
        if (systdb != null) {
            java.util.Map<String, java.util.ArrayList<String>> typecharMap = null;
            for (tdb.TypeDefinition td : systdb.getTypeDefinitions()) {
                if (!"MAGNETIC".equalsIgnoreCase(td.property)) continue;

                if ("@".equals(td.phasename)) {
                    // @-form: TYPE_DEFINITION <char> ... @ MAGNETIC aff p --
                    // the hint applies to every phase whose PHASE record's
                    // %-field contains <char> (td.dataTypeCode), not to a
                    // literal phase named "@". See tdb.getTypecharPhaseMap()
                    // (mirrors pycalphad's _typechar_map).
                    if (typecharMap == null) {
                        typecharMap = systdb.getTypecharPhaseMap();
                    }
                    java.util.ArrayList<String> matchingPhases =
                            typecharMap.get(td.dataTypeCode);
                    if (matchingPhases == null || matchingPhases.isEmpty()) {
                        LOG.fine("TYPE_DEFINITION " + td.dataTypeCode
                                + " ... @ MAGNETIC ... matches no PHASE's %-field");
                        continue;
                    }
                    for (String phaseName : matchingPhases) {
                        affMap.put(phaseName, td.value1);
                        pMap.put(phaseName, td.value2);
                        LOG.fine("Magnetic phase: " + phaseName
                               + " aff=" + td.value1 + " p=" + td.value2
                               + " (via typechar " + td.dataTypeCode + ")");
                    }
                } else if (td.phasename != null && !td.phasename.isEmpty()) {
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
                PhaseModelKind resolvedKind = kind == PhaseModelKind.AUTO
                        ? resolveAutoKind(filteredTdb, elements, phaseName)
                        : kind;

                if (resolvedKind == PhaseModelKind.CVM) {
                    GibbsEnergyModel model = system.model.PhaseModelFactory.buildCvmFromTdb(
                        filteredTdb,
                        elements,
                        phaseName
                    );
                    models.add(model);
                    LOG.fine("Built CVM model: " + phaseName);
                } else {
                    system.model.cef.CefGibbs model =
                        system.model.PhaseModelFactory.build(
                            phaseName,
                            filteredTdb,
                            elements,
                            affMap,
                            pMap,
                            resolvedKind
                        );
                    models.add(model);
                    LOG.fine("Built CEF model: " + phaseName
                           + (model.hasMagnetic() ? " [MAGNETIC]" : ""));
                }
            } catch (Exception ex) {
                LOG.warning("Skipping phase " + phaseName
                          + ": " + ex.getMessage());
            }
        }
        return models;
    }

    /** Resolves {@link PhaseModelKind#AUTO} to CEF or CVM for one phase. */
    private static PhaseModelKind resolveAutoKind(tdb database, List<String> elements, String phaseName) {
        List<PhaseModelKind> available = PhaseModelAvailability.availableModels(
                database, new ArrayList<>(elements), phaseName);

        if (available.contains(PhaseModelKind.CEF) || !available.contains(PhaseModelKind.CVM)) {
            return PhaseModelKind.CEF;
        }
        return PhaseModelKind.CVM;
    }
}
