package system.model.cvm.gen;

import java.util.ArrayList;
import java.util.List;

import system.model.cvm.gen.ClusterPrimitives.Position;
import system.model.cvm.gen.ClusterPrimitives.Site;
import system.model.cvm.gen.ClusterPrimitives.Sublattice;

/**
 * Encapsulates the crystallographic space group of a structure, together
 * with the rotation/translation pair that maps an ordered-phase supercell's
 * coordinates into its disordered-parent reference frame (identity for a
 * structure that is its own parent, e.g. BCC_A2).
 *
 * <p>Ported from CEWorkbench's {@code org.ce.model.cluster.SpaceGroup}.</p>
 */
public final class SpaceGroup {

    private final String name;
    private final List<SymmetryOperation> operations;
    private final double[][] rotateMat;
    private final double[] translateMat;

    public SpaceGroup(String name, List<SymmetryOperation> operations,
                       double[][] rotateMat, double[] translateMat) {
        this.name = name;
        this.operations = operations;
        this.rotateMat = rotateMat;
        this.translateMat = translateMat;
    }

    public String getName() { return name; }
    public List<SymmetryOperation> getOperations() { return operations; }
    public double[][] getRotateMat() { return rotateMat; }
    public double[] getTranslateMat() { return translateMat; }
    public int order() { return operations.size(); }

    @Override
    public String toString() {
        return "SpaceGroup{name=" + name + ", order=" + operations.size() + "}";
    }

    /** A combined rotation and translation acting on fractional coordinates. */
    public static final class SymmetryOperation {

        private final double[][] rotation;
        private final double[] translation;

        public SymmetryOperation(double[][] rotation, double[] translation) {
            this.rotation = rotation;
            this.translation = translation;
        }

        public double[][] getRotation() { return rotation; }
        public double[] getTranslation() { return translation; }

        public Site applyToSite(Site site) {
            Position r = site.getPosition();
            double x = rotation[0][0] * r.getX() + rotation[0][1] * r.getY()
                     + rotation[0][2] * r.getZ() + translation[0];
            double y = rotation[1][0] * r.getX() + rotation[1][1] * r.getY()
                     + rotation[1][2] * r.getZ() + translation[1];
            double z = rotation[2][0] * r.getX() + rotation[2][1] * r.getY()
                     + rotation[2][2] * r.getZ() + translation[2];
            return new Site(new Position(x, y, z), site.getSymbol());
        }

        public Cluster applyToCluster(Cluster cluster) {
            List<Sublattice> newSublattices = new ArrayList<>();
            for (Sublattice sub : cluster.getSublattices()) {
                List<Site> newSites = new ArrayList<>();
                for (Site s : sub.getSites()) {
                    newSites.add(applyToSite(s));
                }
                newSublattices.add(new Sublattice(newSites).sorted());
            }
            return new Cluster(newSublattices);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("SymmetryOperation{R=[");
            for (int r = 0; r < 3; r++) {
                sb.append("[");
                for (int c = 0; c < 3; c++) {
                    sb.append(String.format("%.4f", rotation[r][c]));
                    if (c < 2) sb.append(", ");
                }
                sb.append("]");
                if (r < 2) sb.append(", ");
            }
            sb.append(String.format("], t=[%.4f, %.4f, %.4f]}",
                    translation[0], translation[1], translation[2]));
            return sb.toString();
        }
    }
}
