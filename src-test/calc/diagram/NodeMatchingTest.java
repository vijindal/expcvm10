package calc.diagram;

import calc.equil.EquilibriumSolverV2;
import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Node identity/matching test (Step 1 of building the phase-diagram engine
 * described in {@code docs/phase_diagram_engine_flowchart.md}): {@link
 * Node#matches} should recognize the same physical equilibrium as "the
 * same node" and reject a genuinely different one.
 *
 * <p>Reference values are OpenCalphad's own TQ interface output (not
 * pycalphad, unlike most of this codebase's other contract tests) --
 * {@code data/crfe_oc_reference.TDB} is OpenCalphad's bundled
 * {@code examples/TQ4lib/F90/crfe/crfe.TDB}, and both cases below were run
 * through OC's {@code tqex1} example program (via a pseudo-terminal driver
 * -- OC's console/example programs use raw-mode terminal I/O and hang or
 * garble input under plain piped stdin, see
 * {@code docs/oc_reference_tests/run_pty.py}) with matching conditions.
 * This directly validates {@link EquilibriumSolverV2} against a second,
 * independent CALPHAD implementation, and gives {@link Node} real
 * OC-traceable equilibria to key its identity test off rather than
 * synthetic data.
 *
 * <p>OC reference, Cr-Fe, T=1000 K, P=101325 Pa (SER chemical potentials,
 * J/mol):
 * <ul>
 *   <li>x(Cr)=0.3: stable BCC_A2 only; mu(Cr)=-40717.79, mu(Fe)=-44219.07</li>
 *   <li>x(Cr)=0.7: stable BCC_A2 only; mu(Cr)=-38652.39, mu(Fe)=-46254.72</li>
 * </ul>
 * Our solver's own values (asserted below to agree with OC to within
 * {@link #OC_AGREEMENT_TOLERANCE}) are used to build the two {@link Node}s
 * under test, so this is also an equilibrium cross-check, not only a
 * {@code Node} unit test.
 */
public class NodeMatchingTest {

    private static final String TDB = "data/crfe_oc_reference.TDB";
    private static final List<String> ELEMENTS = List.of("CR", "FE");
    private static final List<String> PHASES =
            List.of("LIQUID", "BCC_A2", "FCC_A1", "SIGMA");

    private static final double T = 1000.0;
    private static final double P = 101325.0;

    /** Relative tolerance for this solver's own chemical potentials vs. OC's. */
    private static final double OC_AGREEMENT_TOLERANCE = 1e-4;

    /** Node-matching relative tolerance (see {@link Node#matches}). */
    private static final double NODE_MATCH_TOLERANCE = 1e-4;

    private static EquilibriumResult solve(double xCr) throws IOException {
        ThermodynamicSystem system = ThermodynamicSystem.build(TDB, ELEMENTS, PHASES);
        List<GibbsEnergyModel> candidates = system.phaseModels();
        return new EquilibriumSolverV2().solve(T, P, new double[] {xCr, 1.0 - xCr}, candidates);
    }

    private static void assertAgreesWithOc(EquilibriumResult result, double ocMuCr, double ocMuFe) {
        double[] mu = result.getMu();
        assertEquals(ocMuCr, mu[0], Math.abs(ocMuCr) * OC_AGREEMENT_TOLERANCE,
                "mu(Cr) should agree with OpenCalphad's tqex1 output");
        assertEquals(ocMuFe, mu[1], Math.abs(ocMuFe) * OC_AGREEMENT_TOLERANCE,
                "mu(Fe) should agree with OpenCalphad's tqex1 output");
    }

    @Test
    void solverAgreesWithOpenCalphadAtXCr03() throws IOException {
        EquilibriumResult result = solve(0.3);
        assertTrue(result.isConverged());
        assertAgreesWithOc(result, -40717.79, -44219.07);
    }

    @Test
    void solverAgreesWithOpenCalphadAtXCr07() throws IOException {
        EquilibriumResult result = solve(0.7);
        assertTrue(result.isConverged());
        assertAgreesWithOc(result, -38652.39, -46254.72);
    }

    @Test
    void sameEquilibriumRecomputedTwiceMatchesAsTheSameNode() throws IOException {
        EquilibriumResult a = solve(0.3);
        EquilibriumResult b = solve(0.3);

        Node nodeA = new Node(0, a, new double[] {T, 0.3});
        Node nodeB = new Node(1, b, new double[] {T, 0.3});

        assertTrue(nodeA.matches(nodeB, NODE_MATCH_TOLERANCE),
                "two independent solves of the identical equilibrium should match as the same node");
    }

    @Test
    void differentCompositionsWithSameStablePhaseDoNotMatchAsTheSameNode() throws IOException {
        EquilibriumResult a = solve(0.3);
        EquilibriumResult b = solve(0.7);

        Node nodeA = new Node(0, a, new double[] {T, 0.3});
        Node nodeB = new Node(1, b, new double[] {T, 0.7});

        // Same stable phase set (BCC_A2 only, per the OC reference above) but
        // materially different chemical potentials -- Node#matches must not
        // key off the phase set alone.
        assertEquals(nodeA.stablePhaseNames, nodeB.stablePhaseNames);
        assertFalse(nodeA.matches(nodeB, NODE_MATCH_TOLERANCE),
                "different equilibria with the same stable phase set must not match as the same node");
    }

    @Test
    void nodeIdentityFieldsReflectOcReferenceAtXCr03() throws IOException {
        EquilibriumResult result = solve(0.3);
        Node node = new Node(0, result, new double[] {T, 0.3});

        assertEquals(1, node.stablePhaseNames.size());
        assertTrue(node.stablePhaseNames.contains("BCC_A2"));
        assertEquals(2, node.chemicalPotentials.length);
    }
}
