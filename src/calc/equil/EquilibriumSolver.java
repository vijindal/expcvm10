package calc.equil;

import contracts.EquilibriumResult;
import contracts.EquilibriumResult.PhaseResult;
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
            // Chemical potentials from partial molar Gibbs energies:
            // mu_code[A] = -G - Gx[A] + Σ_B x_B * Gx[B]
            // This satisfies G + Σ mu_code[A]*x[A] = 0 (Euler identity in code convention)
            double[] gx = pr.model.gradient(pr.x, T);
            double sumXGx = 0;
            for (int ic = 0; ic < nc; ic++) sumXGx += pr.x[ic] * gx[ic];
            double gVal = pr.model.evaluateG(pr.x, T);
            for (int ic = 0; ic < nc; ic++) {
                state.mu[ic] = -gVal - gx[ic] + sumXGx;
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
            solveInitialFractions(state);

            // ────────────────────────────────────────────────────────
            // Initialise μ_A from grid-minimiser tangent plane (§2.3.1)
            // Each stable phase contributes its tangent-plane estimate
            // μ^α_A = -G^α - ∂G^α/∂x_A + Σ_B x^α_B · ∂G^α/∂x_B
            // weighted by phase amount, matching the Lagrangian condition
            // ∂L/∂ℵ^α = 0 ⟹ G^α_m + Σ_A μ_A M^α_A = 0 (Eq. 7).
            // Without this, mu[] starts at zero and wastes early iterations.
            // ────────────────────────────────────────────────────────
            if (numPhaseSetResets == 0) {
                double[] muEst = new double[nc];
                double totalAmount = 0.0;
                for (PhaseRecord pr : state.stablePhases()) {
                    double w = Math.max(pr.amount, 1e-10);
                    double gVal  = pr.model.evaluateG(pr.x, T);
                    double[] gx  = pr.model.gradient(pr.x, T);
                    double sumXGx = 0.0;
                    for (int ic = 0; ic < nc; ic++) sumXGx += pr.x[ic] * gx[ic];
                    for (int ic = 0; ic < nc; ic++) {
                        muEst[ic] += w * (-gVal - gx[ic] + sumXGx);
                    }
                    totalAmount += w;
                }
                for (int ic = 0; ic < nc; ic++) state.mu[ic] = muEst[ic] / totalAmount;
                // Initialise driving forces for all phases with the estimated μ
                for (PhaseRecord pr : state.phases) {
                    double gVal = pr.model.evaluateG(pr.x, T);
                    pr.G = gVal;
                    pr.computeDrivingForce(state.mu);
                }
                LOG.fine("Initial mu from tangent plane: " + java.util.Arrays.toString(state.mu));
            }

            // ────────────────────────────────────────────────────────
            // Newton iteration for current phase set
            // ────────────────────────────────────────────────────────
            converged = true;
            for (int iter = 0; iter < Constants.MAX_ITERATIONS; iter++) {
                totalIterations++;

                // Evaluate all stable phases
                for (PhaseRecord pr : state.stablePhases()) {
                    pr.updateFromModel(T, P, 0, 0, state.mu);
                }

                // Assemble and solve equilibrium matrix
                EquilibriumMatrix eqMat = assembleEquilibriumMatrix(state, iter);
                double[] corrections = eqMat.solve();

                // Damped Newton step with analytical step-limit for negative amounts
                // (§2.3.1 / standard CALPHAD practice):
                //   λ_max = 0.9 × min over phases where ΔN<0 of (ℵ^α / |ΔN^α|)
                // This prevents amounts going negative on the first trial,
                // avoiding wasted halving iterations and premature phase removal.
                List<PhaseRecord> stableNow = state.stablePhases();
                double lambda = 1.0;
                for (int ip = 0; ip < stableNow.size(); ip++) {
                    double dN = corrections[nc + ip];
                    if (dN < 0.0) {
                        double safeStep = 0.9 * stableNow.get(ip).amount / Math.abs(dN);
                        if (safeStep < lambda) lambda = safeStep;
                    }
                }
                lambda = Math.max(lambda, 1.0 / (1 << 10)); // floor at 2^-10

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

                    // Check composition validity (y fractions in range)
                    boolean allValid = true;
                    for (int ip = 0; ip < stableNow.size(); ip++) {
                        if (!stableNow.get(ip).model.isValid(trialY[ip])) {
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

                // Convergence test (§2.3.1, Fig. 1):
                //   |Δμ_A|  < Constants.MU_TOL  for all A  (corrections[0..nc-1])
                //   |Δy_is| < Constants.Y_TOL   for all phases/site-fracs (from data.dely)
                // Δℵ (phase amounts, corrections[nc..]) are NOT part of the test —
                // they have different units and the paper does not include them.
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

                if (muOk && yOk) {
                    break;  // converged for this phase set
                }
            }

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
    // Initial fraction solve: Σ f[ip]·x[ip] = compOverAll
    // ------------------------------------------------------------------

    private void solveInitialFractions(EquilibriumState state) {
        int np = state.stablePhases().size();
        int nc = state.numComponents();

        if (np == 1) {
            // Single phase: f = 1
            state.stablePhases().get(0).amount = 1.0;
            return;
        }

        // Linear system: np equations, np unknowns
        // f[ip]·x[ip][ic] = compOverAll[ic] for each ic, solved for f[]
        // For simplicity, use only the first nc equations (one per component)
        // and the constraint Σ f = 1

        double[][] A = new double[np][np];
        double[] b = new double[np];

        for (int ic = 0; ic < Math.min(nc, np - 1); ic++) {
            for (int ip = 0; ip < np; ip++) {
                A[ic][ip] = state.stablePhases().get(ip).x[ic];
            }
            b[ic] = state.compOverAll[ic];
        }

        // Last equation: sum of fractions = 1
        for (int ip = 0; ip < np; ip++) {
            A[np - 1][ip] = 1.0;
        }
        b[np - 1] = 1.0;

        try {
            Matrix matA = new Matrix(A);
            Matrix matB = new Matrix(b, np);
            Matrix matF = matA.solve(matB);
            for (int ip = 0; ip < np; ip++) {
                double f = matF.get(ip, 0);
                state.stablePhases().get(ip).amount = Math.max(f, 0);
            }
        } catch (Exception e) {
            // Fallback: equal fractions
            for (int ip = 0; ip < np; ip++) {
                state.stablePhases().get(ip).amount = 1.0 / np;
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

        // ── Row block 1: Gibbs-Duhem equations (np rows) ──
        // From ∂L/∂ℵ^α = 0 (Eq. 7): G^α_M + Σ_A μ_A·x^α_A = 0
        // Linearised w.r.t. [Δμ, Δℵ]:
        //   Σ_C J_GD^α[C] Δμ_C = -(G^α + Σ_A μ_A x^α_A)
        // where J_GD^α[C] = x^α_C + Σ_A μ_A · eMat^α[A][C]
        //   (chain rule: x depends on μ through y via eMat as ∂x_A/∂μ_C = eMat[A][C])
        // The Lagrangian condition has NO ℵ dependence → Δℵ columns are zero.
        for (int ip = 0; ip < np; ip++) {
            PhaseRecord pr = state.stablePhases().get(ip);
            PhaseEquilData data = pr.lastCompute;

            // RHS: -(G + Σ μ·x)
            double gibbsSum = pr.G;
            for (int ic = 0; ic < nc; ic++) gibbsSum += state.mu[ic] * pr.x[ic];
            rhs[ip] = -gibbsSum;

            // Jacobian vs Δμ_C: x^α_C + Σ_A μ_A · eMat^α[A][C]
            for (int ic = 0; ic < nc; ic++) {
                double jval = pr.x[ic];
                if (data != null && data.eMat != null) {
                    for (int jc = 0; jc < nc; jc++) {
                        jval += state.mu[jc] * data.eMat[jc][ic]; // eMat[A][C], not transposed
                    }
                }
                jac[ip][ic] = jval;
            }

            // Jacobian vs Δℵ: zero — ℵ does not appear in Eq. 7
            // (cols nc..nc+np-1 remain 0 from array initialisation)
        }

        // ── Row block 2: Mass balance equations (c rows) ──
        // Σ_α ℵ^α·x^α_A = compOverAll_A
        // Rows np..np+nc-1  (NOT nc..2nc-1 — block 1 occupies rows 0..np-1)
        for (int ic = 0; ic < nc; ic++) {
            int row = np + ic;
            // RHS: -(Σ ℵ·x - compOverAll)
            double sum = 0;
            for (PhaseRecord pr : state.stablePhases()) {
                sum += pr.amount * pr.x[ic];
            }
            rhs[row] = -(sum - state.compOverAll[ic]);

            // Jacobian vs μ: J[row, jc] = Σ_α ℵ^α·cAB^α[A,B]
            for (int jc = 0; jc < nc; jc++) {
                double jval = 0;
                for (PhaseRecord pr : state.stablePhases()) {
                    PhaseEquilData data = pr.lastCompute;
                    if (data != null && data.eMat != null &&
                        ic < data.eMat.length && jc < data.eMat[ic].length) {
                        jval += pr.amount * data.eMat[ic][jc];
                    }
                }
                jac[row][jc] = jval;
            }

            // Jacobian vs N: J[row, nc+ip] = x^α_A
            for (int ip = 0; ip < np; ip++) {
                jac[row][nc + ip] = state.stablePhases().get(ip).x[ic];
            }
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
                        + " — returning zero corrections. " + e.getMessage());
                return new double[jac.length];
            }
        }
    }
}
