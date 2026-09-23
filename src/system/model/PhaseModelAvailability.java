package system.model;

import system.database.tdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Discovers which Gibbs-energy model implementations are available for
 * a selected phase and element set from a loaded TDB database.
 *
 * <p>Separates parameter discovery from model construction: caller can
 * query which models are available, then explicitly select one to build.
 *
 * <p>Model availability is determined by inspecting parameter families:
 * <ul>
 *   <li>{@code PARAMETER G(...)} → CEF available</li>
 *   <li>{@code PARAMETER G_CVM(...)} → CVM available</li>
 * </ul>
 *
 * <p>Discovery must filter by selected elements and phase name so that
 * unrelated parameters in the TDB do not pollute availability results.
 */
public final class PhaseModelAvailability {

    private PhaseModelAvailability() {}

    /**
     * Queries which model families are available for a phase/element set.
     *
     * @param database       loaded TDB database
     * @param elements       ordered system elements (e.g., ["V", "ZR"])
     * @param phaseName      phase to query (e.g., "BCC_A2")
     * @return               list of available model kinds, in order
     *                       (CEF before CVM if both exist). Empty list if
     *                       neither is available.
     * @throws NullPointerException if database, elements, or phaseName is null
     * @throws IllegalArgumentException if phaseName is blank
     */
    public static List<PhaseModelKind> availableModels(
            tdb database,
            List<String> elements,
            String phaseName) {

        if (database == null)
            throw new NullPointerException("database must not be null");
        if (elements == null)
            throw new NullPointerException("elements must not be null");
        if (phaseName == null || phaseName.isBlank())
            throw new IllegalArgumentException("phaseName must not be blank");

        List<PhaseModelKind> available = new ArrayList<>();

        // Check CEF availability: PARAMETER G(...)
        ArrayList<String> elementList = new ArrayList<>(elements);
        ArrayList<tdb.Parameter> cefParams = database.getPhaseParam(elementList, phaseName);

        if (!cefParams.isEmpty()) {
            available.add(PhaseModelKind.CEF);
        }

        // Check CVM availability: PARAMETER G_CVM(...)
        ArrayList<tdb.Parameter> cvmParams = database.getCvmParams(elementList, phaseName);

        if (!cvmParams.isEmpty()) {
            available.add(PhaseModelKind.CVM);
        }

        return available;
    }

    /**
     * Checks whether a specific model kind is available for this phase/element set.
     *
     * @param database       loaded TDB database
     * @param elements       ordered system elements
     * @param phaseName      phase to query
     * @param kind           the model kind to check
     * @return               true if this model is available, false otherwise
     */
    public static boolean isAvailable(
            tdb database,
            List<String> elements,
            String phaseName,
            PhaseModelKind kind) {

        return availableModels(database, elements, phaseName).contains(kind);
    }
}
