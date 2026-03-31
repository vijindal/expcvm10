package system.model.unary;

import java.util.*;

/**
 * SGTE (Stenholm, Gibbs, Thermodynamic Evaluation) Gibbs energy evaluator
 * for a pure element across multiple phases.
 *
 * <p><b>SHARED MODEL LAYER CLASS</b> — This class implements the common G0
 * calculation used by ALL Gibbs energy models (RK, CEF, CVM, etc.).
 *
 * <h2>Design principle</h2>
 * G0 (reference Gibbs energy) is a <b>thermodynamic property</b>, not a
 * <b>database property</b>. All models need it:
 * <pre>
 *   G_total = G0(T) + G_ideal + G_excess
 * </pre>
 * - G0: shared by all models (evaluated from SGTE polynomial)
 * - G_ideal: shared formula (ideal mixing entropy)
 * - G_excess: model-specific (RK binary/ternary, CEF sites, CVM clusters)
 *
 * This class handles G0 evaluation; models add their own excess contributions.
 *
 * <h2>SGTE polynomial formula</h2>
 * <pre>
 *   G°(T) = a₀ + a₁·T + a₂·T·ln(T) + a₃·T² + a₄/T + a₅·T³ + ...
 * </pre>
 * Coefficients are valid within a temperature range [T_low, T_high].
 * Multiple ranges allow piecewise polynomials for phase transitions.
 *
 * <h2>Data flow (separation of concerns)</h2>
 * <pre>
 *   database/UnaryGibbsBuilder  ← Reads TDB file, determines reference phase, extracts coefficients
 *       ↓ (provides PhaseEntry data + refPhaseName)
 *   model/unary/SgteElementGibbs  ← Evaluates G°(T) from coefficients (no phase logic)
 *       ↓ (returns G0 values)
 *   model/rk/RkGibbs  ← Uses G0 in RK model (adds binary/ternary excess)
 *   model/cef/CefGibbs  ← Uses G0 in CEF model (adds sublattice excess)
 *   model/cvm/CvmGibbs  ← Uses G0 in CVM model (adds cluster excess)
 * </pre>
 *
 * This separation ensures:
 * - Database layer: handles TDB parsing, determines GHSER reference phase
 * - Model layer: pure thermodynamic calculation (no database knowledge)
 * - No hard-coded phase names in model layer
 *
 * <h2>Relationship to Mathematica</h2>
 * Directly translates {@code calG0[element, phase, T]} from CALPHAD codes.
 *
 * <h2>Layer placement (expCVM 10 architecture)</h2>
 * <b>Location:</b> {@code system/model/unary/} (model layer, NOT database layer)
 * <b>Reason:</b> G0 evaluation is a <b>thermodynamic calculation</b>, not a
 *             <b>database operation</b>. All Gibbs energy models (RK, CEF, CVM)
 *             need it; it should not be tied to database/ layer.
 */
public class SgteElementGibbs implements ElementGibbs {

    // ------------------------------------------------------------------
    // Inner class: compiled coefficient data for one phase
    // ------------------------------------------------------------------

    /**
     * Immutable coefficient data for one (element, phase) pair.
     * Contains multiple temperature ranges (e.g., solid below/above melting point).
     *
     * <p>Created by database layer from parsed TDB coefficients.
     */
    public static final class PhaseEntry {
        /** Temperature range lower bounds, length nRanges. */
        public final double[] tLow;

        /** Temperature range upper bounds, length nRanges. */
        public final double[] tHigh;

        /**
         * Polynomial coefficients: coeffs[rangeIdx][k] = a_k for range rangeIdx.
         * Each coefficient set allows evaluation via SgtePolynomial.evaluate().
         */
        public final double[][] coeffs;

