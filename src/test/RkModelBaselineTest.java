package test;

import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;

import java.util.Arrays;
import java.util.List;

/**
 * Baseline for the LIQUID phase's enthalpy of mixing against Fig. 10 of:
 *
 * J. Cui, C. Guo, L. Zou, C. Li and Z. Du,
 * "Thermodynamic modeling of the V-Zr system supported by key experiments",
 * CALPHAD 53 (2016) 122-129.
 *
 * Fig. 10 is "Calculated enthalpies of mixing of liquid at 2400 K in the
 * V-Zr system", a single smooth curve from the paper's own optimized
 * Redlich-Kister liquid model, plus one open-triangle marker near
 * x_Zr=0.5 -- the independent Miedema-method prediction of de Boer
 * et al. [25]. The marker is a DIFFERENT model's prediction, not this
 * paper's own result, so it is excluded from FIG10_SOLID_CURVE below
 * and never checked against.
 *
 * MODEL: LIQUID is (V,Zr)1, a plain one-sublattice substitutional
 * (Redlich-Kister) solution -- NOT a multi-sublattice CEF phase. In
 * this codebase such phases are built as an {@code RkPhaseModelAdapter}
 * / {@code RkGibbs} (see {@code system.model.rk}), reached via
 * {@code PhaseModelFactory.PhaseModel.alternateModel} /
 * {@code PhaseModelFactory.toGibbsModel()} -- {@code PhaseModel.gibbs}
 * (the CEF path) is null for LIQUID. This test exercises exactly that
 * RK code path, not the CEF machinery used by
 * {@link V2ZrGibbsBaselineTest} / {@link V2ZrGibbsLiteratureBaselineTest}.
 *
 * Table 3 of the paper gives the liquid excess parameters:
 *
 *     0L(V,Zr) = -15937.91 + 12.5173*T
 *     1L(V,Zr) = -10361.01 + 7.1042*T
 *     2L(V,Zr) = -5976.59
 *
 * matching VZR-re2.TDB's G(LIQUID,V,ZR;0/1/2) parameters exactly.
 *
 * This test does NOT build or reconstruct any RK formula of its own --
 * it exercises ONLY the RK model already implemented in
 * {@code system.model.rk} ({@link system.model.rk.RkGibbs} via
 * {@link system.model.rk.RkPhaseModelAdapter}, reached through
 * {@code TdbParser}/{@code PhaseModelFactory} exactly as production
 * code would), and checks that model's output against points digitized
 * directly from the published Fig. 10 solid curve (pixel calibration
 * against the figure's own axis ticks: x_Zr: 0-1.0, x=791-2772 px; H:
 * 0 to -5000 J/mol, y=523-2503 px, on a 10x render of the source PDF
 * page). The de Boer/Miedema triangle marker near x_Zr=0.5 was excluded
 * by inspection (it sits as its own separate cluster in that column,
 * clearly distinct from the smooth curve).
 *
 * Units: J/mol of atoms (matching the paper's Fig. 10 y-axis).
 */
public class RkModelBaselineTest {

    private static final String TDB_PATH = "data/VZR-re2.TDB";
    private static final String PHASE_NAME = "LIQUID";

    private static final double T = 2400.0;

    /*
     * RkGibbs.gId() computes x*ln(x) with no guard at x=0, giving NaN
     * at the exact pure end-members (0*ln(0) is mathematically 0 but
     * not IEEE-safe). Evaluate a hair off the end members instead of
     * exactly at x=0/x=1.
     */
    private static final double EPS = 1.0e-9;

    /*
     * Digitization noise on a hand-read curve -- not a floating-point
     * tolerance. The y-axis spans 5000 J/mol over ~1980 px in the
     * source render (~2.5 J/mol/px); 20 J/mol covers the worst observed
     * deviation seen during digitization (~15.5 J/mol, at x_Zr=0.5,
     * where the nearby de Boer marker makes the column noisiest) with
     * margin.
     */
    private static final double FIG10_TOLERANCE_J = 20.0;

    /*
     * Digitized from Cui et al. 2016, Fig. 10 (solid curve only -- the
     * de Boer/Miedema triangle marker near x_Zr=0.5 is excluded), by
     * pixel calibration against the figure's own axis ticks (x_Zr:
     * 0-1.0: x=791-2772 px; H: 0 to -5000 J/mol: y=523-2503 px, on a
     * 10x render of the source PDF page).
     */
    private static final double[][] FIG10_SOLID_CURVE = {
        // { x_Zr, H_mix [J/mol of atoms] }
        { 0.02, -616.2 }, { 0.04, -1169.2 }, { 0.06, -1664.1 }, { 0.08, -2123.7 },
        { 0.10, -2520.2 }, { 0.12, -2871.2 }, { 0.14, -3179.3 }, { 0.16, -3454.5 },
        { 0.18, -3699.5 }, { 0.20, -3883.8 }, { 0.22, -4050.5 }, { 0.24, -4181.8 },
        { 0.26, -4287.9 }, { 0.28, -4366.2 }, { 0.30, -4414.1 }, { 0.32, -4449.5 },
        { 0.34, -4454.5 }, { 0.36, -4449.5 }, { 0.38, -4419.2 }, { 0.40, -4378.8 },
        { 0.42, -4323.2 }, { 0.44, -4252.5 }, { 0.46, -4179.3 }, { 0.48, -4083.3 },
        { 0.50, -4000.0 }, { 0.52, -3878.8 }, { 0.54, -3752.5 }, { 0.56, -3641.4 },
        { 0.58, -3515.2 }, { 0.60, -3381.3 }, { 0.62, -3252.5 }, { 0.64, -3108.6 },
        { 0.66, -2972.2 }, { 0.68, -2825.8 }, { 0.70, -2669.2 }, { 0.72, -2522.7 },
        { 0.74, -2368.7 }, { 0.76, -2214.6 }, { 0.78, -2060.6 }, { 0.80, -1891.4 },
        { 0.82, -1737.4 }, { 0.84, -1563.1 }, { 0.86, -1386.4 }, { 0.88, -1212.1 },
        { 0.90, -1030.3 }, { 0.92, -835.9 }, { 0.94, -641.4 }, { 0.96, -434.3 },
        { 0.98, -232.3 },
    };

