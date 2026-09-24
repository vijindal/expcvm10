package calc.equil;

import util.Matrix;

/**
 * Finds the tangent hyperplane (Gibbs-energy simplex/facet) enclosing a
 * target composition, given a sample of the energy surface.
 *
 * <p>Direct port of pycalphad's {@code pycalphad.core.hyperplane.hyperplane}
 * for the fixed-mole-fraction case this project supports (no fixed chemical
 * potentials, no phase-local conditions): given {@code M} sampled
 * {@code (composition, energy)} points spanning an {@code N}-component
 * system and {@code N-1} independent target mole fractions (the
 * {@code N}-th is implied by summing to 1), finds the {@code N}-point
 * simplex whose barycentric coordinates for the target composition are all
 * non-negative and whose associated chemical potentials place every other
 * sampled point on or above the resulting hyperplane -- i.e. the lower
 * convex-hull facet containing the target point.
 *
 * <p>The method is NOT a QuickHull-style geometric convex hull; it is a
 * Dantzig-style simplex pivot (linear programming) directly on the
 * candidate points: start from an arbitrary {@code N}-point simplex,
 * compute the hyperplane (chemical potentials) through it, find the
 * sampled point with the most negative driving force below that
 * hyperplane, build {@code N} trial simplices (the current simplex with
 * each vertex in turn replaced by that point), pick whichever trial
 * simplex has the target composition's least-negative barycentric
 * coordinate, and repeat until no point has negative driving force. This
 * generalizes directly to any number of components, unlike a
 * low-dimension-specific convex-hull construction.
 */
public final class Hyperplane {

    private Hyperplane() { }

    /** Result of a hyperplane search. */
    public static final class Result {
        /** Energy of the target composition on the found hyperplane. */
        public final double energy;
        /** Chemical potentials (one per component). */
        public final double[] chemicalPotentials;
        /** Indices (into the input arrays) of the simplex vertices. */
        public final int[] simplex;
        /** Barycentric fractions of the target point within that simplex. */
        public final double[] fractions;

        public Result(double energy, double[] chemicalPotentials,
               int[] simplex, double[] fractions) {
            this.energy = energy;
            this.chemicalPotentials = chemicalPotentials;
            this.simplex = simplex;
            this.fractions = fractions;
        }
    }

    private static final int MAX_ITERATIONS = 1000;

