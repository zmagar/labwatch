# labwatch

A single-pane status dashboard for a self-hosted homelab. It polls a Proxmox cluster and a Docker host, normalizes both into one shape, and serves a page showing what's running and what isn't.

Built because `pct list` on one box, `docker ps` on another, and a browser tab per service is a bad way to answer "is anything broken right now?"

**This is not a monitoring system.** No time-series storage, no alerting, no history — Prometheus and Grafana already do that job well. This answers one question: what is the state of things at this moment.

---

## Architecture

```
                    ┌──────────────────────────────┐
   Proxmox API ────►│  collectors (poll on timer)  │
                    │                              │
  Docker socket ───►│  ProxmoxCollector            │
                    │  DockerCollector             │
      proxy         └──────────────┬───────────────┘
                                   │
                          normalize to Service
                                   │
                                   ▼
                        ┌────────────────────┐
                        │  in-memory store   │
                        │  (last good state) │
                        └─────────┬──────────┘
                                  │
                        visibility filter  ◄── profile + labels
                                  │
                                  ▼
                        GET /api/status  ──►  browser
```

Three properties worth calling out, because they're the reasons the design looks like this:

**Collectors poll on a timer; the browser never triggers upstream calls.** A page refresh reads whatever the store last saw. Ten open tabs don't become ten times the load on the Proxmox API.

**Credentials never leave the backend.** The browser talks only to `/api/status`. No API token, no socket path, no upstream hostname is ever sent to the client.

**Filtering happens server-side, before serialization.** Hidden services are absent from the JSON, not hidden by CSS. See below.

---

## Visibility model

Services are hidden by default and must opt in. This is an allowlist, deliberately — a denylist fails open, and a container added six months from now would appear in a public view because nobody remembered to exclude it.

Opt-in happens via Docker labels, so visibility lives in the same compose file as the service it describes:

```yaml
services:
  jellyfin:
    labels:
      - "labwatch.show=true"
      - "labwatch.group=media"
      - "labwatch.name=Jellyfin"
      - "labwatch.url=http://192.168.0.244:8096"
```

| Label | Required | Meaning |
|---|---|---|
| `labwatch.show` | yes | `true` to include. Anything else, or absent, means the service is not returned by the API at all. |
| `labwatch.group` | no | Grouping in the UI. Defaults to `other`. |
| `labwatch.name` | no | Display name. Defaults to the container name. |
| `labwatch.url` | no | Link target for the card. |
| `labwatch.profiles` | no | Comma-separated profiles this service appears in. Defaults to `private` only. |

Proxmox guests use the same model via the guest's Notes field or a matching entry in `config.yaml` — the Proxmox API has no label equivalent.

### Profiles

`LABWATCH_PROFILE` selects which services are eligible:

- `private` (default) — everything with `labwatch.show=true`.
- `public` — only services whose `labwatch.profiles` includes `public`.
- `demo` — ignores collectors entirely and serves fixture data from `src/main/resources/demo.json`. Nothing touches the real infrastructure.

`demo` is what a publicly reachable deployment runs.

### Leak checklist

Filtering the service list is not sufficient. Before any screenshot or public deployment, confirm the response body and the UI contain no:

