package application.calculation;

import application.dto.CalculationRequest;
import application.dto.CalculationResult;
import application.service.CalculationService;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Use case: step calculation — vary one condition, track response.
 * Track B — Calculation workflow.
 */
public class StepCalculationUseCase {

    private final CalculationService calculationService;

    public StepCalculationUseCase(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    /**
     * Run a series of single-point calculations stepping over temperature.
     *
     * @param template base request (T will be overridden per step)
     * @param tStart   start temperature
     * @param tEnd     end temperature
     * @param tStep    temperature increment
     * @return list of results, one per step
     */
    public ArrayList<CalculationResult> execute(CalculationRequest template,
                                                double tStart, double tEnd, double tStep) throws IOException {
        ArrayList<CalculationResult> results = new ArrayList<>();
        for (double t = tStart; t <= tEnd; t += tStep) {
            template.setT(t);
            results.add(calculationService.runCalculation(template));
        }
        return results;
    }
}
