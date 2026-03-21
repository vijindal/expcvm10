package application.calculation;

import application.dto.CalculationRequest;
import application.dto.CalculationResult;
import application.service.CalculationService;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Use case: map calculation — vary two conditions (e.g. T and composition).
 * Track B — Calculation workflow.
 */
public class MapCalculationUseCase {

    private final CalculationService calculationService;

    public MapCalculationUseCase(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    /**
     * Run a 2D grid of calculations over T and composition.
     *
     * @param template base request
     * @param tStart   start temperature
     * @param tEnd     end temperature
     * @param tStep    temperature step
     * @param xStart   start composition (component index 0)
     * @param xEnd     end composition
     * @param xStep    composition step
     * @return list of results
     */
    public ArrayList<CalculationResult> execute(CalculationRequest template,
                                                double tStart, double tEnd, double tStep,
                                                double xStart, double xEnd, double xStep) throws IOException {
        ArrayList<CalculationResult> results = new ArrayList<>();
        for (double t = tStart; t <= tEnd; t += tStep) {
            for (double x = xStart; x <= xEnd; x += xStep) {
                template.setT(t);
                // Update composition in first phase, first component
                if (template.getCompositions() != null && !template.getCompositions().isEmpty()) {
                    template.getCompositions().get(0).set(0, x);
                    if (template.getCompositions().get(0).size() > 1) {
                        template.getCompositions().get(0).set(1, 1.0 - x);
                    }
                }
                results.add(calculationService.runCalculation(template));
            }
        }
        return results;
    }

    /**
     * Phase-diagram-oriented alias of map execution.
     * Kept here to avoid a separate thin wrapper class.
     */
    public ArrayList<CalculationResult> executePhaseDiagram(CalculationRequest template,
                                                            double tStart, double tEnd, double tStep,
                                                            double xStart, double xEnd, double xStep) throws IOException {
        return execute(template, tStart, tEnd, tStep, xStart, xEnd, xStep);
    }
}
