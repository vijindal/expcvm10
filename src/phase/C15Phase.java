/*
 * C15 Laves phase model with two sublattices
 * Structure: MgCu2-type (C15) with A2B composition
 * Sublattice 0: A-type atoms
 * Sublattice 1: B-type atoms
 */
package phase;

import java.util.*;
import java.util.logging.Logger;

/**
 * C15 Laves phase thermodynamic model.
 * Two sublattice model with configurational entropy and excess energy.
 *
 * @author admin
 */
public class C15Phase extends GibbsModel {

    private static final Logger LOG = Logger.getLogger(C15Phase.class.getName());

    // ===== CONSTANTS =====
    private static final double EPS = 1e-14;

    // ===== STRUCTURE =====
    int nSubl = 2;
    double[] a_s = {2.0, 1.0}; // site multiplicities

    int[][] varIndex; // mapping (s,i) → index
    int nSpecSubl0;
    int nSpecSubl1;

    // ===== VARIABLES =====
    double[] y; // internal variables (site occupancies)

    // ===== PARAMETERS =====
    double[][] G0; // reference energy matrix [nSpec0][nSpec1]
    List<Interaction> interactions = new ArrayList<>();
    int[][] speciesToComp; // [sublattice][species] → component index (for composition mapping)

    // ===== CONSTRUCTOR =====
    /**
     * Create C15 phase with specified number of species on each sublattice
     * @param nSpec0 number of species on sublattice 0
     * @param nSpec1 number of species on sublattice 1
     */
    public C15Phase(int nSpec0, int nSpec1) {
        this.nSpecSubl0 = nSpec0;
        this.nSpecSubl1 = nSpec1;

        int totalVars = nSpec0 + nSpec1;
        y = new double[totalVars];

        varIndex = new int[2][];
        varIndex[0] = new int[nSpec0];
        varIndex[1] = new int[nSpec1];

        int idx = 0;
        for (int i = 0; i < nSpec0; i++) {
            varIndex[0][i] = idx++;
        }
        for (int i = 0; i < nSpec1; i++) {
            varIndex[1][i] = idx++;
        }

        G0 = new double[nSpec0][nSpec1];
    }

    // ===== SETTERS =====

    public void setInternalVars(double[] vars) {
        System.arraycopy(vars, 0, y, 0, y.length);
    }

    public double[] getInternalVars() {
        return y.clone();
    }

    public void setG0(double[][] G0) {
        this.G0 = G0;
    }

    public void addInteraction(Interaction term) {
        interactions.add(term);
    }

    public void setSpeciesToComp(int[][] speciesToComp) {
        this.speciesToComp = speciesToComp;
    }

    // =========================================================
    // 🔷 GIBBS ENERGY
    // =========================================================

    @Override
    public double calG() {
        return calcGref() + calcGconfig() + calcGexcess();
    }

    @Override
    public double calGm() {
        // Gibbs energy of mixing (relative to pure components at same T,P)
        // For now, approximate as equal to total G
        return calG();
    }

    private double calcGref() {
        double g0 = 0.0;
        for (int i = 0; i < nSpecSubl0; i++) {
            double yi = y[varIndex[0][i]];
            for (int j = 0; j < nSpecSubl1; j++) {
                double yj = y[varIndex[1][j]];
                g0 += yi * yj * G0[i][j];
            }
        }
        return g0;
    }

    private double calcGconfig() {
        double g = 0.0;
        for (int s = 0; s < nSubl; s++) {
            double a = a_s[s];
            for (int i = 0; i < varIndex[s].length; i++) {
                double yi = Math.max(y[varIndex[s][i]], EPS);
                g += a * yi * Math.log(yi);
            }
        }
        return getR() * getT() * g;
    }

    private double calcGexcess() {
        double gex = 0.0;
        for (Interaction term : interactions) {
            double prod = 1.0;
            for (int k = 0; k < term.indices.length; k++) {
                int s = term.sublattice[k];
                int i = term.indices[k];
                prod *= y[varIndex[s][i]];
            }
            gex += prod * term.getL(getT());
        }
        return gex;
    }

