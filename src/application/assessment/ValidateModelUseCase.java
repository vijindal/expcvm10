package application.assessment;

import application.service.CalculationService;

import java.io.IOException;

/**
 * Use case: validate fitted models against reference datasets.
 * Track A — Assessment workflow.
 */
public class ValidateModelUseCase {

    private final CalculationService calculationService;

    public ValidateModelUseCase(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    /**
     * Run CalModel validation on experimental data and compare with model output.
     */
    public void execute(String exptDataFile, String phaseDataFile) throws IOException {
        calculationService.runCalModel(exptDataFile, phaseDataFile);
    }
}
