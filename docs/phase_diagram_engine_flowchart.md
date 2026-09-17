# Unified Phase-Diagram Engine — Flowchart

Companion to [roadmap_phase_diagrams.md](roadmap_phase_diagrams.md)'s "One
engine, not one tracer per diagram type" section. This is the single
control flow — Sundman 2021 Algorithms A/B/C1/C2/D plus the missing
multi-line/node-stitching outer loop — that all target diagram types
(binary, ternary isothermal, ternary isopleth, pseudo-isothermal,
property/stability-vs-variable) run through. Diagram *type* only affects
"DEFINE SYSTEM + CONDITIONS + AXES + LIMITS" (which conditions are fixed
vs. free) and "CLASSIFY REQUESTED PLOT" (how the same stored equilibrium
data gets rendered) — everything in between is shared.

**Revision note:** this version replaces an earlier draft's standalone
"Exit" and separate "PENDING EXIT LIST" boxes after cross-checking
against OpenCalphad's actual data structures
(`src/stepmapplot/smp2.F90:83-234`, module `ocsmp`). There is no
separate exit type in OC — an exit is an **unfinished `map_line`**
already attached to a node, and the pending-exit bookkeeping is
distributed **per node** (`map_node%linehead`), not one global flat
queue. See "Data structures" below for the full correspondence.

**Scope of this flowchart — 1 or 2 axes only.** Sundman 2021's own
closing summary states: *"The algorithms presented here can be extended
to calculate ZPF lines with 3 or more axes. In such a case there will
be 2 or more phases fix with zero amount along each line."* Everything
below assumes at most 2 axes (1 = step, 2 = map), matching this
codebase's `AxisConfig`/`MapTracer` today, and every "fixed phase"
reference below is written for exactly one phase fixed at zero per
line (`map_fixph`/`linefixph` holding a single entry). This is the
paper's base case, not its full generality.

- **Current implementation target:** 1D step / 2D mapping, one phase
  fixed at zero per ZPF line — covers every target diagram type in
  [roadmap_phase_diagrams.md](roadmap_phase_diagrams.md) (binary,
  ternary isothermal, ternary isopleth, pseudo-isothermal all reduce to
  2 free axes with the rest of the composition/potential space held
  fixed as ordinary conditions, not extra mapping axes).
