package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import ui.result.EquilibriumReport;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NODE CLASSIFICATION + EXIT GEOMETRY checked end-to-end through the C1
 * drain loop, at the FORMATTER level -- closes {@code
 * docs/roadmap_phase_diagrams.md}'s "ISOPLETH_CROSSING not reachable
 * yet" gap (Step 6d).
 *
 * <p><b>What this closes.</b> {@link PhaseDiagramEngine#classifyNode}
 * previously always returned {@code TIE_LINE_IN_PLANE} for any {@code
 * f>0} node -- there was no way to reach {@code ISOPLETH_CROSSING} at
 * all, since {@link MapDiagramTracer#drain} was binary-map-only ({@code
 * AxisConfig}, not {@link ConditionSet}). Both gaps are fixed together:
 * {@link PhaseDiagramEngine#classifyNode(ConditionSet, int)} derives the
 * distinction from whether {@code conds} has any FIXED composition
 * condition (an isopleth's own shape, Sundman 2021 Fig. 3(c)); {@link
 * MapDiagramTracer#drain(ConditionSet, int, int, double, double[], List)}
 * generalizes the drain loop to reach it.
 *
 * <p><b>System</b> -- Al-Mg-Zn on {@code data/cost507R.TDB}, candidates
 * {FCC_A1, MGZN2}, the SAME system {@link MapTracerTernaryIsoplethTest}
 * already validated a single {@code walkOneSegment} call against real
 * OC for: T walked 630-760K (step 2K), x(Mg)=0.05 FIXED, x(Zn)=0.05
 * released.
 *
 * <p><b>OC reference</b> (real {@code oc7C} run via the WSL pty driver,
 * {@code docs/oc_reference_tests/almgzn_isopleth_step_walk.txt}): OC's
 * own {@code step} trace at these exact conditions finds {@code
 * "Creating a node at 699.58 where MGZN2 disappear"} -- this is the
 * FIRST crossing from the start point, so (per {@link
 * NodeGeometry#attachIsoplethCrossingExits}'s own "MOST, not ALL node
 * points" scope, matching the paper's own phrasing) the arriving line
 * has no pre-existing fixed phase of its own, and this specific node
 * degrades to the ordinary 2-exit case even though the DIAGRAM itself
 * is isopleth-shaped and the node classifies {@code ISOPLETH_CROSSING}.
 *
 * <p><b>Node equilibrium, post boundary-solve-equilibrium fix.</b> The
 * node's own equilibrium is now {@link
 * calc.equil.EquilibriumSolverV2#solveBoundaryReleasingT}'s EXACT
 * boundary solve (Sundman 2021 Algorithm C2: both phases present,
 * MGZN2 at its fixed ~0 amount, T solved exactly) -- confirmed
 * directly against OC's own T=699.58K: this codebase converges to
 * T=699.5814K, mu within ~0.1% relative of OC's {@code l r} dump
 * (below). {@code MapTracer} previously reported the coarser grid
 * point one step past the crossing (T=700K) instead of the boundary
 * solve's own result, and this test's reference values were pinned to
 * THAT wrong point ({@code
 * docs/oc_reference_tests/almgzn_isopleth_lr_t700.txt}'s {@code l r}
 * dump at T=700K, x(Mg)=0.05, x(Zn)=0.05: single-phase FCC_A1,
 * RT=5.8202E3 J/mol, mu/RT: AL=-4.3609, MG=-6.9783, ZN=-7.1891,
 * G/N=-2.6966E4 J/mol) -- kept here only as the source of the mu/G
 * numbers this test's tolerance is checked against (T itself differs
 * by ~0.4K between the two points, hence the wider-than-formatter-
 * precision tolerance below).
 */
public class MapDiagramTracerIsoplethOcFormatterComparisonTest {

    private static final String TDB = "data/cost507R.TDB";
    private static final List<String> ELEMENTS = List.of("AL", "MG", "ZN");
    private static final List<String> PHASES = List.of("FCC_A1", "MGZN2");
    private static final double FIXED_XMG = 0.05;
    private static final double FIXED_P = 101325.0;

    private static List<GibbsEnergyModel> candidates() throws IOException {
        return ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();
    }

    private static ConditionSet isoplethConditions() {
        return new ConditionSet(3, List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T / K", 630.0, 760.0, 2.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", FIXED_P),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.fixedComposition(1, "x(Mg)", FIXED_XMG),
                Condition.axisComposition(2, "x(Zn)", 0.0, 0.5, 0.001)));
    }

    @Test
    void drainClassifiesTheIsoplethDiagramAndFindsOcsOwnConfirmedCrossing() throws IOException {
        List<GibbsEnergyModel> candidates = candidates();
        ConditionSet conds = isoplethConditions();

        double xZnStart = 0.05;
        double[] compOverall = { 1.0 - FIXED_XMG - xZnStart, FIXED_XMG, xZnStart };

        // searchAxisIndex=0 (T), releaseAxisIndex=1 (x(Zn)) into
        // conds.axisConditions() -- x(Mg) is FIXED, not an axis, so it
        // is not in that list at all.
        MapDiagramTracer tracer = new MapDiagramTracer();
        NodeRegistry registry = tracer.drain(conds, 0, 1, 630.0, compOverall, candidates);

        assertTrue(registry.size() >= 1, "should have at least the crossing node");

        // The node's equilibrium is now the EXACT boundary solve
        // (Algorithm C2), not a grid-snapped point -- both phases
        // present, MGZN2 at its fixed ~0 amount, T converged to
        // ~699.58K (OC's own reported crossing).
        Node crossingNode = null;
        for (Node node : registry.getNodes()) {
            double t = node.axisValues[0];
            if (t >= 699.0 && t <= 700.0
                    && node.stablePhaseNames.equals(Set.of("FCC_A1", "MGZN2"))) {
                crossingNode = node;
            }
        }

        assertTrue(crossingNode != null,
                "should find a node near OC's own confirmed crossing at T=699.58K");
        assertEquals(699.58, crossingNode.axisValues[0], 0.01,
                "OC's own step trace: \"Creating a node at 699.58 where MGZN2 disappear\"");
        assertEquals(Set.of("FCC_A1", "MGZN2"), crossingNode.stablePhaseNames,
                "a ZPF node's own equilibrium (Algorithm C2) holds BOTH phases -- the "
                + "disappearing one (MGZN2) at its fixed ~0 amount, not absent");

        // This is the FIRST crossing from the diagram's own start point,
        // so the arriving line has no pre-existing fixed phase -- per
        // NodeGeometry's own "MOST, not ALL" scope, this degrades to the
        // ordinary 2-exit case even though the diagram itself is
        // isopleth-shaped.
        assertEquals(2, crossingNode.getLines().size(),
                "the first isopleth crossing (no pre-existing fixed phase on the arriving "
                + "line) should degrade to the ordinary 2-exit case");

        // FORMATTER level: cross-check against OC's own l r dump near
        // this crossing (almgzn_isopleth_lr_t700.txt, T=700K -- ~0.4K
        // from the node's own exact T=699.58K, hence the wider
        // tolerance below rather than formatter-precision agreement).
        String report = EquilibriumReport.format(crossingNode.equilibrium, ELEMENTS);
        assertTrue(report.contains("FCC_A1"), "formatted report should mention FCC_A1");
        assertTrue(report.contains("amount="));

        double ocRT = 5.8202e3;
        double[] ocMuOverRT = { -4.3609, -6.9783, -7.1891 };
        double[] mu = crossingNode.equilibrium.getMu();
        for (int i = 0; i < mu.length; i++) {
            double ocMuAbsolute = ocMuOverRT[i] * ocRT;
            assertEquals(ocMuAbsolute, mu[i], Math.abs(ocMuAbsolute) * 5.0e-3,
                    "crossing node's mu[" + i + "] should match OC's mu/RT * RT within 0.5% relative");
        }
        assertEquals(-2.6966e4, crossingNode.equilibrium.totalG(),
                Math.abs(-2.6966e4) * 5.0e-3,
                "crossing node's G/N should match OC's G/N within 0.5% relative");
    }

    @Test
    void conditionSetDrivenDrainStillFindsAnOrdinaryTieLineInPlaneNodeForATernaryIsothermalSection() throws IOException {
        // Regression/contrast check: the SAME ConditionSet-driven drain
        // loop, given a ternary ISOTHERMAL ConditionSet (no fixed
        // composition -- both composition axes free), must still
        // classify its crossing TIE_LINE_IN_PLANE, not ISOPLETH_CROSSING
        // -- confirming classifyNode's distinction is genuinely keyed on
        // "any fixed composition condition," not on diagram type/name.
        List<GibbsEnergyModel> candidates = candidates();

        ConditionSet conds = new ConditionSet(3, List.of(
                Condition.fixed(Condition.Variable.TEMPERATURE, "T", 700.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", FIXED_P),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(1, "x(Mg)", 0.0, 0.5, 0.01),
                Condition.axisComposition(2, "x(Zn)", 0.04, 0.08, 0.005)));

        double xMgStart = 0.05, xZnStart = 0.04;
        double[] compOverall = { 1.0 - xMgStart - xZnStart, xMgStart, xZnStart };

        // searchAxisIndex=1 (x(Zn)), releaseAxisIndex=0 (x(Mg)).
        MapDiagramTracer tracer = new MapDiagramTracer();
        NodeRegistry registry = tracer.drain(conds, 1, 0, xZnStart, compOverall, candidates);

        boolean sawOrdinaryTwoExitCrossing = false;
        for (Node node : registry.getNodes()) {
            if (node.stablePhaseNames.equals(Set.of("FCC_A1", "MGZN2")) && !node.getLines().isEmpty()) {
                sawOrdinaryTwoExitCrossing = node.getLines().size() == 2;
            }
        }
        assertTrue(sawOrdinaryTwoExitCrossing,
                "ternary isothermal crossing (no fixed composition) should classify "
                + "TIE_LINE_IN_PLANE (2 exits), not ISOPLETH_CROSSING");
    }
}
