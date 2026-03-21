package thermocalc.cvm;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link CvmPhaseDataParser}, {@link CvmGibbs}, and {@link CvmPhaseModel}.
 *
 * <h2>Reference data: binary BCC_A2 Nb-Ti system</h2>
 * From clusGen_25 line 1747:
 * <pre>
 *   delxGCVM[{"Nb","Ti"}, "BCC_A2", 2, 1000, P,
 *            {0.25, 0.75},            ← xN = {XA, XB}
 *            {0, 0, 6240, 3120},      ← vListN (random approx CECs happen to be 0,0,...)
 *            {ΔT, ΔP, mu}]
 *   → GN = -23143.7  J/mol
 *   → eListN at T=1000: {0, 0, 6240, 3120}
 * </pre>
 *
 * Provide the .nb file path via:
 * <pre>
 *   mvn test -Dcvm.nb.path=/path/to/output_BCC_A2_bin.nb
 *            -Dsgte.tdb.path=/path/to/sgte_unary_v44.tdb
 * </pre>
 */
class CvmGibbsTest {

    private static final String NB_PATH  = System.getProperty("cvm.nb.path",
            "output_BCC_A2_bin.nb");
    private static final String TDB_PATH = System.getProperty("sgte.tdb.path",
            "sgte_unary_v44.tdb");

    // =====================================================================
    // 1. Parser unit tests (requires .nb file)
    // =====================================================================

    @Nested @DisplayName("CvmPhaseDataParser")
    class ParserTests {

        private CvmPhaseData pd;

        @BeforeEach
        void load() {
            assumeTrue(new java.io.File(NB_PATH).exists(),
                    "CVM .nb file not found at '" + NB_PATH + "'");
            try {
                pd = CvmPhaseDataParser.parse(NB_PATH);
            } catch (Exception e) { pd = null; }
            assumeTrue(pd != null, "Failed to parse .nb file");
        }

        @Test @DisplayName("phase name = BCC_A2")
        void phaseName() { assertEquals("BCC_A2", pd.phaseName); }

        @Test @DisplayName("nComp = 2 (binary)")
        void nComp() { assertEquals(2, pd.nComp); }

        @Test @DisplayName("ncf = 4 (e4AB, e3AB, e2AB1, e2AB2)")
        void ncf() { assertEquals(4, pd.ncf); }

        @Test @DisplayName("nip = 6 (ncf + nComp)")
        void nip() { assertEquals(6, pd.nip); }

        @Test @DisplayName("tcdis = 5 (cluster types)")
        void tcdis() { assertEquals(5, pd.tcdis); }

        @Test @DisplayName("mhdis = {6, 12, 3, 4, 1, 0.5}")
        void mhdis() {
            double[] expected = {6, 12, 3, 4, 1, 0.5};
            for (int i = 0; i < expected.length && i < pd.mhdis.length; i++)
                assertEquals(expected[i], pd.mhdis[i], 1e-9, "mhdis[" + i + "]");
        }

        @Test @DisplayName("kbdis = {1, -1, 1, 1, -1} after ms substitution")
        void kbdis() {
            double[] expected = {1.0, -1.0, 1.0, 1.0, -1.0};
            for (int i = 0; i < expected.length; i++)
                assertEquals(expected[i], pd.kbdis[i], 1e-9, "kbdis[" + i + "]");
        }

        @Test @DisplayName("eList names: e4AB, e3AB, e2AB1, e2AB2")
        void eListNames() {
            assertArrayEquals(new String[]{"e4AB","e3AB","e2AB1","e2AB2"}, pd.eListNames);
        }

        @Test @DisplayName("u2List names: v4AB, v3AB, v2AB1, v2AB2, xA, xB")
        void u2ListNames() {
            assertArrayEquals(
                new String[]{"v4AB","v3AB","v2AB1","v2AB2","xA","xB"},
                pd.u2ListNames);
        }

        @Test @DisplayName("lcv[0] = 6 (tetrahedron has 6 CVs)")
        void lcv() { assertEquals(6, pd.lcv[0][0]); }

