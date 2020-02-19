/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.cecvm;

import java.util.ArrayList;
import phase.GibbsModel;
import utils.io.Print;
import utils.io.Utils;
import utils.jama.Matrix;

/**
 *
 * @author admin
 */
public abstract class CECVM extends GibbsModel {

    //Following information to be read from GibbsModel
    String phaseTag; // Phase Name
    private int numComp;//Number of components
    private double T; //Temperature
    private double P; //Pressure
    private ArrayList<Double> x;// correlation functions 
    private double[] ec; //to be chnaged
    private double[] ev; //   Eci for each correlation function

    //Highest symmetry phase information: values to be set by respective Phase Class object for example BCCmTBINcCE
    int tcdis; //  No of total clusters
    int nxcdis; //  No of clusters realted to point cluters
    int ncdis; //  No of clusters excluding point clusters
    double[] mhdis; //Unmodified geometric multiplicities for each cluster//vj-2012-06-23
    int[] rcdis; //  No of sites for each cluster
    int[][] nijTable; //coefficients of multplicities icf the product of m*kb//vj-2012-06-20

    // Phase specific information: values to be set by respective phase object for example A2mTBINcCE
    int[] lc; //   List of clusters icf case of broken symemtry (Ordered phases)per highest symmetry (disordred phase) cluster*
    int tc; //total no of clusters: sum of lc
    int nxc; // total no of cluster related to point clusters
    int nc; //no of clusters excluding point clusters
    int[][][] rc; //No of sites for each cluster
    int[][] mh; //Unmodified geometric multiplicities for each cluster
    int tcfdis; //  
    int[] mcfdis; //  
    int[][] rcfdis; //
    int[][] lcf; //  List of no of correlation functions associated with each disordered cluster
    int tcf; //  No of total correlation functions : Sum of lcf
    int nxcf; //  No of total correlation functions realted to point cluters
    int ncf; //No of correlation functions exluding point correlation function
    int[][][][] rcf; // No of sites for each correlation function
    double[][][] mcf; //multiplicities icf case of broken symmetry
    int[][][] wcv; //  Weights of clauter variables for each cluster
    int[][] lcv; //  No of cluster variables for each cluster
    double[][][][] cMat; //  C-f for each cluster
    // Parameters
    double[] msdis; //Multiplicities for each cluster
    double[] kbdis; //   K-B Coefficients for each cluster
    private int np;//No of parameters to be optimized
    private double R;
    private double edis[]; //Eci for each correlation function
    double[][] ecMat; //  ECI vector transformation matrix for specific Phase

    String elementB; //Element B
    String elementA; //Element A//AJ-2012-28-03//new variables for new stdst implementation
    String dataBaseID; //Database type used
    String phaseID; //phase ID
    double G0B; // base energies for standard state
    double G0AT;
    double G0BT; //base energy derivatives with T
    //Macroscopic Parameters
    private double[] G0List; // Gibbs energy of the pure components
    private double[] Smcx;
    //private double[] G0TList; // Temp derivative of Gibbs energy of the pure components
    //private double[] G0PList; // pressure derivative of Gibbs energy of the pure components
    double Gc; //  Configurational Free energy
    private double H;   //Enthalpy
    double Hmc; //  Configurational CE enthalpy
    private double S;   //Entropy
    double Smc; //  Configurational CVM Entropy
    double[] Gcx; //  first derivative of Gc w.r.t. u
    double[] Hcx; //  first derivative of Smc w.r.t. u
    double[][] Gmcxx; //  Second derivative of Smc and Gc w.r.t. u
    double[][] Smcxx;
    private boolean isGMinimized = false;//To track if Gibbs energy function is minimized
    //Microscopic Parameters
    double[] u; //  Correlation functions (flattened) excluding point cluster(s)
    double[][] ut; //  unflattened list of Correlation functions (i.e. cluster wise)
    double[][][] cv; //  Cluster Variables for each cluster
    private double G;   //Free Energy 
    //private double Gm; //  Gibbs energy of mixing
    private double GT; //  first derivative of G w.r.t. T
    private double GP; //  first derivative of G w.r.t. P
    private double[] Gx; //  first derivative of G w.r.t. X
    private double[] GTx;//First derivative of G w.r.t. T and X
    private double[] GPx;//Second derivative of G w.r.t. P and X
    private double[][] Gxx;//Second derivative of G w.r.t. X
    private double[][] eMat; //equlibrium matrix
    private double[] cG; //contains emat dot product with Gx
    private double[] cT;//contains emat dot product with GxT
    private double[] cP;//contains emat dot product with GxP
    private double[][] cAB;//same as eMat matrix

// Setter Methods
    public void setEcdis(double[] ecdis) {
        this.ec = ecdis; //To change body of generated methods, choose Tools | Templates.
        //isGMinimized = false;
    }

