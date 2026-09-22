package ui.result;

import java.util.List;

/**
 * UI-layer wrapper for phase diagram results.
 *
 * <p>This wrapper allows UI components to receive phase diagram results
 * without directly importing from calc.diagram. The actual rendering-ready
 * {@link calc.diagram.PhaseDiagramResult} is accessed through ApplicationLayer only.
 *
 * <p>Currently a simple facade; future versions may add UI-specific
 * transformations or caching.
 */
public class PhaseDiagramResultWrapper {

    private final String[] axisNames;
    private final double[] axisMin;
    private final double[] axisMax;
    private final List<LineSegmentData> lines;
    private final List<NodePointData> nodes;
    private final boolean complete;
    private final String message;

    public PhaseDiagramResultWrapper(String[] axisNames, double[] axisMin, double[] axisMax,
                                      List<LineSegmentData> lines, List<NodePointData> nodes,
                                      boolean complete, String message) {
        this.axisNames = axisNames;
        this.axisMin = axisMin;
        this.axisMax = axisMax;
        this.lines = lines;
        this.nodes = nodes;
        this.complete = complete;
        this.message = message;
    }

    public String[] getAxisNames() { return axisNames; }
    public double[] getAxisMin() { return axisMin; }
    public double[] getAxisMax() { return axisMax; }
    public List<LineSegmentData> getLines() { return lines; }
    public List<NodePointData> getNodes() { return nodes; }
    public boolean isComplete() { return complete; }
    public String getMessage() { return message; }

    /** UI-safe representation of a line segment */
    public static class LineSegmentData {
        public final List<double[]> coords;
        public final String fixedPhase;
        public final List<String> stablePhases;

        public LineSegmentData(List<double[]> coords, String fixedPhase, List<String> stablePhases) {
            this.coords = coords;
            this.fixedPhase = fixedPhase;
            this.stablePhases = stablePhases;
        }

        public String label() {
            String stable = String.join("+", stablePhases);
            return fixedPhase != null ? stable + " / " + fixedPhase + "=0" : stable;
        }

        public int size() { return coords.size(); }
    }

    /** UI-safe representation of a node point */
    public static class NodePointData {
        public enum Type {
            CROSSING,
            INVARIANT,
            BOUNDARY
        }

        public final double[] axisValues;
        public final List<String> stablePhases;
        public final Type type;

        public NodePointData(double[] axisValues, List<String> stablePhases, Type type) {
            this.axisValues = axisValues;
            this.stablePhases = stablePhases;
            this.type = type;
        }
    }
}
