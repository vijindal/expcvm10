# Fix GUI eager/redundant TDB loading; wire the whole GUI lifecycle to `CalculationSession`

## Context

Launching the GUI (`./gradlew run --args="--gui"`) parses TDB files
**before the window is even shown**, and does so **redundantly** — the
startup log shows "tdb method is called with: ..." firing far more times
than there are databases to load.

Investigation (`MainFrame`/`GuiApp`/`DatabaseExtractionPanel`/
`ModelInspectionService`/`TdbParser`, exact file:line references below)
found the concrete cause:

- `GuiApp.launch()` builds `MainFrame` and calls `setVisible(true)`
  (`GuiApp.java:43-46`). `MainFrame`'s constructor calls `buildRoot()`
  (`MainFrame.java:83,92`) **before** that `setVisible(true)` runs.
- `buildRoot()` constructs **5 separate sidebar panels**
  (`SinglePointSidebarPanel`, `PropertyCalcConfigPanel`×2 [STEP/MAP],
  `PhaseDiagramConfigPanel`, `ModelInspectorSidebarPanel` —
  `MainFrame.java:94,98,102,106,109`), and **each one independently
  constructs its own `DatabaseExtractionPanel`** and calls
  `setDefaults("data/tizr_kum_cvm.tdb", ...)` synchronously in its own
  constructor.
- `DatabaseExtractionPanel.setDefaults(...)` (`DatabaseExtractionPanel.java:335-344`)
  calls `onTdbSelected()` (→ `controller.inspectModel(...)`,
  `DatabaseExtractionPanel.java:209`) and, for the 4 panels that also pass
  default elements, `onAddElements()` (→ `controller.getPhasesForElements(...)`,
  `DatabaseExtractionPanel.java:314`) — **two TDB-touching calls per panel**.
- `TdbParser.load(...)` (`TdbParser.java:34-37`) does **no caching**:
  `this.database = new tdb(filePath);` unconditionally, every call —
  even though `ModelInspectionService` reuses one `TdbParser` instance
  across calls (`ModelInspectionService.java:24,58,95,110`), that instance
  still re-parses the file from scratch every single time it's asked.

Net effect: **up to 9 full re-parses of the same default TDB file**
before the user has clicked anything, entirely on the Swing EDT, blocking
the window from appearing.

**Root architectural point:** this is a duplicated-database-load problem —
exactly the class of problem `ThermodynamicSystem`/`CalculationSession`
were built to solve at the calculation layer (see
`docs/plan-3layer-core-dataflow.md`). But this eager GUI-startup path is
**completely separate** from `CalculationSession` — confirmed by direct
trace: `DatabaseExtractionPanel` → `MainController.inspectModel`/
`getPhasesForElements` → `ModelInspectionService` → raw `TdbParser`,
never touching `calculationSession` or `ThermodynamicSystem` at all.
`CalculationSession` is currently *only* invoked from
`MainController.runSinglePoint(...)`, i.e. only when the user clicks "Run
Calculation" — the GUI's model-*browsing*/*inspection* lifecycle (TDB
dropdown, element badges, phase hints — all 5 sidebar panels) has no
shared session state whatsoever.

## Two distinct problems, two distinct fixes — keep them separate

Fixing only the caching bug would stop the redundant *parsing* but leave
the GUI's browsing state (which TDB/elements/phases the user is looking
at) and its calculation state (`CalculationSession`) as two unrelated,
un-unified things — the same disconnect that let this bug exist
undetected. Fixing only the session-wiring without the cache would still
re-parse on every panel construction, since `CalculationSession.setModel`
is never called during `buildRoot()` — it only fires on "Run Calculation."
Both are needed; each is small on its own.

### Fix 1 — `TdbParser.load(...)` caches by file path (stops the redundant parsing, minimal, safe)

```java
// system/database/TdbParser.java
private String loadedFilePath;

@Override
public void load(String filePath) throws IOException {
    if (filePath.equals(loadedFilePath)) {
        return;   // already loaded -- no-op, matches DatabasePort's implicit contract
    }
    LOG.fine("Loading TDB file: " + filePath);
    this.database = new tdb(filePath);
    this.loadedFilePath = filePath;
    LOG.fine("TDB file loaded successfully.");
}
```

This alone collapses the ~9 redundant parses down to 1 per distinct file
path, for every caller of `TdbParser` (GUI, CLI, `ThermodynamicSystem`,
`CalculationSession` — all of them), with no behavior change for any
caller that already expects "load, then use." Low risk: `TdbParser` is
already a thin adapter with one field (`database`); adding a second field
to track what's loaded is the minimal, targeted fix, not a redesign.

**This is the one line-item that must land regardless of the rest of this
plan** — it fixes the actual reported symptom (redundant parsing) by
itself, cheaply, and every other consumer of `TdbParser` benefits
immediately.

### Fix 2 — Give the GUI one shared `CalculationSession`-backed model state, not five independent `DatabaseExtractionPanel`s guessing at defaults

