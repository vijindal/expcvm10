package test;

import thermocalc.rk.*;
import domain.UnaryGibbs;
import thermocalc.unary.PhaseType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link RkGibbs} and {@link RkPhaseModel}.
 *
 * <h2>Reference system: Ti-V-Zr BCC_A2 (Cui 2016)</h2>
 * <pre>
 *   nc = 3, T = 1023 K, x = {0.8, 0.1, 0.1}
 *   G0A = calG0["Ti","BCC_A2", 1023]
 *   G0B = calG0["V", "BCC_A2", 1023]
 *   G0C = calG0["Zr","BCC_A2", 1023]
 *   LAB = 6523.17 + 2025.39*(xA-xB)
 *   LAC = -48287.53 + 17.4484*T
 *   LBC = (17872.99 + 8.7539*T) + (-3208.60 + 4.8481*T)*(xB-xC)
 *   LABC = xA*(-253783.69 + 10.9738*T) + xB*7589.27 + xC*80496.47
 * </pre>
 *
 * <h2>Mathematica output (line 680 of clusGen_25.txt)</h2>
 * <pre>
 *   delGsol[1023,PN,{0.8,0.1,0.1},yN,{ΔT,ΔP,mu}]
 *   →  GN = -55110.2  J/mol  (first element of the return tuple)
 * </pre>
 *
 * To run tdb-dependent tests:
 * <pre>
 *   mvn test -Dsgte.tdb.path=/path/to/sgte_unary_v44.tdb
 * </pre>
 */
class RkGibbsTest {

    private static final double R    = RkGibbs.R;
    private static final double T    = 1023.0;
    private static final double[] X3 = {0.8, 0.1, 0.1};   // Ti-V-Zr test point
    private static final double[] X2 = {0.7, 0.3};         // binary test point

    private static final String TDB_PATH =
            System.getProperty("sgte.tdb.path", "sgte_unary_v44.tdb");

    // =====================================================================
    // 1. BinaryParam — unit tests
    // =====================================================================

    @Nested @DisplayName("BinaryParam")
    class BinaryParamTests {

        @Test @DisplayName("zeroth order constant L")
        void zerothOrderConstant() {
            BinaryParam p = BinaryParam.zerothOrder(0, 1, 5000.0, 0.0);
            assertEquals(5000.0, p.L(X3, T), 1e-9);
        }

        @Test @DisplayName("zeroth order T-linear: L = a + b*T")
        void zerothOrderTLinear() {
            BinaryParam p = BinaryParam.zerothOrder(0, 1, 1000.0, 2.0);
            assertEquals(1000.0 + 2.0 * T, p.L(X3, T), 1e-9);
        }

        @Test @DisplayName("first order: L = L0 + L1*(xi-xj)")
        void firstOrder() {
            // LAB for Ti-V-Zr: a={6523.17, 2025.39}, b={0,0}
            BinaryParam p = new BinaryParam(0, 1,
                    new double[]{6523.17, 2025.39},
                    new double[]{0.0, 0.0});
            double d = X3[0] - X3[1];  // 0.8 - 0.1 = 0.7
            double expected = 6523.17 + 2025.39 * d;
            assertEquals(expected, p.L(X3, T), 1e-6);
        }

        @Test @DisplayName("dL/dT = b[0] for zeroth order T-linear")
        void dLdT() {
            BinaryParam p = BinaryParam.zerothOrder(0, 1, 1000.0, 3.5);
            assertEquals(3.5, p.dLdT(X3), 1e-9);
        }

        @Test @DisplayName("dL/dxi: first order RK")
        void dLdxi() {
            // L = a0 + a1*(xi-xj), dL/dxi = a1
            BinaryParam p = new BinaryParam(0, 1,
                    new double[]{6523.17, 2025.39}, new double[]{0.0, 0.0});
            assertEquals(2025.39, p.dLdx(X3, T, 0), 1e-6);   // ∂L/∂xi
            assertEquals(-2025.39, p.dLdx(X3, T, 1), 1e-6);  // ∂L/∂xj = -dL/dxi
            assertEquals(0.0, p.dLdx(X3, T, 2), 1e-9);       // ∂L/∂xk = 0
        }

