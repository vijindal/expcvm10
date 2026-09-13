package ui.layer;

import java.util.List;
import java.util.Map;

/**
 * Mole-fraction <-> mass-percent composition conversion, shared by any UI
 * (CLI/GUI/API) that wants to accept or display composition in mass units
 * rather than mole fraction.
 *
 * <p>Deliberately NOT part of {@link session.calctype.CalculationInterface}:
 * that class's entire contract is collecting already-mole-fraction params
 * from a UI, passing them to {@code CalculationSession}, and passing the
 * result straight back -- a thin, auditable pass-through, not a place for
 * unit-conversion logic. Composition units are a UI-facing presentation
 * concern (what a user types/reads), not a calculation concern (the
 * solver itself only ever works in mole fraction), so this lives here
 * instead, alongside {@link ModelBrowseService}.
 *
 * <p>Uses the standard mass-fraction/mole-fraction conversion (matches
 * pycalphad's {@code variables.get_mole_fractions}/{@code
 * get_mass_fractions}): {@code moles_i = mass_i / M_i}, normalized.
 *
 * <p>Atomic masses come from {@link #ATOMIC_MASS}, a fixed standard
 * atomic-weight table (IUPAC conventional values, g/mol) -- NOT from
 * whatever mass a specific loaded TDB file declares for that element in
 * its {@code ELEMENT} record. A TDB's own declared mass can differ
 * slightly from the standard table (e.g. one database's {@code ELEMENT
 * FE ... 5.5847E+01 ...} vs. another's 55.845), so composition unit
 * conversions here are consistent across every database rather than
 * varying per TDB, at the cost of not always matching a specific TDB's
 * own value exactly.
 */
public final class CompositionUnits {

    private CompositionUnits() {
    }

    /**
     * Standard atomic weights (g/mol), IUPAC conventional values, keyed by
     * element symbol in upper case (matching TDB {@code ELEMENT} record
     * symbols). Covers the common CALPHAD/metallurgy elements; not
     * exhaustive of the periodic table.
     */
    public static final Map<String, Double> ATOMIC_MASS = Map.ofEntries(
            Map.entry("H", 1.008), Map.entry("HE", 4.0026), Map.entry("LI", 6.94),
            Map.entry("BE", 9.0122), Map.entry("B", 10.81), Map.entry("C", 12.011),
            Map.entry("N", 14.007), Map.entry("O", 15.999), Map.entry("F", 18.998),
            Map.entry("NE", 20.180), Map.entry("NA", 22.990), Map.entry("MG", 24.305),
            Map.entry("AL", 26.982), Map.entry("SI", 28.085), Map.entry("P", 30.974),
            Map.entry("S", 32.06), Map.entry("CL", 35.45), Map.entry("AR", 39.948),
            Map.entry("K", 39.098), Map.entry("CA", 40.078), Map.entry("SC", 44.956),
            Map.entry("TI", 47.867), Map.entry("V", 50.942), Map.entry("CR", 51.996),
            Map.entry("MN", 54.938), Map.entry("FE", 55.845), Map.entry("CO", 58.933),
            Map.entry("NI", 58.693), Map.entry("CU", 63.546), Map.entry("ZN", 65.38),
            Map.entry("GA", 69.723), Map.entry("GE", 72.630), Map.entry("AS", 74.922),
            Map.entry("SE", 78.971), Map.entry("BR", 79.904), Map.entry("KR", 83.798),
            Map.entry("RB", 85.468), Map.entry("SR", 87.62), Map.entry("Y", 88.906),
            Map.entry("ZR", 91.224), Map.entry("NB", 92.906), Map.entry("MO", 95.95),
            Map.entry("TC", 98.0), Map.entry("RU", 101.07), Map.entry("RH", 102.91),
            Map.entry("PD", 106.42), Map.entry("AG", 107.87), Map.entry("CD", 112.41),
            Map.entry("IN", 114.82), Map.entry("SN", 118.71), Map.entry("SB", 121.76),
            Map.entry("TE", 127.60), Map.entry("I", 126.90), Map.entry("XE", 131.29),
            Map.entry("CS", 132.91), Map.entry("BA", 137.33), Map.entry("LA", 138.91),
            Map.entry("CE", 140.12), Map.entry("PR", 140.91), Map.entry("ND", 144.24),
            Map.entry("SM", 150.36), Map.entry("EU", 151.96), Map.entry("GD", 157.25),
            Map.entry("TB", 158.93), Map.entry("DY", 162.50), Map.entry("HO", 164.93),
            Map.entry("ER", 167.26), Map.entry("TM", 168.93), Map.entry("YB", 173.05),
            Map.entry("LU", 174.97), Map.entry("HF", 178.49), Map.entry("TA", 180.95),
            Map.entry("W", 183.84), Map.entry("RE", 186.21), Map.entry("OS", 190.23),
            Map.entry("IR", 192.22), Map.entry("PT", 195.08), Map.entry("AU", 196.97),
            Map.entry("HG", 200.59), Map.entry("TL", 204.38), Map.entry("PB", 207.2),
            Map.entry("BI", 208.98), Map.entry("TH", 232.04), Map.entry("PA", 231.04),
            Map.entry("U", 238.03)
    );

