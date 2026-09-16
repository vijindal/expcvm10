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
 * map_node%stable_phases}/{@code chempots} fields. {@link #matches}'
 * numeric rule is a direct port of OC's own realization of that
 * description, {@code map_newnode} ({@code src/stepmapplot/smp2A.F90}):
 * T and P are compared FIRST (a node search short-circuits to the next
 * candidate the instant T or P disagrees), then every chemical
 * potential -- both gates required, in that order, before two nodes are
 * considered the same, and both scaled relative to the CANDIDATE node's
 * own value (not symmetrically, or against the incoming equilibrium) --
 * see {@code map_newnode}'s {@code abs(vz*mapnode%tpval(1))}/{@code
 * abs(2.0D1*vz*mapnode%chempots(nel))}. Default tolerances mirror OC's
 * own constants ({@code vz=1.0D-8} for T/P, {@code 20*vz=2.0D-7} for
 * mu) -- see {@link #DEFAULT_TP_RELATIVE_TOLERANCE}/{@link
 * #DEFAULT_MU_RELATIVE_TOLERANCE}. These are deliberately tight: they
 * exist to recognize two independent EXACT Newton solves of the same
 * physical point as numerically identical, not to bridge two genuinely
 * different points on a ZPF line found via different walk directions or
 * step sizes.
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

    /**
     * Overall system composition at this node (mole fractions, one per
     * component). Not generally recoverable from {@link #equilibrium}
     * alone for a multi-phase node (it would require each stable phase's
     * amount to already be normalized against a known total), so callers
     * that know it (e.g. {@link MapDiagramTracer}, which tracks it
     * explicitly through the walk) must supply it.
     */
    public final double[] overallComposition;

    /** Lines attached to this node (both pending and walked); see {@link Line}. */
    private final List<Line> lines = new ArrayList<>();

    public Node(int id, EquilibriumResult equilibrium, double[] axisValues) {
        this(id, equilibrium, axisValues, null);
    }

    public Node(int id, EquilibriumResult equilibrium, double[] axisValues, double[] overallComposition) {
        this.id = id;
        this.equilibrium = equilibrium;
        this.axisValues = axisValues.clone();
        this.chemicalPotentials = equilibrium.getMu();
        this.overallComposition = overallComposition == null ? null : overallComposition.clone();

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

    /** OC's own T/P node-matching relative tolerance ({@code vz}, {@code map_newnode}, smp2A.F90). */
    public static final double DEFAULT_TP_RELATIVE_TOLERANCE = 1.0e-8;

    /** OC's own chemical-potential node-matching relative tolerance ({@code 20*vz}, {@code map_newnode}). */
    public static final double DEFAULT_MU_RELATIVE_TOLERANCE = 2.0e-7;

    /**
     * Two nodes are considered the same physical node under OC's own
     * {@code map_newnode} rule (see the class javadoc): stable phase sets
     * identical, AND T and P agree within {@code tpRelativeTolerance},
     * AND every chemical potential agrees within {@code
     * muRelativeTolerance} -- both tolerances scaled relative to THIS
     * node's own value (mirroring {@code map_newnode}'s {@code
     * mapnode%tpval}/{@code mapnode%chempots} as the scale, i.e. the
     * EXISTING registered node being searched, not the incoming
     * candidate), floored at 1.0 to avoid a division blowup near zero.
     *
     * @param other                the node to compare against
     * @param tpRelativeTolerance  e.g. {@link #DEFAULT_TP_RELATIVE_TOLERANCE}
     * @param muRelativeTolerance  e.g. {@link #DEFAULT_MU_RELATIVE_TOLERANCE}
     */
    public boolean matches(Node other, double tpRelativeTolerance, double muRelativeTolerance) {
        if (!this.stablePhaseNames.equals(other.stablePhaseNames)) {
            return false;
        }
        if (!withinRelativeTolerance(other.equilibrium.getT(), this.equilibrium.getT(), tpRelativeTolerance)) {
            return false;
        }
        if (!withinRelativeTolerance(other.equilibrium.getP(), this.equilibrium.getP(), tpRelativeTolerance)) {
            return false;
        }
        if (this.chemicalPotentials.length != other.chemicalPotentials.length) {
            return false;
        }
        for (int i = 0; i < this.chemicalPotentials.length; i++) {
            if (!withinRelativeTolerance(
                    other.chemicalPotentials[i], this.chemicalPotentials[i], muRelativeTolerance)) {
                return false;
            }
        }
        return true;
    }

    /**
     * As {@link #matches(Node, double, double)}, using OC's own default
     * tolerances ({@link #DEFAULT_TP_RELATIVE_TOLERANCE}/{@link
     * #DEFAULT_MU_RELATIVE_TOLERANCE}) for both gates.
     */
    public boolean matches(Node other) {
        return matches(other, DEFAULT_TP_RELATIVE_TOLERANCE, DEFAULT_MU_RELATIVE_TOLERANCE);
    }

    /**
     * True if {@code candidate} is within {@code relativeTolerance} of
     * {@code reference}, scaled by {@code reference}'s own magnitude
     * (floored at 1.0) -- {@code map_newnode}'s {@code
     * abs(x-mapnode%field).gt.abs(tol*mapnode%field)} test, restated as
     * "within," with the existing registered node ({@code reference})
     * always supplying the scale.
     */
    private static boolean withinRelativeTolerance(
            double candidate, double reference, double relativeTolerance) {
        double scale = Math.max(1.0, Math.abs(reference));
        return Math.abs(candidate - reference) <= scale * relativeTolerance;
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
