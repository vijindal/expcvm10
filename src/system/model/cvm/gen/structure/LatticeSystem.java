package system.model.cvm.gen.structure;

import java.util.List;

/**
 * Identifies which family of lattice-centering translations (e.g. identity
 * for simple cubic, identity plus {1/2,1/2,1/2} for body-centered) applies
 * to a {@link StructureDefinition}, so {@link SpaceGroupGenerator} knows
 * which translations to combine with cubic point-group rotations. Currently
 * implements simple and body-centered cubic; additional lattice systems
 * (FCC, hexagonal, ...) are added as new enum cases and translation
 * methods.
 */
public enum LatticeSystem {

    /** Simple cubic: no internal centering translation beyond the identity. */
    CUBIC_SIMPLE {
        @Override
        public List<double[]> centeringTranslations() {
            return List.of(new double[] { 0.0, 0.0, 0.0 });
        }
    },

    /** Body-centered cubic: identity plus the (1/2,1/2,1/2) body-center translation. */
    CUBIC_BODY_CENTERED {
        @Override
        public List<double[]> centeringTranslations() {
            return List.of(
                    new double[] { 0.0, 0.0, 0.0 },
                    new double[] { 0.5, 0.5, 0.5 });
        }
    };

    /**
     * Fractional-coordinate centering translations (always includes the
     * zero vector) that, combined with the lattice's point-group
     * operations, generate the full space group acting on the conventional
     * cell.
     */
    public abstract List<double[]> centeringTranslations();
}
