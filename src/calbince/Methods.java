package calbince;

import binutils.io.Print;
import binutils.jama.LUDecomposition;
import binutils.jama.Mat;
import binutils.jama.Matrix;
import binutils.io.Utils;
import java.io.IOException;
import java.text.DecimalFormat;
import phase.PHASEBINCE;

/**
 *
 * @author JEDIABJ77
 */
public class Methods {

    private PHASEBINCE phase0 = null;
    private PHASEBINCE phase1 = null;
    private PHASEBINCE phase2 = null;
    private PhaseData phasedata = null;
    private double modData[] = new double[4];// Array for input and output
    private final String cname = "Methods";
    static DecimalFormat df = new DecimalFormat("#.########");

    public Methods(PhaseData phasedataIn) throws IOException {//vj-2013-04-04
        Print.f(cname + ".constructor methed called", 6);
        this.phasedata = phasedataIn;
        //phasedata.print();
        Print.f(cname + ".constructor method ended", 6);
    }

    public void funcsCal(ExptDatum datum) throws IOException {//vj-2013-03-24-added
        Print.f(cname + ".funcsCal method called", 7);
        setPhase(datum);//vj-2013-04-01-create phase object(s) according to datum
        switch (datum.getBList()) {
            /*
             * Single phase methods
             */
            case "u1"://vj-2013-06-24
            // case "SRO":
            // case "Cp":
            // case "Th":
            // case "IsoGX"://T fixed , calc X //Iso-G
            // case "IsoGT"://X fixed, calc. T //Iso-G
            // case "SPX"://T fixed , calc. X1/X2//Spinodal
            // case "SPT"://X1/X2 fixed, calc. T//Spinodal
            case "Hm"://vj-2012-07-19
            case "H":
            case "Sm"://vj-2012-07-19
            case "S"://vj-2012-06-22
            case "Gm"://vj-2012-07-19  
            case "G"://vj-2102-05-17
            case "aA":
            case "aB":
            case "SmA":
            case "SmB":
            case "MGX1":
            case "MGX2":
            case "MGT1":
            case "MGT2":
            case "SPT":
            case "COPT":
            case "COPX": {
                modData = new double[4];
                initi(datum, modData);
                switch (datum.getBList()) {
                    case "u1": {
                        phase0.calU1(modData);
                        break;
                    }
                    case "Hm": {
                        calHm(modData);
                        break;
                    }
                    case "H": {
                        calH(modData);
                        break;
                    }
                    case "Sm": {
                        calSm(modData);
                        break;
                    }
                    case "S": {
                        calS(modData);
                        break;
                    }
                    case "Gm": {
                        calGm(modData);
                        break;
                    }
                    case "G": {
                        calG(modData);
                        break;
                    }
                    case "aA": {
                        calaA(modData);
                        break;
                    }
                    case "aB": {
                        calaB(modData);
                        break;
                    }
                    case "SmA": {
                        calSmA(modData);
                        break;
                    }
                    case "SmB": {
                        calSmB(modData);
                        break;
                    }
                    case "MGX1": {
                        calMGX1(modData);
                        if (Math.abs(modData[2] - modData[3]) < 1E-05) {
                            throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change yMod OR x1");
                        }
                        break;
                    }
                    case "MGX2": {
                        calMGX2(modData);
                        if (Math.abs(modData[2] - modData[3]) < 1E-05) {
                            throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change yMod OR x1");
                        }
                        break;
                    }
                    case "MGT1": {
                        calMGT1(modData);
                        if (Math.abs(modData[2] - modData[3]) < 1E-05) {
                            throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change x1");
                        }
                        break;
                    }
                    case "MGT2": {
                        calMGT2(modData);
                        if (Math.abs(modData[2] - modData[3]) < 1E-05) {
                            throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change x1");
                        }
                        break;
                    }
                    case "SPT": {
                        calSPT(modData);//vj-2012-06-23
                        break;
                    }
                    case "COPT": {
                        calCOPT(modData);//vj-2012-06-23
                        break;
                    }
                    case "COPX": {
                        calCOPX(modData);//vj-2012-06-23
                        break;
                    }
                    default:
                        System.err.println("Invalid Case for Single Phase");
                }// Inside switch closed
                datum.setYmod(modData[0]);
                datum.setX(modData[1]);
                datum.setX1(modData[2]);
                datum.setX2(modData[3]);
                break;
            }// Single Phase Cases Closed
            /*
             * Two phase methods
             */
            case "ISOGX":
            case "CXXA":
            case "CXXB":
            case "CXTA":
            case "CXTB":
            case "CRPT":
            case "MXX": { //Cmin/Cmax
                modData = new double[4];
                initi(datum, modData);//Initialize modData
                switch (datum.getBList()) {
                    case "ISOGX": {//T is fised calculate composition
                        calISOGX(modData);//vj-2012-06-19
                        break;
                    }
                    case "CXXA": {//T fixed , calc. Xa/XÃƒÅ¸
                        calCXXA(modData);
                        break;
                    }
                    case "CXXB": {
                        calCXXB(modData);
                        break;
                    }
                    case "CXTA": {
                        //initCXT(datum, modData);
                        calCXTA(modData);
                        break;
                    }
                    case "CXTB": {
                        calCXTB(modData);
                        break;
                    }
                    case "MXX": {
                        calMXX(modData);
                        break;
                    }
                    case "CRPT": {
                        calCRPT(modData);
                        break;
                    }
                    default:
                        System.out.println("Invalid Case for Two Phases");
                        break;
                }
                datum.setYmod(modData[0]);
                datum.setX(modData[1]);
                datum.setX1(modData[2]);
                datum.setX2(modData[3]);
                break;
            }
            /*
             * Three phase methods
             */
            case "CX31T"://Three phase with one phase decomposing
            case "CX32T"://Three phase with one phase decomposing
            case "CX33T": {
                modData = new double[4];//vj-2012-05-21
                initi(datum, modData);//vj-2012-05-21
                switch (datum.getBList()) {
                    case "CX31T": {
                        calCX31(modData);
                        break;
                    }
                    case "CX32T":{
                        calCX33(modData);//To be checked
                        break;
                    }
                    case "CX33T": {
                        calCX33(modData);
                        break;
                    }
                    default:
                        System.out.println("Invalid Case for Three Phases");
                        break;
                }
                datum.setYmod(modData[0]);
                datum.setX(modData[1]);
                datum.setX1(modData[2]);
                datum.setX2(modData[3]);
                break;
            }
            case "CX3T": {//All three phases are distinct phases
                modData = new double[4];//vj-2012-05-21
                initi(datum, modData);//vj-2012-05-21
                calCX3(modData);
                datum.setYmod(modData[0]);
                datum.setX(modData[1]);
                datum.setX1(modData[2]);
                datum.setX2(modData[3]);
                break;
            }
            default: {
                Print.f("Did not Met any predefined method!", 0);
                break;
            }
        }
        Print.f(cname + ".funcsCal method closed", 7);
    }//vj-2013-04-01-funcsOpt for calModel

    public void funcsOpt(ExptDatum datum_in, double[] dyda_out) throws IOException {//vj-2013-04-01-funcs for OptMrq
        Print.f(cname + ".funcsOpt method called", 7);
        setPhase(datum_in); //vj-2013-04-06-create phase object(s) according to datum_in
        for (int temp = 0; temp < dyda_out.length; temp++) {//Input array initialized to zero
            dyda_out[temp] = 0;
        }
        switch (datum_in.getBList()) {
            /*
             * Single phase methods
             */
            // case "SRO":
            // case "Cp":
            // case "Th":
            // case "IsoGX"://T fixed , calc X //Iso-G
            // case "IsoGT"://X fixed, calc. T //Iso-G
            // case "SPX"://T fixed , calc. X1/X2//Spinodal
            // case "SPT"://X1/X2 fixed, calc. T//Spinodal
            case "Hm"://vj-2012-07-19
            case "H":
            case "Sm"://vj-2012-07-19
            case "S"://vj-2012-06-22
            case "Gm"://vj-2012-07-19  
            case "G"://vj-2102-05-17
            case "aA":
            case "aB":
            case "MGX1":
            case "MGX2":
            case "MGT1":
            case "MGT2":
            case "COPX":
            case "COPT": {
                double dyda_local[] = new double[phase0.getNP()];

                modData = new double[4];
                initi(datum_in, modData);
                switch (datum_in.getBList()) {
                    case "Hm": {
                        calHm(modData, dyda_local);
                        break;
                    }
                    case "H": {
                        calH(modData, dyda_local);
                        break;
                    }
                    case "Sm": {
                        calSm(modData, dyda_local);
                        break;
                    }
                    case "S": {
                        calS(modData, dyda_local);
                        break;
                    }
                    case "Gm": {
                        calGm(modData);
                        break;
                    }
                    case "G": {
                        calG(modData);
                        break;
                    }
                    case "aA": {
                        calaA(modData, dyda_local);
                        break;
                    }
                    case "aB": {
                        calaB(modData, dyda_local);
                        break;
                    }
                    case "MGX1": {
                        calMGX1(modData, dyda_local);
//                        if (Math.abs(modData[0] - modData[2]) < 1E-05) {
//                            throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change yMod OR x1");
//                        }
                        break;
                    }
                    case "MGX2": {
                        calMGX2(modData, dyda_local);
//                        if (Math.abs(modData[0] - modData[2]) < 1E-05) {
//                            throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change yMod OR x1");
//                        }
                        break;
                    }
                    case "MGT1": {
                        calMGT1(modData, dyda_local);
                        if (Math.abs(modData[2] - modData[3]) < 1E-05) {
                            throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change x1");
                        }
                        break;
                    }
                    case "MGT2": {
                        calMGT2(modData, dyda_local);
                        if (Math.abs(modData[2] - modData[3]) < 1E-05) {
                            throw new ArithmeticException("Model:funcs() xAlfa == xBeta. Change x1");
                        }
                        break;
                    }
                    case "COPX": {
                        calCOPX(modData);//vj-2012-06-23
                        break;
                    }
                    case "COPT": {
                        calCOPT(modData);//vj-2012-06-23
                        //Prnt.f(modData,"modData:",0);
                        //phase0.calCOPT(modData, dyda_local);
                        break;
                    }
                    default:
                        System.err.println("Invalid Case for Single Phase");
                }// Inside switch closed
                datum_in.setYmod(modData[0]);//vj-2013-04-09
                datum_in.setX(modData[1]);//vj-2013-04-01
                datum_in.setX1(modData[2]);//vj-2013-04-01
                datum_in.setX2(modData[3]);//vj-2013-04-01
                double dyda_temp[] = new double[phase0.getNP()];
                System.arraycopy(dyda_local, 0, dyda_temp, 0, dyda_temp.length);
//                double[][] emat = new double[phase0.getNP()][phase0.getNP()];
//                phase0.getETrans(emat);
//                dyda_temp = Mat.mul(Mat.transpose(emat), dyda_temp);
                int pIndex = phasedata.getPid(datum_in.getPid(0, 0), datum_in.getPid(0, 1), datum_in.getPid(0, 2));
                System.arraycopy(dyda_temp, 0, dyda_out, phasedata.getAindex(pIndex), dyda_temp.length);
                phase0 = null;
                modData = null;
                break;
            }// Single Phase Cases Closed

            /*
             * Two phase methods
             */
            case "ISOGX":
            case "CXXA":
            case "CXXB":
            case "CXTA":
            case "CXTB":
            case "CRPT":
            case "MXX": { //Cmin/Cmax
                double dyda_local[] = new double[phase0.getNP() + phase1.getNP()];
                modData = new double[4];
                initi(datum_in, modData);//Initialize modData
                switch (datum_in.getBList()) {
                    case "ISOGX": {//T is fixed calculate composition
                        calISOGX(modData, dyda_local);//vj-2012-06-19
                        break;
                    }
                    case "CXXA": {//T fixed , calc. Xa/XÃƒÅ¸
                        calCXXA(modData, dyda_local);
                        break;
                    }
                    case "CXXB": {
                        calCXXB(modData, dyda_local);
                        break;
                    }
                    case "CXTA": {
                        //initCXT(datum_in, modData);
                        calCXTA(modData, dyda_local);
                        break;
                    }
                    case "CXTB": {
                        calCXTB(modData, dyda_local);
                        break;
                    }
                    case "MXX": {
                        calMXX(modData, dyda_local);
                        break;
                    }
                    case "CRPT": {
                        calCRPT(modData, dyda_local);
                        break;
                    }
                    default:
                        System.out.println("Invalid Case for Two Phases");
                        break;
                }
                datum_in.setYmod(modData[0]);//vj-2013-04-09
                datum_in.setX(modData[1]);//vj-2013-04-01
                datum_in.setX1(modData[2]);//vj-2013-04-01
                datum_in.setX2(modData[3]);//vj-2013-04-01
                //Copying dyda_local obtained above to dyda_out  
                double dyda_temp0[] = new double[phase0.getNP()];
                double dyda_temp1[] = new double[phase1.getNP()];
                System.arraycopy(dyda_local, 0, dyda_temp0, 0, dyda_temp0.length);
                System.arraycopy(dyda_local, dyda_temp0.length, dyda_temp1, 0, dyda_temp1.length);
                int pIndex0 = phasedata.getPid(datum_in.getPid(0, 0), datum_in.getPid(0, 1), datum_in.getPid(0, 2));
                int pIndex1 = phasedata.getPid(datum_in.getPid(1, 0), datum_in.getPid(1, 1), datum_in.getPid(1, 2));
                System.arraycopy(dyda_temp0, 0, dyda_out, phasedata.getAindex(pIndex0), dyda_temp0.length);
                System.arraycopy(dyda_temp1, 0, dyda_out, phasedata.getAindex(pIndex1), dyda_temp1.length);
                phase0 = null;
                phase1 = null;
                modData = null;
                break;
            }
            /*
             * Three phase methods
             */
            case "CX31T"://Three phase with one phase decomposing
            case "CX32T"://Three phase with one phase decomposing
            case "CX33T": {
                double dyda_local[] = new double[phase0.getNP() + phase1.getNP()];
                modData = new double[4];//vj-2012-05-21
                initi(datum_in, modData);//vj-2012-05-21
                switch (datum_in.getBList()) {
                    case "CX31T": {
                        calCX31(modData, dyda_local);
                        break;
                    }
                    case "CX32T": {
                        calCX32T(modData, dyda_local);
                        break;
                    }
                    case "CX33T": {
                        calCX33(modData, dyda_local);
                        break;
                    }
                    default:
                        System.out.println("Invalid Case for Three Phases");
                        break;
                }
                datum_in.setYmod(modData[0]);//vj-2013-04-09
                datum_in.setX(modData[1]);//vj-2013-04-01
                datum_in.setX1(modData[2]);//vj-2013-04-01
                datum_in.setX2(modData[3]);//vj-2013-04-01
                double dyda_temp0[] = new double[phase0.getNP()];
                double dyda_temp1[] = new double[phase1.getNP()];
                System.arraycopy(dyda_local, 0, dyda_temp0, 0, dyda_temp0.length);
                System.arraycopy(dyda_local, dyda_temp0.length, dyda_temp1, 0, dyda_temp1.length);
                //double[][] emat0 = new double[phase0.getNP()][phase0.getNP()];
                //phase0.getETrans(emat0);
                //double[][] emat1 = new double[phase1.getNP()][phase1.getNP()];
                //phase1.getETrans(emat1);
                //dyda_temp0 = Mat.mul(Mat.transpose(emat0), dyda_temp0);
                //dyda_temp1 = Mat.mul(Mat.transpose(emat1), dyda_temp1);
                int pIndex0 = phasedata.getPid(datum_in.getPid(0, 0), datum_in.getPid(0, 1), datum_in.getPid(0, 2));
                int pIndex1 = phasedata.getPid(datum_in.getPid(1, 0), datum_in.getPid(1, 1), datum_in.getPid(1, 2));
                System.arraycopy(dyda_temp0, 0, dyda_out, phasedata.getAindex(pIndex0), dyda_temp0.length);
                System.arraycopy(dyda_temp1, 0, dyda_out, phasedata.getAindex(pIndex1), dyda_temp1.length);
                phase0 = null;
                phase1 = null;
                modData = null;
                break;
            }
            case "CX3T": {//All three phases are distinct phases
                double dyda_local[] = new double[phase0.getNP() + phase1.getNP() + phase2.getNP()];
                modData = new double[4];//vj-2012-05-21
                initi(datum_in, modData);//vj-2012-05-21
                calCX3(modData, dyda_local);
                datum_in.setY(modData[0]);//vj-2013-04-01
                datum_in.setX(modData[1]);//vj-2013-04-01
                datum_in.setX1(modData[2]);//vj-2013-04-01
                datum_in.setX2(modData[3]);//vj-2013-04-01
                double dyda_temp0[] = new double[phase0.getNP()];
                double dyda_temp1[] = new double[phase1.getNP()];
                double dyda_temp2[] = new double[phase2.getNP()];
                System.arraycopy(dyda_local, 0, dyda_temp0, 0, dyda_temp0.length);
                System.arraycopy(dyda_local, dyda_temp0.length, dyda_temp1, 0, dyda_temp1.length);
                System.arraycopy(dyda_local, dyda_temp1.length, dyda_temp2, 0, dyda_temp2.length);
                int pIndex0 = phasedata.getPid(datum_in.getPid(0, 0), datum_in.getPid(0, 1), datum_in.getPid(0, 2));
                int pIndex1 = phasedata.getPid(datum_in.getPid(1, 0), datum_in.getPid(1, 1), datum_in.getPid(1, 2));
                int pIndex2 = phasedata.getPid(datum_in.getPid(2, 0), datum_in.getPid(2, 1), datum_in.getPid(2, 2));
                System.arraycopy(dyda_temp0, 0, dyda_out, phasedata.getAindex(pIndex0), dyda_temp0.length);
                System.arraycopy(dyda_temp1, 0, dyda_out, phasedata.getAindex(pIndex1), dyda_temp1.length);
                System.arraycopy(dyda_temp2, 0, dyda_out, phasedata.getAindex(pIndex2), dyda_temp1.length);
                phase0 = null;
                phase1 = null;
                phase2 = null;
                modData = null;
                break;
            }
            default: {
                Print.f(cname + ".funcsOpt did not Met any predefined calculation Type!", 0);
                System.exit(-1);
                break;
            }
        }
        Print.f(cname + ".funcsOpt method closed", 7);
    }

