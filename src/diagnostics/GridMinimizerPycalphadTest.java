package diagnostics;

import session.CalculationSession;
import system.ports.EquilibriumResult;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies {@code calc.equil.GridMinimizer} against reference values
 * generated directly from pycalphad 0.11.1, routed through
 * {@link CalculationSession#calculateInitialState} -- the project's single
 * UI-agnostic coordinator (see {@code README.md}'s "Structure": every
 * calculation is dispatched through {@code CalculationSession}, not by
 * reaching into {@code calc/equil} classes directly). This exercises the
 * SAME code path a GUI/CLI/API caller would use to obtain a starting
 * point, not {@code GridMinimizer} in isolation.
 *
 * <p>Two cases, of increasing landscape complexity:
 *
 * <ul>
 *   <li><b>Case A -- V2ZR</b> (V-Zr, {@code data/VZR-re2.TDB}): a binary,
 *       2-sublattice, single-candidate-phase system, (V,Zr)2(V,Zr),
 *       4 internal degrees of freedom. This is the exact initializer stage
 *       {@link CalculationSessionCalGTest}'s "known limitation" comment
 *       identifies: whether the solver's starting point lands near the
 *       true (ordered) global minimum or a spurious disordered one.</li>
 *   <li><b>Case B -- CHI_A12</b> (Cr-Fe-Mo, {@code data/steel1.TDB}): a
 *       ternary, 3-sublattice system, site ratios 24:10:24
 *       ({@code nfu = 58}), constituent layout (Cr,Fe)(Cr,Mo)(Cr,Fe,Mo),
 *       7 internal degrees of freedom -- a genuinely more complicated
 *       energy landscape than V2ZR's. At the tested interior composition
 *       CHI_A12's own tangent hyperplane is approximated by THREE distinct
 *       site-fraction vertices of the SAME phase (not a miscibility gap
 *       between different phases -- there is only one candidate here).
 *       {@code GridMinimizer} (like pycalphad's own
 *       {@code lower_convex_hull}) does not collapse same-phase hyperplane
 *       vertices into one point; it returns all three, each with its own
 *       site fractions and lever-rule amount, exactly reconstructing the
 *       target composition as their weighted mixture. This is precisely
 *       the case that exposed a real merge bug during development
 *       ({@code GridMinimizer.buildState} previously collapsed same-phase
 *       vertices down to a single "best" point, silently returning a phase
 *       at the WRONG composition entirely: x=[0.249,0.544,0.208] instead
 *       of the target [0.3,0.5,0.2]) -- this test's composition-
 *       reconstruction check guards directly against that regression.</li>
 * </ul>
 *
 * <p>Both cases assert the properties that matter for solver correctness,
 * not bit-for-bit agreement with pycalphad's own random/Halton-sampled
 * grid (a different, though equally valid, sample of the same energy
 * surface would select slightly different nearby points):
 *
 * <ol>
 *   <li>the mixture's amount-weighted composition reconstructs
 *       {@code xOverall} exactly (the lever rule/barycentric coordinates
 *       are self-consistent);</li>
 *   <li>the dominant (largest-amount) vertex's constitution is ORDERED
 *       where the true ground state is ordered (V2ZR only -- CHI_A12's
 *       true minimum here is a genuine 3-way mixture, not one ordered
 *       end-member, so this check does not apply to Case B);</li>
 *   <li>the mixture's amount-weighted Gibbs energy is close to BOTH
 *       pycalphad's own grid-hyperplane optimum and its fully converged
 *       equilibrium, within a tolerance far tighter than the
 *       wrong-composition/disordered-guess errors this class of bug
 *       produces.</li>
 * </ol>
 */
public class GridMinimizerPycalphadTest {

    public static void main(String[] args) throws Exception {
        runV2zrCase();
        System.out.println();
        runChiA12Case();
    }

    // ====================================================================
    // Case A -- V2ZR (V-Zr, binary, 2-sublattice, single candidate)
    // ====================================================================

    /**
     * <b>Reference generation.</b> {@code VZR-re2.TDB}'s V2ZR is
     * (V,Zr)2(V,Zr). The reference was produced by pycalphad's own
     * site-fraction grid sampler -- {@code pycalphad.core.calculate.calculate},
     * the same routine {@code GridMinimizer.sampleSiteFractions} ports --
     * filtered to grid points within 0.0015 of x_Zr=1/3, keeping the
     * minimum-G point (the pycalphad-side analogue of
     * {@code GridMinimizer}'s lower-envelope + hyperplane search for a
     * single candidate phase):
     *
     * <pre>
     * from pycalphad import Database, calculate
     * import numpy as np
     *
     * db = Database('data/VZR-re2.TDB')
     * grid = calculate(db, ['V', 'ZR', 'VA'], ['V2ZR'], T=1000.0, P=10000.0,
     *                   pdens=2000, output='GM')
     * X, GM, Y = (grid.X.values.squeeze(), grid.GM.values.squeeze(),
     *             grid.Y.values.squeeze())
     * mask = np.abs(X[:, 1] - 1/3) &lt; 0.0015
     * best = np.where(mask)[0][np.argmin(GM[mask])]
     * # GM (pycalphad convention, J/mol ATOMS) = -50305.19850410267
     * # X = [0.66533267, 0.33466733]
     * # Y = [0.995998, 0.004002, 0.004002, 0.995998]
     * </pre>
     *
     * <p>pycalphad's {@code GM} is Gibbs energy per mole of ATOMS; this
     * project's {@code PhaseRecord.G} follows Sundman's convention of G
     * per mole of FORMULA UNIT. V2ZR has {@code nfu = 3} (site ratios
     * 2+1), so the reference below is {@code GM * nfu}:
     * -50305.19850410267 * 3 = -150915.595512308 J/mol f.u.
     *
     * <p>Independently, pycalphad's full {@code equilibrium()} Newton
     * solve at this exact condition converges to GM = -50295.197572439654
     * J/mol atoms (-150885.59271731897 J/mol f.u.) with a similarly
     * ordered constitution (y &asymp; [0.994, 0.006, 0.012, 0.988]),
     * confirming the grid optimum above is close to (not merely one of
     * many local optima near) the true equilibrium.
     */
    private static void runV2zrCase() throws Exception {

        String tdb = "data/VZR-re2.TDB";
        String phase = "V2ZR";
        double T = 1000.0;
        double P = 10000.0;
        double xZr = 1.0 / 3.0;
        double[] xOverall = {1.0 - xZr, xZr};

        double nfu = 3.0;
        double pycalphadGridG = -50305.19850410267 * nfu;
        double pycalphadEquilibriumG = -50295.197572439654 * nfu;

        /*
         * Grid density differs between the Java port (endmembers + edges
         * + Halton interior points, generated independently of
         * pycalphad's own random draw) and the reference grid, so exact
         * agreement isn't expected -- only proximity to the true optimum.
         * 600 J/mol f.u. (200 J/mol atoms * nfu) covers the observed gap
         * between the grid optimum and the fully converged value while
         * remaining two orders of magnitude tighter than the >300,000
         * J/mol f.u. gap a disordered starting guess produces.
         */
        double gTolerance = 200.0 * nfu;

        System.out.println("=== Case A: V2ZR (V-Zr, binary) ===");
        System.out.println("T=1000K, P=10000Pa, x_Zr=1/3, vs. pycalphad "
                + "0.11.1 reference");
        System.out.println();

        CalculationSession session = new CalculationSession();
        session.setModel(tdb, Arrays.asList("V", "ZR"), Arrays.asList(phase));
        session.calculateInitialState(T, P, xOverall);

        EquilibriumResult result = session.currentInitialState();
        require(result != null, "calculateInitialState() produced no result.");

        List<EquilibriumResult.PhaseResult> stable = result.getStablePhases();
        require(!stable.isEmpty(), "GridMinimizer returned no stable phases.");

        Mixture mix = checkMixtureReconstructsComposition(
                stable, xOverall, phase, "Case A (V2ZR)");

        // ------------------------------------------------------------
        // The dominant vertex's constitution must be ORDERED -- V on
        // sublattice 1, Zr on sublattice 2 -- not a near-uniform
        // disordered split. V2ZR's sublattice layout here is
        // (V,Zr)2(V,Zr): y = [y_V^1, y_Zr^1, y_V^2, y_Zr^2].
        // ------------------------------------------------------------

        double[] y = mix.dominant.y;

        require(y[0] > 0.9,
                "Dominant vertex's sublattice 1 must be dominated by V "
                + "(ordered V:Zr end member); got y_V^1 = " + y[0]);

        require(y[3] > 0.9,
                "Dominant vertex's sublattice 2 must be dominated by Zr "
                + "(ordered V:Zr end member); got y_Zr^2 = " + y[3]);

        checkWeightedEnergy(mix.weightedG, pycalphadGridG, pycalphadEquilibriumG,
                gTolerance, "Case A (V2ZR)");

        System.out.println("Case A (V2ZR) PASSED.");
    }

    // ====================================================================
    // Case B -- CHI_A12 (Cr-Fe-Mo, ternary, 3-sublattice, single candidate)
    // ====================================================================

    /**
     * <b>Reference generation.</b> {@code data/steel1.TDB}'s CHI_A12 is
     * (Cr,Fe)(Cr,Mo)(Cr,Fe,Mo), site ratios 24:10:24, {@code nfu = 58}.
     * pycalphad 0.11.1, the same algorithm this project's
     * {@code Hyperplane.solve} ports (see {@code HyperplanePortTest} for a
     * direct, same-grid verification of the ported algorithm to ~1e-11 J):
     *
     * <pre>
     * from pycalphad import Database, calculate, equilibrium, variables as v
     * from pycalphad.core.hyperplane import hyperplane
     * import numpy as np
     *
     * db = Database('data/steel1.TDB')
     * comps = ['CR', 'FE', 'MO', 'VA']
     * phases = ['CHI_A12']
     * T, P = 1200.0, 101325.0
     * target = np.array([0.3, 0.5, 0.2])  # xCr, xFe, xMo
     *
     * grid = calculate(db, comps, phases, T=T, P=P, pdens=2000, output='GM')
     * X, GM, Y = (np.ascontiguousarray(grid.X.values.squeeze()),
     *             np.ascontiguousarray(grid.GM.values.squeeze()),
     *             grid.Y.values.squeeze())
     *
     * # Reorder columns so the implied component (Fe) is last: [Cr, Mo, Fe]
     * Xr = np.ascontiguousarray(X[:, [0, 2, 1]])
     * coefs = np.array([[1,0,0],[0,1,0]], dtype=float)  # xCr, xMo rows
     * rhs = np.array([target[0], target[2]])
     * chempots = np.zeros(3)
     * fractions = np.zeros(4)
     * simplex = np.zeros(4, dtype=np.int32)
     * energy = hyperplane(Xr, GM, chempots, np.array([], dtype=np.uintp),
     *                      coefs, rhs, fractions, simplex)
     * # energy (GM, J/mol ATOMS) = -59844.08228367277
     * # fractions = [0.42803677, 0.22557801, 0.34638522]
     * # vertex X (Cr,Fe,Mo) = [0.31623593, 0.47162996, 0.21213411], GM=-59641.39119937976
     * # vertex X (Cr,Fe,Mo) = [0.34813859, 0.4865597 , 0.16530171], GM=-59703.86448098611
     * # vertex X (Cr,Fe,Mo) = [0.24858736, 0.54381034, 0.2076023 ], GM=-60185.86733327327
     *
     * eq = equilibrium(db, comps, phases, {v.T: T, v.P: P, v.X('CR'): target[0],
     *                                       v.X('MO'): target[2], v.N: 1})
     * # full-equilibrium GM = -59983.015024668966, X = [0.3, 0.5, 0.2] exactly
     * </pre>
     *
     * <p>pycalphad's {@code GM} is per mole of ATOMS; this project's
     * {@code PhaseRecord.G} is per mole of FORMULA UNIT. CHI_A12 has
     * {@code nfu = 58}, so every reference below is the pycalphad GM
     * value times 58.
     */
    private static void runChiA12Case() throws Exception {

        String tdb = "data/steel1.TDB";
        String phase = "CHI_A12";
        double T = 1200.0;
        double P = 101325.0;
        double[] xOverall = {0.3, 0.5, 0.2}; // xCr, xFe, xMo

        double nfu = 58.0;
        double pycalphadGridG = -59844.08228367277 * nfu;
        double pycalphadEquilibriumG = -59983.015024668966 * nfu;

        /*
         * The grid optimum and the full equilibrium differ by ~139 J/mol
         * atoms (~8060 J/mol f.u.) here -- a much harder 7-DOF landscape
         * than V2ZR's 4-DOF binary, where the gap was ~10 J/mol atoms.
         * 500 J/mol atoms (29000 J/mol f.u.) covers both references with
         * margin while remaining two orders of magnitude tighter than
         * the >1,000,000 J/mol f.u. gap a single-collapsed-vertex bug
         * produced during development (a phase at the wrong composition
         * entirely).
         */
        double gTolerance = 500.0 * nfu;

        System.out.println("=== Case B: CHI_A12 (Cr-Fe-Mo, ternary) ===");
        System.out.println("T=1200K, P=101325Pa, x=(Cr=0.3,Fe=0.5,Mo=0.2), "
                + "vs. pycalphad 0.11.1 reference");
        System.out.println();

        CalculationSession session = new CalculationSession();
        session.setModel(tdb, Arrays.asList("CR", "FE", "MO"), Arrays.asList(phase));
        session.calculateInitialState(T, P, xOverall);

        EquilibriumResult result = session.currentInitialState();
        require(result != null, "calculateInitialState() produced no result.");

        List<EquilibriumResult.PhaseResult> stable = result.getStablePhases();
        require(!stable.isEmpty(), "GridMinimizer returned no stable phases.");

        Mixture mix = checkMixtureReconstructsComposition(
                stable, xOverall, phase, "Case B (CHI_A12)");

        checkWeightedEnergy(mix.weightedG, pycalphadGridG, pycalphadEquilibriumG,
                gTolerance, "Case B (CHI_A12)");

        System.out.println("Case B (CHI_A12) PASSED.");
    }

    // ====================================================================
    // Shared verification helpers
    // ====================================================================

    private static final class Mixture {
        final double weightedG;
        final EquilibriumResult.PhaseResult dominant;

        Mixture(double weightedG, EquilibriumResult.PhaseResult dominant) {
            this.weightedG = weightedG;
            this.dominant = dominant;
        }
    }

    /**
     * Checks that the stable-phase amounts sum to 1 and their
     * amount-weighted composition reconstructs {@code xOverall} exactly
     * -- the regression guard against the same-phase-vertex merge bug
     * found during development (collapsing multiple hyperplane vertices
     * of one phase into a single "best" point silently returns a phase
     * at the WRONG composition entirely).
     */
    private static Mixture checkMixtureReconstructsComposition(
            List<EquilibriumResult.PhaseResult> stable,
            double[] xOverall, String expectedPhase, String label) {

        double totalAmount = 0.0;
        double weightedG = 0.0;
        double[] weightedX = new double[xOverall.length];
        EquilibriumResult.PhaseResult dominant = stable.get(0);

        for (EquilibriumResult.PhaseResult pr : stable) {
            require(expectedPhase.equals(pr.phaseName),
                    label + ": expected stable phase " + expectedPhase
                    + ", got " + pr.phaseName);

            totalAmount += pr.amount;
            weightedG += pr.amount * pr.G;
            for (int i = 0; i < xOverall.length; i++) {
                weightedX[i] += pr.amount * pr.x[i];
            }
            if (pr.amount > dominant.amount) {
                dominant = pr;
            }

            System.out.println(label + " vertex: amount=" + pr.amount
                    + " x=" + Arrays.toString(pr.x)
                    + " G=" + pr.G);
        }

        System.out.println(label + " weighted G = " + weightedG);
        System.out.println(label + " weighted X = " + Arrays.toString(weightedX));

        require(Math.abs(totalAmount - 1.0) < 1e-9,
                label + ": stable-phase amounts must sum to 1, got "
                + totalAmount);

        double compositionTol = 1.0e-6;
        for (int i = 0; i < xOverall.length; i++) {
            double diff = Math.abs(weightedX[i] - xOverall[i]);
            require(diff < compositionTol,
                    label + ": weighted composition[" + i + "]=" + weightedX[i]
                    + " does not match target xOverall[" + i + "]="
                    + xOverall[i] + " (diff=" + diff + ") -- this is "
                    + "exactly the same-phase-vertex merge bug found "
                    + "during development if it reproduces.");
        }

        return new Mixture(weightedG, dominant);
    }

    /**
     * Checks the mixture's weighted G against both pycalphad references
     * (grid optimum and full equilibrium).
     */
    private static void checkWeightedEnergy(double weightedG,
                                             double pycalphadGridG,
                                             double pycalphadEquilibriumG,
                                             double tolerance, String label) {

        double diffFromGrid = Math.abs(weightedG - pycalphadGridG);
        double diffFromEquilibrium = Math.abs(weightedG - pycalphadEquilibriumG);

        System.out.println(label + " pycalphad grid G       = " + pycalphadGridG);
        System.out.println(label + " pycalphad equilibrium G = " + pycalphadEquilibriumG);
        System.out.println(label + " |G - grid ref|        = " + diffFromGrid);
        System.out.println(label + " |G - equilibrium ref| = " + diffFromEquilibrium);

        require(diffFromGrid < tolerance,
                label + ": weighted G=" + weightedG + " differs from "
                + "pycalphad grid reference " + pycalphadGridG + " by "
                + diffFromGrid + " J/mol f.u., exceeding tolerance " + tolerance);

        require(diffFromEquilibrium < tolerance,
                label + ": weighted G=" + weightedG + " differs from "
                + "pycalphad equilibrium reference " + pycalphadEquilibriumG
                + " by " + diffFromEquilibrium + " J/mol f.u., exceeding "
                + "tolerance " + tolerance);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
