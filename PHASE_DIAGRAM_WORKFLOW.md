# Phase Diagram Calculation — End-to-End Workflow

## Overview

This document traces the complete workflow for calculating a binary T-x phase diagram using the Sundman et al. (2021) algorithms (A–D).

---

## Workflow Trace: Binary Ti-Zr T-x Diagram

### Example Input (PhaseDiagramRequest)

```java
PhaseDiagramRequest request = new PhaseDiagramRequest();
request.setTdbFilePath("data/sgte.tdb");
request.setElements(List.of("Ti", "Zr"));
request.setPhases(List.of("LIQUID", "BCC_A2", "HCP_A3"));
request.setDiagramType(DiagramType.MAP);
request.addAxis(new AxisConfig("T / K",  AxisConfig.Type.TEMPERATURE,  800, 2000, 10));
request.addAxis(new AxisConfig("x(Zr)", 1, 0.0, 1.0, 0.01));
request.setFixedP(101325);
request.setStartComposition(new double[]{0.5, 0.5});  // 50% Ti, 50% Zr
```

---

## Step-by-Step Trace

### **LEVEL 1: GUI/Controller**
```
User clicks "Calculate Phase Diagram" button
  ↓
MainFrame.onCalculateAction()
  ├─ Reads inputs from GUI fields
  ├─ Constructs PhaseDiagramRequest
  └─ Calls MainController.runPhaseDiagram(request)
```

### **LEVEL 2: Use Case Orchestration**
```
MainController.runPhaseDiagram(request)
  │
  └─> PhaseDiagramUseCase.execute(request)
      │
      ├─ STEP 1: Load TDB and extract system
      │  ├─ TdbParser.load("data/sgte.tdb")
      │  │  └─ Parses SGTE database, populates tdb object
      │  │
      │  └─ TdbParser.extractSystem(["Ti", "Zr"])
      │     └─ Filters to binary Ti-Zr sub-system
      │
      ├─ STEP 2: Build phase models
      │  ├─ For "LIQUID":
      │  │  └─ RkPhaseModelFactory.build("LIQUID", ["Ti","Zr"], tdb)
      │  │     ├─ TdbUnaryGibbs("Ti", tdb) → G°_Ti(T) from TDB
      │  │     ├─ TdbUnaryGibbs("Zr", tdb) → G°_Zr(T) from TDB
      │  │     ├─ Extract PARAMETER L(Ti,Zr) from TDB → BinaryParam
      │  │     ├─ Construct RkGibbs(nc=2, g0=[Ti-Gibbs, Zr-Gibbs], binaries=[L(Ti,Zr)])
      │  │     └─ Wrap in RkPhaseModelAdapter(RkGibbs, "LIQUID")
      │  │
      │  ├─ For "BCC_A2": same pattern
      │  └─ For "HCP_A3": same pattern
      │     Result: candidates = [LIQUID, BCC_A2, HCP_A3] (all PhaseModelPort)
      │
      ├─ STEP 3: Resolve starting conditions
      │  ├─ startAxes = [800 K, 0.0]  (axis min values)
      │  ├─ startComposition = [0.5, 0.5]
      │  └─ fixedT, fixedP (for non-axis conditions)
      │
      └─ STEP 4: Run DiagramTracer
         └─> DiagramTracer.calculate(candidates, axes, startAxes, fixedT, fixedP, comp)
```

---

### **LEVEL 3: Diagram Tracing (Algorithm B)**

#### **3a. Initialize MAP mode (axes.length == 2)**

