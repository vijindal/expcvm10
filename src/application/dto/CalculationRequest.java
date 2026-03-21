package application.dto;

import java.util.ArrayList;

/**
 * DTO carrying all information needed to set up a calculation or assessment run.
 */
public class CalculationRequest {

    private String method;              // e.g. "HM", "Gm", "G", "SPT"
    private ArrayList<String> phases;   // phase names involved
    private ArrayList<String> elements; // element symbols
    private double T;
    private double P;
    private ArrayList<ArrayList<Double>> compositions;
    private String tdbFilePath;

    public CalculationRequest() {
    }

    // --- Getters / Setters ---

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public ArrayList<String> getPhases() { return phases; }
    public void setPhases(ArrayList<String> phases) { this.phases = phases; }

    public ArrayList<String> getElements() { return elements; }
    public void setElements(ArrayList<String> elements) { this.elements = elements; }

    public double getT() { return T; }
    public void setT(double T) { this.T = T; }

    public double getP() { return P; }
    public void setP(double P) { this.P = P; }

    public ArrayList<ArrayList<Double>> getCompositions() { return compositions; }
    public void setCompositions(ArrayList<ArrayList<Double>> compositions) { this.compositions = compositions; }

    public String getTdbFilePath() { return tdbFilePath; }
    public void setTdbFilePath(String tdbFilePath) { this.tdbFilePath = tdbFilePath; }
}
