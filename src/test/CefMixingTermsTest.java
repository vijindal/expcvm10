package test;

import session.CalculationSession;
import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;
import system.model.cef.CefGibbs;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the CEF <b>ideal configurational</b> and <b>excess (Redlich-
 * Kister)</b> mixing contributions in isolation, against <b>pycalphad
 * 0.11.1</b> as the reference.
 *
 * <p>CEF: {@code G = G_ref + G_id + G_ex}. This project's
 * {@code CefGibbs} exposes the three parts separately
 * ({@code Gref} / {@code Gid} /
 * {@code Gex}); pycalphad exposes them as the named
 * symbolic sub-expressions {@code Model.models['ref' / 'idmix' /
 * 'xsmix']}. Each row compares this project's {@code Gid}
 * and {@code Gex} to the corresponding pycalphad
 * sub-expression, evaluated at the same constitution and multiplied by
 * pycalphad's {@code _site_ratio_normalization} (so both are per mole of
 * formula unit).
 *
 * <p><b>Test conditions</b>, all from {@code data/steel1.TDB}
 * (Cr-Fe-Mo-Si-V-C), non-magnetic phases only:
 * <ul>
 *   <li><b>Disordered solution phases</b> {@code CUB_A13} and
 *       {@code CBCC_A12}, {@code (Cr,Fe,Si,V)(C,Va)} 1:1 -- mixing on
 *       one sublattice: Fe-Si (3 RK orders), Fe-V (one order), and
 *       ternary Cr-Fe-Si metal compositions.</li>
 *   <li><b>Ordered compounds:</b>
 *     <ul>
 *       <li>{@code CEMENTITE} {@code (Cr,Fe,Mo,V)(C)} 3:1 -- binary and
 *           ternary mixing on the metal sublattice, Cr,Fe:C / Cr,V:C
 *           interactions.</li>
 *       <li>{@code M23C6} {@code (Cr,Fe,V)(Cr,Fe,Mo,V)(C)} 20:3:6 --
 *           reciprocal interactions {@code Cr,Fe:X:C}, and points with
 *           all three metal sublattice DOF live.</li>
 *       <li>{@code CHI_A12} {@code (Cr,Fe)(Cr,Mo)(Cr,Fe,Mo)} 24:10:24 --
 *           mixing on THREE sublattices at once. This phase has no
 *           {@code L} parameters, so its excess term is identically zero
 *           -- the rows check that both implementations report exactly
 *           0.</li>
 *     </ul>
 *   </li>
 * </ul>
 * across a range of compositions and two temperatures each (the ideal
 * term scales with RT, so a spread of T exercises it).
 *
 * <p><b>Report-only.</b> pycalphad is the reference of record; each row
 * prints the difference and the section prints error summaries, but the
 * test does not fail the build. Watch the numbers; do not gate on them.
 *
 * <p><b>Known constant offset</b> (2026-09-11): the ideal-mixing column
 * carries a small offset that scales with the sum of site ratios --
 * ~0.008 J per site -- because this project uses the 2014 CODATA gas
 * constant {@code R = 8.3144598} while pycalphad uses the older SGTE
 * value {@code 8.31451}. That is a deliberate constant, not a formula
 * error; the excess column, which has no explicit {@code R}, agrees to
 * floating point.
 *
 * <p>Models are sourced through {@link CalculationSession}
 * (setModel -> currentSystem().phaseModels()), not a raw parser call.
 * The requested element list for each phase is the full constituent set
 * of that phase, so this project and pycalphad build the same model.
 */
public class CefMixingTermsTest {

    private static final String TDB = "data/steel1.TDB";

