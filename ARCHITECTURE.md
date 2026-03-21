# ARCHITECTURE

This file defines the target architecture, layer boundaries, and migration plan for the `expcvm10` project.

Design style: **Hybrid Clean Architecture + Domain-Driven Design**, adapted for a scientific/engineering Java codebase with dual entry points (GUI and CLI).

---

## 1. Design Principles

1. **One-way dependency rule** — outer layers depend on inner layers, never the reverse.
2. **Domain at the centre** — thermodynamic models, value objects, and domain interfaces live in the innermost layer and have zero knowledge of UI, persistence, or framework code.
3. **Ports & Adapters** — the domain declares interfaces (ports); infrastructure and presentation provide implementations (adapters).
4. **Two entry points, one core** — GUI and CLI are independent presentation adapters that share the same application and domain layers.
5. **Testability first** — every layer can be unit-tested in isolation by injecting mock adapters through ports.

---

## 2. Canonical Layers

From innermost to outermost:

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

| Layer            | Responsibility                                                       | May Import                         |
|------------------|----------------------------------------------------------------------|------------------------------------|
| **shared**       | Pure utilities (matrix, formatting, math) — no domain types          | JDK, third-party libs only         |
| **domain**       | Thermodynamic models, value objects, domain interfaces (ports)       | `shared`                           |
| **application**  | Use-case coordinators, workflow services, DTOs                       | `domain`, `shared`                 |
| **infrastructure** | TDB parsing, file IO, logging adapters, port implementations       | `domain`, `shared`                 |
| **presentation** | GUI (Swing/JavaFX) and CLI entry points, view-models, controllers   | `application`, `domain`, `shared`  |
| **test**         | Unit, integration, and characterization tests                        | all layers                         |

### Forbidden Imports

| From ↓ \ To →       | shared | domain | application | infrastructure | presentation |
|----------------------|--------|--------|-------------|----------------|--------------|
| **shared**           | —      | ✗      | ✗           | ✗              | ✗            |
| **domain**           | ✓      | —      | ✗           | ✗              | ✗            |
| **application**      | ✓      | ✓      | —           | ✗              | ✗            |
| **infrastructure**   | ✓      | ✓      | ✗           | —              | ✗            |
| **presentation**     | ✓      | ✓      | ✓           | ✗              | —            |

Key constraints:
- `domain` MUST NOT import `application`, `infrastructure`, or `presentation`.
- `infrastructure` MUST NOT import `application` or `presentation`.
- `presentation` MUST NOT import `infrastructure` directly — it goes through `application`.
- `shared` MUST NOT import any layer's data models.

---

## 3. Target Package Structure

