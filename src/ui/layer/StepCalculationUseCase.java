package ui.layer;

import calc.diagram.AxisConfig;
import calc.diagram.DiagramTracer;
import calc.diagram.PhaseDiagram;
import contracts.DatabasePort;
import system.model.GibbsEnergyModel;
import ui.request.PhaseDiagramRequest;
import ui.result.PhaseDiagramResult;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Use case: STEP calculation — vary one condition, compute equilibrium at each step.
 * Uses {@link DiagramTracer} to compute a 1-axis phase diagram.
 */
public class StepCalculationUseCase {

    private static final Logger LOG = Logger.getLogger(StepCalculationUseCase.class.getName());

    private final DiagramTracer tracer;
    private final PhaseDiagramUseCase diagramUseCase;

    public StepCalculationUseCase() {
        this.tracer = new DiagramTracer();
        this.diagramUseCase = new PhaseDiagramUseCase();
    }

    /**
     * Execute a STEP calculation: vary temperature, compute equilibrium.
     * @param request  base request with one-axis configuration
     * @return diagram result with phase boundaries
     */
    public PhaseDiagramResult execute(PhaseDiagramRequest request) throws IOException {
        LOG.info("StepCalculationUseCase: executing STEP diagram");
        // Delegate to PhaseDiagramUseCase which uses DiagramTracer internally
        return diagramUseCase.execute(request);
    }
}
