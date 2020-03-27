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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import utils.io.Print;

/**
 *
 * @author Vikas Jindal
 * @version 10.0 (current version number of program)
 * @since 10.0 (the version of the package this class was first added to)
 */
public class tdb {

    private String tdbFileName;
    private ArrayList<Element> elementList; //list of elements of Element type
    private ArrayList<Species> speciesList; //list of species of Species type
    private ArrayList<Phase> phaseList;     //list of phases of Phase type
    private ArrayList<Function> functionList;   //list of functions of Function type
    private ArrayList< TypeDefinition> typeDefinitionList;  //list of typeDefinitions of TypeDefinition type
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
        processExpressions();//This method will process expressions and substitute function expressions 
        System.out.println("tdb method is ended");
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

        for (String elementName : inputElementList) {// adding only those species which have elementName int its elementCount
            for (Species sysSpecies : speciesList) {
                if (sysSpecies.elementCount.containsKey(elementName)) {
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

    /*
    * This method returns parameter of a phase inputPhaseName of only elements which are in inputElementList 
    * @param inputElementList List of elments
    * @return   tdb      datbase
    * @since             10.0
     */
    public ArrayList<Parameter> getPhaseParam(ArrayList<String> inputElementList, String inputPhaseName) {
        //printSepLine();
        Print.f("gettdb method is called with:", inputElementList + ", and phase: " + inputPhaseName, 0);
        ArrayList<Parameter> sysparamList = new ArrayList<>();
        for (Phase p : phaseList) {//search phases that contains element(s) of inputElementList
            if (p.getPhaseName() == null ? inputPhaseName == null : p.getPhaseName().equals(inputPhaseName)) {
                sysparamList = p.getParam(inputElementList);
            }
        }
        Print.f("end of gettdb method", 0);
        //printSepLine();
        return (sysparamList);
    }

    private void readFile() throws FileNotFoundException {
        try {
            printSepLine();
            Print.f("readFile is called with tdb file: " + tdbFileName, 0);
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
        Print.f("readfile method ended", 0);
    }

    /**
     * This method will process expressions stored in Functions and Parameters
     * in the form of strings.It will read all the coefficients and terms and
     * will also substitute function expressions, if any
     */
    private void processExpressions() {
        Print.f("method processExpressions is called", 0);

        // read list of name of functions
        //using this list process function expressions
        for (Function f : functionList) {
            System.out.println("function name:" + f.funcName);
            for (Exp exp : f.expList) {
                ArrayList<Double> coeffList = new ArrayList<>();//contains coefficients of the terms in expStr
                ArrayList<String> funcList = new ArrayList<>(); //contains function names
                ArrayList<Double> funcCoeffList = new ArrayList<>(); //contains coefficients of the functions
                String expStr = exp.getExpStr();
                //System.out.println("expStr:" + expStr);
                readExpress(expStr, coeffList, funcList, funcCoeffList);
                exp.setCoeffList(coeffList);
                exp.setFuncList(funcList);
                exp.setFuncCoeffList(funcCoeffList);
                //System.out.println("coeffList:" + coeffList);
                //System.out.println("funcList:" + funcList);
                //System.out.println("funcCoeffList:" + funcCoeffList);
            }
        }
        //substitute function expressions if required
        for (Function f : functionList) {//loop over function expressions
            System.out.println("function name:" + f.funcName);
            for (Exp exp : f.expList) {//loop over individual expressions
                ArrayList<String> funcList = exp.getFuncList(); //contains function names
                if (!funcList.isEmpty()) {
                    //System.out.println("FuncList:" + funcList);
                    ArrayList<Double> coeffList = exp.getCoeffList();//contains coefficients of the terms in expStr
                    ArrayList<Double> funcCoeffList = exp.getFuncCoeffList(); //contains coefficients of the functions
                    for (String func : funcList) {//loop over individual function
                        System.out.println("Func:" + func);
                        Function function;//searching Function by function name
                        function = findFuncByName(func);
                        ArrayList<Exp> expList = function.getExpList();
                        //Sattle temperature range issue
                    }
                }
            }
        }
        //process parameters expressions
        //substitute function expressions if required
        Print.f("method processExpressions is ended", 0);
    }

    /**
     *
     */
    private Function findFuncByName(String funName) {
        Function function = null;
        for (Function fun : functionList) {//loop over function list
            if (funName.equals(fun.funcName)) {//finding match
                function = fun;
            }
        }
        return function;
    }

    /**
     * This class contains record for a phase such as phaseName, phaseType,
     * model, constituentList, paramList. Methods include various getter methods
     */
    public class Phase {//better speciesName for this class!
        //PHASE LIQUID:L %  1  1.0  !
        //PHASE ALMNSI_ALPHA  %  4 16   4   1   2 !

        private String phaseName;
        private String phaseType = "";
        private String model;
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
            String[] keywordStringList = keywordStringLine.trim().split("\\s+");//{ALMNSI_ALPHA,  %,  4, 16,   4,   1,   2, !}
            //Print.f("keywordStringList:", keywordStringList, 0);
            String[] tempList = splitString(keywordStringList[0], ":");//{ALMNSI_ALPHA},{LIQUID,L}
            //Print.f("tempList:", tempList, 0);
            this.phaseName = tempList[0]; //ALMNSI_ALPHA or 
            //Print.f("phaseName:" + phaseName, 0);
            //Print.f("tempList.length:" + tempList.length, 0);
            if (tempList.length > 1) {
                this.phaseType = tempList[1];
                //Print.f("phaseType:" + phaseType, 0);
            } else {
                switch (phaseName) {
                    case "GAS":
                        phaseType = "G";
                        model = "IDEAL";
                        break;
                    case "LIQUID":
                        phaseType = "L";
                        model = "RKM";
                        break;
                    case "IONIC_LIQ":
                        phaseType = "L";
                        model = "IONIC_LIQUID";
                        break;
                    case "SRO":
                        phaseType = "S";
                        model = "CVM";
                        break;
                    default:
                        phaseType = "S";
                        model = "CEF";
                        break;
                }
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

        /**
         * This Method return list of Parameters having constituent List consist
         * of elementList only
         *
         * @param elementList List of elements
         * @return list of Parameters
         * @since 10.0
         */
        public ArrayList<Parameter> getParam(String[] elementList) {
            //Print.f("getParam method is called with:" + Arrays.toString(elementList), 0);
            ArrayList<Parameter> sysparamList = new ArrayList<>();//for storing parameter having constituent List consist of elementList only
            ArrayList<ArrayList<String>> tempList;
            boolean isOtherElement;
            for (Parameter p : paramList) {
                isOtherElement = false;
                tempList = p.constituentList;
                //Print.f("tempList:", tempList, 0);
                for (ArrayList<String> tempList1 : tempList) {//loop over row
                    for (String item : tempList1) {//loop over column
                        //Print.f(" item: " + item, 0);
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

        /**
         * This Method return list of Parameters having constituent List consist
         * of elementList only. This method only differs from another version of
         * getParam in terms of input elementList type
         *
         * @param elementList List of elements
         * @return list of Parameters
         * @since 10.0
         */
        public ArrayList<Parameter> getParam(ArrayList<String> elementList) {
            //Print.f("getParam method is called with:" + Arrays.toString(elementList), 0);
            ArrayList<Parameter> sysparamList = new ArrayList<>();//for storing parameter having constituent List consist of elementList only
            ArrayList<ArrayList<String>> tempList;
            boolean isOtherElement;
            for (Parameter p : paramList) {
                isOtherElement = false;
                tempList = p.constituentList;
                //Print.f("tempList:", tempList, 0);
                for (ArrayList<String> tempList1 : tempList) {//loop over row
                    for (String item : tempList1) {//loop over column
                        //Print.f(" item: " + item, 0);
                        if (!"VA".equals(item)) {
                            if (!elementList.contains(item)) {
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

        public void print() {
            System.out.print("----------------------------");
            System.out.print("phase:" + phaseName + '\t' + phaseType);
            System.out.print("Subl: " + numSubLat + '\t');
            System.out.println(Arrays.toString(numSites));
            System.out.println("Constituents: " + constituentList);
            for (Parameter p : this.paramList) {
                p.print();
            }
        }
    }

    public Phase getPhase(String phasename) {
        Phase phase = null;
        for (Phase x : phaseList) // find in phaseList
        {
            if (x.phaseName == null ? phasename == null : x.phaseName.equals(phasename)) {
                phase = x;
            }
        }
        if (phase == null) {
            System.out.println("phaseName: " + phasename + " not found !");
        }
        return phase;
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

    /**
     * This class handles members (data and methods) for Species composed of
     * elements such as H2O specified by name, formula, elements list and
     * element count
     */
    class Species {//better speciesName for this class!

        String speciesName;			//
        String formula;			//
        //double charge;			//
        List<String> elmntList;
        HashMap elementCount;

        Species(String keywordStringLine) throws IOException {
            elmntList = new ArrayList<>();
            String[] keywordStringList = keywordStringLine.trim().split("\\s+");
            this.speciesName = keywordStringList[0]; //some checks to be added for the Phase speciesName
            keywordStringList[1] = keywordStringList[1].trim().split("!")[0];
            this.formula = keywordStringList[1]; //some checks to be added for the dataTypeCode
            elementCount = countElements(formula);
            //Print.f(elementCount.toString(), 0);
            //this.charge = Double.parseDouble(keywordStringList[3]);
            //Print.f("name:" + speciesName, 0);
            //Print.f("formula:" + formula, 0);
            //Print.f("charge:", charge, 0);
        }
    }

    class Function {//better speciesName for this class!

        private String funcName;			//function name
        private ArrayList<Double> tempRange;	//
        private ArrayList<String> expStringList;         // for storing expression strings 
        private ArrayList<Exp> expList;         //

        Function() {//Generic constructor method
            tempRange = new ArrayList<>();
            expList = new ArrayList<>();
            expStringList = new ArrayList<>();
        }

        Function(String keywordLine) throws IOException {
            //Print.f("****Function is called with:" + keywordLine, 0);
            tempRange = new ArrayList<>();
            expList = new ArrayList<>();
            expStringList = new ArrayList<>();
            Exp exp;
            String endmarkSpace = " ";
            String endMarkSemiColon = ";";
            int numRange;//number of temperature ranges
            String[] splitKeywordLine;
            String[] splitExpressLine;
            splitKeywordLine = splitString(keywordLine.trim(), endmarkSpace);// get function name 
            this.funcName = splitKeywordLine[0];
            keywordLine = splitKeywordLine[1].trim();//remaining keywordLine
            splitKeywordLine = splitString(keywordLine, endmarkSpace);//get lower tempRange limit
            this.tempRange.add(Double.parseDouble(splitKeywordLine[0].trim()));
            keywordLine = splitKeywordLine[1].trim();//remaining keywordLine
            splitKeywordLine = keywordLine.split(endMarkSemiColon);//to get expressions
            //Print.f("splitKeywordLine:", splitKeywordLine, 0);
            numRange = splitKeywordLine.length;
            //Print.f("numRange:", numRange, 0);
            expStringList.add(splitKeywordLine[0].trim());// adding string to the array
            //exp = new Exp(splitKeywordLine[0].trim());// exp object is called with keyword line
            //this.expList.add(exp);
            for (int i = 1; i < numRange - 1; i++) {
                splitExpressLine = splitString(splitKeywordLine[i].trim(), endmarkSpace);// get tempRange
                this.tempRange.add(Double.parseDouble(splitExpressLine[0].trim()));//Add tempRange
                splitKeywordLine[i] = splitExpressLine[1].trim();//remaining ExpressLine
                splitExpressLine = splitString(splitKeywordLine[i].trim(), endmarkSpace);// split to get word "Y/N" and expression
                expStringList.add(splitKeywordLine[0].trim());// adding string to the array
                //exp = new Exp(splitExpressLine[1].trim());
                //this.expList.add(exp);//add expression
            }
            splitExpressLine = splitString(splitKeywordLine[numRange - 1].trim(), endmarkSpace);// get tempRange
            this.tempRange.add(Double.parseDouble(splitExpressLine[0].trim()));//Add tempRange
            //System.out.println("tempRange:" + tempRange);
            //System.out.println("expStringList:" + expStringList);
            for (int i = 0; i < numRange - 1; i++) {
                exp = new Exp(expStringList.get(i), tempRange.get(i), tempRange.get(i + 1));// exp object is called with keyword line
                this.expList.add(exp);
            }
            //Print.f("****Function is ended with funcName:" + funcName, 0);
        }

        public String getFuncName() {
            return funcName;
        }

        public ArrayList<Double> getTempRange() {
            return tempRange;
        }

        public ArrayList<Exp> getExpList() {
            return expList;
        }
    }

    /**
     * This class handles expression
     */
    class Exp {

        private String expStr;//expression String
        private ArrayList<Double> coeffList;//contains coefficients of the terms in expStr
        private ArrayList<String> funcList; //contains terms of the expStr
        private ArrayList<Double> funcCoeffList;
        private ArrayList<Double> tempRange;

        Exp(String inputExp) throws IOException {// this method calls readExpress method 
            this.expStr = inputExp;
            this.funcList = new ArrayList<>();
            this.coeffList = new ArrayList<>();
            this.funcCoeffList = new ArrayList<>();
            //readExpress(inputExp, coeffList, funcList, funcCoeffList);//readExpress is called with expression to be read,list of coeffs,list of functions, output is returned in funcCoeffList
        }

        Exp(String inputExp, double t1, double t2) throws IOException {// this method calls readExpress method 
            this.expStr = inputExp;
            this.funcList = new ArrayList<>();
            this.coeffList = new ArrayList<>();
            this.funcCoeffList = new ArrayList<>();
            this.tempRange = new ArrayList<>();
            tempRange.add(t1);
            tempRange.add(t2);
            //System.out.println("expStr:" + expStr);
            //System.out.println("tempRange:" + tempRange);
            //readExpress(inputExp, coeffList, funcList, funcCoeffList);//readExpress is called with expression to be read,list of coeffs,list of functions, output is returned in funcCoeffList
        }

        public String getExpStr() {
            return (expStr);
        }

        public ArrayList<Double> getCoeffList() {
            return (coeffList);
        }

        public ArrayList<String> getFuncList() {
            return (funcList);
        }

        public ArrayList<Double> getFuncCoeffList() {
            return (funcCoeffList);
        }

        public void setCoeffList(ArrayList<Double> coeffList) {
            this.coeffList = coeffList;
        }

        public void setFuncList(ArrayList<String> funcList) {
            this.funcList = funcList;
        }

        public void setFuncCoeffList(ArrayList<Double> funcCoeffList) {
            this.funcCoeffList = funcCoeffList;
        }
    }

    /*
    This class handle Paramters, contructor method reads keywords line and fill values 
     */
    public class Parameter {

        int order;
        String type;
        String phasename;
        int phaseId;
        ArrayList<ArrayList<String>> constituentList;// 10 sublattices, e in each sublattice is a vector// ele id in Phase constitution£¬eg, constituent :Al,Mg,Zn:Zn,Va:
        ArrayList<Double> T;		//
        ArrayList<Exp> express;		//	//

        Parameter(String keywordLine) throws IOException {
            //Print.f("Parameter is called with:" + keywordLine, 0);
            //printPhaseList();
//          PARAMETER G(HCP_A3,AG,CU:VA;0) 298.15 +35000-8*tempRange; 6000     N REF135 !
            T = new ArrayList<>();
            express = new ArrayList<>();
            constituentList = new ArrayList<>();
            Exp exp;
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
            templineR = tempList[1];//Reamining Line 298.15 +35000-8*tempRange; 6000     N REF135 !
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
            tempList = splitString(keywordLine.trim(), endmarkSpace);//get lower tempRange limit
            //Print.f("tempList:", tempList, 0);
            this.T.add(Double.parseDouble(tempList[0].trim()));
            keywordLine = tempList[1].trim();//remaining kewwordLine
            tempList = keywordLine.split(endMarkSemiColon);//to get expressions
            //Print.f("splitKeywordLine:", splitKeywordLine, 0);
            int numRange = tempList.length;
            //Print.f("numRange:", numRange, 0);
            exp = new Exp(tempList[0].trim());
            this.express.add(exp);
            for (int i = 1; i < numRange - 1; i++) {
                //Print.f("expressLine:" + splitKeywordLine[i], 0);
                splitExpressLine = splitString(tempList[i].trim(), endmarkSpace);// get tempRange
                this.T.add(Double.parseDouble(splitExpressLine[0].trim()));//Add tempRange
                tempList[i] = splitExpressLine[1].trim();//remaining ExpressLine
                splitExpressLine = splitString(tempList[i].trim(), endmarkSpace);// split to get word "Y/N" and expression
                exp = new Exp(splitExpressLine[1].trim());
                this.express.add(exp);//add expression
            }
            //Print.f("expressLine:" + splitKeywordLine[numRange - 1], 0);
            splitExpressLine = splitString(tempList[numRange - 1].trim(), endmarkSpace);// get tempRange
            this.T.add(Double.parseDouble(splitExpressLine[0].trim()));//Add tempRange
        }

        public void print() {
            System.out.print("Paramter" + '\t' + phaseId + '\t' + type + '\t' + phasename + '\t' + order + '\t' + constituentList.toString() + '\t' + T.toString() + '\t');
            for (Exp exp : express) {
                System.out.print(exp.expStr + '\t');
            }
            System.out.println();
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

    public void printPhaseList() throws IOException {
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
            System.out.println("" + i + '\t' + f.funcName + '\t' + f.tempRange + '\t' + f.expList);
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
        // Print the temStrList 
        //System.out.println("Is " + toCheckValue + " present in the array: " + test);
        return test;
    }

    void printSepLine() {
        System.out.println("----------------------------------------------------");
    }

    /*
    * This method process an expression in the .tdb file. It read coefficiens of
    the following terms and store them in the cmat array:
        Constant, tempRange, tempRange*Log(tempRange), tempRange^2, tempRange^3, tempRange^4, tempRange^7, tempRange^-1, tempRange^-2, tempRange^-3, tempRange-9
    @param  expression
    @return coefficints of the terms
    @since 10.0
     */
    private double[] getCoeff(String str) throws IOException {
        Print.f("sgte.getCmat method called", 7);
        int cmatMaxLength = 11;
        double[] cmat_local = new double[cmatMaxLength];
        //prnt.f("////" + str + "////");
        if (str.endsWith(";")) {
            str = str.substring(0, str.length() - 1);
        }
        String delims = "[+]";
        String temp[] = str.split(delims);
        Print.f("temp", temp, 0);
        delims = "[-]";
        int k = 0;
        String store[] = new String[25];
        for (String temp1 : temp) {
            String[] temp2 = temp1.split(delims);
            for (int j = 0; j < temp2.length; j++) {
                if (j == 0) {
                    store[k] = temp2[j];
                } else {
                    store[k] = "-" + temp2[j];
                }
                k++;
            }
        }
        Print.f("Store", store, 0);
        for (int i = 1; i < store.length; i++) {
            if (store[i] == null) {
                break;
            }
            if (store[i - 1].endsWith("E")) {
                //Print.f("E\ti\t" + store[i] + "\ti-1\t" + store[i - 1], 0);
                store[i - 1] = store[i - 1] + store[i];
                for (int j = i; j < store.length; j++) {
                    if (store[j] == null) {
                        break;
                    }
                    store[j] = store[j + 1];
                }
            }
            if (store[i - 1].endsWith("(")) {
                //Print.f("E\ti\t" + store[i] + "\ti-1\t" + store[i - 1], 0);
                store[i - 1] = store[i - 1] + store[i];
                for (int j = i; j < store.length; j++) {
                    if (store[j] == null) {
                        break;
                    }
                    store[j] = store[j + 1];
                }
            }
        }
        //prnt.f("FLAG//"+ store[0]+"//");
//        Print.f(store[0]);
//        Print.f(store[1]);
        for (int i = 0; i < 2; i++) {
            if (store[0].equalsIgnoreCase("-") || store[0].isEmpty()) {
                //prnt.f("FLAG");
                for (int j = 0; j < store.length; j++) {
                    if (store[j] == null) {
                        break;
                    }
                    store[j] = store[j + 1];
                }
            }
        }
        Print.f("Store", store, 0);
        //prnt.f(store[0]);
        //prnt.f(store[1]);
        cmat_local[0] = Double.parseDouble(store[0]);
        for (int i = 1; i < store.length; i++) {
            if (store[i] == null) {
                break;
            }
            delims = "[*]+";
            temp = store[i].split(delims);

            if (temp[1].equalsIgnoreCase("T")) {
                if (temp.length == 2) {
                    cmat_local[1] = Double.parseDouble(temp[0]);
                } else {
                    if (temp[2].endsWith(")")) {
                        temp[2] = temp[2].substring(0, temp[2].length() - 1);
                    }
                    if (temp[2].startsWith("(")) {
                        temp[2] = temp[2].substring(1, temp[2].length());
                    }
                    if (isInt(temp[2])) {
                        double value = Double.parseDouble(temp[0]);
                        //Print.f("value: " + value, 0);
                        int index = Integer.parseInt(temp[2]);
                        //cmat[11] = {Constant, tempRange, tempRange*Log(tempRange), tempRange^2, tempRange^3, tempRange^4, tempRange^7, tempRange^-1, tempRange^-2, tempRange^-3, tempRange-9}
                        switch (index) {
                            case 2:
                                cmat_local[3] = value;
                                break;
                            case 3:
                                cmat_local[4] = value;
                                break;
                            case 4:
                                cmat_local[5] = value;
                                break;
                            case 7:
                                cmat_local[6] = value;
                                break;
                            case -1:
                                cmat_local[7] = value;
                                break;
                            case -2:
                                cmat_local[8] = value;
                                break;
                            case -3:
                                cmat_local[9] = value;
                                break;
                            case -9:
                                cmat_local[10] = value;
                                break;
                            default:
                                Print.f("NO case for No. " + index, 0);
                        }
                    } else {
                        cmat_local[2] = Double.parseDouble(temp[0]);
                    }
                }
            }
        }
        //prnt.f(cmat_local, "cmat_local", 0);
        //System.exit(1);
        Print.f("sgte.getCmat method ended", 7);
        return (cmat_local);
    }// Closed Method getCoeff()

    /*
    *This method simplify string for parsing
    @param  s input string
    @return ss simplified string
    @since 10.0
     */
    public String sympifyString(String s) {//convert to private method !
        //System.out.println("s: " + s);
        String ss;
        //make string upper case;
        ss = s.toUpperCase();
        //remove all white spaces
        ss = ss.replace(" ", "");
        //drop pound symbols ('#') since they denote function names
        ss = ss.replace("#", "");
        //Replace LN as lOG
        ss = ss.replace("LN", "LOG");
        //System.out.println("ss: " + ss);
        return (ss);
    }

    /*
        this method reads coefficient at the end of input str 
     */
    private double readCoeff(String[] str, int index) {
        int k = 0;
        double temp;
        String numStr;
        String tempStr = str[index];
        //System.out.println("(before)tempStr : " + tempStr);
        for (k = (tempStr.length() - 1); k > 0; k--) {
            if ((tempStr.charAt(k) == '+') || (tempStr.charAt(k) == '-')) {
                if ((tempStr.charAt(k - 1) == 'E')) {
                    if (k == 1) {
                        break;
                    }
                    if (k > 1) {
                        char c = tempStr.charAt(k - 2);
                        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
        }
        //System.out.println("Length of the String: " + tempStr.length() + ", k:" + k);
        numStr = tempStr.substring(k).trim();
        //System.out.println("numStr: " + numStr);
        if ("+".equals(numStr)) {
            temp = 1.0;
        } else if ("-".equals(numStr)) {
            temp = -1.0;
        } else {
            temp = Double.parseDouble(numStr.split("\\*")[0]);
        }
        tempStr = tempStr.substring(0, k);
        str[index] = tempStr;
        //System.out.println("(after)str: " + str[index]);
        //System.out.println("temp: " + temp);
        return (temp);
    }

    /*
    
     */
    private boolean isContained(String str, String pattern) {
        //System.out.println("isContained is called with str: " + str + " and pattern: " + pattern);
        boolean flag = false;
        int firstIndex = str.indexOf(pattern);
        if (firstIndex != -1) {
            int lastIndex = firstIndex + pattern.length() - 1;
            System.out.println("firstIndex: " + firstIndex + ", lastIndex:" + lastIndex + ", strlen:" + str.length());
            if (firstIndex == 0) {
                if (lastIndex == str.length() - 1) {
                    flag = true;
                } else {
                    flag = (str.charAt(lastIndex + 1) == '+') | (str.charAt(lastIndex + 1) == '-') | (str.charAt(lastIndex + 1) == '*');
                }
            } else {
                if (((str.charAt(firstIndex - 1) == '+') | (str.charAt(firstIndex - 1) == '-') | (str.charAt(firstIndex - 1) == '*'))) {
                    if (lastIndex == str.length() - 1) {
                        flag = true;
                    } else {
                        flag = (str.charAt(lastIndex + 1) == '+') | (str.charAt(lastIndex + 1) == '-') | (str.charAt(lastIndex + 1) == '*');
                    }
                } else {
                    flag = false;
                }
            }
        }
        //System.out.println("isContained is called with the flag: " + flag);
        return (flag);
    }

    /*
    This method read functions from a expression based on functionList and returns 
    a list of functions and thier coefficients.
    This method searches first Function name and split the string based on function name.
    Now string other than last one carries coeffient of the function hance it keep last part of the string intact and
    take out coeffient from the first (n-1) strings. And lastly add them all.
     //+117369-24.63*tempRange+GHSERCC#+GPCLIQ#
        GHSERCC found will results in {+117369-24.63*tempRange+, +GPCLIQ}
        +117369-24.63*tempRange+ carries coefficient + > 1.0
     */
    private String readFunc(String inputExp, ArrayList<String> funcList, ArrayList<Double> funcCoeffList) {
        inputExp = this.sympifyString(inputExp);
        System.out.println("readFunc is called with: " + inputExp);
        String[] tempList;
        int k = 0;
        double temp, tempSum;
        String numStr, tempStr;
        boolean found;
        //Reading functions in the inputExp
        tempStr = inputExp.trim();
        for (Function f : functionList) {
            tempSum = 0.0;
            if (isContained(inputExp, f.funcName)) {
                //System.out.println("found the the function:" + f.funcName + ", inputExp before process: " + tempStr);//split inputExp based on function name
                tempList = tempStr.trim().split(f.funcName);
                //System.out.println("tempList.length: " + tempList.length);
                switch (tempList.length) {
                    case 0://input string is function name itself
                        tempSum = 1; //for loop should be broken here
                        tempStr = "";
                        break;
                    case 1://function name is in the last position only first term to be searched
                    {//function name is in the last position
                        //System.out.println("case 1 before readCoeff, tempList[0]:" + tempList[0]);
                        tempSum = readCoeff(tempList, 0);
                        //System.out.println("case 1 after readCoeff,tempList[0]:" + tempList[0]);
                        tempStr = tempList[0];
                    }
                    break;
                    default:
                        //function name is in the first or middle positions, only (n-1) terms to be searched
                        if (tempList[0].isEmpty()) {//function name is in the first position
                            tempSum = 1;
                            tempStr = tempList[1];
                        } else {//middle positions, only (n-1) terms to be searched
                            tempStr = "";
                            for (int i = 0; i < tempList.length - 1; i++) {
                                //System.out.println("case 22 before readCoeff, tempList[i]:" + tempList[i]);
                                tempSum = tempSum + readCoeff(tempList, i);
                                //System.out.println("case 22 after readCoeff,tempList[i]:" + tempList[i]);
                                tempStr = tempStr + tempList[i];
                            }
                            tempStr = tempStr + tempList[tempList.length - 1];
                        }
                        break;
                }
                funcList.add(f.funcName);
                funcCoeffList.add(tempSum);
                //System.out.println("found the the function ended with inputExp after process: " + tempStr);
            }//end of if loop
        }//end of function loop
        //System.out.println(funcList);
        //System.out.println(funcCoeffList);
        System.out.println("readFunc is ended");
        return (tempStr);
    }//end of readfunc function

    /**
     * @param inputExp This method reads coefficients of the following terms
     * from the input String and store them in the array: Constant, *tempRange,
     * tempRange*Log(tempRange), *tempRange**2, *tempRange**3, *tempRange**4,
     * *tempRange**7, *tempRange**(-1), *tempRange**(-2), *tempRange**(-3),
     * tempRange**(-9)
     */
    private void readTerms(String inputExp, ArrayList<Double> coeffList) {
        System.out.println("readTerms is called with inputExp : " + inputExp);
        String[] terms = {"1", "*T", "*T*LOG(T)",
            "*T**2", "*T**3", "*T**4", "*T**7", "*T**(-1)", "*T**(-2)",
            "*T**(-3)", "*T**(-9)"};
        String[] temStrList;
        Pattern pattern;
        Matcher m;
        double[] coeffArr = new double[11];
        int k;
        for (int i = 10; i > 0; i--) {
            pattern = Pattern.compile(Pattern.quote(terms[i]));
            m = pattern.matcher(inputExp);
            if (m.find()) {
                //System.out.println("Found a match for " + terms[i]);
                temStrList = pattern.split(inputExp);
                //System.out.println("result[0]: " + temStrList[0]);
                for (k = (temStrList[0].length() - 1); k > 0; k--) {
                    if ((temStrList[0].charAt(k) == '+') || (temStrList[0].charAt(k) == '-')) {
                        if (temStrList[0].charAt(k - 1) != 'E') {
                            break;
                        }
                    }
                }
                String numStr = temStrList[0].substring(k).trim();
                //System.out.println("numStr: " + numStr);
                if (numStr.length() > 0) {
                    coeffArr[i] = Double.parseDouble(numStr);
                } else {
                    coeffArr[i] = 1.0;
                }
                //System.out.println(coeffArr[i]);
                temStrList[0] = temStrList[0].substring(0, k);
                if (temStrList.length > 1) {
                    inputExp = temStrList[0] + temStrList[1];
                } else {
                    inputExp = temStrList[0];
                }
                //System.out.println("remaining express: " + inputExp);
            } else {
                coeffArr[i] = 0.0;
            }

        }
        for (double d : coeffArr) {
            coeffList.add(d);
        }
        //System.out.println("express: " + inputExp);
        if (inputExp.length() > 0) {//remaining expList is Constant
            coeffList.set(0, Double.parseDouble(inputExp));
        }
        System.out.println("readTerms is ended");
    }

    /**
     * This method process an expression in the .tdb file. It read coefficients
     * of the following terms and store them in the cmat array: Constant, *T,
     * *T*Log(T), *T**2, *T**3, *T**4, *T**7, *T**(-1), *T**(-2), *T**(-3),
     * *T**(-9)
     *
     * @param inputExp
     * @param coeffList
     * @param funcList
     * @return coefficients of the terms in the arrayList funcCoeffList
     * @since 10.0
     */
    private void readExpress(String inputExp, ArrayList<Double> coeffList, ArrayList<String> funcList, ArrayList<Double> funcCoeffList) {
        //+20764.117-9.455552*tempRange-5.19136E-22*tempRange**7+GHSERVV#
        //-16635.628+62.699624*tempRange-18.9536*tempRange*LN(tempRange)-0.425243E-3*tempRange**2+0.010721E-6*tempRange**3+4383200*tempRange**(-1)
        // +GHSERCR#+3*GHSERCC#+GPCRBCC#+3*GPCGRA#+416000
        //3100.00-2.1*tempRange+GHSERMG
        //+11005.553-11.840873*tempRange+7.9401E-20*tempRange**7+GHSERAL#
        //+117369-24.63*tempRange+GHSERCC#+GPCLIQ#
        Print.f("readExpress is called with inputExp:" + inputExp, 0);
        //System.out.println("coeffList:" + coeffList);
        //System.out.println("funcList:" + funcList);
        //System.out.println("funcList:" + funcList);
        inputExp = this.sympifyString(inputExp);
        //called readFunc method
        inputExp = readFunc(inputExp, funcList, funcCoeffList);// this method finds all the functions in inputExp based on funcList and stores in funcCoeffList 
        readTerms(inputExp, coeffList);//this method reads coefficients in inputExp and stores in coeffList
        //System.out.println("funcList: " + funcList);
        //System.out.println("funcCoeffList: " + funcCoeffList);
        //System.out.println("coeffList: " + coeffList);
        Print.f("readExpress is ended", 0);
    }

    private boolean isInt(String in) {
        try {
            Integer.parseInt(in);
            //Double.parseDouble(in);
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }// Closed Method isInt()

    int findendmarkforward(String express, String endmark, String endmark_array[], int n) {
        int i;
        for (i = express.indexOf(endmark); i >= 0; i--) {
            for (int j = 0; j < n; j++) {
                if (express.charAt(i) == endmark_array[j].charAt(0)) {
                    if (i > 0) {
                        if (express.charAt(i - 1) != 'E' && express.charAt(i - 1) != '(') {
                            return i;
                        }
                    } else {
                        return i;
                    }

                }
            }
        }
        return -1;
    }
}