    public static void main(String[] args) throws Exception {

        System.out.println(
                "============================================================");
        System.out.println(
                "LIQUID enthalpy of mixing at 2400 K (Cui et al. 2016, Fig. 10)");
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

        if (phase.alternateModel == null) {
            throw new IllegalStateException(
                    PHASE_NAME + " was not constructed as a one-sublattice "
                    + "(RK) model; alternateModel is null.");
        }

        GibbsEnergyModel gm =
                PhaseModelFactory.toGibbsModel(phase, elements);

        System.out.println(
                "Database   : " + TDB_PATH);
        System.out.println(
                "Phase      : " + gm.phaseName());
        System.out.println(
                "Model type : " + gm.modelType()
                + " (" + gm.getClass().getName() + ")");
        System.out.println();

        // ============================================================
        // 2. Pure end-member enthalpies (H_V, H_Zr) at T, needed to
        //    convert absolute H(x,T) into enthalpy OF MIXING.
        // ============================================================

        double hV = pureEnthalpy(gm, 1.0 - EPS, EPS);
        double hZr = pureEnthalpy(gm, EPS, 1.0 - EPS);

        System.out.printf(
                "H_V(liquid, %.0fK)  = %.4f J/mol%n", T, hV);
        System.out.printf(
                "H_Zr(liquid, %.0fK) = %.4f J/mol%n", T, hZr);
        System.out.println();

        // ============================================================
        // 3. Compare against the digitized Fig. 10 literature curve
        // ============================================================

        System.out.println();
        System.out.println(
                "vs. digitized Cui et al. 2016, Fig. 10 (solid curve)");
        System.out.println(
                "--------------------------------------------------------------------------");

        System.out.printf(
                "%-8s %-16s %-16s %-12s %-8s%n",
                "xZr",
                "H_mix code",
                "Fig.10",
                "Abs error",
                "Result");

        System.out.println(
                "--------------------------------------------------------------------------");

        boolean allPassFig10 = true;
        double maxAbsErrorFig10 = 0.0;

        for (double[] point : FIG10_SOLID_CURVE) {

            double xZr = point[0];
            double hFig10 = point[1];
            double xV = 1.0 - xZr;

            double hMixCode =
                    mixingEnthalpy(gm, xV, xZr, hV, hZr);

            double error =
                    Math.abs(hMixCode - hFig10);

            maxAbsErrorFig10 =
                    Math.max(maxAbsErrorFig10, error);

            boolean pass =
                    error <= FIG10_TOLERANCE_J;

            allPassFig10 &= pass;

            System.out.printf(
                    "%-8.2f %-16.4f %-16.4f %-12.4f %-8s%n",
                    xZr,
                    hMixCode,
                    hFig10,
                    error,
                    pass ? "PASS" : "FAIL");
        }

        System.out.println();
        System.out.printf(
                "Maximum absolute error = %.4f J/mol (tolerance = %.1f J/mol)%n",
                maxAbsErrorFig10,
                FIG10_TOLERANCE_J);

        // ============================================================
        // 4. Final result
        // ============================================================

        if (!allPassFig10) {
            throw new AssertionError(
                    "RK model baseline (Cui et al. 2016 Fig. 10) FAILED; "
                    + "maximum absolute error = "
                    + maxAbsErrorFig10
                    + " J/mol");
        }

        System.out.println();
        System.out.println(
                "PASS: LIQUID enthalpy of mixing, computed via "
                + "system.model.rk.RkGibbs, matches the digitized "
                + "Fig. 10 literature curve (Cui et al. 2016).");
    }

    /**
     * H(x,T) = G(x,T) - T*dG/dT(x,T), via the actual RK code path
     * ({@code GibbsEnergyModel.evaluateG}/{@code evaluateGT}).
     */
    private static double pureEnthalpy(
            GibbsEnergyModel gm,
            double xV,
            double xZr) {

        double[] x = { xV, xZr };

        gm.setTemperature(T);
        gm.setInternalVars(x);

        double g = gm.evaluateG(x, T);
        double dgdt = gm.evaluateGT();

        return g - T * dgdt;
    }

    /**
     * H_mix(x) = H(x,T) - [x_V*H_V + x_Zr*H_Zr], via the actual RK code
     * path.
     */
    private static double mixingEnthalpy(
            GibbsEnergyModel gm,
            double xV,
            double xZr,
            double hV,
            double hZr) {

        double h =
                pureEnthalpy(gm, xV, xZr);

        return h - (xV * hV + xZr * hZr);
    }
}
