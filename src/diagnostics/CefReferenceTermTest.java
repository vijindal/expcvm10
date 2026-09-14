package diagnostics;

import session.CalculationSession;
import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;
import system.model.cef.CefGibbs;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the CEF <b>reference term</b> in isolation -- the pure-element
 * and stoichiometric lattice stabilities.
 *
 * <p>CEF: {@code G = G_ref + G_id + G_ex}. At a pure end-member (one
 * non-zero site fraction per sublattice) the ideal-mixing and excess
 * terms are identically zero, so {@code CefGibbs.siteEnergy}
 * there reduces to {@code G_ref = G(phase, X:Y;0)} -- the SGTE lattice
 * stability {@code n_1*GHSER(X) + n_2*GHSER(Y) + ...}.
 *
 * <p>Two checks per row:
 * <ol>
 *   <li><b>Identity (pass/fail, no external tolerance):</b>
 *       {@code siteEnergy(T, y_endmember)} must exactly equal
 *       {@code CefGibbs.endMember(...).G(T)}. This proves the
 *       ideal-mixing and excess contributions really do vanish at an
 *       end-member, so the vs-pycalphad column below IS a test of the
 *       reference term alone.</li>
 *   <li><b>vs. pycalphad 0.11.1 (report-only):</b> the same
 *       {@code siteEnergy} vs. pycalphad's molar Gibbs energy for that
 *       end-member ({@code GM * site-ratio-normalization}). The
 *       difference is printed; its summary is the regression signal but
 *       does not fail the build.</li>
 * </ol>
 *
 * <p>Database: {@code data/steel1.TDB}. Only NON-MAGNETIC phases /
 * end-members are used (BCC_A2/FCC_A1/HCP_A3 for Cr and Fe carry
 * {@code TC}/{@code BMAGN}, which this project does not yet evaluate --
 * see README). The requested element list is the full constituent set of
 * every tested phase, so this project and pycalphad build the same
 * model. The phase model is sourced through {@link CalculationSession}
 * (setModel -> currentSystem().phaseModels()). SGTE temperature
 * sub-range breaks are covered (the sweep includes T = 1811 K, the
 * GHSERFE break, and reaches 3000 K).
 */
public class CefReferenceTermTest {

    public static void main(String[] args) throws Exception {
        boolean identityOk = run();
        System.out.println();
        if (identityOk) {
            System.out.println("PASS: siteEnergy == endMember.G identity holds at every "
                    + "pure end-member. The vs-pycalphad column is report-only.");
        } else {
            throw new AssertionError("CefReferenceTermTest FAILED -- "
                    + "siteEnergy(T, y_endmember) != CefGibbs.endMember(...).G(T) "
                    + "for at least one row (see the identity column).");
        }
    }

    private static final String TDB = "data/steel1.TDB";
    // Every element in every tested phase's constituents, so this project
    // and pycalphad build the same model DOF.
    private static final List<String> ELEMENTS =
            Arrays.asList("CR", "FE", "MO", "SI", "V", "C");

