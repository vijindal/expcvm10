package ui.layer;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Use case: validate fitted models against reference datasets.
 * Track A — Assessment workflow.
 */
public class ValidateModelUseCase {

    private static final Logger LOG = Logger.getLogger(ValidateModelUseCase.class.getName());

    private final ModelInspectionService modelInspectionService;

    public ValidateModelUseCase(ModelInspectionService modelInspectionService) {
        this.modelInspectionService = modelInspectionService;
    }

    /**
     * Run CalModel validation on experimental data and compare with model output.
     */
    public void execute(String exptDataFile, String phaseDataFile) throws IOException {
        LOG.info("ValidateModelUseCase: executing model validation");
        modelInspectionService.runCalModel(exptDataFile, phaseDataFile);
    }
}
