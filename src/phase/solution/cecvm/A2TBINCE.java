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
public class A2TBINCE extends BCCTBINCE {
    //Phase specific information

    private final String phaseTag_local = "A2mTBINCE";
    private final int lc_local[] = {1, 1, 1, 1, 1};
    private final int tc_local = 5;
    private final int nxc_local = 1;
    private final int nc_local = 4;
    private final int lcf_local[] = {1, 1, 1, 1, 1};
    private final int tcf_local = 5;
    private final int nxcf_local = 1;
    private final int ncf_local = 4;
    private final int rcf_local[][] = {{4}, {3}, {2}, {2}, {1}, {0}};
    private final double m_local[][] = {{1}, {1}, {1}, {1}, {1}};
    private final int wcv_local[][][] = {{{1, 4, 4, 2, 4, 1}}, {{1, 1, 2, 2, 1, 1}}, {{1, 2, 1}}, {{1, 2, 1}}, {{1, 1}}, {{1}}};
    private final int[][] lcv_local = {{6}, {6}, {3}, {3}, {2}, {1}};
    private final double[][][][] cmat_local = {{{{0.0625, -0.25, 0.125, 0.25, -0.5, 0.3125}, {-0.0625, 0.125, 0.,
        0., -0.25, 0.1875}, {0.0625, 0., -0.125, 0., 0., 0.0625}, {0.0625,
        0., 0.125, -0.25, 0., 0.0625}, {-0.0625, -0.125, 0., 0.,
        0.25, -0.0625}, {0.0625, 0.25, 0.125, 0.25,
        0.5, -0.1875}}}, {{{0., -0.125, 0.125, 0.25, -0.75, 0.5}, {0.,
        0.125, 0.125, -0.25, -0.25, 0.25}, {0., 0.125, -0.125, 0., -0.25,
        0.25}, {0., -0.125, -0.125, 0., 0.25, 0.}, {0., -0.125,
        0.125, -0.25, 0.25, 0.}, {0., 0.125, 0.125, 0.25,
        0.75, -0.25}}}, {{{0., 0., 0.25, 0., -1., 0.75}, {0., 0., -0.25,
        0., 0., 0.25}, {0., 0., 0.25, 0., 1., -0.25}}}, {{{0., 0., 0.,
        0.25, -1., 0.75}, {0., 0., 0., -0.25, 0., 0.25}, {0., 0., 0.,
        0.25, 1., -0.25}}}, {{{0., 0., 0., 0., -1., 1.}, {0., 0., 0., 0.,
        1., 0.}}}};
    private final int np_local = 8;
    // Arrays to initialize with phase specific size
    private double[] Scu_local = new double[(tcf_local - nxcf_local)];
    private double[] Hcu_local = new double[(tcf_local - nxcf_local)];
    private double[] Gcu_local = new double[(tcf_local - nxcf_local)];
    private double[][] Scuu_local = new double[(tcf_local - nxcf_local)][(tcf_local - nxcf_local)];
    private double[][] Gcuu_local = new double[(tcf_local - nxcf_local)][(tcf_local - nxcf_local)];
    //private double utc[] = new double[lcf_local[tcdis_local - 1]];

    public A2TBINCE(String stdst[], double edis[], String eMatFile, double[] msdis, double T_in, double xB_in) throws FileNotFoundException, IOException { // Constructor Method
        Print.f("*****A2mTBINc constructor called", 6);
        //setEmat(eMatFile);//vj-15-03-11
        // Phase specific information
        setPhaseTag(phaseTag_local);
        setLc(lc_local);//List of clusters in case of broken symemtry(Ordered phases)per highest symmetry(disordred phase)cluster
        setTc(tc_local);
        setNxc(nxc_local);
        setNc(nc_local);
        setLcf(lcf_local);
        setTcf(tcf_local);
        setNxcf(nxcf_local);
        setNcf(ncf_local);
        setRcf(rcf_local);
        setM(m_local);
        setWcv(wcv_local);
        setLcv(lcv_local);
        setCMat(cmat_local);//vj-15-03-12 
        // Parameters
        setNP(np_local);
        //setR(((Math.abs(edis[0]) == 1) ? 1 : 8.3144)); //  Universal gas constant
        setR(8.3144); //  Universal gas constant//vj-2014-11-26
        setEdis(edis);
        setMsdis(msdis);
        setReferenceStateParameters(stdst);
        setT(T_in);
        setX(xB_in);
        updateStdst();
        //Microscopic Parameters
        genInitialValues(xB_in);//set u and update cluster variables as well
        // Initialize Arrays with phase specific size
        initScu(Scu_local);
        initHcu(Hcu_local);
        initGcu(Gcu_local);
        initScuu(Scuu_local);
        initGcuu(Gcuu_local);
        Print.f("****A2mTBINc constructor ended", 6);
    }

    private void genInitialValues(double x) throws IOException {//Phase specific method
        double[] u_In = new double[(tcf_local)];
        for (int icf = 0; icf < (tcf_local - 1); icf++) {
            u_In[icf] = Math.pow((2 * x - 1), rcf_local[icf][0]);
            //System.out.print(u_In[icf] + " g ");
        }
        u_In[tcf_local - 1] = x;
        setU(u_In);
    }

    /**
     *
     * @return
     */
    @Override
    public double[] getURand() {//Phase specific method
        double x = getXB();
        double[] u_In = new double[(tcf_local)];
        for (int icf = 0; icf < (tcf_local - nxcf_local); icf++) {
            u_In[icf] = Math.pow((2 * x - 1), rcf_local[icf][0]);
            //System.out.print(u_In[icf] + " g ");
        }
        u_In[tcf_local - 1] = x;
        return (u_In);
    }
}
