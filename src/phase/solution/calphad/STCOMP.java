/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.solution.calphad;

import utils.io.Print;
import database.stdst;
import java.io.IOException;
import phase.PHASEBINCE;

/**
 *
 * @author metallurgy
 */
public class STCOMP implements PHASEBINCE {
    // Phase specific information

    private String phaseTag = "Stoichiometric Compounds"; // Phase Name
    // Parameters
    private double R = 8.3144;
    private final double[][] eMat = {{1, 0, 0, 0, 0, 0}, {0, 1, 0, 0, 0, 0}, {0, 0, 1, 0, 0, 0}, {0, 0, 0, 1, 0, 0}, {0, 0, 0, 0, 1, 0}, {0, 0, 0, 0, 01}};
    String elementA; //Element A
    String elementB; //Element B
    String dataBaseID;  //Database type used
    String phaseID;//phase ID
    double G0A, G0B;// base energies for standard state
    double G0AT, G0BT;//base energy derivatives with T
    private int np = 4;
    private double edis[]; //   Eci for each correlation function
    private double dyda[];//vj-2013-04-01-eci derivatives of a thermodynamic quantity 
    private double T;// Temperature
    private double xB;//    Composition of one component(i.e. B)
    //Macroscopic Parameters
    private double S; //  Configurational CVM Entropy
    private double H;//  Configurational CE enthalpy
    private double G;//  Configurational Free energy

    public STCOMP(String stdst[], double edis[], double T_in, double xB_in) throws IOException {
        Print.f(phaseTag + ".constructor method called", 4);
        setReferenceStateParameters(stdst);
        this.edis = edis;
        this.T = T_in;
        this.xB = xB_in;
        this.G = calG();
        Print.f(phaseTag + ".constructor method closed", 4);
    }

//Setter Methods
    private void updateStdst() throws IOException {
        G0A = stdst.get(dataBaseID, elementA, phaseID, T, 0);
        G0B = stdst.get(dataBaseID, elementB, phaseID, T, 0);
        G0AT = stdst.get(dataBaseID, elementA, phaseID, T, 1);
        G0BT = stdst.get(dataBaseID, elementB, phaseID, T, 1);
    }

///////////////Zero Order Derivatives///////////////////////////////////////////
    private double calH0() {//vj-2012-07-19
        double H0N = (1 - xB) * (G0A - T * G0AT) + xB * (G0B - T * G0BT);
        return (H0N);
    }

    private double calS0() {//vj-2012-07-19
        double S0N = -((1 - xB) * G0AT + xB * G0BT);
        return (S0N);
    }

    private double calG0() throws IOException {//vj-2012-07-17
        double G0AN = G0A;
        double G0BN = G0B;
        double G0N = (1 - xB) * G0AN + (xB) * G0BN;
        return (G0N);
    }

///////////////First Order Derivatives//////////////////////////////////////////
    private double calG0x() throws IOException {//vj-2012-07-17
        double G0AN = G0A;
        double G0BN = G0B;
        double G0xN = -G0AN + G0BN;
        return (G0xN);
    }

    private double calG0T() throws IOException {//vj-2012-07-22
        double G0ATN = G0AT;
        double G0BTN = G0BT;
        double G0TN = (1 - xB) * G0ATN + (xB) * G0BTN;
        return (G0TN);
    }

    private double calGmT() throws IOException {//vj-2012-07-22
        return (-calSm());
    }

///////////////Second Order Derivatives/////////////////////////////////////////
///////////////Third Order Derivatives//////////////////////////////////////////
/////////////////////////////////Thermodynamic Functions////////////////////////
//set methods
    @Override
    public void setNP(int np_In) {
        this.np = np_In;
    }

    @Override
    public void setR(double R_local) {
        this.R = R_local;
    }

    @Override
    public void setReferenceStateParameters(String stdst_In[]) {
        this.phaseID = stdst_In[0];
        this.elementA = stdst_In[1];
        this.elementB = stdst_In[2];
        this.dataBaseID = stdst_In[3];
    }

    @Override
    public void setEdis(double[] edis_In) {
        this.edis = edis_In;
    }

    @Override
    public void setEmat(String infile) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setT(double T_In) throws IOException {
        this.T = T_In;
        updateStdst();
    }

    @Override
    public void setX(double xB_In) {//2012-01-15(VJ):to be checked for ordered phases
        this.xB = xB_In;
    }

//get methods 
    @Override
    public int getNP() {
        return (this.np);
    }

    @Override
    public String getPhaseTag() {
        return (phaseTag);
    }

    @Override                           //added mani: 05-03-2013
    public String getPhaseId() {
        return (phaseID);
    }

    @Override
    public double getR() {
        return (this.R);
    }

    @Override
    public double getT() {
        return (this.T);
    }

    @Override
    public double getX() {
        return (this.xB);
    }

    @Override
    public void getEdis(double[] edis_out) {
        System.arraycopy(edis, 0, edis_out, 0, edis.length);
    }

    @Override
    public void getETrans(double[][] emat_out) {
        for (int i = 0; i < eMat.length; i++) {
            System.arraycopy(eMat[i], 0, emat_out[i], 0, eMat[0].length);
        }
    }

    @Override
    public double getG0A() {
        return (this.G0A);
    }

