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
 *
 * this class is for handling Conditions set data which is defined by a
 * particular element list. each Conditions set may consists of several types of
 * conditions such enthalpy of mixing, phase equilibria etc for the given
 * element list. for example
 *
 * set1 : Ti-Nb-V
 *
 * enthalpy of mixing-bcc conditions T=1200K, P = 1 atm, composition (Ti) = 0.5,
 * composition(Nb) = 0.1 to 0.9 in the steps of 0.1
 *
 * enthalpy of mixing-bcc conditions P = 1 atm, composition (Ti) = 0.5,
 * composition (Nb) = 0.1 T = 500K to 1500K in the steps of 100K
 *
 * enthalpy of mixing-hcp conditions T = 1200K, P = 1 atm, composition (Ti) =
 * 0.5, composition(Nb)= 0.1 to 0.9 in the steps of 0.1
 *
 * enthalpy of mixing-hcp conditions P = 1 atm, composition (Ti) = 0.5,
 * composition (Nb) = 0.1 T = 500K to 1500K in the steps of 100K
 *
 * phase-equilibria bcc-liquid conditions P = 1 atm, T=1200K, x(Ti)=0.9
 *
 * phase-equilibria bcc-liquid conditions P = 1 atm x(Ti) = 0.1 to 0.9 in the
 * steps of 0.1
 *
 * set2 : Ti-Nb enthalpy of mixing-bcc conditions T = 1200K, P= 1 atm,
 * composition(Ti) = 0.9 (single)
 *
 * enthalpy of mixing-bcc conditions T = 1200K, P = 1 atm, composition(Ti) = 0.1
 * to 0.9 in the steps of 0.1(step)
 *
 * enthalpy of mixing-bcc conditions P = 1 atm, composition (Ti) = 0.5, T = 500K
 * to 1500K in the steps of 100K (step)
 *
 * It contains two datatype (i) ArrayList elementNames of String type and (ii) a
 * ArrayList calcTypes of the type CalcType
 */
public class CalcSet {

    ArrayList<String> elementNames;
    ArrayList<CalcType> calcTypes; // to store lists of CalcType

    public CalcSet() {
        elementNames = new ArrayList<>();
        calcTypes = new ArrayList<>();
    }

    public ArrayList<String> getElementNames() {
        return (this.elementNames);
    }

    public void setElementNames(ArrayList<String> elementNames) {
        this.elementNames = elementNames;
    }

    public ArrayList<CalcType> getCalcType() {
        return (this.calcTypes);
    }

    public void addCalcType(CalcType calcType) {
        this.calcTypes.add(calcType);
    }

    public void setCalcType(ArrayList<CalcType> calcTypes) {
        this.calcTypes = calcTypes;
    }
}
