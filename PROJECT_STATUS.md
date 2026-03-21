
**Last Updated:** Phase 13 (Logging Redesign - Complete)  
**Build Status:** ✅ Passing (Java 8, exit code 0)  
**Launch Status:** ✅ GUI launches without errors


## Current Development Phase

### Phase 13: Custom Hierarchical Logging System with Method Tracing (COMPLETE)

**Objective:** Replace standard 7 JUL levels with meaningful custom levels mapped to the codebase's 4-level method call hierarchy, with structured method enter/exit tracing.

**Status:** ✅ **COMPLETE** — All 4 levels wired, build passes, GUI launches.

#### Design Summary

Custom `AppLevel` class extends `java.util.logging.Level` with 7 meaningful levels:

| Level | Value | Purpose | Used By |
|-------|-------|---------|---------|
| ERROR | 1000 | Errors/exceptions | Unhandled failures |
| WARN | 900 | Warnings | Potential issues |
| RESULT | 800 | Computed results | Interior method results |
| FLOW | 700 | Control flow | L0–L1 entry/exit tracing |
| ENGINE | 500 | Core engine | L2 calculation core entry/exit |
| MODEL | 400 | Thermodynamic model | L3 model entry/exit tracing |
| SOLVER | 300 | Deep solver | L4 CVM solver entry/exit tracing |

**Trace Utility:** Static methods provide structured logging:

#### Wired Classes (By Level)

**L0 – GUI Entry (FLOW level)**
  - Methods: `runSinglePoint()`, `runOptimization()`, `runCalModel()`, `inspectModel()`

**L1 – Service Layer (FLOW level)**
  - Methods: `runCalculation()`, `runCalModel()`, `inspectModel()`
  - Methods: `runOptimization()`

**L2 – Calculation Engine (ENGINE level)**

**L3 – Thermodynamic Models (MODEL level)**
  - Methods: `calG()`, `calGm()`
  - Methods: `calG()`, `calGm()`
# Project Status: expCVM 10

## Current Status (March 2026)
- **Backend:**
  - TDB parser extracts all available elements and phases from the selected database file.
  - Backend exposes these lists to the GUI via `ModelInfo`.
- **Frontend (GUI):**
  - Input panel dynamically displays available elements and phases after TDB load.
  - Results window shows parsed elements and phases for user verification.
  - Method selection is restricted to implemented methods (HM, Gm, G).
- **Known Issues:**
  - None critical. If elements or phases do not appear, check TDB file integrity and logs.
- **Recent Fixes:**
  - Robust handling of empty element lists to avoid exceptions.
  - Results window now always shows parsed elements and phases.
- **Next Steps:**
  - Further UI polish and validation.
  - Add more user guidance and error handling as needed.
  - Methods: `calG()`

**L4 – Solver (SOLVER level)**
- `src/phase/solution/cecvm/CVMBINCE.java` — Base class for all CVM solvers
  - Methods: `calG()`, `calGm()`

#### GUI Enhancements

- **Single-tab layout** with embedded live log console
- **Log level selector** (dropdown) showing custom level names: ERROR, WARN, RESULT, FLOW, ENGINE, MODEL, SOLVER, ALL, OFF
- **Class name filter** for selective logging
- **Default level:** RESULT (shows computed values + method exit summaries)
- **Recommended debugging levels:**
  - FLOW → Follow GUI actions through services
  - ENGINE → Watch core calculation flow
  - MODEL → Trace thermodynamic model calls
  - SOLVER → Deep dive into CVM solver internals

#### Files Created/Modified

**Created:**
- `src/infrastructure/logging/AppLevel.java` (~55 lines) — Custom level definitions + parse()
- `src/infrastructure/logging/Trace.java` (~60 lines) — Structured tracing utility

**Modified:**
- `src/infrastructure/logging/LoggingConfig.java` — Per-package defaults (presentation=FLOW, application=FLOW, calculation=ENGINE, phase=MODEL, cecvm=SOLVER), fromLegacyLevel() mapper, setAllHandlerLevels() method
- `src/presentation/gui/views/MainFrame.java` — Level/filter combs updated, handler wiring to AppLevel
- `src/application/service/CalculationService.java` — Trace enter/exit, LOG.fine→AppLevel
- `src/application/service/OptimizationService.java` — Trace enter/exit, LOG.info→AppLevel
- `src/calbince/calculate.java` — Trace + Logger
- `src/calbince/CalModel.java` — Trace + Logger
- `src/calbince/OptMrq.java` — Trace + Logger
- `src/calbince/Methods.java` — Trace + Logger
- `src/phase/calphad/RK.java` — LOG.fine→AppLevel, Trace in calG()
- `src/phase/solution/calphad/RK.java` — Logger + Trace
- `src/phase/solution/calphad/STCOMP.java` — Logger + Trace
- `src/phase/solution/cecvm/CVMBINCE.java` — Logger + Trace

**Backward Compatibility:**
- Existing `Print.f()` debug calls auto-bridged to JUL via Print.java's bridge (→ AppLevel via fromLegacyLevel)
- Old `LOG.fine/info/warning` converted to AppLevel.RESULT/FLOW/WARN

---

## Prior Phases (Completed)

### Phases 1–6 (Earlier: GUI Implementation, Crash Fixes, Architecture)
- ✅ Single-tab Swing GUI with embedded log console
- ✅ Runtime crash fixes, value mapping
- ✅ Architecture compliance audit

