# Roadmap: Automated Phase-Diagram Calculation

**Priority: first.** Goal — fully automated calculation of the phase-diagram
types users actually ask for, without manually specifying which boundary
lines to trace or where invariants are. Today's tracers (`StepTracer`,
`MapTracer`) each follow **one line per call**, chosen and configured by the
caller; nothing auto-discovers a complete diagram yet.

**`calc.diagram.PhaseDiagramEngine`** is the single skeleton class mirroring
[phase_diagram_engine_flowchart.md](phase_diagram_engine_flowchart.md)
top-to-bottom, one method per flowchart stage — implemented stages delegate
to `MapDiagramTracer`/`MapTracer`/`NodeRegistry`, unimplemented ones throw
`UnsupportedOperationException` naming the exact gap. `PhaseDiagramEngineTest`
keeps it honest: each "not yet implemented" stage has an `assertThrows` that
must be intentionally updated (not silently left stale) once that stage is
actually built. Check that class first when picking up the next step below.

## Target diagram types

1. **Binary phase diagram** (T vs. composition, full diagram)
   Auto-discover every ZPF line and invariant reaction (eutectic,
   peritectic, congruent, etc.) across the whole composition range — not
   one line per `map` call as today.
2. **Ternary isothermal section** (fixed T, composition triangle)
   Auto-trace all two-phase and three-phase region boundaries at a given
   temperature.
3. **Ternary isopleth** (fixed ratio of two components, T vs. third
   component — a vertical section through the ternary prism)
   Same auto-discovery, on a 2D slice defined by a composition constraint
   instead of a free ternary plane.
4. **Pseudo-isothermal section** (fixed T, but for higher-order systems —
   an isothermal cut through a quaternary+ system projected/fixed onto
   two composition axes)
   Generalization of (2) beyond strict ternaries.
5. **Property/stability-vs-variable plot at fixed composition** (e.g. phase
   fraction, or which phases are stable, vs. T — or vs. P, or any other
   state variable — for one fixed alloy composition)
   This is largely `StepTracer` already (single-axis scan + stable-set
   change detection) but packaged as a first-class "stability diagram"
   output (phase-fraction-vs-T stacked plot) rather than raw ZPF-crossing
   data.

## One engine, not one tracer per diagram type

Sundman, Dupin & Hallstedt 2021 (`docs/2021-calphad-sundman-Algorithms
useful for calculating multi-component equilibria, phase diagrams and
other kinds of diagrams.pdf`) is explicit about this and it settles the
question directly: OpenCalphad uses **one mapping engine**, not separate
code per diagram shape.

- **Algorithm A** (single equilibrium, our `EquilibriumSolverV2`) is
  generic over any condition set — T/P/composition fixed, or a phase
  fixed at zero amount, or a composition ratio fixed, etc.
- **Algorithm B** (step vs. map) is one flowchart with two branches
  (1 axis = step, 2 axes = map) — the branch point is the *number of
  axes*, not the diagram's shape.
- **Algorithm C1** (follow a line, incrementing axes) and **C2** (handle
  a stable-set change: fix the appearing/disappearing phase at zero,
  release an axis, spawn a node with exits) are identical machinery
  whether the line being followed is a binary T-x boundary, a ternary
  isothermal boundary, or an isopleth boundary. What changes between
  diagram *types* is only **which conditions are held fixed as axes**:
  T & one composition (binary), T fixed & two compositions (ternary
  isothermal), a composition ratio fixed & T (isopleth), or the same
  ternary-isothermal setup lifted to fixed mole fractions of the extra
  components in a 4+-component system (pseudo-isothermal).
- **Algorithm D** (invariant exit-finding, our `InvariantExitFinder`) is
  written for general *n* components and reused unchanged whether the
  invariant is a 3-phase binary eutectic or the 6-phase invariant in the
  paper's 5-component HSS isopleth example (Section 4.3) — same
  algorithm, no per-diagram-type variant.
- Section 4 of the paper demonstrates binary, ternary isothermal, ternary
  isopleth, and a 5-component isopleth all coming out of **the same
  mapping run**, just plotted/labeled with different axis choices
  afterward.

