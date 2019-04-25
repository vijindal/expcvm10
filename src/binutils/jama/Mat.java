package binutils.jama;

import java.io.IOException;

/**
 * @author metallurgy
 */
public class Mat {

    public static double add(double vec1, double vec2) {//vj-2012-03-16
        return (vec1 + vec2);
    }

    public static double[] add(double[] vec1, double[] vec2) {//2012-02-23(VJ):Added
        int ivec = vec1.length;
        double vec_local[] = new double[ivec];
        for (int i = 0; i < ivec; i++) {
            vec_local[i] = vec1[i] + vec2[i];
        }
        return (vec_local);
    }

    public static double[][] add(double[][] mat1, double[][] mat2) {//vj-2012-03-16
        int ivec = mat1.length;
        int jvec = mat1[0].length;
        double mat_local[][] = new double[ivec][jvec];
        for (int i = 0; i < ivec; i++) {
            for (int j = 0; j < jvec; j++) {
                mat_local[i][j] = mat1[i][j] + mat2[i][j];
            }
        }
        return (mat_local);
    }

    public static double[] mul(double k, double[] vec) {//2012-02-23(VJ):Added
        int ivec = vec.length;
        double vec_local[] = new double[ivec];
        for (int i = 0; i < ivec; i++) {
            vec_local[i] = k * vec[i];
        }
        return (vec_local);
    }
    /*
     * Return scalar producs of the two vectors vecA and vecB 
     */

//    public static double mul(double[] vecA, double[] vecB) throws IOException {//2012-03-13(VJ):Added
//        int ivecA = vecA.length;
//        int ivecB = vecB.length;
//        if (ivecA != ivecB) {
//            throw new IllegalArgumentException("Matrix inner dimensions must agree.");
//        }
//        double prod = 0.0;
//        for (int i = 0; i < ivecA; i++) {
//            prod = prod + vecA[i] * vecB[i];
//        }
//        return (prod);
//    }
//    public static double[] mul(double[][] mat, double[] vec) {
//        int imat = mat.length;//No of rows
//        int jmat = mat[0].length;// No of Columns
//        int ivec = vec.length;
//        double vec_local[] = new double[ivec];
//        if (jmat != ivec) {
//            System.err.println("Mat():mul():Incompatible Matrices Dimensions");
//            return vec;
//        } else {
//            for (int i = 0; i < imat; i++) {
//                vec_local[i] = 0.0;
//                for (int j = 0; j < jmat; j++) {
//                    vec_local[i] = vec_local[i] + mat[i][j] * vec[j];
//                }
//            }
//            return (vec_local);
//        }
//    }

    /*
     * Matrix transpose.
     */
    public static double[][] transpose(double[][] mat) throws IOException {
        int imat = mat.length;//No of rows
        int jmat = mat[0].length;// No of Columns
        double[][] transMat = new double[jmat][imat];
        for (int i = 0; i < imat; i++) {
            for (int j = 0; j < jmat; j++) {
                transMat[j][i] = mat[i][j];
            }
        }
        //prnt.Matrix(transMat,"transMat");
        return transMat;
    }

    // This method Solve equation [A].{X}={B}
    public static double[] LDsolve(double[][] A_In, double[] B_In) {//2012-02-23(VJ):Added
        int n = B_In.length;
        double[] p = new double[n];
        System.arraycopy(B_In, 0, p, 0, n);
        Matrix A = new Matrix(A_In);
        Matrix B = new Matrix(B_In);
        LUDecomposition LU = new LUDecomposition(A);
        Matrix X = LU.solve(B);
        for (int icf = 0; icf < (n); icf++) {
            p[icf] = X.getArray()[icf][0];
        }
        return (p);
    }

    // This method Solve equation [A].{X}={B}
    public static void LDsolve(double[][] A_In, double[] B_In, double[] X_Out) {//2012-02-23(VJ):Added
        int n = B_In.length;
        Matrix A = new Matrix(A_In);
        Matrix B = new Matrix(B_In);
        LUDecomposition LU = new LUDecomposition(A);
        Matrix X = LU.solve(B);
        for (int icf = 0; icf < (n); icf++) {
            X_Out[icf] = X.getArray()[icf][0];
        }
    }

    public static double[] mul(double[][] mat, double[] vec) {
        int imat = mat.length;
        int jmat = mat[0].length;
        int ivec = vec.length;
        double[] vec_local = new double[imat];
        if (jmat != ivec) {
            System.err.println("Incompatible Matrices Dimensions");
            return vec;
        } else {
            for (int i = 0; i < imat; i++) {
                vec_local[i] = 0.0;
                for (int j = 0; j < jmat; j++) {
                    vec_local[i] = vec_local[i] + mat[i][j] * vec[j];
                }
            }
            return vec_local;
        }
    }

