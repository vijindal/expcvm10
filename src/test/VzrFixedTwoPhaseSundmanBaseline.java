package test;

import calc.equil.EquilibriumSolverV2;
import system.model.GibbsEnergyModel;
import system.model.cef.CefPhaseModelAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fixed two-phase iterative Sundman baseline.
 *
 * Purpose:
 *   Test repeated multiphase Sundman iterations with:
 *
 *       V2ZR + BCC_A2
 *
 *   while keeping the stable phase set fixed.
 *
 * This test deliberately does NOT test phase addition/removal.
 *
 * Initial state:
 *
 *   T = 1100 K
 *   x(V,Zr) = (0.55,0.45)
 *
 *   V2ZR  : xZr = 0.35, omega = 1/6
 *   BCC   : xZr = 0.55, omega = 1/2
 *
 * so that initially
 *
 *   N[V]  = 0.55
 *   N[Zr] = 0.45
 *
 * exactly for the normalized system.
 *
 * The test accesses the already-developed private solver stages
 * reflectively so that no production phase-selection API is added
 * prematurely.
 */
public class VzrFixedTwoPhaseSundmanBaseline {

    private static final String TDB =
            "data/VZR-re2.TDB";

    private static final String PHASE_A =
            "V2ZR";

    private static final String PHASE_B =
            "BCC_A2";

    private static final double T =
            1100.0;

    private static final double P =
            101325.0;

    private static final double[] TARGET =
            {0.55, 0.45};

    /*
     * Initial phase compositions.
     */
    private static final double XZR_A =
            0.35;

    private static final double XZR_B =
            0.55;

    /*
     * Formula-unit phase amounts.
     *
     * V2ZR has 3 sites/fu.
     * BCC has 1 site/fu.
     *
     * omega_A = 1/6 gives total amount 0.5
     * omega_B = 1/2 gives total amount 0.5
     */
    private static final double OMEGA_A =
            1.0 / 6.0;

    private static final double OMEGA_B =
            1.0 / 2.0;

    private static final int MAX_ITER =
            30;

    private static final double Y_TOL =
            1.0e-9;

    private static final double MASS_TOL =
            1.0e-8;

    private static final double PHASE_TOL =
            1.0e-7;

    private static final double STEP_TOL =
            1.0e-9;

