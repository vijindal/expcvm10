package system.model.cvm.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import system.model.cvm.gen.ClusterCFIdentificationPipeline.PipelineResult;
import system.model.cvm.gen.ClusterKeys.CFIndex;
import system.model.cvm.gen.ClusterPrimitives.Position;

/**
 * Stage 4: transforms the orthogonal-basis C-matrix into the CVCF
 * ("cluster-variable correlation function") basis -- physically meaningful
 * cluster probabilities/order parameters (e.g. {@code v4AB}, {@code xA})
 * instead of the orthogonal polynomial CFs Stage 3 produces.
 *
 * <p>Ported (logic unchanged) from CEWorkbench's
 * {@code org.ce.model.cvm.CvCfBasis}. The transform itself
 * ({@code register}/{@code VSpec}/{@code T}/{@code M}) is a hand-declared,
 * per-(structure, model, numComponents) table in CEWorkbench too -- it is
 * not derived from symmetry data even there, so this is a faithful port of
 * the reference architecture, not a shortcut. Only the {@code BCC_A2 / T /
 * K=2} registration is transcribed (the immediate validation target); the
 * mechanism itself is fully general and can register more
 * structures/models/K later exactly as CEWorkbench does.</p>
 */
public final class CvCfBasis {

    public final String structurePhase;
    public final String model;
    public final int numComponents;
    public final List<String> cfNames;
    public final int numNonPointCfs;
    public final double[][] T;
    public final double[][] Tinv;
    public final CMatrixPipeline.CMatrixData cvcfCMatrixData;

    private CvCfBasis(String structurePhase, String model, int numComponents,
            List<String> cfNames, int numNonPointCfs,
            double[][] T, double[][] Tinv, CMatrixPipeline.CMatrixData cvcfCMatrixData) {
        this.structurePhase = structurePhase;
        this.model = (model == null ? "" : model.toUpperCase());
        this.numComponents = numComponents;
        this.cfNames = cfNames;
        this.numNonPointCfs = numNonPointCfs;
        this.T = T;
        this.Tinv = Tinv;
        this.cvcfCMatrixData = cvcfCMatrixData;
    }

    public static boolean isSupported(String structurePhase, String model, int numComponents) {
        return REGISTRY.containsKey(structurePhase + "_" + model.toUpperCase() + "_" + numComponents);
    }

    public static String supportedSummary() {
        return "Supported combinations: " + String.join(", ", REGISTRY.keySet());
    }

    public int totalCfs() { return cfNames.size(); }

    private double[][] resolvedTinv() {
        if (Tinv != null) return Tinv;
        return LinearAlgebra.invert(T);
    }

    /** v_random = T^-1 * u_random. */
    public double[] computeRandomCvcfCFs(double[] moleFractions, PipelineResult pr) {
        return computeRandomCvcfCFs(moleFractions, pr, resolvedTinv());
    }

    private static double[] computeRandomCvcfCFs(double[] moleFractions, PipelineResult pr, double[][] mInv) {
        double[] uOrth = pr.computeRandomCFs(moleFractions);
        int dim = mInv.length;
        double[] vFull = new double[dim];
        for (int i = 0; i < dim; i++) {
            double sum = 0;
            for (int k = 0; k < dim; k++) sum += mInv[i][k] * uOrth[k];
            vFull[i] = sum;
        }
        return vFull;
    }

    // =========================================================================
    // VSpec: declarative description of one CVCF variable as a linear
    // combination of site-occupation-probability products.
    // =========================================================================

    public static final class VSpec {

        public static final class Term {
            public final double coefficient;
            /** Flat [site1, atom1, site2, atom2, ...]. */
            public final int[] siteAtomPairs;
            Term(double coefficient, int[] siteAtomPairs) {
                this.coefficient = coefficient;
                this.siteAtomPairs = siteAtomPairs;
            }
        }

        public final List<Term> terms;

        private VSpec(List<Term> terms) { this.terms = Collections.unmodifiableList(terms); }

        /** v = p[s1][a1] * p[s2][a2] * ... */
        public static VSpec product(int... siteAtomPairs) {
            return combo(1.0, siteAtomPairs);
        }

        /** v = product(plusPairs) - product(minusPairs). */
        public static VSpec diff(int[] plusPairs, int[] minusPairs) {
            List<Term> terms = new ArrayList<>();
            terms.add(makeTerm(1.0, plusPairs));
            terms.add(makeTerm(-1.0, minusPairs));
            return new VSpec(terms);
        }