```
DiagramTracer.calculateMap(...)
  │
  ├─ STEP 1: Find initial node (scan T-axis until phase change)
  │  │
  │  └─> DiagramTracer.findInitialNode(...)
  │      │
  │      For T = 800, 810, 820, ... K at x(Zr) = 0.0:
  │      │
  │      ├─ T = 800 K, x(Zr) = 0.0  (pure Ti)
  │      │  └─ EquilibriumSolver.solve(800, P, [1.0, 0.0], candidates)
  │      │     ├─ [Algorithm A starts — inner loop]
  │      │     │  (see LEVEL 4 below)
  │      │     └─ Returns EquilibriumResult:
  │      │        {T=800, P, μ=[μ_Ti, μ_Zr],
  │      │         stablePhases=[{BCC_A2, amount=1.0, x=[1,0], ...}],
  │      │         metastablePhases=[{LIQUID, x=[1,0], γ<0, ...}, ...]}
  │      │
  │      ├─ T = 810 K: same (stable set unchanged)
  │      ├─ T = 820 K: same
  │      │  ...
  │      ├─ T = 1300 K: PHASE CHANGE detected!
  │      │  prevEq: {stable=[BCC_A2], ...}
  │      │  curEq:  {stable=[LIQUID], ...}
  │      │  ⇒ Create DiagramNode at (1290 K, 0.0)
  │      │
  │      └─ Add initial exits:
  │         For each stable phase at node:
  │           addExit(node, fixedPhase="LIQUID", axis=T, dir=+1)
  │           addExit(node, fixedPhase="LIQUID", axis=T, dir=-1)
  │           (repeat for other stable phases at the node)
  │
  ├─ STEP 2: Process all unvisited exits (main loop)
  │  │
  │  while (exit = diagram.nextUnvisitedExit()) != null:
  │  │
  │  └──────────────────────────────────────────────────────────────
  │      Iteration 1: Follow exit (fixedPhase="LIQUID", dir=+1)
  │      │
  │      └─> LineStepper.followLine(exit, candidates, axes, fixedT, fixedP)
  │          │
  │          ├─ Initialize line from parent node
  │          │  activePhases = [BCC_A2, HCP_A3]  (exclude LIQUID)
  │          │  compOverAll = mass-average of stable phases at parent
  │          │
  │          ├─ Step along T-axis (primary axis) in direction +1
  │          │  For each step: T = 1290 + 10, 1300 + 10, ... (or halved if needed)
  │          │  └─ Resolve axes: T_trial, x(Zr)_trial = parent_x(Zr)
  │          │
  │          ├─ Call EquilibriumSolver.solve(T_trial, P, comp, activePhases)
  │          │  └─ Returns eq_trial
  │          │
  │          ├─ Check driving forces of excluded phases (LIQUID):
  │          │  if max_DF(LIQUID) > TOLERANCE:
  │          │    line.phaseChangeDetected = true
  │          │    return line  ← ZPF line ended at phase change
  │          │
  │          ├─ Axis switching heuristic:
  │          │  (if stepping x-axis changes faster than T, switch to x-axis)
  │          │
  │          └─ Continue until axis limit or phase change
  │
  │      [LineStepper returns DiagramLine with sampled points]
  │
  │      ├─ If line.phaseChangeDetected:
  │      │  │
  │      │  └─> PhaseChangeHandler.handle(line, ...)
  │      │      │
  │      │      ├─ Bracket: A = 2nd-to-last point (DF ≤ 0)
  │      │      │           B = last point (DF > 0)
  │      │      │
  │      │      ├─ BISECTION loop:
  │      │      │  For i = 1 to MAX_BISECT:
  │      │      │    mid = (A + B) / 2
  │      │      │    eq_mid = EquilibriumSolver.solve(mid_T, P, comp, active)
  │      │      │    if max_DF(mid) ≤ TOL:  A ← mid
  │      │      │    else:                    B ← mid
  │      │      │
  │      │      ├─ nodeAxes = A  (exact ZPF crossing)
  │      │      ├─ nodeEq = EquilibriumSolver.solve(..., ALL_candidates)
  │      │      │  (re-include the formerly-fixed phase)
  │      │      │
  │      │      ├─ Classify node via Gibbs phase rule:
  │      │      │  f = n_axes - p + c = 2 - 3 + 2 = 1  ⇒ normal crossing (f > 0)
  │      │      │
  │      │      └─ Generate exits (Algorithm C2):
  │      │         Exit 1: fixedPhase="LIQUID", forbiddenPhase="HCP_A3", dir=+1
  │      │         Exit 2: fixedPhase="LIQUID", forbiddenPhase="HCP_A3", dir=-1
  │      │         (latter constrains the phase set change)
  │      │
  │      └─ Add line and node to diagram
  │
  │      [Continue main loop with next unvisited exit...]
  │
  │      Iteration 2: Follow exit (fixedPhase="HCP_A3", forbiddenPhase="LIQUID", dir=+1)
  │      │
  │      └─ [LineStepper follows this ZPF line...]
  │         (repeats until all exits visited or MAX_EXIT_ITERATIONS hit)
  │
  └─ Return PhaseDiagram object with all nodes and lines
```

