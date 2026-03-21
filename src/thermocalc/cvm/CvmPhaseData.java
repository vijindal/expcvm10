package thermocalc.cvm;

/**
 * Immutable data container for the pre-computed CVM (Cluster Variation Method)
 * structural data loaded from a Mathematica {@code .nb} output file.
 *
 * <h2>Source</h2>
 * Each field corresponds directly to a named variable in {@code calGmcecvm[phaseData]}
 * in {@code clusGen_25}. The mapping is exact:
 * <pre>
 *   phaseData[[1]][[1]]  → tcdis
 *   phaseData[[1]][[4]]  → mhdis       (= msdis)
 *   phaseData[[1]][[7]]  → kbdis       (after ms[] substitution)
 *   phaseData[[2]][[1]]  → lc
 *   phaseData[[2]][[10]] → ncf
 *   phaseData[[2]][[12]] → mcf
 *   phaseData[[2]][[13]] → wcv
 *   phaseData[[2]][[14]] → lcv
 *   phaseData[[2]][[15]] → cmat
 *   phaseData[[3]][[1]]  → u2List names (as String[])
 *   phaseData[[3]][[2]]  → replaceCFRules: u[i] = sum_j(cfCoeffs[i][j] * u2List[j])
 *   phaseData[[3]][[3]]  → uRandRules: v_k = polynomial in XA,XB,...
 *   phaseData[[3]][[4]]  → eList names (as String[])
 *   phaseData[[3]][[5]]  → nComp
 *   phaseData[[3]][[6]]  → phaseName
 * </pre>
 *
 * <h2>Binary BCC_A2 concrete values</h2>
 * <pre>
 *   tcdis  = 5  (cluster types: tetrahedron, triangle, pair-1NN, pair-2NN, point)
 *   mhdis  = {6, 12, 3, 4, 1, 0.5}
 *   kbdis  = {1, -1, 1, 1, -1}           (after ms substitution)
 *   ncf    = 4                             (CECs: e4AB, e3AB, e2AB1, e2AB2)
 *   nip    = 6                             (u2List: v4AB,v3AB,v2AB1,v2AB2,xA,xB)
 *   lcv    = {6,6,3,3,2}                  (CVs per cluster type)
 *   lc     = {1,1,1,1,1,1}               (clusters per type)
 *   wcv[0] = {1,4,4,2,4,1}               (tetrahedron CV weights)
 * </pre>
 *
 * <h2>u → CV transformation</h2>
 * Each CV is computed as a dot product of a {@code cmat} row with the {@code u} vector:
 * <pre>
 *   CV[clusterType][clusterIdx][cvIdx] = cmat[clusterType][clusterIdx][cvIdx] · uVec
 * </pre>
 * where {@code uVec[i]} is computed from the CV variables via {@code cfCoeffs}.
 *
 * <h2>replaceCFRules (cfCoeffs)</h2>
 * The pre-computed linear map from u2List (v's and x's) to u-variables:
 * <pre>
 *   u[i] = sum_j  cfCoeffs[i][j] * u2List[j]
 * </pre>
 * For binary BCC_A2: u2List = {v4AB, v3AB, v2AB1, v2AB2, xA, xB}
 * <pre>
 *   u[0] = -16*v2AB1 + 8*v2AB2 + 16*v4AB + xA + xB
 *         coeffs: {16, 0, -16, 8, 1, 1}
 *   u[1] =  -4*v3AB - xA + xB
 *         coeffs: {0, -4, 0, 0, -1, 1}
 *   ...
 * </pre>
 */
public final class CvmPhaseData {

    // ------------------------------------------------------------------
    // Core identifiers
    // ------------------------------------------------------------------

    /** Phase name (e.g. "BCC_A2"). */
    public final String phaseName;

    /** Number of components (e.g. 2 for binary, 3 for ternary). */
    public final int nComp;

    /** Number of cluster expansion coefficients (CECs) = number of independent CVs.
     *  For binary BCC_A2: ncf=4 (v4AB, v3AB, v2AB1, v2AB2). */
    public final int ncf;

    /** Total number of internal parameters = ncf + nComp (CVs + compositions).
     *  nip = ncf + nComp. */
    public final int nip;

    // ------------------------------------------------------------------
    // Block 1: disordered cluster structure
    // ------------------------------------------------------------------

    /** Number of cluster types (tcdis). For BCC_A2: 5. */
    public final int tcdis;

    /** Cluster multiplicities (mhdis = msdis). Length = tcdis (or tcdis+1).
     *  For BCC_A2: {6, 12, 3, 4, 1, 0.5}. */
    public final double[] mhdis;

    /** Boltzmann weight coefficients per cluster type (kbdis after ms[] substitution).
     *  Length = tcdis. For BCC_A2: {1, -1, 1, 1, -1}. */
    public final double[] kbdis;

    // ------------------------------------------------------------------
    // Block 2: CVM structure
    // ------------------------------------------------------------------

    /** Number of CV clusters per cluster type (lc). Length = tcdis (or tcdis+1).
     *  For BCC_A2: {1,1,1,1,1,1} (one cluster per type). */
    public final int[] lc;

    /** Number of CVs per (cluster type, cluster index) (lcv).
     *  lcv[itc][inc] = number of CVs for cluster type itc, cluster inc.
     *  For BCC_A2: {{6},{6},{3},{3},{2}}. */
    public final int[][] lcv;

    /** Cluster multiplicity factors (mh). Same shape as lcv.
     *  For BCC_A2: all 1s ({{1},{1},{1},{1},{1},{1}}). */
    public final double[][] mh;