        /** Convenience: single-site probability, i.e. point CF. v = p[site][atom]. */
        public static VSpec point(int logicalSite, int atom) {
            return product(logicalSite, atom);
        }

        /** v = coefficient * product(siteAtomPairs). */
        public static VSpec combo(double coefficient, int[] siteAtomPairs) {
            List<Term> terms = new ArrayList<>();
            terms.add(makeTerm(coefficient, siteAtomPairs));
            return new VSpec(terms);
        }

        private static Term makeTerm(double coefficient, int[] siteAtomPairs) {
            if (siteAtomPairs.length % 2 != 0) throw new IllegalArgumentException("siteAtomPairs must be even-length");
            return new Term(coefficient, siteAtomPairs);
        }
    }

    private static final class Definition {
        final double[][] logicalSiteCoords;
        final List<String> cfNames;
        final List<VSpec> vSpecs;
        final int numPointCfs;

        Definition(double[][] logicalSiteCoords, List<String> cfNames, List<VSpec> vSpecs, int numPointCfs) {
            this.logicalSiteCoords = logicalSiteCoords;
            this.cfNames = Collections.unmodifiableList(new ArrayList<>(cfNames));
            this.vSpecs = Collections.unmodifiableList(new ArrayList<>(vSpecs));
            this.numPointCfs = numPointCfs;
        }
    }

    private static final Map<String, Definition> REGISTRY = new LinkedHashMap<>();

    private static void register(String structurePhase, String model, int numComponents,
            double[][] coords, List<String> cfNames, List<VSpec> vSpecs) {
        REGISTRY.put(structurePhase + "_" + model.toUpperCase() + "_" + numComponents,
                new Definition(coords, cfNames, vSpecs, numComponents));
    }

