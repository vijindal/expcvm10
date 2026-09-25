# REST API Documentation

## Overview

The expCVM10 REST API provides HTTP/JSON access to thermodynamic equilibrium and phase-diagram calculations. The API uses a **session-based workflow**: each client creates a session, loads a model (TDB file + elements + phases), runs calculations within that session, and deletes the session when done.

All requests and responses use JSON. The API runs on `com.sun.net.httpserver` (not a servlet container) and listens on a configurable port (default 8080).

## Session Workflow

```
POST /sessions
    ↓ { "sessionId": "..." }
PUT /sessions/{id}/model
    ↓ (model loaded, reused for all subsequent calculations in this session)
(optional) POST /sessions/{id}/elements
(optional) POST /sessions/{id}/phases
    ↓
POST /sessions/{id}/calculations/{kind}
    ↓ { result... }
(repeat calculations as needed)
    ↓
DELETE /sessions/{id}
```

Each session maintains an independent `ApplicationLayer` instance, so models and calculations are isolated per session. The model set via `PUT /model` is reused for all subsequent calculations within that session — you do not need to set it again.

## Session Management

### Create Session

**Request**
```
POST /sessions
(empty body)
```

**Response (200 OK)**
```json
{
  "sessionId": "abc123def456"
}
```

**Status codes**
- `200` — Session created
- `500` — Internal server error

---

### Set Model

**Request**
```
PUT /sessions/{id}/model
Content-Type: application/json

{
  "tdbFilePath": "data/agcu.TDB",
  "elements": ["AG", "CU"],
  "phases": ["LIQUID", "FCC_A1"]
}
```

**Fields**
- `tdbFilePath` (string, required) — Path to TDB file relative to server working directory
- `elements` (array of strings, required) — Element symbols to include
- `phases` (array of strings, required) — Phase names to consider in calculations

**Response (204 No Content)**
```
(empty)
```

**Status codes**
- `204` — Model loaded successfully
- `404` — Session not found
- `400` — TDB file not found or invalid format
- `500` — Internal error

---

### Delete Session

**Request**
```
DELETE /sessions/{id}
(empty body)
```

**Response (204 No Content)**
```
(empty)
```

**Status codes**
- `204` — Session deleted
- `404` — Session not found

---

## Model Browsing

These endpoints allow discovery of elements and phases in a TDB without setting a session's model. Useful for UI initialization.

### List Elements (Browse Only)

**Request**
```
POST /sessions/{id}/elements
Content-Type: application/json

{
  "tdbFilePath": "data/agcu.TDB"
}
```

**Response (200 OK)**
```json
{
  "elements": ["AG", "CU"]
}
```

**Status codes**
- `200` — Elements listed
- `404` — Session not found
- `400` — TDB file error

---

### List Phases (Browse Only)

**Request**
```
POST /sessions/{id}/phases
Content-Type: application/json

{
  "tdbFilePath": "data/agcu.TDB",
  "elements": ["AG", "CU"]
}
```

**Response (200 OK)**
```json
{
  "phases": ["LIQUID", "FCC_A1", "SLFCC_A1"]
}
```

**Status codes**
- `200` — Phases listed
- `404` — Session not found
- `400` — TDB file error or invalid elements

---

## Calculations

All calculation endpoints require:
1. A session to exist
2. A model to be set via `PUT /model`

If no model is set, the endpoint returns `409 Conflict`.

### Single-Point Equilibrium

**Request**
```
POST /sessions/{id}/calculations/equilibrium
Content-Type: application/json

{
  "T": 1000.0,
  "P": 101325.0,
  "composition": [0.5, 0.5]
}
```

**Fields**
- `T` (number, required) — Temperature in Kelvin
- `P` (number, required) — Pressure in Pascal
- `composition` (array of numbers, required) — Mole fractions (one per element, must sum to ~1.0)

