package calc.equil;

import system.model.GibbsEnergyModel;
import system.model.PhaseEquilData;
import util.Matrix;

import java.util.logging.Logger;

/**
 * Assembles and inverts a phase's Sundman phase matrix, and derives the
 * equilibrium-matrix response coefficients from it, for any
 * {@link GibbsEnergyModel} -- CEF, or a future CVM implementation.
 *
 * <p>This is calc-layer work, not model-layer work: it borders the model's
 * Hessian with per-sublattice sum constraints (Sundman 2015 Eq. 40),
 * inverts it, and projects the inverse into the composition-space response
 * {@code eMatNC} used by a multiphase Newton solver (Eq. 58) -- exactly the
 * kind of Newton-iteration state pycalphad keeps out of its model layer
 * ({@code Model}/{@code PhaseRecord} only ever return raw value/gradient/
 * Hessian; {@code pycalphad.core} does the assembly). Every quantity used
 * here comes from {@link GibbsEnergyModel}'s abstract contract --
 * {@code G}, {@code dG_dy}, {@code d2G_dy2}, {@code d2G_dydT},
 * {@code d2G_dydP}, {@code moles}, {@code dMoles_dy}, {@code siteRatios},
 * {@code offsets}, {@code constituentsPerSublattice},
 * {@code numSublattices}, {@code compositionFromInternal} -- so this class
 * has no CEF-specific knowledge and works unchanged for any model
 * implementing that contract.
 *
 * <p>Previously this logic lived in {@code CefGibbs.compute()}, duplicating
 * {@code elementIndexOnSublattice} bookkeeping that the abstract
 * {@code dMoles_dy()} Jacobian already expresses generically: column
 * {@code m}'s single nonzero entry, {@code dMoles_dy()[A][m]}, is precisely
 * "does site-fraction {@code y[m]} map to component {@code A}, weighted by
 * its sublattice's site ratio" -- so both the chemical-potential mapping
 * and the composition-sensitivity projection below are expressed directly
 * in terms of it instead of re-deriving the same mapping per model.
 */
public final class PhaseMatrixAssembler {

    private static final Logger LOG = Logger.getLogger(PhaseMatrixAssembler.class.getName());

    private PhaseMatrixAssembler() { }

