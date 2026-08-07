package dev.labwatch.visibility;

import dev.labwatch.model.Service;

import java.util.Set;

/** A service plus its visibility metadata. The inner {@link Service} has no
 *  {@code show} or {@code profiles} field — the {@link Filter} extracts it and
 *  discards this wrapper, so visibility data is structurally unable to reach
 *  the serialized JSON. */
public record CollectedService(Service service, boolean show, Set<String> profiles) {

    public CollectedService {
        profiles = Set.copyOf(profiles);
    }
}
