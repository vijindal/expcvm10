package system.model;

import java.util.ArrayList;
import java.util.Set;
import system.model.unary.ElementGibbs;

/**
 * Comprehensive abstract class for Gibbs energy models (RK, CEF, CVM).
 *
 * <p>This is the primary contract that all thermodynamic models must satisfy.
 * Contains ALL G0 computation and storage. Model-specific classes only implement
 * excess Gibbs energy methods.
 *
 * <h2>Design Principle</h2>
 * <ul>
 *   <li><b>G0 & Pure Component Data</b> (concrete methods + fields)
 *       — Implemented here. All models use identical G0 calculations.</li>
 *   <li><b>Excess Gibbs Energy</b> (abstract methods)
 *       — RK, CEF, CVM implement their specific excess contributions.</li>
 * </ul>
 *
 * <h2>Structure</h2>
 * <ol>
 *   <li><b>Phase Identity</b> (abstract) — phase name, model type, elements
 *   <li><b>State Variables</b> (concrete) — T, P, x, y, n storage & access
 *   <li><b>Pure Component Data</b> (concrete) — G0, G0T, G0P storage & computation
 *   <li><b>G0 Computation</b> (concrete) — evaluate G0, gradients, Hessians
 *   <li><b>Model-Specific G</b> (abstract) — excess Gibbs energy
 *   <li><b>Internal Variables</b> (abstract) — site fractions, cluster vars
 *   <li><b>Equilibrium Matrix</b> (concrete) — solver support
 * </ol>
 */
public abstract class GibbsEnergyModel {

    // ══════════════════════════════════════════════════════════════════
    // State Variables (Concrete Storage)
    // ══════════════════════════════════════════════════════════════════

    protected double T;
    protected double P;
    protected double[] x;
    protected double[] y;
    protected double n = 1.0;

    // ══════════════════════════════════════════════════════════════════
    // Pure Component Data (Concrete Storage)
    // ══════════════════════════════════════════════════════════════════

    protected double[] g0List;
    protected double[] g0TList;
    protected double[] g0PList;

    // ══════════════════════════════════════════════════════════════════
    // Derivative Cache (Concrete Storage)
    // ══════════════════════════════════════════════════════════════════

    protected double cachedG;
    protected double cachedGT;
    protected double cachedGP;
    protected double[] cachedGx;
    protected double[] cachedGTx;
    protected double[] cachedGPx;
    protected double[][] cachedGxx;
    protected boolean derivativesCached = false;

    // ══════════════════════════════════════════════════════════════════
    // Equilibrium Matrix (Concrete Storage)
    // ══════════════════════════════════════════════════════════════════

    protected double[][] eMat;
    protected double[] cG;
    protected double[] cT;
    protected double[] cP;
    protected double[][] cAB;

    // ══════════════════════════════════════════════════════════════════
    // Phase Identity (Abstract - Each Model Provides)
    // ══════════════════════════════════════════════════════════════════

    /** Phase name, e.g. "BCC_A2", "LIQUID", "C15". */
    public abstract String phaseName();

    /** Model type: "RK", "CEF", or "CVM". */
    public abstract String modelType();

    /** Element symbols in this phase (e.g. ["NB", "TI"]). */
    public abstract ArrayList<String> elementNames();

    /** Component symbols/labels. */
    public abstract String[] componentList();

    /** Number of independent components. */
    public abstract int numComponents();

    /** Number of internal parameters (site fractions for CEF, cluster vars for CVM). */
    public abstract int numInternalParams();

    /** Number of total parameters (internal + constraints). */
    public abstract int numTotalParams();

    /** Number of formula units per mole of atoms. RK: 1.0, CEF: e.g. 3 for A₂B. */
    public abstract double nfu();

    // ══════════════════════════════════════════════════════════════════
    // State Variables (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    public void setTemperature(double T)    { this.T = T; derivativesCached = false; }
    public double getTemperature()          { return T; }

    public void setPressure(double P)       { this.P = P; derivativesCached = false; }
    public double getPressure()             { return P; }

    public void setComposition(double[] x)  { this.x = x.clone(); derivativesCached = false; }
    public double[] getComposition()        { return x.clone(); }

    public void setMoles(double n)          { this.n = n; }
    public double getMoles()                { return n; }

    public void setInternalVars(double[] y) { this.y = y.clone(); derivativesCached = false; }
    public double[] getInternalVars()       { return y.clone(); }

    // ══════════════════════════════════════════════════════════════════
    // Pure Component Data (Concrete Implementation - FULL STORAGE HERE)
    // ══════════════════════════════════════════════════════════════════

    public void setG0List(double[] g0) {
        this.g0List = g0 != null ? g0.clone() : null;
        derivativesCached = false;
    }

