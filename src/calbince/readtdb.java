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
public class readtdb {

    private int nel; // no of elements
    private int keww;   //keywords
    String tdbFileName;
    List<phase> phaseList;
    List<element> elementList;

    public readtdb(String tdbFileName) {
        System.out.println("readtdb called with " + tdbFileName);
        this.phaseList = new ArrayList<>();
        this.elementList = new ArrayList<>();
        this.tdbFileName = tdbFileName;
    }

    public void read() throws FileNotFoundException {
        try {
            FileInputStream fin = new FileInputStream(tdbFileName);//vj-15-03-12
            DataInputStream in = new DataInputStream(fin);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String str;
            String keywordString = "";
            String[] keywordStringList;
            String temp = "";
            String keyword;
            List<String> KeywordList = new ArrayList<>();
            while ((str = br.readLine()) != null) { //reading each line of the file
                if (!str.startsWith("$")) { //ignore line strating with "$" sign
                    //System.out.println();
                    //Print.f(str, 1);
                    if (str.endsWith("!")) { // check if keyword string is complete
                        keywordString = temp + str;
                        keywordString = keywordString.trim().replaceAll(" +", " ");// remove extra spces, needs to be checked !
                        Print.f("keyword:" + keywordString, 1);
                        KeywordList.add(keywordString);//may be removed later
                        keywordStringList = keywordString.split(" "); //splitting keywordString using space " " into words
                        //Print.f("keywordStringList", keywordStringList, 0);
                        keyword = keywordStringList[0]; //fisrt word should be a keyword
                        Print.f(keyword, 0);
                        switch (keyword) {//code need to be modified for partial matches cases! one possible way is to trim both kewwords to be matched with minimum possible characters and then match
                            case "ELEMENT":
                                element elementObj = new element();
                                elementObj.enter(keywordStringList);
                                elementList.add(elementObj);
                                break;
                            case "SPECIES":
                                break;
                            case "PHASE":
                                phase phaseObj = new phase();
                                phaseObj.enter(keywordStringList);
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
            Logger.getLogger(readtdb.class.getName()).log(Level.SEVERE, null, ex);
        }

        //System.out.println(phaseList.size());
        System.out.println(elementList.size());
    }

    class phase {//better name for this class!

        String phaseName;
        String dataTypeCode;
        int numSubLat; //Number of sublattices
        List<Float> numSites = new ArrayList<Float>(); //Number of sites of each of the sublattices 

        void enter(String[] keywordStringList) throws IOException {
            this.phaseName = keywordStringList[1]; //some checks to be added for the phase name
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

    class element {//better name for this class!

        String elementName;
        String refState;
        double mass;
        double H298;
        double S298;

        void enter(String[] keywordStringList) throws IOException {
            this.elementName = keywordStringList[1]; //some checks to be added for the phase name
            this.refState = keywordStringList[2]; //some checks to be added for the dataTypeCode
            this.mass = Double.parseDouble(keywordStringList[3]);
            this.H298 = Double.parseDouble(keywordStringList[4]);
            this.S298 = Double.parseDouble(keywordStringList[5]);
            Print.f("elementName:" + elementName, 0);
            Print.f("refState:" + refState, 0);
            Print.f("mass:", mass, 0);
            Print.f("H298:", H298, 0);
            Print.f("S298:", S298, 0);
        }
    }

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
