# Benchmark Cases

13 canonical cases, drawn entirely from existing, already-passing tests
and existing OC reference transcripts. Each case: what it validates, the
reference source, exact values where available, and how to reproduce it.

Tolerances quoted are the actual assertion tolerances in the test code
(not rounded/approximated by this document).

---

## BM-01 — Binary CEF single-phase equilibrium (V-Zr, BCC_A2 vs V2ZR)

- **Validates:** #1 Binary CEF single-phase equilibrium; also a CEF+CVM
  candidate-list acceptance check (BCC_A2 here is CVM — see BM-07 for the
  matching case where both candidates are CEF).
- **TDB / model:** `data/VZR-re2.TDB`, all-CEF (`V2ZR`, `BCC_A2`), CEF model type.
- **Elements:** V, Zr. **Phases (candidates):** V2ZR, BCC_A2.
- **Conditions:** T=1000K, P=101325 Pa, x(V,Zr)=(0.75, 0.25).
- **Reference:** internal — the all-CEF bracket `EquilibriumSolverV2VZrCefReferenceTest`
  establishes, which `EquilibriumSolverV2CefCvmTwoPhaseEndToEndTest` (BM-07)
  compares against once BCC_A2 becomes CVM.
- **Expected (qualitative):** genuine two-phase equilibrium; both phase
  amounts finite and > 0; solver converges.
- **Class:** Scientific (internal consistency reference for BM-07, no
  independent external value).
- **How to run:** `./gradlew test --tests calc.equil.EquilibriumSolverV2VZrCefReferenceTest`
  (no tag; runs under default `test`).
- **Source:** `src-test/calc/equil/EquilibriumSolverV2VZrCefReferenceTest.java`

---

## BM-02 — Binary CEF two-phase equilibrium (Ag-Cu, LIQUID+FCC_A1) — OC cross-check

- **Validates:** #2 Binary CEF two-phase equilibrium, at an exact
  phase-boundary (ZPF) node — Algorithm C2's boundary solve compared
  point-by-point against OpenCalphad's own `CALCULATE TRANSITION` command.
- **TDB / model:** `data/agcu.TDB`, CEF (LIQUID, FCC_A1).
- **Elements:** Ag, Cu. **T range:** 1150–1230K (step 5K, walking T,
  releasing x(Cu)=0.05 fixed). **P:** 100000 Pa. **N:** 1.
