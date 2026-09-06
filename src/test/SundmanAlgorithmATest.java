package test;

import calc.equil.sundman.SundmanEquilibriumSolver;
import calc.equil.sundman.SundmanInitialEstimate;
import calc.equil.sundman.SundmanPhase;
import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import system.model.PhaseModelFactory.PhaseModel;
import system.model.cef.CefPhaseModelAdapter;
import system.ports.EquilibriumResult;
import system.ports.EquilibriumResult.PhaseResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Final validation of the fresh Sundman Algorithm A implementation on the
 * V-Zr TDB, using GridMinimizer (via SundmanInitialEstimate) as the
 * initial estimate for all multiphase cases. For each case, independently
 * reports mass balance, |G+Sum(mu*M)| per stable phase, driving force per
 * metastable phase, sublattice-sum residuals, max|Delta mu|, max|Delta y|,
 * and convergence status.
 */
public class SundmanAlgorithmATest {

    public static void main(String[] args) throws Exception {
        List<String> elements = Arrays.asList("V", "ZR");

        // ---- A. Single phase ----
        singlePhaseCase("BCC_A2", elements, 1200.0, new double[]{0.8, 0.2});
        singlePhaseCase("V2ZR", elements, 1200.0, new double[]{0.6, 0.4});

        // ---- B. Two phase, via GridMinimizer initial estimate ----
        // Scan a few temperatures/compositions across the V2ZR/HCP_A3
        // candidate pair to find a genuine two-phase equilibrium.
        multiPhaseCase("Two-phase scan A", elements,
                Arrays.asList("LIQUID", "BCC_A2", "HCP_A3", "V2ZR"), 1400.0, new double[]{0.5, 0.5});
        multiPhaseCase("Two-phase scan B (different composition)", elements,
                Arrays.asList("LIQUID", "BCC_A2", "HCP_A3", "V2ZR"), 1400.0, new double[]{0.2, 0.8});
        multiPhaseCase("Two-phase scan C", elements,
                Arrays.asList("LIQUID", "BCC_A2", "HCP_A3", "V2ZR"), 900.0, new double[]{0.15, 0.85});

        // ---- C. Three-phase attempt ----
        multiPhaseCase("Three-phase attempt", elements,
                Arrays.asList("LIQUID", "BCC_A2", "HCP_A3", "V2ZR"), 1174.0, new double[]{0.1, 0.9});
    }

    private static void singlePhaseCase(String phaseName, List<String> elements, double T, double[] xStart) throws Exception {
        System.out.println("\n========================================");
        System.out.println("Case: " + phaseName + " single phase, T=" + T + "K, x_start=" + Arrays.toString(xStart));
        System.out.println("========================================");

        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");
        @SuppressWarnings("unchecked")
        List<PhaseModel> models = (List<PhaseModel>) parser.buildPhaseModels(elements, Arrays.asList(phaseName));
        CefPhaseModelAdapter model = (CefPhaseModelAdapter) PhaseModelFactory.toGibbsModel(models.get(0), elements);

        double[] y0 = model.getInitialInternalVars(xStart);
        SundmanPhase phase = new SundmanPhase(model, y0, 1.0, true);

        SundmanEquilibriumSolver solver = new SundmanEquilibriumSolver();
        double[] Ntotal = xStart.clone();
        EquilibriumResult res = solver.solve(T, 101325.0, Ntotal, List.of(phase));

        report(res, Ntotal, List.of(model), solver);
    }

    private static void multiPhaseCase(String label, List<String> elements, List<String> phaseNames,
                                        double T, double[] Ntotal) throws Exception {
        System.out.println("\n========================================");
        System.out.println("Case: " + label + ", T=" + T + "K, N=" + Arrays.toString(Ntotal));
        System.out.println("========================================");

        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");
        @SuppressWarnings("unchecked")
        List<PhaseModel> models = (List<PhaseModel>) parser.buildPhaseModels(elements, phaseNames);

        List<GibbsEnergyModel> candidates = new ArrayList<>();
        List<CefPhaseModelAdapter> adapters = new ArrayList<>();
        for (PhaseModel pm : models) {
            CefPhaseModelAdapter model = (CefPhaseModelAdapter) PhaseModelFactory.toGibbsModel(pm, elements);
            candidates.add(model);
            adapters.add(model);
        }

        // Initial estimate via GridMinimizer, kept entirely separate from
        // SundmanEquilibriumSolver (SundmanInitialEstimate only converts
        // GridMinimizer's existing result into SundmanPhase objects and
        // reseeds metastable phases at their own best composition).
        List<SundmanPhase> phases = SundmanInitialEstimate.build(candidates, T, 101325.0, Ntotal);

        System.out.println("Initial estimate from GridMinimizer:");
        for (SundmanPhase p : phases) {
            System.out.printf("  %-8s stable=%-5b amt=%.4f y=%s%n",
                    p.name, p.stable, p.amount, Arrays.toString(p.y));
        }

        SundmanEquilibriumSolver solver = new SundmanEquilibriumSolver();
        EquilibriumResult res = solver.solve(T, 101325.0, Ntotal, phases);

        report(res, Ntotal, adapters, solver);
    }

