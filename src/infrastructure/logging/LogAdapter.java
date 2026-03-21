package infrastructure.logging;

import utils.io.Print;

/**
 * Logging adapter that routes through the existing Print utility.
 * Provides a single point of control for log level and output destination.
 */
public class LogAdapter {

    private final int logLevel;

    public LogAdapter(int logLevel) {
        this.logLevel = logLevel;
    }

    public void info(String message) {
        Print.f(message, 0);
    }

    public void debug(String message) {
        Print.f(message, logLevel);
    }

    public void error(String message) {
        System.err.println(message);
    }
}