**Conclusion for this codebase:** `MapTracer` + `InvariantExitFinder`
should stay one generic engine (our Algorithm C1/C2/D), extended with:
1. the outer multi-line/node-stitching loop (Algorithm B/C1's "search
   for unresolved exits, generate lines until none remain") that doesn't
   exist yet — this is the actual missing piece, not a per-diagram-type
   tracer;
2. generalized axis/condition setup so a caller can select *which two
   things* are held fixed as plot axes (T+composition, two compositions,
   or a composition-ratio+T), which is what actually distinguishes a
   binary diagram from a ternary isothermal section from an isopleth —
   not new solver logic;
3. `InvariantExitFinder` validated beyond binary systems, since its
   algorithm is already general but only tested on binaries so far.

A single `CalculationSession` method (something like
`calculatePhaseDiagram(axisSpecs...)`, already stubbed as `diagram` in
the CLI but "not yet implemented upstream") is the right long-term
shape — **not** four separate `calculate*Diagram` methods — once the
axis-selection generalization above is done. `calculateCoarseBinaryDiagram`
/ `calculateCoarseTernaryDiagram` remain a separate, legitimately
different code path (`CoarseDiagramTracer`, grid-sampling only, no
precise boundaries) — that's a different algorithm (Sundman's grid
minimizer alone, no Algorithm C1/C2/D), not a diagram-type split of the
same one.

## Verification reference: OpenCalphad

`D:\codes\opencalphad` is a working OpenCalphad build/checkout, to be
used as the step-by-step reference implementation for testing this
engine — the same role the V–Zr TDB + Cui et al. 2016 paper already play
for the equilibrium solver. Relevant OpenCalphad source:

- `src/stepmapplot/smp2.F90`, `smp2A.F90`, `smp2B.F90` — the actual
  step/map implementation. `smp2.F90` documents the diagram-type
  dispatch as a mode flag on one engine (`map_tieline_inplane`,
  `map_isotherm`, `map_isopleth`, ...), confirming the "one engine, mode
  selected by axis/condition choice" architecture above at the
  implementation level, not just in the paper.
- `examples/macros/*.OCM` — runnable macro scripts exercising step/map
  on real systems; useful as input/output pairs to test our tracer
  against, the same way `data/VZR-re2.TDB` is used for the equilibrium
  solver today.

Suggested verification approach: pick one OpenCalphad macro per target
diagram type (binary, ternary isothermal, isopleth), run it against
OpenCalphad to get reference line/node/invariant output, then drive the
same TDB + conditions through our engine once the multi-line stitching
above exists, and diff.

## What already exists (from `calc/diagram`)

- **`StepTracer`** — walks one axis, detects stable-phase-set changes,
  locates crossings by black-box bisection. This is the numerical core of
  target (5) above.
- **`MapTracer`** — walks one axis in fixed increments, solves each
  ordinary boundary crossing exactly (`solveBoundary`, composition
  release), and locates genuine invariant nodes (stable-set jump by more
  than one phase) via `solveBoundaryReleasingT`, confirmed by
  `InvariantExitFinder`'s combinatorial exit enumeration (Sundman
  Algorithm D). This is the numerical core of target (1), but only traces
  **one line** per invocation today.
- **`CoarseDiagramTracer`** — grid-samples a binary/ternary T-x space via
  `GridMinimizer` only (no Newton solve per point), reporting the stable
  phase set at each sample point. Good for a fast scatter/dot preview, not
  precise boundaries. Already wired into the GUI's Coarse Diagram tab.
- **`InvariantExitFinder`** (Algorithm D) — enumerates which lines exit an
  invariant node and in which direction; verified against pycalphad's
  binary-mapper combinatorics, but only for binaries so far.

## Known gaps standing between here and full automation

Carried over from the main README's "Current limitations" (removed there
now that this doc is the tracking location):

- **No multi-line stitching.** `MapTracer` does not yet auto-discover and
  stitch together every line/invariant a full diagram needs — it needs to
  be driven line-by-line externally today.
- **T-release convergence.** Ordinary boundary crossings release a
  composition axis only. T-release (needed to land exactly on an
  invariant node) works when the walk axis is TEMPERATURE but converges
  too slowly when seeding a genuinely new phase from a generic initial
  guess — confirmed against V-Zr's own 1586 K peritectic
  (`CalculationSessionMapTracerTest` Section C documents this). This has
  to be solved before binary auto-discovery (target 1) can be trusted
  end-to-end.
- **P-release is not implemented.** `PhaseMatrixAssembler` already
  computes `dG/dP`-based per-phase coefficients, but they aren't threaded
  through the global equilibrium matrix the way T-release now is. Needed
  for any diagram type where pressure (not composition or temperature) is
  a free axis.
- **`InvariantExitFinder` is binary-only in practice.** Written for general
  component counts but only verified against binary systems so far — the
  ternary isothermal/isopleth/pseudo-isothermal targets (2–4) will need
  this validated for 3+ component invariants (e.g. ternary eutectics,
  quasi-peritectics).
- **REST API is stale here.** `step`/`map` endpoints return 501 — not
  updated for the new tracers. Any UI-facing automated diagram feature
  needs the API layer brought current too.
- **Ternary/pseudo-isothermal auto-tracing doesn't exist yet at all** —
  `CoarseDiagramTracer` covers ternary *sampling*, but there is no
  ternary analogue of `MapTracer`'s precise boundary-following +
  invariant-node logic yet. This is new work, not a gap in an existing
  tracer.
