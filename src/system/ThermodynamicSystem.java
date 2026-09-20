package system;

import system.model.GibbsEnergyModel;
import system.model.PhaseModelKind;
import system.ports.DatabasePort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Thermodynamic System Layer (see README "Structure"): parses a TDB
 * database and constructs the {@link GibbsEnergyModel} for each requested
 * phase, once. The result is immutable and is meant to be built once per
 * calculation and passed by reference to the Calculation Layer.
 *
 * <p>This is the single place that performs
 * {@code TdbParser.load() -> extractSystem() -> buildPhaseModels()} —
 * previously duplicated inline in both {@code ui.layer.EquilibriumUseCase}
 * and {@code ui.layer.PhaseDiagramUseCase}.
 */
public final class ThermodynamicSystem {

    private final List<String> elements;
    private final List<GibbsEnergyModel> phaseModels;

    private ThermodynamicSystem(List<String> elements, List<GibbsEnergyModel> phaseModels) {
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
        this.phaseModels = Collections.unmodifiableList(new ArrayList<>(phaseModels));
    }

    /**
     * Builds a thermodynamic system by parsing {@code tdbFilePath} and
     * constructing a {@link GibbsEnergyModel} for each of {@code phases}.
     *
     * <p>Equivalent to {@link #build(String, List, List, PhaseModelKind)}
     * with {@link PhaseModelKind#AUTO}.
     */
    public static ThermodynamicSystem build(String tdbFilePath,
                                             List<String> elements,
                                             List<String> phases) throws IOException {
        return build(tdbFilePath, elements, phases, PhaseModelKind.AUTO);
    }

    /**
     * Builds a thermodynamic system by parsing {@code tdbFilePath} and
     * constructing a {@link GibbsEnergyModel} for each of {@code phases}.
     *
     * @param tdbFilePath path to the TDB file
     * @param elements    ordered element symbols (defines component indices)
     * @param phases      phase names to build models for
     * @param kind        which Gibbs-energy model to use per phase; see
     *                    {@link PhaseModelKind}. {@code AUTO}/{@code CEF}
     *                    build {@code CefGibbs} for every phase.
     * @return an immutable, ready-to-use thermodynamic system
     * @throws IOException if the TDB file cannot be loaded
     * @throws IllegalStateException if no phase models could be built
     */
    public static ThermodynamicSystem build(String tdbFilePath,
                                             List<String> elements,
                                             List<String> phases,
                                             PhaseModelKind kind) throws IOException {
        DatabasePort parser = new system.database.TdbParser();
        parser.load(tdbFilePath);

        String[] elemArray = elements.toArray(new String[0]);
        DatabasePort system = parser.extractSystem(elemArray);

        List<GibbsEnergyModel> models = new ArrayList<>(
                system.buildPhaseModels(elements, phases, kind));

        if (models.isEmpty()) {
            throw new IllegalStateException(
                    "No phase models could be built for the requested phases: " + phases);
        }

        return new ThermodynamicSystem(elements, models);
    }

    /**
     * Builds a thermodynamic system from an already-constructed model list
     * (e.g. a mix of TDB-built {@code CefGibbs} and spec-built
     * {@code CvmGibbsModel}), for phases with no single shared database
     * path yet.
     */
    public static ThermodynamicSystem of(List<String> elements, List<GibbsEnergyModel> phaseModels) {
        if (phaseModels.isEmpty()) {
            throw new IllegalStateException("No phase models supplied.");
        }
        return new ThermodynamicSystem(elements, phaseModels);
    }

    /** Ordered element symbols this system was built for. */
    public List<String> elements() {
        return elements;
    }

    /** The phase models for this system, one per requested phase, as an unmodifiable list. */
    public List<GibbsEnergyModel> phaseModels() {
        return phaseModels;
    }
}