    /*
     * { phase, elements CSV, T [K], site-fraction vector (sublattice-major,
     *   alphabetical within each sublattice),
     *   pycalphad G_id [J/f.u.], pycalphad G_xs [J/f.u.] }
     *
     * G_id / G_xs = pycalphad 0.11.1 Model.models['idmix' / 'xsmix']
     * evaluated at the constitution, * _site_ratio_normalization.
     */
    private static final Object[][] PYCALPHAD_REF = {
        // ===== DISORDERED  CUB_A13 (Cr,Fe,Si,V)(C,Va) -- Fe-Si (3 RK orders) =====
        { "CUB_A13", "CR,FE,SI,V,C", 600.0,  new double[]{0, 0.1, 0.9, 0, 0, 1}, -1621.741429, -1038.402000 },
        { "CUB_A13", "CR,FE,SI,V,C", 1200.0, new double[]{0, 0.1, 0.9, 0, 0, 1}, -3243.482859, 1471.518000 },
        { "CUB_A13", "CR,FE,SI,V,C", 600.0,  new double[]{0, 0.25, 0.75, 0, 0, 1}, -2805.321336, -11909.437500 },
        { "CUB_A13", "CR,FE,SI,V,C", 1200.0, new double[]{0, 0.25, 0.75, 0, 0, 1}, -5610.642672, -6680.437500 },
        { "CUB_A13", "CR,FE,SI,V,C", 600.0,  new double[]{0, 0.5, 0.5, 0, 0, 1}, -3457.903340, -31313.250000 },
        { "CUB_A13", "CR,FE,SI,V,C", 1200.0, new double[]{0, 0.5, 0.5, 0, 0, 1}, -6915.806679, -24341.250000 },
        { "CUB_A13", "CR,FE,SI,V,C", 600.0,  new double[]{0, 0.75, 0.25, 0, 0, 1}, -2805.321336, -29225.437500 },
        { "CUB_A13", "CR,FE,SI,V,C", 1200.0, new double[]{0, 0.75, 0.25, 0, 0, 1}, -5610.642672, -23996.437500 },
        { "CUB_A13", "CR,FE,SI,V,C", 600.0,  new double[]{0, 0.9, 0.1, 0, 0, 1}, -1621.741429, -14337.090000 },
        { "CUB_A13", "CR,FE,SI,V,C", 1200.0, new double[]{0, 0.9, 0.1, 0, 0, 1}, -3243.482859, -11827.170000 },
        // Fe-V (one RK order)
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.3, 0, 0.7, 0, 1}, -5079.031239, -2100.000000 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.5, 0, 0.5, 0, 1}, -5763.172233, -2500.000000 },
        { "CUB_A13", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.7, 0, 0.3, 0, 1}, -5079.031239, -2100.000000 },
        // ternary metal composition (Muggianu extrapolation of binaries)
        { "CUB_A13", "CR,FE,SI,V,C", 800.0,  new double[]{0.34, 0.33, 0.33, 0, 0, 1}, -7306.866524, -12627.717300 },
        { "CUB_A13", "CR,FE,SI,V,C", 1600.0, new double[]{0.34, 0.33, 0.33, 0, 0, 1}, -14613.733047, -8578.379700 },
        { "CUB_A13", "CR,FE,SI,V,C", 800.0,  new double[]{0.5, 0.3, 0.2, 0, 0, 1}, -6848.839988, -7474.188000 },
        { "CUB_A13", "CR,FE,SI,V,C", 1600.0, new double[]{0.5, 0.3, 0.2, 0, 0, 1}, -13697.679977, -5243.148000 },
        { "CUB_A13", "CR,FE,SI,V,C", 800.0,  new double[]{0.2, 0.5, 0.3, 0, 0, 1}, -6848.839988, -19790.670000 },
        { "CUB_A13", "CR,FE,SI,V,C", 1600.0, new double[]{0.2, 0.5, 0.3, 0, 0, 1}, -13697.679977, -14213.070000 },

        // ===== DISORDERED  CBCC_A12 (Cr,Fe,Si,V)(C,Va) -- Fe-Si (3 RK orders) =====
        { "CBCC_A12", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.2, 0.8, 0, 0, 1}, -4160.595951, -4614.944000 },
        { "CBCC_A12", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.5, 0.5, 0, 0, 1}, -5763.172233, -26665.250000 },
        { "CBCC_A12", "CR,FE,SI,V,C", 1000.0, new double[]{0, 0.8, 0.2, 0, 0, 1}, -4160.595951, -22346.528000 },

        // ===== ORDERED  CEMENTITE (Cr,Fe,Mo,V)(C) -- Cr,Fe:C and Cr,V:C =====
        { "CEMENTITE", "CR,FE,MO,V,C", 600.0,  new double[]{0.2, 0.8, 0, 0, 1}, -7489.072711, 2364.480000 },
        { "CEMENTITE", "CR,FE,MO,V,C", 1400.0, new double[]{0.2, 0.8, 0, 0, 1}, -17474.502992, 124.480000 },
        { "CEMENTITE", "CR,FE,MO,V,C", 600.0,  new double[]{0.5, 0.5, 0, 0, 1}, -10373.710019, 3694.500000 },
        { "CEMENTITE", "CR,FE,MO,V,C", 1400.0, new double[]{0.5, 0.5, 0, 0, 1}, -24205.323378, 194.500000 },
        { "CEMENTITE", "CR,FE,MO,V,C", 600.0,  new double[]{0.8, 0.2, 0, 0, 1}, -7489.072711, 2364.480000 },
        { "CEMENTITE", "CR,FE,MO,V,C", 1400.0, new double[]{0.8, 0.2, 0, 0, 1}, -17474.502992, 124.480000 },
        { "CEMENTITE", "CR,FE,MO,V,C", 1000.0, new double[]{0.3, 0, 0, 0.7, 1}, -15237.093718, -6849.939600 },
        { "CEMENTITE", "CR,FE,MO,V,C", 1000.0, new double[]{0.6, 0, 0, 0.4, 1}, -16787.266516, -9661.780800 },
        { "CEMENTITE", "CR,FE,MO,V,C", 1200.0, new double[]{0.4, 0.4, 0.2, 0, 1}, -31576.081452, 3884.480000 },
        { "CEMENTITE", "CR,FE,MO,V,C", 1200.0, new double[]{0.34, 0.33, 0.33, 0, 1}, -32880.899356, 4967.991600 },

        // ===== ORDERED  M23C6 (Cr,Fe,V)(Cr,Fe,Mo,V)(C) -- reciprocal interactions =====
        { "M23C6", "CR,FE,MO,V,C", 800.0,  new double[]{0.5, 0.5, 0, 0.5, 0.5, 0, 0, 1}, -106042.369083, -23002.160000 },
        { "M23C6", "CR,FE,MO,V,C", 1500.0, new double[]{0.5, 0.5, 0, 0.5, 0.5, 0, 0, 1}, -198829.442030, 1789.512500 },
        { "M23C6", "CR,FE,MO,V,C", 800.0,  new double[]{0.3, 0.7, 0, 0.7, 0.3, 0, 0, 1}, -93454.174806, -19321.814400 },
        { "M23C6", "CR,FE,MO,V,C", 1500.0, new double[]{0.3, 0.7, 0, 0.7, 0.3, 0, 0, 1}, -175226.577761, 1503.190500 },
        { "M23C6", "CR,FE,MO,V,C", 800.0,  new double[]{0.8, 0.2, 0, 0.2, 0.8, 0, 0, 1}, -76554.965489, -14721.382400 },
        { "M23C6", "CR,FE,MO,V,C", 1500.0, new double[]{0.8, 0.2, 0, 0.2, 0.8, 0, 0, 1}, -143540.560293, 1145.288000 },
        { "M23C6", "CR,FE,MO,V,C", 1200.0, new double[]{0.6, 0.3, 0.1, 0.25, 0.25, 0.25, 0.25, 1}, -220678.113580, -42055.532100 },
        { "M23C6", "CR,FE,MO,V,C", 1200.0, new double[]{0.34, 0.3, 0.36, 0.5, 0.25, 0.25, 0, 1}, -249782.151840, -79216.996440 },

        // ===== ORDERED  CHI_A12 (Cr,Fe)(Cr,Mo)(Cr,Fe,Mo) -- 3 mixing sublattices, no L params =====
        { "CHI_A12", "CR,FE,MO", 1000.0, new double[]{0.5, 0.5, 0.5, 0.5, 0.34, 0.33, 0.33}, -415153.851621, 0.000000 },
        { "CHI_A12", "CR,FE,MO", 1800.0, new double[]{0.5, 0.5, 0.5, 0.5, 0.34, 0.33, 0.33}, -747276.932918, 0.000000 },
        { "CHI_A12", "CR,FE,MO", 1000.0, new double[]{0.3, 0.7, 0.7, 0.3, 0.5, 0.25, 0.25}, -380161.262520, 0.000000 },
        { "CHI_A12", "CR,FE,MO", 1800.0, new double[]{0.3, 0.7, 0.7, 0.3, 0.5, 0.25, 0.25}, -684290.272537, 0.000000 },
        { "CHI_A12", "CR,FE,MO", 1000.0, new double[]{0.8, 0.2, 0.4, 0.6, 0.2, 0.4, 0.4}, -366319.067547, 0.000000 },
        { "CHI_A12", "CR,FE,MO", 1800.0, new double[]{0.8, 0.2, 0.4, 0.6, 0.2, 0.4, 0.4}, -659374.321585, 0.000000 },
    };

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("CefMixingTermsTest -- ideal + excess CEF mixing vs. pycalphad 0.11.1");
        System.out.println("           (report-only; steel1.TDB)");
        System.out.println("============================================================");

        Map<String, CefGibbs> cache = new LinkedHashMap<>();

        System.out.printf("%-11s %-8s %-16s %-16s %-14s | %-16s %-16s %-14s%n",
                "Phase", "T [K]",
                "id project", "id pycalphad", "id diff",
                "xs project", "xs pycalphad", "xs diff");
        System.out.println("----------------------------------------------------------------"
                + "--------------------------------------------------------------------");

        double sumIdAbs = 0, maxIdAbs = 0, sumXsAbs = 0, maxXsAbs = 0;
        String maxIdWhere = "", maxXsWhere = "";
        int n = 0;

        for (Object[] row : PYCALPHAD_REF) {
            String phaseName = (String) row[0];
            String elementsCsv = (String) row[1];
            double T = (Double) row[2];
            double[] y = (double[]) row[3];
            double idRef = (Double) row[4];
            double xsRef = (Double) row[5];

            String key = phaseName + "|" + elementsCsv;
            CefGibbs g = cache.get(key);
            if (g == null) {
                g = buildCefGibbs(TDB, Arrays.asList(elementsCsv.split(",")), phaseName);
                cache.put(key, g);
            }

            double idProj = g.Gid(T, y);
            double xsProj = g.Gex(T, y);
            double idDiff = idProj - idRef;
            double xsDiff = xsProj - xsRef;

            n++;
            sumIdAbs += Math.abs(idDiff);
            sumXsAbs += Math.abs(xsDiff);
            if (Math.abs(idDiff) > maxIdAbs) {
                maxIdAbs = Math.abs(idDiff);
                maxIdWhere = phaseName + " T=" + (int) T;
            }
            if (Math.abs(xsDiff) > maxXsAbs) {
                maxXsAbs = Math.abs(xsDiff);
                maxXsWhere = phaseName + " T=" + (int) T;
            }

            System.out.printf("%-11s %-8.0f %-16.4f %-16.4f %-14.5f | %-16.4f %-16.4f %-14.5f%n",
                    phaseName, T, idProj, idRef, idDiff, xsProj, xsRef, xsDiff);
        }

        System.out.println();
        System.out.printf("Summary over %d points:%n", n);
        System.out.printf("  ideal  mixing:  mean |diff| = %.4f J/f.u.   max |diff| = %.4f J/f.u. (at %s)%n",
                sumIdAbs / n, maxIdAbs, maxIdWhere);
        System.out.printf("  excess mixing:  mean |diff| = %.4f J/f.u.   max |diff| = %.4f J/f.u. (at %s)%n",
                sumXsAbs / n, maxXsAbs, maxXsWhere);
        System.out.println("  (report-only: pycalphad is the reference; the ideal offset is the "
                + "8.3144598 vs 8.31451 gas-constant convention, not a formula error)");
        System.out.println();
        System.out.println("DONE (report-only -- see the table above).");
    }

    private static CefGibbs buildCefGibbs(String tdb, List<String> elements,
                                          String phaseName) throws Exception {
        CalculationSession session = new CalculationSession();
        session.setModel(tdb, elements, Arrays.asList(phaseName));
        List<GibbsEnergyModel> models = session.currentSystem().phaseModels();
        if (models.size() != 1) {
            throw new IllegalStateException("Expected exactly one " + phaseName
                    + " model, but obtained " + models.size());
        }
        GibbsEnergyModel model = models.get(0);
        if (!(model instanceof CefGibbs)) {
            throw new IllegalStateException(phaseName
                    + " was not constructed as a CEF model (got "
                    + model.getClass().getSimpleName() + ").");
        }
        return ((CefGibbs) model).getGibbs();
    }
}
