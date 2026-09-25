package ui.api.dto;

/** Request body for {@code POST /sessions/{id}/calculations/coarse-ternary}. */
public final class CoarseTernaryRequest {
    public PhaseDiagramRequest.AxisSpec axisI;
    public PhaseDiagramRequest.AxisSpec axisJ;
    public double fixedT;
    public double fixedP;
    public double[] composition;
}
