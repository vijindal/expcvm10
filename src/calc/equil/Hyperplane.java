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

        int[] bestGuessSimplex = new int[simplexSize];
        for (int i = 0; i < simplexSize; i++) {
            bestGuessSimplex[i] = i;
        }

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

            double[] solved;
            try {
                solved = solveLinearSystem(tieline, rhs);
            } catch (RuntimeException e) {
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

    private static double[] solveLinearSystem(double[][] a, double[] b) {
        Matrix matA = new Matrix(a);
        Matrix matB = new Matrix(b, b.length);
        Matrix x = matA.solve(matB);
        return x.getColumnPackedCopy();
    }
}
