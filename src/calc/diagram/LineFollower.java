package calc.diagram;

import system.model.GibbsEnergyModel;

import java.util.List;

/**
 * Algorithm C1 (Sundman 2021 Calphad 75, Fig. 5): repeatedly searches
 * {@code registry} for a pending {@link Line}, walks it via {@code walker},
 * and repeats until no pending line remains. The per-segment walk and the
 * handling of a resolved crossing (node creation, exit attachment) are
 * supplied by {@code walker} -- STEP and MAP differ enough there (ZPF
 * release, exit count) that this loop stays the only code shared between them.
 */
final class LineFollower {

    private LineFollower() {
    }

    /** One pending {@link Line}'s worth of work: walk it and resolve however it ends. */
    interface SegmentWalker {
        void walkAndResolve(Line line, List<GibbsEnergyModel> candidates);
    }

    /**
     * A registry with its start node and pending exits already attached
     * (Fig. 4's "+node, 2 exits" box, either branch), plus the {@link
     * SegmentWalker} that knows how to walk this diagram's lines -- the
     * handoff point Fig. 4 shows both the STEP and MAP branches merging
     * into C1 from.
     */
    record Setup(NodeRegistry registry, SegmentWalker walker) {
    }

    static void drain(Setup setup, List<GibbsEnergyModel> candidates) {
        NodeRegistry registry = setup.registry();
        SegmentWalker walker = setup.walker();
        while (registry.hasPendingWork()) {
            Line line = registry.nextPendingLine();
            line.startWalking();
            walker.walkAndResolve(line, candidates);
        }
    }
}
