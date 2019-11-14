/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import utils.io.Print;
import java.io.IOException;
import java.text.DecimalFormat;

/**
 * @author mani 13-02-2013
 */
public class ExptDatum {
    //Experimental data

    private double dataIndex; // Data id num
    private String dataType = null;// eg. CXXA-A2-1-L-1
    private String method = null;//for storing calculation method/boundary type e.g. MGT1, MGX1...
    private String pid[][] = null;//for storing phases for each data, maximum three phases with its name and index
    private double y;
    private double ymod;
    private double sig;
    private double wt;
    private double x;
    private double x1;
    private double x2;
    private String ref;//vj-2012-03-23
    private final String cname = "ExptDatum";

    public ExptDatum() throws IOException {
        Print.f(cname + ".constructor called", 6);
        dataIndex = 0;
        dataType = new String();
        method = new String();
        ref = new String();
        pid = new String[3][3];
        Print.f(cname + ".constructor closed", 6);
    }

    public String getBList() {
        return method;
    }

    public double getdIndex() {
        return dataIndex;
    }

    public String getDataType() {
        return dataType;
    }

    public String getPid(int i, int j) {
        return pid[i][j];
    }

    public String[][] getPid() {//vj-2013-04-04
        return pid;
    }

    public double getY() {
        return y;
    }

    public double getYmod() {
        return ymod;
    }

    public double getSig() {
        return sig;
    }

    public double getWt() {
        return wt;
    }

    public double getX() {
        return x;
    }

    public double getX1() {
        return x1;
    }

    public double getX2() {
        return x2;
    }

    public String getRef() {
        return ref;
    }

    //functions for modifying the ExptDatum
    public void setDataIndex(double in) {//vj-2012-03-24
        this.dataIndex = in;
    }

    public void setDataType(String in) {//vj-2012-03-24
        this.dataType = in;
    }

    public void setBList(String in) {//vj-2012-03-24
        this.method = in;
    }

    public void setPid(String[][] in) {//vj-2012-03-24
        for (int i = 0; i < 3; i++) {
            System.arraycopy(in[i], 0, this.pid[i], 0, 3);
        }
    }

    public void setY(double in) {//vj-2012-03-24
        this.y = in;
    }

    public void setYmod(double in) {
        this.ymod = in;
    }

    public void setSig(double in) {//vj-2012-03-24
        this.sig = in;
    }

    public void setWt(double in) {//vj-2012-03-24
        this.wt = in;
    }

    public void setX(double in) {
        this.x = in;
    }

    public void setX1(double in) {
        this.x1 = in;
    }

    public void setX2(double in) {
        this.x2 = in;
    }

    public void setRef(String in) {//vj-2012-03-24
        this.ref = in;
    }

    public void print() throws IOException {//vj-2013-03-24-Added
        DecimalFormat df = new DecimalFormat("0000.000");
        Print.f((int) dataIndex + "\t" + method + "\t" + pid[0][0] + "-" + pid[0][1] + "-" + pid[0][2] + "\t" + pid[1][0] + "-" + pid[1][1] + "-" + pid[1][2] + "\t" + pid[2][0] + "-" + pid[2][1] + "-" + pid[2][2] + "\t" + df.format(y) + "\t" + df.format(ymod) + "\t" + df.format(sig) + "\t" + df.format(wt) + "\t" + df.format(x) + "\t" + df.format(x1) + "\t" + df.format(x2) + "\t" + ref, 1);
    }

    public void print(int printLevel) throws IOException {//vj-2013-03-24-Added
        DecimalFormat df = new DecimalFormat("0000.000");
        Print.f((int) dataIndex + "\t" + method + "\t" + pid[0][0] + "-" + pid[0][1] + "-" + pid[0][2] + "\t" + pid[1][0] + "-" + pid[1][1] + "-" + pid[1][2] + "\t" + pid[2][0] + "-" + pid[2][1] + "-" + pid[2][2] + "\t" + df.format(y) + "\t" + df.format(ymod) + "\t" + df.format(sig) + "\t" + df.format(wt) + "\t" + df.format(x) + "\t" + df.format(x1) + "\t" + df.format(x2) + "\t" + ref, printLevel);
    }
}