    public void setEvdis(double[] evdis) {
        this.ev = evdis; //To change body of generated methods, choose Tools | Templates.
        //isGMinimized = false;
    }

    public void setTcdis(int tcdis_In) {
        this.tcdis = tcdis_In;
    }

    public void setNxcdis(int nxcdis_In) {
        this.nxcdis = nxcdis_In;
    }

    public void setNcdis(int ncdis_In) {
        this.ncdis = ncdis_In;
    }

    public void setMhdis(double[] mgdisIn) {
        this.mhdis = mgdisIn;
        isGMinimized = false;
    }

    public void setMsdis(double[] msdisIn) {//sets kbdis as well
        this.msdis = msdisIn;
        //Print.f("msdis:", msdis,0);
        setKbdis();
        //isGMinimized = false;
    }

    public void setKbdis() {//sets kbdis as well
        //Print.f("msdis:", msdis,0);
        double[] kbdis_local = new double[tcdis];
        double tempSum;
        for (int j = 0; j < tcdis; j++) {
            tempSum = 0.0;
            for (int i = 0; i < j; i++) {
                tempSum = tempSum + (msdis[i] * nijTable[i][j] * kbdis_local[i]);
            }
            kbdis_local[j] = (msdis[j] - tempSum) / msdis[j];
        }
        this.kbdis = kbdis_local;
        //isGMinimized = false;
    }

    public void setRcdis(int[] rcdis_In) {
        this.rcdis = rcdis_In;
    }

    public void setNijTable(int[][] nijTable) {
        this.nijTable = nijTable;
    }

    public void setLc(int[] lc_In) {
        this.lc = lc_In;
    }

    public void setTc(int tc_In) {
        this.tc = tc_In;
    }

    public void setNxc(int nxc_In) {
        this.nxc = nxc_In;
    }

    public void setNc(int nc_In) {
        this.nc = nc_In;
    }

    public void setRc(int[][][] rc_In) {
        this.rc = rc_In;
    }

    public void setMh(int[][] mh_In) {
        this.mh = mh_In;
        //isGMinimized = false;
    }

    public void setTcfdis(int tcfdis_In) {
        this.tcfdis = tcfdis_In;
    }

    public void setMcfdis(int[] mcfdis_In) {
        this.mcfdis = mcfdis_In;
    }

    public void setRcfdis(int[][] rcfdis_In) {
        this.rcfdis = rcfdis_In;
    }

    public void setLcf(int[][] lcf_In) {
        this.lcf = lcf_In;
    }

    public void setTcf(int tcf_In) {
        this.tcf = tcf_In;
    }

    public void setNxcf(int nxcf_In) {
        this.nxcf = nxcf_In;
    }

    public void setNcf(int ncf_In) {
        this.ncf = ncf_In;
    }

    public void setRcf(int[][][][] rcf_In) {
        this.rcf = rcf_In;
    }

    public void setMcf(double[][][] mcf_In) {
        this.mcf = mcf_In;
    }

    public void setWcv(int[][][] wcv_In) {
        this.wcv = wcv_In;
    }

    public void setLcv(int[][] lcv_In) {
        this.lcv = lcv_In;
    }

    public void setCMat(double[][][][] cmat_In) {//vj-14-11-28
        this.cMat = cmat_In;
    }

