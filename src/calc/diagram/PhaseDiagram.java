package calc.diagram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Container for a fully (or partially) traced phase diagram.
 *
 * <p>Accumulated by {@link DiagramTracer} (Algorithm B) as ZPF lines are
 * followed.  Provides the node and line collections needed for rendering
 * and for export to {@link ui.result.PhaseDiagramResult}.
 *
 * <h2>Axis convention</h2>
 * The diagram has 1 or 2 axes (STEP vs MAP).  Axis 0 is the primary axis
 * (typically T for a T-x diagram), axis 1 is the secondary axis (x_B).
 * {@link #axisNames}, {@link #axisMin}, {@link #axisMax} carry the labels
 * and ranges.
 */
public class PhaseDiagram {

    /** Human-readable axis labels (e.g. "T / K", "x(B)"). */
    public final String[] axisNames;

    /** Lower bound of each axis. */
    public final double[] axisMin;

    /** Upper bound of each axis. */
    public final double[] axisMax;

    private final List<DiagramNode> nodes = new ArrayList<>();
    private final List<DiagramLine> lines = new ArrayList<>();

    private final AtomicInteger nodeIdSeq = new AtomicInteger(0);

    public PhaseDiagram(String[] axisNames, double[] axisMin, double[] axisMax) {
        if (axisNames.length != axisMin.length || axisNames.length != axisMax.length) {
            throw new IllegalArgumentException("axisNames, axisMin, axisMax must have equal length");
        }
        this.axisNames = axisNames.clone();
        this.axisMin   = axisMin.clone();
        this.axisMax   = axisMax.clone();
    }

    // ------------------------------------------------------------------
    // Node management
    // ------------------------------------------------------------------

    /**
     * Create and register a new node.
     *
     * @param equilibrium  equilibrium at the node
     * @param axisValues   axis coordinates (length = numAxes)
     * @return the new node
     */
    public DiagramNode addNode(contracts.EquilibriumResult equilibrium,
                               double[] axisValues) {
        DiagramNode node = new DiagramNode(nodeIdSeq.getAndIncrement(),
                                           equilibrium, axisValues);
        nodes.add(node);
        return node;
    }

    /** Returns all nodes (immutable view). */
    public List<DiagramNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    // ------------------------------------------------------------------
    // Line management
    // ------------------------------------------------------------------

    /** Register a completed (or in-progress) line. */
    public void addLine(DiagramLine line) {
        lines.add(line);
    }

    /** Returns all lines (immutable view). */
    public List<DiagramLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    // ------------------------------------------------------------------
    // Exit traversal support (Algorithm B)
    // ------------------------------------------------------------------

    /**
     * Returns the next unvisited exit across all nodes, or {@code null}
     * when the diagram is fully traced.
     */
    public DiagramExit nextUnvisitedExit() {
        for (DiagramNode node : nodes) {
            DiagramExit exit = node.nextUnvisitedExit();
            if (exit != null) return exit;
        }
        return null;
    }

    /** Number of diagram axes. */
    public int numAxes() {
        return axisNames.length;
    }

    // ------------------------------------------------------------------
    // Plot-ready line segments for rendering
    // ------------------------------------------------------------------

    /**
     * Returns all sampled line segments as a flat list of {@link PlotLine}
     * objects, ready for the GUI renderer.
     *
     * <p>Each {@link PlotLine} carries the axis-coordinate arrays for every
     * point on one {@link DiagramLine}, plus the phase set label.
     */
    public List<PlotLine> getPlotLines() {
        List<PlotLine> result = new ArrayList<>();
        for (DiagramLine dl : lines) {
            if (dl.size() < 2) continue;
            result.add(new PlotLine(
                    dl.getAxisCoords(),
                    dl.fixedPhase,
                    new ArrayList<>(dl.stablePhaseSet)));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Summary
    // ------------------------------------------------------------------

    @Override
    public String toString() {
        return "PhaseDiagram[nodes=" + nodes.size()
                + " lines=" + lines.size() + "]";
    }

    // ------------------------------------------------------------------
    // Inner: PlotLine (rendering DTO)
    // ------------------------------------------------------------------

    /**
     * A single renderable line segment: ordered axis-coordinate arrays
     * for every sampled point, plus labels.
     */
    public static final class PlotLine {
        /** Axis coordinates per point; {@code coords.get(k)[axis]}. */
        public final List<double[]> coords;

        /** The ZPF phase (zero amount along this line). */
        public final String fixedPhase;

        /** Phases stable along this line (for labelling). */
        public final List<String> stablePhases;

        public PlotLine(List<double[]> coords,
                        String fixedPhase,
                        List<String> stablePhases) {
            this.coords       = coords;
            this.fixedPhase   = fixedPhase;
            this.stablePhases = stablePhases;
        }
    }
}
