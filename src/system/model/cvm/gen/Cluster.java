package system.model.cvm.gen;

import java.util.ArrayList;
import java.util.List;

import system.model.cvm.gen.ClusterPrimitives.Site;
import system.model.cvm.gen.ClusterPrimitives.Sublattice;

/**
 * Represents a cluster of lattice sites, grouped by sublattice.
 *
 * <p>Ported from CEWorkbench's {@code org.ce.model.cluster.Cluster}. A
 * {@code Cluster} is the central data structure of the CVM model-generation
 * layer: it holds one or more {@link Sublattice} objects, each holding a
 * list of {@link Site} objects. A disordered structure (e.g. BCC_A2) has
 * exactly one sublattice; an ordered structure would have two or more.</p>
 */
public final class Cluster {

    private static final double TOL = 1e-10;

    private final List<Sublattice> sublattices;

    public Cluster(List<Sublattice> sublattices) {
        this.sublattices = sublattices;
    }

    public List<Sublattice> getSublattices() { return sublattices; }

    /** Flattens all sublattices into a single ordered list of sites. */
    public List<Site> getAllSites() {
        List<Site> all = new ArrayList<>();
        for (Sublattice sub : sublattices) {
            all.addAll(sub.getSites());
        }
        return all;
    }

    /** Returns a new {@code Cluster} in which every sublattice is individually sorted. */
    public Cluster sorted() {
        List<Sublattice> sortedSubs = new ArrayList<>();
        for (Sublattice sub : sublattices) {
            sortedSubs.add(sub.sorted());
        }
        return new Cluster(sortedSubs);
    }

    /** Comparator for {@link Site} objects by position (x, then y, then z, ascending). */
    public static int compareSites(Site a, Site b) {
        double dx = a.getPosition().getX() - b.getPosition().getX();
        if (Math.abs(dx) > TOL) return dx < 0 ? -1 : 1;

        double dy = a.getPosition().getY() - b.getPosition().getY();
        if (Math.abs(dy) > TOL) return dy < 0 ? -1 : 1;

        double dz = a.getPosition().getZ() - b.getPosition().getZ();
        if (Math.abs(dz) > TOL) return dz < 0 ? -1 : 1;

        return 0;
    }

    @Override
    public String toString() { return sublattices.toString(); }
}
