package calc.equil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Phase-amount floor used both to damp a Newton step (updateState())
     * and to decide a stable phase is extinct (updateStablePhaseSet()) --
     * matches pycalphad's own MIN_PHASE_AMOUNT (minimizer.pyx:870).
     */
    private static final double MIN_PHASE_AMOUNT = 1.0e-16;

    /**
     * Minimum driving force (Sundman Eq. 62) required before a metastable
     * candidate is added to the stable set -- matches pycalphad's
     * minimum_df argument to add_new_phases() (eqsolver.pyx:247).
     */
    private static final double ADD_DRIVING_FORCE_THRESHOLD = 1.0e-4;

    /**
     * Composition-distinctness tolerance (Chebyshev distance in mole
     * fraction) used to refuse adding a candidate that is not actually
     * distinct from an already-stable slot of the same phase -- matches
     * pycalphad's COMP_DIFFERENCE_TOL (constants.py:11).
     */
    private static final double ADD_COMP_DIFFERENCE_TOL = 1.0e-4;

    /**
     * Initial phase amount given to a newly added stable phase -- matches
     * pycalphad's MIN_PHASE_FRACTION, used as the seed NP for a phase
     * added by add_new_phases() (eqsolver.pyx:74-75, constants.py:8).
     */
    private static final double NEW_PHASE_SEED_AMOUNT = 1.0e-6;

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

    /**
     * Phase-level Sundman state for every candidate phase.
     *
     * Index i corresponds to phaseModels.get(i). Used only for cheap
     * candidate-level metastable bookkeeping (evaluateAllPhases()'s G/gy/
     * gyy/mA/dMdY refresh, and buildEquilibriumResult()'s metastable-phase
     * output) -- NOT for stable-phase Newton state, which lives in
     * stableSlots below.
     */
    private List<PhaseWork> phaseWorks;

    /**
     * Per-candidate cache of sampled internal-DOF site fractions (endmembers
     * + edges + Halton interior points), keyed by candidate index, computed
     * lazily the first time a candidate's driving force is needed for the
     * addition pass in updateStablePhaseSet(). A single frozen y (e.g.
     * phaseWorks.get(p).y, which never relaxes off its initialize()-time
     * seed for a candidate that has never been stable) can sit at a poor,
     * disordered constitution whose driving force is misleadingly small or
     * negative even when the true best-case driving force for that phase
     * is strongly positive elsewhere in its composition space -- matching
     * pycalphad's own add_new_phases() (eqsolver.pyx), which scans the
     * SAME pre-sampled grid used for initial phase selection, not a single
     * Newton-refined point.
     */
    private Map<Integer, double[][]> candidateGridCache;

    /**
     * Independent Newton-iteration state for every STABLE SLOT, indexed by
     * stable slot k (0..stablePhases.length-1) -- parallel to
     * stablePhases[]/phaseAmounts[]/deltaPhaseAmounts[]/
     * deltaPhaseInternalVars[].
     *
     * This is deliberately a SEPARATE population from the candidate-
     * indexed phaseWorks above: two stable slots can share the same
     * candidate phase (a miscibility gap -- stablePhases = [p, p] with two
     * different site-fraction constitutions), which phaseWorks.get(p)
     * cannot represent since it is one object per candidate. Each entry
     * here wraps the same model reference as its candidate but carries
     * its own independent y, G, gy, gyy, mA, dMdY, mu, gamma, equilData.
     */
    private List<PhaseWork> stableSlots;

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

        /**
         * This phase's Sundman response coefficients at fixed T, P,
         * mu=0: G, M_A, eMat (e_ij), cG (c_iG), eMatNC (R_AB = the global
         * mass-balance response matrix, Sundman Eq. 58/59), and deln
         * (q_A = sum_i dM_A/dy_i * c_iG at this mu=0 evaluation point --
         * see {@link PhaseMatrixAssembler#compute}'s class javadoc for why
         * deln reduces to exactly q_A there). Computed once per phase per
         * iteration by {@link #buildPhaseResponses()}.
         */
        PhaseEquilData equilData;

        PhaseWork(CefGibbs model) {
            this.model = model;
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

                PhaseWork w =
                        stableSlots.get(k);

                System.out.println(
                        "Phase " + k
                        + " (" + w.model.phaseName() + ")"
                        + " omega = " + phaseAmounts[k]);

                System.out.println(
                        "  G = " + w.G);

                double maxAbsCG =
                        0.0;

                for (double v : w.equilData.cG) {
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

                PhaseWork w =
                        stableSlots.get(k);

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

                    PhaseWork w =
                            stableSlots.get(k);

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
     * Stable-phase results are built from stableSlots (one PhaseResult per
     * stable SLOT, not per candidate) so a candidate split across two
     * slots (a miscibility gap) correctly produces two PhaseResults with
     * the same phase name and independent y/amount/G, rather than only
     * the first-found slot's data. Metastable-phase results still come
     * from the candidate-indexed phaseWorks, which is genuinely
     * one-per-candidate bookkeeping.
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
                    compositionFromMoles(mA, nc);

            /*
             * Sundman Eq. (62), driving force:
             *
             *     D^beta = -G^beta + sum_A lambda_A * M_A^beta
             */
            double drivingForce =
                    drivingForce(work, muResult);

            if (!isStable[i]) {

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

        /*
         * Stable-phase results: one PhaseResult per STABLE SLOT (not per
         * candidate), built directly from stableSlots. This is what lets
         * the same candidate phase appear twice with independent y/G/mA
         * when a miscibility gap splits it across two slots -- the
         * previous candidate-loop-plus-first-match search above could
         * only ever report one amount/constitution per candidate name.
         */
        if (stablePhases != null
                && stableSlots != null) {

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                PhaseWork work =
                        stableSlots.get(k);

                double[] slotMA =
                        work.mA.clone();

                double slotTotalM =
                        0.0;

                for (double value : slotMA) {
                    slotTotalM += value;
                }

                if (!(slotTotalM > 0.0)
                        || !Double.isFinite(slotTotalM)) {

                    throw new IllegalStateException(
                            "Invalid phase element amount sum: "
                            + slotTotalM);
                }

                double[] slotX =
                        new double[nc];

                for (int A = 0;
                     A < nc;
                     A++) {

                    slotX[A] =
                            slotMA[A] / slotTotalM;
                }

                double slotDrivingForce =
                        -work.G;

                if (Double.isFinite(work.G)) {

                    for (int A = 0;
                         A < Math.min(
                                 nc,
                                 slotMA.length);
                         A++) {

                        slotDrivingForce +=
                                muResult[A]
                                * slotMA[A];
                    }
                }

                stableResults.add(
                        new EquilibriumResult.PhaseResult(
                                work.model.phaseName(),
                                work.model.modelType(),
                                phaseAmounts[k],
                                slotX,
                                work.y.clone(),
                                work.G,
                                slotDrivingForce));
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
             *
             * Each stable slot gets its OWN PhaseWork instance (not a
             * shared candidate-indexed one from phaseWorks) so that
             * stablePhases containing the same candidate index twice
             * (e.g. {0, 0}) produces two independent constitutions rather
             * than aliasing one object -- see stableSlots' javadoc.
             * ------------------------------------------------------------
             */
            stablePhases =
                    testInitialState.stablePhases.clone();

            phaseAmounts =
                    testInitialState.phaseAmounts.clone();

            stableSlots =
                    new ArrayList<>(stablePhases.length);

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                int p =
                        stablePhases[k];

                CefGibbs cef =
                        (CefGibbs) phaseModels.get(p);

                PhaseWork work =
                        new PhaseWork(cef);

                work.y =
                        testInitialState.y[k].clone();

                evaluatePhaseWork(work);

                stableSlots.add(work);
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

            stableSlots =
                    new ArrayList<>(gridStable.size());

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

                /*
                 * A dedicated PhaseWork per stable slot, not a shared
                 * candidate-indexed one -- GridMinimizer can legitimately
                 * return the same candidate model at two different hull
                 * vertices (a miscibility gap), which needs two
                 * independent constitutions here, not one aliased object.
                 */
                CefGibbs cef =
                        (CefGibbs) phaseModels.get(p);

                PhaseWork work =
                        new PhaseWork(cef);

                work.y =
                        pr.y.clone();

                evaluatePhaseWork(work);

                stableSlots.add(work);
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
         * Initial chemical potentials are obtained from the initially
         * selected stable phase only (stable slot 0).
         */
        calculateChemicalPotentials(
                stableSlots.get(0));

        mu =
                (stableSlots.get(0).mu != null)
                        ? stableSlots.get(0).mu.clone()
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



    /*
     * Obtain an initial chemical-potential estimate for the starting
     * state. This is initialization only.
     *
     * The actual multiphase equilibrium chemical potentials are obtained
     * from the global Sundman equilibrium matrix.
     *
     * Solves G_Y = C^T*gamma + J_M^T*mu (J_M[A][i] = dM_A/dY_i) via a
     * minimum-norm least-squares solve, using the given stable slot's
     * PhaseWork (previously always the phaseWork singular alias, i.e.
     * always candidate 0 -- now the caller's choice, e.g. stable slot 0).
     */
    private void calculateChemicalPotentials(PhaseWork work) {

        if (work == null) {
            throw new IllegalStateException(
                    "Phase has not been evaluated.");
        }

        int nip =
                work.model.numSiteVars();

        int ns =
                work.model.numSublattices();

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
                        work.dMdY[a][i];
            }

            b[i] =
                    work.gy[i];
        }

        // ------------------------------------------------------------
        // C^T * gamma
        // ------------------------------------------------------------
        int[] offsets =
                work.model.offsets();

        int[] nconst =
                work.model
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

        work.mu =
                Arrays.copyOf(
                        solution, nc);

        work.gamma =
                Arrays.copyOfRange(
                        solution, nc,
                        nc + ns);

        this.mu =
                work.mu.clone();
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
     * Sundman Eq. (62) driving force for phase work to become stable at
     * the given chemical potentials:
     *
     *     D = -G_M + sum_A mu_A * M_A
     *
     * Positive D means adding this phase to the stable set would lower
     * total G. Shared by buildEquilibriumResult() (metastable-phase
     * reporting) and updateStablePhaseSet() (add decision) so the Eq. 62
     * formula is not duplicated.
     */
    private double drivingForce(
            PhaseWork work,
            double[] muVector) {

        if (work == null
                || !Double.isFinite(work.G)
                || work.mA == null
                || muVector == null) {

            return Double.NEGATIVE_INFINITY;
        }

        double d =
                -work.G;

        int n =
                Math.min(
                        muVector.length,
                        work.mA.length);

        for (int A = 0; A < n; A++) {

            d +=
                    muVector[A]
                    * work.mA[A];
        }

        return d;
    }

    /**
     * Normalizes element amounts M_A into mole fractions x_A = M_A /
     * sum_A M_A. Shared by buildEquilibriumResult() and
     * updateStablePhaseSet() so the normalization is not duplicated.
     */
    private double[] compositionFromMoles(
            double[] mA,
            int nc) {

        double[] x =
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

        for (int A = 0; A < nc; A++) {

            x[A] =
                    mA[A] / totalM;
        }

        return x;
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

            PhaseWork work =
                    stableSlots.get(k);

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

        int nc =
                targetAmounts.length;

        for (PhaseWork work : phaseWorks) {

            /*
             * eMatNC (R_AB) and deln (q_A, at this mu=0 evaluation point --
             * see PhaseMatrixAssembler.compute()'s class javadoc) come
             * directly from one call, replacing the manual
             * sum_i dM_A/dy_i * c_iB / c_iG projection this method used to
             * recompute from a separately-derived cA.
             */
            work.equilData =
                    PhaseMatrixAssembler.compute(
                            work.model, T, P, work.y, 0.0, 0.0,
                            new double[nc]);
        }

        /*
         * Each stable slot needs its OWN equilData evaluated at its OWN
         * y -- the candidate-indexed pass above cannot serve two stable
         * slots that share one candidate (a miscibility gap), since each
         * candidate has only one PhaseWork in phaseWorks.
         */
        if (stableSlots != null) {

            for (PhaseWork work : stableSlots) {

                work.equilData =
                        PhaseMatrixAssembler.compute(
                                work.model, T, P, work.y, 0.0, 0.0,
                                new double[nc]);
            }
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
                    || work.equilData == null) {

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
                    vectorNorm(work.equilData.cG));

            if (work.equilData.eMatNC.length
                    != targetAmounts.length) {

                throw new IllegalStateException(
                        "Wrong eMatNC dimension at phase "
                        + p);
            }
        }

        System.out.println(
                "Phase-indexed state validation: PASS");
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

        if (stableSlots == null
                || stableSlots.isEmpty()) {

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
                    || phaseIndex >= phaseModels.size()) {

                throw new IllegalStateException(
                        "Invalid stable phase index "
                        + phaseIndex);
            }

            PhaseWork work =
                    stableSlots.get(k);

            if (work == null
                    || work.model == null) {

                throw new IllegalStateException(
                        "Missing PhaseWork for stable phase "
                        + phaseIndex);
            }

            if (work.equilData == null) {

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
        // sibling test for STEP 1-2). Each stable phase's
        // PhaseWork.equilData (computed by buildPhaseResponses(), mu=0)
        // already IS the PhaseEquilData the assembler reads
        // (G, mA, eMatNC=R_AB, deln=q_A) -- no adapter needed.
        // ------------------------------------------------------------

        PhaseEquilData[] phaseData =
                new PhaseEquilData[np];

        double[] stablePhaseAmounts =
                new double[np];

        for (int k = 0; k < np; k++) {

            PhaseWork work =
                    stableSlots.get(k);

            phaseData[k] =
                    work.equilData;

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
                    stableSlots.get(k);

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

        if (stableSlots == null
                || stableSlots.isEmpty()) {

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
                    || phaseIndex >= phaseModels.size()) {

                throw new IllegalStateException(
                        "Invalid stable phase index "
                                + phaseIndex
                                + " at stable slot "
                                + k);
            }

            PhaseWork work =
                    stableSlots.get(k);

            if (work == null
                    || work.model == null) {

                throw new IllegalStateException(
                        "Missing PhaseWork for stable phase "
                                + phaseIndex);
            }

            if (work.equilData == null) {

                throw new IllegalStateException(
                        "Missing Sundman phase response for "
                                + work.model.phaseName());
            }

            final int nip =
                    work.model
                            .numSiteVars();

            /*
             * Sundman Eq. (43): DeltaY_i = c_iG + sum_A c_iA * lambda_A.
             * PhaseMatrixAssembler.compute()'s dely IS exactly this
             * expression when called with mu=newLambda (see its class
             * javadoc's derivation: delyN[i] = cG[i] + sum_j eMat[i][j] *
             * (sum_A dM[A][j] * mu[A]), which is algebraically
             * c_iG + sum_A mu_A * c_iA) -- so this is a direct call, not a
             * second manual re-derivation of c_iA from eMat and dMdY.
             */
            double[] deltaY =
                    PhaseMatrixAssembler.compute(
                            work.model, T, P, work.y, 0.0, 0.0, newLambda)
                            .dely;

            for (int i = 0; i < nip; i++) {

                if (!Double.isFinite(deltaY[i])) {

                    throw new IllegalStateException(
                            "Non-finite DeltaY["
                                    + i
                                    + "] for phase "
                                    + work.model.phaseName()
                                    + ": "
                                    + deltaY[i]);
                }
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
                                    work.equilData.cG));

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

        if (stableSlots == null
                || stableSlots.isEmpty()) {

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

            PhaseWork work =
                    stableSlots.get(k);

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
         * 1. Construct the full-step trial state for every stable
         *    phase, damping the step size wherever a full step would
         *    leave the phase amount or a site fraction out of bounds --
         *    matching pycalphad's advance_state() (minimizer.pyx):
         *
         *      - Phase amounts: compute, once, the largest shared step
         *        size s in (0, 1] such that every omega_k + s*DeltaOmega_k
         *        stays >= MIN_PHASE_AMOUNT (a closed-form cap, not an
         *        iterative search -- pycalphad's own phase_amt_step_size).
         *
         *      - Site fractions: a shared step size, halved until every
         *        Y_i = y_i + s*DeltaY_i falls in [MIN_SITE_FRACTION, 1]
         *        (with pycalphad's own 1e-11 boundary tolerance), then
         *        any residual tiny overshoot is clamped rather than
         *        rejected -- so this NEVER hard-fails on a bounds
         *        violation, exactly like pycalphad. A genuinely invalid
         *        Y (NaN, wrong dimension, sublattice sum badly off) is
         *        still a real bug and remains a hard failure below.
         *
         * Previously this method took the full (undamped) step and
         * hard-threw if the result was invalid ("This solver does not
         * ... damp the step") -- a real behavioral gap from Sundman's own
         * algorithm (which has no explicit validate-or-fail stage at all)
         * and from pycalphad (which prevents invalid states by construction
         * inside this same update step, never by rejecting them after the
         * fact). See docs/solver_flowchart_target.png's STEP 7 discussion.
         * ------------------------------------------------------------
         */

        final double MIN_SITE_FRACTION = 1.0e-14;
        final double BOUNDS_TOLERANCE = 1.0e-11;

        double phaseAmtStepSize =
                1.0;

        for (int k = 0; k < np; k++) {

            double trial =
                    phaseAmounts[k] + deltaPhaseAmounts[k];

            if (trial < MIN_PHASE_AMOUNT
                    && Math.abs(deltaPhaseAmounts[k]) > MIN_PHASE_AMOUNT) {

                phaseAmtStepSize =
                        Math.min(
                                phaseAmtStepSize,
                                (MIN_PHASE_AMOUNT - phaseAmounts[k])
                                        / deltaPhaseAmounts[k]);
            }
        }

        double[] trialOmega =
                new double[np];

        for (int k = 0; k < np; k++) {

            trialOmega[k] =
                    phaseAmounts[k]
                    + phaseAmtStepSize * deltaPhaseAmounts[k];

            if (!Double.isFinite(trialOmega[k])) {

                throw new IllegalStateException(
                        "Non-finite phase amount for stable slot "
                        + k + ": " + trialOmega[k]);
            }

            /*
             * pycalphad's advance_state() (minimizer.pyx) computes this
             * same shared step size but never re-checks the result
             * afterward -- it trusts the closed-form cap. In floating
             * point that cap can still leave a slot fractionally at or
             * just below MIN_PHASE_AMOUNT (rounding, or a second slot's
             * tighter shared cap). pycalphad's own Solver.solve() (not
             * advance_state()) is the place that ever compares a phase
             * amount to zero, and it does so ONLY as a post-hoc cleanup
             * after the inner Newton loop has fully converged (NP<=0.0
             * -> remove), never as a mid-iteration hard failure. Mirror
             * that here: clamp to the floor and let
             * updateStablePhaseSet() (STEP 8, immediately after this
             * method returns) remove the phase -- do not throw.
             */
            if (trialOmega[k] < MIN_PHASE_AMOUNT) {

                trialOmega[k] =
                        MIN_PHASE_AMOUNT;
            }
        }

        double[][] trialY =
                new double[np][];

        for (int k = 0; k < np; k++) {

            PhaseWork work =
                    stableSlots.get(k);

            double[] dy =
                    deltaPhaseInternalVars[k];

            if (dy == null
                    || dy.length != work.y.length) {

                throw new IllegalStateException(
                        "DeltaY dimension mismatch for phase "
                        + work.model.phaseName());
            }

            int nip =
                    work.y.length;

            double siteStepSize =
                    1.0;

            double[] candidate =
                    new double[nip];

            while (true) {

                boolean exceededBounds =
                        false;

                for (int i = 0; i < nip; i++) {

                    double value =
                            work.y[i] + siteStepSize * dy[i];

                    if (value > 1.0) {

                        if (value - 1.0 > BOUNDS_TOLERANCE) {
                            exceededBounds = true;
                        }
                        value = 1.0;

                    } else if (value < MIN_SITE_FRACTION) {

                        if (MIN_SITE_FRACTION - value > BOUNDS_TOLERANCE) {
                            exceededBounds = true;
                        }
                        value = Math.max(
                                work.y[i] / 100.0,
                                MIN_SITE_FRACTION);
                    }

                    candidate[i] = value;
                }

                if (!exceededBounds
                        || siteStepSize < 1.0e-20) {
                    break;
                }

                siteStepSize *= 0.5;
            }

            trialY[k] =
                    candidate;

            for (double v : trialY[k]) {

                if (!Double.isFinite(v)) {

                    throw new IllegalStateException(
                            "Non-finite site fraction for phase "
                            + work.model.phaseName()
                            + " (stable slot " + k + ").");
                }
            }
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
                    stableSlots.get(k);

            // ------------------------------------------------------------
            // Constitution and phase amount
            // ------------------------------------------------------------
            work.y =
                    trialY[k];

            phaseAmounts[k] =
                    trialOmega[k];

            /*
             * Candidate-indexed diagnostic/seed array -- when a candidate
             * has multiple stable slots (a miscibility gap), this reflects
             * only the LAST slot processed for that candidate. Acceptable:
             * phaseInternalVars is not read anywhere in the equilibrium
             * math, only as a rough per-candidate seed/diagnostic.
             */
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

            PhaseWork work =
                    stableSlots.get(k);

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

        /*
         * Linearized phase composition response per stable phase,
         * DM_A^k = q_A^k + sum_B R_AB^k lambda_B -- computed once per
         * phase (not once per (A, k) pair). PhaseMatrixAssembler.compute()
         * 's deln IS exactly this vector when called with mu=newLambda
         * (same relation calculateInternalCorrections() uses for
         * dely/DeltaY), recomputed fresh here rather than assuming
         * work.equilData (mu=0) is still the current point.
         */
        double[][] dMPerPhase =
                new double[stablePhases.length][];

        for (int k = 0;
             k < stablePhases.length;
             k++) {

            PhaseWork work =
                    stableSlots.get(k);

            dMPerPhase[k] =
                    PhaseMatrixAssembler.compute(
                            work.model, T, P, work.y, 0.0, 0.0, newLambda)
                            .deln;
        }

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

                /*
                 * ALL quantities below refer to the point at which
                 * buildEquilibriumMatrix() was assembled.
                 */
                double omega =
                        previousPhaseAmounts[k];

                double oldM =
                        previousPhaseMA[k][A];

                /*
                 * Total first-order element change:
                 *
                 *     DN_A =
                 *         omega * DM_A
                 *         + M_A * DOmega
                 */
                dN +=
                        omega * dMPerPhase[k][A]
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

            PhaseWork work =
                    stableSlots.get(k);

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
    /**
     * Validates the current stable-phase state (finiteness, physical
     * site fractions, mass balance, sublattice normalization,
     * stationarity). Always delegates to {@link #validateMultiphaseUpdate()},
     * which is written generically over np = stablePhases.length and
     * handles np == 1 correctly -- the previous np == 1 special case here
     * (built around the phaseWork singular alias, which only worked when
     * the sole stable phase happened to be candidate 0) has been retired.
     */
    private void validateState() {

        if (stableSlots == null
                || stableSlots.isEmpty()) {

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

        validateMultiphaseUpdate();
    }

    /*
     * Phase-set management, following Sundman Eq. 62/Section 5.6 and
     * pycalphad's own implementation of the same idea (pycalphad's
     * Solver.solve()/add_new_phases(), see the design notes in the
     * approved plan for this change):
     *
     *   omega_k <= MIN_PHASE_AMOUNT  -> remove stable slot k
     *   drivingForce_w > threshold   -> add candidate w (at most one per
     *                                   call, largest driving force wins)
     *
     * pycalphad removes with no cooldown and no special-casing of the
     * last remaining phase (an empty stable set is treated as a solver
     * dead end, not silently prevented) -- this mirrors that exactly.
     * Its only anti-thrashing guard is a composition-distinctness check
     * against phases already present, which is what
     * ADD_COMP_DIFFERENCE_TOL implements below.
     */
    private void updateStablePhaseSet() {

        if (stablePhases == null
                || stablePhases.length == 0
                || phaseAmounts == null
                || stableSlots == null) {
            return;
        }

        // ------------------------------------------------------------
        // 1. Removal pass: drop every stable slot at/below the phase-
        //    amount floor.
        // ------------------------------------------------------------

        List<Integer> toRemove =
                new ArrayList<>();

        for (int k = 0; k < stablePhases.length; k++) {

            if (phaseAmounts[k] <= MIN_PHASE_AMOUNT) {
                toRemove.add(k);
            }
        }

        for (int idx = toRemove.size() - 1; idx >= 0; idx--) {
            removeStableSlot(toRemove.get(idx));
        }

        if (stablePhases.length == 0) {

            throw new IllegalStateException(
                    "Sundman phase-set update removed every stable "
                    + "phase -- no thermodynamically consistent stable "
                    + "set remains.");
        }

        // ------------------------------------------------------------
        // 2. Addition pass: among candidates not currently stable, add
        //    at most one -- the largest driving force above threshold,
        //    skipping any candidate not distinct in composition from an
        //    already-stable slot of the same model.
        //
        //    The candidate's OWN never-relaxed phaseWorks.get(p).y (frozen
        //    at whatever initialize() first seeded it to, since nothing
        //    ever runs a Newton step on a metastable candidate's internal
        //    DOF) is not a reliable place to evaluate driving force -- a
        //    disordered/off-optimum y can show a small or negative driving
        //    force even when the phase's true best-case driving force
        //    elsewhere in its composition space is strongly positive.
        //    pycalphad's own add_new_phases() (eqsolver.pyx) avoids this
        //    by scanning the SAME pre-sampled grid used for initial phase
        //    selection (calculate()'s Halton/endmember points), not a
        //    single Newton-refined point -- mirrored here via
        //    candidateSampledGrid(), which lazily computes and caches
        //    that same style of grid per candidate.
        // ------------------------------------------------------------

        int nc =
                targetAmounts.length;

        boolean[] isStable =
                new boolean[phaseModels.size()];

        for (int idx : stablePhases) {
            isStable[idx] = true;
        }

        int bestCandidate =
                -1;

        double[] bestY =
                null;

        double bestDrivingForce =
                ADD_DRIVING_FORCE_THRESHOLD;

        for (int p = 0; p < phaseModels.size(); p++) {

            if (isStable[p]) {
                continue;
            }

            CefGibbs model =
                    (CefGibbs) phaseModels.get(p);

            double[][] grid =
                    candidateSampledGrid(p, model);

            for (double[] y : grid) {

                double[] mA =
                        model.moles(y);

                double g =
                        model.G(T, P, y);

                double d =
                        -g;

                int n =
                        Math.min(nc, mA.length);

                for (int A = 0; A < n; A++) {
                    d += mu[A] * mA[A];
                }

                if (!(d > bestDrivingForce)) {
                    continue;
                }

                if (isCompositionDuplicate(model, mA, nc)) {
                    continue;
                }

                bestDrivingForce = d;
                bestCandidate = p;
                bestY = y;
            }
        }

        if (bestCandidate >= 0) {
            addStableSlot(bestCandidate, bestY);
        }
    }

    /**
     * Lazily samples and caches candidate p's internal-DOF site-fraction
     * grid (endmembers + edges + Halton interior points), reusing
     * GridMinimizer's own sampler so this matches exactly the same style
     * of points used for initial phase selection.
     */
    private double[][] candidateSampledGrid(
            int p,
            GibbsEnergyModel model) {

        if (candidateGridCache == null) {
            candidateGridCache = new HashMap<>();
        }

        double[][] cached =
                candidateGridCache.get(p);

        if (cached != null) {
            return cached;
        }

        double[][] sampled =
                new GridMinimizer()
                        .sampleSiteFractions(model);

        candidateGridCache.put(p, sampled);

        return sampled;
    }

    /**
     * True if a candidate composition (candidateModel, candidateMA) is
     * within ADD_COMP_DIFFERENCE_TOL (Chebyshev distance) of an
     * already-stable slot of the SAME candidate model -- pycalphad's
     * anti-thrashing guard against re-adding a phase indistinguishable
     * from one already present (eqsolver.pyx add_new_phases()).
     */
    private boolean isCompositionDuplicate(
            GibbsEnergyModel candidateModel,
            double[] candidateMA,
            int nc) {

        double[] candidateX =
                compositionFromMoles(
                        candidateMA,
                        nc);

        for (int k = 0; k < stablePhases.length; k++) {

            PhaseWork stableWork =
                    stableSlots.get(k);

            if (stableWork.model != candidateModel) {
                continue;
            }

            double[] stableX =
                    compositionFromMoles(
                            stableWork.mA,
                            nc);

            double maxDiff =
                    0.0;

            for (int A = 0; A < nc; A++) {

                maxDiff =
                        Math.max(
                                maxDiff,
                                Math.abs(
                                        candidateX[A]
                                        - stableX[A]));
            }

            if (maxDiff <= ADD_COMP_DIFFERENCE_TOL) {
                return true;
            }
        }

        return false;
    }

    /**
     * Removes stable slot k by compacting stablePhases/phaseAmounts/
     * stableSlots -- deltaPhaseAmounts/deltaPhaseInternalVars need no
     * handling here since both are freshly reallocated from
     * stablePhases.length every iteration (buildEquilibriumMatrix(),
     * calculateInternalCorrections()). phaseInternalVars for the removed
     * candidate is left untouched (stale but harmless, same as the
     * existing miscibility-gap caveat on that array).
     */
    private void removeStableSlot(int k) {

        int n =
                stablePhases.length;

        int[] newStablePhases =
                new int[n - 1];

        double[] newPhaseAmounts =
                new double[n - 1];

        List<PhaseWork> newStableSlots =
                new ArrayList<>(n - 1);

        int w =
                0;

        for (int j = 0; j < n; j++) {

            if (j == k) {
                continue;
            }

            newStablePhases[w] = stablePhases[j];
            newPhaseAmounts[w] = phaseAmounts[j];
            newStableSlots.add(stableSlots.get(j));
            w++;
        }

        stablePhases = newStablePhases;
        phaseAmounts = newPhaseAmounts;
        stableSlots = newStableSlots;
    }

    /**
     * Adds candidate p as a new stable slot, seeded from its current
     * metastable constitution phaseWorks.get(p).y (kept current every
     * iteration by evaluateAllPhases(), never mutated except by
     * initialize()/updateState()'s own commit for already-stable slots),
     * with a small positive initial amount -- modeled directly on
     * initialize()'s GridMinimizer-branch loop.
     */
    private void addStableSlot(int p, double[] seedY) {

        int n =
                stablePhases.length;

        int[] newStablePhases =
                Arrays.copyOf(stablePhases, n + 1);

        double[] newPhaseAmounts =
                Arrays.copyOf(phaseAmounts, n + 1);

        newStablePhases[n] = p;
        newPhaseAmounts[n] = NEW_PHASE_SEED_AMOUNT;

        CefGibbs cef =
                (CefGibbs) phaseModels.get(p);

        PhaseWork work =
                new PhaseWork(cef);

        work.y =
                seedY.clone();

        evaluatePhaseWork(work);

        /*
         * pycalphad's CompositionSet has no per-phase mu/gamma at all --
         * mu is purely system-level state (SystemState.chemical_
         * potentials), never duplicated per phase. This codebase instead
         * caches a copy of the single global mu on every PhaseWork
         * (updateState()'s commit loop sets work.mu = newLambdaChecked
         * identically for every stable slot -- never phase-specific).
         * A newly added slot must follow that same existing convention
         * so it is not missing the field checkConvergence() reads.
         */
        work.mu =
                mu.clone();

        recomputeSublatticeMultipliers(work);

        stableSlots.add(work);
        stablePhases = newStablePhases;
        phaseAmounts = newPhaseAmounts;

        phaseInternalVars[p] =
                work.y.clone();
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

        if (stableSlots == null
                || stableSlots.isEmpty()) {
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

            PhaseWork work =
                    stableSlots.get(k);

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

                PhaseWork work =
                        stableSlots.get(k);

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
