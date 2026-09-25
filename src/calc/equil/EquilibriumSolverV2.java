package calc.equil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import system.model.GibbsEnergyModel;
import system.model.InternalConstraintSet;
import system.model.PhaseEquilData;
import system.model.cef.CefGibbs;
import system.model.cef.CefInternalStateSampler;
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

    /**
     * When {@code false} (the default), the per-iteration diagnostic trace
     * below (Sundman-equation matrices, lambda/DeltaY/DeltaOmega vectors,
     * convergence-check breakdowns) is suppressed; only genuine callers
     * that want to inspect the solve step-by-step should enable it via
     * {@link #setVerbose}. Off by default so callers like the CLI aren't
     * forced to scroll past ~100 lines of solver internals to find the
     * actual result.
     */
    private boolean verbose = false;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private void log(String message) {
        if (verbose) System.out.println(message);
    }

    private void log() {
        if (verbose) System.out.println();
    }

    private void logf(String format, Object... args) {
        if (verbose) System.out.printf(format, args);
    }

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

    /**
     * Caller-prescribed starting constitutions for boundary/ZPF phases,
     * by phase name -- OpenCalphad's {@code set_constitution}/{@code
     * ycond} pattern (see {@code src/minimizer/matsmin.F90}: a condition
     * on a phase's constituent fractions skips the global gridminimizer
     * entirely, {@code if(ycond) goto 110}). When {@link #addNewStableSlot}
     * needs a starting {@code y} for a newly-appearing phase and this map
     * has an entry for that phase's name, that prescribed {@code y} is
     * used directly and {@link #bestSeedConstitution}'s driving-force
     * search (CEF site-fraction sampling, or the CVM composition scan +
     * per-point relaxation -- expensive: one nested Newton solve per
     * sampled composition) is skipped entirely. Populated only via
     * {@link #setPrescribedBoundaryConstitution}; {@code null}/absent by
     * default, preserving today's automatic-search behavior.
     */
    private Map<String, double[]> prescribedBoundaryConstitutions;

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
     * Coarse composition-sampling density for {@link
     * #bestCvmSeedConstitution}'s automatic CVM boundary-seed search only
     * -- deliberately separate from {@link GridMinimizer}'s own {@code
     * PDENS=2000}, which is unaffected.
     */
    private static final int CVM_BOUNDARY_SEED_PDENS = 50;

    /**
     * Composition-coincidence tolerance (Chebyshev distance in overall
     * mole fraction) used to MERGE two already-stable slots of the same
     * candidate model that have converged toward each other during
     * Newton iteration -- see mergeConvergedDuplicateSlots(). Deliberately
     * the same value as GridMinimizer.SAME_COMPOSITION_TOLERANCE (ported
     * from OpenCalphad's matsmin.F90 same_composition(), xdiff=0.01) since
     * this is the identical phenomenon at a different point in the solve;
     * kept as a separate constant (not shared/extracted) to match this
     * class's existing pattern of ADD_COMP_DIFFERENCE_TOL being its own,
     * differently-tuned tolerance for a related but distinct purpose.
     */
    private static final double SAME_COMPOSITION_TOLERANCE = 0.01;

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
     * Working data for one phase (any {@link GibbsEnergyModel}, CEF or
     * CVM). Generic since Step 5 -- only the CEF-specific candidate-
     * addition/grid-sampling call sites still require a {@link CefGibbs}
     * model beyond this class's own fields.
     */
    private static final class PhaseWork {

        final GibbsEnergyModel model;

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

        PhaseWork(GibbsEnergyModel model) {
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
     * Public production API: prescribe the initial stable-phase set,
     * per-phase constitutions, and phase amounts that {@link #solve}
     * will start from, instead of the normal single-phase initial guess
     * derived by {@link GridMinimizer}.
     *
     * <p>This enables inner solver instances to equilibrate a fixed
     * composition at a prescribed constitution without invoking
     * GridMinimizer. The prescribed state is ONLY an initial state,
     * not the final equilibrium -- the normal Newton/Sundman iteration
     * proceeds from this starting point, allowing phase amounts,
     * constitutions, and the stable-phase set itself to evolve.
     *
     * <p>Solver mathematics are unchanged: Newton equations, phase
     * response calculations, global equilibrium matrix assembly,
     * convergence criteria, phase addition/removal, and constraint
     * machinery all work identically whether the initial state is
     * prescribed or comes from GridMinimizer.
     *
     * <p>Must be called before {@link #solve}.
     *
     * @param stablePhases   candidate-phase indices that start stable
     * @param initialY       initial site-fraction vector per stable
     *                       phase, indexed in the same order as
     *                       {@code stablePhases}
     * @param initialAmounts initial phase amount per stable phase,
     *                       indexed in the same order as
     *                       {@code stablePhases}
     */
    public void setInitialState(
            int[] stablePhases,
            double[][] initialY,
            double[] initialAmounts) {

        if (stablePhases == null
                || initialY == null
                || initialAmounts == null) {

            throw new IllegalArgumentException(
                    "Initial state arrays must not be null.");
        }

        if (stablePhases.length != initialY.length
                || stablePhases.length != initialAmounts.length) {

            throw new IllegalArgumentException(
                    "Initial state arrays must have matching "
                    + "stable-phase length.");
        }

        this.testInitialState =
                new TestInitialState(
                        stablePhases.clone(),
                        initialY.clone(),
                        initialAmounts.clone());
    }

    /**
     * Prescribes a known starting constitution {@code y} for a phase that
     * {@link #addNewStableSlot} may need to introduce during a boundary/
     * ZPF solve ({@link #solveBoundary}/{@link #solveBoundaryReleasingT}/
     * {@link #solveBoundaryReleasingP}), bypassing {@link
     * #bestSeedConstitution}'s automatic driving-force search for that
     * phase entirely.
     *
     * <p>Mirrors OpenCalphad's {@code set_constitution}/{@code ycond}
     * pattern ({@code src/minimizer/matsmin.F90}: fixing a phase's own
     * constituent fractions as a condition skips the global gridminimizer
     * -- {@code if(ycond) goto 110}, no grid search is run when the
     * caller already knows the starting point). The automatic search
     * (CEF site-fraction sampling, or the CVM composition scan with one
     * nested Newton solve per sampled point) exists specifically for the
     * case where the caller does NOT already know a usable starting
     * constitution; when they do, skipping it is both faster and exact,
     * not merely an approximation of the same result.
     *
     * <p>Has no effect on phases already present in the seed {@link
     * EquilibriumResult} passed to a {@code solveBoundary*} call (those
     * are always seeded from the prior converged result, never from this
     * map or from {@link #bestSeedConstitution}) -- only on a phase
     * {@code addNewStableSlot} must introduce fresh.
     *
     * @param phaseName the candidate phase's name (matched against
     *                  {@link GibbsEnergyModel#phaseName()}, same as
     *                  {@code fixedPhaseName} in {@code solveBoundary*})
     * @param y         the prescribed starting constitution; must already
     *                  satisfy {@code model.isValid(y)} for that phase's
     *                  model -- not re-validated or re-relaxed here, used
     *                  exactly as given (the caller is asserting they
     *                  already know a physically admissible starting
     *                  point, the same trust {@code setInitialState}
     *                  extends to its own caller-supplied {@code y}
     *                  arrays)
     */
    public void setPrescribedBoundaryConstitution(String phaseName, double[] y) {

        if (phaseName == null || phaseName.isBlank()) {
            throw new IllegalArgumentException("phaseName must not be blank.");
        }
        if (y == null) {
            throw new IllegalArgumentException("y must not be null.");
        }

        if (prescribedBoundaryConstitutions == null) {
            prescribedBoundaryConstitutions = new HashMap<>();
        }

        prescribedBoundaryConstitutions.put(phaseName, y.clone());
    }

    /**
     * Package-private test hook: prescribe the initial stable-phase set,
     * per-phase constitutions, and phase amounts that {@link #solve}
     * will start from, instead of the normal single-phase initial guess.
     *
     * <p>Deprecated: use the public {@link #setInitialState} instead.
     * This method exists only to preserve backward compatibility with
     * existing test code.
     *
     * @deprecated use {@link #setInitialState} instead
     */
    @Deprecated
    void setInitialStateForTest(
            int[] stablePhases,
            double[][] initialY,
            double[] initialAmounts) {

        setInitialState(stablePhases, initialY, initialAmounts);
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
        return solve(T, P, compOverAll, candidates, false);
    }

    /**
     * As {@link #solve(double, double, double[], List)}, additionally
     * implementing Sundman 2021 Fig. 1's {@code step or map?} early exit:
     * when {@code stopOnStableSetChange} is {@code true} and a Newton
     * iteration's own stable-set update ({@code updateStablePhaseSet()},
     * Fig. 1's {@code gamma^phi>0 or N^alpha<0} test) actually changes the
     * stable set, this method returns IMMEDIATELY with a non-converged
     * {@link EquilibriumResult} carrying a non-null {@link
     * EquilibriumResult#getStableSetChange()} -- the state as it stood at
     * that test, NOT reconverged with the corrected stable set -- instead
     * of looping back to keep iterating (this class's existing behavior,
     * matching Fig. 1's OTHER branch, taken when {@code
     * stopOnStableSetChange} is {@code false}: {@code
     * change_set_of_stable_phases}, loop back, keep iterating to this
     * call's own converged/failed outcome).
     *
     * <p>{@code stopOnStableSetChange=false} (the default via the 4-arg
     * overload) is unchanged from this class's original behavior -- every
     * existing caller is unaffected. This overload exists so a future
     * step/map diagram tracer can opt into the paper's finer-grained,
     * mid-Newton crossing detection (see {@code
     * docs/sundman2021_reference_notes.md} Section 9) without disturbing
     * any current caller; no tracer in this codebase uses {@code true}
     * yet.
     *
     * @param stopOnStableSetChange see above; {@code false} reproduces
     *                              {@link #solve(double, double, double[], List)}
     *                              exactly
     */
    public EquilibriumResult solve(double T,
                      double P,
                      double[] compOverAll,
                      List<GibbsEnergyModel> candidates,
                      boolean stopOnStableSetChange) {

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

            log();
            log(
                    "=== Sundman iteration " + iteration + " ===");

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                PhaseWork w =
                        stableSlots.get(k);

                log(
                        "Phase " + k
                        + " (" + w.model.phaseName() + ")"
                        + " omega = " + phaseAmounts[k]);

                log(
                        "  G = " + w.G);

                double maxAbsCG =
                        0.0;

                for (double v : w.equilData.cG) {
                    maxAbsCG =
                            Math.max(
                                    maxAbsCG,
                                    Math.abs(v));
                }

                log(
                        "  max|cG| = " + maxAbsCG);
            }

            log("lambda:");
            log(
                    "  " + Arrays.toString(newLambda));

            log("DeltaOmega:");
            log(
                    "  " + Arrays.toString(deltaPhaseAmounts));

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                PhaseWork w =
                        stableSlots.get(k);

                log(
                        "DeltaY (phase "
                        + w.model.phaseName() + "):");
                log(
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

                log(
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
            EquilibriumResult.StableSetChange stableSetChange = updateStablePhaseSet();

            if (stopOnStableSetChange && stableSetChange != null) {
                // Sundman 2021 Fig. 1's "step or map?" early exit: the
                // stable set just changed -- bail out NOW, before even
                // checking convergence, with the state as it stands
                // (NOT reconverged with the corrected stable set). See
                // this method's own javadoc and docs/sundman2021_reference_notes.md
                // Section 9.
                return buildEquilibriumResult(false, iteration, stableSetChange);
            }

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
    // ZPF boundary solve (Sundman Algorithm C2)
    // ================================================================

    /**
     * Which condition Algorithm C2 releases at a boundary solve, in place
     * of the phase fixed at {@code fixedAmount} -- Sundman 2021 Fig. 6
     * ("release the current axis condition") releases whichever variable
     * is the walk's own active axis, not always composition; OpenCalphad's
     * {@code map_calcnode} (smp2A.F90, {@code jax=abs(mapline%axandir)})
     * confirms the same: the released condition is always the ACTIVE axis,
     * which can be composition, T, or P depending on what the walk (or
     * Algorithm D's invariant search) is currently doing.
     */
    public enum ReleasedVariable { COMPOSITION, TEMPERATURE, PRESSURE }

    /** Result of {@link #solveBoundary}. */
    public static final class BoundarySolveResult {

        /** The converged equilibrium AT the boundary (fixedPhase amount == fixedAmount). */
        public final EquilibriumResult equilibrium;

        /** The released component's target amount, solved for at the boundary. */
        public final double releasedComponentValue;

        public BoundarySolveResult(EquilibriumResult equilibrium, double releasedComponentValue) {
            this.equilibrium = equilibrium;
            this.releasedComponentValue = releasedComponentValue;
        }
    }

    /**
     * Solves for the exact ZPF (Zero Phase Fraction) boundary where phase
     * {@code fixedPhaseName} has amount exactly {@code fixedAmount} (0 for
     * an ordinary phase-appearance/disappearance boundary), releasing
     * component {@code releasedComponentIndex}'s target amount to be
     * solved for instead -- Sundman 2021 CALPHAD 75's Algorithm C2.
     *
     * <p>Mechanism verified directly against OpenCalphad's own
     * implementation ({@code matsmin.F90}/{@code smp2A.F90}): this is
     * variable ELIMINATION, not a Lagrange-multiplier row. The fixed
     * phase's own phase-equilibrium row stays in the system (it is not
     * exempt from its own {@code M_A*lambda_A = G} equation just because
     * its amount is pinned); only its {@code DeltaOmega} COLUMN is
     * repurposed, via {@link
     * GlobalEquilibriumMatrixAssembler#convertToFixedPhaseAmountSystem},
     * to instead solve for {@code Delta(targetAmounts[
     * releasedComponentIndex])}. See that method's javadoc for the full
     * sign derivation (independently verified numerically against a
     * known-converged V2ZR+BCC_A2 reference point before this method was
     * written).
     *
     * <p>This is a SEPARATE, smaller Newton loop from {@link #solve},
     * reusing {@link #evaluateAllPhases}/{@link #buildPhaseResponses}/
     * {@link #calculateInternalCorrections} (all already generic/
     * dynamically-sized, safe to reuse unchanged) but deliberately NOT
     * reusing {@link #buildEquilibriumMatrix} (hardcodes an {@code
     * omega > 0} guard that a phase pinned at exactly 0 would fail),
     * {@link #updateState} (its phase-amount damping/clamping logic
     * exists to keep every phase's amount positive -- exactly the
     * invariant this method must NOT enforce for the fixed phase), or
     * {@link #updateStablePhaseSet} (must not run at all here -- this
     * solve holds the exact phase set from the moment of crossing
     * detection fixed throughout, per Sundman's own C2 description and
     * OpenCalphad's {@code map_calcnode}).
     *
     * <p>Seeded directly from a caller-supplied prior {@link
     * EquilibriumResult} (typically the last converged ordinary {@link
     * #solve} call before a phase-set change was detected) via the same
     * {@link #setInitialStateForTest}-style mechanism already used for
     * controlled-starting-point tests -- no cold {@link GridMinimizer}
     * restart, matching OpenCalphad's own warm continuation
     * ({@code meq_sameset}, not {@code calceq7}) at a boundary step.
     *
     * @param T                       temperature (K)
     * @param P                       pressure (Pa)
     * @param compOverAll             overall composition to seed the target amounts
     *                                (the released component's entry is overwritten
     *                                as the solve proceeds)
     * @param candidates              candidate phase models (same list solve() uses)
     * @param seed                    the prior converged equilibrium to warm-start from
     * @param fixedPhaseName          name of the phase to fix at {@code fixedAmount}
     * @param fixedAmount             the phase's fixed amount (0 for an ordinary ZPF boundary)
     * @param releasedComponentIndex  index of the component whose target amount
     *                                is released and solved for instead
     * @return the boundary equilibrium and the released component's solved value
     * @throws IllegalStateException if {@code fixedPhaseName} is not present in
     *                                {@code seed}'s stable phases, or the boundary
     *                                solve fails to converge
     */
    public BoundarySolveResult solveBoundary(
            double T,
            double P,
            double[] compOverAll,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult seed,
            String fixedPhaseName,
            double fixedAmount,
            int releasedComponentIndex) {

        int fixedSlotIndex = setUpBoundarySolve(T, P, compOverAll, candidates, seed,
                fixedPhaseName, fixedAmount);

        double internalFixedAmount = phaseAmounts[fixedSlotIndex];

        double releasedValue = solveBoundaryInternalGeneric(
                fixedSlotIndex, internalFixedAmount,
                ReleasedVariable.COMPOSITION, releasedComponentIndex);

        EquilibriumResult eq = buildEquilibriumResult(true, 0);

        return new BoundarySolveResult(eq, releasedValue);
    }

    /**
     * Shared setup for every {@code solveBoundary*} variant: seeds solver
     * state from {@code seed}, locates (or creates) the fixed phase's
     * stable slot, and pins its amount at {@code fixedAmount} (floored to
     * {@link #MIN_PHASE_AMOUNT} -- see the comment previously duplicated
     * in each variant, now here once).
     *
     * @return the fixed phase's stable-slot index
     */
    private int setUpBoundarySolve(
            double T,
            double P,
            double[] compOverAll,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult seed,
            String fixedPhaseName,
            double fixedAmount) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one phase model is required.");
        }

        this.T = T;
        this.P = P;
        this.phaseModels = candidates;
        this.targetAmounts = compOverAll.clone();

        seedFromEquilibriumResult(seed, candidates);

        int fixedSlotIndex = -1;
        for (int k = 0; k < stablePhases.length; k++) {
            if (candidates.get(stablePhases[k]).phaseName().equals(fixedPhaseName)) {
                fixedSlotIndex = k;
                break;
            }
        }

        /*
         * The fixed phase's amount is stored and reported EXACTLY as
         * given -- 0.0 for the paper's own ZPF condition (Sundman 2021
         * S2.3.1: "a stable phase alpha has negative amount... removed";
         * S3.2: "the phase which appears or disappears is set fix with
         * zero amount"). solveBoundaryInternalGeneric() never reads
         * phaseAmounts[fixedSlotIndex] as a divisor (it is a linear
         * coefficient in the mass-balance row, see
         * GlobalEquilibriumMatrixAssembler's own class javadoc) and
         * deliberately does not call buildEquilibriumMatrix()/
         * validateState() (whose omega > 0.0 guards exist for the
         * ORDINARY solve path, not this one) -- so no flooring is needed
         * here to keep the boundary Newton loop well-defined.
         */
        double internalFixedAmount = fixedAmount;

        if (fixedSlotIndex < 0) {
            fixedSlotIndex = addNewStableSlot(candidates, fixedPhaseName, internalFixedAmount);
        } else {
            phaseAmounts[fixedSlotIndex] = internalFixedAmount;
        }

        return fixedSlotIndex;
    }

    /**
     * As {@link #solveBoundary}, but releases TEMPERATURE instead of a
     * composition component -- used to locate an INVARIANT node
     * (eutectic/peritectic), where a genuine walk-axis (T) release is
     * needed because the invariant is a single point where the stable
     * set jumps by more than one phase at once (no adjacent
     * single-phase-change sub-interval exists to solve a composition-
     * release boundary against).
     *
     * <p>Enabled by {@link system.model.PhaseEquilData#dG_dT}/{@code
     * dM_dT} (this session's addition) and {@link
     * GlobalEquilibriumMatrixAssembler#convertToFixedPhaseAmountSystemReleasingT}.
     * The underlying matrix/RHS derivation was independently verified
     * numerically against a known-converged V2ZR+BCC_A2 reference point
     * (DeltaT solved to ~2.5e-4 K at an already-converged T) before this
     * method was written.
     *
     * <p><b>Scope limit: this method only works while {@code np <= nc}
     * after the fixed phase is added.</b> Fixing ONE phase at zero and
     * releasing ONE scalar (T here) assembles a square {@code
     * (nc+np)x(nc+np)} system, but {@link
     * GlobalEquilibriumMatrixAssembler#buildMatrix} never places a
     * DeltaOmega coefficient in any phase-equilibrium row -- those
     * {@code np} rows are nonzero only in the {@code nc} lambda columns.
     * Once a THIRD phase becomes stable at a binary (nc=2) node (Eq. 8:
     * an isobaric binary invariant has exactly {@code p=n+1=3} stable
     * phases), those 3 phase-equilibrium rows are confined to 2 nonzero
     * columns and are therefore linearly dependent BY CONSTRUCTION --
     * the assembled matrix is exactly singular regardless of the new
     * phase's composition or how carefully it is seeded (confirmed
     * directly: even a Halton-sampled, driving-force-optimal seed for
     * the new phase still produces "Matrix is singular" on iteration 0
     * here).
     *
     * <p>This is NOT solved by fixing a second phase and releasing a
     * second condition simultaneously -- neither the Sundman 2021 paper
     * nor OpenCalphad's own implementation ({@code map_calcnode}/{@code
     * meq_sameset}, traced directly this session,
     * {@code src\stepmapplot\smp2A.F90}) ever do that; both always keep
     * Algorithm C2 to exactly one fixed phase and one released
     * condition. When a second phase's driving force also crosses zero
     * during a node solve, OpenCalphad's {@code map_calcnode} treats it
     * as a node-solve FAILURE and {@code map_halfstep} retries from the
     * last converged point with a much smaller walk-axis sub-step (10%
     * of the normal increment, up to 3 attempts) until the jump narrows
     * to a single resolvable phase change. {@code
     * calc.diagram.PhaseDiagramEngine}'s own C1 walk does not yet
     * implement this narrowing retry (only a plain smaller-increment
     * retry on non-convergence); a genuine two-phases-change-together
     * crossing can still defeat this single-fix method (or {@link
     * #solveBoundary}) today.
     *
     * @param T                temperature to seed the search from (the walk's
     *                         current point, at/near the overshoot)
     * @param P                pressure (Pa)
     * @param compOverAll      the FIXED overall composition at which the
     *                         invariant is sought (not released)
     * @param candidates       candidate phase models
     * @param seed             the prior converged equilibrium (2 phases) to
     *                         warm-start from
     * @param fixedPhaseName   name of the (3rd, newly-appearing) phase to
     *                         fix at {@code fixedAmount}
     * @param fixedAmount      the phase's fixed amount (0 for a genuine invariant)
     * @return the boundary equilibrium and the released (solved) temperature
     * @throws IllegalStateException if the boundary solve does not converge
     *                                within {@link #maxIterations} -- see the
     *                                known convergence-speed gap documented above
     */
    public BoundarySolveResult solveBoundaryReleasingT(
            double T,
            double P,
            double[] compOverAll,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult seed,
            String fixedPhaseName,
            double fixedAmount) {

        int fixedSlotIndex = setUpBoundarySolve(T, P, compOverAll, candidates, seed,
                fixedPhaseName, fixedAmount);

        double internalFixedAmount = phaseAmounts[fixedSlotIndex];

        double releasedT = solveBoundaryInternalGeneric(
                fixedSlotIndex, internalFixedAmount, ReleasedVariable.TEMPERATURE, -1);

        EquilibriumResult eq = buildEquilibriumResult(true, 0);

        return new BoundarySolveResult(eq, releasedT);
    }

    /**
     * As {@link #solveBoundaryReleasingT}, but releases PRESSURE instead
     * of temperature -- the direct P-analogue, symmetric with how {@link
     * #solveBoundary} releases a composition component. Subject to the
     * same {@code np <= nc} scope limit documented on {@link
     * #solveBoundaryReleasingT} (a third stable phase makes the
     * assembled matrix singular regardless of which single scalar is
     * released).
     *
     * @param T                temperature (K), held fixed while P is released
     * @param P                pressure to seed the search from (Pa)
     * @param compOverAll      the FIXED overall composition
     * @param candidates       candidate phase models
     * @param seed             the prior converged equilibrium to warm-start from
     * @param fixedPhaseName   name of the phase to fix at {@code fixedAmount}
     * @param fixedAmount      the phase's fixed amount (0 for an ordinary ZPF boundary)
     * @return the boundary equilibrium and the released (solved) pressure
     */
    public BoundarySolveResult solveBoundaryReleasingP(
            double T,
            double P,
            double[] compOverAll,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult seed,
            String fixedPhaseName,
            double fixedAmount) {

        int fixedSlotIndex = setUpBoundarySolve(T, P, compOverAll, candidates, seed,
                fixedPhaseName, fixedAmount);

        double internalFixedAmount = phaseAmounts[fixedSlotIndex];

        double releasedP = solveBoundaryInternalGeneric(
                fixedSlotIndex, internalFixedAmount, ReleasedVariable.PRESSURE, -1);

        EquilibriumResult eq = buildEquilibriumResult(true, 0);

        return new BoundarySolveResult(eq, releasedP);
    }

    /**
     * Dispatches to {@link #solveBoundary}/{@link
     * #solveBoundaryReleasingT}/{@link #solveBoundaryReleasingP} by which
     * variable {@code released} names -- the single entry point Algorithm
     * C1 (Sundman 2021 Fig. 5) needs to solve a ZPF line's next point
     * regardless of which condition the walk currently releases.
     *
     * @param released either {@code COMPOSITION} ({@code
     *                  releasedComponentIndex} selects which component),
     *                  {@code TEMPERATURE}, or {@code PRESSURE}
     * @throws IllegalArgumentException if {@code released} is not one of
     *                                   the three handled values
     */
    public BoundarySolveResult solveZpf(
            double T,
            double P,
            double[] compOverAll,
            List<GibbsEnergyModel> candidates,
            EquilibriumResult seed,
            String fixedPhaseName,
            double fixedAmount,
            ReleasedVariable released,
            int releasedComponentIndex) {

        switch (released) {
            case COMPOSITION:
                return solveBoundary(
                        T, P, compOverAll, candidates,
                        seed, fixedPhaseName, fixedAmount,
                        releasedComponentIndex);

            case TEMPERATURE:
                return solveBoundaryReleasingT(
                        T, P, compOverAll, candidates,
                        seed, fixedPhaseName, fixedAmount);

            case PRESSURE:
                return solveBoundaryReleasingP(
                        T, P, compOverAll, candidates,
                        seed, fixedPhaseName, fixedAmount);

            default:
                throw new IllegalArgumentException("Unsupported released variable: " + released);
        }
    }

    /**
     * Appends a new stable slot for a phase that is not present in the
     * current seed at all -- used when the phase Algorithm C2 must fix
     * at zero amount is a newly-APPEARING one (e.g. LIQUID at a
     * peritectic, seeded from the solid-only side) rather than an
     * already-stable one about to disappear.
     *
     * <p>Seeding this new slot from {@link #initializeSinglePhaseState}
     * (a generic constitution matching only the overall target
     * composition, ignorant of the seed's chemical potentials) was
     * tried and found to make the very first boundary-solve iteration's
     * global matrix SINGULAR: with {@code np} stable phases but only
     * {@code nc} chemical potentials, the {@code np} phase-equilibrium
     * rows ({@code sum_A M_A^k*lambda_A = G^k}, one per stable phase,
     * with all-zero DeltaOmega columns per {@link
     * GlobalEquilibriumMatrixAssembler#buildMatrix}) over-determine
     * {@code lambda} unless the new phase's own {@code (mA, G)} already
     * closely satisfies the SAME {@code lambda} the other phases solved
     * for -- true only once the phase is near its own tangency with the
     * existing hyperplane, which a composition-only initial guess has
     * no reason to satisfy. Releasing one extra scalar (a composition
     * component, or T) frees only one degree of freedom -- nowhere near
     * enough to also pull an arbitrary fresh constitution into
     * tangency in the same Newton step.
     *
     * <p>Fix: seed the new phase's constitution from the sample point
     * (from this model's {@code getStateSampler()}) with the LARGEST
     * Sundman Eq. 62 driving force against the seed's own chemical
     * potentials {@link #mu}. This is exactly how a metastable phase's
     * candidacy is judged everywhere else in this solver ({@link
     * #drivingForce}, {@link #updateStablePhaseSet}'s own add-phase
     * test) -- the sampled point closest to tangency with the current
     * hyperplane is the best available starting constitution, matching
     * how OpenCalphad/pycalphad track a metastable phase's driving
     * force continuously along the walk rather than cold-seeding it
     * only after the crossing is detected.
     *
     * <p>Skipped entirely when the caller has prescribed a starting
     * constitution for this phase via {@link
     * #setPrescribedBoundaryConstitution} -- see that method's javadoc
     * (OpenCalphad's {@code ycond}/{@code set_constitution} pattern).
     *
     * @return the new slot's index into {@link #stablePhases}/{@link
     *         #phaseAmounts}/{@link #stableSlots}
     */
    private int addNewStableSlot(
            List<GibbsEnergyModel> candidates,
            String phaseName,
            double amount) {

        int candidateIndex = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).phaseName().equals(phaseName)) {
                candidateIndex = i;
                break;
            }
        }

        if (candidateIndex < 0) {
            throw new IllegalStateException(
                    "Phase to fix (" + phaseName + ") is not among the "
                    + "given candidate phases.");
        }

        GibbsEnergyModel model = candidates.get(candidateIndex);
        PhaseWork newWork = new PhaseWork(model);

        double[] prescribed = (prescribedBoundaryConstitutions != null)
                ? prescribedBoundaryConstitutions.get(phaseName)
                : null;

        newWork.y = (prescribed != null)
                ? prescribed.clone()
                : bestSeedConstitution(model);
        evaluatePhaseWork(newWork);

        int newSlotCount = stablePhases.length + 1;
        int[] newStablePhases = Arrays.copyOf(stablePhases, newSlotCount);
        double[] newPhaseAmounts = Arrays.copyOf(phaseAmounts, newSlotCount);
        newStablePhases[newSlotCount - 1] = candidateIndex;
        newPhaseAmounts[newSlotCount - 1] = amount;

        stablePhases = newStablePhases;
        phaseAmounts = newPhaseAmounts;
        stableSlots.add(newWork);

        return newSlotCount - 1;
    }

    /**
     * Picks the best available starting constitution for a newly-appearing
     * boundary/ZPF phase against the current seed's chemical potentials
     * {@link #mu} -- see {@link #addNewStableSlot} for why this replaces a
     * generic composition-only initial guess.
     *
     * <p>Dispatches by model type, exactly the same CEF/CVM distinction
     * {@link #updateStablePhaseSet}'s candidate-addition pass already
     * makes, for the same underlying reason (see {@link
     * #relaxCvmCandidateAtComposition}'s javadoc): CEF's internal DOF are
     * directly-sampleable site fractions, but CVM's {@code y=[u;x]} has
     * {@code u} coupled to {@code x} by cluster-probability admissibility
     * constraints that a raw sampler cannot enforce (Phase 4E/4F audits).
     *
     * <ul>
     *   <li><b>CEF</b> (unchanged): sample the phase's site-fraction space
     *       directly via {@link GridMinimizer#sampleSiteFractions} and
     *       score each raw sampled point's driving force.</li>
     *   <li><b>CVM</b>: search OVER COMPOSITION using {@link
     *       GridMinimizer#sampleCompositions} (the same Phase 3A
     *       algorithm {@link GridMinimizer#initialize} already uses to
     *       search composition space), relaxing {@code u} to a stationary
     *       point at each trial composition via {@link
     *       #relaxCvmCandidateAtComposition} before scoring -- a newly-
     *       appearing zero-amount phase is not mass-balance-constrained to
     *       {@link #targetComposition()} (Phase 4F), so the seed's
     *       composition must itself be searched, not fixed.</li>
     * </ul>
     *
     * <p>Falls back to {@link #initializeSinglePhaseState} if {@link #mu}
     * is not yet available (should not happen once {@link
     * #seedFromEquilibriumResult} has run) or no sample/relaxation
     * produces a finite driving force -- this fallback does not guarantee
     * tangency with {@link #mu} (see {@link #addNewStableSlot}'s javadoc
     * on why a composition-only guess can leave the first boundary Newton
     * matrix singular); it exists only so a degenerate search still
     * returns SOME valid constitution rather than throwing.
     */
    private double[] bestSeedConstitution(GibbsEnergyModel model) {

        if (mu == null) {
            return initializeSinglePhaseState(model, targetComposition());
        }

        if (model instanceof system.model.cvm.CvmGibbsModel) {
            return bestCvmSeedConstitution((system.model.cvm.CvmGibbsModel) model);
        }

        double[][] samples = new GridMinimizer().sampleSiteFractions(model);

        double bestDrivingForce = Double.NEGATIVE_INFINITY;
        double[] bestY = null;

        for (double[] y : samples) {

            PhaseWork trial = new PhaseWork(model);
            trial.y = y;

            try {
                evaluatePhaseWork(trial);
            } catch (RuntimeException e) {
                continue;
            }

            double d = drivingForce(trial, mu);
            if (Double.isFinite(d) && d > bestDrivingForce) {
                bestDrivingForce = d;
                bestY = y;
            }
        }

        return (bestY != null)
                ? bestY
                : initializeSinglePhaseState(model, targetComposition());
    }

    /**
     * CVM branch of {@link #bestSeedConstitution}: formulation B of the
     * Phase 4F audit -- search over trial compositions ({@link
     * GridMinimizer#sampleCompositions}), relaxing {@code u} to a
     * stationary, admissible state at each fixed trial composition via
     * {@link #relaxCvmCandidateAtComposition} (the same nested-{@link
     * EquilibriumSolverV2} mechanism {@link #relaxCvmCandidate} already
     * uses for main-loop candidate discovery, just at a searched
     * composition instead of {@link #targetComposition()}), and keeping
     * the relaxed state with the largest finite Sundman Eq. 62 driving
     * force against the current boundary {@link #mu}.
     *
     * <p>Every retained candidate is therefore, by construction: (1) a
     * stationary point of the model's own Gibbs energy at its trial
     * composition ({@code dG/du=0}, via the nested solve), so never a raw
     * unequilibrated {@code [u;x]} guess; (2) {@link
     * system.model.cvm.CvmGibbsModel#isValid} at that state (checked
     * inside {@link #relaxCvmCandidateAtComposition} before it is
     * returned); and (3) at a composition equal to its own trial
     * composition to within the nested solve's own convergence tolerance
     * (single-candidate, single-stable-slot mass balance forces this, the
     * same guarantee {@code GridMinimizerInnerSolverWiringTest} already
     * establishes for this pattern) -- never forcibly replaced by {@link
     * #targetComposition()}.
     *
     * <p>Does not itself construct or modify any correlation-function
     * value -- {@code u} is entirely the nested solver's own Newton
     * output, exactly as {@link #relaxCvmCandidate} already provides for
     * the main loop.
     *
     * <p>Samples trial compositions at {@link #CVM_BOUNDARY_SEED_PDENS},
     * not {@link GridMinimizer}'s full {@code PDENS} -- a boundary seed
     * only needs to be good enough for the boundary Newton loop that
     * follows to refine.
     *
     * @return the best relaxed CVM {@code y=[u;x]}, or {@code null} if no
     *         sampled composition produced a finite-driving-force
     *         candidate (caller falls back to {@link
     *         #initializeSinglePhaseState})
     */
    private double[] bestCvmSeedConstitution(
            system.model.cvm.CvmGibbsModel model) {

        double[][] trialCompositions =
                new GridMinimizer().sampleCompositions(
                        model.numComponents(), CVM_BOUNDARY_SEED_PDENS);

        double bestDrivingForce = Double.NEGATIVE_INFINITY;
        double[] bestY = null;

        for (double[] x : trialCompositions) {

            CvmCandidateRelaxation relaxed =
                    relaxCvmCandidateAtComposition(model, x);

            if (relaxed == null) {
                continue;
            }

            CandidateState state =
                    new CandidateState(relaxed.y, relaxed.G, relaxed.mA);

            double d = candidateDrivingForce(state, mu);

            if (Double.isFinite(d) && d > bestDrivingForce) {
                bestDrivingForce = d;
                bestY = relaxed.y;
            }
        }

        return (bestY != null)
                ? bestY
                : initializeSinglePhaseState(model, targetComposition());
    }

    /**
     * Seeds {@link #stablePhases}/{@link #phaseAmounts}/{@link
     * #stableSlots}/{@link #phaseWorks}/{@link #phaseInternalVars}/
     * {@link #mu} directly from a prior converged {@link
     * EquilibriumResult}, exactly mirroring {@link #initialize}'s
     * {@code testInitialState} branch (one dedicated {@link PhaseWork}
     * per stable slot, not aliased candidate-indexed objects, so two
     * slots sharing one candidate model -- a miscibility gap -- get
     * independent constitutions).
     */
    private void seedFromEquilibriumResult(
            EquilibriumResult seed,
            List<GibbsEnergyModel> candidates) {

        List<EquilibriumResult.PhaseResult> seedStable = seed.getStablePhases();

        int nph = candidates.size();
        int nc = targetAmounts.length;

        phaseWorks = new ArrayList<>(nph);
        for (int p = 0; p < nph; p++) {
            GibbsEnergyModel model = candidates.get(p);
            PhaseWork work = new PhaseWork(model);
            work.y = initializeSinglePhaseState(model, targetComposition());
            evaluatePhaseWork(work);
            phaseWorks.add(work);
        }

        stablePhases = new int[seedStable.size()];
        phaseAmounts = new double[seedStable.size()];
        stableSlots = new ArrayList<>(seedStable.size());

        for (int k = 0; k < seedStable.size(); k++) {

            EquilibriumResult.PhaseResult pr = seedStable.get(k);

            int p = -1;
            for (int i = 0; i < nph; i++) {
                if (candidates.get(i).phaseName().equals(pr.phaseName)) {
                    p = i;
                    break;
                }
            }
            if (p < 0) {
                throw new IllegalStateException(
                        "Seed equilibrium references phase " + pr.phaseName
                        + ", not among the given candidates.");
            }

            stablePhases[k] = p;
            phaseAmounts[k] = pr.amount;

            GibbsEnergyModel model = candidates.get(p);
            PhaseWork work = new PhaseWork(model);
            work.y = pr.y.clone();
            evaluatePhaseWork(work);
            stableSlots.add(work);
        }

        phaseInternalVars = new double[nph][];
        for (int p = 0; p < nph; p++) {
            phaseInternalVars[p] = phaseWorks.get(p).y.clone();
        }

        calculateChemicalPotentials(stableSlots.get(0));
        mu = (stableSlots.get(0).mu != null)
                ? stableSlots.get(0).mu.clone()
                : new double[nc];
    }

    /**
     * The boundary-solve Newton loop itself. Reuses {@link
     * #evaluateAllPhases}/{@link #buildPhaseResponses}/{@link
     * #calculateInternalCorrections}/{@link #checkConvergence} unchanged;
     * builds and solves the fixed-phase system inline (bypassing {@link
     * #buildEquilibriumMatrix}'s positivity guard) and applies the update
     * directly with its own small validation (bypassing {@link
     * #updateState}/{@link #validateState}, both of which assume every
     * slot's amount stays strictly positive -- a hard blocker for a slot
     * fixed at exactly {@code fixedAmount} -- and {@link #validateState}
     * additionally depends on snapshot fields ({@code
     * previousTotalAmounts} etc.) that only {@link #updateState} itself
     * populates). The fixed slot must STAY at {@code fixedAmount}
     * exactly, and the other, non-fixed slots in a 2-3 phase boundary
     * system are normally already well inside their
     * bounds this close to a converged crossing).
     *
     * @return the released component's final solved target-amount value
     */
    private double solveBoundaryInternalGeneric(
            int fixedSlotIndex,
            double fixedAmount,
            ReleasedVariable released,
            int releasedComponentIndex) {

        final int nc = targetAmounts.length;
        final int np = stablePhases.length;

        double releasedValue = released == ReleasedVariable.COMPOSITION
                ? targetAmounts[releasedComponentIndex]
                : released == ReleasedVariable.TEMPERATURE ? this.T : this.P;

        for (int iteration = 0; iteration < maxIterations; iteration++) {

            evaluateAllPhases();
            buildPhaseResponses();

            PhaseEquilData[] phaseData = new PhaseEquilData[np];
            double[] stablePhaseAmounts = new double[np];
            for (int k = 0; k < np; k++) {
                stablePhaseAmounts[k] = phaseAmounts[k];
                phaseData[k] = stableSlots.get(k).equilData;
            }

            double[][] ordinaryMatrix =
                    GlobalEquilibriumMatrixAssembler.buildMatrix(
                            phaseData, stablePhaseAmounts, targetAmounts);
            double[] ordinaryRhs =
                    GlobalEquilibriumMatrixAssembler.buildRhs(
                            phaseData, stablePhaseAmounts, targetAmounts);

            GlobalEquilibriumMatrixAssembler.Result converted;
            switch (released) {
                case TEMPERATURE:
                    converted = GlobalEquilibriumMatrixAssembler.convertToFixedPhaseAmountSystemReleasingT(
                            ordinaryMatrix, ordinaryRhs, phaseData, stablePhaseAmounts,
                            nc, np, fixedSlotIndex);
                    break;
                case PRESSURE:
                    converted = GlobalEquilibriumMatrixAssembler.convertToFixedPhaseAmountSystemReleasingP(
                            ordinaryMatrix, ordinaryRhs, phaseData, stablePhaseAmounts,
                            nc, np, fixedSlotIndex);
                    break;
                default:
                    converted = GlobalEquilibriumMatrixAssembler.convertToFixedPhaseAmountSystem(
                            ordinaryMatrix, ordinaryRhs, nc, np,
                            fixedSlotIndex, releasedComponentIndex);
            }

            equilibriumMatrix = converted.matrix;
            equilibriumRhs = converted.rhs;

            solveEquilibriumMatrix();

            double deltaReleased = deltaPhaseAmounts[fixedSlotIndex];
            deltaPhaseAmounts[fixedSlotIndex] = 0.0;

            calculateInternalCorrections();

            previousMu = (mu != null) ? mu.clone() : new double[nc];
            previousTotalG = 0.0;

            double[] newLambdaChecked = newLambda.clone();
            for (int A = 0; A < nc; A++) {
                if (!Double.isFinite(newLambdaChecked[A])) {
                    throw new IllegalStateException(
                            "Non-finite newLambda[" + A + "]: " + newLambdaChecked[A]);
                }
            }

            for (int k = 0; k < np; k++) {

                PhaseWork work = stableSlots.get(k);

                if (k == fixedSlotIndex) {
                    // Never moves -- stays exactly at fixedAmount.
                    phaseAmounts[k] = fixedAmount;
                } else {
                    double trial = phaseAmounts[k] + deltaPhaseAmounts[k];
                    phaseAmounts[k] = Math.max(trial, MIN_PHASE_AMOUNT);
                }

                double[] dy = deltaPhaseInternalVars[k];
                work.y = boundaryDampedInternalVarStep(work, dy);

                if (!Double.isFinite(phaseAmounts[k])) {
                    throw new IllegalStateException(
                            "Non-finite phase amount for stable slot " + k);
                }

                int phaseIndex = stablePhases[k];
                phaseInternalVars[phaseIndex] = work.y.clone();

                evaluatePhaseWork(work);
                work.mu = newLambdaChecked.clone();
                recomputeSublatticeMultipliers(work);
            }

            mu = newLambdaChecked.clone();

            if (!Double.isFinite(deltaReleased)) {
                throw new IllegalStateException(
                        "Non-finite released-variable delta: " + deltaReleased);
            }

            switch (released) {
                case TEMPERATURE:
                    this.T += deltaReleased;
                    releasedValue = this.T;
                    break;
                case PRESSURE:
                    this.P += deltaReleased;
                    releasedValue = this.P;
                    break;
                default:
                    releasedValue += deltaReleased;
                    // Unlike phaseAmounts (floored at MIN_PHASE_AMOUNT) and site
                    // fractions newY (clamped to [1e-14, 1.0]) a few lines above,
                    // this Newton update was previously applied with NO physical
                    // bound: a poorly-seeded boundary solve (e.g. releasing a
                    // composition from a starting point far from the true
                    // boundary) could walk releasedValue arbitrarily far outside
                    // [0, 1] while every other variable stayed bounded, letting
                    // the iteration "converge" (small residuals in an already
                    // inconsistent system) at a physically nonsensical mole
                    // fraction -- confirmed directly: Ag-Cu, T=1205K, fixing
                    // FCC_A1 at zero and releasing x(Cu) from a seed at T=1210K's
                    // LIQUID-only equilibrium converged to x(Cu)=3.153, no
                    // exception thrown. Clamped the same way for consistency.
                    if (releasedValue < 1.0e-14) releasedValue = 1.0e-14;
                    if (releasedValue > 1.0) releasedValue = 1.0;
                    targetAmounts[releasedComponentIndex] = releasedValue;
            }

            boolean convergedThisIter;
            switch (released) {
                case TEMPERATURE:
                    convergedThisIter = checkConvergence()
                            && Math.abs(deltaReleased) < tolerance * Math.max(1.0, Math.abs(this.T));
                    break;
                case PRESSURE:
                    convergedThisIter = checkConvergence()
                            && Math.abs(deltaReleased) < tolerance * Math.max(1.0, Math.abs(this.P));
                    break;
                default:
                    convergedThisIter = checkConvergence();
            }

            if (convergedThisIter) {
                return releasedValue;
            }
        }

        throw new IllegalStateException(
                "Boundary solve (releasing " + released + ") did not converge within "
                + maxIterations + " iterations.");
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
        return buildEquilibriumResult(converged, iterations, null);
    }

    /**
     * As {@link #buildEquilibriumResult(boolean, int)}, additionally
     * attaching a {@link EquilibriumResult.StableSetChange} -- used only
     * by {@link #solve(double, double, double[], List, boolean)}'s Fig. 1
     * early-exit path; every other call site passes {@code null} via the
     * two-arg overload above.
     */
    private EquilibriumResult buildEquilibriumResult(
            boolean converged,
            int iterations,
            EquilibriumResult.StableSetChange stableSetChange) {

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

                double metaTotalMoles = 0.0;
                for (double value : mA) {
                    metaTotalMoles += value;
                }

                metastableResults.add(
                        new EquilibriumResult.PhaseResult(
                                model.phaseName(),
                                model.modelType(),
                                0.0,
                                x,
                                y,
                                g,
                                drivingForce,
                                metaTotalMoles));
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

            /*
             * Instance labels (OC's own "#1"/"#2" convention, entered
             * once per miscibility-gap split via OC's own
             * enter_composition_set and then kept fixed for the rest of
             * that calculation -- gtp3B.F90). This codebase does not
             * persist a slot-identity across separate solve() calls the
             * way OC's composition-set index does, so instead ordinals
             * are assigned by each duplicate-named slot's own mole
             * fraction, lexicographically ascending, not by its
             * position in stableSlots -- stableSlots' own order is not
             * guaranteed stable between two consecutive walk steps for
             * what is physically the same branch, but along a
             * continuous ZPF line the two branches of a gap do not cross
             * each other's composition, so this keeps "#1"/"#2" attached
             * to the same physical branch step to step.
             */
            java.util.Map<String, java.util.List<Integer>> slotsByName = new java.util.LinkedHashMap<>();
            for (int k = 0; k < stableSlots.size(); k++) {
                slotsByName.computeIfAbsent(stableSlots.get(k).model.phaseName(),
                        n -> new java.util.ArrayList<>()).add(k);
            }
            java.util.Map<Integer, String> instanceLabelBySlot = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, java.util.List<Integer>> e : slotsByName.entrySet()) {
                java.util.List<Integer> slots = e.getValue();
                if (slots.size() == 1) {
                    instanceLabelBySlot.put(slots.get(0), e.getKey());
                    continue;
                }
                slots.sort((k1, k2) -> {
                    double[] mA1 = stableSlots.get(k1).mA;
                    double[] mA2 = stableSlots.get(k2).mA;
                    for (int a = 0; a < Math.min(mA1.length, mA2.length); a++) {
                        int cmp = Double.compare(mA1[a], mA2[a]);
                        if (cmp != 0) return cmp;
                    }
                    return 0;
                });
                for (int ordinal = 0; ordinal < slots.size(); ordinal++) {
                    instanceLabelBySlot.put(slots.get(ordinal), e.getKey() + "#" + (ordinal + 1));
                }
            }

            for (int k = 0;
                 k < stablePhases.length;
                 k++) {

                PhaseWork work =
                        stableSlots.get(k);

                String slotPhaseName = work.model.phaseName();
                String instanceLabel = instanceLabelBySlot.get(k);

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
                                slotPhaseName,
                                instanceLabel,
                                work.model.modelType(),
                                phaseAmounts[k],
                                slotX,
                                work.y.clone(),
                                work.G,
                                slotDrivingForce,
                                slotTotalM));
            }
        }

        return new EquilibriumResult(
                T,
                P,
                muResult,
                stableResults,
                metastableResults,
                converged,
                iterations,
                stableSetChange);
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
     * GridMinimizer is model-agnostic (composition-space sampling), but the
     * candidate-discovery phase-equilibrium search requires the model's
     * internal-variable sampler, which is not yet implemented for CVM.
     * A CVM candidate must instead reach {@link #solve} via {@link
     * #setInitialStateForTest}, which bypasses GridMinimizer with an
     * explicitly supplied stable set.
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

            PhaseWork work =
                    new PhaseWork(model);

            /*
             * For initialization only, seed every candidate phase with
             * a constitution corresponding to the requested overall
             * composition.
             *
             * Later the grid/global initializer will replace this with
             * proper phase-specific starting constitutions.
             *
             * initializeSinglePhaseState() covers every GibbsEnergyModel
             * generically (getInitialInternalVars()/isValid() are already
             * part of the model-agnostic contract) -- see that method's
             * javadoc.
             */
            work.y =
                    initializeSinglePhaseState(
                            model,
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

                PhaseWork work =
                        new PhaseWork(phaseModels.get(p));

                work.y =
                        testInitialState.y[k].clone();

                evaluatePhaseWork(work);

                stableSlots.add(work);
            }

        } else {

            /*
             * Grid/global initial stable-phase set via composition-space
             * sampling: sample composition space uniformly, equilibrate each
             * sampled composition via a single-phase inner solver for each
             * candidate, and use the lower convex hull to find the initial
             * stable-phase set. Returns the hull facet enclosing the
             * requested overall composition, with per-phase equilibrated
             * site fractions and lever-rule amounts.
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
                PhaseWork work =
                        new PhaseWork(phaseModels.get(p));

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
     * Initializes the constitution of a single phase for the requested
     * overall composition, via the model-agnostic {@link
     * GibbsEnergyModel#getInitialInternalVars}/{@link
     * GibbsEnergyModel#isValid} contract.
     *
     * The initialization is delegated to the model itself because it knows
     * its own internal-variable structure -- CEF's sublattice/constituent/
     * vacancy structure via {@link CefGibbs#getInitialInternalVars}, or a
     * CVM phase's {@code u2 = [u; x]} vector via {@link
     * system.model.cvm.CvmGibbsModel#getInitialInternalVars}. No
     * phase-specific expressions are used here.
     */
    private double[] initializeSinglePhaseState(
            GibbsEnergyModel phase,
            double[] xOverall) {

        if (phase == null)
            throw new IllegalArgumentException(
                    "Phase model must not be null.");

        if (xOverall == null || xOverall.length == 0)
            throw new IllegalArgumentException(
                    "Overall composition must not be null or empty.");

        double[] y =
                phase.getInitialInternalVars(xOverall);

        if (y == null)
            throw new IllegalStateException(
                    "Phase model could not generate an initial constitution.");

        if (!phase.isValid(y))
            throw new IllegalStateException(
                    "Initial constitution is invalid for phase "
                    + phase.phaseName() + ".");

        return y;
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

        InternalConstraintSet constraints =
                work.model.getConstraintSet();

        int ns =
                constraints.numConstraints();

        double[][] C =
                constraints.constraintJacobian();

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
        // C^T * gamma: column (nc + k) is constraint k's Jacobian row,
        // transposed into A -- the generic form of "put gamma into the
        // Y positions belonging to a sublattice" (Phase 7A/7B).
        // ------------------------------------------------------------
        for (int s = 0; s < ns; s++) {

            int gammaColumn =
                    nc + s;

            for (int i = 0; i < nip; i++) {

                double coeff =
                        C[s][i];

                if (coeff != 0.0) {
                    A[i][gammaColumn] = coeff;
                }
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
     * Applies one phase's internal-variable Newton step {@code dy} with
     * the SAME step-size-halving damping {@link #updateState}'s ordinary
     * (non-boundary) commit loop already uses, instead of the boundary
     * loop's own previous one-shot per-element {@code [1e-14, 1.0]} clamp.
     *
     * <p>That one-shot clamp bounded each site fraction individually but
     * never restored the per-sublattice (CEF) / per-composition-block
     * (CVM) sum-to-1 constraint {@link GibbsEnergyModel#getConstraintSet}
     * expresses to {@link PhaseMatrixAssembler} -- the linearized Newton
     * step derived from that bordered-Hessian system only approximately
     * satisfies the constraint to first order, so a full, unscaled step
     * can leave the sum measurably off (confirmed directly: a real
     * ternary CEF boundary solve left one sublattice's sum at 1.0024
     * after a single full step, tripping {@code CefGibbs.checkY}'s exact
     * sum-to-1 guard). Halving the step size until {@code
     * work.model.isValid(candidate)} holds (which includes that same
     * sum-to-1 check) restores the guarantee the ordinary solve path
     * already has, without changing the boundary Newton mathematics
     * itself -- only how far along the already-computed direction {@code
     * dy} this step actually goes.
     *
     * @return the damped, bounds-respecting new {@code y} for this phase
     */
    private static double[] boundaryDampedInternalVarStep(
            PhaseWork work,
            double[] dy) {

        final double MIN_SITE_FRACTION = 1.0e-14;
        final double BOUNDS_TOLERANCE = 1.0e-11;

        int nip = work.y.length;
        boolean[] boundedIndex = boundedSiteFractionIndices(work.model, nip);

        double stepSize = 1.0;
        double[] candidate = new double[nip];

        while (true) {

            boolean exceededBounds = false;

            for (int i = 0; i < nip; i++) {

                double value = work.y[i] + stepSize * dy[i];

                if (!boundedIndex[i]) {
                    candidate[i] = value;
                    continue;
                }

                if (value > 1.0) {
                    if (value - 1.0 > BOUNDS_TOLERANCE) {
                        exceededBounds = true;
                    }
                    value = 1.0;
                } else if (value < MIN_SITE_FRACTION) {
                    if (MIN_SITE_FRACTION - value > BOUNDS_TOLERANCE) {
                        exceededBounds = true;
                    }
                    value = Math.max(work.y[i] / 100.0, MIN_SITE_FRACTION);
                }

                candidate[i] = value;
            }

            if ((!exceededBounds && work.model.isValid(candidate))
                    || stepSize < 1.0e-20) {
                break;
            }

            stepSize *= 0.5;
        }

        for (double v : candidate) {
            if (!Double.isFinite(v)) {
                throw new IllegalStateException(
                        "Non-finite site fraction for phase "
                        + work.model.phaseName() + ".");
            }
        }

        return candidate;
    }

    /**
     * True for each y-index that participates in at least one of {@code
     * model}'s declared linear equality constraints ({@link
     * GibbsEnergyModel#getConstraintSet()}) -- the only indices whose
     * physical bound is [0, 1], per CEF site fractions (all of y) or a
     * CVM phase's trailing composition block. Indices outside every
     * constraint (e.g. a CVM phase's correlation-function entries) are
     * not bounded this way. Participation is read directly from the
     * constraint Jacobian's nonzero columns (Phase 7A/7B), rather than
     * reconstructed from sublattice offsets/constituent counts.
     */
    private static boolean[] boundedSiteFractionIndices(
            GibbsEnergyModel model,
            int nip) {

        boolean[] bounded =
                new boolean[nip];

        double[][] C =
                model.getConstraintSet()
                        .constraintJacobian();

        for (double[] row : C) {

            for (int i = 0; i < nip && i < row.length; i++) {

                if (row[i] != 0.0) {
                    bounded[i] = true;
                }
            }
        }

        return bounded;
    }

    /**
     * Evaluate one PhaseWork object from its current constitution.
     */
    private void evaluatePhaseWork(
            PhaseWork work) {

        if (work == null
                || work.model == null) {

            throw new IllegalArgumentException(
                    "PhaseWork must contain a phase model.");
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

        log();
        log(
                "Phase-indexed state");
        log(
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

            logf(
                    "phase %d: %s%n",
                    p,
                    phaseModels.get(p).phaseName());

            logf(
                    "  G      = %.15f%n",
                    work.G);

            logf(
                    "  M      = %s%n",
                    Arrays.toString(work.mA));

            logf(
                    "  sum(M) = %.15f%n",
                    totalM);

            logf(
                    "  Y      = %s%n",
                    Arrays.toString(work.y));

            logf(
                    "  cG norm = %.15e%n",
                    vectorNorm(work.equilData.cG));

            if (work.equilData.eMatNC.length
                    != targetAmounts.length) {

                throw new IllegalStateException(
                        "Wrong eMatNC dimension at phase "
                        + p);
            }
        }

        log(
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
        log();
        log(
                "Multiphase Sundman equilibrium matrix");
        log(
                "--------------------------------------");

        log(
                "Number of stable phases = "
                + np);

        log(
                "Number of components    = "
                + nc);

        log(
                "Matrix size             = "
                + n + " x " + n);

        for (int i = 0;
             i < n;
             i++) {

            logf(
                    "row %d : ",
                    i);

            for (int j = 0;
                 j < n;
                 j++) {

                logf(
                        "% .12e ",
                        A[i][j]);
            }

            logf(
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

        log();
        log(
                "Sundman equilibrium matrix");
        log(
                "--------------------------");

        for (int i = 0;
             i < equilibriumMatrix.length;
             i++) {

            logf(
                    "row %d : ",
                    i);

            for (int j = 0;
                 j < equilibriumMatrix[i].length;
                 j++) {

                logf(
                        "% .12e ",
                        equilibriumMatrix[i][j]);
            }

            logf(
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

        log();
        log(
                "Global Sundman solution");
        log(
                "-----------------------");

        log(
                "newLambda = "
                + Arrays.toString(newLambda));

        log(
                "DeltaOmega = "
                + Arrays.toString(deltaPhaseAmounts));

        log(
                "Solution = "
                + Arrays.toString(equilibriumUnknowns));

        logf(
                "Linear-system residual = %.15e%n",
                residual);

        log();
        log(
                "Phase amount corrections");

        for (int k = 0;
             k < np;
             k++) {

            int phaseIndex =
                    stablePhases[k];

            PhaseWork work =
                    stableSlots.get(k);

            logf(
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

        log();
        log(
                "Multiphase internal-variable corrections");
        log(
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

            log();
            log(
                    "Stable phase slot = "
                            + k);

            log(
                    "Candidate phase   = "
                            + phaseIndex);

            log(
                    "Phase             = "
                            + work.model.phaseName());

            log(
                    "newLambda         = "
                            + Arrays.toString(newLambda));

            log(
                    "cG                = "
                            + Arrays.toString(
                                    work.equilData.cG));

            log(
                    "DeltaY            = "
                            + Arrays.toString(deltaY));

            logf(
                    "||DeltaY||        = %.15e%n",
                    norm);

            log(
                    "Predicted Y       = "
                            + Arrays.toString(predictedY));

            log(
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

        InternalConstraintSet constraints =
                work.model.getConstraintSet();

        int ns =
                constraints.numConstraints();

        double[][] C =
                constraints.constraintJacobian();

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

            for (int i = 0;
                 i < nip;
                 i++) {

                if (C[s][i] == 0.0) {
                    continue;
                }

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

            /*
             * The [MIN_SITE_FRACTION, 1] clamp below is a physical bound
             * on CEF site fractions (and, for CvmGibbsModel, its trailing
             * x-block -- see offsets()/constituentsPerSublattice()'s
             * "sublattice bridge" javadoc there). It does NOT apply to
             * indices outside every declared block, e.g. a CVM phase's
             * leading correlation-function entries, which are legitimately
             * unbounded/negative -- clamping those corrupts the
             * constitution and fails the model's own isValid() afterward.
             */
            boolean[] boundedIndex =
                    boundedSiteFractionIndices(work.model, nip);

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

                    if (!boundedIndex[i]) {

                        candidate[i] = value;
                        continue;
                    }

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

                if ((!exceededBounds
                        && work.model.isValid(candidate))
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

        log();
        log(
                "Accepted multiphase state update");
        log(
                "---------------------------------");

        log(
                "mu(new) = "
                + Arrays.toString(mu));

        for (int k = 0;
             k < np;
             k++) {

            PhaseWork work =
                    stableSlots.get(k);

            logf(
                    "phase slot %d (%s)%n",
                    k,
                    work.model.phaseName());

            logf(
                    "  DeltaOmega      = %.15e%n",
                    deltaPhaseAmounts[k]);

            logf(
                    "  omega(new)      = %.15e%n",
                    phaseAmounts[k]);

            logf(
                    "  ||DeltaY||      = %.15e%n",
                    vectorNorm(
                            deltaPhaseInternalVars[k]));

            log(
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

            logf(
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
            // Sublattice normalization: r_k = C[k]*y - b[k]
            // ----------------------------------------------------------

            InternalConstraintSet constraints =
                    work.model.getConstraintSet();

            double[][] C =
                    constraints.constraintJacobian();

            double[] b =
                    constraints.constraintRhs();

            for (int s = 0;
                 s < C.length;
                 s++) {

                double sum =
                        0.0;

                for (int i = 0;
                     i < work.y.length;
                     i++) {

                    sum +=
                            C[s][i]
                            * work.y[i];
                }

                double r =
                        sum - b[s];

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
        logf(
                "nonlinear target mass residual = %.6e%n",
                maxTargetMassResidual);

        /*
         * ------------------------------------------------------------
         * First-order Sundman mass-balance prediction, evaluated at the
         * exact point where the global matrix was assembled (before
         * this update), via calculateFirstOrderMassResidual().
         * ------------------------------------------------------------
         */
        log();
        log(
                "First-order mass-balance check");
        log(
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
        log();
        log(
                "Multiphase state validation");
        log(
                "---------------------------");

        log(
                "Previous total amounts = "
                + Arrays.toString(
                        previousTotalAmounts));

        log(
                "Current total amounts  = "
                + Arrays.toString(
                        current.amounts));

        log(
                "Target amounts         = "
                + Arrays.toString(
                        targetAmounts));

        logf(
                "Previous total G       = %.15f%n",
                previousG);

        logf(
                "Current total G        = %.15f%n",
                current.totalG);

        logf(
                "Delta G                = %.15e%n",
                current.totalG
                        - previousG);

        logf(
                "max exact Delta N_A    = %.6e%n",
                maxMassChange);

        logf(
                "max target mass residual = %.6e%n",
                maxTargetMassResidual);

        logf(
                "max 1st-order Delta N  = %.6e%n",
                maxFirstOrderMassResidual);

        logf(
                "max normalization      = %.6e%n",
                maxNormalizationResidual);

        logf(
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
        logf(
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

        double[][] C =
                work.model.getConstraintSet()
                        .constraintJacobian();

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

            // C^T gamma: sum over every constraint row k for which y[i]
            // participates (nonzero C[k][i]). An index in no constraint
            // (e.g. a CVM phase's correlation-function entries) carries
            // no such term.
            for (int k = 0; k < C.length; k++) {

                double coeff = C[k][i];

                if (coeff != 0.0) {
                    r -= coeff * work.gamma[k];
                }
            }

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
    /**
     * Sundman 2021 Fig. 1's {@code gamma^phi>0 or N^alpha<0} test result:
     * which phase (if any) the removal/addition pass below actually
     * changed, and in which direction -- {@code null} if this call left
     * the stable set unchanged. Populated only by the removal pass
     * (Section 1, {@code N^alpha<0}) and the addition pass (Section 2,
     * {@code gamma^phi>0}); the same-composition MERGE pass (Section 1.5)
     * is internal bookkeeping cleanup, not a Fig. 1 stable-set change, and
     * never populates this.
     */
    private EquilibriumResult.StableSetChange updateStablePhaseSet() {

        if (stablePhases == null
                || stablePhases.length == 0
                || phaseAmounts == null
                || stableSlots == null) {
            return null;
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

        String removedPhaseName = null;
        if (!toRemove.isEmpty()) {
            // Report the FIRST removed slot's name (matches Fig. 1's
            // single gamma^phi/N^alpha test firing on one phase at a
            // time in the common case); a multi-phase-at-once removal is
            // the caller's own retry/halving territory, not this method's.
            removedPhaseName = stableSlots.get(toRemove.get(0)).model.phaseName();
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
        // 1.5. Same-composition merge pass: unlike isCompositionDuplicate()
        //    (which only guards the addition pass below against adding a
        //    NEW slot indistinguishable from one already stable),
        //    Newton iteration can also drive two slots that were ALREADY
        //    both stable toward the same composition (most often two
        //    slots of the same candidate model converging together,
        //    e.g. a spurious extra miscibility-gap slot collapsing back
        //    onto its sibling). Left unmerged, this produces two nearly
        //    identical rows in the global linear system -- "Matrix is
        //    singular"/"Excessive global linear-system residual"
        //    failures observed on quaternary systems mid-sweep (see
        //    CalculationSessionStepTracerTest Section E). This mirrors
        //    GridMinimizer.mergeDuplicateCompositions()'s
        //    SAME_COMPOSITION_TOLERANCE=0.01 (ported from OpenCalphad's
        //    matsmin.F90 same_composition(), xdiff=0.01), applied here
        //    every iteration instead of once at GridMinimizer init --
        //    the two fixes close different halves of the same gap.
        // ------------------------------------------------------------

        mergeConvergedDuplicateSlots();

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

            GibbsEnergyModel candidate =
                    phaseModels.get(p);

            if (candidate instanceof CefGibbs) {

                /*
                 * CEF candidate addition-by-driving-force: unchanged from
                 * before Step 8. Scans a CEF-specific sampled grid
                 * (candidateSampledGrid(), backed by CefInternalStateSampler
                 * as of Phase 4D) -- this candidate discovery mechanism is
                 * CEF-only because only CEF's internal DOF are direct site
                 * fractions that a generic Halton/endmember sampler can
                 * cover.
                 */
                CefGibbs model =
                        (CefGibbs) candidate;

                double[][] grid =
                        candidateSampledGrid(p, model);

                for (double[] y : grid) {

                    double[] mA =
                            model.moles(y);

                    double g =
                            model.G(T, P, y);

                    CandidateState state =
                            new CandidateState(y, g, mA);

                    double d =
                            candidateDrivingForce(state, mu);

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

            } else if (candidate instanceof system.model.cvm.CvmGibbsModel) {

                /*
                 * CVM candidate addition-by-driving-force (Step 8).
                 *
                 * A CVM candidate's internal variables y = [u; x] are CVCF
                 * correlation functions, not site fractions -- there is no
                 * analogue of GridMinimizer.sampleSiteFractions() for them
                 * (sampling a "grid" of u would sample points with no
                 * guaranteed relation to a stationary/equilibrium cluster
                 * state, and most such points are far from the true
                 * candidate minimum). Evaluating the frozen, never-relaxed
                 * phaseWorks.get(p).y directly (as buildEquilibriumResult()
                 * still does for its own metastable-phase reporting) is
                 * exactly the failure mode this class already documents
                 * for CEF (see the candidateGridCache javadoc): a
                 * disordered/off-optimum y can show a small or even
                 * negative driving force even when the candidate's true
                 * best-case driving force is strongly positive.
                 *
                 * Instead, relax the CVM candidate's OWN internal
                 * variables to their stationary point using the existing
                 * EquilibriumSolverV2 Newton machinery itself (see
                 * relaxCvmCandidate()'s javadoc for exactly how and why),
                 * then evaluate Sundman Eq. 62 driving force at that
                 * relaxed state against the CURRENT outer chemical
                 * potentials mu -- the identical formula drivingForce()
                 * uses everywhere else in this class.
                 */
                CvmCandidateRelaxation relaxed =
                        relaxCvmCandidate(
                                (system.model.cvm.CvmGibbsModel) candidate);

                if (relaxed == null) {
                    continue;
                }

                CandidateState state =
                        new CandidateState(
                                relaxed.y, relaxed.G, relaxed.mA);

                double d =
                        candidateDrivingForce(state, mu);

                if (!(d > bestDrivingForce)) {
                    continue;
                }

                if (isCompositionDuplicate(candidate, relaxed.mA, nc)) {
                    continue;
                }

                bestDrivingForce = d;
                bestCandidate = p;
                bestY = relaxed.y;

            }
            // Any other GibbsEnergyModel subtype is simply never a
            // contender for automatic addition here (not an error) --
            // matches this method's pre-Step-8 behavior for non-CEF
            // candidates in general.
        }

        if (bestCandidate >= 0) {
            String addedPhaseName = phaseModels.get(bestCandidate).phaseName();
            addStableSlot(bestCandidate, bestY);
            // An addition takes precedence when both an addition and a
            // removal happen in the same call (rare, but Fig. 1's own
            // "gamma^phi>0 or N^alpha<0" test does not order the two) --
            // report whichever this method actually still has fresh state
            // for; the addition is the more specific signal since it just
            // happened, vs. removedPhaseName pointing at an already-gone slot.
            return new EquilibriumResult.StableSetChange(
                    addedPhaseName, EquilibriumResult.ChangeDirection.APPEARING);
        }

        if (removedPhaseName != null) {
            return new EquilibriumResult.StableSetChange(
                    removedPhaseName, EquilibriumResult.ChangeDirection.DISAPPEARING);
        }

        return null;
    }

    /**
     * Merges pairs of already-stable slots of the SAME candidate model
     * whose overall compositions have converged to within
     * SAME_COMPOSITION_TOLERANCE -- the per-iteration analogue of
     * GridMinimizer.mergeDuplicateCompositions(), which only runs once
     * at initialization and cannot catch two slots that started apart
     * and converged together mid-solve. Amounts are combined into the
     * lower-indexed slot; the higher-indexed slot is removed via the
     * same removeStableSlot() used by the amount-based removal pass
     * above. If every slot would end up merged into one, the merge is
     * skipped for that pass rather than emptying stablePhases here --
     * the existing "no stable phases" guard above already covers a
     * genuinely empty stable set from the removal pass, and this method
     * must not introduce a second, differently-shaped path to the same
     * failure.
     */
    private void mergeConvergedDuplicateSlots() {

        int nc =
                targetAmounts.length;

        List<Integer> toRemove =
                new ArrayList<>();

        for (int i = 0; i < stablePhases.length; i++) {

            if (toRemove.contains(i)) {
                continue;
            }

            PhaseWork workI =
                    stableSlots.get(i);

            double[] xI =
                    compositionFromMoles(workI.mA, nc);

            for (int j = i + 1; j < stablePhases.length; j++) {

                if (toRemove.contains(j)) {
                    continue;
                }

                PhaseWork workJ =
                        stableSlots.get(j);

                if (workJ.model != workI.model) {
                    continue;
                }

                double[] xJ =
                        compositionFromMoles(workJ.mA, nc);

                double maxDiff =
                        0.0;

                for (int A = 0; A < nc; A++) {
                    maxDiff =
                            Math.max(
                                    maxDiff,
                                    Math.abs(xI[A] - xJ[A]));
                }

                if (maxDiff <= SAME_COMPOSITION_TOLERANCE) {
                    phaseAmounts[i] += phaseAmounts[j];
                    toRemove.add(j);
                }
            }
        }

        if (toRemove.isEmpty()
                || toRemove.size() >= stablePhases.length) {
            return;
        }

        Collections.sort(toRemove, Collections.reverseOrder());

        for (int k : toRemove) {
            removeStableSlot(k);
        }
    }

    /**
     * A candidate phase's state as produced by either model-specific
     * candidate-search mechanism in {@link #updateStablePhaseSet}'s
     * addition pass -- CEF's raw sampled site fractions
     * ({@link #candidateSampledGrid}) or CVM's relaxed stationary point
     * ({@link #relaxCvmCandidate}) -- reduced to exactly the fields
     * {@link #candidateDrivingForce} needs. This is purely an internal
     * scoring adapter: it does not change, and must not be made to
     * change, how either search obtains {@code y}/{@code G}/{@code moles}.
     */
    private static final class CandidateState {

        final double[] y;
        final double G;
        final double[] moles;

        CandidateState(double[] y, double G, double[] moles) {
            this.y = y;
            this.G = G;
            this.moles = moles;
        }
    }

    /**
     * Sundman Eq. (62) driving force for a candidate state to become
     * stable at the given chemical potentials:
     *
     *     D = -G + sum_A mu_A * M_A
     *
     * Identical formula and semantics to {@link #drivingForce(PhaseWork,
     * double[])}, restated over {@link CandidateState} because neither
     * candidate-discovery branch in {@link #updateStablePhaseSet} builds a
     * full {@link PhaseWork} for a not-yet-selected trial state -- CEF
     * scores every raw sampled grid point, and CVM's relaxed result is a
     * {@link CvmCandidateRelaxation}, not a {@code PhaseWork}. Introduced
     * so the addition pass's two branches no longer each inline this sum
     * by hand.
     */
    private double candidateDrivingForce(
            CandidateState state,
            double[] muVector) {

        if (state == null
                || !Double.isFinite(state.G)
                || state.moles == null
                || muVector == null) {

            return Double.NEGATIVE_INFINITY;
        }

        double d =
                -state.G;

        int n =
                Math.min(
                        muVector.length,
                        state.moles.length);

        for (int A = 0; A < n; A++) {

            d +=
                    muVector[A]
                    * state.moles[A];
        }

        return d;
    }

    /**
     * Result of relaxing one CVM candidate's internal variables to a
     * stationary point -- see {@link #relaxCvmCandidate}.
     */
    private static final class CvmCandidateRelaxation {

        final double[] y;
        final double G;
        final double[] mA;

        CvmCandidateRelaxation(double[] y, double G, double[] mA) {
            this.y = y;
            this.G = G;
            this.mA = mA;
        }
    }

    /**
     * Relaxes a not-currently-stable CVM candidate's internal variables
     * {@code y = [u; x]} to a stationary point, so that its Sundman Eq. 62
     * driving force (computed by the caller against the CURRENT outer
     * chemical potentials {@link #mu}) reflects the candidate's actual
     * best-case state rather than a frozen, arbitrary starting guess.
     *
     * <h2>Why this is the correct candidate representation</h2>
     * A CEF candidate's driving force is evaluated over a whole SAMPLED
     * GRID of site-fraction points (candidateSampledGrid()) because CEF's
     * internal DOF ARE directly interpretable composition/constitution
     * points that a Halton/endmember sampler can cover meaningfully. A
     * CVM candidate's internal DOF u are CVCF correlation functions with
     * no such direct sampling interpretation (see this method's caller
     * for the full rationale) -- the model-appropriate candidate
     * representation is instead the phase's OWN equilibrium/stationary
     * state, {@code dG/du = 0} at fixed T, P, x, exactly the quantity
     * Step 7 (EquilibriumSolverV2CvmHillertCrossCheckTest) independently
     * validated this class's existing Newton machinery can produce.
     *
     * <h2>Composition choice: overall x, not a free variable</h2>
     * The candidate is relaxed at the OUTER solve's overall target
     * composition (this.targetComposition()), not left free. This
     * matches how every other single-candidate fixed-composition CVM
     * solve in this codebase is set up (Step 7's cross-check test) and is
     * the only composition available without inventing a second
     * candidate-selection heuristic: Sundman Eq. 62's driving force is by
     * construction a fixed-mu, fixed-candidate-state quantity (grand-
     * potential difference at THIS candidate's (G, M_A) against the
     * CURRENT global mu), not a search over composition -- the global
     * Newton mass-balance equations are what will move a genuinely
     * favorable candidate's amount/composition once it is added to the
     * stable set on a subsequent outer iteration. Relaxing at any other
     * composition would answer a different question (whether the
     * candidate is favorable at SOME OTHER x, not at the composition the
     * rest of the system is actually converging toward).
     *
     * <h2>How relaxation reuses EquilibriumSolverV2's own Newton machinery</h2>
     * This method does NOT implement a second CVM solver: it constructs a
     * fresh {@link EquilibriumSolverV2} instance and calls the SAME public
     * {@link #solve} entry point every other caller uses, with exactly one
     * candidate (this CVM model) and mass balance pinned to the overall
     * composition -- identically to
     * EquilibriumSolverV2CvmHillertCrossCheckTest's already-validated
     * fixed-composition single-CVM-phase pattern. With one candidate and
     * one stable slot, the global Sundman mass-balance equations force
     * that slot's composition to exactly the target x, so this performs
     * precisely the {@code dG/du|_{T,P,x}=0} relaxation described above --
     * the nested solve's internal-variable Newton step, phase matrix
     * assembly, and convergence handling are 100% the shared
     * {@link PhaseMatrixAssembler}/{@link GlobalEquilibriumMatrixAssembler}
     * code path, not a reimplementation.
     *
     * <p>Seeded from {@link
     * system.model.cvm.CvmGibbsModel#getInitialInternalVars}, which
     * itself uses {@link system.model.cvm.CvmPhaseData#evalRandApprox}
     * (already validated in Step 7 to match CEWorkbench's disordered-state
     * ordering to full double precision) -- not an arbitrary or random u.
     *
     * <p>Delegates to {@link #relaxCvmCandidateAtComposition} at {@code
     * this.targetComposition()} -- see that method for the actual
     * mechanism. This wrapper exists only to keep the main-loop call site
     * ({@link #updateStablePhaseSet}) unchanged: it must always mean
     * "relax at the outer solve's overall composition," per this method's
     * own "Composition choice" section above (still true and unchanged by
     * Phase 4G -- {@link #bestSeedConstitution}'s boundary/ZPF use of the
     * generalized helper searches OTHER compositions instead, because a
     * newly-appearing zero-amount boundary phase is not mass-balance-
     * constrained to the overall composition the way a main-loop candidate
     * conceptually is).
     *
     * @return the relaxed state, or {@code null} if the nested solve does
     *         not converge or the candidate's initial state is invalid
     *         (an invalid/non-convergent candidate cannot be favorably
     *         added -- treated the same as "not a contender" rather than
     *         propagating an exception into the outer solve)
     */
    private CvmCandidateRelaxation relaxCvmCandidate(
            system.model.cvm.CvmGibbsModel candidate) {

        return relaxCvmCandidateAtComposition(candidate, targetComposition());
    }

    /**
     * Generalized form of {@link #relaxCvmCandidate}: relaxes {@code
     * candidate}'s internal CVM variables {@code u} to a stationary point
     * at the CALLER-SUPPLIED fixed composition {@code trialComposition},
     * rather than always at {@code this.targetComposition()}.
     *
     * <p>Introduced by Phase 4G so the SAME nested-{@link
     * EquilibriumSolverV2} relaxation mechanism can serve two distinct
     * callers that need it at two different compositions:
     * <ul>
     *   <li>{@link #relaxCvmCandidate} (main-loop candidate discovery,
     *       {@link #updateStablePhaseSet}) -- always at {@code
     *       targetComposition()}, unchanged;</li>
     *   <li>{@link #bestSeedConstitution} (boundary/ZPF new-phase
     *       seeding, {@link #addNewStableSlot}) -- at each composition
     *       {@link GridMinimizer#sampleCompositions} produces, because
     *       (per the Phase 4F audit) a newly-appearing phase pinned at
     *       zero amount is constrained only by its OWN tangent-plane
     *       condition against the current {@link #mu}, not by the outer
     *       mass balance, so its seed composition must itself be
     *       searched rather than fixed at the overall target.</li>
     * </ul>
     *
     * <p>Mechanically identical to the former single-composition body of
     * {@link #relaxCvmCandidate}: {@code
     * candidate.getInitialInternalVars(trialComposition)} seeds a FRESH
     * nested {@link EquilibriumSolverV2} (never the outer/caller
     * instance), with exactly one candidate model and one stable slot
     * ({@code stablePhases={0}}), mass balance pinned to {@code
     * trialComposition}. Neither the outer candidate list nor any outer
     * model index is used -- the inner problem is self-contained, exactly
     * as {@link GridMinimizer#initialize}'s own inner-solve loop and
     * {@code GridMinimizerInnerSolverWiringTest} already establish for
     * this same single-phase-at-fixed-x pattern.
     *
     * @param candidate         the CVM model to relax
     * @param trialComposition  the fixed composition to relax it at
     *                          (length {@code candidate.numComponents()})
     * @return the relaxed state, or {@code null} if the nested solve does
     *         not converge, converges to something other than exactly one
     *         stable phase, or the candidate's initial/relaxed state is
     *         invalid
     */
    private CvmCandidateRelaxation relaxCvmCandidateAtComposition(
            system.model.cvm.CvmGibbsModel candidate,
            double[] trialComposition) {

        double[] y0;
        try {
            y0 = candidate.getInitialInternalVars(trialComposition);
        } catch (RuntimeException e) {
            return null;
        }

        if (y0 == null || !candidate.isValid(y0)) {
            return null;
        }

        List<GibbsEnergyModel> singleCandidateList =
                Collections.singletonList(
                        (GibbsEnergyModel) candidate);

        EquilibriumSolverV2 nestedSolver =
                new EquilibriumSolverV2();

        nestedSolver.setTolerance(tolerance);
        nestedSolver.setInitialState(
                new int[]{0},
                new double[][]{y0},
                new double[]{1.0});

        EquilibriumResult nestedResult;
        try {
            nestedResult =
                    nestedSolver.solve(
                            T, P, trialComposition, singleCandidateList);
        } catch (RuntimeException e) {
            return null;
        }

        if (nestedResult == null || !nestedResult.isConverged()) {
            return null;
        }

        List<EquilibriumResult.PhaseResult> nestedStable =
                nestedResult.getStablePhases();

        if (nestedStable.size() != 1) {
            return null;
        }

        double[] yRelaxed =
                nestedStable.get(0).y;

        if (yRelaxed == null || !candidate.isValid(yRelaxed)) {
            return null;
        }

        double gRelaxed =
                candidate.G(T, P, yRelaxed);

        double[] mARelaxed =
                candidate.moles(yRelaxed);

        return new CvmCandidateRelaxation(
                yRelaxed, gRelaxed, mARelaxed);
    }

    /**
     * Lazily samples and caches candidate p's internal-variable grid,
     * using parameters matching the initial-phase selection constants.
     *
     * <p>Sampling itself is owned by {@link CefInternalStateSampler}, not
     * {@link GridMinimizer} -- {@link GridMinimizer} is composition-space
     * sampling and equilibrium-surface hull initialization; CEF's own
     * internal (site-fraction) state sampling belongs with the CEF model
     * machinery instead (see the Phase 4C audit establishing that {@link
     * GridMinimizer#sampleSiteFractions} and {@link
     * CefInternalStateSampler#sample} are the same ported algorithm,
     * confirmed bit-identical by {@code
     * CefInternalStateSamplerEquivalenceTest}). {@link
     * GridMinimizer#getPDENS()} is reused directly, package-visible for
     * exactly this kind of test/production access, rather than
     * constructing a {@link GridMinimizer} merely to read a constant --
     * candidate discovery no longer depends on {@link GridMinimizer} at
     * all here. The boundary/ZPF path's CEF branch ({@link
     * #bestSeedConstitution}) still calls {@link
     * GridMinimizer#sampleSiteFractions} directly and is intentionally
     * left untouched by this change (Phase 4G's CVM-only boundary-seed
     * fix left CEF's own sampling mechanism as-is; migrating it to
     * {@link CefInternalStateSampler} remains a separate cleanup).
     */
    private double[][] candidateSampledGrid(
            int p,
            CefGibbs model) {

        if (candidateGridCache == null) {
            candidateGridCache = new HashMap<>();
        }

        double[][] cached =
                candidateGridCache.get(p);

        if (cached != null) {
            return cached;
        }

        int density =
                GridMinimizer.getPDENS();

        double[][] sampled =
                new CefInternalStateSampler(
                        model.numSublattices(),
                        model.constituentsPerSublattice(),
                        model.offsets(),
                        model.numSiteVars())
                        .sample(density, density);

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

        GibbsEnergyModel candidateModel =
                phaseModels.get(p);

        PhaseWork work =
                new PhaseWork(candidateModel);

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
            // Sublattice normalization: r_k = C[k]*y - b[k]
            // ----------------------------------------------------------

            InternalConstraintSet constraints =
                    work.model.getConstraintSet();

            double[][] C =
                    constraints.constraintJacobian();

            double[] b =
                    constraints.constraintRhs();

            for (int s = 0;
                 s < C.length;
                 s++) {

                double sum =
                        0.0;

                for (int i = 0;
                     i < work.y.length;
                     i++) {

                    sum +=
                            C[s][i]
                            * work.y[i];
                }

                maxSublatticeResidual =
                        Math.max(
                                maxSublatticeResidual,
                                Math.abs(sum - b[s]));
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

        log();
        log(
                "Convergence check");
        log(
                "-----------------");

        logf(
                "mass balance      = %.6e%n",
                maxMassResidual);

        logf(
                "phase G relation  = %.6e%n",
                maxGibbsResidual);

        logf(
                "sublattice        = %.6e%n",
                maxSublatticeResidual);

        logf(
                "stationarity      = %.6e%n",
                maxStationarity);

        logf(
                "DeltaY norm       = %.6e%n",
                maxDeltaY);

        logf(
                "DeltaOmega        = %.6e%n",
                maxDeltaOmega);

        logf(
                "DeltaMu           = %.6e%n",
                maxDeltaMu);

        log(
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
