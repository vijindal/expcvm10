# Vision: an integrated database (.cedb) and CE-CVM as the long-term model

**Status: vision / not-yet-built.** This document states direction and
sequencing, not an implementation plan for the next PR. It exists so
short-term decisions (what to hardcode, what to port, what to defer) can be
checked against where the project is actually going, without re-litigating
the destination each time.

## The destination

Today's model layer offers one general phase model, the Compound Energy
Formalism (`system.model.cef.CefGibbs`), which captures **long-range order
(LRO)** through sublattice site fractions but has no way to represent
**short-range order (SRO)** — the correlation between neighbouring atoms
that survives even in a nominally disordered solid solution. The Cluster
Variation Method (CVM), driven by cluster-expansion coefficients (CE-CVM),
captures both LRO and SRO in one free-energy expression: a disordered phase
and its ordered variant are the same cluster algebra at different points,
not two different models bolted together.

The long-term vision for this project is for **CE-CVM to become the primary
Gibbs-energy model**, with CEF demoted to the fallback used where CE-CVM
coverage does not yet reach. This is a capability upgrade, not a parallel
feature: a converged CE-CVM phase should eventually make dedicated
short-range-order machinery, ordering-reaction bookkeeping, and some
CEF-specific interaction-parameter fitting unnecessary, because SRO/LRO
already fall out of the same cluster probabilities the free energy is built
from (this is exactly what CEWorkbench's `pairSro`/`quaternaryTetrahedronSro`
already demonstrate for the cluster-expansion side of the physics — see
`CVMGibbsModel.State` there, and note per the existing project boundary that
that code is a *reference implementation to port from*, not a dependency;
nothing in this project imports or links against CEWorkbench).

Alongside the model change, the database itself should stop being a
second-class, CEF-shaped text format that CE-CVM parameters are awkwardly
squeezed into. The vision is a **native integrated database format**,
tentatively named **`.cedb`** (Cluster-Expansion Database — name open to a
better suggestion; alternatives considered: `.tcdb` "thermodynamic +
cluster", `.xdb`; `.cedb` is used below as a placeholder, not a commitment),
that represents CEF parameters, CE-CVM cluster expansion coefficients, and
unary/reference data as first-class, equally-weighted citizens of one
schema — not one borrowing the other's grammar. `.TDB` remains fully
supported, both because the field's real-life databases are almost all
`.TDB` and because CEF must keep working standalone; `.cedb` is an
**addition**, reachable by lossless import from `.TDB` and by export back to
it wherever the round trip is representable (a CE-CVM phase has no `.TDB`
equivalent to export to — see below).

## Why this is staged, not immediate

Three concrete gaps keep CE-CVM from simply replacing CEF today, and each
gates part of the sequencing below:

1. **Structure coverage.** CE-CVM's cluster-geometry pipeline (ported from
   CEWorkbench's Stage 1-4 identification: `Cluster`, `CvCfBasis`,
   `CMatrixPipeline`) currently has worked-through geometry data for a
   handful of structures — BCC_A2, HCP_A3, FCC_A1, with BCC_B2/FCC_L12/
   CUB_C15 present but less exercised. CEF, by contrast, represents *any*
   sublattice structure a `.TDB` `PHASE`/`CONSTITUENT` line can express,
   because it only needs site ratios and constituent lists, not a
   pre-derived cluster algebra. Real multicomponent systems routinely need
   phases with no CE-CVM geometry yet (intermetallics, complex sublattice
   compounds, anything not yet run through the cluster-identification
   pipeline).
2. **Ordered-structure treatment is structure-specific in CE-CVM in a way
   CEF's sublattice formalism is not.** CEF treats an ordered phase (e.g.
   BCC_B2) as "the same model, a different constituent-array pattern" —
   the same `CefGibbs` code path handles A2 and B2 uniformly. CE-CVM
   instead needs a distinct cluster algebra *per structure*, because the
   symmetry-distinct sub-orbits, the CVCF C-matrices, and even which
   correlation functions carry an order parameter (B2's extra point CF
   `eta`, noted as an open item in CEWorkbench's own `CvmGeometry`
   javadoc) are all structure-dependent, non-generic derivations. This
   project's own ordered-phase support is presently limited even on the
   CEF side (per current project state), so CE-CVM's ordered-phase
   handling — already flagged as unreviewed even in the reference
   implementation — is further out still.