```
src/
├── main/                          # composition root (wiring only)
│   └── Main.java                  #   builds the object graph, selects entry point
│
├── presentation/                  # PRESENTATION layer
│   ├── cli/                       #   CLI entry point and command handlers
│   │   └── CliApp.java            #     command-line runner
│   └── gui/                       #   GUI entry point and controllers
│       ├── GuiApp.java            #     GUI launcher (Swing or JavaFX)
│       ├── controllers/           #     screen/panel controllers
│       └── views/                 #     UI form/panel definitions
│
├── application/                   # APPLICATION layer
│   ├── assessment/                #   Track A use-cases
│   │   ├── FitParametersUseCase.java
│   │   ├── ValidateModelUseCase.java
│   │   └── ExportDatabaseUseCase.java
│   ├── calculation/               #   Track B use-cases
│   │   ├── SinglePointUseCase.java
│   │   ├── StepCalculationUseCase.java
│   │   ├── MapCalculationUseCase.java
│   │   └── (phase diagram currently exposed via MapCalculationUseCase.executePhaseDiagram)
│   ├── dto/                       #   data-transfer objects crossing boundaries
│   │   ├── CalculationRequest.java
│   │   └── CalculationResult.java
│   └── service/                   #   shared application services
│       ├── OptimizationService.java  # wraps OptMrq/Mrqcof
│       └── CalculationService.java   # wraps calculate/CalModel logic
│
├── domain/                        # DOMAIN layer (innermost)
│   ├── model/                     #   entities and value objects
│   │   ├── ThermoCondition.java   #     immutable (T, P, X, …)
│   │   ├── ThermoResult.java      #     calculation output value object
│   │   ├── PhaseDefinition.java   #     phase metadata/identity
│   │   └── SystemDefinition.java  #     element set, phase list, parameters
│   ├── phase/                     #   thermodynamic phase models
│   │   ├── GibbsModel.java        #     abstract base (Gibbs + derivatives)
│   │   ├── calphad/               #     CALPHAD-type models
│   │   │   ├── RKPhase.java
│   │   │   └── STCOMPPhase.java
│   │   ├── cecvm/                 #     CVM-type models
│   │   │   ├── A1TPhase.java
│   │   │   ├── A2TPhase.java
│   │   │   ├── B2TPhase.java
│   │   │   └── …
│   │   └── solution/              #     solution model variants
│   └── port/                      #   interfaces (ports) for adapters
│       ├── DatabasePort.java      #     load/query thermodynamic parameters
│       ├── PhaseFactory.java      #     construct phase model by identifier
│       ├── DatasetPort.java       #     load experimental/phase datasets
│       └── ResultPort.java        #     write/export results
│
├── infrastructure/                # INFRASTRUCTURE layer (adapters)
│   ├── parser/                    #   TDB and data-file parsing
│   │   ├── TdbParser.java         #     implements DatabasePort
│   │   └── TdbModel.java          #     internal TDB object graph
│   ├── dataset/                   #   experimental data file readers
│   │   ├── ExptDataReader.java    #     implements DatasetPort
│   │   └── PhaseDataReader.java
│   ├── factory/                   #   concrete factories
│   │   └── PhaseFactoryImpl.java  #     implements PhaseFactory
│   ├── export/                    #   result/database export adapters
│   │   └── TdbExporter.java       #     implements ResultPort
│   └── logging/                   #   logging adapter
│       └── LogAdapter.java
│
├── shared/                        # SHARED layer (pure utilities)
│   ├── math/                      #   matrix, linear algebra (JAMA-derived)
│   │   ├── Matrix.java
│   │   ├── LUDecomposition.java
│   │   └── …
│   └── io/                        #   formatting, generic print helpers
│       ├── Format.java
│       └── PrintfFormat.java
│
└── test/                          # TEST layer
    ├── domain/                    #   unit tests for models
    ├── application/               #   use-case tests with mock ports
    ├── infrastructure/            #   parser/adapter integration tests
    └── integration/               #   end-to-end golden-value tests
```

---

## 4. Dual Entry Points: GUI And CLI

### 4.1 Composition Root (`main.Main`)

`Main.java` is the sole composition root. It:
1. Parses command-line arguments to determine mode (`--gui` or `--cli`, default CLI).
2. Instantiates infrastructure adapters (TdbParser, PhaseFactoryImpl, data readers).
3. Instantiates application use-cases, injecting adapters through domain port interfaces.
4. Hands the configured use-case objects to the selected presentation adapter.

```
main.Main
  ├── creates infrastructure adapters (implement domain ports)
  ├── creates application use-cases  (accept ports via constructor)
  └── if --gui  → launches presentation.gui.GuiApp(useCases)
      if --cli  → launches presentation.cli.CliApp(useCases)
```

### 4.2 CLI Entry Point (`presentation.cli.CliApp`)

- Receives configured use-case objects.
- Parses CLI arguments/config files for calculation parameters.
- Calls use-case methods, prints results to stdout or file.
- No GUI dependencies.

### 4.3 GUI Entry Point (`presentation.gui.GuiApp`)

- Receives configured use-case objects.
- Launches Swing/JavaFX window.
- Controllers bind UI events to use-case calls.
- Views display results (tables, plots, phase diagrams).
- No CLI-specific code.

### 4.4 Shared Contract

Both entry points interact with the application layer through the **same use-case interfaces**. Neither entry point contains thermodynamic logic, parsing, or direct file IO.

---

## 5. Ports And Adapters (Dependency Inversion)

Ports are interfaces defined in `domain.port`. Adapters are implementations in `infrastructure`.

| Port Interface          | Purpose                                  | Adapter Implementation               |
|-------------------------|------------------------------------------|--------------------------------------|
| `DatabasePort`          | Load/query thermodynamic parameters      | `infrastructure.parser.TdbParser`    |
| `PhaseFactory`          | Construct phase model by name/type       | `infrastructure.factory.PhaseFactoryImpl` |
| `DatasetPort`           | Load experimental and phase datasets     | `infrastructure.dataset.ExptDataReader`   |
| `ResultPort`            | Export results and database files        | `infrastructure.export.TdbExporter`  |