    public void setMdis(double[] mdisIn, double[] kbdisIn) {
        this.msdis = mdisIn;
        this.kbdis = kbdisIn;
        isGMinimized = false;
    }

//    public void setU(double[] u_In) {
//        u = new double[tcf];
//        for (int i = 0; i < u_In.length; i++) {
//            if ((u_In[i] < (-1.0)) || (u_In[i] > 1.0)) {
//                throw new ArithmeticException("Value of correlation function is out of range");
//            } else if (Math.abs(u_In[i]) < 1.0E-15) {
//                u_In[i] = 0.0;
//            } else {
//                u[i] = u_In[i];
//            }
//        }
//        //updateUt();
//        updateCV();
//    }
    public void updateCV() {
        //System.out.print("x:" + x);
        double CVN[][][] = new double[tcdis][][];
        for (int i = 0; i < tcdis; i++) {
            CVN[i] = new double[lc[i]][];
            for (int j = 0; j < lc[i]; j++) {
                CVN[i][j] = new double[lcv[i][j]];
            }
        }
        //System.out.print("tcf:" + tcf);
        for (int itc = 0; itc < tcdis; itc++) {
            for (int inc = 0; inc < lc[itc]; inc++) {
                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                    CVN[itc][inc][incv] = 0;
                    for (int itcf = 0; itcf < (tcf); itcf++) {
                        CVN[itc][inc][incv] = CVN[itc][inc][incv] + cMat[itc][inc][incv][itcf] * x.get(itcf);
                    }
                    //CVN[itc][inc][incv] = CVN[itc][inc][incv] + cMat[itc][inc][incv][tcf] * (1);//for empty cluster
                    //System.out.print("CVN[" + itc + "][" + inc + "][" + incv + "]:" + CVN[itc][inc][incv] + ",");
                }
                //System.out.println();
            }
            //System.out.println();
        }
        //System.out.println("done");
        cv = CVN;
    }// Closed Method updateCV()

