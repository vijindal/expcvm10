/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.solution.cecvm.fcc;

import utils.io.Print;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 *
 * @author metallurgy
 */
public class A1TOBINCE extends FCCTOBINCE {
//Phase specific information

    private final String phaseTag_local = "A1mTOBINCE";
    private final int lc_local[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private final int tc_local = 10;
    private final int nxc_local = 1;
    private final int nc_local = 9;
    private final int lcf_local[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private final int tcf_local = 10;
    private final int nxcf_local = 1;
    private final int ncf_local = 9;
    private final int rcf_local[][] = {{6}, {5}, {4}, {4}, {4}, {3}, {3}, {2}, {2}, {1}};
    private final double m_local[][] = {{1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}};
    private final int wcv_local[][][] = {{{1, 6, 12, 8, 3, 12, 12, 3, 6, 1}}, {{1, 1, 4, 4, 4, 4, 2, 2, 4, 4, 1, 1}}, {{1, 4, 4, 2, 4, 1}}, {{1, 2, 1, 2, 4, 2, 1, 2, 1}}, {{1, 4, 6, 4, 1}}, {{1, 1, 2, 2, 1, 1}}, {{1, 3, 3, 1}}, {{1, 2, 1}}, {{1, 2, 1}}, {{1, 1}}, {{1}}};
    private final int[][] lcv_local = {{10}, {12}, {6}, {9}, {5}, {6}, {4}, {3}, {3}, {2}, {1}};
    private final double[][][][] cmat_local = {{{{0.015625, -0.09375, 0.046875, 0.1875, 0., -0.1875, -0.125,
        0.046875, 0.1875, -0.1875, 0.109375}, {-0.015625,
        0.0625, -0.015625, -0.0625, 0., 0., 0., 0.015625, 0.0625, -0.125,
        0.078125}, {0.015625, -0.03125, -0.015625, 0., 0., 0.0625,
        0., -0.015625, 0., -0.0625, 0.046875}, {-0.015625, 0., 0.046875,
        0., 0., 0., 0., -0.046875, 0., 0., 0.015625}, {0.015625, -0.03125,
        0.046875, -0.0625, 0., -0.0625, 0.125,
        0.046875, -0.0625, -0.0625, 0.046875}, {-0.015625, 0., -0.015625,
        0.0625, 0., 0., 0., 0.015625, -0.0625, 0., 0.015625}, {0.015625,
        0.03125, -0.015625, 0., 0., -0.0625, 0., -0.015625, 0.,
        0.0625, -0.015625}, {0.015625, 0.03125, 0.046875, -0.0625, 0.,
        0.0625, -0.125, 0.046875, -0.0625,
        0.0625, -0.015625}, {-0.015625, -0.0625, -0.015625, -0.0625, 0.,
        0., 0., 0.015625, 0.0625, 0.125, -0.046875}, {0.015625, 0.09375,
        0.046875, 0.1875, 0., 0.1875, 0.125, 0.046875, 0.1875,
        0.1875, -0.078125}}}, {{{0., -0.03125, 0.03125, 0.125,
        0., -0.1875, -0.125, 0.0625, 0.25, -0.3125, 0.1875}, {0., 0.03125,
        0.03125, -0.125, 0., -0.0625, 0.125, 0.0625, 0., -0.1875,
        0.125}, {0., 0.03125, -0.03125, -0.0625, 0., 0.0625, 0., 0.,
        0.0625, -0.1875, 0.125}, {0., -0.03125, -0.03125, 0.0625, 0.,
        0.0625, 0., 0., -0.0625, -0.0625, 0.0625}, {0., -0.03125, 0.03125,
        0., 0., 0.0625, 0., -0.0625, 0., -0.0625, 0.0625}, {0., 0.03125,
        0.03125, 0., 0., -0.0625, 0., -0.0625, 0., 0.0625,
        0.}, {0., -0.03125, 0.03125, 0., 0., -0.0625, 0.125,
        0.0625, -0.125, -0.0625, 0.0625}, {0., 0.03125, 0.03125, 0., 0.,
        0.0625, -0.125, 0.0625, -0.125, 0.0625, 0.}, {0.,
        0.03125, -0.03125, 0.0625, 0., -0.0625, 0., 0., -0.0625, 0.0625,
        0.}, {0., -0.03125, -0.03125, -0.0625, 0., -0.0625, 0., 0.,
        0.0625, 0.1875, -0.0625}, {0., -0.03125, 0.03125, -0.125, 0.,
        0.0625, -0.125, 0.0625, 0., 0.1875, -0.0625}, {0., 0.03125,
        0.03125, 0.125, 0., 0.1875, 0.125, 0.0625, 0.25,
        0.3125, -0.125}}}, {{{0., 0., 0.0625, 0., 0., -0.25, 0., 0.125,
        0.25, -0.5, 0.3125}, {0., 0., -0.0625, 0., 0., 0.125, 0., 0.,
        0., -0.25, 0.1875}, {0., 0., 0.0625, 0., 0., 0., 0., -0.125, 0.,
        0., 0.0625}, {0., 0., 0.0625, 0., 0., 0., 0., 0.125, -0.25, 0.,
        0.0625}, {0., 0., -0.0625, 0., 0., -0.125, 0., 0., 0.,
        0.25, -0.0625}, {0., 0., 0.0625, 0., 0., 0.25, 0., 0.125, 0.25,
        0.5, -0.1875}}}, {{{0., 0., 0., 0.0625, 0., -0.125, -0.125,
        0.0625, 0.3125, -0.5, 0.3125}, {0., 0., 0., -0.0625, 0., 0.,
        0.125, 0.0625, -0.0625, -0.25, 0.1875}, {0., 0., 0., 0.0625, 0.,
        0.125, -0.125, 0.0625, -0.1875, 0., 0.0625}, {0., 0., 0., -0.0625,
        0., 0.125, 0., -0.0625, 0.0625, -0.25, 0.1875}, {0., 0., 0.,
        0.0625, 0., 0., 0., -0.0625, -0.0625, 0., 0.0625}, {0., 0.,
        0., -0.0625, 0., -0.125, 0., -0.0625, 0.0625, 0.25, -0.0625}, {0.,
        0., 0., 0.0625, 0., -0.125, 0.125, 0.0625, -0.1875, 0.,
        0.0625}, {0., 0., 0., -0.0625, 0., 0., -0.125, 0.0625, -0.0625,
        0.25, -0.0625}, {0., 0., 0., 0.0625, 0., 0.125, 0.125, 0.0625,
        0.3125, 0.5, -0.1875}}}, {{{0., 0., 0., 0., 0.0625, 0., -0.25, 0.,
        0.375, -0.5, 0.3125}, {0., 0., 0., 0., -0.0625, 0., 0.125, 0.,
        0., -0.25, 0.1875}, {0., 0., 0., 0., 0.0625, 0., 0., 0., -0.125,
        0., 0.0625}, {0., 0., 0., 0., -0.0625, 0., -0.125, 0., 0.,
        0.25, -0.0625}, {0., 0., 0., 0., 0.0625, 0., 0.25, 0., 0.375,
        0.5, -0.1875}}}, {{{0., 0., 0., 0., 0., -0.125, 0., 0.125,
        0.25, -0.75, 0.5}, {0., 0., 0., 0., 0., 0.125, 0.,
        0.125, -0.25, -0.25, 0.25}, {0., 0., 0., 0., 0., 0.125,
        0., -0.125, 0., -0.25, 0.25}, {0., 0., 0., 0., 0., -0.125,
        0., -0.125, 0., 0.25, 0.}, {0., 0., 0., 0., 0., -0.125, 0.,
        0.125, -0.25, 0.25, 0.}, {0., 0., 0., 0., 0., 0.125, 0., 0.125,
        0.25, 0.75, -0.25}}}, {{{0., 0., 0., 0., 0., 0., -0.125, 0.,
        0.375, -0.75, 0.5}, {0., 0., 0., 0., 0., 0., 0.125,
        0., -0.125, -0.25, 0.25}, {0., 0., 0., 0., 0., 0., -0.125,
        0., -0.125, 0.25, 0.}, {0., 0., 0., 0., 0., 0., 0.125, 0., 0.375,
        0.75, -0.25}}}, {{{0., 0., 0., 0., 0., 0., 0., 0.25, 0., -1.,
        0.75}, {0., 0., 0., 0., 0., 0., 0., -0.25, 0., 0., 0.25}, {0., 0.,
        0., 0., 0., 0., 0., 0.25, 0., 1., -0.25}}}, {{{0., 0., 0., 0.,
        0., 0., 0., 0., 0.25, -1., 0.75}, {0., 0., 0., 0., 0., 0., 0.,
        0., -0.25, 0., 0.25}, {0., 0., 0., 0., 0., 0., 0., 0., 0.25,
        1., -0.25}}}, {{{0., 0., 0., 0., 0., 0., 0., 0., 0., -1.,
        1.}, {0., 0., 0., 0., 0., 0., 0., 0., 0., 1., 0.}}}};
    private final int np_local = 18;
    // Arrays to initialize with phase specific size
    private double[] Scu_local = new double[(tcf_local - nxcf_local)];
    private double[] Hcu_local = new double[(tcf_local - nxcf_local)];
    private double[] Gcu_local = new double[(tcf_local - nxcf_local)];
    private double[][] Scuu_local = new double[(tcf_local - nxcf_local)][(tcf_local - nxcf_local)];
    private double[][] Gcuu_local = new double[(tcf_local - nxcf_local)][(tcf_local - nxcf_local)];

    public A1TOBINCE(String stdst[], double edis[], String eMatFile, double mdis[], double T_in, double xB_in) throws FileNotFoundException, IOException { // Constructor Method
        Print.f("*****A1mTOBINCE constructor type 1 called", 6);
        setEmat(eMatFile);//vj-15-03-11
        // Phase specific information
        setPhaseTag(phaseTag_local);
        setLc(lc_local);//List of clusters in case of broken symemtry(Ordered phases)per highest symmetry(disordred phase)cluster
        setTc(tc_local);
        setNxc(nxc_local);
        setNc(nc_local);
        setRcf(rcf_local);
        setLcf(lcf_local);
        setTcf(tcf_local);
        setNxcf(nxcf_local);
        setNcf(ncf_local);
        setM(m_local);
        setWcv(wcv_local);
        setLcv(lcv_local);
        setCMat(cmat_local);
        // Parameters
        setNP(np_local);
        //setR(((Math.abs(edis[0]) == 1) ? 1 : 8.3144)); //  Universal gas constant
        setR(8.3144); //  Universal gas constant//vj-2014-11-26
        setEdis(edis);
        setMsdis(mdis);
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
        Print.f("****A1mTOBINCE constructor type 1 ended", 6);
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