    /**
     * Finds the lower-hull simplex containing {@code targetMoleFractions}.
     *
     * @param compositions        sampled points' compositions, shape
     *                            [numPoints][numComponents]
     * @param energies            sampled points' energies, length
     *                            numPoints, aligned with {@code compositions}
     * @param targetMoleFractions independent mole-fraction targets, length
     *                            numComponents-1 (components 0..n-2; the
     *                            last component's fraction is implied)
     * @return the found hyperplane
     */
    public static Result solve(double[][] compositions, double[] energies,
                         double[] targetMoleFractions) {

        int numPoints = compositions.length;
        int numComponents = compositions[0].length;
        int simplexSize = numComponents;

        if (targetMoleFractions.length != numComponents - 1) {
            throw new IllegalArgumentException(
                    "Expected " + (numComponents - 1)
                    + " independent mole-fraction targets for "
                    + numComponents + " components, got "
                    + targetMoleFractions.length);
        }

        // Linear mole-fraction constraints: component i's mole fraction
        // equals targetMoleFractions[i], for i = 0..numComponents-2, plus
        // an implicit "coordinates sum to 1" row supplied inside
        // simplexFractions (mirroring pycalphad's intersecting_point,
        // which appends the hyperplane-membership row itself).
        double[][] constraintCoefs = new double[numComponents - 1][numComponents];
        double[] constraintRhs = new double[numComponents - 1];
        for (int i = 0; i < numComponents - 1; i++) {
            constraintCoefs[i][i] = 1.0;
            constraintRhs[i] = targetMoleFractions[i];
        }

        int[] bestGuessSimplex = selectWellConditionedSeed(
                compositions, numPoints, simplexSize, constraintCoefs, constraintRhs);

        int[][] trialSimplices = new int[simplexSize][simplexSize];
        for (int i = 0; i < simplexSize; i++) {
            trialSimplices[i] = bestGuessSimplex.clone();
        }

        double[] candidatePotentials = new double[simplexSize];
        int savedTrial = 0;
        double[][] fractions = new double[simplexSize][simplexSize];

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {

            double[] smallestFractions = new double[simplexSize];

            for (int trial = 0; trial < simplexSize; trial++) {
                double[] frac = simplexFractions(
                        compositions, trialSimplices[trial],
                        constraintCoefs, constraintRhs);
                fractions[trial] = frac;
                smallestFractions[trial] = min(frac);
            }

            savedTrial = argmax(smallestFractions);

            if (smallestFractions[savedTrial] < -simplexSize) {
                break;
            }

            int[] candidateSimplex = trialSimplices[savedTrial].clone();

            // Solve for chemical potentials through candidateSimplex:
            // energies[idx] = sum_j chempot[j] * compositions[idx][j]
            double[][] tieline = new double[simplexSize][simplexSize];
            double[] rhs = new double[simplexSize];
            for (int i = 0; i < simplexSize; i++) {
                int idx = candidateSimplex[i];
                for (int j = 0; j < simplexSize; j++) {
                    tieline[i][j] = compositions[idx][j];
                }
                rhs[i] = energies[idx];
            }

            double[] solved = solveLinearSystem(tieline, rhs);
            if (solved[0] == SINGULAR_SENTINEL) {
                break; // singular tieline matrix -- stop, matching pycalphad
            }
            candidatePotentials = solved;

            // Driving force at every sampled point under this hyperplane.
            double[] drivingForces = new double[numPoints];
            for (int idx = 0; idx < numPoints; idx++) {
                double df = energies[idx];
                for (int j = 0; j < simplexSize; j++) {
                    df -= candidatePotentials[j] * compositions[idx][j];
                }
                drivingForces[idx] = df;
            }

            bestGuessSimplex = candidateSimplex.clone();

            int minDfIdx = argminArray(drivingForces);
            double lowestDf = drivingForces[minDfIdx];

            // Next iteration's trial simplices: current simplex with each
            // vertex in turn replaced by the point of lowest driving force.
            for (int trial = 0; trial < simplexSize; trial++) {
                trialSimplices[trial] = bestGuessSimplex.clone();
                trialSimplices[trial][trial] = minDfIdx;
            }

            if (lowestDf > -1e-8) {
                break;
            }
        }

        double outEnergy = 0.0;
        for (int i = 0; i < simplexSize; i++) {
            int idx = bestGuessSimplex[i];
            outEnergy += fractions[savedTrial][i] * energies[idx];
        }

        return new Result(outEnergy, candidatePotentials,
                bestGuessSimplex.clone(), fractions[savedTrial].clone());
    }