---

### **LEVEL 4: Multi-Phase Equilibrium (Algorithm A)**

```
EquilibriumSolver.solve(T, P, compOverAll, candidates)
  │
  ├─ INITIALIZATION
  │  │
  │  ├─ GridMinimizer.initialize(candidates, T, P, compOverAll)
  │  │  ├─ For binary: evaluate G on 201-point grid for each phase
  │  │  ├─ Find convex hull lower envelope
  │  │  ├─ Detect common tangent for two-phase regions
  │  │  └─ Return EquilibriumState with estimated stable set, compositions, amounts
  │  │
  │  └─ state.T = T
  │     state.P = P
  │
  ├─ OUTER LOOP: Phase set iteration (up to 5 resets)
  │  │
  │  └─ for numPhaseSetResets = 0 to MAX_PHASE_SET_RESETS:
  │     │
  │     ├─ STEP 1: Solve initial phase fractions
  │     │  solveInitialFractions(state)
  │     │  ├─ Linear system: Σ f[ip]·x[ip] = compOverAll
  │     │  └─ Updates state.stablePhases[].amount
  │     │
  │     ├─ STEP 2: INNER LOOP: Newton iteration (up to 200 iterations)
  │     │  │
  │     │  for iter = 0 to MAX_ITERATIONS:
  │     │  │
  │     │  ├─ Update all phases via per-phase compute
  │     │  │  for each PhaseRecord in state.stablePhases():
  │     │  │  └─ pr.updateFromModel(T, P, 0, 0, state.mu)
  │     │  │     │
  │     │  │     └─ pr.lastCompute = RkPhaseModelAdapter.compute(T, P, pr.y, 0, 0, mu)
  │     │  │        ├─ Evaluate G(y, T)
  │     │  │        ├─ Compute Hessian, invert → eMat
  │     │  │        ├─ Compute composition response coefficients cG, cT, cP
  │     │  │        ├─ dely[j] = cG[j] + cT[j]·ΔT + cP[j]·ΔP + Σ eMat[j][k]·μ[k]
  │     │  │        └─ Return PhaseEquilData(G, dely, deln, x, eMat, cG, cT, cP, eList)
  │     │  │
  │     │  ├─ Assemble equilibrium matrix (c+p rows, c+p columns)
  │     │  │  assembleEquilibriumMatrix(state)
  │     │  │  │
  │     │  │  ├─ ROW BLOCK 1: Gibbs-Duhem equations (p rows)
  │     │  │  │  For each phase ip:
  │     │  │  │    rhs[ip] = -(G[ip] + Σ_A μ_A·x[ip][A])
  │     │  │  │    Jacobian vs μ: J[ip][A] = x[ip][A] + Σ_B μ_B·eMat[ip][A,B]
  │     │  │  │    Jacobian vs N: J[ip][nc+jp] = δ(ip,jp)
  │     │  │  │
  │     │  │  └─ ROW BLOCK 2: Mass balance equations (c rows)
  │     │  │     For each component A:
  │     │  │       rhs[nc+A] = -(Σ N[ip]·x[ip][A] - compOverAll[A])
  │     │  │       Jacobian vs μ: J[nc+A][B] = Σ_ip N[ip]·eMat[ip][A,B]
  │     │  │       Jacobian vs N: J[nc+A][nc+ip] = x[ip][A]
  │     │  │
  │     │  ├─ Solve (c+p)×(c+p) linear system via Matrix.solve()
  │     │  │  corrections = [Δμ[0..c-1], ΔN[0..p-1]]
  │     │  │
  │     │  ├─ DAMPED NEWTON STEP (critical for CVM/CEF convergence)
  │     │  │  lambda = 1.0
  │     │  │  for retry = 0 to 10:
  │     │  │    │
  │     │  │    ├─ Trial update:
  │     │  │    │  trialMu[A] = μ[A] + lambda·Δμ[A]
  │     │  │    │  trialN[ip] = N[ip] + lambda·ΔN[ip]
  │     │  │    │  trialY[ip][j] = y[ip][j] + lambda·dely[ip][j]
  │     │  │    │
  │     │  │    ├─ Validity check (gates acceptance):
  │     │  │    │  allValid = true
  │     │  │    │  for each phase ip:
  │     │  │    │    if NOT model[ip].isValid(trialY[ip]):
  │     │  │    │      allValid = false; break
  │     │  │    │    if trialN[ip] < -1e-10:
  │     │  │    │      allValid = false; break
  │     │  │    │
  │     │  │    ├─ If allValid:
  │     │  │    │  μ ← trialMu
  │     │  │    │  y[ip] ← trialY[ip] for all ip
  │     │  │    │  N[ip] ← trialN[ip] for all ip
  │     │  │    │  accepted = true
  │     │  │    │  break from retry loop
  │     │  │    │
  │     │  │    └─ Else:
  │     │  │       lambda ← lambda / 2.0  (halve damping)
  │     │  │
  │     │  ├─ If NOT accepted: converged = false; break inner loop
  │     │  │
  │     │  ├─ Check convergence: maxCorr = max(|λ·Δμ[A]|, |λ·ΔN[ip]|)
  │     │  │  if maxCorr < CONVERGED_TOL: break inner loop (converged)
  │     │  │
  │     │  └─ [Continue inner loop iteration...]
  │     │
  │     ├─ STEP 3: Phase set management (after Newton convergence)
  │     │  │
  │     │  ├─ Check for phases with negative amounts:
  │     │  │  for each stable phase ip:
  │     │  │    if N[ip] < MIN_AMOUNT: mark for removal
  │     │  │
  │     │  ├─ Check driving forces of metastable phases:
  │     │  │  for each metastable phase im:
  │     │  │    im.computeDrivingForce(μ)  ← γ = G + Σ μ_A·x_A
  │     │  │    if γ > DRIVING_FORCE_TOL: mark for addition
  │     │  │
  │     │  ├─ If phase set changed:
  │     │  │  Remove phases with negative amounts
  │     │  │  Add new phases from metastable set
  │     │  │  numPhaseSetResets++
  │     │  │  converged = false
  │     │  │  [continue outer loop with new phase set]
  │     │  │
  │     │  └─ Else:
  │     │     [phase set stable; outer loop exits]
  │     │
  │     └─ [Continue outer loop...]
  │
  ├─ BUILD RESULT
  │  ├─ Collect stable phases (amount > 0) as PhaseResult list
  │  ├─ Collect metastable phases (amount = 0) as PhaseResult list
  │  └─ Return EquilibriumResult(T, P, μ, stablePhases, metastablePhases, converged, totalIterations)
  │
  └─ [End EquilibriumSolver.solve()]
```

