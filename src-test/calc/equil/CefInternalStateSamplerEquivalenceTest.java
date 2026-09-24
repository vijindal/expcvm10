package calc.equil;

import org.junit.jupiter.api.Test;

import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;
import system.model.cef.CefInternalStateSampler;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4D: behavioral-equivalence test between {@link
 * GridMinimizer#sampleSiteFractions(GibbsEnergyModel)} and {@link
 * CefInternalStateSampler#sample(int, int)}, required BEFORE {@code
 * EquilibriumSolverV2.candidateSampledGrid()} is rewired to use the latter.
 *
 * <p>Fixture: SIGMA_SGTE from {@code data/AlCoCrNi-volume.TDB} (Al,Co,Cr,Ni)
 * -- a genuine 3-sublattice CEF phase (constitution {@code (AL,CO,NI):
 * (AL,CO,CR,NI):CR}, i.e. 3x4x1 = 12 endmember combinations), deliberately
 * chosen over the 2-sublattice V2ZR fixture used elsewhere so this test
 * exercises multi-sublattice Cartesian-product endmember generation, real
 * {@code offsets()} (non-zero, non-trivial spacing), and per-sublattice
 * block normalization, not just a single non-trivial sublattice.
 */
class CefInternalStateSamplerEquivalenceTest {

    private static final String TDB = "data/AlCoCrNi-volume.TDB";

    private static CefGibbs loadSigmaSgte() throws Exception {
        TdbParser parser = new TdbParser();
        parser.load(TDB);
        List<GibbsEnergyModel> models = parser.buildPhaseModels(
                Arrays.asList("AL", "CO", "CR", "NI"),
                Arrays.asList("SIGMA_SGTE"));
        assertEquals(1, models.size(), "Expected exactly one SIGMA_SGTE candidate.");
        return (CefGibbs) models.get(0);
    }

    private static double[][] sampleSiteFractionsViaGridMinimizer(
            GibbsEnergyModel model) throws Exception {
        var method = GridMinimizer.class.getDeclaredMethod(
                "sampleSiteFractions", GibbsEnergyModel.class);
        method.setAccessible(true);
        return (double[][]) method.invoke(new GridMinimizer(), model);
    }

    @Test
    void sigmaSgteFixtureIsGenuinelyMultiSublattice() throws Exception {
        CefGibbs model = loadSigmaSgte();

        assertEquals(3, model.numSublattices(),
                "SIGMA_SGTE must be a 3-sublattice fixture for this test to be meaningful.");
        assertArrayEquals(new int[]{3, 4, 1}, model.constituentsPerSublattice());
        assertArrayEquals(new int[]{0, 3, 7}, model.offsets());
        assertEquals(8, model.numSiteVars());
    }

    // ------------------------------------------------------------------
    // The core equivalence check: same model, same effective density/
    // edge-point configuration (PDENS=2000 for both), compared point count,
    // per-point variable count, ordering, and exact numerical values.
    // ------------------------------------------------------------------
    @Test
    void cefInternalStateSamplerReproducesGridMinimizerOutputExactly() throws Exception {
        CefGibbs model = loadSigmaSgte();

        int ns = model.numSublattices();
        int[] constituentsPerSublattice = model.constituentsPerSublattice();
        int[] offsets = model.offsets();
        int nip = model.numSiteVars();

        double[][] fromGridMinimizer = sampleSiteFractionsViaGridMinimizer(model);

        int density = GridMinimizer.getPDENS();
        double[][] fromSampler =
                new CefInternalStateSampler(ns, constituentsPerSublattice, offsets, nip)
                        .sample(density, density);

        assertEquals(fromGridMinimizer.length, fromSampler.length,
                "Same number of sampled points.");
        assertTrue(fromGridMinimizer.length > 0, "Sanity: fixture must actually produce points.");

        for (int i = 0; i < fromGridMinimizer.length; i++) {
            assertEquals(fromGridMinimizer[i].length, fromSampler[i].length,
                    "Point " + i + ": same number of site-fraction variables.");
            assertArrayEquals(fromGridMinimizer[i], fromSampler[i], 0.0,
                    "Point " + i + " must be bit-identical between "
                    + "GridMinimizer.sampleSiteFractions() and "
                    + "CefInternalStateSampler.sample() -- same ordering, same values.");
        }
    }

    // ------------------------------------------------------------------
    // Cross-check the expected endmember count independently (3*4*1=12)
    // so a future change to the fixture or the Cartesian-product logic
    // cannot silently pass the loop above with zero points compared.
    // ------------------------------------------------------------------
    @Test
    void expectedEndmemberCountIsCartesianProductOfSublatticeSizes() throws Exception {
        CefGibbs model = loadSigmaSgte();
        double[][] samples = sampleSiteFractionsViaGridMinimizer(model);

        int expectedEndmembers = 3 * 4 * 1;
        int edgePairs = expectedEndmembers * (expectedEndmembers - 1) / 2;
        int expectedEdgePoints = edgePairs * GridMinimizer.getPDENS();

        // nip(8) > ns(3), so interior Halton sampling also contributes.
        int expectedMinimum = expectedEndmembers + expectedEdgePoints;
        assertTrue(samples.length >= expectedMinimum,
                "Expected at least " + expectedMinimum + " endmember+edge points, got "
                + samples.length);
    }

    // ------------------------------------------------------------------
    // Phase 4D regression: the REAL production method
    // EquilibriumSolverV2.candidateSampledGrid() (now backed by
    // CefInternalStateSampler) must still produce exactly the same grid
    // as the old GridMinimizer.sampleSiteFractions() path it replaced --
    // exercised via reflection on the actual private method, not a
    // second implementation of the sampling algorithm in this test.
    // ------------------------------------------------------------------
    @Test
    void candidateSampledGridProductionMethodMatchesOldGridMinimizerOutput() throws Exception {
        CefGibbs model = loadSigmaSgte();

        double[][] fromGridMinimizer = sampleSiteFractionsViaGridMinimizer(model);

        var method = EquilibriumSolverV2.class.getDeclaredMethod(
                "candidateSampledGrid", int.class, CefGibbs.class);
        method.setAccessible(true);
        double[][] fromProduction =
                (double[][]) method.invoke(new EquilibriumSolverV2(), 0, model);

        assertEquals(fromGridMinimizer.length, fromProduction.length,
                "candidateSampledGrid() must produce the same point count as the "
                + "old GridMinimizer.sampleSiteFractions() path it replaced.");
        for (int i = 0; i < fromGridMinimizer.length; i++) {
            assertArrayEquals(fromGridMinimizer[i], fromProduction[i], 0.0,
                    "candidateSampledGrid() point " + i + " must be bit-identical to the "
                    + "old GridMinimizer-backed output.");
        }
    }
}
