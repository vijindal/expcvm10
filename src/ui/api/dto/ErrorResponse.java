package ui.api.dto;

/** Structured JSON error body -- never a raw stack trace to the client. */
public final class ErrorResponse {
    public final String error;

    public ErrorResponse(String error) {
        this.error = error;
    }
}
