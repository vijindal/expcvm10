package calc.diagram;

import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.List;

/**
 * A ZPF line as this codebase's engine tracks it -- Sundman 2021 Calphad
 * 75, Section 3.1's "line," modeled on OpenCalphad's {@code map_line}
 * record ({@code src/stepmapplot/smp2.F90}).
 *
 * <p>Per {@code docs/phase_diagram_engine_flowchart.md}: there is no
 * separate "Exit" type. A {@code Line} in {@link State#PENDING} — created
 * attached to a {@link Node} with only its starting axis/direction/fixed-
 * phase known, not yet walked — <em>is</em> what the Sundman 2021 prose
 * calls an "exit." Once walked it holds a range of {@link
 * EquilibriumResult} points and (once it terminates) an end node.
 *
 * <p><b>Scope: 1-2 axes only</b> (see the flowchart's scope note) — {@link
 * #fixedPhases} holds at most one phase for every target diagram type in
 * {@code docs/roadmap_phase_diagrams.md}. OC's {@code map_fixph} allows
 * more for 3+ axes, not needed here.
 */
public final class Line {

    /** State of a {@link Line} -- see the class javadoc. */
    public enum State {
        /** Attached to its start node; axis/direction/fixed-phase known, not yet walked. */
        PENDING,
        /** Actively being walked (equilibria appended as they're solved). */
        WALKING,
        /** Reached an axis limit or a stable-set change; {@link #endNode} may be set. */
        TERMINATED
    }

    /** Node this line starts from. */
    public final Node startNode;

    /**
     * Phase(s) held at zero amount along this line (the ZPF constraint).
     * Empty for a step line (no ZPF fixing at all, per the flowchart's
     * step/map distinction). At most one entry for every diagram type in
     * the roadmap (see class javadoc).
     */
    public final List<String> fixedPhases;

    /** Index into the diagram's axis array of the axis to vary first. */
    public final int initialAxisIndex;

    /** Direction to step along {@link #initialAxisIndex}: +1 or -1. */
    public final int direction;

    private State state = State.PENDING;

    /** Node this line terminates at, once {@link State#TERMINATED} with a real endpoint. */
    private Node endNode;

    /** Equilibria sampled along this line, in walk order. */
    private final List<EquilibriumResult> points = new ArrayList<>();

    /** Axis-coordinate arrays parallel to {@link #points}. */
    private final List<double[]> axisCoords = new ArrayList<>();

    public Line(Node startNode, List<String> fixedPhases,
                int initialAxisIndex, int direction) {
        this.startNode = startNode;
        this.fixedPhases = List.copyOf(fixedPhases);
        this.initialAxisIndex = initialAxisIndex;
        this.direction = direction;
    }

    public State getState() {
        return state;
    }

    /** Mark this line as actively being walked. Only valid from {@link State#PENDING}. */
    public void startWalking() {
        if (state != State.PENDING) {
            throw new IllegalStateException("Line already " + state);
        }
        state = State.WALKING;
    }

    /** Append one sampled equilibrium point with its axis coordinates. */
    public void addPoint(EquilibriumResult eq, double[] axisValues) {
        if (state != State.WALKING) {
            throw new IllegalStateException("Cannot add a point to a line in state " + state);
        }
        points.add(eq);
        axisCoords.add(axisValues.clone());
    }

    /** Terminate this line at an axis limit, with no end node. */
    public void terminateAtAxisLimit() {
        this.endNode = null;
        this.state = State.TERMINATED;
    }

    /** Terminate this line at a real end node (a stable-set change was located). */
    public void terminateAtNode(Node endNode) {
        this.endNode = endNode;
        this.state = State.TERMINATED;
    }

    /** The node this line ends at, or {@code null} if terminated at an axis limit or not yet terminated. */
    public Node getEndNode() {
        return endNode;
    }

    public List<EquilibriumResult> getPoints() {
        return List.copyOf(points);
    }

    public List<double[]> getAxisCoords() {
        return List.copyOf(axisCoords);
    }

    public int size() {
        return points.size();
    }

    @Override
    public String toString() {
        return "Line[" + startNode.id + "->"
                + (endNode != null ? endNode.id : "?")
                + " state=" + state
                + " fixed=" + fixedPhases
                + " pts=" + points.size() + "]";
    }
}
