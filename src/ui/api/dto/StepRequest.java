package ui.api.dto;

/** Request body for {@code POST /sessions/{id}/calculations/step}. */
public final class StepRequest {
    /** The axis to walk -- same shape as one entry of {@link PhaseDiagramRequest#axes}. */
    public PhaseDiagramRequest.AxisSpec axis;
    public double fixedT;
    public double fixedP;
    public double[] composition;
}
