package system.database;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that both CEF and CVM model parameters can be fetched from a
 * single, genuinely multicomponent TDB (the quaternary
 * {@code data/NbTiVZr-CVM-eName-model.TDB}) for every binary, ternary, and
 * quaternary sub-system it defines -- not just the one combination a given
 * feature happened to be developed against.
 *
 * <h2>Background</h2>
 * {@link tdb#getCvmParams} used to filter {@code G_CVM} parameters by
 * SUBSET containment (every element in a candidate parameter's
 * constituent list must appear in the query's element list) -- the same
 * filter {@link tdb.Phase#getParam} uses for ordinary {@code G}/{@code L}
 * parameters, where a subset match is correct (a lower-order binary
 * interaction legitimately contributes to a higher-order system). For
 * {@code G_CVM}, that filter was wrong: a single TDB commonly stores
 * several independent {@code G_CVM} blocks for the SAME phase, one per
 * distinct element-order query (a block per binary pair, one per ternary
 * triple, one for the full quaternary), each with its own strictly
 * positional CEC names ({@link system.model.cvm.gen.CvCfBasisGenerator}
 * assigns {@code A/B/C/D} by query order, not by any fixed physical-pair
 * identity). A subset match pulled in every qualifying block at once --
 * e.g. a ternary query also matched all three of its binary sub-blocks --
 * producing duplicate CEC names and an {@code IllegalArgumentException}
 * from {@link system.model.cvm.CecEvaluator}. {@link tdb#getCvmParams} now
 * requires an EXACT element-set match, so each query resolves to exactly
 * the one block written for it.
 *
 * <p>This test also exercises the CEF side ({@code LAVES_C15}), which
 * already used the (correct-for-CEF) subset-based {@link
 * tdb.Phase#getParam}, to guard against a future regression narrowing
 * that path instead.
 */
class TdbMulticomponentFetchTest {

    private static final String TDB_PATH = "data/NbTiVZr-CVM-eName-model.TDB";
    private static final double T = 1273.0;
    private static final double P = 101325.0;

    private static Stream<List<String>> subsystems() {
        return Stream.of(
                Arrays.asList("NB", "V"),
                Arrays.asList("NB", "ZR"),
                Arrays.asList("V", "ZR"),
                Arrays.asList("NB", "TI"),
                Arrays.asList("TI", "V"),
                Arrays.asList("TI", "ZR"),
                Arrays.asList("NB", "V", "ZR"),
                Arrays.asList("NB", "TI", "V"),
                Arrays.asList("NB", "TI", "ZR"),
                Arrays.asList("TI", "V", "ZR"),
                Arrays.asList("NB", "TI", "V", "ZR"));
    }

    @ParameterizedTest
    @MethodSource("subsystems")
    void cefLavesC15FetchesForEverySubsystem(List<String> elements) throws Exception {
        TdbParser parser = new TdbParser();
        parser.load(TDB_PATH);

        List<GibbsEnergyModel> models = parser.buildPhaseModels(elements, Arrays.asList("LAVES_C15"));

        assertEquals(1, models.size(),
                "Expected exactly one LAVES_C15 model for elements " + elements);

        GibbsEnergyModel model = models.get(0);
        assertEquals(elements.size(), model.numComponents());

        double[] x = new double[elements.size()];
        Arrays.fill(x, 1.0 / x.length);
        double[] y0 = model.getInitialInternalVars(x);

        assertTrue(model.isValid(y0), "Initial constitution must be valid for " + elements);
        assertTrue(Double.isFinite(model.G(T, P, y0)), "G must be finite for " + elements);
    }

    @ParameterizedTest
    @MethodSource("subsystems")
    void cvmBccA2FetchesForEverySubsystem(List<String> elements) throws Exception {
        tdb database = new tdb(TDB_PATH);

        GibbsEnergyModel model = PhaseModelFactory.buildCvmFromTdb(database, elements, "BCC_A2");

        assertEquals(elements.size(), model.numComponents());
        assertEquals("CVM", model.modelType());

        // getInitialInternalVars()/evalRandApprox() is only implemented
        // for nComp=2 (a separate, pre-existing limitation of
        // CvmPhaseData -- not what this test targets), so a valid
        // constitution is built directly here for every component count:
        // internal correlation functions u=0, composition = equimolar.
        int ncf = model.numTotalParams() - elements.size();
        double[] y = new double[model.numTotalParams()];
        for (int i = ncf; i < y.length; i++) {
            y[i] = 1.0 / elements.size();
        }

        assertTrue(model.isValid(y), "Equimolar constitution must be valid for " + elements);
        assertTrue(Double.isFinite(model.G(T, P, y)), "G must be finite for " + elements);
    }
}
