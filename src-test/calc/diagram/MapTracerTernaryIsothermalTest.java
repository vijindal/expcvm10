package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 5c of {@code docs/roadmap_phase_diagrams.md}: the first ternary
 * isothermal-section crossing traced by {@link MapTracer#walkOneSegment},
 * confirming Step 5b's {@link ConditionSet} generalization already
 * handles a genuine 2-free-composition-axis case (T fixed, x(Mg) and
 * x(Zn) both free) with NO changes needed to the walk body itself --
 * {@link EquilibriumSolverV2#solveBoundary} already indexes {@code
 * targetAmounts[releasedComponentIndex]} generically (confirmed no n=2
 * assumption exists), so walking one composition axis while releasing a
 * DIFFERENT composition axis (rather than releasing the same axis that
 * is walked, or releasing T) is mechanically identical to today's
 * binary T-x case from the walk loop's point of view.
 *
 * <p><b>System and reference data.</b> Al-Mg-Zn on
 * {@code data/cost507R.TDB} -- OpenCalphad's OWN bundled COST 507
 * database ({@code examples/macros/cost507R.TDB}, copied byte-identical
 * into this repo; this project's OTHER cost507-named file, {@code
 * data/cost507.tdb}, was checked directly this session and found to be
 * a DIFFERENT, incompatible assessment for this system -- e.g. it gives
 * FCC_A1+LIQUID where {@code cost507R.TDB} gives FCC_A1+MGZN2 at the
 * same conditions -- so {@code cost507R.TDB} is used exclusively here to
 * keep this test and its OC reference on the identical database,
 * matching every prior OC-referenced test in this project).
 *
 * <p>OC reference (driven via the pty pattern from Step 1, {@code
 * docs/oc_reference_tests/run_pty.py}, against the real {@code oc7C}
 * binary):
 * <ul>
 *   <li>T=700K, x(Mg)=0.05, x(Zn)=0.05: single-phase FCC_A1,
 *       G/N=-26965.8 J/mol -- our solver with FCC_A1 as sole candidate
 *       matches to 6 significant figures (G=-26965.8217 J/mol.f.u.,
 *       confirmed directly this session).</li>
 *   <li>T=700K, x(Mg)=0.05, x(Zn)=0.10: two-phase FCC_A1 (0.9386 f.u.,
 *       x(Zn)=0.0629 in FCC_A1) + MGZN2 (0.06141 mol, i.e. 0.02047 f.u.
 *       given MGZN2's 3 atoms/formula-unit) -- our solver with
 *       {FCC_A1, MGZN2} candidates matches these amounts and
 *       compositions closely (confirmed directly this session).</li>
 * </ul>
 * Direct {@code EquilibriumSolverV2} scanning (no tracer) at x(Mg)=0.05
 * narrowed the actual FCC_A1 -> FCC_A1+MGZN2 crossing to x(Zn) in
 * [0.05, 0.055], consistent with both OC-confirmed bracketing points
 * above.
 *
 * <p><b>Scope note.</b> This test calls {@code walkOneSegment} directly
 * with an EXPLICIT choice of which composition axis is walked (x(Zn))
 * and which is released (x(Mg)) -- the dynamic "pick the fastest-
 * varying axis" reselection a full drain loop needs (Sundman's own
 * per-step axis reselection) is Step 5d's concern, not this one. This
 * test only has to prove the walk MECHANISM generalizes correctly to 2
 * composition axes, which it does with zero changes beyond Step 5b's
 * ConditionSet plumbing.
 */
public class MapTracerTernaryIsothermalTest {

    private static final String TDB = "data/cost507R.TDB";
    private static final List<String> ELEMENTS = List.of("AL", "MG", "ZN");
    private static final List<String> PHASES = List.of("FCC_A1", "MGZN2");
    private static final double FIXED_T = 700.0;
    private static final double FIXED_P = 101325.0;

    private static List<GibbsEnergyModel> candidates() throws IOException {
        return ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();
    }

