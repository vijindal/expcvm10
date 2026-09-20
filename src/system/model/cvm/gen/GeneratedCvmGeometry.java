package system.model.cvm.gen;

import java.util.List;
import java.util.function.Consumer;

import system.model.cvm.gen.ClusterCFIdentificationPipeline.PipelineResult;

/**
 * Generated equivalent of CEWorkbench's {@code org.ce.model.cvm.CvmGeometry}:
 * the immutable product of running the Stages 1-4 cluster/CF/C-matrix/CVCF
 * pipeline for one (structure, model, numComponents) identity.
 *
 * <p>Ported concept, adapted construction: CEWorkbench's {@code CvmGeometry
 * .build} loads structure/symmetry data from its filesystem
 * {@code Workspace}/{@code InputLoader}; here {@link StructureFileLoader}
 * plays that role, reading the same verbatim-copied {@code inputs/clus/
 * BCC_A2-T.txt} and {@code inputs/sym/BCC_A2-SG.txt} files under
 * expcvm10's own {@code inputs/} directory. The pipeline stages themselves
 * ({@link ClusterCFIdentificationPipeline}, {@link CMatrixPipeline},
 * {@link CvCfBasis}) are unchanged from the port.</p>
 *
 * <p>This class deliberately stops at cluster algebra: it holds no
 * Hamiltonian and no temperature, exactly like the CEWorkbench original.
 * {@code system.model.cvm.CvmGibbsModel} (the existing, validated production
 * model) is the next layer up, and is not modified by this class.</p>
 */
public final class GeneratedCvmGeometry {

    public final String elements;
    public final String structure;
    public final int numComponents;

    public final int tcdis;
    public final double[] kb;
    public final double[] mhdis;
    public final double[][] mh;
    public final int[] lc;
    public final PipelineResult pipelineResult;

    public final List<List<double[][]>> cmat;
    public final int[][] lcv;
    public final List<List<int[]>> wcv;

    public final CvCfBasis basis;
    public final int ncf;
    public final int tcf;

    private GeneratedCvmGeometry(
            String elements, String structure, int numComponents,
            int tcdis, double[] kb, double[] mhdis, double[][] mh, int[] lc, PipelineResult pipelineResult,
            List<List<double[][]>> cmat, int[][] lcv, List<List<int[]>> wcv,
            CvCfBasis basis, int ncf, int tcf) {
        this.elements = elements;
        this.structure = structure;
        this.numComponents = numComponents;
        this.tcdis = tcdis;
        this.kb = kb;
        this.mhdis = mhdis;
        this.mh = mh;
        this.lc = lc;
        this.pipelineResult = pipelineResult;
        this.cmat = cmat;
        this.lcv = lcv;
        this.wcv = wcv;
        this.basis = basis;
        this.ncf = ncf;
        this.tcf = tcf;
    }

    /**
     * Builds the generated geometry for disordered binary BCC_A2, T-model
     * (tetrahedron approximation) -- the immediate validation target of
     * this generation layer.
     */
    public static GeneratedCvmGeometry buildBccA2Binary(Consumer<String> progressSink) {
        return buildBccA2("A-B", 2, progressSink);
    }

