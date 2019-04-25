package phase.solution.cecvm;

import binutils.jama.Mat;
import binutils.jama.CholeskyDecomposition;
import binutils.jama.Matrix;
import binutils.stat.Utils;
import binutils.io.DataReader;
import binutils.io.Print;
import binutils.jama.LUDecomposition;
import database.stdst;
import java.io.*;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import phase.PHASEBINCE;

/**
 * @author: metallurgy setEcMat() on new format !
 *
 */
public abstract class CVMBINCE implements PHASEBINCE {

    //Highest symmetry phase information: values to be set by respective Phase Class object for example BCCmTBINcCE
    int tcdis; //  No of total clusters
    int nxcdis; //  No of clusters realted to point cluters
    int ncdis; //  No of clusters excluding point clusters
    double[] mhdis; //Unmodified geometric multiplicities for each cluster//vj-2012-06-23
    int[][] nij; //coefficients of multplicities icf the product of m*kb//vj-2012-06-20
    int[] rcdis; //  No of sites for each cluster
    double[] uAdis;
    double[] uBdis; // Value of correlation functions for pure A and pure B resp.
    // Phase specific information: values to be set by respective phase object for example A2mTBINcCE
    String phaseTag; // Phase Name
    int[] lc; //   List of clusters icf case of broken symemtry (Ordered phases)per highest symmetry (disordred phase) cluster*
    int tc; //total no of clusters: sum of lc
    int nxc; // total no of cluster related to point clusters
    int nc; //no of clusters excluding point clusters
    int[] lcf; //  List of no of correlation functions associated with each disordered cluster
    int tcf; //  No of total correlation functions : Sum of lcf
    int nxcf; //  No of total correlation functions realted to point cluters
    int ncf; //No of correlation functions exluding point correlation function
    int[] rcf; // No of sites for each correlation function
    double[][] m; //multiplicities icf case of broken symmetry
    int[][][] wcv; //  Weights of clauter variables for each cluster
    int[][] lcv; //  No of cluster variables for each cluster
    double[][][][] cMat; //  C-f for each cluster
    // Parameters
    double[] msdis; //Multiplicities for each cluster
    double[] kbdis; //   K-B Coefficients for each cluster
    private int np;//No of parameters to be optimized
    private double R;
    private double edis[]; //Eci for each correlation function
    double[] ecdis; //   Eci for each correlation function
    double[] evdis; //   Eci for each correlation function
    private double eMat[][];//  ECI vector transformation matrix for specific Phase
    double[][] ecMat; //  ECI vector transformation matrix for specific Phase
    double T; // Temperature
    double xB; //    Composition of one component(i.e. B)
    String elementB; //Element B
    String elementA; //Element A//AJ-2012-28-03//new variables for new stdst implementation
    String dataBaseID; //Database type used
    String phaseID; //phase ID
    double G0A;
    double G0B; // base energies for standard state
    double G0AT;
    double G0BT; //base energy derivatives with T
    //Macroscopic Parameters
    private double G;   //Free Energy 
    double Gc; //  Configurational Free energy
    private double H;   //Enthalpy
    double Hc; //  Configurational CE enthalpy
    private double S;   //Entropy
    double Sc; //  Configurational CVM Entropy
    double[] Gcu; //  first derivative of Gc w.r.t. u
    double[] Hcu; //  first derivative of Sc w.r.t. u
    double[] Scu; //  first derivative of Hc w.r.t. u
    private double Gu[];//First derivative of G w.r.t. u
    private double Guu[][];//Second derivative of G w.r.t. u
    double[][] Gcuu; //  Second derivative of Sc and Gc w.r.t. u
    double[][] Scuu;
    private boolean isGMinimized = false;//To track if Gibbs energy function is minimized
    //Microscopic Parameters
    double[] u; //  Correlation functions (flattened) excluding point cluster(s)
    double[][] ut; //  unflattened list of Correlation functions (i.e. cluster wise)
    double[][][] cv; //  Cluster Variables for each cluster

    public CVMBINCE() throws IOException {
        Print.f("*****PHASEmBINCE constructor method called", 6);
        Print.f("****PHASEmBINCE constructor method closed", 6);
    }
// Setter Methods

    public void setTcdis(int tcdis_In) {
        this.tcdis = tcdis_In;
    }

    public void setNxcdis(int nxcdis_In) {
        this.nxcdis = nxcdis_In;
    }

    public void setNcdis(int ncdis_In) {
        this.ncdis = ncdis_In;
    }

    public void setMdis(double[] mdisIn, double[] kbdisIn) {
        this.msdis = mdisIn;
        this.kbdis = kbdisIn;
        isGMinimized = false;
    }

    public void setMhdis(double[] mgdisIn) {
        this.mhdis = mgdisIn;
        isGMinimized = false;
    }

    public void setRcdis(int[] rcdis_In) {
        this.rcdis = rcdis_In;
    }

    public void setUAB(double[] uA_In, double[] uB_In) {
        this.uAdis = uA_In;
        this.uBdis = uB_In;
    }

    @Override
    public void setEmat(String infile) throws FileNotFoundException, IOException {
        String currentDirectory = System.getProperty("user.dir");
        String phaseIn = currentDirectory + "/../data/" + infile;
        //System.out.println(phaseIn);
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(phaseIn);
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Usage: ShowFile File");
        }
        DataInputStream in = new DataInputStream(fin);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String str;
        int dim = DataReader.getNData(phaseIn);
        int itr = 0, i = 0;
        double eTransMat_local[][] = new double[dim][dim];

        while ((str = br.readLine()) != null) {
            if (str.equalsIgnoreCase("#************************STOP#")) {
                break;
            }
            if (str.equalsIgnoreCase("#************************START#")) {
                itr++;
                str = br.readLine();
            }
            if (itr == 1) {
                if (str.trim().equalsIgnoreCase("")) {
                    //System.out.println("Input File for seteTrans has blank Spaces and Tabs !");
                } else {
                    String delims = ", *";
                    while (i < eTransMat_local.length) {
                        StringTokenizer st = new StringTokenizer(str);
                        for (int j = 0; j < eTransMat_local[i].length; j++) {
                            eTransMat_local[i][j] = Double.parseDouble(st.nextToken());
                        }
                        i++;
                        break;
                    }// Closed while i loop
                }// else Closed
            }// if itr==1 condition closed
        }// while loop on whole file Closed
        fin.close();
        this.eMat = eTransMat_local;
    }// Closed Method setEcMat()

//    public void setEcMat(String infile) throws FileNotFoundException, IOException {
//        FileInputStream fin = null;
//        try {
//            fin = new FileInputStream(infile);
//        } catch (FileNotFoundException e) {
//            System.out.println("File Not Found");
//        } catch (ArrayIndexOutOfBoundsException e) {
//            System.out.println("Usage: ShowFile File");
//        }
//        DataInputStream in = new DataInputStream(fin);
//        BufferedReader br = new BufferedReader(new InputStreamReader(in));
//        String str;
//        int dim = DataReader.getNData(infile);
//        int itr = 0;
//        int i = 0;
//        double[][] eTransMat_local = new double[dim][dim];
//        while ((str = br.readLine()) != null) {
//            if (str.equalsIgnoreCase("#************************STOP#")) {
//                break;
//            }
//            if (str.equalsIgnoreCase("#************************START#")) {
//                itr++;
//                str = br.readLine();
//            }
//            if (itr == 1) {
//                if (str.trim().equalsIgnoreCase("")) {
//                    System.out.println("Input File for setEcTrans has blank Spaces and Tabs !");
//                } else {
//                    String delims = ", *";
//                    while (i < eTransMat_local.length) {
//                        StringTokenizer st = new StringTokenizer(str);
//                        for (int j = 0; j < eTransMat_local[i].length; j++) {
//                            eTransMat_local[i][j] = Double.parseDouble(st.nextToken());
//                        }
//                        i++;
//                        break;
//                    }
//                }
//            }
//        }
//        fin.close();
//        this.ecMat = eTransMat_local;
//    }
    public void setPhaseTag(String phaseTag_In) {
        this.phaseTag = phaseTag_In;
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

    public void setLcf(int[] lcf_In) {
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

    public void setRcf(int[][][] rcf_In) {//vj-170107
        rcf = new int[tc];
        int index = 0;
        for (int i = 0; i < tcdis; i++) {//loop running over no of clusters
            for (int j = 0; j < lc[i]; j++) {
                rcf[index] = 0;
                for (int isl = 0; isl < rcf_In[i][j].length; isl++) {//loop over no of sublatices
                    rcf[index] = rcf[index] + rcf_In[i][j][isl];
                }
                //System.out.println("rcf[index]:" + rcf[index]);
                index = index + 1;
            }
        }
    }

    public void setRcf(int[][] rcf_In) {//vj-170107
        rcf = new int[tc];
        for (int itc = 0; itc < tc; itc++) {//loop running over no of clusters
            rcf[itc] = 0;
            for (int isl = 0; isl < rcf_In[itc].length; isl++) {//loop over no of sublatices
                rcf[itc] = rcf[itc] + rcf_In[itc][isl];
            }
        }
    }

    public void setM(double[][] m_In) {
        this.m = m_In;
    }

    public void setWcv(int[][][] wcv_In) {
        this.wcv = wcv_In;
    }

    public void setLcv(int[][] lcv_In) {
        this.lcv = lcv_In;
    }

    public void setCMat(double[][][][] cmat_In) throws FileNotFoundException, IOException {//vj-14-11-28
        this.cMat = cmat_In;
    }

    public void setCMat(String infile) throws FileNotFoundException, IOException {
        double[][][][] cMatrix_local;
        cMatrix_local = new double[tcdis][][][];
        for (int itc = 0; itc < tcdis; itc++) {
            cMatrix_local[itc] = new double[lc[itc]][][];
            for (int inc = 0; inc < lc[itc]; inc++) {
                cMatrix_local[itc][inc] = new double[lcv[itc][inc]][];
                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                    cMatrix_local[itc][inc][incv] = new double[tcf + 1];
                }
            }
        }
        Reader inp = new FileReader(infile);
        StreamTokenizer tstream = new StreamTokenizer(inp);
        tstream.parseNumbers();
        tstream.eolIsSignificant(true);
        tstream.nextToken();
        for (int itc = 0; itc < tcdis; itc++) {
            for (int inc = 0; inc < lc[itc]; inc++) {
                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                    for (int icf = 0; icf < (tcf + 1); icf++) {
                        cMatrix_local[itc][inc][incv][icf] = tstream.nval;
                        tstream.nextToken();
                        tstream.nextToken();
                    }
                    //System.out.println(itc+","+inc+","+incv);
                    //Print.f("cmat:",cMatrix_local[itc][inc][incv],0);
                }
            }
        }
        cMat = cMatrix_local;
    }

    @Override
    public void setNP(int np_In) {//vj-2012-03-16
        this.np = np_In;
    }

    @Override
    public void setR(double R_In) {
        this.R = R_In;
        isGMinimized = false;
    }

    @Override
    // public void setEdis(double[] edis_In) {
    //    this.edis = edis_In;//vj-2014-07-12
    //    isGMinimized = false;
    //}
    public void setEdis(double[] edis_In) {//2014-07-16: arraycopy() to be used instead of assignment
        //System.out.println(edis_In.length);
        //System.out.println(ncdis);
        this.edis = edis_In;
        double ecdis_local[] = new double[ncdis];
        double evdis_local[] = new double[ncdis];
        System.arraycopy(edis_In, 0, ecdis_local, 0, ncdis);
        for (int j = 0; j < ncdis; j++) {
            evdis_local[j] = edis_In[j + ncdis];
        }
        //System.arraycopy(edis_In, 0, evdis_local, ncdis - 1, ncdis);
        this.ecdis = ecdis_local;
        this.evdis = evdis_local;
        isGMinimized = false;
    }

    public void setMsdis(double[] msdisIn) throws IOException {//sets kbdis as well
        this.msdis = msdisIn;
        double[] kbdis_local = new double[tcdis];
        double tempSum = 0;
        //double[] mkb_local = new double[tcdis];
//        for (int j = 0; j < tcdis; j++) {
//            kbdis_local[j] = 0;
//            for (int i = 0; i < tcdis; i++) {
//                kbdis_local[j] = kbdis_local[j] + msdisIn[i] * nij[j][i];
//            }
//            kbdis_local[j] = kbdis_local[j] / msdisIn[j];
//        }
        for (int j = 0; j < tcdis; j++) {
            tempSum = 0.0;
            for (int i = 0; i < j; i++) {
                tempSum = tempSum + (msdisIn[i] * nij[i][j] * kbdis_local[i]);
            }
            kbdis_local[j] = (msdisIn[j] - tempSum) / msdisIn[j];
        }
        setKbdis(kbdis_local);
        isGMinimized = false;
    }

    public void setKbdis(double[] kbdis_In) {
        this.kbdis = kbdis_In;
    }

    public void setNij(int[][] mCoeffInmkbIn) {
        this.nij = mCoeffInmkbIn;
    }

    @Override
    public void setT(double T_In) throws IOException {//vj-2012-03-16
        this.T = T_In;
        updateStdst();
        isGMinimized = false;
    }

    @Override
    public void setX(double xB_In) {//vj-2012-03-16
        this.xB = xB_In;
        isGMinimized = false;
    }

    @Override
    public void setReferenceStateParameters(String[] stdst_In) {
        this.phaseID = stdst_In[0];
        this.elementA = stdst_In[1];
        this.elementB = stdst_In[2];
        this.dataBaseID = stdst_In[3];
    }

    public void updateStdst() throws IOException {
        G0A = stdst.get(dataBaseID, elementA, phaseID, T, 0);
        G0B = stdst.get(dataBaseID, elementB, phaseID, T, 0);
        G0AT = stdst.get(dataBaseID, elementA, phaseID, T, 1);
        G0BT = stdst.get(dataBaseID, elementB, phaseID, T, 1);
    }

    public void setU(double[] u_In) throws IOException {
        u = new double[tcf];
        for (int i = 0; i < u_In.length; i++) {
            if ((u_In[i] < (-1.0)) || (u_In[i] > 1.0)) {
                throw new ArithmeticException("Value of correlation function is out of range");
            } else if (Math.abs(u_In[i]) < 1.0E-15) {
                u_In[i] = 0.0;
            } else {
                u[i] = u_In[i];
            }
        }
        updateUt();
        updateCV();
    }

    private void updateUt() {//convert flattened correlation function list to two dimensional Cluster wise list
        int counter = 0;
        double ut_In[][] = new double[tcdis][];
        for (int itc = 0; itc < (tcdis); itc++) {
            ut_In[itc] = new double[lcf[itc]];
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                ut_In[itc][ilcf] = u[counter];
                //System.out.println(its + ":" + itc + ":" + ilcf + ":" + ut_In[itc][ilcf]);
                counter = counter + 1;
            }
        }
        ut = ut_In;
    }

    private void updateCV() {
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
                        CVN[itc][inc][incv] = CVN[itc][inc][incv] + cMat[itc][inc][incv][itcf] * u[itcf];
                    }
                    CVN[itc][inc][incv] = CVN[itc][inc][incv] + cMat[itc][inc][incv][tcf] * (1);//for empty cluster
                    //System.out.print("CVN[" + itc + "][" + inc + "][" + incv + "]:" + CVN[itc][inc][incv] + ",");
                }
                //System.out.println();
            }
            //System.out.println();
        }
        //System.out.println("done");
        cv = CVN;
    }// Closed Method updateCV()

    public void initGcu(double[] Gcu_In) {//To initialize array size
        this.Gcu = Gcu_In;
    }

    public void initHcu(double[] Hcu_In) {//To initialize array size
        this.Hcu = Hcu_In;
    }

    public void initScu(double[] Scu_In) {//To initialize array size
        this.Scu = Scu_In;
    }

    public void initGcuu(double[][] Gcuu_In) {//To initialize array size
        this.Gcuu = Gcuu_In;
    }

    public void initScuu(double[][] Scuu_In) {//To initialize array size
        this.Scuu = Scuu_In;
    }

    public int getTcdis() {
        return (tcdis);
    }

    public int getNxcdis() {
        return (nxcdis);
    }

    public int getNcdis() {
        return (ncdis);
    }

    public int[] getRcdis() {
        return (rcdis);
    }

    @Override
    public String getPhaseTag() {
        return (phaseTag);
    }

    public int[] getLc() {
        return (lc);
    }

    public int[] getLcf() {
        return (lcf);
    }

    public int getTcf() {
        return (tcf);
    }

    public int getNxcf() {
        return (nxcf);
    }

    public int getNcf() { // ncf = no. of independent correlation func.
        //return (ncf);
        return (ncf);
    }

    public double[][] getM() { //AJ-2012-03-27
        return (m);
    }

    public int[][] getNcv() {
        return (lcv);
    }

    @Override
    public int getNP() {//vj-2012-03-16
        return (np);
    }

    @Override
    public double getR() {
        return (this.R);
    }

    @Override
    public void getEdis(double[] edis_out) {
        System.arraycopy(edis, 0, edis_out, 0, edis.length);
    }

    public void getMdis(double[] mdis_out) {//vj-2012-06-20
        System.arraycopy(msdis, 0, mdis_out, 0, msdis.length);
    }

    public void getKbdis(double[] kbdis_out) {//vj-2012-06-20
        System.arraycopy(kbdis, 0, kbdis_out, 0, kbdis.length);
    }

    public int[][] getMCoeffInmkb() {//to be changed
        return (nij);
    }

    @Override
    public double getT() {
        return (this.T);
    }

    @Override
    public double getX() {
        return (this.xB);
    }

    public double getXB() {
        return (this.xB);
    }

    @Override                           //added mani: 05-03-2013
    public String getPhaseId() {
        return (phaseID);
    }

    @Override
    public double getG0A() {
        return (this.G0A);
    }

    @Override
    public double getG0B() {
        return (this.G0B);
    }

    private double getG0AT() {
        return (this.G0AT);
    }

    private double getG0BT() {
        return (this.G0BT);
    }

    public double[] getU() {//to be changed
        return (this.u);
    }

    public double[][] getUt() {//to be changed
        return (this.ut);
    }

    public double[][][] getCV() {//to be changed
        return (this.cv);
    }

    @Override
    public void getETrans(double[][] emat_out) {
        for (int i = 0; i < eMat.length; i++) {
            System.arraycopy(eMat[i], 0, emat_out[i], 0, eMat[0].length);
        }
    }

    public double[][] getEcTrans() {//to be changed
        return (this.ecMat);
    }

    private double getHc() {
        return (this.Hc);
    }

    private double getSc() {
        return (this.Sc);
    }

    private double getGc() {
        return (this.Gc);
    }

