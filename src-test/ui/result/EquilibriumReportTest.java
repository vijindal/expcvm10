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

    private static EquilibriumResult dummyEquilibrium() {
        EquilibriumResult.PhaseResult fcc1 = new EquilibriumResult.PhaseResult(
                "FCC_A1#1", "CEF", 0.8877,
                new double[] { 0.896932, 0.103068 },
                new double[] { 0.896932, 0.103068, 1.0 },
                -54660.0, 0.0, 1.0);
        EquilibriumResult.PhaseResult fcc2 = new EquilibriumResult.PhaseResult(
                "FCC_A1_AUTO#2", "CEF", 0.1123,
                new double[] { 0.033674, 0.966326 },
                new double[] { 0.033674, 0.966326, 1.0 },
                -50000.0, 0.0, 1.0);
        return new EquilibriumResult(1000.0, 1.0e5,
                new double[] { -6.8174e3, -5.6004e3 },
                List.of(fcc1, fcc2), List.of(), true, 9);
    }

    @Test
    void reportContainsAllPaperMandatedFields() {
        String report = EquilibriumReport.format(dummyEquilibrium(), List.of("AG", "CU"));

        // Sundman 2021 Section 3.1's minimum required content: T, P,
        // chemical potentials, and amount + constitution of all phases.
        assertTrue(report.contains("T=1000.00"), "must report T");
        assertTrue(report.contains("P=1.0000E+05"), "must report P");
        assertTrue(report.contains("AG") && report.contains("CU"),
                "must report chemical potentials per component");
        assertTrue(report.contains("FCC_A1#1") && report.contains("FCC_A1_AUTO#2"),
                "must report every stable phase by name");
        assertTrue(report.contains("amount="), "must report phase amount");
        assertTrue(report.contains("x:"), "must report phase composition (constitution)");
        assertTrue(report.contains("y:"), "must report phase internal site fractions (constitution)");
    }

    @Test
    void reportSurfacesDataBeyondOcsOwnDump() {
        String report = EquilibriumReport.format(dummyEquilibrium(), List.of("AG", "CU"));

        // This codebase's own additions, not present in OC's l r dump:
        // driving force per phase, and total system G per mole of real
        // atoms (comparable across differently-sized formula units).
        assertTrue(report.contains("driving force="), "should surface per-phase driving force");
        assertTrue(report.contains("G_sys/atom"), "should surface per-atom system G");
        assertTrue(report.contains("iterations"), "should surface solver iteration count");
    }

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
