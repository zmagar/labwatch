package dev.labwatch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.http.Json;
import dev.labwatch.http.Routes;
import dev.labwatch.model.Service;
import dev.labwatch.model.Source;
import dev.labwatch.store.StatusStore;
import dev.labwatch.visibility.CollectedService;
import dev.labwatch.visibility.Profile;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/** Entrypoint and wiring. No collectors exist yet; the demo profile seeds the
 *  store from {@code /demo.json} on the classpath and nothing else happens. */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /** Shape of the demo fixture: sources + services. generated_at is omitted
     *  — the store stamps it on load so the demo always looks fresh. */
    public record DemoData(List<Source> sources, List<Service> services) {
    }

    public static void main(String[] args) throws IOException {
        Profile profile = Profile.fromEnv(env("LABWATCH_PROFILE", "private"));
        String addr = env("LABWATCH_ADDR", ":8080");

        ObjectMapper mapper = Json.mapper();
        StatusStore store = new StatusStore();

        if (profile == Profile.DEMO) {
            loadDemo(mapper, store);
        } else {
            LOG.info("profile={}: no collectors exist yet, serving an empty status", profile);
        }

        Javalin app = Routes.create(store, mapper, profile);
        start(app, addr);
        LOG.info("labwatch serving on {}", addr);
    }

    /** Package-private so tests can exercise the real demo-loading path. */
    static void loadDemo(ObjectMapper mapper, StatusStore store) throws IOException {
        try (InputStream in = Main.class.getResourceAsStream("/demo.json")) {
            if (in == null) {
                throw new IllegalStateException(
                        "demo profile: /demo.json not found on the classpath");
            }
            DemoData data = mapper.readValue(in, new TypeReference<>() {
            });
            List<CollectedService> wrapped = data.services().stream()
                    .map(svc -> new CollectedService(svc, true, Set.of("private")))
                    .toList();
            store.update(data.sources(), wrapped);
            LOG.info("demo profile: loaded {} sources, {} services from demo.json",
                    data.sources().size(), wrapped.size());
        }
    }

    private static void start(Javalin app, String addr) {
        int colon = addr.lastIndexOf(':');
        String host = colon >= 0 ? addr.substring(0, colon) : "";
        int port = Integer.parseInt(addr.substring(colon + 1));
        if (host.isEmpty()) {
            app.start(port); // all interfaces, e.g. ":8080"
        } else {
            app.start(host, port);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
