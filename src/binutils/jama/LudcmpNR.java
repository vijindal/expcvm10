/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package binutils.jama;

/**
 * Given a matrix a[0..n-1][0..n-1], this routine replaces it by the LU
 * decomposition of a row wise permutation of itself. a is input. On output, it
 * is arranged as in equation (2.3.14) above; indx[0..n-1] is an output vector
 * that records the row permutation effected by the partial pivoting; d is
 * output as ÿ1 depending on whether the number of row interchanges was even or
 * odd, respectively. This routine is used in combination with solve to solve
 * linear equations or invert a matrix.
 */
public class LudcmpNR {

    int n;
    double[][] lu;
    int[] indx;
    double d;
    double[][] aref;

    LudcmpNR(double[][] a) {
        n = a.length;
        lu = a;
        aref = a;
        indx = new int[n];
        double TINY = 1.0e-40;
        int i, imax, j, k;
        double big, temp;
        double[] vv = new double[n];
        d = 1.0;
        for (i = 0; i < n; i++) {
            big = 0.0;
            for (j = 0; j < n; j++) {
                if ((temp = Math.abs(lu[i][j])) > big) {
                    big = temp;
                }
            }
            if (big == 0.0) {
                System.out.println("Singular matrix in LUdcmp");
            }
            vv[i] = 1.0 / big;
        }
        for (k = 0; k < n; k++) {
            big = 0.0;
            imax = k;
            for (i = k; i < n; i++) {
                temp = vv[i] * Math.abs(lu[i][k]);
                if (temp > big) {
                    big = temp;
                    imax = i;
                }
            }
            if (k != imax) {
                for (j = 0; j < n; j++) {
                    temp = lu[imax][j];
                    lu[imax][j] = lu[k][j];
                    lu[k][j] = temp;
                }
                d = -d;
                vv[imax] = vv[k];
            }
            indx[k] = imax;
            if (lu[k][k] == 0.0) {
                lu[k][k] = TINY;
            }
            for (i = k + 1; i < n; i++) {
                temp = lu[i][k] /= lu[k][k];
                for (j = k + 1; j < n; j++) {
                    lu[i][j] -= temp * lu[k][j];
                }
            }
        }
    }

    public void solve(double[] b, double[] x) {
        int i, ii = 0, ip, j;
        double sum;
        if (b.length != n || x.length != n) {
            System.out.println("LUdcmp::solve bad sizes");
        }
        for (i = 0; i < n; i++) {
            x[i] = b[i];
        }
        for (i = 0; i < n; i++) {
            ip = indx[i];
            sum = x[ip];
            x[ip] = x[i];
            if (ii != 0) {
                for (j = ii - 1; j < i; j++) {
                    sum -= lu[i][j] * x[j];
                }
            } else if (sum != 0.0) {
                ii = i + 1;
            }
            x[i] = sum;
        }
        for (i = n - 1; i >= 0; i--) {
            sum = x[i];
            for (j = i + 1; j < n; j++) {
                sum -= lu[i][j] * x[j];
            }
            x[i] = sum / lu[i][i];
        }
    }

    public void solve(double[][] b, double[][] x) {
        int i, j, m = b[0].length;
        if (b.length != n || x.length != n || b[0].length != x[0].length) {
            System.out.println("LUdcmp::solve bad sizes");
        }
        double[] xx = new double[n];
        for (j = 0; j < m; j++) {
            for (i = 0; i < n; i++) {
                xx[i] = b[i][j];
            }
            solve(xx, xx);
            for (i = 0; i < n; i++) {
                x[i][j] = xx[i];
            }
        }
    }

    private void inverse(double[][] ainv) {
        int i, j;
        ainv = new double[n][n];
        for (i = 0; i < n; i++) {
            for (j = 0; j < n; j++) {
                ainv[i][j] = 0.;
            }
            ainv[i][i] = 1.;
        }
        solve(ainv, ainv);
    }

    public double det() {
        double dd = d;
        for (int i = 0; i < n; i++) {
            dd *= lu[i][i];
        }
        return dd;
    }

    public void mprove(double[] b, double[] x) {
        int i, j;
        double[] r = new double[n];
        for (i = 0; i < n; i++) {
            long sdp = (long) -b[i];
            for (j = 0; j < n; j++) {
                sdp += (long) aref[i][j] * (long) x[j];
            }
            r[i] = sdp;
        }
        solve(r, r);
        for (i = 0; i < n; i++) {
            x[i] -= r[i];
        }
    }
};