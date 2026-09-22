package system.model.cvm;

import system.database.tdb;
import system.database.UnaryGibbsBuilder;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.cvm.gen.CvmGeometryGenerator;
import system.model.cvm.gen.GeneratedCvmGeometry;
import system.model.cvm.gen.GeneratedCvmPhaseDataAdapter;
import system.model.unary.ElementGibbs;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link CvmGibbsModel} from a loaded TDB database by:
 * <ol>
 *   <li>Extracting CVM parameters via {@code tdb.getCvmParams(...)}</li>
 *   <li>Converting them to CecTerms via {@code TdbCvmParameterConverter}</li>
 *   <li>Generating phase geometry via {@code CvmGeometryGenerator}</li>
 *   <li>Converting geometry to {@code CvmPhaseData} via adapter</li>
 *   <li>Obtaining GHSER reference energies via {@code UnaryGibbsBuilder}</li>
 *   <li>Assembling a {@link CvmPhaseSpec} and building the model</li>
 * </ol>
 *
 * <p>This is the entry point for TDB → CvmGibbsModel construction.
 * Currently supports BCC_A2 (T approximation) with binary or ternary systems;
 * extension to other structures/approximations requires corresponding CVCF
 * basis registration in {@link system.model.cvm.gen.CvCfBasis}.
 */
public final class TdbCvmModelBuilder {

    private TdbCvmModelBuilder() {}

    /**
     * Constructs a {@link CvmGibbsModel} from a TDB database.
     *
     * @param database    loaded TDB with G_CVM parameters and GHSER data
     * @param elements    ordered system elements (e.g., ["V", "ZR"])
     * @param phaseName   phase to construct (e.g., "BCC_A2")
     * @return            a fully operational CvmGibbsModel
     * @throws IllegalArgumentException if phase/elements not found or
     *                    CVM parameters are malformed
     * @throws IllegalStateException if GHSER data missing for any element
     */
    public static GibbsEnergyModel buildTdbCvmModel(
            tdb database,
            List<String> elements,
            String phaseName) {

        if (database == null)
            throw new NullPointerException("database must not be null");
        if (elements == null)
            throw new NullPointerException("elements must not be null");
        if (phaseName == null || phaseName.isBlank())
            throw new IllegalArgumentException("phaseName must not be blank");

        // Step 1: Extract G_CVM parameters from TDB
        ArrayList<String> elementList = new ArrayList<>(elements);
        ArrayList<tdb.Parameter> cvmParams = database.getCvmParams(elementList, phaseName);

        if (cvmParams.isEmpty())
            throw new IllegalArgumentException(
                    "No G_CVM parameters found for phase '" + phaseName
                    + "' with elements " + elements);

        // Step 2: Convert TDB parameters to CecTerms
        List<CecTerm> cecTerms = TdbCvmParameterConverter.toCecTerms(database, cvmParams);

        // Step 2b: Map TDB energy names (e...) to CVM variable names (v...)
        cecTerms = remapCecTermNames(cecTerms);

        // Step 3: Generate CVM phase geometry and convert to CvmPhaseData
        CvmPhaseData phaseData = generateCvmPhaseData(phaseName, elements);

        // Step 4: Load GHSER reference energies for all elements
        ElementGibbs[] ghserArray = loadGhserArray(database, elements);

        // Step 5: Assemble spec and build model
        CvmPhaseSpec spec = new CvmPhaseSpec(phaseData, cecTerms, ghserArray, elements);
        return PhaseModelFactory.buildCvm(spec);
    }

    /**
     * Generates CVM phase geometry and converts to production CvmPhaseData.
     */
    private static CvmPhaseData generateCvmPhaseData(String phaseName, List<String> elements) {
        // Currently only BCC_A2 (T approximation) is supported
        if (!"BCC_A2".equalsIgnoreCase(phaseName)) {
            throw new IllegalArgumentException(
                    "CVM geometry not yet available for phase '" + phaseName
                    + "'; supported: BCC_A2");
        }

        // Build element symbol string for generator (e.g., "V-ZR")
        String elementSymbols = String.join("-", elements).toUpperCase();

        // Generate geometry (no progress reporting for now)
        GeneratedCvmGeometry geo = CvmGeometryGenerator.generateBccA2(
                elementSymbols, elements.size(), null);

        // Convert to production CvmPhaseData
        return GeneratedCvmPhaseDataAdapter.toCvmPhaseData(geo);
    }

    /**
     * Remaps TDB energy names (e...) to CVM variable names (v...).
     * E.g.: e4AB → v4AB, e3AB → v3AB, e22AB → v22AB, e21AB → v21AB.
     */
    private static List<CecTerm> remapCecTermNames(List<CecTerm> cecTerms) {
        List<CecTerm> remapped = new ArrayList<>();
        for (CecTerm term : cecTerms) {
            String remappedName = remapEnergyNameToVarName(term.name);
            remapped.add(new CecTerm(remappedName, term.a, term.b));
        }
        return remapped;
    }

    /**
     * Maps a TDB energy identifier (e...) to the corresponding variable name (v...).
     * For now, the mapping is: e-prefix → v-prefix, everything else unchanged.
     */
    private static String remapEnergyNameToVarName(String energyName) {
        if (energyName.startsWith("e") && energyName.length() > 1) {
            // e4AB → v4AB, e3AB → v3AB, etc.
            return "v" + energyName.substring(1);
        }
        return energyName; // No remapping needed for other names
    }

    /**
     * Loads GHSER reference energies for all elements.
     */
    private static ElementGibbs[] loadGhserArray(tdb database, List<String> elements) {
        ElementGibbs[] ghser = new ElementGibbs[elements.size()];

        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            ghser[i] = UnaryGibbsBuilder.build(element, database);
        }

        return ghser;
    }
}
