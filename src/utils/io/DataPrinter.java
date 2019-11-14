package utils.io;

/**
 * Prints Output Files.
 *
 * Date:
 *
 * @author Shivam
 */
import calbince.ExptData;
import calbince.PhaseData;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DataPrinter {

    public static String nl = System.getProperty("line.separator");
    public static char nt = '\t';
    public static int pr2F = 3, logLevel = 1;// 0 for nothing, 1 for console, 2 for file, 3 for both console & file
    public static boolean isPrintOutFile = false, isPrintLogFile = false;
    public static PrintfFormat fileFd = new PrintfFormat("%-10.8f");
    public static PrintfFormat fileFd63 = new PrintfFormat("%-12.8f");
    public static PrintfFormat fileFs = new PrintfFormat("%-10s");
    public static String prefix, postfix = "", newtime;
    protected static FileWriter f;
    private static DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    private static DateFormat tf = new SimpleDateFormat("HH:mm:ss");
    private ExptData exptdata = null;//vj-2013-04-04
    private PhaseData phasedata = null;//vj-2013-04-04
    private String outfile;
    private String cname = "DataPrinter";

    public DataPrinter(String filePrefix, int logLevelParamIn, boolean isPrintOutFileIn, boolean isPrintLogFileIn) throws IOException {//vj-2012-03-25
        logLevel = logLevelParamIn;
        isPrintOutFile = isPrintOutFileIn;
        isPrintLogFile = isPrintLogFileIn;
        if (isPrintOutFile) {
            df.setTimeZone(TimeZone.getTimeZone("IST"));
            tf.setTimeZone(TimeZone.getTimeZone("IST"));
            postfix = df.format(new Date()) + postfix;
            newtime = tf.format(new Date());
            prefix = filePrefix;
        }
    }

    public DataPrinter(String filePrefix, int logLevelIn) throws IOException {//vj-2013-04-04
        logLevel = logLevelIn;
        df.setTimeZone(TimeZone.getTimeZone("IST"));
        tf.setTimeZone(TimeZone.getTimeZone("IST"));
        postfix = df.format(new Date()) + postfix;
        newtime = tf.format(new Date());
        prefix = filePrefix;
    }

    public void initOutput(ExptData exptdataIn, PhaseData phasedataIn) throws IOException {//vj-2013-04-04
        Print.f(cname + ".initcal method called", 7);
        this.exptdata = exptdataIn;
        this.phasedata = phasedataIn;
        outfile = prefix + "-CalModelOut" + ".txt";
        drawLine(outfile);
        df.setTimeZone(TimeZone.getTimeZone("IST"));
        tf.setTimeZone(TimeZone.getTimeZone("IST"));
        newtime = df.format(new Date()) + nt + tf.format(new Date());
        writeln(newtime, outfile);//vj-2012-03-27-f(newtime, outfile);
        writeln(outfile);
        writeln("Input:", outfile);
        writeln("Phase Input File:" + phasedata.getPhaseFileName(), outfile);
        writeln("No of Phase(s):" + phasedata.getNumPhases(), outfile);
        fprintPhaseData();
        writeln("Data Input File:" + exptdata.getDataFileName(), outfile);
        writeln("No of Data Points:" + exptdata.getNdat(), outfile);
        fprintExptData();
        //drawLine(outfile);
        writeln(outfile);
        this.exptdata = null;
        this.phasedata = null;
        Print.f(cname + ".initcal method ended", 7);
    }// 

    public void finalOutput(ExptData exptdataIn, PhaseData phasedataIn) throws IOException {//vj-2013-04-04
        Print.f(cname + ".finalCal method called", 7);
        //exptdataIn.print();
        this.exptdata = exptdataIn;
        this.phasedata = phasedataIn;
        writeln("Output:", outfile);
        fprintExptData();
        drawLine(outfile);
        writeln(outfile);
        this.exptdata = null;
        this.phasedata = null;
        Print.f(cname + ".finalCal method ended", 7);
    }// 

    public void initOpt(ExptData exptdataIn, PhaseData phasedataIn) throws IOException {//vj-2013-04-04
        Print.f(cname + ".initOpt method called", 7);
        this.exptdata = exptdataIn;
        this.phasedata = phasedataIn;
        outfile = prefix + "-OptMrqOut" + ".txt";
        drawLine(outfile);
        df.setTimeZone(TimeZone.getTimeZone("IST"));
        tf.setTimeZone(TimeZone.getTimeZone("IST"));
        newtime = df.format(new Date()) + nt + tf.format(new Date());
        writeln(newtime, outfile);//vj-2012-03-27-f(newtime, outfile);
        writeln(outfile);
        writeln("Input:", outfile);
        writeln("Phase Input File:" + phasedata.getPhaseFileName(), outfile);
        writeln("No of Phase(s):" + phasedata.getNumPhases(), outfile);
        fprintPhaseData();
        writeln("No of Optimization Parameters:" + phasedata.getNpt(), outfile);
        writeln("Data Input File:" + exptdata.getDataFileName(), outfile);
        writeln("No of Data Points:" + exptdata.getNdat(), outfile);
        fprintExptData();
        //drawLine(outfile);
        writeln(outfile);
        this.exptdata = null;
        this.phasedata = null;
        Print.f(cname + ".initOpt method ended", 7);
    }// 

    public void outOpt(PhaseData phasedata) throws IOException {//vj-2013-05-07
        outfile = prefix + "-OptOut" + ".txt";
        writeln("#************************START#", outfile);
        for (int iphase = 0; iphase < phasedata.getNumPhases(); iphase++) {
            writeln(phasedata.getPhaseIndex(iphase) + "\t" + phasedata.getPid0(iphase) + "\t" + phasedata.getComponentA(iphase) + "\t" + phasedata.getComponentB(iphase) + "\t" + phasedata.getStdst(iphase)[3]  + "\t" + phasedata.getNp(iphase) + "\t" + phasedata.getStrM(iphase) + "\t" + phasedata.getStrIa(iphase) + "\t" + phasedata.getStrE(iphase), outfile);
        }
        writeln("#************************STOP#", outfile);
    }

    public void finalOpt(PhaseData phasedataIn) throws IOException {//vj-2013-05-07
        outfile = prefix + "-OptMrqOut" + ".txt";
        writeln(outfile);
        String fLine = "Iteration" + nt + "ChiSquare" + nt + "Lambda" + nt + "ECIs" + nt;
        String temp = "";
        int mfit = 0;
        for (int j = 0; j < phasedataIn.getNpt(); j++) {
            if (phasedataIn.getIa(j)) {
                mfit++;
            }
        }
        String str[] = {"Beta", "da"};
        for (int j = 0; j < str.length; j++) {
            for (int i = 0; i < mfit; i++) {
                temp += str[j] + (i + 1) + nt;
            }
        }
        fLine += temp;
        writeln(fLine, outfile);
        int itrFit = phasedataIn.getMaxFitItr();
        System.out.println("itrFit:" + itrFit);
        String[][] strOpt = new String[itrFit + 1][6];
        phasedataIn.getStrOpt(strOpt);
        //Print.f(strOpt, "strOpt", 0);
        writeln("#************************START#", outfile);
        for (int i = 0; i < itrFit; i++) {
            for (int j = 0; j < strOpt[i].length; j++) {
                write("  " + strOpt[i][j], outfile);
            }
            writeln(outfile);
        }
        writeln("#************************STOP#", outfile);
        drawLine(outfile);
        writeln(outfile);
    }

    private void fprintExptData() throws IOException {//vj-2012-03-23
        //exptdata.print();
        DecimalFormat dtf = new DecimalFormat("#.#####");
        Print.f(cname + ".fprintExptData() method called", 7);
        writeln("#************************START#", outfile);
        for (int idat = 0; idat < exptdata.getNdat(); idat++) {
            writeln((int) exptdata.getDataIndex(idat) + "\t" + exptdata.getBList(idat) + "\t" + exptdata.getPid(idat)[0][0] + "-" + exptdata.getPid(idat)[0][1] + "\t" + exptdata.getPid(idat)[1][0] + "-" + exptdata.getPid(idat)[1][1] + "\t" + exptdata.getPid(idat)[2][0] + "-" + exptdata.getPid(idat)[2][1] + "\t" + dtf.format(exptdata.getY(idat)) + "\t" + dtf.format(exptdata.getYmod(idat)) + "\t" + dtf.format(exptdata.getSig(idat)) + "\t" + dtf.format(exptdata.getWt(idat)) + "\t" + dtf.format(exptdata.getX(idat)) + "\t" + dtf.format(exptdata.getX1(idat)) + "\t" + dtf.format(exptdata.getX2(idat)) + "\t" + exptdata.getRef(idat), outfile);
        }
        writeln("#************************STOP#", outfile);
        Print.f(cname + ".fprintExptData() method ended", 7);
    }

    private void fprintPhaseData() throws IOException {//vj-2012-04-04
        Print.f(cname + ".fprintPhaseData() method called", 7);
        writeln("#************************START#", outfile);
        for (int iphase = 0; iphase < phasedata.getNumPhases(); iphase++) {
            writeln(phasedata.getPhaseIndex(iphase) + "\t" + phasedata.getPid0(iphase) + "\t" + phasedata.getComponentA(iphase) + "\t" + phasedata.getComponentB(iphase) + "\t" + phasedata.getStdst(iphase)[3]  + "\t" + phasedata.getNp(iphase) + "\t"  + "\t" + phasedata.getStrM(iphase) + "\t" + phasedata.getStrIa(iphase) + "\t" + phasedata.getStrE(iphase), outfile);
        }
        writeln("#************************STOP#", outfile);
        Print.f(cname + ".fprintPhaseData() method ended", 7);
    }

    private static void write(String str, String infile) throws IOException {
        try (FileWriter fl = new FileWriter(infile, true)) {
            fl.write(str);
        }
    }// closed Method 

    private static void writeln(String str, String Infile) throws IOException {
        write(str + nl, Infile);
    }// closed Method f() 1

    private static void writeln(String infile) throws IOException {
        write(System.getProperty("line.separator"), infile);
    }// closed Method f() 2

    private void drawLine(String outfile) throws IOException {
        writeln("================================================================================", outfile);
    }
}// Closed Class DataPrinter

