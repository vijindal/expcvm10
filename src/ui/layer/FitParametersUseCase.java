package ui.layer;

import system.ports.LegacyFitPort;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Use case: fit model parameters against experimental datasets.
 * Track A — Assessment workflow.
 * Delegates to {@link OptimizationUseCase}.
 */
public class FitParametersUseCase {

    private static final Logger LOG = Logger.getLogger(FitParametersUseCase.class.getName());

    private final OptimizationUseCase optimizationUseCase;

    public FitParametersUseCase(OptimizationUseCase optimizationUseCase) {
        this.optimizationUseCase = optimizationUseCase;
    }

    public void execute(String exptDataFile, String phaseDataFile,
                        String filePrefix, int maxIterations) throws IOException {
        LOG.info("FitParametersUseCase: executing parameter fitting");
        optimizationUseCase.runOptimization(exptDataFile, phaseDataFile, filePrefix, maxIterations);
    }
}
