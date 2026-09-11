package test;

import calc.equil.PhaseMatrixAssembler;
import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import system.model.PhaseModelFactory;
import system.model.cef.CefGibbs;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies {@code calc.equil.PhaseMatrixAssembler} -- the solver flowchart's
 * STEP 1-2 ("Evaluate every candidate phase" / "Build &amp; invert the
 * phase matrix", see {@code docs/solver_flowchart_target.png}) -- against
 * pycalphad-traceable reference values, superseding the former
 * {@code EMatNCTest} (which checked only internal self-consistency: that
 * {@code eMatNC} was not the identity placeholder, and that it matched a
 * finite-difference re-derivation of ITS OWN {@code dely} output. Neither
 * check could catch a wrong-but-internally-consistent implementation, and
 * neither touched pycalphad at all).
 *
 * <h2>Reference generation: gated, not report-only</h2>
 * The Sundman bordered phase matrix (2015 Eq. 40) --
 * <pre>
 *     M = [ G_YY   C^T ]        e = M^-1 (top-left nip x nip block)
 *         [ C       0  ]
 * </pre>
 * is built from {@code (G_YY, G_Y, moles, dMoles_dy)} at each of
 * {@link CefContractTest}'s own 11 cases -- the same cases, same
 * {@code (T, P, y)} points, same {@code G_YY}/{@code G_Y}/{@code moles}/
 * {@code dMoles_dy} values that test already gates against pycalphad
 * 0.11.1 (R-constant corrected; see that class's javadoc) across CEF's
 * structural variety: 2-sublattice ordinary phases (BCC_A2, with a fixed
 * vacancy sublattice), a stoichiometric 2-sublattice ordered phase
 * (V2ZR), a 1-sublattice solution (LIQUID), and two 5-component,
 * 2-sublattice phases with an active magnetic contribution and (for one)
 * an unrepresented-pressure-term residual (CEMENTITE, FCC_A1).
 *
 * <p>Reusing {@code CefContractTest}'s already-pycalphad-gated model
 * values, rather than re-deriving fresh pycalphad references directly at
 * the phase-matrix level, avoids re-doing pycalphad's own unit/R-constant
 * conventions a second time (a real mistake made once already while
 * scoping this test -- pycalphad's raw {@code Model.GM} is per mole of
 * ATOMS, not per mole of formula unit, and its R constant differs from
 * this project's by a small but non-negligible amount for
 * R-proportional terms). This isolates exactly what is NEW at this
 * stage: the bordered-matrix construction and inversion
 * ({@code PhaseMatrixAssembler.compute}), not the underlying model
 * values (already independently gated by {@code CefContractTest}).
 *
 * <p>The reference {@code eMat}/{@code cG}/{@code cA}/{@code eMatNC} for
 * each case is an INDEPENDENT inversion (numpy {@code linalg.inv}, not
 * this project's {@code Matrix} class) of exactly those same gated
 * {@code (G_YY, G_Y, moles, dMoles_dy)} values, run once and pinned here
 * as literals -- see the reference-generation script this session's
 * scratchpad. Every quantity is compared, gated (no report-only
 * exceptions), matching {@code CefContractTest}'s own standard.
 *
 * <h2>What this covers that {@code CefContractTest} does not</h2>
 * {@code CefContractTest} verifies the MODEL layer only -- raw
 * {@code G}/{@code dG_dy}/{@code d2G_dy2}/{@code moles}/{@code dMoles_dy}.
 * It never builds or inverts the bordered phase matrix, so a bug in
 * {@code PhaseMatrixAssembler}'s Lagrange-multiplier border construction,
 * its matrix inversion, or its {@code cG}/{@code cA}/{@code eMatNC}
 * derivation would be invisible to it. This test is the CALCULATION-layer
 * counterpart: same verified model inputs, but checking the Newton-step
 * response coefficients ({@code eMat}, {@code cG}, {@code cA},
 * {@code eMatNC}) a multiphase solver actually assembles from them
 * (Sundman Eq. 40/43-44/58).
 */
public class PhaseMatrixAssemblerContractTest {

    private static int failures = 0;

    /*
     * The reference eMat/cG/cA/eMatNC are an independent numpy inversion
     * of CefContractTest's own reference G_YY/G_Y/moles/dMoles_dy -- which
     * that test itself only gates to 1e-4 absolute / 5e-6 relative against
     * pycalphad (TIGHT_TOL there). PhaseMatrixAssembler.compute() here
     * runs on CefGibbs's ACTUAL G_YY/G_Y (not the reference table's
     * values), so any discrepancy at that ~1e-4/5e-6 floor propagates
     * through the matrix inversion into eMat/cG/cA/eMatNC.
     *
     * Empirically this floor propagates to ~1e-5 relative error in every
     * quantity checked here (measured directly: max observed relative
     * error across all 11 cases was ~9e-5) -- REL_TOL below is set an
     * order of magnitude above that measured ceiling, comfortably beyond
     * floating-point noise but far below the magnitude a genuine
     * PhaseMatrixAssembler defect (a sign error, a missing border term,
     * a transposed index) would produce, which shows up as O(1) relative
     * error or a qualitatively wrong matrix shape/symmetry, not a
     * borderline-tolerance miss.
     */
    private static final double ABS_TOL = 1.0e-9;
    private static final double REL_TOL = 1.0e-3;

    public static void main(String[] args) throws Exception {

        for (Case c : CASES) {
            checkCase(c);
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL PHASE-MATRIX-ASSEMBLER CONTRACT CHECKS PASSED");
        } else {
            System.out.println(failures + " PHASE-MATRIX-ASSEMBLER CONTRACT CHECK(S) FAILED");
            throw new AssertionError(failures + " check(s) failed -- see log above.");
        }
    }

    private static void checkCase(Case c) throws Exception {

        System.out.println("=== " + c.label + " ===");

        TdbParser parser = new TdbParser();
        parser.load(c.tdb);

        List<String> elements = Arrays.asList(c.elements);

        @SuppressWarnings("unchecked")
        List<CefGibbs> models = (List<CefGibbs>)
                parser.buildPhaseModels(elements, Arrays.asList(c.phase));

        CefGibbs pm = models.get(0);
        GibbsEnergyModel gm = PhaseModelFactory.toGibbsModel(pm, elements);

        // Chemical potentials are irrelevant to eMat/cG/cA/eMatNC (they
        // depend only on G_YY, G_Y, moles, dMoles_dy at this y) -- use
        // zero, matching how the reference was generated.
        double[] mu0 = new double[c.numComponents];

        PhaseEquilData data =
                PhaseMatrixAssembler.compute(gm, c.T, c.P, c.y, 0.0, 0.0, mu0);

        assertMatrixClose(c.label + ": eMat", data.eMat, c.eMat);
        assertArrayClose(c.label + ": cG", data.cG, c.cG);
        assertMatrixClose(c.label + ": cA", cAFromDely(gm, c, mu0, data), c.cA);
        assertMatrixClose(c.label + ": eMatNC", data.eMatNC, c.eMatNC);
    }

    /**
     * {@link PhaseEquilData} does not expose {@code cA} directly (only the
     * combined {@code dely}/{@code eMatNC}), so recover {@code cA[A][i]}
     * -- the {@code dY_i/dmu_A} sensitivity from Sundman Eq. 44 -- by
     * calling {@link PhaseMatrixAssembler#compute} once per component with
     * mu set to a unit vector along that component and subtracting off
     * the mu=0 baseline (cG/cT/cP), isolating exactly the
     * {@code eMat @ dMoles_dy[A]} term {@code cA[A]} is defined as.
     */
    private static double[][] cAFromDely(GibbsEnergyModel gm, Case c,
                                          double[] mu0, PhaseEquilData baseline) {
        int nc = c.numComponents;
        int nip = c.y.length;
        double[][] cA = new double[nc][nip];

        for (int A = 0; A < nc; A++) {
            double[] muUnit = new double[nc];
            muUnit[A] = 1.0;
            PhaseEquilData withMu =
                    PhaseMatrixAssembler.compute(gm, c.T, c.P, c.y, 0.0, 0.0, muUnit);
            for (int i = 0; i < nip; i++) {
                cA[A][i] = withMu.dely[i] - baseline.dely[i];
            }
        }
        return cA;
    }

    private static void assertArrayClose(String label, double[] actual, double[] expected) {
        boolean ok = actual.length == expected.length;
        double maxErr = 0.0;
        if (ok) {
            for (int i = 0; i < actual.length; i++) {
                double tol = ABS_TOL + REL_TOL * Math.abs(expected[i]);
                double err = Math.abs(actual[i] - expected[i]);
                maxErr = Math.max(maxErr, err);
                if (err > tol) { ok = false; }
            }
        }
        if (ok) {
            System.out.println("  PASS: " + label + " (max err=" + maxErr + ")");
        } else {
            System.out.println("  FAIL: " + label
                    + " expected=" + Arrays.toString(expected)
                    + " actual=" + Arrays.toString(actual));
            failures++;
        }
    }

    private static void assertMatrixClose(String label, double[][] actual, double[][] expected) {
        boolean ok = actual.length == expected.length;
        double maxErr = 0.0;
        if (ok) {
            for (int i = 0; i < actual.length; i++) {
                if (actual[i].length != expected[i].length) { ok = false; break; }
                for (int j = 0; j < actual[i].length; j++) {
                    double tol = ABS_TOL + REL_TOL * Math.abs(expected[i][j]);
                    double err = Math.abs(actual[i][j] - expected[i][j]);
                    maxErr = Math.max(maxErr, err);
                    if (err > tol) { ok = false; }
                }
            }
        }
        if (ok) {
            System.out.println("  PASS: " + label + " (max err=" + maxErr + ")");
        } else {
            System.out.println("  FAIL: " + label);
            System.out.println("    expected=" + Arrays.deepToString(expected));
            System.out.println("    actual  =" + Arrays.deepToString(actual));
            failures++;
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Reference cases
    // ────────────────────────────────────────────────────────────────

    private static final class Case {
        final String label;
        final String tdb, phase;
        final String[] elements;
        final int numComponents;
        final double T, P;
        final double[] y;
        final double[][] eMat;
        final double[] cG;
        final double[][] cA;
        final double[][] eMatNC;

        Case(String label, String tdb, String phase, String[] elements,
             double T, double P, double[] y,
             double[][] eMat, double[] cG, double[][] cA, double[][] eMatNC) {
            this.label = label;
            this.tdb = tdb; this.phase = phase; this.elements = elements;
            this.numComponents = elements.length;
            this.T = T; this.P = P; this.y = y;
            this.eMat = eMat; this.cG = cG; this.cA = cA; this.eMatNC = eMatNC;
        }
    }

    /*
     * Every (T, P, y) point below is copied verbatim from
     * CefContractTest.CASES -- same phase, same site-fraction vector --
     * so the (G_YY, G_Y, moles, dMoles_dy) PhaseMatrixAssembler consumes
     * here are exactly the values that test already gates against
     * pycalphad 0.11.1. eMat/cG/cA/eMatNC below are an INDEPENDENT numpy
     * inversion of those same values (not re-derived from pycalphad
     * directly) -- see this session's scratchpad reference-generation
     * script.
     */
    private static final Case[] CASES = {

        new Case("BCC_A2 T=1200 (2-sublattice, fixed VA sublattice)",
            "data/VZR-re2.TDB", "BCC_A2", new String[]{"V", "ZR"},
            1200.0, 101325.0, new double[]{0.8, 0.2, 1.0},
            new double[][]{
                { 1.7633039153e-04, -1.7633039153e-04,  0.0},
                {-1.7633039153e-04,  1.7633039153e-04,  0.0},
                { 0.0,               0.0,               0.0},
            },
            new double[]{-1.3202051701e+00, 1.3202051701e+00, 0.0},
            new double[][]{
                { 1.7633039153e-04, -1.7633039153e-04, 0.0},
                {-1.7633039153e-04,  1.7633039153e-04, 0.0},
            },
            new double[][]{
                { 1.7633039153e-04, -1.7633039153e-04},
                {-1.7633039153e-04,  1.7633039153e-04},
            }),

        new Case("BCC_A2 T=800",
            "data/VZR-re2.TDB", "BCC_A2", new String[]{"V", "ZR"},
            800.0, 101325.0, new double[]{0.3, 0.7, 1.0},
            new double[][]{
                {-5.8459052494e-05,  5.8459052494e-05, 0.0},
                { 5.8459052494e-05, -5.8459052494e-05, 0.0},
                { 0.0,               0.0,              0.0},
            },
            new double[]{6.6979032925e-01, -6.6979032925e-01, 0.0},
            new double[][]{
                {-5.8459052494e-05,  5.8459052494e-05, 0.0},
                { 5.8459052494e-05, -5.8459052494e-05, 0.0},
            },
            new double[][]{
                {-5.8459052494e-05,  5.8459052494e-05},
                { 5.8459052494e-05, -5.8459052494e-05},
            }),

        new Case("BCC_A2 T=1500 (near-pure endmember, near-singular)",
            "data/VZR-re2.TDB", "BCC_A2", new String[]{"V", "ZR"},
            1500.0, 101325.0, new double[]{0.999999, 1.0e-6, 1.0},
            new double[][]{
                { 8.01818874e-11, -8.01818874e-11, 0.0},
                {-8.01818874e-11,  8.01818874e-11, 0.0},
                { 0.0,             0.0,            0.0},
            },
            new double[]{-1.2587080867e-05, 1.2587080867e-05, 0.0},
            new double[][]{
                { 8.01818874e-11, -8.01818874e-11, 0.0},
                {-8.01818874e-11,  8.01818874e-11, 0.0},
            },
            new double[][]{
                { 8.01818874e-11, -8.01818874e-11},
                {-8.01818874e-11,  8.01818874e-11},
            }),

        new Case("V2ZR T=1200 (stoichiometric ordered 2-sublattice)",
            "data/VZR-re2.TDB", "V2ZR", new String[]{"V", "ZR"},
            1200.0, 101325.0, new double[]{0.6, 0.4, 0.6, 0.4},
            new double[][]{
                { 2.7541145264e-05, -2.7541145264e-05, -1.2222933275e-05,  1.2222933275e-05},
                {-2.7541145264e-05,  2.7541145264e-05,  1.2222933275e-05, -1.2222933275e-05},
                {-1.2222933275e-05,  1.2222933275e-05,  7.3703812406e-05, -7.3703812406e-05},
                { 1.2222933275e-05, -1.2222933275e-05, -7.3703812406e-05,  7.3703812406e-05},
            },
            new double[]{0.0337039202, -0.0337039202, -1.5645431117, 1.5645431117},
            new double[][]{
                { 4.2859357253e-05, -4.2859357253e-05,  4.9257945856e-05, -4.9257945856e-05},
                {-4.2859357253e-05,  4.2859357253e-05, -4.9257945856e-05,  4.9257945856e-05},
            },
            new double[][]{
                { 0.0001349767, -0.0001349767},
                {-0.0001349767,  0.0001349767},
            }),

        new Case("V2ZR T=900",
            "data/VZR-re2.TDB", "V2ZR", new String[]{"V", "ZR"},
            900.0, 101325.0, new double[]{0.3, 0.7, 0.8, 0.2},
            new double[][]{
                { 3.6721737874e-05, -3.6721737874e-05,  3.1489196737e-05, -3.1489196737e-05},
                {-3.6721737874e-05,  3.6721737874e-05, -3.1489196737e-05,  3.1489196737e-05},
                { 3.1489196737e-05, -3.1489196737e-05,  5.5556480650e-05, -5.5556480650e-05},
                {-3.1489196737e-05,  3.1489196737e-05, -5.5556480650e-05,  5.5556480650e-05},
            },
            new double[]{-0.4263131723, 0.4263131723, -1.2121693824, 1.2121693824},
            new double[][]{
                { 0.0001049327, -0.0001049327,  0.0001185349, -0.0001185349},
                {-0.0001049327,  0.0001049327, -0.0001185349,  0.0001185349},
            },
            new double[][]{
                { 0.0003284002, -0.0003284002},
                {-0.0003284002,  0.0003284002},
            }),

        new Case("LIQUID T=1500 (1-sublattice solution)",
            "data/VZR-re2.TDB", "LIQUID", new String[]{"V", "ZR"},
            1500.0, 101325.0, new double[]{0.45, 0.55},
            new double[][]{
                { 2.9712624814e-05, -2.9712624814e-05},
                {-2.9712624814e-05,  2.9712624814e-05},
            },
            new double[]{-0.4599611019, 0.4599611019},
            new double[][]{
                { 2.9712624814e-05, -2.9712624814e-05},
                {-2.9712624814e-05,  2.9712624814e-05},
            },
            new double[][]{
                { 2.9712624814e-05, -2.9712624814e-05},
                {-2.9712624814e-05,  2.9712624814e-05},
            }),

        new Case("LIQUID T=2500 (near-pure, near-singular)",
            "data/VZR-re2.TDB", "LIQUID", new String[]{"V", "ZR"},
            2500.0, 101325.0, new double[]{0.01, 0.99},
            new double[][]{
                { 4.6097137322e-07, -4.6097137322e-07},
                {-4.6097137322e-07,  4.6097137322e-07},
            },
            new double[]{0.0297059915, -0.0297059915},
            new double[][]{
                { 4.6097137322e-07, -4.6097137322e-07},
                {-4.6097137322e-07,  4.6097137322e-07},
            },
            new double[][]{
                { 4.6097137322e-07, -4.6097137322e-07},
                {-4.6097137322e-07,  4.6097137322e-07},
            }),

        new Case("CEMENTITE T=1000 (5-component, magnetic, unrepresented-P residual)",
            "data/steel1.TDB", "CEMENTITE", new String[]{"CR", "FE", "MO", "V", "C"},
            1000.0, 101325.0, new double[]{0.2, 0.5, 0.1, 0.2, 1.0},
            new double[][]{
                { 7.8620059051e-06, -5.0608763256e-06, -1.7919791267e-06, -1.0091504528e-06, 0.0},
                {-5.0608763256e-06,  8.1843890529e-06, -1.2899849744e-06, -1.8335277529e-06, 0.0},
                {-1.7919791267e-06, -1.2899849744e-06,  4.2572039955e-06, -1.1752398945e-06, 0.0},
                {-1.0091504528e-06, -1.8335277529e-06, -1.1752398945e-06,  4.0179181002e-06, 0.0},
                { 0.0,               0.0,               0.0,               0.0,              0.0},
            },
            new double[]{2.6954059671e-01, -5.7369317349e-01, -2.7767444293e-01, 5.8182701970e-01, 0.0},
            new double[][]{
                { 2.3586017715e-05, -1.5182628977e-05, -5.3759373801e-06, -3.0274513585e-06, 0.0},
                {-1.5182628977e-05,  2.4553167159e-05, -3.8699549231e-06, -5.5005832588e-06, 0.0},
                {-5.3759373801e-06, -3.8699549231e-06,  1.2771611987e-05, -3.5257196835e-06, 0.0},
                {-3.0274513585e-06, -5.5005832588e-06, -3.5257196835e-06,  1.2053754301e-05, 0.0},
                { 0.0,               0.0,               0.0,               0.0,              0.0},
            },
            new double[][]{
                { 7.0758053146e-05, -4.5547886930e-05, -1.6127812140e-05, -9.0823540755e-06, 0.0},
                {-4.5547886930e-05,  7.3659501476e-05, -1.1609864769e-05, -1.6501749776e-05, 0.0},
                {-1.6127812140e-05, -1.1609864769e-05,  3.8314835960e-05, -1.0577159050e-05, 0.0},
                {-9.0823540755e-06, -1.6501749776e-05, -1.0577159050e-05,  3.6161262902e-05, 0.0},
                { 0.0,               0.0,               0.0,               0.0,              0.0},
            }),

        new Case("CEMENTITE T=1400",
            "data/steel1.TDB", "CEMENTITE", new String[]{"CR", "FE", "MO", "V", "C"},
            1400.0, 101325.0, new double[]{0.5, 0.3, 0.1, 0.1, 1.0},
            new double[][]{
                { 8.2231001394e-06, -4.8625203875e-06, -2.3639077643e-06, -9.9667198760e-07, 0.0},
                {-4.8625203875e-06,  5.7234102380e-06, -3.0987681281e-07, -5.5101303763e-07, 0.0},
                {-2.3639077643e-06, -3.0987681281e-07,  3.1037232775e-06, -4.2993870037e-07, 0.0},
                {-9.9667198760e-07, -5.5101303763e-07, -4.2993870037e-07,  1.9776237256e-06, 0.0},
                { 0.0,               0.0,               0.0,               0.0,              0.0},
            },
            new double[]{-1.4074443232e-01, -9.6328937408e-02, -8.1113685382e-02, 3.1818705511e-01, 0.0},
            new double[][]{
                { 2.4669300418e-05, -1.4587561163e-05, -7.0917232929e-06, -2.9900159628e-06, 0.0},
                {-1.4587561163e-05,  1.7170230714e-05, -9.2963043843e-07, -1.6530391129e-06, 0.0},
                {-7.0917232929e-06, -9.2963043843e-07,  9.3111698324e-06, -1.2898161011e-06, 0.0},
                {-2.9900159628e-06, -1.6530391129e-06, -1.2898161011e-06,  5.9328711768e-06, 0.0},
                { 0.0,               0.0,               0.0,               0.0,              0.0},
            },
            new double[][]{
                { 7.4007901255e-05, -4.3762683488e-05, -2.1275169879e-05, -8.9700478884e-06, 0.0},
                {-4.3762683488e-05,  5.1510692142e-05, -2.7888913153e-06, -4.9591173386e-06, 0.0},
                {-2.1275169879e-05, -2.7888913153e-06,  2.7933509497e-05, -3.8694483033e-06, 0.0},
                {-8.9700478884e-06, -4.9591173386e-06, -3.8694483033e-06,  1.7798613530e-05, 0.0},
                { 0.0,               0.0,               0.0,               0.0,              0.0},
            }),

        new Case("FCC_A1 T=1000 (5-component, unrepresented-P residual)",
            "data/AlCoCrNi-volume.TDB", "FCC_A1", new String[]{"AL", "CO", "CR", "NI"},
            1000.0, 101325.0, new double[]{0.4, 0.3, 0.2, 0.1, 1.0},
            new double[][]{
                { 7.4878865631e-06, -5.5108421543e-07, -1.0731986989e-05,  3.7951846409e-06, 0.0},
                {-5.5108421543e-07,  1.5832882537e-05, -6.0843996189e-06, -9.1973987029e-06, 0.0},
                {-1.0731986989e-05, -6.0843996189e-06,  3.1039652942e-05, -1.4223266335e-05, 0.0},
                { 3.7951846409e-06, -9.1973987029e-06, -1.4223266335e-05,  1.9625480397e-05, 0.0},
                { 0.0,               0.0,               0.0,               0.0,              0.0},
            },
            new double[]{4.8955486932e-01, -1.7712704892e-02, -1.4647005798e+00, 9.9285841538e-01, 0.0},
            new double[][]{
                { 7.4878865631e-06, -5.5108421543e-07, -1.0731986989e-05,  3.7951846409e-06, 0.0},
                {-5.5108421543e-07,  1.5832882537e-05, -6.0843996189e-06, -9.1973987029e-06, 0.0},
                {-1.0731986989e-05, -6.0843996189e-06,  3.1039652942e-05, -1.4223266335e-05, 0.0},
                { 3.7951846409e-06, -9.1973987029e-06, -1.4223266335e-05,  1.9625480397e-05, 0.0},
            },
            new double[][]{
                { 7.4878865631e-06, -5.5108421543e-07, -1.0731986989e-05,  3.7951846409e-06},
                {-5.5108421543e-07,  1.5832882537e-05, -6.0843996189e-06, -9.1973987029e-06},
                {-1.0731986989e-05, -6.0843996189e-06,  3.1039652942e-05, -1.4223266335e-05},
                { 3.7951846409e-06, -9.1973987029e-06, -1.4223266335e-05,  1.9625480397e-05},
            }),

        new Case("FCC_A1 T=1600",
            "data/AlCoCrNi-volume.TDB", "FCC_A1", new String[]{"AL", "CO", "CR", "NI"},
            1600.0, 101325.0, new double[]{0.1, 0.6, 0.2, 0.1, 1.0},
            new double[][]{
                { 4.7092965135e-06, -2.4308192951e-06, -3.6833379180e-06,  1.4048606995e-06, 0.0},
                {-2.4308192951e-06,  1.3425603048e-05, -5.1107136191e-06, -5.8840701334e-06, 0.0},
                {-3.6833379180e-06, -5.1107136191e-06,  1.1197927759e-05, -2.4038762215e-06, 0.0},
                { 1.4048606995e-06, -5.8840701334e-06, -2.4038762215e-06,  6.8830856554e-06, 0.0},
                { 0.0,               0.0,               0.0,               0.0,              0.0},
            },
            new double[]{4.1912295420e-01, -3.4143960326e-01, -4.0670483121e-01, 3.2902148027e-01, 0.0},
            new double[][]{
                { 4.7092965135e-06, -2.4308192951e-06, -3.6833379180e-06,  1.4048606995e-06, 0.0},
                {-2.4308192951e-06,  1.3425603048e-05, -5.1107136191e-06, -5.8840701334e-06, 0.0},
                {-3.6833379180e-06, -5.1107136191e-06,  1.1197927759e-05, -2.4038762215e-06, 0.0},
                { 1.4048606995e-06, -5.8840701334e-06, -2.4038762215e-06,  6.8830856554e-06, 0.0},
            },
            new double[][]{
                { 4.7092965135e-06, -2.4308192951e-06, -3.6833379180e-06,  1.4048606995e-06},
                {-2.4308192951e-06,  1.3425603048e-05, -5.1107136191e-06, -5.8840701334e-06},
                {-3.6833379180e-06, -5.1107136191e-06,  1.1197927759e-05, -2.4038762215e-06},
                { 1.4048606995e-06, -5.8840701334e-06, -2.4038762215e-06,  6.8830856554e-06},
            }),
    };
}
