package dev.labwatch.collect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpTimeoutsTest {
    @Test
    void parsesDocumentedUnits() {
        assertEquals(Duration.ofMillis(250), HttpTimeouts.parse("TIMEOUT", "250ms"));
        assertEquals(Duration.ofSeconds(10), HttpTimeouts.parse("TIMEOUT", "10s"));
        assertEquals(Duration.ofSeconds(5), HttpTimeouts.parse("TIMEOUT", " 5 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0ms", "-1s", "", "oops", "1m", "1.5s", "10seconds", "9999999999999999999s"})
    void invalidTimeoutFailsWithSettingName(String value) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> HttpTimeouts.parse("LABWATCH_REQUEST_TIMEOUT", value));
        assertTrue(error.getMessage().contains("LABWATCH_REQUEST_TIMEOUT"));
    }

    @Test
    void rejectsNonPositiveConstructorValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpTimeouts(Duration.ZERO, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class,
                () -> new HttpTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(-1)));
    }
}
