package system.model;

import system.database.tdb;
import system.model.cef.CefGibbs;
import system.model.cvm.CvmPhaseSpec;
import system.model.cvm.TdbCvmModelBuilder;

import java.util.List;
import java.util.Map;

/**
 * Constructs phase {@link GibbsEnergyModel}s from a TDB database.
 *
 * <p>Supports both CEF (via {@link CefGibbs}) and CVM (via
 * {@link TdbCvmModelBuilder}) through the TDB-driven path. Callers should
 * use {@link PhaseModelAvailability#availableModels} to discover which
 * models are available, then explicitly request one via {@link #buildCef}
 * or {@link #buildCvm}.
 *
 * <p>Legacy {@link #build(String, tdb, List, Map, Map, PhaseModelKind)}
 * entry points remain for backward compatibility.
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

    /**
     * Legacy direct CEF construction (end-member references).
     *
     * <p><b>Backward compatibility:</b> Builds CEF using historical end-member
     * reference path directly from TDB, without loading phase-specific unary
     * references via PhaseUnaryGibbsExtractor.
     *
     * <p>For TDB-driven construction that shares unary references with CVM,
     * use {@link #buildCefFromTdb} instead.
     *
     * @param phaseName phase name
     * @param database  loaded TDB
     * @param elements  ordered system elements
     * @param affMap    magnetic A-function map (may be null)
     * @param pMap      magnetic p-function map (may be null)
     * @return          the CEF model
     * @throws IllegalArgumentException if CEF parameters are not found
     */
    public static GibbsEnergyModel buildCef(
            String phaseName,
            tdb database,
            List<String> elements,
            Map<String, Double> affMap,
            Map<String, Double> pMap) {

        return build(phaseName, database, elements, affMap, pMap, PhaseModelKind.CEF);
    }

    /**
     * TDB-driven CEF construction with mandatory shared unary references.
     *
     * <p><b>TDB-driven path:</b> Uses phase-specific unary references
     * via {@link system.database.PhaseUnaryGibbsExtractor} for consistency with
     * CVM models. This ensures G_ref_CEF == G_ref_CVM when both models are
     * available for the same phase/element set.
     *
     * <p>Mandatory references: Does not fall back to end-member computation if
     * unary references cannot be loaded. Throws a clear exception if the shared
     * reference path fails.
     *
     * <p>Caller is responsible for verifying that CEF is available via
     * {@link PhaseModelAvailability#isAvailable}. No auto-fallback occurs
     * if CEF or reference parameters are missing; the constructor will throw.
     *
     * @param phaseName phase name
     * @param database  loaded TDB
     * @param elements  ordered system elements
     * @param affMap    magnetic A-function map (may be null)
     * @param pMap      magnetic p-function map (may be null)
     * @return          the CEF model using common unary references
     * @throws IllegalArgumentException if CEF or unary reference data not found
     */
    public static GibbsEnergyModel buildCefFromTdb(
            String phaseName,
            tdb database,
            List<String> elements,
            Map<String, Double> affMap,
            Map<String, Double> pMap) {

        return CefGibbs.buildCefFromTdbWithCommonReferences(
                database, elements, phaseName, affMap, pMap, PhaseModelKind.CEF);
    }

    /**
     * Explicitly builds a CVM model for the given phase/element set via TDB.
     *
     * <p>Caller is responsible for verifying that CVM is available via
     * {@link PhaseModelAvailability#isAvailable}. Throws clearly if CVM
     * parameters are not found.
     *
     * @param database  loaded TDB with G_CVM parameters
     * @param elements  ordered system elements
     * @param phaseName phase name
     * @return          the CVM model
     * @throws IllegalArgumentException if G_CVM parameters are not found
     * @throws IllegalStateException if GHSER data is missing
     */
    public static GibbsEnergyModel buildCvmFromTdb(
            tdb database,
            List<String> elements,
            String phaseName) {

        return TdbCvmModelBuilder.buildTdbCvmModel(database, elements, phaseName);
    }
}
