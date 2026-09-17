package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link PhaseDiagramEngine}'s skeleton matches {@code
 * docs/phase_diagram_engine_flowchart.md}: implemented stages produce
 * real results (delegating to already-tested Steps 1-4 code), and
 * unimplemented stages throw {@link UnsupportedOperationException}
 * rather than silently doing nothing.
 *
 * <p>This test exists to keep the skeleton honest over time -- if a
 * stage listed here as "not yet implemented" is later implemented, this
 * test's corresponding {@code assertThrows} will fail, forcing an
 * intentional update here (and, ideally, of the flowchart/roadmap docs)
 * rather than letting the skeleton's javadoc silently drift out of date.
 */
public class PhaseDiagramEngineTest {

    private static final String TDB = "data/agcu.TDB";
    private static final List<String> ELEMENTS = List.of("AG", "CU");
    private static final List<String> PHASES = List.of("LIQUID", "FCC_A1");

    @Test
    void defineSystemIsImplemented() throws IOException {
        ThermodynamicSystem system = PhaseDiagramEngine.defineSystem(TDB, ELEMENTS, PHASES);
        assertEquals(2, system.elements().size());
        assertEquals(2, system.phaseModels().size());
    }

    @Test
    void generateStartingPointsReturnsTheSingleSuppliedPoint() {
        List<double[]> points = PhaseDiagramEngine.generateStartingPoints(1150.0);
        assertEquals(1, points.size());
        assertEquals(1150.0, points.get(0)[0]);
    }

    @Test
    void drainC1LoopIsImplementedForTheOrdinaryMapCase() throws IOException {
        List<GibbsEnergyModel> candidates =
                ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();

        AxisConfig walkAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1150.0, 1230.0, 5.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cu)", 1, 0.01, 0.6, 0.01);

        NodeRegistry registry = PhaseDiagramEngine.drainC1Loop(
                walkAxis, releaseAxis, 1150.0, 101325.0, 1150.0,
                new double[] { 0.95, 0.05 }, candidates);

