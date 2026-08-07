package dev.labwatch.collect;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelParserTest {

    @Test
    void showDefaultsToFalseWhenKeyAbsent() {
        var labels = LabelParser.parse(Map.of(), "/svc");
        assertFalse(labels.show());
    }

    @Test
    void showTrueWhenExactlyTrue() {
        var labels = LabelParser.parse(Map.of("labwatch.show", "true"), "/svc");
        assertTrue(labels.show());
    }

    @Test
    void showFalseWhenNotTrue() {
        var labels = LabelParser.parse(Map.of("labwatch.show", "false"), "/svc");
        assertFalse(labels.show());
    }

    @Test
    void profilesDefaultsToPrivate() {
        var labels = LabelParser.parse(Map.of("labwatch.show", "true"), "/svc");
        assertEquals(1, labels.profiles().size());
        assertTrue(labels.profiles().contains("private"));
    }

    @Test
    void profilesParsesCommaSeparatedList() {
        var labels = LabelParser.parse(Map.of(
                "labwatch.show", "true",
                "labwatch.profiles", "public,private"), "/svc");
        assertEquals(2, labels.profiles().size());
        assertTrue(labels.profiles().contains("public"));
        assertTrue(labels.profiles().contains("private"));
    }

    @Test
    void profilesHandlesWhitespace() {
        var labels = LabelParser.parse(Map.of(
                "labwatch.show", "true",
                "labwatch.profiles", " public , private "), "/svc");
        assertEquals(2, labels.profiles().size());
        assertTrue(labels.profiles().contains("public"));
        assertTrue(labels.profiles().contains("private"));
    }

    @Test
    void nameFromLabelOverridesContainerName() {
        var labels = LabelParser.parse(Map.of(
                "labwatch.show", "true",
                "labwatch.name", "Web Frontend"), "/web");
        assertEquals("Web Frontend", labels.name());
    }

    @Test
    void nameFallsBackToStrippedContainerName() {
        var labels = LabelParser.parse(Map.of("labwatch.show", "true"), "/jellyfin");
        assertEquals("jellyfin", labels.name());
    }

    @Test
    void groupDefaultsToOther() {
        var labels = LabelParser.parse(Map.of("labwatch.show", "true"), "/svc");
        assertEquals("other", labels.group());
    }

    @Test
    void urlPresentWhenLabeled() {
        var labels = LabelParser.parse(Map.of(
                "labwatch.show", "true",
                "labwatch.url", "http://example.test:8080"), "/svc");
        assertEquals("http://example.test:8080", labels.url());
    }

    @Test
    void urlNullWhenAbsent() {
        var labels = LabelParser.parse(Map.of("labwatch.show", "true"), "/svc");
        assertNull(labels.url());
    }

    @Test
    void ignoresNonLabwatchKeys() {
        var labels = LabelParser.parse(Map.of(
                "labwatch.show", "true",
                "org.opencontainers.image.title", "nginx",
                "com.docker.compose.project", "example"), "/web");
        assertTrue(labels.show());
        assertEquals("other", labels.group());
        assertNull(labels.url());
    }
}
