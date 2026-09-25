package ui.gui;

import ui.request.DatabaseSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared GUI-level state for the currently selected TDB, elements, and
 * phases, held once by {@link MainFrame} and read/written by every
 * activity's sidebar panel so a selection made in one activity survives
 * switching to another.
 *
 * <p>Holds only GUI selection state -- no calculation results, solver
 * state, or {@code ApplicationLayer} objects.
 */
public class GuiCalculationContext {

    private final DatabaseSelection selection = new DatabaseSelection();
    private List<String> selectedPhases = new ArrayList<>();

    private final List<Consumer<GuiCalculationContext>> listeners = new ArrayList<>();

    public DatabaseSelection getSelection() { return selection; }

    public List<String> getSelectedPhases() { return selectedPhases; }

    /** Replaces the TDB/element selection wholesale (called by the shared DatabaseExtractionPanel). */
    public void applySelection(DatabaseSelection newSelection) {
        selection.setTdbPath(newSelection.getTdbPath());
        selection.setAvailableElements(newSelection.getAvailableElements());
        selection.setElements(newSelection.getElements());
        selection.setAvailablePhases(newSelection.getAvailablePhases());
        pruneInvalidPhases();
        fireChanged();
    }

    /** Sets the phases the user has chosen for the current TDB/elements; invalid entries are dropped first. */
    public void setSelectedPhases(List<String> phases) {
        this.selectedPhases = phases != null ? new ArrayList<>(phases) : new ArrayList<>();
        pruneInvalidPhases();
        fireChanged();
    }

    private void pruneInvalidPhases() {
        List<String> available = selection.getAvailablePhases();
        if (available == null || available.isEmpty()) return;
        selectedPhases.removeIf(p -> !available.contains(p));
    }

    /** Registers a listener notified whenever the TDB/elements/phases change. */
    public void addListener(Consumer<GuiCalculationContext> listener) {
        listeners.add(listener);
    }

    private void fireChanged() {
        for (Consumer<GuiCalculationContext> l : listeners) l.accept(this);
    }
}
