package dev.labwatch.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.store.StatusStore;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

/** Route wiring. Serialization goes through the shared {@link ObjectMapper}
 *  (jsr310 + ISO-8601 timestamps) via Javalin's Jackson mapper. */
public final class Routes {

    private Routes() {
    }

    public static Javalin create(StatusStore store, ObjectMapper mapper) {
        Javalin app = Javalin.create(config ->
                // Second argument useVirtualThreads=false: keep serialization
                // synchronous (mapper.writeValueAsString on the request thread)
                // rather than streaming through a piped executor — payloads here
                // are small and the poll loop never blocks on serialization.
                config.jsonMapper(new JavalinJackson(mapper, false)));

        app.get("/healthz", ctx -> {
            ctx.contentType("text/plain");
            ctx.result("ok");
        });

        app.get("/api/status", ctx -> ctx.json(store.snapshot()));

        return app;
    }
}
