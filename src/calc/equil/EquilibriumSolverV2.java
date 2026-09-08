package calc.equil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import system.model.GibbsEnergyModel;
import system.model.cef.CefPhaseModelAdapter;
import system.ports.EquilibriumResult;
import util.Matrix;
import util.SingularValueDecomposition;

/**
 * General thermodynamic equilibrium solver based on the
 * Sundman-Lu-Ohtani iterative equilibrium algorithm.
 *
 * <p>This class is intentionally model-independent. Phase models
 * provide Gibbs-energy and composition information; this class
 * performs the global equilibrium calculation.</p>
 *
 * <p>Initial implementation scope:
 * fixed T, fixed P, fixed total element amounts.
 * Variable T/P and additional external constraints will be added later.</p>
 *
 * <p>This is the v2 redesign of {@link EquilibriumSolver}. It is being
 * built up incrementally alongside the existing solver; once it reaches
 * feature parity and is validated, it will replace {@link EquilibriumSolver}.</p>
 */
public class EquilibriumSolverV2 {

    // ================================================================
    // Solver state
    // ================================================================

    private List<GibbsEnergyModel> phaseModels;

    /** System temperature [K]. */
    private double T;

    /** System pressure [Pa]. */
    private double P;

    /** Target total amount of each component/element. */
    private double[] targetAmounts;

    /** Chemical potentials of system components. */
    private double[] mu;

    /** Amount of each stable phase in formula units. */
    private double[] phaseAmounts;

    /** Site/internal variables for each phase. */
    private double[][] phaseInternalVars;

    /** Indices of phases currently considered stable. */
    private int[] stablePhases;

    /** Current global Sundman equilibrium matrix. */
    private double[][] equilibriumMatrix;

    /** Right-hand side of the current global equilibrium matrix. */
    private double[] equilibriumRhs;

    /**
     * Solution variables of the global matrix.
     *
     * For the first one-phase implementation:
     *
     *   [ mu_1, ..., mu_nc, deltaOmega ]
     *
     * The chemical potentials are the Sundman lambda_A variables.
     */
    private double[] equilibriumUnknowns;

    /**
     * Phase-amount corrections obtained from the global matrix.
     */
    private double[] phaseAmountCorrections;

    /**
     * New chemical potentials obtained from the Sundman global
     * equilibrium matrix.
     *
     * These are the l_A variables of Sundman's Eq. (58), not
     * increments Delta(mu_A).
     */
    private double[] newLambda;

    /** Phase-amount corrections from the global Sundman solve. */
    private double[] deltaPhaseAmounts;

    /** Internal-variable corrections for each stable phase. */
    private double[][] deltaPhaseInternalVars;

    private double acceptedStepScale = 1.0;

    // ================================================================
    // Single-phase kernel state (first Sundman implementation increment)
    // ================================================================

    private double lastResidual;
    private double lastStep;

    private PhaseWork phaseWork;

    private static final double SOLVER_TOL = 1.0e-10;
    private static final int MAX_INTERNAL_ITER = 50;

    /**
     * Working data for one CEF phase.
     */
    private static final class PhaseWork {

        final CefPhaseModelAdapter model;

        double[] y;
        double G;

        double[] gy;
        double[][] gyy;

        double[] mA;
        double[][] dMdY;

        double[][] phaseMatrix;
        double[][] phaseMatrixInverse;

        double[] mu;
        double[] gamma;

        PhaseResponse response;

        /** R_AB = sum_i (dM_A/dY_i) c_iB. */
        double[][] massResponse;

        /** q_A = sum_i (dM_A/dY_i) c_iG. */
        double[] massGResponse;

        PhaseWork(CefPhaseModelAdapter model) {
            this.model = model;
        }
    }

    /**
     * Sundman phase-response coefficients (Eq. 43-44): the phase-matrix
     * inverse e_ij together with c_iG and c_iA, used at fixed T, P to
     * express the site-fraction correction as
     *
     *     Delta y_i = c_iG + sum_A c_iA * Delta mu_A.
     */
    private static final class PhaseResponse {

        final double[][] e;
        final double[] cG;
        final double[][] cA;

        PhaseResponse(
                double[][] e,
                double[] cG,
                double[][] cA) {

            this.e = e;
            this.cG = cG;
            this.cA = cA;
        }
    }

    // ================================================================
    // Solver controls
    // ================================================================

    private int maxIterations = 100;
    private double tolerance = 1.0e-10;

    // ================================================================
    // Constructor
    // ================================================================

    public EquilibriumSolverV2() {
    }

    // ================================================================
    // Public solve method
    // ================================================================

    /**
     * Solve equilibrium for a closed system at fixed T and P.
     *
     * @param T            temperature [K]
     * @param P            pressure [Pa]
     * @param compOverAll  overall composition (normalized mole fractions,
     *                     Σ_A x_A = 1)
     * @param candidates   candidate phase models
     * @return equilibrium result
     */
    public EquilibriumResult solve(double T,
                      double P,
                      double[] compOverAll,
                      List<GibbsEnergyModel> candidates) {

        // ------------------------------------------------------------
        // 0. Store problem conditions
        // ------------------------------------------------------------

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one phase model is required.");
        }

        this.T = T;
        this.P = P;
        this.phaseModels = candidates;

        /*
         * Convert the normalized overall composition to Sundman's
         * target component amounts N_A. For the present closed-system
         * formulation the total system amount N is arbitrary; taking
         * N = 1 gives N_A^target = x_A directly.
         */
        this.targetAmounts = compOverAll.clone();

        // ------------------------------------------------------------
        // 1. INITIALIZATION
        //
        // Obtain an initial stable-phase set and initial constitutions.
        //
        // This will later call the grid/global initialization procedure.
        // ------------------------------------------------------------

        initialize();

        // ------------------------------------------------------------
        // 2. MAIN EQUILIBRIUM ITERATION
        // ------------------------------------------------------------

        for (int iteration = 0;
             iteration < maxIterations;
             iteration++) {

            // ========================================================
            // STEP 1
            // Evaluate thermodynamic state of every relevant phase
            // ========================================================

            /*
             * For each phase α obtain:
             *
             *   G_M^α
             *   G_Y^α
             *   G_YY^α
             *   G_YT^α
             *   G_YP^α
             *   M_A^α
             *   ∂M_A^α/∂Y_i
             *
             * These quantities are supplied by the phase model.
             */
            evaluateAllPhases();

            // ========================================================
            // STEP 2
            // Construct and invert the phase matrix for each phase
            // ========================================================

            /*
             * For each phase α construct
             *
             *       [ G_YY   C^T ]
             *   P = [          ]
             *       [ C       0 ]
             *
             * and obtain the inverse response coefficients:
             *
             *   e_ij
             *   c_iG
             *   c_iT
             *   c_iP
             *   c_iA
             */
            buildPhaseResponses();

            // ========================================================
            // STEP 3
            // Construct global equilibrium matrix
            // ========================================================

            /*
             * For fixed T and P the global unknown corrections are:
             *
             *   Δμ_A
             *   Δω_α
             *
             * The equations are:
             *
             *   G_M^α - Σ_A M_A^α μ_A = 0
             *
             * and the element mass balances:
             *
             *   N_A(target) - Σ_α ω_α M_A^α = 0
             */
            buildEquilibriumMatrix();

            // ========================================================
            // STEP 4
            // Solve global Newton system
            // ========================================================

            /*
             * Solve for:
             *
             *   Δμ_A
             *   Δω_α
             */
            solveEquilibriumMatrix();

            // ========================================================
            // STEP 5
            // Calculate internal-variable corrections
            // ========================================================

            /*
             * For every phase α and internal variable i:
             *
             *   ΔY_i^α =
             *       c_iG^α
             *       + Σ_A c_iA^α Δμ_A
             *
             * for fixed T and P.
             *
             * Variable-T/P terms will later add:
             *
             *       c_iT^α ΔT
             *       c_iP^α ΔP
             */
            calculateInternalCorrections();

            // ========================================================
            // STEP 6
            // Update phase constitutions, phase amounts and μ
            // ========================================================

            updateState();

            // ========================================================
            // STEP 7
            // Check validity of updated state
            // ========================================================

            /*
             * Verify:
             *
             *   Σ_i Y_i,s = 1
             *   Y_i >= 0
             *   ω_α >= 0 for stable phases
             *   finite values everywhere
             */
            validateState();

            // ========================================================
            // STEP 8
            // Check / modify stable phase set
            // ========================================================

            /*
             * Stable phase:
             *
             *   ω_α < 0  -> remove phase
             *
             * Unstable phase:
             *
             *   c_w = Σ_A μ_A M_A^w - G_M^w
             *
             *   c_w > 0  -> add phase
             *
             * A phase-set change causes the global system to be
             * rebuilt on the next iteration.
             */
            updateStablePhaseSet();

            // ========================================================
            // STEP 9
            // Convergence check
            // ========================================================

            /*
             * Require small:
             *
             *   Δμ
             *   Δω
             *   ΔY
             *
             * together with small equilibrium and mass-balance
             * residuals.
             */
            if (checkConvergence()) {
                return buildEquilibriumResult(true, iteration);
            }
        }

