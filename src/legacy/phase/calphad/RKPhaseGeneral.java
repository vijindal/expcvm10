package legacy.phase.calphad;

import legacy.phase.GibbsModel;
import java.util.ArrayList;
import java.util.List;

/**
 * General Multi-component Redlich-Kister Phase Model.
 *
 * Gibbs energy:
 * G = Σi xi*G0i + RT*Σi xi*ln(xi) + Σ(i<j) xi*xj * Σk Lk_ij*(xi-xj)^k
 * where Lk_ij = a_k + b_k*T
 *
 * Correctly handles multicomponent systems with symmetric Hessian.
 */
public class RKPhaseGeneral extends GibbsModel {

    public static class Interaction {
        final int i, j;
        final double[] a;   // Lk = a_k + b_k*T
        final double[] b;

        public Interaction(int i, int j, double[] a, double[] b) {
            this.i = i;
            this.j = j;
            this.a = a;
            this.b = b;
        }
    }

    private final List<Interaction> interactions;

    public RKPhaseGeneral(int numComp, List<Interaction> interactions) {
        this.interactions = interactions;
        validateInteractions();
        setR(8.3144);
        setNumComp(numComp);
    }

    // ============== Helper: safe log with clamping ==============
    private double safeLog(double xi) {
        // Always clamp to avoid -∞ and ensure consistency in derivatives
        return Math.log(Math.max(xi, 1e-12));
    }

    // ============== Validation ==============
    private void validateInteractions() {
        for (Interaction inter : interactions) {
            if (inter.a.length != inter.b.length) {
                throw new IllegalArgumentException(
                    "Interaction (" + inter.i + "," + inter.j + "): " +
                    "a.length (" + inter.a.length + ") != b.length (" + inter.b.length + ")"
                );
            }
        }
    }

    // ============== Zero-order: G and Gm ==============

    @Override
    public double calG() {
        return calG0() + calGm();
    }

    @Override
    public double calGm() {
        return calGid() + calGExcess();
    }

    @Override
    public double calGid() {
        // Override base class to use safe log (clamped, no conditionals)
        double temp = 0.0;
        for (int i = 0; i < getNumComp(); i++) {
            double xiSafe = Math.max(getX().get(i), 1e-12);
            temp += xiSafe * safeLog(xiSafe);
        }
        return getR() * getT() * temp;
    }

    private double calG0() {
        double[] G0 = getG0List();
        double G = 0.0;
        for (int i = 0; i < getNumComp(); i++) {
            G += getX().get(i) * G0[i];
        }
        return G;
    }

    private double calGExcess() {
        double Gex = 0.0;
        double T = getT();
        for (Interaction inter : interactions) {
            double xi = getX().get(inter.i);
            double xj = getX().get(inter.j);
            double u = xi - xj;
            double F = evalF(inter, u, T);
            Gex += xi * xj * F;
        }
        return Gex;
    }

    // ============== First order: dG/dx and dG/dT ==============

    @Override
    public double[] calDGx() {
        int n = getNumComp();
        double[] mu = new double[n];
        double T = getT();
        double[] G0 = getG0List();
        ArrayList<Double> x = getX();

        // ---- Compute unconstrained chemical potentials ----
        // μ_i = G0i + RT*ln(xi) + excess  [NO "+1" term for constrained space]
        for (int i = 0; i < n; i++) {
            double xiSafe = Math.max(x.get(i), 1e-12);
            mu[i] = G0[i] + getR() * T * safeLog(xiSafe);
        }

        // Excess contributions: ∂(xi*xj*F)/∂xi = xj*F + xi*xj*F'
        for (Interaction inter : interactions) {
            int ii = inter.i;
            int jj = inter.j;
            double xi = x.get(ii);
            double xj = x.get(jj);
            double u = xi - xj;
            double F = evalF(inter, u, T);
            double Fp = evalFp(inter, u, T);  // dF/du

            mu[ii] += xj * F + xi * xj * Fp;
            mu[jj] += xi * F - xi * xj * Fp;
        }

        // ---- Enforce Gibbs-Duhem consistency: Σ xi*μi = G ----
        double G = calG();
        double muSum = 0.0;
        for (int i = 0; i < n; i++) {
            muSum += x.get(i) * mu[i];
        }
        double correction = G - muSum;
        for (int i = 0; i < n; i++) {
            mu[i] += correction;
        }

        return mu;
    }

    @Override
    public double calDGT() {
        double[] G0T = getG0TList();
        double dGT = 0.0;
        ArrayList<Double> x = getX();

        // G0 T-derivative
        for (int i = 0; i < getNumComp(); i++) {
            dGT += x.get(i) * G0T[i];
        }

        // Ideal: ∂(RT Σxi ln xi)/∂T = R*Σxi*ln(xi)  [consistent safe log]
        for (int i = 0; i < getNumComp(); i++) {
            double xiSafe = Math.max(x.get(i), 1e-12);
            dGT += getR() * xiSafe * safeLog(xiSafe);
        }

        // Excess entropy: ∂(Σ xi*xj*Σk bk*(xi-xj)^k)/∂T = Σ xi*xj*Σk bk*(xi-xj)^k
        for (Interaction inter : interactions) {
            double xi = x.get(inter.i);
            double xj = x.get(inter.j);
            double u = xi - xj;
            double Fb = evalFb(inter, u);  // Σk bk*u^k
            dGT += xi * xj * Fb;
        }

        return dGT;
    }

