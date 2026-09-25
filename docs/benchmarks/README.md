# Scientific Benchmark Baseline

Phase 16A. A small, reproducible set of canonical thermodynamic cases that
represents the application's current known-good behavior. This is the
reference future changes should be checked against — not a new test
framework and not a replacement for the existing JUnit suite.

## What this is

Every case in [benchmark_cases.md](benchmark_cases.md) is an **existing**,
already-passing test or an **existing** OC (OpenCalphad) reference
transcript already committed under `docs/oc_reference_tests/`. No new
expected values were invented for this phase; every reference number
traces to one of:

- an independent-model cross-check (CEWorkbench's `HillertSolver`), or
- an independent-implementation cross-check (OpenCalphad's own
  `CALCULATE TRANSITION` / `c transition` command, run and captured in
  `docs/oc_reference_tests/*_output.txt`), or
- a self-consistent numerical check within this codebase (e.g. driving
  force converged to ~0, mass balance closes, G matches a direct
  model evaluation).

## How to use this benchmark

Run the case's JUnit test (see each case's "How to run" line) and confirm:

1. It still passes.
2. Where a **numerical reference** value is listed, the test's own
   assertion tolerance (given per case) still holds — these are exact
   values from an independent source, not just "did it converge".
3. Where only a **qualitative** expectation is listed (e.g. "converges,
   correct phase set"), only completion and phase-set identity matter —
   there is no independent numerical reference for that case.

If a change to solver/model code causes one of these cases to start
failing or to converge to a different value outside its stated
tolerance, treat that as a regression against the frozen baseline and
investigate before proceeding — do not loosen the tolerance to make it
pass.

## Reference classification

Each case is tagged as one of:

- **Numerical** — has an exact external reference value (OC transcript or
  CEWorkbench cross-check) with a stated tolerance. A deviation beyond
  tolerance is a scientific regression.
- **Scientific** — internally self-consistent physics check (stationarity,
  mass balance, global-stability comparison against an independent
  search) but no external absolute-value reference.
- **Integration** — exercises a full code path (GUI/CLI/REST wiring,
  multi-component TDB parsing) where successful completion and correct
  phase-set/structure matter more than a specific numeric value.

## Running the benchmark

```
./gradlew test --tests "<FullyQualifiedTestClassName>"
```

for an individual case, or `./gradlew test` for the full suite (138
tests, 0 failures, 13 skipped as of this phase — see
[benchmark_cases.md](benchmark_cases.md) case list for which of the 13
skips are `@Disabled`/slow-suite exclusions relevant here).

**Tag note:** `build.gradle`'s default `test` task *excludes*
`slow`, `integration-heavy`, `external`, and `exploratory` tags (this is
what produces the 138/0/13 baseline). Several benchmark cases below
carry one of those tags and need the matching Gradle task instead:

| Tag | Task |
|---|---|
| (none) | `./gradlew test` |
| `slow` | `./gradlew testSlow` |
| `integration-heavy` | `./gradlew testIntegrationHeavy` |
| `external` | `./gradlew testExternal` |
| `exploratory` | `./gradlew testExplore` |
| any/all | `./gradlew testAll` |

Each case below states its tag and the task to use.

## Files

- `README.md` — this file.
- `benchmark_cases.md` — the 13 selected cases, one per category, with
  exact reference values, tolerances, and reproduction steps.
