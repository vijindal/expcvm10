package util;

/** Single source of truth for all shared numerical constants. Contract sheet Part 4. */
public final class Constants {
    private Constants() {}

    // Algorithm A (EquilibriumSolver)
    public static final double MU_TOL               = 1e-6;
    public static final double Y_TOL                = 1e-6;
    public static final double DRIVING_FORCE_TOL    = 1e-6;   // unified for A and C1
    public static final double MIN_PHASE_AMOUNT     = 1e-10;
    public static final int    MAX_ITERATIONS       = 100;
    public static final int    MAX_PHASE_SET_RESETS = 5;
    public static final int    MAX_DAMPING_HALVINGS = 10;
    public static final double LAMBDA_FLOOR         = 1e-3;
    public static final double X_FLOOR              = 1e-9;

    // GridMinimizer
    public static final int    GRID_DENSITY         = 20;
    public static final double GRID_X_FLOOR         = 1e-6;

    // Algorithm C1 (LineStepper)
    public static final int    MAX_STEPS            = 1000;
    public static final int    MAX_RETRIES          = 3;
    public static final double STEP_MIN_FRACTION    = 0.125;

    // Algorithm C2 (PhaseChangeHandler)
    public static final double BISECT_TOL           = 1e-6;
    public static final int    MAX_BISECT           = 50;
    public static final double NODE_MERGE_TOL       = 1e-5;
    public static final int    MAX_EXIT_ITERATIONS  = 500;

    // Algorithm D (InvariantHandler)
    public static final double MIN_INVARIANT_AMOUNT = 1e-8;
}
