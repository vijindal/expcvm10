# Development Plan

This document records the staged roadmap for expCVM10, independent of any
other codebase. See [README.md](README.md) for the project's scope and
long-term vision.

## Staged roadmap

1. **Thermodynamic foundation** — reliable Gibbs-energy models, analytical
   derivatives (gradient, Hessian, T/P derivatives), correct
   composition/site-fraction mapping, and TDB parsing support for each
   model type.
2. **Equilibrium kernel** — single-phase → two-phase → general multiphase
   equilibrium: stability, phase addition/removal, mass balance, and
   equality of chemical potentials, built on the model-agnostic
   `GibbsEnergyModel` contract.
3. **Binary phase diagrams** — ZPF line tracing, invariant reactions,
   isobaric T-x sections.
4. **Ternary equilibrium and phase diagrams** — extending the equilibrium
   kernel and diagram tracer to three-component systems, including a true
   ternary convex-hull initial guess.
5. **General multicomponent capability** — n-component equilibrium and
   diagram/section calculation.
6. **Advanced thermodynamic models and properties** — additional model
   types (ionic liquid, two-sublattice liquid, higher-order CEF
   interactions, activated magnetic/Einstein/two-state contributions) and
   derived properties (enthalpy, entropy, heat capacity, activities).
7. **Assessment/optimization and production ecosystem** — parameter
   fitting against experimental data integrated with the new architecture,
   stable API/CLI/GUI, packaging, and documentation.

## Current status

**Stage 1 (thermodynamic foundation) is essentially complete for the
generalized CEF single-phase case.** The recent V–Zr validation
(`data/VZR-re2.TDB`) confirmed, for both BCC_A2 (a sublattice phase with a
vacancy sublattice) and V2ZR (a stoichiometric two-sublattice ordered
phase):

- correct TDB parsing and CEF model construction;
- correct composition ↔ site-fraction mapping, including a fix for phases
  with non-element (vacancy) sublattices;
- correct single-phase equilibrium: chemical potentials satisfying the
  tangent-plane/Euler relation exactly, and zero driving force at
  convergence;
- a well-defined, invertible phase (Hessian) matrix, including at an
  ordered stoichiometric composition.

**The immediate next milestone is robust binary multiphase equilibrium**,
using the V–Zr system as the primary validation case (BCC_A2, HCP_A3,
LIQUID, V2ZR).

## Immediate milestone: binary multiphase equilibrium — acceptance criteria

The multiphase equilibrium kernel (`EquilibriumSolver`'s Newton iteration
and phase-set management) will be considered validated for this milestone
when, on the V–Zr system, it demonstrates:

- **Correct single-phase limit** — multiphase code paths reduce to the
  already-validated single-phase result when only one phase is stable.
- **Two-phase equilibrium** — correct compositions, amounts, and Gibbs
  energy for known two-phase V–Zr regions (e.g. V2Zr + HCP_A3, BCC_A2 +
  LIQUID).
- **Valid, non-negative phase amounts** — no phase is reported with a
  negative or physically nonsensical (e.g. astronomically large) amount.
- **Mass balance** — the sum of phase amounts weighted by phase
  composition reproduces the prescribed overall composition to solver
  tolerance.
- **Equality of chemical potentials** — each component's chemical
  potential is equal across all stable phases at convergence.
- **Consistent driving forces** — zero driving force for all stable
  phases, and non-positive driving force for all metastable phases, at
  convergence.
- **Phase addition/removal** — the solver correctly adds a metastable
  phase with positive driving force and removes a stable phase whose
  amount goes to zero/negative, without oscillation or failure to
  converge.
- **Robust convergence** — convergence within a bounded number of
  iterations across a representative set of V–Zr temperatures and overall
  compositions, without reliance on ad hoc tuning per test case.
- **Validation against known V–Zr equilibria** — results consistent with
  the phase relationships described by the V–Zr thermodynamic assessment
  underlying `data/VZR-re2.TDB` (expected stable phase sets and
  approximate phase boundaries at representative temperatures).

## Explicitly not immediate scope

The following are recognized as necessary eventually but are **not** part
of the current milestone and should not be pursued opportunistically while
it is in progress:

- Broad architectural refactoring of the existing layers.
- Expanding RK or CVM model coverage, or wiring them into the production
  TDB → equilibrium pipeline.
- A more sophisticated GUI.
- Ternary or general n-dimensional convex-hull development for the grid
  minimizer.
- Performance optimization of the equilibrium engine or numerics.
- Cleanup of unrelated legacy code (`legacy/calbince`, `legacy/phase`)
  not required for the multiphase equilibrium milestone.