    /**
     * Builds the generated BCC_A2 T-model geometry for an arbitrary number
     * of components K, using the CVCF basis registered in {@link CvCfBasis}
     * for that K (currently K=2/3/4 are registered, ported from
     * CEWorkbench). {@code elements} is a hyphen-separated element string
     * whose length must equal {@code numComponents}, e.g. {@code
     * "Nb-Ti-V-Zr"} for K=4.
     *
     * <p>Reads the precomputed {@code inputs/clus/BCC_A2-T.txt}/{@code
     * inputs/sym/BCC_A2-SG.txt} files via {@link StructureFileLoader}. The
     * generic, structure-driven equivalent of Stages 1-3 lives in {@code
     * system.model.cvm.gen.structure} ({@code MaximalClusterGenerator}/
     * {@code SpaceGroupGenerator}); Stage 4 ({@link CvCfBasis}) still
     * requires this method's file-based maximal cluster, since {@link
     * CvCfBasis}'s registered site coordinates are matched by exact
     * position and a generated cluster's absolute placement is an
     * arbitrary choice among symmetry-equivalent candidates.</p>
     */
    public static GeneratedCvmGeometry buildBccA2(String elements, int numComponents, Consumer<String> progressSink) {
        List<Cluster> maximalClusters = StructureFileLoader.parseClusterFile("clus/BCC_A2-T.txt");
        maximalClusters.replaceAll(Cluster::sorted);
        SpaceGroup spaceGroup = StructureFileLoader.parseSpaceGroup("BCC_A2-SG");

        return buildFromClustersAndSymmetry(elements, "BCC_A2", "T", numComponents,
                maximalClusters, spaceGroup, progressSink);
    }

    /**
     * Shared Stages 1-4 pipeline invocation, given already-resolved
     * maximal cluster(s) and space group.
     */
    private static GeneratedCvmGeometry buildFromClustersAndSymmetry(
            String elements, String structure, String model, int numComponents,
            List<Cluster> maximalClusters, SpaceGroup spaceGroup, Consumer<String> progressSink) {

        // BCC_A2 is its own disordered parent: same clusters, same
        // symmetry, identity transform (matches CvmGeometry.build's
        // resolveParentStructure/resolveClusterFile/resolveSymmetryGroup
        // behaviour for a self-parent structure).
        PipelineResult pr = ClusterCFIdentificationPipeline.run(
                maximalClusters, spaceGroup.getOperations(),
                maximalClusters, spaceGroup.getOperations(),
                spaceGroup.getRotateMat(), spaceGroup.getTranslateMat(),
                numComponents, progressSink);

        CMatrixPipeline.CMatrixData cmatOrth = CMatrixPipeline.run(
                pr, maximalClusters, numComponents, progressSink);

        CvCfBasis basis = CvCfBasis.generate(structure, pr, cmatOrth, model, progressSink);

        return new GeneratedCvmGeometry(
                elements, structure, numComponents,
                pr.getTcdis(), pr.getKbdis(), pr.getMhdis(), pr.getMh(), pr.getLc(), pr,
                basis.cvcfCMatrixData.getCmat(), basis.cvcfCMatrixData.getLcv(), basis.cvcfCMatrixData.getWcv(),
                basis, basis.numNonPointCfs, basis.totalCfs());
    }

    /**
     * Concatenates non-point CFs and composition into the full CVCF vector
     * {@code uFull = [u ; x]} this geometry's C-matrix multiplies against.
     */
    public double[] buildFullVector(double[] u, double[] x) {
        if (x.length != numComponents) {
            throw new IllegalArgumentException("x.length=" + x.length + " != numComponents=" + numComponents);
        }
        if (tcf - ncf != numComponents) {
            throw new IllegalStateException("tcf - ncf = " + (tcf - ncf) + " point CFs, expected numComponents="
                    + numComponents + " (ordered-phase point sets are not supported by this method).");
        }
        double[] full = new double[tcf];
        System.arraycopy(u, 0, full, 0, ncf);
        System.arraycopy(x, 0, full, ncf, numComponents);
        return full;
    }

    /** Evaluates every cluster variable: {@code cv[t][j][v] = sum_k cmat[t][j][v][k] * uFull[k]}. */
    public double[][][] evaluateCVs(double[] u, double[] x) {
        return evaluateCVsFull(buildFullVector(u, x));
    }

    public double[][][] evaluateCVsFull(double[] uFull) {
        return CMatrixPipeline.evaluateCVs(uFull, cmat, lcv, tcdis, lc);
    }

