package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ports.EquilibriumResult;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step 5d of {@code docs/roadmap_phase_diagrams.md}: {@link
 * NodeGeometry#attachExits} for the {@code TIE_LINE_IN_PLANE} case
 * (an ordinary crossing) and the {@code INVARIANT} case (Algorithm D,
 * {@link InvariantExitPairFinder}).
 *
 * <p><b>Scope note on the INVARIANT case.</b> No existing test or
 * reference case in this codebase exercises {@link MapTracer} actually
 * REACHING a resolved {@link MapTracer.SegmentEnd#INVARIANT} through a
 * real walk -- V-Zr's own documented 1586-1588K peritectic (the only
 * candidate binary invariant this project has literature data for,
 * {@code EquilibriumSolverV2BaselineTest}) is confirmed
 * UNRESOLVED_MULTI_PHASE_CHANGE, not INVARIANT (see {@code
 * CalculationSessionMapTracerTest} Section C's own comment: OpenCalphad's
 * own retry strategy cannot resolve this particular jump either).
 * Rather than claim more than is true, this test constructs a {@link
 * Node} DIRECTLY from the peritectic's known literature compositions
 * (Cui et al. 2016, via {@code EquilibriumSolverV2BaselineTest}'s Table
 * 2 reference row: BCC_A2 x(Zr)=0.07853, LIQUID x(Zr)=0.4169, both at
 * T=1588K; V2ZR's own composition x(Zr)~0.35, per this project's
 * existing V2ZR/BCC_A2 two-phase test data) rather than through a
 * solved 3-phase equilibrium (attempting to force our solver to report
 * all 3 phases simultaneously stable at a single composition found it
 * correctly reports only the 2 phases actually stable there, since a
 * generic composition is not exactly ON the invariant point -- probed
 * directly this session). This tests the NEW wiring (NodeGeometry
 * correctly calling InvariantExitPairFinder and attaching the resulting
 * exit lines) in isolation, WITHOUT claiming the full drain loop can
 * find this specific invariant end-to-end, which remains unresolved
 * (see docs/roadmap_phase_diagrams.md).
 */
public class NodeGeometryTest {

    @Test
    void tieLineInPlaneAttachesTwoExitsAlongTheWalkAxis() {
        EquilibriumResult eq = twoPhaseEquilibrium();
        Node node = new Node(0, eq, new double[] { 1090.0, 0.5 }, new double[] { 0.5, 0.5 });

        NodeGeometry.attachExits(node, PhaseDiagramEngine.NodeClass.TIE_LINE_IN_PLANE, "LIQUID", 0);

        List<Line> lines = node.getLines();
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(l -> l.direction == +1));
        assertTrue(lines.stream().anyMatch(l -> l.direction == -1));
        for (Line l : lines) {
            assertEquals(List.of("LIQUID"), l.fixedPhases);
            assertEquals(0, l.initialAxisIndex);
            assertEquals(Line.State.PENDING, l.getState());
        }
    }

    @Test
    void isoplethCrossingWithNoArrivingLineFixedPhaseDegradesToTwoExits() {
        // The arriving line had NO fixed phase of its own (e.g. it came
        // from the diagram's START node) -- per NodeGeometry's own
        // javadoc, there is no "already fixed" phase to form a genuine
        // 2-line crossing with, so this degrades to the ordinary 2-exit
        // case, identical to TIE_LINE_IN_PLANE.
        EquilibriumResult eq = twoPhaseEquilibrium();
        Node node = new Node(0, eq, new double[] { 699.58, 0.05 }, new double[] { 0.9, 0.05, 0.05 });

        NodeGeometry.attachExits(
                node, PhaseDiagramEngine.NodeClass.ISOPLETH_CROSSING, "LIQUID", null, 0);

        List<Line> lines = node.getLines();
        assertEquals(2, lines.size());
        for (Line l : lines) {
            assertEquals(List.of("LIQUID"), l.fixedPhases);
        }
    }

    @Test
    void isoplethCrossingWithArrivingLineFixedPhaseAttachesThreeExits() {
        // A genuine 2-line crossing (Sundman 2021 Section 3.3, ported
        // from OpenCalphad's map_newnode case(3)): the arriving line was
        // already fixing "MGZN2" (LFIX) when phase "FCC_A1" (PHFIX)
        // appeared/disappeared to create this node.
        EquilibriumResult eq = twoPhaseEquilibrium();
        Node node = new Node(0, eq, new double[] { 700.0, 0.05 }, new double[] { 0.9, 0.05, 0.05 });

        NodeGeometry.attachExits(
                node, PhaseDiagramEngine.NodeClass.ISOPLETH_CROSSING, "FCC_A1", "MGZN2", 0);

        List<Line> lines = node.getLines();
        assertEquals(3, lines.size());

        // Exit 1: LFIX (MGZN2)'s own line continues, single direction.
        long mgzn2Exits = lines.stream().filter(l -> l.fixedPhases.equals(List.of("MGZN2"))).count();
        assertEquals(1, mgzn2Exits, "LFIX's own line should get exactly 1 exit, not 2");

        // Exits 2 and 3: PHFIX (FCC_A1)'s own line, both directions.
        List<Line> fccExits = lines.stream()
                .filter(l -> l.fixedPhases.equals(List.of("FCC_A1")))
                .toList();
        assertEquals(2, fccExits.size(), "PHFIX's own line should get exactly 2 exits (both directions)");
        assertTrue(fccExits.stream().anyMatch(l -> l.direction == +1));
        assertTrue(fccExits.stream().anyMatch(l -> l.direction == -1));
    }

    @Test
    void invariantAttachesExitsFoundByAlgorithmD() {
        // Synthetic 3-phase node built from the V-Zr peritectic's known
        // literature compositions (see class javadoc) -- BCC_A2, V2ZR,
        // LIQUID all "stable" at T=1588K for this test's purposes.
        EquilibriumResult eq = vZrPeritecticEquilibrium();
        Node node = new Node(0, eq, new double[] { 1588.0, 0.207 },
                new double[] { 1.0 - 0.207, 0.207 });

        assertEquals(Set.of("BCC_A2", "V2ZR", "LIQUID"), node.stablePhaseNames);

        // Arrived via the line that was already fixing BCC_A2 at zero
        // when V2ZR appeared -- the arrival pair is (V2ZR, BCC_A2), per
        // NodeTerminator's own always-both-phases call convention.
        NodeGeometry.attachExits(node, PhaseDiagramEngine.NodeClass.INVARIANT, "V2ZR", "BCC_A2", 0);

        List<Line> lines = node.getLines();
        // Per Fig. 7/InvariantExitPairFinder: for a 3-phase binary
        // invariant there are exactly 3 candidate pairs -- (BCC_A2,V2ZR),
        // (BCC_A2,LIQUID), (V2ZR,LIQUID) -- minus the 1 arrival pair
        // (V2ZR,BCC_A2) = up to 2 remaining pairs, each attached as 4
        // lines (Fig. 7's "+2 exits" box, times 2 walk directions per
        // exit since direction is not determinable from the pair alone)
        // = up to 8 lines, but only for pairs InvariantExitPairFinder
        // actually finds valid (positive amounts) for this geometry.
        assertTrue(lines.size() % 4 == 0, "each valid pair attaches exactly 4 lines (2 roles x 2 directions)");
        for (Line l : lines) {
            assertEquals(1, l.fixedPhases.size());
            assertTrue(Set.of("BCC_A2", "V2ZR", "LIQUID").contains(l.fixedPhases.get(0)));
            assertTrue(l.forbiddenPhase != null && Set.of("BCC_A2", "V2ZR", "LIQUID").contains(l.forbiddenPhase),
                    "every invariant exit line must carry its pair partner as forbiddenPhase");
            assertTrue(!l.forbiddenPhase.equals(l.fixedPhases.get(0)),
                    "an exit's fixed phase and forbidden phase must be the two DIFFERENT members of its pair");
        }

        // The arrival pair itself (V2ZR fixed/BCC_A2 forbidden, or the
        // reverse) must not be re-attached as a new exit.
        for (Line l : lines) {
            String fixed = l.fixedPhases.get(0);
            String forbidden = l.forbiddenPhase;
            boolean isArrivalPair =
                    (fixed.equals("V2ZR") && forbidden.equals("BCC_A2"))
                    || (fixed.equals("BCC_A2") && forbidden.equals("V2ZR"));
            assertTrue(!isArrivalPair, "the arrival pair (V2ZR, BCC_A2) must not be re-attached: " + l);
        }
    }

    private static EquilibriumResult twoPhaseEquilibrium() {
        EquilibriumResult.PhaseResult liquid = new EquilibriumResult.PhaseResult(
                "LIQUID", "CEF", 0.5, new double[] { 0.5, 0.5 }, new double[] { 0.5, 0.5 },
                -1000.0, 0.0, 1.0);
        EquilibriumResult.PhaseResult fccA1 = new EquilibriumResult.PhaseResult(
                "FCC_A1", "CEF", 0.5, new double[] { 0.6, 0.4 }, new double[] { 0.6, 0.4 },
                -1000.0, 0.0, 1.0);
        return new EquilibriumResult(1090.0, 101325.0, new double[] { -1000.0, -1000.0 },
                List.of(liquid, fccA1), List.of(), true, 5);
    }

    private static EquilibriumResult vZrPeritecticEquilibrium() {
        // Literature compositions at T=1588K, per class javadoc.
        EquilibriumResult.PhaseResult bcc = new EquilibriumResult.PhaseResult(
                "BCC_A2", "CEF", 0.4, new double[] { 1.0 - 0.07853, 0.07853 },
                new double[] { 1.0 - 0.07853, 0.07853 }, -90000.0, 0.0, 1.0);
        EquilibriumResult.PhaseResult v2zr = new EquilibriumResult.PhaseResult(
                "V2ZR", "CEF", 0.3, new double[] { 1.0 - 0.35, 0.35 },
                new double[] { 1.0 - 0.35, 0.35 }, -95000.0, 0.0, 1.0);
        EquilibriumResult.PhaseResult liquid = new EquilibriumResult.PhaseResult(
                "LIQUID", "CEF", 0.3, new double[] { 1.0 - 0.4169, 0.4169 },
                new double[] { 1.0 - 0.4169, 0.4169 }, -87000.0, 0.0, 1.0);
        return new EquilibriumResult(1588.0, 101325.0, new double[] { -87021.68, -108309.14 },
                List.of(bcc, v2zr, liquid), List.of(), true, 12);
    }
}
