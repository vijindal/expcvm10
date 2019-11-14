package utils.io;

import java.io.IOException;
import java.text.DecimalFormat;

/**
 * @author: Shivam Updated: SU: 11.02.2012 : added method f() for printing 2D
 * boolean array
 *
 */
public class Print {

    static DecimalFormat df = new DecimalFormat("#.########");
    //public static final int pr2F = 1;// 0 for nothing, 1 for console, 2 for file, 3 for both console & file

    private static void write(String str, int loglevel) throws IOException {
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

    public static void f(String str, int loglevel) throws IOException {
        Print.write(str + System.getProperty("line.separator"), loglevel);
    }// closed Method f() 1

// 0D f Printing Starts//2013-03-21-VJ-Added
    public static void f(String varName, boolean var, int loglevel) throws IOException {
        Print.f(varName + ":" + var, loglevel);
    }

    public static void f(String varName, char var, int loglevel) throws IOException {
        Print.f(varName + ":" + var, loglevel);
    }

    public static void f(String varName, int var, int loglevel) throws IOException {
        Print.f(varName + ":" + var, loglevel);
    }

    public static void f(String varName, double var, int loglevel) throws IOException {
        Print.f(varName + ":" + df.format(var), loglevel);
    }

    public static void f(String varName, String var, int loglevel) throws IOException {
        Print.f(varName + ":" + var, loglevel);
    }
// 1D f Printing Starts

    public static void f(String varName, boolean[] var, int loglevel) throws IOException {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.f(var[i] + "}", loglevel);
    }

    public static void f(String varName, char[] var, int loglevel) throws IOException {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.f(var[i] + "}", loglevel);
    }

    public static void f(String varName, int[] var, int loglevel) throws IOException {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.f(var[i] + "}", loglevel);
    }

    public static void f(String varName, double[] var, int loglevel) throws IOException {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(df.format(var[i]) + ",", loglevel);
        }
        Print.f(df.format(var[i]) + "}", loglevel);
    }

    public static void f(String varName, String[] var, int loglevel) throws IOException {//vj-2013-03-24-modified
        int i;
        Print.write(varName + ": {", loglevel);
        for (i = 0; i < var.length - 1; i++) {
            Print.write(var[i] + ",", loglevel);
        }
        Print.f(var[i] + "}", loglevel);
    }
// 2D f Printing Starts

    public static void f(String varName, boolean[][] var, int loglevel) throws IOException {
        Print.f("(2D) (boolean) " + varName + " : ", loglevel);
        for (boolean[] var1 : var) {
            for (int j = 0; j < var1.length; j++) {
                Print.write("  " + var1[j], loglevel);
            }
            Print.f("", loglevel);
        }
    }

    public static void f(String varName, double[][] var, int loglevel) throws IOException {
        Print.f("(2D) (double) " + varName + " : ", loglevel);
        for (double[] var1 : var) {
            for (int j = 0; j < var1.length; j++) {
                Print.write("  " + df.format(var1[j]), loglevel);
            }
            Print.f("", loglevel);
        }
    }

    public static void f(String varName, int[][] var, int loglevel) throws IOException {
        Print.f("(2D) (int) " + varName + " : ", loglevel);
        for (int[] var1 : var) {
            for (int j = 0; j < var1.length; j++) {
                Print.write("  " + var1[j], loglevel);
            }
            Print.f("", loglevel);
        }
    }

    public static void f(String varName, String[][] var, int loglevel) throws IOException {
        Print.f("(2D) (String) " + varName + " : ", loglevel);
        for (String[] var2 : var) {
            for (String var1 : var2) {
                Print.write("  " + var1, loglevel);
            }
            Print.f("", loglevel);
        }
    }

// 3D f Printing Starts
    public static void f(String varName, double[][][] var, int loglevel) throws IOException {
        Print.f("(3D) (double) " + varName + " : ", loglevel);
        for (int i = 0; i < var.length; i++) {
            Print.f("3D Iterator " + i + " :", loglevel);
            for (double[] var1 : var[i]) {
                for (int k = 0; k < var1.length; k++) {
                    Print.write(" " + df.format(var1[k]), loglevel);
                }
                Print.f("", loglevel);
            }
            Print.f("", loglevel);
        }
    }

    public static void f(String varName, int[][][] var, int loglevel) throws IOException {
        Print.f("(3D) (int) " + varName + " : ", loglevel);
        for (int i = 0; i < var.length; i++) {
            Print.f("3D Iterator " + i + " :", loglevel);
            for (int[] var1 : var[i]) {
                for (int k = 0; k < var1.length; k++) {
                    Print.write(" " + var1[k], loglevel);
                }
                Print.f("", loglevel);
            }
            Print.f("", loglevel);
        }
    }

    public static void f(String varName, String[][][] var, int loglevel) throws IOException {
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

    public static void f(String varName, double[][][][] var, int loglevel) throws IOException {
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
