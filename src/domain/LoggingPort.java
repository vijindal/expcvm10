package domain;

/**
 * Port interface for application logging.
 * Infrastructure layer provides the concrete implementation.
 */
public interface LoggingPort {

    /**
     * Log a message with the specified log level.
     * @param message the message to log
     * @param level the log level (0=critical, higher=more verbose)
     */
    void log(String message, int level);
}