    /**
     * Builds a phase's {@link PhaseEquilData}: evaluates {@code model} at
     * {@code (T,P,y)}, assembles and inverts the bordered phase matrix, and
     * derives the linearised composition/energy response used by a
     * multiphase Newton step.
     *
     * @param model  any Gibbs energy model
     * @param T      temperature in Kelvin
     * @param P      pressure in Pa
     * @param y      site-fraction / internal-variable vector
     * @param deltaT temperature step for the linearised response
     * @param deltaP pressure step for the linearised response
     * @param mu     chemical potentials, length = model.numComponents()
     * @return       immutable per-phase equilibrium data
     */
    public static PhaseEquilData compute(GibbsEnergyModel model,
                                          double T, double P, double[] y,
                                          double deltaT, double deltaP,
                                          double[] mu) {
        int nip = model.numSiteVars();
        int nc  = model.numComponents();

        // Step 1: evaluate G and all derivatives.
        double G       = model.G(T, P, y);
        double[] Gx    = model.dG_dy(T, P, y);
        double[][] Gxx = model.d2G_dy2(T, P, y);
        double[] GxT   = model.d2G_dydT(T, P, y);
        double[] GxP   = model.d2G_dydP(T, P, y);

        // Step 2: assemble phase matrix M (nip+ns)x(nip+ns), with one
        // Lagrange-multiplier row/column per sublattice s, enforcing
        // Sigma_i y[s,i] = 1 independently for each sublattice (Sundman
        // 2015 Eq. 40).
        int ns       = model.numSublattices();
        int[] offs   = model.offsets();
        int[] ncSL   = model.constituentsPerSublattice();
        int matDim   = nip + ns;
        double[][] M = new double[matDim][matDim];
        for (int i = 0; i < nip; i++) {
            for (int j = 0; j < nip; j++) {
                M[i][j] = Gxx[i][j];
            }
        }
        for (int s = 0; s < ns; s++) {
            int borderRow = nip + s;
            for (int i = offs[s]; i < offs[s] + ncSL[s]; i++) {
                M[i][borderRow] = 1.0;
                M[borderRow][i] = 1.0;
            }
        }

        // Step 3: invert M, extract top-left nip x nip block -> eMat.
        double[][] eMat = new double[nip][nip];
        try {
            Matrix matM  = new Matrix(M);
            Matrix matMI = matM.inverse();
            for (int i = 0; i < nip; i++) {
                for (int j = 0; j < nip; j++) {
                    eMat[i][j] = matMI.get(i, j);
                }
            }
        } catch (Exception e) {
            LOG.warning("Phase matrix singular for " + model.phaseName()
                    + " — using zero eMat");
            // eMat remains zero matrix
        }

        // Step 4: composition response coefficients.
        double[] cG = new double[nip];
        double[] cT = new double[nip];
        double[] cP = new double[nip];
        for (int i = 0; i < nip; i++) {
            for (int k = 0; k < nip; k++) {
                cG[i] -= eMat[i][k] * Gx[k];
                cT[i] -= eMat[i][k] * GxT[k];
                cP[i] -= eMat[i][k] * GxP[k];
            }
        }

        // Step 5: linearised composition change. dMoles_dy()[A][m] is the
        // generic dM_A/dy Jacobian -- its single nonzero entry per column m
        // is exactly the "does y[m] map to component A, weighted by a[s]"
        // mapping mapMuToSiteFractions used to re-derive from CEF-internal
        // fields.
        double[][] dM = model.dMoles_dy();
        double[] muMapped = new double[nip];
        for (int m = 0; m < nip; m++) {
            double sum = 0.0;
            for (int A = 0; A < nc; A++) {
                sum += dM[A][m] * (A < mu.length ? mu[A] : 0.0);
            }
            muMapped[m] = sum;
        }

        double[] delyN = new double[nip];
        for (int i = 0; i < nip; i++) {
            delyN[i] = cG[i] + cT[i] * deltaT + cP[i] * deltaP;
            for (int j = 0; j < nip; j++) {
                delyN[i] += eMat[i][j] * muMapped[j];
            }
        }

        double[] x = model.compositionFromInternal(y);

        // ΔM_A = Σ_m dM_A/dy[m] * Δy[m].
        double[] delnN = new double[nc];
        for (int A = 0; A < nc; A++) {
            double sum = 0.0;
            for (int m = 0; m < nip; m++) {
                sum += dM[A][m] * delyN[m];
            }
            delnN[A] = sum;
        }

        // dM_A/dT = Sigma_m dM_A/dy[m] * cT[m] -- the deln-analogue using
        // ONLY the pure T-sensitivity cT, independent of deltaT/mu, needed
        // to release T as a Newton unknown (see PhaseEquilData.dM_dT).
        double[] dM_dT = new double[nc];
        for (int A = 0; A < nc; A++) {
            double sum = 0.0;
            for (int m = 0; m < nip; m++) {
                sum += dM[A][m] * cT[m];
            }
            dM_dT[A] = sum;
        }

        // dM_A/dP -- P-release analogue of dM_dT, using cP instead of cT.
        double[] dM_dP = new double[nc];
        for (int A = 0; A < nc; A++) {
            double sum = 0.0;
            for (int m = 0; m < nip; m++) {
                sum += dM[A][m] * cP[m];
            }
            dM_dP[A] = sum;
        }

        // mA = M_A^phase (Sundman Eq. 2): moles of component A per formula
        // unit, unnormalized.
        double[] mA = model.moles(y);

        // eMatNC[A][B] = dM_A/dmu_B, chained through eMat and dMoles_dy():
        //   dy[m]/dmu_B  = Σ_j dM[B][j] * eMat[m][j]
        //   eMatNC[A][B] = Σ_m dM[A][m] * dy[m]/dmu_B
        double[][] dyDmu = new double[nip][nc];
        for (int m = 0; m < nip; m++) {
            for (int B = 0; B < nc; B++) {
                double sum = 0.0;
                for (int j = 0; j < nip; j++) {
                    sum += dM[B][j] * eMat[m][j];
                }
                dyDmu[m][B] = sum;
            }
        }
        double[][] eMatNC = new double[nc][nc];
        for (int A = 0; A < nc; A++) {
            for (int B = 0; B < nc; B++) {
                double sum = 0.0;
                for (int m = 0; m < nip; m++) {
                    sum += dM[A][m] * dyDmu[m][B];
                }
                eMatNC[A][B] = sum;
            }
        }

        double dGdT = model.dG_dT(T, P, y);
        double dGdP = model.dG_dP(T, P, y);

        return new PhaseEquilData(G, delyN, delnN, x, mA, eMat, eMatNC, cG, cT, cP,
                dM_dT, dM_dP, dGdT, dGdP, null);
    }
}
