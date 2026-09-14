# Roadmap: Automated Phase-Diagram Calculation

**Priority: first.** Goal — fully automated calculation of the phase-diagram
types users actually ask for, without manually specifying which boundary
lines to trace or where invariants are.

**`calc.diagram.PhaseDiagramEngine`** is the single entry point mirroring
[phase_diagram_engine_flowchart.md](phase_diagram_engine_flowchart.md)
top-to-bottom, one method per flowchart stage — implemented stages delegate
to `MapDiagramTracer`/`MapTracer`/`NodeRegistry`, unimplemented ones throw
`UnsupportedOperationException` naming the exact gap. `PhaseDiagramEngineTest`
keeps it honest: each "not yet implemented" stage has an `assertThrows` that
must be intentionally updated once that stage is built.

## Target diagram types

1. **Binary phase diagram** (T vs. composition) — auto-discover every ZPF
   line and invariant reaction across the whole composition range.
2. **Ternary isothermal section** (fixed T, composition triangle) —
   auto-trace all two- and three-phase region boundaries.
3. **Ternary isopleth** (fixed composition/ratio, T vs. one remaining
   composition) — a vertical section through the ternary prism.
4. **Pseudo-isothermal section** — (2) generalized to a quaternary+ system
   with the extra components fixed.
5. **Property/stability-vs-variable plot at fixed composition** — largely
   `StepTracer` already, packaged as a first-class output.

## One engine, not one tracer per diagram type

Sundman, Dupin & Hallstedt 2021 (`docs/2021-calphad-sundman-*.pdf`) is
explicit: OpenCalphad uses **one mapping engine**, not separate code per
diagram shape. Algorithm A (single equilibrium) is generic over any
condition set; Algorithm B branches only on axis *count* (1=step, 2=map);
Algorithms C1/C2 (line following, node/exit handling) and D (invariant
exit-finding) are identical machinery regardless of diagram type — what
differs is only **which conditions are held fixed as axes** (T+composition
for binary, two compositions for ternary isothermal, a fixed composition/
ratio+T for isopleth). Confirmed at the implementation level too: OC's
`smp2.F90` dispatches diagram type as a mode flag on one engine.

**Conclusion for this codebase:** `MapTracer` + `InvariantExitFinder` stay
one generic engine, extended with (a) the outer multi-line/node-stitching
loop, (b) generalized axis/condition setup (`Condition`/`ConditionSet`,
done — see Step 5 below), and (c) `InvariantExitFinder` validated beyond
binary systems.

## Verification reference: OpenCalphad

`D:\codes\opencalphad` is a working OpenCalphad build, used as the
step-by-step reference implementation — the same role the V–Zr TDB + Cui
et al. 2016 play for the equilibrium solver. Every OC-referenced test in
this project is captured via the pty-based driver
(`docs/oc_reference_tests/run_pty.py`) against the real `oc7C` binary,
since OC's console needs a real pseudo-terminal (plain piped stdin hangs
or garbles input) — never invented numbers.

## What already exists (`calc/diagram`)

- **`StepTracer`** — walks one axis, detects stable-phase-set changes,
  locates crossings by bisection. Numerical core of target 5.
- **`MapTracer`** — walks one axis, solves each boundary crossing exactly
  (`solveBoundary`), locates invariant nodes via `solveBoundaryReleasingT`,
  confirmed by `InvariantExitFinder` (Algorithm D). Numerical core of
  target 1; traces one line per invocation.
- **`CoarseDiagramTracer`** — grid-samples via `GridMinimizer` only (no
  Newton solve per point) for fast scatter/dot preview; wired into the
  GUI's Coarse Diagram tab.
- **`InvariantExitFinder`** (Algorithm D) — enumerates invariant exits;
  verified against pycalphad's binary-mapper combinatorics, binary-only
  so far.
- **`Node`/`Line`/`NodeRegistry`/`MapDiagramTracer`** — the C1 drain loop:
  given a starting equilibrium, repeatedly walks pending lines, classifies
  each resolved crossing (Eq. 8), attaches exits (ordinary or Algorithm D),
  until no pending lines remain.
- **`Condition`/`ConditionSet`** — engine-internal generalization of the
  n+2 condition set (`calc/diagram` only; `AxisConfig` and its CLI/GUI/
  API/session callers untouched), letting one walk primitive handle
  binary, ternary isothermal, and ternary isopleth condition shapes.
- **`PhaseDiagramEngine.isGloballyStable`** — the global stability check
  (§2.3.3), wired into the drain loop at node creation.

## Known gaps

