package ui.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output DTO for a coarse binary/ternary phase diagram calculation:
 * an independent equilibrium sample at each point of a 2D grid, colored
 * (by the GUI) per its resulting stable-phase-set.
 *
 * <p>Deliberately not {@link PhaseDiagramResult}: that DTO's
 * {@code LineSegment} implies an ordered curve through consecutive
 * points, which does not fit a grid of independent samples where each
 * point is computed and marked (converged or not) on its own -- see
 * {@code calc.diagram.CoarseDiagramTracer}.
 */
public final class CoarseDiagramResult {

    // ── Axis metadata ─────────────────────────────────────────────────

    private final String[] axisNames;
    private final double[] axisMin;
    private final double[] axisMax;

    // ── Diagram content ───────────────────────────────────────────────

    private final List<GridPoint> points = new ArrayList<>();

    /** Whether every sampled grid point converged. */
    private boolean complete = true;

    /** Human-readable status message. */
    private String message = "";

    public CoarseDiagramResult(String[] axisNames, double[] axisMin, double[] axisMax) {
        this.axisNames = axisNames.clone();
        this.axisMin   = axisMin.clone();
        this.axisMax   = axisMax.clone();
    }

    // ── Builder methods ───────────────────────────────────────────────

    public void addPoint(GridPoint point) { points.add(point); }

    // ── Accessors ─────────────────────────────────────────────────────

    public String[] getAxisNames()                  { return axisNames.clone(); }
    public double[] getAxisMin()                    { return axisMin.clone(); }
    public double[] getAxisMax()                    { return axisMax.clone(); }
    public List<GridPoint> getPoints()              { return Collections.unmodifiableList(points); }
    public boolean          isComplete()            { return complete; }
    public void             setComplete(boolean c)  { this.complete = c; }
    public String           getMessage()            { return message; }
    public void             setMessage(String m)    { this.message = m; }

    // ------------------------------------------------------------------
    // Inner: GridPoint
    // ------------------------------------------------------------------

    /**
     * One independently-sampled grid point: its two axis coordinates,
     * the resulting stable phase names (empty if not converged), and
     * whether the solve converged.
     *
     * <p>Deliberately does not carry the raw {@code EquilibriumResult} --
     * keeps a dense grid (e.g. 100x100 points) lightweight; a coarse/
     * scatter diagram's own purpose only needs the stable-phase-set
     * summary, not full amounts/G/site-fractions per point.
     */
    public static final class GridPoint {

        public final double axisXValue;
        public final double axisYValue;
        public final List<String> stablePhases;
        public final boolean converged;

        public GridPoint(double axisXValue, double axisYValue,
                          List<String> stablePhases, boolean converged) {
            this.axisXValue   = axisXValue;
            this.axisYValue   = axisYValue;
            this.stablePhases = Collections.unmodifiableList(new ArrayList<>(stablePhases));
            this.converged    = converged;
        }
    }
}
