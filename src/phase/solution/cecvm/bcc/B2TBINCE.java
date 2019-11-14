package phase.solution.cecvm.bcc;

import utils.io.Print;
import java.io.FileNotFoundException;
import java.io.IOException;
//import debug.*;

public class B2TBINCE extends BCCTBINCE {
//Phase specific information

    private final String phaseTag_local = "B2mTBINCE";
    private final int lc_local[] = {1, 2, 2, 1, 2};
    private final int tc_local = 8;
    private final int nxc_local = 1;
    private final int nc_local = 7;
    private final int lcf_local[] = {1, 2, 2, 1, 2};
    private final int tcf_local = 8;
    private final int nxcf_local = 1;
    private final int ncf_local = 7;
    private final int rcf_local[][] = {{1, 1}, {2, 0}, {0, 2}, {2, 1}, {1, 2}, {2, 2}, {1, 0}, {0, 1}};
    private final int rcfaLocal[] = {1, 2, 0, 2, 1, 2, 1, 0};//No of site operators of alfa type
    private final int rcfbLocal[] = {1, 0, 2, 1, 2, 2, 0, 1};//No of site operators of beta type
    private final double m_local[][] = {{1.}, {0.5, 0.5}, {0.5, 0.5}, {1.}, {0.5, 0.5}};
    private final int wcv_local[][][] = {{{1, 1, 1, 1}}, {{1, 2, 1}, {1, 2, 1}}, {{1, 1, 2, 1, 2, 1}, {1, 1, 2, 1, 2, 1}}, {{1, 2, 2, 1, 1, 4, 2, 2, 1}}, {{1, 1}, {1, 1}}};
    private final int[][] lcv_local = {{4}, {3, 3}, {6, 6}, {9}, {2, 2}};
    private final double cmat_local[][][][] = {{{{0.25, 0., 0., 0., 0., 0., 0., -1., 0.75}, {-0.25, 0., 0., 0., 0., 0., -0.5, 0., 0.25}, {-0.25, 0., 0., 0., 0., 0., 0.5, 0.,
        0.25}, {0.25, 0., 0., 0., 0., 0., 0., 1., -0.25}}}, {{{0., 0.25,
        0., 0., 0., 0., 0.5, -1., 0.75}, {0., -0.25, 0., 0., 0., 0., 0.,
        0., 0.25}, {0., 0.25, 0., 0., 0., 0., -0.5, 1., -0.25}}, {{0., 0.,
        0.25, 0., 0., 0., -0.5, -1., 0.75}, {0., 0., -0.25, 0., 0., 0.,
        0., 0., 0.25}, {0., 0., 0.25, 0., 0., 0., 0.5,
        1., -0.25}}}, {{{0.25, 0.125, 0., -0.125, 0., 0., 0.125, -0.75,
        0.5}, {-0.25, 0.125, 0., 0.125, 0., 0., 0.375, -0.25,
        0.25}, {0., -0.125, 0., 0.125, 0., 0., -0.125, -0.25,
        0.25}, {-0.25, 0.125, 0., -0.125, 0., 0., -0.375, 0.25,
        0.}, {0., -0.125, 0., -0.125, 0., 0., 0.125, 0.25, 0.}, {0.25,
        0.125, 0., 0.125, 0., 0., -0.125, 0.75, -0.25}}, {{0.25, 0.,
        0.125, 0., -0.125, 0., -0.125, -0.75, 0.5}, {-0.25, 0., 0.125, 0.,
        0.125, 0., -0.375, -0.25, 0.25}, {0., 0., -0.125, 0., 0.125, 0.,
        0.125, -0.25, 0.25}, {-0.25, 0., 0.125, 0., -0.125, 0., 0.375,
        0.25, 0.}, {0., 0., -0.125, 0., -0.125, 0., -0.125, 0.25,
        0.}, {0.25, 0., 0.125, 0., 0.125, 0., 0.125,
        0.75, -0.25}}}, {{{0.25, 0.0625, 0.0625, -0.125, -0.125, 0.0625,
        0., -0.5, 0.3125}, {0., 0.0625, -0.0625, 0., 0.125, -0.0625,
        0.125, -0.25, 0.1875}, {0., -0.0625, 0.0625, 0.125,
        0., -0.0625, -0.125, -0.25, 0.1875}, {-0.25, 0.0625,
        0.0625, -0.125, 0.125, 0.0625, -0.25, 0., 0.0625}, {-0.25, 0.0625,
        0.0625, 0.125, -0.125, 0.0625, 0.25, 0.,
        0.0625}, {0., -0.0625, -0.0625, 0., 0., 0.0625, 0., 0.,
        0.0625}, {0., 0.0625, -0.0625, 0., -0.125, -0.0625, -0.125,
        0.25, -0.0625}, {0., -0.0625, 0.0625, -0.125, 0., -0.0625, 0.125,
        0.25, -0.0625}, {0.25, 0.0625, 0.0625, 0.125, 0.125, 0.0625, 0.,
        0.5, -0.1875}}}, {{{0., 0., 0., 0., 0., 0., 0.5, -1., 1.}, {0.,
        0., 0., 0., 0., 0., -0.5, 1., 0.}}, {{0., 0., 0., 0., 0.,
        0., -0.5, -1., 1.}, {0., 0., 0., 0., 0., 0., 0.5, 1., 0.}}}};
    private final int np_local = 8;
    // Arrays to initialize with phase specific size
    private double[] Scu_local = new double[(ncf_local)];
    private double[] Hcu_local = new double[(ncf_local)];
    private double[] Gcu_local = new double[(ncf_local)];
    private double[][] Scuu_local = new double[(ncf_local)][(ncf_local)];
    private double[][] Gcuu_local = new double[(ncf_local)][(ncf_local)];

    public B2TBINCE(String stdst[], double edis[], String eMatFile, double mdis[], double T_in, double xB_in) throws FileNotFoundException, IOException { // Constructor Method
        Print.f("b2mTBINcCE constructor method called ", 6);
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
        setEdis(edis);//vj-2014-07-12
        setMsdis(mdis);
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
        Print.f("b2mTBINcCE constructor method ended", 6);
    }

    private double[] genRandCF(double x) {//vj-2012-04-03:Generate random CF
        double[] u_In = new double[(tcf_local)];
        double siteOpAlfa, siteOpbeta;
        double LRO1 = x <= 0.5 ? 1.98 * x : 1.98 * (1 - x);
        siteOpAlfa = (2 * x - 1) - LRO1;
        siteOpbeta = (2 * x - 1) + LRO1;
        for (int icf = 0; icf < (tcf_local - 2); icf++) {
            u_In[icf] = Math.pow(siteOpAlfa, rcf_local[icf][0]) * Math.pow(siteOpbeta, rcf_local[icf][1]);
        }
        u_In[tcf_local - 2] = LRO1;
        u_In[tcf_local - 1] = x;
        return (u_In);
    }

    private void randInit(double x) throws IOException {//Phase specific method
        double[] u_In = genRandCF(x);
        setU(u_In);
    }

//    @Override
//    public void randInit() throws IOException {//Phase specific method
//        double x = getXB();
//        randInit(x);
//    }
    @Override
    public double[] getURand() {//Phase specific method
        double x = getXB();
        double[] u_In = genRandCF(x);
        return (u_In);
    }
}
