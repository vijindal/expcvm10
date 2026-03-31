package legacy;

import contracts.LegacyFitPort;
import legacy.phase.C15Phase;

/**
 * Adapter that implements {@link LegacyFitPort} by wrapping legacy minimizers.
 *
 * Acts as a bridge between the modern application and the legacy `calbince`/`phase` packages.
 * All calls are delegated to {@link C15Minimizer} or {@link C15MinimizerImproved}.
 */
public class LegacyFitAdapter implements LegacyFitPort {

    @Override
    public double[] fitC15Parameters(Object phase, double[] composition, int nComp, int[][] speciesToComp) {
        if (!(phase instanceof C15Phase)) {
            throw new IllegalArgumentException("Expected C15Phase, got " + phase.getClass().getName());
        }
        return C15Minimizer.minimizeGibbs((C15Phase) phase, composition, nComp, speciesToComp);
    }

    @Override
    public double[] fitC15ParametersImproved(Object phase, double[] composition, int nComp, int[][] speciesToComp) {
        if (!(phase instanceof C15Phase)) {
            throw new IllegalArgumentException("Expected C15Phase, got " + phase.getClass().getName());
        }
        return C15MinimizerImproved.minimizeGibbs((C15Phase) phase, composition, nComp, speciesToComp);
    }
}
