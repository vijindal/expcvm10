package ui.api;

import java.io.IOException;

/** Standalone entry point for the REST API server. */
public final class ApiMain {

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        CalculationApiServer server = new CalculationApiServer(port);
        server.start();
        System.out.println("expCVM10 calculation API listening on http://localhost:" + port);
    }
}
