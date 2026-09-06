package calc.equil.sundman;

import system.model.GibbsEnergyModel;
import system.model.cef.CefPhaseModelAdapter;

/**
 * One candidate phase's state during Algorithm A (Sundman, Dupin &amp;
 * Hallstedt, CALPHAD 75 (2021) 102330, Fig. 1).
 *
 * <p>Notation and convention (fixed for the whole {@code calc.equil.sundman}
 * package, per the M3 design report):
 * <ul>
 *   <li>{@code G_M}   — Gibbs energy per formula unit (Eq. 4).</li>
 *   <li>{@code M_A}   — Sundman Eq. (2): moles of component A per formula
 *       unit, Σ_s a[s]·y[s,A], summed only over element-mapped
 *       constituents. NOT normalized. Vacancies do not contribute.</li>
 *   <li>{@code x_A = M_A / Σ_A M_A} — mole fraction, derived only for
 *       reporting; never used in any equilibrium equation.</li>
 *   <li>No {@code nfu} rescaling appears anywhere in this package.</li>
 * </ul>
 *
 * <p>This class holds only per-phase state (constitution, amount, cached
 * derivatives for the current y). It does not decide phase-set membership
 * or run any iteration — that is {@link SundmanEquilibriumSolver}'s job.
 */
public final class SundmanPhase {

    /** The underlying CEF model (validated V–Zr thermodynamic evaluator). */
    public final GibbsEnergyModel model;

    /** Phase name, e.g. "BCC_A2", "V2ZR". */
    public final String name;

    /** Number of sublattices. */
    public final int ns;

    /** Total number of site fractions (flat length of y). */
    public final int nip;

    /** Number of system components (elements). */
    public final int nc;

    /** Site fractions y[m], length nip. Mutated in place during iteration. */
    public double[] y;

    /** Phase amount ℵ (moles of formula units). ℵ ≥ 0 for a stable phase. */
    public double amount;

    /** True if this phase is currently in the stable set. */
    public boolean stable;

    // ---- Cached quantities at the current y (refreshed by evaluate()) ----

    /** G_M(y, T), Gibbs energy per formula unit. */
    public double G;

    /** ∂G_M/∂y[m], length nip (raw site-fraction gradient, unprojected). */
    public double[] Gx;

    /** ∂²G_M/∂y[m]∂y[n], nip×nip (raw site-fraction Hessian). */
    public double[][] Gxx;

    /** M_A(y), length nc (Sundman Eq. 2, unnormalized). */
    public double[] mA;

    /** Driving force γ = G_M + Σ_A μ_A·M_A, evaluated at the current μ. */
    public double drivingForce;

    public SundmanPhase(GibbsEnergyModel model, double[] yInit, double amount, boolean stable) {
        this.model = model;
        this.name = model.phaseName();
        this.ns = model instanceof CefPhaseModelAdapter
                ? ((CefPhaseModelAdapter) model).getGibbs().ns() : 1;
        this.nip = model.numInternalParams();
        this.nc = model.numComponents();
        this.y = yInit.clone();
        this.amount = amount;
        this.stable = stable;
        this.mA = new double[nc];
    }

    /**
     * Recomputes G, Gx, Gxx, mA at the current y and T. Must be called
     * before this phase's contribution to Eq. (6) is assembled.
     */
    public void evaluate(double T) {
        model.setInternalVars(y);
        model.setComposition(model.compositionFromInternal(y));
        model.setTemperature(T);
        this.G = model.evaluateG(y, T);
        if (model instanceof CefPhaseModelAdapter) {
            CefPhaseModelAdapter cef = (CefPhaseModelAdapter) model;
            this.Gx = cef.getGibbs().gradient(y, T);
            this.Gxx = cef.getGibbs().hessian(y, T);
        } else {
            this.Gx = model.gradient(y, T);
            this.Gxx = model.hessian(y, T);
        }
        this.mA = computeM();
    }

    /** γ = G_M + Σ_A μ_A·M_A (Sundman Eq. 7 residual / driving force). */
    public double computeDrivingForce(double[] mu) {
        double sum = G;
        for (int A = 0; A < nc && A < mu.length; A++) {
            sum += mu[A] * mA[A];
        }
        this.drivingForce = sum;
        return sum;
    }

    /** x_A = M_A / Σ_A M_A — derived, for reporting only. */
    public double[] moleFractions() {
        double total = 0.0;
        for (double m : mA) total += m;
        double[] x = new double[nc];
        if (total > 0) {
            for (int A = 0; A < nc; A++) x[A] = mA[A] / total;
        }
        return x;
    }

    /**
     * Per-sublattice constraint residuals Σ_i y[s,i] − 1, length ns.
     * Should be ~0 for a valid constitution (Eq. 1).
     */
    public double[] sublatticeSumResiduals() {
        int[] offs = offsets();
        int[] ncSL = constituentsPerSublattice();
        double[] r = new double[ns];
        for (int s = 0; s < ns; s++) {
            double sum = 0.0;
            for (int i = 0; i < ncSL[s]; i++) sum += y[offs[s] + i];
            r[s] = sum - 1.0;
        }
        return r;
    }

    public int[] offsets() {
        return model instanceof CefPhaseModelAdapter
                ? ((CefPhaseModelAdapter) model).getGibbs().offsets() : new int[]{0};
    }

    public int[] constituentsPerSublattice() {
        return model instanceof CefPhaseModelAdapter
                ? ((CefPhaseModelAdapter) model).getGibbs().constituentsPerSublattice()
                : new int[]{nc};
    }

    public double[] stoichiometry() {
        return model instanceof CefPhaseModelAdapter
                ? ((CefPhaseModelAdapter) model).getGibbs().stoichiometry() : new double[]{1.0};
    }

    public int[][] elementMap() {
        if (model instanceof CefPhaseModelAdapter) {
            return ((CefPhaseModelAdapter) model).getElementIndexOnSublattice();
        }
        int[][] map = new int[1][nc];
        for (int i = 0; i < nc; i++) map[0][i] = i;
        return map;
    }

    private double[] computeM() {
        double[] composition = model.compositionFromInternal(y);
        double[] moles = new double[nc];
        double nfu = model.nfu();
        for (int i = 0; i < nc; i++) moles[i] = nfu * composition[i];
        return moles;
    }
}
