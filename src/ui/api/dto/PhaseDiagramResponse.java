package ui.api.dto;

import calc.diagram.PhaseDiagramResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire-format DTO for {@link PhaseDiagramResult}. A dedicated DTO is used
 * instead of serializing {@code PhaseDiagramResult} directly so the wire
 * format is stable and independent of internal field layout -- same
 * convention as {@link EquilibriumResponse} for {@code EquilibriumResult}.
 */
public final class PhaseDiagramResponse {

    public final String[] axisNames;
    public final double[] axisMin;
    public final double[] axisMax;
    public final boolean complete;
    public final String message;
    public final List<LineEntry> lines;
    public final List<NodeEntry> nodes;

    public PhaseDiagramResponse(PhaseDiagramResult result) {
        this.axisNames = result.getAxisNames();
        this.axisMin = result.getAxisMin();
        this.axisMax = result.getAxisMax();
        this.complete = result.isComplete();
        this.message = result.getMessage();
        this.lines = toLineEntries(result.getLines());
        this.nodes = toNodeEntries(result.getNodes());
    }

    private static List<LineEntry> toLineEntries(List<PhaseDiagramResult.LineSegment> lines) {
        List<LineEntry> entries = new ArrayList<>();
        for (PhaseDiagramResult.LineSegment line : lines) {
            entries.add(new LineEntry(line));
        }
        return entries;
    }

    private static List<NodeEntry> toNodeEntries(List<PhaseDiagramResult.NodePoint> nodes) {
        List<NodeEntry> entries = new ArrayList<>();
        for (PhaseDiagramResult.NodePoint node : nodes) {
            entries.add(new NodeEntry(node));
        }
        return entries;
    }

    public static final class LineEntry {
        public final double[][] coords;
        public final String fixedPhase;
        public final List<String> stablePhases;
        public final double[] propertyValues;

        public LineEntry(PhaseDiagramResult.LineSegment line) {
            this.coords = line.coords.toArray(new double[0][]);
            this.fixedPhase = line.fixedPhase;
            this.stablePhases = line.stablePhases;
            this.propertyValues = line.propertyValues;
        }
    }

    public static final class NodeEntry {
        public final double[] axisValues;
        public final List<String> stablePhases;
        public final String type;

        public NodeEntry(PhaseDiagramResult.NodePoint node) {
            this.axisValues = node.axisValues;
            this.stablePhases = node.stablePhases;
            this.type = node.type.name();
        }
    }
}
