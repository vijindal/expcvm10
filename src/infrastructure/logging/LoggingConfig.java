package infrastructure.logging;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Central configuration for java.util.logging throughout the application.
 * Called once from the composition root (Main) to set up log levels,
 * handlers, and formatting for all packages.
 *
 * <p>Log level mapping from legacy numeric levels:
 * <pre>
 *   Legacy 0 → SEVERE  (critical / always shown)
 *   Legacy 1 → INFO    (normal operational messages)
 *   Legacy 2 → CONFIG  (configuration details)
 *   Legacy 3 → FINE    (method entry/exit)
 *   Legacy 4 → FINE    (constructor traces)
 *   Legacy 5 → FINER   (derivative calculations)
 *   Legacy 6 → FINEST  (low-level constructor / loop traces)
 *   Legacy 7 → FINEST  (DataPrinter internal)
 * </pre>
 */
public class LoggingConfig {

    private static boolean initialized = false;
    private static FileHandler fileHandler;

    /**
     * Initialise global logging. Safe to call multiple times (no-op after first).
     *
     * @param consoleLevel minimum level printed to stderr/stdout
     * @param logFilePath  if non-null, also log to this file
     * @param fileLevel    minimum level written to the file (ignored when logFilePath is null)
     */
    public static synchronized void init(Level consoleLevel, String logFilePath, Level fileLevel) {
        if (initialized) {
            return;
        }
        initialized = true;

        // Remove default JUL handlers on the root logger
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
        root.setLevel(Level.ALL);

        // --- Console handler ---
        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(consoleLevel);
        console.setFormatter(new CompactFormatter());
        root.addHandler(console);

        // --- File handler (optional) ---
        if (logFilePath != null) {
            try {
                fileHandler = new FileHandler(logFilePath, /* append */ true);
                fileHandler.setLevel(fileLevel);
                fileHandler.setFormatter(new CompactFormatter());
                root.addHandler(fileHandler);
            } catch (IOException e) {
                Logger.getLogger(LoggingConfig.class.getName())
                      .log(Level.WARNING, "Could not open log file: " + logFilePath, e);
            }
        }

        // --- Per-package levels using AppLevel hierarchy ---
        // L0-L1: controller + service → FLOW
        Logger.getLogger("presentation").setLevel(AppLevel.FLOW);
        Logger.getLogger("application").setLevel(AppLevel.FLOW);
        // L2: engine → ENGINE
        Logger.getLogger("calbince").setLevel(AppLevel.ENGINE);
        // L3: model → MODEL
        Logger.getLogger("phase").setLevel(AppLevel.MODEL);
        // L4: solver → SOLVER
        Logger.getLogger("phase.solution.cecvm").setLevel(AppLevel.SOLVER);
        // infrastructure + domain
        Logger.getLogger("infrastructure").setLevel(AppLevel.FLOW);
        Logger.getLogger("domain").setLevel(AppLevel.FLOW);
        Logger.getLogger("database").setLevel(AppLevel.RESULT);
    }

    /**
     * Convenience overload: console-only logging at INFO level.
     */
    public static void init() {
        init(Level.INFO, null, Level.ALL);
    }

    /**
     * Change the console log level at runtime (e.g. from a GUI control).
     */
    public static void setConsoleLevel(Level level) {
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setLevel(level);
            }
        }
    }

    /**
     * Change the level for a specific package at runtime.
     */
    public static void setPackageLevel(String packageName, Level level) {
        Logger.getLogger(packageName).setLevel(level);
    }

    /**
     * Flush and close the file handler (call at shutdown).
     */
    public static synchronized void shutdown() {
        if (fileHandler != null) {
            fileHandler.flush();
            fileHandler.close();
            fileHandler = null;
        }
    }

    /**
     * Convert legacy numeric log level (0-7) to AppLevel.
     */
    public static Level fromLegacyLevel(int legacyLevel) {
        switch (legacyLevel) {
            case 0:  return AppLevel.ERROR;
            case 1:  return AppLevel.RESULT;
            case 2:  return AppLevel.RESULT;
            case 3:
            case 4:  return AppLevel.ENGINE;
            case 5:  return AppLevel.MODEL;
            case 6:  return AppLevel.SOLVER;
            default: return AppLevel.SOLVER;
        }
    }

    /**
     * Set level on ALL handlers attached to the root logger (console, file, GUI).
     */
    public static void setAllHandlerLevels(Level level) {
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) {
            h.setLevel(level);
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Compact single-line formatter for readable console and file output.
     * Format: {@code HH:mm:ss.SSS LEVEL [short-class.method] message}
     */
    public static class CompactFormatter extends Formatter {
        private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss.SSS");

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder(160);
            sb.append(TIME_FMT.format(new Date(record.getMillis())));
            sb.append(' ');
            sb.append(padLevel(record.getLevel()));
            sb.append(" [");
            sb.append(shortClassName(record.getSourceClassName()));
            String method = record.getSourceMethodName();
            if (method != null) {
                sb.append('.').append(method);
            }
            sb.append("] ");
            sb.append(formatMessage(record));
            sb.append(System.lineSeparator());
            if (record.getThrown() != null) {
                java.io.StringWriter sw = new java.io.StringWriter();
                record.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                sb.append(sw.toString());
            }
            return sb.toString();
        }

        private static String padLevel(Level level) {
            String name = level.getName();
            // Pad to 7 chars for alignment
            while (name.length() < 7) {
                name = name + " ";
            }
            return name;
        }

        private static String shortClassName(String fqcn) {
            if (fqcn == null) return "?";
            int dot = fqcn.lastIndexOf('.');
            return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
        }
    }
}
