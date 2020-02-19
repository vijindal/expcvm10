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
public abstract class FCCTOBINCE extends CVMBINCE {

    private int tcdis_local = 10;
    private int nxcdis_local = 1;
    private int ncdis_local = 9;
    private double mgdis_local[] = {1, 6, 3, 12, 2, 12, 8, 3, 6, 1};//vj-2012-11-29
    private int rcdis_local[] = {6, 5, 4, 4, 4, 3, 3, 2, 2, 1};//  No of sites for each cluster    
    private int nij[][] = {{1, 6, 3, 12, 0, 12, 8, 3, 12, 6, 1}, {0, 1, 1, 4, 0,
        6, 4, 2, 8, 5, 1}, {0, 0, 1, 0, 0, 4, 0, 2, 4, 4,
        1}, {0, 0, 0, 1, 0, 2, 2, 1, 5, 4, 1}, {0, 0, 0, 0,
        1, 0, 4, 0, 6, 4, 1}, {0, 0, 0, 0, 0, 1, 0, 1, 2,
        3, 1}, {0, 0, 0, 0, 0, 0, 1, 0, 3, 3, 1}, {0, 0, 0,
        0, 0, 0, 0, 1, 0, 2, 1}, {0, 0, 0, 0, 0, 0, 0, 0,
        1, 2, 1}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1}, {0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 1}};

    public FCCTOBINCE() throws FileNotFoundException, IOException {
        Print.f("*****FCCmTOBINCE constructor called ", 6);
        setTcdis(tcdis_local); //  No of total clusters
        setNxcdis(nxcdis_local); //  No of clusters realted to point cluters
        setNcdis(ncdis_local);
        setMhdis(mgdis_local);//vj-2012-11-29
        setRcdis(rcdis_local);
        setUab(tcdis_local, rcdis_local);//Calculate and set uA and uB arrays
        setNij(nij);
        Print.f("****FCCmTOBINCE constructor closed ", 6);
    }

    // Getter Methods
    @Override
    public int[][] getMCoeffInmkb() {
        return (nij);
    }
    // Setter Methods

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
