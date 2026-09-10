# expCVM10 — Thermodynamic Phase-Equilibrium Software

## Project scope

This is an **independent** thermodynamic software project. It is not derived
from, and must not be referenced against or mixed with, CEworkbench or any
other external codebase. Development, documentation, and design decisions
here stand on their own.

## Long-term vision

The eventual goal is a production-grade thermodynamic phase-equilibrium and
phase-diagram calculator, broadly comparable in capability and philosophy to
**pycalphad** / **OpenCalphad**: a CALPHAD-style engine that reads standard
thermodynamic databases (TDB), evaluates Gibbs-energy models for arbitrary
phases, computes multicomponent multiphase equilibria, traces phase
diagrams, and supports parameter assessment against experimental data.

### Intended major layers

- **Thermodynamic database / TDB engine** — parse standard TDB syntax
  (elements, functions, phases, sublattices, `G`/`L`/`TC`/`BMAGN`
  parameters, type definitions) into a model-agnostic in-memory
  representation.
- **Thermodynamic model engine** — pluggable Gibbs-energy models sharing one
  contract: unary (pure-element SGTE), Redlich-Kister (RK) substitutional
  solutions, generalized Compound Energy Formalism (CEF) for sublattice
  phases, Cluster Variation Method (CVM) for short-range-order phases, and
  future model types (ionic liquid, two-sublattice liquid, etc.).
- **Equilibrium engine** — the central, model-agnostic kernel that computes
  single- and multiphase equilibria from any conforming Gibbs-energy model:
  chemical-potential equality, mass balance, stability, and phase
  addition/removal.
- **Phase-diagram engine** — binary, ternary, and eventually general
  multicomponent phase-diagram tracing (ZPF line following, invariant
  reactions, isothermal/isopleth sections).
- **Thermodynamic properties** — derived quantities (enthalpy, entropy,
  heat capacity, activities, driving forces) computed from converged
  equilibria.
- **Parameter assessment / optimization** — fitting model parameters against
  experimental data (currently the legacy Levenberg-Marquardt pathway).
- **API / CLI / GUI and production infrastructure** — stable programmatic
  interfaces, scriptable CLI, and a usable GUI, backed by proper packaging,
  testing, and documentation.

### Design principle: the equilibrium kernel is central

The equilibrium engine (`calc/equil/`) is the architectural core of this
project. It must depend only on the model-agnostic `GibbsEnergyModel`
contract (G, gradient, Hessian, T/P derivatives, internal-variable
handling) and must never contain model-specific logic (CEF sublattice
details, RK polynomial order, CVM cluster variables, etc.). Conversely,
each model implementation (RK, CEF, CVM, unary) is responsible for
correctly satisfying that contract and must not require the equilibrium
engine to know its internals. Keeping this separation intact is what
allows new models, and eventually new equilibrium algorithms, to be added
without rewriting the rest of the system.

## Structure

