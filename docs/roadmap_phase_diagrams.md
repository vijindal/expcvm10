# Roadmap: Automated Phase-Diagram Calculation

Top-level status against [phase_diagram_engine_flowchart.md](phase_diagram_engine_flowchart.md)
(the detailed reference for every box below). `calc.diagram.PhaseDiagramEngine`
is the single entry point, one method per box; `PhaseDiagramEngineTest`'s
`assertThrows` calls are the code-verified ground truth.

```
DATABASE
    │
    ▼
[✅] DEFINE SYSTEM + CONDITIONS + AXES + LIMITS
        └── PhaseDiagramEngine.defineSystem
    │
    ▼
[✅] VALIDATE n + 2 EQUILIBRIUM CONDITIONS
        └── PhaseDiagramEngine.validateConditionCount
    │
    ▼
[✅] GENERATE STARTING POINT(S)
        └── PhaseDiagramEngine.generateStartingPoints
            (current code: single starting point)
    │
    ▼
[✅] FOR EACH STARTING POINT
    │   (checked truly end-to-end, chaining defineSystem->
    │    validateConditionCount->generateStartingPoints->
    │    drainC1Loop/drainStepLoop in ONE call:
    │    PhaseDiagramEngineEndToEndTest.java)
    │
    ├── STEP (1 axis)
    │     └── [✅] PhaseDiagramEngine.drainStepLoop
    │               → StepDiagramTracer.drain (StepDiagramTracer.java)
    │               → StepTracer.walkOneSegment (StepTracer.java)
    │               (formatter-level OC check: StepDiagramTracer
    │                OcFormatterComparisonTest.java)
    │
    └── MAPPING (2 axes)
          └── [✅] PhaseDiagramEngine.drainC1Loop
                    → MapDiagramTracer.drain (MapDiagramTracer.java;
                      ONE loop implementation serving both an
                      AxisConfig-pair overload -- for binary T-x
                      callers -- and a ConditionSet overload reaching
                      every diagram type, incl. isopleth)
                  │
                  ▼
          [✅] C1 : DRAIN LOOP
                  │   (MapDiagramTracer.drain's while loop, calling
                  │    MapTracer.walkOneSegment, MapTracer.java)
                  ├── calculate equilibrium
                  │       └── EquilibriumSolveHelper.solveOrSentinel
                  │           (calc/equil/EquilibriumSolverV2.java)
                  │
                  ├── [✅] GLOBAL STABILITY CHECK
                  │       └── PhaseDiagramEngine.isGloballyStable
                  │           (at node creation + every 10th mid-line
                  │            point, via walkOneSegment's
                  │            globalCheckInterval)
                  │
                  ├── continue walk
                  │
                  └── stable-set change
                          │
                          ▼
                  [✅] C2 : EXACT ENDPOINT
                          │   (MapTracer.walkOneSegmentInternal)
                          ├── fix appearing/disappearing phase
                          │   at zero
                          │
                          ├── release the ACTIVE WALK AXIS
                          │   (composition, T, or P -- Fig. 6 +
                          │    OC's map_calcnode; not always
                          │    composition)
                          │       └── EquilibriumSolverV2
                          │           .solveBoundary /
                          │           .solveBoundaryReleasingT /
                          │           .solveBoundaryReleasingP
                          │
                          └── solve endpoint equilibrium
                                  │
                                  ▼
                  [✅] NODE MATCHING
                          └── NodeRegistry.findOrCreate
                              (NodeRegistry.java, Node.java, Node.matches:
                               T/P gate first, then chemical potentials,
                               tolerances ported from OC's own
                               map_newnode, smp2A.F90)
                                  │
                                  ▼
                  [✅] NODE CLASSIFICATION + EXIT GEOMETRY
                          ├── Eq. 8 classification
                          │       └── PhaseDiagramEngine.classifyNode
                          │           (ConditionSet overload: any FIXED
                          │            composition condition → isopleth-
                          │            shaped diagram, per §3.3)
                          ├── TIE_LINE_IN_PLANE → implemented
                          │       └── NodeGeometry.attachExits
                          │           (NodeGeometry.java)
                          ├── INVARIANT / Algorithm D → implemented
                          │       └── InvariantExitFinder.findExits
                          │           (InvariantExitFinder.java)
                          └── ISOPLETH_CROSSING → implemented
                                  └── NodeGeometry.attachIsoplethCrossingExits
                                      (LFIX/PHFIX exit table ported from
                                       OC's map_newnode case(3));
                                      MapDiagramTracer.drain's
                                      ConditionSet overload reaches it
                                      end-to-end
                                  │
                                  ▼
                         ADD NODE + PENDING EXITS
                                  │   (Node.addLine, Line.java)
                                  └──────→ back to C1
    │
    ▼
[✅] MERGE / DEDUP NETWORK
        └── PhaseDiagramEngine.mergeDedupNetwork
            (post-hoc filter of Line.isExcluded() lines, ported
             from OC's ocplot2/ocplot3 EXCLUDEDLINE guard;
             node-matching dedup was already inline via
             NodeRegistry.findOrCreate/Node.matches)
    │
    ▼
[✅] IDENTIFY / LABEL PHASE REGIONS
        └── PhaseDiagramEngine.identifyPhaseRegions
            (returns PhaseRegions: Line/Node stable-phase-set
             labels, plus a TieTriangle per 3-phase node per
             §2.4/§4.2's "green triangles"; formatter-verified
             against a real OC eutectic capture on agcu.TDB.
             Enclosed-area polygons for single-/two-phase
             regions stay out of scope -- neither OC nor the
             paper produces one)
    │
    ▼
[⚠️] CLASSIFY REQUESTED PLOT / VALIDATE PLOT
        └── PhaseDiagramEngine.classifyPlot
            (BINARY_T_X/PROPERTY_OR_STEP_DIAGRAM/TERNARY_ISOTHERMAL/
             TERNARY_ISOPLETH implemented -- builds the complete
             PhaseDiagramResult directly from NodeRegistry/Line,
             calc/diagram now owns PhaseDiagramResult itself per
             this project's "calc hands ui a complete result,
             never the reverse" layering rule; formatter-verified
             against the Ag-Cu liquidus, PhaseDiagramEngineEndToEndTest.
             ACTIVITY_OR_CHEMICAL_POTENTIAL/H_X_S_X_G_X/
             MULTICOMPONENT_ISOPLETH_OR_PSEUDO_ISOTHERMAL still
             throw -- need a reference-state convention, H/S
             computation, and ternary GUI rendering respectively,
             each a separate follow-up)
    │
    ▼
PHASE DIAGRAM / PROPERTY DIAGRAM
```

