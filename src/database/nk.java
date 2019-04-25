/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package database;

/*
 * @author JEDIABJ77 @editied AJ-2012-03-27
 *
 * For get method: @param E : string type for Element Symbol e.g. "Cr" @param
 * phase : string type for Phase type e.g. "A2" @param T: double type for
 * Temperature @param typ: integer type for type of calculation 0 -> calG() 1 ->
 * calGT() 2 -> calGTT()
 *
 * Index for coefficient matirx(cmat): cmat[0] : H cmat[1] : Transformation T
 * cmat[2] : ThetaD Alpha cmat[3] : ThetaD Beta cmat[4] : k alpha cmat[5] : k
 * beta
 */
public class nk {

    public static double R = 8.3144; // modifiable

    //get method
    public static double get(String E, String phase, double T, int typ) {

        double[] cmat;// = new double[11];
        double value;// = 0;
        switch (E) {

            case ("Hf"): {
                cmat = Hf(phase, T);
                break;
            }

            case ("Ti"): {
                cmat = Ti(phase, T);
                break;
            }

            case ("Sc"): {
                cmat = Sc(phase, T);
                break;
            }
            case ("Au"): {
                cmat = Au(phase, T);
                break;
            }
            case ("Pt"): {
                cmat = Pt(phase, T);
                break;
            }
            case ("Cr"): {
                cmat = Cr(phase, T);
                break;
            }
            case ("Mo"): {
                cmat = Mo(phase, T);
                break;
            }
            case ("W"): {
                cmat = W(phase, T);
                break;
            }
            case ("A"): {//Dummy element
                cmat = A(phase, T);
                break;
            }
            case ("B"): {//Dummy element
                cmat = B(phase, T);
                break;
            }
            default: {
                System.err.println(E + " not implemented in NC database");
                return (0);
            }
        }

        switch (typ) {

            case (0): {
                value = calH(T, cmat) - T * calS(T, cmat);
                break;
            }
            case (1): {
                value = calHT(T, cmat) - T * calST(T, cmat) - calS(T, cmat);
                break;
            }
            case (2): {
                value = 0;
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
    private static double calH(double T, double cmat[]) {

        double H = 0;
        if (cmat[0] == 0) {
            return (H);
        }
        H = cmat[0] + (1.0 / 2) * (cmat[5] - cmat[4]) * (Math.pow(T, 2) - Math.pow(cmat[1], 2)) + 3 * R * ((1.0 / 20) * (cmat[3] * cmat[3] - cmat[2] * cmat[2]) * ((1.0 / T) - (1.0 / cmat[1])) - (1.0 / 1680) * (Math.pow(cmat[3], 4) - Math.pow(cmat[2], 4)) * ((1.0 / Math.pow(T, 3)) - (1.0 / Math.pow(cmat[1], 3))));
        return (H);
    }

    private static double calS(double T, double cmat[]) {

        double S = 0;
        if (cmat[0] == 0) {
            return (S);
        }
        S = (cmat[5] - cmat[4]) * T + 3 * R * (-Math.log(cmat[3] / cmat[2]) + (1.0 / 40) * (Math.pow(cmat[3], 2) - Math.pow(cmat[2], 2)) * (1.0 / (T * T)) - (1.0 / 2240) * (Math.pow(cmat[3], 4) - Math.pow(cmat[2], 4)) * ((1.0 / Math.pow(T, 4))));
        return (S);
    }

    private static double calHT(double T, double cmat[]) {

        double HT = 0;
        if (cmat[0] == 0) {
            return (HT);
        }
        HT = (1.0 / 2) * (cmat[5] - cmat[4]) * (2 * Math.pow(T, 1)) + 3 * R * ((1.0 / 20) * (cmat[3] * cmat[3] - cmat[2] * cmat[2]) * (-1 * Math.pow(T, -2)) - (1.0 / 1680) * (Math.pow(cmat[3], 4) - Math.pow(cmat[2], 4)) * ((-3 * Math.pow(T, -4))));
        return (HT);
    }

    private static double calST(double T, double cmat[]) {

        double ST = 0;
        if (cmat[0] == 0) {
            return (ST);
        }
        ST = (cmat[5] - cmat[4]) + 3 * R * ((1.0 / 40) * (Math.pow(cmat[3], 2) - Math.pow(cmat[2], 2)) * (-2 * Math.pow(T, -3)) - (1.0 / 2240) * (Math.pow(cmat[3], 4) - Math.pow(cmat[2], 4)) * (-4 * Math.pow(T, -5)));
        return (ST);
    }

    //Element Database (Keep adding elements(and/or) phase info. on same format) 
    private static double[] A(String phase, double T) {//2012-05-12-vj:Dummy element
        double cmat_local[] = new double[6];
        if (phase.equals("A1")) {
            return (cmat_local);
        }
        if (phase.equals("L10")) {
            return (cmat_local);
        }
        if (phase.equals("L12")) {
            return (cmat_local);
        }
        if (phase.equals("A2")) {
            return (cmat_local);
        }
        if (phase.equals("A3")) {
            return (cmat_local);
        }
        return (cmat_local);
    }

    private static double[] B(String phase, double T) {//2012-05-12-vj:Dummy element
        double cmat_local[] = new double[6];
        if (phase.equals("A1")) {
            return (cmat_local);
        }
        if (phase.equals("L10")) {
            return (cmat_local);
        }
        if (phase.equals("L12")) {
            return (cmat_local);
        }
        if (phase.equals("A2")) {
            return (cmat_local);
        }
        if (phase.equals("A3")) {
            return (cmat_local);
        }
        return (cmat_local);
    }

    private static double[] Sc(String phase, double T) {

        double cmat_local[] = new double[6];

        if (phase.equalsIgnoreCase("A3")) {
            return (cmat_local);
        }
        if (phase.equalsIgnoreCase("A2")) {

            double cmat[] = {4008.27, 1608, 306, 210, 0.0063396, 0.00205746};
            cmat_local = cmat;

        }
        return (cmat_local);
    }

    private static double[] Ti(String phase, double T) {

        double cmat_local[] = new double[6];

        if (phase.equalsIgnoreCase("A3")) {
            return (cmat_local);
        }
        if (phase.equalsIgnoreCase("A2")) {

            double cmat[] = {4170, 1155, 385, 269, 0.00776589, 0.00316867};
            cmat_local = cmat;

        }
        return (cmat_local);
    }

    private static double[] Hf(String phase, double T) {
        double cmat_local[] = new double[6];
        if (phase.equals("A3")) {
            return (cmat_local);
        }
        if (phase.equals("A2")) {

            double cmat[] = {5860.29, 2016, 234.415, 159.905, 0.00720296, 0.00391439};
            cmat_local = cmat;
        }
        return (cmat_local);
    }

    private static double[] Au(String phase, double T) {
        double cmat_local[] = new double[6];
        return (cmat_local);
    }

    private static double[] Pt(String phase, double T) {
        double cmat_local[] = new double[6];
        return (cmat_local);
    }

    private static double[] Cr(String phase, double T) {
        double cmat_local[] = new double[6];
        return (cmat_local);
    }

    private static double[] Mo(String phase, double T) {
        double cmat_local[] = new double[6];
        return (cmat_local);
    }

    private static double[] W(String phase, double T) {
        double cmat_local[] = new double[6];
        return (cmat_local);
    }
}
