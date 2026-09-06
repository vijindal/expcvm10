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

## Current state (as of the V–Zr single-phase CEF validation)

### Current capabilities

- TDB parsing for standard SGTE-style syntax: elements, `FUNCTION`
  substitution, multi-sublattice `PHASE`/`CONSTITUENT` records, `G`/`L`
  (Redlich-Kister interaction) and `TC`/`BMAGN` (magnetic) parameters,
  `TYPE_DEFINITION` magnetic declarations.
- A general n-sublattice CEF Gibbs-energy evaluator (`system/model/cef`)
  with analytical G, gradient, and Hessian, wired end-to-end into the
  equilibrium solver via `CefPhaseModelAdapter`.
- A Sundman-style (CALPHAD 75, 2021) single-phase equilibrium path in
  `calc/equil/EquilibriumSolver`: given a fixed T, P, and overall
  composition, it builds the correct site-fraction state, converges the
  chemical potentials via the tangent-plane/Euler relation, and reports a
  self-consistent Gibbs energy, mole fractions, and zero driving force.
  This has been validated against the V–Zr TDB (`data/VZR-re2.TDB`) for
  both an ordinary substitutional-like sublattice phase (BCC_A2, with a
  vacancy sublattice) and a stoichiometric two-sublattice ordered phase
  (V2ZR), including correct composition round-tripping and Hessian
  evaluation at the ordered stoichiometric point.
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
  `EquilibriumSolver` for 2+ stable phases, and the `eMatNC`
  (site-fraction-to-mole-fraction response projection) it depends on in
  `CefPhaseModelAdapter`, are still incomplete/placeholder — `eMatNC` is
  currently a hard-coded identity matrix rather than a true projection of
  the phase-matrix response. Multiphase test cases in the existing
  diagnostic (`src/test/CefBuildTest.java`) do not currently converge to
  physically correct results.
- CEF interaction parameters currently support only 2-sublattice pair×
  single-sublattice interactions with a single T-linear term; there is no
  higher-order Redlich-Kister expansion within CEF interactions.
- The magnetic contribution (Inden-Hillert model) is implemented but its
  composition-dependent Curie temperature/Bohr-magneton-number
  calculation is not yet wired in (`computeTc`/`computeBeta` return 0).
- RK and CVM models are not connected to the TDB → equilibrium production
  path; only CEF phases can currently be built and solved end-to-end from
  a TDB file.
- There is no automated regression test suite (no JUnit tests exist despite
  being referenced in the build configuration); validation is currently
  done via standalone diagnostic programs under `src/test/`.
- Phase-diagram tracing has only been exercised for binary systems; the
  underlying grid-minimizer's convex-hull step is binary-only (ternary+
  falls back to a non-hull heuristic).

### Long-term intended capabilities

- Robust single-phase through general multicomponent, multiphase
  equilibrium for arbitrary combinations of unary, RK, CEF, and CVM
  phases.
- Binary, ternary, and general multicomponent phase-diagram calculation.
- A stable, documented API/CLI usable for scripting and integration, and a
  GUI sufficient for interactive exploration.
- Parameter assessment against experimental data integrated with the new
  model/equilibrium architecture (not only the legacy pathway).

See [PLAN.md](PLAN.md) for the staged development roadmap and the current
immediate milestone.