- **No multi-line stitching.** `MapDiagramTracer` drains one connected
  component from one starting point; multiple disconnected components
  (e.g. Fe-Mo's γ-loop) need separate starting points, and — per a full
  paper + OC source check — neither source has a working algorithm for
  discovering those automatically either (OC's only attempt,
  `auto_startpoints` in `smp2A.F90`, is disabled dead code, never called).
  Not a gap relative to either source; `generateStartingPoints` reflects
  this.
- **T-release convergence.** Works when the walk axis is TEMPERATURE but
  converges too slowly seeding a genuinely new phase from a generic guess
  (confirmed against V-Zr's 1586K peritectic). Blocks trusting invariant
  discovery end-to-end.
- **P-release is not implemented.** `PhaseMatrixAssembler` computes the
  per-phase coefficients but they aren't threaded through the global
  matrix the way T-release is.
- **`InvariantExitFinder` is binary-only in practice** — written for
  general component counts, not yet validated for 3+ component invariants.
- **No post-hoc grid retest** (paper §2.3.3's alternate path, used only
  when T isn't itself a condition) — `EquilibriumSolverV2` always runs
  `GridMinimizer` unconditionally up front instead. Not a live gap since
  every caller here always supplies T.
- **REST API is stale** — `step`/`map` endpoints return 501.
- **The moving phase boundary is only re-solved AT a detected crossing,
  not continuously** — OC's mapper re-solves the exact boundary at every
  walk step; this codebase holds the release-axis composition fixed
  between crossings. Needs fixing before a real liquidus/solidus pair
  traces the way OC's Fig. 2(a) does.
- **STILL OPEN: node dedup doesn't catch every case.** Independent walks
  reaching the same physical boundary from different directions can
  converge to meaningfully different equilibria (chemical potentials
  differing well beyond solver tolerance — confirmed: ~0.3% difference,
  four orders of magnitude past the `1e-10` convergence tolerance), which
  `Node#matches` then treats as distinct nodes. Needs a dedicated
  investigation (seed boundary solves from the nearest known node, or
  merge near-matches post-hoc). None of Steps 5c onward depend on this
  being fixed — every test asserts "the expected node/assemblage
  appears," never an exact count.
- **`ISOPLETH_CROSSING` (3-exit node) unimplemented.** Paper-stated
  (§3.3: "two crossing lines... requires the creation of 3 exits"), not
  merely OC's own constant, but `NodeGeometry`/`classifyNode` can't
  produce this case yet — only a single ordinary isopleth boundary line
  is traced today, not a full isopleth node network. Isopleth sections
  also have no tie-lines in the plane, unlike binary/ternary-isothermal.
- **`MERGE/DEDUP NETWORK`, `IDENTIFY/LABEL PHASE REGIONS`,
  `CLASSIFY REQUESTED PLOT`** — all still throw `UnsupportedOperationException`
  in `PhaseDiagramEngine`; no algorithm exists to port for phase-region
  identification (paper/OC both only define the property, not a
  computational-geometry algorithm).
- **Global stability check not checked at the START node**, and not on
  ordinary mid-line points (matches OC's own behavior — its mid-line
  interval check is a separate, cheaper, off-by-default mechanism for
  un-sticking metastable constitutions, unrelated to line abandonment).

## Suggested build order

1. Fix T-release convergence (blocks reliable invariant closing).
2. Multi-line/node-stitching loop — the single missing piece for binary
   full-diagram auto-discovery (target 1).
3. Stability-vs-variable plot (target 5) — lowest new-numerics risk.
4. Generalize axis/condition configuration for ternary isothermal (2) and
   isopleth (3) — **done, Step 5 below**.
5. Pseudo-isothermal / higher-order sections (target 4).
6. Bring the REST API (`step`/`map`, a new `diagram` endpoint) current.

## Progress log

**Step 5 — ternary generalization** (all done): `Condition`/`ConditionSet`
(5a); a `ConditionSet`-driven `walkOneSegment` overload sharing one walk
body with the `AxisConfig` overloads (5b, `MapTracerConditionSetEquivalenceTest`);
ternary isothermal tracing on Al-Mg-Zn/`cost507R.TDB`, needing zero walk-
loop changes beyond 5b's plumbing (5c, `MapTracerTernaryIsothermalTest`);
`InvariantExitFinder` wired into the drain loop via `NodeGeometry` and a
real `classifyNode` (Eq. 8) (5d, `NodeGeometryTest`); ternary isopleth
tracing, also on Al-Mg-Zn (a fixed-composition isopleth turned out
structurally identical to the binary map, needing no new production code
— matches the paper's own Fig. 3(c) worked example) (5e,
`MapTracerTernaryIsoplethTest`); the 5-diagram-type test set with three
strictness tiers — topology, exact-value, and multi-point-along-a-line,
the last specifically to catch "boundary only re-solved at a crossing"
defects endpoint-only tests miss (5f, `MultiDiagramTypeSuiteTest`).

**Step 6 — global stability check** (§2.3.3, done): `isGloballyStable`
re-runs `GridMinimizer`'s independent search at a converged point and
flags it unstable if another candidate phase set is lower in G per real
atom (`EquilibriumResult.totalGPerAtom()`) by more than a 1e-4 relative
tolerance; calibrated against a genuine failure case (Ag-Cu forced to
converge as a single FCC_A1 phase instead of splitting across its real
miscibility gap) (6a, `PhaseDiagramEngineGlobalStabilityTest`). Wired
into `MapDiagramTracer.drain` at node creation — checking every node
point, not a mid-line interval, per a full OC source search showing that
is exactly OC's own real behavior for the line-abandoning check (6b,
`LineTest`, `Line#markExcluded`/`#isExcluded`).

Every step above is verified against the full JUnit suite plus the two
critical standalone diagnostics (`CalculationSessionMapTracerTest`,
`EquilibriumSolverV2BaselineTest`) confirmed unchanged, and every new OC
reference is a real, pty-driven `oc7C` capture — see individual test/class
javadoc for exact citations and values.

## Non-goals for this effort

- General multicomponent (4+) full-diagram auto-discovery — pseudo-
  isothermal sections (target 4) are the practical ceiling for now.
- RK/CVM model integration into the equilibrium pipeline — orthogonal to
  diagram tracing.
