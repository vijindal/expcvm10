/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import phase.calphad.RK;
import database.tdb;
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 * @author admin This is the core property calculation routine. Unlike pycalphad
 * which has a separate routine for the calculation where equilibrium is
 * required, this implementation will automatically check for the need of
 * equilibrium. This is equivalent to the old "method" class. This method was
 * used to call with a "phasedata" object and then the funcsCal method returned
 * the desired calculated value. The argument of the "funcsCal" method contained
 * all the necessary information for the calculations such as property to be
 * calculated, phase, and conditions. The new implementation will have similar
 * arguments such as tdb, list of components, name of the phases, output
 * property, (optional) custom model, points (carries information about the
 * conditions such as temperature, pressure, and compositions), (optional) T,
 * (optional) P and additional parameters.
 */
public class Calculate {

    private CalVars calvars;
    private tdb systdb; // to store database defined a particular element set such as (Ti-Nb-V-Zr)
    private ArrayList<CalcSet> calcSets; // to store lists of Conditions sets 
    private ArrayList<String> elementNames;
    private ArrayList<CalcType> calcTypes; // to store lists of CalcType
    private String method;
    private ArrayList<String> phases;
    private ArrayList<Conditions> conditions;
    private Double T;
    private Double P;
    private ArrayList<ArrayList<Double>> x;

    public Calculate(CalVars calvars) throws IOException {
        System.out.println("Calculate.constructor methed called");
        this.calvars = calvars;
        calvars.printCalcSetList();
        System.out.println("Calculate.constructor method ended");
    }

    /**
     * 200129-Phase/GibbsModel objects should be created in the beginning of
     * calculations. These objects should passed to the individual calculation
     * module. Such phase objects may be created based on combination of input
     * tdb database and input element list. A separate method can be written for
     * that purpose
     */
    public void cal() {
        System.out.println("Calculate.cal methed called");
        int i = 0;
        systdb = calvars.gettdb();
        calcSets = calvars.getcalcSet();
        for (CalcSet calcSet : calcSets) {
            elementNames = calcSet.elementNames;//element specific tdb database to be obtained 
            calcTypes = calcSet.calcTypes;
            System.out.println("calcSet:" + i + ", element List: " + elementNames);
            for (CalcType calcType : calcTypes) {
                method = calcType.getMethod();
                phases = calcType.getPhases();
                conditions = calcType.getConditions();
                System.out.println("Method:" + method);
                System.out.println("Phases:" + phases);
                for (Conditions condition : conditions) {
                    T = condition.getT();
                    P = condition.getP();
                    x = condition.getX();
                    System.out.println("T:" + T);
                    System.out.println("P:" + P);
                    System.out.println("x:" + x);
                    switch (method) {
//                        case "u1": {
//                            //phase0.calU1(modData);
//                            break;
//                        }
                        case "HM": {
                            calHm(systdb, elementNames, phases, condition);
                            break;
                        }
//                        case "H": {
//                            calH(modData);
//                            break;
//                        }
//                        case "Sm": {
//                            calSm(modData);
//                            break;
//                        }
//                        case "S": {
//                            calS(modData);
//                            break;
//                        }
                        case "Gm": {
                            //calGm(modData);
                            break;
                        }
                        case "G": {
                            //calG(modData);
                            break;
                        }
//                        case "aA": {
//                            calaA(modData);
//                            break;
//                        }
//                        case "aB": {
//                            calaB(modData);
//                            break;
//                        }
//                        case "SmA": {
//                            calSmA(modData);
//                            break;
//                        }
//                        case "SmB": {
//                            calSmB(modData);
//                            break;
//                        }
//                        case "MGX1": {
//                            calMGX1(modData);
//                            if (Math.abs(modData[2] - modData[3]) < 1E-05) {
//                                throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change yMod OR x1");
//                            }
//                            break;
//                        }
//                        case "MGX2": {
//                            calMGX2(modData);
//                            if (Math.abs(modData[2] - modData[3]) < 1E-05) {
//                                throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change yMod OR x1");
//                            }
//                            break;
//                        }
//                        case "MGT1": {
//                            calMGT1(modData);
//                            if (Math.abs(modData[2] - modData[3]) < 1E-05) {
//                                throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change x1");
//                            }
//                            break;
//                        }
//                        case "MGT2": {
//                            calMGT2(modData);
//                            if (Math.abs(modData[2] - modData[3]) < 1E-05) {
//                                throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change x1");
//                            }
//                            break;
//                        }
//                        case "SPT": {
//                            calSPT(modData);//vj-2012-06-23
//                            break;
//                        }
//                        case "COPT": {
//                            calCOPT(modData);//vj-2012-06-23
//                            break;
//                        }
//                        case "COPX": {
//                            calCOPX(modData);//vj-2012-06-23
//                            break;
//                        }
                        default:
                            System.err.println("Invalid Case for Single Phase");
                    }// Inside switch closed

                }
            }
            i = i + 1;
        }
        System.out.println("Calculate.cal method ended");
    }

    /**
     * Returns the enthalpy of mixing
     *
     * @param systdb
     * @param elementNames
     * @param phases
     * @param condition
     */
    public void calHm(tdb systdb, ArrayList<String> elementNames, ArrayList<String> phases, Conditions condition) {
        System.out.println(".calHm() method called");
        //Initialization
        double H_local;//y:Enthalpy
        //Double T_local;//param:Temperature
        String phase_local = phases.get(0); //Only one phase will be passed in case of enthalpy of mixing calculation
        RK phaseModel = new RK(systdb, elementNames, phase_local, condition);
        System.out.println(".calHm() method ended");
    }
}