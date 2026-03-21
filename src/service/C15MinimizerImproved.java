package service;

import phase.C15Phase;
import java.util.logging.Logger;

/**
 * Improved internal minimization for C15 phase using augmented Lagrangian method.
 *
 * Solves the constrained optimization problem:
 *   min G(y) subject to:
 *     - c_s(y) = sum(y_{s,i}) - 1 = 0  for each sublattice s
 *     - x_j(y) = target_x_j              for each component j
 *
 * Uses augmented Lagrangian (penalty method) combined with Newton's method.
 */
public class C15MinimizerImproved {

    private static final Logger LOG = Logger.getLogger(C15MinimizerImproved.class.getName());

    private static final double TOLERANCE = 1e-7;
    private static final int MAX_ITERATIONS = 200;
    private static final double PENALTY_SCALE = 100.0;

    /**
     * Minimize Gibbs energy with composition constraints
     */
    public static double[] minimizeGibbs(C15Phase c15, double[] target_composition,
                                         int nComp, int[][] speciesToComp) {
        LOG.info("Starting constrained minimization for C15 phase");
        LOG.info("Target: " + arrayToString(target_composition));

        double[] y = c15.getInternalVars();
        if (y == null || y.length == 0) {
            y = initializeUniform(c15, speciesToComp);
        }

        int nVars = y.length;
        int nSubl = speciesToComp.length;

        // Lagrange multipliers for sublattice constraints
        double[] lambda_subL = new double[nSubl];

        // Penalty parameter for composition constraints
        double mu = 1.0;

        double G_best = c15.calG();
        double[] y_best = y.clone();

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // Set current y
            c15.setInternalVars(y);

            // Compute current composition
            double[] x_current = c15.computeComposition(nComp, speciesToComp);

            // Compute constraint violations
            double[] constr_subL = new double[nSubl];
            for (int s = 0; s < nSubl; s++) {
                for (int i = 0; i < speciesToComp[s].length; i++) {
                    constr_subL[s] += y[getVarIndex(speciesToComp, s, i)];
                }
                constr_subL[s] -= 1.0;
            }

            // Composition constraint violation
            double constr_comp_norm = 0;
            for (int j = 0; j < nComp; j++) {
                double err = x_current[j] - target_composition[j];
                constr_comp_norm += err * err;
            }
            constr_comp_norm = Math.sqrt(constr_comp_norm);

            // Sublattice constraint violation
            double constr_subL_norm = norm(constr_subL);

            if (iter % 20 == 0) {
                LOG.fine(String.format("Iter %d: G=%.0f, ||c_subL||=%.2e, ||c_x||=%.2e, mu=%.2e",
                    iter, c15.calG(), constr_subL_norm, constr_comp_norm, mu));
            }

            // Check convergence
            if (constr_subL_norm < TOLERANCE && constr_comp_norm < TOLERANCE) {
                LOG.info("Converged at iteration " + iter);
                c15.setInternalVars(y);
                return y;
            }

            // Compute gradient of Lagrangian
            double[] grad_G = c15.calDGy();
            double[] grad_L = grad_G.clone();

            // Add Lagrange multiplier terms for sublattice constraints
            for (int s = 0; s < nSubl; s++) {
                for (int i = 0; i < speciesToComp[s].length; i++) {
                    int idx = getVarIndex(speciesToComp, s, i);
                    grad_L[idx] += lambda_subL[s];
                }
            }

            // Add penalty terms for composition constraints
            double[][] J_comp = computeCompositionJacobian(c15, speciesToComp, y);
            for (int j = 0; j < nComp; j++) {
                double err = x_current[j] - target_composition[j];
                for (int k = 0; k < nVars; k++) {
                    grad_L[k] += mu * err * J_comp[j][k];
                }
            }

            // Check if gradient is small
            double grad_norm = norm(grad_L);
            if (grad_norm < TOLERANCE / 10) {
                LOG.fine("Gradient very small: " + grad_norm);
            }

            // Compute Hessian of Lagrangian (approximation: just use G's Hessian)
            double[][] H = c15.calDGyy();

            // Add penalty matrix for composition: mu * J^T * J
            for (int i = 0; i < nVars; i++) {
                for (int j = 0; j < nVars; j++) {
                    for (int k = 0; k < nComp; k++) {
                        H[i][j] += mu * J_comp[k][i] * J_comp[k][j];
                    }
                }
            }

            // Regularize Hessian
            for (int i = 0; i < nVars; i++) {
                H[i][i] += 1e-6;
            }

            // Solve H * delta_y = -grad_L
            double[] delta_y = solveSystem(H, grad_L);

            // Line search
            double alpha = 1.0;
            double[] y_trial = addVectors(y, scaleVector(delta_y, alpha));

            // Enforce bounds: 0 <= y <= 1
            for (int i = 0; i < nVars; i++) {
                y_trial[i] = Math.max(0.0, Math.min(1.0, y_trial[i]));
            }

            // Normalize sublattices
            for (int s = 0; s < nSubl; s++) {
                double sum = 0;
                int[] indices = new int[speciesToComp[s].length];
                for (int i = 0; i < speciesToComp[s].length; i++) {
                    indices[i] = getVarIndex(speciesToComp, s, i);
                    sum += y_trial[indices[i]];
                }
                if (sum > 0) {
                    for (int i = 0; i < speciesToComp[s].length; i++) {
                        y_trial[indices[i]] /= sum;
                    }
                }
            }

            c15.setInternalVars(y_trial);
            double G_trial = c15.calG();

            if (G_trial < G_best) {
                y_best = y_trial.clone();
                G_best = G_trial;
                y = y_trial;
            } else {
                // Reduce step size with line search
                for (int ls = 0; ls < 5; ls++) {
                    alpha *= 0.5;
                    y_trial = addVectors(y, scaleVector(delta_y, alpha));
                    for (int i = 0; i < nVars; i++) {
                        y_trial[i] = Math.max(0.0, Math.min(1.0, y_trial[i]));
                    }
                    for (int s = 0; s < nSubl; s++) {
                        double sum = 0;
                        int[] indices = new int[speciesToComp[s].length];
                        for (int i = 0; i < speciesToComp[s].length; i++) {
                            indices[i] = getVarIndex(speciesToComp, s, i);
                            sum += y_trial[indices[i]];
                        }
                        if (sum > 0) {
                            for (int i = 0; i < speciesToComp[s].length; i++) {
                                y_trial[indices[i]] /= sum;
                            }
                        }
                    }
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

            // Update Lagrange multipliers
            for (int s = 0; s < nSubl; s++) {
                lambda_subL[s] += mu * constr_subL[s];
            }

            // Update penalty parameter
            if (constr_subL_norm > 0.1 || constr_comp_norm > 0.1) {
                mu *= PENALTY_SCALE;
                mu = Math.min(mu, 1e8);  // Cap penalty parameter
            }
        }

        LOG.warning("Did not converge after " + MAX_ITERATIONS + " iterations");
        LOG.warning("Final ||c_subL|| and ||c_x|| may be non-zero");
        c15.setInternalVars(y_best);
        return y_best;
    }

    private static double[][] computeCompositionJacobian(C15Phase c15, int[][] speciesToComp, double[] y) {
        int nComp = c15.getNumComp();
        int nVars = y.length;
        double[][] J = new double[nComp][nVars];

        // This is a simplified Jacobian - assumes we can compute ∂x/∂y
        // For C15: x_comp = M_comp / total where M_comp = Σ_s a_s * y_{s,i}(comp)
        // ∂x_comp/∂y_{s,j} = (a_s * δ_{j→comp} * total - M_comp) / total²

        // We'll use numerical differentiation as approximation
        double eps = 1e-8;
        for (int k = 0; k < nVars; k++) {
            double[] y_plus = y.clone();
            y_plus[k] += eps;

            // Normalize sublattice
            int nSubl = speciesToComp.length;
            for (int s = 0; s < nSubl; s++) {
                double sum = 0;
                for (int i = 0; i < speciesToComp[s].length; i++) {
                    sum += y_plus[getVarIndex(speciesToComp, s, i)];
                }
                if (sum > 0) {
                    for (int i = 0; i < speciesToComp[s].length; i++) {
                        y_plus[getVarIndex(speciesToComp, s, i)] /= sum;
                    }
                }
            }

            c15.setInternalVars(y_plus);
            double[] x_plus = c15.computeComposition(nComp, speciesToComp);

            c15.setInternalVars(y);
            double[] x = c15.computeComposition(nComp, speciesToComp);

            for (int j = 0; j < nComp; j++) {
                J[j][k] = (x_plus[j] - x[j]) / eps;
            }
        }

        return J;
    }

    private static int getVarIndex(int[][] speciesToComp, int sublattice, int species) {
        int idx = 0;
        for (int s = 0; s < sublattice; s++) {
            idx += speciesToComp[s].length;
        }
        idx += species;
        return idx;
    }

    private static double[] initializeUniform(C15Phase c15, int[][] speciesToComp) {
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
                y[idx++] = 1.0 / nSpec;
            }
        }
        return y;
    }

    private static double[] solveSystem(double[][] A, double[] b) {
        int n = b.length;
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                aug[i][j] = A[i][j];
            }
            aug[i][n] = b[i];
        }

        // Gaussian elimination
        for (int i = 0; i < n; i++) {
            int maxRow = i;
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(aug[k][i]) > Math.abs(aug[maxRow][i])) {
                    maxRow = k;
                }
            }
            double[] temp = aug[i];
            aug[i] = aug[maxRow];
            aug[maxRow] = temp;

            for (int k = i + 1; k < n; k++) {
                if (Math.abs(aug[i][i]) > 1e-14) {
                    double factor = aug[k][i] / aug[i][i];
                    for (int j = i; j <= n; j++) {
                        aug[k][j] -= factor * aug[i][j];
                    }
                }
            }
        }

        double[] x = new double[n];
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
