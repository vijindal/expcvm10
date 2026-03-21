package domain;

/**
 * Immutable value object for thermodynamic calculation results.
 */
public final class ThermoResult {

    private final double G;
    private final double GT;
    private final double GP;
    private final double[] Gx;
    private final double[] GTx;
    private final double[] GPx;
    private final double[][] Gxx;

    public ThermoResult(double G, double GT, double GP,
                        double[] Gx, double[] GTx, double[] GPx,
                        double[][] Gxx) {
        this.G = G;
        this.GT = GT;
        this.GP = GP;
        this.Gx = Gx != null ? Gx.clone() : null;
        this.GTx = GTx != null ? GTx.clone() : null;
        this.GPx = GPx != null ? GPx.clone() : null;
        if (Gxx != null) {
            this.Gxx = new double[Gxx.length][];
            for (int i = 0; i < Gxx.length; i++) {
                this.Gxx[i] = Gxx[i].clone();
            }
        } else {
            this.Gxx = null;
        }
    }

    public double getG() { return G; }
    public double getGT() { return GT; }
    public double getGP() { return GP; }
    public double[] getGx() { return Gx != null ? Gx.clone() : null; }
    public double[] getGTx() { return GTx != null ? GTx.clone() : null; }
    public double[] getGPx() { return GPx != null ? GPx.clone() : null; }
    public double[][] getGxx() {
        if (Gxx == null) return null;
        double[][] copy = new double[Gxx.length][];
        for (int i = 0; i < Gxx.length; i++) {
            copy[i] = Gxx[i].clone();
        }
        return copy;
    }
}
