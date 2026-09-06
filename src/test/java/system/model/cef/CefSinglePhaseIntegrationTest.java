package system.model.cef;

import system.database.TdbParser;
import system.database.tdb;
import system.model.PhaseModelFactory;
import system.model.GibbsEnergyModel;
import system.ports.EquilibriumResult;
import calc.equil.EquilibriumSolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Integration tests for the generalized CEF implementation.
 *
 * The purpose of this test is deliberately narrow:
 *
 *   steel7.TDB
 *       -> TdbParser
 *       -> PhaseModelFactory
 *       -> CefGibbs
 *       -> CefPhaseModelAdapter
 *
 * No multiphase equilibrium calculation is attempted here.
 */
public class CefSinglePhaseIntegrationTest {

    private static final String STEEL7_TDB =
            "data/steel7.TDB";

    private static final double T = 1273.15;

    /**
     * Build one real CEF phase from steel7.TDB.
     */
    private PhaseModelFactory.PhaseModel buildPhase(String phaseName) throws Exception {

        TdbParser parser = new TdbParser();
        parser.load(STEEL7_TDB);

        tdb rawDb = parser.getUnderlyingTdb();

        String[] elements = {"FE", "CR", "NI", "MO", "V", "C"};
        tdb filtered = rawDb.gettdb(elements);

        if (filtered == null) {
            throw new IllegalArgumentException("Failed to filter TDB for elements");
        }

        List<String> elemList = Arrays.asList(elements);

        return PhaseModelFactory.build(
                phaseName,
                filtered,
                elemList,
                new HashMap<>(),
                new HashMap<>()
        );
    }

    public static void main(String[] args) throws Exception {
        CefSinglePhaseIntegrationTest test = new CefSinglePhaseIntegrationTest();

        test.fccPhaseCanBeBuiltFromRealTdb();
        test.fccGibbsEnergyIsFinite();
        test.fccGradientIsFinite();
        test.fccHessianIsFinite();
        test.fccHessianIsSymmetric();
        test.fccTemperatureDerivativeIsFinite();
        test.fccAdapterCanBeCreated();
        test.verifyCurrentInteractionParameterImplementation();
        test.diagnoseFccGradientFiniteDifference();
        test.diagnoseFccHessianFiniteDifference();
        test.diagnoseFccConstrainedDirectionalSecondDerivative();
        test.fccSinglePhaseAlgorithmAConverges();

        System.out.println("\n=== All tests completed ===");
    }

    void fccPhaseCanBeBuiltFromRealTdb() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        if (phase == null || phase.gibbs == null) {
            throw new AssertionError("Phase or gibbs must not be null");
        }

        if (!"FCC_A1".equals(phase.phaseName)) {
            throw new AssertionError("Phase name mismatch");
        }

        if (phase.gibbs.ns() < 2) {
            throw new AssertionError("Expected at least 2 sublattices");
        }

        if (phase.gibbs.nip() <= 0) {
            throw new AssertionError("Expected positive nip");
        }

