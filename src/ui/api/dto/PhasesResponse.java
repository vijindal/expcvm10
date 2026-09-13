package ui.api.dto;

import java.util.List;

/** Response body for {@code POST /sessions/{id}/phases}. */
public final class PhasesResponse {
    public final List<String> phases;

    public PhasesResponse(List<String> phases) {
        this.phases = phases;
    }
}