    void setei(double[] a_In) {
        phase0.setEdis(a_In);
    }

    void setei(double[] ei_In1, double[] ei_In2) {
        phase0.setEdis(ei_In1);
        phase1.setEdis(ei_In2);
    }

    void setei(double[] ei_In1, double[] ei_In2, double[] ei_In3) {
        phase0.setEdis(ei_In1);
        phase1.setEdis(ei_In2);
        phase2.setEdis(ei_In3);
    }

    private void initi(ExptDatum datum, double[] modDataIn) throws IOException {
        modDataIn[0] = datum.getYmod();//Globals.y[i];
        modDataIn[1] = datum.getX();
        modDataIn[2] = datum.getX1();
        modDataIn[3] = datum.getX2();
    }

    private void initCXT(ExptDatum datum, double[] modDataIn) throws IOException {//vj-2012-07-04//Returns initial compositions for MG 
        Print.f("Model.initCXT() method called", 7);
        //prnt.f("dindex = " + datum.getdIndex());
        //prnt.Array(modData, "ModdataIn", "printing moddata input to CXX");
        modDataIn[0] = datum.getYmod();
        modDataIn[1] = datum.getX();
        modDataIn[2] = datum.getX1();
        modDataIn[3] = datum.getX2();
        //prnt.Array(modData, "ModdataIn", "printing moddata output from CXX");
    }

    private void initMGX(ExptDatum datum, double[] modDataIn) throws IOException {//vj-2012-07-04//Returns initial compositions for MG 
        Print.f("Model.initMGX() method called", 7);
        modDataIn[0] = datum.getYmod();
        modDataIn[1] = datum.getX();
        modDataIn[2] = datum.getX1();
        modDataIn[3] = datum.getX2();
        double[][] GList = calGList(modDataIn[1], 0.01, phase0);
        double[][] G = new double[101][2];
        double xTrial, GTrial;
        //boolean iStable;
        boolean iLeft;
        int i = 0;
        G[i][0] = GList[0][0];
        G[i][1] = GList[0][1];
        i = i + 1;
        G[i][0] = GList[1][0];
        G[i][1] = GList[1][1];
        int j = 2;
        xTrial = GList[j][0];
        GTrial = GList[j][1];
        while (j < 100) {
            //iStable = isStable(G[i - 1][0], G[i][0], xTrial, G[i - 1][1], G[i][1], GTrial);
            iLeft = isLeft(G[i - 1][0], G[i][0], xTrial, G[i - 1][1], G[i][1], GTrial);
            //if (iStable) {
            if (iLeft) {
                i = i + 1;//current position of counter for G
                G[i][0] = xTrial;
                G[i][1] = GTrial;
                j = j + 1;
                xTrial = GList[j][0];
                GTrial = GList[j][1];
            } else {
                i = i - 1;
            }
            //System.out.println("i:" + i + ", j:" + j + ", iStable:" + iStable);
            //System.out.println("i:" + i + ", j:" + j + ", iLeft:" + iLeft);
        }
        //prnt.f(GList0, "GList0:", 0);
        double xl = 0.0, xr = 1.0;
        for (int k = 0; k < 99; k++) {
            if (G[k + 1][0] - G[k][0] > 0.02) {
                xl = G[k][0];
                xr = G[k + 1][0];
                break;
            }
        }
        //prnt.f(G, "G:", 0);
        //System.out.println("xl:" + xl + ", xr:" + xr);
        modDataIn[0] = xl;
        modDataIn[2] = xr;
        Print.f("Model.initMGX() method called", 7);
    }

    private boolean isLeft(double xB1, double xB2, double xB3, double G1N, double G2N, double G3N) {//vj-2012-07-05:based on Graham scan
        //System.out.println("xB1:" + xB1 + ", xB2:" + xB2 + ", xB3:" + xB3);
        //System.out.println("G1:" + G1N + ", G2:" + G2N + ", G3:" + G3N);
        double m1 = Math.atan((G2N - G1N) / (xB2 - xB1));
        double m2 = Math.atan((G3N - G2N) / (xB3 - xB2));
        //System.out.println(xB2 + "," + G2N + ", m1:" + m1 + ", m2:" + m2 + ", m2-m1:" + (m2 - m1));
        return (((m2 - m1) >= 0.0));
    }
    ///////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the enthalpy of mixing
     *
     * @param modDataIn {ymod, temperature, composition, x3}
     * @throws IOException
     */
    public void calHm(double[] modDataIn) throws IOException {
        Print.f(phase0.getPhaseTag() + ".calHm() method called", 7);
        //Initialization
        double H_local;//y:Enthalpy
        double T_local = modDataIn[1];//param:Temperature
        double x_local = modDataIn[2];//x:Composition
        phase0.setT(T_local);
        phase0.setX(x_local);
        //calculation
        H_local = phase0.calHm();
        //Output
        modDataIn[0] = H_local;
        Print.f(phase0.getPhaseTag() + ".calHm() method ended", 7);
    }

    public void calHm(double[] modDataIn, double[] dydaIn) throws IOException {//2012-02-23(VJ): Added
        Print.f(phase0.getPhaseTag() + ".calH() method called", 7);
        calHm(modDataIn);
        double[] DHeN = phase0.calDHe();
        System.arraycopy(DHeN, 0, dydaIn, 0, phase0.getNP());
        //Print.f("dydaIn", dydaIn, 1);
        Print.f(phase0.getPhaseTag() + ".calH() method ended", 7);
    }

    public void calH(double[] modDataIn) throws IOException {//2012-02-23(VJ): Added
        Print.f(phase0.getPhaseTag() + ".calH() method called", 7);
        //Initialization
        double H_local;//y:Enthalpy
        double T_local = modDataIn[1];//x:Composition
        double x_local = modDataIn[2];//param:Temperature
        phase0.setT(T_local);
        phase0.setX(x_local);
        //Prnt.f(getU(), "U:", 0);
        //calculation
        H_local = phase0.calH();
        //Output
        modDataIn[0] = H_local;
        Print.f(phase0.getPhaseTag() + ".calH() method ended", 7);
    }

    public void calH(double[] modDataIn, double[] dydaIn) throws IOException {//2012-02-23(VJ): Added
        Print.f(phase0.getPhaseTag() + ".calH() method called", 7);
        calH(modDataIn);
        double[] DHeN = phase0.calDHe();
        System.arraycopy(DHeN, 0, dydaIn, 0, phase0.getNP());
        Print.f(phase0.getPhaseTag() + ".calH() method ended", 7);
    }

    public void calSm(double[] modDataIn) throws IOException {//vj-2012-06-22//entropy of mixing
        Print.f(phase0.getPhaseTag() + ".calSm() method called", 7);
        //Initialization
        double S_local;
        double T_local = modDataIn[1];//Temperature
        double x_local = modDataIn[2];//Composition 
        phase0.setT(T_local);
        phase0.setX(x_local);
        //calculation
        S_local = phase0.calSm();
        //Output
        modDataIn[0] = S_local;
        Print.f(phase0.getPhaseTag() + ".calSm() method ended with S=" + S_local, 2);
        Print.f(phase0.getPhaseTag() + ".calSm() method ended", 7);
    }

    public void calSm(double[] modDataIn, double[] dydaIn) throws IOException {//vj-2012-06-22//entropy of mixing
        Print.f(phase0.getPhaseTag() + ".calSm() method called", 7);
        calSm(modDataIn);
        double[] DSeN = phase0.calDSe();
        System.arraycopy(DSeN, 0, dydaIn, 0, phase0.getNP());
        Print.f(phase0.getPhaseTag() + ".calSm() method ended", 7);
    }

    public void calS(double[] modDataIn) throws IOException {//vj-2012-06-22
        Print.f("PHASEmBINCE.calModS() method called", 7);
        //Initialization
        double S_local;
        double T_local = modDataIn[1];
        double x_local = modDataIn[2];
        phase0.setT(T_local);
        phase0.setX(x_local);
        //calculation
        S_local = phase0.calS();
        //Output
        modDataIn[0] = S_local;
        Print.f("PHASEmBINCE.calModH() method ended", 7);
    }

    public void calS(double[] modDataIn, double[] dydaIn) throws IOException {//vj-2012-06-22
        Print.f("PHASEmBINCE.calModS() method called", 7);
        calS(modDataIn);
        double[] DSeN = phase0.calDSe();
        System.arraycopy(DSeN, 0, dydaIn, 0, phase0.getNP());
        Print.f("PHASEmBINCE.calModH() method ended", 7);
    }

    public void calGm(double[] modDataIn) throws IOException {//vj-2012-05-18//Gibbs energy of mixing
        Print.f(phase0.getPhaseTag() + ".calGm() method called", 7);
        //Initialization
        double G_local;//y:Free Energy
        double T_local = modDataIn[1];//x:Composition
        double x_local = modDataIn[2];//param:Temperature
        phase0.setT(T_local);
        phase0.setX(x_local);
        //calculation
        G_local = phase0.calGm();
        //Output
        modDataIn[0] = G_local;
        Print.f(phase0.getPhaseTag() + ".calGm() method ended with G=" + G_local + " at T:" + T_local + ", x:" + x_local, 3);
        Print.f(phase0.getPhaseTag() + ".calGm() method ended", 7);
    }

    public void calG(double[] modDataIn) throws IOException {//vj-2012-05-17
        //Initialization
        double G_local;//y:Free Energy
        double T_local = modDataIn[1];//x:Composition
        double x_local = modDataIn[2];//param:Temperature
        Print.f(phase0.getPhaseTag() + ".calG() method called with T=" + T_local + ", xB=" + x_local, 7);
        phase0.setT(T_local);
        phase0.setX(x_local);
        //calculation
        G_local = phase0.calG();
        //Output
        modDataIn[0] = G_local;
        Print.f(phase0.getPhaseTag() + ".calG() method ended with G=" + G_local + " at T:" + T_local + ", x:" + x_local, 3);
        Print.f(phase0.getPhaseTag() + ".calG() method ended", 7);
    }

    public double[][] calGList(double T_in, double xIntvl, PHASEBINCE phase) throws IOException {//Mani-2013-03-06
        Print.f(phase.getPhaseTag() + ".calGList() method called with T=" + T_in, 7);
        int nIntvl = (int) (1 / xIntvl);
        phase.setT(T_in);//calls updateStdst
        //phase.printPhaseInfo();
        double[][] GListLocal = new double[nIntvl + 1][2];
        double GxxN;
        double epsGxx = 100.0;//minimum values of Gxx to avoid calculations inside spinodal
        //double[] moddata = {0.0, T_in, 0.00, 0.0};//Initia composition and temperature
        double x_local;
        double G_local;
        int il;
        {
            x_local = 0.00;
            for (il = 1; il < nIntvl; il = il + 1) {
                x_local = x_local + (1 / (double) nIntvl);
                phase.setX(x_local);
                G_local = phase.calG();
                GxxN = phase.calDGxx();
                GListLocal[il][0] = x_local;//Composition
                GListLocal[il][1] = G_local;//Free Energy
                //System.out.println("il:" + il + ", modData[1]:" + modData[1] + ", GxxN:" + GxxN);
                if ((x_local > (1 - (1 / (double) nIntvl))) || (GxxN < epsGxx)) {
                    il = il - 1;
                    break;
                }
            }
            //System.out.println("il:" + il);
        }
        int ir;
        if (x_local < (1 - (1 / (double) nIntvl))) {//if composition not reached 0.99, start again from right side
            x_local = 1.00;
            for (ir = nIntvl - 1; ir > 0; ir = ir - 1) {
                x_local = x_local - (1 / (double) nIntvl);
                phase.setX(x_local);
                G_local = phase.calG();
                GxxN = phase.calDGxx();
                GListLocal[ir][0] = x_local;//Composition
                GListLocal[ir][1] = G_local;//Free Energy
                //System.out.println("ir:" + ir + ", modData[1]:" + modData[1] + ", GxxN:" + GxxN);
                if ((x_local < (1 / (double) nIntvl)) || (GxxN < epsGxx)) {
                    ir = ir + 1;
                    break;
                }
            }
            x_local = GListLocal[il][0];
            double GSlope = ((GListLocal[ir][1] - GListLocal[il][1]) / (ir - il));
            for (int i = il + 1; i < ir; i++) {
                x_local = x_local + (1 / (double) nIntvl);
                GListLocal[i][0] = x_local;//Composition
                GListLocal[i][1] = GListLocal[il][1] + (GSlope * (i - il));
            }
            //System.out.println("ir:" + ir);
        }
        GListLocal[0][0] = 0.0;
        GListLocal[0][1] = phase.getG0A();
        GListLocal[nIntvl][0] = 1.0;
        GListLocal[nIntvl][1] = phase.getG0B();
        Print.f(phase.getPhaseTag() + ".calGList() method ended", 7);
        return (GListLocal);
    }

    public void calSmA(double[] modDataIn) throws IOException {
        Print.f(phase0.getPhaseTag() + ".calSmA() method called", 7);
        double T_local = modDataIn[1];//x:Composition
        double x_local = modDataIn[2];//param:Temperature
        phase0.setT(T_local);
        phase0.setX(x_local);
        double[] SmAB = phase0.calSmAB();
        modDataIn[0] = SmAB[0];//y:Activity of component B
        Print.f(phase0.getPhaseTag() + ".calSmA() method ended with aB=" + SmAB[0], 2);
        Print.f(phase0.getPhaseTag() + ".calSmA() method ended", 7);
    }

    public void calSmB(double[] modDataIn) throws IOException {
        Print.f(phase0.getPhaseTag() + ".calSmB() method called", 7);
        double T_local = modDataIn[1];//x:Composition
        double x_local = modDataIn[2];//param:Temperature
        phase0.setT(T_local);
        phase0.setX(x_local);
        double[] SmAB = phase0.calSmAB();
        modDataIn[0] = SmAB[1];//y:Activity of component B
        Print.f(phase0.getPhaseTag() + ".calSmB() method ended with aB=" + SmAB[0], 2);
        Print.f(phase0.getPhaseTag() + ".calSmB() method ended", 7);
    }

    public void calSPT(double[] modData) throws IOException {//vj-2012-04-13
        double T_local = modData[0];
        double x_local = modData[1];
        double errGxx = 1E-4;
        int maxIterations = 20;
        double DGxxN = 0.0;
        for (int iter = 0; iter < maxIterations; iter++) {
            phase0.setT(T_local);
            phase0.setX(x_local);
            DGxxN = phase0.calDGxx();
            double DGTxxN = phase0.calDGTxx();
            Print.f("iter:" + iter + ", T:" + T_local + ", x:" + x_local + ", DGxxN:" + DGxxN, 3);
            if ((Math.abs(DGxxN) <= errGxx)) {
                modData[0] = T_local;
                Print.f("PHASEmBINcCE:calSpinodalTemperature() executed with DGxxN:" + DGxxN, 2);
                return;
            }
            double delT = -DGxxN / DGTxxN;
            T_local = T_local + delT;
        }
        modData[0] = T_local;
        Print.f("PHASEmBINcCE:calConsolutePoint() not converged! and executed with DGxxN:" + DGxxN, 2);
    }

    public void calaA(double[] modDataIn, double[] dyda_in) throws IOException {
        Print.f("solutionPhase:calModelActivity() method called", 7);
        calaA(modDataIn);
        double a_local = modDataIn[0];
        double[][] DMUeN = phase0.calDMUe();
        double aRT = a_local / (phase0.getR() * phase0.getT());
        for (int itc = 0; itc < phase0.getNP(); itc++) {
            dyda_in[itc] = aRT * DMUeN[0][itc];
        }
        Print.f("solutionPhase:calModelActivity() method ended", 7);
    }

    public void calaA(double[] modDataIn) throws IOException {
        Print.f("solutionPhase:calModelActivity() method called", 7);
        double a_local; //y:Activity of component A
        double T_local = modDataIn[1];
        double x_local = modDataIn[2];
        phase0.setT(T_local);
        phase0.setX(x_local);
        //phase0.printPhaseInfo();
        double[] MUN = phase0.calGmAB();
        a_local = Math.exp(MUN[0] / (phase0.getR() * phase0.getT()));
        modDataIn[0] = a_local;
        Print.f("solutionPhase:calModelActivity() method ended with a_local:" + a_local, 7);
    }

    public void calaB(double[] modDataIn) throws IOException {//2012-02-23(VJ): Added
        Print.f(phase0.getPhaseTag() + ".calModB() method called", 7);
        double a_local;//y:Activity of component B
        double T_local = modDataIn[1];//x:Composition
        double x_local = modDataIn[2];//param:Temperature
        phase0.setT(T_local);
        phase0.setX(x_local);
        double[] MUN = phase0.calGmAB();
        a_local = Math.exp((MUN[1] / (phase0.getR() * phase0.getT())));//Activity of component B
        modDataIn[0] = a_local;//y:Activity of component B       
        Print.f(phase0.getPhaseTag() + ".calModB() method ended", 7);
    }

