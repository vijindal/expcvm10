/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import database.tdb;
import java.util.ArrayList;
import utils.jama.Matrix;

/**
 *
 * @author admin This class handles all the data required for a conditions such
 * as: (i) tdb , (ii) elements , (iii) calculation types and phases, and (iv)
 * external conditions. It contains two datataype (i) systdb of tdb type and
 * (ii) a ArrayList calcSetList of the type CalcSet
 */
public class CalVars {

    private tdb systdb; // to store database defined a particular element set such as (Ti-Nb-V-Zr)
    private ArrayList<CalcSet> calcSets; // to store lists of Condition sets 

    /**
     *
     * @param systdb
     */
    public CalVars(tdb systdb) {
        System.out.println("CalVars method is called");
        this.systdb = systdb;
        calcSets = new ArrayList<>();
    }

    tdb gettdb() {
        return (systdb);
    }

    ArrayList<CalcSet> getcalcSet() {
        return (calcSets);
    }

    public void addcalcSet(CalcSet calcSet) {
        this.calcSets.add(calcSet);
    }

    /**
     *
     * @param calcSetList
     */
    public void setcalcSet(ArrayList<CalcSet> calcSetList) {
        this.calcSets = calcSetList;
    }

    /**
     * this class is for handling Conditions set data which is defined by a
     * particular element list. each Conditions set may consists of several
     * types of conditions such enthalpy of mixing, phase equilibria etc for the
     * given element list. for example
     *
     * set1 : Ti-Nb-V
     *
     * enthalpy of mixing-bcc conditions T=1200K, P = 1 atm, composition (Ti) =
     * 0.5, composition(Nb) = 0.1 to 0.9 in the steps of 0.1
     *
     * enthalpy of mixing-bcc conditions P = 1 atm, composition (Ti) = 0.5,
     * composition (Nb) = 0.1 T = 500K to 1500K in the steps of 100K
     *
     * enthalpy of mixing-hcp conditions T = 1200K, P = 1 atm, composition (Ti)
     * = 0.5, composition(Nb)= 0.1 to 0.9 in the steps of 0.1
     *
     * enthalpy of mixing-hcp conditions P = 1 atm, composition (Ti) = 0.5,
     * composition (Nb) = 0.1 T = 500K to 1500K in the steps of 100K
     *
     * phase-equilibria bcc-liquid conditions P = 1 atm, T=1200K, x(Ti)=0.9
     *
     * phase-equilibria bcc-liquid conditions P = 1 atm x(Ti) = 0.1 to 0.9 in
     * the steps of 0.1
     *
     * set2 : Ti-Nb enthalpy of mixing-bcc conditions T = 1200K, P= 1 atm,
     * composition(Ti) = 0.9 (single)
     *
     * enthalpy of mixing-bcc conditions T = 1200K, P = 1 atm, composition(Ti) =
     * 0.1 to 0.9 in the steps of 0.1(step)
     *
     * enthalpy of mixing-bcc conditions P = 1 atm, composition (Ti) = 0.5, T =
     * 500K to 1500K in the steps of 100K (step)
     *
     * It contains two datatype (i) ArrayList elementNames of String type and
     * (ii) a ArrayList calcTypes of the type CalcType
     */
//    public class CalcSet {
//
//        ArrayList<String> elementNames;
//        ArrayList<CalcType> calcTypes; // to store lists of CalcType
//
//        public CalcSet() {
//            elementNames = new ArrayList<>();
//            calcTypes = new ArrayList<>();
//        }
//
//        public ArrayList<String> getElementNames() {
//            return (this.elementNames);
//        }
//
//        public void setElementNames(ArrayList<String> elementNames) {
//            this.elementNames = elementNames;
//        }
//
//        public ArrayList<CalcType> getCalcType() {
//            return (this.calcTypes);
//        }
//
//        public void addCalcType(CalcType calcType) {
//            this.calcTypes.add(calcType);
//        }
//
//        public void setCalcType(ArrayList<CalcType> calcTypes) {
//            this.calcTypes = calcTypes;
//        }
//
//        /**
//         * This class is for handling Condition-type data and defined by a
//         * combination of (i) method and (ii) phases information. Each CalcType
//         * may consists of several types of external conditions such as T,P,
//         * compositions. for example for elementNames Ti-Nb-V, different
//         * calcTypes are as follows
//         *
//         * enthalpy of mixing-bcc conditions T=1200K, P = 1 atm, composition
//         * (Ti) = 0.5, composition(Nb) = 0.1 to 0.9 in the steps of 0.1
//         *
//         * enthalpy of mixing-bcc conditions P = 1 atm, composition (Ti) = 0.5,
//         * composition (Nb) = 0.1 T = 500K to 1500K in the steps of 100K
//         *
//         * enthalpy of mixing-hcp conditions T = 1200K, P = 1 atm, composition
//         * (Ti) = 0.5, composition(Nb)= 0.1 to 0.9 in the steps of 0.1
//         *
//         * enthalpy of mixing-hcp conditions P = 1 atm, composition (Ti) = 0.5,
//         * composition (Nb) = 0.1 T = 500K to 1500K in the steps of 100K
//         *
//         * phase-equilibria bcc-liquid conditions P = 1 atm, T=1200K, getX(Ti)=0.9
//         *
//         * phase-equilibria bcc-liquid conditions P = 1 atm getX(Ti) = 0.1 to 0.9
//         * in the steps of 0.1
//         *
//         * It contains three datatype (i) ArrayList phases of String type and
//         * (ii) a ArrayList conditions of the type Condition and (iii) method
//         */
////        public class CalcType {
////
////            String method;
////            ArrayList<String> phases;
////            ArrayList<Conditions> conditions;
////
////            public CalcType() {
////                phases = new ArrayList<>();
////                conditions = new ArrayList<>();
////            }
////
////            public String getMethod() {
////                return (this.method);
////            }
////
////            public void setMethod(String method) {
////                this.method = method;
////            }
////
////            public ArrayList<String> getPhases() {
////                return (this.phases);
////            }
////
////            public void addPhases(String phases) {
////                this.phases.add(phases);
////            }
////
////            public void setPhases(ArrayList<String> phases) {
////                this.phases = phases;
////            }
////
////            public ArrayList<Conditions> getConditions() {
////                return (this.conditions);
////            }
////
////            public void addConditions(Condition calculations) {
////                this.conditions.add(calculations);
////            }
////
////            public void setConditions(ArrayList<Conditions> conditions) {
////                this.conditions = conditions;
////            }
//
//           
////            public class Condition {
////
////                ArrayList<Double> T;
////                ArrayList<Double> P;
////                ArrayList<ArrayList<Double>> getX;
////
////                public Condition(double[] condT, double[] condP, double[][] condX) {
////                    T = new ArrayList<>();
////                    P = new ArrayList<>();
////                    getX = new ArrayList<>();
////                    for (double i = condT[0]; i <= condT[1]; i = i + condT[2]) {
////                        T.add(i);
////                    }
////                    for (double i = condP[0]; i <= condP[1]; i = i + condP[2]) {
////                        P.add(i);
////                    }
////                    for (double[] condX1 : condX) {
////                        ArrayList<Double> comp = new ArrayList<>();
////                        for (double i = condX1[0]; i <= condX1[1]; i = i + condX1[2]) {
////                            comp.add(i);
////                        }
////                        getX.add(comp);
////                    }
////                }
////
////                public Condition(double condT, double condP, double[] condX) {
////                    T = new ArrayList<>();
////                    P = new ArrayList<>();
////                    getX = new ArrayList<>();
////
////                    T.add(condT);
////                    P.add(condP);
////
////                    ArrayList<Double> comp = new ArrayList<>();
////                    for (double i : condX) {
////                        comp.add(i);
////                    }
////                    getX.add(comp);
////                }
////
////                public ArrayList<Double> getT() {
////                    return (this.T);
////                }
////
////                public ArrayList<Double> getP() {
////                    return (this.P);
////                }
////
////                public ArrayList<ArrayList<Double>> getX() {
////                    return (this.getX);
////                }
////            }
//
//        }
//    }
    public void printCalcSetList() {
        int i = 0;
        for (CalcSet calcSet : calcSets) {
            System.out.println("calcSet:" + i + ", element List: " + calcSet.elementNames);
            for (CalcType calcType : calcSet.calcTypes) {
                System.out.println("Method:" + calcType.getMethod());
                System.out.println("Phases:" + calcType.getPhases());
                for (Condition calculation : calcType.getConditions()) {
                    System.out.println("T:" + calculation.getT());
                    System.out.println("P:" + calculation.getP());
                    System.out.println("x:" + calculation.getX());
                }
            }
            i = i + 1;
        }
    }


    /*
        This Method returns Phase Matrix
     */
    private double[][] calPhaseMat(double[][] Gxx, int numComp, int numCF) {
        double[][] phaseMat = new double[numCF + 1][numCF + 1];
        for (int i = 0; i < numCF; i++) {
            System.arraycopy(Gxx[i], 0, phaseMat[i], 0, numCF);
        }
        for (int i = 0; i < numComp; i++) {//filling last column*
            phaseMat[numCF - numComp + i][numCF] = 1;
        }
        for (int i = 0; i < numComp; i++) {//filling last row
            phaseMat[numCF][numCF - numComp + i] = 1;
        }
        return (phaseMat);
    }

    /*
    This method returns inverted phase matrix
     */
    private double[][] calEmat(double[][] Gxx, int numComp, int numCF) {
        double[][] eMat;
        double[][] phaseMat = calPhaseMat(Gxx, numComp, numCF);
        Matrix phasemat = new Matrix(phaseMat);
        Matrix emat = phasemat.inverse();
        eMat = emat.getArrayCopy();
        return (eMat);
    }

}
