
Thermodynamic calculation and optimization codebase for CALPHAD/CVM-style binary and multicomponent phase modeling.

## Project Vision

The project is expected to deliver two major capabilities:

1. Thermodynamic assessment of multicomponent systems using experimental and theoretical data (for example thermochemical and phase-diagram data) to generate and refine thermodynamic database (`.tdb`) files.
2. Use available database files to perform multiple types of calculations (single-point, step, map, phase-diagram and related calculations).

Both capabilities are large and will be implemented incrementally through smaller subtasks.

## Tech Stack


## Build And Run

From project root:

```bash
# Ant-based build
ant clean
ant jar
ant run

# Direct command-line compile and run
javac --release 8 -sourcepath src -d build/classes src/main/Main.java

# Run with GUI (single-tab, embedded log console)
java -cp build/classes main.Main --gui

# Run without GUI (CLI mode)
java -cp build/classes main.Main
```

GUI output jar is generated at `dist/expcvm10.jar`.

## Expected Features

### A. Assessment And Database Development

1. Import/standardize mixed data sources (thermochemical and phase-equilibria datasets).
2. Define model structure and parameterization per phase/system.
3. Fit model parameters against selected datasets.
4. Validate fitted models against withheld/reference datasets.
5. Export/maintain database files (`.tdb`) with traceable revisions.

### B. Database-Driven Calculations

1. Single-point equilibrium/property calculations.
2. Step calculations (vary one condition, track response).
3. Map calculations (vary two conditions/composition dimensions).
4. Automated phase-diagram workflows.
5. Batch runs and reproducible output/report generation.
# expCVM 10 — Thermodynamic Workbench

## Overview
expCVM 10 is a professional Java-based GUI application for thermodynamic calculations using CALPHAD and CVM models. It supports database-driven workflows, dynamic input panels, and immediate feedback on database metadata, available elements, and phases.

## Features
- Java 8, Swing GUI
- Dynamic input panel: elements, phases, and methods are populated from the selected TDB file
- Results window shows parsed elements and phases from the TDB file
- Robust backend/frontend data flow
- Logging and export options

## Usage
1. **Build:**
  ```
  javac --release 8 -sourcepath src -d build/classes (Get-ChildItem -Recurse -Filter "*.java" src | ForEach-Object { $_.FullName })
  ```
2. **Run GUI:**
  ```
  java -cp build/classes main.Main --gui
  ```
3. **Select a TDB file:**
  - The GUI will display available elements and phases from the database in both the input panel and the results window.

## Project Structure
- `src/` — Java source code
- `data/` — TDB and data files
- `build/` — Compiled classes

## Status
See `project_status.md` for current progress and known issues.

## Features Already Implemented (Current State)

### Logging Infrastructure (Phase 13 — Complete)

Custom hierarchical logging system with 7 meaningful application-level levels and structured method entry/exit tracing:

- **AppLevel**: Custom `Level` subclass replacing standard JUL levels — ERROR(1000), WARN(900), RESULT(800), FLOW(700), ENGINE(500), MODEL(400), SOLVER(300)
- **Trace Utility**: Structured `>> ClassName.method [ClassName.java]` / `<< ClassName.method [ClassName.java] (42ms)` logging for method entry/exit
- **4-Level Method Hierarchy**:
  - **L0 (FLOW)**: `MainController` — GUI action handlers
  - **L1 (FLOW)**: `CalculationService`, `OptimizationService` — Service facades
  - **L2 (ENGINE)**: `calculate`, `CalModel`, `OptMrq`, `Methods` — Core calculation engines
  - **L3 (MODEL)**: `phase.calphad.RK`, `phase.solution.calphad.RK`, `STCOMP` — Thermodynamic models
  - **L4 (SOLVER)**: `CVMBINCE` (CVM solver base) — Deep CVM calculations
- **GUI Log Console**: Single-panel Swing interface with embedded live log viewer, level selector (dropdown), and filter by class name
- **Backward Compatibility**: Legacy `Print.f()` debug statements auto-bridged to JUL; old log statements converted to AppLevel

### Core Data/Model Infrastructure

1. TDB parsing and internal database object model (`src/database/tdb.java`).
2. System extraction from larger databases by element selection (`gettdb(...)`).
3. Phase-model abstraction and multiple concrete phase model classes (`src/phase/**`, `src/phase/solution/**`).

### Assessment-Oriented Building Blocks

1. Experimental and phase input data structures/readers (`ExptData`, `ExptDatum`, `PhaseData`).
2. Calculation and fitting flow classes (`CalModel`, `Methods`, `OptMrq`, `Mrqcof`).
3. Parameter-vector handling for fitting in `PhaseData` and optimization routines in `OptMrq`.

### Calculation-Oriented Building Blocks

1. Runtime wiring from `main.Main` for loading `.tdb`, selecting elements, creating calculation sets.
2. Newer calculation scaffolding in `calbince.calculate` for condition-driven workflows.
3. Legacy/established property calculations and optimization paths through `Methods`/`CalModel`.

## Partially Implemented / Not Yet First-Class

1. End-to-end automated `.tdb` creation workflow from raw datasets is not yet a single integrated pipeline.
2. Standardized production workflows for step/map/phase-diagram calculations are not yet exposed as stable user-facing modules.
3. Validation, reporting, and test automation need expansion for large-scale assessment/calculation campaigns.
4. Architecture violation remediation is complete; per-package logging defaults are in place but may need tuning as new layers are added.

## Incremental Subtask Roadmap

### Track A: Assessment To Database (`.tdb`)