3. **Database coverage.** The field's validated, literature-backed
   thermodynamic assessments exist overwhelmingly as `.TDB` CEF parameters,
   built up over decades of CALPHAD assessment work. CE-CVM Hamiltonians
   (CE-fitted cluster expansion coefficients) exist today for only a
   handful of systems (the Nb-Ti-V-Zr family and close relatives). Dropping
   CEF support, or de-prioritizing `.TDB` import, would cut this project
   off from every real multicomponent system it needs to be useful for in
   the near term.

Given these three gaps, **CEF and `.TDB` remain load-bearing for real
calculations for the foreseeable future.** CE-CVM is adopted
phase-by-phase, structure-by-structure, as coverage is built out and
validated — never as a wholesale replacement while coverage is this narrow.

## Sequencing (high-level; each phase is its own later plan, not detailed here)

1. **Phase 0 — proof of integration (near-term, already planned
   separately).** Embed CE-CVM cluster-expansion coefficients directly as
   new `PARAMETER` records inside the existing `.TDB` grammar (a new
   parameter type, e.g. `CECVM(...)`, alongside today's `G(...)`), with a
   `TYPE_DEFINITION` marking which phases use it and which CVCF model
   identity to build against. Dispatch is hardcoded to one structure
   (BCC_A2) at first. This validates the `GibbsEnergyModel` adapter and the
   equilibrium-solver wiring end-to-end without yet requiring a new file
   format. CEF continues to serve every other phase in the same database.
2. **Phase 1 — design `.cedb`.** Once Phase 0 has proven the model and
   solver integration, design `.cedb` as a schema that natively expresses:
   CEF sublattice parameters (a structural transcription of `.TDB`'s own
   grammar, not a reinvention), CE-CVM cluster expansion coefficients keyed
   by named correlation functions, and the per-structure cluster-geometry
   identity a CE-CVM phase needs to resolve against. Build the `.TDB` <->
   `.cedb` transform: import is total (every `.TDB` construct maps into
   `.cedb`); export is total only for the CEF-representable subset (a
   `.cedb` phase using CE-CVM parameters has no `.TDB` target and exports
   with an explicit, visible "CEF equivalent not available" marker rather
   than a silent lossy approximation.
3. **Phase 2 — broaden CE-CVM structure coverage.** Port/derive
   cluster-geometry data for additional structures as real systems demand
   them, validating each against a reference calculation before it is
   trusted for production use — the same discipline `docs/roadmap_phase_diagrams.md`
   already applies to solver features.
4. **Phase 3 — ordered structures.** Extend CE-CVM's per-structure
   ordered-phase handling once the disordered-phase path (Phase 0-2) is
   solid, tracking the reference implementation's own open item that
   ordered CVCF geometry is unreviewed.
5. **Phase 4 — CE-CVM as default, CEF as fallback.** Once coverage,
   validation, and the `.cedb` schema are mature enough that most
   real-system calculations resolve to a CE-CVM phase model without a user
   having to ask for it explicitly, flip the default: `PhaseModelFactory`
   picks CE-CVM whenever geometry and cluster-expansion data are available
   for a phase, and falls back to CEF only when they are not. This is the
   point at which the project's model-agnostic `calc/equil` design (see
   README's "Design principle: the equilibrium kernel is central") pays
   off — the equilibrium engine does not change at all when the default
   flips, because it was never written against CEF specifically.

## What does not change

- The equilibrium kernel (`calc/equil`) stays model-agnostic through the
  existing `GibbsEnergyModel` contract; CE-CVM is adopted as another
  implementation of that contract, not as a parallel solver stack.
- `.TDB` import/compatibility is permanent, not a migration off-ramp. Real
  literature databases will be `.TDB` for a long time, and `.cedb` is
  additive.
- This project remains independent per its stated project scope: CE-CVM is
  **ported and re-implemented** here against this project's own
  conventions and its own equilibrium solver, never imported as a
  dependency on or reference-tested against CEWorkbench at runtime.