The engine is organized into **three core layers** — UI, Thermodynamic
System, Calculation — following the PANDAT/Sundman separation of a
**static** system from a **dynamic** calculation (see [Static vs.
dynamic](#static-vs-dynamic) below). A fourth package, `session/`, holds a
UI-agnostic **coordinator** (`CalculationSession`) that every UI goes
through in both directions. Plus shared code (`system/ports`, `util`) and
quarantined `legacy/`.

| Package     | Layer                    | Responsibility |
|-------------|--------------------------|----------------|
| `ui/`       | UI                       | Entry points (`ui/gui`, `ui/cli`, `ui/api`), request/result DTOs, use-case glue (`ui/layer`). Never touches solvers or models directly. |
| `system/`   | Thermodynamic System     | `system/database` (TDB parsing), `system/model` (RK / CEF / CVM / unary Gibbs models), `ThermodynamicSystem` (built once, immutable). Static. |
| `calc/`     | Calculation              | `calc/equil` (single-point equilibrium, Sundman Algorithm A), `calc/diagram` (phase-diagram tracing, Algorithms B/C1/C2/D). Dynamic. |
| `session/`  | Coordinator (UI-agnostic) | `CalculationSession` — the single point of contact between any UI and the System + Calculation layers. Builds a system once, reuses it across calculations, holds the latest result, serves database/element/phase browsing. |
| `system/ports/` | Shared contracts     | `DatabasePort`, `LoggingPort`, etc. |
| `util/`     | Shared utilities         | Matrix/JAMA math, formatting, IO. No layer types. |
| `legacy/`   | Quarantined              | Old Levenberg-Marquardt assessment engine (`legacy/calbince`), reached only via `LegacyFitPort`. |

### Data-flow diagram

```
   ┌────────────────────────────────────────────────────────────────┐
   │  UI   (GUI / CLI / API)                                         │
   │  browses databases + sends model / calculation details;        │
   │  reads results and status back                                  │
   └────────────────────────────────────────────────────────────────┘
              │ ▲                                    two-way
   browse ────┘ └──── db/element/phase lists  ·  results + status
   model / calc                                    │ ▲
              │ ▼                                   │ │
   ┌────────────────────────────────────────────────────────────────┐
   │  CalculationSession                            (4th layer)      │
   │  holds the current system AND the latest result;               │
   │  single point of contact for the UI, both ways                 │
   └────────────────────────────────────────────────────────────────┘
        │ ▲                    │  setModel(...)         │  calculate(...)    ▲
   browse│└─ db/element/       │  build GibbsEnergy-    ▼                    │ result
   req.  │   phase lists       │  Model[] once, reuse                       │
        │ ▼   (two-way)        ▼                                            │
   ┌──────────────────────────────────────┐        ┌──────────────────────────────┐
   │  Thermodynamic System Layer          │ (T,y)  │  Calculation Layer           │
   │                                      │ ─────▶ │  runs the solver, querying   │
   │  builds GibbsEnergyModel[]; evaluates │ per    │  the models many times per   │
   │  G, dG/dy, d2G/dy2;                   │ iter   │  solve                       │
   │  browsable database / element /       │ ◀───── │                              │
   │  phase metadata                      │  G,    │                              │
   │                                      │ dG/dy, │                              │
   │                                      │ d2G/dy2│                              │
   └──────────────────────────────────────┘        └──────────────────────────────┘
```

Every UI↔System interaction is two-way and passes through
`CalculationSession`. The **browse** path — list a TDB's databases /
elements / phases — sends a request down and returns the lists back up at
both hops (UI ⇄ session ⇄ System Layer), with no model build.
`setModel(...)` builds the `GibbsEnergyModel[]` once, then reuses it. The
System ↔ Calculation exchange repeats many times per `calculate(...)`
call — the solver needs fresh `G` and derivatives at each trial `(T, y)`.
See [docs/dataflow_target.png](docs/dataflow_target.png) for the rendered
version.

### Boundary rules

- `ui/` may import `system/ports`, `util`, `session`, `calc/diagram` DTOs — **not** solvers or models directly.
- `system/` may import `system/ports`, `util` — **not** `calc/` or `ui/`.
- `calc/` may import `system/model` (via `GibbsEnergyModel`), `system/ports`, `util` — **not** `system/database` or `ui/`.
- `session/` sits above `system/` and `calc/`, below `ui/`.
- `util/` and `system/ports/` import only the JDK.
- `legacy/` is reachable only through a port in `system/ports/`.

### Static vs. dynamic

The split follows the PANDAT/Sundman separation of a **static** part (the
thermodynamic system: phase models, parameters, database-derived
structure — built once, unchanged during a calculation) from a
**dynamic** part (the calculation: phase amounts, compositions, chemical
potentials, solver iterations). The System Layer holds no state between
solver calls; the Calculation Layer holds no thermodynamic knowledge.

### Core data flow (UI → UI)

1. The **UI** turns user input (elements, phases, T, P, composition,
   calculation type) into a problem object. It does no thermodynamics and
   never touches a phase model or solver directly.
2. The **Thermodynamic System Layer** is activated **once**: the database
   is parsed, parameters extracted, and a phase model built for every
   requested phase. This collection — the *thermodynamic system* — stays
   fixed for the rest of the calculation.
3. The problem object plus the thermodynamic system go to the
   **Calculation Layer**, which picks the calculation module (equilibrium,
   phase diagram, …) and starts its numerical solver.
4. **Two-way loop (the critical concept).** On every iteration the solver
   sends the current `(T, y)` to the System Layer and gets back
   `G, ∂G/∂y, ∂²G/∂y²`; it assembles the governing equations, solves for
   variable updates, and iterates until convergence.
5. The converged **result object** (phase amounts, compositions, chemical
   potentials, derived properties) flows back through
   `CalculationSession` to the UI, which renders it as tables, graphs, or
   logs.

See [docs/plan-3layer-core-dataflow.md](docs/plan-3layer-core-dataflow.md)
and [docs/dataflow_target.png](docs/dataflow_target.png) for the full
data-flow figure and the implementation record.

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
./gradlew test           # JUnit (currently one test in src-test/)
```

Most validation is still standalone diagnostic `main()` programs under
`src/test/` run directly, not through the Gradle test task.

## Current state (as of the 3-layer refactor and `CalculationSession` wiring)

The codebase is organized into the layers described above. The legacy
assessment code is quarantined under `legacy/`.

### Current capabilities

- TDB parsing for standard SGTE-style syntax: elements, `FUNCTION`
  substitution, multi-sublattice `PHASE`/`CONSTITUENT` records, `G`/`L`
  (Redlich-Kister interaction) and `TC`/`BMAGN` (magnetic) parameters,
  `TYPE_DEFINITION` magnetic declarations. `TdbParser` caches by file
  path, so repeated loads of the same database are no-ops.
- A general n-sublattice CEF Gibbs-energy evaluator (`system/model/cef`)
  with analytical G, gradient, and Hessian, wired end-to-end into the
  equilibrium solver via `CefPhaseModelAdapter`. The
  site-fraction-to-mole-fraction response projection (`eMatNC`) is a real
  analytical computation, verified against central differences by
  `src/test/EMatNCTest.java`.
- A Sundman-style (CALPHAD 75, 2021) single-phase equilibrium path in
  `calc/equil/EquilibriumSolver`: given a fixed T, P, and overall
  composition, it builds the correct site-fraction state, converges the
  chemical potentials via the tangent-plane/Euler relation, and reports a
  self-consistent Gibbs energy, mole fractions, and zero driving force.
  This has been validated against the V–Zr TDB (`data/VZR-re2.TDB`) for
  both an ordinary substitutional-like sublattice phase (BCC_A2, with a
  vacancy sublattice) and a stoichiometric two-sublattice ordered phase
  (V2ZR), including composition round-tripping and Hessian evaluation.
- `ThermodynamicSystem` and `CalculationSession`: a single build-once/
  reuse coordinator that parses a TDB and builds phase models one time,
  then serves multiple calculations (and database/element/phase browsing)
  against the same system without re-parsing. All three UIs go through it:
  the GUI single-point path (`MainController.runSinglePoint`), the CLI
  `equilibrium` command, and the REST API — all three independently
  verified to produce bit-identical results for the same V–Zr scenario.
- A REST/JSON API (`src/ui/api`, `com.sun.net.httpserver` + Gson, no
  server framework) exposing `CalculationSession` over HTTP: explicit
  session lifecycle, per-session locking, `equilibrium` and
  `phase-diagram` endpoints (`step`/`map` return 501), structured error
  bodies. Launch via `ui.api.ApiMain [port]`.
- A standalone RK (Redlich-Kister) Gibbs-energy model (binary/ternary/
  quaternary interaction terms, analytical derivatives) and a standalone
  CVM Gibbs-energy evaluator (binary systems, parsed from Mathematica
  `.nb` output), both implemented but not yet wired into the production
  TDB → equilibrium pipeline.
- A binary phase-diagram tracer (`calc/diagram`) implementing ZPF line
  following, phase-boundary bisection, and invariant-reaction handling,
  wired to the CLI/use-case layer.
- A legacy Levenberg-Marquardt parameter-fitting/assessment pathway
  (`legacy/calbince`), used by the `opt`/`cal` CLI commands, kept separate
  from the new model/equilibrium code.

### Current limitations

- **Multiphase equilibrium is not yet validated.** The Newton iteration in
  `EquilibriumSolver` for 2+ stable phases does not yet converge to
  physically correct results; the multi-phase JUnit case
  (`src-test/calc/equil/EquilibriumSolverV2TwoPhaseEndToEndTest.java`)
  deliberately asserts only that the iteration runs, not its output.
- **Single-phase solver misses the ordered minimum at stoichiometric
  points.** For V2ZR at its ideal stoichiometry, `EquilibriumSolver` /
  `GridMinimizer` converge to a *disordered* constitution ~13 kJ/mol
  above the true ordered end-member minimum (see
  `src/test/CalculationSessionCalGTest.java`). The grid minimizer's
  coarse scan does not land near the narrow, deep ordered minimum, and a
  single Newton step from a nearby disordered guess does not reach it.
  Flagged for a dedicated `EquilibriumSolver`/`GridMinimizer` pass.
- `calculateStep`/`calculateMap` on `CalculationSession` are explicit
  unimplemented stubs — there is no plain property-sampling engine (as
  opposed to full phase-boundary tracing) in the codebase yet.
- CEF interaction parameters currently support only 2-sublattice pair×
  single-sublattice interactions with a single T-linear term; there is no
  higher-order Redlich-Kister expansion within CEF interactions.
- The magnetic contribution (Inden-Hillert model) is implemented but its
  composition-dependent Curie temperature/Bohr-magneton-number
  calculation is not yet wired in (`computeTc`/`computeBeta` return 0).
- RK and CVM models are not connected to the TDB → equilibrium production
  path; only CEF phases can currently be built and solved end-to-end from
  a TDB file.
- The test suite is mostly standalone diagnostic `main()` programs under
  `src/test/` rather than the Gradle/JUnit setup `build.gradle` declares;
  only one true JUnit test exists so far (`src-test/`). There is no
  single command that runs everything as a pass/fail regression gate.
- Phase-diagram tracing has only been exercised for binary systems; the
  underlying grid-minimizer's convex-hull step is binary-only (ternary+
  falls back to a non-hull heuristic).
- API/GUI hardening is out of scope so far: no authentication, TLS, or
  session expiry on the REST API; the GUI has no phase-diagram or
  property-scan wiring through `CalculationSession` yet.

### Long-term intended capabilities

- Robust single-phase through general multicomponent, multiphase
  equilibrium for arbitrary combinations of unary, RK, CEF, and CVM
  phases.
- Binary, ternary, and general multicomponent phase-diagram calculation.
- A stable, documented API/CLI usable for scripting and integration, and a
  GUI sufficient for interactive exploration.
- Parameter assessment against experimental data integrated with the new
  model/equilibrium architecture (not only the legacy pathway).

See [Structure](#structure) above for the architecture and layer
boundaries, and [docs/plan-3layer-core-dataflow.md](docs/plan-3layer-core-dataflow.md)
for the implementation record.
