/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import database.tdb;
import java.io.IOException;
import static java.lang.Math.abs;
import java.util.ArrayList;
import phase.GibbsModel;
import phase.A2TTERN;
import utils.io.Print;
import static utils.jama.Mat.LDsolve;

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
public class calculate {

    private final CalVars calvars;
    private tdb systdb; // to store database defined a particular element set such as (Ti-Nb-V-Zr)
    private ArrayList<CalcSet> calcSets; // to store lists of Conditions sets 
    private ArrayList<String> elementNames;
    private ArrayList<CalcType> calcTypes; // to store lists of CalcType
    private String method;
    private ArrayList<String> phases;
    private ArrayList<Conditions> conditions;
    private int p;//number of phases
    private int c;//number of components
    private int[] ncfList;
    private int[] tcfList;
    //unknown variables whose values/uptdated values will be determined after solving equilibrium matrix
    private double[] muList; //List of chemical poetntial values
    private double[] nList; //List of number of moles of phases 
    private double[] delNList;//List of change in number of moles of phases 
    private double T;
    private double delT;//Change in temperature
    private double P;
    private double delP;//Change in pressure
    private boolean[] isvarfix;//list keeping track of number of fixed unknowns, this list will be used for knowing no of columns in the equilibrium matrix
    //composition and internal variables 
    private ArrayList<ArrayList<Double>> X;
    double[][] delX; //Changes in composition
    //thermodyanmic Quantities
    GibbsModel[] phaseList;
    double[] G;
    double[] GT;
    double[] GP;
    double[][] cG;
    double[][] cT;
    double[][] cP;
    double[][][] cAB;
    double[][][] eMat;
    //Loop variables
    double errx;
    double maxerrx = 1.0E-11;

