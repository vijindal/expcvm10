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
public class RK implements PHASEBINCE {
    // Phase specific information

    private final String phaseTag = "RK"; // Phase Name
    // Parameters
    private double R = 8.3144;
    private final double[][] eMat = {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
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

    public RK(String stdst[], double edis[], double T_in, double xB_in) throws IOException {
        Print.f(phaseTag + ".constructor method called", 4);
        setReferenceStateParameters(stdst);
        this.edis = edis;
        this.T = T_in;
        this.xB = xB_in;
        this.G = calG();
        Print.f(phaseTag + ".constructor method closed", 4);
    }

//Setter Methods
    @Override
    public void setReferenceStateParameters(String stdst_In[]) {
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

    @Override
    public void setR(double R_local) {
        this.R = R_local;
    }

    @Override
    public void setEdis(double[] edis_In) {
        this.edis = edis_In;
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

    @Override
    public void setNP(int np_In) {
        this.np = np_In;
    }

//Getter Methods
    public double getXB() {
        return (this.xB);
    }

    @Override
    public double getG0A() {
        return (this.G0A);
    }

    @Override
    public double getG0B() {
        return (this.G0B);
    }

    public double getG0AT() {
        return (this.G0AT);
    }

    public double getG0BT() {
        return (this.G0BT);
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

    private double calHid() throws IOException {//vj-2012-07-22
        return (0);
    }

    private double calSid() throws IOException {//vj-2012-07-17
        double SidN = -R * ((1 - xB) * Math.log(1 - xB) + xB * Math.log(xB));
        return (SidN);
    }

    private double calGid() throws IOException {//vj-2012-07-17
        double GidN = calHid() - T * calSid();
        return (GidN);
    }

    private double calHEm() throws IOException {//vj-2012-07-17//Excess enthalpy of mixing
        double xA = 1 - xB;
        double L0 = edis[0];
        double L1 = edis[2];
        double HEmN = (1 - xB) * xB * (L0 + (xB - xA) * L1);
        return (HEmN);
    }

    private double calSEm() throws IOException {//vj-2012-07-17//Excess entropy of mixing
        double xA = 1 - xB;
        double L0 = -edis[1];
        double L1 = -edis[3];
        double SEmN = (1 - xB) * xB * (L0 + (xB - xA) * L1);
        return (SEmN);
    }

    private double calGEm() throws IOException {//vj-2012-07-17//Excess free energy of mixing
        double xA = 1 - xB;
        double L0 = edis[0] + T * edis[1];
        double L1 = edis[2] + T * edis[3];
        double GEmN = xA * xB * (L0 + (xB - xA) * L1);
        return (GEmN);
    }

    @Override
    public double calHm() throws IOException {//vj-2012-07-17// enthalpy of mixing
        return (calHid() + calHEm());
    }

    @Override
    public double calSm() throws IOException {//vj-2012-07-17// entropy of mixing
        return (calSid() + calSEm());
    }

    @Override
    public double calH() throws IOException {
        double H0N = calH0();
        double HmN = calHm();
        //System.out.println("H0:" + H0N + ", HmN:" + HmN);
        H = H0N + HmN;
        return (H);
    }

    @Override
    public double calS() throws IOException {//VJ-2012-06-22
        double S0N = calS0();
        double SmN = calSm();
        S = S0N + SmN;
        return (S);
    }
///////////////First Order Derivatives//////////////////////////////////////////

    private double calH0x() {//vj-2012-07-22
        double H0xN = (-1.0) * (G0A - T * G0AT) + (1.0) * (G0B - T * G0BT);
        return (H0xN);
    }

    private double calS0x() {//vj-2012-07-22
        double S0N = -((-1.0) * G0AT + (1.0) * G0BT);
        return (S0N);
    }

    private double calG0x() throws IOException {//vj-2012-07-17
        double G0AN = G0A;
        double G0BN = G0B;
        double G0xN = -G0AN + G0BN;
        return (G0xN);
    }

    private double calHidx() throws IOException {//vj-2012-07-22
        return (0);
    }

    private double calSidx() throws IOException {//vj-2012-07-17
        double SidxN = -R * (-Math.log(1 - xB) + Math.log(xB));
        return (SidxN);
    }

    private double calGidx() throws IOException {//vj-2012-07-17
        double GidxN = calHidx() - T * calSidx();
        return (GidxN);
    }

    private double calHEmx() throws IOException {//vj-2012-07-22
        double xA = 1 - xB;
        double L0 = edis[0];
        double L1 = edis[2];
        double HEmxN = (xA - xB) * (L0 + (xB - xA) * L1) + xA * xB * 2 * L1;
        return (HEmxN);
    }

    public double calSEmx() throws IOException {//vj-2012-07-17
        double xA = 1 - xB;
        double L0 = -edis[1];
        double L1 = -edis[3];
        double SEmxN = (xA - xB) * (L0 + (xB - xA) * L1) + xA * xB * 2 * L1;
        return (SEmxN);
    }

    private double calGEmx() throws IOException {//vj-2012-07-17
        double xA = 1 - xB;
        double L0 = edis[0] + T * edis[1];
        double L1 = edis[2] + T * edis[3];
        double GEmxN = (xA - xB) * (L0 + (xB - xA) * L1) + xA * xB * 2 * L1;
        return (GEmxN);
    }

    private double calHmx() throws IOException {//vj-2012-07-23
        return (calHidx() + calHEmx());
    }

    private double calSmx() throws IOException {//2012-02-29(VJ): Added
        return (calSidx() + calSEmx());
    }

    private double calGmx() throws IOException {//vj-2012-07-22
        return (calGidx() + calGEmx());
    }

    private double calHx() throws IOException {//vj-2012-07-22
        return (calH0x() + calHmx());
    }

    private double calSx() throws IOException {//vj-2012-07-22
        return (calS0x() + calSmx());
    }

    private double calGx() throws IOException {//2012-02-29(VJ): Added
        return (calG0x() + calGmx());
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

    private double calGT() throws IOException {//2012-01-14: Added, 2012-02-23:Modified
        return (calG0T() + calGmT());
    }

    public double[] calHe() {//2012-02-29(VJ): Added
        double[] He = new double[np];
        double x = xB;
        He[0] = ((1 - x) * x);
        He[1] = 0.0;
        He[2] = x * (1 - x) * (2 * x - 1);
        He[3] = 0.0;
        return (He);
    }

    private double[] calSe() throws IOException {//vj-2012-07-22
        double[] Se = new double[np];
        double xA = 1 - xB;
        Se[0] = 0;
        Se[1] = -xA * xB;
        Se[2] = 0;
        Se[3] = -(xA) * xB * (xB - xA);
        return (Se);
    }

    public double[] calGe() {//2012-02-29(VJ): Added
        double[] Ge = new double[np];
        double x = xB;
        Ge[0] = (1 - x) * x;
        Ge[1] = T * (1 - x) * x;
        Ge[2] = (1 - x) * x * (-1 + 2 * x);
        Ge[3] = T * (1 - x) * x * (-1 + 2 * x);
        return (Ge);
    }

    public double[] calSEmAB() throws IOException {
        double[] SEmABN = new double[2];
        double SEmN = calSEm();
        double SEmxN = calSEmx();
        double xBN = xB;
        SEmABN[0] = SEmN - xBN * SEmxN;
        SEmABN[1] = SEmN + (1 - xBN) * SEmxN;
        return (SEmABN);
    }

    public double[] calGEmAB() throws IOException {
        double[] GEmABN = new double[2];
        double GEmN = calGEm();
        double GEmxN = calGEmx();
        double xBN = xB;
        GEmABN[0] = GEmN - xBN * GEmxN;
        GEmABN[1] = GEmN + (1 - xBN) * GEmxN;
        return (GEmABN);
    }

    @Override
    public double[] calSmAB() throws IOException {//vj-2012-07-23
        double[] SmABN = new double[2];
        double SmN = calSm();
        double SmxN = calSmx();
        double xBN = xB;
        SmABN[0] = SmN - xBN * SmxN;
        SmABN[1] = SmN + (1 - xBN) * SmxN;
        return (SmABN);
    }

///////////////Second Order Derivatives/////////////////////////////////////////
    private double calDHxx() {//2012-02-29(VJ): Added
        double HxB = - 2 * edis[0] + 6 * edis[2] - 12 * edis[2] * xB;
        return (HxB);
    }

    private double calDSxx() {///2012-02-29(VJ): Added
        double SxxN = 2 * edis[1] - 6 * edis[3] + 12 * edis[3] * xB - R * ((1) / (1 - xB)) - R * (1 / xB);
        return SxxN;
    }

    private double calG0Tx() throws IOException {//vj-2012-07-22
        double G0ATN = G0AT;
        double G0BTN = G0BT;
        double G0TxN = -G0ATN + G0BTN;
        return (G0TxN);
    }

    private double calDGmTx() throws IOException {//vj-2012-07-22
        double SmxN = calSmx();
        return (-SmxN);
    }

    public double[] calDGex() {//2012-02-29(VJ):Added
        double Gex[] = new double[np];
        double x = xB;
        Gex[0] = 1 - 2 * x;
        Gex[1] = T * (1 - 2 * x);
        Gex[2] = -1 + 6 * x - 6 * Math.pow(x, 2);
        Gex[3] = T * (-1 + 6 * x - 6 * Math.pow(x, 2));
        return Gex;
    }
///////////////Third Order Derivatives//////////////////////////////////////////

    private double calDHxxx() { //2012-02-29(VJ): Added
        double HxxxN = -12 * edis[2];
        return HxxxN;
    }

    private double calDSxxx() { //2012-02-29(VJ): Added
        double SxxxN = 12 * edis[3] + R * (-Math.pow(-1 + xB, -2) + Math.pow(xB, -2));
        return SxxxN;
    }

    @Override
    public double calDGxxx() {//2012-02-29(VJ): Added
        double HxxxN = calDHxxx();
        double SxxxN = calDSxxx();
        double GxxxN = HxxxN - T * SxxxN;
        return GxxxN;
    }
///////////////Forth Order Derivatives//////////////////////////////////////////

    private double calDHxxxx() { //2012-02-29(VJ): Added
        double HxxxxN = 0.0;
        return HxxxxN;
    }

    private double calDSxxxx() { //2012-02-29(VJ): Added
        double SxxxxN = -2 * R * (Math.pow(1 - xB, -3) + Math.pow(xB, -3));
        return SxxxxN;
    }

    @Override
    public double calDGxxxx() {//2012-02-29(VJ): Added
        double HxxxxN = calDHxxxx();
        double SxxxxN = calDSxxxx();
        double GxxxxN = HxxxxN - T * SxxxxN;
        return GxxxxN;
    }

    @Override
    public double[] calDMUx() throws IOException {//2012-02-29(VJ): Added
        Print.f(getPhaseTag() + ".calDMUx() method called", 5);
        double DMUxN[] = new double[2];
        double x = getXB();
        double DGxxN = calDGxx();
        DMUxN[0] = -x * DGxxN;
        DMUxN[1] = (1 - x) * DGxxN;
        Print.f(getPhaseTag() + ".calDMUx() method ended", 5);
        return DMUxN;
    }

/////////////////////////////////Thermodynamic Functions////////////////////////
    public void calSpinodalTemperature(double[] T_in, double x_in) throws IOException {//2012-02-28(VJ):Added
        double T_local = T_in[0];
        double x_local = x_in;
        double delT = 1.0;
        double errT = 1E-6;
        int maxIterations = 20;

        double DGxxN;
        double DSxxN;
        //Execution Begins....
        setT(T_local);
        setX(x_local);
        for (int iter = 0; iter < maxIterations; iter++) {
            System.out.println("iter:" + iter + ", T:" + T_local + ", delT:" + delT);
            if ((Math.abs(delT) <= errT)) {
                return;
            }
            DGxxN = calDGxx();
            DSxxN = calDSxx();
            delT = DGxxN / DSxxN;
            T_local = T_local + delT;
            setT(T_local);
        }
        T_in[0] = T_local;
    }

//    private double calH() {//2012-02-29(VJ): Added
//        double HN = edis[0] * xB - edis[2] * xB - edis[0] * Math.pow(xB, 2) + 3 * edis[2] * Math.pow(xB, 2) - 2 * edis[2] * Math.pow(xB, 3);
//        H = HN;
//        return (H);
//    }
//    private double calS() {//2012-02-29(VJ): Added
//        double SN = -(1 - xB) * xB * (edis[1] + edis[3] * (-1 + 2 * xB)) - R * ((1 - xB) * Math.log(1 - xB) + xB * Math.log(xB));
//        S = SN;
//        return (S);
//    }
//    Overridden Method
    @Override
    public int getNP() {
        return (this.np);
    }

    @Override                           //added mani: 05-03-2013
    public String getPhaseId() {
        return (phaseID);
    }

    @Override
    public String getPhaseTag() {
        return (phaseTag);
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
    public double calGm() throws IOException {//vj-2012-07-17// free energy of mixing
        return (calGid() + calGEm());
    }

    @Override
    public double calG() throws IOException {//2012-02-29(VJ): Added
        G = calG0() + calGm();
        //Print.f(calG0()+","+calGm(), 0);
        return (G);
    }

    @Override
    public double calDGmx() throws IOException {
        return (calGmx());
    }

    @Override
    public double calDGx() throws IOException {
        return (calGx());
    }

    @Override
    public double calDGT() throws IOException {
        return (calGT());
    }

    @Override
    public double calDGxx() {//2012-02-29(VJ): Added
        double HxxN = calDHxx();
        double SxxN = calDSxx();
        double GxxN = HxxN - T * SxxN;
        return GxxN;
    }

    @Override
    public double calDGTx() throws IOException {//2012-02-29(VJ):Added
        return (calG0Tx() + calDGmTx());
    }

    @Override
    public double[] calGmAB() throws IOException {//vj-2012-07-22
        double[] GmABN = new double[2];
        double GmN = calGm();
        double GmxN = calDGmx();
        double xBN = xB;
        GmABN[0] = GmN - xBN * GmxN;
        GmABN[1] = GmN + (1 - xBN) * GmxN;
        return (GmABN);
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
    public double[] calDMUT() throws IOException {
        Print.f(getPhaseTag() + ".calDMUT() method called", 5);
        double DMUTN[] = new double[2];
        double xN = getXB();
        double DGTN = calDGT();
        double DGTxN = calDGTx();
        DMUTN[0] = DGTN - xN * DGTxN;
        DMUTN[1] = DGTN + (1 - xN) * DGTxN;
        Print.f(getPhaseTag() + ".calDMUT() method ended", 5);
        return DMUTN;
    }

    @Override
    public double[][] calDMUe() throws IOException {//2012-02-23(VJ): Added
        Print.f(getPhaseTag() + ".calDMUe() method called", 5);
        double x = xB;
        double DGeN[] = calGe();
        double DGexN[] = calDGex();
        //prnt.Array(DGexN, "DGexN:");
        double DMUeN[][] = new double[2][np];
        for (int itc = 0; itc < (np); itc++) {
            DMUeN[0][itc] = DGeN[itc] - x * DGexN[itc];
            DMUeN[1][itc] = DGeN[itc] + (1 - x) * DGexN[itc];
        }
        Print.f(getPhaseTag() + ".calDMUe() method ended", 5);
        return DMUeN;
    }

    @Override
    public void printPhaseInfo() throws IOException {
        drawLine();
        Print.f("elementA:", elementA, 0);
        Print.f("elementB:", elementB, 0);
        Print.f("dataBaseID:", dataBaseID, 0);
        Print.f("phaseID:", phaseID, 0);
        System.out.print("ECI:");
        for (int i = 0; i < (np); i++) {
            System.out.print(edis[i] + " ");
        }
        System.out.println();
        System.out.println("Temperature:" + T + ", xB:" + xB);
        updateStdst();
        System.out.println("G0:" + calG0() + ", H0:" + calH0() + ", S0:" + calS0());
        System.out.println("Gm:" + calGm() + ", Hm:" + calHm() + ", Sm:" + calSm());
        System.out.println("G:" + calG() + ", H:" + calH() + ", S:" + calS());
        drawLine();
    }

    private void drawLine() {
        System.out.println("------------------------------------------------------------------");

    }

    @Override
    public void calU1(double[] modData) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double[] calDHe() throws IOException {
        return (calHe());
    }

    @Override
    public double[] calDSe() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double calDGTxx() throws IOException {
        return (R * ((1 / xB) + (1 / (1 - xB))));
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
    public double calDGTxxx() throws IOException {
        return (R * (((-1) / (xB * xB)) + (1 / ((1 - xB) * (1 - xB))))); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setEmat(String infile) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double calLRO() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
