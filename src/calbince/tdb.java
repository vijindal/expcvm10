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
import java.util.Arrays;
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

    public tdb() {//Constructor for initiating datbase structure
        System.out.println("readtdb called");
        this.elementList = new ArrayList<>();
        this.speciesList = new ArrayList<>();
        this.phaseList = new ArrayList<>();
    }

    public tdb(String tdbFileName) throws FileNotFoundException {//Constructor for initiating datbase structure and filled with tdbFileName file
        System.out.println("readtdb called with " + tdbFileName);
        this.elementList = new ArrayList<>();
        this.speciesList = new ArrayList<>();
        this.phaseList = new ArrayList<>();
        this.tdbFileName = tdbFileName;
        readFile();
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
                if (!str.startsWith("$")) { //ignore line strating with "$" sign
                    System.out.println();
                    //Print.f(str, 1);
                    if (str.endsWith("!")) { // check if keyword string is complete
                        keywordString = temp + str;
                        keywordString = keywordString.trim().replaceAll(" +", " ");// remove extra spces, needs to be checked !
                        Print.f("keyword:" + keywordString, 1);
                        keywordStringList = keywordString.split(" "); //splitting keywordString using space " " into words, needs to be checked for "!" as well
                        //Print.f("keywordStringList", keywordStringList, 0);
                        keyword = keywordStringList[0]; //fisrt word should be a keyword
                        Print.f(keyword, 0);
                        switch (keyword) {//code need to be modified for partial matches cases! one possible way is to trim both kewwords to be matched with minimum possible characters and then match
                            case "ELEMENT":
                                Element elementObj = new Element(keywordStringList);
                                elementList.add(elementObj);
                                break;
                            case "SPECIES":
                                Species speciesObj = new Species(keywordStringList);
                                speciesList.add(speciesObj);
                                break;
                            case "PHASE":
                                Phase phaseObj = new Phase();
                                phaseObj.Phase(keywordStringList);
                                phaseList.add(phaseObj);
                                break;
                            case "CONSTITUENT":
                            case "CONST":
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
                                break;
                            case "PARAMETER":
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
                        temp = temp + str;
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

        String phaseName;
        String dataTypeCode;
        int numSubLat; //Number of sublattices
        List<Float> numSites = new ArrayList<Float>(); //Number of sites of each of the sublattices 

        void Phase(String[] keywordStringList) throws IOException {
            this.phaseName = keywordStringList[1]; //some checks to be added for the Phase speciesName
            this.dataTypeCode = keywordStringList[2]; //some checks to be added for the dataTypeCode
            numSubLat = Integer.parseInt(keywordStringList[3]);
            for (int i = 0; i < numSubLat; i++) {
                numSites.add(Float.parseFloat(keywordStringList[4 + i]));
            }
            Print.f("phaseName:" + phaseName, 0);
            Print.f("numSubLat:" + numSubLat, 0);
            System.out.println(numSites);
            //Print.f("numSites:", numSites, 0);
        }
    }

    class Element {//better speciesName for this class!

        String elementName;
        String ref_state;
        double mass;
        double H298;
        double S298;

        private Element(String[] keywordStringList) throws IOException {
            this.elementName = keywordStringList[1]; //some checks to be added for the Phase speciesName
            this.ref_state = keywordStringList[2]; //some checks to be added for the dataTypeCode
            this.mass = Double.parseDouble(keywordStringList[3]);
            this.H298 = Double.parseDouble(keywordStringList[4]);
            this.S298 = Double.parseDouble(keywordStringList[5]);
            Print.f("elementName:" + elementName, 0);
            Print.f("refState:" + ref_state, 0);
            Print.f("mass:", mass, 0);
            Print.f("H298:", H298, 0);
            Print.f("S298:", S298, 0);
        }
    }

    private class Species {//better speciesName for this class!

        String speciesName;			//
        String formula;			//
        //double charge;			//

        private Species() {//Generic constructor method

        }

        private Species(String[] keywordStringList) throws IOException {
            this.speciesName = keywordStringList[1]; //some checks to be added for the Phase speciesName
            this.formula = keywordStringList[2]; //some checks to be added for the dataTypeCode
            //this.charge = Double.parseDouble(keywordStringList[3]);
            Print.f("name:" + speciesName, 0);
            Print.f("formula:" + formula, 0);
            //Print.f("charge:", charge, 0);
        }
    }

    private class Function {//better speciesName for this class!

        String name;			//
        List<Double> T;		//
        List<String> express;		//

        private void Function() {//Generic constructor method

        }

        private void Function(String[] keywordStringList) throws IOException {
            T = new ArrayList<Double>();
            express = new ArrayList<String>();
            this.name = keywordStringList[1]; //some checks to be added for the Phase speciesName
            //this.T = Double.parseDouble(keywordStringList[2]); //some checks to be added for the dataTypeCode
            //this.express = keywordStringList[3];
            Print.f("name:" + name, 0);
            //Print.f("formula:" + formula, 0);
            //Print.f("charge:", charge, 0);
        }
    }

    private class Parameter {

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
        String phasename;
        String type;
        String[] con = new String[10];// 10 sublattices, element in each sublattice is a vector
        // ele id in Phase constitution£¬eg, constituent :Al,Mg,Zn:Zn,Va:
        // para G(BCC,Al:Zn,0),become G(BCC,1:4,0)
        int yn = 0;		// constituent number
        int yidc[] = new int[MDim * 3];	// constituent id
        int yids[] = new int[MDim * 3];// sublattice id
        int vyn = 0;	 // varible y number
        int vyidc[] = new int[MDim * 3]; // constituent id
        int vyids[] = new int[MDim * 3]; // sublattice id
        int vyidv[] = new int[MDim * 3];// para's vy in Phase's vy id
        double[] T;		//
        double[] express;			//
        //express_digit express_digit[10]; // 10 T segment, terms in each segment is a vector 

        void paramter() {

        }

    }

    private class TypeDefinition {

        String label;			//
        String model;			//
        String command;			//
        String phasename;		//
        String property;		//
        String disname;
        double value1;			//
        double value2;			//
    };

//    try {
//    
//    String currentDirectory = System.getProperty("user.dir");//vj-15-03-12
//            fin = new FileInputStream(currentDirectory + "/../data/unary.dat");//vj-15-03-12
//            DataInputStream in = new DataInputStream(fin);
//            BufferedReader br = new BufferedReader(new InputStreamReader(in));
//            String str;
//            Boolean isStartReading = false, isTemp = false, isEqn = false, quit = false;
//    
//    }catch (FileNotFoundException e) {
//            System.err.println("File Not Found");
//    }
    public int istdbkeyword(String word, int nextc) {
        List<String> words = Arrays.asList("ELEMENT", "SPECIES", "PHASE", "CONSTITUENT", "FUNCTION", "PARAMETER", "SPECIAL", "BIBLIOGRAPHY",
                "TABLE_OF_MODELS", "TABLE_OF_IDENTIFIERS", "DATABASE_INFORMATION", "ASSESSED_SYSTEMS", "DEFAULTS", "VERSION", "INCLUDE_FILE", "CHECKSUM");
        System.out.println("Index of word: " + words.indexOf(word));
        return (0);

    }
}
