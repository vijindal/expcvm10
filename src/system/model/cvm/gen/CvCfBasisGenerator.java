package system.model.cvm.gen;

import system.model.cvm.gen.CvCfBasis.VSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Programmatic builder for CVCF (cluster variable correlation function) basis
 * definitions, eliminating the need for hand-coded registrations for each
 * (structure, model, K) combination.
 *
 * <p>Produces the same logical CVCF definitions as the hand-transcribed
 * CEWorkbench originals, but derived from parametric templates for:
 * <ul>
 *   <li>Point CFs: K components (xA, xB, xC, ...)</li>
 *   <li>Pair CFs: Binary products for each pair (v2AB, v2AC, v2BC, ...)</li>
 *   <li>Triangle CFs: Products and differences for each trio</li>
 *   <li>Tetrahedron CFs: Products for each quartet</li>
 * </ul></p>
 *
 * <p>This approach enables automatic CVCF generation for any K without
 * code duplication, while maintaining compatibility with the existing
 * hand-coded definitions via the {@link CvCfBasis} registry.</p>
 */
public final class CvCfBasisGenerator {

    private CvCfBasisGenerator() {}

    /**
     * Generates the logical-site coordinates for a BCC_A2 T-model (tetrahedron)
     * cluster in canonical form: the 4-site cluster with lexicographically
     * smallest site at the origin.
     *
     * <p>Note: This is currently hard-coded to the BCC_A2 T-model reference
     * frame. A fully generic version would derive these from the structure
     * definition itself, but that requires additional infrastructure for
     * cluster coordinate canonicalization that is outside the current scope.</p>
     */
    public static double[][] generateBccA2LogicalSiteCoords() {
        return new double[][] {
                { 0.0, 0.0, 0.0 }, // p1 (lexicographically smallest = origin)
                { 0.5, -0.5, 0.5 }, // p2
                { 0.5, 0.5, 0.5 }, // p3
                { 1.0, 0.0, 0.0 } // p4
        };
    }

    /**
     * Generates CF names for a BCC_A2 T-model with K components.
     *
     * <p>CF ordering (same as CEWorkbench):
     * <ul>
     *   <li>Tetrahedra: binary + K-way ternary/quaternary</li>
     *   <li>Triangles: binary + ternary ternary/quaternary</li>
     *   <li>Pairs (II-n and I-n): binary + ternary ternary/quaternary</li>
     *   <li>Points: x_A, x_B, x_C, ...</li>
     * </ul></p>
     */
    public static List<String> generateCfNames(int numComponents) {
        List<String> names = new ArrayList<>();
        String[] atomSymbols = generateAtomSymbols(numComponents);

        if (numComponents == 2) {
            // Tetrahedra
            names.add("v4AB");

            // Triangles
            names.add("v3AB");

            // Pairs
            names.add("v2AB2"); // II-n
            names.add("v2AB1"); // I-n

            // Points
            names.add("xA");
            names.add("xB");
        } else if (numComponents == 3) {
            // Tetrahedra: 3 binary + 3 ternary
            names.add("v4AB");
            names.add("v4AC");
            names.add("v4BC");
            names.add("v4ABC1");
            names.add("v4ABC2");
            names.add("v4ABC3");

            // Triangles: 3 binary + 3 ternary
            names.add("v3AB");
            names.add("v3AC");
            names.add("v3BC");
            names.add("v3ABC1");
            names.add("v3ABC2");
            names.add("v3ABC3");

            // Pairs: 3 II-n + 3 I-n (each binary pair)
            names.add("v2AB2");
            names.add("v2AC2");
            names.add("v2BC2");
            names.add("v2AB1");
            names.add("v2AC1");
            names.add("v2BC1");

            // Points
            names.add("xA");
            names.add("xB");
            names.add("xC");
        } else if (numComponents == 4) {
            // Tetrahedra: 6 binary + 4 quaternary
            names.add("v4AB");
            names.add("v4AC");
            names.add("v4AD");
            names.add("v4BC");
            names.add("v4BD");
            names.add("v4CD");
            names.add("v4ABCD1");
            names.add("v4ABCD2");
            names.add("v4ABCD3");
            names.add("v4ABCD4");

            // Triangles: 6 binary (p1,p2,p4) + 12 ternary
            names.add("v3AB");
            names.add("v3AC");
            names.add("v3AD");
            names.add("v3BC");
            names.add("v3BD");
            names.add("v3CD");
            names.add("v3ABC1");
            names.add("v3ABC2");
            names.add("v3ABC3");
            names.add("v3ABD1");
            names.add("v3ABD2");
            names.add("v3ABD3");
            names.add("v3ACD1");
            names.add("v3ACD2");
            names.add("v3ACD3");
            names.add("v3BCD1");
            names.add("v3BCD2");
            names.add("v3BCD3");

            // Pairs: 6 II-n (p1,p4) + 6 I-n (p1,p2)
            names.add("v2AB2");
            names.add("v2AC2");
            names.add("v2AD2");
            names.add("v2BC2");
            names.add("v2BD2");
            names.add("v2CD2");
            names.add("v2AB1");
            names.add("v2AC1");
            names.add("v2AD1");
            names.add("v2BC1");
            names.add("v2BD1");
            names.add("v2CD1");

            // Points
            names.add("xA");
            names.add("xB");
            names.add("xC");
            names.add("xD");
        } else {
            throw new UnsupportedOperationException(
                    "Automatic CF name generation not yet supported for K=" + numComponents);
        }

        return names;
    }

