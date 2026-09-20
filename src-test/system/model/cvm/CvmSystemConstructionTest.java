package system.model.cvm;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.cef.CefGibbs;
import system.model.unary.ElementGibbs;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step 4: proves a {@link CvmGibbsModel} is constructible via
 * {@link CvmPhaseSpec}/{@link PhaseModelFactory#buildCvm} and usable as a
 * plain {@link GibbsEnergyModel} member of a {@link ThermodynamicSystem},
 * alongside TDB-built {@link CefGibbs} phases, with no unchecked casts.
 */
class CvmSystemConstructionTest {

    private static CvmPhaseSpec bccSpec() {
        CvmPhaseData data = CvmBinaryBccFixture.build();
        List<CecTerm> cecTerms = List.of(
                new CecTerm("v4AB", 100.0, 0.0),
                new CecTerm("v3AB", -50.0, 0.01),
                new CecTerm("v22AB", 200.0, -0.02),
                new CecTerm("v21AB", -300.0, 0.05));
        ElementGibbs ghserA = new FakeElementGibbs("A", 1000.0, -5.0);
        ElementGibbs ghserB = new FakeElementGibbs("B", -2000.0, 3.0);
        return new CvmPhaseSpec(data, cecTerms,
                new ElementGibbs[]{ghserA, ghserB}, List.of("A", "B"));
    }

    // ══════════════════════════════════════════════════════════════════
    // Test 7 -- single-CVM-phase ThermodynamicSystem
    // ══════════════════════════════════════════════════════════════════

    @Test
    void thermodynamicSystemHoldsCvmPhaseAsGibbsEnergyModel() {
        GibbsEnergyModel cvmModel = PhaseModelFactory.buildCvm(bccSpec());

        ThermodynamicSystem system = ThermodynamicSystem.of(
                List.of("A", "B"), List.of(cvmModel));

        assertEquals(1, system.phaseModels().size());
        GibbsEnergyModel model = system.phaseModels().get(0);

        assertTrue(model instanceof CvmGibbsModel);
        assertEquals("CVM", model.modelType());
        assertEquals("BCC_A2", model.phaseName());
        assertEquals(2, model.numComponents());
        assertEquals(4 + 2, model.numSiteVars());

        double T = 1000.0, P = 101325.0;
        double[] x = {0.6, 0.4};
        double[] y = model.getInitialInternalVars(x);

        double G = model.G(T, P, y);
        assertTrue(Double.isFinite(G), "G must be finite: " + G);

        double[] composition = model.compositionFromInternal(y);
        assertArrayEquals(x, composition, 1e-12);

        double[] moles = model.moles(y);
        assertArrayEquals(x, moles, 1e-12);

        System.out.println("Step 4 CVM construction/evaluation check: G(T=" + T + "K, x="
                + Arrays.toString(x) + ") = " + G + " J/mol");
    }

    // ══════════════════════════════════════════════════════════════════
    // Test 8 -- mixed CEF + CVM phase list
    // ══════════════════════════════════════════════════════════════════

    @Test
    void mixedCefAndCvmPhaseListRequiresNoUncheckedCast() throws Exception {
        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");

        List<String> elements = Arrays.asList("V", "ZR");
        List<GibbsEnergyModel> cefModels =
                parser.buildPhaseModels(elements, Arrays.asList("LIQUID", "BCC_A2"));
        assertEquals(2, cefModels.size());

        GibbsEnergyModel cef1 = cefModels.get(0);
        GibbsEnergyModel cef2 = cefModels.get(1);
        GibbsEnergyModel cvm = PhaseModelFactory.buildCvm(bccSpec());

        List<GibbsEnergyModel> mixed = List.of(cef1, cvm, cef2);

        ThermodynamicSystem system = ThermodynamicSystem.of(List.of("A", "B"), mixed);
        assertEquals(3, system.phaseModels().size());

        assertTrue(system.phaseModels().get(0) instanceof CefGibbs);
        assertTrue(system.phaseModels().get(1) instanceof CvmGibbsModel);
        assertTrue(system.phaseModels().get(2) instanceof CefGibbs);

        // CEF phase evaluation still works.
        double T = 900.0, P = 101325.0;
        double[] yCef = cef1.getInitialInternalVars(new double[]{0.55, 0.45});
        assertTrue(Double.isFinite(cef1.G(T, P, yCef)));

        // CVM phase evaluation still works.
        double[] yCvm = cvm.getInitialInternalVars(new double[]{0.6, 0.4});
        assertTrue(Double.isFinite(cvm.G(T, P, yCvm)));
    }
}
