package thermocalc.equil;

import domain.PhaseModelPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Initial estimate for multi-phase equilibrium via grid search.
 *
 * <p>For binary systems (nc=2): evaluates G(x) on a composition grid for each
 * candidate phase, finds the convex hull / common tangent construction to
 * determine the initial stable phase set, compositions, and amounts.
 *
 * <p>For systems with more components, falls back to a simpler approach:
 * evaluate G at the overall composition for each phase and pick the lowest.
 */
public class GridMinimizer {

    /** Number of grid points for binary search. */
    private static final int GRID_POINTS = 201;

    /** Small offset to avoid ln(0) at pure-component endpoints. */
    private static final double X_MIN = 1e-6;
    private static final double X_MAX = 1.0 - 1e-6;

    /**
     * Find initial equilibrium estimate for a binary system.
     *
     * @param candidates   all candidate phase models
     * @param T            temperature
     * @param compOverAll  overall composition (mole fractions), length nc
     * @return initial EquilibriumState with estimated stable set
     */
    public EquilibriumState initialize(List<PhaseModelPort> candidates,
                                       double T, double P,
                                       double[] compOverAll) {
        int nc = compOverAll.length;

        if (nc == 2) {
            return initializeBinary(candidates, T, P, compOverAll);
        } else {
            return initializeSimple(candidates, T, P, compOverAll);
        }
    }

    // ------------------------------------------------------------------
    // Binary: grid + common tangent
    // ------------------------------------------------------------------

    private EquilibriumState initializeBinary(List<PhaseModelPort> candidates,
                                              double T, double P,
                                              double[] compOverAll) {
        int nPhase = candidates.size();

        // Evaluate G on grid for each phase
        double[] xGrid = new double[GRID_POINTS];
        double[][] gGrid = new double[nPhase][GRID_POINTS]; // G for each phase at each grid point
        for (int ig = 0; ig < GRID_POINTS; ig++) {
            xGrid[ig] = X_MIN + (X_MAX - X_MIN) * ig / (GRID_POINTS - 1);
        }

        for (int ip = 0; ip < nPhase; ip++) {
            for (int ig = 0; ig < GRID_POINTS; ig++) {
                double[] x = {xGrid[ig], 1.0 - xGrid[ig]};
                gGrid[ip][ig] = candidates.get(ip).evaluateG(x, T);
            }
        }

        // Find global minimum G at each grid point (convex hull lower envelope)
        int[] bestPhase = new int[GRID_POINTS];
        double[] bestG = new double[GRID_POINTS];
        for (int ig = 0; ig < GRID_POINTS; ig++) {
            bestPhase[ig] = 0;
            bestG[ig] = gGrid[0][ig];
            for (int ip = 1; ip < nPhase; ip++) {
                if (gGrid[ip][ig] < bestG[ig]) {
                    bestG[ig] = gGrid[ip][ig];
                    bestPhase[ig] = ip;
                }
            }
        }

        // Detect two-phase regions by looking for common tangents
        // between all pairs of phases
        TwoPhaseEstimate best2p = null;
        for (int ip = 0; ip < nPhase; ip++) {
            for (int jp = ip + 1; jp < nPhase; jp++) {
                TwoPhaseEstimate est = findCommonTangent(
                        candidates.get(ip), candidates.get(jp),
                        ip, jp, xGrid, gGrid[ip], gGrid[jp], T);
                if (est != null) {
                    // Check if overall composition falls in this two-phase region
                    double x0 = compOverAll[0];
                    if (x0 >= Math.min(est.x1, est.x2) &&
                        x0 <= Math.max(est.x1, est.x2)) {
                        if (best2p == null || est.tangentG(x0) < singlePhaseG(candidates, x0, T)) {
                            best2p = est;
                        }
                    }
                }
            }
        }

        List<PhaseRecord> records = new ArrayList<>();

        if (best2p != null) {
            // Two-phase initial estimate
            double x0 = compOverAll[0];
            // Lever rule: f1*(x1) + f2*(x2) = x0, f1+f2=1
            double f1 = (best2p.x2 - x0) / (best2p.x2 - best2p.x1);
            double f2 = 1.0 - f1;

            double[] x1 = {best2p.x1, 1.0 - best2p.x1};
            double[] x2 = {best2p.x2, 1.0 - best2p.x2};

            records.add(new PhaseRecord(candidates.get(best2p.phase1), x1, f1, true));
            records.add(new PhaseRecord(candidates.get(best2p.phase2), x2, f2, true));

            // Add remaining phases as metastable
            for (int ip = 0; ip < nPhase; ip++) {
                if (ip != best2p.phase1 && ip != best2p.phase2) {
                    records.add(new PhaseRecord(candidates.get(ip),
                            compOverAll.clone(), 0.0, false));
                }
            }
        } else {
            // Single-phase: pick the lowest G at overall composition
            int bestIdx = 0;
            double bestGval = candidates.get(0).evaluateG(compOverAll, T);
            for (int ip = 1; ip < nPhase; ip++) {
                double gVal = candidates.get(ip).evaluateG(compOverAll, T);
                if (gVal < bestGval) {
                    bestGval = gVal;
                    bestIdx = ip;
                }
            }

            records.add(new PhaseRecord(candidates.get(bestIdx),
                    compOverAll.clone(), 1.0, true));
            for (int ip = 0; ip < nPhase; ip++) {
                if (ip != bestIdx) {
                    records.add(new PhaseRecord(candidates.get(ip),
                            compOverAll.clone(), 0.0, false));
                }
            }
        }

        return new EquilibriumState(T, P, compOverAll, records);
    }