    /** CV weights (wcv). wcv[itc][inc] = double[] of length lcv[itc][inc].
     *  For BCC_A2: {{1,4,4,2,4,1},{1,1,2,2,1,1},{1,2,1},{1,2,1},{1,1}}. */
    public final double[][][] wcv;

    /** Coefficient matrix for u→CV transformation (cmat).
     *  cmat[itc][inc] = double[lcv[itc][inc]][uListLen] matrix row.
     *  CV[itc][inc][icv] = dot(cmat[itc][inc][icv], uVec).
     *  For BCC_A2 tetrahedron: 6×6 matrix. */
    public final double[][][][] cmat;

    /** Length of the u-vector (= nip, the number of u-variables). */
    public final int uListLen;

    // ------------------------------------------------------------------
    // Block 3: CV variable rules
    // ------------------------------------------------------------------

    /** Names of the u2List variables (symbolic, for diagnostics).
     *  For BCC_A2: {"v4AB","v3AB","v2AB1","v2AB2","xA","xB"}. */
    public final String[] u2ListNames;

    /** Names of the CEC variables in eList (symbolic, for diagnostics).
     *  For BCC_A2: {"e4AB","e3AB","e2AB1","e2AB2"}. */
    public final String[] eListNames;

    /**
     * Linear coefficients for the replaceCFRules: {@code u[i] = sum_j cfCoeffs[i][j] * u2List[j]}.
     * Shape: {@code [uListLen][uListLen]}.
     * <p>
     * For binary BCC_A2 (uListLen=6):
     * <pre>
     *   row 0: { 16,  0, -16,  8,  1,  1}   u[0] = 16*v4AB - 16*v2AB1 + 8*v2AB2 + xA + xB
     *   row 1: {  0, -4,   0,  0, -1,  1}   u[1] = -4*v3AB - xA + xB
     *   row 2: {  0,  0,   0, -4,  1,  1}   u[2] = -4*v2AB2 + xA + xB
     *   row 3: {  0,  0,  -4,  0,  1,  1}   u[3] = -4*v2AB1 + xA + xB
     *   row 4: {  0,  0,   0,  0, -1,  1}   u[4] = -xA + xB
     *   row 5: {  0,  0,   0,  0,  1,  1}   u[5] = xA + xB  (= 1)
     * </pre>
     */
    public final double[][] cfCoeffs;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Full constructor — all fields must be pre-computed by the parser.
     */
    public CvmPhaseData(
            String phaseName, int nComp, int ncf,
            int tcdis, double[] mhdis, double[] kbdis,
            int[] lc, int[][] lcv, double[][] mh,
            double[][][] wcv, double[][][][] cmat,
            int uListLen, double[][] cfCoeffs,
            String[] u2ListNames, String[] eListNames) {

        this.phaseName    = phaseName;
        this.nComp        = nComp;
        this.ncf          = ncf;
        this.nip          = ncf + nComp;
        this.tcdis        = tcdis;
        this.mhdis        = mhdis.clone();
        this.kbdis        = kbdis.clone();
        this.lc           = lc.clone();
        this.lcv          = lcv;
        this.mh           = mh;
        this.wcv          = wcv;
        this.cmat         = cmat;
        this.uListLen     = uListLen;
        this.cfCoeffs     = cfCoeffs;
        this.u2ListNames  = u2ListNames.clone();
        this.eListNames   = eListNames.clone();
    }

    /**
     * Computes the u-vector from a u2List value array (v's and x's).
     * {@code u[i] = sum_j cfCoeffs[i][j] * u2vals[j]}.
     *
     * @param u2vals  values of {v0, v1, ..., v_{ncf-1}, x0, x1, ..., x_{nComp-1}}
     * @return        u-vector of length uListLen
     */
    public double[] computeU(double[] u2vals) {
        double[] u = new double[uListLen];
        for (int i = 0; i < uListLen; i++) {
            for (int j = 0; j < uListLen; j++) {
                u[i] += cfCoeffs[i][j] * u2vals[j];
            }
        }
        return u;
    }

    /**
     * Computes the random-approximation initial values of the CVs from bulk compositions.
     * <p>
     * For binary: {v4AB, v3AB, v2AB1, v2AB2, xA, xB}
     * <pre>
     *   v4AB  = xA^2 * xB^2
     *   v3AB  = xA*xB*(xB - xA)  = -xA^2*xB + xA*xB^2
     *   v2AB1 = xA*xB
     *   v2AB2 = xA*xB
     *   xA    = xA,  xB = xB
     * </pre>
     * For ternary and quaternary, this method must be overridden by subclasses
     * or specialised implementations — the polynomial form changes with nComp.
     *
     * @param x  bulk mole fractions x[0..nComp-1]
     * @return   initial u2vals array of length nip
     */
    public double[] evalRandApprox(double[] x) {
        double[] u2 = new double[nip];
        if (nComp == 2) {
            double xA = x[0], xB = x[1];
            u2[0] = xA * xA * xB * xB;          // v4AB
            u2[1] = -xA * xA * xB + xA * xB * xB; // v3AB
            u2[2] = xA * xB;                     // v2AB1
            u2[3] = xA * xB;                     // v2AB2
            u2[4] = xA;                           // xA
            u2[5] = xB;                           // xB
        } else {
            // For nComp > 2: generalised random approximation
            // v_{cluster} = product of x[i] for each site in the cluster
            // (details depend on cluster type — handled by subclass or specialised parser)
            throw new UnsupportedOperationException(
                    "evalRandApprox for nComp=" + nComp + " must be provided by subclass");
        }
        return u2;
    }

    @Override
    public String toString() {
        return String.format("CvmPhaseData{phase=%s, nComp=%d, ncf=%d, nip=%d, tcdis=%d}",
                phaseName, nComp, ncf, nip, tcdis);
    }
}
