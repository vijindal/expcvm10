/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import utils.io.DataReader;
import utils.io.Print;
import utils.io.Utils;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 *
 * @author admin
 */
public class ExptData {
    //Experimental data

    private String dataInputFileName = null;//vj-2012-03-25
    private int ndat = 0;//vj-2012-03-25-No of Data Points
    private double dataIndex[] = null; // SU: added 20.03.2012
    private String dataType[] = null;//vj-2012-03-25
    private String[] methodType = null;//for storing calculation methodType/boundary type e.g. MGT1, MGX1...
    private String pList[][][] = null;//for storing phases for each data, maximum three phases with its name and index
    private double y[] = null;
    private double ymod[] = null;
    private double sig[] = null;
    private double wt[] = null;
    private double x[];
    private double x1[] = null;
    private double x2[] = null;
    private String ref[] = null;//vj-2012-03-23
    private final String cname = "ExptData";//vj-2013-05-18

    public ExptData() throws IOException {
        Print.f(cname + ".constructor called", 6);
        Print.f(cname + ".constructor closed", 6);
    }

    public ExptData(String dataInputFileName) throws IOException {
        Print.f(cname + ".constructor called", 6);
        this.dataInputFileName = dataInputFileName;
        this.ndat = getNumData(dataInputFileName);
        dataIndex = new double[ndat];
        methodType = new String[ndat];
        pList = new String[ndat][3][3];//vj-2013-05-19-Increased one dimension for storing model also.
        dataType = new String[ndat];
        y = new double[ndat];
        ymod = new double[ndat];
        sig = new double[ndat];
        wt = new double[ndat];
        x = new double[ndat];
        x1 = new double[ndat];
        x2 = new double[ndat];
        ref = new String[ndat];
        Print.f(cname + ".constructor closed", 6);
    }

    public void init(String dataFileNameIn, int ndatIn) throws IOException {
        Print.f(cname + ".init method called", 7);
        dataInputFileName = dataFileNameIn;
        ndat = ndatIn;
        methodType = new String[ndat];
        pList = new String[ndat][3][3];//vj-2013-05-19-Increased one dimension for storing model also.
        dataIndex = new double[ndat];
        for (int i = 0; i < ndat; i++) {
            dataIndex[i] = i;
        }
        dataType = new String[ndat];
        y = new double[ndat];
        ymod = new double[ndat];
        sig = new double[ndat];
        wt = new double[ndat];
        x = new double[ndat];
        x1 = new double[ndat];
        x2 = new double[ndat];
        ref = new String[ndat];
        Print.f(cname + ".init method closed", 7);
    }

    public void init(String dataFileNameIn, ExptData expdataIn) throws IOException {//vj-2013-06-02
        Print.f(cname + ".init method called", 7);
        dataInputFileName = dataFileNameIn;
        ndat = expdataIn.getNdat();
        methodType = new String[ndat];
        pList = new String[ndat][3][3];//vj-2013-05-19-Increased one dimension for storing model also.
        dataIndex = new double[ndat];
        for (int i = 0; i < ndat; i++) {
            dataIndex[i] = expdataIn.getDataIndex(i);
        }
        dataType = new String[ndat];
        y = new double[ndat];
        ymod = new double[ndat];
        sig = new double[ndat];
        wt = new double[ndat];
        x = new double[ndat];
        x1 = new double[ndat];
        x2 = new double[ndat];
        ref = new String[ndat];
        Print.f(cname + ".init method closed", 7);
    }

    //Setter Methods
    public void setdataIndex(double xIn, int i) {
        dataIndex[i] = xIn;
    }

    public void setDataType(String xIn, int i) {
        dataType[i] = xIn;
    }

    public void setBList(String xIn, int i) {
        methodType[i] = xIn;
    }

    public void setPid(String xIn, int i, int j, int k) {
        pList[i][j][k] = xIn;
    }

    public void setY(double xIn, int i) {
        y[i] = xIn;
    }

    public void setYmod(double xIn, int i) {
        ymod[i] = xIn;
    }

    public void setSig(double xIn, int i) {
        sig[i] = xIn;
    }