//Abstract Methods
///////////////Pure Partial Derivatives/////////////////////////////////////////
///////////////Zero Order Derivatives///////////////////////////////////////////
    private double calHmv() {
        return (0.0);
    }

    private double calSmv() {
        double SvN = 0;
        for (int itc = 0; itc < ncdis; itc++) {
            double Svin = 0;
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                Svin = Svin + m[itc][ilcf] * (ut[itc][ilcf]);
            }
            //SvN = SvN + msdis[itc] * edis[ncdis + itc] * (Svin - (1 - xB) * uAdis[itc] - (xB) * uBdis[itc]);//vj-2014-08-27
            SvN = SvN + msdis[itc] * evdis[itc] * (Svin - (1 - xB) * uAdis[itc] - (xB) * uBdis[itc]);//vj-2014-08-27
        }
        SvN = -SvN;
        return (SvN);
    }

    private double calGmv() {
        double SmvN = calSmv();
        double HmvN = calHmv();
        double GmvN = HmvN - T * SmvN;
        return (GmvN);
    }

    @Override
    public double calHm() throws IOException {//vj-2012-07-19
        checkMinimize();
        double HmcN = calHmc();
        double HmvN = calHmv();
        double HmN = HmcN + HmvN;
        return (HmN);
    }

    @Override
    public double calSm() throws IOException {//vj-2012-07-18
        checkMinimize();
        double SmcN = calSmc();
        double SmvN = calSmv();
        double SmN = SmcN + SmvN;
        //System.out.println("SmcN:" + SmcN + ", SmvN:" + SmvN + ", SmN:" + SmN);
        return (SmN);
    }

    @Override
    public double calH() throws IOException {
        checkMinimize();
        double H0N = calH0();
        double HmN = calHm();
        System.out.println("H0:" + H0N + ", HmN:" + HmN);
        H = H0N + HmN;
        return (H);
    }

    private double calH0() {//vj-2012-07-19
        double H0N = (1 - xB) * (G0A - T * G0AT) + xB * (G0B - T * G0BT);
        return (H0N);
    }

    @Override
    public double calS() throws IOException {//VJ-2012-06-22
        checkMinimize();
        double S0N = calS0();
        double SmN = calSm();
        System.out.println(getPhaseTag() + ".S0:" + S0N + ", SmN:" + SmN);
        S = S0N + SmN;
        return (S);
    }

    private double calS0() {//vj-2012-07-19
        double S0N = -((1 - xB) * G0AT + xB * G0BT);
        return (S0N);
    }

///////////////First Order Derivatives//////////////////////////////////////////
    private double[] calGu() {//vj-2012-03-16
        double[] GcuN = calGcu();
        double[] GvuN = calGvu();
        Gu = Mat.add(GcuN, GvuN);
        return (Gu);
    }

    private double[] calGvu() {
        double GvuN[] = new double[ncf];
        int uIndex = 0;
        for (int itc = 0; itc < ncdis; itc++) {
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                //GvuN[uIndex] = T * msdis[itc] * edis[ncdis + itc] * m[itc][ilcf];//vj-2014-08-27
                GvuN[uIndex] = T * msdis[itc] * evdis[itc] * m[itc][ilcf];//vj-2014-08-27
                uIndex = uIndex + 1;
            }
        }
        return (GvuN);
    }

    public double[] calGcu() {// throws IOException {//2012-02-23(VJ): Added
        calScu();
        calHcu();
        for (int icf = 0; icf < (ncf); icf++) {
            Gcu[icf] = Hcu[icf] - T * Scu[icf];
        }
        return (Gcu);
    }

    private double[] calHcu() {//2012-03-28(VJ): Added
        double HcuN[] = new double[ncf];
        int uIndex = 0;
        for (int itc = 0; itc < (tcdis - nxcdis); itc++) {
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                HcuN[uIndex] = mhdis[itc] * ecdis[itc] * m[itc][ilcf];
                uIndex = uIndex + 1;
            }
        }
        Hcu = HcuN;
        return (Hcu);
    }

    private double[] calScu() {//vj-2012-05-20
        for (int icf = 0; icf < ncf; icf++) {
            Scu[icf] = 0.0;
            for (int itc = 0; itc < tcdis; itc++) {
                double prtlScuout = 0.0;
                for (int inc = 0; inc < lc[itc]; inc++) {
                    double prtlScuin = 0.0;
                    for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                        prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][icf] * Math.log(cv[itc][inc][incv]));
                    }
                    prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                }
                Scu[icf] = Scu[icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
            }
            Scu[icf] = (-R * Scu[icf]);
        }
        return (Scu);
    }

    private double[] calHu() { //To be tested
        double[] HuN = new double[ncf];
        double[] GTuN = calGTu();
        for (int itc = 0; itc < ncf; itc++) {
            HuN[itc] = -GTuN[itc] * T;
        }
        return (HuN);
    }

    private double[] calSu() { //VJ-2012-06-22
        double[] SuN = new double[ncf];
        double[] GTuN = calGTu();
        for (int itc = 0; itc < ncf; itc++) {
            SuN[itc] = -GTuN[itc];
        }
        return (SuN);
    }

    private double calDSmvx() {//vj-2013-04-20//to be checked
        double Gvx = 0;
        for (int itc = 0; itc < ncdis; itc++) {
            //Gvx = Gvx + msdis[itc] * edis[ncdis + itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);//vj-2014-08-27
            Gvx = Gvx + msdis[itc] * evdis[itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);//vj-2014-08-27
        }
        return (-Gvx);
    }

    private double calSmx() throws IOException {//vj-2013-04-20
        double DSmcxN = calDSmcx();
        double DSmvxN = calDSmvx();
        double DSmxN = DSmcxN + DSmvxN;
        return (DSmxN);
    }

    public double calDSmcx() throws IOException {//vj-2013-04-20
        checkMinimize();
        double DSmcxN = 0.0;
        double[][] GcuuN = calGcuu();
        double[] GcxuN = calGcxu();
        double DuxN[] = calDux(GcuuN, GcxuN);
        double SmcxN = calSmcx();//method without CV update
        double ScuN[] = calScu();//method without CV update
        for (int i = 0; i < ncf; i++) {
            DSmcxN = DSmcxN + ScuN[i] * DuxN[i];
        }
        DSmcxN = DSmcxN + SmcxN;
        return DSmcxN;
    }

    private double[] calDGve() {
        double Gve[] = new double[ncdis];
        for (int itc = 0; itc < ncdis; itc++) {
            Gve[itc] = 0;
            double Gvin = 0;
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                Gvin = Gvin + m[itc][ilcf] * (ut[itc][ilcf]);
            }
            Gve[itc] = T * msdis[itc] * (Gvin - (1 - xB) * uAdis[itc] - (xB) * uBdis[itc]);//AJ-3-4-2012
        }
        return (Gve);
    }

    private double[] calGe() throws IOException {//vj-2012-03-16
        double[] DGceN = calDGce();
        double[] DGveN = calDGve();
        double[] DGeN = new double[np];
        System.arraycopy(DGceN, 0, DGeN, 0, getNcdis());
        System.arraycopy(DGveN, 0, DGeN, getNcdis(), getNcdis());
        return (DGeN);
    }

    public double[] calDGce() throws IOException, ArithmeticException {//vj-2012-03-16
        checkMinimize();
        return (calGce());
    }

    private double[] calGce() {//without CV update
        double[] Gce = new double[tcdis - nxcdis];
        double[] Hce = calHce();
        double[] Sce = calSce();
        for (int itc = 0; itc < (tcdis - nxcdis); itc++) {
            Gce[itc] = Hce[itc] - T * Sce[itc];
        }
        return (Gce);
    }

    private double[] calHce() {//Need to be checked for ordred phases
        double Hce[] = new double[tcdis - nxcdis];
        for (int itc = 0; itc < tcdis - nxcdis; itc++) {
            Hce[itc] = 0;
            double Hcin = 0;
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                Hcin = Hcin + m[itc][ilcf] * (ut[itc][ilcf]);
            }
            //Hce[itc] = msdis[itc] * (Hcin - (1 - xB) * Math.pow(-1, rcdis[itc]) - (xB) * Math.pow(1, rcdis[itc]));
            Hce[itc] = mhdis[itc] * (Hcin - (1 - xB) * Math.pow(-1, rcdis[itc]) - (xB) * Math.pow(1, rcdis[itc]));
        }
        return (Hce);
    }

    private double[] calSce() {
        double Sce[] = new double[tcdis - nxcdis];
        for (int itc = 0; itc < (tcdis - nxcdis); itc++) {
            Sce[itc] = 0;
        }
        return (Sce);
    }

    private double[] calHe() throws IOException {//AJ-2012-03-27
        double HeN[] = new double[np];
        double GeN[] = calGe();
        double DGeTN[] = calDGeT();
        for (int itc = 0; itc < np; itc++) {
            HeN[itc] = GeN[itc] - T * DGeTN[itc];
        }
        return (HeN);
    }

    private double[] calSe() throws IOException {//VJ-2012-06-22
        double SeN[] = new double[np];
        double GeN[] = calGe();
        double DGeTN[] = calDGeT();
        System.arraycopy(DGeTN, 0, SeN, 0, np);
        return (SeN);
    }
