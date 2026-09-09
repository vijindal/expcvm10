# Merge RkGibbs and RkPhaseModelAdapter; extend GibbsEnergyModel's contract for all models

## Context

`GibbsEnergyModel` (`src/system/model/GibbsEnergyModel.java`) is a 563-line
abstract class carrying two generations of API: a legacy stateful surface
(`evaluateG()`/`gradient(x,T)` twins, `compute()`/`PhaseEquilData`, an
`eMat`/`cG`/`cT`/`cP`/`cAB` "equilibrium matrix" block, full G0 storage/caching)
used by the **legacy `EquilibriumSolver`** (still wired into production: the UI
solve path via `ui/layer/EquilibriumUseCase.java`, and phase-diagram tracing via
`calc/diagram/DiagramTracer.java`/`LineStepper.java`/`PhaseChangeHandler.java`) —
plus a newer stateless `sundman*` surface that only `CefPhaseModelAdapter`
currently implements, for the active `EquilibriumSolverV2` (Sundman-algorithm
solver). `RkGibbs` (pure RK math) and `RkPhaseModelAdapter` (its
`GibbsEnergyModel` wrapper) are split the same way `CefGibbs`/
`CefPhaseModelAdapter` are, but `RkGibbs`/`RkPhaseModelAdapter` were never
given the `sundman*` methods — so RK phases (LIQUID) cannot be driven through
`EquilibriumSolverV2` at all today.

**Scope note**: the `sundman*` contract addition to `GibbsEnergyModel` is a
change to the abstract class itself, so it applies to every model that
extends it — CEF, RK, and any future model type, not RK alone. The
CODE-MERGE work in this pass (folding `RkPhaseModelAdapter` into `RkGibbs`)
is scoped to RK only because that is the concrete gap today
(`CefPhaseModelAdapter` already implements the contract); nothing here treats
RK as a permanent special case, and `CefPhaseModelAdapter` is expected to
need no changes precisely because it's already conforming.

The user wants `RkGibbs` to implement `GibbsEnergyModel` directly, eliminating
the separate `RkPhaseModelAdapter` class, with `GibbsEnergyModel`'s contract
extended so the 10 `sundman*` methods become abstract methods that every
extending model class must implement. Confirmed with the user: `EquilibriumSolverV2`
currently holds phases as the concrete type `CefPhaseModelAdapter`, not the
abstract `GibbsEnergyModel` (see "Solver wiring" note below) — this pass
establishes the CONTRACT on the abstract class; making the solver consume any
model polymorphically through that contract is a deliberately separate, later
step, not part of this pass.

**Investigation found the legacy (pre-Sundman) surface is still live**, not
dead code (see below) — this changes the shape of the merge from "shrink the
contract" to "add to it."

## What EquilibriumSolverV2 actually requires

Exhaustive grep of every method `EquilibriumSolverV2` calls on a phase-model
object gives exactly these 13 methods:

| Method | Sundman quantity | Purpose |
|---|---|---|
| `sundmanG(T, y)` | G(Y) | phase Gibbs energy |
| `sundmanGradient(T, y)` | ∂G/∂Y | stationarity driver |
| `sundmanHessian(T, y)` | ∂²G/∂Y² | bordered phase matrix |
| `sundmanM(y)` | M_A(Y) | element content per formula unit |
| `sundmanMJacobian()` | ∂M_A/∂Y | mass-balance/stationarity coupling |
| `sundmanSiteRatios()` | a[s] | site ratios per sublattice |
| `sundmanNumSublattices()` | ns | sizes constraint block |
| `sundmanNumSiteVariables()` | nip | sizes G_YY, gy, dMdY |
| `sundmanOffsets()` | — | constituent block offsets in Y |
| `sundmanConstituentsPerSublattice()` | nc[s] | constraint/normalization structure |
| `isValid(y)` | — | constitution validity check |
| `getInitialInternalVars(x)` | — | initial constitution from overall composition |
| `phaseName()` / `modelType()` | — | reporting only |

