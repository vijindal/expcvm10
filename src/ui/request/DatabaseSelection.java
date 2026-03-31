package ui.request;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared DTO representing a fully validated database + element + phase selection.
 * Produced by DatabaseExtractionPanel (GUI) or DatabaseSelectionHelper (CLI)
 * and consumed by all non-assessment calculation activities.
 */
public class DatabaseSelection {

    private String       tdbPath;           // absolute or relative path to .tdb file
    private List<String> elements;          // validated, uppercase element names chosen by user
    private List<String> availableElements; // all elements present in the loaded TDB
    private List<String> availablePhases;   // all phases for the chosen elements

    public DatabaseSelection() {
        elements          = new ArrayList<>();
        availableElements = new ArrayList<>();
        availablePhases   = new ArrayList<>();
    }

    // ── Getters ──────────────────────────────────────────────────────

    public String getTdbPath()                  { return tdbPath; }
    public List<String> getElements()           { return elements; }
    public List<String> getAvailableElements()  { return availableElements; }
    public List<String> getAvailablePhases()    { return availablePhases; }

    // ── Setters ──────────────────────────────────────────────────────

    public void setTdbPath(String tdbPath)                          { this.tdbPath = tdbPath; }
    public void setElements(List<String> elements)                  { this.elements = elements; }
    public void setAvailableElements(List<String> availableElements){ this.availableElements = availableElements; }
    public void setAvailablePhases(List<String> availablePhases)    { this.availablePhases = availablePhases; }

    // ── Convenience ──────────────────────────────────────────────────

    public boolean hasTdb()      { return tdbPath != null && !tdbPath.isEmpty(); }
    public boolean hasElements() { return elements != null && !elements.isEmpty(); }
    public boolean hasPhases()   { return availablePhases != null && !availablePhases.isEmpty(); }
}
