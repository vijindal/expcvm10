package ui.request;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Request DTO for a property scan calculation (STEP or MAP).
 *
 * STEP: sweep one variable (T or X), fixed other conditions → line of property values.
 * MAP : sweep two variables (T and X) on a grid → 2D property surface + iso-value lines.
 */
public class PropertyScanRequest {

    public enum ScanType { STEP, MAP }
    public enum AxisType { TEMPERATURE, COMPOSITION }

    private ScanType scanType = ScanType.STEP;

    private String tdbFilePath;
    private List<String> elements  = new ArrayList<>();
    private List<String> phases    = new ArrayList<>();
    private String       method    = "HM"; // HM, Gm, G

    // Axis 0 — always the primary scan variable
    private AxisType axis0Type  = AxisType.TEMPERATURE;
    private double   axis0Min   = 500.0;
    private double   axis0Max   = 2000.0;
    private double   axis0Step  = 50.0;

    // Axis 1 — MAP only (ignored for STEP)
    private AxisType axis1Type  = AxisType.COMPOSITION;
    private double   axis1Min   = 0.0;
    private double   axis1Max   = 1.0;
    private double   axis1Step  = 0.05;

    // Fixed conditions
    private double fixedP = 101_325.0;
    /** Fixed T (K) — used when axis0=COMPOSITION and scanType=STEP */
    private double fixedT = 1000.0;
    /** Fixed mole fraction of component 2 — used when axis0=TEMPERATURE and scanType=STEP */
    private double fixedX = 0.5;

    /** Optional progress callback; called from background thread after each computed point. */
    private transient Consumer<String> progressCallback;

    // ── Getters / Setters ──────────────────────────────────────────────

    public ScanType getScanType()              { return scanType; }
    public void     setScanType(ScanType t)    { this.scanType = t; }

    public String        getTdbFilePath()      { return tdbFilePath; }
    public void          setTdbFilePath(String p) { this.tdbFilePath = p; }

    public List<String>  getElements()         { return elements; }
    public void          setElements(List<String> e) { this.elements = new ArrayList<>(e); }

    public List<String>  getPhases()           { return phases; }
    public void          setPhases(List<String> p)  { this.phases = new ArrayList<>(p); }

    public String        getMethod()           { return method; }
    public void          setMethod(String m)   { this.method = m; }

    public AxisType getAxis0Type()             { return axis0Type; }
    public void     setAxis0Type(AxisType t)   { this.axis0Type = t; }
    public double   getAxis0Min()              { return axis0Min; }
    public void     setAxis0Min(double v)      { this.axis0Min = v; }
    public double   getAxis0Max()              { return axis0Max; }
    public void     setAxis0Max(double v)      { this.axis0Max = v; }
    public double   getAxis0Step()             { return axis0Step; }
    public void     setAxis0Step(double v)     { this.axis0Step = v; }

    public AxisType getAxis1Type()             { return axis1Type; }
    public void     setAxis1Type(AxisType t)   { this.axis1Type = t; }
    public double   getAxis1Min()              { return axis1Min; }
    public void     setAxis1Min(double v)      { this.axis1Min = v; }
    public double   getAxis1Max()              { return axis1Max; }
    public void     setAxis1Max(double v)      { this.axis1Max = v; }
    public double   getAxis1Step()             { return axis1Step; }
    public void     setAxis1Step(double v)     { this.axis1Step = v; }

    public double   getFixedP()               { return fixedP; }
    public void     setFixedP(double v)       { this.fixedP = v; }
    public double   getFixedT()               { return fixedT; }
    public void     setFixedT(double v)       { this.fixedT = v; }
    public double   getFixedX()               { return fixedX; }
    public void     setFixedX(double v)       { this.fixedX = v; }

    public Consumer<String> getProgressCallback()              { return progressCallback; }
    public void             setProgressCallback(Consumer<String> cb) { this.progressCallback = cb; }
}
