# expCVM 10 — Thermodynamic Workbench

A professional Java-based application for thermodynamic calculations and database assessment using **CALPHAD** and **CVM** (Cluster Variation Method) models.

---

## Features

### 🔬 **Core Capabilities**
- **Database-driven workflows**: Load and work with CALPHAD thermodynamic databases (`.tdb` files)
- **Single-point calculations**: Compute equilibrium properties at fixed temperature/pressure
- **Parameter fitting**: Optimize model parameters against experimental data (Levenberg-Marquardt)
- **Multiple methods**: Support for HM (Enthalpy), Gm (Molar Gibbs), G (Total Gibbs) calculations
- **Dynamic model composition**: Automatically parse available elements and phases from TDB files

### 🖥️ **User Interface**
- **Swing GUI** with intuitive input panels and results display
- **Live logging console** with custom log levels (ERROR, WARN, RESULT, FLOW, ENGINE, MODEL, SOLVER)
- **Real-time feedback** on database metadata and parsed phase information
- **CLI mode** available for batch operations and scripting

### ⚙️ **Technical Highlights**
- **Java 8 compatible** — Runs on any Java 8+ environment
- **Clean Architecture** — Layered design with clear separation of concerns
- **Comprehensive logging** — Hierarchical 7-level tracing for debugging complex calculations
- **Extensible design** — Easy to add new phase models and calculation types

---

## Quick Start

### 1. **Build the Project**

```bash
# Using javac directly (fastest)
javac --release 8 -sourcepath src -d build/classes $(find src -name "*.java")

# Or use Ant (NetBeans)
ant clean && ant jar
```

### 2. **Run with GUI**

```bash
java -cp build/classes main.Main --gui
```

The GUI will open with:
- **Input Panel**: Select temperature, pressure, elements, phases, and calculation method
- **Results Window**: View calculation outputs and parsed database information
- **Log Console**: Monitor real-time execution with adjustable log levels

### 3. **Run CLI (Batch Mode)**

```bash
java -cp build/classes main.Main
```

Runs a default Ti-Zr binary system calculation at 500 K and 10,000 bar.

---

## Installation

### Requirements
- **Java 8** or higher (JDK or JRE)
- **Ant** (optional, for NetBeans builds)
- ~50 MB disk space

### Setup

```bash
# Clone the repository
git clone https://github.com/vijindal/expcvm10.git
cd expcvm10

# Compile
javac --release 8 -sourcepath src -d build/classes $(find src -name "*.java")

# Verify build
java -cp build/classes main.Main --gui
```

---

## Usage Guide

### Workflow: Single-Point Calculation

#### **Via GUI**
1. **Load Database**
   - The default Ti-Zr database (`data/tizr_kum_cvm.tdb`) is pre-configured
   - Elements: Ti, Zr
   - Phases: LIQUID

2. **Configure Calculation**
   - Temperature: 500 K
   - Pressure: 10,000 bar
   - Method: HM (or Gm, G)
   - Phase: LIQUID

3. **Run Calculation**
   - Click **Calculate**
   - Results appear in the Results Window
   - Execution time: typically 0.1–1 seconds

4. **View Logs**
   - Set log level to **RESULT** to see computed values
   - Set to **FLOW** to trace GUI → Service flow
   - Set to **ENGINE** for calculation engine details

#### **Via CLI**

```bash
# Run with default parameters (Ti-Zr, 500 K, 10,000 bar, HM method)
java -cp build/classes main.Main

# Expected output
# [INFO] CLI running default calculation demo
# [RESULT] runCalculation: method=HM, T=500, P=10,000
# [RESULT] Computed value: -4,567.151
# #Calculations took 0.094 sec
```

---

## Architecture Overview

### Layered Design

```
┌─────────────────────────────────────────────┐
│         Presentation Layer                  │
│   (GUI: Swing, CLI: Console Interface)      │
└──────────────────┬──────────────────────────┘
                   ↓
┌─────────────────────────────────────────────┐
│         Application Layer                   │
│  (CalculationService, OptimizationService)  │
└──────────────────┬──────────────────────────┘
                   ↓
┌─────────────────────────────────────────────┐
│         Domain Layer                        │
│  (ThermoCondition, ThermoResult, Ports)     │
└──────────────────┬──────────────────────────┘
                   ↓
┌─────────────────────────────────────────────┐
│      Infrastructure Layer                   │
│  (TDB Parser, Logger, Factory Implementations)
└──────────────────┬──────────────────────────┘
                   ↓
┌─────────────────────────────────────────────┐
│      Physics Core (Calculation Engines)     │
│  (GibbsModel, Phase Models, CVM Solvers)    │
└─────────────────────────────────────────────┘
```

