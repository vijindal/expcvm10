package test;

import infra.TdbParser;
import domain.DatabasePort;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration test: Database exploration for Nb-Ti system in cost507.tdb
 * Shows available phases across temperature range 300K - 1000K
 */
public class GridMinimizerCost507Test {

    public static void main(String[] args) {
        System.out.println("=== Exploring cost507.tdb (Nb-Ti system) ===\n");

        try {
            // Load database
            System.out.println("Loading cost507.tdb...");
            TdbParser parser = new TdbParser();
            parser.load("data/cost507.tdb");

            // Get all phase names
            ArrayList<String> allPhases = parser.getPhaseNames();
            System.out.println("Total phases in database: " + allPhases.size());
            System.out.println("Phases: " + allPhases);
            System.out.println();

            // Extract Nb-Ti subsystem
            System.out.println("Extracting Nb-Ti system...");
            DatabasePort system = parser.extractSystem(new String[]{"NB", "TI"});

            if (system == null) {
                System.out.println("ERROR: Could not extract Nb-Ti system");
                return;
            }

            ArrayList<String> nbTiPhases = system.getPhaseNames();
            System.out.println("Phases available for Nb-Ti system: " + nbTiPhases.size());
            System.out.println("Phases: " + nbTiPhases);
            System.out.println();

            // Get underlying tdb for detailed queries
            database.tdb db = parser.getUnderlyingTdb();
            ArrayList<String> elements = new ArrayList<>();
            elements.add("NB");
            elements.add("TI");

            System.out.println("Phase parameters for Nb-Ti system:");
            System.out.println("-".repeat(75));

            for (String phaseName : nbTiPhases) {
                ArrayList<database.tdb.Parameter> params = db.getPhaseParam(elements, phaseName);
                if (params != null && !params.isEmpty()) {
                    System.out.printf("%-20s: %d parameter entries\n", phaseName, params.size());

                    // Show temperature ranges from first parameter
                    if (params.size() > 0) {
                        database.tdb.Parameter p = params.get(0);
                        ArrayList<database.tdb.Exp> exps = p.getExpList();
                        if (exps != null) {
                            System.out.print("  Temperature ranges: ");
                            for (database.tdb.Exp exp : exps) {
                                ArrayList<Double> tRange = exp.getTempRange();
                                if (tRange != null && tRange.size() >= 2) {
                                    System.out.printf("%.0f-%.0f K  ", tRange.get(0), tRange.get(1));
                                }
                            }
                            System.out.println();
                        }
                    }
                } else {
                    System.out.printf("%-20s: NO PARAMETERS\n", phaseName);
                }
            }

            System.out.println("-".repeat(75));
            System.out.println("\nNote: GridMinimizer with real phase models requires RkPhaseModelFactory");
            System.out.println("which creates actual thermodynamic model instances from the TDB.");
            System.out.println("For complete testing, use EquilibriumUseCase or PhaseDiagramUseCase.");

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
