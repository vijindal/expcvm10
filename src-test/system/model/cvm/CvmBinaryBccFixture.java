package system.model.cvm;

/**
 * Test-only fixture: the exact numeric binary BCC_A2 tetrahedron-
 * approximation ({@code model="T"}) cluster algebra, taken verbatim from
 * CEWorkbench's own {@code CvmGeometry.build("A-B", "BCC_A2", "T")} pipeline
 * output (a sibling repo's independently generated, {@code validate()}-passing
 * lattice combinatorics), reshaped into this project's {@link CvmPhaseData}.
 *
 * <p>No {@code .nb} file for binary BCC_A2 exists in this repository, so this
 * fixture is what lets {@link CvmPhaseData}/{@link CvmGibbs}/
 * {@link CvmGibbsModel} be exercised at all, and is also what makes the
 * CEWorkbench cross-check test (Test D) possible: both sides evaluate from
 * the same underlying numbers rather than two independently-typed guesses.
 *
 * <h2>Column order (uListLen = 6)</h2>
 * <pre>
 *   index 0: v4AB   (tetrahedron non-point CVCF)
 *   index 1: v3AB   (triangle non-point CVCF)
 *   index 2: v22AB  (2nd-nearest-neighbour pair non-point CVCF)
 *   index 3: v21AB  (1st-nearest-neighbour pair non-point CVCF)
 *   index 4: xA
 *   index 5: xB
 * </pre>
 * This is CEWorkbench's own {@code cfNames} order, kept unchanged here
 * (rather than renumbered to {@code CvmPhaseData}'s own javadoc example,
 * which names the same two pair CVCFs {@code v2AB1}/{@code v2AB2} without
 * fixing which is 1NN and which is 2NN) specifically so the numeric cmat
 * below needs no reordering and cannot be silently transposed between the
 * two neighbour shells -- see the task's note that {@code v2AB1}/{@code
 * v2AB2} must not be assumed to map to "first/second neighbour" without
 * confirmation from source data. Here the source data names the shells
 * explicitly, so this fixture uses {@code v21AB}/{@code v22AB} rather than
 * the ambiguous {@code v2AB1}/{@code v2AB2}.
 *
 * <h2>Cluster types (tcdis = 5)</h2>
 * <pre>
 *   t=0: tetrahedron   (4-site, lcv=6, wcv={1,4,4,2,4,1})
 *   t=1: triangle      (3-site, lcv=6, wcv={1,2,1,1,2,1})
 *   t=2: pair, 2nd-NN  (2-site, lcv=3, wcv={1,2,1})
 *   t=3: pair, 1st-NN  (2-site, lcv=3, wcv={1,2,1})
 *   t=4: point         (1-site, lcv=2, wcv={1,1})
 * </pre>
 * confirmed against CEWorkbench's {@code CVMGibbsModel.Shell} javadoc
 * ("t=2 2nd-NN pair, t=3 1st-NN pair") and its {@code mhdis}/{@code kb}
 * dump ({@code mhdis={6,12,4,3,1}}, {@code kb={1,-1,1,1,-1}}).
 */
final class CvmBinaryBccFixture {

    private CvmBinaryBccFixture() {}

    static final String[] U2_NAMES = {"v4AB", "v3AB", "v22AB", "v21AB", "xA", "xB"};
    static final String[] E_NAMES  = {"v4AB", "v3AB", "v22AB", "v21AB"};

    static CvmPhaseData build() {
        int nComp = 2;
        int ncf = 4;
        int tcdis = 5;

        double[] mhdis = {6.0, 12.0, 4.0, 3.0, 1.0};
        double[] kbdis = {1.0, -1.0, 1.0, 1.0, -1.0};

        int[] lc = {1, 1, 1, 1, 1};

        int[][] lcv = {
                {6}, // tetrahedron
                {6}, // triangle
                {3}, // pair 2NN
                {3}, // pair 1NN
                {2}, // point
        };

        double[][] mh = {
                {1.0}, {1.0}, {1.0}, {1.0}, {1.0}
        };

        double[][][] wcv = {
                { {1, 4, 4, 2, 4, 1} },
                { {1, 2, 1, 1, 2, 1} },
                { {1, 2, 1} },
                { {1, 2, 1} },
                { {1, 1} },
        };

        // cmat[itc][inc][icv][col], col order = U2_NAMES above.
        // Verbatim from CEWorkbench CvmGeometry.build("A-B","BCC_A2","T").cmat.
        double[][][][] cmat = {
                // t=0 tetrahedron
                { {
                        { 1.0,  1.0,  0.0, -2.0,  1.0,  0.0},
                        {-1.0, -0.5, -0.5,  1.0,  0.0,  0.0},
                        { 1.0,  0.0,  1.0, -1.0,  0.0,  0.0},
                        { 1.0,  0.0,  0.0,  0.0,  0.0,  0.0},
                        {-1.0,  0.5, -0.5,  1.0,  0.0,  0.0},
                        { 1.0, -1.0,  0.0, -2.0,  0.0,  1.0},
                } },
                // t=1 triangle
                { {
                        { 0.0,  0.5, -0.5, -1.0,  1.0,  0.0},
                        { 0.0, -0.5,  0.5,  0.0,  0.0,  0.0},
                        { 0.0,  0.5, -0.5,  1.0,  0.0,  0.0},
                        { 0.0, -0.5, -0.5,  1.0,  0.0,  0.0},
                        { 0.0,  0.5,  0.5,  0.0,  0.0,  0.0},
                        { 0.0, -0.5, -0.5, -1.0,  0.0,  1.0},
                } },
                // t=2 pair 2NN
                { {
                        { 0.0,  0.0,  0.0, -1.0,  1.0,  0.0},
                        { 0.0,  0.0,  0.0,  1.0,  0.0,  0.0},
                        { 0.0,  0.0,  0.0, -1.0,  0.0,  1.0},
                } },
                // t=3 pair 1NN
                { {
                        { 0.0,  0.0, -1.0,  0.0,  1.0,  0.0},
                        { 0.0,  0.0,  1.0,  0.0,  0.0,  0.0},
                        { 0.0,  0.0, -1.0,  0.0,  0.0,  1.0},
                } },
                // t=4 point
                { {
                        { 0.0,  0.0,  0.0,  0.0,  1.0,  0.0},
                        { 0.0,  0.0,  0.0,  0.0,  0.0,  1.0},
                } },
        };

        int uListLen = 6;

        // cfCoeffs: u = cfCoeffs . u2vals. This project's u2vals IS the
        // u-vector directly for this fixture (CEWorkbench's CVCF basis
        // already uses u/uFull as one and the same vector -- there is no
        // separate orthogonal-basis transform layered on top here), so
        // cfCoeffs is the 6x6 identity.
        double[][] cfCoeffs = new double[uListLen][uListLen];
        for (int i = 0; i < uListLen; i++) cfCoeffs[i][i] = 1.0;

        return new CvmPhaseData(
                "BCC_A2", nComp, ncf,
                tcdis, mhdis, kbdis,
                lc, lcv, mh, wcv, cmat,
                uListLen, cfCoeffs,
                U2_NAMES, E_NAMES);
    }
}
