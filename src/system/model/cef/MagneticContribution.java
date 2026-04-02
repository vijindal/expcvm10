package system.model.cef;

/**
 * Inden-Hillert magnetic Gibbs energy contribution for CALPHAD phases.
 *
 * <h2>TDB source</h2>
 * Parameters are read from TYPE_DEFINITION lines, e.g.:
 * <pre>
 *   TYPE_DEFINITION &amp; GES A_P_D BCC_A2 MAGNETIC  -1.0    4.00000E-01
 *                                                   ↑aff    ↑p
 * </pre>
 *
 * <h2>Formula</h2>
 * <pre>
 *   G_magn = R * T * f(tau) * ln(beta + 1)
 *
 *   tau = T / Tc
 *   A   = 518/1125 + 11692/15975 * (1/p - 1)
 *
 *   tau &lt; 1:  f = 1 - (1/A) * [ 79/(140p) * tau^-1
 *                              + 474/497*(1/p-1)*(tau^3/6 + tau^9/135 + tau^15/600) ]
 *   tau >= 1: f = -(1/A) * ( tau^-5/10 + tau^-15/315 + tau^-25/1500 )
 * </pre>
 *
 * <h2>Self-test values (Fe BCC)</h2>
 * At T=1000K, Tc=1043K, beta=2.22:
 * tau ≈ 0.9588 (below Tc), G should be negative (ferromagnetic stabilization),
 * dGdT should be negative (entropy contribution).
 */
public class MagneticContribution {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final double R = 8.3144598;

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    /** Antiferromagnetic factor: -1.0 for BCC, -3.0 for FCC/HCP. */
    private final double aff;

    /** Structure factor: 0.4 for BCC, 0.28 for FCC/HCP. */
    private final double p;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Constructs a magnetic contribution evaluator.
     *
     * @param aff antiferromagnetic factor: must be -1.0 (BCC) or -3.0 (FCC/HCP)
     * @param p   structure factor: must be &gt; 0
     */
    public MagneticContribution(double aff, double p) {
        if (aff != -1.0 && aff != -3.0)
            throw new IllegalArgumentException(
                "aff must be -1.0 (BCC) or -3.0 (FCC/HCP), got " + aff);
        if (p <= 0)
            throw new IllegalArgumentException(
                "p must be > 0, got " + p);
        this.aff = aff;
        this.p   = p;
    }

    // ------------------------------------------------------------------
    // Public methods
    // ------------------------------------------------------------------

    /**
     * Magnetic Gibbs energy contribution in J/mol.
     *
     * @param T    temperature in Kelvin
     * @param Tc   Curie/Neel temperature in Kelvin (composition-dependent)
     * @param beta Bohr magneton number (composition-dependent)
     * @return     G_magn in J/mol
     */
    public double G(double T, double Tc, double beta) {
        if (Tc <= 0 || beta <= -1.0) return 0.0;
        double tau = T / Tc;
        double logb1 = Math.log(beta + 1.0);
        return R * T * f(tau) * logb1;
    }

    /**
     * dG/dT of the magnetic contribution in J/(mol·K).
     *
     * @param T    temperature in Kelvin
     * @param Tc   Curie/Neel temperature in Kelvin
     * @param beta Bohr magneton number
     * @return     dG_magn/dT in J/(mol·K)
     */
    public double dGdT(double T, double Tc, double beta) {
        if (Tc <= 0 || beta <= -1.0) return 0.0;
        double tau = T / Tc;
        double logb1 = Math.log(beta + 1.0);
        // d/dT [R*T*f(tau)*logb1] = R*logb1*(f(tau) + T*df/dT)
        // df/dT = df/dtau * dtau/dT = df/dtau * (1/Tc)
        return R * logb1 * (f(tau) + T * dfdtau(tau) / Tc);
    }

    /**
     * dG/dy[m] for each site fraction — requires dTc/dy and dbeta/dy arrays.
     *
     * @param T       temperature in Kelvin
     * @param Tc      Curie/Neel temperature in Kelvin
     * @param beta    Bohr magneton number
     * @param dTcdy   dTc/dy[m] for each site fraction m
     * @param dbetady dbeta/dy[m] for each site fraction m
     * @return        dG_magn/dy[m] array in J/mol
     */
    public double[] dGdy(double T, double Tc, double beta,
                         double[] dTcdy, double[] dbetady) {
        int n = dTcdy.length;
        double[] result = new double[n];
        if (Tc <= 0 || beta <= -1.0) return result;
        double tau = T / Tc;
        double logb1 = Math.log(beta + 1.0);
        double fval = f(tau);
        double dfdtauVal = dfdtau(tau);
        // dG/dy[m] = R*T * ( df/dtau * dtau/dy[m] * logb1
        //                  + f(tau) * 1/(beta+1) * dbeta/dy[m] )
        // dtau/dy[m] = -T/Tc^2 * dTc/dy[m] = -tau/Tc * dTc/dy[m]
        for (int m = 0; m < n; m++) {
            double dtaudy = -tau / Tc * dTcdy[m];
            result[m] = R * T * (dfdtauVal * dtaudy * logb1
                               + fval / (beta + 1.0) * dbetady[m]);
        }
        return result;
    }

    /** Returns the antiferromagnetic factor aff. */
    public double aff() { return aff; }

    /** Returns the structure factor p. */
    public double p() { return p; }

    // ------------------------------------------------------------------
    // Static factory
    // ------------------------------------------------------------------

    /**
     * Creates a MagneticContribution from values parsed from a TYPE_DEFINITION line.
     *
     * @param aff antiferromagnetic factor (e.g. -1.0 for BCC, -3.0 for FCC)
     * @param p   structure factor (e.g. 0.4 for BCC, 0.28 for FCC)
     * @return    new MagneticContribution instance
     */
    public static MagneticContribution fromTypeDefinition(double aff, double p) {
        return new MagneticContribution(aff, p);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Inden-Hillert polynomial f(tau), normalized so total magnetic entropy = R*ln(beta+1).
     * Continuous at tau=1.
     */
    private double f(double tau) {
        double A = 518.0/1125.0 + (11692.0/15975.0) * (1.0/p - 1.0);
        if (tau < 1.0) {
            double series = (79.0/(140.0*p)) * (1.0/tau)
                          + (474.0/497.0) * (1.0/p - 1.0)
                          * (Math.pow(tau, 3)/6.0 + Math.pow(tau, 9)/135.0 + Math.pow(tau, 15)/600.0);
            return 1.0 - series / A;
        } else {
            double series = Math.pow(tau, -5)/10.0
                          + Math.pow(tau, -15)/315.0
                          + Math.pow(tau, -25)/1500.0;
            return -series / A;
        }
    }

    /** Analytical derivative df/dtau. */
    private double dfdtau(double tau) {
        double A = 518.0/1125.0 + (11692.0/15975.0) * (1.0/p - 1.0);
        if (tau < 1.0) {
            double dseries = -(79.0/(140.0*p)) * (1.0/(tau*tau))
                           + (474.0/497.0) * (1.0/p - 1.0)
                           * (Math.pow(tau, 2)/2.0 + Math.pow(tau, 8)/15.0 + Math.pow(tau, 14)/40.0);
            return -dseries / A;
        } else {
            double dseries = -5.0*Math.pow(tau, -6)/10.0
                            -15.0*Math.pow(tau, -16)/315.0
                            -25.0*Math.pow(tau, -26)/1500.0;
            return -dseries / A;
        }
    }
}
