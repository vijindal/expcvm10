package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ports.EquilibriumResult;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PhaseDiagramEngine#identifyPhaseRegions}'s tie-triangle
 * construction (Sundman 2021 Calphad 75 &sect;2.4/&sect;4.2, Fig. 3(a)/11:
 * "The green triangles define 3-phase regions; their corners indicate the
 * compositions of the three phases in equilibrium") checked at the
 * FORMATTER level against a genuine 3-phase point captured from real OC.
 *
 * <p><b>OC reference</b> (real {@code oc7C} run via the WSL pty driver,
 * per {@code docs/roadmap_phase_diagrams.md}'s "Running OC calculations"
 * section): the Ag-Cu eutectic on {@code data/agcu.TDB} (the Hayes et al.
 * assessment -- Fig. 2(a) of the very paper this codebase implements),
 * bracketed this session by scanning T at fixed x(Cu)=0.32:
 * <ul>
 *   <li>T=1056.0K: LIQUID + FCC_A1 stable (2 phases) -- above the eutectic.</li>
 *   <li>T=1055.5K: FCC_A1#1 + FCC_A1_AUTO#2 stable (2 phases, no liquid)
 *       -- below the eutectic, inside the miscibility gap.</li>
 *   <li>T=1055.9K ({@code docs/oc_reference_tests/agcu_eutectic_lr_t1055.9.txt}):
 *       LIQUID and FCC_A1#1 stable, FCC_A1_AUTO#2 metastable at exactly
 *       zero amount with a driving force of only 9.43E-4 RT -- i.e.
 *       numerically AT the eutectic ZPF boundary to OC's own tolerance
 *       (OC's own footer: "There are phase(s) which would like to be
 *       stable: FCC_A1 9.4278E-04"). This is the invariant's 3-phase
 *       point per &sect;2.3.3's own definition of a node ("a positive
 *       driving force means the metastable phase is to be added to the
 *       set of stable phases") -- the compositions below are OC's own
 *       {@code X:} lines for all 3 phases at this exact point.</li>
 * </ul>
 *
 * <p>Since this codebase's own {@code EquilibriumSolverV2} (like OC's
 * own algorithm A at this exact T) converges to only the 2 STABLE phases
 * at this T (matching OC's own stable-phase classification here), this
 * test builds the {@link Node}'s {@link EquilibriumResult} directly from
 * OC's own captured 3-phase composition/amount data (all 3 phases,
 * including the ~zero-amount one) rather than re-solving -- the tie-
 * triangle's geometry only depends on {@link
 * EquilibriumResult.PhaseResult#x}, which OC's dump gives exactly, so no
 * new geometry is invented, only OC's own reported compositions.
 */
public class PhaseRegionsOcFormatterComparisonTest {

    // OC's own X: lines at T=1055.9K, x(Cu)=0.32, P=1e5
    // (agcu_eutectic_lr_t1055.9.txt), all reordered to this codebase's
    // own [AG, CU] convention -- OC itself prints components in whatever
    // order it lists them per phase (CU before AG for FCC_A1_AUTO#2).
    private static final double[] LIQUID_X = { 0.584351, 0.415649 }; // AG, CU
    private static final double[] FCC_A1_1_X = { 0.869833, 0.130167 }; // AG, CU
    private static final double[] FCC_A1_AUTO2_X = { 0.0456811, 0.954319 }; // AG, CU

    private static EquilibriumResult eutecticEquilibrium() {
        EquilibriumResult.PhaseResult liquid = new EquilibriumResult.PhaseResult(
                "LIQUID", "CEF", 0.665, LIQUID_X, LIQUID_X, 0.0, 0.0, 1.0);
        EquilibriumResult.PhaseResult fccAgRich = new EquilibriumResult.PhaseResult(
                "FCC_A1#1", "CEF", 0.335, FCC_A1_1_X, FCC_A1_1_X, 0.0, 0.0, 1.0);
        EquilibriumResult.PhaseResult fccCuRich = new EquilibriumResult.PhaseResult(
                "FCC_A1_AUTO#2", "CEF", 0.0, FCC_A1_AUTO2_X, FCC_A1_AUTO2_X, 0.0, 9.43e-4, 1.0);
        return new EquilibriumResult(1055.9, 101325.0, new double[] { -6.9657, -5.7333 },
                List.of(liquid, fccAgRich, fccCuRich), List.of(), true, 9);
    }

    @Test
    void tieTriangleVerticesMatchOcsOwnPhaseCompositionsAtTheEutectic() {
        NodeRegistry registry = new NodeRegistry();
        Node node = registry.findOrCreate(
                eutecticEquilibrium(), new double[] { 1055.9, 0.32 }, new double[] { 0.68, 0.32 });

        PhaseRegions regions = PhaseDiagramEngine.identifyPhaseRegions(registry);

        assertEquals(1, regions.tieTriangles().size(),
                "a node with 3 stable phases should produce exactly 1 tie-triangle");
        PhaseRegions.TieTriangle triangle = regions.tieTriangles().get(0);

        assertEquals(List.of("LIQUID", "FCC_A1#1", "FCC_A1_AUTO#2"), triangle.phaseNames);
        assertEquals(Set.of("LIQUID", "FCC_A1#1", "FCC_A1_AUTO#2"), regions.nodeLabels().get(node));

        // Each vertex IS the phase's own OC-reported composition --
        // Sundman 2021 §2.4's "corners indicate the compositions of the
        // three phases in equilibrium" -- verified directly against
        // OC's own X: dump, not a value this codebase computed.
        assertArrayEquals(LIQUID_X, triangle.vertices.get(0), 1e-9, "LIQUID vertex vs OC's X: line");
        assertArrayEquals(FCC_A1_1_X, triangle.vertices.get(1), 1e-9, "FCC_A1#1 vertex vs OC's X: line");
        assertArrayEquals(FCC_A1_AUTO2_X, triangle.vertices.get(2), 1e-9, "FCC_A1_AUTO#2 vertex vs OC's X: line");
    }

}