- **The moving phase boundary is only re-solved AT a detected crossing,
  not continuously.** Discovered while building Step 2's drain loop
  (`MapDiagramTracer`) and cross-checking against a real OpenCalphad
  `map` run on Ag-Cu: OC's mapper re-solves the exact boundary
  composition via Algorithm C2 at EVERY walk step, continuously
  tracking the moving boundary, so its release-axis composition value
  changes smoothly across the whole line. `MapTracer.walkOneSegment`
  (and the `trace()` it's refactored from) instead holds the
  release-axis composition FIXED at its last value between crossings,
  only invoking the exact boundary solve when a stable-set change is
  first detected. Confirmed directly: scanning `EquilibriumSolverV2` at
  a fixed x(Cu) across a wide T range on Ag-Cu shows genuine phase-set
  changes purely as a side effect of holding composition fixed while T
  moves — the tracer is answering "what phases are stable at this
  fixed composition as T varies," not "where does the 2-phase boundary
  sit as T varies," even though the latter is what a map is supposed to
  trace. This did not block Step 2 (its test was redesigned around
  genuinely crossing-free/crossing windows found by direct solver
  scanning rather than assuming continuous tracking), but it needs
  fixing before binary full-diagram auto-discovery (target 1) can trace
  a real liquidus/solidus pair the way OC's Fig. 2(a) does — likely by
  calling something like `solveBoundary` at every ordinary walk point,
  not only at a detected crossing, mirroring OC's own per-step
  Algorithm C2 usage.
- **Two drain-loop bugs surfaced by Step 3b's testing (start-node search
  wiring) — one fixed, one open:**
  - **FIXED: an out-of-range composition value.** Draining Ag-Cu from a
    single-phase FCC_A1 start (T=1150K, x(Cu)=0.05) over T∈[1150,1230]K
    produced a node with `x(Cu) = 3.15330` — physically impossible.
    Root cause: `EquilibriumSolverV2.solveBoundaryInternal`'s Newton
    update applied NO physical bound to the released composition
    variable, unlike phase amounts (floored at `MIN_PHASE_AMOUNT`) and
    site fractions (clamped to `[1e-14, 1.0]`) a few lines above it in
    the same loop — a poorly-seeded boundary solve (confirmed
    reproducible in isolation: fixing FCC_A1 at zero and releasing
    x(Cu) at T=1205K from a seed converged at a different point,
    T=1210K) could walk the released value arbitrarily far outside
    `[0, 1]` while everything else stayed bounded, letting the
    iteration falsely "converge." Fixed by clamping the same way;
    regression test `EquilibriumSolverV2SolveBoundaryClampTest`
    confirms the exact reproduction case now correctly throws instead
    of returning a nonsensical value. Full solver test suite (including
    the Cui et al. 2016 literature baseline) still passes unchanged.
  - **STILL OPEN: nodes at the same physical point don't merge, and the
    drain loop over-produces nodes.** Re-running the same drain case
    after the fix above (no more out-of-range values) still produced
    18 nodes for a small T range, several visibly near-duplicate.
    Directly comparing one such pair confirmed this is NOT a
    `NodeRegistry`/tolerance bug: their chemical potentials genuinely
    differ by ~0.3% (μ(Cu): -79360 vs. -79621 J/mol) — four orders of
    magnitude past the solver's own `1e-10` convergence tolerance, i.e.
    each individual solve legitimately converged to a different
    self-consistent point. This points to either a physically
    shallow/ill-conditioned region near this particular LIQUID
    boundary, or a real design gap: nothing in the drain loop reuses or
    refines against an already-found node when a line re-approaches the
    same physical boundary from a different walk direction — every line
    solves its own crossing fresh from its own local seed. Needs a
    dedicated investigation (likely: either seed boundary solves from
    the nearest known node's equilibrium rather than purely local
    walk history, or accept looser node-identity tolerance and instead
    snap/merge near-matches after the fact) before `NodeRegistry`'s
    dedup can be trusted beyond Step 1's simplest same-point test.

## Suggested build order

Each step below extends the **same** `MapTracer`/`InvariantExitFinder`
engine rather than adding a parallel tracer; "new tracer" language from
earlier drafts of this doc is replaced with "new axis/condition
configuration" throughout.

1. **Fix T-release convergence** (blocks everything downstream that
   needs to close an invariant node reliably — target 1 in particular).