    public static double[][] mul(double[][][] mat, double[] vec) {
        int imat = mat.length;
        int jmat = mat[0].length;
        int kmat = mat[0][0].length;
        int ivec = vec.length;
        double[][] mat_local = new double[imat][jmat];
        if (kmat != ivec) {
            System.err.println("Incompatible Matrices Dimensions");
        } else {
            for (int i = 0; i < imat; i++) {
                for (int j = 0; j < jmat; j++) {
                    mat_local[i][j] = 0.0;
                    for (int k = 0; k < kmat; k++) {
                        mat_local[i][j] = mat_local[i][j] + mat[i][j][k] * vec[k];
                    }
                }
            }
        }
        return mat_local;
    }
    //Abstract Methods

    public static double[] mul(double[] vec, double[][] mat) {
        int imat = mat.length;
        int jmat = mat[0].length;
        int ivec = vec.length;
        double[] vec_local = new double[jmat];
        if (imat != ivec) {
            System.err.println("Incompatible Matrices Dimensions");
            return vec;
        } else {
            for (int j = 0; j < jmat; j++) {
                vec_local[j] = 0.0;
                for (int i = 0; i < imat; i++) {
                    vec_local[j] = vec_local[j] + mat[i][j] * vec[i];
                }
            }
            return vec_local;
        }
    }

    public static double[][] mul(double[] vec, double[][][] mat) {
        int imat = mat.length;
        int jmat = mat[0].length;
        int kmat = mat[0][0].length;
        int ivec = vec.length;
        double[][] mat_local = new double[jmat][kmat];
        if (imat != ivec) {
            System.err.println("Incompatible Matrices Dimensions");
        } else {
            for (int j = 0; j < jmat; j++) {
                for (int k = 0; k < kmat; k++) {
                    mat_local[j][k] = 0.0;
                    for (int i = 0; i < imat; i++) {
                        mat_local[j][k] = mat_local[j][k] + mat[i][j][k] * vec[i];
                    }
                }
            }
        }
        return mat_local;
    }

    public static double[][][] mul(double[][][][] mat, double[] vec) {
        int imat = mat.length;
        int jmat = mat[0].length;
        int kmat = mat[0][0].length;
        int lmat = mat[0][0][0].length;
        int ivec = vec.length;
        double[][][] mat_local = new double[imat][jmat][kmat];
        if (lmat != ivec) {
            System.err.println("Incompatible Matrices Dimensions");
        } else {
            for (int i = 0; i < imat; i++) {
                for (int j = 0; j < jmat; j++) {
                    for (int k = 0; k < kmat; k++) {
                        mat_local[i][j][k] = 0.0;
                        for (int l = 0; l < lmat; l++) {
                            mat_local[i][j][k] = mat_local[i][j][k] + mat[i][j][k][l] * vec[l];
                        }
                    }
                }
            }
        }
        return mat_local;
    }

    public static double[] add(double[] vec1, double[] vec2, double[] vec3) {
        int ivec = vec1.length;
        double[] vec_local = new double[ivec];
        for (int i = 0; i < ivec; i++) {
            vec_local[i] = vec1[i] + vec2[i] + vec3[i];
        }
        return vec_local;
    }

    public static double[] add(double[] vec1, double[] vec2, double[] vec3, double[] vec4) {
        int ivec = vec1.length;
        double[] vec_local = new double[ivec];
        for (int i = 0; i < ivec; i++) {
            vec_local[i] = vec1[i] + vec2[i] + vec3[i] + vec4[i];
        }
        return vec_local;
    }

    public static double[] add(double[] vec1, double[] vec2, double[] vec3, double[] vec4, double[] vec5, double[] vec6) {
        int ivec = vec1.length;
        double[] vec_local = new double[ivec];
        for (int i = 0; i < ivec; i++) {
            vec_local[i] = vec1[i] + vec2[i] + vec3[i] + vec4[i] + vec5[i] + vec6[i];
        }
        return vec_local;
    }

    public static double mul(double[] vec1, double[] vec2) {
        int ivec = vec1.length;
        double vec_local = 0.0;
        for (int i = 0; i < ivec; i++) {
            vec_local = vec_local + vec1[i] * vec2[i];
        }
        return vec_local;
    }

}
