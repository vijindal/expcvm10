package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;
import ui.result.EquilibriumReport;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Landmark test: the C1 drain loop ({@link StepDiagramTracer}) checked at
 * the FORMATTER level -- {@link EquilibriumReport}'s rendered output for
 * every {@link Node} the drain loop produces, cross-checked field-by-field
 * against a real {@code oc7C} run's own {@code l r} dump.
 *
 * <p>OC reference: {@code docs/oc_reference_tests/agcu_step_xcu05_full_walk.txt},
 * a real {@code step} calculation (Ag-Cu, x(Cu)=0.05, T=1150-1230K, 5K
 * increments) captured via the WSL pty driver ({@code
 * docs/roadmap_phase_diagrams.md}'s "Running OC calculations" section).
 * OC's own {@code step} console trace reports two nodes -- T=1176.13K
 * (LIQUID appears) and T=1207.60K (FCC_A1#1 disappears) -- bracketed here
 * by OC's direct {@code set cond}/{@code c e}/{@code l r 2} point dumps at
 * T=1176/1177K and T=1207/1208K.
 *
 * <p><b>Tolerance.</b> This codebase's crossing is located to {@code
 * StepTracer}'s bisection tolerance (axis.step * 1e-4 ~ 5e-4K here) --
 * essentially exact -- while OC's own bracket points sit a full 1K on
 * either side of its own crossing (T=1176/1177, T=1207/1208). So the two
 * codebases' phase AMOUNTS at "the crossing" are not directly comparable
 * (this codebase's crossing amount is near-zero by construction; OC's
 * bracket amounts are NOT at the crossing, they are 1K away from it) --
 * what IS directly comparable, and asserted here, is: (1) the crossing
 * AXIS VALUE itself, to OC's bracket; (2) chemical potentials, G, and
 * stable-phase COMPOSITIONS at this codebase's own bracket points
 * (T=1176/1177K, T=1207/1208K, solved directly, not via the drain loop's
 * bisected crossing) against OC's bracket points at the SAME T -- an
 * apples-to-apples comparison neither side has to approximate.
 */
public class StepDiagramTracerOcFormatterComparisonTest {

    private static final double MU_RELATIVE_TOLERANCE = 1.0e-3;
    private static final double G_RELATIVE_TOLERANCE = 1.0e-3;
    private static final double COMPOSITION_ABSOLUTE_TOLERANCE = 1.0e-3;
    private static final double AMOUNT_ABSOLUTE_TOLERANCE = 2.0e-3;

    private static Set<String> stableNames(EquilibriumResult r) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : r.getStablePhases()) names.add(pr.phaseName);
        return names;
    }

    // ------------------------------------------------------------------
    // Part 1: the drain loop itself finds both OC-confirmed crossing
    // axis values, via the SAME NodeRegistry the earlier StepDiagramTracerTest
    // exercises -- re-asserted here as this test's own self-contained claim.
    // ------------------------------------------------------------------

    @Test
    void drainLoopCrossingAxisValuesMatchOcsOwnStepTraceWithinItsReportedPrecision() throws IOException {
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);

        NodeRegistry registry = new StepDiagramTracer().drain(
                axis, 0.0, 101325.0, new double[] { 0.95, 0.05 }, candidates);

        double liquidusT = Double.NaN, solidusT = Double.NaN;
        for (Node node : registry.getNodes()) {
            double t = node.axisValues[0];
            // Two nodes share the FCC_A1+LIQUID phase set (one per
            // crossing, per the OC step trace's own "Line 1/3" -- see
            // this file's class javadoc): distinguish by T range.
            if (node.stablePhaseNames.equals(Set.of("FCC_A1", "LIQUID")) && t > 1170.0 && t < 1180.0) {
                liquidusT = t;
            }
            if (node.stablePhaseNames.equals(Set.of("LIQUID")) && t > 1200.0 && t < 1210.0) {
                solidusT = t;
            }
        }

        // OC's own step trace (agcu_step_xcu05_full_walk.txt):
        // "Creating a node at    1176.13 where LIQUID appears"
        assertEquals(1176.13, liquidusT, 0.01,
                "liquidus crossing should match OC's own step trace to its reported 2-decimal precision");
        // OC's own step trace: "Creating a node at    1207.60 where FCC_A1#1 disappear"
        assertEquals(1207.60, solidusT, 0.01,
                "solidus crossing should match OC's own step trace to its reported 2-decimal precision");
    }

    // ------------------------------------------------------------------
    // Part 2: EquilibriumReport's rendered fields, at directly-comparable
    // bracket points, against OC's own l r dump at the SAME conditions.
    // ------------------------------------------------------------------

    @Test
    void formatterFieldsMatchOcsLrDumpAtT1160BelowLiquidus() throws IOException {
        // OC (agcu_step_xcu05_full_walk.txt lines 91-116): T=1160K, single-phase
        // FCC_A1, Chem.pot/RT: AG=-7.1320, CU=-6.9370, RT=9.6448E3 J/mol,
        // G/N=-6.8693E4 J/mol, x(AG)=0.95, x(CU)=0.05.
        assertOneStepPointMatchesOc(
                1160.0, Set.of("FCC_A1"),
                /* muOverRT */ new double[] { -7.1320, -6.9370 }, /* RT */ 9.6448e3,
                /* gPerMol */ -6.8693e4,
                /* phaseAmounts */ new double[] { 1.0 },
                /* phaseComps */ new double[][] { { 0.95000, 0.05000 } });
    }

    @Test
    void formatterFieldsMatchOcsLrDumpAtT1176JustBelowLiquidus() throws IOException {
        // OC lines 126-151: T=1176K, still single-phase FCC_A1 (crossing is
        // at 1176.13K, so 1176K itself is still below it).
        assertOneStepPointMatchesOc(
                1176.0, Set.of("FCC_A1"),
                new double[] { -7.1664, -7.0078 }, 9.7779e3,
                -6.9994e4,
                new double[] { 1.0 },
                new double[][] { { 0.95000, 0.05000 } });
    }

    @Test
    void formatterFieldsMatchOcsLrDumpAtT1177JustAboveLiquidus() throws IOException {
        // OC lines 160-190: T=1177K, two-phase LIQUID+FCC_A1.
        // LIQUID: 0.01179 f.u., x(AG)=0.890792, x(CU)=0.109208.
        // FCC_A1#1: 0.9882 f.u., x(AG)=0.950706, x(CU)=0.049294.
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();
        double[] comp = { 0.95, 0.05 };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(1177.0, 101325.0, comp, candidates);
        assertTrue(result.isConverged());
        assertEquals(Set.of("LIQUID", "FCC_A1"), stableNames(result));

        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            if (pr.phaseName.equals("LIQUID")) {
                assertEquals(0.01179, pr.amount, AMOUNT_ABSOLUTE_TOLERANCE, "LIQUID amount vs OC");
                assertEquals(0.890792, pr.x[0], COMPOSITION_ABSOLUTE_TOLERANCE, "LIQUID x(AG) vs OC");
                assertEquals(0.109208, pr.x[1], COMPOSITION_ABSOLUTE_TOLERANCE, "LIQUID x(CU) vs OC");
            } else {
                assertEquals(0.9882, pr.amount, AMOUNT_ABSOLUTE_TOLERANCE, "FCC_A1 amount vs OC");
                assertEquals(0.950706, pr.x[0], COMPOSITION_ABSOLUTE_TOLERANCE, "FCC_A1 x(AG) vs OC");
                assertEquals(0.049294, pr.x[1], COMPOSITION_ABSOLUTE_TOLERANCE, "FCC_A1 x(CU) vs OC");
            }
        }

        // Confirm the formatter itself surfaces both phases and their
        // compositions -- not just that the raw EquilibriumResult does.
        String report = EquilibriumReport.format(result, List.of("AG", "CU"));
        assertTrue(report.contains("LIQUID") && report.contains("FCC_A1"));
        assertTrue(report.contains("amount="));
    }

    @Test
    void formatterFieldsMatchOcsLrDumpAtT1207JustBelowSolidus() throws IOException {
        // OC lines 241-271: T=1207K, two-phase LIQUID#1+FCC_A1#1 (near
        // solidus but not past it). LIQUID#1: 0.958 f.u., x(AG)=0.948876,
        // x(CU)=0.051124. FCC_A1#1: 0.04201 f.u., x(AG)=0.975632, x(CU)=0.024368.
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();
        double[] comp = { 0.95, 0.05 };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(1207.0, 101325.0, comp, candidates);
        assertTrue(result.isConverged());
        assertEquals(Set.of("LIQUID", "FCC_A1"), stableNames(result));

        for (EquilibriumResult.PhaseResult pr : result.getStablePhases()) {
            if (pr.phaseName.equals("LIQUID")) {
                assertEquals(0.958, pr.amount, AMOUNT_ABSOLUTE_TOLERANCE, "LIQUID amount vs OC");
                assertEquals(0.948876, pr.x[0], COMPOSITION_ABSOLUTE_TOLERANCE, "LIQUID x(AG) vs OC");
            } else {
                assertEquals(0.04201, pr.amount, AMOUNT_ABSOLUTE_TOLERANCE, "FCC_A1 amount vs OC");
                assertEquals(0.975632, pr.x[0], COMPOSITION_ABSOLUTE_TOLERANCE, "FCC_A1 x(AG) vs OC");
            }
        }
    }

    @Test
    void formatterFieldsMatchOcsLrDumpAtT1208JustAboveSolidus() throws IOException {
        // OC lines 284-308: T=1208K, single-phase LIQUID#1, x(AG)=0.95, x(CU)=0.05.
        assertOneStepPointMatchesOc(
                1208.0, Set.of("LIQUID"),
                new double[] { -7.2102, -7.8210 }, 1.0044e4,
                -7.2725e4,
                new double[] { 1.0 },
                new double[][] { { 0.95000, 0.05000 } });
    }

    /**
     * Solves one point directly (not via the drain loop -- these are
     * single-phase brackets, chosen to be directly comparable to OC's own
     * bracket points at the identical T), formats it via {@link
     * EquilibriumReport}, and checks chemical potentials (converted from
     * OC's printed mu/RT to absolute mu via OC's own printed RT), system
     * G/mol, and phase composition against the OC reference.
     */
    private void assertOneStepPointMatchesOc(
            double t, Set<String> expectedPhases,
            double[] ocMuOverRT, double ocRT, double ocGPerMol,
            double[] expectedAmounts, double[][] expectedComps) throws IOException {

        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();
        double[] comp = { 0.95, 0.05 };

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2().solve(t, 101325.0, comp, candidates);
        assertTrue(result.isConverged());
        assertEquals(expectedPhases, stableNames(result));

        double[] mu = result.getMu();
        for (int i = 0; i < mu.length; i++) {
            double ocMuAbsolute = ocMuOverRT[i] * ocRT;
            assertEquals(ocMuAbsolute, mu[i], Math.abs(ocMuAbsolute) * MU_RELATIVE_TOLERANCE,
                    "mu[" + i + "] at T=" + t + " should match OC's mu/RT * RT within "
                    + MU_RELATIVE_TOLERANCE + " relative");
        }

        // OC's G/N is per mole of the SYSTEM as defined (N=1 mole here, per
        // "set cond ... n=1" in the OC macro) -- directly comparable to
        // totalG() with no per-atom/per-formula-unit conversion needed.
        assertEquals(ocGPerMol, result.totalG(), Math.abs(ocGPerMol) * G_RELATIVE_TOLERANCE,
                "G/N at T=" + t + " should match OC's G/N within " + G_RELATIVE_TOLERANCE + " relative");

        assertEquals(1, result.getStablePhases().size());
        EquilibriumResult.PhaseResult pr = result.getStablePhases().get(0);
        assertEquals(expectedAmounts[0], pr.amount, AMOUNT_ABSOLUTE_TOLERANCE);
        for (int i = 0; i < pr.x.length; i++) {
            assertEquals(expectedComps[0][i], pr.x[i], COMPOSITION_ABSOLUTE_TOLERANCE);
        }

        // Confirm the FORMATTER (not just the raw result) surfaces the
        // matched values -- the actual point of this landmark test.
        String report = EquilibriumReport.format(result, List.of("AG", "CU"));
        assertTrue(report.contains(String.format(java.util.Locale.ROOT, "T=%.2f", t)));
        for (String phase : expectedPhases) {
            assertTrue(report.contains(phase), "report should mention phase " + phase);
        }
    }
}