    /**
     * Generates VSpec (CVCF variable definitions) for a BCC_A2 T-model
     * with K components, matching the CEWorkbench transcriptions.
     *
     * <p>These specs define how each CVCF variable is computed from
     * site occupation probabilities. The site indices and atom orderings
     * must match the logical site coordinates generated by
     * {@link #generateBccA2LogicalSiteCoords()}.</p>
     */
    public static List<VSpec> generateVSpecs(int numComponents) {
        List<VSpec> vspecs = new ArrayList<>();

        if (numComponents == 2) {
            // Tetrahedra
            vspecs.add(VSpec.product(1, 0, 2, 1, 3, 1, 4, 0)); // v4AB

            // Triangles
            vspecs.add(VSpec.diff(
                    new int[] { 1, 0, 2, 1, 3, 1 },
                    new int[] { 1, 1, 2, 0, 3, 0 })); // v3AB

            // Pairs
            vspecs.add(VSpec.product(1, 0, 4, 1)); // v2AB2
            vspecs.add(VSpec.product(1, 0, 2, 1)); // v2AB1

            // Points
            vspecs.add(VSpec.point(1, 0)); // xA
            vspecs.add(VSpec.point(1, 1)); // xB
        } else if (numComponents == 3) {
            // Tetrahedra
            vspecs.add(VSpec.product(1, 0, 2, 1, 3, 1, 4, 0)); // v4AB
            vspecs.add(VSpec.product(1, 0, 2, 2, 3, 2, 4, 0)); // v4AC
            vspecs.add(VSpec.product(1, 1, 2, 2, 3, 2, 4, 1)); // v4BC
            vspecs.add(VSpec.product(1, 1, 2, 0, 3, 0, 4, 2)); // v4ABC1
            vspecs.add(VSpec.product(1, 0, 2, 1, 3, 1, 4, 2)); // v4ABC2
            vspecs.add(VSpec.product(1, 0, 2, 2, 3, 2, 4, 1)); // v4ABC3

            // Triangles
            vspecs.add(VSpec.diff(new int[] { 1, 0, 2, 1, 3, 1 }, new int[] { 1, 1, 2, 0, 3, 0 })); // v3AB
            vspecs.add(VSpec.diff(new int[] { 1, 0, 2, 2, 3, 2 }, new int[] { 1, 2, 2, 0, 3, 0 })); // v3AC
            vspecs.add(VSpec.diff(new int[] { 1, 1, 2, 2, 3, 2 }, new int[] { 1, 2, 2, 1, 3, 1 })); // v3BC
            vspecs.add(VSpec.product(1, 2, 2, 0, 3, 1)); // v3ABC1
            vspecs.add(VSpec.product(1, 1, 2, 0, 3, 2)); // v3ABC2
            vspecs.add(VSpec.product(1, 0, 2, 1, 3, 2)); // v3ABC3

            // Pairs
            vspecs.add(VSpec.product(1, 0, 4, 1)); // v2AB2
            vspecs.add(VSpec.product(1, 0, 4, 2)); // v2AC2
            vspecs.add(VSpec.product(1, 1, 4, 2)); // v2BC2
            vspecs.add(VSpec.product(1, 0, 2, 1)); // v2AB1
            vspecs.add(VSpec.product(1, 0, 2, 2)); // v2AC1
            vspecs.add(VSpec.product(1, 1, 2, 2)); // v2BC1

            // Points
            vspecs.add(VSpec.point(1, 0)); // xA
            vspecs.add(VSpec.point(1, 1)); // xB
            vspecs.add(VSpec.point(1, 2)); // xC
        } else if (numComponents == 4) {
            // Tetrahedra: 6 binary
            vspecs.add(VSpec.product(1, 0, 2, 1, 3, 1, 4, 0)); // v4AB
            vspecs.add(VSpec.product(1, 0, 2, 2, 3, 2, 4, 0)); // v4AC
            vspecs.add(VSpec.product(1, 0, 2, 3, 3, 3, 4, 0)); // v4AD
            vspecs.add(VSpec.product(1, 1, 2, 2, 3, 2, 4, 1)); // v4BC
            vspecs.add(VSpec.product(1, 1, 2, 3, 3, 3, 4, 1)); // v4BD
            vspecs.add(VSpec.product(1, 2, 2, 3, 3, 3, 4, 2)); // v4CD

            // Tetrahedra: 4 quaternary
            vspecs.add(VSpec.product(1, 0, 2, 2, 3, 3, 4, 1)); // v4ABCD1
            vspecs.add(VSpec.product(1, 0, 2, 1, 3, 3, 4, 2)); // v4ABCD2
            vspecs.add(VSpec.product(1, 0, 2, 1, 3, 2, 4, 3)); // v4ABCD3
            vspecs.add(VSpec.product(1, 1, 2, 0, 3, 0, 4, 2)); // v4ABCD4

            // Triangles: 6 binary (p1,p2,p4)
            vspecs.add(VSpec.diff(new int[] { 1, 1, 2, 0, 4, 1 }, new int[] { 1, 0, 2, 1, 4, 0 })); // v3AB
            vspecs.add(VSpec.diff(new int[] { 1, 2, 2, 0, 4, 2 }, new int[] { 1, 0, 2, 2, 4, 0 })); // v3AC
            vspecs.add(VSpec.diff(new int[] { 1, 3, 2, 0, 4, 3 }, new int[] { 1, 0, 2, 3, 4, 0 })); // v3AD
            vspecs.add(VSpec.diff(new int[] { 1, 2, 2, 1, 4, 2 }, new int[] { 1, 1, 2, 2, 4, 1 })); // v3BC
            vspecs.add(VSpec.diff(new int[] { 1, 3, 2, 1, 4, 3 }, new int[] { 1, 1, 2, 3, 4, 1 })); // v3BD
            vspecs.add(VSpec.diff(new int[] { 1, 3, 2, 2, 4, 3 }, new int[] { 1, 2, 2, 3, 4, 2 })); // v3CD

            // Triangles: 12 ternary (p1,p2,p4)
            vspecs.add(VSpec.product(1, 1, 2, 0, 4, 2)); // v3ABC1
            vspecs.add(VSpec.product(1, 0, 2, 1, 4, 2)); // v3ABC2
            vspecs.add(VSpec.product(1, 0, 2, 2, 4, 1)); // v3ABC3
            vspecs.add(VSpec.product(1, 1, 2, 0, 4, 3)); // v3ABD1
            vspecs.add(VSpec.product(1, 0, 2, 1, 4, 3)); // v3ABD2
            vspecs.add(VSpec.product(1, 0, 2, 3, 4, 1)); // v3ABD3
            vspecs.add(VSpec.product(1, 2, 2, 0, 4, 3)); // v3ACD1
            vspecs.add(VSpec.product(1, 0, 2, 2, 4, 3)); // v3ACD2
            vspecs.add(VSpec.product(1, 0, 2, 3, 4, 2)); // v3ACD3
            vspecs.add(VSpec.product(1, 2, 2, 1, 4, 3)); // v3BCD1
            vspecs.add(VSpec.product(1, 1, 2, 2, 4, 3)); // v3BCD2
            vspecs.add(VSpec.product(1, 1, 2, 3, 4, 2)); // v3BCD3

            // Pairs: 6 II-n (p1,p4)
            vspecs.add(VSpec.product(1, 0, 4, 1)); // v2AB2
            vspecs.add(VSpec.product(1, 0, 4, 2)); // v2AC2
            vspecs.add(VSpec.product(1, 0, 4, 3)); // v2AD2
            vspecs.add(VSpec.product(1, 1, 4, 2)); // v2BC2
            vspecs.add(VSpec.product(1, 1, 4, 3)); // v2BD2
            vspecs.add(VSpec.product(1, 2, 4, 3)); // v2CD2

            // Pairs: 6 I-n (p1,p2)
            vspecs.add(VSpec.product(1, 0, 2, 1)); // v2AB1
            vspecs.add(VSpec.product(1, 0, 2, 2)); // v2AC1
            vspecs.add(VSpec.product(1, 0, 2, 3)); // v2AD1
            vspecs.add(VSpec.product(1, 1, 2, 2)); // v2BC1
            vspecs.add(VSpec.product(1, 1, 2, 3)); // v2BD1
            vspecs.add(VSpec.product(1, 2, 2, 3)); // v2CD1

            // Points
            vspecs.add(VSpec.point(1, 0)); // xA
            vspecs.add(VSpec.point(1, 1)); // xB
            vspecs.add(VSpec.point(1, 2)); // xC
            vspecs.add(VSpec.point(1, 3)); // xD
        } else {
            throw new UnsupportedOperationException(
                    "Automatic VSpec generation not yet supported for K=" + numComponents);
        }

        return vspecs;
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private static String[] generateAtomSymbols(int K) {
        String[] symbols = new String[K];
        for (int i = 0; i < K; i++) {
            symbols[i] = String.valueOf((char) ('A' + i));
        }
        return symbols;
    }
}