    public static void main(String[] args)
            throws Exception {

        System.out.println(
                "============================================================");

        System.out.println(
                "V-Zr fixed two-phase iterative Sundman baseline");

        System.out.println(
                "============================================================");

        System.out.printf(
                "T = %.2f K%n",
                T);

        System.out.printf(
                "P = %.0f Pa%n",
                P);

        System.out.println(
                "Phases = V2ZR + BCC_A2");

        System.out.println(
                "Target X[V,Zr] = "
                + Arrays.toString(TARGET));

        // ------------------------------------------------------------
        // 1. Load models using the existing application pathway.
        // ------------------------------------------------------------

        system.database.TdbParser parser =
                new system.database.TdbParser();

        parser.load(TDB);

        List<String> elements =
                Arrays.asList("V", "ZR");

        List<String> phaseNames =
                Arrays.asList(
                        PHASE_A,
                        PHASE_B);

        @SuppressWarnings("unchecked")
        List<system.model.PhaseModelFactory.PhaseModel> raw =
                (List<system.model.PhaseModelFactory.PhaseModel>)
                        parser.buildPhaseModels(
                                elements,
                                phaseNames);

        if (raw.size() != 2) {
            throw new IllegalStateException(
                    "Expected two phases, got "
                    + raw.size());
        }

        CefPhaseModelAdapter v2zr =
                new CefPhaseModelAdapter(
                        raw.get(0).gibbs,
                        raw.get(0).magnetic,
                        raw.get(0).phaseName,
                        new ArrayList<>(elements),
                        raw.get(0).constituentNames);

        CefPhaseModelAdapter bcc =
                new CefPhaseModelAdapter(
                        raw.get(1).gibbs,
                        raw.get(1).magnetic,
                        raw.get(1).phaseName,
                        new ArrayList<>(elements),
                        raw.get(1).constituentNames);

        List<GibbsEnergyModel> candidates =
                Arrays.asList(
                        v2zr,
                        bcc);

        // ------------------------------------------------------------
        // 2. Create solver.
        // ------------------------------------------------------------

        EquilibriumSolverV2 solver =
                new EquilibriumSolverV2();

        // ------------------------------------------------------------
        // 3. Put solver into initialized phase-indexed state.
        //
        // The public solve() path is deliberately not used because its
        // stable-set selection is still part of the later implementation.
        // ------------------------------------------------------------

        setPrivate(
                solver,
                "T",
                T);

        setPrivate(
                solver,
                "P",
                P);

        setPrivate(
                solver,
                "phaseModels",
                candidates);

        setPrivate(
                solver,
                "targetAmounts",
                TARGET.clone());

        invoke(
                solver,
                "initialize");

        // ------------------------------------------------------------
        // 4. Force fixed stable set {V2ZR,BCC_A2}.
        // ------------------------------------------------------------

        setPrivate(
                solver,
                "stablePhases",
                new int[] {0, 1});

        setPrivate(
                solver,
                "phaseAmounts",
                new double[] {
                    OMEGA_A,
                    OMEGA_B
                });

        /*
         * Access phaseWorks.
         */
        @SuppressWarnings("unchecked")
        List<Object> phaseWorks =
                (List<Object>) getPrivate(
                        solver,
                        "phaseWorks");

        if (phaseWorks.size() != 2) {
            throw new IllegalStateException(
                    "Expected two PhaseWork objects.");
        }

        /*
         * Set explicit starting constitutions.
         */
        Object workA =
                phaseWorks.get(0);

        Object workB =
                phaseWorks.get(1);

        double[] yA =
                v2zr.getInitialInternalVars(
                        new double[] {
                            1.0 - XZR_A,
                            XZR_A
                        });

        double[] yB =
                bcc.getInitialInternalVars(
                        new double[] {
                            1.0 - XZR_B,
                            XZR_B
                        });

        setField(
                workA,
                "y",
                yA.clone());

        setField(
                workB,
                "y",
                yB.clone());

        /*
         * Make candidate-phase internal-variable storage consistent.
         */
        setPrivate(
                solver,
                "phaseInternalVars",
                new double[][] {
                    yA.clone(),
                    yB.clone()
                });

        // ------------------------------------------------------------
        // 5. Evaluate the initial state.
        // ------------------------------------------------------------

        invoke(
                solver,
                "evaluateAllPhases");

        invoke(
                solver,
                "buildPhaseResponses");

        // ------------------------------------------------------------
        // 6. Print initial phase state.
        // ------------------------------------------------------------

        printPhaseState(
                phaseWorks);

        printTotals(
                solver,
                phaseWorks,
                "Initial state");

        // ------------------------------------------------------------
        // 7. Fixed two-phase Sundman iterations.
        // ------------------------------------------------------------

        double previousG =
                totalG(
                        phaseWorks);

        boolean converged =
                false;

        for (int iteration = 0;
             iteration < MAX_ITER;
             iteration++) {

            System.out.println();
            System.out.println(
                    "==================================================");
            System.out.println(
                    "ITERATION "
                    + iteration);
            System.out.println(
                    "==================================================");

            /*
             * --------------------------------------------------------
             * A. Re-evaluate both phases.
             * --------------------------------------------------------
             */
            invoke(
                    solver,
                    "evaluateAllPhases");

            /*
             * --------------------------------------------------------
             * B. Rebuild both phase responses.
             * --------------------------------------------------------
             */
            invoke(
                    solver,
                    "buildPhaseResponses");

            /*
             * --------------------------------------------------------
             * C. Build global two-phase Eq.(59).
             * --------------------------------------------------------
             */
            invoke(
                    solver,
                    "buildEquilibriumMatrix");

            /*
             * --------------------------------------------------------
             * D. Solve global system.
             * --------------------------------------------------------
             */
            invoke(
                    solver,
                    "solveEquilibriumMatrix");

            /*
             * --------------------------------------------------------
             * E. Calculate DeltaY for both phases.
             * --------------------------------------------------------
             */
            invoke(
                    solver,
                    "calculateInternalCorrections");

            /*
             * --------------------------------------------------------
             * F. Update both phases simultaneously.
             * --------------------------------------------------------
             */
            invoke(
                    solver,
                    "updateState");

            /*
             * --------------------------------------------------------
             * G. Evaluate accepted state.
             * --------------------------------------------------------
             */
            invoke(
                    solver,
                    "evaluateAllPhases");

            double currentG =
                    totalG(
                        phaseWorks);

            double dG =
                    currentG - previousG;

            /*
             * --------------------------------------------------------
             * H. Independent equilibrium residuals.
             * --------------------------------------------------------
             */
            double maxMassResidual =
                    calculateMassResidual(
                            solver,
                            phaseWorks);

            double maxPhaseResidual =
                    calculatePhaseEquilibriumResidual(
                            solver,
                            phaseWorks);

            double maxStationarity =
                    calculateStationarityResidual(
                            solver,
                            phaseWorks);

            double maxDY =
                    calculateAcceptedDY(
                            solver);

            System.out.println();
            System.out.println(
                    "Independent convergence diagnostics");
            System.out.println(
                    "-----------------------------------");

            System.out.printf(
                    "G                    = %.15f%n",
                    currentG);

            System.out.printf(
                    "Delta G              = %.15e%n",
                    dG);

            System.out.printf(
                    "max mass residual    = %.6e%n",
                    maxMassResidual);

            System.out.printf(
                    "max phase residual   = %.6e%n",
                    maxPhaseResidual);

            System.out.printf(
                    "max stationarity     = %.6e%n",
                    maxStationarity);

            System.out.printf(
                    "max accepted DeltaY  = %.6e%n",
                    maxDY);

            printTotals(
                    solver,
                    phaseWorks,
                    "Accepted state");

            /*
             * Stop only when BOTH equilibrium residuals and changes
             * are small.
             */
            if (maxMassResidual <= MASS_TOL
                    && maxPhaseResidual <= PHASE_TOL
                    && maxStationarity <= PHASE_TOL
                    && maxDY <= Y_TOL) {

                converged =
                        true;

                System.out.println();
                System.out.println(
                        "FIXED TWO-PHASE CONVERGENCE ACHIEVED.");

                break;
            }

            previousG =
                    currentG;
        }

        // ------------------------------------------------------------
        // 8. Final result.
        // ------------------------------------------------------------

        System.out.println();
        System.out.println(
                "============================================================");
        System.out.println(
                "FINAL FIXED TWO-PHASE RESULT");
        System.out.println(
                "============================================================");

        printPhaseState(
                phaseWorks);

        printTotals(
                solver,
                phaseWorks,
                "Final state");

        System.out.println(
                "Converged = "
                + converged);

        if (!converged) {

            throw new AssertionError(
                    "Fixed V2ZR + BCC_A2 Sundman iteration "
                    + "did not converge within "
                    + MAX_ITER
                    + " iterations.");
        }

        System.out.println();
        System.out.println(
                "PASS: fixed two-phase Sundman equilibrium "
                + "iteration converged.");
    }

