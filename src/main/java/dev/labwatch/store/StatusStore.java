package dev.labwatch.store;

import dev.labwatch.model.Service;
import dev.labwatch.model.Source;

import java.time.Instant;
import java.util.List;

/** In-memory last-good state. Collectors write with {@link #update}; the API
 *  reads with {@link #snapshot()}. The swap is atomic on the volatile
 *  reference, so a poll running mid-request can't mutate what's being
 *  serialized. */
public class StatusStore {

    private volatile StatusSnapshot current;

    public StatusStore() {
        this.current = new StatusSnapshot(Instant.now(), List.of(), List.of());
    }

    /** Immutable snapshot of the current state. */
    public StatusSnapshot snapshot() {
        return current;
    }

    /** Replace the stored state, stamping generated_at to now. */
    public void update(List<Source> sources, List<Service> services) {
        current = new StatusSnapshot(Instant.now(), sources, services);
    }
}