**Response (200 OK)**
```json
{
  "T": 1000.0,
  "P": 101325.0,
  "mu": [-56683.44566717048, -46564.47907887187],
  "converged": true,
  "iterations": 3,
  "stablePhases": [
    {
      "phaseName": "FCC_A1",
      "modelType": "CEF",
      "amount": 0.540192,
      "x": [0.896934415854347, 0.10306558414565309],
      "y": [0.896934415854347, 0.10306558414565309, 1.0],
      "G": -55640.5285,
      "drivingForce": 5.457e-12
    },
    {
      "phaseName": "FCC_A1",
      "modelType": "CEF",
      "amount": 0.459808,
      "x": [0.03367320261333245, 0.9663267973866676],
      "y": [0.03367320261333245, 0.9663267973866676, 1.0],
      "G": -46905.2171,
      "drivingForce": 0.0
    }
  ],
  "metastablePhases": [
    {
      "phaseName": "LIQUID",
      "modelType": "CEF",
      "amount": 0.0,
      "x": [0.5, 0.5],
      "y": [0.5, 0.5],
      "G": -50835.0,
      "drivingForce": -788.92
    }
  ]
}
```

**Response fields**
- `T`, `P` (numbers) — Temperature and pressure (echoed from request)
- `mu` (array) — Chemical potentials (one per element, in units of J/mol)
- `converged` (boolean) — Whether equilibrium converged
- `iterations` (integer) — Newton iterations used
- `stablePhases` (array of Phase) — Phases present at equilibrium
- `metastablePhases` (array of Phase) — Phases with negative driving force (not present)

**Phase object fields**
- `phaseName` (string) — Phase identifier from TDB
- `modelType` (string) — Model type: "CEF" (Compound Energy Formalism) or "CVM" (Cluster Variation Method)
- `amount` (number) — Amount in formula units (f.u.)
- `x` (array) — Site fractions (one per constituent, context-dependent on phase definition)
- `y` (array) — Internal variables (same length as x)
- `G` (number) — Molar Gibbs energy of the phase (J/mol)
- `drivingForce` (number) — Thermodynamic driving force; negative means the phase is unstable

**Status codes**
- `200` — Equilibrium calculated
- `404` — Session not found
- `409` — No model set; call `PUT /model` first
- `400` — Invalid request (bad T/P/composition)

---

### Phase Diagram (Algorithms B/C1/C2/D)

Automated binary phase-diagram tracing. Axis 0 is walked in fixed increments; Axis 1 is released and solved exactly at phase boundaries.

**Request**
```
POST /sessions/{id}/calculations/phase-diagram
Content-Type: application/json

{
  "axes": [
    {
      "type": "TEMPERATURE",
      "name": "T",
      "min": 500.0,
      "max": 1200.0,
      "step": 50.0
    },
    {
      "type": "COMPOSITION",
      "componentIndex": 1,
      "name": "x(CU)",
      "min": 0.0,
      "max": 1.0,
      "step": 0.05
    }
  ],
  "startAxes": [500.0, 0.5],
  "fixedT": 0.0,
  "fixedP": 0.0,
  "composition": [0.5, 0.5]
}
```

**Axis type specifications**
- `type` (string) — "TEMPERATURE", "PRESSURE", or "COMPOSITION"
- `componentIndex` (integer, required for COMPOSITION) — 0-based index into the elements list
- `name` (string) — Human-readable axis name
- `min`, `max` (numbers) — Axis range
- `step` (number) — Walking step size

**Fields**
- `axes` (array of 2 AxisSpec) — Axis 0 (walked axis) and Axis 1 (released axis, must be COMPOSITION)
- `startAxes` (array of 2 numbers) — Starting point on each axis
- `fixedT` (number) — Fixed temperature (K) if no axis is TEMPERATURE
- `fixedP` (number) — Fixed pressure (Pa) if no axis is PRESSURE
- `composition` (array) — Overall composition (mole fractions)

