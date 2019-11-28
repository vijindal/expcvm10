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
 * @author admin
 */
public class calculate {

    private void equilibrium(tdb dbf, ArrayList<String> comps, ArrayList<String> phases,
            ArrayList<String> conditions, String output, String model,
            int verbose) {

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