    private static Set<String> stableNames(EquilibriumResult r) {
        Set<String> names = new LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : r.getStablePhases()) names.add(pr.phaseName);
        return names;
    }

    @Test
    void startPointIsSinglePhaseFccMatchingOc() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();
        double xMg = 0.05, xZn = 0.04;
        double[] comp = { 1.0 - xMg - xZn, xMg, xZn };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(FIXED_T, FIXED_P, comp, candidates);

        assertTrue(result.isConverged());
        assertEquals(1, result.getStablePhases().size());
        assertEquals("FCC_A1", result.getStablePhases().get(0).phaseName);
    }

    @Test
    void ocConfirmedSinglePhasePointMatchesOurSolverClosely() throws IOException {
        // T=700K, x(Mg)=0.05, x(Zn)=0.05 -- OC: FCC_A1 alone, G/N=-26965.8 J/mol.
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                TDB, ELEMENTS, List.of("FCC_A1")).phaseModels();
        double[] comp = { 0.90, 0.05, 0.05 };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(FIXED_T, FIXED_P, comp, candidates);

        assertTrue(result.isConverged());
        assertEquals(1, result.getStablePhases().size());
        double gPerMol = result.getStablePhases().get(0).G;
        assertEquals(-26965.8, gPerMol, 1.0,
                "should match OC's G/N=-26965.8 J/mol to within 1 J/mol");
    }

    @Test
    void walkingXZnWhileReleasingXMgFindsTheGenuineTernaryCrossing() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();

        double xMgStart = 0.05, xZnStart = 0.04;
        double[] startComp = { 1.0 - xMgStart - xZnStart, xMgStart, xZnStart };
        EquilibriumResult startResult = new calc.equil.EquilibriumSolverV2().solve(
                FIXED_T, FIXED_P, startComp, candidates);
        Set<String> startNames = stableNames(startResult);
        assertEquals(Set.of("FCC_A1"), startNames);

        // Walk x(Zn) (component index 2), release x(Mg) (component index 1)
        // -- two DIFFERENT composition axes, neither one T. This is the
        // case that does not exist in the binary map (which always
        // releases the ONLY other composition while walking T or P).
        AxisConfig walkAxis = new AxisConfig("x(Zn)", 2, 0.04, 0.08, 0.005);
        AxisConfig releaseAxis = new AxisConfig("x(Mg)", 1, 0.0, 0.5, 0.01);

        MapTracer tracer = new MapTracer();
        MapTracer.SegmentResult seg = tracer.walkOneSegment(
                xZnStart, startNames, walkAxis, releaseAxis, FIXED_T, FIXED_P,
                startComp, candidates, startResult);

        assertEquals(MapTracer.SegmentEnd.CROSSING, seg.end);
        assertEquals(Set.of("FCC_A1", "MGZN2"), seg.newStableNames);

        // Crossing should land in the confirmed [0.05, 0.055] bracket.
        assertTrue(seg.endWalkValue >= 0.05 && seg.endWalkValue <= 0.06,
                "crossing x(Zn) should be within the confirmed bracket, got " + seg.endWalkValue);
    }

    @Test
    void ocConfirmedTwoPhasePointMatchesOurSolverClosely() throws IOException {
        // T=700K, x(Mg)=0.05, x(Zn)=0.10 -- OC: FCC_A1 (0.9386 f.u.,
        // x(Zn)=0.0629) + MGZN2 (0.02047 f.u.).
        List<GibbsEnergyModel> candidates = candidates();
        double[] comp = { 0.85, 0.05, 0.10 };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(FIXED_T, FIXED_P, comp, candidates);

        assertTrue(result.isConverged());
        assertEquals(2, result.getStablePhases().size());

        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            if (pr.phaseName.equals("FCC_A1")) {
                assertEquals(0.9386, pr.amount, 0.005, "FCC_A1 amount should match OC's 0.9386 f.u.");
                assertEquals(0.0629, pr.x[2], 0.005, "x(Zn) in FCC_A1 should match OC's 0.0629");
            } else if (pr.phaseName.equals("MGZN2")) {
                assertEquals(0.0205, pr.amount, 0.005, "MGZN2 amount should match OC's ~0.0205 f.u.");
            } else {
                throw new AssertionError("Unexpected stable phase: " + pr.phaseName);
            }
        }
    }
}
