package calc.equil.sundman;

import system.ports.EquilibriumResult;
import system.ports.EquilibriumResult.PhaseResult;
import system.model.cef.CefPhaseModelAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Fresh implementation of Algorithm A (Sundman, Dupin &amp; Hallstedt,
 * CALPHAD 75 (2021) 102330, Fig. 1) for a single multi-component
 * equilibrium at fixed T, P, and overall composition Ñ_A.
 *
 * <p>This is a single flat loop, matching Fig. 1 exactly:
 * <pre>
 *   solve eq.(6) for the current stable set
 *   -&gt; evaluate driving forces gamma^phi for ALL candidate phases
 *   -&gt; if any gamma^phi &gt; 0 (metastable) or Aleph^alpha &lt; 0 (stable):
 *        change the stable set and go back to "solve eq.(6)" (no
 *        convergence check this iteration)
 *   -&gt; else test |Delta mu_A| &lt; eps and |Delta y_is| &lt; eps
 *   -&gt; converged, or +iter and repeat, or failed if iter &gt; max
 * </pre>
 * There is no separate single-phase code path and no nested outer
 * "phase-set" / inner "Newton" loop: a one-phase system is simply the
 * p=1 case of the same loop, and phase-set changes take effect
 * immediately within the same iteration sequence, not after an inner
 * loop has already reported convergence.
 *
 * <h2>Convention (fixed for this class, see SundmanPhase)</h2>
 * G is per formula unit; M_A is Sundman's unnormalized Eq.(2) quantity;
 * x_A = M_A/ΣM_A is derived only when building the result. No nfu
 * rescaling appears anywhere in the equilibrium equations.
 *
 * <h2>Phase-set rule (Sundman, stated directly in the paper's prose)</h2>
 * A positive driving force for a metastable phase means it is added to
 * the stable set. A negative amount for a stable phase, AT AN ITERATION
 * (i.e. after accepting the raw Newton correction), means it is removed.
 * This solver applies that rule literally: it does not pre-emptively
 * reject or feasibility-clamp a step to avoid negative amounts. An
 * optional damping factor may shrink the step for numerical robustness
 * (see {@link #dampingFactor}), but it never suppresses the add/remove
 * decision itself — the decision is always made from the actual
 * post-step state.
 */
public final class SundmanEquilibriumSolver {

    /** Δμ_A convergence tolerance (Fig. 1: "Δμ_A < ε"). Implementation choice of magnitude; not specified numerically by the paper. */
    public static final double MU_TOL = 1e-6;

    /** Δy_is convergence tolerance (Fig. 1: "Δy_is < ε"). */
    public static final double Y_TOL = 1e-8;

    /** Driving-force threshold for adding a metastable phase (γ^φ > 0, per Sundman's stated rule; a small positive tolerance avoids adding on numerical noise). */
    public static final double DRIVING_FORCE_TOL = 1e-6;

    /** Maximum iterations before reporting failure (Fig. 1: "iter > max?"). */
    public static final int MAX_ITERATIONS = 200;

    /** Lower bound for a site fraction used by the step-length limiter (numerical safeguard). */
    private static final double Y_LOWER = 1e-12;

    /** Upper bound for a site fraction used by the step-length limiter (numerical safeguard). */
    private static final double Y_UPPER = 1.0;

    /** Floor on the damped step length, so progress is never reduced to exactly zero. */
    private static final double LAMBDA_MIN = 1e-10;

    /**
     * A remaining margin to a site-fraction bound smaller than this is
     * treated as "already at the bound" by the step-length limiter, so a
     * constituent pinned at 0 or 1 (e.g. the sole constituent of a
     * single-species sublattice) is not limited by floating-point noise
     * in its own (mathematically zero) step component.
     */
    private static final double Y_BOUND_SLACK = 1e-9;

    /**
     * A phase amount step is limited to at most this many multiples of
     * the total system size (Sum_A |Ntotal_A|). Implementation choice: a
     * generous bound (amounts an order of magnitude larger than the whole
     * system are already unphysical for a mass-balance-respecting
     * solution) that only intervenes for a clearly invalid linearization,
     * not for normal Newton steps.
     */
    private static final double MAX_AMOUNT_GROWTH_FACTOR = 100.0;

    /**
     * Optional damping factor applied to the raw Newton correction before
     * it is accepted, purely as a numerical-robustness safeguard against
     * an oversized first step (NOT part of Sundman's Algorithm A). Set to
     * 1.0 to take the raw, undamped step exactly as the paper's rule
     * implies (accept, then remove any phase whose resulting amount is
     * negative). This field is deliberately public and explicit so it
     * cannot be confused with, or silently substitute for, the
     * phase-set rule itself.
     */
    public double dampingFactor = 1.0;

    /** max|Δμ_A| from the final (converged or last-attempted) iteration. Diagnostic only. */
    public double lastMaxDeltaMu = Double.NaN;

    /** max|Δy_is| from the final (converged or last-attempted) iteration. Diagnostic only. */
    public double lastMaxDeltaY = Double.NaN;

    /**
     * Solves for a single equilibrium at fixed T, P and overall
     * composition, starting from the given initial phase set (typically
     * produced by a separate global/grid initial-estimate step — this
     * solver has no knowledge of how the initial estimate was obtained).
     *
     * @param T        temperature, K
     * @param P        pressure, Pa
     * @param Ntotal   prescribed total moles per component, Ñ_A
     * @param initial  initial candidate phases (stable and metastable),
     *                 each already carrying its starting y and amount
     * @return the equilibrium result
     */
    public EquilibriumResult solve(double T, double P, double[] Ntotal, List<SundmanPhase> initial) {
        SundmanEquilibriumState state = new SundmanEquilibriumState(T, P, Ntotal, initial);
        int nc = Ntotal.length;

        int iter = 0;
        boolean converged = false;

        while (true) {
            List<SundmanPhase> stable = state.stablePhases();
            int np = stable.size();

            if (np == 0) {
                break;
            }

            // ---- evaluate all stable phases at current y, T ----
            for (SundmanPhase p : stable) p.evaluate(T);

            // ---- build per-phase steps ("solve eq.(6)" per-phase reduction) ----
            List<SundmanPhaseStep> steps = new ArrayList<>();
            boolean anySingular = false;
            for (SundmanPhase p : stable) {
                int[][] elMap = elementMap(p);
                SundmanPhaseStep step = SundmanPhaseStep.build(p, elMap, state.mu);
                steps.add(step);
                if (step.singular) anySingular = true;
            }

            if (anySingular) {
                // No valid per-phase step exists for at least one stable
                // phase this iteration -- a singular linear system means
                // no Newton correction can be trusted; this is a failed
                // iteration, not a zero-correction pass.
                iter++;
                if (iter > MAX_ITERATIONS) { break; }
                continue;
            }

            // ---- assemble and solve the global Eq.(6) system ----
            SundmanGlobalSystem sys = SundmanGlobalSystem.solve(stable, steps, state.mu, Ntotal);
            if (sys.singular) {
                iter++;
                if (iter > MAX_ITERATIONS) { break; }
                continue;
            }

            double[] deltaMu = new double[nc];
            System.arraycopy(sys.delta, 0, deltaMu, 0, nc);
            double[] deltaAleph = new double[np];
            System.arraycopy(sys.delta, nc, deltaAleph, 0, np);

            // ---- Δy for every stable phase (full Newton step) ----
            double[][] dyAll = new double[np][];
            for (int a = 0; a < np; a++) {
                SundmanPhase p = stable.get(a);
                int[][] elMap = elementMap(p);
                dyAll[a] = steps.get(a).deltaY(deltaMu, elMap,
                        p.offsets(), p.constituentsPerSublattice());
            }

            // ---- step-length limiter (NUMERICAL SAFEGUARD ONLY) ----
            // The frozen-Hessian linearization is only valid locally; a raw
            // Newton step can drive site fractions far outside [0,1], where
            // G and its derivatives are meaningless. lambda is reduced just
            // enough to keep every y_m within the physical domain (with a
            // safety factor), which is a restriction on STEP LENGTH ONLY.
            //
            // This does NOT suppress Sundman's phase-set rule: the
            // add/remove decisions below are still taken from the actual
            // post-step state, a negative phase amount is still allowed to
            // occur and still triggers removal, and no trial is ever
            // rejected on feasibility grounds.
            double lambda = dampingFactor;
            for (int a = 0; a < np; a++) {
                SundmanPhase p = stable.get(a);
                for (int m = 0; m < p.nip; m++) {
                    double d = dyAll[a][m];
                    // A constituent already at (or within numerical noise
                    // of) the bound it is moving toward has no room left
                    // to limit -- e.g. the sole constituent of a
                    // single-species sublattice (such as a vacancy
                    // sublattice) sits exactly at y=1 and can never
                    // legitimately move further in the positive
                    // direction. Without this guard, floating-point noise
                    // in dy at such an index (which is mathematically
                    // supposed to be exactly zero, since that constituent
                    // has nowhere to go) would divide by a near-zero
                    // remaining margin and collapse lambda to zero for
                    // every phase, even when the real step is fine.
                    if (d > 0.0 && Y_UPPER - p.y[m] > Y_BOUND_SLACK) {
                        double allowed = 0.9 * (Y_UPPER - p.y[m]) / d;
                        if (allowed < lambda) lambda = allowed;
                    } else if (d < 0.0 && p.y[m] - Y_LOWER > Y_BOUND_SLACK) {
                        double allowed = 0.9 * (p.y[m] - Y_LOWER) / (-d);
                        if (allowed < lambda) lambda = allowed;
                    }
                }
            }

            // ---- amount-growth limiter (NUMERICAL SAFEGUARD ONLY) ----
            // Same rationale as the y-bound limiter above: a phase amount
            // cannot physically exceed what is needed to account for the
            // total system size (Sum_A Ntotal_A), since M_A values are
            // O(1). A raw Newton step taken far from the solution can
            // occasionally produce an amount growing by many orders of
            // magnitude in one step (observed: amount ~3e10 against
            // Ntotal ~1) with a well-conditioned, non-singular system --
            // the condition-number/residual checks in SundmanGlobalSystem
            // catch a bad LINEAR SOLVE, not a step that is simply too
            // large for the frozen-Hessian linearization to remain valid.
            // This bounds STEP LENGTH only, exactly like the y-limiter: it
            // does not reject the step, does not prevent a negative
            // amount from occurring, and does not alter the add/remove
            // decision, which is still taken from the actual post-step
            // amount.
            double totalN = 0.0;
            for (double v : Ntotal) totalN += Math.abs(v);
            double amountScale = Math.max(totalN, 1.0) * MAX_AMOUNT_GROWTH_FACTOR;
            for (int a = 0; a < np; a++) {
                double d = deltaAleph[a];
                if (Math.abs(d) <= amountScale) continue;
                double allowed = amountScale / Math.abs(d);
                if (allowed < lambda) lambda = allowed;
            }

            if (lambda < LAMBDA_MIN) lambda = LAMBDA_MIN;

            double[] newMu = new double[nc];
            for (int B = 0; B < nc; B++) newMu[B] = state.mu[B] + lambda * deltaMu[B];

            double[] newAmount = new double[np];
            double[][] newY = new double[np][];
            double maxDeltaY = 0.0;
            for (int a = 0; a < np; a++) {
                SundmanPhase p = stable.get(a);
                newAmount[a] = p.amount + lambda * deltaAleph[a];
                newY[a] = new double[p.nip];
                for (int m = 0; m < p.nip; m++) {
                    newY[a][m] = p.y[m] + lambda * dyAll[a][m];
                    maxDeltaY = Math.max(maxDeltaY, Math.abs(lambda * dyAll[a][m]));
                }
            }

            // ---- apply the update ----
            state.mu = newMu;
            for (int a = 0; a < np; a++) {
                stable.get(a).y = newY[a];
                stable.get(a).amount = newAmount[a];
            }
            // Refresh evaluated quantities (G, mA) at the new y for the
            // phase-set decision immediately below -- Fig. 1 evaluates
            // driving forces on the just-updated state, every iteration,
            // for every candidate phase (no "settled" gate of any kind).
            for (SundmanPhase p : state.phases) p.evaluate(T);
            for (SundmanPhase p : state.phases) p.computeDrivingForce(state.mu);

            // ---- Sundman's phase-set rule (literal, unconditional) ----
            boolean phaseSetChanged = false;
            for (SundmanPhase p : new ArrayList<>(stable)) {
                if (p.amount < 0.0) {
                    p.stable = false;
                    phaseSetChanged = true;
                }
            }
            // Phase addition, subject to the Gibbs phase-rule bound.
            //
            // At fixed T and P, Sundman Eq. (8) f = n + 2 - p - c gives
            // f = n - p with c = 2 (both T and P are fixed conditions, and
            // neither is an axis variable). So at most p = n = nc phases
            // can be simultaneously stable. Exceeding this makes the
            // Gibbs-Duhem block structurally rank-deficient (np rows with
            // nonzeros in only nc columns), leaving the system unsolvable
            // and preventing the negative-amount rule from ever pruning
            // the set.
            //
            // When more candidates have gamma > 0 than can be admitted,
            // the current stable phases are retained and the remaining
            // slots are filled deterministically by largest driving force
            // first. The normal Sundman negative-amount mechanism still
            // removes an unstable phase on a later iteration, so this only
            // limits how many phases enter at once -- it does not change
            // the driving-force definition or the removal rule.
            int maxStable = nc;
            int freeSlots = maxStable - state.numStable();
            if (freeSlots > 0) {
                List<SundmanPhase> candidates = new ArrayList<>();
                for (SundmanPhase p : state.metastablePhases()) {
                    if (p.drivingForce > DRIVING_FORCE_TOL) candidates.add(p);
                }
                candidates.sort((u, v) -> Double.compare(v.drivingForce, u.drivingForce));
                int added = 0;
                for (SundmanPhase p : candidates) {
                    if (added >= freeSlots) break;
                    p.stable = true;
                    p.amount = 0.0;
                    phaseSetChanged = true;
                    added++;
                }
            }

            if (phaseSetChanged) {
                // Fig. 1: "change set of stable phases" -> loop back to
                // "solve eq.(6)" immediately; no convergence test this
                // iteration.
                iter++;
                if (iter > MAX_ITERATIONS) { break; }
                continue;
            }

            // ---- convergence test (Fig. 1: Delta mu_A < eps, Delta y_is < eps) ----
            double maxDeltaMu = 0.0;
            for (int B = 0; B < nc; B++) maxDeltaMu = Math.max(maxDeltaMu, Math.abs(lambda * deltaMu[B]));
            lastMaxDeltaMu = maxDeltaMu;
            lastMaxDeltaY = maxDeltaY;

            if (maxDeltaMu < MU_TOL && maxDeltaY < Y_TOL) {
                converged = true;
                break;
            }

            iter++;
            if (iter > MAX_ITERATIONS) { break; }
        }

        return buildResult(state, converged, iter);
    }

    private int[][] elementMap(SundmanPhase p) {
        return p.elementMap();
    }

    private EquilibriumResult buildResult(SundmanEquilibriumState state, boolean converged, int iterations) {
        List<PhaseResult> stableResults = new ArrayList<>();
        for (SundmanPhase p : state.stablePhases()) {
            p.computeDrivingForce(state.mu);
            stableResults.add(new PhaseResult(p.name, p.model.modelType(),
                    p.amount, p.moleFractions(), p.y, p.G, p.drivingForce));
        }
        List<PhaseResult> metaResults = new ArrayList<>();
        for (SundmanPhase p : state.metastablePhases()) {
            p.evaluate(state.T);
            p.computeDrivingForce(state.mu);
            metaResults.add(new PhaseResult(p.name, p.model.modelType(),
                    0.0, p.moleFractions(), p.y, p.G, p.drivingForce));
        }
        return new EquilibriumResult(state.T, state.P, state.mu, stableResults, metaResults, converged, iterations);
    }
}
