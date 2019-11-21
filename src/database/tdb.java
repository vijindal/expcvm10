/**
 * @author      Vikas Jindal
 * @version 10.0 (current version number of program)
 * @since 10.0 (the version of the package this class was first added to)
 */
package database;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import utils.io.Print;

/**
 *
 * @author Vikas Jindal
 * @version 10.0 (current version number of program)
 * @since 10.0 (the version of the package this class was first added to)
 */
public class tdb {

    private String tdbFileName;
    private ArrayList<Element> elementList;
    private ArrayList<Species> speciesList;
    private ArrayList<Phase> phaseList;
    private ArrayList<Function> functionList;
    private ArrayList< TypeDefinition> typeDefinitionList;
    private Reference reference;
    private int numElement;
    private int numSpecies;
    private int numPhases;
    private int numFunction;
    private int numTypeDef;

    public tdb() {//Constructor for initiating datbase structure
        System.out.println("tdb method is called");
        this.elementList = new ArrayList<>();
        this.speciesList = new ArrayList<>();
        this.phaseList = new ArrayList<>();
        this.functionList = new ArrayList<>();
        this.typeDefinitionList = new ArrayList<>();
    }

    public tdb(String tdbFileName) throws FileNotFoundException, IOException {//Constructor for initiating datbase structure and filled with tdbFileName file
        System.out.println("tdb method is called with: " + tdbFileName);
        this.tdbFileName = tdbFileName;
        this.elementList = new ArrayList<>();
        this.speciesList = new ArrayList<>();
        this.phaseList = new ArrayList<>();
        this.functionList = new ArrayList<>();
        this.typeDefinitionList = new ArrayList<>();
        readFile();
    }

    /*
    * This method returns relevent part of the database as per inputElementList
    * @param inputElementList List of elments
    * @return   tdb      datbase
    * @since             10.0
     */
    public tdb gettdb(String[] inputElementList) throws IOException {
        printSepLine();
        Print.f("gettdb method is called with:", inputElementList, 0);
        tdb systdb = new tdb();
        boolean found;
        systdb.tdbFileName = tdbFileName;
        int sysNumElement = inputElementList.length;
        systdb.numElement = sysNumElement;
        for (String s : inputElementList) {
            for (Element e : elementList) {
                if (s.equals(e.elementName)) {
                    systdb.elementList.add(e);
                } else {
                    //Print.f("Element: " + s + " is not found in the database file: " + tdbFileName, 0);
                }
            }
        }

        for (String elementName : inputElementList) {
            for (Species sysSpecies : speciesList) {
                if (sysSpecies.elmntCount.containsKey(elementName)) {
                    systdb.speciesList.add(sysSpecies);
                }
            }
        }
        systdb.numSpecies = systdb.speciesList.size();

        for (Phase p : phaseList) {//search phases that contains element(s) of inputElementList
            Phase sysPhase = new Phase();
            // On all the sublattices of the phase one of the element of inputElementList or "VA" should be found 
            //Print.f("Phase: " + p.phaseName + " is called", 0);
            //p.print();
            ArrayList<ArrayList<String>> tempList = p.getConstituentList();//to hold a copy of constituent list
            //Print.f(tempList.toString(), 0);
            found = true;
            ArrayList<ArrayList<String>> sysList = new ArrayList<>();
            for (ArrayList<String> list1 : tempList) {
                ArrayList<String> systemp = new ArrayList<>();
                for (String e : list1) {
                    if ("VA".equals(e) | isPresent(inputElementList, e)) {
                        systemp.add(e);
                    }
                }
                //Print.f(systemp.toString(), 0);
                if (systemp.isEmpty()) {
                    found = false;
                    break;
                } else {
                    sysList.add(systemp);
                }
            }
            if (found) {
                ArrayList<Parameter> sysParamList = p.getParam(inputElementList);
                if (!sysParamList.isEmpty()) {
                    //System.out.println("matched and added");
                    sysPhase.phaseName = p.phaseName;
                    sysPhase.phaseType = p.phaseType;
                    sysPhase.numSubLat = p.numSubLat;
                    sysPhase.numSites = p.numSites;
                    sysPhase.dataTypeCode = p.dataTypeCode;
                    sysPhase.constituentList = sysList;
                    sysPhase.paramList = sysParamList;
                    systdb.phaseList.add(sysPhase);
                }
            }
            //System.out.println(sysParamList.toString());
        }
        systdb.numPhases = systdb.phaseList.size();
        systdb.functionList = functionList;// to be improved !
        systdb.numFunction = numFunction;
        systdb.typeDefinitionList = typeDefinitionList;// to be improved !
        systdb.numTypeDef = numTypeDef;
        systdb.reference = reference;// to be improved !
        Print.f("end of gettdb method", 0);
        printSepLine();
        return (systdb);
    }

