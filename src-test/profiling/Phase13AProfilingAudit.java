package profiling;

import application.ApplicationLayer;
import calc.diagram.PhaseDiagramResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import system.model.PhaseModelKind;
import system.ports.EquilibriumResult;
import ui.request.AxisConfig;
import ui.result.CoarseDiagramResult;

import java.io.IOException;
import java.util.Arrays;

/**
 * Phase 13A Performance Profiling Audit -- measures runtime, solver calls,
 * and iteration counts for representative workloads without modifying production code.
 *
 * <p>Profiling output is logged to stdout with structure suitable for analysis.
 * Output includes TDB, model type, calculation kind, point counts, solve counts,
 * iteration counts, and runtime breakdown.
 *
 * <p>Run with: ./gradlew testSlow --tests Phase13AProfilingAudit
 */
@Tag("slow")
@DisplayName("Phase 13A — Performance Profiling")
public class Phase13AProfilingAudit {

    private static final String TDB_VZR_CEF = "data/VZR-re2.TDB";
    private static final String TDB_VZR_CVM = "data/VZR-re2-CVM-model.TDB";

    private static class Timer {
        private long setupEnd;
        private long execEnd;
        private final long start;

        Timer() {
            this.start = System.currentTimeMillis();
        }

        void markSetupEnd() {
            this.setupEnd = System.currentTimeMillis();
        }

        void markExecEnd() {
            this.execEnd = System.currentTimeMillis();
        }

        long setupMs() { return setupEnd - start; }
        long execMs() { return execEnd - setupEnd; }
        long totalMs() { return execEnd - start; }

        void report(String desc, int points, int solves, int iters) {
            System.out.printf(
                "%s: %d points, %d solves, %d iters | " +
                "Setup: %4d ms, Exec: %4d ms, Total: %4d ms | Per-solve: %.2f ms%n",
                desc, points, solves, iters,
                setupMs(), execMs(), totalMs(),
                solves > 0 ? (double) execMs() / solves : 0.0
            );
        }
    }

