package system.model.cvm.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import system.model.cvm.gen.ClusterCFIdentificationPipeline.GroupedCFData;
import system.model.cvm.gen.ClusterCFIdentificationPipeline.PipelineResult;
import system.model.cvm.gen.ClusterKeys.CFIndex;
import system.model.cvm.gen.ClusterKeys.SiteOp;
import system.model.cvm.gen.ClusterKeys.SiteOpProductKey;
import system.model.cvm.gen.ClusterPrimitives.Position;
import system.model.cvm.gen.ClusterPrimitives.Site;
import system.model.cvm.gen.ClusterPrimitives.Sublattice;

/**
 * Stage 3: generates the orthogonal-basis C-matrix ({@code cmat}, {@code lcv},
 * {@code wcv}) that maps correlation functions to cluster (probability)
 * variables: {@code cv = cmat . uFull}.
 *
 * <p>Ported from CEWorkbench's {@code org.ce.model.cluster.CMatrixPipeline}
 * (with the unused {@code RowKey}-adjacent debug printing trimmed).</p>
 */
public final class CMatrixPipeline {

    private CMatrixPipeline() {}

    public static final class CMatrixData {
        public final List<List<double[][]>> cmat;
        public final int[][] lcv;
        public final List<List<int[]>> wcv;
        public final List<Position> siteList;
        public final int[][] cfBasisIndices;
        /** R-matrix used to expand site-occupation products (Stage 4 reuse). */
        public final double[][] rMat;
        /** Site-op-product -> CF-index substitution rules (Stage 4 reuse). */
        public final Map<SiteOpProductKey, CFIndex> substituteRules;
        /** CF-index -> orthogonal column map (Stage 4 reuse). */
        public final Map<CFIndex, Integer> cfColumnMap;

        CMatrixData(List<List<double[][]>> cmat, int[][] lcv, List<List<int[]>> wcv,
                List<Position> siteList, int[][] cfBasisIndices,
                double[][] rMat, Map<SiteOpProductKey, CFIndex> substituteRules,
                Map<CFIndex, Integer> cfColumnMap) {
            this.cmat = cmat;
            this.lcv = lcv;
            this.wcv = wcv;
            this.siteList = siteList;
            this.cfBasisIndices = cfBasisIndices;
            this.rMat = rMat;
            this.substituteRules = substituteRules;
            this.cfColumnMap = cfColumnMap;
        }

        public List<List<double[][]>> getCmat() { return cmat; }
        public int[][] getLcv() { return lcv; }
        public List<List<int[]>> getWcv() { return wcv; }
        public int[][] getCfBasisIndices() { return cfBasisIndices; }
        public List<Position> getSiteList() { return siteList; }

        /** Transforms all C-matrix blocks into a new basis: C_new = C_orth . T. */
        public CMatrixData transform(double[][] T) {
            List<List<double[][]>> newCmat = new ArrayList<>();
            for (List<double[][]> typeBlock : cmat) {
                List<double[][]> newTypeBlock = new ArrayList<>();
                for (double[][] groupBlock : typeBlock) {
                    newTypeBlock.add(LinearAlgebra.multiply(groupBlock, T));
                }
                newCmat.add(newTypeBlock);
            }
            return new CMatrixData(newCmat, lcv, wcv, siteList, cfBasisIndices, rMat, substituteRules, cfColumnMap);
        }

        /**
         * Converts a product of physical site-atom probabilities into an
         * equivalent row vector in the orthogonal CF basis, using the exact
         * substitution rules this C-matrix was itself built with (Stage 4 reuse).
         */
        public double[] expandProbabilityExpression(List<Integer> siteIndices, int[] config, int totalCfs) {
            return CMatrixPipeline.expandConfiguration(siteIndices, config, rMat, substituteRules, cfColumnMap, totalCfs);
        }
    }

