/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils.jama;

/**
 *
 * @author metallurgy
 */
public class Cholesky {

    int n;
    double[][] el;

    Cholesky(double[][] a) {
        n = a.length;
        el = a;
        int i, j, k;

        double sum;
        if (el[0].length != n) {
            System.out.println("need square matrix");
        }
        for (i = 0; i < n; i++) {
            for (j = i; j < n; j++) {
                for (sum = el[i][j], k = i - 1; k >= 0; k--) {
                    sum -= el[i][k] * el[j][k];
                }
                if (i == j) {
                    if (sum <= 0.0) {
                        System.out.println("Cholesky failed");
                    }
                    el[i][i] = Math.sqrt(sum);
                } else {
                    el[j][i] = sum / el[i][i];
                }
            }
        }
        for (i = 0; i < n; i++) {
            for (j = 0; j < i; j++) {
                el[j][i] = 0.;
            }
        }
    }

    void solve(double[] b, double[] x) {
        int i, k;
        double sum;
        if (b.length != n || x.length != n) {
            System.out.println("bad lengths in Cholesky");
        }
        for (i = 0; i < n; i++) {
            for (sum = b[i], k = i - 1; k >= 0; k--) {
                sum -= el[i][k] * x[k];
            }
            x[i] = sum / el[i][i];
        }
        for (i = n - 1; i >= 0; i--) {
            for (sum = x[i], k = i + 1; k < n; k++) {
                sum -= el[k][i] * x[k];
            }
            x[i] = sum / el[i][i];
        }
    }

    void elmult(double[] y, double[] b) {
        int i, j;
        if (b.length != n || y.length != n) {
            System.out.println("bad lengths");
        }
        for (i = 0; i < n; i++) {
            b[i] = 0.;
            for (j = 0; j <= i; j++) {
                b[i] += el[i][j] * y[j];
            }
        }
    }

    void elsolve(double[] b, double[] y) {
        int i, j;
        double sum;
        if (b.length != n || y.length != n) {
            System.out.println("bad lengths");
        }
        for (i = 0; i < n; i++) {
            for (sum = b[i], j = 0; j < i; j++) {
                sum -= el[i][j] * y[j];
            }
            y[i] = sum / el[i][i];
        }
    }

    void inverse(double[][] ainv) {
        int i, j, k;
        double sum;
        ainv = new double[n][n];
        for (i = 0; i < n; i++) {
            for (j = 0; j <= i; j++) {
                sum = (i == j ? 1. : 0.);
                for (k = i - 1; k >= j; k--) {
                    sum -= el[i][k] * ainv[j][k];
                }
                ainv[j][i] = sum / el[i][i];
            }
        }
        for (i = n - 1; i >= 0; i--) {
            for (j = 0; j <= i; j++) {
                sum = (i < j ? 0. : ainv[j][i]);
                for (k = i + 1; k < n; k++) {
                    sum -= el[k][i] * ainv[j][k];
                }
                ainv[i][j] = ainv[j][i] = sum / el[i][i];
            }
        }
    }

    double logdet() {
        double sum = 0.;
        for (int i = 0; i < n; i++) {
            sum += Math.log(el[i][i]);
        }
        return 2. * sum;
    }
};