    // ================================================================
    // Diagnostics
    // ================================================================

    private static double totalG(
            List<Object> phaseWorks)
            throws Exception {

        double[] omega =
                {OMEGA_A, OMEGA_B};

        double total =
                0.0;

        for (int k = 0;
             k < 2;
             k++) {

            Object work =
                    phaseWorks.get(k);

            double G =
                    (double) getField(
                            work,
                            "G");

            total +=
                    omega[k] * G;
        }

        return total;
    }

    /*
     * The reflection helpers below are intentionally simple. They allow
     * this regression test to exercise the current private solver stages
     * without adding a temporary testing API to production code.
     */

    private static double calculateMassResidual(
            EquilibriumSolverV2 solver,
            List<Object> phaseWorks)
            throws Exception {

        double[] target =
                (double[]) getPrivate(
                        solver,
                        "targetAmounts");

        int[] stable =
                (int[]) getPrivate(
                        solver,
                        "stablePhases");

        double[] omega =
                (double[]) getPrivate(
                        solver,
                        "phaseAmounts");

        double maximum =
                0.0;

        for (int A = 0;
             A < target.length;
             A++) {

            double total =
                    0.0;

            for (int k = 0;
                 k < stable.length;
                 k++) {

                Object work =
                        phaseWorks.get(
                                stable[k]);

                double[] mA =
                        (double[]) getField(
                                work,
                                "mA");

                total +=
                        omega[k] * mA[A];
            }

            maximum =
                    Math.max(
                            maximum,
                            Math.abs(
                                    total - target[A]));
        }

        return maximum;
    }