---

### **LEVEL 5: Per-Phase Computation (RK Model)**

```
RkPhaseModelAdapter.compute(T, P, y, ΔT, ΔP, μ)
  │
  ├─ Step 1: Evaluate Gibbs energy and derivatives
  │  ├─ G = RkGibbs.evaluate(y, T)
  │  ├─ Gx[] = RkGibbs.gradient(y, T)   [∂G/∂x_i]
  │  ├─ Gxx[][] = RkGibbs.hessian(y, T)  [∂²G/∂x_i∂x_j]
  │  ├─ GxT[] = RkGibbs.gradientDT(y, T)  [∂²G/∂x_i∂T]
  │  └─ GxP[] = zeros (no P-dependence in RK)
  │
  ├─ Step 2: Assemble and invert phase matrix
  │  ├─ M = [[Gxx, 1]    [nc+1 × nc+1 matrix]
  │  │        [1,   0]]
  │  ├─ M⁻¹ = Matrix.inverse(M)
  │  └─ eMat = M⁻¹[0:nc, 0:nc]   [composition response coefficients]
  │
  ├─ Step 3: Compute composition response coefficients
  │  ├─ cG[i] = -Σ eMat[i][k]·Gx[k]
  │  ├─ cT[i] = -Σ eMat[i][k]·GxT[k]
  │  └─ cP[i] = -Σ eMat[i][k]·GxP[k]  (zero for RK)
  │
  ├─ Step 4: Linearised composition change
  │  ├─ dely[i] = cG[i] + cT[i]·ΔT + cP[i]·ΔP + Σ eMat[i][j]·μ[j]
  │  └─ deln[i] = dely[i]   (same for RK; differs for CEF/CVM)
  │
  ├─ Step 5: Current composition
  │  └─ x[i] = y[i]   (identity for RK; differs for CEF/CVM)
  │
  └─ Return PhaseEquilData(G, dely, deln, x, eMat, cG, cT, cP, eList)
```

