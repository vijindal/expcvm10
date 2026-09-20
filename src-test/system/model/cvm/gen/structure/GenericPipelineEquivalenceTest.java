package system.model.cvm.gen.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import system.model.cvm.gen.CMatrixPipeline;
import system.model.cvm.gen.Cluster;
import system.model.cvm.gen.ClusterCFIdentificationPipeline;
import system.model.cvm.gen.ClusterCFIdentificationPipeline.PipelineResult;
import system.model.cvm.gen.SpaceGroup;
import system.model.cvm.gen.StructureFileLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test F (adaptation-layer equivalence): compares the Stages 1-3 result
 * (cluster/CF identification + orthogonal C-matrix -- structure-agnostic,
 * no Hamiltonian/CVCF/thermodynamics involved) obtained from {@link
 * MaximalClusterGenerator}/{@link SpaceGroupGenerator}'s generated
 * BCC_A2 cluster and space group, against the same Stages 1-3 result
 * obtained from the file-based {@link StructureFileLoader} reference.
 *
 * <p>This isolates exactly the new generation layer introduced in Step 10
 * (structure -&gt; cluster -&gt; symmetry/orbit) from everything above it
 * (CVCF basis registration, the Hamiltonian, cluster-variable-entropy
 * evaluation), which is unmodified, already validated elsewhere, and not
 * re-tested here.</p>
 */
class GenericPipelineEquivalenceTest {

    private static PipelineResult runPipeline(List<Cluster> maxClusters, SpaceGroup sg, int numComponents) {
        return ClusterCFIdentificationPipeline.run(
                maxClusters, sg.getOperations(), maxClusters, sg.getOperations(),
                sg.getRotateMat(), sg.getTranslateMat(), numComponents, null);
    }

    @Test
    void dimensionsMatchFileBasedReference() {
        List<Cluster> generatedClusters = MaximalClusterGenerator.generate(
                StructureDefinition.bccA2(), ApproximationDefinition.tetrahedron());
        SpaceGroup generatedSg = SpaceGroupGenerator.generate(StructureDefinition.bccA2());
        PipelineResult generated = runPipeline(generatedClusters, generatedSg, 2);

        List<Cluster> fileClusters = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        fileClusters.replaceAll(Cluster::sorted);
        SpaceGroup fileSg = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");
        PipelineResult fileBased = runPipeline(fileClusters, fileSg, 2);

        assertEquals(fileBased.getTcdis(), generated.getTcdis(), "tcdis");
        assertEquals(fileBased.getTcf(), generated.getTcf(), "tcf");
        assertEquals(fileBased.getNcf(), generated.getNcf(), "ncf");
        assertArrayEquals(fileBased.getLc(), generated.getLc(), "lc");
    }

    @Test
    void multiplicitiesAndKbCoefficientsMatchFileBasedReference() {
        List<Cluster> generatedClusters = MaximalClusterGenerator.generate(
                StructureDefinition.bccA2(), ApproximationDefinition.tetrahedron());
        SpaceGroup generatedSg = SpaceGroupGenerator.generate(StructureDefinition.bccA2());
        PipelineResult generated = runPipeline(generatedClusters, generatedSg, 2);

        List<Cluster> fileClusters = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        fileClusters.replaceAll(Cluster::sorted);
        SpaceGroup fileSg = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");
        PipelineResult fileBased = runPipeline(fileClusters, fileSg, 2);

        assertArrayEquals(fileBased.getMhdis(), generated.getMhdis(), 1e-9, "mhdis (Kikuchi-Barker multiplicities)");
        assertArrayEquals(fileBased.getKbdis(), generated.getKbdis(), 1e-9, "kb (Kikuchi-Baker coefficients)");
    }

    @Test
    void cMatrixShapesMatchFileBasedReference() {
        List<Cluster> generatedClusters = MaximalClusterGenerator.generate(
                StructureDefinition.bccA2(), ApproximationDefinition.tetrahedron());
        SpaceGroup generatedSg = SpaceGroupGenerator.generate(StructureDefinition.bccA2());
        PipelineResult generatedPr = runPipeline(generatedClusters, generatedSg, 2);
        CMatrixPipeline.CMatrixData generatedCm = CMatrixPipeline.run(generatedPr, generatedClusters, 2, null);

        List<Cluster> fileClusters = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        fileClusters.replaceAll(Cluster::sorted);
        SpaceGroup fileSg = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");
        PipelineResult filePr = runPipeline(fileClusters, fileSg, 2);
        CMatrixPipeline.CMatrixData fileCm = CMatrixPipeline.run(filePr, fileClusters, 2, null);

        assertEquals(fileCm.getCmat().size(), generatedCm.getCmat().size(), "cmat.size() (== tcdis)");
        for (int t = 0; t < fileCm.getCmat().size(); t++) {
            assertEquals(fileCm.getCmat().get(t).size(), generatedCm.getCmat().get(t).size(), "cluster count at t=" + t);
            assertArrayEquals(fileCm.getLcv()[t], generatedCm.getLcv()[t], "lcv[" + t + "]");
        }
    }

    @Test
    void quaternaryDimensionsMatchFileBasedReference() {
        // Same comparison at K=4 (the real Nb-Ti-V-Zr validation target's
        // component count), to confirm the generated cluster/symmetry
        // layer is not accidentally binary-only.
        List<Cluster> generatedClusters = MaximalClusterGenerator.generate(
                StructureDefinition.bccA2(), ApproximationDefinition.tetrahedron());
        SpaceGroup generatedSg = SpaceGroupGenerator.generate(StructureDefinition.bccA2());
        PipelineResult generated = runPipeline(generatedClusters, generatedSg, 4);

        List<Cluster> fileClusters = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        fileClusters.replaceAll(Cluster::sorted);
        SpaceGroup fileSg = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");
        PipelineResult fileBased = runPipeline(fileClusters, fileSg, 4);

        assertEquals(fileBased.getNcf(), generated.getNcf(), "ncf");
        assertEquals(fileBased.getTcf(), generated.getTcf(), "tcf");
    }
}