    /*
     * Pure single-element end-members of NON-MAGNETIC phases in
     * steel1.TDB (BCC_A2/FCC_A1/HCP_A3 for Cr/Fe carry TC/BMAGN, so those
     * are excluded). At a pure end-member the ideal-mixing and excess
     * terms are identically zero, so G reduces to the CEF reference term
     * G_ref = G(phase, X:Y;0) -- the pure-element lattice stability. Each
     * row's SGTE polynomial spans the standard sub-range breaks (T = 1811
     * is the GHSERFE break; 3000 is well past most).
     *
     * Row format: { phase, element, T [K], pycalphad G [J/mol f.u.] }
     * value = pycalphad 0.11.1 GM * site-ratio-normalization, data/steel1.TDB.
     */
    private static final Object[][] PYCALPHAD_REF = {
        { "BCC_A2", "MO", 298.15, -8514.190288 },
        { "BCC_A2", "MO", 400.00, -11804.476424 },
        { "BCC_A2", "MO", 600.00, -20061.001218 },
        { "BCC_A2", "MO", 800.00, -30108.484115 },
        { "BCC_A2", "MO", 1000.00, -41543.180696 },
        { "BCC_A2", "MO", 1300.00, -60793.267015 },
        { "BCC_A2", "MO", 1600.00, -82147.187437 },
        { "BCC_A2", "MO", 1811.00, -98270.310918 },
        { "BCC_A2", "MO", 2000.00, -113428.623101 },
        { "BCC_A2", "MO", 2400.00, -147632.064645 },
        { "BCC_A2", "MO", 3000.00, -204255.987608 },
        { "BCC_A2", "SI", 298.15, 34680.551111 },
        { "BCC_A2", "SI", 800.00, 7516.830068 },
        { "BCC_A2", "SI", 1000.00, -5891.303386 },
        { "BCC_A2", "SI", 1600.00, -51915.635036 },
        { "BCC_A2", "SI", 2000.00, -86350.604911 },
        { "BCC_A2", "SI", 3000.00, -181366.071679 },
        { "BCC_A2", "V", 298.15, -9209.853341 },
        { "BCC_A2", "V", 800.00, -32148.282597 },
        { "BCC_A2", "V", 1000.00, -44205.110726 },
        { "BCC_A2", "V", 1600.00, -87061.702357 },
        { "BCC_A2", "V", 2000.00, -120302.403404 },
        { "BCC_A2", "V", 3000.00, -217490.282366 },
        { "FCC_A1", "MO", 298.15, 6873.644212 },
        { "FCC_A1", "MO", 800.00, -14404.484115 },
        { "FCC_A1", "MO", 1000.00, -25713.180696 },
        { "FCC_A1", "MO", 1600.00, -65939.187437 },
        { "FCC_A1", "MO", 2000.00, -96968.623101 },
        { "FCC_A1", "MO", 3000.00, -187165.987608 },
        { "FCC_A1", "SI", 298.15, 38889.256111 },
        { "FCC_A1", "SI", 800.00, 12076.830068 },
        { "FCC_A1", "SI", 1000.00, -1191.303386 },
        { "FCC_A1", "SI", 1600.00, -46795.635036 },
        { "FCC_A1", "SI", 2000.00, -80950.604911 },
        { "FCC_A1", "SI", 3000.00, -175266.071679 },
        { "FCC_A1", "V", 298.15, -1202.998341 },
        { "FCC_A1", "V", 800.00, -23288.282597 },
        { "FCC_A1", "V", 1000.00, -35005.110726 },
        { "FCC_A1", "V", 1600.00, -76841.702357 },
        { "FCC_A1", "V", 2000.00, -109402.403404 },
        { "FCC_A1", "V", 3000.00, -205386.823006 },
        { "HCP_A3", "MO", 298.15, 3035.809712 },
        { "HCP_A3", "MO", 800.00, -18558.484115 },
        { "HCP_A3", "MO", 1000.00, -29993.180696 },
        { "HCP_A3", "MO", 1600.00, -70597.187437 },
        { "HCP_A3", "MO", 2000.00, -101878.623101 },
        { "HCP_A3", "MO", 3000.00, -192705.987608 },
        { "HCP_A3", "SI", 298.15, 37387.406111 },
        { "HCP_A3", "SI", 800.00, 11076.830068 },
        { "HCP_A3", "SI", 1000.00, -1991.303386 },
        { "HCP_A3", "SI", 1600.00, -46995.635036 },
        { "HCP_A3", "SI", 2000.00, -80750.604911 },
        { "HCP_A3", "SI", 3000.00, -174066.071679 },
        { "HCP_A3", "V", 298.15, -4494.293341 },
        { "HCP_A3", "V", 800.00, -26228.282597 },
        { "HCP_A3", "V", 1000.00, -37805.110726 },
        { "HCP_A3", "V", 1600.00, -79221.702357 },
        { "HCP_A3", "V", 2000.00, -111502.403404 },
        { "HCP_A3", "V", 3000.00, -206786.823006 },
        { "DIAMOND_A4", "SI", 298.15, -5611.073889 },
        { "DIAMOND_A4", "SI", 800.00, -21483.169932 },
        { "DIAMOND_A4", "SI", 1000.00, -30391.303386 },
        { "DIAMOND_A4", "SI", 1600.00, -62915.635036 },
        { "DIAMOND_A4", "SI", 2000.00, -88350.604911 },
        { "DIAMOND_A4", "SI", 3000.00, -160866.071679 },
        { "DIAMOND_A4", "C", 298.15, 1192.557814 },
        { "DIAMOND_A4", "C", 800.00, -3189.327188 },
        { "DIAMOND_A4", "C", 1000.00, -6700.823533 },
        { "DIAMOND_A4", "C", 1600.00, -21970.481120 },
        { "DIAMOND_A4", "C", 2000.00, -35299.026660 },
        { "DIAMOND_A4", "C", 3000.00, -76813.418985 },
        { "CUB_A13", "CR", 298.15, 9068.477137 },
        { "CUB_A13", "CR", 800.00, -9737.370914 },
        { "CUB_A13", "CR", 1000.00, -20167.829047 },
        { "CUB_A13", "CR", 1600.00, -58733.043634 },
        { "CUB_A13", "CR", 2000.00, -89963.561763 },
        { "CUB_A13", "CR", 3000.00, -185517.337787 },
        { "CUB_A13", "FE", 298.15, 1903.597290 },
        { "CUB_A13", "FE", 800.00, -24217.157112 },
        { "CUB_A13", "FE", 1000.00, -37705.417957 },
        { "CUB_A13", "FE", 1600.00, -85438.104890 },
        { "CUB_A13", "FE", 1811.00, -104405.138092 },
        { "CUB_A13", "FE", 2000.00, -122251.662942 },
        { "CUB_A13", "FE", 3000.00, -228578.488832 },
        { "CUB_A13", "SI", 298.15, 35592.523561 },
        { "CUB_A13", "SI", 800.00, 9494.230068 },
        { "CUB_A13", "SI", 1000.00, -3489.303386 },
        { "CUB_A13", "SI", 1600.00, -48239.835036 },
        { "CUB_A13", "SI", 2000.00, -81825.604911 },
        { "CUB_A13", "SI", 3000.00, -174718.071679 },
        { "CBCC_A12", "CR", 298.15, 4880.206937 },
        { "CBCC_A12", "CR", 800.00, -12875.770914 },
        { "CBCC_A12", "CR", 1000.00, -22887.829047 },
        { "CBCC_A12", "CR", 1600.00, -60197.843634 },
        { "CBCC_A12", "CR", 2000.00, -90591.561763 },
        { "CBCC_A12", "CR", 3000.00, -184053.337787 },
        { "CBCC_A12", "FE", 298.15, 2903.597290 },
        { "CBCC_A12", "FE", 800.00, -23217.157112 },
        { "CBCC_A12", "FE", 1000.00, -36705.417957 },
        { "CBCC_A12", "FE", 1811.00, -103405.138092 },
        { "CBCC_A12", "FE", 2000.00, -121251.662942 },
        { "CBCC_A12", "FE", 3000.00, -227578.488832 },
        { "CBCC_A12", "SI", 298.15, 38521.523561 },
        { "CBCC_A12", "SI", 800.00, 12423.230068 },
        { "CBCC_A12", "SI", 1000.00, -560.303386 },
        { "CBCC_A12", "SI", 1600.00, -45310.835036 },
        { "CBCC_A12", "SI", 2000.00, -78896.604911 },
        { "CBCC_A12", "SI", 3000.00, -171789.071679 },
    };

