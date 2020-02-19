
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.solution.cecvm;

import phase.solution.cecvm.CPHTOBINCE;
import utils.io.Print;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 *
 * @author metallurgy
 */
public class D019TOBINCE extends CPHTOBINCE {

    //Phase specific information
    private final String phaseTag_local = "D019TOBINCE"; // Phase Name 
    private final int lc_local[] = {2, 3, 3, 3, 3, 2, 2, 4, 2, 3, 3, 2, 3, 2, 1};
    private final int tc_local = 37;
    private final int nxc_local = 1;
    private final int nc_local = 36;
    private final int lcf_local[] = {2, 3, 3, 3, 3, 2, 2, 4, 2, 3, 3, 2, 3, 2, 1};
    private final int tcf_local = 37;
    private final int nxcf_local = 1;
    private final int ncf_local = 36;
    private final int rcf_local[][][] = {{{2, 4}, {0, 6}}, {{1, 4}, {2, 3}, {0, 5}}, {{0, 4}, {2, 2}, {0, 4}}, {{1, 3}, {2, 2}, {0, 4}}, {{2, 2}, {1, 3}, {0, 4}}, {{1, 3}, {1, 3}}, {{1, 2}, {0, 3}}, {{2, 1}, {1, 2}, {0, 3}, {0, 3}}, {{1, 2}, {0, 3}}, {{1, 2}, {1, 2}, {0, 3}}, {{2, 0}, {0, 2}, {0, 2}}, {{1, 1}, {0, 2}}, {{0, 2}, {1, 1}, {0, 2}}, {{1, 0}, {0, 1}}, {{0, 0}}};
    private final double m_local[][] = {{0.75, 0.25}, {0.25, 0.5, 0.25}, {0.25, 0.5, 0.25}, {0.5, 0.25,
        0.25}, {0.25, 0.5, 0.25}, {0.75, 0.25}, {0.75, 0.25}, {0.25, 0.25,
        0.25, 0.25}, {0.75, 0.25}, {0.25, 0.5, 0.25}, {0.25, 0.5,
        0.25}, {0.5, 0.5}, {0.25, 0.5, 0.25}, {0.25, 0.75}, {0.25}};
    private final int wcv_local[][][] = {{{1, 4, 2, 2, 2, 4, 1, 2, 4, 4, 4, 2, 4, 4, 2, 4, 2, 1, 4, 2, 2, 2, 4, 1}, {1, 6, 6, 6, 6, 3, 12, 6, 2, 6, 3, 6, 1}}, {{1, 2, 2, 2, 1, 2, 2, 1, 2, 1, 1, 2, 2, 2, 1, 2, 2, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 2, 2, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 2, 2, 1, 1}}, {{1, 4, 2, 2, 2, 4, 1}, {1, 2, 1, 2, 2, 2, 2, 1, 2, 1}, {1, 4, 2, 2, 2, 4, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 2, 1, 1, 2, 1, 1, 2, 1, 1, 2, 1}, {1, 2, 1, 2, 1, 1, 1, 2, 1, 2, 1, 1}}, {{1, 2, 1, 2, 2, 2, 2, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 2, 1, 2, 2, 2, 2, 1, 2, 1}}, {{1, 2, 1, 1, 2, 1, 1, 2, 1, 1, 2, 1}, {1, 3, 3, 1, 1, 3, 3, 1}}, {{1, 2, 1, 1, 2, 1}, {1, 3, 3, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 2, 1, 1, 2, 1}, {1, 3, 3, 1}}, {{1, 2, 1, 1, 2, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 2, 2, 1, 1}}, {{1, 2, 1}, {1, 2, 1}, {1, 2, 1}}, {{1, 1, 1, 1}, {1, 2, 1}}, {{1, 2, 1}, {1, 1, 1, 1}, {1, 2, 1}}, {{1, 1}, {1, 1}}, {{1}}};
    private final int[][] lcv_local = {{24, 13}, {20, 32, 20}, {7, 10, 7}, {16, 12, 12}, {10, 16, 10}, {12, 8}, {6, 4}, {8, 8, 8, 8}, {6, 4}, {6, 8, 6}, {3, 3, 3}, {4, 3}, {3, 4, 3}, {2, 2}, {1}};
// Arrays to initialize with phase specific size
    private double[] Scu_local = new double[(tcf_local - nxc_local)];
    private double[] Hcu_local = new double[(tcf_local - nxc_local)];
    private double[] Gcu_local = new double[(tcf_local - nxc_local)];
    private double[][] Scuu_local = new double[(tcf_local - nxcf_local)][(tcf_local - nxcf_local)];
    private double[][] Gcuu_local = new double[(tcf_local - nxc_local)][(tcf_local - nxc_local)];
    private final int np_local = 26;

