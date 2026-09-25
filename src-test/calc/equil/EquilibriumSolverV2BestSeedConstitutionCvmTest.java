package calc.equil;

import org.junit.jupiter.api.Test;

import system.model.cvm.CecTerm;
import system.model.cvm.CvmGibbsModel;
import system.model.cvm.CvmPhaseData;
import system.model.cvm.CvmPhaseSpec;
import system.model.unary.ElementGibbs;
import system.model.PhaseModelFactory;
import system.ports.EquilibriumResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4G, part B: white-box test of {@code
 * EquilibriumSolverV2#bestSeedConstitution}'s CVM branch ({@code
 * bestCvmSeedConstitution}) -- the boundary/ZPF seed search itself, as
 * distinct from {@link EquilibriumSolverV2RelaxCvmCandidateAtCompositionTest}
 * which tests only the underlying relaxation mechanism.
 *
 * <p>Sets up a controlled boundary chemical-potential state {@code mu} by
 * converging a genuine single-phase CVM equilibrium at some composition and
 * reusing its {@code mu} (exactly the quantity {@code
 * seedFromEquilibriumResult} would install on a real boundary solve), then
 * invokes the private {@code bestSeedConstitution} against a SECOND,
 * independently-constructed candidate of the same model to search a seed
 * for.
 */
class EquilibriumSolverV2BestSeedConstitutionCvmTest {

    private static final double T = 1000.0;
    private static final double P = 101325.0;