    private void readFile() throws FileNotFoundException {
        try {
            printSepLine();
            Print.f("Reading tdb file: " + tdbFileName, 0);
            FileInputStream fin = new FileInputStream(tdbFileName);//vj-15-03-12
            DataInputStream in = new DataInputStream(fin);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String str;
            String keywordString;
            String[] keywordStringList;
            String temp = "";
            String keyword;
            String endmarkSpace = " ";
            while ((str = br.readLine()) != null) { //reading each line of the file
                str = str.trim();//remove all leading and trailing spaces from string
                if (!str.startsWith("$")) { //ignore line strating with "$" sign
                    //System.out.println();
                    //Print.f(str, 1);
                    if (str.endsWith("!")) { // check if keyword string is complete
                        keywordString = temp + endmarkSpace + str;
                        //keywordString = keywordString.trim().replaceAll(" +", " ");// remove extra spces, needs to be checked !
                        //Print.f("keyword:" + keywordString, 1);
                        keywordStringList = keywordString.trim().split(" ", 2); //splitting keywordString using space " " into words, needs to be checked for "!" as well
                        //Print.f("keywordStringList", keywordStringList, 0);
                        keyword = keywordStringList[0]; //fisrt word should be a keyword
                        //Print.f(keyword, 0);
                        switch (keyword) {//code need to be modified for partial matches cases! one possible way is to trim both kewwords to be matched with minimum possible characters and then match
                            case "ELEMENT":
                                Element elementObj = new Element(keywordStringList[1]);
                                elementList.add(elementObj);
                                numElement = numElement + 1;
                                break;
                            case "SPECIES":
                                Species speciesObj = new Species(keywordStringList[1]);
                                speciesList.add(speciesObj);
                                numSpecies = numSpecies + 1;
                                break;
                            case "PHASE":
                                Phase phaseObj = new Phase(keywordStringList[1]);
                                phaseList.add(phaseObj);
                                numPhases = numPhases + 1;
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
                                TypeDefinition Obj = new TypeDefinition(keywordStringList[1]);
                                typeDefinitionList.add(Obj);
                                numTypeDef = numTypeDef + 1;
                                break;
                            case "FTP_FILE":
                                break;
                            case "FUNCTION":
                                Function funcObj = new Function(keywordStringList[1]);
                                functionList.add(funcObj);
                                numFunction = numFunction + 1;
                                break;
                            case "PARAMETER":
                                Parameter paramObj = new Parameter(keywordStringList[1]);
                                if (paramObj.phaseId != -1) {
                                    phaseList.get(paramObj.phaseId).paramList.add(paramObj);
                                }
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
                                reference = new Reference(keywordStringList[1]);
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
            Print.f("End of reading tdb file", 0);
            printSepLine();
        } catch (FileNotFoundException e) {
            System.err.println("File Not Found");
        } catch (IOException ex) {
            Logger.getLogger(tdb.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    class Phase {//better speciesName for this class!
        //PHASE LIQUID:L %  1  1.0  !

        private String phaseName;
        private String phaseType = "";
        private String dataTypeCode;
        private int numSubLat; //Number of sublattices
        private int[] numSites; //Number of sites of each of the sublattices 
        private ArrayList<ArrayList<String>> constituentList;
        private ArrayList<Parameter> paramList; //List of parameters objects

        Phase() {
            constituentList = new ArrayList<>();
            paramList = new ArrayList<>();//Initialize paramList
        }

        Phase(String keywordStringLine) throws IOException {
            constituentList = new ArrayList<>();
            paramList = new ArrayList<>();//Initialize paramList
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

        public String getPhaseName() {
            return (phaseName);
        }

        public String getPhaseType() {
            return (phaseType);
        }

        public String getDataTypeCode() {
            return (dataTypeCode);
        }

        public int getNumSubLat() {
            return (numSubLat);
        }

        public int[] getNumSites() {
            return (numSites);
        }

        public ArrayList<ArrayList<String>> getConstituentList() {
            return (this.constituentList);
        }

        public List<Parameter> getParam() {
            return (paramList);
        }

        /*
        * This Method return list of Paramters having constituent List consist of elementList only
        * @param elementList List of elements
        * @return   list of Paramters
        * @since             10.0
         */
        public ArrayList<Parameter> getParam(String[] elementList) throws IOException {
            //Print.f("getParam method is called with:" + Arrays.toString(elementList), 0);
            ArrayList<Parameter> sysparamList = new ArrayList<>();//for storing parameter having constituent List consist of elementList only
            ArrayList<ArrayList<String>> tempList;
            boolean isOtherElement;
            for (Parameter p : paramList) {
                isOtherElement = false;
                tempList = p.constituentList;
                //Print.f("tempList:", tempList, 0);
                for (ArrayList<String> tempList1 : tempList) {//loop over row
                    for (String item : tempList1) {
                        //Print.f(" item: " + item, 0);//loop over column
                        if (!"VA".equals(item)) {
                            if (!isPresent(elementList, item)) {
                                isOtherElement = true;
                                break;
                            }
                        }
                    }
                }
                if (!isOtherElement) {
                    sysparamList.add(p);
                }
            }
            return (sysparamList);
        }

        public void setConstituentList(ArrayList<ArrayList<String>> constList) {
            this.constituentList = constList;
        }

        public void print() throws IOException {
            Print.f("----------------------------", 0);
            Print.f("phase:" + phaseName + '\t' + phaseType, 0);
            System.out.print("Subl: " + numSubLat + '\t');
            Print.f("", numSites, 0);
            System.out.println("Constituents: " + constituentList);
            paramList.forEach((p) -> {
                System.out.println("" + p.type + '\t' + p.constituentList.toString() + '\t' + p.order + '\t' + p.T + '\t' + p.express);
            });
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
        ArrayList<ArrayList<String>> constituentList = new ArrayList<>();
        for (int i = 0; i < numSubLat; i++) {
            //Print.f("lists:", keywordString2[i + 1], 0);
            String[] temp = keywordString2[i + 1].trim().split(",");
            ArrayList<String> arrayList = new ArrayList<>();
            for (String temp1 : temp) {//trim % after element name, if present
                arrayList.add(temp1.split("%")[0]);
            }
            constituentList.add(arrayList);
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
            //Print.f("elementName:",keywordStringList[0], 0);
            this.elementName = keywordStringList[0]; //some checks to be added for the Phase speciesName
            this.ref_state = keywordStringList[1]; //some checks to be added for the dataTypeCode
            this.mass = Double.parseDouble(keywordStringList[2]);
            this.H298 = Double.parseDouble(keywordStringList[3]);
            this.S298 = Double.parseDouble(keywordStringList[4]);
        }

        public void print() {
            System.out.format("%16s%16s%16s%16s%16s%n", elementName, ref_state, mass, H298, S298);
        }
    }

    class Species {//better speciesName for this class!

        String speciesName;			//
        String formula;			//
        //double charge;			//
        List<String> elmntList;
        HashMap elmntCount;

        Species(String keywordStringLine) throws IOException {
            elmntList = new ArrayList<>();
            String[] keywordStringList = keywordStringLine.trim().split("\\s+");
            this.speciesName = keywordStringList[0]; //some checks to be added for the Phase speciesName
            keywordStringList[1] = keywordStringList[1].trim().split("!")[0];
            this.formula = keywordStringList[1]; //some checks to be added for the dataTypeCode
            elmntCount = countElements(formula);
            //Print.f(elmntCount.toString(), 0);
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
            String endmarkSpace = " ";
            String endMarkSemiColon = ";";
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
                splitExpressLine = splitString(splitKeywordLine[i].trim(), endmarkSpace);// get T
                this.T.add(Double.parseDouble(splitExpressLine[0].trim()));//Add T
                splitKeywordLine[i] = splitExpressLine[1].trim();//remaining ExpressLine
                splitExpressLine = splitString(splitKeywordLine[i].trim(), endmarkSpace);// split to get word "Y/N" and expression
                this.express.add(splitExpressLine[1].trim());//add expression
            }
            splitExpressLine = splitString(splitKeywordLine[numRange - 1].trim(), endmarkSpace);// get T
            this.T.add(Double.parseDouble(splitExpressLine[0].trim()));//Add T
            //Print.f("name:" + name, 0);
            //System.out.println(T);
            //System.out.println(express);
        }
    }

    class Parameter {

        int order;
        String type;
        String phasename;
        int phaseId;
        ArrayList<ArrayList<String>> constituentList;// 10 sublattices, e in each sublattice is a vector// ele id in Phase constitution£¬eg, constituent :Al,Mg,Zn:Zn,Va:
        ArrayList<Double> T;		//
        ArrayList<String> express;		//	//

        Parameter(String keywordLine) throws IOException {
            //Print.f("Parameter is called with:" + keywordLine, 0);
            //printPhaseList();
//          PARAMETER G(HCP_A3,AG,CU:VA;0) 298.15 +35000-8*T; 6000     N REF135 !
            T = new ArrayList<>();
            express = new ArrayList<>();
            constituentList = new ArrayList<>();
            String endmarkSpace = " ";
            String endMarkSemiColon = ";";
            String[] tempList;
            String templineL;
            String templineR;
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
                //consList = phaseList.get(phaseId).constituentList;//Read consList
                numSubLat = phaseList.get(phaseId).getNumSubLat();//read numSubLat
                //constituentList = new String[numSubLat][];
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
                    String[] temp = tempList[i].split(endmark[2]); //split with ","
                    ArrayList<String> cons = new ArrayList<>();
                    for (String temp1 : temp) { //To remove "%" sign after element name
                        cons.add(temp1.split("%")[0]);
                    }
                    constituentList.add(cons);
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
        }

        public void print() throws IOException {
            System.out.println("" + phaseId + '\t' + type + '\t' + phasename + '\t' + order + '\t' + constituentList.toString() + '\t' + T.toString() + '\t' + express.toString());
        }

    }

    class TypeDefinition {
        // TYPE_DEFINITION [data-type code]*1 [secondary keyword with parameters] !
        //TYPE_DEFINITION & GES A_P_D BCC_A2 MAGNETIC  -1.0    4.00000E-01 !

        String label;			//
        String model;			//
        String command;			//
        String phasename;		//
        String property;		//
        String disname;
        double value1;			//
        double value2;			//
        String dataTypeCode;
        String secondaryKeyword;

        TypeDefinition(String keywordLine) throws IOException {
            String endmarkSpace = " ";
            String[] tempList = splitString(keywordLine, endmarkSpace);
            this.dataTypeCode = tempList[0];
            this.secondaryKeyword = tempList[1];//Processing of secondaryKeyword is to be done !
            //Print.f("dataTypeCode: " + dataTypeCode, 0);
            //Print.f("secondaryKeyword: " + secondaryKeyword, 0);
        }
    };

    class Reference {

        ArrayList<String> number;				//
        ArrayList<String> source;				//

        Reference(String keywordLine) throws IOException {
            number = new ArrayList<>();
            source = new ArrayList<>();
            String endmark2 = "\\'";
            String endmark3 = "SOURCE";
            String endmarkSpace = " ";
            String[] tempList;
            keywordLine = splitString(keywordLine, endmark3)[1];
            //Print.f("keywordLine: " + keywordLine, 0);
            while ((!"!".equals(keywordLine.trim())) && (0 != keywordLine.length())) {
                tempList = splitString(keywordLine.trim(), endmarkSpace);
                //Print.f("tempList1: ", tempList, 0);
                this.number.add(tempList[0]);		// "REF1"
                keywordLine = tempList[1];
                tempList = splitString(keywordLine.trim(), endmark2);
                tempList = splitString(tempList[1].trim(), endmark2);
                //Print.f("tempList2: ", tempList, 0);
                this.source.add(tempList[0]);
                keywordLine = tempList[1];
                //Print.f("keywordLine: ", keywordLine, 0);
            }
        }

        public void print() {
            System.out.println(number.toString() + '\t' + source.toString());
        }

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

    public void printtdb() throws IOException {
        printSepLine();
        Print.f("Listing data", 0);
        Print.f("List of " + numElement + " elements", 0);
        System.out.format("%16s%16s%16s%16s%16s%16s%n", "No", "Name", "Reference state", "Mass", "H298-H0", "S298");
        int i = 0;
        for (Element e : elementList) {
            System.out.format("%16s%16s%16s%16s%16s%16s%n", i, e.elementName, e.ref_state, e.mass, e.H298, e.S298);
            //Print.f("" + i + '\t' + e.elementName + '\t' + e.ref_state + '\t' + '\t' + e.mass + '\t' + e.H298 + '\t' + e.S298, 0);
            i = i + 1;
        }
        Print.f("List of " + numSpecies + " species", 0);
        System.out.format("%16s%16s%16s%n", "No", "Name", "Formula");
        i = 0;
        for (Species s : speciesList) {
            System.out.format("%16s%16s%16s%n", i, s.speciesName, s.formula);
            i = i + 1;
        }
        i = 0;
        Print.f("List of all symbols used in phase parameters:", 0);
        for (Function f : functionList) {
            System.out.println("" + i + '\t' + f.name + '\t' + f.T + '\t' + f.express);
            i = i + 1;
        }
        i = 0;
        Print.f("List of " + numPhases + " phases", 0);
        for (Phase p : phaseList) {
            p.print();
            i = i + 1;
        }
        Print.f("End of Listing data", 0);
        printSepLine();
    }

    public HashMap countElements(String formula) {
        HashMap<String, Integer> elements = new HashMap<>();
        Stack<Integer> mults = new Stack<>();

        StringBuilder element = new StringBuilder("");
        StringBuilder digits = new StringBuilder("");

        char currChar;
        int num = 1;
        int mult = 1;

        for (int i = formula.length() - 1; i >= 0; i--) {
            currChar = formula.charAt(i);

            if (Character.isDigit(currChar)) {
                digits.append(currChar);
            } else if (isLowerCase(currChar)) {
                element.append(currChar);
            } else {
                if (digits.length() == 0) {
                    num = 1;
                } else {
                    num = Integer.parseInt(digits.reverse().toString());
                }

                if (currChar == '(') {
                    mult /= mults.pop();
                } else if (currChar == ')') {
                    mults.push(num);
                    mult *= num;
                    digits.delete(0, digits.length());
                } else {
                    element.append(currChar).reverse();
                    elements.put(element.toString(), elements.getOrDefault(element.toString(), 0) + num * mult);
                    digits.delete(0, digits.length());
                    element.delete(0, element.length());
                }
            }
        }

//        StringBuilder output = new StringBuilder();
//        for (Map.Entry<String, Integer> entry : elements.entrySet()) {
//            output.append(entry.getKey() + ": " + entry.getValue() + "\n");
//        }
        //return output.toString();
        return (elements);

    }

    private boolean isLowerCase(char c) {
        return c >= 'a' && c <= 'z';
    }

    /*
    * This method checks if a item toCheckValue is present in the array arr
     */
    private boolean isPresent(String[] arr, String toCheckValue) {
        boolean test = false;
        for (String element : arr) {
            if (element == null ? toCheckValue == null : element.equals(toCheckValue)) {
                test = true;
                break;
            }
        }
        // Print the result 
        //System.out.println("Is " + toCheckValue + " present in the array: " + test);
        return test;
    }

    void printSepLine() {
        System.out.println("----------------------------------------------------");
    }

}
