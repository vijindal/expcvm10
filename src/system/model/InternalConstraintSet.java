package system.model;

/**
 * Represents linear equality constraints on internal variables (y) of a Gibbs energy model.
 *
 * <p>For a model with {@code nip} site-fraction/internal variables, this describes
 * how many linear equality constraints exist: C * y = b, where C is the constraint
 * matrix and b is the right-hand side. Each constraint has the form:
 * <pre>
 *   sum_i C[k][i] * y[i] = b[k]
 * </pre>
 * For sum-to-1 constraints (CEF sublattices, CVM composition block), b[k] = 1.
 *
 * <p>This abstraction separates model identity from the solver's constraint
 * bookkeeping: a CEF model has sublattices, a CVM model has only a composition
 * normalization, and a future model might have other structures -- all plugged
 * into the same Sundman solver via this contract without modification.
 */
public interface InternalConstraintSet {

    /**
     * Number of linear equality constraints on the internal variables.
     * @return number of constraints (e.g., 3 for a CEF phase with 3 sublattices)
     */
    int numConstraints();

    /**
     * Jacobian (matrix C) of the equality constraints with respect to internal variables.
     *
     * <p>For sum-to-1 constraints, this is a sparse indicator matrix:
     * {@code constraintJacobian()[k][i] = 1.0} if variable {@code y[i]}
     * appears in constraint {@code k}, and {@code 0.0} otherwise.
     *
     * @return double[numConstraints][numInternalVariables] matrix where each
     *         row k describes the coefficients C[k][i] of constraint k
     */
    double[][] constraintJacobian();

    /**
     * Right-hand side (vector b) of the equality constraints.
     *
     * <p>For sum-to-1 constraints, each element is 1.0. For other constraint
     * types (e.g., charge balance, stoichiometric constraints), values may differ.
     *
     * @return double[numConstraints] right-hand side vector
     */
    double[] constraintRhs();
}
