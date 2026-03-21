package infra;

import domain.PhaseFactory;
import phase.PHASEBINCE;
// REMOVED: import phase.calphad.RKBince; (consolidated to RKPhaseGeneral)
import phase.calphad.STCOMP;
import phase.cecvm.A1QTBINCE;
import phase.cecvm.A1TOBINCE;
import phase.cecvm.A2ORCBINCE;
import phase.cecvm.A2TBINCE;
import phase.cecvm.A3TOBINCE;
import phase.cecvm.B19TOBINCE;
import phase.cecvm.B2TBINCE;
import phase.cecvm.D019TOBINCE;
import phase.cecvm.L10TOBINCE;
import phase.cecvm.L12TOBINCE;

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
                    // REMOVED: case "RK": (consolidated to RKPhaseGeneral)
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
                    // REMOVED: case "RK": (consolidated to RKPhaseGeneral)
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
                    // REMOVED: case "RK": (consolidated to RKPhaseGeneral)
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
                // REMOVED: RK case (consolidated to RKPhaseGeneral)
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