    /**
     * Checks the geometry's own structural invariants -- shape agreement
     * and configuration-count conservation. Pure combinatorics: no
     * Hamiltonian, no temperature.
     */
    public void validate() {
        require(cmat.size() == tcdis, "cmat.size()=" + cmat.size() + " != tcdis=" + tcdis);
        require(lc.length == tcdis, "lc.length=" + lc.length + " != tcdis=" + tcdis);
        require(lcv.length == tcdis, "lcv.length=" + lcv.length + " != tcdis=" + tcdis);
        require(wcv.size() == tcdis, "wcv.size()=" + wcv.size() + " != tcdis=" + tcdis);
        require(kb.length == tcdis, "kb.length=" + kb.length + " != tcdis=" + tcdis);
        require(mhdis.length == tcdis, "mhdis.length=" + mhdis.length + " != tcdis=" + tcdis);
        require(mh.length == tcdis, "mh.length=" + mh.length + " != tcdis=" + tcdis);

        int numPointCfs = tcf - ncf;
        require(numPointCfs >= numComponents,
                "tcf - ncf = " + numPointCfs + " point CFs, fewer than K=" + numComponents);
        require(tcf == basis.totalCfs(), "tcf=" + tcf + " != basis.totalCfs()=" + basis.totalCfs());
        require(ncf == basis.numNonPointCfs, "ncf=" + ncf + " != basis.numNonPointCfs=" + basis.numNonPointCfs);

        for (int t = 0; t < tcdis; t++) {
            require(cmat.get(t).size() == lc[t], "at t=" + t + ": cmat.get(t).size()=" + cmat.get(t).size() + " != lc[t]=" + lc[t]);
            require(lcv[t].length == lc[t], "at t=" + t + ": lcv[t].length=" + lcv[t].length + " != lc[t]=" + lc[t]);
            require(wcv.get(t).size() == lc[t], "at t=" + t + ": wcv.get(t).size()=" + wcv.get(t).size() + " != lc[t]=" + lc[t]);
            require(mh[t].length == lc[t], "at t=" + t + ": mh[t].length=" + mh[t].length + " != lc[t]=" + lc[t]);

            int size = clusterSize(t);
            long expected = ipow(numComponents, size);

            for (int j = 0; j < lc[t]; j++) {
                double[][] block = cmat.get(t).get(j);
                int[] w = wcv.get(t).get(j);
                int nv = lcv[t][j];

                require(block.length == nv, "at (t=" + t + ", j=" + j + "): cmat rows=" + block.length + " != lcv[t][j]=" + nv);
                require(w.length == nv, "at (t=" + t + ", j=" + j + "): wcv length=" + w.length + " != lcv[t][j]=" + nv);
                for (int v = 0; v < nv; v++) {
                    require(block[v].length == tcf, "at (t=" + t + ", j=" + j + ", v=" + v + "): cmat columns="
                            + block[v].length + " != tcf=" + tcf);
                }

                long wsum = 0;
                for (int v = 0; v < nv; v++) {
                    require(w[v] > 0, "at (t=" + t + ", j=" + j + ", v=" + v + "): wcv=" + w[v] + " must be positive");
                    wsum += w[v];
                }
                require(wsum == expected, "at (t=" + t + ", j=" + j + "): sum(wcv)=" + wsum + " != K^size="
                        + numComponents + "^" + size + "=" + expected);
            }
        }
    }

    private int clusterSize(int t) {
        return pipelineResult.getDisClusData().getClusCoordList().get(t).getAllSites().size();
    }

    private static long ipow(int base, int exp) {
        long r = 1;
        for (int i = 0; i < exp; i++) r *= base;
        return r;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("GeneratedCvmGeometry invariant violated: " + message);
    }

    @Override
    public String toString() {
        return String.format("GeneratedCvmGeometry[%s %s K=%d, tcdis=%d, ncf=%d, tcf=%d]",
                elements, structure, numComponents, tcdis, ncf, tcf);
    }
}
