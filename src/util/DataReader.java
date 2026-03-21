package util;

import calbince.ExptData;
import calbince.PhaseData;
import java.io.*;
import java.util.StringTokenizer;

/**
 * @author : Shivam
 * @version : 2012.03.29 (SU): *Added Escape Character "//" for input file so
 * that weight need not to be zero to skip a point.
 *
 *
 */
public class DataReader {

    private static String cname = "DataReader";
    private String phaseInputFileName;
    private String dataInputFileName;
    private PhaseData phasedata;
    private ExptData exptdata;
    private int numPhases;

    public DataReader(String dataInputFileName, ExptData exptdata) throws IOException {//vj-2013-05-20
        Print.f(cname + ".constructor called", 6);
        this.dataInputFileName = dataInputFileName;
        this.exptdata = exptdata;
        //readExptDataFile(dataInputFileName, exptdata);//2017-01-26-VJ
        Print.f(cname + ".constructor ended", 6);
    }

    public DataReader(String phaseInputFileName, PhaseData phasedata) throws IOException {//vj-2013-05-20
        Print.f(cname + ".constructor called", 6);
        this.phaseInputFileName = phaseInputFileName;
        this.phasedata = phasedata;
        //readPhaseDataFile(phaseInputFileName, phasedata);//2017-01-26-VJ
        Print.f(cname + ".constructor ended", 6);
    }

    public int getPid(String pType, String pModel, String pInstance) throws IOException {//vj-2013-05-19-Modified
        Print.f(cname + ".getpIndex() method called", 7);
        //System.out.println(pType+","+pModel+","+pInstance);
        for (int i = 0; i < numPhases; i++) {
            //System.out.println(i);
            //System.out.println(phasedata.getPid0(i)+","+phasedata.getPid1(i)+","+phasedata.getPid2(i));
            if (phasedata.getPid0(i) == null) {
                continue;
            }
            if ((phasedata.getPid0(i).equalsIgnoreCase(pType)) && (phasedata.getPid1(i).equalsIgnoreCase(pModel)) && (phasedata.getPid2(i).equalsIgnoreCase(pInstance))) {
                Print.f(cname + ".getpIndex() method ended with pIndex:" + i, 7);
                return (phasedata.getPhaseIndex(i));
            }
        }//2012=02-16(VJ): Modified
        Print.f(cname + ".getpIndex() method with pIndex:"+"-1", 7);
        return (-1);
    }

    public static int getNData(String infile) throws FileNotFoundException, IOException {
        int ndat;
        //Print.f(cname + ".getNData called with Input File:" + infile, 7);
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
        //Print.f(cname + ".getNData ended with ndat:" + ndat, 7);
        return (ndat);
    }// Close getNData() Method
}// Closed class FilIO