    private static boolean run() throws Exception {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("CefReferenceTermTest -- CEF reference term at pure "
                + "end-members (steel1.TDB) vs. pycalphad 0.11.1");
        System.out.println("============================================================");
        System.out.println("  Two checks per row:");
        System.out.println("    (1) siteEnergy(T, y_em) == CefGibbs.endMember(...).G(T)  "
                + "-- IDENTITY, pass/fail");
        System.out.println("        (proves the ideal-mixing and excess terms vanish at");
        System.out.println("         a pure end-member, so what is compared IS G_ref).");
        System.out.println("    (2) siteEnergy(T, y_em) vs. pycalphad G  -- report-only.");
        System.out.println();

        Map<String, CefGibbs> cache = new LinkedHashMap<>();

        System.out.printf("%-11s %-4s %-8s %-18s %-18s %-14s %-16s%n",
                "Phase", "El", "T [K]", "siteEnergy [J/fu]", "pycalphad [J/fu]",
                "vs pyc", "identity |diff|");
        System.out.println("--------------------------------------------------------------"
                + "----------------------------------------------------------");

        boolean identityOk = true;
        double sumAbs = 0.0, maxAbs = 0.0, sumRel = 0.0, maxRel = 0.0;
        String maxAbsWhere = "", maxRelWhere = "";
        double maxIdentity = 0.0;
        String maxIdentityWhere = "";
        int n = 0;

        for (Object[] row : PYCALPHAD_REF) {
            String phaseName = (String) row[0];
            String el = (String) row[1];
            double T = (Double) row[2];
            double gRef = (Double) row[3];

            CefGibbs phase = cache.get(phaseName);
            if (phase == null) {
                phase = buildCef(TDB, ELEMENTS, phaseName);
                cache.put(phaseName, phase);
            }

            // Build the pure end-member y and the per-sublattice constituent
            // index of `el` (VA -- mapped element index -1 -- fills any
            // sublattice that has no `el`).
            system.model.cef.CefGibbs g = phase.getGibbs();
            int nip = g.numSiteVars();
            int[] ncSL = g.constituentsPerSublattice();
            int[] offs = g.offsets();
            int[][] elIdxMap = phase.getElementIndexOnSublattice();
            int elGlobal = ELEMENTS.indexOf(el);   // ELEMENTS is upper-case
            int ns = ncSL.length;
            double[] y = new double[nip];
            int[] emIdx = new int[ns];
            for (int s = 0; s < ns; s++) {
                int chosen = -1;
                // prefer the `el` constituent on this sublattice
                for (int i = 0; i < ncSL[s]; i++) {
                    if (elIdxMap[s][i] == elGlobal) { chosen = i; break; }
                }
                // otherwise the vacancy (mapped element index -1)
                if (chosen < 0) {
                    for (int i = 0; i < ncSL[s]; i++) {
                        if (elIdxMap[s][i] < 0) { chosen = i; break; }
                    }
                }
                if (chosen < 0) {
                    throw new IllegalStateException(phaseName + " sublattice " + s
                            + " has neither " + el + " nor VA");
                }
                emIdx[s] = chosen;
                y[offs[s] + chosen] = 1.0;
            }

            double se = phase.G(T, 101325.0, y);
            double emG = g.endMember(emIdx).G(T);
            double identityDiff = Math.abs(se - emG);
            if (identityDiff > 1e-6) {
                identityOk = false;
            }
            if (identityDiff > maxIdentity) {
                maxIdentity = identityDiff;
                maxIdentityWhere = phaseName + " " + el + " T=" + (int) T;
            }

            double absDiff = se - gRef;
            double relDiff = Math.abs(gRef) > 1.0 ? absDiff / Math.abs(gRef) : absDiff;
            n++;
            sumAbs += Math.abs(absDiff);
            sumRel += Math.abs(relDiff);
            if (Math.abs(absDiff) > maxAbs) {
                maxAbs = Math.abs(absDiff);
                maxAbsWhere = phaseName + " " + el + " T=" + (int) T;
            }
            if (Math.abs(relDiff) > maxRel) {
                maxRel = Math.abs(relDiff);
                maxRelWhere = phaseName + " " + el + " T=" + (int) T;
            }

            System.out.printf("%-11s %-4s %-8.0f %-18.4f %-18.4f %-14.4f %-16.3e%n",
                    phaseName, el, T, se, gRef, absDiff, identityDiff);
        }

        System.out.println();
        System.out.printf("Summary over %d pure end-members:%n", n);
        System.out.printf("  identity (siteEnergy == endMember.G):  max |diff| = %.3e "
                + "(at %s) -> %s%n", maxIdentity,
                maxIdentityWhere.isEmpty() ? "-" : maxIdentityWhere,
                identityOk ? "PASS" : "FAIL");
        System.out.printf("  vs pycalphad (report-only):  mean |abs| = %.4f J/fu, "
                + "mean |rel| = %.3e%n", sumAbs / n, sumRel / n);
        System.out.printf("                               max  |abs| = %.4f J/fu (at %s)%n",
                maxAbs, maxAbsWhere);
        System.out.printf("                               max  |rel| = %.3e     (at %s)%n",
                maxRel, maxRelWhere);
        return identityOk;
    }


    private static CefGibbs buildCef(String tdb, List<String> elements,
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
        return (CefGibbs) model;
    }
}
