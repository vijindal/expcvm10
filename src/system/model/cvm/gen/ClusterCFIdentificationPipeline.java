package system.model.cvm.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import system.model.cvm.gen.ClusterPrimitives.Position;
import system.model.cvm.gen.ClusterPrimitives.Site;
import system.model.cvm.gen.ClusterPrimitives.Sublattice;
import system.model.cvm.gen.SpaceGroup.SymmetryOperation;

/**
 * Cluster and correlation-function (CF) identification pipeline: Stages
 * 1a/1b (cluster orbits, multiplicities, Kikuchi-Baker coefficients) and
 * 2a/2b (CF orbits, grouped by HSP cluster type).
 *
 * <p>Ported from CEWorkbench's
 * {@code org.ce.model.cluster.ClusterCFIdentificationPipeline}. The file/
 * workspace-driven {@code runFullWorkflow} orchestration (which loaded
 * cluster/symmetry files via {@code InputLoader} and then called into
 * {@code CvCfBasis}) is not ported -- {@link GeneratedCvmGeometry} calls
 * {@link #run} directly with in-code structure data instead. Everything
 * else (the Stage 1a/1b/2a/2b algorithms themselves) is ported verbatim:
 * this is the reusable, structure-agnostic cluster/CF enumeration
 * machinery the whole generation layer is built on.</p>
 */
public final class ClusterCFIdentificationPipeline {

    private ClusterCFIdentificationPipeline() {}

    // =====================================================================
    // Result container: genClusCoordList output
    // =====================================================================

    public static final class ClusCoordListData {
        private final List<Cluster> clusCoordList;
        private final List<Double> multiplicities;
        private final List<List<Cluster>> orbitList;
        private final List<List<Integer>> rcList;
        private final int tc;

        ClusCoordListData(List<Cluster> clusCoordList, List<Double> multiplicities,
                List<List<Cluster>> orbitList, List<List<Integer>> rcList, int tc) {
            this.clusCoordList = clusCoordList;
            this.multiplicities = multiplicities;
            this.orbitList = orbitList;
            this.rcList = rcList;
            this.tc = tc;
        }

        public List<Cluster> getClusCoordList() { return clusCoordList; }
        public List<Double> getMultiplicities() { return multiplicities; }
        public List<List<Cluster>> getOrbitList() { return orbitList; }
        public List<List<Integer>> getRcList() { return rcList; }
        public int getTc() { return tc; }
    }

    // =====================================================================
    // Result container: transClusCoordList output
    // =====================================================================

    public static final class ClassifiedData {
        private final List<List<Cluster>> coordList;
        private final List<List<Double>> multiplicityList;
        private final List<List<List<Cluster>>> orbitList;
        private final List<List<List<Integer>>> rcList;

        ClassifiedData(List<List<Cluster>> coordList, List<List<Double>> multiplicityList,
                List<List<List<Cluster>>> orbitList, List<List<List<Integer>>> rcList) {
            this.coordList = coordList;
            this.multiplicityList = multiplicityList;
            this.orbitList = orbitList;
            this.rcList = rcList;
        }

        public List<List<Cluster>> getCoordList() { return coordList; }
        public List<List<Double>> getMultiplicityList() { return multiplicityList; }
        public List<List<List<Cluster>>> getOrbitList() { return orbitList; }
        public List<List<List<Integer>>> getRcList() { return rcList; }
    }

    // =====================================================================
    // Result container: groupCFData output
    // =====================================================================

    public static final class GroupedCFData {
        private final List<List<List<Cluster>>> coordData;
        private final List<List<List<Double>>> multiplicityData;
        private final List<List<List<List<Cluster>>>> orbitData;
        private final List<List<List<List<Integer>>>> rcData;

        GroupedCFData(List<List<List<Cluster>>> coordData,
                List<List<List<Double>>> multiplicityData,
                List<List<List<List<Cluster>>>> orbitData,
                List<List<List<List<Integer>>>> rcData) {
            this.coordData = coordData;
            this.multiplicityData = multiplicityData;
            this.orbitData = orbitData;
            this.rcData = rcData;
        }

        public List<List<List<Cluster>>> getCoordData() { return coordData; }
        public List<List<List<Double>>> getMultiplicityData() { return multiplicityData; }
        public List<List<List<List<Cluster>>>> getOrbitData() { return orbitData; }
        public List<List<List<List<Integer>>>> getRcData() { return rcData; }
    }

    // =====================================================================
    // Full pipeline result
    // =====================================================================