    private static double calculatePhaseEquilibriumResidual(
            EquilibriumSolverV2 solver,
            List<Object> phaseWorks)
            throws Exception {

        double[] lambda =
                (double[]) getPrivate(
                        solver,
                        "mu");

        int[] stable =
                (int[]) getPrivate(
                        solver,
                        "stablePhases");

        double maximum =
                0.0;

        for (int k = 0;
             k < stable.length;
             k++) {

            Object work =
                    phaseWorks.get(
                            stable[k]);

            double G =
                    (double) getField(
                            work,
                            "G");

            double[] mA =
                    (double[]) getField(
                            work,
                            "mA");

            double predicted =
                    0.0;

            for (int A = 0;
                 A < lambda.length;
                 A++) {

                predicted +=
                        mA[A] * lambda[A];
            }

            maximum =
                    Math.max(
                            maximum,
                            Math.abs(
                                    G - predicted));
        }

        return maximum;
    }

    private static double calculateStationarityResidual(
            EquilibriumSolverV2 solver,
            List<Object> phaseWorks)
            throws Exception {

        double[] lambda =
                (double[]) getPrivate(
                        solver,
                        "mu");

        int[] stable =
                (int[]) getPrivate(
                        solver,
                        "stablePhases");

        double maximum =
                0.0;

        for (int k = 0;
             k < stable.length;
             k++) {

            Object work =
                    phaseWorks.get(
                            stable[k]);

            double[] gy =
                    (double[]) getField(
                            work,
                            "gy");

            double[][] dMdY =
                    (double[][]) getField(
                            work,
                            "dMdY");

            double[] gamma =
                    (double[]) getField(
                            work,
                            "gamma");

            CefPhaseModelAdapter model =
                    (CefPhaseModelAdapter)
                            getField(
                                    work,
                                    "model");

            int[] offsets =
                    model.sublatticeOffsets();

            int[] nconst =
                    model.constituentsPerSublattice();

            for (int i = 0;
                 i < gy.length;
                 i++) {

                double r =
                        gy[i];

                for (int A = 0;
                     A < lambda.length;
                     A++) {

                    r -=
                            dMdY[A][i]
                            * lambda[A];
                }

                int s =
                        sublatticeOf(
                                i,
                                offsets,
                                nconst);

                if (gamma != null
                        && s < gamma.length) {

                    r -= gamma[s];
                }

                maximum =
                        Math.max(
                                maximum,
                                Math.abs(r));
            }
        }

        return maximum;
    }

    private static double calculateAcceptedDY(
            EquilibriumSolverV2 solver)
            throws Exception {

        double[][] delta =
                (double[][]) getPrivate(
                        solver,
                        "deltaPhaseInternalVars");

        int[] stable =
                (int[]) getPrivate(
                        solver,
                        "stablePhases");

        double alpha =
                (double) getPrivate(
                        solver,
                        "acceptedStepScale");

        double maximum =
                0.0;

        for (int k = 0;
             k < stable.length;
             k++) {

            double norm =
                    vectorNorm(
                            delta[k]);

            maximum =
                    Math.max(
                            maximum,
                            alpha * norm);
        }

        return maximum;
    }

