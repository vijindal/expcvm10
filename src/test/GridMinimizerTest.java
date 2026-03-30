package test;

import thermocalc.equil.GridMinimizer;
import thermocalc.equil.EquilibriumState;
import thermocalc.equil.PhaseRecord;
import domain.PhaseModelPort;
import domain.PhaseEquilData;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for GridMinimizer using synthetic PhaseModelPort stubs.
 *
 * Every stub is a pure function defined by a closed-form G(y, T) expression.
 * No TDB file, no RK class, no element names — the test is purely about
 * whether the hull algorithm finds the right phases and amounts.
 *
 * Run:
 *   javac -sourcepath src -d build/classes $(find src -name "*.java")
 *   java  -cp build/classes test.GridMinimizerTest
 *
 * ── Test cases ────────────────────────────────────────────────────────────
 *
 * BINARY (nc = 2) — two phases with known common-tangent compositions:
 *
 *   G_alpha(x) = 0.5·(x - 0.2)²           (parabola centred at x=0.2)
 *   G_beta (x) = 0.5·(x - 0.8)² - 0.1    (parabola centred at x=0.8, lower)
 *
 *   Common tangent: both phases stable between x≈0.20 and x≈0.80.
 *   At x=0.5:  alpha amount ≈ 0.5, beta amount ≈ 0.5.
 *   At x=0.1:  single-phase alpha.
 *   At x=0.9:  single-phase beta.
 *
 * TERNARY (nc = 3) — three phases, each a paraboloid centred at a corner:
 *
 *   G_A(y) = 0.5·[(y0-1)² + (y1-0)²]     (minimum at corner A: y=(1,0,0))
 *   G_B(y) = 0.5·[(y0-0)² + (y1-1)²]     (minimum at corner B: y=(0,1,0))
 *   G_C(y) = 0.5·[(y0-0)² + (y1-0)²] - 0.05   (minimum at corner C: y=(0,0,1))
 *
 *   At the centroid y=(1/3,1/3,1/3): all three phases compete.
 *   GridMinimizer should find 2-3 phases as stable.
 *   At y=(0.8,0.1,0.1): phase A should dominate.
 */
public class GridMinimizerTest {

    public static void main(String[] args) {
        System.out.println("=== GridMinimizerTest ===\n");
        int pass = 0, fail = 0;

        // ── Binary tests ──────────────────────────────────────────────────────
        PhaseModelPort alpha = parabolicPhase("alpha", 0.2,  0.0);
        PhaseModelPort beta  = parabolicPhase("beta",  0.8, -0.1);
        List<PhaseModelPort> binary = listOf(alpha, beta);
        GridMinimizer gm = new GridMinimizer(40);

        // Test 1: x=0.5 — should be two-phase alpha+beta
        EquilibriumState s1 = gm.initialize(binary, 1000, 1e5,
                                              new double[]{0.5, 0.5});
        pass += check("Binary x=0.5: two stable phases",
                      s1.stablePhases().size() == 2);
        pass += check("Binary x=0.5: amounts sum to 1",
                      Math.abs(amountSum(s1) - 1.0) < 0.02);
        pass += check("Binary x=0.5: lever rule OK",
                      leverError(s1, new double[]{0.5, 0.5}) < 0.05);

        // Test 2: x=0.1 — should be single-phase alpha
        EquilibriumState s2 = gm.initialize(binary, 1000, 1e5,
                                              new double[]{0.1, 0.9});
        pass += check("Binary x=0.1: single stable phase",
                      s2.stablePhases().size() == 1);
        pass += check("Binary x=0.1: stable phase is alpha",
                      s2.stablePhases().get(0).model.phaseName().equals("alpha"));

        // Test 3: x=0.9 — should be single-phase beta
        EquilibriumState s3 = gm.initialize(binary, 1000, 1e5,
                                              new double[]{0.9, 0.1});
        pass += check("Binary x=0.9: single stable phase",
                      s3.stablePhases().size() == 1);
        pass += check("Binary x=0.9: stable phase is beta",
                      s3.stablePhases().get(0).model.phaseName().equals("beta"));

        // Test 4: metastable count
        pass += check("Binary x=0.5: one metastable entry for each non-stable phase",
                      s1.stablePhases().size() + s1.metastablePhases().size() == 2);

        // ── Ternary tests ─────────────────────────────────────────────────────
        PhaseModelPort phA = cornerPhase("A", new double[]{1,0,0},  0.0);
        PhaseModelPort phB = cornerPhase("B", new double[]{0,1,0},  0.0);
        PhaseModelPort phC = cornerPhase("C", new double[]{0,0,1}, -0.05);
        List<PhaseModelPort> ternary = listOf(phA, phB, phC);

        // Test 5: corner A — should be single-phase A
        EquilibriumState t1 = gm.initialize(ternary, 1000, 1e5,
                                              new double[]{0.8, 0.1, 0.1});
        pass += check("Ternary near A: stable phase is A",
                      containsPhase(t1.stablePhases(), "A"));
        pass += check("Ternary near A: amounts sum to 1",
                      Math.abs(amountSum(t1) - 1.0) < 0.02);

        // Test 6: corner C — should be single-phase C (lower by 0.05)
        EquilibriumState t2 = gm.initialize(ternary, 1000, 1e5,
                                              new double[]{0.05, 0.05, 0.9});
        pass += check("Ternary near C: stable phase is C",
                      containsPhase(t2.stablePhases(), "C"));

        // Test 7: centroid — GridMinimizer should not crash and amounts sum to 1
        EquilibriumState t3 = gm.initialize(ternary, 1000, 1e5,
                                              new double[]{1.0/3, 1.0/3, 1.0/3});
        pass += check("Ternary centroid: amounts sum to 1",
                      Math.abs(amountSum(t3) - 1.0) < 0.02);
        pass += check("Ternary centroid: at least one stable phase",
                      !t3.stablePhases().isEmpty());

        // Test 8: lever rule holds at centroid
        pass += check("Ternary centroid: lever rule OK",
                      leverError(t3, new double[]{1.0/3, 1.0/3, 1.0/3}) < 0.05);

        // ── Density sensitivity ───────────────────────────────────────────────
        GridMinimizer coarse = new GridMinimizer(5);
        EquilibriumState sc = coarse.initialize(binary, 1000, 1e5,
                                                  new double[]{0.5, 0.5});
        pass += check("Binary coarse grid x=0.5: two stable phases",
                      sc.stablePhases().size() == 2);

        // ── nc=1 corner case ─────────────────────────────────────────────────
        PhaseModelPort pure = constantPhase("pure", -500.0);
        EquilibriumState p1 = gm.initialize(listOf(pure), 1000, 1e5,
                                              new double[]{1.0});
        pass += check("nc=1 pure substance: single stable phase",
                      p1.stablePhases().size() == 1);
        pass += check("nc=1 pure substance: amounts sum to 1",
                      Math.abs(amountSum(p1) - 1.0) < 0.02);

        System.out.printf("\n=== %d passed ===\n", pass);
    }

