package dev.labwatch.collect;

import java.time.Duration;

/** Shared upstream HTTP limits, resolved once at startup. */
public record HttpTimeouts(Duration connect, Duration request) {
    public static final HttpTimeouts DEFAULT =
            new HttpTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(10));

    public HttpTimeouts {
        validate("connect timeout", connect);
        validate("request timeout", request);
    }

    /** Accept whole milliseconds (250ms), seconds (10s), or bare seconds (10). */
    public static Duration parse(String name, String value) {
        try {
            String text = value.trim();
            if (!text.matches("[0-9]+(ms|s)?")) throw new IllegalArgumentException();
            Duration duration = text.endsWith("ms")
                    ? Duration.ofMillis(Long.parseLong(text.substring(0, text.length() - 2)))
                    : Duration.ofSeconds(Long.parseLong(text.replaceFirst("s$", "")));
            validate(name, duration);
            return duration;
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new IllegalArgumentException(name + " must be a positive duration "
                    + "in whole milliseconds or seconds (e.g. 250ms or 10s)", e);
        }
    }

    private static void validate(String name, Duration value) {
        if (value == null || value.toMillis() < 1) {
            throw new IllegalArgumentException(name + " must be at least 1ms");
        }
    }
}