    @Override
    public double[] calDGTx() {
        int n = getNumComp();
        double[] res = new double[n];
        double[] G0T = getG0TList();
        ArrayList<Double> x = getX();

        // G0T contribution
        for (int i = 0; i < n; i++) {
            res[i] = G0T[i];
        }

        // Ideal: ∂²(RT Σxi ln xi)/∂T∂xi = R*ln(xi)  [consistent, no "+1" to match calDGx]
        for (int i = 0; i < n; i++) {
            double xiSafe = Math.max(x.get(i), 1e-12);
            res[i] += getR() * safeLog(xiSafe);
        }

        // Excess T cross-derivative: ∂²(Σ xi*xj*Σk bk*u^k)/∂T∂xi
        for (Interaction inter : interactions) {
            int ii = inter.i;
            int jj = inter.j;
            double xi = x.get(ii);
            double xj = x.get(jj);
            double u = xi - xj;
            double Fb = evalFb(inter, u);
            double Fbp = evalFbp(inter, u);  // dFb/du

            res[ii] += xj * Fb + xi * xj * Fbp;
            res[jj] += xi * Fb - xi * xj * Fbp;
        }

        return res;
    }

    @Override
    public double calDGP() {
        return 0.0;
    }

    // ============== Second order: d²G/dx² ==============

    @Override
    public double[][] calDGxx() {
        int n = getNumComp();
        double[][] H = new double[n][n];
        double T = getT();

        // Ideal: ∂²(RT Σxi ln xi)/∂xi² = RT/xi (diagonal only)
        for (int i = 0; i < n; i++) {
            double xi = Math.max(getX().get(i), 1e-12);
            H[i][i] += getR() * T / xi;
        }

        // Excess: correct per-interaction Hessian formulas
        for (Interaction inter : interactions) {
            int ii = inter.i;
            int jj = inter.j;
            double xi = getX().get(ii);
            double xj = getX().get(jj);
            double u = xi - xj;

            double F = evalF(inter, u, T);
            double Fp = evalFp(inter, u, T);      // dF/du
            double Fpp = evalFpp(inter, u, T);    // d²F/du²

            // ∂²(xi*xj*F)/∂xi²     = 2*xj*F' + xi*xj*F''
            H[ii][ii] += 2.0 * xj * Fp + xi * xj * Fpp;

            // ∂²(xi*xj*F)/∂xj²     = -2*xi*F' + xi*xj*F''  (opposite sign!)
            H[jj][jj] += -2.0 * xi * Fp + xi * xj * Fpp;

            // ∂²(xi*xj*F)/∂xi∂xj   = F + (xi-xj)*F' - xi*xj*F''  (symmetric)
            double cross = F + u * Fp - xi * xj * Fpp;
            H[ii][jj] += cross;
            H[jj][ii] += cross;
        }

        return H;
    }

    @Override
    public double[] calDGPx() {
        return new double[getNumComp()];
    }

    // ============== Helper evaluators: F and derivatives ==============

    /** F(u,T) = Σk (a_k + b_k*T) * u^k */
    private double evalF(Interaction inter, double u, double T) {
        double F = 0.0;
        for (int k = 0; k < inter.a.length; k++) {
            F += (inter.a[k] + T * inter.b[k]) * Math.pow(u, k);
        }
        return F;
    }

    /** dF/du = Σk k*(a_k + b_k*T)*u^(k-1) */
    private double evalFp(Interaction inter, double u, double T) {
        double Fp = 0.0;
        for (int k = 1; k < inter.a.length; k++) {
            Fp += k * (inter.a[k] + T * inter.b[k]) * Math.pow(u, k - 1);
        }
        return Fp;
    }

    /** d²F/du² = Σk k*(k-1)*(a_k + b_k*T)*u^(k-2) */
    private double evalFpp(Interaction inter, double u, double T) {
        double Fpp = 0.0;
        for (int k = 2; k < inter.a.length; k++) {
            Fpp += k * (k - 1) * (inter.a[k] + T * inter.b[k]) * Math.pow(u, k - 2);
        }
        return Fpp;
    }

    /** Fb(u) = Σk b_k * u^k  (T-coefficient of F) */
    private double evalFb(Interaction inter, double u) {
        double Fb = 0.0;
        for (int k = 0; k < inter.b.length; k++) {
            Fb += inter.b[k] * Math.pow(u, k);
        }
        return Fb;
    }

    /** dFb/du = Σk k*b_k*u^(k-1) */
    private double evalFbp(Interaction inter, double u) {
        double Fbp = 0.0;
        for (int k = 1; k < inter.b.length; k++) {
            Fbp += k * inter.b[k] * Math.pow(u, k - 1);
        }
        return Fbp;
    }

    // ============== Other required abstract methods ==============

    @Override
    public void calGderivatives() {
        updateGE(
            calG(), calDGT(), calDGP(),
            calDGx(), calDGTx(), calDGPx(),
            calDGxx(),
            null, null, null, null, null
        );
    }

    @Override
    public ArrayList<Double> getInitlIntVarValues(ArrayList<Double> x) {
        return new ArrayList<>();
    }

    @Override
    public void printPhaseInfo() {
        System.out.println("=== RKPhaseGeneral ===");
        System.out.println("T=" + getT() + ", P=" + getP());
        System.out.println("x=" + getX());
        System.out.println("G=" + calG() + ", Gm=" + calGm());
    }
}