**Response (200 OK)**
```json
{
  "axisNames": ["T", "x(CU)"],
  "axisMin": [500.0, 0.0],
  "axisMax": [1200.0, 1.0],
  "complete": true,
  "message": "Phase diagram complete",
  "lines": [
    {
      "startAxisValues": [500.0, 0.5],
      "endAxisValues": [1055.9, 0.321],
      "equilibria": [ ... ],
      "reason": "phase_change"
    }
  ],
  "nodes": [
    {
      "axisValues": [500.0, 0.5],
      "equilibrium": { ... },
      "stablePhases": ["FCC_A1"]
    }
  ]
}
```

**Response fields**
- `axisNames` (array) — Names of the two axes
- `axisMin`, `axisMax` (arrays) — Axis ranges
- `complete` (boolean) — Whether the full diagram was traced (true) or terminated early
- `message` (string) — Human-readable status (e.g., "Phase diagram complete" or "Stopped at axis limit")
- `lines` (array) — Phase boundary line segments, each with a list of equilibria along the line
- `nodes` (array) — Key points where phase sets change

**Status codes**
- `200` — Diagram calculated
- `404` — Session not found
- `409` — No model set
- `400` — Invalid request (bad axis, composition, etc.)

---

### STEP (Single-Axis Scan)

Walk one axis at fixed increments, calculating equilibrium at each step.

**Request**
```
POST /sessions/{id}/calculations/step
Content-Type: application/json

{
  "axis": {
    "type": "TEMPERATURE",
    "name": "T",
    "min": 800.0,
    "max": 1200.0,
    "step": 50.0
  },
  "fixedT": 0.0,
  "fixedP": 101325.0,
  "composition": [0.5, 0.5]
}
```

**Fields**
- `axis` (AxisSpec) — Single axis to walk
- `fixedT`, `fixedP` (numbers) — Fixed temperature/pressure if not the walking axis
- `composition` (array) — Overall composition

**Response (200 OK)**
Same structure as phase-diagram response (lines + nodes).

**Status codes**
- `200` — Scan complete
- `404` — Session not found
- `409` — No model set
- `400` — Invalid request

---

### MAP (Two-Axis ZPF Phase Diagram)

Two-axis ZPF phase-diagram map using Algorithms C1/C2/D. Axis 0 is walked; Axis 1 (must be COMPOSITION) is released and solved exactly.

**Request**
```
POST /sessions/{id}/calculations/map
Content-Type: application/json

{
  "axes": [
    {
      "type": "TEMPERATURE",
      "name": "T",
      "min": 500.0,
      "max": 1200.0,
      "step": 50.0
    },
    {
      "type": "COMPOSITION",
      "componentIndex": 1,
      "name": "x(CU)",
      "min": 0.0,
      "max": 1.0,
      "step": 0.05
    }
  ],
  "startAxes": [500.0, 0.5],
  "fixedT": 0.0,
  "fixedP": 101325.0,
  "composition": [0.5, 0.5]
}
```

**Response (200 OK)**
Same structure as phase-diagram response.

**Status codes**
- `200` — Map calculated
- `404` — Session not found
- `409` — No model set
- `400` — Invalid request

---

### Coarse Binary (2D Grid)

Independent equilibrium calculations on a 2D grid (e.g., composition × temperature). Results are **not** connected phase boundaries.

**Request**
```
POST /sessions/{id}/calculations/coarse-binary
Content-Type: application/json

{
  "axisX": {
    "type": "COMPOSITION",
    "componentIndex": 1,
    "name": "x(CU)",
    "min": 0.0,
    "max": 1.0,
    "step": 0.1
  },
  "axisY": {
    "type": "TEMPERATURE",
    "name": "T",
    "min": 800.0,
    "max": 1200.0,
    "step": 100.0
  },
  "fixedT": 0.0,
  "fixedP": 101325.0,
  "composition": [0.5, 0.5]
}
```

**Fields**
- `axisX`, `axisY` (AxisSpec) — Grid axes (X and Y dimensions)
- `fixedT`, `fixedP` (numbers) — Fixed temperature/pressure if not grid axes
- `composition` (array) — Overall composition (renormalized for swept axes)

