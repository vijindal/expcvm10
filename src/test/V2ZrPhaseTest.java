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
 * Stage 2 validation: V2ZR single-phase CEF equilibrium in the V-Zr system.
 * Only V2ZR is offered as a candidate. V2ZR has two (V,Zr) sublattices with
 * stoichiometry (V,Zr)2(V,Zr)1 -- no vacancy sublattice, so its
 * compositionFromInternal mapping is the identity case (both sublattices'
 * constituent lists equal the element list) and should already be correct
 * even without the constituent-name map fix needed for BCC_A2/HCP_A3.
 */
public class V2ZrPhaseTest {
    public static void main(String[] args) throws Exception {
        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phases = Arrays.asList("V2ZR");

        @SuppressWarnings("unchecked")
        List<PhaseModel> models = (List<PhaseModel>) parser.buildPhaseModels(elements, phases);
        PhaseModel v2zr = models.get(0);
        GibbsEnergyModel gm = PhaseModelFactory.toGibbsModel(v2zr, elements);

        System.out.println("V2ZR structure: ns=" + v2zr.gibbs.ns() + " nip=" + v2zr.gibbs.nip()
                + " a=" + Arrays.toString(v2zr.gibbs.stoichiometry())
                + " nc=" + Arrays.toString(v2zr.gibbs.constituentsPerSublattice()));

        // V2Zr stoichiometric composition is x_V = 2/3, x_Zr = 1/3.
        // Test a composition inside the homogeneity range, close to but not
        // exactly at the stoichiometric point.
        double T = 1200.0;
        double P = 101325.0;
        double xV = 0.60, xZr = 0.40;

        System.out.println("\n=== Stage 2: V2ZR single-phase, T=" + T + "K, x_V=" + xV + " x_Zr=" + xZr + " ===");

        double[] y0 = gm.getInitialInternalVars(new double[]{xV, xZr});
        System.out.println("Initial y (site fractions) = " + Arrays.toString(y0));
        double[] x0 = gm.compositionFromInternal(y0);
        System.out.println("Round-trip x from y0        = " + Arrays.toString(x0));

        // Asymmetric internal check: verify compositionFromInternal correctly
        // weights sublattice 0 (a=2) vs sublattice 1 (a=1) rather than
        // assuming a naive average.
        double[] yAsym = {0.9, 0.1, 0.2, 0.8}; // sl0: 90% V, sl1: 80% Zr
        double[] xAsym = gm.compositionFromInternal(yAsym);
        double xVExpected = (2.0 * 0.9 + 1.0 * 0.2) / 3.0;
        System.out.println("Asymmetric y=" + Arrays.toString(yAsym)
                + " -> x=" + Arrays.toString(xAsym)
                + "  expected x_V=" + xVExpected);

        List<GibbsEnergyModel> candidates = new ArrayList<>();
        candidates.add(gm);

        calc.equil.EquilibriumSolver solver = new calc.equil.EquilibriumSolver();
        EquilibriumResult res = solver.solve(T, P, new double[]{xV, xZr}, candidates);

        System.out.println("\nConverged = " + res.isConverged() + "  iterations = " + res.getIterations());
        double[] mu = res.getMu();
        System.out.println("mu[V] = " + mu[0] + "  mu[Zr] = " + mu[1]);
        var stable = res.getStablePhases().get(0);
        System.out.println("Phase " + stable.phaseName + " amount=" + stable.amount
                + " x=" + Arrays.toString(stable.x) + " y=" + Arrays.toString(stable.y)
                + " G=" + stable.G + " drivingForce=" + stable.drivingForce);

        // Independent residual check via tangent-plane / Euler relation
        double[] gx = gm.gradient(stable.x, T);
        double gVal = gm.evaluateG(stable.x, T);
        double nfu = gm.nfu();
        double gTilde = gVal / nfu;
        double sumXGx = stable.x[0] * gx[0] + stable.x[1] * gx[1];
        double[] muCheck = new double[2];
        for (int i = 0; i < 2; i++) muCheck[i] = gTilde + gx[i] - sumXGx;
        System.out.println("Tangent-plane mu check: " + Arrays.toString(muCheck));
        System.out.println("Residual |mu - muCheck| = "
                + Math.abs(mu[0] - muCheck[0]) + ", " + Math.abs(mu[1] - muCheck[1]));

        double eulerCheck = gTilde - (mu[0] * stable.x[0] + mu[1] * stable.x[1]);
        System.out.println("Euler residual (should be 0): " + eulerCheck);

        // Hessian sanity check: must be evaluable and (for a stable single
        // phase away from a spinodal) yield a non-singular phase matrix.
        double[][] hess = gm.hessian(y0, T);
        System.out.println("\nHessian at y0 (nip x nip):");
        for (double[] row : hess) System.out.println("  " + Arrays.toString(row));

        // ── eMatNC check at the exact stoichiometric composition ──────────
        System.out.println("\n=== eMatNC effect check at x_V=2/3 (V2Zr stoichiometric) ===");
        double xVStoich = 2.0 / 3.0, xZrStoich = 1.0 / 3.0;
        double[] yStoich = gm.getInitialInternalVars(new double[]{xVStoich, xZrStoich});
        System.out.println("y at stoichiometric x: " + Arrays.toString(yStoich));
        var data = gm.compute(T, P, yStoich, 0, 0, new double[]{0, 0});
        System.out.println("eMat (nip x nip, from inverted phase matrix):");
        for (double[] row : data.eMat) System.out.println("  " + Arrays.toString(row));
        System.out.println("eMatNC (nc x nc, used directly in EquilibriumSolver Jacobian):");
        for (double[] row : data.eMatNC) System.out.println("  " + Arrays.toString(row));
        System.out.println("mA (compositionFromInternal): " + Arrays.toString(data.mA));
        System.out.println("=> eMatNC is a hard-coded identity matrix regardless of the actual\n"
                + "   eMat/phase-matrix response -- it does not depend on y, T, or the\n"
                + "   model at all. In the single-phase fast path used above, eMatNC is\n"
                + "   never referenced (EquilibriumSolver.java lines 74-113 use gx/mA\n"
                + "   directly), so it has NO effect on the Stage 1/2 single-phase results\n"
                + "   reported here. It DOES feed EquilibriumSolver.assembleEquilibriumMatrix\n"
                + "   (the multiphase Newton Jacobian, both the Gibbs-Duhem rows via\n"
                + "   Sigma_A mu_A*eMatNC[A][B] and the mass-balance rows), which is only\n"
                + "   reached once 2+ phases are stable -- out of scope for this task.");
    }
}