    public calculate(CalVars calvars) throws IOException {
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
        systdb = calvars.gettdb();// Reading Database
        calcSets = calvars.getcalcSet();//Reading Calculation Sets
        for (CalcSet calcSet : calcSets) {//Loop over each calculation set
            elementNames = calcSet.elementNames;// reading element name  
            c = elementNames.size();//raeding number of elements
            calcTypes = calcSet.calcTypes; //reading calculation type such as phase equilibria calculations
            System.out.println("calcSet:" + i + ", element List: " + elementNames);
            for (CalcType calcType : calcTypes) { //loop over each calculation type
                method = calcType.getMethod(); //reading method such as phase equilibria calculations
                phases = calcType.getPhases();// reading phases involved
                p = phases.size();//reading number of phases
                phaseList = genPhaseList();//Creating phaseList  
                conditions = calcType.getConditions();// reading values of the unknown variables 
                System.out.println("Method:" + method);
                System.out.println("Phases:" + phases);
                for (Conditions condition : conditions) {
                    T = condition.getT();
                    P = condition.getP();
                    X = condition.getX();
                    //System.out.println("dof:" + condition.dof());
                    //System.out.println("T:" + T);
                    //System.out.println("P:" + P);
                    //System.out.println("x:" + X);
                    switch (method) {
//                        case "u1": {
//                            //phase0.calU1(modData);
//                            break;
//                        }
                        case "HM": {
                            //calHm();
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
     * This method will generate list of phase objects based on Gibbs energy
     * parameters extracted from the database (systdb) for the given elements
     * (elementNames) and phases name (phases).
     *
     * @param systdb
     * @param elementNames
     * @param phases
     * @return
     */
    private GibbsModel[] genPhaseList() {
        GibbsModel[] phaselist = new GibbsModel[p];
        ArrayList<tdb.Parameter> paramList;
        for (String phase : phases) {//loop over phases
            //1. Extract phenomenological parameters from the database
            paramList = systdb.getPhaseParam(elementNames, phase);
            for (tdb.Parameter param : paramList) {

                param.print();
            }
            //2. Generate phase object
            //3. Append to phase list
        }
        return phaselist;
    }

    /**
     * Returns the enthalpy of mixing
     *
     *
     */
    public void calHm() {
        System.out.println(".calHm() method called");
        //Initialization
        double H_local;//y:Enthalpy
        X = new ArrayList<>();
        //T = condition.getT();
        //P = condition.getP();
        //ArrayList<ArrayList<Double>> x = condition.getX();
        //Double T_local;//param:Temperature
        String phase_local = phases.get(0); //Only one phase will be passed in case of enthalpy of mixing calculation
        //RK phaseModel = new RK(systdb, elementNames, phase_local, condition);
        String[] stdst = {""};
        double[] ecdis = {0, 0, 0, 0, 0, 0, 0., 0., -1500., 0., 0., 0., 2966.67, 3026.67, 4666.67, 5933.33, 6053.33, 9333.33};
        double[] evdis = {0, 0, 0, 0, 0, 0, 0., 0., 0., 0., 0., 0., 0, 0, 0, 0, 0, 0};
        //System.out.println(X);
        phaseList = new GibbsModel[1];
        GibbsModel a2ttern0 = new A2TTERN();
        //System.out.println(a2ttern0.getInitlIntVarValues(x.get(0)));
        //X.add(a2ttern0.getInitlIntVarValues(x.get(0)));
        GibbsModel a2ttern = new A2TTERN(stdst, ecdis, evdis, T, X.get(0));
        phaseList[0] = a2ttern;
        //a2ttern.printPhaseInfo();
        p = 1;
        c = a2ttern.getNumComp();
        //intialising unknown variable lists
        initializeVar(p, c);
        for (int i = 0; i < c; i++) {//chemical potentials are not fixed
            isvarfix[i] = false;
        }
        for (int i = 0; i < p; i++) {//changes in number of moles of the phases are not fixed
            isvarfix[c + i] = false;
        }
        isvarfix[p + c] = true; //Temperture fixed
        isvarfix[p + c + 1] = true;//pressure fixed
        calEq(phaseList);
        //System.out.println(a2ttern.calHmc());
        System.out.println(".calHm() method ended");
    }

    private void calEq(GibbsModel[] phaseList) {

        int nv; //initial guess of number of stable phases
        int nphase; //total number of phases and composition sets
        int nstph;//current number of stable phases
        int dormlink;//is start of list of phases temporarily set dormant
        int noofits;//current number of iterations
        int status;//for various things
        int nrel;//number of elements (components)
        int typesofcond;//typesofcond: types of conditions, =1 only massbal, =2 any conditions
        int maxsph;//number of fixed chemical potentials
        int nfixmu;//number of fixed chemical potentials
        int nfixph;//number of conditions representing fix phases
        int maxIter = 10;//Maximum number of intervals
        //Variables holding information about the fixed quatities/conditions
        //Variables corresponding to columns of the equilibrium matrix: mu,fixedphase, (T&P), 
        boolean[] muindep = new boolean[c]; //if chemical potentials are fixed, otherwise FALSE
        boolean[] phaseIndep = new boolean[p];//if phases are fixed
        boolean[] tpindep = new boolean[2]; //if variable T and P these are TRUE, otherwise FALSE
        tpindep[0] = false;//fixed T
        tpindep[1] = false;//fixed P

        double[][] eqmat;
        double[][] reducedEqmat;
        double[] sol;
        //Initializing varaibles

        //double[] n = new double[p];
        //int[] ncfList = new int[p];
        ncfList = new int[this.p];
        tcfList = new int[this.p];
        X = new ArrayList<>();
        G = new double[this.p];
        GT = new double[this.p];
        GP = new double[this.p];
        eMat = new double[this.p][][];
        cG = new double[this.p][];
        cT = new double[this.p][];
        cP = new double[this.p][];
        cAB = new double[this.p][][];
        delX = new double[p][];
        for (int i = 0; i < p; i++) {//loop over phases
            ncfList[i] = phaseList[i].getNcf();
            tcfList[i] = phaseList[i].getTcf();
            delX[i] = new double[tcfList[i]];
            X.add(phaseList[i].getX());
        }
        for (int i = 0; i < maxIter; i++) {//Loop over error in changes in number of moles of phases, temperture and pressure
            Print.f("Iter:", i, 0);
            System.out.println("X:" + X);
            Print.f("delX:", delX, 0);
            updatePhaseList();//updating phase objects with current values of compositions parameters as well as T,P 
            eqmat = calEqMat(phaseList);//calculating 
            reducedEqmat = calReducedEqMat(eqmat, muindep, phaseIndep, tpindep);
            //Print.f("reducedEqmat:", reducedEqmat, 0);
            sol = LDsolve(reducedEqmat);
            //Print.f("sol:", sol, 0);
            updateVar(sol); //update unkwown variables
            Print.f("muList:", muList, 0);
            //Print.f("nList:", nList, 0);
            Print.f("delNList:", delNList, 0);
            //Print.f("T:", T, 0);
            Print.f("delT:", delT, 0);
            //Print.f("P:", P, 0);
            Print.f("delP:", delP, 0);
            updateX();//update composition and internal variables
            if (abs(errx) < maxerrx) {
                break;
            }
        }
    }

    /**
     * This method updates composition variables of the phase object(s)
     */
    private void updatePhaseList() {
        for (int i = 0; i < p; i++) {//needs to be updated for intercation parameter updates
            //macroscopic variables
            phaseList[i].setT(T);//
            phaseList[i].setP(P);//
            //microscopic variables
            phaseList[i].setX(X.get(i));
            phaseList[i].calGderivatives();
            nList[i] = phaseList[i].getN();
            G[i] = phaseList[i].getG();
            GT[i] = phaseList[i].getGT();
            GP[i] = phaseList[i].getGP();
            eMat[i] = phaseList[i].getEMat();
            cG[i] = phaseList[i].getCG();
            cT[i] = phaseList[i].getCT();
            cP[i] = phaseList[i].getCP();
            cAB[i] = phaseList[i].getCAB();
            //Print.f("G(i):", G[i], 0);
            //Print.f("cG(i):", cG[i], 0);
            //Print.f("cT(i):", cT[i], 0);
        }
    }

    /**
     * This method calculates augmented equilibrium matrix based on Hillert's
     * method
     */
    private double[][] calEqMat(GibbsModel[] phaseList) {
        //This method will constrct so called equlibrium matrix of the size p+c+2
        //rows corresponding to p number of phases and extentive variables:
        //c number of components and 2 additional equations for Volume and entropy. 
        // number of columns corresponds to unknowns: 
        // c number of chemical potentials, changes in number of moles of the phases,
        // changes in temperature and pressure. hence there will be 9 blocks
        //equlibrium matrix is working like a stiffness matrix for the changes in system
        double[][] eqMat = new double[p + c + 2][p + c + 2 + 1]; //One extra colume for right-hand side part of the equation

        //block 1 size p X c    : compositions of p phases (rows) of c components (columns)         
        for (int i = 0; i < p; i++) {
            ArrayList<Double> tempX = X.get(i);
            for (int j = 0; j < c; j++) {
                eqMat[i][j] = tempX.get(ncfList[i] + j);
            }
        }
        //block 2 size p X p    : to be filled with zeros
        for (int i = 0; i < p; i++) {
            for (int j = c; j < p + c; j++) {
                eqMat[i][j] = 0.0;
            }
        }
        //block 3 size p X 2    : derivatives of Gibbs energy of p phases (rows)with T and P (columns): GT and GP
        for (int i = 0; i < p; i++) {
            eqMat[i][p + c] = GT[i];
            eqMat[i][p + c + 1] = GP[i];

        }
        //column 1 size p X 1 : Gibss energy of the p phases
        for (int i = 0; i < p; i++) {
            eqMat[i][p + c + 2] = G[i];
        }
        //block 4 size c X c    :
        for (int i = p; i < p + c; i++) {//loop over components
            for (int j = 0; j < c; j++) {//loop over components
                double temp = 0;
                for (int k = 0; k < p; k++) {//sum over phases
                    temp = temp + nList[k] * cAB[k][i - p][j];
                }
                eqMat[i][j] = temp;
            }
        }
        //block 5 size c X p    : compositions of c components (rows) of p phases(columns)
        for (int i = c; i < c + p; i++) {//loop over phases
            for (int j = p; j < p + c; j++) {//loop over components
                eqMat[j][i] = X.get(i - c).get(ncfList[i - c] + j - p);
            }
        }
        //block 6 size c X 2    :
        for (int i = p; i < p + c; i++) {//loop over compositions
            double temp = 0;
            for (int k = 0; k < p; k++) {//sum over phases
                temp = temp + nList[k] * cT[k][i - p + ncfList[k]];//index to be checked
            }
            eqMat[i][c + p] = temp;
            temp = 0;
            for (int k = 0; k < p; k++) {//sum over phases
                temp = temp + nList[k] * cP[k][i - p + ncfList[k]];//index to be checked
            }
            eqMat[i][c + p + 1] = temp;
        }
        //column 2 size c X 1 : summation of Gibbs energy derivatives CiG of c components
        for (int i = p; i < p + c; i++) {//loop over components
            double temp = 0;
            for (int k = 0; k < p; k++) {//sum over phases
                temp = temp + nList[k] * cG[k][i - p + ncfList[k]];
            }
            eqMat[i][c + p + 2] = -temp;
        }
        //block 7 size 2 X c    :
        for (int i = p + c; i < p + c + 2; i++) {
            for (int j = 0; j < c; j++) {
                eqMat[i][j] = 0.0;// to be filled later
            }
        }
        //block 8 size 2 X p    :
        for (int i = p + c; i < p + c + 2; i++) {
            for (int j = c; j < p + c; j++) {
                eqMat[i][j] = 0.0;//to be filled later
            }
        }
        //block 9 size 2 X 2    :
        for (int i = p + c; i < p + c + 2; i++) {
            for (int j = p + c; j < p + c + 2; j++) {
                eqMat[i][j] = 0.0;// to be filled later
            }
        }
        //col 3 size 2 X 1    :
        for (int i = p + c; i < p + c + 2; i++) {
            eqMat[i][p + c + 2] = 0.0;// to be filled later
        }
        //Print.f("eqMat:", eqMat, 0);
        return eqMat;
    }

    /**
     * This method returns reduced eqMat based on list of unknown
     * quantities/parameters, which includes chemical potentials (for each
     * component,c), changes in number of moles (of each phase, p) and changes
     * in Temperature and Pressure (2)
     *
     * @param phaseList
     * @return
     */
    private double[][] calReducedEqMat(double[][] eqMat, boolean[] muindep, boolean[] phaseIndep, boolean[] tpindep) {
        int p = this.p;
        int c = this.c;
        double[][] reducedEqMat = eqMat;
        //Changes in columns
        //  1. Cases of fixed mu are to be delt with readjusting that particular mu term from 
        //      left side to the right side, this will result in removal of that particular column
        //  2. Cases of fixed T and P will result in directly deleting corresponding columns

        if ((tpindep[0] == false) && (tpindep[0] == true)) {//Case corresponding to ONLY fixed temperature,
            reducedEqMat = removeCol(reducedEqMat, p + c);
        }
        if ((tpindep[0] == true) && (tpindep[0] == false)) {//Case corresponding to ONLY fixed pressure,
            reducedEqMat = removeCol(reducedEqMat, p + c + 1);
        }
        if ((tpindep[0] == false) && (tpindep[0] == false)) {//Case corresponding to both fixed temperature and pressure,
            reducedEqMat = removeCol(reducedEqMat, p + c);
            reducedEqMat = removeCol(reducedEqMat, p + c);
        }

        //  3. Cases of specifying fixed number of a phases instead of moles of a component will result in 
        //      slightly different formulation and these cases will be delt separately
        //Changes in rows
        //  1. cases of unstable phases to be delt with removing corresponding Gibbs-Duhem relation and also
        //      excluding this phase from the summation terms. this will result in reduced number of rows
        //  2. If conditions on V, entropy or any other extensive proporties are not specified, these row will
        //      be dropped
        reducedEqMat = removeRow(reducedEqMat, this.p + this.c);//removing two last rown corresponding to conditions on V and S
        reducedEqMat = removeRow(reducedEqMat, this.p + this.c);//removing two last rown corresponding to conditions on V and S

        //System.out.println("rows:" + reducedEqMat.length + ", columns:" + reducedEqMat[0].length);
        //System.out.println("p + c:" + (p + c));
        return (reducedEqMat);
    }

    /**
     *
     * @param p
     * @param c
     */
    private void initializeVar(int p, int c) {
        muList = new double[c];
        nList = new double[p];
        delNList = new double[p];
        isvarfix = new boolean[p + c + 2];//Intializing list
    }

    void updateVar(double[] sol) {
        // Solution list consists chemical potentials, changes in number of moles of phases, temperture and pressure 
        int count = 0;
        for (int i = 0; i < p + c + 2; i++) {//loop over all the unknown variables

        }
        for (int i = 0; i < c; i++) {//update chemical potentials 
            if (isvarfix[count] == false) {
                muList[i] = sol[count];
            }
            count = count + 1;
        }
        for (int i = 0; i < p; i++) {//changes in number of moles of the phases are not fixed
            if (isvarfix[count] == false) {
                delNList[i] = sol[count];
                nList[i] = nList[i] + delNList[i];
            }
            count = count + 1;
        }
        if (isvarfix[count] == false) {
            delT = sol[count];
            T = T + delT;
        }
        count = count + 1;
        if (isvarfix[count] == false) {
            delP = sol[count];
            P = P + delP;
        }
    }

    /**
     * This method updates compositional and internal parameters
     */
    void updateX() {
        //Print.f("muList:", muList, 0);
        errx = 0.0;
        for (int i = 0; i < p; i++) {//loop over phases
            for (int j = 0; j < tcfList[i]; j++) {//loop over variables
                delX[i][j] = cG[i][j];
                setValue(X, i, j, X.get(i).get(j) + cG[i][j]); //Adding CiG values 
                if (isvarfix[p + c] == false) {//Case corresponding to T is not fixed and deltaT is not zero
                    delX[i][j] = delX[i][j] + cT[i][j] * delT;
                    setValue(X, i, j, X.get(i).get(j) + cT[i][j] * delT);
                }
                if (isvarfix[p + c] == false) {//Case corresponding to P is not fixed and deltaP is not zero
                    delX[i][j] = delX[i][j] + cP[i][j] * delP;
                    setValue(X, i, j, X.get(i).get(j) + cP[i][j] * delP);
                }
                //Print.f("eMat[i][j]:", eMat[i][j], 0);
                for (int k = 0; k < c; k++) {//loop over chemical potentials of the components
                    delX[i][j] = delX[i][j] + eMat[i][j][ncfList[i] + k] * muList[k];
                    setValue(X, i, j, X.get(i).get(j) + eMat[i][j][ncfList[i] + k] * muList[k]);
                }
                errx = errx + delX[i][j];
            }
        }
        Print.f("errx:", errx, 0);
        //Print.f("delX:", delX, 0);
        //System.out.println("X:" + X);
    }

    /**
     *
     * @param mat
     * @param row
     * @return
     */
    private double[][] removeRow(double[][] mat, int row) {
        double[][] realCopy = new double[mat.length - 1][mat[0].length];
        int count = 0;
        if (row == mat.length - 1) {
            for (int r = 0; r < mat.length - 1; r++) {
                for (int c = 0; c < mat[0].length; c++) {
                    if (row == r) {
                        r++;
                    }
                    realCopy[count][c] = mat[r][c];
                }
                count++;
            }
        } else {
            //System.out.println("row:" + row);
            //System.out.println("rows:" + mat.length + ", columns:" + mat[0].length);
            for (int r = 0; r < mat.length; r++) {
                //System.out.println("r:" + r);
                for (int c = 0; c < mat[0].length; c++) {
                    if (row == r) {
                        r++;
                    }
                    realCopy[count][c] = mat[r][c];
                }
                count++;
            }
        }
        return realCopy;
    }

    /**
     *
     * @param mat
     * @param colRemove
     * @return
     */
    private static double[][] removeCol(double[][] mat, int colRemove) {
        int row = mat.length;
        int col = mat[0].length - 1;
        int oldCol = mat[0].length;

        double[][] newArray = new double[row][col];

        for (int i = 0; i < row; i++) {
            for (int j = 0, k = 0; j < oldCol && k < col; j++) {
                if (j != colRemove) {
                    newArray[i][k++] = mat[i][j];
                }
            }
        }

        return newArray;
    }

    /**
     *
     * @param list
     * @param row
     * @param column
     * @param value
     */
    public static void setValue(ArrayList<ArrayList<Double>> list, int row, int column, double value) {
        list.get(row).set(column, value);
    }
}