**Response (200 OK)**
```json
{
  "axisNames": ["x(CU)", "T"],
  "axisMin": [0.0, 800.0],
  "axisMax": [1.0, 1200.0],
  "gridPoints": [
    {
      "axisValues": [0.0, 800.0],
      "equilibrium": { ... }
    },
    ...
  ]
}
```

**Status codes**
- `200` — Grid calculated
- `404` — Session not found
- `409` — No model set
- `400` — Invalid request

---

### Coarse Ternary (2D Grid over Composition)

Independent equilibrium calculations on a 2D grid of two composition axes. The third component is renormalized to maintain Σx=1.

**Request**
```
POST /sessions/{id}/calculations/coarse-ternary
Content-Type: application/json

{
  "axisX": {
    "type": "COMPOSITION",
    "componentIndex": 1,
    "name": "x(B)",
    "min": 0.0,
    "max": 0.5,
    "step": 0.1
  },
  "axisY": {
    "type": "COMPOSITION",
    "componentIndex": 2,
    "name": "x(C)",
    "min": 0.0,
    "max": 0.5,
    "step": 0.1
  },
  "fixedT": 1000.0,
  "fixedP": 101325.0,
  "composition": [0.33, 0.33, 0.34]
}
```

**Response (200 OK)**
Same structure as coarse-binary.

**Status codes**
- `200` — Grid calculated
- `404` — Session not found
- `409` — No model set
- `400` — Invalid request

---

### Assessment (Parameter Optimization)

**Request**
```
POST /sessions/{id}/calculations/assessment
Content-Type: application/json

{ }
```

**Response (501 Not Implemented)**
```json
{
  "error": "Assessment/parameter optimization not yet implemented"
}
```

Parameter assessment is not yet wired. Use the CLI's `opt` command for legacy assessment workflows.

---

## HTTP Status Codes

| Code | Meaning | When it occurs |
|------|---------|---------------|
| `200` | OK | Successful calculation or response |
| `204` | No Content | Model set successfully, session deleted |
| `400` | Bad Request | Invalid JSON, bad composition values, missing required fields |
| `404` | Not Found | Session ID does not exist, or unknown endpoint |
| `409` | Conflict | No model set yet (must `PUT /model` first) |
| `501` | Not Implemented | Assessment endpoint called |
| `500` | Internal Server Error | Unexpected runtime error |

---

## Error Response

All error responses (4xx, 5xx) return JSON:

```json
{
  "error": "Descriptive error message"
}
```

No stack traces are sent to the client; stack traces are logged server-side.

---

## Example: Complete Session

```bash
# 1. Create session
curl -X POST http://localhost:8080/sessions \
  -H "Content-Type: application/json"
# Response: { "sessionId": "abc123" }

# 2. Set model
curl -X PUT http://localhost:8080/sessions/abc123/model \
  -H "Content-Type: application/json" \
  -d '{
    "tdbFilePath": "data/agcu.TDB",
    "elements": ["AG", "CU"],
    "phases": ["LIQUID", "FCC_A1"]
  }'
# Response: (204 No Content)

# 3. Calculate equilibrium
curl -X POST http://localhost:8080/sessions/abc123/calculations/equilibrium \
  -H "Content-Type: application/json" \
  -d '{
    "T": 1000.0,
    "P": 101325.0,
    "composition": [0.5, 0.5]
  }'
# Response: { "T": 1000.0, "P": 101325.0, ... }

# 4. Delete session
curl -X DELETE http://localhost:8080/sessions/abc123
# Response: (204 No Content)
```

---

## Notes

- **Session isolation:** Each session owns its own `ApplicationLayer` and model. Sessions do not share state.
- **Thread safety:** Each session is protected by a lock during calculation. Concurrent requests to the same session are serialized.
- **Model reuse:** Once set via `PUT /model`, the model is reused for all subsequent calculations in that session without re-parsing the TDB.
- **Working directory:** File paths (e.g., `tdbFilePath`) are relative to the server's working directory.
- **Port selection:** Start the server with `java -cp dist/expcvm10.jar ui.api.ApiMain [port]` (build first with `./gradlew build`). Default is 8080.
