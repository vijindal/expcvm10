package calc.equil;

import org.junit.jupiter.api.Test;
import system.ThermodynamicSystem;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * NOT a correctness test -- a data-generation sweep, run on request to
 * check whether {@link EquilibriumSolverV2#solve} itself is reliable
 * (converges, gives a plausible stable-phase set) across a grid of T and
 * x(Cu) for Ag-Cu, independent of any diagram-tracing/boundary-solve
 * logic ({@link EquilibriumSolverV2#solveBoundaryReleasingT}).
 *
 * <p>Originally written to investigate a node-duplication finding (a
 * diagram CLI report of 4 nodes vs. OC's 2 real crossings for Ag-Cu
 * x(Cu)=0.05, T=1150-1230K): root cause traced to
 * solveBoundaryReleasingT failing to converge at every crossing tried.
 * Before chasing that boundary solve specifically, this sweep checks the
 * more basic question: is the plain point-equilibrium solve (no
 * boundary release, just solve()) itself reliable across this system's
 * T-x space, or does convergence trouble show up even here.
 *
 * <p>Writes one CSV row per grid point to
 * {@code build/agcu_solver_sweep.csv}: T, x(Cu), converged, iterations,
 * stable phase names, each stable phase's own x(Cu), plus the accuracy/
 * diagnostic fields {@link ui.result.EquilibriumReport} already surfaces
 * at the UI/CLI level for a single equilibrium ({@code l r}-equivalent
 * report) -- max stable-phase driving force (should be ~0 at a true
 * equilibrium; the natural accuracy proxy since the solver has no
 * separate reported residual/error value), system Gibbs energy per mole
 * of real atoms, and chemical potentials (mu/RT) per component -- so a
 * point that "converged=true" but is only weakly self-consistent is
 * still visible.
 */
public class EquilibriumSolverV2AgCuGridSweepTest {

    @Test
    void sweepAgCuGridAndDumpResults() throws IOException {
        var system = ThermodynamicSystem.build(
                "data/agcu.TDB", List.of("AG", "CU"), List.of("LIQUID", "FCC_A1"));

        Path outDir = Path.of("build");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("agcu_solver_sweep.csv");

        double P = 101325.0;

        int converged = 0;
        int failed = 0;
        int total = 0;

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outFile))) {
            w.println("T_K,x_Cu,converged,iterations,exception,stable_phases,phase_x_Cu_values,"
                    + "max_abs_driving_force,G_sys_per_atom_J_per_mol,mu_Ag,mu_Cu");

            for (double T = 900.0; T <= 1200.0; T += 25.0) {
                for (double xCu = 0.05; xCu < 1.0; xCu += 0.05) {
                    double xAg = 1.0 - xCu;
                    double[] comp = { xAg, xCu };

                    total++;
                    EquilibriumSolverV2 solver = new EquilibriumSolverV2();
                    try {
                        EquilibriumResult result = solver.solve(T, P, comp, system.phaseModels());

                        StringBuilder phases = new StringBuilder();
                        StringBuilder phaseXs = new StringBuilder();
                        double maxAbsDrivingForce = 0.0;
                        for (var ph : result.getStablePhases()) {
                            if (phases.length() > 0) {
                                phases.append('+');
                                phaseXs.append('+');
                            }
                            phases.append(ph.phaseName);
                            phaseXs.append(String.format("%.6f", ph.x[1]));
                            maxAbsDrivingForce = Math.max(maxAbsDrivingForce, Math.abs(ph.drivingForce));
                        }

                        String gSysPerAtom;
                        try {
                            gSysPerAtom = String.format("%.6E", result.totalGPerAtom());
                        } catch (IllegalStateException e) {
                            gSysPerAtom = "n/a";
                        }

                        double[] mu = result.getMu();
                        String muAg = mu.length > 0 ? String.format("%.6E", mu[0]) : "";
                        String muCu = mu.length > 1 ? String.format("%.6E", mu[1]) : "";

                        w.printf("%.2f,%.4f,%b,%d,,%s,%s,%.6E,%s,%s,%s%n",
                                T, xCu, result.isConverged(), result.getIterations(),
                                phases, phaseXs, maxAbsDrivingForce, gSysPerAtom, muAg, muCu);

                        if (result.isConverged()) {
                            converged++;
                        } else {
                            failed++;
                        }
                    } catch (Exception e) {
                        failed++;
                        w.printf("%.2f,%.4f,false,,%s,,,,,,%n",
                                T, xCu, e.getClass().getSimpleName() + ":"
                                        + String.valueOf(e.getMessage()).replace(",", ";").replace("\n", " "));
                    }
                }
            }
        }

        System.out.println("Ag-Cu solver sweep: " + total + " points, "
                + converged + " converged, " + failed + " failed/exception. "
                + "Results: " + outFile.toAbsolutePath());
    }
}
