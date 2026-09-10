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
3. **`CalculationSession` becomes the UI's ONLY point of contact with the
   System Layer — for browsing, not just calculating** (design correction,
   2026-09-10, to stay compatible with `docs/dataflow_target.png`, which
   shows exactly one arrow each way between "UI" and "CalculationSession"
   and none between "UI" and the System/Calculation layers directly). The
   original draft of this plan proposed keeping `DatabaseExtractionPanel`'s
   TDB-selection step (list available elements from a file, before any
   phases are chosen) on a separate `ModelInspectionService`-only path,
   with `CalculationSession.setModel` only invoked once phases are
   confirmed. **That would recreate the exact disconnect that caused this
   bug** — the UI reaching around `CalculationSession` into a System-layer
   concern directly — so it is rejected in favor of:

   - Add two lightweight, calculation-free query methods to
     `CalculationSession`:
     ```java
     public List<String> availableElements(String tdbFilePath) throws IOException;
     public List<String> availablePhasesFor(String tdbFilePath, List<String> elements) throws IOException;
     ```
     Backed internally by a `DatabasePort`/`TdbParser` the session owns
     for browsing — a *separate* concern from the `ThermodynamicSystem`
     it builds for calculating, since listing phase names doesn't require
     a full `GibbsEnergyModel[]` build. Both of the System Layer's two
     jobs (browsing metadata, building calculable models) are reached
     through `CalculationSession` and nothing else, matching the diagram.
   - `DatabaseExtractionPanel.onTdbSelected()` calls
     `calculationSession.availableElements(tdbPath)` instead of
     `controller.inspectModel(...)` → `ModelInspectionService` →
     raw `TdbParser`. `onAddElements()`/`refreshPhasesForConfirmed()`
     calls `calculationSession.availablePhasesFor(tdbPath, elements)`
     instead of `controller.getPhasesForElements(...)`.
   - Once the user has actually confirmed a phase selection (not just
     browsed), `calculationSession.setModel(tdbPath, elements, phases)`
     fires — building the real `ThermodynamicSystem` from the same
     already-cached (via Fix 1) TDB load the browsing steps just did, so
     "Run Calculation" doesn't rebuild from scratch a second time.
   - `MainController.inspectModel(...)`/`getPhasesForElements(...)` (the
     current UI-facing methods backed by `ModelInspectionService`) either
     become thin pass-throughs to these new `CalculationSession` methods,
     or are retired in favor of GUI panels calling `CalculationSession`
     directly via `MainController` — exact call-site shape to be decided
     at implementation time, but in either case `ModelInspectionService`/
     raw `TdbParser` must no longer be reachable from any GUI panel except
     through `CalculationSession`.

## What this plan does NOT do (explicitly out of scope)

- Does not change `CalculationSession`'s existing calculation methods
  (`calculateEquilibrium`/`calculatePhaseDiagram`/`calculateStep`/
  `calculateMap`/their accessors) — Fix 2 adds two new browsing methods
  alongside them; it does not touch what's already there.
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

## Implementation  ✅ DONE (2026-09-10)

Both fixes landed as designed, plus the figure/plan correction that
routes browsing through `CalculationSession` (see the note at the top of
Fix 2 above):

- **Fix 1**: `TdbParser` gained a `loadedFilePath` field; `load(...)` is
  now a no-op if the requested path matches what's already loaded.
- **Fix 2a (browsing)**: `CalculationSession.availableElements(tdbPath)`
  and `availablePhasesFor(tdbPath, elements)` added, backed by a
  `DatabasePort` the session owns for browsing (separate from
  `currentSystem`, since listing names doesn't need a full
  `GibbsEnergyModel[]` build). `MainController.inspectModel(...)`/
  `getPhasesForElements(...)` now delegate to these instead of
  `ModelInspectionService` directly — `DatabaseExtractionPanel` (the
  only GUI class that calls these two `MainController` methods) was
  changed nowhere else, since it already went through `MainController`.
- **Fix 2b, revised same day — fully lazy, zero parses at startup.**
  The first cut of this fix (deferring `setDefaults(...)`'s body via
  `SwingUtilities.invokeLater(...)`) reduced startup parsing from up to 9
  down to 1 — the single "pre-populate the default database" load still
  ran, just after first paint instead of blocking it. Per explicit
  follow-up direction, this was tightened further to **zero** parses at
  startup: `setDefaults(...)` now only pre-fills the combo box's editor
  text and the element input field (`tdbCombo.getEditor().setItem(...)`,
  `elemInputField.setText(...)`) — plain text-field updates, no TDB
  access — and no longer calls `onTdbSelected()`/`onAddElements()` at
  all. The actual load/parse now happens only on genuine user action:
  selecting an item from the combo box, pressing Enter in its editor, or
  clicking Browse — all three already wired to `onTdbSelected()`
  independently of `setDefaults`. Confirmed
  `tdbCombo.getEditor().setItem(...)` does not itself fire the combo's
  `comboBoxChanged` action (that only fires from real selection/
  `setSelectedItem`), so this is safe with no `invokeLater` needed for
  `setDefaults` itself anymore.