### Key Packages

| Package | Purpose | Key Classes |
|---------|---------|------------|
| `src/main` | Application entry point | `Main.java` |
| `src/presentation` | UI layer | `GuiApp`, `CliApp`, `MainFrame` |
| `src/application` | Business logic orchestration | `CalculationService`, `OptimizationService` |
| `src/domain` | Domain models & contracts | `ThermoCondition`, `PhaseFactory` (interface) |
| `src/infrastructure` | Implementations & adapters | `TdbParser`, `ConsoleLogger`, `PhaseFactoryImpl` |
| `src/calbince` | Calculation engines | `calculate`, `CalModel`, `Methods`, `OptMrq` |
| `src/phase` | Thermodynamic models | `GibbsModel`, `RK` (Redlich-Kister), `CECVM` |
| `src/database` | Database layer | `tdb`, `stdst` (parser & access) |
| `src/utils` | Utilities | JAMA matrix library, IO helpers |

---

## Use Cases & Examples

### **Use Case 1: Single-Point Equilibrium Calculation**

**Goal**: Calculate Gibbs free energy at a fixed condition

```
Input:  System = Ti-Zr
        Temperature = 500 K
        Pressure = 10,000 bar
        Phase = LIQUID
        Method = HM (Enthalpy method)

Process: Load TDB → Extract system → Set conditions → Calculate
Output:  G (Gibbs free energy) = -4,567.151 J/mol
         Execution time: 94 ms
```

### **Use Case 2: Parameter Fitting (Assessment)**

**Goal**: Optimize phase model parameters against experimental data

```
Input:   Experimental data (enthalpy, equilibrium points)
         Phase model structure (e.g., RK interaction for LIQUID)
         Initial parameter guesses

Process: 1. Load experimental data
         2. Define phase model
         3. Run Levenberg-Marquardt optimizer
         4. Monitor fit quality with R² and residuals

Output:  Optimized parameters
         Fit statistics
         Validated model for export to TDB
```

### **Use Case 3: Step Calculation (Vary One Variable)**

**Goal**: Track property change as temperature varies

```
Input:   Base condition: P = 10,000 bar
         Variable: T from 300 to 1500 K (step 100 K)
         Phase: LIQUID

Output:  Series of (T, G) pairs
         Plot-ready data for phase diagram/thermodynamic curves
```

---

## Configuration & Logging

### **Log Levels**

| Level | Value | Use Case |
|-------|-------|----------|
| **SOLVER** | 300 | Deep CVM solver iterations (per-atom details) |
| **MODEL** | 400 | Thermodynamic model calculations (per-phase) |
| **ENGINE** | 500 | Core calculation engine (per-method) |
| **FLOW** | 700 | Service and controller flow (per-action) |
| **RESULT** | 800 | Computed values and summaries (default) |
| **WARN** | 900 | Potential issues |
| **ERROR** | 1000 | Errors and exceptions |

### **Setting Log Level (GUI)**

1. Open **Log Console** (embedded in main window)
2. Select log level from dropdown: **RESULT** (default) → **FLOW** → **ENGINE** → **SOLVER**
3. Optionally filter by class name

### **Configuration File**

Logging is configured via `LoggingConfig.java`:
- **Default level per package**: presentation=FLOW, application=FLOW, calbince=ENGINE, phase=MODEL
- **Output**: File (`data/expcvm.log`) + Console
- **Format**: `[HH:mm:ss.SSS] LEVEL [ClassName.method] message`

---

## Major Updates

### **Phase 13: Custom Logging System** (Completed)
- Replaced standard Java logging with 7 application-level log levels
- Structured method entry/exit tracing via `Trace` utility
- GUI log console with live filtering and level selection
- Backward compatibility with legacy `Print.f()` debug calls

### **Phase 12: Single-Tab GUI** (Completed)
- Refactored GUI to single-panel Swing layout
- Embedded real-time log console
- Dynamic input panel populated from TDB files
- Results window showing parsed elements and phases

