package ui.request;

import calc.diagram.AxisConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Input DTO for a phase diagram calculation.
 *
 * <h2>Usage examples</h2>
 *
 * <b>Binary T-x MAP diagram (Ti-Zr, 800–2000 K):</b>
 * <pre>
 *   PhaseDiagramRequest req = new PhaseDiagramRequest();
 *   req.setTdbFilePath("sgte.tdb");
 *   req.setElements(List.of("Ti", "Zr"));
 *   req.setPhases(List.of("LIQUID", "BCC_A2", "HCP_A3"));
 *   req.setDiagramType(DiagramType.MAP);
 *   req.addAxis(new AxisConfig("T / K",  AxisConfig.Type.TEMPERATURE,  800, 2000, 10));
 *   req.addAxis(new AxisConfig("x(Zr)", 1, 0.0, 1.0, 0.01));
 *   req.setFixedP(101325);
 *   req.setStartComposition(new double[]{0.5, 0.5});
 * </pre>
 *
 * <b>STEP scan at fixed composition:</b>
 * <pre>
 *   req.setDiagramType(DiagramType.STEP);
 *   req.addAxis(new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 500, 1800, 5));
 *   req.setFixedP(101325);
 *   req.setStartComposition(new double[]{0.3, 0.7});
 * </pre>
 */
public class PhaseDiagramRequest {

    /** Calculation type: one axis (STEP) or two axes (MAP). */
    public enum DiagramType { STEP, MAP }

    // ── Required fields ───────────────────────────────────────────────

    private String           tdbFilePath;
    private List<String>     elements  = new ArrayList<>();
    private List<String>     phases    = new ArrayList<>();
    private DiagramType      diagramType = DiagramType.MAP;

    /** Axis configurations; 1 entry for STEP, 2 entries for MAP. */
    private List<AxisConfig> axes      = new ArrayList<>();

    // ── Fixed conditions ─────────────────────────────────────────────

    /** Fixed pressure (Pa). Default: 101 325 Pa (1 atm). */
    private double fixedP = 101_325.0;

    /**
     * Fixed temperature (K) — used only when T is not a diagram axis.
     * Ignored for MAP diagrams with a TEMPERATURE axis.
     */
    private double fixedT = 1000.0;

    /**
     * Overall composition (mole fractions, length = elements.size()).
     * Used as the initial composition for diagram scanning.
     * For MAP with a COMPOSITION axis, this provides a starting point;
     * the axis value overrides the corresponding component at each step.
     */
    private double[] startComposition;

    // ── Getters / setters ────────────────────────────────────────────

    public String getTdbFilePath()                  { return tdbFilePath; }
    public void   setTdbFilePath(String p)          { this.tdbFilePath = p; }

    public List<String> getElements()               { return elements; }
    public void         setElements(List<String> e) { this.elements = new ArrayList<>(e); }

    public List<String> getPhases()                 { return phases; }
    public void         setPhases(List<String> p)   { this.phases = new ArrayList<>(p); }

    public DiagramType getDiagramType()             { return diagramType; }
    public void        setDiagramType(DiagramType t){ this.diagramType = t; }

    public List<AxisConfig> getAxes()               { return axes; }
    public void addAxis(AxisConfig axis)             { this.axes.add(axis); }
    public void setAxes(List<AxisConfig> axes)       { this.axes = new ArrayList<>(axes); }

    public double getFixedP()                       { return fixedP; }
    public void   setFixedP(double p)               { this.fixedP = p; }

    public double getFixedT()                       { return fixedT; }
    public void   setFixedT(double t)               { this.fixedT = t; }

    public double[] getStartComposition()           { return startComposition == null
                                                             ? null
                                                             : startComposition.clone(); }
    public void setStartComposition(double[] c)     { this.startComposition = c.clone(); }

    // ── Convenience: build AxisConfig[] ─────────────────────────────

    /** Returns the axis list as an array for use by {@link DiagramTracer}. */
    public AxisConfig[] axisArray() {
        return axes.toArray(new AxisConfig[0]);
    }

    /**
     * Build the starting axis-value vector from the current axis configs.
     * Sets each axis to its minimum value as the default start.
     */
    public double[] startAxisValues() {
        double[] vals = new double[axes.size()];
        for (int i = 0; i < axes.size(); i++) vals[i] = axes.get(i).min;
        return vals;
    }
}
