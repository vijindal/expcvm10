package system.model.cvm.gen.structure;

/**
 * Identifies a CVM cluster approximation by name and maximal-cluster size,
 * e.g. the tetrahedron ("T") approximation (4-site cluster) used in
 * expcvm10's validated BCC_A2 CVM model. Second input (with
 * {@link StructureDefinition}) to the generic generation API.
 */
public final class ApproximationDefinition {

    private final String name;
    private final int clusterSize;

    public ApproximationDefinition(String name, int clusterSize) {
        this.name = name;
        this.clusterSize = clusterSize;
    }

    public String getName() { return name; }
    public int getClusterSize() { return clusterSize; }

    /** The tetrahedron approximation: 4-site maximal cluster. */
    public static ApproximationDefinition tetrahedron() {
        return new ApproximationDefinition("T", 4);
    }

    @Override
    public String toString() {
        return "ApproximationDefinition{" + name + ", clusterSize=" + clusterSize + "}";
    }
}
