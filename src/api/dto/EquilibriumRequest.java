package api.dto;

/** Request body for {@code POST /sessions/{id}/calculations/equilibrium}. */
public final class EquilibriumRequest {
    public double T;
    public double P;
    public double[] composition;
}
