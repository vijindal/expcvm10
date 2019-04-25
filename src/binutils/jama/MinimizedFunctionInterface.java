/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package binutils.jama;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: ANU</p>
 *
 * @author Dr. V. Vasilyev
 * 
 *
 */
public interface MinimizedFunctionInterface {

    double function(int n, double[] X, double[] Grads);

    float function(int n, float[] X, float[] Grads);

    double function(int n, double[] X);

    float function(int n, float[] X);
}