    public double[] getG0List() {
        return g0List != null ? g0List.clone() : null;
    }

    public void setG0TList(double[] g0t) {
        this.g0TList = g0t != null ? g0t.clone() : null;
        derivativesCached = false;
    }

    public double[] getG0TList() {
        return g0TList != null ? g0TList.clone() : null;
    }

    public void setG0PList(double[] g0p) {
        this.g0PList = g0p != null ? g0p.clone() : null;
        derivativesCached = false;
    }

    public double[] getG0PList() {
        return g0PList != null ? g0PList.clone() : null;
    }

    // ══════════════════════════════════════════════════════════════════
    // G0 List Population (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Populate g0List, g0TList, and g0PList from ElementGibbs array.
     * This is a shared utility for all Gibbs energy models to compute reference energies
     * and their temperature/pressure derivatives at a reference state.
     *
     * <p>This method should be called by concrete implementations (RK, CEF, CVM)
     * during model initialization to populate the base class storage.
     *
     * @param elements    array of ElementGibbs objects, length must equal numComponents()
     * @param phaseName   the phase name for evaluation (e.g., "BCC_A2", "LIQUID")
     * @param refT        reference temperature in Kelvin (typically 298.15 K)
     * @throws IllegalArgumentException if elements.length != numComponents()
     */
    protected void populateG0Lists(ElementGibbs[] elements, String phaseName, double refT) {
        if (elements == null) {
            throw new IllegalArgumentException("elements array cannot be null");
        }
        if (phaseName == null || phaseName.isEmpty()) {
            throw new IllegalArgumentException("phaseName cannot be null or empty");
        }
        int nc = numComponents();
        if (elements.length != nc) {
            throw new IllegalArgumentException(
                    "elements.length (" + elements.length + ") must equal numComponents() (" + nc + ")");
        }

        // Evaluate G0 and its derivatives at reference temperature
        double[] g0List_new = new double[nc];
        double[] g0TList_new = new double[nc];
        double[] g0PList_new = new double[nc];

        for (int i = 0; i < nc; i++) {
            g0List_new[i] = elements[i].gibbs(phaseName, refT);
            // Numerical derivative: (G(T+h) - G(T-h)) / (2h)
            double h = 0.01;
            g0TList_new[i] = (elements[i].gibbs(phaseName, refT + h)
                            - elements[i].gibbs(phaseName, refT - h)) / (2.0 * h);
            // Note: dG0/dP is typically 0 for most models (volume term). Override in subclass if needed.
            g0PList_new[i] = 0.0;
        }

        // Store in base class
        setG0List(g0List_new);
        setG0TList(g0TList_new);
        setG0PList(g0PList_new);
    }

    // ══════════════════════════════════════════════════════════════════
    // G0 Convenience Getters (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Get G0 for a specific component.
     *
     * @param componentIndex index in [0, numComponents)
     * @return G0[componentIndex] in J/mol
     */
    public double getG0(int componentIndex) {
        if (g0List == null || componentIndex < 0 || componentIndex >= g0List.length) {
            throw new IllegalArgumentException("Invalid component index: " + componentIndex);
        }
        return g0List[componentIndex];
    }

    /**
     * Get G0 for a specific component by element symbol.
     *
     * @param elementSymbol element name, e.g. "NB", "TI"
     * @return G0 for that element in J/mol
     * @throws IllegalArgumentException if element not found
     */
    public double getG0(String elementSymbol) {
        ArrayList<String> elements = elementNames();
        int idx = elements.indexOf(elementSymbol.toUpperCase());
        if (idx < 0) {
            throw new IllegalArgumentException("Element not found: " + elementSymbol);
        }
        return getG0(idx);
    }

    /**
     * Get ∂G0/∂T for a specific component.
     *
     * @param componentIndex index in [0, numComponents)
     * @return ∂G0[componentIndex]/∂T in J/(mol·K)
     */
    public double getG0T(int componentIndex) {
        if (g0TList == null) return 0.0;
        if (componentIndex < 0 || componentIndex >= g0TList.length) {
            throw new IllegalArgumentException("Invalid component index: " + componentIndex);
        }
        return g0TList[componentIndex];
    }

    /**
     * Get ∂G0/∂T for a specific component by element symbol.
     *
     * @param elementSymbol element name, e.g. "NB", "TI"
     * @return ∂G0/∂T for that element in J/(mol·K)
     */
    public double getG0T(String elementSymbol) {
        ArrayList<String> elements = elementNames();
        int idx = elements.indexOf(elementSymbol.toUpperCase());
        if (idx < 0) {
            throw new IllegalArgumentException("Element not found: " + elementSymbol);
        }
        return getG0T(idx);
    }

