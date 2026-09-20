package system.model.cvm.gen.structure;

import java.util.ArrayList;
import java.util.List;

import system.model.cvm.gen.Cluster;
import system.model.cvm.gen.ClusterGenerationException;
import system.model.cvm.gen.ClusterPrimitives.Position;
import system.model.cvm.gen.ClusterPrimitives.Site;
import system.model.cvm.gen.ClusterPrimitives.Sublattice;
import system.model.cvm.gen.SpaceGroup;
import system.model.cvm.gen.SpaceGroup.SymmetryOperation;

/**
 * Derives a structure's maximal cluster for a given approximation directly
 * from lattice geometry, instead of reading a precomputed
 * {@code clus/*.txt} file. Currently supports only the 4-site tetrahedron
 * ("T") approximation, the validated target; picks the most compact
 * {@code clusterSize}-site cluster containing the origin, canonicalized to
 * a fixed representative of its symmetry orbit.
 */
public final class MaximalClusterGenerator {

    private static final double TOL = 1e-9;
    private static final int SUPERCELL_RANGE = 2;

    private MaximalClusterGenerator() {}

    /**
     * Generates the maximal cluster(s) for {@code structure} under
     * {@code approximation}. Returns a singleton list for a single-orbit
     * maximal cluster (all approximations currently supported).
     */
    public static List<Cluster> generate(StructureDefinition structure, ApproximationDefinition approximation) {
        if (approximation.getClusterSize() != 4) {
            throw new UnsupportedOperationException(
                    "MaximalClusterGenerator currently supports only the 4-site tetrahedron approximation "
                            + "(the validated T-model); requested clusterSize=" + approximation.getClusterSize());
        }

        List<Position> latticePoints = enumerateLatticePoints(structure);
        Position origin = new Position(0.0, 0.0, 0.0);

        double nnDistance = nearestNeighborDistance(origin, latticePoints);

        // Candidate pool: lattice points within a handful of NN shells of
        // the origin. The T-model maximal cluster need not be a regular
        // NN-simplex (on BCC it is not: it has four short NN edges and two
        // longer 2nd-NN "diagonal" edges), so the search below picks the
        // most compact clusterSize-subset containing the origin, rather
        // than requiring every pair to be at the same distance.
        List<Position> pool = new ArrayList<>();
        for (Position p : latticePoints) {
            double d = distance(origin, p);
            if (d > TOL && d < nnDistance * 1.5) pool.add(p);
        }

        List<Position> best = findMostCompactCluster(origin, pool, approximation.getClusterSize());
        if (best == null) {
            throw new ClusterGenerationException("MaximalClusterGenerator",
                    "No " + approximation.getClusterSize() + "-site compact cluster found "
                            + "for structure " + structure.getName());
        }

        List<Site> sites = new ArrayList<>();
        for (Position p : best) sites.add(new Site(p, "s1"));
        Cluster cluster = new Cluster(List.of(new Sublattice(sites))).sorted();

        // The most-compact-cluster search above can have several
        // symmetry-equivalent solutions (e.g. on BCC, any of the 8 NN
        // tetrahedra sharing the origin vertex ties on compactness); which
        // one comes out depends on lattice-enumeration order, not physics.
        // Canonicalize to a fixed representative of that cluster's orbit
        // under the structure's own space group, so the result is
        // deterministic and independent of enumeration order.
        SpaceGroup spaceGroup = SpaceGroupGenerator.generate(structure);
        Cluster canonical = canonicalOrbitRepresentative(cluster, spaceGroup.getOperations());
        return List.of(canonical);
    }

    /**
     * Picks a fixed, deterministic representative from {@code cluster}'s
     * orbit under {@code spaceGroup}: the one whose sorted flat coordinate
     * list is lexicographically smallest. This has no physical meaning by
     * itself (any orbit member is an equally valid maximal cluster) -- it
     * only removes the dependence of {@link #generate}'s output on lattice
     * -enumeration order, which is otherwise incidental.
     */
    private static Cluster canonicalOrbitRepresentative(Cluster cluster, List<SymmetryOperation> spaceGroup) {
        List<Cluster> orbit = new ArrayList<>();
        for (SymmetryOperation op : spaceGroup) {
            Cluster transformed = op.applyToCluster(cluster);
            boolean isNew = true;
            for (Cluster existing : orbit) {
                if (sameCoordinates(existing, transformed)) { isNew = false; break; }
            }
            if (isNew) orbit.add(transformed);
        }

        Cluster canonical = orbit.get(0);
        for (Cluster candidate : orbit) {
            if (compareLexicographically(candidate, canonical) < 0) canonical = candidate;
        }
        return canonical;
    }

