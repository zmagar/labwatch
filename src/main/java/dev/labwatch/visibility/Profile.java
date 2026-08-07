package dev.labwatch.visibility;

/** Profiles that control which services survive filtering.
 *  Parsed once at startup from {@code LABWATCH_PROFILE}. */
public enum Profile {

    PRIVATE,
    PUBLIC,
    DEMO;

    /** Case-sensitive. Unknown values fail fast rather than silently
     *  falling through to a default — a typo in the env var must not
     *  serve data under the wrong filtering rules. */
    public static Profile fromEnv(String value) {
        for (Profile p : values()) {
            if (p.name().equals(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown LABWATCH_PROFILE: " + value + ". "
                + "Expected one of private, public, demo.");
    }
}