---

### **LEVEL 6: RkGibbs Evaluation (Thermodynamic Model)**

```
RkGibbs.evaluate(x, T)  [and gradient, hessian]
  │
  └─ G(x, T) = G₀(x, T) + G_id(x, T) + G_Em(x, T)
     │
     ├─ G₀(x, T) = Σ x_i · G0_i(T)    [from TdbUnaryGibbs SGTE polynomials]
     ├─ G_id(x, T) = R·T · Σ x_i · ln(x_i)    [ideal mixing]
     └─ G_Em(x, T) = Σ x_i·x_j·L_ij + Σ x_i·x_j·x_k·L_ijk + ...
        [excess: binary, ternary, quaternary RK interaction parameters]

  [All derivatives (∂G/∂x, ∂²G/∂x², ∂G/∂T, etc.) computed analytically]
```

---

### **LEVEL 7: Convert Result to DTO**

```
PhaseDiagramUseCase.convert(PhaseDiagram → PhaseDiagramResult)
  │
  ├─ For each DiagramLine in diagram:
  │  └─ Create LineSegment(coords, fixedPhase, stablePhases)
  │
  ├─ For each DiagramNode in diagram:
  │  ├─ Classify via Gibbs phase rule: f = n_axes - p + c
  │  │  (f ≤ 0 → INVARIANT; f > 0 → CROSSING; no exits → BOUNDARY)
  │  └─ Create NodePoint(axisValues, stablePhases, type)
  │
  └─ Return PhaseDiagramResult (ready for GUI rendering)
```

---

### **LEVEL 8: Render to GUI**

```
MainFrame.getGraphicsPanel()
  │
  └─ PhaseDiagramPanel.setDiagram(result)
     │
     ├─ Extract axis metadata (names, min/max, numAxes)
     ├─ Build colour palette for phase sets (8-colour cycle)
     ├─ Render axes with ticks and labels
     ├─ Render ZPF lines (colour-coded by stable phase set)
     │  └─ Path2D connects all sampled points
     └─ Render nodes (symbols):
        Circle: normal phase boundary crossing (f > 0)
        Square: invariant point (f = 0)
        Triangle: axis boundary endpoint
```

---

## Data Flow Summary

```
PhaseDiagramRequest
  ↓
[TDB loading] → tdb object
  ↓
[Phase model factory] → List<PhaseModelPort>
  ↓
[DiagramTracer (Algorithm B)]
  ├─ [GridMinimizer] → initial phase set estimate
  ├─ [EquilibriumSolver (Algorithm A)]
  │  ├─ [Newton iteration with damped step]
  │  └─ [Per-phase compute via RkGibbs]
  ├─ [LineStepper (Algorithm C1)]
  │  ├─ [Axis stepping + switching]
  │  └─ [Phase change detection]
  └─ [PhaseChangeHandler (Algorithm C2) + InvariantHandler (Algorithm D)]
     ├─ [Bisection to exact ZPF crossing]
     └─ [Exit topology determination]
     ↓
[PhaseDiagram (internal)]
  ↓
[Convert to DTO]
  ↓
PhaseDiagramResult
  ↓
[GUI Rendering]
  ↓
PhaseDiagramPanel (visual diagram)
```

---

## Summary

**Yes, the end-to-end workflow is complete and ready for use.**

The workflow follows this path:

1. **GUI Input** → PhaseDiagramRequest DTO
2. **Use Case** → TDB loading + phase model construction
3. **Algorithm B** (DiagramTracer) → orchestrates STEP/MAP calculation
4. **Algorithm A** (EquilibriumSolver) → multi-phase equilibrium at each point
5. **Algorithm C1** (LineStepper) → follows ZPF lines with axis switching
6. **Algorithm C2** (PhaseChangeHandler) → bisects to exact phase boundary
7. **Algorithm D** (InvariantHandler) → handles f=0 invariant points
8. **Result DTO** → PhaseDiagramResult (rendering-ready)
9. **GUI Rendering** → PhaseDiagramPanel displays the diagram

All 10 implementation phases are complete and integrated.
