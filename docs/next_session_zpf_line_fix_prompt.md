We're continuing work on expcvm10, a Java thermodynamic phase-diagram
engine modeled on the Sundman 2021 Calphad 75 paper ("Algorithms useful
for calculating multi-component equilibria, phase diagrams and other
kinds of diagrams"), cross-validated against OpenCalphad (OC).

**Read first, in this order:**
1. `README.md` and `docs/roadmap_phase_diagrams.md` (project structure,
   current state, the "Other known gaps" section).
2. `docs/sundman2021_zpf_line_notes.md` -- the full running log of this
   investigation, now covering Algorithm A (Fig. 1), the step-vs-map
   split, ZPF-line interior conditions, composition sets, axis
   switching, and a completed solver-vs-paper audit. **Read ALL of it,
   in order, including Sections 1, 9, and 10 (the most recent work) --
   do not re-derive any of it from the paper again, it is already
   answered there with page/section/quote citations.** Section
   numbering: 1=Algorithm A, 2=step vs map split, 3=fix-phase-at-zero is
   node-transient, 4=ZPF line interior conditions, 5=composition sets/
   miscibility gaps, 6=axis-switching mid-line, 7=OC cross-checks done,
   8=where the T/composition-release design work stalled, 9=the
   `step or map?` early-exit plan (IMPLEMENTED last session, solver-only,
   see below), 10=full Algorithm A vs. `EquilibriumSolverV2` audit
   (closed out, no serious departures found beyond the Section 9 gap,
   which is now fixed).
3. `docs/2021-calphad-sundman-Algorithms useful for calculating
   multi-component equilibria, phase diagrams and other kinds of
   diagrams.pdf` -- the source paper, for anything the notes file
   doesn't cover in enough depth. **Figures (Fig. 1, 4, 5, 6, ...) are
   images, not extractable text** -- `pdftotext` will not show them.
   Render the needed page directly instead: PyMuPDF is available
   (`python3 -c "import fitz; ..."`, confirmed working last session --
   `pdftoppm`/poppler is NOT installed, do not rely on the Read tool's
   built-in PDF page rendering, it also depends on pdftoppm and will
   fail). Fig. 4 (Algorithm B) is on **page 5** of the PDF (0-indexed
   page 4) alongside Fig. 5 (Algorithm C1) -- both were already rendered
   and read last session in the same screenshot; re-render page 5 at a
   high zoom matrix (e.g. `fitz.Matrix(3,3)` or higher) and read it
   directly with the Read tool rather than guessing from memory.

**Local codebases available for cross-reference (read-only, do not
modify):**
- OpenCalphad (Fortran): `D:\codes\opencalphad` -- the step/map engine
  is `src/stepmapplot/smp2A.F90` (subroutines `map_doallines`,
  `map_calcnode`, `map_step`/`map_step_old`/`map_step2`); phase/
  composition-set bookkeeping is in `src/models/gtp3Y.F90` and
  `gtp3_dd2.F90` (`CSAUTO` flag, `enter_composition_set`). Runs only via
  WSL2 -- see `docs/roadmap_phase_diagrams.md`'s "Running OC
  calculations" section for the exact invocation
  (`run_pty_interval.py`, not the other two driver scripts).
- pycalphad (Python): `D:\codes\pycalphad\pycalphad` -- has its own
  `mapping/` module implementing ZPF-line stepping and phase-diagram
  tracing, independent of both OC and this project. Worth reading as a
  THIRD independent implementation of the same paper-derived algorithms.
- This project: `D:\codes\expcvm10`.

**What was done in the last two sessions (do not redo):**
- Full paper-vs-OC-vs-code investigation of why `MapTracer`'s T/P-walk
  branch diverges from real OC `map` output (composition wrongly held
  fixed on a 2+-phase line's interior) -- root cause confirmed, fix
  DESIGN stalled on a degrees-of-freedom mismatch, NOT yet implemented
  in code (Section 8 of the notes -- still open, re-derive DOF from
  first principles before writing matrix code if picked back up).
- Algorithm A (Fig. 1) fully transcribed and cross-checked line-by-line
  against `EquilibriumSolverV2.solve()`. Found and closed the gap: this
  codebase's solver had NO analogue of Fig. 1's `step or map?` early
  exit (Algorithm A bailing out to C1 mid-Newton, BEFORE reconverging,
  the instant `gamma^phi>0 or N^alpha<0` is detected) -- it always
  resolved a stable-set change internally and kept iterating to its own
  converged/failed outcome, regardless of caller.
- **IMPLEMENTED (solver-only, opt-in, no tracer wiring)**: `
  EquilibriumResult` gained `StableSetChange` (phase name + `APPEARING`/
  `DISAPPEARING`) and a `getStableSetChange()` accessor.
  `EquilibriumSolverV2.solve()` gained a new overload `solve(T, P,
  compOverAll, candidates, boolean stopOnStableSetChange)` -- when
  `true`, returns immediately (converged=false, `getStableSetChange()`
  populated) the instant `updateStablePhaseSet()` changes the stable set,
  skipping the convergence check entirely, matching Fig. 1's early exit.
  `stopOnStableSetChange=false` (the default, used by the original 4-arg
  `solve()` overload) is byte-for-byte the OLD behavior -- confirmed via
  full suite re-run, 104 tests, 0 failures/errors/skipped. New test:
  `src-test/calc/equil/EquilibriumSolverV2StopOnStableSetChangeTest.java`.
  **`MapTracer`/`StepTracer` are completely untouched** -- they still use
  only the old after-the-fact `findChangedPhase` comparison. Wiring a
  tracer to actually USE the new exit was explicitly deferred (changes
  WHEN a crossing is detected relative to the OC-validated walks, needs
  its own re-validation against `docs/oc_reference_tests/`) -- out of
  scope for this thread so far, may or may not be worth doing later.
- Full solver-vs-Fig.-1 audit completed (notes Section 10): no other
  structural departures found. One candidate finding (the paper's
  "grid minimizer used as post-convergence test when T is not a
  condition" passage, Section 2.3.3) was raised, checked against
  `solveBoundaryReleasingT`/`solveBoundaryReleasingP`'s actual call
  graph, and closed as a non-issue -- those are always seeded from a
  prior ordinary `solve()` call made at a REAL fixed T (so the grid
  minimizer already ran then), never from a T-less cold start. No
  action needed there.

**This session's task: review and test Algorithm B (Fig. 4, Section
3.1-3.3 discussion) against this codebase.**

Fig. 4 (rendered and read last session): "Make a diagram" -> "set
conditions" -> **A** -> forks into "set 1 axis" (left branch, STEP) vs.
"set 2 axes" (right branch, MAP):
- Left (step): "set 1 axis" -> "+node, 2 exits" -> **C1** -> "set plot
  axes and plot" -> end.
- Right (map): "set 2 axes" -> "increment axis" -> **A** -> loop while
  "no phase change" -> once "phases changed" -> "+node, 2 exits" ->
  **C1** -> "set plot axes and plot" -> end.

Do the SAME rigor as the Algorithm A audit: (1) re-read the paper's own
Section 3.1-3.3 text description of Algorithm B precisely (page
4-5, `pdftotext -layout` extraction already captured most of this in
earlier sessions -- re-extract if needed, cited quotes are in notes
Sections 2/6), (2) identify the corresponding code path(s) in this
project -- likely `PhaseDiagramEngine`, `StepDiagramTracer`,
`MapDiagramTracer`, `CalculationSession.calculateStep`/`calculateMap`/
`calculatePhaseDiagram` -- read `docs/phase_diagram_engine_flowchart.md`
first since it's this project's own existing mapping of engine stages
to Sundman's algorithm names, (3) read OC's own Algorithm B equivalent
(`smp2A.F90`'s top-level `map_doallines`/step entry points) and
pycalphad's `mapping/` module's top-level diagram-driving entry point as
secondary cross-references, (4) compare line-by-line / box-by-box like
the Algorithm A audit did, (5) write findings into
`docs/sundman2021_zpf_line_notes.md` as a new numbered section (start at
Section 11), using the same style as Sections 1 and 10 -- lean on
directly-extracted paper quotes over paraphrase, keep it minimal,
pseudocode or a compact box transcription over an ASCII art diagram if a
flowchart is needed. Then design a concrete test plan for whatever is
found (existing tests to check first via
`PhaseDiagramEngineEndToEndTest`/`MultiDiagramTypeSuiteTest`/etc. before
writing new ones).

**Working agreement, same as previous sessions:** confirm scope with the
user before running large calculations or making solver-level code
changes. Notes-only work (reading, transcribing, auditing) does not need
pre-confirmation; implementation does.
