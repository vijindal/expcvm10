package ui.result;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * {@link EquilibriumReport} -- this codebase's own equivalent of
 * OpenCalphad's {@code l r <n>} console dump, verified against Sundman
 * 2021 Calphad 75, Section 3.1's own minimum spec ("T, P, the amount and
 * constitution of all phases and the chemical potentials") rather than
 * OC's specific text layout.
 */
public class EquilibriumReportTest {

    @Test
    void reportMatchesRealSolvedAgCuPoint() throws IOException {
        // Same OC-confirmed point used throughout MultiDiagramTypeSuiteTest:
        // T=1150K, x(Cu)=0.05, single-phase FCC_A1.
        // OC reference (docs/oc_reference_tests/agcu_mu_1150.txt):
        // mu(Ag)=-67987.5, mu(Cu)=-65900.2 J/mol (SER reference).
        ThermodynamicSystem system = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));
        List<GibbsEnergyModel> candidates = system.phaseModels();

        EquilibriumResult result = new calc.equil.EquilibriumSolverV2()
                .solve(1150.0, 101325.0, new double[] { 0.95, 0.05 }, candidates);

        assertTrue(result.isConverged());

        String report = EquilibriumReport.format(result, system.elements());

        assertTrue(report.contains("FCC_A1"), "should name the real stable phase");
        assertTrue(report.contains("T=1150.00"));
        assertTrue(report.contains("AG") && report.contains("CU"));
    }
}
