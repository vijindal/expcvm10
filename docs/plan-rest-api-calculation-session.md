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

## Open questions — resolve before implementation

1. **Session lifecycle model:** explicit `POST /sessions` + session-ID
   header/path (proposed above), vs. one-shot stateless calls that bundle
   model + calculation details together (simpler, but loses reuse). The
   proposed design assumes the former; confirm.
2. **Session expiry/cleanup.** In-memory sessions need a lifecycle policy
   (idle timeout? explicit `DELETE` only? server restart clears all?) —
   otherwise this is a memory leak in a long-running server. Not decided.
3. **Concurrency.** `CalculationSession` itself is not thread-safe. If two
   requests hit the same session ID concurrently, what happens — reject
   the second (409), queue it, or accept undefined behavior? Needs an
   explicit answer (likely: synchronize per-session, reject/queue
   concurrent calls to the same session ID).
4. **JSON library choice.** `org.json` (tiny, no schema/annotation
   machinery, very manual mapping) vs. Gson (reflection-based, less
   boilerplate, slightly heavier). Given `EquilibriumResult`/`PhaseDiagram`
   are plain data-holding classes already, either works; recommend Gson
   for less hand-written mapping code, but this is a small decision either
   way — confirm before adding the dependency.
5. **Authentication/authorization.** Out of scope for a first version
   (assume trusted network / localhost), but flag explicitly as
   deliberately deferred, not forgotten, since "production-level" was
   mentioned as a future direction.
6. **Where this lives in the source tree.** A new top-level package,
   e.g. `src/api/` (sibling to `ui/`, `system/`, `calc/`, `session/`),
   mirroring the reasoning that put `CalculationSession` in its own
   `session/` package rather than under `ui/`.

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

## Verification plan (once implemented)

- Start the server, use `curl` (or an equivalent scriptable HTTP client)
  to exercise the full session lifecycle: create session → set model
  (V-Zr, V2ZR) → run the calG single-point calculation from
  `CalculationSessionCalGTest` → confirm the JSON response's numeric
  values match that test's Java-side output exactly.
- Confirm step/map endpoints return 501 with a clear message, not a crash.
- Confirm calling a calculation endpoint before `PUT .../model` returns
  the mapped 409/400, not a 500.
- Confirm two sessions (two session IDs) with different models don't
  interfere with each other (session isolation).
