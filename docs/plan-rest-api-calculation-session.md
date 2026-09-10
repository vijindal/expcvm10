# REST API for `CalculationSession` — external, cross-language access

## Context

`CalculationSession` (`src/session/CalculationSession.java`) is the
UI-agnostic coordinator introduced in `docs/plan-3layer-core-dataflow.md`,
designed to be usable by "many UI layers such GUI, CLI, API." So far only
in-process Java callers (the GUI, a smoke/unit test) exercise it.

The user's actual target: **other code — not necessarily Java, not
necessarily in this process — should be able to drive thermodynamic
calculations through this codebase over a standard protocol.** A plain
Java facade class only helps other JVM code in the same process; it is not
a "standard protocol." The correct shape for this requirement is an
**HTTP/REST API with JSON request/response bodies** — the de facto standard
protocol for this kind of cross-language, cross-process access.

**What this needs that the codebase has zero of today:**
- An HTTP server. **No new dependency needed** — the JDK (this project
  runs on JDK 25) has shipped `com.sun.net.httpserver.HttpServer` since
  Java 6, with meaningful improvements (virtual-thread-friendly executors,
  `SimpleFileServer`) in Java 18+. Sufficient for this; no need for
  Jetty/Spark/etc.
- JSON serialization. **No JSON library exists anywhere in this project**
  (confirmed by search). Decision: add a small, widely-used JSON library
  as a new dependency (e.g. `org.json` or Gson) to `lib/`, rather than
  hand-rolling a parser/writer — standard practice for a REST API, and the
  DTOs involved (`EquilibriumResult`, `PhaseDiagram`, request parameters)
  are simple enough that a general-purpose library handles them with no
  custom work.

This plan covers this new HTTP/JSON layer only. It does not change
`CalculationSession`, `ThermodynamicSystem`, or any solver/model class —
this is purely an additive layer on top of what already exists.

## Key design tension: `CalculationSession` is stateful; HTTP is stateless

`CalculationSession` is deliberately **not thread-safe** and designed for
**one sequential caller** (see its own Javadoc) — it holds a
`ThermodynamicSystem` and the latest result as mutable instance fields.
A real HTTP API serves potentially many concurrent clients. This must be
resolved explicitly, not glossed over:

- **One `CalculationSession` instance per API session, not a shared
  singleton.** The API layer must map each client's session to its own
  `CalculationSession` instance — e.g. a session ID (returned from a
  "create session" call, or derived from an API key/token) keyed to an
  in-memory map of live `CalculationSession` objects.
- Two concrete session-lifecycle models to choose between (see Open
  Questions below): (a) explicit session creation/destruction endpoints
  (`POST /sessions` → session ID; all subsequent calls include that ID;
  `DELETE /sessions/{id}` to free it), matching how `CalculationSession`
  already separates `setModel` from `calculate*`; or (b) a single
  "stateless" endpoint per calculation that takes model details AND
  calculation details together in one request body, building a
  short-lived `CalculationSession` internally per request (simpler
  protocol, but loses the "reuse the system across calculations" benefit
  `CalculationSession` was built for — every call re-parses the TDB).

## Proposed endpoint design (draft — confirm before implementing)

Following option (a) above, since it's the one that actually uses
`CalculationSession`'s reuse behavior rather than defeating it:

```
POST   /sessions
       -> { "sessionId": "..." }
       Creates a new, empty CalculationSession.

PUT    /sessions/{id}/model
       body: { "tdbFilePath": "...", "elements": ["V","ZR"], "phases": ["V2ZR"] }
       -> 204 No Content (or the model key echoed back)
       Calls CalculationSession.setModel(...). No-op rebuild-wise if
       unchanged, exactly as CalculationSession already guarantees.

POST   /sessions/{id}/calculations/equilibrium
       body: { "T": 1000, "P": 10000, "composition": [0.667, 0.333] }
       -> 200 { ...serialized EquilibriumResult... }
       Calls calculateEquilibrium(...), then serializes currentEquilibriumResult().

POST   /sessions/{id}/calculations/phase-diagram
       body: { "axes": [...], "startAxes": [...], "fixedT":.., "fixedP":.., "composition": [...] }
       -> 200 { ...serialized PhaseDiagram... }

POST   /sessions/{id}/calculations/step     -> 501 Not Implemented
POST   /sessions/{id}/calculations/map      -> 501 Not Implemented
       (CalculationSession.calculateStep/calculateMap are themselves
       stubs today -- see docs/plan-3layer-core-dataflow.md Step 3. The
       API surface should exist and return a clear, correct HTTP status
       for "not yet implemented," not a generic 500 or silent wrong answer.)

DELETE /sessions/{id}
       Frees the session's CalculationSession/ThermodynamicSystem.
```