This is the deeper fix: unify "what TDB/elements/phases is the user
currently working with" into one place the whole `MainFrame` shares,
instead of 5 panels each independently tracking their own
`DatabaseSelection` and triggering their own loads. Concretely:

1. **`MainController` already holds one `CalculationSession` for its
   lifetime** (`MainController.java:37`, added in the prior GUI-wiring
   pass) — reuse it as the single source of truth for "what model is
   loaded," instead of introducing a second, parallel piece of session
   state.
2. **Defer all default-population to *after* the window is shown.**
   `MainFrame`'s eager `setDefaults(...)` calls exist only to pre-fill
   dropdowns with a sensible starting point — that is a nice-to-have, not
   something that should block first paint. Move the 5 panels'
   `setDefaults(...)` calls out of their constructors and into one
   `SwingUtilities.invokeLater(...)` block run once, after
   `frame.setVisible(true)` in `GuiApp.launch()` — this alone means the
   window appears instantly and the (now-cached, thanks to Fix 1)
   TDB parse happens in the background/next EDT tick instead of blocking
   construction.
3. **Route `DatabaseExtractionPanel`'s inspection calls through
   `MainController`'s existing `CalculationSession`-aware surface,
   instead of the raw `ModelInspectionService`/`TdbParser` path,** so
   "browsing a model" and "calculating against a model" share the same
   notion of "the current system" rather than being two disconnected
   pieces of state that happen to often point at the same file by
   coincidence of matching default strings. Concretely: add a thin method
   on `MainController` — e.g. `inspectModel(tdbPath, elements)` already
   exists and can internally call
   `calculationSession.setModel(tdbPath, elements, phases)` (once phases
   are known) so that by the time the user clicks "Run," the session's
   `ThermodynamicSystem` is *already* built from the same load the
   sidebar just did — not rebuilt from scratch a second time.

   **Caveat, to resolve during implementation, not guessed here:**
   `CalculationSession.setModel(...)` needs a phase list, but
   `DatabaseExtractionPanel`'s TDB-selection step (`onTdbSelected`)
   happens *before* the user has picked elements/phases — so this
   3-argument `setModel` call can only fire once elements+phases are
   actually confirmed (`onAddElements`/`refreshPhasesForConfirmed`), not
   at raw TDB-selection time. `inspectModel`'s *no-elements* call (just
   listing available elements from the file) should stay on a lighter
   path that doesn't require a full `ThermodynamicSystem` build — likely
   a `ModelInspectionService`-only call for that first step, with
   `CalculationSession.setModel` only invoked once phases are confirmed.
   This nuance needs to be worked out precisely against the actual
   `DatabaseExtractionPanel` state machine during implementation, not
   assumed here.

## What this plan does NOT do (explicitly out of scope)

- Does not change `CalculationSession`'s public API (`setModel`/
  `calculateEquilibrium`/`calculatePhaseDiagram`/accessors) — Fix 2 is
  purely about *when* and *how often* the GUI calls into it, not what it
  exposes.
- Does not unify the 5 `DatabaseExtractionPanel` instances into one
  shared widget/component — each calculation type (single-point, step,
  map, phase-diagram, inspect) still has its own sidebar UI. What's
  unified is the *backing state* (one `CalculationSession` per
  `MainController`), not the UI widget count.
- Does not touch `ModelInspectionService`/`TdbParser`'s public contracts
  beyond the one-field cache in Fix 1 — no signature changes.
- Does not address `EquilibriumSolver`/`GridMinimizer`'s disordered-
  constitution bug (Step 6 of `docs/plan-3layer-core-dataflow.md`) — a
  separate, already-flagged issue.

## Verification plan

- Add a small counter/log-count check (or reuse the existing "tdb method
  is called with" print) to confirm exactly ONE parse of the default TDB
  happens across the entire GUI startup sequence, not up to 9.
- Confirm the GUI window appears (visually, or via a startup-timing log)
  before any TDB parsing completes, not after.
- Confirm existing GUI single-point flow
  (`GuiMainControllerEquilibriumTest`) still passes unchanged — Fix 2
  must not alter `runSinglePoint`'s existing behavior/output.
- Confirm switching the TDB path in one sidebar panel doesn't silently
  affect another panel's independent selection (each panel's own
  `DatabaseSelection` UI state stays independent; only the underlying
  cache/session sharing changes, not per-panel UI behavior) — the fix is
  about not re-parsing identical files, not about forcing all panels to
  agree on one TDB at all times.
- Re-run the full existing regression suite
  (`CalculationSessionTest`, `CalculationApiServerTest`,
  `CliEquilibriumCommandTest`, `RkModelBaselineTest`,
  `V2ZrGibbsBaselineTest`, `EMatNCTest`, `ThermodynamicSystemSmokeTest`)
  to confirm the `TdbParser` caching change (Fix 1) doesn't change any
  numeric result for any consumer.
