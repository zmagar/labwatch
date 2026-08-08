package dev.labwatch.collect;

import dev.labwatch.model.Service;
import dev.labwatch.model.State;
import dev.labwatch.store.StatusStore;
import dev.labwatch.visibility.CollectedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives PollLoop.tick() manually so we can control exactly which
 *  collectors succeed or fail without a real timer. */
class PollLoopTest {

    private StatusStore store;
    private PollLoop loop;
    private static final Duration INTERVAL = Duration.ofSeconds(30);

    @BeforeEach
    void setUp() {
        store = new StatusStore();
    }

    @Test
    void bothCollectorsSucceedServicesMerged() {
        var collectors = new LinkedHashMap<String, Collector>();
        collectors.put("docker", () -> List.of(
                collected("docker:s1", "S1")));
        collectors.put("proxmox", () -> List.of(
                collected("proxmox:s2", "S2")));

        loop = PollLoop.forTesting(store, collectors);
        loop.tick();

        var raw = store.raw();
        assertEquals(2, raw.services().size());
        assertEquals(2, raw.sources().size());

        var dockerSrc = sourceByName("docker", raw.sources());
        assertTrue(dockerSrc.ok());
        assertNotNull(dockerSrc.lastSuccess());

        var proxmoxSrc = sourceByName("proxmox", raw.sources());
        assertTrue(proxmoxSrc.ok());
        assertNotNull(proxmoxSrc.lastSuccess());
    }

    @Test
    void firstTickFailureHasNullLastSuccessAndEmptyServices() {
        var collectors = new LinkedHashMap<String, Collector>();
        collectors.put("docker", () -> {
            throw new RuntimeException("socket proxy down");
        });
        collectors.put("proxmox", () -> List.of(
                collected("proxmox:s2", "S2")));

        loop = PollLoop.forTesting(store, collectors);
        loop.tick();

        var raw = store.raw();
        assertEquals(1, raw.services().size());
        assertEquals("proxmox:s2", raw.services().get(0).service().id());

        var dockerSrc = sourceByName("docker", raw.sources());
        assertFalse(dockerSrc.ok());
        assertEquals(null, dockerSrc.lastSuccess());
        assertTrue(dockerSrc.error().contains("first poll"));

        var proxmoxSrc = sourceByName("proxmox", raw.sources());
        assertTrue(proxmoxSrc.ok());
        assertNotNull(proxmoxSrc.lastSuccess());
    }

    @Test
    void subsequentFailurePreservesLastSuccessAndLastKnownServices() {
        var collectors = new LinkedHashMap<String, Collector>();
        collectors.put("docker", new FailingAfterCollector(1));
        collectors.put("proxmox", () -> List.of(
                collected("proxmox:s2", "S2")));

        loop = PollLoop.forTesting(store, collectors);
        loop.tick(); // both succeed: docker 1, proxmox 1 → 2 total
        loop.tick(); // docker fails, proxmox succeeds: docker 1 (last), proxmox 1 → 2 total

        var raw = store.raw();
        assertEquals(2, raw.services().size());

        var dockerSrc = sourceByName("docker", raw.sources());
        assertFalse(dockerSrc.ok());
        assertNotNull(dockerSrc.lastSuccess(), "last_success preserved from prior success");
        // On subsequent failure, the error is the collector's exception message,
        // not the "first poll" placeholder.
        assertEquals("simulated failure on call 2", dockerSrc.error());
    }

    @Test
    void tokenNotInLogOutputOnFailure() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(stderr));
        try {
            var collectors = new LinkedHashMap<String, Collector>();
            String secret = "secret-value-xyz";
            var failing = new ProxmoxCollector(
                    "http://127.0.0.1:9",
                    "user@pve!tokenname",
                    secret,
                    new VisibilityConfig(java.nio.file.Path.of("nonexistent.yaml")),
                    false);
            collectors.put("proxmox", failing);

            loop = PollLoop.forTesting(store, collectors);
            loop.tick();

            String logOutput = stderr.toString();
            assertFalse(logOutput.contains(secret),
                    "token secret must not appear in log output");
            assertFalse(logOutput.contains("user@pve!tokenname"),
                    "token id must not appear in log output");
        } finally {
            System.setErr(originalErr);
        }
    }

    private static dev.labwatch.model.Source sourceByName(
            String name, List<dev.labwatch.model.Source> sources) {
        return sources.stream()
                .filter(s -> s.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static CollectedService collected(String id, String name) {
        var svc = new Service(id, name, "container", "other",
                State.UP, "ok", null, null, null, null, null, null);
        return new CollectedService(svc, true, Set.of("private"));
    }

    /** Succeeds for the first {@code succeedCount} calls then throws. */
    static class FailingAfterCollector implements Collector {
        private final int succeedCount;
        private int calls;

        FailingAfterCollector(int succeedCount) {
            this.succeedCount = succeedCount;
        }

        @Override
        public List<CollectedService> collect() {
            if (++calls <= succeedCount) {
                return List.of(collected("docker:test", "Test"));
            }
            throw new RuntimeException("simulated failure on call " + calls);
        }
    }
}