These 10 non-identity `sundman*` methods exist TODAY ONLY as concrete methods
on `CefPhaseModelAdapter` (`src/system/model/cef/CefPhaseModelAdapter.java:160-257`)
— they are not currently declared on `GibbsEnergyModel` at all, so nothing
today actually enforces that a model implements them. `isValid`,
`getInitialInternalVars`, `phaseName`, `modelType` already exist as abstract
methods on `GibbsEnergyModel` and need no change.

For RK (single sublattice, Y≡x, no vacancies), every one of the 10 new methods
reduces to a constant or a one-line passthrough to `RkGibbs`'s existing
`evaluate`/`gradient`/`hessian`/`nc()`: `sundmanM(y)`→`y.clone()`,
`sundmanMJacobian()`→identity matrix, `sundmanSiteRatios()`→`{1.0}`,
`sundmanNumSublattices()`→`1`, `sundmanNumSiteVariables()`→`nc()`,
`sundmanOffsets()`→`{0}`, `sundmanConstituentsPerSublattice()`→`{nc()}`. No new
derivative math is needed — this is the same "RK is CEF's ns=1 special case"
already noted in `CefPhaseModelAdapter`'s own commentary.

## CRITICAL FINDING — the legacy surface is NOT dead code; do not delete it

A whole-tree grep (not limited to files touching RK/CEF adapters) for callers
of `compute()`, `numComponents()`/`numInternalParams()`/`numTotalParams()`,
`cacheDerivatives()`/`getCached*`, the equilibrium-matrix accessors, all G0
methods, and the no-arg `evaluateG()`/`evaluateGx()`/`evaluateGT()`/
`evaluateGP()` family found it is actively used by:

