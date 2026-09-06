package system.model.cef;

import system.database.TdbParser;
import system.database.tdb;
import system.model.PhaseModelFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Verifies that interactionBasisAD produces exactly the same composition basis
 * as the original interactionBasis method.
 */
public class CefBasisVerification {

    private static final String TDB_FILE = "data/steel7.TDB";
    private static final double T = 1273.15;

    public static void main(String[] args) throws Exception {
        CefBasisVerification test = new CefBasisVerification();
        test.verifyBases();
    }

    private PhaseModelFactory.PhaseModel buildPhase(String phaseName) throws Exception {
        TdbParser parser = new TdbParser();
        parser.load(TDB_FILE);
        tdb rawDb = parser.getUnderlyingTdb();
        String[] elements = {"FE", "CR", "NI", "MO", "V", "C"};
        tdb filtered = rawDb.gettdb(elements);
        if (filtered == null)
            throw new IllegalArgumentException("Failed to filter TDB for elements");
        List<String> elemList = Arrays.asList(elements);
        return PhaseModelFactory.build(phaseName, filtered, elemList, new HashMap<>(), new HashMap<>());
    }

    void verifyBases() throws Exception {
        PhaseModelFactory.PhaseModel phase = buildPhase("FCC_A1");
        CefGibbs g = phase.gibbs;

        double[] y = makeInteriorComposition(g);

        System.out.println("=".repeat(70));
        System.out.println("INTERACTION BASIS VERIFICATION");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.println("T = " + T);
        System.out.println("y = " + Arrays.toString(y));
        System.out.println();

        // Access interactions via reflection since they're private
        java.lang.reflect.Field interactionsField = CefGibbs.class.getDeclaredField("interactions");
        interactionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<CefInteractionParam> interactions = (List<CefInteractionParam>) interactionsField.get(g);

        System.out.println("Total interactions: " + interactions.size());
        System.out.println();

        int mismatchCount = 0;

        for (int idx = 0; idx < interactions.size(); idx++) {
            CefInteractionParam interaction = interactions.get(idx);

            // Access the original interactionBasis method via reflection
            java.lang.reflect.Method basisMethod = CefGibbs.class.getDeclaredMethod("interactionBasis", CefInteractionParam.class, double[].class);
            basisMethod.setAccessible(true);
            double originalBasis = (double) basisMethod.invoke(g, interaction, y);

            // Access the AD2-based method via reflection
            java.lang.reflect.Method adBasisMethod = CefGibbs.class.getDeclaredMethod("interactionBasisAD", CefInteractionParam.class, double[].class);
            adBasisMethod.setAccessible(true);

            // AD2 is private, so we need to access its value field
            Object ad2Object = adBasisMethod.invoke(g, interaction, y);
            java.lang.reflect.Field valueField = ad2Object.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            double adBasis = (double) valueField.get(ad2Object);

            double error = Math.abs(adBasis - originalBasis);
            double relError = (Math.max(Math.abs(adBasis), Math.abs(originalBasis)) > 0)
                    ? error / Math.max(Math.abs(adBasis), Math.abs(originalBasis))
                    : 0.0;

            System.out.println("Interaction " + idx + ":");
            System.out.println("  Constituents: " + interaction.size() + ", RK order: " + interaction.rkOrder());
            System.out.println("  Original basis: " + originalBasis);
            System.out.println("  AD2 basis:      " + adBasis);
            System.out.println("  Absolute error: " + error);
            System.out.println("  Relative error: " + relError);

            if (relError > 1e-10) {
                System.out.println("  ⚠️ MISMATCH!");
                mismatchCount++;
            } else {
                System.out.println("  ✓ OK");
            }
            System.out.println();
        }

        System.out.println("=".repeat(70));
        System.out.println("Summary: " + (interactions.size() - mismatchCount) + "/" + interactions.size() + " bases match");
        if (mismatchCount > 0) {
            System.out.println("⚠️ MISMATCH FOUND - interactionBasisAD does not match interactionBasis!");
        } else {
            System.out.println("✓ All bases match - AD2 properly mirrors original computation");
        }
        System.out.println();
    }

    private double[] makeInteriorComposition(CefGibbs g) {
        int[] nc = g.constituentsPerSublattice();
        int[] offsets = g.offsets();

        double[] y = new double[g.nip()];

        for (int s = 0; s < nc.length; s++) {
            double value = 1.0 / nc[s];
            for (int i = 0; i < nc[s]; i++) {
                y[offsets[s] + i] = value;
            }
        }

        return y;
    }
}
