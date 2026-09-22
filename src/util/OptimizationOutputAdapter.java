package util;

import system.ports.OptimizationOutputPort;

import java.io.IOException;

/**
 * @deprecated Legacy optimization output adapter. No longer used after legacy code removal.
 */
@Deprecated
public class OptimizationOutputAdapter implements OptimizationOutputPort {

    @Override
    @Deprecated
    public OutputWriter createWriter(String filePrefix, int logLevel) throws IOException {
        throw new UnsupportedOperationException("OptimizationOutputAdapter is deprecated -- legacy code folder has been removed");
    }
}
