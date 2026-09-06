package ui.layer;

import system.ports.DatabasePort;
import system.ports.EquilibriumResult;
import system.model.GibbsEnergyModel;
import system.model.PhaseModelFactory;
import calc.equil.EquilibriumSolver;
import ui.request.CalculationRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Use case: multi-phase equilibrium calculation using Algorithm A
 * (Sundman et al. 2021).
 *
 * <p>Wires together:
 * <ol>
 *   <li>TDB loading via {@link DatabasePort}</li>
 *   <li>Phase model construction via {@link DatabasePort#buildPhaseModels}</li>
 *   <li>Equilibrium solving via {@link EquilibriumSolver}</li>
 * </ol>
 *
 * <p>Currently supports RK phases only; CEF/CVM adapters will be added
 * in a later phase.
 */
public class EquilibriumUseCase {

    private static final Logger LOG = Logger.getLogger(EquilibriumUseCase.class.getName());

    private final EquilibriumSolver solver;

    public EquilibriumUseCase() {
        this.solver = new EquilibriumSolver();
    }

    /**
     * Execute a multi-phase equilibrium calculation.
     *
     * <p>The request must supply:
     * <ul>
     *   <li>{@code tdbFilePath} — path to the TDB file</li>
     *   <li>{@code elements} — ordered list of element symbols</li>
     *   <li>{@code phases} — list of phase names to include as candidates</li>
     *   <li>{@code T}, {@code P} — temperature (K) and pressure (Pa)</li>
     *   <li>{@code compositions} — overall mole fractions as a single inner list,
     *       length = number of elements</li>
     * </ul>
     *
     * @param request  calculation input
     * @return multi-phase equilibrium result
     * @throws IOException if the TDB file cannot be loaded
     */
    public EquilibriumResult execute(CalculationRequest request) throws IOException {
        // ── 1. Load and extract TDB system via DatabasePort ────────────────
        DatabasePort parser = new system.database.TdbParser();
        parser.load(request.getTdbFilePath());

        String[] elemArray = request.getElements().toArray(new String[0]);
        DatabasePort system = parser.extractSystem(elemArray);

        // ── 2. Build GibbsEnergyModel for each requested phase ────────────────
        List<?> modelList = system.buildPhaseModels(
                request.getElements(), request.getPhases());

        @SuppressWarnings("unchecked")
        List<PhaseModelFactory.PhaseModel> phaseModels =
            (List<PhaseModelFactory.PhaseModel>) (List<?>) modelList;

        List<GibbsEnergyModel> candidates = new ArrayList<>();
        for (PhaseModelFactory.PhaseModel pm : phaseModels) {
            candidates.add(pm.toGibbsModel(request.getElements()));
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No phase models could be built for the requested phases: "
                    + request.getPhases());
        }

        // ── 3. Overall composition from request ───────────────────────────
        double[] compOverAll = extractComposition(request);
        LOG.info("EquilibriumUseCase: T=" + request.getT()
                + " P=" + request.getP()
                + " phases=" + request.getPhases()
                + " comp=" + Arrays.toString(compOverAll));

        // ── 4. Solve ──────────────────────────────────────────────────────
        return solver.solve(request.getT(), request.getP(),
                            compOverAll, candidates);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Extract the overall composition array from the request.
     * Uses the first composition vector; falls back to uniform fractions.
     */
    private double[] extractComposition(CalculationRequest request) {
        int nc = request.getElements().size();
        double[] comp = new double[nc];

        var comps = request.getCompositions();
        if (comps != null && !comps.isEmpty()) {
            var first = comps.get(0);
            for (int i = 0; i < Math.min(nc, first.size()); i++) {
                comp[i] = first.get(i);
            }
        } else {
            // Uniform composition fallback
            Arrays.fill(comp, 1.0 / nc);
        }
        return comp;
    }
}
