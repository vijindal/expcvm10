package ui.api.dto;

/** Request body for {@code POST /sessions/{id}/calculations/coarse-binary}. */
public final class CoarseBinaryRequest {
    public PhaseDiagramRequest.AxisSpec axisX;
    public PhaseDiagramRequest.AxisSpec axisY;
    public double fixedT;
    public double fixedP;
    public double[] composition;
}