    // =========================================================
    // 🔷 GRADIENT: ∂G/∂y (internal variables)
    // =========================================================

    public double[] calDGy() {
        double[] grad = new double[y.length];
        addConfigGradient(grad);
        addRefGradient(grad);
        addExcessGradient(grad);
        return grad;
    }

    private void addConfigGradient(double[] grad) {
        for (int s = 0; s < nSubl; s++) {
            double a = a_s[s];
            for (int i = 0; i < varIndex[s].length; i++) {
                int idx = varIndex[s][i];
                double yi = Math.max(y[idx], EPS);
                grad[idx] += getR() * getT() * a * (1.0 + Math.log(yi));
            }
        }
    }

    private void addRefGradient(double[] grad) {
        // ∂G0/∂y: G0 = Σ yi yj G0[i][j]
        for (int i = 0; i < nSpecSubl0; i++) {
            int idx_i = varIndex[0][i];
            double sum = 0.0;
            for (int j = 0; j < nSpecSubl1; j++) {
                sum += y[varIndex[1][j]] * G0[i][j];
            }
            grad[idx_i] += sum;
        }

        for (int j = 0; j < nSpecSubl1; j++) {
            int idx_j = varIndex[1][j];
            double sum = 0.0;
            for (int i = 0; i < nSpecSubl0; i++) {
                sum += y[varIndex[0][i]] * G0[i][j];
            }
            grad[idx_j] += sum;
        }
    }

    private void addExcessGradient(double[] grad) {
        for (Interaction term : interactions) {
            double L = term.getL(getT());
            int n = term.indices.length;
            double totalProd = 1.0;
            int[] idxList = new int[n];

            for (int k = 0; k < n; k++) {
                idxList[k] = varIndex[term.sublattice[k]][term.indices[k]];
                totalProd *= y[idxList[k]];
            }

            for (int k = 0; k < n; k++) {
                int idx = idxList[k];
                double yi = Math.max(y[idx], EPS);
                grad[idx] += L * (totalProd / yi);
            }
        }
    }

    // =========================================================
    // 🔷 HESSIAN: ∂²G/∂y²
    // =========================================================

    public double[][] calDGyy() {
        int n = y.length;
        double[][] H = new double[n][n];
        addConfigHessian(H);
        addRefHessian(H);
        addExcessHessian(H);
        return H;
    }

    private void addConfigHessian(double[][] H) {
        for (int s = 0; s < nSubl; s++) {
            double a = a_s[s];
            for (int i = 0; i < varIndex[s].length; i++) {
                int idx = varIndex[s][i];
                double yi = Math.max(y[idx], EPS);
                H[idx][idx] += getR() * getT() * a / yi;
            }
        }
    }

    private void addRefHessian(double[][] H) {
        for (int i = 0; i < nSpecSubl0; i++) {
            int idx_i = varIndex[0][i];
            for (int j = 0; j < nSpecSubl1; j++) {
                int idx_j = varIndex[1][j];
                double val = G0[i][j];
                H[idx_i][idx_j] += val;
                H[idx_j][idx_i] += val;
            }
        }
    }

    private void addExcessHessian(double[][] H) {
        for (Interaction term : interactions) {
            double L = term.getL(getT());
            int n = term.indices.length;
            double totalProd = 1.0;
            int[] idxList = new int[n];

            for (int k = 0; k < n; k++) {
                idxList[k] = varIndex[term.sublattice[k]][term.indices[k]];
                totalProd *= y[idxList[k]];
            }

            for (int k = 0; k < n; k++) {
                int idx_k = idxList[k];
                double yk = Math.max(y[idx_k], EPS);

                for (int m = 0; m < n; m++) {
                    int idx_m = idxList[m];
                    double ym = Math.max(y[idx_m], EPS);

                    if (k == m) {
                        H[idx_k][idx_k] -= L * (totalProd / (yk * yk));
                    } else {
                        H[idx_k][idx_m] += L * (totalProd / (yk * ym));
                    }
                }
            }
        }
    }

    // =========================================================
    // 🔷 COMPOSITION MAPPING (y → x)
    // =========================================================