    /**
     * @param maxClusters the ORDERED-PHASE maximal cluster(s) (== the
     *                     disordered parent's maximal cluster for a
     *                     disordered structure like BCC_A2).
     */
    public static CMatrixData run(
            PipelineResult pipelineResult,
            List<Cluster> maxClusters,
            int numElements,
            Consumer<String> sink) {

        emit(sink, "=== CMatrixPipeline: START ===");

        if (numElements < 2) {
            throw new ClusterGenerationException("Stage 3", "numElements must be >= 2, got " + numElements + ".");
        }
        if (maxClusters == null || maxClusters.isEmpty()) {
            throw new ClusterGenerationException("Stage 3", "maxClusters is null or empty.");
        }

        List<Position> siteList = buildSiteList(maxClusters);
        emit(sink, "[1] genSiteList -> " + siteList.size() + " unique sites");

        double[][] rMat = ClusterMath.buildRMatrix(numElements);

        List<String> basisSymbolList = new ArrayList<>();
        for (int s = 1; s < numElements; s++) basisSymbolList.add("s" + s);

        GroupedCFData groupedCF = pipelineResult.getCfData();
        List<List<List<List<Cluster>>>> cfOrbitList = groupedCF.getOrbitData();
        List<List<List<List<Cluster>>>> groupCfCoordList = groupSubClusters(maxClusters, cfOrbitList, basisSymbolList, sink);

        List<List<List<List<List<SiteOp>>>>> cfSiteOpList = buildCfSiteOpList(groupCfCoordList, siteList, sink);

        Map<SiteOpProductKey, CFIndex> substituteRules = buildSubstituteRules(cfSiteOpList, sink);
        emit(sink, "[3] substituteRules -> " + substituteRules.size() + " rules");

        int[][] lcf = pipelineResult.getLcf();
        int totalCfs = pipelineResult.getTcf();
        Map<CFIndex, Integer> cfColumnMap = buildCfColumnMap(lcf);
        int[][] cfBasisIndices = deriveCfBasisIndices(cfSiteOpList, cfColumnMap, totalCfs);
        emit(sink, "[4] CF column map -> " + totalCfs + " total CFs");

        List<List<Cluster>> ordClusCoordList = pipelineResult.getOrdClusData().getCoordList();
        CMatrixData result = generateCMatrix(
                ordClusCoordList, siteList, rMat, substituteRules, cfColumnMap, totalCfs, numElements, sink);

        double[][] pRulesMatrix = rMat;
        CMatrixData finalResult = new CMatrixData(result.cmat, result.lcv, result.wcv, siteList, cfBasisIndices,
                pRulesMatrix, substituteRules, cfColumnMap);
        emit(sink, "=== CMatrixPipeline: COMPLETE ===");
        return finalResult;
    }

    static List<Position> buildSiteList(List<Cluster> maxClusters) {
        List<Position> siteList = new ArrayList<>();
        for (Cluster cluster : maxClusters) {
            for (Sublattice sub : cluster.getSublattices()) {
                for (Site site : sub.getSites()) {
                    Position pos = site.getPosition();
                    if (!containsPosition(siteList, pos)) siteList.add(pos);
                }
            }
        }
        return siteList;
    }

    static List<List<List<List<Cluster>>>> groupSubClusters(
            List<Cluster> maxClusters, List<List<List<List<Cluster>>>> cfOrbitList,
            List<String> basisSymbolList, Consumer<String> sink) {

        List<List<List<List<Cluster>>>> classifiedSubClusList = new ArrayList<>();

        for (int i = 0; i < cfOrbitList.size(); i++) {
            List<List<List<Cluster>>> typeLevel = new ArrayList<>();
            for (int j = 0; j < cfOrbitList.get(i).size(); j++) {
                List<List<Cluster>> groupLevel = new ArrayList<>();
                for (int k = 0; k < cfOrbitList.get(i).get(j).size(); k++) {
                    List<Cluster> cfOrbit = cfOrbitList.get(i).get(j).get(k);
                    List<Cluster> matched = new ArrayList<>();

                    for (Cluster maxClus : maxClusters) {
                        List<Cluster> subClusCoordList =
                                ClusterCFIdentificationPipeline.genSubClusCoord(maxClus, basisSymbolList);
                        for (Cluster subClus : subClusCoordList) {
                            if (ClusterCFIdentificationPipeline.isContained(cfOrbit, subClus)) {
                                matched.add(subClus);
                            }
                        }
                    }

                    if (matched.isEmpty()) {
                        throw new ClusterGenerationException("Stage 3",
                                "No subcluster of the maximal clusters matches CF orbit type=" + i
                                        + " group=" + j + " index=" + k + " under the current symmetry.");
                    }
                    groupLevel.add(matched);
                }
                typeLevel.add(groupLevel);
            }
            classifiedSubClusList.add(typeLevel);
        }
        return classifiedSubClusList;
    }

