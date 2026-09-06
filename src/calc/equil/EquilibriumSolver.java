package calc.equil;

import system.ports.EquilibriumResult;
import system.ports.EquilibriumResult.PhaseResult;
import system.model.PhaseEquilData;
import system.model.GibbsEnergyModel;
import util.Matrix;
import util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Core multi-phase equilibrium solver implementing Algorithm A from Sundman et al. (2021).
 *
 * <p>Mirrors the Mathematica {@code phaseq} function structure:
 * <ol>
 *   <li>Initialize internal variables y[] for each phase from starting composition</li>
 *   <li>Solve initial phase fractions from mass balance</li>
 *   <li>Newton iteration:
 *       <ul>
 *         <li>Call GibbsEnergyModel.compute() for each stable phase</li>
 *         <li>Assemble Gibbs-Duhem + mass balance equations</li>
 *         <li>Solve for {Δμ, ΔN}</li>
 *         <li>Damped update with validity check (critical for CVM/CEF)</li>
 *         <li>Check convergence</li>
 *       </ul>
 *   </li>
 *   <li>Phase set management: add/remove phases based on driving force and amounts</li>
 * </ol>
 */
public class EquilibriumSolver {

    private static final Logger LOG = Logger.getLogger(EquilibriumSolver.class.getName());

    private final GridMinimizer gridMinimizer;

    public EquilibriumSolver() {
        this.gridMinimizer = new GridMinimizer();
    }

