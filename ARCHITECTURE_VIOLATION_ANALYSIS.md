# Architecture Violation Analysis

Date: 2026-03-07
Scope: Full workspace audit against `ARCHITECTURE.md` rules (Sections 2, 8, 9)

## Severity Summary

- High: 0 open categories (+3 resolved, +1 partially resolved)
- Medium: 2 open categories (+1 resolved)
- Low: 1 category

## High Severity Findings

### 1) Presentation imports infrastructure directly

Status: RESOLVED on 2026-03-07.

Rule violated: `presentation` must not import `infrastructure` directly; data should flow through `application`.

Previous evidence:
- `src/presentation/gui/controllers/MainController.java:7`
  - `import database.tdb;`
- `src/presentation/gui/controllers/MainController.java:101`
  - `tdb full = new tdb(tdbPath);`
- `src/presentation/gui/controllers/MainController.java:102`
  - `tdb system = full.gettdb(elements);`

Implemented fix:
- Added DTO `src/application/dto/ModelInfo.java`.
- Moved model-inspection logic into `src/application/service/CalculationService.java` (`inspectModel`).
- Updated `src/presentation/gui/controllers/MainController.java` to delegate to `CalculationService.inspectModel` and removed all `database.*` imports.
- Updated `src/presentation/gui/views/MainFrame.java` to use `application.dto.ModelInfo`.

Current verification:
- No presentation-layer imports of `database`, `utils`, or `infrastructure` remain.


### 2) Application imports infrastructure parser directly

Status: RESOLVED on 2026-03-07.

Rule violated: `application` must depend on ports, not infrastructure implementations.

Previous evidence:
- `src/application/service/CalculationService.java:6`
  - `import database.tdb;`
- `src/application/service/CalculationService.java:35`
  - `tdb rtbd = new tdb(tdbPath);`

Implemented fix:
- Added `DatabasePort databasePort` field to `CalculationService` with constructor injection.
- Replaced direct `new tdb(tdbPath)` instantiation with `databasePort.load(tdbPath)` and `databasePort.extractSystem(elements)`.
- Updated `main.Main` to inject `TdbParser` instance into `CalculationService` constructor.
- Used `TdbParser.getUnderlyingTdb()` bridge method for backward compatibility with legacy code (`CalVars`, `calculate`) that still expects `tdb` objects.

Current verification:
- No direct instantiation of `database.tdb` in application layer.
- `CalculationService` now depends only on `domain.port.DatabasePort` interface.
- Legacy `database.tdb` import remains for type compatibility until legacy code migration completes.

### 3) Application imports infrastructure logging/export utilities

Status: RESOLVED on 2026-03-07.

Rule violated: `application` should not directly use `utils.io` logging/export adapters.

Previous evidence:
- `src/application/service/OptimizationService.java:4`
  - `import utils.io.DataPrinter;`
- `src/application/service/OptimizationService.java:5`
  - `import utils.io.Print;`
- `src/application/service/OptimizationService.java:26`
  - `DataPrinter dataPrinter = new DataPrinter(filePrefix, logLevel);`

Implemented fix:
- Created `domain.port.LoggingPort` interface for logging operations.
- Created `domain.port.OptimizationOutputPort` factory interface with nested `OutputWriter` for optimization output operations.
- Implemented `infrastructure.logging.ConsoleLogger` wrapping legacy `Print.f()`.
- Implemented `infrastructure.output.OptimizationOutputAdapter` wrapping legacy `DataPrinter`.
- Added constructor injection to `OptimizationService` accepting `LoggingPort` and `OptimizationOutputPort`.
- Updated `main.Main` to inject `ConsoleLogger` and `OptimizationOutputAdapter` instances into `OptimizationService`.

Current verification:
- No `utils.io.*` imports in `OptimizationService`.
- Application layer depends only on domain port interfaces.
- Factory pattern handles runtime configuration (filePrefix, logLevel).

### 4) Domain phase models depend on infrastructure/database and io utilities

Status: PARTIALLY RESOLVED on 2026-03-07.

Rule violated: `domain` must not import infrastructure/database/io adapters.

Previous evidence:
- `src/phase/GibbsModel.java:9` imported `database.tdb`
- `src/phase/GibbsModel.java:11` imported `utils.io.Print`
- `src/phase/calphad/RK.java:8-9` imported `database.tdb` and `database.tdb.Phase`
- Multiple classes under `src/phase/solution/**` imported `database.stdst` or `utils.io.Print`

