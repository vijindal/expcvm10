package calc.equil;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import system.database.TdbParser;
import system.database.tdb;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.ports.EquilibriumResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4G, part C: boundary/ZPF INTEGRATION tests that exercise the real
 * production path --
 *
 * <pre>
 *     solveBoundary()
 *         -&gt; setUpBoundarySolve()
 *             -&gt; addNewStableSlot()
 *                 -&gt; (prescribed constitution, bypassing bestSeedConstitution())
 *                    OR
 *                 -&gt; bestSeedConstitution() -&gt; bestCvmSeedConstitution()
 *                    (automatic composition search + nested CVM relaxation)
 * </pre>
 *
 * -- for a newly-appearing CVM phase, WITHOUT bypassing PHASE-SET
 * management via {@code setInitialStateForTest} the way every earlier CVM
 * test in this package deliberately does: {@code solveBoundary} itself
 * still runs, still fixes BCC_A2 at zero and releases a composition, and
 * still performs the real boundary Newton loop.
 *
 * <p>{@link #boundarySolveInitializesNewlyAppearingCvmBccPhaseAtPrescribedComposition}
 * supplies BCC_A2's ONE-TIME starting {@code y} directly via {@link
 * EquilibriumSolverV2#setPrescribedBoundaryConstitution} (OpenCalphad's
 * {@code set_constitution}/{@code ycond} pattern: when the caller already
 * knows a usable starting constitution, the automatic driving-force search
 * is redundant work, not a correctness requirement -- see that method's
 * javadoc) and converges.
 *
 * <p>{@link #boundarySolveAutomaticallySeedsNewlyAppearingCvmBccPhaseWithoutPrescription}
 * supplies no prescription, forcing {@code addNewStableSlot} through the
 * automatic {@code bestCvmSeedConstitution} search instead. KNOWN
 * LIMITATION (Phase 4G.2/4G.3): that search does produce a valid, finite,
 * stationary CVM seed, but the boundary Newton trajectory from it still
 * encounters singular BCC_A2 phase matrices ({@link
 * PhaseMatrixAssembler#compute}'s {@code Matrix.inverse()} fallback) and
 * does not converge on this real fixture, so this second test currently
 * fails. The Phase 4G.3 diagnostic audit found neither seed's phase matrix
 * exactly singular in isolation (both invert; AUTO's bordered-matrix
 * condition number ~4.6e11, PRESCRIBED's ~1.3e14 -- worse, yet PRESCRIBED
 * converges and AUTO does not), so the failure is in how the boundary
 * Newton trajectory evolves from the automatic seed, not a defect in the
 * seed-selection logic itself (confirmed stationary via re-relaxation at
 * its own composition). Left failing and undiagnosed further per that
 * phase's explicit stop instruction -- not disabled, not weakened.
 *
 * <h2>Fixture: the real ternary Nb-V-Zr system</h2>
 * Uses {@code data/NbTiVZr-CVM-eName-model.TDB} -- BCC_A2 built as a real
 * TDB-driven ternary CVM model ({@link PhaseModelFactory#buildCvmFromTdb},
 * the published Jindal &amp; Lele CALPHAD 89 (2025) 102825 Table 17 CECs for
 * the Nb,V,Zr sub-system) and LAVES_C15 built as a real TDB-driven ternary
 * CEF model (same file, restricted to elements NB,V,ZR so both candidates
 * share the same 3-component space {@link EquilibriumSolverV2#solve}
 * requires). No synthetic/invented parameters are introduced here.
 *
 * <p>Uses the user-specified compositions directly: BCC_A2-rich
 * x(Nb,V,Zr)=(0.01,0.09,0.90) and LAVES_C15-rich x(Nb,V,Zr)=(0.01,0.64,0.35),
 * at T=1273K.
 */
class EquilibriumSolverV2CvmBoundaryZpfIntegrationTest {

    private static final String TDB_PATH = "data/NbTiVZr-CVM-eName-model.TDB";
    private static final double T = 1273.0;
    private static final double P = 101325.0;

    /** User-specified LAVES_C15-rich composition x(Nb,V,Zr). */
    private static final double[] LAVES_SEED_X = {0.01, 0.64, 0.35};

    /** User-specified BCC_A2-rich composition x(Nb,V,Zr). */
    private static final double[] BCC_SEED_X = {0.01, 0.09, 0.90};

    private static GibbsEnergyModel buildBcc(tdb database) {
        return PhaseModelFactory.buildCvmFromTdb(database, Arrays.asList("NB", "V", "ZR"), "BCC_A2");
    }

    private static GibbsEnergyModel buildLaves(TdbParser parser) throws Exception {
        List<GibbsEnergyModel> models = parser.buildPhaseModels(
                Arrays.asList("NB", "V", "ZR"), Arrays.asList("LAVES_C15"));
        assertEquals(1, models.size(), "Expected exactly one LAVES_C15 model.");
        return models.get(0);
    }

    @Test
    void boundarySolveInitializesNewlyAppearingCvmBccPhaseAtPrescribedComposition() throws Exception {

        tdb database = new tdb(TDB_PATH);
        GibbsEnergyModel bcc = buildBcc(database);
        assertEquals("CVM", bcc.modelType(), "BCC_A2 must be a real CVM model, not CEF");
        assertEquals(3, bcc.numComponents());

        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);
        GibbsEnergyModel laves = buildLaves(parser);
        assertEquals(3, laves.numComponents());

        List<GibbsEnergyModel> candidates = List.of(bcc, laves);

        // Single-phase LAVES_C15 seed at its own user-specified composition.
        double[] y0Laves = laves.getInitialInternalVars(LAVES_SEED_X);
        assertTrue(laves.isValid(y0Laves), "LAVES_C15 initial constitution must be valid.");

        EquilibriumSolverV2 seedSolver = new EquilibriumSolverV2();
        seedSolver.setTolerance(1.0e-6);
        seedSolver.setInitialStateForTest(
                new int[]{0},
                new double[][]{y0Laves},
                new double[]{1.0});
        EquilibriumResult seed = seedSolver.solve(T, P, LAVES_SEED_X, List.of(laves));

        assertTrue(seed.isConverged(), "Single-phase LAVES_C15 seed must converge");
        assertEquals(1, seed.getStablePhases().size());
        assertEquals("LAVES_C15", seed.getStablePhases().get(0).phaseName);

        // Prescribe BCC_A2's starting constitution at the user-specified
        // BCC_A2-rich composition directly -- OpenCalphad-style
        // set_constitution/ycond bypass of the automatic driving-force
        // search (which would otherwise scan ~PDENS*(nc-1) trial
        // compositions and relax a nested ternary CVM solve at each one,
        // several minutes for this system). The caller already knows
        // exactly where BCC_A2 should appear, so that search is skipped
        // entirely and the raw disordered-state y at BCC_SEED_X is used
        // as-is.
        double[] yBccPrescribed = bcc.getInitialInternalVars(BCC_SEED_X);
        assertTrue(bcc.isValid(yBccPrescribed),
                "Prescribed BCC_A2 constitution must be valid before handing it to the solver.");

        // Fix BCC_A2 (absent from `seed`, and a real ternary CVM model) at
        // zero amount and release component 0 (Nb)'s target amount --
        // exactly the "newly-appearing phase at a boundary" scenario
        // addNewStableSlot() exists for (Phase 4E/4F/4G).
        EquilibriumSolverV2 boundarySolver = new EquilibriumSolverV2();
        boundarySolver.setTolerance(1.0e-6);
        boundarySolver.setPrescribedBoundaryConstitution("BCC_A2", yBccPrescribed);

        EquilibriumSolverV2.BoundarySolveResult result;
        try {
            result = boundarySolver.solveBoundary(
                    T, P, LAVES_SEED_X, candidates, seed, "BCC_A2", 0.0, 0);
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "Boundary solve introducing BCC_A2 at the prescribed "
                    + "composition should not throw: " + e.getMessage(), e);
        }

        assertNotNull(result);
        assertTrue(result.equilibrium.isConverged(),
                "Boundary solve (fixing BCC_A2 at zero, releasing x(Nb)) "
                + "must converge using the prescribed CVM starting constitution");

        List<EquilibriumResult.PhaseResult> stable = result.equilibrium.getStablePhases();
        boolean hasLaves = false;
        boolean hasBcc = false;
        for (EquilibriumResult.PhaseResult pr : stable) {
            if (pr.phaseName.equals("LAVES_C15")) hasLaves = true;
            if (pr.phaseName.equals("BCC_A2")) {
                hasBcc = true;
                assertTrue(Double.isFinite(pr.G), "BCC_A2's G must be finite");
                assertTrue(bcc.isValid(pr.y), "BCC_A2's constitution must be valid");
                assertEquals("CVM", pr.modelType);
            }
        }
        assertTrue(hasLaves, "LAVES_C15 must remain stable in the boundary result");
        assertTrue(hasBcc, "BCC_A2 (the prescribed-constitution phase) must appear in the boundary result");

        assertTrue(Double.isFinite(result.releasedComponentValue),
                "Released composition component must be finite");

        System.out.println("Boundary result: releasedX(Nb)=" + result.releasedComponentValue);
        for (EquilibriumResult.PhaseResult pr : stable) {
            System.out.println("  " + pr.phaseName + " (" + pr.modelType + "): amount="
                    + pr.amount + " x=" + Arrays.toString(pr.x));
        }
    }

    /**
     * Phase 4G.2: category-D end-to-end test -- identical fixture to
     * {@link #boundarySolveInitializesNewlyAppearingCvmBccPhaseAtPrescribedComposition}
     * except {@link EquilibriumSolverV2#setPrescribedBoundaryConstitution}
     * is never called, so {@code addNewStableSlot} must fall through to the
     * automatic {@code bestSeedConstitution} -&gt; {@code
     * bestCvmSeedConstitution} search ({@link GridMinimizer#sampleCompositions}
     * + {@code relaxCvmCandidateAtComposition} per trial composition) to
     * seed the newly-appearing BCC_A2 slot.
     *
     * <p>To confirm that search actually ran (not just that the boundary
     * solve converged), this test reflectively re-derives the same {@code
     * mu} {@code setUpBoundarySolve} installs (the converged LAVES_C15
     * seed's own chemical potentials -- see {@code seedFromEquilibriumResult},
     * which calls {@code calculateChemicalPotentials} on the seed's sole
     * stable slot) and independently invokes the private {@code
     * bestSeedConstitution} with that same {@code mu}/T/P. If {@code
     * addNewStableSlot}'s installed pre-Newton seed for BCC_A2 matches that
     * independently-recomputed value exactly, the automatic search (and no
     * other path) produced it.
     */
    @Test
    @Tag("slow")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void boundarySolveAutomaticallySeedsNewlyAppearingCvmBccPhaseWithoutPrescription() throws Exception {

        tdb database = new tdb(TDB_PATH);
        GibbsEnergyModel bcc = buildBcc(database);
        assertEquals("CVM", bcc.modelType(), "BCC_A2 must be a real CVM model, not CEF");
        assertEquals(3, bcc.numComponents());

        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);
        GibbsEnergyModel laves = buildLaves(parser);
        assertEquals(3, laves.numComponents());

        List<GibbsEnergyModel> candidates = List.of(bcc, laves);

        double[] y0Laves = laves.getInitialInternalVars(LAVES_SEED_X);
        assertTrue(laves.isValid(y0Laves), "LAVES_C15 initial constitution must be valid.");

        EquilibriumSolverV2 seedSolver = new EquilibriumSolverV2();
        seedSolver.setTolerance(1.0e-6);
        seedSolver.setInitialStateForTest(
                new int[]{0},
                new double[][]{y0Laves},
                new double[]{1.0});
        EquilibriumResult seed = seedSolver.solve(T, P, LAVES_SEED_X, List.of(laves));

        assertTrue(seed.isConverged(), "Single-phase LAVES_C15 seed must converge");
        assertEquals(1, seed.getStablePhases().size());
        assertEquals("LAVES_C15", seed.getStablePhases().get(0).phaseName);

        // NOTE: no setPrescribedBoundaryConstitution() call anywhere below
        // -- addNewStableSlot() must fall through to the automatic
        // bestSeedConstitution()/bestCvmSeedConstitution() search for
        // BCC_A2, exactly as it does when the caller supplies no seed.
        EquilibriumSolverV2 boundarySolver = new EquilibriumSolverV2();
        boundarySolver.setTolerance(1.0e-6);

        // Reflectively capture the PRE-NEWTON seed addNewStableSlot()
        // installs for BCC_A2, via the same private setUpBoundarySolve()
        // the public solveBoundary() itself calls first -- this isolates
        // the seed-selection step from the subsequent Newton loop so the
        // seed itself (not just eventual convergence) can be inspected.
        Method setUpBoundarySolve = EquilibriumSolverV2.class.getDeclaredMethod(
                "setUpBoundarySolve",
                double.class, double.class, double[].class,
                List.class, EquilibriumResult.class, String.class, double.class);
        setUpBoundarySolve.setAccessible(true);
        setUpBoundarySolve.invoke(
                boundarySolver, T, P, LAVES_SEED_X, candidates, seed, "BCC_A2", 0.0);

        double[] installedSeedY = readStableSlotY(boundarySolver, "BCC_A2");
        assertNotNull(installedSeedY, "BCC_A2 must have been installed as a stable slot");
        assertTrue(bcc.isValid(installedSeedY),
                "Automatically-selected BCC_A2 seed must be a valid CVM state");

        double[] seedX = bcc.compositionFromInternal(installedSeedY);
        assertFalse(compositionsEqual(seedX, LAVES_SEED_X, 1.0e-6),
                "Automatic seed composition must not simply be forced to the "
                + "bulk/system targetComposition()");

        // Diagnostic only (Phase 4G.2 failure-mode reporting): the
        // installed seed's own G/moles, for reporting if the subsequent
        // boundary Newton loop fails to converge from it.
        System.out.println("Automatic BCC_A2 seed: x=" + Arrays.toString(seedX)
                + " G=" + bcc.G(T, P, installedSeedY)
                + " moles=" + Arrays.toString(bcc.moles(installedSeedY)));

        // Independently re-derive the same mu setUpBoundarySolve() installs
        // (the converged LAVES_C15 seed's own chemical potentials) and call
        // the private bestSeedConstitution() directly with it, exactly as
        // EquilibriumSolverV2BestSeedConstitutionCvmTest already does. If
        // this reproduces the installed seed exactly, bestSeedConstitution()
        // (and specifically its CVM branch, since bcc is a CvmGibbsModel)
        // is confirmed to be the method that produced it.
        double[] muFromSeed = seed.getMu();
        assertNotNull(muFromSeed, "Converged LAVES_C15 seed must expose mu");

        EquilibriumSolverV2 probe = new EquilibriumSolverV2();
        probe.setTolerance(1.0e-6);
        setField(probe, "T", T);
        setField(probe, "P", P);
        setField(probe, "mu", muFromSeed);

        Method bestSeedConstitution = EquilibriumSolverV2.class.getDeclaredMethod(
                "bestSeedConstitution", GibbsEnergyModel.class);
        bestSeedConstitution.setAccessible(true);
        double[] independentlyRecomputedSeed =
                (double[]) bestSeedConstitution.invoke(probe, bcc);

        assertNotNull(independentlyRecomputedSeed,
                "bestSeedConstitution() must produce a candidate for BCC_A2");
        assertEquals(installedSeedY.length, independentlyRecomputedSeed.length);
        for (int i = 0; i < installedSeedY.length; i++) {
            assertEquals(installedSeedY[i], independentlyRecomputedSeed[i], 1.0e-12,
                    "addNewStableSlot()'s installed seed must exactly match "
                    + "bestSeedConstitution()'s own output at index " + i
                    + " -- confirms the automatic search (not the prescribed "
                    + "path or any other fallback) produced the installed seed");
        }

        // Now run the actual public boundary API (fresh solver instance,
        // same as above but never inspected mid-flight) to confirm the
        // automatically-seeded boundary Newton solve itself converges.
        EquilibriumSolverV2 fullBoundarySolver = new EquilibriumSolverV2();
        fullBoundarySolver.setTolerance(1.0e-6);

        long startNanos = System.nanoTime();
        EquilibriumSolverV2.BoundarySolveResult result;
        try {
            result = fullBoundarySolver.solveBoundary(
                    T, P, LAVES_SEED_X, candidates, seed, "BCC_A2", 0.0, 0);
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "Boundary solve introducing BCC_A2 via the automatic "
                    + "CVM seed search should not throw: " + e.getMessage(), e);
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        assertNotNull(result);
        assertTrue(result.equilibrium.isConverged(),
                "Boundary solve (fixing BCC_A2 at zero, releasing x(Nb)) "
                + "must converge using the automatically-searched CVM "
                + "starting constitution");

        List<EquilibriumResult.PhaseResult> stable = result.equilibrium.getStablePhases();
        boolean hasLaves = false;
        boolean hasBcc = false;
        for (EquilibriumResult.PhaseResult pr : stable) {
            if (pr.phaseName.equals("LAVES_C15")) hasLaves = true;
            if (pr.phaseName.equals("BCC_A2")) {
                hasBcc = true;
                assertTrue(Double.isFinite(pr.G), "BCC_A2's G must be finite");
                assertTrue(bcc.isValid(pr.y), "BCC_A2's constitution must be valid");
                assertEquals("CVM", pr.modelType);
            }
        }
        assertTrue(hasLaves, "LAVES_C15 must remain stable in the boundary result");
        assertTrue(hasBcc, "BCC_A2 (the automatically-seeded phase) must appear in the boundary result");

        assertTrue(Double.isFinite(result.releasedComponentValue),
                "Released composition component must be finite");

        System.out.println("Automatic-seed boundary solve wall-clock: " + elapsedMillis + " ms");
        System.out.println("Boundary result: releasedX(Nb)=" + result.releasedComponentValue);
        for (EquilibriumResult.PhaseResult pr : stable) {
            System.out.println("  " + pr.phaseName + " (" + pr.modelType + "): amount="
                    + pr.amount + " x=" + Arrays.toString(pr.x));
        }
    }

    /**
     * Reflectively reads {@code phaseName}'s constitution {@code y} out of
     * {@code solver}'s current stable slots ({@code stableSlots}, a {@code
     * List} of the private nested {@code PhaseWork} type) -- used to
     * inspect the pre-Newton seed {@code addNewStableSlot} installs without
     * running the boundary Newton loop.
     */
    private static double[] readStableSlotY(EquilibriumSolverV2 solver, String phaseName) throws Exception {

        Field stableSlotsField = EquilibriumSolverV2.class.getDeclaredField("stableSlots");
        stableSlotsField.setAccessible(true);
        List<?> stableSlots = (List<?>) stableSlotsField.get(solver);

        Field stablePhasesField = EquilibriumSolverV2.class.getDeclaredField("stablePhases");
        stablePhasesField.setAccessible(true);
        int[] stablePhases = (int[]) stablePhasesField.get(solver);

        Field phaseModelsField = EquilibriumSolverV2.class.getDeclaredField("phaseModels");
        phaseModelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<GibbsEnergyModel> phaseModels = (List<GibbsEnergyModel>) phaseModelsField.get(solver);

        for (int k = 0; k < stableSlots.size(); k++) {
            if (phaseModels.get(stablePhases[k]).phaseName().equals(phaseName)) {
                Object phaseWork = stableSlots.get(k);
                Field yField = phaseWork.getClass().getDeclaredField("y");
                yField.setAccessible(true);
                return (double[]) yField.get(phaseWork);
            }
        }
        return null;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static boolean compositionsEqual(double[] a, double[] b, double tol) {
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > tol) return false;
        }
        return true;
    }
}
