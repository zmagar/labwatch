package dev.labwatch.collect;

import com.sun.net.httpserver.HttpServer;
import dev.labwatch.store.StatusStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class CollectorHttpTest {
    private static final HttpTimeouts TIMEOUTS =
            new HttpTimeouts(Duration.ofMillis(200), Duration.ofMillis(400));

    @ParameterizedTest
    @ValueSource(strings = {"docker", "proxmox", "proxmox-insecure"})
    void acceptedRequestWithoutResponseTimesOut(String kind) throws Exception {
        try (var upstream = new Upstream()) {
            upstream.stalled.set(true);
            Collector collector = collector(kind, upstream.url());
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThrows(HttpTimeoutException.class, collector::collect));
            assertTrue(upstream.accepted.await(1, TimeUnit.SECONDS), "server received the request");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"docker", "proxmox", "proxmox-insecure"})
    void responseHeadersDoNotDisableBodyDeadline(String kind) throws Exception {
        try (var upstream = new Upstream()) {
            upstream.stalled.set(true);
            upstream.sendHeaders = true;
            Collector collector = collector(kind, upstream.url());
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThrows(HttpTimeoutException.class, collector::collect));
            assertTrue(upstream.accepted.await(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void docker403ReportsStatusInsteadOfParsingHtml() throws Exception {
        try (var upstream = new Upstream()) {
            upstream.status = 403;
            upstream.body = "<html><body>Forbidden</body></html>";
            IOException error = assertThrows(IOException.class,
                    () -> collector("docker", upstream.url()).collect());
            assertEquals("docker returned HTTP 403", error.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"docker", "proxmox"})
    void timedOutSourceRetainsDataAndNextCollectorStillRunsThenRecovers(String kind) throws Exception {
        try (var upstream = new Upstream()) {
            String fixture = kind.equals("docker") ? "/docker-containers.json" : "/proxmox-resources.json";
            try (var in = getClass().getResourceAsStream(fixture)) {
                upstream.body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            var store = new StatusStore();
            var collectors = new LinkedHashMap<String, Collector>();
            collectors.put(kind, collector(kind, upstream.url()));
            collectors.put("next", List::of);
            PollLoop loop = PollLoop.forTesting(store, collectors);
            loop.tick();
            var before = store.raw();
            assertTrue(before.sources().getFirst().ok());
            assertFalse(before.services().isEmpty());

            upstream.stalled.set(true);
            assertTimeoutPreemptively(Duration.ofSeconds(3), loop::tick);
            var failed = store.raw();
            assertFalse(failed.sources().getFirst().ok());
            assertTrue(failed.sources().getFirst().error().contains("timed out"));
            assertEquals(before.sources().getFirst().lastSuccess(), failed.sources().getFirst().lastSuccess());
            assertEquals(before.services(), failed.services());
            assertTrue(failed.sources().get(1).ok());
            assertTrue(failed.sources().get(1).lastSuccess().isAfter(before.sources().get(1).lastSuccess()));

            upstream.stalled.set(false);
            upstream.release.countDown();
            loop.tick();
            assertTrue(store.raw().sources().getFirst().ok());
            assertNull(store.raw().sources().getFirst().error());
        }
    }

    private Collector collector(String kind, String url) {
        if (kind.equals("docker")) return new DockerCollector(url, TIMEOUTS);
        return new ProxmoxCollector(url, "test@pve!test", "test-secret",
                new VisibilityConfig(Path.of("src/test/resources/proxmox-config.yaml")),
                kind.equals("proxmox-insecure"), TIMEOUTS);
    }

    /** A real HTTP peer that can receive a request and deliberately never answer. */
    private static class Upstream implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final AtomicBoolean stalled = new AtomicBoolean();
        final CountDownLatch accepted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean sendHeaders;
        volatile int status = 200;
        volatile String body = "[]";

        Upstream() throws IOException {
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try (exchange) {
                    if (stalled.get()) {
                        if (sendHeaders) {
                            exchange.sendResponseHeaders(200, 100);
                            exchange.getResponseBody().write('[');
                            exchange.getResponseBody().flush();
                        }
                        accepted.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return;
                    }
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

        public void close() {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
