package session.calctype;

import system.model.PhaseModelKind;

import java.util.List;

/**
 * The "Gibbs energy model parameters selected" bundle a caller passes to
 * {@link CalculationCatalog#runCalculating} before a {@link CalculationKind}'s
 * own parameters are collected/run -- mirrors {@code CalculationSession
 * .ModelKey}'s fields deliberately, but is a distinct, public-facing type so
 * this package's API surface never leaks {@code CalculationSession}'s
 * private cache-identity record.
 */
public record ModelSelection(String tdbFilePath, List<String> elements, List<String> phases,
                              PhaseModelKind modelKind) {

    public ModelSelection(String tdbFilePath, List<String> elements, List<String> phases) {
        this(tdbFilePath, elements, phases, PhaseModelKind.AUTO);
    }
}