    static List<List<List<List<List<SiteOp>>>>> buildCfSiteOpList(
            List<List<List<List<Cluster>>>> groupClusCoordList, List<Position> siteList, Consumer<String> sink) {

        List<List<List<List<List<SiteOp>>>>> rules = new ArrayList<>();

        for (List<List<List<Cluster>>> typeGroups : groupClusCoordList) {
            List<List<List<List<SiteOp>>>> typeLevel = new ArrayList<>();
            for (List<List<Cluster>> groupCfs : typeGroups) {
                List<List<List<SiteOp>>> groupLevel = new ArrayList<>();
                for (List<Cluster> cfSubclusters : groupCfs) {
                    List<List<SiteOp>> cfLevel = new ArrayList<>();
                    for (Cluster subCluster : cfSubclusters) {
                        List<SiteOp> siteOp = new ArrayList<>();
                        for (Sublattice sub : subCluster.getSublattices()) {
                            for (Site site : sub.getSites()) {
                                int siteIdx = positionIndexOf(siteList, site.getPosition());
                                if (siteIdx < 0) {
                                    throw new IllegalStateException("Site not found in siteList: " + site.getPosition());
                                }
                                int basisIdx = parseBasisIndex(site.getSymbol());
                                siteOp.add(new SiteOp(siteIdx, basisIdx));
                            }
                        }
                        cfLevel.add(siteOp);
                    }
                    groupLevel.add(cfLevel);
                }
                typeLevel.add(groupLevel);
            }
            rules.add(typeLevel);
        }
        return rules;
    }

    static Map<SiteOpProductKey, CFIndex> buildSubstituteRules(
            List<List<List<List<List<SiteOp>>>>> cfSiteOpList, Consumer<String> sink) {

        Map<SiteOpProductKey, CFIndex> rules = new LinkedHashMap<>();
        for (int i = 0; i < cfSiteOpList.size(); i++) {
            for (int j = 0; j < cfSiteOpList.get(i).size(); j++) {
                for (int k = 0; k < cfSiteOpList.get(i).get(j).size(); k++) {
                    for (List<SiteOp> tempClusCoord : cfSiteOpList.get(i).get(j).get(k)) {
                        SiteOpProductKey productKey = new SiteOpProductKey(tempClusCoord);
                        CFIndex cfIndex = new CFIndex(i, j, k);
                        rules.putIfAbsent(productKey, cfIndex);
                    }
                }
            }
        }
        return rules;
    }

    private static Map<CFIndex, Integer> buildCfColumnMap(int[][] lcf) {
        Map<CFIndex, Integer> map = new LinkedHashMap<>();
        int col = 0;
        for (int t = 0; t < lcf.length; t++) {
            for (int j = 0; j < lcf[t].length; j++) {
                for (int k = 0; k < lcf[t][j]; k++) map.put(new CFIndex(t, j, k), col++);
            }
        }
        return map;
    }

    static List<Integer> translateCluster(Cluster cluster, List<Position> siteList) {
        List<Integer> indices = new ArrayList<>();
        for (Sublattice sub : cluster.getSublattices()) {
            for (Site site : sub.getSites()) {
                int idx = positionIndexOf(siteList, site.getPosition());
                if (idx < 0) throw new IllegalStateException("Cluster site not found in siteList: " + site.getPosition());
                indices.add(idx);
            }
        }
        return indices;
    }

