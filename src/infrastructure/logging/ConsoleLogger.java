package infrastructure.logging;

import domain.port.LoggingPort;
import utils.io.Print;

import java.util.logging.Logger;

/**
 * Infrastructure adapter for logging via the legacy Print utility.
 * Print.f() now also routes through JUL automatically.
 */
public class ConsoleLogger implements LoggingPort {

    private static final Logger LOG = Logger.getLogger(ConsoleLogger.class.getName());

    @Override
    public void log(String message, int level) {
        Print.f(message, level);
    }
}