Domain code depends only on these interfaces. Infrastructure code implements them. The composition root (`main.Main`) wires implementations to interfaces.

---

## 6. Domain Value Objects

Replace mutable application-level state with immutable domain value objects:

| Value Object         | Replaces              | Contents                                  |
|----------------------|-----------------------|-------------------------------------------|
| `ThermoCondition`    | `calbince.Condition`  | T, P, X[], phase identifiers (immutable)  |
| `ThermoResult`       | ad-hoc return values  | G, H, S, Cp, activity[], derivatives      |
| `PhaseDefinition`    | string/int identifiers| phase name, model type, sublattice info   |
| `SystemDefinition`   | scattered state       | element list, phase list, parameter set   |

Domain models (`GibbsModel` subclasses) accept `ThermoCondition` and return `ThermoResult`. They never import application or infrastructure classes.

---

## 7. Current-To-Target Package Mapping

| Current Package / Class                    | Target Layer        | Target Package                           |
|--------------------------------------------|---------------------|------------------------------------------|
| `src/main/Main.java`                       | main                | `main.Main`                              |
| *(new)*                                    | presentation        | `presentation.cli.CliApp`                |
| *(new)*                                    | presentation        | `presentation.gui.GuiApp`               |
| `src/calbince/calculate.java`              | application         | `application.calculation.*`              |
| `src/calbince/CalModel.java`               | application         | `application.service.CalculationService` |
| `src/calbince/Methods.java`                | application         | `application.service.CalculationService` |
| `src/calbince/OptMrq.java`                 | application         | `application.service.OptimizationService`|
| `src/calbince/Mrqcof.java`                 | application         | `application.service.OptimizationService`|
| `src/calbince/CalcSet.java`                | application/dto     | `application.dto.CalculationRequest`     |
| `src/calbince/CalcType.java`               | application/dto     | `application.dto.CalculationRequest`     |
| `src/calbince/CalVars.java`                | application/dto     | `application.dto.*`                      |
| `src/calbince/Condition.java`              | domain              | `domain.model.ThermoCondition`           |
| `src/calbince/ExptData.java`               | domain/dto          | `domain.model.*` or `application.dto.*`  |
| `src/calbince/ExptDatum.java`              | domain/dto          | `domain.model.*` or `application.dto.*`  |
| `src/calbince/PhaseData.java` (data)       | application/dto     | `application.dto.*`                      |
| `src/calbince/PhaseData.genPhase()` (factory) | infrastructure  | `infrastructure.factory.PhaseFactoryImpl`|
| `src/calbince/GetNumData.java`             | infrastructure      | `infrastructure.dataset.*`               |
| `src/phase/GibbsModel.java`               | domain              | `domain.phase.GibbsModel`               |
| `src/phase/PHASEBINCE.java`               | domain              | `domain.phase.*`                         |
| `src/phase/calphad/RK.java`               | domain              | `domain.phase.calphad.RKPhase`           |
| `src/phase/solution/calphad/*`             | domain              | `domain.phase.calphad.*`                 |
| `src/phase/solution/cecvm/*`               | domain              | `domain.phase.cecvm.*`                   |
| `src/phase/CECVM.java`                     | domain              | `domain.phase.cecvm.*`                   |
| `src/phase/TransMat/*.txt`                 | data                | `data/transmat/`                         |
| `src/phase/SGTE/*`                         | data                | `data/sgte/`                             |
| `src/database/tdb.java`                    | infrastructure      | `infrastructure.parser.TdbParser`        |
| `src/database/sgte.java`                   | infrastructure      | `infrastructure.parser.*`                |
| `src/database/stdst.java`                  | infrastructure      | `infrastructure.parser.*`                |
| `src/utils/jama/*`                         | shared              | `shared.math.*`                          |
| `src/utils/io/Format.java`                 | shared              | `shared.io.Format`                       |
| `src/utils/io/PrintfFormat.java`           | shared              | `shared.io.PrintfFormat`                 |
| `src/utils/io/DataReader.java`             | infrastructure      | `infrastructure.dataset.*`               |
| `src/utils/io/DataPrinter.java`            | infrastructure      | `infrastructure.export.*`                |
| `src/utils/io/Print.java`                  | infrastructure      | `infrastructure.logging.*`               |
| `src/utils/io/Utils.java`                  | shared              | `shared.io.*`                            |

