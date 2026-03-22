# Phase Diagram Debug Log — NB-TI System

## Overview

Investigation of why the MAP phase diagram calculation produced 0 lines and 0 nodes
for the NB-TI system (cost507.tdb). Found and fixed 6 cascading bugs.

---

## Bugs Found and Fixed

### Bug 1 — GridMinimizer: null short-circuit accepted spurious tangents

**File:** `src/thermocalc/equil/GridMinimizer.java`

**Symptom:** Multi-phase equilibrium solver returned `conv=false` at T=600K, x=0.5
(LIQUID+BCC initial estimate instead of single-phase BCC).

**Root cause:** The condition `if (best2p == null || ...)` accepted the FIRST
two-phase tangent found without checking whether it is actually lower than the
single-phase energy. Resulted in spurious LIQUID+BCC estimates at temperatures
where LIQUID is far above BCC.

**Fix:** Always require `tangentG < singlePhaseG` before accepting a two-phase
estimate. Also keep the best (lowest) among all valid two-phase estimates.

```java
// Old (wrong):
if (best2p == null || est.tangentG(x0) < singlePhaseG(...)) { best2p = est; }

// New (correct):
double singleG = singlePhaseG(candidates, x0, T);
if (est.tangentG(x0) < singleG) {
    if (best2p == null || est.tangentG(x0) < best2p.tangentG(x0)) {
        best2p = est;
    }
}
```

---

### Bug 2 — EquilibriumSolver: convergence tolerance too tight

**File:** `src/thermocalc/equil/EquilibriumSolver.java`

**Symptom:** Newton iterations never formally converged; `isConverged()` always false.

**Root cause:** `CONVERGED_TOL = 1e-10` is sub-machine-precision for μ corrections
in J/mol (typical magnitude ~1e4 J/mol). With double precision (~1e-16 relative
error), corrections bottom out at ~1e-12, never reaching 1e-10.

**Fix:** Changed to `CONVERGED_TOL = 1e-4` (0.1 mJ/mol — more than sufficient
for thermodynamic calculations).

---

### Bug 3 — EquilibriumSolver: degenerate boundary compositions

**File:** `src/thermocalc/equil/EquilibriumSolver.java`

**Symptom:** At exact x=0.0 (pure TI, comp={0.0, 1.0}), solver returned BCC_A2
instead of HCP_A3 at T=600K, and BCC_A2 instead of LIQUID at T=2000K.

**Root cause:** `log(0)` in the ideal mixing entropy term corrupts the Gibbs
energy when any component has exactly zero mole fraction.

**Fix:** Clamp all composition components to ≥ 1e-9 and renormalize at the start
of `solve()`:

```java
compOverAll = compOverAll.clone();
double sum = 0;
for (int i = 0; i < nc; i++) {
    compOverAll[i] = Math.max(compOverAll[i], 1e-9);
    sum += compOverAll[i];
}
for (int i = 0; i < nc; i++) compOverAll[i] /= sum;
```

---

### Bug 4 — CliApp / PhaseDiagramConfigPanel: wrong composition axis component index

**Files:** `src/ui/cli/CliApp.java`, `src/ui/gui/PhaseDiagramConfigPanel.java`

**Symptom:** MAP diagram always scanned at x={0.5, 0.5} regardless of axis position.
Found 0 lines/nodes (initial scan found no phase change).

**Root cause:** `componentIndex=1` was used for the COMPOSITION axis. When
`resolveComp` sets `comp[1] = axisValue` and then re-normalizes by setting
`comp[1] = 1 - comp[0]`, it immediately overwrites the axis value. Result: the
composition always resolved to the baseComp regardless of the axis value.

**Fix:** Changed to `componentIndex=0` (first element = NB, the independent
component in the NB-TI binary):

```java
// Old: return new AxisConfig(name + " (X)", 1, min, max, step);
// New:
return new AxisConfig(name + " (X)", 0, min, max, step);  // index 0 = first component
```

---

### Bug 5 — DiagramTracer: initial exits only for axis 0

**File:** `src/thermocalc/diagram/DiagramTracer.java`

**Symptom:** Only composition-direction (horizontal) ZPF lines were traced.
Temperature-direction (vertical) boundaries were never discovered.

