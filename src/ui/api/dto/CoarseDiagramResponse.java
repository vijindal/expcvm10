package ui.api.dto;

import ui.result.CoarseDiagramResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire-format DTO for {@link CoarseDiagramResult}. A dedicated DTO is used
 * instead of serializing {@code CoarseDiagramResult} directly so the wire
 * format is stable and independent of internal field layout — same
 * convention as {@link EquilibriumResponse} and {@link PhaseDiagramResponse}.
 */
public final class CoarseDiagramResponse {

    public final String[] axisNames;
    public final double[] axisMin;
    public final double[] axisMax;
    public final boolean complete;
    public final String message;
    public final List<GridPointEntry> points;

    public CoarseDiagramResponse(CoarseDiagramResult result) {
        this.axisNames = result.getAxisNames();
        this.axisMin = result.getAxisMin();
        this.axisMax = result.getAxisMax();
        this.complete = result.isComplete();
        this.message = result.getMessage();
        this.points = toPointEntries(result.getPoints());
    }

    private static List<GridPointEntry> toPointEntries(List<CoarseDiagramResult.GridPoint> points) {
        List<GridPointEntry> entries = new ArrayList<>();
        for (CoarseDiagramResult.GridPoint point : points) {
            entries.add(new GridPointEntry(point));
        }
        return entries;
    }

    public static final class GridPointEntry {
        public final double axisXValue;
        public final double axisYValue;
        public final List<String> stablePhases;
        public final boolean converged;

        public GridPointEntry(CoarseDiagramResult.GridPoint point) {
            this.axisXValue = point.axisXValue;
            this.axisYValue = point.axisYValue;
            this.stablePhases = point.stablePhases;
            this.converged = point.converged;
        }
    }
}
