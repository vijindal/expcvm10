# expCVM10 — Thermodynamic Phase-Equilibrium Software

## Project scope

This is an **independent** thermodynamic software project. It is not derived
from, and must not be referenced against or mixed with, CEworkbench or any
other external codebase.

## Long-term vision

A production-grade CALPHAD-style engine, broadly comparable in capability
and philosophy to **pycalphad** / **OpenCalphad**: reads TDB databases,
evaluates Gibbs-energy models for arbitrary phases, computes multicomponent
multiphase equilibria, traces phase diagrams, and supports parameter
assessment against experimental data.

Phase-diagram tracing is the current top priority — see
[docs/roadmap_phase_diagrams.md](docs/roadmap_phase_diagrams.md) and
[docs/phase_diagram_engine_flowchart.md](docs/phase_diagram_engine_flowchart.md)
for the target diagram types, the unified engine design, and current
progress.

### Design principle: the equilibrium kernel is central

The equilibrium engine (`calc/equil/`) depends only on the model-agnostic
`GibbsEnergyModel` contract and never contains model-specific logic (CEF
sublattices, RK polynomial order, CVM cluster variables). This is what lets
new models, and new equilibrium algorithms, be added without rewriting the
rest of the system.

## Structure

Three core layers — UI, Thermodynamic System, Calculation — following the
PANDAT/Sundman separation of a **static** system from a **dynamic**
calculation. A fourth package, `session/`, holds a UI-agnostic coordinator
(`CalculationSession`) every UI goes through in both directions.

| Package     | Layer                    | Responsibility |
|-------------|--------------------------|----------------|
| `ui/`       | UI                       | Entry points (`ui/gui`, `ui/cli`, `ui/api`), request/result DTOs. Never touches solvers or models directly. |
| `system/`   | Thermodynamic System     | `system/database` (TDB parsing), `system/model` (RK / CEF / CVM / unary Gibbs models), `ThermodynamicSystem` (built once, immutable). Static. |
| `calc/`     | Calculation              | `calc/equil` (single-point equilibrium, Sundman Algorithm A), `calc/diagram` (phase-diagram tracing, Algorithms B/C1/C2/D). Dynamic. |
| `session/`  | Coordinator (UI-agnostic) | `CalculationSession` — single point of contact between any UI and the System + Calculation layers. |
| `system/ports/` | Shared contracts     | `DatabasePort`, `LoggingPort`, etc. |
| `util/`     | Shared utilities         | Matrix/JAMA math, formatting, IO. |
| `legacy/`   | Quarantined              | Old Levenberg-Marquardt assessment engine, reached only via `LegacyFitPort`. |

### Boundary rules

- `ui/` may import `system/ports`, `util`, `session`, `calc/diagram` DTOs — **not** solvers or models directly.
- `system/` may import `system/ports`, `util` — **not** `calc/` or `ui/`.
- `calc/` may import `system/model` (via `GibbsEnergyModel`), `system/ports`, `util` — **not** `system/database` or `ui/`.
- `session/` sits above `system/` and `calc/`, below `ui/`.
- `util/` and `system/ports/` import only the JDK.
- `legacy/` is reachable only through a port in `system/ports/`.

### Static vs. dynamic

The System Layer (phase models, parameters, database-derived structure) is
built once and unchanged during a calculation; the Calculation Layer (phase
amounts, compositions, chemical potentials, solver iterations) changes every
iteration.

### Core data flow

UI → problem object → System Layer builds `GibbsEnergyModel`s once →
Calculation Layer's solver iterates, querying the System Layer for
`G, ∂G/∂y, ∂²G/∂y²` each step → converged result flows back through
`CalculationSession` to the UI. See
[docs/dataflow_target.png](docs/dataflow_target.png) for the diagram.

## Entry points

| Command | Launches |
|---------|----------|
| `java ui.Main` / `java ui.Main --cli` | CLI (`ui.cli.CliApp`) |
| `java ui.Main --gui` | Swing GUI (`ui.gui.GuiApp`) |
| `java ui.api.ApiMain [port]` | REST/JSON API on `com.sun.net.httpserver` (default 8080) |

All three route calculations through `session.CalculationSession`.

## Build

Gradle (`build.gradle`), Java 17 target, one runtime dependency (Gson).
`lib/` JARs are gitignored — re-fetch Gson and the JUnit jars on a fresh
clone.

```
./gradlew build          # compile + fat jar (dist/expcvm10.jar)
./gradlew run --args="--gui"
./gradlew test           # JUnit tests in src-test/
```

Most validation is still standalone diagnostic `main()` programs under
`src/diagnostics/`, run directly rather than through the Gradle test task.