///////////////Second Order Derivatives/////////////////////////////////////////

    private double[][] calGuu() {//vj-2012-03-16
        double[][] GcuuN = calGcuu();
        Guu = GcuuN;
        return (Guu);
    }

    public double[][] calGcuu() {//2012-02-23: Modified
        calScuu(); //update Scuu
        for (int jcf = 0; jcf < ncf; jcf++) {
            for (int icf = 0; icf < ncf; icf++) {
                Gcuu[jcf][icf] = -(T * Scuu[jcf][icf]);
            }
        }
        return (Gcuu);
    }

    private double[][] calScuu() {//vj-2012-05-20
        double ScuuN[][] = new double[ncf][ncf];
        for (int jcf = 0; jcf < ncf; jcf++) {
            for (int icf = 0; icf < ncf; icf++) {
                ScuuN[jcf][icf] = 0.0;
                for (int itc = 0; itc < tcdis; itc++) {
                    double prtlScuout = 0.0;
                    for (int inc = 0; inc < lc[itc]; inc++) {
                        double prtlScuin = 0.0;
                        for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                            prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * (1 / cv[itc][inc][incv]));
                        }
                        prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                    }
                    ScuuN[jcf][icf] = ScuuN[jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                }
                ScuuN[jcf][icf] = -(R * ScuuN[jcf][icf]);
                //System.out.print(Gcuu[jcf][icf]+",");
            }
        }
        Scuu = ScuuN;
        return (Scuu);
    }

    private double[] calGxu() {//vj-2012-03-16
        double[] GcxuN = calGcxu();
        return (GcxuN);
    }

    double[] calGcxu() {//vj-2012-05-20
        double GcxBuN[] = new double[ncf];
        for (int icf = 0; icf < ncf; icf++) {
            GcxBuN[icf] = 0.0;
            for (int itc = 0; itc < tcdis; itc++) {
                double prtlScuout = 0.0;
                for (int inc = 0; inc < lc[itc]; inc++) {
                    double prtlScuin = 0.0;
                    for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                        prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][icf] * (1 / cv[itc][inc][incv]));
                    }
                    prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                }
                GcxBuN[icf] = GcxBuN[icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
            }
            GcxBuN[icf] = (R * T * GcxBuN[icf]);
        }
        return GcxBuN;
    }

    private double calGxx() {//vj-2012-03-16
        double GcxxN = calGcxx();
        return (GcxxN);
    }

    double calGcxx() {//vj-2012-05-20
        double GcxxN;
        GcxxN = 0.0;
        for (int itc = 0; itc < tcdis; itc++) {
            double prtlScuout = 0.0;
            for (int inc = 0; inc < lc[itc]; inc++) {
                double prtlScuin = 0.0;
                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                    prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * (1 / cv[itc][inc][incv]));
                }
                prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
            }
            GcxxN = GcxxN + (msdis[itc] * kbdis[itc] * prtlScuout);
        }
        GcxxN = (R * T * GcxxN);
        return GcxxN;
    }

    private double[] calGTu() {//vj-2012-03-16
        double[] GcTuN = calGcTu();
        double[] GvTuN = calGvTu();
        double[] GTuN = Mat.add(GcTuN, GvTuN);
        return (GTuN);
    }

    private double[] calGvTu() {
        double GvTuN[] = new double[ncf];
        int uIndex = 0;
        for (int itc = 0; itc < ncdis; itc++) {
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                //GvTuN[uIndex] = msdis[itc] * edis[ncdis + itc] * m[itc][ilcf];//vj-2014-08-27
                GvTuN[uIndex] = msdis[itc] * evdis[itc] * m[itc][ilcf];//vj-2014-08-27
                uIndex = uIndex + 1;
            }
        }
        return (GvTuN);
    }

    double[] calGcTu() {//2012-01-14:Added new Method
        double GcTu[] = new double[ncf];
        for (int icf = 0; icf < (ncf); icf++) {
            GcTu[icf] = -Scu[icf];
        }
        return (GcTu);
    }

    private double calGTx() {//vj-2012-03-16
        double GcTxN = calGcTx();
        double GvTxN = calGvTx();
        double GTxN = Mat.add(GcTxN, GvTxN);
        return (GTxN);
    }

    private double calGvTx() {
        double Gvx = 0;
        for (int itc = 0; itc < ncdis; itc++) {
            //Gvx = Gvx + msdis[itc] * edis[ncdis + itc] * (-(-1.0) * uAdis[itc] - (1.0) * uBdis[itc]);//vj-2014-08-27
            Gvx = Gvx + msdis[itc] * evdis[itc] * (-(-1.0) * uAdis[itc] - (1.0) * uBdis[itc]);//vj-2014-08-27
        }
        return (Gvx);
    }

    double calGcTx() {//2012-01-14:Added new Method //AJ-2012-03-28
        return ((-calSmcx() - G0AT + G0BT));
    }

    private double[][] calGeu() {//AJ-2012-03-27 Need to be checked for Ordred phases
        double[][] GceuN = calGceu();
        double[][] GveuN = calGveu();
        double[][] GeuN = new double[ncf][np];
        for (int itc = 0; itc < ncf; itc++) {
            System.arraycopy(GceuN[itc], 0, GeuN[itc], 0, getNcdis());
            System.arraycopy(GveuN[itc], 0, GeuN[itc], getNcdis(), getNcdis());
        }
        return (GeuN);
    }

    public double[][] calGveu() {//AJ-2012-03-27 Need to be checked for Ordred phases
        double[][] Gveu = new double[ncf][ncdis];
        //prnt.Array(Gceu,"Gceu:");
        int uIndex = 0;
        for (int itc = 0; itc < ncdis; itc++) {
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                for (int itc2 = 0; itc2 < (ncdis); itc2++) {
                    Gveu[uIndex][itc2] = ((itc2 == itc) ? T * msdis[itc] * m[itc][ilcf] : 0);
                }
                uIndex = uIndex + 1;
            }
        }
        //prnt.Array(Gceu, "Gceu:");
        return (Gveu);
    }

    double[][] calGceu() {//Need to be checked for Ordred phases
        double[][] Gceu = new double[ncf][tcdis - nxcdis];
        //prnt.Array(Gceu,"Gceu:");
        int uIndex = 0;
        for (int itc = 0; itc < tcdis - nxcdis; itc++) {
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                for (int itc2 = 0; itc2 < (tcdis - nxcdis); itc2++) {
                    //Gceu[uIndex][ itc2] = ((itc2 == itc) ? msdis[itc] * m[itc][ilcf] : 0);
                    Gceu[uIndex][itc2] = ((itc2 == itc) ? mhdis[itc] * m[itc][ilcf] : 0);
                    //System.out.println("itc:" + itc + ", ilcf:" + ilcf + ", itc2:" + itc2 + ", uIndex:" + uIndex);
                }
                uIndex = uIndex + 1;
            }
        }
        //prnt.Array(Gceu, "Gceu:");
        return (Gceu);
    }

    private double[] calGex() {//vj-2012-03-16
        double[] GexN = new double[np];
        double[] GcexN = calGcex();
        double[] GvexN = calGvex();
        System.arraycopy(GcexN, 0, GexN, 0, getNcdis());
        System.arraycopy(GvexN, 0, GexN, getNcdis(), getNcdis());
        return (GexN);
    }

    private double[] calGvex() {
        double[] GvexN = new double[ncdis];
        // to be implemented
        for (int itc = 0; itc < ncdis; itc++) {
            GvexN[itc] = T * msdis[itc] * (uAdis[itc] - uBdis[itc]);
        }
        return (GvexN);
    }

    double[] calGcex() {
        double[] GcexBN = new double[tcdis - nxcdis];
        // to be implemented
        for (int itc = 0; itc < tcdis - nxcdis; itc++) {
            //GcexBN[itc] = msdis[itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);
            GcexBN[itc] = mhdis[itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);
        }
        return (GcexBN);
    }

    private double[] calDGeT() {
        double GeTN[] = new double[np];
        double GveTN[] = calDGveT();
        double GceTN[] = calDGceT();
        System.arraycopy(GceTN, 0, GeTN, 0, getNcdis());
        System.arraycopy(GveTN, 0, GeTN, getNcdis(), getNcdis());
        return (GeTN);
    }

    private double[] calDGveT() {
        double GveTN[] = new double[ncdis];
        for (int itc = 0; itc < ncdis; itc++) {
            GveTN[itc] = 0;
            double Gvin = 0;
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                Gvin = Gvin + m[itc][ilcf] * (ut[itc][ilcf]);
            }
            GveTN[itc] = msdis[itc] * (Gvin - (1 - xB) * uAdis[itc] - (xB) * uBdis[itc]);
        }
        return (GveTN);
    }

    public double[] calDGceT() { //AJ-202-03-27 
        double DGceTN[] = new double[ncdis];
        return (DGceTN);
    }

///////////////Third Order Derivatives//////////////////////////////////////////
//////////////calGcuuu,calGcxuu,calGcxxu,calGcxxx///////////////////////////////
    private double[][][] calGuuu() {//vj-2012-03-16
        double[][][] GcuuuN = calGcuuu();
        double[][][] GuuuN = GcuuuN;
        return (GuuuN);
    }

    double[][][] calGcuuu() {//vj-2012-05-20
        double GcuuuN[][][] = new double[ncf][ncf][ncf];
        for (int kcf = 0; kcf < ncf; kcf++) {
            for (int jcf = 0; jcf < ncf; jcf++) {
                for (int icf = 0; icf < ncf; icf++) {
                    GcuuuN[kcf][jcf][icf] = 0.0;
                    for (int itc = 0; itc < tcdis; itc++) {
                        double prtlScuout = 0.0;
                        for (int inc = 0; inc < lc[itc]; inc++) {
                            double prtlScuin = 0.0;
                            for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                                prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][kcf] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * ((-1) / (cv[itc][inc][incv] * cv[itc][inc][incv])));
                            }
                            prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                        }
                        GcuuuN[kcf][jcf][icf] = GcuuuN[kcf][jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                    }
                    GcuuuN[kcf][jcf][icf] = (R * T * GcuuuN[kcf][jcf][icf]);
                    //System.out.print(Gcuu[jcf][icf]+",");
                }
            }
        }
        return GcuuuN;
    }

    private double[][] calGxuu() {//vj-2012-03-16
        double[][] GcxuuN = calGcxuu();
        //double[][] GvxuuN = calGvxuu();
        double[][] GxuuN = GcxuuN;
        return (GxuuN);
    }

    double[][] calGcxuu() {//vj-2012-05-20
        double GcxBuuN[][] = new double[ncf][ncf];
        for (int jcf = 0; jcf < ncf; jcf++) {
            for (int icf = 0; icf < ncf; icf++) {
                GcxBuuN[jcf][icf] = 0.0;
                for (int itc = 0; itc < tcdis; itc++) {
                    double prtlScuout = 0.0;
                    for (int inc = 0; inc < lc[itc]; inc++) {
                        double prtlScuin = 0.0;
                        for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                            prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * ((-1) / (cv[itc][inc][incv] * cv[itc][inc][incv])));
                        }
                        prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                    }
                    GcxBuuN[jcf][icf] = GcxBuuN[jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                }
                GcxBuuN[jcf][icf] = (R * T * GcxBuuN[jcf][icf]);
            }
        }
        return GcxBuuN;
    }

    private double[] calGxxu() {//vj-2012-03-16
        double[] GcxxuN = calGcxxu();
        //double[] GvxxuN = calGvxxu();
        double[] GxxuN = GcxxuN;
        return (GxxuN);
    }

    double[] calGcxxu() {//vj-2012-05-20
        double GcxxuN[] = new double[ncf];
        for (int icf = 0; icf < ncf; icf++) {
            GcxxuN[icf] = 0.0;
            for (int itc = 0; itc < tcdis; itc++) {
                double prtlScuout = 0.0;
                for (int inc = 0; inc < lc[itc]; inc++) {
                    double prtlScuin = 0.0;
                    for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                        prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][icf] * ((-1) / (cv[itc][inc][incv] * cv[itc][inc][incv])));
                    }
                    prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                }
                GcxxuN[icf] = GcxxuN[icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
            }
            GcxxuN[icf] = (R * T * GcxxuN[icf]);
        }
        return GcxxuN;
    }

    private double calGxxx() {//vj-2012-03-16
        double GcxxxN = calGcxxx();
        //double GvxxxN = calGvxxx();
        double GxxxN = GcxxxN;
        return (GxxxN);
    }

    double calGcxxx() {//vj-2012-05-20
        double GcxxxN;
        {
            GcxxxN = 0.0;
            for (int itc = 0; itc < tcdis; itc++) {
                double prtlScuout = 0.0;
                for (int inc = 0; inc < lc[itc]; inc++) {
                    double prtlScuin = 0.0;
                    for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                        prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * ((-1) / (cv[itc][inc][incv] * cv[itc][inc][incv])));
                    }
                    prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                }
                GcxxxN = GcxxxN + (msdis[itc] * kbdis[itc] * prtlScuout);
            }
            GcxxxN = (R * T * GcxxxN);
        }
        return GcxxxN;
    }

    private double[][] calGTuu() {
        double[][] GcTuuN = calGcTuu();
        double[][] GTuuN = GcTuuN;
        return (GTuuN);
    }

    double[][] calGcTuu() {//vj-2012-04-11: check for standard state contribution
        double GcTuuN[][] = new double[ncf][ncf];
        double ScuuN[][] = new double[ncf][ncf];
        for (int jcf = 0; jcf < ncf; jcf++) {
            for (int icf = 0; icf < ncf; icf++) {
                ScuuN[jcf][icf] = 0.0;
                for (int itc = 0; itc < tcdis; itc++) {
                    double prtlScuout = 0.0;
                    for (int inc = 0; inc < lc[itc]; inc++) {
                        double prtlScuin = 0.0;
                        for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                            prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * (1 / cv[itc][inc][incv]));
                        }
                        prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                    }
                    ScuuN[jcf][icf] = ScuuN[jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                }
                ScuuN[jcf][icf] = -(R * ScuuN[jcf][icf]);
                GcTuuN[jcf][icf] = -ScuuN[jcf][icf];
            }
        }
        return (GcTuuN);
    }

    private double[] calGTxu() {
        double[] GcTxuN = calGcTxu();
        double[] GTxuN = GcTxuN;
        return (GTxuN);
    }

    double[] calGcTxu() {//vj-2012-04-11: check for standard state contribution
        double GcTxuN[] = new double[ncf];
        double ScxuN[] = new double[ncf];
        for (int icf = 0; icf < ncf; icf++) {
            ScxuN[icf] = 0.0;
            for (int itc = 0; itc < tcdis; itc++) {
                double prtlScuout = 0.0;
                for (int inc = 0; inc < lc[itc]; inc++) {
                    double prtlScuin = 0.0;
                    for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                        prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][icf] * (1 / cv[itc][inc][incv]));
                    }
                    prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                }
                ScxuN[icf] = ScxuN[icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
            }
            ScxuN[icf] = -(R * ScxuN[icf]);
            GcTxuN[icf] = -ScxuN[icf];
        }
        return (GcTxuN);
    }

    private double calGTxx() {
        double GcTxxN = calGcTxx();
        double GTxxN = GcTxxN;
        return (GTxxN);
    }

    double calGcTxx() {//vj-2012-04-11: check for standard state contribution
        double GcTxx;
        double ScxxN = 0.0;
        for (int itc = 0; itc < tcdis; itc++) {
            double prtlScuout = 0.0;
            for (int inc = 0; inc < lc[itc]; inc++) {
                double prtlScuin = 0.0;
                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                    prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * (1 / cv[itc][inc][incv]));
                }
                prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
            }
            ScxxN = ScxxN + (msdis[itc] * kbdis[itc] * prtlScuout);
        }
        ScxxN = -(R * ScxxN);
        GcTxx = -ScxxN;
        return (GcTxx);
    }
