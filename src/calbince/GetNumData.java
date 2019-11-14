/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import utils.io.Print;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

/**
 *
 * @author admin
 */
public class GetNumData {

    private int ndat;
    private final String inputFileName;
    private final String cname = "GetNumData";

    public GetNumData(String infile) throws IOException {
        Print.f(cname + ".constructor called", 6);
        this.inputFileName = infile;
        Print.f(cname + ".constructor ended", 6);
    }

    /*
    returns number of data in an input file (such as number of phases in phasesData file and  number of experimental data points in exptData file
     */
    public int getNumDataPoints() throws FileNotFoundException, IOException {
        //Print.f(cname + ".getNumData called with Input File:" + infile, 7);
        LineNumberReader lineCounter = new LineNumberReader(new InputStreamReader(new FileInputStream(inputFileName)));
        String nextLine;
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
}
