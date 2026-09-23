package system.model;

import org.junit.jupiter.api.Test;
import system.database.tdb;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PhaseModelAvailability} model discovery.
 *
 * Uses existing databases:
 * - data/VZR-re2.TDB (CEF only)
 * - data/VZR-re2-CVM-eName-model.TDB (CVM only)
 */
class PhaseModelAvailabilityTest {

    /**
     * Test 1: CEF-only database.
     *
     * Verifies that the standard CEF database reports CEF available
     * and CVM unavailable for V-Zr BCC_A2.
     */
    @Test
    void cefOnlyDatabase() throws IOException {
        tdb database = new tdb("data/VZR-re2.TDB");

        List<PhaseModelKind> available = PhaseModelAvailability.availableModels(
                database,
                Arrays.asList("V", "ZR"),
                "BCC_A2");

        assertEquals(1, available.size(), "CEF-only DB should report exactly one model");
        assertEquals(PhaseModelKind.CEF, available.get(0), "Should be CEF");

        assertTrue(PhaseModelAvailability.isAvailable(database, Arrays.asList("V", "ZR"), "BCC_A2", PhaseModelKind.CEF),
                "CEF should be available");
        assertFalse(PhaseModelAvailability.isAvailable(database, Arrays.asList("V", "ZR"), "BCC_A2", PhaseModelKind.CVM),
                "CVM should NOT be available");
    }

    /**
     * Test 2: CVM-dominant database (contains both CEF and CVM).
     *
     * The VZR-re2-CVM-eName-model.TDB contains both CEF (PARAMETER G) and
     * CVM (PARAMETER G_CVM) parameters for BCC_A2. This is the realistic
     * case where both models are available for the same phase/element set.
     *
     * Verifies that both models are reported as available.
     */
    @Test
    void bothCefAndCvmAvailable() throws IOException {
        tdb database = new tdb("data/VZR-re2-CVM-eName-model.TDB");

        List<PhaseModelKind> available = PhaseModelAvailability.availableModels(
                database,
                Arrays.asList("V", "ZR"),
                "BCC_A2");

        assertEquals(2, available.size(), "DB should report two models (CEF and CVM)");
        assertEquals(PhaseModelKind.CEF, available.get(0), "CEF should be first");
        assertEquals(PhaseModelKind.CVM, available.get(1), "CVM should be second");

        assertTrue(PhaseModelAvailability.isAvailable(database, Arrays.asList("V", "ZR"), "BCC_A2", PhaseModelKind.CEF),
                "CEF should be available");
        assertTrue(PhaseModelAvailability.isAvailable(database, Arrays.asList("V", "ZR"), "BCC_A2", PhaseModelKind.CVM),
                "CVM should be available");
    }

    /**
     * Test 3: CEF explicit construction still works (CEF-only DB).
     */
    @Test
    void cefExplicitBuildFromCefOnlyDb() throws IOException {
        tdb database = new tdb("data/VZR-re2.TDB");

        // Verify CEF is available
        assertTrue(PhaseModelAvailability.isAvailable(database, Arrays.asList("V", "ZR"), "BCC_A2", PhaseModelKind.CEF),
                "CEF should be available");

        // Explicitly build CEF model
        GibbsEnergyModel model = PhaseModelFactory.buildCef(
                "BCC_A2",
                database,
                Arrays.asList("V", "ZR"),
                null, // affMap
                null  // pMap
        );

        assertTrue(model instanceof system.model.cef.CefGibbs, "Should return CefGibbs");
        assertEquals("BCC_A2", model.phaseName());
        assertEquals("CEF", model.modelType());
    }

    /**
     * Test 4: CVM explicit construction from TDB (both-model DB).
     */
    @Test
    void cvmExplicitBuildFromBothModelDb() throws IOException {
        tdb database = new tdb("data/VZR-re2-CVM-eName-model.TDB");

        // Verify CVM is available
        assertTrue(PhaseModelAvailability.isAvailable(database, Arrays.asList("V", "ZR"), "BCC_A2", PhaseModelKind.CVM),
                "CVM should be available");

        // Explicitly build CVM model
        GibbsEnergyModel model = PhaseModelFactory.buildCvmFromTdb(
                database,
                Arrays.asList("V", "ZR"),
                "BCC_A2"
        );

        assertTrue(model instanceof system.model.cvm.CvmGibbsModel, "Should return CvmGibbsModel");
        assertEquals("BCC_A2", model.phaseName());
        assertEquals("CVM", model.modelType());
    }

    /**
     * Test 5: Request unavailable model (CEF from CEF-only DB fails if no CEF).
     *
     * Uses the CEF-only database and requests a non-existent phase.
     * Verifies that the factory fails clearly.
     */
    @Test
    void requestUnavailablePhase() throws IOException {
        tdb database = new tdb("data/VZR-re2.TDB");

        // Verify non-existent phase has no models
        List<PhaseModelKind> available = PhaseModelAvailability.availableModels(
                database,
                Arrays.asList("V", "ZR"),
                "NONEXISTENT");

        assertEquals(0, available.size(), "Non-existent phase should have no models");

        // Attempt to build any model for the phase should fail
        assertThrows(
                Exception.class,  // CefGibbs will throw IllegalArgumentException
                () -> PhaseModelFactory.buildCef("NONEXISTENT", database, Arrays.asList("V", "ZR"), null, null),
                "Should reject build of non-existent phase"
        );
    }

    /**
     * Test 6: Null argument handling.
     */
    @Test
    void nullArgumentHandling() throws IOException {
        tdb database = new tdb("data/VZR-re2.TDB");

        assertThrows(NullPointerException.class, () ->
                PhaseModelAvailability.availableModels(null, Arrays.asList("V", "ZR"), "BCC_A2"),
                "Should reject null database");

        assertThrows(NullPointerException.class, () ->
                PhaseModelAvailability.availableModels(database, null, "BCC_A2"),
                "Should reject null elements");

        assertThrows(IllegalArgumentException.class, () ->
                PhaseModelAvailability.availableModels(database, Arrays.asList("V", "ZR"), null),
                "Should reject null phase name");

        assertThrows(IllegalArgumentException.class, () ->
                PhaseModelAvailability.availableModels(database, Arrays.asList("V", "ZR"), "  "),
                "Should reject blank phase name");
    }

    /**
     * Test 7: Non-existent phase returns empty availability.
     */
    @Test
    void nonExistentPhase() throws IOException {
        tdb database = new tdb("data/VZR-re2.TDB");

        List<PhaseModelKind> available = PhaseModelAvailability.availableModels(
                database,
                Arrays.asList("V", "ZR"),
                "NONEXISTENT_PHASE");

        assertEquals(0, available.size(), "Non-existent phase should have no available models");
    }
}
