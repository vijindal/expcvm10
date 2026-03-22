package thermocalc.equil;

import domain.EquilibriumResult;
import domain.EquilibriumResult.PhaseResult;
import domain.PhaseEquilData;
import domain.PhaseModelPort;
import util.Matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Core multi-phase equilibrium solver implementing Algorithm A from Sundman et al. (2021).
 *
 * <p>Mirrors the Mathematica {@code phaseq} function structure:
 * <ol>
 *   <li>Initialize internal variables y[] for each phase from starting composition</li>
 *   <li>Solve initial phase fractions from mass balance</li>
 *   <li>Newton iteration:
 *       <ul>
 *         <li>Call PhaseModelPort.compute() for each stable phase</li>
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

    private static final double CONVERGED_TOL  = 1e-10;
    private static final double DRIVING_FORCE_TOL = 1e-6;
    private static final int    MAX_ITERATIONS = 200;
    private static final int    MAX_PHASE_SET_RESETS = 5;
    private static final double MIN_AMOUNT = 1e-10;

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
                                   List<PhaseModelPort> candidates) {
        int nc = compOverAll.length;

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
            for (PhaseRecord mpr : state.metastablePhases()) {
                mpr.updateFromModel(T, P, 0, 0, state.mu);
                mpr.computeDrivingForce(state.mu);
            }

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

        // ────────────────────────────────────────────────────────────
        // Phase set iteration (outer loop)
        // ────────────────────────────────────────────────────────────
        int numPhaseSetResets = 0;
        boolean converged = false;
        int totalIterations = 0;

        while (!converged && numPhaseSetResets < MAX_PHASE_SET_RESETS) {

            // ────────────────────────────────────────────────────────
            // Solve initial phase fractions from mass balance
            // ────────────────────────────────────────────────────────
            solveInitialFractions(state);

            // ────────────────────────────────────────────────────────
            // Newton iteration for current phase set
            // ────────────────────────────────────────────────────────
            converged = true;
            for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
                totalIterations++;

                // Evaluate all stable phases
                for (PhaseRecord pr : state.stablePhases()) {
                    pr.updateFromModel(T, P, 0, 0, state.mu);
                }

                // Assemble and solve equilibrium matrix
                EquilibriumMatrix eqMat = assembleEquilibriumMatrix(state);
                double[] corrections = eqMat.solve();

                // Damped Newton step with validity gating
                // (critical from Mathematica code: lambda halving)
                double lambda = 1.0;
                boolean accepted = false;
                for (int itr = 0; itr < 10 && !accepted; itr++) {
                    // Trial update
                    double[] trialMu = new double[nc];
                    for (int i = 0; i < nc; i++) {
                        trialMu[i] = state.mu[i] + lambda * corrections[i];
                    }

                    double[] trialN = new double[state.numStable()];
                    for (int ip = 0; ip < state.numStable(); ip++) {
                        trialN[ip] = state.stablePhases().get(ip).amount +
                                     lambda * corrections[nc + ip];
                    }

                    double[][] trialY = new double[state.numStable()][];
                    for (int ip = 0; ip < state.numStable(); ip++) {
                        PhaseRecord pr = state.stablePhases().get(ip);
                        trialY[ip] = new double[pr.y.length];
                        PhaseEquilData data = pr.lastCompute;
                        if (data != null && data.dely != null) {
                            for (int j = 0; j < pr.y.length; j++) {
                                trialY[ip][j] = pr.y[j] + lambda * data.dely[j];
                            }
                        } else {
                            for (int j = 0; j < pr.y.length; j++) {
                                trialY[ip][j] = pr.y[j];
                            }
                        }
                    }

                    // Check validity for all phases
                    boolean allValid = true;
                    for (int ip = 0; ip < state.numStable(); ip++) {
                        PhaseRecord pr = state.stablePhases().get(ip);
                        if (!pr.model.isValid(trialY[ip])) {
                            allValid = false;
                            break;
                        }
                        if (trialN[ip] < -1e-10) {
                            allValid = false;
                            break;
                        }
                    }

                    if (allValid) {
                        // Accept update
                        state.mu = trialMu;
                        for (int ip = 0; ip < state.numStable(); ip++) {
                            state.stablePhases().get(ip).y = trialY[ip];
                            state.stablePhases().get(ip).amount = trialN[ip];
                            state.stablePhases().get(ip).updateComposition();
                        }
                        accepted = true;
                    } else {
                        // Halve damping
                        lambda /= 2.0;
                    }
                }

                if (!accepted) {
                    converged = false;
                    break;
                }

                // Check convergence
                double maxCorr = 0;
                for (int i = 0; i < corrections.length; i++) {
                    maxCorr = Math.max(maxCorr, Math.abs(lambda * corrections[i]));
                }

                if (maxCorr < CONVERGED_TOL) {
                    break;  // converged for this phase set
                }
            }

            if (!converged) {
                break;  // Couldn't converge within MAX_ITERATIONS
            }

            // ────────────────────────────────────────────────────────
            // Phase set management: check driving forces and amounts
            // ────────────────────────────────────────────────────────
            List<Integer> toRemove = new ArrayList<>();
            List<Integer> toAdd = new ArrayList<>();

            // Check negative amounts
            for (int ip = 0; ip < state.stablePhases().size(); ip++) {
                if (state.stablePhases().get(ip).amount < MIN_AMOUNT) {
                    toRemove.add(ip);
                }
            }

            // Check driving forces for metastable phases
            for (int im = 0; im < state.metastablePhases().size(); im++) {
                PhaseRecord pr = state.metastablePhases().get(im);
                pr.updateFromModel(T, P, 0, 0, state.mu);
                pr.computeDrivingForce(state.mu);
                if (pr.drivingForce > DRIVING_FORCE_TOL) {
                    toAdd.add(im);
                }
            }

            if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
                // Phase set changed; reset and iterate
                numPhaseSetResets++;
                converged = false;

                // Remove phases with negative amounts
                for (int i = toRemove.size() - 1; i >= 0; i--) {
                    state.stablePhases().remove((int) toRemove.get(i));
                }

                // Add metastable phases
                for (int im : toAdd) {
                    PhaseRecord pr = state.metastablePhases().get(im);
                    pr.stable = true;
                    state.stablePhases().add(pr);
                }

                // If all phases removed or no stable phases, stop
                if (state.stablePhases().isEmpty()) {
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

    private EquilibriumMatrix assembleEquilibriumMatrix(EquilibriumState state) {
        int np = state.stablePhases().size();
        int nc = state.numComponents();
        int matSize = nc + np;

        double[][] jac = new double[matSize][matSize];
        double[] rhs = new double[matSize];

        // ── Row block 1: Gibbs-Duhem equations (p rows) ──
        // G^α + Σ_A μ_A·x^α_A = 0
        for (int ip = 0; ip < np; ip++) {
            PhaseRecord pr = state.stablePhases().get(ip);
            PhaseEquilData data = pr.lastCompute;

            // RHS: -(G + Σ μ·x)
            double gibbsSum = pr.G;
            for (int ic = 0; ic < nc; ic++) {
                gibbsSum += state.mu[ic] * pr.x[ic];
            }
            rhs[ip] = -gibbsSum;

            // Jacobian vs μ: J[ip, ic] = x^α_A + Σ_B μ_B·cAB^α[A,B]
            // For now, simplified: J[ip, ic] ≈ x^α_A
            if (data != null && data.eMat != null) {
                for (int ic = 0; ic < nc; ic++) {
                    double jval = pr.x[ic];
                    for (int jc = 0; jc < nc; jc++) {
                        jval += state.mu[jc] * data.eMat[ic][jc];
                    }
                    jac[ip][ic] = jval;
                }
            } else {
                for (int ic = 0; ic < nc; ic++) {
                    jac[ip][ic] = pr.x[ic];
                }
            }

            // Jacobian vs N: J[ip, nc+jp] = δ_ip,jp
            for (int jp = 0; jp < np; jp++) {
                jac[ip][nc + jp] = (ip == jp ? 1.0 : 0.0);
            }
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

        return new EquilibriumMatrix(jac, rhs);
    }

    // ------------------------------------------------------------------
    // Equilibrium matrix wrapper
    // ------------------------------------------------------------------

    private static class EquilibriumMatrix {
        final double[][] jac;
        final double[] rhs;

        EquilibriumMatrix(double[][] jac, double[] rhs) {
            this.jac = jac;
            this.rhs = rhs;
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
                // Return zero corrections on singular matrix
                return new double[jac.length];
            }
        }
    }
}
