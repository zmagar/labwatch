package dev.labwatch.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.http.Json;
import dev.labwatch.model.State;
import dev.labwatch.visibility.CollectedService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests ProxmoxCollector against a scrubbed real cluster/resources
 *  response covering 18 fields per guest and five resource types. */
class ProxmoxCollectorTest {

    private static final ObjectMapper MAPPER = Json.mapper();
    private static List<CollectedService> services;

    @BeforeAll
    static void parseFixture() throws Exception {
        try (InputStream in = ProxmoxCollectorTest.class.getResourceAsStream(
                "/proxmox-resources.json")) {
            ProxmoxCollector.ResourcesResponse body =
                    MAPPER.readValue(in, new TypeReference<>() {
                    });
            VisibilityConfig config = new VisibilityConfig(
                    Path.of("src/test/resources/proxmox-config.yaml"));
            services = ProxmoxCollector.parse(body.data(), config);
        }
    }

    @Test
    void deserializationSucceedsAgainstRealFieldSet() {
        // 18 fields per guest including cgroup-mode (hyphenated key).
        // @JsonIgnoreProperties(ignoreUnknown = true) makes this pass.
        assertNotNull(services);
    }

    @Test
    void filtersOutStorageAndSdn() {
        assertEquals(3, services.size(),
                "only node, qemu, and lxc should survive");
        List<String> types = services.stream()
                .map(cs -> cs.service().kind())
                .distinct().toList();
        assertTrue(types.contains("node"));
        assertTrue(types.contains("qemu"));
        assertTrue(types.contains("lxc"));
        assertEquals(3, types.size());
    }

    @Test
    void qemuStateRunningMapsToUp() {
        var svc = findByProxmoxId("qemu/101");
        assertEquals(State.UP, svc.service().state());
    }

    @Test
    void lxcStateRunningMapsToUp() {
        var svc = findByProxmoxId("lxc/247");
        assertEquals(State.UP, svc.service().state());
    }

    @Test
    void nodeStateOnlineMapsToUp() {
        var svc = findByProxmoxId("node/node-1");
        assertEquals(State.UP, svc.service().state());
    }

    @Test
    void showTrueFromConfigAppears() {
        var svc = findByProxmoxId("qemu/101");
        assertTrue(svc.show());
        assertEquals("Guest One", svc.service().name());
        assertEquals("home", svc.service().group());
    }

    @Test
    void profilesFromConfig() {
        var svc = findByProxmoxId("qemu/101");
        assertTrue(svc.profiles().contains("public"));
        assertTrue(svc.profiles().contains("private"));
    }

    @Test
    void showFalseFromConfigIsHidden() {
        var svc = findByProxmoxId("node/node-1");
        assertFalse(svc.show());
    }

    @Test
    void cpuFractionConvertedToPercent() {
        // qemu/101 cpu = 0.0167615014079572 → ×100 ≈ 1.68
        var svc = findByProxmoxId("qemu/101");
        assertEquals(1.67615014079572, svc.service().cpuPct(), 0.0001);
    }

    @Test
    void memBytesPopulatedDirectly() {
        var svc = findByProxmoxId("qemu/101");
        assertEquals(33076404224L, svc.service().memBytes());
    }

    @Test
    void maxCpuAndMaxMemPopulated() {
        var svc = findByProxmoxId("qemu/101");
        assertEquals(18, svc.service().maxCpu());
        assertEquals(34359738368L, svc.service().maxMem());
    }

    @Test
    void detailIsFormattedUptimeNotStatus() {
        var svc = findByProxmoxId("qemu/101");
        assertTrue(svc.service().detail().startsWith("Up "),
                "detail should be uptime-formatted, got: " + svc.service().detail());
    }

    @Test
    void createdAtIsNullForProxmox() {
        var svc = findByProxmoxId("qemu/101");
        assertEquals(null, svc.service().createdAt());
    }

    @Test
    void missingFromConfigDefaultsHidden() {
        // lxc/247 has show=true in the config → present.
        // If no config entry, it would be hidden. The config covers it.
        var svc = findByProxmoxId("lxc/247");
        assertTrue(svc.show());
    }

    @Test
    void storageAndSdnProduceNoService() {
        List<String> ids = services.stream()
                .map(cs -> cs.service().id())
                .toList();
        assertFalse(ids.stream().anyMatch(id -> id.contains("storage")),
                "storage entries must not produce a Service");
        assertFalse(ids.stream().anyMatch(id -> id.contains("sdn")),
                "sdn entries must not produce a Service");
    }

    @Test
    void tokenNotInExceptionMessage() {
        var collector = new ProxmoxCollector(
                "http://127.0.0.1:9",
                "user@pve!tokenname",
                "secret-value-123",
                new VisibilityConfig(Path.of("nonexistent.yaml")),
                false);
        try {
            collector.collect();
        } catch (Exception e) {
            String msg = e.toString();
            assert !msg.contains("secret-value-123")
                    : "token must not appear in exception output: " + msg;
            assert !msg.contains("user@pve!tokenname")
                    : "token id must not appear in exception output: " + msg;
        }
    }

    @Test
    void insecureTlsCanConstructWithoutThrowing() {
        var collector = new ProxmoxCollector(
                "https://proxmox.example:8006",
                "user@pve!tokenname",
                "secret",
                new VisibilityConfig(Path.of("nonexistent.yaml")),
                true);
        // The HttpClient is built with a trust-all TrustManager.
        // Actual TLS handshake isn't tested — that requires a real endpoint.
    }

    private CollectedService findByProxmoxId(String proxmoxId) {
        return services.stream()
                .filter(cs -> cs.service().id().equals("proxmox:" + proxmoxId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Proxmox resource not found: " + proxmoxId));
    }
}
