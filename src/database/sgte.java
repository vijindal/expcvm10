/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package database;

import utils.io.Print;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import java.util.ArrayList;

/*
 * @author JEDIABJ77 @editied AJ-2012-04-09
 *
 * For get method: @param E : string type for Element Symbol e.g. "Cr" @param
 * phase : string type for Phase type e.g. "A2" @param T: double type for
 * Temperature @param typ: integer type for type of calculation 0 -> calG() 1 ->
 * calGT() 2 -> calGTT()
 *
 * Index for coefficient matirx (cmat): cmat[0] : Constant cmat[1] : T cmat[2] :
 * T*Log(T) cmat[3] : T^2 cmat[4] : T^3 cmat[5] : T^4 cmat[6] : T^7 cmat[7] :
 * T^-1 cmat[8] : T^-2 cmat[9] : T^-3 cmat[10]: T^-9
 */

 /*
 * @author Mani @edited mani-2013-04-03
 * 
 * Whenever a new phase for an element is required it is read from the sgte unary
 * file and is strored in a arraylist which can be used later rather than reading
 * freshly from the file each time
 */
public class sgte {

    private static final int cmatMaxLength = 11;

    private static class elementData {

        public String element_phase = null;
        public String[] gEqn = new String[10];
        public double[] temp = new double[10];
        public int nTemp;
    };
    private static ArrayList<elementData> Data = new ArrayList(0);
    //get methods

