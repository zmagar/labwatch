package dev.labwatch.visibility;

import dev.labwatch.model.Service;
import dev.labwatch.store.RawStatus;
import dev.labwatch.store.StatusSnapshot;

import java.util.List;

/** Applies the label rules from the README to produce an API-safe snapshot.
 *  Demo differs from private only in data source, not in filtering: both
 *  filter on {@code show == true}. */
public final class Filter {

    private Filter() {
    }

    /** Produce a {@link StatusSnapshot} containing only services eligible
     *  under {@code profile}. Sources and generated_at pass through unchanged. */
    public static StatusSnapshot apply(RawStatus raw, Profile profile) {
        List<Service> services = switch (profile) {
            case PRIVATE, DEMO -> raw.services().stream()
                    .filter(CollectedService::show)
                    .map(CollectedService::service)
                    .toList();
            case PUBLIC -> raw.services().stream()
                    .filter(cs -> cs.show() && cs.profiles().contains("public"))
                    .map(CollectedService::service)
                    .toList();
        };
        return new StatusSnapshot(raw.generatedAt(), raw.sources(), services);
    }
}
