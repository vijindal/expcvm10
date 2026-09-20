package system.model.cvm.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified container for core cluster-related primitives: Position, Site,
 * and Sublattice. These classes form a single conceptual unit for lattice
 * geometry and site representation.
 *
 * <p>Ported from CEWorkbench's {@code org.ce.model.cluster.ClusterPrimitives}
 * (with {@code Vector3D} dropped -- unused by anything the CVM
 * model-generation layer needs).</p>
 */
public final class ClusterPrimitives {

    private ClusterPrimitives() {
        // Utility container
    }

    /** Immutable three-dimensional vector for fractional lattice coordinates. */
    public static final class Position {
        private static final double TOL = 1e-10;
        private final double x;
        private final double y;
        private final double z;

        public Position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }

        public Position add(Position other) {
            return new Position(x + other.x, y + other.y, z + other.z);
        }

        public Position subtract(Position other) {
            return new Position(x - other.x, y - other.y, z - other.z);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Position)) return false;
            Position other = (Position) obj;
            return Math.abs(x - other.x) < TOL
                && Math.abs(y - other.y) < TOL
                && Math.abs(z - other.z) < TOL;
        }

        @Override
        public int hashCode() {
            return Objects.hash(round(x), round(y), round(z));
        }

        private double round(double value) {
            return Math.round(value / TOL) * TOL;
        }

        @Override
        public String toString() {
            return String.format("(%.6f, %.6f, %.6f)", x, y, z);
        }
    }

    /** Represents a single lattice site within a cluster. */
    public static final class Site {
        private final Position position;
        private final String symbol;

        public Site(Position position, String symbol) {
            this.position = position;
            this.symbol = symbol;
        }

        public Position getPosition() { return position; }
        public String getSymbol() { return symbol; }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Site)) return false;
            Site other = (Site) obj;
            return position.equals(other.position) && Objects.equals(symbol, other.symbol);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, symbol);
        }

        @Override
        public String toString() {
            return position + "," + symbol;
        }
    }

    /**
     * An ordered collection of {@link Site} objects that belong to the same
     * crystallographic sublattice within a {@link Cluster}.
     */
    public static final class Sublattice {
        private final List<Site> sites;

        public Sublattice(List<Site> sites) {
            this.sites = sites;
        }

        public List<Site> getSites() { return sites; }

        public Sublattice sorted() {
            List<Site> sortedSites = new ArrayList<>(sites);
            for (int i = 1; i < sortedSites.size(); i++) {
                Site x = sortedSites.get(i);
                int j = i - 1;
                while (j >= 0 && Cluster.compareSites(sortedSites.get(j), x) > 0) {
                    sortedSites.set(j + 1, sortedSites.get(j));
                    j--;
                }
                sortedSites.set(j + 1, x);
            }
            return new Sublattice(sortedSites);
        }

        @Override
        public String toString() { return sites.toString(); }
    }
}
