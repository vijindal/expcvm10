package api;

import api.dto.EquilibriumRequest;
import api.dto.EquilibriumResponse;
import api.dto.ErrorResponse;
import api.dto.PhaseDiagramRequest;
import api.dto.PhaseDiagramResponse;
import api.dto.SetModelRequest;
import calc.diagram.AxisConfig;
import calc.diagram.PhaseDiagram;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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
 *   POST   /sessions/{id}/calculations/equilibrium      -> 200 EquilibriumResponse
 *   POST   /sessions/{id}/calculations/phase-diagram    -> 200 PhaseDiagramResponse
 *   POST   /sessions/{id}/calculations/step             -> 501
 *   POST   /sessions/{id}/calculations/map              -> 501
 *   DELETE /sessions/{id}                                -> 204
 * </pre>
 */
public final class CalculationApiServer {

    private static final Logger LOG = Logger.getLogger(CalculationApiServer.class.getName());

    private static final Pattern SESSION_MODEL_PATH =
            Pattern.compile("^/sessions/([^/]+)/model$");
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
                exchange.sendResponseHeaders(204, -1);
                exchange.getResponseBody().close();
            } catch (IOException e) {
                sendJson(exchange, 400, new ErrorResponse("Could not load model: " + e.getMessage()));
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
            }
        }
    }

    private void runEquilibrium(HttpExchange exchange, SessionStore.Entry entry) throws IOException {
        EquilibriumRequest req = readJson(exchange, EquilibriumRequest.class);
        entry.session.calculateEquilibrium(req.T, req.P, req.composition);
        EquilibriumResult result = entry.session.currentEquilibriumResult();
        sendJson(exchange, 200, new EquilibriumResponse(result));
    }

    private void runPhaseDiagram(HttpExchange exchange, SessionStore.Entry entry) throws IOException {
        PhaseDiagramRequest req = readJson(exchange, PhaseDiagramRequest.class);

        AxisConfig[] axes = new AxisConfig[req.axes.size()];
        for (int i = 0; i < axes.length; i++) {
            PhaseDiagramRequest.AxisSpec spec = req.axes.get(i);
            axes[i] = toAxisConfig(spec);
        }

        entry.session.calculatePhaseDiagram(axes, req.startAxes, req.fixedT, req.fixedP, req.composition);
        PhaseDiagram diagram = entry.session.currentPhaseDiagram();
        sendJson(exchange, 200, new PhaseDiagramResponse(diagram));
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