    public void calaB(double[] modDataIn, double[] dyda_in) throws IOException {//2012-02-23(VJ): Added
        Print.f(phase0.getPhaseTag() + ".calModB() method called", 7);
        double a_local = modDataIn[0];//y:Activity of component B
        double[][] DMUeN = phase0.calDMUe();
        double aRT = a_local / (phase0.getR() * phase0.getT());
        for (int itc = 0; itc < phase0.getNP(); itc++) {
            dyda_in[itc] = aRT * DMUeN[1][itc];
        }
        Print.f(phase0.getPhaseTag() + ".calModB() method ended", 7);
    }

    public void calMGX1(double[] modDataIn) throws IOException {//2012-02-23(VJ): Added
        Print.f(phase0.getPhaseTag() + ".calMGX1() method called with T:" + modDataIn[1] + ", x0:" + modDataIn[0] + ", x1:" + modDataIn[2], 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//y:Composition of alfa phase
        double xBeta = modDataIn[3];//Paramater:Composition of beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            double[] MU0N = phase0.calGAB(); //{MUA, MUB} of alfa phase//MU will be calculated at xB
            double[] DMU0x0N = phase0.calDMUx();//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
            double[] DMU0x1N = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
            phase0.setT(T_local);
            phase0.setX(xBeta);
            double[] MU1N = phase0.calGAB();//MUA, MUB of beta phase//MU will be calculated at xB
            double[] DMU1x0N = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            double[] DMU1x1N = phase0.calDMUx();//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            Print.f("Iteration:" + counter + ", T_local:" + T_local + ", x0_local:" + xAlpha + ", x1_local:" + xBeta + ", errf:" + errf, 3);
            if (errf <= tolf) {
                Print.f("PHASEmBINCE:calMGX1() ended with T_local:" + T_local + ", x0_local:" + xAlpha + ", x1_local:" + xBeta + ", errf:" + errf, 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[0]);
            double stpmax1 = Utils.stpmaX(xBeta, xBeta + delX[1]);
            double stpmax = Math.min(stpmax0, stpmax1);
            xAlpha = xAlpha + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            Print.f("T_local:" + T_local + ", x0_local:" + xAlpha + ", x1_local:" + xBeta + ", stpmax:" + stpmax + ", errf:" + errf, 2);
        }
        if (counter == ntrial) {
            Print.f("PHASEmBINCE.calMGX1() ntrial reached with errf:" + errf + "\t!", 1);
        }
        modDataIn[0] = xAlpha;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        Print.f(phase0.getPhaseTag() + ".calMGX1() method ended", 7);
    }

    public void calMGX1(double[] modDataIn, double[] dydaIn) throws IOException {//2012-02-23(VJ): Added
        Print.f(phase0.getPhaseTag() + ".calMGX1() method called with T:" + modDataIn[1] + ", x0:" + modDataIn[0] + ", x1:" + modDataIn[2], 5);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//y:Composition of alfa phase
        double xBeta = modDataIn[3];//Paramater:Composition of beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            double[] MU0N = phase0.calGAB(); //{MUA, MUB} of alfa phase//MU will be calculated at xB
            double[] DMU0x0N = phase0.calDMUx();//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
            double[] DMU0x1N = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
            phase0.setT(T_local);
            phase0.setX(xBeta);
            double[] MU1N = phase0.calGAB();//MUA, MUB of beta phase//MU will be calculated at xB
            double[] DMU1x0N = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            double[] DMU1x1N = phase0.calDMUx();//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            Print.f("Iteration:" + counter + ", T_local:" + T_local + ", x0_local:" + xAlpha + ", x1_local:" + xBeta + ", errf:" + errf, 2);
            if (errf <= tolf) {
                Print.f("PHASEmBINCE:calMGX1() executed with errf:" + errf, 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[0]);
            double stpmax1 = Utils.stpmaX(xBeta, xBeta + delX[1]);
            double stpmax = Math.min(stpmax0, stpmax1);
            xAlpha = xAlpha + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            //Print.f("T_local:" + T_local + ", x0_local:" + xAlpha + ", x1_local:" + xBeta + ", stpmax:" + stpmax + ", errf:" + errf, 2);
        }
        if (counter == ntrial) {
            Print.f("PHASEmBINCE.calMGX1() ntrial reached with errf:" + errf + "\t!", 1);
        }
        modDataIn[0] = xAlpha;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        double[] delDMUe = new double[2];
        double[] Dx0eN = new double[phase0.getNP()];//Derivatives xB of alfa phase with ECIs
        double[] Dx1eN = new double[phase0.getNP()];//Derivatives xB of beta phase with ECIs
        phase0.setT(T_local);
        phase0.setX(xAlpha);
        double[][] DMU0eN = phase0.calDMUe();
        phase0.setT(T_local);
        phase0.setX(xBeta);
        double[][] DMU1eN = phase0.calDMUe();
        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < phase0.getNP(); icf0++) {
            delDMUe[0] = -(DMU1eN[0][icf0] - DMU0eN[0][icf0]);
            delDMUe[1] = -(DMU1eN[1][icf0] - DMU0eN[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            Dx0eN[icf0] = X.getArray()[0][0];
            Dx1eN[icf0] = X.getArray()[1][0];
        }
        System.arraycopy(Dx0eN, 0, dydaIn, 0, phase0.getNP());

        Print.f(phase0.getPhaseTag() + ".calMGX1() method ended", 7);
    }

    public void calMGX2(double[] modDataIn) throws IOException {//2012-02-23(VJ): Added
        Print.f("PHASEmBINCE.calMGX2() method called", 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//x2:Composition of alfa phase
        double xBeta = modDataIn[3];//y:Composition of beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        int np = phase0.getNP();
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double[] delDMUe = new double[2];
        double[] Dx0eN = new double[np];//Derivatives xB of alfa phase with ECIs
        double[] Dx1eN = new double[np];//Derivatives xB of beta phase with ECIs
        double[][] DMU0eN = new double[2][np];//to be checked
        double[][] DMU1eN = new double[2][np];//to be checked
        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            double[] MU0N = phase0.calGAB();//MU will be calculated at xB
            double[] DMU0x0N = phase0.calDMUx();
            double[] DMU0x1N = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
            phase0.setT(T_local);
            phase0.setX(xBeta);
            double[] MU1N = phase0.calGAB();//MU will be calculated at xB
            double[] DMU1x0N = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            double[] DMU1x1N = phase0.calDMUx();
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            Print.f("Iteration:" + counter + ", T_local:" + T_local + ", x0_local:" + xAlpha + ", x1_local:" + xBeta + ", errf:" + errf, 2);
            if (errf <= tolf) {
                Print.f("PHASEmBINCE.calMGX2() executed with errf:" + errf, 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[0]);
            double stpmax1 = Utils.stpmaX(xBeta, xBeta + delX[1]);
            double stpmax = Math.min(stpmax0, stpmax1);
            xAlpha = xAlpha + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            //prnt.f("T_local:" + T_local + ", xAlpha_local:" + xAlpha_local + ", xBeta_local:" + xBeta_local + ", stpmax:" + stpmax + ", errf:" + errf, 2);
        }
        if (counter == ntrial) {
            Print.f("PHASEmBINCE.calMGX2() ntrial reached with errf:" + errf + "\t!", 1);
        }
        modDataIn[0] = xBeta;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        Print.f("PHASEmBINCE.calMGX2() method ended", 7);
    }

    public void calMGX2(double[] modDataIn, double[] dydaIn) throws IOException {//2012-02-23(VJ): Added
        Print.f("PHASEmBINCE.calMGX2() method called", 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//x2:Composition of alfa phase
        double xBeta = modDataIn[3];//y:Composition of beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        int np = phase0.getNP();
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double[] delDMUe = new double[2];
        double[] Dx0eN = new double[np];//Derivatives xB of alfa phase with ECIs
        double[] Dx1eN = new double[np];//Derivatives xB of beta phase with ECIs
        double[][] DMU0eN = new double[2][np];//to be checked
        double[][] DMU1eN = new double[2][np];//to be checked
        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            double[] MU0N = phase0.calGAB();//MU will be calculated at xB
            double[] DMU0x0N = phase0.calDMUx();
            double[] DMU0x1N = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
            phase0.setT(T_local);
            phase0.setX(xBeta);
            double[] MU1N = phase0.calGAB();//MU will be calculated at xB
            double[] DMU1x0N = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            double[] DMU1x1N = phase0.calDMUx();
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            Print.f("Iteration:" + counter + ", T_local:" + T_local + ", x0_local:" + xAlpha + ", x1_local:" + xBeta + ", errf:" + errf, 2);
            if (errf <= tolf) {
                Print.f("PHASEmBINCE.calMGX2() executed with errf:" + errf, 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[0]);
            double stpmax1 = Utils.stpmaX(xBeta, xBeta + delX[1]);
            double stpmax = Math.min(stpmax0, stpmax1);
            xAlpha = xAlpha + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            //prnt.f("T_local:" + T_local + ", xAlpha_local:" + xAlpha_local + ", xBeta_local:" + xBeta_local + ", stpmax:" + stpmax + ", errf:" + errf, 2);
        }
        if (counter == ntrial) {
            Print.f("PHASEmBINCE.calMGX2() ntrial reached with errf:" + errf + "\t!", 1);
        }
        modDataIn[0] = xBeta;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        phase0.setT(T_local);
        phase0.setX(xAlpha);
        DMU0eN = phase0.calDMUe();
        phase0.setT(T_local);
        phase0.setX(xBeta);
        DMU1eN = phase0.calDMUe();
        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < np; icf0++) {
            delDMUe[0] = -(DMU1eN[0][icf0] - DMU0eN[0][icf0]);
            delDMUe[1] = -(DMU1eN[1][icf0] - DMU0eN[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            Dx0eN[icf0] = X.getArray()[0][0];
            Dx1eN[icf0] = X.getArray()[1][0];
        }
        //Output

        System.arraycopy(Dx1eN, 0, dydaIn, 0, np);
        Print.f("PHASEmBINCE.calMGX2() method ended", 7);
    }

    public void calMGT1(double[] modDataIn) throws IOException {//2012-02-23(VJ): Added
        Print.f("PHASEmBINCE.calMGT1() method called", 7);
        //Initialization
        double T_local = modDataIn[0];//y:Temperature
        double x0_local = modDataIn[1];//x1:Composition of alfa phase
        double x1_local = modDataIn[2];//x2:Composition of beta phase
        //calculation
        int ntrial = 40;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        int np = phase0.getNP();
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        //double DMU0x0N[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
        double DMU0x1N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
        double DMU0TN[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with temperature
        //double DMU1x0N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
        double DMU1x1N[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
        double DMU1TN[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with temperature

        double[] delDMUe = new double[2];
        double[][] DMU0eN = new double[2][np];//{MUAe, MUBe} of alfa phase
        double[][] DMU1eN = new double[2][np];//MUAe, MUBe of beta phase
        double[] DTeN = new double[np];//Derivatives of T with ECIs
        //double[] Dx0eN = new double[np];//Derivatives of xB of alfa phase with ECIs
        double[] Dx1eN = new double[np];//Derivatives of xB of beta phase with ECIs

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            //DMU0x0N = calDMUx();
            DMU0TN = phase0.calDMUT();

            phase0.setT(T_local);
            phase0.setX(x1_local);
            MU1N = phase0.calGAB();
            DMU1x1N = phase0.calDMUx();
            DMU1TN = phase0.calDMUT();

            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1TN[0] - DMU0TN[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1TN[1] - DMU0TN[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            Print.f("Iteration:" + counter + ", T_local:" + T_local + ", x0_local:" + x0_local + ", x1_local:" + x1_local + ", errf:" + errf, 2);
            if (errf <= tolf) {
                Print.f("PHASEmBINCE:calMGT1() executed with errf:" + errf, 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double xTrial = x1_local + delX[1];
            double stpmax = Utils.stpmaX(x1_local, xTrial);
            T_local = T_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            //prnt.f("T_local:" + T_local + ", xAlpha_local:" + xAlpha_local + ", xBeta_local:" + xBeta_local + ", stpmax:" + stpmax + ", errf:" + errf, 2);
        }
        if (counter == ntrial) {
            Print.f("PHASEmBINCE.calMGT1() ntrial reached with errf:" + errf + "\t!", 1);
        }
        modDataIn[0] = T_local;
        modDataIn[1] = x0_local;
        modDataIn[2] = x1_local;
        Print.f("PHASEmBINCE.calMGT1() method ended", 7);
    }

    public void calMGT1(double[] modDataIn, double[] dydaIn) throws IOException {//2012-02-23(VJ): Added
        Print.f("PHASEmBINCE.calMGT1() method called", 7);
        //Initialization
        //Print.f("modDataIn",modDataIn,0);

        double T_local = modDataIn[0];//y:Temperature
        double x0_local = modDataIn[1];//x1:Composition of alfa phase
        double x1_local = modDataIn[2];//x2:Composition of beta phase
        //Print.f("T_local",T_local,0);

        //calculation
        int ntrial = 40;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        int np = phase0.getNP();
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        //double DMU0x0N[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
        double DMU0x1N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
        double DMU0TN[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with temperature
        //double DMU1x0N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
        double DMU1x1N[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
        double DMU1TN[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with temperature

        double[] delDMUe = new double[2];
        double[][] DMU0eN = new double[2][np];//{MUAe, MUBe} of alfa phase
        double[][] DMU1eN = new double[2][np];//MUAe, MUBe of beta phase
        double[] DTeN = new double[np];//Derivatives of T with ECIs
        //double[] Dx0eN = new double[np];//Derivatives of xB of alfa phase with ECIs
        double[] Dx1eN = new double[np];//Derivatives of xB of beta phase with ECIs

        for (counter = 0; counter < ntrial; counter++) {
            //Print.f("T_local",T_local,0);
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            //DMU0x0N = calDMUx();
            DMU0TN = phase0.calDMUT();

            phase0.setT(T_local);
            phase0.setX(x1_local);
            MU1N = phase0.calGAB();
            DMU1x1N = phase0.calDMUx();
            DMU1TN = phase0.calDMUT();

            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1TN[0] - DMU0TN[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1TN[1] - DMU0TN[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            Print.f("Iteration:" + counter + ", T_local:" + T_local + ", x0_local:" + x0_local + ", x1_local:" + x1_local + ", errf:" + errf, 2);
            if (errf <= tolf) {
                Print.f("PHASEmBINCE:calMGT1() executed with errf:" + errf, 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double xTrial = x1_local + delX[1];
            double stpmax = Utils.stpmaX(x1_local, xTrial);
            T_local = T_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            //prnt.f("T_local:" + T_local + ", xAlpha_local:" + xAlpha_local + ", xBeta_local:" + xBeta_local + ", stpmax:" + stpmax + ", errf:" + errf, 2);
        }
        if (counter == ntrial) {
            Print.f("PHASEmBINCE.calMGT1() ntrial reached with errf:" + errf + "\t!", 1);
        }
        phase0.setT(T_local);
        phase0.setX(x0_local);
        DMU0eN = phase0.calDMUe();
        phase0.setT(T_local);
        phase0.setX(x1_local);
        DMU1eN = phase0.calDMUe();
        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < np; icf0++) {
            delDMUe[0] = -(DMU1eN[0][icf0] - DMU0eN[0][icf0]);
            delDMUe[1] = -(DMU1eN[1][icf0] - DMU0eN[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTeN[icf0] = X.getArray()[0][0];
            Dx1eN[icf0] = X.getArray()[1][0];
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[1] = x0_local;
        modDataIn[2] = x1_local;
        System.arraycopy(DTeN, 0, dydaIn, 0, np);
        Print.f("PHASEmBINCE.calMGT1() method ended", 7);
    }

    public void calMGT2(double[] modDataIn) throws IOException {//2012-02-23(VJ): Added,2012-03-13(VJ): Modified
        Print.f("PHASEmBINCE.calMGT2() method called", 7);
        //Initialization
        double T_local = modDataIn[0];//y:Temperature
        double x0_local = modDataIn[2];//x1:Composition of alfa phase
        double x1_local = modDataIn[1];//x2:Composition of beta phase
        //calculation
        int ntrial = 40;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        int np = phase0.getNP();
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double DMU0x0N[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
        //double DMU0x1N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
        double DMU0TN[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with temperature
        double DMU1x0N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
        //double DMU1x1N[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
        double DMU1TN[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with temperature

        double[] delDMUe = new double[2];
        double[][] DMU0eN = new double[2][np];//{MUAe, MUBe} of alfa phase
        double[][] DMU1eN = new double[2][np];//MUAe, MUBe of beta phase
        double[] DTeN = new double[np];//Derivatives of T with ECIs
        double[] Dx0eN = new double[np];//Derivatives of xB of alfa phase with ECIs
        //double[] Dx1eN = new double[np];//Derivatives of xB of beta phase with ECIs

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();

            phase0.setT(T_local);
            phase0.setX(x1_local);
            MU1N = phase0.calGAB();
            //DMU1x1N = calDMUx();
            DMU1TN = phase0.calDMUT();

            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1TN[0] - DMU0TN[0];
            fjac[0][1] = DMU1x0N[0] - DMU0x0N[0];
            fjac[1][0] = DMU1TN[1] - DMU0TN[1];
            fjac[1][1] = DMU1x0N[1] - DMU0x0N[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            Print.f("Iteration:" + counter + ", T_local:" + T_local + ", x0_local:" + x0_local + ", x1_local:" + x1_local + ", errf:" + errf, 2);
            if (errf <= tolf) {
                Print.f("PHASEmBINCE.calMGT2() executed with errf:" + errf, 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double xTrial = x0_local + delX[1];
            double stpmax = Utils.stpmaX(x0_local, xTrial);
            T_local = T_local + stpmax * delX[0];
            x0_local = x0_local + stpmax * delX[1];
            //prnt.f("T_local:" + T_local + ", xAlpha_local:" + xAlpha_local + ", xBeta_local:" + xBeta_local + ", stpmax:" + stpmax + ", errf:" + errf, 2);

        }
        if (counter == ntrial) {
            Print.f("PHASEmBINCE.calMGT2() ntrial reached with errf:" + errf + "\t!", 1);
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[2] = x0_local;
        modDataIn[1] = x1_local;
        Print.f("PHASEmBINCE.calMGT2() method ended", 7);
    }

    public void calMGT2(double[] modDataIn, double[] dydaIn) throws IOException {//2012-02-23(VJ): Added,2012-03-13(VJ): Modified
        Print.f("PHASEmBINCE.calMGT2() method called", 7);
        //Initialization
        double T_local = modDataIn[0];//y:Temperature
        double x0_local = modDataIn[2];//x1:Composition of alfa phase
        double x1_local = modDataIn[1];//x2:Composition of beta phase
        //calculation
        int ntrial = 40;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        int np = phase0.getNP();
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double DMU0x0N[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
        //double DMU0x1N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
        double DMU0TN[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with temperature
        double DMU1x0N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
        //double DMU1x1N[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
        double DMU1TN[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with temperature

        double[] delDMUe = new double[2];
        double[][] DMU0eN = new double[2][np];//{MUAe, MUBe} of alfa phase
        double[][] DMU1eN = new double[2][np];//MUAe, MUBe of beta phase
        double[] DTeN = new double[np];//Derivatives of T with ECIs
        double[] Dx0eN = new double[np];//Derivatives of xB of alfa phase with ECIs
        //double[] Dx1eN = new double[np];//Derivatives of xB of beta phase with ECIs

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();

            phase0.setT(T_local);
            phase0.setX(x1_local);
            MU1N = phase0.calGAB();
            //DMU1x1N = calDMUx();
            DMU1TN = phase0.calDMUT();

            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1TN[0] - DMU0TN[0];
            fjac[0][1] = DMU1x0N[0] - DMU0x0N[0];
            fjac[1][0] = DMU1TN[1] - DMU0TN[1];
            fjac[1][1] = DMU1x0N[1] - DMU0x0N[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            Print.f("Iteration:" + counter + ", T_local:" + T_local + ", x0_local:" + x0_local + ", x1_local:" + x1_local + ", errf:" + errf, 2);
            if (errf <= tolf) {
                Print.f("PHASEmBINCE.calMGT2() executed with errf:" + errf, 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double xTrial = x0_local + delX[1];
            double stpmax = Utils.stpmaX(x0_local, xTrial);
            T_local = T_local + stpmax * delX[0];
            x0_local = x0_local + stpmax * delX[1];
            //prnt.f("T_local:" + T_local + ", xAlpha_local:" + xAlpha_local + ", xBeta_local:" + xBeta_local + ", stpmax:" + stpmax + ", errf:" + errf, 2);

        }
        if (counter == ntrial) {
            Print.f("PHASEmBINCE.calMGT2() ntrial reached with errf:" + errf + "\t!", 1);
        }
        phase0.setT(T_local);
        phase0.setX(x0_local);
        DMU0eN = phase0.calDMUe();
        phase0.setT(T_local);
        phase0.setX(x1_local);
        DMU1eN = phase0.calDMUe();
        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < np; icf0++) {
            delDMUe[0] = -(DMU1eN[0][icf0] - DMU0eN[0][icf0]);
            delDMUe[1] = -(DMU1eN[1][icf0] - DMU0eN[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTeN[icf0] = X.getArray()[0][0];
            Dx0eN[icf0] = X.getArray()[1][0];
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[2] = x0_local;
        modDataIn[1] = x1_local;
        System.arraycopy(DTeN, 0, dydaIn, 0, np);
        Print.f("PHASEmBINCE.calMGT2() method ended", 7);
    }

    /* Consolute point calculation: Temperature
    
     */
    public void calCOPT(double[] modData) throws IOException {//vj-2012-04-12
        double T_local = modData[1];//VJ-170104
        double x_local = modData[2];//VJ-170104
        double errGxx = 1E-4;
        double errGxxx = 1E-4;
        int maxIterations = 20;
        double DGxxN = 0.0;
        double DGxxxN = 0.0;

        //phase0.printPhaseInfo();
        for (int iter = 0; iter < maxIterations; iter++) {
            phase0.setT(T_local);
            phase0.setX(x_local);
            DGxxN = phase0.calDGxx();
            DGxxxN = phase0.calDGxxx();
            double DGTxxN = phase0.calDGTxx();
            double DGTxxxN = phase0.calDGTxxx();
            double DGxxxxN = phase0.calDGxxxx();
            Print.f("iter:" + iter + ", T:" + T_local + ", x:" + x_local + ", DGxxN:" + DGxxN + ", DGxxxN:" + DGxxxN, 3);
            if ((Math.abs(DGxxN) <= errGxx) && (Math.abs(DGxxxN) <= errGxxx)) {
                modData[0] = T_local;
                modData[1] = T_local;
                modData[2] = x_local;
                Print.f("Methods.calCOPT() executed with DGxxN:" + DGxxN + ", DGxxxN:" + DGxxxN, 7);
                return;
            }
            double delT = -DGxxN / DGTxxN;
            double delX = (DGxxN * DGTxxxN - DGxxxN * DGTxxN) / (DGxxxxN * DGTxxN);
            T_local = T_local + delT;
            x_local = x_local + delX;
        }
        modData[0] = T_local;
        modData[1] = T_local;
        modData[2] = x_local;
        Print.f("Methods.calCOPT() not converged! and executed with DGxxN:" + DGxxN + ", DGxxxN:" + DGxxxN, 1);
    }

    /* Consolute point calculation: Composition
    
     */
    public void calCOPX(double[] modData) throws IOException {//vj-2014-12-05: return composition
        Print.f("Methods.calCOPX() method called", 7);
        double T_local = modData[1];
        double x_local = modData[0];
        double errGxx = 1E-4;
        double errGxxx = 1E-4;
        int maxIterations = 20;
        double DGxxN = 0.0;
        double DGxxxN = 0.0;

        //phase0.printPhaseInfo();
        for (int iter = 0; iter < maxIterations; iter++) {
            phase0.setT(T_local);
            phase0.setX(x_local);
            DGxxN = phase0.calDGxx();
            DGxxxN = phase0.calDGxxx();
            double DGTxxN = phase0.calDGTxx();
            double DGTxxxN = phase0.calDGTxxx();
            double DGxxxxN = phase0.calDGxxxx();
            Print.f("iter:" + iter + ", T:" + T_local + ", x:" + x_local + ", DGxxN:" + DGxxN + ", DGxxxN:" + DGxxxN, 2);
            if ((Math.abs(DGxxN) <= errGxx) && (Math.abs(DGxxxN) <= errGxxx)) {
                modData[1] = T_local;
                modData[0] = x_local;
                Print.f("Methods.calCOPX() executed with DGxxN:" + DGxxN + ", DGxxxN:" + DGxxxN, 2);
                return;
            }
            double delT = -DGxxN / DGTxxN;
            double delX = (DGxxN * DGTxxxN - DGxxxN * DGTxxN) / (DGxxxxN * DGTxxN);
            T_local = T_local + delT;
            x_local = x_local + delX;
        }
        modData[0] = x_local;
        modData[1] = T_local;
        modData[2] = x_local;
        Print.f("Methods.calCOPX() not converged! and executed with DGxxN:" + DGxxN + ", DGxxxN:" + DGxxxN, 1);
    }

    public void calCOPT(double[] modData, double[] dydaIn) throws IOException {//vj-2012-05-06
        Print.f("PHASEmBINCE.calConsolutePoint() method called", 7);
        double T_local = modData[0];
        double x_local = modData[1];
        calCOPT(modData);
        modData[0] = T_local;
        modData[1] = x_local;

        double dteb, dtef;
        double de = 0.0001;
        double[] edis = new double[phase0.getNP()];
        phase0.getEdis(edis);
        for (int i = 0; i < phase0.getNP(); i++) {
            if (Math.abs(edis[i] - 0.0) < 1E-16) {
                continue;
            }
            edis[i] = edis[i] + de;
            phase0.setEdis(edis);
            calCOPT(modData);
            dtef = modData[0];
            edis[i] = edis[i] - 2.0 * de;
            phase0.setEdis(edis);
            calCOPT(modData);
            dteb = modData[0];
            dydaIn[i] = (dtef - dteb) / (2 * de);
            edis[i] = edis[i] + de;
            phase0.setEdis(edis);
        }
        Print.f("PHASEmBINCE.calConsolutePoint() method ended", 7);
    }

    public void calISOGX(double[] modDataIn) throws IOException {//vj-2012-06-19
        Print.f("Model:calISOG() method called", 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double x_local = modDataIn[0];//y:Composition of alfa phase and beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x_local);
            double G0N = phase0.calG();//G of alfa phase calculated at xB
            double Gx0N = phase0.calDGx();//Derivatives of G of alfa phase with composition, xB of alfa phase
            phase1.setT(T_local);
            phase1.setX(x_local);
            double G1N = phase1.calG();//G of beta phase  calculated at xB
            double Gx1N = phase1.calDGx();//Derivatives of G of beta phase with composition, xB of beta phase
            double delG = -(G1N - G0N);
            double delGx = (Gx1N - Gx0N);
            errf = Math.abs(delG);
            if (errf <= tolf) {
                Print.f("Model:calISOG() ended with xB:" + x_local + " and errf:" + df.format(errf), 2);
                break;
            }
            double delX = delG / delGx;
            double stpmax = Utils.stpmaX(x_local, x_local + delX);
            x_local = x_local + stpmax * delX;
            Print.f("T_local:" + T_local + ", x_local:" + x_local + ", stpmax:" + stpmax, 7);
        }

        //Output
        modDataIn[1] = T_local;
        modDataIn[0] = x_local;
        Print.f("Model:calISOG() method called", 7);
    }

    public void calISOGX(double[] modDataIn, double[] dydaIn) throws IOException {//vj-2012-06-19
        Print.f("Model:calISOG() method called", 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double x_local = modDataIn[0];//y:Composition of alfa phase and beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x_local);
            double G0N = phase0.calG();//G of alfa phase calculated at xB
            double Gx0N = phase0.calDGx();//Derivatives of G of alfa phase with composition, xB of alfa phase
            phase1.setT(T_local);
            phase1.setX(x_local);
            double G1N = phase1.calG();//G of beta phase  calculated at xB
            double Gx1N = phase1.calDGx();//Derivatives of G of beta phase with composition, xB of beta phase
            double delG = -(G1N - G0N);
            double delGx = (Gx1N - Gx0N);
            errf = Math.abs(delG);
            if (errf <= tolf) {
                Print.f("Model:calISOG() executed with df.format(errf):" + df.format(errf), 2);
                break;
            }
            double delX = delG / delGx;
            double stpmax = Utils.stpmaX(x_local, x_local + delX);
            x_local = x_local + stpmax * delX;
            Print.f("T_local:" + T_local + ", x_local:" + x_local + ", stpmax:" + stpmax, 7);
        }

        //Output
        modDataIn[1] = T_local;
        modDataIn[0] = x_local;
        double[] Dxe0N = new double[phase0.getNP()];//to be implemented
        double[] Dxe1N = new double[phase1.getNP()];//to be implemented
        System.arraycopy(Dxe0N, 0, dydaIn, 0, (phase0.getNP()));
        System.arraycopy(Dxe1N, 0, dydaIn, (phase0.getNP()), (phase1.getNP()));
        Print.f("Model:calISOG() method called", 7);
    }

//    private void calCXXA(double[] modDataIn) throws IOException {//vj-2012-07-04//Returns initial compositions for MG 
//        Print.f("Model:calCXXA() method called", 3);
//        //Initialization
//        double T_local = modDataIn[1];//x:Temperature
//        double xAlpha = modDataIn[2];//y:Composition of alfa phase
//        double xBeta = modDataIn[3];//Paramater:Composition of beta phase
//        //Prnt.f(modData, "ModdataIn", "printing moddata output from CXX");
//        double[][] GList0 = calGList(T_local, 0.01, phase0);
//        double[][] GList1 = calGList(T_local, 0.01, phase1);
//        //Print.f("GList0-phase0", GList0, 1);
//        //Print.f("GList0-phase1", GList1, 1);
//
//        //System.out.printf("Glist calculated");
//        for (int i = 0; i < 101; i++) {//lower countour of both the GLists is saved in GList0
//            //System.out.printf("%lf  %lf",GList0[i])
//            if (GList0[i][1] > GList1[i][1]) {
//                GList0[i][1] = GList1[i][1];
//            }
//        }
//        //Print.f("Merged GList0", GList0, 1);
//        double[][] G = new double[101][2];
//        double xTrial, GTrial;
//        //boolean iStable;
//        boolean iLeft;
//        int i = 0;
//        G[i][0] = GList0[0][0];
//        G[i][1] = GList0[0][1];
//        i = i + 1;
//        G[i][0] = GList0[1][0];
//        G[i][1] = GList0[1][1];
//        int j = 2;
//        xTrial = GList0[j][0];
//        GTrial = GList0[j][1];
//        while (j < 100) {
//            //iStable = isStable(G[i - 1][0], G[i][0], xTrial, G[i - 1][1], G[i][1], GTrial);
//            //System.out.println(i + "," + j + "," + G[i - 1][0] + "," + G[i][0] + "," + xTrial);
//            //System.out.println(G[i - 1][1] + "," + G[i][1] + "," + GTrial);
//            iLeft = isLeft(G[i - 1][0], G[i][0], xTrial, G[i - 1][1], G[i][1], GTrial);
//            //if (iStable) {
//            if (iLeft) {
//                i = i + 1;//current position of counter for G
//                G[i][0] = xTrial;
//                G[i][1] = GTrial;
//                j = j + 1;
//                xTrial = GList0[j][0];
//                GTrial = GList0[j][1];
//            } else {
//                i = i - 1;
//                if (i <1) {
//                    break;
//                }
//            }
//            //System.out.println("i:" + i + ", j:" + j + ", iStable:" + iStable);
//            //System.out.println("i:" + i + ", j:" + j + ", iLeft:" + iLeft);
//        }
//        //prnt.f(GList0, "GList0:", 0);
//        double xl = 0.0, xr = 1.0;
//        for (int k = 0; k < 99; k++) {
//            if (G[k + 1][0] - G[k][0] > 0.02) {
//                xl = G[k][0];
//                xr = G[k + 1][0];
//                break;
//            }
//        }
//        xAlpha = xl;
//        xBeta = xr;
//        modDataIn[0] = xAlpha;
//        modDataIn[1] = T_local;
//        modDataIn[2] = xAlpha;
//        modDataIn[3] = xBeta;
//        Print.f("T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta), 3);
////        
//        Print.f("Model.calCXXA() method ended ", 3);//with xl:"+xl+", xr:"+xr, 1);
//    }

    public void calCXXA(double[] modDataIn) throws IOException {//2012-02-27(VJ): Added
        Print.f("Model:calCXXA() method called", 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//y:Composition of alfa phase
        double xBeta = modDataIn[3];//Paramater:Composition of beta phase
        //calculation
        int ntrial = 50;
        int counter;
        double tolf = 1E-06;
        double errf = 0.0;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            double[] MU0N = phase0.calGAB();//{MUA, MUB} of alfa phase calculated at xB
            double DMU0x0N[] = phase0.calDMUx();//Derivatives of {MUA, MUB} of alfa phase with composition, xB of alfa phase
            double DMU0x1N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition, xB of beta phase   
            phase1.setT(T_local);
            phase1.setX(xBeta);
            double[] MU1N = phase1.calGAB();////MUA, MUB of beta phase  calculated at xB
            double DMU1x0N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            double DMU1x1N[] = phase1.calDMUx();//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCXXA() ended with T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[0]);
            double stpmax1 = Utils.stpmaX(xBeta, xBeta + delX[1]);
            double stpmax = Math.min(stpmax0, stpmax1);
            xAlpha = xAlpha + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 3);
        }
        //Output
        modDataIn[0] = xAlpha;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        if (counter == ntrial) {
            double[] MU1N = phase1.calGAB();////MUA, MUB of beta phase  calculated at xB
            Print.f("!Model:calCXXA() not coverged and ended with T_local:" + df.format(T_local) + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
        }
        Print.f("Model:calCXXA() method called", 7);
    }
    /**
     *
     * @param modDataIn {ymod-xalfa, T, xalfa, xbeta}
     * @param dydaIn
     * @throws java.io.IOException
     */
    public void calCXXA(double[] modDataIn, double[] dydaIn) throws IOException {//2012-02-27(VJ): Added
        Print.f("Model:calCXXA() method called", 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//y:Composition of alfa phase
        double xBeta = modDataIn[3];//Paramater:Composition of beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double[] delDMUe = new double[2];
        double[] Dx0e0N = new double[phase0.getNP()];//Derivatives xB of alfa phase with alfa phase ECIs
        double[] Dx1e0N = new double[phase0.getNP()];//Derivatives xB of beta phase with alfa phase ECIs
        double[] Dx0e1N = new double[phase1.getNP()];//Derivatives xB of alfa phase with beta phase ECIs
        double[] Dx1e1N = new double[phase1.getNP()];//Derivatives xB of beta phase with beta phase ECIs
        double[][] DMU0e0N = new double[2][phase0.getNP()];//Derivatives MU of alfa phase with alfa phase ECIs
        double[][] DMU0e1N = new double[2][phase1.getNP()];//to be checked//Derivatives MU of beta phase with alfa phase ECIs
        double[][] DMU1e0N = new double[2][phase0.getNP()];//to be checked//Derivatives MU of alfa phase with beta phase ECIs
        double[][] DMU1e1N = new double[2][phase1.getNP()];;//Derivatives MU of beta phase with beta phase ECIs

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            double[] MU0N = phase0.calGAB();//{MUA, MUB} of alfa phase calculated at xB
            double DMU0x0N[] = phase0.calDMUx();//Derivatives of {MUA, MUB} of alfa phase with composition, xB of alfa phase
            double DMU0x1N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition, xB of beta phase   
            phase1.setT(T_local);
            phase1.setX(xBeta);
            double[] MU1N = phase1.calGAB();////MUA, MUB of beta phase  calculated at xB
            double DMU1x0N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            double DMU1x1N[] = phase1.calDMUx();//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCXXA() ended with T_local:" + df.format(T_local) + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[0]);
            double stpmax1 = Utils.stpmaX(xBeta, xBeta + delX[1]);
            double stpmax = Math.min(stpmax0, stpmax1);
            xAlpha = xAlpha + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            Print.f("T_local:" + df.format(T_local) + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", stpmax:" + stpmax, 3);
        }
        DMU0e0N = phase0.calDMUe();
        DMU1e1N = phase1.calDMUe();
        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            delDMUe[0] = -(DMU1e0N[0][icf0] - DMU0e0N[0][icf0]);
            delDMUe[1] = -(DMU1e0N[1][icf0] - DMU0e0N[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            Dx0e0N[icf0] = X.getArray()[0][0];
            Dx1e0N[icf0] = X.getArray()[1][0];
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            delDMUe[0] = -(DMU1e1N[0][icf1] - DMU0e1N[0][icf1]);
            delDMUe[1] = -(DMU1e1N[1][icf1] - DMU0e1N[1][icf1]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            Dx0e1N[icf1] = X.getArray()[0][0];
            Dx1e1N[icf1] = X.getArray()[1][0];
        }
        //Output
        modDataIn[0] = xAlpha;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        System.arraycopy(Dx0e0N, 0, dydaIn, 0, (phase0.getNP()));
        System.arraycopy(Dx0e1N, 0, dydaIn, (phase0.getNP()), (phase1.getNP()));
        Print.f("Model:calCXXA() method called", 7);
    }

    public void calCXXB(double[] modDataIn) throws IOException {//2012-02-27(VJ): Added
        Print.f("Model:calCXXB() method called", 7);
        //System.out.println("Phase0:" + phase0.getPhaseTag() + ", Phase1:" + phase1.getPhaseTag());
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//x2:Composition of alfa phase
        double xBeta = modDataIn[3];//y:Composition of beta phase
        //calculation
        int ntrial = 50;
        int counter;
        double tolf = 1E-06;
        double errf;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double DMU0x0N[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
        double DMU0x1N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
        double DMU1x0N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
        double DMU1x1N[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            MU0N = phase0.calGAB();//MU will be calculated at xB
            DMU0x0N = phase0.calDMUx();
            phase1.setT(T_local);
            phase1.setX(xBeta);
            //phase1.printPhaseInfo();
            MU1N = phase1.calGAB();//MU will be calculated at xB
            DMU1x1N = phase1.calDMUx();
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCXXB() ended with T_local:" + df.format(T_local) + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[0]);
            double stpmax1 = Utils.stpmaX(xBeta, xBeta + delX[1]);
            double stpmax = Math.min(stpmax0, stpmax1);
            xAlpha = xAlpha + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 3);
        }
        //Output
        modDataIn[0] = xBeta;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        Print.f("Model:calCXXB() method called", 7);
    }

    public void calCXXB(double[] modDataIn, double[] dydaIn) throws IOException {//2012-02-27(VJ): Added
        Print.f("Model:calCXXB() method called", 7);
        //System.out.println("Phase0:" + phase0.getPhaseTag() + ", Phase1:" + phase1.getPhaseTag());
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//x2:Composition of alfa phase
        double xBeta = modDataIn[3];//y:Composition of beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double DMU0x0N[] = new double[2];//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
        double DMU0x1N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
        double DMU1x0N[] = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
        double DMU1x1N[] = new double[2];//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase

        double[] delDMUe = new double[2];
        double[] Dx0e0N = new double[phase0.getNP()];//Derivatives xB of alfa phase with alfa phase ECIs
        double[] Dx1e0N = new double[phase0.getNP()];//Derivatives xB of beta phase with alfa phase ECIs
        double[] Dx0e1N = new double[phase1.getNP()];//Derivatives xB of alfa phase with beta phase ECIs
        double[] Dx1e1N = new double[phase1.getNP()];//Derivatives xB of beta phase with beta phase ECIs
        double[][] DMU0e0N = new double[2][phase0.getNP()];//Derivatives MU of alfa phase with alfa phase ECIs
        double[][] DMU0e1N = new double[2][phase1.getNP()];//to be checked//Derivatives MU of beta phase with alfa phase ECIs
        double[][] DMU1e0N = new double[2][phase0.getNP()];//to be checked//Derivatives MU of alfa phase with beta phase ECIs
        double[][] DMU1e1N = new double[2][phase1.getNP()];;//Derivatives MU of beta phase with beta phase ECIs

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            MU0N = phase0.calGAB();//MU will be calculated at xB
            DMU0x0N = phase0.calDMUx();
            phase1.setT(T_local);
            phase1.setX(xBeta);
            //phase1.printPhaseInfo();
            MU1N = phase1.calGAB();//MU will be calculated at xB
            DMU1x1N = phase1.calDMUx();
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCXXB() ended with T_local:" + df.format(T_local) + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[0]);
            double stpmax1 = Utils.stpmaX(xBeta, xBeta + delX[1]);
            double stpmax = Math.min(stpmax0, stpmax1);
            xAlpha = xAlpha + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", stpmax:" + stpmax, 3);
        }
        DMU0e0N = phase0.calDMUe();
        DMU1e1N = phase1.calDMUe();
        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            delDMUe[0] = -(DMU1e0N[0][icf0] - DMU0e0N[0][icf0]);
            delDMUe[1] = -(DMU1e0N[1][icf0] - DMU0e0N[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            Dx0e0N[icf0] = X.getArray()[0][0];
            Dx1e0N[icf0] = X.getArray()[1][0];
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            delDMUe[0] = -(DMU1e1N[0][icf1] - DMU0e1N[0][icf1]);
            delDMUe[1] = -(DMU1e1N[1][icf1] - DMU0e1N[1][icf1]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            Dx0e1N[icf1] = X.getArray()[0][0];
            Dx1e1N[icf1] = X.getArray()[1][0];
        }
        //Output
        modDataIn[0] = xBeta;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        System.arraycopy(Dx1e0N, 0, dydaIn, 0, (phase0.getNP()));
        System.arraycopy(Dx1e1N, 0, dydaIn, (phase0.getNP()), (phase1.getNP()));
        Print.f("Model:calCXXB() method called", 7);
    }

    public void calCXTA(double[] modDataIn) throws IOException {
        Print.f("Model:calCXTA() method called", 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha_local = modDataIn[2];//x2:Composition of alfa phase
        double xBeta_local = modDataIn[3];//y:Composition of beta phase
        //Print.f("T_local",xAlpha_local, 0);
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha_local);
            MU0N = phase0.calGAB();//MU will be calculated at xB
            //double[] DMU0x0N = phase0.calDMUx();//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
            double[] DMU0x1N = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
            double[] DMU0TN = phase0.calDMUT();//Derivatives of {MUA, MUB} of alfa phase with temperature
            phase1.setT(T_local);
            phase1.setX(xBeta_local);
            MU1N = phase1.calGAB();//MU will be calculated at xB
            //double[] DMU1x0N = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            double[] DMU1x1N = phase1.calDMUx();//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
            double[] DMU1TN = phase1.calDMUT();//Derivatives of {MUA, MUB} of beta phase with temperature
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1TN[0] - DMU0TN[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1TN[1] - DMU0TN[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCXTA() ended with T_local:" + df.format(T_local) + ", x0_local:" + df.format(xAlpha_local) + ", x1_local:" + df.format(xBeta_local) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax = Utils.stpmaX(xBeta_local, xBeta_local + delX[1]);
            T_local = T_local + stpmax * delX[0];
            xBeta_local = xBeta_local + stpmax * delX[1];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(xAlpha_local) + ", x1_local:" + df.format(xBeta_local) + ", stpmax:" + stpmax, 3);
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha_local;
        modDataIn[3] = xBeta_local;
        Print.f("Model:calCXTA() method called", 7);
    }

    /**
     *
     * @param modDataIn {ymod-T, T, xalfa, xbeta}
     * @param dydaIn
     * @throws IOException
     */
    public void calCXTA(double[] modDataIn, double[] dydaIn) throws IOException {
        Print.f("Model:calCXTA() method called", 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//x2:Composition of alfa phase
        double xBeta = modDataIn[3];//y:Composition of beta phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-06;
        double errf = 0;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase

        double[] delDMUe = new double[2];
        double[] DTe0N = new double[phase0.getNP()];//Derivatives T with alfa phase ECIs
        //double[] Dx0e0N = new double[phase0.getNP()];//Derivatives xB of alfa phase with alfa phase ECIs
        double[] Dx1e0N = new double[phase0.getNP()];//Derivatives xB of beta phase with alfa phase ECIs
        double[] DTe1N = new double[phase1.getNP()];//Derivatives T with beta phase ECIs
        //double[] Dx0e1N = new double[phase1.getNP()];//Derivatives xB of alfa phase with beta phase ECIs
        double[] Dx1e1N = new double[phase1.getNP()];//Derivatives xB of beta phase with beta phase ECIs
        double[][] DMU0e0N = new double[2][phase0.getNP()];//Derivatives MU of alfa phase with alfa phase ECIs
        double[][] DMU0e1N = new double[2][phase1.getNP()];//to be checked//Derivatives MU of beta phase with alfa phase ECIs
        double[][] DMU1e0N = new double[2][phase0.getNP()];//to be checked//Derivatives MU of alfa phase with beta phase ECIs
        double[][] DMU1e1N = new double[2][phase1.getNP()];//Derivatives MU of beta phase with beta phase ECIs

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            MU0N = phase0.calGAB();//MU will be calculated at xB
            //double[] DMU0x0N = phase0.calDMUx();//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
            double[] DMU0x1N = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
            double[] DMU0TN = phase0.calDMUT();//Derivatives of {MUA, MUB} of alfa phase with temperature
            phase1.setT(T_local);
            phase1.setX(xBeta);
            MU1N = phase1.calGAB();//MU will be calculated at xB
            //double[] DMU1x0N = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            double[] DMU1x1N = phase1.calDMUx();//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
            double[] DMU1TN = phase1.calDMUT();//Derivatives of {MUA, MUB} of beta phase with temperature
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1TN[0] - DMU0TN[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[1][0] = DMU1TN[1] - DMU0TN[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCXTA() ended with T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax = Utils.stpmaX(xBeta, xBeta + delX[1]);
            T_local = T_local + stpmax * delX[0];
            xBeta = xBeta + stpmax * delX[1];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", stpmax:" + stpmax, 3);
        }
        DMU0e0N = phase0.calDMUe();
        DMU1e1N = phase1.calDMUe();
        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            delDMUe[0] = -(DMU1e0N[0][icf0] - DMU0e0N[0][icf0]);
            delDMUe[1] = -(DMU1e0N[1][icf0] - DMU0e0N[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTe0N[icf0] = X.getArray()[0][0];
            Dx1e0N[icf0] = X.getArray()[1][0];
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            delDMUe[0] = -(DMU1e1N[0][icf1] - DMU0e1N[0][icf1]);
            delDMUe[1] = -(DMU1e1N[1][icf1] - DMU0e1N[1][icf1]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTe1N[icf1] = X.getArray()[0][0];
            Dx1e1N[icf1] = X.getArray()[1][0];
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        System.arraycopy(DTe0N, 0, dydaIn, 0, (phase0.getNP()));
        System.arraycopy(DTe1N, 0, dydaIn, (phase0.getNP()), (phase1.getNP()));
        Print.f("Model:calCXTA() method called", 7);
    }

    public void calCXTB(double[] modDataIn) throws IOException {
        Print.f("Model.calCXTB() method called for " + phase0.getPhaseTag() + " and " + phase1.getPhaseTag(), 7);
        //Initialization
        double T_local = modDataIn[1];//Temperature
        double xAlpha = modDataIn[2];//Composition of alfa phase
        double xBeta = modDataIn[3];//Composition of beta phase

        //calculation
        int ntrial = 20;
        int counter;
        //double tolf = 1E-06;
        double tolf = ((("A2ORCBINCE".equals(phase0.getPhaseTag())) && (xAlpha < 0.02)) ? 10 : 1E-06);
        double errf = 0;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            double[] MU0N = phase0.calGAB();//MU will be calculated at xB
            double[] DMU0x0N = phase0.calDMUx();//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
            //double[] DMU0x1N = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
            double[] DMU0TN = phase0.calDMUT();//Derivatives of {MUA, MUB} of alfa phase with temperature
            phase1.setT(T_local);
            phase1.setX(xBeta);
            double[] MU1N = phase1.calGAB();//MU will be calculated at xB
            double[] DMU1x0N = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            //double[] DMU1x1N = phase1.calDMUx();//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
            double[] DMU1TN = phase1.calDMUT();//Derivatives of {MUA, MUB} of beta phase with temperature
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1TN[0] - DMU0TN[0];
            fjac[0][1] = DMU1x0N[0] - DMU0x0N[0];
            fjac[1][0] = DMU1TN[1] - DMU0TN[1];
            fjac[1][1] = DMU1x0N[1] - DMU0x0N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCXTB() ended with T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[1]);
            double stpmax = stpmax0;
            T_local = T_local + stpmax * delX[0];
            xAlpha = xAlpha + stpmax * delX[1];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 3);
        }
        if (counter == ntrial) {
            Print.f("Model:calCXTB() executed with errf:" + df.format(errf), 2);
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        Print.f("Model:calCXTB() method called", 7);
    }

    public void calCXTB(double[] modDataIn, double[] dydaIn) throws IOException {
        Print.f("Model.calCXTB() method called for " + phase0.getPhaseTag() + " and " + phase1.getPhaseTag(), 7);
        //Initialization
        double T_local = modDataIn[1];//x:Temperature
        double xAlpha = modDataIn[2];//x2:Composition of alfa phase
        double xBeta = modDataIn[3];//y:Composition of beta phase
        //Print.f("T_local", T_local, 0);
        //Print.f("xAlpha_local", xAlpha_local, 0);
        //Print.f("xBeta_local", xBeta_local, 0);
        //calculation
        int ntrial = 20;
        int counter;
        //double tolf = 1E-06;
        double tolf = ((("A2ORCBINCE".equals(phase0.getPhaseTag())) && (xAlpha < 0.02)) ? 10 : 1E-06);
        double errf = 0;
        int n = 2;
        double fvec[] = new double[2];
        double fjac[][] = new double[2][2];
        //double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        //double MU1N[] = new double[2];//MUA, MUB of beta phase

        double[] delDMUe = new double[2];
        //double[] DTe0N = new double[phase0.getNP()];//Derivatives T with alfa phase ECIs
        //double[] Dx0e0N = new double[phase0.getNP()];//Derivatives xB of alfa phase with alfa phase ECIs
        //double[] Dx1e0N = new double[phase0.getNP()];//Derivatives xB of beta phase with alfa phase ECIs
        double[] DTe1N = new double[phase1.getNP()];//Derivatives T with beta phase ECIs
        double[] Dx0e1N = new double[phase1.getNP()];//Derivatives xB of alfa phase with beta phase ECIs
        //double[] Dx1e1N = new double[phase1.getNP()];//Derivatives xB of beta phase with beta phase ECIs
        //double[][] DMU0e0N = new double[2][phase0.getNP()];//Derivatives MU of alfa phase with alfa phase ECIs
        //double[][] DMU0e1N = new double[2][phase1.getNP()];//to be checked//Derivatives MU of beta phase with alfa phase ECIs
        //double[][] DMU1e0N = new double[2][phase0.getNP()];//to be checked//Derivatives MU of alfa phase with beta phase ECIs
        //double[][] DMU1e1N = new double[2][phase1.getNP()];//Derivatives MU of beta phase with beta phase ECIs

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(xAlpha);
            double[] MU0N = phase0.calGAB();//MU will be calculated at xB
            double[] DMU0x0N = phase0.calDMUx();//Derivatives of {MUA, MUB} of alfa phase with composition of xB of alfa phase
            //double[] DMU0x1N = {0.0, 0.0};//Derivatives of {MUA, MUB} of alfa phase with composition of xB of beta phase
            double[] DMU0TN = phase0.calDMUT();//Derivatives of {MUA, MUB} of alfa phase with temperature
            phase1.setT(T_local);
            phase1.setX(xBeta);
            double[] MU1N = phase1.calGAB();//MU will be calculated at xB
            double[] DMU1x0N = {0.0, 0.0};//Derivatives of {MUA, MUB} of beta phase with composition of xB of alfa phase
            //double[] DMU1x1N = phase1.calDMUx();//Derivatives of {MUA, MUB} of beta phase with composition of xB of beta phase
            double[] DMU1TN = phase1.calDMUT();//Derivatives of {MUA, MUB} of beta phase with temperature
            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fjac[0][0] = DMU1TN[0] - DMU0TN[0];
            fjac[0][1] = DMU1x0N[0] - DMU0x0N[0];
            fjac[1][0] = DMU1TN[1] - DMU0TN[1];
            fjac[1][1] = DMU1x0N[1] - DMU0x0N[1];
            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCXTB() ended with T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", errf:" + df.format(errf) + ", MUA:" + MU1N[0] + ", MUB:" + MU1N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double stpmax0 = Utils.stpmaX(xAlpha, xAlpha + delX[1]);
            double stpmax = stpmax0;
            T_local = T_local + stpmax * delX[0];
            xAlpha = xAlpha + stpmax * delX[1];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(xAlpha) + ", x1_local:" + df.format(xBeta) + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 3);
        }
        if (counter == ntrial) {
            Print.f("Model:calCXTB() executed with errf:" + df.format(errf), 2);
        }
        double[][] DMU0e0N = phase0.calDMUe();
        double[][] DMU0e1N = new double[2][phase1.getNP()];
        double[][] DMU1e1N = phase1.calDMUe();
        double[][] DMU1e0N = new double[2][phase0.getNP()];
        double[] DTe0N = new double[phase0.getNP()];
        double[] Dx0e0N = new double[phase0.getNP()];
        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            delDMUe[0] = -(DMU1e0N[0][icf0] - DMU0e0N[0][icf0]);
            delDMUe[1] = -(DMU1e0N[1][icf0] - DMU0e0N[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTe0N[icf0] = X.getArray()[0][0];
            Dx0e0N[icf0] = X.getArray()[1][0];
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            delDMUe[0] = -(DMU1e1N[0][icf1] - DMU0e1N[0][icf1]);
            delDMUe[1] = -(DMU1e1N[1][icf1] - DMU0e1N[1][icf1]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTe1N[icf1] = X.getArray()[0][0];
            Dx0e1N[icf1] = X.getArray()[1][0];
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[1] = T_local;
        modDataIn[2] = xAlpha;
        modDataIn[3] = xBeta;
        System.arraycopy(DTe0N, 0, dydaIn, 0, (phase0.getNP()));
        System.arraycopy(DTe1N, 0, dydaIn, (phase0.getNP()), (phase1.getNP()));
        Print.f("Model:calCXTB() method called", 7);
    }

    public void calCRPT(double[] modDataIn) throws IOException {//vj-2012-05-12:Critical point for order-disorder (First order) transformation  
        Print.f("Model.calCRPT() method called", 7);
        calMXX(modDataIn);
        Print.f("Model.calCRPT() method called", 7);
    }

    public void calCRPT(double[] modDataIn, double[] dydaIn) throws IOException {//vj-2012-05-12:Critical point for order-disorder (First order) transformation  
        Print.f("Model.calCRPT() with dydaIn method called", 7);
        double T_local = modDataIn[1];
        double x_local = modDataIn[2];
        double delT;
        double delX;
        double errG = 1E-6;
        double errGx = 1E-6;
        int maxIterations = 20;
        double GN0, DGTN0 = 0, DGxN0, DGTxN0 = 0, DGxxN0 = 0;
        double GN1, DGTN1 = 0, DGxN1, DGTxN1 = 0, DGxxN1 = 0;
        double delG = 0, delGT, delGx = 0, delGTx, delGxx;
        int iter;

        for (iter = 0; iter < 1; iter++) {
            phase0.setT(T_local);
            phase0.setX(x_local);
            double LRO = phase0.calLRO();
            Print.f("LRO", LRO, 1);
        }
        for (iter = 0; iter < maxIterations; iter++) {
            Print.f("iter:" + iter + ", T:" + T_local + ", x:" + x_local, 3);
            phase0.setT(T_local);
            phase0.setX(x_local);
            GN0 = phase0.calG();
            DGTN0 = phase0.calDGT();
            DGxN0 = phase0.calDGx();
            DGTxN0 = phase0.calDGTx();
            DGxxN0 = phase0.calDGxx();
            //MU0N = phase0.calGAB();//SU:2012.05.22
            phase1.setT(T_local);
            phase1.setX(x_local);
            GN1 = phase1.calG();
            DGTN1 = phase1.calDGT();
            DGxN1 = phase1.calDGx();
            DGTxN1 = phase1.calDGTx();
            DGxxN1 = phase1.calDGxx();
            //MU1N = phase1.calGAB();//SU:2012.05.22
            delG = GN1 - GN0;
            delGx = DGxN1 - DGxN0;
            delGT = DGTN1 - DGTN0;
            delGTx = DGTxN1 - DGTxN0;
            delGxx = DGxxN1 - DGxxN0;
            if ((Math.abs(delG) <= errG) && (Math.abs(delGx) <= errGx)) {
                Print.f("calCRPT():Tolerance on f reached in " + iter + " iterations, errG:" + Math.abs(delG) + ", errGx:" + (Math.abs(delGx)), 2);
                //modDataIn[0] = T_local;
                //modDataIn[1] = x_local;
                break;
            }
            delT = -(delG / delGT);
            delX = (delG * delGTx - delGT * delGx) / (delGxx * delGT);
            double stpmax0 = Utils.stpmaX(x_local, x_local + delX);
            T_local = T_local + stpmax0 * delT;
            x_local = x_local + stpmax0 * delX;

        }
        if (iter == maxIterations) {
            Print.f("!calMXX():Tolerance on f not reached in " + maxIterations + " iterations, errG:" + Math.abs(delG) + ", errGx:" + (Math.abs(delGx)), 2);
            //modDataIn[0] = T_local;
            //modDataIn[1] = x_local;
        }

        double fjac[][] = new double[2][2];
        fjac[0][0] = (DGTN1 - x_local * DGTxN1) - (DGTN0 - x_local * DGTxN0);
        fjac[1][0] = (DGTN1 - (1 - x_local) * DGTxN1) - (DGTN0 - (1 - x_local) * DGTxN0);
        fjac[0][1] = (-x_local * DGxxN1) - (-x_local * DGxxN0);
        fjac[1][1] = ((1 - x_local) * DGxxN1) - ((1 - x_local) * DGxxN0);
        double[][] DMU0e0N = phase0.calDMUe();
        double[][] DMU1e0N = new double[2][phase0.getNP()];
        double[][] DMU0e1N = new double[2][phase1.getNP()];
        double[][] DMU1e1N = phase1.calDMUe();
        double delDMUe[] = new double[2];
        double[] DTe0N = new double[phase0.getNP()], DTe1N = new double[phase1.getNP()];
        double[] Dxe0N = new double[phase0.getNP()], Dxe1N = new double[phase1.getNP()];

        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            delDMUe[0] = -(DMU1e0N[0][icf0] - DMU0e0N[0][icf0]);
            delDMUe[1] = -(DMU1e0N[1][icf0] - DMU0e0N[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTe0N[icf0] = X.getArray()[0][0];
            Dxe0N[icf0] = X.getArray()[1][0];
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            delDMUe[0] = -(DMU1e1N[0][icf1] - DMU0e1N[0][icf1]);
            delDMUe[1] = -(DMU1e1N[1][icf1] - DMU0e1N[1][icf1]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTe1N[icf1] = X.getArray()[0][0];
            Dxe1N[icf1] = X.getArray()[1][0];
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[1] = x_local;
        System.arraycopy(DTe0N, 0, dydaIn, 0, (phase0.getNP()));
        System.arraycopy(DTe1N, 0, dydaIn, (phase0.getNP()), (phase1.getNP()));
        //Print.f("dydaIn:", dydaIn, 1);
        Print.f("Model.calCRPT() method with dydaIn called", 7);
    }

    public void calMXX(double[] modDataIn) throws IOException {//vj-2012-05-10
        Print.f("Model.calMXX() method called with T:" + modDataIn[0] + " and x:" + modDataIn[1], 7);
        double T_local = modDataIn[1];
        double x_local = modDataIn[2];
        double delT;
        double delX;
        double errG = 1E-6;
        double errGx = 1E-6;
        int maxIterations = 20;
        double GN0, DGTN0 = 0, DGxN0, DGTxN0 = 0, DGxxN0 = 0;
        double GN1, DGTN1 = 0, DGxN1, DGTxN1 = 0, DGxxN1 = 0;
        double delG, delGT, delGx, delGTx, delGxx;
        int iter;
        for (iter = 0; iter < maxIterations; iter++) {
            phase0.setT(T_local);
            phase0.setX(x_local);
            //phase0.printPhaseInfo();
            GN0 = phase0.calG();
            DGTN0 = phase0.calDGT();
            DGxN0 = phase0.calDGx();
            DGTxN0 = phase0.calDGTx();
            DGxxN0 = phase0.calDGxx();
            //MU0N = phase0.calGAB();
            phase1.setT(T_local);
            phase1.setX(x_local);
            GN1 = phase1.calG();
            DGTN1 = phase1.calDGT();
            DGxN1 = phase1.calDGx();
            DGTxN1 = phase1.calDGTx();
            DGxxN1 = phase1.calDGxx();
            //MU1N = phase1.calGAB();
            delG = GN1 - GN0;
            delGx = DGxN1 - DGxN0;
            delGT = DGTN1 - DGTN0;
            delGTx = DGTxN1 - DGTxN0;
            delGxx = DGxxN1 - DGxxN0;
            if ((Math.abs(delG) <= errG) && (Math.abs(delGx) <= errGx)) {
                double[] mu = phase0.calGAB();
                Print.f("Methods.calMXX()ended with errG:" + errG + ", errGx:" + errGx + ", T:" + T_local + ", x:" + x_local + ", MUA:" + mu[0] + ", MUB:" + mu[1], 2);
                //modDataIn[0] = T_local;
                //modDataIn[1] = x_local;
                break;
            }
            delT = -(delG / delGT);
            delX = (delG * delGTx - delGT * delGx) / (delGxx * delGT);
            Print.f("iter:" + iter + ", T:" + T_local + ", x:" + x_local + ", delX:" + delX + ", delT:" + delT, 3);
            double stpmax0 = Utils.stpmaX(x_local, x_local + delX);
            T_local = T_local + stpmax0 * delT;
            x_local = x_local + stpmax0 * delX;
        }
        if (iter == maxIterations) {
            double[] mu = phase0.calGAB();
            Print.f("!Methods.calMXX()not converged and ended with errG:" + errG + ", errGx:" + errGx + ", MUA:" + mu[0] + ", MUB:" + mu[1], 2);
            //modDataIn[0] = T_local;
            //modDataIn[1] = x_local;
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[1] = T_local;
        modDataIn[2] = x_local;
        Print.f("Model.calMXX() method called", 7);
    }

    public void calMXX(double[] modDataIn, double[] dydaIn) throws IOException {//vj-2012-05-09
        Print.f("Model.calMXX() method called", 7);
        double T_local = modDataIn[0];
        double x_local = modDataIn[1];
        double delT;
        double delX;
        double errG = 1E-6;
        double errGx = 1E-6;
        int maxIterations = 20;
        double GN0, DGTN0 = 0, DGxN0, DGTxN0 = 0, DGxxN0 = 0;
        double GN1, DGTN1 = 0, DGxN1, DGTxN1 = 0, DGxxN1 = 0;
        double delG = 0, delGT, delGx = 0, delGTx, delGxx;
        int iter;
        for (iter = 0; iter < maxIterations; iter++) {
            Print.f("iter:" + iter + ", T:" + T_local + ", x:" + x_local, 3);
            phase0.setT(T_local);
            phase0.setX(x_local);
            GN0 = phase0.calG();
            DGTN0 = phase0.calDGT();
            DGxN0 = phase0.calDGx();
            DGTxN0 = phase0.calDGTx();
            DGxxN0 = phase0.calDGxx();
            //MU0N = phase0.calGAB();//SU:2012.05.22
            phase1.setT(T_local);
            phase1.setX(x_local);
            GN1 = phase1.calG();
            DGTN1 = phase1.calDGT();
            DGxN1 = phase1.calDGx();
            DGTxN1 = phase1.calDGTx();
            DGxxN1 = phase1.calDGxx();
            //MU1N = phase1.calGAB();//SU:2012.05.22
            delG = GN1 - GN0;
            delGx = DGxN1 - DGxN0;
            delGT = DGTN1 - DGTN0;
            delGTx = DGTxN1 - DGTxN0;
            delGxx = DGxxN1 - DGxxN0;
            if ((Math.abs(delG) <= errG) && (Math.abs(delGx) <= errGx)) {
                Print.f("calMXX():Tolerance on f reached in " + iter + " iterations, errG:" + errG + ", errGx:" + errGx, 2);
                //modDataIn[0] = T_local;
                //modDataIn[1] = x_local;
                break;
            }
            delT = -(delG / delGT);
            delX = (delG * delGTx - delGT * delGx) / (delGxx * delGT);
            double stpmax0 = Utils.stpmaX(x_local, x_local + delX);
            T_local = T_local + stpmax0 * delT;
            x_local = x_local + stpmax0 * delX;

        }
        if (iter == maxIterations) {
            Print.f("!calMXX():Tolerance on f not reached in " + maxIterations + " iterations, errG:" + delG + ", errGx:" + delGx, 2);
            //modDataIn[0] = T_local;
            //modDataIn[1] = x_local;
        }

        double fjac[][] = new double[2][2];
        fjac[0][0] = (DGTN1 - x_local * DGTxN1) - (DGTN0 - x_local * DGTxN0);
        fjac[1][0] = (DGTN1 - (1 - x_local) * DGTxN1) - (DGTN0 - (1 - x_local) * DGTxN0);
        fjac[0][1] = (-x_local * DGxxN1) - (-x_local * DGxxN0);
        fjac[1][1] = ((1 - x_local) * DGxxN1) - ((1 - x_local) * DGxxN0);
        double[][] DMU0e0N = phase0.calDMUe();
        double[][] DMU1e0N = new double[2][phase0.getNP()];
        double[][] DMU0e1N = new double[2][phase1.getNP()];
        double[][] DMU1e1N = phase1.calDMUe();
        double delDMUe[] = new double[2];
        double[] DTe0N = new double[phase0.getNP()], DTe1N = new double[phase1.getNP()];
        double[] Dxe0N = new double[phase0.getNP()], Dxe1N = new double[phase1.getNP()];

        Matrix A = new Matrix(fjac);
        LUDecomposition CD = new LUDecomposition(A);
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            delDMUe[0] = -(DMU1e0N[0][icf0] - DMU0e0N[0][icf0]);
            delDMUe[1] = -(DMU1e0N[1][icf0] - DMU0e0N[1][icf0]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTe0N[icf0] = X.getArray()[0][0];
            Dxe0N[icf0] = X.getArray()[1][0];
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            delDMUe[0] = -(DMU1e1N[0][icf1] - DMU0e1N[0][icf1]);
            delDMUe[1] = -(DMU1e1N[1][icf1] - DMU0e1N[1][icf1]);
            Matrix B = new Matrix(delDMUe);
            Matrix X = CD.solve(B);
            DTe1N[icf1] = X.getArray()[0][0];
            Dxe1N[icf1] = X.getArray()[1][0];
        }
        //Output
        modDataIn[0] = T_local;
        modDataIn[1] = x_local;
        System.arraycopy(DTe0N, 0, dydaIn, 0, (phase0.getNP()));
        System.arraycopy(DTe1N, 0, dydaIn, (phase0.getNP()), (phase1.getNP()));
        Print.f("dydaIn:", dydaIn, 1);
        Print.f("Model.calMXX() method called", 7);
    }

    public void calCX31(double[] modDataIn) throws IOException {//Aj-2012-03-20 //alfa phase independent
        Print.f("Model:calCX31() method called", 7);
        //Initialization
        double T_local = modDataIn[0];//Temperature
        double x0_local = modDataIn[1];//Composition of alfa phase
        double x1_local = modDataIn[2];//Composition of beta phase
        double x2_local = modDataIn[3];//Composition of gamma phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-08;
        double errf = 0;
        int n = 4;
        double fvec[] = new double[4];
        double fjac[][] = new double[4][4];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double MU2N[] = new double[2];//MUA, MUB of gama phase
        double DMU0x0N[] = new double[2];
        double DMU0x1N[] = {0.0, 0.0};
        double DMU0x2N[] = {0.0, 0.0};
        double DMU0TN[] = new double[2];
        double DMU1x0N[] = {0.0, 0.0};
        double DMU1x1N[] = new double[2];
        double DMU1x2N[] = {0.0, 0.0};
        double DMU1TN[] = new double[2];
        double DMU2x0N[] = {0.0, 0.0};
        double DMU2x1N[] = {0.0, 0.0};
        double DMU2x2N[] = new double[2];
        double DMU2TN[] = new double[2];

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x1_local);
            MU1N = phase1.calGAB();
            DMU1x1N = phase1.calDMUx();
            DMU1TN = phase1.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x2_local);
            MU2N = phase1.calGAB();
            DMU2x2N = phase1.calDMUx();
            DMU2TN = phase1.calDMUT();

            fvec[0] = -(MU1N[0] - MU2N[0]);
            fvec[1] = -(MU1N[1] - MU2N[1]);
            fvec[2] = -(MU0N[0] - MU2N[0]);
            fvec[3] = -(MU0N[1] - MU2N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU2x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU2x1N[0];
            fjac[0][2] = DMU1x2N[0] - DMU2x2N[0];
            fjac[0][3] = DMU1TN[0] - DMU2TN[0];
            fjac[1][0] = DMU1x0N[1] - DMU2x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU2x1N[1];
            fjac[1][2] = DMU1x2N[1] - DMU2x2N[1];
            fjac[1][3] = DMU1TN[1] - DMU2TN[1];
            fjac[2][0] = DMU0x0N[0] - DMU2x0N[0];
            fjac[2][1] = DMU0x1N[0] - DMU2x1N[0];
            fjac[2][2] = DMU0x2N[0] - DMU2x2N[0];
            fjac[2][3] = DMU0TN[0] - DMU2TN[0];
            fjac[3][0] = DMU0x0N[1] - DMU2x0N[1];
            fjac[3][1] = DMU0x1N[1] - DMU2x1N[1];
            fjac[3][2] = DMU0x2N[1] - DMU2x2N[1];
            fjac[3][3] = DMU0TN[1] - DMU2TN[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCX31() executed with errf:" + df.format(errf), 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double x0Trial = x0_local + delX[0];
            double stpmax0 = Utils.stpmaX(x0_local, x0Trial);
            double x1Trial = x1_local + delX[1];
            double stpmax1 = Utils.stpmaX(x1_local, x1Trial);
            double x2Trial = x2_local + delX[2];
            double stpmax2 = Utils.stpmaX(x2_local, x2Trial);
            double stpmax = Math.min(Math.min(stpmax0, stpmax1), stpmax2);
            T_local = T_local + stpmax * delX[3];
            x0_local = x0_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            x2_local = x2_local + stpmax * delX[2];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 2);
        }
        modDataIn[0] = T_local;//Temperature
        modDataIn[1] = x0_local;//Composition of alfa phase
        modDataIn[2] = x1_local;//Composition of beta phase
        modDataIn[3] = x2_local;//Composition of gamma phase
    }

    /*
    
     */
    public void calCX31(double[] modDataIn, double[] dyda_in) throws IOException {//Aj-2012-03-20 //alfa phase independent
        Print.f("Model:calCX31() method called", 7);
        //Initialization
        double T_local = modDataIn[0];//Temperature
        double x0_local = modDataIn[1];//Composition of alfa phase
        double x1_local = modDataIn[2];//Composition of beta phase
        double x2_local = modDataIn[3];//Composition of gamma phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-08;
        double errf = 0;
        int n = 4;
        double fvec[] = new double[4];
        double fjac[][] = new double[4][4];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double MU2N[] = new double[2];//MUA, MUB of gama phase
        double DMU0x0N[] = new double[2];
        double DMU0x1N[] = {0.0, 0.0};
        double DMU0x2N[] = {0.0, 0.0};
        double DMU0TN[] = new double[2];
        double DMU1x0N[] = {0.0, 0.0};
        double DMU1x1N[] = new double[2];
        double DMU1x2N[] = {0.0, 0.0};
        double DMU1TN[] = new double[2];
        double DMU2x0N[] = {0.0, 0.0};
        double DMU2x1N[] = {0.0, 0.0};
        double DMU2x2N[] = new double[2];
        double DMU2TN[] = new double[2];

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x1_local);
            MU1N = phase1.calGAB();
            DMU1x1N = phase1.calDMUx();
            DMU1TN = phase1.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x2_local);
            MU2N = phase1.calGAB();
            DMU2x2N = phase1.calDMUx();
            DMU2TN = phase1.calDMUT();

            fvec[0] = -(MU1N[0] - MU2N[0]);
            fvec[1] = -(MU1N[1] - MU2N[1]);
            fvec[2] = -(MU0N[0] - MU2N[0]);
            fvec[3] = -(MU0N[1] - MU2N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU2x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU2x1N[0];
            fjac[0][2] = DMU1x2N[0] - DMU2x2N[0];
            fjac[0][3] = DMU1TN[0] - DMU2TN[0];
            fjac[1][0] = DMU1x0N[1] - DMU2x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU2x1N[1];
            fjac[1][2] = DMU1x2N[1] - DMU2x2N[1];
            fjac[1][3] = DMU1TN[1] - DMU2TN[1];
            fjac[2][0] = DMU0x0N[0] - DMU2x0N[0];
            fjac[2][1] = DMU0x1N[0] - DMU2x1N[0];
            fjac[2][2] = DMU0x2N[0] - DMU2x2N[0];
            fjac[2][3] = DMU0TN[0] - DMU2TN[0];
            fjac[3][0] = DMU0x0N[1] - DMU2x0N[1];
            fjac[3][1] = DMU0x1N[1] - DMU2x1N[1];
            fjac[3][2] = DMU0x2N[1] - DMU2x2N[1];
            fjac[3][3] = DMU0TN[1] - DMU2TN[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCX31() executed with errf:" + df.format(errf), 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double x0Trial = x0_local + delX[0];
            double stpmax0 = Utils.stpmaX(x0_local, x0Trial);
            double x1Trial = x1_local + delX[1];
            double stpmax1 = Utils.stpmaX(x1_local, x1Trial);
            double x2Trial = x2_local + delX[2];
            double stpmax2 = Utils.stpmaX(x2_local, x2Trial);
            double stpmax = Math.min(Math.min(stpmax0, stpmax1), stpmax2);
            T_local = T_local + stpmax * delX[3];
            x0_local = x0_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            x2_local = x2_local + stpmax * delX[2];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 2);
        }
        modDataIn[0] = T_local;//Temperature
        modDataIn[1] = x0_local;//Composition of alfa phase
        modDataIn[2] = x1_local;//Composition of beta phase
        modDataIn[3] = x2_local;//Composition of gamma phase

        //dyda calculations        
        phase0.setT(T_local);
        phase0.setX(x0_local);
        double[][] DMU0e0N = phase0.calDMUe();
        double[][] DMU0e1N = new double[2][phase1.getNP()];//to be checked
        double[][] DMU0e2N = new double[2][phase1.getNP()];//to be checked

        phase1.setT(T_local);
        phase1.setX(x1_local);
        double[][] DMU1e0N = new double[2][phase0.getNP()];//to be checked
        double[][] DMU1e1N = phase1.calDMUe();
        double[][] DMU1e2N = phase1.calDMUe();//to be checked
        phase1.setT(T_local);
        phase1.setX(x2_local);
        double[][] DMU2e0N = new double[2][phase0.getNP()];//to be checked
        double[][] DMU2e1N = phase1.calDMUe();//to be checked
        double[][] DMU2e2N = phase1.calDMUe();

        Matrix A = new Matrix(fjac);
        LUDecomposition LD = new LUDecomposition(A);
        double[] p = new double[4];
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            p[0] = -(DMU1e0N[0][icf0] - DMU2e0N[0][icf0]);
            p[1] = -(DMU1e0N[1][icf0] - DMU2e0N[1][icf0]);
            p[2] = -(DMU0e0N[0][icf0] - DMU2e0N[0][icf0]);
            p[3] = -(DMU0e0N[1][icf0] - DMU2e0N[1][icf0]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[icf0] = X.getArray()[3][0];
            //System.out.println(icf0+" "+X.getArray()[3][0]);
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            p[0] = -(DMU1e1N[0][icf1] - DMU2e1N[0][icf1]);
            p[1] = -(DMU1e1N[1][icf1] - DMU2e1N[1][icf1]);
            p[2] = -(DMU0e1N[0][icf1] - DMU2e1N[0][icf1]);
            p[3] = -(DMU0e1N[1][icf1] - DMU2e1N[1][icf1]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[(phase0.getNP()) + icf1] = X.getArray()[3][0];
            // System.out.println(icf1+" "+X.getArray()[3][0]);
        }
    }

    public void calCX32T(double[] modDataIn, double[] dyda_in) throws IOException {//VJ-2013-06-20 //alfa phase independent and used in making mu equations
        Print.f("Model:calCX31() method called", 7);
        Print.f("Methods.calCX32() method called with phases: " + phase0.getPhaseTag() + ", " + phase1.getPhaseTag(), 2);
        //Initialization
        double T_local = modDataIn[0];//Temperature
        double x0_local = modDataIn[1];//Composition of alfa phase
        double x1_local = modDataIn[2];//Composition of beta phase
        double x2_local = modDataIn[3];//Composition of gamma phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-08;
        double errf = 0;
        int n = 4;
        double fvec[] = new double[4];
        double fjac[][] = new double[4][4];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double MU2N[] = new double[2];//MUA, MUB of gama phase
        double DMU0x0N[] = new double[2];
        double DMU0x1N[] = {0.0, 0.0};
        double DMU0x2N[] = {0.0, 0.0};
        double DMU0TN[] = new double[2];
        double DMU1x0N[] = {0.0, 0.0};
        double DMU1x1N[] = new double[2];
        double DMU1x2N[] = {0.0, 0.0};
        double DMU1TN[] = new double[2];
        double DMU2x0N[] = {0.0, 0.0};
        double DMU2x1N[] = {0.0, 0.0};
        double DMU2x2N[] = new double[2];
        double DMU2TN[] = new double[2];

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x1_local);
            MU1N = phase1.calGAB();
            DMU1x1N = phase1.calDMUx();
            DMU1TN = phase1.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x2_local);
            MU2N = phase1.calGAB();
            DMU2x2N = phase1.calDMUx();
            DMU2TN = phase1.calDMUT();

            fvec[0] = -(MU1N[0] - MU0N[0]);
            fvec[1] = -(MU1N[1] - MU0N[1]);
            fvec[2] = -(MU2N[0] - MU0N[0]);
            fvec[3] = -(MU2N[1] - MU0N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU0x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU0x1N[0];
            fjac[0][2] = DMU1x2N[0] - DMU0x2N[0];
            fjac[0][3] = DMU1TN[0] - DMU0TN[0];
            fjac[1][0] = DMU1x0N[1] - DMU0x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU0x1N[1];
            fjac[1][2] = DMU1x2N[1] - DMU0x2N[1];
            fjac[1][3] = DMU1TN[1] - DMU0TN[1];
            fjac[2][0] = DMU2x0N[0] - DMU0x0N[0];
            fjac[2][1] = DMU2x1N[0] - DMU0x1N[0];
            fjac[2][2] = DMU2x2N[0] - DMU0x2N[0];
            fjac[2][3] = DMU2TN[0] - DMU0TN[0];
            fjac[3][0] = DMU2x0N[1] - DMU0x0N[1];
            fjac[3][1] = DMU2x1N[1] - DMU0x1N[1];
            fjac[3][2] = DMU2x2N[1] - DMU0x2N[1];
            fjac[3][3] = DMU2TN[1] - DMU0TN[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCX31() executed with errf:" + df.format(errf), 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double x0Trial = x0_local + delX[0];
            double stpmax0 = Utils.stpmaX(x0_local, x0Trial);
            double x1Trial = x1_local + delX[1];
            double stpmax1 = Utils.stpmaX(x1_local, x1Trial);
            double x2Trial = x2_local + delX[2];
            double stpmax2 = Utils.stpmaX(x2_local, x2Trial);
            double stpmax = Math.min(Math.min(stpmax0, stpmax1), stpmax2);
            T_local = T_local + stpmax * delX[3];
            x0_local = x0_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            x2_local = x2_local + stpmax * delX[2];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 2);
        }
        modDataIn[0] = T_local;//Temperature
        modDataIn[1] = x0_local;//Composition of alfa phase
        modDataIn[2] = x1_local;//Composition of beta phase
        modDataIn[3] = x2_local;//Composition of gamma phase

        //dyda calculations        
        phase0.setT(T_local);
        phase0.setX(x0_local);
        double[][] DMU0e0N = phase0.calDMUe();
        double[][] DMU0e1N = new double[2][phase1.getNP()];//to be checked
        double[][] DMU0e2N = new double[2][phase1.getNP()];//to be checked
        phase1.setT(T_local);
        phase1.setX(x1_local);
        double[][] DMU1e0N = new double[2][phase0.getNP()];//to be checked
        double[][] DMU1e1N = phase1.calDMUe();
        double[][] DMU1e2N = phase1.calDMUe();//to be checked
        phase1.setT(T_local);
        phase1.setX(x2_local);
        double[][] DMU2e0N = new double[2][phase0.getNP()];//to be checked
        double[][] DMU2e1N = phase1.calDMUe();//to be checked
        double[][] DMU2e2N = phase1.calDMUe();

        Matrix A = new Matrix(fjac);
        LUDecomposition LD = new LUDecomposition(A);
        double[] p = new double[4];
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            p[0] = -(DMU1e0N[0][icf0] - DMU0e0N[0][icf0]);
            p[1] = -(DMU1e0N[1][icf0] - DMU0e0N[1][icf0]);
            p[2] = -(DMU2e0N[0][icf0] - DMU0e0N[0][icf0]);
            p[3] = -(DMU2e0N[1][icf0] - DMU0e0N[1][icf0]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[icf0] = X.getArray()[3][0];
            //System.out.println(icf0+" "+X.getArray()[3][0]);
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            p[0] = -(DMU1e1N[0][icf1] - DMU0e1N[0][icf1]);
            p[1] = -(DMU1e1N[1][icf1] - DMU0e1N[1][icf1]);
            p[2] = -(DMU2e1N[0][icf1] - DMU0e1N[0][icf1]);
            p[3] = -(DMU2e1N[1][icf1] - DMU0e1N[1][icf1]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[(phase0.getNP()) + icf1] = X.getArray()[3][0];
            // System.out.println(icf1+" "+X.getArray()[3][0]);
        }
    }

    public void calCX33(double[] modDataIn) throws IOException {//Aj-2012-03-20 //Y phase independent
        Print.f("Model:calCX33() method called", 7);
        phase0.printPhaseInfo();
        //Initialization
        double T_local = modDataIn[0];//Temperature
        double x0_local = modDataIn[1];//Composition of alfa phase
        double x1_local = modDataIn[2];//Composition of beta phase
        double x2_local = modDataIn[3];//Composition of gamma phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-08;
        double errf = 0;
        int n = 4;
        double fvec[] = new double[4];
        double fjac[][] = new double[4][4];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double MU2N[] = new double[2];//MUA, MUB of gama phase
        double DMU0x0N[] = new double[2];
        double DMU0x1N[] = {0.0, 0.0};
        double DMU0x2N[] = {0.0, 0.0};
        double DMU0TN[] = new double[2];
        double DMU1x0N[] = {0.0, 0.0};
        double DMU1x1N[] = new double[2];
        double DMU1x2N[] = {0.0, 0.0};
        double DMU1TN[] = new double[2];
        double DMU2x0N[] = {0.0, 0.0};
        double DMU2x1N[] = {0.0, 0.0};
        double DMU2x2N[] = new double[2];
        double DMU2TN[] = new double[2];

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();
            phase0.setT(T_local);
            phase0.setX(x1_local);
            MU1N = phase0.calGAB();
            DMU1x1N = phase0.calDMUx();
            DMU1TN = phase0.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x2_local);
            MU2N = phase1.calGAB();
            DMU2x2N = phase1.calDMUx();
            DMU2TN = phase1.calDMUT();

            fvec[0] = -(MU1N[0] - MU2N[0]);
            fvec[1] = -(MU1N[1] - MU2N[1]);
            fvec[2] = -(MU0N[0] - MU2N[0]);
            fvec[3] = -(MU0N[1] - MU2N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU2x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU2x1N[0];
            fjac[0][2] = DMU1x2N[0] - DMU2x2N[0];
            fjac[0][3] = DMU1TN[0] - DMU2TN[0];
            fjac[1][0] = DMU1x0N[1] - DMU2x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU2x1N[1];
            fjac[1][2] = DMU1x2N[1] - DMU2x2N[1];
            fjac[1][3] = DMU1TN[1] - DMU2TN[1];
            fjac[2][0] = DMU0x0N[0] - DMU2x0N[0];
            fjac[2][1] = DMU0x1N[0] - DMU2x1N[0];
            fjac[2][2] = DMU0x2N[0] - DMU2x2N[0];
            fjac[2][3] = DMU0TN[0] - DMU2TN[0];
            fjac[3][0] = DMU0x0N[1] - DMU2x0N[1];
            fjac[3][1] = DMU0x1N[1] - DMU2x1N[1];
            fjac[3][2] = DMU0x2N[1] - DMU2x2N[1];
            fjac[3][3] = DMU0TN[1] - DMU2TN[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCX31() executed with errf:" + df.format(errf), 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double x0Trial = x0_local + delX[0];
            double stpmax0 = Utils.stpmaX(x0_local, x0Trial);
            double x1Trial = x1_local + delX[1];
            double stpmax1 = Utils.stpmaX(x1_local, x1Trial);
            double x2Trial = x2_local + delX[2];
            double stpmax2 = Utils.stpmaX(x2_local, x2Trial);
            double stpmax = Math.min(Math.min(stpmax0, stpmax1), stpmax2);
            T_local = T_local + stpmax * delX[3];
            x0_local = x0_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            x2_local = x2_local + stpmax * delX[2];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 2);
        }
        modDataIn[0] = T_local;//Temperature
        modDataIn[1] = x0_local;//Composition of alfa phase
        modDataIn[2] = x1_local;//Composition of beta phase
        modDataIn[3] = x2_local;//Composition of gamma phase

    }

    /*
     15	CX33T-alpha-TO-1-gama-T-1        1148        1148            10          6       x(alpha)-1           x(alpha)-2        x(gama) 	    1962-Beaudry
     */
    public void calCX33(double[] modDataIn, double[] dyda_in) throws IOException {//Aj-2012-03-20 //Y phase independent
        Print.f("Model:calCX33() method called", 7);
        Print.f("Methods.calCX33() method called with phases: " + phase0.getPhaseTag() + ", " + phase1.getPhaseTag(), 2);
        //Initialization
        double T_local = modDataIn[0];//Temperature
        double x0_local = modDataIn[1];//Composition of alfa phase
        double x1_local = modDataIn[2];//Composition of beta phase
        double x2_local = modDataIn[3];//Composition of gamma phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-08;
        double errf = 0;
        int n = 4;
        double fvec[] = new double[4];
        double fjac[][] = new double[4][4];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double MU2N[] = new double[2];//MUA, MUB of gama phase
        double DMU0x0N[] = new double[2];
        double DMU0x1N[] = {0.0, 0.0};
        double DMU0x2N[] = {0.0, 0.0};
        double DMU0TN[] = new double[2];
        double DMU1x0N[] = {0.0, 0.0};
        double DMU1x1N[] = new double[2];
        double DMU1x2N[] = {0.0, 0.0};
        double DMU1TN[] = new double[2];
        double DMU2x0N[] = {0.0, 0.0};
        double DMU2x1N[] = {0.0, 0.0};
        double DMU2x2N[] = new double[2];
        double DMU2TN[] = new double[2];

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();
            phase0.setT(T_local);
            phase0.setX(x1_local);
            MU1N = phase0.calGAB();
            DMU1x1N = phase0.calDMUx();
            DMU1TN = phase0.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x2_local);
            MU2N = phase1.calGAB();
            DMU2x2N = phase1.calDMUx();
            DMU2TN = phase1.calDMUT();

            fvec[0] = -(MU1N[0] - MU2N[0]);
            fvec[1] = -(MU1N[1] - MU2N[1]);
            fvec[2] = -(MU0N[0] - MU2N[0]);
            fvec[3] = -(MU0N[1] - MU2N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU2x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU2x1N[0];
            fjac[0][2] = DMU1x2N[0] - DMU2x2N[0];
            fjac[0][3] = DMU1TN[0] - DMU2TN[0];
            fjac[1][0] = DMU1x0N[1] - DMU2x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU2x1N[1];
            fjac[1][2] = DMU1x2N[1] - DMU2x2N[1];
            fjac[1][3] = DMU1TN[1] - DMU2TN[1];
            fjac[2][0] = DMU0x0N[0] - DMU2x0N[0];
            fjac[2][1] = DMU0x1N[0] - DMU2x1N[0];
            fjac[2][2] = DMU0x2N[0] - DMU2x2N[0];
            fjac[2][3] = DMU0TN[0] - DMU2TN[0];
            fjac[3][0] = DMU0x0N[1] - DMU2x0N[1];
            fjac[3][1] = DMU0x1N[1] - DMU2x1N[1];
            fjac[3][2] = DMU0x2N[1] - DMU2x2N[1];
            fjac[3][3] = DMU0TN[1] - DMU2TN[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCX31() executed with errf:" + df.format(errf), 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double x0Trial = x0_local + delX[0];
            double stpmax0 = Utils.stpmaX(x0_local, x0Trial);
            double x1Trial = x1_local + delX[1];
            double stpmax1 = Utils.stpmaX(x1_local, x1Trial);
            double x2Trial = x2_local + delX[2];
            double stpmax2 = Utils.stpmaX(x2_local, x2Trial);
            double stpmax = Math.min(Math.min(stpmax0, stpmax1), stpmax2);
            T_local = T_local + stpmax * delX[3];
            x0_local = x0_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            x2_local = x2_local + stpmax * delX[2];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 2);
        }
        modDataIn[0] = T_local;//Temperature
        modDataIn[1] = x0_local;//Composition of alfa phase
        modDataIn[2] = x1_local;//Composition of beta phase
        modDataIn[3] = x2_local;//Composition of gamma phase

        //dyda calculations        
        phase0.setT(T_local);
        phase0.setX(x0_local);
        double[][] DMU0e0N = phase0.calDMUe();
        double[][] DMU0e1N = phase0.calDMUe();//to be checked
        double[][] DMU0e2N = new double[2][phase1.getNP()];//to be checked

        phase0.setT(T_local);
        phase0.setX(x1_local);
        double[][] DMU1e0N = phase0.calDMUe();//to be checked
        double[][] DMU1e1N = phase0.calDMUe();
        double[][] DMU1e2N = new double[2][phase1.getNP()];//to be checked
        phase1.setT(T_local);
        phase1.setX(x2_local);
        double[][] DMU2e0N = new double[2][phase0.getNP()];//to be checked
        double[][] DMU2e1N = new double[2][phase0.getNP()];//to be checked
        double[][] DMU2e2N = phase1.calDMUe();

        Matrix A = new Matrix(fjac);
        LUDecomposition LD = new LUDecomposition(A);
        double[] p = new double[4];
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            p[0] = -(DMU1e0N[0][icf0] - DMU2e0N[0][icf0]);
            p[1] = -(DMU1e0N[1][icf0] - DMU2e0N[1][icf0]);
            p[2] = -(DMU0e0N[0][icf0] - DMU2e0N[0][icf0]);
            p[3] = -(DMU0e0N[1][icf0] - DMU2e0N[1][icf0]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[icf0] = X.getArray()[3][0];
        }

        for (int icf2 = 0; icf2 < (phase1.getNP()); icf2++) {
            p[0] = -(DMU1e2N[0][icf2] - DMU2e2N[0][icf2]);
            p[1] = -(DMU1e2N[1][icf2] - DMU2e2N[1][icf2]);
            p[2] = -(DMU0e2N[0][icf2] - DMU2e2N[0][icf2]);
            p[3] = -(DMU0e2N[1][icf2] - DMU2e2N[1][icf2]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[(phase0.getNP()) + icf2] = X.getArray()[3][0];
        }
    }

    public void calCX3(double[] modDataIn) throws IOException {//vj-2012-03-15
        Print.f("Model:calCX3() method called", 7);
        //Initialization
        double T_local = modDataIn[0];//Temperature
        double x0_local = modDataIn[1];//Composition of alfa phase
        double x1_local = modDataIn[2];//Composition of beta phase
        double x2_local = modDataIn[3];//Composition of gamma phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-08;
        double errf = 0;
        int n = 4;
        double fvec[] = new double[4];
        double fjac[][] = new double[4][4];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double MU2N[] = new double[2];//MUA, MUB of gama phase
        double DMU0x0N[] = new double[2];
        double DMU0x1N[] = {0.0, 0.0};
        double DMU0x2N[] = {0.0, 0.0};
        double DMU0TN[] = new double[2];
        double DMU1x0N[] = {0.0, 0.0};
        double DMU1x1N[] = new double[2];
        double DMU1x2N[] = {0.0, 0.0};
        double DMU1TN[] = new double[2];
        double DMU2x0N[] = {0.0, 0.0};
        double DMU2x1N[] = {0.0, 0.0};
        double DMU2x2N[] = new double[2];
        double DMU2TN[] = new double[2];

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x1_local);
            MU1N = phase1.calGAB();
            DMU1x1N = phase1.calDMUx();
            DMU1TN = phase1.calDMUT();
            phase2.setT(T_local);
            phase2.setX(x2_local);
            MU2N = phase2.calGAB();
            DMU2x2N = phase2.calDMUx();
            DMU2TN = phase2.calDMUT();

            fvec[0] = -(MU1N[0] - MU2N[0]);
            fvec[1] = -(MU1N[1] - MU2N[1]);
            fvec[2] = -(MU0N[0] - MU2N[0]);
            fvec[3] = -(MU0N[1] - MU2N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU2x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU2x1N[0];
            fjac[0][2] = DMU1x2N[0] - DMU2x2N[0];
            fjac[0][3] = DMU1TN[0] - DMU2TN[0];
            fjac[1][0] = DMU1x0N[1] - DMU2x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU2x1N[1];
            fjac[1][2] = DMU1x2N[1] - DMU2x2N[1];
            fjac[1][3] = DMU1TN[1] - DMU2TN[1];
            fjac[2][0] = DMU0x0N[0] - DMU2x0N[0];
            fjac[2][1] = DMU0x1N[0] - DMU2x1N[0];
            fjac[2][2] = DMU0x2N[0] - DMU2x2N[0];
            fjac[2][3] = DMU0TN[0] - DMU2TN[0];
            fjac[3][0] = DMU0x0N[1] - DMU2x0N[1];
            fjac[3][1] = DMU0x1N[1] - DMU2x1N[1];
            fjac[3][2] = DMU0x2N[1] - DMU2x2N[1];
            fjac[3][3] = DMU0TN[1] - DMU2TN[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Model:calCX31() ended with T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ",errf:" + df.format(errf) + ", MUA:" + MU0N[0] + ", MUB:" + MU0N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double x0Trial = x0_local + delX[0];
            double stpmax0 = Utils.stpmaX(x0_local, x0Trial);
            double x1Trial = x1_local + delX[1];
            double stpmax1 = Utils.stpmaX(x1_local, x1Trial);
            double x2Trial = x2_local + delX[2];
            double stpmax2 = Utils.stpmaX(x2_local, x2Trial);
            double stpmax = Math.min(Math.min(stpmax0, stpmax1), stpmax2);
            T_local = T_local + stpmax * delX[3];
            x0_local = x0_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            x2_local = x2_local + stpmax * delX[2];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 3);
        }
        if (counter == ntrial) {
            Print.f("!Methods.calCX3() not conerged and ended with T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ",errf:" + df.format(errf) + ", MUA:" + MU0N[0] + ", MUB:" + MU0N[1], 2);
        }
        modDataIn[0] = T_local;//Temperature
        modDataIn[1] = x0_local;//Composition of alfa phase
        modDataIn[2] = x1_local;//Composition of beta phase
        modDataIn[3] = x2_local;//Composition of gamma phase
    }

    /*
        All three phases are distinct 
     */
    public void calCX3(double[] modDataIn, double[] dyda_in) throws IOException {//vj-2012-03-15
        Print.f("Model:calCX3() method called", 7);
        //Initialization
        double T_local = modDataIn[0];//Temperature
        double x0_local = modDataIn[1];//Composition of alfa phase
        double x1_local = modDataIn[2];//Composition of beta phase
        double x2_local = modDataIn[3];//Composition of gamma phase
        //calculation
        int ntrial = 20;
        int counter;
        double tolf = 1E-08;
        double errf = 0;
        int n = 4;
        double fvec[] = new double[4];
        double fjac[][] = new double[4][4];
        double MU0N[] = new double[2];//{MUA, MUB} of alfa phase
        double MU1N[] = new double[2];//MUA, MUB of beta phase
        double MU2N[] = new double[2];//MUA, MUB of gama phase
        double DMU0x0N[] = new double[2];
        double DMU0x1N[] = {0.0, 0.0};
        double DMU0x2N[] = {0.0, 0.0};
        double DMU0TN[] = new double[2];
        double DMU1x0N[] = {0.0, 0.0};
        double DMU1x1N[] = new double[2];
        double DMU1x2N[] = {0.0, 0.0};
        double DMU1TN[] = new double[2];
        double DMU2x0N[] = {0.0, 0.0};
        double DMU2x1N[] = {0.0, 0.0};
        double DMU2x2N[] = new double[2];
        double DMU2TN[] = new double[2];

        for (counter = 0; counter < ntrial; counter++) {
            phase0.setT(T_local);
            phase0.setX(x0_local);
            MU0N = phase0.calGAB();
            DMU0x0N = phase0.calDMUx();
            DMU0TN = phase0.calDMUT();
            phase1.setT(T_local);
            phase1.setX(x1_local);
            MU1N = phase1.calGAB();
            DMU1x1N = phase1.calDMUx();
            DMU1TN = phase1.calDMUT();
            phase2.setT(T_local);
            phase2.setX(x2_local);
            MU2N = phase2.calGAB();
            DMU2x2N = phase2.calDMUx();
            DMU2TN = phase2.calDMUT();

            fvec[0] = -(MU1N[0] - MU2N[0]);
            fvec[1] = -(MU1N[1] - MU2N[1]);
            fvec[2] = -(MU0N[0] - MU2N[0]);
            fvec[3] = -(MU0N[1] - MU2N[1]);
            fjac[0][0] = DMU1x0N[0] - DMU2x0N[0];
            fjac[0][1] = DMU1x1N[0] - DMU2x1N[0];
            fjac[0][2] = DMU1x2N[0] - DMU2x2N[0];
            fjac[0][3] = DMU1TN[0] - DMU2TN[0];
            fjac[1][0] = DMU1x0N[1] - DMU2x0N[1];
            fjac[1][1] = DMU1x1N[1] - DMU2x1N[1];
            fjac[1][2] = DMU1x2N[1] - DMU2x2N[1];
            fjac[1][3] = DMU1TN[1] - DMU2TN[1];
            fjac[2][0] = DMU0x0N[0] - DMU2x0N[0];
            fjac[2][1] = DMU0x1N[0] - DMU2x1N[0];
            fjac[2][2] = DMU0x2N[0] - DMU2x2N[0];
            fjac[2][3] = DMU0TN[0] - DMU2TN[0];
            fjac[3][0] = DMU0x0N[1] - DMU2x0N[1];
            fjac[3][1] = DMU0x1N[1] - DMU2x1N[1];
            fjac[3][2] = DMU0x2N[1] - DMU2x2N[1];
            fjac[3][3] = DMU0TN[1] - DMU2TN[1];

            errf = 0;
            for (int i = 0; i < n; i++) {
                errf = errf + Math.abs(fvec[i]);
            }
            if (errf <= tolf) {
                Print.f("Methods.calCX3() ended with T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ",errf:" + df.format(errf) + ", MUA:" + MU0N[0] + ", MUB:" + MU0N[1], 2);
                break;
            }
            double[] delX = Mat.LDsolve(fjac, fvec);
            double x0Trial = x0_local + delX[0];
            double stpmax0 = Utils.stpmaX(x0_local, x0Trial);
            double x1Trial = x1_local + delX[1];
            double stpmax1 = Utils.stpmaX(x1_local, x1Trial);
            double x2Trial = x2_local + delX[2];
            double stpmax2 = Utils.stpmaX(x2_local, x2Trial);
            double stpmax = Math.min(Math.min(stpmax0, stpmax1), stpmax2);
            T_local = T_local + stpmax * delX[3];
            x0_local = x0_local + stpmax * delX[0];
            x1_local = x1_local + stpmax * delX[1];
            x2_local = x2_local + stpmax * delX[2];
            Print.f("T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ", stpmax:" + stpmax + ", errf:" + df.format(errf), 3);
        }
        if (counter == ntrial) {
            Print.f("!Methods.calCX3() not conerged and ended with T_local:" + T_local + ", x0_local:" + df.format(x0_local) + ", x1_local:" + df.format(x1_local) + ", x2_local:" + x2_local + ",errf:" + df.format(errf) + ", MUA:" + MU0N[0] + ", MUB:" + MU0N[1], 2);
        }
        modDataIn[0] = T_local;//Temperature
        modDataIn[1] = x0_local;//Composition of alfa phase
        modDataIn[2] = x1_local;//Composition of beta phase
        modDataIn[3] = x2_local;//Composition of gamma phase

        //dyda calculations        
        phase0.setT(T_local);
        phase0.setX(x0_local);
        double[][] DMU0e0N = phase0.calDMUe();
        double[][] DMU0e1N = new double[2][phase1.getNP()];//to be checked
        double[][] DMU0e2N = new double[2][phase2.getNP()];//to be checked
        phase1.setT(T_local);
        phase1.setX(x1_local);
        double[][] DMU1e0N = new double[2][phase0.getNP()];//to be checked
        double[][] DMU1e1N = phase1.calDMUe();
        double[][] DMU1e2N = new double[2][phase2.getNP()];//to be checked
        phase2.setT(T_local);
        phase2.setX(x2_local);
        double[][] DMU2e0N = new double[2][phase0.getNP()];//to be checked
        double[][] DMU2e1N = new double[2][phase1.getNP()];//to be checked
        double[][] DMU2e2N = phase2.calDMUe();

        Matrix A = new Matrix(fjac);
        LUDecomposition LD = new LUDecomposition(A);
        double[] p = new double[4];
        for (int icf0 = 0; icf0 < (phase0.getNP()); icf0++) {
            p[0] = -(DMU1e0N[0][icf0] - DMU2e0N[0][icf0]);
            p[1] = -(DMU1e0N[1][icf0] - DMU2e0N[1][icf0]);
            p[2] = -(DMU0e0N[0][icf0] - DMU2e0N[0][icf0]);
            p[3] = -(DMU0e0N[1][icf0] - DMU2e0N[1][icf0]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[icf0] = X.getArray()[3][0];
        }
        for (int icf1 = 0; icf1 < (phase1.getNP()); icf1++) {
            p[0] = -(DMU1e1N[0][icf1] - DMU2e1N[0][icf1]);
            p[1] = -(DMU1e1N[1][icf1] - DMU2e1N[1][icf1]);
            p[2] = -(DMU0e1N[0][icf1] - DMU2e1N[0][icf1]);
            p[3] = -(DMU0e1N[1][icf1] - DMU2e1N[1][icf1]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[(phase0.getNP()) + icf1] = X.getArray()[3][0];
        }
        for (int icf2 = 0; icf2 < (phase2.getNP()); icf2++) {
            p[0] = -(DMU1e2N[0][icf2] - DMU2e2N[0][icf2]);
            p[1] = -(DMU1e2N[1][icf2] - DMU2e2N[1][icf2]);
            p[2] = -(DMU0e2N[0][icf2] - DMU2e2N[0][icf2]);
            p[3] = -(DMU0e2N[1][icf2] - DMU2e2N[1][icf2]);
            Matrix B = new Matrix(p);
            Matrix X = LD.solve(B);
            dyda_in[(phase0.getNP()) + (phase1.getNP()) + icf2] = X.getArray()[3][0];
        }
    }

    private void setPhase(ExptDatum datum) throws IOException {
        Print.f("Mathods.setPhase method called", 7);
        //String pType;
        //String pModel;//vj-2013-05-19
        //String pInstance;//vj-2013-05-19
        String pid[][] = datum.getPid();//vj-2013-03-23-reading phaseList ids
        PHASEBINCE[] phaseList = {null, null, null};//Creating an array of three phases
        int ip = 0;//phase index
        while ((ip < 3) && (pid[ip][0] != null)) {
            String pType = pid[ip][0];
            String pModel = pid[ip][1];//vj-2013-05-19
            String pInstance = pid[ip][2];//vj-2013-05-19
            //Print.f("PhaseData.genPhase() method called with pType:" + pType + " and pModel:" + pModel + " and pInstance:" + pInstance, 1);
            phaseList[ip] = phasedata.genPhase(pid[ip]);
            Print.f("Phase", phaseList[ip].getPhaseTag(), 7);
            ip = ip + 1;
        }
        phase0 = phaseList[0];
        phase1 = phaseList[1];
        phase2 = phaseList[2];
        Print.f("Mathods.setPhase method ended", 7);
    }

}