///////////////Forth Order Derivatives//////////////////////////////////////////
//////////////calGcuuuu,calGcxuuu,calGcxxuu,calGcxxxu,calGcxxxx/////////////////

    private double[][][][] calGuuuu() {
        double[][][][] GcuuuuN = calGcuuuu();
        double[][][][] GuuuuN = GcuuuuN;
        return (GuuuuN);
    }

    double[][][][] calGcuuuu() {//vj-2012-05-20
        double GcuuuuN[][][][] = new double[ncf][ncf][ncf][ncf];
        for (int lcf1 = 0; lcf1 < ncf; lcf1++) {
            //System.out.println(getPhaseTag() + ".calGcuuuu, lcf1:" + lcf1);
            for (int kcf = 0; kcf < ncf; kcf++) {
                for (int jcf = 0; jcf < ncf; jcf++) {
                    for (int icf = 0; icf < ncf; icf++) {
                        GcuuuuN[lcf1][kcf][jcf][icf] = 0.0;
                        for (int itc = 0; itc < tcdis; itc++) {
                            double prtlScuout = 0.0;
                            for (int inc = 0; inc < lc[itc]; inc++) {
                                double prtlScuin = 0.0;
                                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                                    prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][lcf1] * cMat[itc][inc][incv][kcf] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * ((2) / (cv[itc][inc][incv] * cv[itc][inc][incv] * cv[itc][inc][incv])));
                                }
                                prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                            }
                            GcuuuuN[lcf1][kcf][jcf][icf] = GcuuuuN[lcf1][kcf][jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                        }
                        GcuuuuN[lcf1][kcf][jcf][icf] = (R * T * GcuuuuN[lcf1][kcf][jcf][icf]);
                    }
                }
            }
        }
        return GcuuuuN;
    }

    private double[][][] calGxuuu() {
        double[][][] GcxuuuN = calGcxuuu();
        double[][][] GxuuuN = GcxuuuN;
        return (GxuuuN);
    }

    double[][][] calGcxuuu() {//vj-2012-05-20
        double GcxuuuN[][][] = new double[ncf][ncf][ncf];
        {
            for (int kcf = 0; kcf < ncf; kcf++) {
                for (int jcf = 0; jcf < ncf; jcf++) {
                    for (int icf = 0; icf < ncf; icf++) {
                        GcxuuuN[kcf][jcf][icf] = 0.0;
                        for (int itc = 0; itc < tcdis; itc++) {
                            double prtlScuout = 0.0;
                            for (int inc = 0; inc < lc[itc]; inc++) {
                                double prtlScuin = 0.0;
                                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                                    prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][kcf] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * ((2) / (cv[itc][inc][incv] * cv[itc][inc][incv] * cv[itc][inc][incv])));
                                }
                                prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                            }
                            GcxuuuN[kcf][jcf][icf] = GcxuuuN[kcf][jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                        }
                        GcxuuuN[kcf][jcf][icf] = (R * T * GcxuuuN[kcf][jcf][icf]);
                    }
                }
            }
        }
        return GcxuuuN;
    }

    private double[][] calGxxuu() {
        double[][] GcxxuuN = calGcxxuu();
        double[][] GxxuuN = GcxxuuN;
        return (GxxuuN);
    }

    double[][] calGcxxuu() {//vj-2012-05-20
        double GcxxuuN[][] = new double[ncf][ncf];
        {
            {
                for (int jcf = 0; jcf < ncf; jcf++) {
                    for (int icf = 0; icf < ncf; icf++) {
                        GcxxuuN[jcf][icf] = 0.0;
                        for (int itc = 0; itc < tcdis; itc++) {
                            double prtlScuout = 0.0;
                            for (int inc = 0; inc < lc[itc]; inc++) {
                                double prtlScuin = 0.0;
                                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                                    prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * ((2) / (cv[itc][inc][incv] * cv[itc][inc][incv] * cv[itc][inc][incv])));
                                }
                                prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                            }
                            GcxxuuN[jcf][icf] = GcxxuuN[jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                        }
                        GcxxuuN[jcf][icf] = (R * T * GcxxuuN[jcf][icf]);
                    }
                }
            }
        }
        return GcxxuuN;
    }

    private double[] calGxxxu() {
        double[] GcxxxuN = calGcxxxu();
        double[] GxxxuN = GcxxxuN;
        return (GxxxuN);
    }

    double[] calGcxxxu() {//vj-2012-05-20
        double GcxxxuN[] = new double[ncf];
        {
            {
                {
                    for (int icf = 0; icf < ncf; icf++) {
                        GcxxxuN[icf] = 0.0;
                        for (int itc = 0; itc < tcdis; itc++) {
                            double prtlScuout = 0.0;
                            for (int inc = 0; inc < lc[itc]; inc++) {
                                double prtlScuin = 0.0;
                                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                                    prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][icf] * ((2) / (cv[itc][inc][incv] * cv[itc][inc][incv] * cv[itc][inc][incv])));
                                }
                                prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                            }
                            GcxxxuN[icf] = GcxxxuN[icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                        }
                        GcxxxuN[icf] = (R * T * GcxxxuN[icf]);
                    }
                }
            }
        }
        return GcxxxuN;
    }

    private double calGxxxx() {
        double GcxxxxN = calGcxxxx();
        double GxxxxN = GcxxxxN;
        return (GxxxxN);
    }

    double calGcxxxx() { //2012-02-23(VJ): Added
        double GcxxxxN;
        {
            {
                {
                    {
                        GcxxxxN = 0.0;
                        for (int itc = 0; itc < tcdis; itc++) {
                            double prtlScuout = 0.0;
                            for (int inc = 0; inc < lc[itc]; inc++) {
                                double prtlScuin = 0.0;
                                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                                    prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * ((2) / (cv[itc][inc][incv] * cv[itc][inc][incv] * cv[itc][inc][incv])));
                                }
                                prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                            }
                            GcxxxxN = GcxxxxN + (msdis[itc] * kbdis[itc] * prtlScuout);
                        }
                        GcxxxxN = (R * T * GcxxxxN);
                    }
                }
            }
        }
        return GcxxxxN;
    }

    private double[][][] calGTuuu() {
        double[][][] GcTuuuN = calGcTuuu();
        double[][][] GTuuuN = GcTuuuN;
        return (GTuuuN);
    }

    double[][][] calGcTuuu() { //vj-2012-04-11:check fot standard state contribution
        double GcTuuuN[][][] = new double[ncf][ncf][ncf];
        double ScuuuN[][][] = new double[ncf][ncf][ncf];
        for (int kcf = 0; kcf < ncf; kcf++) {
            for (int jcf = 0; jcf < ncf; jcf++) {
                for (int icf = 0; icf < ncf; icf++) {
                    ScuuuN[kcf][jcf][icf] = 0.0;
                    for (int itc = 0; itc < tcdis; itc++) {
                        double prtlScuout = 0.0;
                        for (int inc = 0; inc < lc[itc]; inc++) {
                            double prtlScuin = 0.0;
                            for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                                prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][kcf] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * ((-1) / (cv[itc][inc][incv] * cv[itc][inc][incv])));
                            }
                            prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                        }
                        ScuuuN[kcf][jcf][icf] = ScuuuN[kcf][jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                    }
                    ScuuuN[kcf][jcf][icf] = -(R * ScuuuN[kcf][jcf][icf]);
                    GcTuuuN[kcf][jcf][icf] = -ScuuuN[kcf][jcf][icf];
                }
            }
        }
        return (GcTuuuN);
    }

    private double[][] calGTxuu() {
        double[][] GcTxuuN = calGcTxuu();
        double[][] GTxuuN = GcTxuuN;
        return (GTxuuN);
    }

    double[][] calGcTxuu() { //vj-2012-04-11:check fot standard state contribution
        double GcTxuu[][] = new double[ncf][ncf];
        double Scxuu[][] = new double[ncf][ncf];
        for (int jcf = 0; jcf < ncf; jcf++) {
            for (int icf = 0; icf < ncf; icf++) {
                Scxuu[jcf][icf] = 0.0;
                for (int itc = 0; itc < tcdis; itc++) {
                    double prtlScuout = 0.0;
                    for (int inc = 0; inc < lc[itc]; inc++) {
                        double prtlScuin = 0.0;
                        for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                            prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][jcf] * cMat[itc][inc][incv][icf] * ((-1) / (cv[itc][inc][incv] * cv[itc][inc][incv])));
                        }
                        prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                    }
                    Scxuu[jcf][icf] = Scxuu[jcf][icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
                }
                Scxuu[jcf][icf] = -(R * Scxuu[jcf][icf]);
                GcTxuu[jcf][icf] = -Scxuu[jcf][icf];
            }
        }
        return (GcTxuu);
    }

    private double[] calGTxxu() {
        double[] GcTxxuN = calGcTxxu();
        double[] GTxxuN = GcTxxuN;
        return (GTxxuN);
    }

    double[] calGcTxxu() { //vj-2012-04-11:check fot standard state contribution
        double GcTxxuN[] = new double[ncf];
        double ScxxuN[] = new double[ncf];
        for (int icf = 0; icf < ncf; icf++) {
            ScxxuN[icf] = 0.0;
            for (int itc = 0; itc < tcdis; itc++) {
                double prtlScuout = 0.0;
                for (int inc = 0; inc < lc[itc]; inc++) {
                    double prtlScuin = 0.0;
                    for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                        prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][icf] * ((-1) / (cv[itc][inc][incv] * cv[itc][inc][incv])));
                    }
                    prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                }
                ScxxuN[icf] = ScxxuN[icf] + (msdis[itc] * kbdis[itc] * prtlScuout);
            }
            ScxxuN[icf] = -(R * ScxxuN[icf]);
            GcTxxuN[icf] = -ScxxuN[icf];
        }
        return GcTxxuN;
    }

    private double calGTxxx() {
        double GcTxxxN = calGcTxxx();
        double GTxxxN = GcTxxxN;
        return (GTxxxN);
    }

    double calGcTxxx() { //vj-2012-04-11:check fot standard state contribution
        double GcTxxxN;
        double ScxxxN;
        {
            ScxxxN = 0.0;
            for (int itc = 0; itc < tcdis; itc++) {
                double prtlScuout = 0.0;
                for (int inc = 0; inc < lc[itc]; inc++) {
                    double prtlScuin = 0.0;
                    for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                        prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * cMat[itc][inc][incv][tcf - 1] * ((-1) / (cv[itc][inc][incv] * cv[itc][inc][incv])));
                    }
                    prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
                }
                ScxxxN = ScxxxN + (msdis[itc] * kbdis[itc] * prtlScuout);
            }
            ScxxxN = -(R * ScxxxN);
            GcTxxxN = -ScxxxN;
        }
        return (GcTxxxN);
    }