2. **Multi-line/node-stitching loop** (Algorithm B/C1's outer loop):
   given one starting equilibrium, keep searching unresolved exits and
   generating lines until none remain. This is the single missing piece
   for **binary full-diagram auto-discovery** (target 1) — verify against
   an OpenCalphad binary macro (e.g. Ag-Cu, matching the paper's Fig. 2a).
3. **Stability-vs-variable plot** (target 5): packaging work over
   `StepTracer` at fixed composition — lowest new-numerics risk, good
   near-term deliverable in parallel with (2).
4. **Generalize axis/condition configuration** to support two
   composition axes at fixed T (ternary isothermal, target 2) and one
   composition-ratio condition + T axis (ternary isopleth, target 3),
   reusing the stitching loop from (2) unchanged. Validate
   `InvariantExitFinder` against ternary invariants using an OpenCalphad
   ternary macro as reference.
5. **Pseudo-isothermal / higher-order sections** (target 4): same
   configuration generalization extended to fixing the extra
   components' composition in a 4+-component system — no new algorithm,
   just more fixed conditions alongside the two free axes.
6. Bring REST API `step`/`map` (and a new `diagram` endpoint) up to date
   once the engine stabilizes — no point wiring an API to a tracer still
   under active change.

### Step 5 progress (item 4 above, broken into sub-steps)

Item 4's ternary generalization is being built as a sequence of smaller,
individually-committable sub-steps (see the approved plan for full
detail; summarized here for tracking):

- **5a (done):** `Condition`/`ConditionSet` — engine-internal-only
  generalization of the n+2 condition set (`calc/diagram` package only;
  `AxisConfig` and its 19 existing callers across CLI/GUI/API/session
  untouched). Confirmed structurally to represent both the binary map
  shape (T=AXIS, P=FIXED, N=FIXED, x(B)=AXIS) and a genuine ternary
  isothermal shape (T=FIXED, P=FIXED, N=FIXED, x(B)=AXIS, x(C)=AXIS).
- **5b (done):** `MapTracer.walkOneSegment` gained a `ConditionSet`
  -driven overload sharing one walk body (`walkOneSegmentInternal`)
  with the existing `AxisConfig` overloads — verified behaviorally
  identical via `MapTracerConditionSetEquivalenceTest`. Also
  generalized `retryWithHalvedSteps` (previously hard-restricted to a
  TEMPERATURE walk axis) to work for any walk-axis type, needed because
  a ternary's walk axis is always a composition, never T. Full suite
  and both critical diagnostics (`CalculationSessionMapTracerTest`,
  `EquilibriumSolverV2BaselineTest`) confirmed unchanged.
- **5c (next):** ternary isothermal single-line tracing — at each walk
  step, one composition axis is walked and the other released into
  Algorithm C2 exactly as today's binary case (no solver change needed;
  `solveBoundary` already indexes the released component generically).
  New `AxisSelector` class picks which axis to walk per step (the
  flowchart's "fastest-varying axis" reselection). OC reference:
  `examples/macros/map14.OCM` / `steel1.TDB` (vendor into `data/`),
  cross-checked against `crfemo-1400-leftcorner.pdf`.
- **5d (planned):** wire `InvariantExitFinder` into `MapDiagramTracer`'s
  drain loop via a new `NodeGeometry` class and `PhaseDiagramEngine
  .classifyNode`'s real implementation (Eq. 8) — closes the "invariant
  nodes get zero exit lines" gap and is a prerequisite for genuine
  ternary invariants (common in Cr-Fe-Mo at 1400K). Highest regression
  risk of the sequence — first step changing existing drain-loop
  behavior, not just adding entry points.
- **5e (planned):** ternary isopleth — new `Condition.Variable
  .COMPOSITION_RATIO` (additive), new OC macro
  (`docs/oc_reference_tests/crfemo_isopleth.OCM`, no existing OC example
  covers this). Isopleth's 3-exit node case accepted as OC's hard-coded
  constant, not re-derived (neither source formalizes it).
- **5f (planned):** the 5-diagram-type test set (binary, ternary
  isothermal, ternary isopleth, property/step at fixed composition,
  binary activity/μ representation), each with 3 strictness tiers
  (topology / exact-value / multi-point-along-a-line) — the multi-point
  tier is designed specifically to catch the "boundary only re-solved
  AT a crossing" defect two sections above, which endpoint-only tests
  cannot see.

None of 5c-5f depend on fixing the still-open node-dedup bug above —
every new test asserts "the expected node/assemblage appears in the
registry," never an exact node count.

## Non-goals for this effort

- General multicomponent (4+) full-diagram auto-discovery — pseudo-
  isothermal sections (target 4) are the practical ceiling for now; a
  true N-dimensional diagram tracer is out of scope until 1–5 above are
  solid.
- RK/CVM model integration into the equilibrium pipeline — orthogonal to
  diagram tracing; tracers work off `GibbsEnergyModel` regardless of which
  model backs it, so this can proceed independently.