Implemented fix (Logging removal):
- Removed `utils.io.Print` imports from:
  - `phase/GibbsModel.java` - replaced printGE() logging with System.out
  - `phase/calphad/RK.java` - removed all Print imports
  - `phase/solution/calphad/RK.java` and `STCOMP.java` - removed Print imports, commented out debug logging
  - `phase/solution/cecvm/A1QTBINCE.java`, `A1TOBINCE.java`, `A2ORCBINCE.java`, `A2TBINCE.java`, `A3TOBINCE.java` - removed Print imports, commented out level-6 debug calls
- Removed unused `tdb` and `tdb.Phase` fields from `GibbsModel.java` (getTdb/setTdb methods removed)
- Changed `phase/calphad/RK.java` paramList type from `ArrayList<tdb.Parameter>` to `ArrayList<?>` (type-erased for backward compatibility)

Current verification:
- Build passes (exit code 0)
- Most `utils.io.Print` logging removed from domain layer
- GibbsModel no longer exposes tdb infrastructure types

Remaining work (deferred):
- `database.stdst` usage in `phase/solution/calphad` classes (RK, STCOMP) - used for actual reference state lookups during calculations, requires deeper refactoring
- `utils.io.Utils` and `utils.io.DataReader` in `phase/CECVM.java` and `phase/solution/cecvm/CVMBINCE.java` - used for transformation matrix loading
- Additional CVM solution classes (B*, CPH*, FCC*, L1*, D019*) still have Print imports for debug logging

Impact:
- Major reduction of logging infrastructure violations (15+ files cleaned)
- Core domain model (GibbsModel, phase/calphad/RK) now independent of database infrastructure types
- Remaining violations are in specialized solution-phase calculators that require data-fetching refactoring

## Medium Severity Findings

### 1) File parsing remains in `calbince` mixed package

Evidence:
- `src/calbince/PhaseData.java:10-12` (`BufferedReader`, `DataInputStream`, `FileInputStream`)
- `src/calbince/ExptData.java:9-11` (same)
- `src/calbince/GetNumData.java:9` (`FileInputStream`)

Impact:
- Parsing/IO concerns still mixed with application legacy classes.

Minimal fix:
- Continue extraction into `infrastructure/dataset` readers and keep DTOs in `application`.

### 2) Composition root creates adapters but does not wire them into services

Status: RESOLVED on 2026-03-07.

Previous evidence:
- `src/main/Main.java:22` creates `PhaseFactory`.
- `src/main/Main.java:23` creates `TdbParser`.
- Services were created with default constructors and self-resolved dependencies.

Implemented fix:
- `CalculationService` now accepts `DatabasePort` via constructor (injected from Main).
- `OptimizationService` now accepts `LoggingPort` and `OptimizationOutputPort` via constructor (injected from Main).
- `main.Main` composition root creates all adapters (`TdbParser`, `ConsoleLogger`, `OptimizationOutputAdapter`) and injects them into services.

Current verification:
- All application services use constructor injection.
- Main.java properly wires infrastructure adapters to services.

### 3) Wildcard imports from legacy package in application services

Evidence:
- `src/application/service/CalculationService.java:5` `import calbince.*;`
- `src/application/service/OptimizationService.java:3` `import calbince.*;`

Impact:
- Blurs responsibility boundaries and hides coupling.

Minimal fix:
- Replace with explicit imports; migrate responsibilities package-by-package.

## Low Severity Findings

### 1) Architecture doc drift

Evidence:
- `ARCHITECTURE.md` package tree still listed `PhaseDiagramUseCase.java` after minimal merge removed that class.

Fix:
- Update package structure to current implementation.

## Recommended Remediation Order

1. Remove presentation->database dependency (`MainController` metadata path).
2. Inject `DatabasePort` into `CalculationService`.
3. Decouple `OptimizationService` from `utils.io` adapters.
4. Continue parser/reader extraction from `calbince` to `infrastructure/dataset`.
5. Reduce domain->infrastructure coupling in `phase/**` classes.

## Notes

- This report is a compliance snapshot. It does not imply runtime failure; several violating paths currently execute successfully.
- Highest-risk architectural debt currently lies in boundary direction violations, not in compilation stability.
