package application.service;

import calbince.*;
import domain.port.LoggingPort;
import domain.port.OptimizationOutputPort;
import infrastructure.logging.AppLevel;
import infrastructure.logging.Trace;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application service facade for parameter optimization workflows.
 * Wraps the existing OptMrq/Mrqcof logic (Track A: Assessment).
 */
public class OptimizationService {

    private static final Logger LOG = Logger.getLogger(OptimizationService.class.getName());
    private final LoggingPort logger;
    private final OptimizationOutputPort outputPort;

    /**
     * Constructor with dependency injection.
     * @param logger port for logging messages
     * @param outputPort port for writing optimization outputs
     */
    public OptimizationService(LoggingPort logger, OptimizationOutputPort outputPort) {
        this.logger = logger;
        this.outputPort = outputPort;
    }

    /**
     * Run Levenberg-Marquardt optimization from data files.
     *
     * @param exptDataFile  path to experimental data file
     * @param phaseDataFile path to phase data file
     * @param filePrefix    output file prefix
     * @param maxIterations maximum number of fit iterations
     */
    public void runOptimization(String exptDataFile, String phaseDataFile,
                                String filePrefix, int maxIterations) throws IOException {
        Trace.enter(LOG, AppLevel.FLOW, "OptimizationService", "runOptimization");
        LOG.log(AppLevel.RESULT, "Optimization: exptData={0}, phaseData={1}, maxIter={2}",
                new Object[]{exptDataFile, phaseDataFile, maxIterations});
        int logLevel = 1;
        OptimizationOutputPort.OutputWriter writer = outputPort.createWriter(filePrefix, logLevel);

        PhaseData phasedata = new PhaseData(phaseDataFile);
        phasedata.readPhaseDataInputFile();
        phasedata.print();

        ExptData exptdata = new ExptData(exptDataFile);
        exptdata.readExptDataFile();
        exptdata.check(phasedata);

        phasedata.setFitParam();
        exptdata.print();
        phasedata.print();
        phasedata.printFitParam();
        writer.initOptimization(exptdata, phasedata);

        logger.log("Simultaneous optimization begins...", 0);
        LOG.log(AppLevel.RESULT, "Optimization starting: maxIterations={0}", maxIterations);
        OptMrq optmrq = new OptMrq(exptdata, phasedata);
        optmrq.fit(maxIterations);
        LOG.log(AppLevel.RESULT, "Optimization completed.");
        logger.log("Simultaneous optimization calculations ended", 0);

        writer.finalizeOptimization(phasedata);
        logger.log("Finished Execution", 0);
        Trace.exit(LOG, AppLevel.FLOW, "OptimizationService", "runOptimization");
    }
}
