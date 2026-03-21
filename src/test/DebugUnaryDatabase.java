package test;

import database.tdb;
import java.io.IOException;

/**
 * Debug script to inspect the structure of the SGTE Unary database.
 */
public class DebugUnaryDatabase {

    public static void main(String[] args) {
        try {
            System.out.println("Loading data/Unary.TDB...\n");
            tdb database = new tdb("data/Unary.TDB");

            System.out.println("Elements in database:");
            for (String elem : database.getElementNames()) {
                System.out.println("  - " + elem);
            }

            System.out.println("\nPhases in database:");
            for (String phase : database.getPhaseNames()) {
                System.out.println("  - " + phase);
            }

            System.out.println("\n\nInspecting V element:");
            inspectElement(database, "V");

            System.out.println("\n\nInspecting ZR element:");
            inspectElement(database, "ZR");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void inspectElement(tdb database, String elemName) {
        System.out.println("Element: " + elemName);

        java.util.ArrayList<String> elemList = new java.util.ArrayList<>();
        elemList.add(elemName.toUpperCase());
        elemList.add("VA");

        System.out.println("Phases with parameters for " + elemName + ":");
        for (String phase : database.getPhaseNames()) {
            java.util.ArrayList<database.tdb.Parameter> params =
                database.getPhaseParam(elemList, phase);

            if (params != null && !params.isEmpty()) {
                System.out.println("  Phase: " + phase);
                for (database.tdb.Parameter p : params) {
                    System.out.println("    - Type: " + p.getType() +
                                     ", Order: " + p.getOrder());
                    java.util.List<database.tdb.Exp> exps = p.getExpList();
                    if (exps != null) {
                        System.out.println("      Expressions: " + exps.size());
                    }
                }
            }
        }
    }
}
