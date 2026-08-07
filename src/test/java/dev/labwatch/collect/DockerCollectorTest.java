package dev.labwatch.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.http.Json;
import dev.labwatch.model.State;
import dev.labwatch.store.RawStatus;
import dev.labwatch.visibility.CollectedService;
import dev.labwatch.visibility.Filter;
import dev.labwatch.visibility.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests DockerCollector against a synthetic fixture covering every state
 *  and label combination the collector must handle. */
class DockerCollectorTest {

    private static final ObjectMapper MAPPER = Json.mapper();
    private static List<CollectedService> services;

    @BeforeAll
    static void parseFixture() throws Exception {
        try (InputStream in = DockerCollectorTest.class.getResourceAsStream(
                "/docker-containers.json")) {
            List<DockerCollector.ContainerSummary> containers =
                    MAPPER.readValue(in, new TypeReference<>() {
                    });
            services = DockerCollector.parse(containers);
        }
    }

    @Test
    void stateRunningMapsToUp() {
        var svc = findByContainerName("web");
        assertEquals(State.UP, svc.service().state());
    }

    @Test
    void stateExitedMapsToDown() {
        var svc = findByContainerName("worker");
        assertEquals(State.DOWN, svc.service().state());
    }

    @Test
    void stateRestartingMapsToDegraded() {
        var svc = findByContainerName("cache");
        assertEquals(State.DEGRADED, svc.service().state());
    }

    @Test
    void statePausedMapsToDegraded() {
        var svc = findByContainerName("paused-svc");
        assertEquals(State.DEGRADED, svc.service().state());
    }

    @Test
    void stateDeadMapsToDown() {
        var svc = findByContainerName("odd-state");
        assertEquals(State.DOWN, svc.service().state());
    }

    @Test
    void showTrueContainerIsPresent() {
        var svc = findByContainerName("web");
        assertTrue(svc.show());
    }

    @Test
    void showFalseContainerIsAbsent() {
        var svc = findByContainerName("hidden-by-label");
        assertFalse(svc.show());
    }

    @Test
    void publicProfilesContainerHasPublicInProfiles() {
        var svc = findByContainerName("api");
        assertTrue(svc.profiles().contains("public"));
    }

    @Test
    void noLabwatchShowDefaultsToFalse() {
        var svc = findByContainerName("unlabeled");
        assertFalse(svc.show());
    }

    @Test
    void orphanExitedNoLabelsDefaultsShowFalse() {
        var svc = findByContainerName("orphan");
        assertFalse(svc.show());
    }

    @Test
    void orphanIsAbsentFromFilteredOutput() {
        String body = filteredJson(Profile.PRIVATE);
        assertFalse(body.contains("\"orphan\""));
    }

    @Test
    void leadingSlashIsStrippedFromContainerName() {
        var svc = findByContainerName("web");
        assertEquals("docker:web", svc.service().id());
    }

    @Test
    void nameLabelOverridesStrippedContainerName() {
        var svc = findByContainerName("web");
        assertEquals("Web Frontend", svc.service().name());
    }

    @Test
    void nineContainersParsed() {
        assertEquals(9, services.size());
    }

    @Test
    void noLeakedDataInSerializedOutput() {
        String body = filteredJson(Profile.PRIVATE);
        assertFalse(body.contains("nginx"));
        assertFalse(body.contains("postgres"));
        assertFalse(body.contains("redis"));
        assertFalse(body.contains("/var/lib/docker/volumes"));
        assertFalse(body.contains("169.254"));
        assertFalse(body.contains("172.17"));
    }

    // --- helpers ---

    private CollectedService findByContainerName(String containerName) {
        return services.stream()
                .filter(cs -> cs.service().id().endsWith(":" + containerName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "container not found: " + containerName));
    }

    private String filteredJson(Profile profile) {
        var raw = new RawStatus(
                java.time.Instant.now(),
                List.of(new dev.labwatch.model.Source("docker", true,
                        java.time.Instant.EPOCH, null)),
                services);
        var snapshot = Filter.apply(raw, profile);
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