1. Define canonical dataset schema and input adapters for thermochemical/phase data.
2. Add dataset quality and consistency checks.
3. Stabilize parameter fitting pipeline (objective definitions, constraints, diagnostics).
4. Add model validation suite and acceptance criteria.
5. Add controlled `.tdb` export/versioning workflow.

### Track B: Database To Calculations

1. Stabilize single-point API/CLI workflow.
2. Implement step-calculation driver with structured outputs.
3. Implement map-calculation driver (2D sweep).
4. Implement phase-diagram workflow modules.
5. Add batch orchestration and reproducible report outputs.

### Cross-Cutting

1. Architecture boundary enforcement using `ARCHITECTURE.md`.
2. Add regression/characterization tests for parser, fitting, and calculation kernels.
3. Improve logging and run metadata for reproducibility.

## Current Architecture (Observed)

- `src/main`: program entry and orchestration (`Main.java`)
- `src/calbince`: application logic, workflows, optimization, input models
- `src/phase`: thermodynamic model abstractions and implementations
- `src/database`: TDB parsing and database object graph
- `src/utils`: IO helpers and matrix/numerical utilities

High-level runtime flow:

1. `main.Main` reads a TDB file and configures calculation inputs.
2. `calbince.calculate` and/or `calbince.CalModel` execute calculations.
3. `phase.*` implementations compute Gibbs and derivatives.
4. `database.tdb` provides thermodynamic parameters.
5. `utils.io.*` handles printing and file utilities.

## Layer Logic Review (Against Standard Practices)

### What Is Good

- Package separation exists by concern (`main`, `calbince`, `phase`, `database`, `utils`).
- Domain-level abstraction is present (`phase.GibbsModel`, `phase.PHASEBINCE`).
- Entry point is isolated in `main.Main`, not spread across many files.

### Key Gaps

1. **Layer direction is mixed (infrastructure and domain leak into application internals).**
- `phase.GibbsModel` depends on `calbince.Condition` (`src/phase/GibbsModel.java`).
- `phase.calphad.RK` depends on `calbince.Condition` (`src/phase/calphad/RK.java`).
- This creates bidirectional coupling between application and domain layers.

2. **Application layer performs concrete domain composition (factory logic hardcoded).**
- `calbince.PhaseData.genPhase(...)` constructs many concrete phase classes via nested `switch` (`src/calbince/PhaseData.java`).
- Adding a new phase/model requires editing this central method, violating Open/Closed Principle.

3. **Utilities depend on application models (wrong dependency direction).**
- `utils.io.DataReader` imports `calbince.ExptData` and `calbince.PhaseData` (`src/utils/io/DataReader.java`).
- Utility/infrastructure code should not depend on higher-level application objects.

4. **Extensive logging and stdout in core paths reduces testability and signal/noise control.**
- Core classes print directly (`src/database/tdb.java`, `src/calbince/calculate.java`, many `Print.f` calls).
- No centralized logging abstraction/level policy.

5. **No automated tests present.**
- No test files discovered (`**/*Test*.java` returned none).
- Refactoring layer boundaries is higher risk without regression tests.

6. **Naming and style inconsistency affects maintainability.**
- Class names with lowercase initials (`tdb`, `calculate`) and mixed conventions.
- Legacy patterns and large classes increase cognitive load.

## Recommended Target Layering

Use a one-way dependency rule:

- `main` (composition root) -> `application` -> `domain` -> `infrastructure`

Where:

- `application`: use-cases and orchestration (`calculate`, `CalModel`, optimization services)
- `domain`: `GibbsModel`, thermodynamic state/value objects, phase behavior interfaces
- `infrastructure`: `tdb` parsing, file IO, persistence/input adapters, logging adapters
- `utils`: pure helpers only (no dependency on application/domain models)

## Refactoring Roadmap

### Phase 1: Safe Structural Steps

1. Introduce small immutable domain value objects for state.
- Example: `ThermoCondition` in domain package.
- Remove direct domain dependency on `calbince.Condition`.

2. Extract phase creation into a dedicated factory interface.
- `PhaseFactory` interface in domain/application boundary.
- Move concrete mapping logic out of `PhaseData.genPhase(...)`.

3. Invert `DataReader` dependency.
- Make `DataReader` return DTOs or parsed records.
- Application layer maps DTOs into `ExptData`/`PhaseData`.

### Phase 2: Testability And Reliability

1. Add characterization tests around current behavior.
- `tdb` parsing smoke tests with fixed input snapshots.
- `calculate` and `Methods.funcsCal` golden-value tests.

2. Replace direct prints in core logic with logger facade.
- Keep current verbosity options but route through one abstraction.

### Phase 3: Maintainability

1. Standardize naming to Java conventions (PascalCase classes).
2. Break up large classes (`tdb`, `Methods`, `PhaseData`) by responsibility.
3. Introduce package-level API boundaries and keep implementation classes internal by convention.

## Practical Notes For Current Codebase

- Current architecture is workable for research workflows, but scaling new phases/models will become costly because construction and dispatch logic is centralized.
- The highest-leverage improvement is to separate model creation and condition/state objects from application package classes.
- Add tests before major refactors to preserve existing scientific results.

## Important Files

- Entry point: `src/main/Main.java`
- Core application workflow: `src/calbince/calculate.java`, `src/calbince/CalModel.java`, `src/calbince/Methods.java`
- Phase abstraction/modeling: `src/phase/GibbsModel.java`, `src/phase/PHASEBINCE.java`, `src/phase/calphad/RK.java`
- Database parser: `src/database/tdb.java`
- IO utilities: `src/utils/io/DataReader.java`, `src/utils/io/DataPrinter.java`

## Status

This README includes an architecture review snapshot based on the current code state and is intended as a practical modernization guide.