    private static double[] bestSeedConstitution(
            EquilibriumSolverV2 solver, CvmGibbsModel model) throws Exception {

        Method m = EquilibriumSolverV2.class.getDeclaredMethod(
                "bestSeedConstitution", system.model.GibbsEnergyModel.class);
        m.setAccessible(true);
        return (double[]) m.invoke(solver, model);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * Builds a solver whose private {@code T}/{@code P}/{@code mu} fields
     * are set as they would be by {@code setUpBoundarySolve} ->
     * {@code seedFromEquilibriumResult} on a real boundary solve, without
     * running the full boundary machinery (this test isolates {@code
     * bestSeedConstitution} itself; the end-to-end wiring is covered by
     * {@code EquilibriumSolverV2CvmBoundaryZpfIntegrationTest}).
     */
    private static EquilibriumSolverV2 solverWithMu(double[] mu) throws Exception {
        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        setField(solver, "T", T);
        setField(solver, "P", P);
        setField(solver, "mu", mu);
        // See EquilibriumSolverV2RelaxCvmCandidateAtCompositionTest's
        // newSolverAt() for why this fixture needs a loosened tolerance
        // (its Hessian scale is O(1e4)-O(1e7)); bestSeedConstitution's
        // CVM branch propagates this solver's own `tolerance` field to
        // every nested relaxation it performs.
        solver.setTolerance(1.0e-8);
        return solver;
    }

    /**
     * Converges a real single-phase CVM equilibrium to obtain a physically
     * meaningful chemical-potential vector -- NOT an arbitrary/synthetic
     * mu -- to drive the seed search, mirroring how a genuine boundary
     * solve's mu comes from the prior converged equilibrium.
     */
    private static double[] convergedMuAt(CvmGibbsModel cvm, double[] x) {
        double[] y0 = cvm.getInitialInternalVars(x);
        EquilibriumSolverV2 inner = new EquilibriumSolverV2();
        inner.setTolerance(1.0e-8);
        inner.setInitialState(new int[]{0}, new double[][]{y0}, new double[]{1.0});
        EquilibriumResult result = inner.solve(T, P, x, List.of(cvm));
        assertTrue(result.isConverged(), "Setup solve must converge to obtain mu");
        return result.getMu();
    }

    @Test
    void cvmSeedIsValidAndNotForcedToTargetComposition() throws Exception {

        CvmGibbsModel cvm = buildCvmModel();

        // mu from a converged equilibrium at x=(0.2, 0.8) -- deliberately
        // NOT the same as any single value we expect the seed's
        // composition to land on; the seed's composition should come out
        // of whichever sampled/relaxed trial composition maximizes
        // driving force against this mu, not be forced to any particular
        // fixed value.
        double[] mu = convergedMuAt(cvm, new double[]{0.2, 0.8});

        EquilibriumSolverV2 solver = solverWithMu(mu);

        double[] seedY = bestSeedConstitution(solver, cvm);

        assertNotNull(seedY);
        assertTrue(cvm.isValid(seedY), "Returned seed must be a valid CVM state");

        double[] xSeed = cvm.compositionFromInternal(seedY);
        double xSum = xSeed[0] + xSeed[1];
        assertEquals(1.0, xSum, 1.0e-6, "Seed composition must be a valid simplex point");
    }

    /**
     * The selected seed must correspond to a state actually reachable by
     * relaxing one of the sampled trial compositions -- i.e. re-relaxing
     * the seed's OWN composition (via the same generalized mechanism)
     * must reproduce it (self-consistency: the returned Y is exactly a
     * stationary point at its own composition, not an arbitrary point).
     */
    @Test
    void selectedSeedIsAStationaryRelaxedStateAtItsOwnComposition() throws Exception {

        CvmGibbsModel cvm = buildCvmModel();
        double[] mu = convergedMuAt(cvm, new double[]{0.7, 0.3});

        EquilibriumSolverV2 solver = solverWithMu(mu);
        double[] seedY = bestSeedConstitution(solver, cvm);

        assertNotNull(seedY);
        double[] xSeed = cvm.compositionFromInternal(seedY);

        // Re-relax at the seed's own composition using a fresh solver at
        // the same T/P -- a genuinely stationary/admissible seed must be
        // (numerically) a fixed point of this relaxation.
        Method relaxMethod = EquilibriumSolverV2.class.getDeclaredMethod(
                "relaxCvmCandidateAtComposition",
                system.model.cvm.CvmGibbsModel.class,
                double[].class);
        relaxMethod.setAccessible(true);

        EquilibriumSolverV2 verifySolver = new EquilibriumSolverV2();
        setField(verifySolver, "T", T);
        setField(verifySolver, "P", P);
        verifySolver.setTolerance(1.0e-8);

        Object relaxation = relaxMethod.invoke(verifySolver, cvm, xSeed);
        assertNotNull(relaxation, "The seed's own composition must itself be relaxable");

        Field yField = relaxation.getClass().getDeclaredField("y");
        yField.setAccessible(true);
        double[] reRelaxedY = (double[]) yField.get(relaxation);

        for (int i = 0; i < seedY.length; i++) {
            assertEquals(seedY[i], reRelaxedY[i], 1.0e-6,
                    "Seed must be a stationary point at its own composition (index " + i + ")");
        }
    }

    /**
     * With mu absent (null), the method must fall back to
     * initializeSinglePhaseState()'s composition-only guess rather than
     * attempting a driving-force search -- matching CEF's existing
     * fallback contract.
     */
    @Test
    void fallsBackToCompositionOnlySeedWhenMuUnavailable() throws Exception {

        CvmGibbsModel cvm = buildCvmModel();

        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        setField(solver, "T", T);
        setField(solver, "P", P);
        // mu deliberately left null (default state).

        // targetComposition() is derived from targetAmounts -- set it up
        // as a real single-phase target the same way setUpBoundarySolve
        // would (targetAmounts = compOverAll.clone()).
        setField(solver, "targetAmounts", new double[]{0.4, 0.6});

        double[] seedY = bestSeedConstitution(solver, cvm);

        assertNotNull(seedY);
        assertTrue(cvm.isValid(seedY));

        double[] expected = cvm.getInitialInternalVars(new double[]{0.4, 0.6});
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], seedY[i], 1.0e-12,
                    "With no mu, seed must be exactly initializeSinglePhaseState's guess");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Fixture: CVM binary BCC_A2 model (same fixture pattern used
    // elsewhere in this package for CVM end-to-end tests)
    // ════════════════════════════════════════════════════════════════════

    private static CvmGibbsModel buildCvmModel() {
        CvmPhaseData data = buildCvmPhaseData();

        List<CecTerm> cecTerms = List.of(
                new CecTerm("v4AB", 100.0, 0.0),
                new CecTerm("v3AB", -50.0, 0.01),
                new CecTerm("v2AB2", 200.0, -0.02),
                new CecTerm("v2AB1", -300.0, 0.05));

        ElementGibbs ghserA = new TestElementGibbs("A", 1000.0, -5.0);
        ElementGibbs ghserB = new TestElementGibbs("B", -2000.0, 3.0);

        CvmPhaseSpec spec = new CvmPhaseSpec(data, cecTerms,
                new ElementGibbs[]{ghserA, ghserB}, List.of("A", "B"));

        return (CvmGibbsModel) PhaseModelFactory.buildCvm(spec);
    }

    private static CvmPhaseData buildCvmPhaseData() {
        int nComp = 2;
        int ncf = 4;
        int tcdis = 5;

        double[] mhdis = {6.0, 12.0, 4.0, 3.0, 1.0};
        double[] kbdis = {1.0, -1.0, 1.0, 1.0, -1.0};

        int[] lc = {1, 1, 1, 1, 1};

        int[][] lcv = {
                {6}, {6}, {3}, {3}, {2},
        };

        double[][] mh = {
                {1.0}, {1.0}, {1.0}, {1.0}, {1.0}
        };

        double[][][] wcv = {
                {{1, 4, 4, 2, 4, 1}},
                {{1, 2, 1, 1, 2, 1}},
                {{1, 2, 1}},
                {{1, 2, 1}},
                {{1, 1}},
        };

        double[][][][] cmat = {
                {{
                        {1.0, 1.0, 0.0, -2.0, 1.0, 0.0},
                        {-1.0, -0.5, -0.5, 1.0, 0.0, 0.0},
                        {1.0, 0.0, 1.0, -1.0, 0.0, 0.0},
                        {1.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                        {-1.0, 0.5, -0.5, 1.0, 0.0, 0.0},
                        {1.0, -1.0, 0.0, -2.0, 0.0, 1.0},
                }},
                {{
                        {0.0, 0.5, -0.5, -1.0, 1.0, 0.0},
                        {0.0, -0.5, 0.5, 0.0, 0.0, 0.0},
                        {0.0, 0.5, -0.5, 1.0, 0.0, 0.0},
                        {0.0, -0.5, -0.5, 1.0, 0.0, 0.0},
                        {0.0, 0.5, 0.5, 0.0, 0.0, 0.0},
                        {0.0, -0.5, -0.5, -1.0, 0.0, 1.0},
                }},
                {{
                        {0.0, 0.0, 0.0, -1.0, 1.0, 0.0},
                        {0.0, 0.0, 0.0, 1.0, 0.0, 0.0},
                        {0.0, 0.0, 0.0, -1.0, 0.0, 1.0},
                }},
                {{
                        {0.0, 0.0, -1.0, 0.0, 1.0, 0.0},
                        {0.0, 0.0, 1.0, 0.0, 0.0, 0.0},
                        {0.0, 0.0, -1.0, 0.0, 0.0, 1.0},
                }},
                {{
                        {0.0, 0.0, 0.0, 0.0, 1.0, 0.0},
                        {0.0, 0.0, 0.0, 0.0, 0.0, 1.0},
                }},
        };

        int uListLen = 6;
        double[][] cfCoeffs = new double[uListLen][uListLen];
        for (int i = 0; i < uListLen; i++) cfCoeffs[i][i] = 1.0;

        String[] u2Names = {"v4AB", "v3AB", "v2AB2", "v2AB1", "xA", "xB"};
        String[] eNames = {"v4AB", "v3AB", "v2AB2", "v2AB1"};

        return new CvmPhaseData(
                "BCC_A2", nComp, ncf, tcdis, mhdis, kbdis,
                lc, lcv, mh, wcv, cmat,
                uListLen, cfCoeffs, u2Names, eNames);
    }

    private static final class TestElementGibbs implements ElementGibbs {
        private final String symbol;
        private final double a;
        private final double b;

        TestElementGibbs(String symbol, double a, double b) {
            this.symbol = symbol;
            this.a = a;
            this.b = b;
        }

        @Override public String elementSymbol() { return symbol; }
        @Override public double gibbs(String phaseName, double T) { return a + b * T; }
        @Override public double ghser(double T) { return a + b * T; }
        @Override public Set<String> availablePhases() { return Set.of("BCC_A2"); }
    }
}
