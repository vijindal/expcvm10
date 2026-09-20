package system.model.cvm.gen.structure;

import java.util.Collections;
import java.util.List;

/**
 * Generic crystallographic structure definition: a Bravais lattice (3x3
 * lattice-vector matrix) plus a basis of fractional site positions,
 * consumed by {@link SpaceGroupGenerator}/{@link MaximalClusterGenerator}.
 * Carries no thermodynamic information. BCC_A2 is one instance of this
 * class (a cubic lattice with a single-site basis, body-centered), not a
 * hard-coded generator method; other structures are additional instances.
 */
public final class StructureDefinition {

    private final String name;
    private final double[][] latticeVectors;
    private final List<double[]> basisFractional;
    private final LatticeSystem latticeSystem;

    /**
     * @param name             structure identifier, e.g. {@code "BCC_A2"}
     * @param latticeVectors   3x3 matrix of conventional-cell lattice vectors (rows = a1, a2, a3)
     * @param basisFractional  fractional coordinates of each site in the basis (all in [0,1))
     * @param latticeSystem    the lattice system, used to select the correct point-group generator
     */
    public StructureDefinition(String name, double[][] latticeVectors,
            List<double[]> basisFractional, LatticeSystem latticeSystem) {
        if (latticeVectors.length != 3 || latticeVectors[0].length != 3) {
            throw new IllegalArgumentException("latticeVectors must be a 3x3 matrix");
        }
        if (basisFractional.isEmpty()) {
            throw new IllegalArgumentException("basisFractional must have at least one site");
        }
        this.name = name;
        this.latticeVectors = latticeVectors;
        this.basisFractional = Collections.unmodifiableList(basisFractional);
        this.latticeSystem = latticeSystem;
    }

    public String getName() { return name; }
    public double[][] getLatticeVectors() { return latticeVectors; }
    public List<double[]> getBasisFractional() { return basisFractional; }
    public LatticeSystem getLatticeSystem() { return latticeSystem; }

    /**
     * Builds the primitive-cubic-lattice BCC_A2 structure: a cubic
     * conventional cell (edge 1) with lattice points at the corners and one
     * body-centering translation, represented here as a single-site basis
     * at the origin together with the {@link LatticeSystem#CUBIC_BODY_CENTERED}
     * translation set (see {@link SpaceGroupGenerator}). This mirrors the
     * disordered BCC_A2 structure used throughout expcvm10's CVM layer.
     */
    public static StructureDefinition bccA2() {
        double[][] cubic = {
                { 1.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 1.0 }
        };
        return new StructureDefinition("BCC_A2", cubic,
                List.of(new double[] { 0.0, 0.0, 0.0 }),
                LatticeSystem.CUBIC_BODY_CENTERED);
    }

    @Override
    public String toString() {
        return "StructureDefinition{" + name + ", basisSize=" + basisFractional.size()
                + ", latticeSystem=" + latticeSystem + "}";
    }
}