- **`src/calc/equil/EquilibriumSolver.java`** — a separate, still-live
  production solver (distinct from `EquilibriumSolverV2`), instantiated from
  **`src/ui/layer/EquilibriumUseCase.java`** (the UI's solve path) and from
  **`src/calc/diagram/DiagramTracer.java`** / **`LineStepper.java`** /
  **`PhaseChangeHandler.java`** (phase-diagram tracing).
- **`src/calc/equil/PhaseRecord.java`**, **`src/system/model/PhaseEquilData.java`**
  — supporting types for that solver.
- **`src/calc/equil/sundman/SundmanPhase.java`** (used by the separate, older
  `SundmanEquilibriumSolver` — confirmed CEF-only, no RK dependency, but it
  does call `model.numComponents()`).
- Tests `EMatNCTest.java` (calls `gm.compute(...)` directly),
  `V2ZrPhaseTest.java`, `CefSinglePhaseIntegrationTest.java`.

So this surface is dead **relative to `EquilibriumSolverV2` only** — it is
live relative to the rest of the application. Deleting it would break the
UI's actual solve path and phase-diagram tracing today. **This plan does not
remove anything from `GibbsEnergyModel`.** Retiring the legacy surface is a
separate, much larger effort gated on migrating `EquilibriumSolver`/
`EquilibriumUseCase`/`DiagramTracer` to V2 first, and is explicitly out of
scope here.

## Approach — extend the contract for all models; merge RK's implementation now

1. ✅ **DONE.** **Add the 10 `sundman*` methods to `GibbsEnergyModel`** as new
   abstract methods (signatures copied verbatim from `CefPhaseModelAdapter`).
   This is a change to the shared base class, so it applies to every current
   and future subclass — not an RK-specific addition. No existing method,
   field, or behavior is removed. `CefPhaseModelAdapter` automatically
   satisfies the extended abstract class with no code changes (this is itself
   the validation that the new contract is correctly scoped, proven against a
   second, structurally different implementation before RK is merged).

   Implementation note: per user direction, the 10 methods were renamed away
   from the `sundman*` prefix to meaningful names before landing:
   `sundmanG`→`siteEnergy`, `sundmanGradient`→`siteGradient`,
   `sundmanHessian`→`siteHessian`, `sundmanM`→`elementAmounts`,
   `sundmanMJacobian`→`elementAmountsJacobian`,
   `sundmanSiteRatios`→`siteRatios`, `sundmanNumSublattices`→`numSublattices`,
   `sundmanNumSiteVariables`→`numSiteVariables`, `sundmanOffsets`→
   `sublatticeOffsets`, `sundmanConstituentsPerSublattice`→
   `constituentsPerSublattice`. Verified zero naming collisions before
   implementing. `CefPhaseModelAdapter`'s existing concrete methods were
   renamed to match (`@Override` added), and all call sites in
   `EquilibriumSolverV2.java` and the V2Zr/Sundman test files were updated.
   As an interim compile fix (since `RkPhaseModelAdapter` did not yet
   implement the new abstract methods), minimal RK-specialized (ns=1)
   implementations were added directly to `RkPhaseModelAdapter.java` — these
   are forward-compatible stubs that will migrate into `RkGibbs` unchanged in
   step 2 below. Verified via whole-project `javac` compile (clean) and by
   re-running `RkModelBaselineTest`, `V2ZrGibbsBaselineTest`, and
   `V2ZrGibbsLiteratureBaselineTest` (all PASS, no regression).

2. ✅ **DONE.** **Merge `RkPhaseModelAdapter` into `RkGibbs`**: `RkGibbs` becomes
   `public class RkGibbs extends GibbsEnergyModel`, absorbing
   `RkPhaseModelAdapter`'s entire current body as-is:
   - The `phaseName`/`elementNames` fields and constructor parameter (moved
     from `RkPhaseModelAdapter`; `RkGibbs`'s constructor doesn't take
     `elementNames` today, so this is a real constructor-signature change).
   - All of `RkPhaseModelAdapter`'s existing `GibbsEnergyModel`
     implementations unchanged: `evaluateG()`/`evaluateG(x,T)`/`gradient`/
     `hessian`/`evaluateGT()`/`evaluateGP()`/`evaluateGx()`/etc.,
     `getInitialInternalVars`/`compositionFromInternal`/`isValid`, the full
     `compute()`/`PhaseEquilData`/`buildEList` logic, `printPhaseInfo()`/
     `printDerivatives()` — none of this is dead, per the finding above, so
     none of it is dropped.
   - New: the 10 `sundman*` methods as thin wrappers over `RkGibbs`'s own
     pure-math methods (`sundmanG(T,y)`→`evaluate(y,T)` — note the arg-order
     swap vs. CEF's `(T,y)` — `sundmanGradient(T,y)`→`gradient(y,T)`,
     `sundmanHessian(T,y)`→`hessian(y,T)`, plus the constant/identity forms
     listed above).
   - `RkGibbs`'s existing pure-function methods (`evaluate(x,T)`,
     `gradient(x,T)`, `hessian(x,T)`, `temperatureDerivative(x,T)`, `nc()`,
     etc.) stay unchanged and public, so callers wanting stateless access
     (e.g. `RkModelBaselineTest`) can keep using them directly.
   - Delete `RkPhaseModelAdapter.java` once its body has moved.

3. ✅ **DONE.** **Update the two production construction sites** (both confirmed by direct
   read, not just grep):
   - `src/system/model/rk/RkPhaseModelFactory.java:47-82` — `build()`
     currently does `RkGibbs gibbs = new RkGibbs(nc, g0Elements, phaseName,
     binaries, ternaries, quaternaries); return new
     RkPhaseModelAdapter(gibbs, phaseName, new ArrayList<>(elements));` —
     change to construct `RkGibbs` with the extra `elementNames` argument and
     return it directly; update `build()`'s return type from
     `RkPhaseModelAdapter` to `RkGibbs`.
   - `src/system/model/PhaseModelFactory.java:159-170` — the local variable
     `RkPhaseModelAdapter rk = RkPhaseModelFactory.build(...)` becomes
     `RkGibbs rk = ...`; no other change, since it's immediately passed to
     `PhaseModel.forAlternateModel(phaseName, rk)`, which already takes a
     `GibbsEnergyModel`.

