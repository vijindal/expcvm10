package system.model.cvm.gen.structure;

import java.util.ArrayList;
import java.util.List;

import system.model.cvm.gen.SpaceGroup;
import system.model.cvm.gen.SpaceGroup.SymmetryOperation;

/**
 * Derives a {@link SpaceGroup} directly from a {@link StructureDefinition}'s
 * lattice geometry, instead of reading a precomputed {@code sym/*.txt}
 * file: enumerates the cubic point group O_h (48 signed-permutation
 * matrices) combined with the lattice's centering translations, and keeps
 * only the operations that map the structure's site motif onto itself.
 * Reproduces {@code inputs/sym/BCC_A2-SG.txt}'s 96 operations exactly for
 * BCC_A2 (see {@code SpaceGroupGeneratorBccA2Test}).
 */
public final class SpaceGroupGenerator {

    private static final double TOL = 1e-9;

    private SpaceGroupGenerator() {}

    /** Generates the space group for {@code structure}'s conventional cell. */
    public static SpaceGroup generate(StructureDefinition structure) {
        List<double[][]> pointGroup = cubicPointGroup();
        List<double[]> centerings = structure.getLatticeSystem().centeringTranslations();

        // The motif tested for invariance is every lattice point in the
        // cell: each basis site combined with each centering translation
        // (e.g. for BCC_A2's single-site basis at the origin, the motif is
        // {(0,0,0), (1/2,1/2,1/2)} -- the corner site and the body-center
        // site). Testing invariance of the bare basis alone would miss the
        // centering translations themselves as symmetries.
        List<double[]> motif = expandMotif(structure.getBasisFractional(), centerings);

        List<SymmetryOperation> operations = new ArrayList<>();
        for (double[][] rotation : pointGroup) {
            for (double[] translation : centerings) {
                if (mapsBasisOntoItself(rotation, translation, motif)) {
                    operations.add(new SymmetryOperation(rotation, translation));
                }
            }
        }

        // Identity, rotation about the origin (the structure's own
        // parent/HSP frame), zero translation -- matches
        // StructureFileLoader.parseSpaceGroup's rotateMat/translateMat for
        // a self-parent structure like BCC_A2.
        double[][] identityRotate = {
                { 1.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 1.0 }
        };
        double[] zeroTranslate = { 0.0, 0.0, 0.0 };

        return new SpaceGroup(structure.getName() + "-SG", operations, identityRotate, zeroTranslate);
    }

    private static List<double[]> expandMotif(List<double[]> basis, List<double[]> centerings) {
        List<double[]> motif = new ArrayList<>();
        for (double[] site : basis) {
            for (double[] c : centerings) {
                motif.add(new double[] { site[0] + c[0], site[1] + c[1], site[2] + c[2] });
            }
        }
        return motif;
    }

    /**
     * True if applying (rotation, translation) to every motif site
     * reproduces the motif set exactly, up to integer lattice translations
     * (fractional-coordinate periodicity).
     */
    private static boolean mapsBasisOntoItself(double[][] rotation, double[] translation, List<double[]> basis) {
        for (double[] site : basis) {
            double[] image = applyMod1(rotation, translation, site);
            if (!containsModuloLattice(basis, image)) return false;
        }
        return true;
    }

    private static double[] applyMod1(double[][] rotation, double[] translation, double[] site) {
        double x = rotation[0][0] * site[0] + rotation[0][1] * site[1] + rotation[0][2] * site[2] + translation[0];
        double y = rotation[1][0] * site[0] + rotation[1][1] * site[1] + rotation[1][2] * site[2] + translation[1];
        double z = rotation[2][0] * site[0] + rotation[2][1] * site[1] + rotation[2][2] * site[2] + translation[2];
        return new double[] { x, y, z };
    }

    private static boolean containsModuloLattice(List<double[]> basis, double[] point) {
        for (double[] candidate : basis) {
            if (differsByIntegerTranslation(candidate, point)) return true;
        }
        return false;
    }

    private static boolean differsByIntegerTranslation(double[] a, double[] b) {
        for (int i = 0; i < 3; i++) {
            double diff = a[i] - b[i];
            double frac = diff - Math.round(diff);
            if (Math.abs(frac) > TOL) return false;
        }
        return true;
    }

    /**
     * The 48-element cubic point group O_h: every 3x3 matrix that permutes
     * the coordinate axes with an arbitrary sign flip on each -- i.e. every
     * orthogonal "signed permutation matrix". These are exactly the
     * rotation/reflection symmetries of a cube (or of the simple cubic
     * lattice) about its center.
     */
    static List<double[][]> cubicPointGroup() {
        List<double[][]> group = new ArrayList<>();
        int[][] permutations = {
                { 0, 1, 2 }, { 0, 2, 1 }, { 1, 0, 2 }, { 1, 2, 0 }, { 2, 0, 1 }, { 2, 1, 0 }
        };
        int[] signs = { 1, -1 };
        for (int[] perm : permutations) {
            for (int sx : signs) {
                for (int sy : signs) {
                    for (int sz : signs) {
                        double[][] m = new double[3][3];
                        m[0][perm[0]] = sx;
                        m[1][perm[1]] = sy;
                        m[2][perm[2]] = sz;
                        group.add(m);
                    }
                }
            }
        }
        return group;
    }
}