    static List<int[]> generateConfigurations(int numSites, int K) {
        int total = 1;
        for (int i = 0; i < numSites; i++) total *= K;
        List<int[]> configs = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int[] cfg = new int[numSites];
            int x = i;
            for (int s = numSites - 1; s >= 0; s--) {
                cfg[s] = x % K;
                x /= K;
            }
            configs.add(cfg);
        }
        return configs;
    }

    static double[] expandConfiguration(
            List<Integer> siteIndices, int[] config, double[][] rMat,
            Map<SiteOpProductKey, CFIndex> substituteRules, Map<CFIndex, Integer> cfColumnMap, int totalCfs) {

        int K = rMat.length;
        Map<SiteOpProductKey, Double> poly = new LinkedHashMap<>();
        poly.put(new SiteOpProductKey(List.of()), 1.0);

        for (int s = 0; s < siteIndices.size(); s++) {
            int globalSite = siteIndices.get(s);
            int element = config[s];
            double[] R_row = rMat[element];
            Map<SiteOpProductKey, Double> nextPoly = new LinkedHashMap<>();

            for (Map.Entry<SiteOpProductKey, Double> term : poly.entrySet()) {
                List<SiteOp> existingOps = term.getKey().getOps();
                double existingCoeff = term.getValue();
                for (int a = 0; a < K; a++) {
                    double c = R_row[a];
                    if (Math.abs(c) < 1e-14) continue;
                    List<SiteOp> newOps = new ArrayList<>(existingOps);
                    if (a > 0) newOps.add(new SiteOp(globalSite, a));
                    SiteOpProductKey newKey = new SiteOpProductKey(newOps);
                    nextPoly.merge(newKey, existingCoeff * c, Double::sum);
                }
            }
            poly = nextPoly;
        }

        double[] row = new double[totalCfs + 1];
        for (Map.Entry<SiteOpProductKey, Double> term : poly.entrySet()) {
            SiteOpProductKey key = term.getKey();
            double coeff = term.getValue();
            if (Math.abs(coeff) < 1e-14) continue;

            if (key.getOps().isEmpty()) {
                row[totalCfs] += coeff;
            } else {
                CFIndex cfIdx = substituteRules.get(key);
                if (cfIdx == null) throw new IllegalStateException("No substitute rule for site-op product: " + key);
                Integer col = cfColumnMap.get(cfIdx);
                if (col == null) throw new IllegalStateException("No column mapping for CF index: " + cfIdx);
                row[col] += coeff;
            }
        }
        return row;
    }

    private static CMatrixData generateCMatrix(
            List<List<Cluster>> ordClusCoordList, List<Position> siteList, double[][] rMat,
            Map<SiteOpProductKey, CFIndex> substituteRules, Map<CFIndex, Integer> cfColumnMap,
            int totalCfs, int numElements, Consumer<String> sink) {

        List<List<double[][]>> cmat = new ArrayList<>();
        List<List<int[]>> wcv = new ArrayList<>();
        int[][] lcv = new int[ordClusCoordList.size()][];

        for (int t = 0; t < ordClusCoordList.size(); t++) {
            List<Cluster> groups = ordClusCoordList.get(t);
            List<double[][]> cmatType = new ArrayList<>();
            List<int[]> wcvType = new ArrayList<>();
            lcv[t] = new int[groups.size()];

            for (int j = 0; j < groups.size(); j++) {
                Cluster cluster = groups.get(j);
                List<Integer> siteIndices = translateCluster(cluster, siteList);
                List<int[]> configs = generateConfigurations(siteIndices.size(), numElements);

                Map<RowKey, double[]> seenRows = new LinkedHashMap<>();
                Map<RowKey, Integer> seenCounts = new LinkedHashMap<>();
                for (int[] config : configs) {
                    double[] row = expandConfiguration(siteIndices, config, rMat, substituteRules, cfColumnMap, totalCfs);
                    RowKey key = new RowKey(row);
                    seenRows.putIfAbsent(key, row);
                    seenCounts.merge(key, 1, Integer::sum);
                }

                lcv[t][j] = seenRows.size();
                double[][] cmatBlock = new double[seenRows.size()][];
                int[] wcvBlock = new int[seenRows.size()];
                int idx = 0;
                for (Map.Entry<RowKey, double[]> entry : seenRows.entrySet()) {
                    cmatBlock[idx] = entry.getValue();
                    wcvBlock[idx] = seenCounts.get(entry.getKey());
                    idx++;
                }
                cmatType.add(cmatBlock);
                wcvType.add(wcvBlock);
            }
            cmat.add(cmatType);
            wcv.add(wcvType);
        }

        return new CMatrixData(cmat, lcv, wcv, null, null, null, null, null);
    }

    private static final class RowKey {
        private final long[] rounded;
        RowKey(double[] row) {
            rounded = new long[row.length];
            for (int i = 0; i < row.length; i++) rounded[i] = Math.round(row[i] * 1e10);
        }
        @Override public boolean equals(Object o) {
            return o instanceof RowKey && java.util.Arrays.equals(rounded, ((RowKey) o).rounded);
        }
        @Override public int hashCode() { return java.util.Arrays.hashCode(rounded); }
    }

    private static int positionIndexOf(List<Position> siteList, Position pos) {
        for (int i = 0; i < siteList.size(); i++) if (siteList.get(i).equals(pos)) return i;
        return -1;
    }

    private static boolean containsPosition(List<Position> list, Position pos) {
        for (Position p : list) if (p.equals(pos)) return true;
        return false;
    }

    private static int parseBasisIndex(String symbol) {
        if (symbol == null || !symbol.startsWith("s")) throw new IllegalArgumentException("Invalid basis symbol: " + symbol);
        return Integer.parseInt(symbol.substring(1));
    }

    static int[][] deriveCfBasisIndices(
            List<List<List<List<List<SiteOp>>>>> cfSiteOpList, Map<CFIndex, Integer> cfColumnMap, int totalCfs) {

        int[][] result = new int[totalCfs][];
        for (int t = 0; t < cfSiteOpList.size(); t++) {
            for (int j = 0; j < cfSiteOpList.get(t).size(); j++) {
                for (int k = 0; k < cfSiteOpList.get(t).get(j).size(); k++) {
                    Integer col = cfColumnMap.get(new CFIndex(t, j, k));
                    if (col != null) {
                        List<SiteOp> ops = cfSiteOpList.get(t).get(j).get(k).get(0);
                        result[col] = new int[ops.size()];
                        for (int m = 0; m < ops.size(); m++) result[col][m] = ops.get(m).getBasisIndex();
                    }
                }
            }
        }
        for (int col = 0; col < result.length; col++) {
            if (result[col] == null) {
                throw new ClusterGenerationException("Stage 3", "CF basis index missing for column " + col + ".");
            }
        }
        return result;
    }

    /**
     * Evaluates every cluster variable: {@code cv[t][j][v] = sum_k cmat[t][j][v][k] * uFull[k]}.
     */
    public static double[][][] evaluateCVs(double[] uFull, List<List<double[][]>> cmat, int[][] lcv, int tcdis, int[] lc) {
        if (cmat.size() != tcdis || lc.length != tcdis || lcv.length != tcdis) {
            throw new ClusterGenerationException("Stage 3", "evaluateCVs: dimension mismatch.");
        }
        int width = uFull.length;
        double[][][] cv = new double[tcdis][][];
        for (int t = 0; t < tcdis; t++) {
            cv[t] = new double[lc[t]][];
            for (int j = 0; j < lc[t]; j++) {
                double[][] block = cmat.get(t).get(j);
                int nv = lcv[t][j];
                cv[t][j] = new double[nv];
                for (int v = 0; v < nv; v++) {
                    double val = 0.0;
                    for (int k = 0; k < width; k++) val += block[v][k] * uFull[k];
                    cv[t][j][v] = val;
                }
            }
        }
        return cv;
    }

    private static void emit(Consumer<String> sink, String msg) {
        if (sink != null) sink.accept(msg);
    }
}
