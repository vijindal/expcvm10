package system.model.cvm.gen;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test B: CVCF basis. Verifies CF names/order and dimensions for the
 * generated BCC_A2 binary basis.
 */
@Tag("exploratory")
class CvCfBasisGenerationTest {

    @Test
    void cfNamesAndDimensionsMatchRegistration() {
        GeneratedCvmGeometry geo = GeneratedCvmGeometry.buildBccA2Binary(null);

        List<String> expectedNames = List.of("v4AB", "v3AB", "v22AB", "v21AB", "xA", "xB");
        assertEquals(expectedNames, geo.basis.cfNames);
        assertEquals(6, geo.basis.totalCfs());
        assertEquals(4, geo.basis.numNonPointCfs, "4 non-point CVCF variables carry ECIs");
        assertEquals(geo.ncf, geo.basis.numNonPointCfs);
        assertEquals(geo.tcf, geo.basis.totalCfs());
    }

    @Test
    void tAndMAreMutualInverses() {
        GeneratedCvmGeometry geo = GeneratedCvmGeometry.buildBccA2Binary(null);
        double[][] T = geo.basis.T;
        double[][] M = geo.basis.Tinv;
        int n = T.length;
        double[][] product = system.model.cvm.gen.LinearAlgebra.multiply(T, M);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertEquals(i == j ? 1.0 : 0.0, product[i][j], 1e-9,
                        "T * M should be the identity at (" + i + "," + j + ")");
            }
        }
    }
}
