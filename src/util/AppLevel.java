package util;

import java.util.logging.Level;

/**
 * Custom log levels replacing the standard 7 JUL levels.
 * Mapped to the method-call hierarchy of the thermodynamic calculation engine.
 *
 * <pre>
 * Level     int   Meaning
 * --------- ----- -------------------------------------------------------
 * ERROR     1000  Exceptions, crashes, unrecoverable failures
 * WARN       900  Recoverable issues, questionable input values
 * RESULT     800  Key thermodynamic outputs (G, Gm, H, S), chi², convergence
 * FLOW       700  Enter/exit of L0-L1 methods (Controller + Service)
 * ENGINE     500  Enter/exit of L2 methods (calculate, CalModel, OptMrq, Methods) + engine results
 * MODEL      400  Enter/exit of L3 methods (GibbsModel, RK, STCOMP) + model intermediates
 * SOLVER     300  Enter/exit of L4 methods (CVM solvers, Newton iterations)
 * </pre>
 *
 * Setting the level to ENGINE shows ERROR, WARN, RESULT, FLOW, and ENGINE
 * but hides MODEL and SOLVER noise.
 */
public final class AppLevel extends Level {

    public static final Level ERROR  = Level.SEVERE;    // 1000
    public static final Level WARN   = Level.WARNING;   // 900
    public static final Level RESULT = new AppLevel("RESULT", 800);
    public static final Level FLOW   = new AppLevel("FLOW",   700);
    public static final Level ENGINE = new AppLevel("ENGINE", 500);
    public static final Level MODEL  = new AppLevel("MODEL",  400);
    public static final Level SOLVER = new AppLevel("SOLVER", 300);

    private AppLevel(String name, int value) {
        super(name, value);
    }

    /** Parse a level name, recognising both custom and standard names. */
    public static Level parse(String name) {
        if (name == null) return RESULT;
        switch (name.toUpperCase()) {
            case "ERROR":  return ERROR;
            case "WARN":   return WARN;
            case "RESULT": return RESULT;
            case "FLOW":   return FLOW;
            case "ENGINE": return ENGINE;
            case "MODEL":  return MODEL;
            case "SOLVER": return SOLVER;
            case "ALL":    return Level.ALL;
            case "OFF":    return Level.OFF;
            default:       return Level.parse(name);  // fallback to JUL
        }
    }
}
