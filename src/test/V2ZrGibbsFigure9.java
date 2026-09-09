package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * Reproduces Fig. 9 of:
 *
 * J. Cui, C. Guo, L. Zou, C. Li and Z. Du,
 * "Thermodynamic modeling of the V-Zr system supported by key experiments",
 * CALPHAD 53 (2016) 122-129.
 *
 * Calculated Gibbs free energy of the V2Zr end member (stoichiometric
 * V:ZR, y=[1,0,0,1]) from T = 298.15 K to 2000 K, in kJ/mol formula
 * unit, evaluated directly from the project's own CEF machinery
 * (phase.gibbs.evaluate(T, y)) -- no equilibrium solver involved.
 *
 * Does NOT evaluate below 298.15 K: the SGTE unary lattice stabilities
 * (GHSERVV, GHSERZR) that this TDB's V2ZR expression is built from are
 * only defined for T >= 298.15 K. Evaluating the same polynomial
 * expressions below their fitted lower bound is an unphysical
 * extrapolation, not a valid calculation, and Cui et al.'s Fig. 9 does
 * not show or claim any result below that temperature either.
 *
 * Writes a CSV of (T, G_CEF_kJ) to stdout / a file so it can be plotted.
 */
public class V2ZrGibbsFigure9 {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    /** SGTE unary lower validity bound; do not evaluate below this. */
    private static final double T_MIN = 298.15;

    public static void main(String[] args) throws Exception {

        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phases = Arrays.asList(PHASE_NAME);

        @SuppressWarnings("unchecked")
        List<PhaseModel> models =
                (List<PhaseModel>) parser.buildPhaseModels(
                        elements,
                        phases);

        PhaseModel phase = models.get(0);

        final double[] y = {
            1.0, 0.0,
            0.0, 1.0
        };

        String outPath = args.length > 0
                ? args[0]
                : "fig9_data.csv";

        try (PrintWriter w = new PrintWriter(outPath)) {

            w.println("T_K,G_kJ_per_mol_fu");

            for (double T = T_MIN; T <= 2000.0; T += 10.0) {

                double gJ =
                        phase.gibbs.evaluate(T, y);

                double gKJ =
                        gJ / 1000.0;

                w.printf("%.2f,%.6f%n", T, gKJ);
            }
        }

        System.out.println("Wrote " + outPath);
    }
}
