package dev.labwatch.collect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.Set;

/** Calls the Proxmox API and normalises nodes, VMs, and LXCs into
 *  {@link CollectedService}. Storage and pool resources are ignored. */
public class ProxmoxCollector implements Collector {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String url;
    private final String authHeader;
    private final VisibilityConfig config;
    private final boolean insecureTls;

    /** @param url          e.g. {@code https://proxmox.example:8006}
     *  @param tokenId      e.g. {@code user@pve!tokenname}
     *  @param secret       the token secret
     *  @param insecureTls  if true, accept self-signed certificates */
    public ProxmoxCollector(String url, String tokenId, String secret,
                            VisibilityConfig config, boolean insecureTls) {
        this.httpClient = insecureTls ? insecureClient() : HttpClient.newHttpClient();
        this.mapper = Json.mapper();
        this.url = url;
        this.authHeader = "PVEAPIToken=" + tokenId + "=" + secret;
        this.config = config;
        this.insecureTls = insecureTls;
    }

    private static HttpClient insecureClient() {
        try {
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    }
            };
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new java.security.SecureRandom());
            return HttpClient.newBuilder().sslContext(ctx).build();
        } catch (Exception e) {
            throw new RuntimeException("failed to build insecure SSL context", e);
        }
    }

    @Override
    public List<CollectedService> collect() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(url + "/api2/json/cluster/resources"))
                .header("Authorization", authHeader)
                .GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("proxmox collect interrupted", e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("proxmox returned HTTP " + response.statusCode());
        }
        ResourcesResponse body = mapper.readValue(response.body(),
                new TypeReference<>() {
                });
        return parse(body.data(), config);
    }

    /** Package-private so tests can feed a fixture directly. */
    static List<CollectedService> parse(List<ProxmoxResource> resources,
                                        VisibilityConfig config) {
        return resources.stream()
                .filter(r -> {
                    String type = r.type();
                    return "node".equals(type) || "qemu".equals(type) || "lxc".equals(type);
                })
                .map(r -> toCollectedService(r, config))
                .toList();
    }

    private static CollectedService toCollectedService(ProxmoxResource r,
                                                       VisibilityConfig config) {
        VisibilityConfig.Entry entry = config.forId(r.id());

        String displayName = entry.name() != null ? entry.name() : r.name();
        if (displayName == null) displayName = r.id();

        String group = entry.group() != null ? entry.group() : "other";

        Double cpuPct = r.cpu() != null ? r.cpu() * 100.0 : null;
        Long memBytes = r.mem() != null ? r.mem() : null;
        Integer maxCpu = r.maxcpu() != null ? r.maxcpu().intValue() : null;
        Long maxMem = r.maxmem();
        String detail = formatUptime(r.uptime());

        Service service = new Service(
                "proxmox:" + r.id(),
                displayName,
                r.type(), // node / qemu / lxc
                group,
                mapState(r.status()),
                detail,
                null, // url — no equivalent in Proxmox
                cpuPct,
                memBytes,
                maxCpu,
                maxMem,
                null); // createdAt

        return new CollectedService(service, entry.show(), entry.profiles());
    }

    private static State mapState(String proxmoxStatus) {
        return switch (proxmoxStatus) {
            case "running", "online" -> State.UP;
            case "stopped", "offline" -> State.DOWN;
            default -> State.UNKNOWN;
        };
    }

    private static String formatUptime(Long seconds) {
        if (seconds == null) return null;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        if (days > 0) return "Up " + days + " days";
        if (hours > 0) return "Up " + hours + " hours";
        return "Up " + (seconds / 60) + " minutes";
    }

    /** DTO for the JSON array wrapped in {@code {"data": [...]}}.
     *  Proxmox adds fields between versions; ignore unknowns. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResourcesResponse(List<ProxmoxResource> data) {
    }

    /** DTO for a single entry in {@code cluster/resources}.
     *  Proxmox adds fields between versions (disk, maxdisk, uptime,
     *  maxcpu, …); ignore unknowns so the collector tolerates them. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProxmoxResource(
            String id,
            String type,
            String node,
            String status,
            String name,
            Double cpu,
            Long mem,
            Long uptime,
            Long maxcpu,
            Long maxmem) {
    }
}
