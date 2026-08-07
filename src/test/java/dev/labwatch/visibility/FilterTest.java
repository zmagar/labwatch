package dev.labwatch.visibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.http.Json;
import dev.labwatch.model.Service;
import dev.labwatch.model.Source;
import dev.labwatch.model.State;
import dev.labwatch.store.RawStatus;
import dev.labwatch.store.StatusSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Filtering behaviour before any collector exists: test against programmatic
 *  CollectedService objects so we can control exactly what the collectors will
 *  produce once M03/M04 add them. */
class FilterTest {

    private static final ObjectMapper MAPPER = Json.mapper();

    private static final CollectedService hiddenByDefault =
            collected("hidden-default", "HiddenDefault", false, Set.of("private"));

    private static final CollectedService showFalse =
            collected("show-false", "ShowFalse", false, Set.of("private"));

    private static final CollectedService onlyPrivate =
            collected("only-private", "OnlyPrivate", true, Set.of("private"));

    private static final CollectedService publicService =
            collected("public-svc", "PublicService", true, Set.of("public"));

    private static final CollectedService showFalsePublic =
            collected("false-public", "FalsePublic", false, Set.of("public"));

    private static final CollectedService showTrueNoProfiles =
            collected("noprofiles", "NoProfiles", true, Set.of());

    private static final List<CollectedService> ALL = List.of(
            hiddenByDefault, showFalse, onlyPrivate, publicService);

    private static final Source SRC = new Source("docker", true, Instant.EPOCH, null);

    @Test
    void showFalseExcludedFromPrivate() throws Exception {
        StatusSnapshot api = Filter.apply(raw(ALL), Profile.PRIVATE);
        String body = MAPPER.writeValueAsString(api);
        assertEquals(2, MAPPER.readTree(body).get("services").size());
        assertFalse(body.contains("\"ShowFalse\""));
        assertTrue(body.contains("\"OnlyPrivate\""));
        assertTrue(body.contains("\"PublicService\""));
    }

    @Test
    void noShowKeyDefaultsToFalseAndIsFilteredOut() throws Exception {
        // This is the default the Docker label parser will produce in M03
        // when labwatch.show is absent from the label map. See MILESTONES.md 03.
        CollectedService defaulted = new CollectedService(
                svc("noshow-default", "NoShowDefault"), false, Set.of("private"));
        StatusSnapshot api = Filter.apply(
                raw(List.of(defaulted, showFalse)), Profile.PRIVATE);
        String body = MAPPER.writeValueAsString(api);
        assertFalse(body.contains("\"NoShowDefault\""));
        assertFalse(body.contains("\"ShowFalse\""));
    }

    @Test
    void showTrueOnlyPrivateOmittedFromPublicEntireBody() throws Exception {
        StatusSnapshot api = Filter.apply(raw(ALL), Profile.PUBLIC);
        String body = MAPPER.writeValueAsString(api);
        assertEquals(1, MAPPER.readTree(body).get("services").size());
        assertTrue(body.contains("\"PublicService\""));
        // absent from the entire response body string, not just the services array
        assertFalse(body.contains("\"OnlyPrivate\""));
        assertFalse(body.contains("\"ShowFalse\""));
        assertFalse(body.contains("\"HiddenDefault\""));
    }

    @Test
    void showTruePublicVisibleInAllProfiles() throws Exception {
        for (Profile profile : Profile.values()) {
            StatusSnapshot api = Filter.apply(raw(ALL), profile);
            String body = MAPPER.writeValueAsString(api);
            assertTrue(body.contains("\"PublicService\""),
                    "public service should appear in " + profile);
        }
    }

    @Test
    void demoFiltersSameAsPrivate() throws Exception {
        RawStatus raw = raw(ALL);
        StatusSnapshot fromDemo = Filter.apply(raw, Profile.DEMO);
        StatusSnapshot fromPrivate = Filter.apply(raw, Profile.PRIVATE);
        assertEquals(MAPPER.writeValueAsString(fromDemo),
                MAPPER.writeValueAsString(fromPrivate));
    }

    @Test
    void showFalseWithPublicProfilesAbsentFromPublic() throws Exception {
        StatusSnapshot api = Filter.apply(
                raw(List.of(showFalsePublic, publicService)), Profile.PUBLIC);
        String body = MAPPER.writeValueAsString(api);
        assertEquals(1, MAPPER.readTree(body).get("services").size());
        assertTrue(body.contains("\"PublicService\""));
        assertFalse(body.contains("\"FalsePublic\""));
    }

    @Test
    void showTrueWithEmptyProfilesAbsentFromPublicPresentInPrivate() throws Exception {
        // public: empty profiles doesn't contain "public" → absent
        StatusSnapshot api = Filter.apply(
                raw(List.of(showTrueNoProfiles, publicService)), Profile.PUBLIC);
        String body = MAPPER.writeValueAsString(api);
        assertEquals(1, MAPPER.readTree(body).get("services").size());
        assertTrue(body.contains("\"PublicService\""));
        assertFalse(body.contains("\"NoProfiles\""));

        // private: show=true is all that matters → present
        api = Filter.apply(raw(List.of(showTrueNoProfiles)), Profile.PRIVATE);
        body = MAPPER.writeValueAsString(api);
        assertEquals(1, MAPPER.readTree(body).get("services").size());
        assertTrue(body.contains("\"NoProfiles\""));
    }

    @Test
    void noLeakedFieldsInServiceJson() throws Exception {
        // The Service record must not carry image names, volume paths, port
        // mappings, or network names — every service in the response must
        // have exactly these 9 keys and no others.
        StatusSnapshot api = Filter.apply(raw(ALL), Profile.PRIVATE);
        String body = MAPPER.writeValueAsString(api);
        Set<String> allowedKeys = Set.of(
                "id", "name", "kind", "group", "state",
                "detail", "url", "cpu_pct", "mem_bytes");
        for (var node : MAPPER.readTree(body).get("services")) {
            Set<String> keys = new java.util.HashSet<>();
            node.fieldNames().forEachRemaining(keys::add);
            assertEquals(allowedKeys, keys,
                    "Service field names must match the README shape exactly");
        }
    }

    private static RawStatus raw(List<CollectedService> services) {
        return new RawStatus(Instant.now(), List.of(SRC), services);
    }

    private static CollectedService collected(String id, String name,
                                              boolean show, Set<String> profiles) {
        return new CollectedService(svc(id, name), show, profiles);
    }

    private static Service svc(String id, String name) {
        return new Service(id, name, "container", "other", State.UP,
                "test detail", null, 1.0, 100L);
    }
}