///////////////Total Partial Derivatives////////////////////////////////////////

    private double[] calDux(double[][] GuuN, double[] GxuN) throws IOException {//vj-2012-03-16
        if (isGMinimized == false) {
            minimize();
        }
        //double[] uxN = CDsolve(GuuN, GxuN);
        double[] uxN = LDsolve(GuuN, GxuN);//vj-2015-03-25
        return (uxN);
    }

    private double[] calDuT() throws IOException {//vj-2012-04-14
        if (isGMinimized == false) {
            minimize();
        }
        double[][] GuuN = calGuu();
        double[] GTuN = calGTu();
        //double[] uTN = CDsolve(GuuN, GTuN);
        double[] uTN = LDsolve(GuuN, GTuN);//vj-2015-03-25
        return (uTN);
    }

    private double[][] calDue() throws ArithmeticException, IOException {//AJ-2012-03-27
        double[][] Geu = calGeu();
        double p[] = new double[ncf];
        double DueN[][] = new double[ncf][2 * (ncdis)];
        Matrix A = new Matrix(calGuu());
        //Print.f("Guu", calGuu(),1);
        LUDecomposition CD = new LUDecomposition(A);
        for (int itc = 0; itc < (2 * ncdis); itc++) {
            for (int icf = 0; icf < ncf; icf++) {
                p[icf] = -Geu[icf][itc];
            }
            Matrix B = new Matrix(p);
            Matrix X = CD.solve(B);
            for (int j = 0; j < ncf; j++) {
                DueN[j][itc] = X.getArray()[j][0];
            }
        }
        return DueN;
    }

    private double[] calDuxx(double[][] GuuN, double[] GxuN, double[][][] GuuuN, double[][] GxuuN, double[] GxxuN) throws IOException {//vj-2012-03-16
        double[] uxN = calDux(GuuN, GxuN);
        //double[] uxxN = CDsolve(GuuN, Mat.add(GxxuN, Mat.mul(2, Mat.mul(GxuuN, uxN)), Mat.mul(uxN, Mat.mul(GuuuN, uxN))));
        double[] uxxN = LDsolve(GuuN, Mat.add(GxxuN, Mat.mul(2, Mat.mul(GxuuN, uxN)), Mat.mul(uxN, Mat.mul(GuuuN, uxN))));//vj-2015-03-25
        return (uxxN);
    }

    private double[] calDuxxx(double[][] GuuN, double[] GxuN, double[][][] GuuuN, double[][] GxuuN, double[] GxxuN, double[][][][] GuuuuN, double[][][] GxuuuN, double[][] GxxuuN, double[] GxxxuN) throws IOException {//vj-2012-03-16
        double[] uxN = calDux(GuuN, GxuN);
        double[] uxxN = calDuxx(GuuN, GxuN, GuuuN, GxuuN, GxxuN);
        //double[] uxxxN = CDsolve(GuuN, Mat.add(Mat.mul(3, Mat.mul(uxxN, Mat.mul(GuuuN, uxN))), Mat.mul(3, Mat.mul(GxuuN, uxxN)), Mat.mul(uxN, Mat.mul(uxN, Mat.mul(GuuuuN, uxN))), Mat.mul(3, Mat.mul(uxN, Mat.mul(GxuuuN, uxN))), Mat.mul(3, Mat.mul(GxxuuN, uxN)), GxxxuN));
        double[] uxxxN = LDsolve(GuuN, Mat.add(Mat.mul(3, Mat.mul(uxxN, Mat.mul(GuuuN, uxN))), Mat.mul(3, Mat.mul(GxuuN, uxxN)), Mat.mul(uxN, Mat.mul(uxN, Mat.mul(GuuuuN, uxN))), Mat.mul(3, Mat.mul(uxN, Mat.mul(GxuuuN, uxN))), Mat.mul(3, Mat.mul(GxxuuN, uxN)), GxxxuN));//vj-2015-03-25
        return (uxxxN);
    }

    public double[] calDGe() throws IOException {//vj-2012-03-16
        if (isGMinimized == false) {
            minimize();
        }
        return (calGe());
    }

    @Override
    public double[] calDHe() throws IOException { //AJ-2012-03-27
        double[] HuN = calHu();
        double[] HeN = calHe();
        double[][] DueN = calDue();
        double[] DHeN = new double[np];
        //System.out.println(DHeN.length+","+HuN.length+","+DueN.length);
        for (int itc = 0; itc < np; itc++) {
            DHeN[itc] = HeN[itc];
            for (int icf = 0; icf < ncf; icf++) {
                DHeN[itc] = DHeN[itc] + HuN[icf] * DueN[icf][itc];
            }
        }
        return (DHeN);
    }

    @Override
    public double[] calDSe() throws IOException { //VJ-2012-06-22
        double[] SuN = calSu();
        double[] SeN = calSe();
        double[][] DueN = calDue();
        double[] DSeN = new double[np];
        //System.out.println(DHeN.length+","+HuN.length+","+DueN.length);
        for (int itc = 0; itc < np; itc++) {
            DSeN[itc] = SeN[itc];
            for (int icf = 0; icf < ncf; icf++) {
                DSeN[itc] = DSeN[itc] + SuN[icf] * DueN[icf][itc];
            }
        }
        return (DSeN);
    }

    private double calDGxx(double[] uxN, double[] GxuN, double GxxN) {//vj-2012-03-16
        double DGxxN = GxxN + Mat.mul(GxuN, uxN);
        return (DGxxN);
    }

    public double[] calDGex() throws ArithmeticException, IOException {//vj-2012-03-16
        double DGexBN[] = new double[np];
        double GexBN[] = calGex();
        double GxuN[] = calGxu();
        double DueN[][] = calDue();
        for (int itc = 0; itc < (np); itc++) {
            DGexBN[itc] = GexBN[itc];
            for (int icf = 0; icf < (getNcf()); icf++) {
                DGexBN[itc] = DGexBN[itc] + GxuN[icf] * DueN[icf][itc];
            }
        }
        return DGexBN;
    }

    @Override
    public double calDGxxx() throws IOException {//vj-2012-04-13
        checkMinimize();
        double[][] GuuN = calGuu();
        double[] GxuN = calGxu();
        double[][][] GuuuN = calGuuu();
        double[][] GxuuN = calGxuu();
        double[] GxxuN = calGxxu();
        double GxxxN = calGxxx();
        double[] uxN = calDux(GuuN, GxuN);
        double[] uxxN = calDuxx(GuuN, GxuN, GuuuN, GxuuN, GxxuN);
        double DGxxxN = GxxxN + 2 * Mat.mul(GxxuN, uxN) + Mat.mul(uxN, Mat.mul(GxuuN, uxN)) + Mat.mul(GxuN, uxxN);
        return (DGxxxN);
    }

    @Override
    public double calDGTxx() throws IOException {//vj-2012-04-13
        checkMinimize();
        double[][] GuuN = calGuu();
        double[] GxuN = calGxu();
        double[] GTuN = calGTu();
        double[][][] GuuuN = calGuuu();
        double[][] GxuuN = calGxuu();
        double[] GxxuN = calGxxu();
        double[][] GTuuN = calGTuu();
        double[] GTxuN = calGTxu();
        double GTxxN = calGTxx();
        double[] uxN = calDux(GuuN, GxuN);
        double[] uxxN = calDuxx(GuuN, GxuN, GuuuN, GxuuN, GxxuN);
        double DGTxxN = GTxxN + 2 * Mat.mul(GTxuN, uxN) + Mat.mul(GTuN, uxxN) + Mat.mul(uxN, Mat.mul(GTuuN, uxN));
        return (DGTxxN);
    }

    private double calDScxxx(double[] uxN, double[] uxxN, double[] uxxxN, double[] ScuN, double[][] ScuuN, double[] ScxuN, double[] ScxxuN, double[][] ScxuuN, double ScxxxN, double[][][] ScuuuN) {//2012-02-23(VJ): Added
        double DScxxxN;
        DScxxxN = ScxxxN + 3 * Mat.mul(ScxxuN, uxN) + 3 * Mat.mul(ScxuN, uxxN) + Mat.mul(ScuN, uxxxN) + 3 * Mat.mul(uxN, Mat.mul(ScxuuN, uxN)) + 3 * Mat.mul(uxxN, Mat.mul(ScuuN, uxN)) + Mat.mul(uxN, Mat.mul(uxN, Mat.mul(ScuuuN, uxN)));
        return (DScxxxN);
    }

    @Override
    public double calDGxxxx() throws IOException {//2012-02-23(VJ): Added
        checkMinimize();
        double[][] GuuN = calGuu();
        double[] GxuN = calGxu();
        double[][][] GuuuN = calGuuu();
        double[][] GxuuN = calGxuu();
        double[] GxxuN = calGxxu();
        //double GxxxN = calGxxx();
        double[][][][] GuuuuN = calGuuuu();
        double[][][] GxuuuN = calGxuuu();
        double[][] GxxuuN = calGxxuu();
        double[] GxxxuN = calGxxxu();
        double GxxxxN = calGxxxx();
        double[] uxN = calDux(GuuN, GxuN);
        double[] uxxN = calDuxx(GuuN, GxuN, GuuuN, GxuuN, GxxuN);
        double[] uxxxN = calDuxxx(GuuN, GxuN, GuuuN, GxuuN, GxxuN, GuuuuN, GxuuuN, GxxuuN, GxxxuN);
        double DGxxxxN = GxxxxN + 3 * Mat.mul(GxxxuN, uxN) + 3 * Mat.mul(GxxuN, uxxN) + 3 * Mat.mul(uxN, Mat.mul(GxuuN, uxxN)) + 3 * Mat.mul(uxN, Mat.mul(GxxuuN, uxN)) + Mat.mul(uxN, Mat.mul(uxN, Mat.mul(GxuuuN, uxN))) + Mat.mul(GxuN, uxxxN);
        return (DGxxxxN);
    }

    @Override
    public double calDGTxxx() throws IOException {//vj-2012-04-12
        checkMinimize();
        double[][] GuuN = calGuu();
        double[] GxuN = calGxu();
        double[] GTuN = calGTu();
        double[][][] GuuuN = calGuuu();
        double[][] GxuuN = calGxuu();
        double[] GxxuN = calGxxu();
        double[][] GTuuN = calGTuu();
        double[] GTxuN = calGTxu();
        double[][][][] GuuuuN = calGuuuu();
        double[][][] GxuuuN = calGxuuu();
        double[][] GxxuuN = calGxxuu();
        double[] GxxxuN = calGxxxu();
        double[][][] GTuuuN = calGTuuu();
        double[][] GTxuuN = calGTxuu();
        double[] GTxxuN = calGTxxu();
        double GTxxxN = calGTxxx();
        double[] uxN = calDux(GuuN, GxuN);
        double[] uxxN = calDuxx(GuuN, GxuN, GuuuN, GxuuN, GxxuN);
        double[] uxxxN = calDuxxx(GuuN, GxuN, GuuuN, GxuuN, GxxuN, GuuuuN, GxuuuN, GxxuuN, GxxxuN);
        double DGTxxxN = GTxxxN + 3 * Mat.mul(GTxxuN, uxN) + 3 * Mat.mul(GTxuN, uxxN) + Mat.mul(GTuN, uxxxN) + 3 * Mat.mul(uxN, Mat.mul(GTxuuN, uxN)) + 3 * Mat.mul(uxxN, Mat.mul(GTuuN, uxN)) + Mat.mul(uxN, Mat.mul(uxN, Mat.mul(GTuuuN, uxN)));
        return (DGTxxxN);
    }

    @Override
    public double calG() throws IOException {//vj-2012-03-16
        checkMinimize();
        G = calG0() + calGm();
        return (G);
    }

    private double calG(double[] x) throws IOException {//vj-2012-07-18//Gibbs energy of mixing
        double GON = calG0();
        double GmcN = calGmc();
        double GmvN = calGmv();
        double GmN = GmcN + GmvN;
        double GN = GON + GmN;
        return (GN);
    }

    private double calG0() {//vj-2012-07-19
        double G0N = (1 - xB) * G0A + xB * G0B;
        return (G0N);
    }

    @Override
    public double calGm() throws IOException {//vj-2012-07-18//Gibbs energy of mixing
        checkMinimize();
        double GmcN = calGmc();
        double GmvN = calGmv();
        double GmN = GmcN + GmvN;
        return (GmN);
    }

    private double calGmc() throws IOException { //vj-2012-07-18
        return (calHmc() - T * calSmc());
    }

    private double calHmc() throws IOException, ArithmeticException {//vj-2012-03-28//Enthalpy of mixing
        Hc = 0;
        for (int itc = 0; itc < (tcdis - nxcdis); itc++) {
            double Hcin = 0;
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                Hcin = Hcin + m[itc][ilcf] * (ut[itc][ilcf]);
            }
            Hc = Hc + mhdis[itc] * ecdis[itc] * (Hcin - (1 - xB) * uAdis[itc] - (xB) * uBdis[itc]);//vj-2012-06-23
        }
        return (Hc);
    }

    private double calSmc() throws IOException {//vj-2012-03-28//entropy of mixing
        //checkMinimize();
        double Sc_In = 0.0;
        for (int itc = 0; itc < tcdis; itc++) {
            double prtlScout = 0.0;
            for (int inc = 0; inc < lc[itc]; inc++) {
                double prtlScin = 0.0;
                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                    prtlScin = prtlScin + (wcv[itc][inc][incv] * cv[itc][inc][incv] * Math.log(cv[itc][inc][incv]));
                }
                prtlScout = prtlScout + (m[itc][inc] * prtlScin);
            }
            Sc_In = Sc_In + (msdis[itc] * kbdis[itc] * prtlScout);
        }
        Sc = (-R * Sc_In);
        return (Sc);
    }

    public void calHmc(double[] modDataIn) throws IOException {//2012-02-23(VJ): Added
        Print.f(getPhaseTag() + ".calHc() method called", 7);
        //Initialization
        double Hc_local;//y:Enthalpy
        double x_local = modDataIn[1];//x:Composition
        double T_local = modDataIn[2];//param:Temperature
        setT(T_local);
        setX(x_local);
        minimize();
        //calculation
        Hc_local = calHmc();
        //Output
        modDataIn[0] = Hc_local;
        Print.f(getPhaseTag() + ".calHmc() method at T=" + T_local + ", xB=" + x_local + " ended with Hc=" + Hc_local, 2);
        Print.f(getPhaseTag() + ".calHmc() method ended", 7);
    }

////////////////////////////////////////////////////////////////////////////////
    @Override
    public double calDGmx() throws IOException {//vj-2012-07-23
        if (isGMinimized == false) {
            minimize();
        }
        return (calGmx());
    }

    private double calGmx() throws IOException {//vj-2012-07-23
        double DGmcxN = calDGmcx();
        double DGmvxN = calDGmvx();
        double DGmxN = DGmcxN + DGmvxN;
        return (DGmxN);
    }

    public double calDGmcx() throws IOException {
        return (calGmcx());
    }

    @Override
    public double calDGx() throws IOException {//vj-2012-03-16
        checkMinimize();
        return (calGx());
    }

    private double calGx() throws IOException {//vj-2012-03-16
        double DGcxN = calDGcx();
        double DGvxN = calDGmvx();
        double DGxN = DGcxN + DGvxN;
        return (DGxN);
    }

    private double calDGmvx() {
        double Gvx = 0;
        for (int itc = 0; itc < ncdis; itc++) {
            //Gvx = Gvx + msdis[itc] * edis[ncdis + itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);//vj-2014-08-27
            Gvx = Gvx + msdis[itc] * evdis[itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);//vj-2014-08-27
        }
        return (Gvx * T);
    }

    public double calDGcx() throws IOException, ArithmeticException {//vj-2012-03-16
        checkMinimize();
        return (calGcx());
    }

    private double calGcx() throws IOException {//without CV update //AJ-2012-03-28
        return (calG0x() + calGmcx()); //AJ-2012-03-28
    }

    private double calG0x() {
        double G0xN = -G0A + G0B;
        return (G0xN);
    }

    private double calGmcx() throws IOException {
        return (calHmcx() - T * calSmcx());
    }

    private double calHmcx() throws IOException {//Need to be checked for ordred phases
        double HcxB = 0;
        for (int itc = 0; itc < (tcdis - nxcdis); itc++) {
            HcxB = HcxB + mhdis[itc] * ecdis[itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);
            //HcxB = HcxB + msdis[itc] * ecdis[itc] * (-(- 1) * uAdis[itc] - (1.0) * uBdis[itc]);
        }
        return (HcxB);
    }

    private double calSmcx() {//vj-2012-05-20
        double ScxB = 0.0;
        for (int itc = 0; itc < tcdis; itc++) {
            double prtlScuout = 0.0;
            for (int inc = 0; inc < lc[itc]; inc++) {
                double prtlScuin = 0.0;
                for (int incv = 0; incv < lcv[itc][inc]; incv++) {
                    prtlScuin = prtlScuin + (wcv[itc][inc][incv] * cMat[itc][inc][incv][tcf - 1] * Math.log(cv[itc][inc][incv]));
                }
                prtlScuout = prtlScuout + (m[itc][inc] * prtlScuin);
            }
            ScxB = ScxB + (msdis[itc] * kbdis[itc] * prtlScuout);
        }
        ScxB = (-R * ScxB);
        return (ScxB);
    }
