package application;

import org.junit.jupiter.api.Test;
import system.model.PhaseModelKind;
import system.model.cef.CefGibbs;
import system.model.cvm.CvmGibbsModel;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Regression test for {@link ApplicationLayer#setModel} building its
 * {@link system.ThermodynamicSystem} from the same already-loaded {@code
 * browseDatabase} instance rather than re-parsing the TDB file -- covers
 * both CEF and CVM dispatch through that shared-database path.
 */
class ApplicationLayerModelKindTest {

    @Test
    void setModelWithCefKindBuildsCefGibbs() throws IOException {
        ApplicationLayer session = new ApplicationLayer();
        session.setModel("data/VZR-re2.TDB", List.of("V", "ZR"), List.of("BCC_A2"),
                PhaseModelKind.CEF);

        assertInstanceOf(CefGibbs.class, session.currentSystem().phaseModels().get(0));
    }

    @Test
    void setModelWithCvmKindBuildsCvmGibbsModel() throws IOException {
        ApplicationLayer session = new ApplicationLayer();
        session.setModel("data/VZR-re2-CVM-eName-model.TDB", List.of("V", "ZR"), List.of("BCC_A2"),
                PhaseModelKind.CVM);

        assertInstanceOf(CvmGibbsModel.class, session.currentSystem().phaseModels().get(0));
    }
}