- image names (they identify software even when the display name doesn't)
- volume or bind-mount paths
- port mappings
- Docker network names
- names of dependency containers referenced by a visible service
- internal hostnames or IPs

The normalized `Service` shape below deliberately omits all of these. Don't add them back for convenience.

---

## API

`GET /api/status`

```json
{
  "generated_at": "2026-08-03T14:22:07Z",
  "sources": [
    { "name": "proxmox", "ok": true,  "last_success": "2026-08-03T14:22:05Z", "error": null },
    { "name": "docker",  "ok": false, "last_success": "2026-08-03T14:09:41Z", "error": "connection refused" }
  ],
  "services": [
    {
      "id": "docker:jellyfin",
      "name": "Jellyfin",
      "kind": "container",
      "group": "media",
      "state": "up",
      "detail": "Up 6 days",
      "url": "http://192.168.0.244:8096",
      "cpu_pct": 3.1,
      "mem_bytes": 812187648
    },
    {
      "id": "proxmox:lxc/107",
      "name": "dns247",
      "kind": "lxc",
      "group": "network",
      "state": "up",
      "detail": "uptime 21d",
      "url": null,
      "cpu_pct": 0.4,
      "mem_bytes": 268435456
    }
  ]
}
```

`state` is one of `up`, `down`, `degraded`, `unknown`. Both collectors map onto that set; the UI never branches on which collector produced a service.

`sources` is how partial failure surfaces. If Proxmox is unreachable the endpoint still returns 200 with the last known service list and `ok: false` on that source — a dead upstream should not blank the page. The UI marks stale sections rather than hiding them.

`GET /healthz` returns 200 whenever the process is serving, independent of upstream health.

### Service id contract

Service `id` values follow the pattern `"<source>:<rest>"` where `<source>` matches
the `name` field in the `sources` array — currently `docker` or `proxmox`. The
part before the first colon is the source; source names must never contain a
colon. This lets the UI map a service to its source for staleness without a
separate field.

---

## Configuration

Environment variables. No secrets in `config.yaml`, no secrets in the repo.

| Variable | Default | Notes |
|---|---|---|
| `LABWATCH_PROFILE` | `private` | `private` \| `public` \| `demo` |
| `LABWATCH_ADDR` | `:8080` | Listen address |
| `LABWATCH_POLL_INTERVAL` | `30s` | Applies to all collectors |
| `PROXMOX_URL` | — | e.g. `https://proxmox.example:8006` |
| `PROXMOX_TOKEN_ID` | — | `user@pve!tokenname` |
| `PROXMOX_TOKEN_SECRET` | — | |
| `DOCKER_HOST` | `tcp://socket-proxy:2375` | Points at the socket proxy, never the raw socket |

See `.env.example`.

---

## Deployment

Runs on the Docker host, alongside a socket proxy:

```yaml
services:
  labwatch:
    image: labwatch:latest
    environment:
      - LABWATCH_PROFILE=private
      - DOCKER_HOST=tcp://socket-proxy:2375
    env_file: .env
    ports:
      - "8080:8080"
    depends_on:
      - socket-proxy

  socket-proxy:
    image: tecnativa/docker-socket-proxy
    environment:
      - CONTAINERS=1      # everything else stays off
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
```

Build is a two-stage Dockerfile: `maven:3-eclipse-temurin-21` to build, `eclipse-temurin:21-jre-alpine` to run.

The application container never sees `/var/run/docker.sock`. Access to the Docker socket is equivalent to root on the host, so the proxy exposes exactly one endpoint family and nothing else.

The Proxmox side uses a dedicated API token with the `PVEAuditor` role — read-only, scoped, and revocable without touching any account password.

---

## Development

Java 21, Maven, Javalin for HTTP. No database, no frontend build step.

```
src/main/java/dev/labwatch/
  Main.java                    entrypoint, wiring, poll scheduler
  model/                       Service, Source, State — records
  collect/
    Collector.java             interface: List<Service> collect()
    ProxmoxCollector.java
    DockerCollector.java
  store/StatusStore.java       in-memory last-good state
  http/                        routes, JSON serialization
  visibility/Filter.java       profile + label filtering
src/main/resources/web/        template + static assets
src/main/resources/demo.json   fixture data for demo profile
```

```bash
mvn test
LABWATCH_PROFILE=demo mvn exec:java     # no infrastructure required
```

`Collector` is a single-method interface, and collectors are the only place upstream-specific types exist. Everything past `collect/` sees `List<Service>` and nothing else — that's what makes adding a third source a contained change.

Model types are records, not mutable beans. The store hands out immutable snapshots so a poll running mid-request can't mutate what's being serialized.

### Dependencies

Deliberately few, because every added library is more surface to audit:

- Javalin — HTTP routing
- Jackson — JSON (comes with Javalin)
- `java.net.http.HttpClient` — Proxmox and Docker calls, JDK built-in, no HTTP client dependency
- JUnit 5 — tests

No Spring, no dependency injection framework. Wiring is explicit in `Main`.

---

## Roadmap

- Kubernetes collector
- TrueNAS collector (pool health, scrub status)
- Optional HTTP health probes for services that are "up" as a container but not actually answering
