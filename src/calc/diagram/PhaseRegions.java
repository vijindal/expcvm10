package calc.diagram;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Output of {@link PhaseDiagramEngine#identifyPhaseRegions}: the labeled
 * network Sundman 2021 Calphad 75 Section 2.4 describes ("ZPF lines
 * separate regions in a phase diagram where a phase is present from
 * regions where it is not present"), plus tie-triangles (Section 2.4/4.2,
 * Figs. 3(a)/11) -- the one region SHAPE the paper actually specifies.
 *
 * <p><b>Two distinct concepts, per the paper's own Sections 2.4 and 4.2
 * (confirmed against OC's actual runtime output, not just source
 * comments):</b>
 * <ul>
 *   <li><b>Line/node labeling</b> -- every {@link Line}/{@link Node} tagged
 *       with its bounding stable-phase set. This is ALL either OC or the
 *       paper's own plotted diagrams ever show for single-/two-phase
 *       areas (OC's {@code ocplot2}/{@code ocplot3}, {@code smp2B.F90},
 *       color/label LINES by stable-phase set -- confirmed no polygon or
 *       filled-area object exists in OC's plot routines for these).</li>
 *   <li><b>Tie-triangles</b> -- an explicit filled 2D region the paper DOES
 *       describe (&sect;2.4: "The green triangles define 3-phase regions;
 *       their corners indicate the compositions of the three phases in
 *       equilibrium," Fig. 3(a); &sect;4.2/Fig. 11's "large green areas
 *       correspond to tie-triangles"). Its geometry is trivial once a
 *       node's equilibrium is known: the 3 vertices ARE the 3 stable
 *       phases' own compositions ({@link
 *       system.ports.EquilibriumResult.PhaseResult#x}) -- no line-graph
 *       polygon-extraction algorithm is needed or exists for this, only
 *       for the (out of scope) single-/two-phase area fills.</li>
 * </ul>
 */
public final class PhaseRegions {

    private final Map<Line, Set<String>> lineLabels;
    private final Map<Node, Set<String>> nodeLabels;
    private final List<TieTriangle> tieTriangles;

    PhaseRegions(Map<Line, Set<String>> lineLabels,
                 Map<Node, Set<String>> nodeLabels,
                 List<TieTriangle> tieTriangles) {
        this.lineLabels = Collections.unmodifiableMap(new LinkedHashMap<>(lineLabels));
        this.nodeLabels = Collections.unmodifiableMap(new LinkedHashMap<>(nodeLabels));
        this.tieTriangles = List.copyOf(tieTriangles);
    }

    /** Every {@link Line}'s bounding stable-phase set (the phases stable along it). */
    public Map<Line, Set<String>> lineLabels() {
        return lineLabels;
    }

    /** Every {@link Node}'s stable-phase set -- same as {@link Node#stablePhaseNames}, exposed per-node here for symmetry with {@link #lineLabels()}. */
    public Map<Node, Set<String>> nodeLabels() {
        return nodeLabels;
    }

    /** One {@link TieTriangle} per node with exactly 3 stable phases (Section 2.4's 3-phase region). */
    public List<TieTriangle> tieTriangles() {
        return tieTriangles;
    }

    /**
     * A 3-phase region (Sundman 2021 &sect;2.4's "green triangle"): the
     * node it was found at, the 3 stable phase names, and each phase's
     * own composition (the triangle's vertices, one per phase, same
     * component order as the node's equilibrium).
     */
    public static final class TieTriangle {

        public final Node node;
        public final List<String> phaseNames;
        public final List<double[]> vertices;

        TieTriangle(Node node, List<String> phaseNames, List<double[]> vertices) {
            this.node = node;
            this.phaseNames = List.copyOf(phaseNames);
            this.vertices = List.copyOf(vertices);
        }

        @Override
        public String toString() {
            return "TieTriangle" + phaseNames + "@Node[" + node.id + "]";
        }
    }
}
