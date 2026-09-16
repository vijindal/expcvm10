package calc.diagram;

import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The global node registry Algorithm C1/C2 search, per Sundman 2021
 * Section 3.1 ("All node points are stored in a list searched by
 * algorithms C1 and C2") and OpenCalphad's {@code map_node%first/next/
 * previous} doubly-linked list ({@code src/stepmapplot/smp2.F90}).
 *
 * <p>A {@link java.util.List} is used here rather than a linked-list
 * structure -- the doubly-linked list in OC's Fortran is a mechanical
 * necessity of that language/era, not a requirement this codebase needs
 * to reproduce; {@link #hasPendingWork}/{@link #nextPendingLine} do a
 * linear scan either way, per the flowchart's "per-node pending
 * bookkeeping, not one flat queue" design.
 */
public final class NodeRegistry {

    private final List<Node> nodes = new ArrayList<>();
    private int nextId = 0;

    /**
     * Algorithm C2's "already found?" check (Fig. 6): returns an existing
     * node matching {@code equilibrium} within OC's own default
     * tolerances (see {@link Node#matches(Node)}), or registers and
     * returns a new one.
     *
     * @param overallComposition the overall system composition at this
     *                           point (see {@link Node#overallComposition});
     *                           may be {@code null} if the caller does not
     *                           track it
     */
    public Node findOrCreate(EquilibriumResult equilibrium, double[] axisValues,
                              double[] overallComposition) {
        return findOrCreate(equilibrium, axisValues, overallComposition,
                Node.DEFAULT_TP_RELATIVE_TOLERANCE, Node.DEFAULT_MU_RELATIVE_TOLERANCE);
    }

    /**
     * As {@link #findOrCreate(EquilibriumResult, double[], double[])},
     * with explicit T/P and chemical-potential tolerances (see {@link
     * Node#matches(Node, double, double)}).
     */
    public Node findOrCreate(EquilibriumResult equilibrium, double[] axisValues,
                              double[] overallComposition,
                              double tpRelativeTolerance, double muRelativeTolerance) {
        Node candidate = new Node(-1, equilibrium, axisValues, overallComposition);
        for (Node existing : nodes) {
            if (existing.matches(candidate, tpRelativeTolerance, muRelativeTolerance)) {
                return existing;
            }
        }
        Node created = new Node(nextId++, equilibrium, axisValues, overallComposition);
        nodes.add(created);
        return created;
    }

    /** All registered nodes, in creation order. */
    public List<Node> getNodes() {
        return List.copyOf(nodes);
    }

    /** True if any registered node has a {@link Line} still {@link Line.State#PENDING}. */
    public boolean hasPendingWork() {
        for (Node node : nodes) {
            if (node.hasPendingLine()) return true;
        }
        return false;
    }

    /**
     * The first pending line found by scanning registered nodes in
     * creation order (Algorithm C1, Fig. 5: "search" the node list for
     * an exit), or {@code null} if none remain.
     */
    public Line nextPendingLine() {
        for (Node node : nodes) {
            Line line = node.nextPendingLine();
            if (line != null) return line;
        }
        return null;
    }

    public int size() {
        return nodes.size();
    }
}