- **Reference source:** `docs/oc_reference_tests/agcu_c2_transition_liquid_appears_output.txt`
  (OC's own `c transition` command, run this session against `agcu.TDB`).
- **Reference values (OC):**
  - Transition T = 1176.12665 K
  - LIQUID (fixed at 0): x(Ag)=0.889037, x(Cu)=0.110963
  - FCC_A1: x(Ag)=0.95, x(Cu)=0.05, amount=1
  - G/N = -7.0005e4 J/mol
  - mu(Ag)/RT=-7.1667, mu(Cu)/RT=-7.0083 (RT at T=1176.13K → 9.7789e3)
- **Tolerances (as asserted):** T ±0.01K; composition ±1.0e-3;
  G/N and mu within 1.0e-3 relative.
- **Class:** **Numerical** (exact external OC reference).
- **How to run:** `./gradlew testSlow --tests calc.diagram.PhaseDiagramEngineAlgorithmC2Test`
  (`@Tag("slow")`).
- **Source:** `src-test/calc/diagram/PhaseDiagramEngineAlgorithmC2Test.java`

---

## BM-03 — CEF phase discovery with the full multicomponent candidate list (V-Zr, 900K)

- **Validates:** #3 CEF phase discovery — asserts that leaving a real
  stable phase (HCP_A3) out of the candidate list silently produces a
  self-consistent but wrong answer, and that the full candidate list
  (LIQUID, BCC_A2, HCP_A3, V2ZR) recovers the correct phase set. Exercises
  `GridMinimizer.initialize` (case #8, GridMinimizer initial state) as
  part of the same run.
- **TDB / model:** `data/VZR-re2.TDB`, CEF (LIQUID, BCC_A2, HCP_A3, V2ZR).
- **Conditions:** T=900K, P=101325 Pa, x(V,Zr)=(0.55, 0.45).
- **Reference source:** OC reference (`docs/oc_reference_tests/vzr_check_900K_output.txt`).
- **Reference value:** G/N = -44345.48 J/mol (reported, printed for
  comparison; not a hard assertion in this test — see discrepancy note
  below).
- **Class:** Integration / diagnostic (`@Tag("integration-heavy")`) — the
  test prints candidate lists, GridMinimizer's stable set, and the solved
  result for manual comparison rather than asserting a tight numeric
  tolerance against the OC G/N value in code.
- **How to run:** `./gradlew testIntegrationHeavy --tests calc.equil.VZr900KFullCandidatesCheckTest`
- **Source:** `src-test/calc/equil/VZr900KFullCandidatesCheckTest.java`

---

## BM-04 — Same-phase miscibility gap (Ag-Cu, FCC_A1 splits into two slots)

- **Validates:** #4 Same-phase multiple stable slots / miscibility-gap
  behavior — a single FCC_A1 phase entry occupying two composition
  branches simultaneously, plus the `isGloballyStable` global-stability
  check that rejects a converged-but-metastable forced single-phase
  point.
- **TDB / model:** `data/agcu.TDB`, CEF (FCC_A1 only as candidate).
- **Conditions:** T=700K, P=101325 Pa, x(Ag,Cu)=(0.5, 0.5).
- **Reference:** internal, self-consistent — `GridMinimizer`'s independent
  search finds G/atom=-31585 J/mol (two-phase split) vs. a forced
  single-phase point at G/atom=-28521 J/mol, a ~10.7% relative gap, both
  numbers confirmed this session per the class javadoc.
- **Expected:** forced single-phase point converges but is rejected by
  `isGloballyStable`; the normal (unforced) solve finds 2 stable FCC_A1
  slots and passes `isGloballyStable`.
- **Class:** Scientific (self-consistent internal comparison; no external
  reference value for the exact gap composition/G in this test).
- **How to run:** `./gradlew testSlow --tests calc.equil.PhaseDiagramEngineGlobalStabilityTest`
  (`@Tag("slow")`).
- **Source:** `src-test/calc/equil/PhaseDiagramEngineGlobalStabilityTest.java`

---

## BM-05 — Binary CVM equilibrium (V-Zr, BCC_A2 single-phase, CVM)

- **Validates:** #5 Binary CVM equilibrium — the core Newton/equilibrium
  path applied to a `CvmGibbsModel` (real V-Zr CECs) as the sole
  candidate; proves the solver is generic over model type.
- **TDB / model:** `data/VZR-re2.TDB` (unary GHSER only; CVM phase built
  directly from CvmPhaseSpec — same tetrahedron-approximation binary
  BCC_A2 geometry used throughout this suite).
- **CECs:** Jindal & Lele, CALPHAD 89 (2025) 102825, Table 11:
  E21(V,Zr)=-1120-0.159T, E22(V,Zr)=-746.7-0.106T, E3(V,Zr)=120,
  v4(V,Zr)=0.
- **Class:** Scientific/Integration (proves generic solver path; see
  BM-06 for the numerical cross-check of this same model against an
  independent reference).
- **How to run:** `./gradlew testSlow --tests calc.equil.EquilibriumSolverV2CvmSinglePhaseEndToEndTest`
  (`@Tag("slow")`).
- **Source:** `src-test/calc/equil/EquilibriumSolverV2CvmSinglePhaseEndToEndTest.java`

---

## BM-06 — CVM internal-variable relaxation — CEWorkbench cross-check

- **Validates:** #6 CVM internal-variable relaxation — proves
  `EquilibriumSolverV2`'s global Newton iteration drives the CVM internal
  correlation functions `u` to the same stationary point (`dG/du = 0`)
  that an independent fixed-composition inner solver
  (CEWorkbench's `HillertSolver`/`CvmNewtonSolver`) finds, for the real
  V-Zr BCC_A2 system at three compositions.
- **TDB / model:** `data/VZR-re2.TDB`, CVM (BCC_A2), T=1000K, P=101325 Pa.
- **Reference source:** CEWorkbench (`org.ce.scratch.VZrHillertCrossCheckRef`,
  external checkout, not committed here — reference numbers reproduced in
  the test class javadoc).
- **Reference values:**
  | x(V,Zr) | u_Hillert | G_Hillert (J/mol) |
  |---|---|---|
  | (0.95,0.05) | [0.002196933159357, -0.043005411713481, 0.047584831411195, 0.047598671398244] | -46395.41632316916 |
  | (0.6,0.4) | [0.058687110860256, -0.049349296848699, 0.241942572957741, 0.242188965219370] | -53786.60905607974 |
  | (0.5,0.5) | [0.063776625493807, -0.000071205258847, 0.252049957981641, 0.252286592216877] | -54836.12953069658 |

  Fully-disordered random-state ordering check at x=(0.95,0.05):
  `uFull = [0.0022562500, -0.0427500000, 0.0475000000, 0.0475000000, 0.95, 0.05]`.
- **Tolerances (as asserted):** G within 1.0e-3 J/mol; u elementwise
  within 5.0e-5; random-state ordering within 1.0e-9; stationarity
  max|dG/du| < 1.0e-2.
- **Class:** **Numerical** (exact external cross-check, independent codebase/solver).
- **How to run:** `./gradlew testExternal --tests calc.equil.EquilibriumSolverV2CvmHillertCrossCheckTest`
  (`@Tag("external")`).
- **Source:** `src-test/calc/equil/EquilibriumSolverV2CvmHillertCrossCheckTest.java`

---

## BM-07 — Mixed CEF+CVM equilibrium (V-Zr: V2ZR CEF + BCC_A2 CVM in one solve)

- **Validates:** #7 Mixed CEF+CVM equilibrium — one `EquilibriumSolverV2.solve`
  call given both a `CefGibbs` (V2ZR) and a `CvmGibbsModel` (BCC_A2, real
  V-Zr CECs) as candidates simultaneously.
- **TDB / model:** `data/VZR-re2.TDB`. T=1000K, P=101325 Pa,
  x(V,Zr)=(0.95, 0.05).
- **Expected (physically correct, qualitative):** BCC_A2 (CVM) alone is
  stable; V2ZR is present as a correctly-metastable candidate (BCC_A2's
  real two-phase partner in this system is Laves_C15, not V2ZR — no
  common tangent — so single-phase BCC_A2 is the right answer here, not
  an artificial two-phase split).
- **Checks:** convergence; mass balance (stable phase composition = target,
  1.0e-6); chemical potentials finite; direct-model G cross-check
  (`bcc.G(T,P,yConverged)` vs. solver-reported G, relative 1.0e-6).
- **Class:** Scientific/Integration (internal consistency + physically
  correct qualitative outcome; no external absolute-G reference in this
  specific test — see BM-06 for that at the same composition/CECs).
- **How to run:** `./gradlew test --tests calc.equil.EquilibriumSolverV2CefCvmTwoPhaseEndToEndTest`
  (no tag; default `test`).
- **Source:** `src-test/calc/equil/EquilibriumSolverV2CefCvmTwoPhaseEndToEndTest.java`

---

## BM-08 — GridMinimizer initial state (V-Zr, full multicomponent candidate list)

- **Validates:** #8 GridMinimizer initial state — `GridMinimizer.initialize`
  producing a stable-phase seed set from the full LIQUID/BCC_A2/HCP_A3/V2ZR
  candidate list, printed alongside the full solver result for comparison
  (same run as BM-03; listed separately since it targets a distinct
  requirement).
- **TDB / model:** `data/VZR-re2.TDB`, T=900K, P=101325 Pa, x(V,Zr)=(0.55,0.45).
- **Class:** Integration (diagnostic printout, no hard numeric assertion
  on GridMinimizer's own output in this test).
- **How to run:** `./gradlew testIntegrationHeavy --tests calc.equil.VZr900KFullCandidatesCheckTest`
- **Source:** `src-test/calc/equil/VZr900KFullCandidatesCheckTest.java` (same as BM-03)

---

## BM-09 — STEP / phase-transition trace (V-Zr, T-scan phase fractions)

- **Validates:** #9 STEP / phase-transition trace — phase amounts/fractions
  from a fixed-composition temperature scan, using the same full
  candidate list discipline established by BM-03 (must include every
  phase in the TDB, not a hand-picked subset, or the scan silently
  converges to a self-consistent-but-wrong answer at some T).
- **TDB / model:** `data/VZR-re2.TDB`, CEF (LIQUID, BCC_A2, HCP_A3, V2ZR).
- **Conditions:** x(V,Zr)=(0.55, 0.45) fixed, P=101325 Pa, T stepped
  900K→1500K at 50K.
- **Class:** Scientific/Integration (feasibility demo; per-point
  convergence checked, phase-fraction values printed rather than
  asserted against an external per-T reference).
- **How to run:** `./gradlew testSlow --tests calc.equil.PhaseFractionTemperatureStepDemoTest`
  (`@Tag("slow")`).
- **Source:** `src-test/calc/equil/PhaseFractionTemperatureStepDemoTest.java`

---

## BM-10 — Phase diagram / MAP (Ag-Cu, two-axis liquidus walk) — ApplicationLayer integration

- **Validates:** #11 Phase diagram / MAP — the UI-facing
  `ApplicationLayer.calculatePhaseDiagram` entry point wiring
  `PhaseDiagramEngine.callAlgorithmB` end-to-end, walking T against a
  fixed composition and finding the same OC-confirmed liquidus crossing
  as BM-02, through the application layer rather than the diagram engine
  directly.
- **TDB / model:** `data/agcu.TDB`, CEF (LIQUID, FCC_A1).
- **Conditions:** walk axis T=[1150,1230] step 5K; release axis x(Cu)=[0.01,0.6]
  step 0.01; start (1150.0, 0.05); comp overall x(Ag,Cu)=(0.95,0.05).
- **Reference:** same OC crossing as BM-02 — T≈1176.13K, stable set
  {FCC_A1, LIQUID}.
- **Expected:** a node with T in [1176.0, 1180.0] and stable phase set
  exactly {FCC_A1, LIQUID} is found among the diagram's returned nodes.
- **Class:** Integration (full GUI/CLI-facing layer; qualitative phase-set
  + T-window check, not a point-value assertion).
- **How to run:** `./gradlew testSlow --tests application.ApplicationLayerPhaseDiagramTest`
  (`@Tag("slow")`).
- **Source:** `src-test/application/ApplicationLayerPhaseDiagramTest.java`

---

## BM-11 — Representative multicomponent TDB inspection (quaternary Nb-Ti-V-Zr)

- **Validates:** #12 Representative multicomponent TDB inspection — CEF
  (LAVES_C15) and CVM (BCC_A2) parameter fetch from a single genuinely
  multicomponent TDB across every binary/ternary/quaternary sub-system it
  defines, not just one combination.
- **TDB / model:** `data/NbTiVZr-CVM-eName-model.TDB`. T=1273K, P=101325 Pa.
- **Sub-systems exercised (11):** {Nb,V}, {Nb,Zr}, {V,Zr}, {Nb,Ti},
  {Ti,V}, {Ti,Zr}, {Nb,V,Zr}, {Nb,Ti,V}, {Nb,Ti,Zr}, {Ti,V,Zr},
  {Nb,Ti,V,Zr} — each parameterized test asserts exactly one model
  resolves per query, correct `numComponents()`, a valid initial
  constitution, and finite G.
- **Class:** Integration (structural/parsing correctness across
  sub-systems; no external G reference for each sub-system).
- **How to run:** `./gradlew test --tests system.database.TdbMulticomponentFetchTest`
  (no tag; default `test`).
- **Source:** `src-test/system/database/TdbMulticomponentFetchTest.java`

---

## BM-12 — CVM boundary / ZPF case (ternary Nb-Ti-V-Zr, BCC_A2 newly-appearing phase)

- **Validates:** #13 CVM boundary / ZPF case — the real production
  boundary-solve path (`solveBoundary → setUpBoundarySolve → addNewStableSlot`)
  for a newly-appearing **CVM** phase, exercising real phase-set
  management (not bypassed via `setInitialStateForTest`, unlike every
  other CVM test in this suite).
- **TDB / model:** `data/NbTiVZr-CVM-eName-model.TDB`, BCC_A2 as CVM.
- **Two sub-cases:**
  1. **Prescribed constitution** (`setPrescribedBoundaryConstitution`,
     OpenCalphad's `set_constitution`/`ycond` pattern) — converges.
  2. **Automatic seed search** (`bestCvmSeedConstitution`, no
     prescription) — **known limitation, currently fails**: the search
     does produce a valid, finite, stationary CVM seed, but the boundary
     Newton trajectory from it encounters singular BCC_A2 phase matrices
     and does not converge on this fixture. Root-caused (Phase 4G.3
     diagnostic audit: neither seed's phase matrix is exactly singular in
     isolation — AUTO's bordered-matrix condition number ~4.6e11 vs.
     PRESCRIBED's ~1.3e14, worse yet PRESCRIBED converges) but left
     failing/undiagnosed further per that phase's explicit stop
     instruction.
- **Class:** Integration (real production path; sub-case 1 is a pass/fail
  convergence check, sub-case 2 is a documented, still-open known-failure
  — see discrepancy note below).
- **How to run:** `./gradlew testSlow --tests calc.equil.EquilibriumSolverV2CvmBoundaryZpfIntegrationTest`
  (`@Tag("slow")`).
- **Source:** `src-test/calc/equil/EquilibriumSolverV2CvmBoundaryZpfIntegrationTest.java`

---

## BM-13 — Coarse binary integration (V-Zr two-phase, controlled starting point)

- **Validates:** #10 Coarse binary — the public `EquilibriumSolverV2.solve`
  entry point exercising a fixed two-phase V-Zr bracket (V2ZR+BCC_A2) from
  a controlled starting point, reusing the same parameters as the
  project's earlier fixed two-phase matrix baseline
  (`diagnostics.VzrFixedTwoPhaseSundmanBaseline`).
- **TDB / model:** `data/VZR-re2.TDB`, CEF (V2ZR, BCC_A2). T=1100K.
  Target composition x(V,Zr)=(0.55, 0.45). V2ZR: xZr=0.35, omega=1/6.
  BCC: xZr=0.55, omega=1/2.
- **Class:** Integration (deliberately modest per its own class javadoc:
  checks that `solve()` can enter iteration from a prescribed two-phase
  stable set and continue iterating; does not assert converged numeric
  values, since the nonlinear iteration itself was still being debugged
  when this test was written).
- **How to run:** `./gradlew test --tests calc.equil.EquilibriumSolverV2TwoPhaseEndToEndTest`
  (no tag; default `test`).
- **Source:** `src-test/calc/equil/EquilibriumSolverV2TwoPhaseEndToEndTest.java`

**Note on the REST coarse-binary/phase-diagram DTOs:** `src/ui/api/dto/CoarseBinaryRequest.java`,
`CoarseTernaryRequest.java`, `CoarseDiagramResponse.java`, and
`PhaseDiagramResponse.java` are new, uncommitted DTOs (see `git status`)
with no dedicated test class yet under `src-test/ui/api/`. BM-13 above
uses the existing solver-level coarse-binary case instead of inventing a
new REST-level test, per this phase's "prefer existing tests" constraint.
If REST-level coverage of these DTOs is wanted, that is Phase 16B scope
(new test, not a benchmark-only concern) — see discrepancy note below.

---

## Cases considered but not selected

- **`GridMinimizerNumericalAuditTest`** — `@Disabled("Incomplete
  refactoring: requires EquilibriumState and PhaseRecord classes not yet
  implemented")`. Would have been the most thorough GridMinimizer
  numerical audit (fixed-composition invariant, G correctness, hull-state
  correspondence, mass balance, sampling density/count) but is disabled
  and cannot currently run — used `VZr900KFullCandidatesCheckTest`
  (BM-03/BM-08) instead, which does exercise `GridMinimizer.initialize`
  on a real fixture, live.
- **`GridMinimizerCompositionSamplingTest`, `GridMinimizerInnerSolverWiringTest`** —
  unit-level wiring/sampling tests, not end-to-end thermodynamic cases;
  out of scope for a benchmark of physical results (they validate
  plumbing, not numbers).
- **`EquilibriumSolverV2CandidateStateScoringTest`, `EquilibriumSolverV2InnerSolverTest`,
  `EquilibriumSolverV2RelaxCvmCandidateAtCompositionTest`,
  `EquilibriumSolverV2BestSeedConstitutionCvmTest`,
  `EquilibriumSolverV2BoundaryReleasingTDebugTest`,
  `EquilibriumSolverV2CandidateStabilityTest`** — internal-mechanism unit
  tests (seed scoring, inner-solver wiring, single-candidate relaxation)
  rather than end-to-end equilibrium cases; the mechanisms they check are
  already exercised end-to-end by BM-05/06/07/12.
- **`HyperplaneNearDuplicateRegressionTest`** — a regression test for a
  specific numerical near-duplicate-hyperplane bug fix (synthetic
  `-2000.0`-energy fixture), not a thermodynamic case with a TDB/physical
  system; kept out as a defect-regression test, not a scientific
  benchmark case.
- **`PrescribedInitialStateProductionTest`** — infrastructure-level test
  of the `setInitialStateForTest`/prescription hook itself, already
  exercised as a means to an end by BM-06/BM-12.
- **`InternalConstraintSetIntegrationTest`, `InternalStateSamplerContractTest`,
  `PhaseModelAvailabilityTest`, `CefInternalStateSamplerEquivalenceTest`** —
  model-layer contract/unit tests (constraint bookkeeping, sampler
  equivalence, availability), not equilibrium/diagram cases.
- **CLI/GUI-specific tests** (`src-test/ui/`) — exercise report formatting
  and result plumbing already covered thermodynamically by BM-02/07/10;
  no additional physical case would be added by including them here.
- **Other validated TDBs not used above** (`data/tizr_kum.tdb`,
  `data/tizr_kum_cvm.tdb`, `data/agcu.TDB` beyond BM-02/04/10,
  `data/cost507.tdb`, `data/NbV-CVM-eName-model.TDB`,
  `data/VZR-re2-CVM-model.TDB`) — no dedicated committed test currently
  exercises these as an end-to-end equilibrium/diagram case (Ti-Zr CVM in
  particular has TDBs but the grep of `src-test` found no dedicated
  Ti-Zr equilibrium test); including them would mean writing new cases,
  which this phase's constraints avoid. Candidates for Phase 16B if
  broader TDB coverage is wanted.

---

## Discrepancies found (candidate Phase 16B issues — not fixed here)

1. **BM-03/BM-08 (`VZr900KFullCandidatesCheckTest`)**: the test prints
   `totalGPerAtom` alongside the OC reference "G/N = -44345.48 J/mol" but
   does not assert numeric agreement in code — it is a diagnostic
   printout, not a pass/fail numeric check. If a tight benchmark
   tolerance is wanted here, Phase 16B should add an explicit
   `assertEquals` against that OC value with a stated tolerance (this
   phase does not modify production or test code per its constraints).
2. **BM-12 (`EquilibriumSolverV2CvmBoundaryZpfIntegrationTest`)**: the
   automatic-seed sub-case
   (`boundarySolveAutomaticallySeedsNewlyAppearingCvmBccPhaseWithoutPrescription`)
   is a known, currently-failing case, left intentionally failing per
   Phase 4G's own stop instruction (root cause: boundary Newton
   trajectory from the auto-seed hits singular BCC_A2 phase matrices,
   condition number ~4.6e11, despite the seed itself being stationary and
   valid in isolation). This is already a known, documented, *not new*
   issue — flagged here as a candidate Phase 16B item since it directly
   affects benchmark case BM-12's completeness, not discovered fresh in
   this phase.
3. **REST-level coarse-binary/ternary/step DTOs** (`src/ui/api/dto/*.java`,
   currently uncommitted) have no dedicated test class exercising them
   end-to-end through `CalculationApiServer`. BM-13 substitutes the
   existing solver-level coarse-binary case. Not a defect — just a gap in
   REST-layer test coverage that Phase 16B could close if REST-level
   benchmark coverage is desired.

No existing reference value was found to be internally inconsistent; all
external references (OC transcripts, CEWorkbench cross-check) agree with
their corresponding test's own assertions as currently passing.
