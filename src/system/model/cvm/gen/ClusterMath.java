package system.model.cvm.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import system.model.cvm.gen.ClusterPrimitives.Position;
import system.model.cvm.gen.ClusterPrimitives.Site;
import system.model.cvm.gen.ClusterPrimitives.Sublattice;
import system.model.cvm.gen.SpaceGroup.SymmetryOperation;

/**
 * Unified container for cluster-related mathematical operations: the Inden
 * (1992) R-matrix / symmetric integer basis, the Nij containment table, and
 * Kikuchi-Baker entropy coefficients.
 *
 * <p>Ported from CEWorkbench's {@code org.ce.model.cluster.ClusterMath}
 * (with the P-operator-rules and higher-level list/orbit-debug helpers
 * dropped as unused by this generation-only layer; {@code buildBasis} and
 * {@code buildRMatrix} are kept because they are used directly by
 * {@link CMatrixPipeline} and {@link ClusterCFIdentificationPipeline}).</p>
 */
public final class ClusterMath {

    private static final double DELTA = 1e-6;

    private ClusterMath() {}

    /**
     * Builds the Inden (1992) R-matrix for the given number of elements:
     * the inverse of a Vandermonde-like matrix from the symmetric integer
     * basis.
     */
    public static double[][] buildRMatrix(int numElements) {
        if (numElements < 2) {
            throw new IllegalArgumentException("numElements must be >= 2");
        }
        double[] basis = buildBasis(numElements);
        double[][] matM = new double[numElements][numElements];
        for (int i = 0; i < numElements; i++) {
            for (int j = 0; j < numElements; j++) {
                matM[i][j] = (i == 0) ? 1.0 : Math.pow(basis[j], i);
            }
        }
        return LinearAlgebra.invert(matM);
    }

    /**
     * Returns the symmetric integer basis sequence for K components.
     * Even K: {-K/2, ..., -1, 1, ..., K/2}. Odd K: {-(K-1)/2, ..., 0, ..., (K-1)/2}.
     */
    public static double[] buildBasis(int numElements) {
        double[] basis = new double[numElements];
        if (numElements % 2 == 0) {
            int half = numElements / 2;
            for (int i = 0; i < half; i++) basis[i] = -half + i;
            for (int i = 0; i < half; i++) basis[half + i] = 1 + i;
        } else {
            int start = -((numElements - 1) / 2);
            for (int i = 0; i < numElements; i++) basis[i] = start + i;
        }
        return basis;
    }

    /**
     * Computes the Nij containment table: nij[i][j] is the number of times
     * cluster type j appears as a geometrically distinct sub-cluster inside
     * cluster type i.
     */
    public static int[][] computeNijTable(List<Cluster> clusCoordList, List<List<Cluster>> clusOrbitList) {
        int numClus = clusCoordList.size();
        int[][] nijTable = new int[numClus][numClus];

        for (int i = 0; i < numClus; i++) {
            List<Cluster> subClusCoord = ClusterCFIdentificationPipeline.genSubClusCoord(clusCoordList.get(i));
            int numSubClus = subClusCoord.size();

            for (int k = numSubClus - 1; k >= 0; k--) {
                Cluster subCluster = subClusCoord.get(k);
                List<Integer> subSizes = sublatticeSizes(subCluster);

                for (int j = 0; j < numClus; j++) {
                    if (j >= i) {
                        if (subSizes.equals(sublatticeSizes(clusCoordList.get(j)))) {
                            if (isContained(clusOrbitList.get(j), subCluster)) {
                                nijTable[i][j]++;
                            }
                        }
                    }
                }
            }
        }
        return nijTable;
    }

    /**
     * Computes KB entropy coefficients via the inclusion-exclusion
     * recurrence: {@code kb[j] = (m[j] - sum_{i<j} m[i]*nij[i][j]*kb[i]) / m[j]}.
     */
    public static double[] computeKikuchiBaker(double[] multiplicities, int[][] nijTable) {
        int n = multiplicities.length;
        if (nijTable.length != n || nijTable[0].length != n) {
            throw new IllegalArgumentException("Dimension mismatch between multiplicities and Nij table.");
        }
        double[] kb = new double[n];
        for (int j = 0; j < n; j++) {
            if (multiplicities[j] == 0) {
                throw new ClusterGenerationException("Stage 1a",
                        "Cluster multiplicity[" + j + "]=0; cannot compute Kikuchi-Baker coefficient.");
            }
            double sumTerm = 0.0;
            for (int i = 0; i < j; i++) {
                sumTerm += multiplicities[i] * nijTable[i][j] * kb[i];
            }
            kb[j] = (multiplicities[j] - sumTerm) / multiplicities[j];
        }
        return kb;
    }

    // ---- Geometric utilities ----

    public static boolean isTranslated(Cluster c1, Cluster c2) {
        if (c1.getSublattices().size() != c2.getSublattices().size()) return false;

        Set<Position> diffSet = new HashSet<>();
        for (int i = 0; i < c1.getSublattices().size(); i++) {
            Sublattice sub1 = c1.getSublattices().get(i);
            Sublattice sub2 = c2.getSublattices().get(i);
            List<Site> s1 = sub1.getSites();
            List<Site> s2 = sub2.getSites();
            if (s1.size() != s2.size()) return false;
            for (int j = 0; j < s1.size(); j++) {
                Site site1 = s1.get(j);
                Site site2 = s2.get(j);
                if (!site1.getSymbol().equals(site2.getSymbol())) return false;
                diffSet.add(site2.getPosition().subtract(site1.getPosition()));
            }
        }
        if (diffSet.size() > 1) return false;
        if (diffSet.isEmpty()) return true;
        Position d = diffSet.iterator().next();
        return isIntegerShift(d.getX()) && isIntegerShift(d.getY()) && isIntegerShift(d.getZ());
    }

    private static boolean isIntegerShift(double value) {
        return Math.abs(value - Math.round(value)) < DELTA;
    }

    public static boolean isContained(List<Cluster> orbit, Cluster cluster) {
        for (Cluster existing : orbit) {
            if (isTranslated(existing, cluster)) return true;
        }
        return false;
    }

    public static List<Cluster> generateOrbit(Cluster cluster, List<SymmetryOperation> spaceGroup) {
        List<Cluster> orbit = new ArrayList<>();
        for (SymmetryOperation op : spaceGroup) {
            Cluster transformed = op.applyToCluster(cluster);
            if (!isContained(orbit, transformed)) orbit.add(transformed);
        }
        return orbit;
    }

    private static List<Integer> sublatticeSizes(Cluster c) {
        List<Integer> sizes = new ArrayList<>();
        for (Sublattice sub : c.getSublattices()) {
            sizes.add(sub.getSites().size());
        }
        return sizes;
    }

    static double[] copyOf(double[] a) { return Arrays.copyOf(a, a.length); }
}