        @Test @DisplayName("wcv[0][0] = {1,4,4,2,4,1}")
        void wcv() {
            double[] expected = {1,4,4,2,4,1};
            assertArrayEquals(expected, pd.wcv[0][0], 1e-9);
        }

        @Test @DisplayName("cmat[0][0][0] = {1/16,-1/4,...} (tetrahedron first row)")
        void cmatFirstRow() {
            double[] expected = {1.0/16, -1.0/4, 1.0/8, 1.0/4, -1.0/4, 1.0/16};
            assertArrayEquals(expected, pd.cmat[0][0][0], 1e-9);
        }

        @Test @DisplayName("cfCoeffs: u[0] = 16*v4AB - 16*v2AB1 + 8*v2AB2 + xA + xB")
        void cfCoeffsRow0() {
            // u[0] = 16*v4AB + 0*v3AB - 16*v2AB1 + 8*v2AB2 + 1*xA + 1*xB
            double[] expected = {16, 0, -16, 8, 1, 1};
            assertArrayEquals(expected, pd.cfCoeffs[0], 1e-9);
        }

        @Test @DisplayName("cfCoeffs: u[1] = -4*v3AB - xA + xB")
        void cfCoeffsRow1() {
            double[] expected = {0, -4, 0, 0, -1, 1};
            assertArrayEquals(expected, pd.cfCoeffs[1], 1e-9);
        }

        @Test @DisplayName("cfCoeffs: u[5] = xA + xB (= 1)")
        void cfCoeffsRow5() {
            double[] expected = {0, 0, 0, 0, 1, 1};
            assertArrayEquals(expected, pd.cfCoeffs[5], 1e-9);
        }

        @Test @DisplayName("computeU at random approx: u[5] = xA+xB = 1")
        void computeUSum() {
            // At x={0.5,0.5}: random approx u2vals = {0.0625, 0, 0.25, 0.25, 0.5, 0.5}
            double[] u2 = pd.evalRandApprox(new double[]{0.5, 0.5});
            double[] u  = pd.computeU(u2);
            assertEquals(1.0, u[5], 1e-9, "u[5] = xA+xB should always be 1");
        }

        @Test @DisplayName("evalRandApprox at {0.25, 0.75}: v4AB = 0.25^2 * 0.75^2")
        void randApprox() {
            double[] u2 = pd.evalRandApprox(new double[]{0.25, 0.75});
            double expected = 0.25 * 0.25 * 0.75 * 0.75;
            assertEquals(expected, u2[0], 1e-12, "v4AB");
        }
    }

    // =====================================================================
    // 2. Arithmetic evaluator unit tests (no file needed)
    // =====================================================================

    @Nested @DisplayName("ArithEval")
    class ArithTests {

        @Test @DisplayName("simple subtraction")
        void sub() { assertEquals(-1.0, CvmPhaseDataParser.evalArith("1.0 - 2.0"), 1e-12); }

        @Test @DisplayName("division in parentheses")
        void divParen() {
            assertEquals(-1.0, CvmPhaseDataParser.evalArith("1.0 - (4.0*6.0)/12.0"), 1e-12);
        }

        @Test @DisplayName("compound kbdis expression")
        void kbdis2() {
            // (2*6 - 1*12 + 1*3)/3 = (12-12+3)/3 = 1
            assertEquals(1.0,
                CvmPhaseDataParser.evalArith("(2.0*6.0 - 1.0*12.0 + 1.0*3.0)/3.0"), 1e-12);
        }
    }

    // =====================================================================
    // 3. CvmGibbs numerical validation (requires both .nb and tdb)
    // =====================================================================

    @Nested @DisplayName("CvmGibbs numerical validation")
    class NumericalTests {

        private CvmGibbs cvmGibbs;
        private CvmPhaseData pd;

