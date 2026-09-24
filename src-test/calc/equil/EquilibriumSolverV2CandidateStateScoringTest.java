package calc.equil;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4B: white-box coverage for the private {@code CandidateState}/
 * {@code candidateDrivingForce()} extraction that replaced the driving-force
 * arithmetic previously inlined separately in {@link
 * EquilibriumSolverV2#updateStablePhaseSet()}'s CEF and CVM branches.
 *
 * <p>This does not exercise CEF sampling, CVM relaxation, or any search
 * algorithm -- both remain untouched by Phase 4B. It only verifies that the
 * shared scoring helper computes exactly Sundman Eq. 62,
 * {@code D = -G + sum_A mu_A * M_A}, for state data shaped like each
 * branch's own candidate representation (raw sampled CEF site fractions;
 * relaxed CVM correlation functions), and that CEF-shaped and CVM-shaped
 * inputs are scored by the identical helper with no branch-specific
 * behavior.
 */
class EquilibriumSolverV2CandidateStateScoringTest {

    private static Object newCandidateState(double[] y, double g, double[] moles) throws Exception {
        Class<?> stateClass = candidateStateClass();
        Constructor<?> ctor = stateClass.getDeclaredConstructor(double[].class, double.class, double[].class);
        ctor.setAccessible(true);
        return ctor.newInstance(y, g, moles);
    }

    private static Class<?> candidateStateClass() throws Exception {
        for (Class<?> c : EquilibriumSolverV2.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("CandidateState")) {
                return c;
            }
        }
        throw new AssertionError("EquilibriumSolverV2.CandidateState not found.");
    }

    private static double candidateDrivingForce(
            EquilibriumSolverV2 solver, Object state, double[] mu) throws Exception {
        Method method = EquilibriumSolverV2.class.getDeclaredMethod(
                "candidateDrivingForce", candidateStateClass(), double[].class);
        method.setAccessible(true);
        return (double) method.invoke(solver, state, mu);
    }

    // ------------------------------------------------------------------
    // 1. CEF-shaped candidate: same arithmetic as the previously inlined
    //    "d = -g; for A: d += mu[A]*mA[A]" in the CEF branch.
    // ------------------------------------------------------------------
    @Test
    void cefShapedCandidateMatchesSundmanEq62() throws Exception {
        double[] y = {0.2, 0.8, 0.5, 0.5};
        double g = -12345.6789;
        double[] moles = {0.4, 0.6};
        double[] mu = {-1000.0, -2000.0};

        Object state = newCandidateState(y, g, moles);

        double expected = -g;
        for (int a = 0; a < mu.length; a++) {
            expected += mu[a] * moles[a];
        }

        double actual = candidateDrivingForce(new EquilibriumSolverV2(), state, mu);

        assertEquals(expected, actual, 1.0e-12,
                "CandidateState driving force must equal -G + sum mu_A*M_A exactly, "
                + "matching the formula previously inlined in the CEF branch.");
    }

    // ------------------------------------------------------------------
    // 2. CVM-shaped candidate (relaxed y=[u;x], differently sized moles):
    //    same helper, same formula -- no CEF/CVM branching inside scoring.
    // ------------------------------------------------------------------
    @Test
    void cvmShapedCandidateMatchesSundmanEq62() throws Exception {
        double[] yRelaxed = {0.0021969, -0.0430054, 0.0475848, 0.0475986, 0.95, 0.05};
        double g = -9876.54321;
        double[] moles = {0.95, 0.05};
        double[] mu = {-500.0, -750.0};

        Object state = newCandidateState(yRelaxed, g, moles);

        double expected = -g;
        for (int a = 0; a < mu.length; a++) {
            expected += mu[a] * moles[a];
        }

        double actual = candidateDrivingForce(new EquilibriumSolverV2(), state, mu);

        assertEquals(expected, actual, 1.0e-12,
                "CandidateState driving force must equal -G + sum mu_A*M_A exactly, "
                + "matching the formula previously inlined in the CVM branch.");
    }

    // ------------------------------------------------------------------
    // 3. mu shorter than moles: only the overlapping components contribute
    //    (Math.min(mu.length, moles.length)), same truncation behavior as
    //    both previously inlined loops and as drivingForce(PhaseWork, mu).
    // ------------------------------------------------------------------
    @Test
    void drivingForceTruncatesToShorterOfMuAndMoles() throws Exception {
        double[] y = {1.0};
        double g = -100.0;
        double[] moles = {1.0, 2.0, 3.0};
        double[] mu = {10.0};

        Object state = newCandidateState(y, g, moles);

        double expected = -g + mu[0] * moles[0];
        double actual = candidateDrivingForce(new EquilibriumSolverV2(), state, mu);

        assertEquals(expected, actual, 1.0e-12);
    }

    // ------------------------------------------------------------------
    // 4. Degenerate inputs return -Infinity, exactly like the existing
    //    drivingForce(PhaseWork, mu) helper -- no new tolerance introduced.
    // ------------------------------------------------------------------
    @Test
    void nonFiniteGYieldsNegativeInfinity() throws Exception {
        Object state = newCandidateState(new double[]{0.5}, Double.NaN, new double[]{1.0});
        double actual = candidateDrivingForce(new EquilibriumSolverV2(), state, new double[]{1.0});
        assertFalse(Double.isFinite(actual));
        assertEquals(Double.NEGATIVE_INFINITY, actual);
    }

    @Test
    void nullMolesYieldsNegativeInfinity() throws Exception {
        Object state = newCandidateState(new double[]{0.5}, -1.0, null);
        double actual = candidateDrivingForce(new EquilibriumSolverV2(), state, new double[]{1.0});
        assertEquals(Double.NEGATIVE_INFINITY, actual);
    }

    @Test
    void nullMuYieldsNegativeInfinity() throws Exception {
        Object state = newCandidateState(new double[]{0.5}, -1.0, new double[]{1.0});
        double actual = candidateDrivingForce(new EquilibriumSolverV2(), state, null);
        assertEquals(Double.NEGATIVE_INFINITY, actual);
    }

    // ------------------------------------------------------------------
    // 5. Both branches' shapes are scored by the SAME method object --
    //    confirms there is exactly one shared helper, not two overloads.
    // ------------------------------------------------------------------
    @Test
    void cefAndCvmShapedCandidatesUseTheSameHelperMethod() throws Exception {
        long methodCount = 0;
        for (Method m : EquilibriumSolverV2.class.getDeclaredMethods()) {
            if (m.getName().equals("candidateDrivingForce")) {
                methodCount++;
            }
        }
        assertTrue(methodCount == 1,
                "Expected exactly one candidateDrivingForce(...) helper shared by both "
                + "the CEF and CVM branches, found " + methodCount);
    }
}
