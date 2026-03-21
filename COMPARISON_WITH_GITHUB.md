# Project Comparison: Local vs GitHub Repository

## Overview
Your local project is a **significantly refactored version** of the GitHub repository. It's been restructured from a flat, physics-focused codebase into a modern **Clean Architecture** with layered separation of concerns.

---

## Architecture Comparison

### GitHub Repository (Original)
```
src/
├── calbince/          (Calibration/inverse modeling)
├── database/          (TDB, SGTE database access)
├── main/              (Main entry point)
├── phase/             (Thermodynamic phases)
│   ├── calphad/
│   ├── cecvm/
│   └── solution/
└── utils/             (IO, math libraries)
```
**Total: 61 Java files**

### Local Repository (Refactored)
```
src/
├── application/       (NEW) Use cases, business logic orchestration
│   ├── assessment/    → Export DB, fit parameters, validate model
│   ├── calculation/   → Single point, step, map calculations
│   ├── dto/           → Data transfer objects
│   └── service/       → Service layer coordinating use cases
│
├── domain/            (NEW) Core domain models & ports (interfaces)
│   ├── model/         → ThermoCondition, ThermoResult
│   └── port/          → Database, Dataset, Logging, Optimization ports
│
├── infrastructure/    (NEW) Implementations of domain ports
│   ├── dataset/       → Experimental data readers
│   ├── export/        → TDB exporter
│   ├── factory/       → Phase factory implementation
│   ├── logging/       → Console logger, logging adapters
│   ├── output/        → Optimization output handling
│   └── parser/        → TDB parser
│
├── presentation/      (NEW) UI layer
│   ├── cli/           → Command-line interface
│   └── gui/           → GUI with controllers and views
│
├── test/              (NEW) Unit tests
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── calbince/          (ORIGINAL) Physics calculations
├── database/          (ORIGINAL) Database access
├── main/              (ORIGINAL) Main, refactored to be cleaner
├── phase/             (ORIGINAL) Thermodynamic phases
└── utils/             (ORIGINAL) Utilities
```
**Total: 98 Java files (+37 new)**

---

## Key Changes

### 1. **Main.java Refactoring**
- **GitHub**: 177 lines with lots of commented-out code, direct imports from physics packages
- **Local**: 57 lines, clean, delegating to application services

### 2. **New Architectural Layers**

#### Application Layer
- `CalculationService` - Orchestrates calculations
- `OptimizationService` - Manages parameter fitting
- Use cases for: exports, validation, fitting, step/map/single-point calculations

#### Domain Layer
- Clear contracts via Port interfaces (Dependency Inversion Principle)
- `DatabasePort`, `DatasetPort`, `LoggingPort`, `OptimizationOutputPort`, `PhaseFactory`
- Domain models: `ThermoCondition`, `ThermoResult`

#### Infrastructure Layer
- Concrete implementations of ports
- `TdbParser`, `TdbExporter`, `ExptDataReader`
- `ConsoleLogger`, `LoggingConfig`, `AppLevel`
- `PhaseFactoryImpl` - Bridges legacy phase classes

#### Presentation Layer
- `CliApp` - Command-line interface
- `GuiApp` with controllers and views

### 3. **Testing**
- New test structure with domain, application, and infrastructure tests
- Example: `TdbParserTest`, `UseCaseTest`, `ThermoResultTest`

### 4. **Documentation**
- **GitHub**: `changelog.txt`, `known-issues.txt`
- **Local**: Added `README.md`, `ARCHITECTURE.md`, `PROJECT_STATUS.md` (untracked)

---

## Files Modified in Local Version
All core physics files have been **updated with new imports** to support the new architecture:

- `src/calbince/*.java` - Added imports for domain/application layers
- `src/database/*.java` - Integrated with infrastructure layer
- `src/phase/*.java` - Updated to work with new factory pattern
- `src/main/Main.java` - Simplified, delegates to services
- `src/utils/io/*.java` - Used by new infrastructure layer

---

## What's the Same?
- All 61 original Java files from physics/calculation logic remain
- `build.xml`, `.gitignore`, `manifest.mf` unchanged
- Data files and legacy JAMA math library intact
- Phase models (A1, A2, B2, FCC, BCC, etc.) unchanged

---

## Why This Refactoring?

This is a classic **Clean Architecture** pattern applied to a scientific computing project:

1. **Testability** - Domain logic separated from implementation details
2. **Maintainability** - Clear separation of concerns
3. **Extensibility** - New UI, database, or export implementations can be added without touching core logic
4. **Reusability** - Services can be used by CLI, GUI, or future APIs
5. **Decoupling** - Physics code doesn't depend on specific database/UI implementations

---

## Untracked Changes
Your git status shows:

**Modified**: All the `.java` files mentioned above (refactored imports)
**Untracked**:
- `src/application/`, `src/domain/`, `src/infrastructure/`, `src/presentation/`, `src/test/`
- `README.md`, `ARCHITECTURE.md`, `PROJECT_STATUS.md`
- Various log files and data directories

---

## Next Steps?
- Commit the architecture refactoring if this is intentional
- Decide whether to sync with GitHub repo or keep as a separate fork
- Consider updating README with new architecture documentation
