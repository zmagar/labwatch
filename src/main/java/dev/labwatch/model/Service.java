package dev.labwatch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** A normalized service as served by the API. Deliberately omits image
 *  names, volume paths, port mappings, and network names — see the README
 *  leak checklist. */
public record Service(
        String id,
        String name,
        String kind,
        String group,
        State state,
        String detail,
        String url,
        @JsonProperty("cpu_pct") Double cpuPct,
        @JsonProperty("mem_bytes") Long memBytes,
        @JsonProperty("max_cpu") Integer maxCpu,
        @JsonProperty("max_mem") Long maxMem,
        @JsonProperty("created_at") Instant createdAt
) {
}
