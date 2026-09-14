package calc.diagram;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link MapTracer#findInitialBoundary} -- Step 3a of {@code
 * docs/phase_diagram_engine_flowchart.md}'s map-branch initialization:
 * the single-axis search from a possibly SINGLE-PHASE starting
 * equilibrium that locates the true first/START node, which {@link
 * MapDiagramTracer} (as of Step 2) does not yet do -- it currently
 * assumes its caller already supplies a multi-phase starting point.
 * This test exercises the search in isolation, not yet wired into the
 * drain loop (that is Step 3b).
 *
 * <p><b>Reference values.</b> {@code data/crfe_oc_reference.TDB} (OC's
 * own bundled {@code examples/TQ4lib/F90/crfe/crfe.TDB}, already used
 * in {@code NodeMatchingTest}). SIGMA is excluded from the candidate
 * phases here: including it makes {@code EquilibriumSolverV2} throw
 * ("Matrix is singular") at these compositions (a pre-existing solver/
 * candidate-set issue, out of scope for this test).
 *
 * <p><b>Finding a genuine single-phase -&gt; two-phase crossing.</b> An
 * earlier draft of this test used x(Cr)=0.05 near T=1130K, where direct
 * solver scanning found only a direct BCC_A2-to-FCC_A1 swap with no
 * intermediate two-phase step -- {@link MapTracer#findChangedPhase}
 * correctly reports that as {@code UNRESOLVED_MULTI_PHASE_CHANGE} (both
 * phases differ at once), which is NOT the case {@code
 * findInitialBoundary} is meant to find (the flowchart's search step
 * looks for where a NEW phase first appears alongside the existing one,
 * i.e. an ordinary single-phase-fix Algorithm C2 crossing). Scanning
 * {@code EquilibriumSolverV2} directly at x(Cr)=0.3 instead found
 * exactly that case: BCC_A2 alone at T=1801K, BCC_A2+LIQUID at
 * T=1803-1809K, LIQUID alone from T=1811K -- narrowed to the 1801-1803K
 * bracket this session. OC's own {@code tqex1} (via the pty driver, see
 * {@code docs/oc_reference_tests/run_pty.py}) confirms BCC_A2 stable at
 * T=1750K, x(Cr)=0.3 (mu(Cr)=-102638.2, mu(Fe)=-107510.1 J/mol, SER
 * reference) -- a safely single-phase point in the same field the
 * search starts from, following Step 2's precedent of using OC to
 * confirm topology/qualitative starting conditions and this project's
 * own solver for the exact crossing bracket.
 */
public class MapTracerInitialBoundaryTest {

    private static final String TDB = "data/crfe_oc_reference.TDB";
    private static final List<String> ELEMENTS = List.of("CR", "FE");
    private static final List<String> PHASES = List.of("LIQUID", "BCC_A2", "FCC_A1");

    private static List<GibbsEnergyModel> candidates() throws IOException {
        return ThermodynamicSystem.build(TDB, ELEMENTS, PHASES).phaseModels();
    }

    @Test
    void findsTheBccToBccPlusLiquidCrossingFromASinglePhaseStart() throws IOException {
        // Start deep in single-phase BCC_A2 (OC-confirmed at T=1750K) and
        // search T upward with a 2K step, matching the window this
        // session's direct solver scan narrowed the crossing to
        // (1801 BCC_A2 -> 1803 BCC_A2+LIQUID).
        AxisConfig searchAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1750.0, 1830.0, 2.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cr)", 0, 0.01, 0.5, 0.01);

        MapTracer.InitialBoundaryResult result = new MapTracer().findInitialBoundary(
                searchAxis, 1750.0, releaseAxis, 1750.0, 101325.0,
                new double[] { 0.3, 0.7 }, candidates());

        assertTrue(result.found, "a BCC_A2 -> BCC_A2+LIQUID crossing exists in [1750, 1830]K at x(Cr)=0.3");
        assertEquals(2, result.stableNames.size(),
                "the new stable set should be the two-phase BCC_A2+LIQUID field");
        assertTrue(result.stableNames.contains("BCC_A2"));
        assertTrue(result.stableNames.contains("LIQUID"));

        // The crossing should land within the confirmed 1801-1803K bracket.
        assertTrue(result.crossingSearchValue > 1799.0 && result.crossingSearchValue < 1805.0,
                "crossing should be located within the confirmed 1801-1803K bracket, got "
                        + result.crossingSearchValue);
    }

    @Test
    void reportsNotFoundWhenNoCrossingExistsInRange() throws IOException {
        // A window entirely inside the single-phase BCC_A2 field (per the
        // same scan: BCC_A2 stable at least through 1799K) should find
        // no crossing at all.
        AxisConfig searchAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1750.0, 1795.0, 2.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cr)", 0, 0.01, 0.5, 0.01);

        MapTracer.InitialBoundaryResult result = new MapTracer().findInitialBoundary(
                searchAxis, 1750.0, releaseAxis, 1750.0, 101325.0,
                new double[] { 0.3, 0.7 }, candidates());

        assertFalse(result.found, "no crossing should exist purely within the BCC_A2 single-phase field");
    }

    @Test
    void intermediateSearchPointsKeepReleaseAxisFixedUntilTheCrossingItself() throws IOException {
        // The defining property that distinguishes the SEARCH axis from
        // the RELEASE axis (flowchart): x(Cr) must stay at exactly its
        // starting value (0.3) at every intermediate search point, and
        // is only solved-for exactly (and so may move) at the crossing
        // itself, via Algorithm C2.
        AxisConfig searchAxis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1750.0, 1830.0, 2.0);
        AxisConfig releaseAxis = new AxisConfig("x(Cr)", 0, 0.01, 0.5, 0.01);

        MapTracer.InitialBoundaryResult result = new MapTracer().findInitialBoundary(
                searchAxis, 1750.0, releaseAxis, 1750.0, 101325.0,
                new double[] { 0.3, 0.7 }, candidates());

        assertTrue(result.found);

        // Every intermediate walked point (all but the final crossing
        // entry) must report x(Cr)=0.3 exactly for its (single) stable
        // phase.
        for (int i = 0; i < result.segment.points.size() - 1; i++) {
            double xCr = result.segment.points.get(i).getStablePhases().get(0).x[0];
            assertEquals(0.3, xCr, 1e-9,
                    "intermediate search point should keep x(Cr) fixed at the start value");
        }

        // At the crossing, x(Cr) is released and solved exactly; the
        // resolved equilibrium reports BOTH phases with genuine positive
        // amounts (confirmed directly this session: BCC_A2 amount~0.776,
        // LIQUID amount~0.224 at the located crossing) -- Algorithm C2's
        // zero-amount CONDITION is what located this exact point, but the
        // reported equilibrium is the two-phase state just past it, not
        // a phantom zero-amount snapshot.
        assertEquals(2, result.equilibrium.getStablePhases().size());
        for (var phase : result.equilibrium.getStablePhases()) {
            assertTrue(phase.amount > 0.0, phase.phaseName + " should have a positive amount");
        }
    }
}
