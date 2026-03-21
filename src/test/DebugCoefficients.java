package test;

import database.tdb;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug script to check if coefficient lists are populated.
 */
public class DebugCoefficients {

    public static void main(String[] args) {
        try {
            System.out.println("Loading data/Unary.TDB...\n");
            tdb database = new tdb("data/Unary.TDB");

            // Query for V BCC_A2
            ArrayList<String> elemList = new ArrayList<>();
            elemList.add("V");
            elemList.add("VA");

            System.out.println("Querying for V in BCC_A2 phase:");
            ArrayList<database.tdb.Parameter> params = database.getPhaseParam(elemList, "BCC_A2");

            if (params == null) {
                System.out.println("  Result: null");
            } else if (params.isEmpty()) {
                System.out.println("  Result: empty list");
            } else {
                System.out.println("  Found " + params.size() + " parameters");
                for (database.tdb.Parameter p : params) {
                    System.out.println("\n  Parameter type: " + p.getType());
                    System.out.println("  Order: " + p.getOrder());
                    System.out.println("  Constituent list: " + p.getConstituentList());

                    List<database.tdb.Exp> expList = p.getExpList();
                    if (expList == null) {
                        System.out.println("  Expressions: null");
                    } else {
                        System.out.println("  Expressions: " + expList.size());
                        for (int i = 0; i < expList.size(); i++) {
                            database.tdb.Exp e = expList.get(i);
                            List<Double> coeffs = e.getSubCoeffList();
                            System.out.println("    Exp " + i + ":");
                            System.out.println("      Temp range: " + e.getTempRange());
                            if (coeffs == null) {
                                System.out.println("      SubCoeffList: null");
                            } else {
                                System.out.println("      SubCoeffList length: " + coeffs.size());
                                if (coeffs.size() > 0) {
                                    System.out.println("      First 3 coeffs: " + coeffs.subList(0, Math.min(3, coeffs.size())));
                                }
                            }
                        }
                    }
                }
            }

            System.out.println("\n\n");

            // Same for Zr
            elemList.clear();
            elemList.add("ZR");
            elemList.add("VA");

            System.out.println("Querying for ZR in BCC_A2 phase:");
            params = database.getPhaseParam(elemList, "BCC_A2");

            if (params == null) {
                System.out.println("  Result: null");
            } else if (params.isEmpty()) {
                System.out.println("  Result: empty list");
            } else {
                System.out.println("  Found " + params.size() + " parameters");
                for (database.tdb.Parameter p : params) {
                    System.out.println("\n  Parameter type: " + p.getType());
                    System.out.println("  Order: " + p.getOrder());

                    List<database.tdb.Exp> expList = p.getExpList();
                    if (expList != null) {
                        System.out.println("  Expressions: " + expList.size());
                        for (int i = 0; i < expList.size(); i++) {
                            database.tdb.Exp e = expList.get(i);
                            List<Double> coeffs = e.getSubCoeffList();
                            System.out.println("    Exp " + i + ": SubCoeffList size = "
                                    + (coeffs == null ? "null" : coeffs.size()));
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