    /**
     * Chooses the initial pivot-search seed simplex: the same
     * {@code {0, 1, ..., simplexSize-1}} input-order seed used
     * unconditionally before, but with any vertex that would leave the
     * seed degenerate replaced by the next not-yet-included sampled point.
     *
     * <p>The seed simplex is the one piece of {@link #solve} that is never
     * validated before use -- every later trial simplex is filtered
     * through the pivot loop's own {@code smallestFractions[savedTrial]
     * < -simplexSize} degeneracy check ({@code solve}, a few lines above
     * the pivot loop) before it is trusted, but the seed feeds directly
     * into the first iteration's {@code simplexFractions} call without
     * ever passing that same check itself. If the seed happens to be
     * near-duplicate (or otherwise near-collinear) points, that first call
     * solves a near-singular system that {@link util.Matrix#solve} does
     * not reject (its LU pivot test is an exact {@code == 0.0}, not a
     * tolerance), producing enormous but finite barycentric fractions --
     * which then satisfy that very same {@code < -simplexSize} check on
     * the seed itself, so the pivot loop exits at iteration 0 reporting
     * the unrefined seed, before a single pivot ever runs.
     *
     * <p>This helper closes that gap by applying the pivot loop's own
     * {@code simplexFractions} / {@code < -simplexSize} degeneracy test to
     * the seed up front, using the exact same convention the pivot loop
     * already trusts elsewhere, rather than inventing a separate
     * conditioning criterion.
     */
    private static int[] selectWellConditionedSeed(double[][] compositions,
                                                     int numPoints,
                                                     int simplexSize,
                                                     double[][] constraintCoefs,
                                                     double[] constraintRhs) {

        int[] seed = new int[simplexSize];
        for (int i = 0; i < simplexSize; i++) {
            seed[i] = i;
        }

        if (isNondegenerateSimplex(compositions, seed, simplexSize, constraintCoefs, constraintRhs)) {
            return seed;
        }

        // The default seed is degenerate. Repair it one vertex at a time --
        // the same index-by-index vertex-replacement order the pivot loop
        // itself already uses to build trial simplices -- but, for each
        // vertex, scan the remaining not-yet-included sampled points and
        // keep whichever single replacement yields the LARGEST min(fractions)
        // for the current seed (the same argmax(smallestFractions) standard
        // the pivot loop already applies when choosing among its own trial
        // simplices), not merely the first replacement that clears the
        // degeneracy bound. A replacement that only barely clears
        // "non-degenerate" can still be a poor bracketing choice that
        // strands the pivot loop a few iterations later; scanning for the
        // best available vertex (bounded by the remaining candidate points,
        // so still O(numPoints) per vertex) makes the repaired seed a much
        // more reliable starting point without touching the pivot loop
        // itself. This is a local repair of the seed only; it does not
        // sort, deduplicate, or otherwise touch the rest of the point cloud.
        for (int vertex = 0; vertex < simplexSize; vertex++) {
            int original = seed[vertex];
            int bestCandidate = original;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (int candidate = 0; candidate < numPoints; candidate++) {
                if (containsIndex(seed, candidate) && candidate != original) {
                    continue; // already used by another seed vertex
                }
                seed[vertex] = candidate;
                double[] frac = simplexFractions(compositions, seed, constraintCoefs, constraintRhs);
                double score = min(frac);
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = candidate;
                }
            }

            seed[vertex] = bestCandidate;
        }

