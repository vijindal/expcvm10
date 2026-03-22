package thermocalc.diagram;

import domain.EquilibriumResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A sequence of equilibrium points forming one ZPF line on the phase diagram.
 *
 * <p>Along the entire line:
 * <ul>
 *   <li>The stable phase set is constant ({@link #stablePhaseSet}).</li>
 *   <li>The ZPF phase ({@link #fixedPhase}) has zero amount at every point.</li>
 * </ul>
 *
 * <p>The line starts at {@link #startNode} and ends at {@link #endNode}
 * (which may be {@code null} if the line terminates at an axis boundary
 * rather than a proper phase-change node).
 */
public class DiagramLine {

    /** Node where this ZPF line originates. */
    public final DiagramNode startNode;

    /**
     * Node where this ZPF line terminates.
     * {@code null} until the line is fully traced (it may end at an axis limit
     * rather than at a proper diagram node).
     */
    public DiagramNode endNode;

    /**
     * The ZPF phase: held at exactly zero amount along this line.
     * Matches {@link DiagramExit#fixedPhase} of the exit that spawned this line.
     */
    public final String fixedPhase;

    /**
     * Names of the phases that are stable (amount > 0) along this line.
     * Constant throughout — if the set changes, a new node and line begin.
     */
    public final Set<String> stablePhaseSet;

    /**
     * Set to {@code true} by {@link LineStepper} when a positive driving
     * force signals that a phase wants to enter — i.e. the line ended at
     * a phase change rather than an axis boundary.
     * PhaseChangeHandler (Phase 7) uses this to bisect and create a new node.
     */
    public boolean phaseChangeDetected = false;

    /** Ordered sequence of equilibrium results sampled along the line. */
    private final List<EquilibriumResult> points = new ArrayList<>();

    /**
     * Axis coordinate arrays for each sampled point, parallel to {@link #points}.
     * Each entry has length = number of diagram axes.
     */
    private final List<double[]> axisCoords = new ArrayList<>();

    public DiagramLine(DiagramNode startNode, String fixedPhase,
                       Set<String> stablePhaseSet) {
        this.startNode      = startNode;
        this.fixedPhase     = fixedPhase;
        this.stablePhaseSet = Collections.unmodifiableSet(
                new LinkedHashSet<>(stablePhaseSet));
    }

    /** Append a sampled equilibrium point with its axis coordinates. */
    public void addPoint(EquilibriumResult eq, double[] axes) {
        points.add(eq);
        axisCoords.add(axes.clone());
    }

    /** Returns all sampled equilibrium results along this line. */
    public List<EquilibriumResult> getPoints() {
        return Collections.unmodifiableList(points);
    }

    /** Returns the axis coordinate arrays for each sampled point. */
    public List<double[]> getAxisCoords() {
        return Collections.unmodifiableList(axisCoords);
    }

    /** Number of points sampled along this line. */
    public int size() {
        return points.size();
    }

    @Override
    public String toString() {
        return "Line[" + startNode.id + "→"
                + (endNode != null ? endNode.id : "?")
                + " fixed=" + fixedPhase
                + " stable=" + stablePhaseSet
                + " pts=" + points.size() + "]";
    }
}
