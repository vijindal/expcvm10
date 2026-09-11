package calc.equil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import system.model.cef.CefGibbs;
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

    /** Total Gibbs energy of the accepted stable-phase state before update. */
    private double previousTotalG;

    /** Element amounts of the accepted stable-phase state before update. */
    private double[] previousTotalAmounts;

    /** Stable-phase amounts at the exact point where the global matrix was built. */
    private double[] previousPhaseAmounts;

    /** Stable-phase Gibbs energies at the exact point where the global matrix was built. */
    private double[] previousPhaseG;

    /** Element amounts M_A of each stable phase at matrix-assembly point. */
    private double[][] previousPhaseMA;

    /**
     * Chemical potentials from BEFORE the most recent updateState() call,
     * used by checkConvergence() to compute the actual DeltaMu = newLambda
     * - previousMu. mu itself is overwritten with newLambda during
     * updateState(), so comparing newLambda against the (already-updated)
     * mu would spuriously always give DeltaMu = 0.
     */
    private double[] previousMu;

    // ================================================================
    // Single-phase kernel state (first Sundman implementation increment)
    // ================================================================

    private double lastResidual;
    private double lastStep;

    private PhaseWork phaseWork;

    /**
     * Phase-level Sundman state for every candidate phase.
     *
     * Index i corresponds to phaseModels.get(i).
     */
    private List<PhaseWork> phaseWorks;

    private static final double SOLVER_TOL = 1.0e-10;
    private static final int MAX_INTERNAL_ITER = 50;

    /**
     * Working data for one CEF phase.
     */
    private static final class PhaseWork {

        final CefGibbs model;

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

        PhaseWork(CefGibbs model) {
            this.model = model;
        }
    }

    /**
     * Sundman phase-response coefficients (Eq. 43-44): the phase-matrix
     * inverse e_ij together with c_iG and c_iA, used at fixed T, P to
     * express the site-fraction correction as
     *
     *     Delta y_i = c_iG + sum_A c_iA * lambda_A.
     *
     * lambda_A is the new (absolute) chemical potential from Sundman's
     * Eq. (58), not an increment Delta mu_A.
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
    // Test-only controlled initialization hook
    // ================================================================

    /**
     * Prescribed initial multiphase state used by {@link #initialize()}
     * in place of the normal single-phase starting guess, when set.
     *
     * This exists ONLY to let end-to-end tests reproduce a controlled
     * starting point (e.g. a known two-phase constitution/amount set)
     * while still exercising the actual public {@link #solve} iteration.
     * It does not change solver mathematics.
     */
    private static final class TestInitialState {

        final int[] stablePhases;
        final double[][] y;
        final double[] phaseAmounts;

        TestInitialState(
                int[] stablePhases,
                double[][] y,
                double[] phaseAmounts) {

            this.stablePhases = stablePhases;
            this.y = y;
            this.phaseAmounts = phaseAmounts;
        }
    }

    private TestInitialState testInitialState;

    /**
     * Package-private test hook: prescribe the initial stable-phase set,
     * per-phase constitutions, and phase amounts that {@link #solve}
     * will start from, instead of the normal single-phase initial guess.
     *
     * Intended for end-to-end tests that need to reproduce a controlled
     * starting point (already validated against a separate matrix-level
     * test) through the actual public solve() iteration. This does not
     * alter solver mathematics, line search, convergence, or phase
     * management -- it only overrides the starting point that
     * {@link #initialize()} would otherwise construct.
     *
     * Must be called before {@link #solve}.
     *
     * @param stablePhases   candidate-phase indices that start stable
     * @param initialY       initial site-fraction vector per stable
     *                       phase, indexed in the same order as
     *                       {@code stablePhases}
     * @param initialAmounts initial phase amount per stable phase,
     *                       indexed in the same order as
     *                       {@code stablePhases}
     */
    void setInitialStateForTest(
            int[] stablePhases,
            double[][] initialY,
            double[] initialAmounts) {

        if (stablePhases == null
                || initialY == null
                || initialAmounts == null) {

            throw new IllegalArgumentException(
                    "Test initial state arrays must not be null.");
        }

        if (stablePhases.length != initialY.length
                || stablePhases.length != initialAmounts.length) {

            throw new IllegalArgumentException(
                    "Test initial state arrays must have matching "
                    + "stable-phase length.");
        }

        this.testInitialState =
                new TestInitialState(
                        stablePhases.clone(),
                        initialY.clone(),
                        initialAmounts.clone());
    }

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
             * For fixed T and P the global unknowns are:
             *
             *   lambda_A  (new, absolute chemical potentials -- not
             *              increments Delta mu_A)
             *   Δω_α
             *
             * The equations are:
             *
             *   G_M^α - Σ_A M_A^α λ_A = 0
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
             *   lambda_A = new chemical potentials
             *   DeltaOmega_alpha = phase-amount corrections
             *
             * lambda_A are the new chemical potentials of Sundman's
             * global equilibrium equations, not Delta(mu_A).
             */
            solveEquilibriumMatrix();

            // ========================================================
            // STEP 5
            // Calculate internal-variable corrections
            // ========================================================

            /*
             * DeltaY_i^alpha =
             *     c_iG^alpha
             *     + Σ_A c_iA^alpha * lambda_A
             *
             * for fixed T and P.
             *
             * lambda_A are the new chemical potentials obtained
             * from the global Sundman equilibrium matrix.
             *
             * Variable-T/P terms will later add:
             *
             *       c_iT^α ΔT
             *       c_iP^α ΔP
             */
            calculateInternalCorrections();

            // ========================================================
            // DIAGNOSTIC: pre-update Sundman-equation snapshot
            // ========================================================
            //
            // Print the state the update is about to apply, so the
            // Sundman equations themselves can be inspected directly
            // rather than by tuning a damping factor.
            // ========================================================

            System.out.println();
            System.out.println(
                    "=== Sundman iteration " + iteration + " ===");

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                int p =
                        stablePhases[k];

                PhaseWork w =
                        phaseWorks.get(p);

                System.out.println(
                        "Phase " + k
                        + " (" + w.model.phaseName() + ")"
                        + " omega = " + phaseAmounts[k]);

                System.out.println(
                        "  G = " + w.G);

                double maxAbsCG =
                        0.0;

                for (double v : w.response.cG) {
                    maxAbsCG =
                            Math.max(
                                    maxAbsCG,
                                    Math.abs(v));
                }

                System.out.println(
                        "  max|cG| = " + maxAbsCG);
            }

            System.out.println("lambda:");
            System.out.println(
                    "  " + Arrays.toString(newLambda));

            System.out.println("DeltaOmega:");
            System.out.println(
                    "  " + Arrays.toString(deltaPhaseAmounts));

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                int p =
                        stablePhases[k];

                PhaseWork w =
                        phaseWorks.get(p);

                System.out.println(
                        "DeltaY (phase "
                        + w.model.phaseName() + "):");
                System.out.println(
                        "  " + Arrays.toString(
                                deltaPhaseInternalVars[k]));
            }

            for (int A = 0;
                 A < targetAmounts.length;
                 A++) {

                double represented =
                        0.0;

                for (int k = 0;
                     k < stablePhases.length;
                     k++) {

                    int p =
                            stablePhases[k];

                    PhaseWork w =
                            phaseWorks.get(p);

                    represented +=
                            phaseAmounts[k]
                            * w.mA[A];
                }

                System.out.println(
                        "Mass residual A=" + A
                        + " : "
                        + (targetAmounts[A] - represented));
            }

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
             * Every candidate phase (stable or not) has its own
             * PhaseWork, evaluated by evaluateAllPhases() every
             * iteration -- do not assume candidate 0 is the only
             * active phase; use the PhaseWork keyed by candidate
             * index i directly rather than re-deriving state from
             * the model.
             */
            if (phaseWorks == null
                    || i >= phaseWorks.size()
                    || phaseWorks.get(i) == null
                    || phaseWorks.get(i).model != model
                    || phaseWorks.get(i).y == null) {

                throw new IllegalStateException(
                        "Missing or unsynchronized PhaseWork for "
                        + "candidate phase "
                        + i + " ("
                        + model.phaseName()
                        + "); evaluateAllPhases() must run before "
                        + "buildEquilibriumResult().");
            }

            PhaseWork work =
                    phaseWorks.get(i);

            y =
                    work.y.clone();

            g =
                    work.G;

            mA =
                    work.mA.clone();

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

            /*
             * Sundman Eq. (62), driving force:
             *
             *     D^beta = -G^beta + sum_A lambda_A * M_A^beta
             */
            double drivingForce =
                    -g;

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

                /*
                 * phaseAmounts is indexed by STABLE SLOT, not candidate
                 * index -- do not assume candidatePhase[i] ->
                 * phaseAmounts[i]. Find the stable slot k such that
                 * stablePhases[k] == i explicitly.
                 */
                double amount =
                        0.0;

                if (stablePhases != null
                        && phaseAmounts != null) {

                    for (int k = 0;
                         k < stablePhases.length;
                         k++) {

                        if (stablePhases[k] == i
                                && k < phaseAmounts.length) {

                            amount =
                                    phaseAmounts[k];

                            break;
                        }
                    }
                }

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
     * Initialize the phase-indexed V2 state.
     *
     * Every candidate phase is evaluated once with a constitution seeded
     * from the overall requested composition
     * (initializeSinglePhaseState()); this seed is only used for the
     * per-candidate PhaseWork bookkeeping (phaseInternalVars, the
     * metastable candidates' constitutions) and is immediately overwritten
     * for whichever phases {@link GridMinimizer} selects as stable.
     *
     * The initial stable-phase set, per-phase constitutions, and initial
     * phase amounts are then obtained from {@link GridMinimizer}, which
     * samples every candidate's internal degrees of freedom directly
     * (site fractions) -- exact endmembers, endmember-pair edges, and
     * Halton-sampled interior points -- and takes the lower convex hull of
     * the combined (composition, G/atom) point cloud, mirroring
     * pycalphad's {@code calculate()}/{@code starting_point()} global
     * initializer. This is what allows the solver to detect a miscibility
     * gap and select an initial stable-phase set from among the
     * candidates, rather than always starting at candidate 0.
     *
     * Remaining scope limitation: CEF phases only (GridMinimizer itself is
     * model-agnostic, but the PhaseWork bookkeeping below still requires
     * CefGibbs).
     *
     * For a KNOWN, already-validated fixed multiphase starting point
     * (e.g. a controlled two-phase test), use
     * {@link #setInitialStateForTest} to bypass GridMinimizer entirely.
     * Phase selection during the iteration itself remains
     * updateStablePhaseSet()'s responsibility (currently a no-op).
     */
    private void initialize() {

        if (phaseModels == null
                || phaseModels.isEmpty()) {

            throw new IllegalStateException(
                    "No candidate phase models are available.");
        }

        final int nph =
                phaseModels.size();

        phaseWorks =
                new ArrayList<>(nph);

        /*
         * ------------------------------------------------------------
         * Build phase work objects for ALL candidate phases.
         * ------------------------------------------------------------
         */
        for (int p = 0;
             p < nph;
             p++) {

            GibbsEnergyModel model =
                    phaseModels.get(p);

            if (!(model instanceof CefGibbs)) {

                throw new UnsupportedOperationException(
                        "EquilibriumSolverV2 currently requires "
                        + "CEF candidate phases. Phase "
                        + p + " (" + model.phaseName()
                        + ") is not a CefGibbs.");
            }

            CefGibbs cef =
                    (CefGibbs) model;

            PhaseWork work =
                    new PhaseWork(cef);

            /*
             * For initialization only, seed every candidate phase with
             * a constitution corresponding to the requested overall
             * composition.
             *
             * Later the grid/global initializer will replace this with
             * proper phase-specific starting constitutions.
             */
            work.y =
                    initializeSinglePhaseState(
                            cef,
                            targetComposition());

            evaluatePhaseWork(work);

            phaseWorks.add(work);
        }

        if (testInitialState != null) {

            /*
             * ------------------------------------------------------------
             * Test-only controlled initial state (see
             * setInitialStateForTest()).  Overrides the default
             * single-phase starting guess with a prescribed stable-phase
             * set, per-phase constitution, and phase amounts.
             * ------------------------------------------------------------
             */
            stablePhases =
                    testInitialState.stablePhases.clone();

            phaseAmounts =
                    testInitialState.phaseAmounts.clone();

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                int p =
                        stablePhases[k];

                PhaseWork work =
                        phaseWorks.get(p);

                work.y =
                        testInitialState.y[k].clone();

                evaluatePhaseWork(work);
            }

        } else {

            /*
             * ------------------------------------------------------------
             * Grid/global initial stable-phase set.
             *
             * GridMinimizer samples every candidate phase's internal
             * degrees of freedom directly (site fractions) -- exact
             * endmembers, edge points, and Halton-sampled interior points,
             * mirroring pycalphad's calculate() -- takes the lower convex
             * hull of the combined (composition, G/atom) point cloud, and
             * returns the hull facet enclosing the requested overall
             * composition as the initial stable-phase set, with per-phase
             * site fractions and lever-rule amounts.
             *
             * This replaces the previous "always start at candidate 0"
             * placeholder, closing the miscibility-gap / global-min gap
             * documented on this method's class-level javadoc.
             * ------------------------------------------------------------
             */
            GridMinimizer gridMinimizer =
                    new GridMinimizer();

            EquilibriumState gridState =
                    gridMinimizer.initialize(
                            phaseModels, T, P, targetComposition());

            List<PhaseRecord> gridStable =
                    gridState.stablePhases();

            if (gridStable.isEmpty()) {

                throw new IllegalStateException(
                        "GridMinimizer returned no stable phases.");
            }

            stablePhases =
                    new int[gridStable.size()];

            phaseAmounts =
                    new double[gridStable.size()];

            for (int k = 0;
                 k < gridStable.size();
                 k++) {

                PhaseRecord pr =
                        gridStable.get(k);

                int p =
                        phaseModels.indexOf(pr.model);

                if (p < 0) {

                    throw new IllegalStateException(
                            "GridMinimizer returned a phase record whose "
                            + "model is not among the candidate phases: "
                            + pr.phaseName());
                }

                stablePhases[k] = p;
                phaseAmounts[k] = pr.amount;

                PhaseWork work =
                        phaseWorks.get(p);

                work.y =
                        pr.y.clone();

                evaluatePhaseWork(work);
            }
        }

        /*
         * Internal variables are retained per candidate phase.
         */
        phaseInternalVars =
                new double[nph][];

        for (int p = 0;
             p < nph;
             p++) {

            phaseInternalVars[p] =
                    phaseWorks.get(p).y.clone();
        }

        /*
         * ------------------------------------------------------------
         * Temporary compatibility alias.
         *
         * Existing single-phase methods still use phaseWork.
         * ------------------------------------------------------------
         */
        phaseWork =
                phaseWorks.get(
                        stablePhases[0]);

        /*
         * Initial chemical potentials are obtained from the initially
         * selected stable phase only.
         */
        calculateChemicalPotentials();

        mu =
                (phaseWork.mu != null)
                        ? phaseWork.mu.clone()
                        : new double[targetAmounts.length];

        lastResidual =
                Double.POSITIVE_INFINITY;

        lastStep =
                Double.POSITIVE_INFINITY;
    }

    /**
     * Evaluate all candidate phase states.
     *
     * Every candidate has its own PhaseWork.  The method does not decide
     * stability; it only evaluates the thermodynamic state of each phase.
     */
    private void evaluateAllPhases() {

        if (phaseModels == null
                || phaseModels.isEmpty()) {

            throw new IllegalStateException(
                    "No candidate phase models are available.");
        }

        if (phaseWorks == null
                || phaseWorks.size() != phaseModels.size()) {

            throw new IllegalStateException(
                    "Phase-work state is not synchronized with "
                    + "the candidate phase list.");
        }

        for (int p = 0;
             p < phaseModels.size();
             p++) {

            PhaseWork work =
                    phaseWorks.get(p);

            GibbsEnergyModel model =
                    phaseModels.get(p);

            if (work.model != model) {

                throw new IllegalStateException(
                        "PhaseWork/model mismatch at phase "
                        + p + ": "
                        + model.phaseName());
            }

            /*
             * Evaluate only; do not alter the accepted constitution.
             */
            evaluatePhaseWork(work);

            /*
             * Keep phaseInternalVars synchronized.
             */
            if (phaseInternalVars != null
                    && p < phaseInternalVars.length) {

                phaseInternalVars[p] =
                        work.y.clone();
            }
        }

        /*
         * Temporary compatibility alias:
         * phaseWork represents the first candidate phase.
         */
        phaseWork =
                phaseWorks.get(0);
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
            CefGibbs phase,
            double[] xOverall) {

        if (phase == null)
            throw new IllegalArgumentException(
                    "CEF phase must not be null.");

        if (xOverall == null || xOverall.length == 0)
            throw new IllegalArgumentException(
                    "Overall composition must not be null or empty.");

        /*
         * CefGibbs.getInitialInternalVars() constructs a strictly
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
            CefGibbs phase) {

        return phase.nfu();
    }

    /**
     * Constructs the target M_A values corresponding to the requested
     * normalized overall composition for a single phase.
     *
     * The normalization is chosen so that the total element amount
     * corresponds to one mole of lattice sites.
     */
    private double[] initializeSinglePhaseTargetM(
            CefGibbs phase,
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
            CefGibbs phase) {

        int ns =
                phase.numSublattices();

        int nip =
                phase.numSiteVars();

        double[][] C =
                new double[ns][nip];

        int[] offsets =
                phase.offsets();

        int[] nc =
                phase.constituentsPerSublattice();

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
            CefGibbs phase) {

        int nip =
                phase.numSiteVars();

        int nc =
                targetAmounts.length;

        int ns =
                phase.numSublattices();

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
            CefGibbs phase,
            double[] targetM) {

        int nip =
                phase.numSiteVars();

        int nc =
                targetAmounts.length;

        int ns =
                phase.numSublattices();

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
                            phase.offsets(),
                            phase.constituentsPerSublattice());

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
                phase.offsets();

        int[] ncSL =
                phase.constituentsPerSublattice();

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

        CefGibbs model =
                phaseWork.model;

        int nip =
                model.numSiteVars();

        int ns =
                model.numSublattices();

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
                model.offsets();

        int[] nc =
                model.constituentsPerSublattice();

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
     * Computes the Sundman phase-response coefficients (Eq. 43-44) for one
     * phase by delegating to {@link PhaseMatrixAssembler#compute}, the
     * same bordered-phase-matrix build/invert this method used to
     * re-implement inline (duplicating {@code PhaseMatrixAssembler}'s
     * build-matrix, invert, and {@code cG} derivation exactly, just
     * against {@code PhaseWork}'s raw {@code gy}/{@code gyy}/{@code dMdY}
     * fields instead of calling the already-extracted, already
     * pycalphad-verified utility -- see
     * {@code PhaseMatrixAssemblerContractTest}). Evaluated at {@code mu=0},
     * {@code deltaT=0}, {@code deltaP=0} so {@code eMat} is exactly
     * {@code e_ij} and {@link PhaseEquilData#cG} is exactly Sundman's
     * {@code c_iG} at fixed T, P -- {@code c_iA} is then recovered
     * directly from that same {@code eMat} and {@code work.dMdY} (Eq. 44),
     * not re-derived via a separate matrix build.
     */
    private PhaseResponse calculatePhaseResponse(
            PhaseWork work,
            CefGibbs phase) {

        int nc =
                targetAmounts.length;

        PhaseEquilData data =
                PhaseMatrixAssembler.compute(
                        phase, T, P, work.y, 0.0, 0.0, new double[nc]);

        int nip =
                phase.numSiteVars();

        /*
         * cA[A][i] = c_iA = sum_j e_ij * dM_A/dY_j (Sundman Eq. 44).
         */
        double[][] cA =
                new double[nc][nip];

        for (int A = 0; A < nc; A++) {

            for (int i = 0; i < nip; i++) {

                double sum = 0.0;

                for (int j = 0; j < nip; j++) {

                    sum +=
                            data.eMat[i][j]
                            * work.dMdY[A][j];
                }

                cA[A][i] = sum;
            }
        }

        return new PhaseResponse(
                data.eMat,
                data.cG,
                cA);
    }

    /*
     * Obtain an initial chemical-potential estimate for the starting
     * state. This is initialization only.
     *
     * The actual multiphase equilibrium chemical potentials are obtained
     * from the global Sundman equilibrium matrix.
     *
     * Solves G_Y = C^T*gamma + J_M^T*mu (J_M[A][i] = dM_A/dY_i) via a
     * minimum-norm least-squares solve, using whatever single phase is
     * currently aliased to phaseWork.
     */
    private void calculateChemicalPotentials() {

        if (phaseWork == null) {
            throw new IllegalStateException(
                    "Phase has not been evaluated.");
        }

        int nip =
                phaseWork.model.numSiteVars();

        int ns =
                phaseWork.model.numSublattices();

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
                phaseWork.model.offsets();

        int[] nconst =
                phaseWork.model
                        .constituentsPerSublattice();

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
                phaseWork.model.numSiteVars();

        int ns =
                phaseWork.model.numSublattices();

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
                phaseWork.model.offsets();

        int[] nconst =
                phaseWork.model
                        .constituentsPerSublattice();

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
                phaseWork.model.offsets();

        int[] nconst =
                phaseWork.model
                        .constituentsPerSublattice();

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
                        .numSiteVars();

        int nc =
                targetAmounts.length;

        int ns =
                phaseWork.model
                        .numSublattices();

        int[] offsets =
                phaseWork.model
                        .offsets();

        int[] nconst =
                phaseWork.model
                        .constituentsPerSublattice();

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
            CefGibbs phase,
            double[] y) {

        phaseWork.G =
                phase.G(T, P, y);

        phaseWork.gy =
                phase.dG_dy(T, P, y);

        phaseWork.gyy =
                phase.d2G_dy2(T, P, y);

        phaseWork.mA =
                phase.moles(y);

        phaseWork.dMdY =
                phase.dMoles_dy();
    }

    /**
     * Evaluate one PhaseWork object from its current constitution.
     */
    private void evaluatePhaseWork(
            PhaseWork work) {

        if (work == null
                || work.model == null) {

            throw new IllegalArgumentException(
                    "PhaseWork must contain a CEF model.");
        }

        if (work.y == null) {

            throw new IllegalStateException(
                    "PhaseWork constitution is null.");
        }

        work.G =
                work.model.G(
                        T,
                        P,
                        work.y);

        work.gy =
                work.model.dG_dy(
                        T,
                        P,
                        work.y);

        work.gyy =
                work.model.d2G_dy2(
                        T,
                        P,
                        work.y);

        work.mA =
                work.model.moles(
                        work.y);

        work.dMdY =
                work.model.dMoles_dy();
    }

    /**
     * Initial formula-unit amount for one phase under the present
     * normalized-system convention.
     *
     * N_total = 1, hence
     *
     *     omega = 1 / sum_A M_A.
     */
    private double initialPhaseAmount(
            PhaseWork work) {

        double totalM =
                0.0;

        for (double value : work.mA) {

            if (!Double.isFinite(value)) {

                throw new IllegalStateException(
                        "Non-finite initial M_A value: "
                        + value);
            }

            totalM += value;
        }

        if (!(totalM > 0.0)) {

            throw new IllegalStateException(
                    "Invalid total initial phase M: "
                    + totalM);
        }

        return 1.0 / totalM;
    }

    /**
     * Calculate total element amounts and total Gibbs energy for the
     * current stable-phase state.
     *
     *     N_A = sum_k omega_k M_A^k
     *
     *     G   = sum_k omega_k G^k
     */
    private StateTotals calculateStableStateTotals() {

        if (stablePhases == null
                || stablePhases.length == 0) {

            throw new IllegalStateException(
                    "No stable phases are available.");
        }

        if (phaseAmounts == null
                || phaseAmounts.length != stablePhases.length) {

            throw new IllegalStateException(
                    "phaseAmounts/stablePhases dimension mismatch.");
        }

        final int nc =
                targetAmounts.length;

        StateTotals totals =
                new StateTotals(nc);

        for (int k = 0;
             k < stablePhases.length;
             k++) {

            int p =
                    stablePhases[k];

            PhaseWork work =
                    phaseWorks.get(p);

            double omega =
                    phaseAmounts[k];

            if (!Double.isFinite(omega)
                    || omega <= 0.0) {

                throw new IllegalStateException(
                        "Invalid phase amount for phase "
                        + work.model.phaseName()
                        + ": " + omega);
            }

            if (work.mA == null
                    || work.mA.length != nc) {

                throw new IllegalStateException(
                        "Invalid M_A vector for phase "
                        + work.model.phaseName());
            }

            if (!Double.isFinite(work.G)) {

                throw new IllegalStateException(
                        "Non-finite G for phase "
                        + work.model.phaseName());
            }

            totals.totalG +=
                    omega * work.G;

            for (int A = 0;
                 A < nc;
                 A++) {

                totals.amounts[A] +=
                        omega * work.mA[A];
            }
        }

        return totals;
    }

    private static final class StateTotals {

        final double[] amounts;
        double totalG;

        StateTotals(int nc) {

            amounts =
                    new double[nc];

            totalG =
                    0.0;
        }
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
            CefGibbs phase,
            double[] targetM) {

        int nip =
                phase.numSiteVars();

        int nc =
                targetAmounts.length;

        int ns =
                phase.numSublattices();

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
     * Build Sundman phase responses for every candidate phase.
     *
     * Phase stability is deliberately not considered here.
     */
    private void buildPhaseResponses() {

        if (phaseWorks == null
                || phaseWorks.size() != phaseModels.size()) {

            throw new IllegalStateException(
                    "Phase-work state is not initialized.");
        }

        for (PhaseWork work : phaseWorks) {

            work.response =
                    calculatePhaseResponse(
                            work,
                            work.model);

            int nc =
                    targetAmounts.length;

            int nip =
                    work.model
                            .numSiteVars();

            work.massResponse =
                    new double[nc][nc];

            work.massGResponse =
                    new double[nc];

            for (int A = 0; A < nc; A++) {

                for (int i = 0; i < nip; i++) {

                    double dM =
                            work.dMdY[A][i];

                    work.massGResponse[A] +=
                            dM
                            * work.response.cG[i];

                    for (int B = 0; B < nc; B++) {

                        work.massResponse[A][B] +=
                                dM
                                * work.response.cA[B][i];
                    }
                }
            }
        }

        /*
         * Restore phase 0 alias.
         */
        if (!phaseWorks.isEmpty()) {
            phaseWork =
                    phaseWorks.get(0);
        }
    }

    /**
     * Verify the indexed phase state and response data.
     */
    private void validatePhaseIndexedState() {

        if (phaseWorks == null) {

            throw new IllegalStateException(
                    "phaseWorks is null.");
        }

        System.out.println();
        System.out.println(
                "Phase-indexed state");
        System.out.println(
                "------------------");

        for (int p = 0;
             p < phaseWorks.size();
             p++) {

            PhaseWork work =
                    phaseWorks.get(p);

            if (work.y == null
                    || work.mA == null
                    || work.response == null) {

                throw new IllegalStateException(
                        "Incomplete PhaseWork at index "
                        + p);
            }

            double totalM =
                    0.0;

            for (double value :
                    work.mA) {

                totalM += value;
            }

            System.out.printf(
                    "phase %d: %s%n",
                    p,
                    phaseModels.get(p).phaseName());

            System.out.printf(
                    "  G      = %.15f%n",
                    work.G);

            System.out.printf(
                    "  M      = %s%n",
                    Arrays.toString(work.mA));

            System.out.printf(
                    "  sum(M) = %.15f%n",
                    totalM);

            System.out.printf(
                    "  Y      = %s%n",
                    Arrays.toString(work.y));

            System.out.printf(
                    "  cG norm = %.15e%n",
                    vectorNorm(work.response.cG));

            if (work.response.cA.length
                    != targetAmounts.length) {

                throw new IllegalStateException(
                        "Wrong cA dimension at phase "
                        + p);
            }
        }

        System.out.println(
                "Phase-indexed state validation: PASS");
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
                        .numSiteVars();

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
                        .numSiteVars();

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
     * Build the generic multiphase Sundman global equilibrium matrix.
     *
     * Current thermodynamic scope:
     *
     *     fixed T
     *     fixed P
     *     closed system
     *
     * Stable phases are indexed through:
     *
     *     stablePhases[k]  -> candidate phase index
     *     phaseWorks[p]    -> thermodynamic data for candidate p
     *     phaseAmounts[k]  -> omega of stable phase k
     *
     * Unknown vector:
     *
     *     z =
     *     [ lambda_1 ... lambda_nc
     *       DeltaOmega_1 ... DeltaOmega_np ]
     *
     * where:
     *
     *     lambda_A       = new element chemical potentials
     *     DeltaOmega_k   = correction to stable phase k amount
     *
     * For every stable phase alpha:
     *
     *     G_M^alpha = sum_A M_A^alpha * lambda_A
     *
     * For every element A:
     *
     *     sum_alpha omega_alpha
     *         sum_B R_AB^alpha * lambda_B
     *
     *     + sum_alpha M_A^alpha * DeltaOmega_alpha
     *
     *     = -sum_alpha omega_alpha * q_A^alpha
     *
     * with
     *
     *     R_AB^alpha =
     *         sum_i (dM_A/dY_i) c_iB
     *
     *     q_A^alpha =
     *         sum_i (dM_A/dY_i) c_iG
     *
     * This is the fixed-T/P multiphase form corresponding to
     * Sundman's Eq. (59).
     */
    private void buildEquilibriumMatrix() {

        if (phaseWorks == null
                || phaseWorks.isEmpty()) {

            throw new IllegalStateException(
                    "Phase-indexed state has not been initialized.");
        }

        if (stablePhases == null
                || stablePhases.length == 0) {

            throw new IllegalStateException(
                    "No stable phases are available.");
        }

        if (phaseAmounts == null
                || phaseAmounts.length != stablePhases.length) {

            throw new IllegalStateException(
                    "phaseAmounts/stablePhases dimension mismatch: "
                    + "amounts="
                    + (phaseAmounts == null
                            ? -1
                            : phaseAmounts.length)
                    + ", stablePhases="
                    + stablePhases.length);
        }

        final int nc =
                targetAmounts.length;

        final int np =
                stablePhases.length;

        /*
         * Number of global unknowns:
         *
         *     nc chemical potentials
         *     np phase-amount corrections
         */
        final int n =
                nc + np;

        // ------------------------------------------------------------
        // Validate stable-phase mapping and gather phase data.
        // ------------------------------------------------------------

        for (int k = 0; k < np; k++) {

            int phaseIndex =
                    stablePhases[k];

            if (phaseIndex < 0
                    || phaseIndex >= phaseWorks.size()) {

                throw new IllegalStateException(
                        "Invalid stable phase index "
                        + phaseIndex);
            }

            PhaseWork work =
                    phaseWorks.get(phaseIndex);

            if (work == null
                    || work.model == null) {

                throw new IllegalStateException(
                        "Missing PhaseWork for stable phase "
                        + phaseIndex);
            }

            if (work.response == null) {

                throw new IllegalStateException(
                        "Missing Sundman response for stable phase "
                        + phaseIndex
                        + " ("
                        + work.model.phaseName()
                        + ").");
            }

            if (work.mA == null
                    || work.mA.length != nc) {

                throw new IllegalStateException(
                        "M_A dimension mismatch for stable phase "
                        + work.model.phaseName()
                        + ": expected "
                        + nc
                        + ", got "
                        + (work.mA == null
                                ? -1
                                : work.mA.length));
            }

            double omega =
                    phaseAmounts[k];

            if (!Double.isFinite(omega)
                    || omega <= 0.0) {

                throw new IllegalStateException(
                        "Invalid amount for stable phase "
                        + work.model.phaseName()
                        + ": "
                        + omega);
            }
        }

        // ------------------------------------------------------------
        // Assemble via GlobalEquilibriumMatrixAssembler (STEP 3-4;
        // see its javadoc and PhaseMatrixAssemblerContractTest's
        // sibling test for STEP 1-2). Adapts each stable phase's
        // PhaseWork into the minimal PhaseEquilData the assembler
        // reads (mA, G, eMatNC=R_AB, deln=q_A); this avoids
        // re-implementing the same (A, b) assembly inline a second
        // time, as this method used to.
        // ------------------------------------------------------------

        PhaseEquilData[] phaseData =
                new PhaseEquilData[np];

        double[] stablePhaseAmounts =
                new double[np];

        for (int k = 0; k < np; k++) {

            PhaseWork work =
                    phaseWorks.get(stablePhases[k]);

            phaseData[k] =
                    new PhaseEquilData(
                            work.G,
                            null,
                            work.massGResponse,
                            null,
                            work.mA,
                            null,
                            work.massResponse,
                            null,
                            null,
                            null,
                            null);

            stablePhaseAmounts[k] =
                    phaseAmounts[k];
        }

        double[][] A =
                GlobalEquilibriumMatrixAssembler.buildMatrix(
                        phaseData, stablePhaseAmounts, targetAmounts);

        double[] b =
                GlobalEquilibriumMatrixAssembler.buildRhs(
                        phaseData, stablePhaseAmounts, targetAmounts);

        equilibriumMatrix =
                A;

        equilibriumRhs =
                b;

        /*
         * ------------------------------------------------------------
         * Diagnostics
         * ------------------------------------------------------------
         */
        System.out.println();
        System.out.println(
                "Multiphase Sundman equilibrium matrix");
        System.out.println(
                "--------------------------------------");

        System.out.println(
                "Number of stable phases = "
                + np);

        System.out.println(
                "Number of components    = "
                + nc);

        System.out.println(
                "Matrix size             = "
                + n + " x " + n);

        for (int i = 0;
             i < n;
             i++) {

            System.out.printf(
                    "row %d : ",
                    i);

            for (int j = 0;
                 j < n;
                 j++) {

                System.out.printf(
                        "% .12e ",
                        A[i][j]);
            }

            System.out.printf(
                    " | % .12e%n",
                    b[i]);
        }
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
     * Unknown vector:
     *
     *     [ lambda_1 ... lambda_nc
     *       DeltaOmega_1 ... DeltaOmega_np ]
     *
     * where:
     *
     *     lambda_A      = newly calculated element chemical potentials
     *     DeltaOmega_k  = phase-amount correction for stable phase k
     *
     * IMPORTANT:
     *     lambda_A are the new Sundman chemical potentials, not increments.
     *
     * The method is fully phase-indexed.  It does not modify the accepted
     * thermodynamic state; that is done later by updateState().
     */
    private void solveEquilibriumMatrix() {

        if (equilibriumMatrix == null
                || equilibriumRhs == null) {

            throw new IllegalStateException(
                    "Global equilibrium matrix has not been built.");
        }

        if (stablePhases == null
                || stablePhases.length == 0) {

            throw new IllegalStateException(
                    "No stable phases are available.");
        }

        final int nc =
                targetAmounts.length;

        final int np =
                stablePhases.length;

        final int n =
                nc + np;

        // ------------------------------------------------------------
        // 1. Dimension checks
        // ------------------------------------------------------------

        if (equilibriumRhs.length != n) {

            throw new IllegalStateException(
                    "Global system RHS has length "
                    + equilibriumRhs.length
                    + ", expected "
                    + n
                    + " (nc=" + nc
                    + ", np=" + np + ").");
        }

        if (equilibriumMatrix.length != n) {

            throw new IllegalStateException(
                    "Global equilibrium matrix has "
                    + equilibriumMatrix.length
                    + " rows, expected "
                    + n + ".");
        }

        for (int i = 0; i < n; i++) {

            if (equilibriumMatrix[i] == null
                    || equilibriumMatrix[i].length != n) {

                throw new IllegalStateException(
                        "Global equilibrium matrix must be "
                        + n + " x " + n
                        + "; row " + i
                        + " is invalid.");
            }
        }

        // ------------------------------------------------------------
        // 2. Finiteness check
        // ------------------------------------------------------------

        for (int i = 0; i < n; i++) {

            if (!Double.isFinite(
                    equilibriumRhs[i])) {

                throw new IllegalStateException(
                        "Non-finite equilibrium RHS at row "
                        + i + ": "
                        + equilibriumRhs[i]);
            }

            for (int j = 0; j < n; j++) {

                if (!Double.isFinite(
                        equilibriumMatrix[i][j])) {

                    throw new IllegalStateException(
                            "Non-finite equilibrium matrix entry ["
                            + i + "][" + j + "]: "
                            + equilibriumMatrix[i][j]);
                }
            }
        }

        // ------------------------------------------------------------
        // 3. Solve A*x = b
        // ------------------------------------------------------------

        Matrix A =
                new Matrix(
                        equilibriumMatrix);

        Matrix b =
                new Matrix(
                        equilibriumRhs,
                        equilibriumRhs.length);

        Matrix x;

        try {

            x =
                    A.solve(b);

        } catch (RuntimeException ex) {

            throw new IllegalStateException(
                    "Failed to solve global Sundman "
                    + "equilibrium system.",
                    ex);
        }

        double[] solution =
                x.getColumnPackedCopy();

        if (solution.length != n) {

            throw new IllegalStateException(
                    "Unexpected global solution length: "
                    + solution.length
                    + ", expected "
                    + n + ".");
        }

        // ------------------------------------------------------------
        // 4. Check solution
        // ------------------------------------------------------------

        for (int i = 0;
             i < solution.length;
             i++) {

            if (!Double.isFinite(solution[i])) {

                throw new IllegalStateException(
                        "Non-finite global solution at index "
                        + i + ": "
                        + solution[i]);
            }
        }

        // ------------------------------------------------------------
        // 5. Partition solution
        //
        //     [ lambda_1 ... lambda_nc ]
        //     [ DeltaOmega_1 ... DeltaOmega_np ]
        // ------------------------------------------------------------

        newLambda =
                Arrays.copyOfRange(
                        solution,
                        0,
                        nc);

        /*
         * IMPORTANT:
         *
         * deltaPhaseAmounts is indexed by STABLE-PHASE SLOT k,
         * not by candidate-phase index.
         *
         * Therefore:
         *
         *     deltaPhaseAmounts[k]
         *
         * corresponds to
         *
         *     stablePhases[k].
         */
        deltaPhaseAmounts =
                Arrays.copyOfRange(
                        solution,
                        nc,
                        nc + np);

        /*
         * Keep the complete solved vector for diagnostics.
         */
        equilibriumUnknowns =
                solution.clone();

        /*
         * Keep the explicit phase-correction alias synchronized.
         */
        phaseAmountCorrections =
                deltaPhaseAmounts.clone();

        // ------------------------------------------------------------
        // 6. Verify A*x = b
        // ------------------------------------------------------------

        double residual =
                equilibriumLinearResidualNorm();

        if (!Double.isFinite(residual)) {

            throw new IllegalStateException(
                    "Global linear-system residual is non-finite.");
        }

        final double LINEAR_TOL =
                1.0e-8;

        if (residual > LINEAR_TOL) {

            throw new IllegalStateException(
                    "Excessive global linear-system residual: "
                    + residual);
        }

        // ------------------------------------------------------------
        // 7. Diagnostics
        // ------------------------------------------------------------

        System.out.println();
        System.out.println(
                "Global Sundman solution");
        System.out.println(
                "-----------------------");

        System.out.println(
                "newLambda = "
                + Arrays.toString(newLambda));

        System.out.println(
                "DeltaOmega = "
                + Arrays.toString(deltaPhaseAmounts));

        System.out.println(
                "Solution = "
                + Arrays.toString(equilibriumUnknowns));

        System.out.printf(
                "Linear-system residual = %.15e%n",
                residual);

        System.out.println();
        System.out.println(
                "Phase amount corrections");

        for (int k = 0;
             k < np;
             k++) {

            int phaseIndex =
                    stablePhases[k];

            PhaseWork work =
                    phaseWorks.get(phaseIndex);

            System.out.printf(
                    "  phase %d (%s): DeltaOmega = %.15e%n",
                    phaseIndex,
                    work.model.phaseName(),
                    deltaPhaseAmounts[k]);
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
     * Calculate Sundman internal-variable corrections for every stable phase.
     *
     * At fixed T and P, Sundman's Eq. (43) gives
     *
     *     DeltaY_i^alpha =
     *          c_iG^alpha
     *          + sum_A c_iA^alpha * lambda_A
     *
     * where lambda_A are the NEW element chemical potentials obtained from
     * the global equilibrium matrix.
     *
     * Stable-phase indexing:
     *
     *     k                  = stable-phase slot
     *     stablePhases[k]    = candidate-phase index
     *     phaseWorks[p]      = PhaseWork for candidate p
     *     deltaPhaseInternalVars[k] = DeltaY for stable phase k
     *
     * This method calculates corrections only. It does not modify the
     * accepted phase state; updateState() will do that later.
     */
    private void calculateInternalCorrections() {

        if (phaseWorks == null
                || phaseWorks.isEmpty()) {

            throw new IllegalStateException(
                    "Phase-indexed state has not been initialized.");
        }

        if (stablePhases == null
                || stablePhases.length == 0) {

            throw new IllegalStateException(
                    "No stable phases are available.");
        }

        if (newLambda == null) {

            throw new IllegalStateException(
                    "New chemical potentials have not been "
                            + "obtained from the global equilibrium matrix.");
        }

        final int nc =
                targetAmounts.length;

        if (newLambda.length != nc) {

            throw new IllegalStateException(
                    "Chemical-potential vector length mismatch: "
                            + "newLambda="
                            + newLambda.length
                            + ", expected="
                            + nc);
        }

        final int np =
                stablePhases.length;

        /*
         * One DeltaY vector per stable-phase slot.
         *
         * This is intentionally indexed by stable-phase slot rather than
         * candidate-phase index. The mapping is:
         *
         *     stablePhases[k] -> phaseWorks[p]
         *     deltaPhaseInternalVars[k] -> correction for that phase
         */
        deltaPhaseInternalVars =
                new double[np][];

        System.out.println();
        System.out.println(
                "Multiphase internal-variable corrections");
        System.out.println(
                "---------------------------------------");

        for (int k = 0;
             k < np;
             k++) {

            int phaseIndex =
                    stablePhases[k];

            if (phaseIndex < 0
                    || phaseIndex >= phaseWorks.size()) {

                throw new IllegalStateException(
                        "Invalid stable phase index "
                                + phaseIndex
                                + " at stable slot "
                                + k);
            }

            PhaseWork work =
                    phaseWorks.get(phaseIndex);

            if (work == null
                    || work.model == null) {

                throw new IllegalStateException(
                        "Missing PhaseWork for stable phase "
                                + phaseIndex);
            }

            if (work.response == null) {

                throw new IllegalStateException(
                        "Missing Sundman phase response for "
                                + work.model.phaseName());
            }

            final int nip =
                    work.model
                            .numSiteVars();

            if (work.response.cG == null
                    || work.response.cG.length != nip) {

                throw new IllegalStateException(
                        "cG dimension mismatch for phase "
                                + work.model.phaseName());
            }

            if (work.response.cA == null
                    || work.response.cA.length != nc) {

                throw new IllegalStateException(
                        "cA element dimension mismatch for phase "
                                + work.model.phaseName());
            }

            double[] deltaY =
                    new double[nip];

            // ------------------------------------------------------------
            // Sundman Eq. (43):
            //
            //     DeltaY_i =
            //          c_iG
            //          + sum_A c_iA * lambda_A
            // ------------------------------------------------------------

            for (int i = 0;
                 i < nip;
                 i++) {

                double value =
                        work.response.cG[i];

                for (int A = 0;
                     A < nc;
                     A++) {

                    if (work.response.cA[A] == null
                            || work.response.cA[A].length != nip) {

                        throw new IllegalStateException(
                                "cA["
                                        + A
                                        + "] dimension mismatch for phase "
                                        + work.model.phaseName());
                    }

                    value +=
                            work.response.cA[A][i]
                            * newLambda[A];
                }

                if (!Double.isFinite(value)) {

                    throw new IllegalStateException(
                            "Non-finite DeltaY["
                                    + i
                                    + "] for phase "
                                    + work.model.phaseName()
                                    + ": "
                                    + value);
                }

                deltaY[i] =
                        value;
            }

            /*
             * Store by stable-phase slot.
             */
            deltaPhaseInternalVars[k] =
                    deltaY;

            // ------------------------------------------------------------
            // Diagnostics
            // ------------------------------------------------------------

            double norm =
                    vectorNorm(deltaY);

            double[] predictedY =
                    new double[nip];

            for (int i = 0;
                 i < nip;
                 i++) {

                predictedY[i] =
                        work.y[i]
                        + deltaY[i];
            }

            boolean physicallyValid =
                    work.model.isValid(
                            predictedY);

            System.out.println();
            System.out.println(
                    "Stable phase slot = "
                            + k);

            System.out.println(
                    "Candidate phase   = "
                            + phaseIndex);

            System.out.println(
                    "Phase             = "
                            + work.model.phaseName());

            System.out.println(
                    "newLambda         = "
                            + Arrays.toString(newLambda));

            System.out.println(
                    "cG                = "
                            + Arrays.toString(
                                    work.response.cG));

            System.out.println(
                    "DeltaY            = "
                            + Arrays.toString(deltaY));

            System.out.printf(
                    "||DeltaY||        = %.15e%n",
                    norm);

            System.out.println(
                    "Predicted Y       = "
                            + Arrays.toString(predictedY));

            System.out.println(
                    "Predicted Y physically valid = "
                            + physicallyValid);
        }
    }

    /**
     * Maximum norm of the internal-variable corrections over all
     * currently stable phases.
     */
    private double internalCorrectionNorm() {

        if (deltaPhaseInternalVars == null
                || stablePhases == null
                || deltaPhaseInternalVars.length
                        != stablePhases.length) {

            return Double.POSITIVE_INFINITY;
        }

        double maximum =
                0.0;

        for (int k = 0;
             k < stablePhases.length;
             k++) {

            if (deltaPhaseInternalVars[k] == null) {
                return Double.POSITIVE_INFINITY;
            }

            maximum =
                    Math.max(
                            maximum,
                            vectorNorm(
                                    deltaPhaseInternalVars[k]));
        }

        return maximum;
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
                        .numSiteVars();

        int nc =
                targetAmounts.length;

        int ns =
                phaseWork.model
                        .numSublattices();

        int[] offsets =
                phaseWork.model
                        .offsets();

        int[] nconst =
                phaseWork.model
                        .constituentsPerSublattice();

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
     * Recompute the sublattice Lagrange multipliers for one PhaseWork.
     *
     * Stationarity:
     *
     *     G_Y - J_M^T mu - C^T gamma = 0
     *
     * For each sublattice, gamma_s is obtained from the common value of
     *
     *     G_i - sum_A (dM_A/dY_i) mu_A
     *
     * over its constituents.
     */
    private void recomputeSublatticeMultipliers(
            PhaseWork work) {

        if (work == null
                || work.model == null) {

            throw new IllegalArgumentException(
                    "PhaseWork must not be null.");
        }

        if (work.gy == null
                || work.dMdY == null
                || work.mu == null) {

            throw new IllegalStateException(
                    "Incomplete phase state for gamma calculation.");
        }

        int nip =
                work.model
                        .numSiteVars();

        int nc =
                targetAmounts.length;

        int ns =
                work.model
                        .numSublattices();

        int[] offsets =
                work.model
                        .offsets();

        int[] nconst =
                work.model
                        .constituentsPerSublattice();

        if (work.gy.length != nip) {

            throw new IllegalStateException(
                    "gy dimension mismatch for phase "
                    + work.model.phaseName());
        }

        if (work.mu.length != nc) {

            throw new IllegalStateException(
                    "mu dimension mismatch for phase "
                    + work.model.phaseName());
        }

        double[] gamma =
                new double[ns];

        for (int s = 0;
             s < ns;
             s++) {

            double sum =
                    0.0;

            int count =
                    0;

            int begin =
                    offsets[s];

            int end =
                    begin + nconst[s];

            for (int i = begin;
                 i < end;
                 i++) {

                double value =
                        work.gy[i];

                for (int A = 0;
                     A < nc;
                     A++) {

                    value -=
                            work.dMdY[A][i]
                            * work.mu[A];
                }

                sum +=
                        value;

                count++;
            }

            if (count <= 0) {

                throw new IllegalStateException(
                        "Empty sublattice "
                        + s
                        + " for phase "
                        + work.model.phaseName());
            }

            gamma[s] =
                    sum / count;
        }

        work.gamma =
                gamma;
    }

    /**
     * Accept the current multiphase Sundman correction directly, with no
     * line search or damping:
     *
     *     Y_k(new)     = Y_k(old) + DeltaY_k
     *     omega_k(new) = omega_k(old) + DeltaOmega_k
     *     mu(new)      = newLambda
     *
     * If the full (alpha = 1) correction is not physically valid for
     * every stable phase (invalid CEF constitution, or a non-positive
     * phase amount), the update is REJECTED outright -- this is a fixed
     * stable-phase-set solver; a negative/invalid phase amount is not
     * silently handled by damping or by removing the phase here. Phase
     * addition/removal is updateStablePhaseSet()'s responsibility (a
     * no-op for now).
     */
    private void updateState() {

        if (phaseWorks == null
                || phaseWorks.isEmpty()) {

            throw new IllegalStateException(
                    "Phase-indexed state has not been initialized.");
        }

        if (stablePhases == null
                || stablePhases.length == 0) {

            throw new IllegalStateException(
                    "No stable phases are available.");
        }

        if (phaseAmounts == null
                || phaseAmounts.length != stablePhases.length) {

            throw new IllegalStateException(
                    "phaseAmounts/stablePhases dimension mismatch.");
        }

        if (deltaPhaseAmounts == null
                || deltaPhaseAmounts.length != stablePhases.length) {

            throw new IllegalStateException(
                    "deltaPhaseAmounts/stablePhases dimension mismatch.");
        }

        if (deltaPhaseInternalVars == null
                || deltaPhaseInternalVars.length != stablePhases.length) {

            throw new IllegalStateException(
                    "deltaPhaseInternalVars/stablePhases dimension mismatch.");
        }

        if (newLambda == null
                || newLambda.length != targetAmounts.length) {

            throw new IllegalStateException(
                    "newLambda is unavailable or has wrong dimension.");
        }

        final int nc =
                targetAmounts.length;

        final int np =
                stablePhases.length;

        previousPhaseAmounts =
                phaseAmounts.clone();

        previousPhaseG =
                new double[np];

        previousPhaseMA =
                new double[np][];

        for (int k = 0;
             k < np;
             k++) {

            int p =
                    stablePhases[k];

            PhaseWork work =
                    phaseWorks.get(p);

            previousPhaseG[k] =
                    work.G;

            previousPhaseMA[k] =
                    work.mA.clone();
        }

        /*
         * ------------------------------------------------------------
         * Snapshot the accepted multiphase state before this update,
         * for later comparison in validateMultiphaseUpdate() and for
         * the DeltaMu convergence check.
         * ------------------------------------------------------------
         */
        StateTotals oldTotals =
                calculateStableStateTotals();

        previousTotalG =
                oldTotals.totalG;

        previousTotalAmounts =
                oldTotals.amounts.clone();

        if (previousMu == null) {

            previousMu =
                    (mu != null)
                            ? mu.clone()
                            : new double[nc];

        } else {

            System.arraycopy(
                    mu, 0,
                    previousMu, 0,
                    nc);
        }

        double[] newLambdaChecked =
                newLambda.clone();

        for (int A = 0;
             A < nc;
             A++) {

            if (!Double.isFinite(
                    newLambdaChecked[A])) {

                throw new IllegalStateException(
                        "Non-finite newLambda["
                        + A
                        + "]: "
                        + newLambdaChecked[A]);
            }
        }

        /*
         * ------------------------------------------------------------
         * 1. Construct the full (alpha = 1) trial state for every
         *    stable phase, without committing anything.
         * ------------------------------------------------------------
         */
        double[][] trialY =
                new double[np][];

        double[] trialOmega =
                new double[np];

        boolean physical =
                true;

        int invalidSlot =
                -1;

        for (int k = 0;
             k < np
                     && physical;
             k++) {

            int phaseIndex =
                    stablePhases[k];

            PhaseWork work =
                    phaseWorks.get(phaseIndex);

            double[] dy =
                    deltaPhaseInternalVars[k];

            if (dy == null
                    || dy.length != work.y.length) {

                throw new IllegalStateException(
                        "DeltaY dimension mismatch for phase "
                        + work.model.phaseName());
            }

            trialY[k] =
                    new double[work.y.length];

            for (int i = 0;
                 i < work.y.length;
                 i++) {

                trialY[k][i] =
                        work.y[i]
                        + dy[i];
            }

            trialOmega[k] =
                    phaseAmounts[k]
                    + deltaPhaseAmounts[k];

            if (!work.model.isValid(trialY[k])
                    || !Double.isFinite(trialOmega[k])
                    || trialOmega[k] <= 0.0) {

                physical =
                        false;

                invalidSlot =
                        k;
            }
        }

        if (!physical) {

            int phaseIndex =
                    stablePhases[invalidSlot];

            PhaseWork work =
                    phaseWorks.get(phaseIndex);

            throw new IllegalStateException(
                    "Fixed-phase-set Sundman update is not physically "
                    + "valid for phase "
                    + work.model.phaseName()
                    + " (stable slot "
                    + invalidSlot
                    + "): invalid constitution or non-positive "
                    + "phase amount "
                    + trialOmega[invalidSlot]
                    + ". This solver does not add/remove phases or "
                    + "damp the step; phase-set changes are "
                    + "updateStablePhaseSet()'s responsibility.");
        }

        // ================================================================
        // 2. Commit ALL stable phase updates simultaneously
        // ================================================================

        for (int k = 0;
             k < np;
             k++) {

            int phaseIndex =
                    stablePhases[k];

            PhaseWork work =
                    phaseWorks.get(
                            phaseIndex);

            // ------------------------------------------------------------
            // Constitution and phase amount
            // ------------------------------------------------------------
            work.y =
                    trialY[k];

            phaseAmounts[k] =
                    trialOmega[k];

            phaseInternalVars[phaseIndex] =
                    work.y.clone();

            /*
             * Re-evaluate the accepted thermodynamic state immediately.
             */
            evaluatePhaseWork(work);

            // ------------------------------------------------------------
            // Chemical potentials: mu(new) = newLambda, common to every
            // stable phase, no artificial alpha.
            // ------------------------------------------------------------
            work.mu =
                    newLambdaChecked.clone();

            recomputeSublatticeMultipliers(
                    work);
        }

        // ================================================================
        // 3. Commit common chemical potentials
        // ================================================================

        mu =
                newLambdaChecked.clone();

        double maxDY =
                0.0;

        for (int k = 0;
             k < np;
             k++) {

            maxDY =
                    Math.max(
                            maxDY,
                            vectorNorm(
                                    deltaPhaseInternalVars[k]));
        }

        lastStep =
                maxDY;

        /*
         * Keep the phase-0 compatibility alias.
         */
        if (!phaseWorks.isEmpty()) {

            phaseWork =
                    phaseWorks.get(0);
        }

        // ================================================================
        // 4. Diagnostics
        // ================================================================

        System.out.println();
        System.out.println(
                "Accepted multiphase state update");
        System.out.println(
                "---------------------------------");

        System.out.println(
                "mu(new) = "
                + Arrays.toString(mu));

        for (int k = 0;
             k < np;
             k++) {

            int phaseIndex =
                    stablePhases[k];

            PhaseWork work =
                    phaseWorks.get(
                            phaseIndex);

            System.out.printf(
                    "phase slot %d (%s)%n",
                    k,
                    work.model.phaseName());

            System.out.printf(
                    "  DeltaOmega      = %.15e%n",
                    deltaPhaseAmounts[k]);

            System.out.printf(
                    "  omega(new)      = %.15e%n",
                    phaseAmounts[k]);

            System.out.printf(
                    "  ||DeltaY||      = %.15e%n",
                    vectorNorm(
                            deltaPhaseInternalVars[k]));

            System.out.println(
                    "  Y(new)          = "
                    + Arrays.toString(work.y));
        }
    }

    /**
     * Verify the linearized mass-balance equation actually solved by the
     * current global Sundman matrix.
     *
     * For each element A:
     *
     *   DN_A^(1) =
     *
     *       sum_k {
     *
     *           omega_k *
     *           [ q_A^k + sum_B R_AB^k lambda_B ]
     *
     *           + M_A^k * DeltaOmega_k
     *
     *       }
     *
     * All quantities in this expression must be evaluated at the SAME
     * matrix-assembly point.
     *
     * In particular, M_A must be the PRE-UPDATE phase amount.
     *
     * The global matrix guarantees:
     *
     *   DN_A^(1) = 0
     *
     * to numerical precision.
     */
    private double calculateFirstOrderMassResidual() {

        if (previousPhaseAmounts == null
                || previousPhaseMA == null) {

            throw new IllegalStateException(
                    "Pre-update phase data were not saved.");
        }

        if (deltaPhaseAmounts == null
                || deltaPhaseAmounts.length
                        != stablePhases.length) {

            throw new IllegalStateException(
                    "DeltaOmega/stable-phase dimension mismatch.");
        }

        if (newLambda == null) {

            throw new IllegalStateException(
                    "newLambda is not available.");
        }

        final int nc =
                targetAmounts.length;

        double maximum =
                0.0;

        for (int A = 0;
             A < nc;
             A++) {

            double dN =
                    0.0;

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                int p =
                        stablePhases[k];

                PhaseWork work =
                        phaseWorks.get(p);

                /*
                 * ALL quantities below refer to the point at which
                 * buildEquilibriumMatrix() was assembled.
                 */
                double omega =
                        previousPhaseAmounts[k];

                double oldM =
                        previousPhaseMA[k][A];

                /*
                 * Linearized phase composition response:
                 *
                 *     DM_A =
                 *         q_A
                 *         + sum_B R_AB lambda_B
                 */
                double dM =
                        work.massGResponse[A];

                for (int B = 0;
                     B < nc;
                     B++) {

                    dM +=
                            work.massResponse[A][B]
                            * newLambda[B];
                }

                /*
                 * Total first-order element change:
                 *
                 *     DN_A =
                 *         omega * DM_A
                 *         + M_A * DOmega
                 */
                dN +=
                        omega * dM
                        +
                        oldM * deltaPhaseAmounts[k];
            }

            maximum =
                    Math.max(
                            maximum,
                            Math.abs(dN));

            System.out.printf(
                    "First-order DeltaN[%d] = %.15e%n",
                    A,
                    dN);
        }

        return maximum;
    }

    /**
     * Total Gibbs energy of the stable-phase state at the exact point
     * where the global matrix was assembled (before this update).
     */
    private double calculatePreviousTotalG() {

        if (previousPhaseAmounts == null
                || previousPhaseG == null) {

            throw new IllegalStateException(
                    "Previous stable-phase state was not saved.");
        }

        double totalG =
                0.0;

        for (int k = 0;
             k < stablePhases.length;
             k++) {

            totalG +=
                    previousPhaseAmounts[k]
                    * previousPhaseG[k];
        }

        return totalG;
    }

    /**
     * Validate the current accepted multiphase state.
     *
     * This is intentionally independent of phase selection.  It checks:
     *
     *   - every stable phase has a valid constitution;
     *   - every sublattice is normalized;
     *   - every stable phase amount is positive;
     *   - all phase thermodynamic quantities are finite;
     *   - exact total element amounts before/after the update;
     *   - exact nonlinear mass-balance change;
     *   - first-order mass-balance prediction from the Sundman correction.
     */
    private void validateMultiphaseUpdate() {

        if (stablePhases == null
                || stablePhases.length == 0) {

            throw new IllegalStateException(
                    "No stable phases are available.");
        }

        final int nc =
                targetAmounts.length;

        final int np =
                stablePhases.length;

        /*
         * ------------------------------------------------------------
         * Current totals
         * ------------------------------------------------------------
         */
        StateTotals current =
                calculateStableStateTotals();

        /*
         * ------------------------------------------------------------
         * State-level diagnostics
         * ------------------------------------------------------------
         */
        double maxNormalizationResidual =
                0.0;

        double maxGibbsResidual =
                0.0;

        double maxStationarityResidual =
                0.0;

        for (int k = 0;
             k < np;
             k++) {

            int p =
                    stablePhases[k];

            PhaseWork work =
                    phaseWorks.get(p);

            // ----------------------------------------------------------
            // Finite thermodynamic quantities
            // ----------------------------------------------------------

            checkFinite(
                    "G for phase " + work.model.phaseName(),
                    work.G);

            for (int A = 0;
                 A < nc;
                 A++) {

                checkFinite(
                        "M[" + A + "] for phase "
                        + work.model.phaseName(),
                        work.mA[A]);
            }

            // ----------------------------------------------------------
            // Constitution validity
            // ----------------------------------------------------------

            if (work.y == null
                    || !work.model.isValid(work.y)) {

                throw new IllegalStateException(
                        "Invalid accepted constitution for phase "
                        + work.model.phaseName());
            }

            // ----------------------------------------------------------
            // Phase amount
            // ----------------------------------------------------------

            double omega =
                    phaseAmounts[k];

            if (!Double.isFinite(omega)
                    || omega <= 0.0) {

                throw new IllegalStateException(
                        "Invalid accepted amount for phase "
                        + work.model.phaseName()
                        + ": " + omega);
            }

            // ----------------------------------------------------------
            // Sublattice normalization
            // ----------------------------------------------------------

            int[] offsets =
                    work.model.offsets();

            int[] nconst =
                    work.model
                            .constituentsPerSublattice();

            for (int s = 0;
                 s < nconst.length;
                 s++) {

                double sum =
                        0.0;

                for (int i = 0;
                     i < nconst[s];
                     i++) {

                    sum +=
                            work.y[
                                    offsets[s] + i];
                }

                double r =
                        sum - 1.0;

                maxNormalizationResidual =
                        Math.max(
                                maxNormalizationResidual,
                                Math.abs(r));
            }

            // ----------------------------------------------------------
            // Phase Gibbs-equilibrium relation: G_M - sum_A M_A*mu_A
            // ----------------------------------------------------------

            double gibbsResidual =
                    work.G;

            for (int A = 0;
                 A < nc;
                 A++) {

                gibbsResidual -=
                        work.mA[A]
                        * work.mu[A];
            }

            maxGibbsResidual =
                    Math.max(
                            maxGibbsResidual,
                            Math.abs(gibbsResidual));

            // ----------------------------------------------------------
            // Phase-local stationarity
            // ----------------------------------------------------------

            double stationarity =
                    phaseStationarityNorm(work);

            maxStationarityResidual =
                    Math.max(
                            maxStationarityResidual,
                            stationarity);
        }

        /*
         * ------------------------------------------------------------
         * Exact nonlinear mass-balance change
         * ------------------------------------------------------------
         */
        double maxMassChange =
                0.0;

        for (int A = 0;
             A < nc;
             A++) {

            double dN =
                    current.amounts[A]
                    - previousTotalAmounts[A];

            maxMassChange =
                    Math.max(
                            maxMassChange,
                            Math.abs(dN));
        }

        /*
         * ------------------------------------------------------------
         * Global element mass balance against the target composition.
         *
         *     N_A = sum_k omega_k M_A^k
         *
         * Do not use an extremely tight absolute tolerance here.
         * This is a physical-state validation immediately after a
         * damped Newton/Sundman update, not the final convergence test.
         * ------------------------------------------------------------
         */
        double maxTargetMassResidual =
                0.0;

        for (int A = 0;
             A < nc;
             A++) {

            double residual =
                    current.amounts[A]
                    - targetAmounts[A];

            maxTargetMassResidual =
                    Math.max(
                            maxTargetMassResidual,
                            Math.abs(residual));
        }

        /*
         * Sundman's iterative procedure is based on the linearized
         * balance followed by repeated re-evaluation/correction; it does
         * not impose an absolute residual threshold here to terminate
         * the iteration. checkConvergence() is the genuine convergence
         * gate, so this is diagnostic only.
         */
        System.out.printf(
                "nonlinear target mass residual = %.6e%n",
                maxTargetMassResidual);

        /*
         * ------------------------------------------------------------
         * First-order Sundman mass-balance prediction, evaluated at the
         * exact point where the global matrix was assembled (before
         * this update), via calculateFirstOrderMassResidual().
         * ------------------------------------------------------------
         */
        System.out.println();
        System.out.println(
                "First-order mass-balance check");
        System.out.println(
                "-------------------------------");

        double maxFirstOrderMassResidual =
                calculateFirstOrderMassResidual();

        /*
         * ------------------------------------------------------------
         * Previous total G, from the exact matrix-assembly snapshot.
         * ------------------------------------------------------------
         */
        double previousG =
                calculatePreviousTotalG();

        /*
         * ------------------------------------------------------------
         * Report
         * ------------------------------------------------------------
         */
        System.out.println();
        System.out.println(
                "Multiphase state validation");
        System.out.println(
                "---------------------------");

        System.out.println(
                "Previous total amounts = "
                + Arrays.toString(
                        previousTotalAmounts));

        System.out.println(
                "Current total amounts  = "
                + Arrays.toString(
                        current.amounts));

        System.out.println(
                "Target amounts         = "
                + Arrays.toString(
                        targetAmounts));

        System.out.printf(
                "Previous total G       = %.15f%n",
                previousG);

        System.out.printf(
                "Current total G        = %.15f%n",
                current.totalG);

        System.out.printf(
                "Delta G                = %.15e%n",
                current.totalG
                        - previousG);

        System.out.printf(
                "max exact Delta N_A    = %.6e%n",
                maxMassChange);

        System.out.printf(
                "max target mass residual = %.6e%n",
                maxTargetMassResidual);

        System.out.printf(
                "max 1st-order Delta N  = %.6e%n",
                maxFirstOrderMassResidual);

        System.out.printf(
                "max normalization      = %.6e%n",
                maxNormalizationResidual);

        System.out.printf(
                "max phase G relation   = %.6e%n",
                maxGibbsResidual);

        /*
         * This is the nonlinear residual at the freshly updated
         * constitution, using response coefficients that were built
         * at the PREVIOUS constitution. It is not the equilibrium
         * stationarity test -- that only becomes meaningful after the
         * phase matrices/global matrix are rebuilt at the new state on
         * the next full iteration.
         */
        System.out.printf(
                "nonlinear post-step stationarity = %.6e%n",
                maxStationarityResidual);
    }

    /**
     * Phase-local stationarity residual:
     *
     *     || G_Y - J_M^T mu - C^T gamma ||
     */
    private double phaseStationarityNorm(
            PhaseWork work) {

        if (work.gy == null
                || work.dMdY == null
                || work.mu == null
                || work.gamma == null) {

            return Double.POSITIVE_INFINITY;
        }

        int nip =
                work.model
                        .numSiteVars();

        int nc =
                targetAmounts.length;

        int[] offsets =
                work.model.offsets();

        int[] nconst =
                work.model
                        .constituentsPerSublattice();

        double sum2 =
                0.0;

        for (int i = 0;
             i < nip;
             i++) {

            double r =
                    work.gy[i];

            for (int A = 0;
                 A < nc;
                 A++) {

                r -=
                        work.dMdY[A][i]
                        * work.mu[A];
            }

            int s =
                    sublatticeOf(
                            i,
                            offsets,
                            nconst);

            r -=
                    work.gamma[s];

            sum2 +=
                    r * r;
        }

        return Math.sqrt(sum2);
    }

    /**
     * Validate the current accepted state.
     *
     * For a single stable phase, checks:
     *   1. physical CEF constitution
     *   2. finite thermodynamic quantities
     *   3. sublattice normalization
     *   4. positive finite phase amount
     *   5. system element mass balance
     *   6. phase-equilibrium relation
     *   7. phase stationarity relation
     *
     * For a prescribed multiphase stable set (more than one stable
     * phase), dispatches to {@link #validateMultiphaseUpdate()}
     * instead -- see that method for its (intentionally more limited)
     * scope.
     */
    private void validateState() {

        if (phaseWork == null
                && (phaseWorks == null
                    || phaseWorks.isEmpty())) {

            throw new IllegalStateException(
                    "Phase state is not available.");
        }

        if (phaseAmounts == null) {

            throw new IllegalStateException(
                    "Phase amounts are not available.");
        }

        if (stablePhases == null
                || stablePhases.length == 0) {

            throw new IllegalStateException(
                    "No stable phases are available.");
        }

        /*
         * The V2 solver now supports a prescribed multiphase stable set.
         *
         * Use the multiphase validator whenever more than one stable
         * phase is present. The existing one-phase validation below
         * remains unchanged.
         */
        if (stablePhases.length > 1) {

            validateMultiphaseUpdate();

            return;
        }

        if (phaseAmounts.length != 1) {

            throw new IllegalStateException(
                    "Single-phase validation requires exactly one "
                    + "phase amount.");
        }

        if (stablePhases[0] != 0) {

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
                phaseWork.model.offsets();

        int[] nconst =
                phaseWork.model
                        .constituentsPerSublattice();

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

    /*
     * Phase-set management is intentionally deferred.
     *
     * Sundman requires:
     *   omega_alpha < 0     -> remove stable phase
     *   drivingForce_w > 0  -> add unstable phase
     *
     * where the driving force for an unstable phase w is
     *
     *   c^w = sum_A lambda_A * M_A^w - G_M^w
     *
     * (already calculated correctly in buildEquilibriumResult() as
     * drivingForce = -G + sum(mu[A] * mA[A])), but not yet consumed
     * here to decide phase addition/removal.
     *
     * Current V2 scope uses a prescribed fixed stable-phase set:
     * updateState() rejects an update that would make a stable phase's
     * amount non-positive, rather than removing that phase, and no
     * unstable candidate is ever added.
     */
    private void updateStablePhaseSet() {
        // To be implemented.
    }

    /**
     * Check convergence of the current multiphase Sundman equilibrium.
     *
     * Convergence requires ALL of:
     *
     *   A. small thermodynamic residuals, over every stable phase
     *
     *      mass balance
     *      phase Gibbs relation
     *      phase stationarity
     *      sublattice constraints
     *
     *   B. small accepted numerical changes, over every stable phase
     *
     *      internal constitution   (DeltaY)
     *      phase amount            (DeltaOmega)
     *      chemical potentials     (DeltaMu = newLambda - previousMu)
     *
     * A small step alone is not considered sufficient evidence of
     * equilibrium.
     */
    private boolean checkConvergence() {

        if (phaseWorks == null
                || phaseWorks.isEmpty()) {
            return false;
        }

        if (stablePhases == null
                || stablePhases.length == 0) {
            return false;
        }

        if (phaseAmounts == null
                || phaseAmounts.length != stablePhases.length) {
            return false;
        }

        final double residualTol =
                tolerance;

        final double stepTol =
                Math.max(
                        tolerance,
                        1.0e-12);

        final int nc =
                targetAmounts.length;

        final int np =
                stablePhases.length;

        double maxMassResidual =
                0.0;

        double maxGibbsResidual =
                0.0;

        double maxSublatticeResidual =
                0.0;

        double maxStationarity =
                0.0;

        double maxDeltaY =
                0.0;

        double maxDeltaOmega =
                0.0;

        // ------------------------------------------------------------
        // Per-phase residuals and steps.
        // ------------------------------------------------------------

        for (int k = 0;
             k < np;
             k++) {

            int p =
                    stablePhases[k];

            PhaseWork work =
                    phaseWorks.get(p);

            // ----------------------------------------------------------
            // Sublattice normalization
            // ----------------------------------------------------------

            int[] offsets =
                    work.model.offsets();

            int[] nconst =
                    work.model
                            .constituentsPerSublattice();

            for (int s = 0;
                 s < nconst.length;
                 s++) {

                double sum =
                        0.0;

                for (int i = 0;
                     i < nconst[s];
                     i++) {

                    sum +=
                            work.y[
                                    offsets[s] + i];
                }

                maxSublatticeResidual =
                        Math.max(
                                maxSublatticeResidual,
                                Math.abs(sum - 1.0));
            }

            // ----------------------------------------------------------
            // Phase Gibbs-equilibrium residual: G_M - sum_A M_A*mu_A
            // ----------------------------------------------------------

            double gibbsResidual =
                    work.G;

            for (int A = 0;
                 A < nc;
                 A++) {

                gibbsResidual -=
                        work.mA[A]
                        * work.mu[A];
            }

            maxGibbsResidual =
                    Math.max(
                            maxGibbsResidual,
                            Math.abs(gibbsResidual));

            // ----------------------------------------------------------
            // Phase stationarity: || G_Y - JM^T*mu - C^T*gamma ||
            // ----------------------------------------------------------

            maxStationarity =
                    Math.max(
                            maxStationarity,
                            phaseStationarityNorm(work));

            // ----------------------------------------------------------
            // Accepted internal-variable step for this phase.
            // ----------------------------------------------------------

            if (deltaPhaseInternalVars != null
                    && k < deltaPhaseInternalVars.length
                    && deltaPhaseInternalVars[k] != null) {

                maxDeltaY =
                        Math.max(
                                maxDeltaY,
                                vectorNorm(
                                        deltaPhaseInternalVars[k]));
            }

            // ----------------------------------------------------------
            // Accepted phase-amount step for this phase.
            // ----------------------------------------------------------

            if (deltaPhaseAmounts != null
                    && k < deltaPhaseAmounts.length) {

                maxDeltaOmega =
                        Math.max(
                                maxDeltaOmega,
                                Math.abs(
                                        deltaPhaseAmounts[k]));
            }
        }

        // ------------------------------------------------------------
        // Global element mass balance: N_A - sum_alpha omega_alpha*M_A
        // ------------------------------------------------------------

        for (int A = 0;
             A < nc;
             A++) {

            double represented =
                    0.0;

            for (int k = 0;
                 k < np;
                 k++) {

                int p =
                        stablePhases[k];

                PhaseWork work =
                        phaseWorks.get(p);

                represented +=
                        phaseAmounts[k]
                        * work.mA[A];
            }

            double residual =
                    targetAmounts[A]
                    - represented;

            maxMassResidual =
                    Math.max(
                            maxMassResidual,
                            Math.abs(residual));
        }

        // ------------------------------------------------------------
        // Chemical-potential step: DeltaMu = newLambda - previousMu.
        //
        // mu is already overwritten with newLambda by updateState() by
        // the time this runs, so comparing newLambda against the
        // CURRENT mu would spuriously always give zero. previousMu
        // holds the value from BEFORE that update.
        // ------------------------------------------------------------

        double maxDeltaMu =
                0.0;

        if (newLambda != null
                && previousMu != null
                && newLambda.length == previousMu.length) {

            for (int A = 0;
                 A < newLambda.length;
                 A++) {

                maxDeltaMu =
                        Math.max(
                                maxDeltaMu,
                                Math.abs(
                                        newLambda[A]
                                        - previousMu[A]));
            }

        } else {

            maxDeltaMu =
                    Double.POSITIVE_INFINITY;
        }

        // ------------------------------------------------------------
        // Final decision.
        // ------------------------------------------------------------

        boolean massConverged =
                maxMassResidual
                        <= residualTol;

        boolean gibbsConverged =
                maxGibbsResidual
                        <= residualTol;

        boolean sublatticeConverged =
                maxSublatticeResidual
                        <= residualTol;

        boolean stationarityConverged =
                maxStationarity
                        <= residualTol;

        boolean deltaYConverged =
                maxDeltaY
                        <= stepTol;

        boolean deltaOmegaConverged =
                maxDeltaOmega
                        <= stepTol;

        boolean deltaMuConverged =
                maxDeltaMu
                        <= stepTol;

        boolean converged =
                massConverged
                        && gibbsConverged
                        && sublatticeConverged
                        && stationarityConverged
                        && deltaYConverged
                        && deltaOmegaConverged
                        && deltaMuConverged;

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
                maxGibbsResidual);

        System.out.printf(
                "sublattice        = %.6e%n",
                maxSublatticeResidual);

        System.out.printf(
                "stationarity      = %.6e%n",
                maxStationarity);

        System.out.printf(
                "DeltaY norm       = %.6e%n",
                maxDeltaY);

        System.out.printf(
                "DeltaOmega        = %.6e%n",
                maxDeltaOmega);

        System.out.printf(
                "DeltaMu           = %.6e%n",
                maxDeltaMu);

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
