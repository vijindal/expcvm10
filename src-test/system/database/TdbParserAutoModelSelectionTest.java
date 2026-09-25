package system.database;

import org.junit.jupiter.api.Test;

import system.model.GibbsEnergyModel;
import system.model.PhaseModelKind;
import system.model.cef.CefGibbs;
import system.ports.DatabasePort;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Regression test for {@link TdbParser#buildPhaseModels}'s new
 * {@link PhaseModelKind#AUTO} dispatch: BCC_A2/V-Zr in {@code
 * VZR-re2-CVM-eName-model.TDB} has both ordinary CEF and {@code G_CVM}
 * parameters (a genuinely dual-available phase), so AUTO must still select
 * {@link CefGibbs} -- the documented CEF-first behavior for the ambiguous
 * case, unchanged by this dispatch becoming availability-aware.
 */
class TdbParserAutoModelSelectionTest {

    @Test
    void autoPrefersCefWhenBothCefAndCvmAreAvailable() throws IOException {
        DatabasePort parser = new TdbParser();
        parser.load("data/VZR-re2-CVM-eName-model.TDB");
        DatabasePort system = parser.extractSystem(new String[]{"V", "ZR"});

        List<GibbsEnergyModel> models = system.buildPhaseModels(
                Arrays.asList("V", "ZR"), Arrays.asList("BCC_A2"), PhaseModelKind.AUTO);

        assertEquals(1, models.size());
        assertInstanceOf(CefGibbs.class, models.get(0));
    }
}