    public static int sgteReader(String element, String phase) throws IOException {
        Print.f("sgte.sgteReader method called with " + element + ", " + phase, 7);
        FileInputStream fin;
        elementData elementdata = new elementData();
        elementdata.element_phase = element + "-" + phase;
        //String element = "Cr";
        //String phase = "BCC_A2";
        String eqn = "";
        try {
            String currentDirectory = System.getProperty("user.dir");//vj-15-03-12
            fin = new FileInputStream(currentDirectory + "/../data/unary.dat");//vj-15-03-12
            DataInputStream in = new DataInputStream(fin);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String str;
            Boolean isStartReading = false, isTemp = false, isEqn = false, quit = false;

            while ((str = br.readLine()) != null) {
                if (str.equalsIgnoreCase("$ " + element)) {
                    //Print.f("$ " + element, 1);
                    while ((str = br.readLine()) != null) {
                        //Print.f(str, 1);
                        if (str.equalsIgnoreCase("$ -------------------------------------")) {
                            if (!isStartReading) {
                                isStartReading = true;
                            } else {
                                break;
                            }
                        }
                        if (str.startsWith("PARAMETER G(" + phase)) {
                            isTemp = true;
                            StringTokenizer st = new StringTokenizer(str);
                            //System.out.println(st.countTokens());
                            for (int i = 0; i < st.countTokens(); i++) {
                                if (st.nextToken().startsWith("G")) {
                                    do {
                                        for (int j = 0; j < st.countTokens(); j++) {
                                            if (st.countTokens() != 0) {
                                                if (isTemp) {
                                                    elementdata.temp[elementdata.nTemp] = Double.parseDouble(st.nextToken());
                                                    //System.out.println("temp = " + elementdata.temp[elementdata.nTemp]);
                                                    if (elementdata.nTemp != 0) {
                                                        st.nextToken();
                                                    }
                                                    elementdata.nTemp++;
                                                    isTemp = false;
                                                    isEqn = true;
                                                    eqn = "";
                                                }
                                            }
                                            if (st.countTokens() != 0) {
                                                if (isEqn) {
                                                    String next = st.nextToken();
                                                    if (next.endsWith(";")) {
                                                        //System.out.println(eqn + next);
                                                        elementdata.gEqn[elementdata.nTemp - 1] = eqn + next;
                                                        eqn = "";
                                                        isEqn = false;
                                                        isTemp = true;
                                                    } else if (next.equalsIgnoreCase("!")) {
                                                        //Print.f("elementdata", elementdata.element_phase, 1);
                                                        Data.add(elementdata);
                                                        Print.f("sgte.sgteReader method ended", 7);
                                                        return (Data.size() - 1);
                                                    } else {
                                                        eqn = eqn + next;
                                                    }
                                                }
                                            }
                                        }
                                        str = br.readLine().trim();
                                        st = new StringTokenizer(str);
                                    } while (!quit);
                                }

                            }
                        }
                    }
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File Not Found");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Usage: ShowFile File");
        } catch (IOException e) {
            System.err.println("IOException");
        }
        Print.f(element + ":" + phase + " not found in SGTE Database, pls check element and phase name", 0);
        Print.f("sgte.sgteReader method ended with error", 7);
        System.exit(0);
        return -1;
    }

    // Returns equivalent sgte phase name
    private static String getsgtePhaseName(String phase) throws IOException {
        Print.f("sgte.getsgtePhaseName method called with " + phase, 7);
        String phase_local = null;
        switch (phase) {
            case "A1":
                phase_local = "FCC_A1";
                break;
            case "L10":
                phase_local = "FCC_A1";
                break;
            case "L12":
                phase_local = "FCC_A1";
                break;
            case "A2":
                phase_local = "BCC_A2";
                break;
            case "B2":
                phase_local = "BCC_A2";
                break;
            case "A3":
                phase_local = "HCP_A3";
                break;
            case "B19":
                phase_local = "HCP_A3";
                break;
            case "D019":
                phase_local = "HCP_A3";
                break;
            case "A4":
                phase_local = "DIAMOND_A4";
                break;
            case "A5":
                phase_local = "BCT_" + phase;
                break;
            case "A6":
                phase_local = "TETRAGONAL_" + phase;
                break;
            case "A7":
                phase_local = "RHOMBOHEDRAL_" + phase;
                break;
            case "A8":
                phase_local = "HEXAGONAL_" + phase;
                break;
            case "L":
                phase_local = "LIQUID";
                break;
            case "SC":
                phase_local = "SC";//Stoichiometric Compounds
                break;

            default:
                Print.f("sgte: Error! Phase name not defined in SGTE.java", 0);
                System.exit(0);
        }
        Print.f("sgte.getsgtePhaseName method returned:" + phase_local, 7);
        return (phase_local);
    }

    public static double get(String E, String phase, double T, int typ, String option) throws IOException {
        Print.f("sgte.get method called with " + E + "," + phase + "," + typ + "," + option, 7);
        double[] cmat = null;//new double[11];
        double value;// = 0;
        switch (option) {
            case "sgte":
                String phase_local = getsgtePhaseName(phase); // get equivalent sgte phase name  
                if (!Data.isEmpty()) {
                    //Print.f("cmat", 0);
                    for (int i = 0; i < Data.size(); i++) {
                        if (Data.get(i).element_phase.equalsIgnoreCase(E + "-" + phase_local)) {
                            for (int j = 0; j < Data.get(i).nTemp; j++) {
                                if ((T > Data.get(i).temp[j]) && (T <= Data.get(i).temp[j + 1])) {
                                    cmat = getCmat(Data.get(i).gEqn[j]);
                                }
                            }
                            //Print.f("cmat" +cmat, 0);
                            if (cmat == null) {
                                Print.f("SGTE : " + E + "-" + phase_local + " not found @ temp = " + T, 0);
                            }
                        }
                    }
                }
                if (cmat == null) {
                    int i = sgteReader(E, phase_local);
                    //Print.f("i:"+Data.get(i).nTemp,1);
                    for (int j = 0; j < Data.get(i).nTemp; j++) {
                        if ((T > Data.get(i).temp[j]) && (T <= Data.get(i).temp[j + 1])) {
                            cmat = getCmat(Data.get(i).gEqn[j]);
                        }
                    }
                    if (cmat == null) {
                        Print.f("SGTE : " + E + "-" + phase_local + " not found @ temp = " + T, 0);
                    }
                }
                break;
            case "model":
                switch (E) {          //only for model calculation
                    case ("A"): {
                        cmat = A(phase, T);
                        break;
                    }
                    case ("B"): {
                        cmat = B(phase, T);
                        break;
                    }

                    default: {
                        System.out.println(E + " not implemented inGTE database");
                        return (0);
                    }
                }
                break;
        }
        switch (typ) {
            case (0): {
                value = calG(T, cmat);
                break;
            }
            case (1): {
                value = calGT(T, cmat);
                break;
            }
            case (2): {
                value = calGTT(T, cmat);
                break;
            }
            default: {
                System.out.println("invalid calculation id: " + typ);
                return (0);
            }

        }

        return value;
    }
//Standard Methods to calculate G,GT,GTT based on SGTE data

    private static double calG(double T, double cmat[]) {
        double GN = cmat[0] + cmat[1] * T + cmat[2] * T * Math.log(T) + cmat[3] * Math.pow(T, 2) + cmat[4] * Math.pow(T, 3) + cmat[5] * Math.pow(T, 4)
                + cmat[6] * Math.pow(T, 7) + cmat[7] * Math.pow(T, -1) + cmat[8] * Math.pow(T, -2) + cmat[9] * Math.pow(T, -3) + cmat[10] * Math.pow(T, -9);

        return (GN);
    }

    private static double calGT(double T, double cmat[]) {
        double GTN = cmat[1] + cmat[2] * (1 + Math.log(T)) + cmat[3] * (2 * T) + cmat[4] * (3 * Math.pow(T, 2)) + cmat[5] * (4 * Math.pow(T, 3))
                + cmat[6] * (7 * Math.pow(T, 6)) + cmat[7] * (-Math.pow(T, -2)) + cmat[8] * (-2 * Math.pow(T, -3)) + cmat[9] * (-3 * Math.pow(T, -4)) + cmat[10] * (-9 * Math.pow(T, -10));

        return (GTN);
    }

    private static double calGTT(double T, double cmat[]) {
        double GTTN = cmat[2] * (1 / T) + cmat[3] * (2) + cmat[4] * (6 * Math.pow(T, 1)) + cmat[5] * (12 * Math.pow(T, 2))
                + cmat[6] * (42 * Math.pow(T, 5)) + cmat[7] * (2 * Math.pow(T, -3)) + cmat[8] * (6 * Math.pow(T, -4)) + cmat[9] * (12 * Math.pow(T, -5)) + cmat[10] * (90 * Math.pow(T, -11));

        return (GTTN);
    }

    //Element Database (Keep adding elements(and/or) phase info. on same format)
    private static double[] A(String phase, double T) throws IOException {
        double cmat_local[] = null;
        //cmat[11] = {Constant, T, T*Log(T), T^2, T^3, T^4, T^7, T^-1, T^-2, T^-3, T^-9}
        if (phase.equalsIgnoreCase("A3")) {
            cmat_local = getCmat("0;");
        } else if (phase.equalsIgnoreCase("A2")) {
            cmat_local = getCmat("0;");//("7000-10*T;");
        } else if (phase.equalsIgnoreCase("L")) {
            cmat_local = getCmat("10000-10*T;");
        } else if (phase.equalsIgnoreCase("S")) {
            cmat_local = getCmat("0;");

        } else {
            Print.f("sgte.A() Phase Not Found ! \t" + phase, 0);
            System.exit(1);
        }
        if (cmat_local.length != cmatMaxLength) {
            Print.f("sgte.A() Wrong length cmat !", 0);
            System.exit(1);
        }
        return (cmat_local);
    }// Closed Method A

    private static double[] B(String phase, double T) throws IOException {
        double cmat_local[] = null;
        //cmat[11] = {Constant, T, T*Log(T), T^2, T^3, T^4, T^7, T^-1, T^-2, T^-3, T^-9}
        if (phase.equalsIgnoreCase("A3")) {
            cmat_local = getCmat("0;");
        } else if (phase.equalsIgnoreCase("A2")) {
            cmat_local = getCmat("0;");//("5000-10*T;");
        } else if (phase.equalsIgnoreCase("L")) {
            cmat_local = getCmat("8000-10*T;");
        } else if (phase.equalsIgnoreCase("S")) {
            cmat_local = getCmat("0;");
        } else {
            Print.f("sgte.B() Phase Not Found ! \t" + phase, 0);
            System.exit(1);
        }
        if (cmat_local.length != cmatMaxLength) {
            Print.f("sgte.B() Wrong length cmat !", 0);
            System.exit(1);
        }
        return (cmat_local);
    }// Closed Method B

    private static double[] getCmat(String str) throws IOException {
        Print.f("sgte.getCmat method called", 7);
        double[] cmat_local = new double[cmatMaxLength];
        //prnt.f("////" + str + "////");
        if (str.endsWith(";")) {
            str = str.substring(0, str.length() - 1);
        }
        String delims = "[+]";
        String temp[] = str.split(delims);
        //prnt.f(temp, "temp", 0);
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
        //prnt.f(store, "Store", 0);
        for (int i = 1; i < store.length; i++) {
            if (store[i] == null) {
                break;
            }
            if (store[i - 1].endsWith("E") || store[i - 1].endsWith("(")) {
                //prnt.f("E\ti\t"+store[i]+"\ti-1\t"+store[i-1]);
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
        //prnt.f(store, "Store", 0);
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
                        int index = Integer.parseInt(temp[2]);
                        //cmat[11] = {Constant, T, T*Log(T), T^2, T^3, T^4, T^7, T^-1, T^-2, T^-3, T-9}
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
    }// Closed Method getCmat()

    public static boolean isInt(String in) {
        try {
            Integer.parseInt(in);
            //Double.parseDouble(in);
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }// Closed Method isInt()
}// Closed Class sgte
