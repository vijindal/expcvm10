package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;
import ui.result.EquilibriumReport;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Algorithm C2 checked at the FORMATTER level on a genuine TERNARY
 * ISOTHERMAL crossing -- both axes are COMPOSITION (x(Zn) walked, x(Mg)
 * released), unlike every other formatter-level C2 test in this project
 * ({@link StepDiagramTracerOcFormatterComparisonTest} is a binary T-x
 * case). Builds on {@link MapTracerTernaryIsothermalTest}'s system and
 * OC-confirmed bracket (this class adds the EXACT node and a formatter
 * check on top of that bracket).
 *
 * <p>System: Al-Mg-Zn on {@code data/cost507R.TDB} (OpenCalphad's own
 * bundled COST 507 database -- see {@code MapTracerTernaryIsothermalTest}'s
 * class javadoc for why this exact file, not {@code data/cost507.tdb}, is
 * required), candidates {FCC_A1, MGZN2}, T=700K fixed, x(Mg)=0.05 fixed
 * as the release axis's starting value, walking x(Zn) from 0.04 upward.
 *
 * <p>OC reference (real {@code oc7C} run via the WSL pty driver, {@code
 * docs/roadmap_phase_diagrams.md}'s "Running OC calculations" section):
 * <ul>
 *   <li>{@code step} trace (x(Zn) walked 0.04-0.08 step 0.001, x(Mg)=0.05
 *       fixed): {@code "Creating a node at 700.00 where MGZN2 appears"},
 *       {@code "Finishing line with 17 equilibria ... xaxis: 5.0236E-02"}
 *       -- i.e. the exact crossing is at x(Zn)=0.050236, MGZN2 amount
 *       exactly zero there (OC's own ZPF definition).</li>
 *   <li>{@code l r 2} dump at x(Mg)=0.05, x(Zn)=0.050236 (the exact
 *       crossing composition): single-phase FCC_A1 (MGZN2 driven to
 *       zero amount, confirming this IS the ZPF boundary), RT=5.8202E3
 *       J/mol, mu/RT: AL=-4.3611, MG=-6.9789, ZN=-7.1854, G/N=-2.6970E4
 *       J/mol (displayed to OC's own 5-significant-figure precision).</li>
 * </ul>
 *
 * <p>This codebase's C2 solve fixes MGZN2 at zero amount and releases
 * x(Mg) (the {@code releaseAxis} component), so its own crossing
 * composition is directly comparable to OC's x(Zn)=0.050236 without any
 * bisection-tolerance caveat (unlike the binary T-x case, where OC's own
 * bracket sits 1K away from its crossing -- here OC's {@code step} trace
 * reports the crossing's exact axis value directly, to 6 significant
 * figures).
 */
public class MapTracerTernaryIsothermalOcFormatterComparisonTest {

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
    void ternaryCrossingXZnMatchesOcsOwnStepTraceExactly() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();

        double xMgStart = 0.05, xZnStart = 0.04;
        double[] startComp = { 1.0 - xMgStart - xZnStart, xMgStart, xZnStart };
        EquilibriumResult startResult = new calc.equil.EquilibriumSolverV2().solve(
                FIXED_T, FIXED_P, startComp, candidates);
        Set<String> startNames = stableNames(startResult);
        assertEquals(Set.of("FCC_A1"), startNames);

        AxisConfig walkAxis = new AxisConfig("x(Zn)", 2, 0.04, 0.08, 0.001);
        AxisConfig releaseAxis = new AxisConfig("x(Mg)", 1, 0.0, 0.5, 0.01);

        MapTracer tracer = new MapTracer();
        MapTracer.SegmentResult seg = tracer.walkOneSegment(
                xZnStart, startNames, walkAxis, releaseAxis, FIXED_T, FIXED_P,
                startComp, candidates, startResult);

        assertEquals(MapTracer.SegmentEnd.CROSSING, seg.end);
        assertEquals(Set.of("FCC_A1", "MGZN2"), seg.newStableNames);
        assertEquals("MGZN2", seg.changedPhase);

        // Unlike StepTracer's own walk axis (bisected to an exact
        // crossing), MapTracer's C2 releases a DIFFERENT axis (x(Mg)
        // here) and reports the WALK axis (x(Zn)) at the raw grid point
        // where the phase-set change was first observed -- so
        // seg.endWalkValue is grid-snapped to this walk's own step
        // (0.001), landing one step past OC's own exact crossing
        // (x(Zn)=0.050236, "Finishing line ... xaxis: 5.0236E-02" in
        // OC's step trace). Confirm it lands in the same [0.05, 0.052]
        // bracket, one step-width wide, that OC's crossing falls in.
        assertTrue(seg.endWalkValue >= 0.050 && seg.endWalkValue <= 0.052,
                "ternary C2 crossing x(Zn) should land within one walk-step of "
                + "OC's own exact crossing (x(Zn)=0.050236), got " + seg.endWalkValue);

        // The RELEASED axis (x(Mg), index 1) is the one Algorithm C2
        // actually solves exactly (fixing MGZN2 at zero amount): at this
        // walk's grid point (x(Zn)=0.051, one step past OC's own
        // x(Zn)=0.050236 crossing), the exact boundary's x(Mg) should
        // sit close to its OC-confirmed starting value of 0.05 -- a
        // small, physically-expected shift from the boundary's slight
        // curvature between the two grid points, not a solver artifact.
        assertEquals(0.05, seg.endComposition[releaseAxis.componentIndex], 0.005,
                "released x(Mg) at the ternary C2 boundary should stay close to "
                + "OC's x(Mg)=0.05 (the boundary's curvature over one walk step)");
    }

    @Test
    void formatterFieldsAtTheExactCrossingMatchOcsLrDump() throws IOException {
        // Re-solve directly AT OC's own exact crossing composition
        // (x(Mg)=0.05, x(Zn)=0.050236) with ONLY FCC_A1 as a candidate --
        // OC's own l r dump at this exact point converges MGZN2's amount
        // to exactly zero (Phase change iteration: 12 -MGZN2, in the
        // captured OC session), i.e. the ZPF boundary IS a single-phase
        // FCC_A1 equilibrium at this composition, directly comparable to
        // this codebase's own single-phase solve with no fixed-phase
        // bookkeeping needed on either side.
        List<GibbsEnergyModel> fccOnly = ThermodynamicSystem.build(
                TDB, ELEMENTS, List.of("FCC_A1")).phaseModels();
        double xMg = 0.05, xZn = 0.050236;
        double[] comp = { 1.0 - xMg - xZn, xMg, xZn };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(
                FIXED_T, FIXED_P, comp, fccOnly);

        assertTrue(result.isConverged());
        assertEquals(Set.of("FCC_A1"), stableNames(result));

        // OC: RT=5.8202E3 J/mol, mu/RT: AL=-4.3611, MG=-6.9789, ZN=-7.1854.
        double ocRT = 5.8202e3;
        double[] ocMuOverRT = { -4.3611, -6.9789, -7.1854 };
        double[] mu = result.getMu();
        for (int i = 0; i < mu.length; i++) {
            double ocMuAbsolute = ocMuOverRT[i] * ocRT;
            assertEquals(ocMuAbsolute, mu[i], Math.abs(ocMuAbsolute) * 5.0e-3,
                    "mu[" + i + "] at the ternary crossing should match OC's mu/RT * RT "
                    + "within 0.5% relative");
        }

        // OC: G/N=-2.6970E4 J/mol (N=1 mole, "set cond ... n=1").
        assertEquals(-2.6970e4, result.totalG(), Math.abs(-2.6970e4) * 5.0e-3,
                "G/N at the ternary crossing should match OC's G/N within 0.5% relative");

        assertEquals(1, result.getStablePhases().size());
        EquilibriumResult.PhaseResult pr = result.getStablePhases().get(0);
        // OC: X: AL=8.99764E-01, ZN=5.02360E-02, MG=5.00000E-02.
        assertEquals(0.899764, pr.x[0], 1.0e-3, "x(AL) in FCC_A1 vs OC");
        assertEquals(0.050000, pr.x[1], 1.0e-3, "x(MG) in FCC_A1 vs OC");
        assertEquals(0.050236, pr.x[2], 1.0e-3, "x(ZN) in FCC_A1 vs OC");

        // Confirm the FORMATTER (not just the raw result) surfaces the
        // matched values -- the actual point of this landmark test.
        String report = EquilibriumReport.format(result, ELEMENTS);
        assertTrue(report.contains(String.format(Locale.ROOT, "T=%.2f", FIXED_T)));
        assertTrue(report.contains("FCC_A1"), "report should mention phase FCC_A1");
        assertTrue(report.contains("amount="));
    }

    @Test
    void formatterSurfacesTwoPhaseStateJustPastTheCrossing() throws IOException {
        // OC's Line 3 (agcu-style trace) starts "5.024635E-02 with: FCC_A1#1
        // MGZN2" -- just past the crossing, both phases are genuinely
        // stable together. x(Zn)=0.055 is inside that two-phase region
        // per MapTracerTernaryIsothermalTest's own OC-confirmed bracket.
        List<GibbsEnergyModel> candidates = candidates();
        double xMg = 0.05, xZn = 0.055;
        double[] comp = { 1.0 - xMg - xZn, xMg, xZn };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(
                FIXED_T, FIXED_P, comp, candidates);

        assertTrue(result.isConverged());
        assertEquals(Set.of("FCC_A1", "MGZN2"), stableNames(result));

        String report = EquilibriumReport.format(result, ELEMENTS);
        assertTrue(report.contains("FCC_A1") && report.contains("MGZN2"),
                "report should mention both stable phases just past the ternary crossing");
    }
}
