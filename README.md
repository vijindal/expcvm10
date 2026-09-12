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

The equilibrium engine (`calc/equil/`) depends only on the model-agnostic
`GibbsEnergyModel` contract (G, gradient, Hessian, T/P derivatives,
internal-variable handling) and never contains model-specific logic
(CEF sublattices, RK polynomial order, CVM cluster variables). Each
model implementation is responsible for satisfying that contract
without the engine knowing its internals — this is what lets new
models, and eventually new equilibrium algorithms, be added without
rewriting the rest of the system.

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

The System Layer (phase models, parameters, database-derived structure)
is built once and unchanged during a calculation; the Calculation Layer
(phase amounts, compositions, chemical potentials, solver iterations)
changes every iteration. The System Layer holds no state between solver
calls; the Calculation Layer holds no thermodynamic knowledge.

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

See [docs/dataflow_target.png](docs/dataflow_target.png) for the full
data-flow figure.

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

## Current state

The codebase is organized into the layers described above. The legacy
assessment code is quarantined under `legacy/`.

### Current capabilities

- **TDB parsing** for standard SGTE-style syntax: elements, `FUNCTION`
  substitution, multi-sublattice `PHASE`/`CONSTITUENT` records, `G`/`L`
  (Redlich-Kister, arbitrary order), `TC`/`BMAGN` (magnetic), `V0`/`VA`
  (molar volume), and `TYPE_DEFINITION` declarations. Caches by file
  path. 516/545 phase × database combinations across the 11 bundled
  TDBs build and evaluate cleanly; the rest are genuine missing-
  parameter rejections. Known gap: end-member `G()` expressions with
  pressure dependence via nested `FUNCTION` chains (a pre-`V0`/`VA`
  mechanism) are silently dropped (`CefContractTest`'s `knownPResidual`
  cases).
- **`GibbsEnergyModel`** (`system/model`): a minimal, model-agnostic
  abstract contract — every method is either required by Sundman's
  phase-matrix/equilibrium-matrix construction (2015 Eq. 40/58) or a
  genuine external entry point. `CefGibbs` (`system/model/cef`) is its
  sole implementation: a general n-sublattice CEF evaluator with
  analytical `G`, `dG_dy`, `d2G_dy2`, `dG_dT`, `d2G_dydT`, `dG_dP`,
  `d2G_dydP` — reference, ideal, excess, magnetic (Inden-Hillert-Jarl),
  and volume/pressure contributions, all sharing one `AD2`
  (second-order automatic differentiation) construction so gradients
  and Hessians cannot silently diverge. `CefContractTest` (`src/test/`)
  verifies every quantity against pycalphad 0.11.1, plus structural
  invariants (Hessian symmetry, `dMoles_dy` y-independence,
  composition/mole consistency, `isValid` accept/reject).
- **Newton phase-matrix assembly** (`calc/equil/PhaseMatrixAssembler`)
  is calculation-layer work operating purely through
  `GibbsEnergyModel`'s abstract surface, so a future non-CEF model
  (e.g. CVM) gets it for free. `EquilibriumSolverV2` is the sole
  production solver consumer.
- **Multiphase equilibrium solver** (`calc/equil/EquilibriumSolverV2`),
  implementing the full flowchart (`docs/solver_flowchart_target.png`):
  - *Initialization* (`GridMinimizer`): a pycalphad-verified port of
    `calculate()`'s grid sampling (Halton sequence, endmembers, edges —
    `Halton.java`) and `lower_convex_hull()`'s tangent-hyperplane pivot
    search (`Hyperplane.java`), correctly representing a miscibility
    gap as two independent stable slots of the same candidate phase,
    with duplicate-composition-slot merging (`mergeDuplicateCompositions`,
    ported from OpenCalphad's `same_composition()`).
  - *Newton iteration*: per-phase response coefficients
    (`PhaseMatrixAssembler`, Eq. 40) and the global equilibrium matrix
    (`GlobalEquilibriumMatrixAssembler`, Eq. 58) are stateless,
    independently-tested assemblers; the Newton step is damped at
    bounds rather than hard-failing, matching pycalphad's
    `advance_state()`.
  - *Phase-set management* (`updateStablePhaseSet()`): removes a
    stable phase once its amount hits the phase-amount floor and adds
    a metastable candidate once its driving force (Eq. 62) turns
    positive, following pycalphad's `Solver.solve()`/`add_new_phases()`
    design; also merges stable slots that converge to the same
    composition mid-iteration.
  - *ZPF boundary solve* (`solveBoundary`, Algorithm C2): fixes one
    phase's amount at exactly zero via variable elimination (ported
    from and verified against OpenCalphad's `matsmin.F90`/
    `map_calcnode`) and solves for the exact boundary composition —
    the numerical basis for `MapTracer`. A sibling, `solveBoundaryReleasingT`,
    releases temperature instead (needed to locate an invariant node,
    where the stable set jumps by more than one phase at a single
    point) — enabled by `dG_dT`/`dM_dT` on `PhaseEquilData`, composed
    from per-phase T-derivative machinery `PhaseMatrixAssembler`
    already computed but never wired into the global matrix before.
  - Validated against the V–Zr TDB (`data/VZR-re2.TDB`) for BCC_A2
    (vacancy sublattice), HCP_A3, LIQUID, and the ordered V2ZR,
    including converging two-phase equilibria, via pycalphad-referenced
    contract tests (`src/test/`) and JUnit end-to-end tests
    (`src-test/calc/equil/`). `EquilibriumSolverV2BaselineTest`
    (`src/test/`) additionally checks the solver itself against
    J. Cui et al. 2016 (CALPHAD 53): the V2ZR Gibbs-energy curve
    (Fig. 9) and all three Table 2 invariant reactions, each split
    into its adjacent two-phase fields.