    /**
     * Solve multi-phase equilibrium.
     *
     * @param T            temperature in Kelvin
     * @param P            pressure in Pa
     * @param compOverAll  overall composition (total moles per component)
     * @param candidates   all candidate phase models
     * @return equilibrium result
     */
    public EquilibriumResult solve(double T, double P,
                                   double[] compOverAll,
                                   List<GibbsEnergyModel> candidates) {
        int nc = compOverAll.length;

        // Clamp composition away from exact 0 to avoid log(0) in mixing entropy
        compOverAll = compOverAll.clone();
        double sum = 0;
        for (int i = 0; i < nc; i++) {
            compOverAll[i] = Math.max(compOverAll[i], 1e-9);
            sum += compOverAll[i];
        }
        for (int i = 0; i < nc; i++) compOverAll[i] /= sum;

        // Get initial estimate from grid minimizer
        EquilibriumState state = gridMinimizer.initialize(candidates, T, P, compOverAll);
        state.T = T;
        state.P = P;

        // ────────────────────────────────────────────────────────────
        // Single-phase fast path: no Newton iteration needed
        // ────────────────────────────────────────────────────────────
        if (state.numStable() == 1) {
            PhaseRecord pr = state.stablePhases().get(0);
            pr.amount = 1.0;
            // μ_A from single-phase tangent: G_M + Σ_A μ_A M^α_A = 0
            // Standard CALPHAD result for single phase:
            double[] gx   = pr.model.gradient(pr.x, T);
            double[] mA   = pr.mA;
            double   gVal = pr.model.evaluateG(pr.x, T);
            double   nfu  = pr.model.nfu();
            double   gTilde = gVal / nfu;
            double sumMGx = 0.0;
            for (int ic = 0; ic < nc; ic++) sumMGx += mA[ic] * gx[ic];
            // mu_A = Gtilde + dG/dx_A - sum_B x_B * dG/dx_B (Euler/tangent-plane
            // relation for a single phase; gx is already per mole of atoms).
            for (int ic = 0; ic < nc; ic++) {
                state.mu[ic] = gTilde + gx[ic] - sumMGx;
            }
            pr.updateFromModel(T, P, 0, 0, state.mu);
            pr.computeDrivingForce(state.mu);

            // Check metastable phases for phase set instability
            boolean anyPhaseToAdd = false;
            for (PhaseRecord mpr : state.metastablePhases()) {
                mpr.updateFromModel(T, P, 0, 0, state.mu);
                mpr.computeDrivingForce(state.mu);
                if (mpr.drivingForce > Constants.DRIVING_FORCE_TOL) anyPhaseToAdd = true;
            }

            // If a metastable phase has positive driving force it wants to enter.
            // Fall through to the full Newton loop to handle the phase set change.
            if (!anyPhaseToAdd) {
                List<PhaseResult> stableResults = new ArrayList<>();
                stableResults.add(new PhaseResult(pr.phaseName(), pr.modelType(),
                        pr.amount, pr.x, pr.y, pr.G, pr.drivingForce));
                List<PhaseResult> metaResults = new ArrayList<>();
                for (PhaseRecord mpr : state.metastablePhases()) {
                    metaResults.add(new PhaseResult(mpr.phaseName(), mpr.modelType(),
                            0.0, mpr.x, mpr.y, mpr.G, mpr.drivingForce));
                }
                return new EquilibriumResult(T, P, state.mu, stableResults, metaResults, true, 1);
            }
            // else: fall through — phase set change detected, handled by Newton loop below
        }

        // ────────────────────────────────────────────────────────────
        // Phase set iteration (outer loop)
        // ────────────────────────────────────────────────────────────
        int numPhaseSetResets = 0;
        boolean converged = false;
        int totalIterations = 0;

        while (!converged && numPhaseSetResets < Constants.MAX_PHASE_SET_RESETS) {

            // ────────────────────────────────────────────────────────
            // Solve initial phase fractions from mass balance
            // ────────────────────────────────────────────────────────
            solveInitialFractions(state, T, P);

            // ────────────────────────────────────────────────────────
            // Initialise μ_A from grid-minimiser tangent plane (§2.3.1)
            // Each stable phase contributes its tangent-plane estimate
            // μ^α_A = -G^α - ∂G^α/∂x_A + Σ_B x^α_B · ∂G^α/∂x_B
            // weighted by phase amount, matching the Lagrangian condition
            // ∂L/∂ℵ^α = 0 ⟹ G^α_m + Σ_A μ_A M^α_A = 0 (Eq. 7).
            // Without this, mu[] starts at zero and wastes early iterations.
            //
            // On the very first phase set, state.mu may already carry a good
            // tangent-plane estimate computed by the single-phase fast path
            // above (before falling through here because a metastable phase
            // had positive driving force). That estimate must be preserved,
            // not discarded — resetting to zero here was throwing away a
            // valid starting point and forcing the first Newton step to
            // cover the full gap from mu=0 to the true equilibrium value.
            // ────────────────────────────────────────────────────────
            if (numPhaseSetResets == 0) {
                // Initialise driving forces for all phases with the current μ
                // (state.mu is whatever the caller/fast-path already set;
                // it defaults to all-zero from EquilibriumState's constructor
                // if no fast-path estimate was computed).
                for (PhaseRecord pr : state.phases) {
                    double gVal = pr.model.evaluateG(pr.x, T);
                    pr.G = gVal;
                    pr.computeDrivingForce(state.mu);
                }
                LOG.fine("Initial mu (preserved from fast path or zero): "
                        + java.util.Arrays.toString(state.mu));
            }

            // ────────────────────────────────────────────────────────
            // Newton iteration for current phase set
            // ────────────────────────────────────────────────────────
            // converged starts false and is set true only when the
            // muOk && yOk tolerance is actually satisfied (see break below).
            // Exhausting MAX_ITERATIONS without hitting that break must
            // leave converged == false, not silently report success.
            converged = false;
            for (int iter = 0; iter < Constants.MAX_ITERATIONS; iter++) {
                totalIterations++;

                // Evaluate all stable phases
                for (PhaseRecord pr : state.stablePhases()) {
                    pr.updateFromModel(T, P, 0, 0, state.mu);
                }

                // Assemble and solve equilibrium matrix
                EquilibriumMatrix eqMat = assembleEquilibriumMatrix(state, iter);
                double[] corrections = eqMat.solve();

                // A singular matrix means no valid Newton correction exists
                // for this iteration -- this is a failed iteration, not a
                // (falsely convergence-satisfying) zero correction.
                if (corrections == null) {
                    converged = false;
                    break;
                }

                // Damped Newton step with analytical step-limit for negative amounts
                // (§2.3.1 / standard CALPHAD practice):
                //   λ_max = 0.9 × min over phases where ΔN<0 of (ℵ^α / |ΔN^α|)
                // This prevents amounts going negative on the first trial,
                // avoiding wasted halving iterations and premature phase removal.
                //
                // The computed safeStep must be allowed to go arbitrarily
                // small (it can legitimately be far below 2^-10 when a phase
                // amount is small and the raw correction is large) — a floor
                // applied via Math.max would override and defeat this safety
                // clamp exactly when it matters most. The halving loop below
                // (which starts from this lambda and repeatedly halves it)
                // provides its own separate lower bound via its fixed
                // iteration count; no additional floor is applied here.
                List<PhaseRecord> stableNow = state.stablePhases();
                double lambda = 1.0;
                for (int ip = 0; ip < stableNow.size(); ip++) {
                    double dN = corrections[nc + ip];
                    if (dN < 0.0) {
                        double safeStep = 0.9 * stableNow.get(ip).amount / Math.abs(dN);
                        if (safeStep < lambda) lambda = safeStep;
                    }
                }

                boolean accepted = false;
                for (int itr = 0; itr < 10 && !accepted; itr++) {
                    // Trial update
                    double[] trialMu = new double[nc];
                    for (int i = 0; i < nc; i++) {
                        trialMu[i] = state.mu[i] + lambda * corrections[i];
                    }

                    double[] trialN = new double[stableNow.size()];
                    for (int ip = 0; ip < stableNow.size(); ip++) {
                        trialN[ip] = stableNow.get(ip).amount + lambda * corrections[nc + ip];
                    }

                    double[][] trialY = new double[stableNow.size()][];
                    for (int ip = 0; ip < stableNow.size(); ip++) {
                        PhaseRecord pr = stableNow.get(ip);
                        trialY[ip] = new double[pr.y.length];
                        PhaseEquilData data = pr.lastCompute;
                        if (data != null && data.dely != null) {
                            for (int j = 0; j < pr.y.length; j++) {
                                trialY[ip][j] = pr.y[j] + lambda * data.dely[j];
                            }
                        } else {
                            System.arraycopy(pr.y, 0, trialY[ip], 0, pr.y.length);
                        }
                    }

                    // Check composition validity (y fractions in range) and
                    // that no phase amount has gone negative.
                    boolean allValid = true;
                    for (int ip = 0; ip < stableNow.size(); ip++) {
                        if (!stableNow.get(ip).model.isValid(trialY[ip])) {
                            allValid = false;
                            break;
                        }
                        if (trialN[ip] < 0.0) {
                            allValid = false;
                            break;
                        }
                    }

                    if (allValid) {
                        state.mu = trialMu;
                        for (int ip = 0; ip < stableNow.size(); ip++) {
                            stableNow.get(ip).y      = trialY[ip];
                            stableNow.get(ip).amount = trialN[ip];
                            stableNow.get(ip).updateComposition();
                        }
                        accepted = true;
                    } else {
                        lambda /= 2.0;
                    }
                }

                if (!accepted) {
                    converged = false;
                    break;
                }

                // Convergence test (§2.3.1, Fig. 1), extended per M2 Step 6/7:
                //   |Δμ_A|  < Constants.MU_TOL  for all A  (corrections[0..nc-1])
                //   |Δy_is| < Constants.Y_TOL   for all phases/site-fracs (from data.dely)
                // Δℵ (phase amounts, corrections[nc..]) are NOT part of the test —
                // they have different units and the paper does not include them.
                //
                // Small Δμ/Δy alone only certifies that the LAST step was
                // small -- it does not certify that the governing equations
                // (Gibbs-Duhem per stable phase, overall mass balance) are
                // actually satisfied at the current state. A phase-set pass
                // can otherwise report converged=true while a stable phase's
                // own Gibbs-Duhem residual, or the mass-balance residual, is
                // still far from zero (see M2 Step 6 diagnostic). Both
                // physical residuals are now required to be small as well,
                // using the phase's own driving force (= Gibbs-Duhem
                // residual, via the existing computeDrivingForce/
                // DRIVING_FORCE_TOL convention) and mA (= M_A, already used
                // by assembleEquilibriumMatrix's mass-balance rows) for the
                // mass-balance residual, reusing Y_TOL as its tolerance
                // (same order of magnitude as the existing site-fraction
                // tolerance; no new tolerance constant introduced).
                boolean muOk = true;
                for (int i = 0; i < nc; i++) {
                    if (Math.abs(lambda * corrections[i]) >= Constants.MU_TOL) { muOk = false; break; }
                }
                boolean yOk = true;
                if (muOk) {
                    outer:
                    for (PhaseRecord pr : stableNow) {
                        PhaseEquilData data = pr.lastCompute;
                        if (data != null && data.dely != null) {
                            for (double dy : data.dely) {
                                if (Math.abs(lambda * dy) >= Constants.Y_TOL) { yOk = false; break outer; }
                            }
                        }
                    }
                }

                // Gibbs-Duhem / phase-equilibrium criterion (M2 Step 9):
                // the direct residual |G_phi + Sum_A mu_A*mA_phi[A]| for
                // each stable phase, matching exactly the Sundman-Eq.7
                // convention assembleEquilibriumMatrix solves (F_phi=0).
                // computeDrivingForce() now computes exactly this quantity
                // (fixed in M2 Step 9 to match the current mA=M_A
                // definition), so it is used directly rather than
                // re-deriving the same sum inline.
                boolean gibbsDuhemOk = true;
                if (muOk && yOk) {
                    for (PhaseRecord pr : stableNow) {
                        pr.computeDrivingForce(state.mu);
                        if (Math.abs(pr.drivingForce) >= Constants.DRIVING_FORCE_TOL) {
                            gibbsDuhemOk = false;
                            break;
                        }
                    }
                }

                boolean massBalanceOk = true;
                if (muOk && yOk && gibbsDuhemOk) {
                    for (int A = 0; A < nc; A++) {
                        double massSum = 0.0;
                        for (PhaseRecord pr : stableNow) massSum += pr.amount * pr.mA[A];
                        if (Math.abs(massSum - state.compOverAll[A]) >= Constants.Y_TOL) {
                            massBalanceOk = false;
                            break;
                        }
                    }
                }

                if (muOk && yOk && gibbsDuhemOk && massBalanceOk) {
                    converged = true;
                    break;  // converged for this phase set
                }
            }
            // If the loop above exhausted MAX_ITERATIONS without ever
            // executing the muOk&&yOk break, converged remains false here
            // (it is never left as a stale "true" from a previous phase set).

            if (!converged) {
                break;  // Couldn't converge within Constants.MAX_ITERATIONS
            }

            // ────────────────────────────────────────────────────────
            // Phase set management: check driving forces and amounts
            // ────────────────────────────────────────────────────────
            List<Integer> toRemove = new ArrayList<>();
            List<Integer> toAdd = new ArrayList<>();

            // Check negative amounts
            for (int ip = 0; ip < state.stablePhases().size(); ip++) {
                if (state.stablePhases().get(ip).amount < Constants.MIN_PHASE_AMOUNT) {
                    toRemove.add(ip);
                }
            }

            // Check driving forces for metastable phases
            for (int im = 0; im < state.metastablePhases().size(); im++) {
                PhaseRecord pr = state.metastablePhases().get(im);
                pr.updateFromModel(T, P, 0, 0, state.mu);
                pr.computeDrivingForce(state.mu);
                if (pr.drivingForce > Constants.DRIVING_FORCE_TOL) {
                    toAdd.add(im);
                }
            }

            if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
                // Phase set changed; reset and iterate
                numPhaseSetResets++;
                converged = false;

                // Remove phases with negative amounts: set stable=false on the PhaseRecord.
                // stablePhases() returns a snapshot list — .remove() on it has no effect
                // on state.phases, so we mutate the PhaseRecord objects directly.
                List<PhaseRecord> stableSnap = state.stablePhases();
                for (int i = toRemove.size() - 1; i >= 0; i--) {
                    stableSnap.get(toRemove.get(i)).stable = false;
                }

                // Add metastable phases: set stable=true on the PhaseRecord.
                List<PhaseRecord> metaSnap = state.metastablePhases();
                for (int im : toAdd) {
                    metaSnap.get(im).stable = true;
                }

                // If no stable phases remain, stop
                if (state.numStable() == 0) {
                    converged = false;
                    break;
                }
            }
        }