    static {
        // -----------------------------------------------------------------
        // BCC_A2 | T-model | binary (K=2)
        //
        // Logical site coordinates (fractional):
        //   p1 = {0.0, 0.0, 0.0}
        //   p2 = {0.5, -0.5, 0.5}
        //   p3 = {0.5, 0.5, 0.5}
        //   p4 = {1.0, 0.0, 0.0}
        //
        // Pair types:
        //   p1-p4 and p2-p3 are II-n pairs (used in v22AB)
        //   p1-p2 (and others) are I-n pairs (used in v21AB)
        //
        // Atom indices: A=0, B=1
        //
        // Transcribed verbatim from CEWorkbench's org.ce.model.cvm.CvCfBasis.
        // -----------------------------------------------------------------
        register("BCC_A2", "T", 2,
                new double[][] {
                        { 0.0, 0.0, 0.0 }, // p1
                        { 0.5, -0.5, 0.5 }, // p2
                        { 0.5, 0.5, 0.5 }, // p3
                        { 1.0, 0.0, 0.0 } // p4
                },
                List.of("v4AB", "v3AB", "v22AB", "v21AB", "xA", "xB"),
                List.of(
                        // v4AB = p[1][A]*p[2][B]*p[3][B]*p[4][A]
                        VSpec.product(1, 0, 2, 1, 3, 1, 4, 0),

                        // v3AB = p[1][A]*p[2][B]*p[3][B] - p[1][B]*p[2][A]*p[3][A]
                        VSpec.diff(
                                new int[] { 1, 0, 2, 1, 3, 1 },
                                new int[] { 1, 1, 2, 0, 3, 0 }),

                        // v22AB = p[1][A]*p[4][B] (II-n pair)
                        VSpec.product(1, 0, 4, 1),

                        // v21AB = p[1][A]*p[2][B] (I-n pair)
                        VSpec.product(1, 0, 2, 1),

                        // xA = p[1][A]
                        VSpec.point(1, 0),

                        // xB = p[1][B]
                        VSpec.point(1, 1)));

        // -----------------------------------------------------------------
        // BCC_A2 | T-model | ternary (K=3)
        //
        // Same logical site coordinates as binary:
        //   p1 = {0.0, 0.0, 0.0}
        //   p2 = {0.5, -0.5, 0.5}
        //   p3 = {0.5, 0.5, 0.5}
        //   p4 = {1.0, 0.0, 0.0}
        //
        // Atom indices: A=0, B=1, C=2
        //
        // 21 CVs total: 6 tetr + 6 tri + 6 pair + 3 point
        //   Tetrahedron (6): 3 binary (v4AB,v4AC,v4BC) + 3 ternary (v4ABC1/2/3)
        //   Triangle (6):    3 binary (v3AB,v3AC,v3BC) + 3 ternary (v3ABC1/2/3)
        //   Pair (6):        3 II-n (v22AB,v22AC,v22BC) + 3 I-n (v21AB,v21AC,v21BC)
        //   Point (3):       xA, xB, xC
        //
        // Transcribed verbatim from CEWorkbench's org.ce.model.cvm.CvCfBasis.
        // -----------------------------------------------------------------
        register("BCC_A2", "T", 3,
                new double[][] {
                        { 0.0, 0.0, 0.0 }, // p1
                        { 0.5, -0.5, 0.5 }, // p2
                        { 0.5, 0.5, 0.5 }, // p3
                        { 1.0, 0.0, 0.0 } // p4
                },
                List.of(
                        "v4AB", "v4AC", "v4BC",
                        "v4ABC1", "v4ABC2", "v4ABC3",
                        "v3AB", "v3AC", "v3BC",
                        "v3ABC1", "v3ABC2", "v3ABC3",
                        "v22AB", "v22AC", "v22BC",
                        "v21AB", "v21AC", "v21BC",
                        "xA", "xB", "xC"),
                List.of(
                        // ---- tetrahedra ----

                        // v4AB = p[1][A]*p[2][B]*p[3][B]*p[4][A]
                        VSpec.product(1, 0, 2, 1, 3, 1, 4, 0),

                        // v4AC = p[1][A]*p[2][C]*p[3][C]*p[4][A]
                        VSpec.product(1, 0, 2, 2, 3, 2, 4, 0),

                        // v4BC = p[1][B]*p[2][C]*p[3][C]*p[4][B]
                        VSpec.product(1, 1, 2, 2, 3, 2, 4, 1),

                        // v4ABC1 = p[1][B]*p[2][A]*p[3][A]*p[4][C]
                        VSpec.product(1, 1, 2, 0, 3, 0, 4, 2),

                        // v4ABC2 = p[1][A]*p[2][B]*p[3][B]*p[4][C]
                        VSpec.product(1, 0, 2, 1, 3, 1, 4, 2),

                        // v4ABC3 = p[1][A]*p[2][C]*p[3][C]*p[4][B]
                        VSpec.product(1, 0, 2, 2, 3, 2, 4, 1),

                        // ---- triangles ----

                        // v3AB = p[1][A]*p[2][B]*p[3][B] - p[1][B]*p[2][A]*p[3][A]
                        VSpec.diff(
                                new int[] { 1, 0, 2, 1, 3, 1 },
                                new int[] { 1, 1, 2, 0, 3, 0 }),

                        // v3AC = p[1][A]*p[2][C]*p[3][C] - p[1][C]*p[2][A]*p[3][A]
                        VSpec.diff(
                                new int[] { 1, 0, 2, 2, 3, 2 },
                                new int[] { 1, 2, 2, 0, 3, 0 }),

                        // v3BC = p[1][B]*p[2][C]*p[3][C] - p[1][C]*p[2][B]*p[3][B]
                        VSpec.diff(
                                new int[] { 1, 1, 2, 2, 3, 2 },
                                new int[] { 1, 2, 2, 1, 3, 1 }),

                        // v3ABC1 = p[1][C]*p[2][A]*p[3][B]
                        VSpec.product(1, 2, 2, 0, 3, 1),

                        // v3ABC2 = p[1][B]*p[2][A]*p[3][C]
                        VSpec.product(1, 1, 2, 0, 3, 2),

                        // v3ABC3 = p[1][A]*p[2][B]*p[3][C]
                        VSpec.product(1, 0, 2, 1, 3, 2),

                        // ---- pairs ----

                        // v22AB = p[1][A]*p[4][B] (II-n pair)
                        VSpec.product(1, 0, 4, 1),

                        // v22AC = p[1][A]*p[4][C] (II-n pair)
                        VSpec.product(1, 0, 4, 2),

                        // v22BC = p[1][B]*p[4][C] (II-n pair)
                        VSpec.product(1, 1, 4, 2),

                        // v21AB = p[1][A]*p[2][B] (I-n pair)
                        VSpec.product(1, 0, 2, 1),

                        // v21AC = p[1][A]*p[2][C] (I-n pair)
                        VSpec.product(1, 0, 2, 2),

                        // v21BC = p[1][B]*p[2][C] (I-n pair)
                        VSpec.product(1, 1, 2, 2),

                        // ---- points ----

                        // xA = p[1][A]
                        VSpec.point(1, 0),

                        // xB = p[1][B]
                        VSpec.point(1, 1),

                        // xC = p[1][C]
                        VSpec.point(1, 2)));

        // -----------------------------------------------------------------
        // BCC_A2 | T-model | quaternary (K=4)
        //
        // Same logical site coordinates as binary/ternary.
        // Atom indices: A=0, B=1, C=2, D=3
        //
        // 55 CVs total: 21 tetr + 18 tri + 12 pair + 4 point
        //   Tetrahedron (21): 6 binary + 12 ternary (ABC/ABD/ACD/BCD, 3 each) + 3 quaternary
        //   Triangle (18):    6 binary + 12 ternary (ABC/ABD/ACD/BCD, 3 each)
        //   Pair (12):        6 II-n + 6 I-n
        //   Point (4):        xA, xB, xC, xD
        //
        // Transcribed verbatim from CEWorkbench's org.ce.model.cvm.CvCfBasis.
        // -----------------------------------------------------------------
        register("BCC_A2", "T", 4,
                new double[][] {
                        { 0.0, 0.0, 0.0 }, // p1
                        { 0.5, -0.5, 0.5 }, // p2
                        { 0.5, 0.5, 0.5 }, // p3
                        { 1.0, 0.0, 0.0 } // p4
                },
                List.of(
                        "v4AB", "v4AC", "v4AD", "v4BC", "v4BD", "v4CD",
                        "v4ABC1", "v4ABC2", "v4ABC3",
                        "v4ABD1", "v4ABD2", "v4ABD3",
                        "v4ACD1", "v4ACD2", "v4ACD3",
                        "v4BCD1", "v4BCD2", "v4BCD3",
                        "v4ABCD1", "v4ABCD2", "v4ABCD3",
                        "v3AB", "v3AC", "v3AD", "v3BC", "v3BD", "v3CD",
                        "v3ABC1", "v3ABC2", "v3ABC3",
                        "v3ABD1", "v3ABD2", "v3ABD3",
                        "v3ACD1", "v3ACD2", "v3ACD3",
                        "v3BCD1", "v3BCD2", "v3BCD3",
                        "v22AB", "v22AC", "v22AD", "v22BC", "v22BD", "v22CD",
                        "v21AB", "v21AC", "v21AD", "v21BC", "v21BD", "v21CD",
                        "xA", "xB", "xC", "xD"),
                List.of(
                        // ---- tetrahedra: 6 binary ----

                        // v4AB = p[1][A]*p[2][B]*p[3][B]*p[4][A]
                        VSpec.product(1, 0, 2, 1, 3, 1, 4, 0),
                        // v4AC = p[1][A]*p[2][C]*p[3][C]*p[4][A]
                        VSpec.product(1, 0, 2, 2, 3, 2, 4, 0),
                        // v4AD = p[1][A]*p[2][D]*p[3][D]*p[4][A]
                        VSpec.product(1, 0, 2, 3, 3, 3, 4, 0),
                        // v4BC = p[1][B]*p[2][C]*p[3][C]*p[4][B]
                        VSpec.product(1, 1, 2, 2, 3, 2, 4, 1),
                        // v4BD = p[1][B]*p[2][D]*p[3][D]*p[4][B]
                        VSpec.product(1, 1, 2, 3, 3, 3, 4, 1),
                        // v4CD = p[1][C]*p[2][D]*p[3][D]*p[4][C]
                        VSpec.product(1, 2, 2, 3, 3, 3, 4, 2),

                        // ---- tetrahedra: 12 ternary ----

                        // v4ABC1 = p[1][B]*p[2][A]*p[3][A]*p[4][C]
                        VSpec.product(1, 1, 2, 0, 3, 0, 4, 2),
                        // v4ABC2 = p[1][A]*p[2][B]*p[3][B]*p[4][C]
                        VSpec.product(1, 0, 2, 1, 3, 1, 4, 2),
                        // v4ABC3 = p[1][A]*p[2][C]*p[3][C]*p[4][B]
                        VSpec.product(1, 0, 2, 2, 3, 2, 4, 1),

                        // v4ABD1 = p[1][B]*p[2][A]*p[3][A]*p[4][D]
                        VSpec.product(1, 1, 2, 0, 3, 0, 4, 3),
                        // v4ABD2 = p[1][A]*p[2][B]*p[3][B]*p[4][D]
                        VSpec.product(1, 0, 2, 1, 3, 1, 4, 3),
                        // v4ABD3 = p[1][A]*p[2][D]*p[3][D]*p[4][B]
                        VSpec.product(1, 0, 2, 3, 3, 3, 4, 1),

                        // v4ACD1 = p[1][C]*p[2][A]*p[3][A]*p[4][D]
                        VSpec.product(1, 2, 2, 0, 3, 0, 4, 3),
                        // v4ACD2 = p[1][A]*p[2][C]*p[3][C]*p[4][D]
                        VSpec.product(1, 0, 2, 2, 3, 2, 4, 3),
                        // v4ACD3 = p[1][A]*p[2][D]*p[3][D]*p[4][C]
                        VSpec.product(1, 0, 2, 3, 3, 3, 4, 2),

                        // v4BCD1 = p[1][C]*p[2][B]*p[3][B]*p[4][D]
                        VSpec.product(1, 2, 2, 1, 3, 1, 4, 3),
                        // v4BCD2 = p[1][B]*p[2][C]*p[3][C]*p[4][D]
                        VSpec.product(1, 1, 2, 2, 3, 2, 4, 3),
                        // v4BCD3 = p[1][B]*p[2][D]*p[3][D]*p[4][C]
                        VSpec.product(1, 1, 2, 3, 3, 3, 4, 2),

                        // ---- tetrahedra: 3 quaternary ----

                        // v4ABCD1 = p[1][A]*p[2][C]*p[3][D]*p[4][B]
                        VSpec.product(1, 0, 2, 2, 3, 3, 4, 1),
                        // v4ABCD2 = p[1][A]*p[2][B]*p[3][D]*p[4][C]
                        VSpec.product(1, 0, 2, 1, 3, 3, 4, 2),
                        // v4ABCD3 = p[1][A]*p[2][B]*p[3][C]*p[4][D]
                        VSpec.product(1, 0, 2, 1, 3, 2, 4, 3),

                        // ---- triangles: 6 binary (sites p1,p2,p4) ----

                        // v3AB = p[1][B]*p[2][A]*p[4][B] - p[1][A]*p[2][B]*p[4][A]
                        VSpec.diff(new int[] { 1, 1, 2, 0, 4, 1 }, new int[] { 1, 0, 2, 1, 4, 0 }),
                        // v3AC = p[1][C]*p[2][A]*p[4][C] - p[1][A]*p[2][C]*p[4][A]
                        VSpec.diff(new int[] { 1, 2, 2, 0, 4, 2 }, new int[] { 1, 0, 2, 2, 4, 0 }),
                        // v3AD = p[1][D]*p[2][A]*p[4][D] - p[1][A]*p[2][D]*p[4][A]
                        VSpec.diff(new int[] { 1, 3, 2, 0, 4, 3 }, new int[] { 1, 0, 2, 3, 4, 0 }),
                        // v3BC = p[1][C]*p[2][B]*p[4][C] - p[1][B]*p[2][C]*p[4][B]
                        VSpec.diff(new int[] { 1, 2, 2, 1, 4, 2 }, new int[] { 1, 1, 2, 2, 4, 1 }),
                        // v3BD = p[1][D]*p[2][B]*p[4][D] - p[1][B]*p[2][D]*p[4][B]
                        VSpec.diff(new int[] { 1, 3, 2, 1, 4, 3 }, new int[] { 1, 1, 2, 3, 4, 1 }),
                        // v3CD = p[1][D]*p[2][C]*p[4][D] - p[1][C]*p[2][D]*p[4][C]
                        VSpec.diff(new int[] { 1, 3, 2, 2, 4, 3 }, new int[] { 1, 2, 2, 3, 4, 2 }),

                        // ---- triangles: 12 ternary (sites p1,p2,p4) ----

                        // v3ABC1 = p[1][B]*p[2][A]*p[4][C]
                        VSpec.product(1, 1, 2, 0, 4, 2),
                        // v3ABC2 = p[1][A]*p[2][B]*p[4][C]
                        VSpec.product(1, 0, 2, 1, 4, 2),
                        // v3ABC3 = p[1][A]*p[2][C]*p[4][B]
                        VSpec.product(1, 0, 2, 2, 4, 1),

                        // v3ABD1 = p[1][B]*p[2][A]*p[4][D]
                        VSpec.product(1, 1, 2, 0, 4, 3),
                        // v3ABD2 = p[1][A]*p[2][B]*p[4][D]
                        VSpec.product(1, 0, 2, 1, 4, 3),
                        // v3ABD3 = p[1][A]*p[2][D]*p[4][B]
                        VSpec.product(1, 0, 2, 3, 4, 1),

                        // v3ACD1 = p[1][C]*p[2][A]*p[4][D]
                        VSpec.product(1, 2, 2, 0, 4, 3),
                        // v3ACD2 = p[1][A]*p[2][C]*p[4][D]
                        VSpec.product(1, 0, 2, 2, 4, 3),
                        // v3ACD3 = p[1][A]*p[2][D]*p[4][C]
                        VSpec.product(1, 0, 2, 3, 4, 2),

                        // v3BCD1 = p[1][C]*p[2][B]*p[4][D]
                        VSpec.product(1, 2, 2, 1, 4, 3),
                        // v3BCD2 = p[1][B]*p[2][C]*p[4][D]
                        VSpec.product(1, 1, 2, 2, 4, 3),
                        // v3BCD3 = p[1][B]*p[2][D]*p[4][C]
                        VSpec.product(1, 1, 2, 3, 4, 2),

                        // ---- pairs: 6 II-n (p1,p4) ----

                        // v22AB = p[1][A]*p[4][B]
                        VSpec.product(1, 0, 4, 1),
                        // v22AC = p[1][A]*p[4][C]
                        VSpec.product(1, 0, 4, 2),
                        // v22AD = p[1][A]*p[4][D]
                        VSpec.product(1, 0, 4, 3),
                        // v22BC = p[1][B]*p[4][C]
                        VSpec.product(1, 1, 4, 2),
                        // v22BD = p[1][B]*p[4][D]
                        VSpec.product(1, 1, 4, 3),
                        // v22CD = p[1][C]*p[4][D]
                        VSpec.product(1, 2, 4, 3),

                        // ---- pairs: 6 I-n (p1,p2) ----

                        // v21AB = p[1][A]*p[2][B]
                        VSpec.product(1, 0, 2, 1),
                        // v21AC = p[1][A]*p[2][C]
                        VSpec.product(1, 0, 2, 2),
                        // v21AD = p[1][A]*p[2][D]
                        VSpec.product(1, 0, 2, 3),
                        // v21BC = p[1][B]*p[2][C]
                        VSpec.product(1, 1, 2, 2),
                        // v21BD = p[1][B]*p[2][D]
                        VSpec.product(1, 1, 2, 3),
                        // v21CD = p[1][C]*p[2][D]
                        VSpec.product(1, 2, 2, 3),

                        // ---- points ----

                        // xA = p[1][A]
                        VSpec.point(1, 0),
                        // xB = p[1][B]
                        VSpec.point(1, 1),
                        // xC = p[1][C]
                        VSpec.point(1, 2),
                        // xD = p[1][D]
                        VSpec.point(1, 3)));
    }

