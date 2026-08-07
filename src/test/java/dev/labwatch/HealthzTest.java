package dev.labwatch;

import com.fasterxml.jackson.databind.JsonNode;
import dev.labwatch.http.Json;
import dev.labwatch.http.Routes;
import dev.labwatch.store.StatusStore;
import dev.labwatch.visibility.Profile;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The acceptance that /healthz returns 200 with no fixtures loaded at all:
 *  the store is built empty and no demo data is loaded. */
class HealthzTest {

    private static Javalin app;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static int port;

    @BeforeAll
    static void startEmptyApp() {
        StatusStore store = new StatusStore(); // no fixtures, no demo load
        app = Routes.create(store, Json.mapper(), Profile.PRIVATE);
        app.start(0);
        port = app.port();
    }

    @AfterAll
    static void stopApp() {
        app.stop();
    }

    @Test
    void healthzReturns200WithNoFixturesLoaded() throws Exception {
        HttpResponse<String> resp = get("/healthz");
        assertEquals(200, resp.statusCode());
    }

    @Test
    void apiStatusIsStillAValidEmptyPayload() throws Exception {
        HttpResponse<String> resp = get("/api/status");
        assertEquals(200, resp.statusCode());
        JsonNode payload = Json.mapper().readTree(resp.body());
        assertTrue(payload.has("generated_at"));
        assertEquals(0, payload.get("sources").size());
        assertEquals(0, payload.get("services").size());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
