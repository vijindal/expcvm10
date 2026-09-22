package system.model.cvm.gen;

import system.model.cvm.gen.ClusterCFIdentificationPipeline.PipelineResult;
import system.model.cvm.gen.structure.ApproximationDefinition;
import system.model.cvm.gen.structure.MaximalClusterGenerator;
import system.model.cvm.gen.structure.SpaceGroupGenerator;
import system.model.cvm.gen.structure.StructureDefinition;

import java.util.List;
import java.util.function.Consumer;

/**
 * High-level API for generating CVM geometry from structure/approximation
 * definitions, consolidating the entire Stages 1-4 pipeline into a single
 * entry point.
 *
 * <p>Replaces structure-specific builders like {@code buildBccA2()} with
 * a generic framework: any (structure, approximation, numComponents) triple
 * can be processed through the same machinery, provided:
 * <ul>
 *   <li>The structure has a {@link StructureDefinition}</li>
 *   <li>The approximation has an {@link ApproximationDefinition}</li>
 *   <li>CVCF definitions are registered for that combination in {@link CvCfBasis}</li>
 * </ul></p>
 *
 * <p>The old, hard-coded BCC_A2 methods are preserved as convenience
 * wrappers that delegate to this generator.</p>
 */
public final class CvmGeometryGenerator {

    private CvmGeometryGenerator() {}

    /**
     * Generates CVM geometry for any (structure, approximation, K) combination
     * supported by the registered CVCF basis definitions.
     *
     * @param structure        the crystallographic structure (e.g., BCC_A2)
     * @param approximation    the CVM approximation/model (e.g., T for tetrahedron)
     * @param numComponents    number of components K
     * @param elementSymbols   hyphen-separated element symbols, e.g. "A-B" or "Nb-Ti-V-Zr"
     * @param progressSink     optional progress callback (may be null)
     * @return the generated geometry
     * @throws IllegalArgumentException if the combination is not supported
     */
    public static GeneratedCvmGeometry generate(
            StructureDefinition structure,
            ApproximationDefinition approximation,
            int numComponents,
            String elementSymbols,
            Consumer<String> progressSink) {

        // Stage 1-3: Generate cluster orbits and symmetry-reduced cluster
        // variables from the structure definition.
        List<Cluster> maximalClusters = MaximalClusterGenerator.generate(structure, approximation);
        maximalClusters.replaceAll(Cluster::sorted);
        maximalClusters.replaceAll(ClusterCoordinateNormalizer::normalizeToOrigin);

        SpaceGroup spaceGroup = SpaceGroupGenerator.generate(structure);

        // Stages 1-3 pipeline
        PipelineResult pr = ClusterCFIdentificationPipeline.run(
                maximalClusters, spaceGroup.getOperations(),
                maximalClusters, spaceGroup.getOperations(),
                spaceGroup.getRotateMat(), spaceGroup.getTranslateMat(),
                numComponents, progressSink);

        CMatrixPipeline.CMatrixData cmatOrth = CMatrixPipeline.run(
                pr, maximalClusters, numComponents, progressSink);

        // Stage 4: CVCF basis transformation
        CvCfBasis basis = CvCfBasis.generate(
                structure.getName(), pr, cmatOrth, approximation.getName(), progressSink);

        // Package into GeneratedCvmGeometry immutable value
        return GeneratedCvmGeometry.create(
                elementSymbols, structure.getName(), numComponents,
                pr.getTcdis(), pr.getKbdis(), pr.getMhdis(), pr.getMh(), pr.getLc(), pr,
                basis.cvcfCMatrixData.getCmat(), basis.cvcfCMatrixData.getLcv(),
                basis.cvcfCMatrixData.getWcv(),
                basis, basis.numNonPointCfs, basis.totalCfs());
    }

    /**
     * Convenience method for generating BCC_A2 / T-model (tetrahedron) geometry.
     *
     * <p>Currently uses pre-computed cluster files from {@code inputs/clus/BCC_A2-T.txt}
     * rather than deriving them from structure definition, for compatibility with
     * existing validated CVCF registrations. The clusters are normalized to a
     * canonical reference frame for Stage 4 (CVCF basis transformation).</p>
     */
    public static GeneratedCvmGeometry generateBccA2(
            String elementSymbols,
            int numComponents,
            Consumer<String> progressSink) {
        // Use file-based clusters (for now, while generic generation is in development)
        List<Cluster> maximalClusters = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        maximalClusters.replaceAll(Cluster::sorted);
        maximalClusters.replaceAll(ClusterCoordinateNormalizer::normalizeToOrigin);
        SpaceGroup spaceGroup = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");

        // Stages 1-3 pipeline
        PipelineResult pr = ClusterCFIdentificationPipeline.run(
                maximalClusters, spaceGroup.getOperations(),
                maximalClusters, spaceGroup.getOperations(),
                spaceGroup.getRotateMat(), spaceGroup.getTranslateMat(),
                numComponents, progressSink);

        CMatrixPipeline.CMatrixData cmatOrth = CMatrixPipeline.run(
                pr, maximalClusters, numComponents, progressSink);

        // Stage 4: CVCF basis transformation
        CvCfBasis basis = CvCfBasis.generate("BCC_A2", pr, cmatOrth, "T", progressSink);

        // Package into GeneratedCvmGeometry immutable value
        return GeneratedCvmGeometry.create(
                elementSymbols, "BCC_A2", numComponents,
                pr.getTcdis(), pr.getKbdis(), pr.getMhdis(), pr.getMh(), pr.getLc(), pr,
                basis.cvcfCMatrixData.getCmat(), basis.cvcfCMatrixData.getLcv(),
                basis.cvcfCMatrixData.getWcv(),
                basis, basis.numNonPointCfs, basis.totalCfs());
    }

    /**
     * Convenience method for generating binary BCC_A2 / T-model geometry.
     * Equivalent to: {@code generateBccA2("A-B", 2, progressSink)}
     */
    public static GeneratedCvmGeometry generateBccA2Binary(Consumer<String> progressSink) {
        return generateBccA2("A-B", 2, progressSink);
    }
}