    private static void report(EquilibriumResult res, double[] Ntotal, List<CefPhaseModelAdapter> models,
                                SundmanEquilibriumSolver solver) {
        System.out.println("Converged = " + res.isConverged() + "   iterations = " + res.getIterations());
        System.out.println("mu = " + Arrays.toString(res.getMu()));
        System.out.println("max|Delta mu| = " + solver.lastMaxDeltaMu + "   max|Delta y| = " + solver.lastMaxDeltaY);

        double[] massBalance = Ntotal.clone();
        double maxGibbsDuhem = 0.0;
        System.out.println("\n-- Stable phases --");
        for (PhaseResult pr : res.getStablePhases()) {
            CefPhaseModelAdapter model = findModel(models, pr.phaseName);
            double[] mA = model.computeM(pr.y);
            double residual = pr.G;
            for (int A = 0; A < Ntotal.length; A++) residual += res.getMu()[A] * mA[A];
            System.out.printf("  %-10s amt=%.6f  x=%s  y=%s  G=%.4f  |G+Sum(mu*M)|=%.6g%n",
                    pr.phaseName, pr.amount, Arrays.toString(pr.x), Arrays.toString(pr.y), pr.G, Math.abs(residual));
            for (int A = 0; A < Ntotal.length; A++) massBalance[A] -= pr.amount * mA[A];
            maxGibbsDuhem = Math.max(maxGibbsDuhem, Math.abs(residual));

            double[] slRes = sublatticeResiduals(model, pr.y);
            System.out.println("    sublattice-sum residuals: " + Arrays.toString(slRes));
        }

        System.out.println("\n-- Metastable phases --");
        double maxMetaDF = Double.NEGATIVE_INFINITY;
        for (PhaseResult pr : res.getMetastablePhases()) {
            System.out.printf("  %-10s x=%s  G=%.4f  drivingForce=%.6g%n",
                    pr.phaseName, Arrays.toString(pr.x), pr.G, pr.drivingForce);
            maxMetaDF = Math.max(maxMetaDF, pr.drivingForce);
        }
        if (res.getMetastablePhases().isEmpty()) System.out.println("  (none)");

        System.out.println("\nMass-balance residual (Ntotal - Sum(aleph*M)): " + Arrays.toString(massBalance));
        double maxMassBal = 0.0;
        for (double v : massBalance) maxMassBal = Math.max(maxMassBal, Math.abs(v));
        System.out.println("Max |mass-balance residual| = " + maxMassBal);
        System.out.println("Max |G+Sum(mu*M)| over stable phases = " + maxGibbsDuhem);
        if (!res.getMetastablePhases().isEmpty()) {
            System.out.println("Max driving force over metastable phases (should be <= tol) = " + maxMetaDF);
        }
    }

    private static double[] sublatticeResiduals(CefPhaseModelAdapter model, double[] y) {
        int ns = model.getGibbs().ns();
        int[] offs = model.getGibbs().offsets();
        int[] ncSL = model.getGibbs().constituentsPerSublattice();
        double[] r = new double[ns];
        for (int s = 0; s < ns; s++) {
            double sum = 0.0;
            for (int i = 0; i < ncSL[s]; i++) sum += y[offs[s] + i];
            r[s] = sum - 1.0;
        }
        return r;
    }

    private static CefPhaseModelAdapter findModel(List<CefPhaseModelAdapter> models, String name) {
        for (CefPhaseModelAdapter m : models) if (m.phaseName().equals(name)) return m;
        throw new IllegalStateException("model not found: " + name);
    }
}
