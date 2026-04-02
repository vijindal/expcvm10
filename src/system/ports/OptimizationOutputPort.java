package system.ports;

import legacy.calbince.ExptData;
import legacy.calbince.PhaseData;

import java.io.IOException;

/**
 * Port interface for writing optimization workflow outputs.
 * Infrastructure layer provides the concrete implementation.
 */
public interface OptimizationOutputPort {

    /**
     * Factory method to create an output writer with the specified configuration.
     * @param filePrefix prefix for output files
     * @param logLevel log level
     * @return configured output port
     */
    OutputWriter createWriter(String filePrefix, int logLevel) throws IOException;

    /**
     * Writer interface for actual output operations.
     */
    interface OutputWriter {
        /**
         * Initialize optimization output and write header information.
         * @param exptdata experimental data
         * @param phasedata phase data
         */
        void initOptimization(ExptData exptdata, PhaseData phasedata) throws IOException;

        /**
         * Finalize optimization output and write results.
         * @param phasedata phase data containing results
         */
        void finalizeOptimization(PhaseData phasedata) throws IOException;
    }
}
