package calc.diagram;

import contracts.EquilibriumResult;

import java.util.ArrayList;
import java.util.List;

/**
 * A point on a phase diagram where the stable phase set changes.
 *
 * <p>Nodes arise at:
 * <ul>
 *   <li>Phase boundaries (one phase appears or disappears — ZPF crossing)</li>
 *   <li>Invariant points (eutectic, peritectic — f = 0)</li>
 *   <li>The start/end of a ZPF line at an axis limit</li>
 * </ul>
 *
 * <p>Each node has one or more {@link DiagramExit}s representing the ZPF
 * lines that leave it.  Algorithm B processes unvisited exits until all are
 * exhausted.
 */
public class DiagramNode {

    /** Sequential identifier assigned by {@link PhaseDiagram}. */
    public final int id;

    /** The equilibrium state at this node. */
    public final EquilibriumResult equilibrium;

    /**
     * Axis coordinates at this node, length = number of diagram axes.
     * e.g. {@code [T, x_B]} for a binary T-x diagram.
     */
    public final double[] axisValues;

    /** ZPF exits from this node (filled by PhaseChangeHandler / InvariantHandler). */
    private final List<DiagramExit> exits = new ArrayList<>();

    public DiagramNode(int id, EquilibriumResult equilibrium, double[] axisValues) {
        this.id           = id;
        this.equilibrium  = equilibrium;
        this.axisValues   = axisValues.clone();
    }

    /** Attach a new exit to this node. */
    public void addExit(DiagramExit exit) {
        exits.add(exit);
    }

    /** Returns all exits (visited and unvisited). */
    public List<DiagramExit> getExits() {
        return exits;
    }

    /** Returns {@code true} if at least one exit has not yet been followed. */
    public boolean hasUnvisitedExits() {
        for (DiagramExit e : exits) {
            if (!e.visited) return true;
        }
        return false;
    }

    /**
     * Returns the next unvisited exit, or {@code null} if all are visited.
     * Does NOT mark it as visited — the caller must do that when starting
     * to follow the exit.
     */
    public DiagramExit nextUnvisitedExit() {
        for (DiagramExit e : exits) {
            if (!e.visited) return e;
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Node[").append(id).append("] axes=(");
        for (int i = 0; i < axisValues.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.4g", axisValues[i]));
        }
        sb.append(") exits=").append(exits.size());
        return sb.toString();
    }
}