    // =========================================================================
    // Generation (logic ported verbatim from CEWorkbench's CvCfBasis.generate)
    // =========================================================================

    public static CvCfBasis generate(
            String structurePhase,
            PipelineResult pr,
            CMatrixPipeline.CMatrixData matrixData,
            String model,
            Consumer<String> sink) {

        if (!"T".equalsIgnoreCase(model)) {
            throw new UnsupportedOperationException("Dynamic generation only supported for T-model; got: " + model);
        }

        int numComponents = pr.getNumComponents();

        Definition def = REGISTRY.get(structurePhase + "_" + model.toUpperCase() + "_" + numComponents);
        if (def == null) {
            throw new IllegalArgumentException("Unregistered CVCF combination. " + supportedSummary());
        }

        List<Position> siteList = matrixData.getSiteList();
        Map<Integer, Integer> siteMap = resolveSiteMap(def.logicalSiteCoords, siteList);

        int totalCfs = pr.getTcf();
        int basisSize = def.vSpecs.size();

        Map<CFIndex, Integer> cfColMap = buildCfColumnMap(pr.getLcf());

        double[][] M = buildMMatrix(def.vSpecs, siteMap, matrixData, cfColMap, totalCfs, basisSize);

        double[][] T = LinearAlgebra.invert(M);
        double[][] Tinv = M;

        CMatrixPipeline.CMatrixData cvcfData = matrixData.transform(T);

        int numNonPointCfs = def.cfNames.size() - def.numPointCfs;

        return new CvCfBasis(structurePhase, model, numComponents,
                def.cfNames, numNonPointCfs, T, Tinv, cvcfData);
    }