    @Override
    public double getG0B() {
        return (this.G0B);
    }

//Other methods  
    @Override
    public void printPhaseInfo() throws IOException {
        System.out.println("------------------------------------------------------------------");
        System.out.print("ECI:");
        for (int i = 0; i < (np); i++) {
            System.out.print(edis[i] + " ");
        }
        System.out.println();
        System.out.println("Temperature:" + T + ", xB:" + xB);
        System.out.println("G0:" + calG0() + ", H0:" + calH0() + ", S0:" + calS0());
        System.out.println("Gm:" + calGm() + ", Hm:" + calHm() + ", Sm:" + calSm());
        System.out.println("G:" + calG() + ", H:" + calH() + ", S:" + calS());
        System.out.println("------------------------------------------------------------------");
    }
//Thermodynamic Calculations

    @Override
    public double calLRO() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void calU1(double[] modData) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double calG() throws IOException {//2012-02-29(VJ): Added
        G = edis[0] + edis[1] * T + edis[2] * T * Math.log(T) + edis[3] * T * T + (edis[4] / T) + (edis[5] * T * T * T);
        return (G);
    }

    @Override
    public double calH() throws IOException {
        H = edis[0] + (2 * edis[4]) / T - edis[2] * T - edis[3] * Math.pow(T, 2) - 2 * edis[5] * Math.pow(T, 3);
        return (H);
    }

    @Override
    public double calS() throws IOException {//VJ-2012-06-22
        S = -edis[1] - edis[2] + edis[4] / Math.pow(T, 2) - 2 * edis[3] * T - 3 * edis[5] * Math.pow(T, 2) - edis[2] * Math.log(T);
        return (S);
    }

    @Override
    public double calGm() throws IOException {//vj-2012-07-17// free energy of mixing
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double calHm() throws IOException {//vj-2012-07-17// enthalpy of mixing
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double calSm() throws IOException {//vj-2012-07-17// entropy of mixing
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double[] calGAB() throws IOException {//vj-2012-02-29//Returns Chemical activity of component A and B at xB
        Print.f(getPhaseTag() + ".calMU() method called", 5);
        double MU[] = new double[2];
        double GN = calG();
        double xN = xB;
        double GxN = calDGx();
        //System.out.println("GN:" + GN + ", xN:" + xN + ", GxN:" + GxN);
        MU[0] = GN - xN * GxN;
        MU[1] = GN + (1 - xN) * GxN;
        Print.f(getPhaseTag() + ".calMU() method ended", 5);
        return MU;
    }

    @Override
    public double[] calGmAB() throws IOException {//vj-2012-07-22
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double[] calSmAB() throws IOException {//vj-2012-07-23
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
//First order derivatives

    @Override
    public double calDGx() throws IOException {
        return (0);
    }

    @Override
    public double calDGmx() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double[] calDMUx() throws IOException {//2012-02-29(VJ): Added
        Print.f(getPhaseTag() + ".calDMUx() method called", 5);
        double DMUxN[] = new double[2];
        double x = getX();
        double DGxxN = calDGxx();
        DMUxN[0] = -x * DGxxN;
        DMUxN[1] = (1 - x) * DGxxN;
        Print.f(getPhaseTag() + ".calDMUx() method ended", 5);
        return DMUxN;
    }

    @Override
    public double calDGT() throws IOException {
        return (edis[1] + edis[2] - (edis[4] / (T * T)) + 2 * edis[3] * T + 3 * edis[5] * T * T + edis[2] * Math.log(T));
    }

    @Override
    public double[] calDMUT() throws IOException {
        Print.f(getPhaseTag() + ".calDMUT() method called", 5);
        double DMUTN[] = new double[2];
        double xN = getX();
        double DGTN = calDGT();
        double DGTxN = calDGTx();
        DMUTN[0] = DGTN - xN * DGTxN;
        DMUTN[1] = DGTN + (1 - xN) * DGTxN;
        Print.f(getPhaseTag() + ".calDMUT() method ended", 5);
        return DMUTN;
    }

    @Override
    public double[] calDHe() throws IOException {
        double DHeN[] = {1, 0, -T, -T * T, 2 / T, -2 * T * T * T};
        return (DHeN);
    }

    @Override
    public double[] calDSe() throws IOException {
        double DSeN[] = {0, -1, -1 - Math.log(T), -2 * T, 1 / (T * T), -3 * T * T};
        return (DSeN);
    }

    @Override
    public double[][] calDMUe() throws IOException {//2012-02-23(VJ): Added
        Print.f(getPhaseTag() + ".calDMUe() method called", 5);
        double DGeN[] = {1, T, T * Math.log(T), T * T, 1 / T, T * T * T};
        double DGexN[] = {0, 0, 0, 0, 0, 0};
        //prnt.Array(DGexN, "DGexN:");
        double DMUeN[][] = new double[2][np];
        for (int itc = 0; itc < (np); itc++) {
            DMUeN[0][itc] = DGeN[itc] - xB * DGexN[itc];
            DMUeN[1][itc] = DGeN[itc] + (1 - xB) * DGexN[itc];
        }
        Print.f(getPhaseTag() + ".calDMUe() method ended", 5);
        return DMUeN;
    }
//Second order derivatives

    @Override
    public double calDGTx() throws IOException {//2012-02-29(VJ):Added
        return 0;
    }

    @Override
    public double calDGxx() {//2012-02-29(VJ): Added
        return 0;
    }

//Third order derivatives
    @Override
    public double calDGxxx() {//2012-02-29(VJ): Added
        return 0;
    }

    @Override
    public double calDGTxx() throws IOException {
        return 0;
    }
//Fourth order derivatives

    @Override
    public double calDGxxxx() {//2012-02-29(VJ): Added
        return 0;
    }

    @Override
    public double calDGTxxx() throws IOException {
        return 0;
    }

}
