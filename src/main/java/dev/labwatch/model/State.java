package dev.labwatch.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Normalized service state. Both collectors map onto this set; the UI
 *  never branches on which collector produced a service. */
public enum State {

    UP("up"),
    DOWN("down"),
    DEGRADED("degraded"),
    UNKNOWN("unknown");

    private final String wire;

    State(String wire) {
        this.wire = wire;
    }

    /** Serializes as the lowercase wire value: {@code "up"}, not {@code "UP"}. */
    @JsonValue
    public String wire() {
        return wire;
    }

    /** Parses the lowercase wire value back into the enum. Unrecognized or
     *  null input maps to {@link #UNKNOWN} instead of throwing: Docker reports
     *  states like {@code created}/{@code restarting}/{@code paused}/{@code exited}/{@code dead}
     *  that we don't model, and an unmapped state must degrade to unknown rather
     *  than break a collector (M03) or the whole payload. */
    @JsonCreator
    public static State fromWire(String value) {
        for (State state : values()) {
            if (state.wire.equals(value)) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
