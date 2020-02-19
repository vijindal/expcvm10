
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.solution.cecvm;

import utils.io.Print;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 *
 * @author metallurgy
 */
public abstract class BCCTBINCE extends CVMBINCE {
    //Highest symmetry phase information: values to be set by respective Phase Class object for example BCCmTBINcCE

    private final int tcdis_local = 5;
    private final int nxcdis_local = 1;
    private final int ncdis_local = 4;
    private final double[] mhdis_local = {6, 12, 3, 4, 1};//vj-2012-06-23
    private final int rcdis_local[] = {4,3,2,2,1,0}; //  No of sites for each cluster
    private final int nij[][] = {
        {1, 4, 2, 4, 4},
        {0, 1, 1, 2, 3},
        {0, 0, 1, 0, 2},
        {0, 0, 0, 1, 2},
        {0, 0, 0, 0, 1}};

    public BCCTBINCE() throws FileNotFoundException, IOException {
        Print.f("*****BCCmTBINc constructor called ", 6);
        setTcdis(tcdis_local); //  No of total clusters
        setNxcdis(nxcdis_local); //  No of clusters realted to point cluters
        setNcdis(ncdis_local);
        setMhdis(mhdis_local);
        setRcdis(rcdis_local);
        setUab(tcdis_local, rcdis_local);//Calculate and set uA and uB arrays
        setNij(nij);
        Print.f("****BCCmTBINc constructor closed ", 6);
    }

    // Getter Methods
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