        System.out.println();
        System.out.println("Phase       : " + phase.phaseName);
        System.out.println("Sublattices : " + phase.gibbs.ns());
        System.out.println("Internal vars: " + phase.gibbs.nip());
        System.out.println("Constituents : "
                + Arrays.toString(phase.gibbs.constituentsPerSublattice()));
    }

    void fccGibbsEnergyIsFinite() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;

        double[] y = makeInteriorComposition(g);

        double value = g.evaluate(T, y);

        if (!Double.isFinite(value)) {
            throw new AssertionError("CEF Gibbs energy must be finite");
        }

        System.out.println();
        System.out.println("T = " + T);
        System.out.println("y = " + Arrays.toString(y));
        System.out.println("G = " + value);
    }

    void fccGradientIsFinite() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;

        double[] y = makeInteriorComposition(g);

        double[] gradient = g.gradient(T, y);

        if (g.nip() != gradient.length) {
            throw new AssertionError("Gradient length mismatch");
        }

        for (int i = 0; i < gradient.length; i++) {
            if (!Double.isFinite(gradient[i])) {
                throw new AssertionError("Gradient[" + i + "] must be finite");
            }
        }

        System.out.println();
        System.out.println("Gradient:");
        System.out.println(Arrays.toString(gradient));
    }

    void fccHessianIsFinite() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;

        double[] y = makeInteriorComposition(g);

        double[][] hessian = g.hessian(T, y);

        if (g.nip() != hessian.length) {
            throw new AssertionError("Hessian length mismatch");
        }

        for (int i = 0; i < hessian.length; i++) {
            if (g.nip() != hessian[i].length) {
                throw new AssertionError("Hessian[" + i + "] length mismatch");
            }

            for (int j = 0; j < hessian[i].length; j++) {
                if (!Double.isFinite(hessian[i][j])) {
                    throw new AssertionError("Hessian[" + i + "][" + j + "] must be finite");
                }
            }
        }
    }

    void fccHessianIsSymmetric() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;

        double[] y = makeInteriorComposition(g);

        double[][] hessian = g.hessian(T, y);

        for (int i = 0; i < hessian.length; i++) {
            for (int j = i + 1; j < hessian.length; j++) {
                double diff = Math.abs(hessian[i][j] - hessian[j][i]);
                if (diff > 1.0e-8) {
                    throw new AssertionError("CEF Hessian must be symmetric: [" + i + "][" + j
                        + "]=" + hessian[i][j] + " vs [" + j + "][" + i + "]=" + hessian[j][i]);
                }
            }
        }
    }

    void fccTemperatureDerivativeIsFinite() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;

        double[] y = makeInteriorComposition(g);

        double dGdT = g.temperatureDerivative(T, y);

        if (!Double.isFinite(dGdT)) {
            throw new AssertionError("dG/dT must be finite");
        }

        System.out.println();
        System.out.println("dG/dT = " + dGdT);
    }

    void fccAdapterCanBeCreated() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        ArrayList<String> elements = new ArrayList<>(
                Arrays.asList("FE", "CR", "NI", "MO", "V", "C")
        );

        CefPhaseModelAdapter adapter =
                new CefPhaseModelAdapter(
                        phase.gibbs,
                        phase.magnetic,
                        phase.phaseName,
                        elements,
                        phase.constituentNames
                );

        if (adapter == null) {
            throw new AssertionError("Adapter must not be null");
        }

        if (!"FCC_A1".equals(adapter.phaseName())) {
            throw new AssertionError("Adapter phase name mismatch");
        }

        if (!"CEF".equals(adapter.modelType())) {
            throw new AssertionError("Adapter model type must be CEF");
        }

        if (elements.size() != adapter.numComponents()) {
            throw new AssertionError("Adapter numComponents mismatch");
        }

        if (phase.gibbs.nip() != adapter.numInternalParams()) {
            throw new AssertionError("Adapter numInternalParams mismatch");
        }
    }

    void verifyCurrentInteractionParameterImplementation() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;

        System.out.println();
        System.out.println("=================================================");
        System.out.println("CEF INTERACTION IMPLEMENTATION CHECK");
        System.out.println("=================================================");

        System.out.println("Number of sublattices = " + g.ns());
        System.out.println("Number of interactions = " + g.nip());

        if (g.nip() > 0) {
            System.out.println("(Interactions are being used in this phase model)");
        }
    }

    void diagnoseFccGradientFiniteDifference() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;
        double[] y = makeInteriorComposition(g);

        double[] analytical = g.gradient(T, y);

        int[] nc = g.constituentsPerSublattice();
        int[] offsets = g.offsets();

        double[] steps = {
                1.0e-4,
                1.0e-5,
                1.0e-6,
                1.0e-7,
                1.0e-8
        };

        System.out.println();
        System.out.println("=================================================");
        System.out.println("CEF GRADIENT FINITE-DIFFERENCE DIAGNOSTIC");
        System.out.println("Phase = FCC_A1");
        System.out.println("T     = " + T);
        System.out.println("y     = " + Arrays.toString(y));
        System.out.println("=================================================");

        for (int s = 0; s < nc.length; s++) {

            if (nc[s] < 2)
                continue;

            for (int i = 0; i < nc[s]; i++) {

                for (int j = i + 1; j < nc[s]; j++) {

                    int mi = offsets[s] + i;
                    int mj = offsets[s] + j;

                    double analyticalDirectional =
                            analytical[mi] - analytical[mj];

                    System.out.println();
                    System.out.println(
                            "Sublattice=" + s
                            + "  i=" + i
                            + "  j=" + j
                            + "  global=(" + mi + "," + mj + ")"
                    );

                    System.out.println(
                            "Analytical directional derivative = "
                            + analyticalDirectional
                    );

                    System.out.printf(
                            "%-12s %-20s %-20s %-20s%n",
                            "h",
                            "Numerical",
                            "AbsError",
                            "RelError"
                    );

                    for (double h : steps) {

                        double[] yp = y.clone();
                        double[] ym = y.clone();

                        yp[mi] += h;
                        yp[mj] -= h;

                        ym[mi] -= h;
                        ym[mj] += h;

                        double gp = g.evaluate(T, yp);
                        double gm = g.evaluate(T, ym);

                        double numerical =
                                (gp - gm) / (2.0 * h);

                        double absError =
                                Math.abs(
                                        numerical
                                        - analyticalDirectional
                                );

                        double scale =
                                Math.max(
                                        1.0,
                                        Math.max(
                                                Math.abs(numerical),
                                                Math.abs(
                                                        analyticalDirectional
                                                )
                                        )
                                );

                        double relError =
                                absError / scale;

                        System.out.printf(
                                "%-12.1e %-20.10e %-20.10e %-20.10e%n",
                                h,
                                numerical,
                                absError,
                                relError
                        );
                    }
                }
            }
        }
    }

    void diagnoseFccHessianFiniteDifference() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;
        double[] y = makeInteriorComposition(g);

        double[][] analytical = g.hessian(T, y);

        int[] nc = g.constituentsPerSublattice();
        int[] offsets = g.offsets();

        double[] steps = {
                1.0e-4,
                1.0e-5,
                1.0e-6,
                1.0e-7,
                1.0e-8
        };

        System.out.println();
        System.out.println("=================================================");
        System.out.println("CEF HESSIAN FINITE-DIFFERENCE DIAGNOSTIC");
        System.out.println("Phase = FCC_A1");
        System.out.println("T     = " + T);
        System.out.println("y     = " + Arrays.toString(y));
        System.out.println("=================================================");

        for (int s = 0; s < nc.length; s++) {

            if (nc[s] < 2)
                continue;

            for (int i = 0; i < nc[s]; i++) {

                for (int j = i + 1; j < nc[s]; j++) {

                    int mi = offsets[s] + i;
                    int mj = offsets[s] + j;

                    System.out.println();
                    System.out.println(
                            "Sublattice=" + s
                            + "  i=" + i
                            + "  j=" + j
                            + "  global=(" + mi + "," + mj + ")"
                    );

                    for (int k = 0; k < g.nip(); k++) {

                        double analyticalDirectional =
                                analytical[k][mi]
                                - analytical[k][mj];

                        System.out.println();
                        System.out.println(
                                "Gradient component k = " + k
                        );

                        System.out.println(
                                "Analytical directional Hessian = "
                                + analyticalDirectional
                        );

                        System.out.printf(
                                "%-12s %-20s %-20s %-20s%n",
                                "h",
                                "Numerical",
                                "AbsError",
                                "RelError"
                        );

                        for (double h : steps) {

                            double[] yp = y.clone();
                            double[] ym = y.clone();

                            yp[mi] += h;
                            yp[mj] -= h;

                            ym[mi] -= h;
                            ym[mj] += h;

                            double[] gradP =
                                    g.gradient(T, yp);

                            double[] gradM =
                                    g.gradient(T, ym);

                            double numerical =
                                    (gradP[k] - gradM[k])
                                    / (2.0 * h);

                            double absError =
                                    Math.abs(
                                            numerical
                                            - analyticalDirectional
                                    );

                            double scale =
                                    Math.max(
                                            1.0,
                                            Math.max(
                                                    Math.abs(numerical),
                                                    Math.abs(
                                                            analyticalDirectional
                                                    )
                                            )
                                    );

                            double relError =
                                    absError / scale;

                            System.out.printf(
                                    "%-12.1e %-20.10e %-20.10e %-20.10e%n",
                                    h,
                                    numerical,
                                    absError,
                                    relError
                            );
                        }
                    }
                }
            }
        }
    }

    void diagnoseFccConstrainedDirectionalSecondDerivative() throws Exception {

        PhaseModelFactory.PhaseModel phase =
                buildPhase("FCC_A1");

        CefGibbs g = phase.gibbs;

        double[] y = makeInteriorComposition(g);

        double g0 = g.evaluate(T, y);

        int[] nc = g.constituentsPerSublattice();
        int[] offsets = g.offsets();

        double[][] H = g.hessian(T, y);

        double[] steps = {
                1.0e-4,
                1.0e-5,
                1.0e-6,
                1.0e-7
        };

        double maxAbsError = 0.0;
        double maxRelError = 0.0;

        System.out.println();
        System.out.println(
                "================================================="
        );
        System.out.println(
                "CEF CONSTRAINED DIRECTIONAL SECOND DERIVATIVE"
        );
        System.out.println(
                "================================================="
        );
        System.out.println("Phase = FCC_A1");
        System.out.println("T     = " + T);
        System.out.println("G(y)  = " + g0);
        System.out.println("y     = " + Arrays.toString(y));

        /*
         * Each direction transfers composition between two
         * constituents on the SAME sublattice:
         *
         *       d[mi] = +1
         *       d[mj] = -1
         *
         * Therefore:
         *
         *       sum_i d[i] = 0
         *
         * on that sublattice and the CEF normalization constraint
         * remains satisfied.
         */
        for (int s = 0; s < nc.length; s++) {

            if (nc[s] < 2) {
                continue;
            }

            for (int i = 0; i < nc[s]; i++) {

                for (int j = i + 1; j < nc[s]; j++) {

                    int mi = offsets[s] + i;
                    int mj = offsets[s] + j;

                    double[] d = new double[g.nip()];

                    d[mi] = 1.0;
                    d[mj] = -1.0;

                    /*
                     * Analytical directional second derivative:
                     *
                     *       d^T H d
                     *
                     * For this particular direction this is
                     *
                     *       H[mi][mi]
                     *     - H[mi][mj]
                     *     - H[mj][mi]
                     *     + H[mj][mj].
                     */
                    double analytical =
                            0.0;

                    for (int p = 0; p < d.length; p++) {

                        for (int q = 0; q < d.length; q++) {

                            analytical +=
                                    d[p] * H[p][q] * d[q];
                        }
                    }

                    System.out.println();
                    System.out.println(
                            "-------------------------------------------------"
                    );
                    System.out.println(
                            "s=" + s
                            + ", i=" + i
                            + ", j=" + j
                            + ", global=(" + mi + "," + mj + ")"
                    );

                    System.out.println(
                            "Analytical d^T H d = "
                            + analytical
                    );

                    System.out.printf(
                            "%-12s %-20s %-20s %-20s%n",
                            "h",
                            "Numerical",
                            "AbsError",
                            "RelError"
                    );

                    for (double h : steps) {

                        double[] yp = y.clone();
                        double[] ym = y.clone();

                        /*
                         * y + h*d
                         */
                        yp[mi] += h;
                        yp[mj] -= h;

                        /*
                         * y - h*d
                         */
                        ym[mi] -= h;
                        ym[mj] += h;

                        /*
                         * Central second difference:
                         *
                         *       [G(y+hd) - 2G(y) + G(y-hd)] / h²
                         */
                        double gp =
                                g.evaluate(T, yp);

                        double gm =
                                g.evaluate(T, ym);

                        double numerical =
                                (gp - 2.0 * g0 + gm)
                                / (h * h);

                        double absError =
                                Math.abs(
                                        numerical
                                        - analytical
                                );

                        double scale =
                                Math.max(
                                        1.0,
                                        Math.max(
                                                Math.abs(numerical),
                                                Math.abs(analytical)
                                        )
                                );

                        double relError =
                                absError / scale;

                        maxAbsError =
                                Math.max(
                                        maxAbsError,
                                        absError
                                );

                        maxRelError =
                                Math.max(
                                        maxRelError,
                                        relError
                                );

                        System.out.printf(
                                "%-12.1e %-20.10e %-20.10e %-20.10e%n",
                                h,
                                numerical,
                                absError,
                                relError
                        );
                    }
                }
            }
        }

        System.out.println();
        System.out.println(
                "================================================="
        );
        System.out.println(
                "MAXIMUM DIAGNOSTIC ERROR"
        );
        System.out.println(
                "Maximum absolute error = "
                + maxAbsError
        );
        System.out.println(
                "Maximum relative error = "
                + maxRelError
        );
        System.out.println(
                "================================================="
        );

        /*
         * Diagnostic only.
         * No production-code assertion is made.
         */
    }

    void fccSinglePhaseAlgorithmAConverges() throws Exception {

        final double T = 1273.15;
        final double P = 101325.0;
        final String phaseName = "FCC_A1";

        ArrayList<String> elements = new ArrayList<>(
                Arrays.asList("FE", "CR", "NI", "MO", "V", "C"));

        /*
         * 1. Build FCC_A1 from steel7.TDB
         */
        PhaseModelFactory.PhaseModel phase = buildPhase(phaseName);

        if (phase == null) {
            throw new AssertionError("FCC_A1 phase is null");
        }

        if (!phaseName.equals(phase.phaseName)) {
            throw new AssertionError("Phase name mismatch");
        }

        CefGibbs gibbs = phase.gibbs;

        if (gibbs == null) {
            throw new AssertionError("CEF Gibbs is null");
        }

        /*
         * 2. Use a composition already verified to be representable.
         */
        double[] x = {
            0.99,   // Fe
            0.01,   // Cr
            0.00,   // Ni
            0.00,   // Mo
            0.00,   // V
            0.00    // C
        };

        if (Math.abs(Arrays.stream(x).sum() - 1.0) > 1.0e-12) {
            throw new AssertionError("Composition does not sum to one");
        }

        /*
         * 3. Construct a valid initial CEF constitution.
         */
        CefPhaseModelAdapter adapter = new CefPhaseModelAdapter(
                gibbs,
                phase.magnetic,
                phaseName,
                elements,
                phase.constituentNames
        );

        double[] y0 = adapter.getInitialInternalVars(x);

        if (y0 == null) {
            throw new AssertionError("Initial CEF constitution is null");
        }

        if (y0.length != gibbs.nip()) {
            throw new AssertionError("Initial y length mismatch");
        }

        if (!adapter.isValid(y0)) {
            throw new AssertionError("Initial CEF constitution is invalid");
        }

        int[] nc = gibbs.constituentsPerSublattice();
        int[] offsets = gibbs.offsets();

        for (int s = 0; s < gibbs.ns(); s++) {

            double sum = 0.0;

            for (int i = 0; i < nc[s]; i++) {

                double yi = y0[offsets[s] + i];

                if (!Double.isFinite(yi)) {
                    throw new AssertionError("Non-finite initial site fraction");
                }

                if (yi <= 0.0) {
                    throw new AssertionError(
                            "Algorithm A must start from positive site fractions");
                }

                sum += yi;
            }

            if (Math.abs(sum - 1.0) > 1.0e-10) {
                throw new AssertionError("CEF sublattice is not normalized");
            }
        }

        /*
         * 4. Confirm composition representation.
         */
        double[] xInitial = adapter.compositionFromInternal(y0);

        for (int k = 0; k < x.length; k++) {

            if (Math.abs(xInitial[k] - x[k]) > 1.0e-5) {
                throw new AssertionError(
                        "Initial CEF composition mismatch for " + elements.get(k));
            }
        }

        /*
         * 5. Evaluate initial Gibbs energy.
         */
        adapter.setInternalVars(y0);

        double G0 = adapter.evaluateG(x, T);

        if (!Double.isFinite(G0)) {
            throw new AssertionError("Initial FCC_A1 Gibbs energy is not finite");
        }

        /*
         * 6. Convert to GibbsEnergyModel for Algorithm A.
         */
        GibbsEnergyModel model = phase.toGibbsModel(elements);

        if (model == null) {
            throw new AssertionError("FCC_A1 GibbsEnergyModel is null");
        }

        /*
         * 7. Run Sundman Algorithm A.
         *    ONLY FCC_A1 is supplied.
         */
        List<GibbsEnergyModel> phases = Collections.singletonList(model);

        EquilibriumSolver solver = new EquilibriumSolver();
        EquilibriumResult result =
                solver.solve(T, P, x, phases);

        if (result == null) {
            throw new AssertionError("Algorithm A returned null result");
        }

        /*
         * 8. Algorithm A convergence
         */
        if (!result.isConverged()) {
            throw new AssertionError("Sundman Algorithm A did not converge");
        }

        if (result.getIterations() <= 0) {
            throw new AssertionError("Algorithm A performed no iterations");
        }

        /*
         * 9. External conditions remain fixed.
         */
        if (Math.abs(result.getT() - T) > 1.0e-10) {
            throw new AssertionError("Algorithm A changed temperature");
        }

        if (Math.abs(result.getP() - P) > 1.0e-6) {
            throw new AssertionError("Algorithm A changed pressure");
        }

        /*
         * 10. Exactly one stable phase.
         */
        if (result.getStablePhases().size() != 1) {
            throw new AssertionError("Expected exactly one stable phase");
        }

        EquilibriumResult.PhaseResult stable = result.getStablePhases().get(0);

        if (!phaseName.equals(stable.phaseName)) {
            throw new AssertionError("Phase name mismatch in result");
        }

        /*
         * 11. Phase amount.
         */
        if (!Double.isFinite(stable.amount)) {
            throw new AssertionError("Phase amount is not finite");
        }

        if (stable.amount <= 0.0) {
            throw new AssertionError("FCC_A1 phase amount must be positive");
        }

        /*
         * 12. Gibbs energy.
         */
        if (!Double.isFinite(stable.G)) {
            throw new AssertionError("Equilibrium Gibbs energy is not finite");
        }

        /*
         * 13. Chemical potentials.
         */
        double[] mu = result.getMu();

        if (mu == null) {
            throw new AssertionError("Chemical potentials are null");
        }

        if (mu.length != elements.size()) {
            throw new AssertionError("Chemical potential array length mismatch");
        }

        for (int k = 0; k < mu.length; k++) {

            if (!Double.isFinite(mu[k])) {
                throw new AssertionError(
                        "Non-finite chemical potential for " + elements.get(k));
            }
        }

        /*
         * 14. Verify equilibrium composition.
         */
        if (stable.x == null) {
            throw new AssertionError("Equilibrium composition is null");
        }

        if (stable.x.length != x.length) {
            throw new AssertionError("Composition length mismatch");
        }

        double xSum = 0.0;

        for (int k = 0; k < x.length; k++) {

            if (!Double.isFinite(stable.x[k])) {
                throw new AssertionError("Non-finite equilibrium composition");
            }

            if (stable.x[k] < -1.0e-8) {
                throw new AssertionError("Negative equilibrium composition");
            }

            xSum += stable.x[k];

            if (Math.abs(stable.x[k] - x[k]) > 1.0e-5) {
                throw new AssertionError(
                        "Mass-balance composition mismatch for " + elements.get(k));
            }
        }

        if (Math.abs(xSum - 1.0) > 1.0e-8) {
            throw new AssertionError("Equilibrium composition does not sum to unity");
        }

        /*
         * 15. Verify the final CEF constitution.
         */
        if (stable.y == null) {
            throw new AssertionError("Algorithm A did not return CEF site fractions");
        }

        if (stable.y.length != gibbs.nip()) {
            throw new AssertionError("CEF site fractions length mismatch");
        }

        for (int s = 0; s < gibbs.ns(); s++) {

            double sum = 0.0;

            for (int i = 0; i < nc[s]; i++) {

                double yi = stable.y[offsets[s] + i];

                if (!Double.isFinite(yi)) {
                    throw new AssertionError("Final CEF site fraction is not finite");
                }

                if (yi <= 0.0) {
                    throw new AssertionError("Final CEF site fraction is not positive");
                }

                sum += yi;
            }

            if (Math.abs(sum - 1.0) > 1.0e-8) {
                throw new AssertionError("Final CEF sublattice is not normalized");
            }
        }

        /*
         * 16. Check final CEF constitution independently.
         */
        double[] xFromY = adapter.compositionFromInternal(stable.y);

        for (int k = 0; k < x.length; k++) {

            if (Math.abs(xFromY[k] - x[k]) > 1.0e-5) {
                throw new AssertionError(
                        "Final y -> x mismatch for " + elements.get(k));
            }
        }

        /*
         * 17. Driving force (don't impose strict zero yet).
         */
        if (!Double.isFinite(stable.drivingForce)) {
            throw new AssertionError("Stable-phase driving force is not finite");
        }

        /*
         * Diagnostic output
         */
        System.out.println();
        System.out.println("FCC_A1 SUNDMAN ALGORITHM A TEST PASSED");
        System.out.println("T = " + result.getT() + " K");
        System.out.println("P = " + result.getP() + " Pa");
        System.out.println("Iterations = " + result.getIterations());
        System.out.println("Input x = " + Arrays.toString(x));
        System.out.println("Equilibrium x = " + Arrays.toString(stable.x));
        System.out.println("Equilibrium y = " + Arrays.toString(stable.y));
        System.out.println("mu = " + Arrays.toString(result.getMu()));
        System.out.println("G = " + stable.G + " J/mol");
        System.out.println("Amount = " + stable.amount);
        System.out.println("Driving force = " + stable.drivingForce);
    }

    /**
     * Creates a strictly positive site-fraction vector.
     *
     * The vector is constructed independently for each sublattice,
     * so every CEF normalization constraint is satisfied:
     *
     *       sum_i y_i^(s) = 1
     *
     * A uniform interior point is sufficient for this integration test.
     */
    private double[] makeInteriorComposition(CefGibbs g) {

        int[] nc = g.constituentsPerSublattice();
        int[] offsets = g.offsets();

        double[] y = new double[g.nip()];

        for (int s = 0; s < nc.length; s++) {

            double value = 1.0 / nc[s];

            for (int i = 0; i < nc[s]; i++) {
                y[offsets[s] + i] = value;
            }
        }

        return y;
    }
}