    public void setWt(double xIn, int i) {
        wt[i] = xIn;
    }

    public void setX(double xIn, int i) {
        x[i] = xIn;
    }

    public void setX1(double xIn, int i) {
        x1[i] = xIn;
    }

    public void setX2(double xIn, int i) {
        x2[i] = xIn;
    }

    public void setRefList(String xIn, int i) {
        ref[i] = xIn;
    }

    public void setVoid() throws IOException {//vj-2013-05-18
        Print.f(cname + ".setVoid() method called", 7);
        dataInputFileName = null;
        ndat = 0;
        methodType = null;
        pList = null;
        dataIndex = null;
        dataType = null;
        y = null;
        ymod = null;
        sig = null;
        wt = null;
        x = null;
        x1 = null;
        x2 = null;
        ref = null;
        Print.f(cname + ".setVoid() method closed", 7);
    }
    //Getter Methods

    public String getDataFileName() {
        return (dataInputFileName);
    }

    public int getNdat() {
        return (ndat);
    }

    public double getDataIndex(int i) {
        return (dataIndex[i]);
    }

    public String getDataType(int i) {
        return (dataType[i]);
    }

    public String getBList(int i) {
        return (methodType[i]);
    }

    public String[][][] getPid() {//vj-2013-03-23
        return (pList);
    }

    public String[][] getPid(int i) {//vj-2013-03-23
        return (pList[i]);
    }

    public String getPid(int i, int j, int k) {
        return (pList[i][j][k]);
    }

    public double getY(int i) {
        return (y[i]);
    }

    public double[] getY() {//vj-2013-03-24
        return (y);
    }

    public double getYmod(int i) {
        return (ymod[i]);
    }

    public double getSig(int i) {
        return (sig[i]);
    }

    public double[] getSig() {//vj-2013-03-24
        return (sig);
    }

    public double getWt(int i) {
        return (wt[i]);
    }

    public double[] getWt() {//vj-2013-03-31
        return (wt);
    }

    public double getX(int i) {
        return (x[i]);
    }

    public double[] getX() {//vj-2013-03-24
        return (x);
    }

    public double getX1(int i) {
        return (x1[i]);
    }

    public double getX2(int i) {
        return (x2[i]);
    }

    public String getRef(int i) {
        return (ref[i]);
    }

    public int nph(String pid) {//vj-2013-03-23-returns number of phases involved for the given thermodynamic/phase diagram calculation
        return (0);
    }

    public ExptDatum getExptDatum(int idat) throws IOException {
        ExptDatum exptdatum = new ExptDatum();
        exptdatum.setDataIndex(dataIndex[idat]);
        exptdatum.setDataType(dataType[idat]);
        exptdatum.setBList(methodType[idat]);
        exptdatum.setPid(pList[idat]);
        exptdatum.setY(y[idat]);
        exptdatum.setYmod(ymod[idat]);
        exptdatum.setSig(sig[idat]);
        exptdatum.setWt(wt[idat]);
        exptdatum.setX(x[idat]);
        exptdatum.setX1(x1[idat]);
        exptdatum.setX2(x2[idat]);
        exptdatum.setRef(ref[idat]);
        return (exptdatum);
    }

    public void setExptDatum(ExptDatum exptdatum) throws IOException {
        Print.f(cname + ".setExptDatum method called", 7);
        for (int i = 0; i < ndat; i++) {
            if (exptdatum.getdIndex() == dataIndex[i]) {
                this.methodType[i] = exptdatum.getBList();
                this.pList[i] = exptdatum.getPid();
                this.y[i] = exptdatum.getY();
                this.ymod[i] = exptdatum.getYmod();
                this.sig[i] = exptdatum.getSig();
                this.wt[i] = exptdatum.getWt();
                this.x[i] = exptdatum.getX();
                this.x1[i] = exptdatum.getX1();
                this.x2[i] = exptdatum.getX2();
                this.ref[i] = exptdatum.getRef();
            }
        }
        Print.f(cname + ".setExptDatum method ended", 7);
    }