Error mapping (`CalculationSession`'s exceptions → HTTP status), to nail
down during implementation:
- `IllegalStateException` ("no model set") → 409 Conflict or 400 Bad Request
- `UnsupportedOperationException` (step/map stubs) → 501 Not Implemented
- `IOException` (bad TDB path) → 400 Bad Request or 404, with a clear body
- Unexpected/solver exceptions → 500, but with a structured JSON error
  body (`{"error": "..."}`), never a raw stack trace to the client

## Decisions (2026-09-10)

1. **Session lifecycle: explicit sessions.** `POST /sessions` returns an
   ID; `PUT .../model` and `POST .../calculations/*` use that ID. This is
   what actually exercises `CalculationSession`'s build-once/reuse design
   rather than defeating it with a fresh `ThermodynamicSystem` per call.
2. **JSON library: Gson.** Reflects over `EquilibriumResult`/
   `PhaseDiagram`'s existing fields with minimal hand-written mapping code,
   at the cost of a slightly heavier dependency than `org.json`.
3. **Concurrency: synchronize per session.** A second request to a session
   ID that's mid-calculation waits for the first to finish rather than
   being rejected — one lock per `CalculationSession` instance. Simple,
   never corrupts state; the tradeoff (a slow calculation blocks a second
   one on the *same* session) is acceptable since different clients should
   use different sessions anyway.

**Still open, deferred (not blocking initial implementation):**
- **Session expiry/cleanup.** In-memory sessions need a lifecycle policy
  (idle timeout? explicit `DELETE` only? server restart clears all?) —
  otherwise this is a memory leak in a long-running server. Deferred to a
  later pass; the first implementation supports explicit `DELETE` only.
- **Authentication/authorization.** Out of scope for a first version
  (assume trusted network / localhost), but flagged explicitly as
  deliberately deferred, not forgotten, since "production-level" was
  mentioned as a future direction.
- **Source tree placement.** A new top-level package, `src/api/` (sibling
  to `ui/`, `system/`, `calc/`, `session/`), mirroring the reasoning that
  put `CalculationSession` in its own `session/` package rather than
  under `ui/`.

## Explicitly out of scope for this pass

- Any change to `CalculationSession`, `ThermodynamicSystem`, or any
  solver/model class.
- Building real `calculateStep`/`calculateMap` engines (still stubs,
  per `docs/plan-3layer-core-dataflow.md`) — the API exposes them as
  501 Not Implemented, it does not implement them.
- Authentication, HTTPS/TLS, rate limiting, and other production hardening
  — noted as future work once the core protocol shape is validated.
- CLI/GUI wiring through `CalculationSession` — tracked separately (see
  `docs/plan-3layer-core-dataflow.md`); this plan is scoped to the new
  HTTP/JSON layer only.

## Implementation  ✅ DONE (2026-09-10)

Implemented in a new `src/api/` package:
- `CalculationApiServer` — the HTTP server (`com.sun.net.httpserver`,
  no new dependency), routing the six endpoints above.
- `SessionStore` — in-memory `Map<String, Entry>`, one `CalculationSession`
  + one lock per API session id (`ConcurrentHashMap` for the map itself;
  `synchronized (entry.lock)` around every operation on one session, per
  the concurrency decision above).
- `api.dto.*` — wire-format DTOs (`SetModelRequest`, `EquilibriumRequest`,
  `EquilibriumResponse`, `PhaseDiagramRequest`, `PhaseDiagramResponse`,
  `ErrorResponse`). **`PhaseDiagram`/`EquilibriumResult` are NOT serialized
  directly** — found during implementation that `DiagramNode` holds
  `DiagramExit`s that reference their `parentNode` back, a real cycle that
  would make Gson's reflection-based serializer recurse forever.
  `PhaseDiagramResponse` flattens nodes/lines into plain, cycle-free data
  (nodes referenced by integer id, not embedded).
- `ApiMain` — standalone launcher (`java -cp ... api.ApiMain [port]`,
  default 8080).
- `lib/gson-2.11.0.jar` — added, SHA-1 verified against Maven Central
  (`527175ca6d81050b53bdd4c457a6d6e017626b0e`).

  **Note:** `lib/` is entirely gitignored in this repo (see commit
  `cece461`, "Remove lib/ JARs from version control" — deliberate,
  applies to the pre-existing JUnit jars too, not something changed
  here). This means `gson-2.11.0.jar` will NOT survive a fresh clone and
  is not restorable from git history — anyone building this project
  needs to re-download it (e.g. from
  `https://repo1.maven.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar`,
  verified against the SHA-1 above) alongside the existing JUnit jars.
  There is no documented dependency-restoration process in this repo
  (checked `README.md` — no build/dependencies section exists); this is
  a pre-existing gap this addition inherits, not something introduced
  by it, but worth fixing at some point (a `lib/README.md` listing every
  required jar + its source/checksum, or a small fetch script) so the
  project remains buildable from a clean clone.

**Verified:**
- `src/test/CalculationApiServerTest.java` — starts a real server on a
  test port, drives the full session lifecycle over real HTTP
  (`java.net.HttpURLConnection`, no test-only HTTP client dependency
  needed), and checks the calG scenario's JSON response against
  `CalculationSessionCalGTest`'s known Java-side values
  (`G=-137349.4480`, `mu[0]=116674.3539155658`) — confirms the REST API
  reproduces the in-process result exactly, not just "returns something."
  Also covers: 409 when no model is set, 501 for step/map, 404 for an
  unknown session id, and two-session isolation. All 13 checks pass.
- Manually cross-checked via `curl` before writing the automated test
  (session create → set model → equilibrium → phase-diagram → delete),
  confirming the phase-diagram endpoint's cycle-avoidance DTO works —
  no stack overflow, clean JSON, 200 OK.
- Whole-project compile clean with the new `gson` dependency; all
  pre-existing tests (`CalculationSessionTest`, `RkModelBaselineTest`,
  `V2ZrGibbsBaselineTest`, `EMatNCTest`, `ThermodynamicSystemSmokeTest`)
  re-run clean, confirming no regression.
