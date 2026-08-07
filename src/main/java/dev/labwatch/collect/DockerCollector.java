package dev.labwatch.collect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.labwatch.http.Json;
import dev.labwatch.model.Service;
import dev.labwatch.model.State;
import dev.labwatch.visibility.CollectedService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/** Calls the Docker socket proxy and normalises containers into
 *  {@link CollectedService}. The endpoint URL is passed via constructor;
 *  {@code DOCKER_HOST} is read once by {@code Main} (M04 wiring). */
public class DockerCollector implements Collector {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String endpoint;

    public DockerCollector(String endpoint) {
        this.endpoint = normalize(endpoint);
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = Json.mapper();
    }

    /** Normalise the {@code DOCKER_HOST} value into an HTTP URL.
     *  {@code tcp://} is the conventional Docker scheme; HttpClient speaks
     *  {@code http}. {@code unix://} is rejected because the socket proxy
     *  is the only supported path — raw socket access is root-equivalent
     *  and must not work even by accident. */
    static String normalize(String raw) {
        String s = raw.trim();
        if (s.startsWith("unix://")) {
            throw new IllegalArgumentException(
                    "unix:// socket paths are not supported. "
                    + "Point DOCKER_HOST at a socket proxy: tcp://socket-proxy:2375");
        }
        if (s.startsWith("tcp://")) {
            s = "http://" + s.substring("tcp://".length());
        }
        return s;
    }

    @Override
    public List<CollectedService> collect() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(endpoint + "/containers/json?all=true")).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("docker collect interrupted", e);
        }
        List<ContainerSummary> containers =
                mapper.readValue(response.body(), new TypeReference<>() {
                });
        return parse(containers);
    }

    /** Package-private so tests can feed a fixture directly. */
    static List<CollectedService> parse(List<ContainerSummary> containers) {
        return containers.stream()
                .map(DockerCollector::toCollectedService)
                .toList();
    }

    private static CollectedService toCollectedService(ContainerSummary c) {
        String containerName = c.names().get(0); // Docker Names is never empty
        LabelParser.Labels labels = LabelParser.parse(c.labels(), containerName);

        String id = "docker:" + containerName.replaceFirst("^/", "");

        Service service = new Service(
                id,
                labels.name(),
                "container",
                labels.group(),
                mapState(c.state()),
                c.status(),
                labels.url(),
                null, // cpuPct — stats API not in scope for this milestone
                null  // memBytes
        );

        return new CollectedService(service, labels.show(), labels.profiles());
    }

    private static State mapState(String dockerState) {
        return switch (dockerState) {
            case "running" -> State.UP;
            case "restarting", "paused" -> State.DEGRADED;
            case "exited", "dead", "created" -> State.DOWN;
            default -> State.UNKNOWN;
        };
    }

    /** Minimal DTO for {@code GET /containers/json}. PascalCase JSON keys
     *  are mapped explicitly; unknown fields in the response (ImageID,
     *  HostConfig, Health, NetworkSettings, Mounts, …) are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContainerSummary(
            @JsonProperty("Id") String id,
            @JsonProperty("Names") List<String> names,
            @JsonProperty("State") String state,
            @JsonProperty("Status") String status,
            @JsonProperty("Labels") Map<String, String> labels) {
    }
}
