package diagnostics;

import ui.api.CalculationApiServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * End-to-end verification of {@link CalculationApiServer}: full session
 * lifecycle over real HTTP, using the same calG scenario as {@code
 * CalculationSessionCalGTest} to cross-check the JSON response against
 * OC-verified values (see {@link CliEquilibriumCommandTest}'s javadoc for
 * the OC cross-check), plus error-status mapping and session isolation.
 */
public class CalculationApiServerTest {

    private static int failures = 0;
    private static final int PORT = 18099;
    private static final String BASE = "http://localhost:" + PORT;

    public static void main(String[] args) throws Exception {
        CalculationApiServer server = new CalculationApiServer(PORT);
        server.start();
        try {
            testFullLifecycleMatchesCalGTest();
            testMissingModelReturns409();
            testStepAndMapReturn501();
            testUnknownSessionReturns404();
            testTwoSessionsAreIsolated();
        } finally {
            server.stop();
        }

        if (failures == 0) {
            System.out.println("ALL CalculationApiServer CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static void check(boolean condition, String description) {
        if (condition) {
            System.out.println("  PASS: " + description);
        } else {
            System.out.println("  FAIL: " + description);
            failures++;
        }
    }

    private static void testFullLifecycleMatchesCalGTest() throws Exception {
        System.out.println("=== full lifecycle: calG via HTTP matches CalculationSessionCalGTest ===");

        Response created = request("POST", "/sessions", null);
        check(created.status == 200, "POST /sessions returns 200");
        String sessionId = extractField(created.body, "sessionId");
        check(sessionId != null && !sessionId.isEmpty(), "sessionId is present in response");

        String modelBody = "{\"tdbFilePath\":\"data/VZR-re2.TDB\","
                + "\"elements\":[\"V\",\"ZR\"],\"phases\":[\"V2ZR\"]}";
        Response modelSet = request("PUT", "/sessions/" + sessionId + "/model", modelBody);
        check(modelSet.status == 204, "PUT .../model returns 204");

        String calcBody = "{\"T\":1000.0,\"P\":10000.0,"
                + "\"composition\":[0.6666666666666666,0.3333333333333333]}";
        Response calc = request("POST", "/sessions/" + sessionId + "/calculations/equilibrium", calcBody);
        check(calc.status == 200, "POST .../calculations/equilibrium returns 200");
        check(calc.body.contains("\"phaseName\":\"V2ZR\""), "response contains V2ZR stable phase");
        check(calc.body.contains("-150885.58"), "response G matches OC-verified value (-150885.5871)");
        check(calc.body.contains("-44941.44"), "response mu[0] matches OC-verified value (-44941.446)");

        Response deleted = request("DELETE", "/sessions/" + sessionId, null);
        check(deleted.status == 204, "DELETE /sessions/{id} returns 204");
    }

    private static void testMissingModelReturns409() throws Exception {
        System.out.println("=== calculation before setModel returns 409 ===");
        String sessionId = extractField(request("POST", "/sessions", null).body, "sessionId");
        Response resp = request("POST", "/sessions/" + sessionId + "/calculations/equilibrium",
                "{\"T\":1000,\"P\":10000,\"composition\":[0.5,0.5]}");
        check(resp.status == 409, "returns 409 Conflict when no model is set");
        check(resp.body.contains("error"), "response body has an error field");
    }

    private static void testStepAndMapReturn501() throws Exception {
        System.out.println("=== step/map calculation types return 501 ===");
        String sessionId = extractField(request("POST", "/sessions", null).body, "sessionId");
        request("PUT", "/sessions/" + sessionId + "/model",
                "{\"tdbFilePath\":\"data/VZR-re2.TDB\",\"elements\":[\"V\",\"ZR\"],\"phases\":[\"V2ZR\"]}");

        Response step = request("POST", "/sessions/" + sessionId + "/calculations/step", "{}");
        check(step.status == 501, "step returns 501 Not Implemented");

        Response map = request("POST", "/sessions/" + sessionId + "/calculations/map", "{}");
        check(map.status == 501, "map returns 501 Not Implemented");
    }

    private static void testUnknownSessionReturns404() throws Exception {
        System.out.println("=== unknown session id returns 404 ===");
        Response resp = request("POST", "/sessions/does-not-exist/calculations/equilibrium", "{}");
        check(resp.status == 404, "unknown session returns 404");
    }

    private static void testTwoSessionsAreIsolated() throws Exception {
        System.out.println("=== two sessions with different models don't interfere ===");
        String s1 = extractField(request("POST", "/sessions", null).body, "sessionId");
        String s2 = extractField(request("POST", "/sessions", null).body, "sessionId");

        request("PUT", "/sessions/" + s1 + "/model",
                "{\"tdbFilePath\":\"data/VZR-re2.TDB\",\"elements\":[\"V\",\"ZR\"],\"phases\":[\"V2ZR\"]}");
        // s2 never gets a model.

        Response calcOnS1 = request("POST", "/sessions/" + s1 + "/calculations/equilibrium",
                "{\"T\":1000.0,\"P\":10000.0,\"composition\":[0.6666666666666666,0.3333333333333333]}");
        check(calcOnS1.status == 200, "session with model set succeeds");

        Response calcOnS2 = request("POST", "/sessions/" + s2 + "/calculations/equilibrium",
                "{\"T\":1000.0,\"P\":10000.0,\"composition\":[0.5,0.5]}");
        check(calcOnS2.status == 409, "session without its own model still has no model (isolation)");
    }

    // ------------------------------------------------------------------
    // Minimal HTTP client (no new dependency needed for the test itself)
    // ------------------------------------------------------------------

    private record Response(int status, String body) {}

    private static Response request(String method, String path, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(BASE + path).toURL().openConnection();
        conn.setRequestMethod(method);
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = conn.getResponseCode();
        InputStream stream = status < 400 ? conn.getInputStream() : conn.getErrorStream();
        String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return new Response(status, responseBody);
    }

    private static String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