    /**
     * Get ∂G0/∂P for a specific component.
     *
     * @param componentIndex index in [0, numComponents)
     * @return ∂G0[componentIndex]/∂P in J/(mol·Pa)
     */
    public double getG0P(int componentIndex) {
        if (g0PList == null) return 0.0;
        if (componentIndex < 0 || componentIndex >= g0PList.length) {
            throw new IllegalArgumentException("Invalid component index: " + componentIndex);
        }
        return g0PList[componentIndex];
    }

    /**
     * Get ∂G0/∂P for a specific component by element symbol.
     *
     * @param elementSymbol element name, e.g. "NB", "TI"
     * @return ∂G0/∂P for that element in J/(mol·Pa)
     */
    public double getG0P(String elementSymbol) {
        ArrayList<String> elements = elementNames();
        int idx = elements.indexOf(elementSymbol.toUpperCase());
        if (idx < 0) {
            throw new IllegalArgumentException("Element not found: " + elementSymbol);
        }
        return getG0P(idx);
    }

    // ══════════════════════════════════════════════════════════════════
    // G0 Computation (Concrete Implementation - FULL CODE HERE)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Compute total G0 (sum of pure components weighted by composition).
     * G0_total = Σ(x[i] · G0[i])
     *
     * @return total pure component Gibbs energy in J/mol
     */
    public double computeG0() {
        double sum = 0;
        if (g0List != null && x != null) {
            for (int i = 0; i < x.length; i++) {
                sum += x[i] * g0List[i];
            }
        }
        return sum;
    }