    public static final class PipelineResult {
        // Stage 1a
        private final ClusCoordListData disClusData;
        private final int tcdis;
        private final double[] mhdis;
        private final int[][] nijTable;
        private final double[] kbdis;

        // Stage 1b
        private final ClassifiedData ordClusData;
        private final int[] lc;
        private final int tc;
        private final double[][] mh;

        // Stage 2b
        private final GroupedCFData cfData;
        private final int[][] lcf;
        private final int tcf;
        private final int ncf;

        /** cfBasisIndices[col] = 1-based sigma-power decorations for CF column {@code col}. */
        private final int[][] cfBasisIndices;

        private final int numComponents;

        PipelineResult(ClusCoordListData disClusData, int tcdis, double[] mhdis,
                int[][] nijTable, double[] kbdis,
                ClassifiedData ordClusData, int[] lc, int tc, double[][] mh,
                GroupedCFData cfData, int[][] lcf, int tcf, int ncf,
                int[][] cfBasisIndices, int numComponents) {
            this.disClusData = disClusData;
            this.tcdis = tcdis;
            this.mhdis = mhdis;
            this.nijTable = nijTable;
            this.kbdis = kbdis;
            this.ordClusData = ordClusData;
            this.lc = lc;
            this.tc = tc;
            this.mh = mh;
            this.cfData = cfData;
            this.lcf = lcf;
            this.tcf = tcf;
            this.ncf = ncf;
            this.cfBasisIndices = cfBasisIndices;
            this.numComponents = numComponents;
        }

        public ClusCoordListData getDisClusData() { return disClusData; }
        public int getTcdis() { return tcdis; }
        public double[] getMhdis() { return mhdis; }
        public int[][] getNijTable() { return nijTable; }
        public double[] getKbdis() { return kbdis; }
        public ClassifiedData getOrdClusData() { return ordClusData; }
        public int[] getLc() { return lc; }
        public int getTc() { return tc; }
        public double[][] getMh() { return mh; }
        public GroupedCFData getCfData() { return cfData; }
        public int[][] getLcf() { return lcf; }
        public int getTcf() { return tcf; }
        public int getNcf() { return ncf; }
        public int[][] getCfBasisIndices() { return cfBasisIndices; }
        public int getNumComponents() { return numComponents; }

        /**
         * Computes the full CF vector at the disordered (random) state.
         * Length = ncf + K (non-point CFs, then K-1 orthogonal point CFs,
         * then the empty-cluster constant 1.0) -- matches the column count
         * expected by {@link CMatrixPipeline}'s C-matrices.
         *
         * @param moleFractions mole fractions (length K, sum = 1)
         */
        public double[] computeRandomCFs(double[] moleFractions) {
            int K = moleFractions.length;
            int pointBasisCount = K - 1;
            int fullLength = ncf + pointBasisCount + 1;

            double[] basis = ClusterMath.buildBasis(K);
            double[] pointCF = new double[pointBasisCount];
            for (int k = 0; k < pointBasisCount; k++) {
                for (int i = 0; i < K; i++) {
                    pointCF[k] += moleFractions[i] * Math.pow(basis[i], k + 1);
                }
            }

            double[] uFull = new double[fullLength];
            for (int col = 0; col < ncf; col++) {
                int[] indices = cfBasisIndices[col];
                double val = 1.0;
                for (int b : indices) val *= pointCF[b - 1];
                uFull[col] = val;
            }
            for (int k = 0; k < pointBasisCount; k++) {
                int col = ncf + k;
                int power = cfBasisIndices[col][0];
                uFull[col] = pointCF[power - 1];
            }
            uFull[fullLength - 1] = 1.0;
            return uFull;
        }
    }

    // =====================================================================
    // PIPELINE ENTRY POINT
    // =====================================================================