✅ implemented · ⚠️ partially implemented · ❌ not implemented

## Notes

- **GENERATE STARTING POINT(S)** — single point only, but **not a gap**:
  neither the paper nor OC has a working multi-start-point algorithm (OC's
  only attempt is disabled dead code, never called).
- All 5 target diagram types (binary, ternary isothermal, ternary
  isopleth, pseudo-isothermal, property/step) run through the SAME
  mapping engine above via `Condition`/`ConditionSet`
  (Condition.java, ConditionSet.java) — diagram type only changes which
  conditions are fixed vs. free, consumed by `MapTracer.walkOneSegment`'s
  `ConditionSet`-based overload, validated against real OC output
  (`MultiDiagramTypeSuiteTest.java`).
- None of the ternary work depends on the node-dedup gap being fixed
  first — every test asserts "the expected node/assemblage appears,"
  never an exact node count.

## Other known gaps (outside this flowchart)

Locating a genuine INVARIANT node (3+ phases at once, singular matrix once
a binary node's 3rd phase becomes stable -- see
`EquilibriumSolverV2.solveBoundaryReleasingT`'s own javadoc) still relies on
`retryWithHalvedSteps`'s sub-stepping, not a direct release-based solve;
no multi-line stitching (one connected component per starting point); the
moving phase boundary is only re-solved AT a detected crossing, not
continuously; REST API's
`step`/`map` endpoints return 501.

**`MapTracer.walkOneSegmentInternal`'s TEMPERATURE/PRESSURE-walk branch
holds overall composition FIXED for a whole 2+-phase line** -- confirmed
this is wrong against both the Sundman 2021 paper and a fresh OC
reference (`docs/oc_reference_tests/agcu_full_map_clean_narrow_output.txt`):
once 2+ phases are enforced stable, composition should not be an imposed
condition at all (phase rule already consumes that DOF), so it must be
recovered as Algorithm A's own output, warm-started from the previous
point -- not held at the line's starting value. Holding it fixed makes
the walk a fixed-composition-column scan that self-terminates the moment
that one column exits the multi-phase field, producing spurious nodes
OC's own map never reports. A lever-rule "predict from the previous
point's own phases" reconstruction was tried and confirmed to be a
mathematical no-op. The real fix needs a new `EquilibriumSolverV2`/
`GlobalEquilibriumMatrixAssembler` solve mode (T known/stepped by full
re-evaluation, one composition mass-balance row released so composition
falls out as an output) -- design work stalled on an unresolved
degrees-of-freedom mismatch (naively dropping one mass-balance row while
keeping every lambda/DeltaOmega unknown is under-determined by 1). Full
paper citations, the OC cross-check, and where the design work stopped
are in `docs/sundman2021_zpf_line_notes.md` -- read that before
re-investigating so the same ground doesn't get re-covered.

`MapTracer.trace()` (the `map` CLI command's pipeline, via
`CalculationSession.calculateMap`) still plots OVERALL composition on a
two-phase line, not each stable phase's own composition -- Sundman 2021
§4.1's own explicit rule for a correct binary T-x diagram (contrasted with
Fig. 10(b), the "may obtain without intention" mistake case). Fixed in
`PhaseDiagramEngine.classifyPlot`/`buildResult` (the `diagram` CLI
command's pipeline) by splitting a two-phase `Line` into one `LineSegment`
per stable phase; `trace()` was NOT fixed the same way since it builds
segments incrementally rather than from a retained per-point
`EquilibriumResult` list -- see `MapTracer`'s own class javadoc for the
full finding. Known follow-up, not yet scheduled.

## Non-goals

General multicomponent (4+) full-diagram auto-discovery; RK/CVM model
integration into the equilibrium pipeline.

## Running OC calculations (for generating reference values)

`D:\codes\opencalphad`'s binaries (`oc7C` = Linux ELF, `oc6P` = macOS
Mach-O) do not run natively on Windows -- they run via **WSL2** (see
`opencalphad/run_oc.bat`/`run_oc.ps1`, both `wsl --distribution Ubuntu --
... oc7C`). OC reads commands from stdin in raw/character-mode, so plain
piped stdin does not work reliably for multi-command macros -- use a
pseudo-terminal driver, not `oc7C < macro.OCM`.

