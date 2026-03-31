package ui.result;

/**
 * Result DTO from a property scan (STEP or MAP).
 *
 * STEP: axis0Values[i] + stepValues[i] — property vs one variable.
 * MAP : axis0Values[j] (X) + axis1Values[i] (Y) + mapValues[i][j] — 2D property grid.
 */
public class PropertyScanResult {

    private double[] axis0Values;    // scan variable 0 (always present)
    private double[] axis1Values;    // scan variable 1 (MAP only; null for STEP)
    private double[] stepValues;     // STEP: property[i] at axis0Values[i]
    private double[][] mapValues;    // MAP: property[i][j] at (axis1Values[i], axis0Values[j])

    private String  axis0Label  = "Axis 0";
    private String  axis1Label  = "Axis 1";
    private String  propertyLabel = "Property";
    private String  method;

    private double  valueMin  = Double.NaN;
    private double  valueMax  = Double.NaN;

    private boolean success = true;
    private String  message = "";

    // ── Getters / Setters ──────────────────────────────────────────────

    public double[] getAxis0Values()          { return axis0Values; }
    public void     setAxis0Values(double[] v){ this.axis0Values = v; }

    public double[] getAxis1Values()          { return axis1Values; }
    public void     setAxis1Values(double[] v){ this.axis1Values = v; }

    public double[] getStepValues()           { return stepValues; }
    public void     setStepValues(double[] v) {
        this.stepValues = v;
        updateMinMax1D(v);
    }

    public double[][] getMapValues()          { return mapValues; }
    public void       setMapValues(double[][] v) {
        this.mapValues = v;
        updateMinMax2D(v);
    }

    public String  getAxis0Label()            { return axis0Label; }
    public void    setAxis0Label(String s)    { this.axis0Label = s; }

    public String  getAxis1Label()            { return axis1Label; }
    public void    setAxis1Label(String s)    { this.axis1Label = s; }

    public String  getPropertyLabel()         { return propertyLabel; }
    public void    setPropertyLabel(String s) { this.propertyLabel = s; }

    public String  getMethod()                { return method; }
    public void    setMethod(String m)        { this.method = m; }

    public double  getValueMin()              { return valueMin; }
    public double  getValueMax()              { return valueMax; }

    public boolean isSuccess()                { return success; }
    public void    setSuccess(boolean b)      { this.success = b; }

    public String  getMessage()               { return message; }
    public void    setMessage(String s)       { this.message = s; }

    // ── Helpers ────────────────────────────────────────────────────────

    private void updateMinMax1D(double[] v) {
        if (v == null) return;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double d : v) { if (!Double.isNaN(d) && !Double.isInfinite(d)) { lo = Math.min(lo, d); hi = Math.max(hi, d); } }
        if (lo <= hi) { valueMin = lo; valueMax = hi; }
    }

    private void updateMinMax2D(double[][] v) {
        if (v == null) return;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[] row : v) for (double d : row) {
            if (!Double.isNaN(d) && !Double.isInfinite(d)) { lo = Math.min(lo, d); hi = Math.max(hi, d); }
        }
        if (lo <= hi) { valueMin = lo; valueMax = hi; }
    }
}
