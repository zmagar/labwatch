const REFRESH_S = 15;
const FETCH_TIMEOUT_MS = 10000;
let lastSnapshot = null;
let lastContact = null;
let connectionLost = false;
let refreshPending = false;

function relativeTime(iso) {
  if (!iso) return "never";
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 5) return "just now";
  if (diff < 60) return Math.floor(diff) + "s ago";
  if (diff < 3600) return Math.floor(diff / 60) + "m ago";
  if (diff < 86400) return Math.floor(diff / 3600) + "h ago";
  return Math.floor(diff / 86400) + "d ago";
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

function age(iso, prefix) {
  const node = element("span", "relative-time");
  node.dataset.time = iso || "";
  node.dataset.prefix = prefix;
  node.textContent = prefix + relativeTime(iso);
  return node;
}

// Runs independently of fetches, including while offline or waiting for a response.
function updateAges() {
  document.querySelectorAll(".relative-time").forEach(node => {
    node.textContent = node.dataset.prefix + relativeTime(node.dataset.time);
  });
}

function formatMem(bytes) {
  if (!bytes && bytes !== 0) return "";
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + " GiB";
  if (bytes >= 1048576) return Math.round(bytes / 1048576) + " MiB";
  return "0 B";
}

function sourceForService(serviceId, sources) {
  const prefix = serviceId.split(":")[0];
  return sources.find(s => s.name === prefix);
}

function safeHttpUrl(raw) {
  try {
    const url = new URL(raw);
    return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
  } catch {
    return null;
  }
}

async function render() {
  if (refreshPending) return;
  refreshPending = true;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const resp = await fetch("/api/status", {signal: controller.signal, cache: "no-store"});
    if (!resp.ok) throw new Error("HTTP " + resp.status);
    const data = await resp.json();
    if (!Array.isArray(data.sources) || !Array.isArray(data.services) || !data.generated_at) {
      throw new Error("Invalid status response");
    }
    lastSnapshot = data;
    lastContact = new Date().toISOString();
    connectionLost = false;
  } catch (e) {
    connectionLost = true;
    console.error("fetch failed", e);
  } finally {
    clearTimeout(timeout);
    refreshPending = false;
  }
  renderDashboard();
}

function renderDashboard() {
  const connection = document.getElementById("connection-status");
  connection.hidden = !connectionLost;
  connection.replaceChildren();
  if (connectionLost) {
    connection.append(element("strong", "", "Page cannot reach labwatch. "));
    connection.append(document.createTextNode(lastSnapshot
      ? "Showing retained data. Source badges describe the last report. "
      : "No status has been received yet. "));
    connection.append(age(lastContact, "Last contact: "));
  }

  if (!lastSnapshot) {
    document.getElementById("services").replaceChildren(
      element("div", "empty-message", "Waiting for status from labwatch."));
    return;
  }
  document.getElementById("update-time").replaceChildren(age(lastSnapshot.generated_at, "updated "));
  renderSources(lastSnapshot.sources);
  renderServices(lastSnapshot.services, lastSnapshot.sources);
}

function renderSources(sources) {
  const badges = sources.map(src => {
    const prefix = connectionLost ? "Last report: " : "";
    const text = src.ok ? "✓ labwatch can reach " : "✗ labwatch cannot reach ";
    const badge = element("span", "source-badge " + (src.ok ? "ok" : "failed"),
      prefix + text + src.name);
    if (!src.ok) badge.append(age(src.last_success, " · last seen "));
    return badge;
  });
  document.getElementById("sources").replaceChildren(...badges);
}

function renderServices(services, sources) {
  const el = document.getElementById("services");
  if (services.length === 0) {
    el.replaceChildren(element("div", "empty-message", "No services — nothing is configured to show."));
    return;
  }

  const groups = new Map();
  for (const svc of services) {
    const g = groups.get(svc.group) || [];
    g.push(svc);
    groups.set(svc.group, g);
  }

  const nodes = [];
  for (const group of [...groups.keys()].sort()) {
    nodes.push(element("div", "group-heading", group));
    const sorted = groups.get(group).sort((a, b) => a.name.localeCompare(b.name));
    for (const svc of sorted) nodes.push(serviceCard(svc, sources));
  }
  el.replaceChildren(...nodes);
}

function serviceCard(svc, sources) {
  const source = sourceForService(svc.id, sources);
  const collectorStale = source && !source.ok;
  const state = ["up", "down", "degraded", "unknown"].includes(svc.state) ? svc.state : "unknown";
  const card = element("div", "service-card state-" + state);
  card.classList.toggle("stale", Boolean(collectorStale || connectionLost));
  card.classList.toggle("collector-stale", Boolean(collectorStale));
  card.classList.toggle("browser-stale", connectionLost);
  card.append(element("div", "state-dot"), element("div", "service-name", svc.name));

  const flags = element("div", "service-flags");
  if (collectorStale) flags.append(element("span", "stale-badge", source.name + " data stale"));
  if (connectionLost) flags.append(element("span", "browser-stale-badge", "page disconnected"));
  card.append(flags);

  const meta = element("div", "service-meta");
  meta.append(element("span", "service-kind", svc.kind));
  if (source && source.name === "proxmox") {
    const cpuPart = svc.cpu_pct != null
      ? `cpu: ${svc.cpu_pct.toFixed(1)}%` + (svc.max_cpu ? ` of ${svc.max_cpu} cores` : "")
      : "";
    const memPart = svc.mem_bytes != null
      ? formatMem(svc.mem_bytes) + (svc.max_mem ? ` / ${formatMem(svc.max_mem)}` : "")
      : "";
    meta.append(document.createTextNode([cpuPart, memPart].filter(Boolean).join(" · ")));
  } else if (source && source.name === "docker" && svc.created_at) {
    meta.append(age(svc.created_at, "created "));
  }
  card.append(meta);
  if (svc.detail) card.append(element("div", "service-detail", svc.detail));
  if (svc.url) {
    const href = safeHttpUrl(svc.url);
    const link = element(href ? "a" : "span", "service-url", svc.url);
    if (href) {
      link.href = href;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
    }
    card.append(link);
  }
  return card;
}

render();
setInterval(render, REFRESH_S * 1000);
setInterval(updateAges, 1000);
