package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;

import java.io.IOException;
import java.util.List;

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
    void mergeDedupNetworkIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.mergeDedupNetwork(new NodeRegistry()));
    }

    @Test
    void identifyPhaseRegionsIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.identifyPhaseRegions(new NodeRegistry()));
    }

    @Test
    void classifyPlotIsNotYetImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> PhaseDiagramEngine.classifyPlot(new NodeRegistry(), PhaseDiagramEngine.PlotType.BINARY_T_X));
    }
}
