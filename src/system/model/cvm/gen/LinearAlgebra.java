package system.model.cvm.gen;

/**
 * Minimal linear algebra utilities for the CVM model-generation layer:
 * matrix inversion and multiplication, needed for the Inden (1992) R-matrix
 * (Stage 2/3) and the CVCF basis transform (Stage 4).
 *
 * <p>Trimmed port of CEWorkbench's {@code org.ce.model.cluster.LinearAlgebra}
 * -- only {@link #invert} and {@link #multiply} are needed here; that
 * class's iterative-refinement linear solver exists to support its Newton
 * solver ({@code HillertSolver}), which is explicitly out of scope for this
 * generation-only layer.</p>
 */
public final class LinearAlgebra {

    private LinearAlgebra() { /* utility class */ }

    /**
     * Computes the inverse of a square matrix using Gaussian elimination
     * with partial pivoting.
     */
    public static double[][] invert(double[][] A) {
        int n = A.length;
        if (n == 0) throw new IllegalArgumentException("Empty matrix");
        if (A[0].length != n) throw new IllegalArgumentException("Matrix must be square");

        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n + i] = 1.0;
        }

        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > Math.abs(aug[pivot][col])) pivot = row;
            }
            if (Math.abs(aug[pivot][col]) < 1e-18) {
                throw new IllegalArgumentException("Matrix is singular or near-singular at column " + col);
            }
            double[] tmp = aug[col];
            aug[col] = aug[pivot];
            aug[pivot] = tmp;

            double scale = aug[col][col];
            for (int j = 0; j < 2 * n; j++) aug[col][j] /= scale;

            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double f = aug[row][col];
                for (int j = 0; j < 2 * n; j++) {
                    aug[row][j] -= f * aug[col][j];
                }
            }
        }

        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(aug[i], n, inv[i], 0, n);
        }
        return inv;
    }

    /** Computes the matrix product C = A . B. */
    public static double[][] multiply(double[][] A, double[][] B) {
        int m = A.length;
        int n = A[0].length;
        int p = B[0].length;
        if (B.length != n) {
            throw new IllegalArgumentException("Matrix dimension mismatch: " + n + " != " + B.length);
        }
        double[][] C = new double[m][p];
        for (int i = 0; i < m; i++) {
            for (int k = 0; k < n; k++) {
                double aik = A[i][k];
                if (aik == 0.0) continue;
                for (int j = 0; j < p; j++) {
                    C[i][j] += aik * B[k][j];
                }
            }
        }
        return C;
    }
}
