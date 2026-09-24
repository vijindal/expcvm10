package system.model.cvm;

import system.model.InternalStateSampler;

/**
 * CVM internal-state sampler (not yet implemented).
 *
 * <p>CVM correlation functions and composition block require a different
 * sampling strategy than CEF's simplex-based endmember/Halton approach. This
 * sampler placeholder throws UnsupportedOperationException to signal that
 * sampling is not yet available for CVM phases.
 *
 * <p>This is intentional: we are decoupling GridMinimizer from CEF-specific
 * assumptions first, without inventing untested CVM sampling mathematics.
 */
public final class CvmInternalStateSampler implements InternalStateSampler {

    public CvmInternalStateSampler() {
    }

    @Override
    public double[][] sample(int density, int edgePoints) {
        throw new UnsupportedOperationException(
            "Global internal-state-space sampling not implemented for CVM. "
            + "CVM correlation functions cannot be enumerated like CEF endmembers. "
            + "Use EquilibriumSolverV2.setInitialStateForTest() to supply explicit initial state, "
            + "or implement CVM-specific global-search strategy."
        );
    }
}
