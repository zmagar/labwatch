package dev.labwatch.store;

import dev.labwatch.model.Source;
import dev.labwatch.model.State;
import dev.labwatch.visibility.CollectedService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusStoreTest {

    @Test
    void startsEmptyAndServable() {
        StatusStore store = new StatusStore();
        RawStatus raw = store.raw();
        assertNotNull(raw.generatedAt());
        assertTrue(raw.sources().isEmpty());
        assertTrue(raw.services().isEmpty());
    }

    @Test
    void snapshotListsAreImmutable() {
        StatusStore store = seededStore();
        RawStatus raw = store.raw();
        assertThrows(UnsupportedOperationException.class, () -> raw.services().add(null));
        assertThrows(UnsupportedOperationException.class, () -> raw.sources().add(null));
    }

    @Test
    void updateCopiesInputLists() {
        StatusStore store = seededStore();

        List<CollectedService> services = new ArrayList<>(List.of(collected("docker:a", "A")));
        List<Source> sources = new ArrayList<>(List.of(new Source("docker", true, Instant.now(), null)));
        store.update(sources, services);

        services.add(collected("docker:b", "B"));
        sources.add(new Source("proxmox", true, Instant.now(), null));

        assertEquals(1, store.raw().services().size());
        assertEquals(1, store.raw().sources().size());
    }

    @Test
    void updateStampsFreshGeneratedAt() {
        StatusStore store = seededStore();
        Instant before = Instant.now().minusSeconds(1);
        store.update(List.of(), List.of());
        assertTrue(store.raw().generatedAt().isAfter(before));
    }

    private static StatusStore seededStore() {
        StatusStore store = new StatusStore();
        store.update(
                List.of(new Source("docker", true, Instant.now(), null)),
                List.of(collected("docker:jellyfin", "Jellyfin")));
        return store;
    }

    private static CollectedService collected(String id, String name) {
        var svc = new dev.labwatch.model.Service(id, name, "container", "media",
                State.UP, "Up 6 days", "http://example.test", 1.0, 100L);
        return new CollectedService(svc, true, Set.of("private"));
    }
}
