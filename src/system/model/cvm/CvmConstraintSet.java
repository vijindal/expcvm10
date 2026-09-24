package system.model.cvm;

import system.model.InternalConstraintSet;

/**
 * Implements composition-block normalization constraint for CVM phases.
 *
 * <p>For a CVM phase with ncf correlation-function variables and nComp composition
 * variables, this provides exactly 1 constraint: the trailing composition block
 * must sum to 1. The leading ncf variables (cluster probabilities, correlation
 * functions) are unconstrained.
 */
public final class CvmConstraintSet implements InternalConstraintSet {

    private final int ncf;
    private final int nComp;
    private final double[][] jacobian;

    /**
     * Constructs a CVM constraint set.
     *
     * @param ncf number of non-point correlation-function variables (leading y-block)
     * @param nComp number of components (composition variables in trailing y-block)
     */
    public CvmConstraintSet(int ncf, int nComp) {
        this.ncf = ncf;
        this.nComp = nComp;

        int nip = ncf + nComp;
        this.jacobian = new double[1][nip];

        // Constraint: sum_{i=ncf}^{nip-1} y[i] = 1
        // Jacobian row 0: [0, 0, ..., 0, 1, 1, ..., 1]
        for (int i = ncf; i < nip; i++) {
            jacobian[0][i] = 1.0;
        }
    }

    @Override
    public int numConstraints() {
        return 1;
    }

    @Override
    public double[][] constraintJacobian() {
        return jacobian;
    }

    @Override
    public double[] constraintRhs() {
        return new double[] { 1.0 };
    }
}
