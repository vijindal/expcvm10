/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package phase;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 *
 * @author JEDIABJ77
 */
public interface PHASEBINCE {

    //set methods
    public void setNP(int np_In);// Total number of model paramters

    public void setR(double R_local);// Universal Gas contant

    public void setReferenceStateParameters(String[] stdst_In);//Standard State

    public void setEdis(double[] ei_In);//Model paramters

    public void setEmat(String infile) throws FileNotFoundException, IOException;//2014-07-15 : for setting interdependencies between model paramters, if any

    public void setT(double T_local) throws IOException;// Temperature

    public void setX(double x0_local) throws IOException;//Composition, xB

    //get methods   
    public int getNP();

    public String getPhaseTag();

    public String getPhaseId();

    public double getR();

    public double getT();

    public double getX();

    public void getEdis(double[] E_out);//vj-2013-03-24

    public void getETrans(double[][] emat_out);

    public double getG0A() throws IOException;

    public double getG0B() throws IOException;
    //Other methods

    public void printPhaseInfo() throws IOException;
    //Thermodynamic Calculations

    public void calU1(double[] modDataIn) throws IOException;//vj-2013-06-24

    public double calLRO() throws IOException;

    public double calG() throws IOException;//Total Gibbs energy

    public double calH() throws IOException;

    public double calS() throws IOException;

    public double calGm() throws IOException;//Gibbs energy of mixing

    public double calHm() throws IOException;

    public double calSm() throws IOException;

    public double[] calGAB() throws IOException;

    public double[] calGmAB() throws IOException;

    public double[] calSmAB() throws IOException;
    //First order derivatives

    public double calDGx() throws IOException;

    public double calDGmx() throws IOException;

    public double[] calDMUx() throws IOException;

    public double calDGT() throws IOException;

    public double[] calDMUT() throws IOException;

    public double[] calDHe() throws IOException;

    public double[] calDSe() throws IOException;

    public double[][] calDMUe() throws IOException;
    //Second order derivatives

    public double calDGxx() throws IOException;

    public double calDGTx() throws IOException;
    //Third order derivatives

    public double calDGxxx() throws IOException;

    public double calDGTxx() throws IOException;
    //Fourth order derivatives

    public double calDGxxxx() throws IOException;

    public double calDGTxxx() throws IOException;

}