        // ────────────────────────────────────────────────────────────
        // Build result
        // ────────────────────────────────────────────────────────────
        List<PhaseResult> stableResults = new ArrayList<>();
        for (PhaseRecord pr : state.stablePhases()) {
            if (pr.amount > 0) {
                pr.computeDrivingForce(state.mu);
                stableResults.add(new PhaseResult(
                        pr.phaseName(), pr.modelType(),
                        pr.amount, pr.x, pr.y,
                        pr.G, pr.drivingForce));
            }
        }

        List<PhaseResult> metastableResults = new ArrayList<>();
        for (PhaseRecord pr : state.metastablePhases()) {
            pr.updateFromModel(T, P, 0, 0, state.mu);
            pr.computeDrivingForce(state.mu);
            metastableResults.add(new PhaseResult(
                    pr.phaseName(), pr.modelType(),
                    0.0, pr.x, pr.y,
                    pr.G, pr.drivingForce));
        }

        return new EquilibriumResult(T, P, state.mu,
                                      stableResults, metastableResults,
                                      converged, totalIterations);
    }

    // ------------------------------------------------------------------
    // Initial fraction solve: Σ_ip ℵ[ip]·mA[ip][A] = compOverAll[A]
    // ------------------------------------------------------------------

    /**
     * Solves the initial phase amounts from the component mass-balance
     * equations
     *
     *     sum_alpha N_alpha M_A^alpha = N_A
     *
     * using the same M_A definition as the Newton mass-balance block.
     *
     * No additional constraint such as sum(N_alpha) = 1 is imposed:
     * Sundman's phase amounts are amounts of phase formula units and need
     * not sum to unity.
     */
    private void solveInitialFractions(
            EquilibriumState state,
            double T,
            double P) {

        int np = state.stablePhases().size();
        int nc = state.numComponents();

        /*
         * Refresh M_A for the current phase constitutions.
         */
        for (PhaseRecord pr : state.stablePhases()) {
            pr.updateFromModel(
                T,
                P,
                0.0,
                0.0,
                new double[nc]
            );
        }

        /*
         * One stable phase:
         *
         * The overall composition determines its constitution, while
         * the amount is one formula-unit-normalized system amount for
         * the present solver convention.
         */
        if (np == 1) {
            state.stablePhases().get(0).amount = 1.0;
            return;
        }

        /*
         * There cannot be more independent phase amounts than there are
         * independent component-balance equations.
         */
        if (np > nc) {
            throw new IllegalStateException(
                "Too many stable phases (" + np
                + ") for " + nc
                + " component mass-balance equations"
            );
        }

        /*
         * Select np independent component equations.
         *
         * For the present binary/two-phase case this simply gives:
         *
         *   [ M_Fe^alpha ] N_alpha = N_Fe
         *   [ M_Cr^alpha ] N_alpha = N_Cr
         *
         * For a future general implementation, row selection should be
         * replaced by rank-revealing selection if necessary.
         */
        double[][] A = new double[np][np];
        double[] b = new double[np];

        for (int row = 0; row < np; row++) {

            int component = row;

            b[row] = state.compOverAll[component];

            for (int ip = 0; ip < np; ip++) {
                A[row][ip] =
                    state.stablePhases().get(ip).mA[component];
            }
        }

        try {

            Matrix matA = new Matrix(A);
            Matrix matB = new Matrix(b, np);
            Matrix matN = matA.solve(matB);

            for (int ip = 0; ip < np; ip++) {

                double amount = matN.get(ip, 0);

                if (!Double.isFinite(amount)) {
                    throw new IllegalStateException(
                        "Non-finite initial amount for phase "
                        + state.stablePhases().get(ip).phaseName()
                    );
                }

                /*
                 * Do not silently turn a negative solution into zero.
                 * A negative solution means that this phase set cannot
                 * represent the specified overall composition with the
                 * current phase constitutions.
                 */
                if (amount < -Constants.MIN_PHASE_AMOUNT) {
                    throw new IllegalStateException(
                        "Negative initial phase amount for "
                        + state.stablePhases().get(ip).phaseName()
                        + ": " + amount
                    );
                }

                state.stablePhases().get(ip).amount =
                    Math.max(0.0, amount);
            }

        } catch (RuntimeException e) {

            /*
             * Initial phase amounts are only an initial guess.
             * If the selected component equations are singular, let the
             * Newton phase-set machinery start from a neutral positive
             * guess rather than silently claiming that mass balance was
             * satisfied.
             */
            LOG.warning(
                "Could not solve initial phase amounts: "
                + e.getMessage()
                + " -- using equal positive initial amounts."
            );

            for (PhaseRecord pr : state.stablePhases()) {
                pr.amount = 1.0 / np;
            }
        }
    }

    // ------------------------------------------------------------------
    // Equilibrium matrix assembly and solving
    // ------------------------------------------------------------------

    private EquilibriumMatrix assembleEquilibriumMatrix(EquilibriumState state, int iter) {
        int np = state.stablePhases().size();
        int nc = state.numComponents();
        int matSize = nc + np;

        double[][] jac = new double[matSize][matSize];
        double[] rhs = new double[matSize];

        // ── Row block 1: Gibbs-Duhem equations (np rows) ──────────────
        // From Sundman Eq.7: G^α_M + Σ_A μ_A M^α_A = 0
        // Linearised: Σ_B (M^α_B + Σ_A μ_A eMatNC[A][B]) Δμ_B
        //           = -(G^α_M + Σ_A μ_A M^α_A)
        // Note: Δℵ does NOT appear in Eq.7 → jac cols nc..nc+np-1 are zero.
        for (int ip = 0; ip < np; ip++) {
            PhaseRecord pr   = state.stablePhases().get(ip);
            PhaseEquilData d = pr.lastCompute;

            // RHS = -(G^α_M + Σ_A μ_A M^α_A)
            double gibbsSum = pr.G;  // G^α_M per FU
            for (int A = 0; A < nc; A++)
                gibbsSum += state.mu[A] * pr.mA[A];
            rhs[ip] = -gibbsSum;

            // Jacobian vs Δμ_B:
            //   jac[ip][B] = M^α_B + Σ_A μ_A * eMatNC[A][B]
            for (int B = 0; B < nc; B++) {
                double jval = pr.mA[B];
                if (d != null && d.eMatNC != null) {
                    for (int A = 0; A < nc; A++)
                        jval += state.mu[A] * d.eMatNC[A][B];
                }
                jac[ip][B] = jval;
            }
            // Columns nc..nc+np-1 remain zero (ℵ not in Eq.7)
        }

        // ── Row block 2: Mass balance equations (nc rows) ─────────────
        // Σ_α ℵ^α M^α_A = Ñ_A   (per Sundman Eq.5 / mass balance)
        // Linearised:
        //   Σ_α (M^α_A Δℵ^α + ℵ^α ΔM^α_A) = Ñ_A - Σ_α ℵ^α M^α_A
        //   ΔM^α_A = Σ_B eMatNC[A][B] Δμ_B
        for (int A = 0; A < nc; A++) {
            int row = np + A;

            // RHS = -(Σ_α ℵ^α M^α_A - Ñ_A)
            double sum = 0.0;
            for (PhaseRecord pr : state.stablePhases())
                sum += pr.amount * pr.mA[A];
            rhs[row] = -(sum - state.compOverAll[A]);

            // Jacobian vs Δμ_B: Σ_α ℵ^α eMatNC[A][B]
            for (int B = 0; B < nc; B++) {
                double jval = 0.0;
                for (PhaseRecord pr : state.stablePhases()) {
                    PhaseEquilData d = pr.lastCompute;
                    if (d != null && d.eMatNC != null
                            && A < d.eMatNC.length
                            && B < d.eMatNC[A].length)
                        jval += pr.amount * d.eMatNC[A][B];
                }
                jac[row][B] = jval;
            }

            // Jacobian vs Δℵ^α: M^α_A
            for (int ip = 0; ip < np; ip++)
                jac[row][nc + ip] = state.stablePhases().get(ip).mA[A];
        }

        return new EquilibriumMatrix(jac, rhs, iter);
    }

    // ------------------------------------------------------------------
    // Equilibrium matrix wrapper
    // ------------------------------------------------------------------

    private class EquilibriumMatrix {
        final double[][] jac;
        final double[] rhs;
        final int iterationNum;

        EquilibriumMatrix(double[][] jac, double[] rhs, int iterationNum) {
            this.jac = jac;
            this.rhs = rhs;
            this.iterationNum = iterationNum;
        }

        /**
         * Solves the linear system, or returns null if the matrix is
         * singular. A singular matrix means no valid Newton correction
         * exists for this iteration; it must be treated as a failed
         * iteration by the caller, not silently replaced with a
         * zero-correction vector (which would trivially satisfy the
         * |correction| < tolerance convergence test and could report
         * false convergence -- see M2 Step 6 diagnostic).
         */
        double[] solve() {
            try {
                Matrix matJ = new Matrix(jac);
                Matrix matRhs = new Matrix(rhs, jac.length);
                Matrix matSol = matJ.solve(matRhs);
                double[] sol = new double[jac.length];
                for (int i = 0; i < jac.length; i++) {
                    sol[i] = matSol.get(i, 0);
                }
                return sol;
            } catch (Exception e) {
                LOG.warning("EquilibriumSolver: singular matrix at iteration " + iterationNum
                        + " — treating as a failed Newton iteration. " + e.getMessage());
                return null;
            }
        }
    }
}
