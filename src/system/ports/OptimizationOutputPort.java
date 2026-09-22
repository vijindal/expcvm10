package system.ports;

import java.io.IOException;

/**
 * @deprecated Legacy optimization output port. No longer used after legacy code removal.
 * Parameter optimization workflow has been replaced with modern architecture.
 */
@Deprecated
public interface OptimizationOutputPort {

    /**
     * @deprecated Not implemented
     */
    @Deprecated
    OutputWriter createWriter(String filePrefix, int logLevel) throws IOException;

    /**
     * @deprecated Not implemented
     */
    @Deprecated
    interface OutputWriter {
        /**
         * @deprecated Not implemented
         */
        @Deprecated
        void initOptimization(Object exptdata, Object phasedata) throws IOException;

        /**
         * @deprecated Not implemented
         */
        @Deprecated
        void finalizeOptimization(Object phasedata) throws IOException;
    }
}
