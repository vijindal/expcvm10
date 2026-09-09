package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;

import java.util.Arrays;
import java.util.List;

/**
 * Literature baseline for the V2Zr Gibbs energy against the published
 * Fig. 9 of:
 *
 * J. Cui, C. Guo, L. Zou, C. Li and Z. Du,
 * "Thermodynamic modeling of the V-Zr system supported by key experiments",
 * CALPHAD 53 (2016) 122-129.
 *
 * Unlike {@link V2ZrGibbsBaselineTest} (which checks the CEF evaluation
 * against an independent analytical reconstruction of the TDB's own
 * G(V2ZR,V:ZR;0) formula), this test checks it against the paper's own
 * PUBLISHED RESULT -- the solid curve in Fig. 9 -- to confirm this
 * project's implementation reproduces the literature figure, not just
 * its own database.
 *
 * V2Zr is modeled as (V,Zr)2(V,Zr); at the stoichiometric V:ZR end
 * member (y = [1,0,0,1]) all configurational and interaction
 * contributions vanish, so G_CEF must equal G(V2ZR,V:ZR;0) alone.
 *
 * DATA SOURCE: the paper publishes no numeric table for Fig. 9 (only
 * the plotted curve), so the expected values here were digitized from
 * the published PDF figure by pixel calibration against the figure's
 * own axis ticks (T: 0-2000 K; G: 0 to -400 kJ/mol), tracking the
 * SOLID curve's pixel continuity from one column to the next (the
 * solid curve is unbroken; the dashed Zhao et al. [20] comparison
 * curve in the same figure has gaps). See docs/fig9_reproduce.py for
 * the full digitization and docs/fig9_comparison.png for the resulting
 * comparison plot.
 *
 * Because these expected values are read from a printed figure rather
 * than an exact source, the tolerance here is set by observed
 * digitization noise (~3 kJ/mol at worst, growing mildly at high T
 * where the curve is steepest), not by floating-point precision --
 * this is a literature-agreement check, not an exact-value check like
 * {@link V2ZrGibbsBaselineTest}.
 *
 * Units: kJ/mol formula unit (matching the paper's Fig. 9 y-axis).
 */
public class V2ZrGibbsLiteratureBaselineTest {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "V2ZR";

    /*
     * Digitization noise on a steep, hand-read curve -- not a
     * floating-point tolerance. 5 kJ/mol covers the worst observed
     * deviation (~3.4 kJ/mol at 1950 K) with margin.
     */
    private static final double TOLERANCE_KJ = 5.0;

    /*
     * Digitized from Cui et al. 2016, Fig. 9 (solid curve), by pixel
     * calibration against the figure's own axis ticks (T: 0-2000 K:
     * x=713-2746.5 px; G: 0 to -400 kJ/mol: y=626-2660 px on a 10x
     * render of the source PDF page). Points below 298.15 K are not
     * included: the SGTE unary lattice stabilities (GHSERVV, GHSERZR)
     * this TDB's V2ZR expression is built from are undefined there, so
     * no valid comparison exists in that range, and the paper's own
     * figure does not show a result below it either.
     */
    private static final double[][] FIG9_SOLID_CURVE = {
        // { T [K], G [kJ/mol formula unit] }
        {  300, -41.69 }, {  350, -46.61 }, {  400, -52.90 }, {  450, -58.80 },
        {  500, -65.68 }, {  550, -72.57 }, {  600, -79.84 }, {  650, -87.32 },
        {  700, -96.17 }, {  750, -104.03 }, {  800, -113.08 }, {  850, -121.73 },
        {  900, -131.17 }, {  950, -139.23 }, { 1000, -150.05 }, { 1050, -160.28 },
        { 1100, -170.70 }, { 1150, -181.51 }, { 1200, -192.33 }, { 1250, -203.74 },
        { 1300, -215.73 }, { 1350, -225.37 }, { 1400, -237.17 }, { 1450, -248.57 },
        { 1500, -260.77 }, { 1550, -272.37 }, { 1600, -285.35 }, { 1650, -297.54 },
        { 1700, -310.32 }, { 1750, -322.71 }, { 1800, -335.89 }, { 1850, -348.87 },
        { 1900, -362.83 }, { 1950, -376.40 },
    };

    public static void main(String[] args) throws Exception {

        System.out.println(
                "============================================================");
        System.out.println(
                "V2ZR Gibbs energy vs. Cui et al. 2016, Fig. 9 (literature)");
        System.out.println(
                "============================================================");

        // ============================================================
        // 1. Load the actual V-Zr database
        // ============================================================

        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phases = Arrays.asList(PHASE_NAME);

        @SuppressWarnings("unchecked")
        List<PhaseModel> models =
                (List<PhaseModel>) parser.buildPhaseModels(
                        elements,
                        phases);

        if (models.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one " + PHASE_NAME
                    + " model, but obtained " + models.size());
        }

        PhaseModel phase = models.get(0);

        if (phase.gibbs == null) {
            throw new IllegalStateException(
                    PHASE_NAME + " was not constructed as a CEF model.");
        }

        // ============================================================
        // 2. Stoichiometric V2Zr end member: y = [1,0,0,1]
        // ============================================================

        final double[] y = {
            1.0, 0.0,
            0.0, 1.0
        };

        // ============================================================
        // 3. Compare against the digitized literature curve
        // ============================================================

        System.out.printf(
                "%-10s %-16s %-16s %-12s %-8s%n",
                "T [K]",
                "CEF G [kJ/mol]",
                "Fig.9 [kJ/mol]",
                "Abs error",
                "Result");

        System.out.println(
                "--------------------------------------------------------------------------");

        boolean allPass = true;
        double maxAbsError = 0.0;

        for (double[] point : FIG9_SOLID_CURVE) {

            double T = point[0];
            double gFig9 = point[1];

            double gCefKJ =
                    phase.gibbs.evaluate(T, y) / 1000.0;

            double error =
                    Math.abs(gCefKJ - gFig9);

            maxAbsError =
                    Math.max(maxAbsError, error);

            boolean pass =
                    error <= TOLERANCE_KJ;

            allPass &= pass;

            System.out.printf(
                    "%-10.2f %-16.4f %-16.4f %-12.4f %-8s%n",
                    T,
                    gCefKJ,
                    gFig9,
                    error,
                    pass ? "PASS" : "FAIL");
        }

        System.out.println();
        System.out.printf(
                "Maximum absolute error = %.4f kJ/mol (tolerance = %.1f kJ/mol)%n",
                maxAbsError,
                TOLERANCE_KJ);

        // ============================================================
        // 4. Final result
        // ============================================================

        if (!allPass) {
            throw new AssertionError(
                    "V2ZR Gibbs-energy vs. Cui et al. 2016 Fig. 9 FAILED; "
                    + "maximum absolute error = "
                    + maxAbsError
                    + " kJ/mol");
        }

        System.out.println();
        System.out.println(
                "PASS: V2ZR Gibbs energy reproduces the published Fig. 9 "
                + "curve of Cui et al. 2016 within digitization tolerance.");
    }
}