    /**
     * Converts weight fractions (0-1, {@code elements} order) to mole
     * fractions using {@link #ATOMIC_MASS}: {@code moles_i = w_i / M_i},
     * then normalized so the result sums to 1.
     *
     * @throws IllegalArgumentException if any element's mass is unknown
     */
    public static double[] weightToMoleFractions(double[] weightFractions, List<String> elements) {
        double[] moles = new double[weightFractions.length];
        double totalMoles = 0.0;
        for (int i = 0; i < elements.size(); i++) {
            double mass = massOf(elements.get(i));
            moles[i] = weightFractions[i] / mass;
            totalMoles += moles[i];
        }
        double[] moleFractions = new double[moles.length];
        for (int i = 0; i < moles.length; i++) {
            moleFractions[i] = moles[i] / totalMoles;
        }
        return moleFractions;
    }

    /**
     * Converts mole fractions ({@code elements} order) to mass percent
     * (0-100) using {@link #ATOMIC_MASS}: {@code mass_i = x_i * M_i},
     * normalized to 100. Returns {@code null} (rather than throwing) if
     * any element's mass is unknown or the total mass is not positive,
     * since callers typically use this for display and prefer to omit
     * mass% over failing outright.
     */
    public static double[] moleToMassPercent(double[] moleFractions, List<String> elements) {
        double[] mass = new double[moleFractions.length];
        double totalMass = 0.0;
        for (int i = 0; i < elements.size() && i < moleFractions.length; i++) {
            Double m = ATOMIC_MASS.get(elements.get(i).toUpperCase(java.util.Locale.ROOT));
            if (m == null) return null;
            mass[i] = moleFractions[i] * m;
            totalMass += mass[i];
        }
        if (!(totalMass > 0.0)) return null;
        double[] massPct = new double[mass.length];
        for (int i = 0; i < mass.length; i++) {
            massPct[i] = 100.0 * mass[i] / totalMass;
        }
        return massPct;
    }

    /**
     * Average atomic mass (g/mol) of a composition -- {@code Σ x_i * M_i}
     * using {@link #ATOMIC_MASS}, {@code elements} order matching {@code
     * moleFractions}. Used to weight a phase's real-atom amount ({@code
     * PhaseResult#atoms()}) into a real mass, for relative phase-amount
     * reporting (mass% of system, alongside atomic% of system). Returns
     * {@code NaN} if any element's mass is unknown.
     */
    public static double averageAtomicMass(double[] moleFractions, List<String> elements) {
        double mass = 0.0;
        for (int i = 0; i < elements.size() && i < moleFractions.length; i++) {
            Double m = ATOMIC_MASS.get(elements.get(i).toUpperCase(java.util.Locale.ROOT));
            if (m == null) return Double.NaN;
            mass += moleFractions[i] * m;
        }
        return mass;
    }

    /**
     * Converts mole fractions ({@code elements} order) to a weight-percent
     * CSV string (4 decimal places) -- convenience for showing a
     * meaningful wt.% prompt default derived from a mole-fraction default.
     * Returns {@code null} if conversion fails (e.g. an unknown element),
     * leaving the caller to fall back to something else.
     */
    public static String moleFractionsToWeightPercentCsv(double[] moleFractions, List<String> elements) {
        double[] massPct = moleToMassPercent(moleFractions, elements);
        if (massPct == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < massPct.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(java.util.Locale.ROOT, "%.4f", massPct[i]));
        }
        return sb.toString();
    }

    private static double massOf(String element) {
        Double mass = ATOMIC_MASS.get(element.toUpperCase(java.util.Locale.ROOT));
        if (mass == null) {
            throw new IllegalArgumentException("No standard atomic mass known for element " + element);
        }
        return mass;
    }
}
