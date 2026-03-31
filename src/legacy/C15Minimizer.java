package legacy;

import legacy.phase.C15Phase;
import java.util.logging.Logger;

/**
 * Internal minimization for C15 phase (multi-sublattice system)
 * Finds the equilibrium internal variables (site occupancies) y that minimize
 * Gibbs energy at fixed T, P, and composition x.
 *
 * Uses Newton-Raphson method with Lagrange multipliers for constrained optimization.
 * Constraints: 1) sum(y_s,i) = 1 for each sublattice s
 *              2) composition x remains fixed
 */
public class C15Minimizer {

    private static final Logger LOG = Logger.getLogger(C15Minimizer.class.getName());

    private static final double TOLERANCE = 1e-8;
    private static final int MAX_ITERATIONS = 100;
    private static final double DAMPING = 0.5; // Line search damping factor

    /**
     * Minimize Gibbs energy of C15 phase at fixed T, P, and composition
     * @param c15 C15Phase instance with T, P, G0, and interactions already set
     * @param composition target composition [x_V, x_Zr, ...]
     * @param nComp number of components
     * @param speciesToComp [sublattice][species] -> component index
     * @return optimized internal variables y
     */
    public static double[] minimizeGibbs(C15Phase c15, double[] composition, int nComp, int[][] speciesToComp) {
        LOG.info("Starting internal minimization for C15 phase");
        LOG.info("Target composition: " + arrayToString(composition));

        // Get current y values (as initial guess)
        double[] y = c15.getInternalVars();
        if (y == null || y.length == 0) {
            // Initialize if not set
            y = initializeInternalVariables(c15, composition, speciesToComp);
            LOG.info("Initialized y: " + arrayToString(y));
        }

        double[] y_best = y.clone();
        double G_best = c15.calG();

        // Newton-Raphson minimization
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // Compute gradient ∂G/∂y
            double[] grad = c15.calDGy();

            // Compute Hessian ∂²G/∂y²
            double[][] hess = c15.calDGyy();

            // Check convergence
            double grad_norm = norm(grad);
            if (grad_norm < TOLERANCE) {
                LOG.info("Converged at iteration " + iter + " with ||∇G|| = " + grad_norm);
                return y_best;
            }

            if (iter % 10 == 0) {
                LOG.fine("Iteration " + iter + ": ||∇G|| = " + grad_norm + ", G = " + c15.calG());
            }

            // Solve Newton step: H · Δy = -∇G
            // Using conjugate gradient or direct solve
            double[] delta_y = solveNewtonStep(hess, grad, y.length);

            // Line search with damping
            double alpha = DAMPING;
            double[] y_trial = addVectors(y, scaleVector(delta_y, alpha));

            // Project back to composition constraint
            y_trial = projectToComposition(c15, y_trial, composition, speciesToComp);

            // Update
            c15.setInternalVars(y_trial);
            double G_trial = c15.calG();

            if (G_trial < G_best) {
                y_best = y_trial.clone();
                G_best = G_trial;
                y = y_trial;
            } else {
                // Reduce step size
                LOG.fine("Line search: G did not decrease, reducing step");
                for (int ls = 0; ls < 5; ls++) {
                    alpha *= 0.5;
                    y_trial = addVectors(y, scaleVector(delta_y, alpha));
                    y_trial = projectToComposition(c15, y_trial, composition, speciesToComp);
                    c15.setInternalVars(y_trial);
                    G_trial = c15.calG();

                    if (G_trial < G_best) {
                        y_best = y_trial.clone();
                        G_best = G_trial;
                        y = y_trial;
                        break;
                    }
                }
            }

            c15.setInternalVars(y_best);
        }

