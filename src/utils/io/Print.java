package utils.io;

import java.text.DecimalFormat;

/**
 * @author : Shivam Updated: SU: 11.02.2012 : added method f() for printing 2D
 * boolean array
 *
 */
public class Print {

    static DecimalFormat df = new DecimalFormat("#.########");
    //public static final int pr2F = 1;// 0 for nothing, 1 for console, 2 for file, 3 for both console & file

    private static void write(String str, int loglevel) {
        if (loglevel <= DataPrinter.logLevel) {
            switch (DataPrinter.pr2F) {
                case 0:// Prints Nothing
                    break;
                case 1:// Prints to Console Only
                    if (str.startsWith("*****")) {
                        System.out.println();
                    }
                    System.out.print(str);
                    break;
                case 2:// Prints to File Only
                    //if (str.startsWith("*****")) {
                    //}
                    break;
                case 3:// Prints to Both Console & File
                    if (str.startsWith("*****")) {
                        System.out.println();
                        //DataPrinter.printLog(str+DataPrinter.nl);
                    }
                    System.out.print(str);
                    //DataPrinter.printLog(str);
                    break;
                default:
                    System.out.println("Didn't match any predefined printing Criteria !");
                    break;
            }
        }
    }// closed Method 

    public static void f(String str, int loglevel) {
        Print.write(str + System.getProperty("line.separator"), loglevel);
    }// closed Method f() 1

// 0D f Printing Starts//2013-03-21-VJ-Added
    public static void f(String varName, boolean var, int loglevel) {
        Print.f(varName + ":" + var, loglevel);
    }

    public static void f(String varName, char var, int loglevel) {
        Print.f(varName + ":" + var, loglevel);
    }

    public static void f(String varName, int var, int loglevel) {
        Print.f(varName + ":" + var, loglevel);
    }

    public static void f(String varName, double var, int loglevel) {
        Print.f(varName + ":" + df.format(var), loglevel);
    }

    public static void f(String varName, String var, int loglevel) {
        Print.f(varName + ":" + var, loglevel);
    }
// 1D f Printing Starts

    public static void f(String varName, boolean[] var, int loglevel) {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.f(var[i] + "}", loglevel);
    }

    public static void f(String varName, char[] var, int loglevel) {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.f(var[i] + "}", loglevel);
    }

    public static void f(String varName, int[] var, int loglevel) {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.f(var[i] + "}", loglevel);
    }

    public static void f(String varName, double[] var, int loglevel) {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(df.format(var[i]) + ",", loglevel);
        }
        Print.f(df.format(var[i]) + "}", loglevel);
    }

    public static void f(String varName, String[] var, int loglevel) {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.f(var[i] + "}", loglevel);
    }

    public static void f(String[] var, int loglevel) {//vj-2013-03-24-modified
        int i;
        Print.write("{", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.write(var[i] + "}", loglevel);
    }

    public static void f(int[] var, int loglevel) {//vj-2013-03-24-modified
        int i;
        Print.write("{", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.write(var[i] + "}", loglevel);
    }

    public static void f(double[] var, int loglevel) {//vj-2013-03-24-modified
        int i;
        Print.write("{", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.write(var[i] + "}", loglevel);
    }
// 2D f Printing Starts

    public static void f(String varName, boolean[][] var, int loglevel) {
        Print.f("(2D) (boolean) " + varName + " : ", loglevel);
        for (boolean[] var1 : var) {
            for (int j = 0; j < var1.length; j++) {
                Print.write("  " + var1[j], loglevel);
            }
            Print.f("", loglevel);
        }
    }

    public static void f(String varName, double[][] var, int loglevel) {
        int j;
        Print.write(varName + ": {", loglevel);
        for (j = 0; j < var.length - 1; j++) {
            Print.f(var[j], loglevel);
            Print.write(",", loglevel);
        }
        Print.f(var[j], loglevel);
        Print.f("}", loglevel);
    }

    public static void f(String varName, int[][] var, int loglevel) {
        int j;
        Print.write(varName + ": {", loglevel);
        for (j = 0; j < var.length - 1; j++) {
            Print.f(var[j], loglevel);
            Print.write(",", loglevel);
        }
        Print.f(var[j], loglevel);
        Print.f("}", loglevel);
    }

    public static void f(String varName, String[][] var, int loglevel) {
        int j;
        Print.write(varName + ": {", loglevel);
        for (j = 0; j < var.length - 1; j++) {
            Print.f(var[j], loglevel);
            Print.write(",", loglevel);
        }
        Print.f(var[j], loglevel);
        Print.f("}", loglevel);
    }

    /**
     *
     * @param varName
     * @param var
     * @param loglevel
     */
// 3D f Printing Starts
    public static void f(String varName, double[][][] var, int loglevel) {
        int i, j, k;
        Print.write(varName + ": {", loglevel);
        for (double[][] var1 : var) {
            Print.write("{", loglevel);
            for (double[] var11 : var1) {
                Print.write("{", loglevel);
                for (k = 0; k < var11.length - 1; k++) {
                    Print.write(var11[k] + ",", loglevel);
                }
                Print.write(var11[k] + "}", loglevel);
            }
            Print.write("},", loglevel);
        }
        System.out.println();
    }

    public static void f(String varName, int[][][] var, int loglevel) {
//        Print.f("(3D) (int) " + varName + " : ", loglevel);
//        for (int i = 0; i < var.length; i++) {
//            Print.f("3D Iterator " + i + " :", loglevel);
//            for (int[] var1 : var[i]) {
//                for (int k = 0; k < var1.length; k++) {
//                    Print.write(" " + var1[k], loglevel);
//                }
//                Print.f("", loglevel);
//            }
//            Print.f("", loglevel);
//        }
        int i, j, k;
        Print.write(varName + ": {", loglevel);
        for (int[][] var1 : var) {
            Print.write("{", loglevel);
            for (int[] var11 : var1) {
                Print.write("{", loglevel);
                for (k = 0; k < var11.length - 1; k++) {
                    Print.write(var11[k] + ",", loglevel);
                }
                Print.write(var11[k] + "}", loglevel);
            }
            Print.write("},", loglevel);
        }
        System.out.println();
    }

    public static void f(String varName, String[][][] var, int loglevel) {
        Print.f("(3D) (String) " + varName + " : ", loglevel);
        for (int i = 0; i < var.length; i++) {
            Print.f("3D Iterator " + i + " :", loglevel);
            for (String[] var1 : var[i]) {
                for (String var11 : var1) {
                    Print.write(" " + var11, loglevel);
                }
                Print.f("", loglevel);
            }
            Print.f("", loglevel);
        }
    }
// 4D f Printing Starts

    /**
     *
     * @param varName
     * @param var
     * @param loglevel
     */
    public static void f(String varName, double[][][][] var, int loglevel) {
        Print.f("(4D) (double) " + varName + " : ", loglevel);
        Print.f("{ ", loglevel);
        for (double[][][] var1 : var) {
            Print.write(" {", loglevel);
            for (double[][] var11 : var1) {
                Print.write("  {", loglevel);
                for (double[] var111 : var11) {
                    Print.write(" {", loglevel);
                    for (int l = 0; l < var111.length; l++) {
                        Print.write(df.format(var111[l]) + ", ", loglevel);
                    } // l loop closed
                    Print.write("},", loglevel);
                } // k loop closed
                Print.f(" }", loglevel);
            } // j loop closed
            Print.f(" }", loglevel);
        } // i loop closed
        Print.f("}//4D closed", loglevel);
    }
}// Closed Class Print
