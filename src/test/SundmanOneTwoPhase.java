package test;

import calc.equil.sundman.*;
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
 * Single bounded two-phase case: exactly two candidate phases (p<=nc=2,
 * so NOT over-determined by the Gibbs phase rule), run once, printing
 * incrementally so the result is visible even if the run is slow.
 */
public class SundmanOneTwoPhase {
    public static void main(String[] args) throws Exception {
        String pa = args.length > 0 ? args[0] : "LIQUID";
        String pb = args.length > 1 ? args[1] : "V2ZR";
        double T = args.length > 2 ? Double.parseDouble(args[2]) : 1400.0;
        double xv = args.length > 3 ? Double.parseDouble(args[3]) : 0.5;

        List<String> elements = Arrays.asList("V", "ZR");
        double[] Ntotal = {xv, 1.0 - xv};

        System.out.println("Pair=" + pa + "+" + pb + " T=" + T + " N=" + Arrays.toString(Ntotal));
        System.out.flush();

        TdbParser parser = new TdbParser();
        parser.load("data/VZR-re2.TDB");
        @SuppressWarnings("unchecked")
        List<PhaseModel> models = (List<PhaseModel>) parser.buildPhaseModels(elements, Arrays.asList(pa, pb));

        List<GibbsEnergyModel> candidates = new ArrayList<>();
        List<CefPhaseModelAdapter> adapters = new ArrayList<>();
        for (PhaseModel pm : models) {
            CefPhaseModelAdapter m = (CefPhaseModelAdapter) PhaseModelFactory.toGibbsModel(pm, elements);
            candidates.add(m);
            adapters.add(m);
        }
        System.out.println("models built: " + adapters.size());
        System.out.flush();

        List<SundmanPhase> phases = SundmanInitialEstimate.build(candidates, T, 101325.0, Ntotal);
        System.out.println("initial estimate:");
        for (SundmanPhase p : phases) {
            System.out.printf("  %-8s stable=%-5b amt=%.4f y=%s%n", p.name, p.stable, p.amount, Arrays.toString(p.y));
        }
        System.out.flush();

        SundmanEquilibriumSolver solver = new SundmanEquilibriumSolver();
        long t0 = System.currentTimeMillis();
        EquilibriumResult res = solver.solve(T, 101325.0, Ntotal, phases);
        long dt = System.currentTimeMillis() - t0;

        System.out.println("solve took " + dt + " ms");
        System.out.println("Converged=" + res.isConverged() + " iters=" + res.getIterations());
        System.out.println("mu=" + Arrays.toString(res.getMu()));
        System.out.println("max|dMu|=" + solver.lastMaxDeltaMu + " max|dY|=" + solver.lastMaxDeltaY);

        double[] mb = Ntotal.clone();
        double maxGD = 0.0;
        for (PhaseResult pr : res.getStablePhases()) {
            CefPhaseModelAdapter m = find(adapters, pr.phaseName);
            double[] mA = m.computeM(pr.y);
            double r = pr.G;
            for (int A = 0; A < 2; A++) r += res.getMu()[A] * mA[A];
            maxGD = Math.max(maxGD, Math.abs(r));
            for (int A = 0; A < 2; A++) mb[A] -= pr.amount * mA[A];
            System.out.printf("STABLE %-8s amt=%.6f x=%s |G+SumMuM|=%.6g%n",
                    pr.phaseName, pr.amount, Arrays.toString(pr.x), Math.abs(r));
            System.out.println("   sublattice residuals=" + Arrays.toString(slRes(m, pr.y)));
        }
        for (PhaseResult pr : res.getMetastablePhases()) {
            System.out.printf("META   %-8s x=%s df=%.6g%n", pr.phaseName, Arrays.toString(pr.x), pr.drivingForce);
        }
        System.out.println("mass-balance residual=" + Arrays.toString(mb));
        System.out.println("max|G+SumMuM|=" + maxGD);
        System.out.flush();
    }

    private static double[] slRes(CefPhaseModelAdapter m, double[] y) {
        int ns = m.getGibbs().ns();
        int[] offs = m.getGibbs().offsets();
        int[] ncSL = m.getGibbs().constituentsPerSublattice();
        double[] r = new double[ns];
        for (int s = 0; s < ns; s++) {
            double sum = 0;
            for (int i = 0; i < ncSL[s]; i++) sum += y[offs[s] + i];
            r[s] = sum - 1.0;
        }
        return r;
    }

    private static CefPhaseModelAdapter find(List<CefPhaseModelAdapter> l, String n) {
        for (CefPhaseModelAdapter m : l) if (m.phaseName().equals(n)) return m;
        throw new IllegalStateException(n);
    }
}
