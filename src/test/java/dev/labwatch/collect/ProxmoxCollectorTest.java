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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests ProxmoxCollector against synthetic fixtures. */
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
    void filtersOutStorageAndPools() {
        List<String> types = services.stream()
                .map(cs -> cs.service().kind())
                .distinct().toList();
        assertTrue(types.contains("node"));
        assertTrue(types.contains("qemu"));
        assertTrue(types.contains("lxc"));
        assertEquals(3, types.size(),
                "only node, qemu, and lxc should appear");
    }

    @Test
    void stateRunningMapsToUp() {
        var svc = findByProxmoxId("qemu/200");
        assertEquals(State.UP, svc.service().state());
    }

    @Test
    void stateOnlineMapsToUp() {
        var svc = findByProxmoxId("node/proxmox");
        assertEquals(State.UP, svc.service().state());
    }

    @Test
    void stateStoppedMapsToDown() {
        var svc = findByProxmoxId("qemu/201");
        assertEquals(State.DOWN, svc.service().state());
    }

    @Test
    void stateOfflineMapsToDown() {
        var svc = findByProxmoxId("node/second");
        assertEquals(State.DOWN, svc.service().state());
    }

    @Test
    void showTrueFromConfigAppears() {
        var svc = findByProxmoxId("lxc/107");
        assertTrue(svc.show());
        assertEquals("dns247", svc.service().name());
        assertEquals("network", svc.service().group());
    }

    @Test
    void showFalseFromConfigIsHidden() {
        var svc = findByProxmoxId("lxc/108");
        assertEquals(false, svc.show());
    }

    @Test
    void missingFromConfigDefaultsHidden() {
        var svc = findByProxmoxId("qemu/201");
        assertEquals(false, svc.show());
        assertEquals("other", svc.service().group());
    }

    @Test
    void profilesFromConfig() {
        var svc = findByProxmoxId("qemu/200");
        assertTrue(svc.profiles().contains("public"));
        assertTrue(svc.profiles().contains("private"));
    }

    @Test
    void cpuPctConvertedFromFractionToPercent() {
        var svc = findByProxmoxId("qemu/200");
        assertEquals(5.0, svc.service().cpuPct(), 0.01);
    }

    @Test
    void memBytesPopulatedDirectly() {
        var svc = findByProxmoxId("qemu/200");
        assertEquals(2147483648L, svc.service().memBytes());
    }

    @Test
    void cpuAndMemNullForOfflineNode() {
        var svc = findByProxmoxId("node/second");
        assertEquals(null, svc.service().cpuPct());
        assertEquals(null, svc.service().memBytes());
    }

    @Test
    void tokenNotInExceptionMessage() {
        // Force a failure with a collector that will hit a connection error.
        // The token is in the Authorization header and must not appear in
        // the IOException message (which only contains the host).
        var collector = new ProxmoxCollector(
                "http://127.0.0.1:9", // discard port — guaranteed to fail
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
        // Construction succeeds; the trust-all SSL context is wired in.
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