        /**
         * Creates a phase entry from coefficient arrays.
         *
         * @param tLow   lower temperature bounds (must be sorted ascending)
         * @param tHigh  upper temperature bounds (must be sorted ascending)
         * @param coeffs polynomial coefficients; coeffs[i] applies to range [tLow[i], tHigh[i]]
         */
        public PhaseEntry(double[] tLow, double[] tHigh, double[][] coeffs) {
            if (tLow.length != tHigh.length || tLow.length != coeffs.length) {
                throw new IllegalArgumentException(
                        "tLow, tHigh, and coeffs arrays must have the same length");
            }
            this.tLow = tLow.clone();
            this.tHigh = tHigh.clone();
            this.coeffs = new double[coeffs.length][];
            for (int i = 0; i < coeffs.length; i++) {
                this.coeffs[i] = coeffs[i].clone();
            }
        }

        /**
         * Evaluates G°(T) using the appropriate temperature range.
         * Selects the first range whose upper bound exceeds T.
         * Falls back to the last range if T is above all ranges (extrapolation).
         *
         * @param T temperature in Kelvin
         * @return G°(T) in J/mol
         */
        public double evaluate(double T) {
            for (int i = 0; i < tHigh.length - 1; i++) {
                if (T < tHigh[i]) return SgtePolynomial.evaluate(coeffs[i], T);
            }
            return SgtePolynomial.evaluate(coeffs[coeffs.length - 1], T);
        }

        // Accessors for inspection
        public int numRanges() { return tLow.length; }
        public double tLow(int i) { return tLow[i]; }
        public double tHigh(int i) { return tHigh[i]; }
        public double[] coeffs(int i) { return coeffs[i]; }
    }

    // ------------------------------------------------------------------
    // Instance state
    // ------------------------------------------------------------------

    private final String symbol;
    private final Map<String, PhaseEntry> phases;  // phase name (upper-case) → entry
    private final String refPhase;                 // GHSER reference phase (provided by caller)

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Creates an SGTE element Gibbs energy evaluator.
     *
     * <p>The reference phase name is provided by the caller (database layer knows it).
     * This class simply evaluates G°(T) — it does NOT determine the reference phase.
     *
     * @param symbol        element symbol (case-insensitive, stored upper-case)
     * @param phaseData     map from phase name (upper-case) to PhaseEntry data
     *                      (populated from database layer)
     * @param refPhaseName  GHSER reference phase name (must be a key in phaseData)
     *                      Provided by database layer; this class does not determine it.
     * @throws IllegalArgumentException if refPhaseName is not in phaseData
     */
    public SgteElementGibbs(String symbol, Map<String, PhaseEntry> phaseData,
                            String refPhaseName) {
        this.symbol = symbol.toUpperCase();
        this.phases = new LinkedHashMap<>(phaseData);

        String refKey = refPhaseName.toUpperCase();
        if (!phases.containsKey(refKey)) {
            throw new IllegalArgumentException(
                    "Reference phase '" + refPhaseName + "' not in available phases: "
                    + phases.keySet());
        }
        this.refPhase = refKey;
    }

    // ------------------------------------------------------------------
    // ElementGibbs implementation
    // ------------------------------------------------------------------

    @Override
    public String elementSymbol() {
        return symbol;
    }

    @Override
    public double gibbs(String phaseName, double T) {
        String key = phaseName.toUpperCase();
        PhaseEntry entry = phases.get(key);
        if (entry == null) {
            throw new UnsupportedOperationException(
                    "Phase '" + phaseName + "' not available for element '" + symbol
                    + "'. Available: " + phases.keySet());
        }
        return entry.evaluate(T);
    }

    @Override
    public double ghser(double T) {
        return gibbs(refPhase, T);
    }

    @Override
    public Set<String> availablePhases() {
        return Collections.unmodifiableSet(phases.keySet());
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    /**
     * Returns the GHSER reference phase name for this element.
     */
    public String getReferencePhase() {
        return refPhase;
    }

    /**
     * Returns the phase entry for a given phase (if present).
     */
    public PhaseEntry getPhaseEntry(String phaseName) {
        return phases.get(phaseName.toUpperCase());
    }
}
