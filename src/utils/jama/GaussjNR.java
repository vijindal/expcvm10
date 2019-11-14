/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils.jama;

/*
 * Linear equation solution by Gauss-Jordan elimination, equation (2.1.1) from
 * Press(2007). The input matrix is a[0..n-1][0..n-1]. b[0..n-1][0..m-1] is
 * input containing the m right-hand side vectors. On output, a is replaced by
 * its matrix inverse, and b is replaced by the corresponding set of solution
 * vectors.
 */
public class GaussjNR {

    private double[][] a;
    private double[][] b;

    public GaussjNR(double[][] a, double[][] b) {
        this.a = a;
        this.b = b;
    }

    public void solve() {
        int i, icol = 0, irow = 0, j, k, l, ll, n = a.length, m = b[0].length;
        double big, dum, pivinv;
        int[] indxc = new int[n];
        int[] indxr = new int[n];
        int[] ipiv = new int[n];
        for (j = 0; j < n; j++) {
            ipiv[j] = 0;
        }
        for (i = 0; i < n; i++) {
            big = 0.0;
            for (j = 0; j < n; j++) {
                if (ipiv[j] != 1) {
                    for (k = 0; k < n; k++) {
                        if (ipiv[k] == 0) {
                            if (Math.abs(a[j][k]) >= big) {
                                big = Math.abs(a[j][k]);
                                irow = j;
                                icol = k;
                            }
                        }
                    }
                }
            }
            ++(ipiv[icol]);
            if (irow != icol) {
                for (l = 0; l < n; l++) {
                    double temp;
                    temp = a[irow][l];
                    a[irow][l] = a[icol][l];
                    a[icol][l] = temp;
                    //SWAP(a[irow][l], a[icol][l]);
                }
                for (l = 0; l < m; l++) {
                    double temp;
                    temp = b[irow][l];
                    b[irow][l] = b[icol][l];
                    b[icol][l] = temp;
                    //SWAP(b[irow][l], b[icol][l]);
                }
            }
            indxr[i] = irow;
            indxc[i] = icol;
            if (a[icol][icol] == 0.0) {
                System.out.println("gaussj: Singular Matrix");
            }
            pivinv = 1.0 / a[icol][icol];
            a[icol][icol] = 1.0;
            for (l = 0; l < n; l++) {
                a[icol][l] *= pivinv;
            }
            for (l = 0; l < m; l++) {
                b[icol][l] *= pivinv;
            }
            for (ll = 0; ll < n; ll++) {
                if (ll != icol) {
                    dum = a[ll][icol];
                    a[ll][icol] = 0.0;
                    for (l = 0; l < n; l++) {
                        a[ll][l] -= a[icol][l] * dum;
                    }
                    for (l = 0; l < m; l++) {
                        b[ll][l] -= b[icol][l] * dum;
                    }
                }
            }
        }
        for (l = n - 1; l >= 0; l--) {
            if (indxr[l] != indxc[l]) {
                for (k = 0; k < n; k++) {
                    double temp;
                    temp = a[k][indxr[l]];
                    a[k][indxr[l]] = a[k][indxc[l]];
                    a[k][indxc[l]] = temp;
                    //SWAP(a[k][indxr[l]], a[k][indxc[l]]);
                }
            }
        }

    }
}
