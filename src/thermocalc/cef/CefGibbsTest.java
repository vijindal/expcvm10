package thermocalc.cef;

import domain.UnaryGibbs;
import thermocalc.rk.RkGibbs;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link CefGibbs} and {@link CefPhaseModel}.
 *
 * <h2>Reference system: V-Nb binary C15 (Gorbacheva 2019)</h2>
 * As used in the active path of clusGen_25 delxGC15, nc=2 case:
 * <pre>
 *   G[V][V]  = 3·GHSERV  + 15000
 *   G[V][Zr] = 2·GHSERV  + GHSERZR - 10796.71 + 3.8144·T
 *   G[Zr][V] = 2·GHSERZR + GHSERV  + 40796.71 - 3.8144·T
 *   G[Zr][Zr]= 3·GHSERZR + 15000
 *   L12x1x = 35860.62 + 12.7268·T  (pair V,Zr on SL1, single V on SL2)
 *   L12x2x = 35860.62 + 12.7268·T  (pair V,Zr on SL1, single Zr on SL2)
 *   L1x12x =  4086.75 +  9.5308·T  (single V on SL1, pair V,Zr on SL2)
 *   L2x12x =  4086.75 +  9.5308·T  (single Zr on SL1, pair V,Zr on SL2)
 * </pre>
 *
 * <h2>Mathematica reference output (line 1261 of clusGen_25)</h2>
 * <pre>
 *   delxGC15[elementList, numComp, 1000, P, {2/3, 1/3}, {0.99, 0.01, 0.02, 0.98}, {ΔT,ΔP,mu}]
 *   →  GN = -130296  J/mol
 * </pre>
 *
 * Run tdb-dependent tests with:
 * <pre>
 *   mvn test -Dsgte.tdb.path=/path/to/sgte_unary_v44.tdb
 * </pre>
 */
class CefGibbsTest {

    private static final double R      = CefGibbs.R;
    private static final double T1000  = 1000.0;

    // Binary V-Zr test point from Mathematica
    private static final double[] Y_BIN = {0.99, 0.01, 0.02, 0.98};
    // nc=2: {y[0][0]=0.99, y[0][1]=0.01, y[1][0]=0.02, y[1][1]=0.98}

    private static final String TDB_PATH =
            System.getProperty("sgte.tdb.path", "sgte_unary_v44.tdb");

    // =====================================================================
    // 1. CefEndMember unit tests
    // =====================================================================

    @Nested @DisplayName("CefEndMember")
    class EndMemberTests {

        @Test @DisplayName("G(T) = a1·GHSER_i + a2·GHSER_j + deltaA + deltaB·T")
        void gValue() {
            RkGibbs.G0Function ghserV  = t -> -10000.0 + 5.0 * t;
            RkGibbs.G0Function ghserZr = t -> -20000.0 + 8.0 * t;
            // G[V][Zr] = 2·GHSERV + 1·GHSERZR - 10796.71 + 3.8144·T
            CefEndMember em = new CefEndMember(0, 1, 2.0, 1.0,
                    ghserV, ghserZr, -10796.71, 3.8144);

            double expected = 2.0 * (-10000.0 + 5.0 * T1000)
                    + 1.0 * (-20000.0 + 8.0 * T1000)
                    - 10796.71 + 3.8144 * T1000;
            assertEquals(expected, em.G(T1000), 1e-6);
        }

        @Test @DisplayName("dG/dT = a1·dGHSER_i/dT + a2·dGHSER_j/dT + deltaB")
        void dGdT() {
            // GHSER linear in T: dGHSER/dT = slope
            RkGibbs.G0Function ghserV  = t -> -10000.0 + 5.0 * t;
            RkGibbs.G0Function ghserZr = t -> -20000.0 + 8.0 * t;
            CefEndMember em = new CefEndMember(0, 1, 2.0, 1.0,
                    ghserV, ghserZr, -10796.71, 3.8144);
            // dG/dT = 2·5 + 1·8 + 3.8144 = 21.8144
            assertEquals(21.8144, em.dGdT(T1000), 1e-4);
        }
    }