### Phase 7–10 (Architecture Violation Remediation)
- ✅ Eliminated 4 major cross-layer dependencies
- ✅ Introduced clean architecture: presentation → application → domain
- ✅ Created service layer facades (CalculationService, OptimizationService)

### Phase 11 (JUL Infrastructure)
- ✅ Implemented `java.util.logging` throughout
- ✅ `LoggingConfig` central configuration
- ✅ Print.f() → JUL bridge for legacy code

### Phase 12 (Single-Tab GUI + JUL Logging UI)
- ✅ GUI restructured to single-panel layout
- ✅ JUL log console embedded in main window
- ✅ Level selector + filter combs

---

## Overall Codebase Status

### Infrastructure Layer (`src/infrastructure/`)
- ✅ Logging configuration and custom levels (Phase 13)
- ✅ Utilities: IO, printing, data readers
- ✅ Math libraries (Jama: matrix, decompositions)

### Presentation Layer (`src/presentation/gui/`)
- ✅ Single-panel Swing GUI with embedded logger
- ✅ MainController (FLOW-level tracing)
- ✅ Views: MainFrame, LogPanel

### Application Layer (`src/application/service/`)
- ✅ CalculationService (FLOW-level tracing)
- ✅ OptimizationService (FLOW-level tracing)

### Domain/Business Logic Layer (`src/application/domain/`, `src/calbince/`, `src/phase/`)
- ✅ `calculate.java` — Single-point engine (ENGINE-level tracing)
- ✅ `CalModel.java` — Model fitting (ENGINE-level tracing)
- ✅ `OptMrq.java` — Optimization (ENGINE-level tracing)
- ✅ `Methods.java` — Central dispatch (ENGINE-level tracing)
- ✅ Thermodynamic models: RK, STCOMP (MODEL-level tracing)
- ✅ CVM solvers: CVMBINCE, 15 concrete implementations (SOLVER-level tracing)

### Database Layer (`src/database/`)
- ✅ TDB parser and object model
- ✅ Standard state database interface

---

## Known Limitations / Next Steps

### Current Limitations
1. **Method Hierarchy Extensions:** L3/L4 contain only key entry methods; interior calculations (e.g., `calGderivatives`, CVM equilibrium iterations) use legacy Print.f bridge. This is intentional to avoid log spam; can be extended selectively.
2. **Per-Class Tuning:** Logging levels are currently per-package; may need per-class fine-tuning as development proceeds.
3. **No Async Architecture:** All logging is synchronous; high-frequency logging (deep solver iterations) may impact performance if set to SOLVER level.

### Recommended Next Steps
1. **Test Logging Hierarchy:** Run GUI with each level (FLOW → SOLVER) and verify trace messages appear as expected
2. **Add Selective Solver Tracing:** If needed, add Trace calls to CVM equilibrium methods (calGmc, calGmv, etc.) at SOLVER level
3. **Extend Model Tracing:** Add Trace to derivative methods (calGderivatives, etc.) if deeper model diagnostics needed
4. **Archive Old Print.f:** After verifying sufficient coverage, consider deprecating Print.f or consolidating remaining calls
5. **Logging Tests:** Add unit tests for AppLevel.parse(), Trace formatting, level filtering

---

## Build Instructions

### Quick Build & Run

```bash
# Compile
javac --release 8 -sourcepath src -d build/classes src/main/Main.java

# Run with GUI
java -cp build/classes main.Main --gui

# Run CLI only
java -cp build/classes main.Main
```

### Ant Build (NetBeans)

```bash
ant clean
ant jar
ant run
```

---

## File Manifest (Key Files)

### Logging Infrastructure
- `src/infrastructure/logging/AppLevel.java` — Custom JUL level definitions
- `src/infrastructure/logging/Trace.java` — Structured enter/exit tracing
- `src/infrastructure/logging/LoggingConfig.java` — Central configuration

### GUI
- `src/presentation/gui/views/MainFrame.java` — Main window with level/filter combs
- `src/presentation/gui/controllers/MainController.java` — Action handlers
- `src/presentation/gui/components/LogPanel.java` — Embedded log console

### Services
- `src/application/service/CalculationService.java`
- `src/application/service/OptimizationService.java`

### Calculation Core
- `src/calbince/calculate.java` — Single-point calculation entry
- `src/calbince/CalModel.java` — Model fitting
- `src/calbince/OptMrq.java` — Levenberg-Marquardt optimizer
- `src/calbince/Methods.java` — Central thermodynamic calculation dispatch

### Thermodynamic Models & Solvers
- `src/phase/calphad/RK.java` — Redlich-Kister (new)
- `src/phase/solution/calphad/RK.java` — Redlich-Kister (legacy)
- `src/phase/solution/calphad/STCOMP.java` — Stoichiometric phases
- `src/phase/solution/cecvm/CVMBINCE.java` — CVM solver base
- `src/phase/solution/cecvm/*BINCE.java` — 15 CVM implementations

### Database
- `src/database/tdb.java` — TDB parser
- `src/database/stdst.java` — Standard state database

---

## Contact / Notes

This logging system was designed to provide **meaningful tracing at multiple levels** without requiring external dependencies or sacrificing Java 8 compatibility. The 4-level method hierarchy maps cleanly to architectural layers, making it straightforward to increase verbosity from UI actions → services → core engines → models → solvers as needed for debugging.