//    public void setUAB(double[] uA_In, double[] uB_In) {
//        this.uAdis = uA_In;
//        this.uBdis = uB_In;
//    }
    @Override
    public double calG() {
        double GN;
        GN = calG0() + calGm();
        return (GN);
    }

    public double calG0() {
        G0List = getG0List();
        double G0N = 0.0;
        for (int iComp = ncf; iComp < tcf; iComp++) {
            G0N = G0N + (x.get(iComp) * G0List[iComp - ncf]);
        }
        return (G0N);
    }

    @Override
    public double calGm() {
        double GmcN = calGmc();
        double GmvN = calGmv();
        double GmN = GmcN + GmvN;
        return (GmN);
    }

    public double calGmc() { //vj-2012-07-18
        return (calHmc() - T * calSmc());
    }

    /**
     *
     * @return configurational enthalpy of mixing
     */
    public double calHmc() {//vj-2012-03-28//Enthalpy of mixing
        double HmcN = 0;
        for (int icf = 0; icf < ncf; icf++) {
            HmcN = HmcN + ec[icf] * x.get(icf);
        }
        Hmc = HmcN;
        return (HmcN);
    }

    public double calSmc() {//vj-2012-03-28//entropy of mixing
        //checkMinimize();
        double Sc_In = 0.0;
        for (int itc = 0; itc < tcdis; itc++) {
            double prtlScout = 0.0;
            for (int inc = 0; inc < lc[itc]; inc++) {
                double prtlScin = 0.0;
                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                    prtlScin = prtlScin + (wcv[itc][inc][incv] * cv[itc][inc][incv] * Math.log(cv[itc][inc][incv]));
                }
                prtlScout = prtlScout + (mh[itc][inc] * prtlScin);
            }
            Sc_In = Sc_In + (msdis[itc] * kbdis[itc] * prtlScout);
        }
        Smc = (-R * Sc_In);
        return (Smc);
    }

    private double calGmv() {
        double SmvN = calSmv();
        double HmvN = calHmv();
        double GmvN = HmvN - T * SmvN;
        return (GmvN);
    }

    private double calHmv() {
        return (0.0);
    }

    private double calSmv() {
        double SvN = 0;
        for (int icf = 0; icf < ncf; icf++) {
            SvN = SvN + ev[icf] * x.get(icf);
        }
        SvN = -SvN;
        return (SvN);
    }

    /**
     * Returns total derivatives of configurational Gibbs energy with cfs
     *
     * @return
     */
    public double[] calDGcx() {//vj-2012-03-16
        return (calGcx());
    }

    /**
     * Returns derivatives of configurational Gibbs energy with cfs
     *
     * @return
     */
    private double[] calGcx() {
        double[] GcxN = new double[tcf];
        double[] G0xN = calG0x();
        double[] GmcxN = calGmcx();
        for (int i = 0; i < tcf; i++) {
            GcxN[i] = G0xN[i] + GmcxN[i];
        }
        return (GcxN);
    }

    private double[] calDGmvx() {// to be written
        double[] GvxN = new double[tcf];
//        for (int itc = 0; itc < ncdis; itc++) {
//            //Gvx = Gvx + msdis[itc] * edis[ncdis + itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);//vj-2014-08-27
//            Gvx = Gvx + msdis[itc] * ev[itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);//vj-2014-08-27
//        }
        for (int i = 0; i < tcf; i++) {
            GvxN[i] = T + GvxN[i];
        }
        return (GvxN);
    }

    private double[] calGx() {//vj-2012-03-16
        double[] DGxN = new double[tcf];
        double[] DGcxN = calDGcx();
        double[] DGvxN = calDGmvx();
        for (int i = 0; i < tcf; i++) {
            DGxN[i] = DGcxN[i] + DGvxN[i];
        }
        return (DGxN);
    }

    @Override
    public double calDGT() {
        double GTN, G0TN, GmTN;
        G0TN = calG0T();
        GmTN = calGmT();
        GTN = G0TN + GmTN;
        return (GTN);
    }

    /**
     * Return temperature derivative of Gibbs reference energy
     *
     * @return
     */
    private double calG0T() {
        double[] G0TList = getG0TList();
        //Print.f("getG0TList:", G0TList, 0);
        //System.out.println("x:" + x);
        double G0TN = 0.0;
        for (int iComp = ncf; iComp < tcf; iComp++) {
            G0TN = G0TN + (x.get(iComp) * G0TList[iComp - ncf]);
        }
        return (G0TN);
    }

    private double calGmT() {
        double GmTN, GmcTN, GmvTN;
        GmcTN = calGmcT();
        GmvTN = calGmvT();
        GmTN = GmcTN + GmvTN;
        return (GmTN);
    }

    private double calGmcT() {
        double GmcTN;
        GmcTN = -calSmc();
        return GmcTN;
    }

    private double calGmvT() {
        double GmvTN = 0;
        for (int icf = 0; icf < ncf; icf++) {
            GmvTN = GmvTN + ev[icf] * x.get(icf);
        }
        GmvTN = T * GmvTN;
        return (GmvTN);
    }

    @Override
    public double calDGP() {
        double GPN, G0PN, GmPN;
        G0PN = calG0P();
        GmPN = calGmP();
        GPN = G0PN + GmPN;
        return (GPN);
    }

    /**
     * Return pressure derivative of Gibbs reference energy
     *
     * @return
     */
    public double calG0P() {// to be checked for pressure derivatives 
        double[] G0PList = getG0PList();
        double G0PN = 0.0;
        for (int iComp = 0; iComp < numComp; iComp++) {
            G0PN = G0PN + (x.get(iComp) * G0PList[iComp]);
        }
        return (G0PN);
    }

    private double calGmP() {
        double GmPN, GmcPN, GmvPN;
        GmcPN = calGmcP();
        GmvPN = calGmvP();
        GmPN = GmcPN + GmvPN;
        return (GmPN);
    }

    private double calGmcP() {
        double GmcPN = 0;
        return GmcPN;
    }

    private double calGmvP() {
        double GmvPN = 0;
        return GmvPN;
    }

    @Override
    public double[] calDGx() {
        double[] GxN = new double[tcf];
        double[] G0xN = calG0x();
        double[] GmxN = calGmx();
        for (int i = 0; i < tcf; i++) {
            GxN[i] = G0xN[i] + GmxN[i];
        }
        return (GxN);
    }

    private double[] calG0x() {
        double[] G0xN = new double[tcf];
        double[] G0N = getG0List();
        for (int i = ncf; i < tcf; i++) {
            G0xN[i] = G0N[i - ncf];
        }
        return (G0xN);
    }

    private double[] calGmx() {
        double[] GmxN = new double[tcf];
        double[] GmcxN, GmvxN;
        GmcxN = calGmcx();
        GmvxN = calGmvx();
        for (int i = 0; i < tcf; i++) {
            GmxN[i] = GmcxN[i] + GmvxN[i];
        }
        return (GmxN);
    }

    /**
     * Returns derivatives of configurational Gibbs energy of mixing with cfs
     *
     * @return
     */
    private double[] calGmcx() {
        double[] GmcxN = new double[tcf];
        double[] HmcxN = calHmcx();
        double[] SmcxN = calSmcx();
        //Print.f("HmcxN",HmcxN, 0);
        //Print.f("SmcxN",SmcxN, 0);
        for (int i = 0; i < tcf; i++) {
            GmcxN[i] = HmcxN[i] - T * SmcxN[i];
        }
        return (GmcxN);
    }

    /**
     * Returns derivatives of configurational enthalpy of mixing with cfs
     *
     * @return
     */
    private double[] calHmcx() {//to be written
        double[] HcxN = new double[tcf];
        System.arraycopy(ec, 0, HcxN, 0, ncf);
        return (HcxN);
    }

    /**
     * Returns derivatives of configurational entropy of mixing with cfs
     *
     * @return
     */
    private double[] calSmcx() {//to be checked
        double[] SmcN = new double[tcf];
        for (int icf = 0; icf < tcf; icf++) {//loop running over all cfs
            for (int itc = 0; itc < tcdis; itc++) {
                double prtlScuout = 0.0;
                for (int inc = 0; inc < lc[itc]; inc++) {
                    //Print.f("cv[itc][inc]:",cv[itc][inc], 0);
                    double prtlScuin = 0.0;
                    for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                        prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][icf] * Math.log(cv[itc][inc][incv]));
                    }
                    prtlScuout = prtlScuout + (mh[itc][inc] * prtlScuin);
                }
                SmcN[icf] = SmcN[icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
            }
            SmcN[icf] = (-R * SmcN[icf]);
        }
        Smcx = SmcN;
        return (SmcN);
    }

    private double[] calGmvx() {
        double[] GmvxN = new double[tcf];
        for (int i = 0; i < ncf; i++) {
            GmvxN[i] = T * ev[i];
        }
        return (GmvxN);
    }

    @Override
    public double[] calDGTx() {
        double[] GTxN = new double[tcf];
        double[] G0TxN = calG0Tx();
        double[] GmTxN = calGmTx();
        for (int i = 0; i < tcf; i++) {
            GTxN[i] = G0TxN[i] + GmTxN[i];
        }
        return (GTxN);
    }

    private double[] calG0Tx() {
        double[] G0TxN = new double[tcf];
        double[] G0TList = getG0TList();
        for (int i = ncf; i < tcf; i = i + 1) {
            G0TxN[i] = G0TList[i - ncf];
        }
        return (G0TxN);
    }

    private double[] calGmTx() {
        double[] GmTxN = new double[tcf];
        double[] GmcTxN, GmvTxN;
        GmcTxN = calGmcTx();
        GmvTxN = calGmvTx();
        for (int i = 0; i < tcf; i++) {
            GmTxN[i] = GmcTxN[i] + GmvTxN[i];
        }
        return (GmTxN);
    }

    private double[] calGmcTx() {
        double[] GmcTxN = new double[tcf];
        for (int i = 0; i < tcf; i = i + 1) {
            GmcTxN[i] = -Smcx[i];
        }
        return GmcTxN;
    }

    private double[] calGmvTx() {
        double[] GmvTxN = new double[tcf];
        System.arraycopy(ev, 0, GmvTxN, 0, ncf);
        return (GmvTxN);
    }

    @Override
    public double[] calDGPx() {
        double[] GPxN = new double[tcf];
        double[] G0PxN = calG0Px();
        double[] GmPxN = calGmPx();
        for (int i = 0; i < tcf; i++) {
            GPxN[i] = G0PxN[i] + GmPxN[i];
        }
        return (GPxN);
    }

    public double[] calG0Px() {
        double[] GP0xN = new double[tcf];
        return (GP0xN);
    }

    private double[] calGmPx() {
        double[] GmPxN = new double[tcf];
        double[] GmcPxN, GmvPxN;
        GmcPxN = calGmcPx();
        GmvPxN = calGmvPx();
        for (int i = 0; i < tcf; i++) {
            GmPxN[i] = GmcPxN[i] + GmvPxN[i];
        }
        return (GmPxN);
    }

    private double[] calGmcPx() {
        double[] GmcPxN = new double[tcf];
        return (GmcPxN);
    }

    private double[] calGmvPx() {
        double[] GmvPxN = new double[tcf];
        return (GmvPxN);
    }

    @Override
    public double[][] calDGxx() {
        double[][] GxxN = new double[tcf][tcf];
        double[][] G0xxN = calG0xx();
        double[][] GmxxN = calGmxx();
        for (int i = 0; i < tcf; i++) {
            for (int j = 0; j < tcf; j++) {
                GxxN[i][j] = G0xxN[i][j] + GmxxN[i][j];
            }
        }
        //Gxx = GxxN;
        return (GxxN);
    }

    public double[][] calG0xx() {
        double[][] G0xxN = new double[tcf][tcf];
        return (G0xxN);
    }

    private double[][] calGmxx() {
        double[][] GmxxN = new double[tcf][tcf];
        double[][] GmcxxN, GmvxxN;
        GmcxxN = calGmcxx();
        GmvxxN = calGmvxx();
        for (int i = 0; i < tcf; i++) {
            for (int j = 0; j < tcf; j++) {
                GmxxN[i][j] = GmcxxN[i][j] + GmvxxN[i][j];
            }
        }
        return (GmxxN);
    }

    public double[][] calGmcxx() {//2012-02-23: Modified
        double GmcxxN[][] = new double[tcf][tcf];
        calSmcxx(); //update Smcxx
        //Print.f("Smcxx:", Smcxx, 0);
        for (int jcf = 0; jcf < tcf; jcf++) {
            for (int icf = 0; icf < tcf; icf++) {
                GmcxxN[jcf][icf] = -(T * Smcxx[jcf][icf]);
            }
        }
        Gmcxx = GmcxxN;
        return (GmcxxN);
    }

    private double[][] calSmcxx() {//vj-2012-05-20
        double ScuuN[][] = new double[tcf][tcf];
        for (int jcf = 0; jcf < tcf; jcf++) {
            for (int icf = 0; icf < tcf; icf++) {
                ScuuN[jcf][icf] = 0.0;
                for (int itc = 0; itc < tcdis; itc++) {
                    double prtlScuout = 0.0;
                    for (int inc = 0; inc < lc[itc]; inc++) {
                        double prtlScuin = 0.0;
                        for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                            prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * (1 / cv[itc][inc][incv]));
                        }
                        prtlScuout = prtlScuout + (mh[itc][inc] * prtlScuin);
                    }
                    ScuuN[jcf][icf] = ScuuN[jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                }
                ScuuN[jcf][icf] = -(R * ScuuN[jcf][icf]);
                //System.out.print(Gcuu[jcf][icf]+",");
            }
        }
        Smcxx = ScuuN;
        return (ScuuN);
    }

    private double[][] calGmvxx() {//vj-2012-05-20
        double GmvxxN[][] = new double[tcf][tcf];
        return GmvxxN;
    }

    /**
     * This method extracts and sets parameters from the GibbsModel
     */
    public void setPhaseParam() {
        phaseTag = getPhaseTag();
        numComp = getNumComp();
        R = getR();
        T = getT();
        P = getP();
        x = getX();
    }

    /**
     * This module returns inverted phase matrix
     *
     * @return
     */
    private double[][] calEmat() {
        double[][] eMatN;
        double[][] phaseMatN = calPhaseMat();
        Matrix A = new Matrix(phaseMatN);
        Matrix B = A.inverse();
        Matrix C = B.getMatrix(0, tcf - 1, 0, tcf - 1);
        eMatN = C.getArray();
        return (eMatN);
    }

    /**
     * This Method returns Phase Matrix, Gxx matrix should be available
     */
    private double[][] calPhaseMat() {
        double[][] phaseMatN = new double[tcf + 1][tcf + 1];
        for (int i = 0; i < tcf; i++) {
            System.arraycopy(Gxx[i], 0, phaseMatN[i], 0, tcf);
        }
        for (int i = 0; i < numComp; i++) {//filling last column
            phaseMatN[tcf - numComp + i][tcf] = 1;
        }
        for (int i = 0; i < numComp; i++) {//filling last row
            phaseMatN[tcf][tcf - numComp + i] = 1;
        }
        return (phaseMatN);
    }

    private double[] calCG() {
        double[] cGN = new double[tcf];
        double tempSum;
        for (int i = 0; i < tcf; i++) {//loop over all the correlation functions
            tempSum = 0;
            for (int j = 0; j < tcf; j++) {
                tempSum = tempSum + eMat[i][j] * Gx[j];
            }
            cGN[i] = -tempSum;
        }
        return (cGN);
    }

    private double[] calCT() {
        double[] cGN = new double[tcf];
        double tempSum;
        for (int i = 0; i < tcf; i++) {//loop over all the correlation functions
            tempSum = 0;
            for (int j = 0; j < tcf; j++) {
                tempSum = tempSum + eMat[i][j] * GTx[j];
            }
            cGN[i] = -tempSum;
        }
        return (cGN);
    }

    private double[] calCP() {
        double[] cGN = new double[tcf];
        double tempSum;
        for (int i = 0; i < tcf; i++) {//loop over all the correlation functions
            tempSum = 0;
            for (int j = 0; j < tcf; j++) {
                tempSum = tempSum + eMat[i][j] * GPx[j];
            }
            cGN[i] = -tempSum;
        }
        return (cGN);
    }

    private double[][] calCAB() {
        double[][] cABN = eMat;
        return (cABN);
    }

    public void calGderivatives() {
        G = calG();
        GT = calDGT();
        GP = calDGP();
        Gx = calDGx();
        GTx = calDGTx();
        GPx = calDGPx();
        Gxx = calDGxx();
        eMat = calEmat();
        cG = calCG();
        cT = calCT();
        cP = calCP();
        cAB = calCAB();
        updateGE(G, GT, GP, Gx, GTx, GPx, Gxx, eMat, cG, cT, cP, cAB);
    }

    /**
     * For printing various parameters that defines a phase and few
     * thermodynamic calculations
     */
    @Override
    public void printPhaseInfo() {//vj-2012-03-20
        Utils.drawLine();
        //phaseTag = getPhaseTag();
        //numComp = getNumComp();
        //R = getR();
        //T = getT();
        //P = getP();
        //x = getX();

        Print.f("phaseTag:", phaseTag, 0);
        Print.f("number of components:", numComp, 0);
        Print.f("R:", R, 0);
        Print.f("T:", T, 0);
        Print.f("P:", P, 0);
        Print.f("x:", x.toString(), 0);

        Print.f("tcdis:" + tcdis + ", nxcdis:" + nxcdis + ", ncdis:" + ncdis, 0);
        Print.f("mhdis:", mhdis, 0);
        Print.f("kbdis:", kbdis, 0);
        Print.f("rcdis:", rcdis, 0);
        Print.f("nijTable:", nijTable, 0);

        Print.f("tc:" + tc + ", nxc:" + nxc + ", nc:" + nc, 0);
        Print.f("lc:", lc, 0);
        Print.f("rc:", rc, 0);

        Print.f("ec:", ec, 0);
        Print.f("ev:", ev, 0);
        //Print.f(edis.length, "Size of edis", 0);
        //Print.f("ec:", ec, 0);
        //Print.f(eMat, "eMat:", 0);
        //Print.f(ecMat, "ecMat:", 0);
        //Print.f("elementA:", elementA, 0);
        //Print.f("elementB:", elementB, 0);
        //Print.f("dataBaseID:", dataBaseID, 0);
        //Print.f("phaseID:", phaseID, 0);
        Print.f("tcfdis:" + tcfdis, 0);
        Print.f("mcfdis:", mcfdis, 0);
        Print.f("rcfdis:", rcfdis, 0);

        Print.f("tcf:" + tcf + ", nxcf:" + nxcf + ", ncf:" + ncf, 0);
        Print.f("lcf:", lcf, 0);
        //Print.f("rcf:", rcf, 0);
        Print.f("wcv:", wcv, 0);
        Print.f("lcv:", lcv, 0);
        //Print.f("x:" + x, 0);
        Print.f("cv:", cv, 0);

//        System.out.print("Cluster Variables:");
//        for (int i = 0; i < getTcdis(); i++) {
//            for (int j = 0; j < lc[i]; j++) {
//                for (int l = 0; l < lcv[i][j]; l++) {
//                    System.out.print(" cv[" + i + "][" + j + "][" + l + "]:" + cv[i][j][l]);
//                }
//                System.out.print(",");
//            }
//            System.out.println();
//        }
        //calG(u);
        //System.out.println("Free Energy:" + G);
        Utils.drawLine();
        //System.out.println("Smc:"+calSmc());
        //System.out.println(calHmc());
        System.out.println("G:" + calG());
        //System.out.println("Gm:" + calGm());
        // System.out.println("Gmc:" + calGmc());
        //System.out.println("Smc:" + calSmc());
        System.out.println("GT:" + calDGT());
        System.out.println("GP:" + calDGP());
        Print.f("Gx:", calDGx(), 0);
        Print.f("Gxx:", calDGxx(), 0);
        Print.f("GTx:", calDGTx(), 0);
        Print.f("GPx:", calDGPx(), 0);
        Print.f("EMat:", calEmat(), 0);
    }
}
