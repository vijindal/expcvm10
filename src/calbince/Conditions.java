/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import java.util.ArrayList;

/**
 *
 * @author admin This class handles Conditions type data which is defined by
 * external conditions such as T, P, compositions. for example
 *
 * phase-equilibria bcc-liquid type data, several possible conditions are:
 * conditions P = 1 atm, T=1200K, getX(Ti)=0.9
 *
 * This gives information
 */
public class Conditions {

    private double T;
    private double P;
    private int p; //number of phases
    private int c;  //number of components
    private ArrayList<ArrayList<Double>> x; //List of phase compositions such as {{0.1,0.1,0.8},{0.1,0.8,0.1}}

//    public Conditions(double[] condT, double[] condP, double[][] condX) {
//        T = new ArrayList<>();
//        P = new ArrayList<>();
//        getX = new ArrayList<>();
//        for (double i = condT[0]; i <= condT[1]; i = i + condT[2]) {
//            T.add(i);
//        }
//        for (double i = condP[0]; i <= condP[1]; i = i + condP[2]) {
//            P.add(i);
//        }
//        for (double[] condX1 : condX) {
//            ArrayList<Double> comp = new ArrayList<>();
//            for (double i = condX1[0]; i <= condX1[1]; i = i + condX1[2]) {
//                comp.add(i);
//            }
//            getX.add(comp);
//        }
//    }
//    public Conditions(double condT, double condP, double[] condX) {
//        T = new ArrayList<>();
//        P = new ArrayList<>();
//        getX = new ArrayList<>();
//
//        T.add(condT);
//        P.add(condP);
//
//        ArrayList<Double> comp = new ArrayList<>();
//        for (double i : condX) {
//            comp.add(i);
//        }
//        getX.add(comp);
//    }
    public Conditions(double condT, double condP, ArrayList<ArrayList<Double>> condX) {
        this.x = new ArrayList<>();
        this.T = condT;
        this.P = condP;
        this.x = condX;
        this.p = x.size(); //reading size of composition array as p
        this.c = x.get(0).size(); //size of the first element of x as c 
    }

//    public ArrayList<Double> getT() {
//        return (this.T);
//    }
//
//    public ArrayList<Double> getP() {
//        return (this.P);
//    }
//
//    public ArrayList<ArrayList<Double>> getX() {
//        return (this.getX);
//    }
    public double getT() {
        return (this.T);
    }

    public double getP() {
        return (this.P);
    }

    public ArrayList<ArrayList<Double>> getX() {
        return (this.x);
    }

    /**
     *
     * @return degree of freedom using phaserule
     */
    public int dof() {
        return (c + 2 - p);
    }
}
