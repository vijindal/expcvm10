package system.model.cvm;

import system.model.unary.ElementGibbs;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, explicit input bundle for constructing a {@link CvmGibbsModel}:
 * {@link CvmPhaseData} + CEC terms + {@link ElementGibbs}[] + elements, in
 * one immutable value holder -- not a database abstraction or a second TDB
 * grammar. Stands in for the {@code .cedb} input path that doesn't exist
 * yet; only the source of a {@code CvmPhaseSpec} should need to change when
 * it does.
 */
public final class CvmPhaseSpec {

    private final CvmPhaseData data;
    private final List<CecTerm> cecTerms;
    private final ElementGibbs[] ghser;
    private final List<String> elements;

    public CvmPhaseSpec(CvmPhaseData data, List<CecTerm> cecTerms,
                         ElementGibbs[] ghser, List<String> elements) {
        if (data == null) throw new IllegalArgumentException("data must not be null");
        if (cecTerms == null) throw new IllegalArgumentException("cecTerms must not be null");
        if (ghser == null) throw new IllegalArgumentException("ghser must not be null");
        if (elements == null) throw new IllegalArgumentException("elements must not be null");
        this.data = data;
        this.cecTerms = new ArrayList<>(cecTerms);
        this.ghser = ghser.clone();
        this.elements = new ArrayList<>(elements);
    }

    /** Phase name this spec builds a model for, e.g. "BCC_A2". */
    public String phaseName() { return data.phaseName; }

    /** Builds the {@link CvmGibbsModel} described by this spec. */
    public CvmGibbsModel toModel() {
        return new CvmGibbsModel(data, cecTerms, ghser, elements);
    }
}
