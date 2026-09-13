package ui.api.dto;

import java.util.List;

/** Request body for {@code POST /sessions/{id}/phases}. */
public final class PhasesRequest {
    public String tdbFilePath;
    public List<String> elements;
}