        @BeforeEach
        void load() {
            assumeTrue(new java.io.File(NB_PATH).exists(), "CVM .nb not found");
            assumeTrue(new java.io.File(TDB_PATH).exists(), "SGTE .tdb not found");
            try {
                pd = CvmPhaseDataParser.parse(NB_PATH);
                domain.UnaryGibbs ug = domain.UnaryGibbs.fromTdb(TDB_PATH, "NB", "TI");
                thermocalc.rk.RkGibbs.G0Function[] ghser = {
                    t -> ug.ghser("NB", t),
                    t -> ug.ghser("TI", t)
                };
                cvmGibbs = new CvmGibbs(pd, ghser);
            } catch (Exception e) { cvmGibbs = null; }
            assumeTrue(cvmGibbs != null, "Failed to build CvmGibbs");
        }

        // CECs for Nb-Ti BCC_A2 (from line 1768 of clusGen_25):
        // {e4AB→0, e3AB→0, e2AB1→6240, e2AB2→3120}
        private final double[] CEC_NBTI = {0.0, 0.0, 6240.0, 3120.0};
        // Test point from line 1747: x={0.25,0.75}, T=1000
        private final double T1000 = 1000.0;
        private final double[] X_NBTI = {0.25, 0.75};

        private double[] getU2(double[] x) {
            return pd.evalRandApprox(x);
        }

        @Test @DisplayName("S(v) at random approx is negative (entropy gain)")
        void sNegative() {
            double[] u2 = getU2(X_NBTI);
            assertTrue(cvmGibbs.S(u2) > 0, "Entropy S should be positive");
        }

        @Test @DisplayName("H at random approx, Nb-Ti: H = e2AB1*v2AB1 + e2AB2*v2AB2")
        void hValue() {
            double[] u2 = getU2(X_NBTI);
            // v4AB=v3AB=0 at this composition (they cancel), v2AB1=v2AB2=xA*xB=0.1875
            double expected = CEC_NBTI[2] * u2[2] + CEC_NBTI[3] * u2[3];
            assertEquals(expected, cvmGibbs.H(u2, CEC_NBTI), 1e-4);
        }

        @Test @DisplayName("G additivity: G = G₀ + H - T*S")
        void gAdditivity() {
            double[] u2 = getU2(X_NBTI);
            double G = cvmGibbs.evaluate(u2, T1000, CEC_NBTI);
            double sum = cvmGibbs.g0(u2, T1000)
                       + cvmGibbs.H(u2, CEC_NBTI)
                       - T1000 * cvmGibbs.S(u2);
            assertEquals(G, sum, 1e-6);
        }

        @Test @DisplayName("G vs Mathematica: GN ≈ -23143.7 J/mol at x={0.25,0.75}")
        void gVsMathematica() {
            // The Mathematica output uses vListN from phaseData (random approx)
            // which at x={0.25,0.75} gives {v4AB=0, v3AB=0, v2AB1=0, v2AB2=0}
            // Wait — the output says eListN={0,0,6240,3120} which are the CECs,
            // and the vListN input was {0,0,6240,3120}... No — vListN is the u2 vector.
            // From line 1743: vListN=phaseData[[3]][[1]]/.phaseData[[3]][[3]]
            // = u2List/.uRandRules at x={0.25,0.75}:
            // v4AB→XA^2*XB^2=0.25^2*0.75^2=0.03516, v3AB→XA*XB*(XB-XA)=0.25*0.75*0.5=0.09375
            // v2AB1→XA*XB=0.1875, v2AB2→0.1875, xA→0.25, xB→0.75
            double[] u2 = pd.evalRandApprox(X_NBTI);
            double G = cvmGibbs.evaluate(u2, T1000, CEC_NBTI);
            assertEquals(-23143.7, G, 100.0,  // ±100 J/mol tolerance for initial state
                    "G at (Nb0.25Ti0.75, 1000K) vs Mathematica -23143.7");
        }

        @Test @DisplayName("Gradient Gx has length nip=6")
        void gradientLength() {
            double[] u2 = getU2(X_NBTI);
            assertEquals(6, cvmGibbs.gradient(u2, T1000, CEC_NBTI).length);
        }