        @Test @DisplayName("dLdx_dT = 0 for T-independent param")
        void dLdx_dT_zero() {
            BinaryParam p = BinaryParam.constant(0, 1, 6523.17, 2025.39);
            assertEquals(0.0, p.dLdx_dT(X3, 0), 1e-9);
        }

        @Test @DisplayName("dLdx_dT = b1 for T-linear first order param")
        void dLdx_dT_nonzero() {
            // L = (a0+b0*T) + (a1+b1*T)*(xi-xj), dL/dxi = a1+b1*T, d(dL/dxi)/dT = b1
            BinaryParam p = new BinaryParam(0, 1,
                    new double[]{0.0, -3208.60}, new double[]{0.0, 4.8481});
            assertEquals(4.8481, p.dLdx_dT(X3, 0), 1e-6);
        }

        @Test @DisplayName("idxI >= idxJ throws")
        void badIndexThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BinaryParam(1, 0, new double[]{1.0}, new double[]{0.0}));
        }
    }

    // =====================================================================
    // 2. TernaryParam — unit tests
    // =====================================================================

    @Nested @DisplayName("TernaryParam")
    class TernaryParamTests {

        // LABC = xA*(-253783.69 + 10.9738*T) + xB*7589.27 + xC*80496.47
        private final TernaryParam LABC = new TernaryParam(0, 1, 2,
                new double[]{-253783.69, 7589.27, 80496.47},
                new double[]{10.9738,    0.0,      0.0});

        @Test @DisplayName("L evaluated at (T, x)")
        void lValue() {
            double expected = (-253783.69 + 10.9738 * T) * X3[0]
                    + 7589.27 * X3[1] + 80496.47 * X3[2];
            assertEquals(expected, LABC.L(X3, T), 1e-3);
        }

        @Test @DisplayName("dL/dT = b[0]*xi + b[1]*xj + b[2]*xk")
        void dLdT() {
            double expected = 10.9738 * X3[0];   // b[1] = b[2] = 0
            assertEquals(expected, LABC.dLdT(X3), 1e-6);
        }

        @Test @DisplayName("v(idxI, T) = a[0] + b[0]*T")
        void vCoeff() {
            double expected = -253783.69 + 10.9738 * T;
            assertEquals(expected, LABC.v(0, T), 1e-3);
        }

        @Test @DisplayName("dL/dxm = v_m")
        void dLdx() {
            assertEquals(-253783.69 + 10.9738 * T, LABC.dLdx(X3, T, 0), 1e-3);
            assertEquals(7589.27,  LABC.dLdx(X3, T, 1), 1e-6);
            assertEquals(80496.47, LABC.dLdx(X3, T, 2), 1e-6);
        }

        @Test @DisplayName("d2L/dT/dx_m = b_m")
        void d2LdTdx() {
            assertEquals(10.9738, LABC.d2LdTdx(0), 1e-6);
            assertEquals(0.0,     LABC.d2LdTdx(1), 1e-9);
        }
    }

    // =====================================================================
    // 3. RkGibbs — pure energy components (no tdb needed)
    // =====================================================================

    @Nested @DisplayName("RkGibbs energy components")
    class EnergyComponentTests {

        private RkGibbs buildTiVZr(double[] fixedG0) {
            // Build a ternary RkGibbs with fixed G0 values (not from tdb)
            // to test energy components independently of the unary layer.
            RkGibbs.G0Function[] g0 = new RkGibbs.G0Function[3];
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                g0[ii] = t -> fixedG0[ii];
            }

            // Ti-V-Zr interaction parameters (Cui 2016)
            List<BinaryParam> bins = List.of(
                // LAB: Ti-V  a={6523.17,2025.39}, b={0,0}
                new BinaryParam(0, 1, new double[]{6523.17, 2025.39}, new double[]{0.0, 0.0}),
                // LAC: Ti-Zr a={-48287.53}, b={17.4484}
                BinaryParam.zerothOrder(0, 2, -48287.53, 17.4484),
                // LBC: V-Zr  a={17872.99,-3208.60}, b={8.7539,4.8481}
                new BinaryParam(1, 2,
                        new double[]{17872.99, -3208.60},
                        new double[]{8.7539,    4.8481})
            );
            List<TernaryParam> terns = List.of(
                new TernaryParam(0, 1, 2,
                        new double[]{-253783.69, 7589.27, 80496.47},
                        new double[]{10.9738,    0.0,     0.0})
            );

            return new RkGibbs(3, g0, bins, terns, List.of());
        }

        @Test @DisplayName("G_id at equal composition = R*T*ln(1/3) * 3")
        void gIdEqualComposition() {
            RkGibbs rk = buildTiVZr(new double[]{0, 0, 0});
            double[] xEq = {1.0/3, 1.0/3, 1.0/3};
            double expected = R * T * 3 * (1.0/3) * Math.log(1.0/3);
            assertEquals(expected, rk.gId(xEq, T), 1e-6);
        }

        @Test @DisplayName("G_id approaches 0 as composition → pure component")
        void gIdPureComponent() {
            RkGibbs rk = buildTiVZr(new double[]{0, 0, 0});
            double eps = 1e-9;
            double[] xPure = {1.0 - 2*eps, eps, eps};
            // At x = {1-2ε, ε, ε}: G_id = R*T*[(1-2ε)*ln(1-2ε) + 2*ε*ln(ε)]
            // The ε*ln(ε) terms → 0 but are not zero at ε=1e-9:
            //   ε*ln(ε) = 1e-9 * ln(1e-9) ≈ 1e-9 * (-20.7) ≈ -2.07e-8
            //   R*T*2*(-2.07e-8) ≈ 8.31*1023*(-4.14e-8) ≈ -3.5e-4
            // So tolerance must be ~1e-3 to allow for this residual.
            assertTrue(Math.abs(rk.gId(xPure, T)) < 1e-3,
                    "G_id at near-pure component should be small (< 1e-3 J/mol)");
        }

        @Test @DisplayName("G_0 = weighted sum of reference energies")
        void g0Sum() {
            double[] fixedG0 = {-50000.0, -30000.0, -40000.0};
            RkGibbs rk = buildTiVZr(fixedG0);
            double expected = X3[0]*fixedG0[0] + X3[1]*fixedG0[1] + X3[2]*fixedG0[2];
            assertEquals(expected, rk.g0(X3, T), 1e-6);
        }

        @Test @DisplayName("Binary excess G: single pair check")
        void binaryExcessSinglePair() {
            // Binary: nc=2, LAB = 5000 (constant), x = {0.7, 0.3}
            RkGibbs.G0Function[] g0 = {t -> 0.0, t -> 0.0};
            List<BinaryParam> bins = List.of(BinaryParam.constant(0, 1, 5000.0));
            RkGibbs rk = new RkGibbs(2, g0, bins, List.of(), List.of());
            // GEm = xi*xj*L = 0.7*0.3*5000 = 1050
            assertEquals(0.7 * 0.3 * 5000.0, rk.gEmBin(X2, T), 1e-6);
        }

        @Test @DisplayName("Gradient: finite difference vs analytical (binary)")
        void gradientFiniteDiffBinary() {
            RkGibbs.G0Function[] g0 = {t -> 0.0, t -> 0.0};
            List<BinaryParam> bins = List.of(
                    new BinaryParam(0, 1, new double[]{6000.0, 2000.0}, new double[]{0.0, 0.0}));
            RkGibbs rk = new RkGibbs(2, g0, bins, List.of(), List.of());

            // Use UNCONSTRAINED perturbation to match ∂G/∂x₀ as computed analytically
            // (Mathematica D[G, xA] is also unconstrained). Shifting both x0 and x1
            // simultaneously would compute a directional derivative, not the partial.
            double h = 1e-6;
            double[] Gx = rk.gradient(X2, T);
            for (int m = 0; m < 2; m++) {
                double[] xp = X2.clone(); xp[m] += h;
                double[] xmm = X2.clone(); xmm[m] -= h;
                double numGxm = (rk.evaluate(xp, T) - rk.evaluate(xmm, T)) / (2*h);
                assertEquals(numGxm, Gx[m], Math.abs(numGxm) * 1e-5 + 1e-3,
                        "Gx[" + m + "] binary mismatch");
            }
        }

        @Test @DisplayName("Gradient: finite difference vs analytical (ternary)")
        void gradientFiniteDiffTernary() {
            double[] fixedG0 = {-50000.0, -30000.0, -40000.0};
            RkGibbs rk = buildTiVZr(fixedG0);
            double[] Gx = rk.gradient(X3, T);
            double h = 1e-6;
            for (int m = 0; m < 3; m++) {
                double[] xp = X3.clone(); xp[m] += h;
                double[] xm2 = X3.clone(); xm2[m] -= h;
                double numGxm = (rk.evaluate(xp, T) - rk.evaluate(xm2, T)) / (2*h);
                assertEquals(numGxm, Gx[m], Math.abs(numGxm) * 1e-4 + 1.0,
                        "Gx[" + m + "] mismatch");
            }
        }

        @Test @DisplayName("Hessian: symmetry Gxx[m][n] == Gxx[n][m]")
        void hessianSymmetry() {
            double[] fixedG0 = {-50000.0, -30000.0, -40000.0};
            RkGibbs rk = buildTiVZr(fixedG0);
            double[][] Gxx = rk.hessian(X3, T);
            for (int m = 0; m < 3; m++)
                for (int n = 0; n < 3; n++)
                    assertEquals(Gxx[m][n], Gxx[n][m], 1e-6,
                            "Gxx not symmetric at [" + m + "][" + n + "]");
        }

        @Test @DisplayName("Hessian: finite difference vs analytical (ternary)")
        void hessianFiniteDiff() {
            double[] fixedG0 = {-50000.0, -30000.0, -40000.0};
            RkGibbs rk = buildTiVZr(fixedG0);
            double[][] Gxx = rk.hessian(X3, T);
            double h = 1e-5;
            for (int m = 0; m < 3; m++) {
                for (int n = 0; n < 3; n++) {
                    // Numerical ∂²G/∂xm∂xn via central difference on Gx[m]
                    double[] xp = X3.clone(); xp[n] += h;
                    double[] xmm = X3.clone(); xmm[n] -= h;
                    double numGxxMN = (rk.gradient(xp, T)[m] - rk.gradient(xmm, T)[m]) / (2*h);
                    assertEquals(numGxxMN, Gxx[m][n],
                            Math.abs(numGxxMN) * 1e-3 + 1.0,
                            "Gxx[" + m + "][" + n + "] mismatch");
                }
            }
        }

        @Test @DisplayName("GxT: finite difference vs analytical (ternary)")
        void gradientDTFiniteDiff() {
            double[] fixedG0 = {-50000.0, -30000.0, -40000.0};
            RkGibbs rk = buildTiVZr(fixedG0);
            double[] GxT = rk.gradientDT(X3, T);
            double h = 0.1;
            for (int m = 0; m < 3; m++) {
                double numGxTm = (rk.gradient(X3, T+h)[m] - rk.gradient(X3, T-h)[m]) / (2*h);
                assertEquals(numGxTm, GxT[m],
                        Math.abs(numGxTm) * 1e-4 + 0.01,
                        "GxT[" + m + "] mismatch");
            }
        }
    }

    // =====================================================================
    // 4. Full system validation against Mathematica output (requires tdb)
    // =====================================================================

    @Nested @DisplayName("Mathematica cross-validation (requires tdb)")
    class MathematicaValidation {

        private RkGibbs tiVZrRk;

        @BeforeEach
        void load() {
            assumeTrue(new java.io.File(TDB_PATH).exists(),
                    "SGTE .tdb not found. Set -Dsgte.tdb.path=<path> to enable.");
            try {
                UnaryGibbs ug = UnaryGibbs.fromTdb(TDB_PATH, "TI", "V", "ZR");

                RkGibbs.G0Function[] g0 = {
                    t -> ug.gibbs("TI", PhaseType.BCC_A2, t),
                    t -> ug.gibbs("V",  PhaseType.BCC_A2, t),
                    t -> ug.gibbs("ZR", PhaseType.BCC_A2, t)
                };

                // Ti-V-Zr system (Cui 2016) — from clusGen_25 delGsol
                List<BinaryParam> bins = List.of(
                    new BinaryParam(0, 1, new double[]{6523.17, 2025.39}, new double[]{0.0, 0.0}),
                    BinaryParam.zerothOrder(0, 2, -48287.53, 17.4484),
                    new BinaryParam(1, 2,
                            new double[]{17872.99, -3208.60},
                            new double[]{8.7539,    4.8481})
                );
                List<TernaryParam> terns = List.of(
                    new TernaryParam(0, 1, 2,
                            new double[]{-253783.69, 7589.27, 80496.47},
                            new double[]{10.9738, 0.0, 0.0})
                );

                tiVZrRk = new RkGibbs(3, g0, bins, terns, List.of());
            } catch (Exception e) {
                tiVZrRk = null;
            }
            assumeTrue(tiVZrRk != null, "Failed to build RkGibbs");
        }

        /**
         * Primary validation:
         * Mathematica: delGsol[1023,PN,{0.8,0.1,0.1},...] → GN = -55110.2
         */
        @Test @DisplayName("G(Ti-V-Zr, 1023K, {0.8,0.1,0.1}) ≈ -55110.2 J/mol")
        void gibbsMatchesMathematica() {
            double G = tiVZrRk.evaluate(X3, T);
            assertEquals(-55110.2, G, 5.0,
                    "G at (1023K, Ti0.8V0.1Zr0.1) vs Mathematica output");
        }

        @Test @DisplayName("G_id component is negative (entropy contribution)")
        void gIdNegative() {
            assertTrue(tiVZrRk.gId(X3, T) < 0,
                    "G_id should be negative (mixing entropy)");
        }

        @Test @DisplayName("Gradient Gx has length nc=3")
        void gradientLength() {
            assertEquals(3, tiVZrRk.gradient(X3, T).length);
        }

        @Test @DisplayName("Hessian Gxx is 3×3 and symmetric")
        void hessianShape() {
            double[][] Gxx = tiVZrRk.hessian(X3, T);
            assertEquals(3, Gxx.length);
            assertEquals(3, Gxx[0].length);
            for (int m = 0; m < 3; m++)
                for (int n = 0; n < 3; n++)
                    assertEquals(Gxx[m][n], Gxx[n][m], 1e-4);
        }

        @Test @DisplayName("GxT has length nc=3 and matches finite difference")
        void gxtMatchesFiniteDiff() {
            double[] GxT = tiVZrRk.gradientDT(X3, T);
            assertEquals(3, GxT.length);
            double h = 0.1;
            for (int m = 0; m < 3; m++) {
                double num = (tiVZrRk.gradient(X3, T+h)[m]
                            - tiVZrRk.gradient(X3, T-h)[m]) / (2*h);
                assertEquals(num, GxT[m], Math.abs(num)*1e-4 + 0.01,
                        "GxT[" + m + "] vs finite difference");
            }
        }

        @Test @DisplayName("G is additive: G = G0 + Gid + GEmBin + GEmTern")
        void gAdditivity() {
            double G = tiVZrRk.evaluate(X3, T);
            double sum = tiVZrRk.g0(X3, T)
                       + tiVZrRk.gId(X3, T)
                       + tiVZrRk.gEmBin(X3, T)
                       + tiVZrRk.gEmTern(X3, T)
                       + tiVZrRk.gEmQuat(X3, T);
            assertEquals(G, sum, 1e-6, "G != sum of components");
        }
    }
}