    private static int check(String label, boolean condition) {
        if (condition) {
            System.out.println("  PASS  " + label);
            return 1;
        } else {
            System.out.println("  FAIL  " + label);
            return 0;
        }
    }

    private static double amountSum(EquilibriumState s) {
        double sum = 0;
        for (PhaseRecord pr : s.stablePhases()) sum += pr.amount;
        return sum;
    }

    private static double leverError(EquilibriumState s, double[] x) {
        double[] recon = new double[x.length];
        for (PhaseRecord pr : s.stablePhases()) {
            double[] y = pr.x;
            for (int i = 0; i < x.length; i++) recon[i] += pr.amount * y[i];
        }
        double err = 0;
        for (int i = 0; i < x.length; i++) err = Math.max(err, Math.abs(recon[i] - x[i]));
        return err;
    }

    private static boolean containsPhase(List<PhaseRecord> list, String name) {
        for (PhaseRecord pr : list)
            if (pr.model.phaseName().equals(name)) return true;
        return false;
    }

    @SafeVarargs
    private static <T> List<T> listOf(T... items) {
        List<T> l = new ArrayList<>();
        for (T t : items) l.add(t);
        return l;
    }

    // =========================================================================
    //  Synthetic PhaseModelPort stubs
    // =========================================================================

    private static PhaseModelPort parabolicPhase(String name, double centre, double offset) {
        return new PhaseModelPort() {
            public String phaseName() { return name; }
            public String modelType() { return "RK"; }
            public int numComponents() { return 2; }
            public int numInternalParams() { return 2; }
            public double nfu() { return 1.0; }
            public double evaluateG(double[] y, double T) {
                double d = y[0] - centre;
                return 0.5 * d * d + offset;
            }
            public double[] gradient(double[] x, double T) { throw new UnsupportedOperationException(); }
            public double[][] hessian(double[] x, double T) { throw new UnsupportedOperationException(); }
            public double[] compositionFromInternal(double[] y) { return y.clone(); }
            public double[] getInitialInternalVars(double[] x) { return x.clone(); }
            public boolean isValid(double[] y) { return true; }
            public PhaseEquilData compute(double T, double P, double[] y, double dT, double dP, double[] mu) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static PhaseModelPort cornerPhase(String name, double[] centre, double offset) {
        return new PhaseModelPort() {
            public String phaseName() { return name; }
            public String modelType() { return "RK"; }
            public int numComponents() { return 3; }
            public int numInternalParams() { return 3; }
            public double nfu() { return 1.0; }
            public double evaluateG(double[] y, double T) {
                double s = 0;
                for (int i = 0; i < y.length; i++) { double d = y[i]-centre[i]; s+=d*d; }
                return 0.5 * s + offset;
            }
            public double[] gradient(double[] x, double T) { throw new UnsupportedOperationException(); }
            public double[][] hessian(double[] x, double T) { throw new UnsupportedOperationException(); }
            public double[] compositionFromInternal(double[] y) { return y.clone(); }
            public double[] getInitialInternalVars(double[] x) { return x.clone(); }
            public boolean isValid(double[] y) { return true; }
            public PhaseEquilData compute(double T, double P, double[] y, double dT, double dP, double[] mu) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static PhaseModelPort constantPhase(String name, double g) {
        return new PhaseModelPort() {
            public String phaseName() { return name; }
            public String modelType() { return "RK"; }
            public int numComponents() { return 1; }
            public int numInternalParams() { return 1; }
            public double nfu() { return 1.0; }
            public double evaluateG(double[] y, double T) { return g; }
            public double[] gradient(double[] x, double T) { throw new UnsupportedOperationException(); }
            public double[][] hessian(double[] x, double T) { throw new UnsupportedOperationException(); }
            public double[] compositionFromInternal(double[] y) { return y.clone(); }
            public double[] getInitialInternalVars(double[] x) { return x.clone(); }
            public boolean isValid(double[] y) { return true; }
            public PhaseEquilData compute(double T, double P, double[] y,
                                           double dT, double dP, double[] mu) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
