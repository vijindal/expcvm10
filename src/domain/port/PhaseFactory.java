package domain.port;

import java.io.IOException;
import phase.PHASEBINCE;

/**
 * Port interface for creating phase model objects.
 * Infrastructure layer provides the concrete implementation.
 */
public interface PhaseFactory {

    /**
     * Create a phase model instance by its identifiers.
     *
     * @param phaseType   e.g. "A1", "A2", "L", "SC"
     * @param phaseModel  e.g. "RK", "TO", "T", "ORC", "QT", "STCOMP"
     * @param phaseInstance e.g. "1", "2"
     * @param stdst       standard-state parameter array
     * @param edis        energy parameters
     * @param eMatFileName transformation matrix file name
     * @param mList       model parameter array
     * @param T           initial temperature
     * @param xB          initial composition
     * @return the constructed phase, or null if identifiers are unrecognised
     */
    PHASEBINCE createPhase(String phaseType, String phaseModel, String phaseInstance,
                           String[] stdst, double[] edis, String eMatFileName,
                           double[] mList, double T, double xB) throws IOException;
}