    // =========================================================================
    // Site coordinate matching
    // =========================================================================

    private static Map<Integer, Integer> resolveSiteMap(double[][] logicalSiteCoords, List<Position> siteList) {
        Map<Integer, Integer> siteMap = new LinkedHashMap<>();
        for (int logIdx = 0; logIdx < logicalSiteCoords.length; logIdx++) {
            double[] coord = logicalSiteCoords[logIdx];
            Position matched = findMatchingPosition(coord, siteList);
            int physIdx = indexOf(matched, siteList);
            siteMap.put(logIdx + 1, physIdx);
        }
        return siteMap;
    }

    private static Position findMatchingPosition(double[] coord, List<Position> positions) {
        for (Position p : positions) {
            double dx = p.getX() - coord[0];
            double dy = p.getY() - coord[1];
            double dz = p.getZ() - coord[2];
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < 1e-4) {
                return p;
            }
        }
        throw new IllegalStateException(String.format(
                "No site in maximal cluster matches logical site coordinate {%.4f, %.4f, %.4f}. "
                        + "Check that the definition coordinates match the cluster input file.",
                coord[0], coord[1], coord[2]));
    }

    private static int indexOf(Position pos, List<Position> siteList) {
        for (int i = 0; i < siteList.size(); i++) {
            if (siteList.get(i).equals(pos)) return i;
        }
        return -1;
    }

    // =========================================================================
    // M matrix assembly natively derived via CMatrixPipeline
    // =========================================================================

    private static double[][] buildMMatrix(
            List<VSpec> vSpecs,
            Map<Integer, Integer> siteMap,
            CMatrixPipeline.CMatrixData matrixData,
            Map<CFIndex, Integer> cfColMap,
            int totalCfs,
            int basisSize) {

        double[][] M = new double[basisSize][basisSize];

        for (int i = 0; i < basisSize; i++) {
            VSpec spec = vSpecs.get(i);
            double[] row = new double[basisSize];
            for (VSpec.Term term : spec.terms) {
                double[] termRow = evaluateSpecTerm(term.siteAtomPairs, siteMap, matrixData, cfColMap, totalCfs);
                for (int j = 0; j < basisSize; j++) {
                    row[j] += term.coefficient * termRow[j];
                }
            }
            M[i] = row;
        }
        return M;
    }

    private static double[] evaluateSpecTerm(
            int[] termPairs,
            Map<Integer, Integer> siteMap,
            CMatrixPipeline.CMatrixData matrixData,
            Map<CFIndex, Integer> cfColMap,
            int totalCfs) {

        List<Integer> siteIndices = new ArrayList<>();
        int[] config = new int[termPairs.length / 2];
        for (int i = 0; i < termPairs.length; i += 2) {
            int logicalSite = termPairs[i];
            int atom = termPairs[i + 1];
            Integer physicalSite = siteMap.get(logicalSite);
            if (physicalSite == null) {
                throw new IllegalStateException("No physical site mapped for logical site " + logicalSite);
            }
            siteIndices.add(physicalSite);
            config[i / 2] = atom;
        }
        return matrixData.expandProbabilityExpression(siteIndices, config, totalCfs);
    }

    private static Map<CFIndex, Integer> buildCfColumnMap(int[][] lcf) {
        Map<CFIndex, Integer> map = new LinkedHashMap<>();
        int col = 0;
        for (int t = 0; t < lcf.length; t++) {
            for (int j = 0; j < lcf[t].length; j++) {
                for (int k = 0; k < lcf[t][j]; k++) {
                    map.put(new CFIndex(t, j, k), col++);
                }
            }
        }
        return map;
    }
}
