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
public class B19TOBINCE extends CPHTOBINCE {

    //Phase specific information
    private final String phaseTag_local = "B19TOBINCE"; // Phase Name 
    private final int lc_local[] = {2, 4, 4, 4, 4, 2, 2, 6, 2, 4, 4, 3, 4, 2};
    private final int tc_local = 47;
    private final int nxc_local = 1;
    private final int nc_local = 46;
    private final int lcf_local[] = {2, 4, 4, 4, 4, 2, 2, 6, 2, 4, 4, 3, 4, 2};
    private final int tcf_local = 47;
    private final int nxcf_local = 1;
    private final int ncf_local = 46;
    private final int rcf_local[][][] = {{{2, 4}, {4, 2}}, {{1, 4}, {2, 3}, {4, 1}, {3, 2}}, {{0, 4}, {2, 2}, {4, 0}, {2, 2}}, {{2, 2}, {1, 3}, {2, 2}, {3, 1}}, {{1, 3}, {2, 2}, {3, 1}, {2, 2}}, {{2, 2}, {2, 2}}, {{1, 2}, {2, 1}}, {{2, 1}, {1, 2}, {0, 3}, {1, 2}, {2, 1}, {3, 0}}, {{1, 2}, {2, 1}}, {{1, 2}, {1, 2}, {2, 1}, {2, 1}}, {{2, 0}, {0, 2}, {0, 2}, {2, 0}}, {{0, 2}, {1, 1}, {2, 0}}, {{0, 2}, {1, 1}, {2, 0}, {1, 1}}, {{0, 1}, {1, 0}}, {{0, 0}}};
    private final double m_local[][] = {{0.5, 0.5}, {0.166667, 0.333333, 0.166667, 0.333333}, {0.166667,
        0.333333, 0.166667, 0.333333}, {0.166667, 0.333333, 0.166667,
        0.333333}, {0.333333, 0.166667, 0.333333, 0.166667}, {0.5,
        0.5}, {0.5, 0.5}, {0.166667, 0.166667, 0.166667, 0.166667, 0.166667,
        0.166667}, {0.5, 0.5}, {0.166667, 0.333333, 0.166667,
        0.333333}, {0.166667, 0.333333, 0.166667, 0.333333}, {0.166667,
        0.666667, 0.166667}, {0.166667, 0.333333, 0.166667, 0.333333}, {0.5,
        0.5}, {0.5}};
    private final int wcv_local[][][] = {{{1, 4, 2, 2, 2, 4, 1, 2, 4, 2, 4, 4, 4, 4, 2, 4, 2, 1, 4, 2, 2, 2, 4, 1}, {1, 2, 1, 4, 4, 4, 4, 2, 2, 2, 2, 2, 4, 2, 2, 4, 2, 4, 4, 4, 4, 1, 2, 1}}, {{1, 2, 1, 2, 2, 2, 2, 1, 2, 1, 1, 2, 1, 2, 2, 2, 2, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 2, 2, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 2, 2, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 4, 2, 2, 2, 4, 1}, {1, 2, 1, 2, 2, 2, 2, 1, 2, 1}, {1, 4, 2, 2, 2, 4, 1}, {1, 2, 1, 2, 2, 2, 2, 1, 2, 1}}, {{1, 2, 1, 1, 2, 1, 1, 2, 1, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 2, 1, 2, 2, 2, 2, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 2, 1, 2, 2, 2, 2, 1, 2, 1}}, {{1, 2, 1, 1, 2, 1, 1, 2, 1, 1, 2, 1}, {1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1}}, {{1, 2, 1, 1, 2, 1}, {1, 1, 2, 2, 1, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 2, 1, 1, 2, 1}, {1, 1, 2, 2, 1, 1}}, {{1, 2, 1, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 2, 2, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 2, 1}, {1, 2, 1}, {1, 2, 1}, {1, 2, 1}}, {{1, 2, 1}, {1, 1, 1, 1}, {1, 2, 1}}, {{1, 2, 1}, {1, 1, 1, 1}, {1, 2, 1}, {1, 1, 1, 1}}, {{1, 1}, {1, 1}}, {{1}}};
    private final int[][] lcv_local = {{24, 24}, {20, 32, 20, 32}, {7, 10, 7, 10}, {12, 16, 12, 16}, {16, 10, 16, 10}, {12, 12}, {6, 6}, {8, 8, 8, 8, 8, 8}, {6, 6}, {6, 8, 6, 8}, {3, 3, 3, 3}, {3, 4, 3}, {3, 4, 3, 4}, {2, 2}, {1}};
// Arrays to initialize with phase specific size
    private double[] Scu_local = new double[(tcf_local - nxc_local)];
    private double[] Hcu_local = new double[(tcf_local - nxc_local)];
    private double[] Gcu_local = new double[(tcf_local - nxc_local)];
    private double[][] Scuu_local = new double[(tcf_local - nxcf_local)][(tcf_local - nxcf_local)];
    private double[][] Gcuu_local = new double[(tcf_local - nxc_local)][(tcf_local - nxc_local)];
    private final int np_local = 26;

    public B19TOBINCE(String stdst[], double edis[], String eMatFile, double[] msdis, double T_in, double xB_in) throws FileNotFoundException, IOException { // Constructor Method
        Print.f("B19TOBINCE constructor method called ", 6);
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
        setCMat("data\\TransMat\\cmatB19TOBIN.txt");
        // Parameters
        setNP(np_local);
        //setR(((Math.abs(edis[0]) == 1) ? 1 : 8.3144)); //  Universal gas constant
        setR(8.3144); //  Universal gas constant//vj-2014-11-26
        setEdis(edis);//vj-2014-07-12
        setMsdis(msdis);
        setReferenceStateParameters(stdst);
        setT(T_in);
        updateStdst();
        //Microscopic Paramters
        randInit(xB_in);//set u and update cluster variables as well
        setX(xB_in);
        // Initialize Arrays with phase specific size
        initScu(Scu_local);
        initHcu(Hcu_local);
        initGcu(Gcu_local);
        initScuu(Scuu_local);
        initGcuu(Gcuu_local);
        Print.f("B19TOBINCE constructor method ended", 6);
    }

    private void randInit(double x) throws IOException {//Phase specific method
        double[] u_In = genRandCF(x);
        setU(u_In);
    }

    private double[] genRandCF(double x) {//vj-2012-04-03:Generate random CF
        int index = 0;
        double[] u_In = new double[(tcf_local)];
        double siteOpAlfa, siteOpbeta;
        double LRO1 = x <= 0.5 ? 1.98 * x : 1.98 * (1 - x);
        siteOpAlfa = (2 * x - 1) - LRO1;
        siteOpbeta = (2 * x - 1) + LRO1;
        for (int i = 0; i < getTcdis() - 1; i++) {
            for (int j = 0; j < getLc()[i]; j++) {
                u_In[index] = Math.pow(siteOpAlfa, rcf_local[i][j][1]) * Math.pow(siteOpbeta, rcf_local[i][j][0]);
                index = index + 1;
            }
        }
        u_In[tcf_local - 2] = LRO1;
        u_In[tcf_local - 1] = x;
        return (u_In);
    }

    @Override
    public double[] getURand() {//Phase specific method
        double x = getXB();
        double[] u_In = genRandCF(x);
        return (u_In);
    }

}
