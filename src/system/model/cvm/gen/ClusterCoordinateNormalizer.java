package system.model.cvm.gen;

import system.model.cvm.gen.ClusterPrimitives.Position;
import system.model.cvm.gen.ClusterPrimitives.Site;
import system.model.cvm.gen.ClusterPrimitives.Sublattice;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes cluster coordinates to canonical reference frames, bridging the
 * gap between generated clusters (which have arbitrary absolute coordinates)
 * and CVCF registrations (which expect specific reference positions).
 *
 * <p>A generated cluster is geometrically correct but positioned at
 * (potentially) different absolute coordinates than the reference CVCF
 * coordinate expectations. Normalization translates the cluster to a
 * canonical frame without changing its internal geometry:
 * all pairwise distances and angles remain identical.</p>
 *
 * <p>This is purely structural/geometric; no thermodynamic information
 * is involved.</p>
 */
public final class ClusterCoordinateNormalizer {

    private static final double TOL = 1e-9;

    private ClusterCoordinateNormalizer() {}

    /**
     * Normalizes {@code cluster} to origin: translates all sites so that the
     * lexicographically smallest site is positioned at (0, 0, 0).
     *
     * <p>This is the canonical normalization used by CVCF definitions:
     * the first/smallest site becomes the logical reference, and all other
     * coordinates are relative to it.</p>
     *
     * @param cluster the cluster to normalize
     * @return a new cluster with normalized coordinates
     */
    public static Cluster normalizeToOrigin(Cluster cluster) {
        List<Site> allSites = cluster.getAllSites();
        if (allSites.isEmpty()) {
            return cluster;
        }

        Position minPos = allSites.get(0).getPosition();
        for (Site site : allSites) {
            Position pos = site.getPosition();
            if (isLexicographicallySmaller(pos, minPos)) {
                minPos = pos;
            }
        }

        return translateBy(cluster, minPos.getX(), minPos.getY(), minPos.getZ(), -1.0);
    }

    /**
     * Normalizes {@code cluster} by translating it so that the site closest
     * to {@code referencePos} is positioned exactly at {@code referencePos}.
     *
     * <p>Useful for aligning a generated cluster to a specific CVCF site
     * coordinate.</p>
     *
     * @param cluster      the cluster to normalize
     * @param referencePos the target position for the nearest site
     * @return a new cluster with normalized coordinates
     */
    public static Cluster normalizeToPosition(Cluster cluster, Position referencePos) {
        List<Site> allSites = cluster.getAllSites();
        if (allSites.isEmpty()) {
            return cluster;
        }

        Site nearest = allSites.get(0);
        double minDist = distance(nearest.getPosition(), referencePos);
        for (Site site : allSites) {
            double d = distance(site.getPosition(), referencePos);
            if (d < minDist) {
                minDist = d;
                nearest = site;
            }
        }

        Position nearestPos = nearest.getPosition();
        double dx = referencePos.getX() - nearestPos.getX();
        double dy = referencePos.getY() - nearestPos.getY();
        double dz = referencePos.getZ() - nearestPos.getZ();

        return translateBy(cluster, dx, dy, dz, 1.0);
    }

    /**
     * Checks if the first site of {@code cluster} is at the origin (within
     * tolerance). Useful for diagnosing coordinate issues.
     */
    public static boolean isAtOrigin(Cluster cluster) {
        List<Site> sites = cluster.getAllSites();
        if (sites.isEmpty()) return false;
        Position pos = sites.get(0).getPosition();
        return Math.abs(pos.getX()) < TOL && Math.abs(pos.getY()) < TOL && Math.abs(pos.getZ()) < TOL;
    }

    // =========================================================================
    // Implementation details
    // =========================================================================

    private static Cluster translateBy(Cluster cluster, double dx, double dy, double dz, double sign) {
        List<Sublattice> newSublattices = new ArrayList<>();
        for (Sublattice sl : cluster.getSublattices()) {
            List<Site> newSites = new ArrayList<>();
            for (Site site : sl.getSites()) {
                Position pos = site.getPosition();
                Position newPos = new Position(
                        pos.getX() + sign * dx,
                        pos.getY() + sign * dy,
                        pos.getZ() + sign * dz);
                newSites.add(new Site(newPos, site.getSymbol()));
            }
            newSublattices.add(new Sublattice(newSites));
        }
        return new Cluster(newSublattices);
    }

    private static boolean isLexicographicallySmaller(Position a, Position b) {
        if (Math.abs(a.getX() - b.getX()) > TOL)
            return a.getX() < b.getX();
        if (Math.abs(a.getY() - b.getY()) > TOL)
            return a.getY() < b.getY();
        return a.getZ() < b.getZ();
    }

    private static double distance(Position a, Position b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
