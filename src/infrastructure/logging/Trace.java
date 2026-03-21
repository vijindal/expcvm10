package infrastructure.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Structured method-enter / method-exit tracing utility.
 *
 * Usage:
 * <pre>
 *   private static final Logger LOG = Logger.getLogger(MyClass.class.getName());
 *
 *   public void myMethod() {
 *       Trace.enter(LOG, AppLevel.ENGINE, "MyClass", "myMethod");
 *       // ... work ...
 *       Trace.exit(LOG, AppLevel.ENGINE, "MyClass", "myMethod");
 *   }
 * </pre>
 *
 * Produces log lines like:
 *   >> MyClass.myMethod [MyClass.java]
 *   << MyClass.myMethod [MyClass.java]
 *
 * For timed exits:
 *   Trace.exit(LOG, AppLevel.ENGINE, "MyClass", "myMethod", t0);
 *   << MyClass.myMethod [MyClass.java] (42ms)
 */
public final class Trace {

    private Trace() { }

    /** Log method entry: ">> ClassName.method [ClassName.java]" */
    public static void enter(Logger log, Level level, String className, String method) {
        if (log.isLoggable(level)) {
            log.log(level, ">> {0}.{1} [{0}.java]", new Object[]{className, method});
        }
    }

    /** Log method exit: "<< ClassName.method [ClassName.java]" */
    public static void exit(Logger log, Level level, String className, String method) {
        if (log.isLoggable(level)) {
            log.log(level, "<< {0}.{1} [{0}.java]", new Object[]{className, method});
        }
    }

    /** Log method exit with elapsed time: "<< ClassName.method [ClassName.java] (42ms)" */
    public static void exit(Logger log, Level level, String className, String method, long startNanos) {
        if (log.isLoggable(level)) {
            long ms = (System.nanoTime() - startNanos) / 1_000_000;
            log.log(level, "<< {0}.{1} [{0}.java] ({2}ms)", new Object[]{className, method, ms});
        }
    }

    /** Log a result/value message at the given level. */
    public static void result(Logger log, Level level, String message) {
        if (log.isLoggable(level)) {
            log.log(level, "   " + message);
        }
    }
}