    private static boolean sameCoordinates(Cluster a, Cluster b) {
        List<Site> sa = a.getAllSites();
        List<Site> sb = b.getAllSites();
        if (sa.size() != sb.size()) return false;
        for (int i = 0; i < sa.size(); i++) {
            if (!sa.get(i).getPosition().equals(sb.get(i).getPosition())) return false;
        }
        return true;
    }

    private static int compareLexicographically(Cluster a, Cluster b) {
        List<Site> sa = a.getAllSites();
        List<Site> sb = b.getAllSites();
        for (int i = 0; i < sa.size(); i++) {
            int c = Cluster.compareSites(sa.get(i), sb.get(i));
            if (c != 0) return c;
        }
        return 0;
    }

    // =====================================================================
    // Lattice enumeration
    // =====================================================================

    private static List<Position> enumerateLatticePoints(StructureDefinition structure) {
        double[][] lattice = structure.getLatticeVectors();
        List<double[]> motif = new ArrayList<>();
        for (double[] basisSite : structure.getBasisFractional()) {
            for (double[] c : structure.getLatticeSystem().centeringTranslations()) {
                motif.add(new double[] { basisSite[0] + c[0], basisSite[1] + c[1], basisSite[2] + c[2] });
            }
        }

        List<Position> points = new ArrayList<>();
        for (double[] motifSite : motif) {
            for (int i = -SUPERCELL_RANGE; i <= SUPERCELL_RANGE; i++) {
                for (int j = -SUPERCELL_RANGE; j <= SUPERCELL_RANGE; j++) {
                    for (int k = -SUPERCELL_RANGE; k <= SUPERCELL_RANGE; k++) {
                        double fx = motifSite[0] + i;
                        double fy = motifSite[1] + j;
                        double fz = motifSite[2] + k;
                        double x = lattice[0][0] * fx + lattice[1][0] * fy + lattice[2][0] * fz;
                        double y = lattice[0][1] * fx + lattice[1][1] * fy + lattice[2][1] * fz;
                        double z = lattice[0][2] * fx + lattice[1][2] * fy + lattice[2][2] * fz;
                        points.add(new Position(x, y, z));
                    }
                }
            }
        }
        return points;
    }

    private static double nearestNeighborDistance(Position origin, List<Position> points) {
        double best = Double.MAX_VALUE;
        for (Position p : points) {
            double d = distance(origin, p);
            if (d > TOL && d < best) best = d;
        }
        if (best == Double.MAX_VALUE) {
            throw new ClusterGenerationException("MaximalClusterGenerator", "No neighbors found around origin.");
        }
        return best;
    }

    /**
     * Finds the most compact {@code size}-site cluster containing
     * {@code origin}, drawn from {@code candidates}: the one minimizing
     * (in lexicographic order) the maximum pairwise distance, then the sum
     * of all pairwise distances. This is the natural geometric definition
     * of "the maximal cluster of the smallest non-trivial CVM
     * approximation" and needs no assumption that every pair sits at the
     * same (nearest-neighbor) distance.
     */
    private static List<Position> findMostCompactCluster(Position origin, List<Position> candidates, int size) {
        List<Position> chosen = new ArrayList<>();
        chosen.add(origin);
        List<Position> current = new ArrayList<>();

        // Enumerate all (size-1)-subsets of candidates via simple recursive
        // combinations; candidate pools are small (a handful of NN shells)
        // so this is cheap for the clusterSize=4 case supported today.
        double[] bestMaxHolder = { Double.MAX_VALUE };
        double[] bestSumHolder = { Double.MAX_VALUE };
        List<List<Position>> bestHolder = new ArrayList<>();
        bestHolder.add(null);
        combine(candidates, 0, size - 1, current, chosen, bestMaxHolder, bestSumHolder, bestHolder);
        return bestHolder.get(0);
    }

    private static void combine(
            List<Position> candidates, int start, int remaining, List<Position> current, List<Position> chosen,
            double[] bestMax, double[] bestSum, List<List<Position>> bestHolder) {

        if (remaining == 0) {
            List<Position> full = new ArrayList<>(chosen);
            full.addAll(current);
            double max = 0.0, sum = 0.0;
            for (int i = 0; i < full.size(); i++) {
                for (int j = i + 1; j < full.size(); j++) {
                    double d = distance(full.get(i), full.get(j));
                    max = Math.max(max, d);
                    sum += d;
                }
            }
            if (max < bestMax[0] - TOL || (max < bestMax[0] + TOL && sum < bestSum[0] - TOL)) {
                bestMax[0] = max;
                bestSum[0] = sum;
                bestHolder.set(0, full);
            }
            return;
        }
        for (int i = start; i <= candidates.size() - remaining; i++) {
            current.add(candidates.get(i));
            combine(candidates, i + 1, remaining - 1, current, chosen, bestMax, bestSum, bestHolder);
            current.remove(current.size() - 1);
        }
    }

    private static double distance(Position a, Position b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
