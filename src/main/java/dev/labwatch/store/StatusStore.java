package dev.labwatch.store;

import dev.labwatch.model.Source;
import dev.labwatch.visibility.CollectedService;

import java.time.Instant;
import java.util.List;

/** In-memory last-good state. Collectors write with {@link #update}; the API
 *  reads with {@link #raw()}, filtering through
 *  {@link dev.labwatch.visibility.Filter}. The swap is atomic on the volatile
 *  reference, so a poll running mid-request can't mutate what's being
 *  serialized. */
public class StatusStore {

    private volatile RawStatus current;

    public StatusStore() {
        this.current = new RawStatus(Instant.now(), List.of(), List.of());
    }

    /** Internal snapshot of the current state (before filtering). */
    public RawStatus raw() {
        return current;
    }

    /** Replace the stored state, stamping generated_at to now. */
    public void update(List<Source> sources, List<CollectedService> services) {
        current = new RawStatus(Instant.now(), sources, services);
    }
}
