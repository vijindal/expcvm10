/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phase;

import calbince.Conditions;
import database.tdb;
import java.util.ArrayList;
import utils.io.Print;

/**
 *
 * @author admin
 */
//Gibbs energy expressiona consists of following parts:
//(i) structure or model, (this part will be addressed in this file, model to be specified)
//(ii) system specific parameters (to be read from database for a given phase, model and component(s) combination) and
//(iii) condition parameters (like T, P, composition) (to be supplied by user)
public abstract class GibbsModel {

    private double R; //= 8.3144;
    //Following information will be filled during object creation of a specific phase such as A2TTERN
    String phaseTag; // Phase Name
    private int numComp;//Number of components
    private double T; //Temperature
    private double P; //Pressure
    private ArrayList<Double> x;// composition of the components
    private tdb systdb;
    private tdb.Phase phase;
    private String phaseName;
    private Conditions condition;
    private ArrayList<String> elementNames;
    private String[] compList;
    //Following information wiil be filled by the corresponding phase model such as CECVM
    private double[] G0List; // Gibbs energy of the pure components
    private double[] G0TList; // Temp derivative of Gibbs energy of the pure components
    private double[] G0PList; // pressure derivative of Gibbs energy of the pure components
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

    //set methods
    public void setPhaseTag(String phaseTag_In) {
        this.phaseTag = phaseTag_In;
    }

    public void setTdb(tdb systdb) {
        this.systdb = systdb;
    }

    public void setElementNames(ArrayList<String> elementNames) {
        this.elementNames = elementNames;
    }

    public void setR(double R_input) {
        this.R = R_input; //To change body of generated methods, choose Tools | Templates.
    }

    public void setNumComp(int numComp) {
        this.numComp = numComp; //To change body of generated methods, choose Tools | Templates.
    }

    public void setT(double T_input) {
        this.T = T_input; //To change body of generated methods, choose Tools | Templates.
        //isGMinimized = false;
    }

    public void setP(double P_input) {
        this.P = P_input;
    }

    public void setX(ArrayList<Double> x_input) {
        this.x = x_input; //To change body of generated methods, choose Tools | Templates.
        //isGMinimized = false;
    }

    public void updateGE(double GN, double GTN, double GPN, double[] GxN, double[] GTxN, double[] GPxN, double[][] GxxN, double[][] eMatN, double[] cGN, double[] cTN, double[] cPN, double[][] cABN) {
        this.G = GN;
        this.GT = GTN;
        this.GP = GPN;
        this.Gx = GxN;
        this.GTx = GTxN;
        this.GPx = GPxN;
        this.Gxx = GxxN;
        this.eMat = eMatN;
        this.cG = cGN;
        this.cT = cTN;
        this.cP = cPN;
        this.cAB = cABN;
    }

    //get methods
    public String getPhaseTag() {
        return (this.phaseTag);
    }

    public tdb getTdb() {
        return (this.systdb);
    }

    public ArrayList<String> getElementNames() {
        return (this.elementNames);
    }

    public double getR() {
        return (this.R);
    }

    public int getNumComp() {
        return (this.numComp);
    }

    public double getT() {
        return (this.T);
    }

    public double getP() {
        return (this.P);
    }

    public ArrayList<Double> getX() {
        return (this.x);
    }

    public double[] getG0List() {
        return (this.G0List);
    }

    public double[] getG0TList() {
        return (this.G0TList);
    }

    public double[] getG0PList() {
        return (this.G0PList);
    }

    //Thermodynamic Calculations
    abstract public double calG();

    abstract public double calGm();

    //First order derivatives
    abstract public double calDGT();

    abstract public double calDGP();

    abstract public double[] calDGx();

    //Second order derivatives
    abstract public double[] calDGTx();

    abstract public double[] calDGPx();

    abstract public double[][] calDGxx();

    abstract public void printPhaseInfo();

    public double calGid() {//Ideal gibbs energy of mixing
        double temp = 0.0;
        double GidN;
        for (int iComp = 0; iComp < numComp; iComp++) {
            temp = temp + (x.get(iComp) * Math.log(x.get(iComp)));
        }
        GidN = R * T * temp;
        return (GidN);
    }

    public void calG0List() {// to be modified 
        G0List = new double[numComp];
    }

    public void calG0TList() {// to be modified 
        G0TList = new double[numComp];
    }

    public void calG0PList() {// to be modified 
        G0PList = new double[numComp];
    }

    /**
     * This method prints values of Gibbs energy and its first and second order
     * derivatives with temperature, pressure and composition variables
     */
    public void printGE() {
        Print.f("G:", G, 0);
        Print.f("GT:", GT, 0);
        Print.f("GP:", GP, 0);
        Print.f("Gx:", Gx, 0);
        Print.f("GTx:", GTx, 0);
        Print.f("GPx:", GPx, 0);
        Print.f("Gxx:", Gxx, 0);
        Print.f("cG:", cG, 0);
        Print.f("cT:", cT, 0);
        Print.f("cP:", cP, 0);
        Print.f("cAB:", cAB, 0);
    }
}