**Verified:**
- Startup TDB-parse count confirmed via the existing "tdb method is
  called with" print: **0** (first fixed to 1, then to 0 per follow-up
  direction; both down from up to 9 originally) — checked directly
  against `./gradlew run --args="--gui"` and direct-`java` GUI launch
  output.
- New `CalculationSessionTest` case
  (`testBrowsingDoesNotRequireSetModel`) confirms `availableElements`/
  `availablePhasesFor` work with no `setModel()` call, don't themselves
  build a `ThermodynamicSystem` (`hasModel()` stays false), and compose
  correctly with a subsequent `setModel()` call using the same
  TDB/elements/phases just browsed.
- `ModelInfo.isFileExists()`/`getLastModifiedEpochMillis()` (relied on by
  `ModelInspectorSidebarPanel.showStatus`) preserved correctly in the
  rewritten `MainController.inspectModel` despite no longer routing
  through `ModelInspectionService.inspectModel` for that check.
- Whole-project compile clean via both the direct `javac` sweep and
  `./gradlew compileJava compileTestJava`.
- Full existing regression suite (`CalculationSessionTest`,
  `CalculationApiServerTest`, `CliEquilibriumCommandTest`,
  `GuiMainControllerEquilibriumTest`, `RkModelBaselineTest`,
  `V2ZrGibbsBaselineTest`, `V2ZrGibbsLiteratureBaselineTest`,
  `EMatNCTest`, `ThermodynamicSystemSmokeTest`) re-run clean — no
  numeric result changed for any consumer.

**Not changed, confirmed still correct:** `ModelInspectionService`/raw
`TdbParser` remain reachable from `MainController.getPhaseParameters(...)`
(used by `ModelInspectorSidebarPanel`'s detailed parameter dump) — a
genuinely different, deeper inspection feature than
`DatabaseExtractionPanel`'s browsing, explicitly out of this plan's scope
per "does not touch `ModelInspectionService`'s public contracts."
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

## Fix 3 (2026-09-10) — extend browsing to database *discovery*, for all three UIs

**Gap found, per direct follow-up:** Fix 2's browsing methods
(`availableElements`/`availablePhasesFor`) answer "what's inside this
already-chosen TDB file" — but discovering *which TDB files exist to
choose from in the first place* was never unified. Confirmed by direct
check: only the GUI has this capability at all, and even there it
bypasses `CalculationSession` entirely —
`DatabaseExtractionPanel.populateTdbCombo()` does a raw
`new File("data").listFiles((d, name) -> name.endsWith(".tdb"))` scan
locally inside the panel. The CLI and API have no equivalent — every
CLI command and the API's request DTOs simply require the caller to
already know a specific TDB path (`--tdb FILE`, `"tdbFilePath": "..."`).

**Fix:** add a third browsing method,
`CalculationSession.availableDatabases()`, scanning a fixed `data`
directory (relative to the working directory — matching what every
existing hardcoded default in this codebase already assumes; no new
configuration surface). `DatabaseExtractionPanel.populateTdbCombo()`
calls this (via `MainController`) instead of scanning the filesystem
itself; the CLI/API gain the same capability by construction, since
they already hold or can trivially hold a `CalculationSession`.

**Scope note:** this only lists file names in one fixed, conventional
directory — it is not a general file-picker replacement. The GUI's
"Browse..." button (`JFileChooser`, letting a user pick any file
anywhere on disk) is a different, legitimate capability and is
untouched by this fix; `availableDatabases()` only serves the
dropdown's pre-populated list of the project's own `data/*.tdb` files.

**Verification for Fix 3:**
- `CalculationSessionTest`: `availableDatabases()` lists at least the
  known fixture files (e.g. `data/VZR-re2.TDB`) and requires no
  `setModel()` call, matching Fix 2's other two browsing methods.
- Confirm `DatabaseExtractionPanel`'s combo box is still populated with
  the same set of files as before (no behavior change from the user's
  point of view — only the code path moved from local `File.listFiles()`
  to `CalculationSession`).
- CLI/API gain no new commands/endpoints in this pass — adding a
  `list-databases`-style CLI command or `GET /databases` API endpoint
  that surfaces this is a natural follow-on but is not required by this
  fix; the fix's scope is making the *capability* exist on
  `CalculationSession` for any UI to use, not building new UI surface
  for it in the CLI/API immediately.
