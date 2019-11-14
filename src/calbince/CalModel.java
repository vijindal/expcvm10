package calbince;

import utils.io.Print;
import java.io.IOException;

public class CalModel {

    private PhaseData phasedata = null;
    private ExptData exptdata = null;
    private ExptData recordDat = null;
    private final double NLN = -1.0e10; //negative large numver
    private final String cname = "CalModel";

    public CalModel(ExptData exptdataIn, PhaseData phasedataIn, ExptData recordDatIn) throws IOException {//VJ-2013-03-23-Modified
        Print.f(cname + ".constructor method called", 6);
        this.exptdata = exptdataIn;
        this.phasedata = phasedataIn;
        this.recordDat = recordDatIn;
        Print.f(cname + ".constructor method closed", 6);
    }// Closed calModel Constructor

    public void Run() throws IOException {
        Print.f(cname + ".Run method called", 7);
        //recordDat.init(exptdata.getDataFileName(), exptdata.getNdat());
        recordDat.init(exptdata.getDataFileName(), exptdata);//vj-2013-06-02
        Methods methods = new Methods(phasedata);//vj-2013-04-04
        for (int i = 0; i < exptdata.getNdat(); i++) {
            ExptDatum dat = exptdata.getExptDatum(i);
            methods.funcsCal(dat);
            recordDat.setExptDatum(dat);
        }
        Print.f(cname + ".Run method ended", 7);
    }

    public void RunIntgQuant() throws IOException {//vj-2013-04-19
        Print.f(cname + ".RunIntgQuant method called", 7);
        recordDat.init(exptdata.getDataFileName(), 55);
        Methods methods = new Methods(phasedata);
        int iDat = 0;
        double x = 0.0;
        ExptDatum dat = new ExptDatum();
        String[][] PID = exptdata.getPid(0);
        double X1 = exptdata.getX1(0);
        String REF = exptdata.getRef(0);
        dat.setPid(PID);
        dat.setX1(X1);
        dat.setRef(REF);
        for (int i = 0; i < 11; i++) {

            dat.setDataIndex(iDat);
            dat.setBList("Gm");
            dat.setPid(exptdata.getExptDatum(0).getPid());
            dat.setX(x);

            if (i == 0 || i == 10) {
                dat.setYmod(0.0);
            } else {
                methods.funcsCal(dat);
            }
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 33);
            dat.setBList("GmE");
            dat.setPid(exptdata.getExptDatum(0).getPid());
            dat.setX(x);

            if (i == 0 || i == 10) {
                dat.setYmod(0.0);
            } else {
                dat.setYmod(dat.getYmod() - 8.314 * (dat.getX1()) * (x * Math.log(x) + (1 - x) * Math.log(1 - x)));
            }
            recordDat.setExptDatum(dat);

            iDat = iDat + 1;
            x = x + 0.1;
        }

        x = 0.0;
        for (int i = 0; i < 11; i++) {

            dat.setDataIndex(iDat);
            dat.setBList("Hm");
            dat.setPid(exptdata.getExptDatum(0).getPid());
            dat.setX(x);

            if (i == 0 || i == 10) {
                dat.setYmod(0.0);
            } else {
                methods.funcsCal(dat);
            }
            recordDat.setExptDatum(dat);
            iDat = iDat + 1;
            x = x + 0.1;
        }

