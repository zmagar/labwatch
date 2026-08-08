package dev.labwatch.collect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Proxmox visibility config read from {@code config.yaml} at startup.
 *  Keys are Proxmox guest ids ({@code lxc/107}, {@code qemu/200}); values
 *  carry the same fields as Docker labels. Missing keys default to
 *  {@code show=false} — the same fail-closed rule. Missing file logs a
 *  warning and serves everything as hidden. */
public class VisibilityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(VisibilityConfig.class);

    private final Map<String, Entry> entries;

    /** @param path  absolute or workspace-relative path to config.yaml */
    public VisibilityConfig(Path path) {
        if (!Files.isRegularFile(path)) {
            LOG.warn("{} not found — all Proxmox guests will be treated as show=false", path);
            entries = Map.of();
            return;
        }
        try {
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            entries = yaml.readValue(path.toFile(),
                    new TypeReference<Map<String, Entry>>() {
                    });
            LOG.info("loaded {} Proxmox visibility entries from {}", entries.size(), path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse " + path, e);
        }
    }

    public Entry forId(String id) {
        return entries.getOrDefault(id, Entry.HIDDEN);
    }

    public record Entry(boolean show, String name, String group,
                        Set<String> profiles) {

        /** Default when an id has no config entry. */
        static final Entry HIDDEN = new Entry(false, null, null, Set.of("private"));

        public Entry {
            profiles = profiles != null ? Set.copyOf(profiles) : Set.of("private");
        }
    }
}
