package dev.labwatch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.http.Json;
import dev.labwatch.model.Service;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** demo.json lives on the runtime classpath (src/main/resources) so it rides
 *  along in the shaded jar. This guards the demo profile's data. */
class DemoFixtureTest {

    @Test
    void fixtureIsOnClasspathAndParses() throws Exception {
        try (InputStream in = DemoFixtureTest.class.getResourceAsStream("/demo.json")) {
            assertNotNull(in, "/demo.json must be on the classpath");
            ObjectMapper mapper = Json.mapper();
            Main.DemoData data = mapper.readValue(in, new TypeReference<>() {
            });

            assertEquals(2, data.sources().size());
            assertEquals(2, data.services().size());

            for (Service service : data.services()) {
                assertNotNull(service.id());
                assertNotNull(service.name());
                assertNotNull(service.kind());
                assertNotNull(service.group());
                assertNotNull(service.state());
                assertNotNull(service.detail());
            }

            // Mirrors the README example exactly (url and error can be null).
            Service jellyfin = data.services().get(0);
            assertEquals("docker:jellyfin", jellyfin.id());
            assertEquals("up", jellyfin.state().wire());
            assertNotNull(jellyfin.url());
            assertNull(data.services().get(1).url());

            List<String> sourceNames = data.sources().stream().map(s -> s.name()).toList();
            assertTrue(sourceNames.containsAll(List.of("proxmox", "docker")));
        }
    }
}
