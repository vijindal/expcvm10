package ui.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output DTO for a phase diagram calculation.
 *
 * <p>Contains rendering-ready data:
 * <ul>
 *   <li>{@link LineSegment} — a series of (axis0, axis1) coordinate pairs
 *       forming one ZPF boundary line, labelled with the stable phase set.</li>
 *   <li>{@link NodePoint} — a single point where phase sets change
 *       (eutectic, peritectic, or simple crossing), labelled by type.</li>
 * </ul>
 *
 * <p>The GUI renderer ({@code PhaseDiagramPanel}) consumes this DTO directly.
 */
public class PhaseDiagramResult {

    // ── Axis metadata ─────────────────────────────────────────────────

    private final String[] axisNames;
    private final double[] axisMin;
    private final double[] axisMax;

    // ── Diagram content ───────────────────────────────────────────────

    private final List<LineSegment> lines = new ArrayList<>();
    private final List<NodePoint>   nodes = new ArrayList<>();

    /** Whether the calculation completed without hitting any iteration limits. */
    private boolean complete = true;

    /** Human-readable status message. */
    private String message = "";

    public PhaseDiagramResult(String[] axisNames, double[] axisMin, double[] axisMax) {
        this.axisNames = axisNames.clone();
        this.axisMin   = axisMin.clone();
        this.axisMax   = axisMax.clone();
    }

    // ── Builder methods ───────────────────────────────────────────────

    public void addLine(LineSegment line) { lines.add(line); }
    public void addNode(NodePoint   node) { nodes.add(node); }

    // ── Accessors ─────────────────────────────────────────────────────

    public String[] getAxisNames()                    { return axisNames.clone(); }
    public double[] getAxisMin()                      { return axisMin.clone(); }
    public double[] getAxisMax()                      { return axisMax.clone(); }
    public List<LineSegment> getLines()               { return Collections.unmodifiableList(lines); }
    public List<NodePoint>   getNodes()               { return Collections.unmodifiableList(nodes); }
    public boolean           isComplete()             { return complete; }
    public void              setComplete(boolean c)   { this.complete = c; }
    public String            getMessage()             { return message; }
    public void              setMessage(String m)     { this.message = m; }

    /** Number of diagram axes (1 for STEP, 2 for MAP). */
    public int numAxes() { return axisNames.length; }

    // ------------------------------------------------------------------
    // Inner: LineSegment
    // ------------------------------------------------------------------

    /**
     * A single phase-boundary line: ordered sequence of axis-coordinate
     * arrays, the ZPF phase (held at zero amount), and the stable phase set.
     */
    public static final class LineSegment {

        /**
         * Axis coordinates for each sampled point along the line.
         * {@code coords.get(k)[i]} = value of axis i at point k.
         */
        public final List<double[]> coords;

        /**
         * The ZPF phase — the phase held at zero amount along this line.
         * {@code null} for STEP-mode lines that are not strict ZPF lines.
         */
        public final String fixedPhase;

        /** Names of the phases stable (amount > 0) along this line. */
        public final List<String> stablePhases;

        public LineSegment(List<double[]> coords,
                           String fixedPhase,
                           List<String> stablePhases) {
            // Deep-copy the coordinate list so caller can reuse its arrays
            List<double[]> copy = new ArrayList<>(coords.size());
            for (double[] c : coords) copy.add(c.clone());
            this.coords       = Collections.unmodifiableList(copy);
            this.fixedPhase   = fixedPhase;
            this.stablePhases = Collections.unmodifiableList(new ArrayList<>(stablePhases));
        }

        /** Convenience: label for rendering, e.g. "LIQUID+BCC_A2 / HCP_A3=0". */
        public String label() {
            String stable = String.join("+", stablePhases);
            return fixedPhase != null ? stable + " / " + fixedPhase + "=0" : stable;
        }

        /** Number of sampled points. */
        public int size() { return coords.size(); }
    }

    // ------------------------------------------------------------------
    // Inner: NodePoint
    // ------------------------------------------------------------------

    /**
     * A phase-change node: a single point with its axis coordinates and
     * a classification (CROSSING, INVARIANT, or BOUNDARY).
     */
    public static final class NodePoint {

        public enum Type {
            /** Normal two-ZPF-line crossing (f > 0). */
            CROSSING,
            /** Invariant equilibrium — eutectic, peritectic (f = 0). */
            INVARIANT,
            /** Axis-boundary endpoint of a ZPF line. */
            BOUNDARY
        }

        /** Axis coordinates of the node, length = numAxes. */
        public final double[] axisValues;

        /** Names of all stable phases at this node. */
        public final List<String> stablePhases;

        /** Classification of the node. */
        public final Type type;

        public NodePoint(double[] axisValues, List<String> stablePhases, Type type) {
            this.axisValues   = axisValues.clone();
            this.stablePhases = Collections.unmodifiableList(new ArrayList<>(stablePhases));
            this.type         = type;
        }
    }
}