    /**
     * Map internal variables (site occupancies) to composition
     * @param nComp number of components
     * @param speciesToComp [sublattice][species] → component index
     * @return mole fractions x[i]
     */
    public double[] computeComposition(int nComp, int[][] speciesToComp) {
        double[] M = new double[nComp];

        for (int s = 0; s < nSubl; s++) {
            double a = a_s[s];
            for (int i = 0; i < varIndex[s].length; i++) {
                int idx = varIndex[s][i];
                int comp = speciesToComp[s][i];
                M[comp] += a * y[idx];
            }
        }

        double total = 0.0;
        for (double v : M) total += v;

        double[] x = new double[nComp];
        for (int i = 0; i < nComp; i++) {
            x[i] = (total > 0) ? M[i] / total : 0.0;
        }

        return x;
    }

    // =========================================================
    // 🔷 JACOBIAN: ∂x/∂y (Composition mapping gradient)
    // =========================================================

    /**
     * Compute Jacobian matrix J where J[i][j] = ∂x_i/∂y_j
     * x_i = M_i / total, where M_i = Σ_s a_s · y_{s,species_that_maps_to_i}
     * @return Jacobian matrix [nComp][nVars]
     */
    private double[][] computeJacobian() {
        if (speciesToComp == null) {
            return null; // Cannot compute without mapping
        }

        int nComp = getNumComp();
        int nVars = y.length;
        double[][] J = new double[nComp][nVars];

        // Compute M[comp] and total
        double[] M = new double[nComp];
        for (int s = 0; s < nSubl; s++) {
            double a = a_s[s];
            for (int i = 0; i < varIndex[s].length; i++) {
                int idx = varIndex[s][i];
                int comp = speciesToComp[s][i];
                M[comp] += a * y[idx];
            }
        }

        double total = 0.0;
        for (double m : M) total += m;
        total = Math.max(total, EPS);

        // Compute Jacobian: ∂x_comp/∂y_{s,i} = (a_s · δ_{species_maps_to_comp} · total - M_comp) / total²
        for (int s = 0; s < nSubl; s++) {
            double a = a_s[s];
            for (int i = 0; i < varIndex[s].length; i++) {
                int idx = varIndex[s][i];
                int comp_species = speciesToComp[s][i];

                for (int comp = 0; comp < nComp; comp++) {
                    double numerator;
                    if (comp == comp_species) {
                        // Diagonal: ∂M_comp/∂y contributes positively
                        numerator = a * total - M[comp];
                    } else {
                        // Off-diagonal: -M_comp (because ∂total/∂y > 0)
                        numerator = -M[comp];
                    }
                    J[comp][idx] = numerator / (total * total);
                }
            }
        }

        return J;
    }

    /**
     * Invert Jacobian or compute inverse using linear algebra
     * For small systems (nComp ≤ 3), use direct inversion
     */
    private double[][] invertJacobian(double[][] J) {
        int n = J.length;
        if (J[0].length < n) {
            // Underdetermined system - use pseudo-inverse or Moore-Penrose
            return pseudoInverseJacobian(J);
        }

        if (n == 2) {
            return invert2x2(J);
        } else if (n == 3) {
            return invert3x3(J);
        } else {
            // For larger systems, use Gaussian elimination
            return gaussianElimination(J, n);
        }
    }

    private double[][] invert2x2(double[][] J) {
        double det = J[0][0] * J[1][1] - J[0][1] * J[1][0];
        if (Math.abs(det) < EPS) {
            return identityMatrix(J.length);
        }
        double[][] inv = new double[2][2];
        inv[0][0] = J[1][1] / det;
        inv[0][1] = -J[0][1] / det;
        inv[1][0] = -J[1][0] / det;
        inv[1][1] = J[0][0] / det;
        return inv;
    }

