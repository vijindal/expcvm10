package ui.request;

import calc.diagram.AxisConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

    /**
     * Calculation type: one axis (STEP), two axes with ZPF boundary
     * tracing (MAP), or two axes sampled independently as a coarse
     * scatter/dot diagram (COARSE -- see {@link #ternary}).
     */
    public enum DiagramType { STEP, MAP, COARSE }

    // ── Required fields ───────────────────────────────────────────────

    private String           tdbFilePath;
    private List<String>     elements  = new ArrayList<>();
    private List<String>     phases    = new ArrayList<>();
    private DiagramType      diagramType = DiagramType.MAP;

    /** Axis configurations; 1 entry for STEP, 2 entries for MAP/COARSE. */
    private List<AxisConfig> axes      = new ArrayList<>();

    /**
     * COARSE only: {@code true} selects the ternary case (both axes are
     * COMPOSITION axes over two different components, the third implied
     * by the simplex constraint); {@code false} selects the binary case
     * (one axis is typically TEMPERATURE). Ignored for STEP/MAP.
     */
    private boolean ternary = false;

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

    /**
     * Per-axis starting value, same length/order as {@link #axes}. Null
     * (or a null entry) falls back to that axis's own {@code min} --
     * {@link #startAxisValues()}'s previous, only behavior. Set this when
     * the diagram should start its stable-set search from somewhere other
     * than an axis's lower bound, e.g. a MAP's T-axis starting at a
     * user-chosen temperature instead of always the range floor.
     */
    private double[] startAxisValues;

    /**
     * Optional progress callback for COARSE diagrams (ignored by
     * STEP/MAP): called from the calculation's background thread after
     * each completed grid row -- see {@code CoarseDiagramTracer}. Not
     * serialized; set per-request by a caller that wants live feedback
     * (e.g. a GUI worker streaming rows into a log panel).
     */
    private transient Consumer<String> progressCallback;

    // ── Getters / setters ────────────────────────────────────────────

    public String getTdbFilePath()                  { return tdbFilePath; }
    public void   setTdbFilePath(String p)          { this.tdbFilePath = p; }

    public List<String> getElements()               { return elements; }
    public void         setElements(List<String> e) { this.elements = new ArrayList<>(e); }

    public List<String> getPhases()                 { return phases; }
    public void         setPhases(List<String> p)   { this.phases = new ArrayList<>(p); }

    public DiagramType getDiagramType()             { return diagramType; }
    public void        setDiagramType(DiagramType t){ this.diagramType = t; }

    public boolean isTernary()                      { return ternary; }
    public void    setTernary(boolean t)            { this.ternary = t; }

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

    public void setStartAxisValue(int axisIndex, double value) {
        if (startAxisValues == null || startAxisValues.length != axes.size()) {
            startAxisValues = new double[axes.size()];
            java.util.Arrays.fill(startAxisValues, Double.NaN);
        }
        startAxisValues[axisIndex] = value;
    }

    public Consumer<String> getProgressCallback()              { return progressCallback; }
    public void             setProgressCallback(Consumer<String> cb) { this.progressCallback = cb; }

    // ── Convenience: build AxisConfig[] ─────────────────────────────

    /** Returns the axis list as an array for use by {@link DiagramTracer}. */
    public AxisConfig[] axisArray() {
        return axes.toArray(new AxisConfig[0]);
    }

    /**
     * Build the starting axis-value vector from the current axis configs:
     * each axis defaults to its own {@code min}, overridden per axis by
     * {@link #setStartAxisValue} where the caller supplied one.
     */
    public double[] startAxisValues() {
        double[] vals = new double[axes.size()];
        for (int i = 0; i < axes.size(); i++) {
            double override = startAxisValues != null && i < startAxisValues.length
                    ? startAxisValues[i] : Double.NaN;
            vals[i] = Double.isNaN(override) ? axes.get(i).min : override;
        }
        return vals;
    }
}
