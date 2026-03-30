package test;

import infra.TdbParser;
import infra.RkPhaseModelFactory;
import infra.RkPhaseModelAdapter;
import domain.DatabasePort;
import domain.PhaseModelPort;
import thermocalc.equil.GridMinimizer;
import thermocalc.equil.EquilibriumState;
import thermocalc.equil.PhaseRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration test: GridMinimizer with real RK phase models for Nb-Ti system.
 * Temperature sweep from 300K to 1000K at 100K intervals using cost507.tdb.
 */
public class GridMinimizerCost507Test {

    public static void main(String[] args) {
        System.out.println("=== GridMinimizer: Nb-Ti System (cost507.tdb) ===\n");

        try {
            // Load database
            System.out.println("Loading cost507.tdb...");
            TdbParser parser = new TdbParser();
            parser.load("data/cost507.tdb");
            database.tdb db = parser.getUnderlyingTdb();

            // Elements of interest
            List<String> elements = new ArrayList<>();
            elements.add("NB");
            elements.add("TI");

            // Extract Nb-Ti phases
            System.out.println("Extracting Nb-Ti system...");
            DatabasePort system = parser.extractSystem(new String[]{"NB", "TI"});
            if (system == null) {
                System.out.println("ERROR: Could not extract Nb-Ti system");
                return;
            }

            ArrayList<String> nbTiPhases = system.getPhaseNames();
            System.out.println("Phases available: " + nbTiPhases.size());
            System.out.println("All phases: " + nbTiPhases);
            System.out.println();

            // Filter to main phases only
            ArrayList<String> mainPhases = new ArrayList<>();
            for (String phase : nbTiPhases) {
                if (phase.equalsIgnoreCase("LIQUID") ||
                    phase.equalsIgnoreCase("BCC_A2") ||
                    phase.equalsIgnoreCase("HCP_A3")) {
                    mainPhases.add(phase);
                }
            }

            System.out.println("Selected phases: " + mainPhases.size());
            System.out.println("Phases: " + mainPhases);
            System.out.println();

            // ──── Temperature sweep: 300K to 1000K @ 100K intervals ────
            System.out.println("Temperature sweep: 50% Nb - 50% Ti composition");
            System.out.println("=".repeat(80));

            double[] composition = {0.5, 0.5};  // 50% Nb, 50% Ti
            double P = 1e5;                     // 1 atm
            GridMinimizer gm = new GridMinimizer(20);  // grid density

            int[] temperatures = {300, 400, 500, 600, 700, 800, 900, 1000};

            for (int T : temperatures) {
                System.out.printf("\n[%4d K] ", T);

                try {
                    // Build phase models for this temperature
                    List<PhaseModelPort> models = new ArrayList<>();
                    for (String phaseName : mainPhases) {
                        try {
                            RkPhaseModelAdapter model = RkPhaseModelFactory.build(phaseName, elements, db);
                            models.add(model);
                        } catch (Exception e) {
                            // Skip phases that fail to build
                            System.out.printf("[%s: skipped] ", phaseName);
                        }
                    }

                    if (models.isEmpty()) {
                        System.out.println("ERROR: No phase models could be built");
                        continue;
                    }

                    // Run GridMinimizer
                    EquilibriumState state = gm.initialize(models, T, P, composition);

                    // Report stable phases
                    List<PhaseRecord> stablePhases = state.stablePhases();
                    System.out.printf("Stable phases: ");
                    if (stablePhases.isEmpty()) {
                        System.out.print("(none)");
                    } else {
                        for (int i = 0; i < stablePhases.size(); i++) {
                            PhaseRecord pr = stablePhases.get(i);
                            if (i > 0) System.out.print(" + ");
                            System.out.printf("%s (%.1f%%)", pr.model.phaseName(), pr.amount * 100);
                        }
                    }
                    System.out.println();

                } catch (Exception e) {
                    System.out.printf("ERROR at %dK: %s\n", T, e.getMessage());
                }
            }

            System.out.println("\n" + "=".repeat(80));
            System.out.println("Temperature sweep complete.");

        } catch (Exception e) {
            System.err.println("FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
