package ui.api.dto;

import java.util.List;

/** Request body for {@code PUT /sessions/{id}/model}. */
public final class SetModelRequest {
    public String tdbFilePath;
    public List<String> elements;
    public List<String> phases;
}