        return seed;
    }

    /**
     * True if {@code simplex}'s own barycentric fractions for the target
     * composition -- the exact quantity {@code simplexFractions} computes
     * for every trial simplex inside the pivot loop -- pass the pivot
     * loop's existing degeneracy convention ({@code min(fractions) >=
     * -simplexSize}, mirroring the {@code smallestFractions[savedTrial]
     * < -simplexSize} early-exit in {@link #solve}). Reusing that exact
     * check here, rather than a separate geometric tolerance, means a
     * candidate seed is judged degenerate by precisely the same standard
     * the pivot loop already applies to every other simplex it considers.
     */
    private static boolean isNondegenerateSimplex(double[][] compositions,
                                                    int[] simplex,
                                                    int simplexSize,
                                                    double[][] constraintCoefs,
                                                    double[] constraintRhs) {

        double[] frac = simplexFractions(compositions, simplex, constraintCoefs, constraintRhs);
        return min(frac) >= -simplexSize;
    }

    /**
     * Direct port of pycalphad's {@code simplex_fractions}: the barycentric
     * coordinates of the constraint-defined target point within the given
     * simplex of sampled points.
     */
    private static double[] simplexFractions(double[][] compositions,
                                              int[] simplex,
                                              double[][] constraintCoefs,
                                              double[] constraintRhs) {

        int numComponents = compositions[0].length;
        int simplexSize = simplex.length;

        double[] targetPoint = intersectingPoint(
                compositions, simplex, constraintCoefs, constraintRhs);

        // Solve: sum_i fractions[i] * compositions[simplex[i]][j] = targetPoint[j]
        // for j = 0..numComponents-1 (simplexSize == numComponents here,
        // since this project has no fixed chemical potentials).
        double[][] coordMatrix = new double[simplexSize][simplexSize];
        for (int j = 0; j < simplexSize; j++) {
            for (int i = 0; i < simplexSize; i++) {
                coordMatrix[j][i] = compositions[simplex[i]][j];
            }
        }

        return solveLinearSystem(coordMatrix, targetPoint);
    }

    /**
     * Direct port of pycalphad's {@code intersecting_point}: the point
     * where the hyperplane through {@code simplex}'s sampled points meets
     * the linear mole-fraction constraints.
     */
    private static double[] intersectingPoint(double[][] compositions,
                                               int[] simplex,
                                               double[][] constraintCoefs,
                                               double[] constraintRhs) {

        int numComponents = compositions[0].length;

        if (simplex.length == 1) {
            return compositions[simplex[0]].clone();
        }

        // Hyperplane coefficients: plane through simplex's points,
        // i.e. sum_j planeCoefs[j] * compositions[simplex[i]][j] = 1
        // for each vertex i (pycalphad's hyperplane_coefficients, with no
        // fixed chemical potentials in this project's usage).
        double[][] planeMatrix = new double[simplex.length][numComponents];
        double[] planeRhs = new double[simplex.length];
        for (int i = 0; i < simplex.length; i++) {
            for (int j = 0; j < numComponents; j++) {
                planeMatrix[i][j] = compositions[simplex[i]][j];
            }
            planeRhs[i] = 1.0;
        }
        double[] planeCoefs = solveLinearSystem(planeMatrix, planeRhs);

        // Intersect the plane with the constraint rows: solve the square
        // system [constraintCoefs; planeCoefs] * point = [constraintRhs; 1].
        int numConstraints = constraintRhs.length;
        double[][] system = new double[numConstraints + 1][numComponents];
        double[] rhs = new double[numConstraints + 1];
        for (int i = 0; i < numConstraints; i++) {
            System.arraycopy(constraintCoefs[i], 0, system[i], 0, numComponents);
            rhs[i] = constraintRhs[i];
        }
        System.arraycopy(planeCoefs, 0, system[numConstraints], 0, numComponents);
        rhs[numConstraints] = 1.0;

        return solveLinearSystem(system, rhs);
    }

    private static double min(double[] a) {
        double m = Double.POSITIVE_INFINITY;
        for (double v : a) if (v < m) m = v;
        return m;
    }

    private static boolean containsIndex(int[] simplex, int target) {
        for (int idx : simplex) {
            if (idx == target) {
                return true;
            }
        }
        return false;
    }

    private static int argmax(double[] a) {
        int best = 0;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > bestVal) { bestVal = a[i]; best = i; }
        }
        return best;
    }

    private static int argminArray(double[] a) {
        int best = 0;
        double bestVal = Double.POSITIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            if (a[i] < bestVal) { bestVal = a[i]; best = i; }
        }
        return best;
    }

    /**
     * Sentinel returned by {@link #solveLinearSystem} in place of a
     * solution when {@code a} is singular -- pycalphad's own {@code solve}
     * (hyperplane.pyx) does exactly this ("Special for our case: singular
     * matrix results get set to a special value") rather than raising,
     * specifically so a degenerate trial simplex is naturally deprioritized
     * by the pivot search's own {@code argmax(smallestFractions)}/{@code
     * == SINGULAR_SENTINEL} checks instead of aborting the whole search.
     * A degenerate trial simplex is an expected, not exceptional, outcome
     * of this pivot search: e.g. two sampled points from different phases
     * sharing the same pure-component endmember composition (confirmed
     * directly: Ag-Cu, T=950K, x(Cu)=0.05 -- LIQUID's and FCC_A1's own
     * pure-Ag endmembers coincide at x(Cu)=0, and the search's own
     * most-negative-driving-force heuristic can propose exactly that pair
     * as a trial simplex before it settles on the true answer).
     */
    private static final double SINGULAR_SENTINEL = -1e19;

    private static double[] solveLinearSystem(double[][] a, double[] b) {
        try {
            Matrix matA = new Matrix(a);
            Matrix matB = new Matrix(b, b.length);
            Matrix x = matA.solve(matB);
            return x.getColumnPackedCopy();
        } catch (RuntimeException e) {
            double[] sentinel = new double[b.length];
            java.util.Arrays.fill(sentinel, SINGULAR_SENTINEL);
            return sentinel;
        }
    }
}