////////////////////////////////////////////////////////////////////////////////

    @Override
    public double calDGT() throws IOException {//vj-2012-03-16
        checkMinimize();
        return (calGT());
    }

    private double calGT() throws IOException {//vj-2012-03-16
        double DGcTN = calDGcT();
        double DGvTN = calDGmvT();
        double DGTN = DGcTN + DGvTN;
        return (DGTN);
    }

    private double calDGmvT() {
        double GvT = 0;
        for (int itc = 0; itc < ncdis; itc++) {
            double Gvin = 0;
            for (int ilcf = 0; ilcf < lcf[itc]; ilcf++) {
                Gvin = Gvin + m[itc][ilcf] * (ut[itc][ilcf]);
            }
            //GvT = GvT + msdis[itc] * edis[ncdis + itc] * (Gvin - (1 - xB) * uAdis[itc] - (xB) * uBdis[itc]);//vj-2014-08-27
            GvT = GvT + msdis[itc] * evdis[itc] * (Gvin - (1 - xB) * uAdis[itc] - (xB) * uBdis[itc]);//vj-2014-08-27
        }
        return (GvT);
    }

    private double calDGcT() throws IOException, ArithmeticException {//vj-2012-03-16
        checkMinimize();
        return (calGcT());
    }

    private double calGcT() throws IOException {//2012-01-14: Added, 2012-02-23:Modified
        return (calG0T() + calGmcT()); //AJ-2012-03-28
    }

    private double calG0T() throws IOException {//vj-2012-07-22
        double G0ATN = G0AT;
        double G0BTN = G0BT;
        double G0TN = (1 - xB) * G0ATN + (xB) * G0BTN;
        return (G0TN);
    }

    private double calGmcT() throws IOException {//vj-2012-07-23
        return (-calSmc());
    }

    @Override
    public double calDGxx() throws IOException {//vj-2012-03-16
        checkMinimize();
        double[][] GuuN = calGuu();
        double[] GxuN = calGxu();
        double[] uxN = calDux(GuuN, GxuN);
        double GxxN = calGxx();
        double DGxxN = GxxN + Mat.mul(GxuN, uxN);
        return (DGxxN);
    }

    public double calDSmx() throws IOException {//vj-2013-04-20
        checkMinimize();
        double DSmxN = 0.0;
        double[][] GuuN = calGuu();
        double[] GxuN = calGxu();
        double DuxN[] = calDux(GuuN, GxuN);
        double SmxN = calSmx();//method without CV update
        double SuN[] = calSu();//method without CV update
        for (int i = 0; i < ncf; i++) {
            DSmxN = DSmxN + SuN[i] * DuxN[i];
        }
        DSmxN = DSmxN + SmxN;
        return DSmxN;
    }

    @Override
    public double calDGTx() throws IOException {//vj-2012-03-16
        checkMinimize();
        double DGTxN = 0.0;
        double[][] GuuN = calGuu();
        double[] GxuN = calGxu();
        double DuxN[] = calDux(GuuN, GxuN);
        double GTxN = calGTx();//method without CV update
        double GTuN[] = calGTu();//method without CV update
        for (int i = 0; i < getNcf(); i++) {
            DGTxN += GTuN[i] * DuxN[i];
        }
        DGTxN += GTxN;
        return DGTxN;
    }

    @Override
    public double[] calGmAB() throws IOException, ArithmeticException {//vj-2012-07-22//Returns Chemical activity of component A and B at xB
        Print.f(getPhaseTag() + "calGmAB() method called", 7);
        checkMinimize();
        double MU[] = new double[2];
        double GmN = calGm();
        double xN = xB;
        double GmxN = calDGmx();
        MU[0] = GmN - xN * GmxN;
        MU[1] = GmN + (1 - xN) * GmxN;
        Print.f(getPhaseTag() + "calGmAB() method ended", 7);
        return MU;
    }

    @Override
    public double[] calSmAB() throws IOException {//vj-2013-04-20
        Print.f(getPhaseTag() + "calGmAB() method called", 7);
        checkMinimize();
        double SmAB[] = new double[2];
        double SmN = calSm();
        double xN = xB;
        double DSmxN = calDSmx();
        SmAB[0] = SmN - xN * DSmxN;
        SmAB[1] = SmN + (1 - xN) * DSmxN;
        //System.out.println("GcN:" + GcN + ", GcxN:" + GcxN);
        Print.f(getPhaseTag() + "calGmAB() method ended", 7);
        return SmAB;
    }

    @Override
    public double[] calGAB() throws IOException {//2012-02-23(VJ): Added //Returns Chemical activity of component A and B at xB
        Print.f(getPhaseTag() + ".calGAB() method called", 7);
        double MU[] = new double[2];
        if (isGMinimized == false) {
            minimize();
        }
        double GN = calG();
        double xN = getXB();
        double GxN = calDGx();
        MU[0] = GN - xN * GxN;
        MU[1] = GN + (1 - xN) * GxN;
        Print.f(getPhaseTag() + ".calGAB() method ended", 7);
        return MU;
    }

    @Override
    public double[] calDMUx() throws IOException {//2012-02-23(VJ): Added
        Print.f(getPhaseTag() + ".calDMUx() method called", 7);
        checkMinimize();
        double DMUxN[] = new double[2];
        double xN = getXB();
        double DGxxN = calDGxx();
        DMUxN[0] = -xN * DGxxN;
        DMUxN[1] = (1 - xN) * DGxxN;
        Print.f(getPhaseTag() + ".calDMUx() method ended", 7);
        return DMUxN;
    }

    @Override
    public double[] calDMUT() throws IOException {
        Print.f(getPhaseTag() + ".calDMUT() method called", 7);
        checkMinimize();
        double DMUTN[] = new double[2];
        double xN = getXB();
        double DGTN = calDGT();
        double DGTxN = calDGTx();
        DMUTN[0] = DGTN - xN * DGTxN;
        DMUTN[1] = DGTN + (1 - xN) * DGTxN;
        Print.f(getPhaseTag() + ".calDMUT() method ended", 7);
        return DMUTN;
    }

    @Override
    public double[][] calDMUe() throws IOException {//2012-02-23(VJ): Added
        Print.f(getPhaseTag() + ".calDMUe() method called", 7);
        checkMinimize();
        double x = getXB();
        double DGeN[] = calGe();
        double DGexN[] = calDGex();
        //prnt.Array(DGexN, "DGexN:");
        double DMUeN[][] = new double[2][2 * (ncdis)];
        for (int itc = 0; itc < 2 * (ncdis); itc++) {
            DMUeN[0][itc] = DGeN[itc] - x * DGexN[itc];
            DMUeN[1][itc] = DGeN[itc] + (1 - x) * DGexN[itc];
        }
        Print.f(getPhaseTag() + ".calDMUe() method ended", 7);
        return DMUeN;
    }

    @Override
    public void calU1(double[] modDataIn) throws IOException {//vj-2012-03-28//Enthalpy of mixing
        Print.f(getPhaseTag() + ".calU1() method called", 7);
        //Initialization
        double u1_local;//y:Enthalpy
        double x_local = modDataIn[1];//x:Composition
        double T_local = modDataIn[2];//param:Temperature
        setT(T_local);
        setX(x_local);
        //printPhaseInfo();
        minimize();
        Print.f("u", u, 0);
        //calculation
        u1_local = u[0];
        //Output
        modDataIn[0] = u1_local;
        Print.f(getPhaseTag() + ".calU1() method at T=" + T_local + ", xB=" + x_local + " ended with Hc=" + u1_local, 2);
        Print.f(getPhaseTag() + ".calU1() method ended", 7);
    }

    /**
     * Calculates LRO of the phase
     *
     * @return LRO
     * @throws IOException
     */
    @Override
    public double calLRO() throws IOException {//vj-2017-02-08
        Print.f(getPhaseTag() + ".calLRO() method called", 7);
        //Initialization
        double LRO;//y:Enthalpy
        //double T_local = modDataIn[1];//param:Temperature
        //double x_local = modDataIn[2];//x:Composition
        //setT(T_local);
        //setX(x_local);
        //printPhaseInfo();
        minimize();
        //Print.f("u", u, 0);
        //calculation
        LRO = ut[tcdis - 1][0];
        //Output
        return (LRO);
        //Print.f(getPhaseTag() + ".calLRO() method at T=" + T_local + ", xB=" + x_local + " ended with LRO=" + LRO, 2);
        //Print.f(getPhaseTag() + ".calLRO() method ended", 7);
    }

    private void usrfun(double[] x, double[] fvec, double[][] fjac) throws IOException {//2012-02-23(VJ): Added
        Print.f(getPhaseTag() + ".usrfun() method called", 7);
        int n = fvec.length;
        setU(x);//update u, ut,cv
        //Print.f("u:", u,0);
        double[] GuN = calGu();//update Scu,Hcu, and Gu
        //Print.f("GuN:", GuN,0);
        double[][] GuuN = calGuu();//update Scuu,Hcuu, and Guu
        for (int i = 0; i < n; i++) {
            fvec[i] = GuN[i];
            System.arraycopy(GuuN[i], 0, fjac[i], 0, n);
        }
        Print.f(getPhaseTag() + ".usrfun() method ended", 7);
    }

    private void checkMinimize() throws IOException {
        Print.f(getPhaseTag() + ".checkMinimize() method called", 7);
        if (isGMinimized == false) {
            minimize();
        }
        Print.f(getPhaseTag() + ".checkMinimize() method ended", 7);
    }

    /**
     *
     * @throws IOException
     * @throws ArithmeticException
     */
    private void minimize() throws IOException, ArithmeticException {//2012-02-23(VJ):Added
        Print.f(getPhaseTag() + ".minimize() method called with T:" + T + " and xB:" + xB, 7);
        //Parameters
        int its;
        int ntrial = 400;
        double TOLF = 1E-8;
        double TOLX = 1E-12;
        double scalFactor;
        double errf = 0;
        double errx = 0;
        int n = getNcf();
        double eps = Math.ulp(1.0);

        double p[] = new double[n];
        double xold[] = getURand();//Initializing with random values
        double xTrial[] = new double[getTcf()];
        double[] fvec = new double[n];//Gradient vector
        double[][] fjac = new double[n][n];
        System.arraycopy(xold, 0, xTrial, 0, getTcf());//xTrial filled with random values
        //printPhaseInfo();
        for (its = 0; its < ntrial; its++) {
            setU(xold);//this will update u, ut, cv, Sc, Hc and G
            double cv_old[][][] = getCV();
            double cvMin = findMin(cv_old);
            //Print.f("cv_old:", cv_old, 0);
            //System.out.println("cvMin:" + cvMin);
            if (cvMin <= 0) {
                Print.f(getPhaseTag() + ".minimize():Iter:" + (its) + ", errf:" + errf + ", errx:" + errx, 4);
                isGMinimized = true;
                return;
            }

            usrfun(xold, fvec, fjac);//filling values of fvec and fjac at given xold
            //Print.f("fvec:", fvec, 0);
            //Print.f("fjac:", fjac, 0);
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= TOLF) {
                Print.f("minimize():Tolerance on f reached icf " + its + " iterations, errx:" + errx + ", errf:" + errf, 4);
                isGMinimized = true;
                return;
            }
            for (int i = 0; i < n; i++) {
                p[i] = -fvec[i];//p is pupulated with negaitve values of gradient vector
            }
            Matrix A = new Matrix(fjac);
            Matrix B = new Matrix(p);
            LUDecomposition CD = new LUDecomposition(A);
            Matrix X = CD.solve(B);
            for (int i = 0; i < n; i++) {
                p[i] = X.getArray()[i][0];
            }
            //printPhaseInfo();
            //Print.f("p:", p,0);
            //Print.f("fvec:", fvec,0);
            //Print.f("fjac:", fjac,0);
            errx = 0;
            for (int i = 0; i < n; i++) {
                errx = errx + Math.abs(p[i]);
                xTrial[i] = xold[i] + p[i];
            }
            Utils.normalU(xold, xTrial);
            scalFactor = stpmx(xold, xTrial);
            for (int i = 0; i < n; i++) {
                xold[i] = xold[i] + scalFactor * X.getArray()[i][0];
            }
            if (errx <= TOLX) {
                Print.f(getPhaseTag() + ".minimize(): Tolerance on xold reached icf " + its + " iterations, errx:" + errx + ", errf:" + errf, 4);
                isGMinimized = true;
                return;
            }
            Print.f(getPhaseTag() + ".minimize():Iter:" + (its) + ", stpmaxCF:" + scalFactor + ", errf:" + errf + ", errx:" + errx, 5);
            Print.f("u", xold, 5);
        }
        Print.f(getPhaseTag() + ".minimize():Tolerance limit not reached icf " + its + " iterations, errx:" + errx + ", errf:" + errf, 4);
    }

    private double findMin(double[][][] cv) {
        double minCV = cv[0][0][0];
        for (int i = 0; i < getTcdis() - 1; i++) {
            for (int j = 0; j < getLc()[i]; j++) {
                for (int k = 0; k < getNcv()[i][j]; k++) {
                    if (cv[i][j][k] < minCV) {
                        minCV = cv[i][j][k];
                    }
                }
            }
        }
        return minCV;
    }

    /**
     * returns maximum possible step size that keeps all the CV's in [0,1]
     *
     * @param uold
     * @param unew
     * @return step size
     * @throws IOException
     */
    private double stpmx(double uold[], double unew[]) throws IOException {//2012-02-23(VJ):Added,2012-02-25:Modified
        double lambda;
        double fmin = 1.0;
        double factor;
        //double flist[];
        //flist = new double[202];
        setU(uold);//this will update u, ut, cv, Sc, Hc and G
        double cv_old[][][] = getCV();
        setU(unew);//this will update u, ut, cv, Sc, Hc and G
        double cv_new[][][] = getCV();
        //int flist_index = 0;
        for (int i = 0; i < getTcdis() - 1; i++) {
            for (int j = 0; j < getLc()[i]; j++) {
                for (int incv = 0; incv < getNcv()[i][j]; incv++) {
                    if (cv_new[i][j][incv] <= 0) {
                        factor = Math.abs((0.0 - cv_old[i][j][incv]) / (cv_new[i][j][incv] - cv_old[i][j][incv]));
                        fmin = Math.min(fmin, factor);
                    }
                    if (cv_new[i][j][incv] >= 1) {
                        factor = Math.abs((1.0 - cv_old[i][j][incv]) / (cv_new[i][j][incv] - cv_old[i][j][incv]));
                        fmin = Math.min(fmin, factor);
                    }
                }
            }
        }
        if (fmin == 1) {
            lambda = 1.0;
        } else {
            lambda = 0.1 * fmin;
        }
        //System.out.println("lambda:" + lambda);
        return lambda;
    }

    /* private void minimize() throws IOException, ArithmeticException {//2012-02-23(VJ):Added
     Print.f(getPhaseTag() + ".minimize() method called with T:" + T + " and xB:" + xB, 7);
     boolean check = false;
     double[] xold = getURand();//Initializing with random values
     //Parameters
     isGMinimized = true; //Testing
     int ntrial = 1000;
     double TOLF = 1E-6, TOLMIN = 1.0e-12, STPMX = 100;
     double TOLX = 1E-12;// to be replaced with java equivalnet of numeric_limits<Doub>::epsilon();
     int i, j, its, n = ncf;
     double den, f, fold, stepmax = 1.0, sum, temp, test = 0.0;
     double[] g = new double[n];
     double[] p = new double[n];
     double[] x = new double[xold.length];
     double[][] fjac = new double[n][n];
     double[] fvec = new double[n];//Gradient vector
     for (i = 0; i < xold.length; i++) {
     x[i] = xold[i];
     }
     Print.f("x0:", xold, 0);
     for (its = 0; its < ntrial; its++) {
     fold = fmin(xold, fvec, fjac);//setU(x), returns values of f,fvec and fjac at given xold
     Print.f(getPhaseTag() + ".minimize():Iter:" + (its) + ", stepmax:" + stepmax + ", errf:" + test + ", fmin:" + fold, 5);
     Print.f("xold", xold, 5);
     Print.f("fvec", fvec, 5);
     //printPhaseInfo();
     for (i = 0; i < n; i++) {
     p[i] = -fvec[i];
     }
     Matrix A = new Matrix(fjac);
     Matrix B = new Matrix(p);
     LUDecomposition LD = new LUDecomposition(A);
     Matrix X = LD.solve(B);
     for (i = 0; i < n; i++) {
     p[i] = X.getArray()[i][0];
     }
     for (i = 0; i < n; i++) {
     x[i] = xold[i] + p[i];
     }
     Print.f("p", p, 5);
     Print.f("x", x, 5);
     stepmax = stepMax(xold, p);
     double pfvec = 0.0;
     for (i = 0; i < n; i++) {
     pfvec = pfvec + p[i] * fvec[i];
     }
     if (pfvec > 0.0) {
     Print.f("Newton direction is not a descent direction", 0);
     for (i = 0; i < n; i++) {
     p[i] = -fvec[i];
     }
     Print.f("p", p, 5);
     stepmax = stepMax(xold, p);
     lnsrch(xold, fold, p, x, stepmax);
     } else {
     if (stepmax < 1.0) {
     Print.f("Newton step leads out of physically acceptable domain", 0);
     lnsrch(xold, fold, p, x, stepmax);
     } else {
     f = fmin(x);
     if (fold < f) {
     Print.f("Newton step has not decreased the function value", 0);
     lnsrch(xold, fold, p, x, stepmax);
     }
     }
     }
     for (i = 0; i < n; i++) {
     xold[i] = x[i];
     }
     //Print.f("fvec", fvec, 0);
     test = 0.0;
     for (i = 0; i < n; i++) {
     if (Math.abs(fvec[i]) > test) {
     test = Math.abs(fvec[i]);
     }
     }
     //Print.f("test", test, 0);
     if (test < TOLF) {
     check = false;
     Print.f(getPhaseTag() + ".minimize() method ended 2" + ", errf:" + test, 7);
     isGMinimized = true;
     return;
     }
     }
     Print.f(getPhaseTag() + ".minimize() method ended without converging", 7);
     }
     */
 /*private void minimize() throws IOException, ArithmeticException {//2014-09-2-VJ:L-M based
     Print.f(getPhaseTag() + ".minimize() method called with T:" + T + " and xB:" + xB, 7);
     boolean check = false;
     double[] xold = getURand();//Initializing with random values
     //Parameters
     isGMinimized = true; //Testing
     int ntrial = 100;
     double TOLF = 1E-6, TOLMIN = 1.0e-12, STPMX = 100;
     double TOLX = 1E-12;// to be replaced with java equivalnet of numeric_limits<Doub>::epsilon();
     int i, j, its, n = ncf;
     double f, fold, stepmax = 1.0, test = 0.0;
     double lambda = 1E-6, scalFactor = 2.0;
     double[] g = new double[n];
     double[] p = new double[n];
     double[] x = new double[xold.length];
     double[][] fjac = new double[n][n];
     double[] fvec = new double[n];//Gradient vector
     for (i = 0; i < xold.length; i++) {
     x[i] = xold[i];
     }
     //Print.f("x0:", xold, 5);
     for (its = 0; its < ntrial; its++) {
     fold = fmin(xold, fvec, fjac);//setU(x), returns values of f,fvec and fjac at given xold
     Print.f(getPhaseTag() + ".minimize():Iter:" + (its) + ", lambda:" + lambda + ", errf:" + test + ", fold:" + fold, 5);
     Print.f("xold", xold, 5);
     Print.f("fvec", fvec, 5);
     //printPhaseInfo();
     for (i = 0; i < n; i++) {
     p[i] = -fvec[i];
     fjac[i][i] = fjac[i][i] * (1 + lambda);
     }
     Matrix A = new Matrix(fjac);
     Matrix B = new Matrix(p);
     LUDecomposition LD = new LUDecomposition(A);
     Matrix X = LD.solve(B);
     for (i = 0; i < n; i++) {
     p[i] = X.getArray()[i][0];
     }
     stepmax = stepMax(xold, p);
     for (i = 0; i < n; i++) {
     x[i] = xold[i] + Math.min(1.0, stepmax) * p[i];
     }
     Print.f("p", p, 5);
     Print.f("x", x, 5);
     f = fmin(xold, x);
     if (f > fold) {
     lambda = scalFactor * lambda;
     } else {
     lambda = scalFactor * lambda;
     for (i = 0; i < n; i++) {
     xold[i] = x[i];
     }
     }
     Print.f("f", f, 5);
     test = 0.0;
     for (i = 0; i < n; i++) {
     if (Math.abs(fvec[i]) > test) {
     test = Math.abs(fvec[i]);
     }
     }
     if (test < TOLF) {
     check = false;
     Print.f(getPhaseTag() + ".minimize() method ended 2" + ", errf:" + test, 7);
     isGMinimized = true;
     return;
     }
     }
     Print.f(getPhaseTag() + ".minimize() method ended without converging", 7);
     }
     */
    private double fmin(double[] x, double[] fvec, double[][] fjac) throws IOException {//2012-02-23(VJ): Added
        //Print.f(getPhaseTag() + ".stepmaxcv() method called", 7);
        int n = fvec.length;
        setU(x);//update u, ut,cv
        double GN = calG(x);
        double[] GuN = calGu();//update Scu,Hcu, and Gu
        double[][] GuuN = calGuu();//update Scuu,Hcuu, and Guu
        for (int i = 0; i < n; i++) {
            fvec[i] = GuN[i];
            System.arraycopy(GuuN[i], 0, fjac[i], 0, n);
        }
        //Print.f(getPhaseTag() + ".stepmaxcv() method ended", 7);
        return (GN);
    }

    private double fmin(double[] x) throws IOException {//2012-02-23(VJ): Added
        //Print.f(getPhaseTag() + ".stepmaxcv() method called", 7);
        int n = x.length;
        setU(x);//update u, ut,cv
        double GN = calG(x);
        return (GN);
        //Print.f(getPhaseTag() + ".stepmaxcv() method ended", 7);
    }

    private double fmin(double[] xold, double[] x) throws IOException {//2012-02-23(VJ): Added
        //Print.f(getPhaseTag() + ".stepmaxcv() method called", 7);
        int n = x.length;
        setU(x);//update u, ut,cv
        double GN = calG(x);
        setU(xold);//set xold back
        return (GN);
        //Print.f(getPhaseTag() + ".stepmaxcv() method ended", 7);
    }

    private double stepMax(double uold[], double p[]) throws IOException {//2014-09-16-VJ-added
        Print.f("PHASEmBINCE.stepMax() method called", 7);
        double[] unew = new double[tcf];
        for (int icf = 0; icf < ncf; icf++) {
            unew[icf] = uold[icf] + p[icf];
        }
        unew[tcf - 1] = uold[tcf - 1];
        Print.f("uold", uold, 0);
        //Print.f("unew(Trial)", unew, 0);
        double stepmax = 1.0;
        double stepmaxcf = stepMaxCF(uold, unew);
        //Print.f("stepmaxcf", stepmaxcf, 0);
        double scalFactor = 1.0;
        double stepmaxcv = 1000.0;//2014-09-16-VJ-Changed
        double stepTrial = 0.0;
        setU(uold);//this will update u, ut, cv, Sc, Hc and G
        double cv_old[][][] = getCV();
        if (stepmaxcf < 1) {
            for (int icf = 0; icf < tcf; icf++) {
                unew[icf] = uold[icf] + 0.99 * stepmaxcf * (unew[icf] - uold[icf]);
            }
        }
        Print.f("unew(Trial)", unew, 0);
        setU(unew);//this will update u, ut, cv, Sc, Hc and G
        double cv_new[][][] = getCV();
        for (int i = 0; i < getTcdis() - 1; i++) {
            for (int j = 0; j < getLc()[i]; j++) {
                for (int incv = 0; incv < getNcv()[i][j]; incv++) {
                    //if (cv_new[i][j][incv] <= 0) {//2014-09-16-VJ-Changed
                    if (cv_new[i][j][incv] < cv_old[i][j][incv]) {//2014-09-16-VJ-Changed
                        stepTrial = Math.abs((0.0 - cv_old[i][j][incv]) / (cv_new[i][j][incv] - cv_old[i][j][incv]));
                        //stepmaxcv = Math.min(stepmaxcv, stepTrial);//2014-09-16-VJ-Changed
                    } else if (cv_new[i][j][incv] > cv_old[i][j][incv]) {//2014-09-16-VJ-Changed
                        stepTrial = Math.abs((1.0 - cv_old[i][j][incv]) / (cv_new[i][j][incv] - cv_old[i][j][incv]));
                        //stepmaxcv = Math.min(stepmaxcv, stepTrial);//2014-09-16-VJ-Changed
                    }
                    stepmaxcv = Math.min(stepmaxcv, stepTrial);//2014-09-16-VJ-Changed
                }
            }
        }
        Print.f("stepmaxcv", stepmaxcv, 0);
        if (stepmaxcf >= 1) {
            stepmax = Math.min(stepmaxcf, stepmaxcv);
        } else {
            stepmax = 0.99 * stepmaxcf * stepmaxcv;
        }
        //Print.f("stepmax", stepmax, 0);
        if (stepmax == 1.0) {
            scalFactor = 1.0;
        } else {
            scalFactor = 0.9 * stepmax;
        }
        setU(uold);//this will update u, ut, cv, Sc, Hc and G
//        for (int icf = 0; icf < tcf; icf++) {
//            unew[icf] = uold[icf] + scalFactor * (unew[icf] - uold[icf]);
//        }
        //Print.f("unew", unew, 0);
        //isCFPhysical(uold, unew);
        Print.f("PHASEmBINCE.stepMax() method ended with scalFactor:" + scalFactor, 7);
        return scalFactor;
    }
