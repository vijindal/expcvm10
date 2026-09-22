package system.database;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link tdb#getCvmParams(ArrayList, String)} correctly
 * extracts CVM parameters from a loaded TDB file.
 *
 * <p>Tests the integration of:
 * <ol>
 *   <li>TDB file parsing (PARAMETER G_CVM records)</li>
 *   <li>Phase lookup by name</li>
 *   <li>Parameter filtering by element set</li>
 *   <li>Type-based filtering (keep only G_CVM)</li>
 * </ol>
 *
 * <p>Uses the prototype TDB file {@code data/VZR-re2-CVM-model.TDB},
 * which contains CVM parameters for the BCC_A2 phase.
 */
class TdbCvmParameterTest {

    private static tdb database;

    @BeforeAll
    static void loadDatabase() throws IOException {
        database = new tdb("data/VZR-re2-CVM-model.TDB");
    }

    @Test
    void getCvmParamsReturnsAtLeastOneCvmParameterForBccA2() {
        ArrayList<String> elements = new ArrayList<>(Arrays.asList("V", "ZR"));
        ArrayList<tdb.Parameter> cvmParamsList = database.getCvmParams(elements, "BCC_A2");

        assertTrue(cvmParamsList.size() > 0,
                "Expected at least 1 CVM parameter for BCC_A2(V,ZR), got " + cvmParamsList.size());
    }

    @Test
    void allParametersHaveTypeGCvm() {
        ArrayList<String> elements = new ArrayList<>(Arrays.asList("V", "ZR"));
        ArrayList<tdb.Parameter> cvmParams = database.getCvmParams(elements, "BCC_A2");

        for (int i = 0; i < cvmParams.size(); i++) {
            tdb.Parameter p = cvmParams.get(i);
            String type = p.getType();
            assertTrue(type != null && type.trim().equalsIgnoreCase("G_CVM"),
                    "Parameter " + i + " has type '" + type + "', expected G_CVM");
        }
    }

    @Test
    void parametersHaveValidOrders() {
        ArrayList<String> elements = new ArrayList<>(Arrays.asList("V", "ZR"));
        ArrayList<tdb.Parameter> cvmParams = database.getCvmParams(elements, "BCC_A2");

        for (int i = 0; i < cvmParams.size(); i++) {
            int order = cvmParams.get(i).getOrder();
            assertTrue(order >= 0,
                    "Parameter " + i + " has invalid order " + order + ", expected >= 0");
        }
    }

    @Test
    void parametersHaveValidConstituentStructure() {
        ArrayList<String> elements = new ArrayList<>(Arrays.asList("V", "ZR"));
        ArrayList<tdb.Parameter> cvmParams = database.getCvmParams(elements, "BCC_A2");

        // BCC_A2 has 2 sublattices: [V,ZR] and [VA]
        for (int i = 0; i < cvmParams.size(); i++) {
            tdb.Parameter p = cvmParams.get(i);
            ArrayList<ArrayList<String>> constituentList = p.getConstituentList();

            assertNotNull(constituentList,
                    "Parameter " + i + " has null constituent list");
            assertEquals(2, constituentList.size(),
                    "Parameter " + i + " has " + constituentList.size() + " sublattices, expected 2");

            // Sublattice 0: [V, ZR]
            ArrayList<String> sub0 = constituentList.get(0);
            assertEquals(2, sub0.size(), "Parameter " + i + " sublattice 0 has " + sub0.size() + " elements, expected 2");
            assertTrue(sub0.contains("V") && sub0.contains("ZR"),
                    "Parameter " + i + " sublattice 0 should contain V,ZR, got " + sub0);

            // Sublattice 1: [VA]
            ArrayList<String> sub1 = constituentList.get(1);
            assertEquals(1, sub1.size(), "Parameter " + i + " sublattice 1 has " + sub1.size() + " elements, expected 1");
            assertEquals("VA", sub1.get(0),
                    "Parameter " + i + " sublattice 1 should be VA, got " + sub1.get(0));
        }
    }

    @Test
    void parametersHaveNonEmptyExpressions() {
        ArrayList<String> elements = new ArrayList<>(Arrays.asList("V", "ZR"));
        ArrayList<tdb.Parameter> cvmParams = database.getCvmParams(elements, "BCC_A2");

        for (int i = 0; i < cvmParams.size(); i++) {
            tdb.Parameter p = cvmParams.get(i);
            ArrayList<tdb.Exp> expList = p.getExpList();

            assertNotNull(expList,
                    "Parameter " + i + " has null expression list");
            assertTrue(expList.size() > 0,
                    "Parameter " + i + " has empty expression list");
        }
    }

    @Test
    void emptyResultForUnknownPhase() {
        ArrayList<String> elements = new ArrayList<>(Arrays.asList("V", "ZR"));
        ArrayList<tdb.Parameter> cvmParams = database.getCvmParams(elements, "NONEXISTENT_PHASE");

        assertEquals(0, cvmParams.size(),
                "Expected empty list for unknown phase, got " + cvmParams.size());
    }

    @Test
    void emptyResultForIncompatibleElements() {
        ArrayList<String> elements = new ArrayList<>(Arrays.asList("AL", "CU"));
        ArrayList<tdb.Parameter> cvmParams = database.getCvmParams(elements, "BCC_A2");

        assertEquals(0, cvmParams.size(),
                "Expected empty list for incompatible elements, got " + cvmParams.size());
    }
}
