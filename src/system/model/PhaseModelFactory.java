package system.model;

import system.database.tdb;
import system.model.cef.CefGibbs;
import system.model.cvm.CvmPhaseSpec;

import java.util.List;
import java.util.Map;

/**
 * Constructs phase {@link GibbsEnergyModel}s: {@link CefGibbs} from a TDB
 * database via {@link #build}, or a CVM model from an explicit
 * {@link CvmPhaseSpec} via {@link #buildCvm} (CVM has no TDB grammar yet).
 * {@link PhaseModelKind#CVM} through the TDB path still throws
 * {@link UnsupportedOperationException}.
 */
public final class PhaseModelFactory {

    private PhaseModelFactory() {}

    /**
     * Builds a CEF phase model from the supplied TDB. Equivalent to
     * {@link #build(String, tdb, List, Map, Map, PhaseModelKind)} with
     * {@link PhaseModelKind#AUTO}.
     */
    public static CefGibbs build(String phaseName,
                                 tdb database,
                                 List<String> elements,
                                 Map<String, Double> affMap,
                                 Map<String, Double> pMap) {
        return build(phaseName, database, elements, affMap, pMap, PhaseModelKind.AUTO);
    }

    /**
     * Builds a CEF phase model from the supplied TDB.
     *
     * @param phaseName phase name
     * @param database  loaded and element-filtered TDB
     * @param elements  ordered system elements
     * @param affMap    magnetic A-function map (may be null)
     * @param pMap      magnetic p-function map (may be null)
     * @param kind      which model to use. {@link PhaseModelKind#AUTO} and
     *                  {@link PhaseModelKind#CEF} both build a
     *                  {@link CefGibbs}; {@link PhaseModelKind#CVM} throws.
     * @return the constructed model
     */
    public static CefGibbs build(String phaseName,
                                 tdb database,
                                 List<String> elements,
                                 Map<String, Double> affMap,
                                 Map<String, Double> pMap,
                                 PhaseModelKind kind) {
        return new CefGibbs(database, elements, phaseName, affMap, pMap, kind);
    }

    /**
     * Identity pass-through: a {@link CefGibbs} already is the
     * {@link GibbsEnergyModel} used by the equilibrium solver.
     */
    public static GibbsEnergyModel toGibbsModel(CefGibbs model, List<String> elements) {
        return model;
    }

    /**
     * Builds a CVM phase model from an explicit {@link CvmPhaseSpec}
     * (there is no TDB-driven path for CVM yet).
     */
    public static GibbsEnergyModel buildCvm(CvmPhaseSpec spec) {
        return spec.toModel();
    }
}