        assertTrue(registry.size() >= 1, "the drain loop should have produced at least a start node");
    }

    /**
     * OC-confirmed cases (via the pty driver on the real {@code oc7C}
     * binary, {@code docs/oc_reference_tests/run_pty.py}): a 2-component
     * Ag-Cu system with only T,P set (2 conditions) raises OC's error
     * 4144 ("Degrees of freedom not zero"); the same system with
     * T,P,N,x(Cu),x(Ag) set (5 conditions) raises the SAME error 4144 --
     * OC's idf != 0 check is symmetric, not underdetermined-only.
     */
    @Test
    void rejectsTooFewConditionsMatchingOcsUnderdeterminedCase() {
        // 2-component system, only T and P set (2 conditions; needs 4).
        assertThrows(IllegalArgumentException.class,
                () -> PhaseDiagramEngine.validateConditionCount(2, 2));
    }

    @Test
    void rejectsTooManyConditionsMatchingOcsOverdeterminedCase() {
        // 2-component system, T,P,N,x(Cu),x(Ag) set (5 conditions; needs 4).
        assertThrows(IllegalArgumentException.class,
                () -> PhaseDiagramEngine.validateConditionCount(2, 5));
    }

    @Test
    void acceptsExactlyNPlusTwoConditions() {
        // 2-component system, T,P,N,x(Cu) set (4 conditions) -- matches OC's
        // correctly-determined case, which every other equilibrium call in
        // this codebase already relies on succeeding.
        PhaseDiagramEngine.validateConditionCount(2, 4);
    }

    /**
     * See {@code calc.equil.PhaseDiagramEngineGlobalStabilityTest} for
     * the real calibration cases -- those need package-private access
     * to {@code EquilibriumSolverV2.setInitialStateForTest} to force a
     * genuinely-converged-but-globally-unstable point, unavailable from
     * this package.
     */
    @Test
    void isGloballyStableAcceptsAGenuinelyStablePoint() throws Exception {
        List<GibbsEnergyModel> candidates = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1")).phaseModels();
        double[] comp = { 0.95, 0.05 };

        var result = new calc.equil.EquilibriumSolverV2().solve(1150.0, 101325.0, comp, candidates);

        assertTrue(result.isConverged());
        assertTrue(PhaseDiagramEngine.isGloballyStable(result, candidates));
    }

    /**
     * Eq. 8 worked against the paper's own binary invariant example
     * (§3.3: "A binary isobaric phase diagram has f=3-p... and an
     * invariant has thus 3 stable phases" -- isobaric means P is FIXED,
     * so c=1 for n=2, giving f=2+2-p-1=3-p; see {@link
     * PhaseDiagramEngine#classifyNode}'s javadoc for the full
     * derivation) and against this project's own OC-referenced binary
     * cases (V-Zr peritectic: 3 phases at the invariant, per {@code
     * CalculationSessionMapTracerTest} Section C; Ag-Cu ordinary
     * crossings: 2 phases, per {@code MapDiagramTracerAgCuTest}).
     */
    @Test
    void classifyNodeDistinguishesOrdinaryFromInvariantForABinaryIsobaricSystem() {
        // Binary T-x (Ag-Cu's own shape): T=AXIS, P=FIXED, N=FIXED,
        // x(Cu)=AXIS -- c=1 (P is the only fixed potential condition).
        ConditionSet binaryIsobaric = new ConditionSet(2, List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 1150, 1230, 5),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 101325.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(1, "x(Cu)", 0.0, 1.0, 0.01)));

        // p=2 (ordinary 2-phase crossing, e.g. Ag-Cu's liquidus):
        // f=2+2-2-1=1 -- ordinary.
        assertEquals(PhaseDiagramEngine.NodeClass.TIE_LINE_IN_PLANE,
                PhaseDiagramEngine.classifyNode(binaryIsobaric, 2));

        // p=3 (V-Zr's 1586K peritectic: BCC_A2+V2ZR+LIQUID): f=2+2-3-1=0
        // -- invariant, matching the paper's own p=3 result for a binary
        // isobaric invariant exactly.
        assertEquals(PhaseDiagramEngine.NodeClass.INVARIANT,
                PhaseDiagramEngine.classifyNode(binaryIsobaric, 3));
    }

    @Test
    void conditionSetClassifyNodeDistinguishesTieLineInPlaneFromIsopleth() {
        // Binary T-x (Ag-Cu's own shape): T=AXIS, P=FIXED, N=FIXED,
        // x(Cu)=AXIS -- no FIXED composition, so ordinary p=2 crossing
        // is TIE_LINE_IN_PLANE.
        ConditionSet binary = new ConditionSet(2, List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 1000, 1200, 5),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 101325.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(1, "x(Cu)", 0.0, 1.0, 0.01)));
        assertEquals(PhaseDiagramEngine.NodeClass.TIE_LINE_IN_PLANE,
                PhaseDiagramEngine.classifyNode(binary, 2));

        // Ternary isothermal (Al-Mg-Zn Step 5c's own shape): T=FIXED,
        // P=FIXED, N=FIXED, x(Mg)=AXIS, x(Zn)=AXIS -- still no FIXED
        // composition (both are AXIS), so still TIE_LINE_IN_PLANE.
        ConditionSet ternaryIsothermal = new ConditionSet(3, List.of(
                Condition.fixed(Condition.Variable.TEMPERATURE, "T", 700.0),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 101325.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.axisComposition(1, "x(Mg)", 0.0, 0.5, 0.01),
                Condition.axisComposition(2, "x(Zn)", 0.0, 0.5, 0.01)));
        assertEquals(PhaseDiagramEngine.NodeClass.TIE_LINE_IN_PLANE,
                PhaseDiagramEngine.classifyNode(ternaryIsothermal, 2));

        // Ternary isopleth (Al-Mg-Zn Step 5e's own shape, Fig. 3(c)):
        // T=AXIS, P=FIXED, N=FIXED, x(Mg)=FIXED, x(Zn)=AXIS -- x(Mg) is
        // FIXED, so this IS an isopleth-shaped diagram.
        ConditionSet isopleth = new ConditionSet(3, List.of(
                Condition.axis(Condition.Variable.TEMPERATURE, "T", 630, 760, 2),
                Condition.fixed(Condition.Variable.PRESSURE, "P", 101325.0),
                Condition.fixed(Condition.Variable.TOTAL_MOLES, "N", 1.0),
                Condition.fixedComposition(1, "x(Mg)", 0.05),
                Condition.axisComposition(2, "x(Zn)", 0.0, 0.5, 0.001)));
        assertEquals(PhaseDiagramEngine.NodeClass.ISOPLETH_CROSSING,
                PhaseDiagramEngine.classifyNode(isopleth, 2));

        // The SAME isopleth ConditionSet still recognizes a genuine
        // invariant: n=3, p=4 (isopleth's own invariant per §3.3's own
        // Fig. 13(c) discussion), c=2 (T is AXIS here, so only P is
        // fixed... wait: T is AXIS, so c counts only P=1 fixed potential.
        // f=3+2-4-1=0 -- invariant.
        assertEquals(PhaseDiagramEngine.NodeClass.INVARIANT,
                PhaseDiagramEngine.classifyNode(isopleth, 4));
    }

    @Test
    void mergeDedupNetworkFiltersExcludedLinesOnly() {
        // Node identity is governed by Node.matches (stable phases, T/P,
        // chemical potentials), so build the two nodes via the registry's
        // own findOrCreate rather than constructing Node directly.
        NodeRegistry registry = new NodeRegistry();
        Node registryStart = registry.findOrCreate(
                dummyEquilibrium(1000.0), new double[] { 1000.0, 0.5 }, new double[] { 0.5, 0.5 });
        Node registryEnd = registry.findOrCreate(
                dummyEquilibrium(1050.0), new double[] { 1050.0, 0.5 }, new double[] { 0.6, 0.4 });

        Line keptLine = new Line(registryStart, List.of(), 0, +1);
        keptLine.startWalking();
        keptLine.terminateAtNode(registryEnd);
        registryStart.addLine(keptLine);

        Line excludedLine = new Line(registryStart, List.of(), 0, -1);
        excludedLine.startWalking();
        excludedLine.terminateAtNode(registryEnd);
        excludedLine.markExcluded();
        registryStart.addLine(excludedLine);

        List<Line> result = PhaseDiagramEngine.mergeDedupNetwork(registry);

        assertEquals(1, result.size());
        assertTrue(result.contains(keptLine));
    }

    @Test
    void mergeDedupNetworkOnEmptyRegistryReturnsEmptyList() {
        assertTrue(PhaseDiagramEngine.mergeDedupNetwork(new NodeRegistry()).isEmpty());
    }

    private static system.ports.EquilibriumResult dummyEquilibrium(double t) {
        system.ports.EquilibriumResult.PhaseResult liquid = new system.ports.EquilibriumResult.PhaseResult(
                "LIQUID", "CEF", 1.0, new double[] { 0.5, 0.5 }, new double[] { 0.5, 0.5 },
                -1000.0, 0.0, 1.0);
        return new system.ports.EquilibriumResult(t, 101325.0, new double[] { -1000.0, -1000.0 },
                List.of(liquid), List.of(), true, 5);
    }

    @Test
    void identifyPhaseRegionsLabelsNodesAndLines() {
        NodeRegistry registry = new NodeRegistry();
        Node startNode = registry.findOrCreate(
                dummyEquilibrium(1000.0), new double[] { 1000.0, 0.5 }, new double[] { 0.5, 0.5 });
        Node endNode = registry.findOrCreate(
                twoPhaseEquilibrium(1050.0), new double[] { 1050.0, 0.5 }, new double[] { 0.6, 0.4 });

        Line line = new Line(startNode, List.of(), 0, +1);
        line.startWalking();
        line.addPoint(twoPhaseEquilibrium(1010.0), new double[] { 1010.0, 0.5 });
        line.terminateAtNode(endNode);
        startNode.addLine(line);

        PhaseRegions regions = PhaseDiagramEngine.identifyPhaseRegions(registry);

        assertEquals(Set.of("LIQUID"), regions.nodeLabels().get(startNode));
        assertEquals(Set.of("LIQUID", "FCC_A1"), regions.nodeLabels().get(endNode));
        assertEquals(Set.of("LIQUID", "FCC_A1"), regions.lineLabels().get(line));
        assertTrue(regions.tieTriangles().isEmpty());
    }

    @Test
    void identifyPhaseRegionsBuildsTieTriangleAtThreePhaseNode() {
        NodeRegistry registry = new NodeRegistry();
        Node node = registry.findOrCreate(
                threePhaseEquilibrium(), new double[] { 1000.0, 0.5 }, new double[] { 0.34, 0.33 });

        PhaseRegions regions = PhaseDiagramEngine.identifyPhaseRegions(registry);

        assertEquals(1, regions.tieTriangles().size());
        PhaseRegions.TieTriangle triangle = regions.tieTriangles().get(0);
        assertEquals(node, triangle.node);
        assertEquals(List.of("LIQUID", "FCC_A1", "BCC_A2"), triangle.phaseNames);
        assertEquals(3, triangle.vertices.size());
        // Each vertex IS the corresponding phase's own composition --
        // Sundman 2021 §2.4's "corners indicate the compositions of the
        // three phases in equilibrium" -- no separate geometry computed.
        assertArrayEquals(new double[] { 0.2, 0.8 }, triangle.vertices.get(0), 1e-12);
        assertArrayEquals(new double[] { 0.9, 0.1 }, triangle.vertices.get(1), 1e-12);
        assertArrayEquals(new double[] { 0.05, 0.95 }, triangle.vertices.get(2), 1e-12);
    }

    private static system.ports.EquilibriumResult twoPhaseEquilibrium(double t) {
        system.ports.EquilibriumResult.PhaseResult liquid = new system.ports.EquilibriumResult.PhaseResult(
                "LIQUID", "CEF", 0.5, new double[] { 0.5, 0.5 }, new double[] { 0.5, 0.5 },
                -1000.0, 0.0, 1.0);
        system.ports.EquilibriumResult.PhaseResult fcc = new system.ports.EquilibriumResult.PhaseResult(
                "FCC_A1", "CEF", 0.5, new double[] { 0.7, 0.3 }, new double[] { 0.7, 0.3 },
                -900.0, 0.0, 1.0);
        return new system.ports.EquilibriumResult(t, 101325.0, new double[] { -1000.0, -1000.0 },
                List.of(liquid, fcc), List.of(), true, 5);
    }

    private static system.ports.EquilibriumResult threePhaseEquilibrium() {
        system.ports.EquilibriumResult.PhaseResult liquid = new system.ports.EquilibriumResult.PhaseResult(
                "LIQUID", "CEF", 0.34, new double[] { 0.2, 0.8 }, new double[] { 0.2, 0.8 },
                -1000.0, 0.0, 1.0);
        system.ports.EquilibriumResult.PhaseResult fcc = new system.ports.EquilibriumResult.PhaseResult(
                "FCC_A1", "CEF", 0.33, new double[] { 0.9, 0.1 }, new double[] { 0.9, 0.1 },
                -900.0, 0.0, 1.0);
        system.ports.EquilibriumResult.PhaseResult bcc = new system.ports.EquilibriumResult.PhaseResult(
                "BCC_A2", "CEF", 0.33, new double[] { 0.05, 0.95 }, new double[] { 0.05, 0.95 },
                -950.0, 0.0, 1.0);
        return new system.ports.EquilibriumResult(1000.0, 101325.0, new double[] { -1000.0, -1000.0 },
                List.of(liquid, fcc, bcc), List.of(), true, 5);
    }

    @Test
    void classifyPlotBinaryTxSplitsATwoPhaseLineIntoOneSegmentPerStablePhase() {
        // Sundman 2021 §4.1: a proper T-x diagram plots the mole fraction
        // in EACH stable phase, not the overall composition -- one walked
        // two-phase run (LIQUID+FCC_A1 at every point) must become TWO
        // LineSegments here, one per phase, each carrying that phase's
        // OWN composition at component index 1 (twoPhaseEquilibrium's
        // fixture: LIQUID x=[0.5,0.5] -> 0.5, FCC_A1 x=[0.7,0.3] -> 0.3),
        // not the Node's tracked overall composition (0.5 then 0.6) that
        // used to leak through before this fix.
        NodeRegistry registry = new NodeRegistry();
        Node startNode = registry.findOrCreate(
                twoPhaseEquilibrium(1000.0), new double[] { 1000.0, 0.5 }, new double[] { 0.5, 0.5 });
        Node endNode = registry.findOrCreate(
                twoPhaseEquilibrium(1050.0), new double[] { 1050.0, 0.6 }, new double[] { 0.6, 0.4 });

        Line line = new Line(startNode, List.of(), 0, +1);
        line.startWalking();
        line.addPoint(twoPhaseEquilibrium(1000.0), new double[] { 1000.0, 0.5 });
        line.addPoint(twoPhaseEquilibrium(1050.0), new double[] { 1050.0, 0.6 });
        line.terminateAtNode(endNode);
        startNode.addLine(line);

        PhaseDiagramResult result = PhaseDiagramEngine.classifyPlot(
                registry, PhaseDiagramEngine.PlotType.BINARY_T_X,
                new String[] { "T", "x(Cu)" }, new double[] { 900.0, 0.0 }, new double[] { 1100.0, 1.0 },
                1, 1);

        assertEquals(2, result.getLines().size(), "one LineSegment per stable phase (LIQUID, FCC_A1)");
        assertEquals(2, result.getNodes().size());

        PhaseDiagramResult.LineSegment liquidSeg = null, fccSeg = null;
        for (PhaseDiagramResult.LineSegment seg : result.getLines()) {
            if (seg.stablePhases.equals(List.of("LIQUID"))) liquidSeg = seg;
            if (seg.stablePhases.equals(List.of("FCC_A1"))) fccSeg = seg;
        }
        assertTrue(liquidSeg != null && fccSeg != null, "expected exactly one LIQUID segment and one FCC_A1 segment");

        // Each segment's composition coordinate is that phase's OWN x --
        // constant across both points in this fixture (twoPhaseEquilibrium
        // doesn't vary composition with T), NOT the node's tracked overall
        // composition (0.5 then 0.6).
        for (double[] c : liquidSeg.coords) assertEquals(0.5, c[1], 1e-12);
        for (double[] c : fccSeg.coords) assertEquals(0.3, c[1], 1e-12);
        assertEquals(1000.0, liquidSeg.coords.get(0)[0], 1e-12);
        assertEquals(1050.0, liquidSeg.coords.get(1)[0], 1e-12);

        for (PhaseDiagramResult.NodePoint node : result.getNodes()) {
            assertEquals(PhaseDiagramResult.NodePoint.Type.CROSSING, node.type,
                    "2 or fewer stable phases on a 2-axis diagram is an ordinary crossing, not an invariant");
        }
    }

    @Test
    void classifyPlotRejectsWrongAxisCount() {
        assertThrows(IllegalArgumentException.class,
                () -> PhaseDiagramEngine.classifyPlot(
                        new NodeRegistry(), PhaseDiagramEngine.PlotType.BINARY_T_X,
                        new String[] { "T" }, new double[] { 900.0 }, new double[] { 1100.0 }, 1, 0),
                "BINARY_T_X needs 2 axes -- VALIDATE PLOT should reject 1");
    }

    @Test
    void classifyPlotPropertyOrStepDiagramAcceptsOneAxis() {
        PhaseDiagramResult result = PhaseDiagramEngine.classifyPlot(
                new NodeRegistry(), PhaseDiagramEngine.PlotType.PROPERTY_OR_STEP_DIAGRAM,
                new String[] { "T" }, new double[] { 900.0 }, new double[] { 1100.0 }, -1, -1);
        assertTrue(result.getLines().isEmpty());
        assertTrue(result.getNodes().isEmpty());
    }

    @Test
    void classifyPlotStillThrowsForUnimplementedPlotTypes() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.classifyPlot(
                        new NodeRegistry(), PhaseDiagramEngine.PlotType.ACTIVITY_OR_CHEMICAL_POTENTIAL,
                        new String[] { "T", "AC(CU)" }, new double[] { 900.0, 0.0 }, new double[] { 1100.0, 1.0 }, 1, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.classifyPlot(
                        new NodeRegistry(), PhaseDiagramEngine.PlotType.H_X_S_X_G_X,
                        new String[] { "x(Cu)", "H" }, new double[] { 0.0, 0.0 }, new double[] { 1.0, 1.0 }, 0, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.classifyPlot(
                        new NodeRegistry(), PhaseDiagramEngine.PlotType.MULTICOMPONENT_ISOPLETH_OR_PSEUDO_ISOTHERMAL,
                        new String[] { "T", "x" }, new double[] { 0.0, 0.0 }, new double[] { 1.0, 1.0 }, 1, 0));
    }
}
