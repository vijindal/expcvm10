package service;

import database.tdb;
import domain.EquilibriumResult;
import domain.PhaseModelPort;
import infra.RkPhaseModelFactory;
import infra.TdbParser;
import thermocalc.diagram.AxisConfig;
import thermocalc.diagram.DiagramLine;
import thermocalc.diagram.DiagramNode;
import thermocalc.diagram.DiagramTracer;
import thermocalc.diagram.PhaseDiagram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Use case: compute a phase diagram (STEP or MAP) and convert the result
 * to a rendering-ready {@link PhaseDiagramResult}.
 *
 * <p>Wires together:
 * <ol>
 *   <li>TDB loading via {@link TdbParser}</li>
 *   <li>Phase model construction via {@link RkPhaseModelFactory}</li>
 *   <li>Diagram calculation via {@link DiagramTracer} (Algorithms A–D)</li>
 *   <li>Conversion from internal {@link PhaseDiagram} to {@link PhaseDiagramResult} DTO</li>
 * </ol>
 */
public class PhaseDiagramUseCase {

    private static final Logger LOG = Logger.getLogger(PhaseDiagramUseCase.class.getName());

    private final DiagramTracer tracer;

    public PhaseDiagramUseCase() {
        this.tracer = new DiagramTracer();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Execute the phase diagram calculation described by {@code request}.
     *
     * @param request  fully populated diagram request (see {@link PhaseDiagramRequest})
     * @return rendering-ready diagram result
     * @throws IOException if the TDB file cannot be loaded
     * @throws IllegalStateException if no phase models can be built
     */
    public PhaseDiagramResult execute(PhaseDiagramRequest request) throws IOException {

        // ── 1. Load TDB and extract system ───────────────────────────────
        TdbParser parser = new TdbParser();
        parser.load(request.getTdbFilePath());

        String[]  elemArray = request.getElements().toArray(new String[0]);
        TdbParser system    = (TdbParser) parser.extractSystem(elemArray);
        tdb       systdb    = system.getUnderlyingTdb();

        // ── 2. Build phase models ─────────────────────────────────────────
        List<PhaseModelPort> candidates = new ArrayList<>();
        for (String phaseName : request.getPhases()) {
            try {
                candidates.add(RkPhaseModelFactory.build(
                        phaseName, request.getElements(), systdb));
            } catch (Exception ex) {
                LOG.warning("PhaseDiagramUseCase: skipping phase '" + phaseName
                        + "': " + ex.getMessage());
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No phase models built for: " + request.getPhases());
        }

        // ── 3. Resolve start composition ─────────────────────────────────
        int      nc   = request.getElements().size();
        double[] comp = request.getStartComposition();
        if (comp == null) {
            comp = new double[nc];
            for (int i = 0; i < nc; i++) comp[i] = 1.0 / nc;
        }

        // ── 4. Run DiagramTracer ──────────────────────────────────────────
        AxisConfig[] axes        = request.axisArray();
        double[]     startAxes   = request.startAxisValues();

        LOG.info("PhaseDiagramUseCase: " + request.getDiagramType()
                + " diagram, " + candidates.size() + " phases, "
                + axes.length + " axes.");

        PhaseDiagram diagram = tracer.calculate(
                candidates, axes, startAxes,
                request.getFixedT(), request.getFixedP(), comp);

        // ── 5. Convert to result DTO ──────────────────────────────────────
        return convert(diagram, request);
    }

    // ------------------------------------------------------------------
    // Conversion: PhaseDiagram → PhaseDiagramResult
    // ------------------------------------------------------------------

    private PhaseDiagramResult convert(PhaseDiagram diagram,
                                        PhaseDiagramRequest request) {

        AxisConfig[] axes = request.axisArray();
        String[] axisNames = new String[axes.length];
        double[] axisMin   = new double[axes.length];
        double[] axisMax   = new double[axes.length];
        for (int i = 0; i < axes.length; i++) {
            axisNames[i] = axes[i].name;
            axisMin[i]   = axes[i].min;
            axisMax[i]   = axes[i].max;
        }

        PhaseDiagramResult result = new PhaseDiagramResult(axisNames, axisMin, axisMax);

        // ── Convert lines ─────────────────────────────────────────────────
        for (DiagramLine dl : diagram.getLines()) {
            if (dl.size() < 2) continue;

            List<double[]> coords = dl.getAxisCoords();
            List<String>   stable = new ArrayList<>(dl.stablePhaseSet);

            result.addLine(new PhaseDiagramResult.LineSegment(
                    coords, dl.fixedPhase, stable));
        }

        // ── Convert nodes ─────────────────────────────────────────────────
        for (DiagramNode dn : diagram.getNodes()) {
            List<String> stableNames = new ArrayList<>();
            for (EquilibriumResult.PhaseResult pr : dn.equilibrium.getStablePhases()) {
                stableNames.add(pr.phaseName);
            }

            PhaseDiagramResult.NodePoint.Type type = classifyNode(dn, axes.length,
                    dn.equilibrium.getStablePhases().size(),
                    candidates(request));

            result.addNode(new PhaseDiagramResult.NodePoint(
                    dn.axisValues, stableNames, type));
        }

        result.setMessage("Diagram: " + diagram.getNodes().size() + " nodes, "
                + diagram.getLines().size() + " lines.");
        return result;
    }

    // ------------------------------------------------------------------
    // Node classification
    // ------------------------------------------------------------------

    /**
     * Classify a node by the Gibbs phase rule:
     * f = n_axes - p + c
     * <ul>
     *   <li>f &le; 0  → INVARIANT</li>
     *   <li>f &gt; 0  → CROSSING (normal phase boundary)</li>
     *   <li>no exits  → BOUNDARY (axis limit)</li>
     * </ul>
     */
    private PhaseDiagramResult.NodePoint.Type classifyNode(DiagramNode node,
                                                             int nAxes,
                                                             int numStable,
                                                             int nc) {
        if (node.getExits().isEmpty()) return PhaseDiagramResult.NodePoint.Type.BOUNDARY;
        int f = nAxes - numStable + nc;
        return f <= 0
                ? PhaseDiagramResult.NodePoint.Type.INVARIANT
                : PhaseDiagramResult.NodePoint.Type.CROSSING;
    }

    /** Number of components inferred from the request element list. */
    private int candidates(PhaseDiagramRequest request) {
        return request.getElements().size();
    }
}