//    private void lnsrch(double[] xold, Double fold, double[] g, double[] p, double[] fvec, double[][] fjac, double[] x, Double f, double stpmaxCF, boolean check) throws IOException {
//        double ALF = 1.0e-4, TOLX = java.lang.Double.MIN_VALUE;
//        double a, alam, alam2 = 0.0, alamin, b, disc, f2 = 0.0;
//        double rhs1, rhs2, slope = 0.0, sum = 0.0, temp, test, tmplam;
//        int i, n = xold.length;
//        check = false;
//        for (i = 0; i < n; i++) {
//            sum = sum + p[i] * p[i];
//        }
//        sum = Math.sqrt(sum);
//        if (sum > stpmaxCF) {
//            for (i = 0; i < n; i++) {
//                p[i] *= stpmaxCF / sum;
//            }
//        }
//        for (i = 0; i < n; i++) {
//            slope += g[i] * p[i];
//        }
//        if (slope >= 0.0) {
//            Print.f("Roundoff problem icf lnsrch.", 4);
//        }
//        test = 0.0;
//        for (i = 0; i < n; i++) {
//            temp = Math.abs(p[i]) / Math.max(Math.abs(xold[i]), 1.0);
//            if (temp > test) {
//                test = temp;
//            }
//        }
//        alamin = TOLX / test;
//        alam = 1.0;
//        for (;;) {
//            for (i = 0; i < n; i++) {
//                x[i] = xold[i] + alam * p[i];
//            }
//            f = stepmaxcv(x, fvec, fjac);
//            if (alam < alamin) {
//                for (i = 0; i < n; i++) {
//                    x[i] = xold[i];
//                }
//                check = true;
//                return;
//            } else if (f <= fold + ALF * alam * slope) {
//                return;
//            } else {
//                if (alam == 1.0) {
//                    tmplam = -slope / (2.0 * (f - fold - slope));
//                } else {
//                    rhs1 = f - fold - alam * slope;
//                    rhs2 = f2 - fold - alam2 * slope;
//                    a = (rhs1 / (alam * alam) - rhs2 / (alam2 * alam2)) / (alam - alam2);
//                    b = (-alam2 * rhs1 / (alam * alam) + alam * rhs2 / (alam2 * alam2)) / (alam - alam2);
//                    if (a == 0.0) {
//                        tmplam = -slope / (2.0 * b);
//                    } else {
//                        disc = b * b - 3.0 * a * slope;
//                        if (disc < 0.0) {
//                            tmplam = 0.5 * alam;
//                        } else if (b <= 0.0) {
//                            tmplam = (-b + Math.sqrt(disc)) / (3.0 * a);
//                        } else {
//                            tmplam = -slope / (b + Math.sqrt(disc));
//                        }
//                    }
//                    if (tmplam > 0.5 * alam) {
//                        tmplam = 0.5 * alam;
//                    }
//                }
//            }
//            alam2 = alam;
//            f2 = f;
//            alam = Math.max(tmplam, 0.1 * alam);
//        }
//    }

    private void lnsrch(double[] xold, Double fold, double[] p, double[] x, double stpmax) throws IOException {
        Print.f(getPhaseTag() + ".lnsrch() method called", 7);
        //Print.f("xold", xold, 0);
        //Print.f("fold", fold, 0);
        //Print.f("p", p, 0);
        //Print.f("stepmax", stepmax, 0);
        int i, n = ncf;
        double[] xtrial = new double[xold.length];
        for (i = 0; i <= n; i++) {
            xtrial[i] = xold[i];
            x[i] = xold[i];
        }
        double fm, step, ftrial, stpmin = 0.0;
        fm = fold;
        for (step = 0.0; step <= stpmax; step = step + (stpmax / 100)) {
            //Print.f("step", step, 0);
            for (i = 0; i < n; i++) {
                xtrial[i] = xold[i] + step * p[i];
            }
            //Print.f("xtrial", xtrial, 0);
            //isCFPhysical(xold, xtrial);
            ftrial = fmin(xtrial);
            if (ftrial < fm) {
                fm = ftrial;
                stpmin = step;
            }
            //Print.f("ftrial", ftrial, 0);
            //Print.f("fm", fm, 0);
            //Print.f("stpmin", stpmin, 0);
        }
        for (i = 0; i < n; i++) {
            x[i] = xold[i] + stpmin * p[i];
        }
        //Print.f("xold", xold, 0);
        //Print.f("x", x, 0);
        Print.f(getPhaseTag() + ".lnsrch() method ended with stpmin:" + stpmin, 7);
    }

    private double func(double[] x) throws IOException {//vj-2012-03-16
        setU(x);//update u, ut,cv
        double f = calG0() + calGm();
        return (f);
    }

