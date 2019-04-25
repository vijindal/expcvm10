/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.solution.cecvm.bcc;

import binutils.io.Print;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 *
 * @author metallurgy
 */
public class A2ORCBINCE extends BCCTBINCE {
    //Highest symmetry phase information

    private int tcdis_local = 70;
    private int nxcdis_local = 1;
    private int ncdis_local = 69;
    private int[] rcdis_local = {2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 8, 8, 8, 9, 1};
    private double[] kbdis_local = {0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0, 0, -2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0};
    private double[] mdis_local = {4, 3, 6, 12, 4, 12, 12, 4, 12, 24, 8, 24, 48, 24, 24, 8, 3, 8, 24, 24, 6, 2, 6, 6, 12, 48, 24, 48, 24, 24, 6, 8, 24, 24, 6, 2, 24, 24, 8, 12, 48, 48, 24, 48, 24, 24, 24, 24, 8, 12, 12, 4, 24, 24, 48, 24, 24, 4, 3, 12, 12, 4, 8, 24, 8, 8, 1, 4, 1, 1};
    //Phase specific information
    private String phaseTag_local = "A2ORCBINCE";
    private int lc_local[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private int tc_local = 70;
    private int nxc_local = 1;
    private int nc_local = 69;
    private int np_local = 138;
    private int rcf_local[][] = {{2}, {2}, {2}, {2}, {2}, {3}, {3}, {3}, {3}, {3}, {3}, {3}, {3}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {4}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {5}, {6}, {6}, {6}, {6}, {6}, {6}, {6}, {6}, {6}, {6}, {6}, {6}, {6}, {7}, {7}, {7}, {7}, {7}, {7}, {8}, {8}, {8}, {9}, {1}};
    private int lcf_local[] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private int tcf_local = 70;
    private int nxcf_local = 1;
    private int ncf_local = 69;
    private double m_local[][] = {{1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}};
    private int wcv_local[][][] = {{{1, 2, 1}}, {{1, 2, 1}}, {{1, 2, 1}}, {{1, 2, 1}}, {{1, 2, 1}}, {{1,
        1, 2, 1, 2, 1}}, {{1, 1, 2, 1, 2, 1}}, {{1, 1, 2, 1, 2, 1}}, {{1,
        1, 2, 1, 2, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 3, 3, 1}}, {{1,
        1, 1, 1, 1, 1, 1, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 1, 1, 2, 1,
        1, 2, 2, 1, 1, 2, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1}}, {{1, 1, 3, 3, 3, 1, 3, 1}}, {{1, 4, 2, 4, 4, 1}}, {{1, 1,
        3, 3, 3, 1, 3, 1}}, {{1, 2, 2, 1, 1, 2, 2, 2, 2, 1}}, {{1, 1, 1, 2,
        1, 1, 2, 2, 1, 1, 2, 1}}, {{1, 4, 2, 2, 2, 4, 1}}, {{1, 4, 6, 4,
        1}}, {{1, 4, 2, 4, 4, 1}}, {{1, 2, 2, 1, 1, 4, 2, 2, 1}}, {{1, 2,
        2, 1, 1, 2, 2, 2, 2, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1}}, {{1, 2, 2, 1, 1, 2, 2, 2, 2, 1}}, {{1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 1, 1, 2, 1, 1, 2, 2, 1, 1, 2,
        1}}, {{1, 4, 2, 2, 2, 4, 1}}, {{1, 1, 4, 2, 4, 4, 2, 4, 4, 1, 4,
        1}}, {{1, 1, 1, 3, 1, 3, 3, 3, 1, 3, 3, 3, 1, 1, 3, 1}}, {{1, 1, 2,
        2, 1, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2, 1, 2, 2, 1}}, {{1, 1, 1, 1,
        2, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 2, 1}}, {{1,
        1, 4, 2, 2, 2, 4, 2, 2, 2, 4, 1, 4, 1}}, {{1, 1, 4, 4, 6, 4, 6, 1,
        4, 1}}, {{1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2,
        1, 1, 1, 2, 1}}, {{1, 1, 2, 2, 1, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2,
        1, 2, 2, 1}}, {{1, 1, 1, 3, 1, 3, 3, 3, 1, 3, 3, 3, 1, 1, 3,
        1}}, {{1, 1, 2, 2, 1, 1, 2, 2, 4, 1, 1, 2, 2, 4, 1, 2, 2, 1}}, {{1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1}}, {{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}}, {{1,
        1, 1, 1, 2, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 2,
        1}}, {{1, 1, 2, 2, 1, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2, 1, 2, 2,
        1}}, {{1, 1, 2, 2, 1, 1, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2, 1, 2, 2,
        1}}, {{1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 1,
        1, 1, 2, 1}}, {{1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
        1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 2,
        2, 2, 2, 1, 1, 1, 1, 2, 1}}, {{1, 1, 1, 2, 2, 1, 1, 1, 2, 2, 2, 2,
        2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2,
        2, 1, 1, 2, 2, 1}}, {{1, 1, 1, 1, 3, 1, 1, 1, 3, 3, 3, 3, 1, 1, 3,
        3, 3, 3, 3, 3, 1, 1, 1, 3, 3, 3, 3, 1, 1, 1, 3, 1}}, {{1, 2, 4, 1,
        2, 2, 2, 4, 4, 2, 2, 4, 4, 4, 4, 1, 2, 2, 2, 4, 4, 2, 4, 1}}, {{1,
        2, 2, 2, 1, 1, 1, 2, 2, 4, 4, 2, 2, 2, 2, 2, 2, 4, 4, 1, 1, 1, 2,
        2, 4, 4, 2, 2, 2, 1}}, {{1, 6, 3, 6, 6, 2, 6, 12, 3, 6, 6, 6,
        1}}, {{1, 2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1}}, {{1, 1, 1, 1, 1,
        2, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2,
        2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2,
        1}}, {{1, 2, 4, 1, 2, 2, 2, 4, 4, 2, 2, 4, 4, 4, 4, 1, 2, 2, 2, 4,
        4, 2, 4, 1}}, {{1, 1, 1, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1,
        1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 2, 2,
        1}}, {{1, 1, 1, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2,
        2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 2, 2, 1}}, {{1,
        6, 3, 6, 6, 2, 6, 12, 3, 6, 6, 6, 1}}, {{1, 2, 4, 1, 2, 4, 8, 4, 4,
        4, 8, 1, 2, 4, 8, 2, 4, 1}}, {{1, 1, 2, 4, 1, 2, 2, 2, 2, 4, 4, 4,
        1, 2, 2, 2, 2, 2, 4, 4, 4, 4, 4, 4, 1, 2, 2, 2, 2, 2, 4, 4, 4, 4,
        4, 4, 1, 2, 2, 2, 2, 4, 4, 4, 1, 2, 4, 1}}, {{1, 1, 2, 2, 2, 1, 1,
        1, 2, 2, 2, 2, 2, 4, 4, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4,
        4, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4, 4, 1, 1, 1, 2, 2, 2,
        2, 2, 4, 4, 1, 2, 2, 2, 1}}, {{1, 1, 6, 3, 6, 6, 6, 2, 3, 6, 6, 6,
        12, 2, 3, 6, 6, 6, 12, 3, 6, 6, 6, 1, 6, 1}}, {{1, 1, 3, 3, 3, 3,
        3, 3, 3, 6, 1, 1, 3, 3, 3, 3, 3, 6, 6, 6, 1, 1, 3, 3, 3, 3, 3, 6,
        6, 6, 3, 3, 3, 3, 3, 6, 1, 3, 3, 1}}, {{1, 1, 1, 1, 2, 2, 1, 1, 1,
        1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1,
        1, 1, 2, 2, 1}}, {{1, 1, 3, 3, 3, 3, 3, 3, 3, 6, 1, 1, 3, 3, 3, 3,
        3, 6, 6, 6, 1, 1, 3, 3, 3, 3, 3, 6, 6, 6, 3, 3, 3, 3, 3, 6, 1, 3,
        3, 1}}, {{1, 1, 1, 3, 3, 1, 3, 3, 3, 3, 3, 3, 3, 6, 1, 1, 3, 3, 3,
        3, 3, 3, 3, 3, 3, 3, 6, 6, 6, 6, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3,
        3, 3, 3, 6, 6, 6, 6, 6, 6, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 6,
        6, 6, 6, 1, 3, 3, 3, 3, 3, 3, 3, 6, 1, 1, 3, 3, 1}}, {{1, 8, 4, 12,
        12, 8, 24, 24, 2, 6, 6, 8, 24, 24, 8, 24, 24, 4, 12, 12, 8,
        1}}, {{1, 2, 6, 1, 3, 6, 6, 6, 6, 2, 6, 6, 6, 6, 6, 12, 12, 2, 2,
        3, 3, 6, 6, 6, 6, 6, 6, 12, 12, 2, 6, 6, 6, 6, 6, 12, 12, 1, 3, 6,
        6, 6, 6, 2, 6, 1}}, {{1, 1, 8, 4, 8, 12, 12, 4, 8, 12, 12, 24, 24,
        2, 6, 6, 8, 8, 24, 24, 24, 24, 2, 6, 6, 8, 8, 24, 24, 24, 24, 4, 8,
        12, 12, 24, 24, 4, 8, 12, 12, 1, 8, 1}}, {{1, 1}}};
    private int[][] lcv_local = {{3}, {3}, {3}, {3}, {3}, {6}, {6}, {6}, {6}, {8}, {4}, {8}, {8}, {12}, {16}, {8}, {6}, {8}, {10}, {12}, {7}, {5}, {6}, {9}, {10}, {16}, {10}, {16}, {12}, {7}, {12}, {16}, {20}, {24}, {14}, {10}, {24}, {20}, {16}, {18}, {32}, {32}, {24}, {20}, {20}, {24}, {48}, {40}, {32}, {24}, {30}, {13}, {36}, {48}, {24}, {40}, {40}, {13}, {18}, {48}, {60}, {26}, {40}, {80}, {40}, {80}, {22}, {46}, {44}, {2}};
    // Arrays to initialize with phase specific size
    private double[] Scu_local = new double[(tcf_local - nxcf_local)];
    private double[] Hcu_local = new double[(tcf_local - nxcf_local)];
    private double[] Gcu_local = new double[(tcf_local - nxcf_local)];
    private double[][] Scuu_local = new double[(tcf_local - nxcf_local)][(tcf_local - nxcf_local)];
    private double[][] Gcuu_local = new double[(tcf_local - nxcf_local)][(tcf_local - nxcf_local)];
    //private double utc[] = new double[lcf_local[tcdis_local - 1]];

    public A2ORCBINCE(String stdst[], double edis[], String eMatFile, double T_in, double xB_in) throws FileNotFoundException, IOException { // Constructor Method
        Print.f("*****A2ORCBINCE constructor type 1 called", 6);
        //Highest symmetry phase information
        setKbdis(kbdis_local);
        setTcdis(tcdis_local);
        setNxcdis(nxcdis_local); //  No of clusters realted to point cluters
        setNcdis(ncdis_local);//vj-2012-06-21
        setRcdis(rcdis_local);
        setUab(tcdis_local, rcdis_local);
        // Phase specific information
        setPhaseTag(phaseTag_local);
        setLc(lc_local);//List of clusters in case of broken symemtry(Ordered phases)per highest symmetry(disordred phase)cluster
        setTc(tc_local);
        setNxc(nxc_local);
        setNc(nc_local);
        setNP(np_local);
        setLcf(lcf_local);
        setTcf(tcf_local);
        setNxcf(nxcf_local);
        setNcf(ncf_local);
        setRcf(rcf_local);
        setM(m_local);
        setWcv(wcv_local);
        setLcv(lcv_local);
        setCMat("C:\\Users\\admin\\Dropbox\\Proj\\CVM\\SourceCodes\\expCVM.04.30\\data\\TransMat\\cmatA2ORCBIN.txt");
        setEmat(eMatFile);//vj-15-09-15 //setEmat("EmatA2ORCBIN.txt");
        // Parameters
        //double R_local = ((Math.abs(edis[0]) == 1) ? 1 : 8.3144);
        double R_local = 8.3144;
        setR(R_local); //  Universal gas constant
        setReferenceStateParameters(stdst);
        setEdis(edis);
        setMhdis(mdis_local);
        setMdis(mdis_local, kbdis_local);
        //setKbdis(kbdis_local);
        setT(T_in);
        genInitialValues(xB_in);//set u and update cluster variables as well
        setX(xB_in);
        // Initialize Arrays with phase specific size
        initScu(Scu_local);
        initHcu(Hcu_local);
        initGcu(Gcu_local);
        initScuu(Scuu_local);
        initGcuu(Gcuu_local);
        Print.f("****A2ORCBINCE constructor type 1 ended", 6);
    }

    private void setUab(int tcdis_In, int[] rcdis_In) {
        double uA[] = new double[tcdis_In];
        double uB[] = new double[tcdis_In];
        for (int itc = 0; itc < tcdis_In; itc++) {
            uA[itc] = Math.pow(-1.0, rcdis_In[itc]);
            uB[itc] = Math.pow(1.0, rcdis_In[itc]);
        }
        setUAB(uA, uB);
    }

    private void genInitialValues(double x) throws IOException {//Phase specific method
        double[] u_In = new double[(tcf_local)];
        for (int icf = 0; icf < (tcf_local - 1); icf++) {
            u_In[icf] = Math.pow((2 * x - 1), rcf_local[icf][0]);
        }
        u_In[tcf_local - 1] = x;
        setU(u_In);
    }

    public void randInit() throws IOException {//Phase specific method
        double x = getXB();
        double[] u_In = new double[(tcf_local)];
        for (int icf = 0; icf < (tcf_local - nxcf_local); icf++) {
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
