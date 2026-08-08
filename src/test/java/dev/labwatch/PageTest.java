package dev.labwatch;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The page is a static HTML shell. No service data is embedded in markup —
 *  everything comes from /api/status at runtime via app.js. */
class PageTest {

    private static Javalin app;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        StatusStore store = new StatusStore();
        Main.loadDemo(Json.mapper(), store);
        app = Routes.create(store, Json.mapper(), Profile.DEMO);
        app.start(0);
        port = app.port();
    }

    @AfterAll
    static void stop() {
        app.stop();
    }

    private String get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void indexServesHtml() throws Exception {
        String body = get("/");
        assertTrue(body.contains("<!DOCTYPE html>"));
        assertTrue(body.contains("<title>labwatch</title>"));
    }

    @Test
    void htmlContainsNoServiceData() {
        String body;
        try { body = get("/"); } catch (Exception e) { throw new RuntimeException(e); }
        assertFalse(body.contains("jellyfin"), "service name must not be in HTML markup");
        assertFalse(body.contains("dns247"), "service name must not be in HTML markup");
        assertFalse(body.contains("Jellyfin"));
        assertFalse(body.contains("Web Frontend"));
    }

    @Test
    void htmlReferencesAppJsAndStyleCss() throws Exception {
        String body = get("/");
        assertTrue(body.contains("<script defer src=\"/app.js\">"),
                "HTML must reference /app.js with defer");
        assertTrue(body.contains("<link rel=\"stylesheet\" href=\"/style.css\">"),
                "HTML must reference /style.css");
    }

    @Test
    void htmlHasViewportMeta() throws Exception {
        String body = get("/");
        assertTrue(body.contains("viewport"),
                "HTML must have a viewport meta tag for mobile rendering");
    }

    @Test
    void appJsServesAndFetchesApiStatus() throws Exception {
        String body = get("/app.js");
        assertTrue(body.contains("/api/status"),
                "app.js must fetch /api/status at runtime");
        assertTrue(body.contains("REFRESH_S"),
                "app.js must have a refresh constant");
    }

    @Test
    void styleCssServes() throws Exception {
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/style.css"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains(".state-up"));
    }
}
