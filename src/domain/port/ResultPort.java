package domain.port;

import java.io.IOException;

/**
 * Port interface for exporting/writing results and database files.
 * Infrastructure layer provides the concrete implementation.
 */
public interface ResultPort {

    /**
     * Write a line of text to the output destination.
     */
    void writeLine(String line) throws IOException;

    /**
     * Flush and close the output destination.
     */
    void close() throws IOException;
}
