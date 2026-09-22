package system.model.cvm.gen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test A: cluster/structure generation. Verifies the BCC_A2 structure loads
 * correctly from the copied {@code inputs/clus}/{@code inputs/sym} files
 * and the Stage 1a/1b cluster-identification pipeline runs and produces the
 * expected cluster inventory.
 */
class BccA2StructureGenerationTest {

    @Test
    void loadsExpectedMaximalCluster() {
        List<Cluster> maxClusters = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        assertEquals(1, maxClusters.size(), "BCC_A2-T.txt declares one maximal cluster");

        Cluster tetra = maxClusters.get(0);
        assertEquals(1, tetra.getSublattices().size(), "disordered structure -> single sublattice");
        assertEquals(4, tetra.getAllSites().size(), "T-model maximal cluster is a 4-site tetrahedron");
    }

    @Test
    void loadsExpectedSpaceGroup() {
        SpaceGroup sg = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");
        assertEquals(96, sg.order(), "Im-3m space group: 48 point-group rotations x 2 BCC translations");

        double[][] rotateMat = sg.getRotateMat();
        double[] translateMat = sg.getTranslateMat();
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0, translateMat[i], 1e-12, "BCC_A2 is its own parent: zero translation");
            for (int j = 0; j < 3; j++) {
                assertEquals(i == j ? 1.0 : 0.0, rotateMat[i][j], 1e-12, "BCC_A2 is its own parent: identity rotation");
            }
        }
    }

    @Test
    void identificationPipelineProducesExpectedClusterInventory() {
        List<Cluster> maxClusters = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        maxClusters.replaceAll(Cluster::sorted);
        SpaceGroup sg = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");

        ClusterCFIdentificationPipeline.PipelineResult pr = ClusterCFIdentificationPipeline.run(
                maxClusters, sg.getOperations(), maxClusters, sg.getOperations(),
                sg.getRotateMat(), sg.getTranslateMat(), 2, null);

        // 5 disordered cluster types: tetrahedron, triangle, 2NN pair, 1NN pair, point.
        assertEquals(5, pr.getTcdis());
        assertArrayEquals(new double[] {6.0, 12.0, 4.0, 3.0, 1.0}, pr.getMhdis(), 1e-9);
        assertArrayEquals(new double[] {1.0, -1.0, 1.0, 1.0, -1.0}, pr.getKbdis(), 1e-9);
        for (int lc : pr.getLc()) {
            assertEquals(1, lc, "each HSP cluster type has exactly one ordered-phase representative");
        }
    }
}
