package application.assessment;

import application.service.OptimizationService;

import java.io.IOException;

/**
 * Use case: fit model parameters against experimental datasets.
 * Track A — Assessment workflow.
 */
public class FitParametersUseCase {

    private final OptimizationService optimizationService;

    public FitParametersUseCase(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    public void execute(String exptDataFile, String phaseDataFile,
                        String filePrefix, int maxIterations) throws IOException {
        optimizationService.runOptimization(exptDataFile, phaseDataFile, filePrefix, maxIterations);
    }
}
