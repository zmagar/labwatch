# Milestones

One milestone per working session. Each is independently testable, and the
first three need no access to real infrastructure at all.

Rules that apply to every milestone:

- Plan mode first. Read the plan and correct it before approving any edits.
- `mvn test` passes before the session ends. No exceptions, no "fix it next time."
- Review the diff in IntelliJ before committing. Never `git add -A` unreviewed.
- Commit on a branch named for the milestone; merge when it's green.

---

## 01 — Skeleton, model, and the demo profile

**Scope**

- Maven project builds and runs.
- `model` package: `Service`, `Source`, `State` as records / enum.
- `StatusStore` holding the last good state, handing out immutable snapshots.
- `GET /healthz` → 200 whenever the process is serving.
- `GET /api/status` → the shape defined in the README.
- `LABWATCH_PROFILE=demo` loads `src/main/resources/demo.json` fixtures and
  serves them. No collectors exist yet.

**Acceptance**

- `LABWATCH_PROFILE=demo mvn exec:java` serves a valid status payload on :8080.
- Timestamps serialize as ISO-8601 (jsr310 module registered).
- A test asserts the JSON shape against the README example.
- `/healthz` returns 200 with no fixtures loaded at all.

**Out of scope:** any real API call, any HTML.

**Status:** complete.

---

## 02 — Visibility filtering

Deliberately before the collectors, so it can be tested against fixtures with
zero infrastructure — and so filtering exists before there is ever real data
to leak.

**Scope**

- `Filter` applying the label rules from the README.
- Profile resolution: `private`, `public`, `demo`.
- Filtering happens in the service layer, before serialization.

**Acceptance**

- A service without `labwatch.show=true` never appears in the payload, under
  any profile.
- Under `public`, a service whose `labwatch.profiles` omits `public` is absent.
- Test asserts on the **serialized JSON string**, not on the object graph —
  the point is that hidden names are not in the response body.
- Test asserts the payload contains no image names, volume paths, or port
  mappings for any service.

**Status:** complete. Both filter arms mutation-verified — breaking either
predicate fails a test.

---

## 03 — Docker collector

**Scope**

- `DockerCollector` implementing `Collector`, using `java.net.http.HttpClient`
  against the socket proxy.
- Reads container list, maps labels, normalizes to `Service`.
- Container state → `up` / `down` / `degraded` / `unknown`.

**Acceptance**

- Returns real containers from `.244`.
- Labels are honored end to end: adding `labwatch.show=true` to a compose file
  and recreating the container makes it appear.
- Collector failure returns an error to the caller rather than throwing out of
  the poll loop.
- Parsing is unit-tested against a captured JSON response committed as a
  fixture (scrubbed of anything real).
- The label-parsing path must also be tested for the default
  (`show=false` when `labwatch.show` is absent from the label map).
  `FilterTest` M02 covers the constructor-default case; M03 needs the
  real label-map path.

### State mapping (decided)

Docker `State` maps to `State` as follows:

| Docker        | labwatch   |
|---------------|------------|
| `running`     | `UP`       |
| `restarting`  | `DEGRADED` |
| `paused`      | `DEGRADED` |
| `exited`      | `DOWN`     |
| `dead`        | `DOWN`     |
| `created`     | `DOWN`     |
| anything else | `UNKNOWN`  |

The `Health` object is deliberately ignored in M03 — a running container
reporting `unhealthy` still maps to `UP`. Revisit when HTTP health probes
are added.

### Live verification — 2026-08-07

Verified against real Docker on `.244`, not only fixtures:

- Collected 39 real containers through the socket proxy over an SSH tunnel,
  using a `tcp://` endpoint — exercising the scheme normalization that unit
  tests could not reach.
- With no `labwatch.*` labels present anywhere, 0 of 39 were visible. The
  allowlist default holds against real data.
- After labelling one container `labwatch.show=true`, exactly one service
  appeared, with `labwatch.name` and `labwatch.group` applied.

**Known gap:** `cpu_pct` and `mem_bytes` come back null. `/containers/json`
does not return resource usage; populating them needs a per-container
`/containers/{id}/stats` call and an additional socket-proxy permission.
Decide in M04 whether to fill them or drop the fields.

**Status:** complete.

---

## 04 — Proxmox collector and partial failure

**Scope**

- `ProxmoxCollector` against `/api2/json/cluster/resources`, token auth.
- Nodes, VMs, LXCs normalized to the same `Service` shape.
- `sources` array populated with per-source ok / last_success / error.
- Poll scheduler runs both collectors on `LABWATCH_POLL_INTERVAL`.

**Acceptance**

- With Proxmox unreachable, `/api/status` still returns 200, still returns the
  last known services, and reports `ok: false` for that source.
- Stopping the socket proxy produces the same behavior for Docker.
- The token is never present in any response body or log line.
- Verified live: `docker stop socket-proxy` while the app is running degrades
  that source without blanking the page.

---

## 05 — The page

**Scope**

- Server-rendered HTML, grouped cards, health colors.
- Stale-source indicator driven by the `sources` array.
- Auto-refresh via polling `/api/status`.

**Acceptance**

- Readable on a phone.
- A down source shows as a visible stale badge, not an empty page.
- View-source contains nothing that `/api/status` doesn't already expose.

---

## 06 — Package and deploy

**Scope**

- Two-stage Dockerfile: `maven:3-eclipse-temurin-21` build,
  `eclipse-temurin:21-jre-alpine` runtime.
- Compose file with the socket proxy, per the README.
- Deployed on `.244`.

**Acceptance**

- Comes up clean from `docker compose up -d` on a machine with only `.env`.
- App container has no access to `/var/run/docker.sock`.
- Socket proxy exposes `CONTAINERS=1` and nothing else.
- README instructions work when followed literally by someone who has never
  seen the project.

---

## Later

- Kubernetes collector
- TrueNAS collector (pool health, scrub status)
- HTTP health probes for services that are "up" as a container but not
  actually answering