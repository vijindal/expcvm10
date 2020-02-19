/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.solution.cecvm;

import utils.io.Print;
import java.io.FileNotFoundException;
import java.io.IOException;
import phase.solution.cecvm.CVMBINCE;

/**
 *
 * @author metallurgy
 */
public abstract class CPHTOBINCE extends CVMBINCE {

    private int tcdis_local = 14;
    private int nxcdis_local = 1;
    private int ncdis_local = 13;
    private double mhdis_local[] = {1, 6, 3, 6, 6, 2, 1, 12, 1, 6, 3, 3, 3, 1};//vj-2013-05-31
    private int rcdis_local[] = {6, 5, 4, 4, 4, 4, 3, 3, 3, 3, 2, 2, 2, 1};//No of sites for each cluster
    private int nij_local[][] = {
        {1, 6, 3, 6, 6, 0, 2, 12, 0, 6, 3, 6, 6, 6, 1},
        {0, 1, 1, 2, 2, 0, 1, 6, 0, 3, 2, 4, 4, 5, 1},
        {0, 0, 1, 0, 0, 0, 0, 4, 0, 0, 2, 2, 2, 4, 1},
        {0, 0, 0, 1, 0, 0, 1, 2, 0, 1, 1, 2, 3, 4, 1},
        {0, 0, 0, 0, 1, 0, 0, 2, 0, 2, 1, 3, 2, 4, 1},
        {0, 0, 0, 0, 0, 1, 0, 0, 1, 3, 0, 3, 3, 4, 1},
        {0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 3, 3, 1},
        {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 3, 1},
        {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 3, 3, 1},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 2, 1, 3, 1},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 2, 1},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 2, 1},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 1},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1},
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}}; // coefficients of multplicities in the product of m*kb

    public CPHTOBINCE() throws FileNotFoundException, IOException {
        Print.f("*****CPHmTOBINCE constructor called ", 6);
        setTcdis(tcdis_local); //  No of total clusters
        setNxcdis(nxcdis_local); //  No of clusters realted to point cluters
        setNcdis(ncdis_local);
        setMhdis(mhdis_local);
        setRcdis(rcdis_local);
        setUab(tcdis_local, rcdis_local);//Calculate and set uA and uB arrays
        setNij(nij_local);
        Print.f("****CPHmTOBINCE constructor closed ", 6);
    }

    // Getter Methods
//    @Override
//    public int[][] getMCoeffInmkb() {
//        return (nij_local);
//    }
    private void setUab(int tcdis_In, int[] rcdis_In) {
        double uA[] = new double[tcdis_In];
        double uB[] = new double[tcdis_In];
        for (int itc = 0; itc < tcdis_In; itc++) {
            uA[itc] = Math.pow(-1.0, rcdis_In[itc]);
            uB[itc] = Math.pow(1.0, rcdis_In[itc]);
        }
        setUAB(uA, uB);
    }
}
