package system.model.cvm.gen;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test C: C-matrix. Verifies {@code cmat}, {@code lcv}, {@code wcv}
 * dimensions and the configuration-count conservation invariant (each
 * cluster type's CV rows partition all K^size atomic configurations).
 */
@Tag("exploratory")
class CMatrixGenerationTest {

    @Test
    void cmatLcvWcvDimensionsMatchFixture() {
        GeneratedCvmGeometry geo = GeneratedCvmGeometry.buildBccA2Binary(null);

        int[] expectedLcv = {6, 6, 3, 3, 2};
        for (int t = 0; t < geo.tcdis; t++) {
            assertEquals(1, geo.lcv[t].length, "one ordered-phase group per HSP type at t=" + t);
            assertEquals(expectedLcv[t], geo.lcv[t][0], "lcv at t=" + t);
            assertEquals(expectedLcv[t], geo.cmat.get(t).get(0).length, "cmat row count at t=" + t);
            for (double[] row : geo.cmat.get(t).get(0)) {
                assertEquals(geo.tcf, row.length, "cmat column count at t=" + t + " must equal tcf");
            }
        }
    }

    @Test
    void wcvConservesConfigurationCount() {
        // geo.validate() already checks this per (t,j); this test asserts
        // the same physically-meaningful property directly and independently.
        GeneratedCvmGeometry geo = GeneratedCvmGeometry.buildBccA2Binary(null);
        int K = geo.numComponents;

        for (int t = 0; t < geo.tcdis; t++) {
            int size = geo.pipelineResult.getDisClusData().getClusCoordList().get(t).getAllSites().size();
            long expected = (long) Math.pow(K, size);
            for (int j = 0; j < geo.lc[t]; j++) {
                long sum = 0;
                for (int w : geo.wcv.get(t).get(j)) sum += w;
                assertEquals(expected, sum, "sum(wcv) at t=" + t + ", j=" + j + " must equal K^size=" + expected);
            }
        }
    }
}