    public D019TOBINCE(String stdst[], double edis[], String eMatFile, double[] msdis, double T_in, double xB_in) throws FileNotFoundException, IOException { // Constructor Method
        Print.f("D019TOBINCE constructor method called ", 6);
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
        setCMat("..\\data\\TransMat\\cmatD019TOBIN.txt");
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
        Print.f("D019TOBINCE constructor method ended", 6);
    }

    private void randInit(double x) throws IOException {//Phase specific method
        double[] u_In = genRandCF(x);
        setU(u_In);
    }

//    private double[] genRandCF(double x) {//vj-2012-04-03:Generate random CF
//        int index = 0;
//        double[] u_In = new double[(tcf_local)];
//        double siteOpAlfa, siteOpbeta;
//        double factor = 0.9;
//        double LRO1 = x <= m_local[getTcdis() - 1][1] ? factor * (1 / m_local[getTcdis() - 1][1]) * x : factor * (1 / m_local[getTcdis() - 1][0]) * (1 - x);
//        siteOpAlfa = (2 * x - 1) - (1.5) * LRO1;
//        siteOpbeta = (2 * x - 1) + (0.5) * LRO1;
//        for (int i = 0; i < getTcdis() - 1; i++) {
//            for (int j = 0; j < getLc()[i]; j++) {
//                u_In[index] = Math.pow(siteOpAlfa, rcf_local[i][j][0]) * Math.pow(siteOpbeta, rcf_local[i][j][1]);
//                index = index + 1;
//            }
//        }
//        u_In[tcf_local - 2] = LRO1;
//        u_In[tcf_local - 1] = x;
//        return (u_In);
//    }
    private double[] genRandCF(double x) {//vj-2012-04-03:Generate random CF
        int index = 0;
        double[] u_In = new double[(tcf_local)];
        double siteOpAlfa, siteOpbeta;
        double factor = 0.9;
        int tcdis = getTcdis();
        double LRO1;
        if (m_local[tcdis - 1][0] < m_local[tcdis - 1][1]) {
            if (x < 0.5) {
                if (x <= m_local[tcdis - 1][0]) {
                    LRO1 = -(x / m_local[tcdis - 1][0]);
                } else {
                    LRO1 = -((1 - x) / (1 - m_local[tcdis - 1][0]));
                }
            } else {
                if (x <= m_local[tcdis - 1][1]) {
                    LRO1 = (x / m_local[tcdis - 1][1]);
                } else {
                    LRO1 = ((1 - x) / (1 - m_local[tcdis - 1][1]));
                }
            }
        } else {
            if (x < 0.5) {
                if (x <= m_local[tcdis - 1][1]) {
                    LRO1 = (x / m_local[tcdis - 1][1]);
                } else {
                    LRO1 = ((1 - x) / (1 - m_local[tcdis - 1][1]));
                }
            } else {
                if (x <= m_local[tcdis - 1][0]) {
                    LRO1 = -(x / m_local[tcdis - 1][0]);
                } else {
                    LRO1 = -((1 - x) / (1 - m_local[tcdis - 1][0]));
                }
            }
        }
        LRO1=factor*LRO1;
        siteOpAlfa = (2 * x - 1) - (1.5) * LRO1;
        siteOpbeta = (2 * x - 1) + (0.5) * LRO1;
        for (int i = 0; i < getTcdis() - 1; i++) {
            for (int j = 0; j < getLc()[i]; j++) {
                u_In[index] = Math.pow(siteOpAlfa, rcf_local[i][j][0]) * Math.pow(siteOpbeta, rcf_local[i][j][1]);
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
