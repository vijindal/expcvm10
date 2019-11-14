/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package database;

import java.io.IOException;

/**
 * @author JEDIABJ77
 *
 */
// 0 -> calG(), 1 -> calGT(), 2 -> calGTT() 
public class stdst {

    /**
     * For get method:
     *
     * @param db database to be used for calculation
     * @param E string type for Element Symbol e.g. "Cr"
     * @param phase string type for Phase type e.g. "A2"
     * @param T double type for Temperature
     * @param typ integer type for type of calculation
     * @return
     * @throws java.io.IOException
     */
    public static double get(String db, String E, String phase, double T, int typ) throws IOException {
        double value = 0;
        switch (db) {
            case ("sgte"): {
                value = sgte.get(E, phase, T, typ, "sgte");
                break;
            }
            case ("model"): {
                value = sgte.get(E, phase, T, typ, "model");
                break;
            }
            default:
                System.out.println(db + " not implemented yet");
        }
        return (value);
    }
}
