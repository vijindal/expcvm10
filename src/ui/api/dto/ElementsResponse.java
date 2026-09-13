package ui.api.dto;

import java.util.List;

/** Response body for {@code POST /sessions/{id}/elements}. */
public final class ElementsResponse {
    public final List<String> elements;

    public ElementsResponse(List<String> elements) {
        this.elements = elements;
    }
}
