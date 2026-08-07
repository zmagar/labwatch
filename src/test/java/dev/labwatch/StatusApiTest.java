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
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end: demo profile seeds the store, and GET /api/status serves the
 *  README shape with ISO-8601 timestamps. */
class StatusApiTest {

    /** The example payload from README.md. demo.json mirrors its sources and
     *  services; generated_at is stamped at load so only its type is asserted. */
    private static final String README_EXAMPLE = """
            {
              "generated_at": "2026-08-03T14:22:07Z",
              "sources": [
                { "name": "proxmox", "ok": true,  "last_success": "2026-08-03T14:22:05Z", "error": null },
                { "name": "docker",  "ok": false, "last_success": "2026-08-03T14:09:41Z", "error": "connection refused" }
              ],
              "services": [
                {
                  "id": "docker:jellyfin",
                  "name": "Jellyfin",
                  "kind": "container",
                  "group": "media",
                  "state": "up",
                  "detail": "Up 6 days",
                  "url": "http://192.168.0.244:8096",
                  "cpu_pct": 3.1,
                  "mem_bytes": 812187648
                },
                {
                  "id": "proxmox:lxc/107",
                  "name": "dns247",
                  "kind": "lxc",
                  "group": "network",
                  "state": "up",
                  "detail": "uptime 21d",
                  "url": null,
                  "cpu_pct": 0.4,
                  "mem_bytes": 268435456
                }
              ]
            }
            """;

    private static Javalin app;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static int port;

    @BeforeAll
    static void startDemoApp() throws Exception {
        StatusStore store = new StatusStore();
        Main.loadDemo(Json.mapper(), store);
        app = Routes.create(store, Json.mapper(), Profile.DEMO);
        app.start(0);
        port = app.port();
    }

    @AfterAll
    static void stopApp() {
        app.stop();
    }

    private static String get(String path) throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    @Test
    void servesReadmeShape() throws Exception {
        String body = get("/api/status");
        JsonNode expected = Json.mapper().readTree(README_EXAMPLE);
        JsonNode actual = Json.mapper().readTree(body);
        assertShape(expected, actual, "$");

        // state is constrained to the README's enum, not just any string.
        Set<String> states = Set.of("up", "down", "degraded", "unknown");
        for (JsonNode service : actual.get("services")) {
            assertTrue(states.contains(service.get("state").asText()),
                    "unexpected state: " + service.get("state").asText());
        }
    }

    @Test
    void timestampsAreIso8601() throws Exception {
        JsonNode payload = Json.mapper().readTree(get("/api/status"));

        // generated_at is stamped at load, so only its format is asserted.
        assertDoesNotThrow(() -> Instant.parse(payload.get("generated_at").asText()));

        // last_success round-trips from the fixture exactly.
        JsonNode proxmox = payload.get("sources").get(0);
        assertEquals("proxmox", proxmox.get("name").asText());
        assertEquals("2026-08-03T14:22:05Z", proxmox.get("last_success").asText());
        assertDoesNotThrow(() -> Instant.parse(proxmox.get("last_success").asText()));
    }

    @Test
    void servesContentTypeJson() throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/status")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("content-type").orElse("")
                .startsWith("application/json"));
    }

    @Test
    void healthzReturns200() throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/healthz")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
    }

    private static void assertShape(JsonNode expected, JsonNode actual, String path) {
        if (expected.isObject()) {
            assertTrue(actual.isObject(), path + ": expected an object");
            assertEquals(expected.size(), actual.size(), path + ": key set differs");
            expected.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                assertTrue(actual.has(key), path + ": missing key '" + key + "'");
                assertShape(entry.getValue(), actual.get(key), path + "." + key);
            });
        } else if (expected.isArray()) {
            assertTrue(actual.isArray(), path + ": expected an array");
            assertEquals(expected.size(), actual.size(), path + ": array size differs");
            for (int i = 0; i < expected.size(); i++) {
                assertShape(expected.get(i), actual.get(i), path + "[" + i + "]");
            }
        } else if (expected.isTextual()) {
            assertTrue(actual.isTextual(), path + ": expected a string");
        } else if (expected.isNumber()) {
            assertTrue(actual.isNumber(), path + ": expected a number");
        } else if (expected.isBoolean()) {
            assertTrue(actual.isBoolean(), path + ": expected a boolean");
        } else if (expected.isNull()) {
            assertTrue(actual.isNull(), path + ": expected null");
        } else {
            throw new AssertionError(path + ": unexpected node type " + expected.getNodeType());
        }
    }
}
