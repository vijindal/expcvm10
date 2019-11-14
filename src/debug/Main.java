
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package debug;

import utils.io.DataPrinter;
import utils.io.Print;
import calbince.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;

public class Main {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("-------------Job/System Properties--------------------");
        String currentDirectory = System.getProperty("user.dir");
        String expIn = currentDirectory + "/data/ExptData.txt";
        String phaseIn = currentDirectory + "/data/PhaseData.txt";
        String filePrefix = currentDirectory + "/data/log";
        try {
            System.out.println("-------------Job/System Properties--------------------");
            System.out.println("  started on: " + (new Date()).toString());
            System.out.println("    hostname: " + InetAddress.getLocalHost().getHostName());
            System.out.println("executing on: " + System.getProperty("os.name"));
            System.out.println("        arch: " + System.getProperty("os.arch"));
            System.out.println("      kernel: " + System.getProperty("os.version"));
            System.out.println(" JVM-version: " + System.getProperty("java.vm.version"));
            System.out.println("  JVM-vender: " + System.getProperty("java.vm.vender"));
            System.out.println("    JVM-name: " + System.getProperty("java.vm.name"));
            System.out.println("-----------------------------------------------------");
            System.out.println("                expCVM-version: 05.00");
            System.out.println("Default experimental data file: " + expIn);
            System.out.println("       Default phase data file: " + phaseIn);
            System.out.println("              Default log file: data/log.txt");
            System.out.println("-----------------------------------------------------");
            long beg = System.currentTimeMillis();
            if (args.length == 0) {
                System.out.println("No arguments were given, default calModule() called");
                calModule(expIn, phaseIn, filePrefix);
            } else {
                if (args[0].equals("opt")) {
                    optimizeModule(expIn, phaseIn, filePrefix);
                }
            }
            long end = System.currentTimeMillis();
            System.out.println("#Calculations took " + (double) (end - beg) / 1000 + " sec");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void calModule(String exptDataFileName, String phaseDataFileName, String filePrefix) throws IOException {
        int logLevelParamIn = 2;
        DataPrinter Dp = new DataPrinter(filePrefix, logLevelParamIn);
        //Reading phase data
        //PhaseData phasedata = new PhaseData();
        //DataReader dr2 = new DataReader(phaseDataFileName, phasedata);
        //phasedata.readPhaseDataInputFile();
        PhaseData phasedata = new PhaseData(phaseDataFileName);
        //System.out.println("filename:"+phaseDataFileName);
        phasedata.readPhaseDataInputFile();
        phasedata.print();
        //Reading expt data
        //ExptData exptdata = new ExptData();
        //DataReader dr1 = new DataReader(exptDataFileName,exptdata);
        ExptData exptdata = new ExptData(exptDataFileName);
        exptdata.readExptDataFile();
        exptdata.print();
        //Dp.initOutput(exptdata, phasedata);
        //Calculations
        Print.f("Cal Model calculations for " + filePrefix + " system begins", 0);
        ExptData recordData = new ExptData();
        CalModel calmodel = new CalModel(exptdata, phasedata, recordData);
        calmodel.Run();
        //Post-Processing
        recordData.print();
        //Dp.finalOutput(recordData, phasedata);
        Print.f("Finished Execution", 0);
    }

    private static void optimizeModule(String exptDataFileName, String phaseDataFileName, String filePrefix) throws IOException {
        //Initialization
        int logLevelParamIn = 1;
        DataPrinter dataPrinter = new DataPrinter(filePrefix, logLevelParamIn);

        //Reading phase data
        //PhaseData phasedata = new PhaseData();
        //DataReader dr2 = new DataReader(phaseDataFileName, phasedata);
        //phasedata.readPhaseDataInputFile();
        PhaseData phasedata = new PhaseData(phaseDataFileName);
        phasedata.readPhaseDataInputFile();
        phasedata.print();

        //Reading expt data
        //ExptData exptdata = new ExptData();
        //DataReader dr1 = new DataReader(exptDataFileName,exptdata);
        ExptData exptdata = new ExptData(exptDataFileName);
        exptdata.readExptDataFile();
        exptdata.readExptDataFile();
        //Checking Consistency
        exptdata.check(phasedata);

        phasedata.setFitParam();
        exptdata.print();
        phasedata.print();
        phasedata.printFitParam();
        dataPrinter.initOpt(exptdata, phasedata);
        //Calculations
        Print.f("Simultaneous optimization begins...", 0);
        OptMrq optmrq = new OptMrq(exptdata, phasedata);
        //optmrq.print();
        optmrq.fit(50);
        //Dp.outOpt(phasedata);
        Print.f("Simultaneous optimization calculations ended", 0);
        //Post-Processing
        dataPrinter.finalOpt(phasedata);
        Print.f("Finished Execution", 0);
    }
}