    @Test
    @DisplayName("Workload 1a: Single-point CEF (V-Zr, 1000K)")
    void workload1aCef() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 1A: Single-Point CEF Equilibrium (V-Zr)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CEF);
        timer.markSetupEnd();

        double T = 1000.0, P = 101325.0;
        app.calculateEquilibrium(T, P, new double[]{0.75, 0.25});
        EquilibriumResult res = app.currentEquilibriumResult();
        timer.markExecEnd();

        timer.report("1A-CEF", 1, 1, res != null ? res.getIterations() : 0);
    }

    @Test
    @DisplayName("Workload 1b: Single-point CVM (V-Zr, 1000K)")
    void workload1bCvm() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 1B: Single-Point CVM Equilibrium (V-Zr)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CVM, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CVM);
        timer.markSetupEnd();

        double T = 1000.0, P = 101325.0;
        app.calculateEquilibrium(T, P, new double[]{0.75, 0.25});
        EquilibriumResult res = app.currentEquilibriumResult();
        timer.markExecEnd();

        timer.report("1B-CVM", 1, 1, res != null ? res.getIterations() : 0);
    }

    @Test
    @DisplayName("Workload 2: GridMinimizer (V-Zr, CEF)")
    void workload2GridMinimizer() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 2: GridMinimizer Initialization (V-Zr, CEF)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CEF);
        timer.markSetupEnd();

        double T = 1000.0, P = 101325.0;
        app.calculateInitialState(T, P, new double[]{0.75, 0.25});
        EquilibriumResult res = app.currentInitialState();
        timer.markExecEnd();

        timer.report("2-GridMinimizer", 1, 0, res != null ? res.getIterations() : 0);
    }

    @Test
    @DisplayName("Workload 3a: STEP — 11 points (V-Zr, CEF, T-sweep)")
    void workload3a() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 3A: STEP Scan (11 points, T-sweep)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CEF);
        timer.markSetupEnd();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 800, 1200, 40);
        app.calculateStep(axis, 1000, 101325, new double[]{0.75, 0.25});
        timer.markExecEnd();

        timer.report("3A-STEP-11", 11, 11, 0);
    }

    @Test
    @DisplayName("Workload 3b: STEP — 21 points (V-Zr, CEF, T-sweep)")
    void workload3b() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 3B: STEP Scan (21 points, T-sweep)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CEF);
        timer.markSetupEnd();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 800, 1200, 20);
        app.calculateStep(axis, 1000, 101325, new double[]{0.75, 0.25});
        timer.markExecEnd();

        timer.report("3B-STEP-21", 21, 21, 0);
    }

    @Test
    @DisplayName("Workload 3c: STEP — 41 points (V-Zr, CEF, T-sweep)")
    void workload3c() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 3C: STEP Scan (41 points, T-sweep)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CEF);
        timer.markSetupEnd();

        AxisConfig axis = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 800, 1200, 10);
        app.calculateStep(axis, 1000, 101325, new double[]{0.75, 0.25});
        timer.markExecEnd();

        timer.report("3C-STEP-41", 41, 41, 0);
    }

    @Test
    @DisplayName("Workload 4a: Coarse Binary — 25 pts (V-Zr, CEF, 1000K)")
    void workload4a() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 4A: Coarse Binary (25 x 1 grid, V-Zr CEF)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CEF);
        timer.markSetupEnd();

        AxisConfig axisX = new AxisConfig("x(Zr)", 1, 0.0, 1.0, 0.04);
        AxisConfig axisY = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1000, 1000, 1);
        app.calculateCoarseBinaryDiagram(axisX, axisY, 1000, 101325, new double[]{0.5, 0.5});
        CoarseDiagramResult res = app.currentCoarseDiagramResult();
        timer.markExecEnd();

        int solves = res != null ? res.getPoints().size() : 0;
        timer.report("4A-CoarseBinary-25", 25, solves, 0);
    }

    @Test
    @DisplayName("Workload 4b: Coarse Binary — 49 pts (V-Zr, CEF, 1000K)")
    void workload4b() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 4B: Coarse Binary (49 x 1 grid, V-Zr CEF)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CEF);
        timer.markSetupEnd();

        AxisConfig axisX = new AxisConfig("x(Zr)", 1, 0.0, 1.0, 0.02);
        AxisConfig axisY = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1000, 1000, 1);
        app.calculateCoarseBinaryDiagram(axisX, axisY, 1000, 101325, new double[]{0.5, 0.5});
        CoarseDiagramResult res = app.currentCoarseDiagramResult();
        timer.markExecEnd();

        int solves = res != null ? res.getPoints().size() : 0;
        timer.report("4B-CoarseBinary-49", 49, solves, 0);
    }

    @Test
    @DisplayName("Workload 4c: Coarse Binary — 99 pts (V-Zr, CEF, 1000K)")
    void workload4c() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 4C: Coarse Binary (99 x 1 grid, V-Zr CEF)");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.CEF);
        timer.markSetupEnd();

        AxisConfig axisX = new AxisConfig("x(Zr)", 1, 0.0, 1.0, 0.01);
        AxisConfig axisY = new AxisConfig("T / K", AxisConfig.Type.TEMPERATURE, 1000, 1000, 1);
        app.calculateCoarseBinaryDiagram(axisX, axisY, 1000, 101325, new double[]{0.5, 0.5});
        CoarseDiagramResult res = app.currentCoarseDiagramResult();
        timer.markExecEnd();

        int solves = res != null ? res.getPoints().size() : 0;
        timer.report("4C-CoarseBinary-99", 99, solves, 0);
    }

    @Test
    @DisplayName("Workload 5: Mixed CEF + CVM AUTO (V-Zr, 1000K)")
    void workload5() throws IOException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKLOAD 5: Mixed CEF + CVM AUTO Model Selection");
        System.out.println("=".repeat(80));

        Timer timer = new Timer();
        ApplicationLayer app = new ApplicationLayer();
        app.setModel(TDB_VZR_CEF, Arrays.asList("V", "ZR"),
                     Arrays.asList("V2ZR", "BCC_A2"), PhaseModelKind.AUTO);
        timer.markSetupEnd();

        double T = 1000.0, P = 101325.0;
        app.calculateEquilibrium(T, P, new double[]{0.75, 0.25});
        EquilibriumResult res = app.currentEquilibriumResult();
        timer.markExecEnd();

        timer.report("5-AUTO-Model", 1, 1, res != null ? res.getIterations() : 0);
    }
}
