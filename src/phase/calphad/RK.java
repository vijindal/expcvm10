/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.calphad;

import database.tdb;
import database.tdb.Phase;
import java.util.ArrayList;
import phase.GibbsModel;
import calbince.Conditions;

/**
 *
 * @author admin
 */
public class RK extends GibbsModel {
    // Parameters

    //private double[] xList; 
    //Macroscopic Parameters
    private double G;   //Free Energy 
    private double[] G0List; // Gibbs energy of the pure components
//    private double Gc; //  Configurational Free energy
//    private double H;   //Enthalpy
//    private double Hc; //  Configurational CE enthalpy
//    private double S;   //Entropy
//    double Sc; //  Configurational CVM Entropy
//    double[] Gcu; //  first derivative of Gc w.r.t. u
//    double[] Hcu; //  first derivative of Sc w.r.t. u
//    double[] Scu; //  first derivative of Hc w.r.t. u
//    private double Gu[];//First derivative of G w.r.t. u
//    private double Guu[][];//Second derivative of G w.r.t. u

    private tdb systdb;
    private Phase phase;
    private String phaseName;
    private Conditions condition;
    private ArrayList<String> elementNames;
    String[] compList;

    public RK(tdb systdb, ArrayList<String> elementNames, String phaseName, Conditions condition) {
        this.systdb = systdb;
        this.elementNames = elementNames;
        this.phaseName = phaseName;
        this.condition = condition;
        setNumComp(elementNames.size());
        setT(condition.getT());
        setP(condition.getP());
        setX(condition.getX().get(0));
        phase = systdb.getPhase(phaseName);
        phase.print();

    }
    //set methods

    //get methods   
    //Thermodynamic Calculations
    @Override
    public double calG() {//vj-2012-03-16
        G = calG0() + calGm();
        return (G);
    }

    public double calG0() {
        G0List = getG0List();
        double G0N = 0.0;
        for (int iComp = 0; iComp < getNumComp(); iComp++) {
            G0N = G0N + (getX().get(iComp) * G0List[iComp]);
        }
        return (G0N);
    }

    @Override
    public double calGm() {// Gibbs energy of mixing
        return (calGid() + calGEm());
    }

    private double calGEm() {//Excess free energy of mixing
        double GEmN = 0.0;
//        double xA = 1 - xB;
//        double L0 = edis[0] + T * edis[1];
//        double L1 = edis[2] + T * edis[3];
//        double GEmN = xA * xB * (L0 + (xB - xA) * L1);
        return (GEmN);
    }
    //First order derivatives

    @Override
    public double calDGT() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double calDGP() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double[] calDGx() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double[] calDGTx() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double[] calDGPx() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     *
     * @return
     */
    @Override
    public double[][] calDGxx() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void printPhaseInfo() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
