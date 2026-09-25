package system.database;

import org.junit.jupiter.api.Test;

import system.model.GibbsEnergyModel;
import system.model.PhaseModelKind;
import system.model.cef.CefGibbs;
import system.model.cvm.CvmGibbsModel;
import system.ports.DatabasePort;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Regression test for {@link TdbParser#buildPhaseModels}'s
 * {@link PhaseModelKind} dispatch: explicit {@code CVM} must reach
 * {@link system.model.PhaseModelFactory#buildCvmFromTdb} (previously it
 * always built {@link CefGibbs}, ignoring {@code kind}), while explicit
 * {@code CEF} must keep building {@link CefGibbs}.
 */
class TdbParserModelDispatchTest {

    @Test
    void explicitCvmKindBuildsCvmGibbsModel() throws IOException {
        DatabasePort parser = new TdbParser();
        parser.load("data/VZR-re2-CVM-eName-model.TDB");
        DatabasePort system = parser.extractSystem(new String[]{"V", "ZR"});

        List<GibbsEnergyModel> models = system.buildPhaseModels(
                Arrays.asList("V", "ZR"), Arrays.asList("BCC_A2"), PhaseModelKind.CVM);

        assertEquals(1, models.size());
        assertInstanceOf(CvmGibbsModel.class, models.get(0));
    }

    @Test
    void explicitCefKindStillBuildsCefGibbs() throws IOException {
        DatabasePort parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");
        DatabasePort system = parser.extractSystem(new String[]{"V", "ZR"});

        List<GibbsEnergyModel> models = system.buildPhaseModels(
                Arrays.asList("V", "ZR"), Arrays.asList("BCC_A2"), PhaseModelKind.CEF);

        assertEquals(1, models.size());
        assertInstanceOf(CefGibbs.class, models.get(0));
    }
}