- **Future extension (out of scope here):** mapping with 3+ axes, where
  a ZPF "line" generalizes to a manifold requiring 2 or more phases
  fixed at zero simultaneously. OC's own comments flag this as
  unfinished territory too (`smp2.F90`'s note on `map_fixph`: *"if we
  have 3 or more axis there can be 2 or more fix phases along the
  line??"* — the `??` is in the original source). Not needed for any
  target diagram type in the roadmap; revisit only if a future
  diagram type genuinely requires 3+ simultaneous free axes.

```
DATABASE
   │
   ▼
DEFINE SYSTEM + CONDITIONS + AXES + LIMITS
   including the SET OF CANDIDATE PHASES to consider
   (§2.4 / Fig. 2c: "Not only stable phase diagrams... but also
    metastable ones... can be calculated" -- the paper's Fe-C example
    shows a graphite-stable result and, from the same database, a
    cementite-"metastable" result (Fig. 2c). The calculated
    equilibrium is stable with respect to the candidate phase set
    supplied to the calculation; different candidate-phase selections
    can therefore be used to generate stable or metastable diagrams.
    This is as far as the paper's own discussion goes in this passage
    -- it does not formally define a "stable/metastable mode" as a
    software concept, so this flowchart does not either; the paper is
    read here only as evidence that candidate-phase selection belongs
    at system-definition time, not that the engine internally
    branches on stable-vs-metastable intent)
   │
   ▼
VALIDATE n + 2 EQUILIBRIUM CONDITIONS
(§2.3.2: T, P, composition by default, or any allowed substitute --
 chemical potential/activity, S, H, V, a phase held stable, x in a
 phase, a constituent fraction, a state-variable expression. This is
 the condition-count rule for one Algorithm-A call, distinct from the
 Gibbs phase rule used later at node classification.)
   │
   ▼
GENERATE STARTING POINT(S)
   │        (usually one; extra ones needed for diagram components
   │         disconnected from the first, e.g. the Fe-Mo γ-loop case)
   ▼
┌────────────────────────────────────────────────────────────────┐
│ FOR EACH STARTING POINT                                        │
│                                                                │
│   Build initial estimate (GridMinimizer)                       │
│        │                                                       │
│        ▼                                                       │
│   Algorithm A  ->  EquilibriumState                             │
│        │                                                       │
│        ▼                                                       │
│   Initial-equilibrium grid test (§2.3.3): if the condition set     │
│   did not allow the grid minimizer up front (e.g. T not a          │
│   condition), re-run it now against the computed (T, y) and        │
│   recalculate if any gridpoint sits below this result              │
│        │                                                       │
│        ▼                                                       │
│   ┌────┴──────────────────────┐                                │
│   │                            │                                │
│  ONE AXIS (step, Algorithm B  TWO AXES (map, Algorithm B         │
│  left branch, §3.2)           right branch, §3.3)                │
│   │                            │                                │
│   ▼                            ▼                                │
│  Create / identify START      Choose ONE of the two axes as     │
│  Node here directly           the initial search axis           │
│  (this equilibrium IS         (normally a potential condition,  │
│  the node)                    per the paper's convention)        │
│   │                            │                                │
│   ▼                            ▼                                │
│  Attach 2 PENDING Lines,      Increment ONLY that axis via       │
│  one in each direction of     Algorithm A, holding the other     │
│  the single axis               axis's condition fixed, until      │
│   │                            the stable phase set changes       │
│   │                            (this is itself a small step-like  │
│   │                            walk -- NOT yet node/line          │
│   │                            bookkeeping; no node exists yet)   │
│   │                                 │                             │
│   │                                 ▼                             │
│   │                            C2 : locate exact ZPF boundary     │
│   │                            (fix the appearing/disappearing    │
│   │                            phase at zero, release the search  │
│   │                            axis) -- THIS equilibrium becomes  │
│   │                            the first/START Node, not the      │
│   │                            original initial equilibrium       │
│   │                                 │                             │
│   │                                 ▼                             │
│   │                            Attach 2 PENDING Lines in the +    │
│   │                            and - directions of the OTHER      │
│   │                            axis (the one NOT used for the     │
│   │                            initial search) -- per §3.3        │
│   │                                 │                             │
│   └─────────────┬───────────────────┘                             │
│                 ▼                                                 │
│         NODE REGISTRY (add start node, with its identity per      │
│         "Open implementation choices" below)                      │
└────────────────────────────────────────────────────────────────┘
   │
   ▼
┌────────────────────────────────────────────────────────────────┐
│ C1 : DRAIN LOOP  --  while NODE REGISTRY has a node with a       │
│                      PENDING line, pick one                     │
│                                                                │
│   Pick one PENDING Line L from some Node N in the registry       │
│        │                                                       │
│        ▼                                                       │
│   Small direction-check step (confirm sign of increment)        │
│        │                                                       │
│        ▼                                                       │
│   ┌─────────────── walk loop, appending EquilibriumState ──┐    │
│   │  Increment active axis (pick fastest-varying axis        │    │
│   │  if >1 axis, matching OC's per-step axis reselection)     │    │
│   │        │                                                 │    │
│   │        ▼                                                 │    │
│   │  Algorithm A  (phase(s) in L's Exit fixed at zero, per    │    │
│   │  `map_fixph`; other axis condition released if 2+ axes)   │    │
│   │        │                                                 │    │
│   │   ┌────┴─────┐                                           │    │
│   │  fail       success                                      │    │
│   │   │            │                                          │    │
│   │  shrink Δ       ▼                                          │    │
│   │  (<=3x,        GLOBAL STABILITY CHECK (§2.3.3, general       │    │
│   │   else         form): is there another phase set             │    │
│   │   TERMINATE L) representing a MORE STABLE equilibrium at      │    │
│   │   │            this point? (not gridpoint-specific -- any     │    │
│   │   │            cheaper phase set counts)                      │    │
│   │   │                   │                                    │    │
│   │   │             ┌─────┴─────┐                              │    │
│   │   │            YES          NO                              │    │
│   │   │             │             │                             │    │
│   │   │        ABANDON &     append EquilibriumState to          │    │
│   │   │        SUPPRESS L    L's buffer                          │    │
│   │   │        (line          │                                  │    │
│   │   │        dropped        ┌─────┴─────┐                      │    │
│   │   │        entirely,  axis limit  stable-set changed?        │    │
│   │   │        not just       │             │                    │    │
│   │   │        terminated) TERMINATE L  (loop back: increment)   │    │
│   │   │             │      (no new node)                         │    │
│   │   └──────────────┘                                          │    │
│   └───────────────────────────────────────────────────────────┘    │
│        │ (stable-set changed: exit the walk loop)                  │
│        ▼                                                       │
│   C2 : Locate exact endpoint                                    │
│   (fix appearing/disappearing phase at zero, release axis,       │
│    solve once more for the precise boundary point)               │
│        │                                                       │
│        ▼                                                       │
│   Node MATCHING against NODE REGISTRY                            │
│   (§3.1: "already found" test -- criterion + tolerance are an     │
│    open implementation choice, see below)                        │
│        │                                                       │
│   ┌────┴────────────┐                                          │
│  found            not found                                    │
│   │                  │                                          │
│  mark L's Exit       Create new Node                            │
│  DONE, link L                                                   │
│  to that Node             │                                     │
│                      GIBBS PHASE RULE (Eq. 8):                  │
│                      f = n+2-p-c                                │
│                      (f = degrees of freedom, n = components,    │
│                       p = stable phases, c = potential           │
│                       conditions not used as axes -- this is a   │
│                       node-classification check, separate from   │
│                       the n+2-condition setup rule above)         │
│                            │                                     │
│                    ┌───────┴────────┐                            │
│                 f > 0            f = 0 (invariant)                │
│              (non-invariant:         │                            │
│               exit count comes       ▼                            │
│               from NODE GEOMETRY,  Algorithm D:                   │
│               NOT from f -- the    enumerate phase pairs (βi,βj), │
│               paper gives these    solve Eq. 9 for exit amounts,  │
│               as separate prose    keep only exits where ALL       │
│               cases, §3.3/Fig.6,   remaining phase amounts >= 0    │
│               not a formula)       -> retain as PENDING lines.     │
│                                     MAXIMUM POSSIBLE exits = 2p     │
│                                     (§3.3: "the maximum number of  │
│                                     exits is thus equal to 2p" --  │
│                                     p = stable phases at the       │
│                                     invariant; Algorithm D finds    │
│                                     the subset actually valid in    │
│                                     the calculated section, which   │
│                                     is usually far fewer than 2p.   │
│                                     NOTE: OC's own source comment   │
│                                     at smp2A.F90 ~4789-4797 uses    │
│                                     "2*haha-1" for this case,       │
│                                     which does NOT match the        │
│                                     paper's 2p -- an unresolved      │
│                                     discrepancy between the two      │
│                                     sources; follow the PAPER's 2p   │
│                                     as the authoritative bound       │
│                                     unless/until this is checked     │
│                                     against a running OC example)    │
│               not a formula)            │                          │
│                    │                    │                          │
│         ┌──────────┴──────────┐         │                          │
│         ▼                      ▼         │                          │
│   TIE-LINE-IN-PLANE      ISO-PLETH-STYLE │                          │
│   crossing (§3.3,        CROSSING (§3.3, │                          │
│   "the rather simple      "most node      │                          │
│   cases where              points          │                          │
│   tie-lines are in         correspond to    │                          │
│   the calculated           two crossing      │                          │
│   plane"): 2 exits         lines," Fig.13b): │                          │
│                             3 exits           │                          │
│         └──────────┬───────────┘         │                          │
│                    ▼                    │                          │
│   (also, STEP nodes -- not shown as their own f-branch since step  │
│   has no ZPF-fixed axis at all -- split into TWO distinct cases    │
│   per §3.2, not one exit count:                                    │
│     - the STEP INITIAL node (the very first equilibrium, before    │
│       any stable-set change) gets 2 exits, one in EACH direction   │
│       of the single axis -- same as the "Attach 2 PENDING Lines"   │
│       box for the step branch near the top of this flowchart;      │
│     - every step node CREATED BY C2 afterward (i.e. every node     │
│       from a stable-set change mid-walk) gets only 1 exit: a       │
│       continuation in the SAME direction the line was already      │
│       going, per §3.2: "a new node will be created with one exit   │
│       to continue calculating along the axis in the same           │
│       direction with the new set of stable phases."                │
│   See OC's smp2A.F90 ~4763-4806 for how step/tieline-in-plane/     │
│   isopleth/invariant map onto newnode%lines.)                      │
│                    │                  │                           │
│                    └────────┬─────────┘                           │
│                             ▼                                     │
│                   add new Node (with its PENDING lines)            │
│                   to NODE REGISTRY                                 │
│        │                                                       │
│        └──────────────────────► back to top of C1 drain loop      │
│                                  (registry scan finds next PENDING) │
└────────────────────────────────────────────────────────────────┘
   │  (loop exits when NODE REGISTRY has no PENDING lines left)
   ▼
MERGE / DEDUP NETWORK
   - nodes reached from two directions collapse to one via node
     MATCHING (§3.1 "already found" test, see "Open implementation
     choices" above)
   - remove/suppress lines rejected by the GLOBAL STABILITY CHECK
     (§2.3.3 -- the only line-suppression mechanism the paper states)
   - remove duplicate representations where required
     (IMPLEMENTATION CLEANUP, not sourced from the paper -- e.g. a
      degenerate or duplicate-composition line arising from how this
      codebase's node/line bookkeeping is built, not a mechanism
      Sundman 2021 describes)
   │
   ▼
IDENTIFY / LABEL PHASE REGIONS using ZPF lines and equilibrium results
   (§2.4: "ZPF lines separate regions in a phase diagram where a phase
    is present from regions where it is not present; a phase diagram
    with at least one composition variable consists entirely of ZPF
    lines." The paper states this defining property but gives no
    computational-geometry algorithm -- no polygon/region construction
    step exists in either the paper or OC's actual source (checked:
    `smp2A.F90`/`smp2B.F90` mention "region" only in comments
    describing where a line sits, never as a data structure or
    algorithm). Turning the Node/Line graph into explicit region
    polygons for rendering is IMPLEMENTATION WORK this codebase would
    have to design, not something to port from either source.)
   │
   ▼
CLASSIFY REQUESTED PLOT
   (may OVERLAY results from separate calculation runs with different
    candidate-phase sets -- Fig. 2c overlays a graphite-stable run and
    a cementite-"metastable" run from the same database on one Fe-C
    diagram. Each run is a full, independent pass through this whole
    engine with its own PHASE SELECTION; the overlay itself is a
    plotting-stage step, not a new engine mode.)
   │
   ├── Conventional binary T-x
   ├── Activity / μ representation
   ├── H-x / S-x / G-x
   ├── Ternary isothermal
   ├── Ternary iso-pleth
   ├── Multicomponent iso-pleth / pseudo-isothermal
   └── Property / step diagram (single axis, no ZPF fixing)
   │
   ▼
VALIDATE PLOT (axes/conditions selected make a well-posed diagram)
   │
   ▼
PHASE DIAGRAM / PROPERTY DIAGRAM
```

## Open implementation choices (not settled by the paper or by OC's structs)

Some flowchart steps above are marked "open implementation choice." These
are places where Sundman 2021 and/or OC's source establish *what* has to
happen, but not *exactly how* — this codebase has to make an explicit
decision rather than translate a formula.

- **Node matching / identity.** §3.1 states in prose that a node "is
  identified by the set of stable phases and the chemical potentials of
  the components... one must be able to identify the node as one may
  find the same node following different lines," and Fig. 6 (Algorithm
  C2) calls this an "already found" check without giving an algorithm.
  OC's `map_node` struct stores both `stable_phases` and `chempots`,
  which supports using them as the identity key — but this is OC's
  implementation choice, not a formula from the paper, and neither
  source specifies:
  - a numeric tolerance for "the same" chemical potentials (equilibria
    approaching the same node from different lines/directions will not
    land on bit-identical values);
  - whether phase compositions/constitutions should also factor into
    matching (two nodes could in principle share a stable phase set and
    very close potentials while being physically distinct in a
    pathological case);
  - what to do on a near-miss (treat as same node vs. treat as distinct
    and risk unbounded node proliferation).
  **Decision needed before implementation**: pick a concrete matching
  rule (e.g. stable phase set exact match + chemical potentials within
  a relative tolerance tied to the solver's own convergence tolerance)
  and document it as this codebase's choice, not as something derived
  from Sundman 2021.
- **Exit count at a "normal" (non-invariant) node.** OC's code
  (`smp2A.F90` ~4788-4806) hard-codes small integers per case (step=1,
  tie-line-in-plane=2, generic=3) with a comment flagging the generic
  case as not fully principled ("Unknown type of node create exit
  lines"). Algorithm D (Eq. 8/9) only formally derives the exit count
  for the *invariant* (`f=0`) case. **Correction (confirmed via a full
  re-read of the paper this session, prompted by isopleth design work):
  the 3-exit ISOPLETH-CROSSING case specifically is NOT merely OC's own
  unprincipled generic-case constant** — §3.3 states it directly in
  prose, via geometric reasoning about two lines crossing rather than a
  numbered formula: "In iso-pleths, with extensive or normalized
  properties as conditions, in addition to T most node points correspond
  to two crossing lines... Such a node requires the creation of 3 exits
  when they are found." So this specific case (tie-line-in-plane=2,
  isopleth-crossing=3) is paper-derived, just not paper-*formalized* as
  an equation the way Eq. 8/9 formalize the invariant case — treat OC's
  matching constants here as confirmation of the paper's own stated
  geometry, not as an independent, unprincipled implementation choice.
  Any OTHER non-invariant node topology beyond these two named cases
  remains reasoned informally in the paper's prose and should still be
  treated as "match OC's constant, not a formula to re-derive."

## Data structures (grounded in OC's `ocsmp` module, `smp2.F90:83-234`)

OpenCalphad's four core types map directly onto what this engine needs.
Naming below is suggested for this codebase, not copied verbatim from
Fortran field names.

### `EquilibriumState`
One converged (or attempted) equilibrium — this already exists as the
result of `EquilibriumSolverV2` / `EquilibriumSolveHelper`. Corresponds
to one `gtp_equilibrium_data` record in OC. Stored **by index into a
shared, appendable buffer** (OC's `map_ceqresults.savedceq`), not
embedded per-line — a `Line` holds `firstIndex`/`lastIndex`/`count` into
that buffer, not a `List<EquilibriumState>` of its own. This matters at
scale (a mapped diagram can hold thousands of equilibria) and also
because OC explicitly designs this buffer to be checkpoint/resumable
("saved on a random file").

### `Node`
Created whenever the stable phase set changes. Fields needed, per
`map_node`:
- **Identity**: stable phase set (phase + composition-set index pairs,
  OC's `gtp_phasetuple` list) **and** chemical potentials — both are
  required to recognize "is this the same node reached by a different
  line," not composition/T alone (two lines can arrive at the same
  phase-equilibrium point from different axis paths).
- One converged `EquilibriumState` at the node itself (`nodeceq`).
- Which phase is held fixed at zero to arrive at this node (`nodefix`)
  — relevant for classification, not identity.
- The node's mode/kind: step / tie-line-in-plane / isotherm / isopleth
  (OC's `type_of_node`) — this is what makes "one engine" concrete: the
  node carries a mode tag, not a different class hierarchy.
- A **list of `Line`s attached to it** (OC's `linehead` array) — this
  *is* the per-node pending/done exit bookkeeping; no separate `Exit`
  type exists in OC.
- Membership in a **global doubly-linked list of all nodes**
  (`first`/`next`/`previous`) — this is what Algorithm C1 scans to find
  the next pending line to follow, and what node-dedup searches.

### `Line`
A line is created **attached to a node**, in one of two states:
- **Pending** (OC calls this state simply "empty," i.e.
  `number_of_equilibria == 0`): only the axis to vary, direction, and
  which phase(s) are fixed at zero are known (`axandir`, `linefixph` /
  OC's separate `map_fixph`). This *is* what the earlier flowchart draft
  called an "Exit" — it is not a distinct type, just a `Line` that
  hasn't been walked yet.
- **Walked**: has a range of `EquilibriumState` indices
  (`first`/`last`/`number_of_equilibria`), a `done`/`more` status, and
  pointers to its `start` and `end` nodes (`end` is null/unset until the
  line terminates at a node).

A `Line`'s fixed-phase set can hold more than one phase when 3+ axes are
used (documented in OC's comments as a forward-looking case, not needed
for our target diagram types with 1-2 axes).

### `AxisConfig` (already exists in this codebase)
Matches OC's `map_axis`: which state-variable(s) the axis condition is
built from, min/max/increment, and `lastaxval` for resuming/reselecting
the fastest-varying axis during a multi-axis walk. No structural change
needed here beyond confirming it can represent a composition-ratio
condition for isopleths (a linear combination, not just one variable) —
`map_axis.nterm`/`indices`/`coeffs` already generalize to that in OC and
should be checked against `AxisConfig`.

## Mapping to this codebase

| Concept | Sundman 2021 | OC type (`ocsmp`/`matsmin`) | Current code |
|---|---|---|---|
| Single equilibrium | Algorithm A | `gtp_equilibrium_data` | `EquilibriumSolverV2` |
| Converged/attempted state | — | `gtp_equilibrium_data`, buffered in `map_ceqresults` | needs a `EquilibriumState` buffer type (new) |
| Node | node points (§3.1) | `map_node` | **missing** — `MapTracer` has no persistent node concept across calls |
| Line / exit | lines, exits (§3.1, Fig. 5/6) | `map_line` (pending vs. walked state of the same type) | `MapTracer`'s single-line walk; no `Line`/pending-state abstraction |
| Fixed-phase-at-zero set for a line | §3.3 | `map_fixph` | ad hoc inside `MapTracer`/`solveBoundary` calls |
| C1 (follow line, pick next exit) | Fig. 5 | loop in `smp2A.F90` over `map_node` list | **missing outer loop** — this is the core gap |
| C2 (stable-set change -> node + exits) | Fig. 6 | `smp2A.F90` ~4543-4900 (node creation + exit-count-by-case) | `MapTracer`'s inline crossing handling; not a reusable node/exit step |
| Algorithm D (invariant exits) | Fig. 7 | same block, `haha`/`inveq` branch, `2*haha-1` exits | `InvariantExitPairFinder` |
| Node registry / dedup | "list searched by algorithms C1 and C2" | `map_node%first/next/previous` | **missing** |
| `GENERATE STARTING POINTS` | §3, Fe-Mo γ-loop note — explicitly declines to give an algorithm ("such issues will not be considered in the algorithms presented here") | one disabled, never-called prototype (`auto_startpoints`, `smp2A.F90:9342-9498`, gated by status bit `GSNOAUTOSP`; author's own comment there: "I have not really implemented several startpoint") | single start point only — **matches both sources' real behavior, not a gap relative to either** (confirmed by full source search this session; see `PhaseDiagramEngine#generateStartingPoints`'s javadoc for the complete finding) |
| `CLASSIFY REQUESTED PLOT` | §4.1-4.4 | plotting layer, reads `type_of_node` + saved equilibria | **missing** |

The concrete build target, restated in these terms: add `EquilibriumState`
buffer, `Node`, and `Line` (with a pending/walked state, not a separate
`Exit` class) to `calc/diagram`, give `Node`s a global registry with
identity-based dedup, and wrap today's single-line `MapTracer` walk plus
`InvariantExitPairFinder` inside the C1 drain loop above. `GENERATE STARTING
POINTS` (for disconnected diagram components, e.g. Fe-Mo's γ-loop) is
NOT a comparable gap to track alongside these — neither the paper nor
OC has working, validated logic here to converge toward; any future
multi-start-point search would be genuinely new implementation work,
not a missing port.
