package ui.api.dto;

import system.ports.EquilibriumResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire-format DTO for {@link EquilibriumResult}. A dedicated DTO is used
 * instead of serializing {@code EquilibriumResult} directly so the wire
 * format is stable and independent of internal field layout.
 */
public final class EquilibriumResponse {

    public final double T;
    public final double P;
    public final double[] mu;
    public final boolean converged;
    public final int iterations;
    public final List<PhaseEntry> stablePhases;
    public final List<PhaseEntry> metastablePhases;

    public EquilibriumResponse(EquilibriumResult result) {
        this.T = result.getT();
        this.P = result.getP();
        this.mu = result.getMu();
        this.converged = result.isConverged();
        this.iterations = result.getIterations();
        this.stablePhases = toEntries(result.getStablePhases());
        this.metastablePhases = toEntries(result.getMetastablePhases());
    }

    private static List<PhaseEntry> toEntries(List<EquilibriumResult.PhaseResult> phases) {
        List<PhaseEntry> entries = new ArrayList<>();
        for (EquilibriumResult.PhaseResult pr : phases) {
            entries.add(new PhaseEntry(pr));
        }
        return entries;
    }

    public static final class PhaseEntry {
        public final String phaseName;
        public final String modelType;
        public final double amount;
        public final double[] x;
        public final double[] y;
        public final double G;
        public final double drivingForce;

        public PhaseEntry(EquilibriumResult.PhaseResult pr) {
            this.phaseName = pr.phaseName;
            this.modelType = pr.modelType;
            this.amount = pr.amount;
            this.x = pr.x;
            this.y = pr.y;
            this.G = pr.G;
            this.drivingForce = pr.drivingForce;
        }
    }
}
