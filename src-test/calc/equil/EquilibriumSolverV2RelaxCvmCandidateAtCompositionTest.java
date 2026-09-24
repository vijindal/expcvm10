package calc.equil;

import org.junit.jupiter.api.Test;

import system.model.cvm.CecTerm;
import system.model.cvm.CvmGibbsModel;
import system.model.cvm.CvmPhaseData;
import system.model.cvm.CvmPhaseSpec;
import system.model.unary.ElementGibbs;
import system.model.PhaseModelFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4G, part A: white-box test of {@code
 * EquilibriumSolverV2#relaxCvmCandidateAtComposition}, the generalized
 * (composition-parameterized) form of {@code relaxCvmCandidate} introduced
 * so the boundary/ZPF seed path can relax a CVM candidate at a SEARCHED
 * trial composition rather than always at {@code targetComposition()} (see
 * the Phase 4F audit for why the two need different compositions).
 *
 * <p>Reflection is used only because the method under test is intentionally
 * {@code private} (an internal mechanism shared by {@code
 * relaxCvmCandidate} and {@code bestSeedConstitution}, not a public API) --
 * the same pattern {@code GridMinimizerCompositionSamplingTest} already
 * uses for {@code GridMinimizer#sampleCompositions}.
 */
class EquilibriumSolverV2RelaxCvmCandidateAtCompositionTest {

    private static final double T = 1000.0;
    private static final double P = 101325.0;

    /**
     * Builds a fresh {@link EquilibriumSolverV2} with its private {@code
     * T}/{@code P} fields set to this test's fixture conditions.
     *
     * <p>{@code relaxCvmCandidateAtComposition} reads {@code this.T}/{@code
     * this.P} (the enclosing/outer solver's own fields, normally set by
     * {@code setUpBoundarySolve}/{@code solve} before {@code
     * bestSeedConstitution} ever runs) rather than taking them as
     * parameters -- calling it on a solver that never went through {@code
     * solve()} would silently relax at T=P=0, which is not a meaningful
     * condition for this fixture's CEC terms. Reflection is used only
     * because there is no public setter for a solver's T/P outside {@code
     * solve()} itself.
     */
    private static EquilibriumSolverV2 newSolverAt(double t, double p) throws Exception {
        EquilibriumSolverV2 solver = new EquilibriumSolverV2();
        var fT = EquilibriumSolverV2.class.getDeclaredField("T");
        fT.setAccessible(true);
        fT.set(solver, t);
        var fP = EquilibriumSolverV2.class.getDeclaredField("P");
        fP.setAccessible(true);
        fP.set(solver, p);
        /*
         * Matches EquilibriumSolverV2CvmSinglePhaseEndToEndTest's own
         * tolerance loosening: this fixture's CVM Hessian spans O(1e4) to
         * O(1e7), so the solver's default 1e-10 residual tolerance is
         * tighter than this problem's numerical conditioning supports.
         * relaxCvmCandidateAtComposition propagates the OUTER solver's
         * own `tolerance` field to its nested solver, so it must be set
         * here exactly as a real boundary solver would have it set.
         */
        solver.setTolerance(1.0e-8);
        return solver;
    }

    /**
     * Invokes the private {@code relaxCvmCandidateAtComposition(CvmGibbsModel,
     * double[])} method and returns its result object (also accessed via
     * reflection, since {@code CvmCandidateRelaxation} is itself a private
     * nested class).
     */
    private static Object relaxAtComposition(
            EquilibriumSolverV2 solver, CvmGibbsModel candidate, double[] x)
            throws Exception {

        Method m = EquilibriumSolverV2.class.getDeclaredMethod(
                "relaxCvmCandidateAtComposition",
                system.model.cvm.CvmGibbsModel.class,
                double[].class);
        m.setAccessible(true);
        return m.invoke(solver, candidate, x);
    }

    private static double[] fieldY(Object relaxation) throws Exception {
        var f = relaxation.getClass().getDeclaredField("y");
        f.setAccessible(true);
        return (double[]) f.get(relaxation);
    }

    private static double fieldG(Object relaxation) throws Exception {
        var f = relaxation.getClass().getDeclaredField("G");
        f.setAccessible(true);
        return (double) f.get(relaxation);
    }

    private static double[] fieldMA(Object relaxation) throws Exception {
        var f = relaxation.getClass().getDeclaredField("mA");
        f.setAccessible(true);
        return (double[]) f.get(relaxation);
    }

    /**
     * At a trial composition where the naive (unrelaxed) random-
     * approximation seed is NOT already stationary, the relaxed u must
     * differ from the raw {@code getInitialInternalVars} seed -- proving
     * the nested solver actually performed a Newton relaxation rather than
     * returning the seed verbatim (mirrors {@code
     * GridMinimizerInnerSolverWiringTest#hullPointYIsEquilibratedNotRawSeed}'s
     * same check for the production hull-search path).
     */
    @Test
    void relaxesInternalVariablesRatherThanReturningRawSeed() throws Exception {

        CvmGibbsModel cvm = buildCvmModel();
        double[] xTrial = {0.6, 0.4};

        double[] rawSeed = cvm.getInitialInternalVars(xTrial);

        EquilibriumSolverV2 solver = newSolverAt(T, P);
        Object relaxed = relaxAtComposition(solver, cvm, xTrial);

        assertNotNull(relaxed, "Relaxation should succeed for this fixture/composition");

        double[] relaxedY = fieldY(relaxed);
        assertNotNull(relaxedY);

        boolean differs = false;
        for (int i = 0; i < rawSeed.length; i++) {
            if (Math.abs(rawSeed[i] - relaxedY[i]) > 1.0e-9) {
                differs = true;
                break;
            }
        }
        assertTrue(differs,
                "Relaxed Y must differ from the raw unequilibrated seed "
                + "(non-trivial CEC terms bias the true stationary state "
                + "away from the naive random-approximation guess)");
    }

    /**
     * The relaxed state's composition (trailing x-block, or equivalently
     * moles/totalMoles) must reproduce the REQUESTED trial composition,
     * not any other value -- proving the inner single-phase mass balance
     * actually pinned the nested solve to trialComposition, exactly the
     * guarantee {@code GridMinimizerInnerSolverWiringTest
     * #innerSolveIsSinglePhaseAndIndependent} already establishes for this
     * same nested-solver pattern.
     */
    @Test
    void returnedCompositionMatchesTrialComposition() throws Exception {

        CvmGibbsModel cvm = buildCvmModel();
        double[] xTrial = {0.3, 0.7};

        EquilibriumSolverV2 solver = newSolverAt(T, P);
        Object relaxed = relaxAtComposition(solver, cvm, xTrial);

        assertNotNull(relaxed);

        double[] relaxedY = fieldY(relaxed);
        assertTrue(cvm.isValid(relaxedY),
                "Relaxed Y must be a valid CVM state (cluster-probability "
                + "admissible, per CvmGibbsModel#isValid)");

        double[] xFromY = cvm.compositionFromInternal(relaxedY);
        assertEquals(xTrial[0], xFromY[0], 1.0e-6);
        assertEquals(xTrial[1], xFromY[1], 1.0e-6);

        double[] mA = fieldMA(relaxed);
        double totalM = mA[0] + mA[1];
        assertEquals(xTrial[0], mA[0] / totalM, 1.0e-6);
        assertEquals(xTrial[1], mA[1] / totalM, 1.0e-6);

        assertTrue(Double.isFinite(fieldG(relaxed)), "G must be finite");
    }

    /**
     * Two different trial compositions must each drive the nested solve to
     * their OWN requested composition, independently -- confirming the
     * composition really is a free parameter of the helper (not silently
     * hardcoded to {@code targetComposition()} the way the original
     * single-composition {@code relaxCvmCandidate} was).
     */
    @Test
    void differentTrialCompositionsProduceDifferentRelaxedStates() throws Exception {

        CvmGibbsModel cvm = buildCvmModel();

        EquilibriumSolverV2 solver = newSolverAt(T, P);

        Object relaxedA = relaxAtComposition(solver, cvm, new double[]{0.2, 0.8});
        Object relaxedB = relaxAtComposition(solver, cvm, new double[]{0.8, 0.2});

        assertNotNull(relaxedA);
        assertNotNull(relaxedB);

        double[] xA = cvm.compositionFromInternal(fieldY(relaxedA));
        double[] xB = cvm.compositionFromInternal(fieldY(relaxedB));

        assertEquals(0.2, xA[0], 1.0e-6);
        assertEquals(0.8, xB[0], 1.0e-6);
        assertNotEquals(xA[0], xB[0], 1.0e-3);
    }

    /**
     * The inner problem must contain exactly one candidate model and reach
     * exactly one stable phase -- verified indirectly (the method already
     * returns {@code null} on any other stable-set size, per its own
     * javadoc contract) by confirming a normal, well-posed relaxation
     * succeeds and produces single-phase-consistent moles (sum of mole
     * fractions = 1).
     */
    @Test
    void innerProblemIsSinglePhaseAndSelfContained() throws Exception {

        CvmGibbsModel cvm = buildCvmModel();
        double[] xTrial = {0.5, 0.5};

        EquilibriumSolverV2 solver = newSolverAt(T, P);
        Object relaxed = relaxAtComposition(solver, cvm, xTrial);

        assertNotNull(relaxed);

        double[] mA = fieldMA(relaxed);
        assertEquals(2, mA.length, "moles length must equal numComponents()");
        assertEquals(1.0, mA[0] + mA[1], 1.0e-6,
                "Single-phase mass balance: mole fractions must sum to 1");
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
                new CecTerm("v22AB", 200.0, -0.02),
                new CecTerm("v21AB", -300.0, 0.05));

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

        String[] u2Names = {"v4AB", "v3AB", "v22AB", "v21AB", "xA", "xB"};
        String[] eNames = {"v4AB", "v3AB", "v22AB", "v21AB"};

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
