package test;

import system.database.TdbParser;
import system.model.PhaseModelFactory.PhaseModel;

import java.util.Arrays;
import java.util.List;

public class CefBuildTest {

    public static void main(String[] args) throws Exception {

        // ── Load database ─────────────────────────────────────────────
        String tdbPath = "data/VZR-re2.TDB";
        TdbParser parser = new TdbParser();
        parser.load(tdbPath);

        // Check if GHSERVV function exists and what it contains
        system.database.tdb rawDb = parser.getUnderlyingTdb();

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
        system.database.tdb filteredDb = rawDb.gettdb(new String[]{"V","ZR"});
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

        // ── Expected values ───────────────────────────────────────────
        System.out.println();
        System.out.println("=== Expected structure ===");
        System.out.println("LIQUID  : ns=1, nip=2, a=[1.0],       nc=[2],   magnetic=false");
        System.out.println("BCC_A2  : ns=2, nip=3, a=[1.0,3.0],   nc=[2,1], magnetic=true (aff=-1.0 p=0.4)");
        System.out.println("HCP_A3  : ns=2, nip=3, a=[1.0,0.5],   nc=[2,1], magnetic=true (aff=-3.0 p=0.28)");
        System.out.println("V2ZR    : ns=2, nip=4, a=[2.0,1.0],   nc=[2,2], magnetic=false");
    }
}
