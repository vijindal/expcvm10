package test;

import calc.equil.EquilibriumState;
import calc.equil.GridMinimizer;
import calc.equil.PhaseRecord;
import system.database.TdbParser;
import system.model.GibbsEnergyModel;
import system.model.cef.CefGibbs;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies {@link GridMinimizer} against reference values generated
 * directly from pycalphad 0.11.1, using the same {@code VZR-re2.TDB}
 * V-Zr system and (T, P, x_Zr) point as {@link CalculationSessionCalGTest}
 * (T = 1000 K, P = 10000 Pa, x_Zr = 1/3), so this test exercises the exact
 * initializer stage that test's "known limitation" comment identifies:
 * whether the solver's starting point lands near the true (ordered) global
 * minimum or a spurious disordered one.
 *
 * <p><b>Reference generation.</b> {@code VZR-re2.TDB}'s V2ZR is
 * (V,Zr)2(V,Zr), a single candidate phase for this test. The reference was
 * produced by pycalphad's own site-fraction grid sampler --
 * {@code pycalphad.core.calculate.calculate}, the same routine
 * {@code GridMinimizer.sampleSiteFractions} ports -- filtered to grid
 * points within 0.0015 of x_Zr=1/3, keeping the minimum-G point (the
 * pycalphad-side analogue of {@code GridMinimizer}'s lower-envelope +
 * facet search for a single candidate phase):
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
 * project's {@link PhaseRecord#G} (and {@code GridMinimizer}'s internal
 * lower-envelope/hull search) follows Sundman's convention of G per mole
 * of FORMULA UNIT. V2ZR has {@code nfu = 3} (site ratios 2+1), so the
 * reference below is {@code GM * nfu}: -50305.19850410267 * 3 =
 * -150915.595512308 J/mol f.u.
 *
 * <p>Independently, pycalphad's full {@code equilibrium()} Newton solve at
 * this exact condition converges to GM = -50295.197572439654 J/mol atoms
 * (-150885.59271731897 J/mol f.u.) with a similarly ordered constitution
 * (y &asymp; [0.994, 0.006, 0.012, 0.988]), confirming the grid optimum
 * above is close to (not merely one of many local optima near) the true
 * equilibrium -- i.e. this is what a correct grid initializer should hand
 * the Newton solver as a starting point.
 *
 * <p>This test does not assert bit-for-bit agreement with pycalphad's own
 * random/Halton-sampled grid (a different, though equally valid, sample of
 * the same energy surface would select a slightly different nearby point);
 * it asserts the two properties that matter for solver correctness:
 *
 * <ol>
 *   <li>the selected constitution is ORDERED (dominated by the V:Zr
 *       end-member), not the strongly disordered guess a naive
 *       composition-only seed would produce;</li>
 *   <li>the selected point's Gibbs energy is close to the pycalphad
 *       reference optimum, i.e. within the grid-density-limited tolerance
 *       below, not off by the tens-of-kJ margin that a disordered guess
 *       would produce.</li>
 * </ol>
 */
public class GridMinimizerPycalphadTest {

    private static final String TDB = "data/VZR-re2.TDB";
    private static final String PHASE = "V2ZR";

    private static final double T = 1000.0;
    private static final double P = 10000.0;
    private static final double X_ZR = 1.0 / 3.0;

    /** V2ZR site ratios are 2+1 -- pycalphad's per-atom GM * nfu = per f.u. G. */
    private static final double NFU = 3.0;

    /**
     * pycalphad grid optimum near x_Zr=1/3 (see class javadoc), converted
     * from GM [J/mol atoms] to this project's G [J/mol formula unit].
     */
    private static final double PYCALPHAD_GRID_G = -50305.19850410267 * NFU;

    /**
     * pycalphad full-Newton equilibrium GM at this exact condition,
     * likewise converted to J/mol formula unit.
     */
    private static final double PYCALPHAD_EQUILIBRIUM_G = -50295.197572439654 * NFU;

