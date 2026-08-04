package dev.labwatch.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.http.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** State must round-trip through JSON as the lowercase wire values from the
 *  README: up / down / degraded / unknown — never the Java constant names. */
class StateTest {

    private final ObjectMapper mapper = Json.mapper();

    @Test
    void serializesToLowercaseWireValues() throws Exception {
        assertEquals("\"up\"", mapper.writeValueAsString(State.UP));
        assertEquals("\"down\"", mapper.writeValueAsString(State.DOWN));
        assertEquals("\"degraded\"", mapper.writeValueAsString(State.DEGRADED));
        assertEquals("\"unknown\"", mapper.writeValueAsString(State.UNKNOWN));
    }

    @Test
    void parsesLowercaseWireValuesBack() throws Exception {
        assertEquals(State.UP, mapper.readValue("\"up\"", State.class));
        assertEquals(State.DOWN, mapper.readValue("\"down\"", State.class));
        assertEquals(State.DEGRADED, mapper.readValue("\"degraded\"", State.class));
        assertEquals(State.UNKNOWN, mapper.readValue("\"unknown\"", State.class));
    }

    @Test
    void unknownWireValueParsesAsUnknown() throws Exception {
        assertEquals(State.UNKNOWN, mapper.readValue("\"bogus\"", State.class));
    }

    @Test
    void fromWireMapsUnknownAndNullToUnknown() {
        assertEquals(State.UNKNOWN, State.fromWire("bogus"));
        assertEquals(State.UNKNOWN, State.fromWire(null));
    }
}
