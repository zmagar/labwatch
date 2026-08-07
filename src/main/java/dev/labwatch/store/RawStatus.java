package dev.labwatch.store;

import dev.labwatch.model.Source;
import dev.labwatch.visibility.CollectedService;

import java.time.Instant;
import java.util.List;

/** Internal snapshot held by {@link StatusStore}. Not exposed to the API:
 *  the {@link dev.labwatch.visibility.Filter} converts it to a
 *  {@link StatusSnapshot} after stripping visibility metadata. */
public record RawStatus(
        Instant generatedAt,
        List<Source> sources,
        List<CollectedService> services
) {

    public RawStatus {
        sources = List.copyOf(sources);
        services = List.copyOf(services);
    }
}