    /*
     * Grid density differs between the Java port (endmembers + edges +
     * Halton interior points, generated independently of pycalphad's own
     * random draw) and the reference grid, so exact agreement isn't
     * expected -- only proximity to the true optimum. 600 J/mol f.u.
     * (200 J/mol atoms * nfu) covers the observed gap between the grid
     * optimum and the fully converged value while remaining two orders of
     * magnitude tighter than the >300,000 J/mol f.u. gap a disordered
     * starting guess produces.
     */
    private static final double G_TOLERANCE = 200.0 * NFU;

    public static void main(String[] args) throws Exception {

        System.out.println("=== GridMinimizerPycalphadTest ===");
        System.out.println("V2ZR grid initializer at T=1000K, P=10000Pa, "
                + "x_Zr=1/3, vs. pycalphad 0.11.1 reference");
        System.out.println();

        TdbParser parser = new TdbParser();
        parser.load(TDB);

        List<String> elements = Arrays.asList("V", "ZR");
        List<String> phaseNames = Arrays.asList(PHASE);

        @SuppressWarnings("unchecked")
        List<CefGibbs> raw =
                (List<CefGibbs>) parser.buildPhaseModels(elements, phaseNames);

        require(raw.size() == 1,
                "Expected exactly one candidate phase, got " + raw.size());

        List<GibbsEnergyModel> candidates =
                Arrays.asList((GibbsEnergyModel) raw.get(0));

        double[] xOverall = {1.0 - X_ZR, X_ZR};

        GridMinimizer gridMinimizer = new GridMinimizer();
        EquilibriumState state =
                gridMinimizer.initialize(candidates, T, P, xOverall);

        List<PhaseRecord> stable = state.stablePhases();

        require(stable.size() == 1,
                "Single-candidate system must select exactly one stable "
                + "phase, got " + stable.size());

        PhaseRecord pr = stable.get(0);

        require(PHASE.equals(pr.phaseName()),
                "Expected stable phase " + PHASE + ", got " + pr.phaseName());

        double[] y = pr.y;

        System.out.println("Selected y = " + Arrays.toString(y));
        System.out.println("Selected G = " + pr.G);
        System.out.println("pycalphad grid G        = " + PYCALPHAD_GRID_G);
        System.out.println("pycalphad equilibrium G  = " + PYCALPHAD_EQUILIBRIUM_G);
        System.out.println();

        // ------------------------------------------------------------
        // Property 1: the constitution must be ORDERED -- V on
        // sublattice 1, Zr on sublattice 2 -- not a near-uniform
        // disordered split. V2ZR's sublattice layout here is
        // (V,Zr)2(V,Zr): y = [y_V^1, y_Zr^1, y_V^2, y_Zr^2].
        // ------------------------------------------------------------

        require(y[0] > 0.9,
                "Sublattice 1 must be dominated by V (ordered "
                + "V:Zr end member); got y_V^1 = " + y[0]);

        require(y[3] > 0.9,
                "Sublattice 2 must be dominated by Zr (ordered "
                + "V:Zr end member); got y_Zr^2 = " + y[3]);

        // ------------------------------------------------------------
        // Property 2: G must be close to the pycalphad reference
        // optimum, not off by the disordered-guess-sized margin.
        // ------------------------------------------------------------

        double diffFromGrid = Math.abs(pr.G - PYCALPHAD_GRID_G);
        double diffFromEquilibrium = Math.abs(pr.G - PYCALPHAD_EQUILIBRIUM_G);

        require(diffFromGrid < G_TOLERANCE,
                "Selected G=" + pr.G + " differs from pycalphad grid "
                + "reference " + PYCALPHAD_GRID_G + " by " + diffFromGrid
                + " J/mol, exceeding tolerance " + G_TOLERANCE);

        require(diffFromEquilibrium < G_TOLERANCE,
                "Selected G=" + pr.G + " differs from pycalphad "
                + "equilibrium reference " + PYCALPHAD_EQUILIBRIUM_G
                + " by " + diffFromEquilibrium + " J/mol, exceeding "
                + "tolerance " + G_TOLERANCE);

        System.out.println("GridMinimizerPycalphadTest PASSED.");
        System.out.println("  |G - grid ref|        = " + diffFromGrid + " J/mol");
        System.out.println("  |G - equilibrium ref| = " + diffFromEquilibrium + " J/mol");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
