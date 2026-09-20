package system.model.cvm;

import org.junit.jupiter.api.Test;
import system.model.cvm.gen.GeneratedCvmGeometry;
import system.model.cvm.gen.GeneratedCvmPhaseDataAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the generated K=4 BCC_A2 CVCF geometry, evaluated through the
 * existing, validated {@link CvmGibbs} mixing-thermodynamics math, against
 * CEWorkbench's own Nb-Ti-V-Zr quaternary reference.
 *
 * <h2>Reference source</h2>
 * CEWorkbench's stored Hamiltonian database
 * {@code data/CEWorkbench/hamiltonians/Nb-Ti-V-Zr_BCC_A2_T_CVCF/hamiltonian.json}
 * (51 CVCF ECI terms, {@code CF_0..CF_50} positional to the registered CVCF
 * basis order), evaluated at {@code T=1273 K}, equimolar composition
 * {@code x=[0.25,0.25,0.25,0.25]}, via CEWorkbench's own JSON API
 * ({@code {"system":{"elements":"Nb-Ti-V-Zr","structure":"BCC_A2","model":"T",
 * "engine":"CVM"}, "calculation":"GIBBS_ENERGY",
 * "conditions":{"temperature":1273,"composition":{"Ti":0.25,"V":0.25,"Zr":0.25}}}}
 * ). The converged equilibrium correlation functions returned in that
 * response are reused directly here as {@code u2vals}, so this test checks
 * the mixing thermodynamics ({@code Hm}, {@code Sm}, {@code Gm = Hm - T*Sm})
 * at a known, externally-converged state -- it does not re-run the CVM
 * minimizer (no {@code CvmNewtonSolver}/equivalent exists or is ported into
 * expcvm10; see Step 9's scope note).
 *
 * <p>Per CEWorkbench's own {@code ThermodynamicWorkflow} (see its
 * "Mixing quantities (Gm/Hm/Sm), not the pure-element-anchored absolutes"
 * comment), the JSON API's {@code gibbsEnergy}/{@code enthalpy}/
 * {@code entropy} fields are already the pure mixing quantities -- the same
 * quantities {@link CvmGibbs#H} and {@link CvmGibbs#S} compute -- so no
 * SGTE/GHSER reference-energy term needs to be added or subtracted for this
 * comparison to be apples-to-apples.</p>
 */
class QuaternaryBccA2ThermodynamicEquivalenceTest {

    private static final double T = 1273.0;
    private static final double[] X = {0.25, 0.25, 0.25, 0.25};

    /** CEWorkbench reference: Gm/Hm/Sm at T=1273K, equimolar Nb-Ti-V-Zr. */
    private static final double REF_G = -10456.817643499955;
    private static final double REF_H = 3649.568573939777;
    private static final double REF_S = 11.081214624854464;

    /**
     * Converged equilibrium CVCF values from CEWorkbench's JSON API response
     * at this (T, x), in {@code geo.basis.cfNames} order (v4AB..v21CD, 51
     * entries) -- see class javadoc for the exact request.
     */
    private static final double[] CONVERGED_CFS = {
        0.003370542538120201, 0.004759205094070094, 0.0030752413247143455, 0.004531707166198103,
        0.006526640142917579, 0.0019573124436298157, 0.0030283830977832064, 0.005051346845108039,
        0.006557611200381956, 0.003107095270527535, 0.004277047995821901, 0.004730572712248874,
        0.00270356741629531, 0.002575441438705877, 0.0025165060869658646, 0.0035674268602422367,
        0.0025440917997255606, 0.0031364026199428205, 0.0036143204703474254, 0.003085411649077794,
        0.002976041520261205, 0.0018355593438708618, 0.005345886076594929, 0.0031438176642946455,
        0.0036462240786389395, 0.004761324790459896, 0.0010889102754857828, 0.014724144117820704,
        0.01836785582483139, 0.019088564141990463, 0.01455876410920044, 0.015824288408817236,
        0.015910187074697858, 0.011957933138437148, 0.011160219261691307, 0.01138938882179015,
        0.01353207754751491, 0.011792750508122264, 0.011827572053199339, 0.06572615378655113,
        0.06788559623604387, 0.05693831740163093, 0.05689764154553358, 0.0655818056309472,
        0.04933827784092451, 0.06178346994450673, 0.06544114072841023, 0.05577823063466339,
        0.06432574940044908, 0.06886664121647407, 0.045740255333410244
    };

    /**
     * CEC constant (a) coefficients from the stored Nb-Ti-V-Zr hamiltonian.json,
     * CF_0..CF_50 positional to geo.basis.cfNames order (all in J/mol).
     */
    private static final double[] CEC_A = {
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 48800.0, -24400.0, -24400.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, -4224.0, 0.0, 0.0, -5760.0, -20000.0, -20000.0, -20000.0, -3333.33, -3333.33, -3333.33,
        0.0, 0.0, 0.0, 10000.0, 10000.0, 10000.0, 3120.0, 7040.0, 3700.8, 4080.0, -4632.0, 8960.0, 6240.0, 14080.0,
        7401.6, 8160.0, -9264.0, 17920.0
    };

    /** CEC linear-in-T (b) coefficients, same order, in J/(mol*K). */
    private static final double[] CEC_B = {
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 3.3216, 0.0,
        0.0, 1.272, 0.0, 0.0, 6.6432, 0.0, 0.0, 2.544
    };

    @Test
    void mixingThermodynamicsMatchCEWorkbenchReference() {
        GeneratedCvmGeometry geo = GeneratedCvmGeometry.buildBccA2("Nb-Ti-V-Zr", 4, null);
        geo.validate();

        CvmPhaseData data = GeneratedCvmPhaseDataAdapter.toCvmPhaseData(geo);
        assertEquals(51, data.ncf, "ncf must match the Nb-Ti-V-Zr hamiltonian's 51 CVCF ECI terms");

        // cec[i] = a[i] + b[i]*T, in eListNames order (== cfNames[0..ncf-1]).
        double[] cec = new double[data.ncf];
        for (int i = 0; i < data.ncf; i++) {
            cec[i] = CEC_A[i] + CEC_B[i] * T;
        }

        double[] u2vals = new double[data.nip];
        System.arraycopy(CONVERGED_CFS, 0, u2vals, 0, data.ncf);
        System.arraycopy(X, 0, u2vals, data.ncf, X.length);

        CvmGibbs gibbs = new CvmGibbs(data, dummyGhser(4));

        double hm = gibbs.H(u2vals, cec);
        double sm = gibbs.S(u2vals);
        double gm = hm - T * sm;

        System.out.printf("Hm: expcvm10=%.10f  CEWorkbench=%.10f  diff=%.3e%n", hm, REF_H, hm - REF_H);
        System.out.printf("Sm: expcvm10=%.10f  CEWorkbench=%.10f  diff=%.3e%n", sm, REF_S, sm - REF_S);
        System.out.printf("Gm: expcvm10=%.10f  CEWorkbench=%.10f  diff=%.3e%n", gm, REF_G, gm - REF_G);

        assertEquals(REF_H, hm, 1e-4, "mixing enthalpy Hm");
        assertEquals(REF_S, sm, 1e-6, "mixing entropy Sm");
        assertEquals(REF_G, gm, 1e-3, "mixing Gibbs energy Gm = Hm - T*Sm");
    }

    /** GHSER is not exercised by this test (mixing-only comparison, see class javadoc). */
    private static system.model.unary.ElementGibbs[] dummyGhser(int n) {
        system.model.unary.ElementGibbs[] arr = new system.model.unary.ElementGibbs[n];
        for (int i = 0; i < n; i++) {
            arr[i] = new system.model.unary.ElementGibbs() {
                @Override public String elementSymbol() { return "X"; }
                @Override public double gibbs(String phaseName, double T) { return 0.0; }
                @Override public double ghser(double T) { return 0.0; }
                @Override public java.util.Set<String> availablePhases() { return java.util.Set.of(); }
            };
        }
        return arr;
    }
}
