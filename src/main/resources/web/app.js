const REFRESH_S = 15;

function relativeTime(iso) {
  if (!iso) return "never";
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 5) return "just now";
  if (diff < 60) return Math.floor(diff) + "s ago";
  if (diff < 3600) return Math.floor(diff / 60) + "m ago";
  if (diff < 86400) return Math.floor(diff / 3600) + "h ago";
  return Math.floor(diff / 86400) + "d ago";
}

function formatMem(bytes) {
  if (!bytes && bytes !== 0) return "";
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + " GiB";
  if (bytes >= 1048576) return Math.round(bytes / 1048576) + " MiB";
  return "0 B";
}

function formatCpu(pct) {
  if (pct == null) return "";
  return pct.toFixed(1) + "%";
}

function sourceForService(serviceId, sources) {
  const prefix = serviceId.split(":")[0];
  return sources.find(s => s.name === prefix);
}

async function render() {
  try {
    const resp = await fetch("/api/status");
    if (!resp.ok) return;
    const data = await resp.json();
    document.getElementById("update-time").textContent =
      "updated " + relativeTime(data.generated_at);

    renderSources(data.sources);
    renderServices(data.services, data.sources);
  } catch (e) {
    console.error("fetch failed", e);
  }
}

function renderSources(sources) {
  const el = document.getElementById("sources");
  el.innerHTML = sources.map(src => {
    const cls = src.ok ? "ok" : "failed";
    const seen = src.ok ? "" : " · last seen " + relativeTime(src.last_success);
    return `<span class="source-badge ${cls}">
      <span>${src.ok ? "&#10003;" : "&#10007;"}</span>
      ${src.name}${seen}
    </span>`;
  }).join("");
}

function renderServices(services, sources) {
  const el = document.getElementById("services");
  if (services.length === 0) {
    el.innerHTML = `<div class="empty-message">No services — nothing is configured to show.</div>`;
    return;
  }

  const groups = new Map();
  for (const svc of services) {
    const g = groups.get(svc.group) || [];
    g.push(svc);
    groups.set(svc.group, g);
  }

  const sortedGroups = [...groups.keys()].sort();
  let html = "";
  for (const group of sortedGroups) {
    html += `<div class="group-heading">${group}</div>`;
    const sorted = groups.get(group).sort((a, b) => a.name.localeCompare(b.name));
    for (const svc of sorted) {
      html += serviceCard(svc, sources);
    }
  }
  el.innerHTML = html;
}

function serviceCard(svc, sources) {
  const source = sourceForService(svc.id, sources);
  const stale = source && !source.ok;
  const staleBadge = stale
    ? `<span class="stale-badge" title="last seen ${relativeTime(source.last_success)}">stale</span>`
    : "";

  const detail = svc.detail ? `<div class="service-detail">${svc.detail}</div>` : "";
  const url = svc.url
    ? `<a class="service-url" href="${svc.url}" target="_blank" rel="noopener">${svc.url}</a>`
    : "";

  let resource = "";
  if (source && source.name === "proxmox") {
    const cpuPart = svc.cpu_pct != null
      ? `cpu: ${svc.cpu_pct.toFixed(1)}%` + (svc.max_cpu ? ` of ${svc.max_cpu} cores` : "")
      : "";
    const memPart = svc.mem_bytes != null
      ? formatMem(svc.mem_bytes) + (svc.max_mem ? ` / ${formatMem(svc.max_mem)}` : "")
      : "";
    resource = [cpuPart, memPart].filter(Boolean).join(" · ");
  } else if (source && source.name === "docker" && svc.created_at) {
    resource = `created ${relativeTime(svc.created_at)}`;
  }

  return `<div class="service-card state-${svc.state} ${stale ? "stale" : ""}">
    <div class="state-dot"></div>
    <div class="service-name">${svc.name}</div>
    ${staleBadge}
    <div class="service-meta"><span class="service-kind">${svc.kind}</span> ${resource}</div>
    ${detail}
    ${url}
  </div>`;
}

render();
setInterval(render, REFRESH_S * 1000);
