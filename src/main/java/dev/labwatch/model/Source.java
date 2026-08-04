package dev.labwatch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Per-source health, how partial failure surfaces in the payload. */
public record Source(
        String name,
        boolean ok,
        @JsonProperty("last_success") Instant lastSuccess,
        String error
) {
}