    /**
     * Runs the Stages 1a-2b pipeline for one (structure, symmetry, K).
     *
     * @param disMaxClusCoord HSP maximal cluster(s)
     * @param disSymOpList    HSP space-group operations
     * @param maxClusCoord    ordered-phase maximal cluster(s) (== disMaxClusCoord for a disordered structure)
     * @param symOpList       ordered-phase space-group operations
     * @param rotateMat       3x3 rotation (ordered frame -> HSP frame; identity for a disordered structure)
     * @param translateMat    translation vector (ordered frame -> HSP frame; zero for a disordered structure)
     * @param numComp         number of chemical components (>= 2)
     * @param sink            optional progress sink (may be null)
     */
    public static PipelineResult run(
            List<Cluster> disMaxClusCoord, List<SymmetryOperation> disSymOpList,
            List<Cluster> maxClusCoord, List<SymmetryOperation> symOpList,
            double[][] rotateMat, double[] translateMat, int numComp,
            Consumer<String> sink) {

        emit(sink, "=== ClusterCFIdentificationPipeline: START ===");

        // ---- Stage 1a: HSP clusters (binary basis) ----
        List<String> basisSymbolListBin = genBasisSymbolList(2);
        ClusCoordListData disClusData = genClusCoordList(disMaxClusCoord, disSymOpList, basisSymbolListBin, sink);

        int tcdis = disClusData.getTc();
        List<Cluster> disClusList = disClusData.getClusCoordList();
        List<Double> mhdisList = disClusData.getMultiplicities();
        List<List<Cluster>> disOrbitList = disClusData.getOrbitList();

        double[] mhdis = new double[tcdis];
        for (int i = 0; i < tcdis; i++) mhdis[i] = mhdisList.get(i);

        int[][] nijTable = getNijTable(disClusList, mhdisList, disOrbitList);
        double[] kbdis = generateKikuchiBakerCoefficients(mhdis, nijTable);

        emit(sink, "[1a] tcdis=" + tcdis);
        emit(sink, "[1a] mhdis=" + Arrays.toString(mhdis));
        emit(sink, "[1a] kbdis=" + Arrays.toString(kbdis));

        // ---- Stage 1b: Phase clusters ----
        ClusCoordListData phaseClusterData = genClusCoordList(maxClusCoord, symOpList, basisSymbolListBin, sink);
        int tc = phaseClusterData.getTc();

        List<Cluster> transformedClusList = ordToDisordCoord(rotateMat, translateMat,
                phaseClusterData.getClusCoordList());
        ClassifiedData ordClusData = transClusCoordList(disClusData, phaseClusterData, transformedClusList);

        int[] lc = new int[tcdis];
        for (int t = 0; t < tcdis; t++) lc[t] = ordClusData.getCoordList().get(t).size();

        double[][] mh = new double[tcdis][];
        for (int t = 0; t < tcdis; t++) {
            if (mhdis[t] == 0.0) {
                throw new ClusterGenerationException("Stage 1b",
                        "HSP cluster type " + t + " has zero multiplicity; cannot normalize.");
            }
            mh[t] = new double[lc[t]];
            for (int j = 0; j < lc[t]; j++) {
                mh[t][j] = ordClusData.getMultiplicityList().get(t).get(j) / mhdis[t];
            }
        }

        emit(sink, "[1b] tc=" + tc);
        emit(sink, "[1b] lc=" + Arrays.toString(lc));

        // ---- Stage 2a: HSP CFs (n-component basis) ----
        List<String> basisSymbolList = genBasisSymbolList(numComp);
        ClusCoordListData disCFData = genClusCoordList(disMaxClusCoord, disSymOpList, basisSymbolList, sink);

        // ---- Stage 2b: Phase CFs ----
        ClusCoordListData phaseCFDataRaw = genClusCoordList(maxClusCoord, symOpList, basisSymbolList, sink);
        List<Cluster> transformedCFList = ordToDisordCoord(rotateMat, translateMat, phaseCFDataRaw.getClusCoordList());
        ClassifiedData ordCFData = transClusCoordList(disCFData, phaseCFDataRaw, transformedCFList);

        GroupedCFData cfData = groupCFData(disClusData, disCFData, ordCFData, basisSymbolListBin);

        int[][] lcf = readLength(cfData.getCoordData());
        int tcf = 0;
        for (int[] row : lcf) for (int v : row) tcf += v;

        if (tcdis - 1 >= lcf.length) {
            throw new ClusterGenerationException("Stage 2b",
                    "CF grouping produced " + lcf.length + " HSP-type rows but Stage 1a found tcdis=" + tcdis);
        }
        int nxcf = 0;
        for (int j = 0; j < lcf[tcdis - 1].length; j++) nxcf += lcf[tcdis - 1][j];
        int ncf = tcf - nxcf;

        if (tcf == 0) {
            throw new ClusterGenerationException("Stage 2b", "No correlation functions were identified (tcf=0).");
        }

        int[][] cfBasisIndices = deriveCfBasisIndices(cfData, lcf);

        emit(sink, "[2b] tcf=" + tcf + ", nxcf=" + nxcf + ", ncf=" + ncf);
        emit(sink, "=== ClusterCFIdentificationPipeline: COMPLETE ===");

        return new PipelineResult(
                disClusData, tcdis, mhdis, nijTable, kbdis,
                ordClusData, lc, tc, mh,
                cfData, lcf, tcf, ncf,
                cfBasisIndices, numComp);
    }