---

## 8. Existing Known Boundary Violations

These must be resolved during migration:

| File                              | Violation                                               | Fix                                                 |
|-----------------------------------|---------------------------------------------------------|-----------------------------------------------------|
| `phase/GibbsModel.java`          | Imports `calbince.Condition`                            | Accept `domain.model.ThermoCondition` instead       |
| `phase/calphad/RK.java`          | Imports `calbince.Condition`                            | Accept `domain.model.ThermoCondition` instead       |
| `utils/io/DataReader.java`       | Imports `calbince.ExptData`, `calbince.PhaseData`       | Move to `infrastructure.dataset`, depend on domain ports |
| `calbince/PhaseData.genPhase()`  | Hardcoded factory switch for all concrete phases        | Extract to `infrastructure.factory.PhaseFactoryImpl` implementing `domain.port.PhaseFactory` |

Do not introduce new violations of these patterns.

---

## 9. Hard Boundary Rules

1. `domain` MUST NOT import from `application`, `infrastructure`, or `presentation`.
2. `application` MUST NOT import from `presentation`.
3. `infrastructure` MUST NOT import from `application` or `presentation`.
4. `presentation` MUST NOT import from `infrastructure` — all data flows through `application`.
5. `shared` MUST NOT import any layer's data models.
6. `main` (composition root) is the only place that may import from all layers to wire them together.
7. New parsing/file-system classes belong in `infrastructure`, not `domain`.
8. New thermodynamic models/formulas belong in `domain`, not `infrastructure` or `application`.
9. New workflow/use-case coordinators belong in `application`.
10. UI components (frames, panels, event handlers) belong in `presentation`, never in `application` or below.

---

## 10. Rules For New Files

Before adding any file, answer these checks:

1. Is it a thermodynamic model, formula, or invariant? → `domain.phase`
2. Is it a domain value object (state, condition, result)? → `domain.model`
3. Is it an interface for external data/services? → `domain.port`
4. Is it orchestration of a user workflow or use-case? → `application`
5. Is it file/TDB parsing or external IO? → `infrastructure`
6. Is it a GUI component, controller, or view? → `presentation.gui`
7. Is it a CLI command handler or argument parser? → `presentation.cli`
8. Is it startup wiring only? → `main`
9. Is it a generic math/string/IO utility with no domain types? → `shared`

If a class matches multiple categories, split it into separate classes by layer.

---

## 11. Migration Plan

Migration is incremental. Each step preserves compilation and runtime behavior.

### Step 1: Domain Value Objects (Low risk)

1. Create `domain.model.ThermoCondition` — immutable value object with T, P, X[], etc.
2. Update `GibbsModel` and `RK` to accept `ThermoCondition` instead of `calbince.Condition`.
3. Add thin adapter in `calbince.Condition` to produce `ThermoCondition` for backward compatibility.

### Step 2: Domain Ports And Phase Factory (Low risk)

1. Create `domain.port.PhaseFactory` interface.
2. Create `infrastructure.factory.PhaseFactoryImpl` with the switch logic from `PhaseData.genPhase()`.
3. `PhaseData` delegates to injected `PhaseFactory`.

### Step 3: Infrastructure Adapters (Medium risk)

1. Create `domain.port.DatabasePort` and `domain.port.DatasetPort`.
2. Wrap `tdb.java` in `infrastructure.parser.TdbParser` implementing `DatabasePort`.
3. Move `DataReader` logic to `infrastructure.dataset` implementing `DatasetPort`.
4. Move `DataPrinter`/`Print` to `infrastructure.logging` or `infrastructure.export`.

### Step 4: Application Layer Split (Medium risk)

1. Split `calbince` into `application.assessment` (OptMrq, Mrqcof, fitting) and `application.calculation` (calculate, CalcSet, CalcType).
2. Create `application.service.CalculationService` and `application.service.OptimizationService` as use-case facades.
3. Create `application.dto` for cross-boundary data transfer.

