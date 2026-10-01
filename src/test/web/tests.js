// Real DOM checks plus live failure reproduction through the local test proxy.
const results = [];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const post = (path, data) => fetch(path, {
  method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(data)
});
const control = data => post("/control", data);
async function check(condition, message) {
  if (!condition) throw new Error(message);
  results.push(message);
  document.getElementById("results").textContent = results.join("\n");
  await post("/progress", {message: "PASS: " + message});
}
async function until(fn, timeout = 15000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const value = await fn();
    if (value) return value;
    await sleep(100);
  }
  throw new Error("Condition not reached within " + timeout + "ms");
}
const snapshot = () => fetch("/api/status").then(r => r.json());
const source = (data, name) => data.sources.find(s => s.name === name);

(async () => {
  try {
    const frame = document.getElementById("dashboard");
    await until(() => frame.contentWindow.render && frame.contentDocument.querySelector(".service-card"));
    const w = frame.contentWindow;
    const doc = frame.contentDocument;
    const originalFetch = w.fetch;
    const banner = () => doc.getElementById("connection-status");
    const cards = () => [...doc.querySelectorAll(".service-card")];
    const fresh = await snapshot();
    const injected = structuredClone(fresh);
    const markup = '<img src=x onerror="window.labelExecuted=true">';
    const svc = injected.services[0];
    svc.name = svc.group = svc.detail = markup;
    svc.url = 'https://example.test/" onmouseover="window.labelExecuted=true';
    w.fetch = async () => new w.Response(JSON.stringify(injected));
    await w.render();
    await check(doc.querySelector(".group-heading").textContent === markup,
      "Label markup in group renders literally");
    await check([...doc.querySelectorAll(".service-name")].some(n => n.textContent === markup) &&
      [...doc.querySelectorAll(".service-detail")].some(n => n.textContent === markup),
      "Label markup in name and detail renders literally");
    await check(!doc.querySelector("#services img, #services [onerror], #services [onmouseover]") && !w.labelExecuted,
      "Metadata creates no executable markup or injected attributes");
    await check([...doc.querySelectorAll("a.service-url")].some(n => n.textContent === svc.url),
      "HTTP URL label is text and cannot inject link attributes");
    for (const url of ["javascript:alert(1)", "data:text/html,<script>alert(1)</script>", "//example.test", "/relative", "not a URL"]) {
      svc.url = url;
      await w.render();
      await check(![...doc.querySelectorAll("a")].some(n => n.textContent === url) &&
        [...doc.querySelectorAll("span.service-url")].some(n => n.textContent === url),
        "Unsafe or nonabsolute URL stays nonclickable: " + url);
    }
    for (const url of ["http://example.test/", "https://example.test/"]) {
      svc.url = url;
      await w.render();
      await check([...doc.querySelectorAll("a.service-url")].some(n => n.href === url),
        "Allowed URL remains clickable: " + url);
    }
    w.fetch = originalFetch;
    await w.render();

    // Drive both real collectors through accepted TCP connections that never reply.
    for (const name of ["docker", "proxmox"]) {
      const other = name === "docker" ? "proxmox" : "docker";
      const controls = await fetch("/control").then(r => r.json());
      await control({[name]: "hang"});
      await until(async () => (await fetch("/control").then(r => r.json())).stalls[name] > controls.stalls[name]);
      const before = await snapshot();
      const start = Date.now();
      const failed = await until(async () => {
        const data = await snapshot();
        return !source(data, name).ok && data;
      });
      await check(source(failed, name).error.includes("timed out"),
        name + " accepted-but-unanswered request becomes a normal timeout failure");
      await check(Date.now() - start < 13000 && source(failed, name).last_success === source(before, name).last_success,
        name + " times out within the 10s budget (plus scheduling tolerance) and retains last_success");
      const servicesFor = data => data.services.filter(s => s.id.startsWith(name + ":"));
      await check(JSON.stringify(servicesFor(failed)) === JSON.stringify(servicesFor(before)),
        name + " retains its last-known services");
      await check(source(failed, other).ok && source(failed, other).last_success > source(before, other).last_success,
        other + " continues updating despite " + name + " timing out");
      if (name === "docker") {
        await control({docker: "online"});
        await until(async () => source(await snapshot(), "docker").ok);
      }
    }

    // Proxmox stays stalled: browser and collector failures must coexist.
    await w.render();
    await until(() => doc.querySelector(".collector-stale"));
    const retainedNames = cards().map(c => c.querySelector(".service-name").textContent).join("|");
    await control({api: "error"});
    await w.render();
    await until(() => !banner().hidden);
    await check(banner().textContent.includes("Page cannot reach labwatch"), "HTTP 503 shows page-to-labwatch connection loss");
    await check(cards().every(c => c.classList.contains("browser-stale")), "All retained cards are marked page disconnected");
    await check(Boolean(doc.querySelector(".collector-stale.browser-stale .stale-badge")) &&
      Boolean(doc.querySelector(".collector-stale.browser-stale .browser-stale-badge")),
      "Collector staleness and browser disconnection are displayed together with distinct badges");
    await check(doc.getElementById("sources").textContent.includes("Last report: ✗ labwatch cannot reach proxmox"),
      "Source failure remains explicitly attributed to labwatch-to-Proxmox");
    const ageBefore = doc.getElementById("update-time").textContent;
    await until(() => doc.getElementById("update-time").textContent !== ageBefore, 7000);
    await check(doc.getElementById("update-time").textContent !== ageBefore, "Snapshot age advances during browser connection loss");
    await check(cards().map(c => c.querySelector(".service-name").textContent).join("|") === retainedNames,
      "Connection loss retains the displayed services");

    await control({api: "online"});
    await w.render();
    await until(() => banner().hidden);
    await check(!doc.querySelector(".browser-stale") && Boolean(doc.querySelector(".collector-stale")),
      "Browser recovery clears only browser staleness while Proxmox remains failed");
    await control({api: "drop"});
    await w.render();
    await until(() => !banner().hidden);
    await check(!banner().hidden, "Dropped network connection shows connection loss");
    await control({api: "online"});
    await w.render();
    await until(() => banner().hidden);
    await control({api: "hang"});
    await w.render();
    await until(() => !banner().hidden);
    await check(!banner().hidden, "Accepted-but-unanswered browser fetch is aborted and shows connection loss");
    await control({api: "online", proxmox: "online"});
    await until(async () => source(await snapshot(), "proxmox").ok);
    await w.render();
    await until(() => banner().hidden && !doc.querySelector(".stale"));
    await check(!doc.querySelector(".stale"), "Both connections recover and clear their own failure indicators");

    await control({docker: "forbidden"});
    await until(async () => source(await snapshot(), "docker").error === "docker returned HTTP 403");
    await check(true, "Live Docker 403 surfaces HTTP status rather than an HTML parsing error");
    await control({docker: "online"});
    await until(async () => source(await snapshot(), "docker").ok);

    // First-load failure must also explain an empty page.
    await control({api: "error"});
    await new Promise(resolve => { frame.onload = resolve; frame.contentWindow.location.reload(); });
    await until(() => !frame.contentDocument.getElementById("connection-status").hidden);
    await check(frame.contentDocument.getElementById("connection-status").textContent.includes("No status has been received"),
      "First-load failure shows a connection warning before any snapshot exists");
    await control({api: "online"});
    await frame.contentWindow.render();
    await until(() => frame.contentDocument.getElementById("connection-status").hidden);
    await post("/results", {ok: true, checks: results});
  } catch (error) {
    document.getElementById("results").textContent += "\nFAIL: " + error.stack;
    await post("/results", {ok: false, checks: results, error: error.toString() + "\n" + error.stack});
  }
})();
