package ui.layer;

import session.CalculationSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared bridge between {@link CalculationSession}'s browsing methods
 * ({@link CalculationSession#availableElements}/{@link
 * CalculationSession#availablePhasesFor}) and the three UIs (GUI, CLI,
 * REST API), so "which elements/phases can I pick" logic -- including
 * filtering out TDB pseudo-elements that are never a real selection --
 * lives in exactly one place instead of being reimplemented per UI.
 *
 * <p>Every TDB file declares two reserved, non-physical {@code ELEMENT}
 * entries per the TDB format spec: {@code /-} (electron gas) and
 * {@code VA} (vacancy) -- both always present, both meaningless as a
 * user-facing element choice. {@link CalculationSession#availableElements}
 * returns them as-is (it is a thin, faithful wrapper over the parsed
 * database, and rightly so); this service is where the UI-facing
 * filtering belongs instead.
 */
public class ModelBrowseService {

    private static final Set<String> PSEUDO_ELEMENTS = Set.of("/-", "VA");

    private final CalculationSession session;

    public ModelBrowseService(CalculationSession session) {
        this.session = session;
    }

    /**
     * Elements a user can actually pick from {@code tdbFilePath} --
     * {@link CalculationSession#availableElements} with the reserved
     * pseudo-elements ({@code /-}, {@code VA}) removed, sorted
     * alphabetically.
     *
     * <p>{@link CalculationSession#availableElements} returns elements in
     * raw TDB declaration order (e.g. {@code [FE, C]}), which then becomes
     * the composition-index order for the whole calculation (see {@code
     * ThermodynamicSystem.build}'s {@code elements} parameter). A user
     * typing a composition against that list has no way to know the
     * declaration order without re-reading the prompt every time, and a
     * mismatch is not just cosmetic -- e.g. typing {@code 0.05,0.95}
     * expecting alphabetical (C, Fe) against a Fe-then-C list silently
     * requests 95% carbon steel instead of 5%, which then fails deep
     * inside the solver ("CEF phase could not generate an initial
     * constitution") rather than at input time. Sorting here fixes it for
     * every caller (CLI/GUI/API) at once, since this is the one shared
     * bridge all three already use for element discovery.
     */
    public List<String> selectableElements(String tdbFilePath) throws IOException {
        List<String> all = session.availableElements(tdbFilePath);
        List<String> real = new ArrayList<>(all.size());
        for (String element : all) {
            if (!PSEUDO_ELEMENTS.contains(element)) {
                real.add(element);
            }
        }
        real.sort(String::compareTo);
        return real;
    }

    /**
     * Phases available for {@code elements} in {@code tdbFilePath} --
     * currently a direct pass-through to {@link
     * CalculationSession#availablePhasesFor}, kept here so callers only
     * need this one service for both browsing steps.
     */
    public List<String> selectablePhases(String tdbFilePath, List<String> elements) throws IOException {
        return session.availablePhasesFor(tdbFilePath, elements);
    }
}
