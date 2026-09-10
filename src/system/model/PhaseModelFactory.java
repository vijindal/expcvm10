package system.model;

import system.database.tdb;
import system.model.cef.CefGibbs;

import java.util.List;
import java.util.Map;

/**
 * Thin compatibility shim for constructing phase Gibbs-energy models from
 * a TDB database.
 *
 * <p>The CEF model now builds itself directly from a database, in the
 * style of pycalphad's {@code Model(dbe, comps, phase_name)} — see
 * {@link CefGibbs#CefGibbs(tdb, List, String)}. This class exists only so
 * existing callers keep compiling; both {@link #build} and
 * {@link #toGibbsModel} just forward to / return a {@link CefGibbs}, which
 * is itself a {@link GibbsEnergyModel}.
 *
 * <p>Every phase is represented with the Compound Energy Formalism,
 * regardless of sublattice count (a one-sublattice substitutional
 * solution is the CEF 1-sublattice special case).
 * {@link PhaseModelKind#CVM} is reserved for a future Cluster Variation
 * Method model and currently throws {@link UnsupportedOperationException}.
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
}