    // =====================================================================
    // genBasisSymbolList[numComp]
    // =====================================================================

    public static List<String> genBasisSymbolList(int numComp) {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= numComp - 1; i++) result.add("s" + i);
        return result;
    }

    // =====================================================================
    // sortClusCoord
    // =====================================================================

    public static List<Site> sortClusCoord(List<Site> sites) {
        List<Site> sorted = new ArrayList<>(sites);
        for (int i = 1; i < sorted.size(); i++) {
            Site x = sorted.get(i);
            int j = i - 1;
            while (j >= 0 && compareSites(sorted.get(j), x) > 0) {
                sorted.set(j + 1, sorted.get(j));
                j--;
            }
            sorted.set(j + 1, x);
        }
        return sorted;
    }

    static int compareSites(Site a, Site b) {
        Position pa = a.getPosition();
        Position pb = b.getPosition();
        int cx = Double.compare(pa.getX(), pb.getX());
        if (cx != 0) return cx;
        int cy = Double.compare(pa.getY(), pb.getY());
        if (cy != 0) return cy;
        return Double.compare(pa.getZ(), pb.getZ());
    }

    // =====================================================================
    // applySymOpPoint / applySymOpClus / genOrbit
    // =====================================================================

    static Site applySymOpPoint(SymmetryOperation symOp, Site site) {
        Position r = site.getPosition();
        double[][] rot = symOp.getRotation();
        double[] trans = symOp.getTranslation();
        double x = rot[0][0] * r.getX() + rot[0][1] * r.getY() + rot[0][2] * r.getZ() + trans[0];
        double y = rot[1][0] * r.getX() + rot[1][1] * r.getY() + rot[1][2] * r.getZ() + trans[1];
        double z = rot[2][0] * r.getX() + rot[2][1] * r.getY() + rot[2][2] * r.getZ() + trans[2];
        return new Site(new Position(x, y, z), site.getSymbol());
    }

    static Cluster applySymOpClus(SymmetryOperation symOp, Cluster cluster) {
        List<Sublattice> newSublattices = new ArrayList<>();
        for (Sublattice sub : cluster.getSublattices()) {
            List<Site> newSites = new ArrayList<>();
            for (Site site : sub.getSites()) newSites.add(applySymOpPoint(symOp, site));
            newSites = sortClusCoord(newSites);
            newSublattices.add(new Sublattice(newSites));
        }
        return new Cluster(newSublattices);
    }

    public static List<Cluster> genOrbit(Cluster cluster, List<SymmetryOperation> spaceGroup) {
        List<Cluster> orbit = new ArrayList<>();
        for (SymmetryOperation op : spaceGroup) {
            Cluster transformed = applySymOpClus(op, cluster);
            if (!isContained(orbit, transformed)) orbit.add(transformed);
        }
        return orbit;
    }

    // =====================================================================
    // isTranslated / isContained
    // =====================================================================

    private static final double DELTA = 1e-6;

    public static boolean isTranslated(Cluster c1, Cluster c2) {
        if (c1.getSublattices().size() != c2.getSublattices().size()) return false;

        List<double[]> diffs = new ArrayList<>();
        for (int i = 0; i < c1.getSublattices().size(); i++) {
            List<Site> s1 = c1.getSublattices().get(i).getSites();
            List<Site> s2 = c2.getSublattices().get(i).getSites();
            if (s1.size() != s2.size()) return false;
            for (int j = 0; j < s1.size(); j++) {
                Position p1 = s1.get(j).getPosition();
                Position p2 = s2.get(j).getPosition();
                double dx = p2.getX() - p1.getX();
                double dy = p2.getY() - p1.getY();
                double dz = p2.getZ() - p1.getZ();
                double symMatch = s1.get(j).getSymbol().equals(s2.get(j).getSymbol()) ? 1.0 : 0.0;
                diffs.add(new double[] { dx, dy, dz, symMatch });
            }
        }

        List<double[]> unique = new ArrayList<>();
        for (double[] d : diffs) {
            boolean isDup = false;
            for (double[] u : unique) {
                if (Math.abs(d[0] - u[0]) < DELTA && Math.abs(d[1] - u[1]) < DELTA
                        && Math.abs(d[2] - u[2]) < DELTA && d[3] == u[3]) {
                    isDup = true;
                    break;
                }
            }
            if (!isDup) unique.add(d);
        }

        if (unique.size() > 1) return false;
        if (unique.isEmpty()) return true;

        double[] diff = unique.get(0);
        if (diff[3] == 0.0) return false;

        for (int j = 0; j < 3; j++) {
            double absVal = Math.abs(diff[j]);
            double fracPart = absVal - Math.floor(absVal);
            if (fracPart >= DELTA && fracPart <= (1.0 - DELTA)) return false;
        }
        return true;
    }

    public static boolean isContained(List<Cluster> orbit, Cluster cluster) {
        List<Integer> candidateSizes = sublatticeSizes(cluster);
        for (Cluster existing : orbit) {
            if (sublatticeSizes(existing).equals(candidateSizes)) {
                if (isTranslated(existing, cluster)) return true;
            }
        }
        return false;
    }

    // =====================================================================
    // genSubClusCoord (undecorated + decorated)
    // =====================================================================

    public static List<Cluster> genSubClusCoord(Cluster cluster) {
        List<Sublattice> originalSubs = cluster.getSublattices();
        int numSubLattice = originalSubs.size();

        List<Site> allSites = new ArrayList<>();
        for (Sublattice sub : originalSubs) allSites.addAll(sub.getSites());
        List<Site> sorted = sortClusCoord(allSites);

        List<List<Site>> subsets = generateAllSubsets(sorted);

        List<Cluster> result = new ArrayList<>();
        for (List<Site> subset : subsets) {
            List<Sublattice> subClus = new ArrayList<>();
            for (int k = 0; k < numSubLattice; k++) subClus.add(new Sublattice(new ArrayList<>()));
            for (Site site : subset) {
                for (int k = 0; k < numSubLattice; k++) {
                    if (containsSite(originalSubs.get(k).getSites(), site)) {
                        subClus.get(k).getSites().add(site);
                        break;
                    }
                }
            }
            result.add(new Cluster(subClus));
        }
        return result;
    }

    public static List<Cluster> genSubClusCoord(Cluster cluster, List<String> basisSymbolList) {
        List<Sublattice> originalSubs = cluster.getSublattices();
        int numSubLattice = originalSubs.size();

        List<Site> allSites = new ArrayList<>();
        for (Sublattice sub : originalSubs) allSites.addAll(sub.getSites());
        allSites = sortClusCoord(allSites);

        List<List<Site>> disClus = new ArrayList<>();
        for (Site site : allSites) {
            List<Site> options = new ArrayList<>();
            options.add(null);
            for (String symbol : basisSymbolList) options.add(new Site(site.getPosition(), symbol));
            disClus.add(options);
        }

        List<List<Site>> tuples = cartesianProduct(disClus);

        List<Cluster> result = new ArrayList<>();
        for (List<Site> tuple : tuples) {
            List<Sublattice> subClus = new ArrayList<>();
            for (int k = 0; k < numSubLattice; k++) subClus.add(new Sublattice(new ArrayList<>()));
            for (Site decoratedSite : tuple) {
                if (decoratedSite == null) continue;
                for (int k = 0; k < numSubLattice; k++) {
                    if (containsPosition(originalSubs.get(k).getSites(), decoratedSite.getPosition())) {
                        subClus.get(k).getSites().add(decoratedSite);
                        break;
                    }
                }
            }
            result.add(new Cluster(subClus));
        }
        return result;
    }

    // =====================================================================
    // genClusCoordList
    // =====================================================================

    public static ClusCoordListData genClusCoordList(
            List<Cluster> maxClusCoord, List<SymmetryOperation> spaceGroup,
            List<String> basisSymbolList, Consumer<String> sink) {

        emit(sink, "  genClusCoordList called: maxClus=" + maxClusCoord.size()
                + ", symOps=" + spaceGroup.size() + ", basis=" + basisSymbolList);

        List<Cluster> clusCoordList = new ArrayList<>();
        List<List<Cluster>> subClusOrbitList = new ArrayList<>();
        List<Integer> subClusMList = new ArrayList<>();
        List<List<Integer>> rc = new ArrayList<>();

        for (Cluster maxClus : maxClusCoord) {
            List<Cluster> subClusCoord = genSubClusCoord(maxClus, basisSymbolList);
            subClusCoord.sort((a, b) -> Integer.compare(b.getAllSites().size(), a.getAllSites().size()));

            int numSubClus = subClusCoord.size();
            for (int i = numSubClus - 1; i >= 0; i--) {
                Cluster candidate = subClusCoord.get(i);
                if (candidate.getAllSites().isEmpty()) continue;

                boolean foundNewCluster = true;
                for (List<Cluster> existingOrbit : subClusOrbitList) {
                    if (isContained(existingOrbit, candidate)) {
                        foundNewCluster = false;
                        break;
                    }
                }

                if (foundNewCluster) {
                    clusCoordList.add(candidate);
                    List<Cluster> orbit = genOrbit(candidate, spaceGroup);
                    subClusOrbitList.add(orbit);
                    subClusMList.add(orbit.size());

                    List<Integer> rcEntry = new ArrayList<>();
                    for (Sublattice sub : candidate.getSublattices()) rcEntry.add(sub.getSites().size());
                    rc.add(rcEntry);
                }
            }
        }

        int tcCount = clusCoordList.size();

        int numPointSubClusFound = 0;
        double pointM = 0;
        List<Position> pointPositions = new ArrayList<>();
        for (int i = 0; i < tcCount; i++) {
            List<Site> flatList = clusCoordList.get(i).getAllSites();
            if (flatList.size() == 1) {
                Position pos = flatList.get(0).getPosition();
                boolean alreadyCounted = false;
                for (Position existing : pointPositions) {
                    if (positionsMatch(existing, pos)) { alreadyCounted = true; break; }
                }
                if (!alreadyCounted) {
                    pointPositions.add(pos);
                    pointM += subClusOrbitList.get(i).size();
                }
                numPointSubClusFound++;
            }
        }

        if (pointM == 0) {
            throw new ClusterGenerationException("genClusCoordList",
                    "No point (single-site) cluster orbit was found among " + tcCount
                            + " enumerated cluster types; cannot normalize multiplicities.");
        }

        List<Double> normalizedM = new ArrayList<>();
        for (int m : subClusMList) normalizedM.add(m / pointM);

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < tcCount; i++) indices.add(i);
        indices.sort((i1, i2) -> Integer.compare(
                clusCoordList.get(i2).getAllSites().size(),
                clusCoordList.get(i1).getAllSites().size()));

        List<Cluster> finalClusList = new ArrayList<>();
        List<Double> finalM = new ArrayList<>();
        List<List<Cluster>> finalOrbit = new ArrayList<>();
        List<List<Integer>> finalRc = new ArrayList<>();
        for (int idx : indices) {
            finalClusList.add(clusCoordList.get(idx));
            finalM.add(normalizedM.get(idx));
            finalOrbit.add(subClusOrbitList.get(idx));
            finalRc.add(rc.get(idx));
        }

        emit(sink, "  genClusCoordList ended: tc=" + tcCount + " (point subclusters found=" + numPointSubClusFound + ")");

        return new ClusCoordListData(finalClusList, finalM, finalOrbit, finalRc, tcCount);
    }

    // =====================================================================
    // getNijTable / generateKikuchiBakerCoefficients
    // =====================================================================

    public static int[][] getNijTable(List<Cluster> clusCoordList, List<Double> clusMList,
            List<List<Cluster>> clusOrbitList) {
        int numClus = clusCoordList.size();
        int[][] nijTable = new int[numClus][numClus];

        for (int i = 0; i < numClus; i++) {
            List<Cluster> subClusCoord = genSubClusCoord(clusCoordList.get(i));
            int numSubClus = subClusCoord.size();
            for (int k = numSubClus - 1; k >= 0; k--) {
                Cluster subCluster = subClusCoord.get(k);
                List<Integer> subSizes = sublatticeSizes(subCluster);
                for (int j = 0; j < numClus; j++) {
                    if (j >= i) {
                        if (subSizes.equals(sublatticeSizes(clusCoordList.get(j)))) {
                            if (isContained(clusOrbitList.get(j), subCluster)) nijTable[i][j]++;
                        }
                    }
                }
            }
        }
        return nijTable;
    }

    public static double[] generateKikuchiBakerCoefficients(double[] mList, int[][] nijTable) {
        int n = mList.length;
        for (int j = 0; j < n; j++) {
            if (mList[j] == 0) {
                throw new ClusterGenerationException("Stage 1a",
                        "Cluster multiplicity mList[" + j + "]=0; cannot compute Kikuchi-Baker coefficient.");
            }
        }
        double[] kb = new double[n];
        for (int j = 0; j < n; j++) {
            double tempSum = 0.0;
            for (int i = 0; i < j; i++) tempSum += mList[i] * nijTable[i][j] * kb[i];
            kb[j] = (mList[j] - tempSum) / mList[j];
        }
        return kb;
    }

    // =====================================================================
    // ordToDisordCoord
    // =====================================================================

    public static List<Cluster> ordToDisordCoord(double[][] rotateMat, double[] translateMat, List<Cluster> clusters) {
        List<Cluster> result = new ArrayList<>();
        for (Cluster cluster : clusters) {
            List<Sublattice> newSublattices = new ArrayList<>();
            for (Sublattice sub : cluster.getSublattices()) {
                List<Site> newSites = new ArrayList<>();
                for (Site site : sub.getSites()) {
                    Position r = site.getPosition();
                    double x = rotateMat[0][0] * r.getX() + rotateMat[0][1] * r.getY()
                            + rotateMat[0][2] * r.getZ() + translateMat[0];
                    double y = rotateMat[1][0] * r.getX() + rotateMat[1][1] * r.getY()
                            + rotateMat[1][2] * r.getZ() + translateMat[1];
                    double z = rotateMat[2][0] * r.getX() + rotateMat[2][1] * r.getY()
                            + rotateMat[2][2] * r.getZ() + translateMat[2];
                    newSites.add(new Site(new Position(x, y, z), site.getSymbol()));
                }
                newSublattices.add(new Sublattice(newSites));
            }
            result.add(new Cluster(newSublattices));
        }
        return result;
    }

    // =====================================================================
    // transClusCoordList
    // =====================================================================

    public static ClassifiedData transClusCoordList(
            ClusCoordListData disClusData, ClusCoordListData clusData, List<Cluster> transformedCoords) {

        List<List<Cluster>> disClusOrbitList = disClusData.getOrbitList();
        int tcdis = disClusData.getClusCoordList().size();

        List<Cluster> clusCoordList1 = clusData.getClusCoordList();
        List<Double> clusMList = clusData.getMultiplicities();
        List<List<Cluster>> clusOrbitList = clusData.getOrbitList();
        List<List<Integer>> clusRcList = clusData.getRcList();

        int tc = transformedCoords.size();

        List<Cluster> flattenClusCoordList = new ArrayList<>();
        for (int i = 0; i < tc; i++) {
            List<Site> flat = new ArrayList<>(transformedCoords.get(i).getAllSites());
            flat = sortClusCoord(flat);
            List<Sublattice> singleSub = new ArrayList<>();
            singleSub.add(new Sublattice(flat));
            flattenClusCoordList.add(new Cluster(singleSub));
        }

        List<List<Cluster>> classifiedCoord = new ArrayList<>();
        List<List<Double>> classifiedM = new ArrayList<>();
        List<List<List<Cluster>>> classifiedOrbit = new ArrayList<>();
        List<List<List<Integer>>> classifiedRc = new ArrayList<>();

        for (int j = 0; j < tcdis; j++) {
            classifiedCoord.add(new ArrayList<>());
            classifiedM.add(new ArrayList<>());
            classifiedOrbit.add(new ArrayList<>());
            classifiedRc.add(new ArrayList<>());

            for (int i = 0; i < tc; i++) {
                if (isContained(disClusOrbitList.get(j), flattenClusCoordList.get(i))) {
                    classifiedCoord.get(j).add(clusCoordList1.get(i));
                    classifiedM.get(j).add(clusMList.get(i));
                    classifiedOrbit.get(j).add(clusOrbitList.get(i));
                    classifiedRc.get(j).add(clusRcList.get(i));
                }
            }
        }

        return new ClassifiedData(classifiedCoord, classifiedM, classifiedOrbit, classifiedRc);
    }

    // =====================================================================
    // groupCFData
    // =====================================================================

    public static GroupedCFData groupCFData(
            ClusCoordListData disClusData, ClusCoordListData disCFData,
            ClassifiedData ordCFData, List<String> basisSymbolListBin) {

        List<Cluster> disClusCoordData = disClusData.getClusCoordList();
        List<List<Cluster>> disClusOrbitList = disClusData.getOrbitList();
        List<Cluster> disCFCoordData = disCFData.getClusCoordList();

        List<Cluster> transDisCFCoordData = new ArrayList<>();
        for (Cluster cf : disCFCoordData) {
            List<Sublattice> newSubs = new ArrayList<>();
            for (Sublattice sub : cf.getSublattices()) {
                List<Site> newSites = new ArrayList<>();
                for (Site s : sub.getSites()) newSites.add(new Site(s.getPosition(), basisSymbolListBin.get(0)));
                newSubs.add(new Sublattice(newSites));
            }
            transDisCFCoordData.add(new Cluster(newSubs));
        }

        List<List<List<Cluster>>> groupedCoord = new ArrayList<>();
        List<List<List<Double>>> groupedM = new ArrayList<>();
        List<List<List<List<Cluster>>>> groupedOrbit = new ArrayList<>();
        List<List<List<List<Integer>>>> groupedRc = new ArrayList<>();

        for (int i = 0; i < disClusCoordData.size(); i++) {
            groupedCoord.add(new ArrayList<>());
            groupedM.add(new ArrayList<>());
            groupedOrbit.add(new ArrayList<>());
            groupedRc.add(new ArrayList<>());

            for (int j = 0; j < transDisCFCoordData.size(); j++) {
                if (isContained(disClusOrbitList.get(i), transDisCFCoordData.get(j))) {
                    groupedCoord.get(i).add(ordCFData.getCoordList().get(j));
                    groupedM.get(i).add(ordCFData.getMultiplicityList().get(j));
                    groupedOrbit.get(i).add(ordCFData.getOrbitList().get(j));
                    groupedRc.get(i).add(ordCFData.getRcList().get(j));
                }
            }
        }

        return new GroupedCFData(groupedCoord, groupedM, groupedOrbit, groupedRc);
    }

    // =====================================================================
    // readLength / deriveCfBasisIndices
    // =====================================================================

    public static int[][] readLength(List<List<List<Cluster>>> array) {
        int[][] lenArray = new int[array.size()][];
        for (int i = 0; i < array.size(); i++) {
            lenArray[i] = new int[array.get(i).size()];
            for (int j = 0; j < array.get(i).size(); j++) {
                lenArray[i][j] = array.get(i).get(j).size();
            }
        }
        return lenArray;
    }

    static int[][] deriveCfBasisIndices(GroupedCFData cfData, int[][] lcf) {
        int totalCfs = 0;
        for (int[] row : lcf) for (int val : row) totalCfs += val;

        int[][] indices = new int[totalCfs][];
        int col = 0;

        List<List<List<Cluster>>> coordData = cfData.getCoordData();
        for (List<List<Cluster>> groupsT : coordData) {
            for (List<Cluster> cfList : groupsT) {
                for (Cluster cfCluster : cfList) {
                    List<Site> sites = cfCluster.getAllSites();
                    int[] basisIdx = new int[sites.size()];
                    for (int s = 0; s < sites.size(); s++) {
                        String symbol = sites.get(s).getSymbol();
                        if (symbol == null || symbol.length() < 2 || symbol.charAt(0) != 's') {
                            throw new ClusterGenerationException("Stage 2b",
                                    "CF site at column " + col + " has unexpected decoration symbol '" + symbol + "'.");
                        }
                        basisIdx[s] = Integer.parseInt(symbol.substring(1));
                    }
                    indices[col++] = basisIdx;
                }
            }
        }
        return indices;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static List<List<Site>> generateAllSubsets(List<Site> sites) {
        List<List<Site>> subsets = new ArrayList<>();
        int n = sites.size();
        int total = 1 << n;
        for (int mask = 0; mask < total; mask++) {
            List<Site> subset = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) subset.add(sites.get(i));
            }
            subsets.add(subset);
        }
        return subsets;
    }

    private static List<List<Site>> cartesianProduct(List<List<Site>> lists) {
        List<List<Site>> result = new ArrayList<>();
        cartesianRecursive(lists, 0, new ArrayList<>(), result);
        return result;
    }

    private static void cartesianRecursive(List<List<Site>> lists, int depth, List<Site> current, List<List<Site>> result) {
        if (depth == lists.size()) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (Site s : lists.get(depth)) {
            current.add(s);
            cartesianRecursive(lists, depth + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private static boolean positionsMatch(Position p1, Position p2) {
        return Math.abs(p1.getX() - p2.getX()) < DELTA
                && Math.abs(p1.getY() - p2.getY()) < DELTA
                && Math.abs(p1.getZ() - p2.getZ()) < DELTA;
    }

    private static boolean containsPosition(List<Site> sites, Position pos) {
        for (Site s : sites) if (positionsMatch(s.getPosition(), pos)) return true;
        return false;
    }

    private static boolean containsSite(List<Site> sites, Site target) {
        for (Site s : sites) {
            if (positionsMatch(s.getPosition(), target.getPosition()) && s.getSymbol().equals(target.getSymbol())) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> sublatticeSizes(Cluster c) {
        List<Integer> sizes = new ArrayList<>();
        for (Sublattice sub : c.getSublattices()) sizes.add(sub.getSites().size());
        return sizes;
    }

    private static void emit(Consumer<String> sink, String message) {
        if (sink != null) sink.accept(message);
    }
}
