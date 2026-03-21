package service;

import domain.ResultPort;

import java.io.IOException;

/**
 * Use case: export fitted database to TDB format.
 * Track A — Assessment workflow.
 */
public class ExportDatabaseUseCase {

    private final ResultPort resultPort;

    public ExportDatabaseUseCase(ResultPort resultPort) {
        this.resultPort = resultPort;
    }

    /**
     * Export database content to the result output.
     * Placeholder — implementation will grow as TDB export is fleshed out.
     */
    public void execute(String content) throws IOException {
        resultPort.writeLine(content);
        resultPort.close();
    }
}