    private double[][] invert3x3(double[][] J) {
        double det = J[0][0] * (J[1][1] * J[2][2] - J[1][2] * J[2][1])
                   - J[0][1] * (J[1][0] * J[2][2] - J[1][2] * J[2][0])
                   + J[0][2] * (J[1][0] * J[2][1] - J[1][1] * J[2][0]);

        if (Math.abs(det) < EPS) {
            return identityMatrix(3);
        }

        double[][] inv = new double[3][3];
        inv[0][0] = (J[1][1] * J[2][2] - J[1][2] * J[2][1]) / det;
        inv[0][1] = (J[0][2] * J[2][1] - J[0][1] * J[2][2]) / det;
        inv[0][2] = (J[0][1] * J[1][2] - J[0][2] * J[1][1]) / det;
        inv[1][0] = (J[1][2] * J[2][0] - J[1][0] * J[2][2]) / det;
        inv[1][1] = (J[0][0] * J[2][2] - J[0][2] * J[2][0]) / det;
        inv[1][2] = (J[0][2] * J[1][0] - J[0][0] * J[1][2]) / det;
        inv[2][0] = (J[1][0] * J[2][1] - J[1][1] * J[2][0]) / det;
        inv[2][1] = (J[0][1] * J[2][0] - J[0][0] * J[2][1]) / det;
        inv[2][2] = (J[0][0] * J[1][1] - J[0][1] * J[1][0]) / det;
        return inv;
    }

