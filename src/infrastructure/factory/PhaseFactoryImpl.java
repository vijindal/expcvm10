package infrastructure.factory;

import domain.port.PhaseFactory;
import phase.PHASEBINCE;
import phase.solution.calphad.RK;
import phase.solution.calphad.STCOMP;
import phase.solution.cecvm.A1QTBINCE;
import phase.solution.cecvm.A1TOBINCE;
import phase.solution.cecvm.A2ORCBINCE;
import phase.solution.cecvm.A2TBINCE;
import phase.solution.cecvm.A3TOBINCE;
import phase.solution.cecvm.B19TOBINCE;
import phase.solution.cecvm.B2TBINCE;
import phase.solution.cecvm.D019TOBINCE;
import phase.solution.cecvm.L10TOBINCE;
import phase.solution.cecvm.L12TOBINCE;

import java.io.IOException;

/**
 * Concrete factory that maps phase type/model identifiers to phase model instances.
 * Extracted from PhaseData.genPhase().
 */
public class PhaseFactoryImpl implements PhaseFactory {

    @Override
    public PHASEBINCE createPhase(String phaseType, String phaseModel, String phaseInstance,
                                  String[] stdst, double[] edis, String eMatFileName,
                                  double[] mList, double T, double xB) throws IOException {
        switch (phaseType) {
            case "A1": {
                switch (phaseModel) {
                    case "RK":
                        return new RK(stdst, edis, T, xB);
                    case "TO":
                        return new A1TOBINCE(stdst, edis, eMatFileName, mList, T, xB);
                    case "QT":
                        return new A1QTBINCE(stdst, edis, eMatFileName, mList, T, xB);
                }
                break;
            }
            case "L10": {
                if ("TO".equals(phaseModel)) {
                    return new L10TOBINCE(stdst, edis, eMatFileName, mList, T, xB);
                }
                break;
            }
            case "L12": {
                if ("TO".equals(phaseModel)) {
                    return new L12TOBINCE(stdst, edis, eMatFileName, mList, T, xB);
                }
                break;
            }
            case "A2": {
                switch (phaseModel) {
                    case "RK":
                        return new RK(stdst, edis, T, xB);
                    case "T":
                        return new A2TBINCE(stdst, edis, eMatFileName, mList, T, xB);
                    case "ORC":
                        return new A2ORCBINCE(stdst, edis, eMatFileName, T, xB);
                }
                break;
            }
            case "B2": {
                if ("T".equals(phaseModel)) {
                    return new B2TBINCE(stdst, edis, eMatFileName, mList, T, xB);
                }
                break;
            }
            case "A3": {
                switch (phaseModel) {
                    case "RK":
                        return new RK(stdst, edis, T, xB);
                    case "TO":
                        return new A3TOBINCE(stdst, edis, eMatFileName, mList, T, xB);
                }
                break;
            }
            case "B19": {
                if ("TO".equals(phaseModel)) {
                    return new B19TOBINCE(stdst, edis, eMatFileName, mList, T, xB);
                }
                break;
            }
            case "D019": {
                if ("TO".equals(phaseModel)) {
                    return new D019TOBINCE(stdst, edis, eMatFileName, mList, T, xB);
                }
                break;
            }
            case "L": {
                if ("RK".equals(phaseModel)) {
                    return new RK(stdst, edis, T, xB);
                }
                break;
            }
            case "SC": {
                if ("STCOMP".equals(phaseModel)) {
                    return new STCOMP(stdst, edis, T, xB);
                }
                break;
            }
        }
        return null; // unrecognised phase
    }
}