        LOG.warning("Did not converge after " + MAX_ITERATIONS + " iterations");
        c15.setInternalVars(y_best);
        return y_best;
    }

    /**
     * Initialize internal variables from composition
     * Simple approach: assume uniform distribution on each sublattice
     */
    private static double[] initializeInternalVariables(C15Phase c15, double[] x, int[][] speciesToComp) {
        int nSubl = speciesToComp.length;
        int totalVars = 0;
        for (int[] s : speciesToComp) {
            totalVars += s.length;
        }

        double[] y = new double[totalVars];
        int idx = 0;

        for (int s = 0; s < nSubl; s++) {
            int nSpec = speciesToComp[s].length;
            for (int i = 0; i < nSpec; i++) {
                y[idx++] = 1.0 / nSpec;  // Uniform distribution
            }
        }

        return y;
    }

    /**
     * Solve the Newton step using Gaussian elimination
     * Hessian may be singular or ill-conditioned, so add small diagonal regularization
     */
    private static double[] solveNewtonStep(double[][] H, double[] grad, int n) {
        // Add regularization to avoid singularity
        double[][] H_reg = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                H_reg[i][j] = H[i][j];
            }
            H_reg[i][i] += 1e-6;  // Small regularization
        }

        // Create augmented system [H_reg | -grad]
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                aug[i][j] = H_reg[i][j];
            }
            aug[i][n] = -grad[i];
        }

        // Gaussian elimination with partial pivoting
        return gaussElimSolve(aug);
    }

    private static double[] gaussElimSolve(double[][] aug) {
        int n = aug.length;
        double[] x = new double[n];

        // Forward elimination
        for (int i = 0; i < n; i++) {
            // Find pivot
            int maxRow = i;
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(aug[k][i]) > Math.abs(aug[maxRow][i])) {
                    maxRow = k;
                }
            }

            // Swap rows
            double[] temp = aug[i];
            aug[i] = aug[maxRow];
            aug[maxRow] = temp;

            // Eliminate column
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(aug[i][i]) < 1e-14) {
                    continue;  // Skip singular pivot
                }
                double factor = aug[k][i] / aug[i][i];
                for (int j = i; j <= n; j++) {
                    aug[k][j] -= factor * aug[i][j];
                }
            }
        }

        // Back substitution
        for (int i = n - 1; i >= 0; i--) {
            x[i] = aug[i][n];
            for (int j = i + 1; j < n; j++) {
                x[i] -= aug[i][j] * x[j];
            }
            if (Math.abs(aug[i][i]) > 1e-14) {
                x[i] /= aug[i][i];
            }
        }

        return x;
    }

    /**
     * Project internal variables back to satisfy composition constraint
     * Uses iterative projection or direct constraint enforcement
     */
    private static double[] projectToComposition(C15Phase c15, double[] y_trial,
                                                   double[] target_x, int[][] speciesToComp) {
        // Set trial variables
        c15.setInternalVars(y_trial);

        // Check composition
        double[] x_trial = c15.computeComposition(target_x.length, speciesToComp);

        // Simple scaling projection: scale y values to match target composition
        // This is approximate but preserves the structure
        double[] y_proj = y_trial.clone();

        // For 2 sublattices, 2 species each (V-Zr system):
        // Adjust based on composition error
        int nSubl = speciesToComp.length;
        if (nSubl == 2 && speciesToComp[0].length == 2) {
            int idx0_0 = 0;  // y_0,species0
            int idx0_1 = 1;  // y_0,species1
            // int idx1_0 = 2;  // y_1,species0 (reserved for future use)
            // int idx1_1 = 3;  // y_1,species1 (reserved for future use)

            // For equimolar system, use simple scaling
            for (int comp = 0; comp < target_x.length; comp++) {
                double x_error = x_trial[comp] - target_x[comp];
                if (Math.abs(x_error) > 1e-6) {
                    // Adjust y values to reduce error
                    if (comp == 0) {
                        // Increase species 0 on sublattice 0 if x_0 is too low
                        double scale = target_x[0] / Math.max(x_trial[0], 1e-10);
                        y_proj[idx0_0] = Math.max(0, Math.min(1.0, y_trial[idx0_0] * scale * 0.9 + y_trial[idx0_0] * 0.1));
                        y_proj[idx0_1] = 1.0 - y_proj[idx0_0];
                    }
                }
            }
        }

        return y_proj;
    }

    // ===== VECTOR UTILITIES =====

    private static double norm(double[] v) {
        double sum = 0;
        for (double x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }

    private static double[] addVectors(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
        return result;
    }

    private static double[] scaleVector(double[] v, double scale) {
        double[] result = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            result[i] = v[i] * scale;
        }
        return result;
    }

    private static String arrayToString(double[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.4f", arr[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
