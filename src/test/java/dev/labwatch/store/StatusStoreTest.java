package dev.labwatch.store;

import dev.labwatch.model.Service;
import dev.labwatch.model.Source;
import dev.labwatch.model.State;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusStoreTest {

    @Test
    void startsEmptyAndServable() {
        StatusStore store = new StatusStore();
        StatusSnapshot snap = store.snapshot();
        assertNotNull(snap.generatedAt());
        assertTrue(snap.sources().isEmpty());
        assertTrue(snap.services().isEmpty());
    }

    @Test
    void snapshotListsAreImmutable() {
        StatusStore store = seededStore();
        StatusSnapshot snap = store.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snap.services().add(null));
        assertThrows(UnsupportedOperationException.class, () -> snap.sources().add(null));
    }

    @Test
    void updateCopiesInputLists() {
        StatusStore store = seededStore();

        // Mutating the lists the caller passed to update() must not change
        // what the store hands out afterwards.
        List<Service> services = new ArrayList<>(List.of(service("docker:a", "A")));
        List<Source> sources = new ArrayList<>(List.of(new Source("docker", true, Instant.now(), null)));
        store.update(sources, services);

        services.add(service("docker:b", "B"));
        sources.add(new Source("proxmox", true, Instant.now(), null));

        assertEquals(1, store.snapshot().services().size());
        assertEquals(1, store.snapshot().sources().size());
    }

    @Test
    void updateStampsFreshGeneratedAt() {
        StatusStore store = seededStore();
        Instant before = Instant.now().minusSeconds(1);
        store.update(List.of(), List.of());
        assertTrue(store.snapshot().generatedAt().isAfter(before));
    }

    private static StatusStore seededStore() {
        StatusStore store = new StatusStore();
        store.update(
                List.of(new Source("docker", true, Instant.now(), null)),
                List.of(service("docker:jellyfin", "Jellyfin")));
        return store;
    }

    private static Service service(String id, String name) {
        return new Service(id, name, "container", "media", State.UP, "Up 6 days",
                "http://example.test", 1.0, 100L);
    }
}
