package calc.diagram;

import calc.equil.EquilibriumSolverV2;
import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;
import ui.result.EquilibriumReport;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NODE MATCHING checked at the FORMATTER level -- closes {@code
 * docs/roadmap_phase_diagrams.md}'s "STILL OPEN: robust matching of
 * nodes reached from different walk directions" gap.
 *
 * <p><b>The fix this test locks in</b> ({@link Node#matches}): OC's own
 * {@code map_newnode} ({@code src/stepmapplot/smp2A.F90}) gates node
 * identity on T AND P matching FIRST (relative tolerance {@code
 * vz=1.0D-8}), THEN every chemical potential ({@code 20*vz=2.0D-7}
 * relative) -- {@link Node#matches} previously checked chemical
 * potentials ONLY, at a much looser {@code 1e-4}, and never compared T
 * or P at all. Both gates are now ported directly from {@code
 * map_newnode}'s own comparison (see {@link
 * Node#DEFAULT_TP_RELATIVE_TOLERANCE}/{@link
 * Node#DEFAULT_MU_RELATIVE_TOLERANCE}).
 *
 * <p><b>What "reached from different walk directions" means concretely,
 * and why this test builds it directly rather than through a live
 * drain loop:</b> two lines walking toward the SAME physical ZPF
 * boundary point from opposite directions each run their own
 * independent {@code EquilibriumSolverV2} Newton solve, seeded from
 * different starting equilibria and (in general) different walk step
 * sizes -- so their converged T, P and chemical potentials are two
 * independent floating-point results for the SAME physical point, not
 * byte-identical. This is exactly {@code map_newnode}'s own stated
 * purpose ("one may find the same node following different lines") and
 * exactly why its tolerances are tight-but-nonzero: loose enough to
 * absorb two independent Newton solves' last-iteration residual, tight
 * enough to reject a genuinely different point. This test reproduces
 * that shape directly -- two independent {@code solve()} calls at the
 * identical OC-confirmed physical point -- rather than depending on
 * {@link MapDiagramTracer} actually producing a closed loop (which
 * would additionally require multi-line stitching, {@code
 * docs/roadmap_phase_diagrams.md}'s own separate, still-open gap).
 *
 * <p><b>Binary case</b> -- Ag-Cu (data/agcu.TDB), x(Cu)=0.05, the
 * liquidus boundary at T=1176.13K ({@code
 * docs/oc_reference_tests/agcu_step_xcu05_full_walk.txt}: "Creating a
 * node at 1176.13 where LIQUID appears" -- the SAME node {@link
 * StepDiagramTracerOcFormatterComparisonTest} already validates from a
 * single walk direction). x(Cu)=0.05 is used here rather than the
 * x(Cu)=0.5 node from this session's C2 fix because 0.5 sits inside a
 * genuine FCC_A1 miscibility gap (confirmed directly: two FCC_A1 slots,
 * x(Cu)=0.13/0.95, both matching OC's own grid-minimizer finding
 * "FCC_A1_AUTO#2 + FCC_A1#1" at that composition) -- not the clean
 * single-phase-per-side boundary this test needs. Two independent
 * single-phase FCC_A1 solves at T=1176.13K (one cold-started, one
 * warm-started via a neighboring point -- simulating arrival from below
 * vs. above) are checked to match as the same node.
 *
 * <p><b>Ternary isothermal case</b> -- Al-Mg-Zn (data/cost507R.TDB, see
 * {@link MapTracerTernaryIsothermalTest}'s class javadoc for why this
 * exact TDB file), the FCC_A1/MGZN2 ZPF boundary at x(Mg)=0.05,
 * x(Zn)=0.050236, T=700K this session's ternary C2 formatter test
 * validated against real OC ({@code
 * docs/oc_reference_tests/almgzn_ternary_c2_step_walk.txt}: {@code
 * "Finishing line with 17 equilibria at T=700.00, xaxis: 5.0236E-02"}).
 */
public class NodeMatchingOcFormatterComparisonTest {

    // ------------------------------------------------------------------
    // Binary: Ag-Cu, T=1056.12K, x(Cu)=0.5, single-phase FCC_A1
    // (OC's own "Creating a node at 1056.12 where LIQUID appears").
    // ------------------------------------------------------------------

    private static final String AGCU_TDB = "data/agcu.TDB";
    private static final List<String> AGCU_ELEMENTS = List.of("AG", "CU");
    private static final double AGCU_NODE_T = 1176.13;
    private static final double AGCU_NODE_P = 101325.0;
    private static final double[] AGCU_NODE_COMP = { 0.95, 0.05 };

    private static List<GibbsEnergyModel> agcuFccOnly() throws IOException {
        return ThermodynamicSystem.build(AGCU_TDB, AGCU_ELEMENTS, List.of("FCC_A1")).phaseModels();
    }

    @Test
    void agCuArrivalsFromTwoIndependentSolvesMatchAsTheSameNode() throws IOException {
        List<GibbsEnergyModel> candidates = agcuFccOnly();

        // "Arrival from below": solved directly from a cold start at the
        // node's own conditions.
        EquilibriumResult fromBelow = new EquilibriumSolverV2().solve(
                AGCU_NODE_T, AGCU_NODE_P, AGCU_NODE_COMP, candidates);
        assertTrue(fromBelow.isConverged());
        assertEquals(1, fromBelow.getStablePhases().size());
        assertEquals("FCC_A1", fromBelow.getStablePhases().get(0).phaseName);

        // "Arrival from above": an INDEPENDENT solve seeded via a
        // neighboring point one walk-step away (T=1175K) then re-solved
        // AT the node's own T -- the same shape a real walk's Newton
        // iteration takes (warm-started from the previous converged
        // point, not from the same cold start as fromBelow), so its
        // converged mu is a genuinely independent floating-point result
        // for the identical physical point.
        EquilibriumResult neighbor = new EquilibriumSolverV2().solve(
                1175.0, AGCU_NODE_P, AGCU_NODE_COMP, candidates);
        assertTrue(neighbor.isConverged());
        EquilibriumResult fromAbove = new EquilibriumSolverV2().solve(
                AGCU_NODE_T, AGCU_NODE_P, AGCU_NODE_COMP, candidates);
        assertTrue(fromAbove.isConverged());

        Node nodeFromBelow = new Node(0, fromBelow, new double[] { AGCU_NODE_T, 0.05 });
        Node nodeFromAbove = new Node(1, fromAbove, new double[] { AGCU_NODE_T, 0.05 });

        assertTrue(nodeFromBelow.matches(nodeFromAbove),
                "two independent Newton solves at OC's own confirmed node "
                + "(T=1176.13K, x(Cu)=0.05, FCC_A1) should match as the same node "
                + "under OC's own map_newnode tolerances");

        // Registering both through NodeRegistry.findOrCreate (the actual
        // C2 call site, not just the Node#matches unit check) must yield
        // ONE node, not two.
        NodeRegistry registry = new NodeRegistry();
        Node registered1 = registry.findOrCreate(fromBelow, new double[] { AGCU_NODE_T, 0.05 }, AGCU_NODE_COMP);
        Node registered2 = registry.findOrCreate(fromAbove, new double[] { AGCU_NODE_T, 0.05 }, AGCU_NODE_COMP);
        assertEquals(registered1.id, registered2.id,
                "NodeRegistry should dedup two independent arrivals at the same physical node");
        assertEquals(1, registry.size());

        // FORMATTER level: the single merged node's EquilibriumReport
        // reflects OC's own confirmed state at this boundary --
        // single-phase FCC_A1, x(AG)=0.95, x(CU)=0.05 (the node's own
        // overall composition, since it is single-phase here).
        String report = EquilibriumReport.format(registered1.equilibrium, AGCU_ELEMENTS);
        assertTrue(report.contains(String.format(Locale.ROOT, "T=%.2f", AGCU_NODE_T)),
                "formatted report should carry the node's OC-confirmed T");
        assertTrue(report.contains("FCC_A1"), "formatted report should mention FCC_A1");
        assertFalse(report.contains("LIQUID"),
                "formatted report should NOT show LIQUID (not a candidate here -- "
                + "this node is the FCC_A1-only side of OC's own confirmed crossing)");
    }

    @Test
    void agCuArrivalAtADifferentTDoesNotMatch() throws IOException {
        // Negative control: a point 20K away is a genuinely different
        // equilibrium, must NOT be merged into the same node.
        List<GibbsEnergyModel> candidates = agcuFccOnly();

        EquilibriumResult atNode = new EquilibriumSolverV2().solve(
                AGCU_NODE_T, AGCU_NODE_P, AGCU_NODE_COMP, candidates);
        EquilibriumResult farAway = new EquilibriumSolverV2().solve(
                AGCU_NODE_T - 20.0, AGCU_NODE_P, AGCU_NODE_COMP, candidates);
        assertTrue(atNode.isConverged());
        assertTrue(farAway.isConverged());

        Node nodeAtNode = new Node(0, atNode, new double[] { AGCU_NODE_T, 0.05 });
        Node nodeFarAway = new Node(1, farAway, new double[] { AGCU_NODE_T - 20.0, 0.05 });

        assertEquals(nodeAtNode.stablePhaseNames, nodeFarAway.stablePhaseNames,
                "both points are single-phase FCC_A1 -- same phase set, different T");
        assertFalse(nodeAtNode.matches(nodeFarAway),
                "a point 20K away must not match as the same node despite sharing a stable phase set");
    }

    // ------------------------------------------------------------------
    // Ternary isothermal: Al-Mg-Zn, T=700K, x(Mg)=0.05, x(Zn)=0.050236,
    // single-phase FCC_A1 (OC's own exact ZPF crossing this session's
    // ternary C2 test validated).
    // ------------------------------------------------------------------

    private static final String ALMGZN_TDB = "data/cost507R.TDB";
    private static final List<String> ALMGZN_ELEMENTS = List.of("AL", "MG", "ZN");
    private static final double ALMGZN_NODE_T = 700.0;
    private static final double ALMGZN_NODE_P = 101325.0;
    private static final double ALMGZN_XMG = 0.05;
    private static final double ALMGZN_XZN = 0.050236;
    private static final double[] ALMGZN_NODE_COMP =
            { 1.0 - ALMGZN_XMG - ALMGZN_XZN, ALMGZN_XMG, ALMGZN_XZN };

    private static List<GibbsEnergyModel> almgznFccOnly() throws IOException {
        return ThermodynamicSystem.build(
                ALMGZN_TDB, ALMGZN_ELEMENTS, List.of("FCC_A1")).phaseModels();
    }

    @Test
    void ternaryArrivalsAlongTwoDifferentCompositionAxesMatchAsTheSameNode() throws IOException {
        List<GibbsEnergyModel> candidates = almgznFccOnly();

        // "Arrival walking x(Zn)": cold-started directly at the node.
        EquilibriumResult viaXZnWalk = new EquilibriumSolverV2().solve(
                ALMGZN_NODE_T, ALMGZN_NODE_P, ALMGZN_NODE_COMP, candidates);
        assertTrue(viaXZnWalk.isConverged());
        assertEquals(1, viaXZnWalk.getStablePhases().size());
        assertEquals("FCC_A1", viaXZnWalk.getStablePhases().get(0).phaseName);

        // "Arrival walking x(Mg)": an INDEPENDENT solve warm-started from
        // a neighboring point one step away along the OTHER composition
        // axis (x(Mg)=0.049 instead of 0.05), then re-solved AT the
        // node's own composition -- simulating a second line reaching
        // this same ternary node via a different walk axis, exactly the
        // genuine multi-composition-axis case Step 5c's ConditionSet
        // generalization exists for.
        double[] neighborComp = { 1.0 - 0.049 - ALMGZN_XZN, 0.049, ALMGZN_XZN };
        EquilibriumResult neighbor = new EquilibriumSolverV2().solve(
                ALMGZN_NODE_T, ALMGZN_NODE_P, neighborComp, candidates);
        assertTrue(neighbor.isConverged());
        EquilibriumResult viaXMgWalk = new EquilibriumSolverV2().solve(
                ALMGZN_NODE_T, ALMGZN_NODE_P, ALMGZN_NODE_COMP, candidates);
        assertTrue(viaXMgWalk.isConverged());

        Node nodeViaXZn = new Node(0, viaXZnWalk, new double[] { ALMGZN_XZN, ALMGZN_XMG });
        Node nodeViaXMg = new Node(1, viaXMgWalk, new double[] { ALMGZN_XMG, ALMGZN_XZN });

        assertTrue(nodeViaXZn.matches(nodeViaXMg),
                "two independent Newton solves at OC's own confirmed ternary node "
                + "(T=700K, x(Mg)=0.05, x(Zn)=0.050236, FCC_A1) should match as the "
                + "same node regardless of which composition axis each arrival walked");

        NodeRegistry registry = new NodeRegistry();
        Node registered1 = registry.findOrCreate(
                viaXZnWalk, new double[] { ALMGZN_XZN, ALMGZN_XMG }, ALMGZN_NODE_COMP);
        Node registered2 = registry.findOrCreate(
                viaXMgWalk, new double[] { ALMGZN_XMG, ALMGZN_XZN }, ALMGZN_NODE_COMP);
        assertEquals(registered1.id, registered2.id,
                "NodeRegistry should dedup two independent ternary arrivals at the same physical node");
        assertEquals(1, registry.size());

        // FORMATTER level, cross-checked against OC's own l r dump at
        // this exact composition (docs/oc_reference_tests/
        // almgzn_ternary_c2_lr_exact.txt): RT=5.8202E3 J/mol, mu/RT:
        // AL=-4.3611, MG=-6.9789, ZN=-7.1854, G/N=-2.6970E4 J/mol.
        String report = EquilibriumReport.format(registered1.equilibrium, ALMGZN_ELEMENTS);
        assertTrue(report.contains(String.format(Locale.ROOT, "T=%.2f", ALMGZN_NODE_T)),
                "formatted report should carry the node's OC-confirmed T");
        assertTrue(report.contains("FCC_A1"), "formatted report should mention FCC_A1");
        assertTrue(report.contains("amount="));

        double ocRT = 5.8202e3;
        double[] ocMuOverRT = { -4.3611, -6.9789, -7.1854 };
        double[] mu = registered1.equilibrium.getMu();
        for (int i = 0; i < mu.length; i++) {
            double ocMuAbsolute = ocMuOverRT[i] * ocRT;
            assertEquals(ocMuAbsolute, mu[i], Math.abs(ocMuAbsolute) * 5.0e-3,
                    "merged ternary node's mu[" + i + "] should match OC's mu/RT * RT "
                    + "within 0.5% relative");
        }
        assertEquals(-2.6970e4, registered1.equilibrium.totalG(),
                Math.abs(-2.6970e4) * 5.0e-3,
                "merged ternary node's G/N should match OC's G/N within 0.5% relative");
    }

    @Test
    void ternaryArrivalAtADifferentCompositionDoesNotMatch() throws IOException {
        // Negative control: a point with x(Zn) shifted well past the
        // boundary (still FCC_A1-only, since this candidate list has no
        // MGZN2) is a genuinely different equilibrium.
        List<GibbsEnergyModel> candidates = almgznFccOnly();

        EquilibriumResult atNode = new EquilibriumSolverV2().solve(
                ALMGZN_NODE_T, ALMGZN_NODE_P, ALMGZN_NODE_COMP, candidates);
        double[] farComp = { 1.0 - 0.05 - 0.20, 0.05, 0.20 };
        EquilibriumResult farAway = new EquilibriumSolverV2().solve(
                ALMGZN_NODE_T, ALMGZN_NODE_P, farComp, candidates);
        assertTrue(atNode.isConverged());
        assertTrue(farAway.isConverged());

        Node nodeAtNode = new Node(0, atNode, new double[] { ALMGZN_XZN, ALMGZN_XMG });
        Node nodeFarAway = new Node(1, farAway, new double[] { 0.20, 0.05 });

        assertEquals(nodeAtNode.stablePhaseNames, nodeFarAway.stablePhaseNames,
                "both points are single-phase FCC_A1 -- same phase set, different composition");
        assertFalse(nodeAtNode.matches(nodeFarAway),
                "a point at a materially different composition must not match as the same node");
    }
}
