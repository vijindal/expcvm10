package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory.PhaseModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CefBuildTest {

    public static void main(String[] args) throws Exception {

        // ── Load database ─────────────────────────────────────────────
        String tdbPath = "data/VZR-re2.TDB";
        TdbParser parser = new TdbParser();
        parser.load(tdbPath);

        system.database.tdb rawDb = parser.getUnderlyingTdb();
        system.database.tdb filtered = rawDb.gettdb(new String[]{"V","ZR"});
        ArrayList<system.database.tdb.Parameter> p2 =
            filtered.getPhaseParam(
                new java.util.ArrayList<>(java.util.Arrays.asList("V","ZR")),
                "V2ZR");
        for (system.database.tdb.Parameter p : p2) {
            if (!"G".equalsIgnoreCase(p.getType())) continue;
            ArrayList<ArrayList<String>> cl = p.getConstituentList();
            // V:V end member only
            if (cl.size()==2
                && cl.get(0).size()==1 && "V".equals(cl.get(0).get(0))
                && cl.get(1).size()==1 && "V".equals(cl.get(1).get(0))) {
                System.out.println("V2ZR V:V raw expList:");
                for (system.database.tdb.Exp exp : p.getExpList()) {
                    System.out.println("  range=" + exp.getTempRange());
                    System.out.println("  subCoeffList=" + exp.getSubCoeffList());
                    System.out.println("  expStr=" + exp.getExpStr());
                }
            }
        }

        // Check if GHSERVV function exists and what it contains

        // Print phase param for V in BCC_A2 from the FULL unfiltered db
        // (before gettdb filtering)
        java.util.ArrayList<system.database.tdb.Parameter> allParams =
            rawDb.getPhaseParam(
                new java.util.ArrayList<>(java.util.Arrays.asList("V","ZR")),
                "BCC_A2");
        for (system.database.tdb.Parameter p : allParams) {
            java.util.ArrayList<java.util.ArrayList<String>> cl = p.getConstituentList();
            // Find V:VA end member only
            if (cl.size() >= 1 && cl.get(0).size() == 1
                    && "V".equals(cl.get(0).get(0))
                    && cl.size() >= 2 && cl.get(1).size() == 1
                    && "VA".equals(cl.get(1).get(0))) {
                System.out.println("FOUND V:VA param");
                for (system.database.tdb.Exp exp : p.getExpList()) {
                    System.out.println("  range=" + exp.getTempRange());
                    System.out.println("  subCoeffList=" + exp.getSubCoeffList());
                    System.out.println("  coeffList="    + exp.getCoeffList());
                    System.out.println("  funcList="     + exp.getFuncList());
                    System.out.println("  funcCoeffList="+ exp.getFuncCoeffList());
                    System.out.println("  expStr="       + exp.getExpStr());
                }
            }
        }

        // Also check V:VA param from element-filtered tdb
        system.database.tdb filteredRaw = rawDb.gettdb(new String[]{"V","ZR"});
        java.util.ArrayList<system.database.tdb.Parameter> filtParams =
            filteredRaw.getPhaseParam(
                new java.util.ArrayList<>(java.util.Arrays.asList("V","ZR")),
                "BCC_A2");
        for (system.database.tdb.Parameter p : filtParams) {
            java.util.ArrayList<java.util.ArrayList<String>> cl = p.getConstituentList();
            if (cl.size() >= 1 && cl.get(0).size() == 1
                    && "V".equals(cl.get(0).get(0))
                    && cl.size() >= 2 && cl.get(1).size() == 1
                    && "VA".equals(cl.get(1).get(0))) {
                System.out.println("FILTERED V:VA param");
                for (system.database.tdb.Exp exp : p.getExpList()) {
                    System.out.println("  range=" + exp.getTempRange());
                    System.out.println("  subCoeffList=" + exp.getSubCoeffList());
                    System.out.println("  funcList="     + exp.getFuncList());
                }
            }
        }

        // ── Define system ─────────────────────────────────────────────
        List<String> elements = Arrays.asList("V", "ZR");
        java.util.ArrayList<system.database.tdb.Parameter> params =
            rawDb.getPhaseParam(new java.util.ArrayList<>(elements), "BCC_A2");

        // Print ALL params for V constituent only
        for (system.database.tdb.Parameter p : params) {
            boolean hasV = false;
            for (java.util.ArrayList<String> sl : p.getConstituentList())
                for (String c : sl)
                    if ("V".equals(c)) hasV = true;
            if (!hasV) continue;
            System.out.println("PARAM type=" + p.getType()
                + " order=" + p.getOrder()
                + " const=" + p.getConstituentList());
            for (system.database.tdb.Exp exp : p.getExpList()) {
                System.out.println("  EXP range=" + exp.getTempRange());
                System.out.println("      coeffList="    + exp.getCoeffList());
                System.out.println("      subCoeffList=" + exp.getSubCoeffList());
                System.out.println("      funcList="     + exp.getFuncList());
                System.out.println("      funcCoeffList="+ exp.getFuncCoeffList());
                System.out.println("      expStr="       + exp.getExpStr());
            }
        }

        // Also print the GHSERVV function from the database
        // to see if it was parsed
        System.out.println("\n--- Functions in rawDb ---");
        // We cannot access functionList directly, but we can
        // check by loading a fresh tdb and calling printtdb()
        // Instead just print the raw expStr for V params above
        List<String> phases   = Arrays.asList("LIQUID", "BCC_A2", "HCP_A3", "V2ZR");

        // ── Build models ──────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        List<PhaseModel> models = (List<PhaseModel>)
            parser.buildPhaseModels(elements, phases);

        // ── Print results ─────────────────────────────────────────────
        System.out.println("\n=== CEF Build Results ===");
        System.out.println("Phases requested : " + phases.size());
        System.out.println("Phases built     : " + models.size());
        System.out.println();

        for (PhaseModel m : models) {
            System.out.println("Phase  : " + m.phaseName);
            System.out.println("  ns   : " + m.gibbs.ns());
            System.out.println("  nip  : " + m.gibbs.nip());
            System.out.println("  a[]  : " + Arrays.toString(m.gibbs.stoichiometry()));
            System.out.println("  nc[] : " + Arrays.toString(m.gibbs.constituentsPerSublattice()));
            System.out.println("  magnetic : " + m.hasMagnetic()
                + (m.hasMagnetic()
                   ? " (aff=" + m.aff + " p=" + m.p + ")"
                   : ""));
            System.out.println();
        }

        // ── Spot-check: evaluate G for each phase at T=1000K ─────────
        System.out.println("=== G evaluation at T=1000K ===");
        for (PhaseModel m : models) {
            int nip = m.gibbs.nip();
            double[] y = new double[nip];
            // Equal distribution as starting point
            int offset = 0;
            int[] nc = m.gibbs.constituentsPerSublattice();
            for (int s = 0; s < m.gibbs.ns(); s++) {
                for (int i = 0; i < nc[s]; i++)
                    y[offset + i] = 1.0 / nc[s];
                offset += nc[s];
            }
            double G = m.gibbs.evaluate(y, 1000.0);
            System.out.printf("  %-10s G = %14.2f J/mol%n", m.phaseName, G);
        }

        // Diagnostic: check G vs composition for each phase at several
        // compositions to verify grid minimizer inputs are correct
        System.out.println("\n=== G vs composition sweep ===");
        double[] xVals = {0.1, 0.3, 0.5, 0.67, 0.9};
        for (PhaseModel m : models) {
            System.out.println("Phase: " + m.phaseName);
            system.model.GibbsEnergyModel gm =
                system.model.PhaseModelFactory.toGibbsModel(m, elements);
            for (double xV : xVals) {
                double[] x = {xV, 1.0 - xV};
                double G = gm.evaluateG(x, 1000.0);
                double[] y = gm.getInitialInternalVars(x);
                double[] xBack = gm.compositionFromInternal(y);
                System.out.printf("  x_V=%.2f -> G=%.2f  y=%s  xBack=[%.3f,%.3f]%n",
                    xV, G,
                    java.util.Arrays.toString(y),
                    xBack[0], xBack[1]);
            }
        }

        // ── Expected values ───────────────────────────────────────────
        System.out.println();
        System.out.println("=== Expected structure ===");
        System.out.println("LIQUID  : ns=1, nip=2, a=[1.0],       nc=[2],   magnetic=false");
        System.out.println("BCC_A2  : ns=2, nip=3, a=[1.0,3.0],   nc=[2,1], magnetic=true (aff=-1.0 p=0.4)");
        System.out.println("HCP_A3  : ns=2, nip=3, a=[1.0,0.5],   nc=[2,1], magnetic=true (aff=-3.0 p=0.28)");
        System.out.println("V2ZR    : ns=2, nip=4, a=[2.0,1.0],   nc=[2,2], magnetic=false");

        // ── Validate LIQUID G against SGTE Table IIIa at 2200K ────────
        // Table IIIa gives ΔGm (integral molar Gibbs energy of mixing)
        // at 2200K for liquid phase, reference: V(liquid), Zr(liquid)
        //
        // ΔGm = G_mix(x) - [x_V * G°_V(liq) + x_Zr * G°_Zr(liq)]
        //
        // We can verify ΔGm by computing:
        //   ΔGm(x) = G_liquid(x,T) - [x_V*G_liquid(xV=1,T)
        //                             + x_Zr*G_liquid(xZr=1,T)]
        //
        // SGTE Table IIIa values at 2200K (J/mol):
        // xZr=0.0: ΔGm=0
        // xZr=0.1: ΔGm=-3903
        // xZr=0.2: ΔGm=-5687
        // xZr=0.3: ΔGm=-6842
        // xZr=0.4: ΔGm=-7608
        // xZr=0.5: ΔGm=-8040
        // xZr=1.0: ΔGm=0

        List<system.model.GibbsEnergyModel> gibbsModels = new ArrayList<>();
        for (PhaseModel m : models) {
            gibbsModels.add(system.model.PhaseModelFactory.toGibbsModel(
                m, elements));
        }

        // CSV output for plotting
        System.out.println("\n=== CSV_START ===");
        System.out.println("xV,LIQUID,BCC_A2,HCP_A3,V2ZR");
        for (int i = 1; i <= 99; i++) {
            double xV = i / 100.0;
            double[] xc = {xV, 1.0 - xV};
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%.2f", xV));
            for (var gm : gibbsModels) {
                double G   = gm.evaluateG(xc, 1000.0);
                double nfu = Math.max(gm.nfu(), 1.0);
                sb.append(String.format(",%.2f", G / nfu));
            }
            System.out.println(sb);
        }
        System.out.println("=== CSV_END ===");

        calc.equil.EquilibriumSolver solver =
            new calc.equil.EquilibriumSolver();

        double[][] tests = {
            {2000.0, 0.5, 0.5},
            {1000.0, 0.5, 0.5},
            {1000.0, 0.95, 0.05},
            {1000.0, 1.0-0.927, 0.927},
        };
        String[] labels = {
            "2000K xV=0.50 expect:LIQUID",
            "1000K xV=0.50 expect:V2Zr+HCP",
            "1000K xV=0.95 expect:BCC",
            "1000K xV=0.07 expect:BCC+V2Zr+HCP",
        };

        for (int t = 0; t < tests.length; t++) {
            double T    = tests[t][0];
            double[] comp = {tests[t][1], tests[t][2]};
            System.out.println("\n=== " + labels[t] + " ===");
            try {
                system.ports.EquilibriumResult res =
                    solver.solve(T, 101325.0, comp, gibbsModels);
                System.out.printf("Converged=%-5b iter=%d%n",
                    res.isConverged(), res.getIterations());
                double[] mu = res.getMu();
                if (mu != null)
                    System.out.printf("mu[V]=%.1f  mu[Zr]=%.1f%n",
                        mu[0], mu[1]);
                for (var pr : res.getStablePhases())
                    System.out.printf("  %-10s amt=%.4f  x=[%.4f,%.4f]%n",
                        pr.phaseName, pr.amount,
                        pr.x[0], pr.x[1]);
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }
}
