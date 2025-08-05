/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import java.util.ArrayList;

/**
 *
 * @author admin

 This class is for handling Condition-type data and defined by a combination
 of (i) method and (ii) phases information. Each CalcType may consists of
 several types of external conditions such as T,P, compositions. for example
 for elementNames Ti-Nb-V, different calcTypes are as follows

 enthalpy of mixing-
 * \conditions T=1200K, P = 1 atm, composition (Ti) = 0.5,
 composition(Nb) = 0.1 to 0.9 in the steps of 0.1

 enthalpy of mixing-bcc conditions P = 1 atm, composition (Ti) = 0.5,
 composition (Nb) = 0.1 T = 500K to 1500K in the steps of 100K

 enthalpy of mixing-hcp conditions T = 1200K, P = 1 atm, composition (Ti) =
 0.5, composition(Nb)= 0.1 to 0.9 in the steps of 0.1

 enthalpy of mixing-hcp conditions P = 1 atm, composition (Ti) = 0.5,
 composition (Nb) = 0.1 T = 500K to 1500K in the steps of 100K

 phase-equilibria bcc-liquid conditions P = 1 atm, T=1200K, x(Ti)=0.9

 phase-equilibria bcc-liquid conditions P = 1 atm x(Ti) = 0.1 to 0.9 in the
 steps of 0.1

 It contains three datatype (i) ArrayList phases of String type and (ii) a
 ArrayList conditions of the type Condition and (iii) method
 
 conditions P = 1 atm,T=1200K, x(Ti)=
 0.1 to 0.9 in the steps of 0.1
 */
public class CalcType {

    private String method;
    private ArrayList<String> phases;
    private ArrayList<Condition> conditions;

    public CalcType() {
        phases = new ArrayList<>();
        conditions = new ArrayList<>();
    }

    public String getMethod() {
        return (this.method);
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public ArrayList<String> getPhases() {
        return (this.phases);
    }

    public void addPhases(String phases) {
        this.phases.add(phases);
    }

    public void setPhases(ArrayList<String> phases) {
        this.phases = phases;
    }

    public ArrayList<Condition> getConditions() {
        return (this.conditions);
    }

    public void addConditions(Condition calculations) {
        this.conditions.add(calculations);
    }

    public void setConditions(ArrayList<Condition> conditions) {
        this.conditions = conditions;
    }
}
