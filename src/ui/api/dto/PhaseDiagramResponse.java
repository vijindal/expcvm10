package ui.api.dto;

import calc.diagram.DiagramLine;
import calc.diagram.DiagramNode;
import calc.diagram.PhaseDiagram;
import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire-format DTO for {@link PhaseDiagram}. {@code PhaseDiagram} is not
 * serialized directly: {@code DiagramNode} holds {@code DiagramExit}s that
 * reference their {@code parentNode} back, a cycle that would make a
 * reflection-based JSON serializer (Gson) recurse forever. This DTO
 * flattens nodes and lines into plain, cycle-free data, referencing nodes
 * by id rather than embedding them.
 */
public final class PhaseDiagramResponse {

    public final String[] axisNames;
    public final double[] axisMin;
    public final double[] axisMax;
    public final List<NodeEntry> nodes;
    public final List<LineEntry> lines;

    public PhaseDiagramResponse(PhaseDiagram diagram) {
        this.axisNames = diagram.axisNames;
        this.axisMin = diagram.axisMin;
        this.axisMax = diagram.axisMax;

        this.nodes = new ArrayList<>();
        for (DiagramNode node : diagram.getNodes()) {
            nodes.add(new NodeEntry(node));
        }

        this.lines = new ArrayList<>();
        for (DiagramLine line : diagram.getLines()) {
            lines.add(new LineEntry(line));
        }
    }

    public static final class NodeEntry {
        public final int id;
        public final double[] axisValues;
        public final List<String> stablePhases;

        public NodeEntry(DiagramNode node) {
            this.id = node.id;
            this.axisValues = node.axisValues;
            this.stablePhases = new ArrayList<>();
            for (EquilibriumResult.PhaseResult pr : node.equilibrium.getStablePhases()) {
                stablePhases.add(pr.phaseName);
            }
        }
    }

    public static final class LineEntry {
        public final Integer startNodeId;
        public final Integer endNodeId;
        public final String fixedPhase;
        public final List<String> stablePhaseSet;
        public final List<double[]> axisCoords;

        public LineEntry(DiagramLine line) {
            this.startNodeId = line.startNode != null ? line.startNode.id : null;
            this.endNodeId = line.endNode != null ? line.endNode.id : null;
            this.fixedPhase = line.fixedPhase;
            this.stablePhaseSet = new ArrayList<>(line.stablePhaseSet);
            this.axisCoords = line.getAxisCoords();
        }
    }
}