**Root cause:** `addInitialExits` created exits only for `axisIdx=0`, missing
the temperature axis direction.

**Fix:** Loop over all axes when creating initial exits:

```java
for (int axisIdx = 0; axisIdx < axes.length; axisIdx++) {
    for (EquilibriumResult.PhaseResult pr : eq.getStablePhases()) {
        node.addExit(new DiagramExit(node, pr.phaseName, null, axisIdx, +1));
        node.addExit(new DiagramExit(node, pr.phaseName, null, axisIdx, -1));
    }
}
```

---

### Bug 6 — LineStepper: excluded phase driving force never computed; wrong sign

**File:** `src/thermocalc/diagram/LineStepper.java`

**Symptom:** ZPF lines were traced indefinitely without ever detecting a phase
change. Phase change flag was never set.

**Root cause (a):** The old `maxExcludedDrivingForce` looked for the fixedPhase in
`eq.getMetastablePhases()`. But the fixedPhase is excluded from the active
candidates and therefore never appears in the solver's metastable list — the
method always returned 0.0.

**Root cause (b):** The detection condition was `maxDF > DRIVING_FORCE_TOL`, but
the correct condition for "phase wants to enter" is `γ < 0` (phase is BELOW the
tangent plane = more stable). The sign convention is:
- γ = G(x) + Σ μᵢ · xᵢ
- γ < 0: phase is more stable than current equilibrium → SHOULD enter
- γ > 0: phase is metastable → should NOT enter

**Fix:** Replaced with `minExcludedDrivingForce` that manually evaluates the
fixedPhase's driving force using the equilibrium's μ values. Changed condition to
`minDF < -DRIVING_FORCE_TOL`.

```java
private double minExcludedDrivingForce(EquilibriumResult eq,
                                        List<PhaseModelPort> allCandidates,
                                        String fixedPhase, String forbiddenPhase,
                                        double[] comp) {
    double minDF = Double.POSITIVE_INFINITY;
    double[] mu  = eq.getMu();
    double   T   = eq.getT();
    for (PhaseModelPort m : allCandidates) {
        String name = m.phaseName();
        if (!name.equals(fixedPhase) && !name.equals(forbiddenPhase)) continue;
        double G = m.evaluateG(comp, T);
        double df = G;
        for (int i = 0; i < mu.length; i++) df += mu[i] * comp[i];
        if (df < minDF) minDF = df;
    }
    return minDF == Double.POSITIVE_INFINITY ? 0.0 : minDF;
}
```

---

## Current Status (as of 2026-03-23)

After applying all 6 fixes, the MAP diagram produces **2 nodes and several lines**
for NB-TI with axis0=COMPOSITION(0,1,0.02) and axis1=TEMPERATURE(500,3000,25).
The HCP/BCC boundary is being partially traced.

**Remaining issue (pending systematic investigation):**

The driving force evaluation in `minExcludedDrivingForce` evaluates G at the
*overall* composition rather than at the phase's *tie-line endpoint* composition.
This is an approximation. For some exits (particularly when stepping along the
temperature axis), the phase change may not be detected because:

- The active single-phase equilibrium at `x_overall` gives correct μ values
- But the excluded phase's driving force evaluated at `x_overall` may be
  insufficiently negative even though the phase genuinely wants to enter

The correct approach: minimize `G_A(x) - Σμᵢxᵢ` over the composition of phase A
(find the tangent-plane distance at the optimal composition). This is the true
thermodynamic driving force.

**Test results confirming partial correctness:**

- T=600K, x=0.24: BCC driving force = -205 J/mol → correctly detected
- T=600K, x=0.22: BCC driving force = +84 J/mol → correctly NOT detected
- T=2000K, pure TI: LIQUID now correctly identified (after bug 3 fix)

**Next steps for systematic fix:**

1. Implement proper driving force minimization over composition in
   `minExcludedDrivingForce`
2. Or use the solver itself: call `solver.solve(T, P, comp, List.of(fixedPhase))`
   and compare its Gibbs energy against the current equilibrium tangent plane
3. Verify the full NB-TI diagram topology (HCP/BCC boundary + liquidus)
