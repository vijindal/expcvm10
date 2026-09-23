package system.model.cvm.gen.structure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import system.model.cvm.gen.Cluster;
import system.model.cvm.gen.ClusterMath;
import system.model.cvm.gen.SpaceGroup;
import system.model.cvm.gen.StructureFileLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test B (cluster generation): verifies {@link MaximalClusterGenerator}
 * derives the same 4-site NN-tetrahedron maximal cluster as the previously
 * verified {@code inputs/clus/BCC_A2-T.txt} file, for BCC_A2 under the
 * tetrahedron approximation.
 *
 * <p>Since the absolute lattice origin/labeling is a free choice, exact
 * coordinate equality is not required -- only that the generated cluster
 * is symmetry-equivalent (same orbit under the generated space group) to
 * the file's cluster, which is the physically meaningful notion of
 * "the same maximal cluster".</p>
 */
@Tag("exploratory")
class MaximalClusterGeneratorBccA2Test {

    @Test
    void generatesFourSiteSingleSublatticeCluster() {
        List<Cluster> generated = MaximalClusterGenerator.generate(
                StructureDefinition.bccA2(), ApproximationDefinition.tetrahedron());

        assertEquals(1, generated.size());
        Cluster tetra = generated.get(0);
        assertEquals(1, tetra.getSublattices().size(), "disordered structure -> single sublattice");
        assertEquals(4, tetra.getAllSites().size(), "T-model maximal cluster is a 4-site tetrahedron");
    }

    @Test
    void generatedClusterIsSymmetryEquivalentToFileCluster() {
        List<Cluster> generated = MaximalClusterGenerator.generate(
                StructureDefinition.bccA2(), ApproximationDefinition.tetrahedron());
        Cluster generatedTetra = generated.get(0);

        List<Cluster> fromFile = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        fromFile.replaceAll(Cluster::sorted);
        Cluster fileTetra = fromFile.get(0);

        SpaceGroup sg = SpaceGroupGenerator.generate(StructureDefinition.bccA2());
        List<Cluster> orbit = ClusterMath.generateOrbit(fileTetra, sg.getOperations());

        assertTrue(ClusterMath.isContained(orbit, generatedTetra),
                "generated tetrahedron must be in the same symmetry orbit as the file-based tetrahedron");
    }
}
