package dev.labwatch.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Shared Jackson configuration. Timestamps serialize as ISO-8601 strings
 *  ({@code 2026-08-03T14:22:07Z}) via the jsr310 module. Field naming is NOT
 *  globally switched to snake_case: the same mapper will later parse Docker
 *  (PascalCase) and Proxmox responses, so renaming is explicit per field with
 *  {@code @JsonProperty}. */
public final class Json {

    private Json() {
    }

    public static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