4. ✅ **DONE.** **Update `src/test/RkModelBaselineTest.java`** — it already reaches the
   model only through `PhaseModelFactory.toGibbsModel()` (returns
   `pm.alternateModel` typed as `GibbsEnergyModel`), so it likely needs no
   source change at all; re-run to confirm it still passes against the same
   digitized Fig. 10 data (49 points, ≤20 J/mol tolerance) and still prints
   the correct model class name (now `RkGibbs` instead of
   `RkPhaseModelAdapter`).

5. ✅ **DONE.** **Fix any other RK-specific reference.** Whole-tree grep for
   `RkPhaseModelAdapter`/`new RkGibbs(` found no other production callers
   beyond the two factory files above. The broader 21-file grep from initial
   exploration turned out to be dominated by files that call the *legacy*
   `GibbsEnergyModel` surface on **`CefPhaseModelAdapter`** instances (not
   RK) — those are entirely unaffected by this purely-additive merge and need
   no changes. Confirmed post-merge: final grep for `RkPhaseModelAdapter`
   across `src/` returns zero matches; `RkPhaseModelAdapter.java` deleted.

## Status: all 5 steps complete

Verified via whole-project `javac` compile (clean, `EXIT=0`),
`RkModelBaselineTest` (49 digitized Fig. 10 points, PASS, now printing
`system.model.rk.RkGibbs` as the model class), `V2ZrGibbsBaselineTest` /
`V2ZrGibbsLiteratureBaselineTest` (CEF path, no regression), and `EMatNCTest`
(legacy `EquilibriumSolver`-path consumer on `CefPhaseModelAdapter`, confirms
the legacy surface was correctly left untouched). Solver-side wiring for
`EquilibriumSolverV2` (see below) remains explicitly deferred.

## Solver wiring — explicitly deferred, not part of this pass

Confirmed by direct read: `EquilibriumSolverV2.PhaseWork.model` (line 143) is
typed as the CONCRETE class `CefPhaseModelAdapter`, not `GibbsEnergyModel`,
and `initialize()` has a hard `instanceof CefPhaseModelAdapter` gate that
rejects anything else. So the solver does not call the 10 `sundman*` methods
polymorphically through `GibbsEnergyModel` today, even after this merge — it
only ever holds `CefPhaseModelAdapter` references. This pass makes the 10
methods part of `GibbsEnergyModel`'s abstract contract (so every extending
model class must implement them) and confirms both `CefPhaseModelAdapter` and
`RkGibbs` satisfy it; it does NOT retype `PhaseWork.model` to
`GibbsEnergyModel` or relax `initialize()`'s `instanceof` check. That
solver-side wiring — needed before any non-CEF model (RK/LIQUID included) can
actually be driven through `EquilibriumSolverV2` end-to-end — is a separate,
later change.

## Verification

- Compile the whole project via the plain `javac` sweep used throughout this
  session (the Gradle wrapper here is incompatible with the installed JDK):
  compile every file under `src/`, confirm zero errors.
- Re-run `src/test/RkModelBaselineTest.java` — must still pass (49 digitized
  Fig. 10 points, ≤20 J/mol tolerance), now printing `RkGibbs` as the model
  class.
- Re-run `V2ZrGibbsBaselineTest`/`V2ZrGibbsLiteratureBaselineTest` (CEF path,
  untouched) to confirm no incidental regression from the `GibbsEnergyModel`
  contract addition.
- Spot-check one legacy `EquilibriumSolver`-path consumer still compiles and
  runs unchanged (e.g. `EMatNCTest.java`, since it calls `compute()` directly
  on a `CefPhaseModelAdapter`) — this is the concrete check that the "don't
  touch the legacy surface" decision was actually respected.
- Do NOT attempt to run `EquilibriumSolverV2` end-to-end with LIQUID — that
  integration is explicitly out of scope for this pass.