    /**
     * Compute ∂G0/∂T at current state.
     * ∂G0/∂T = Σ(x[i] · ∂G0[i]/∂T)
     *
     * @return temperature derivative in J/(mol·K)
     */
    public double computeG0T() {
        double sum = 0;
        if (g0TList == null || x == null) return 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += x[i] * g0TList[i];
        }
        return sum;
    }

    /**
     * Compute ∂G0/∂P at current state.
     * ∂G0/∂P = Σ(x[i] · ∂G0[i]/∂P)
     *
     * @return pressure derivative in J/(mol·Pa)
     */
    public double computeG0P() {
        double sum = 0;
        if (g0PList == null || x == null) return 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += x[i] * g0PList[i];
        }
        return sum;
    }

    /**
     * Compute ∂G0/∂x[i] (chemical potentials from pure components).
     * ∂G0/∂x[i] = G0[i]
     *
     * @return gradient vector, length numComponents
     */
    public double[] computeG0Gradient() {
        if (g0List == null) return new double[numComponents()];
        return g0List.clone();
    }

    /**
     * Compute ∂²G0/∂T∂x[i].
     * ∂²G0/∂T∂x[i] = ∂G0[i]/∂T
     *
     * @return second derivative vector, length numComponents
     */
    public double[] computeG0Tx() {
        if (g0TList == null) return new double[numComponents()];
        return g0TList.clone();
    }

    /**
     * Compute ∂²G0/∂P∂x[i].
     * ∂²G0/∂P∂x[i] = ∂G0[i]/∂P
     *
     * @return second derivative vector, length numComponents
     */
    public double[] computeG0Px() {
        if (g0PList == null) return new double[numComponents()];
        return g0PList.clone();
    }

    /**
     * Compute ∂²G0/∂x[i]∂x[j] (Hessian for pure component contribution).
     * Always zero — G0 is linear in composition.
     *
     * @return zero matrix, numComponents × numComponents
     */
    public double[][] computeG0Hessian() {
        int nc = numComponents();
        return new double[nc][nc];  // zero matrix
    }

    // ══════════════════════════════════════════════════════════════════
    // Ideal Mixing Entropy (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Gibbs energy of ideal mixing (common formula, can be overridden if needed).
     * Gm_ideal = RT Σ(xᵢ ln xᵢ)
     */
    public double evaluateIdealMixing() {
        double sum = 0;
        if (x != null) {
            for (int i = 0; i < x.length; i++) {
                if (x[i] > 1e-15) sum += x[i] * Math.log(x[i]);
            }
        }
        return 8.314 * T * sum;
    }

    // ══════════════════════════════════════════════════════════════════
    // Model-Specific G Evaluation (Abstract - Each Model Implements)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Compute total molar Gibbs energy (model-specific).
     * G = G0 + G_ideal_mixing + G_excess_model
     *
     * @return G in J/mol
     */
    public abstract double evaluateG();

    /**
     * Compute molar Gibbs energy at given composition and temperature (model-specific).
     *
     * @param x composition (mole fractions), length numComponents
     * @param T temperature in Kelvin
     * @return G in J/mol
     */
    public abstract double evaluateG(double[] x, double T);

    /**
     * Gradient dG/dx at given composition and temperature (model-specific).
     *
     * @param x composition, length numComponents
     * @param T temperature in Kelvin
     * @return dG/dx array, length numComponents
     */
    public abstract double[] gradient(double[] x, double T);

    /**
     * Hessian d²G/dxdx at given composition and temperature (model-specific).
     *
     * @param x composition, length numComponents
     * @param T temperature in Kelvin
     * @return d²G/dxdx matrix, numComponents × numComponents
     */
    public abstract double[][] hessian(double[] x, double T);

    /**
     * Compute ∂G/∂T at current state (model-specific).
     */
    public abstract double evaluateGT();

    /**
     * Compute ∂G/∂P at current state (model-specific).
     */
    public abstract double evaluateGP();

    /**
     * Compute ∂G/∂x[i] at current state (model-specific).
     */
    public abstract double[] evaluateGx();

    /**
     * Compute ∂²G/∂T∂x[i] at current state (model-specific).
     */
    public abstract double[] evaluateGTx();

    /**
     * Compute ∂²G/∂P∂x[i] at current state (model-specific).
     */
    public abstract double[] evaluateGPx();

    /**
     * Compute ∂²G/∂x[i]∂x[j] at current state (model-specific).
     */
    public abstract double[][] evaluateGxx();

    // ══════════════════════════════════════════════════════════════════
    // Derivative Caching (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    public void cacheDerivatives() {
        cachedG = evaluateG();
        cachedGT = evaluateGT();
        cachedGP = evaluateGP();
        cachedGx = evaluateGx();
        cachedGTx = evaluateGTx();
        cachedGPx = evaluateGPx();
        cachedGxx = evaluateGxx();
        derivativesCached = true;
    }

    public double getCachedG()      { return cachedG; }
    public double getCachedGT()     { return cachedGT; }
    public double getCachedGP()     { return cachedGP; }
    public double[] getCachedGx()   { return cachedGx.clone(); }
    public double[] getCachedGTx()  { return cachedGTx.clone(); }
    public double[] getCachedGPx()  { return cachedGPx.clone(); }
    public double[][] getCachedGxx() { return cloneMatrix(cachedGxx); }

    // ══════════════════════════════════════════════════════════════════
    // Internal Variable Management (Abstract - Each Model Implements)
    // ══════════════════════════════════════════════════════════════════

    public abstract double[] getInitialInternalVars(double[] x);
    public abstract double[] compositionFromInternal(double[] y);
    public abstract boolean isValid(double[] y);

    // ══════════════════════════════════════════════════════════════════
    // Equilibrium Matrix (Concrete Implementation)
    // ══════════════════════════════════════════════════════════════════

    public void setEquilibriumMatrix(double[][] emat) { this.eMat = cloneMatrix(emat); }
    public double[][] getEquilibriumMatrix()          { return cloneMatrix(eMat); }

    public void setConstraintGradients(double[] cg)    { this.cG = cg != null ? cg.clone() : null; }
    public double[] getConstraintGradients()           { return cG != null ? cG.clone() : null; }

    public void setConstraintTempDeriv(double[] ct)    { this.cT = ct != null ? ct.clone() : null; }
    public double[] getConstraintTempDeriv()           { return cT != null ? cT.clone() : null; }

    public void setConstraintPressDeriv(double[] cp)   { this.cP = cp != null ? cp.clone() : null; }
    public double[] getConstraintPressDeriv()          { return cP != null ? cP.clone() : null; }

    public void setABMatrix(double[][] cab)            { this.cAB = cloneMatrix(cab); }
    public double[][] getABMatrix()                    { return cloneMatrix(cAB); }

    // ══════════════════════════════════════════════════════════════════
    // Full Per-Phase Computation (Abstract - Each Model Implements)
    // ══════════════════════════════════════════════════════════════════

    public abstract PhaseEquilData compute(double T, double P, double[] y,
                                          double deltaT, double deltaP, double[] mu);

    // ══════════════════════════════════════════════════════════════════
    // Output / Debugging (Abstract - Each Model Implements)
    // ══════════════════════════════════════════════════════════════════

    public abstract void printPhaseInfo();
    public abstract void printDerivatives();

    // ══════════════════════════════════════════════════════════════════
    // Private Helpers
    // ══════════════════════════════════════════════════════════════════

    protected static double[][] cloneMatrix(double[][] mat) {
        if (mat == null) return null;
        double[][] clone = new double[mat.length][];
        for (int i = 0; i < mat.length; i++)
            clone[i] = mat[i].clone();
        return clone;
    }
}