    private static void printTotals(
            EquilibriumSolverV2 solver,
            List<Object> phaseWorks,
            String title)
            throws Exception {

        int[] stable =
                (int[]) getPrivate(
                        solver,
                        "stablePhases");

        double[] omega =
                (double[]) getPrivate(
                        solver,
                        "phaseAmounts");

        System.out.println();
        System.out.println(
                title);
        System.out.println(
                "--------------------------------");

        double totalG =
                0.0;

        double[] totalM =
                new double[2];

        for (int k = 0;
             k < stable.length;
             k++) {

            Object work =
                    phaseWorks.get(
                            stable[k]);

            double G =
                    (double) getField(
                            work,
                            "G");

            double[] M =
                    (double[]) getField(
                            work,
                            "mA");

            totalG +=
                    omega[k] * G;

            for (int A = 0;
                 A < totalM.length;
                 A++) {

                totalM[A] +=
                        omega[k] * M[A];
            }

            System.out.printf(
                    "slot %d  %s  omega=%.12e  M=%s%n",
                    k,
                    ((CefPhaseModelAdapter)
                            getField(work, "model"))
                            .phaseName(),
                    omega[k],
                    Arrays.toString(M));
        }

        System.out.println(
                "Total M = "
                + Arrays.toString(totalM));

        System.out.printf(
                "Total G = %.15f%n",
                totalG);

        System.out.println(
                "Target M = ["
                + TARGET[0]
                + ", "
                + TARGET[1]
                + "]");
    }

    private static void printPhaseState(
            List<Object> phaseWorks)
            throws Exception {

        System.out.println();
        System.out.println(
                "Phase constitutions");
        System.out.println(
                "-------------------");

        for (int p = 0;
             p < phaseWorks.size();
             p++) {

            Object work =
                    phaseWorks.get(p);

            CefPhaseModelAdapter model =
                    (CefPhaseModelAdapter)
                            getField(
                                    work,
                                    "model");

            double[] y =
                    (double[]) getField(
                            work,
                            "y");

            System.out.println(
                    p
                    + " "
                    + model.phaseName()
                    + " Y="
                    + Arrays.toString(y));
        }
    }

    // ================================================================
    // Reflection helpers
    // ================================================================

    private static Object invoke(
            Object object,
            String methodName)
            throws Exception {

        Method method =
                findMethod(
                        object.getClass(),
                        methodName);

        method.setAccessible(true);

        return method.invoke(object);
    }

    private static Method findMethod(
            Class<?> clazz,
            String name)
            throws NoSuchMethodException {

        Class<?> c =
                clazz;

        while (c != null) {

            for (Method m :
                    c.getDeclaredMethods()) {

                if (m.getName().equals(name)
                        && m.getParameterCount() == 0) {

                    return m;
                }
            }

            c =
                    c.getSuperclass();
        }

        throw new NoSuchMethodException(
                name);
    }

    private static Object getPrivate(
            Object object,
            String fieldName)
            throws Exception {

        Field f =
                findField(
                        object.getClass(),
                        fieldName);

        f.setAccessible(true);

        return f.get(object);
    }

    private static void setPrivate(
            Object object,
            String fieldName,
            Object value)
            throws Exception {

        Field f =
                findField(
                        object.getClass(),
                        fieldName);

        f.setAccessible(true);

        f.set(
                object,
                value);
    }

    private static Object getField(
            Object object,
            String fieldName)
            throws Exception {

        Field f =
                findField(
                        object.getClass(),
                        fieldName);

        f.setAccessible(true);

        return f.get(object);
    }

    private static void setField(
            Object object,
            String fieldName,
            Object value)
            throws Exception {

        Field f =
                findField(
                        object.getClass(),
                        fieldName);

        f.setAccessible(true);

        f.set(
                object,
                value);
    }

    private static Field findField(
            Class<?> clazz,
            String name)
            throws NoSuchFieldException {

        Class<?> c =
                clazz;

        while (c != null) {

            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }

        throw new NoSuchFieldException(
                name);
    }

    private static double[] vector(
            double[] a) {

        return a.clone();
    }

    private static double vectorNorm(
            double[] v) {

        double sum =
                0.0;

        for (double x : v) {
            sum += x * x;
        }

        return Math.sqrt(sum);
    }

    private static int sublatticeOf(
            int index,
            int[] offsets,
            int[] nconst) {

        for (int s = 0;
             s < offsets.length;
             s++) {

            if (index >= offsets[s]
                    && index < offsets[s] + nconst[s]) {

                return s;
            }
        }

        throw new IllegalArgumentException(
                "Unknown sublattice for Y index "
                        + index);
    }

    /*
     * Dummy helper retained solely so the test compiles if an IDE
     * performs strict unused-method checking. It is never called.
     */
    private static Object getPrivateStaticHack(
            Object ignored) {

        return new double[] {
            OMEGA_A,
            OMEGA_B
        };
    }
}
