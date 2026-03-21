package application.calculation;

import application.dto.CalculationRequest;
import application.dto.CalculationResult;
import application.service.CalculationService;

import java.io.IOException;

/**
 * Use case: single-point equilibrium/property calculation.
 * Track B — Calculation workflow.
 */
public class SinglePointUseCase {

    private final CalculationService calculationService;

    public SinglePointUseCase(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    public CalculationResult execute(CalculationRequest request) throws IOException {
        return calculationService.runCalculation(request);
    }
}
