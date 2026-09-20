package system.model.cvm.gen;

import system.model.cvm.CecTerm;
import system.model.cvm.CvmGibbsModel;
import system.model.cvm.CvmPhaseData;
import system.model.cvm.CvmPhaseSpec;
import system.model.unary.ElementGibbs;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for constructing {@link CvmGibbsModel} instances from
 * {@link GeneratedCvmGeometry}, bridging the geometry layer to the
 * thermodynamic model layer.
 *
 * <p>Encapsulates the pipeline: generated geometry → CvmPhaseData
 * (via adapter) → CvmPhaseSpec → CvmGibbsModel, with support for
 * customizing element symbols and CEC (cluster expansion coefficient)
 * terms.</p>
 */
public final class GeneratedCvmModelFactory {

    private GeneratedCvmModelFactory() {}

    /**
     * Constructs a {@link CvmGibbsModel} from a generated geometry,
     * CEC terms, and unary reference data.
     *
     * @param geo       the generated CVM geometry (Stages 1-4)
     * @param cecTerms  named, temperature-dependent CEC terms; must cover
     *                  exactly the non-point CF set in the generated geometry
     * @param ghser     SGTE reference-energy evaluators, one per component,
     *                  in the same order as geo's element symbols
     * @param elements  element symbols corresponding to the compositions,
     *                  same order as ghser
     * @return a ready-to-use CvmGibbsModel
     * @throws IllegalArgumentException if sizes don't match
     */
    public static CvmGibbsModel buildCvmGibbsModel(
            GeneratedCvmGeometry geo,
            List<CecTerm> cecTerms,
            ElementGibbs[] ghser,
            List<String> elements) {

        if (ghser.length != geo.numComponents) {
            throw new IllegalArgumentException(
                    "ghser.length=" + ghser.length + " != numComponents=" + geo.numComponents);
        }
        if (elements.size() != geo.numComponents) {
            throw new IllegalArgumentException(
                    "elements.size()=" + elements.size() + " != numComponents=" + geo.numComponents);
        }

        // Bridge generated geometry to CvmPhaseData
        CvmPhaseData phaseData = GeneratedCvmPhaseDataAdapter.toCvmPhaseData(geo);

        // Construct CvmPhaseSpec from phase data, CEC terms, and unary data
        CvmPhaseSpec spec = new CvmPhaseSpec(phaseData, cecTerms, ghser, elements);

        // Build and return the model
        return spec.toModel();
    }

    /**
     * Convenience overload that extracts element symbols from the
     * generated geometry (expected to be hyphen-separated, e.g., "A-B").
     *
     * @param geo      the generated CVM geometry
     * @param cecTerms named CEC terms
     * @param ghser    SGTE reference evaluators
     * @return a ready-to-use CvmGibbsModel
     */
    public static CvmGibbsModel buildCvmGibbsModel(
            GeneratedCvmGeometry geo,
            List<CecTerm> cecTerms,
            ElementGibbs[] ghser) {

        List<String> elements = parseElementSymbols(geo.elements, geo.numComponents);
        return buildCvmGibbsModel(geo, cecTerms, ghser, elements);
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private static List<String> parseElementSymbols(String hyphenSeparated, int expectedCount) {
        String[] parts = hyphenSeparated.split("-");
        if (parts.length != expectedCount) {
            throw new IllegalArgumentException(
                    "Element string '" + hyphenSeparated + "' has " + parts.length
                            + " components, expected " + expectedCount);
        }
        return new ArrayList<>(java.util.Arrays.asList(parts));
    }
}