### **Phase 7–10: Architecture Refactoring** (Completed)
- Migrated from flat structure to Clean Architecture
- Introduced application, domain, infrastructure, presentation layers
- Eliminated cross-layer dependencies
- Added service layer facades (CalculationService, OptimizationService)

### **Phase 1–6: Foundation** (Completed)
- Initial GUI implementation
- Runtime crash fixes
- Core calculation engines (CALPHAD, CVM solvers)
- TDB parser and database abstraction

---

## Project Status

**Current Build**: ✅ **PASSING** (Java 8, 120 compiled classes)
**Last Update**: Phase 13 (Logging Redesign)
**GUI Status**: ✅ Launches without errors

For detailed development progress, architecture decisions, and known limitations, see:
- **[PROJECT_STATUS.md](PROJECT_STATUS.md)** — Internal development phases and component status
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Architectural layer definitions and boundaries
- **[COMPARISON_WITH_GITHUB.md](COMPARISON_WITH_GITHUB.md)** — Refactoring summary

---

## Data Files

### **Thermodynamic Databases (`.tdb`)**

Located in `data/`:
- `tizr_kum_cvm.tdb` — Ti-Zr binary system with CALPHAD/CVM models

### **Transformation Matrices**

Located in `data/transmat/`:
Pre-computed energy transformation matrices for CVM solvers (crystal structure specific).

### **Standard State Database**

Located in `data/sgte/`:
Elemental reference properties (SGTE standards).

---

## Build Outputs

- **Compiled classes**: `build/classes/`
- **JAR file**: `dist/expcvm10.jar` (generated by Ant)
- **Logs**: `data/expcvm.log`

---

## System Requirements

| Component | Requirement |
|-----------|-------------|
| **Java** | 8 or higher |
| **RAM** | 256 MB minimum (1+ GB for large systems) |
| **Disk** | ~50 MB |
| **OS** | Windows, macOS, Linux |

---

## Troubleshooting

### **GUI doesn't launch**
```bash
# Check Java version
java -version

# Verify build
javac -version

# Rebuild from scratch
rm -rf build/classes
javac --release 8 -sourcepath src -d build/classes $(find src -name "*.java")
java -cp build/classes main.Main --gui
```

### **TDB file not found**
- Ensure you're in the project root directory
- Check that `data/tizr_kum_cvm.tdb` exists
- Verify file path in log output

### **Calculations are slow**
- Reduce log level to **RESULT** or lower
- Avoid **SOLVER** level (very verbose, 1000s of messages)
- Check system resources (RAM, CPU)

### **Unexpected results**
- Check **Results Window** for parsed elements/phases
- Compare with known reference values
- Set log level to **ENGINE** to trace calculation steps

---

## Developer Resources

### **Adding a New Phase Model**

1. Create `src/phase/solution/cecvm/NewModel.java` extending `GibbsModel`
2. Implement `calG()` and `calGm()` methods
3. Register in `PhaseFactoryImpl.createPhase()` (infrastructure layer)
4. Add corresponding TDB data

### **Adding a New Calculation Type**

1. Create use case in `src/application/calculation/NewUseCase.java`
2. Wire into `CalculationService` (application layer)
3. Expose via GUI or CLI (presentation layer)

### **Contributing Logging**

Use the `Trace` utility for method entry/exit:
```java
import infrastructure.logging.Trace;

public void myMethod() {
    Trace.enter(this, "myMethod");
    try {
        // ... calculation code ...
        Trace.exit(this, "myMethod", 42); // 42ms elapsed
    } catch (Exception e) {
        Trace.error(this, "myMethod", e);
    }
}
```

---

## License & Citation

**Author**: Vijay Jindal
**Refactored**: March 2026
**Language**: Java 8+
**License**: See repository for license details

---

## Contact & Support

- **Issues**: [GitHub Issues](https://github.com/vijindal/expcvm10/issues)
- **Questions**: Refer to `PROJECT_STATUS.md` for detailed component documentation

---

## Quick Reference

```bash
# Compile
javac --release 8 -sourcepath src -d build/classes $(find src -name "*.java")

# Run GUI
java -cp build/classes main.Main --gui

# Run CLI
java -cp build/classes main.Main

# Build JAR (requires Ant)
ant clean && ant jar && ant run

# View logs
tail -f data/expcvm.log
```

---

**Last Updated**: March 21, 2026 | **Version**: 10.00
