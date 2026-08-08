package dev.labwatch.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.store.StatusStore;
import dev.labwatch.visibility.Filter;
import dev.labwatch.visibility.Profile;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;

/** Route wiring. Serialization goes through the shared {@link ObjectMapper}
 *  (jsr310 + ISO-8601 timestamps) via Javalin's Jackson mapper. */
public final class Routes {

    private Routes() {
    }

    public static Javalin create(StatusStore store, ObjectMapper mapper, Profile profile) {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.staticFiles.add("/web", Location.CLASSPATH);
        });

        app.get("/healthz", ctx -> {
            ctx.contentType("text/plain");
            ctx.result("ok");
        });

        app.get("/api/status", ctx ->
                ctx.json(Filter.apply(store.raw(), profile)));

        return app;
    }
}
