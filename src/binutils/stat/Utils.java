/*
 * To change this template, choose Tools | Templates
 * and open the template i the editor.
 */
package binutils.stat;

import binutils.io.Print;
import java.io.IOException;

/**
 *
 * @author ViJindal
 */
public class Utils {

    public static double stpmaX(double oldX, double newX) throws IOException {//2012-02-25(VJ): added, Returns scaling factor for x update
        double stpmax;
        //prnt.writeln("oldX:" + oldX + ",newX:" + newX, 0);
        if (newX > 1) {
            stpmax = 0.9 * ((1 - oldX) / (newX - oldX));

        } else if (newX < 0) {
            stpmax = 0.9 * ((0 - oldX) / (newX - oldX));
        } else {
            stpmax = 1;
        }
        return stpmax;
    }
    
/**
 * Method normalizes values of correlation function array x_Out between [-1,1]
 * @param x_In Input correlation function array
 * @param x_Out output correlation function array
 * @return stpmax maximum step size 
 * @throws IOException 
 */
    public static Double normalU(double[] x_In, double[] x_Out) throws IOException {
        Print.f("Utils.normalU called", 7);
        int n = x_In.length;
        double stpmax = 1.0;
        for (int i = 0; i < n; i++) {
            double stpTrial;
            if (x_Out[i] > (1.0)) {
                stpTrial = 0.9 * ((1.0 - x_In[i]) / (x_Out[i] - x_In[i]));

            } else if (x_Out[i] < (-1.0)) {
                stpTrial = 0.9 * ((-1.0 - x_In[i]) / (x_Out[i] - x_In[i]));
            } else {
                stpTrial = 1;
            }
            stpmax = Math.min(stpmax, stpTrial);
        }
        for (int in = 0; in < n; in++) {
            x_Out[in] = x_In[in] + stpmax * (x_Out[in] - x_In[in]);
        }
        //Print.f("x_Out", x_Out, 0);
        Print.f("Utils.normalU ended with stpmax", stpmax, 7);
        return (stpmax);
    }

    public static void drawLine() {
        System.out.println("------------------------------------------------------------------");
    }
}
