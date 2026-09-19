package session.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a phase diagram calculation.
 *
 * <p>Contains rendering-ready data: lines (ZPF boundaries) and nodes (phase change points).
 * Lives in the session layer so both UI and calculation layers can access it without
 * creating cross-layer dependencies.
 */
public final class PhaseDiagramResult {

    private final String[] axisNames;
    private final double[] axisMin;
    private final double[] axisMax;
    private final List<LineSegment> lines;
    private final List<NodePoint> nodes;
    private boolean complete;
    private String message;

    public PhaseDiagramResult(String[] axisNames, double[] axisMin, double[] axisMax) {
        this.axisNames = axisNames.clone();
        this.axisMin = axisMin.clone();
        this.axisMax = axisMax.clone();
        this.lines = new ArrayList<>();
        this.nodes = new ArrayList<>();
        this.complete = true;
        this.message = "";
    }

    public void addLine(LineSegment line) { lines.add(line); }
    public void addNode(NodePoint node) { nodes.add(node); }

    public String[] getAxisNames() { return axisNames.clone(); }
    public double[] getAxisMin() { return axisMin.clone(); }
    public double[] getAxisMax() { return axisMax.clone(); }
    public List<LineSegment> getLines() { return Collections.unmodifiableList(lines); }
    public List<NodePoint> getNodes() { return Collections.unmodifiableList(nodes); }
    public boolean isComplete() { return complete; }
    public void setComplete(boolean c) { this.complete = c; }
    public String getMessage() { return message; }
    public void setMessage(String m) { this.message = m; }
    public int numAxes() { return axisNames.length; }

    public static final class LineSegment {
        public final List<double[]> coords;
        public final String fixedPhase;
        public final List<String> stablePhases;

        public LineSegment(List<double[]> coords, String fixedPhase, List<String> stablePhases) {
            List<double[]> copy = new ArrayList<>(coords.size());
            for (double[] c : coords) copy.add(c.clone());
            this.coords = Collections.unmodifiableList(copy);
            this.fixedPhase = fixedPhase;
            this.stablePhases = Collections.unmodifiableList(new ArrayList<>(stablePhases));
        }

        public String label() {
            String stable = String.join("+", stablePhases);
            return fixedPhase != null ? stable + " / " + fixedPhase + "=0" : stable;
        }

        public int size() { return coords.size(); }
    }

    public static final class NodePoint {
        public enum Type {
            CROSSING,
            INVARIANT,
            BOUNDARY
        }

        public final double[] axisValues;
        public final List<String> stablePhases;
        public final Type type;

        public NodePoint(double[] axisValues, List<String> stablePhases, Type type) {
            this.axisValues = axisValues.clone();
            this.stablePhases = Collections.unmodifiableList(new ArrayList<>(stablePhases));
            this.type = type;
        }
    }
}
