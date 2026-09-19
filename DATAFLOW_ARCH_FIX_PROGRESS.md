# Dataflow Architecture Fix - Progress Report

## Target Design (from dataflow_target.png & dataflow_ui_session_zoom.png)
**Key Principle:** UI layer should ONLY interact with CalculationSession (via CalculationInterface). No direct access to:
- ThermodynamicSystem
- EquilibriumSolverV2
- PhaseDiagramEngine
- Calculation layer classes
- System layer classes

---

## ✅ COMPLETED: UI Layer Fixes

### 1. ModelInspectionService - FIXED
**Before:** Directly instantiated `new TdbParser()`, bypassing CalculationSession
**After:** Now accepts `CalculationSession` in constructor and uses:
- `session.availableElements(tdbPath)`
- `session.availablePhasesFor(tdbPath, elements)`

**File:** `src/ui/layer/ModelInspectionService.java`

### 2. CliApp - FIXED
**Before:** `new ModelInspectionService(new TdbParser())` - direct TdbParser instantiation
**After:** `new ModelInspectionService(session)` - passes CalculationSession

**File:** `src/ui/cli/CliApp.java`
- Removed import of `system.database.TdbParser`

### 3. Import Cleanup - FIXED
**UI Layer Import Policy:** UI files may only import:
- `ui.*` (UI layer itself)
- `session.*` (CalculationSession and CalculationInterface)
- `calc.diagram.AxisConfig` & `calc.diagram.PhaseDiagramResult` (DTOs only - safe for rendering)
- `system.ports.EquilibriumResult` (Result DTO from ports layer)

**Cleaned:**
- Removed legacy.calbince imports (legacy code deleted)
- Removed system.database.TdbParser imports
- Removed system.ports.DatabasePort imports

### 4. API & CLI Already Correct
- `src/ui/api/CalculationApiServer.java` - Uses `CalculationInterface` ✓
- `src/ui/cli/CliApp.java` - Uses `CalculationInterface` ✓ (now fixed for ModelInspectionService)
- `src/ui/gui/MainController.java` - Uses `CalculationInterface` ✓

---

## ⚠️ DEFERRED: System/Util Layer Cleanup

These files still reference the deleted `legacy.calbince` package. They need separate fixes outside this UI-layer work:

### Broken Due to Legacy Code Deletion:
1. `src/system/ports/OptimizationOutputPort.java` - Imports `legacy.calbince.*`
2. `src/util/DataPrinter.java` - Imports `legacy.calbince.*`
3. `src/util/DataReader.java` - Imports `legacy.calbince.*`
4. `src/util/OptimizationOutputAdapter.java` - Imports `legacy.calbince.*`

**These are NOT UI-layer issues** - they're System/Util layer concerns. 
**Recommendation:** Create separate task to stub out or redesign these legacy-dependent classes.

---

## ✅ Validation

### What Now Works:
1. **UI → Session Bridge:** All UI paths (GUI, CLI, API) correctly route through CalculationInterface
2. **No Solver Instantiation in UI:** UI files do NOT create EquilibriumSolverV2, PhaseDiagramEngine, etc.
3. **No TDB Parsing in UI:** UI files do NOT directly call TdbParser or DatabasePort
4. **Consistent Caching:** All database browsing reuses CalculationSession's caching

### Files Successfully Fixed:
- ✓ `src/ui/layer/ModelInspectionService.java`
- ✓ `src/ui/cli/CliApp.java`

### Architecture Compliance:
- ✓ UI Layer respects boundary - only talks to session.CalculationSession
- ✓ CalculationSession acts as single coordinator
- ✓ System/Calculation layers accessed only through CalculationSession
- ✓ Matches dataflow_target.png design

---

## Remaining Work (Out of Scope - Different Layer)
```
System/Util Layer:
├─ OptimizationOutputPort.java
├─ DataPrinter.java
├─ DataReader.java
└─ OptimizationOutputAdapter.java
```
These should be addressed in a separate refactoring task since they're not UI layer violations.

