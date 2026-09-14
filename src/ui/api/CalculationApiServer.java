package ui.api;

import ui.api.dto.ElementsRequest;
import ui.api.dto.ElementsResponse;
import ui.api.dto.EquilibriumRequest;
import ui.api.dto.EquilibriumResponse;
import ui.api.dto.ErrorResponse;
import ui.api.dto.PhaseDiagramRequest;
import ui.api.dto.PhasesRequest;
import ui.api.dto.PhasesResponse;
import ui.api.dto.SetModelRequest;
import calc.diagram.AxisConfig;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import session.calctype.CalculationInterface;
import session.calctype.CalculationKind;
import session.calctype.CalculationOutcome;
import session.calctype.ModelSelection;
import system.ports.EquilibriumResult;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP/JSON REST API exposing {@link session.CalculationSession} to
 * external, cross-process, cross-language callers.
 *
 * <p>See {@code docs/plan-rest-api-calculation-session.md} for the full
 * design: session lifecycle (explicit create/delete, one
 * {@code CalculationSession} per session id, never a shared singleton),
 * concurrency policy (synchronized per session), and error-to-HTTP-status
 * mapping.
 *
 * <p>Endpoints:
 * <pre>
 *   POST   /sessions                                   -> { "sessionId": "..." }
 *   PUT    /sessions/{id}/model                         -> 204
 *   POST   /sessions/{id}/elements                      -> 200 ElementsResponse
 *   POST   /sessions/{id}/phases                        -> 200 PhasesResponse
 *   POST   /sessions/{id}/calculations/equilibrium      -> 200 EquilibriumResponse
 *   POST   /sessions/{id}/calculations/phase-diagram    -> 501 (not implemented yet)
 *   POST   /sessions/{id}/calculations/assessment       -> 501 (opt -- not implemented yet)
 *   POST   /sessions/{id}/calculations/step             -> 501
 *   POST   /sessions/{id}/calculations/map              -> 501
 *   DELETE /sessions/{id}                                -> 204
 * </pre>
 *
 * <p>Every {@code /calculations/{kind}} request is routed through {@link
 * session.calctype.CalculationInterface}, never {@link
 * session.CalculationSession#calculateEquilibrium}/{@code calculatePhaseDiagram}
 * directly: {@code equilibrium}/{@code phase-diagram} call {@link
 * session.calctype.CalculationInterface#runCalculating} using the {@link
 * session.calctype.ModelSelection} recorded by the most recent {@code PUT
 * .../model} call (409 if none has been made yet); {@code assessment} calls
 * {@link session.calctype.CalculationInterface#runAssessing}, which never
 * touches the session at all. The {@code cal}/{@code opt} group choice is
 * therefore not a separate landing request -- each calculation kind already
 * names its own group in the URL.
 *
 * <p>{@code /elements} and {@code /phases} are the browsing counterpart to
 * {@code /model}: routed through {@link ui.layer.ModelBrowseService}, the
 * same shared bridge to {@link session.CalculationSession#availableElements}/
 * {@link session.CalculationSession#availablePhasesFor} the GUI and CLI
 * use, so element/phase discovery (and its pseudo-element filtering)
 * behaves identically across all three UIs rather than being
 * reimplemented per UI.
 */
public final class CalculationApiServer {

    private static final Logger LOG = Logger.getLogger(CalculationApiServer.class.getName());

    private static final Pattern SESSION_MODEL_PATH =
            Pattern.compile("^/sessions/([^/]+)/model$");
    private static final Pattern SESSION_ELEMENTS_PATH =
            Pattern.compile("^/sessions/([^/]+)/elements$");
    private static final Pattern SESSION_PHASES_PATH =
            Pattern.compile("^/sessions/([^/]+)/phases$");
    private static final Pattern SESSION_CALC_PATH =
            Pattern.compile("^/sessions/([^/]+)/calculations/([a-z-]+)$");
    private static final Pattern SESSION_PATH =
            Pattern.compile("^/sessions/([^/]+)$");

    private final Gson gson = new Gson();
    private final SessionStore sessions = new SessionStore();
    private final HttpServer server;

    public CalculationApiServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/sessions", this::handleSessions);
    }

    public void start() {
        server.start();
        LOG.info("CalculationApiServer listening on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------

    private void handleSessions(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (Exception e) {
            LOG.warning("Unhandled error: " + e);
            sendJson(exchange, 500, new ErrorResponse("Internal error: " + e.getMessage()));
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equals(method) && "/sessions".equals(path)) {
            createSession(exchange);
            return;
        }

        Matcher modelMatch = SESSION_MODEL_PATH.matcher(path);
        if ("PUT".equals(method) && modelMatch.matches()) {
            setModel(exchange, modelMatch.group(1));
            return;
        }

        Matcher elementsMatch = SESSION_ELEMENTS_PATH.matcher(path);
        if ("POST".equals(method) && elementsMatch.matches()) {
            listElements(exchange, elementsMatch.group(1));
            return;
        }

        Matcher phasesMatch = SESSION_PHASES_PATH.matcher(path);
        if ("POST".equals(method) && phasesMatch.matches()) {
            listPhases(exchange, phasesMatch.group(1));
            return;
        }

        Matcher calcMatch = SESSION_CALC_PATH.matcher(path);
        if ("POST".equals(method) && calcMatch.matches()) {
            runCalculation(exchange, calcMatch.group(1), calcMatch.group(2));
            return;
        }

        Matcher sessionMatch = SESSION_PATH.matcher(path);
        if ("DELETE".equals(method) && sessionMatch.matches()) {
            deleteSession(exchange, sessionMatch.group(1));
            return;
        }

        sendJson(exchange, 404, new ErrorResponse("No route for " + method + " " + path));
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private void createSession(HttpExchange exchange) throws IOException {
        String id = sessions.create();
        sendJson(exchange, 200, new CreatedSession(id));
    }

    private void deleteSession(HttpExchange exchange, String id) throws IOException {
        sessions.remove(id);
        exchange.sendResponseHeaders(204, -1);
    }

    private void setModel(HttpExchange exchange, String sessionId) throws IOException {
        SessionStore.Entry entry = sessions.get(sessionId);
        if (entry == null) {
            sendJson(exchange, 404, new ErrorResponse("No such session: " + sessionId));
            return;
        }

        SetModelRequest req = readJson(exchange, SetModelRequest.class);

        synchronized (entry.lock) {
            try {
                entry.session.setModel(req.tdbFilePath, req.elements, req.phases);
                entry.modelSelection = new ModelSelection(req.tdbFilePath, req.elements, req.phases);
                exchange.sendResponseHeaders(204, -1);
                exchange.getResponseBody().close();
            } catch (IOException e) {
                sendJson(exchange, 400, new ErrorResponse("Could not load model: " + e.getMessage()));
            }
        }
    }

    private void listElements(HttpExchange exchange, String sessionId) throws IOException {
        SessionStore.Entry entry = sessions.get(sessionId);
        if (entry == null) {
            sendJson(exchange, 404, new ErrorResponse("No such session: " + sessionId));
            return;
        }

        ElementsRequest req = readJson(exchange, ElementsRequest.class);

        synchronized (entry.lock) {
            try {
                List<String> elements = entry.browse.selectableElements(req.tdbFilePath);
                sendJson(exchange, 200, new ElementsResponse(elements));
            } catch (IOException e) {
                sendJson(exchange, 400, new ErrorResponse("Could not read elements: " + e.getMessage()));
            }
        }
    }

    private void listPhases(HttpExchange exchange, String sessionId) throws IOException {
        SessionStore.Entry entry = sessions.get(sessionId);
        if (entry == null) {
            sendJson(exchange, 404, new ErrorResponse("No such session: " + sessionId));
            return;
        }

        PhasesRequest req = readJson(exchange, PhasesRequest.class);

        synchronized (entry.lock) {
            try {
                List<String> phases = entry.browse.selectablePhases(req.tdbFilePath, req.elements);
                sendJson(exchange, 200, new PhasesResponse(phases));
            } catch (IOException e) {
                sendJson(exchange, 400, new ErrorResponse("Could not read phases: " + e.getMessage()));
            }
        }
    }

    private void runCalculation(HttpExchange exchange, String sessionId, String kind)
            throws IOException {
        SessionStore.Entry entry = sessions.get(sessionId);
        if (entry == null) {
            sendJson(exchange, 404, new ErrorResponse("No such session: " + sessionId));
            return;
        }

        synchronized (entry.lock) {
            try {
                switch (kind) {
                    case "equilibrium":
                        runEquilibrium(exchange, entry);
                        break;
                    case "phase-diagram":
                        runPhaseDiagram(exchange, entry);
                        break;
                    case "assessment":
                        runAssessment(exchange);
                        break;
                    case "step":
                    case "map":
                        sendJson(exchange, 501,
                                new ErrorResponse(kind + " calculation not yet implemented"));
                        break;
                    default:
                        sendJson(exchange, 404, new ErrorResponse("Unknown calculation type: " + kind));
                }
            } catch (IllegalStateException e) {
                sendJson(exchange, 409, new ErrorResponse(e.getMessage()));
            } catch (UnsupportedOperationException e) {
                sendJson(exchange, 501, new ErrorResponse(e.getMessage()));
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, new ErrorResponse(e.getMessage()));
            }
        }
    }

    private void runEquilibrium(HttpExchange exchange, SessionStore.Entry entry) throws IOException {
        if (entry.modelSelection == null) {
            sendJson(exchange, 409, new ErrorResponse("No model set -- PUT .../model first"));
            return;
        }
        EquilibriumRequest req = readJson(exchange, EquilibriumRequest.class);
        CalculationInterface.EquilibriumParams params =
                new CalculationInterface.EquilibriumParams(req.T, req.P, req.composition);
        EquilibriumResult result = CalculationInterface.runCalculating(
                entry.session, CalculationKind.EQUILIBRIUM, entry.modelSelection, params);
        sendJson(exchange, 200, new EquilibriumResponse(result));
    }

    private void runPhaseDiagram(HttpExchange exchange, SessionStore.Entry entry) throws IOException {
        if (entry.modelSelection == null) {
            sendJson(exchange, 409, new ErrorResponse("No model set -- PUT .../model first"));
            return;
        }
        PhaseDiagramRequest req = readJson(exchange, PhaseDiagramRequest.class);

        AxisConfig[] axes = new AxisConfig[req.axes.size()];
        for (int i = 0; i < axes.length; i++) {
            PhaseDiagramRequest.AxisSpec spec = req.axes.get(i);
            axes[i] = toAxisConfig(spec);
        }

        CalculationInterface.PhaseDiagramParams params = new CalculationInterface.PhaseDiagramParams(
                axes, req.startAxes, req.fixedT, req.fixedP, req.composition);
        // calculatePhaseDiagram always throws today (not yet implemented -- see
        // docs/roadmap_phase_diagrams.md); runCalculating below is expected to
        // propagate that as an error response, same as any other 501.
        CalculationInterface.<CalculationInterface.PhaseDiagramParams, Object>runCalculating(
                entry.session, CalculationKind.PHASE_DIAGRAM, entry.modelSelection, params);
    }

    /**
     * {@link session.calctype.CalculationGroup#ASSESS} ("opt") -- not
     * implemented yet. Never touches {@code entry.session}: {@link
     * CalculationInterface#runAssessing} takes no {@code CalculationSession}
     * parameter at all, so nothing on this path can reach one.
     */
    private void runAssessment(HttpExchange exchange) throws IOException {
        CalculationOutcome.NotImplemented<Void> outcome =
                CalculationInterface.runAssessing(CalculationKind.ASSESSMENT, null);
        sendJson(exchange, 501, new ErrorResponse(outcome.message()));
    }

    private AxisConfig toAxisConfig(PhaseDiagramRequest.AxisSpec spec) {
        AxisConfig.Type type = AxisConfig.Type.valueOf(spec.type);
        if (type == AxisConfig.Type.COMPOSITION) {
            return new AxisConfig(spec.name, spec.componentIndex, spec.min, spec.max, spec.step);
        }
        return new AxisConfig(spec.name, type, spec.min, spec.max, spec.step);
    }

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

    private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        try (InputStreamReader reader =
                     new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, type);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final class CreatedSession {
        final String sessionId;
        CreatedSession(String sessionId) { this.sessionId = sessionId; }
    }
}
