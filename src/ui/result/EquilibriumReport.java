package ui.result;

import system.ports.EquilibriumResult;
import system.ports.EquilibriumResult.PhaseResult;

import java.util.List;
import java.util.Locale;

/**
 * Formats a full, human-readable report of one {@link EquilibriumResult} --
 * this codebase's own equivalent of OpenCalphad's {@code l r <n>} console
 * dump, not a byte-for-byte replica of it.
 *
 * <p>The required content is Sundman 2021 Calphad 75, Section 3.1's own
 * specification of what a saved equilibrium must carry: <i>"a complete
 * description of the equilibrium... i.e. T, P, the amount and constitution
 * of all phases and the chemical potentials"</i> -- not OC's specific text
 * layout (its {@code --->OC6:} prompts, {@code 3Y}/echo lines, or
 * clock-cycle/CPU-time telemetry are OC console artifacts, not equilibrium
 * data the paper asks for, and are deliberately not reproduced here).
 *
 * <p>Beyond the paper's minimum, this report also surfaces data this
 * codebase's {@link EquilibriumResult} already computes but OC's own
 * {@code l r} dump does not print: per-phase driving force (OC treats this
 * as an internal solver quantity, not user-facing output), total system
 * Gibbs energy per mole of real atoms ({@link EquilibriumResult#totalGPerAtom()}
 * -- comparable across candidate phase sets with different formula-unit
 * sizes, unlike a raw per-formula-unit total), and solver iteration count
 * (this codebase's own convergence diagnostic).
 */
public final class EquilibriumReport {

    private EquilibriumReport() {
    }

    /**
     * Formats {@code result} into a multi-line report.
     *
     * @param result          the equilibrium to report on
     * @param componentNames  component symbols in the same order as
     *                        {@link EquilibriumResult#getMu()} and each
     *                        {@link PhaseResult#x}
     */
    public static String format(EquilibriumResult result, List<String> componentNames) {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, result);
        sb.append('\n');
        appendChemicalPotentials(sb, result, componentNames);
        sb.append('\n');
        appendSystemTotals(sb, result);
        sb.append('\n');
        appendPhases(sb, result.getStablePhases(), "Stable phases", componentNames);

        List<PhaseResult> metastable = result.getMetastablePhases();
        if (!metastable.isEmpty()) {
            sb.append('\n');
            appendPhases(sb, metastable, "Metastable phases", componentNames);
        }

        return sb.toString();
    }

    private static void appendHeader(StringBuilder sb, EquilibriumResult result) {
        sb.append(String.format(Locale.ROOT,
                "Equilibrium: T=%.2f K, P=%.4E Pa%n", result.getT(), result.getP()));
        sb.append(String.format(Locale.ROOT,
                "Converged: %s (%d iterations)%n",
                result.isConverged(), result.getIterations()));
    }

    private static void appendChemicalPotentials(
            StringBuilder sb, EquilibriumResult result, List<String> componentNames) {

        double[] mu = result.getMu();
        sb.append("Chemical potentials (mu/RT):\n");
        for (int i = 0; i < mu.length; i++) {
            String name = i < componentNames.size() ? componentNames.get(i) : "component" + i;
            sb.append(String.format(Locale.ROOT, "  %-6s %+.6E%n", name, mu[i]));
        }
    }

    private static void appendSystemTotals(StringBuilder sb, EquilibriumResult result) {
        sb.append("System totals:\n");
        sb.append(String.format(Locale.ROOT,
                "  G_sys      = %+.6E J (per formula unit basis)%n", result.totalG()));
        try {
            sb.append(String.format(Locale.ROOT,
                    "  G_sys/atom = %+.6E J/mol (comparable across candidate phase sets)%n",
                    result.totalGPerAtom()));
        } catch (IllegalStateException e) {
            sb.append("  G_sys/atom = n/a (no real atoms in the stable phase set)\n");
        }
    }

    private static void appendPhases(
            StringBuilder sb, List<PhaseResult> phases, String heading, List<String> componentNames) {

        sb.append(heading).append(":\n");
        for (PhaseResult ph : phases) {
            appendOnePhase(sb, ph, componentNames);
        }
    }

    private static void appendOnePhase(
            StringBuilder sb, PhaseResult ph, List<String> componentNames) {

        sb.append(String.format(Locale.ROOT,
                "  %-20s amount=%.4E f.u.  atoms=%.4E  G=%+.4E J/mol  driving force=%+.4E%n",
                ph.phaseName, ph.amount, ph.atoms(), ph.G, ph.drivingForce));

        sb.append("    x: ");
        for (int i = 0; i < ph.x.length; i++) {
            String name = i < componentNames.size() ? componentNames.get(i) : "component" + i;
            sb.append(String.format(Locale.ROOT, "%s=%.6f  ", name, ph.x[i]));
        }
        sb.append('\n');

        sb.append("    y: ");
        for (int i = 0; i < ph.y.length; i++) {
            sb.append(String.format(Locale.ROOT, "y[%d]=%.6f  ", i, ph.y[i]));
        }
        sb.append('\n');
    }
}