**Working invocation** (from this project's own root, not `opencalphad`'s):
```bash
wsl.exe --distribution Ubuntu -- bash -c \
  "cd /mnt/d/codes/opencalphad/examples/macros && \
   python3 /mnt/d/codes/expcvm10/docs/oc_reference_tests/run_pty_interval.py \
     /mnt/d/codes/opencalphad/oc7C  <macro-file>" > output.txt 2>&1
```
Three driver scripts exist in `docs/oc_reference_tests/`; **use
`run_pty_interval.py`** -- the other two were tried this session and found
not to reliably drive a multi-command macro (`run_pty.py`'s prompt-string
heuristic can miss OC's actual prompt and stall; `run_pty_bulk.py`'s
whole-file paste outruns OC's raw-mode reader and comes back as unprocessed
terminal echo) -- see that file's own docstring for the full finding.

**Macro-file gotchas** (each cost real debugging time this session):
- After `r t <tdb-name>` (load database), OC prompts for elements --
  an **empty line** selects all elements; skipping it sends the NEXT
  macro line into that prompt instead, silently corrupting everything after.
- `step`/`map` themselves open a sub-prompt ("Step options? /NORMAL/:") --
  needs its own empty line (accept the default) before the following
  macro lines are treated as new top-level commands.
- **`l r <n>` is a display-mode selector** (mass-fraction vs. mole-fraction
  etc., `listresopt` in OC's source), **not an index into the step/map-
  saved equilibrium buffer** -- it always reports the CURRENT working
  equilibrium (`c e`'s result), regardless of `n`. There is no interactive
  OC command to browse individual points inside a `step`/`map` walk (only
  `plot` reads that buffer) -- to get OC reference values at specific
  points along a walk, issue separate `set cond ... / c e / l r 2` calls
  at each point of interest, not one `step` followed by multiple `l r <n>`.
- `step`'s own console trace (`New line ... / Creating a node at ... /
  Finishing line with N equilibria ...`) IS useful ground truth on its
  own -- it reports each node's exact axis value and which phase
  appeared/disappeared, independent of the `l r` limitation above.

See `docs/oc_reference_tests/agcu_step_xcu05_full_walk.txt` for a worked
example combining both (a `step` trace plus bracketing `l r` point dumps).