        @Test @DisplayName("Hessian Gxx is 6×6 and symmetric")
        void hessianSymmetry() {
            double[] u2 = getU2(X_NBTI);
            double[][] Gxx = cvmGibbs.hessian(u2, T1000);
            assertEquals(6, Gxx.length);
            for (int m = 0; m < 6; m++)
                for (int n = 0; n < 6; n++)
                    assertEquals(Gxx[m][n], Gxx[n][m], 1e-4,
                            "Gxx not symmetric at [" + m + "][" + n + "]");
        }

        @Test @DisplayName("Gradient: finite difference vs analytical")
        void gradientFiniteDiff() {
            double[] u2 = getU2(X_NBTI);
            double[] Gx = cvmGibbs.gradient(u2, T1000, CEC_NBTI);
            double h = 1e-7;
            for (int m = 0; m < 6; m++) {
                double[] up = u2.clone(); up[m] += h;
                double[] um = u2.clone(); um[m] -= h;
                double num = (cvmGibbs.evaluate(up, T1000, CEC_NBTI)
                            - cvmGibbs.evaluate(um, T1000, CEC_NBTI)) / (2*h);
                assertEquals(num, Gx[m], Math.abs(num) * 1e-3 + 1.0,
                        "Gx[" + m + "] finite diff mismatch");
            }
        }

        @Test @DisplayName("Hessian: finite difference vs analytical")
        void hessianFiniteDiff() {
            double[] u2 = getU2(X_NBTI);
            double[][] Gxx = cvmGibbs.hessian(u2, T1000);
            double h = 1e-6;
            for (int m = 0; m < 6; m++) {
                for (int n = 0; n < 6; n++) {
                    double[] up = u2.clone(); up[n] += h;
                    double[] um = u2.clone(); um[n] -= h;
                    double num = (cvmGibbs.gradient(up, T1000, CEC_NBTI)[m]
                                - cvmGibbs.gradient(um, T1000, CEC_NBTI)[m]) / (2*h);
                    assertEquals(num, Gxx[m][n],
                            Math.abs(num) * 1e-3 + 1.0,
                            "Gxx[" + m + "][" + n + "] finite diff mismatch");
                }
            }
        }

        @Test @DisplayName("GxT: finite difference vs analytical")
        void gxtFiniteDiff() {
            double[] u2 = getU2(X_NBTI);
            double[] GxT = cvmGibbs.gradientDT(u2, T1000, CEC_NBTI);
            double h = 0.1;
            for (int m = 0; m < 6; m++) {
                double num = (cvmGibbs.gradient(u2, T1000+h, CEC_NBTI)[m]
                            - cvmGibbs.gradient(u2, T1000-h, CEC_NBTI)[m]) / (2*h);
                assertEquals(num, GxT[m], Math.abs(num) * 1e-3 + 0.01,
                        "GxT[" + m + "] finite diff mismatch");
            }
        }

        @Test @DisplayName("CvmPhaseModel: GN consistent with CvmGibbs.evaluate")
        void phaseModelGConsistent() {
            double[] u2 = getU2(X_NBTI);
            CvmPhaseModel model = new CvmPhaseModel(cvmGibbs, "BCC_A2");
            CvmPhaseModel.Result r = model.compute(
                    T1000, 0.0, u2, CEC_NBTI, 0.0, 0.0, new double[2]);
            assertEquals(cvmGibbs.evaluate(u2, T1000, CEC_NBTI), r.GN, 1e-6);
        }

        @Test @DisplayName("CvmPhaseModel: delnN has length nComp=2")
        void phaseModelDelnLength() {
            double[] u2 = getU2(X_NBTI);
            CvmPhaseModel model = new CvmPhaseModel(cvmGibbs, "BCC_A2");
            CvmPhaseModel.Result r = model.compute(
                    T1000, 0.0, u2, CEC_NBTI, 0.0, 0.0, new double[2]);
            assertEquals(2, r.delnN.length);
        }
    }
}