    public void check(PhaseData phasedata) throws IOException {
        String[] phaseIndex = new String[3];
        if (phasedata == null) {
            Print.f(phasedata + " object not initialized", 0);
        } else {
            //countPhases();
        }
    }

    private void countPhases() throws IOException {
        List<String[]> p = new ArrayList<String[]>();
        for (int i = 0; i < ndat; i++) {
            for (int j = 0; j < pList[i].length; j++) {
                Print.f("pList[" + i + "][item]", pList[i][j], 1);
                System.out.println(p.size());
                System.out.println((p.contains(pList[i][j])));
                if (!(p.contains(pList[i][j]))) {
                    p.add(pList[i][j]);
                };
            }
        }
        System.out.println(Arrays.toString(p.get(0)));
        System.out.println(p.size());
    }

    public void print() throws IOException {//vj-2012-03-23
        DecimalFormat df = new DecimalFormat("#0.000000");
        Print.f(cname + ".print method called", 7);
        Utils.drawLine();
        int logLevel = 0;
        Print.f("Data File:" + dataInputFileName, logLevel);
        Print.f("No of Data Points:" + ndat, logLevel);
        for (int idata = 0; idata < ndat; idata++) {
            Print.f((int) dataIndex[idata] + "\t" + methodType[idata] + "\t" + pList[idata][0][0] + "-" + pList[idata][0][1] + "-" + pList[idata][0][2] + "\t" + pList[idata][1][0] + "-" + pList[idata][1][1] + "-" + pList[idata][1][2] + "\t" + pList[idata][2][0] + "-" + pList[idata][2][1] + "-" + pList[idata][2][2] + "\t" + df.format(y[idata]) + "\t" + df.format(ymod[idata]) + "\t" + df.format(sig[idata]) + "\t" + df.format(wt[idata]) + "\t" + df.format(x[idata]) + "\t" + df.format(x1[idata]) + "\t" + df.format(x2[idata]) + "\t" + ref[idata], logLevel);
        }
        Utils.drawLine();
        Print.f(cname + ".print method executed", 7);
    }

    /*
    returns number of data in an input file (such as number of phases in phasesData file and  number of experimental data points in exptData file
     */
    private int getNumData(String infile) throws FileNotFoundException, IOException {
        int ndat;
        //Print.f(cname + ".getNumData called with Input File:" + infile, 7);
        LineNumberReader lineCounter = new LineNumberReader(new InputStreamReader(new FileInputStream(infile)));
        String nextLine = null;
        int commentLines = 0, itr = 0;
        try {
            while ((nextLine = lineCounter.readLine()) != null) {

                if (nextLine.trim().equalsIgnoreCase("#************************STOP#")) {
                    commentLines++;
                    break;
                } else if (nextLine.trim().equalsIgnoreCase("#************************START#")) {
                    itr++;
                    commentLines++;
                } else if ((itr == 0) || (itr == 1 && (nextLine.trim().equalsIgnoreCase("") || nextLine.startsWith("//")))) {
                    commentLines++;
                }
            }
        } catch (IOException done) {
            System.err.println("Exception in Reading ndata in getNData()");
        }
        ndat = lineCounter.getLineNumber() - commentLines;
        //Print.f(cname + ".getNumData ended with ndat:" + ndat, 7);
        return (ndat);
    }// Close getNumData() Method

