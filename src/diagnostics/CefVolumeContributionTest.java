package diagnostics;

import session.CalculationSession;
import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies the CEF volume/pressure contribution (V0 constituent-array
 * parameters) against pycalphad's {@code Model.volume_energy}, on
 * {@code data/AlCoCrNi-volume.TDB} (pulled from pycalphad's own test
 * fixtures, {@code alcocrni.tdb}) -- the one bundled database that
 * carries real, active {@code V0} parameters.
 *
 * <p>FCC_A1 = (Al,Co,Cr,Ni)_1 (Va)_1, with {@code V0} on the Al:VA,
 * Co:VA, Cr:VA and Ni:VA end-members and no {@code VA} (thermal
 * expansion) parameter -- so pycalphad's {@code volume_energy} reduces
 * to {@code V0(y) * (P - 101325)}, T-independent. Reference values were
 * computed directly from pycalphad against this database:
 *
 * <pre>
 * y=[1,0,0,0:VA]   T=300/1000   P=1e9   Gvol = 10160.970335 J/mol-fu
 * y=[0,1,0,0:VA]   T=300/1000   P=1e9   Gvol =  6676.733411 J/mol-fu
 * y=[.5,.5,0,0:VA] T=300/1000   P=1e9   Gvol =  8418.851873 J/mol-fu
 * At P=101325 (the reference pressure) Gvol=0 exactly, for every y, T.
 * </pre>
 *
 * <p>Unlike the ideal-mixing/magnetic pycalphad comparisons elsewhere
 * (which carry a known R-constant/normalization offset and are
 * therefore report-only), these values are exact rational multiples of
 * the V0 parameters with no such ambiguity, so this test asserts a
 * tight numeric tolerance and fails the build on disagreement.
 *
 * <p>Every phase model is sourced through {@link CalculationSession}
 * ({@code setModel(...)} then {@code currentSystem().phaseModels()}) --
 * the same coordinator every UI goes through -- so nothing here reaches
 * a database/parser class directly, matching {@code CefReferenceTermTest}
 * / {@code CefLiteratureBaselineTest}.
 */
public class CefVolumeContributionTest {

    private static final String TDB = "data/AlCoCrNi-volume.TDB";
    private static final String PHASE = "FCC_A1";
    private static final double P_REF = 101325.0;
    private static final double TOL = 1.0e-3;

    public static void main(String[] args) throws Exception {
        boolean ok = true;

        CefGibbs fcc = buildCef(TDB,
                Arrays.asList("AL", "CO", "CR", "NI", "VA"), PHASE);

        System.out.println("phase=" + fcc.phaseName()
                + "  numSiteVars=" + fcc.numSiteVars());

        // y layout: [Al, Co, Cr, Ni, VA] (sublattice-major, alphabetical
        // within sublattice 0, then the single VA constituent of sublattice 1).
        double[][] cases = {
            // {Al, Co, Cr, Ni, T, P, expectedGvol}
            {1.0, 0.0, 0.0, 0.0,  300.0, P_REF,      0.0},
            {1.0, 0.0, 0.0, 0.0,  300.0, 1.0e9,  10160.970335},
            {1.0, 0.0, 0.0, 0.0, 1000.0, 1.0e9,  10160.970335},
            {0.0, 1.0, 0.0, 0.0,  300.0, P_REF,      0.0},
            {0.0, 1.0, 0.0, 0.0,  300.0, 1.0e9,   6676.733411},
            {0.0, 1.0, 0.0, 0.0, 1000.0, 1.0e9,   6676.733411},
            {0.5, 0.5, 0.0, 0.0,  300.0, 1.0e9,   8418.851873},
            {0.5, 0.5, 0.0, 0.0, 1000.0, 1.0e9,   8418.851873},
        };

        System.out.printf("%-20s %8s %10s %16s %16s %10s%n",
                "y(Al,Co,Cr,Ni)", "T[K]", "P[Pa]", "Gvol[J/mol-fu]",
                "expected", "diff");

        for (double[] c : cases) {
            double[] y = {c[0], c[1], c[2], c[3], 1.0};
            double T = c[4], P = c[5], expected = c[6];
            double gvol = fcc.Gvol(T, P, y);
            double diff = Math.abs(gvol - expected);
            boolean pass = diff < TOL;
            ok &= pass;
            System.out.printf("[%.2f,%.2f,%.2f,%.2f]     %8.1f %10.3e %16.6f %16.6f %10.2e %s%n",
                    c[0], c[1], c[2], c[3], T, P, gvol, expected, diff,
                    pass ? "PASS" : "FAIL");
        }

        // G(T,P,y) must include Gvol on top of the non-volume terms.
        double[] yAl = {1.0, 0.0, 0.0, 0.0, 1.0};
        double gFull = fcc.G(300.0, 1.0e9, yAl);
        double gParts = fcc.Gref(300.0, yAl) + fcc.Gid(300.0, yAl)
                + fcc.Gex(300.0, yAl) + fcc.Gvol(300.0, 1.0e9, yAl);
        double partsDiff = Math.abs(gFull - gParts);
        boolean partsOk = partsDiff < 1.0e-6;
        ok &= partsOk;
        System.out.printf("%nG(T,P,y) == Gref+Gid+Gex+Gvol: diff=%.3e -> %s%n",
                partsDiff, partsOk ? "PASS" : "FAIL");

        // dG_dP should equal the finite-difference derivative of G w.r.t. P.
        double h = 1.0;
        double dGdP_fd = (fcc.G(300.0, 1.0e9 + h, yAl) - fcc.G(300.0, 1.0e9 - h, yAl)) / (2.0 * h);
        double dGdP_analytic = fcc.dG_dP(300.0, 1.0e9, yAl);
        double dgdpDiff = Math.abs(dGdP_fd - dGdP_analytic);
        boolean dgdpOk = dgdpDiff < 1.0e-6;
        ok &= dgdpOk;
        System.out.printf("dG_dP analytic=%.6e  finite-diff=%.6e  diff=%.3e -> %s%n",
                dGdP_analytic, dGdP_fd, dgdpDiff, dgdpOk ? "PASS" : "FAIL");

        // A phase with no V0 parameters (V-Zr LIQUID) must have zero
        // volume contribution and be P-independent.
        CefGibbs liquidNoVolume = buildCef("data/VZR-re2.TDB",
                Arrays.asList("V", "ZR"), "LIQUID");
        double[] yLiq = {0.5, 0.5};
        double gLow  = liquidNoVolume.G(1000.0, P_REF, yLiq);
        double gHigh = liquidNoVolume.G(1000.0, 1.0e9, yLiq);
        double noVolDiff = Math.abs(gHigh - gLow);
        boolean noVolOk = noVolDiff < 1.0e-9;
        ok &= noVolOk;
        System.out.printf("Phase with no V0 params: G(P=1e9) - G(P=101325) = %.3e -> %s%n",
                noVolDiff, noVolOk ? "PASS (P-independent, as expected)" : "FAIL");

        System.out.println();
        System.out.println(ok ? "ALL CEF VOLUME CONTRIBUTION CHECKS PASSED"
                              : "SOME CEF VOLUME CONTRIBUTION CHECKS FAILED");
        if (!ok) System.exit(1);
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
