# PROJECT SPECIFICATION: expCVM 10

**Date:** 2026-03-21
**Version:** 2.0 (Post-Structure Simplification)
**Status:** ✅ Active Development
**Build:** ✅ Passing (Java 8, 0 errors, 3 warnings)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Project Overview](#2-project-overview)
3. [Architecture & Design Principles](#3-architecture--design-principles)
4. [Simplified Package Structure](#4-simplified-package-structure)
5. [Layer Responsibilities & Boundaries](#5-layer-responsibilities--boundaries)
6. [Ports & Adapters Pattern](#6-ports--adapters-pattern)
7. [Entry Points (GUI & CLI)](#7-entry-points-gui--cli)
8. [Domain Models & Value Objects](#8-domain-models--value-objects)
9. [Recent Changes: Structure Simplification (2026-03-21)](#9-recent-changes-structure-simplification-2026-03-21)
10. [Compilation & Build Instructions](#10-compilation--build-instructions)
11. [Development Status & Phases](#11-development-status--phases)
12. [Rules & Conventions](#12-rules--conventions)
13. [Future Enhancements](#13-future-enhancements)
14. [File Manifest](#14-file-manifest)

---

## 1. Executive Summary

**expCVM 10** is a scientific/engineering Java application for thermodynamic calculations and phase equilibrium modeling. It features:

- **Hybrid Clean Architecture + Domain-Driven Design**
- **Simplified, flat package structure** (8 top-level packages, max 2 levels deep)
- **Dual entry points**: GUI (Swing) and CLI
- **Physics engine**: Redlich-Kister (Calphad) and Cluster Variation Method (CVM) solvers
- **Database**: TDB parser for thermodynamic parameters
- **Java 8 compatible**, no external dependencies (except JAMA bundled)
- **Custom hierarchical logging** with method-level tracing

**Key Metrics:**
- 99 Java files (~25K LOC)
- 13 packages (gui, service, domain, infra, util, test, calbince, phase, database, main, + legacy)
- Compilation: ~2 seconds
- GUI launch: <1 second

---

## 2. Project Overview

### 2.1 Purpose

expCVM 10 provides a computational engine for:
- **Single-point equilibrium calculations** (given T, P, X)
- **Phase diagram mapping** (vary one/two variables, compute equilibrium)
- **Parameter optimization** (fit thermodynamic model coefficients to experimental data)
- **Model validation** (compare calculated vs experimental properties)

### 2.2 Technology Stack

| Component | Technology | Notes |
|-----------|-----------|-------|
| **Language** | Java 8 | Source target: Java 8, compiled with `--release 8` |
| **UI Framework** | Swing | Standard JDK Swing, no external UI libraries |
| **Build Tool** | Ant | NetBeans integration (`nbproject/build.xml`) |
| **Logging** | JUL (java.util.logging) | Custom `AppLevel` hierarchy + JUL config |
| **Math Library** | JAMA (bundled) | Matrix operations, decompositions (19 classes in `src/util/`) |
| **Database Format** | TDB (OpenCalphad) | Plain-text thermodynamic database files |

### 2.3 Code Organization

```
src/
├── main/          Composition root (wiring only)
├── gui/           Presentation layer (Swing GUI + CLI)
├── service/       Application layer (use-cases, orchestration)
├── domain/        Domain layer (models, ports, value objects)
├── infra/         Infrastructure layer (adapters, logging, parsing)
├── util/          Shared utilities (math, IO, formatting)
├── test/          Tests (unit, integration)
├── calbince/      Legacy calculation engine (UNCHANGED)
├── phase/         Legacy phase models (UNCHANGED)
└── database/      Legacy TDB database handler (UNCHANGED)
```

**Total files affected by structure simplification (2026-03-21):** 59 files (package/import changes only)
**Logic changes:** 0 (pure refactoring)

---

## 3. Architecture & Design Principles

### 3.1 Design Style

**Hybrid Clean Architecture + Domain-Driven Design**, adapted for scientific/engineering codebases with dual entry points.

**Core Principles:**

1. **One-way dependency rule** — outer layers depend on inner layers only; never the reverse.
2. **Domain at the centre** — thermodynamic models, value objects, and domain interfaces have zero knowledge of UI, persistence, or framework.
3. **Ports & Adapters** — domain declares interfaces (ports); infrastructure/presentation provide implementations (adapters).
4. **Two entry points, one core** — GUI and CLI are independent presentation adapters sharing the same application and domain layers.
5. **Testability first** — every layer independently testable by injecting mock adapters through ports.

### 3.2 Canonical Layer Structure

```
┌─────────────────────────────────────────────────┐
│                    shared                        │  pure math / string / IO helpers
├─────────────────────────────────────────────────┤
│                    domain                        │  models, value objects, ports
├─────────────────────────────────────────────────┤
│                 application                      │  use-cases, orchestration
├──────────────┬──────────────┬───────────────────┤
│ infrastructure│  presentation │     test          │  adapters, UI, tests
│  (adapters)  │  (GUI / CLI) │                   │
└──────────────┴──────────────┴───────────────────┘
```

| Layer            | Responsibility                                                       | May Import                         | Must NOT Import |
|------------------|----------------------------------------------------------------------|------------------------------------|----|
| **shared**       | Pure utilities (matrix, formatting, math) — no domain types          | JDK, third-party libs only         | Any layer |
| **domain**       | Thermodynamic models, value objects, domain interfaces (ports)       | `shared`                           | application, infrastructure, presentation |
| **application**  | Use-case coordinators, workflow services, DTOs                       | `domain`, `shared`                 | infrastructure (via ports only), presentation |
| **infrastructure** | TDB parsing, file IO, logging adapters, port implementations       | `domain`, `shared`                 | application, presentation |
| **presentation** | GUI (Swing/JavaFX) and CLI entry points, view-models, controllers   | `application`, `domain`, `shared`  | infrastructure |
| **test**         | Unit, integration, and characterization tests                        | all layers                         | — |

---

## 4. Simplified Package Structure

### 4.1 Before vs After (2026-03-21)

**Before:** Deeply nested (4–6 sub-levels)
```
src/presentation/gui/controllers/
src/presentation/gui/views/
src/presentation/gui/theme/
src/presentation/cli/
src/application/service/
src/application/calculation/
src/application/assessment/
src/application/dto/
src/infrastructure/logging/
src/infrastructure/parser/
... (20+ sub-folders total)
```

**After:** Flat top-level packages (max 2 levels)
```
src/gui/
src/service/
src/domain/
src/infra/
src/util/
src/test/
```

### 4.2 Current Structure

```
src/
├── main/                          # Composition root (wiring only)
│   └── Main.java                  #   builds object graph, selects entry point
│
├── gui/                           # PRESENTATION layer (5 files)
│   ├── CliApp.java                #   CLI entry point and command handlers
│   ├── GuiApp.java                #   GUI launcher (Swing)
│   ├── MainController.java        #   GUI event handler / controller
│   ├── MainFrame.java             #   Main GUI window (1100 LOC)
│   └── DarkTheme.java             #   VS Code dark theme for Swing
│
├── service/                       # APPLICATION layer (11 files)
│   ├── CalculationService.java    #   wraps calculate/CalModel logic
│   ├── OptimizationService.java   #   wraps OptMrq/Mrqcof
│   ├── SinglePointUseCase.java    #   use-case: single-point calculation
│   ├── StepCalculationUseCase.java#   use-case: step-by-step calculation
│   ├── MapCalculationUseCase.java #   use-case: phase map generation
│   ├── FitParametersUseCase.java  #   use-case: parameter fitting
│   ├── ValidateModelUseCase.java  #   use-case: model validation
│   ├── ExportDatabaseUseCase.java #   use-case: database export
│   ├── CalculationRequest.java    #   DTO: input parameters
│   ├── CalculationResult.java     #   DTO: calculation output
│   └── ModelInfo.java             #   DTO: model metadata
│
├── domain/                        # DOMAIN layer (8 files)
│   ├── ThermoCondition.java       #   immutable (T, P, X[], …)
│   ├── ThermoResult.java          #   calculation output value object
│   ├── DatabasePort.java          #   load/query thermodynamic parameters
│   ├── PhaseFactory.java          #   construct phase model by identifier
│   ├── DatasetPort.java           #   load experimental/phase datasets
│   ├── LoggingPort.java           #   logging operations interface
│   ├── OptimizationOutputPort.java#   optimization output factory interface
│   └── ResultPort.java            #   write/export results
│
├── infra/                         # INFRASTRUCTURE layer (10 files)
│   ├── TdbParser.java             #   implements DatabasePort
│   ├── PhaseFactoryImpl.java       #   implements PhaseFactory
│   ├── ExptDataReader.java        #   implements DatasetPort
│   ├── TdbExporter.java           #   implements ResultPort
│   ├── LoggingConfig.java         #   central logging configuration
│   ├── AppLevel.java              #   custom log levels (ERROR, WARN, RESULT, FLOW, ENGINE, MODEL, SOLVER)
│   ├── Trace.java                 #   method entry/exit tracing utility
│   ├── LogAdapter.java            #   logging adapter
│   ├── ConsoleLogger.java         #   implements LoggingPort
│   └── OptimizationOutputAdapter.java  #   optimization output adapter
│
├── util/                          # SHARED layer (26 files)
│   ├── Print.java                 #   legacy print utility (bridged to JUL)
│   ├── DataPrinter.java           #   file output utility
│   ├── DataReader.java            #   file input utility
│   ├── Format.java                #   number formatting
│   ├── PrintfFormat.java          #   printf-style formatting (3098 LOC)
│   ├── Utils.java                 #   generic utilities
│   ├── Ftest.java                 #   F-test statistics
│   ├── Matrix.java & JAMA classes │   matrix operations, decompositions (19 files)
│   └── ...
│
├── test/                          # TEST layer (4 files)
│   ├── UseCaseTest.java           #   use-case level tests
│   ├── ThermoConditionTest.java   #   ThermoCondition tests
│   ├── ThermoResultTest.java      #   ThermoResult tests
│   └── TdbParserTest.java         #   TDB parser tests
│
├── calbince/                      # LEGACY: Calculation Engine (13 files, UNCHANGED)
│   ├── calculate.java             #   core property calculation routine (788 LOC)
│   ├── CalModel.java              #   model validation runner (408 LOC)
│   ├── Methods.java               #   calculation methods/functions (3619 LOC)
│   ├── OptMrq.java                #   Levenberg-Marquardt optimization (318 LOC)
│   ├── PhaseData.java             #   phase data parser & manager (961 LOC)
│   ├── ExptData.java              #   experimental data container (488 LOC)
│   ├── ExptDatum.java             #   single experimental data point (153 LOC)
│   ├── CalVars.java               #   calculation sets container (294 LOC)
│   ├── CalcSet.java               #   condition set for element list (79 LOC)
│   ├── CalcType.java              #   condition type (method + phases) (85 LOC)
│   ├── Condition.java             #   external conditions (T, P, X) (118 LOC)
│   ├── GetNumData.java            #   data point counter (59 LOC)
│   └── Mrqcof.java                #   (empty stub)
│
├── phase/                         # LEGACY: Phase Models (24 files, UNCHANGED)
│   ├── GibbsModel.java            #   abstract Gibbs energy model (281 LOC)
│   ├── CECVM.java                 #   abstract CECVM base class (928 LOC)
│   ├── PHASEBINCE.java            #   phase interface/contract (110 LOC)
│   ├── calphad/RK.java            #   Redlich-Kister Calphad model (155 LOC)
│   ├── solution/calphad/RK.java   #   full RK Calphad solver (677 LOC)
│   ├── solution/calphad/STCOMP.java # stoichiometric compound (381 LOC)
│   ├── solution/cecvm/CVMBINCE.java  #   core CVM solver (3007 LOC)
│   ├── solution/cecvm/*BINCE.java    #   15 CVM solution implementations
│   └── (other variants)
│
└── database/                      # LEGACY: Thermodynamic Database (3 files, UNCHANGED)
    ├── tdb.java                   #   TDB parser & database handler (1872 LOC)
    ├── sgte.java                  #   SGTE database interface (477 LOC)
    └── stdst.java                 #   standard state calculations (44 LOC)
```

### 4.3 Metrics

| Metric | Value |
|--------|-------|
| **Top-level packages** | 8 (gui, service, domain, infra, util, test, + 2 legacy: calbince, phase, database, main) |
| **Max folder depth** | 2 levels (e.g., `src/gui/MainFrame.java`) |
| **Sub-folders removed** | ~20 |
| **Total files refactored** | 59 (package/import changes only) |
| **Total files unchanged** | 40 (legacy physics engine) |
| **Total Java files** | 99 |
| **Total lines of code** | ~25,000 |

---

## 5. Layer Responsibilities & Boundaries

### 5.1 gui/ (PRESENTATION Layer)

**Responsibility:** Handle user interactions via Swing GUI or CLI.

**Files:**
- `CliApp.java` — CLI mode entry point
- `GuiApp.java` — GUI launcher
- `MainFrame.java` — Main Swing window (1100 LOC, comprehensive UI)
- `MainController.java` — Event handlers, bind UI to use-cases
- `DarkTheme.java` — VS Code-style dark theme (260 lines)

**Imports:** `application` (use-cases via service), `domain` (value objects), `shared`
**Must NOT import:** `infrastructure` directly (data flows through application layer)

---

### 5.2 service/ (APPLICATION Layer)

**Responsibility:** Orchestrate workflows and use-cases. Coordinate between presentation and domain layers.

**Core Classes:**
- `CalculationService.java` — Single-point and phase-map calculations
- `OptimizationService.java` — Parameter fitting via Levenberg-Marquardt
- `*UseCase.java` — Individual workflow implementations

**DTOs (Data Transfer Objects):**
- `CalculationRequest` — Input parameters (T, P, X, method, phases, db path)
- `CalculationResult` — Output values (G, H, S, Cp, derivatives)
- `ModelInfo` — Parsed model metadata (element list, phase list)

**Imports:** `domain` (ports, models), `shared`
**Must NOT import:** `infrastructure` (via ports only), `presentation`

---

### 5.3 domain/ (DOMAIN Layer)

**Responsibility:** Define thermodynamic models, value objects, and port interfaces.

**Value Objects (Immutable):**
- `ThermoCondition` — immutable state (T, P, X[], phases)
- `ThermoResult` — immutable output (G, H, S, Cp, derivatives)

**Port Interfaces (Contracts for adapters):**
- `DatabasePort` — load/query thermodynamic parameters
- `PhaseFactory` — construct phase models by identifier
- `DatasetPort` — load experimental/phase datasets
- `LoggingPort` — logging operations
- `OptimizationOutputPort` — optimization output factory
- `ResultPort` — write/export results

**Imports:** `shared` only
**Must NOT import:** `application`, `infrastructure`, `presentation`

---

### 5.4 infra/ (INFRASTRUCTURE Layer)

**Responsibility:** Provide adapters that implement domain ports. Handle external data, parsing, logging, and file IO.

**Adapter Implementations:**
- `TdbParser.java` — implements `DatabasePort`, parses OpenCalphad TDB files
- `PhaseFactoryImpl.java` — implements `PhaseFactory`, constructs phase models
- `ExptDataReader.java` — implements `DatasetPort`, loads experimental datasets
- `TdbExporter.java` — implements `ResultPort`, exports results
- `ConsoleLogger.java` — implements `LoggingPort`, logs to console + file

**Logging Infrastructure:**
- `LoggingConfig.java` — central JUL configuration
- `AppLevel.java` — custom log level definitions (ERROR, WARN, RESULT, FLOW, ENGINE, MODEL, SOLVER)
- `Trace.java` — structured method entry/exit tracing
- `LogAdapter.java` — legacy print bridge to JUL

**Imports:** `domain` (ports), `shared`
**Must NOT import:** `application`, `presentation`

---

### 5.5 util/ (SHARED Layer)

**Responsibility:** Provide pure utilities with no domain/application/infrastructure knowledge.

**Math & Decompositions (JAMA, 19 files):**
- Matrix operations, LU/Cholesky/QR/SVD decompositions, eigenvalue solvers
- Generic utilities for optimization and linear algebra

**IO & Formatting:**
- `Print.java` — legacy print utility (auto-bridged to JUL)
- `DataPrinter.java` — file output
- `DataReader.java` — file input
- `Format.java` — number/unit formatting
- `Utils.java` — generic helpers

**Statistics:**
- `Ftest.java` — F-test calculations

**Imports:** JDK, third-party libs only
**Must NOT import:** Any layer's data models

---

### 5.6 Legacy Packages (UNCHANGED)

**calbince/** — Calculation engine (13 files, 9,382 LOC)
- Core calculation: `calculate.java`
- Model fitting: `CalModel.java`, `OptMrq.java`
- Central dispatch: `Methods.java`
- Data containers: `Condition.java`, `CalcSet.java`, etc.

**phase/** — Phase models (24 files, 8,726 LOC)
- Base: `GibbsModel.java` (abstract Gibbs energy model)
- CECVM: `CECVM.java` base + 15 concrete implementations
- Calphad: `calphad/RK.java`, `solution/calphad/RK.java`, etc.

**database/** — TDB database (3 files, 2,393 LOC)
- TDB parser and object model
- Standard state calculations

**Strategy:** Left untouched to minimize risk. Legacy classes import adapted through infra adapters where possible.

---

## 6. Ports & Adapters Pattern

### 6.1 Port Definitions

Ports are interfaces defined in `domain/`. They specify contracts for external services without specifying implementations.

| Port | Purpose | Adapter Implementation |
|------|---------|------------------------|
| `DatabasePort` | Load/query thermodynamic parameters from TDB file | `infra.TdbParser` |
| `PhaseFactory` | Construct phase model by name/type identifier | `infra.PhaseFactoryImpl` |
| `DatasetPort` | Load experimental and phase datasets | `infra.ExptDataReader` |
| `LoggingPort` | Handle logging operations (write, level control) | `infra.ConsoleLogger` |
| `OptimizationOutputPort` | Factory for optimization output writers | `infra.OptimizationOutputAdapter` |
| `ResultPort` | Export results and database files | `infra.TdbExporter` |

### 6.2 Injection Pattern

All ports are injected via **constructor dependency injection** at the composition root (`main.Main`):

```java
// main/Main.java (composition root)
public static void main(String[] args) {
    // Create adapters (implementations)
    DatabasePort dbPort = new TdbParser();
    PhaseFactory phaseFactory = new PhaseFactoryImpl();
    LoggingPort logger = new ConsoleLogger();
    OptimizationOutputPort optOutput = new OptimizationOutputAdapter();

    // Create services, inject ports
    CalculationService calcService = new CalculationService(dbPort, phaseFactory, logger);
    OptimizationService optService = new OptimizationService(logger, optOutput);

    // Select entry point
    if (args.length > 0 && args[0].equals("--gui")) {
        new GuiApp(calcService, optService, ...);
    } else {
        new CliApp(calcService, optService, ...);
    }
}
```

### 6.3 Testing Benefit

Services can be tested in isolation by injecting mock adapters:

```java
@Test
public void testCalculation() {
    MockDatabasePort mockDb = new MockDatabasePort();
    CalculationService calc = new CalculationService(mockDb, ...);
    // Test with controlled data, no file IO
    CalculationResult result = calc.runCalculation(...);
    assertEquals(expected, result.getGibbs());
}
```

---

## 7. Entry Points (GUI & CLI)

### 7.1 Composition Root: `main.Main`

Single entry point that:
1. Parses command-line arguments (`--gui` or `--cli`, default CLI)
2. Instantiates infrastructure adapters
3. Instantiates application use-cases, injecting adapters
4. Launches selected presentation adapter (GUI or CLI)

```
main.Main
├── creates infrastructure adapters (implement domain ports)
├── creates application use-cases (accept ports via constructor)
├── creates domain models/value objects
└── selects entry point:
    ├── if --gui → launches gui.GuiApp(useCases)
    └── if --cli → launches gui.CliApp(useCases)
```

### 7.2 GUI Entry Point: `gui.GuiApp`

- Receives configured use-case objects
- Launches Swing window (`MainFrame`)
- Wires event handlers (`MainController`) to use-case calls
- Displays results in embedded panels and tables
- Embedded live log console with level selector

**Features:**
- Single-panel layout with splitters
- Dark theme (VS Code-inspired, #1E1E1E background)
- Real-time log display + filtering
- Model inspection and result summary

### 7.3 CLI Entry Point: `gui.CliApp`

- Receives configured use-case objects
- Parses CLI arguments / reads config files for parameters
- Calls use-case methods
- Prints results to stdout or file
- No GUI dependencies

---

## 8. Domain Models & Value Objects

### 8.1 ThermoCondition (Immutable State)

Represents thermodynamic conditions for calculation.

```java
public class ThermoCondition {
    private final double temperature;      // K
    private final double pressure;         // Pa
    private final double[] composition;    // mole fractions
    private final String[] phaseIdentifiers;
}
```

**Usage:** Passed to `GibbsModel.calG()`, `calGm()`, etc.

### 8.2 ThermoResult (Immutable Output)

Represents calculated properties.

```java
public class ThermoResult {
    private final double G;                // Gibbs energy
    private final double H;                // Enthalpy
    private final double S;                // Entropy
    private final double Cp;               // Heat capacity
    private final double[] activities;     // Component activities
    private final double[][] derivatives;  // Partial derivatives
}
```

**Usage:** Returned from calculation use-cases, displayed in GUI.

### 8.3 ModelInfo (Metadata)

Parsed metadata from TDB file.

```java
public class ModelInfo {
    private final String[] elements;
    private final String[] phases;
    private final String databasePath;
}
```

**Usage:** Displayed in GUI input panel; used to validate user selections.

---

## 9. Recent Changes: Structure Simplification (2026-03-21)

### 9.1 What Changed

Simplified Java package structure by **flattening 4 nested architecture layers** into flat top-level packages.

**Transformation:**
- **Before:** 10 top-level packages with 2–6 sub-levels each → 20+ sub-folders
- **After:** 8 flat top-level packages (max 2 levels deep)

### 9.2 Files Moved (59 refactored)

| Package | Before | After | Files |
|---------|--------|-------|-------|
| **gui/** | `presentation/{cli,gui/{controllers,theme,views}}` | `gui/` | 5 |
| **service/** | `application/{service,calculation,assessment,dto}` | `service/` | 11 |
| **domain/** | `domain/{model,port}` | `domain/` | 8 |
| **infra/** | `infrastructure/{logging,parser,factory,dataset,export,output}` | `infra/` | 10 |
| **util/** | `utils/{io,jama}` | `util/` | 26 |
| **test/** | `test/{application,domain,infrastructure}` | `test/` | 4 |
| **Legacy** | `calbince`, `phase`, `database` | unchanged | 40 |

### 9.3 Package Name Mapping

```
presentation.cli          → gui
presentation.gui.*        → gui
application.service       → service
application.calculation   → service
application.assessment    → service
application.dto           → service
domain.model              → domain
domain.port               → domain
infrastructure.logging    → infra
infrastructure.parser     → infra
infrastructure.factory    → infra
infrastructure.dataset    → infra
infrastructure.export     → infra
infrastructure.output     → infra
utils.io                  → util
utils.jama                → util
test.application          → test
test.domain               → test
test.infrastructure       → test
```

### 9.4 Cross-Package Imports Updated

**In unchanged legacy packages** (calbince, phase, database):
- `utils.io.*` → `util.*`
- `utils.jama.*` → `util.*`
- `infrastructure.logging.*` → `infra.*`
- `domain.model.*` → `domain.*`
- `domain.port.*` → `domain.*`

### 9.5 Verification

✅ **Compilation:** `javac --release 8` → 0 errors, 3 warnings (expected JDK deprecation notices)

✅ **CLI Test:** Ran default calculation successfully
```
Result: Calculation completed.
Computed value: -4,567.151
#Calculations took 0.06 sec
```

✅ **File Integrity:** All 99 Java files accounted for
- 59 refactored (package/import changes only, no logic changes)
- 40 legacy unchanged (calbince + phase + database)

### 9.6 Benefits

| Aspect | Improvement |
|--------|-------------|
| **Folder Depth** | 4–5 levels → 2 levels max |
| **Sub-folder Count** | 20+ → 0 |
| **Package Imports** | Shorter, clearer names |
| **File Discovery** | Faster (all top-level packages visible) |
| **Maintenance** | Easier (simpler hierarchy) |
| **Compilation** | Same (no logic changes) |
| **Runtime** | Same (no behavioral changes) |

---

## 10. Compilation & Build Instructions

### 10.1 Command-Line Compilation

```bash
# Compile (Java 8 target)
javac --release 8 -sourcepath src -d build/classes $(find src -name "*.java")

# Or: Compile single entry point (pulls dependencies)
javac --release 8 -sourcepath src -d build/classes src/main/Main.java
```

**Expected Output:**
```
Note: src/.../some/Class.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
```
(3 warnings expected; these are JDK internal deprecations, not errors)

### 10.2 Run CLI Mode

```bash
# Single-point calculation (default)
java -cp build/classes main.Main

# With options (parsed by CliApp)
java -cp build/classes main.Main --method HM --db path/to/db.tdb
```

### 10.3 Run GUI Mode

```bash
# Launch Swing GUI
java -cp build/classes main.Main --gui
```

### 10.4 Ant Build (NetBeans)

```bash
# Clean and build
ant clean
ant jar

# Run (selects based on project settings)
ant run
```

### 10.5 Build Output

- **Classes:** `build/classes/` (user-compiled)
- **Jar:** `dist/expcvm10.jar` (ant target)
- **Logs:** `data/expcvm.log` (at runtime)

---

## 11. Development Status & Phases

### 11.1 Current Phase: Phase 13 (Logging Redesign) — COMPLETE ✅

**Objective:** Custom hierarchical logging system with method-level tracing.

**Status:** All 4 levels wired, build passes, GUI launches.

**Custom Log Levels:**

| Level | Value | Purpose | Used By |
|-------|-------|---------|---------|
| ERROR | 1000 | Errors/exceptions | Unhandled failures |
| WARN | 900 | Warnings | Potential issues |
| RESULT | 800 | Computed results | Interior method results |
| FLOW | 700 | Control flow | L0–L1 entry/exit (GUI/Service) |
| ENGINE | 500 | Core engine | L2 calculation core (calbince) |
| MODEL | 400 | Model evaluation | L3 thermodynamic models (phase) |
| SOLVER | 300 | Deep solver | L4 CVM solver internals |

**Tracing Utility:**
```java
// In methods
Trace.enter(LOG, "methodName", params);
// ... method body
Trace.exit(LOG, "methodName", result);
```

**GUI Features:**
- Dropdown selector for log level (ERROR → FLOW → ENGINE → MODEL → SOLVER → ALL → OFF)
- Class name filter for selective logging
- Default level: RESULT (shows computed values + method exits)
- Embedded live log console in main window

### 11.2 Prior Completed Phases

| Phase | Objective | Status |
|-------|-----------|--------|
| **1–6** | GUI implementation, crash fixes, architecture | ✅ Complete |
| **7–10** | Architecture violation remediation, clean architecture | ✅ Complete |
| **11** | JUL logging infrastructure | ✅ Complete |
| **12** | Single-tab GUI + JUL logging UI | ✅ Complete |
| **13** | Custom hierarchical logging with tracing | ✅ Complete |

### 11.3 Overall Codebase Health

| Layer | Status | Notes |
|-------|--------|-------|
| **Infrastructure** | ✅ Complete | Logging, parsing, adapters |
| **Presentation** | ✅ Complete | Swing GUI + CLI with dark theme |
| **Application** | ✅ Complete | Use-cases with port injection |
| **Domain** | ✅ Complete | Value objects, ports defined |
| **Shared** | ✅ Complete | Math, IO, utilities |
| **Legacy** | ✅ Complete | Physics engine stable |

---

## 12. Rules & Conventions

### 12.1 Hard Boundary Rules

1. **domain** MUST NOT import from application, infrastructure, or presentation.
2. **application** MUST NOT import from presentation.
3. **infrastructure** MUST NOT import from application or presentation.
4. **presentation** MUST NOT import from infrastructure — all data flows through application.
5. **shared** MUST NOT import any layer's data models.
6. **main** (composition root) is the only place that may import from all layers.
7. New parsing/file-system classes belong in infrastructure, not domain.
8. New thermodynamic models/formulas belong in domain, not infrastructure or application.
9. New workflow/use-case coordinators belong in application.
10. UI components (frames, panels, event handlers) belong in presentation, never in application or below.

### 12.2 Naming Conventions

1. **Class names:** Java PascalCase (e.g., `TdbParser`, not `tdb`)
2. **Method names:** camelCase
3. **Constants:** UPPER_SNAKE_CASE
4. **One responsibility per class**
5. **Suffix conventions:**
   - Use-cases: `*UseCase` (e.g., `SinglePointUseCase`)
   - Ports: `*Port` or descriptive (e.g., `DatabasePort`, `PhaseFactory`)
   - Adapters: `*Impl` or descriptive (e.g., `TdbParser`, `ConsoleLogger`)
   - Tests: `*Test` (e.g., `ThermoConditionTest`)

### 12.3 Package Placement Rules

Before adding any file, answer:

1. Is it a thermodynamic model/formula? → `domain`
2. Is it a domain value object (state, condition, result)? → `domain`
3. Is it a domain port (external service interface)? → `domain`
4. Is it use-case orchestration? → `service`
5. Is it file/parsing/external IO? → `infra`
6. Is it a GUI component/controller? → `gui`
7. Is it a CLI command handler? → `gui`
8. Is it startup wiring only? → `main`
9. Is it a generic math/string/IO utility (no domain types)? → `util`

If a class matches multiple categories, split it into separate classes by layer.

### 12.4 PR / Commit Checklist

Before merging any change:

- [ ] Identified target layer and package before creating file
- [ ] Verified imports follow the allowlist matrix
- [ ] Confirmed no boundary-forbidden import introduced
- [ ] Thermodynamic formulas only in `domain`
- [ ] Parsing/IO only in `infrastructure`
- [ ] UI code only in `presentation`
- [ ] Updated ARCHITECTURE.md if new top-level package or port introduced
- [ ] Tests pass and cover changed code paths

---

## 13. Future Enhancements

### 13.1 Short-Term (Within Current Phase)

1. **Extend Solver Tracing:** Add `Trace` calls to CVM equilibrium methods (calGmc, calGmv) at SOLVER level for deeper diagnostics
2. **Selective Logging Tests:** Add unit tests for `AppLevel.parse()`, `Trace` formatting, level filtering
3. **Performance Profiling:** Monitor high-frequency logging impact (deep solver iterations) at SOLVER level
4. **Archive Print.f():** After verifying sufficient coverage, consolidate remaining `Print.f()` calls

### 13.2 Medium-Term (Next Architecture Phases)

1. **Phase Model Migration:** Move `phase/**` and `calbince/**` classes to domain layer under `domain.phase` and `domain.engine` per ARCHITECTURE.md Step 1
2. **Parser Extraction:** Continue moving file-IO logic from `calbince` to `infra` per ARCHITECTURE.md Step 3
3. **Dedicated Shared Package:** Create `src/shared/` for pure math/IO utilities (currently in `util/`)
4. **Additional Ports:** Define ports for:
   - `PhaseDataPort` — manage phase input data
   - `ExperimentalDataPort` — manage experimental datasets
   - `ResultExportPort` — flexible result output formats

### 13.3 Long-Term (Future Roadmap)

1. **Performance Optimization:** Profile and optimize hot paths in CVM solver
2. **Extended Thermodynamic Models:** Add additional phase models (sublattice, etc.)
3. **Parallel Calculations:** Support batch phase-map calculations with executor service
4. **Database Extensions:** Support additional TDB formats (TCS JSON, custom formats)
5. **Visualization:** Add phase diagram rendering (plot, contours)

---

## 14. File Manifest

### 14.1 Key Architecture Files

| File | Purpose | Lines |
|------|---------|-------|
| `ARCHITECTURE.md` | Architecture definition, layer rules | 462 |
| `STRUCTURE_SIMPLIFICATION.md` | Structure refactoring (2026-03-21) | 244 |
| `PROJECT_STATUS.md` | Development phases and status | 240 |
| `README.md` | User documentation | 150 |
| **PROJECT_SPECIFICATION.md** | **THIS FILE** | — |

### 14.2 Composition Root

| File | Purpose | Lines |
|------|---------|-------|
| `src/main/Main.java` | Wire dependencies, select entry point | 57 |

### 14.3 Presentation Layer

| File | Purpose | Lines |
|------|---------|-------|
| `src/gui/GuiApp.java` | GUI launcher | 44 |
| `src/gui/CliApp.java` | CLI entry point | 123 |
| `src/gui/MainFrame.java` | Main Swing window | 1100 |
| `src/gui/MainController.java` | GUI event handlers | 108 |
| `src/gui/DarkTheme.java` | VS Code dark theme | 260 |

### 14.4 Application Layer

| File | Purpose | Lines |
|------|---------|-------|
| `src/service/CalculationService.java` | Calculation orchestration | 205 |
| `src/service/OptimizationService.java` | Optimization orchestration | 74 |
| `src/service/SinglePointUseCase.java` | Single-point calculation | 45 |
| `src/service/StepCalculationUseCase.java` | Step-by-step calculation | 40 |
| `src/service/MapCalculationUseCase.java` | Phase map generation | 42 |
| `src/service/FitParametersUseCase.java` | Parameter fitting | 25 |
| `src/service/ValidateModelUseCase.java` | Model validation | 25 |
| `src/service/ExportDatabaseUseCase.java` | Database export | 25 |
| `src/service/CalculationRequest.java` | Input DTO | 45 |
| `src/service/CalculationResult.java` | Output DTO | 50 |
| `src/service/ModelInfo.java` | Metadata DTO | 35 |

### 14.5 Domain Layer

| File | Purpose | Lines |
|------|---------|-------|
| `src/domain/ThermoCondition.java` | Immutable state | 55 |
| `src/domain/ThermoResult.java` | Immutable output | 52 |
| `src/domain/DatabasePort.java` | Database port interface | 20 |
| `src/domain/PhaseFactory.java` | Phase factory port interface | 15 |
| `src/domain/DatasetPort.java` | Dataset port interface | 18 |
| `src/domain/LoggingPort.java` | Logging port interface | 15 |
| `src/domain/OptimizationOutputPort.java` | Output port interface | 20 |
| `src/domain/ResultPort.java` | Result export port interface | 18 |

### 14.6 Infrastructure Layer

| File | Purpose | Lines |
|------|---------|-------|
| `src/infra/TdbParser.java` | TDB file parser (DatabasePort impl) | 180 |
| `src/infra/PhaseFactoryImpl.java` | Phase constructor factory | 140 |
| `src/infra/ExptDataReader.java` | Experimental data reader | 80 |
| `src/infra/TdbExporter.java` | TDB export utility | 85 |
| `src/infra/ConsoleLogger.java` | Console logger (LoggingPort impl) | 75 |
| `src/infra/LoggingConfig.java` | Central logging configuration | 120 |
| `src/infra/AppLevel.java` | Custom log levels | 95 |
| `src/infra/Trace.java` | Structured tracing utility | 75 |
| `src/infra/LogAdapter.java` | Legacy Print → JUL bridge | 50 |
| `src/infra/OptimizationOutputAdapter.java` | Optimization output adapter | 85 |

### 14.7 Legacy Calculation Engine

| File | Purpose | Lines |
|------|---------|-------|
| `src/calbince/calculate.java` | Single-point calculation | 788 |
| `src/calbince/CalModel.java` | Model fitting | 408 |
| `src/calbince/Methods.java` | Calculation dispatch | 3619 |
| `src/calbince/OptMrq.java` | Levenberg-Marquardt optimizer | 318 |
| `src/calbince/PhaseData.java` | Phase data manager | 961 |
| `src/calbince/ExptData.java` | Experimental data container | 488 |
| `src/calbince/ExptDatum.java` | Single data point | 153 |
| `src/calbince/CalVars.java` | Calculation sets | 294 |
| `src/calbince/CalcSet.java` | Condition set | 79 |
| `src/calbince/CalcType.java` | Condition type | 85 |
| `src/calbince/Condition.java` | External conditions | 118 |
| `src/calbince/GetNumData.java` | Data counter | 59 |
| `src/calbince/Mrqcof.java` | (stub) | — |

### 14.8 Legacy Phase Models

| File | Purpose | Lines |
|------|---------|-------|
| `src/phase/GibbsModel.java` | Abstract Gibbs model | 281 |
| `src/phase/CECVM.java` | Abstract CVM base | 928 |
| `src/phase/PHASEBINCE.java` | Phase interface | 110 |
| `src/phase/A2TTERN.java` | A2-type CVM phase | 224 |
| `src/phase/calphad/RK.java` | Redlich-Kister (Calphad) | 155 |
| `src/phase/solution/calphad/RK.java` | Full RK Calphad solver | 677 |
| `src/phase/solution/calphad/STCOMP.java` | Stoichiometric compound | 381 |
| `src/phase/solution/cecvm/*BINCE.java` | 15 CVM solution models | ~4000+ total |

### 14.9 Shared Utilities

| File | Purpose | Lines |
|------|---------|-------|
| `src/util/Print.java` | Legacy print utility | 150 |
| `src/util/DataPrinter.java` | File output | 120 |
| `src/util/DataReader.java` | File input | 200 |
| `src/util/Format.java` | Number formatting | 250 |
| `src/util/PrintfFormat.java` | Printf formatting | 3098 |
| `src/util/Utils.java` | Generic utilities | 180 |
| `src/util/Ftest.java` | F-test statistics | 100 |
| `src/util/Matrix.java` | JAMA matrix operations | 500 |
| `src/util/[JAMA classes]` | Matrix decompositions (19 files) | ~6000+ total |

### 14.10 Tests

| File | Purpose | Lines |
|------|---------|-------|
| `src/test/UseCaseTest.java` | Use-case level tests | 163 |
| `src/test/ThermoConditionTest.java` | ThermoCondition tests | 125 |
| `src/test/ThermoResultTest.java` | ThermoResult tests | 127 |
| `src/test/TdbParserTest.java` | TDB parser tests | 78 |

---

## Document Information

**Created:** 2026-03-21
**Last Updated:** 2026-03-21
**Version:** 2.0 (Consolidated from ARCHITECTURE.md + STRUCTURE_SIMPLIFICATION.md)
**Scope:** Complete project specification
**Audience:** Developers, architects, contributors

---

**This document is the authoritative specification for the expCVM 10 project. For implementation details, refer to specific sections or the code itself. For changes, update this document and create a new git commit.**