        // Non-convergence within maxIterations is a normal (if
        // unsuccessful) solver outcome, not an input error -- report it
        // via converged=false rather than throwing, since callers expect
        // an EquilibriumResult back in all non-exceptional cases.
        return buildEquilibriumResult(false, maxIterations);
    }

    // ================================================================
    // Result assembly
    // ================================================================

    /**
     * Assemble EquilibriumResult from the accepted solver state.
     *
     * For the current one-phase implementation, the active phase state
     * is taken directly from phaseWork rather than re-entering the legacy
     * GibbsEnergyModel composition interface. This avoids reconstructing
     * a CEF constitution from a potentially stale/undefined composition.
     */
    private EquilibriumResult buildEquilibriumResult(
            boolean converged,
            int iterations) {

        int nc =
                targetAmounts.length;

        double[] muResult =
                (mu != null)
                        ? mu.clone()
                        : new double[nc];

        boolean[] isStable =
                new boolean[phaseModels.size()];

        if (stablePhases != null) {

            for (int idx :
                    stablePhases) {

                if (idx >= 0
                        && idx < isStable.length) {

                    isStable[idx] = true;
                }
            }
        }

        List<EquilibriumResult.PhaseResult> stableResults =
                new ArrayList<>();

        List<EquilibriumResult.PhaseResult> metastableResults =
                new ArrayList<>();

        for (int i = 0;
             i < phaseModels.size();
             i++) {

            GibbsEnergyModel model =
                    phaseModels.get(i);

            double[] x;
            double[] y;
            double g;
            double[] mA;

            /*
             * Current implementation has one active CEF phase.
             * Use the accepted phaseWork state directly.
             */
            if (i == 0
                    && phaseWork != null
                    && phaseWork.model == model) {

                y =
                        phaseWork.y.clone();

                g =
                        phaseWork.G;

                mA =
                        phaseWork.mA.clone();

                /*
                 * Calculate composition from M_A so that the returned
                 * x is guaranteed to correspond to the accepted CEF
                 * constitution.
                 */
                x =
                        new double[nc];

                double totalM =
                        0.0;

                for (double value : mA) {
                    totalM += value;
                }

                if (!(totalM > 0.0)
                        || !Double.isFinite(totalM)) {

                    throw new IllegalStateException(
                            "Invalid phase element amount sum: "
                            + totalM);
                }

                for (int A = 0;
                     A < nc;
                     A++) {

                    x[A] =
                            mA[A] / totalM;
                }

            } else {

                /*
                 * Non-active phases are not yet part of the V2 solver
                 * implementation.  Retain a guarded legacy path for
                 * result reporting.
                 */
                try {

                    x =
                            model.getComposition();

                    y =
                            model.getInternalVars();

                    g =
                            model.evaluateG(
                                    x,
                                    T);

                    mA =
                            model.getM();

                } catch (RuntimeException ex) {

                    /*
                     * Do not let result construction destroy an otherwise
                     * converged equilibrium calculation.
                     */
                    x =
                            new double[nc];

                    y =
                            new double[0];

                    g =
                            Double.NaN;

                    mA =
                            new double[nc];
                }
            }

            double drivingForce =
                    g;

            if (Double.isFinite(g)
                    && mA != null) {

                for (int A = 0;
                     A < Math.min(
                             nc,
                             mA.length);
                     A++) {

                    drivingForce +=
                            muResult[A]
                            * mA[A];
                }
            }

            if (isStable[i]) {

                double amount =
                        (phaseAmounts != null
                                && i < phaseAmounts.length)
                                ? phaseAmounts[i]
                                : 0.0;

                stableResults.add(
                        new EquilibriumResult.PhaseResult(
                                model.phaseName(),
                                model.modelType(),
                                amount,
                                x,
                                y,
                                g,
                                drivingForce));

            } else {

                metastableResults.add(
                        new EquilibriumResult.PhaseResult(
                                model.phaseName(),
                                model.modelType(),
                                0.0,
                                x,
                                y,
                                g,
                                drivingForce));
            }
        }

        return new EquilibriumResult(
                T,
                P,
                muResult,
                stableResults,
                metastableResults,
                converged,
                iterations);
    }

    // ================================================================
    // Main-loop placeholder methods
    //
    // These will be implemented one by one later.
    // ================================================================

    /**
     * Initialize the first V2 equilibrium problem.
     *
     * Current implementation scope:
     *   - exactly one candidate phase
     *   - CEF phase only
     *   - fixed T and P
     *   - closed system
     *
     * The system-level targetAmounts remain normalized so that
     *
     *      sum_A N_A = 1
     *
     * while the phase amount is initialized from the CEF formula-unit
     * site ratio. For a single phase:
     *
     *      N_A = omega * M_A
     *
     * and therefore
     *
     *      omega = N_total / sum_A M_A
     *
     * once the initial constitution has been constructed.
     */
    private void initialize() {

        if (phaseModels == null || phaseModels.isEmpty()) {
            throw new IllegalStateException(
                    "No candidate phase models are available.");
        }

        if (phaseModels.size() != 1) {
            throw new UnsupportedOperationException(
                    "EquilibriumSolverV2 initialization currently "
                    + "supports one phase only.");
        }

        GibbsEnergyModel model =
                phaseModels.get(0);

        if (!(model instanceof CefPhaseModelAdapter)) {
            throw new UnsupportedOperationException(
                    "EquilibriumSolverV2 first implementation "
                    + "requires a CEF phase model.");
        }

        CefPhaseModelAdapter cef =
                (CefPhaseModelAdapter) model;

        double[] xOverall =
                targetComposition();

        /*
         * Construct a valid initial constitution using the generic
         * CEF initializer. No phase-specific constitution is used.
         */
        double[] y =
                initializeSinglePhaseState(
                        cef,
                        xOverall);

        phaseWork =
                new PhaseWork(cef);

        phaseWork.y =
                y.clone();

        /*
         * Evaluate the initial CEF state.
         */
        evaluateSinglePhaseState(
                cef,
                phaseWork.y);

        /*
         * For a one-phase closed system, the initial phase amount is
         *
         *      omega = N_total / sum_A M_A.
         *
         * Since targetAmounts are normalized to N_total = 1,
         * this becomes 1 / sum_A M_A.
         *
         * For V2ZR, sum_A M_A = 3, hence omega = 1/3 for
         * one mole of total elements under the N_total=1 convention.
         */
        double totalM = 0.0;

        for (double value : phaseWork.mA) {
            totalM += value;
        }

        if (!(totalM > 0.0) || !Double.isFinite(totalM)) {
            throw new IllegalStateException(
                    "Invalid initial phase formula-unit amount: "
                    + totalM);
        }

        phaseAmounts =
                new double[] {
                        1.0 / totalM
                };

        /*
         * Stable phase set initially contains the single candidate.
         */
        stablePhases =
                new int[] {0};

        phaseInternalVars =
                new double[phaseModels.size()][];

        phaseInternalVars[0] =
                phaseWork.y.clone();

        /*
         * Initialize chemical potentials through the phase stationarity
         * relation. The resulting values are only the initial Lagrange
         * multipliers; the global Sundman system will subsequently update
         * them.
         */
        calculateChemicalPotentials();

        /*
         * Synchronize the public solver state.
         */
        mu =
                (phaseWork.mu != null)
                        ? phaseWork.mu.clone()
                        : new double[targetAmounts.length];

        lastResidual =
                Double.POSITIVE_INFINITY;

        lastStep =
                Double.POSITIVE_INFINITY;
    }

    private void evaluateAllPhases() {

        if (phaseModels == null || phaseModels.isEmpty()) {
            throw new IllegalStateException(
                    "No candidate phase models are available.");
        }

        if (phaseModels.size() != 1) {
            throw new UnsupportedOperationException(
                    "First Sundman implementation increment supports "
                    + "one phase only.");
        }

        GibbsEnergyModel gm = phaseModels.get(0);

        if (!(gm instanceof CefPhaseModelAdapter)) {
            throw new UnsupportedOperationException(
                    "First Sundman implementation increment requires "
                    + "a CEF phase model.");
        }

        CefPhaseModelAdapter cef =
                (CefPhaseModelAdapter) gm;

        if (phaseWork == null
                || phaseWork.model != cef) {

            phaseWork = new PhaseWork(cef);

            phaseWork.y =
                    cef.getInitialInternalVars(
                            targetComposition());

        } else {
            // Keep the current constitution.
            phaseWork.y =
                    phaseWork.y.clone();
        }

        phaseWork.G =
                cef.sundmanG(T, phaseWork.y);

        phaseWork.gy =
                cef.sundmanGradient(
                        T, phaseWork.y);

        phaseWork.gyy =
                cef.sundmanHessian(
                        T, phaseWork.y);

        phaseWork.mA =
                cef.sundmanM(
                        phaseWork.y);

        phaseWork.dMdY =
                cef.sundmanMJacobian();
    }

    /**
     * Overall normalized composition x_A used to seed the initial CEF
     * constitution. The solver receives normalized x_A, whereas
     * Sundman's M_A is an amount per formula unit.
     */
    private double[] targetComposition() {

        if (targetAmounts == null) {
            throw new IllegalStateException(
                    "targetAmounts has not been initialized.");
        }

        double sum = 0.0;

        for (double n : targetAmounts) {
            sum += n;
        }

        if (!(sum > 0.0)) {
            throw new IllegalStateException(
                    "Target element amounts have zero total.");
        }

        double[] x = new double[targetAmounts.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = targetAmounts[i] / sum;
        }

        return x;
    }

    // ================================================================
    // Single-phase CEF kernel (first Sundman implementation increment)
    // ================================================================

    /**
     * Initializes the constitution of a single CEF phase for the requested
     * overall composition.
     *
     * The initialization is delegated to the CEF adapter because it knows
     * the complete sublattice structure, constituent mapping, vacancies, etc.
     *
     * No phase-specific expressions are used here.
     */
    private double[] initializeSinglePhaseState(
            CefPhaseModelAdapter phase,
            double[] xOverall) {

        if (phase == null)
            throw new IllegalArgumentException(
                    "CEF phase must not be null.");

        if (xOverall == null || xOverall.length == 0)
            throw new IllegalArgumentException(
                    "Overall composition must not be null or empty.");

        /*
         * CefPhaseModelAdapter.getInitialInternalVars() constructs a strictly
         * positive constitution satisfying the CEF sublattice-normalization
         * constraints and matching the requested overall composition as closely
         * as possible.
         */
        double[] y =
                phase.getInitialInternalVars(xOverall);

        if (y == null)
            throw new IllegalStateException(
                    "CEF phase could not generate an initial constitution.");

        if (!phase.isValid(y))
            throw new IllegalStateException(
                    "CEF initial constitution is invalid.");

        return y;
    }

    /**
     * Returns the total number of sites per formula unit.
     */
    private double totalSiteRatio(
            CefPhaseModelAdapter phase) {

        double sum = 0.0;

        for (double a :
             phase.sundmanSiteRatios()) {

            sum += a;
        }

        return sum;
    }

    /**
     * Constructs the target M_A values corresponding to the requested
     * normalized overall composition for a single phase.
     *
     * The normalization is chosen so that the total element amount
     * corresponds to one mole of lattice sites.
     */
    private double[] initializeSinglePhaseTargetM(
            CefPhaseModelAdapter phase,
            double[] xOverall) {

        double[] x =
                xOverall.clone();

        double xSum = 0.0;

        for (double xi : x)
            xSum += xi;

        for (int i = 0; i < x.length; i++)
            x[i] /= xSum;

        double nSites =
                totalSiteRatio(phase);

        double[] targetM =
                new double[x.length];

        for (int a = 0; a < x.length; a++) {

            targetM[a] =
                    nSites * x[a];
        }

        return targetM;
    }

    /**
     * Builds C where each row represents one sublattice-normalization
     * constraint:
     *
     *     sum_i y_is - 1 = 0
     *
     * C has dimensions ns x nip.
     */
    private double[][] buildSublatticeConstraintMatrix(
            CefPhaseModelAdapter phase) {

        int ns =
                phase.sundmanNumSublattices();

        int nip =
                phase.sundmanNumSiteVariables();

        double[][] C =
                new double[ns][nip];

        int[] offsets =
                phase.sundmanOffsets();

        int[] nc =
                phase.sundmanConstituentsPerSublattice();

        for (int s = 0; s < ns; s++) {

            for (int i = 0;
                 i < nc[s];
                 i++) {

                C[s][offsets[s] + i] = 1.0;
            }
        }

        return C;
    }

    /**
     * Constructs the generic single-phase constrained Newton matrix.
     *
     * Unknown ordering:
     *
     *   [ DeltaY | DeltaMu | DeltaLambda ]
     *
     * Dimensions:
     *
     *   nip + nElements + nSublattices
     */
    private double[][] buildSinglePhaseKktMatrix(
            CefPhaseModelAdapter phase) {

        int nip =
                phase.sundmanNumSiteVariables();

        int nc =
                targetAmounts.length;

        int ns =
                phase.sundmanNumSublattices();

        int n =
                nip + nc + ns;

        double[][] A =
                new double[n][n];

        // ------------------------------------------------------------
        // G_YY
        // ------------------------------------------------------------

        for (int i = 0; i < nip; i++) {

            for (int j = 0; j < nip; j++) {

                A[i][j] =
                        phaseWork.gyy[i][j];
            }
        }

        // ------------------------------------------------------------
        // -J_M^T
        // ------------------------------------------------------------

        for (int i = 0; i < nip; i++) {

            for (int a = 0; a < nc; a++) {

                double v =
                        phaseWork.dMdY[a][i];

                A[i][nip + a] = -v;
                A[nip + a][i] = -v;
            }
        }

        // ------------------------------------------------------------
        // -C^T
        // ------------------------------------------------------------

        double[][] C =
                buildSublatticeConstraintMatrix(phase);

        for (int s = 0; s < ns; s++) {

            int col =
                    nip + nc + s;

            for (int i = 0; i < nip; i++) {

                double v = C[s][i];

                if (v == 0.0)
                    continue;

                A[i][col] = -v;
                A[col][i] = -v;
            }
        }

        return A;
    }

    /**
     * Builds the single-phase KKT residual.
     *
     * Residual ordering:
     *
     *   [ stationarity
     *     element mass balance
     *     sublattice normalization ]
     *
     * <p>{@code targetM} is the phase-level target element-amount vector
     * (distinct from the system-level {@link #targetAmounts}); it is an
     * explicit argument rather than a field so that the later multiphase
     * solver can supply a per-phase share of the overall target without
     * ambiguity.
     */
    private double[] buildSinglePhaseResidual(
            CefPhaseModelAdapter phase,
            double[] targetM) {

        int nip =
                phase.sundmanNumSiteVariables();

        int nc =
                targetAmounts.length;

        int ns =
                phase.sundmanNumSublattices();

        int n =
                nip + nc + ns;

        double[] r =
                new double[n];

        // ------------------------------------------------------------
        // Stationarity:
        //
        // G_Y - J_M^T mu - C^T lambda
        // ------------------------------------------------------------

        for (int i = 0; i < nip; i++) {

            double value =
                    phaseWork.gy[i];

            for (int a = 0; a < nc; a++) {

                value -=
                        phaseWork.dMdY[a][i]
                        * phaseWork.mu[a];
            }

            /*
             * phaseWork.gamma contains one multiplier per
             * sublattice.
             */
            int s =
                    sublatticeOf(
                            i,
                            phase.sundmanOffsets(),
                            phase.sundmanConstituentsPerSublattice());

            value -=
                    phaseWork.gamma[s];

            r[i] = value;
        }

        // ------------------------------------------------------------
        // Element balance:
        //
        // M_A - target M_A
        // ------------------------------------------------------------

        for (int a = 0; a < nc; a++) {

            r[nip + a] =
                    phaseWork.mA[a]
                    - targetM[a];
        }

        // ------------------------------------------------------------
        // Sublattice normalization:
        //
        // sum(y_is) - 1
        // ------------------------------------------------------------

        int[] offsets =
                phase.sundmanOffsets();

        int[] ncSL =
                phase.sundmanConstituentsPerSublattice();

        for (int s = 0; s < ns; s++) {

            double sum = 0.0;

            for (int i = 0;
                 i < ncSL[s];
                 i++) {

                sum +=
                        phaseWork.y[
                                offsets[s] + i];
            }

            r[nip + nc + s] =
                    sum - 1.0;
        }

        return r;
    }

    /**
     * Construct the phase matrix
     *
     *     [ G_YY   C^T ]
     *     [ C       0  ]
     *
     * where C represents the sublattice constraints 1 - sum_i y_is = 0.
     */
    private void buildPhaseMatrix() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase has not been evaluated.");
        }

        CefPhaseModelAdapter model =
                phaseWork.model;

        int nip =
                model.sundmanNumSiteVariables();

        int ns =
                model.sundmanNumSublattices();

        int n =
                nip + ns;

        double[][] A =
                new double[n][n];

        // ------------------------------------------------------------
        // Upper-left block: d2G/dYdY
        // ------------------------------------------------------------
        for (int i = 0; i < nip; i++) {
            for (int j = 0; j < nip; j++) {
                A[i][j] =
                        phaseWork.gyy[i][j];
            }
        }

        // ------------------------------------------------------------
        // Constraint Jacobian C
        //
        // g_s = 1 - sum_i y_is
        //
        // derivative is -1 for every constituent on sublattice s.
        //
        // The sign is immaterial provided the same convention is used
        // consistently for the multipliers.
        // ------------------------------------------------------------
        int[] offsets =
                model.sundmanOffsets();

        int[] nc =
                model.sundmanConstituentsPerSublattice();

        for (int s = 0; s < ns; s++) {

            int row = nip + s;

            for (int i = 0; i < nc[s]; i++) {

                int k =
                        offsets[s] + i;

                A[k][row] = 1.0;
                A[row][k] = 1.0;
            }
        }

        phaseWork.phaseMatrix = A;
    }

    /**
     * Builds the Sundman phase matrix (the CEF bordered Hessian):
     *
     *     E = [ G_YY   C^T ]
     *         [ C       0  ]
     *
     * Unlike {@link #buildPhaseMatrix()} (which stores the result
     * directly into {@code phaseWork.phaseMatrix}), this returns the
     * matrix so it can be inverted by the phase-response calculation
     * (Sundman Eq. 43-44) without an intervening field read.
     */
    private double[][] buildSundmanPhaseMatrix(
            CefPhaseModelAdapter phase) {

        int nip =
                phase.sundmanNumSiteVariables();

        int ns =
                phase.sundmanNumSublattices();

        double[][] e =
                new double[nip + ns][nip + ns];

        // G_YY
        for (int i = 0; i < nip; i++) {
            for (int j = 0; j < nip; j++) {
                e[i][j] = phaseWork.gyy[i][j];
            }
        }

        int[] offsets =
                phase.sundmanOffsets();

        int[] nconst =
                phase.sundmanConstituentsPerSublattice();

        // C^T and C
        for (int s = 0; s < ns; s++) {

            int row = nip + s;

            for (int i = 0; i < nconst[s]; i++) {

                int k = offsets[s] + i;

                e[k][row] = 1.0;
                e[row][k] = 1.0;
            }
        }

        return e;
    }

    /**
     * Inverts a square matrix using the project's existing dense
     * linear-algebra utility (LU-based).
     */
    private static double[][] invertMatrix(double[][] A) {
        Matrix matA = new Matrix(A);
        Matrix matInv = matA.inverse();
        return matInv.getArray();
    }

    /**
     * Computes the Sundman phase-response coefficients from the inverted
     * phase matrix.
     *
     * Sundman Eq. (43)-(44):
     *
     *     DeltaY_i = c_iG
     *              + c_iT * DeltaT
     *              + c_iP * DeltaP
     *              + sum_A c_iA * l_A
     *
     * At fixed T and P:
     *
     *     DeltaY_i = c_iG + sum_A c_iA * l_A
     *
     * with
     *
     *     c_iG = sum_j e_ij * dG/dY_j
     *
     *     c_iA = sum_j e_ij * dM_A/dY_j
     *
     * per Sundman's Eq. (44) (positive sign on c_iG).
     *
     * This method only calculates the phase-response coefficients.
     */
    private PhaseResponse calculatePhaseResponse(
            CefPhaseModelAdapter phase) {

        double[][] phaseMatrix =
                buildSundmanPhaseMatrix(phase);

        double[][] inverse =
                invertMatrix(phaseMatrix);

        int nip =
                phase.sundmanNumSiteVariables();

        int ns =
                phase.sundmanNumSublattices();

        int nc =
                targetAmounts.length;

        /*
         * The upper-left nip x nip block of the inverse is e_ij.
         */
        double[] cG =
                new double[nip];

        /*
         * cA[A][i] = c_iA
         */
        double[][] cA =
                new double[nc][nip];

        // ------------------------------------------------------------
        // c_iG
        //
        //     c_iG = - sum_j e_ij * dG/dY_j
        // ------------------------------------------------------------

        for (int i = 0; i < nip; i++) {

            double sum = 0.0;

            for (int j = 0; j < nip; j++) {

                sum +=
                        inverse[i][j]
                        * phaseWork.gy[j];
            }

            cG[i] = -sum;
        }

        // ------------------------------------------------------------
        // c_iA
        //
        //     c_iA = sum_j e_ij * dM_A/dY_j
        // ------------------------------------------------------------

        for (int A = 0; A < nc; A++) {

            for (int i = 0; i < nip; i++) {

                double sum = 0.0;

                for (int j = 0; j < nip; j++) {

                    sum +=
                            inverse[i][j]
                            * phaseWork.dMdY[A][j];
                }

                cA[A][i] = sum;
            }
        }

        return new PhaseResponse(
                inverse,
                cG,
                cA);
    }

    /**
     * Current-state chemical potentials, solving
     *
     *     G_Y = C^T*gamma + J_M^T*mu
     *
     * where J_M[A][i] = dM_A/dY_i. This is only the current-state
     * chemical-potential calculation; it is not yet the global Sundman
     * correction equation.
     */
    private void calculateChemicalPotentials() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase has not been evaluated.");
        }

        int nip =
                phaseWork.model.sundmanNumSiteVariables();

        int ns =
                phaseWork.model.sundmanNumSublattices();

        int nc =
                targetAmounts.length;

        int nUnknown =
                nc + ns;

        double[][] A =
                new double[nip][nUnknown];

        double[] b =
                new double[nip];

        // ------------------------------------------------------------
        // J_M^T * mu
        // ------------------------------------------------------------
        for (int i = 0; i < nip; i++) {

            for (int a = 0; a < nc; a++) {

                A[i][a] =
                        phaseWork.dMdY[a][i];
            }

            b[i] =
                    phaseWork.gy[i];
        }

        // ------------------------------------------------------------
        // C^T * gamma
        // ------------------------------------------------------------
        int[] offsets =
                phaseWork.model.sundmanOffsets();

        int[] nconst =
                phaseWork.model
                        .sundmanConstituentsPerSublattice();

        for (int s = 0; s < ns; s++) {

            int gammaColumn =
                    nc + s;

            for (int i = 0; i < nconst[s]; i++) {

                int k =
                        offsets[s] + i;

                A[k][gammaColumn] = 1.0;
            }
        }

        double[] solution =
                solveMinimumNormSystem(A, b);

        phaseWork.mu =
                Arrays.copyOf(
                        solution, nc);

        phaseWork.gamma =
                Arrays.copyOfRange(
                        solution, nc,
                        nc + ns);

        this.mu =
                phaseWork.mu.clone();
    }

    /**
     * Constrained Newton correction for the single closed-system phase,
     * solving simultaneously for DeltaY, Delta-mu and Delta-gamma:
     *
     *     [ G_YY   J_M^T   C^T ] [DeltaY    ]   [G_Y - J_M^T*mu - C^T*gamma]
     *     [ J_M     0      0   ] [Delta-mu  ] = -[M - N                    ]
     *     [ C       0      0   ] [Delta-gamma]  [g(Y)                     ]
     */
    private void solveSinglePhaseCorrection() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase has not been evaluated.");
        }

        int nip =
                phaseWork.model.sundmanNumSiteVariables();

        int ns =
                phaseWork.model.sundmanNumSublattices();

        int nc =
                targetAmounts.length;

        int n =
                nip + nc + ns;

        double[][] A =
                new double[n][n];

        double[] rhs =
                new double[n];

        // ============================================================
        // 1. d2G/dYdY block
        // ============================================================

        for (int i = 0; i < nip; i++) {
            for (int j = 0; j < nip; j++) {
                A[i][j] =
                        phaseWork.gyy[i][j];
            }
        }

        // ============================================================
        // 2. J_M^T block
        // ============================================================

        for (int i = 0; i < nip; i++) {

            for (int a = 0; a < nc; a++) {

                double value =
                        phaseWork.dMdY[a][i];

                A[i][nip + a] = value;
                A[nip + a][i] = value;
            }
        }

        // ============================================================
        // 3. Sublattice constraints
        // ============================================================

        int[] offsets =
                phaseWork.model.sundmanOffsets();

        int[] nconst =
                phaseWork.model
                        .sundmanConstituentsPerSublattice();

        for (int s = 0; s < ns; s++) {

            int gammaCol =
                    nip + nc + s;

            for (int i = 0; i < nconst[s]; i++) {

                int k =
                        offsets[s] + i;

                A[k][gammaCol] = 1.0;
                A[gammaCol][k] = 1.0;
            }
        }

        // ============================================================
        // 4. Stationarity residual
        //
        //      G_Y - J_M^T mu - C^T gamma
        // ============================================================

        for (int i = 0; i < nip; i++) {

            double r =
                    phaseWork.gy[i];

            for (int a = 0; a < nc; a++) {

                r -=
                        phaseWork.dMdY[a][i]
                        * phaseWork.mu[a];
            }

            int sublattice =
                    sublatticeOf(
                            i, offsets, nconst);

            r -=
                    phaseWork.gamma[sublattice];

            rhs[i] = -r;
        }

        // ============================================================
        // 5. Element mass-balance residual
        //
        //       M_A - N_A = 0
        // ============================================================

        double[] target =
                targetAmounts;

        for (int a = 0; a < nc; a++) {

            rhs[nip + a] =
                    -(phaseWork.mA[a]
                            - target[a]);
        }

        // ============================================================
        // 6. Sublattice normalization residual
        // ============================================================

        for (int s = 0; s < ns; s++) {

            double sum = 0.0;

            for (int i = 0; i < nconst[s]; i++) {

                sum +=
                        phaseWork.y[
                                offsets[s] + i];
            }

            rhs[nip + nc + s] =
                    -(sum - 1.0);
        }

        double[] correction =
                solveLinearSystem(A, rhs);

        System.arraycopy(
                correction, 0,
                phaseWork.y, 0,
                nip);

        this.lastStep =
                vectorNorm(correction);
    }

    /**
     * Temporary single-phase main loop connecting the phase-level
     * evaluation and constrained Newton correction above. Does not yet
     * call the full global {@link #buildEquilibriumMatrix()} or the
     * phase-set management logic.
     */
    private boolean solveSinglePhase() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase has not been initialized.");
        }

        for (int iter = 0;
                iter < MAX_INTERNAL_ITER;
                iter++) {

            evaluateAllPhases();

            calculateChemicalPotentials();

            buildPhaseMatrix();

            solveSinglePhaseCorrection();

            evaluateAllPhases();

            calculateChemicalPotentials();

            double residual =
                    singlePhaseResidualNorm();

            lastResidual =
                    residual;

            if (residual < SOLVER_TOL
                    && lastStep < SOLVER_TOL) {

                return true;
            }
        }

        return false;
    }

    /**
     * Combined residual norm over element mass balance, sublattice
     * constraints and stationarity, at the current phaseWork state.
     */
    private double singlePhaseResidualNorm() {

        double sum2 = 0.0;

        // ------------------------------------------------------------
        // Element mass balance
        // ------------------------------------------------------------
        for (int a = 0;
                a < targetAmounts.length;
                a++) {

            double r =
                    phaseWork.mA[a]
                    - targetAmounts[a];

            sum2 += r * r;
        }

        // ------------------------------------------------------------
        // Sublattice constraints
        // ------------------------------------------------------------
        int[] offsets =
                phaseWork.model.sundmanOffsets();

        int[] nconst =
                phaseWork.model
                        .sundmanConstituentsPerSublattice();

        for (int s = 0; s < nconst.length; s++) {

            double sum = 0.0;

            for (int i = 0; i < nconst[s]; i++) {

                sum +=
                        phaseWork.y[
                                offsets[s] + i];
            }

            double r =
                    sum - 1.0;

            sum2 += r * r;
        }

        // ------------------------------------------------------------
        // Stationarity
        // ------------------------------------------------------------
        for (int i = 0;
                i < phaseWork.y.length;
                i++) {

            double r =
                    phaseWork.gy[i];

            for (int a = 0;
                    a < targetAmounts.length;
                    a++) {

                r -=
                        phaseWork.dMdY[a][i]
                        * phaseWork.mu[a];
            }

            int s =
                    sublatticeOf(
                            i, offsets, nconst);

            r -= phaseWork.gamma[s];

            sum2 += r * r;
        }

        return Math.sqrt(sum2);
    }

    /**
     * Thermodynamic stationarity residual norm at the current accepted
     * phase state:
     *
     *     || G_Y - J_M^T lambda - C^T gamma ||
     *
     * checkConvergence() must require this to be small, not merely
     * lastStep -- a small step does not by itself certify that the
     * governing equations are satisfied.
     */
    private double singlePhaseStationarityNorm() {

        if (phaseWork == null
                || phaseWork.y == null) {

            return Double.POSITIVE_INFINITY;
        }

        int nip =
                phaseWork.model
                        .sundmanNumSiteVariables();

        int nc =
                targetAmounts.length;

        int ns =
                phaseWork.model
                        .sundmanNumSublattices();

        int[] offsets =
                phaseWork.model
                        .sundmanOffsets();

        int[] nconst =
                phaseWork.model
                        .sundmanConstituentsPerSublattice();

        double sum2 = 0.0;

        /*
         * G_Y - J_M^T lambda - C^T gamma
         */
        for (int i = 0;
             i < nip;
             i++) {

            double r =
                    phaseWork.gy[i];

            for (int A = 0;
                 A < nc;
                 A++) {

                r -=
                        phaseWork.dMdY[A][i]
                        * phaseWork.mu[A];
            }

            int s =
                    sublatticeOf(
                            i,
                            offsets,
                            nconst);

            r -=
                    phaseWork.gamma[s];

            sum2 += r * r;
        }

        return Math.sqrt(sum2);
    }

    /**
     * Re-evaluates G, G_Y, G_YY, M_A and dM/dY for the given phase at the
     * given constitution y, writing the results into {@link #phaseWork}.
     * Does not modify {@code phaseWork.y} itself, so callers can probe a
     * trial constitution without disturbing the accepted state.
     */
    private void evaluateSinglePhaseState(
            CefPhaseModelAdapter phase,
            double[] y) {

        phaseWork.G =
                phase.sundmanG(T, y);

        phaseWork.gy =
                phase.sundmanGradient(T, y);

        phaseWork.gyy =
                phase.sundmanHessian(T, y);

        phaseWork.mA =
                phase.sundmanM(y);

        phaseWork.dMdY =
                phase.sundmanMJacobian();
    }

    /**
     * Performs the generic single-phase constrained Newton iteration.
     *
     * This is the first real equilibrium kernel of the new solver.
     *
     * It is intentionally limited to one phase.  Phase selection and the
     * Sundman global equilibrium matrix will be added later.
     *
     * <p>Uses {@link #phaseWork}'s existing {@code gamma} field for the
     * per-sublattice Lagrange multipliers (the same role referred to as
     * "lambda" in the KKT formulation), rather than introducing a second,
     * differently-named field alongside it.
     */
    private boolean solveSinglePhaseNewton(
            CefPhaseModelAdapter phase,
            double[] targetM) {

        int nip =
                phase.sundmanNumSiteVariables();

        int nc =
                targetAmounts.length;

        int ns =
                phase.sundmanNumSublattices();

        /*
         * Start with zero Lagrange multipliers.
         */
        phaseWork.mu =
                new double[nc];

        phaseWork.gamma =
                new double[ns];

        for (int iter = 0;
             iter < MAX_INTERNAL_ITER;
             iter++) {

            evaluateSinglePhaseState(
                    phase,
                    phaseWork.y);

            double[] residual =
                    buildSinglePhaseResidual(
                            phase,
                            targetM);

            double norm =
                    vectorNorm(residual);

            if (norm < SOLVER_TOL) {

                lastResidual = norm;

                return true;
            }

            double[][] A =
                    buildSinglePhaseKktMatrix(
                            phase);

            double[] rhs =
                    negate(residual);

            double[] delta =
                    solveLinearSystem(
                            A,
                            rhs);

            /*
             * Partition correction.
             */
            double[] dy =
                    Arrays.copyOfRange(
                            delta,
                            0,
                            nip);

            double[] dmu =
                    Arrays.copyOfRange(
                            delta,
                            nip,
                            nip + nc);

            double[] dgamma =
                    Arrays.copyOfRange(
                            delta,
                            nip + nc,
                            nip + nc + ns);

            /*
             * Backtracking keeps every CEF site fraction physical.
             */
            double alpha = 1.0;

            double[] trialY =
                    new double[nip];

            boolean accepted = false;

            for (int ls = 0;
                 ls < 25;
                 ls++) {

                for (int i = 0; i < nip; i++) {

                    trialY[i] =
                            phaseWork.y[i]
                            + alpha * dy[i];
                }

                if (!phase.isValid(trialY)) {

                    alpha *= 0.5;
                    continue;
                }

                /*
                 * Evaluate trial residual.
                 */
                double[] oldY =
                        phaseWork.y;

                double[] oldMu =
                        phaseWork.mu;

                double[] oldGamma =
                        phaseWork.gamma;

                phaseWork.y =
                        trialY.clone();

                phaseWork.mu =
                        addScaled(
                                oldMu,
                                dmu,
                                alpha);

                phaseWork.gamma =
                        addScaled(
                                oldGamma,
                                dgamma,
                                alpha);

                evaluateSinglePhaseState(
                        phase,
                        phaseWork.y);

                double trialNorm =
                        vectorNorm(
                                buildSinglePhaseResidual(
                                        phase,
                                        targetM));

                phaseWork.y =
                        oldY;

                phaseWork.mu =
                        oldMu;

                phaseWork.gamma =
                        oldGamma;

                if (trialNorm < norm) {

                    accepted = true;

                    for (int i = 0;
                         i < nip;
                         i++) {

                        phaseWork.y[i] =
                                trialY[i];
                    }

                    for (int a = 0;
                         a < nc;
                         a++) {

                        phaseWork.mu[a] +=
                                alpha * dmu[a];
                    }

                    for (int s = 0;
                         s < ns;
                         s++) {

                        phaseWork.gamma[s] +=
                                alpha * dgamma[s];
                    }

                    lastResidual =
                            trialNorm;

                    lastStep =
                            alpha * vectorNorm(dy);

                    break;
                }

                alpha *= 0.5;
            }

            if (!accepted)
                return false;
        }

        return false;
    }

    // ================================================================
    // Numerical helpers
    // ================================================================

    private static double[] negate(
            double[] v) {

        double[] r =
                new double[v.length];

        for (int i = 0; i < v.length; i++)
            r[i] = -v[i];

        return r;
    }

    private static double[] addScaled(
            double[] a,
            double[] b,
            double scale) {

        double[] r =
                new double[a.length];

        for (int i = 0; i < a.length; i++)
            r[i] =
                    a[i] + scale * b[i];

        return r;
    }

    private static int sublatticeOf(
            int index,
            int[] offsets,
            int[] nconst) {

        for (int s = 0; s < offsets.length; s++) {

            int begin = offsets[s];
            int end =
                    begin + nconst[s];

            if (index >= begin && index < end) {
                return s;
            }
        }

        throw new IllegalArgumentException(
                "Site variable index outside sublattice ranges: "
                        + index);
    }

    private static double vectorNorm(
            double[] v) {

        double sum = 0.0;

        for (double x : v) {
            sum += x * x;
        }

        return Math.sqrt(sum);
    }

    private static void checkFinite(
            String name,
            double value) {

        if (!Double.isFinite(value)) {

            throw new IllegalStateException(
                    "Non-finite " + name + ": " + value);
        }
    }

    /**
     * Solve the square linear system A*x = b using the project's
     * existing dense linear-algebra utility.
     */
    private static double[] solveLinearSystem(double[][] A, double[] b) {
        Matrix matA = new Matrix(A);
        Matrix matB = new Matrix(b, b.length);
        Matrix matX = matA.solve(matB);
        return matX.getColumnPackedCopy();
    }

    /**
     * Solve A*x = b for the minimum-norm solution via the Moore-Penrose
     * pseudoinverse (SVD-based).
     *
     * <p>The current-state chemical-potential/multiplier system
     * G_Y = J_M^T*mu + C^T*gamma is a genuine Lagrange-multiplier
     * relation: its coefficient matrix is structurally rank-deficient
     * (the sublattice-constraint and mass-action rows are linearly
     * dependent for a stoichiometric phase), so a plain LU solve fails
     * on a singular matrix. SVD-based pseudoinversion is required to
     * obtain the minimum-norm mu/gamma instead of failing outright.
     */
    private static double[] solveMinimumNormSystem(double[][] A, double[] b) {

        Matrix matA = new Matrix(A);

        SingularValueDecomposition svd =
                new SingularValueDecomposition(matA);

        double[] s = svd.getSingularValues();
        Matrix U = svd.getU();
        Matrix V = svd.getV();

        int m = A.length;
        int n = A[0].length;
        int r = Math.min(m, n);

        double tol = Math.max(m, n) * s[0] * Math.pow(2.0, -52.0);

        // UtB = U^T * b
        double[] uTb = new double[r];
        double[][] uArr = U.getArray();
        for (int k = 0; k < r; k++) {
            double sum = 0.0;
            for (int i = 0; i < m; i++) {
                sum += uArr[i][k] * b[i];
            }
            uTb[k] = sum;
        }

        // Sinv * UtB, zeroing negligible singular values
        double[] sInvUtB = new double[r];
        for (int k = 0; k < r; k++) {
            sInvUtB[k] = (s[k] > tol) ? uTb[k] / s[k] : 0.0;
        }

        // x = V * sInvUtB
        double[][] vArr = V.getArray();
        double[] x = new double[n];
        for (int j = 0; j < n; j++) {
            double sum = 0.0;
            for (int k = 0; k < r; k++) {
                sum += vArr[j][k] * sInvUtB[k];
            }
            x[j] = sum;
        }

        return x;
    }

    /**
     * Evaluates the Sundman phase-response coefficients (Eq. 43-44) for
     * the current phase and stores them in {@link PhaseWork#response}.
     */
    private void buildPhaseResponses() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase has not been evaluated.");
        }

        phaseWork.response =
                calculatePhaseResponse(
                        phaseWork.model);
    }

    /**
     * Diagnostic for the sign convention of the Sundman phase response.
     *
     * The thermodynamic stationarity condition is
     *
     *     G_Y - J_M^T lambda - C^T gamma = 0.
     *
     * Consequently the linearized phase correction is driven by
     *
     *     -G_Y + J_M^T lambda.
     *
     * This diagnostic compares the two possible conventions:
     *
     *     dy_plus  =  +cG + cA^T lambda
     *     dy_minus =  -cG + cA^T lambda
     *
     * against the direct Newton correction obtained from the complete
     * constrained KKT system at the current phase state.
     *
     * No solver state is modified.
     */
    private void diagnosePhaseResponseSign() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase has not been evaluated.");
        }

        if (phaseWork.response == null) {
            throw new IllegalStateException(
                    "Phase response has not been calculated.");
        }

        if (phaseWork.mu == null) {
            throw new IllegalStateException(
                    "Chemical potentials are not available.");
        }

        final int nip =
                phaseWork.model.sundmanNumSiteVariables();

        final int nc =
                targetAmounts.length;

        final int ns =
                phaseWork.model.sundmanNumSublattices();

        // ------------------------------------------------------------
        // 1. Candidate Sundman responses
        // ------------------------------------------------------------

        double[] dyPlus =
                new double[nip];

        double[] dyMinus =
                new double[nip];

        for (int i = 0; i < nip; i++) {

            double plus =
                    phaseWork.response.cG[i];

            double minus =
                    -phaseWork.response.cG[i];

            for (int A = 0; A < nc; A++) {

                double contribution =
                        phaseWork.response.cA[A][i]
                        * phaseWork.mu[A];

                plus += contribution;
                minus += contribution;
            }

            dyPlus[i] = plus;
            dyMinus[i] = minus;
        }

        // ------------------------------------------------------------
        // 2. Direct KKT Newton correction
        //
        // Solve:
        //
        // [ GYY  -JM^T  -C^T ] [dy    ] =
        // [ JM     0      0  ] [dmu   ]
        // [ C      0      0  ] [dgamma]
        //
        // with residual:
        //
        // [ GY - JM^T mu - C^T gamma ]
        // [ M - targetM               ]
        // [ C(Y)                      ]
        //
        // The negative sign in front of GY comes from Newton's method.
        // ------------------------------------------------------------

        double[] targetM =
                initializeSinglePhaseTargetM(
                        phaseWork.model,
                        targetComposition());

        int n =
                nip + nc + ns;

        double[][] A =
                new double[n][n];

        double[] rhs =
                new double[n];

        // GYY
        for (int i = 0; i < nip; i++) {
            for (int j = 0; j < nip; j++) {
                A[i][j] =
                        phaseWork.gyy[i][j];
            }
        }

        // -JM^T / -JM
        for (int i = 0; i < nip; i++) {

            for (int Aidx = 0;
                 Aidx < nc;
                 Aidx++) {

                double v =
                        phaseWork.dMdY[Aidx][i];

                A[i][nip + Aidx] = -v;
                A[nip + Aidx][i] = -v;
            }
        }

        // -C^T / -C
        int[] offsets =
                phaseWork.model.sundmanOffsets();

        int[] nconst =
                phaseWork.model
                        .sundmanConstituentsPerSublattice();

        for (int s = 0; s < ns; s++) {

            int col =
                    nip + nc + s;

            for (int i = 0; i < nconst[s]; i++) {

                int k =
                        offsets[s] + i;

                A[k][col] = -1.0;
                A[col][k] = -1.0;
            }
        }

        // Stationarity residual.
        for (int i = 0; i < nip; i++) {

            double r =
                    phaseWork.gy[i];

            for (int Aidx = 0;
                 Aidx < nc;
                 Aidx++) {

                r -=
                        phaseWork.dMdY[Aidx][i]
                        * phaseWork.mu[Aidx];
            }

            int s =
                    sublatticeOf(
                            i,
                            offsets,
                            nconst);

            r -=
                    phaseWork.gamma[s];

            rhs[i] = -r;
        }

        // M-target residual.
        for (int Aidx = 0;
             Aidx < nc;
             Aidx++) {

            rhs[nip + Aidx] =
                    -(phaseWork.mA[Aidx]
                            - targetM[Aidx]);
        }

        // Sublattice normalization residual.
        for (int s = 0; s < ns; s++) {

            double sum = 0.0;

            for (int i = 0; i < nconst[s]; i++) {

                sum +=
                        phaseWork.y[
                                offsets[s] + i];
            }

            rhs[nip + nc + s] =
                    -(sum - 1.0);
        }

        double[] direct =
                solveLinearSystem(A, rhs);

        double[] dyDirect =
                Arrays.copyOfRange(
                        direct,
                        0,
                        nip);

        // ------------------------------------------------------------
        // 3. Compare
        // ------------------------------------------------------------

        double errPlus =
                vectorDifferenceNorm(
                        dyPlus,
                        dyDirect);

        double errMinus =
                vectorDifferenceNorm(
                        dyMinus,
                        dyDirect);

        System.out.println();
        System.out.println(
                "Sundman phase-response sign diagnostic");
        System.out.println(
                "--------------------------------------");

        System.out.println(
                "mu = "
                + Arrays.toString(
                        phaseWork.mu));

        System.out.println(
                "cG = "
                + Arrays.toString(
                        phaseWork.response.cG));

        System.out.println(
                "dy(+cG + cA*mu) = "
                + Arrays.toString(dyPlus));

        System.out.println(
                "dy(-cG + cA*mu) = "
                + Arrays.toString(dyMinus));

        System.out.println(
                "dy(direct KKT)   = "
                + Arrays.toString(dyDirect));

        System.out.printf(
                "||dyPlus  - dyDirect||  = %.15e%n",
                errPlus);

        System.out.printf(
                "||dyMinus - dyDirect||  = %.15e%n",
                errMinus);

        if (errMinus < errPlus) {

            System.out.println();
            System.out.println(
                    "RESULT: -cG + cA*mu matches the direct "
                    + "Newton correction better.");

        } else {

            System.out.println();
            System.out.println(
                    "RESULT: +cG + cA*mu matches the direct "
                    + "Newton correction better.");
        }
    }

    /**
     * Euclidean norm of a-b.
     */
    private static double vectorDifferenceNorm(
            double[] a,
            double[] b) {

        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector-length mismatch.");
        }

        double sum = 0.0;

        for (int i = 0; i < a.length; i++) {

            double d =
                    a[i] - b[i];

            sum += d * d;
        }

        return Math.sqrt(sum);
    }

    /**
     * Element response matrix for Sundman's mass-balance equation:
     *
     *     R[A][B] = sum_i dM_A/dy_i * c_iB
     */
    private double[][] calculateMassResponseMatrix() {

        int nc =
                targetAmounts.length;

        int nip =
                phaseWork.model
                        .sundmanNumSiteVariables();

        double[][] R =
                new double[nc][nc];

        for (int a = 0; a < nc; a++) {

            for (int b = 0; b < nc; b++) {

                double sum = 0.0;

                for (int i = 0; i < nip; i++) {

                    sum +=
                            phaseWork.dMdY[a][i]
                            * phaseWork.response.cA[b][i];
                }

                R[a][b] = sum;
            }
        }

        return R;
    }

    /**
     * Element response vector for Sundman's mass-balance equation:
     *
     *     r[A] = sum_i dM_A/dy_i * c_iG
     */
    private double[] calculateMassGResponse() {

        int nc =
                targetAmounts.length;

        int nip =
                phaseWork.model
                        .sundmanNumSiteVariables();

        double[] r =
                new double[nc];

        for (int a = 0; a < nc; a++) {

            double sum = 0.0;

            for (int i = 0; i < nip; i++) {

                sum +=
                        phaseWork.dMdY[a][i]
                        * phaseWork.response.cG[i];
            }

            r[a] = sum;
        }

        return r;
    }

    /**
     * Build the Sundman global equilibrium matrix for the current stable
     * phase set.
     *
     * Current implementation increment:
     *     one stable CEF phase only
     *     fixed T
     *     fixed P
     *     closed system
     *
     * For one phase, the unknowns are:
     *
     *     mu_1 ... mu_nc, DeltaOmega
     *
     * The matrix is the generalization of Sundman Eq. (58):
     *
     *             | M_1 ... M_nc | 0 |
     *             | alpha R_11 ...  | M_1 |
     *             |       ...       | ... |
     *             | alpha R_nc1 ... | M_nc|
     *
     * where
     *
     *     R_AB = sum_i (dM_A/dY_i) c_iB
     *
     * and
     *
     *     q_A = sum_i (dM_A/dY_i) c_iG.
     *
     * The equations are:
     *
     *     sum_A M_A mu_A = G_M
     *
     * and
     *
     *     alpha sum_B R_AB mu_B + M_A DeltaOmega
     *         = alpha q_A
     *
     * for each element A.
     *
     * This is the first global Sundman matrix only.  Phase addition/removal
     * and multiple stable phases are intentionally not included yet.
     */
    private void buildEquilibriumMatrix() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase state has not been initialized.");
        }

        if (stablePhases == null
                || stablePhases.length != 1
                || stablePhases[0] != 0) {

            throw new UnsupportedOperationException(
                    "First global Sundman implementation supports "
                    + "one stable phase only.");
        }

        if (phaseWork.response == null) {
            throw new IllegalStateException(
                    "Phase-response coefficients have not been built.");
        }

        final int nc =
                targetAmounts.length;

        final int nUnknown =
                nc + 1;       // nc chemical potentials + DeltaOmega

        final double omega =
                phaseAmounts[0];

        if (!Double.isFinite(omega)
                || omega <= 0.0) {

            throw new IllegalStateException(
                    "Invalid stable-phase amount: " + omega);
        }

        /*
         * ------------------------------------------------------------
         * Mass-response terms
         *
         *     R_AB =
         *         sum_i dM_A/dY_i * c_iB
         *
         *     q_A =
         *         sum_i dM_A/dY_i * c_iG
         * ------------------------------------------------------------
         */
        double[][] R =
                new double[nc][nc];

        double[] q =
                new double[nc];

        final int nip =
                phaseWork.model
                        .sundmanNumSiteVariables();

        for (int A = 0; A < nc; A++) {

            for (int i = 0; i < nip; i++) {

                double dMAdYi =
                        phaseWork.dMdY[A][i];

                q[A] +=
                        dMAdYi
                        * phaseWork.response.cG[i];

                for (int B = 0; B < nc; B++) {

                    R[A][B] +=
                            dMAdYi
                            * phaseWork.response.cA[B][i];
                }
            }
        }

        /*
         * ------------------------------------------------------------
         * Allocate global system
         *
         * row 0       : phase equilibrium
         * rows 1..nc  : element mass balances
         *
         * columns 0..nc-1 : mu_A
         * column nc       : DeltaOmega
         * ------------------------------------------------------------
         */
        double[][] A =
                new double[nUnknown][nUnknown];

        double[] b =
                new double[nUnknown];

        /*
         * ------------------------------------------------------------
         * Row 0: stable-phase Gibbs equilibrium
         *
         *     G_M = sum_A M_A mu_A
         *
         * Sundman Eq. (49), specialized to one phase.
         * ------------------------------------------------------------
         */
        for (int Aidx = 0;
             Aidx < nc;
             Aidx++) {

            A[0][Aidx] =
                    phaseWork.mA[Aidx];
        }

        /*
         * There is no DeltaOmega term in the phase-equilibrium equation.
         */
        A[0][nc] = 0.0;

        b[0] =
                phaseWork.G;

        /*
         * ------------------------------------------------------------
         * Rows 1..nc: element balance differential equations
         *
         *     omega * sum_B R_AB lambda_B
         *       + M_A * DeltaOmega
         *       = -omega * q_A
         *
         * This is Sundman's Eq. (57)/(58) for the present one-phase
         * fixed-T/P case.
         * ------------------------------------------------------------
         */
        for (int Aidx = 0;
             Aidx < nc;
             Aidx++) {

            int row =
                    Aidx + 1;

            /*
             * Chemical-potential columns.
             */
            for (int B = 0;
                 B < nc;
                 B++) {

                A[row][B] =
                        omega * R[Aidx][B];
            }

            /*
             * Phase amount correction column.
             */
            A[row][nc] =
                    phaseWork.mA[Aidx];

            /*
             * Right-hand side from the closed-system mass balance:
             *
             *     omega * R_AB * lambda_B
             *       + M_A * DeltaOmega
             *       = -omega * q_A
             */
            b[row] =
                    -omega * q[Aidx];
        }

        /*
         * Save for solveEquilibriumMatrix().
         */
        equilibriumMatrix = A;
        equilibriumRhs = b;

        /*
         * Save the response terms in the PhaseWork object for diagnostics.
         */
        phaseWork.massResponse = R;
        phaseWork.massGResponse = q;
    }

    /**
     * Print the current one-phase Sundman equilibrium matrix.
     *
     * This is a development diagnostic and should be removed or disabled
     * once the matrix has been independently verified.
     */
    private void printEquilibriumMatrix() {

        if (equilibriumMatrix == null
                || equilibriumRhs == null) {

            throw new IllegalStateException(
                    "Equilibrium matrix has not been built.");
        }

        System.out.println();
        System.out.println(
                "Sundman equilibrium matrix");
        System.out.println(
                "--------------------------");

        for (int i = 0;
             i < equilibriumMatrix.length;
             i++) {

            System.out.printf(
                    "row %d : ",
                    i);

            for (int j = 0;
                 j < equilibriumMatrix[i].length;
                 j++) {

                System.out.printf(
                        "% .12e ",
                        equilibriumMatrix[i][j]);
            }

            System.out.printf(
                    " | % .12e%n",
                    equilibriumRhs[i]);
        }
    }

    /**
     * Solve the current global Sundman equilibrium system.
     *
     * For the present one-phase, fixed-T, fixed-P implementation the
     * unknown vector is
     *
     *     [ lambda_1 ... lambda_nc | DeltaOmega ]
     *
     * where lambda_A are the NEW chemical potentials obtained from
     * Sundman's equilibrium matrix (Eq. 58).
     *
     * IMPORTANT:
     *     lambda_A are NOT chemical-potential increments.
     *
     * The phase-constitution correction of Eq. (43) uses these new
     * lambda_A values directly:
     *
     *     DeltaY_i = c_iG + sum_A c_iA * lambda_A
     *
     * State variables are not modified in this method.
     */
    private void solveEquilibriumMatrix() {

        if (equilibriumMatrix == null
                || equilibriumRhs == null) {

            throw new IllegalStateException(
                    "Global equilibrium matrix has not been built.");
        }

        final int nc =
                targetAmounts.length;

        final int n =
                equilibriumRhs.length;

        if (n != nc + 1) {

            throw new IllegalStateException(
                    "Current one-phase global system must have "
                    + (nc + 1)
                    + " unknowns, but has "
                    + n);
        }

        if (equilibriumMatrix.length != n) {

            throw new IllegalStateException(
                    "Global equilibrium matrix row dimension mismatch.");
        }

        for (int i = 0; i < n; i++) {

            if (equilibriumMatrix[i] == null
                    || equilibriumMatrix[i].length != n) {

                throw new IllegalStateException(
                        "Global equilibrium matrix must be square.");
            }

            if (!Double.isFinite(equilibriumRhs[i])) {

                throw new IllegalStateException(
                        "Non-finite RHS at row "
                        + i + ": "
                        + equilibriumRhs[i]);
            }

            for (int j = 0; j < n; j++) {

                if (!Double.isFinite(
                        equilibriumMatrix[i][j])) {

                    throw new IllegalStateException(
                            "Non-finite matrix entry ["
                            + i + "][" + j + "]: "
                            + equilibriumMatrix[i][j]);
                }
            }
        }

        Matrix A =
                new Matrix(equilibriumMatrix);

        Matrix b =
                new Matrix(
                        equilibriumRhs,
                        equilibriumRhs.length);

        Matrix x;

        try {

            /*
             * This is a square global Newton/Sundman system.
             * Use the ordinary project matrix solver.
             */
            x = A.solve(b);

        } catch (RuntimeException ex) {

            throw new IllegalStateException(
                    "Failed to solve global Sundman "
                    + "equilibrium matrix.",
                    ex);
        }

        double[] solution =
                x.getColumnPackedCopy();

        if (solution.length != n) {

            throw new IllegalStateException(
                    "Unexpected global solution length: "
                    + solution.length
                    + ", expected " + n);
        }

        for (int i = 0; i < solution.length; i++) {

            if (!Double.isFinite(solution[i])) {

                throw new IllegalStateException(
                        "Non-finite global solution at index "
                        + i + ": "
                        + solution[i]);
            }
        }

        /*
         * ------------------------------------------------------------
         * Partition the solution.
         *
         * solution[0 .. nc-1] = NEW chemical potentials lambda_A
         * solution[nc]         = DeltaOmega
         * ------------------------------------------------------------
         */
        newLambda =
                Arrays.copyOfRange(
                        solution,
                        0,
                        nc);

        deltaPhaseAmounts =
                new double[1];

        deltaPhaseAmounts[0] =
                solution[nc];

        /*
         * Keep complete vector for diagnostics.
         */
        equilibriumUnknowns =
                solution.clone();

        /*
         * ------------------------------------------------------------
         * Verify A*x = b.
         * ------------------------------------------------------------
         */
        double residual =
                equilibriumLinearResidualNorm();

        System.out.println();
        System.out.println(
                "Global Sundman solution");
        System.out.println(
                "-----------------------");

        System.out.println(
                "newLambda = "
                + Arrays.toString(newLambda));

        System.out.printf(
                "DeltaOmega = %.15e%n",
                deltaPhaseAmounts[0]);

        System.out.println(
                "Solution = "
                + Arrays.toString(equilibriumUnknowns));

        System.out.printf(
                "Linear-system residual = %.15e%n",
                residual);

        if (residual > 1.0e-8) {

            throw new IllegalStateException(
                    "Excessive global linear-system residual: "
                    + residual);
        }
    }

    /**
     * Verify the residual of the just-solved global linear system.
     */
    private double equilibriumLinearResidualNorm() {

        if (equilibriumMatrix == null
                || equilibriumRhs == null
                || equilibriumUnknowns == null) {

            throw new IllegalStateException(
                    "Global equilibrium system has not been solved.");
        }

        double sum2 = 0.0;

        for (int i = 0;
             i < equilibriumMatrix.length;
             i++) {

            double value = 0.0;

            for (int j = 0;
                 j < equilibriumMatrix[i].length;
                 j++) {

                value +=
                        equilibriumMatrix[i][j]
                        * equilibriumUnknowns[j];
            }

            double r =
                    value - equilibriumRhs[i];

            sum2 += r * r;
        }

        return Math.sqrt(sum2);
    }

    /**
     * Calculate the internal-constitution correction from Sundman's
     * phase-response equation (Eq. 43).
     *
     * At fixed T and P:
     *
     *     DeltaY_i =
     *          c_iG
     *          + sum_A c_iA * lambda_A
     *
     * where lambda_A are the NEW chemical potentials obtained from the
     * global equilibrium matrix.
     *
     * IMPORTANT:
     *     lambda_A are not Delta(mu_A).
     *
     * The correction is calculated only; the accepted phase state is
     * not modified here.
     */
    private void calculateInternalCorrections() {

        if (phaseWork == null) {

            throw new IllegalStateException(
                    "Phase state has not been initialized.");
        }

        if (phaseWork.response == null) {

            throw new IllegalStateException(
                    "Phase response has not been calculated.");
        }

        if (newLambda == null) {

            throw new IllegalStateException(
                    "New chemical potentials have not been "
                    + "obtained from the global equilibrium system.");
        }

        final int nip =
                phaseWork.model
                        .sundmanNumSiteVariables();

        final int nc =
                targetAmounts.length;

        if (newLambda.length != nc) {

            throw new IllegalStateException(
                    "Chemical-potential vector length mismatch.");
        }

        deltaPhaseInternalVars =
                new double[phaseModels.size()][];

        double[] deltaY =
                new double[nip];

        /*
         * Sundman Eq. (43):
         *
         *     DeltaY_i =
         *          c_iG
         *          + sum_A c_iA * lambda_A
         */
        for (int i = 0; i < nip; i++) {

            double value =
                    phaseWork.response.cG[i];

            for (int A = 0; A < nc; A++) {

                value +=
                        phaseWork.response.cA[A][i]
                        * newLambda[A];
            }

            if (!Double.isFinite(value)) {

                throw new IllegalStateException(
                        "Non-finite DeltaY[" + i + "]: "
                        + value);
            }

            deltaY[i] = value;
        }

        deltaPhaseInternalVars[0] =
                deltaY;

        /*
         * ------------------------------------------------------------
         * Diagnostics
         * ------------------------------------------------------------
         */
        System.out.println();
        System.out.println(
                "Internal-variable correction");
        System.out.println(
                "----------------------------");

        System.out.println(
                "newLambda = "
                + Arrays.toString(newLambda));

        System.out.println(
                "cG = "
                + Arrays.toString(
                        phaseWork.response.cG));

        System.out.println(
                "DeltaY = "
                + Arrays.toString(deltaY));

        System.out.printf(
                "||DeltaY|| = %.15e%n",
                vectorNorm(deltaY));

        double[] predictedY =
                new double[nip];

        for (int i = 0; i < nip; i++) {

            predictedY[i] =
                    phaseWork.y[i]
                    + deltaY[i];
        }

        boolean valid =
                phaseWork.model.isValid(
                        predictedY);

        System.out.println(
                "Predicted Y = "
                + Arrays.toString(predictedY));

        System.out.println(
                "Predicted Y physically valid = "
                + valid);
    }

    /**
     * Returns the norm of the current predicted CEF constitution change.
     */
    private double internalCorrectionNorm() {

        if (deltaPhaseInternalVars == null
                || deltaPhaseInternalVars.length == 0
                || deltaPhaseInternalVars[0] == null) {

            return Double.POSITIVE_INFINITY;
        }

        return vectorNorm(
                deltaPhaseInternalVars[0]);
    }

    /**
     * Recompute the sublattice Lagrange multipliers gamma for the
     * current accepted constitution and accepted chemical potentials.
     *
     * The stationarity equation is
     *
     *     G_Y - J_M^T * mu - C^T * gamma = 0.
     *
     * For each sublattice the corresponding row of C contains ones,
     * therefore gamma_s is simply the common value of
     *
     *     G_i - sum_A (dM_A/dY_i) * mu_A
     *
     * over all constituents i belonging to sublattice s.
     *
     * At a converged state all values within a sublattice should agree
     * to numerical precision.
     */
    private void recomputeSublatticeMultipliers() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase state is not available.");
        }

        if (phaseWork.mu == null) {
            throw new IllegalStateException(
                    "Chemical potentials are not available.");
        }

        int nip =
                phaseWork.model
                        .sundmanNumSiteVariables();

        int nc =
                targetAmounts.length;

        int ns =
                phaseWork.model
                        .sundmanNumSublattices();

        int[] offsets =
                phaseWork.model
                        .sundmanOffsets();

        int[] nconst =
                phaseWork.model
                        .sundmanConstituentsPerSublattice();

        double[] gamma =
                new double[ns];

        for (int s = 0; s < ns; s++) {

            int begin =
                    offsets[s];

            int end =
                    begin + nconst[s];

            double sum = 0.0;

            int count = 0;

            for (int i = begin;
                 i < end;
                 i++) {

                double value =
                        phaseWork.gy[i];

                for (int A = 0;
                     A < nc;
                     A++) {

                    value -=
                            phaseWork.dMdY[A][i]
                            * phaseWork.mu[A];
                }

                sum += value;
                count++;
            }

            gamma[s] =
                    sum / count;
        }

        phaseWork.gamma =
                gamma.clone();
    }

    /**
     * Accept the current Sundman correction with backtracking.
     *
     * For the present implementation:
     *   - one stable CEF phase
     *   - fixed T and P
     *   - closed system
     *
     * The undamped correction is:
     *
     *     DeltaY_i =
     *         c_iG + sum_A c_iA * lambda_A
     *
     * and
     *
     *     DeltaOmega
     *
     * The full Newton/extrapolation step may leave the physical
     * constitution domain when the current state is far from equilibrium.
     * We therefore reduce the common step length until the trial
     * constitution is physically valid and the constrained Gibbs energy
     * does not increase.
     *
     * The accepted update is:
     *
     *     Y       <- Y       + alpha * DeltaY
     *     Omega   <- Omega   + alpha * DeltaOmega
     *     lambda  <- lambda  + alpha * (newLambda - lambda)
     *
     * This is a numerical globalization device; it does not alter the
     * Sundman thermodynamic equations.
     */
    private void updateState() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase state has not been initialized.");
        }

        if (deltaPhaseInternalVars == null
                || deltaPhaseInternalVars.length == 0
                || deltaPhaseInternalVars[0] == null) {

            throw new IllegalStateException(
                    "Internal-variable correction has not been calculated.");
        }

        if (newLambda == null) {
            throw new IllegalStateException(
                    "New chemical potentials have not been calculated.");
        }

        if (deltaPhaseAmounts == null
                || deltaPhaseAmounts.length != 1) {

            throw new IllegalStateException(
                    "Single-phase amount correction is missing.");
        }

        final double[] oldY =
                phaseWork.y.clone();

        final double oldG =
                phaseWork.G;

        final double oldOmega =
                phaseAmounts[0];

        final double[] oldMu =
                (phaseWork.mu != null)
                        ? phaseWork.mu.clone()
                        : new double[newLambda.length];

        final double[] deltaY =
                deltaPhaseInternalVars[0];

        final double deltaOmega =
                deltaPhaseAmounts[0];

        /*
         * We use the new lambda from the global Sundman matrix as the
         * target value. With alpha=1 this is the full paper update.
         * For a damped step we interpolate toward it.
         */
        double alpha = 1.0;

        boolean accepted = false;

        final int MAX_BACKTRACK =
                30;

        double[] trialY =
                new double[oldY.length];

        double[] trialMu =
                new double[oldMu.length];

        double trialOmega =
                oldOmega;

        double trialG =
                Double.NaN;

        for (int attempt = 0;
             attempt < MAX_BACKTRACK;
             attempt++) {

            // ------------------------------------------------------------
            // 1. Trial constitution
            // ------------------------------------------------------------

            for (int i = 0;
                 i < oldY.length;
                 i++) {

                trialY[i] =
                        oldY[i]
                        + alpha * deltaY[i];
            }

            /*
             * Never evaluate an invalid CEF constitution.
             */
            if (!phaseWork.model.isValid(trialY)) {

                alpha *= 0.5;
                continue;
            }

            // ------------------------------------------------------------
            // 2. Trial phase amount
            // ------------------------------------------------------------

            trialOmega =
                    oldOmega
                    + alpha * deltaOmega;

            if (!Double.isFinite(trialOmega)
                    || trialOmega <= 0.0) {

                alpha *= 0.5;
                continue;
            }

            // ------------------------------------------------------------
            // 3. Trial chemical potentials
            // ------------------------------------------------------------

            for (int A = 0;
                 A < oldMu.length;
                 A++) {

                trialMu[A] =
                        oldMu[A]
                        + alpha
                        * (newLambda[A] - oldMu[A]);
            }

            // ------------------------------------------------------------
            // 4. Trial Gibbs energy
            //
            // The present problem is a one-phase closed-system problem
            // with fixed element amounts, so comparing the constrained
            // phase Gibbs energy provides a useful globalization test.
            // ------------------------------------------------------------

            trialG =
                    phaseWork.model.sundmanG(
                            T,
                            trialY);

            if (!Double.isFinite(trialG)) {

                alpha *= 0.5;
                continue;
            }

            /*
             * Accept a step that lowers G.
             *
             * If we are already at numerical equilibrium, also allow an
             * essentially neutral step.
             */
            final double ENERGY_TOL =
                    1.0e-10
                    * Math.max(
                            1.0,
                            Math.abs(oldG));

            if (trialG <= oldG + ENERGY_TOL) {

                accepted = true;
                break;
            }

            alpha *= 0.5;
        }

        if (!accepted) {

            throw new IllegalStateException(
                    "Could not find a physically valid, "
                    + "non-increasing Gibbs-energy step. "
                    + "Full DeltaY="
                    + Arrays.toString(deltaY));
        }

        // ------------------------------------------------------------
        // 5. Commit accepted update
        // ------------------------------------------------------------

        phaseWork.y =
                trialY.clone();

        phaseWork.G =
                trialG;

        phaseAmounts[0] =
                trialOmega;

        phaseWork.mu =
                trialMu.clone();

        mu =
                trialMu.clone();

        /*
         * Recompute gamma for the newly accepted mu and Y.
         */
        recomputeSublatticeMultipliers();

        phaseInternalVars[0] =
                phaseWork.y.clone();

        acceptedStepScale =
                alpha;

        /*
         * Record the accepted step, not the undamped step.
         */
        lastStep =
                alpha * vectorNorm(deltaY);

        System.out.println();
        System.out.println(
                "Accepted state update");
        System.out.println(
                "---------------------");

        System.out.printf(
                "Step scale   = %.15e%n",
                alpha);

        System.out.printf(
                "DeltaOmega   = %.15e%n",
                alpha * deltaOmega);

        System.out.printf(
                "G(old)       = %.15f%n",
                oldG);

        System.out.printf(
                "G(new)       = %.15f%n",
                trialG);

        System.out.printf(
                "Delta G      = %.15e%n",
                trialG - oldG);

        System.out.println(
                "Y(new)       = "
                + Arrays.toString(phaseWork.y));

        System.out.println(
                "mu(new)      = "
                + Arrays.toString(mu));
    }

    /**
     * Validate the current accepted one-phase state.
     *
     * Checks:
     *   1. physical CEF constitution
     *   2. finite thermodynamic quantities
     *   3. sublattice normalization
     *   4. positive finite phase amount
     *   5. system element mass balance
     *   6. phase-equilibrium relation
     *   7. phase stationarity relation
     *
     * Current scope:
     *   one stable CEF phase, fixed T and P, closed system.
     */
    private void validateState() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase state is not available.");
        }

        if (phaseAmounts == null
                || phaseAmounts.length != 1) {

            throw new IllegalStateException(
                    "Expected exactly one phase amount.");
        }

        if (stablePhases == null
                || stablePhases.length != 1
                || stablePhases[0] != 0) {

            throw new IllegalStateException(
                    "Current validation supports one stable phase only.");
        }

        // ------------------------------------------------------------
        // 1. Basic finiteness
        // ------------------------------------------------------------

        checkFinite(
                "phase Gibbs energy",
                phaseWork.G);

        if (phaseWork.y == null) {
            throw new IllegalStateException(
                    "Phase constitution is null.");
        }

        if (!phaseWork.model.isValid(phaseWork.y)) {
            throw new IllegalStateException(
                    "CEF phase constitution is physically invalid: "
                    + Arrays.toString(phaseWork.y));
        }

        for (int i = 0;
             i < phaseWork.y.length;
             i++) {

            checkFinite(
                    "Y[" + i + "]",
                    phaseWork.y[i]);

            if (phaseWork.y[i] < -1.0e-12
                    || phaseWork.y[i] > 1.0 + 1.0e-12) {

                throw new IllegalStateException(
                        "Unphysical site fraction Y[" + i + "] = "
                                + phaseWork.y[i]);
            }
        }

        if (phaseWork.mA == null
                || phaseWork.dMdY == null
                || phaseWork.gy == null
                || phaseWork.gyy == null) {

            throw new IllegalStateException(
                    "Incomplete phase thermodynamic state.");
        }

        // ------------------------------------------------------------
        // 2. Phase amount
        // ------------------------------------------------------------

        double omega =
                phaseAmounts[0];

        checkFinite(
                "phase amount",
                omega);

        if (omega <= 0.0) {
            throw new IllegalStateException(
                    "Stable phase has non-positive amount: "
                            + omega);
        }

        // ------------------------------------------------------------
        // 3. Sublattice normalization
        // ------------------------------------------------------------

        int[] offsets =
                phaseWork.model.sundmanOffsets();

        int[] nconst =
                phaseWork.model
                        .sundmanConstituentsPerSublattice();

        double maxConstraintResidual =
                0.0;

        for (int s = 0;
             s < nconst.length;
             s++) {

            double sum = 0.0;

            for (int i = 0;
                 i < nconst[s];
                 i++) {

                sum +=
                        phaseWork.y[
                                offsets[s] + i];
            }

            double residual =
                    sum - 1.0;

            maxConstraintResidual =
                    Math.max(
                            maxConstraintResidual,
                            Math.abs(residual));

            if (Math.abs(residual) > 1.0e-10) {

                throw new IllegalStateException(
                        "Sublattice "
                                + (s + 1)
                                + " normalization residual = "
                                + residual);
            }
        }

        // ------------------------------------------------------------
        // 4. Element mass balance
        //
        //     N_A(target) = omega * M_A
        // ------------------------------------------------------------

        if (targetAmounts.length != phaseWork.mA.length) {

            throw new IllegalStateException(
                    "Target amount/component dimension mismatch.");
        }

        double maxMassResidual =
                0.0;

        for (int A = 0;
             A < targetAmounts.length;
             A++) {

            double residual =
                    targetAmounts[A]
                    - omega * phaseWork.mA[A];

            maxMassResidual =
                    Math.max(
                            maxMassResidual,
                            Math.abs(residual));

            checkFinite(
                    "mass-balance residual[" + A + "]",
                    residual);

            if (Math.abs(residual) > 1.0e-9) {

                throw new IllegalStateException(
                        "Element mass balance failed for component "
                                + A
                                + ": residual = "
                                + residual);
            }
        }

        // ------------------------------------------------------------
        // 5. Phase equilibrium relation
        //
        //     G_M = sum_A M_A * mu_A
        // ------------------------------------------------------------

        if (phaseWork.mu == null
                || phaseWork.mu.length != targetAmounts.length) {

            throw new IllegalStateException(
                    "Chemical-potential vector is unavailable.");
        }

        double phaseChemicalPotentialSum =
                0.0;

        for (int A = 0;
             A < targetAmounts.length;
             A++) {

            checkFinite(
                    "mu[" + A + "]",
                    phaseWork.mu[A]);

            phaseChemicalPotentialSum +=
                    phaseWork.mA[A]
                    * phaseWork.mu[A];
        }

        double phaseEquilibriumResidual =
                phaseWork.G
                - phaseChemicalPotentialSum;

        checkFinite(
                "phase-equilibrium residual",
                phaseEquilibriumResidual);

        // ------------------------------------------------------------
        // 6. Stationarity
        //
        //     G_Y - J_M^T mu - C^T gamma = 0
        // ------------------------------------------------------------

        double stationarityResidual =
                singlePhaseStationarityNorm();

        checkFinite(
                "stationarity residual",
                stationarityResidual);

        // ------------------------------------------------------------
        // 7. Store useful residual information
        // ------------------------------------------------------------

        lastResidual =
                Math.max(
                        Math.max(
                                maxMassResidual,
                                Math.abs(
                                        phaseEquilibriumResidual)),
                        Math.max(
                                maxConstraintResidual,
                                stationarityResidual));

        System.out.println();
        System.out.println(
                "State validation");
        System.out.println(
                "----------------");
        System.out.printf(
                "max mass-balance residual = %.6e%n",
                maxMassResidual);
        System.out.printf(
                "phase-equilibrium residual = %.6e%n",
                Math.abs(phaseEquilibriumResidual));
        System.out.printf(
                "sublattice residual = %.6e%n",
                maxConstraintResidual);
        System.out.printf(
                "stationarity residual = %.6e%n",
                stationarityResidual);
        System.out.printf(
                "overall state residual = %.6e%n",
                lastResidual);
    }

    private void updateStablePhaseSet() {
        // To be implemented.
    }

    /**
     * Check convergence of the current one-phase Sundman equilibrium.
     *
     * Convergence requires BOTH:
     *
     *   A. small thermodynamic residuals
     *
     *      mass balance
     *      phase Gibbs relation
     *      phase stationarity
     *      sublattice constraints
     *
     *   B. small accepted numerical changes
     *
     *      internal constitution
     *      chemical potentials
     *      phase amount
     *
     * A small step alone is not considered sufficient evidence of
     * equilibrium.
     */
    private boolean checkConvergence() {

        if (phaseWork == null) {
            return false;
        }

        if (phaseAmounts == null
                || phaseAmounts.length != 1) {
            return false;
        }

        final double residualTol =
                tolerance;

        final double stepTol =
                Math.max(
                        tolerance,
                        1.0e-12);

        // ------------------------------------------------------------
        // 1. Sublattice normalization
        // ------------------------------------------------------------

        int[] offsets =
                phaseWork.model.sundmanOffsets();

        int[] nconst =
                phaseWork.model
                        .sundmanConstituentsPerSublattice();

        double maxConstraintResidual =
                0.0;

        for (int s = 0;
             s < nconst.length;
             s++) {

            double sum = 0.0;

            for (int i = 0;
                 i < nconst[s];
                 i++) {

                sum +=
                        phaseWork.y[
                                offsets[s] + i];
            }

            maxConstraintResidual =
                    Math.max(
                            maxConstraintResidual,
                            Math.abs(sum - 1.0));
        }

        // ------------------------------------------------------------
        // 2. Element mass balance
        //
        //     N_A - omega*M_A
        // ------------------------------------------------------------

        double maxMassResidual =
                0.0;

        double omega =
                phaseAmounts[0];

        for (int A = 0;
             A < targetAmounts.length;
             A++) {

            double residual =
                    targetAmounts[A]
                    - omega * phaseWork.mA[A];

            maxMassResidual =
                    Math.max(
                            maxMassResidual,
                            Math.abs(residual));
        }

        // ------------------------------------------------------------
        // 3. Phase Gibbs-equilibrium residual
        //
        //     G_M - sum_A M_A*mu_A
        // ------------------------------------------------------------

        double muResidual =
                phaseWork.G;

        for (int A = 0;
             A < targetAmounts.length;
             A++) {

            muResidual -=
                    phaseWork.mA[A]
                    * phaseWork.mu[A];
        }

        // ------------------------------------------------------------
        // 4. Internal phase stationarity
        // ------------------------------------------------------------

        double stationarityResidual =
                singlePhaseStationarityNorm();

        // ------------------------------------------------------------
        // 5. Thermodynamic residual
        // ------------------------------------------------------------

        double thermodynamicResidual =
                Math.max(
                        Math.max(
                                maxMassResidual,
                                Math.abs(muResidual)),
                        Math.max(
                                maxConstraintResidual,
                                stationarityResidual));

        // ------------------------------------------------------------
        // 6. Constitution step
        // ------------------------------------------------------------

        double yStep =
                internalCorrectionNorm();

        // ------------------------------------------------------------
        // 7. Phase-amount step
        // ------------------------------------------------------------

        double omegaStep =
                (deltaPhaseAmounts != null
                        && deltaPhaseAmounts.length == 1)
                        ? Math.abs(
                                acceptedStepScale
                                * deltaPhaseAmounts[0])
                        : Double.POSITIVE_INFINITY;

        // ------------------------------------------------------------
        // 8. Chemical-potential step
        //
        // newLambda is the newly calculated Sundman lambda.  Compare
        // it with the currently accepted phaseWork.mu.
        // ------------------------------------------------------------

        double muStep =
                0.0;

        if (newLambda != null
                && phaseWork.mu != null
                && newLambda.length == phaseWork.mu.length) {

            for (int A = 0;
                 A < newLambda.length;
                 A++) {

                muStep =
                        Math.max(
                                muStep,
                                Math.abs(
                                        newLambda[A]
                                        - phaseWork.mu[A]));
            }
        }

        /*
         * When updateState() has already accepted the new lambda, the
         * difference above is normally zero. Therefore the actual
         * accepted state change is primarily represented by lastStep.
         */
        double acceptedStep =
                lastStep;

        // ------------------------------------------------------------
        // 9. Final decision
        // ------------------------------------------------------------

        boolean residualsConverged =
                thermodynamicResidual
                        <= residualTol;

        boolean stepsConverged =
                acceptedStep
                        <= stepTol
                && yStep
                        <= stepTol
                && omegaStep
                        <= stepTol;

        boolean converged =
                residualsConverged
                        && stepsConverged;

        System.out.println();
        System.out.println(
                "Convergence check");
        System.out.println(
                "-----------------");

        System.out.printf(
                "mass balance      = %.6e%n",
                maxMassResidual);

        System.out.printf(
                "phase G relation  = %.6e%n",
                Math.abs(muResidual));

        System.out.printf(
                "sublattice        = %.6e%n",
                maxConstraintResidual);

        System.out.printf(
                "stationarity      = %.6e%n",
                stationarityResidual);

        System.out.printf(
                "thermodynamic     = %.6e%n",
                thermodynamicResidual);

        System.out.printf(
                "accepted step     = %.6e%n",
                acceptedStep);

        System.out.printf(
                "DeltaY norm       = %.6e%n",
                yStep);

        System.out.printf(
                "DeltaOmega        = %.6e%n",
                omegaStep);

        System.out.println(
                "Residual criterion = "
                + residualsConverged);

        System.out.println(
                "Step criterion     = "
                + stepsConverged);

        System.out.println(
                "CONVERGED          = "
                + converged);

        return converged;
    }

    // ================================================================
    // Configuration
    // ================================================================

    public void setMaxIterations(int maxIterations) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException(
                    "maxIterations must be positive.");
        }
        this.maxIterations = maxIterations;
    }

    public void setTolerance(double tolerance) {
        if (!(tolerance > 0.0)) {
            throw new IllegalArgumentException(
                    "tolerance must be positive.");
        }
        this.tolerance = tolerance;
    }
}
