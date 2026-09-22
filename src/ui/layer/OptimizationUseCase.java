package ui.layer;

import system.ports.LoggingPort;
import system.ports.OptimizationOutputPort;
import util.AppLevel;
import util.Trace;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Use case: parameter optimization workflows.
 *
 * <p><b>Deprecated:</b> This class relied on legacy code (OptMrq/Mrqcof) that has
 * been removed. Parameter optimization is not currently implemented in the modern
 * architecture. This class remains as a stub for CLI backward compatibility.
 */
@Deprecated
public class OptimizationUseCase {

    private static final Logger LOG = Logger.getLogger(OptimizationUseCase.class.getName());
    private final LoggingPort logger;
    private final OptimizationOutputPort outputPort;

    public OptimizationUseCase(LoggingPort logger, OptimizationOutputPort outputPort) {
        this.logger = logger;
        this.outputPort = outputPort;
    }

    /**
     * Not implemented - legacy code has been removed.
     * @throws UnsupportedOperationException always
     */
    public void runOptimization(String exptDataFile, String phaseDataFile,
                                String filePrefix, int maxIterations) throws IOException {
        throw new UnsupportedOperationException(
                "Parameter optimization is not implemented -- legacy code folder has been removed");
    }
}
