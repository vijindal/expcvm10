package calc.diagram;

import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A point on the ZPF-line network where the stable phase set changes --
 * Sundman 2021 Calphad 75, Section 3.1's "node point," modeled on
 * OpenCalphad's {@code map_node} record ({@code src/stepmapplot/smp2.F90},
 * module {@code ocsmp}).
 *
 * <p>Per {@code docs/phase_diagram_engine_flowchart.md}'s Data Structures
 * section: a node owns the {@link Line}s that exit it (OC's {@code
 * linehead} array) -- there is no separate "Exit" type. A node's identity
 * (used to recognize the same physical node reached by two different
 * lines) is its stable phase set plus its chemical potentials, per
 * Sundman 2021 Section 3.1's prose description and OC's {@code
 * map_node%stable_phases}/{@code chempots} fields. The exact numeric
 * matching rule is this codebase's own choice (see {@link #matches},
 * and "Open implementation choices" in the flowchart doc) -- neither the
 * paper nor OC's source specifies a tolerance.
 *
 * <p><b>Known limitation carried from {@link EquilibriumResult}:</b> a
 * stable phase set here is a {@link Set} of phase names, which cannot
 * distinguish two stable slots of the same phase (a miscibility gap,
 * e.g. two FCC compositions both named "FCC_A1") -- {@link
 * EquilibriumResult.PhaseResult} does not carry a composition-set index
 * (OC's {@code #<digit>} suffix). This mirrors the same known gap in
 * {@code EquilibriumSolverV2}/{@code GridMinimizer} (see README's
 * "Current limitations"), not something introduced here.
 */
public final class Node {

    /** Sequential identifier assigned by whatever registry creates this node. */
    public final int id;

    /** The converged equilibrium at this node. */
    public final EquilibriumResult equilibrium;

    /**
     * Axis coordinates at this node, length = number of diagram axes
     * (1 for step, 2 for map).
     */
    public final double[] axisValues;

    /**
     * Names of phases stable (amount &gt; 0) at this node -- part of this
     * node's identity. See the class javadoc's miscibility-gap caveat.
     */
    public final Set<String> stablePhaseNames;

    /** Chemical potentials at this node, one per component -- part of this node's identity. */
    public final double[] chemicalPotentials;

    /** Lines attached to this node (both pending and walked); see {@link Line}. */
    private final List<Line> lines = new ArrayList<>();

    public Node(int id, EquilibriumResult equilibrium, double[] axisValues) {
        this.id = id;
        this.equilibrium = equilibrium;
        this.axisValues = axisValues.clone();
        this.chemicalPotentials = equilibrium.getMu();

        Set<String> names = new LinkedHashSet<>();
        for (EquilibriumResult.PhaseResult pr : equilibrium.getStablePhases()) {
            names.add(pr.phaseName);
        }
        this.stablePhaseNames = names;
    }

    /** Attach a line (pending or already walked) to this node. */
    public void addLine(Line line) {
        lines.add(line);
    }

    /** All lines attached to this node, pending and walked alike. */
    public List<Line> getLines() {
        return lines;
    }

    /** True if at least one attached line is still {@link Line.State#PENDING}. */
    public boolean hasPendingLine() {
        for (Line l : lines) {
            if (l.getState() == Line.State.PENDING) return true;
        }
        return false;
    }

    /** The first attached line still {@link Line.State#PENDING}, or {@code null}. */
    public Line nextPendingLine() {
        for (Line l : lines) {
            if (l.getState() == Line.State.PENDING) return l;
        }
        return null;
    }

    /**
     * This codebase's node-matching rule (an open implementation choice --
     * see the class javadoc): two nodes are considered the same physical
     * node if their stable phase sets are identical and every chemical
     * potential agrees within {@code relativeTolerance} (relative to the
     * magnitude of the potential being compared, floored at 1.0 to avoid
     * a division blowup near mu == 0).
     *
     * @param other              the node to compare against
     * @param relativeTolerance  e.g. {@code 1e-4}, tied to the equilibrium
     *                           solver's own convergence tolerance
     */
    public boolean matches(Node other, double relativeTolerance) {
        if (!this.stablePhaseNames.equals(other.stablePhaseNames)) {
            return false;
        }
        if (this.chemicalPotentials.length != other.chemicalPotentials.length) {
            return false;
        }
        for (int i = 0; i < this.chemicalPotentials.length; i++) {
            double a = this.chemicalPotentials[i];
            double b = other.chemicalPotentials[i];
            double scale = Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
            if (Math.abs(a - b) / scale > relativeTolerance) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Node[").append(id).append("] axes=(");
        for (int i = 0; i < axisValues.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6g", axisValues[i]));
        }
        sb.append(") stable=").append(stablePhaseNames)
          .append(" lines=").append(lines.size());
        return sb.toString();
    }
}
