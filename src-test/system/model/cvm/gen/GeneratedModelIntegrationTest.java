package system.model.cvm.gen;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import system.model.unary.ElementGibbs;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests: verify that generated CVM models bridge correctly
 * to the thermodynamic/model layer infrastructure.
 *
 * <p>These tests focus on model construction and geometry validation
 * rather than full equilibrium solving.</p>
 */
@Tag("exploratory")
class GeneratedModelIntegrationTest {

    @Test
    void generatedBinaryBccA2CanBeConvertedToPhaseData() {
        // Build generated geometry
        GeneratedCvmGeometry geo = CvmGeometryGenerator.generateBccA2Binary(null);

        // Verify it has the expected dimensions
        assertEquals(2, geo.numComponents);
        assertEquals("BCC_A2", geo.structure);
        assertEquals(4, geo.ncf, "Binary BCC_A2 T-model should have 4 non-point CFs");
        assertEquals(6, geo.tcf, "Total CFs = 4 non-point + 2 point (for K=2)");

        // Convert to CvmPhaseData (the bridge to CvmGibbsModel)
        var phaseData = GeneratedCvmPhaseDataAdapter.toCvmPhaseData(geo);

        // Verify the adapter worked
        assertEquals(2, phaseData.nComp);
        assertEquals(4, phaseData.ncf);
        assertEquals(6, phaseData.uListLen, "uListLen = ncf + nComp");
    }

    @Test
    void generatedTernaryBccA2HasCorrectDimensions() {
        // Build ternary geometry (K=3)
        GeneratedCvmGeometry geo = CvmGeometryGenerator.generateBccA2("A-B-C", 3, null);

        // Ternary T-model should have 18 non-point CFs
        assertEquals(18, geo.ncf, "Ternary BCC_A2 should have 18 non-point CFs");
        assertEquals(21, geo.tcf, "tcf = ncf + K = 18 + 3");
    }

    @Test
    void generatedQuaternaryModelHasCorrectDimensions() {
        // Build quaternary geometry (K=4)
        GeneratedCvmGeometry geo = CvmGeometryGenerator.generateBccA2(
                "A-B-C-D", 4, null);

        // Quaternary T-model should have many CFs
        assertTrue(geo.ncf > 40, "Quaternary BCC_A2 should have >40 non-point CFs");
        assertEquals(geo.ncf + 4, geo.tcf, "tcf should be ncf + numComponents");
    }

    @Test
    void generatedModelFactoryCanBuildModel() {
        // Build generated geometry
        GeneratedCvmGeometry geo = CvmGeometryGenerator.generateBccA2Binary(null);

        // Create minimal CEC term list matching the non-point CFs
        var cecTerms = java.util.List.of(
                new system.model.cvm.CecTerm("v4AB", 0.0, 0.0),
                new system.model.cvm.CecTerm("v3AB", 0.0, 0.0),
                new system.model.cvm.CecTerm("v2AB2", 0.0, 0.0),
                new system.model.cvm.CecTerm("v2AB1", 0.0, 0.0)
        );

        // Create minimal ElementGibbs implementations
        ElementGibbs[] ghser = new ElementGibbs[] {
                SimpleElementGibbs.create("A", 0.0, 0.0),
                SimpleElementGibbs.create("B", 0.0, 0.0)
        };

        // Build model via factory
        var model = GeneratedCvmModelFactory.buildCvmGibbsModel(
                geo, cecTerms, ghser, java.util.List.of("A", "B"));

        // Verify basic properties
        assertEquals("BCC_A2", model.phaseName());
        assertEquals("CVM", model.modelType());
        assertEquals(2, model.numComponents());
        assertEquals(1.0, model.nfu());
    }

    // =========================================================================
    // Minimal ElementGibbs implementation for testing
    // =========================================================================

    private static final class SimpleElementGibbs implements ElementGibbs {
        private final String symbol;
        private final double h0;
        private final double s0;

        SimpleElementGibbs(String symbol, double h0, double s0) {
            this.symbol = symbol;
            this.h0 = h0;
            this.s0 = s0;
        }

        static ElementGibbs create(String symbol, double h0, double s0) {
            return new SimpleElementGibbs(symbol, h0, s0);
        }

        @Override
        public String elementSymbol() {
            return symbol;
        }

        @Override
        public Set<String> availablePhases() {
            return Set.of("BCC");
        }

        @Override
        public double gibbs(String phase, double T) {
            return h0 - s0 * T;
        }

        @Override
        public double ghser(double T) {
            return gibbs("BCC", T);
        }
    }
}