    public void readExptDataFile() throws IOException {
        //VJ-2013-03-22
        Print.f(cname + ".readExptDataFile() Called with Input File : " + dataInputFileName, 7);
        //int nDataPoints = getNumData(dataInputFileName);
        //Prnt.f("No. of data points (ndata)= " + nDataPoints, 4);
        // Local arrays to store Data from file
        double[][] arrayIn = new double[8][ndat];
        String[] dataType = new String[ndat];
        String[] reference = new String[ndat];
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(dataInputFileName);
        } catch (FileNotFoundException e) {
            System.err.println("File Not Found");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Usage: ShowFile File");
        }
        DataInputStream in = new DataInputStream(fin);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String str;
        Boolean isStartReading = false;
        Boolean isBlank = true;
        int index = 0;
        while ((str = br.readLine()) != null) {
            if (str.trim().equalsIgnoreCase("#************************STOP#")) {
                break;
            } else if (str.trim().equalsIgnoreCase("#************************START#")) {
                isStartReading = true;
                str = br.readLine();
            }
            if (str.startsWith("//")) {
                continue;
            }
            if (isStartReading) {
                if (str.trim().equalsIgnoreCase("")) {
                    if (isBlank) {
                        Print.f("Input File for expData has blank Spaces and Tabs !", 0);
                        isBlank = false;
                    }
                } else {
                    StringTokenizer st = new StringTokenizer(str);
                    arrayIn[0][index] = Double.parseDouble(st.nextToken()); //Index
                    dataType[index] = st.nextToken(); //data Type
                    arrayIn[1][index] = Double.parseDouble(st.nextToken()); //y
                    arrayIn[2][index] = Double.parseDouble(st.nextToken()); //ymod
                    arrayIn[3][index] = Double.parseDouble(st.nextToken()); //sigy
                    arrayIn[4][index] = Double.parseDouble(st.nextToken()); //wt
                    arrayIn[5][index] = Double.parseDouble(st.nextToken()); //x
                    arrayIn[6][index] = Double.parseDouble(st.nextToken()); //x1
                    arrayIn[7][index] = Double.parseDouble(st.nextToken()); //x2
                    reference[index] = st.nextToken(); //reference
                    index++;
                } // else Closed
            } // if isStartReading condition closed
        } // while loop for whole file
        if (fin != null) {
            fin.close();
        }
        //dataReader.exptdata.init(dataReader.dataInputFileName, nDataPoints); //vj-2012-04-18:Initialize exptdatas array with size nUsedDataPoints
        for (int idata = 0; idata < ndat; idata++) {
            setdataIndex(arrayIn[0][idata], idata);
            setDataType(dataType[idata], idata);
            setY(arrayIn[1][idata], idata);
            setYmod(arrayIn[2][idata], idata);
            setSig(arrayIn[3][idata], idata);
            setWt(arrayIn[4][idata], idata);
            setX(arrayIn[5][idata], idata);
            setX1(arrayIn[6][idata], idata);
            setX2(arrayIn[7][idata], idata);
            setRefList(reference[idata], idata);
        }
        //exptdatas.printGlobalData();
        patternList(dataType, ndat); // It sets bList & pid on case basis.
        Print.f(cname + ".readExptDataFile() ended", 7);
    } //  Method  Closed

    private void patternList(String[] dataType, int nDataPoints) throws IOException {//VJ-2013-03-22-Added
        Print.f(cname + ".patternList() method called", 7);
        String delims = "-", temp[];
        for (int i = 0; i < nDataPoints; i++) {
            temp = dataType[i].split(delims);
            //Print.f(temp, "temp", 1);
            //Print.f(temp.length, "temp.length", 1);
            setBList(temp[0], i);

            switch (temp.length) {
                case 4:
                    setPid(temp[1], i, 0, 0);
                    setPid(temp[2], i, 0, 1);
                    setPid(temp[3], i, 0, 2);
                    break;
                case 7:
                    setPid(temp[1], i, 0, 0);
                    setPid(temp[2], i, 0, 1);
                    setPid(temp[3], i, 0, 2);
                    setPid(temp[4], i, 1, 0);
                    setPid(temp[5], i, 1, 1);
                    setPid(temp[6], i, 1, 2);
                    break;
                case 10:
                    setPid(temp[1], i, 0, 0);
                    setPid(temp[2], i, 0, 1);
                    setPid(temp[3], i, 0, 2);
                    setPid(temp[4], i, 1, 0);
                    setPid(temp[5], i, 1, 1);
                    setPid(temp[6], i, 1, 2);
                    setPid(temp[7], i, 2, 0);
                    setPid(temp[8], i, 2, 1);
                    setPid(temp[9], i, 2, 2);
                    break;
                default:
                    Print.f(cname + ".patternList: Phase description in expdata input is incorrect", 0);
                    System.exit(-1);
            }
        }// Closed for i loop
        Print.f(cname + ".patternList() Closed", 7);
    }// Closed  method
}
