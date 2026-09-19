package ui.layer;

import session.CalculationSession;
import system.database.tdb;
import util.AppLevel;
import util.Trace;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service: TDB inspection and model metadata.
 *
 * <p>Per the target data flow ({@code docs/dataflow_target.png}), this service
 * accesses the TDB database through {@link CalculationSession}, never by creating
 * a {@link system.ports.DatabasePort} or {@link TdbParser} directly. Database
 * browsing methods ({@link #inspectModel}, {@link #getPhasesForElements},
 * {@link #getPhaseParameters}) delegate to the session's cache, so repeated
 * accesses to the same TDB file do not re-parse it.
 */
public class ModelInspectionService {

    private static final Logger LOG = Logger.getLogger(ModelInspectionService.class.getName());
    private final CalculationSession session;

    /**
     * Constructor with dependency injection.
     * @param session the CalculationSession through which all TDB access happens
     */
    public ModelInspectionService(CalculationSession session) {
        this.session = session;
    }

    /**
     * Inspect TDB and return model metadata for GUI/CLI, routed through
     * {@link CalculationSession} to benefit from its caching.
     */
    public ui.result.ModelInfo inspectModel(String tdbPath, String[] elements) {
        Trace.enter(LOG, AppLevel.FLOW, "ModelInspectionService", "inspectModel");
        ui.result.ModelInfo info = new ui.result.ModelInfo();
        info.setFilePath(tdbPath);
        File file = new File(tdbPath);
        info.setFileExists(file.exists());
        info.setLastModifiedEpochMillis(info.isFileExists() ? file.lastModified() : 0L);
        info.setDetectedElements(java.util.Arrays.asList(elements));

        if (!info.isFileExists()) {
            info.setError("TDB file not found.");
            info.setAvailablePhases(Collections.emptyList());
            return info;
        }

        try {
            java.util.List<String> allElements = session.availableElements(tdbPath);
            info.setAvailableElements(allElements);
            if (elements != null && elements.length > 0) {
                java.util.List<String> elementList = java.util.Arrays.asList(elements);
                info.setAvailablePhases(session.availablePhasesFor(tdbPath, elementList));
                LOG.log(AppLevel.RESULT, "inspectModel found phases: {0}", info.getAvailablePhases());
            } else {
                // Show all phases in the TDB if no elements specified
                info.setAvailablePhases(session.availablePhasesFor(tdbPath, Collections.emptyList()));
            }
        } catch (Exception ex) {
            info.setAvailablePhases(Collections.emptyList());
            info.setAvailableElements(Collections.emptyList());
            info.setError(ex.getMessage());
            LOG.log(AppLevel.WARN, "inspectModel error", ex);
        }
        Trace.exit(LOG, AppLevel.FLOW, "ModelInspectionService", "inspectModel");
        return info;
    }

    /**
     * Returns phase names whose constituents are a subset of the given elements,
     * routed through {@link CalculationSession}.
     */
    public List<String> getPhasesForElements(String tdbPath, List<String> elements) {
        if (elements == null || elements.isEmpty()) return Collections.emptyList();
        try {
            return session.availablePhasesFor(tdbPath, elements);
        } catch (Exception ex) {
            LOG.log(AppLevel.WARN, "getPhasesForElements error", ex);
            return Collections.emptyList();
        }
    }

    /**
     * Returns the Parameter list for a given phase + element set from the loaded TDB.
     *
     * <p><b>TODO:</b> This method accesses internal TDB details (the parsed {@code tdb}
     * object) that {@link CalculationSession} does not currently expose. This needs a
     * new {@code CalculationSession} method to bridge it, or the GUI inspector to
     * be redesigned to work without raw parameter access.
     */
    public List<tdb.Parameter> getPhaseParameters(String tdbPath, List<String> elements, String phaseName) {
        if (elements == null || elements.isEmpty() || phaseName == null) return Collections.emptyList();
        // TODO: Implement via CalculationSession
        LOG.log(AppLevel.WARN, "getPhaseParameters not yet wired through CalculationSession");
        return Collections.emptyList();
    }

    /**
     * Legacy CalModel-based calculation removed with legacy code folder deletion.
     * This method is deprecated and should not be used.
     */
    @Deprecated
    public ui.result.CalculationResult runCalModel(String exptDataFile, String phaseDataFile) throws IOException {
        throw new UnsupportedOperationException("CalModel is no longer available -- legacy code folder has been removed");
    }
}
