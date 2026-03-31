package ui.layer;

import contracts.EquilibriumResult;
import ui.request.CalculationRequest;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Use case: single-point equilibrium calculation.
 * Delegates to {@link EquilibriumUseCase}.
 */
public class SinglePointUseCase {

    private static final Logger LOG = Logger.getLogger(SinglePointUseCase.class.getName());

    private final EquilibriumUseCase equilibriumUseCase;

    public SinglePointUseCase() {
        this.equilibriumUseCase = new EquilibriumUseCase();
    }

    public EquilibriumResult execute(CalculationRequest request) throws IOException {
        LOG.info("SinglePointUseCase: executing single-point calculation");
        return equilibriumUseCase.execute(request);
    }
}
