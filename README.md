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
./gradlew test           # JUnit tests in src-test/
```

Most validation is still standalone diagnostic `main()` programs under
`src/test/` run directly, not through the Gradle test task.

## Current state (as of the multiphase equilibrium solver pass)

The codebase is organized into the layers described above. The legacy
assessment code is quarantined under `legacy/`.

### Current capabilities

- TDB parsing for standard SGTE-style syntax: elements, `FUNCTION`
  substitution, multi-sublattice `PHASE`/`CONSTITUENT` records, `G`/`L`
  (Redlich-Kister interaction, arbitrary order) and `TC`/`BMAGN`
  (magnetic) parameters, `V0`/`VA` (molar volume) parameters, both
  literal-phase-name and `%`-flag-resolved `TYPE_DEFINITION` (magnetic/
  disordered-part) declarations. `TdbParser` caches by file path, so
  repeated loads of the same database are no-ops. 516/545 phase ×
  database combinations across the 11 bundled TDBs build and evaluate
  cleanly; the rest are genuine missing-parameter rejections, not parser
  bugs. Known, precisely-characterized gap: end-member `G()` expressions
  with pressure dependence expressed through nested transcendental
  `FUNCTION` chains (an older, pre-`V0`/`VA` mechanism) are silently
  dropped rather than evaluated — see `CefContractTest`'s
  `knownPResidual` cases for the two instances found.
- `GibbsEnergyModel` is a minimal, model-agnostic abstract contract
  (`system/model`): every method on it is either required by Sundman's
  phase-matrix/equilibrium-matrix construction (2015 Eq. 40/58) or is a
  genuine external entry point, verified by an explicit audit this
  session (no dead accessors, no CEF-specific assumptions baked in
  beyond the sublattice-block accessors, deliberately deferred pending a
  second, CVM, implementation). `CefGibbs` (`system/model/cef`) is its
  sole current implementation: a general n-sublattice CEF Gibbs-energy
  evaluator with analytical `G`, `dG_dy`, `d2G_dy2`, `dG_dT`,
  `d2G_dydT`, `dG_dP`, `d2G_dydP` — reference, ideal, excess, magnetic
  (Inden-Hillert-Jarl), and volume/pressure (`V0*exp(VA)*(P-P_ref)`)
  contributions all analytically differentiated, with reference/excess
  gradients and Hessians both derived from one shared `AD2`
  (second-order automatic differentiation) construction so they cannot
  silently diverge from each other.
- Assembling and inverting the Newton phase matrix from those values is
  calculation-layer work, not model-layer work
  (`calc/equil/PhaseMatrixAssembler`) — it operates purely through
  `GibbsEnergyModel`'s abstract surface, so a future non-CEF model (e.g.
  CVM) gets phase-matrix/equilibrium-matrix assembly for free instead of
  reimplementing it. `EquilibriumSolverV2` is the sole production
  solver consumer.
- `CefContractTest` (`src/test/`) verifies every quantity on
  `GibbsEnergyModel`'s abstract contract against pycalphad 0.11.1 with
  every comparison gated (no report-only exceptions — the R-constant
  convention difference is corrected analytically in the reference
  values, not excused), plus structural invariants (Hessian symmetry,
  `dMoles_dy` y-independence, composition/mole consistency, `isValid`
  accept/reject) that hold independent of any reference implementation.
  This caught a real bug this session: the Redlich-Kister interaction
  gradient double-counted a term for any RK order > 0, silently wrong
  for essentially any phase with a binary/ternary interaction parameter,
  fixed and now regression-tested.
- A Sundman-style (2015 ComMatSci) multiphase equilibrium solver in
  `calc/equil/EquilibriumSolverV2`, implementing the full flowchart
  (`docs/solver_flowchart_target.png`) rather than a single-phase-only
  path:
  - **Initialization** (`GridMinimizer`): a pycalphad-verified port of
    `calculate()`'s grid sampling (scrambled Halton sequence, endmembers,
    edges, interior points — `Halton.java`) and `lower_convex_hull()`'s
    N-dimensional tangent-hyperplane pivot search (`Hyperplane.java`),
    used to find the initial stable-phase set and per-phase site
    fractions directly, including correctly representing a miscibility
    gap as two independent stable slots of the same candidate phase.
  - **Newton iteration** (STEP 1-9): per-phase Newton-response
    coefficients (`PhaseMatrixAssembler`, Sundman Eq. 40) and the global
    multiphase equilibrium matrix (`GlobalEquilibriumMatrixAssembler`,
    Eq. 58) are extracted into stateless, independently-tested
    assemblers; the Newton step is damped at bounds (phase-amount and
    site-fraction step-size capping) rather than hard-failing, matching
    pycalphad's own `advance_state()`.
  - **Phase-set management** (STEP 8, `updateStablePhaseSet()`): removes
    a stable phase once its amount reaches the phase-amount floor and
    adds a metastable candidate once its driving force (Eq. 62) turns
    positive — evaluated over the same style of sampled grid
    `GridMinimizer` uses, not a single frozen constitution, following
    pycalphad's `Solver.solve()`/`add_new_phases()` design
    (`eqsolver.pyx`) including its composition-distinctness
    anti-thrashing guard.
  - Every stage above has a pycalphad-referenced regression test
    (`PhaseMatrixAssemblerContractTest`, `GlobalEquilibriumMatrixAssemblerContractTest`,
    `SiteFractionCorrectionContractTest`, `GridMinimizerPycalphadTest`
    in `src/test/`, plus JUnit end-to-end tests in
    `src-test/calc/equil/`), validated against the V–Zr TDB
    (`data/VZR-re2.TDB`) for both an ordinary substitutional-like
    sublattice phase (BCC_A2, with a vacancy sublattice) and a
    stoichiometric two-sublattice ordered phase (V2ZR), including a
    genuine two-phase (V2ZR + BCC_A2) equilibrium that converges.
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

- **`updateStablePhaseSet()`'s add/remove tolerances are engineering
  defaults, not derived from Sundman's paper.** The paper only says
  "allow a few iterations after a change... before another change is
  allowed" with no numbers; the current thresholds mirror pycalphad's
  own constants (`minimum_df=1e-4`, `COMP_DIFFERENCE_TOL=1e-4`,
  `MIN_PHASE_FRACTION=1e-6`) rather than anything independently derived
  for this solver's own unit/normalization conventions.
- **Multiphase convergence has been validated on the V-Zr binary only**
  (V2ZR + BCC_A2), including phase-set changes (a redundant
  miscibility-gap slot being removed, and a missing phase being added
  from a single-phase start). Ternary+ systems and larger phase counts
  are untested.
- `calculateStep`/`calculateMap` on `CalculationSession` are explicit
  unimplemented stubs — there is no plain property-sampling engine (as
  opposed to full phase-boundary tracing) in the codebase yet.
- Two-state/Einstein and ordering/disordering (B2/L1₂-style) contributions
  are not implemented in `CefGibbs` at all (separate from the
  pressure-dependent-`FUNCTION` gap noted above). `VK` (isothermal
  compressibility) is detected and rejected with an explicit exception
  rather than silently ignored, matching pycalphad's own unimplemented
  status for it.
- RK and CVM models are not connected to the TDB → equilibrium production
  path; only CEF phases can currently be built and solved end-to-end from
  a TDB file. `GibbsEnergyModel`'s sublattice-block accessors
  (`numSublattices`/`offsets`/`constituentsPerSublattice`) carry CEF's
  own vocabulary for now — deliberately not generalized until a second
  (CVM) implementation exists to validate what generalization actually
  fits both.
- The test suite is mostly standalone diagnostic `main()` programs under
  `src/test/` (pycalphad-referenced contract tests among them — run
  individually via `java -cp build/classes/java/main test.<ClassName>`)
  rather than the Gradle/JUnit setup `build.gradle` declares; only one
  JUnit test class exists so far (`src-test/calc/equil/`, several
  end-to-end methods). There is no single command that runs everything
  as a pass/fail regression gate.
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
