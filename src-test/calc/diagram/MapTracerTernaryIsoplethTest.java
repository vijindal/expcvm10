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
 * Step 5e of {@code docs/roadmap_phase_diagrams.md}: ternary isopleth
 * tracing on Al-Mg-Zn/{@code data/cost507R.TDB}, the same system Step 5c
 * already validated for ternary isothermal sections.
 *
 * <p><b>Design, corrected against a full re-read of Sundman 2021 (per
 * explicit direction this session).</b> The original plan (recorded in
 * the approved plan doc) assumed an isopleth needs a NEW {@code
 * COMPOSITION_RATIO} condition type and two simultaneously-free
 * composition axes (T and x(Cr) with x(Mo)/x(Cr) held at a fixed
 * ratio). Re-reading the paper's own worked isopleth figure (Fig. 3(c):
 * "Iso-pleth in the Al-Mg-Zn system at x_Zn = 0.05 calculated using the
 * COST 507 database" -- the EXACT system and database this project
 * already vendored for Step 5c) shows this was wrong: an isopleth's two
 * axes are T and ONE composition, with every OTHER composition held
 * FIXED (a constant value, as in the paper's own example, or a fixed
 * ratio -- the paper states both are valid, "a constant ratio between
 * two elements or more generally a linear equation between several
 * compositions", but its own figure uses the simpler fixed-value case).
 * This makes a fixed-composition isopleth structurally IDENTICAL to
 * today's binary map (T walked, one composition released) applied to a
 * ternary+ system with the extra composition(s) pinned -- confirmed
 * directly this session to need ZERO new production code:
 * <ul>
 *   <li>{@link ConditionSet#initialComposition()} already fills a FIXED
 *       composition condition's exact value and leaves it alone (only
 *       AXIS/unspecified components get computed) -- no new solving
 *       logic needed.</li>
 *   <li>{@link StepTracer#applyCompositionAxis} only ever writes the
 *       WALKED composition's own index (never applicable here since T,
 *       not a composition, is walked).</li>
 *   <li>{@code EquilibriumSolverV2#solveBoundaryInternal} only ever
 *       writes {@code targetAmounts[releasedComponentIndex]} each
 *       Newton iteration (confirmed by direct code reading) -- every
 *       other component, including a genuinely FIXED third one, is left
 *       exactly as passed in, with no implicit sum-to-one
 *       renormalization inside that method.</li>
 * </ul>
 * So this test calls the EXISTING {@code AxisConfig}-based {@link
 * MapTracer#walkOneSegment} directly (matching Step 5c's own scope
 * note: the dynamic "pick which axis to walk" reselection is a drain-
 * loop concern, not this primitive's), with T as {@code walkAxis} and
 * x(Zn) as {@code releaseAxis}, and x(Mg)=0.05 baked into the constant
 * (never-written) component of {@code compAtStart} -- exactly how a
 * FIXED {@link Condition} would flow through {@link
 * ConditionSet#fromBinaryAxes} if extended to carry one, but proven
 * here directly against the walk mechanism without needing that
 * plumbing yet (no caller constructs a 3rd-FIXED-composition {@code
 * ConditionSet} today; see the roadmap's Step 5e note for why this is
 * deliberately not built until a real caller needs it).
 *
 * <p><b>OC reference (driven via the pty pattern, {@code
 * docs/oc_reference_tests/run_pty.py}, against the real {@code oc7C}
 * binary; captures in {@code
 * docs/oc_reference_tests/almgzn_isopleth_xmg05.txt}).</b> At x(Mg)=0.05
 * fixed:
 * <ul>
 *   <li>T=650K, x(Zn)=0.050: FCC_A1 (0.969 f.u., x(Zn)=0.0306,
 *       x(Mg)=0.0411) + MGZN2 (0.0102 f.u.) -- both phases present, i.e.
 *       this point is ALREADY inside the two-phase field.</li>
 *   <li>T=700K, x(Zn)=0.052: FCC_A1 (0.9976 f.u.) + MGZN2 (0.00080
 *       f.u., barely two-phase) -- essentially AT the boundary.</li>
 *   <li>T=700K, x(Zn)=0.058: FCC_A1 (0.9895 f.u.) + MGZN2 (0.00349
 *       f.u.) -- clearly two-phase.</li>
 * </ul>
 * Direct scanning (no tracer, this session) at x(Mg)=0.05 fixed across a
 * T/x(Zn) grid confirms the FCC_A1-only / FCC_A1+MGZN2 boundary curves
 * from x(Zn)~0.02 at T=630K up to x(Zn)~0.052 at T=700K, and that the
 * boundary at x(Zn)=0.05 exactly sits between T=698K (two-phase) and
 * T=700K (single-phase) -- consistent with the OC point above (x(Zn)
 * =0.052 already two-phase at T=700K, so the true boundary is a shade
 * below 0.052 there).
 */
public class MapTracerTernaryIsoplethTest {

    private static final String TDB = "data/cost507R.TDB";
    private static final List<String> ELEMENTS = List.of("AL", "MG", "ZN");
    private static final List<String> PHASES = List.of("FCC_A1", "MGZN2");
    private static final double FIXED_XMG = 0.05;
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
    void ocConfirmedTwoPhasePointNearTheBoundaryMatchesOurSolverClosely() throws IOException {
        // T=700K, x(Mg)=0.05, x(Zn)=0.052 -- OC: FCC_A1 (0.9976 f.u.) +
        // MGZN2 (0.00080 f.u.), barely two-phase (just past the boundary).
        List<GibbsEnergyModel> candidates = candidates();
        double[] comp = { 1.0 - FIXED_XMG - 0.052, FIXED_XMG, 0.052 };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(700.0, FIXED_P, comp, candidates);

        assertTrue(result.isConverged());
        assertEquals(Set.of("FCC_A1", "MGZN2"), stableNames(result));
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            if (pr.phaseName.equals("FCC_A1")) {
                assertEquals(0.9976, pr.amount, 0.005, "FCC_A1 amount should match OC's 0.9976 f.u.");
            } else {
                assertEquals(0.00080, pr.amount, 0.002, "MGZN2 amount should match OC's 0.00080 f.u.");
            }
        }
    }

    @Test
    void ocConfirmedTwoPhasePointFurtherFromTheBoundaryMatchesOurSolverClosely() throws IOException {
        // T=700K, x(Mg)=0.05, x(Zn)=0.058 -- OC: FCC_A1 (0.9895 f.u.) +
        // MGZN2 (0.00349 f.u.).
        List<GibbsEnergyModel> candidates = candidates();
        double[] comp = { 1.0 - FIXED_XMG - 0.058, FIXED_XMG, 0.058 };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(700.0, FIXED_P, comp, candidates);

        assertTrue(result.isConverged());
        assertEquals(Set.of("FCC_A1", "MGZN2"), stableNames(result));
        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            if (pr.phaseName.equals("FCC_A1")) {
                assertEquals(0.9895, pr.amount, 0.005, "FCC_A1 amount should match OC's 0.9895 f.u.");
            } else {
                assertEquals(0.00349, pr.amount, 0.002, "MGZN2 amount should match OC's 0.00349 f.u.");
            }
        }
    }

    @Test
    void walkingTemperatureWithXMgFixedFindsTheGenuineIsoplethCrossing() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();

        // Start comfortably inside the two-phase field (T=630K, x(Zn)=0.05
        // is already FCC_A1+MGZN2 per the direct scan above) and walk T
        // upward -- x(Mg)=0.05 is baked into compAtStart's constant entry
        // and never written by either applyCompositionAxis (walkAxis is
        // TEMPERATURE, not a composition) or solveBoundary (which only
        // ever writes releaseAxis's own component index), so it stays
        // fixed at 0.05 for the whole walk, exactly the isopleth's
        // defining condition.
        double tStart = 630.0;
        double xZnStart = 0.05;
        double[] startComp = { 1.0 - FIXED_XMG - xZnStart, FIXED_XMG, xZnStart };
        EquilibriumResult startResult = new calc.equil.EquilibriumSolverV2().solve(
                tStart, FIXED_P, startComp, candidates);
        Set<String> startNames = stableNames(startResult);
        assertEquals(Set.of("FCC_A1", "MGZN2"), startNames);

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 630.0, 760.0, 2.0);
        AxisConfig releaseAxis = new AxisConfig("x(Zn)", 2, 0.001, 0.5, 0.001);

        MapTracer tracer = new MapTracer();
        MapTracer.SegmentResult seg = tracer.walkOneSegment(
                tStart, startNames, walkAxis, releaseAxis, tStart, FIXED_P,
                startComp, candidates, startResult);

        assertEquals(MapTracer.SegmentEnd.CROSSING, seg.end);
        assertEquals(Set.of("FCC_A1"), seg.newStableNames);

        // Direct scanning found this boundary sits between T=698K
        // (two-phase) and T=700K (single-phase) at x(Zn)=0.05 fixed --
        // consistent with OC's own T=700K, x(Zn)=0.052 point being
        // already (barely) two-phase.
        assertTrue(seg.endWalkValue >= 698.0 && seg.endWalkValue <= 702.0,
                "isopleth crossing temperature should be within the confirmed bracket, got "
                + seg.endWalkValue);

        // x(Mg) must have stayed exactly fixed at 0.05 throughout --
        // the defining isopleth condition, never written by this walk.
        assertEquals(FIXED_XMG, seg.endComposition[1], 1.0e-12,
                "x(Mg) is the isopleth's FIXED composition and must not move during the walk");
    }
}