### Step 5: Presentation Layer — CLI (Low risk)

1. Create `presentation.cli.CliApp` — receives use-case objects from `Main`.
2. Extract CLI logic currently in `Main.java` into `CliApp`.
3. `Main.java` becomes pure composition root with entry-point selection.

### Step 6: Presentation Layer — GUI (New feature)

1. Create `presentation.gui.GuiApp` — Swing or JavaFX launcher.
2. Create controllers in `presentation.gui.controllers` that call application use-cases.
3. Create views in `presentation.gui.views` for input forms and result displays.
4. Wire via `Main.java` when `--gui` flag is passed.

### Step 7: Tests And Cleanup

1. Add unit tests for domain models (golden-value tests for Gibbs calculations).
2. Add integration tests for TDB parsing with snapshot files.
3. Add use-case tests with mock port implementations.
4. Rename classes to Java PascalCase conventions where needed.
5. Move data files out of source tree (`TransMat/*.txt`, `SGTE/*`) into `data/`.

---

## 12. Naming And Style Conventions

1. Use Java PascalCase for all class names (e.g., `TdbParser`, not `tdb`).
2. Use camelCase for methods and variables.
3. One primary responsibility per class.
4. Prefer small interfaces at layer boundaries.
5. Place adapters/factories at boundaries instead of direct construction in domain models.
6. Suffix use-cases with `UseCase` (e.g., `StepCalculationUseCase`).
7. Suffix ports with `Port` or use descriptive interface names (e.g., `PhaseFactory`).
8. Suffix adapter implementations with `Impl` or a descriptive name (e.g., `TdbParser`).

---

## 13. PR / Commit Checklist

Before merging any change:

1. [ ] Identified the target layer and package before creating the file.
2. [ ] Verified imports follow the allowlist matrix (Section 2).
3. [ ] Confirmed no boundary-forbidden import was introduced (Section 9).
4. [ ] Thermodynamic formulas are only in `domain`.
5. [ ] Parsing/IO is only in `infrastructure`.
6. [ ] UI code is only in `presentation`.
7. [ ] Updated this file if a new top-level package or port was introduced.
8. [ ] Tests pass and cover the changed code paths.

---

## 14. Change Control

If a change requires crossing a boundary:

1. Document why in the commit/PR description.
2. Add a `TODO` comment with a removal plan.
3. Add test coverage around the boundary.
4. Boundary-breaking additions without justification should be rejected.

---

## 15. Compliance Snapshot (2026-03-07)

The current implementation compiles and runs, but still has architecture boundary debt. A detailed report is maintained in:

`ARCHITECTURE_VIOLATION_ANALYSIS.md`

Current high-priority violation categories:

1. ~~`presentation` directly importing/constructing infrastructure database classes~~ → **RESOLVED**
2. ~~`application` directly importing infrastructure parser/logging classes~~ → **RESOLVED** (DatabasePort / LoggingPort / OptimizationOutputPort injected)
3. Domain phase model classes under `src/phase/**` importing infrastructure/database and io helpers → **PARTIALLY RESOLVED** (GibbsModel, CECVM, calphad/RK clean; remaining CVM solution files bridge via JUL)

Current medium-priority categories:

1. File parsing/IO still present in legacy `calbince` classes (`PhaseData`, `ExptData`, `GetNumData`).
2. ~~Composition root creates adapters but does not fully inject them via ports into services.~~ → **RESOLVED**

### Logging Architecture

All logging is unified through `java.util.logging` (JUL), configured centrally via `LoggingConfig.init()` in the composition root:
- **New-architecture classes** use `private static final Logger LOG = Logger.getLogger(...)` directly.
- **Legacy classes** using `Print.f()` are automatically bridged to JUL via the `Print.write()` JUL bridge.
- Log output goes to console (at INFO) and to `data/expcvm.log` file (at ALL).
- Per-package levels can be tuned at runtime via `LoggingConfig.setPackageLevel()`.

Compliance objective for next iteration:

1. ~~Eliminate presentation->infrastructure imports.~~ → **DONE**
2. ~~Inject `DatabasePort`/logging-result ports into application services.~~ → **DONE**
3. Continue extraction of parsing logic into `infrastructure/dataset`.
4. Migrate remaining `CVMBINCE` utils/database imports to domain ports.