    private double[][] gaussianElimination(double[][] A, int n) {
        // Create augmented matrix [A | I]
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                aug[i][j] = A[i][j];
            }
            aug[i][n + i] = 1.0;
        }

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

            // Check for singularity
            if (Math.abs(aug[i][i]) < EPS) {
                return identityMatrix(n);
            }

            // Eliminate column
            for (int k = i + 1; k < n; k++) {
                double factor = aug[k][i] / aug[i][i];
                for (int j = i; j < 2 * n; j++) {
                    aug[k][j] -= factor * aug[i][j];
                }
            }
        }

        // Back substitution
        for (int i = n - 1; i >= 0; i--) {
            for (int j = n - 1; j >= i; j--) {
                if (i != j) {
                    double factor = aug[i][j] / aug[j][j];
                    for (int k = 0; k < 2 * n; k++) {
                        aug[i][k] -= factor * aug[j][k];
                    }
                }
            }
            for (int j = 0; j < 2 * n; j++) {
                aug[i][j] /= aug[i][i];
            }
        }

        // Extract inverse from right side
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                inv[i][j] = aug[i][n + j];
            }
        }
        return inv;
    }

    private double[][] pseudoInverseJacobian(double[][] J) {
        // For underdetermined systems, return identity as fallback
        int nComp = J.length;
        return identityMatrix(nComp);
    }

    private double[][] identityMatrix(int n) {
        double[][] I = new double[n][n];
        for (int i = 0; i < n; i++) {
            I[i][i] = 1.0;
        }
        return I;
    }

    /**
     * Matrix-vector product: result = A · v
     */
    private double[] matrixVectorProduct(double[][] A, double[] v) {
        int m = A.length;
        int n = A[0].length;
        double[] result = new double[m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                result[i] += A[i][j] * v[j];
            }
        }
        return result;
    }

    /**
     * Matrix-matrix product: result = A · B
     */
    private double[][] matrixProduct(double[][] A, double[][] B) {
        int m = A.length;
        int n = B[0].length;
        int k = B.length;
        double[][] result = new double[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                for (int p = 0; p < k; p++) {
                    result[i][j] += A[i][p] * B[p][j];
                }
            }
        }
        return result;
    }

    // =========================================================
    // 🔷 ABSTRACT METHODS: Temperature/Composition Derivatives
    // =========================================================

    @Override
    public double calDGT() {
        // ∂G/∂T (at constant y)
        double dgt = 0.0;

        // From configurational entropy: T * (∂Gconfig/∂T) = Gconfig
        for (int s = 0; s < nSubl; s++) {
            double a = a_s[s];
            for (int i = 0; i < varIndex[s].length; i++) {
                double yi = Math.max(y[varIndex[s][i]], EPS);
                dgt += a * yi * Math.log(yi);
            }
        }
        dgt *= getR(); // ∂(RT ln y)/∂T = R ln y

        // From excess energy
        for (Interaction term : interactions) {
            double dLdT = term.getDLdT(getT());
            double prod = 1.0;
            for (int k = 0; k < term.indices.length; k++) {
                int s = term.sublattice[k];
                int i = term.indices[k];
                prod *= y[varIndex[s][i]];
            }
            dgt += prod * dLdT;
        }

        return dgt;
    }

    @Override
    public double calDGP() {
        // ∂G/∂P (pressure dependence)
        // For C15, assume negligible pressure dependence for now
        return 0.0;
    }

    @Override
    public double[] calDGx() {
        // ∂G/∂x (at constant T, P): Chemical potentials
        // Using implicit function theorem with constraints on sublattice occupancies
        // For a two-sublattice system with constraints (y_0V + y_0Zr = 1, y_1V + y_1Zr = 1),
        // we have only 2 independent variables. Extract columns for independent variables
        // and compute: ∂G/∂x = inv(J_indep)^T · ∂G/∂y_indep

        if (speciesToComp == null) {
            int nComp = getNumComp();
            LOG.warning("speciesToComp not set - returning zero chemical potentials");
            return new double[nComp];
        }

        double[][] J = computeJacobian();
        if (J == null) {
            int nComp = getNumComp();
            return new double[nComp];
        }

        // Compute ∂G/∂y in internal variable space
        double[] dGdy = calDGy();

        // Extract Jacobian columns for independent variables
        // For C15 (2 sublattices): independent vars are y[0] (subL0 species 0) and y[2] (subL1 species 0)
        // Dependent vars: y[1] = 1 - y[0], y[3] = 1 - y[2]
        int nComp = getNumComp();
        double[][] J_indep = new double[nComp][2];
        double[] dGdy_indep = new double[2];

        if (nSubl == 2 && varIndex[0].length == 2 && varIndex[1].length == 2) {
            // Standard case: 2 sublattices, 2 species each
            // Independent vars: [y_0V, y_1V]
            J_indep[0][0] = J[0][varIndex[0][0]];  // ∂x_V/∂y_0V
            J_indep[0][1] = J[0][varIndex[1][0]];  // ∂x_V/∂y_1V
            J_indep[1][0] = J[1][varIndex[0][0]];  // ∂x_Zr/∂y_0V
            J_indep[1][1] = J[1][varIndex[1][0]];  // ∂x_Zr/∂y_1V

            dGdy_indep[0] = dGdy[varIndex[0][0]];  // ∂G/∂y_0V
            dGdy_indep[1] = dGdy[varIndex[1][0]];  // ∂G/∂y_1V
        } else {
            // Fallback: use pseudo-inverse
            return computeChemicalPotentialsPseudoInverse(J, dGdy);
        }

        // Invert 2×2 Jacobian
        double[][] Jinv = invert2x2(J_indep);

        // Multiply: ∂G/∂x = Jinv^T · ∂G/∂y_indep
        double[] dGdx = new double[nComp];
        for (int i = 0; i < nComp; i++) {
            for (int j = 0; j < 2; j++) {
                dGdx[i] += Jinv[j][i] * dGdy_indep[j];
            }
        }

        return dGdx;
    }

    private double[] computeChemicalPotentialsPseudoInverse(double[][] J, double[] dGdy) {
        // Fallback: compute using pseudo-inverse J^+ = J^T · (J·J^T)^{-1}
        int nComp = J.length;
        int nVars = J[0].length;

        // Compute J·J^T (nComp × nComp)
        double[][] JJT = new double[nComp][nComp];
        for (int i = 0; i < nComp; i++) {
            for (int j = 0; j < nComp; j++) {
                for (int k = 0; k < nVars; k++) {
                    JJT[i][j] += J[i][k] * J[j][k];
                }
            }
        }

        // Invert (J·J^T)
        double[][] JJT_inv;
        if (nComp == 2) {
            JJT_inv = invert2x2(JJT);
        } else if (nComp == 3) {
            JJT_inv = invert3x3(JJT);
        } else {
            JJT_inv = gaussianElimination(JJT, nComp);
        }

        // Compute J^+ = J^T · (J·J^T)^{-1}
        double[][] Jplus = new double[nVars][nComp];
        for (int i = 0; i < nVars; i++) {
            for (int j = 0; j < nComp; j++) {
                for (int k = 0; k < nComp; k++) {
                    Jplus[i][j] += J[k][i] * JJT_inv[k][j];
                }
            }
        }

        // Compute dGdx = Jplus · dGdy
        double[] dGdx = new double[nComp];
        for (int i = 0; i < nComp; i++) {
            for (int j = 0; j < nVars; j++) {
                dGdx[i] += Jplus[j][i] * dGdy[j];
            }
        }

        return dGdx;
    }

    @Override
    public double[] calDGTx() {
        // ∂²G/∂T∂x using independent variables (same approach as calDGx)
        if (speciesToComp == null) {
            int nComp = getNumComp();
            return new double[nComp];
        }

        double[][] J = computeJacobian();
        if (J == null) {
            int nComp = getNumComp();
            return new double[nComp];
        }

        // Compute ∂²G/∂T∂y analytically
        double[] dGTdy = computeD2GTy();

        int nComp = getNumComp();

        // Extract Jacobian columns for independent variables
        if (nSubl == 2 && varIndex[0].length == 2 && varIndex[1].length == 2) {
            double[][] J_indep = new double[nComp][2];
            double[] dGTdy_indep = new double[2];

            J_indep[0][0] = J[0][varIndex[0][0]];
            J_indep[0][1] = J[0][varIndex[1][0]];
            J_indep[1][0] = J[1][varIndex[0][0]];
            J_indep[1][1] = J[1][varIndex[1][0]];

            dGTdy_indep[0] = dGTdy[varIndex[0][0]];
            dGTdy_indep[1] = dGTdy[varIndex[1][0]];

            // Invert 2×2 Jacobian
            double[][] Jinv = invert2x2(J_indep);

            // Multiply: ∂²G/∂T∂x = Jinv^T · ∂²G/∂T∂y
            double[] d2GTx = new double[nComp];
            for (int i = 0; i < nComp; i++) {
                for (int j = 0; j < 2; j++) {
                    d2GTx[i] += Jinv[j][i] * dGTdy_indep[j];
                }
            }
            return d2GTx;
        }

        return new double[nComp];
    }

    private double[] computeD2GTy() {
        // ∂²G/∂T∂y: derivative of ∂G/∂y with respect to T
        double[] d2GTy = new double[y.length];

        // Configuration part: ∂/∂T(RT·a·(1+ln y)) = R·a·(1+ln y)
        for (int s = 0; s < nSubl; s++) {
            double a = a_s[s];
            for (int i = 0; i < varIndex[s].length; i++) {
                int idx = varIndex[s][i];
                double yi = Math.max(y[idx], EPS);
                d2GTy[idx] += getR() * a * (1.0 + Math.log(yi));
            }
        }

        // Excess part: ∂/∂T(Σ prod · L(T)) = Σ prod · dL/dT
        for (Interaction term : interactions) {
            double dLdT = term.getDLdT(getT());
            int n = term.indices.length;
            double totalProd = 1.0;
            int[] idxList = new int[n];

            for (int k = 0; k < n; k++) {
                idxList[k] = varIndex[term.sublattice[k]][term.indices[k]];
                totalProd *= y[idxList[k]];
            }

            for (int k = 0; k < n; k++) {
                int idx = idxList[k];
                double yi = Math.max(y[idx], EPS);
                d2GTy[idx] += dLdT * (totalProd / yi);
            }
        }

        return d2GTy;
    }

    @Override
    public double[] calDGPx() {
        // ∂²G/∂P∂x: For C15, pressure dependence is negligible
        int nComp = getNumComp();
        return new double[nComp];
    }

    @Override
    public double[][] calDGxx() {
        // ∂²G/∂x²: Hessian in composition space using independent variables
        // ∂²G/∂x² = inv(J_indep)^T · ∂²G/∂y_indep² · inv(J_indep)
        if (speciesToComp == null) {
            int nComp = getNumComp();
            return new double[nComp][nComp];
        }

        double[][] J = computeJacobian();
        if (J == null) {
            int nComp = getNumComp();
            return new double[nComp][nComp];
        }

        // Compute ∂²G/∂y² in internal variable space
        double[][] d2Gyy = calDGyy();

        int nComp = getNumComp();

        // Extract Jacobian columns for independent variables
        if (nSubl == 2 && varIndex[0].length == 2 && varIndex[1].length == 2) {
            double[][] J_indep = new double[nComp][2];
            J_indep[0][0] = J[0][varIndex[0][0]];
            J_indep[0][1] = J[0][varIndex[1][0]];
            J_indep[1][0] = J[1][varIndex[0][0]];
            J_indep[1][1] = J[1][varIndex[1][0]];

            // Extract submatrix of ∂²G/∂y² for independent variables
            double[][] d2Gyy_indep = new double[2][2];
            int idx_0V = varIndex[0][0];
            int idx_1V = varIndex[1][0];
            d2Gyy_indep[0][0] = d2Gyy[idx_0V][idx_0V];
            d2Gyy_indep[0][1] = d2Gyy[idx_0V][idx_1V];
            d2Gyy_indep[1][0] = d2Gyy[idx_1V][idx_0V];
            d2Gyy_indep[1][1] = d2Gyy[idx_1V][idx_1V];

            // Invert 2×2 Jacobian
            double[][] Jinv = invert2x2(J_indep);

            // Compute: Jinv^T · ∂²G/∂y_indep²
            double[][] temp = new double[2][2];
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    for (int k = 0; k < 2; k++) {
                        temp[i][j] += Jinv[k][i] * d2Gyy_indep[k][j];
                    }
                }
            }

            // Compute: (Jinv^T · ∂²G/∂y²) · Jinv
            double[][] result = new double[nComp][nComp];
            for (int i = 0; i < nComp; i++) {
                for (int j = 0; j < nComp; j++) {
                    for (int k = 0; k < 2; k++) {
                        result[i][j] += temp[i][k] * Jinv[k][j];
                    }
                }
            }

            return result;
        }

        return new double[nComp][nComp];
    }

    @Override
    public ArrayList<Double> getInitlIntVarValues(ArrayList<Double> x) {
        // Initialize internal variables from composition
        ArrayList<Double> vars = new ArrayList<>();
        int nComp = x.size();

        // Simple initialization: assume equal distribution on each sublattice
        for (int i = 0; i < nSpecSubl0; i++) {
            vars.add(1.0 / nSpecSubl0);
        }
        for (int j = 0; j < nSpecSubl1; j++) {
            vars.add(1.0 / nSpecSubl1);
        }

        return vars;
    }

    @Override
    public void calGderivatives() {
        // Compute all derivatives
        // This method is called by the system to update all G and derivatives
        LOG.fine("C15Phase.calGderivatives() called");
    }

    @Override
    public void printPhaseInfo() {
        LOG.info("=== C15Phase ===");
        LOG.info("Sublattice 0: " + nSpecSubl0 + " species, multiplicity " + a_s[0]);
        LOG.info("Sublattice 1: " + nSpecSubl1 + " species, multiplicity " + a_s[1]);
        LOG.info("Internal variables (y): " + Arrays.toString(y));
        LOG.info("Interactions: " + interactions.size());
        LOG.info("G = " + calG());
    }

    // =========================================================
    // 🔷 INTERACTION TERM: Excess energy
    // =========================================================

    /**
     * Represents an interaction term in excess energy
     * Gexcess += coeff * product(y[i]) * (a + b*T)
     */
    public static class Interaction {
        public final int[] sublattice; // which sublattice each species is on
        public final int[] indices;    // which species on that sublattice
        public final double a;         // linear coefficient
        public final double b;         // temperature coefficient

        /**
         * Create interaction term
         * @param sublattice array of sublattice indices
         * @param indices array of species indices on each sublattice
         * @param a constant part of L parameter
         * @param b temperature coefficient of L parameter
         */
        public Interaction(int[] sublattice, int[] indices, double a, double b) {
            this.sublattice = sublattice;
            this.indices = indices;
            this.a = a;
            this.b = b;
        }

        /**
         * Get L parameter at temperature T: L(T) = a + b*T
         */
        public double getL(double T) {
            return a + b * T;
        }

        /**
         * Get temperature derivative: dL/dT = b
         */
        public double getDLdT(double T) {
            return b;
        }
    }
}
