package dev.labwatch.collect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityConfigTest {

    @Test
    void loadsYamlConfig() throws Exception {
        String yaml = """
                "lxc/107":
                  show: true
                  name: dns247
                  group: network
                  profiles: [private]
                "qemu/200":
                  show: false
                  profiles: [public]
                """;
        Path file = Files.createTempFile("test-config", ".yaml");
        Files.writeString(file, yaml);
        try {
            VisibilityConfig config = new VisibilityConfig(file);
            assertTrue(config.forId("lxc/107").show());
            assertEquals("dns247", config.forId("lxc/107").name());
            assertEquals("network", config.forId("lxc/107").group());
            assertTrue(config.forId("lxc/107").profiles().contains("private"));

            assertFalse(config.forId("qemu/200").show());
            assertTrue(config.forId("qemu/200").profiles().contains("public"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void missingIdDefaultsToHidden() throws Exception {
        String yaml = """
                "lxc/107":
                  show: true
                """;
        Path file = Files.createTempFile("test-config", ".yaml");
        Files.writeString(file, yaml);
        try {
            VisibilityConfig config = new VisibilityConfig(file);
            VisibilityConfig.Entry absent = config.forId("qemu/999");
            assertFalse(absent.show());
            assertNotNull(absent.profiles());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void missingFileTreatsEverythingAsHidden(@TempDir Path tmp) {
        Path missing = tmp.resolve("nonexistent.yaml");
        VisibilityConfig config = new VisibilityConfig(missing);
        assertFalse(config.forId("lxc/107").show());
        assertFalse(config.forId("qemu/200").show());
    }

    @Test
    void profilesDefaultsToPrivateWhenAbsentInYaml() throws Exception {
        String yaml = """
                "lxc/107":
                  show: true
                """;
        Path file = Files.createTempFile("test-config", ".yaml");
        Files.writeString(file, yaml);
        try {
            VisibilityConfig config = new VisibilityConfig(file);
            assertEquals(Set.of("private"), config.forId("lxc/107").profiles());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
