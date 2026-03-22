package test;

import infra.TdbParser;
import infra.RkPhaseModelFactory;
import infra.TdbUnaryGibbs;
import database.tdb;
import domain.*;
import thermocalc.equil.*;
import java.util.*;

public class NbTiDebug {
    public static void main(String[] a) throws Exception {
        TdbParser parser = new TdbParser();
        parser.load("data/cost507.tdb");
        TdbParser system = (TdbParser) parser.extractSystem(new String[]{"NB","TI"});
        tdb systdb = system.getUnderlyingTdb();

        List<String> elems = List.of("NB","TI");
        String[] phaseNames = {"BCC_A2","HCP_A3","LIQUID"};
        List<PhaseModelPort> candidates = new ArrayList<>();
        for (String ph : phaseNames)
            candidates.add(RkPhaseModelFactory.build(ph, elems, systdb));

        // Check G0 unary for TI and NB at different phases/temperatures
        System.out.println("\n--- Unary G0 for TI ---");
        TdbUnaryGibbs tiUnary = new TdbUnaryGibbs("TI", systdb);
        for (String ph : phaseNames) {
            System.out.printf("%-10s  ", ph);
            for (double T : new double[]{1000, 1500, 2000, 2500})
                try { System.out.printf("T=%4.0f: %9.0f  ", T, tiUnary.gibbs(ph, T)); }
                catch(Exception e) { System.out.printf("T=%4.0f: ERR  ", T); }
            System.out.println();
        }
        System.out.println("\n--- Unary G0 for NB ---");
        TdbUnaryGibbs nbUnary = new TdbUnaryGibbs("NB", systdb);
        for (String ph : phaseNames) {
            System.out.printf("%-10s  ", ph);
            for (double T : new double[]{1000, 1500, 2000, 2500})
                try { System.out.printf("T=%4.0f: %9.0f  ", T, nbUnary.gibbs(ph, T)); }
                catch(Exception e) { System.out.printf("T=%4.0f: ERR  ", T); }
            System.out.println();
        }

        // G at pure-TI composition vs T
        System.out.println("\n--- G at x(NB)=0.02 (TI-rich), scan T ---");
        double[] cTi = {0.02, 0.98};
        for (double T : new double[]{1000,1500,1800,2000,2200}) {
            System.out.printf("T=%5.0f  ", T);
            for (PhaseModelPort m : candidates)
                System.out.printf("%-8s=%9.0f  ", m.phaseName(), m.evaluateG(cTi, T));
            System.out.println();
        }

        // G at x=0.5 vs T
        System.out.println("\n--- G at x(NB)=0.5, scan T ---");
        double[] c5 = {0.5, 0.5};
        for (double T : new double[]{1000,1500,1800,2000,2200}) {
            System.out.printf("T=%5.0f  ", T);
            for (PhaseModelPort m : candidates)
                System.out.printf("%-8s=%9.0f  ", m.phaseName(), m.evaluateG(c5, T));
            System.out.println();
        }

        // GridMinimizer at T=2000, various compositions
        GridMinimizer gm = new GridMinimizer();
        System.out.println("\n--- GridMinimizer at T=2000K, scan x(NB) ---");
        for (double x = 0.05; x <= 0.96; x += 0.1) {
            double[] c = {x, 1-x};
            EquilibriumState st = gm.initialize(candidates, 2000, 101325, c);
            StringBuilder sb = new StringBuilder();
            for (PhaseRecord pr : st.stablePhases())
                sb.append(pr.phaseName()).append("(x=").append(String.format("%.3f",pr.x[0])).append(",f=").append(String.format("%.2f",pr.amount)).append(") ");
            System.out.printf("x(NB)=%.2f  %s%n", x, sb);
        }
    }
}
