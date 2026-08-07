package dev.labwatch.visibility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileTest {

    @Test
    void acceptsLowercase() {
        assertEquals(Profile.PRIVATE, Profile.fromEnv("private"));
        assertEquals(Profile.PUBLIC, Profile.fromEnv("public"));
        assertEquals(Profile.DEMO, Profile.fromEnv("demo"));
    }

    @Test
    void acceptsUppercase() {
        assertEquals(Profile.PRIVATE, Profile.fromEnv("PRIVATE"));
        assertEquals(Profile.PUBLIC, Profile.fromEnv("PUBLIC"));
        assertEquals(Profile.DEMO, Profile.fromEnv("DEMO"));
    }

    @Test
    void rejectsUnknownWithDescriptiveMessage() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Profile.fromEnv("bogus"));
        assertTrue(ex.getMessage().contains("bogus"));
        assertTrue(ex.getMessage().contains("private"));
        assertTrue(ex.getMessage().contains("public"));
        assertTrue(ex.getMessage().contains("demo"));
    }
}
