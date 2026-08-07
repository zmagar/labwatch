package dev.labwatch.collect;

import dev.labwatch.model.Service;
import dev.labwatch.model.State;
import dev.labwatch.visibility.CollectedService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Extracts visibility metadata from Docker labels. Only {@code labwatch.*}
 *  keys are read — OCI annotations, compose metadata, and everything else
 *  in the label map are ignored. */
public final class LabelParser {

    private LabelParser() {
    }

    /** Parsed visibility fields from a container's label map. */
    public record Labels(boolean show, Set<String> profiles, String name,
                         String group, String url) {
    }

    /** Extract visibility metadata from the raw label map.
     *  @param rawLabels  the container's {@code Labels} object as a flat map
     *  @param containerName  first entry of the {@code Names} array (e.g. {@code /jellyfin}) */
    public static Labels parse(Map<String, String> rawLabels, String containerName) {
        boolean show = "true".equals(rawLabels.get("labwatch.show"));

        String rawProfiles = rawLabels.getOrDefault("labwatch.profiles", "private");
        Set<String> profiles = rawProfiles.isBlank()
                ? Set.of("private")
                : Arrays.stream(rawProfiles.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());

        String name = rawLabels.getOrDefault("labwatch.name",
                containerName.replaceFirst("^/", ""));

        String group = rawLabels.getOrDefault("labwatch.group", "other");

        String url = rawLabels.get("labwatch.url"); // null when absent

        return new Labels(show, profiles, name, group, url);
    }
}