    // ------------------------------------------------------------------
    // Fallback: pick lowest G at overall composition
    // ------------------------------------------------------------------

    private EquilibriumState initializeSimple(List<PhaseModelPort> candidates,
                                              double T, double P,
                                              double[] compOverAll) {
        int bestIdx = 0;
        double bestG = candidates.get(0).evaluateG(compOverAll, T);
        for (int ip = 1; ip < candidates.size(); ip++) {
            double gVal = candidates.get(ip).evaluateG(compOverAll, T);
            if (gVal < bestG) {
                bestG = gVal;
                bestIdx = ip;
            }
        }

        List<PhaseRecord> records = new ArrayList<>();
        records.add(new PhaseRecord(candidates.get(bestIdx),
                compOverAll.clone(), 1.0, true));
        for (int ip = 0; ip < candidates.size(); ip++) {
            if (ip != bestIdx) {
                records.add(new PhaseRecord(candidates.get(ip),
                        compOverAll.clone(), 0.0, false));
            }
        }

        return new EquilibriumState(T, P, compOverAll, records);
    }

    // ------------------------------------------------------------------
    // Common tangent search
    // ------------------------------------------------------------------

    /**
     * Find approximate common tangent between two phases on a grid.
     * Returns null if no valid common tangent is found.
     */
    private TwoPhaseEstimate findCommonTangent(
            PhaseModelPort model1, PhaseModelPort model2,
            int phase1, int phase2,
            double[] xGrid, double[] g1, double[] g2, double T) {

        int n = xGrid.length;
        TwoPhaseEstimate best = null;
        double bestResidual = Double.MAX_VALUE;

        // Try all pairs of grid points
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                // Tangent line from (xGrid[i], g1[i]) to (xGrid[j], g2[j])
                double x1 = xGrid[i], x2 = xGrid[j];
                double G1 = g1[i], G2 = g2[j];
                double slope = (G2 - G1) / (x2 - x1);

                // Check tangency: dG/dx at x1 for phase1 ≈ slope,
                //                  dG/dx at x2 for phase2 ≈ slope
                double[] comp1 = {x1, 1.0 - x1};
                double[] comp2 = {x2, 1.0 - x2};
                double[] grad1 = model1.gradient(comp1, T);
                double[] grad2 = model2.gradient(comp2, T);

                // For binary, the effective slope is dG/dx₁
                // = grad[0] - grad[1] (chain rule with x₂ = 1-x₁)
                double slope1 = grad1[0] - grad1[1];
                double slope2 = grad2[0] - grad2[1];

                double residual = Math.abs(slope1 - slope) + Math.abs(slope2 - slope);
                if (residual < bestResidual) {
                    bestResidual = residual;
                    best = new TwoPhaseEstimate(phase1, phase2, x1, x2, G1, G2);
                }
            }
        }

        // Accept if tangent residual is below threshold.
        // On a 201-point grid the nearest grid points to the true common tangent
        // can be ~0.005 away in composition; with typical G curvatures of
        // ~R*T/x(1-x), the slope mismatch is ~R*T*0.005/x(1-x) which can reach
        // 5 000–20 000 J/mol. Threshold = 1e5 accepts genuine tangents while
        // rejecting non-intersecting phase pairs (residual >> 1e5).
        if (best != null && bestResidual < 1e5) {
            return best;
        }
        return null;
    }

    /** Evaluate lowest single-phase G at composition x0. */
    private double singlePhaseG(List<PhaseModelPort> candidates, double x0, double T) {
        double[] comp = {x0, 1.0 - x0};
        double minG = Double.MAX_VALUE;
        for (PhaseModelPort m : candidates) {
            double g = m.evaluateG(comp, T);
            if (g < minG) minG = g;
        }
        return minG;
    }

    // ------------------------------------------------------------------
    // Internal data class
    // ------------------------------------------------------------------

    private static class TwoPhaseEstimate {
        final int phase1, phase2;
        final double x1, x2;   // compositions (first component mole fraction)
        final double G1, G2;   // Gibbs energies at x1, x2

        TwoPhaseEstimate(int phase1, int phase2,
                         double x1, double x2, double G1, double G2) {
            this.phase1 = phase1;
            this.phase2 = phase2;
            this.x1 = x1;
            this.x2 = x2;
            this.G1 = G1;
            this.G2 = G2;
        }

        /** G on the tangent line at composition x0. */
        double tangentG(double x0) {
            return G1 + (G2 - G1) / (x2 - x1) * (x0 - x1);
        }
    }
}
