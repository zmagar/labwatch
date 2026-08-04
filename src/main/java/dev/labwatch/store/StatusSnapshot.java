package dev.labwatch.store;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.labwatch.model.Service;
import dev.labwatch.model.Source;

import java.time.Instant;
import java.util.List;

/** Immutable point-in-time view of the status. Lists are defensively copied
 *  so callers can never mutate what the store hands out. */
public record StatusSnapshot(
        @JsonProperty("generated_at") Instant generatedAt,
        List<Source> sources,
        List<Service> services
) {

    public StatusSnapshot {
        sources = List.copyOf(sources);
        services = List.copyOf(services);
    }
}