- **Phase-diagram tracing** (`calc/diagram`), Sundman Algorithm B:
  - `StepTracer` (step branch): walks one axis, detects stable-phase-set
    changes, locates crossings by black-box bisection.
  - `MapTracer` (map branch): walks one axis in fixed increments (C1),
    solves each ordinary boundary crossing exactly via `solveBoundary`
    (C2, composition release), and locates a genuine invariant node
    (eutectic/peritectic, where the stable set jumps by more than one
    phase at once) via `solveBoundaryReleasingT` instead, confirming it
    with `InvariantExitFinder` (D, combinatorial exit enumeration
    verified against pycalphad's binary-mapper combinatorics — an
    earlier version of this class had an exit-count bug that made it
    never fire). Currently traces one line per call and only releases T
    at an invariant when the walk axis is TEMPERATURE; multi-line
    auto-discovery of a full diagram is not yet implemented. Known,
    documented gap: `solveBoundaryReleasingT` converges too slowly when
    seeding a genuinely new phase from a generic initial guess (confirmed
    against V-Zr's own 1586K peritectic) — `MapTracer` degrades
    gracefully (`isComplete()==false`) rather than mislabeling the node.
  - `CoarseDiagramTracer`: samples a binary/ternary T-x grid via
    `GridMinimizer` only (no Newton solve per point) and reports the
    stable phase set at each point, for scatter/dot rendering — distinct
    from `StepTracer`/`MapTracer`'s precise line-following.
  - Both `calculateStep` and `calculateMap` on `CalculationSession` are
    implemented and wired into the GUI (Coarse Diagram tab; STEP/MAP
    activities use the shared `PhaseDiagramConfigPanel`).
- **`ThermodynamicSystem` / `CalculationSession`**: a build-once/reuse
  coordinator — parses a TDB and builds phase models once, then serves
  repeated calculations and database/element/phase browsing. All three
  UIs (GUI, CLI, REST API) go through it and produce bit-identical
  results for the same scenario.
- **REST/JSON API** (`src/ui/api`, `com.sun.net.httpserver` + Gson):
  explicit session lifecycle, per-session locking, `equilibrium` and
  `phase-diagram` endpoints (`step`/`map` return 501 — not yet updated
  for the new tracers). Launch via `ui.api.ApiMain [port]`.
- A standalone RK (Redlich-Kister) model and a standalone CVM model
  (binary systems, parsed from Mathematica `.nb` output), implemented
  but not yet wired into the TDB → equilibrium pipeline.
- A legacy Levenberg-Marquardt assessment pathway (`legacy/calbince`),
  used by the `opt`/`cal` CLI commands, kept separate from the new code.

### Current limitations

- `updateStablePhaseSet()`'s add/remove tolerances
  (`minimum_df=1e-4`, `COMP_DIFFERENCE_TOL=1e-4`, `MIN_PHASE_FRACTION=1e-6`)
  are engineering defaults borrowed from pycalphad, not derived from
  Sundman's paper (which gives no numbers).
- Multiphase convergence is validated on the V–Zr binary and a handful
  of other binaries/quaternaries only. A known, unfixed gap remains:
  two stable slots can converge to near-identical compositions
  *during* Newton iteration (not just at `GridMinimizer` init) and
  intermittently cause a singular global matrix — most visible on
  quaternary systems and near-symmetric compositions.
- `MapTracer` traces one line per call and does not yet auto-discover
  and stitch together every line/invariant a full diagram needs.
  Ordinary boundary crossings release a composition axis only; T-release
  (needed for invariant nodes) works for TEMPERATURE walk axes but
  converges too slowly when seeding a genuinely new phase to reliably
  locate an invariant yet (`CalculationSessionMapTracerTest` Section C
  documents this against a real peritectic). P-release is not
  implemented (`PhaseMatrixAssembler` computes `dG/dP`-based
  coefficients per phase, but they are not yet threaded through the
  global matrix the way T now is).
- Two-state/Einstein and ordering/disordering (B2/L1₂-style)
  contributions are not implemented in `CefGibbs`. `VK` (isothermal
  compressibility) is detected and rejected explicitly rather than
  silently ignored, matching pycalphad's own unimplemented status.
- RK and CVM models are not connected to the TDB → equilibrium path;
  only CEF phases build and solve end-to-end from a TDB file.
  `GibbsEnergyModel`'s sublattice-block accessors carry CEF's own
  vocabulary for now, pending a second (CVM) implementation to
  validate what generalization fits both.
- Most of the test suite is standalone diagnostic `main()` programs
  under `src/test/` (including pycalphad-referenced contract tests —
  run via `java -cp build/classes/java/main test.<ClassName>`), not
  the Gradle/JUnit setup `build.gradle` declares. Only one JUnit test
  class exists (`src-test/calc/equil/`). No single command runs
  everything as a pass/fail gate.
- `InvariantExitFinder` (Algorithm D) is written for general component
  counts but only verified against binary systems; the grid-minimizer's
  convex-hull step itself is not similarly limited.
- API/GUI hardening is out of scope: no authentication, TLS, or
  session expiry on the REST API; the REST API's `step`/`map` endpoints
  have not been updated to use the new tracers.

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
boundaries.