    // =====================================================================
    // 2. CefInteractionParam unit tests
    // =====================================================================

    @Nested @DisplayName("CefInteractionParam")
    class InteractionParamTests {

        @Test @DisplayName("L(T) = a + b·T")
        void lValue() {
            CefInteractionParam p = new CefInteractionParam(0, 0, 1, 0,
                    35860.62, 12.7268);
            assertEquals(35860.62 + 12.7268 * T1000, p.L(T1000), 1e-4);
        }

        @Test @DisplayName("dL/dT = b")
        void dLdT() {
            CefInteractionParam p = new CefInteractionParam(0, 0, 1, 0,
                    35860.62, 12.7268);
            assertEquals(12.7268, p.dLdT(), 1e-9);
        }

        @Test @DisplayName("zero factory gives L = 0")
        void zeroFactory() {
            CefInteractionParam p = CefInteractionParam.zero(0, 0, 1, 0);
            assertEquals(0.0, p.L(T1000), 1e-9);
        }

        @Test @DisplayName("pairA >= pairB throws")
        void badOrderThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CefInteractionParam(0, 1, 0, 0, 0.0, 0.0));
        }
    }

    // =====================================================================
    // 3. CefGibbs energy components — no tdb needed (fixed G0 values)
    // =====================================================================

    @Nested @DisplayName("CefGibbs energy components")
    class EnergyComponentTests {

        /**
         * Builds a binary V-Zr CefGibbs with fixed reference G0 values
         * (independent of tdb) for component-level testing.
         */
        private CefGibbs buildVZrFixed() {
            double gV  = -50000.0;   // fixed GHSER_V  at T1000
            double gZr = -40000.0;   // fixed GHSER_Zr at T1000

            RkGibbs.G0Function fV  = t -> gV;
            RkGibbs.G0Function fZr = t -> gZr;

            // G[0][0]=G[V][V] = 3·GHSERV+15000, G[0][1]=G[V][Zr], G[1][0]=G[Zr][V], G[1][1]=G[Zr][Zr]
            CefEndMember[] g0 = {
                new CefEndMember(0, 0, 2.0, 1.0, fV,  fV,  15000.0,  0.0),  // V-V
                new CefEndMember(0, 1, 2.0, 1.0, fV,  fZr, -10796.71, 3.8144), // V-Zr
                new CefEndMember(1, 0, 2.0, 1.0, fZr, fV,  40796.71, -3.8144), // Zr-V
                new CefEndMember(1, 1, 2.0, 1.0, fZr, fZr, 15000.0,  0.0)   // Zr-Zr
            };

            List<CefInteractionParam> ints = List.of(
                new CefInteractionParam(0, 0, 1, 0, 35860.62, 12.7268), // L12x1x
                new CefInteractionParam(0, 0, 1, 1, 35860.62, 12.7268), // L12x2x
                new CefInteractionParam(1, 0, 1, 0,  4086.75,  9.5308), // L1x12x
                new CefInteractionParam(1, 0, 1, 1,  4086.75,  9.5308)  // L2x12x
            );

            return new CefGibbs(2, g0, ints);
        }

        @Test @DisplayName("nip = 2·nc for binary")
        void nipCorrect() {
            assertEquals(4, buildVZrFixed().nip());
        }

        @Test @DisplayName("G₀ = sum of y[0][i]·y[1][j]·G[i][j]")
        void g0RefValue() {
            CefGibbs rk = buildVZrFixed();
            double[] y = Y_BIN;
            // G[V][V] at T1000 with gV=-50000: 2*(-50000) + 1*(-50000) + 15000 = -135000
            double GVV  = 2*(-50000) + 1*(-50000) + 15000;
            // G[V][Zr]: 2*(-50000) + 1*(-40000) + (-10796.71) + 3.8144*1000 = -143652.56
            double GVZr = 2*(-50000) + 1*(-40000) + (-10796.71) + 3.8144*1000;
            double GZrV = 2*(-40000) + 1*(-50000) + 40796.71 + (-3.8144)*1000;
            double GZrZr= 2*(-40000) + 1*(-40000) + 15000;
            double expected = y[0]*y[2]*GVV + y[0]*y[3]*GVZr
                            + y[1]*y[2]*GZrV + y[1]*y[3]*GZrZr;
            assertEquals(expected, rk.g0Ref(y, T1000), 1.0);
        }

        @Test @DisplayName("G_id: sublattice-weighted ideal entropy")
        void gIdFormula() {
            CefGibbs rk = buildVZrFixed();
            double[] y = Y_BIN;
            // G_id = R*T*(2*(y00*ln(y00)+y01*ln(y01)) + 1*(y10*ln(y10)+y11*ln(y11)))
            double sl1 = y[0]*Math.log(y[0]) + y[1]*Math.log(y[1]);
            double sl2 = y[2]*Math.log(y[2]) + y[3]*Math.log(y[3]);
            double expected = R * T1000 * (2*sl1 + 1*sl2);
            assertEquals(expected, rk.gId(y, T1000), 1e-6);
        }

        @Test @DisplayName("G is additive: G = G₀ + G_id + G_Em")
        void gAdditivity() {
            CefGibbs rk = buildVZrFixed();
            double[] y = Y_BIN;
            double G = rk.evaluate(y, T1000);
            double sum = rk.g0Ref(y, T1000) + rk.gId(y, T1000) + rk.gEm(y, T1000);
            assertEquals(G, sum, 1e-9);
        }

        @Test @DisplayName("Gradient: finite difference vs analytical (all components)")
        void gradientFiniteDiff() {
            CefGibbs rk = buildVZrFixed();
            double[] Gx = rk.gradient(Y_BIN, T1000);
            double h = 1e-6;
            for (int m = 0; m < 4; m++) {
                double[] yp = Y_BIN.clone(); yp[m] += h;
                double[] ym = Y_BIN.clone(); ym[m] -= h;
                double num = (rk.evaluate(yp, T1000) - rk.evaluate(ym, T1000)) / (2*h);
                assertEquals(num, Gx[m],
                        Math.abs(num) * 1e-4 + 1.0,
                        "Gx[" + m + "] mismatch");
            }
        }

        @Test @DisplayName("Hessian: symmetric")
        void hessianSymmetry() {
            CefGibbs rk = buildVZrFixed();
            double[][] Gxx = rk.hessian(Y_BIN, T1000);
            for (int m = 0; m < 4; m++)
                for (int n = 0; n < 4; n++)
                    assertEquals(Gxx[m][n], Gxx[n][m], 1e-6,
                            "Gxx not symmetric at [" + m + "][" + n + "]");
        }

        @Test @DisplayName("Hessian: finite difference vs analytical")
        void hessianFiniteDiff() {
            CefGibbs rk = buildVZrFixed();
            double[][] Gxx = rk.hessian(Y_BIN, T1000);
            double h = 1e-5;
            for (int m = 0; m < 4; m++) {
                for (int n = 0; n < 4; n++) {
                    double[] yp = Y_BIN.clone(); yp[n] += h;
                    double[] ym = Y_BIN.clone(); ym[n] -= h;
                    double num = (rk.gradient(yp, T1000)[m]
                                - rk.gradient(ym, T1000)[m]) / (2*h);
                    assertEquals(num, Gxx[m][n],
                            Math.abs(num) * 1e-3 + 1.0,
                            "Gxx[" + m + "][" + n + "] mismatch");
                }
            }
        }

        @Test @DisplayName("GxT: finite difference vs analytical")
        void gradientDTFiniteDiff() {
            CefGibbs rk = buildVZrFixed();
            double[] GxT = rk.gradientDT(Y_BIN, T1000);
            double h = 0.1;
            for (int m = 0; m < 4; m++) {
                double num = (rk.gradient(Y_BIN, T1000+h)[m]
                            - rk.gradient(Y_BIN, T1000-h)[m]) / (2*h);
                assertEquals(num, GxT[m],
                        Math.abs(num) * 1e-4 + 0.01,
                        "GxT[" + m + "] mismatch");
            }
        }

        @Test @DisplayName("y.length != nip throws")
        void wrongYLengthThrows() {
            CefGibbs rk = buildVZrFixed();
            assertThrows(IllegalArgumentException.class,
                    () -> rk.evaluate(new double[]{0.5, 0.5, 0.5}, T1000));
        }
    }

    // =====================================================================
    // 4. Mathematica cross-validation (requires tdb)
    // =====================================================================

    @Nested @DisplayName("Mathematica cross-validation (requires tdb)")
    class MathematicaValidation {

        private CefGibbs vZrCef;

        @BeforeEach
        void load() {
            assumeTrue(new java.io.File(TDB_PATH).exists(),
                    "SGTE .tdb not found. Set -Dsgte.tdb.path=<path> to enable.");
            try {
                UnaryGibbs ug = UnaryGibbs.fromTdb(TDB_PATH, "V", "ZR");

                RkGibbs.G0Function fV  = t -> ug.ghser("V",  t);
                RkGibbs.G0Function fZr = t -> ug.ghser("ZR", t);

                // V-Zr system (Gorbacheva 2019) — active system in clusGen_25 delxGC15
                // G[i][j] = 2·GHSER_i(SL1) + 1·GHSER_j(SL2) + ΔE
                CefEndMember[] g0 = {
                    new CefEndMember(0, 0, 2.0, 1.0, fV,  fV,  15000.0,   0.0),  // G[V][V]  = 3·GHSERV+15000
                    new CefEndMember(0, 1, 2.0, 1.0, fV,  fZr, -10796.71, 3.8144), // G[V][Zr]
                    new CefEndMember(1, 0, 2.0, 1.0, fZr, fV,  40796.71, -3.8144), // G[Zr][V]
                    new CefEndMember(1, 1, 2.0, 1.0, fZr, fZr, 15000.0,   0.0)   // G[Zr][Zr]= 3·GHSERZR+15000
                };

                List<CefInteractionParam> ints = List.of(
                    new CefInteractionParam(0, 0, 1, 0, 35860.62, 12.7268), // L12x1x: pair(V,Zr) on SL1, V on SL2
                    new CefInteractionParam(0, 0, 1, 1, 35860.62, 12.7268), // L12x2x: pair(V,Zr) on SL1, Zr on SL2
                    new CefInteractionParam(1, 0, 1, 0,  4086.75,  9.5308), // L1x12x: V on SL1, pair(V,Zr) on SL2
                    new CefInteractionParam(1, 0, 1, 1,  4086.75,  9.5308)  // L2x12x: Zr on SL1, pair(V,Zr) on SL2
                );

                vZrCef = new CefGibbs(2, g0, ints);
            } catch (Exception e) {
                vZrCef = null;
            }
            assumeTrue(vZrCef != null, "Failed to build CefGibbs");
        }

        /**
         * Primary validation:
         * Mathematica line 1261: delxGC15[{V,Zr}, 2, 1000, P, {2/3,1/3}, {0.99,0.01,0.02,0.98}, ...]
         * → GN = -130296  J/mol
         *
         * Note: xN={2/3,1/3} are bulk compositions, yN={0.99,0.01,0.02,0.98} are site fractions.
         * The G evaluation uses yN, not xN.
         */
        @Test @DisplayName("G(V-Zr C15, 1000K, y={0.99,0.01,0.02,0.98}) ≈ -130296 J/mol")
        void gibbsMatchesMathematica() {
            double G = vZrCef.evaluate(Y_BIN, T1000);
            assertEquals(-130296.0, G, 20.0,
                    "G at (1000K, V-Zr C15) vs Mathematica output -130296");
        }

        @Test @DisplayName("G_id is negative (entropy contribution)")
        void gIdNegative() {
            assertTrue(vZrCef.gId(Y_BIN, T1000) < 0);
        }

        @Test @DisplayName("Gradient length = nip = 4 for binary")
        void gradientLength() {
            assertEquals(4, vZrCef.gradient(Y_BIN, T1000).length);
        }

        @Test @DisplayName("Hessian is 4×4 and symmetric")
        void hessianShape() {
            double[][] Gxx = vZrCef.hessian(Y_BIN, T1000);
            assertEquals(4, Gxx.length);
            assertEquals(4, Gxx[0].length);
            for (int m = 0; m < 4; m++)
                for (int n = 0; n < 4; n++)
                    assertEquals(Gxx[m][n], Gxx[n][m], 1e-4);
        }

        @Test @DisplayName("GxT matches finite difference (tdb G0 functions)")
        void gxtMatchesFiniteDiff() {
            double[] GxT = vZrCef.gradientDT(Y_BIN, T1000);
            double h = 0.1;
            for (int m = 0; m < 4; m++) {
                double num = (vZrCef.gradient(Y_BIN, T1000+h)[m]
                            - vZrCef.gradient(Y_BIN, T1000-h)[m]) / (2*h);
                assertEquals(num, GxT[m], Math.abs(num) * 1e-4 + 0.01,
                        "GxT[" + m + "] vs finite diff");
            }
        }

        @Test @DisplayName("G additivity with tdb G0")
        void gAdditivity() {
            double G = vZrCef.evaluate(Y_BIN, T1000);
            double sum = vZrCef.g0Ref(Y_BIN, T1000)
                       + vZrCef.gId(Y_BIN, T1000)
                       + vZrCef.gEm(Y_BIN, T1000);
            assertEquals(G, sum, 1e-6);
        }

        @Test @DisplayName("CefPhaseModel.compute: G matches CefGibbs.evaluate")
        void phaseModelGConsistent() {
            CefPhaseModel model = new CefPhaseModel(vZrCef, "LAVES_C15_CEF");
            double[] mu   = new double[2];  // zero chemical potentials
            CefPhaseModel.Result r = model.compute(T1000, 0.0, Y_BIN, 0.0, 0.0, mu);
            assertEquals(vZrCef.evaluate(Y_BIN, T1000), r.GN, 1e-6);
        }

        @Test @DisplayName("CefPhaseModel: deln length = nc = 2")
        void phaseModelDelnLength() {
            CefPhaseModel model = new CefPhaseModel(vZrCef, "LAVES_C15_CEF");
            CefPhaseModel.Result r = model.compute(T1000, 0.0, Y_BIN, 0.0, 0.0, new double[2]);
            assertEquals(2, r.delnN.length);
        }

        @Test @DisplayName("CefPhaseModel: dely length = nip = 4")
        void phaseModelDelyLength() {
            CefPhaseModel model = new CefPhaseModel(vZrCef, "LAVES_C15_CEF");
            CefPhaseModel.Result r = model.compute(T1000, 0.0, Y_BIN, 0.0, 0.0, new double[2]);
            assertEquals(4, r.delyN.length);
        }

        @Test @DisplayName("S = -dG/dT (numerical check)")
        void entropyCheck() {
            CefPhaseModel model = new CefPhaseModel(vZrCef, "LAVES_C15_CEF");
            CefPhaseModel.Result r = model.compute(T1000, 0.0, Y_BIN, 0.0, 0.0, new double[2]);
            double h = 0.1;
            double numS = -(vZrCef.evaluate(Y_BIN, T1000+h)
                          - vZrCef.evaluate(Y_BIN, T1000-h)) / (2*h);
            assertEquals(numS, r.SN, Math.abs(numS) * 1e-4 + 0.01,
                    "S vs -dG/dT finite diff");
        }

        @Test @DisplayName("H = G + T·S")
        void enthalpyCheck() {
            CefPhaseModel model = new CefPhaseModel(vZrCef, "LAVES_C15_CEF");
            CefPhaseModel.Result r = model.compute(T1000, 0.0, Y_BIN, 0.0, 0.0, new double[2]);
            assertEquals(r.GN + T1000 * r.SN, r.HN, 1e-4);
        }
    }
}