        x = 0.0;
        for (int i = 0; i < 11; i++) {

            dat.setDataIndex(iDat);
            dat.setBList("Sm");
            dat.setPid(exptdata.getExptDatum(0).getPid());
            dat.setX(x);

            if (i == 0 || i == 10) {
                dat.setYmod(0.0);
            } else {
                methods.funcsCal(dat);
            }
            recordDat.setExptDatum(dat);
            dat.setDataIndex(iDat + 22);
            dat.setBList("SmE");
            dat.setPid(exptdata.getExptDatum(0).getPid());
            dat.setX(x);

            if (i == 0 || i == 10) {
                dat.setYmod(0.0);
            } else {

                dat.setYmod(dat.getYmod() + 8.314 * (x * Math.log(x) + (1 - x) * Math.log(1 - x)));
            }
            //dat.print();
            recordDat.setExptDatum(dat);
            iDat = iDat + 1;
            x = x + 0.1;
        }
        Print.f(cname + ".RunIntgQuant method ended", 7);
        //recordDat.print();
    }

    public void RunPrtlQuant() throws IOException {//vj-2013-04-19
        Print.f(cname + ".RunPrtlQuant method called", 7);
        recordDat.init(exptdata.getDataFileName(), 154);
        Methods methods = new Methods(phasedata);
        int iDat = 0;
        double x = 0.0;
        double aA, aB, GmA, GmB, SmA, SmB;
        ExptDatum dat = new ExptDatum();
        String[][] PID = exptdata.getPid(0);
        double X1 = exptdata.getX1(0);
        String REF = exptdata.getRef(0);
        dat.setPid(PID);
        dat.setX1(X1);
        dat.setRef(REF);
        for (int i = 0; i < 11; i++) {
            //Print.f("xB:" + x,0);
            dat.setDataIndex(iDat + 55);
            dat.setBList("aA");
            dat.setX(x);
            switch (i) {
                case 0:
                    dat.setYmod(1.0);
                    break;
                case 10:
                    dat.setYmod(0.0);
                    break;
                default:
                    methods.funcsCal(dat);
                    break;
            }
            aA = dat.getYmod();
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 132);
            dat.setBList("aB");
            dat.setX(x);
            switch (i) {
                case 0:
                    dat.setYmod(0.0);
                    break;
                case 10:
                    dat.setYmod(1.0);
                    break;
                default:
                    methods.funcsCal(dat);
                    break;
            }
            aB = dat.getYmod();
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 66);
            dat.setBList("aCoeffA");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(1.0);
                    break;
                case 10:
                    dat.setYmod(0.0);//vj-2013-04-20-to be fixed
                    break;
                default:
                    dat.setYmod(aA / (1 - x));
                    break;
            }
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 143);
            dat.setBList("aCoeffB");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);//to be fixed
                    break;
                case 10:
                    dat.setYmod(1.0);
                    break;
                default:
                    dat.setYmod(aB / (x));
                    break;
            }
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat);
            dat.setBList("GmA");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);
                    break;
                case 10:
                    dat.setYmod(0.0);//vj-2013-04-20-to be fixed
                    break;
                default:
                    dat.setYmod(8.314 * dat.getX1() * Math.log(aA));
                    break;
            }
            GmA = dat.getYmod();
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 77);
            dat.setBList("GmB");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(NLN);
                    break;
                case 10:
                    dat.setYmod(0.0);//vj-2013-04-20-to be fixed
                    break;
                default:
                    dat.setYmod(8.314 * dat.getX1() * Math.log(aB));
                    break;
            }
            GmB = dat.getYmod();
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 33);
            dat.setBList("GmEA");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);
                    break;
                case 10:
                    dat.setYmod(NLN);
                    break;
                default:
                    dat.setYmod(GmA - (8.314 * dat.getX1() * Math.log(1 - x)));
                    break;
            }
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 110);
            dat.setBList("GmEB");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);//to be fixed
                    break;
                case 10:
                    dat.setYmod(0);
                    break;
                default:
                    dat.setYmod(GmB - (8.314 * dat.getX1() * Math.log(x)));
                    break;
            }
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 22);
            dat.setBList("SmA");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);
                    break;
                case 10:
                    dat.setYmod(NLN);
                    break;
                default:
                    methods.funcsCal(dat);
                    break;
            }
            SmA = dat.getYmod();
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 99);
            dat.setBList("SmB");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);//to be fixed
                    break;
                case 10:
                    dat.setYmod(0.0);
                    break;
                default:
                    methods.funcsCal(dat);
                    break;
            }
            SmB = dat.getYmod();
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 11);
            dat.setBList("HmA");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);
                    break;
                case 10:
                    dat.setYmod(0.0);//to be fixed
                    break;
                default:
                    dat.setYmod(GmA + (dat.getX1() * SmA));
                    break;
            }
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 88);
            dat.setBList("HmB");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);//to be fixed
                    break;
                case 10:
                    dat.setYmod(0.0);
                    break;
                default:
                    dat.setYmod(GmB + (dat.getX1() * SmB));
                    break;
            }
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 44);
            dat.setBList("SmEA");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);
                    break;
                case 10:
                    dat.setYmod(0.0);//to be fixed
                    break;
                default:
                    dat.setYmod(SmA + (8.314 * Math.log(1 - x)));
                    break;
            }
            recordDat.setExptDatum(dat);

            dat.setDataIndex(iDat + 121);
            dat.setBList("SmEB");

            dat.setX(x);

            switch (i) {
                case 0:
                    dat.setYmod(0.0);//to be fixed
                    break;
                case 10:
                    dat.setYmod(0.0);
                    break;
                default:
                    dat.setYmod(SmB + (8.314 * Math.log(x)));
                    break;
            }
            recordDat.setExptDatum(dat);

            iDat = iDat + 1;
            x = x + 0.1;
        }
        Print.f(cname + ".RunPrtlQuant method ended", 7);
    }
}