## Running the CLI

Every calculation/browse command is a thin wrapper around one
`CalculationSession` method — the same session the GUI and REST API use.

**For interactive use (no-args menu, or a command with `-i` / no flags),
run the built jar directly, not `./gradlew run`** — Gradle's own progress
UI redraws over the child process's stdout while it's blocked on a prompt;
the jar has no such layer in the way:

```
./gradlew build          # once, or after code changes
java -jar dist/expcvm10.jar
java -jar dist/expcvm10.jar equilibrium -i
```

**For quick one-off commands with all flags supplied**, `./gradlew run` is
fine and doesn't require a rebuild after each source change:

```
./gradlew run --args="<command> [options]"
```

PowerShell: use `.\gradlew.bat` instead of `./gradlew`, and don't redirect
stderr with `2>&1` (PS 5.1 misreports exit status on Gradle's own log
output) — check `$LASTEXITCODE` instead.

`-h` / `--help` (bare, or after any command) prints usage and exits 0.
Unknown commands exit 2.

| Command | CalculationSession method | Example |
|---|---|---|
| `equilibrium` | `calculateEquilibrium` | `equilibrium --tdb data/agcu.TDB --elements AG,CU --phases LIQUID,FCC_A1 --T 1000 --P 1e5 --composition 0.8,0.2` |
| `initial-state` | `calculateInitialState` (grid-minimizer only, no Newton step) | `initial-state --tdb data/VZR-re2.TDB --elements V,ZR --phases V2ZR --T 1000 --P 10000 --composition 0.6667,0.3333` |
| `step` | `calculateStep` (single-axis scan) | `step --tdb data/agcu.TDB --elements AG,CU --phases LIQUID,FCC_A1 --axis TEMPERATURE,1000,1200,5 --P 101325 --composition 0.5,0.5` |
| `coarse-binary` | `calculateCoarseBinaryDiagram` | `coarse-binary --tdb data/VZR-re2.TDB --elements V,ZR --phases V2ZR,BCC_A2 --axisX COMPOSITION:1,0.02,0.20,0.01 --axisY TEMPERATURE,1200,1600,50 --composition 1.0,0.0` |
| `coarse-ternary` | `calculateCoarseTernaryDiagram` | `coarse-ternary --tdb data/Cr-Fe-Mo.TDB --elements CR,FE,MO --phases LIQUID,A2 --axisI COMPOSITION:1,0.0,0.6,0.1 --axisJ COMPOSITION:2,0.0,0.6,0.1 --T 1800 --composition 1.0,0.0,0.0` |
| `map` | `calculateMap` (two-axis ZPF map; release axis must be COMPOSITION) | `map --tdb data/agcu.TDB --elements AG,CU --phases LIQUID,FCC_A1 --axis0 TEMPERATURE,1000,1200,5 --axis1 COMPOSITION:1,0.0,1.0,0.01 --composition 0.5,0.5` |
| `diagram` | `calculatePhaseDiagram` — **not yet implemented upstream**, always errors | — |
| `inspect` | `availableElements` / `availablePhasesFor` | `inspect --tdb data/agcu.TDB --elements AG,CU` |

Axis spec format: `TYPE,min,max,step` for `TEMPERATURE`/`PRESSURE`, or
`COMPOSITION:i,min,max,step` where `i` is the component index (0-based,
matching `--elements` order).

Every calculation command and `inspect` also accept `-i` / `--interactive`
(or no flags at all), prompting for each value on stdin — TDB, elements,
and phases are guided from the chosen database's live contents.

```
java -jar dist/expcvm10.jar equilibrium -i
```

Run `<command> --help` for that command's full flag reference.

## Current state

- **TDB parsing, Gibbs-energy models (`CefGibbs`), the multiphase
  equilibrium solver (`EquilibriumSolverV2`)**, and **phase-diagram
  tracing (`calc/diagram`)** are implemented and tested — see
  [docs/roadmap_phase_diagrams.md](docs/roadmap_phase_diagrams.md) for
  what's built, validated (against real OpenCalphad output), and still
  open in the phase-diagram engine specifically.
- RK and CVM models exist but are not yet connected to the TDB →
  equilibrium pipeline; only CEF phases build and solve end-to-end.
- The legacy Levenberg-Marquardt assessment pathway (`legacy/calbince`)
  is quarantined and reachable only via `LegacyFitPort`.
- The REST API's `step`/`map` endpoints return 501 (not yet updated for
  the current tracers); no auth/TLS/session expiry.

See [Structure](#structure) above for the architecture and layer
boundaries.
