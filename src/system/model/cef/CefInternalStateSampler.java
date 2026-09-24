package system.model.cef;

import system.model.InternalStateSampler;
import calc.equil.Halton;

import java.util.ArrayList;
import java.util.List;

/**
 * CEF-specific internal-state sampler: endmembers, edges, and Halton interior
 * points in site-fraction space.
 *
 * <p>Extracted from GridMinimizer to decouple model-specific sampling from the
 * generic global-initialization algorithm. This class owns all CEF sublattice
 * assumptions and Cartesian-product endmember generation logic.
 *
 * <p>Ports pycalphad's {@code calculate()} / {@code _sample_phase_constitution}
 * (endmembers + fixed-grid edges + Halton interior) for CEF phases.
 */
public final class CefInternalStateSampler implements InternalStateSampler {

    private static final double MIN_SITE_FRACTION = 1.0e-14;

    private final int ns;
    private final int[] constituentsPerSublattice;
    private final int[] offsets;
    private final int nip;

    /**
     * Constructs a CEF state sampler from CEF phase structure.
     *
     * @param ns                        number of sublattices
     * @param constituentsPerSublattice number of constituents per sublattice
     * @param offsets                   offset of each sublattice in y
     * @param nip                       total number of site-fraction variables
     */
    public CefInternalStateSampler(int ns,
                                    int[] constituentsPerSublattice,
                                    int[] offsets,
                                    int nip) {
        this.ns = ns;
        this.constituentsPerSublattice = constituentsPerSublattice.clone();
        this.offsets = offsets.clone();
        this.nip = nip;
    }

    @Override
    public double[][] sample(int density, int edgePoints) {
        List<double[]> points = new ArrayList<>();

        // --- Endmembers: full Cartesian product of one-hot vectors per sublattice ---
        List<double[]> endmembers = buildEndmemberMatrix();
        points.addAll(endmembers);

        // --- Fixed grid: linear interpolation along every endmember-pair edge ---
        int numEndmembers = endmembers.size();
        if (numEndmembers >= 2) {
            for (int a = 0; a < numEndmembers; a++) {
                for (int b = a + 1; b < numEndmembers; b++) {
                    double[] emA = endmembers.get(a);
                    double[] emB = endmembers.get(b);
                    for (int k = 0; k < edgePoints; k++) {
                        double lam = (edgePoints == 1)
                                ? 0.5
                                : (double) k / (edgePoints - 1);
                        double[] p = new double[nip];
                        for (int i = 0; i < nip; i++) {
                            p[i] = emA[i] * lam + emB[i] * (1.0 - lam);
                        }
                        points.add(p);
                    }
                }
            }
        }

        // --- Interior Halton sampling, only when the phase has internal degrees of freedom ---
        if (nip > ns) {
            double[][] interior = Halton.pointSample(constituentsPerSublattice, density);
            for (double[] p : interior) {
                points.add(p);
            }
        }

        return points.toArray(new double[0][]);
    }

    /**
     * Full Cartesian product of one-hot constituent vectors per sublattice,
     * with zero entries floored to MIN_SITE_FRACTION and each sublattice
     * block renormalized to sum to 1.
     */
    private List<double[]> buildEndmemberMatrix() {
        List<int[]> combos = new ArrayList<>();
        cartesianProduct(constituentsPerSublattice, 0, new int[ns], combos);

        List<double[]> result = new ArrayList<>(combos.size());

        for (int[] combo : combos) {
            double[] y = new double[nip];
            for (int i = 0; i < nip; i++) {
                y[i] = MIN_SITE_FRACTION;
            }
            for (int s = 0; s < ns; s++) {
                y[offsets[s] + combo[s]] = 1.0;
            }
            // Renormalize each sublattice block to sum to 1.
            for (int s = 0; s < ns; s++) {
                double sum = 0.0;
                for (int i = 0; i < constituentsPerSublattice[s]; i++) {
                    sum += y[offsets[s] + i];
                }
                for (int i = 0; i < constituentsPerSublattice[s]; i++) {
                    y[offsets[s] + i] /= sum;
                }
            }
            result.add(y);
        }

        return result;
    }

    private void cartesianProduct(int[] ncSub, int s, int[] combo,
                                   List<int[]> out) {
        if (s == ncSub.length) {
            out.add(combo.clone());
            return;
        }
        for (int i = 0; i < ncSub[s]; i++) {
            combo[s] = i;
            cartesianProduct(ncSub, s + 1, combo, out);
        }
    }
}
