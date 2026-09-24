package system.model.cef;

import system.model.InternalConstraintSet;

/**
 * Implements sublattice sum-to-1 constraints for CEF phases.
 *
 * <p>For a CEF phase with ns sublattices, this provides ns constraints:
 * one sum-to-1 constraint for each sublattice's constituent block.
 */
public final class CefConstraintSet implements InternalConstraintSet {

    private final int numSiteVars;
    private final int[] offsets;
    private final int[] constituentsPerSublattice;
    private final double[][] jacobian;

    /**
     * Constructs a CEF constraint set from sublattice structure.
     *
     * @param numSiteVars total number of site-fraction variables
     * @param offsets offset of each sublattice's block in y (from
     *                {@link system.model.GibbsEnergyModel#offsets()})
     * @param constituentsPerSublattice number of constituents per sublattice
     *                                   (from {@link system.model.GibbsEnergyModel#constituentsPerSublattice()})
     */
    public CefConstraintSet(int numSiteVars,
                            int[] offsets,
                            int[] constituentsPerSublattice) {
        this.numSiteVars = numSiteVars;
        this.offsets = offsets.clone();
        this.constituentsPerSublattice = constituentsPerSublattice.clone();

        int ns = offsets.length;
        this.jacobian = new double[ns][numSiteVars];

        for (int s = 0; s < ns; s++) {
            int start = offsets[s];
            int count = constituentsPerSublattice[s];
            for (int i = start; i < start + count; i++) {
                if (i < numSiteVars) {
                    jacobian[s][i] = 1.0;
                }
            }
        }
    }

    @Override
    public int numConstraints() {
        return offsets.length;
    }

    @Override
    public double[][] constraintJacobian() {
        return jacobian;
    }

    @Override
    public double[] constraintRhs() {
        double[] rhs = new double[offsets.length];
        for (int i = 0; i < rhs.length; i++) {
            rhs[i] = 1.0;
        }
        return rhs;
    }
}
