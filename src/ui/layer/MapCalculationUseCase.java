package ui.layer;

import calc.diagram.DiagramTracer;
import contracts.DatabasePort;
import system.model.GibbsEnergyModel;
import ui.request.PhaseDiagramRequest;
import ui.result.PhaseDiagramResult;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Use case: MAP calculation — vary two conditions (e.g. T and composition).
 * Uses {@link DiagramTracer} to compute a 2-axis phase diagram.
 */
public class MapCalculationUseCase {

    private static final Logger LOG = Logger.getLogger(MapCalculationUseCase.class.getName());

    private final DiagramTracer tracer;
    private final PhaseDiagramUseCase diagramUseCase;

    public MapCalculationUseCase() {
        this.tracer = new DiagramTracer();
        this.diagramUseCase = new PhaseDiagramUseCase();
    }

    /**
     * Execute a MAP calculation: vary two conditions (T and composition).
     * @param request  request with two-axis configuration
     * @return diagram result with phase boundaries
     */
    public PhaseDiagramResult execute(PhaseDiagramRequest request) throws IOException {
        LOG.info("MapCalculationUseCase: executing MAP diagram");
        // Delegate to PhaseDiagramUseCase which uses DiagramTracer internally
        return diagramUseCase.execute(request);
    }
}
