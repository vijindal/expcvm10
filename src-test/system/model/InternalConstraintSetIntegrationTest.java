package system.model;

import system.model.cef.CefConstraintSet;
import system.model.cvm.CvmConstraintSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify that InternalConstraintSet implementations produce correct constraint
 * matrices and RHS vectors for CEF and CVM models.
 */
public class InternalConstraintSetIntegrationTest {

    // ════════════════════════════════════════════════════════════════════
    // CEF Constraint Tests
    // ════════════════════════════════════════════════════════════════════

    /**
     * CEF with 2 sublattices: (V,Zr)1(Va)3
     * - Sublattice 0: 2 constituents (V, Zr)
     * - Sublattice 1: 2 constituents (Va)  [note: only 1 choice, simplified for test]
     * Expected: 2 constraints, one per sublattice
     */
    @Test
    void cefConstraintMatrix_BCC_A2() {
        int numSiteVars = 4;  // 2 on sublattice 0, 2 on sublattice 1
        int[] offsets = {0, 2};
        int[] ncSub = {2, 2};

        CefConstraintSet constraints = new CefConstraintSet(numSiteVars, offsets, ncSub);

        // Verify constraint count
        assertEquals(2, constraints.numConstraints(), "Should have 2 constraints (one per sublattice)");

        // Verify constraint matrix
        double[][] C = constraints.constraintJacobian();
        assertEquals(2, C.length, "Jacobian rows");
        assertEquals(4, C[0].length, "Jacobian columns");

        // Constraint 0: y[0] + y[1] = 1
        assertEquals(1.0, C[0][0], "C[0][0] should be 1.0");
        assertEquals(1.0, C[0][1], "C[0][1] should be 1.0");
        assertEquals(0.0, C[0][2], "C[0][2] should be 0.0");
        assertEquals(0.0, C[0][3], "C[0][3] should be 0.0");

        // Constraint 1: y[2] + y[3] = 1
        assertEquals(0.0, C[1][0], "C[1][0] should be 0.0");
        assertEquals(0.0, C[1][1], "C[1][1] should be 0.0");
        assertEquals(1.0, C[1][2], "C[1][2] should be 1.0");
        assertEquals(1.0, C[1][3], "C[1][3] should be 1.0");

        // Verify RHS
        double[] b = constraints.constraintRhs();
        assertEquals(2, b.length, "RHS length");
        assertEquals(1.0, b[0], "b[0] = 1.0 for sum-to-1 constraint");
        assertEquals(1.0, b[1], "b[1] = 1.0 for sum-to-1 constraint");
    }

    /**
     * CEF with 3 sublattices, varying constituent counts.
     * This verifies the Cartesian offset calculation.
     */
    @Test
    void cefConstraintMatrix_3Sublattices() {
        int numSiteVars = 9;  // 2 + 3 + 4
        int[] offsets = {0, 2, 5};
        int[] ncSub = {2, 3, 4};

        CefConstraintSet constraints = new CefConstraintSet(numSiteVars, offsets, ncSub);

        assertEquals(3, constraints.numConstraints(), "Should have 3 constraints");

        double[][] C = constraints.constraintJacobian();

        // Constraint 0: y[0] + y[1] = 1
        assertEquals(1.0, C[0][0]);
        assertEquals(1.0, C[0][1]);
        for (int i = 2; i < 9; i++) {
            assertEquals(0.0, C[0][i], "C[0][" + i + "] should be 0.0");
        }

        // Constraint 1: y[2] + y[3] + y[4] = 1
        assertEquals(1.0, C[1][2]);
        assertEquals(1.0, C[1][3]);
        assertEquals(1.0, C[1][4]);
        for (int i = 0; i < 2; i++) {
            assertEquals(0.0, C[1][i], "C[1][" + i + "] should be 0.0");
        }
        for (int i = 5; i < 9; i++) {
            assertEquals(0.0, C[1][i], "C[1][" + i + "] should be 0.0");
        }

        // Constraint 2: y[5] + y[6] + y[7] + y[8] = 1
        for (int i = 5; i < 9; i++) {
            assertEquals(1.0, C[2][i], "C[2][" + i + "] should be 1.0");
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(0.0, C[2][i], "C[2][" + i + "] should be 0.0");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CVM Constraint Tests
    // ════════════════════════════════════════════════════════════════════

    /**
     * CVM with ncf=2 correlation functions, nComp=2 composition variables.
     * y = [u1, u2, xA, xB]  (nip = 4)
     * Single constraint: xA + xB = 1 (composition block)
     */
    @Test
    void cvmConstraintMatrix_Binary() {
        int ncf = 2;    // correlation functions
        int nComp = 2;  // components

        CvmConstraintSet constraints = new CvmConstraintSet(ncf, nComp);

        // Verify constraint count
        assertEquals(1, constraints.numConstraints(), "Should have 1 constraint (composition normalization)");

        // Verify constraint matrix: [0, 0, 1, 1]
        double[][] C = constraints.constraintJacobian();
        assertEquals(1, C.length, "Jacobian rows");
        assertEquals(4, C[0].length, "Jacobian columns");

        assertEquals(0.0, C[0][0], "C[0][0] (u1) should be 0.0");
        assertEquals(0.0, C[0][1], "C[0][1] (u2) should be 0.0");
        assertEquals(1.0, C[0][2], "C[0][2] (xA) should be 1.0");
        assertEquals(1.0, C[0][3], "C[0][3] (xB) should be 1.0");

        // Verify RHS
        double[] b = constraints.constraintRhs();
        assertEquals(1, b.length, "RHS length");
        assertEquals(1.0, b[0], "b[0] = 1.0 for composition-normalization constraint");
    }

    /**
     * CVM with ncf=5 correlation functions, nComp=3 composition variables.
     * Single constraint on the trailing 3 composition variables.
     */
    @Test
    void cvmConstraintMatrix_Ternary() {
        int ncf = 5;
        int nComp = 3;

        CvmConstraintSet constraints = new CvmConstraintSet(ncf, nComp);

        assertEquals(1, constraints.numConstraints());

        double[][] C = constraints.constraintJacobian();
        assertEquals(1, C.length);
        assertEquals(8, C[0].length, "nip = ncf + nComp = 5 + 3 = 8");

        // Leading ncf positions should be zero
        for (int i = 0; i < ncf; i++) {
            assertEquals(0.0, C[0][i], "C[0][" + i + "] (correlation function) should be 0.0");
        }

        // Trailing nComp positions should be 1.0
        for (int i = ncf; i < ncf + nComp; i++) {
            assertEquals(1.0, C[0][i], "C[0][" + i + "] (composition) should be 1.0");
        }

        double[] b = constraints.constraintRhs();
        assertEquals(1.0, b[0]);
    }

    /**
     * Edge case: ncf=0 (pure composition variables), nComp=2.
     * All variables are composition; single constraint sums them to 1.
     */
    @Test
    void cvmConstraintMatrix_PureComposition() {
        int ncf = 0;
        int nComp = 2;

        CvmConstraintSet constraints = new CvmConstraintSet(ncf, nComp);

        assertEquals(1, constraints.numConstraints());

        double[][] C = constraints.constraintJacobian();
        assertEquals(1.0, C[0][0], "All variables are composition");
        assertEquals(1.0, C[0][1]);
    }
}
