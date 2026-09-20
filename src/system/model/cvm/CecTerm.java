package system.model.cvm;

/**
 * One named, temperature-dependent Cluster Expansion Coefficient (CEC).
 *
 * <p>Adapted from CEWorkbench's {@code CECEntry}/{@code CECTerm} concept,
 * trimmed to the minimum needed here: a name (matched against
 * {@link CvmPhaseData#eListNames}) and a linear-in-T value
 *
 * <pre>
 *   J(T) = a + b*T
 * </pre>
 *
 * <p>This is intentionally not a general polynomial -- the existing CVM
 * data/evaluator pathway in this project has no data source for anything
 * higher order, and CEWorkbench's own CEC representation is linear too.
 */
public final class CecTerm {

    /** Name matched against {@link CvmPhaseData#eListNames}, e.g. "e4AB". */
    public final String name;

    /** Constant coefficient, J/mol. */
    public final double a;

    /** Linear-in-T coefficient, J/(mol*K). */
    public final double b;

    public CecTerm(String name, double a, double b) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("CEC term name must not be blank");
        this.name = name;
        this.a = a;
        this.b = b;
    }

    /** J(T) = a + b*T. */
    public double value(double T) {
        return a + b * T;
    }

    /** dJ/dT = b (exact; J is affine in T). */
    public double dValueDT() {
        return b;
    }

    @Override
    public String toString() {
        return String.format("CecTerm{%s: %.6g + %.6g*T}", name, a, b);
    }
}
