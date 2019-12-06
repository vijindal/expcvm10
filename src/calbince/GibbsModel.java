/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import database.tdb;
import java.util.ArrayList;

/**
 *
 * @author admin
 */
public class GibbsModel {

    // Parameters
    private final double R = 8.3144;
    private double T;
    private int numComp;//Number of components
    private double[] xList; // composition of the components
    //Macroscopic Parameters
    private double[] G0List; // Gibbs enery of the pure components
    private double G;   //Free Energy 
    private double Gc; //  Configurational Free energy
    private double H;   //Enthalpy
    private double Hc; //  Configurational CE enthalpy
    private double S;   //Entropy
    double Sc; //  Configurational CVM Entropy
    double[] Gcu; //  first derivative of Gc w.r.t. u
    double[] Hcu; //  first derivative of Sc w.r.t. u
    double[] Scu; //  first derivative of Hc w.r.t. u
    private double Gu[];//First derivative of G w.r.t. u
    private double Guu[][];//Second derivative of G w.r.t. u
    tdb systdb;
    String[] compList;

    //Gibbs energy expressiona consists of following parts:
    //(i) structure or model, (this part will be addressed in this file, model to be specified)
    //(ii) system specific parameters (to be read from database for a given phase, model and component(s) combination) and
    //(iii) condition parameters (like T, P, composition) (to be supplied by user)
    
    GibbsModel(tdb systdb, String[] compList, String phaseList, ArrayList<tdb.Parameter> sysparamList) {
        this.systdb = systdb;
        this.compList = compList;

    }

    public double calG() {//vj-2012-03-16
        G = calG0() + calGm();
        return (G);
    }

    private double calG0() {//Reference energy
        double G0N = 0.0;
        for (int iComp = 0; iComp < numComp; iComp++) {
            G0N = G0N + (xList[iComp] * G0List[iComp]);
        }
        return (G0N);
    }

    public double calGm() {// Gibbs energy of mixing
        return (calGid() + calGEm());
    }

    private double calGid() {//Ideal gibbs energy of mixing
        double temp = 0.0;
        double GidN;
        for (int iComp = 0; iComp < numComp; iComp++) {
            temp = temp + (xList[iComp] * Math.log(xList[iComp]));
        }
        GidN = R * T * temp;
        return (GidN);
    }

    private double calGEm() {//Excess free energy of mixing
        double GEmN = 0.0;
//        double xA = 1 - xB;
//        double L0 = edis[0] + T * edis[1];
//        double L1 = edis[2] + T * edis[3];
//        double GEmN = xA * xB * (L0 + (xB - xA) * L1);
        return (GEmN);
    }
}
