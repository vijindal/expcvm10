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
[⚠️] FOR EACH STARTING POINT
    │
    ├── STEP (1 axis)
    │     └── [❌] StepTracer.trace exists (StepTracer.java),
    │               not wired into Node / NodeRegistry network
    │
    └── MAPPING (2 axes)
          └── [✅] PhaseDiagramEngine.drainC1Loop
                    → MapDiagramTracer.drain (MapDiagramTracer.java)
                  │
                  ▼
          [✅] C1 : DRAIN LOOP
                  │   (MapDiagramTracer.drain's while loop, calling
                  │    MapTracer.walkOneSegment, MapTracer.java)
                  ├── calculate equilibrium
                  │       └── EquilibriumSolveHelper.solveOrSentinel
                  │           (calc/equil/EquilibriumSolverV2.java)
                  │
                  ├── [⚠️] GLOBAL STABILITY CHECK
                  │       └── PhaseDiagramEngine.isGloballyStable
                  │           (uses calc/equil/GridMinimizer.java)
                  │           (current code: node creation only;
                  │            Sundman: node points + regular
                  │            intervals along lines)
                  │
                  ├── continue walk
                  │
                  └── stable-set change
                          │
                          ▼
                  [⚠️] C2 : EXACT ENDPOINT
                          │   (MapTracer.walkOneSegmentInternal)
                          ├── fix appearing/disappearing phase
                          │   at zero
                          │
                          ├── release current axis
                          │       └── EquilibriumSolverV2.solveBoundary
                          │
                          └── solve endpoint equilibrium
                                  │
                                  ▼
                  [⚠️] NODE MATCHING
                          └── NodeRegistry.findOrCreate
                              (NodeRegistry.java, Node.java, Node.matches)
                              (STILL OPEN: robust matching of
                               nodes reached from different
                               walk directions)
                                  │
                                  ▼
                  [⚠️] NODE CLASSIFICATION + EXIT GEOMETRY
                          ├── Eq. 8 classification
                          │       └── PhaseDiagramEngine.classifyNode
                          ├── TIE_LINE_IN_PLANE → implemented
                          │       └── NodeGeometry.attachExits
                          │           (NodeGeometry.java)
                          ├── INVARIANT / Algorithm D → implemented
                          │       └── InvariantExitFinder.findExits
                          │           (InvariantExitFinder.java)
                          └── ISOPLETH_CROSSING → not reachable yet
                                  │
                                  ▼
                         ADD NODE + PENDING EXITS
                                  │   (Node.addLine, Line.java)
                                  └──────→ back to C1
    │
    ▼
[❌] MERGE / DEDUP NETWORK
        └── PhaseDiagramEngine.mergeDedupNetwork
            (final implementation cleanup;
             C2 should already perform node reuse)
    │
    ▼
[❌] IDENTIFY / LABEL PHASE REGIONS
        └── PhaseDiagramEngine.identifyPhaseRegions
            (no computational-geometry algorithm specified
             by Sundman 2021)
    │
    ▼
[❌] CLASSIFY REQUESTED PLOT / VALIDATE PLOT
        └── PhaseDiagramEngine.classifyPlot
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

T-release convergence is slow seeding a genuinely new phase from a generic
guess; P-release is not implemented; no multi-line stitching (one
connected component per starting point); the moving phase boundary is
only re-solved AT a detected crossing, not continuously; REST API's
`step`/`map` endpoints return 501.

## Non-goals

General multicomponent (4+) full-diagram auto-discovery; RK/CVM model
integration into the equilibrium pipeline.
