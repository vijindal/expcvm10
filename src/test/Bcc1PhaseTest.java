package test;

import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;
import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stage 1 validation: BCC_A2 single-phase CEF equilibrium in the V-Zr system.
 * Only BCC_A2 is offered as a candidate so the equilibrium solver has no
 * phase-set choice to make -- isolates the single-phase Newton/tangent-plane
 * path from grid-minimizer phase selection.
 */
public class Bcc1PhaseTest {
    public static void main(String[] args) throws Exception {
        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phases = Arrays.asList("BCC_A2");

        @SuppressWarnings("unchecked")
        List<PhaseModel> models = (List<PhaseModel>) parser.buildPhaseModels(elements, phases);
        PhaseModel bcc = models.get(0);
        GibbsEnergyModel gm = PhaseModelFactory.toGibbsModel(bcc, elements);

        double T = 1200.0;
        double P = 101325.0;
        double xV = 0.80, xZr = 0.20;

        System.out.println("=== Stage 1: BCC_A2 single-phase, T=" + T + "K, x_V=" + xV + " x_Zr=" + xZr + " ===");

        double[] y0 = gm.getInitialInternalVars(new double[]{xV, xZr});
        System.out.println("Initial y (site fractions) = " + Arrays.toString(y0));
        double[] x0 = gm.compositionFromInternal(y0);
        System.out.println("Round-trip x from y0        = " + Arrays.toString(x0));

        List<GibbsEnergyModel> candidates = new ArrayList<>();
        candidates.add(gm);

        calc.equil.EquilibriumSolver solver = new calc.equil.EquilibriumSolver();
        EquilibriumResult res = solver.solve(T, P, new double[]{xV, xZr}, candidates);

        System.out.println("Converged = " + res.isConverged() + "  iterations = " + res.getIterations());
        double[] mu = res.getMu();
        System.out.println("mu[V] = " + mu[0] + "  mu[Zr] = " + mu[1]);
        for (var pr : res.getStablePhases()) {
            System.out.println("Phase " + pr.phaseName + " amount=" + pr.amount
                    + " x=" + Arrays.toString(pr.x) + " y=" + Arrays.toString(pr.y)
                    + " G=" + pr.G + " drivingForce=" + pr.drivingForce);
        }

        // Residual check: recompute G, gradient at the converged y and verify
        // mu_A = -G - dG/dx_A + sum_B x_B dG/dx_B  (tangent-plane condition, current code formula)
        var stable = res.getStablePhases().get(0);
        double[] gx = gm.gradient(stable.x, T);
        double gVal = gm.evaluateG(stable.x, T);
        double nfu = gm.nfu();
        double gTilde = gVal / nfu;
        System.out.println("G=" + gVal + " nfu=" + nfu + " Gtilde=" + gTilde);
        System.out.println("gx (dG/dx, per atom) = " + Arrays.toString(gx));

        double sumXGx = 0;
        for (int i = 0; i < 2; i++) sumXGx += stable.x[i] * gx[i];

        double[] muCodeFormula = new double[2];
        for (int i = 0; i < 2; i++) muCodeFormula[i] = -(gTilde) - gx[i] + sumXGx / nfu;
        System.out.println("Current-code mu formula : " + Arrays.toString(muCodeFormula));

        double[] muCorrect = new double[2];
        for (int i = 0; i < 2; i++) muCorrect[i] = gTilde + gx[i] - sumXGx;
        System.out.println("Corrected mu formula     : " + Arrays.toString(muCorrect));

        System.out.println("Euler check: sum(x*mu) should equal Gtilde=" + gTilde);
        double sumXMuCode = stable.x[0]*muCodeFormula[0] + stable.x[1]*muCodeFormula[1];
        double sumXMuCorrect = stable.x[0]*muCorrect[0] + stable.x[1]*muCorrect[1];
        System.out.println("  sum(x*muCodeFormula) = " + sumXMuCode);
        System.out.println("  sum(x*muCorrect)     = " + sumXMuCorrect);

        double dfCode = gTilde;
        for (int i=0;i<2;i++) dfCode += stable.x[i]*muCodeFormula[i];
        double dfCorrect = gTilde;
        for (int i=0;i<2;i++) dfCorrect += stable.x[i]*muCorrect[i];
        System.out.println("Driving force with code formula (should be 0): " + (gTilde - sumXMuCode));
        System.out.println("Driving force with corrected formula (should be 0): " + (gTilde - sumXMuCorrect));
    }
}
