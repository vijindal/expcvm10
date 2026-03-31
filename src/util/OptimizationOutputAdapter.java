package util;

import legacy.calbince.ExptData;
import legacy.calbince.PhaseData;
import contracts.OptimizationOutputPort;
import util.DataPrinter;

import java.io.IOException;

/**
 * Infrastructure adapter for optimization output via the legacy DataPrinter.
 */
public class OptimizationOutputAdapter implements OptimizationOutputPort {

    @Override
    public OutputWriter createWriter(String filePrefix, int logLevel) throws IOException {
        return new DataPrinterWriter(filePrefix, logLevel);
    }

    /**
     * Writer implementation wrapping DataPrinter.
     */
    private static class DataPrinterWriter implements OutputWriter {
        private final DataPrinter dataPrinter;

        DataPrinterWriter(String filePrefix, int logLevel) throws IOException {
            this.dataPrinter = new DataPrinter(filePrefix, logLevel);
        }

        @Override
        public void initOptimization(ExptData exptdata, PhaseData phasedata) throws IOException {
            dataPrinter.initOpt(exptdata, phasedata);
        }

        @Override
        public void finalizeOptimization(PhaseData phasedata) throws IOException {
            dataPrinter.finalOpt(phasedata);
        }
    }
}