//    private double stepMax(double uold[], double unew[]) throws IOException {//2012-02-23(VJ):Added,2012-02-25:Modified
//        Print.f("CVMBINCE.stepMax() method called", 7);
//        Print.f("uold", uold, 0);
//        Print.f("unew(Trial)", unew, 0);
//        double stepmax;
//        double stepmaxcf = stepMaxCF(uold, unew);
//        double scalFactor = 1.0;
//        double stepmaxcv = 1000.0;//2014-09-16-VJ-Changed
//        double stepTrial = 0.0;
//        setU(uold);//this will update u, ut, cv, Sc, Hc and G
//        double cv_old[][][] = getCV();
//        if (stepmaxcf < 1) {
//            for (int icf = 0; icf < tcf; icf++) {
//                unew[icf] = uold[icf] + 0.99 * stepmaxcf * (unew[icf] - uold[icf]);
//            }
//        }
//        setU(unew);//this will update u, ut, cv, Sc, Hc and G
//        double cv_new[][][] = getCV();
//        for (int i = 0; i < getTcdis() - 1; i++) {
//            for (int j = 0; j < getLc()[i]; j++) {
//                for (int incv = 0; incv < getNcv()[i][j]; incv++) {
//                    //if (cv_new[i][j][incv] <= 0) {//2014-09-16-VJ-Changed
//                    if (cv_new[i][j][incv] < cv_old[i][j][incv]) {//2014-09-16-VJ-Changed
//                        stepTrial = Math.abs((0.0 - cv_old[i][j][incv]) / (cv_new[i][j][incv] - cv_old[i][j][incv]));
//                        //stepmaxcv = Math.min(stepmaxcv, stepTrial);//2014-09-16-VJ-Changed
//                    } else if (cv_new[i][j][incv] > cv_old[i][j][incv]) {//2014-09-16-VJ-Changed
//                        stepTrial = Math.abs((1.0 - cv_old[i][j][incv]) / (cv_new[i][j][incv] - cv_old[i][j][incv]));
//                        //stepmaxcv = Math.min(stepmaxcv, stepTrial);//2014-09-16-VJ-Changed
//                    }
//                    stepmaxcv = Math.min(stepmaxcv, stepTrial);//2014-09-16-VJ-Changed
//                }
//            }
//        }
//        Print.f("stepmaxcv", stepmaxcv, 0);
//        stepmax = Math.min(stepmaxcf, stepmaxcv);
//        Print.f("stepmax", stepmax, 0);
//        if (stepmaxcv == 1.0) {
//            scalFactor = 1.0;
//        } else {
//            scalFactor = 0.9 * stepmax;
//        }
//        setU(uold);//this will update u, ut, cv, Sc, Hc and G
//        for (int icf = 0; icf < tcf; icf++) {
//            unew[icf] = uold[icf] + scalFactor * (unew[icf] - uold[icf]);
//        }
//        Print.f("unew", unew, 0);
//        //isCFPhysical(uold, unew);
//        Print.f("CVMBINCE.stepMax() method ended with scalFactor:" + scalFactor, 7);
//        return scalFactor;
//    }
    private Double stepMaxCF(double[] x_In, double[] x_Out) throws IOException {//2014-09-16-VJ-returns maxiumum step size keeping CFs within physical domain
        //Print.f("CVMBINCE.stepMaxCF called", 7);
        double stpmaxCF = 1000.0;
        double stpTrial = 1.0;
        for (int icf = 0; icf < ncf; icf++) {
            if (x_Out[icf] > x_In[icf]) {
                stpTrial = ((1.0 - x_In[icf]) / (x_Out[icf] - x_In[icf]));
            } else if (x_Out[icf] < x_In[icf]) {
                stpTrial = ((-1.0 - x_In[icf]) / (x_Out[icf] - x_In[icf]));
            }
            stpmaxCF = Math.min(stpmaxCF, stpTrial);
        }
        //Print.f("CVMBINCE.stepMaxCF ended with stpmax", stpmaxCF, 7);
        return (stpmaxCF);
    }

    private boolean isCFPhysical(double[] uOld, double[] uTrial) throws IOException {//2012-02-23(VJ):Added,2012-02-25:Modified
        Print.f("PHASEmBINCE.isCFPhysical() method called", 7);
        boolean check = true;
        setU(uTrial);//this will update u, ut, cv, Sc, Hc and G
        double cv_local[][][] = getCV();
        for (int icf = 0; icf < tcf; icf++) {
            if (uTrial[icf] < (-1)) {
                check = false;
            } else if (uTrial[icf] > 1) {
                check = false;
            }
        }
        if (check == true) {
            for (int i = 0; i < getTcdis() - 1; i++) {
                for (int j = 0; j < getLc()[i]; j++) {
                    for (int incv = 0; incv < getNcv()[i][j]; incv++) {
                        if (cv_local[i][j][incv] < 0) {
                            check = false;
                        } else if (cv_local[i][j][incv] > 1) {
                            check = false;
                        }
                    }
                }
            }
        }
        setU(uOld);
        Print.f("PHASEmBINCE.isCFPhysical() method ended with:" + check, 7);
        return check;
    }

    private double[] CDsolve(double[][] A_In, double[] B_In) throws ArithmeticException {//2012-02-23(VJ):Added
        int n = B_In.length;
        double[] p = new double[n];
        for (int icf = 0; icf < (n); icf++) {
            p[icf] = -B_In[icf];
        }
        Matrix A = new Matrix(A_In);
        Matrix B = new Matrix(p);
        CholeskyDecomposition CD = new CholeskyDecomposition(A);
        Matrix X = CD.solve(B);
        for (int icf = 0; icf < (n); icf++) {
            p[icf] = X.getArray()[icf][0];
        }
        return (p);
    }

    private double[] LDsolve(double[][] A_In, double[] B_In) throws ArithmeticException {//2015-03-25(VJ):Added
        int n = B_In.length;
        double[] p = new double[n];
        for (int icf = 0; icf < (n); icf++) {
            p[icf] = -B_In[icf];
        }
        Matrix A = new Matrix(A_In);
        Matrix B = new Matrix(p);
        LUDecomposition LUD = new LUDecomposition(A);
        Matrix X = LUD.solve(B);
        for (int icf = 0; icf < (n); icf++) {
            p[icf] = X.getArray()[icf][0];
        }
        return (p);
    }

    public void printPartialDerivatives() throws IOException {
        Utils.drawLine();
        System.out.println("Printing Partial Derivatives.......");
        System.out.print("Gu: ");
        for (int i = 0; i < getNcf(); i++) {
            System.out.print(Gu[i] + " ");
        }
        System.out.println();
        System.out.print("GxB: ");
        System.out.print(calGx() + " ");
        System.out.println();
        System.out.print("Ge: ");
        double Ge[] = calGe();
        for (int itc = 0; itc < np; itc++) {
            System.out.print(Ge[itc] + " ");
        }
        System.out.println();
        System.out.print("Guu: ");
        for (int i = 0; i < getNcf(); i++) {
            for (int j = 0; j < getNcf(); j++) {
                System.out.print(Guu[i][j] + " ");
            }
            System.out.println();
        }
        System.out.println();
        System.out.print("GxBu: ");
        double GxBu[] = calGxu();
        for (int i = 0; i < getNcf(); i++) {
            System.out.print(GxBu[i] + " ");
        }
        System.out.println();
        System.out.print("Gxx: ");
        System.out.print(calGxx() + " ");
        System.out.println();
        System.out.print("Guuu: ");
        double Guuu[][][] = calGuuu();
        for (int i1 = 0; i1 < getNcf(); i1++) {
            for (int i = 0; i < getNcf(); i++) {
                for (int j = 0; j < getNcf(); j++) {
                    System.out.print(Guuu[i1][i][j] + " ");
                }
                System.out.println();
            }
            System.out.println();
        }
        System.out.println();
        System.out.print("GxBuu: ");
        double GxBuu[][] = calGxuu();
        for (int i = 0; i < getNcf(); i++) {
            for (int j = 0; j < getNcf(); j++) {
                System.out.print(GxBuu[i][j] + " ");
            }
            System.out.println();
        }
        System.out.println();
        System.out.print("Gxxu: ");
        double Gxxu[] = calGxxu();
        for (int j = 0; j < getNcf(); j++) {
            System.out.print(Gxxu[j] + " ");
        }
        System.out.println();
        Utils.drawLine();
    }

    @Override
    public void printPhaseInfo() throws IOException {//vj-2012-03-20
        Utils.drawLine();
        System.out.println(getPhaseTag());
        Print.f("NP:", np, 0);
        Print.f("R:", R, 0);
        Print.f("edis:", edis, 0);
        //Print.f(edis.length, "Size of edis", 0);
        Print.f("ecdis:", ecdis, 0);
        //Print.f(eMat, "eMat:", 0);
        //Print.f(ecMat, "ecMat:", 0);
        Print.f("mdis:", msdis, 0);
        Print.f("mhdis:", mhdis, 0);
        Print.f("kbdis:", kbdis, 0);
        Print.f("mCoeffInmkb:", nij, 0);
        Print.f("T:", T, 0);
        Print.f("xB:", xB, 0);
        Print.f("elementA:", elementA, 0);
        Print.f("elementB:", elementB, 0);
        Print.f("dataBaseID:", dataBaseID, 0);
        Print.f("phaseID:", phaseID, 0);

        System.out.print("lcf:");
        for (int i = 0; i < tcdis; i++) {
            System.out.print(lcf[i] + " ");
        }
        System.out.println();
        System.out.println("tc:" + tc + ", nc:" + nc + ", tcf:" + tcf + ", ncf:" + ncf);
        System.out.print("Correlation Functions:");
        Print.f("u:", u, 0);
        for (int i = 0; i < (getTcf()); i++) {
            System.out.print(" u[" + i + "]:" + u[i]);
        }
        System.out.println();
        System.out.print("Correlation Functions(ut):");
        for (int i = 0; i < (getTcdis()); i++) {
            for (int j = 0; j < lcf[i]; j++) {
                System.out.print(" u[" + i + "][" + j + "]:" + ut[i][j]);
            }
        }
        System.out.println();
        System.out.print("Cluster Variables:");
        for (int i = 0; i < getTcdis(); i++) {
            for (int j = 0; j < lc[i]; j++) {
                for (int l = 0; l < lcv[i][j]; l++) {
                    System.out.print(" cv[" + i + "][" + j + "][" + l + "]:" + cv[i][j][l]);
                }
                System.out.print(",");
            }
            System.out.println();
        }
        //Print.f(wcv, "wcv",0);
        //Print.f(lcv, "ncv",0);
        calG(u);
        System.out.println("Free Energy:" + G);
        Utils.drawLine();
    }

    //abstract public void randInit() throws IOException;
    abstract public double[] getURand();
}
