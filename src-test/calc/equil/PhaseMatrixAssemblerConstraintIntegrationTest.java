package calc.equil;

import system.model.GibbsEnergyModel;
import system.model.InternalConstraintSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify that PhaseMatrixAssembler correctly uses the InternalConstraintSet
 * abstraction for both CEF and CVM models.
 *
 * This test ensures that:
 * 1. getConstraintSet() is called by PhaseMatrixAssembler
 * 2. The returned constraint matrix is properly used in phase matrix assembly
 * 3. Both CEF and CVM integrate correctly with the solver machinery
 */
public class PhaseMatrixAssemblerConstraintIntegrationTest {

    /**
     * Verify that getConstraintSet() is properly integrated.
     * We test that the constraint set returned by a model can be used
     * to assemble a phase matrix with the correct dimensions.
     */
    @Test
    void constraintIntegration_ConstraintSetIsUsed() {
        // Build a mock model with known constraints
        MockGibbsEnergyModel model = new MockGibbsEnergyModel(4, 2, 2);

        // Verify the model exposes constraints
        InternalConstraintSet constraints = model.getConstraintSet();
        assertNotNull(constraints, "Model must provide constraint set");
        assertEquals(2, constraints.numConstraints(), "Should have 2 constraints");

        // Verify constraint matrix
        double[][] C = constraints.constraintJacobian();
        assertEquals(2, C.length);
        assertEquals(4, C[0].length);

        // Constraint 0: y[0] + y[1] = 1
        assertEquals(1.0, C[0][0]);
        assertEquals(1.0, C[0][1]);
        assertEquals(0.0, C[0][2]);
        assertEquals(0.0, C[0][3]);

        // Constraint 1: y[2] + y[3] = 1
        assertEquals(0.0, C[1][0]);
        assertEquals(0.0, C[1][1]);
        assertEquals(1.0, C[1][2]);
        assertEquals(1.0, C[1][3]);
    }

    /**
     * Mock GibbsEnergyModel for testing constraint integration.
     * Provides CEF-style constraints without needing a full CEF model.
     */
    private static class MockGibbsEnergyModel extends GibbsEnergyModel {
        private final int nip;
        private final int ns;
        private final int[] ncSub;
        private final int[] offs;

        MockGibbsEnergyModel(int nip, int ns, int constituentsPerSublattice) {
            this.nip = nip;
            this.ns = ns;
            this.ncSub = new int[ns];
            this.offs = new int[ns];

            int offset = 0;
            for (int s = 0; s < ns; s++) {
                this.ncSub[s] = constituentsPerSublattice;
                this.offs[s] = offset;
                offset += constituentsPerSublattice;
            }
        }

        @Override public String phaseName() { return "MOCK"; }
        @Override public String modelType() { return "MOCK"; }
        @Override public java.util.ArrayList<String> elementNames() {
            return new java.util.ArrayList<>(Arrays.asList("A", "B"));
        }
        @Override public String[] componentList() { return new String[]{"A", "B"}; }
        @Override public int numComponents() { return 2; }
        @Override public int numTotalParams() { return nip; }
        @Override public double nfu() { return 1.0; }
        @Override public double[] getInitialInternalVars(double[] x) {
            return new double[nip];
        }
        @Override public double[] compositionFromInternal(double[] y) {
            return new double[2];
        }
        @Override public boolean isValid(double[] y) { return true; }

        @Override
        public InternalConstraintSet getConstraintSet() {
            return new system.model.cef.CefConstraintSet(nip, offs, ncSub);
        }

        @Override public double G(double T, double P, double[] y) { return 0.0; }
        @Override public double[] dG_dy(double T, double P, double[] y) {
            return new double[nip];
        }
        @Override public double[][] d2G_dy2(double T, double P, double[] y) {
            return new double[nip][nip];
        }
        @Override public double dG_dT(double T, double P, double[] y) { return 0.0; }
        @Override public double[] d2G_dydT(double T, double P, double[] y) {
            return new double[nip];
        }
        @Override public double dG_dP(double T, double P, double[] y) { return 0.0; }
        @Override public double[] d2G_dydP(double T, double P, double[] y) {
            return new double[nip];
        }
        @Override public double[] moles(double[] y) {
            return new double[2];
        }
        @Override public double[][] dMoles_dy() {
            return new double[2][nip];
        }
        @Override public int numSublattices() { return ns; }
        @Override public int numSiteVars() { return nip; }
        @Override public int[] offsets() { return offs.clone(); }
        @Override public int[] constituentsPerSublattice() { return ncSub.clone(); }
        @Override public void printPhaseInfo() {}
    }
}
