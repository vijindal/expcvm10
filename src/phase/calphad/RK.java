/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phase.calphad;

import java.util.ArrayList;
import java.util.logging.Logger;
import phase.GibbsModel;
import domain.model.ThermoCondition;
import infrastructure.logging.AppLevel;
import infrastructure.logging.Trace;

/**
 *
 * @author admin
 */
public class RK extends GibbsModel {
    private static final Logger LOG = Logger.getLogger(RK.class.getName());
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

    // Removed infrastructure dependencies: tdb systdb, Phase phase
    private String phaseName;
    private ThermoCondition condition;
    private ArrayList<String> elementNames;
    String[] compList;
    // Note: paramList retained as Object for backward compatibility bridge
    // Domain should not depend on database.tdb.Parameter type
    private final ArrayList<?> paramList;

    public RK(ArrayList<?> paramList, ThermoCondition condition) {
        this.paramList = (paramList != null) ? new ArrayList<>(paramList) : new ArrayList<>();
        this.condition = condition;
        setR(8.3144);

        if (condition != null && condition.getX() != null && !condition.getX().isEmpty()) {
            ArrayList<Double> x0 = new ArrayList<>(condition.getX().get(0));
            setNumComp(x0.size());
            setX(x0);
            setT(condition.getT());
            setP(condition.getP());
        } else {
            setNumComp(0);
            setX(new ArrayList<Double>());
            setT(298.15);
            setP(101325.0);
        }

    }
    //set methods

    //get methods   
    //Thermodynamic Calculations
    @Override
    public double calG() {//vj-2012-03-16
        Trace.enter(LOG, AppLevel.MODEL, "RK", "calG");
        G = calG0() + calGm();
        Trace.result(LOG, AppLevel.MODEL, "RK.calG: G=" + G);
        Trace.exit(LOG, AppLevel.MODEL, "RK", "calG");
        return (G);
    }

    public double calG0() {
        G0List = getG0List();
        if (G0List == null || G0List.length < getNumComp()) {
            G0List = new double[getNumComp()];
        }
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

    @Override
    public void calGderivatives() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ArrayList<Double> getInitlIntVarValues(ArrayList<Double> x) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
