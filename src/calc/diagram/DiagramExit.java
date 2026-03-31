package calc.diagram;

/**
 * A directed exit from a {@link DiagramNode} representing the start of a
 * ZPF (Zero Phase Fraction) line to be followed.
 *
 * <p>Algorithm B maintains a queue of unvisited exits.  For each exit,
 * {@link LineStepper} follows the corresponding ZPF line until it either
 * reaches an axis limit or encounters another phase change, creating a new
 * node.
 *
 * <h2>ZPF semantics</h2>
 * Along the ZPF line leaving this exit:
 * <ul>
 *   <li>{@link #fixedPhase} is held at zero amount (it is the ZPF phase).</li>
 *   <li>{@link #forbiddenPhase} is not allowed to appear (it was just removed
 *       at the parent node, so we must not immediately re-add it).</li>
 * </ul>
 */
public class DiagramExit {

    /** The node this exit leaves from. */
    public final DiagramNode parentNode;

    /**
     * Name of the phase held at zero amount (the ZPF constraint).
     * {@code null} for the very first exit of a diagram before any ZPF
     * constraint has been set.
     */
    public final String fixedPhase;

    /**
     * Name of a phase not allowed to re-enter the stable set immediately.
     * Prevents the solver from cycling back to the same phase set that was
     * just left.  {@code null} if no exclusion is needed.
     */
    public final String forbiddenPhase;

    /**
     * Index of the axis to vary first when stepping along this line.
     * 0 = first axis (e.g. T), 1 = second axis (e.g. x_B).
     */
    public final int initialAxisIndex;

    /**
     * Direction to step along {@link #initialAxisIndex}: +1 or -1.
     */
    public final int direction;

    /**
     * Whether this exit has already been followed by {@link LineStepper}.
     * Set to {@code true} by Algorithm B before calling {@code LineStepper}.
     */
    public boolean visited;

    public DiagramExit(DiagramNode parentNode,
                       String fixedPhase,
                       String forbiddenPhase,
                       int initialAxisIndex,
                       int direction) {
        this.parentNode       = parentNode;
        this.fixedPhase       = fixedPhase;
        this.forbiddenPhase   = forbiddenPhase;
        this.initialAxisIndex = initialAxisIndex;
        this.direction        = direction;
        this.visited          = false;
    }

    @Override
    public String toString() {
        return "Exit[from=" + parentNode.id
                + " fixed=" + fixedPhase
                + " forbidden=" + forbiddenPhase
                + " axis=" + initialAxisIndex
                + " dir=" + (direction > 0 ? "+" : "-")
                + (visited ? " VISITED" : "") + "]";
    }
}
