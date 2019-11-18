/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calbince;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import utils.io.Print;

/**
 *
 * @author admin
 */
public class tdb {

    private int nel; // no of elements
    private int keww;   //keywords
    String tdbFileName;
    List<Element> elementList;
    List<Species> speciesList;
    List<Phase> phaseList;
    List<Function> functionList;
    String endmarkSpace = " ";
    String endMarkSemiColon = ";";
    String embarkY = "Y";

    public tdb() {//Constructor for initiating datbase structure
        System.out.println("readtdb called");
        this.elementList = new ArrayList<>();
        this.speciesList = new ArrayList<>();
        this.phaseList = new ArrayList<>();
        this.functionList = new ArrayList<>();
    }

    public tdb(String tdbFileName) throws FileNotFoundException, IOException {//Constructor for initiating datbase structure and filled with tdbFileName file
        System.out.println("readtdb called with " + tdbFileName);
        this.tdbFileName = tdbFileName;
        this.elementList = new ArrayList<>();
        this.speciesList = new ArrayList<>();
        this.phaseList = new ArrayList<>();
        this.functionList = new ArrayList<>();
        readFile();
//        for (Phase phase : phaseList) {
//            phase.print();
//        }
    }

    private void readFile() throws FileNotFoundException {
        try {
            FileInputStream fin = new FileInputStream(tdbFileName);//vj-15-03-12
            DataInputStream in = new DataInputStream(fin);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String str;
            String keywordString = "";
            String[] keywordStringList;
            String temp = "";
            String keyword;
            while ((str = br.readLine()) != null) { //reading each line of the file
                str = str.trim();//remove all leading and trailing spaces from string
                if (!str.startsWith("$")) { //ignore line strating with "$" sign
                    System.out.println();
                    //Print.f(str, 1);
                    if (str.endsWith("!")) { // check if keyword string is complete
                        keywordString = temp + endmarkSpace + str;
                        //keywordString = keywordString.trim().replaceAll(" +", " ");// remove extra spces, needs to be checked !
                        Print.f("keyword:" + keywordString, 1);
                        keywordStringList = keywordString.trim().split(" ", 2); //splitting keywordString using space " " into words, needs to be checked for "!" as well
                        //Print.f("keywordStringList", keywordStringList, 0);
                        keyword = keywordStringList[0]; //fisrt word should be a keyword
                        //Print.f(keyword, 0);
                        switch (keyword) {//code need to be modified for partial matches cases! one possible way is to trim both kewwords to be matched with minimum possible characters and then match
                            case "ELEMENT":
                                Element elementObj = new Element(keywordStringList[1]);
                                elementList.add(elementObj);
                                break;
                            case "SPECIES":
                                Species speciesObj = new Species(keywordStringList[1]);
                                speciesList.add(speciesObj);
                                break;
                            case "PHASE":
                                Phase phaseObj = new Phase(keywordStringList[1]);
                                phaseList.add(phaseObj);
                                break;
                            case "CONSTITUENT":
                            case "CONST":
                                addConstituent(keywordStringList[1]);
                                break;
                            case "ADD_CONSTITUENT":
                                break;
                            case "COMPOUND_PHASE":
                                break;
                            case "ALLOTROPIC_PHASE":
                                break;
                            case "TEMPERATURE_LIMITS":
                            case "TEMP_LIM":
                                break;
                            case "DEFINE_SYSTEM_DEFAULT":
                                break;
                            case "DEFAULT_COMMAND":
                                break;
                            case "DATABASE_INFORMATION":
                                break;
                            case "TYPE_DEFINITION":
                            case "TYPE_DEF":
                                break;
                            case "FTP_FILE":
                                break;
                            case "FUNCTION":
                                Function funcObj = new Function(keywordStringList[1]);
                                functionList.add(funcObj);
                                break;
                            case "PARAMETER":
                                Parameter paramObj = new Parameter(keywordStringList[1]);
                                //functionList.add(funcObj);
                                break;
                            case "PARA":
                            case "PARAM":
                                break;
                            case "OPTIONS":
                                break;
                            case "TABLE":
                                break;
                            case "ASSESSED_SYSTEMS":
                                break;
                            case "REFERENCE_FILE":
                                break;
                            case "LIST_OF_REFERENCES":
                                break;
                            case "ADD_REFERENCE":
                                break;
                            case "CASE":
                                break;
                            case "ENDCASE":
                                break;
                            case "VERSION_DATA":
                                break;
                            case "VERSION_DATE":
                                break;
                            case "DIFFUSION":
                                break;
                            case "ZERO_VOLUME_SPECIES":
                                break;
                            default:
                                Print.f("No keyword match found !", 0);

                        }
                        temp = ""; //reset temp

                    } else { // otherwise, keep adding remaining line to the keyword string 
                        //Print.f("keyword incomplete", 1);
                        temp = temp + endmarkSpace + str; // to deal with cases in which no space is given between two lines
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File Not Found");
        } catch (IOException ex) {
            Logger.getLogger(tdb.class.getName()).log(Level.SEVERE, null, ex);
        }

        //System.out.println(phaseList.size());
        System.out.println(elementList.size());
    }

    class Phase {//better speciesName for this class!
        //PHASE LIQUID:L %  1  1.0  !

        private String phaseName;
        private String phaseType = "";
        private String dataTypeCode;
        private int numSubLat; //Number of sublattices
        private int[] numSites; //Number of sites of each of the sublattices 
        private String[][] constituentList;

        Phase(String keywordStringLine) throws IOException {
            String[] keywordStringList = keywordStringLine.trim().split("\\s+");
            //Print.f("keywordStringList:", keywordStringList, 0);
            String[] tempList = splitString(keywordStringList[0], ":");
            //Print.f("tempList:", tempList, 0);
            this.phaseName = tempList[0]; //some checks to be added for the Phase speciesName
            //Print.f("phaseName:" + phaseName, 0);
            //Print.f("tempList.length:" + tempList.length, 0);
            if (tempList.length > 1) {
                this.phaseType = tempList[1];
                //Print.f("phaseType:" + phaseType, 0);
            }
            //Print.f("dataTypeCode:", 0);
            this.dataTypeCode = keywordStringList[1]; //some checks to be added for the dataTypeCode
            //Print.f("dataTypeCode:" + keywordStringList[1], 0);
            numSubLat = Integer.parseInt(keywordStringList[2]);
            //Print.f("numSubLat:" + numSubLat, 0);
            numSites = new int[numSubLat];
            for (int i = 0; i < numSubLat; i++) {
                numSites[i] = (int) (Float.parseFloat(keywordStringList[3 + i]));
            }
            //Print.f("numSites:", numSites, 0);
        }

        public String getPhaseNamet() {
            return (phaseName);
        }

        public int getNumSubLat() {
            return (numSubLat);
        }

        public int[] getNumSites() {
            return (numSites);
        }

        public void setConstituentList(String[][] constList) {
            this.constituentList = constList;
        }

        public String[][] getConstituentList() {
            return (this.constituentList);
        }

        public void print() throws IOException {
            Print.f("----------------------------", 0);
            Print.f("phaseName:" + phaseName, 0);
            Print.f("phaseType:" + phaseType, 0);
            Print.f("numSubLat:" + numSubLat, 0);
            Print.f("numSites:", numSites, 0);
            //Print.f("constituentList", constituentList, 0);
        }
    }

    // find phase in phaseList based on phase name, and return the id;
    private int findPhase(String phasename) throws IOException {
        int i = 0;
        int phaseid = -1;
        for (Phase x : phaseList) // find in phaseList
        {
            if (x.phaseName == null ? phasename == null : x.phaseName.equals(phasename)) {
                phaseid = i;
                return phaseid;
            }
            i++;
        }
        // if phase not be found, exit, and print error
        if (phaseid == -1) {
            Print.f("phaseName: " + phasename + " not found !", 0);
            return -1;
        }
        return phaseid;
    }

    private void addConstituent(String keywordStringLine) throws IOException {
        // LIQUID:L :AG,CU :  !
        //Print.f("addConstituent called: " + keywordStringLine, 0);
        String[] keywordString1 = keywordStringLine.trim().split(" ", 2);
        //Print.f("keywordString1: ", keywordString1, 0);
        String[] tempList = splitString(keywordString1[0], ":");//trim phase type
        String phaseName = tempList[0].trim();
        int phaseIndex = findPhase(phaseName);
        //Print.f("phaseIndex:" + phaseIndex, 0);
        int numSubLat = phaseList.get(phaseIndex).getNumSubLat();
        String[] keywordString2 = keywordString1[1].trim().split(":");
        //Print.f("keywordString2: ", keywordString2, 0);
        String[][] constituentList = new String[numSubLat][];
        for (int i = 0; i < numSubLat; i++) {
            //Print.f("lists:", keywordString2[i + 1], 0);
            constituentList[i] = keywordString2[i + 1].trim().split(",");
            //Print.f("consList[i]:", consList[i], 0);
        }
        phaseList.get(phaseIndex).setConstituentList(constituentList);
    }

    class Element {//better speciesName for this class!

        String elementName;
        String ref_state;
        double mass;
        double H298;
        double S298;

        Element(String keywordStringLine) throws IOException {
            //Print.f("keywordStringLine:" + keywordStringLine, 0);
            String[] keywordStringList = keywordStringLine.trim().split("\\s+|!");
            //Print.f("keywordStringList:",keywordStringList, 0);
            this.elementName = keywordStringList[0]; //some checks to be added for the Phase speciesName
            this.ref_state = keywordStringList[1]; //some checks to be added for the dataTypeCode
            this.mass = Double.parseDouble(keywordStringList[2]);
            this.H298 = Double.parseDouble(keywordStringList[3]);
            this.S298 = Double.parseDouble(keywordStringList[4]);
            //Print.f("elementName:" + elementName, 0);
            //Print.f("refState:" + ref_state, 0);
            //Print.f("mass:", mass, 0);
            //Print.f("H298:", H298, 0);
            //Print.f("S298:", S298, 0);
        }
    }

    class Species {//better speciesName for this class!

        String speciesName;			//
        String formula;			//
        //double charge;			//

        Species() {//Generic constructor method

        }

        Species(String keywordStringLine) throws IOException {
            String[] keywordStringList = keywordStringLine.trim().split("\\s+");
            this.speciesName = keywordStringList[0]; //some checks to be added for the Phase speciesName
            this.formula = keywordStringList[1]; //some checks to be added for the dataTypeCode
            //this.charge = Double.parseDouble(keywordStringList[3]);
            //Print.f("name:" + speciesName, 0);
            //Print.f("formula:" + formula, 0);
            //Print.f("charge:", charge, 0);
        }
    }

    class Function {//better speciesName for this class!

        String name;			//
        List<Double> T;		//
        List<String> express;		//

        Function() {//Generic constructor method
            T = new ArrayList<>();
            express = new ArrayList<>();
        }

        Function(String keywordLine) throws IOException {
            //Print.f("Function is called with:" + keywordLine, 0);
            T = new ArrayList<>();
            express = new ArrayList<>();
            String[] expressLine;
            int numRange;//number of temperature ranges
            String[] splitKeywordLine;
            String[] splitExpressLine;
            splitKeywordLine = splitString(keywordLine.trim(), endmarkSpace);// get function name 
            this.name = splitKeywordLine[0];
            keywordLine = splitKeywordLine[1].trim();//remaining kewwordLine
            splitKeywordLine = splitString(keywordLine, endmarkSpace);//get lower T limit
            this.T.add(Double.parseDouble(splitKeywordLine[0].trim()));
            keywordLine = splitKeywordLine[1].trim();//remaining kewwordLine
            splitKeywordLine = keywordLine.split(endMarkSemiColon);//to get expressions
            //Print.f("splitKeywordLine:", splitKeywordLine, 0);
            numRange = splitKeywordLine.length;
            //Print.f("numRange:", numRange, 0);
            express.add(splitKeywordLine[0].trim());
            for (int i = 1; i < numRange - 1; i++) {
                //Print.f("expressLine:" + splitKeywordLine[i], 0);
                splitExpressLine = splitString(splitKeywordLine[i].trim(), endmarkSpace);// get T
                this.T.add(Double.parseDouble(splitExpressLine[0].trim()));//Add T
                splitKeywordLine[i] = splitExpressLine[1].trim();//remaining ExpressLine
                splitExpressLine = splitString(splitKeywordLine[i].trim(), endmarkSpace);// split to get word "Y/N" and expression
                this.express.add(splitExpressLine[1].trim());//add expression
            }
            //Print.f("expressLine:" + splitKeywordLine[numRange - 1], 0);
            splitExpressLine = splitString(splitKeywordLine[numRange - 1].trim(), endmarkSpace);// get T
            this.T.add(Double.parseDouble(splitExpressLine[0].trim()));//Add T
            //Print.f("name:" + name, 0);
            //System.out.println(T);
            //System.out.println(express);
        }
    }

    class Parameter {

        /* para types
		1 end member;2 binary interaction parameters; 
		3 ternary interaction parameters
		4 reciprocal interaction parameter;
         */
        int MDim;
        int order;
        int kind;
        int nsub2 = 0;  // binary para
        int nsub3 = 0;  // ternary para
        int[] idsub2 = new int[2];  // interaction ele id in binary para
        int[] idsub3 = new int[3];  // interaction ele id in ternary para
        int[] vidsub2 = new int[2];
        int[] vidsub3 = new int[3];
        String type;
        String phasename;
        int phaseId;
        String[][] constituentList;// 10 sublattices, element in each sublattice is a vector// ele id in Phase constitution£¬eg, constituent :Al,Mg,Zn:Zn,Va:
        int yn = 0;		// constituent number // para G(BCC,Al:Zn,0),become G(BCC,1:4,0)
        int yidc[] = new int[MDim * 3];	// constituent id
        int yids[] = new int[MDim * 3];// sublattice id
        int vyn = 0;	 // varible y number
        int vyidc[] = new int[MDim * 3]; // constituent id
        int vyids[] = new int[MDim * 3]; // sublattice id
        int vyidv[] = new int[MDim * 3];// para's vy in Phase's vy id
        List<Double> T;		//
        List<String> express;		//	//
        //express_digit express_digit[10]; // 10 T segment, terms in each segment is a vector 

        Parameter(String keywordLine) throws IOException {
            //Print.f("Parameter is called with:" + keywordLine, 0);
            //printPhaseList();
//          PARAMETER G(HCP_A3,AG,CU:VA;0) 298.15 +35000-8*T; 6000     N REF135 !
            T = new ArrayList<>();
            express = new ArrayList<>();
            String[] tempList;
            String templineL;
            String templineR;
            String element;
            int tempid;
            int elementid;
            String[][] consList;
            int numSubLat;
            //                 0   1    2    3    4   5 
            String endmark[] = {" ", ":", ",", "\\(", "\\)", ";"};
            tempList = splitString(keywordLine, endmark[4]);
            templineL = tempList[0];//Stored upto  G(HCP_A3,AG,CU:VA;0
            templineR = tempList[1];//Reamining Line 298.15 +35000-8*T; 6000     N REF135 !
            keywordLine = templineR;//Reamining Line 
            tempList = splitString(templineL, endmark[3]);//split with "(" to get phase type 
            templineL = tempList[0];//Phase Type -> G
            templineR = tempList[1];//Remaining tempLine-> HCP_A3,AG,CU:VA;0
            this.type = templineL;//Phase Type
            //Print.f("type: " + type, 0);
            tempList = splitString(templineR, endmark[2]);//split with "," to get phase name
            templineL = tempList[0];//HCP_A3
            templineR = tempList[1];//AG,CU:VA;0
            this.phasename = templineL;
            //Print.f("phasename: " + phasename, 0);
            this.phaseId = findPhase(phasename);
            //Print.f("phaseId: " + phaseId, 0);
            if (phaseId != -1) {
                consList = phaseList.get(phaseId).constituentList;//Read consList
                numSubLat = phaseList.get(phaseId).getNumSubLat();//read numSubLat
                constituentList = new String[numSubLat][];
                //Print.f("numSubLat:" + numSubLat, 0);
                tempList = splitString(templineR, endmark[5]); //split with ";" to get order
                templineL = tempList[0];//AG,CU:VA
                if (tempList.length > 1) {
                    templineR = tempList[1];//0
                    this.order = Integer.parseInt(templineR);
                    //Print.f("order: " + order, 0);
                }
                tempList = templineL.split(endmark[1]); //split with ":" 
                for (int i = 0; i < numSubLat; i++) { // loop sub
                    constituentList[i] = tempList[i].split(endmark[2]); //split with ","
                }
                //Print.f("constituentList: ", constituentList, 0);
            }
            String[] splitExpressLine;
            tempList = splitString(keywordLine.trim(), endmarkSpace);//get lower T limit
            //Print.f("tempList:", tempList, 0);
            this.T.add(Double.parseDouble(tempList[0].trim()));
            keywordLine = tempList[1].trim();//remaining kewwordLine
            tempList = keywordLine.split(endMarkSemiColon);//to get expressions
            //Print.f("splitKeywordLine:", splitKeywordLine, 0);
            int numRange = tempList.length;
            //Print.f("numRange:", numRange, 0);
            express.add(tempList[0].trim());
            for (int i = 1; i < numRange - 1; i++) {
                //Print.f("expressLine:" + splitKeywordLine[i], 0);
                splitExpressLine = splitString(tempList[i].trim(), endmarkSpace);// get T
                this.T.add(Double.parseDouble(splitExpressLine[0].trim()));//Add T
                tempList[i] = splitExpressLine[1].trim();//remaining ExpressLine
                splitExpressLine = splitString(tempList[i].trim(), endmarkSpace);// split to get word "Y/N" and expression
                this.express.add(splitExpressLine[1].trim());//add expression
            }
            //Print.f("expressLine:" + splitKeywordLine[numRange - 1], 0);
            splitExpressLine = splitString(tempList[numRange - 1].trim(), endmarkSpace);// get T
            this.T.add(Double.parseDouble(splitExpressLine[0].trim()));//Add T
            //Print.f("type: " + type, 0);
            //Print.f("phasename: " + phasename, 0);
            //Print.f("phaseId: " + phaseId, 0);
            //Print.f("order: " + order, 0);
            //Print.f("constituentList: ", constituentList, 0);
            //System.out.println(T);
            //System.out.println(express);
        }

    }

    class TypeDefinition {

        String label;			//
        String model;			//
        String command;			//
        String phasename;		//
        String property;		//
        String disname;
        double value1;			//
        double value2;			//
        
        
    };

    private String[] splitString(String inputLine, String endmark) throws IOException {
        //Print.f("splitString called with endmark: " + endmark, 0);
        //Print.f("splitString called with inputString: " + inputLine, 0);
        String[] splitLine = inputLine.split(endmark, 2); //splitting keywordString using space " " into words
        return splitLine;
    }

    private void printPhaseList() throws IOException {
        for (Phase phase : phaseList) {
            phase.print();
        }
    }

}
